package org.totschnig.myexpenses.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.DebtEditScreen
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.epoch2ZonedDateTime
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.DebtViewModel
import org.totschnig.myexpenses.viewmodel.data.Debt
import java.math.BigDecimal
import java.time.LocalDate

class DebtEdit : ProtectedFragmentActivity() {
    private val viewModel: DebtViewModel by viewModels()
    private val currencyViewModel: CurrencyViewModel by viewModels()

    val payeeName: String
        get() = intent.getStringExtra(KEY_PAYEE_NAME) ?: ""

    val payeeId: Long
        get() = intent.getLongExtra(KEY_PAYEEID, 0L)

    private val debtId: Long
        get() = intent.getLongExtra(KEY_DEBT_ID, 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with((application as MyApplication).appComponent) {
            inject(viewModel)
            inject(currencyViewModel)
        }

        setContent {
            AppTheme {
                val availableCurrencies by currencyViewModel.currencies.collectAsStateWithLifecycle(emptyList())

                if (debtId != 0L) {
                    val loadedDebt by viewModel.loadDebt(debtId).collectAsStateWithLifecycle()
                    if (loadedDebt == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val debt = loadedDebt!!
                        if (debt.isSealed) {
                            setResult(RESULT_FIRST_USER)
                            finish()
                        } else {
                            DebtEditContent(
                                isNew = false,
                                initialLabel = debt.label,
                                initialDescription = debt.description,
                                initialAmount = Money(debt.currency, debt.amount).amountMajor.abs().toPlainString(),
                                initialIsOwesMe = debt.amount > 0,
                                initialCurrency = debt.currency,
                                initialDate = epoch2ZonedDateTime(debt.date).toLocalDate(),
                                initialEquivalent = debt.equivalentAmount?.let {
                                    Money(homeCurrency, it).amountMajor.abs().toPlainString()
                                } ?: "",
                                availableCurrencies = availableCurrencies
                            )
                        }
                    }
                } else {
                    DebtEditContent(
                        isNew = true,
                        initialLabel = "",
                        initialDescription = "",
                        initialAmount = "",
                        initialIsOwesMe = true,
                        initialCurrency = homeCurrency,
                        initialDate = LocalDate.now(),
                        initialEquivalent = "",
                        availableCurrencies = availableCurrencies
                    )
                }
            }
        }
    }

    @Composable
    private fun DebtEditContent(
        isNew: Boolean,
        initialLabel: String,
        initialDescription: String,
        initialAmount: String,
        initialIsOwesMe: Boolean,
        initialCurrency: CurrencyUnit,
        initialDate: LocalDate,
        initialEquivalent: String,
        availableCurrencies: List<org.totschnig.myexpenses.viewmodel.data.Currency>
    ) {
        DebtEditScreen(
            isNewDebt = isNew,
            payeeName = payeeName,
            initialLabel = initialLabel,
            initialDescription = initialDescription,
            initialAmount = initialAmount,
            initialIsOwesMe = initialIsOwesMe,
            initialCurrencyUnit = initialCurrency,
            initialDate = initialDate,
            initialEquivalentAmount = initialEquivalent,
            availableCurrencies = availableCurrencies,
            homeCurrency = homeCurrency,
            currencyContextLookup = { code -> currencyContext[code] },
            onSave = { label, description, amountMajor, currencyUnit, date, equivalentAmountMajor ->
                val money = Money.buildWithMajor(currencyUnit, amountMajor).getOrNull()
                if (money == null) {
                    showSnackBar(getString(R.string.number_too_large))
                    return@DebtEditScreen
                }

                val equivalentMoney = if (currencyUnit.code != homeCurrency.code && equivalentAmountMajor != null) {
                    Money.buildWithMajor(homeCurrency, equivalentAmountMajor).getOrNull()
                } else null

                val debt = Debt(
                    id = debtId,
                    label = label,
                    description = description,
                    payeeId = payeeId,
                    amount = money.amountMinor,
                    currency = currencyUnit,
                    date = date,
                    equivalentAmount = equivalentMoney?.amountMinor
                )

                viewModel.saveDebt(debt).observe(this) {
                    finish()
                }
            },
            onClose = { finish() }
        )
    }
}
