package org.totschnig.myexpenses.export

import android.content.Context
import androidx.annotation.Keep
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.ExportFormat
import org.totschnig.myexpenses.model.TransactionDTO
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.provider.filter.Criterion
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return if (decoder is JsonDecoder) {
            BigDecimal(decoder.decodeJsonElement().jsonPrimitive.content)
        } else {
            BigDecimal(decoder.decodeString())
        }
    }
}

class ZonedDateTimeSerializer(private val formatter: DateTimeFormatter) : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(formatter.format(value))
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime {
        return ZonedDateTime.parse(decoder.decodeString(), formatter)
    }
}

/**
 * @param account          Account to print
 * @param filter           only transactions matched by filter will be considered
 * @param notYetExportedP  if true only transactions not marked as exported will be handled
 * @param dateFormat       format that can be parsed by SimpleDateFormat class
 * @param decimalSeparator , or .
 * @param encoding         the string describing the desired character encoding.
 */
class JSONExporter(
    account: Account,
    currencyContext: CurrencyContext,
    filter: Criterion?,
    notYetExportedP: Boolean,
    dateFormat: String,
    decimalSeparator: Char,
    encoding: String,
    private val preamble: String = "",
    private val appendix: String = ""
) :
    AbstractExporter(
        account, currencyContext, filter, notYetExportedP, dateFormat,
        decimalSeparator, encoding
    ) {

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(ZonedDateTimeSerializer(dateFormatter))
            contextual(BigDecimalSerializer)
        }
    }

    override val format = ExportFormat.JSON

    override val useCategoryOfFirstPartForParent = false

    override fun header(context: Context) =
        "$preamble{\"uuid\":${json.encodeToString(account.uuid)},\"label\":${json.encodeToString(account.label)},\"currency\":${json.encodeToString(account.currency)},\"openingBalance\":${json.encodeToString(BigDecimalSerializer, openingBalance)},\"transactions\": ["

    override fun TransactionDTO.marshall(categoryPaths: Map<Long, List<String>>): String =
        json.encodeToString(convert(this))

    override fun recordDelimiter(isLastLine: Boolean) = if (isLastLine) null else ","

    override fun footer(): String = "]}$appendix"

    private fun convert(dto: TransactionDTO) : Transaction = with(dto) {
        Transaction(
            uuid = uuid,
            date = date,
            payee = payee,
            amount = amount,
            category = catId?.let { cat -> categoryPaths[cat] },
            transferAccount = transferAccount,
            comment = comment,
            methodLabel = methodLabel,
            status = status,
            referenceNumber = referenceNumber,
            attachments = attachmentFileNames,
            tags = tagList,
            splits = splits?.map(::convert)
        )
    }
}

@Keep
@Serializable
data class Transaction(
    val uuid: String,
    @Contextual val date: ZonedDateTime,
    val payee: String? = null,
    @Contextual val amount: BigDecimal,
    val category: List<String>? = null,
    val transferAccount: String? = null,
    val comment: String? = null,
    val methodLabel: String? = null,
    val status: CrStatus? = null,
    val referenceNumber: String? = null,
    val attachments: List<String>? = null,
    val tags: List<String>? = null,
    val splits: List<Transaction>? = null
)