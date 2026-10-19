package org.totschnig.myexpenses.retrofit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
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

    protected fun extractJsonError(body: ResponseBody, key: String): String? = try {
        val content = body.string()
        try {
            Json.parseToJsonElement(content).jsonObject[key]?.jsonPrimitive?.content ?: content
        } catch (_: Exception) {
            content.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    open fun extractError(body: ResponseBody): String? = null

    object None : ExchangeRateApi(0, "none", "")

    object Frankfurter : ExchangeRateApi(1, "FRANKFURTER", "api.frankfurter.dev") {
        override val limitToOneRequestPerDay = true
        override fun extractError(body: ResponseBody): String? = extractJsonError(body, "message")
    }

    object OpenExchangeRates : ExchangeRateApi(2, "OPENEXCHANGERATES", "openexchangerates.org") {
        override val hasTimeSeriesRequest = false
        override fun extractError(body: ResponseBody): String? = extractJsonError(body, "description")
    }

    object CoinApi : ExchangeRateApi(3, "COIN_API", "coinapi.io") {
        override fun extractError(body: ResponseBody): String? = extractJsonError(body, "error")
    }

    companion object {
        val values get() = arrayOf(Frankfurter, OpenExchangeRates, CoinApi)

        fun getById(id: Int): ExchangeRateApi? = values.find { it.id == id }
        fun getByName(name: String): ExchangeRateApi = values.firstOrNull { it.name == name } ?: None
        fun configuredSources(prefHandler: PrefHandler): Set<ExchangeRateApi> = emptySet()
    }
}

class MissingApiKeyException(val source: ExchangeRateApi) : Exception()

