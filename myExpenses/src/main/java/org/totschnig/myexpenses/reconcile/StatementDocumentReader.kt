package org.totschnig.myexpenses.reconcile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

sealed class DocumentReadResult {
    data class Success(
        val fileName: String,
        val transactions: List<StatementTransaction>,
        val pageCount: Int?,
        val rawText: String
    ) : DocumentReadResult()

    data class PasswordRequired(
        val fileName: String,
        val uri: Uri,
        val errorMessage: String? = null
    ) : DocumentReadResult()

    data class Error(
        val message: String
    ) : DocumentReadResult()
}

object StatementDocumentReader {

    private val isInitialized = AtomicBoolean(false)

    private fun ensurePdfBox(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (e: Exception) {
                Timber.w(e, "PDFBoxResourceLoader initialization failed")
            }
        }
    }

    fun readDocument(
        context: Context,
        uri: Uri,
        password: String? = null
    ): DocumentReadResult {
        val fileName = getFileName(context, uri) ?: "statement"
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val isPdf = fileName.endsWith(".pdf", ignoreCase = true) || mimeType.equals("application/pdf", ignoreCase = true)

        return if (isPdf) {
            readPdf(context, uri, fileName, password)
        } else {
            readTextOrCsv(context, uri, fileName)
        }
    }

    private fun readPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        password: String?
    ): DocumentReadResult {
        ensurePdfBox(context)
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return DocumentReadResult.Error("Unable to open statement file: $fileName")

            val document = try {
                if (password != null) {
                    PDDocument.load(stream, password)
                } else {
                    PDDocument.load(stream)
                }
            } catch (e: InvalidPasswordException) {
                return DocumentReadResult.PasswordRequired(
                    fileName = fileName,
                    uri = uri,
                    errorMessage = if (password != null) "Incorrect password. Please try again." else null
                )
            } catch (e: Exception) {
                if (e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("encrypted", ignoreCase = true) == true
                ) {
                    return DocumentReadResult.PasswordRequired(
                        fileName = fileName,
                        uri = uri,
                        errorMessage = if (password != null) "Incorrect password. Please try again." else null
                    )
                }
                throw e
            }

            document.use { doc ->
                if (doc.isEncrypted && !doc.isAllSecurityToBeRemoved) {
                    // Try to decrypt with empty or provided password if not already decrypted
                    try {
                        doc.setAllSecurityToBeRemoved(true)
                    } catch (_: Exception) {}
                }

                val pageCount = doc.numberOfPages
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val fullText = stripper.getText(doc)

                val transactions = BankStatementParser.parse(fullText)
                if (transactions.isEmpty()) {
                    DocumentReadResult.Error(
                        "Parsed $pageCount pages from $fileName, but could not detect standard bank statement transactions. Please verify the format."
                    )
                } else {
                    DocumentReadResult.Success(
                        fileName = fileName,
                        transactions = transactions,
                        pageCount = pageCount,
                        rawText = fullText
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading PDF statement: %s", fileName)
            DocumentReadResult.Error("Failed to read PDF statement: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun readTextOrCsv(
        context: Context,
        uri: Uri,
        fileName: String
    ): DocumentReadResult {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return DocumentReadResult.Error("Unable to open statement file: $fileName")

            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val fullText = reader.use { it.readText() }

            val transactions = BankStatementParser.parse(fullText)
            if (transactions.isEmpty()) {
                DocumentReadResult.Error(
                    "Read file $fileName, but could not identify tabular transactions. Please check if it is a valid bank statement CSV."
                )
            } else {
                DocumentReadResult.Success(
                    fileName = fileName,
                    transactions = transactions,
                    pageCount = null,
                    rawText = fullText
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading text/CSV statement: %s", fileName)
            DocumentReadResult.Error("Failed to read CSV statement: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }
}
