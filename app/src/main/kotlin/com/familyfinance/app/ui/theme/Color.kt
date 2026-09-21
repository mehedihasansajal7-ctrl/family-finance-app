package com.familyfinance.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------- brand palette
// Deep teal conveys trust and is legible against both light and dark surfaces.
val Teal10 = Color(0xFF00201B)
val Teal20 = Color(0xFF003730)
val Teal30 = Color(0xFF005046)
val Teal40 = Color(0xFF0F6A5C)
val Teal80 = Color(0xFF7AD8C5)
val Teal90 = Color(0xFFA6F2DE)

val Sage10 = Color(0xFF071F1C)
val Sage20 = Color(0xFF1F3532)
val Sage30 = Color(0xFF364B48)
val Sage40 = Color(0xFF4D6360)
val Sage80 = Color(0xFFB5CCC8)
val Sage90 = Color(0xFFD1E8E4)

val Plum10 = Color(0xFF251431)
val Plum20 = Color(0xFF3B2947)
val Plum30 = Color(0xFF523F5F)
val Plum40 = Color(0xFF6A5678)
val Plum80 = Color(0xFFD6BEE4)
val Plum90 = Color(0xFFF2DAFF)

val Sand10 = Color(0xFF241A00)
val Sand20 = Color(0xFF3E2E00)
val Sand30 = Color(0xFF594400)
val Sand40 = Color(0xFF755B00)
val Sand80 = Color(0xFFE8C34B)
val Sand90 = Color(0xFFFFE08C)

// Neutrals
val Neutral10 = Color(0xFF191C1B)
val Neutral20 = Color(0xFF2D3130)
val Neutral90 = Color(0xFFE0E3E1)
val Neutral95 = Color(0xFFEFF2F0)
val Neutral99 = Color(0xFFFBFDFB)
val Neutral100 = Color(0xFFFFFFFF)

// ---------------------------------------------------------------- semantic (money) colours
// These are intentionally NOT derived from the M3 scheme: income/expense must stay
// unmistakable in both themes, and colour-blind users get an icon + sign as well.
val IncomeLight = Color(0xFF14663F)
val IncomeDark = Color(0xFF5FD79B)
val ExpenseLight = Color(0xFFB3261E)
val ExpenseDark = Color(0xFFFF897D)
val WarningLight = Color(0xFF8A5300)
val WarningDark = Color(0xFFFFB95C)
val TransferLight = Color(0xFF2A5FA8)
val TransferDark = Color(0xFF9CC4FF)
val CreditLight = Color(0xFF8B2E8B)
val CreditDark = Color(0xFFE7A6E7)

// ---------------------------------------------------------------- chart palette
// Ordered so adjacent series remain distinguishable in greyscale and for deuteranopia.
val ChartLight = listOf(
    Color(0xFF0F6A5C),
    Color(0xFF2A5FA8),
    Color(0xFF8B5E00),
    Color(0xFF7A3E9D),
    Color(0xFFB3261E),
    Color(0xFF14663F),
    Color(0xFF00687A),
    Color(0xFF6D4C41),
)

val ChartDark = listOf(
    Color(0xFF7AD8C5),
    Color(0xFF9CC4FF),
    Color(0xFFE8C34B),
    Color(0xFFD6BEE4),
    Color(0xFFFF897D),
    Color(0xFF5FD79B),
    Color(0xFF6FD3E8),
    Color(0xFFC9B0A4),
)
