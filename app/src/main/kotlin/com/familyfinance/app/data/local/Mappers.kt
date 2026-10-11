package com.familyfinance.app.data.local

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.data.local.entity.AccountEntity
import com.familyfinance.app.data.local.entity.AttachmentEntity
import com.familyfinance.app.data.local.entity.AuditLogEntity
import com.familyfinance.app.data.local.entity.FamilyBillEntity
import com.familyfinance.app.data.local.entity.FamilyChatMessageEntity
import com.familyfinance.app.data.local.entity.ChatReadEntity
import com.familyfinance.app.data.local.entity.ExpenseSplitEntity
import com.familyfinance.app.data.local.entity.SettlementEntity
import com.familyfinance.app.data.local.entity.FamilyBudgetEntity
import com.familyfinance.app.data.local.entity.FamilyEntity
import com.familyfinance.app.data.local.entity.FamilyGoalEntity
import com.familyfinance.app.data.local.entity.InvitationEntity
import com.familyfinance.app.data.local.entity.MemberProfileEntity
import com.familyfinance.app.data.local.entity.NotificationEntity
import com.familyfinance.app.data.local.entity.PersonalLoanEntity
import com.familyfinance.app.data.local.entity.RecurringRuleEntity
import com.familyfinance.app.data.local.entity.SavingsGoalEntity
import com.familyfinance.app.data.local.entity.TransactionEntity
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.AppNotification
import com.familyfinance.app.domain.model.Attachment
import com.familyfinance.app.domain.model.AuditLog
import com.familyfinance.app.domain.model.BillStatus
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyChatMessage
import com.familyfinance.app.domain.model.ChatReadState
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilySettlement
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.FamilyInvitation
import com.familyfinance.app.domain.model.FamilyMemberView
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.IncomeCategory
import com.familyfinance.app.domain.model.LoanDirection
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType

/**
 * Entity <-> domain mapping.
 *
 * Mapping is explicit (rather than reflection-based) so that adding a field to a domain model
 * forces a deliberate decision about how — and whether — it is persisted. That matters most for
 * `ownerUid`, which every personal mapper must carry through unchanged.
 */

// ------------------------------------------------------------------ accounts

fun AccountEntity.toDomain() = Account(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    name = name,
    type = AccountType.fromId(typeId),
    institution = institution,
    identifier = identifier,
    last4 = last4,
    openingBalance = Money.ofMinor(openingBalanceMinor),
    currencyCode = currencyCode,
    isActive = isActive,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = SyncState.fromId(syncStateId),
)

fun Account.toEntity() = AccountEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    name = name,
    typeId = type.id,
    institution = institution,
    identifier = identifier,
    last4 = last4,
    openingBalanceMinor = openingBalance.minor,
    currencyCode = currencyCode,
    isActive = isActive,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStateId = syncState.id,
)

// ------------------------------------------------------------------ transactions

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    clientId = clientId,
    ownerUid = ownerUid,
    familyId = familyId,
    type = TransactionType.fromId(typeId) ?: TransactionType.EXPENSE,
    amount = Money.ofMinor(amountMinor),
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    accountId = accountId,
    transferFromAccountId = transferFromAccountId,
    transferToAccountId = transferToAccountId,
    dateEpochDay = dateEpochDay,
    note = note,
    attachmentIds = attachmentIds,
    recurringRuleId = recurringRuleId,
    recurrenceOccurrenceEpochDay = recurrenceOccurrenceEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = SyncState.fromId(syncStateId),
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    clientId = clientId,
    ownerUid = ownerUid,
    familyId = familyId,
    typeId = type.id,
    amountMinor = amount.minor,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    accountId = accountId,
    transferFromAccountId = transferFromAccountId,
    transferToAccountId = transferToAccountId,
    dateEpochDay = dateEpochDay,
    note = note,
    attachmentIds = attachmentIds,
    recurringRuleId = recurringRuleId,
    recurrenceOccurrenceEpochDay = recurrenceOccurrenceEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStateId = syncState.id,
)

