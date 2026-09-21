# Security & privacy model

This document explains what is protected, how, and — importantly — what is *not* protected. It is
written to be accurate rather than reassuring.

---

## 1. The product rule

> The Family Dashboard is common and visible to all family members.
> Individual financial information is private.

Concretely, no member may see another member's:

- income amount, expense amount, cash balance, savings, loans
- bank account details, bKash/Nagad/Rocket numbers, card information
- personal transactions, personal reports, receipts

This holds **even for the family owner and for admins**. Administration is not financial access.

---

## 2. Where the rule is enforced

Three layers, listed in decreasing order of authority.

### 2.1 Firestore security rules — the enforcement point

`backend/firestore.rules`.

```
match /users/{uid}/{document=**} {
  allow read:  if signedIn() && me() == uid;
  allow create, update: if signedIn() && me() == uid && ownerConsistent(uid) && noCredentials();
  allow delete: if signedIn() && me() == uid;
}
```

One rule shape protects every private collection (accounts, transactions, recurring rules, savings
goals, loans, notifications, audit, profile). There is deliberately **no family-scoped read rule
anywhere under `/users`**, so an admin's request is rejected by exactly the same expression that
rejects a stranger's.

Verified by 40 emulator tests in `backend/rules-tests`, including explicit denials for a member, an
admin and the owner reading another member's data.

### 2.2 Cloud Functions — the only writer of family financial data

`families/{familyId}/aggregates/{month}` is `allow write: if false`. The Android app can read the
aggregate and can never fabricate or edit one; only the Admin SDK (inside a Cloud Function) can
produce it.

### 2.3 `FamilyAccessPolicy` — a client-side mirror

`domain/security/FamilyAccessPolicy.kt`. Pure Kotlin, no Android dependency, tested as a 22-case
matrix in `FamilyAccessPolicyTest`.

**This is not the security boundary.** Its purpose is that the UI never *attempts* a forbidden read,
so the app fails closed and the rules are never the thing that surprises a user. If this file and the
rules ever disagree, the rules win — but a disagreement is a bug, and the test suite is designed to
catch it.

---

## 3. Why the family dashboard is server-computed

This is the most important design decision in the project.

### The naive implementation, and why it is wrong

The obvious way to build the dashboard is for each client to download every family member's
transactions and add them up locally, displaying only the totals.

That leaks everything. Every member's private rows physically arrive on every other member's device —
in the API response, in the local Room cache, in any proxy log, in a memory dump. The UI showing only
totals is cosmetic; the data has already crossed the trust boundary.

### What this project does instead

A Cloud Function (`backend/functions/index.js`) reads each member's private transactions **with
server credentials**, computes the family totals, and writes back a document whose shape contains no
member attribution at all:

```
families/{familyId}/aggregates/{yyyy-MM}
  totalIncomeMinor            totalExpenseMinor         totalSavingsMinor
  netWorthMinor?              memberCount
  expenseByCategory[]         monthlyTrend[]            previousMonthIncome/ExpenseMinor
```

The client receives only that. There is no member field to leak because there is no member field.

Two tests defend this:

- `FamilyAggregateCalculatorTest.the aggregate never carries member attribution` renders an aggregate
  built from distinctive member keys and asserts none of them appear in the output, and that nothing
  resembling `uid`, `memberKey` or `ownerUid` does either.
- The rules test `aggregate write protection` asserts that a member, an admin and the owner are all
  denied when writing an aggregate.

---

## 4. Identity

Identity is always derived from the authenticated session, never from client input.

- **Firebase mode:** `FirebaseAuth.currentUser.uid`. `FirebaseRemoteSyncEngine.guard()` additionally
  refuses any write whose payload claims an `ownerUid` other than the authenticated one — failing
  locally and cheaply rather than relying on the rules to reject it.
- **Demo mode:** a locally stored profile keyed by email.
- **No shared family login exists.** Each member has an individual account, because ownership of a
  financial record *is* the authenticated identity. This is why there is no "paid by" or "income by"
  field anywhere in the data model — the field would have to be trusted, and a trusted client-supplied
  owner is an IDOR waiting to happen.

### Repository-level defence in depth

Every personal repository method takes the owner uid explicitly and filters on it in SQL:

```kotlin
@Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid ORDER BY dateEpochDay DESC")
```

There is no `getAllTransactions()` anywhere. A query that cannot be written cannot be misused.
`LocalIsolationTest` (Robolectric) asserts the SQL actually scopes: cross-owner reads return nothing,
cross-owner deletes remove nothing, and a keyword search cannot reach another owner's rows.

---

## 5. Credentials the app refuses to store

PINs, OTPs, CVVs, banking passwords and transaction passwords are **never** persisted. Enforcement is
triple-layered:

1. `SensitiveInputRules.isForbiddenField()` — used by the validator.
2. `TransactionValidator.containsForbiddenFields()` — rejects a payload carrying one.
3. Firestore `noCredentials()` — rejects the write at the database edge.

### Identifier handling

- Full account/card numbers are stored only for the owner's own reference and are **always** rendered
  as `•••• •••• 1234`.
