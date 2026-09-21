package com.familyfinance.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Semantic money colours.
 *
 * These are held outside the Material colour scheme so that "income" is always green and "expense"
 * always red regardless of the family's chosen accent. Colour alone is never the only signal —
 * every amount also carries a sign and an icon.
 */
data class SemanticColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val credit: Color,
    val warning: Color,
    val chart: List<Color>,
) {
    fun forType(type: com.familyfinance.app.domain.model.TransactionType): Color = when (type) {
        com.familyfinance.app.domain.model.TransactionType.INCOME -> income
        com.familyfinance.app.domain.model.TransactionType.EXPENSE -> expense
        com.familyfinance.app.domain.model.TransactionType.TRANSFER -> transfer
    }
}

private val LightSemanticColors = SemanticColors(
    income = IncomeLight,
    expense = ExpenseLight,
    transfer = TransferLight,
    credit = CreditLight,
    warning = WarningLight,
    chart = ChartLight,
)

private val DarkSemanticColors = SemanticColors(
    income = IncomeDark,
    expense = ExpenseDark,
    transfer = TransferDark,
    credit = CreditDark,
    warning = WarningDark,
    chart = ChartDark,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** When true, ৳ amounts render with Bengali numerals (০১২৩৪৫৬৭৮৯). */
val LocalBengaliDigits = staticCompositionLocalOf { false }

private val LightScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral100,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Sage40,
    onSecondary = Neutral100,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Plum40,
    onTertiary = Neutral100,
    tertiaryContainer = Plum90,
    onTertiaryContainer = Plum10,
    error = ExpenseLight,
    onError = Neutral100,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral20,
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C5),
)

private val DarkScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Sage80,
    onSecondary = Sage10,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Plum80,
    onTertiary = Plum10,
    tertiaryContainer = Plum30,
    onTertiaryContainer = Plum90,
    error = ExpenseDark,
    onError = Color(0xFF690005),
    background = Color(0xFF101416),
    onBackground = Neutral90,
    surface = Color(0xFF101416),
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

@Composable
fun FamilyFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    bengaliDigits: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val semantics = if (darkTheme) DarkSemanticColors else LightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSemanticColors provides semantics,
        LocalBengaliDigits provides bengaliDigits,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Accessors: `AppTheme.semantics.income`, `AppTheme.bengaliDigits`. */
object AppTheme {
    val semantics: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current

    val bengaliDigits: Boolean
        @Composable @ReadOnlyComposable get() = LocalBengaliDigits.current
}
