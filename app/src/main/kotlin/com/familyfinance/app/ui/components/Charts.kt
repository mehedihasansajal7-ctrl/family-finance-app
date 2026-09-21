package com.familyfinance.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.ui.theme.AppTheme

/**
 * Charts.
 *
 * Implemented directly on Compose `Canvas` rather than pulling in a charting library. The reasons are
 * practical: the three chart shapes this app needs are simple, a Canvas implementation has no
 * version-conflict surface, it renders identically offline, and — most importantly — the data
 * mapping is a pure function that can be unit-tested without a rendering harness
 * (see `ChartMathTest`).
 *
 * Every chart is accompanied by a text label or legend, so no information is conveyed by colour alone.
 */

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

/** A rounded progress track. `fraction` is clamped to 0..1. */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/**
 * Income vs expense over the last months.
 *
 * Both series share one y-scale so the gap between them is meaningful, and the scale is derived from
 * the data (never hard-coded), so a family with small numbers still sees a readable chart.
 */
@Composable
fun MonthlyTrendChart(
    points: List<MonthlyPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val semantics = AppTheme.semantics
    val incomeValues = points.map { it.income.minor.toFloat() }
    val expenseValues = points.map { it.expense.minor.toFloat() }
    val peak = (incomeValues + expenseValues).maxOrNull() ?: 0f
    val scale = if (peak <= 0f) 1f else peak

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val w = size.width
            val h = size.height
            val pad = 6f
            val usableW = w - pad * 2
            val usableH = h - pad * 2

            // Baseline grid: three faint rules give the eye a scale without adding labels.
            repeat(3) { index ->
                val y = pad + usableH * (index + 1) / 4f
                drawLine(
                    color = Color.Gray.copy(alpha = 0.18f),
                    start = Offset(pad, y),
                    end = Offset(w - pad, y),
                    strokeWidth = 1f,
                )
            }

            fun pointAt(index: Int, value: Float): Offset {
                val step = if (points.size == 1) 0f else usableW / (points.size - 1)
                val x = pad + step * index
                val y = pad + usableH * (1f - (value / scale))
                return Offset(x, y)
            }

            fun linePath(values: List<Float>): Path = Path().apply {
                values.forEachIndexed { index, value ->
                    val p = pointAt(index, value)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }

            fun areaPath(values: List<Float>): Path = Path().apply {
                val line = linePath(values)
                addPath(line)
                val first = pointAt(0, values.first())
                val last = pointAt(values.lastIndex, values.last())
                lineTo(last.x, h - pad)
                lineTo(first.x, h - pad)
                close()
            }

            drawPath(areaPath(incomeValues), semantics.income.copy(alpha = 0.14f))
            drawPath(areaPath(expenseValues), semantics.expense.copy(alpha = 0.12f))

            drawPath(linePath(incomeValues), semantics.income, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawPath(linePath(expenseValues), semantics.expense, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

            incomeValues.forEachIndexed { index, value ->
                drawCircle(semantics.income, radius = 3.5f, center = pointAt(index, value))
            }
            expenseValues.forEachIndexed { index, value ->
                drawCircle(semantics.expense, radius = 3.5f, center = pointAt(index, value))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { point ->
                Text(
                    text = AppTime.monthLabelShort(point.month).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch("Income", semantics.income)
            LegendSwatch("Expense", semantics.expense)
        }
    }
}

@Composable
fun LegendSwatch(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Expense-by-category, as a donut plus a legend.
 *
 * A legend is mandatory: the slice colours are a visual aid, not the data. Each legend row states the
 * category and its share in text.
 */
@Composable
fun ExpenseDonut(
    totals: List<CategoryTotal>,
    total: Money,
    modifier: Modifier = Modifier,
    labelFor: (String) -> String,
) {
    val chartColors = AppTheme.semantics.chart
    if (totals.isEmpty() || total.isZero) {
        Text(
            "No expenses recorded for this period.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val shares = ChartMath.shares(totals.map { it.total.minor.toFloat() })

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(modifier = Modifier.size(124.dp)) {
            val stroke = size.minDimension * 0.22f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            var start = -90f
            shares.forEachIndexed { index, share ->
                val sweep = share * 360f
                // A hairline gap keeps adjacent slices visually separable.
                val gap = if (shares.size > 1) 1.6f else 0f
                drawArc(
                    color = chartColors[index % chartColors.size],
                    startAngle = start + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            totals.take(6).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(chartColors[index % chartColors.size]),
                    )
                    Text(
                        text = labelFor(item.categoryId),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompactMoneyText(item.total)
                }
            }
            if (totals.size > 6) {
                Text(
                    "+${totals.size - 6} more categories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Horizontal bar list for category breakdowns.
 *
 * Bars are scaled against the largest value in the set (not the total) so small categories remain
 * visible next to a dominant one.
 */
@Composable
fun CategoryBarList(
    totals: List<CategoryTotal>,
    modifier: Modifier = Modifier,
    labelFor: (String) -> String,
    color: Color? = null,
    maxRows: Int = 8,
) {
    if (totals.isEmpty()) {
        Text(
            "Nothing to show yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val peak = totals.maxOf { it.total.minor }.coerceAtLeast(1L)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        totals.take(maxRows).forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        labelFor(item.categoryId),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CompactMoneyText(item.total)
                }
                ProgressTrack(
                    fraction = item.total.minor.toFloat() / peak.toFloat(),
                    color = color ?: MaterialTheme.colorScheme.primary,
                    height = 6.dp,
                )
            }
        }
    }
}

/**
 * Pure chart maths, separated from rendering so it can be tested on the JVM.
 *
 * `shares` returns fractions that sum to exactly 1.0 (within float precision), which is what makes
 * the donut close cleanly instead of leaving a sliver at the top.
 */
object ChartMath {

    fun shares(values: List<Float>): List<Float> {
        val cleaned = values.map { if (it.isFinite() && it > 0f) it else 0f }
        val total = cleaned.sum()
        if (total <= 0f) return cleaned.map { 0f }
        return cleaned.map { it / total }
    }

    /** Scales values into 0..1 against the largest value in the set. */
    fun normalise(values: List<Float>): List<Float> {
        val peak = values.filter { it.isFinite() }.maxOrNull() ?: 0f
        if (peak <= 0f) return values.map { 0f }
        return values.map { (if (it.isFinite() && it > 0f) it else 0f) / peak }
    }
}
