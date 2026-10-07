package com.familyfinance.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.domain.repository.AuthRepository
import com.familyfinance.app.domain.repository.RecurringRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.ui.auth.AuthScreen
import com.familyfinance.app.ui.components.LoadingState
import com.familyfinance.app.ui.family.FamilySetupScreen
import com.familyfinance.app.ui.navigation.MainScaffold
import com.familyfinance.app.ui.security.AppLockGate
import com.familyfinance.app.ui.security.BiometricLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class NeedsFamily(val user: UserProfile) : AppState
    data class Ready(val user: UserProfile) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val session: SessionManager,
    private val auth: AuthRepository,
    private val recurring: RecurringRepository,
    private val transactions: TransactionRepository,
    private val lockManager: BiometricLockManager,
    private val reminderScheduler: com.familyfinance.app.reminder.ReminderScheduler,
) : ViewModel() {

    val state: StateFlow<AppState> = session.currentUser
        .map { user ->
            when {
                user == null -> AppState.SignedOut
                user.familyId.isNullOrBlank() -> AppState.NeedsFamily(user)
                else -> AppState.Ready(user)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    val lockEnabled: StateFlow<Boolean> = session.biometricLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bengaliDigits: StateFlow<Boolean> = session.bengaliDigitsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    val biometricAvailable: Boolean get() = lockManager.canAuthenticate()

    init {
        // Generate any recurring entries that came due while the app was closed, then flush the
        // offline queue. Both are idempotent, so running them on every start is safe.
        viewModelScope.launch {
            state.collect { current ->
                if (current !is AppState.Ready) return@collect
                runCatching {
                    recurring.generateDue(current.user.uid, AppTime.todayEpochDay())
                    transactions.syncPending(current.user.uid)
                }
                reminderScheduler.scheduleDaily()
                reminderScheduler.runNow()
                _locked.value = lockEnabled.value
            }
        }
    }

    /** Called when the app returns to the foreground; re-locks if the idle timeout elapsed. */
    fun onResume() {
        viewModelScope.launch {
            if (lockEnabled.value && session.isIdleExpired(SessionManager.IDLE_TIMEOUT_MILLIS)) {
                _locked.value = true
            }
            session.touch()
        }
    }

    fun onStop() {
        viewModelScope.launch { session.touch() }
    }

    fun unlock() {
        _locked.value = false
        viewModelScope.launch { session.touch() }
    }

    fun signOut() {
        viewModelScope.launch {
            _locked.value = false
            auth.signOut()
        }
    }
}

/**
 * Application root.
 *
 * Routing is derived from the persisted session rather than from navigation callbacks, so a session
 * restored after process death, an account whose family was just created, and a fresh sign-in all
 * land in the right place without special cases.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val locked by viewModel.locked.collectAsState()
    val bengali by viewModel.bengaliDigits.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LocalContext.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onResume()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    com.familyfinance.app.ui.theme.FamilyFinanceTheme(bengaliDigits = bengali) {
        when (val current = state) {
            AppState.Loading -> LoadingState(message = "Preparing your ledger…")

            AppState.SignedOut -> AuthScreen()

            is AppState.NeedsFamily -> FamilySetupScreen(user = current.user)

            is AppState.Ready -> AppLockGate(
                locked = locked,
                onUnlocked = viewModel::unlock,
                onSignOut = viewModel::signOut,
                manager = hiltLockManager(),
            ) {
                MainScaffold(user = current.user, onSignOut = viewModel::signOut)
            }
        }
    }
}

/** Resolves the lock manager from the Hilt graph without threading it through every screen. */
@Composable
private fun hiltLockManager(): BiometricLockManager = hiltViewModel<LockManagerHolder>().manager

@HiltViewModel
class LockManagerHolder @Inject constructor(val manager: BiometricLockManager) : ViewModel()
