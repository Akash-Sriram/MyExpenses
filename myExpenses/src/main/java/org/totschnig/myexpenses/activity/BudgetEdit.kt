package org.totschnig.myexpenses.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.BudgetEditScreen
import org.totschnig.myexpenses.dialog.KEY_RESULT_FILTER
import org.totschnig.myexpenses.dialog.select.SelectCrStatusDialogFragment
import org.totschnig.myexpenses.dialog.select.SelectMethodsAllDialogFragment
import org.totschnig.myexpenses.dialog.select.SelectMultipleAccountDialogFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.DataBaseAccount
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.filter.AccountCriterion
import org.totschnig.myexpenses.provider.filter.CategoryCriterion
import org.totschnig.myexpenses.provider.filter.CrStatusCriterion
import org.totschnig.myexpenses.provider.filter.MethodCriterion
import org.totschnig.myexpenses.provider.filter.PayeeCriterion
import org.totschnig.myexpenses.provider.filter.SimpleCriterion
import org.totschnig.myexpenses.provider.filter.TagCriterion
import org.totschnig.myexpenses.viewmodel.BudgetEditViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import org.totschnig.myexpenses.viewmodel.data.Budget
import java.math.BigDecimal
import java.time.LocalDate

class BudgetEdit : ProtectedFragmentActivity() {

    private val viewModel: BudgetEditViewModel by viewModels()
    val filterRequestKey = "confirmFilterBudget"

    private val budgetId: Long
        get() = intent.extras?.getLong(KEY_ROWID) ?: 0L

    private inline fun buildLauncher(createContract: () -> PickObjectContract) =
        registerForActivityResult(createContract()) { criterion ->
            criterion?.let { addFilterCriterion(it) }
        }

    private val getCategory = buildLauncher(::PickCategoryContract)
    private val getPayee = buildLauncher(::PickPayeeContract)
    private val getTags = buildLauncher(::PickTagContract)

    fun addFilterCriterion(c: SimpleCriterion<*>) {
        viewModel.addFilterCriterion(c)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        if (budgetId != 0L) {
            viewModel.initWith(budgetId)
        }

        viewModel.databaseResult.observe(this) { result ->
            if (result > -1) {
                finish()
            } else {
                Toast.makeText(this, "Error while saving budget", Toast.LENGTH_LONG).show()
            }
        }

        supportFragmentManager.setFragmentResultListener(filterRequestKey, this) { _, result ->
            BundleCompat.getParcelable(result, KEY_RESULT_FILTER, SimpleCriterion::class.java)
                ?.let {
                    addFilterCriterion(it)
                }
        }

        setContent {
            AppTheme {
                val accounts by viewModel.accountsMinimal().collectAsStateWithLifecycle(emptyList())
                val criteria by viewModel.criteria.collectAsStateWithLifecycle(emptyList())

                if (accounts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (budgetId != 0L) {
                    val loadedBudget by viewModel.budget(budgetId).observeAsState()
                    if (loadedBudget == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val b = loadedBudget!!
                        BudgetEditScreen(
                            isNewBudget = false,
                            initialTitle = b.title,
                            initialDescription = b.description ?: "",
                            initialAmount = "",
                            initialAccountId = b.accountId,
                            initialGrouping = b.grouping,
                            initialStartDate = b.start ?: LocalDate.now(),
                            initialEndDate = b.end ?: LocalDate.now(),
                            initialIsDefault = b.default,
                            accounts = accounts,
                            currencyContextLookup = { code -> currencyContext[code] },
                            criteria = criteria.toList(),
                            onFilterClick = { id -> startFilterDialog(id, b.accountId, accounts) },
                            onRemoveFilter = { id -> viewModel.removeFilter(id) },
                            onSave = { title, description, _, accountId, grouping, start, end, isDefault ->
                                val acc = accounts.find { it.id == accountId } ?: accounts.first()
                                val budget = Budget(
                                    budgetId,
                                    acc.id,
                                    title,
                                    description,
                                    acc.currency,
                                    grouping,
                                    -1,
                                    start,
                                    end,
                                    null,
                                    isDefault
                                )
                                viewModel.saveBudget(budget, null)
                            },
                            onClose = { finish() }
                        )
                    }
                } else {
                    BudgetEditScreen(
                        isNewBudget = true,
                        initialTitle = "",
                        initialDescription = "",
                        initialAmount = "",
                        initialAccountId = accounts.first().id,
                        initialGrouping = Grouping.MONTH,
                        initialStartDate = LocalDate.now(),
                        initialEndDate = LocalDate.now(),
                        initialIsDefault = false,
                        accounts = accounts,
                        currencyContextLookup = { code -> currencyContext[code] },
                        criteria = criteria.toList(),
                        onFilterClick = { id -> startFilterDialog(id, accounts.first().id, accounts) },
                        onRemoveFilter = { id -> viewModel.removeFilter(id) },
                        onSave = { title, description, allocationAmount, accountId, grouping, start, end, isDefault ->
                            val acc = accounts.find { it.id == accountId } ?: accounts.first()
                            val currencyUnit = currencyContext[acc.currency]
                            val initialAmountMinor = allocationAmount?.let {
                                Money.buildWithMajor(currencyUnit, it).getOrNull()?.amountMinor
                            }
                            val budget = Budget(
                                0L,
                                acc.id,
                                title,
                                description,
                                acc.currency,
                                grouping,
                                -1,
                                start,
                                end,
                                null,
                                isDefault
                            )
                            viewModel.saveBudget(budget, initialAmountMinor)
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun startFilterDialog(id: Int, selectedAccountId: Long, accounts: List<AccountMinimal>) {
        val edit = viewModel.criteria.value.find { it.id == id }
        when (id) {
            R.id.FILTER_CATEGORY_COMMAND -> getCategory.launch(null to edit as? CategoryCriterion)
            R.id.FILTER_TAG_COMMAND -> getTags.launch(null to edit as? TagCriterion)
            R.id.FILTER_PAYEE_COMMAND -> getPayee.launch(null to edit as? PayeeCriterion)
            R.id.FILTER_METHOD_COMMAND -> {
                SelectMethodsAllDialogFragment.newInstance(
                    filterRequestKey,
                    edit as? MethodCriterion
                ).show(supportFragmentManager, "METHOD_FILTER")
            }
            R.id.FILTER_STATUS_COMMAND -> {
                SelectCrStatusDialogFragment.newInstance(
                    filterRequestKey,
                    edit as? CrStatusCriterion,
                    false
                ).show(supportFragmentManager, "STATUS_FILTER")
            }
            R.id.FILTER_ACCOUNT_COMMAND -> {
                val acc = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
                SelectMultipleAccountDialogFragment.newInstance(
                    filterRequestKey,
                    acc?.takeIf { !DataBaseAccount.isHomeAggregate(it.id) }?.currency,
                    edit as? AccountCriterion
                ).show(supportFragmentManager, "ACCOUNT_FILTER")
            }
        }
    }
}
