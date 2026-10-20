package org.totschnig.myexpenses.sync

import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.myApplication
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_UUID
import org.totschnig.myexpenses.provider.TransactionProvider
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accountName = inputData.getString(KEY_ACCOUNT_NAME)
        val uuid = inputData.getString(KEY_UUID)
        val autoBackupOnly = inputData.getBoolean(KEY_AUTO_BACKUP_ONLY, false)
        val extras = Bundle().apply {
            if (uuid != null) putString(KEY_UUID, uuid)
            if (autoBackupOnly) putBoolean(SyncAdapter.KEY_AUTO_BACKUP_ONLY, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        }

        Timber.i("SyncWorker executing for account: %s, uuid: %s, autoBackupOnly: %b", accountName, uuid, autoBackupOnly)

        val syncAdapter = SyncAdapter(applicationContext, true).also {
            applicationContext.myApplication.appComponent.inject(it)
        }

        val accounts = if (!accountName.isNullOrEmpty()) {
            arrayOf(GenericAccountService.getAccount(accountName))
        } else {
            GenericAccountService.getAccounts(applicationContext)
        }

        if (accounts.isEmpty()) {
            Timber.i("SyncWorker: No accounts to sync")
            return@withContext Result.success()
        }

        var anyFailure = false
        val providerClient = applicationContext.contentResolver.acquireContentProviderClient(TransactionProvider.AUTHORITY)
        try {
            for (account in accounts) {
                val syncResult = SyncResult()
                if (providerClient != null) {
                    syncAdapter.onPerformSync(account, extras, TransactionProvider.AUTHORITY, providerClient, syncResult)
                }
                if (syncResult.hasError()) {
                    Timber.w("SyncWorker: Sync failed for account %s: %s", account.name, syncResult)
                    anyFailure = true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Exception during sync execution")
            anyFailure = true
        } finally {
            providerClient?.close()
        }

        if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_UUID = "uuid"
        const val KEY_AUTO_BACKUP_ONLY = "auto_backup_only"
        private const val WORK_NAME_PREFIX = "sync_account_"

        fun enqueueOneTimeSync(
            context: Context,
            accountName: String,
            uuid: String? = null,
            autoBackupOnly: Boolean = false,
            expedited: Boolean = true
        ) {
            val data = Data.Builder()
                .putString(KEY_ACCOUNT_NAME, accountName)
                .apply {
                    if (uuid != null) putString(KEY_UUID, uuid)
                    putBoolean(KEY_AUTO_BACKUP_ONLY, autoBackupOnly)
                }
                .build()

            val prefHandler = context.myApplication.appComponent.prefHandler()
            val wifiOnly = prefHandler.getBoolean(PrefKey.SYNC_WIFI_ONLY, false)
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(constraints)

            if (expedited) {
                try {
                    requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                } catch (e: Exception) {
                    Timber.w(e, "Cannot set expedited sync work")
                }
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_PREFIX}${accountName}",
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        fun enqueuePeriodicSync(
            context: Context,
            accountName: String,
            frequencyHours: Long,
            wifiOnly: Boolean = false
        ) {
            if (frequencyHours <= 0) {
                cancelSync(context, accountName)
                return
            }

            val data = Data.Builder()
                .putString(KEY_ACCOUNT_NAME, accountName)
                .build()

            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                frequencyHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodic_${WORK_NAME_PREFIX}${accountName}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelSync(context: Context, accountName: String) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("${WORK_NAME_PREFIX}${accountName}")
            workManager.cancelUniqueWork("periodic_${WORK_NAME_PREFIX}${accountName}")
        }
    }
}
