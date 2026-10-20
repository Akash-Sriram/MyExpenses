package org.totschnig.myexpenses.util.csv

import java.io.BufferedReader
import java.io.Reader

class CSVRecord(
    val values: List<String>,
    val headerMap: Map<String, Int>? = null,
    val parser: CSVParserResult = CSVParserResult(emptyList()),
    val recordNumber: Long = 0,
) : Iterable<String> {

    val size: Int get() = values.size
    fun size(): Int = values.size

    operator fun get(index: Int): String = values.getOrElse(index) { "" }

    fun get(name: String): String {
        val index = headerMap?.get(name)
            ?: throw IllegalArgumentException("Mapping for '$name' not found. Available headers: ${headerMap?.keys}")
        return values.getOrElse(index) { "" }
    }

    fun isMapped(name: String): Boolean = headerMap?.containsKey(name) == true

    fun isSet(index: Int): Boolean = index in 0 until values.size

    override fun iterator(): Iterator<String> = values.iterator()

    fun toList(): List<String> = values
}

class CSVParserResult(
    val records: List<CSVRecord>,
    var headerNames: List<String> = emptyList(),
    var currentLineNumber: Long = 0,
) : Iterable<CSVRecord> {
    override fun iterator(): Iterator<CSVRecord> = records.iterator()
}

class CSVFormat(
    val delimiter: Char = ',',
    val quote: Char = '"',
    val ignoreSurroundingSpaces: Boolean = false,
    val hasHeader: Boolean = false,
    val skipHeaderRecord: Boolean = false,
) {
    fun builder(): Builder = Builder(
        delimiter = delimiter,
        quote = quote,
        ignoreSurroundingSpaces = ignoreSurroundingSpaces,
        hasHeader = hasHeader,
        skipHeaderRecord = skipHeaderRecord
    )

    fun parse(reader: Reader): CSVParserResult = CSVParser.parse(reader, this)

    class Builder(
        private var delimiter: Char = ',',
        private var quote: Char = '"',
        private var ignoreSurroundingSpaces: Boolean = false,
        private var hasHeader: Boolean = false,
        private var skipHeaderRecord: Boolean = false,
    ) {
        fun setDelimiter(delimiter: Char) = apply { this.delimiter = delimiter }
        fun setQuote(quote: Char) = apply { this.quote = quote }
        fun setIgnoreSurroundingSpaces(ignore: Boolean) = apply { this.ignoreSurroundingSpaces = ignore }
        fun setHeader(vararg headers: String) = apply { this.hasHeader = true }
        fun setSkipHeaderRecord(skip: Boolean) = apply { this.skipHeaderRecord = skip }
        fun get(): CSVFormat = build()
        fun build(): CSVFormat = CSVFormat(
            delimiter = delimiter,
            quote = quote,
            ignoreSurroundingSpaces = ignoreSurroundingSpaces,
            hasHeader = hasHeader,
            skipHeaderRecord = skipHeaderRecord,
        )
    }

    companion object {
        val DEFAULT = CSVFormat()
    }
}

object CSVParser {
    fun parse(reader: Reader, format: CSVFormat = CSVFormat.DEFAULT): CSVParserResult {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val records = mutableListOf<CSVRecord>()
        var headerNames: List<String> = emptyList()
        var headerMap: Map<String, Int>? = null
        var currentLineNumber: Long = 1
        var recordNumber: Long = 0

        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var hasQuotes = false

        val resultHolder = CSVParserResult(
            records = records,
            headerNames = headerNames,
            currentLineNumber = currentLineNumber
        )

        fun finishField() {
            var value = currentField.toString()
            if (format.ignoreSurroundingSpaces && !hasQuotes) {
                value = value.trim()
            }
            currentRecord.add(value)
            currentField.clear()
            hasQuotes = false
        }

        fun finishRecord() {
            finishField()
            if (currentRecord.size == 1 && currentRecord[0].isEmpty() && !hasQuotes) {
                currentRecord.clear()
                return
            }
            val recordValues = currentRecord.toList()
            currentRecord.clear()

            if (format.hasHeader && headerMap == null) {
                headerNames = recordValues.map { if (format.ignoreSurroundingSpaces) it.trim() else it }
                resultHolder.headerNames = headerNames
                val map = mutableMapOf<String, Int>()
                headerNames.forEachIndexed { index, name ->
                    map.putIfAbsent(name, index)
                }
                headerMap = map
                if (!format.skipHeaderRecord) {
                    recordNumber++
                    records.add(CSVRecord(recordValues, headerMap, resultHolder, recordNumber))
                }
            } else {
                recordNumber++
                records.add(CSVRecord(recordValues, headerMap, resultHolder, recordNumber))
            }
        }

        var ch = bufferedReader.read()
        while (ch != -1) {
            val c = ch.toChar()
            if (inQuotes) {
                if (c == format.quote) {
                    val next = bufferedReader.read()
                    if (next != -1 && next.toChar() == format.quote) {
                        currentField.append(format.quote)
                    } else {
                        inQuotes = false
                        ch = next
                        continue
                    }
                } else {
                    if (c == '\r') {
                        currentLineNumber++
                        resultHolder.currentLineNumber = currentLineNumber
                        val next = bufferedReader.read()
                        if (next != -1 && next.toChar() == '\n') {
                            currentField.append('\n')
                        } else {
                            currentField.append('\r')
                            ch = next
                            continue
                        }
                    } else if (c == '\n') {
                        currentLineNumber++
                        resultHolder.currentLineNumber = currentLineNumber
                        currentField.append('\n')
                    } else {
                        currentField.append(c)
                    }
                }
            } else {
                when (c) {
                    format.quote -> {
                        if (format.ignoreSurroundingSpaces && currentField.isBlank()) {
                            currentField.clear()
                        }
                        hasQuotes = true
                        inQuotes = true
                    }
                    format.delimiter -> {
                        finishField()
                    }
                    '\r' -> {
                        currentLineNumber++
                        resultHolder.currentLineNumber = currentLineNumber
                        val next = bufferedReader.read()
                        if (next != -1 && next.toChar() == '\n') {
                            // CRLF
                        } else {
                            ch = next
                            finishRecord()
                            continue
                        }
                        finishRecord()
                    }
                    '\n' -> {
                        currentLineNumber++
                        resultHolder.currentLineNumber = currentLineNumber
                        finishRecord()
                    }
                    ' ', '\t' -> {
                        if (!(hasQuotes && !inQuotes && format.ignoreSurroundingSpaces)) {
                            currentField.append(c)
                        }
                    }
                    else -> {
                        currentField.append(c)
                    }
                }
            }
            ch = bufferedReader.read()
        }

        if (currentField.isNotEmpty() || currentRecord.isNotEmpty() || hasQuotes) {
            finishRecord()
        }

        resultHolder.headerNames = headerNames
        resultHolder.currentLineNumber = currentLineNumber
        return resultHolder
    }
}
