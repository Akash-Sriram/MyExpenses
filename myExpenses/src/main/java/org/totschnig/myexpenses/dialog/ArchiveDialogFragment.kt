package org.totschnig.myexpenses.dialog

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.DateRangePickerScaffold
import org.totschnig.myexpenses.compose.rememberDefaultDateRangePickerState
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.archive
import org.totschnig.myexpenses.db2.canBeArchived
import org.totschnig.myexpenses.db2.getAccountTransactionDateBounds
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.util.epochMillis2LocalDate
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@Parcelize
data class ArchiveInfo(
    val count: Int,
    val hasNested: Boolean,
    val statuses: List<CrStatus>,
    val accountType: AccountType,
) : Parcelable {
    val canArchive: Boolean
        get() = count > 0 &&
                !hasNested &&
                (!accountType.supportsReconciliation || statuses.filter { it != CrStatus.VOID }.size <= 1)
}

@OptIn(ExperimentalMaterial3Api::class)
class ArchiveDialogFragment : ComposeBaseDialogFragment2() {
    @Inject
    lateinit var repository: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(this)
    }

    override val fullScreenIfNotLarge = true

    val accountId: Long
        get() = requireArguments().getLong(KEY_ACCOUNTID)

    val DateRangePickerState.range: Pair<LocalDate, LocalDate>?
        get() = selectedStartDateMillis?.let { start ->
            selectedEndDateMillis?.let { end ->
                epochMillis2LocalDate(start, ZoneOffset.UTC) to
                        epochMillis2LocalDate(end, ZoneOffset.UTC)
            }
        }

    @Composable
    override fun BuildContent() {
        var dateBounds by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
        var isBoundsLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(accountId) {
            val bounds = withContext(Dispatchers.IO) {
                repository.getAccountTransactionDateBounds(accountId)
            }
            dateBounds = bounds
            isBoundsLoaded = true
        }

        if (!isBoundsLoaded) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return
        }

        val bounds = dateBounds
        val minYear = bounds?.first?.year ?: 1970
        val maxYear = bounds?.second?.year ?: LocalDate.now(ZoneOffset.UTC).year

        // Pre-select the full range or the previous completed year if available
        val initialStartMillis = remember(bounds) {
            bounds?.first?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }
        val initialEndMillis = remember(bounds) {
            bounds?.second?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }

        val state = rememberDefaultDateRangePickerState(
            initialSelectedStartDateMillis = initialStartMillis,
            initialSelectedEndDateMillis = initialEndMillis,
            initialYearRange = (minYear..maxYear)
        )

        var archiveInfo by remember { mutableStateOf<ArchiveInfo?>(null) }

        LaunchedEffect(state) {
            snapshotFlow { state.range }.collect {
                archiveInfo = null
                it?.let { range ->
                    archiveInfo = withContext(Dispatchers.IO) {
                        repository.canBeArchived(accountId, range)
                    }
                }
            }
        }

        DateRangePickerScaffold(
            confirmButtonText = stringResource(R.string.archive),
            state = state,
            headerContent = {
                if (bounds != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentYear = LocalDate.now(ZoneOffset.UTC).year
                        val prevYear = currentYear - 1
                        if (minYear <= prevYear) {
                            SuggestionChip(
                                onClick = {
                                    val start = LocalDate.of(prevYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                    val end = LocalDate.of(prevYear, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                    state.setSelection(start, end)
                                },
                                label = { Text("FY $prevYear") }
                            )
                        }
                        SuggestionChip(
                            onClick = {
                                initialStartMillis?.let { s ->
                                    initialEndMillis?.let { e ->
                                        state.setSelection(s, e)
                                    }
                                }
                            },
                            label = { Text("All Active ($minYear–$maxYear)") }
                        )
                    }
                }
            },
            warning = archiveInfo?.let { info ->
                when {
                    info.canArchive -> stringResource(R.string.archive_warning, info.count)
                    info.hasNested -> stringResource(R.string.warning_nested_archives)
                    info.count == 0 -> stringResource(R.string.warning_empty_archive)
                    info.statuses.size > 1 -> stringResource(
                        R.string.warning_archive_inconsistent_state,
                        info.statuses.joinToString {
                            getString(it.toStringRes())
                        })

                    else -> throw IllegalStateException()
                }
            },
            enabled = archiveInfo?.canArchive == true
        ) {
            repository.archive(accountId, it)
        }
    }

    companion object {
        fun newInstance(account: FullAccount) = ArchiveDialogFragment().apply {
            arguments = Bundle().apply {
                putLong(KEY_ACCOUNTID, account.id)
                putString(KEY_LABEL, account.label)
            }
        }
    }
}