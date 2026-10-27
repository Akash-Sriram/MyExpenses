package org.totschnig.myexpenses.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.methods.MethodEditScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model2.PAYMENT_METHOD_NEUTRAL
import org.totschnig.myexpenses.model2.PaymentMethod
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.MethodViewModel

class MethodEdit : ProtectedFragmentActivity() {
    private val viewModel by viewModels<MethodViewModel>()

    private val rowId: Long
        get() = intent.extras?.getLong(KEY_ROWID) ?: 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        setContent {
            AppTheme {
                val accountTypes by viewModel.accountTypes
                    .collectAsStateWithLifecycle()

                var loadedMethod by remember { mutableStateOf<PaymentMethod?>(null) }
                var isLoading by remember { mutableStateOf(rowId != 0L) }

                LaunchedEffect(rowId) {
                    if (rowId != 0L) {
                        loadedMethod = viewModel.loadPaymentMethod(rowId)
                        isLoading = false
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    MethodEditScreen(
                        isNew = rowId == 0L,
                        initialLabel = loadedMethod?.label ?: "",
                        initialType = loadedMethod?.type ?: PAYMENT_METHOD_NEUTRAL,
                        initialIsNumbered = loadedMethod?.isNumbered ?: false,
                        initialIcon = loadedMethod?.icon,
                        allAccountTypes = accountTypes,
                        initialSelectedAccountTypes = loadedMethod?.accountTypes ?: emptyList(),
                        onSave = { label, type, isNumbered, icon, selectedAccountTypes ->
                            lifecycleScope.launch {
                                viewModel.saveMethod(
                                    PaymentMethod(
                                        id = rowId,
                                        label = label,
                                        icon = icon,
                                        type = type,
                                        isNumbered = isNumbered,
                                        accountTypes = selectedAccountTypes,
                                        preDefinedPaymentMethod = loadedMethod?.preDefinedPaymentMethod
                                    )
                                )
                                Toast.makeText(
                                    this@MethodEdit,
                                    if (rowId == 0L) "Payment method created" else "Payment method updated",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        },
                        onClose = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
