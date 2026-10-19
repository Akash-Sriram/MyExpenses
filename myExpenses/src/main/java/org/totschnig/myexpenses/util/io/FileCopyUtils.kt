package org.totschnig.myexpenses.util.io

import android.content.ContentResolver
import android.net.Uri
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object FileCopyUtils {
    @JvmStatic
    fun copy(src: File, dst: File): Boolean {
        return try {
            FileInputStream(src).use { srcStream ->
                FileOutputStream(dst).use { dstStream ->
                    dstStream.channel.transferFrom(srcStream.channel, 0, srcStream.channel.size())
                    true
                }
            }
        } catch (e: IOException) {
            CrashHandler.report(e)
            false
        }
    }

    /**
     * copy src uri to dest uri
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copy(contentResolver: ContentResolver, src: Uri, dest: Uri) {
        contentResolver.openInputStream(src).use { input ->
            if (input == null) {
                throw IOException("Could not open InputStream $src")
            }
            contentResolver.openOutputStream(dest).use { output ->
                if (output == null) {
                    throw IOException("Could not open OutputStream $dest")
                }
                copy(input, output)
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }
}
