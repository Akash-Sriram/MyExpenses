package org.totschnig.myexpenses.util

import java.util.regex.Pattern

object PdfHelper {
    private val HAS_ANY_RTL_RE: Pattern = Pattern.compile(".*[\\p{InArabic}\\p{InHebrew}].*")

    @JvmStatic
    fun hasAnyRtl(str: String): Boolean {
        return HAS_ANY_RTL_RE.matcher(str).matches()
    }
}
