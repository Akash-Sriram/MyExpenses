package org.totschnig.myexpenses.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.AppDirHelper.cacheDir
import org.totschnig.myexpenses.util.AppDirHelper.getContentUriForFile
import org.totschnig.myexpenses.util.AppDirHelper.getFileProviderAuthority
import org.totschnig.myexpenses.util.AppDirHelper.isMyExpensesAuthority
import org.totschnig.myexpenses.util.crashreporting.CrashHandler.Companion.report
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PictureDirHelper {
    fun getOutputMediaFile(
        fileName: String,
        temp: Boolean,
        checkUnique: Boolean,
        application: MyApplication,
        extension: String = "jpg"
    ) = getOutputMediaFile(
        fileName,
        temp,
        application,
        checkUnique,
        extension
    )

    /**
     * create a File object for storage of picture data
     *
     * @param temp if true the returned file is suitable for temporary storage while
     * the user is editing the transaction if false the file will serve
     * as permanent storage,
     * care is taken that the file does not yet exist
     * @return a file on the external storage
     */
    private fun getOutputMediaFile(
        fileName: String,
        temp: Boolean,
        application: MyApplication,
        checkUnique: Boolean,
        extension: String? = "jpg"
    ) = getOutputMediaFile(
        fileName = fileName,
        mediaStorageDir = (
                if (temp) cacheDir(application) else
                    getPictureDir(application, application.isProtected)
                ),
        checkUnique = checkUnique,
        extension = extension
    )

    private fun getOutputMediaFile(
        fileName: String,
        mediaStorageDir: File,
        checkUnique: Boolean,
        extension: String?
    ): File {
        var postfix = 0
        var result: File
        do {
            result = File(mediaStorageDir, getOutputMediaFileName(fileName, postfix, extension))
            postfix++
        } while (checkUnique && result.exists())
        return result
    }

    val defaultFileName: String
        get() = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(LocalDateTime.now())

    fun getOutputMediaUri(
        temp: Boolean,
        application: MyApplication,
        prefix: String = "",
        fileName: String = prefix + defaultFileName,
        extension: String? = "jpg"
    ): Uri {
        val outputMediaFile = getOutputMediaFile(fileName, temp, application, true, extension)
        return try {
            getContentUriForFile(application, outputMediaFile)
        } catch (e: IllegalArgumentException) {
            if (!temp) {
                throw NougatFileProviderException(e)
            }
            Uri.fromFile(outputMediaFile)
        }
    }

    fun getPictureUriBase(temp: Boolean, application: MyApplication) =
        getOutputMediaUri(temp, application).toString()
            .substring(
                0, getOutputMediaUri(temp, application).toString().lastIndexOf('/')
            )

    private fun getOutputMediaFileName(base: String, postfix: Int, extension: String?) =
        "$base${
            if (postfix > 0) {
                "_$postfix"
            } else ""
        }${extension?.let { ".$it" } ?: "" }"

    @JvmStatic
    fun getPictureDir(context: Context, secure: Boolean): File {
        val result: File = if (secure) {
            File(context.filesDir, "images")
        } else {
            //https://stackoverflow.com/a/43497841/1199911
            context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES)[0]
        }
        result.mkdirs()
        return if (result.exists() && result.canWrite()) result else File(context.filesDir, "images").apply { mkdirs() }
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getFileForUri(context: Context, pictureUri: Uri): File {
        if ("file" == pictureUri.scheme) {
            return File(pictureUri.path!!)
        }
        if (!isMyExpensesAuthority(pictureUri.authority)) {
            Preconditions.checkArgument(
                "authority",
                getFileProviderAuthority(context),
                pictureUri.authority
            )
        }
        val pathSegments = pictureUri.pathSegments
        val fileName = pathSegments.lastOrNull() ?: ""
        val file = when (pathSegments.firstOrNull()) {
            "external-files" -> {
                if (pathSegments.size > 2) {
                    File(getPictureDir(context, false), pathSegments.last())
                } else if (pathSegments.size == 2) {
                    File(getPictureDir(context, false), pathSegments[1])
                } else {
                    File(getPictureDir(context, false), fileName)
                }
            }
            "images" -> {
                if (pathSegments.size > 1) File(getPictureDir(context, true), pathSegments[1])
                else File(getPictureDir(context, true), fileName)
            }
            "cache" -> {
                if (pathSegments.size > 1) File(context.cacheDir, pathSegments[1])
                else File(context.cacheDir, fileName)
            }
            "external" -> File(getPictureDir(context, false), fileName)
            else -> File(getPictureDir(context, false), fileName)
        }

        if (!file.exists() && fileName.isNotEmpty()) {
            val restored = AttachmentHealHelper.restoreAttachmentFile(context, fileName, file)
            if (restored != null && restored.exists() && restored.length() > 0) {
                return restored
            }
        }
        return file
    }
}