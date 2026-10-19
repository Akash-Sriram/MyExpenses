package org.totschnig.myexpenses.retrofit

import org.totschnig.myexpenses.preference.PrefHandler

sealed class ExchangeRateSource(val name: String) {
    object User : ExchangeRateSource("user")
    object Calculation : ExchangeRateSource("calculation")
    object Import : ExchangeRateSource("import")

    companion object {
        val values
            get() = arrayOf(
                User,
                Calculation,
                Import
            )

        fun getByName(name: String) = values.firstOrNull { it.name == name } ?: User
    }
}

sealed class ExchangeRateApi(val id: Int, name: String, val host: String) :
    ExchangeRateSource(name) {

    open val limitToOneRequestPerDay: Boolean = false
    open val hasTimeSeriesRequest: Boolean = false

    open fun isSupported(vararg currency: String) = false

    object None : ExchangeRateApi(0, "none", "")

    companion object {
        val values get() = emptyArray<ExchangeRateApi>()

        fun getById(id: Int): ExchangeRateApi? = null
        fun getByName(name: String): ExchangeRateApi = None
        fun configuredSources(prefHandler: PrefHandler): Set<ExchangeRateApi> = emptySet()
    }
}

class MissingApiKeyException(val source: ExchangeRateApi) : Exception()
