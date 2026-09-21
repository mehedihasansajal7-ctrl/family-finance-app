package com.familyfinance.app.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.AccountBalance
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.ui.common.CategoryLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV and PDF export.
 *
 * ## Privacy contract
 * Two separate entry points exist and they take different inputs:
 *  - [exportPersonalCsv] / [exportPersonalPdf] take the **owner's own** transactions. They are called
 *    with the signed-in user's rows and nothing else.
 *  - [exportFamilyPdf] takes a [FamilyAggregate] and category totals — aggregate types that carry no
 *    member attribution at all.
 *
 * There is deliberately no function that takes "all transactions for a family". A family export
 * therefore cannot contain another member's data, because the type it accepts cannot hold it.
 *
 * Money is written in decimal form from the integer minor units, so an exported spreadsheet always
 * reconciles exactly with the app.
 */
@Singleton
class Exporter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {

    // ------------------------------------------------------------------ personal CSV

    suspend fun exportPersonalCsv(
        ownerLabel: String,
        transactions: List<Transaction>,
        fromEpochDay: Long?,
        toEpochDay: Long?,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = newFile("personal-transactions-${stamp()}.csv")
            file.bufferedWriter().use { writer ->
                writer.appendLine(
                    listOf(
                        "Date", "Type", "Amount", "Currency", "Category",
                        "AccountId", "TransferFromAccountId", "TransferToAccountId", "Note",
                    ).joinToString(",")
                )
                transactions
                    .sortedBy { it.dateEpochDay }
                    .forEach { transaction ->
                        writer.appendLine(
                            listOf(
                                AppTime.formatEpochDay(transaction.dateEpochDay),
                                transaction.type.label,
                                transaction.amount.toBigDecimal().toPlainString(),
                                "BDT",
                                csv(CategoryLabels.any(transaction.categoryId)),
                                csv(transaction.accountId.orEmpty()),
                                csv(transaction.transferFromAccountId.orEmpty()),
                                csv(transaction.transferToAccountId.orEmpty()),
                                csv(transaction.note),
                            ).joinToString(",")
                        )
                    }
                writer.appendLine()
                writer.appendLine("# Owner: ${csv(ownerLabel)}")
                writer.appendLine("# Range: ${fromEpochDay?.let { AppTime.formatEpochDay(it) } ?: "start"} to ${toEpochDay?.let { AppTime.formatEpochDay(it) } ?: "today"}")
                writer.appendLine("# Personal export. Contains only the signed-in member's own records.")
            }
            file
        }
    }

    // ------------------------------------------------------------------ personal PDF

    suspend fun exportPersonalPdf(
        ownerLabel: String,
        month: YearMonth,
        totalBalance: Money,
        income: Money,
        expense: Money,
        accountBalances: List<AccountBalance>,
        categoryTotals: List<CategoryTotal>,
        trend: List<MonthlyPoint>,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = newFile("personal-report-${monthKey(month)}.pdf")
            writePdf(file) { page ->
                var y = 56f
                y = heading(page, "Personal financial report", y)
                y = line(page, "Member: $ownerLabel", y)
                y = line(page, "Period: ${AppTime.monthLabel(month)}", y)
                y = line(page, "Generated: ${AppTime.format(AppTime.today())}", y)
                y += 10f
                y = section(page, "Summary", y)
                y = line(page, "Total balance: ${MoneyFormat.bdt(totalBalance, showSymbol = true)}", y)
                y = line(page, "Income this month: ${MoneyFormat.bdt(income)}", y)
                y = line(page, "Expense this month: ${MoneyFormat.bdt(expense)}", y)
                y = line(page, "Savings this month: ${MoneyFormat.bdt(income - expense)}", y)

                y += 10f
                y = section(page, "Accounts", y)
                accountBalances.forEach { row ->
                    y = line(
                        page,
                        "${row.account.name} (${row.account.type.label})" +
                            row.account.last4?.let { " ${MoneyFormat.maskIdentifier(it)}" }.orEmpty() +
                            "   ${MoneyFormat.bdt(row.balance)}",
                        y,
                    )
                }

                y += 10f
                y = section(page, "Expense by category", y)
                if (categoryTotals.isEmpty()) {
                    y = line(page, "No expenses recorded.", y)
                } else {
                    categoryTotals.forEach { item ->
                        y = line(page, "${CategoryLabels.expense(item.categoryId)}   ${MoneyFormat.bdt(item.total)}", y)
                    }
                }

                y += 10f
                y = section(page, "6-month trend", y)
                trend.forEach { point ->
                    y = line(
                        page,
                        "${AppTime.monthLabelShort(point.month)}   in ${MoneyFormat.bdt(point.income)}   " +
                            "out ${MoneyFormat.bdt(point.expense)}",
                        y,
                    )
                }
                y += 14f
                line(page, "Private document — contains one member's own data only.", y)
            }
            file
        }
    }

    // ------------------------------------------------------------------ family PDF (aggregate only)

    suspend fun exportFamilyPdf(
        familyName: String,
        month: YearMonth,
        totalIncome: Money,
        totalExpense: Money,
        totalSavings: Money,
        netWorth: Money?,
        memberCount: Int,
        categoryTotals: List<CategoryTotal>,
        trend: List<MonthlyPoint>,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = newFile("family-report-${monthKey(month)}.pdf")
            writePdf(file) { page ->
                var y = 56f
                y = heading(page, familyName, y)
                y = line(page, "Family financial summary — ${AppTime.monthLabel(month)}", y)
                y = line(page, "Generated: ${AppTime.format(AppTime.today())}", y)
                y += 10f
                y = section(page, "Totals (all members combined)", y)
                y = line(page, "Total income: ${MoneyFormat.bdt(totalIncome)}", y)
                y = line(page, "Total expense: ${MoneyFormat.bdt(totalExpense)}", y)
                y = line(page, "Total savings: ${MoneyFormat.bdt(totalSavings)}", y)
                if (netWorth != null) y = line(page, "Family net worth: ${MoneyFormat.bdt(netWorth)}", y)
                y = line(page, "Contributing members: $memberCount", y)

                y += 10f
                y = section(page, "Expense by category", y)
                if (categoryTotals.isEmpty()) {
                    y = line(page, "No expenses recorded.", y)
                } else {
                    categoryTotals.forEach { item ->
                        y = line(page, "${CategoryLabels.expense(item.categoryId)}   ${MoneyFormat.bdt(item.total)}", y)
                    }
                }

                y += 10f
                y = section(page, "6-month trend", y)
                trend.forEach { point ->
                    y = line(
                        page,
                        "${AppTime.monthLabelShort(point.month)}   in ${MoneyFormat.bdt(point.income)}   " +
                            "out ${MoneyFormat.bdt(point.expense)}",
                        y,
                    )
                }
                y += 14f
                line(page, "This report contains family totals only. No member-wise figures are included.", y)
            }
            file
        }
    }

    // ------------------------------------------------------------------ sharing

    /**
     * Opens the system share sheet for an exported file.
     *
     * The URI is a scoped FileProvider URI, so the receiving app gets read access to that one file
     * and nothing else — the app's private storage is never exposed.
     */
    fun shareIntent(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun newFile(name: String): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun writePdf(file: File, block: (android.graphics.pdf.PdfDocument.Page) -> Unit) {
        val document = PdfDocument()
        // A4 at 72dpi.
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        block(page)
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun heading(page: android.graphics.pdf.PdfDocument.Page, text: String, y: Float): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            isFakeBoldText = true
        }
        page.canvas.drawText(text, 40f, y, paint)
        return y + 28f
    }

    private fun section(page: android.graphics.pdf.PdfDocument.Page, text: String, y: Float): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
            isFakeBoldText = true
        }
        page.canvas.drawText(text, 40f, y, paint)
        return y + 20f
    }

    private fun line(page: android.graphics.pdf.PdfDocument.Page, text: String, y: Float): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
        }
        // 842pt page height with a 60pt bottom margin; wrap long lines rather than running off-page.
        val maxWidth = 595f - 80f
        val words = text.split(' ')
        val builder = StringBuilder()
        var cursor = y
        words.forEach { word ->
            val candidate = if (builder.isEmpty()) word else "${builder} $word"
            if (paint.measureText(candidate) > maxWidth && builder.isNotEmpty()) {
                page.canvas.drawText(builder.toString(), 40f, cursor, paint)
                cursor += 16f
                builder.clear()
                builder.append(word)
            } else {
                builder.clear()
                builder.append(candidate)
            }
        }
        if (builder.isNotEmpty()) {
            page.canvas.drawText(builder.toString(), 40f, cursor, paint)
            cursor += 16f
        }
        return cursor
    }

    private fun csv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun stamp(): String = AppTime.today().toString()

    private fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)
}
