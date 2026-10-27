package org.totschnig.myexpenses.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.ButtonRow
import org.totschnig.myexpenses.provider.filter.DateCriterion
import org.totschnig.myexpenses.provider.filter.KEY_CRITERION
import org.totschnig.myexpenses.provider.filter.Operation
import org.totschnig.myexpenses.provider.filter.criterion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DateFilterDialog : ComposeBaseDialogFragment3() {

    private var isConfirmed = false

    override val title: CharSequence?
        get() = getString(R.string.search_date)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ColumnScope.MainContent() {
        val initialCriterion = remember {
            requireArguments().criterion(DateCriterion::class.java)
        }

        var operation by remember {
            mutableStateOf(initialCriterion?.operation ?: Operation.GT)
        }
        var date1 by remember {
            mutableStateOf(initialCriterion?.values?.getOrNull(0) ?: LocalDate.now())
        }
        var date2 by remember {
            mutableStateOf(initialCriterion?.values?.getOrNull(1) ?: LocalDate.now())
        }

        var activeDatePickerTarget by remember { mutableStateOf<Int?>(null) }

        activeDatePickerTarget?.let { target ->
            val currentDate = if (target == 1) date1 else date2
            val initialMillis = remember(currentDate) {
                currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

            DatePickerDialog(
                onDismissRequest = { activeDatePickerTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            if (target == 1) date1 = picked else date2 = picked
                        }
                        activeDatePickerTarget = null
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDatePickerTarget = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Operator Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = operation == Operation.GT,
                    onClick = { operation = Operation.GT },
                    label = { Text(stringResource(R.string.comparison_operator_date_after)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = operation == Operation.LT,
                    onClick = { operation = Operation.LT },
                    label = { Text(stringResource(R.string.comparison_operator_date_before)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = operation == Operation.BTW,
                    onClick = { operation = Operation.BTW },
                    label = { Text(stringResource(R.string.comparison_operator_between)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Date 1 Card
            DateDisplayCard(
                label = if (operation == Operation.BTW) "From Date" else "Date",
                date = date1,
                onClick = { activeDatePickerTarget = 1 }
            )

            // Date 2 Card (if Between)
            if (operation == Operation.BTW) {
                DateDisplayCard(
                    label = "To Date",
                    date = date2,
                    onClick = { activeDatePickerTarget = 2 }
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
                    isConfirmed = true
                    val criterion = if (operation == Operation.BTW) {
                        DateCriterion(date1, date2)
                    } else {
                        DateCriterion(operation, date1)
                    }
                    parentFragmentManager.confirmFilter(requestKey, criterion)
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
        fun newInstance(requestKey: String, dateCriterion: DateCriterion?) = DateFilterDialog().apply {
            arguments = configureArguments(requestKey).apply {
                putParcelable(KEY_CRITERION, dateCriterion)
            }
        }
    }
}

@Composable
private fun DateDisplayCard(
    label: String,
    date: LocalDate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
