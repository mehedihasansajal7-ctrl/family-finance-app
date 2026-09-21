package com.familyfinance.app.ui.myfinance

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyMath
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.LoanDirection
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.PersonalSummary
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.PersonalFinanceRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.AccountBalanceCalculator
import com.familyfinance.app.domain.usecase.GoalProgressCalculator
import com.familyfinance.app.domain.usecase.PersonalSummaryCalculator
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.CategoryLabels
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.CompactMoneyText
import com.familyfinance.app.ui.components.DateField
import com.familyfinance.app.ui.components.InlineMessage
import com.familyfinance.app.ui.components.MoneyField
import com.familyfinance.app.ui.components.MoneyHeadline
import com.familyfinance.app.ui.components.MoneyText
import com.familyfinance.app.ui.components.PickerField
import com.familyfinance.app.ui.components.PlainTextField
import com.familyfinance.app.ui.components.ProgressTrack
import com.familyfinance.app.ui.components.RowDivider
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.SignedMoneyText
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

data class MyFinanceData(
    val summary: PersonalSummary,
    val recent: List<Transaction>,
    val savingsGoals: List<SavingsGoal>,
    val loans: List<PersonalLoan>,
    val displayName: String,
)

/** Intermediate holder for the nested combine in [MyFinanceViewModel]. */
private data class PersonalBundle(
    val accounts: List<com.familyfinance.app.domain.model.Account>,
    val all: List<Transaction>,
    val recent: List<Transaction>,
    val goals: List<SavingsGoal>,
    val loans: List<PersonalLoan>,
)

