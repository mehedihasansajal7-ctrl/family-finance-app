package com.familyfinance.app.domain.security

import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.Attachment
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.Transaction

/**
 * The identity a decision is made for. Built from the authenticated session only — never from a
 * client-supplied uid.
 */
data class Viewer(
    val uid: String,
    val familyId: String?,
    val role: Role,
    val status: MemberStatus,
) {
    val isActive: Boolean get() = status == MemberStatus.ACTIVE && !familyId.isNullOrBlank()
}

/**
 * The authoritative privacy policy, expressed once as pure Kotlin.
 *
 * This exact decision table is mirrored in `backend/firestore.rules` (enforced by Firestore itself)
 * and re-checked in the repository layer. The UI only ever hides things; it is never the control.
 *
 * ## The rule
 * **Family-level data is shared with active members of that family. Personal financial data is
 * readable and writable by its owner and by nobody else — not by admins, not by the family owner.**
 */
object FamilyAccessPolicy {

    // ---------------------------------------------------------------- personal financial data

    /**
     * Personal financial records (accounts, transactions, loans, savings goals, attachments,
     * personal reports) are owner-only. Family role is deliberately *not* consulted.
     *
     * Note that **active membership is not required either**. Your ledger is yours: suspending or
     * removing someone from a family withdraws their access to the *family* (aggregate, budgets,
     * goals, bills, member directory) but must never lock a person out of their own financial
     * history. This mirrors the Firestore rule `match /users/{uid}/{document=**}` exactly, which
     * grants access on `request.auth.uid == uid` and nothing else.
     */
    fun canReadOwnFinancialData(viewer: Viewer, ownerUid: String): Boolean =
        viewer.uid == ownerUid

    fun canWriteOwnFinancialData(viewer: Viewer, ownerUid: String): Boolean =
        viewer.uid == ownerUid

    fun canReadTransaction(viewer: Viewer, transaction: Transaction): Boolean =
        canReadOwnFinancialData(viewer, transaction.ownerUid)

    fun canWriteTransaction(viewer: Viewer, transaction: Transaction): Boolean =
        canWriteOwnFinancialData(viewer, transaction.ownerUid)

    fun canReadAccount(viewer: Viewer, account: Account): Boolean =
        canReadOwnFinancialData(viewer, account.ownerUid)

    fun canWriteAccount(viewer: Viewer, account: Account): Boolean =
        canWriteOwnFinancialData(viewer, account.ownerUid)

    /**
     * An attachment is readable by its owner, or by any active family member **only when the owner
     * explicitly marked it family-visible** (e.g. a shared receipt). Default is private.
     */
    fun canReadAttachment(viewer: Viewer, attachment: Attachment): Boolean {
        if (!viewer.isActive) return false
        if (viewer.uid == attachment.ownerUid) return true
        return attachment.isFamilyVisible && viewer.familyId == attachment.familyId
    }

    /**
     * The private member profile is readable by the owner always, and by other family members only
     * when the owner enabled [MemberProfile.shareProfileWithFamily]. Financial fields are never part
     * of this document.
     */
    fun canReadMemberProfile(viewer: Viewer, profile: MemberProfile): Boolean {
        if (!viewer.isActive) return false
        if (viewer.uid == profile.uid) return true
        return profile.shareProfileWithFamily && viewer.familyId == profile.familyId
    }

    // ---------------------------------------------------------------- family-level (shared) data

    fun canReadFamilyAggregate(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId

    fun canReadFamilyBudget(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId

    fun canReadFamilyGoal(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId

    fun canReadFamilyBill(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId

    fun canReadFamilyMembers(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId

    // ---------------------------------------------------------------- family administration

    fun canManageFamily(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId && viewer.role.canManageFamily

    fun canInviteMembers(viewer: Viewer, familyId: String): Boolean = canManageFamily(viewer, familyId)

    fun canManageBudgets(viewer: Viewer, familyId: String): Boolean = canManageFamily(viewer, familyId)

    fun canManageGoals(viewer: Viewer, familyId: String): Boolean = canManageFamily(viewer, familyId)

    fun canManageBills(viewer: Viewer, familyId: String): Boolean = canManageFamily(viewer, familyId)

    /**
     * A member may always leave. An admin may remove another member, but **nobody may remove the
     * family owner**, and an admin may not remove a peer admin (only the owner may).
     */
    fun canRemoveMember(viewer: Viewer, familyId: String, targetUid: String, targetRole: Role, ownerUid: String): Boolean {
        if (!viewer.isActive || viewer.familyId != familyId) return false
        if (targetUid == ownerUid) return false
        if (viewer.uid == targetUid) return true // leaving voluntarily
        if (viewer.role == Role.OWNER) return true
        if (viewer.role == Role.ADMIN) return targetRole == Role.MEMBER
        return false
    }

    fun canChangeRole(viewer: Viewer, familyId: String, targetUid: String, ownerUid: String): Boolean {
        if (!viewer.isActive || viewer.familyId != familyId) return false
        if (viewer.role != Role.OWNER) return false
        return targetUid != ownerUid
    }

    /**
     * A member may contribute to a family goal, but a contribution is recorded as a **family-level
     * total**; the individual amount is never published. See docs/SECURITY.md.
     */
    fun canContributeToFamilyGoal(viewer: Viewer, familyId: String): Boolean =
        viewer.isActive && viewer.familyId == familyId
}
