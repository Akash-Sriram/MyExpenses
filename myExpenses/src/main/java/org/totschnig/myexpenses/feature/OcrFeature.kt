package org.totschnig.myexpenses.feature

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.time.LocalDate
import java.time.LocalTime

interface OcrFeature {
    companion object {
        const val ACTION = "org.totschnig.ocr.action.RECOGNIZE"
        const val MIME_TYPE = "image/jpeg"
    }
}

@Parcelize
data class OcrResult(val amountCandidates: List<String>, val dateCandidates: List<Pair<LocalDate, LocalTime?>>, val payeeCandidates: List<Payee>): Parcelable {
    fun needsDisambiguation() = amountCandidates.size > 1 || dateCandidates.size > 1 || payeeCandidates.size > 1
    fun selectCandidates(amountIndex: Int = 0, dateIndex: Int = 0, payeeIndex: Int = 0) =
            OcrResultFlat(amountCandidates.getOrNull(amountIndex), dateCandidates.getOrNull(dateIndex), payeeCandidates.getOrNull(payeeIndex))
}

@Parcelize
data class OcrResultFlat(val amount: String?, val date: Pair<LocalDate, LocalTime?>?, val payee: Payee?): Parcelable {
    @IgnoredOnParcel
    val isEmpty = amount == null && date == null && payee == null
}

@Parcelize
data class Payee(val id: Long, val name: String): Parcelable

interface OcrHost {
    fun processOcrResult(result: Result<OcrResult>, scanUri: Uri)
}
