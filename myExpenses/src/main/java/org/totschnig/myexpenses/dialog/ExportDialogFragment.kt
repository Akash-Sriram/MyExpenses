package org.totschnig.myexpenses.dialog

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseMyExpenses
import org.totschnig.myexpenses.compose.ButtonRow
import org.totschnig.myexpenses.export.AbstractExporter.Companion.ENCODING_LATIN_1
import org.totschnig.myexpenses.export.AbstractExporter.Companion.ENCODING_UTF_8
import org.totschnig.myexpenses.export.AbstractExporter.Companion.ENCODING_UTF_8_BOM
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.ExportFormat
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.enumValueOrDefault
import org.totschnig.myexpenses.provider.DataBaseAccount.Companion.HOME_AGGREGATE_ID
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_FLAG
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.timeFormatterPattern
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.EXPORT_HANDLE_DELETED_CREATE_HELPER
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.EXPORT_HANDLE_DELETED_DO_NOTHING
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.EXPORT_HANDLE_DELETED_UPDATE_BALANCE
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_DATE_FORMAT
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_DECIMAL_SEPARATOR
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_DELETE_P
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_DELIMITER
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_ENCODING
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_EXPORT_HANDLE_DELETED
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_FILE_NAME
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_FORMAT
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_MERGE_P
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_NOT_YET_EXPORTED_P
import org.totschnig.myexpenses.viewmodel.ExportViewModel.Companion.KEY_TIME_FORMAT
import java.io.Serializable
import java.time.LocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

