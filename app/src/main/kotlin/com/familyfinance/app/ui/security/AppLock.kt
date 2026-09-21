package com.familyfinance.app.ui.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

/**
 * Blocks the UI while the biometric lock is engaged.
 *
 * [locked] is driven by the app-level session timeout, so the lock also engages after the app has
 * been idle — not just on cold start.
 */
@Composable
fun AppLockGate(
    locked: Boolean,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
    manager: BiometricLockManager,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompted by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }

    // Sensitive screens must not appear in the recents thumbnail or in screenshots.
    SecureScreen(enabled = true)

    LaunchedEffect(locked, prompted) {
        if (!locked || prompted) return@LaunchedEffect
        prompted = true
        val activity = context as? FragmentActivity
        if (activity == null || !manager.canAuthenticate()) {
            unavailable = true
            return@LaunchedEffect
        }
        val ok = manager.authenticate(activity)
        if (ok) {
            onUnlocked()
        }
    }

    if (!locked) {
        content()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Family Finance is locked",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                if (unavailable) {
                    "Biometric unlock is not available on this device. Sign in again to continue."
                } else {
                    "Unlock to see your accounts and transactions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(
                onClick = {
                    val activity = context as? FragmentActivity
                    if (activity == null || !manager.canAuthenticate()) {
                        unavailable = true
                        return@Button
                    }
                    scope.launch {
                        if (manager.authenticate(activity)) onUnlocked()
                    }
                },
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text("Unlock")
            }
            TextButton(onClick = onSignOut) { Text("Sign out instead") }
        }
    }
}

/**
 * Sets `FLAG_SECURE` while composed, keeping balances out of screenshots, the recents thumbnail and
 * screen recordings. Cleared on dispose so non-sensitive screens are unaffected.
 */
@Composable
fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (window != null && enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null && enabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
