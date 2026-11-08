package org.totschnig.myexpenses.util.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun testReceiptPaidToUserIsIncome() {
        val rawText = """
            ₹1,000
            Paid to
            MRG AKASH SRIRAM
            8680878355@icici
            24 October 2026, 2:29 pm
            POWERED BY UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("1000"), result.amount)
        assertFalse(result.isExpense)
        assertTrue(result.isIncome)
        assertEquals("MRG AKASH SRIRAM", result.recipientName)
        assertNull(result.payeeName)
        assertEquals("8680878355@icici", result.upiId)
        assertEquals("ICICI Bank", result.accountOrBankHint)
        assertEquals(LocalDateTime.of(2026, 10, 24, 14, 29), result.dateTime)
    }

    @Test
    fun testReceiptPaidToUserWithOcrDigitConfusionRepaired() {
        val rawText = """
            ₹1,000
            Paid to
            MRG AKASH SRIRAM
            4640478355@icici
            24 October 2026, 2:29 pm
            POWERED BY UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("1000"), result.amount)
        assertFalse(result.isExpense)
        assertEquals("8680878355@icici", result.upiId)
    }

    @Test
    fun testReceiptReceivedFromSenderIsIncome() {
        val rawText = """
            ₹2,500
            Received from
            Rahul Sharma
            rahul.sharma@okaxis
            24 October 2026, 11:15 am
            Credited to ICICI Bank
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("2500"), result.amount)
        assertFalse(result.isExpense)
        assertTrue(result.isIncome)
        assertEquals("Rahul Sharma", result.payeeName)
        assertEquals("ICICI Bank", result.accountOrBankHint)
    }

    @Test
    fun testReceiptPaidToMerchantIsExpense() {
        val rawText = """
            ₹350
            Paid to
            Swiggy
            swiggy@icici
            24 October 2026, 8:45 pm
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("350"), result.amount)
        assertTrue(result.isExpense)
        assertEquals("Swiggy", result.payeeName)
    }

    @Test
    fun testIciciPaymentReceiptWithRefNo() {
        val rawText = """
            iIcICIBank
            Paid successfully!
            ₹5,000
            To
            SIVAPRAKASAM R
            To UPI ID
            siva07071972@oksbi
            Ref No.
            429812345678
            24 October 2026, 2:29 pm
            POWERED BY UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("5000"), result.amount)
        assertEquals("SIVAPRAKASAM R", result.payeeName)
        assertEquals("siva07071972@oksbi", result.upiId)
        assertEquals("429812345678", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testIciciPaymentReceiptWithInlineRefNo() {
        val rawText = """
            ICICI Bank
            Paid successfully! ₹5,000
            To SIVAPRAKASAM R
            siva07071972@oksbi
            Ref No. 429812345678
            From ICICI Bank Account XX1234
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("5000"), result.amount)
        assertEquals("SIVAPRAKASAM R", result.payeeName)
        assertEquals("siva07071972@oksbi", result.upiId)
        assertEquals("429812345678", result.referenceNumber)
    }

    @Test
    fun testCategoryXmlArraysConsistency() {
        val baseDir = java.io.File("src/main/res/values")
        val catTreeFile = java.io.File(baseDir, "cat_tree.xml")
        val iconsFile = java.io.File(baseDir, "icons.xml")
        val uuidsFile = java.io.File(baseDir, "category_uuids.xml")

        assertTrue("cat_tree.xml exists", catTreeFile.exists())
        assertTrue("icons.xml exists", iconsFile.exists())
        assertTrue("category_uuids.xml exists", uuidsFile.exists())

        fun parseStringArrayCounts(file: java.io.File): Map<String, Int> {
            val docBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = docBuilder.parse(file)
            val stringArrays = doc.getElementsByTagName("string-array")
            val result = mutableMapOf<String, Int>()

            for (i in 0 until stringArrays.length) {
                val node = stringArrays.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                var itemCount = 0
                val children = node.childNodes
                for (j in 0 until children.length) {
                    if (children.item(j).nodeName == "item") {
                        itemCount++
                    }
                }
                result[name] = itemCount
            }
            return result
        }

        val catTreeCounts = parseStringArrayCounts(catTreeFile)
        val iconsCounts = parseStringArrayCounts(iconsFile)
        val uuidsCounts = parseStringArrayCounts(uuidsFile)

        for (i in 1..7) {
            val catKey = "Cat_I_$i"
            val iconKey = "${catKey}_Icons"
            val uuidKey = "${catKey}_Uuids"

            assertTrue("cat_tree contains $catKey", catTreeCounts.containsKey(catKey))
            assertTrue("icons contains $iconKey", iconsCounts.containsKey(iconKey))
            assertTrue("uuids contains $uuidKey", uuidsCounts.containsKey(uuidKey))

            val catCount = catTreeCounts[catKey]!!
            val iconCount = iconsCounts[iconKey]!!
            val uuidCount = uuidsCounts[uuidKey]!!

            assertEquals("Mismatch between $catKey and $iconKey", catCount, iconCount)
            assertEquals("Mismatch between $catKey and $uuidKey", catCount, uuidCount)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // POCO F1 receipt regression tests (Nov 2026 testing session)
    // ──────────────────────────────────────────────────────────────

    /**
     * Samsung Wallet / Zepto receipt.
     * Bug 1: Payee was parsed as "Z155.00" (OCR misread ₹ as Z).
     * Bug 2: UPI ID picked was sender asg98@pingpay instead of cf.zepto12@cashfreensdlpb.
     */
    @Test
    fun testSamsungWalletZeptoReceipt() {
        val rawText = """
            Paid from
            Samsung Wallet
            Zepto
            cf.zepto12@cashfreensdlpb
            01 Nov 2026, 03:52 PM
            Z155.00
            Sent
            Sender:
            asg98@pingpay
            Transaction ID: 428654321098
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        // Payee should be Zepto, not "Z155.00"
        assertEquals("Zepto", result.payeeName)
        // Amount should be 155.00
        assertEquals(BigDecimal("155.00"), result.amount)
        // UPI ID should be payee's VPA, not sender's
        assertEquals("cf.zepto12@cashfreensdlpb", result.upiId)
        assertEquals("428654321098", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    /**
     * ICICI / Optival Health receipt.
     * Bug: "Tr. ID:" label wasn't recognized so UTR was empty.
     */
    @Test
    fun testIciciOptivalHealthTrIdLabel() {
        val rawText = """
            iMobile Pay
            Payment Successful
            OPTIVAL HEALTH SOLUT
            Tr. ID: 111057198606
            Amount
            ₹3,260.10
            01 Nov 2026, 11:47 AM
            Sent from: 8680878355@icici
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("OPTIVAL HEALTH SOLUT", result.payeeName)
        assertEquals(BigDecimal("3260.10"), result.amount)
        assertEquals("111057198606", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    /**
     * ICICI / Hathway Broadband receipt.
     * Bug: "IC31630500000022VYFE" truncated to "IC3163050000" because OCR split it.
     */
    @Test
    fun testIciciHathwayOcrSplitUtr() {
        val rawText = """
            iMobile Pay
            Bill Payment Successful!
            Hathway Broadband
            Amount
            ₹960.52
            Ref No.
            IC3163050000 0022VYFE
            01 Nov 2026, 01:30 PM
            Sent from: 8680878355@icici
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("Hathway Broadband", result.payeeName)
        assertEquals(BigDecimal("960.52"), result.amount)
        assertEquals("IC31630500000022VYFE", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    /**
     * ICICI / Bharat Gas receipt.
     * Bug: OCR misread gas cylinder icon as "Ö " — payee was "Ö Bharat Gas (BPCL)".
     */
    @Test
    fun testIciciBharatGasLeadingOcrNoise() {
        val rawText = """
            iMobile Pay
            Bill Payment Successful!
            Ö Bharat Gas (BPCL)
            Amount
            ₹957.50
            Ref No.
            IC31825000000123ABCD
            01 Nov 2026, 02:00 PM
            Sent from: 8680878355@icici
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("Bharat Gas (BPCL)", result.payeeName)
        assertEquals(BigDecimal("957.50"), result.amount)
        assertEquals("IC31825000000123ABCD", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    /**
     * cleanPayee: verify OCR noise stripping.
     */
    @Test
    fun testCleanPayeeOcrNoise() {
        assertEquals("Bharat Gas (BPCL)", UpiReceiptParser.cleanPayee("Ö Bharat Gas (BPCL)"))
        assertEquals("Hathway Broadband", UpiReceiptParser.cleanPayee("© Hathway Broadband"))
        assertEquals("Bharat Gas (BPCL)", UpiReceiptParser.cleanPayee("Bharat Gas (BPCL)"))
        // Bengali checkmark noise from ML Kit: "Zepto ৩" -> "Zepto"
        assertEquals("Zepto", UpiReceiptParser.cleanPayee("Zepto ৩"))
        assertEquals("Zepto", UpiReceiptParser.cleanPayee("Zepto ✔"))
    }

    @Test
    fun testSamsungWalletZeptoCheckmarkIndicDigitNoise() {
        val rawText = """
            Paid from
            Samsung Wallet
            Zepto ৩
            cf.zepto12@cashfreensdlpb
            01 NOV 2026, 09:38 AM
            ₹155.00
            Sent
            Sender:
            asg98@pingpay
            Sent from:
            ICICI Bank
            Transaction ID:
            294430763056
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("Zepto", result.payeeName)
        assertEquals(BigDecimal("155.00"), result.amount)
        assertEquals("cf.zepto12@cashfreensdlpb", result.upiId)
        assertEquals("294430763056", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testSamsungWalletSenderVpaNeverUsedAsPayeeVpa() {
        // If merchant VPA is missing or unreadable, sender VPA (asg98@pingpay) must NOT be returned as upiId!
        val rawText = """
            Paid from
            Samsung Wallet
            Zepto
            NSDL Payments Bank
            01 NOV 2026, 09:38 AM
            ₹155.00
            Sent
            Sender:
            asg98@pingpay
            Transaction ID:
            294430763056
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("Zepto", result.payeeName)
        assertEquals(BigDecimal("155.00"), result.amount)
        assertNull("Sender VPA must never be returned as payee VPA", result.upiId)
        assertEquals("294430763056", result.referenceNumber)
    }

    /**
     * Exact raw ML Kit OCR output from POCO F1 on Zepto Samsung Wallet receipt (Nov 2, 2026).
     */
    @Test
    fun testSamsungWalletPocoF1ActualMlKitLogcatScan() {
        val rawText = """
            Sender:
            Paid from
            Samsung Wallet
            Sent from:
            cf.zepto12@cashfreernsdlpb
            NSDL Payments Bank
            Zepto ৩
            ০1 NOV 2026, 09:38 AM
            Z155.00
            Transaction ID:
            Sent
            asg98 @pingpay
            ICICI Bank
            294430763056
            Powered by AXIS BANK
            UPI»
            | UNIFED PAYENTIS INাEAEACE
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertEquals("Zepto", result.payeeName)
        assertEquals(BigDecimal("155.00"), result.amount)
        assertEquals("cf.zepto12@cashfreensdlpb", result.upiId)
        assertEquals("294430763056", result.referenceNumber)
        assertTrue(result.accountOrBankHint?.contains("ICICI", ignoreCase = true) == true || result.accountOrBankHint?.contains("Samsung", ignoreCase = true) == true)
        assertTrue(result.isExpense)
    }

    @Test
    fun testIciciPotluckReceipt() {
        val rawText = """
            i ICICI Bank
            Paid successfully!
            ₹ 290
            for potluck
            To TAMILNADU MUTTON AND
            44117073@idfcbank
            From G AKASH SRIRAM
            8680878355@icici
            Paid at 01:11PM, 02 Nov '26 IST
            Tr. ID: 111065040686
            POWERED BY UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)
        assertEquals(BigDecimal("290"), result.amount)
        assertEquals("TAMILNADU MUTTON", result.payeeName)
        assertEquals("for potluck", result.comment)
        assertEquals("88117073@idfcbank", result.upiId)
        assertEquals(LocalDateTime.of(2026, 11, 2, 13, 11), result.dateTime)
        assertEquals("111065040686", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testIciciPotluckReceiptWithLetterOInTime() {
        val rawText = """
            i ICICI Bank
            Paid successfully!
            ₹ 290
            for potluck
            To TAMILNADU MUTTON AND
            44117073@idfcbank
            From G AKASH SRIRAM
            8680878355@icici
            Paid at O1:11PM, 02 Nov '26 IST
            Tr. ID: 111065040686
            POWERED BY UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)
        assertEquals(BigDecimal("290"), result.amount)
        assertEquals("TAMILNADU MUTTON", result.payeeName)
        assertEquals("for potluck", result.comment)
        assertEquals("88117073@idfcbank", result.upiId)
        assertEquals(LocalDateTime.of(2026, 11, 2, 13, 11), result.dateTime)
        assertEquals("111065040686", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testExtractCommentsLabeledAndDefaultFiltering() {
        // Labeled comment
        val textWithRemarks = """
            Amount: ₹ 500
            Paid to Supermarket
            Remarks: Diwali sweets
            01 Nov 2026, 10:00 AM
        """.trimIndent()
        val res1 = UpiReceiptParser.parse(textWithRemarks)
        assertEquals("Diwali sweets", res1.comment)

        // Default bank placeholder ignored
        val textWithDefaultRemark = """
            Amount: ₹ 500
            Paid to Supermarket
            Remarks: UPI Payment
            01 Nov 2026, 10:00 AM
        """.trimIndent()
        val res2 = UpiReceiptParser.parse(textWithDefaultRemark)
        assertNull(res2.comment)

        // Notes duplicating payee ignored
        val textWithPayeeNotes = """
            Amount: ₹ 500
            Paid to Swiggy
            Notes: Swiggy
            01 Nov 2026, 10:00 AM
        """.trimIndent()
        val res3 = UpiReceiptParser.parse(textWithPayeeNotes)
        assertNull(res3.comment)
    }

    @Test
    fun testGeethamOmrSamsungWalletReceipt() {
        val rawText = """
            Paid from
            Samsung Wallet
            
            GEETHAM OMR
            geethamomr@icici
            08 NOV 2026, 08:17 PM
            
            ₹441.00
            Sent
            
            Sent from: asg98@pingpay
            Transaction ID: 341082813126
            Notes: GEETHAM OMR
            
            Powered by AXIS BANK | UPI
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("441.00"), result.amount)
        assertEquals("GEETHAM OMR", result.payeeName)
        assertEquals("geethamomr@icici", result.upiId)
        assertEquals(LocalDateTime.of(2026, 11, 8, 20, 17), result.dateTime)
        assertEquals("341082813126", result.referenceNumber)
        assertTrue(result.isExpense)
    }

    @Test
    fun testGeethamOmrSamsungWalletReceiptAttachedSentAndNoise() {
        val rawText = """
            GEETHAM OMR
            geethamomr@icici
            08 n0v 2026, 8:17 pm
            441.00 Sent
            Transaction ID: 341082813126
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("441.00"), result.amount)
        assertEquals("GEETHAM OMR", result.payeeName)
        assertEquals(LocalDateTime.of(2026, 11, 8, 20, 17), result.dateTime)
        assertEquals("341082813126", result.referenceNumber)
    }

    @Test
    fun testOcrMisreadRupeeSymbolAndLetterOInDecimals() {
        val rawText = """
            Paid to GEETHAM OMR
            रु 441.OO
            Completed
            08 NOV 2026, 20:17
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("441.00"), result.amount)
        assertEquals("GEETHAM OMR", result.payeeName)
        assertEquals(LocalDateTime.of(2026, 11, 8, 20, 17), result.dateTime)
    }

    @Test
    fun testDevanagariDigitsNormalization() {
        val rawText = """
            GEETHAM OMR
            ₹४४१.००
            Sent
            ०८ NOV २०२६, ०८:१७ PM
            Transaction ID: ३४१०८२८१३१२६
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("441.00"), result.amount)
        assertEquals(LocalDateTime.of(2026, 11, 8, 20, 17), result.dateTime)
        assertEquals("341082813126", result.referenceNumber)
    }

    @Test
    fun testExactPocoF1SamsungWalletGeethamOmrLogcat() {
        val rawText = """
            Sender
            Paid from
            Samsung Wallet
            GEETHAM OMR
            Sent from:
            Q337112731@ybl
            Yes Bank
            ০৪ NOV 2026, 08:17 PM
            T441.00
            Transaction ID:
            Sent
            asg98 @pingpay
            ICICI Bank
            341082813126
            Powered by AXIS BANK
            UPI»
            | UNIFED PAYENTIS INাEAEACE
        """.trimIndent()

        val result = UpiReceiptParser.parse(rawText)

        assertNotNull(result)
        assertEquals(BigDecimal("441.00"), result.amount)
        assertEquals("GEETHAM OMR", result.payeeName)
        assertEquals("Q337112731@ybl", result.upiId)
        assertEquals(LocalDateTime.of(2026, 11, 8, 20, 17), result.dateTime)
        assertEquals("341082813126", result.referenceNumber)
        assertTrue(result.isExpense)
    }
}


