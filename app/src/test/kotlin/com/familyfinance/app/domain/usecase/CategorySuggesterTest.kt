package com.familyfinance.app.domain.usecase

import com.familyfinance.app.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategorySuggesterTest {

    @Test
    fun `suggests food for a grocery note`() {
        assertThat(CategorySuggester.suggest("Monthly grocery from bazar", TransactionType.EXPENSE))
            .isEqualTo("food_grocery")
    }

    @Test
    fun `suggests transport for a cng ride`() {
        assertThat(CategorySuggester.suggest("CNG fare to office", TransactionType.EXPENSE))
            .isEqualTo("transport")
    }

    @Test
    fun `suggests mobile for a recharge`() {
        assertThat(CategorySuggester.suggest("GP recharge", TransactionType.EXPENSE))
            .isEqualTo("mobile")
    }

    @Test
    fun `suggests salary for income`() {
        assertThat(CategorySuggester.suggest("September salary", TransactionType.INCOME))
            .isEqualTo("salary")
    }

    @Test
    fun `returns null for a transfer and for unknown text`() {
        assertThat(CategorySuggester.suggest("Food", TransactionType.TRANSFER)).isNull()
        assertThat(CategorySuggester.suggest("random words", TransactionType.EXPENSE)).isNull()
    }

    @Test
    fun `is case-insensitive`() {
        assertThat(CategorySuggester.suggest("ELECTRICITY BILL DPDC", TransactionType.EXPENSE))
            .isEqualTo("electricity")
    }
}
