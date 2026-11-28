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
}
