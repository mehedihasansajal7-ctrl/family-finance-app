package com.familyfinance.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.familyfinance.app.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host.
 *
 * Extends `FragmentActivity` because `BiometricPrompt` requires it. `FLAG_SECURE` is applied by the
 * UI layer while sensitive screens are composed, not here, so it is scoped to exactly those screens.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        installShortcuts()

        val openAdd = wantsAdd(intent)
        val openChat = wantsChat(intent)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                AppRoot(openAdd = openAdd, openChat = openChat)
            }
        }
    }

    /**
     * A launcher shortcut or the widget opened this activity while it was already running. Re-running
     * `onCreate` with the new intent keeps the "quick action" behaviour in that case too.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (wantsAdd(intent) || wantsChat(intent)) recreate()
    }

    private fun wantsAdd(intent: Intent?): Boolean =
        intent?.action == ACTION_ADD || intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true

    private fun wantsChat(intent: Intent?): Boolean = intent?.action == ACTION_CHAT

    /** Long-press launcher shortcuts: "Add expense" and "Family chat". */
    private fun installShortcuts() {
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)
        val add = ShortcutInfo.Builder(this, "add_expense")
            .setShortLabel(getString(R.string.shortcut_add_short))
            .setLongLabel(getString(R.string.shortcut_add_long))
            .setIcon(icon)
            .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_ADD))
            .build()
        val chat = ShortcutInfo.Builder(this, "family_chat")
            .setShortLabel(getString(R.string.shortcut_chat_short))
            .setLongLabel(getString(R.string.shortcut_chat_long))
            .setIcon(icon)
            .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_CHAT))
            .build()
        runCatching { manager.setDynamicShortcuts(listOf(add, chat)) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_ADD = "com.familyfinance.app.EXTRA_OPEN_ADD"
        const val ACTION_ADD = "com.familyfinance.app.SHORTCUT_ADD"
        const val ACTION_CHAT = "com.familyfinance.app.SHORTCUT_CHAT"
    }
}
