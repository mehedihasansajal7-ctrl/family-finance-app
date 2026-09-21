package com.familyfinance.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                AppRoot()
            }
        }
    }
}
