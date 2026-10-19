package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.os.Bundle
import android.database.Cursor
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.db2.AccountOpeningInfo
import org.totschnig.myexpenses.db2.loadOpeningBalancesPerAccountV1
import org.totschnig.myexpenses.db2.loadOpeningBalancesPerAccountV2
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.KEY_ACCOUNT_GROUPING
import org.totschnig.myexpenses.provider.DataBaseAccount
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_GROUPING
import org.totschnig.myexpenses.util.enumValueOrDefault

data class HistoryQueryParams(
    val uri: Uri,
    val selection: String?,
    val selectionArgs: Array<String>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistoryQueryParams
        if (uri != other.uri) return false
        if (selection != other.selection) return false
        if (selectionArgs != null) {
            if (other.selectionArgs == null) return false
            if (!selectionArgs.contentEquals(other.selectionArgs)) return false
        } else if (other.selectionArgs != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + (selection?.hashCode() ?: 0)
        result = 31 * result + (selectionArgs?.contentHashCode() ?: 0)
        return result
    }
}

class HistoryViewModel(application: Application, val savedStateHandle: SavedStateHandle) :
    ContentResolvingAndroidViewModel(application) {

    val historyQueryParams = MutableStateFlow<HistoryQueryParams?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyCursor: Flow<Cursor?> = historyQueryParams.filterNotNull().flatMapLatest { params ->
        contentResolver.observeQuery(
            params.uri,
            null,
            params.selection,
            params.selectionArgs,
            null
        ).map { it.run() }
    }
    private val accountId by lazy { savedStateHandle.get<Long>(KEY_ACCOUNTID) }
    private val defaultGrouping: Grouping by lazy {
        savedStateHandle.get<Grouping>(KEY_GROUPING)
            .takeIf { it != Grouping.NONE }
            ?: Grouping.MONTH
    }
    private val groupingPrefKey = stringPreferencesKey("historyGrouping_$accountId")

    val grouping by lazy {
        dataStore.data.map {
            enumValueOrDefault(it[groupingPrefKey], defaultGrouping)
        }.stateIn(viewModelScope, SharingStarted.Lazily, defaultGrouping)
    }

    fun accountInfo(extras: Bundle) = combine(account(extras), grouping, openingBalances(extras)) {
        account, grouping, openingBalances ->
        Triple(account, grouping, openingBalances)
    }

    fun openingBalances(extras: Bundle): Flow<Map<Long, AccountOpeningInfo>> {
        val accountId = extras.getLong(KEY_ACCOUNTID)
        return when {
            DataBaseAccount.isHomeAggregate(accountId) -> repository.loadOpeningBalancesPerAccountV1(accountId)
            accountId == 0L && extras.containsKey(KEY_ACCOUNT_GROUPING) && extras.getString(KEY_ACCOUNT_GROUPING) != AccountGrouping.CURRENCY.name ->
                repository.loadOpeningBalancesPerAccountV2(extras)
            else -> account(extras).map { mapOf(it.id to
                    AccountOpeningInfo(
                        it.currency,
                        it.openingBalance,
                        false, //single account is shown in account currency, no conversion needed
                        1.0
                    )
            ) }
        }
    }

    fun persistGrouping(grouping: Grouping) {
        viewModelScope.launch {
            dataStore.edit { preference ->
                preference[groupingPrefKey] = grouping.name
            }
        }
    }
}