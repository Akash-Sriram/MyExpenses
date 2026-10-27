package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.parties.PartiesScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.DebtViewModel
import org.totschnig.myexpenses.viewmodel.PartyListViewModel

const val HELP_VARIANT_MERGE_MODE = "mergeMode"

class ManageParties : DebtActivity() {
    fun setFabEnabled(enabled: Boolean) {}

    override val debtViewModel: DebtViewModel by viewModels()
    private val partyListViewModel: PartyListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(partyListViewModel)
        partyListViewModel.loadDebts().observe(this) {}

        val action = intent.asAction

        setContent {
            AppTheme {
                val parties by partyListViewModel.parties(hierarchical = true)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val unusedCount by partyListViewModel.unusedCount
                    .collectAsStateWithLifecycle()

                var searchQuery by remember { mutableStateOf(partyListViewModel.filter ?: "") }

                PartiesScreen(
                    action = action,
                    parties = parties,
                    unusedCount = unusedCount,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        partyListViewModel.filter = query
                    },
                    debtsLookup = { partyId ->
                        partyListViewModel.getDebts(partyId)
                    },
                    onPartySelected = { party ->
                        when (action) {
                            Action.SELECT_MAPPING -> {
                                setResult(Activity.RESULT_OK, Intent().apply {
                                    putExtra(KEY_ROWID, party.id)
                                    putExtra(KEY_LABEL, party.name)
                                    putExtra(KEY_PAYEE_NAME, party.name)
                                })
                                finish()
                            }
                            Action.SELECT_FILTER -> {
                                setResult(Activity.RESULT_FIRST_USER, Intent().apply {
                                    putExtra(KEY_ROWID, longArrayOf(party.id))
                                    putExtra(KEY_LABEL, party.name)
                                })
                                finish()
                            }
                            Action.MANAGE -> {
                                // Manage mode allows browsing / viewing
                            }
                        }
                    },
                    onSaveParty = { id, name, shortName, iban ->
                        partyListViewModel.saveParty(id ?: 0L, name, shortName, iban).observe(this@ManageParties) { success ->
                            if (!success) {
                                Toast.makeText(this@ManageParties, getString(R.string.already_defined, name), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ManageParties, if (id == null) "Payee added" else "Payee updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDeleteParty = { party ->
                        if (party.mappedTransactions || party.mappedTemplates) {
                            var message = ""
                            if (party.mappedTransactions) {
                                message += resources.getQuantityString(
                                    R.plurals.not_deletable_mapped_transactions, 1, 1
                                )
                            }
                            if (party.mappedTemplates) {
                                message += resources.getQuantityString(
                                    R.plurals.not_deletable_mapped_templates, 1, 1
                                )
                            }
                            Toast.makeText(this@ManageParties, message, Toast.LENGTH_LONG).show()
                        } else if (party.mappedDebts) {
                            Toast.makeText(this@ManageParties, getString(R.string.warning_party_delete_debt), Toast.LENGTH_LONG).show()
                        } else {
                            partyListViewModel.deleteParty(party.id).observe(this@ManageParties) { result ->
                                result.onSuccess { count ->
                                    Toast.makeText(
                                        this@ManageParties,
                                        resources.getQuantityString(R.plurals.delete_success, count, count),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { error ->
                                    showDeleteFailureFeedback(error.message)
                                }
                            }
                        }
                    },
                    onNewDebt = { party ->
                        startActivity(Intent(this@ManageParties, DebtEdit::class.java).apply {
                            putExtra(KEY_PAYEEID, party.id)
                            putExtra(KEY_PAYEE_NAME, party.name)
                        })
                    },
                    onCleanupUnused = {
                        partyListViewModel.cleanup()
                        Toast.makeText(this@ManageParties, "Cleaned up unused payees", Toast.LENGTH_SHORT).show()
                    },
                    onClose = {
                        finish()
                    }
                )
            }
        }
    }
}
