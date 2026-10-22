package org.totschnig.myexpenses.db2

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.domain.commitment.BillingCycle
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentPayment
import org.totschnig.myexpenses.domain.commitment.CommitmentStatus
import org.totschnig.myexpenses.domain.commitment.CommitmentType
import org.totschnig.myexpenses.domain.commitment.CommitmentsSummary
import org.totschnig.myexpenses.domain.commitment.CommitmentAttachment
import org.totschnig.myexpenses.domain.commitment.DueCommitmentInfo
import org.totschnig.myexpenses.domain.commitment.InsurancePolicy
import org.totschnig.myexpenses.domain.commitment.InsuranceType
import org.totschnig.myexpenses.domain.commitment.PaymentRecordStatus
import org.totschnig.myexpenses.domain.commitment.PremiumFrequency
import org.totschnig.myexpenses.domain.commitment.Subscription
import org.totschnig.myexpenses.domain.commitment.UtilityMeter
import org.totschnig.myexpenses.domain.commitment.MeterCycleStatus
import org.totschnig.myexpenses.domain.commitment.PaidCommitmentInfo
import org.totschnig.myexpenses.domain.commitment.MobilePlanTracker
import org.totschnig.myexpenses.domain.commitment.ContextChipData
import org.totschnig.myexpenses.viewmodel.data.Tag
import org.totschnig.myexpenses.provider.TABLE_TAGS
import org.totschnig.myexpenses.provider.TABLE_TRANSACTIONS_TAGS
import org.totschnig.myexpenses.provider.KEY_COLOR
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.provider.TABLE_EMI_INSTALLMENTS
import org.totschnig.myexpenses.provider.TABLE_LOANS
import org.totschnig.myexpenses.provider.KEY_LOAN_ID
import org.totschnig.myexpenses.provider.KEY_TOTAL_AMOUNT
import org.totschnig.myexpenses.provider.KEY_INSTALLMENT_NUMBER
import org.totschnig.myexpenses.provider.KEY_LENDER_NAME
import org.totschnig.myexpenses.provider.KEY_TENURE_MONTHS
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_AUTO_DEBIT
import org.totschnig.myexpenses.provider.KEY_BILLING_CYCLE
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_COMMITMENT_ID
import org.totschnig.myexpenses.provider.KEY_COMMITMENT_TYPE
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_DUE_DATE
import org.totschnig.myexpenses.provider.KEY_DUE_DAY_OF_MONTH
import org.totschnig.myexpenses.provider.KEY_DUE_MONTH
import org.totschnig.myexpenses.provider.KEY_END_DATE
import org.totschnig.myexpenses.provider.KEY_FREQUENCY
import org.totschnig.myexpenses.provider.KEY_INSURANCE_TYPE
import org.totschnig.myexpenses.provider.KEY_INSURER_NAME
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_LINKED_ACCOUNT_ID
import org.totschnig.myexpenses.provider.KEY_NEXT_RENEWAL_DATE
import org.totschnig.myexpenses.provider.KEY_NOTES
import org.totschnig.myexpenses.provider.KEY_PAID_DATE
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_POLICY_NAME
import org.totschnig.myexpenses.provider.KEY_POLICY_NUMBER
import org.totschnig.myexpenses.provider.KEY_PREMIUM_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SERVICE_NAME
import org.totschnig.myexpenses.provider.KEY_START_DATE
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.KEY_SUM_ASSURED
import org.totschnig.myexpenses.provider.KEY_TEMPLATE_ID
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID
import org.totschnig.myexpenses.provider.KEY_TRANSACTION_ID
import org.totschnig.myexpenses.provider.KEY_SUBSCRIPTION_ID
import org.totschnig.myexpenses.provider.KEY_METER_ID
import org.totschnig.myexpenses.provider.KEY_METER_NUMBER
import org.totschnig.myexpenses.provider.KEY_METER_LABEL
import org.totschnig.myexpenses.provider.KEY_ESTIMATED_AMOUNT
import org.totschnig.myexpenses.provider.KEY_MATCHING_KEYWORDS
import org.totschnig.myexpenses.provider.KEY_CREATED_AT
import org.totschnig.myexpenses.provider.KEY_UNIT_PRICE
import org.totschnig.myexpenses.provider.KEY_UNITS_PER_DAY
import org.totschnig.myexpenses.provider.getDoubleOrNull
import org.totschnig.myexpenses.provider.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.TABLE_COMMITMENT_PAYMENTS
import org.totschnig.myexpenses.provider.TABLE_INSURANCE_POLICIES
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.TABLE_PLAN_INSTANCE_STATUS
import org.totschnig.myexpenses.provider.TABLE_SUBSCRIPTIONS
import org.totschnig.myexpenses.provider.TABLE_TRANSACTIONS
import org.totschnig.myexpenses.provider.TABLE_UTILITY_METERS
import org.totschnig.myexpenses.provider.TABLE_COMMITMENT_ATTACHMENTS
import org.totschnig.myexpenses.provider.KEY_URI
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getIntOrNull
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.provider.useAndMapToOne
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositoryCommitment @Inject constructor(
    private val openHelper: SupportSQLiteOpenHelper,
    private val repository: Repository,
    private val repositoryLoan: RepositoryLoan,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _commitmentsInvalidation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun invalidate() {
        _commitmentsInvalidation.tryEmit(Unit)
    }

    // ==========================================
    // INSURANCE POLICIES CRUD
    // ==========================================

    fun getAllInsurancePolicies(): Flow<List<InsurancePolicy>> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_INSURANCE_POLICIES ORDER BY $KEY_STATUS ASC, $KEY_START_DATE DESC",
                    emptyArray()
                ).useAndMapToList { it.toInsurancePolicy() }
            }
        }.flowOn(coroutineDispatcher)

    fun getInsurancePolicy(id: Long): Flow<InsurancePolicy?> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_INSURANCE_POLICIES WHERE $KEY_ROWID = ?",
                    arrayOf(id)
                ).useAndMapToOne { it.toInsurancePolicy() }
            }
        }.flowOn(coroutineDispatcher)

    suspend fun createInsurancePolicy(policy: InsurancePolicy, autoMatch: Boolean = true): Long =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put(KEY_POLICY_NAME, policy.policyName)
                    put(KEY_POLICY_NUMBER, policy.policyNumber)
                    put(KEY_INSURER_NAME, policy.insurerName)
                    put(KEY_INSURANCE_TYPE, policy.insuranceType.name)
                    put(KEY_PREMIUM_AMOUNT, policy.premiumAmount)
                    put(KEY_FREQUENCY, policy.frequency.name)
                    put(KEY_START_DATE, policy.startDate)
                    put(KEY_END_DATE, policy.endDate)
                    put(KEY_DUE_DAY_OF_MONTH, policy.dueDayOfMonth)
                    put(KEY_DUE_MONTH, policy.dueMonth)
                    put(KEY_SUM_ASSURED, policy.sumAssured)
                    put(KEY_LINKED_ACCOUNT_ID, policy.linkedAccountId)
                    put(KEY_TEMPLATE_ID, policy.templateId)
                    put(KEY_AUTO_DEBIT, if (policy.autoDebit) 1 else 0)
                    put(KEY_STATUS, policy.status.name)
                    put("payer_mode", policy.payerMode)
                    put(KEY_PAYEEID, policy.payeeId)
                }
                val policyId = db.insert(TABLE_INSURANCE_POLICIES, SQLiteDatabase.CONFLICT_NONE, values)

                if (autoMatch && policyId > 0) {
                    autoMatchInsuranceTransactions(db, policy.copy(id = policyId))
                }

                db.setTransactionSuccessful()
                invalidate()
                policyId
            } finally {
                db.endTransaction()
            }
        }

    suspend fun updateInsurancePolicy(policy: InsurancePolicy): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            val values = ContentValues().apply {
                put(KEY_POLICY_NAME, policy.policyName)
                put(KEY_POLICY_NUMBER, policy.policyNumber)
                put(KEY_INSURER_NAME, policy.insurerName)
                put(KEY_INSURANCE_TYPE, policy.insuranceType.name)
                put(KEY_PREMIUM_AMOUNT, policy.premiumAmount)
                put(KEY_FREQUENCY, policy.frequency.name)
                put(KEY_START_DATE, policy.startDate)
                put(KEY_END_DATE, policy.endDate)
                put(KEY_DUE_DAY_OF_MONTH, policy.dueDayOfMonth)
                put(KEY_DUE_MONTH, policy.dueMonth)
                put(KEY_SUM_ASSURED, policy.sumAssured)
                put(KEY_LINKED_ACCOUNT_ID, policy.linkedAccountId)
                put(KEY_TEMPLATE_ID, policy.templateId)
                put(KEY_AUTO_DEBIT, if (policy.autoDebit) 1 else 0)
                put(KEY_STATUS, policy.status.name)
                put("payer_mode", policy.payerMode)
                put(KEY_PAYEEID, policy.payeeId)
            }
            val rows = db.update(
                TABLE_INSURANCE_POLICIES,
                SQLiteDatabase.CONFLICT_NONE,
                values,
                "$KEY_ROWID = ?",
                arrayOf(policy.id)
            )
            if (rows > 0) {
                autoMatchInsuranceTransactions(db, policy)
            }
            invalidate()
            rows > 0
        }

    suspend fun deleteInsurancePolicy(id: Long): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_COMMITMENT_PAYMENTS, "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ?", arrayOf("INSURANCE", id.toString()))
                val rows = db.delete(TABLE_INSURANCE_POLICIES, "$KEY_ROWID = ?", arrayOf(id.toString()))
                db.setTransactionSuccessful()
                invalidate()
                rows > 0
            } finally {
                db.endTransaction()
            }
        }

    // ==========================================
    // SUBSCRIPTIONS CRUD
    // ==========================================

    fun getAllSubscriptions(): Flow<List<Subscription>> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_SUBSCRIPTIONS ORDER BY $KEY_STATUS ASC, $KEY_NEXT_RENEWAL_DATE ASC",
                    emptyArray()
                ).useAndMapToList { it.toSubscription() }
            }
        }.flowOn(coroutineDispatcher)

    fun getSubscription(id: Long): Flow<Subscription?> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_SUBSCRIPTIONS WHERE $KEY_ROWID = ?",
                    arrayOf(id)
                ).useAndMapToOne { it.toSubscription() }
            }
        }.flowOn(coroutineDispatcher)

    suspend fun createSubscription(subscription: Subscription, autoMatch: Boolean = true): Long =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put(KEY_SERVICE_NAME, subscription.serviceName)
                    put(KEY_BILLING_CYCLE, subscription.billingCycle.name)
                    put(KEY_AMOUNT, subscription.amount)
                    put(KEY_CATID, subscription.categoryId)
                    put(KEY_START_DATE, subscription.startDate)
                    put(KEY_NEXT_RENEWAL_DATE, subscription.nextRenewalDate)
                    put(KEY_LINKED_ACCOUNT_ID, subscription.linkedAccountId)
                    put(KEY_TEMPLATE_ID, subscription.templateId)
                    put(KEY_AUTO_DEBIT, if (subscription.autoDebit) 1 else 0)
                    put(KEY_STATUS, subscription.status.name)
                    put(KEY_NOTES, subscription.notes)
                    put(KEY_PAYEEID, subscription.payeeId)
                    put("is_variable_amount", if (subscription.isVariableAmount) 1 else 0)
                    put("cancel_before_renewal", if (subscription.cancelBeforeRenewal) 1 else 0)
                }
                val subId = db.insert(TABLE_SUBSCRIPTIONS, SQLiteDatabase.CONFLICT_NONE, values)

                if (autoMatch && subId > 0) {
                    autoMatchSubscriptionTransactions(db, subscription.copy(id = subId))
                }

                db.setTransactionSuccessful()
                invalidate()
                subId
            } finally {
                db.endTransaction()
            }
        }

    suspend fun updateSubscription(subscription: Subscription): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put(KEY_SERVICE_NAME, subscription.serviceName)
                    put(KEY_BILLING_CYCLE, subscription.billingCycle.name)
                    put(KEY_AMOUNT, subscription.amount)
                    put(KEY_CATID, subscription.categoryId)
                    put(KEY_START_DATE, subscription.startDate)
                    put(KEY_NEXT_RENEWAL_DATE, subscription.nextRenewalDate)
                    put(KEY_LINKED_ACCOUNT_ID, subscription.linkedAccountId)
                    put(KEY_TEMPLATE_ID, subscription.templateId)
                    put(KEY_AUTO_DEBIT, if (subscription.autoDebit) 1 else 0)
                    put(KEY_STATUS, subscription.status.name)
                    put(KEY_NOTES, subscription.notes)
                    put(KEY_PAYEEID, subscription.payeeId)
                    put("is_variable_amount", if (subscription.isVariableAmount) 1 else 0)
                    put("cancel_before_renewal", if (subscription.cancelBeforeRenewal) 1 else 0)
                }
                val rows = db.update(
                    TABLE_SUBSCRIPTIONS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ROWID = ?",
                    arrayOf(subscription.id)
                )

                if (subscription.status == CommitmentStatus.CANCELLED) {
                    // Remove pending renewal payments if cancelled
                    db.delete(
                        TABLE_COMMITMENT_PAYMENTS,
                        "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                        arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PENDING.name)
                    )
                } else {
                    // Sync pending payment amount and due date with subscription's renewal
                    val pendingValues = ContentValues().apply {
                        put(KEY_AMOUNT, subscription.amount)
                        put(KEY_DUE_DATE, subscription.nextRenewalDate)
                    }
                    db.update(
                        TABLE_COMMITMENT_PAYMENTS,
                        SQLiteDatabase.CONFLICT_NONE,
                        pendingValues,
                        "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                        arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PENDING.name)
                    )
                }

                if (rows > 0) {
                    autoMatchSubscriptionTransactions(db, subscription)
                }
                db.setTransactionSuccessful()
                invalidate()
                rows > 0
            } finally {
                db.endTransaction()
            }
        }

    suspend fun cancelSubscription(id: Long): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put(KEY_STATUS, CommitmentStatus.CANCELLED.name)
                }
                val rows = db.update(
                    TABLE_SUBSCRIPTIONS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ROWID = ?",
                    arrayOf(id.toString())
                )
                db.delete(
                    TABLE_COMMITMENT_PAYMENTS,
                    "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("SUBSCRIPTION", id.toString(), PaymentRecordStatus.PENDING.name)
                )
                db.setTransactionSuccessful()
                invalidate()
                rows > 0
            } finally {
                db.endTransaction()
            }
        }

    suspend fun deleteSubscription(id: Long): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_COMMITMENT_PAYMENTS, "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ?", arrayOf("SUBSCRIPTION", id.toString()))
                val rows = db.delete(TABLE_SUBSCRIPTIONS, "$KEY_ROWID = ?", arrayOf(id.toString()))
                db.setTransactionSuccessful()
                invalidate()
                rows > 0
            } finally {
                db.endTransaction()
            }
        }

    // ==========================================
    // UTILITY METERS CRUD & OPERATIONS
    // ==========================================

    fun getMetersForSubscription(subscriptionId: Long): Flow<List<UtilityMeter>> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                getMetersForSubscriptionSync(db, subscriptionId)
            }
        }.flowOn(coroutineDispatcher)

    fun getMetersForSubscriptionSync(db: SupportSQLiteDatabase, subscriptionId: Long): List<UtilityMeter> {
        return db.query(
            "SELECT * FROM $TABLE_UTILITY_METERS WHERE $KEY_SUBSCRIPTION_ID = ? ORDER BY $KEY_ROWID ASC",
            arrayOf(subscriptionId.toString())
        ).useAndMapToList { it.toUtilityMeter() }
    }

    suspend fun saveUtilityMeters(subscriptionId: Long, meters: List<UtilityMeter>) =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val existingIds = meters.mapNotNull { if (it.id > 0) it.id else null }
                if (existingIds.isNotEmpty()) {
                    db.delete(
                        TABLE_UTILITY_METERS,
                        "$KEY_SUBSCRIPTION_ID = ? AND $KEY_ROWID NOT IN (${existingIds.joinToString(",")})",
                        arrayOf(subscriptionId.toString())
                    )
                } else {
                    db.delete(TABLE_UTILITY_METERS, "$KEY_SUBSCRIPTION_ID = ?", arrayOf(subscriptionId.toString()))
                }

                for (meter in meters) {
                    val values = ContentValues().apply {
                        put(KEY_SUBSCRIPTION_ID, subscriptionId)
                        put(KEY_METER_NUMBER, meter.meterNumber.trim())
                        put(KEY_METER_LABEL, meter.meterLabel.trim())
                        put(KEY_ESTIMATED_AMOUNT, meter.estimatedAmount)
                        put(KEY_MATCHING_KEYWORDS, meter.matchingKeywords.trim())
                        put(KEY_CREATED_AT, if (meter.createdAt > 0) meter.createdAt else System.currentTimeMillis())
                        put(KEY_UNIT_PRICE, meter.unitPrice)
                        put(KEY_UNITS_PER_DAY, meter.unitsPerDay)
                    }
                    if (meter.id > 0) {
                        db.update(TABLE_UTILITY_METERS, SQLiteDatabase.CONFLICT_NONE, values, "$KEY_ROWID = ?", arrayOf(meter.id.toString()))
                    } else {
                        db.insert(TABLE_UTILITY_METERS, SQLiteDatabase.CONFLICT_NONE, values)
                    }
                }
                db.setTransactionSuccessful()
                invalidate()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun deleteUtilityMeter(meterId: Long): Boolean =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            val rows = db.delete(TABLE_UTILITY_METERS, "$KEY_ROWID = ?", arrayOf(meterId.toString()))
            if (rows > 0) invalidate()
            rows > 0
        }

    suspend fun payMeter(
        meter: UtilityMeter,
        subscription: Subscription,
        fromAccountId: Long?,
        amount: Long = meter.estimatedAmount,
        paidDate: Long = System.currentTimeMillis(),
        attachmentUri: Uri? = null
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val accId = fromAccountId ?: subscription.linkedAccountId
                var txId: Long? = null
                if (accId != null && accId > 0) {
                    val tx = Transaction(
                        accountId = accId,
                        amount = -kotlin.math.abs(amount),
                        date = paidDate / 1000L,
                        uuid = generateUuid(),
                        comment = "${meter.meterLabel} (Consumer #${meter.meterNumber})",
                        categoryId = subscription.categoryId ?: 57L,
                        payeeId = subscription.payeeId ?: 90L
                    )
                    val createdTx = repository.createTransaction(tx)
                    txId = createdTx.id

                    if (attachmentUri != null) {
                        try {
                            repository.addAttachments(createdTx.id, listOf(attachmentUri))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to attach document to meter transaction ${createdTx.id}")
                        }
                    }
                }

                val currentCycleDueDate = subscription.nextRenewalDate
                val notesText = buildString {
                    append("Meter: ${meter.meterLabel} • Consumer #${meter.meterNumber}")
                    if (attachmentUri != null) {
                        append("\nAttachment: $attachmentUri")
                    }
                }
                val cv = ContentValues().apply {
                    put(KEY_COMMITMENT_TYPE, "SUBSCRIPTION")
                    put(KEY_COMMITMENT_ID, subscription.id)
                    put(KEY_DUE_DATE, currentCycleDueDate)
                    put(KEY_AMOUNT, kotlin.math.abs(amount))
                    put(KEY_PAID_DATE, paidDate)
                    put(KEY_TRANSACTION_ID, txId)
                    put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                    put("payer", "SELF")
                    put("notes", notesText)
                    put(KEY_METER_ID, meter.id)
                }
                db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, cv)

                // Check if all active meters for this cycle are now paid
                val allMeters = getMetersForSubscriptionSync(db, subscription.id)
                val tolerance = if (subscription.billingCycle == BillingCycle.BI_MONTHLY) 45L * 86400000L else 25L * 86400000L
                val cyclePayments = db.query(
                    "SELECT $KEY_METER_ID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? AND ABS($KEY_DUE_DATE - ?) <= ?",
                    arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PAID.name, currentCycleDueDate.toString(), tolerance.toString())
                ).useAndMapToList { it.getLongOrNull(KEY_METER_ID) }.filterNotNull().toSet()

                val allPaid = allMeters.isNotEmpty() && allMeters.all { it.id in cyclePayments }
                if (allPaid) {
                    val intervalMonths = subscription.billingCycle.monthsInterval().coerceAtLeast(1)
                    val renewalLocalDate = Instant.ofEpochMilli(subscription.nextRenewalDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    val targetDueDay = renewalLocalDate.dayOfMonth
                    var nextCycleDate = renewalLocalDate.plusMonths(intervalMonths.toLong())
                    val today = LocalDate.now()
                    while (nextCycleDate.isBefore(today)) {
                        nextCycleDate = nextCycleDate.plusMonths(intervalMonths.toLong())
                    }
                    nextCycleDate = nextCycleDate.withDayOfMonth(targetDueDay.coerceAtMost(nextCycleDate.lengthOfMonth()))
                    val nextRenewalMillis = nextCycleDate.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val subCv = ContentValues().apply {
                        put(KEY_NEXT_RENEWAL_DATE, nextRenewalMillis)
                    }
                    db.update(TABLE_SUBSCRIPTIONS, SQLiteDatabase.CONFLICT_NONE, subCv, "$KEY_ROWID = ?", arrayOf(subscription.id.toString()))
                }

                db.setTransactionSuccessful()
                invalidate()
            } finally {
                db.endTransaction()
            }
        }
    }

    // ==========================================
    // PAYMENT HISTORY & DUES
    // ==========================================

    suspend fun getPaymentsForCommitment(type: String, commitmentId: Long): List<CommitmentPayment> =
        withContext(coroutineDispatcher) {
            val db = openHelper.readableDatabase
            db.query(
                "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? ORDER BY $KEY_DUE_DATE DESC",
                arrayOf(type, commitmentId.toString())
            ).useAndMapToList { it.toCommitmentPayment() }
        }

    fun getPaymentsForCommitmentFlow(type: String, commitmentId: Long): Flow<List<CommitmentPayment>> =
        _commitmentsInvalidation
            .onStart { emit(Unit) }
            .map { getPaymentsForCommitment(type, commitmentId) }
            .flowOn(coroutineDispatcher)

    suspend fun getDueCommitments(cutoffEpochMillis: Long): List<DueCommitmentInfo> =
        withContext(coroutineDispatcher) {
            val db = openHelper.readableDatabase
            val results = mutableListOf<DueCommitmentInfo>()

            val payments = db.query(
                "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_STATUS = ? AND $KEY_DUE_DATE <= ? ORDER BY $KEY_DUE_DATE ASC",
                arrayOf(PaymentRecordStatus.PENDING.name, cutoffEpochMillis.toString())
            ).useAndMapToList { it.toCommitmentPayment() }

            for (p in payments) {
                if (p.commitmentType == "INSURANCE") {
                    val policy = db.query(
                        "SELECT * FROM $TABLE_INSURANCE_POLICIES WHERE $KEY_ROWID = ?",
                        arrayOf(p.commitmentId)
                    ).useAndMapToOne { it.toInsurancePolicy() } ?: continue

                    val accountLabel = policy.linkedAccountId?.let { accId ->
                        db.query("SELECT $KEY_LABEL FROM $TABLE_ACCOUNTS WHERE $KEY_ROWID = ?", arrayOf(accId))
                            .useAndMapToOne { it.getString(KEY_LABEL) }
                    }

                    results.add(
                        DueCommitmentInfo(
                            payment = p,
                            title = policy.policyName,
                            amount = p.amount,
                            linkedAccountId = policy.linkedAccountId,
                            autoDebit = policy.autoDebit,
                            type = CommitmentType.INSURANCE,
                            accountLabel = accountLabel
                        )
                    )
                } else if (p.commitmentType == "SUBSCRIPTION") {
                    val sub = db.query(
                        "SELECT * FROM $TABLE_SUBSCRIPTIONS WHERE $KEY_ROWID = ?",
                        arrayOf(p.commitmentId)
                    ).useAndMapToOne { it.toSubscription() } ?: continue

                    val accountLabel = sub.linkedAccountId?.let { accId ->
                        db.query("SELECT $KEY_LABEL FROM $TABLE_ACCOUNTS WHERE $KEY_ROWID = ?", arrayOf(accId))
                            .useAndMapToOne { it.getString(KEY_LABEL) }
                    }

                    results.add(
                        DueCommitmentInfo(
                            payment = p,
                            title = sub.serviceName,
                            amount = p.amount,
                            linkedAccountId = sub.linkedAccountId,
                            autoDebit = sub.autoDebit,
                            type = CommitmentType.SUBSCRIPTION,
                            accountLabel = accountLabel
                        )
                    )
                }
            }
            results
        }

    /**
     * Records a commitment payment as paid, optionally creating a ledger transaction.
     * Automatically schedules the subsequent due installment.
     */
    suspend fun markCommitmentPaid(
        paymentId: Long,
        paidDate: Long = System.currentTimeMillis(),
        createTransaction: Boolean = false,
        payer: String = "SELF",
        notes: String = "",
        attachmentUri: Uri? = null
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val payment = db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_ROWID = ?",
                    arrayOf(paymentId)
                ).useAndMapToOne { it.toCommitmentPayment() }
                    ?: throw IllegalArgumentException("Payment $paymentId not found")

                var linkedAccId: Long? = null
                var title = ""
                var catId: Long? = null
                var nextIntervalMonths = 1
                var targetDueDay = 5
                var commitmentPayeeId: Long? = null

                if (payment.commitmentType == "INSURANCE") {
                    val policy = db.query(
                        "SELECT * FROM $TABLE_INSURANCE_POLICIES WHERE $KEY_ROWID = ?",
                        arrayOf(payment.commitmentId)
                    ).useAndMapToOne { it.toInsurancePolicy() }
                        ?: throw IllegalArgumentException("Insurance ${payment.commitmentId} not found")
                    linkedAccId = policy.linkedAccountId
                    title = "Premium - ${policy.policyName}"
                    catId = 178L // Life insurance or default
                    nextIntervalMonths = policy.frequency.monthsInterval()
                    targetDueDay = policy.dueDayOfMonth
                    commitmentPayeeId = policy.payeeId
                } else if (payment.commitmentType == "SUBSCRIPTION") {
                    val sub = db.query(
                        "SELECT * FROM $TABLE_SUBSCRIPTIONS WHERE $KEY_ROWID = ?",
                        arrayOf(payment.commitmentId)
                    ).useAndMapToOne { it.toSubscription() }
                        ?: throw IllegalArgumentException("Subscription ${payment.commitmentId} not found")
                    linkedAccId = sub.linkedAccountId
                    title = "Subscription - ${sub.serviceName}"
                    catId = sub.categoryId ?: 145L
                    nextIntervalMonths = sub.billingCycle.monthsInterval().coerceAtLeast(1)
                    targetDueDay = Instant.ofEpochMilli(sub.nextRenewalDate).atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth
                    commitmentPayeeId = sub.payeeId
                }

                var createdTransactionId: Long? = null
                if (createTransaction && payer == "SELF" && linkedAccId != null && linkedAccId > 0) {
                    val tx = Transaction(
                        accountId = linkedAccId,
                        amount = -payment.amount,
                        date = paidDate / 1000L,
                        uuid = generateUuid(),
                        comment = title,
                        categoryId = catId,
                        payeeId = commitmentPayeeId
                    )
                    val resultTx = repository.createTransaction(tx)
                    createdTransactionId = resultTx.id

                    if (attachmentUri != null) {
                        try {
                            repository.addAttachments(resultTx.id, listOf(attachmentUri))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to attach document to transaction ${resultTx.id}")
                        }
                    }
                }

                val finalNotes = buildString {
                    if (notes.isNotBlank()) append(notes)
                    if (attachmentUri != null) {
                        if (isNotEmpty()) append("\n")
                        append("Attachment: $attachmentUri")
                    }
                }

                val updateValues = ContentValues().apply {
                    put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                    put(KEY_PAID_DATE, paidDate)
                    put("payer", payer)
                    if (finalNotes.isNotBlank()) {
                        put("notes", finalNotes)
                    }
                    if (createdTransactionId != null) {
                        put(KEY_TRANSACTION_ID, createdTransactionId)
                    }
                }
                db.update(
                    TABLE_COMMITMENT_PAYMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    updateValues,
                    "$KEY_ROWID = ?",
                    arrayOf(paymentId)
                )

                // Schedule next due, strictly anchored to targetDueDay and the latest settled cycle
                val latestPaidDueDate = db.query(
                    "SELECT MAX($KEY_DUE_DATE) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf(payment.commitmentType, payment.commitmentId.toString(), PaymentRecordStatus.PAID.name)
                ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else payment.dueDate }

                val anchorDueMillis = maxOf(payment.dueDate, latestPaidDueDate)
                val currentDue = Instant.ofEpochMilli(anchorDueMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                var nextDueDate = currentDue.plusMonths(nextIntervalMonths.toLong())
                val today = LocalDate.now()
                // Advance to next upcoming cycle if still in the past
                while (nextDueDate.isBefore(today)) {
                    nextDueDate = nextDueDate.plusMonths(nextIntervalMonths.toLong())
                }
                nextDueDate = nextDueDate.withDayOfMonth(targetDueDay.coerceAtMost(nextDueDate.lengthOfMonth()))
                val nextDueMillis = nextDueDate.atTime(LocalTime.of(9, 0))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val nextValues = ContentValues().apply {
                    put(KEY_COMMITMENT_TYPE, payment.commitmentType)
                    put(KEY_COMMITMENT_ID, payment.commitmentId)
                    put(KEY_DUE_DATE, nextDueMillis)
                    put(KEY_AMOUNT, payment.amount)
                    put(KEY_STATUS, PaymentRecordStatus.PENDING.name)
                }

                val existingPendingId = db.query(
                    "SELECT $KEY_ROWID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf(payment.commitmentType, payment.commitmentId.toString(), PaymentRecordStatus.PENDING.name)
                ).use { if (it.moveToFirst()) it.getLong(0) else null }

                if (existingPendingId != null) {
                    db.update(
                        TABLE_COMMITMENT_PAYMENTS,
                        SQLiteDatabase.CONFLICT_NONE,
                        nextValues,
                        "$KEY_ROWID = ?",
                        arrayOf(existingPendingId.toString())
                    )
                } else {
                    db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, nextValues)
                }

                // If subscription, update next_renewal_date
                if (payment.commitmentType == "SUBSCRIPTION") {
                    val subValues = ContentValues().apply {
                        put(KEY_NEXT_RENEWAL_DATE, nextDueMillis)
                    }
                    db.update(
                        TABLE_SUBSCRIPTIONS,
                        SQLiteDatabase.CONFLICT_NONE,
                        subValues,
                        "$KEY_ROWID = ?",
                        arrayOf(payment.commitmentId)
                    )
                }

                db.setTransactionSuccessful()
                invalidate()
            } finally {
                db.endTransaction()
            }
        }
    }

    // ==========================================
    // AUTO-MATCHING & RE-SYNC
    // ==========================================

    suspend fun resyncCommitment(type: String, id: Long) = withContext(coroutineDispatcher) {
        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            if (type == "INSURANCE") {
                val policy = db.query(
                    "SELECT * FROM $TABLE_INSURANCE_POLICIES WHERE $KEY_ROWID = ?",
                    arrayOf(id)
                ).useAndMapToOne { it.toInsurancePolicy() } ?: return@withContext
                autoMatchInsuranceTransactions(db, policy)
            } else if (type == "SUBSCRIPTION") {
                val sub = db.query(
                    "SELECT * FROM $TABLE_SUBSCRIPTIONS WHERE $KEY_ROWID = ?",
                    arrayOf(id)
                ).useAndMapToOne { it.toSubscription() } ?: return@withContext
                autoMatchSubscriptionTransactions(db, sub)
            }
            db.setTransactionSuccessful()
            invalidate()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun resyncAllCommitments() = withContext(coroutineDispatcher) {
        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            val policies = db.query("SELECT * FROM $TABLE_INSURANCE_POLICIES", emptyArray()).useAndMapToList { it.toInsurancePolicy() }
            for (policy in policies) {
                autoMatchInsuranceTransactions(db, policy)
            }
            val subs = db.query("SELECT * FROM $TABLE_SUBSCRIPTIONS", emptyArray()).useAndMapToList { it.toSubscription() }
            for (sub in subs) {
                autoMatchSubscriptionTransactions(db, sub)
            }
            db.setTransactionSuccessful()
            invalidate()
        } finally {
            db.endTransaction()
        }
    }

    private fun autoMatchInsuranceTransactions(db: SupportSQLiteDatabase, policy: InsurancePolicy) {
        try {
            val hasLinkedPayee = policy.payeeId != null
            val payeeGroup = policy.payeeId?.let { resolvePayeeGroup(db, it) } ?: emptySet()

            val queryBuilder = StringBuilder(
                "SELECT t.$KEY_ROWID, t.$KEY_DATE, t.$KEY_AMOUNT, t.$KEY_COMMENT, t.$KEY_PAYEEID, p.$KEY_PAYEE_NAME AS payee_name " +
                        "FROM $TABLE_TRANSACTIONS t " +
                        "LEFT JOIN $TABLE_PAYEES p ON t.$KEY_PAYEEID = p.$KEY_ROWID " +
                        "WHERE t.$KEY_AMOUNT < 0 AND ("
            )

            if (hasLinkedPayee) {
                queryBuilder.append("t.$KEY_PAYEEID IN (${payeeGroup.joinToString(",")})")
            } else {
                val genericStopWords = setOf(
                    "insurance", "policy", "premium", "lic", "plan", "monthly", "yearly",
                    "quarterly", "half", "bill", "payment", "bank", "banking", "online", "direct", "debit"
                )
                val keywords = policy.policyName.split(" ", "-", ",")
                    .map { it.trim() }
                    .filter { it.length > 2 && !genericStopWords.contains(it.lowercase()) }

                val targetAmount = -kotlin.math.abs(policy.premiumAmount)
                queryBuilder.append("t.$KEY_AMOUNT = $targetAmount")
                queryBuilder.append(" OR t.$KEY_COMMENT LIKE '%${policy.policyName.replace("'", "''")}%'")
                queryBuilder.append(" OR p.$KEY_PAYEE_NAME LIKE '%${policy.policyName.replace("'", "''")}%'")
                for (kw in keywords) {
                    queryBuilder.append(" OR t.$KEY_COMMENT LIKE '%${kw.replace("'", "''")}%'")
                    queryBuilder.append(" OR p.$KEY_PAYEE_NAME LIKE '%${kw.replace("'", "''")}%'")
                }
                if (policy.templateId != null) {
                    queryBuilder.append(" OR t.$KEY_ROWID IN (SELECT $KEY_TRANSACTIONID FROM $TABLE_PLAN_INSTANCE_STATUS WHERE $KEY_TEMPLATEID = ${policy.templateId})")
                }
            }
            queryBuilder.append(") ORDER BY t.$KEY_DATE ASC")

            val matchedTxs = db.query(queryBuilder.toString(), emptyArray()).useAndMapToList { cursor ->
                val txId = cursor.getLong(KEY_ROWID)
                val dateSecs = cursor.getLong(KEY_DATE)
                val amt = -cursor.getLong(KEY_AMOUNT)
                val comment = cursor.getStringOrNull(KEY_COMMENT) ?: ""
                val payeeName = cursor.getStringOrNull("payee_name") ?: ""
                val payeeId = cursor.getLongOrNull(KEY_PAYEEID)
                MatchedCandidate(txId, dateSecs * 1000L, amt, comment, payeeName, payeeId)
            }

            // Filter transactions:
            val validMatches = matchedTxs.filter { candidate ->
                val matchesAmount = policy.premiumAmount > 0 && kotlin.math.abs(candidate.amount - policy.premiumAmount) <= 2000L
                if (hasLinkedPayee) {
                    candidate.payeeId in payeeGroup && matchesAmount
                } else {
                    val genericStopWords = setOf(
                        "insurance", "policy", "premium", "lic", "plan", "monthly", "yearly",
                        "quarterly", "half", "bill", "payment", "bank", "banking", "online", "direct", "debit"
                    )
                    val keywords = policy.policyName.split(" ", "-", ",")
                        .map { it.trim() }
                        .filter { it.length > 2 && !genericStopWords.contains(it.lowercase()) }

                    val matchesKeywords = keywords.isNotEmpty() && keywords.any { kw ->
                        candidate.comment.contains(kw, ignoreCase = true) || candidate.payeeName.contains(kw, ignoreCase = true)
                    }
                    val matchesName = candidate.comment.contains(policy.policyName, ignoreCase = true) ||
                            candidate.payeeName.contains(policy.policyName, ignoreCase = true) ||
                            candidate.comment.contains("LIC", ignoreCase = true) ||
                            candidate.payeeName.contains("LIC", ignoreCase = true)

                    matchesAmount && (matchesKeywords || matchesName)
                }
            }

            Timber.i("Auto-matched ${validMatches.size} past transactions for insurance ${policy.policyName}")

            val dueDay = policy.dueDayOfMonth.coerceIn(1, 31)
            for (tx in validMatches) {
                val exists = db.query(
                    "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_TRANSACTION_ID = ?",
                    arrayOf("INSURANCE", policy.id.toString(), tx.txId.toString())
                ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }

                val txLocalDate = Instant.ofEpochMilli(tx.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val estimatedDueLocalDate = when {
                    dueDay <= 10 && txLocalDate.dayOfMonth >= 25 -> {
                        txLocalDate.plusMonths(1).let { it.withDayOfMonth(dueDay.coerceAtMost(it.lengthOfMonth())) }
                    }
                    else -> {
                        txLocalDate.withDayOfMonth(dueDay.coerceAtMost(txLocalDate.lengthOfMonth()))
                    }
                }
                val scheduledDueMillis = estimatedDueLocalDate.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (!exists) {
                    val pendingRecord = db.query(
                        "SELECT $KEY_ROWID, $KEY_DUE_DATE FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                        arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PENDING.name)
                    ).useAndMapToOne { Pair(it.getLong(0), it.getLong(1)) }

                    if (pendingRecord != null && kotlin.math.abs(pendingRecord.second - scheduledDueMillis) <= 35L * 86400000L) {
                        val updateCv = ContentValues().apply {
                            put(KEY_PAID_DATE, tx.dateMillis)
                            put(KEY_TRANSACTION_ID, tx.txId)
                            put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                            put(KEY_AMOUNT, tx.amount)
                            put("payer", "SELF")
                        }
                        db.update(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, updateCv, "$KEY_ROWID = ?", arrayOf(pendingRecord.first.toString()))
                    } else {
                        val cv = ContentValues().apply {
                            put(KEY_COMMITMENT_TYPE, "INSURANCE")
                            put(KEY_COMMITMENT_ID, policy.id)
                            put(KEY_DUE_DATE, scheduledDueMillis)
                            put(KEY_AMOUNT, tx.amount)
                            put(KEY_PAID_DATE, tx.dateMillis)
                            put(KEY_TRANSACTION_ID, tx.txId)
                            put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                            put("payer", "SELF")
                        }
                        db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, cv)
                    }
                }
            }

            // Schedule next pending due date
            val intervalMonths = policy.frequency.monthsInterval()
            val today = LocalDate.now()

            val maxPaidDueMillis = db.query(
                "SELECT MAX($KEY_DUE_DATE) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PAID.name)
            ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

            val targetDue = if (maxPaidDueMillis != null) {
                val maxPaidLocalDate = Instant.ofEpochMilli(maxPaidDueMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val nextCycle = maxPaidLocalDate.plusMonths(intervalMonths.toLong())
                nextCycle.withDayOfMonth(dueDay.coerceAtMost(nextCycle.lengthOfMonth()))
            } else {
                var d = Instant.ofEpochMilli(policy.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                while (d.isBefore(today)) {
                    d = d.plusMonths(intervalMonths.toLong())
                }
                d.withDayOfMonth(dueDay.coerceAtMost(d.lengthOfMonth()))
            }

            val nextDueMillis = targetDue.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val pendingExists = db.query(
                "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PENDING.name)
            ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }

            val nextCv = ContentValues().apply {
                put(KEY_COMMITMENT_TYPE, "INSURANCE")
                put(KEY_COMMITMENT_ID, policy.id)
                put(KEY_DUE_DATE, nextDueMillis)
                put(KEY_AMOUNT, policy.premiumAmount)
                put(KEY_STATUS, PaymentRecordStatus.PENDING.name)
            }

            if (pendingExists) {
                db.update(
                    TABLE_COMMITMENT_PAYMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    nextCv,
                    "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PENDING.name)
                )
            } else {
                db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, nextCv)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error auto-matching insurance transactions for ${policy.policyName}")
        }
    }

    private fun autoMatchSubscriptionTransactions(db: SupportSQLiteDatabase, subscription: Subscription) {
        try {
            val hasLinkedPayee = subscription.payeeId != null
            val payeeGroup = subscription.payeeId?.let { resolvePayeeGroup(db, it) } ?: emptySet()

            val queryBuilder = StringBuilder(
                "SELECT t.$KEY_ROWID, t.$KEY_DATE, t.$KEY_AMOUNT, t.$KEY_COMMENT, t.$KEY_PAYEEID, t.$KEY_CATID, p.$KEY_PAYEE_NAME AS payee_name " +
                        "FROM $TABLE_TRANSACTIONS t " +
                        "LEFT JOIN $TABLE_PAYEES p ON t.$KEY_PAYEEID = p.$KEY_ROWID " +
                        "WHERE t.$KEY_AMOUNT < 0 AND ("
            )

            if (hasLinkedPayee) {
                queryBuilder.append("t.$KEY_PAYEEID IN (${payeeGroup.joinToString(",")})")
                if (subscription.categoryId != null) {
                    queryBuilder.append(" OR t.$KEY_CATID = ${subscription.categoryId}")
                }
            } else {
                val cleanServiceName = subscription.serviceName.trim()
                val genericStopWords = setOf(
                    "internet", "broadband", "service", "services", "monthly", "yearly",
                    "quarterly", "subscription", "premium", "insurance", "policy", "plan",
                    "bill", "payment", "online", "banking", "bank", "card", "direct", "debit",
                    "pay", "fee", "charges", "ltd", "pvt", "limited"
                )
                val keywords = cleanServiceName.split(" ", "-", ",")
                    .map { it.trim() }
                    .filter { it.length > 2 && !genericStopWords.contains(it.lowercase()) }

                val targetAmount = -kotlin.math.abs(subscription.amount)
                if (!subscription.isVariableAmount) {
                    queryBuilder.append("t.$KEY_AMOUNT = $targetAmount")
                    queryBuilder.append(" OR t.$KEY_COMMENT LIKE '%${cleanServiceName.replace("'", "''")}%'")
                    queryBuilder.append(" OR p.$KEY_PAYEE_NAME LIKE '%${cleanServiceName.replace("'", "''")}%'")
                } else {
                    queryBuilder.append("t.$KEY_COMMENT LIKE '%${cleanServiceName.replace("'", "''")}%'")
                    queryBuilder.append(" OR p.$KEY_PAYEE_NAME LIKE '%${cleanServiceName.replace("'", "''")}%'")
                }

                for (kw in keywords) {
                    queryBuilder.append(" OR t.$KEY_COMMENT LIKE '%${kw.replace("'", "''")}%'")
                    queryBuilder.append(" OR p.$KEY_PAYEE_NAME LIKE '%${kw.replace("'", "''")}%'")
                }
                if (subscription.categoryId != null) {
                    if (subscription.isVariableAmount) {
                        queryBuilder.append(" OR t.$KEY_CATID = ${subscription.categoryId}")
                    } else {
                        queryBuilder.append(" OR (t.$KEY_CATID = ${subscription.categoryId} AND t.$KEY_AMOUNT = $targetAmount)")
                    }
                }
                if (subscription.templateId != null) {
                    queryBuilder.append(" OR t.$KEY_ROWID IN (SELECT $KEY_TRANSACTIONID FROM $TABLE_PLAN_INSTANCE_STATUS WHERE $KEY_TEMPLATEID = ${subscription.templateId})")
                }
            }
            queryBuilder.append(") ORDER BY t.$KEY_DATE ASC")

            val matchedTxs = db.query(queryBuilder.toString(), emptyArray()).useAndMapToList { cursor ->
                val txId = cursor.getLong(KEY_ROWID)
                val dateSecs = cursor.getLong(KEY_DATE)
                val amt = -cursor.getLong(KEY_AMOUNT)
                val comment = cursor.getStringOrNull(KEY_COMMENT) ?: ""
                val payeeName = cursor.getStringOrNull("payee_name") ?: ""
                val payeeId = cursor.getLongOrNull(KEY_PAYEEID)
                val catId = cursor.getLongOrNull(KEY_CATID)
                MatchedCandidate(txId, dateSecs * 1000L, amt, comment, payeeName, payeeId, catId)
            }

            val validMatches = matchedTxs.filter { candidate ->
                if (subscription.isVariableAmount) {
                    if (hasLinkedPayee) {
                        candidate.payeeId in payeeGroup || (subscription.categoryId != null && candidate.categoryId == subscription.categoryId)
                    } else if (subscription.categoryId != null) {
                        candidate.categoryId == subscription.categoryId
                    } else {
                        val cleanServiceName = subscription.serviceName.trim()
                        val keywords = cleanServiceName.split(" ", "-", ",")
                            .map { it.trim() }
                            .filter { it.length > 2 }
                        candidate.payeeName.contains(cleanServiceName, ignoreCase = true) ||
                                candidate.comment.contains(cleanServiceName, ignoreCase = true) ||
                                (keywords.isNotEmpty() && keywords.any { kw ->
                                    candidate.payeeName.contains(kw, ignoreCase = true) || candidate.comment.contains(kw, ignoreCase = true)
                                })
                    }
                } else {
                    val matchesAmount = subscription.amount > 0 && kotlin.math.abs(candidate.amount - subscription.amount) <= 2000L
                    if (hasLinkedPayee) {
                        candidate.payeeId in payeeGroup && matchesAmount
                    } else {
                        val cleanServiceName = subscription.serviceName.trim()
                        val genericStopWords = setOf(
                            "internet", "broadband", "service", "services", "monthly", "yearly",
                            "quarterly", "subscription", "premium", "insurance", "policy", "plan",
                            "bill", "payment", "online", "banking", "bank", "card", "direct", "debit",
                            "pay", "fee", "charges", "ltd", "pvt", "limited"
                        )
                        val keywords = cleanServiceName.split(" ", "-", ",")
                            .map { it.trim() }
                            .filter { it.length > 2 && !genericStopWords.contains(it.lowercase()) }

                        val matchesPayee = candidate.payeeName.contains(cleanServiceName, ignoreCase = true) ||
                                (keywords.isNotEmpty() && keywords.any { kw -> candidate.payeeName.contains(kw, ignoreCase = true) })
                        val matchesComment = candidate.comment.contains(cleanServiceName, ignoreCase = true) ||
                                (keywords.isNotEmpty() && keywords.any { kw -> candidate.comment.contains(kw, ignoreCase = true) })

                        matchesAmount && (matchesPayee || matchesComment)
                    }
                }
            }

            // The renewal day is the day the user picked (stored in nextRenewalDate), not the creation date
            val renewalDay = Instant.ofEpochMilli(subscription.nextRenewalDate)
                .atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth

            val utilityMeters = if (subscription.isVariableAmount) {
                getMetersForSubscriptionSync(db, subscription.id)
            } else emptyList()

            for (tx in validMatches) {
                val exists = db.query(
                    "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_TRANSACTION_ID = ?",
                    arrayOf("SUBSCRIPTION", subscription.id.toString(), tx.txId.toString())
                ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }

                val txLocalDate = Instant.ofEpochMilli(tx.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val estimatedDueLocalDate = when {
                    renewalDay <= 10 && txLocalDate.dayOfMonth >= 25 -> {
                        txLocalDate.plusMonths(1).let { it.withDayOfMonth(renewalDay.coerceAtMost(it.lengthOfMonth())) }
                    }
                    else -> {
                        txLocalDate.withDayOfMonth(renewalDay.coerceAtMost(txLocalDate.lengthOfMonth()))
                    }
                }
                val scheduledDueMillis = estimatedDueLocalDate.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (exists) {
                    // Realign previously matched cycle to the current renewal day
                    db.update(
                        TABLE_COMMITMENT_PAYMENTS,
                        SQLiteDatabase.CONFLICT_NONE,
                        ContentValues().apply { put(KEY_DUE_DATE, scheduledDueMillis) },
                        "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_TRANSACTION_ID = ?",
                        arrayOf("SUBSCRIPTION", subscription.id.toString(), tx.txId.toString())
                    )
                } else {
                    val matchedMeter = if (subscription.isVariableAmount && utilityMeters.isNotEmpty()) {
                        utilityMeters.firstOrNull { m ->
                            (m.meterNumber.isNotBlank() && tx.comment.contains(m.meterNumber, ignoreCase = true)) ||
                            (m.meterLabel.isNotBlank() && tx.comment.contains(m.meterLabel, ignoreCase = true)) ||
                            (m.matchingKeywords.isNotBlank() && m.matchingKeywords.split(",").any { kw -> kw.trim().isNotEmpty() && tx.comment.contains(kw.trim(), ignoreCase = true) }) ||
                            (m.estimatedAmount > 0L && kotlin.math.abs(tx.amount - m.estimatedAmount) <= 10000L)
                        }
                    } else null

                    val pendingRecord = db.query(
                        "SELECT $KEY_ROWID, $KEY_DUE_DATE FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                        arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PENDING.name)
                    ).useAndMapToOne { Pair(it.getLong(0), it.getLong(1)) }

                    val toleranceDays = if (subscription.billingCycle == BillingCycle.BI_MONTHLY) 50L else 35L
                    if (pendingRecord != null && kotlin.math.abs(pendingRecord.second - scheduledDueMillis) <= toleranceDays * 86400000L) {
                        val updateCv = ContentValues().apply {
                            put(KEY_PAID_DATE, tx.dateMillis)
                            put(KEY_TRANSACTION_ID, tx.txId)
                            put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                            put(KEY_AMOUNT, tx.amount)
                            put("payer", "SELF")
                            if (matchedMeter != null) {
                                put(KEY_METER_ID, matchedMeter.id)
                                put("notes", "Meter: ${matchedMeter.meterLabel} • Consumer #${matchedMeter.meterNumber}")
                            }
                        }
                        db.update(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, updateCv, "$KEY_ROWID = ?", arrayOf(pendingRecord.first.toString()))
                    } else {
                        val cv = ContentValues().apply {
                            put(KEY_COMMITMENT_TYPE, "SUBSCRIPTION")
                            put(KEY_COMMITMENT_ID, subscription.id)
                            put(KEY_DUE_DATE, scheduledDueMillis)
                            put(KEY_AMOUNT, tx.amount)
                            put(KEY_PAID_DATE, tx.dateMillis)
                            put(KEY_TRANSACTION_ID, tx.txId)
                            put(KEY_STATUS, PaymentRecordStatus.PAID.name)
                            put("payer", "SELF")
                            if (matchedMeter != null) {
                                put(KEY_METER_ID, matchedMeter.id)
                                put("notes", "Meter: ${matchedMeter.meterLabel} • Consumer #${matchedMeter.meterNumber}")
                            }
                        }
                        db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, cv)
                    }
                }
            }

            // Schedule next pending renewal date
            val intervalMonths = subscription.billingCycle.monthsInterval().coerceAtLeast(1)
            val today = LocalDate.now()

            val targetDue = if (subscription.isVariableAmount && utilityMeters.isNotEmpty()) {
                var cycleDate = Instant.ofEpochMilli(subscription.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                cycleDate = cycleDate.withDayOfMonth(renewalDay.coerceAtMost(cycleDate.lengthOfMonth()))
                val tolerance = if (subscription.billingCycle == BillingCycle.BI_MONTHLY) 45L * 86400000L else 25L * 86400000L

                var attempts = 0
                while (attempts < 120) {
                    attempts++
                    val cycleMillis = cycleDate.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val paidMetersInCycle = db.query(
                        "SELECT $KEY_METER_ID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? AND ABS($KEY_DUE_DATE - ?) <= ?",
                        arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PAID.name, cycleMillis.toString(), tolerance.toString())
                    ).useAndMapToList { it.getLongOrNull(KEY_METER_ID) }.filterNotNull().toSet()

                    val allMetersPaid = utilityMeters.all { it.id in paidMetersInCycle }
                    if (!allMetersPaid && !cycleDate.isBefore(today.minusMonths(intervalMonths.toLong()))) {
                        break
                    }
                    cycleDate = cycleDate.plusMonths(intervalMonths.toLong())
                    cycleDate = cycleDate.withDayOfMonth(renewalDay.coerceAtMost(cycleDate.lengthOfMonth()))
                }
                cycleDate
            } else {
                val maxPaidDueMillis = db.query(
                    "SELECT MAX($KEY_DUE_DATE) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PAID.name)
                ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

                if (maxPaidDueMillis != null) {
                    val maxPaidLocalDate = Instant.ofEpochMilli(maxPaidDueMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val nextCycle = maxPaidLocalDate.plusMonths(intervalMonths.toLong())
                    nextCycle.withDayOfMonth(renewalDay.coerceAtMost(nextCycle.lengthOfMonth()))
                } else {
                    var d = Instant.ofEpochMilli(subscription.startDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    while (d.isBefore(today)) {
                        d = d.plusMonths(intervalMonths.toLong())
                    }
                    d.withDayOfMonth(renewalDay.coerceAtMost(d.lengthOfMonth()))
                }
            }

            val nextDueMillis = targetDue.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // Update subscription next_renewal_date
            val subCv = ContentValues().apply {
                put(KEY_NEXT_RENEWAL_DATE, nextDueMillis)
            }
            db.update(
                TABLE_SUBSCRIPTIONS,
                SQLiteDatabase.CONFLICT_NONE,
                subCv,
                "$KEY_ROWID = ?",
                arrayOf(subscription.id.toString())
            )

            val pendingExists = db.query(
                "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PENDING.name)
            ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }

            val nextCv = ContentValues().apply {
                put(KEY_COMMITMENT_TYPE, "SUBSCRIPTION")
                put(KEY_COMMITMENT_ID, subscription.id)
                put(KEY_DUE_DATE, nextDueMillis)
                put(KEY_AMOUNT, subscription.amount)
                put(KEY_STATUS, PaymentRecordStatus.PENDING.name)
            }

            if (pendingExists) {
                db.update(
                    TABLE_COMMITMENT_PAYMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    nextCv,
                    "$KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("SUBSCRIPTION", subscription.id.toString(), PaymentRecordStatus.PENDING.name)
                )
            } else {
                db.insert(TABLE_COMMITMENT_PAYMENTS, SQLiteDatabase.CONFLICT_NONE, nextCv)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error auto-matching subscription transactions for ${subscription.serviceName}")
        }
    }

    // ==========================================
    // UNIFIED HUB & SUMMARY
    // ==========================================

    fun getAllCommitmentItems(): Flow<List<CommitmentItem>> = combine(
        repositoryLoan.getAllActiveLoansWithProgress(),
        getAllInsurancePolicies(),
        getAllSubscriptions()
    ) { loansWithProgress: List<Pair<org.totschnig.myexpenses.domain.loan.Loan, org.totschnig.myexpenses.domain.loan.LoanProgress>>, policies: List<InsurancePolicy>, subscriptions: List<Subscription> ->
        val items = mutableListOf<CommitmentItem>()

        for ((loan, progress) in loansWithProgress) {
            items.add(
                CommitmentItem.LoanItem(
                    loan = loan,
                    nextDueInstallment = progress.nextDueInstallment,
                    progress = progress
                )
            )
        }

        val db = openHelper.readableDatabase
        for (policy in policies) {
            try {
                val nextPayment = db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? ORDER BY $KEY_DUE_DATE ASC LIMIT 1",
                    arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PENDING.name)
                ).useAndMapToOne { it.toCommitmentPayment() }

                val pastCount = db.query(
                    "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PAID.name)
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                val lastPaidPayment = db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? ORDER BY COALESCE($KEY_PAID_DATE, $KEY_DUE_DATE) DESC LIMIT 1",
                    arrayOf("INSURANCE", policy.id.toString(), PaymentRecordStatus.PAID.name)
                ).useAndMapToOne { it.toCommitmentPayment() }

                items.add(
                    CommitmentItem.InsuranceItem(
                        policy = policy,
                        nextPayment = nextPayment,
                        pastPaymentsCount = pastCount,
                        lastPaidPayment = lastPaidPayment
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error mapping insurance policy item: ${policy.policyName}")
                items.add(
                    CommitmentItem.InsuranceItem(
                        policy = policy,
                        nextPayment = null,
                        pastPaymentsCount = 0
                    )
                )
            }
        }

        for (sub in subscriptions) {
            try {
                val nextPayment = db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? ORDER BY $KEY_DUE_DATE ASC LIMIT 1",
                    arrayOf("SUBSCRIPTION", sub.id.toString(), PaymentRecordStatus.PENDING.name)
                ).useAndMapToOne { it.toCommitmentPayment() }

                val pastCount = db.query(
                    "SELECT COUNT(*) FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ?",
                    arrayOf("SUBSCRIPTION", sub.id.toString(), PaymentRecordStatus.PAID.name)
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                val lastPaidPayment = db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? ORDER BY COALESCE($KEY_PAID_DATE, $KEY_DUE_DATE) DESC LIMIT 1",
                    arrayOf("SUBSCRIPTION", sub.id.toString(), PaymentRecordStatus.PAID.name)
                ).useAndMapToOne { it.toCommitmentPayment() }

                val meters = if (sub.isVariableAmount) {
                    runCatching { getMetersForSubscriptionSync(db, sub.id) }.getOrDefault(emptyList())
                } else emptyList()

                val cycleMeterStatus = if (sub.isVariableAmount && meters.isNotEmpty()) {
                    val targetCycleDue = nextPayment?.dueDate ?: sub.nextRenewalDate
                    val tolerance = if (sub.billingCycle == BillingCycle.BI_MONTHLY) 45L * 86400000L else 25L * 86400000L
                    val cyclePayments = runCatching {
                        db.query(
                            "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? AND $KEY_STATUS = ? AND ABS($KEY_DUE_DATE - ?) <= ?",
                            arrayOf("SUBSCRIPTION", sub.id.toString(), PaymentRecordStatus.PAID.name, targetCycleDue.toString(), tolerance.toString())
                        ).useAndMapToList { it.toCommitmentPayment() }
                    }.getOrDefault(emptyList())

                    meters.map { meter ->
                        val paymentForMeter = cyclePayments.firstOrNull { it.meterId == meter.id }
                        val cycleCalculatedAmount = runCatching { meter.calculateAmountForDate(targetCycleDue) }.getOrDefault(meter.estimatedAmount)
                        MeterCycleStatus(
                            meter = meter,
                            isPaid = paymentForMeter != null,
                            paidAmount = paymentForMeter?.amount,
                            paidDate = paymentForMeter?.paidDate,
                            transactionId = paymentForMeter?.transactionId,
                            cycleCalculatedAmount = cycleCalculatedAmount
                        )
                    }
                } else emptyList()

                items.add(
                    CommitmentItem.SubscriptionItem(
                        subscription = sub,
                        nextPayment = nextPayment,
                        pastPaymentsCount = pastCount,
                        meters = meters,
                        cycleMeterStatus = cycleMeterStatus,
                        lastPaidPayment = lastPaidPayment
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error mapping subscription item: ${sub.serviceName}")
                items.add(
                    CommitmentItem.SubscriptionItem(
                        subscription = sub,
                        nextPayment = null,
                        pastPaymentsCount = 0
                    )
                )
            }
        }

        items.sortedBy { it.nextDueDate }
    }.flowOn(coroutineDispatcher)

    fun getCommitmentsSummary(): Flow<CommitmentsSummary> = getAllCommitmentItems().map { items ->
        val now = LocalDate.now()
        val currentYearMonth = YearMonth.from(now)
        val nextYearMonth = currentYearMonth.plusMonths(1)
        val zoneId = ZoneId.systemDefault()

        var totalMonthlyObligation = 0L
        var upcomingCountThisMonth = 0
        var upcomingTotalThisMonth = 0L
        var paidThisMonthCount = 0
        var paidThisMonthTotal = 0L
        var monthlyTotal = 0L
        var quarterlyTotal = 0L
        var halfYearlyTotal = 0L
        var yearlyTotal = 0L
        var nextMonthNeedTotal = 0L
        var nextMonthNeedCount = 0
        var activeLoansCount = 0
        var activePoliciesCount = 0
        var activeSubscriptionsCount = 0

        val db = openHelper.readableDatabase

        val startOfMonthMillis = currentYearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonthMillis = currentYearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
        val startOfNextMonthMillis = nextYearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfNextMonthMillis = nextYearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

        db.query(
            "SELECT * FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_STATUS = ? AND ($KEY_PAID_DATE BETWEEN ? AND ? OR ($KEY_PAID_DATE IS NULL AND $KEY_DUE_DATE BETWEEN ? AND ?))",
            arrayOf(PaymentRecordStatus.PAID.name, startOfMonthMillis.toString(), endOfMonthMillis.toString(), startOfMonthMillis.toString(), endOfMonthMillis.toString())
        ).useAndMapToList { it.toCommitmentPayment() }.forEach { p ->
            paidThisMonthCount++
            paidThisMonthTotal += p.amount
        }

        try {
            db.query(
                "SELECT $KEY_TOTAL_AMOUNT FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_STATUS = ? AND ($KEY_PAID_DATE BETWEEN ? AND ? OR ($KEY_PAID_DATE IS NULL AND $KEY_DUE_DATE BETWEEN ? AND ?))",
                arrayOf(InstallmentStatus.PAID.name, startOfMonthMillis.toString(), endOfMonthMillis.toString(), startOfMonthMillis.toString(), endOfMonthMillis.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    paidThisMonthCount++
                    paidThisMonthTotal += cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating paid EMI installments for commitments summary")
        }

        try {
            val startSecs = startOfMonthMillis / 1000L
            val endSecs = endOfMonthMillis / 1000L
            val catQuery = """
                SELECT count(*), sum(abs(amount))
                FROM $TABLE_TRANSACTIONS
                WHERE ($KEY_CATID = 164 OR $KEY_CATID IN (205, 7)) AND amount < 0 AND date BETWEEN ? AND ?
                  AND _id NOT IN (SELECT $KEY_TRANSACTION_ID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_TRANSACTION_ID IS NOT NULL)
            """.trimIndent()
            db.query(catQuery, arrayOf(startSecs.toString(), endSecs.toString())).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    paidThisMonthCount += cursor.getInt(0)
                    paidThisMonthTotal += if (cursor.isNull(1)) 0L else cursor.getLong(1)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating category-marked commitments for summary")
        }

        for (item in items) {
            when (item) {
                is CommitmentItem.LoanItem -> {
                    activeLoansCount++
                    monthlyTotal += item.loan.monthlyEmiAmount
                    totalMonthlyObligation += item.loan.monthlyEmiAmount
                    item.nextDueInstallment?.let { inst ->
                        if (inst.dueDate <= endOfMonthMillis && inst.status == InstallmentStatus.PENDING) {
                            upcomingCountThisMonth++
                            upcomingTotalThisMonth += inst.totalAmount
                        }
                    }
                    if (item.loan.status == org.totschnig.myexpenses.domain.loan.LoanStatus.ACTIVE && item.progress.paidInstallmentsCount < item.progress.totalInstallmentsCount) {
                        nextMonthNeedCount++
                        nextMonthNeedTotal += item.loan.monthlyEmiAmount
                    }
                }
                is CommitmentItem.InsuranceItem -> {
                    activePoliciesCount++
                    when (item.policy.frequency) {
                        PremiumFrequency.MONTHLY -> {
                            monthlyTotal += item.policy.premiumAmount
                            totalMonthlyObligation += item.policy.premiumAmount
                        }
                        PremiumFrequency.QUARTERLY -> {
                            quarterlyTotal += item.policy.premiumAmount
                            totalMonthlyObligation += item.policy.premiumAmount / 3
                        }
                        PremiumFrequency.HALF_YEARLY -> {
                            halfYearlyTotal += item.policy.premiumAmount
                            totalMonthlyObligation += item.policy.premiumAmount / 6
                        }
                        PremiumFrequency.YEARLY -> {
                            yearlyTotal += item.policy.premiumAmount
                            totalMonthlyObligation += item.policy.premiumAmount / 12
                        }
                    }

                    item.nextPayment?.let { p ->
                        if (p.dueDate <= endOfMonthMillis && p.status == PaymentRecordStatus.PENDING) {
                            upcomingCountThisMonth++
                            upcomingTotalThisMonth += p.amount
                        }
                    }

                    val policyDueNextMonth = when (item.policy.frequency) {
                        PremiumFrequency.MONTHLY -> true
                        PremiumFrequency.QUARTERLY -> {
                            val baseMonth = item.policy.dueMonth ?: Instant.ofEpochMilli(item.policy.startDate).atZone(zoneId).monthValue
                            (nextYearMonth.monthValue - baseMonth + 12) % 3 == 0
                        }
                        PremiumFrequency.HALF_YEARLY -> {
                            val baseMonth = item.policy.dueMonth ?: Instant.ofEpochMilli(item.policy.startDate).atZone(zoneId).monthValue
                            (nextYearMonth.monthValue - baseMonth + 12) % 6 == 0
                        }
                        PremiumFrequency.YEARLY -> {
                            val baseMonth = item.policy.dueMonth ?: Instant.ofEpochMilli(item.policy.startDate).atZone(zoneId).monthValue
                            nextYearMonth.monthValue == baseMonth
                        }
                    }
                    if (policyDueNextMonth && (item.policy.endDate == null || item.policy.endDate >= startOfNextMonthMillis)) {
                        nextMonthNeedCount++
                        nextMonthNeedTotal += item.policy.premiumAmount
                    }
                }
                is CommitmentItem.SubscriptionItem -> {
                    activeSubscriptionsCount++
                    val effectiveAmount = if (item.subscription.isVariableAmount && item.meters.isNotEmpty()) {
                        item.meters.sumOf { it.calculateAmountForDate(item.nextDueDate) }
                    } else {
                        item.subscription.amount
                    }
                    when (item.subscription.billingCycle) {
                        BillingCycle.WEEKLY -> {
                            monthlyTotal += effectiveAmount * 4
                            totalMonthlyObligation += effectiveAmount * 4
                        }
                        BillingCycle.MONTHLY -> {
                            monthlyTotal += effectiveAmount
                            totalMonthlyObligation += effectiveAmount
                        }
                        BillingCycle.BI_MONTHLY -> {
                            totalMonthlyObligation += effectiveAmount / 2
                        }
                        BillingCycle.QUARTERLY -> {
                            quarterlyTotal += effectiveAmount
                            totalMonthlyObligation += effectiveAmount / 3
                        }
                        BillingCycle.YEARLY -> {
                            yearlyTotal += effectiveAmount
                            totalMonthlyObligation += effectiveAmount / 12
                        }
                    }

                    item.nextPayment?.let { p ->
                        if (p.dueDate <= endOfMonthMillis && p.status == PaymentRecordStatus.PENDING) {
                            upcomingCountThisMonth++
                            upcomingTotalThisMonth += p.amount
                        }
                    }

                    val subDueNextMonth = when (item.subscription.billingCycle) {
                        BillingCycle.WEEKLY -> true
                        BillingCycle.MONTHLY -> true
                        BillingCycle.BI_MONTHLY -> {
                            val baseMonth = Instant.ofEpochMilli(item.subscription.startDate).atZone(zoneId).monthValue
                            (nextYearMonth.monthValue - baseMonth + 12) % 2 == 0
                        }
                        BillingCycle.QUARTERLY -> {
                            val baseMonth = Instant.ofEpochMilli(item.subscription.startDate).atZone(zoneId).monthValue
                            (nextYearMonth.monthValue - baseMonth + 12) % 3 == 0
                        }
                        BillingCycle.YEARLY -> {
                            val baseMonth = Instant.ofEpochMilli(item.subscription.startDate).atZone(zoneId).monthValue
                            nextYearMonth.monthValue == baseMonth
                        }
                    }
                    val isDueNextMonth = subDueNextMonth || (item.nextPayment?.dueDate in startOfNextMonthMillis..endOfNextMonthMillis)
                    if (isDueNextMonth && item.subscription.status == CommitmentStatus.ACTIVE) {
                        val amountToAdd = if (item.subscription.billingCycle == BillingCycle.WEEKLY) effectiveAmount * 4 else effectiveAmount
                        nextMonthNeedCount++
                        nextMonthNeedTotal += amountToAdd
                    }
                }
            }
        }

        CommitmentsSummary(
            totalMonthlyObligation = totalMonthlyObligation,
            upcomingCountThisMonth = upcomingCountThisMonth,
            upcomingTotalThisMonth = upcomingTotalThisMonth,
            paidThisMonthCount = paidThisMonthCount,
            paidThisMonthTotal = paidThisMonthTotal,
            monthlyTotal = monthlyTotal,
            quarterlyTotal = quarterlyTotal,
            halfYearlyTotal = halfYearlyTotal,
            yearlyTotal = yearlyTotal,
            nextMonthNeedTotal = nextMonthNeedTotal,
            nextMonthNeedCount = nextMonthNeedCount,
            activeLoansCount = activeLoansCount,
            activePoliciesCount = activePoliciesCount,
            activeSubscriptionsCount = activeSubscriptionsCount
        )
    }.flowOn(coroutineDispatcher)

    /**
     * Resolves a linked payee to its full group: the payee itself, its parent (if it was marked
     * as duplicate of another payee) and all payees marked as duplicates of either.
     * This allows one commitment to cover a biller that appears under multiple payee names.
     */
    private fun resolvePayeeGroup(db: SupportSQLiteDatabase, payeeId: Long): Set<Long> {
        val rootId = db.query(
            "SELECT COALESCE(parent_id, _id) FROM $TABLE_PAYEES WHERE _id = ?",
            arrayOf(payeeId)
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else payeeId }
        val ids = db.query(
            "SELECT _id FROM $TABLE_PAYEES WHERE _id = ? OR parent_id = ?",
            arrayOf(rootId, rootId)
        ).useAndMapToList { it.getLong(0) }
        return (ids + payeeId).toSet()
    }

    private data class MatchedCandidate(
        val txId: Long,
        val dateMillis: Long,
        val amount: Long,
        val comment: String,
        val payeeName: String = "",
        val payeeId: Long? = null,
        val categoryId: Long? = null
    )

    private fun Cursor.toInsurancePolicy() = InsurancePolicy(
        id = getLong(KEY_ROWID),
        policyName = getString(KEY_POLICY_NAME),
        policyNumber = getStringOrNull(KEY_POLICY_NUMBER) ?: "",
        insurerName = getString(KEY_INSURER_NAME),
        insuranceType = runCatching { InsuranceType.valueOf(getString(KEY_INSURANCE_TYPE)) }.getOrDefault(InsuranceType.LIFE),
        premiumAmount = getLong(KEY_PREMIUM_AMOUNT),
        frequency = runCatching { PremiumFrequency.valueOf(getString(KEY_FREQUENCY)) }.getOrDefault(PremiumFrequency.MONTHLY),
        startDate = getLong(KEY_START_DATE),
        endDate = getLongOrNull(KEY_END_DATE),
        dueDayOfMonth = getInt(KEY_DUE_DAY_OF_MONTH),
        dueMonth = getIntOrNull(KEY_DUE_MONTH),
        sumAssured = getLongOrNull(KEY_SUM_ASSURED) ?: 0L,
        linkedAccountId = getLongOrNull(KEY_LINKED_ACCOUNT_ID),
        templateId = getLongOrNull(KEY_TEMPLATE_ID),
        autoDebit = getInt(KEY_AUTO_DEBIT) == 1,
        status = runCatching { CommitmentStatus.valueOf(getString(KEY_STATUS)) }.getOrDefault(CommitmentStatus.ACTIVE),
        payerMode = getStringOrNull("payer_mode") ?: "SELF",
        payeeId = getLongOrNull(KEY_PAYEEID)
    )

    private fun Cursor.toSubscription() = Subscription(
        id = getLong(KEY_ROWID),
        serviceName = getString(KEY_SERVICE_NAME),
        billingCycle = runCatching { BillingCycle.valueOf(getString(KEY_BILLING_CYCLE)) }.getOrDefault(BillingCycle.MONTHLY),
        amount = getLong(KEY_AMOUNT),
        categoryId = getLongOrNull(KEY_CATID),
        startDate = getLong(KEY_START_DATE),
        nextRenewalDate = getLong(KEY_NEXT_RENEWAL_DATE),
        linkedAccountId = getLongOrNull(KEY_LINKED_ACCOUNT_ID),
        templateId = getLongOrNull(KEY_TEMPLATE_ID),
        autoDebit = getInt(KEY_AUTO_DEBIT) == 1,
        status = runCatching { CommitmentStatus.valueOf(getString(KEY_STATUS)) }.getOrDefault(CommitmentStatus.ACTIVE),
        notes = getStringOrNull(KEY_NOTES) ?: "",
        payeeId = getLongOrNull(KEY_PAYEEID),
        isVariableAmount = getIntOrNull("is_variable_amount") == 1,
        cancelBeforeRenewal = getIntOrNull("cancel_before_renewal") == 1
    )

    private fun Cursor.toCommitmentPayment() = CommitmentPayment(
        id = getLong(KEY_ROWID),
        commitmentType = getString(KEY_COMMITMENT_TYPE),
        commitmentId = getLong(KEY_COMMITMENT_ID),
        dueDate = getLong(KEY_DUE_DATE),
        amount = getLong(KEY_AMOUNT),
        paidDate = getLongOrNull(KEY_PAID_DATE),
        transactionId = getLongOrNull(KEY_TRANSACTION_ID),
        status = runCatching { PaymentRecordStatus.valueOf(getString(KEY_STATUS)) }.getOrDefault(PaymentRecordStatus.PENDING),
        payer = getStringOrNull("payer") ?: "SELF",
        notes = getStringOrNull("notes") ?: "",
        meterId = getLongOrNull(KEY_METER_ID)
    )

    private fun Cursor.toUtilityMeter() = UtilityMeter(
        id = getLong(KEY_ROWID),
        subscriptionId = getLong(KEY_SUBSCRIPTION_ID),
        meterNumber = getString(KEY_METER_NUMBER),
        meterLabel = getString(KEY_METER_LABEL),
        estimatedAmount = getLong(KEY_ESTIMATED_AMOUNT),
        matchingKeywords = getStringOrNull(KEY_MATCHING_KEYWORDS) ?: "",
        createdAt = getLongOrNull(KEY_CREATED_AT) ?: 0L,
        unitPrice = getLongOrNull(KEY_UNIT_PRICE) ?: 0L,
        unitsPerDay = getDoubleOrNull(KEY_UNITS_PER_DAY) ?: 0.0
    )

    private fun Cursor.toCommitmentAttachment() = CommitmentAttachment(
        id = getLong(KEY_ROWID),
        commitmentType = getString(KEY_COMMITMENT_TYPE),
        commitmentId = getLong(KEY_COMMITMENT_ID),
        uri = getString(KEY_URI),
        label = getStringOrNull("label"),
        createdAt = getLong(KEY_CREATED_AT)
    )

    // ==========================================
    // COMMITMENT ATTACHMENTS
    // ==========================================

    fun getCommitmentAttachments(type: String, id: Long): Flow<List<CommitmentAttachment>> = _commitmentsInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_COMMITMENT_ATTACHMENTS WHERE $KEY_COMMITMENT_TYPE = ? AND $KEY_COMMITMENT_ID = ? ORDER BY $KEY_ROWID ASC",
                    arrayOf(type, id.toString())
                ).useAndMapToList { it.toCommitmentAttachment() }
            }
        }.flowOn(coroutineDispatcher)

    suspend fun saveCommitmentAttachments(type: String, id: Long, uris: List<Uri>, label: String? = null) =
        withContext(coroutineDispatcher) {
            if (uris.isEmpty()) return@withContext
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                for (uri in uris) {
                    val cv = ContentValues().apply {
                        put(KEY_COMMITMENT_TYPE, type)
                        put(KEY_COMMITMENT_ID, id)
                        put(KEY_URI, uri.toString())
                        put("label", label ?: uri.lastPathSegment ?: "Document")
                        put(KEY_CREATED_AT, System.currentTimeMillis())
                    }
                    db.insert(TABLE_COMMITMENT_ATTACHMENTS, SQLiteDatabase.CONFLICT_NONE, cv)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            _commitmentsInvalidation.emit(Unit)
        }

    suspend fun deleteCommitmentAttachment(id: Long) =
        withContext(coroutineDispatcher) {
            val db = openHelper.writableDatabase
            db.delete(TABLE_COMMITMENT_ATTACHMENTS, "$KEY_ROWID = ?", arrayOf(id.toString()))
            _commitmentsInvalidation.emit(Unit)
        }

    fun getPaidPaymentsThisMonth(): Flow<List<PaidCommitmentInfo>> = combine(
        repositoryLoan.getAllActiveLoansWithProgress(),
        _commitmentsInvalidation.onStart { emit(Unit) }
    ) { _, _ ->
        val db = openHelper.readableDatabase
        val now = LocalDate.now()
        val currentYearMonth = YearMonth.from(now)
        val zoneId = ZoneId.systemDefault()
        val startOfMonthMillis = currentYearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonthMillis = currentYearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

        val result = mutableListOf<PaidCommitmentInfo>()

        // 1. Commitment payments (Insurance, Subscriptions & Utilities)
        try {
            val query = """
                SELECT cp._id, cp.$KEY_COMMITMENT_TYPE, cp.$KEY_COMMITMENT_ID, cp.$KEY_DUE_DATE, cp.$KEY_AMOUNT, cp.$KEY_PAID_DATE, cp.payer, cp.$KEY_METER_ID,
                       ins.$KEY_POLICY_NAME, ins.$KEY_INSURER_NAME, ins.$KEY_INSURANCE_TYPE,
                       sub.$KEY_SERVICE_NAME, sub.is_variable_amount, sub.$KEY_BILLING_CYCLE,
                       m.$KEY_METER_LABEL, m.$KEY_METER_NUMBER
                FROM $TABLE_COMMITMENT_PAYMENTS cp
                LEFT JOIN $TABLE_INSURANCE_POLICIES ins ON (cp.$KEY_COMMITMENT_TYPE = 'INSURANCE' AND cp.$KEY_COMMITMENT_ID = ins._id)
                LEFT JOIN $TABLE_SUBSCRIPTIONS sub ON (cp.$KEY_COMMITMENT_TYPE = 'SUBSCRIPTION' AND cp.$KEY_COMMITMENT_ID = sub._id)
                LEFT JOIN $TABLE_UTILITY_METERS m ON cp.$KEY_METER_ID = m._id
                WHERE cp.$KEY_STATUS = ?
                  AND (cp.$KEY_PAID_DATE BETWEEN ? AND ? OR (cp.$KEY_PAID_DATE IS NULL AND cp.$KEY_DUE_DATE BETWEEN ? AND ?))
                ORDER BY COALESCE(cp.$KEY_PAID_DATE, cp.$KEY_DUE_DATE) DESC
            """.trimIndent()

            db.query(
                query,
                arrayOf(PaymentRecordStatus.PAID.name, startOfMonthMillis.toString(), endOfMonthMillis.toString(), startOfMonthMillis.toString(), endOfMonthMillis.toString())
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val typeIdx = cursor.getColumnIndexOrThrow(KEY_COMMITMENT_TYPE)
                val dueDateIdx = cursor.getColumnIndexOrThrow(KEY_DUE_DATE)
                val amountIdx = cursor.getColumnIndexOrThrow(KEY_AMOUNT)
                val paidDateIdx = cursor.getColumnIndexOrThrow(KEY_PAID_DATE)
                val payerIdx = cursor.getColumnIndexOrThrow("payer")
                val meterIdIdx = cursor.getColumnIndexOrThrow(KEY_METER_ID)
                val policyNameIdx = cursor.getColumnIndexOrThrow(KEY_POLICY_NAME)
                val insurerNameIdx = cursor.getColumnIndexOrThrow(KEY_INSURER_NAME)
                val insTypeIdx = cursor.getColumnIndexOrThrow(KEY_INSURANCE_TYPE)
                val serviceNameIdx = cursor.getColumnIndexOrThrow(KEY_SERVICE_NAME)
                val isVarIdx = cursor.getColumnIndexOrThrow("is_variable_amount")
                val billingCycleIdx = cursor.getColumnIndexOrThrow(KEY_BILLING_CYCLE)
                val meterLabelIdx = cursor.getColumnIndexOrThrow(KEY_METER_LABEL)
                val meterNumberIdx = cursor.getColumnIndexOrThrow(KEY_METER_NUMBER)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val commType = cursor.getString(typeIdx)
                    val amount = cursor.getLong(amountIdx)
                    val paidDate = if (!cursor.isNull(paidDateIdx)) cursor.getLong(paidDateIdx) else cursor.getLong(dueDateIdx)
                    val payer = if (!cursor.isNull(payerIdx)) cursor.getString(payerIdx) else "SELF"
                    val hasMeter = !cursor.isNull(meterIdIdx)

                    if (commType == "INSURANCE") {
                        val policyName = if (!cursor.isNull(policyNameIdx)) cursor.getString(policyNameIdx) else "Insurance Policy"
                        val insurerName = if (!cursor.isNull(insurerNameIdx)) cursor.getString(insurerNameIdx) else ""
                        val insType = if (!cursor.isNull(insTypeIdx)) cursor.getString(insTypeIdx) else ""
                        result.add(
                            PaidCommitmentInfo(
                                id = id,
                                title = policyName,
                                subtitle = insurerName.ifEmpty { "Insurance Premium" },
                                amount = amount,
                                paidDate = paidDate,
                                payer = payer,
                                commitmentType = CommitmentType.INSURANCE,
                                iconKey = insType
                            )
                        )
                    } else {
                        // SUBSCRIPTION or UTILITY
                        val serviceName = if (!cursor.isNull(serviceNameIdx)) cursor.getString(serviceNameIdx) else "Subscription"
                        val isVariable = cursor.getInt(isVarIdx) == 1
                        if (hasMeter) {
                            val meterLabel = if (!cursor.isNull(meterLabelIdx)) cursor.getString(meterLabelIdx) else ""
                            val meterNumber = if (!cursor.isNull(meterNumberIdx)) cursor.getString(meterNumberIdx) else ""
                            result.add(
                                PaidCommitmentInfo(
                                    id = id,
                                    title = if (meterLabel.isNotEmpty()) "$serviceName • $meterLabel" else serviceName,
                                    subtitle = if (meterNumber.isNotEmpty()) "Consumer #$meterNumber" else "Meter Utility",
                                    amount = amount,
                                    paidDate = paidDate,
                                    payer = payer,
                                    commitmentType = CommitmentType.UTILITY,
                                    iconKey = meterLabel.ifEmpty { serviceName }
                                )
                            )
                        } else {
                            val cycle = if (!cursor.isNull(billingCycleIdx)) cursor.getString(billingCycleIdx) else ""
                            val subtitle = if (isVariable) "Utility Bill" else "$cycle Subscription".lowercase().replaceFirstChar { it.uppercase() }
                            result.add(
                                PaidCommitmentInfo(
                                    id = id,
                                    title = serviceName,
                                    subtitle = subtitle,
                                    amount = amount,
                                    paidDate = paidDate,
                                    payer = payer,
                                    commitmentType = if (isVariable) CommitmentType.UTILITY else CommitmentType.SUBSCRIPTION,
                                    iconKey = serviceName
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading paid commitment payments this month")
        }

        // 2. Paid EMI Installments
        try {
            val emiQuery = """
                SELECT inst._id, inst.$KEY_LOAN_ID, inst.$KEY_INSTALLMENT_NUMBER, inst.$KEY_DUE_DATE, inst.$KEY_TOTAL_AMOUNT, inst.$KEY_PAID_DATE,
                       l.$KEY_LABEL, l.$KEY_LENDER_NAME, l.$KEY_TENURE_MONTHS
                FROM $TABLE_EMI_INSTALLMENTS inst
                JOIN $TABLE_LOANS l ON inst.$KEY_LOAN_ID = l._id
                WHERE inst.$KEY_STATUS = ?
                  AND (inst.$KEY_PAID_DATE BETWEEN ? AND ? OR (inst.$KEY_PAID_DATE IS NULL AND inst.$KEY_DUE_DATE BETWEEN ? AND ?))
                ORDER BY COALESCE(inst.$KEY_PAID_DATE, inst.$KEY_DUE_DATE) DESC
            """.trimIndent()

            db.query(
                emiQuery,
                arrayOf(InstallmentStatus.PAID.name, startOfMonthMillis.toString(), endOfMonthMillis.toString(), startOfMonthMillis.toString(), endOfMonthMillis.toString())
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val instNumIdx = cursor.getColumnIndexOrThrow(KEY_INSTALLMENT_NUMBER)
                val dueDateIdx = cursor.getColumnIndexOrThrow(KEY_DUE_DATE)
                val totalAmountIdx = cursor.getColumnIndexOrThrow(KEY_TOTAL_AMOUNT)
                val paidDateIdx = cursor.getColumnIndexOrThrow(KEY_PAID_DATE)
                val labelIdx = cursor.getColumnIndexOrThrow(KEY_LABEL)
                val lenderNameIdx = cursor.getColumnIndexOrThrow(KEY_LENDER_NAME)
                val tenureIdx = cursor.getColumnIndexOrThrow(KEY_TENURE_MONTHS)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val instNum = cursor.getInt(instNumIdx)
                    val amount = cursor.getLong(totalAmountIdx)
                    val paidDate = if (!cursor.isNull(paidDateIdx)) cursor.getLong(paidDateIdx) else cursor.getLong(dueDateIdx)
                    val label = cursor.getString(labelIdx)
                    val lender = if (!cursor.isNull(lenderNameIdx)) cursor.getString(lenderNameIdx) else ""
                    val tenure = cursor.getInt(tenureIdx)

                    val subtitle = if (lender.isNotEmpty()) {
                        "$lender • Installment #$instNum of $tenure"
                    } else {
                        "Installment #$instNum of $tenure"
                    }

                    result.add(
                        PaidCommitmentInfo(
                            id = id,
                            title = label,
                            subtitle = subtitle,
                            amount = amount,
                            paidDate = paidDate,
                            payer = "SELF",
                            commitmentType = CommitmentType.LOAN,
                            iconKey = "loan"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading paid EMI installments this month")
        }

        // 3. Category-Marked Obligations (e.g. Recharge and Groceries/Provisions)
        try {
            val startSecs = startOfMonthMillis / 1000L
            val endSecs = endOfMonthMillis / 1000L

            // Recharge category (164)
            val rechargeQuery = """
                SELECT tr._id, tr.date, tr.amount, p.name, tr.comment
                FROM $TABLE_TRANSACTIONS tr
                LEFT JOIN $TABLE_PAYEES p ON tr.$KEY_PAYEEID = p._id
                WHERE tr.$KEY_CATID = 164 AND tr.amount < 0 AND tr.date BETWEEN ? AND ?
                  AND tr._id NOT IN (SELECT $KEY_TRANSACTION_ID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_TRANSACTION_ID IS NOT NULL)
                ORDER BY tr.date DESC
            """.trimIndent()

            db.query(rechargeQuery, arrayOf(startSecs.toString(), endSecs.toString())).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                val amtIdx = cursor.getColumnIndexOrThrow("amount")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val commentIdx = cursor.getColumnIndexOrThrow("comment")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val date = cursor.getLong(dateIdx) * 1000L
                    val amt = kotlin.math.abs(cursor.getLong(amtIdx))
                    val payee = if (cursor.isNull(nameIdx)) "Mobile Recharge" else cursor.getString(nameIdx)
                    val comment = if (cursor.isNull(commentIdx)) "" else cursor.getString(commentIdx)

                    result.add(
                        PaidCommitmentInfo(
                            id = id,
                            title = "Mobile Plan • $payee",
                            subtitle = if (comment.isNotBlank()) comment else "Category: Recharge",
                            amount = amt,
                            paidDate = date,
                            payer = "SELF",
                            commitmentType = CommitmentType.SUBSCRIPTION,
                            iconKey = "mobile"
                        )
                    )
                }
            }

            // Grocery / Provisions category (205, 7)
            val groceryQuery = """
                SELECT tr._id, tr.date, tr.amount, p.name, tr.comment
                FROM $TABLE_TRANSACTIONS tr
                LEFT JOIN $TABLE_PAYEES p ON tr.$KEY_PAYEEID = p._id
                WHERE tr.$KEY_CATID IN (205, 7) AND tr.amount < 0 AND tr.date BETWEEN ? AND ?
                  AND tr._id NOT IN (SELECT $KEY_TRANSACTION_ID FROM $TABLE_COMMITMENT_PAYMENTS WHERE $KEY_TRANSACTION_ID IS NOT NULL)
                ORDER BY tr.date DESC
            """.trimIndent()

            db.query(groceryQuery, arrayOf(startSecs.toString(), endSecs.toString())).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                val amtIdx = cursor.getColumnIndexOrThrow("amount")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val commentIdx = cursor.getColumnIndexOrThrow("comment")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val date = cursor.getLong(dateIdx) * 1000L
                    val amt = kotlin.math.abs(cursor.getLong(amtIdx))
                    val payee = if (cursor.isNull(nameIdx)) "Provisions" else cursor.getString(nameIdx)
                    val comment = if (cursor.isNull(commentIdx)) "" else cursor.getString(commentIdx)

                    result.add(
                        PaidCommitmentInfo(
                            id = id,
                            title = "Provisions • $payee",
                            subtitle = if (comment.isNotBlank()) comment else "Category: Grocery",
                            amount = amt,
                            paidDate = date,
                            payer = "SELF",
                            commitmentType = CommitmentType.UTILITY,
                            iconKey = "grocery"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading category-marked commitments this month")
        }

        result.sortedByDescending { it.paidDate }
    }.flowOn(coroutineDispatcher)

    fun getMobilePlanTracker(startOfMonthMillis: Long, endOfMonthMillis: Long): Flow<MobilePlanTracker?> = flow<MobilePlanTracker?> {
        val db = openHelper.readableDatabase

        val latestRechargeQuery = """
            SELECT tr._id, tr.date, tr.amount, p.name, tr.comment
            FROM $TABLE_TRANSACTIONS tr
            LEFT JOIN $TABLE_PAYEES p ON tr.$KEY_PAYEEID = p._id
            WHERE tr.$KEY_CATID = 164 AND tr.amount <= -10000
            ORDER BY tr.date DESC
            LIMIT 1
        """.trimIndent()

        var tracker: MobilePlanTracker? = null

        try {
            db.query(latestRechargeQuery, emptyArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateSecs = cursor.getLong(1)
                    val rechargeDateMillis = dateSecs * 1000L
                    val amtPaise = kotlin.math.abs(cursor.getLong(2))
                    val payeeName = if (cursor.isNull(3)) "Jio Recharge" else cursor.getString(3)
                    val operator = if (payeeName.contains("Vi", ignoreCase = true)) "Vi" else "Jio"

                    val comment = if (cursor.isNull(4)) "" else cursor.getString(4)
                    val customDays = if (comment.contains("booster", ignoreCase = true) || comment.contains("add-on", ignoreCase = true)) {
                        0
                    } else {
                        Regex("""(?i)\b(\d+)\s*d(?:ays)?\b""").find(comment)?.groupValues?.get(1)?.toIntOrNull()
                    }

                    val validityDays = customDays ?: when {
                        amtPaise >= 300000L -> 365
                        amtPaise in 150000L..250000L -> 180
                        amtPaise in 70000L..99900L -> 84
                        amtPaise in 40000L..69900L -> 56
                        amtPaise in 15000L..39900L -> 28
                        else -> 28
                    }

                    val expiryDateMillis = rechargeDateMillis + (validityDays * 86400000L)
                    val nowMillis = System.currentTimeMillis()
                    val daysRemaining = ((expiryDateMillis - nowMillis) / 86400000L).toInt()
                    val isActive = daysRemaining >= 0
                    val rechargedThisMonth = rechargeDateMillis in startOfMonthMillis..endOfMonthMillis

                    val startSecs = startOfMonthMillis / 1000L
                    val endSecs = endOfMonthMillis / 1000L

                    var addonsCount = 0
                    var addonsTotal = 0L

                    val addonsQuery = """
                        SELECT count(*), sum(abs(tr.amount))
                        FROM $TABLE_TRANSACTIONS tr
                        LEFT JOIN $TABLE_PAYEES p ON tr.$KEY_PAYEEID = p._id
                        WHERE tr.date BETWEEN ? AND ? AND tr.amount < 0
                          AND (p.name LIKE '%Addon%' OR (tr.$KEY_CATID = 64 AND tr.amount >= -10000))
                    """.trimIndent()

                    db.query(addonsQuery, arrayOf(startSecs.toString(), endSecs.toString())).use { addCursor ->
                        if (addCursor.moveToFirst() && !addCursor.isNull(0)) {
                            addonsCount = addCursor.getInt(0)
                            addonsTotal = if (addCursor.isNull(1)) 0L else addCursor.getLong(1)
                        }
                    }

                    val totalSpendThisMonth = (if (rechargedThisMonth) amtPaise else 0L) + addonsTotal

                    tracker = MobilePlanTracker(
                        operator = operator,
                        lastRechargeDate = rechargeDateMillis,
                        lastRechargeAmount = amtPaise,
                        validityDays = validityDays,
                        expiryDate = expiryDateMillis,
                        daysRemaining = daysRemaining,
                        isActive = isActive,
                        rechargedThisMonth = rechargedThisMonth,
                        addonsCountThisMonth = addonsCount,
                        addonsTotalThisMonth = addonsTotal,
                        totalSpendThisMonth = totalSpendThisMonth
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating MobilePlanTracker")
        }

        emit(tracker)
    }.flowOn(coroutineDispatcher)

    fun getSuggestedTagsForContext(payeeId: Long?, catId: Long?): List<Tag> {
        val results = LinkedHashMap<Long, Tag>()
        val db = openHelper.readableDatabase

        try {
            // 1. By Payee (Highest Priority)
            if (payeeId != null && payeeId > 0) {
                db.query("""
                    SELECT tg._id, tg.label, tg.color, count(*) as freq, max(tr.date) as last_used
                    FROM $TABLE_TRANSACTIONS_TAGS tt
                    JOIN $TABLE_TRANSACTIONS tr ON tt.transaction_id = tr._id
                    JOIN $TABLE_TAGS tg ON tt.tag_id = tg._id
                    WHERE tr.$KEY_PAYEEID = ?
                    GROUP BY tt.tag_id
                    ORDER BY freq DESC, last_used DESC
                    LIMIT 4
                """.trimIndent(), arrayOf(payeeId.toString())).use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow("_id")
                    val labelIdx = cursor.getColumnIndexOrThrow(KEY_LABEL)
                    val colorIdx = cursor.getColumnIndexOrThrow(KEY_COLOR)
                    while (cursor.moveToNext() && results.size < 4) {
                        val id = cursor.getLong(idIdx)
                        val label = cursor.getString(labelIdx)
                        val color = if (cursor.isNull(colorIdx)) null else cursor.getInt(colorIdx)
                        results[id] = Tag(id, label, color)
                    }
                }
            }

            // 2. By Category (if slots remain)
            if (results.size < 4 && catId != null && catId > 0) {
                db.query("""
                    SELECT tg._id, tg.label, tg.color, count(*) as freq, max(tr.date) as last_used
                    FROM $TABLE_TRANSACTIONS_TAGS tt
                    JOIN $TABLE_TRANSACTIONS tr ON tt.transaction_id = tr._id
                    JOIN $TABLE_TAGS tg ON tt.tag_id = tg._id
                    WHERE tr.$KEY_CATID = ?
                    GROUP BY tt.tag_id
                    ORDER BY freq DESC, last_used DESC
                    LIMIT 4
                """.trimIndent(), arrayOf(catId.toString())).use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow("_id")
                    val labelIdx = cursor.getColumnIndexOrThrow(KEY_LABEL)
                    val colorIdx = cursor.getColumnIndexOrThrow(KEY_COLOR)
                    while (cursor.moveToNext() && results.size < 4) {
                        val id = cursor.getLong(idIdx)
                        if (!results.containsKey(id)) {
                            val label = cursor.getString(labelIdx)
                            val color = if (cursor.isNull(colorIdx)) null else cursor.getInt(colorIdx)
                            results[id] = Tag(id, label, color)
                        }
                    }
                }
            }

            // 3. Fallback to globally frequent recent tags
            if (results.size < 4) {
                db.query("""
                    SELECT tg._id, tg.label, tg.color, count(*) as freq, max(tr.date) as last_used
                    FROM $TABLE_TRANSACTIONS_TAGS tt
                    JOIN $TABLE_TRANSACTIONS tr ON tt.transaction_id = tr._id
                    JOIN $TABLE_TAGS tg ON tt.tag_id = tg._id
                    GROUP BY tt.tag_id
                    ORDER BY freq DESC, last_used DESC
                    LIMIT 4
                """.trimIndent(), emptyArray()).use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow("_id")
                    val labelIdx = cursor.getColumnIndexOrThrow(KEY_LABEL)
                    val colorIdx = cursor.getColumnIndexOrThrow(KEY_COLOR)
                    while (cursor.moveToNext() && results.size < 4) {
                        val id = cursor.getLong(idIdx)
                        if (!results.containsKey(id)) {
                            val label = cursor.getString(labelIdx)
                            val color = if (cursor.isNull(colorIdx)) null else cursor.getInt(colorIdx)
                            results[id] = Tag(id, label, color)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading suggested tags")
        }

        return results.values.toList()
    }

    suspend fun getContextChipInfo(
        payeeId: Long?,
        payeeName: String?,
        catId: Long?,
        amountPaise: Long
    ): ContextChipData? = withContext(coroutineDispatcher) {
        val name = payeeName?.trim() ?: ""
        val isTelecom = catId == 164L ||
                name.contains("Jio", ignoreCase = true) ||
                name.contains("Airtel", ignoreCase = true) ||
                name.contains("Vi", ignoreCase = true) ||
                name.contains("Vodafone", ignoreCase = true) ||
                name.contains("BSNL", ignoreCase = true)

        if (isTelecom) {
            val operator = when {
                name.contains("Airtel", ignoreCase = true) -> "Airtel"
                name.contains("Vi", ignoreCase = true) || name.contains("Vodafone", ignoreCase = true) -> "Vi"
                name.contains("BSNL", ignoreCase = true) -> "BSNL"
                else -> "Jio"
            }
            val isBooster = amountPaise in 1L..9900L
            val validityDays = when {
                isBooster -> 0
                amountPaise >= 300000L -> 365
                amountPaise in 150000L..250000L -> 180
                amountPaise in 70000L..99900L -> 84
                amountPaise in 40000L..69900L -> 56
                amountPaise in 15000L..39900L -> 28
                else -> 84
            }
            return@withContext ContextChipData.MobilePlan(operator, validityDays, isBooster)
        }

        val db = openHelper.readableDatabase
        try {
            // Check subscriptions
            val subQuery = if (payeeId != null) {
                "SELECT _id, title, amount, due_day FROM commitment_subscriptions WHERE status = 'ACTIVE' AND (payee_id = ? OR title LIKE ?) LIMIT 1"
            } else if (name.isNotBlank()) {
                "SELECT _id, title, amount, due_day FROM commitment_subscriptions WHERE status = 'ACTIVE' AND title LIKE ? LIMIT 1"
            } else null

            if (subQuery != null) {
                val args = if (payeeId != null) arrayOf(payeeId.toString(), "%$name%") else arrayOf("%$name%")
                db.query(subQuery, args).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val title = cursor.getString(1)
                        val amount = cursor.getLong(2)
                        val dueDay = cursor.getInt(3)
                        return@withContext ContextChipData.LinkedCommitment(
                            id = id,
                            type = "SUBSCRIPTION",
                            title = title,
                            amount = amount,
                            dueDateFormatted = "Day $dueDay"
                        )
                    }
                }
            }

            // Check insurance
            val insQuery = if (payeeId != null) {
                "SELECT _id, policy_name, premium_amount, due_date FROM commitment_insurance_policies WHERE status = 'ACTIVE' AND (payee_id = ? OR policy_name LIKE ?) LIMIT 1"
            } else if (name.isNotBlank()) {
                "SELECT _id, policy_name, premium_amount, due_date FROM commitment_insurance_policies WHERE status = 'ACTIVE' AND policy_name LIKE ? LIMIT 1"
            } else null

            if (insQuery != null) {
                val args = if (payeeId != null) arrayOf(payeeId.toString(), "%$name%") else arrayOf("%$name%")
                db.query(insQuery, args).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val title = cursor.getString(1)
                        val amount = cursor.getLong(2)
                        val dueDate = cursor.getLong(3)
                        val dateFormatted = java.time.Instant.ofEpochMilli(dueDate)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                        return@withContext ContextChipData.LinkedCommitment(
                            id = id,
                            type = "INSURANCE",
                            title = title,
                            amount = amount,
                            dueDateFormatted = dateFormatted
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking getContextChipInfo")
        }

        null
    }
}