class ExportDialogFragment : ComposeBaseDialogFragment3() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as MyApplication).appComponent.inject(this)
    }

    override val title: CharSequence?
        get() = getString(R.string.menu_export)

    private val splitDateTimeActive: Boolean
        get() = prefHandler.getBoolean(PrefKey.CSV_EXPORT_SPLIT_DATE_TIME, false)

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun ColumnScope.MainContent() {
        val accountInfo = requireArguments().getSerializable(KEY_DATA, AccountInfo::class.java)!!

        val now = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).format(LocalDateTime.now())
        val defaultFileName = when (accountInfo.id) {
            HOME_AGGREGATE_ID -> "export-$now"
            0L -> when (AccountGrouping.valueOf(accountInfo.aggregate!!.first)) {
                AccountGrouping.CURRENCY -> "export-${accountInfo.currency}-$now"
                AccountGrouping.FLAG -> "export-FLAG-${accountInfo.aggregate.second!!.second}-$now"
                AccountGrouping.NONE -> "export-$now"
                AccountGrouping.TYPE -> "export-TYPE-${accountInfo.aggregate.second!!.second}-$now"
            }
            else -> if (accountInfo.id < 0L) {
                "export-${accountInfo.currency}-$now"
            } else {
                Utils.escapeForFileName(accountInfo.label) + "-" + now
            }
        }

        var fileName by remember { mutableStateOf(defaultFileName) }
        var format by remember {
            mutableStateOf(prefHandler.enumValueOrDefault(PrefKey.EXPORT_FORMAT, ExportFormat.CSV))
        }

        val defaultDateFormat = prefHandler.getString(PREF_KEY_EXPORT_DATE_FORMAT, "")
            ?.takeIf {
                it.isNotEmpty() && (try {
                    DateTimeFormatter.ofPattern(it)
                } catch (_: IllegalArgumentException) {
                    null
                }) != null
            } ?: DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE, Locale.getDefault()
        )
        var dateFormat by remember { mutableStateOf(defaultDateFormat) }

        val defaultTimeFormat = prefHandler.getString(PREF_KEY_EXPORT_TIME_FORMAT, "")
            ?.takeIf {
                it.isNotEmpty() && (try {
                    DateTimeFormatter.ofPattern(it)
                } catch (_: IllegalArgumentException) {
                    null
                }) != null
            } ?: requireContext().timeFormatterPattern
        var timeFormat by remember { mutableStateOf(defaultTimeFormat) }

        var encoding by remember {
            mutableStateOf(prefHandler.getString(PREF_KEY_EXPORT_ENCODING, ENCODING_UTF_8) ?: ENCODING_UTF_8)
        }

        var delimiter by remember {
            mutableStateOf(prefHandler.getInt(KEY_DELIMITER, ','.code).toChar())
        }

        var decimalSeparator by remember {
            mutableStateOf(prefHandler.getInt(KEY_DECIMAL_SEPARATOR, '.'.code).toChar())
        }

        var mergeAccounts by remember {
            mutableStateOf(prefHandler.getBoolean(KEY_MERGE_P, false))
        }

        var exportNotYetExported by remember { mutableStateOf(accountInfo.hasExported) }
        var exportDelete by remember { mutableStateOf(false) }
        var handleDeleted by remember {
            mutableIntStateOf(
                prefHandler.getInt(KEY_EXPORT_HANDLE_DELETED, EXPORT_HANDLE_DELETED_DO_NOTHING)
            )
        }

        val isFileNameValid = fileName.isNotBlank() && !fileName.contains("/")
        val isDateFormatValid = try {
            DateTimeFormatter.ofPattern(dateFormat)
            true
        } catch (_: Exception) {
            false
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Target Account Info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (accountInfo.id == HOME_AGGREGATE_ID) "All Accounts" else accountInfo.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (accountInfo.isFiltered) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dialog_export_applied_to_filter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // File Name Input
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(stringResource(R.string.file_name)) },
                isError = !isFileNameValid,
                supportingText = {
                    if (!isFileNameValid) {
                        Text(
                            text = if (fileName.contains("/")) "Slash (/) not allowed in file name" else "File name is required",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (accountInfo.id <= 0 && accountInfo.id != HOME_AGGREGATE_ID) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { mergeAccounts = !mergeAccounts }
                ) {
                    Checkbox(
                        checked = mergeAccounts,
                        onCheckedChange = { mergeAccounts = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.cb_label_merge_accounts),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Format Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.pref_export_format_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.name) }
                        )
                    }
                }
            }

            // CSV Delimiter & Decimal Separator (if CSV)
            if (format == ExportFormat.CSV) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Delimiter",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = delimiter == ',',
                            onClick = { delimiter = ',' },
                            label = { Text("Comma (,)") }
                        )
                        FilterChip(
                            selected = delimiter == ';',
                            onClick = { delimiter = ';' },
                            label = { Text("Semicolon (;)") }
                        )
                        FilterChip(
                            selected = delimiter == '\t',
                            onClick = { delimiter = '\t' },
                            label = { Text("Tab") }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.decimal_separator),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = decimalSeparator == '.',
                            onClick = { decimalSeparator = '.' },
                            label = { Text("Point (.)") }
                        )
                        FilterChip(
                            selected = decimalSeparator == ',',
                            onClick = { decimalSeparator = ',' },
                            label = { Text("Comma (,)") }
                        )
                    }
                }
            }

            // Encoding
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Encoding",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = encoding == ENCODING_UTF_8,
                        onClick = { encoding = ENCODING_UTF_8 },
                        label = { Text("UTF-8") }
                    )
                    if (format == ExportFormat.CSV) {
                        FilterChip(
                            selected = encoding == ENCODING_UTF_8_BOM,
                            onClick = { encoding = ENCODING_UTF_8_BOM },
                            label = { Text("UTF-8 BOM") }
                        )
                    }
                    FilterChip(
                        selected = encoding == ENCODING_LATIN_1,
                        onClick = { encoding = ENCODING_LATIN_1 },
                        label = { Text("Latin-1 (ISO-8859-1)") }
                    )
                }
            }

            // Date Format
            OutlinedTextField(
                value = dateFormat,
                onValueChange = { dateFormat = it },
                label = { Text("Date Format") },
                isError = !isDateFormatValid,
                supportingText = {
                    if (!isDateFormatValid) {
                        Text(
                            text = stringResource(R.string.date_format_illegal),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Checkboxes: Not yet exported & Delete after export
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (!exportDelete) exportNotYetExported = !exportNotYetExported
                    }
                ) {
                    Checkbox(
                        checked = exportNotYetExported,
                        onCheckedChange = { exportNotYetExported = it },
                        enabled = !exportDelete
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dialog_export_not_yet_exported),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        exportDelete = !exportDelete
                        if (exportDelete) {
                            exportNotYetExported = false
                        }
                    }
                ) {
                    Checkbox(
                        checked = exportDelete,
                        onCheckedChange = {
                            exportDelete = it
                            if (it) exportNotYetExported = false
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dialog_export_delete),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (exportDelete) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Warning and deletion strategy
            AnimatedVisibility(visible = exportDelete) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exported transactions will be permanently deleted from account!",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Account Balance Strategy:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                handleDeleted = EXPORT_HANDLE_DELETED_DO_NOTHING
                            }
                        ) {
                            RadioButton(
                                selected = handleDeleted == EXPORT_HANDLE_DELETED_DO_NOTHING,
                                onClick = { handleDeleted = EXPORT_HANDLE_DELETED_DO_NOTHING }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Do nothing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                handleDeleted = EXPORT_HANDLE_DELETED_UPDATE_BALANCE
                            }
                        ) {
                            RadioButton(
                                selected = handleDeleted == EXPORT_HANDLE_DELETED_UPDATE_BALANCE,
                                onClick = { handleDeleted = EXPORT_HANDLE_DELETED_UPDATE_BALANCE }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Update initial balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                handleDeleted = EXPORT_HANDLE_DELETED_CREATE_HELPER
                            }
                        ) {
                            RadioButton(
                                selected = handleDeleted == EXPORT_HANDLE_DELETED_CREATE_HELPER,
                                onClick = { handleDeleted = EXPORT_HANDLE_DELETED_CREATE_HELPER }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Create balance transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            ButtonRow {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    enabled = isFileNameValid && isDateFormatValid,
                    onClick = {
                        with(prefHandler) {
                            putString(PrefKey.EXPORT_FORMAT, format.name)
                            putString(PREF_KEY_EXPORT_DATE_FORMAT, dateFormat)
                            if (splitDateTimeActive) {
                                putString(PREF_KEY_EXPORT_TIME_FORMAT, timeFormat)
                            }
                            putString(PREF_KEY_EXPORT_ENCODING, encoding)
                            putInt(KEY_DECIMAL_SEPARATOR, decimalSeparator.code)
                            putInt(KEY_DELIMITER, delimiter.code)
                            putInt(KEY_EXPORT_HANDLE_DELETED, handleDeleted)
                        }

                        val exportBundle = Bundle().apply {
                            putInt(
                                ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
                                R.id.START_EXPORT_COMMAND
                            )
                            if (accountInfo.id > 0) {
                                putLong(KEY_ROWID, accountInfo.id)
                            } else {
                                if (accountInfo.id != HOME_AGGREGATE_ID) {
                                    if (accountInfo.id < 0) {
                                        putString(KEY_CURRENCY, accountInfo.currency)
                                    } else {
                                        when (AccountGrouping.valueOf(accountInfo.aggregate!!.first)) {
                                            AccountGrouping.CURRENCY -> putString(KEY_CURRENCY, accountInfo.currency)
                                            AccountGrouping.FLAG -> putLong(KEY_FLAG, accountInfo.aggregate.second!!.second!!)
                                            AccountGrouping.NONE -> {}
                                            AccountGrouping.TYPE -> putLong(KEY_TYPE, accountInfo.aggregate.second!!.second!!)
                                        }
                                    }
                                }
                                putBoolean(KEY_MERGE_P, mergeAccounts)
                                prefHandler.putBoolean(KEY_MERGE_P, mergeAccounts)
                            }
                            putSerializable(KEY_FORMAT, format)
                            putBoolean(KEY_DELETE_P, exportDelete)
                            putBoolean(KEY_NOT_YET_EXPORTED_P, exportNotYetExported)
                            putString(KEY_DATE_FORMAT, dateFormat)
                            if (splitDateTimeActive) {
                                putString(KEY_TIME_FORMAT, timeFormat)
                            }
                            putChar(KEY_DECIMAL_SEPARATOR, decimalSeparator)
                            putString(KEY_ENCODING, encoding)
                            putInt(KEY_EXPORT_HANDLE_DELETED, handleDeleted)
                            putString(KEY_FILE_NAME, fileName)
                            putChar(KEY_DELIMITER, delimiter)
                        }

                        (requireActivity() as? BaseMyExpenses<*>)?.startExport(exportBundle)
                        dismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.menu_export),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    data class AccountInfo(
        val id: Long,
        val label: String,
        val currency: String,
        val cannotResetConditions: List<Int>,
        val hasExported: Boolean,
        val isFiltered: Boolean,
        val aggregate: Pair<String, Pair<String, Long?>?>? = null
    ) : Serializable

    companion object {
        private const val KEY_DATA = "data"
        const val PREF_KEY_EXPORT_DATE_FORMAT = "export_date_format"
        const val PREF_KEY_EXPORT_TIME_FORMAT = "export_time_format"
        const val PREF_KEY_EXPORT_ENCODING = "export_encoding"

        fun newInstance(accountInfo: AccountInfo) = ExportDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(KEY_DATA, accountInfo)
            }
        }
    }
}
