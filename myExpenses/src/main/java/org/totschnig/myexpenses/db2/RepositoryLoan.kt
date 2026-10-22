package org.totschnig.myexpenses.db2

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import timber.log.Timber
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.domain.loan.DueInstallmentInfo
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.provider.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.KEY_DUE_DATE
import org.totschnig.myexpenses.provider.KEY_DUE_DAY_OF_MONTH
import org.totschnig.myexpenses.provider.KEY_INSTALLMENT_NUMBER
import org.totschnig.myexpenses.provider.KEY_INTEREST_PART
import org.totschnig.myexpenses.provider.KEY_INTEREST_RATE
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_LENDER_NAME
import org.totschnig.myexpenses.provider.KEY_LINKED_ACCOUNT_ID
import org.totschnig.myexpenses.provider.KEY_LOAN_ID
import org.totschnig.myexpenses.provider.KEY_LOAN_TYPE
import org.totschnig.myexpenses.provider.KEY_MONTHLY_EMI_AMOUNT
import org.totschnig.myexpenses.provider.KEY_PAID_DATE
import org.totschnig.myexpenses.provider.KEY_PRINCIPAL_AMOUNT
import org.totschnig.myexpenses.provider.KEY_PRINCIPAL_PART
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_START_DATE
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.KEY_TENURE_MONTHS
import org.totschnig.myexpenses.provider.KEY_TOTAL_AMOUNT
import org.totschnig.myexpenses.provider.TABLE_CATEGORIES
import org.totschnig.myexpenses.provider.TABLE_EMI_INSTALLMENTS
import org.totschnig.myexpenses.provider.TABLE_LOANS
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.getDouble
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.provider.useAndMapToOne
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID
import org.totschnig.myexpenses.provider.TABLE_PLAN_INSTANCE_STATUS
import org.totschnig.myexpenses.provider.TABLE_TRANSACTIONS
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.util.enumValueOrDefault
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class PastTransactionInfo(
    val id: Long,
    val dateMillis: Long,
    val amount: Long,
    val comment: String?
)

