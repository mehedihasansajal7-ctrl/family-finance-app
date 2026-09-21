package com.familyfinance.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.repository.TransactionFilter
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.DateField
import com.familyfinance.app.ui.components.EmptyState
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.SignedMoneyText
import com.familyfinance.app.ui.components.Tag
import com.familyfinance.app.ui.export.Exporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val session: SessionManager,
    private val exporter: Exporter,
) : BaseViewModel() {

    private val filter = MutableStateFlow(TransactionFilter())

    val activeFilter: StateFlow<TransactionFilter> = filter

    val items: StateFlow<List<Transaction>> = kotlinx.coroutines.flow.combine(
        session.currentUser.map { it?.uid }.distinctUntilChanged(),
        filter,
    ) { uid, currentFilter -> uid to currentFilter }
        .flatMapLatest { (uid, currentFilter) ->
            if (uid == null) flowOf(emptyList()) else transactions.observeTransactions(uid, currentFilter)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun applyFilter(value: TransactionFilter) { filter.value = value }
    fun clearFilter() { filter.value = TransactionFilter() }

    fun delete(transaction: Transaction) {
        run(onSuccess = { info("Transaction deleted.") }) {
            transactions.deleteTransaction(transaction.ownerUid, transaction.id)
        }
    }

    /** Exports only the signed-in member's own rows — the repository query is already owner-scoped. */
    fun exportCsv() {
        run(onSuccess = { result ->
            val file = result as? java.io.File
            if (file != null) {
                info("Exported to ${file.name}")
                shareFile(file, "text/csv")
            }
        }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            exporter.exportPersonalCsv(
                ownerLabel = user.displayName.ifBlank { user.email },
                transactions = items.value,
                fromEpochDay = filter.value.fromEpochDay,
                toEpochDay = filter.value.toEpochDay,
            ).fold(
                onSuccess = { com.familyfinance.app.core.result.Outcome.Success(it) },
                onFailure = { com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.unknown("Export failed.", it)
                ) },
            )
        }
    }

    private fun shareFile(file: java.io.File, mimeType: String) {
        launchSafe {
            val intent = exporter.shareIntent(file, mimeType)
            pendingShare.value = intent
        }
    }

    private val pendingShare = MutableStateFlow<android.content.Intent?>(null)
    val shareIntent: StateFlow<android.content.Intent?> = pendingShare

    fun consumeShare() { pendingShare.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val items by viewModel.items.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    val share by viewModel.shareIntent.collectAsState()
    val context = LocalContext.current
    var showFilters by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Transaction?>(null) }

    ObserveMessages(viewModel, host)

    androidx.compose.runtime.LaunchedEffect(share) {
        share?.let {
            context.startActivity(android.content.Intent.createChooser(it, "Export transactions"))
            viewModel.consumeShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv() }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filter.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filters active", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = viewModel::clearFilter) { Text("Clear") }
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = "No transactions yet",
                    message = "Use the Add button to record your first income or expense.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            onDelete = { confirmDelete = transaction },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showFilters) {
        FilterDialog(
            current = filter,
            onDismiss = { showFilters = false },
            onApply = {
                viewModel.applyFilter(it)
                showFilters = false
            },
        )
    }

    confirmDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this transaction?") },
            text = {
                Text(
                    "This will permanently remove the entry and update your balances. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(transaction)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.note.ifBlank { CategoryLabels.any(transaction.categoryId) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AppTime.formatEpochDay(transaction.dateEpochDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Tag(transaction.type.label)
                    if (transaction.syncState.id != "synced") {
                        Tag(
                            "Pending sync",
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                SignedMoneyText(money = transaction.amount, type = transaction.type)
                if (transaction.attachmentIds.isNotEmpty()) {
                    Text(
                        "${transaction.attachmentIds.size} receipt(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete transaction")
            }
        }
    }
}

@Composable
private fun FilterDialog(
    current: TransactionFilter,
    onDismiss: () -> Unit,
    onApply: (TransactionFilter) -> Unit,
) {
    var type by remember { mutableStateOf(current.type) }
    var keyword by remember { mutableStateOf(current.keyword.orEmpty()) }
    var from by remember { mutableStateOf(current.fromEpochDay) }
    var to by remember { mutableStateOf(current.toEpochDay) }
    var minAmount by remember { mutableStateOf(current.minAmountMinor?.let { Money.ofMinor(it).toBigDecimal().toPlainString() }.orEmpty()) }
    var maxAmount by remember { mutableStateOf(current.maxAmountMinor?.let { Money.ofMinor(it).toBigDecimal().toPlainString() }.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField(
                    label = "Type",
                    options = listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER),
                    selected = type,
                    optionLabel = { it.label },
                    onSelect = { type = it },
                )
                TextButton(onClick = { type = null }) { Text("Any type") }

                PlainTextField(value = keyword, onValueChange = { keyword = it }, label = "Keyword in note")

                from?.let {
                    DateField(label = "From", epochDay = it, onChange = { value -> from = value })
                }
                TextButton(onClick = { from = from ?: AppTime.today().minusMonths(1).toEpochDay() }) {
                    Text(if (from == null) "Set start date" else "Clear start date")
                }

                to?.let {
                    DateField(label = "To", epochDay = it, onChange = { value -> to = value })
                }
                TextButton(onClick = { to = to ?: AppTime.todayEpochDay() }) {
                    Text(if (to == null) "Set end date" else "Clear end date")
                }

                MoneyField(value = minAmount, onValueChange = { minAmount = it }, label = "Minimum amount")
                MoneyField(value = maxAmount, onValueChange = { maxAmount = it }, label = "Maximum amount")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    TransactionFilter(
                        type = type,
                        keyword = keyword.takeIf { it.isNotBlank() },
                        fromEpochDay = from,
                        toEpochDay = to,
                        minAmountMinor = Money.parseOrNull(minAmount.ifBlank { "0" })?.minor,
                        maxAmountMinor = Money.parseOrNull(maxAmount.ifBlank { "0" })?.minor?.takeIf { it > 0 },
                    )
                )
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(TransactionFilter()) }) { Text("Reset") }
        },
    )
}