// ------------------------------------------------------------------ recurring rules

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    type = TransactionType.fromId(typeId) ?: TransactionType.EXPENSE,
    amount = Money.ofMinor(amountMinor),
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    accountId = accountId,
    transferFromAccountId = transferFromAccountId,
    transferToAccountId = transferToAccountId,
    note = note,
    frequency = RecurrenceFrequency.fromId(frequencyId) ?: RecurrenceFrequency.MONTHLY,
    customIntervalDays = customIntervalDays,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    lastGeneratedEpochDay = lastGeneratedEpochDay,
    isActive = isActive,
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    typeId = type.id,
    amountMinor = amount.minor,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    accountId = accountId,
    transferFromAccountId = transferFromAccountId,
    transferToAccountId = transferToAccountId,
    note = note,
    frequencyId = frequency.id,
    customIntervalDays = customIntervalDays,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    lastGeneratedEpochDay = lastGeneratedEpochDay,
    isActive = isActive,
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ------------------------------------------------------------------ family & members

fun FamilyChatMessageEntity.toDomain() = FamilyChatMessage(
    id = id,
    familyId = familyId,
    senderUid = senderUid,
    senderName = senderName,
    text = text,
    createdAt = createdAt,
    attachmentUrl = attachmentUrl,
    attachmentContentType = attachmentContentType,
    replyToId = replyToId,
    replyPreview = replyPreview,
    reactions = decodeReactions(reactionsJson),
)

fun FamilyChatMessage.toEntity() = FamilyChatMessageEntity(
    id = id,
    familyId = familyId,
    senderUid = senderUid,
    senderName = senderName,
    text = text,
    createdAt = createdAt,
    attachmentUrl = attachmentUrl,
    attachmentContentType = attachmentContentType,
    replyToId = replyToId,
    replyPreview = replyPreview,
    reactionsJson = encodeReactions(reactions),
)

fun ChatReadEntity.toDomain() = ChatReadState(familyId = familyId, uid = uid, lastReadAt = lastReadAt)

fun ChatReadState.toEntity() = ChatReadEntity(familyId = familyId, uid = uid, lastReadAt = lastReadAt)

private val reactionsCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
private val reactionsSerializer = MapSerializer(String.serializer(), String.serializer())

internal fun encodeReactions(reactions: Map<String, String>): String =
    reactionsCodec.encodeToString(reactionsSerializer, reactions)

internal fun decodeReactions(raw: String?): Map<String, String> =
    if (raw.isNullOrBlank()) emptyMap()
    else runCatching { reactionsCodec.decodeFromString(reactionsSerializer, raw) }.getOrDefault(emptyMap())

// ------------------------------------------------------------------ expense splits & settlements

private val sharesSerializer = MapSerializer(String.serializer(), Long.serializer())

private fun encodeShares(shares: Map<String, Long>): String =
    reactionsCodec.encodeToString(sharesSerializer, shares)

private fun decodeShares(raw: String?): Map<String, Long> =
    if (raw.isNullOrBlank()) emptyMap()
    else runCatching { reactionsCodec.decodeFromString(sharesSerializer, raw) }.getOrDefault(emptyMap())

