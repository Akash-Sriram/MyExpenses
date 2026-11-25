package org.totschnig.myexpenses.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.PreferenceActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.TimePreference
import org.totschnig.myexpenses.provider.doBackup
import org.totschnig.myexpenses.provider.listOldBackups
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.NotificationBuilderWrapper
import org.totschnig.myexpenses.viewmodel.BackupViewModel
import timber.log.Timber
import java.util.concurrent.TimeUnit

abstract class BaseAutoBackupWorker(context: Context, workerParameters: WorkerParameters) :
    NotifyingBaseWorker(context, workerParameters) {

    override val notificationId = NotificationBuilderWrapper.NOTIFICATION_AUTO_BACKUP
    override val channelId: String = NotificationBuilderWrapper.CHANNEL_ID_AUTO_BACKUP
    override val notificationTitleResId = R.string.pref_auto_backup_title
}

class AutoBackupWorker(context: Context, workerParameters: WorkerParameters) :
    BaseAutoBackupWorker(context, workerParameters) {

    companion object {
        const val ACTION_BACKUP_PURGE_CANCEL = "BACKUP_PURGE_CANCEL"
        const val ACTION_BACKUP_PURGE = "BACKUP_PURGE"

        private const val WORK_NAME = "AutoBackupService"
        private const val IMMEDIATE_WORK_NAME = "AutoBackupService_Immediate"

        fun enqueueOrCancel(context: Context, prefHandler: PrefHandler) {
            val workManager = WorkManager.getInstance(context)
            if (prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)) {
                val delay = TimePreference.getScheduledTime(
                    prefHandler, PrefKey.AUTO_BACKUP_TIME
                )
                val periodicWork = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresStorageNotLow(true)
                            .build()
                    )
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWork
                )
                Timber.i("AutoBackup: scheduled daily recurring backup with initial delay of ${delay}ms")
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                Timber.i("AutoBackup: cancelled auto backup workers")
            }
        }

        fun enqueueImmediate(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val immediateWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateWork
            )
            Timber.i("AutoBackup: enqueued immediate catch-up backup")
        }

        fun checkAndCatchUpOnLaunch(context: Context, prefHandler: PrefHandler) {
            if (!prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)) return

            val lastRun = prefHandler.getLong(PrefKey.AUTO_BACKUP_LAST_RUN, 0L)
            val now = System.currentTimeMillis()
            if (now - lastRun >= TimeUnit.HOURS.toMillis(24)) {
                Timber.i("AutoBackup: Catching up on missed daily backup (last run was ${now - lastRun}ms ago)")
                enqueueImmediate(context)
            }
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }

        fun enqueue(context: Context) {
            enqueueImmediate(context)
        }
    }

    override suspend fun doWork(): Result {
        Timber.w("now doWork $WORK_NAME")

        if (!prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)) {
            Timber.i("Auto-backup is disabled in preferences, skipping doWork")
            return Result.success()
        }

        // 1. Run daily incremental backup (media sync + DB snapshot)
        val incrementalResult = doBackup(
            applicationContext,
            prefHandler,
            withSync = true,
            mode = org.totschnig.myexpenses.provider.BackupMode.INCREMENTAL
        )
        incrementalResult.onSuccess { (snapshot, pruned) ->
            Timber.i("Auto-backup: daily incremental snapshot created (${snapshot.name}), pruned ${pruned.size} older snapshots")
        }.onFailure { e ->
            Timber.e(e, "Auto-backup: daily incremental backup failed")
        }

        // 2. Run daily solid full backup (complete ZIP archive + SQLite integrity validation)
        val fullResult = doBackup(
            applicationContext,
            prefHandler,
            withSync = true,
            mode = org.totschnig.myexpenses.provider.BackupMode.FULL_ZIP
        )
        fullResult.onSuccess { (backupFile, oldBackups) ->
            Timber.i("Auto-backup: daily full solid backup created (${backupFile.name})")
            if (oldBackups.isNotEmpty()) {
                val keep = prefHandler.getInt(PrefKey.PURGE_BACKUP_KEEP, 1)
                val requireConfirmation =
                    keep > 1 && prefHandler.getBoolean(PrefKey.PURGE_BACKUP_REQUIRE_CONFIRMATION, false)
                if (requireConfirmation) {
                    val builder = buildMessage(
                        "${wrappedContext.getString(R.string.dialog_title_purge_backups)} (${oldBackups.size})"
                    )
                    builder.addAction(
                        0,
                        wrappedContext.getString(R.string.menu_delete),
                        PendingIntent.getBroadcast(
                            applicationContext,
                            0,
                            Intent(applicationContext, BackupPurgeReceiver::class.java).setAction(
                                ACTION_BACKUP_PURGE
                            ),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    builder.addAction(
                        0,
                        wrappedContext.getString(android.R.string.cancel),
                        PendingIntent.getBroadcast(
                            applicationContext,
                            0,
                            Intent(applicationContext, BackupPurgeReceiver::class.java).setAction(
                                ACTION_BACKUP_PURGE_CANCEL
                            ),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    notify(builder.build())
                } else {
                    Timber.d("Auto-backup: silently pruned ${oldBackups.size} old backup(s) to retain single verified copy")
                }
            }
        }.onFailure { e ->
            Timber.e(e, "Auto-backup: daily full solid backup failed")
        }

        if (incrementalResult.isSuccess || fullResult.isSuccess) {
            prefHandler.putLong(PrefKey.AUTO_BACKUP_LAST_RUN, System.currentTimeMillis())
            prefHandler.putBoolean(PrefKey.AUTO_BACKUP_DIRTY, false)
            return Result.success()
        }

        return Result.retry()
    }
}

class BackupPurgeWorker(context: Context, workerParameters: WorkerParameters) :
    BaseAutoBackupWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        AppDirHelper.getAppDir(applicationContext).onSuccess { appDir ->
            notify(
                buildMessage(
                    BackupViewModel.purgeResult2Message(
                        applicationContext,
                        listOldBackups(appDir, prefHandler).map { it.delete() })
                ).build()
            )
        }
        return Result.success()
    }
}