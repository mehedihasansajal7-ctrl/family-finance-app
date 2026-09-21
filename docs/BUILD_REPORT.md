# Build & test report

Everything below was produced by actually running the commands on this machine. Nothing is
estimated or projected.

---

## 1. Environment used

| Component | Version | Notes |
|---|---|---|
| OS | Windows (win32) | |
| JDK (Android build) | Temurin **17.0.20.1** | AGP 8.7.3 requirement |
| JDK (Firestore emulator) | Temurin **21.0.12.1** | firebase-tools requires 21+ |
| Android SDK | `platforms;android-35`, `build-tools;35.0.0`, platform-tools 37.0.1 | |
| Gradle | 8.10.2 (wrapper) | |
| Android Gradle Plugin | 8.7.3 | |
| Kotlin | 2.0.21 | |
| Node.js | 24.19.0 / npm 11.17.0 | for the backend and rule tests |

> The machine had **no** JDK, Android SDK or Gradle at the start of the session. A portable toolchain
> was installed to `D:\AndroidDev\` (JDK 17, JDK 21, Android cmdline-tools + SDK, Gradle) with no
> admin rights required. `local.properties` and `D:\AndroidDev\env.ps1` capture that setup.

---

## 2. Build results

```
./gradlew :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL
```

| Artefact | Size | Notes |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | **25.17 MB** | debuggable, `applicationIdSuffix .debug` |
| `app/build/outputs/apk/release/app-release.apk` | **3.90 MB** | R8 minified + resource shrunk |

### Release APK verification

```
$ aapt2 dump badging app-release.apk
package: name='com.familyfinance.app' versionCode='1' versionName='1.0.0'
         platformBuildVersionName='15' compileSdkVersion='35'
