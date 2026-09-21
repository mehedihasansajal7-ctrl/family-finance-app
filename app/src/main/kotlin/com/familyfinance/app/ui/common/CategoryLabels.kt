package com.familyfinance.app.ui.common

import com.familyfinance.app.domain.model.ExpenseCategory
import com.familyfinance.app.domain.model.IncomeCategory

/**
 * Category id to display label.
 *
 * Categories are persisted by stable string id (never by ordinal or by label), so renaming a label or
 * translating it can never change what a stored transaction means. Unknown ids degrade to a readable
 * fallback rather than crashing.
 */
object CategoryLabels {

    fun expense(id: String?): String =
        ExpenseCategory.fromId(id)?.label ?: fallback(id)

    fun income(id: String?): String =
        IncomeCategory.fromId(id)?.label ?: fallback(id)

    fun any(id: String?): String {
        if (id.isNullOrBlank()) return "Uncategorised"
        ExpenseCategory.fromId(id)?.let { return it.label }
        IncomeCategory.fromId(id)?.let { return it.label }
        return fallback(id)
    }

    fun expenseOptions(): List<ExpenseCategory> = ExpenseCategory.entries.toList()

    fun incomeOptions(): List<IncomeCategory> = IncomeCategory.entries.toList()

    private fun fallback(id: String?): String {
        if (id.isNullOrBlank()) return "Uncategorised"
        return id.replace('_', ' ')
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
