package org.totschnig.myexpenses.fragment.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.ListPreference.SimpleSummaryProvider
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.PriceHistory
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.DYNAMIC_EXCHANGE_RATES_DEFAULT_KEY
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.ACCOUNTS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.DYNAMIC_CURRENCIES_URI
import org.totschnig.myexpenses.util.TextUtils
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.data.Currency

@Keep
class PreferenceDataFragment : BasePreferenceFragment() {

    private val currencyViewModel: CurrencyViewModel by viewModels()

    override val preferencesResId = R.xml.preferences_data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(currencyViewModel)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        requirePreference<Preference>(PrefKey.HOME_CURRENCY).onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                val newHomeCurrency = newValue as String
                if (newHomeCurrency != prefHandler.getString(PrefKey.HOME_CURRENCY, null)) {
                    MessageDialogFragment.newInstance(
                        getString(R.string.pref_home_currency_title) + ": " + viewModel.currencyContext[newHomeCurrency].description ,
                        TextUtils.concatResStrings(
                            requireContext(),
                            R.string.recalculate_equivalent_amounts_warning,
                            R.string.continue_confirmation
                        ),
                        MessageDialogFragment.Button(
                            android.R.string.ok, R.id.CHANGE_COMMAND,
                            newHomeCurrency
                        ),
                        null, MessageDialogFragment.noButton()
                    ).show(parentFragmentManager, "CONFIRM")
                }
                false
            }

        requirePreference<Preference>(PrefKey.MANAGE_CURRENCIES).title =
            CommodityType.title(resources)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currencyViewModel.currencies.collect { currencies ->
                    with(requirePreference<ListPreference>(PrefKey.HOME_CURRENCY)) {
                        entries = currencies.map(Currency::toString).toTypedArray()
                        entryValues = currencies.map { it.code }.toTypedArray()
                        isEnabled = true
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasStaleImages.collect { result ->
                    requirePreference<Preference>(PrefKey.MANAGE_STALE_IMAGES).isVisible =
                        result
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currencyViewModel.usedCurrencies.collect { pairs ->
                    with(requirePreference<PreferenceCategory>(PrefKey.CATEGORY_PRICES)) {
                        isVisible = pairs.isNotEmpty()
                        removeAll()
                        pairs.forEach { (commodity, base) ->
                            Preference(requireContext()).apply {
                                // Show "BTC (USD)" or similar if not home currency
                                title = if (base.code == homeCurrency) commodity.description else "${commodity.description} (${base.code})"
                                intent = Intent(requireContext(), PriceHistory::class.java).apply {
                                    putExtra(KEY_COMMODITY, commodity.code)
                                    putExtra(KEY_CURRENCY, base.code) // Explicitly pass the base currency
                                }
                                addPreference(this)
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferenceDataStore.handleList(findPreference(DYNAMIC_EXCHANGE_RATES_DEFAULT_KEY)!!) {
                    requireContext().contentResolver.notifyChange(DYNAMIC_CURRENCIES_URI, null)
                    requireContext().contentResolver.notifyChange(ACCOUNTS_URI, null)
                }
            }
        }
    }

    val homeCurrency: String
        get() = currencyViewModel.currencyContext.homeCurrencyString

    fun updateHomeCurrency(currencyCode: String) {
        requirePreference<ListPreference>(PrefKey.HOME_CURRENCY).value = currencyCode
    }
}