package org.totschnig.myexpenses.compose.images

import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_URI
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString

data class StaleImageItem(
    val id: Long,
    val uri: Uri,
    val fileName: String
)

fun Cursor.toStaleImageList(): List<StaleImageItem> {
    val items = mutableListOf<StaleImageItem>()
    if (moveToFirst()) {
        val uriCol = getColumnIndexOrThrow(KEY_URI)
        val idCol = getColumnIndexOrThrow(KEY_ROWID)
        do {
            val id = getLong(idCol)
            val uriStr = getString(uriCol)
            val uri = Uri.parse(uriStr)
            val fileName = uri.lastPathSegment ?: "image-$id"
            items.add(StaleImageItem(id, uri, fileName))
        } while (moveToNext())
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaleImagesScreen(
    images: List<StaleImageItem>,
    resultMessage: CharSequence?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onViewImage: (Uri) -> Unit,
    onArchiveImages: (LongArray) -> Unit,
    onDeleteImages: (LongArray) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isInSelectionMode = selectedIds.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isInSelectionMode) {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.pref_manage_stale_images_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isInSelectionMode) {
                            selectedIds = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (isInSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (isInSelectionMode) {
                        IconButton(onClick = {
                            selectedIds = if (selectedIds.size == images.size) {
                                emptySet()
                            } else {
                                images.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = {
                            onArchiveImages(selectedIds.toLongArray())
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isInSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Result message banner if any
            if (!resultMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = resultMessage.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No stale images found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All receipt attachments are active and linked to transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(images, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        StaleImageCard(
                            item = item,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedIds = if (isSelected) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            },
                            onView = {
                                if (isInSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                } else {
                                    onViewImage(item.uri)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete ${selectedIds.size} Images?") },
            text = { Text("These images will be permanently removed from your device. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedIds.toLongArray()
                    selectedIds = emptySet()
                    showDeleteConfirm = false
                    onDeleteImages(toDelete)
                }) {
                    Text(
                        stringResource(R.string.menu_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StaleImageCard(
    item: StaleImageItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onView: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, item.uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.setTargetSampleSize(4)
                    }
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                thumbnail = decoded
            } catch (_: Exception) {
                thumbnail = null
            } finally {
                isLoading = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onView,
                onLongClick = onToggleSelect
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    thumbnail != null -> {
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Checkbox badge in corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                }
            }

            Text(
                text = item.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
