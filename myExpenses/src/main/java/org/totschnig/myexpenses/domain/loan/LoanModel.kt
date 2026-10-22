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
    val interestRatePercent: Double,     // Annual percentage rate, e.g. 12.5 for 12.5% p.a.
    val tenureMonths: Int,               // Total installments count (e.g. 12, 36, 60)
    val monthlyEmiAmount: Long,          // Monthly installment amount in minor units
    val startDate: Long,                 // Epoch millis of loan start / disbursement
    val disbursalDate: Long = startDate, // Epoch millis of disbursal
    val firstEmiDate: Long = 0L,         // Epoch millis of 1st EMI due date (0L = auto next month on dueDayOfMonth)
    val actualEmiAmount: Long? = null,   // Optional user override
    val dueDayOfMonth: Int,              // 1 to 31: day of month EMI is debited
    val linkedAccountId: Long? = null,   // Bank account from which EMI is debited
    val status: LoanStatus = LoanStatus.ACTIVE,
    val autoDebit: Boolean = false       // Auto-debit and auto-record transaction on due date
)

/**
 * Encapsulates an installment due for notification or automatic processing.
 */
data class DueInstallmentInfo(
    val installment: EmiInstallment,
    val loan: Loan,
    val accountLabel: String? = null
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
    val totalAmount: Long,               // Total installment amount (principalPart + interestPart + gst)
    val gstPart: Long = 0L,              // 18% GST on interest for Credit Card EMIs
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
    val closingBalance: Long,
    val isBrokenPeriod: Boolean = false,
    val gstOnInterest: Long = 0L
)

/**
 * Details and tracking for Credit Card EMIs (Merchant / No-Cost).
 */
data class CreditCardEmi(
    val id: Long = 0,
    val cardAccountId: Long,             // Links to accounts._id
    val label: String,                   // e.g. "Flipkart No-Cost EMI - iPhone 15"
    val merchantName: String = "",       // e.g. "Flipkart", "Amazon"
    val cartPrice: Long,                 // Original cart price in paise (e.g. ₹18,118.00 = 1811800)
    val merchantDiscount: Long = 0L,     // Upfront merchant discount in paise (e.g. ₹815.82 = 81582)
    val bookedPrincipal: Long,           // Principal booked on card in paise (e.g. ₹17,302.00 = 1730200)
    val interestRatePercent: Double,     // Annual rate, e.g. 15.99
    val tenureMonths: Int,               // Tenure (e.g. 6)
    val monthlyBaseEmi: Long,            // Base EMI before GST in paise
    val processingFee: Long = 0L,        // Processing fee in paise (e.g. ₹199.00 = 19900)
    val gstOnProcessingFee: Long = 0L,   // 18% GST on processing fee in paise
    val gstRatePercent: Double = 18.0,   // Standard GST on card interest (18.0%)
    val startDate: Long,                 // Epoch millis
    val dueDayOfMonth: Int,              // 1 to 31
    val status: LoanStatus = LoanStatus.ACTIVE
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
