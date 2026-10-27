package org.totschnig.myexpenses.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener
import eltos.simpledialogfragment.color.SimpleColorDialog
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.accounts.AccountEditScreen
import org.totschnig.myexpenses.dialog.buildColorDialog
import org.totschnig.myexpenses.fragment.TagList.Companion.KEY_TAG_LIST
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.provider.KEY_COLOR
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.filter.KEY_SELECTION
import org.totschnig.myexpenses.util.calculateRawExchangeRate
import org.totschnig.myexpenses.util.calculateRealExchangeRate
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.AccountEditViewModel
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.TagBaseViewModel
import org.totschnig.myexpenses.viewmodel.data.Tag
import java.math.BigDecimal

class AccountEdit : ProtectedFragmentActivity(), OnDialogResultListener {

    private val viewModel: AccountEditViewModel by viewModels()
    private val currencyViewModel: CurrencyViewModel by viewModels()

    val rowId: Long
        get() = intent.getLongExtra(KEY_ROWID, 0L)

    private val selectTagsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            data?.getLongArrayExtra(TagBaseViewModel.KEY_DELETED_IDS)?.let {
                viewModel.removeTags(*it)
            }
            if (result.resultCode == RESULT_OK && data != null) {
                androidx.core.content.IntentCompat.getParcelableArrayListExtra(
                    data,
                    KEY_TAG_LIST,
                    Tag::class.java
                )?.let {
                    viewModel.updateTags(it, true)
                }
            }
        }

    private var onCustomColorChosen: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(injector) {
            inject(viewModel)
            inject(currencyViewModel)
        }

        if (rowId != 0L) {
            viewModel.loadTags(rowId)
        }

        setContent {
            AppTheme {
                val availableCurrencies by currencyViewModel.currencies.collectAsStateWithLifecycle(emptyList())
                val availableAccountTypes by viewModel.accountTypes.collectAsStateWithLifecycle(emptyList())
                val tags by viewModel.tagsLiveData.observeAsState(emptyList())

                if (rowId != 0L) {
                    val loadedAccount by viewModel.loadAccount(rowId).observeAsState()
                    if (loadedAccount == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val account = loadedAccount!!
                        AccountEditContent(
                            isNew = false,
                            account = account,
                            availableCurrencies = availableCurrencies,
                            availableAccountTypes = availableAccountTypes,
                            tags = tags
                        )
                    }
                } else {
                    val defaultCurrencyCode = currencyViewModel.default.code
                    val defaultType = availableAccountTypes.firstOrNull { it.isCashAccount }
                        ?: availableAccountTypes.firstOrNull()
                        ?: AccountType.CASH
                    val initialColor = intent.getIntExtra(KEY_COLOR, Account.DEFAULT_COLOR)

                    AccountEditContent(
                        isNew = true,
                        account = Account(
                            currency = defaultCurrencyCode,
                            type = defaultType,
                            color = initialColor
                        ),
                        availableCurrencies = availableCurrencies,
                        availableAccountTypes = availableAccountTypes,
                        tags = tags
                    )
                }
            }
        }
    }

    @Composable
    private fun AccountEditContent(
        isNew: Boolean,
        account: Account,
        availableCurrencies: List<org.totschnig.myexpenses.viewmodel.data.Currency>,
        availableAccountTypes: List<AccountType>,
        tags: List<Tag>
    ) {
        val currencyUnit = remember(account.currency) { currencyContext[account.currency] }
        val balanceMoney = remember(account.openingBalance, currencyUnit) {
            Money(currencyUnit, account.openingBalance)
        }
        val initialOpeningBalance = remember(balanceMoney) {
            balanceMoney.amountMajor.abs().toPlainString()
        }
        val initialIsNegative = remember(account.openingBalance) {
            account.openingBalance < 0
        }

        val initialCriterionStr = remember(account.criterion, currencyUnit) {
            account.criterion?.let { Money(currencyUnit, it).amountMajor.abs().toPlainString() } ?: ""
        }
        val initialCriterionNegative = remember(account.criterion) {
            (account.criterion ?: 0L) < 0
        }

        val initialExchangeRateStr = remember(account.exchangeRate, currencyUnit, homeCurrency) {
            calculateRealExchangeRate(account.exchangeRate, currencyUnit, homeCurrency).toPlainString()
        }

        var currentColor by remember { mutableIntStateOf(account.color) }

        AccountEditScreen(
            isNewAccount = isNew,
            initialLabel = account.label,
            initialDescription = account.description,
            initialOpeningBalance = initialOpeningBalance,
            initialIsOpeningBalanceNegative = initialIsNegative,
            initialCurrencyCode = account.currency,
            initialAccountType = account.type ?: availableAccountTypes.firstOrNull() ?: AccountType.CASH,
            initialColor = currentColor,
            initialCriterion = initialCriterionStr,
            initialIsCriterionNegative = initialCriterionNegative,
            initialExchangeRate = initialExchangeRateStr,
            initialExcludeFromTotals = account.excludeFromTotals,
            initialDynamicExchangeRates = account.dynamicExchangeRates,
            availableCurrencies = availableCurrencies,
            availableAccountTypes = availableAccountTypes,
            homeCurrency = homeCurrency,
            currencyContextLookup = { code -> currencyContext[code] },
            tags = tags,
            onSelectTags = {
                val intent = Intent(this, ManageTags::class.java).apply {
                    forwardDataEntryFromWidget(this)
                    viewModel.tagsLiveData.value?.let { tagList ->
                        putExtra(KEY_SELECTION, tagList.map { it.id }.toLongArray())
                    }
                    putExtra(KEY_COLOR, currentColor)
                }
                selectTagsLauncher.launch(intent)
            },
            onRemoveTag = { tag ->
                viewModel.removeTag(tag)
            },
            onColorPaletteRequested = {
                onCustomColorChosen = { newColor ->
                    currentColor = newColor
                }
                buildColorDialog(this, currentColor).show(supportFragmentManager, EDIT_COLOR_DIALOG)
            },
            onSave = { label, description, openingBalance, currencyCode, accountType, chosenColor, criterion, exchangeRate, excludeFromTotals, dynamicExchangeRates ->
                val selectedUnit = currencyContext[currencyCode]
                val money = Money.buildWithMajor(selectedUnit, openingBalance).getOrNull()
                if (money == null) {
                    showSnackBar(getString(R.string.number_too_large))
                    return@AccountEditScreen
                }

                val criterionMinor = criterion?.let {
                    Money.buildWithMajor(selectedUnit, it).getOrNull()?.amountMinor
                }

                val isFx = currencyCode != homeCurrency.code
                val rawRate = if (isFx) {
                    calculateRawExchangeRate(BigDecimal.valueOf(exchangeRate), selectedUnit, homeCurrency)
                } else 1.0

                viewModel.currencyUnit = selectedUnit
                viewModel.accountType = accountType.id
                viewModel.excludeFromTotals = excludeFromTotals
                viewModel.dynamicExchangeRates = dynamicExchangeRates

                val accountToSave = Account(
                    id = rowId,
                    label = label,
                    currency = currencyCode,
                    openingBalance = money.amountMinor,
                    description = description,
                    type = accountType,
                    color = chosenColor,
                    uuid = viewModel.uuid,
                    syncAccountName = null, // stripped obsolete sync
                    criterion = criterionMinor,
                    excludeFromTotals = excludeFromTotals,
                    dynamicExchangeRates = dynamicExchangeRates && isFx,
                    exchangeRate = rawRate
                )

                viewModel.save(accountToSave).observe(this) { result ->
                    result.onFailure { error ->
                        CrashHandler.report(error)
                        showSnackBar(error.safeMessage)
                    }.onSuccess { (savedId, _) ->
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(KEY_ROWID, savedId)
                        })
                        finish()
                    }
                }
            },
            onClose = { finish() }
        )
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (EDIT_COLOR_DIALOG == dialogTag && which == BUTTON_POSITIVE) {
            val pickedColor = extras.getInt(SimpleColorDialog.COLOR)
            color = pickedColor
            onCustomColorChosen?.invoke(pickedColor)
            return true
        }
        return false
    }

    companion object {
        class CreateContract : ActivityResultContract<Unit, Long?>() {
            override fun createIntent(context: Context, input: Unit): Intent {
                return Intent(context, AccountEdit::class.java).apply {
                    putExtra(KEY_COLOR, Account.DEFAULT_COLOR)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Long? {
                if (resultCode != RESULT_OK) {
                    return null
                }
                return intent?.getLongExtra(KEY_ROWID, -1L)?.takeIf { it != -1L }
            }
        }
    }
}
