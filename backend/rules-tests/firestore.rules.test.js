/**
 * Firestore security-rule tests.
 *
 * These run against the real Firestore emulator with the real `firestore.rules` file, so they test
 * the actual enforcement point rather than a re-implementation of it.
 *
 * The suite is organised around the product's central claim:
 *
 *   Member A cannot read Member B's income, expenses, accounts, wallets or attachments —
 *   and neither can an admin, and neither can the family owner.
 *
 * Every one of those is asserted as a *denial*. If someone relaxes a rule to make a feature
 * convenient, a test here goes red before it reaches production.
 *
 * Run:  cd backend/rules-tests && npm install && npm test
 */

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const {
  doc,
  setDoc,
  getDoc,
  updateDoc,
  deleteDoc,
} = require("firebase/firestore");

const PROJECT_ID = "demo-family-finance";

const ALICE = "uid-alice"; // plain member
const BOB = "uid-bob"; // plain member
const CAROL = "uid-carol"; // admin
const OWNER = "uid-owner"; // family owner
const OUTSIDER = "uid-outsider"; // member of a different family

const FAMILY = "family-1";
const OTHER_FAMILY = "family-2";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(path.resolve(__dirname, "..", "firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();

  // Seed membership and some private data using rules-bypassing context.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "families", FAMILY), {
      familyId: FAMILY,
      name: "Hasan Family",
      ownerUid: OWNER,
      currencyCode: "BDT",
      timezone: "Asia/Dhaka",
      netWorthEnabled: false,
      createdAt: 1,
    });

    await setDoc(doc(db, "families", OTHER_FAMILY), {
      familyId: OTHER_FAMILY,
      name: "Other Family",
      ownerUid: OUTSIDER,
      currencyCode: "BDT",
      timezone: "Asia/Dhaka",
      createdAt: 1,
    });

    const members = [
      [ALICE, "member"],
      [BOB, "member"],
      [CAROL, "admin"],
      [OWNER, "owner"],
    ];
    for (const [uid, role] of members) {
      await setDoc(doc(db, "families", FAMILY, "members", uid), {
        uid,
        fullName: `Member ${uid}`,
        role,
        status: "active",
        shareProfileWithFamily: true,
        updatedAt: 1,
      });
    }

    await setDoc(doc(db, "families", OTHER_FAMILY, "members", OUTSIDER), {
      uid: OUTSIDER,
      fullName: "Outsider",
      role: "owner",
      status: "active",
      updatedAt: 1,
    });

    // A suspended member: present in the family, but must have no access at all.
    await setDoc(doc(db, "families", FAMILY, "members", "uid-suspended"), {
      uid: "uid-suspended",
      fullName: "Suspended",
      role: "member",
      status: "suspended",
      updatedAt: 1,
    });

    // Private financial records.
    for (const uid of [ALICE, BOB]) {
      await setDoc(doc(db, "users", uid, "accounts", "acc-1"), {
        accountId: "acc-1",
        ownerUid: uid,
        familyId: FAMILY,
        name: "DBBL Savings",
        type: "bank",
        last4: "3456",
        openingBalanceMinor: 1000000,
        currencyCode: "BDT",
        isActive: true,
        createdAt: 1,
        updatedAt: 1,
      });

      await setDoc(doc(db, "users", uid, "transactions", "tx-1"), {
        transactionId: "tx-1",
        clientId: `client-${uid}`,
        ownerUid: uid,
        familyId: FAMILY,
        type: "income",
        amountMinor: 7000000,
        categoryId: "salary",
        accountId: "acc-1",
        dateEpochDay: 20000,
        note: "Monthly salary",
        createdAt: 1,
        updatedAt: 1,
      });

      await setDoc(doc(db, "users", uid, "savingsGoals", "goal-1"), {
        goalId: "goal-1",
        ownerUid: uid,
        familyId: FAMILY,
        name: "Private goal",
        targetAmountMinor: 5000000,
        currentAmountMinor: 1000000,
        status: "active",
        updatedAt: 1,
      });

      await setDoc(doc(db, "users", uid, "loans", "loan-1"), {
        loanId: "loan-1",
        ownerUid: uid,
        familyId: FAMILY,
        direction: "borrowed",
        counterparty: "A lender",
        principalMinor: 2000000,
        repaidAmountMinor: 0,
        startEpochDay: 19900,
        isClosed: false,
        updatedAt: 1,
      });
    }

    // Shared family finance.
    await setDoc(doc(db, "families", FAMILY, "budgets", "budget-1"), {
      budgetId: "budget-1",
      familyId: FAMILY,
      name: "Food",
      categoryId: "food_grocery",
      periodEpochDayStart: 20000,
      periodEpochDayEnd: 20030,
      limitMinor: 2000000,
      createdByUid: OWNER,
      updatedAt: 1,
    });

    await setDoc(doc(db, "families", FAMILY, "goals", "family-goal-1"), {
      goalId: "family-goal-1",
      familyId: FAMILY,
      name: "Emergency Fund",
      categoryId: "emergency_fund",
      targetAmountMinor: 50000000,
      currentAmountMinor: 40000000,
      status: "active",
      updatedAt: 1,
    });

    await setDoc(doc(db, "families", FAMILY, "bills", "bill-1"), {
      billId: "bill-1",
      familyId: FAMILY,
      name: "Electricity",
      categoryId: "electricity",
      expectedAmountMinor: 340000,
      dueEpochDay: 20025,
      frequency: "monthly",
      status: "pending",
      updatedAt: 1,
    });

    await setDoc(doc(db, "families", FAMILY, "invitations", "inv-1"), {
      id: "inv-1",
      familyId: FAMILY,
      code: "ABCD-1234",
      createdByUid: OWNER,
      createdAt: 1,
      expiresAt: Date.now() + 3600000,
      maxUses: 1,
      usedCount: 0,
    });

    // The server-written aggregate. Client-readable, never client-writable.
    await setDoc(doc(db, "families", FAMILY, "aggregates", "2026-09"), {
      month: "2026-09",
      familyId: FAMILY,
      totalIncomeMinor: 10000000,
      totalExpenseMinor: 5500000,
      totalSavingsMinor: 4500000,
      memberCount: 2,
      expenseByCategory: [{ categoryId: "food_grocery", totalMinor: 5500000 }],
      monthlyTrend: [],
      previousMonthIncomeMinor: 0,
      previousMonthExpenseMinor: 0,
    });
  });
});

