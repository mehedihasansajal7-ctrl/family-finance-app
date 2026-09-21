/**
 * Family Finance — Cloud Functions.
 *
 * =============================================================================
 * WHY THIS FILE EXISTS
 * =============================================================================
 * The product rule is that the family dashboard shows **totals only**, and that an individual's
 * financial data must never reach another member's device — not through an API response, not
 * through a local cache, not through network inspection.
 *
 * The naive implementation (every client downloads every member's transactions and adds them up)
 * breaks that rule the moment it ships, because the private rows physically arrive on the wrong
 * device. So the aggregate is computed *here*, on the server, and the only thing written back to a
 * client-readable location is `families/{familyId}/aggregates/{yyyy-MM}` — a document whose shape
 * contains totals, family-level category totals and a trend series, and no member attribution at
 * all.
 *
 * `firestore.rules` sets `allow write: if false` on that collection, so the app can read the
 * aggregate but can never fabricate or edit one.
 *
 * Money is handled as **integer minor units (poisha)** end to end. No floating point arithmetic is
 * performed on an amount anywhere in this file.
 */

const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

initializeApp();
const db = getFirestore();

// Keep the blast radius small: a runaway loop here must not be able to burn the project's quota.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

// =============================================================================
// Calendar helpers
//
// Transactions carry `dateEpochDay`: a *calendar date* encoded as days since 1970-01-01. That is
// timezone-stable, which is why month bucketing here is exact arithmetic rather than a locale dance.
// =============================================================================

const MS_PER_DAY = 86400000;

/** Epoch-day for a 1-based calendar month. */
function epochDay(year, month, day) {
  return Math.floor(Date.UTC(year, month - 1, day) / MS_PER_DAY);
}

