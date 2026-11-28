package org.totschnig.myexpenses.reconcile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BankStatementParserTest {

    @Test
    fun testParseSpatialElementsIciciStatementLayout() {
        // Simulates the exact spatial coordinates found in ICICI Bank statements
        val elements = listOf(
            // Header elements (should be ignored)
            SpatialPdfElement(x = 23.54f, y = 613.98f, text = "S No.", page = 1),
            SpatialPdfElement(x = 60.24f, y = 619.00f, text = "Transaction", page = 1),
            SpatialPdfElement(x = 74.44f, y = 608.96f, text = "Date", page = 1),
            SpatialPdfElement(x = 247.13f, y = 613.98f, text = "Transaction Remarks", page = 1),
            SpatialPdfElement(x = 398.98f, y = 619.00f, text = "Withdrawal", page = 1),
            SpatialPdfElement(x = 473.79f, y = 619.00f, text = "Deposit", page = 1),
            SpatialPdfElement(x = 532.14f, y = 619.00f, text = "Balance", page = 1),

            // Tx 1: ACCENTURE SOLUTIONS PVT LTD (Deposit 39710.00, Bal 39804.25)
            SpatialPdfElement(x = 192.00f, y = 594.02f, text = "ACCENTURE SOLUTIONS PVT LTD", page = 1),
            SpatialPdfElement(x = 30.10f, y = 588.98f, text = "1", page = 1),
            SpatialPdfElement(x = 61.40f, y = 588.98f, text = "28.08.2026", page = 1),
            SpatialPdfElement(x = 483.50f, y = 588.98f, text = "39710.00", page = 1),
            SpatialPdfElement(x = 535.50f, y = 588.98f, text = "39804.25", page = 1),
            SpatialPdfElement(x = 192.00f, y = 583.98f, text = "NEFT-BOFAH00008026769-ACCENTURE", page = 1),
            SpatialPdfElement(x = 192.00f, y = 573.94f, text = "SOLUTIONS PVT LTD--72447083-BOFA0MM6205", page = 1),

            // Tx 2: Self (Withdrawal 4821.24, Bal 34983.01)
            SpatialPdfElement(x = 192.00f, y = 564.00f, text = "Self", page = 1),
            SpatialPdfElement(x = 30.10f, y = 558.98f, text = "2", page = 1),
            SpatialPdfElement(x = 61.40f, y = 558.98f, text = "28.08.2026", page = 1),
            SpatialPdfElement(x = 422.30f, y = 558.98f, text = "4821.24", page = 1),
            SpatialPdfElement(x = 535.50f, y = 558.98f, text = "34983.01", page = 1),
            SpatialPdfElement(x = 192.00f, y = 553.96f, text = "BIL/INFT/FHW1731943/CC BillPay-5007/Self", page = 1),

            // Tx 3: NANDHINI D (Withdrawal 400.00, Bal 34583.01)
            SpatialPdfElement(x = 192.00f, y = 544.06f, text = "NANDHINI D", page = 1),
            SpatialPdfElement(x = 30.10f, y = 538.98f, text = "3", page = 1),
            SpatialPdfElement(x = 61.40f, y = 538.98f, text = "28.08.2026", page = 1),
            SpatialPdfElement(x = 427.10f, y = 538.98f, text = "400.00", page = 1),
            SpatialPdfElement(x = 535.50f, y = 538.98f, text = "34583.01", page = 1),
            SpatialPdfElement(x = 192.00f, y = 534.02f, text = "UPI/NANDHINI D/nandhini.m87-1/UPI", page = 1)
        )

        val results = BankStatementParser.parseSpatialElements(elements)

        assertEquals(3, results.size)

        // Tx 1
        assertEquals(1, results[0].sNo)
        assertEquals(LocalDate.of(2026, 8, 28), results[0].date)
        assertEquals("ACCENTURE SOLUTIONS PVT LTD", results[0].payeeOrRemarks)
        assertEquals(BigDecimal("39710.00"), results[0].deposit)
        assertEquals(BigDecimal.ZERO, results[0].withdrawal)
        assertEquals(BigDecimal("39710.00"), results[0].amount)
        assertEquals(BigDecimal("39804.25"), results[0].balance)

        // Tx 2
        assertEquals(2, results[1].sNo)
        assertEquals(LocalDate.of(2026, 8, 28), results[1].date)
        assertEquals("Self", results[1].payeeOrRemarks)
        assertEquals(BigDecimal.ZERO, results[1].deposit)
        assertEquals(BigDecimal("4821.24"), results[1].withdrawal)
        assertEquals(BigDecimal("-4821.24"), results[1].amount)
        assertEquals(BigDecimal("34983.01"), results[1].balance)

        // Tx 3
        assertEquals(3, results[2].sNo)
        assertEquals(LocalDate.of(2026, 8, 28), results[2].date)
        assertEquals("NANDHINI D", results[2].payeeOrRemarks)
        assertEquals(BigDecimal.ZERO, results[2].deposit)
        assertEquals(BigDecimal("400.00"), results[2].withdrawal)
        assertEquals(BigDecimal("-400.00"), results[2].amount)
        assertEquals(BigDecimal("34583.01"), results[2].balance)
    }

    @Test
    fun testParseSpatialElementsRunningBalanceDifference() {
        // Tx with missing withdrawal column but with valid balances
        val elements = listOf(
            SpatialPdfElement(x = 30.10f, y = 588.98f, text = "1", page = 1),
            SpatialPdfElement(x = 61.40f, y = 588.98f, text = "01.09.2026", page = 1),
            SpatialPdfElement(x = 192.00f, y = 594.00f, text = "Opening Deposit", page = 1),
            SpatialPdfElement(x = 483.50f, y = 588.98f, text = "10000.00", page = 1),
            SpatialPdfElement(x = 535.50f, y = 588.98f, text = "10000.00", page = 1),

            SpatialPdfElement(x = 30.10f, y = 558.98f, text = "2", page = 1),
            SpatialPdfElement(x = 61.40f, y = 558.98f, text = "02.09.2026", page = 1),
            SpatialPdfElement(x = 192.00f, y = 564.00f, text = "Coffee Shop", page = 1),
            // No withdrawal element here!
            SpatialPdfElement(x = 535.50f, y = 558.98f, text = "9850.00", page = 1)
        )

        val results = BankStatementParser.parseSpatialElements(elements)

        assertEquals(2, results.size)
        assertEquals(BigDecimal("150.00"), results[1].withdrawal)
        assertEquals(BigDecimal("-150.00"), results[1].amount)
        assertEquals(BigDecimal("9850.00"), results[1].balance)
    }

    @Test
    fun testParseStructuredTextFallback() {
        val rawText = """
            S No. Transaction Date Cheque Number Transaction Remarks Withdrawal Amount (INR) Deposit Amount (INR) Balance (INR)
            1 28.08.2026 ACCENTURE SOLUTIONS PVT LTD
            NEFT-BOFAH00008026769-ACCENTURE
            SOLUTIONS PVT LTD--72447083-BOFA0MM6205 39710.00 39804.25
            2 28.08.2026 Self
            BIL/INFT/FHW1731943/CC BillPay-5007/Self 4821.24 34983.01
        """.trimIndent()

        val results = BankStatementParser.parse(rawText)

        assertEquals(2, results.size)
        assertEquals(1, results[0].sNo)
        assertEquals("ACCENTURE SOLUTIONS PVT LTD", results[0].payeeOrRemarks)
        assertEquals(BigDecimal("39804.25"), results[0].balance)

        assertEquals(2, results[1].sNo)
        assertEquals("Self", results[1].payeeOrRemarks)
        assertEquals(BigDecimal("4821.24"), results[1].withdrawal)
        assertEquals(BigDecimal("-4821.24"), results[1].amount)
        assertEquals(BigDecimal("34983.01"), results[1].balance)
    }

    @Test
    fun testStatementReconciliationMatching() {
        val st = StatementTransaction(
            sNo = 1,
            date = LocalDate.of(2026, 8, 28),
            payeeOrRemarks = "ACCENTURE SOLUTIONS PVT LTD",
            withdrawal = BigDecimal.ZERO,
            deposit = BigDecimal("39710.00"),
            amount = BigDecimal("39710.00"),
            balance = BigDecimal("39804.25"),
            ref = "BOFA0MM6205"
        )

        val appTx = AppTransactionSummary(
            id = 101L,
            date = LocalDate.of(2026, 8, 28),
            amount = BigDecimal("39710.00"),
            payee = "ACCENTURE SOLUTIONS PVT LTD",
            referenceNumber = "BOFA0MM6205"
        )

        val report = StatementReconciler.reconcileSummaries(listOf(st), listOf(appTx))

        assertEquals(1, report.matchedCount)
        assertEquals(0, report.unmatchedCount)
        assertTrue(report.items.first().status is MatchStatus.Matched)
    }

    @Test
    fun testMergedTransactionsMatchingPairwise() {
        // Test Sridhar ₹3 + ₹2 = ₹5
        val stSridhar1 = StatementTransaction(
            sNo = 1,
            date = LocalDate.of(2026, 9, 15),
            payeeOrRemarks = "SRIDHAR R",
            withdrawal = BigDecimal("3.00"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-3.00"),
            balance = BigDecimal("1000.00"),
            utr = "505062002586"
        )
        val stSridhar2 = StatementTransaction(
            sNo = 2,
            date = LocalDate.of(2026, 9, 15),
            payeeOrRemarks = "SRIDHAR R",
            withdrawal = BigDecimal("2.00"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-2.00"),
            balance = BigDecimal("998.00"),
            utr = "505194602586"
        )
        val appSridhar = AppTransactionSummary(
            id = 2235L,
            date = LocalDate.of(2026, 9, 15),
            amount = BigDecimal("-5.00"),
            payee = "Sridhar R"
        )

        // Test Vigneshwar ₹1200 + ₹500 = ₹1700
        val stVignesh1 = StatementTransaction(
            sNo = 3,
            date = LocalDate.of(2026, 10, 5),
            payeeOrRemarks = "VIGNESHWAR",
            withdrawal = BigDecimal("1200.00"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-1200.00"),
            balance = BigDecimal("50000.00"),
            utr = "628666362786"
        )
        val stVignesh2 = StatementTransaction(
            sNo = 4,
            date = LocalDate.of(2026, 10, 5),
            payeeOrRemarks = "VIGNESHWAR",
            withdrawal = BigDecimal("500.00"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-500.00"),
            balance = BigDecimal("49500.00"),
            utr = "110840262435"
        )
        val appVignesh = AppTransactionSummary(
            id = 2300L,
            date = LocalDate.of(2026, 10, 5),
            amount = BigDecimal("-1700.00"),
            payee = "VIGNESHWAR B"
        )

        // Test Nandhini +₹100 + +₹100 = +₹200
        val stNandhini1 = StatementTransaction(
            sNo = 5,
            date = LocalDate.of(2026, 10, 6),
            payeeOrRemarks = "NANDHINI D",
            withdrawal = BigDecimal.ZERO,
            deposit = BigDecimal("100.00"),
            amount = BigDecimal("100.00"),
            balance = BigDecimal("49600.00"),
            utr = "627919581999"
        )
        val stNandhini2 = StatementTransaction(
            sNo = 6,
            date = LocalDate.of(2026, 10, 6),
            payeeOrRemarks = "NANDHINI D",
            withdrawal = BigDecimal.ZERO,
            deposit = BigDecimal("100.00"),
            amount = BigDecimal("100.00"),
            balance = BigDecimal("49700.00"),
            utr = "627919584253"
        )
        val appNandhini = AppTransactionSummary(
            id = 2318L,
            date = LocalDate.of(2026, 10, 6),
            amount = BigDecimal("200.00"),
            payee = "NANDHINI DEVI M"
        )

        val statementList = listOf(stSridhar1, stSridhar2, stVignesh1, stVignesh2, stNandhini1, stNandhini2)
        val appList = listOf(appSridhar, appVignesh, appNandhini)

        val report = StatementReconciler.reconcileSummaries(statementList, appList)

        assertEquals("All 6 statement items should be matched", 6, report.matchedCount)
        assertEquals("0 statement items should be unmatched", 0, report.unmatchedCount)
        assertEquals("0 app transactions should be unmatched", 0, report.unmatchedAppTransactions.size)

        for (item in report.items) {
            val status = item.status
            assertTrue("Item ${item.statementTx.sNo} should be Matched", status is MatchStatus.Matched)
            assertTrue(
                "Match reason should denote Merged in App: ${(status as MatchStatus.Matched).reason}",
                status.reason.startsWith("Merged in App")
            )
        }
    }

    @Test
    fun testParseRefundRemarkDetails() {
        val rawRemark = "RFNDZEPTO/XXpayu@hdfcban/burger/HDFC BANK/805834443326/PPPL320496238642811260639526b0a29e0"
        val parsed = BankStatementParser.parseRemarkDetails(rawRemark)

        assertEquals("Zepto", parsed.payee)
        assertEquals("burger refund", parsed.comment)
        assertEquals("805834443326", parsed.utr)
        assertTrue(parsed.isRefund)
    }

    @Test
    fun testParseSpatialElementsWithRefund() {
        val elements = listOf(
            SpatialPdfElement(x = 30.10f, y = 588.98f, text = "1", page = 1),
            SpatialPdfElement(x = 61.40f, y = 588.98f, text = "28.11.2026", page = 1),
            SpatialPdfElement(x = 192.00f, y = 594.02f, text = "RFNDZEPTO/XXpayu@hdfcban/burger/HDFC BANK/805834443326/PPPL320496238642811260639526b0a29e0", page = 1),
            SpatialPdfElement(x = 483.50f, y = 588.98f, text = "160.40", page = 1),
            SpatialPdfElement(x = 535.50f, y = 588.98f, text = "35000.00", page = 1)
        )

        val results = BankStatementParser.parseSpatialElements(elements)

        assertEquals(1, results.size)
        val st = results[0]
        assertEquals("Zepto", st.payeeOrRemarks)
        assertEquals("burger refund", st.comment)
        assertEquals("805834443326", st.utr)
        assertTrue(st.isRefund)
        assertEquals(BigDecimal("160.40"), st.deposit)
        assertEquals(BigDecimal.ZERO, st.withdrawal)
        assertEquals(BigDecimal("160.40"), st.amount)
    }

    @Test
    fun testReconcileOrderReorderAndRefundSharingUtr() {
        // 1. Cancelled Order at 6:40 AM (Expense -160.40, UTR 805834443326)
        val stOrder = StatementTransaction(
            sNo = 1,
            date = LocalDate.of(2026, 11, 28),
            payeeOrRemarks = "Zepto",
            withdrawal = BigDecimal("160.40"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-160.40"),
            balance = BigDecimal("34839.60"),
            utr = "805834443326"
        )

        // 2. Successful Re-order at 7:01 AM (Expense -160.40, UTR 070139746741)
        val stReorder = StatementTransaction(
            sNo = 2,
            date = LocalDate.of(2026, 11, 28),
            payeeOrRemarks = "Zepto",
            withdrawal = BigDecimal("160.40"),
            deposit = BigDecimal.ZERO,
            amount = BigDecimal("-160.40"),
            balance = BigDecimal("34679.20"),
            utr = "070139746741"
        )

        // 3. Auto-refund received at 11:07 AM (Income +160.40, UTR 805834443326, same UTR as original order!)
        val stRefund = StatementTransaction(
            sNo = 3,
            date = LocalDate.of(2026, 11, 28),
            payeeOrRemarks = "Zepto",
            withdrawal = BigDecimal.ZERO,
            deposit = BigDecimal("160.40"),
            amount = BigDecimal("160.40"),
            balance = BigDecimal("34839.60"),
            utr = "805834443326",
            comment = "burger refund",
            isRefund = true
        )

        // App Transactions recorded
        val appOrder = AppTransactionSummary(
            id = 1001L,
            date = LocalDate.of(2026, 11, 28),
            amount = BigDecimal("-160.40"),
            payee = "Zepto",
            referenceNumber = "805834443326"
        )
        val appReorder = AppTransactionSummary(
            id = 1002L,
            date = LocalDate.of(2026, 11, 28),
            amount = BigDecimal("-160.40"),
            payee = "Zepto",
            referenceNumber = "070139746741"
        )
        val appRefund = AppTransactionSummary(
            id = 1003L,
            date = LocalDate.of(2026, 11, 28),
            amount = BigDecimal("160.40"),
            payee = "ZEPTO MARKETPLACE PRIVATE LIMITED", // Even with legacy corporate payee name!
            comment = "burger refund"
        )

        val statementList = listOf(stOrder, stReorder, stRefund)
        val appList = listOf(appOrder, appReorder, appRefund)

        val report = StatementReconciler.reconcileSummaries(statementList, appList)

        assertEquals("All 3 transactions should be matched", 3, report.matchedCount)
        assertEquals("0 transactions should be unmatched", 0, report.unmatchedCount)

        val matchOrder = (report.items.first { it.statementTx.sNo == 1 }.status as MatchStatus.Matched)
        val matchReorder = (report.items.first { it.statementTx.sNo == 2 }.status as MatchStatus.Matched)
        val matchRefund = (report.items.first { it.statementTx.sNo == 3 }.status as MatchStatus.Matched)

        assertEquals("Order should match appOrder (1001)", 1001L, matchOrder.appTransactionId)
        assertEquals("Reorder should match appReorder (1002)", 1002L, matchReorder.appTransactionId)
        assertEquals("Refund should match appRefund (1003)", 1003L, matchRefund.appTransactionId)
    }
}