minSdkVersion:'26'
targetSdkVersion:'35'
application-label:'Family Finance'
```

The release APK is 6.5× smaller than debug — evidence that R8 and resource shrinking are actually
running, not just configured.

**Signing caveat:** the release build is signed with the auto-generated debug keystore so it is
reproducible out of the box. This is documented in the README and is **not** a Play-ready artefact.

---

## 3. Test results

### 3.1 JVM unit tests

```
./gradlew :app:testDebugUnitTest
BUILD SUCCESSFUL
```

**182 tests, 0 failures, 0 errors.**

| Suite | Tests | What it proves |
|---|---|---|
| `MoneyTest` | 13 | exact arithmetic; includes the `0.1 + 0.2` trap and a 10,000-addition drift test |
| `MoneyMathTest` | 11 | percentages, allocation invariant (`allocate(x).sum() == x`), signed vs clamped percentage |
| `MoneyFormatTest` | 8 | South-Asian grouping (12,34,567), Bengali numerals, identifier masking |
| `AppTimeTest` | 14 | Asia/Dhaka, month boundaries, leap February, overdue strictness, Saturday week start |
| `RedactorTest` | 6 | log redaction of credentials and long digit runs |
| `SensitiveInputRulesTest` | 6 | forbidden credential fields, identifier normalisation, Luhn |
| `LocalIsolationTest` (Robolectric) | 10 | **SQL-level owner scoping**, idempotent replay, per-owner wipe |
| `FamilyAccessPolicyTest` | 22 | **the privacy matrix** |
| `BalanceCalculatorTest` | 11 | opening balance, income/expense, **transfer** debits/credits without changing totals |
| `PersonalSummaryCalculatorTest` | 7 | owner scoping, month scoping, category totals excluding transfers |
| `FamilyAggregateCalculatorTest` | 7 | totals, **no member attribution**, signed change percentages |
| `BudgetStatusCalculatorTest` | 8 | 75 / 90 / 100 alert bands, family-wide actuals |
| `GoalProgressCalculatorTest` | 8 | progress, required-per-month, negative/zero edge cases |
| `BillStatusCalculatorTest` | 4 | overdue detection |
| `RecurrenceCalculatorTest` | 16 | **idempotency**, 31st-of-month clamping, leap years, custom intervals |
| `TransactionValidatorTest` | 22 | every validation rule, ownership, forbidden fields |
| `ChartMathTest` | 9 | share/normalise invariants |

### 3.2 Firestore security-rule tests

Run against the **real Firestore emulator** with the real `firestore.rules`:

```
cd backend/rules-tests
firebase emulators:exec --only firestore --project demo-family-finance "mocha firestore.rules.test.js"
```

**40 tests, 0 failures.**

Highlights — each of these is an asserted *denial*:

| Claim | Result |
|---|---|
| A member reads their own transactions, accounts, goals, loans | ✅ allowed |
| A member reads **another member's** transactions / accounts / goals / loans | ✅ denied |
| An **admin** reads another member's transactions / accounts | ✅ denied |
| The **family owner** reads another member's transactions / accounts | ✅ denied |
| A member writes or deletes another member's data | ✅ denied |
| A payload claiming a different `ownerUid` than its path | ✅ denied |
| An outsider reads this family's aggregate / budgets / goals / bills / members | ✅ denied |
| An outsider administers this family | ✅ denied |
| An anonymous request reads any private data | ✅ denied |
| A suspended member reads family-level data | ✅ denied |
| A suspended member reads **their own** records | ✅ allowed |
| A member, admin or owner **writes** an aggregate | ✅ denied |
| A non-admin reads a live invitation code | ✅ denied |
| A user self-promotes to admin while joining | ✅ denied |
| A user self-joins as a plain member | ✅ allowed |
| Anyone removes the family owner | ✅ denied |
| A member leaves voluntarily | ✅ allowed |
| An admin removes a peer admin | ✅ denied |
| A member adds to a family goal total | ✅ allowed |
| A member changes a family goal target | ✅ denied |
| A member marks a bill paid | ✅ allowed |
| A member changes a bill amount | ✅ denied |
| A transaction carrying a `pin` | ✅ denied |

### 3.3 Instrumented (device) tests

Compose UI test sources exist under `app/src/androidTest`. They require a connected device or
emulator and were **not** run — no device was available in this environment. They are therefore
excluded from the verified numbers above. Stated here rather than quietly omitted.

---

## 4. Static analysis

```
./gradlew :app:lintDebug
BUILD SUCCESSFUL
```

| Severity | Count |
|---|---|
| **Error** | **0** |
| Warning | 83 |
| Information | 4 |

Breakdown of the warnings:

| Check | Count | Assessment |
|---|---|---|
| `GradleDependency` | 73 | newer library versions are available. Expected; not a correctness issue. |
| `AutoboxingStateCreation` | 4 | `mutableStateOf` where `mutableIntStateOf` would avoid boxing. Minor. |
| `AndroidGradlePluginVersion` | 3 | newer AGP available. |
| `ObsoleteSdkInt` | 2 | a version check is redundant at `minSdk 26`. Harmless. |
| `UnusedResources` | 2 | leftover resources. Harmless. |
| `UseTomlInstead`, `RedundantLabel`, `ModifierParameter` | 3 | style. |

**No errors.** `abortOnError` is left at its default of `true` in effect for errors; the build
configuration sets `checkReleaseBuilds = false` so a version-availability warning cannot block a
release build.

---

## 5. Defects found and fixed during this build

These are recorded because they are the useful output of the test suite — every one was caught by a
test, not by inspection.

### Product bugs (fixed in `main` source)

| # | Defect | Impact | Fix |
|---|---|---|---|
| 1 | `Money.times(BigDecimal)` returned `Long` instead of `Money` | compile error | wrapped in `Money(...)` |
| 2 | `AccountBalanceCalculator.balances` grouped by a `List<String>` key | balances would have been wrong for every account | inverted to `Map<accountId, List<Transaction>>` |
| 3 | `AppTime.startOfWeek` used `with(DayOfWeek.SATURDAY)` | moved *forward* to the coming Saturday for most weekdays, so "this week" filters were wrong 6 days out of 7 | `TemporalAdjusters.previousOrSame(SATURDAY)` |
| 4 | `RecurrenceCalculator` advanced a **weekly** rule by one day | every weekly rule (rent, subscriptions) posted 7× too often | added `weeklyOnOrAfter` with whole-week stepping |
| 5 | `MoneyMath.percentageOf` clamped negatives to zero | "spending fell 50%" rendered as "0%" on the dashboard | added `signedPercentageOf` for comparisons; kept the clamp for progress bars |
| 6 | Firestore rule allowed **the owner to delete their own membership** | a family could be left with no owner | `resource.data.role != 'owner'` now guards every delete branch |
| 7 | `FamilyAccessPolicy` required *active* membership to read your own data | a suspended or departed member would be locked out of their own ledger — and the app policy disagreed with the Firestore rules | own-data access now requires identity only; suspension withdraws **family** access |

Defects 3–7 are the interesting ones: each was a silent wrong-answer bug that would have shipped
without the test suite.

### Test defects (fixed in `test` source)

| # | Defect |
|---|---|
| 8 | Two test names contained `.` / `:`, which are illegal in JVM method names |
| 9 | `MoneyMathTest` expected `৳100 / 3 = ৳3333.33`; the implementation was right and the expectation was wrong |
| 10 | `RedactorTest` asserted a phone number is *not* redacted. Reversed: mobile numbers are PII and should be redacted |
| 11 | `LocalIsolationTest` assumed a replayed `clientId` creates a second row. The unique index correctly makes it replace instead — the test now asserts the stronger (correct) behaviour |
| 12 | `withSecurityRulesDisabled` resolves to `void`; the self-check captured the value from its return rather than a closure |

---

## 6. Source metrics

| | Files | Lines |
|---|---|---|
| Main Kotlin | 70 | 12,866 |
| Test Kotlin | 14 | 1,854 |
| Backend JS | 1 | ~420 |
| Rules (Firestore + Storage) | 2 | ~230 |
| Rule tests JS | 1 | ~590 |
| Documentation (Markdown) | 5 | ~1,400 |

---

## 7. What is verified vs. what is not

### Verified by execution

- The app compiles and both APKs build.
- 182 JVM tests pass, including the privacy matrix and the money/balance/transfer arithmetic.
- 40 Firestore rule tests pass against the real emulator, including every cross-member and
  cross-family denial.
- Lint reports zero errors.
- The release APK is minified and correctly manifests as `com.familyfinance.app`, minSdk 26,
  targetSdk 35.

### Written and reviewed, but not executed

- **Cloud Functions** (`backend/functions/index.js`) — syntactically complete, but not deployed or
  run. They need a real Firebase project, which requires an interactive Google login.
- **Firestore index deployment** — `firestore.indexes.json` is complete; not pushed to a project.
- **Instrumented UI tests** — need a device or emulator.
- **End-to-end runtime behaviour against Firebase** — the Firebase data path is exercised only by
  compilation, not by a live run.

### Deliberately not claimed

- The app has not been run on a physical device or emulator in this session.
- No performance, battery or memory profiling was performed.
- No penetration test was performed. The rule tests are a correctness check on the access-control
  model, not a substitute for an adversarial security review.

---

## 8. Reproducing everything

```bash
# Android: build + unit tests + lint
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug

# Backend: security-rule tests against the emulator (needs JDK 21+)
cd backend/rules-tests
npm install
npm test
```

To run against a real backend, follow
[Firebase setup](../README.md#firebase-setup--the-one-manual-step) — that is the single step that
cannot be automated from here.
