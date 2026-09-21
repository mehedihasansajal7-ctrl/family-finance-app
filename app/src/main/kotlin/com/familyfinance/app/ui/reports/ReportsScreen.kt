package com.familyfinance.app.ui.reports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.PersonalSummary
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.AccountBalanceCalculator
import com.familyfinance.app.domain.usecase.PersonalSummaryCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.UiState
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.CategoryBarList
import com.familyfinance.app.ui.components.EmptyState
import com.familyfinance.app.ui.components.LoadingState
import com.familyfinance.app.ui.components.MoneyHeadline
import com.familyfinance.app.ui.components.MoneyText
import com.familyfinance.app.ui.components.MonthlyTrendChart
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.export.Exporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

enum class ReportTab(val label: String) { FAMILY("Family"), PERSONAL("Personal") }

data class ReportsData(
    val month: YearMonth,
    val aggregate: FamilyAggregate,
    val personal: PersonalSummary,
    val familyName: String,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val session: SessionManager,
    private val families: FamilyRepository,
    private val familyFinance: FamilyFinanceRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val exporter: Exporter,
) : BaseViewModel() {

    var tab by mutableStateOf(ReportTab.FAMILY)
        private set

    private val month = MutableStateFlow(AppTime.currentMonth())

    val uiState: StateFlow<UiState<ReportsData>> = combine(
        session.currentUser,
        month,
    ) { user, selectedMonth -> Triple(user?.uid, user?.familyId, selectedMonth) }
        .distinctUntilChanged()
        .flatMapLatest { (uid, familyId, selectedMonth) ->
            if (uid == null) {
                flowOf(UiState.Loading)
            } else {
                combine(
                    if (familyId.isNullOrBlank()) flowOf(FamilyAggregate.empty(selectedMonth))
                    else familyFinance.observeAggregate(familyId, selectedMonth),
                    accounts.observeAccounts(uid),
                    transactions.observeTransactions(uid),
                    if (familyId.isNullOrBlank()) flowOf(null) else families.observeFamily(familyId),
                ) { aggregate, accountList, transactionList, family ->
                    UiState.Ready(
                        ReportsData(
                            month = selectedMonth,
                            aggregate = aggregate,
                            personal = PersonalSummaryCalculator.compute(uid, accountList, transactionList, selectedMonth),
                            familyName = family?.name ?: "Family",
                        )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val shareIntent: StateFlow<android.content.Intent?> get() = _share
    private val _share = MutableStateFlow<android.content.Intent?>(null)

    fun onTab(value: ReportTab) { tab = value }
    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() {
        val candidate = month.value.plusMonths(1)
        if (candidate <= AppTime.currentMonth()) month.value = candidate
    }

    fun consumeShare() { _share.value = null }

    fun exportFamilyPdf() {
        viewModelScope.launch {
            val data = uiState.value.dataOrNull() ?: return@launch
            val result = exporter.exportFamilyPdf(
                familyName = data.familyName,
                month = data.month,
                totalIncome = data.aggregate.totalIncome,
                totalExpense = data.aggregate.totalExpense,
                totalSavings = data.aggregate.totalSavings,
                netWorth = data.aggregate.netWorth,
                memberCount = data.aggregate.memberCount,
                categoryTotals = data.aggregate.expenseByCategory,
                trend = data.aggregate.monthlyTrend,
            )
            result.onSuccess { _share.value = exporter.shareIntent(it, "application/pdf") }
            result.onFailure { error("Export failed.") }
        }
    }

    fun exportPersonalPdf() {
        viewModelScope.launch {
            val data = uiState.value.dataOrNull() ?: return@launch
            val user = session.currentUserOnce() ?: return@launch
            val result = exporter.exportPersonalPdf(
                ownerLabel = user.displayName.ifBlank { user.email },
                month = data.month,
                totalBalance = data.personal.totalBalance,
                income = data.personal.monthIncome,
                expense = data.personal.monthExpense,
                accountBalances = data.personal.accountBalances,
                categoryTotals = data.personal.expenseByCategory,
                trend = data.personal.monthlyTrend,
            )
            result.onSuccess { _share.value = exporter.shareIntent(it, "application/pdf") }
            result.onFailure { error("Export failed.") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val host = rememberMessageHost()
    val state by viewModel.uiState.collectAsState()
    val share by viewModel.shareIntent.collectAsState()
    val context = LocalContext.current
    ObserveMessages(viewModel, host)

    androidx.compose.runtime.LaunchedEffect(share) {
        share?.let {
            context.startActivity(android.content.Intent.createChooser(it, "Export report"))
            viewModel.consumeShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    IconButton(
                        onClick = {
                            if (viewModel.tab == ReportTab.FAMILY) viewModel.exportFamilyPdf()
                            else viewModel.exportPersonalPdf()
                        },
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export PDF")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ReportTab.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = viewModel.tab == entry,
                        onClick = { viewModel.onTab(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ReportTab.entries.size),
                    ) { Text(entry.label) }
                }
            }

            when (val current = state) {
                UiState.Loading -> LoadingState()
                is UiState.Failure -> com.familyfinance.app.ui.components.ErrorState(message = current.message)
                is UiState.Ready -> when (viewModel.tab) {
                    ReportTab.FAMILY -> FamilyReport(current.data)
                    ReportTab.PERSONAL -> PersonalReport(current.data)
                }
            }
        }
    }
}

@Composable
private fun FamilyReport(data: ReportsData) {
    val aggregate = data.aggregate
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(data.familyName, style = MaterialTheme.typography.titleMedium)
                    Text(AppTime.monthLabel(data.month), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoneyHeadline(aggregate.totalIncome, "Income", Modifier.weight(1f))
                        MoneyHeadline(aggregate.totalExpense, "Expense", Modifier.weight(1f))
                    }
                    MoneyHeadline(aggregate.totalSavings, "Savings")
                }
            }
        }

        item {
            Text(
                "Family reports contain combined totals only. There is no member-wise income or expense " +
                    "report anywhere in this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionCard(title = "Income vs expense", subtitle = "6-month trend") {
                MonthlyTrendChart(aggregate.monthlyTrend)
            }
        }

        item {
            SectionCard(title = "Expense by category") {
                CategoryBarList(aggregate.expenseByCategory, labelFor = CategoryLabels::expense)
            }
        }

        item {
            SectionCard(title = "Budget vs actual", subtitle = "Shared budgets") {
                if (aggregate.expenseByCategory.isEmpty()) {
                    Text("No spending recorded for this period.", style = MaterialTheme.typography.bodySmall)
                } else {
                    aggregate.expenseByCategory.take(8).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(CategoryLabels.expense(item.categoryId), style = MaterialTheme.typography.bodyMedium)
                            MoneyText(item.total)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PersonalReport(data: ReportsData) {
    val summary = data.personal
    if (summary.accountBalances.isEmpty() && summary.monthIncome.isZero && summary.monthExpense.isZero) {
        EmptyState(
            title = "Nothing to report yet",
            message = "Add an account and a transaction to see your personal report.",
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyHeadline(summary.totalBalance, "My total balance")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoneyHeadline(summary.monthIncome, "Income", Modifier.weight(1f))
                        MoneyHeadline(summary.monthExpense, "Expense", Modifier.weight(1f))
                    }
                    MoneyHeadline(summary.monthSavings, "Savings this month")
                }
            }
        }

        item {
            Text(
                "This report is private to you. No other family member can open it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionCard(title = "My accounts") {
                summary.accountBalances.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(row.account.name, style = MaterialTheme.typography.bodyMedium)
                        MoneyText(row.balance)
                    }
                }
                if (summary.accountBalances.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall)
                        MoneyText(
                            AccountBalanceCalculator.totalBalance(summary.accountBalances),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "My cash flow", subtitle = "6-month trend") {
                MonthlyTrendChart(summary.monthlyTrend)
            }
        }

        item {
            SectionCard(title = "My spending by category") {
                CategoryBarList(summary.expenseByCategory, labelFor = CategoryLabels::expense)
            }
        }

        item {
            SectionCard(title = "My income by category") {
                CategoryBarList(
                    summary.incomeByCategory,
                    labelFor = CategoryLabels::income,
                    color = com.familyfinance.app.ui.theme.AppTheme.semantics.income,
                )
            }
        }

        item {
            SectionCard(title = "My net worth", subtitle = "Accounts only, transfers excluded") {
                MoneyText(summary.totalBalance, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Opening balances plus income minus expenses across all your accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
