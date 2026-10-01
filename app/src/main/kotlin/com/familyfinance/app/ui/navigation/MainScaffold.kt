package com.familyfinance.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.ui.accounts.AccountEditScreen
import com.familyfinance.app.ui.accounts.AccountsScreen
import com.familyfinance.app.ui.add.AddTransactionScreen
import com.familyfinance.app.ui.chat.ChatScreen
import com.familyfinance.app.ui.dashboard.DashboardScreen
import com.familyfinance.app.ui.goals.GoalsScreen
import com.familyfinance.app.ui.members.MembersScreen
import com.familyfinance.app.ui.myfinance.MyFinanceScreen
import com.familyfinance.app.ui.myfinance.SavingsLoansScreen
import com.familyfinance.app.ui.notifications.NotificationsScreen
import com.familyfinance.app.ui.profile.ProfileScreen
import com.familyfinance.app.ui.recurring.RecurringScreen
import com.familyfinance.app.ui.reports.ReportsScreen
import com.familyfinance.app.ui.settings.SettingsScreen
import com.familyfinance.app.ui.transactions.TransactionsScreen

/**
 * The signed-in shell: bottom navigation, the add action, and the navigation graph.
 *
 * The bottom bar is hidden on detail screens (accounts, settings, …) so the back stack is the only
 * way forward there — which keeps "am I still in my private area?" unambiguous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    user: UserProfile,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelDestination.entries.map { it.route }
    // Goals owns its own tab-aware Add action for goals, budgets, and bills.
    val showAddButton = currentRoute in setOf(Routes.DASHBOARD, Routes.MY_FINANCE)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showAddButton) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenGoals = { navController.navigate(Routes.GOALS) { launchSingleTop = true } },
                    onOpenReports = { navController.navigate(Routes.REPORTS) { launchSingleTop = true } },
                    onOpenBills = { navController.navigate(Routes.GOALS) { launchSingleTop = true } },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                )
            }

            composable(Routes.REPORTS) { ReportsScreen() }

            composable(Routes.CHAT) { ChatScreen() }

            composable(Routes.GOALS) { GoalsScreen() }

            composable(Routes.MY_FINANCE) {
                MyFinanceScreen(
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenSavings = { navController.navigate(Routes.SAVINGS) },
                    onOpenLoans = { navController.navigate(Routes.SAVINGS) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                route = "${Routes.ADD_TRANSACTION}?${Routes.ARG_TRANSACTION_ID}={${Routes.ARG_TRANSACTION_ID}}",
                arguments = listOf(
                    navArgument(Routes.ARG_TRANSACTION_ID) { nullable = true; defaultValue = null },
                ),
            ) { entry ->
                AddTransactionScreen(
                    onDone = { navController.popBackStack() },
                    onManageAccounts = {
                        navController.popBackStack()
                        navController.navigate(Routes.ACCOUNTS)
                    },
                    transactionId = entry.arguments?.getString(Routes.ARG_TRANSACTION_ID),
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onBack = { navController.popBackStack() },
                    onAddAccount = { navController.navigate(Routes.accountEdit()) },
                    onEditAccount = { accountId -> navController.navigate(Routes.accountEdit(accountId)) },
                )
            }

            composable("${Routes.ACCOUNT_EDIT}?${Routes.ARG_ACCOUNT_ID}={${Routes.ARG_ACCOUNT_ID}}") { entry ->
                AccountEditScreen(
                    accountId = entry.arguments?.getString(Routes.ARG_ACCOUNT_ID),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { transactionId ->
                        navController.navigate("${Routes.ADD_TRANSACTION}?${Routes.ARG_TRANSACTION_ID}=$transactionId")
                    },
                )
            }

            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SAVINGS) {
                SavingsLoansScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PROFILE) {
                ProfileScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMembers = { navController.navigate(Routes.MEMBERS) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = onSignOut,
                )
            }

            composable(Routes.MEMBERS) {
                MembersScreen(
                    onBack = { navController.popBackStack() },
                    currentUser = user,
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showAddSheet) {
        AddActionSheet(
            onDismiss = { showAddSheet = false },
            onPick = { route ->
                showAddSheet = false
                when (route) {
                    Routes.ADD_TRANSACTION -> navController.navigate(Routes.ADD_TRANSACTION)
                    else -> navController.navigate(route)
                }
            },
        )
    }
}

/**
 * The add action's options, exactly as the product specifies: income, expense, transfer, bill, goal.
 *
 * Income/expense/transfer all land on the same transaction form with the type pre-selected — one
 * form, one validation path, no divergence between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActionSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("What would you like to add?", style = MaterialTheme.typography.titleMedium)
            SheetAction("Add income", "Salary, business, freelance, bonus…", Icons.Default.SouthWest) {
                onPick(Routes.ADD_TRANSACTION)
            }
            SheetAction("Add expense", "Food, rent, utilities, education…", Icons.Default.NorthEast) {
                onPick(Routes.ADD_TRANSACTION)
            }
            SheetAction("Transfer between my accounts", "Moves money without changing income or expense", Icons.Default.SwapHoriz) {
                onPick(Routes.ADD_TRANSACTION)
            }
            SheetAction("Add family bill", "A shared recurring bill", Icons.Default.ReceiptLong) {
                onPick(Routes.GOALS)
            }
            SheetAction("Add family goal", "A shared savings target", Icons.Default.Flag) {
                onPick(Routes.GOALS)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SheetAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("Add") }
    }
}
