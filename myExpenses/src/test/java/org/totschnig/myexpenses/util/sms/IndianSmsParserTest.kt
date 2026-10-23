package org.totschnig.myexpenses.util.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IndianSmsParserTest {

    @Test
    fun testHdfcDebitSms() {
        val sms = "Sent Rs.450.00 from HDFC Bank A/C *1234 to VPA Swiggy@icici on 22-10-24. Ref 429618192021. Not you? Call 1800..."
        val parsed = IndianSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amountRupees, 0.001)
        assertEquals("HDFC", parsed.bankHint)
        assertEquals("Swiggy@icici", parsed.upiId)
        assertEquals("Swiggy", parsed.payee)
        assertEquals("429618192021", parsed.utrNumber)
        assertEquals("1234", parsed.accountDigits)
    }

    @Test
    fun testIciciDebitSms() {
        val sms = "ICICI Bank: Dear Customer, Acct XX567 debited with INR 350.50 on 22-Oct-24. Info: UPI*9876543210@paytm*Zomato. Available Bal INR 12,345.00."
        val parsed = IndianSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(350.50, parsed!!.amountRupees, 0.001)
        assertEquals("ICICI", parsed.bankHint)
        assertEquals("9876543210@paytm", parsed.upiId)
        assertEquals("Zomato", parsed.payee)
        assertEquals("567", parsed.accountDigits)
    }

    @Test
    fun testSenderMatching() {
        val sms = "Dear Customer, Acct XX567 debited with INR 100.00 on 22-Oct-24. Info: UPI*chai@upi."
        val parsed = IndianSmsParser.parse(sms, sender = "VK-ICICIB")
        assertNotNull(parsed)
        assertEquals("ICICI", parsed!!.bankHint)
    }

    @Test
    fun testSbiDebitSms() {
        val sms = "Dear UPI user A/C 9876 debited by 180.0 on 22Oct24 transfer to Ramesh Kumar Ref No 429618192025."
        val parsed = IndianSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(180.0, parsed!!.amountRupees, 0.001)
        assertEquals("Ramesh Kumar", parsed.payee)
        assertEquals("429618192025", parsed.utrNumber)
        assertEquals("9876", parsed.accountDigits)
    }

    @Test
    fun testAxisDebitSms() {
        val sms = "INR 750.00 debited from Axis Bank A/C no. XX4321 on 22-10-2024 towards UPI/P2M/429618192027/BLINKIT."
        val parsed = IndianSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(750.0, parsed!!.amountRupees, 0.001)
        assertEquals("AXIS", parsed.bankHint)
        assertEquals("BLINKIT", parsed.payee)
        assertEquals("429618192027", parsed.utrNumber)
        assertEquals("4321", parsed.accountDigits)
    }

    @Test
    fun testCreditSmsIgnored() {
        val sms = "Dear Customer, your A/C 1234 has been credited with INR 5,000.00 on 22-10-24 by transfer. Available Bal INR 25,000.00."
        val parsed = IndianSmsParser.parse(sms)
        assertNull(parsed)
    }
}
