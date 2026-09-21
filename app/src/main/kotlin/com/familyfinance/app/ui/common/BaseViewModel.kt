package com.familyfinance.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.app.core.logging.AppLog
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Shared ViewModel behaviour: one-shot messages, busy state, and a single place where an
 * [Outcome] failure becomes user-visible text.
 *
 * Nothing here swallows a failure. A write that does not succeed always produces either an error
 * message or an explicit offline notice — the user is never left assuming something saved when it
 * did not.
 */
abstract class BaseViewModel : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    protected fun info(text: String) {
        viewModelScope.launch { _messages.send(UiMessage(text, isError = false)) }
    }

    protected fun error(text: String) {
        viewModelScope.launch { _messages.send(UiMessage(text, isError = true)) }
    }

    protected fun report(error: AppError) = error(error.message)

    /**
     * Runs a write, surfacing failures and toggling [busy]. [onSuccess] receives the value on the
     * success path only, so a caller cannot accidentally act on a failed result.
     */
    protected fun run(
        showBusy: Boolean = true,
        onSuccess: (Any?) -> Unit = {},
        block: suspend () -> Outcome<*>,
    ) {
        viewModelScope.launch {
            if (showBusy) _busy.value = true
            try {
                when (val result = block()) {
                    is Outcome.Success -> onSuccess(result.value)
                    is Outcome.Failure -> report(result.error)
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                AppLog.e("Unexpected failure in view model", unexpected)
                error("Something went wrong. Please try again.")
            } finally {
                if (showBusy) _busy.value = false
            }
        }
    }

    /** Launches a plain suspending side-effect that must not crash the screen if it fails. */
    protected fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                AppLog.e("Background task failed", unexpected)
            }
        }
    }
}

data class UiMessage(val text: String, val isError: Boolean)
