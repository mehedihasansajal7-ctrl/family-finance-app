package com.familyfinance.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import com.familyfinance.app.ui.components.SectionCard
import com.familyfinance.app.ui.components.SegmentedSelector
import com.familyfinance.app.ui.export.Exporter
import com.familyfinance.app.ui.security.BiometricLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val familyFinance: FamilyFinanceRepository,
    private val families: FamilyRepository,
    private val transactions: TransactionRepository,
    private val audit: AuditRepository,
    private val exporter: Exporter,
    private val lockManager: BiometricLockManager,
) : BaseViewModel() {

    val biometricEnabled: StateFlow<Boolean> = session.biometricLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bengaliDigits: StateFlow<Boolean> = session.bengaliDigitsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<String> = session.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionManager.THEME_SYSTEM)

    val lastSync: StateFlow<Long> = session.lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val biometricAvailable: Boolean get() = lockManager.canAuthenticate()

    private val _share = MutableStateFlow<android.content.Intent?>(null)
    val shareIntent: StateFlow<android.content.Intent?> = _share

    fun consumeShare() { _share.value = null }

    fun setBiometric(enabled: Boolean) {
        launchSafe { session.setBiometricLock(enabled) }
        if (enabled && !biometricAvailable) {
            error("This device has no enrolled biometric or screen lock, so the app lock cannot be enabled.")
        }
    }

    fun setBengaliDigits(enabled: Boolean) {
        launchSafe { session.setBengaliDigits(enabled) }
    }

    fun setTheme(mode: String) {
        launchSafe { session.setThemeMode(mode) }
    }

    fun exportFamilyPdf() {
        viewModelScope.launch {
            val user = session.currentUserOnce() ?: return@launch
            val familyId = user.familyId ?: run {
                error("You are not in a family yet.")
                return@launch
            }
            val month = AppTime.currentMonth()
            val aggregate = familyFinance.observeAggregate(familyId, month).first()
            val family = families.observeFamily(familyId).first()
            val result = exporter.exportFamilyPdf(
                familyName = family?.name ?: "Family",
                month = month,
                totalIncome = aggregate.totalIncome,
                totalExpense = aggregate.totalExpense,
                totalSavings = aggregate.totalSavings,
                netWorth = aggregate.netWorth,
                memberCount = aggregate.memberCount,
                categoryTotals = aggregate.expenseByCategory,
                trend = aggregate.monthlyTrend,
            )
            result.onSuccess { _share.value = exporter.shareIntent(it, "application/pdf") }
            result.onFailure { error("Export failed.") }
        }
    }

    fun exportPersonalCsv() {
        viewModelScope.launch {
            val user = session.currentUserOnce() ?: return@launch
            val rows = transactions.observeTransactions(user.uid).first()
            val result = exporter.exportPersonalCsv(
                ownerLabel = user.displayName.ifBlank { user.email },
                transactions = rows,
                fromEpochDay = null,
                toEpochDay = null,
            )
            result.onSuccess { _share.value = exporter.shareIntent(it, "text/csv") }
            result.onFailure { error("Export failed.") }
        }
    }

    fun syncNow() {
        run(onSuccess = { count ->
            val synced = count as? Int ?: 0
            info(
                if (synced == 0) "Everything is already in sync."
                else "Synced $synced pending change(s)."
            )
            launchSafe { session.markSynced() }
        }) {
            val user = session.currentUserOnce()
                ?: return@run com.familyfinance.app.core.result.Outcome.Failure(
                    com.familyfinance.app.core.result.AppError.permission("Please sign in again.")
                )
            transactions.syncPending(user.uid)
        }
    }

    fun signOut() {
        launchSafe {
            val user = session.currentUserOnce()
            if (user != null) {
                audit.record(user.familyId, user.uid, AuditRepository.ACTION_SIGN_OUT)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    val biometric by viewModel.biometricEnabled.collectAsState()
    val bengali by viewModel.bengaliDigits.collectAsState()
    val theme by viewModel.themeMode.collectAsState()
    val share by viewModel.shareIntent.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }

    ObserveMessages(viewModel, host)

    androidx.compose.runtime.LaunchedEffect(share) {
        share?.let {
            context.startActivity(android.content.Intent.createChooser(it, "Export"))
            viewModel.consumeShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Security", subtitle = "Protects this device, not the server") {
                ToggleRow(
                    title = "App lock",
                    subtitle = if (viewModel.biometricAvailable) {
                        "Require biometric or screen-lock unlock after 5 minutes idle."
                    } else {
                        "Not available: no biometric or screen lock is enrolled on this device."
                    },
                    checked = biometric,
                    enabled = viewModel.biometricAvailable,
                    onChange = viewModel::setBiometric,
                )
                Text(
                    "Screenshots and the recent-apps preview are blocked while the app is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Display") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                SegmentedSelector(
                    options = listOf(
                        SessionManager.THEME_SYSTEM,
                        SessionManager.THEME_LIGHT,
                        SessionManager.THEME_DARK,
                    ),
                    selected = theme,
                    optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                    onSelect = viewModel::setTheme,
                )
                ToggleRow(
                    title = "Bengali numerals",
                    subtitle = "Show amounts as ৳১২,৩৪৫ instead of ৳12,345.",
                    checked = bengali,
                    enabled = true,
                    onChange = viewModel::setBengaliDigits,
                )
            }

            SectionCard(title = "Family") {
                OutlinedButton(onClick = onOpenMembers, modifier = Modifier.fillMaxWidth()) {
                    Text("Members & invitations")
                }
                OutlinedButton(onClick = onOpenNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("Notifications")
                }
            }

            SectionCard(
                title = "Export",
                subtitle = "Family exports contain totals only. Personal exports contain your data only.",
            ) {
                OutlinedButton(onClick = viewModel::exportFamilyPdf, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text("  Family report (PDF)")
                }
                OutlinedButton(onClick = viewModel::exportPersonalCsv, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text("  My transactions (CSV)")
                }
            }

            SectionCard(title = "Sync") {
                OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) {
                    Text("Sync pending changes now")
                }
                Text(
                    "Every change is saved on this device first. If you are offline it is queued and sent " +
                        "automatically, and it is never posted twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Account") {
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out")
                }
            }

            Text(
                "Family Finance · v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your data stays on this device. Sign in again to continue where you left off.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
