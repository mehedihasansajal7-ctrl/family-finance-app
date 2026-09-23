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
    primary = Navy40,
    onPrimary = Neutral100,
    primaryContainer = Navy90,
    onPrimaryContainer = Navy10,
    secondary = Slate40,
    onSecondary = Neutral100,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Amber40,
    onTertiary = Neutral100,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    error = ExpenseLight,
    onError = Neutral100,
    background = Color(0xFFF7FAFC),
    onBackground = Neutral10,
    surface = Color(0xFFF7FAFC),
    onSurface = Neutral10,
    surfaceVariant = Color(0xFFE8F0F6),
    onSurfaceVariant = Neutral20,
    outline = Color(0xFF617486),
    outlineVariant = Color(0xFFB9C9D5),
)

private val DarkScheme = darkColorScheme(
    primary = Navy80,
    onPrimary = Navy10,
    primaryContainer = Navy30,
    onPrimaryContainer = Navy90,
    secondary = Slate80,
    onSecondary = Slate10,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Amber80,
    onTertiary = Amber10,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = ExpenseDark,
    onError = Color(0xFF690005),
    background = Color(0xFF09131E),
    onBackground = Neutral90,
    surface = Color(0xFF0D1925),
    onSurface = Neutral90,
    surfaceVariant = Color(0xFF1B2A38),
    onSurfaceVariant = Color(0xFFB9CAD8),
    outline = Color(0xFF8297A8),
    outlineVariant = Color(0xFF3B4D5D),
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
