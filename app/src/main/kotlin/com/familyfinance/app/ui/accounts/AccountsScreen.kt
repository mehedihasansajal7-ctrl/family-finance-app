package com.familyfinance.app.ui.accounts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.AccountBalanceCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.MaskedIdentifierText
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.MoneyHeadline
import com.familyfinance.app.ui.components.MoneyText
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.Tag
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountWithBalance(val account: Account, val balance: Money)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    val items: StateFlow<List<AccountWithBalance>> = session.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                combine(
                    accounts.observeAccounts(uid),
                    transactions.observeTransactions(uid),
                ) { accountList, transactionList ->
                    AccountBalanceCalculator.balances(accountList, transactionList)
                        .map { AccountWithBalance(it.account, it.balance) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Money> = items
        .map { rows ->
            Money.sum(rows.filter { it.account.isActive }.map { it.balance })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Money.ZERO)

    fun delete(account: Account) {
        run { accounts.deleteAccount(account.ownerUid, account.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val rows by viewModel.items.collectAsState()
    val total by viewModel.total.collectAsState()
    ObserveMessages(viewModel, host)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAccount,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add account") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MoneyHeadline(
                            money = total,
                            label = "Total balance",
                            caption = "${rows.count { it.account.isActive }} active account(s)",
                        )
                    }
                }
            }

            item {
                Text(
                    "Only you can see these accounts. Other family members — including the owner — never " +
                        "see your balances, account numbers or wallets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(rows, key = { it.account.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    row.account.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Tag(row.account.type.label)
                            }
                            if (row.account.institution != null) {
                                Text(
                                    row.account.institution,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (row.account.last4 != null) {
                                MaskedIdentifierText(row.account.last4)
                            }
                            if (!row.account.isActive) {
                                Text(
                                    "Inactive",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(row.balance, style = MaterialTheme.typography.titleSmall)
                            Row {
                                IconButton(onClick = { onEditAccount(row.account.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit ${row.account.name}")
                                }
                                IconButton(onClick = { viewModel.delete(row.account) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${row.account.name}")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val audit: AuditRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    private var editingId: String? = null

    var name by mutableStateOf("")
        private set
    var type by mutableStateOf(AccountType.BANK)
        private set
    var institution by mutableStateOf("")
        private set
    var identifier by mutableStateOf("")
        private set
    var openingBalanceRaw by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set
    var isActive by mutableStateOf(true)
        private set
    var validationMessage by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    val isEditing: Boolean get() = editingId != null

    fun load(accountId: String?) {
        if (accountId.isNullOrBlank() || editingId == accountId) return
        editingId = accountId
        run(onSuccess = { result ->
            val account = result as? Account ?: return@run
            name = account.name
            type = account.type
            institution = account.institution.orEmpty()
            // The full identifier is only ever loaded for the owner's own editing session.
            identifier = account.identifier.orEmpty()
            openingBalanceRaw = account.openingBalance.toBigDecimal().toPlainString()
            notes = account.notes.orEmpty()
            isActive = account.isActive
        }) {
            val uid = session.currentUserOnce()?.uid
            if (uid == null) {
                com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            } else {
                accounts.getAccount(uid, accountId)
            }
        }
    }

    fun onName(value: String) { name = value }
    fun onType(value: AccountType) { type = value }
    fun onInstitution(value: String) { institution = value }
    fun onIdentifier(value: String) { identifier = value.filter { it.isDigit() }.take(19) }
    fun onOpeningBalance(value: String) { openingBalanceRaw = value }
    fun onNotes(value: String) { notes = value }
    fun onActive(value: Boolean) { isActive = value }

    fun save() {
        validationMessage = null
        if (name.isBlank()) {
            validationMessage = "Give the account a name."
            return
        }
        val opening = Money.parseOrNull(openingBalanceRaw.ifBlank { "0" })
        if (opening == null) {
            validationMessage = "Opening balance must be a valid amount."
            return
        }

        run(onSuccess = {
            saved = true
            info(if (isEditing) "Account updated." else "Account added.")
        }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            val now = System.currentTimeMillis()
            val account = Account(
                id = editingId ?: Ids.newId(),
                ownerUid = user.uid,
                familyId = user.familyId.orEmpty(),
                name = name.trim(),
                type = type,
                institution = institution.trim().takeIf { it.isNotBlank() },
                identifier = identifier.takeIf { it.isNotBlank() },
                // Only the last four digits are ever displayed, so only they are indexed for display.
                last4 = MoneyFormat.last4Of(identifier),
                openingBalance = opening,
                currencyCode = "BDT",
                isActive = isActive,
                notes = notes.trim().takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING,
            )
            val result = accounts.upsertAccount(account)
            if (result.isSuccess) {
                audit.record(
                    familyId = user.familyId,
                    actorUid = user.uid,
                    action = if (isEditing) "account_updated" else AuditRepository.ACTION_ACCOUNT_CREATED,
                    context = mapOf("accountType" to type.id),
                )
            }
            result
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    accountId: String?,
    onBack: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val busy by viewModel.busy.collectAsState()
    ObserveMessages(viewModel, host)

    androidx.compose.runtime.LaunchedEffect(accountId) { viewModel.load(accountId) }
    androidx.compose.runtime.LaunchedEffect(viewModel.saved) { if (viewModel.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit account" else "Add account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Account") {
                PlainTextField(value = viewModel.name, onValueChange = viewModel::onName, label = "Account name")
                PickerField(
                    label = "Type",
                    options = AccountType.entries.toList(),
                    selected = viewModel.type,
                    optionLabel = { it.label },
                    onSelect = viewModel::onType,
                )
                if (viewModel.type.hasInstitution) {
                    PlainTextField(
                        value = viewModel.institution,
                        onValueChange = viewModel::onInstitution,
                        label = "Bank / provider",
                    )
                }
            }

            SectionCard(
                title = "Identifier",
                subtitle = "Stored for your reference only and always shown masked.",
            ) {
                PlainTextField(
                    value = viewModel.identifier,
                    onValueChange = viewModel::onIdentifier,
                    label = "Account / mobile number (optional)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    supportingText = "Only the last 4 digits are ever displayed.",
                )
                MaskedIdentifierText(MoneyFormat.last4Of(viewModel.identifier))
                Text(
                    "Never enter a PIN, OTP, CVV, or banking password. This app does not store them and " +
                        "will reject them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionCard(title = "Balance") {
                MoneyField(
                    value = viewModel.openingBalanceRaw,
                    onValueChange = viewModel::onOpeningBalance,
                    label = "Opening balance",
                )
                Text(
                    "Current balance = opening balance + income + transfers in − expense − transfers out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "More") {
                PlainTextField(
                    value = viewModel.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Notes (optional)",
                    singleLine = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Active", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = viewModel.isActive, onCheckedChange = viewModel::onActive)
                }
            }

            InlineMessage(message = viewModel.validationMessage.orEmpty(), isError = true)

            Button(
                onClick = viewModel::save,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (viewModel.isEditing) "Save changes" else "Add account")
            }
        }
    }
}
