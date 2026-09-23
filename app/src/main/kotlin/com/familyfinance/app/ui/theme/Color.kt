package com.familyfinance.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------- brand palette
// Technical finance palette: midnight navy, electric cyan, and ledger amber.
val Navy10 = Color(0xFF001A2E)
val Navy20 = Color(0xFF003450)
val Navy30 = Color(0xFF004D70)
val Navy40 = Color(0xFF00658F)
val Navy80 = Color(0xFF83D0F3)
val Navy90 = Color(0xFFBDEAFF)

val Slate10 = Color(0xFF101C28)
val Slate20 = Color(0xFF263746)
val Slate30 = Color(0xFF3D5263)
val Slate40 = Color(0xFF566C7D)
val Slate80 = Color(0xFFB7C9D8)
val Slate90 = Color(0xFFD3E5F4)

val Cyan10 = Color(0xFF00202A)
val Cyan20 = Color(0xFF003642)
val Cyan30 = Color(0xFF004E5B)
val Cyan40 = Color(0xFF006775)
val Cyan80 = Color(0xFF71D5E5)
val Cyan90 = Color(0xFF9CEBF5)

val Amber10 = Color(0xFF271900)
val Amber20 = Color(0xFF412D00)
val Amber30 = Color(0xFF5C4300)
val Amber40 = Color(0xFF785900)
val Amber80 = Color(0xFFE8C45E)
val Amber90 = Color(0xFFFFE19A)

// Neutrals
val Neutral10 = Color(0xFF111A24)
val Neutral20 = Color(0xFF27333F)
val Neutral90 = Color(0xFFDDE7F0)
val Neutral95 = Color(0xFFECF3F8)
val Neutral99 = Color(0xFFF8FBFD)
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
    Color(0xFF00658F),
    Color(0xFF2A5FA8),
    Color(0xFF785900),
    Color(0xFF006775),
    Color(0xFFB3261E),
    Color(0xFF14663F),
    Color(0xFF00687A),
    Color(0xFF6D4C41),
)

val ChartDark = listOf(
    Color(0xFF83D0F3),
    Color(0xFF9CC4FF),
    Color(0xFFE8C45E),
    Color(0xFF71D5E5),
    Color(0xFFFF897D),
    Color(0xFF5FD79B),
    Color(0xFF6FD3E8),
    Color(0xFFC9B0A4),
)
