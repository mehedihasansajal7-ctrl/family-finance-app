package com.familyfinance.app.ui.dashboard

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyMath
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.BudgetAlert
import com.familyfinance.app.domain.model.BudgetStatus
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.NotificationRepository
import com.familyfinance.app.domain.usecase.BillStatusCalculator
import com.familyfinance.app.domain.usecase.BudgetStatusCalculator
import com.familyfinance.app.domain.usecase.GoalProgressCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.UiState
import com.familyfinance.app.ui.components.CategoryBarList
import com.familyfinance.app.ui.components.CompactMoneyText
import com.familyfinance.app.ui.components.ExpenseDonut
import com.familyfinance.app.ui.components.MoneyHeadline
import com.familyfinance.app.ui.components.MoneyText
import com.familyfinance.app.ui.components.MonthlyTrendChart
import com.familyfinance.app.ui.components.ProgressTrack
import com.familyfinance.app.ui.components.RowDivider
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.Tag
import com.familyfinance.app.ui.theme.AppTheme
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
import java.time.YearMonth
import javax.inject.Inject

/**
 * The common family dashboard payload.
 *
 * Everything here is family-wide. There is deliberately no member field, and the type cannot be
 * extended to add one without changing [FamilyAggregate] itself — which a test guards.
 */
data class DashboardData(
    val family: Family?,
    val month: YearMonth,
    val aggregate: FamilyAggregate,
    val budgets: List<BudgetStatus>,
    val upcomingBills: List<FamilyBill>,
    val goals: List<FamilyGoal>,
    val unreadNotifications: Int,
)

