package org.totschnig.myexpenses.io

import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model2.Account
import java.math.BigDecimal
import java.time.Instant

data class ImportAccount(
    val type: String? = PREDEFINED_NAME_CASH,
    val memo: String = "",
    val desc: String = "",
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    val transactions: List<ImportTransaction> = emptyList()
) {
    fun toAccount(currency: CurrencyUnit, type: AccountType): Account {
        return Account(
            label = memo,
            currency = currency.code,
            openingBalance = Money.buildWithMajor(currency, openingBalance).getOrThrow().amountMinor,
            description = desc,
            type = type
        )
    }
}

data class ImportTransaction(
    val date: Instant = Instant.now(),
    val valueDate: Instant? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val payee: String? = null,
    val memo: String? = null,
    val category: String? = null,
    val categoryClass: String? = null,
    val toAccount: String? = null,
    val toAmount: BigDecimal? = null,
    val status: String? = null,
    val number: String? = null,
    val method: String? = null,
    val tags: List<String>? = null,
    val splits: List<ImportTransaction>? = null
) {
    val isSplit: Boolean get() = splits != null
    val isTransfer: Boolean get() = !isSplit && toAccount != null
    val isOpeningBalance: Boolean get() = payee == "Opening Balance"
}