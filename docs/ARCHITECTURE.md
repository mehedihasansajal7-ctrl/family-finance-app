# Architecture

---

## 1. Layering

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ui/  —  Jetpack Compose + MVVM                                              │
│                                                                              │
│   Screen (Composable)  ──state──▶  ViewModel  ──▶  domain/repository (interface)│
│         ▲                              │                                     │
│         └──────────── UiState ─────────┘                                     │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────────────┐
│  data/  —  offline-first repositories                                        │
│                                                                              │
│   ┌──────────────┐   commit    ┌──────────────────┐   push    ┌───────────┐  │
│   │  Room DAOs   │◀────────────│  Repository impl │──────────▶│  Remote   │  │
│   │ (source of   │             │                  │           │  engine   │  │
│   │  truth)      │────────────▶│  SyncCoordinator │◀──fail────│           │  │
│   └──────┬───────┘   Flow      └──────────────────┘  enqueue  └───────────┘  │
│          │                                                                   │
│          │ Flow<List<Entity>> → mappers → domain models                      │
└──────────┼───────────────────────────────────────────────────────────────────┘
           │
┌──────────▼───────────────────────────────────────────────────────────────────┐
│  domain/  —  pure Kotlin, no Android dependency                              │
│                                                                              │
│   model/       entities, enums, aggregates                                   │
│   usecase/     Money maths · balance · summary · aggregate · budget · goal   │
│                · validator · recurrence                                      │
│   security/    FamilyAccessPolicy                                            │
│   repository/  interfaces                                                    │
└──────────────────────────────────────────────────────────────────────────────┘
           ▲
┌──────────┴───────────────────────────────────────────────────────────────────┐
│  core/  —  money · time · security · result · logging · util                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

`domain/` has **zero Android imports**. Everything in it runs in a fast JVM test — which is why 182
tests covering money, balances, aggregation, recurrence and the access policy run in seconds.

---

## 2. Why the domain layer is pure

The rules of a finance app are arithmetic and policy. Those are exactly the things worth testing
exhaustively, and exactly the things that are painful to test through a UI.

Keeping `domain/` free of Android means:

- `Money`, `MoneyMath`, `MoneyFormat`, `AppTime` — plain classes, tested directly.
- `FamilyAccessPolicy` — a pure decision table, testable as a 22-case matrix.
- `FamilyAggregateCalculator` — a pure function whose *output type* can be asserted to contain no
  member attribution.

The `Money` type is a `@JvmInline value class` over `Long`, so it costs nothing at runtime while
making "an amount" a distinct type from "a count".

---

## 3. Offline-first write path

```
user taps Save
      │
      ▼
Repository.upsertTransaction()
      │
      ├─ 1. validate (ownership, amount, dates) ──────────── fail ──▶ Outcome.Failure
      │
      ├─ 2. write to Room  (syncState = PENDING)            ← the UI is now updated
      │
      ├─ 3. engine.isConfigured?
      │       ├─ no  ──▶ done (demo mode)
      │       └─ yes ──▶ push
      │                    ├─ success ──▶ syncState = SYNCED
      │                    └─ failure ──▶ SyncCoordinator.enqueue(op)
      │
      └─ 4. Outcome.Success(local row)   ← the write is never lost
```

Two properties this buys:

**The ledger is never wrong locally.** A failed network call cannot leave the local database and the
UI disagreeing, because the local write already committed.

**Nothing is ever silently dropped.** A failed push is enqueued in `pending_ops`, retried on next
drain, and surfaced to the user via `OfflineBanner` with a count. The user is told, not left
guessing.

### Idempotency

`Transaction.clientId` is generated on the device at creation time and carries a **unique index** in
Room. Replaying the queue — after a timeout, a retry, or an app restart — upserts the same record
rather than creating a second one. `LocalIsolationTest` asserts this at the SQL level.

The `SyncCoordinator` also skips an op whose record has since been deleted locally, treating it as a
satisfied no-op rather than retrying forever.

---

## 4. The sync engine abstraction

```kotlin
interface RemoteSyncEngine {
    val isConfigured: Boolean
    suspend fun pushTransaction(...): Outcome<Unit>
    suspend fun pushAccount(...): Outcome<Unit>
    fun observeFamilyAggregate(familyId, month): Flow<FamilyAggregate>
    // …
}
```

Two implementations:

| | `NoOpRemoteSyncEngine` | `FirebaseRemoteSyncEngine` |
|---|---|---|
| When | no Firebase project configured | real `google-services.json` present |
| Behaviour | every push is a no-op success | Firestore + Storage |
| Aggregate | computed locally from Room | read from the server-written document |

`FirebaseConfig.isConfigured()` detects the placeholder `google-services.json` by its project id and
app id, so demo mode is selected automatically and the app is runnable with zero external setup.

**Note on the aggregate in demo mode.** When there is no backend, the family aggregate is computed
on-device from the local database. That is sound *only* because demo mode is a single device where
every member's data already lives in the same database — there is no trust boundary to cross. The
DAO method that enables it is named `allForFamilyLocalDemo()` and carries an explicit warning so it
cannot be reached for by accident on a synced device.

---

## 5. State flow and the UI

