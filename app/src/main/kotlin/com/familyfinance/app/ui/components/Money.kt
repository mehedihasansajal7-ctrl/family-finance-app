package com.familyfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.ui.theme.AppTheme
import com.familyfinance.app.ui.theme.MoneyDisplayStyle
import com.familyfinance.app.ui.theme.MoneyRowStyle

/**
 * Money rendering.
 *
 * Amounts are always rendered with the ৳ symbol and South-Asian grouping, and income/expense always
 * carry an explicit sign in addition to colour — colour alone is not an accessible signal.
 */

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MoneyRowStyle,
    signed: Boolean = false,
    color: Color? = null,
) {
    val bengali = AppTheme.bengaliDigits
    val prefix = when {
        !signed -> ""
        money.isNegative -> "−"
        money.isPositive -> "+"
        else -> ""
    }
    Text(
        text = prefix + MoneyFormat.bdt(money, bengaliDigits = bengali),
        modifier = modifier,
        style = style,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SignedMoneyText(
    money: Money,
    type: TransactionType,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MoneyRowStyle,
) {
    val semantics = AppTheme.semantics
    val signed = when (type) {
        TransactionType.INCOME -> Money.ofMinor(money.minor)
        TransactionType.EXPENSE -> Money.ofMinor(-money.minor)
        TransactionType.TRANSFER -> money
    }
    MoneyText(money = signed, modifier = modifier, style = style, signed = true, color = semantics.forType(type))
}

@Composable
fun CompactMoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
    color: Color? = null,
) {
    Text(
        text = MoneyFormat.bdtCompact(money, bengaliDigits = AppTheme.bengaliDigits),
        modifier = modifier,
        style = style,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** A large headline amount used on the dashboard and My Finance header. */
@Composable
fun MoneyHeadline(
    money: Money,
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    caption: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormat.bdt(money, bengaliDigits = AppTheme.bengaliDigits),
            style = MoneyDisplayStyle,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Masked financial identifier.
 *
 * The full number is never rendered. Only the last four characters are shown, and they are the only
 * part ever persisted in plain text.
 */
@Composable
fun MaskedIdentifierText(
    last4: String?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = MoneyFormat.maskIdentifier(last4),
        modifier = modifier,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Small coloured pill used for categories, roles and statuses. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A labelled row: description on the left, value on the right. */
@Composable
fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value()
    }
}

/** Thin horizontal rule used between list rows inside a card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(top = 1.dp)
    )
}