/** Intermediate holder so the six dashboard streams can be combined without an array cast. */
private data class FamilySlice(
    val family: Family?,
    val aggregate: FamilyAggregate,
    val budgets: List<com.familyfinance.app.domain.model.FamilyBudget>,
    val bills: List<FamilyBill>,
    val goals: List<FamilyGoal>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val families: FamilyRepository,
    private val familyFinance: FamilyFinanceRepository,
    private val notifications: NotificationRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    private val month = MutableStateFlow(AppTime.currentMonth())

    val currentMonth: StateFlow<YearMonth> = month

    val uiState: StateFlow<UiState<DashboardData>> = combine(
        session.currentUser,
        month,
    ) { user, selectedMonth -> user?.familyId to selectedMonth }
        .flatMapLatest { (familyId, selectedMonth) ->
            if (familyId.isNullOrBlank()) {
                flowOf(UiState.Ready(DashboardData(null, selectedMonth, FamilyAggregate.empty(selectedMonth), emptyList(), emptyList(), emptyList(), 0)))
            } else {
                // `combine` is limited to five flows, so the unread count is folded in separately
                // rather than forcing every stream into one array-typed overload.
                combine(
                    families.observeFamily(familyId),
                    familyFinance.observeAggregate(familyId, selectedMonth),
                    familyFinance.observeBudgets(familyId),
                    familyFinance.observeBills(familyId),
                    familyFinance.observeGoals(familyId),
                ) { family, aggregate, budgets, bills, goals ->
                    FamilySlice(family, aggregate, budgets, bills, goals)
                }.combine(
                    session.currentUser.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                        if (uid == null) flowOf(0) else notifications.unreadCount(uid)
                    },
                ) { slice, unread ->
                    UiState.Ready(
                        DashboardData(
                            family = slice.family,
                            month = selectedMonth,
                            aggregate = slice.aggregate,
                            budgets = slice.budgets
                                .filter { it.periodEpochDayEnd >= AppTime.todayEpochDay() }
                                .map { budget ->
                                    BudgetStatusCalculator.status(budget, budgetLimitActual(budget, slice.aggregate))
                                },
                            upcomingBills = slice.bills
                                .filter { it.status.id != "paid" && it.status.id != "skipped" }
                                .sortedBy { it.dueEpochDay }
                                .take(5),
                            goals = slice.goals.filter { it.status.id == "active" }.take(4),
                            unreadNotifications = unread,
                        )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /**
     * Budget actuals.
     *
     * When the backend supplies family category totals (production), those are authoritative and
     * member-free. Otherwise the local aggregate's category totals are used — which is still
     * family-wide, never per-member.
     */
    private fun budgetLimitActual(
        budget: com.familyfinance.app.domain.model.FamilyBudget,
        aggregate: FamilyAggregate,
    ): Money {
        val categoryId = budget.categoryId
        if (categoryId == null) return aggregate.totalExpense
        return aggregate.expenseByCategory.firstOrNull { it.categoryId == categoryId }?.total ?: Money.ZERO
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val candidate = month.value.plusMonths(1)
        if (candidate <= AppTime.currentMonth()) month.value = candidate
    }
}

@Composable
fun DashboardScreen(
    onOpenGoals: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val month by viewModel.currentMonth.collectAsState()

    when (val current = state) {
        UiState.Loading -> com.familyfinance.app.ui.components.LoadingState(message = "Loading family dashboard…")
        is UiState.Failure -> com.familyfinance.app.ui.components.ErrorState(message = current.message)
        is UiState.Ready -> DashboardContent(
            data = current.data,
            month = month,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onOpenGoals = onOpenGoals,
            onOpenReports = onOpenReports,
            onOpenBills = onOpenBills,
            onOpenNotifications = onOpenNotifications,
        )
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val aggregate = data.aggregate
    val semantics = AppTheme.semantics

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        data.family?.name ?: "Family",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        AppTime.monthLabel(month),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                IconButton(
                    onClick = onNextMonth,
                    enabled = month < AppTime.currentMonth(),
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoneyHeadline(
                            money = aggregate.totalIncome,
                            label = "Total income",
                            modifier = Modifier.weight(1f),
                            color = semantics.income,
                            caption = changeCaption(aggregate.incomeChangePercent, "vs last month"),
                        )
                        MoneyHeadline(
                            money = aggregate.totalExpense,
                            label = "Total expense",
                            modifier = Modifier.weight(1f),
                            color = semantics.expense,
                            caption = changeCaption(aggregate.expenseChangePercent, "vs last month"),
                        )
                    }
                    RowDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoneyHeadline(
                            money = aggregate.totalSavings,
                            label = "Savings",
                            modifier = Modifier.weight(1f),
                            color = if (aggregate.totalSavings.isNegative) semantics.expense else semantics.income,
                            caption = "${aggregate.memberCount} contributing member(s)",
                        )
                        if (aggregate.netWorth != null) {
                            MoneyHeadline(
                                money = aggregate.netWorth,
                                label = "Family net worth",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item { PrivacyNote() }

        item {
            SectionCard(
                title = "Income vs expense",
                subtitle = "Last 6 months, family totals",
                action = {
                    IconButton(onClick = onOpenReports) {
                        Icon(Icons.Default.Insights, contentDescription = "Open reports")
                    }
                },
            ) {
                MonthlyTrendChart(points = aggregate.monthlyTrend.ifEmpty { emptyTrend(month) })
            }
        }

        item {
            SectionCard(title = "Expense categories", subtitle = "Family totals only") {
                ExpenseDonut(
                    totals = aggregate.expenseByCategory,
                    total = aggregate.totalExpense,
                    labelFor = CategoryLabels::expense,
                )
                if (aggregate.expenseByCategory.size > 3) {
                    CategoryBarList(
                        totals = aggregate.expenseByCategory,
                        labelFor = CategoryLabels::expense,
                        maxRows = 5,
                    )
                }
            }
        }

        if (data.budgets.isNotEmpty()) {
            item {
                SectionCard(title = "Family budget", subtitle = "Shared with every member") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.budgets.take(6).forEach { status -> BudgetRow(status) }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Upcoming bills",
                subtitle = "No \"paid by\" is recorded or shown",
                action = {
                    androidx.compose.material3.TextButton(onClick = onOpenBills) { Text("All") }
                },
            ) {
                if (data.upcomingBills.isEmpty()) {
                    Text(
                        "No pending bills. Add one from Add → Add bill.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.upcomingBills.forEach { bill -> BillRow(bill) }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Family goals",
                subtitle = "Contribution details stay private",
                action = {
                    androidx.compose.material3.TextButton(onClick = onOpenGoals) { Text("All") }
                },
            ) {
                if (data.goals.isEmpty()) {
                    Text(
                        "No active goals yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        data.goals.forEach { goal ->
                            val progress = GoalProgressCalculator.familyGoal(goal)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(goal.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("${progress.percent}%", style = MaterialTheme.typography.labelMedium)
                                }
                                ProgressTrack(fraction = MoneyMath.ratio(goal.currentAmount, goal.targetAmount))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    CompactMoneyText(goal.currentAmount)
                                    CompactMoneyText(goal.targetAmount)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Activity", subtitle = "Sign-in and account-security events") {
                androidx.compose.material3.TextButton(onClick = onOpenNotifications) {
                    Text("View notifications (${data.unreadNotifications} unread)")
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun PrivacyNote() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "These are family totals. Individual income, expenses, balances and account numbers are " +
                    "never shown here — not even to the family owner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus) {
    val color = when (status.alert) {
        BudgetAlert.NONE -> MaterialTheme.colorScheme.primary
        BudgetAlert.WARNING -> AppTheme.semantics.warning
        BudgetAlert.CRITICAL -> AppTheme.semantics.warning
        BudgetAlert.EXCEEDED -> AppTheme.semantics.expense
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                status.budget.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${status.usedPercent}%", style = MaterialTheme.typography.labelMedium, color = color)
        }
        ProgressTrack(fraction = MoneyMath.ratio(status.actual, status.budget.limit), color = color)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactMoneyText(status.actual)
            CompactMoneyText(status.budget.limit)
        }
        if (status.alert != BudgetAlert.NONE) {
            Text(
                when (status.alert) {
                    BudgetAlert.WARNING -> "Over 75% of this budget is used."
                    BudgetAlert.CRITICAL -> "Over 90% of this budget is used."
                    BudgetAlert.EXCEEDED -> "This budget has been exceeded."
                    BudgetAlert.NONE -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun BillRow(bill: FamilyBill) {
    val effective = BillStatusCalculator.effectiveStatus(bill.status, bill.dueEpochDay)
    val overdue = effective.id == "overdue"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Due ${AppTime.formatEpochDay(bill.dueEpochDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) AppTheme.semantics.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(bill.expectedAmount)
            if (overdue) Tag("Overdue", color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private fun changeCaption(percent: Int, suffix: String): String =
    when {
        percent == 0 -> "No change $suffix"
        percent > 0 -> "+$percent% $suffix"
        else -> "$percent% $suffix"
    }

private fun emptyTrend(month: YearMonth): List<MonthlyPoint> =
    (5 downTo 0).map { offset ->
        val m = month.minusMonths(offset.toLong())
        MonthlyPoint(m, Money.ZERO, Money.ZERO, Money.ZERO)
    }
