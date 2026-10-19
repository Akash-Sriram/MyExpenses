package org.totschnig.myexpenses.test.feature

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.feature.OcrResult
import org.totschnig.myexpenses.feature.Payee
import java.time.LocalDate
import java.time.LocalTime
import org.totschnig.myexpenses.feature.OcrResultFlat

class OcrResultTest {

    @Test
    fun parcelizeOcrResult() {
        val ocrResult = OcrResult(listOf("17.34"), listOf(LocalDate.now() to LocalTime.now()), listOf(Payee(1, "John Doe")))
        val serializedBytes = Parcel.obtain().run {
            writeParcelable(ocrResult, 0)
            marshall()
        }
        val result = Parcel.obtain().run {
            unmarshall(serializedBytes, 0, serializedBytes.size)
            setDataPosition(0)
            androidx.core.os.ParcelCompat.readParcelable(this, OcrResult::class.java.classLoader, OcrResult::class.java)
        }
        assertEquals(ocrResult, result)
    }

    @Test
    fun parcelizeOcrResultFlat() {
        val ocrResult = OcrResultFlat("17.34", LocalDate.now() to LocalTime.now(), Payee(1, "John Doe"))
        val serializedBytes = Parcel.obtain().run {
            writeParcelable(ocrResult, 0)
            marshall()
        }
        val result = Parcel.obtain().run {
            unmarshall(serializedBytes, 0, serializedBytes.size)
            setDataPosition(0)
            androidx.core.os.ParcelCompat.readParcelable(this, OcrResultFlat::class.java.classLoader, OcrResultFlat::class.java)
        }
        assertEquals(ocrResult, result)
    }
}