package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.methods.MethodsScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.MethodViewModel

class ManageMethods : ProtectedFragmentActivity() {

    private val viewModel by viewModels<MethodViewModel>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        setContent {
            AppTheme {
                val methods = viewModel.paymentMethods
                    .collectAsStateWithLifecycle(initialValue = emptyList()).value

                MethodsScreen(
                    methods = methods,
                    onNewMethod = {
                        startActivity(Intent(this, MethodEdit::class.java))
                    },
                    onEditMethod = { methodId ->
                        startActivity(Intent(this, MethodEdit::class.java).apply {
                            putExtra(KEY_ROWID, methodId)
                        })
                    },
                    onDeleteMethod = { method ->
                        viewModel.deleteMethods(listOf(method.id))
                        Toast.makeText(this, "Deleted \"${method.label}\"", Toast.LENGTH_SHORT).show()
                    },
                    onClose = {
                        finish()
                    }
                )
            }
        }
    }

    fun deleteMethods(methodsIs: List<Long>) {
        viewModel.deleteMethods(methodsIs)
    }
}
