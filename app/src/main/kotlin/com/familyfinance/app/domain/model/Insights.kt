package com.familyfinance.app.domain.model

/** A plain-language observation about the family's money for this month. */
data class Insight(
    val kind: Kind,
    val title: String,
    val detail: String,
) {
    enum class Kind { UP, DOWN, INFO, WARNING }
}