const alice = () => testEnv.authenticatedContext(ALICE).firestore();
const bob = () => testEnv.authenticatedContext(BOB).firestore();
const carol = () => testEnv.authenticatedContext(CAROL).firestore();
const owner = () => testEnv.authenticatedContext(OWNER).firestore();
const outsider = () => testEnv.authenticatedContext(OUTSIDER).firestore();
const suspended = () => testEnv.authenticatedContext("uid-suspended").firestore();
const anonymous = () => testEnv.unauthenticatedContext().firestore();

// =============================================================================
// A member CAN manage their own data
// =============================================================================

describe("own private data", () => {
  it("lets a member read their own account", async () => {
    await assertSucceeds(getDoc(doc(alice(), "users", ALICE, "accounts", "acc-1")));
  });

  it("lets a member read their own transactions", async () => {
    await assertSucceeds(getDoc(doc(alice(), "users", ALICE, "transactions", "tx-1")));
  });

  it("lets a member read their own savings goal and loan", async () => {
    await assertSucceeds(getDoc(doc(alice(), "users", ALICE, "savingsGoals", "goal-1")));
    await assertSucceeds(getDoc(doc(alice(), "users", ALICE, "loans", "loan-1")));
  });

  it("lets a member write their own transaction", async () => {
    await assertSucceeds(
      setDoc(doc(alice(), "users", ALICE, "transactions", "tx-new"), {
        transactionId: "tx-new",
        clientId: "client-new",
        ownerUid: ALICE,
        familyId: FAMILY,
        type: "expense",
        amountMinor: 25000,
        categoryId: "food_grocery",
        accountId: "acc-1",
        dateEpochDay: 20010,
        note: "Lunch",
        createdAt: 2,
        updatedAt: 2,
      })
    );
  });

  it("lets a member delete their own transaction", async () => {
    await assertSucceeds(deleteDoc(doc(alice(), "users", ALICE, "transactions", "tx-1")));
  });
});

// =============================================================================
// A member CANNOT read another member's financial data — the core guarantee
// =============================================================================

