package org.totschnig.myexpenses.db2

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
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
import org.totschnig.myexpenses.provider.TABLE_EMI_INSTALLMENTS
import org.totschnig.myexpenses.provider.TABLE_LOANS
import org.totschnig.myexpenses.provider.getDouble
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.provider.useAndMapToOne
import org.totschnig.myexpenses.util.enumValueOrDefault
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

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
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = withContext(coroutineDispatcher) {
        val calculatedEmi = customEmi ?: loan.monthlyEmiAmount.takeIf { it > 0L }
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
            }

            val loanId = db.insert(TABLE_LOANS, SQLiteDatabase.CONFLICT_NONE, loanValues)
            require(loanId != -1L) { "Failed to insert loan" }

            val installments = EmiCalculator.generateInstallmentsForLoan(loanId, loanToSave, zoneId)

            for (inst in installments) {
                val instValues = ContentValues().apply {
                    put(KEY_LOAN_ID, loanId)
                    put(KEY_INSTALLMENT_NUMBER, inst.installmentNumber)
                    put(KEY_DUE_DATE, inst.dueDate)
                    put(KEY_PRINCIPAL_PART, inst.principalPart)
                    put(KEY_INTEREST_PART, inst.interestPart)
                    put(KEY_TOTAL_AMOUNT, inst.totalAmount)
                    put(KEY_PAID_DATE, inst.paidDate)
                    put("transaction_id", inst.transactionId)
                    put(KEY_STATUS, inst.status.name)
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
     * Marks an installment as paid, optionally recording an actual expense transaction
     * against the loan's linked bank account.
     * Automatically seals/closes the loan once all installments are paid.
     */
    suspend fun markInstallmentPaid(
        installmentId: Long,
        paidDate: Long = System.currentTimeMillis(),
        createTransaction: Boolean = false
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
                if (createTransaction && loan.linkedAccountId != null && loan.linkedAccountId > 0) {
                    val tx = Transaction(
                        accountId = loan.linkedAccountId,
                        amount = -installment.totalAmount,
                        date = paidDate / 1000L,
                        uuid = generateUuid(),
                        comment = "EMI #${installment.installmentNumber} - ${loan.label}"
                    )
                    val resultTx = repository.createTransaction(tx)
                    createdTransactionId = resultTx.id
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

    private fun Cursor.toLoan(): Loan = Loan(
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
        status = enumValueOrDefault(getString(KEY_STATUS), LoanStatus.ACTIVE)
    )

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
