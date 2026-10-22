package org.totschnig.myexpenses.domain.commitment

import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

enum class InsuranceType {
    LIFE,
    HEALTH,
    VEHICLE,
    HOME,
    TERM,
    OTHER
}

enum class PremiumFrequency {
    MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY;

    fun monthsInterval(): Int = when (this) {
        MONTHLY -> 1
        QUARTERLY -> 3
        HALF_YEARLY -> 6
        YEARLY -> 12
    }
}

enum class CommitmentStatus {
    ACTIVE,
    PAUSED,
    MATURED,
    LAPSED,
    CANCELLED
}

enum class BillingCycle {
    WEEKLY,
    MONTHLY,
    BI_MONTHLY,
    QUARTERLY,
    YEARLY;

    fun monthsInterval(): Int = when (this) {
        WEEKLY -> 0
        MONTHLY -> 1
        BI_MONTHLY -> 2
        QUARTERLY -> 3
        YEARLY -> 12
    }
}

enum class CommitmentType {
    ALL,
    LOAN,
    INSURANCE,
    SUBSCRIPTION,
    UTILITY,
    CREDIT_CARD,
    TEMPLATE
}

enum class PaymentRecordStatus {
    PENDING,
    PAID,
    SKIPPED
}

data class InsurancePolicy(
    val id: Long = 0,
    val policyName: String,               // e.g. "S Ganapathy - LIC"
    val policyNumber: String = "",        // Policy #
    val insurerName: String = "",         // e.g. "LIC", "HDFC Life", "Star Health"
    val insuranceType: InsuranceType = InsuranceType.LIFE,
    val premiumAmount: Long,              // in minor units (e.g. 95300 = ₹953.00)
    val frequency: PremiumFrequency = PremiumFrequency.MONTHLY,
    val startDate: Long,                  // Epoch millis
    val endDate: Long? = null,            // Maturity / expiry date epoch millis
    val dueDayOfMonth: Int = 1,           // 1..31
    val dueMonth: Int? = null,            // 1..12 (for Yearly/Half-Yearly)
    val sumAssured: Long = 0L,            // in minor units
    val linkedAccountId: Long? = null,
    val templateId: Long? = null,
    val autoDebit: Boolean = false,
    val status: CommitmentStatus = CommitmentStatus.ACTIVE,
    val payerMode: String = "SELF",       // "SELF" or "FAMILY_SHARED"
    val payeeId: Long? = null
)

data class Subscription(
    val id: Long = 0,
    val serviceName: String,              // e.g. "Bitwarden", "YouTube Premium", "Antigravity Plus"
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val amount: Long,                     // in minor units
    val categoryId: Long? = null,
    val startDate: Long,                  // Epoch millis
    val nextRenewalDate: Long,            // Epoch millis
    val linkedAccountId: Long? = null,
    val templateId: Long? = null,
    val autoDebit: Boolean = false,
    val status: CommitmentStatus = CommitmentStatus.ACTIVE,
    val notes: String = "",
    val payeeId: Long? = null,
    val isVariableAmount: Boolean = false,
    val cancelBeforeRenewal: Boolean = false
)

data class UtilityMeter(
    val id: Long = 0,
    val subscriptionId: Long,
    val meterNumber: String,        // Consumer No. e.g. "092140051234"
    val meterLabel: String,         // e.g. "Single Phase", "Three Phase", "Nandhini", "Tenant", "Milk Card"
    val estimatedAmount: Long,      // in minor units (paise)
    val matchingKeywords: String = "", // Comma-separated e.g. "Single phase, Domestic"
    val createdAt: Long = System.currentTimeMillis(),
    val unitPrice: Long = 0L,       // in minor units (paise), e.g. 1850L for ₹18.50 per packet
    val unitsPerDay: Double = 0.0   // packets/units per day, e.g. 2.0
) {
    val isDailyConsumption: Boolean get() = unitPrice > 0L && unitsPerDay > 0.0

    fun calculateAmountForDays(days: Int): Long {
        if (isDailyConsumption) {
            return (days * unitsPerDay * unitPrice).roundToLong()
        }
        return estimatedAmount
    }

    fun calculateAmountForDate(dateMillis: Long): Long {
        if (isDailyConsumption) {
            val localDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            return calculateAmountForDays(localDate.lengthOfMonth())
        }
        return estimatedAmount
    }
}

data class MeterCycleStatus(
    val meter: UtilityMeter,
    val isPaid: Boolean,
    val paidAmount: Long? = null,
    val paidDate: Long? = null,
    val transactionId: Long? = null,
    val cycleCalculatedAmount: Long = meter.estimatedAmount
)

