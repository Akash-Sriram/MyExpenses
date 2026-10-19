package org.totschnig.myexpenses.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.loadPrice
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.MissingApiKeyException
import java.math.BigDecimal
import java.time.LocalDate

class ExchangeRateHandler(
    val repository: Repository,
    val prefHandler: PrefHandler,
    val currencyContext: CurrencyContext,
) {
    suspend fun loadExchangeRate(
        other: CurrencyUnit,
        base: CurrencyUnit,
        date: LocalDate,
        source: ExchangeRateApi? = null,
    ): BigDecimal = withContext(Dispatchers.IO) {
        repository.loadPrice(base, other, date, source) ?: BigDecimal.ONE
    }

    fun relevantSources(commodity: String): List<ExchangeRateApi> = emptyList()

    suspend fun loadFromNetwork(
        source: ExchangeRateApi,
        date: LocalDate,
        other: String,
        base: String,
    ): Pair<LocalDate, BigDecimal> = withContext(Dispatchers.IO) {
        date to BigDecimal.ONE
    }

    suspend fun loadTimeSeries(
        source: ExchangeRateApi,
        start: LocalDate,
        end: LocalDate,
        except: Set<LocalDate>,
        other: String,
        base: String,
    ): Pair<Int, Exception?> = withContext(Dispatchers.IO) {
        0 to null
    }
}

fun Throwable.transformForUser(context: Context, other: String, base: String) = when (this) {
    is java.lang.UnsupportedOperationException ->
        Exception(
            context.getString(R.string.exchange_rate_not_supported, other, base)
        )

    is MissingApiKeyException ->
        Exception(
            context.getString(R.string.pref_exchange_rates_api_key_summary, source.host)
        )

    else -> this
}
