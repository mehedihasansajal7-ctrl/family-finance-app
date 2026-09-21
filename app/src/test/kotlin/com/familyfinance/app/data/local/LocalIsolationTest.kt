package com.familyfinance.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.data.local.entity.AccountEntity
import com.familyfinance.app.data.local.entity.TransactionEntity
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Data-layer isolation.
 *
 * These tests exercise the SQL, not the UI. They are the evidence for the claim that "Member A
 * cannot read Member B's transactions": the only queries that exist are owner-scoped, and this
 * asserts that they actually scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalIsolationTest {

    private lateinit var database: FamilyFinanceDatabase

    private val alice = "uid-alice"
    private val bob = "uid-bob"
    private val family = "family-1"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyFinanceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun transaction(
        id: String,
        ownerUid: String,
        amountTaka: Long,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: String? = "acc-$ownerUid",
        categoryId: String? = "food_grocery",
    ) = TransactionEntity(
        id = id,
        clientId = "client-$id",
        ownerUid = ownerUid,
        familyId = family,
        typeId = type.id,
        amountMinor = Money.ofBdt(amountTaka).minor,
        categoryId = categoryId,
        subCategoryId = null,
        accountId = accountId,
        transferFromAccountId = null,
        transferToAccountId = null,
        dateEpochDay = 20_000L,
        note = "note-$id",
        attachmentIds = emptyList(),
        recurringRuleId = null,
        recurrenceOccurrenceEpochDay = null,
        createdAt = 1L,
        updatedAt = 1L,
        syncStateId = SyncState.SYNCED.id,
    )

    private fun account(id: String, ownerUid: String) = AccountEntity(
        id = id,
        ownerUid = ownerUid,
        familyId = family,
        name = "Account $id",
        typeId = AccountType.BANK.id,
        institution = null,
        identifier = null,
        last4 = null,
        openingBalanceMinor = Money.ofBdt(10_000).minor,
        currencyCode = "BDT",
        isActive = true,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
        syncStateId = SyncState.SYNCED.id,
    )

    @Test
    fun `observing one owner's transactions never returns another owner's rows`() = runTest {
        val dao = database.transactionDao()
        dao.upsertAll(
            listOf(
                transaction("a1", alice, 100),
                transaction("a2", alice, 200),
                transaction("b1", bob, 900),
                transaction("b2", bob, 800),
            )
        )

        val aliceRows = dao.observeFiltered(alice, null, null, null, null, null, null, null, null).first()

        assertThat(aliceRows.map { it.id }).containsExactly("a1", "a2")
        assertThat(aliceRows.none { it.ownerUid == bob }).isTrue()
    }

    @Test
    fun `finding a transaction by id requires the matching owner`() = runTest {
        val dao = database.transactionDao()
        dao.upsert(transaction("b1", bob, 900))

        assertThat(dao.find(alice, "b1")).isNull()
        assertThat(dao.find(bob, "b1")).isNotNull()
    }

    @Test
    fun `deleting with the wrong owner removes nothing`() = runTest {
        val dao = database.transactionDao()
        dao.upsert(transaction("b1", bob, 900))

        dao.delete(alice, "b1")

        assertThat(dao.find(bob, "b1")).isNotNull()
    }

    @Test
    fun `allForOwner is strictly scoped`() = runTest {
        val dao = database.transactionDao()
        dao.upsertAll(listOf(transaction("a1", alice, 100), transaction("b1", bob, 900)))

        assertThat(dao.allForOwner(alice).map { it.id }).containsExactly("a1")
        assertThat(dao.allForOwner(bob).map { it.id }).containsExactly("b1")
        assertThat(dao.countForOwner(alice)).isEqualTo(1)
    }

    @Test
    fun `accounts are scoped per owner`() = runTest {
        val dao = database.accountDao()
        dao.upsert(account("acc-alice", alice))
        dao.upsert(account("acc-bob", bob))

        val aliceAccounts = dao.observeForOwner(alice).first()

        assertThat(aliceAccounts.map { it.id }).containsExactly("acc-alice")
        assertThat(dao.find(alice, "acc-bob")).isNull()
    }

    @Test
    fun `type filters still respect ownership`() = runTest {
        val dao = database.transactionDao()
        dao.upsertAll(
            listOf(
                transaction("a-expense", alice, 100, TransactionType.EXPENSE),
                transaction("a-income", alice, 500, TransactionType.INCOME, categoryId = "salary"),
                transaction("b-expense", bob, 900, TransactionType.EXPENSE),
            )
        )

        val incomeRows = dao.observeFiltered(
            alice,
            TransactionType.INCOME.id,
            null, null, null, null, null, null, null,
        ).first()

        assertThat(incomeRows.map { it.id }).containsExactly("a-income")
    }

    @Test
    fun `keyword search cannot reach another owner's rows`() = runTest {
        val dao = database.transactionDao()
        dao.upsertAll(
            listOf(
                transaction("a1", alice, 100).copy(note = "groceries"),
                transaction("b1", bob, 900).copy(note = "groceries secret"),
            )
        )

        val matches = dao.observeFiltered(alice, null, null, null, null, null, null, null, "groceries").first()

        assertThat(matches.map { it.id }).containsExactly("a1")
    }

    @Test
    fun `wiping personal data for one owner leaves the other untouched`() = runTest {
        val transactionDao = database.transactionDao()
        val accountDao = database.accountDao()
        transactionDao.upsertAll(listOf(transaction("a1", alice, 100), transaction("b1", bob, 900)))
        accountDao.upsert(account("acc-alice", alice))
        accountDao.upsert(account("acc-bob", bob))

        database.maintenanceDao().wipePersonalData(alice)

        assertThat(transactionDao.allForOwner(alice)).isEmpty()
        assertThat(accountDao.observeForOwner(alice).first()).isEmpty()
        assertThat(transactionDao.allForOwner(bob)).hasSize(1)
        assertThat(accountDao.observeForOwner(bob).first()).hasSize(1)
    }

    @Test
    fun `attachment id lists survive the round trip through the converter`() = runTest {
        val dao = database.transactionDao()
        dao.upsert(transaction("a1", alice, 100).copy(attachmentIds = listOf("att-1", "att-2")))

        val loaded = dao.find(alice, "a1")

        assertThat(loaded?.attachmentIds).containsExactly("att-1", "att-2").inOrder()
    }

    @Test
    fun `the unique client id index makes an offline replay idempotent`() = runTest {
        val dao = database.transactionDao()
        val original = transaction("a1", alice, 100)
        dao.upsert(original)

        // A replayed offline write carries the same clientId but a freshly generated row id.
        dao.upsert(original.copy(id = "a1-retry"))

        // The unique index on clientId means the replay replaces the row rather than duplicating it,
        // so a transaction can never be posted twice. This is enforced by the schema itself, not by
        // application logic that a future refactor could bypass.
        assertThat(dao.countForOwner(alice)).isEqualTo(1)
        assertThat(dao.findByClientId(original.clientId)?.id).isEqualTo("a1-retry")
    }
}