@Singleton
class RepositoryLoan @Inject constructor(
    private val openHelper: SupportSQLiteOpenHelper,
    private val repository: Repository,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _loansInvalidation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun invalidate() {
        _loansInvalidation.tryEmit(Unit)
    }

    /**
     * Creates a new Loan and automatically generates and persists its month-by-month
     * amortization schedule of EMI installments.
     */
    suspend fun createLoanWithSchedule(
        loan: Loan,
        customEmi: Long? = null,
        emisAlreadyPaid: Int = 0,
        linkedTransactions: List<Pair<Long, Long>> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = withContext(coroutineDispatcher) {
        val calculatedEmi = customEmi ?: loan.actualEmiAmount ?: loan.monthlyEmiAmount.takeIf { it > 0L }
            ?: EmiCalculator.calculateEmi(loan.loanType, loan.principalAmount, loan.interestRatePercent, loan.tenureMonths)

        val loanToSave = loan.copy(monthlyEmiAmount = calculatedEmi)

        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            val loanValues = ContentValues().apply {
                put(KEY_LABEL, loanToSave.label)
                put(KEY_LENDER_NAME, loanToSave.lenderName)
                put(KEY_LOAN_TYPE, loanToSave.loanType.name)
                put(KEY_PRINCIPAL_AMOUNT, loanToSave.principalAmount)
                put(KEY_INTEREST_RATE, loanToSave.interestRatePercent)
                put(KEY_TENURE_MONTHS, loanToSave.tenureMonths)
                put(KEY_MONTHLY_EMI_AMOUNT, loanToSave.monthlyEmiAmount)
                put(KEY_START_DATE, loanToSave.startDate)
                put(KEY_DUE_DAY_OF_MONTH, loanToSave.dueDayOfMonth)
                put(KEY_LINKED_ACCOUNT_ID, loanToSave.linkedAccountId)
                put(KEY_STATUS, loanToSave.status.name)
                put("auto_debit", if (loanToSave.autoDebit) 1 else 0)
            }

            val loanId = db.insert(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, loanValues)
            require(loanId != -1L) { "Failed to insert loan" }

            val installments = EmiCalculator.generateInstallmentsForLoan(loanId, loanToSave, zoneId)

            for (inst in installments) {
                val isAlreadyPaid = inst.installmentNumber <= emisAlreadyPaid
                val effectiveStatus = if (isAlreadyPaid) InstallmentStatus.PAID else inst.status
                val linkedTx = if (isAlreadyPaid) linkedTransactions.getOrNull(inst.installmentNumber - 1) else null
                val effectivePaidDate = linkedTx?.second ?: if (isAlreadyPaid) inst.dueDate else inst.paidDate
                val effectiveTxId = linkedTx?.first ?: inst.transactionId

                val instValues = ContentValues().apply {
                    put(KEY_LOAN_ID, loanId)
                    put(KEY_INSTALLMENT_NUMBER, inst.installmentNumber)
                    put(KEY_DUE_DATE, inst.dueDate)
                    put(KEY_PRINCIPAL_PART, inst.principalPart)
                    put(KEY_INTEREST_PART, inst.interestPart)
                    put(KEY_TOTAL_AMOUNT, inst.totalAmount)
                    put(KEY_PAID_DATE, effectivePaidDate)
                    put("transaction_id", effectiveTxId)
                    put(KEY_STATUS, effectiveStatus.name)
                }
                db.insert(TABLE_EMI_INSTALLMENTS, SQLiteDatabase.CONFLICT_NONE, instValues)
            }

            db.setTransactionSuccessful()
            invalidate()
            loanId
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Observes all active loans.
     */
    fun getActiveLoans(): Flow<List<Loan>> = _loansInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_STATUS = ? ORDER BY $KEY_START_DATE DESC",
                    arrayOf(LoanStatus.ACTIVE.name)
                ).useAndMapToList { it.toLoan() }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Observes all loans (active and closed).
     */
    fun getAllLoans(): Flow<List<Loan>> = _loansInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_LOANS ORDER BY $KEY_STATUS ASC, $KEY_START_DATE DESC",
                    emptyArray()
                ).useAndMapToList { it.toLoan() }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Observes a specific loan by ID.
     */
    fun getLoan(loanId: Long): Flow<Loan?> = _loansInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_ROWID = ?",
                    arrayOf(loanId)
                ).useAndMapToOne { it.toLoan() }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Observes a loan along with its full list of EMI installments.
     */
    fun getLoanWithInstallments(loanId: Long): Flow<Pair<Loan, List<EmiInstallment>>?> = _loansInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                val loan = db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_ROWID = ?",
                    arrayOf(loanId)
                ).useAndMapToOne { it.toLoan() } ?: return@withContext null

                val installments = db.query(
                    "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? ORDER BY $KEY_INSTALLMENT_NUMBER ASC",
                    arrayOf(loanId)
                ).useAndMapToList { it.toEmiInstallment() }

                loan to installments
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Computes the aggregated progress for a loan.
     */
    fun getLoanProgress(loanId: Long): Flow<LoanProgress?> = getLoanWithInstallments(loanId).map { pair ->
        if (pair == null) return@map null
        val (loan, installments) = pair
        val paid = installments.filter { it.status == InstallmentStatus.PAID }
        val nextDue = installments.firstOrNull { it.status == InstallmentStatus.PENDING }

        LoanProgress(
            loan = loan,
            paidInstallmentsCount = paid.size,
            totalInstallmentsCount = installments.size,
            totalPrincipalPaid = paid.sumOf { it.principalPart },
            remainingPrincipal = (loan.principalAmount - paid.sumOf { it.principalPart }).coerceAtLeast(0L),
            totalInterestPaid = paid.sumOf { it.interestPart },
            nextDueInstallment = nextDue
        )
    }.flowOn(coroutineDispatcher)

    /**
     * Observes all active loans along with their progress and next due installment.
     */
    fun getAllActiveLoansWithProgress(): Flow<List<Pair<Loan, LoanProgress>>> = _loansInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                val activeLoans = db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_STATUS = ? ORDER BY $KEY_START_DATE DESC",
                    arrayOf(LoanStatus.ACTIVE.name)
                ).useAndMapToList { it.toLoan() }

                activeLoans.map { loan ->
                    val installments = db.query(
                        "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? ORDER BY $KEY_INSTALLMENT_NUMBER ASC",
                        arrayOf(loan.id)
                    ).useAndMapToList { it.toEmiInstallment() }
                    val paid = installments.filter { it.status == InstallmentStatus.PAID }
                    val nextDue = installments.firstOrNull { it.status == InstallmentStatus.PENDING }
                    val progress = LoanProgress(
                        loan = loan,
                        paidInstallmentsCount = paid.size,
                        totalInstallmentsCount = installments.size,
                        totalPrincipalPaid = paid.sumOf { it.principalPart },
                        remainingPrincipal = (loan.principalAmount - paid.sumOf { it.principalPart }).coerceAtLeast(0L),
                        totalInterestPaid = paid.sumOf { it.interestPart },
                        nextDueInstallment = nextDue
                    )
                    loan to progress
                }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Marks an installment as paid, optionally recording an actual expense transaction
     * against the loan's linked bank account.
     * Automatically seals/closes the loan once all installments are paid.
     */
    suspend fun markInstallmentPaid(
        installmentId: Long,
        paidDate: Long = System.currentTimeMillis(),
        createTransaction: Boolean = false,
        attachmentUri: Uri? = null,
        accountId: Long? = null
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val installment = db.query(
                    "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_ROWID = ?",
                    arrayOf(installmentId)
                ).useAndMapToOne { it.toEmiInstallment() }
                    ?: throw IllegalArgumentException("Installment $installmentId not found")

                val loan = db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_ROWID = ?",
                    arrayOf(installment.loanId)
                ).useAndMapToOne { it.toLoan() }
                    ?: throw IllegalArgumentException("Loan ${installment.loanId} not found")

                var createdTransactionId: Long? = null
                val targetAccountId = accountId ?: loan.linkedAccountId
                if (createTransaction && targetAccountId != null && targetAccountId > 0) {
                    val resolvedPayeeId = if (loan.lenderName.isNotBlank()) {
                        db.query(
                            "SELECT $KEY_ROWID FROM $TABLE_PAYEES WHERE $KEY_PAYEE_NAME = ? OR $KEY_PAYEE_NAME LIKE ? LIMIT 1",
                            arrayOf(loan.lenderName, "%${loan.lenderName}%")
                        ).use { if (it.moveToFirst()) it.getLong(0) else null }
                    } else {
                        db.query(
                            "SELECT $KEY_ROWID FROM $TABLE_PAYEES WHERE $KEY_PAYEE_NAME LIKE '%loan%' OR $KEY_PAYEE_NAME LIKE '%icici%' LIMIT 1",
                            emptyArray()
                        ).use { if (it.moveToFirst()) it.getLong(0) else null }
                    }

                    val resolvedCatId = db.query(
                        "SELECT $KEY_ROWID FROM $TABLE_CATEGORIES WHERE $KEY_LABEL LIKE '%loan%' OR $KEY_LABEL LIKE '%emi%' ORDER BY CASE WHEN $KEY_LABEL = 'Personal Loan' THEN 1 WHEN $KEY_LABEL = 'EMI' THEN 2 ELSE 3 END LIMIT 1",
                        emptyArray()
                    ).use { if (it.moveToFirst()) it.getLong(0) else null }

                    val tx = Transaction(
                        accountId = targetAccountId,
                        amount = -installment.totalAmount,
                        date = paidDate / 1000L,
                        uuid = generateUuid(),
                        comment = "EMI #${installment.installmentNumber} - ${loan.label}",
                        payeeId = resolvedPayeeId,
                        categoryId = resolvedCatId
                    )
                    val resultTx = repository.createTransaction(tx)
                    createdTransactionId = resultTx.id

                    if (attachmentUri != null) {
                        try {
                            repository.addAttachments(resultTx.id, listOf(attachmentUri))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to attach document to EMI transaction ${resultTx.id}")
                        }
                    }
                }

                val values = ContentValues().apply {
                    put(KEY_STATUS, InstallmentStatus.PAID.name)
                    put(KEY_PAID_DATE, paidDate)
                    if (createdTransactionId != null) {
                        put("transaction_id", createdTransactionId)
                    }
                }
                db.update(
                    TABLE_EMI_INSTALLMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ROWID = ?",
                    arrayOf(installmentId)
                )

                val unpaidCount = db.query(
                    "SELECT COUNT(*) FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? AND $KEY_STATUS != ?",
                    arrayOf(loan.id.toString(), InstallmentStatus.PAID.name)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

                if (unpaidCount == 0) {
                    val loanValues = ContentValues().apply {
                        put(KEY_STATUS, LoanStatus.CLOSED.name)
                    }
                    db.update(
                        TABLE_LOANS,
                        SQLiteDatabase.CONFLICT_NONE,
                        loanValues,
                        "$KEY_ROWID = ?",
                        arrayOf(loan.id)
                    )
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidate()
        }
    }

    /**
     * Manually closes an active loan.
     */
    suspend fun closeLoan(loanId: Long): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            val values = ContentValues().apply {
                put(KEY_STATUS, LoanStatus.CLOSED.name)
            }
            db.update(
                TABLE_LOANS,
                SQLiteDatabase.CONFLICT_NONE,
                values,
                "$KEY_ROWID = ?",
                arrayOf(loanId)
            )
            invalidate()
        }
    }

    /**
     * Deletes a loan and cascades to its installments.
     */
    suspend fun deleteLoan(loanId: Long): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.delete(TABLE_LOANS, "$KEY_ROWID = ?", arrayOf(loanId))
            invalidate()
        }
    }

    /**
     * Updates an existing loan's details. If [recalculateRemaining] is true,
     * unpaid/pending installments are regenerated according to the updated parameters,
     * while all already-paid installments and their linked transactions remain completely intact.
     */
    suspend fun updateLoan(
        loan: Loan,
        customEmi: Long? = null,
        recalculateRemaining: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val calculatedEmi = customEmi ?: loan.actualEmiAmount ?: loan.monthlyEmiAmount.takeIf { it > 0L }
                    ?: EmiCalculator.calculateEmi(loan.loanType, loan.principalAmount, loan.interestRatePercent, loan.tenureMonths)
                val loanToSave = loan.copy(monthlyEmiAmount = calculatedEmi)

                val loanValues = ContentValues().apply {
                    put(KEY_LABEL, loanToSave.label)
                    put(KEY_LENDER_NAME, loanToSave.lenderName)
                    put(KEY_LOAN_TYPE, loanToSave.loanType.name)
                    put(KEY_PRINCIPAL_AMOUNT, loanToSave.principalAmount)
                    put(KEY_INTEREST_RATE, loanToSave.interestRatePercent)
                    put(KEY_TENURE_MONTHS, loanToSave.tenureMonths)
                    put(KEY_MONTHLY_EMI_AMOUNT, loanToSave.monthlyEmiAmount)
                    put(KEY_START_DATE, loanToSave.startDate)
                    put(KEY_DUE_DAY_OF_MONTH, loanToSave.dueDayOfMonth)
                    put(KEY_LINKED_ACCOUNT_ID, loanToSave.linkedAccountId)
                    put(KEY_STATUS, loanToSave.status.name)
                    put("auto_debit", if (loanToSave.autoDebit) 1 else 0)
                }

                db.update(
                    TABLE_LOANS,
                    SQLiteDatabase.CONFLICT_NONE,
                    loanValues,
                    "$KEY_ROWID = ?",
                    arrayOf(loan.id)
                )

                if (recalculateRemaining) {
                    val existing = db.query(
                        "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? ORDER BY $KEY_INSTALLMENT_NUMBER ASC",
                        arrayOf(loan.id)
                    ).useAndMapToList { it.toEmiInstallment() }

                    val paidCount = existing.count { it.status == InstallmentStatus.PAID }
                    val freshSchedule = EmiCalculator.generateInstallmentsForLoan(loan.id, loanToSave, zoneId)

                    db.delete(
                        TABLE_EMI_INSTALLMENTS,
                        "$KEY_LOAN_ID = ? AND $KEY_STATUS != ?",
                        arrayOf(loan.id.toString(), InstallmentStatus.PAID.name)
                    )

                    for (inst in freshSchedule) {
                        if (inst.installmentNumber > paidCount) {
                            val instValues = ContentValues().apply {
                                put(KEY_LOAN_ID, loan.id)
                                put(KEY_INSTALLMENT_NUMBER, inst.installmentNumber)
                                put(KEY_DUE_DATE, inst.dueDate)
                                put(KEY_PRINCIPAL_PART, inst.principalPart)
                                put(KEY_INTEREST_PART, inst.interestPart)
                                put(KEY_TOTAL_AMOUNT, inst.totalAmount)
                                putNull(KEY_PAID_DATE)
                                putNull("transaction_id")
                                put(KEY_STATUS, InstallmentStatus.PENDING.name)
                            }
                            db.insert(TABLE_EMI_INSTALLMENTS, SQLiteDatabase.CONFLICT_NONE, instValues)
                        }
                    }
                } else {
                    val pendingInstallments = db.query(
                        "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? AND $KEY_STATUS = ?",
                        arrayOf(loan.id.toString(), InstallmentStatus.PENDING.name)
                    ).useAndMapToList { it.toEmiInstallment() }

                    for (inst in pendingInstallments) {
                        val currentDueLocalDate = Instant.ofEpochMilli(inst.dueDate).atZone(zoneId).toLocalDate()
                        val newDueDate = currentDueLocalDate
                            .withDayOfMonth(loanToSave.dueDayOfMonth.coerceAtMost(currentDueLocalDate.lengthOfMonth()))
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli()

                        val v = ContentValues().apply {
                            put(KEY_DUE_DATE, newDueDate)
                            put(KEY_TOTAL_AMOUNT, loanToSave.monthlyEmiAmount)
                        }
                        db.update(TABLE_EMI_INSTALLMENTS, SQLiteDatabase.CONFLICT_NONE, v, "$KEY_ROWID = ?", arrayOf(inst.id))
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidate()
        }
    }

    /**
     * Manually connects an existing transaction to an EMI installment and marks it PAID.
     */
    suspend fun linkTransactionToInstallment(
        installmentId: Long,
        transactionId: Long,
        paidDate: Long
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val installment = db.query(
                    "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_ROWID = ?",
                    arrayOf(installmentId)
                ).useAndMapToOne { it.toEmiInstallment() }
                    ?: throw IllegalArgumentException("Installment $installmentId not found")

                val values = ContentValues().apply {
                    put(KEY_STATUS, InstallmentStatus.PAID.name)
                    put(KEY_PAID_DATE, paidDate)
                    put("transaction_id", transactionId)
                }
                db.update(
                    TABLE_EMI_INSTALLMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ROWID = ?",
                    arrayOf(installmentId)
                )

                val unpaid = db.query(
                    "SELECT COUNT(*) FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? AND $KEY_STATUS != ?",
                    arrayOf(installment.loanId.toString(), InstallmentStatus.PAID.name)
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                if (unpaid == 0) {
                    val loanV = ContentValues().apply { put(KEY_STATUS, LoanStatus.CLOSED.name) }
                    db.update(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, loanV, "$KEY_ROWID = ?", arrayOf(installment.loanId))
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidate()
        }
    }

    /**
     * Unlinks a transaction from an installment, restoring it to PENDING.
     */
    suspend fun unlinkTransactionFromInstallment(installmentId: Long): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val installment = db.query(
                    "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_ROWID = ?",
                    arrayOf(installmentId)
                ).useAndMapToOne { it.toEmiInstallment() }
                    ?: throw IllegalArgumentException("Installment $installmentId not found")

                val values = ContentValues().apply {
                    put(KEY_STATUS, InstallmentStatus.PENDING.name)
                    putNull(KEY_PAID_DATE)
                    putNull("transaction_id")
                }
                db.update(
                    TABLE_EMI_INSTALLMENTS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ROWID = ?",
                    arrayOf(installmentId)
                )

                val loanV = ContentValues().apply { put(KEY_STATUS, LoanStatus.ACTIVE.name) }
                db.update(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, loanV, "$KEY_ROWID = ?", arrayOf(installment.loanId))

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidate()
        }
    }

    /**
     * Retrieves historical transactions generated by a recurring plan template,
     * Returns previous transactions recorded for this recurring plan by combining both
     * plan instance records and matching debits on the account for the given EMI amount.
     */
    suspend fun getHistoricalTransactionsForPlan(
        templateId: Long,
        accountId: Long,
        emiAmount: Long
    ): List<PastTransactionInfo> = withContext(coroutineDispatcher) {
        val db = openHelper.readableDatabase
        val txMap = LinkedHashMap<Long, PastTransactionInfo>()

        // 1. Transactions recorded by plan instances
        db.query(
            """
            SELECT t.$KEY_ROWID, t.$KEY_DATE, t.$KEY_AMOUNT, t.$KEY_COMMENT 
            FROM $TABLE_TRANSACTIONS t 
            JOIN $TABLE_PLAN_INSTANCE_STATUS pit ON t.$KEY_ROWID = pit.$KEY_TRANSACTIONID 
            WHERE pit.$KEY_TEMPLATEID = ? 
            ORDER BY t.$KEY_DATE ASC
            """.trimIndent(),
            arrayOf(templateId.toString())
        ).useAndMapToList { cursor ->
            PastTransactionInfo(
                id = cursor.getLong(0),
                dateMillis = cursor.getLong(1) * 1000L,
                amount = cursor.getLong(2),
                comment = cursor.getStringOrNull(KEY_COMMENT)
            )
        }.forEach { txMap[it.id] = it }

        // 2. Matching debits on the account for this exact EMI amount
        val targetAmount = -kotlin.math.abs(emiAmount)
        db.query(
            """
            SELECT $KEY_ROWID, $KEY_DATE, $KEY_AMOUNT, $KEY_COMMENT 
            FROM $TABLE_TRANSACTIONS 
            WHERE $KEY_ACCOUNTID = ? AND $KEY_AMOUNT = ? 
            ORDER BY $KEY_DATE ASC
            """.trimIndent(),
            arrayOf(accountId.toString(), targetAmount.toString())
        ).useAndMapToList { cursor ->
            PastTransactionInfo(
                id = cursor.getLong(0),
                dateMillis = cursor.getLong(1) * 1000L,
                amount = cursor.getLong(2),
                comment = cursor.getStringOrNull(KEY_COMMENT)
            )
        }.forEach { txMap[it.id] = it }

        txMap.values.sortedBy { it.dateMillis }
    }

    /**
     * Resynchronizes and matches existing account transactions to a loan's installments by month.
     * Realigns schedule if the earliest payment predates the current schedule.
     */
    suspend fun resyncAndMatchTransactions(
        loanId: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<Int> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.beginTransaction()
            try {
                val loan = db.query(
                    "SELECT * FROM $TABLE_LOANS WHERE $KEY_ROWID = ?",
                    arrayOf(loanId.toString())
                ).useAndMapToOne { it.toLoan() }
                    ?: throw IllegalArgumentException("Loan $loanId not found")

                val accountId = loan.linkedAccountId
                    ?: throw IllegalStateException("No bank account linked to loan")

                val targetAmount = -kotlin.math.abs(loan.monthlyEmiAmount)

                // Query all matching debits on this account
                val matchingTxs = db.query(
                    """
                    SELECT $KEY_ROWID, $KEY_DATE, $KEY_AMOUNT, $KEY_COMMENT 
                    FROM $TABLE_TRANSACTIONS 
                    WHERE $KEY_ACCOUNTID = ? AND $KEY_AMOUNT = ? 
                    ORDER BY $KEY_DATE ASC
                    """.trimIndent(),
                    arrayOf(accountId.toString(), targetAmount.toString())
                ).useAndMapToList { cursor ->
                    PastTransactionInfo(
                        id = cursor.getLong(0),
                        dateMillis = cursor.getLong(1) * 1000L,
                        amount = cursor.getLong(2),
                        comment = cursor.getStringOrNull(KEY_COMMENT)
                    )
                }

                if (matchingTxs.isEmpty()) {
                    db.setTransactionSuccessful()
                    return@runCatching 0
                }

                val firstTxDate = matchingTxs.first().dateMillis
                val firstTxLocalDate = Instant.ofEpochMilli(firstTxDate).atZone(zoneId).toLocalDate()

                // Check existing installments
                val existingInstallments = db.query(
                    "SELECT * FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? ORDER BY $KEY_INSTALLMENT_NUMBER ASC",
                    arrayOf(loanId.toString())
                ).useAndMapToList { it.toEmiInstallment() }

                val firstInstLocalDate = existingInstallments.firstOrNull()?.let {
                    Instant.ofEpochMilli(it.dueDate).atZone(zoneId).toLocalDate()
                }

                val firstTxYearMonth = YearMonth.from(firstTxLocalDate)
                val firstInstYearMonth = firstInstLocalDate?.let { YearMonth.from(it) }

                val installmentsToWorkWith = if (firstInstYearMonth != null && firstInstYearMonth != firstTxYearMonth) {
                    val realignedLoan = loan.copy(firstEmiDate = firstTxDate, startDate = firstTxDate)
                    val freshSchedule = EmiCalculator.generateInstallmentsForLoan(loanId, realignedLoan, zoneId)

                    db.delete(TABLE_EMI_INSTALLMENTS, "$KEY_LOAN_ID = ?", arrayOf(loanId.toString()))
                    for (inst in freshSchedule) {
                        val instValues = ContentValues().apply {
                            put(KEY_LOAN_ID, loanId)
                            put(KEY_INSTALLMENT_NUMBER, inst.installmentNumber)
                            put(KEY_DUE_DATE, inst.dueDate)
                            put(KEY_PRINCIPAL_PART, inst.principalPart)
                            put(KEY_INTEREST_PART, inst.interestPart)
                            put(KEY_TOTAL_AMOUNT, inst.totalAmount)
                            putNull(KEY_PAID_DATE)
                            putNull("transaction_id")
                            put(KEY_STATUS, InstallmentStatus.PENDING.name)
                        }
                        db.insert(TABLE_EMI_INSTALLMENTS, SQLiteDatabase.CONFLICT_NONE, instValues)
                    }

                    val loanUpdate = ContentValues().apply {
                        put(KEY_START_DATE, firstTxDate)
                    }
                    db.update(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, loanUpdate, "$KEY_ROWID = ?", arrayOf(loanId.toString()))

                    freshSchedule
                } else {
                    existingInstallments
                }

                // Match each transaction to an installment using a flexible window (-10 to +35 days)
                // to cleanly handle delayed payments (e.g. due on 5th, paid on 20th or early next month)
                val matchedTxIds = mutableSetOf<Long>()
                var matchedCount = 0
                for (inst in installmentsToWorkWith) {
                    val instDueDate = inst.dueDate
                    val matchedTx = matchingTxs.find { tx ->
                        !matchedTxIds.contains(tx.id) &&
                                (tx.dateMillis >= instDueDate - 10L * 86400000L) &&
                                (tx.dateMillis <= instDueDate + 35L * 86400000L)
                    }
                    if (matchedTx != null) {
                        matchedTxIds.add(matchedTx.id)
                        val values = ContentValues().apply {
                            put(KEY_STATUS, InstallmentStatus.PAID.name)
                            put(KEY_PAID_DATE, matchedTx.dateMillis)
                            put("transaction_id", matchedTx.id)
                        }
                        db.update(
                            TABLE_EMI_INSTALLMENTS,
                            SQLiteDatabase.CONFLICT_NONE,
                            values,
                            "$KEY_LOAN_ID = ? AND $KEY_INSTALLMENT_NUMBER = ?",
                            arrayOf(loanId.toString(), inst.installmentNumber.toString())
                        )
                        matchedCount++
                    }
                }

                val unpaid = db.query(
                    "SELECT COUNT(*) FROM $TABLE_EMI_INSTALLMENTS WHERE $KEY_LOAN_ID = ? AND $KEY_STATUS != ?",
                    arrayOf(loanId.toString(), InstallmentStatus.PAID.name)
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                val loanStatus = if (unpaid == 0) LoanStatus.CLOSED else LoanStatus.ACTIVE
                val statusUpdate = ContentValues().apply { put(KEY_STATUS, loanStatus.name) }
                db.update(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, statusUpdate, "$KEY_ROWID = ?", arrayOf(loanId.toString()))

                db.setTransactionSuccessful()
                matchedCount
            } finally {
                db.endTransaction()
            }
        }.also {
            invalidate()
        }
    }

    /**
     * Queries recent debit transactions from a specific account for manual linking.
     */
    suspend fun getCandidateTransactions(
        accountId: Long,
        limit: Int = 100
    ): List<PastTransactionInfo> = withContext(coroutineDispatcher) {
        val db = openHelper.readableDatabase
        db.query(
            """
            SELECT $KEY_ROWID, $KEY_DATE, $KEY_AMOUNT, $KEY_COMMENT 
            FROM $TABLE_TRANSACTIONS 
            WHERE $KEY_ACCOUNTID = ? AND $KEY_AMOUNT < 0 
            ORDER BY $KEY_DATE DESC 
            LIMIT ?
            """.trimIndent(),
            arrayOf(accountId.toString(), limit.toString())
        ).useAndMapToList { cursor ->
            PastTransactionInfo(
                id = cursor.getLong(0),
                dateMillis = cursor.getLong(1) * 1000L,
                amount = cursor.getLong(2),
                comment = cursor.getStringOrNull(KEY_COMMENT)
            )
        }
    }

    /**
     * Retrieves all pending installments whose dueDate is on or before [upToMillis]
     * for active loans, along with the linked account's label.
     */
    suspend fun getDueInstallments(upToMillis: Long): List<DueInstallmentInfo> = withContext(coroutineDispatcher) {
        val db = openHelper.readableDatabase
        val query = """
            SELECT i.$KEY_ROWID AS inst_id, i.$KEY_LOAN_ID, i.$KEY_INSTALLMENT_NUMBER, i.$KEY_DUE_DATE, 
                   i.$KEY_PRINCIPAL_PART, i.$KEY_INTEREST_PART, i.$KEY_TOTAL_AMOUNT, i.$KEY_PAID_DATE, 
                   i.transaction_id, i.$KEY_STATUS AS inst_status,
                   l.$KEY_ROWID AS loan_id, l.$KEY_LABEL AS loan_label, l.$KEY_LENDER_NAME, l.$KEY_LOAN_TYPE, 
                   l.$KEY_PRINCIPAL_AMOUNT, l.$KEY_INTEREST_RATE, l.$KEY_TENURE_MONTHS, l.$KEY_MONTHLY_EMI_AMOUNT, 
                   l.$KEY_START_DATE, l.$KEY_DUE_DAY_OF_MONTH, l.$KEY_LINKED_ACCOUNT_ID, l.$KEY_STATUS AS loan_status, 
                   l.auto_debit,
                   a.$KEY_LABEL AS account_label 
            FROM $TABLE_EMI_INSTALLMENTS i 
            JOIN $TABLE_LOANS l ON i.$KEY_LOAN_ID = l.$KEY_ROWID 
            LEFT JOIN $TABLE_ACCOUNTS a ON l.$KEY_LINKED_ACCOUNT_ID = a.$KEY_ROWID 
            WHERE i.$KEY_STATUS = ? AND i.$KEY_DUE_DATE <= ? AND l.$KEY_STATUS = ? 
            ORDER BY i.$KEY_DUE_DATE ASC
        """.trimIndent()
        db.query(
            query,
            arrayOf(InstallmentStatus.PENDING.name, upToMillis.toString(), LoanStatus.ACTIVE.name)
        ).useAndMapToList { cursor ->
            val installment = EmiInstallment(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("inst_id")),
                loanId = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LOAN_ID)),
                installmentNumber = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_INSTALLMENT_NUMBER)),
                dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)),
                principalPart = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_PRINCIPAL_PART)),
                interestPart = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_INTEREST_PART)),
                totalAmount = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TOTAL_AMOUNT)),
                paidDate = cursor.getLongOrNull(KEY_PAID_DATE),
                transactionId = cursor.getLongOrNull("transaction_id"),
                status = enumValueOrDefault(cursor.getString(cursor.getColumnIndexOrThrow("inst_status")), InstallmentStatus.PENDING)
            )
            val autoDebitCol = cursor.getColumnIndex("auto_debit")
            val loan = Loan(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("loan_id")),
                label = cursor.getString(cursor.getColumnIndexOrThrow("loan_label")),
                lenderName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LENDER_NAME)),
                loanType = enumValueOrDefault(cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOAN_TYPE)), LoanType.REDUCING_BALANCE),
                principalAmount = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_PRINCIPAL_AMOUNT)),
                interestRatePercent = cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_INTEREST_RATE)),
                tenureMonths = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TENURE_MONTHS)),
                monthlyEmiAmount = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_MONTHLY_EMI_AMOUNT)),
                startDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_START_DATE)),
                dueDayOfMonth = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_DUE_DAY_OF_MONTH)),
                linkedAccountId = cursor.getLongOrNull(KEY_LINKED_ACCOUNT_ID),
                status = enumValueOrDefault(cursor.getString(cursor.getColumnIndexOrThrow("loan_status")), LoanStatus.ACTIVE),
                autoDebit = if (autoDebitCol >= 0 && !cursor.isNull(autoDebitCol)) cursor.getInt(autoDebitCol) == 1 else false
            )
            val accLabelCol = cursor.getColumnIndex("account_label")
            val accLabel = if (accLabelCol >= 0 && !cursor.isNull(accLabelCol)) cursor.getString(accLabelCol) else null
            DueInstallmentInfo(installment, loan, accLabel)
        }
    }

    private fun Cursor.toLoan(): Loan {
        val autoDebitCol = getColumnIndex("auto_debit")
        return Loan(
            id = getLong(KEY_ROWID),
            label = getString(KEY_LABEL),
            lenderName = getString(KEY_LENDER_NAME),
            loanType = enumValueOrDefault(getString(KEY_LOAN_TYPE), LoanType.REDUCING_BALANCE),
            principalAmount = getLong(KEY_PRINCIPAL_AMOUNT),
            interestRatePercent = getDouble(KEY_INTEREST_RATE),
            tenureMonths = getInt(KEY_TENURE_MONTHS),
            monthlyEmiAmount = getLong(KEY_MONTHLY_EMI_AMOUNT),
            startDate = getLong(KEY_START_DATE),
            dueDayOfMonth = getInt(KEY_DUE_DAY_OF_MONTH),
            linkedAccountId = getLongOrNull(KEY_LINKED_ACCOUNT_ID),
            status = enumValueOrDefault(getString(KEY_STATUS), LoanStatus.ACTIVE),
            autoDebit = if (autoDebitCol >= 0 && !isNull(autoDebitCol)) getInt(autoDebitCol) == 1 else false
        )
    }

    private fun Cursor.toEmiInstallment(): EmiInstallment = EmiInstallment(
        id = getLong(KEY_ROWID),
        loanId = getLong(KEY_LOAN_ID),
        installmentNumber = getInt(KEY_INSTALLMENT_NUMBER),
        dueDate = getLong(KEY_DUE_DATE),
        principalPart = getLong(KEY_PRINCIPAL_PART),
        interestPart = getLong(KEY_INTEREST_PART),
        totalAmount = getLong(KEY_TOTAL_AMOUNT),
        paidDate = getLongOrNull(KEY_PAID_DATE),
        transactionId = getLongOrNull("transaction_id"),
        status = enumValueOrDefault(getString(KEY_STATUS), InstallmentStatus.PENDING)
    )
}