data class CommitmentPayment(
    val id: Long = 0,
    val commitmentType: String,           // "INSURANCE", "SUBSCRIPTION"
    val commitmentId: Long,
    val dueDate: Long,                    // Epoch millis
    val amount: Long,
    val paidDate: Long? = null,
    val transactionId: Long? = null,
    val status: PaymentRecordStatus = PaymentRecordStatus.PENDING,
    val payer: String = "SELF",           // "SELF", "DAD", "EXTERNAL"
    val notes: String = "",
    val meterId: Long? = null
)

/**
 * Unified commitment item for display in the Commitments Hub.
 */
sealed class CommitmentItem {
    abstract val id: Long
    abstract val title: String
    abstract val subtitle: String
    abstract val amount: Long
    abstract val nextDueDate: Long
    abstract val autoDebit: Boolean
    abstract val type: CommitmentType
    abstract val linkedAccountId: Long?

    data class LoanItem(
        val loan: Loan,
        val nextDueInstallment: EmiInstallment?,
        val progress: LoanProgress
    ) : CommitmentItem() {
        override val id: Long get() = loan.id
        override val title: String get() = loan.label
        override val subtitle: String get() = loan.lenderName.ifEmpty { loan.loanType.name }
        override val amount: Long get() = loan.monthlyEmiAmount
        override val nextDueDate: Long get() = nextDueInstallment?.dueDate ?: loan.startDate
        override val autoDebit: Boolean get() = loan.autoDebit
        override val type: CommitmentType get() = CommitmentType.LOAN
        override val linkedAccountId: Long? get() = loan.linkedAccountId
    }

    data class InsuranceItem(
        val policy: InsurancePolicy,
        val nextPayment: CommitmentPayment?,
        val pastPaymentsCount: Int,
        val lastPaidPayment: CommitmentPayment? = null
    ) : CommitmentItem() {
        override val id: Long get() = policy.id
        override val title: String get() = policy.policyName
        override val subtitle: String get() = "${policy.insurerName.ifEmpty { "Insurance" }} • ${policy.frequency.name}"
        override val amount: Long get() = policy.premiumAmount
        override val nextDueDate: Long get() = nextPayment?.dueDate ?: policy.startDate
        override val autoDebit: Boolean get() = policy.autoDebit
        override val type: CommitmentType get() = CommitmentType.INSURANCE
        override val linkedAccountId: Long? get() = policy.linkedAccountId

        val isCurrentCyclePaid: Boolean get() {
            if (lastPaidPayment == null) return false
            val now = java.time.LocalDate.now()
            val zone = java.time.ZoneId.systemDefault()
            val paidDate = java.time.Instant.ofEpochMilli(lastPaidPayment.paidDate ?: lastPaidPayment.dueDate).atZone(zone).toLocalDate()
            return when (policy.frequency) {
                PremiumFrequency.MONTHLY -> paidDate.year == now.year && paidDate.month == now.month
                PremiumFrequency.QUARTERLY -> {
                    val monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(paidDate.withDayOfMonth(1), now.withDayOfMonth(1))
                    monthsDiff < 3
                }
                PremiumFrequency.HALF_YEARLY -> {
                    val monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(paidDate.withDayOfMonth(1), now.withDayOfMonth(1))
                    monthsDiff < 6
                }
                PremiumFrequency.YEARLY -> paidDate.year == now.year
            }
        }
    }

