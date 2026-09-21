package com.familyfinance.app.ui.goals

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyMath
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.BillStatus
import com.familyfinance.app.domain.model.ExpenseCategory
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.usecase.BillStatusCalculator
import com.familyfinance.app.domain.usecase.BudgetStatusCalculator
import com.familyfinance.app.domain.usecase.GoalProgressCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.CompactMoneyText
import com.familyfinance.app.ui.components.DateField
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.ProgressTrack
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.Tag
import com.familyfinance.app.ui.theme.AppTheme
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
import java.time.YearMonth
import javax.inject.Inject

enum class GoalsTab(val label: String) { GOALS("Goals"), BUDGETS("Budgets"), BILLS("Bills") }

data class GoalsData(
    val goals: List<FamilyGoal>,
    val budgets: List<FamilyBudget>,
    val bills: List<FamilyBill>,
    val canManage: Boolean,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val familyFinance: FamilyFinanceRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    var tab by mutableStateOf(GoalsTab.GOALS)
        private set

    val uiState: StateFlow<GoalsData> = session.currentUser
        .map { it?.familyId to (it?.role?.canManageFamily == true) }
        .distinctUntilChanged()
        .flatMapLatest { (familyId, canManage) ->
            if (familyId.isNullOrBlank()) {
                flowOf(GoalsData(emptyList(), emptyList(), emptyList(), false))
            } else {
                combine(
                    familyFinance.observeGoals(familyId),
                    familyFinance.observeBudgets(familyId),
                    familyFinance.observeBills(familyId),
                ) { goals, budgets, bills ->
                    GoalsData(goals, budgets, bills, canManage)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsData(emptyList(), emptyList(), emptyList(), false))

    fun onTab(value: GoalsTab) { tab = value }

    fun saveGoal(name: String, targetRaw: String, currentRaw: String, targetEpochDay: Long?, description: String, existing: FamilyGoal?) {
        val target = Money.parseOrNull(targetRaw)
        if (name.isBlank() || target == null || !target.isPositive) {
            error("Enter a goal name and a target amount greater than zero.")
            return
        }
        val current = Money.parseOrNull(currentRaw.ifBlank { "0" }) ?: Money.ZERO
        run(onSuccess = { info("Goal saved.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            val now = System.currentTimeMillis()
            familyFinance.upsertGoal(
                FamilyGoal(
                    id = existing?.id ?: Ids.newId(),
                    familyId = user.familyId.orEmpty(),
                    name = name.trim(),
                    categoryId = existing?.categoryId ?: "other",
                    targetAmount = target,
                    currentAmount = current,
                    targetEpochDay = targetEpochDay,
                    description = description.trim().takeIf { it.isNotBlank() },
                    status = existing?.status ?: GoalStatus.ACTIVE,
                    createdByUid = existing?.createdByUid ?: user.uid,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    fun deleteGoal(goal: FamilyGoal) {
        run(onSuccess = { info("Goal removed.") }) { familyFinance.deleteGoal(goal.familyId, goal.id) }
    }

    fun saveBudget(name: String, limitRaw: String, categoryId: String?, existing: FamilyBudget?) {
        val limit = Money.parseOrNull(limitRaw)
        if (name.isBlank() || limit == null || !limit.isPositive) {
            error("Enter a budget name and a limit greater than zero.")
            return
        }
        run(onSuccess = { info("Budget saved.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            val (start, end) = BudgetStatusCalculator.monthlyWindow(AppTime.currentMonth())
            familyFinance.upsertBudget(
                FamilyBudget(
                    id = existing?.id ?: Ids.newId(),
                    familyId = user.familyId.orEmpty(),
                    name = name.trim(),
                    categoryId = categoryId,
                    periodEpochDayStart = existing?.periodEpochDayStart ?: start,
                    periodEpochDayEnd = existing?.periodEpochDayEnd ?: end,
                    limit = limit,
                    createdByUid = existing?.createdByUid ?: user.uid,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteBudget(budget: FamilyBudget) {
        run(onSuccess = { info("Budget removed.") }) { familyFinance.deleteBudget(budget.familyId, budget.id) }
    }

    fun saveBill(name: String, amountRaw: String, dueEpochDay: Long, categoryId: String, frequency: RecurrenceFrequency, existing: FamilyBill?) {
        val amount = Money.parseOrNull(amountRaw)
        if (name.isBlank() || amount == null || !amount.isPositive) {
            error("Enter a bill name and an expected amount greater than zero.")
            return
        }
        run(onSuccess = { info("Bill saved.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            val now = System.currentTimeMillis()
            familyFinance.upsertBill(
                FamilyBill(
                    id = existing?.id ?: Ids.newId(),
                    familyId = user.familyId.orEmpty(),
                    name = name.trim(),
                    categoryId = categoryId,
                    expectedAmount = amount,
                    dueEpochDay = dueEpochDay,
                    frequency = frequency,
                    status = existing?.status ?: BillStatus.PENDING,
                    createdByUid = existing?.createdByUid ?: user.uid,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    fun markBillPaid(bill: FamilyBill) {
        run(onSuccess = { info("Marked as paid.") }) {
            familyFinance.upsertBill(bill.copy(status = BillStatus.PAID, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteBill(bill: FamilyBill) {
        run(onSuccess = { info("Bill removed.") }) { familyFinance.deleteBill(bill.familyId, bill.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val host = rememberMessageHost()
    val data by viewModel.uiState.collectAsState()
    ObserveMessages(viewModel, host)

    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Family goals & bills") }) },
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            if (data.canManage) {
                ExtendedFloatingActionButton(
                    onClick = {
                        dialog = when (viewModel.tab) {
                            GoalsTab.GOALS -> "goal"
                            GoalsTab.BUDGETS -> "budget"
                            GoalsTab.BILLS -> "bill"
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GoalsTab.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = viewModel.tab == entry,
                        onClick = { viewModel.onTab(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = GoalsTab.entries.size),
                    ) { Text(entry.label) }
                }
            }

            if (!data.canManage) {
                Text(
                    "Only the family owner or an admin can create and edit family goals, budgets and bills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (viewModel.tab) {
                    GoalsTab.GOALS -> items(data.goals, key = { it.id }) { goal ->
                        GoalCard(goal, data.canManage, onDelete = { viewModel.deleteGoal(goal) })
                    }

                    GoalsTab.BUDGETS -> items(data.budgets, key = { it.id }) { budget ->
                        BudgetCard(budget, data.canManage, onDelete = { viewModel.deleteBudget(budget) })
                    }

                    GoalsTab.BILLS -> items(data.bills, key = { it.id }) { bill ->
                        BillCard(
                            bill = bill,
                            canManage = data.canManage,
                            onMarkPaid = { viewModel.markBillPaid(bill) },
                            onDelete = { viewModel.deleteBill(bill) },
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    when (dialog) {
        "goal" -> GoalDialog(
            onDismiss = { dialog = null },
            onSave = { name, target, current, date, desc ->
                viewModel.saveGoal(name, target, current, date, desc, null)
                dialog = null
            },
        )
        "budget" -> BudgetDialog(
            onDismiss = { dialog = null },
            onSave = { name, limit, category ->
                viewModel.saveBudget(name, limit, category, null)
                dialog = null
            },
        )
        "bill" -> BillDialog(
            onDismiss = { dialog = null },
            onSave = { name, amount, due, category, frequency ->
                viewModel.saveBill(name, amount, due, category, frequency, null)
                dialog = null
            },
        )
    }
}

@Composable
private fun GoalCard(goal: FamilyGoal, canManage: Boolean, onDelete: () -> Unit) {
    val progress = GoalProgressCalculator.familyGoal(goal)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canManage) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${goal.name}")
                    }
                }
            }
            ProgressTrack(fraction = MoneyMath.ratio(goal.currentAmount, goal.targetAmount))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CompactMoneyText(goal.currentAmount)
                Text("${progress.percent}%", style = MaterialTheme.typography.labelMedium)
                CompactMoneyText(goal.targetAmount)
            }
            Text(
                buildString {
                    append("${progress.remaining} remaining")
                    progress.daysRemaining?.let { days ->
                        if (days > 0) append(" · $days day(s) left")
                    }
                    progress.requiredPerMonth?.let { perMonth ->
                        if (!perMonth.isZero) append(" · ${perMonth} per month")
                    }
                }.replace("৳", "\u09F3"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetCard(budget: FamilyBudget, canManage: Boolean, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(budget.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        budget.categoryId?.let(CategoryLabels::expense) ?: "All categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canManage) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${budget.name}")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Limit", style = MaterialTheme.typography.bodySmall)
                CompactMoneyText(budget.limit)
            }
            Text(
                "Alerts at 75% and 90%. Actual spending is family-wide, so no member is identified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BillCard(bill: FamilyBill, canManage: Boolean, onMarkPaid: () -> Unit, onDelete: () -> Unit) {
    val effective = BillStatusCalculator.effectiveStatus(bill.status, bill.dueEpochDay)
    val overdue = effective.id == "overdue"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bill.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Due ${AppTime.formatEpochDay(bill.dueEpochDay)} · ${bill.frequency.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) AppTheme.semantics.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompactMoneyText(bill.expectedAmount)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(effective.label)
                Tag(CategoryLabels.expense(bill.categoryId))
            }
            if (canManage && bill.status != BillStatus.PAID) {
                TextButton(onClick = onMarkPaid) { Text("Mark as paid") }
            }
            if (canManage) {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            Text(
                "No \"paid by\" is recorded. Bills are a shared family obligation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long?, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(AppTime.today().plusMonths(12).toEpochDay()) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New family goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(value = name, onValueChange = { name = it }, label = "Goal name")
                MoneyField(value = target, onValueChange = { target = it }, label = "Target amount")
                MoneyField(value = current, onValueChange = { current = it }, label = "Already saved")
                DateField(label = "Target date", epochDay = date, onChange = { date = it })
                PlainTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    singleLine = false,
                )
                Text(
                    "Contributions are added to a family total. Individual contribution amounts are not " +
                        "published to other members.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, target, current, date, description) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BudgetDialog(onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExpenseCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New family budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(value = name, onValueChange = { name = it }, label = "Budget name")
                MoneyField(value = limit, onValueChange = { limit = it }, label = "Monthly limit")
                PickerField(
                    label = "Category",
                    options = ExpenseCategory.entries.toList(),
                    selected = category,
                    optionLabel = { it.label },
                    onSelect = { category = it },
                )
                TextButton(onClick = { category = null }) { Text("Apply to all categories") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, limit, category?.id) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BillDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, RecurrenceFrequency) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(AppTime.todayEpochDay()) }
    var category by remember { mutableStateOf(ExpenseCategory.ELECTRICITY) }
    var frequency by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New family bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(value = name, onValueChange = { name = it }, label = "Bill name")
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Expected amount")
                DateField(label = "Due date", epochDay = due, onChange = { due = it })
                PickerField(
                    label = "Category",
                    options = ExpenseCategory.entries.toList(),
                    selected = category,
                    optionLabel = { it.label },
                    onSelect = { category = it },
                )
                PickerField(
                    label = "Repeats",
                    options = listOf(RecurrenceFrequency.MONTHLY, RecurrenceFrequency.WEEKLY, RecurrenceFrequency.YEARLY),
                    selected = frequency,
                    optionLabel = { it.label },
                    onSelect = { frequency = it },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, amount, due, category.id, frequency) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