ViewModels expose `StateFlow<UiState<T>>` where

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Failure(val message: String) : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
}
```

A screen renders exactly one of the three. That is what makes "every screen has loading, empty, error
and success states" a structural property rather than a checklist item someone forgets.

Writes go through `BaseViewModel.run { }`, which is the single place an `Outcome.Failure` becomes
user-visible text. Nothing swallows a failure.

`combine` is limited to five flows, so screens that need more (the dashboard needs six) nest two
combines through a small private holder type rather than resorting to an array-typed overload with
unsafe casts.

---

## 6. Dependency injection

Hilt. The graph is deliberately shallow:

- `AppModule` — `Context`, `TimeProvider`, the Room database and its DAOs, `SessionManager`,
  `DemoSeeder`, `RemoteSyncEngine` (chosen once, at graph construction), `AuthRepository`
  (a router that picks Firebase or demo).
- `RepositoryModule` — `@Binds` for the ten repository interfaces.

`TimeProvider` is injected rather than called statically so date-sensitive logic can be tested
without sleeping. `FirebaseAuth` is injected as a `dagger.Lazy` because `FirebaseAuth.getInstance()`
throws when no Firebase project is configured, and the class is still constructed in demo mode.

---

## 7. Key design decisions

### No "paid by" / "income by" field

Ownership of a financial record is the authenticated uid. A "paid by" field would be client-supplied,
would have to be trusted, and would be an IDOR waiting to happen. Its absence is a product rule
*and* the thing that makes the privacy model enforceable.

### The aggregate is server-computed

Covered in depth in [`SECURITY.md`](SECURITY.md#3-why-the-family-dashboard-is-server-computed). The
short version: computing it on-device would require shipping every member's private rows to every
other member's device, which is a leak regardless of what the UI displays.

### Transfers are first-class and excluded from totals

`TransactionEffect` is the double-entry core:

```kotlin
INCOME   → +amount to accountId
EXPENSE  → -amount from accountId
TRANSFER → -amount from transferFromAccountId, +amount to transferToAccountId
```

Transfers are restricted to one owner's own accounts, so a transfer can never change any income or
expense total, personal or family. `BalanceCalculatorTest` asserts the combined balance of the two
accounts is unchanged and that a transfer contributes zero to both income and expense.

### Charts are Canvas, not a library

Three chart shapes were needed. A Canvas implementation has no version-conflict surface and lets the
maths live in a pure, tested `ChartMath` object.

### Enums persist by stable string id

`ExpenseCategory.FOOD_GROCERY` stores `"food_grocery"`, never an ordinal and never a label. Renaming
or translating a label can therefore never change what a stored transaction means.

### No destructive Room migration

`Room.databaseBuilder(...).build()` with no `fallbackToDestructiveMigration()`. Silently dropping a
ledger is not an acceptable failure mode; a schema change must ship a real migration. Schemas are
exported to `app/schemas/` so migrations are reviewable in a diff.

### The dashboard aggregate is never optimistically patched

After a write, the client does **not** add the amount to the displayed family total. It waits for the
server-written aggregate. Optimistically patching would mean deriving a family figure from one
member's row on the wrong device — which is exactly the leak this architecture exists to prevent.

---

## 8. Error handling

| Concern | Approach |
|---|---|
| Expected failures | `Outcome<T>` (`Success` / `Failure(AppError)`) — never exceptions |
| Programming errors | exceptions, allowed to crash in debug |
| User-visible errors | `AppError.message` is always safe to display and never contains a sensitive value |
| One-shot messages | `Channel` in `BaseViewModel`, collected as snackbars |
| Unexpected failures | caught in `run { }`, logged by class name only, shown as a generic message |

`Outcome` is used instead of Kotlin's `Result` because a repository failure is a normal outcome, not
an exception, and because a custom type lets `AppError` carry a typed `ErrorCode` the UI can branch
on.

---

## 9. Recurring transactions

`RecurrenceCalculator` is pure and exhaustively tested (16 tests). The two properties that matter:

**Idempotency.** Generation resumes strictly after `lastGeneratedEpochDay`, and each generated row
records the occurrence date it came from. Running the generator twice produces zero new rows the
second time. `RecurringRepositoryImpl.generateDue()` additionally checks existing occurrences before
inserting, and the unique `clientId` index is a third line of defence.

**Month-end clamping.** A rule anchored on the 31st fires on 28 Feb (29 in a leap year), then 31 Mar,
then 30 Apr — it neither skips February nor slides into March.

`generateDue` is called on app start from `AppViewModel`, so a rule that came due while the app was
closed is materialised on next launch.

---

## 10. What would change at scale

Honest notes on where this design would need work:

- **Aggregate recomputation is per-write.** A burst of writes recomputes the same month repeatedly. A
  debounce, or incrementally maintained counters, would be the next step.
- **`generateDue` scans all of a user's active rules on every app start.** Fine for a household,
  would want a `nextDueEpochDay` index for anything larger.
- **The trend chart recomputes six months of totals per aggregate write.** For a family that is six
  small queries; for a large tenant it would want a rollup.
- **Pagination.** Transaction lists load in full for a user. Fine for personal finance volumes;
  would need paging for a decade of data.
