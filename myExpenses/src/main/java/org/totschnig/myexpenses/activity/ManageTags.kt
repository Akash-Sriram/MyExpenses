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
import androidx.lifecycle.lifecycleScope
import eltos.simpledialogfragment.form.ColorField
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.tags.TagsScreen
import org.totschnig.myexpenses.fragment.TagList.Companion.KEY_TAG_LIST
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.filter.KEY_SELECTION
import org.totschnig.myexpenses.viewmodel.TagBaseViewModel.Companion.KEY_DELETED_IDS
import org.totschnig.myexpenses.viewmodel.TagListViewModel

class ManageTags : ProtectedFragmentActivity() {
    private val viewModel by viewModels<TagListViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        val selected = intent.getLongArrayExtra(KEY_SELECTION)
        if (savedInstanceState == null) {
            selected?.let { viewModel.selectedTagIds = it.toHashSet() }
            viewModel.loadTags()
        }

        val action = intent.asAction

        setContent {
            AppTheme {
                val tags by viewModel.filteredTags
                    .collectAsStateWithLifecycle()
                var selectedTagIds by remember { mutableStateOf(viewModel.selectedTagIds.toSet()) }

                TagsScreen(
                    action = action,
                    tags = tags,
                    selectedTagIds = selectedTagIds,
                    onToggleSelectTag = { tagId ->
                        viewModel.toggleSelectedTagId(tagId)
                        selectedTagIds = viewModel.selectedTagIds.toSet()
                    },
                    onAddTag = { label ->
                        lifecycleScope.launch {
                            val newTag = viewModel.addTagAndPersist(label)
                            if (newTag != null) {
                                viewModel.selectTagId(newTag.id)
                                selectedTagIds = viewModel.selectedTagIds.toSet()
                            }
                        }
                    },
                    onUpdateTag = { tag, newLabel, colorInt ->
                        viewModel.updateTag(tag, newLabel, colorInt ?: ColorField.NONE)
                            .observe(this@ManageTags) { success ->
                                if (!success) {
                                    Toast.makeText(
                                        this@ManageTags,
                                        getString(R.string.already_defined, newLabel),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    },
                    onDeleteTag = { tag ->
                        viewModel.removeTagAndPersist(tag)
                        selectedTagIds = viewModel.selectedTagIds.toSet()
                    },
                    onConfirm = {
                        val result = Intent().apply {
                            val selectedTags = viewModel.allTags.filter { viewModel.selectedTagIds.contains(it.id) }
                            putParcelableArrayListExtra(KEY_TAG_LIST, ArrayList(selectedTags))
                            if (viewModel.deletedTagIds.isNotEmpty()) {
                                putExtra(KEY_DELETED_IDS, viewModel.deletedTagIds)
                            }
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                    onClose = {
                        val cancelIntent = Intent().apply {
                            if (viewModel.deletedTagIds.isNotEmpty()) {
                                putExtra(KEY_DELETED_IDS, viewModel.deletedTagIds)
                            }
                        }
                        setResult(Activity.RESULT_CANCELED, cancelIntent)
                        finish()
                    }
                )
            }
        }
    }
}
