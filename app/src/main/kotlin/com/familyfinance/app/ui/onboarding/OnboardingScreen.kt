package com.familyfinance.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    OnboardingPage(
        Icons.Default.AccountBalanceWallet,
        "Your money stays private",
        "Your own income and expenses are visible only to you. The family dashboard shows totals, never who spent what.",
    ),
    OnboardingPage(
        Icons.Default.Flag,
        "Everything in one place",
        "Accounts, budgets, bills, savings goals, loans and recurring entries — plus reminders before bills are due.",
    ),
    OnboardingPage(
        Icons.Default.Groups,
        "Split shared expenses",
        "Split a family expense and see exactly who owes whom, with the fewest payments to settle up.",
    ),
    OnboardingPage(
        Icons.Default.Chat,
        "Talk it over",
        "A private family chat, so you can discuss a budget or a bill without leaving the app.",
    ),
)

/** One-time intro shown after the first sign-in. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val page = PAGES[index]

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                page.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAGES.indices.forEach { i ->
                    Text(
                        "\u25CF",
                        color = if (i == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (index < PAGES.lastIndex) index++ else onDone() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (index < PAGES.lastIndex) "Next" else "Get started")
            }
            TextButton(onClick = onDone) { Text("Skip") }
        }
    }
}
