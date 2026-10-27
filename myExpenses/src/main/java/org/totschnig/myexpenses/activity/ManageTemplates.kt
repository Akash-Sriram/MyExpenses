/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.totschnig.myexpenses.activity

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.templates.TemplatesScreen
import org.totschnig.myexpenses.compose.templates.toTemplateItemList
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener
import org.totschnig.myexpenses.fragment.PlannerFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.PermissionHelper.PermissionGroup
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.PlanInstanceInfo
import org.totschnig.myexpenses.viewmodel.TemplatesListViewModel
import java.io.Serializable

const val HELP_VARIANT_TEMPLATES = "templates"
const val HELP_VARIANT_PLANS = "plans"
const val HELP_VARIANT_PLANNER = "planner"
const val PLANNER_FRAGMENT_TAG = "PLANNER_FRAGMENT"

class ManageTemplates : ProtectedFragmentActivity(), ConfirmationDialogListener, ContribIFace {
    var calledFromCalendarWithId = NOT_CALLED
        private set

    val viewModel: TemplatesListViewModel by viewModels()

    private val editInstanceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Screen updates automatically via Flow
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHelpVariant(HELP_VARIANT_TEMPLATES, true)
        injector.inject(viewModel)

        val uriString = intent.getStringExtra(CalendarContract.Events.CUSTOM_APP_URI)
        if (uriString != null) {
            val uriPath = uriString.toUri().pathSegments
            try {
                calledFromCalendarWithId =
                    uriPath[uriPath.size - 1].toLong()
                if (calledFromCalendarWithId == 0L) {
                    calledFromCalendarWithId = NOT_CALLED
                }
            } catch (e: Exception) {
                CrashHandler.report(e)
            }
        }

        viewModel.setSortOrder(null)

        setContent {
            AppTheme {
                val cursor by viewModel.templatesCursor.collectAsStateWithLifecycle(initialValue = null)
                val templateItems = remember(cursor) { cursor?.toTemplateItemList() ?: emptyList() }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                TemplatesScreen(
                    templates = templateItems,
                    snackbarHostState = snackbarHostState,
                    onBack = { finish() },
                    onNewTemplate = {
                        startActivity(Intent(this@ManageTemplates, ExpenseEdit::class.java).apply {
                            putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                            putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                        })
                    },
                    onRunTemplate = { template ->
                        if (template.isSealed) {
                            showSnackBar(R.string.object_sealed)
                            return@TemplatesScreen
                        }
                        if (template.defaultAction == Template.Action.SAVE) {
                            viewModel.newFromTemplate(PlanInstanceInfo(template.id)).observe(this@ManageTemplates) { count ->
                                coroutineScope.launch {
                                    val msg = if (count > 0) {
                                        resources.getQuantityString(
                                            R.plurals.save_transaction_from_template_success,
                                            count,
                                            count
                                        )
                                    } else {
                                        getString(R.string.save_transaction_error)
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        } else {
                            startActivity(Intent(this@ManageTemplates, ExpenseEdit::class.java).apply {
                                action = ExpenseEdit.ACTION_CREATE_FROM_TEMPLATE
                                putExtra(KEY_TEMPLATEID, template.id)
                            })
                        }
                    },
                    onEditInstance = { template ->
                        startActivity(Intent(this@ManageTemplates, ExpenseEdit::class.java).apply {
                            action = ExpenseEdit.ACTION_CREATE_FROM_TEMPLATE
                            putExtra(KEY_TEMPLATEID, template.id)
                        })
                    },
                    onEditTemplate = { template ->
                        editInstanceLauncher.launch(
                            Intent(this@ManageTemplates, ExpenseEdit::class.java).apply {
                                putExtra(KEY_TEMPLATEID, template.id)
                            }
                        )
                    },
                    onToggleDefaultAction = { template ->
                        val newAction = if (template.defaultAction == Template.Action.SAVE) {
                            Template.Action.EDIT
                        } else {
                            Template.Action.SAVE
                        }
                        viewModel.updateDefaultAction(longArrayOf(template.id), newAction).observe(this@ManageTemplates) { success ->
                            coroutineScope.launch {
                                val msg = if (success) {
                                    if (newAction == Template.Action.SAVE) "Default set to Execute directly" else "Default set to Customize before saving"
                                } else {
                                    "Error updating default action"
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    onDeleteTemplate = { template ->
                        val hasCalendar = PermissionGroup.CALENDAR.hasPermission(this@ManageTemplates)
                        viewModel.deleteTemplates(longArrayOf(template.id), hasCalendar).observe(this@ManageTemplates) { result ->
                            coroutineScope.launch {
                                val msg = if (result.success > 0) {
                                    resources.getQuantityString(
                                        R.plurals.delete_success,
                                        result.success,
                                        result.success
                                    )
                                } else {
                                    deleteFailureMessage(null)
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    onOpenPlanner = {
                        PlannerFragment().show(supportFragmentManager, PLANNER_FRAGMENT_TAG)
                    },
                    onViewCalendarPlan = { planId ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, planId)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            CrashHandler.report(e)
                        }
                    }
                )
            }
        }
    }

    override val fabDescription = R.string.menu_create_template

    override val fabActionName = "CREATE_TEMPLATE"

    fun dispatchEditInstance(transactionId: Long) {
        editInstanceLauncher.launch(
            Intent(this, ExpenseEdit::class.java).putExtra(KEY_ROWID, transactionId)
        )
    }

    fun dispatchDeleteInstance(transactionId: Long) {
        viewModel.deleteTransactions(longArrayOf(transactionId))
    }

    fun dispatchRelinkInstance(planInstanceInfo: PlanInstanceInfo, adjustDate: Boolean) {
        viewModel.relink(planInstanceInfo, adjustDate)
    }

    override fun doHome() {
        if (isTaskRoot) {
            val upIntent = NavUtils.getParentActivityIntent(this)
            TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent!!)
                .startActivities()
        } else {
            super.doHome()
        }
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        if (feature == ContribFeature.SPLIT_TRANSACTION) {
            if (tag is Long) {
                startActivity(Intent(this, ExpenseEdit::class.java).apply {
                    action = ExpenseEdit.ACTION_CREATE_FROM_TEMPLATE
                    putExtra(KEY_TEMPLATEID, tag)
                })
            } else if (tag is LongArray) {
                viewModel.newFromTemplate(*tag.map { PlanInstanceInfo(it) }.toTypedArray())
            }
        }
    }

    companion object {
        const val NOT_CALLED = -1L
    }
}