package org.totschnig.myexpenses.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.ButtonRow
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.filter.AmountCriterion
import org.totschnig.myexpenses.provider.filter.AmountCriterion.Companion.create
import org.totschnig.myexpenses.provider.filter.KEY_CRITERION
import org.totschnig.myexpenses.provider.filter.Operation
import org.totschnig.myexpenses.provider.filter.criterion

class AmountFilterDialog : ComposeBaseDialogFragment3() {

    private var isConfirmed = false

    val currency: CurrencyUnit by lazy {
        BundleCompat.getParcelable(requireArguments(), KEY_CURRENCY, CurrencyUnit::class.java)!!
    }

    override val title: CharSequence?
        get() = getString(R.string.search_amount)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ColumnScope.MainContent() {
        val initialCriterion = remember {
            requireArguments().criterion(AmountCriterion::class.java)
        }

        val transformed = remember { initialCriterion?.transformForUi() }

        var isIncome by remember {
            mutableStateOf(initialCriterion?.sign == true)
        }
        var operation by remember {
            mutableStateOf(transformed?.first ?: Operation.EQ)
        }
        var amount1Str by remember {
            mutableStateOf(
                transformed?.second?.getOrNull(0)?.let {
                    Money(currency, it).amountMajor.toPlainString()
                } ?: ""
            )
        }
        var amount2Str by remember {
            mutableStateOf(
                transformed?.second?.getOrNull(1)?.let {
                    Money(currency, it).amountMajor.toPlainString()
                } ?: ""
            )
        }
        var hasError by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Expense / Income Type Segmented Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isIncome,
                    onClick = { isIncome = false },
                    label = { Text(stringResource(R.string.expense)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isIncome,
                    onClick = { isIncome = true },
                    label = { Text(stringResource(R.string.income)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Operator Chips: EQ (=), GTE (≥), LTE (≤), BTW (Between)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = operation == Operation.EQ,
                    onClick = { operation = Operation.EQ },
                    label = { Text("=") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = operation == Operation.GTE,
                    onClick = { operation = Operation.GTE },
                    label = { Text("≥") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = operation == Operation.LTE,
                    onClick = { operation = Operation.LTE },
                    label = { Text("≤") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = operation == Operation.BTW,
                    onClick = { operation = Operation.BTW },
                    label = { Text("Between") },
                    modifier = Modifier.weight(1.5f)
                )
            }

            // Amount 1 Input
            OutlinedTextField(
                value = amount1Str,
                onValueChange = {
                    amount1Str = it
                    if (it.isNotBlank()) hasError = false
                },
                label = { Text(if (operation == Operation.BTW) "From Amount" else "Amount") },
                prefix = { Text(currency.symbol ?: currency.code, fontWeight = FontWeight.Bold) },
                isError = hasError,
                supportingText = if (hasError) {
                    { Text(stringResource(R.string.required), color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Amount 2 Input (if Between)
            if (operation == Operation.BTW) {
                OutlinedTextField(
                    value = amount2Str,
                    onValueChange = { amount2Str = it },
                    label = { Text("To Amount") },
                    prefix = { Text(currency.symbol ?: currency.code, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            ButtonRow {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val a1Major = amount1Str.trim().toBigDecimalOrNull()
                    if (a1Major == null) {
                        hasError = true
                        return@TextButton
                    }
                    val a1Money = Money.buildWithMajor(currency, a1Major).getOrNull()
                    if (a1Money == null) {
                        hasError = true
                        return@TextButton
                    }

                    val a2Money = if (operation == Operation.BTW) {
                        val a2Major = amount2Str.trim().toBigDecimalOrNull() ?: a1Major
                        Money.buildWithMajor(currency, a2Major).getOrNull()
                    } else null

                    isConfirmed = true
                    parentFragmentManager.confirmFilter(
                        requestKey,
                        create(
                            operation,
                            currency.code,
                            isIncome,
                            a1Money.amountMinor,
                            a2Money?.amountMinor
                        )
                    )
                    dismiss()
                }) {
                    Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isConfirmed) {
            parentFragmentManager.confirmFilter(requestKey, null)
        }
    }

    companion object {
        fun newInstance(requestKey: String, currency: CurrencyUnit?, amountCriterion: AmountCriterion?) =
            AmountFilterDialog().apply {
                arguments = configureArguments(requestKey).apply {
                    putParcelable(KEY_CURRENCY, currency)
                    putParcelable(KEY_CRITERION, amountCriterion)
                }
            }
    }
}
