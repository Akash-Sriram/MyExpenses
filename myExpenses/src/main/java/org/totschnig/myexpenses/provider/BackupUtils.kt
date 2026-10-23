package org.totschnig.myexpenses.provider

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import androidx.documentfile.provider.DocumentFile
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.sync.SyncAdapter
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.ZipUtils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.crypt.EncryptionHelper
import org.totschnig.myexpenses.util.localizedThrowable
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val BACKUP_DB_FILE_NAME = "BACKUP"
const val BACKUP_PREF_FILE_NAME = "BACKUP_PREF"
const val BACKUP_DATASTORE_FILE_NAME = "ui_settings.preferences_pb"

@Synchronized
fun doBackup(
    context: Context,
    prefHandler: PrefHandler,
    withSync: Boolean,
    lenientMode: Boolean = false
): Result<Pair<DocumentFile, List<DocumentFile>>> {
    val password = prefHandler.getString(PrefKey.EXPORT_PASSWORD, null)
    val cloudStorage = if (withSync) prefHandler.cloudStorage else null
    return AppDirHelper.checkAppDir(context).mapCatching { appDir ->
        val encrypted = !password.isNullOrEmpty()
        val backupFile =
            requireBackupFile(appDir, prefHandler.backupFilePrefix, encrypted)
                ?: throw localizedThrowable(context, R.string.io_error_backupdir_null)
        val cacheDir = AppDirHelper.newWorkingDirectory(context, "backup").getOrThrow()
        backup(cacheDir, context, prefHandler, lenientMode).getOrThrow()
        try {
            ZipUtils.zipBackup(context, cacheDir, backupFile, password, lenientMode)

            // Automated SQLite & ZIP Integrity Validation
            val isValid = validateBackupFile(context, backupFile, password)
            if (!isValid) {
                backupFile.delete()
                throw IllegalStateException("Backup integrity check failed! Corrupt file deleted. Existing healthy backups were preserved.")
            }

            try {
                sync(context.contentResolver, cloudStorage, backupFile)
            } catch (e: Exception) {
                if (!lenientMode) throw e
            }

            // Keep exactly the last 3 healthy backups, prune older ones silently
            val pruned = pruneOldBackups(appDir, prefHandler.backupFilePrefix, keep = 3)
            backupFile to pruned
        } catch (e: Throwable) {
            CrashHandler.report(e)
            throw if (e is OutOfMemoryError && encrypted) {
                Exception("Encrypting large backup file has failed. Unencrypted backups should work.")
            } else e
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}

fun validateBackupFile(
    context: Context,
    backupFile: DocumentFile,
    password: String? = null
): Boolean {
    val tempDir = AppDirHelper.newWorkingDirectory(context, "validate_backup").getOrNull() ?: return false
    return try {
        context.contentResolver.openInputStream(backupFile.uri)?.use { inputStream ->
            val wrapped = EncryptionHelper.wrap(inputStream) ?: return false
            val isEncrypted = EncryptionHelper.isEncrypted(wrapped)
            ZipUtils.unzip(wrapped, tempDir, if (isEncrypted) password else null)
        } ?: return false

        val dbFile = getBackupDbFile(tempDir)
        val prefFile = getBackupPrefFile(tempDir)
        if (!dbFile.exists() || !prefFile.exists()) return false

        android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.version
            if (version > DATABASE_VERSION) return false

            val pragmaCheckOk = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val integrityOk = try { db.isDatabaseIntegrityOk } catch (_: Exception) { false }
            pragmaCheckOk && integrityOk
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Backup validation failed for %s", backupFile.name)
        false
    } finally {
        tempDir.deleteRecursively()
    }
}

fun pruneOldBackups(
    appDir: DocumentFile,
    prefix: String = "myexpenses-backup",
    keep: Int = 3
): List<DocumentFile> {
    val backups = appDir.listFiles()
        .filter {
            it.name?.matches("""$prefix-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()) == true
        }
        .sortedByDescending { it.lastModified() }
    val toDelete = backups.drop(keep)
    toDelete.forEach { it.delete() }
    return toDelete
}

private val PrefHandler.backupFilePrefix
    get() = requireString(
        PrefKey.BACKUP_FILE_PREFIX,
        "myexpenses-backup"
    )

fun listOldBackups(appDir: DocumentFile, prefHandler: PrefHandler): List<DocumentFile> {
    val keep = prefHandler.getInt(PrefKey.PURGE_BACKUP_KEEP, 3)
    val backups = appDir.listFiles()
        .filter {
            it.name?.matches("""${prefHandler.backupFilePrefix}-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()) == true
        }
        .sortedByDescending { it.lastModified() }
    return backups.drop(keep)
}

private fun sync(contentResolver: ContentResolver, backend: String?, backupFile: DocumentFile) {
    backend?.let {
        var backupFileName = backupFile.name
        if (backupFileName == null) {
            CrashHandler.report(Exception("Could not get name from uri ${backupFile.uri}"))
            backupFileName = "backup-" + DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).format(LocalDate.now())
        }
        DbUtils.storeSetting(
            contentResolver,
            SyncAdapter.KEY_UPLOAD_AUTO_BACKUP_NAME,
            backupFileName
        )
        DbUtils.storeSetting(
            contentResolver,
            SyncAdapter.KEY_UPLOAD_AUTO_BACKUP_URI,
            backupFile.uri.toString()
        )
        GenericAccountService.requestSync(backend, extras = Bundle().apply {
            putBoolean(SyncAdapter.KEY_AUTO_BACKUP_ONLY, true)
        })
    }
}

private fun requireBackupFile(
    appDir: DocumentFile,
    prefix: String,
    encrypted: Boolean
): DocumentFile? {
    return AppDirHelper.timeStampedFile(
        parentDir = appDir,
        prefix = prefix,
        mimeType = if (encrypted) "application/octet-stream" else "application/zip",
        extension = if (encrypted) "enc" else "zip"
    )
}

fun getBackupDbFile(backupDir: File?) = File(backupDir, BACKUP_DB_FILE_NAME)
fun getBackupPrefFile(backupDir: File?) = File(backupDir, BACKUP_PREF_FILE_NAME)
fun getBackupDataStoreFile(backupDir: File?) = File(backupDir, BACKUP_DATASTORE_FILE_NAME)