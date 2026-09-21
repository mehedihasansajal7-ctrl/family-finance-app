package com.familyfinance.app.domain.model

/** Who a member is inside the family workspace. */
enum class Role(val id: String, val label: String, val labelBn: String) {
    OWNER("owner", "Owner", "মালিক"),
    ADMIN("admin", "Admin", "অ্যাডমিন"),
    MEMBER("member", "Member", "সদস্য");

    val isAdmin: Boolean get() = this == OWNER || this == ADMIN

    /**
     * Admins manage the *family workspace* only. They never gain read access to another member's
     * private financial records — see [com.familyfinance.app.domain.security.FamilyAccessPolicy].
     */
    val canManageFamily: Boolean get() = isAdmin

    companion object {
        fun fromId(id: String?): Role = entries.firstOrNull { it.id == id } ?: MEMBER
    }
}

enum class MemberStatus(val id: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    REMOVED("removed");

    companion object {
        fun fromId(id: String?): MemberStatus = entries.firstOrNull { it.id == id } ?: ACTIVE
    }
}

enum class TransactionType(val id: String, val label: String, val labelBn: String) {
    INCOME("income", "Income", "আয়"),
    EXPENSE("expense", "Expense", "ব্যয়"),
    TRANSFER("transfer", "Transfer", "স্থানান্তর");

    companion object {
        fun fromId(id: String?): TransactionType? = entries.firstOrNull { it.id == id }
    }
}

enum class AccountType(
    val id: String,
    val label: String,
    val labelBn: String,
    /** Mobile-financial-service wallets behave slightly differently in the UI. */
    val isWallet: Boolean = false,
    /** Credit lines start negative when money is owed. */
    val isCredit: Boolean = false,
    val hasInstitution: Boolean = false,
) {
    BANK("bank", "Bank Account", "ব্যাংক হিসাব", hasInstitution = true),
    BKASH("bkash", "bKash", "বিকাশ", isWallet = true),
    NAGAD("nagad", "Nagad", "নগদ", isWallet = true),
    ROCKET("rocket", "Rocket", "রকেট", isWallet = true),
    UPAY("upay", "Upay", "উপায়", isWallet = true),
    CASH("cash", "Cash", "নগদ টাকা"),
    DEBIT_CARD("debit_card", "Debit Card", "ডেবিট কার্ড", hasInstitution = true),
    CREDIT_CARD("credit_card", "Credit Card", "ক্রেডিট কার্ড", isCredit = true, hasInstitution = true),
    DPS("dps", "DPS", "ডিপিএস", hasInstitution = true),
    FDR("fdr", "FDR", "এফডিআর", hasInstitution = true),
    SAVINGS_CERTIFICATE("savings_certificate", "Savings Certificate", "সঞ্চয়পত্র", hasInstitution = true),
    INVESTMENT("investment", "Investment", "বিনিয়োগ", hasInstitution = true),
    OTHER("other", "Other", "অন্যান্য");

    companion object {
        fun fromId(id: String?): AccountType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

enum class IncomeCategory(val id: String, val label: String, val labelBn: String) {
    SALARY("salary", "Salary", "বেতন"),
    BUSINESS("business", "Business", "ব্যবসা"),
    FREELANCING("freelancing", "Freelancing", "ফ্রিল্যান্সিং"),
    BONUS("bonus", "Bonus", "বোনাস"),
    RENTAL("rental", "Rental", "ভাড়া আয়"),
    INTEREST_PROFIT("interest_profit", "Interest / Profit", "সুদ / মুনাফা"),
    GIFT("gift", "Gift", "উপহার"),
    OTHER("other", "Other", "অন্যান্য");

    companion object {
        fun fromId(id: String?): IncomeCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class ExpenseCategory(val id: String, val label: String, val labelBn: String) {
    FOOD_GROCERY("food_grocery", "Food & Grocery", "খাদ্য ও মুদি"),
    HOUSING_RENT("housing_rent", "Housing / Rent", "বাসা / ভাড়া"),
    ELECTRICITY("electricity", "Electricity", "বিদ্যুৎ"),
    GAS("gas", "Gas", "গ্যাস"),
    WATER("water", "Water", "পানি"),
    INTERNET("internet", "Internet", "ইন্টারনেট"),
    MOBILE("mobile", "Mobile", "মোবাইল"),
    TRANSPORT("transport", "Transport", "যাতায়াত"),
    EDUCATION("education", "Education", "শিক্ষা"),
    MEDICAL("medical", "Medical", "চিকিৎসা"),
    SHOPPING("shopping", "Shopping", "কেনাকাটা"),
    CLOTHING("clothing", "Clothing", "পোশাক"),
    ENTERTAINMENT("entertainment", "Entertainment", "বিনোদন"),
    FAMILY("family", "Family", "পরিবার"),
    DONATION_CHARITY("donation_charity", "Donation / Charity", "দান / সদকা"),
    LOAN_EMI("loan_emi", "Loan / EMI", "ঋণ / কিস্তি"),
    TRAVEL("travel", "Travel", "ভ্রমণ"),
    OTHER("other", "Other", "অন্যান্য");

    companion object {
        fun fromId(id: String?): ExpenseCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class RecurrenceFrequency(val id: String, val label: String) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromId(id: String?): RecurrenceFrequency? = entries.firstOrNull { it.id == id }
    }
}

enum class GoalStatus(val id: String, val label: String) {
    ACTIVE("active", "Active"),
    ACHIEVED("achieved", "Achieved"),
    PAUSED("paused", "Paused"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun fromId(id: String?): GoalStatus = entries.firstOrNull { it.id == id } ?: ACTIVE
    }
}

enum class BillStatus(val id: String, val label: String) {
    PENDING("pending", "Pending"),
    PAID("paid", "Paid"),
    OVERDUE("overdue", "Overdue"),
    SKIPPED("skipped", "Skipped");

    companion object {
        fun fromId(id: String?): BillStatus = entries.firstOrNull { it.id == id } ?: PENDING
    }
}

enum class LoanDirection(val id: String, val label: String, val labelBn: String) {
    BORROWED("borrowed", "Borrowed (I owe)", "ধার নিয়েছি"),
    LENT("lent", "Lent (owed to me)", "ধার দিয়েছি");

    companion object {
        fun fromId(id: String?): LoanDirection = entries.firstOrNull { it.id == id } ?: BORROWED
    }
}

/** Which budget alert band a category has crossed. Drives the colour of the budget row. */
enum class BudgetAlert { NONE, WARNING, CRITICAL, EXCEEDED }

/** Offline-first sync state for locally created records. */
enum class SyncState(val id: String) {
    SYNCED("synced"),
    PENDING("pending"),
    FAILED("failed");

    companion object {
        fun fromId(id: String?): SyncState = entries.firstOrNull { it.id == id } ?: SYNCED
    }
}
