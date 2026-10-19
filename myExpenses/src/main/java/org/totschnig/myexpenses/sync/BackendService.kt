package org.totschnig.myexpenses.sync

import android.content.Context
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

enum class BackendService(
    private val className: String,
    val id: Int,
    val label: String,
    val feature: Feature?,
    val supportsReconfiguration: Boolean = false
) {
    SAF(
        "org.totschnig.myexpenses.sync.StorageAccessFrameworkBackendProviderFactory",
        R.id.SYNC_BACKEND_LOCAL,
        "SAF",
        null
    );

    open fun isAvailable(context: Context) = true

    fun instantiate(): Result<SyncBackendProviderFactory> = kotlin.runCatching {
        Class.forName(className).getDeclaredConstructor()
            .newInstance() as SyncBackendProviderFactory
    }.onFailure {
        CrashHandler.report(it)
    }

    fun buildAccountName(extra: String): String {
        return "$label - $extra"
    }

    companion object {
        fun forAccount(account: String) = kotlin.runCatching {
            entries.firstOrNull { account.startsWith(it.label) }
                ?: throw IllegalArgumentException("No Backend found for $account")
        }

        fun allAvailable(context: Context) = entries.filter { it.isAvailable(context) }
    }
}
