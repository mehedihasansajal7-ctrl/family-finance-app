package com.familyfinance.app.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Renders one-shot messages from a [BaseViewModel] as snackbars.
 *
 * Every screen that can perform a write calls this, which is how "never silently fail a transaction"
 * is enforced: if a write fails, the message channel is the only way it can be reported, and it is
 * always observed.
 */
@Composable
fun ObserveMessages(viewModel: BaseViewModel, hostState: SnackbarHostState) {
    LaunchedEffect(viewModel, hostState) {
        viewModel.messages.collect { message ->
            hostState.showSnackbar(message.text)
        }
    }
}

/** Creates a snackbar host state that survives recomposition. */
@Composable
fun rememberMessageHost(): SnackbarHostState = remember { SnackbarHostState() }
