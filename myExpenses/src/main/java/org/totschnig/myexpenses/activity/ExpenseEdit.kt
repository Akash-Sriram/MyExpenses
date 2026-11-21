package org.totschnig.myexpenses.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.transactions.ExpenseEditScreen
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.OPERATION_TYPE
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSFER
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel
import org.totschnig.myexpenses.viewmodel.data.Account
import org.totschnig.myexpenses.viewmodel.data.TransactionEditData
import org.totschnig.myexpenses.viewmodel.data.TransferEditData
import org.totschnig.myexpenses.viewmodel.data.toAccount
import java.time.LocalDate
import java.time.LocalDateTime

const val HELP_VARIANT_TRANSACTION = "transaction"
const val HELP_VARIANT_TRANSFER = "transfer"

open class ExpenseEdit : ProtectedFragmentActivity() {

    private lateinit var viewModel: MyExpensesV2ViewModel
    private val currencyViewModel: CurrencyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MyExpensesV2ViewModel::class.java]

        with(injector) {
            inject(this@ExpenseEdit)
            inject(viewModel)
            inject(currencyViewModel)
        }

        val extras = intent.extras
        val rowId = extras?.getLong(KEY_ROWID, 0L) ?: 0L
        val accountId = extras?.getLong(KEY_ACCOUNTID, 0L) ?: 0L
        val operationType = extras?.getInt(OPERATION_TYPE, 0) ?: 0
        val isIncome = extras?.getBoolean(KEY_INCOME, false) == true

        if (rowId != 0L) {
            viewModel.loadTransaction(rowId)
        }

        setContent {
            AppTheme {
                val accounts by viewModel.accountDataV2.collectAsStateWithLifecycle(null)
                val fullAccounts = accounts?.getOrNull()?.filter { !it.isPortfolio && !it.sealed } ?: emptyList()
                val targetAccounts = fullAccounts.map { it.toAccount() }

                val transactionToEdit by viewModel.transactionToEdit.collectAsStateWithLifecycle()

                val defaultAccount = fullAccounts.find { it.id == accountId }
                    ?: fullAccounts.firstOrNull()

                val editData = transactionToEdit ?: defaultAccount?.let { acc ->
                    TransactionEditData(
                        uuid = generateUuid(),
                        amount = Money(acc.currencyUnit, 0L),
                        accountId = acc.id,
                        date = LocalDateTime.now(),
                        valueDate = LocalDate.now(),
                        transferEditData = if (operationType == TYPE_TRANSFER) TransferEditData() else null
                    )
                }

                editData?.let { data ->
                    ExpenseEditScreen(
                        initialData = data,
                        accounts = targetAccounts,
                        quickCategories = viewModel.getQuickCategories(if (isIncome || data.amount.amountMajor.signum() == 1) FLAG_INCOME else FLAG_EXPENSE),
                        allTags = viewModel.getAllTags(),
                        onSave = { savedData ->
                            viewModel.saveTransaction(savedData)
                            finish()
                        },
                        onClose = {
                            finish()
                        },
                        onAddTag = { label ->
                            viewModel.createTag(label)
                        }
                    )
                }
            }
        }
    }

    open fun setAccounts(accounts: List<Account>, isInitialSetup: Boolean) {}

    companion object {
        const val KEY_NEW_TEMPLATE = "newTemplate"
        const val KEY_CLONE = "clone"
        const val KEY_CREATE_TEMPLATE = "createTemplate"
        const val KEY_AUTOFILL_MAY_SET_ACCOUNT = "autoFillMaySetAccount"
        const val KEY_OCR_RESULT = "ocrResult"
        const val KEY_EXTRACT_OCR = "extractOcr"
        const val KEY_INCOME = "isIncome"
        const val KEY_PARENT_HAS_DEBT = "parentHasDebt"
        const val KEY_PARENT_ORIGINAL_AMOUNT_EXCHANGE_RATE = "parentOriginalAmountExchangeRate"
        const val ACTION_CREATE_FROM_TEMPLATE = "CREATE_FROM_TEMPLATE"
        const val ACTION_CREATE_TEMPLATE_FROM_TRANSACTION = "TEMPLATE_FROM_TRANSACTION"
    }
}