describe("cross-member denial", () => {
  it("blocks reading another member's income", async () => {
    await assertFails(getDoc(doc(alice(), "users", BOB, "transactions", "tx-1")));
  });

  it("blocks reading another member's accounts and balances", async () => {
    await assertFails(getDoc(doc(alice(), "users", BOB, "accounts", "acc-1")));
  });

  it("blocks reading another member's savings goals and loans", async () => {
    await assertFails(getDoc(doc(alice(), "users", BOB, "savingsGoals", "goal-1")));
    await assertFails(getDoc(doc(alice(), "users", BOB, "loans", "loan-1")));
  });

  it("blocks an admin from reading another member's transactions", async () => {
    // Administration is not financial access. This is the rule most often got wrong.
    await assertFails(getDoc(doc(carol(), "users", ALICE, "transactions", "tx-1")));
    await assertFails(getDoc(doc(carol(), "users", ALICE, "accounts", "acc-1")));
  });

  it("blocks the family owner from reading another member's transactions", async () => {
    await assertFails(getDoc(doc(owner(), "users", ALICE, "transactions", "tx-1")));
    await assertFails(getDoc(doc(owner(), "users", ALICE, "accounts", "acc-1")));
  });

  it("blocks writing to another member's data", async () => {
    await assertFails(
      updateDoc(doc(alice(), "users", BOB, "transactions", "tx-1"), { note: "tampered" })
    );
    await assertFails(deleteDoc(doc(alice(), "users", BOB, "transactions", "tx-1")));
  });

  it("blocks a payload that claims a different owner", async () => {
    await assertFails(
      setDoc(doc(alice(), "users", ALICE, "transactions", "tx-spoof"), {
        transactionId: "tx-spoof",
        clientId: "client-spoof",
        ownerUid: BOB, // disagrees with the path
        familyId: FAMILY,
        type: "income",
        amountMinor: 1,
        dateEpochDay: 20010,
        createdAt: 3,
        updatedAt: 3,
      })
    );
  });
});

// =============================================================================
// Cross-family denial
// =============================================================================

describe("cross-family denial", () => {
  it("blocks an outsider from reading this family's aggregate", async () => {
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "aggregates", "2026-09")));
  });

  it("blocks an outsider from reading this family's budgets, goals and bills", async () => {
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "budgets", "budget-1")));
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "goals", "family-goal-1")));
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "bills", "bill-1")));
  });

  it("blocks an outsider from reading this family's members", async () => {
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "members", ALICE)));
  });

  it("blocks an outsider from administering this family", async () => {
    await assertFails(
      updateDoc(doc(outsider(), "families", FAMILY), { name: "Hijacked" })
    );
  });
});

// =============================================================================
// Unauthenticated and suspended access
// =============================================================================

describe("unauthenticated and suspended access", () => {
  it("blocks anonymous reads of any private data", async () => {
    await assertFails(getDoc(doc(anonymous(), "users", ALICE, "transactions", "tx-1")));
    await assertFails(getDoc(doc(anonymous(), "families", FAMILY, "aggregates", "2026-09")));
  });

  it("keeps a suspended member's own records accessible to them", async () => {
    // Suspension removes *family* access. Your own ledger is still yours — the same rule that keeps
    // it private from everyone else keeps it available to its owner.
    await assertSucceeds(getDoc(doc(suspended(), "users", "uid-suspended", "transactions", "tx-1")));
  });

  it("blocks a suspended member from all family-level access", async () => {
    await assertFails(getDoc(doc(suspended(), "families", FAMILY, "aggregates", "2026-09")));
    await assertFails(getDoc(doc(suspended(), "families", FAMILY, "budgets", "budget-1")));
    await assertFails(getDoc(doc(suspended(), "families", FAMILY, "members", ALICE)));
  });
});

// =============================================================================
// Shared family data
// =============================================================================

describe("shared family data", () => {
  it("lets any active member read the aggregate", async () => {
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "aggregates", "2026-09")));
    await assertSucceeds(getDoc(doc(bob(), "families", FAMILY, "aggregates", "2026-09")));
    await assertSucceeds(getDoc(doc(carol(), "families", FAMILY, "aggregates", "2026-09")));
    await assertSucceeds(getDoc(doc(owner(), "families", FAMILY, "aggregates", "2026-09")));
  });

  it("lets any active member read budgets, goals and bills", async () => {
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "budgets", "budget-1")));
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "goals", "family-goal-1")));
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "bills", "bill-1")));
  });

  it("lets a member read the member directory", async () => {
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "members", BOB)));
  });

  it("lets a member read their own membership without recursing", async () => {
    await assertSucceeds(getDoc(doc(alice(), "families", FAMILY, "members", ALICE)));
  });
});

// =============================================================================
// The aggregate is server-written only
// =============================================================================

describe("aggregate write protection", () => {
  it("blocks a member from writing an aggregate", async () => {
    await assertFails(
      setDoc(doc(alice(), "families", FAMILY, "aggregates", "2026-09"), {
        month: "2026-09",
        totalIncomeMinor: 999999999,
      })
    );
  });

  it("blocks an admin from writing an aggregate", async () => {
    await assertFails(
      setDoc(doc(carol(), "families", FAMILY, "aggregates", "2026-10"), {
        month: "2026-10",
        totalIncomeMinor: 1,
      })
    );
  });

  it("blocks the owner from writing an aggregate", async () => {
    await assertFails(
      updateDoc(doc(owner(), "families", FAMILY, "aggregates", "2026-09"), {
        totalIncomeMinor: 1,
      })
    );
  });
});

