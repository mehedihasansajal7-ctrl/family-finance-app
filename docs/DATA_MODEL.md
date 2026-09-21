# Data model

---

## 1. Storage topology

```
Firestore
├── families/{familyId}                              SHARED — any active member may read
│   ├── members/{uid}                                role · status · shareable profile fields
│   ├── budgets/{budgetId}                           shared budgets
│   ├── goals/{goalId}                               shared goals
│   ├── bills/{billId}                               shared bills
│   ├── invitations/{invitationId}                   admin-readable only
│   └── aggregates/{yyyy-MM}                         SERVER-WRITTEN · client read-only
│
└── users/{uid}                                      PRIVATE — owner only, no exception
    ├── profile/main
    ├── accounts/{accountId}
    ├── transactions/{transactionId}
    ├── recurringRules/{ruleId}
    ├── savingsGoals/{goalId}
    ├── loans/{loanId}
    ├── notifications/{id}
    └── audit/{id}

Cloud Storage
├── users/{uid}/attachments/…                        PRIVATE — owner only
└── families/{familyId}/shared/…                     readable by active members (explicitly shared)
```

The single most important structural fact: **nothing private is ever written under `families`**.
Private records live in per-user subcollections, which is what lets one rule shape
(`match /users/{uid}/{document=**}`) protect all of them.

---

## 2. Money representation

Money is stored as an **integer number of minor units** — poisha for BDT, where 1 BDT = 100 poisha.

| Layer | Representation | Field |
|---|---|---|
| Kotlin domain | `Money` (`@JvmInline value class` over `Long`) | `.minor` |
| Room | `INTEGER` | `amountMinor`, `openingBalanceMinor`, … |
| Firestore | `number` (integer) | `amountMinor`, `limitMinor`, … |
| CSV export | decimal string, e.g. `1250.75` | — |
| PDF export | formatted `৳1,250.75` | — |

No floating-point arithmetic is performed on an amount anywhere in the codebase. The only lossy
operation is an explicit `HALF_UP`-rounded division in `MoneyMath.divide` / `percentageOf`.

Dates are stored as **epoch-day** integers (days since 1970-01-01). A transaction date is a calendar
date, not an instant, so epoch-day is exact and timezone-stable — which is why month bucketing in the
aggregate function is arithmetic rather than a locale dance.

---

## 3. Entities

### 3.1 `UserProfile`

The authenticated identity. One account per human; no shared family login.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Firebase Auth uid, or a generated id in demo mode |
| `email`, `displayName`, `photoUrl` | string | |
| `familyId` | string? | null until the user creates or joins a family |
| `role` | `owner` \| `admin` \| `member` | |
| `status` | `active` \| `suspended` \| `removed` | |
| `createdAt`, `updatedAt` | epoch millis | |

### 3.2 `Family`

| Field | Type | Notes |
|---|---|---|
| `id`, `name`, `photoUrl` | | |
| `currencyCode` | string | `BDT` |
| `timezone` | string | `Asia/Dhaka` |
| `ownerUid` | string | the owner's membership can never be deleted |
| `netWorthEnabled` | bool | opt-in; controls the dashboard net-worth tile |

### 3.3 `MemberProfile` (private) / family member document (shared)

The private profile lives at `users/{uid}/profile/main` and holds name, photo, date of birth, mobile,
relationship, occupation, notes and `shareProfileWithFamily`.

The **shared** member document at `families/{familyId}/members/{uid}` contains only the shareable
subset plus membership metadata:

```
uid · fullName · photoUrl · relationship · role · status · shareProfileWithFamily · updatedAt
```

No financial field appears in it. This split is what lets the member directory render without any
risk of pulling a balance into the wrong screen.

### 3.4 `Account` — private

| Field | Notes |
|---|---|
| `id`, `ownerUid`, `familyId` | `ownerUid` is the privacy key |
| `name`, `type` | `type` ∈ Bank, bKash, Nagad, Rocket, Upay, Cash, Debit Card, Credit Card, DPS, FDR, Savings Certificate, Investment, Other |
| `institution` | bank or provider name |
| `identifier` | full number — stored for reference, **never rendered in full** |
| `last4` | the only part ever displayed |
| `openingBalance` | money |
| `isActive`, `notes`, `currencyCode` | |
| `createdAt`, `updatedAt`, `syncState` | |