    data class SubscriptionItem(
        val subscription: Subscription,
        val nextPayment: CommitmentPayment?,
        val pastPaymentsCount: Int,
        val meters: List<UtilityMeter> = emptyList(),
        val cycleMeterStatus: List<MeterCycleStatus> = emptyList(),
        val lastPaidPayment: CommitmentPayment? = null
    ) : CommitmentItem() {
        override val id: Long get() = subscription.id
        override val title: String get() = subscription.serviceName
        override val subtitle: String get() = if (subscription.isVariableAmount) {
            val countSuffix = if (meters.isNotEmpty()) " • ${meters.size} Connections" else ""
            "${subscription.billingCycle.name.replace('_', '-')} Utility$countSuffix"
        } else {
            "${subscription.billingCycle.name} Subscription"
        }
        override val amount: Long get() = if (subscription.isVariableAmount && meters.isNotEmpty()) {
            meters.sumOf { runCatching { it.calculateAmountForDate(nextDueDate) }.getOrDefault(it.estimatedAmount) }
        } else {
            subscription.amount
        }
        override val nextDueDate: Long get() = subscription.nextRenewalDate
        override val autoDebit: Boolean get() = subscription.autoDebit
        override val type: CommitmentType get() = if (subscription.isVariableAmount) CommitmentType.UTILITY else CommitmentType.SUBSCRIPTION
        override val linkedAccountId: Long? get() = subscription.linkedAccountId

        val isCurrentCyclePaid: Boolean get() {
            if (lastPaidPayment == null) return false
            val now = java.time.LocalDate.now()
            val zone = java.time.ZoneId.systemDefault()
            val paidDate = java.time.Instant.ofEpochMilli(lastPaidPayment.paidDate ?: lastPaidPayment.dueDate).atZone(zone).toLocalDate()
            return when (subscription.billingCycle) {
                BillingCycle.MONTHLY -> paidDate.year == now.year && paidDate.month == now.month
                BillingCycle.BI_MONTHLY -> {
                    val monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(paidDate.withDayOfMonth(1), now.withDayOfMonth(1))
                    monthsDiff < 2
                }
                BillingCycle.QUARTERLY -> {
                    val monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(paidDate.withDayOfMonth(1), now.withDayOfMonth(1))
                    monthsDiff < 3
                }
                BillingCycle.YEARLY -> paidDate.year == now.year
                BillingCycle.WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(paidDate, now) < 7
            }
        }
    }
}

data class PaidCommitmentInfo(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val paidDate: Long,
    val payer: String,
    val commitmentType: CommitmentType,
    val iconKey: String = ""
)

/**
 * Aggregated summary across all commitments for monthly budget insight.
 */
data class CommitmentsSummary(
    val totalMonthlyObligation: Long = 0L, // Total normalized/amortized monthly outflow in minor units
    val upcomingCountThisMonth: Int = 0,
    val upcomingTotalThisMonth: Long = 0L,
    val paidThisMonthCount: Int = 0,
    val paidThisMonthTotal: Long = 0L,
    val monthlyTotal: Long = 0L,           // Strictly monthly commitments (EMI, monthly policies, monthly subs)
    val quarterlyTotal: Long = 0L,         // Quarterly commitments (e.g. ₹953.00)
    val halfYearlyTotal: Long = 0L,        // Half-yearly commitments
    val yearlyTotal: Long = 0L,            // Yearly commitments (e.g. ₹1,436.73)
    val nextMonthNeedTotal: Long = 0L,     // Total projected outflow for next calendar month
    val nextMonthNeedCount: Int = 0,       // Count of commitments due next calendar month
    val activeLoansCount: Int = 0,
    val activePoliciesCount: Int = 0,
    val activeSubscriptionsCount: Int = 0
)

data class DueCommitmentInfo(
    val payment: CommitmentPayment,
    val title: String,
    val amount: Long,
    val linkedAccountId: Long?,
    val autoDebit: Boolean,
    val type: CommitmentType,
    val accountLabel: String? = null
)

data class CommitmentAttachment(
    val id: Long = 0,
    val commitmentType: String,
    val commitmentId: Long,
    val uri: String,
    val label: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class MobilePlanTracker(
    val operator: String = "Jio",
    val lastRechargeDate: Long,
    val lastRechargeAmount: Long,
    val validityDays: Int = 84,
    val expiryDate: Long,
    val daysRemaining: Int,
    val isActive: Boolean,
    val rechargedThisMonth: Boolean,
    val addonsCountThisMonth: Int = 0,
    val addonsTotalThisMonth: Long = 0L,
    val totalSpendThisMonth: Long = 0L
)

sealed class ContextChipData {
    data class MobilePlan(
        val operator: String,
        val validityDays: Int,
        val isBooster: Boolean
    ) : ContextChipData()

    data class LinkedCommitment(
        val id: Long,
        val type: String,
        val title: String,
        val amount: Long,
        val dueDateFormatted: String
    ) : ContextChipData()

    data class UtilityMeters(
        val serviceName: String,
        val meters: List<UtilityMeterChip>
    ) : ContextChipData()

    data class InsurancePolicies(
        val insurerName: String,
        val policies: List<InsurancePolicyChip>
    ) : ContextChipData()
}

data class UtilityMeterChip(
    val id: Long,
    val label: String,
    val meterNumber: String,
    val estimatedAmountPaise: Long
)

data class InsurancePolicyChip(
    val id: Long,
    val policyName: String,
    val policyNumber: String,
    val premiumAmount: Long
)