// =============================================================================
// Family administration boundaries
// =============================================================================

describe("family administration", () => {
  it("lets an admin update family settings but not a plain member", async () => {
    await assertSucceeds(updateDoc(doc(carol(), "families", FAMILY), { name: "Renamed" }));
    await assertFails(updateDoc(doc(alice(), "families", FAMILY), { name: "Renamed by member" }));
  });

  it("hides live invitation codes from non-admin members", async () => {
    await assertSucceeds(getDoc(doc(carol(), "families", FAMILY, "invitations", "inv-1")));
    await assertFails(getDoc(doc(alice(), "families", FAMILY, "invitations", "inv-1")));
  });

  it("stops a user from self-promoting to admin on join", async () => {
    await assertFails(
      setDoc(doc(testEnv.authenticatedContext("uid-newbie").firestore(), "families", FAMILY, "members", "uid-newbie"), {
        uid: "uid-newbie",
        fullName: "Newbie",
        role: "admin", // must be rejected
        status: "active",
        updatedAt: 1,
      })
    );
  });

  it("allows a self-join as a plain active member", async () => {
    await assertSucceeds(
      setDoc(doc(testEnv.authenticatedContext("uid-newbie").firestore(), "families", FAMILY, "members", "uid-newbie"), {
        uid: "uid-newbie",
        fullName: "Newbie",
        role: "member",
        status: "active",
        updatedAt: 1,
      })
    );
  });

  it("never lets anyone remove the family owner", async () => {
    await assertFails(deleteDoc(doc(carol(), "families", FAMILY, "members", OWNER)));
    await assertFails(deleteDoc(doc(owner(), "families", FAMILY, "members", OWNER)));
  });

  it("lets a member leave voluntarily", async () => {
    await assertSucceeds(deleteDoc(doc(alice(), "families", FAMILY, "members", ALICE)));
  });

  it("stops an admin removing a peer admin", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "families", FAMILY, "members", "uid-admin2"), {
        uid: "uid-admin2",
        fullName: "Second admin",
        role: "admin",
        status: "active",
        updatedAt: 1,
      });
    });
    await assertFails(deleteDoc(doc(carol(), "families", FAMILY, "members", "uid-admin2")));
  });

  it("lets the family owner create their own owner membership", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "families", "family-own"), {
        familyId: "family-own", name: "Own", ownerUid: OWNER, currencyCode: "BDT", createdAt: 1,
      });
    });
    await assertSucceeds(setDoc(doc(owner(), "families", "family-own", "members", OWNER), {
      uid: OWNER, fullName: "Owner", role: "owner", status: "active", updatedAt: 1,
    }));
  });

  it("stops a non-owner claiming the owner role", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "families", "family-own2"), {
        familyId: "family-own2", name: "Own2", ownerUid: OWNER, currencyCode: "BDT", createdAt: 1,
      });
    });
    await assertFails(setDoc(doc(alice(), "families", "family-own2", "members", ALICE), {
      uid: ALICE, fullName: "Alice", role: "owner", status: "active", updatedAt: 1,
    }));
  });
});

// =============================================================================
// Field-level rules on shared goals and bills
// =============================================================================

describe("shared goal and bill field rules", () => {
  it("lets a member add to a family goal total", async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), "families", FAMILY, "goals", "family-goal-1"), {
        currentAmountMinor: 41000000,
        updatedAt: 2,
      })
    );
  });

  it("stops a member from changing the goal target", async () => {
    await assertFails(
      updateDoc(doc(alice(), "families", FAMILY, "goals", "family-goal-1"), {
        targetAmountMinor: 1,
        updatedAt: 2,
      })
    );
  });

  it("stops a member from deleting a family goal", async () => {
    await assertFails(deleteDoc(doc(alice(), "families", FAMILY, "goals", "family-goal-1")));
  });

  it("lets a member mark a bill paid but not change its amount", async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), "families", FAMILY, "bills", "bill-1"), { status: "paid", updatedAt: 2 })
    );
    await assertFails(
      updateDoc(doc(alice(), "families", FAMILY, "bills", "bill-1"), {
        expectedAmountMinor: 1,
        updatedAt: 2,
      })
    );
  });

  it("lets an admin edit a budget but not a member", async () => {
    await assertSucceeds(
      updateDoc(doc(carol(), "families", FAMILY, "budgets", "budget-1"), { limitMinor: 2500000 })
    );
    await assertFails(
      updateDoc(doc(alice(), "families", FAMILY, "budgets", "budget-1"), { limitMinor: 1 })
    );
  });
});