@HiltViewModel
class MyFinanceViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val personal: PersonalFinanceRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    val uiState: StateFlow<MyFinanceData> = session.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(MyFinanceData(PersonalSummary.EMPTY, emptyList(), emptyList(), emptyList(), ""))
            } else {
                // Nested combine: `combine` tops out at five flows, and an array-typed overload
                // would force unsafe casts across heterogeneous types.
                combine(
                    accounts.observeAccounts(uid),
                    transactions.observeTransactions(uid),
                    transactions.observeRecent(uid, 8),
                    personal.observeSavingsGoals(uid),
                    personal.observeLoans(uid),
                ) { accountList, all, recent, goals, loans ->
                    PersonalBundle(accountList, all, recent, goals, loans)
                }.combine(session.currentUser) { bundle, user ->
                    MyFinanceData(
                        summary = PersonalSummaryCalculator.compute(uid, bundle.accounts, bundle.all),
                        recent = bundle.recent,
                        savingsGoals = bundle.goals,
                        loans = bundle.loans,
                        displayName = user?.displayName.orEmpty(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyFinanceData(PersonalSummary.EMPTY, emptyList(), emptyList(), emptyList(), ""))

    fun totalBalanceOf(summary: PersonalSummary): Money = AccountBalanceCalculator.totalBalance(summary.accountBalances)
}

@Composable
fun MyFinanceScreen(
    onOpenAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MyFinanceViewModel = hiltViewModel(),
) {
    val data by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyHeadline(
                        money = viewModel.totalBalanceOf(data.summary),
                        label = "My total balance",
                        caption = "Private to you",
                    )
                    RowDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoneyHeadline(data.summary.monthIncome, "This month income", Modifier.weight(1f))
                        MoneyHeadline(data.summary.monthExpense, "This month expense", Modifier.weight(1f))
                    }
                    MoneyHeadline(data.summary.monthSavings, "This month savings")
                }
            }
        }

        item {
            Text(
                "Everything in this section is visible only to you. Other family members see the shared " +
                    "dashboard totals and nothing else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionCard(title = "Manage") {
                NavigationRow("My accounts", "Balances and account details", Icons.Default.AccountBalance, onOpenAccounts)
                NavigationRow("My transactions", "Search, filter and export", Icons.Default.SwapHoriz, onOpenTransactions)
                NavigationRow("Recurring entries", "Salary, rent, bills and EMIs", Icons.Default.Repeat, onOpenRecurring)
                NavigationRow("My savings goals", "Private to you", Icons.Default.Savings, onOpenSavings)
                NavigationRow("My loans", "Borrowed and lent", Icons.Default.TrendingUp, onOpenLoans)
                NavigationRow("My profile", "Name, photo, contact details", Icons.Default.Person, onOpenProfile)
                NavigationRow("Settings", "App lock, export, sign out", Icons.Default.Receipt, onOpenSettings)
            }
        }

        if (data.summary.accountBalances.isNotEmpty()) {
            item {
                SectionCard(title = "My accounts", subtitle = "Account numbers are always masked") {
                    data.summary.accountBalances.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.account.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    row.account.last4?.let { com.familyfinance.app.core.money.MoneyFormat.maskIdentifier(it) }
                                        ?: row.account.type.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(row.balance)
                        }
                    }
                }
            }
        }

        if (data.recent.isNotEmpty()) {
            item {
                SectionCard(title = "Recent transactions") {
                    data.recent.forEach { transaction ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    transaction.note.ifBlank { CategoryLabels.any(transaction.categoryId) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    AppTime.formatEpochDay(transaction.dateEpochDay),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SignedMoneyText(transaction.amount, transaction.type)
                        }
                    }
                    TextButton(onClick = onOpenTransactions) { Text("See all transactions") }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun NavigationRow(label: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Open $label")
        }
    }
}

// ------------------------------------------------------------------ savings & loans

enum class PersonalTab(val label: String) { SAVINGS("Savings goals"), LOANS("Loans") }

@HiltViewModel
class SavingsLoansViewModel @Inject constructor(
    private val personal: PersonalFinanceRepository,
    private val session: SessionManager,
) : BaseViewModel() {

    var tab by mutableStateOf(PersonalTab.SAVINGS)
        private set

    val goals: StateFlow<List<SavingsGoal>> = session.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else personal.observeSavingsGoals(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loans: StateFlow<List<PersonalLoan>> = session.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else personal.observeLoans(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onTab(value: PersonalTab) { tab = value }

    fun saveGoal(name: String, targetRaw: String, currentRaw: String, targetEpochDay: Long?) {
        val target = Money.parseOrNull(targetRaw)
        if (name.isBlank() || target == null || !target.isPositive) {
            error("Enter a name and a target amount greater than zero.")
            return
        }
        run(onSuccess = { info("Savings goal saved.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            personal.upsertSavingsGoal(
                SavingsGoal(
                    id = Ids.newId(),
                    ownerUid = user.uid,
                    familyId = user.familyId.orEmpty(),
                    name = name.trim(),
                    targetAmount = target,
                    currentAmount = Money.parseOrNull(currentRaw.ifBlank { "0" }) ?: Money.ZERO,
                    targetEpochDay = targetEpochDay,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        run(onSuccess = { info("Savings goal removed.") }) { personal.deleteSavingsGoal(goal.ownerUid, goal.id) }
    }

    fun saveLoan(
        direction: LoanDirection,
        counterparty: String,
        principalRaw: String,
        repaidRaw: String,
        startEpochDay: Long,
        dueEpochDay: Long?,
        note: String,
    ) {
        val principal = Money.parseOrNull(principalRaw)
        if (counterparty.isBlank() || principal == null || !principal.isPositive) {
            error("Enter the lender/borrower and a principal greater than zero.")
            return
        }
        run(onSuccess = { info("Loan saved.") }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            personal.upsertLoan(
                PersonalLoan(
                    id = Ids.newId(),
                    ownerUid = user.uid,
                    familyId = user.familyId.orEmpty(),
                    direction = direction,
                    counterparty = counterparty.trim(),
                    principal = principal,
                    repaidAmount = Money.parseOrNull(repaidRaw.ifBlank { "0" }) ?: Money.ZERO,
                    startEpochDay = startEpochDay,
                    dueEpochDay = dueEpochDay,
                    note = note.trim().takeIf { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteLoan(loan: PersonalLoan) {
        run(onSuccess = { info("Loan removed.") }) { personal.deleteLoan(loan.ownerUid, loan.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsLoansScreen(
    onBack: () -> Unit,
    viewModel: SavingsLoansViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val goals by viewModel.goals.collectAsState()
    val loans by viewModel.loans.collectAsState()
    ObserveMessages(viewModel, host)

    var showGoalDialog by remember { mutableStateOf(false) }
    var showLoanDialog by remember { mutableStateOf(false) }
    var confirmDeleteGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmDeleteLoan by remember { mutableStateOf<PersonalLoan?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings & loans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (viewModel.tab == PersonalTab.SAVINGS) showGoalDialog = true else showLoanDialog = true
                        },
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = "Add")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.onTab(PersonalTab.SAVINGS) }) { Text("Savings goals") }
                TextButton(onClick = { viewModel.onTab(PersonalTab.LOANS) }) { Text("Loans") }
            }

            Text(
                "These records are private to you and are never shown on the family dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (viewModel.tab == PersonalTab.SAVINGS) {
                    items(goals, key = { it.id }) { goal ->
                        val progress = GoalProgressCalculator.savingsGoal(goal)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { confirmDeleteGoal = goal }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete ${goal.name}")
                                    }
                                }
                                ProgressTrack(fraction = MoneyMath.ratio(goal.currentAmount, goal.targetAmount))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    CompactMoneyText(goal.currentAmount)
                                    Text("${progress.percent}%", style = MaterialTheme.typography.labelMedium)
                                    CompactMoneyText(goal.targetAmount)
                                }
                            }
                        }
                    }
                    if (goals.isEmpty()) {
                        item {
                            Text(
                                "No private savings goals yet. Tap the icon above to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(loans, key = { it.id }) { loan ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(loan.counterparty, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            loan.direction.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { confirmDeleteLoan = loan }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete loan")
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Principal", style = MaterialTheme.typography.bodySmall)
                                    MoneyText(loan.principal)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Outstanding", style = MaterialTheme.typography.bodySmall)
                                    MoneyText(loan.outstanding)
                                }
                                loan.dueEpochDay?.let { due ->
                                    Text(
                                        "Due ${AppTime.formatEpochDay(due)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (loans.isEmpty()) {
                        item {
                            Text(
                                "No loans recorded. Tap the icon above to add a borrowed or lent amount.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showGoalDialog) {
        SavingsGoalDialog(
            onDismiss = { showGoalDialog = false },
            onSave = { name, target, current, date ->
                viewModel.saveGoal(name, target, current, date)
                showGoalDialog = false
            },
        )
    }

    if (showLoanDialog) {
        LoanDialog(
            onDismiss = { showLoanDialog = false },
            onSave = { direction, counterparty, principal, repaid, start, due, note ->
                viewModel.saveLoan(direction, counterparty, principal, repaid, start, due, note)
                showLoanDialog = false
            },
        )
    }

    confirmDeleteGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmDeleteGoal = null },
            title = { Text("Delete this savings goal?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(goal); confirmDeleteGoal = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteGoal = null }) { Text("Cancel") } },
        )
    }

    confirmDeleteLoan?.let { loan ->
        AlertDialog(
            onDismissRequest = { confirmDeleteLoan = null },
            title = { Text("Delete this loan record?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLoan(loan); confirmDeleteLoan = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteLoan = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SavingsGoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<Long?>(AppTime.today().plusMonths(12).toEpochDay()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New savings goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainTextField(value = name, onValueChange = { name = it }, label = "Goal name")
                MoneyField(value = target, onValueChange = { target = it }, label = "Target amount")
                MoneyField(value = current, onValueChange = { current = it }, label = "Already saved")
                date?.let {
                    DateField(label = "Target date", epochDay = it, onChange = { value -> date = value })
                }
                TextButton(onClick = { date = if (date == null) AppTime.today().plusMonths(12).toEpochDay() else null }) {
                    Text(if (date == null) "Set a target date" else "Remove target date")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, target, current, date) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoanDialog(
    onDismiss: () -> Unit,
    onSave: (LoanDirection, String, String, String, Long, Long?, String) -> Unit,
) {
    var direction by remember { mutableStateOf(LoanDirection.BORROWED) }
    var counterparty by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var repaid by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(AppTime.todayEpochDay()) }
    var due by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New loan record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField(
                    label = "Direction",
                    options = LoanDirection.entries.toList(),
                    selected = direction,
                    optionLabel = { it.label },
                    onSelect = { direction = it },
                )
                PlainTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = if (direction == LoanDirection.BORROWED) "Lender" else "Borrower",
                )
                MoneyField(value = principal, onValueChange = { principal = it }, label = "Principal")
                MoneyField(value = repaid, onValueChange = { repaid = it }, label = "Already repaid")
                DateField(label = "Start date", epochDay = start, onChange = { start = it })
                due?.let { DateField(label = "Due date", epochDay = it, onChange = { value -> due = value }) }
                TextButton(onClick = { due = if (due == null) AppTime.today().plusMonths(6).toEpochDay() else null }) {
                    Text(if (due == null) "Set a due date" else "Remove due date")
                }
                PlainTextField(value = note, onValueChange = { note = it }, label = "Note (optional)")
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(direction, counterparty, principal, repaid, start, due, note) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
