package org.totschnig.myexpenses.util.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class UpiQrParserTest {

    @Test
    fun parseSimpleUpiQr() {
        val qr = "upi://pay?pa=merchant@okaxis&pn=Chai%20Point&am=45.50&cu=INR&tn=Evening%20Snack"
        assertTrue(UpiQrParser.isUpiQr(qr))

        val data = UpiQrParser.parse(qr)
        assertNotNull(data)
        assertEquals("merchant@okaxis", data?.vpa)
        assertEquals("Chai Point", data?.payeeName)
        assertEquals(BigDecimal("45.50"), data?.amount)
        assertEquals("INR", data?.currency)
        assertEquals("Evening Snack", data?.transactionNote)
        assertTrue(data?.isValid == true)

        val cleanUri = data?.toCleanUri()
        assertNotNull(cleanUri)
        assertEquals("upi", cleanUri?.scheme)
        assertEquals("pay", cleanUri?.authority)
        assertEquals("merchant@okaxis", cleanUri?.getQueryParameter("pa"))
        assertEquals("Chai Point", cleanUri?.getQueryParameter("pn"))
        assertEquals("45.50", cleanUri?.getQueryParameter("am"))
    }

    @Test
    fun parseComplexGatewayQrWithTrackingBloatAndSignatures() {
        // Simulating high-density dynamic QR codes from gateways like Razorpay/Cashfree/IRCTC
        val complexQr = "upi://pay?pa=irctc@hdfcbank&pn=IRCTC%20Official&am=1250.00&cu=INR" +
                "&tr=TXN987654321098&tid=IRCTC12345&mc=4112&tn=Ticket%20Booking" +
                "&url=https%3A%2F%2Fpay.irctc.co.in%2Fcallback%3Fid%3D9999" +
                "&sign=MEQCIB2Q9xLp4Z1o8Vw2A3B4C5D6E7F8G9H0J1K2L3M4N5O6AiA7B8C9D0E1F2G3H4I5J6K7L8M9N0O1P2Q3R4S5T6%3D%3D" +
                "&utm_source=gateway&session_id=sess_abcdef123456"

        assertTrue(UpiQrParser.isUpiQr(complexQr))

        val data = UpiQrParser.parse(complexQr)
        assertNotNull(data)
        assertEquals("irctc@hdfcbank", data?.vpa)
        assertEquals("IRCTC Official", data?.payeeName)
        assertEquals(BigDecimal("1250.00"), data?.amount)
        assertEquals("TXN987654321098", data?.transactionRef)
        assertEquals("4112", data?.merchantCode)

        // Verify clean URI strips bloated parameters (utm_source, session_id, sign, etc.)
        val cleanUri = data?.toCleanUri()
        assertNotNull(cleanUri)
        assertEquals("irctc@hdfcbank", cleanUri?.getQueryParameter("pa"))
        assertEquals("IRCTC Official", cleanUri?.getQueryParameter("pn"))
        assertEquals("1250.00", cleanUri?.getQueryParameter("am"))
        assertEquals("TXN987654321098", cleanUri?.getQueryParameter("tr"))
        assertNull(cleanUri?.getQueryParameter("sign"))
        assertNull(cleanUri?.getQueryParameter("utm_source"))
    }

    @Test
    fun parseQrWithoutAmount() {
        // Static merchant QR code where customer enters amount
        val qr = "upi://pay?pa=kirana@upi&pn=Gupta%20Store"
        val data = UpiQrParser.parse(qr)
        assertNotNull(data)
        assertEquals("kirana@upi", data?.vpa)
        assertEquals("Gupta Store", data?.payeeName)
        assertNull(data?.amount)

        val cleanUri = data?.toCleanUri()
        assertNotNull(cleanUri)
        assertNull(cleanUri?.getQueryParameter("am"))
    }

    @Test
    fun parseP2pQrStripsFakeMerchantCodeAndTr() {
        val p2pQr = "upi://pay?pa=ganapathy@okhdfcbank&pn=Ganapathy&mc=0000&mode=02&tr=FAKE123"
        val data = UpiQrParser.parse(p2pQr)
        assertNotNull(data)
        assertEquals("0000", data?.merchantCode)

        val cleanUri = data?.toCleanUri()
        assertNotNull(cleanUri)
        assertNull(cleanUri?.getQueryParameter("mc"))
        assertNull(cleanUri?.getQueryParameter("tr"))
    }

    @Test
    fun parseInvalidQr() {
        assertFalse(UpiQrParser.isUpiQr("https://example.com/not-upi"))
        assertNull(UpiQrParser.parse("https://example.com/not-upi"))

        // Missing '@' in VPA
        val invalidVpaQr = "upi://pay?pa=notanemailorvpa&pn=Test"
        assertNull(UpiQrParser.parse(invalidVpaQr))
    }
}