fun ExpenseSplitEntity.toDomain() = FamilyExpenseSplit(
    id = id,
    familyId = familyId,
    title = title,
    total = Money.ofMinor(totalMinor),
    paidByUid = paidByUid,
    shares = decodeShares(sharesJson).mapValues { Money.ofMinor(it.value) },
    dateEpochDay = dateEpochDay,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyExpenseSplit.toEntity() = ExpenseSplitEntity(
    id = id,
    familyId = familyId,
    title = title,
    totalMinor = total.minor,
    paidByUid = paidByUid,
    sharesJson = encodeShares(shares.mapValues { it.value.minor }),
    dateEpochDay = dateEpochDay,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SettlementEntity.toDomain() = FamilySettlement(
    id = id,
    familyId = familyId,
    fromUid = fromUid,
    toUid = toUid,
    amount = Money.ofMinor(amountMinor),
    dateEpochDay = dateEpochDay,
    createdAt = createdAt,
)

fun FamilySettlement.toEntity() = SettlementEntity(
    id = id,
    familyId = familyId,
    fromUid = fromUid,
    toUid = toUid,
    amountMinor = amount.minor,
    dateEpochDay = dateEpochDay,
    createdAt = createdAt,
)
fun FamilyEntity.toDomain() = Family(
    id = id,
    name = name,
    photoUrl = photoUrl,
    currencyCode = currencyCode,
    timezone = timezone,
    ownerUid = ownerUid,
    createdAt = createdAt,
    netWorthEnabled = netWorthEnabled,
)

fun Family.toEntity() = FamilyEntity(
    id = id,
    name = name,
    photoUrl = photoUrl,
    currencyCode = currencyCode,
    timezone = timezone,
    ownerUid = ownerUid,
    createdAt = createdAt,
    netWorthEnabled = netWorthEnabled,
)

fun MemberProfileEntity.toDomain() = MemberProfile(
    uid = uid,
    familyId = familyId,
    fullName = fullName,
    photoUrl = photoUrl,
    dateOfBirthEpochDay = dateOfBirthEpochDay,
    mobile = mobile,
    email = email,
    relationship = relationship,
    occupation = occupation,
    notes = notes,
    shareProfileWithFamily = shareProfileWithFamily,
    updatedAt = updatedAt,
)

fun MemberProfileEntity.toMemberView() = FamilyMemberView(
    profile = toDomain(),
    role = Role.fromId(roleId),
    status = MemberStatus.fromId(statusId),
)

fun MemberProfile.toEntity(role: Role = Role.MEMBER, status: MemberStatus = MemberStatus.ACTIVE) =
    MemberProfileEntity(
        uid = uid,
        familyId = familyId,
        fullName = fullName,
        photoUrl = photoUrl,
        dateOfBirthEpochDay = dateOfBirthEpochDay,
        mobile = mobile,
        email = email,
        relationship = relationship,
        occupation = occupation,
        notes = notes,
        shareProfileWithFamily = shareProfileWithFamily,
        roleId = role.id,
        statusId = status.id,
        updatedAt = updatedAt,
    )

fun InvitationEntity.toDomain() = FamilyInvitation(
    id = id,
    familyId = familyId,
    code = code,
    createdByUid = createdByUid,
    createdAt = createdAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    maxUses = maxUses,
    usedCount = usedCount,
    invitedEmail = invitedEmail,
)

fun FamilyInvitation.toEntity() = InvitationEntity(
    id = id,
    familyId = familyId,
    code = code,
    createdByUid = createdByUid,
    createdAt = createdAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    maxUses = maxUses,
    usedCount = usedCount,
    invitedEmail = invitedEmail,
)

// ------------------------------------------------------------------ family budgets / goals / bills

fun FamilyBudgetEntity.toDomain() = FamilyBudget(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    periodEpochDayStart = periodEpochDayStart,
    periodEpochDayEnd = periodEpochDayEnd,
    limit = Money.ofMinor(limitMinor),
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyBudget.toEntity() = FamilyBudgetEntity(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    periodEpochDayStart = periodEpochDayStart,
    periodEpochDayEnd = periodEpochDayEnd,
    limitMinor = limit.minor,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyGoalEntity.toDomain() = FamilyGoal(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    targetAmount = Money.ofMinor(targetAmountMinor),
    currentAmount = Money.ofMinor(currentAmountMinor),
    targetEpochDay = targetEpochDay,
    description = description,
    status = GoalStatus.fromId(statusId),
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyGoal.toEntity() = FamilyGoalEntity(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    targetAmountMinor = targetAmount.minor,
    currentAmountMinor = currentAmount.minor,
    targetEpochDay = targetEpochDay,
    description = description,
    statusId = status.id,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyBillEntity.toDomain() = FamilyBill(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    expectedAmount = Money.ofMinor(expectedAmountMinor),
    dueEpochDay = dueEpochDay,
    frequency = RecurrenceFrequency.fromId(frequencyId) ?: RecurrenceFrequency.MONTHLY,
    status = BillStatus.fromId(statusId),
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    notes = notes,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FamilyBill.toEntity() = FamilyBillEntity(
    id = id,
    familyId = familyId,
    name = name,
    categoryId = categoryId,
    expectedAmountMinor = expectedAmount.minor,
    dueEpochDay = dueEpochDay,
    frequencyId = frequency.id,
    statusId = status.id,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    notes = notes,
    createdByUid = createdByUid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ------------------------------------------------------------------ personal savings & loans

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    name = name,
    targetAmount = Money.ofMinor(targetAmountMinor),
    currentAmount = Money.ofMinor(currentAmountMinor),
    targetEpochDay = targetEpochDay,
    note = note,
    status = GoalStatus.fromId(statusId),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SavingsGoal.toEntity() = SavingsGoalEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    name = name,
    targetAmountMinor = targetAmount.minor,
    currentAmountMinor = currentAmount.minor,
    targetEpochDay = targetEpochDay,
    note = note,
    statusId = status.id,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PersonalLoanEntity.toDomain() = PersonalLoan(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    direction = LoanDirection.fromId(directionId),
    counterparty = counterparty,
    principal = Money.ofMinor(principalMinor),
    interestRatePercent = interestRatePercent,
    installmentAmount = installmentAmountMinor?.let { Money.ofMinor(it) },
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    repaidAmount = Money.ofMinor(repaidAmountMinor),
    startEpochDay = startEpochDay,
    dueEpochDay = dueEpochDay,
    note = note,
    isClosed = isClosed,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PersonalLoan.toEntity() = PersonalLoanEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    directionId = direction.id,
    counterparty = counterparty,
    principalMinor = principal.minor,
    interestRatePercent = interestRatePercent,
    installmentAmountMinor = installmentAmount?.minor,
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    repaidAmountMinor = repaidAmount.minor,
    startEpochDay = startEpochDay,
    dueEpochDay = dueEpochDay,
    note = note,
    isClosed = isClosed,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ------------------------------------------------------------------ attachments, notifications, audit

fun AttachmentEntity.toDomain() = Attachment(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    transactionId = transactionId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    storagePath = storagePath,
    isFamilyVisible = isFamilyVisible,
    createdAt = createdAt,
)

fun Attachment.toEntity() = AttachmentEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    transactionId = transactionId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    storagePath = storagePath,
    isFamilyVisible = isFamilyVisible,
    createdAt = createdAt,
)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    categoryId = categoryId,
    title = title,
    body = body,
    relatedId = relatedId,
    createdAt = createdAt,
    readAt = readAt,
)

fun AppNotification.toEntity() = NotificationEntity(
    id = id,
    ownerUid = ownerUid,
    familyId = familyId,
    categoryId = categoryId,
    title = title,
    body = body,
    relatedId = relatedId,
    createdAt = createdAt,
    readAt = readAt,
)

fun AuditLogEntity.toDomain() = AuditLog(
    id = id,
    familyId = familyId,
    actorUid = actorUid,
    action = action,
    context = emptyMap(),
    createdAt = createdAt,
)

/** Resolves a category id to a human label for either income or expense, without leaking type. */
fun categoryLabel(categoryId: String?): String {
    if (categoryId.isNullOrBlank()) return "Uncategorised"
    return ExpenseCategoryLabel(categoryId) ?: IncomeCategoryLabel(categoryId) ?: categoryId
}

private fun ExpenseCategoryLabel(id: String): String? =
    com.familyfinance.app.domain.model.ExpenseCategory.entries.firstOrNull { it.id == id }?.label

private fun IncomeCategoryLabel(id: String): String? =
    IncomeCategory.entries.firstOrNull { it.id == id }?.label
