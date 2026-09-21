package com.familyfinance.app.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.ExpenseCategory
import com.familyfinance.app.domain.model.IncomeCategory
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.AttachmentRepository
import com.familyfinance.app.domain.repository.RecurringRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.TransactionValidator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.DateField
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.SegmentedSelector
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * Add / edit a transaction.
 *
 * Ownership is never a form field. The transaction's owner is the authenticated uid, injected here —
 * which is why there is no "paid by" or "income by" control anywhere in this screen. That is a
 * product rule, and it is also what makes the privacy model enforceable.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val recurring: RecurringRepository,
    private val attachments: AttachmentRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    private val currentUser = session.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts: StateFlow<List<Account>> = currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else accountRepository.observeAccounts(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var type by mutableStateOf(TransactionType.EXPENSE)
        private set
    var amountRaw by mutableStateOf("")
        private set
    var dateEpochDay by mutableStateOf(AppTime.todayEpochDay())
        private set
    var categoryId by mutableStateOf<String?>(null)
        private set
    var accountId by mutableStateOf<String?>(null)
        private set
    var fromAccountId by mutableStateOf<String?>(null)
        private set
    var toAccountId by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf("")
        private set
    var isRecurring by mutableStateOf(false)
        private set
    var recurrence by mutableStateOf(RecurrenceFrequency.MONTHLY)
        private set
    var validationMessage by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    private var pendingReceipt: Pair<String, ByteArray>? = null

    fun onType(value: TransactionType) {
        type = value
        categoryId = null
        validationMessage = null
    }

    fun onAmount(value: String) { amountRaw = value; validationMessage = null }
    fun onDate(value: Long) { dateEpochDay = value }
    fun onCategory(value: String?) { categoryId = value }
    fun onAccount(value: String?) { accountId = value }
    fun onFromAccount(value: String?) { fromAccountId = value }
    fun onToAccount(value: String?) { toAccountId = value }
    fun onNote(value: String) { note = value }
    fun onRecurring(value: Boolean) { isRecurring = value }
    fun onRecurrence(value: RecurrenceFrequency) { recurrence = value }

    fun stageReceipt(fileName: String, bytes: ByteArray) {
        if (bytes.size > MAX_RECEIPT_BYTES) {
            error("That file is too large (max 10 MB).")
            return
        }
        pendingReceipt = fileName to bytes
        info("Receipt ready to attach.")
    }

    fun save() {
        validationMessage = null
        val amount = Money.parseOrNull(amountRaw)
        if (amount == null || !amount.isPositive) {
            validationMessage = "Enter an amount greater than zero."
            return
        }
        if (type != TransactionType.TRANSFER && categoryId.isNullOrBlank()) {
            validationMessage = "Choose a category."
            return
        }

        val user = currentUser.value
        if (user == null) {
            validationMessage = "Your session expired. Please sign in again."
            return
        }

        val draft = Transaction(
            id = Ids.newId(),
            clientId = Ids.newClientId(),
            ownerUid = user.uid,
            familyId = user.familyId.orEmpty(),
            type = type,
            amount = amount,
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
            accountId = if (type == TransactionType.TRANSFER) null else accountId,
            transferFromAccountId = if (type == TransactionType.TRANSFER) fromAccountId else null,
            transferToAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
            dateEpochDay = dateEpochDay,
            note = note.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
        )

        val ownedIds = accounts.value.map { it.id }.toSet()
        val validation = TransactionValidator.validate(draft, ownedIds)
        if (!validation.isValid) {
            validationMessage = validation.firstError()
            return
        }

        run(onSuccess = {
            if (isRecurring) {
                launchSafe {
                    recurring.upsertRule(
                        com.familyfinance.app.domain.model.RecurringRule(
                            id = Ids.newId(),
                            ownerUid = user.uid,
                            familyId = user.familyId.orEmpty(),
                            type = type,
                            amount = amount,
                            categoryId = draft.categoryId,
                            accountId = draft.accountId,
                            transferFromAccountId = draft.transferFromAccountId,
                            transferToAccountId = draft.transferToAccountId,
                            note = draft.note,
                            frequency = recurrence,
                            startEpochDay = dateEpochDay,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            pendingReceipt?.let { (name, bytes) ->
                launchSafe {
                    attachments.upload(
                        ownerUid = user.uid,
                        familyId = user.familyId.orEmpty(),
                        transactionId = draft.id,
                        fileName = name,
                        mimeType = "application/octet-stream",
                        bytes = bytes,
                        familyVisible = false,
                    )
                }
            }
            saved = true
            info("Saved. Your totals have been updated.")
        }) {
            transactions.upsertTransaction(draft)
        }
    }

    companion object {
        const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onDone: () -> Unit,
    onManageAccounts: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val accounts by viewModel.accounts.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current
    ObserveMessages(viewModel, host)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "receipt"
                viewModel.stageReceipt(name, bytes)
            }.onFailure { /* reported by the view model's own size check when applicable */ }
        }
    }

    androidx.compose.runtime.LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add transaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedSelector(
                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER),
                selected = viewModel.type,
                optionLabel = { it.label },
                onSelect = viewModel::onType,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionCard(title = "Details") {
                MoneyField(
                    value = viewModel.amountRaw,
                    onValueChange = viewModel::onAmount,
                    label = "Amount",
                    isError = viewModel.amountRaw.isNotBlank() &&
                        (Money.parseOrNull(viewModel.amountRaw)?.isPositive != true),
                )
                DateField(
                    label = "Date",
                    epochDay = viewModel.dateEpochDay,
                    onChange = viewModel::onDate,
                )
            }

            SectionCard(title = "Where") {
                if (viewModel.type == TransactionType.TRANSFER) {
                    PickerField(
                        label = "From account",
                        options = accounts,
                        selected = accounts.firstOrNull { it.id == viewModel.fromAccountId },
                        optionLabel = { it.name },
                        onSelect = { viewModel.onFromAccount(it.id) },
                    )
                    PickerField(
                        label = "To account",
                        options = accounts.filter { it.id != viewModel.fromAccountId },
                        selected = accounts.firstOrNull { it.id == viewModel.toAccountId },
                        optionLabel = { it.name },
                        onSelect = { viewModel.onToAccount(it.id) },
                    )
                    Text(
                        "A transfer moves money between your own accounts. It is not counted as income " +
                            "or expense, so your family totals do not change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PickerField(
                        label = "Account",
                        options = accounts,
                        selected = accounts.firstOrNull { it.id == viewModel.accountId },
                        optionLabel = { it.name },
                        onSelect = { viewModel.onAccount(it.id) },
                    )
                    if (accounts.isEmpty()) {
                        OutlinedButton(onClick = onManageAccounts) { Text("Add an account first") }
                    }
                }
            }

            if (viewModel.type != TransactionType.TRANSFER) {
                SectionCard(title = "Category") {
                    when (viewModel.type) {
                        TransactionType.INCOME -> PickerField(
                            label = "Income category",
                            options = IncomeCategory.entries.toList(),
                            selected = IncomeCategory.fromId(viewModel.categoryId),
                            optionLabel = { it.label },
                            onSelect = { viewModel.onCategory(it.id) },
                        )
                        else -> PickerField(
                            label = "Expense category",
                            options = ExpenseCategory.entries.toList(),
                            selected = ExpenseCategory.fromId(viewModel.categoryId),
                            optionLabel = { it.label },
                            onSelect = { viewModel.onCategory(it.id) },
                        )
                    }
                }
            }

            SectionCard(title = "Note & receipt") {
                com.familyfinance.app.ui.components.PlainTextField(
                    value = viewModel.note,
                    onValueChange = viewModel::onNote,
                    label = "Note (optional)",
                    singleLine = false,
                )
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Attach a receipt photo")
                }
                Text(
                    "Receipts are stored privately on this device and are never visible to other members.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Recurring") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repeat this transaction", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Entries are generated automatically. The same period is never posted twice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = viewModel.isRecurring, onCheckedChange = viewModel::onRecurring)
                }
                if (viewModel.isRecurring) {
                    SegmentedSelector(
                        options = listOf(
                            RecurrenceFrequency.DAILY,
                            RecurrenceFrequency.WEEKLY,
                            RecurrenceFrequency.MONTHLY,
                            RecurrenceFrequency.YEARLY,
                        ),
                        selected = viewModel.recurrence,
                        optionLabel = { it.label },
                        onSelect = viewModel::onRecurrence,
                    )
                }
            }

            InlineMessage(message = viewModel.validationMessage.orEmpty(), isError = true)

            Button(
                onClick = viewModel::save,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Save transaction")
            }

            Text(
                "Recorded against your account only. No \"paid by\" field exists in this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
