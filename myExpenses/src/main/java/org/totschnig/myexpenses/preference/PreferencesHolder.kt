package org.totschnig.myexpenses.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesHolder @Inject constructor(
    val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshotStateFlow: StateFlow<Preferences> = dataStore.data.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking(Dispatchers.IO) {
            try {
                dataStore.data.first()
            } catch (_: Exception) {
                emptyPreferences()
            }
        }
    )

    val currentPreferences: Preferences
        get() = snapshotStateFlow.value

    operator fun <T> get(key: Preferences.Key<T>): T? = currentPreferences[key]

    fun <T> getOrDefault(key: Preferences.Key<T>, default: T): T = currentPreferences[key] ?: default
}
