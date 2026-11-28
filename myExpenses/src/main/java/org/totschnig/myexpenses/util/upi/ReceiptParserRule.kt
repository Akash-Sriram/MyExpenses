package org.totschnig.myexpenses.util.upi

interface ReceiptParserRule {
    val ruleName: String
    fun matches(fullText: String, lines: List<String>): Boolean
    fun parse(
        lines: List<String>,
        fullText: String,
        effectiveUserNames: List<String>,
        effectiveUserPhones: List<String>
    ): UpiReceiptData?
}