- Only the last four characters are extracted and indexed for display (`MoneyFormat.last4Of`).
- Card numbers are Luhn-checked so an obvious typo is caught before it is saved.

---

## 6. Logging

`AppLog` is the only logging entry point in the app. It:

- emits nothing at all in release builds,
- passes every message through `Redactor.scrub()`, which removes credential-looking `key=value` pairs
  and any run of 10+ digits,
- logs only a throwable's **class name**, never its message (which may embed values).

`Redactor.redactMap()` is available for structured context; `AuditRepositoryImpl.record()` applies it
to every audit entry before persisting.

---

## 7. Audit trail

`AuditRepositoryImpl` records sign-in, sign-out, family creation, invitation creation/revocation,
member removal, role change, account create/update/delete, profile update, transaction create/update/
delete and attachment add/delete.

Action names are constants on the `AuditRepository` interface, so a caller cannot invent a
misspelled action that later becomes unqueryable.

Context is redacted on the way in. The trail never contains a password, PIN, OTP, full account number
or card CVV.

---

## 8. Device-level protections

| Protection | Implementation |
|---|---|
| Screenshot / recents-preview blocking | `FLAG_SECURE` applied by `SecureScreen()` while sensitive screens are composed |
| Optional app lock | `BiometricLockManager` via `BiometricPrompt`; falls back to device credential |
| Idle timeout | `SessionManager.IDLE_TIMEOUT_MILLIS` (5 min), re-locks on `ON_START` |
| Secure logout | Clears identity from DataStore; Firebase Auth signs out |
| Encrypted local secrets | `EncryptedSharedPreferences` (AES-256-GCM, key in the Android Keystore) |
| Backups disabled | `allowBackup=false`, plus explicit `backup_rules` / `data_extraction_rules` exclusions |
| Scoped file sharing | Exports shared via `FileProvider` with a per-file URI grant |

**Honest framing of the app lock:** it is a *convenience* lock, not the security boundary. It stops
someone holding an unlocked phone from browsing finances. The actual access control is server-side;
bypassing the prompt gains an attacker nothing beyond what an already-unlocked device with a live
session would give them. If the device has no enrolled biometric or screen lock, the app lock is
simply unavailable rather than locking the user out of their own data.

---

## 9. Notifications

Notification bodies are deliberately amount-free, so a lock-screen preview can never disclose a
balance or a salary. `NotificationRepositoryImpl.notify()` is documented to accept only
non-sensitive text; the intended pattern is "You have a financial reminder", never "Your salary of
৳70,000 was received."

---

## 10. Input validation and money safety

`TransactionValidator` enforces: amount > 0, a sanity ceiling, date within 1971 → today + 10 years,
account must belong to the authenticated user, transfer source ≠ destination, both transfer accounts
owned, no category on a transfer, no transfer accounts on an income/expense, note length, and
ownership present.

Balance checks are **warnings by default** and errors only when the caller opts in. A user may
legitimately record an overdraft or a correction, and blocking that would be worse than the risk.

Money is integer minor units (poisha) end to end. `Money` is a value class over `Long`; the only
lossy operation is an explicit, `HALF_UP`-rounded division in `MoneyMath`. `MoneyTest` includes the
canonical `0.1 + 0.2` case and a 10,000-addition drift test specifically to fail if anyone ever swaps
the implementation for `Double`.

---

## 11. What is *not* protected

Stated plainly:

1. **Demo mode is not a security boundary.** With no Firebase project there is no server, so nothing
   can enforce a password. It hashes with PBKDF2 into `EncryptedSharedPreferences`, and the UI says
   plainly that it is demo mode — but it is not an authentication guarantee. Real security begins when
   Firebase Auth is configured.
2. **A rooted device is outside the threat model.** `FLAG_SECURE` and `EncryptedSharedPreferences` are
   platform features; a compromised OS can defeat both.
3. **App Check is not enabled.** It would raise the cost of calling the backend from an unofficial
   client. It is a hardening step, not a correctness fix, and it is not configured here.
4. **No rate limiting on the backend.** Firebase Auth has its own; the callable function does not add
   any.
5. **The release build is signed with the debug keystore**, so it is not a production artefact.
6. **The aggregate is eventually consistent.** A dashboard read immediately after a write may show the
   previous totals until the function completes. The client shows what the server last wrote; it does
   not optimistically patch the aggregate, precisely so the family view never contains data derived
   from another member's rows on the wrong device.

---

## 12. Verifying the claims

Nothing in this document is asserted without a test behind it.

```bash
# Privacy matrix, money maths, balance/transfer, aggregation, recurrence (182 tests)
./gradlew :app:testDebugUnitTest

# The actual Firestore rules, against the emulator (40 tests; needs JDK 21+)
cd backend/rules-tests && npm install && npm test
```

If you are auditing this project, start with
`app/src/test/.../domain/security/FamilyAccessPolicyTest.kt` and
`backend/rules-tests/firestore.rules.test.js`. Between them they are the evidence for every privacy
claim made above.
