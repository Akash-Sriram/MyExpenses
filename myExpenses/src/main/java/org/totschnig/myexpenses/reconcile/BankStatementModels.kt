package org.totschnig.myexpenses.reconcile

import java.math.BigDecimal
import java.time.LocalDate

data class StatementTransaction(
    val sNo: Int,
    val date: LocalDate,
    val payeeOrRemarks: String,
    val withdrawal: BigDecimal,
    val deposit: BigDecimal,
    val amount: BigDecimal, // negative for withdrawal, positive for deposit
    val balance: BigDecimal,
    val utr: String? = null,
    val ref: String? = null,
    val rawText: String = ""
)

sealed class MatchStatus {
    data class Matched(
        val reason: String,
        val appTransactionId: Long,
        val appPayee: String,
        val appDate: String
    ) : MatchStatus()
    
    object UnmatchedInApp : MatchStatus()
}

data class ReconcileResult(
    val statementTx: StatementTransaction,
    val status: MatchStatus
)

data class ReconciliationReport(
    val totalStatementCount: Int,
    val matchedCount: Int,
    val unmatchedCount: Int,
    val items: List<ReconcileResult>,
    val unmatchedAppTransactions: List<AppTransactionSummary> = emptyList()
)

data class AppTransactionSummary(
    val id: Long,
    val date: LocalDate,
    val amount: BigDecimal,
    val payee: String,
    val comment: String? = null,
    val referenceNumber: String? = null
)

data class BalanceCheckpoint(
    val accountDigits: String?,
    val transactionAmount: BigDecimal?,
    val isDebit: Boolean,
    val transactionDate: LocalDate?,
    val info: String?,
    val availableBalance: BigDecimal,
    val rawText: String
)

data class BalanceVerificationResult(
    val appBalance: BigDecimal,
    val bankBalance: BigDecimal,
    val difference: BigDecimal,
    val isAligned: Boolean,
    val checkpoint: BalanceCheckpoint?
)
