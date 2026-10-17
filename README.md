# Family Finance

A private **family finance manager for Android**. Every family member has their own login and their
own private financial records. Everyone shares one **common family dashboard** that shows *aggregate
totals only*.

> **The central rule:** the family dashboard is common, but individual financial information is
> private. No member — not another member, not an admin, and not the family owner — can see your
> income, expenses, account balances, bank or bKash/Nagad numbers, loans or savings. This is
> enforced in the database, not by hiding buttons.

---

## Table of contents

- [Status](#status)
- [Features](#features)
- [Screens](#screens)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Security model](#security-model)
- [Data model](#data-model)
- [Building](#building)
- [Testing](#testing)
- [Release](#release)
- [Firebase setup — the one manual step](#firebase-setup--the-one-manual-step)
- [Known limitations](#known-limitations)

---

## Status

| | |
|---|---|
| Debug APK | ✅ built — `app/build/outputs/apk/debug/app-debug.apk` (24.9 MB) |
| Release APK | ✅ built — `app/build/outputs/apk/release/app-release.apk` (3.9 MB, minified + resource-shrunk) |
| JVM unit tests | ✅ **182 passing**, 0 failing |
| Firestore rule tests | ✅ **40 passing** against the real emulator |
| Static analysis | ✅ `lint` clean of correctness errors (see `docs/BUILD_REPORT.md`) |
| Backend | ✅ rules, indexes, Cloud Functions and rule tests written; **not deployed** (needs your Firebase project — see below) |

The app runs **out of the box in local demo mode** with seeded sample data, so it can be installed
and explored immediately. Dropping in a real `google-services.json` switches it to the Firebase
backend with no code change.

---

## Features

### Shared (every family member sees this)

- **Family dashboard** — total income, total expense, savings, and optional family net worth, with a
  month-by-month comparison
- **Charts** — income vs expense trend, expense-by-category donut and bars
- **Family budget** — shared category budgets with alerts at 75% and 90%, and at 100% overrun
- **Family goals** — shared savings targets with progress and a required-per-month figure
- **Family bills** — shared recurring bills with due dates and overdue detection
- **Family reports** — monthly income, expense, savings, cash flow, category breakdown and a yearly
  trend, exportable to PDF
- **Member directory** and invitation management (owner/admin)

### Private (only you)

- **My accounts** — Bank, bKash, Nagad, Rocket, Upay, Cash, Debit/Credit Card, DPS, FDR, Savings
  Certificate, Investment, Other. Account numbers are always masked to `•••• •••• 1234`.
- **My income / expenses** — full CRUD with categories, notes, receipts and recurring rules
- **Transfers** — move money between your own accounts without affecting any income or expense total
- **My savings goals** and **my loans** (borrowed and lent)
- **My transactions** — search by date range, category, account, type, amount range and keyword
- **My reports** — personal income, expense, balances, category spend, cash flow and net worth
- **My profile** — name, photo, date of birth, mobile, relationship, occupation, with a
  non-financial visibility toggle
- **Receipts / attachments** — photo or PDF, magic-byte validated, stored privately

### Cross-cutting

- Individual logins (email/password, Google Sign-In when Firebase is configured)
- Family creation and **expiring, revocable, single-use invitation codes**
- Optional **biometric app lock** with a 5-minute idle timeout
- Screenshots and the recents preview blocked on sensitive screens (`FLAG_SECURE`)
- **Offline-first**: every write commits locally first, is queued, and syncs when possible — with no
  possibility of double-posting
- Bengali numerals (৳১২,৩৪৫) and South-Asian digit grouping (12,34,567)
- Light / dark theme

---

## Screens

```
Home (family dashboard)      Reports (Family | Personal)      Goals (Goals | Budgets | Bills)
My Finance (hub)             Add (income/expense/transfer)    Settings
 ├ Accounts                   ├ Add account                    ├ Security (app lock)
 ├ Transactions               ├ Search & filter                ├ Display (theme, Bengali digits)
 ├ Recurring entries          └ Export CSV / PDF               ├ Family (members, invitations)
 ├ Savings goals                                               ├ Export
 ├ Loans                                                      ├ Sync
 ├ Profile                                                    └ Account (sign out)
 └ Settings
```

Navigation is a bottom bar (Home · Reports · Goals · My Finance) plus a prominent **Add** action that
opens income, expense, transfer, bill and goal options.

---

## Architecture

```
┌──────────────────────────── Compose UI (MVVM) ────────────────────────────┐
│  Screens ──▶ ViewModels ──▶ domain/repository interfaces (Flow<...>)      │
└──────────────────────────────────┬────────────────────────────────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │   Offline-first repositories │  ← write locally first
                    └──────────────┬───────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
      Room (source of truth)  SyncCoordinator     RemoteSyncEngine
      accounts, transactions,  durable queue      ├ NoOp  (demo mode)
      budgets, goals, bills…   (idempotent)       └ Firebase (Firestore + Storage)
                                                          │
                                              ┌───────────▼───────────┐
                                              │  Cloud Functions       │
                                              │  computes AGGREGATES   │
                                              │  server-side           │
                                              └───────────┬───────────┘
                                                          ▼
                                    families/{id}/aggregates/{yyyy-MM}
                                    (totals only — client read-only)
```

**Clean layering.** `domain/` is pure Kotlin with no Android dependency at all — money, dates,
balance maths, aggregation, validation, the access policy and recurrence are all plain classes that
run in a fast JVM test. `data/` holds Room and Firebase. `ui/` holds Compose.

**Offline-first.** A write always commits to Room first, so the UI updates immediately and the ledger
stays consistent even with no network. The push is best-effort; a failure enqueues the operation in
`pending_ops` and it is retried. `Transaction.clientId` is a device-generated idempotency key backed
by a **unique index**, so a replayed queue cannot create a second row.

**The aggregate is server-computed.** See [Security model](#security-model) — this is the decision
that makes the privacy guarantee real rather than aspirational.

Full detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM + clean layering, Coroutines + Flow |
| DI | Hilt 2.52 |
| Local storage | Room 2.6.1, DataStore Preferences, EncryptedSharedPreferences |
| Backend | Firebase Auth, Cloud Firestore, Cloud Storage, Cloud Functions (Node 20) |
| Build | AGP 8.7.3, Gradle 8.10.2, JDK 17 |
| Charts | **Custom Compose `Canvas`** — no charting library |
| Tests | JUnit4, Truth, Robolectric, Turbine, MockK, `@firebase/rules-unit-testing` |

### Why no charting library?

Three chart shapes were needed (line, donut, horizontal bars). A `Canvas` implementation has no
version-conflict surface, renders identically offline, and — most usefully — lets the *maths* live in
a pure `ChartMath` object that is unit-tested on the JVM (9 tests) rather than only being verifiable
by looking at a device.

### Why Firebase?

It was the preferred stack and it fits the requirements well: managed auth, a document store with a
real server-side rule language, file storage, and a function runtime for the aggregate. The one
requirement it does *not* satisfy by itself is aggregate privacy — Firestore rules cannot compute a
sum — which is exactly why the aggregate is a Cloud Function output. See
[`docs/SECURITY.md`](docs/SECURITY.md).

---

## Project structure

```
family-finance-app/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json          # placeholder → demo mode (see Firebase setup)
│   ├── google-services.json.example  # template with instructions
│   ├── schemas/                      # exported Room schemas (migration review)
│   └── src/
│       ├── main/kotlin/com/familyfinance/app/
│       │   ├── core/                 money · time · security · result · logging · util
│       │   ├── domain/
│       │   │   ├── model/            entities, enums, aggregates
│       │   │   ├── repository/       interfaces (all suspend, all owner-scoped)
│       │   │   ├── security/         FamilyAccessPolicy  ← the privacy decision table
│       │   │   └── usecase/          balance · summary · aggregate · budget · goal
│       │   │                         · validator · recurrence
│       │   ├── data/
│       │   │   ├── local/            Room entities, DAOs, mappers, converters
│       │   │   ├── remote/           Firebase sync engine, config detection
│       │   │   ├── repository/       offline-first implementations
│       │   │   ├── session/          DataStore session + preferences
│       │   │   ├── sync/             durable offline queue
│       │   │   ├── seed/             demo data
│       │   │   └── di/               Hilt modules
│       │   └── ui/                   theme · components · navigation · screens · export
│       ├── test/                     182 JVM tests
│       └── androidTest/              instrumented UI test sources
├── backend/
│   ├── firestore.rules               the enforcement point
│   ├── storage.rules
│   ├── firestore.indexes.json
│   ├── firebase.json
│   ├── functions/index.js            server-side aggregate + invitation redemption
│   └── rules-tests/                  40 emulator tests
└── docs/
    ├── ARCHITECTURE.md
    ├── SECURITY.md
    ├── DATA_MODEL.md
    └── BUILD_REPORT.md
```

---

## Security model

### The rule

> Family-level data is shared with active members of that family.
> **Personal financial data is readable and writable by its owner, and by nobody else.**

### Where it is enforced

Three layers, in order of authority:

1. **`backend/firestore.rules`** — the actual enforcement point. `match /users/{uid}/{document=**}`
   grants access on `request.auth.uid == uid` and nothing else. No family role is consulted anywhere
   under `/users`, so an admin's or the owner's request is rejected identically to a stranger's.
2. **Cloud Functions** — the only writer of the family aggregate. `allow write: if false` on
   `families/{id}/aggregates/{month}` means the app cannot fabricate or edit an aggregate.
3. **`FamilyAccessPolicy`** (client, pure Kotlin) — a mirror of the rules so the UI never *attempts* a
   forbidden read. It is tested as a 22-case matrix, but it is not the security boundary.

### Why the aggregate is computed server-side

The family dashboard could have been built by having every client download every member's
transactions and total them locally. That would leak every member's private rows onto every other
member's device — through the API response, the local cache, and any network inspection — while the
UI displayed only totals.

So the aggregate is computed in a Cloud Function, and the **only** family-wide financial document
that reaches a client is:

```
families/{familyId}/aggregates/{yyyy-MM}
  totalIncomeMinor · totalExpenseMinor · totalSavingsMinor
  netWorthMinor?  · memberCount
  expenseByCategory[]  · monthlyTrend[]  · previousMonthIncome/ExpenseMinor
```

There is no member field in that shape, so there is nothing member-specific to leak.
`FamilyAggregateCalculatorTest` asserts that the Kotlin counterpart of this type never renders a
member key.

### Credentials

The app **refuses to store** PINs, OTPs, CVVs, banking passwords and transaction passwords. The
rejection is enforced in three places: the UI, the validator, and the Firestore rules themselves
(`noCredentials()`), so a crafted or restored payload cannot persist one.

Account and card identifiers are stored for reference only, are rendered exclusively as
`•••• •••• 1234`, and only the last four characters are indexed for display. Backups are disabled
(`allowBackup=false`) because private financial data must not leave the device via cloud backup or
device transfer.

Full detail: [`docs/SECURITY.md`](docs/SECURITY.md).

---

## Data model

```
families/{familyId}                        shared
  ├ members/{uid}                          role, status, shareable profile fields
  ├ budgets/{id}   goals/{id}   bills/{id} shared family finance
  ├ invitations/{id}                       codes (admin-readable only)
  └ aggregates/{yyyy-MM}                   SERVER-WRITTEN, aggregate-only

users/{uid}/                               PRIVATE — owner only
  ├ profile/main
  ├ accounts/{id}          transactions/{id}          recurringRules/{id}
  ├ savingsGoals/{id}      loans/{id}
  └ notifications/{id}     audit/{id}
```

Money is stored as **integer minor units (poisha)** everywhere — in Room, on the wire and in
Firestore. No floating-point arithmetic is performed on an amount anywhere in the codebase.

Full detail: [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).

---

## Building

### Prerequisites

- JDK 17
- Android SDK with `platforms;android-35` and `build-tools;35.0.0`
- A `local.properties` pointing at your SDK (`sdk.dir=...`)

### Commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (minified + shrunk)
./gradlew :app:testDebugUnitTest      # 182 JVM tests
./gradlew :app:lintDebug              # static analysis
./gradlew installDebug                # install on a connected device
```

Outputs land in `app/build/outputs/apk/`.

---

## Testing

### JVM unit tests — 182, all passing

| Suite | Tests | Covers |
|---|---|---|
| `MoneyTest` | 13 | exact arithmetic, parsing, the 0.1 + 0.2 trap, 10,000-addition drift |
| `MoneyMathTest` | 11 | percentages, allocation invariant, division rounding |
| `MoneyFormatTest` | 8 | South-Asian grouping, Bengali numerals, masking |
| `AppTimeTest` | 14 | Asia/Dhaka, month boundaries, leap years, overdue strictness |
| `RedactorTest` + `SensitiveInputRulesTest` | 12 | log redaction, forbidden fields, Luhn |
| `BalanceCalculatorTest` | 11 | opening balance, income/expense, **transfers** |
| `PersonalSummaryCalculatorTest` | 7 | owner scoping, month scoping, category totals |
| `FamilyAggregateCalculatorTest` | 7 | totals, **no member attribution**, change percentages |
| `BudgetStatusCalculatorTest` | 8 | 75/90/100 alerts, family-wide actuals |
| `GoalProgressCalculatorTest` | 8 | progress, required-per-month, edge cases |
| `BillStatusCalculatorTest` | 4 | overdue detection |
| `RecurrenceCalculatorTest` | 16 | **idempotency**, 31st-of-month clamping, leap years |
| `TransactionValidatorTest` | 22 | every rule, ownership, forbidden fields |
| `FamilyAccessPolicyTest` | 22 | **the privacy matrix** |
| `LocalIsolationTest` (Robolectric) | 10 | SQL-level owner scoping, idempotent replay |
| `ChartMathTest` | 9 | share/normalise invariants |

### Firestore rule tests — 40, all passing

Run against the **real Firestore emulator** with the real `firestore.rules`, so they test the actual
enforcement point rather than a re-implementation.

```bash
cd backend/rules-tests
npm install
npm test
```

Requires JDK 21+ (the emulator's requirement).

Coverage includes: a member reading their own data; **a member, an admin and the owner each being
denied** another member's transactions, accounts, savings goals and loans; cross-family denial;
suspended-member family denial; unauthenticated denial; the aggregate being read-only to every role;
non-admins being unable to see live invitation codes; self-promotion to admin being rejected;
and the owner's membership being undeletable.

### Instrumented tests

`app/src/androidTest` holds Compose UI test sources. They require a connected device or emulator and
are **not** part of the verified suite reported above.

---

## Release

`assembleRelease` currently signs with the **debug keystore** so the release build is reproducible
out of the box. Before publishing:

1. Create an upload keystore:
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add it to `app/build.gradle.kts` as a `signingConfig` (keep the passwords out of source — use
   `keystore.properties` and add it to `.gitignore`), and point the `release` build type at it.
3. Copy the sanitised template and fill in your own Firebase values:
   `cp app/google-services.example.json app/google-services.json`
4. Deploy the backend: `cd backend && firebase deploy`.

---

## Firebase setup — the one manual step

The project ships with a **placeholder** `app/google-services.json` containing syntactically valid
but fake identifiers. That is deliberate: it lets the app compile, install and run with zero external
setup, in **local demo mode** with seeded sample data and no network.

To use the real backend:

1. Create a project at <https://console.firebase.google.com>.
2. Add an Android app with package name **`com.familyfinance.app`**
   (and, for debug builds, **`com.familyfinance.app.debug`**).
3. Download the generated `google-services.json` and replace `app/google-services.json`.
   `app/google-services.json.example` documents the required shape.
4. In the console, enable:
   - **Authentication** → Email/Password, and Google if you want it
   - **Cloud Firestore** (production mode)
   - **Cloud Storage**
5. Deploy the rules, indexes and functions:
   ```bash
   cd backend
   npm install -g firebase-tools
   firebase login
   firebase use --add          # select your project
   cd functions && npm install && cd ..
   firebase deploy
   ```

That is genuinely the only step that cannot be automated from here — creating a Firebase project
requires an interactive Google login. **Nothing else about the project needs manual assembly.**

---

## Known limitations

These are stated plainly rather than glossed over.

1. **The backend is written but not deployed.** Rules, indexes, functions and their tests all exist
   and the rule tests pass against the emulator; deployment needs the project in step 5 above.
2. **Release is signed with the debug keystore.** Fine for internal distribution, not for Play.
3. **Demo mode is not a security boundary.** With no Firebase project there is no server, so nothing
   can enforce a password. It hashes with PBKDF2 into `EncryptedSharedPreferences` and the UI says
   plainly that it is demo mode — but it is not an authentication guarantee. Real security begins when
   Firebase Auth is configured.
4. **The family aggregate is recomputed on write, not read.** A burst of writes recomputes the same
   month repeatedly. A debounce or a batched counter would be the next optimisation.
5. **Instrumented UI tests are not in the verified suite.** They need a device/emulator.
6. **`Exporter` writes to the app's private `files/` directory** and shares via a scoped
   `FileProvider` URI. Files accumulate there; a retention sweep would be sensible.
7. **Photo upload from the gallery is passed through unmodified.** The size and magic-byte checks
   apply, but there is no re-compression step (the API supports it; the UI does not yet do it).
8. **No family-level transfer feature.** Transfers are restricted to one owner's own accounts by
   design, since a cross-member transfer would need its own privacy-safe design.

---

## Licence

Private project. Not for redistribution.