**Balance derivation:**

```
openingBalance
  + income where accountId = this
  + transfers where transferToAccountId = this
  - expense where accountId = this
  - transfers where transferFromAccountId = this
  = current balance
```

Computed in `AccountBalanceCalculator`, which filters by `ownerUid` as defence in depth even when the
caller has already scoped the query.

### 3.5 `Transaction` — private

| Field | Notes |
|---|---|
| `id` | document id |
| `clientId` | **device-generated idempotency key** — unique index in Room |
| `ownerUid` | the privacy key; the authenticated uid, never client-supplied |
| `familyId` | which family the entry was recorded under |
| `type` | `income` \| `expense` \| `transfer` |
| `amount` | money, > 0 |
| `categoryId`, `subCategoryId` | stable string ids |
| `accountId` | for income / expense |
| `transferFromAccountId`, `transferToAccountId` | for transfer |
| `dateEpochDay` | calendar date |
| `note` | |
| `attachmentIds` | receipt references |
| `recurringRuleId` | set when generated from a rule |
| `recurrenceOccurrenceEpochDay` | which occurrence this row came from — prevents duplicate posting |
| `createdAt`, `updatedAt`, `syncState` | |

**There is no `paidBy` or `incomeBy` field.** Ownership is the authenticated identity. A
client-supplied owner would have to be trusted, which is exactly the IDOR this model avoids.

**Invariants enforced by `TransactionValidator`:**

- `amount > 0`, and below a sanity ceiling
- income / expense require `accountId` and `categoryId`, and must not carry transfer accounts
- transfer requires both accounts, `from ≠ to`, both owned by the caller, and must **not** carry
  `accountId` or `categoryId`
- `dateEpochDay` within 1971-01-01 → today + 10 years
- note ≤ 500 characters
- `ownerUid` and `familyId` present

### 3.6 `RecurringRule` — private

| Field | Notes |
|---|---|
| `type`, `amount`, `categoryId`, `accountId` / transfer accounts | the template |
| `frequency` | `daily` \| `weekly` \| `monthly` \| `yearly` \| `custom` |
| `customIntervalDays` | for `custom` |
| `startEpochDay`, `endEpochDay` | |
| `lastGeneratedEpochDay` | generation resumes strictly after this — the idempotency guard |
| `isActive`, `reminderEnabled` | |

### 3.7 `FamilyBudget` — shared

| Field | Notes |
|---|---|
| `name`, `categoryId` | `categoryId == null` means "all categories" |
| `periodEpochDayStart`, `periodEpochDayEnd` | the budget window |
| `limit` | money |

`BudgetStatusCalculator.actualFor()` computes the actual from **family-wide** expense totals for the
window. It never reads member identity, so a budget row cannot disclose who spent.

### 3.8 `FamilyGoal` — shared

`name`, `categoryId`, `targetAmount`, `currentAmount`, `targetEpochDay`, `description`, `status`.

Contribution *amounts* are private; only the running `currentAmount` total is shared. The Firestore
rules enforce this at field level — a member may update `currentAmountMinor` and `updatedAt` and
nothing else:

```
allow update: if isAdmin(familyId)
  || (isMember(familyId)
      && request.resource.data.diff(resource.data).affectedKeys()
           .hasOnly(['currentAmountMinor', 'updatedAt']));
```

### 3.9 `FamilyBill` — shared

`name`, `categoryId`, `expectedAmount`, `dueEpochDay`, `frequency`, `status`, `reminderEnabled`,
`reminderDaysBefore`.

**No `paidBy` field.** A member may update `status` and `updatedAt` only (marking it paid); admins may
edit everything.

### 3.10 `SavingsGoal` — private

`name`, `targetAmount`, `currentAmount`, `targetEpochDay`, `note`, `status`. Never surfaced on the
family dashboard.

### 3.11 `PersonalLoan` — private

