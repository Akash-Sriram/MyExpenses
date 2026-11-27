package org.totschnig.myexpenses.reconcile

import org.totschnig.myexpenses.viewmodel.data.Transaction2
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object StatementReconciler {

    fun reconcile(
        statementItems: List<StatementTransaction>,
        appTransactions: List<Transaction2>
    ): ReconciliationReport {
        val summaries = appTransactions.map { tx ->
            AppTransactionSummary(
                id = tx.id,
                date = tx.date.toLocalDate(),
                amount = tx.displayAmount.amountMajor,
                payee = tx.party?.displayName ?: tx.comment ?: "Transaction #${tx.id}",
                comment = tx.comment,
                referenceNumber = tx.referenceNumber
            )
        }
        return reconcileSummaries(statementItems, summaries)
    }

    fun reconcileSummaries(
        statementItems: List<StatementTransaction>,
        appSummaries: List<AppTransactionSummary>
    ): ReconciliationReport {
        val matchedAppTxIds = mutableSetOf<Long>()
        val results = mutableListOf<ReconcileResult>()

        for (st in statementItems) {
            val stAmount = st.amount.abs()
            val isWithdrawal = st.withdrawal > BigDecimal.ZERO || st.amount < BigDecimal.ZERO

            var matchedTx: AppTransactionSummary? = null
            var matchReason = ""

            // Tier 1a: UTR exact match
            if (!st.utr.isNullOrBlank()) {
                matchedTx = appSummaries.firstOrNull { tx ->
                    tx.id !in matchedAppTxIds &&
                    ((tx.referenceNumber?.contains(st.utr) == true) ||
                     (tx.comment?.contains(st.utr) == true))
                }
                if (matchedTx != null) {
                    matchReason = "UTR Match (${st.utr})"
                }
            }

            // Tier 1b: Ref ID match
            if (matchedTx == null && !st.ref.isNullOrBlank()) {
                matchedTx = appSummaries.firstOrNull { tx ->
                    tx.id !in matchedAppTxIds &&
                    ((tx.referenceNumber?.contains(st.ref) == true) ||
                     (tx.comment?.contains(st.ref) == true))
                }
                if (matchedTx != null) {
                    matchReason = "Ref Match (${st.ref})"
                }
            }

            // Tier 2: Exact Amount within ±2 calendar days
            if (matchedTx == null) {
                val candidates = appSummaries.filter { tx ->
                    tx.id !in matchedAppTxIds &&
                    tx.amount.abs().compareTo(stAmount) == 0 &&
                    (if (isWithdrawal) tx.amount <= BigDecimal.ZERO else tx.amount >= BigDecimal.ZERO) &&
                    abs(ChronoUnit.DAYS.between(st.date, tx.date)) <= 2
                }

                if (candidates.isNotEmpty()) {
                    // Pick candidate closest in date, and if tie, closest in payee similarity
                    val best = candidates.minByOrNull { tx ->
                        val dayDiff = abs(ChronoUnit.DAYS.between(st.date, tx.date))
                        val payeeBonus = if (tx.payee.isNotBlank() && st.payeeOrRemarks.contains(tx.payee, ignoreCase = true)) -5 else 0
                        dayDiff + payeeBonus
                    }
                    if (best != null) {
                        matchedTx = best
                        matchReason = "Exact Amount ₹$stAmount on ${best.date}"
                    }
                }
            }

            if (matchedTx != null) {
                matchedAppTxIds.add(matchedTx.id)
                results.add(
                    ReconcileResult(
                        statementTx = st,
                        status = MatchStatus.Matched(
                            reason = matchReason,
                            appTransactionId = matchedTx.id,
                            appPayee = matchedTx.payee,
                            appDate = matchedTx.date.toString()
                        )
                    )
                )
            } else {
                results.add(
                    ReconcileResult(
                        statementTx = st,
                        status = MatchStatus.UnmatchedInApp
                    )
                )
            }
        }

        val matchedCount = results.count { it.status is MatchStatus.Matched }
        val unmatchedCount = results.count { it.status is MatchStatus.UnmatchedInApp }

        val unmatchedApp = if (statementItems.isNotEmpty()) {
            val minDate = statementItems.minOf { it.date }.minusDays(2)
            val maxDate = statementItems.maxOf { it.date }.plusDays(2)
            appSummaries.filter { tx ->
                tx.id !in matchedAppTxIds &&
                !tx.date.isBefore(minDate) &&
                !tx.date.isAfter(maxDate)
            }
        } else emptyList()

        return ReconciliationReport(
            totalStatementCount = statementItems.size,
            matchedCount = matchedCount,
            unmatchedCount = unmatchedCount,
            items = results,
            unmatchedAppTransactions = unmatchedApp
        )
    }
}
