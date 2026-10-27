package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.images.StaleImagesScreen
import org.totschnig.myexpenses.compose.images.toStaleImageList
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.viewmodel.StaleImagesViewModel

class ManageStaleImages : ProtectedFragmentActivity() {
    val viewModel: StaleImagesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        setContent {
            AppTheme {
                val cursor by viewModel.imagesCursor.collectAsStateWithLifecycle(initialValue = null)
                val images = remember(cursor) { cursor?.toStaleImageList() ?: emptyList() }
                val resultMessage by viewModel.result.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                StaleImagesScreen(
                    images = images,
                    resultMessage = resultMessage,
                    snackbarHostState = snackbarHostState,
                    onBack = { finish() },
                    onViewImage = { uri ->
                        startActivity(Intent(this@ManageStaleImages, SimpleImageActivity::class.java).apply {
                            data = uri
                        })
                    },
                    onArchiveImages = { ids ->
                        showSnackBar(R.string.saving)
                        viewModel.saveImages(ids)
                    },
                    onDeleteImages = { ids ->
                        showSnackBar(R.string.progress_dialog_deleting)
                        viewModel.deleteImages(ids)
                    }
                )
            }
        }
    }
}