`direction` (`borrowed` \| `lent`), `counterparty`, `principal`, `interestRatePercent`,
`installmentAmount`, `totalInstallments`, `paidInstallments`, `repaidAmount`, `startEpochDay`,
`dueEpochDay`, `note`, `isClosed`.

`outstanding` is derived: `principal - repaidAmount`, floored at zero.

### 3.12 `Attachment` — private by default

`fileName`, `mimeType`, `sizeBytes`, `storagePath`, `transactionId`, `isFamilyVisible`, `createdAt`.

`mimeType` is **derived from magic bytes**, never from the file name or the client-supplied type, so a
mislabelled upload cannot become an executable payload. Accepted: JPEG, PNG, WebP, PDF, ≤ 10 MB.

### 3.13 `AppNotification`, `AuditLog`

Notifications carry no amount. Audit logs are append-only with redacted context; action names are
constants on `AuditRepository`.

---

## 4. Aggregate document

Written only by the Cloud Function. Read-only to clients.

```
families/{familyId}/aggregates/{yyyy-MM}
  month                       "2026-09"
  familyId
  totalIncomeMinor            integer
  totalExpenseMinor           integer
  totalSavingsMinor           integer
  memberCount                 integer
  expenseByCategory[]         [{ categoryId, totalMinor }]
  monthlyTrend[]              [{ month, incomeMinor, expenseMinor, savingsMinor }]
  previousMonthIncomeMinor    integer
  previousMonthExpenseMinor   integer
  netWorthMinor?              present only when the family enabled it
  generatedAt                 server timestamp
```

**There is no member field.** That is the entire point: the client receives family totals and nothing
that could be traced back to a person.

---

## 5. Room schema

15 entities in one database (`family_finance.db`, version 1), all money as `INTEGER` minor units.

| Table | Scope | Notes |
|---|---|---|
| `accounts` | owner | indexed on `ownerUid` |
| `transactions` | owner | indexed on `ownerUid`, `(ownerUid, dateEpochDay)`, `(ownerUid, typeId)`, `(ownerUid, accountId)`, `(familyId, dateEpochDay)`; **unique** on `clientId` |
| `recurring_rules` | owner | |
| `families` | shared | |
| `member_profiles` | shared directory | role + status live here |
| `invitations` | shared | |
| `family_budgets`, `family_goals`, `family_bills` | shared | |
| `savings_goals`, `personal_loans` | owner | |
| `attachments` | owner | |
| `notifications` | owner | |
| `audit_logs` | shared | |
| `pending_ops` | owner | the offline write queue |

Every personal table carries `ownerUid`, and **every query that returns personal rows filters on it**.
There is no unscoped personal query in the DAO layer at all — a query that cannot be written cannot be
misused.

`exportSchema = true` with schemas written to `app/schemas/`, and no
`fallbackToDestructiveMigration()`: a schema change must ship a real migration, because silently
dropping a ledger is not an acceptable failure mode.

---

## 6. Migrations policy

Version 1 has no migrations yet. When the schema changes:

1. Bump `version` in `FamilyFinanceDatabase`.
2. Add a `Migration(n, n+1)` implementing the transformation explicitly.
3. Register it via `.addMigrations(...)` in `AppModule`.
4. Commit the regenerated schema in `app/schemas/` so the change is reviewable in the diff.
5. Add a `MigrationTestHelper` test.

---

## 7. Categories

Categories persist by **stable string id**, never by ordinal and never by label:

| Income | Expense |
|---|---|
| `salary`, `business`, `freelancing`, `bonus`, `rental`, `interest_profit`, `gift`, `other` | `food_grocery`, `housing_rent`, `electricity`, `gas`, `water`, `internet`, `mobile`, `transport`, `education`, `medical`, `shopping`, `clothing`, `entertainment`, `family`, `donation_charity`, `loan_emi`, `travel`, `other` |

Renaming or translating a label therefore cannot change what a stored transaction means. Unknown ids
degrade to a readable fallback (`CategoryLabels.any`) rather than crashing, so a category can be added
server-side before an app update ships.
