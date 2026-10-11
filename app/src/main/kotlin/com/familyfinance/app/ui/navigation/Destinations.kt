package com.familyfinance.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes.
 *
 * Plain string routes rather than the type-safe API: the graph is small, and string routes keep the
 * deep-link surface explicit and easy to audit (a deep link is an input the app must treat as
 * untrusted, and each one is listed here in one place).
 */
object Routes {
    const val AUTH = "auth"
    const val FAMILY_SETUP = "family_setup"

    const val DASHBOARD = "dashboard"
    const val REPORTS = "reports"
    const val GOALS = "goals"
    const val MY_FINANCE = "my_finance"

    const val ADD_TRANSACTION = "add_transaction"

    const val ACCOUNTS = "accounts"
    const val ACCOUNT_EDIT = "account_edit"
    const val TRANSACTIONS = "transactions"
    const val RECURRING = "recurring"
    const val SAVINGS = "savings"
    const val LOANS = "loans"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val MEMBERS = "members"
    const val INVITE = "invite"
    const val NOTIFICATIONS = "notifications"
    const val BUDGETS = "budgets"
    const val BILLS = "bills"
    const val CHAT = "chat"
    const val SPLIT = "split"

    const val ARG_ACCOUNT_ID = "accountId"
    const val ARG_TRANSACTION_ID = "transactionId"

    fun accountEdit(accountId: String? = null): String =
        if (accountId.isNullOrBlank()) ACCOUNT_EDIT else "$ACCOUNT_EDIT?$ARG_ACCOUNT_ID=$accountId"
}

/** The four primary destinations shown in the bottom bar. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.DASHBOARD, "Home", Icons.Default.Home),
    REPORTS(Routes.REPORTS, "Reports", Icons.Default.BarChart),
    GOALS(Routes.GOALS, "Goals", Icons.Default.Flag),
    MY_FINANCE(Routes.MY_FINANCE, "My Finance", Icons.Default.AccountBalanceWallet),
    CHAT(Routes.CHAT, "Chat", Icons.Default.Chat),
}
