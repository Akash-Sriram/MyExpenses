package org.totschnig.myexpenses.provider

import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.preference.dynamicExchangeRatesDefaultKey

@RunWith(RobolectricTestRunner::class)
class DynamicCurrenciesTest: BaseTestWithRepository() {

    private val preferencesHolder get() = application.appComponent.preferencesHolder()

    @Before
    fun setup() {
        contentResolver.delete(TransactionProvider.ACCOUNTS_URI, null, null)
        runBlocking {
            dataStore.edit {
                it.remove(dynamicExchangeRatesDefaultKey)
            }
            preferencesHolder.snapshotStateFlow.first { it[dynamicExchangeRatesDefaultKey] == null }
        }
        insertAccount("Home")
        insertAccount("ForeignStatic", currency = "VND")
        insertAccount("ForeignDynamic", currency = "PLN", dynamic = true)
    }

    @After
    fun tearDown() {
        contentResolver.delete(TransactionProvider.ACCOUNTS_URI, null, null)
        runBlocking {
            dataStore.edit {
                it.remove(dynamicExchangeRatesDefaultKey)
            }
            preferencesHolder.snapshotStateFlow.first { it[dynamicExchangeRatesDefaultKey] == null }
        }
    }

    @Test
    fun testDynamicCurrenciesPerAccount() {
        val currencies = contentResolver.query(
            TransactionProvider.DYNAMIC_CURRENCIES_URI,
            null, null, null, null
        )!!.useAndMapToList {
            it.getString(0)
        }
        Truth.assertThat(currencies).containsExactly("PLN")
    }

    @Test
    fun testDynamicCurrenciesAllDynamic() {
        runBlocking {
            dataStore.edit {
                it[dynamicExchangeRatesDefaultKey] = "DYNAMIC"
            }
            preferencesHolder.snapshotStateFlow.first { it[dynamicExchangeRatesDefaultKey] == "DYNAMIC" }
        }
        val currencies = contentResolver.query(
            TransactionProvider.DYNAMIC_CURRENCIES_URI,
            null, null, null, null
        )!!.useAndMapToList {
            it.getString(0)
        }
        Truth.assertThat(currencies).containsExactly("PLN", "VND")
    }

    @Test
    fun testDynamicCurrenciesAllStatic() {
        runBlocking {
            dataStore.edit {
                it[dynamicExchangeRatesDefaultKey] = "STATIC"
            }
            preferencesHolder.snapshotStateFlow.first { it[dynamicExchangeRatesDefaultKey] == "STATIC" }
        }
        val currencies = contentResolver.query(
            TransactionProvider.DYNAMIC_CURRENCIES_URI,
            null, null, null, null
        )!!.useAndMapToList {
            it.getString(0)
        }
        Truth.assertThat(currencies).isEmpty()
    }
}