function daysInMonth(year, month) {
  // Day 0 of the following month is the last day of this one; handles leap years for free.
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function monthBounds(year, month) {
  return {
    start: epochDay(year, month, 1),
    end: epochDay(year, month, daysInMonth(year, month)),
  };
}

/** "2026-09" for an epoch-day. */
function monthKeyOfEpochDay(value) {
  const date = new Date(value * MS_PER_DAY);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

function parseMonthKey(key) {
  const [year, month] = key.split("-").map(Number);
  return { year, month };
}

function shiftMonth(key, delta) {
  const { year, month } = parseMonthKey(key);
  const zeroBased = year * 12 + (month - 1) + delta;
  return `${Math.floor(zeroBased / 12)}-${String((zeroBased % 12) + 1).padStart(2, "0")}`;
}

// =============================================================================
// Aggregate computation
// =============================================================================

/**
 * Reads every member's private transactions for one month and reduces them to a single,
 * member-free aggregate document.
 *
 * The per-member totals computed in `contributions` are used for the family total and then
 * discarded. They are never written anywhere.
 */
async function computeAggregate(familyId, monthKey) {
  const { year, month } = parseMonthKey(monthKey);
  const { start, end } = monthBounds(year, month);

  const familySnapshot = await db.collection("families").doc(familyId).get();
  if (!familySnapshot.exists) return null;
  const family = familySnapshot.data();

  const memberSnapshot = await db
    .collection("families")
    .doc(familyId)
    .collection("members")
    .get();

  const memberUids = memberSnapshot.docs
    .filter((doc) => doc.data().status === "active")
    .map((doc) => doc.id);

  // Per-member roll-ups. These stay in this function's memory only.
  const contributions = [];

  // Family-wide category totals. A category total is shared; who contributed it is not.
  const categoryTotals = new Map();

  await Promise.all(
    memberUids.map(async (uid) => {
      const snapshot = await db
        .collection("users")
        .doc(uid)
        .collection("transactions")
        .where("dateEpochDay", ">=", start)
        .where("dateEpochDay", "<=", end)
        .get();

      let income = 0;
      let expense = 0;

      snapshot.forEach((doc) => {
        const tx = doc.data();
        const amount = Number(tx.amountMinor) || 0;

        if (tx.type === "income") {
          income += amount;
        } else if (tx.type === "expense") {
          expense += amount;
          const categoryId = tx.categoryId || "uncategorised";
          categoryTotals.set(categoryId, (categoryTotals.get(categoryId) || 0) + amount);
        }
        // Transfers are deliberately ignored: moving money between a member's own accounts is
        // neither income nor expense and must not change any family figure.
      });

      contributions.push({ income, expense });
    })
  );

  const totalIncome = contributions.reduce((sum, c) => sum + c.income, 0);
  const totalExpense = contributions.reduce((sum, c) => sum + c.expense, 0);

  // Six-month trend, oldest first.
  const monthlyTrend = [];
  for (let offset = 5; offset >= 0; offset -= 1) {
    const key = shiftMonth(monthKey, -offset);
    const point = key === monthKey
      ? { income: totalIncome, expense: totalExpense }
      : await monthTotals(familyId, memberUids, key);
    monthlyTrend.push({
      month: key,
      incomeMinor: point.income,
      expenseMinor: point.expense,
      savingsMinor: point.income - point.expense,
    });
  }

  const previousKey = shiftMonth(monthKey, -1);
  const previous = await monthTotals(familyId, memberUids, previousKey);

  const aggregate = {
    month: monthKey,
    familyId,
    totalIncomeMinor: totalIncome,
    totalExpenseMinor: totalExpense,
    totalSavingsMinor: totalIncome - totalExpense,
    memberCount: memberUids.length,
    expenseByCategory: [...categoryTotals.entries()]
      .map(([categoryId, totalMinor]) => ({ categoryId, totalMinor }))
      .sort((a, b) => b.totalMinor - a.totalMinor),
    monthlyTrend,
    previousMonthIncomeMinor: previous.income,
    previousMonthExpenseMinor: previous.expense,
    generatedAt: FieldValue.serverTimestamp(),
  };

  // Net worth is opt-in, and is itself an aggregate: the sum of every member's income minus
  // expense. No member-level figure is stored.
  if (family.netWorthEnabled === true) {
    aggregate.netWorthMinor = await familyNetWorth(familyId, memberUids);
  }

  return aggregate;
}

/** Income and expense totals for one month across all members. */
async function monthTotals(familyId, memberUids, monthKey) {
  const { year, month } = parseMonthKey(monthKey);
  const { start, end } = monthBounds(year, month);

  const perMember = await Promise.all(
    memberUids.map(async (uid) => {
      const snapshot = await db
        .collection("users")
        .doc(uid)
        .collection("transactions")
        .where("dateEpochDay", ">=", start)
        .where("dateEpochDay", "<=", end)
        .get();

      let income = 0;
      let expense = 0;
      snapshot.forEach((doc) => {
        const tx = doc.data();
        const amount = Number(tx.amountMinor) || 0;
        if (tx.type === "income") income += amount;
        else if (tx.type === "expense") expense += amount;
      });
      return { income, expense };
    })
  );

  return perMember.reduce(
    (acc, cur) => ({ income: acc.income + cur.income, expense: acc.expense + cur.expense }),
    { income: 0, expense: 0 }
  );
}

/** Sum of every member's lifetime income minus expense. An aggregate, never per-member. */
async function familyNetWorth(familyId, memberUids) {
  const perMember = await Promise.all(
    memberUids.map(async (uid) => {
      const snapshot = await db.collection("users").doc(uid).collection("transactions").get();
      let income = 0;
      let expense = 0;
      snapshot.forEach((doc) => {
        const tx = doc.data();
        const amount = Number(tx.amountMinor) || 0;
        if (tx.type === "income") income += amount;
        else if (tx.type === "expense") expense += amount;
      });
      return income - expense;
    })
  );
  return perMember.reduce((sum, value) => sum + value, 0);
}

async function writeAggregate(familyId, monthKey) {
  const aggregate = await computeAggregate(familyId, monthKey);
  if (!aggregate) return;
  await db
    .collection("families")
    .doc(familyId)
    .collection("aggregates")
    .doc(monthKey)
    .set(aggregate, { merge: true });
}

// =============================================================================
// Triggers
// =============================================================================

/**
 * Recomputes the aggregate whenever a private transaction changes.
 *
 * Both the "before" and "after" months are recomputed, so moving a transaction across a month
 * boundary corrects both months rather than leaving a stale total behind.
 */
exports.onTransactionWritten = onDocumentWritten(
  "users/{uid}/transactions/{transactionId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    const current = after || before;
    if (!current) return;

    const familyId = current.familyId;
    if (!familyId) {
      logger.warn("Transaction without a familyId; skipping aggregate", { uid: event.params.uid });
      return;
    }

    const months = new Set();
    if (before && typeof before.dateEpochDay === "number") {
      months.add(monthKeyOfEpochDay(before.dateEpochDay));
    }
    if (after && typeof after.dateEpochDay === "number") {
      months.add(monthKeyOfEpochDay(after.dateEpochDay));
    }

    for (const monthKey of months) {
      try {
        await writeAggregate(familyId, monthKey);
      } catch (error) {
        logger.error("Aggregate recompute failed", { familyId, monthKey, error: String(error) });
        throw error; // let Cloud Functions retry
      }
    }
  }
);

/**
 * Recomputes recent aggregates when membership changes, so a newly joined member's existing
 * transactions are reflected (and a removed member stops contributing).
 */
exports.onMemberWritten = onDocumentWritten(
  "families/{familyId}/members/{uid}",
  async (event) => {
    const familyId = event.params.familyId;
    const now = new Date();
    const currentMonth = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;

    // The current month plus the six-month trend window.
    for (let offset = 0; offset <= 6; offset += 1) {
      await writeAggregate(familyId, shiftMonth(currentMonth, -offset));
    }
  }
);

/**
 * Nightly backfill. Catches anything a missed trigger would have left stale — a trigger that failed
 * after its retries, or a manual data fix.
 */
exports.nightlyAggregateBackfill = onSchedule(
  { schedule: "every day 02:30", timeZone: "Asia/Dhaka" },
  async () => {
    const families = await db.collection("families").get();
    const now = new Date();
    const currentMonth = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;

    for (const family of families.docs) {
      for (let offset = 0; offset <= 6; offset += 1) {
        await writeAggregate(family.id, shiftMonth(currentMonth, -offset));
      }
    }
    logger.info("Nightly aggregate backfill complete", { families: families.size });
  }
);

// =============================================================================
// Invitations
// =============================================================================

/**
 * Redeems a family invitation code.
 *
 * Runs as a callable rather than a client write because consuming a single-use code must be atomic:
 * two simultaneous redemptions of the same code must not both succeed. The whole check-and-consume
 * happens inside one Firestore transaction.
 *
 * The function returns only the family id. It never returns financial data — joining a family must
 * not reveal anything about the members' money.
 */
exports.redeemInvitation = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Please sign in first.");

  const code = String(request.data?.code || "").trim().toUpperCase();
  if (!code) throw new HttpsError("invalid-argument", "An invitation code is required.");

  const matches = await db
    .collectionGroup("invitations")
    .where("code", "==", code)
    .limit(1)
    .get();

  if (matches.empty) {
    throw new HttpsError("not-found", "That invitation code is not valid.");
  }

  const invitationRef = matches.docs[0].ref;
  const familyRef = invitationRef.parent.parent;
  const memberRef = familyRef.collection("members").doc(uid);
  const userSnapshot = await db.collection("users").doc(uid).collection("profile").doc("main").get();

  const displayName =
    userSnapshot.data()?.fullName ||
    request.auth.token.name ||
    request.auth.token.email?.split("@")[0] ||
    "Member";

  await db.runTransaction(async (transaction) => {
    const invitationSnapshot = await transaction.get(invitationRef);
    const invitation = invitationSnapshot.data();
    if (!invitation) throw new HttpsError("not-found", "That invitation no longer exists.");

    const now = Date.now();
    if (invitation.revokedAt) {
      throw new HttpsError("failed-precondition", "That invitation has been revoked.");
    }
    if (typeof invitation.expiresAt === "number" && now >= invitation.expiresAt) {
      throw new HttpsError("failed-precondition", "That invitation has expired.");
    }
    if ((invitation.usedCount || 0) >= (invitation.maxUses || 1)) {
      throw new HttpsError("failed-precondition", "That invitation has already been used.");
    }

    const existingMember = await transaction.get(memberRef);
    if (existingMember.exists) {
      throw new HttpsError("already-exists", "You already belong to a family.");
    }

    // Only the shareable subset plus membership metadata. Role is forced to 'member' — redeeming a
    // code can never grant admin.
    transaction.set(memberRef, {
      uid,
      fullName: displayName,
      photoUrl: userSnapshot.data()?.photoUrl || null,
      relationship: userSnapshot.data()?.relationship || null,
      role: "member",
      status: "active",
      shareProfileWithFamily: true,
      updatedAt: now,
    });

    transaction.update(invitationRef, { usedCount: (invitation.usedCount || 0) + 1 });
  });

  logger.info("Invitation redeemed", { familyId: familyRef.id, uid });
  return { familyId: familyRef.id };
});
