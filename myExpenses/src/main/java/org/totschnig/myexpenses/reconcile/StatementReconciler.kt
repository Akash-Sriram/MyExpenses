package org.totschnig.myexpenses.reconcile

import org.totschnig.myexpenses.viewmodel.data.Transaction2
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object StatementReconciler {

    private val STOP_WORDS = setOf(
        "UPI", "PAY", "TRANSFER", "REV", "IMPS", "NEFT", "RTGS",
        "LIMITED", "PVT", "LTD", "BANK", "CORP", "PAYMENT", "ACCOUNT"
    )

    private fun tokenize(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return text.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 3 }
            .map { it.uppercase() }
            .filter { it !in STOP_WORDS }
            .toSet()
    }

    private fun hasPayeeAffinity(stPayee: String, appPayee: String, appComment: String?): Boolean {
        val stTokens = tokenize(stPayee)
        val appTokens = tokenize(appPayee) + tokenize(appComment)
        return (stTokens.intersect(appTokens)).isNotEmpty()
    }

    private fun <T> findCombinationSum(
        items: List<T>,
        targetSum: BigDecimal,
        maxSize: Int = 4,
        amountExtractor: (T) -> BigDecimal
    ): List<T>? {
        fun search(
            startIndex: Int,
            currentCombo: MutableList<T>,
            currentSum: BigDecimal
        ): List<T>? {
            if (currentCombo.size in 2..maxSize && currentSum.compareTo(targetSum) == 0) {
                return currentCombo.toList()
            }
            if (currentCombo.size >= maxSize || currentSum > targetSum) {
                return null
            }
            for (i in startIndex until items.size) {
                val item = items[i]
                val amt = amountExtractor(item)
                if (currentSum + amt <= targetSum) {
                    currentCombo.add(item)
                    val result = search(i + 1, currentCombo, currentSum + amt)
                    if (result != null) return result
                    currentCombo.removeAt(currentCombo.lastIndex)
                }
            }
            return null
        }

        return search(0, mutableListOf(), BigDecimal.ZERO)
    }

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

        // Pass 1: 1-to-1 exact matching (UTR, Ref, Exact Amount)
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

        // Pass 2: N-to-1 Merged Transactions Matching Pass
        // Handles cases where multiple statement entries (e.g. ₹3 + ₹2 or ₹1200 + ₹500) were entered as a single merged transaction in the app
        val remainingAppSummaries = appSummaries.filter { it.id !in matchedAppTxIds }
        for (appTx in remainingAppSummaries) {
            val appAmountAbs = appTx.amount.abs()
            val isAppWithdrawal = appTx.amount <= BigDecimal.ZERO

            // Get unmatched statement result indices
            val candidateIndices = results.indices.filter { idx ->
                results[idx].status is MatchStatus.UnmatchedInApp && run {
                    val st = results[idx].statementTx
                    val isWithdrawal = st.withdrawal > BigDecimal.ZERO || st.amount < BigDecimal.ZERO
                    isWithdrawal == isAppWithdrawal && abs(ChronoUnit.DAYS.between(st.date, appTx.date)) <= 2
                }
            }

            if (candidateIndices.size >= 2) {
                // Try first with candidate indices that exhibit payee affinity with appTx
                val affinityIndices = candidateIndices.filter { idx ->
                    hasPayeeAffinity(results[idx].statementTx.payeeOrRemarks, appTx.payee, appTx.comment)
                }

                val matchedIndices = if (affinityIndices.size >= 2) {
                    findCombinationSum(
                        items = affinityIndices.sortedByDescending { results[it].statementTx.amount.abs() },
                        targetSum = appAmountAbs,
                        amountExtractor = { results[it].statementTx.amount.abs() }
                    )
                } else null ?: run {
                    // Fallback: check candidates with same date where all candidates share the same payee
                    val sameDateIndices = candidateIndices.filter { results[it].statementTx.date == appTx.date }
                    if (sameDateIndices.size >= 2) {
                        findCombinationSum(
                            items = sameDateIndices.sortedByDescending { results[it].statementTx.amount.abs() },
                            targetSum = appAmountAbs,
                            amountExtractor = { results[it].statementTx.amount.abs() }
                        )
                    } else null
                }

                if (matchedIndices != null) {
                    matchedAppTxIds.add(appTx.id)
                    for (idx in matchedIndices) {
                        results[idx] = ReconcileResult(
                            statementTx = results[idx].statementTx,
                            status = MatchStatus.Matched(
                                reason = "Merged in App (App #${appTx.id} on ${appTx.date}, Total: ₹$appAmountAbs)",
                                appTransactionId = appTx.id,
                                appPayee = appTx.payee,
                                appDate = appTx.date.toString()
                            )
                        )
                    }
                }
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
