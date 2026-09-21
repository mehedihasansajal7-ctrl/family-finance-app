package com.familyfinance.app.domain.security

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.Attachment
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The privacy policy, asserted as a matrix.
 *
 * This is the most important test file in the project. It encodes the product rule that
 * **no member — including the family owner and admins — may read another member's financial data**,
 * and it is the client-side twin of `backend/firestore.rules`. If this test and the rules ever
 * disagree, the rules are wrong; but neither may be relaxed without failing here first.
 */
class FamilyAccessPolicyTest {

    private val familyA = "family-a"
    private val familyB = "family-b"

    private fun viewer(
        uid: String,
        familyId: String? = familyA,
        role: Role = Role.MEMBER,
        status: MemberStatus = MemberStatus.ACTIVE,
    ) = Viewer(uid, familyId, role, status)

    private val alice = viewer("uid-alice")
    private val bob = viewer("uid-bob")
    private val bobAdmin = viewer("uid-bob", role = Role.ADMIN)
    private val owner = viewer("uid-owner", role = Role.OWNER)
    private val outsider = viewer("uid-outsider", familyId = familyB)
    private val suspended = viewer("uid-bob", status = MemberStatus.SUSPENDED)

    private fun account(ownerUid: String, familyId: String = familyA) = Account(
        id = "acc-1",
        ownerUid = ownerUid,
        familyId = familyId,
        name = "DBBL Savings",
        type = AccountType.BANK,
        last4 = "3456",
        openingBalance = Money.ofBdt(100_000),
    )

    private fun transaction(ownerUid: String, familyId: String = familyA) = Transaction(
        id = "tx-1",
        clientId = "c-1",
        ownerUid = ownerUid,
        familyId = familyId,
        type = TransactionType.INCOME,
        amount = Money.ofBdt(70_000),
        categoryId = "salary",
        accountId = "acc-1",
        dateEpochDay = 20_000L,
    )

    private fun profile(uid: String, shared: Boolean = true, familyId: String = familyA) = MemberProfile(
        uid = uid,
        familyId = familyId,
        fullName = "Member $uid",
        shareProfileWithFamily = shared,
    )

    // ------------------------------------------------------------------ own data

    @Test
    fun `a member can read and write their own financial data`() {
        assertThat(FamilyAccessPolicy.canReadOwnFinancialData(alice, "uid-alice")).isTrue()
        assertThat(FamilyAccessPolicy.canWriteOwnFinancialData(alice, "uid-alice")).isTrue()
        assertThat(FamilyAccessPolicy.canReadAccount(alice, account("uid-alice"))).isTrue()
        assertThat(FamilyAccessPolicy.canWriteAccount(alice, account("uid-alice"))).isTrue()
        assertThat(FamilyAccessPolicy.canReadTransaction(alice, transaction("uid-alice"))).isTrue()
        assertThat(FamilyAccessPolicy.canWriteTransaction(alice, transaction("uid-alice"))).isTrue()
    }

    // ------------------------------------------------------------------ cross-member denial