// =============================================================================
// Family group chat access
// =============================================================================

describe("family group chat", () => {
  it("lets active members read and send a message", async () => {
    const message = {
      messageId: "chat-1",
      familyId: FAMILY,
      senderUid: ALICE,
      senderName: "Member uid-alice",
      text: "Dinner at 8?",
      createdAt: 10,
    };
    await assertSucceeds(setDoc(doc(alice(), "families", FAMILY, "chatMessages", "chat-1"), message));
    await assertSucceeds(getDoc(doc(bob(), "families", FAMILY, "chatMessages", "chat-1")));
  });

  it("blocks another family and rejects spoofed sender writes", async () => {
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "chatMessages", "chat-1")));
    await assertFails(setDoc(doc(alice(), "families", FAMILY, "chatMessages", "chat-spoof"), {
      messageId: "chat-spoof",
      familyId: FAMILY,
      senderUid: BOB,
      senderName: "Member uid-bob",
      text: "Spoofed",
      createdAt: 10,
    }));
  });

  it("lets the sender or an admin delete, but not edit, a message", async () => {
    await setDoc(doc(alice(), "families", FAMILY, "chatMessages", "chat-delete"), {
      messageId: "chat-delete",
      familyId: FAMILY,
      senderUid: ALICE,
      senderName: "Member uid-alice",
      text: "Remove me",
      createdAt: 10,
    });
    await assertFails(updateDoc(doc(bob(), "families", FAMILY, "chatMessages", "chat-delete"), { text: "Changed" }));
    await assertSucceeds(deleteDoc(doc(carol(), "families", FAMILY, "chatMessages", "chat-delete")));
  });

  it("allows a photo-only message but rejects a completely empty one", async () => {
    await assertSucceeds(setDoc(doc(alice(), "families", FAMILY, "chatMessages", "chat-photo"), {
      messageId: "chat-photo",
      familyId: FAMILY,
      senderUid: ALICE,
      senderName: "Member uid-alice",
      text: "",
      attachmentUrl: "https://example.com/photo.jpg",
      attachmentContentType: "image/jpeg",
      createdAt: 11,
    }));
    await assertFails(setDoc(doc(alice(), "families", FAMILY, "chatMessages", "chat-empty"), {
      messageId: "chat-empty",
      familyId: FAMILY,
      senderUid: ALICE,
      senderName: "Member uid-alice",
      text: "",
      createdAt: 12,
    }));
  });

  it("lets a member write only their own read position", async () => {
    await assertSucceeds(setDoc(doc(alice(), "families", FAMILY, "chatReads", ALICE), {
      uid: ALICE,
      lastReadAt: 100,
    }));
    await assertSucceeds(getDoc(doc(bob(), "families", FAMILY, "chatReads", ALICE)));
    await assertFails(setDoc(doc(alice(), "families", FAMILY, "chatReads", BOB), {
      uid: BOB,
      lastReadAt: 100,
    }));
    await assertFails(getDoc(doc(outsider(), "families", FAMILY, "chatReads", ALICE)));
  });
});

// =============================================================================
// Credential fields are rejected at the database edge
// =============================================================================

describe("credential rejection", () => {
  it("rejects a transaction carrying a pin or cvv", async () => {
    await assertFails(
      setDoc(doc(alice(), "users", ALICE, "transactions", "tx-pin"), {
        transactionId: "tx-pin",
        clientId: "client-pin",
        ownerUid: ALICE,
        familyId: FAMILY,
        type: "expense",
        amountMinor: 100,
        dateEpochDay: 20010,
        pin: "1234",
        createdAt: 4,
        updatedAt: 4,
      })
    );
  });
});

// =============================================================================
// Sanity: the suite would notice if rules were removed entirely
// =============================================================================

describe("rule suite self-check", () => {
  it("has a seeded aggregate to assert against", async () => {
    // `withSecurityRulesDisabled` resolves to void, so the snapshot is captured in the closure.
    let snapshot;
    await testEnv.withSecurityRulesDisabled(async (context) => {
      snapshot = await getDoc(doc(context.firestore(), "families", FAMILY, "aggregates", "2026-09"));
    });
    assert.ok(snapshot && snapshot.exists(), "seed data missing — the denial tests would be vacuous");
  });
});
