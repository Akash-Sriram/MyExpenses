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
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_CREDIT_LIMIT
import org.totschnig.myexpenses.provider.KEY_DUE_DAY_OF_MONTH
import org.totschnig.myexpenses.provider.KEY_LAST_DUE_DATE
import org.totschnig.myexpenses.provider.KEY_LAST_STATEMENT_DATE
import org.totschnig.myexpenses.provider.KEY_MINIMUM_DUE_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_STATEMENT_DAY_OF_MONTH
import org.totschnig.myexpenses.provider.KEY_TOTAL_DUE_AMOUNT
import org.totschnig.myexpenses.provider.KEY_UNBILLED_AMOUNT
import org.totschnig.myexpenses.provider.TABLE_CREDIT_CARD_DETAILS
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.provider.useAndMapToOne
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositoryCreditCard @Inject constructor(
    private val openHelper: SupportSQLiteOpenHelper,
    private val repository: Repository,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _creditCardInvalidation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun invalidate() {
        _creditCardInvalidation.tryEmit(Unit)
    }

    /**
     * Observes credit card details for a specific account.
     */
    fun getCreditCardDetails(accountId: Long): Flow<CreditCardDetail?> = _creditCardInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_CREDIT_CARD_DETAILS WHERE $KEY_ACCOUNTID = ?",
                    arrayOf(accountId)
                ).useAndMapToOne { it.toCreditCardDetail() }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Observes all configured credit card accounts.
     */
    fun getAllCreditCardDetails(): Flow<List<CreditCardDetail>> = _creditCardInvalidation
        .onStart { emit(Unit) }
        .map {
            withContext(coroutineDispatcher) {
                val db = openHelper.readableDatabase
                db.query(
                    "SELECT * FROM $TABLE_CREDIT_CARD_DETAILS ORDER BY $KEY_ACCOUNTID ASC",
                    emptyArray()
                ).useAndMapToList { it.toCreditCardDetail() }
            }
        }.flowOn(coroutineDispatcher)

    /**
     * Saves or updates credit card metadata and billing cycle configuration.
     */
    suspend fun saveCreditCardDetail(detail: CreditCardDetail): Long = withContext(coroutineDispatcher) {
        val db = openHelper.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ACCOUNTID, detail.accountId)
            put(KEY_CREDIT_LIMIT, detail.creditLimit)
            put(KEY_STATEMENT_DAY_OF_MONTH, detail.statementDayOfMonth)
            put(KEY_DUE_DAY_OF_MONTH, detail.dueDayOfMonth)
            put(KEY_TOTAL_DUE_AMOUNT, detail.totalDueAmount)
            put(KEY_MINIMUM_DUE_AMOUNT, detail.minimumDueAmount)
            put(KEY_UNBILLED_AMOUNT, detail.unbilledAmount)
            put(KEY_LAST_STATEMENT_DATE, detail.lastStatementDate)
            put(KEY_LAST_DUE_DATE, detail.lastDueDate)
        }

        val id = db.insert(
            TABLE_CREDIT_CARD_DETAILS,
            SQLiteDatabase.CONFLICT_REPLACE,
            values
        )
        invalidate()
        id
    }

    /**
     * Updates statement figures (total due, minimum due, unbilled spends).
     */
    suspend fun updateCreditCardStatement(
        accountId: Long,
        totalDue: Long,
        minDue: Long,
        unbilled: Long,
        statementDate: Long? = null,
        dueDate: Long? = null
    ): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            val values = ContentValues().apply {
                put(KEY_TOTAL_DUE_AMOUNT, totalDue)
                put(KEY_MINIMUM_DUE_AMOUNT, minDue)
                put(KEY_UNBILLED_AMOUNT, unbilled)
                if (statementDate != null) put(KEY_LAST_STATEMENT_DATE, statementDate)
                if (dueDate != null) put(KEY_LAST_DUE_DATE, dueDate)
            }

            val rows = db.update(
                TABLE_CREDIT_CARD_DETAILS,
                SQLiteDatabase.CONFLICT_NONE,
                values,
                "$KEY_ACCOUNTID = ?",
                arrayOf(accountId)
            )

            if (rows == 0) {
                values.put(KEY_ACCOUNTID, accountId)
                db.insert(TABLE_CREDIT_CARD_DETAILS, SQLiteDatabase.CONFLICT_NONE, values)
            }
            invalidate()
        }
    }

    /**
     * Settles credit card due by creating a transfer transaction from a bank/payment account
     * into the credit card account, automatically reducing the outstanding total due.
     */
    suspend fun settleCreditCardDue(
        creditCardAccountId: Long,
        fromAccountId: Long,
        amount: Long,
        date: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(coroutineDispatcher) {
        runCatching {
            require(creditCardAccountId != fromAccountId) { "Cannot settle credit card from itself" }
            require(amount > 0L) { "Settlement amount must be positive" }

            val epochSeconds = date / 1000L
            val uuid = generateUuid()

            val sourceTx = Transaction(
                accountId = fromAccountId,
                transferAccountId = creditCardAccountId,
                amount = -amount,
                date = epochSeconds,
                uuid = uuid,
                comment = "Credit Card Bill Payment"
            )

            val destTx = Transaction(
                accountId = creditCardAccountId,
                transferAccountId = fromAccountId,
                amount = amount,
                date = epochSeconds,
                uuid = uuid,
                comment = "Credit Card Bill Payment"
            )

            val transferResult = repository.createTransfer(sourceTx, destTx)

            // Reduce total due on credit card
            val db = openHelper.writableDatabase
            val currentDetail = db.query(
                "SELECT * FROM $TABLE_CREDIT_CARD_DETAILS WHERE $KEY_ACCOUNTID = ?",
                arrayOf(creditCardAccountId)
            ).useAndMapToOne { it.toCreditCardDetail() }

            if (currentDetail != null) {
                val updatedDue = (currentDetail.totalDueAmount - amount).coerceAtLeast(0L)
                val updatedMinDue = (currentDetail.minimumDueAmount - amount).coerceAtLeast(0L)
                val values = ContentValues().apply {
                    put(KEY_TOTAL_DUE_AMOUNT, updatedDue)
                    put(KEY_MINIMUM_DUE_AMOUNT, updatedMinDue)
                }
                db.update(
                    TABLE_CREDIT_CARD_DETAILS,
                    SQLiteDatabase.CONFLICT_NONE,
                    values,
                    "$KEY_ACCOUNTID = ?",
                    arrayOf(creditCardAccountId)
                )
            }

            invalidate()
            transferResult.id
        }
    }

    /**
     * Removes credit card metadata for an account.
     */
    suspend fun deleteCreditCardDetail(accountId: Long): Result<Unit> = withContext(coroutineDispatcher) {
        runCatching {
            val db = openHelper.writableDatabase
            db.delete(TABLE_CREDIT_CARD_DETAILS, "$KEY_ACCOUNTID = ?", arrayOf(accountId))
            invalidate()
        }
    }

    private fun Cursor.toCreditCardDetail(): CreditCardDetail = CreditCardDetail(
        id = getLong(KEY_ROWID),
        accountId = getLong(KEY_ACCOUNTID),
        creditLimit = getLong(KEY_CREDIT_LIMIT),
        statementDayOfMonth = getInt(KEY_STATEMENT_DAY_OF_MONTH),
        dueDayOfMonth = getInt(KEY_DUE_DAY_OF_MONTH),
        totalDueAmount = getLong(KEY_TOTAL_DUE_AMOUNT),
        minimumDueAmount = getLong(KEY_MINIMUM_DUE_AMOUNT),
        unbilledAmount = getLong(KEY_UNBILLED_AMOUNT),
        lastStatementDate = getLongOrNull(KEY_LAST_STATEMENT_DATE),
        lastDueDate = getLongOrNull(KEY_LAST_DUE_DATE)
    )
}
