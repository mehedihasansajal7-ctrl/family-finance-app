package com.familyfinance.app.domain.usecase

import com.familyfinance.app.domain.model.TransactionType

/**
 * Suggests a category from the free-text note.
 *
 * Deterministic keyword matching — no model, no network. The result is only ever a *suggestion*:
 * the Add screen applies it solely when the member has not picked a category yet, so it can never
 * overwrite a deliberate choice. Returns `null` when nothing matches, so the caller keeps "Other".
 */
object CategorySuggester {

    private val EXPENSE_RULES: List<Pair<String, List<String>>> = listOf(
        "food_grocery" to listOf(
            "food", "grocery", "grocer", "bazar", "bazaar", "restaurant", "lunch", "dinner",
            "breakfast", "snack", "rice", "vegetable", "meat", "fish", "tea", "coffee", "pizza", "burger",
        ),
        "electricity" to listOf("electric", "electricity", "dpdc", "desco", "palli bidyut"),
        "gas" to listOf("gas", "titas"),
        "water" to listOf("water", "wasa", "water bill"),
        "internet" to listOf("internet", "wifi", "broadband", "link3", "amber it", "router"),
        "mobile" to listOf("mobile", "recharge", "grameenphone", "gp ", "robi", "banglalink", "airtel", "teletalk", "sim"),
        "housing_rent" to listOf("rent", "house rent", "housing", "flat", "apartment", "landlord"),
        "transport" to listOf(
            "transport", "bus", "cng", "uber", "pathao", "rickshaw", "fuel", "petrol", "diesel",
            "octane", "taxi", "train", "fare", "toll",
        ),
        "education" to listOf("school", "college", "tuition", "education", "exam", "university", "admission", "book", "stationery"),
        "medical" to listOf("medicine", "medical", "doctor", "hospital", "clinic", "pharmacy", "diagnostic", "lab test", "checkup"),
        "shopping" to listOf("shopping", "daraz", "amazon", "market", "gadget", "electronics"),
        "clothing" to listOf("cloth", "clothing", "dress", "shirt", "saree", "pant", "shoe", "garment"),
        "entertainment" to listOf("movie", "cinema", "netflix", "spotify", "entertainment", "game", "subscription"),
        "donation_charity" to listOf("donation", "charity", "zakat", "sadaqah", "mosque", "madrasa", "fitra"),
        "loan_emi" to listOf("emi", "loan", "installment", "instalment", "mortgage"),
        "travel" to listOf("travel", "tour", "flight", "air ticket", "hotel", "visa", "holiday"),
        "family" to listOf("family", "kids", "child", "baby"),
    )

    private val INCOME_RULES: List<Pair<String, List<String>>> = listOf(
        "salary" to listOf("salary", "payroll", "wage", "monthly pay"),
        "freelancing" to listOf("freelance", "freelancing", "upwork", "fiverr", "outsource"),
        "business" to listOf("business", "shop sale", "sales", "trade", "profit share", "commission"),
        "rental" to listOf("rent received", "rental", "tenant", "house rent received"),
        "interest_profit" to listOf("interest", "profit", "dividend", "bank profit"),
        "bonus" to listOf("bonus", "incentive", "eid bonus", "festival bonus"),
        "gift" to listOf("gift", "gifted", "upohar"),
    )

    fun suggest(note: String, type: TransactionType): String? {
        if (type == TransactionType.TRANSFER) return null
        val text = note.lowercase().trim()
        if (text.isBlank()) return null
        val rules = if (type == TransactionType.INCOME) INCOME_RULES else EXPENSE_RULES
        return rules.firstOrNull { (_, keywords) -> keywords.any { text.contains(it) } }?.first
    }
}
