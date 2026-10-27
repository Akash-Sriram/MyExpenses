package org.totschnig.myexpenses.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_URI
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.backup
import org.totschnig.myexpenses.provider.fileName
import org.totschnig.myexpenses.provider.validateBackupFile
import org.totschnig.myexpenses.util.io.FileCopyUtils
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object IncrementalBackupHelper {

    const val DIR_MEDIA = "media"
    const val DIR_SNAPSHOTS = "snapshots"
    const val SNAPSHOT_PREFIX = "myexpenses-db"
    const val MEDIA_EXTENSION = "dat"

    data class SyncResult(
        val totalPictures: Int,
        val copiedPictures: Int,
        val skippedPictures: Int,
        val durationMs: Long
    )

    data class IncrementalBackupResult(
        val snapshotFile: DocumentFile,
        val syncResult: SyncResult,
        val prunedSnapshots: List<DocumentFile>
    )

    /**
     * Obtains or creates the media/ directory inside appDir, ensuring .nomedia is present.
     */
    fun getMediaDir(appDir: DocumentFile): DocumentFile? {
        val media = appDir.findFile(DIR_MEDIA) ?: appDir.createDirectory(DIR_MEDIA)
        if (media != null && media.findFile(".nomedia") == null) {
            try { media.createFile("*/*", ".nomedia") } catch (_: Exception) {}
        }
        return media
    }

    /**
     * Incrementally syncs new picture/receipt attachments into the media/ folder via SAF.
     * Attachments are stored as obfuscated .dat binary blobs to prevent gallery indexing and thumbnailing.
     * Existing media files are skipped in O(1) time using an in-memory set.
     */
    fun syncIncrementalMedia(context: Context, appDir: DocumentFile): SyncResult {
        val startTime = System.currentTimeMillis()
        val resolver = context.contentResolver
        var total = 0
        var copied = 0
        var skipped = 0

        val mediaDir = getMediaDir(appDir)
        if (mediaDir == null) {
            Timber.e("Could not create or find media/ directory in appDir")
            return SyncResult(0, 0, 0, 0)
        }

        // Preload all existing file names into a fast in-memory HashSet
        val existingFiles = mediaDir.listFiles()
            .mapNotNull { it.name }
            .toHashSet()

        try {
            resolver.query(
                TransactionProvider.ATTACHMENTS_URI,
                arrayOf(KEY_ROWID, KEY_URI),
                null,
                null,
                null
            )?.use { cursor ->
                total = cursor.count
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(0)
                    val uriStr = cursor.getString(1) ?: continue
                    val uri = uriStr.toUri()
                    val rawFileName = "${rowId}_${uri.fileName(context)}"
                    val storageFileName = "$rawFileName.$MEDIA_EXTENSION"

                    // Skip if already synced under either the obfuscated or legacy raw name
                    if (existingFiles.contains(storageFileName) || existingFiles.contains(rawFileName)) {
                        skipped++
                        continue
                    }

                    val docFile = mediaDir.createFile("*/*", storageFileName)
                    val copySuccess = if (docFile != null) {
                        try {
                            resolver.openInputStream(uri)?.use { inStream ->
                                resolver.openOutputStream(docFile.uri)?.use { outStream ->
                                    FileCopyUtils.copy(inStream, outStream)
                                    true
                                }
                            } ?: false
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to copy attachment %s via SAF", storageFileName)
                            false
                        }
                    } else false

                    if (copySuccess) {
                        existingFiles.add(storageFileName)
                        copied++
                    } else {
                        skipped++
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed querying attachments for incremental media sync")
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.i("Incremental media sync: total=%d, copied=%d, skipped=%d in %dms", total, copied, skipped, elapsed)
        return SyncResult(total, copied, skipped, elapsed)
    }

    /**
     * Creates an instantaneous, lightweight SQLite DB + preferences snapshot.
     */
    fun createDbSnapshot(
        context: Context,
        appDir: DocumentFile,
        prefHandler: PrefHandler,
        password: String? = null,
        lenientMode: Boolean = false
    ): Result<DocumentFile> = runCatching {
        val encrypted = !password.isNullOrEmpty()
        val extension = if (encrypted) "enc" else "zip"
        val mimeType = if (encrypted) "application/octet-stream" else "application/zip"
        val now = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.US).format(LocalDateTime.now())
        val fileName = "$SNAPSHOT_PREFIX-$now.$extension"

        val snapshotsDir = appDir.findFile(DIR_SNAPSHOTS) ?: appDir.createDirectory(DIR_SNAPSHOTS)
            ?: throw IOException("Could not create snapshots directory in appDir")

        val snapshotFile = snapshotsDir.createFile(
            if (snapshotsDir.uri.scheme == "file") "*/*" else mimeType,
            fileName
        ) ?: throw IOException("Could not create snapshot file $fileName")

        val cacheDir = AppDirHelper.newWorkingDirectory(context, "db_snapshot").getOrThrow()
        try {
            backup(cacheDir, context, prefHandler, lenientMode).getOrThrow()
            ZipUtils.zipDbSnapshot(cacheDir, snapshotFile, context, password)

            // Validate integrity of database snapshot
            val isValid = validateBackupFile(context, snapshotFile, password)
            if (!isValid) {
                snapshotFile.delete()
                throw IllegalStateException("Database snapshot integrity check failed! File deleted.")
            }

            snapshotFile
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    /**
     * Prunes old snapshots in snapshots/ directory, keeping the last [keep] files.
     */
    fun pruneDbSnapshots(appDir: DocumentFile, keep: Int = 3): List<DocumentFile> {
        val snapshotsDir = appDir.findFile(DIR_SNAPSHOTS) ?: return emptyList()
        val regex = """$SNAPSHOT_PREFIX-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()
        val snapshots = snapshotsDir.listFiles()
            .filter { it.name?.matches(regex) == true }
            .sortedByDescending { it.lastModified() }
        val toDelete = snapshots.drop(keep)
        toDelete.forEach { it.delete() }
        return toDelete
    }

    /**
     * Executes the complete daily incremental backup: syncs media + creates instant DB snapshot.
     */
    fun doIncrementalBackup(
        context: Context,
        prefHandler: PrefHandler,
        lenientMode: Boolean = false
    ): Result<IncrementalBackupResult> = AppDirHelper.checkAppDir(context).mapCatching { appDir ->
        val password = prefHandler.getString(PrefKey.EXPORT_PASSWORD, null)

        // 1. Sync new attachments
        val syncResult = syncIncrementalMedia(context, appDir)

        // 2. Create instant DB snapshot
        val snapshotFile = createDbSnapshot(context, appDir, prefHandler, password, lenientMode).getOrThrow()

        // 3. Prune older snapshots (keep strictly last 3)
        val pruned = pruneDbSnapshots(appDir, keep = 3)

        IncrementalBackupResult(snapshotFile, syncResult, pruned)
    }

    /**
     * Locates the companion media folder for an incremental snapshot during restore.
     */
    fun findCompanionMediaDir(context: Context, backupUri: Uri): DocumentFile? {
        // 1. If AppDir is configured in settings
        AppDirHelper.checkAppDir(context).getOrNull()?.let { appDir ->
            val mediaDir = appDir.findFile(DIR_MEDIA)
            if (mediaDir != null && mediaDir.isDirectory) return mediaDir
        }

        // 2. Fallback common storage directories (Music, Download, Documents)
        listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS
        ).forEach { dirType ->
            try {
                val publicDir = Environment.getExternalStoragePublicDirectory(dirType)
                if (publicDir.exists() && publicDir.isDirectory) {
                    val media = File(publicDir, DIR_MEDIA)
                    if (media.exists() && media.isDirectory) {
                        DocumentFile.fromFile(media)?.let { return it }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
