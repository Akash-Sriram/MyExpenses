package org.totschnig.myexpenses.domain.loan

/**
 * Supported loan and EMI calculation types in the Indian banking system.
 */
enum class LoanType {
    REDUCING_BALANCE, // Standard Indian Home, Car, and Personal loans (Interest computed on remaining balance)
    FLAT_RATE,        // Fixed flat interest on original principal across the tenure
    NO_COST_EMI       // Zero interest consumer durable loans (e.g. Amazon, Flipkart)
}

enum class LoanStatus {
    ACTIVE,
    CLOSED
}

enum class InstallmentStatus {
    PENDING,
    PAID,
    SKIPPED
}

/**
 * Representation of a Loan / EMI facility.
 * Amounts are represented in the smallest currency unit (e.g. paise in INR: ₹1,000.00 = 100000).
 */
data class Loan(
    val id: Long = 0,
    val label: String,                   // e.g. "HDFC Car Loan", "Amazon No-Cost EMI"
    val lenderName: String = "",         // e.g. "HDFC Bank", "ICICI Bank"
    val loanType: LoanType,
    val principalAmount: Long,           // in minor units (paise)
    val interestRatePercent: Double,     // Annual percentage rate, e.g. 10.5 for 10.5% p.a.
    val tenureMonths: Int,               // Total installments count (e.g. 12, 36, 60)
    val monthlyEmiAmount: Long,          // Monthly installment amount in minor units
    val startDate: Long,                 // Epoch millis of loan start / disbursement
    val dueDayOfMonth: Int,              // 1 to 31: day of month EMI is debited
    val linkedAccountId: Long? = null,   // Bank account from which EMI is debited
    val status: LoanStatus = LoanStatus.ACTIVE
)

/**
 * Individual monthly installment in a loan schedule.
 */
data class EmiInstallment(
    val id: Long = 0,
    val loanId: Long,
    val installmentNumber: Int,          // 1 to N
    val dueDate: Long,                   // Epoch millis
    val principalPart: Long,             // Portion of EMI reducing principal
    val interestPart: Long,              // Portion of EMI covering interest
    val totalAmount: Long,               // Total installment amount (principalPart + interestPart)
    val paidDate: Long? = null,          // Epoch millis when paid, or null if unpaid
    val transactionId: Long? = null,     // Linked transaction ID in transactions table if recorded
    val status: InstallmentStatus = InstallmentStatus.PENDING
)

/**
 * Pre-computed progress helper for UI dashboards.
 */
data class LoanProgress(
    val loan: Loan,
    val paidInstallmentsCount: Int,
    val totalInstallmentsCount: Int,
    val totalPrincipalPaid: Long,
    val remainingPrincipal: Long,
    val totalInterestPaid: Long,
    val nextDueInstallment: EmiInstallment?
) {
    val progressPercent: Float
        get() = if (totalInstallmentsCount > 0) {
            (paidInstallmentsCount.toFloat() / totalInstallmentsCount.toFloat()) * 100f
        } else 0f
}

/**
 * Single row in an amortization schedule projection.
 */
data class AmortizationRow(
    val installmentNumber: Int,
    val openingBalance: Long,
    val emiAmount: Long,
    val principalPart: Long,
    val interestPart: Long,
    val closingBalance: Long
)

/**
 * Details and tracking for Credit Card accounts.
 */
data class CreditCardDetail(
    val id: Long = 0,
    val accountId: Long,                 // Links directly to accounts._id
    val creditLimit: Long,               // Credit limit in paise
    val statementDayOfMonth: Int,        // e.g. 15th
    val dueDayOfMonth: Int,              // e.g. 5th of next month
    val totalDueAmount: Long = 0,        // Amount from last generated statement
    val minimumDueAmount: Long = 0,      // Minimum due to avoid penalty
    val unbilledAmount: Long = 0,        // Current cycle spends since statement date
    val lastStatementDate: Long? = null,
    val lastDueDate: Long? = null
)
