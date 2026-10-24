package org.totschnig.myexpenses.util.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class UpiReceiptParserTest {

    @Test
    fun testParseSamsungWalletReceipt() {
        val rawText = """
            Paid from
            Samsung Wallet
            
            BIGTREE ENTERTAINMENT PRIVATE LIMITED
            bookmyshow@axb
            14 OCT 2026, 05:23 PM
            
            ₹70.87
            Sent
            
            Sent from: asg98@pingpay
            Transaction ID: 301966032876
            Notes: BIGTREE ENTERTAINMENT PRIVATE LIMITED
            
            Powered by AXIS BANK | UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("70.87"), result.amount)
        assertEquals("BIGTREE ENTERTAINMENT PRIVATE LIMITED", result.payeeName)
        assertEquals("bookmyshow@axb", result.upiId)
        assertEquals(LocalDateTime.of(2026, 10, 14, 17, 23), result.dateTime)
        assertEquals("301966032876", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("AXIS", ignoreCase = true) == true || result.accountOrBankHint?.contains("Samsung", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseSamsungWalletReceiptPocoF1LogcatScan() {
        val rawText = """
            Sent from:
            Notes:
            Paid from
            SRI VIGNESHWARY AGENCY
            paytmqr6tgwxe@ptys
            Transaction ID:
            Samsung Walet
            14 0CT 2026, 12:23 PM
            215.94
            Sent
            Powered by AXIS BANK
            asg98@pingpay
            242169832876
            UNIFIED PAYMENTS INTERFACE
            UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("215.94"), result.amount)
        assertEquals("SRI VIGNESHWARY AGENCY", result.payeeName)
        assertEquals("paytmqr6tgwxe@ptys", result.upiId)
        assertEquals(LocalDateTime.of(2026, 10, 14, 12, 23), result.dateTime)
        assertEquals("242169832876", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("AXIS", ignoreCase = true) == true || result.accountOrBankHint?.contains("Samsung", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseSamsungWalletReceiptWithUnicodeWhitespaceAndDot() {
        val rawText = """
            Paid from
            Samsung Wallet
            
            BIGTREE ENTERTAINMENT PRIVATE LIMITED
            bookmyshow@axb
            14 OCT 2026. 05:23${'\u202F'}PM
            
            ₹70.87
            Sent
            
            Transaction ID: 301966032876
            Powered by AXIS BANK | UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("70.87"), result.amount)
        assertEquals("BIGTREE ENTERTAINMENT PRIVATE LIMITED", result.payeeName)
        assertEquals("bookmyshow@axb", result.upiId)
        assertEquals(LocalDateTime.of(2026, 10, 14, 17, 23), result.dateTime)
        assertEquals("301966032876", result.referenceNumber)
    }

    @Test
    fun testParseSamsungWalletReceiptMultiLineDateTime() {
        val rawText = """
            Paid from
            Samsung Wallet
            
            BIGTREE ENTERTAINMENT PRIVATE LIMITED
            bookmyshow@axb
            14 OCT 2026,
            05:23${'\u202F'}PM
            
            ₹70.87
            Sent
            
            Transaction ID: 301966032876
            Powered by AXIS BANK | UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("70.87"), result.amount)
        assertEquals(LocalDateTime.of(2026, 10, 14, 17, 23), result.dateTime)
    }

    @Test
    fun testParseIciciIMobileReceipt() {
        val rawText = """
            Transaction Successful
            Paid to Akshaya R
            Payment of INR 2,224.00
            Txn Date: 15/03/2026 10:34:52
            Paid from ICICI Bank Account XX1234
            UPI Transaction ID: 607512345678
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("2224.00"), result.amount)
        assertEquals("Akshaya R", result.payeeName)
        assertEquals(LocalDateTime.of(2026, 3, 15, 10, 34, 52), result.dateTime)
        assertEquals("607512345678", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseGooglePayReceipt() {
        val rawText = """
            ₹100.00
            Paid to Rani H
            Payment completed
            Oct 10, 2024 02:45 PM
            To: Rani H (rani@okhdfcbank)
            From: Akash Sriram (ICICI Bank XX5678)
            UPI transaction ID: 431512345678
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals("Rani H", result.payeeName)
        assertEquals("rani@okhdfcbank", result.upiId)
        assertEquals(LocalDateTime.of(2024, 10, 10, 14, 45), result.dateTime)
        assertEquals("431512345678", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParsePaytmReceipt() {
        val rawText = """
            Payment Successful
            ₹130.00
            To: Mansoor
            From: ICICI Bank A/c No. XX1234
            UPI Ref No: 500412345678
            04 Jan 2025, 12:44 PM
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("130.00"), result.amount)
        assertEquals("Mansoor", result.payeeName)
        assertEquals(LocalDateTime.of(2025, 1, 4, 12, 44), result.dateTime)
        assertEquals("500412345678", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseReceiptWithDateWithoutYearAndAt() {
        val currentYear = java.time.LocalDate.now().year
        val rawText = """
            ₹500
            Paid to Chai Point
            14th Oct at 05:23 PM
            UPI transaction ID: 998877665544
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("500"), result.amount)
        assertEquals("Chai Point", result.payeeName)
        assertEquals(LocalDateTime.of(currentYear, 10, 14, 17, 23), result.dateTime)
        assertEquals("998877665544", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseReceiptWithTwoDigitYearAndLabeledAmount() {
        val rawText = """
            Payment of 1,500.50
            Paid to Dr. Ramesh
            14/10/24 10:45 AM
            Transaction ID: 112233445566
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("1500.50"), result.amount)
        assertEquals("Dr. Ramesh", result.payeeName)
        assertEquals(LocalDateTime.of(2024, 10, 14, 10, 45), result.dateTime)
        assertEquals("112233445566", result.referenceNumber)
    }

    @Test
    fun testParseReceiptWithTimeBeforeDate() {
        val rawText = """
            ₹250.00
            Paid to Supermarket
            05:23 PM, 14 Oct 2026
            UPI Ref No: 123456789012
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("250.00"), result.amount)
        assertEquals("Supermarket", result.payeeName)
        assertEquals(LocalDateTime.of(2026, 10, 14, 17, 23), result.dateTime)
        assertEquals("123456789012", result.referenceNumber)
    }

    @Test
    fun testParseIciciMultiLineReceiptWithReferenceAndPromoBanner() {
        val rawText = """
            ICICIBank
            Success!
            Reference # 424475190746
            To
            NIVETHA S
            To VPA
            nxxxxxxxxxxxx4@okicici
            Amount
            ₹ 618.00
            From
            SBA-XXXXXXXXXX07
            From UPI ID
            8680878355@icici
            When
            31 Aug 2024, 10:22 AM
            Remarks
            UPI Payment
            Once in a while
            it's ok to give in to
            your shopping desires.
            Treat your family to a good time.
            with iWish Goal Based Savings!
            OPEN iWISH
            Home
            UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("618.00"), result.amount)
        assertEquals("NIVETHA S", result.payeeName)
        assertEquals("nxxxxxxxxxxxx4@okicici", result.upiId)
        assertEquals(LocalDateTime.of(2024, 8, 31, 10, 22), result.dateTime)
        assertEquals("424475190746", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertEquals("UPI", result.paymentMethodHint)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParsePocoF1LogcatActualMlKitOutput() {
        val rawText = """
            To
            To VPA
            Amount
            From
            From UP| ID
            When
            Remarks
            Wih ivVish Goal
            OPEN WISH
            cICI Bank
            Success!
            TaCs
            Reference # 424475190746
            NIVETHAS
            nXXXXXXXXXXXX4@okicici
            7 618.00
            SBA-XXXXXXXXXXO7
            Once in a while,
            i's ok to aive in to
            O give int
            your shopping desires.
            8680878355@icici
            31 Aug 2024, 10:22 AM
            Treot your family to a good time.
            UPI Payment
            Savings!
            Home
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("618.00"), result.amount)
        assertEquals("NIVETHAS", result.payeeName)
        assertEquals("nXXXXXXXXXXXX4@okicici", result.upiId)
        assertEquals(LocalDateTime.of(2024, 8, 31, 10, 22), result.dateTime)
        assertEquals("424475190746", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertEquals("UPI", result.paymentMethodHint)
        assertTrue(result.isExpense)
    }

    @Test
    fun testParseIciciSujatha() {
        val rawText = """
            iIcICIBank
            To
            SUJATHAS
            ToVPA
            axxxxxxxxxxxxxx1@okicici
            Amount
            600.00
            From
            SBA-XXXXXXXXXX07
            From UPI ID
            8680878355@icici
            When
            4Oct2024.03:21PM
            Rurriar k.u
            UP1 Purytwmnt
            iMobile Pay
            Applyfor
            Personal Loan up to
            Personal
            Loan
            天50lakh
            digitallywithminimal
            documentation.
            ApplyNow
            Approved
            &Cs
            Home
            POWERED BY
            UP
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("600.00"), result.amount)
        assertEquals("SUJATHAS", result.payeeName)
        assertEquals("axxxxxxxxxxxxxx1@okicici", result.upiId)
        assertEquals(LocalDateTime.of(2024, 10, 4, 15, 21), result.dateTime)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertEquals("UPI", result.paymentMethodHint)
    }

    @Test
    fun testParseIciciSujathaPocoF1Logcat() {
        val rawText = """
            To
            To VPA
            Amount
            From
            From UP ID
            When
            urmar ku
            IcICI Bank
            SUJATHA S
            aXXXXXXXXXXXXX1@okicici
            R600.00
            SBA-XXXXXXXXXX07
            ৪680878355G@icicl
            4০ct 2024, 03:21PM
            urayrr।
            Mobile Pey
            Logn
            Personal
            Approved
            Apply for
            Personal Loan up to
            ₹50 |akh
            digitally with minimal
            documentation.
            Home
            Apply Now
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("600.00"), result.amount)
        assertEquals("SUJATHA S", result.payeeName)
        assertEquals("aXXXXXXXXXXXXX1@okicici", result.upiId)
        assertEquals(LocalDateTime.of(2024, 10, 4, 15, 21), result.dateTime)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true)
        assertEquals("UPI", result.paymentMethodHint)
    }

    @Test
    fun testParseIciciMahaamPump() {
        val rawText = """
            Paid to
            To UPI ID
            1cICI Bank
            From account
            Success!
            Amount: ३ 201.72
            Fri, 27 Sep, 2024 at 06:34 PM
            PUMP 3 MAHAA MHERU
            ENTERPRISE
            Q३20098314@ybl
            SBA-XXXXXXXX2407
            Transaction ID 427102071017
            Next Time - Save Time.
            Pay directly without scanning the OR
            code.
            Add to your favourite list.
            Home
            UFID
            Add Now
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("201.72"), result.amount)
        assertEquals("PUMP 3 MAHAA MHERU ENTERPRISE", result.payeeName)
        assertEquals("Q320098314@ybl", result.upiId)
        assertEquals(LocalDateTime.of(2024, 9, 27, 18, 34), result.dateTime)
        assertEquals("427102071017", result.referenceNumber)
        assertEquals("ICICI Bank", result.accountOrBankHint)
        assertEquals("UPI", result.paymentMethodHint)
    }

    @Test
    fun testParsePhonePeFuelPump() {
        val rawText = """
            पे
            Paid to
            PUMP 9 MAHAA MHERU
            Q75775971 6@ybl
            Payment details
            Transaction ID
            T2411020824381969639267
            Debited from
            XX
            (XXX07
            UTR: 141814848725
            Powered by
            UPIP VYES BANK
            হ125
            ₹125
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("125"), result.amount)
        assertEquals("PUMP 9 MAHAA MHERU", result.payeeName)
        assertEquals("Q757759716@ybl", result.upiId)
        assertEquals("T2411020824381969639267", result.referenceNumber)
        assertEquals("UPI", result.paymentMethodHint)
    }

    @Test
    fun testParseIciciAutoDebit() {
        val rawText = """
            Money sent!
            Customername
            RAJADURAIK
            Transaction ID
            Amount
            431209530935
            258.00
            Date
            Time
            7thNov 2024
            09:07AM
            Debited from
            ICICI Bank
            XXXXXXXXXX07
            Remarks
            Auto
            Share
            POWERED BY
            BHIM>
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("258.00"), result.amount)
        assertEquals("RAJADURAIK", result.payeeName)
        assertEquals(LocalDateTime.of(2024, 11, 7, 9, 7), result.dateTime)
        assertEquals("431209530935", result.referenceNumber)
        assertEquals("Auto Debit", result.paymentMethodHint)
        assertEquals("ICICI Bank", result.accountOrBankHint)
    }

    @Test
    fun testParseIciciBillPayTneb() {
        val rawText = """
            iIcICIBank
            Bill payment successful!
            453
            Tamil NaduElectricityBoard(TNEB)
            0921412285,SGANAPATHY
            "Single Phase"
            From G Akash Sriram
            IClClBank·oo.2407Savingsa/c
            via iMobile
            Paidat6:33AM,01Feb'25IST
            B-ConnectTxnID:IC31503200000010GBHN
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("453"), result.amount)
        assertTrue(result.payeeName?.contains("Tamil Nadu", ignoreCase = true) == true)
        assertEquals(LocalDateTime.of(2025, 2, 1, 6, 33), result.dateTime)
        assertEquals("IC31503200000010GBHN", result.referenceNumber)
        assertEquals("Bill Payment", result.paymentMethodHint)
        assertEquals("ICICI Bank", result.accountOrBankHint)
    }

    @Test
    fun testParseIciciBillPayHathway() {
        val rawText = """
            iIcICIBank
            Bill payment successful!
            960.52
            Hathway Broadband
            1184540433,AkashSriram
            “Hathway"
            FromGAkashSriram
            IClClBank·oo.2407Savingsa/c
            via iMobile
            Paidat6:39AM,01Feb'25IST
            B-ConnectTxnID:IC31503200000010GBV6
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("960.52"), result.amount)
        assertEquals("Hathway Broadband", result.payeeName)
        assertEquals(LocalDateTime.of(2025, 2, 1, 6, 39), result.dateTime)
        assertEquals("IC31503200000010GBV6", result.referenceNumber)
        assertEquals("Bill Payment", result.paymentMethodHint)
        assertEquals("ICICI Bank", result.accountOrBankHint)
    }

    @Test
    fun testParseIciciQuickPayGas() {
        val rawText = """
            QUICK PAY SUMMARY
            Transaction Reference Number IC3143060000000XH7UP
            Payment Mode Internet banking
            Biller Name Bharat Gas (BPCL)
            Biller Id BHAR00000NATR4
            Registered Contact Number 8680878355
            Bill Amount INR|818.5
            Convenience Fee INR|0.0
            Total Amount INR|818.5
            Bill Paid Date and Time 01-11-2024
            Transaction Status Success
            Pay From 189801512407
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("818.5"), result.amount)
        assertTrue(result.payeeName?.contains("Bharat Gas", ignoreCase = true) == true)
        assertEquals("IC3143060000000XH7UP", result.referenceNumber)
        assertEquals("Net Banking", result.paymentMethodHint)
    }

    @Test
    fun testParseCanaraBankNeft() {
        val rawText = """
            Canara Bank
            Transaction Successful
            AmountPaid
            1553.00
            TransactionID
            18935288677
            Date&Time
            03-02-26,11:46AM
            From,
            Savings
            NANDHINIDEVI
            XXXXXXXXX5090
            CANARABANK
            To,
            NEFT
            AKASHSRIRAMG
            XXXXXXXX2407
            ICICIBANK
            Remarks
            eb
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("1553.00"), result.amount)
        assertEquals(LocalDateTime.of(2026, 2, 3, 11, 46), result.dateTime)
        assertEquals("18935288677", result.referenceNumber)
        assertEquals("Net Banking", result.paymentMethodHint)
        assertEquals("Canara Bank", result.accountOrBankHint)
    }

    @Test
    fun testParseIciciBillPayGasBpclLogcat() {
        val rawText = """
            cICI Bank
            Transaction Reference Number
            Payment Mode
            Biller Name
            Biller ld
            Registered Contact Number
            Bill Amount
            Convenience Fee
            Total Amount
            |Bill Paid Date and Time
            Transaction Status
            | Pay From
            Enable Auto Bill Pay
            QUICK PAY SUMMARY
            IC3143060000000XH7UP
            Internet banking
            Bharat Gas (BPCL)
            BHARO0000NATR4
            8680878355
            INRJ818.5
            INRỊ0.0
            INR|818.5
            01-11-2024
            Success
            189801512407
            BİLLPAỲ (B)
            BHARAT
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("818.5"), result.amount)
        assertEquals("Bharat Gas (BPCL)", result.payeeName)
        assertEquals("Net Banking", result.paymentMethodHint)
        assertEquals("ICICI Bank", result.accountOrBankHint)
        assertEquals("IC3143060000000XH7UP", result.referenceNumber)
        assertEquals(LocalDate.of(2024, 11, 1), result.dateTime?.toLocalDate())
    }

    @Test
    fun testParseCanaraBankNeftLogcat() {
        val rawText = """
            Amount Paid
            Transaction Successful
            Transaction ID
            18935288677
            7 1553.00
            [স From,
            Savings
            Canara Bank; স
            NEFT
            केनटा बैंक
            NANDHINI DEVI
            XXXXXXXXX5090
            CANARA BANK
            eb
            JCICI BANK
            Remarks
            AKASH SRIRAM G
            XXXXXXXX2407
            ०Expiring soon
            You won a scratch card
            Date & Time
            al
            Send Again
            03-02-26, 11:46 AM
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("1553.00"), result.amount)
        assertEquals("NANDHINI DEVI", result.payeeName)
        assertEquals(LocalDateTime.of(2026, 2, 3, 11, 46), result.dateTime)
        assertEquals("18935288677", result.referenceNumber)
        assertEquals("Net Banking", result.paymentMethodHint)
        assertEquals("Canara Bank", result.accountOrBankHint)
    }

    @Test
    fun testCleanComment() {
        assertEquals("", UpiReceiptParser.cleanComment("UTR: 242169832876 • UPI: paytmqr6tgwxe@ptys"))
        assertEquals("", UpiReceiptParser.cleanComment("UPI: paytmqr6tgwxe@ptys • UTR: 242169832876"))
        assertEquals("", UpiReceiptParser.cleanComment("UTR: 450885942956"))
        assertEquals("", UpiReceiptParser.cleanComment("UPI: pinelabs.stq4526680@pineaxis"))
        assertEquals("Grocery shopping", UpiReceiptParser.cleanComment("Grocery shopping (UTR: 242169832876 • UPI: paytmqr6tgwxe@ptys)"))
        assertEquals("Grocery shopping", UpiReceiptParser.cleanComment("Grocery shopping • UTR: 242169832876"))
        assertEquals("UPI to cash", UpiReceiptParser.cleanComment("UPI to cash"))
        assertEquals("Upi by Devi", UpiReceiptParser.cleanComment("Upi by Devi"))
        assertEquals("50 rs shared as upi refund", UpiReceiptParser.cleanComment("50 rs shared as upi refund"))
        assertEquals("", UpiReceiptParser.cleanComment(null))
        assertEquals("", UpiReceiptParser.cleanComment(""))
    }

    @Test
    fun testParseSamsungWalletWhiskAndSipCafe() {
        val rawText = """
            23 Oct 2026
            10:26:36 pm
            Paid from
            Samsung Wallet
            WHISK AND SIP CAFE
            Vyapar.178345286643@hdfcbank
            HDFC BANK
            23 OCT 2026, 10:26 PM
            730.00
            Sent
            Sender:
            asg98@pingpay
            Sent from:
            ICICI Bank
            Transaction ID:
            948930342966
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("30.00"), result.amount)
        assertEquals("WHISK AND SIP CAFE", result.payeeName)
        assertEquals("Vyapar.178345286643@hdfcbank", result.upiId)
        assertEquals(LocalDateTime.of(2026, 10, 23, 22, 26), result.dateTime)
        assertEquals("948930342966", result.referenceNumber)
        assertEquals("ICICI Bank", result.accountOrBankHint)
        assertTrue(result.isExpense)
    }

    @Test
    fun testCleanPayeePrefixes() {
        assertEquals("WHISK AND SIP CAFE", UpiReceiptParser.cleanPayee("Sender: WHISK AND SIP CAFE"))
        assertEquals("Hunger Box", UpiReceiptParser.cleanPayee("To Hunger Box"))
        assertEquals("SRI LAKSHMI KAAIKANI ANGAADI", UpiReceiptParser.cleanPayee("To SRI LAKSHMI KAAIKANI ANGAADI"))
        assertEquals("Govindhan R", UpiReceiptParser.cleanPayee("To Govindhan R"))
        assertEquals("Akshaya R", UpiReceiptParser.cleanPayee("Paid to Akshaya R"))
        assertEquals("Zomato", UpiReceiptParser.cleanPayee("Payment to Zomato"))
        assertEquals("TNEB", UpiReceiptParser.cleanPayee("Biller Name: TNEB"))
        assertEquals("WHISK AND SIP CAFE", UpiReceiptParser.cleanPayee("WHISK AND SIP CAFE (verified merchant)"))
        assertEquals("WHISK AND SIP CAFE", UpiReceiptParser.cleanPayee("WHISK AND SIP CAFE [verified]"))
        assertEquals("Hunger Box", UpiReceiptParser.cleanPayee("To: Hunger Box"))
        assertEquals("Amazon", UpiReceiptParser.cleanPayee("To  Amazon"))
    }
}
