package org.totschnig.myexpenses.util

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_URI
import org.totschnig.myexpenses.provider.TABLE_ATTACHMENTS
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.io.FileCopyUtils
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

object AttachmentHealHelper {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isHealingRunning = AtomicBoolean(false)

    // In-memory cache mapping clean filename -> SAF DocumentFile or java.io.File source
    @Volatile
    private var mediaMapCache: Map<String, Any>? = null

    /**
     * Attempts to find and restore a missing attachment picture to [targetFile].
     *
     * @param context Application or Activity context.
     * @param targetFileName The expected image file name (e.g. "20260427_132740_2.jpg").
     * @param targetFile The destination file in the app's picture directory. If null, derived automatically.
     * @return The local File if found or restored, or null if recovery was not possible.
     */
    fun restoreAttachmentFile(
        context: Context,
        targetFileName: String,
        targetFile: File? = null
    ): File? {
        val destFile = targetFile ?: File(PictureDirHelper.getPictureDir(context, false), targetFileName)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile
        }

        val cleanTarget = targetFileName.trim()
        if (cleanTarget.isEmpty()) return null

        // 1. Direct candidate directories (old package paths across previous migrations)
        val candidatePaths = listOf(
            "/storage/emulated/0/Android/data/org.totschnig.myexpenses/files/Pictures/$cleanTarget",
            "/storage/emulated/0/Android/data/com.myexpenses/files/Pictures/$cleanTarget",
            "/storage/emulated/0/Android/data/com.myexpenses.debug/files/Pictures/$cleanTarget"
        )
        for (candidatePath in candidatePaths) {
            val candidate = File(candidatePath)
            if (candidate.exists() && candidate.canRead() && candidate.length() > 0 && candidate.absolutePath != destFile.absolutePath) {
                try {
                    candidate.copyTo(destFile, overwrite = true)
                    if (destFile.exists() && destFile.length() > 0) {
                        Timber.i("Restored attachment %s from candidate path: %s", cleanTarget, candidatePath)
                        return destFile
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to copy from candidate path: %s", candidatePath)
                }
            }
        }

        // 2. Direct filesystem search in common backup media directories
        val directMediaDirs = listOf(
            File("/storage/emulated/0/Documents/Backup/My Expenses/media"),
            File("/storage/emulated/0/Documents/Backup/My Expense/media"),
            File(context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile?.parentFile, "Documents/Backup/My Expenses/media")
        )
        for (mediaDir in directMediaDirs) {
            if (mediaDir.exists() && mediaDir.isDirectory && mediaDir.canRead()) {
                val directMatch = findFileByTargetName(mediaDir, cleanTarget)
                if (directMatch != null) {
                    try {
                        directMatch.copyTo(destFile, overwrite = true)
                        if (destFile.exists() && destFile.length() > 0) {
                            Timber.i("Restored attachment %s from direct media directory: %s", cleanTarget, directMatch.absolutePath)
                            return destFile
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to copy from direct media file: %s", directMatch.absolutePath)
                    }
                }
            }
        }

        // 3. Search via SAF tree permission (AppDir / companion media directory)
        try {
            val mediaMap = getOrBuildMediaMap(context)
            val source = mediaMap[cleanTarget]
            if (source != null) {
                val copySuccess = copySourceToFile(context, source, destFile)
                if (copySuccess && destFile.exists() && destFile.length() > 0) {
                    Timber.i("Restored attachment %s via SAF media map", cleanTarget)
                    return destFile
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error during SAF media search for %s", cleanTarget)
        }

        // 4. Search within backup ZIP archives
        try {
            val restoredFromZip = restoreFromBackupZip(context, cleanTarget, destFile)
            if (restoredFromZip != null && destFile.exists() && destFile.length() > 0) {
                Timber.i("Restored attachment %s from backup ZIP archive", cleanTarget)
                return destFile
            }
        } catch (e: Exception) {
            Timber.w(e, "Error searching backup ZIP for %s", cleanTarget)
        }

        return null
    }

    private fun findFileByTargetName(dir: File, targetFileName: String): File? {
        val files = dir.listFiles() ?: return null
        val datTarget = "$targetFileName.dat"
        for (f in files) {
            val name = f.name
            if (name == targetFileName || name == datTarget ||
                name.endsWith("_$targetFileName") || name.endsWith("_$datTarget")
            ) {
                return f
            }
        }
        return null
    }

    @Synchronized
    private fun getOrBuildMediaMap(context: Context): Map<String, Any> {
        mediaMapCache?.let { return it }

        val map = mutableMapOf<String, Any>()

        // Look for companion media directory via IncrementalBackupHelper and AppDir
        val mediaDir = IncrementalBackupHelper.findCompanionMediaDir(context, Uri.EMPTY)
            ?: AppDirHelper.getAppDir(context).getOrNull()?.findFile(IncrementalBackupHelper.DIR_MEDIA)

        if (mediaDir != null && mediaDir.isDirectory) {
            val datSuffix = ".${IncrementalBackupHelper.MEDIA_EXTENSION}"
            for (doc in mediaDir.listFiles()) {
                val rawName = doc.name ?: continue
                if (rawName == ".nomedia") continue
                val cleanName = if (rawName.endsWith(datSuffix)) rawName.removeSuffix(datSuffix) else rawName
                map[rawName] = doc
                map[cleanName] = doc
                if (rawName.contains('_')) {
                    map[rawName.substringAfter('_')] = doc
                }
                if (cleanName.contains('_')) {
                    map[cleanName.substringAfter('_')] = doc
                }
            }
        }

        // Also index direct media directory if accessible
        val directMediaDir = File("/storage/emulated/0/Documents/Backup/My Expenses/media")
        if (directMediaDir.exists() && directMediaDir.isDirectory && directMediaDir.canRead()) {
            val datSuffix = ".${IncrementalBackupHelper.MEDIA_EXTENSION}"
            directMediaDir.listFiles()?.forEach { file ->
                val rawName = file.name
                if (rawName != ".nomedia") {
                    val cleanName = if (rawName.endsWith(datSuffix)) rawName.removeSuffix(datSuffix) else rawName
                    map.putIfAbsent(rawName, file)
                    map.putIfAbsent(cleanName, file)
                    if (rawName.contains('_')) {
                        map.putIfAbsent(rawName.substringAfter('_'), file)
                    }
                    if (cleanName.contains('_')) {
                        map.putIfAbsent(cleanName.substringAfter('_'), file)
                    }
                }
            }
        }

        mediaMapCache = map
        return map
    }

    private fun copySourceToFile(context: Context, source: Any, destFile: File): Boolean {
        return try {
            when (source) {
                is File -> {
                    source.copyTo(destFile, overwrite = true)
                    true
                }
                is DocumentFile -> {
                    context.contentResolver.openInputStream(source.uri)?.use { inStream ->
                        FileOutputStream(destFile).use { outStream ->
                            FileCopyUtils.copy(inStream, outStream)
                            true
                        }
                    } ?: false
                }
                else -> false
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to copy source %s to %s", source, destFile)
            false
        }
    }

    private fun restoreFromBackupZip(context: Context, targetFileName: String, destFile: File): File? {
        val appDir = AppDirHelper.getAppDir(context).getOrNull() ?: return null
        val zipFiles = appDir.listFiles().filter { it.name?.endsWith(".zip") == true }
        for (zipDoc in zipFiles) {
            try {
                context.contentResolver.openInputStream(zipDoc.uri)?.use { inStream ->
                    val tempZip = File(context.cacheDir, "temp_search.zip")
                    try {
                        FileOutputStream(tempZip).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                        ZipFile(tempZip).use { zip ->
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val entryName = entry.name.substringAfterLast('/')
                                if (entryName == targetFileName || entryName.endsWith("_$targetFileName")) {
                                    zip.getInputStream(entry).use { entryStream ->
                                        FileOutputStream(destFile).use { outStream ->
                                            entryStream.copyTo(outStream)
                                        }
                                    }
                                    return destFile
                                }
                            }
                        }
                    } finally {
                        tempZip.delete()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error inspecting backup zip %s", zipDoc.name)
            }
        }
        return null
    }

    /**
     * Asynchronously scans all attachments in the database and restores any missing pictures
     * in the background. Does not block the caller.
     */
    fun healAllMissingPicturesAsync(context: Context) {
        if (!isHealingRunning.compareAndSet(false, true)) {
            return
        }

        scope.launch {
            try {
                val resolver = context.contentResolver
                val urisToHeal = mutableListOf<String>()

                resolver.query(
                    TransactionProvider.ATTACHMENTS_URI,
                    arrayOf(KEY_ROWID, KEY_URI),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val uriStr = cursor.getString(1) ?: continue
                        urisToHeal.add(uriStr)
                    }
                }

                if (urisToHeal.isEmpty()) {
                    return@launch
                }

                val pictureDir = PictureDirHelper.getPictureDir(context, false)
                var restoredCount = 0

                for (uriStr in urisToHeal) {
                    val uri = uriStr.toUri()
                    val targetFileName = uri.lastPathSegment ?: continue
                    val targetFile = File(pictureDir, targetFileName)

                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        val restored = restoreAttachmentFile(context, targetFileName, targetFile)
                        if (restored != null && restored.exists() && restored.length() > 0) {
                            restoredCount++
                        }
                    }
                }

                if (restoredCount > 0) {
                    Timber.i("Attachment heal completed: successfully restored %d missing pictures to %s", restoredCount, pictureDir.absolutePath)
                }
            } catch (e: Exception) {
                Timber.e(e, "Background attachment healing failed")
            } finally {
                isHealingRunning.set(false)
            }
        }
    }

    /**
     * Clears in-memory caches (e.g. after a backup restore).
     */
    fun clearCache() {
        mediaMapCache = null
    }
}