    @Test
    fun `a member cannot read another member's account, transaction or balance`() {
        assertThat(FamilyAccessPolicy.canReadAccount(alice, account("uid-bob"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadTransaction(alice, transaction("uid-bob"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadOwnFinancialData(alice, "uid-bob")).isFalse()
    }

    @Test
    fun `a member cannot write or delete another member's data`() {
        assertThat(FamilyAccessPolicy.canWriteAccount(alice, account("uid-bob"))).isFalse()
        assertThat(FamilyAccessPolicy.canWriteTransaction(alice, transaction("uid-bob"))).isFalse()
        assertThat(FamilyAccessPolicy.canWriteOwnFinancialData(alice, "uid-bob")).isFalse()
    }

    @Test
    fun `an admin still cannot read another member's financial data`() {
        // This is the rule people most often get wrong: family administration is not financial access.
        assertThat(FamilyAccessPolicy.canReadAccount(bobAdmin, account("uid-alice"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadTransaction(bobAdmin, transaction("uid-alice"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadOwnFinancialData(bobAdmin, "uid-alice")).isFalse()
    }

    @Test
    fun `the family owner cannot read another member's financial data`() {
        assertThat(FamilyAccessPolicy.canReadAccount(owner, account("uid-alice"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadTransaction(owner, transaction("uid-alice"))).isFalse()
    }

    // ------------------------------------------------------------------ cross-family denial

    @Test
    fun `a member of another family cannot read this family's data`() {
        assertThat(FamilyAccessPolicy.canReadAccount(outsider, account("uid-alice"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadTransaction(outsider, transaction("uid-alice"))).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyAggregate(outsider, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyBudget(outsider, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyGoal(outsider, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyBill(outsider, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyMembers(outsider, familyA)).isFalse()
    }

    @Test
    fun `an owner of one family cannot administer another family`() {
        assertThat(FamilyAccessPolicy.canManageFamily(owner, familyB)).isFalse()
        assertThat(FamilyAccessPolicy.canInviteMembers(owner, familyB)).isFalse()
    }

    @Test
    fun `your own record stays yours even if it names a family you have left`() {
        // Ownership is the invariant for personal records, not current family membership — leaving a
        // family must never lock you out of your own ledger.
        assertThat(FamilyAccessPolicy.canReadTransaction(alice, transaction("uid-alice", familyB))).isTrue()
        assertThat(FamilyAccessPolicy.canReadAccount(alice, account("uid-alice", familyB))).isTrue()
    }

    @Test
    fun `a record owned by someone else is denied even inside your own family`() {
        assertThat(FamilyAccessPolicy.canReadTransaction(alice, transaction("uid-bob", familyA))).isFalse()
        assertThat(FamilyAccessPolicy.canReadAccount(alice, account("uid-bob", familyA))).isFalse()
    }

    // ------------------------------------------------------------------ inactive members

    @Test
    fun `suspension withdraws family access but never access to your own records`() {
        // The same rule that keeps a ledger private from everyone else keeps it available to its owner.
        assertThat(FamilyAccessPolicy.canReadOwnFinancialData(suspended, "uid-bob")).isTrue()
        assertThat(FamilyAccessPolicy.canWriteOwnFinancialData(suspended, "uid-bob")).isTrue()

        assertThat(FamilyAccessPolicy.canReadFamilyAggregate(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyBudget(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyGoal(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyBill(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadFamilyMembers(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canManageFamily(suspended, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canContributeToFamilyGoal(suspended, familyA)).isFalse()
    }

    @Test
    fun `a member with no family has no family access but still owns their data`() {
        val homeless = viewer("uid-nobody", familyId = null)
        assertThat(FamilyAccessPolicy.canReadFamilyAggregate(homeless, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canReadOwnFinancialData(homeless, "uid-nobody")).isTrue()
    }

    // ------------------------------------------------------------------ shared family data

    @Test
    fun `every active member can read the shared family aggregate and family records`() {
        listOf(alice, bob, bobAdmin, owner).forEach { member ->
            assertThat(FamilyAccessPolicy.canReadFamilyAggregate(member, familyA)).isTrue()
            assertThat(FamilyAccessPolicy.canReadFamilyBudget(member, familyA)).isTrue()
            assertThat(FamilyAccessPolicy.canReadFamilyGoal(member, familyA)).isTrue()
            assertThat(FamilyAccessPolicy.canReadFamilyBill(member, familyA)).isTrue()
            assertThat(FamilyAccessPolicy.canReadFamilyMembers(member, familyA)).isTrue()
        }
    }

    @Test
    fun `only the owner and admins can manage the family`() {
        assertThat(FamilyAccessPolicy.canManageFamily(owner, familyA)).isTrue()
        assertThat(FamilyAccessPolicy.canManageFamily(bobAdmin, familyA)).isTrue()
        assertThat(FamilyAccessPolicy.canManageFamily(bob, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canInviteMembers(bob, familyA)).isFalse()
        assertThat(FamilyAccessPolicy.canManageBudgets(bob, familyA)).isFalse()
    }

    @Test
    fun `any active member may contribute to a family goal`() {
        assertThat(FamilyAccessPolicy.canContributeToFamilyGoal(bob, familyA)).isTrue()
        assertThat(FamilyAccessPolicy.canContributeToFamilyGoal(outsider, familyA)).isFalse()
    }

    // ------------------------------------------------------------------ profiles & attachments

    @Test
    fun `a member always reads their own profile`() {
        assertThat(FamilyAccessPolicy.canReadMemberProfile(alice, profile("uid-alice", shared = false))).isTrue()
    }

    @Test
    fun `a profile is shared with family only when the owner opted in`() {
        assertThat(FamilyAccessPolicy.canReadMemberProfile(bob, profile("uid-alice", shared = true))).isTrue()
        assertThat(FamilyAccessPolicy.canReadMemberProfile(bob, profile("uid-alice", shared = false))).isFalse()
        assertThat(FamilyAccessPolicy.canReadMemberProfile(outsider, profile("uid-alice", shared = true))).isFalse()
    }

    @Test
    fun `an attachment is private unless explicitly marked family-visible`() {
        val private = Attachment(
            id = "att-1", ownerUid = "uid-alice", familyId = familyA,
            fileName = "receipt.jpg", mimeType = "image/jpeg", sizeBytes = 100,
            storagePath = "attachments/uid-alice/att-1", isFamilyVisible = false,
        )
        val shared = private.copy(isFamilyVisible = true)

        assertThat(FamilyAccessPolicy.canReadAttachment(alice, private)).isTrue()
        assertThat(FamilyAccessPolicy.canReadAttachment(bob, private)).isFalse()
        assertThat(FamilyAccessPolicy.canReadAttachment(bob, shared)).isTrue()
        assertThat(FamilyAccessPolicy.canReadAttachment(outsider, shared)).isFalse()
    }

    // ------------------------------------------------------------------ membership management

    @Test
    fun `nobody can remove the family owner`() {
        assertThat(
            FamilyAccessPolicy.canRemoveMember(owner, familyA, "uid-owner", Role.OWNER, "uid-owner")
        ).isFalse()
        assertThat(
            FamilyAccessPolicy.canRemoveMember(bobAdmin, familyA, "uid-owner", Role.OWNER, "uid-owner")
        ).isFalse()
        assertThat(
            FamilyAccessPolicy.canRemoveMember(owner, familyA, "uid-owner", Role.MEMBER, "uid-owner")
        ).isFalse()
    }

    @Test
    fun `a member may always leave voluntarily`() {
        assertThat(
            FamilyAccessPolicy.canRemoveMember(bob, familyA, "uid-bob", Role.MEMBER, "uid-owner")
        ).isTrue()
    }

    @Test
    fun `an admin may remove a member but not a peer admin`() {
        assertThat(
            FamilyAccessPolicy.canRemoveMember(bobAdmin, familyA, "uid-carol", Role.MEMBER, "uid-owner")
        ).isTrue()
        assertThat(
            FamilyAccessPolicy.canRemoveMember(bobAdmin, familyA, "uid-dave", Role.ADMIN, "uid-owner")
        ).isFalse()
    }

    @Test
    fun `only the owner may change roles`() {
        assertThat(FamilyAccessPolicy.canChangeRole(owner, familyA, "uid-bob", "uid-owner")).isTrue()
        assertThat(FamilyAccessPolicy.canChangeRole(bobAdmin, familyA, "uid-carol", "uid-owner")).isFalse()
        assertThat(FamilyAccessPolicy.canChangeRole(owner, familyA, "uid-owner", "uid-owner")).isFalse()
    }

    @Test
    fun `an outsider cannot remove anyone from this family`() {
        assertThat(
            FamilyAccessPolicy.canRemoveMember(outsider, familyA, "uid-bob", Role.MEMBER, "uid-owner")
        ).isFalse()
    }
}
