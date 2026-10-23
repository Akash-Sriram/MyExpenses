package org.totschnig.myexpenses.compose.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BackupRestoreActivity
import org.totschnig.myexpenses.activity.ManageCategories
import org.totschnig.myexpenses.activity.ManageMethods
import org.totschnig.myexpenses.activity.ManageParties
import org.totschnig.myexpenses.activity.ManageTags
import org.totschnig.myexpenses.viewmodel.BackupOperationState
import org.totschnig.myexpenses.viewmodel.SettingsBackupSnapshot
import org.totschnig.myexpenses.viewmodel.SettingsViewModel2
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GreenAccent = Color(0xFF2E7D32)
private val BlueAccent = Color(0xFF1565C0)
private val PurpleAccent = Color(0xFF6A1B9A)
private val AmberAccent = Color(0xFFE65100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel2,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme by viewModel.theme.collectAsState()
    val deviceLock by viewModel.deviceLockScreen.collectAsState()
    val secureWindow by viewModel.secureWindow.collectAsState()
    val smsLogging by viewModel.smsLoggingEnabled.collectAsState()
    val resolveContacts by viewModel.resolveContactsPayee.collectAsState()
    val autoBackup by viewModel.autoBackup.collectAsState()
    val backupLocation by viewModel.backupLocation.collectAsState()
    val snapshots by viewModel.backupSnapshots.collectAsState()
    val backupOpState by viewModel.backupOpState.collectAsState()

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setBackupFolder(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. General & Accounts Section
            item(key = "general_section") {
                SettingsCard(title = "General & Accounts", icon = Icons.Default.AccountBalance, iconTint = BlueAccent) {
                    // Currency Locked to INR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GreenAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyRupee,
                                contentDescription = null,
                                tint = GreenAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Primary Currency",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GreenAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Fixed INR (₹)",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenAccent
                                    )
                                }
                            }
                            Text(
                                text = "Indian Rupee is permanently locked as standard currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    NavigationSettingTile(
                        icon = Icons.Default.Category,
                        title = "Manage Categories",
                        subtitle = "Customize expense & income hierarchies",
                        onClick = { context.startActivity(Intent(context, ManageCategories::class.java)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    NavigationSettingTile(
                        icon = Icons.Default.Payment,
                        title = "Manage Payment Methods",
                        subtitle = "UPI, Credit Cards, Net Banking, Cash",
                        onClick = { context.startActivity(Intent(context, ManageMethods::class.java)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    NavigationSettingTile(
                        icon = Icons.Default.Label,
                        title = "Manage Tags",
                        subtitle = "Custom tags for transaction tracking",
                        onClick = { context.startActivity(Intent(context, ManageTags::class.java)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    NavigationSettingTile(
                        icon = Icons.Default.Contacts,
                        title = "Payees & Contacts",
                        subtitle = "Manage merchant names and contact mappings",
                        onClick = { context.startActivity(Intent(context, ManageParties::class.java)) }
                    )
                }
            }

            // 2. UPI & SMS Intelligence Section
            item(key = "sms_upi_section") {
                SettingsCard(title = "UPI & SMS Intelligence", icon = Icons.Default.Sms, iconTint = GreenAccent) {
                    SwitchSettingTile(
                        title = "Bank SMS 1-Tap Log",
                        subtitle = "Auto-parse debit SMS from HDFC, ICICI, SBI, Axis, GPay, PhonePe, Paytm with 1-tap notification",
                        checked = smsLogging,
                        onCheckedChange = { viewModel.setSmsLoggingEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    SwitchSettingTile(
                        title = "Resolve Payee via Phone Contacts",
                        subtitle = "Automatically match mobile-number UPI IDs (e.g. 9876543210@paytm) with your contact names",
                        checked = resolveContacts,
                        onCheckedChange = { viewModel.setResolveContactsPayee(it) }
                    )
                }
            }

            // 3. Security & App Lock Section
            item(key = "security_section") {
                SettingsCard(title = "Security & Privacy", icon = Icons.Default.Security, iconTint = PurpleAccent) {
                    SwitchSettingTile(
                        title = "Biometric / Device Screen Lock",
                        subtitle = "Require device PIN, pattern, or fingerprint to open app",
                        checked = deviceLock,
                        onCheckedChange = { viewModel.setDeviceLockScreen(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    SwitchSettingTile(
                        title = "Secure Screen in Recents",
                        subtitle = "Mask financial details in app switcher and block unauthorized screenshots",
                        checked = secureWindow,
                        onCheckedChange = { viewModel.setSecureWindow(it) }
                    )
                }
            }

            // 4. Backup & Data Safety Section (3-Snapshot Retention System)
            item(key = "backup_section") {
                SettingsCard(title = "Backup & Data Safety", icon = Icons.Default.Storage, iconTint = AmberAccent) {
                    Text(
                        text = "Automated SQLite integrity check guarantees solid, uncorrupted backups. Exactly 3 healthy snapshots are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Backup Storage Location Tile
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_folder),
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Backup Folder",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (backupLocation?.isDefault == false) GreenAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (backupLocation?.isDefault == false) "Custom" else "Default Storage",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (backupLocation?.isDefault == false) GreenAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = backupLocation?.displayName ?: "Default App Storage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            pickFolderLauncher.launch(backupLocation?.uri)
                                        } catch (_: Exception) {
                                            pickFolderLauncher.launch(null)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_folder),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Folder", style = MaterialTheme.typography.labelMedium)
                                }

                                if (backupLocation?.isDefault == false) {
                                    OutlinedButton(
                                        onClick = { viewModel.setBackupFolder(null) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Backup Now Action Button
                    Button(
                        onClick = { viewModel.performBackupNow() },
                        enabled = backupOpState !is BackupOperationState.InProgress,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (backupOpState is BackupOperationState.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Validating SQLite DB & Creating Backup...")
                        } else {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Verified Backup Now")
                        }
                    }

                    // Operation state alert
                    when (val state = backupOpState) {
                        is BackupOperationState.Success -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GreenAccent.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        is BackupOperationState.Error -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        else -> {}
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Verified Healthy Snapshots (${snapshots.size}/3)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (snapshots.isEmpty()) {
                        Text(
                            text = "No backup snapshots created yet. Tap 'Create Verified Backup Now' above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        snapshots.forEach { snapshot ->
                            SnapshotItemCard(snapshot = snapshot, onRestore = {
                                val restoreIntent = Intent(context, BackupRestoreActivity::class.java).apply {
                                    action = "RESTORE"
                                    data = snapshot.uri
                                }
                                context.startActivity(restoreIntent)
                            })
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    SwitchSettingTile(
                        title = "Automated Daily Backup",
                        subtitle = "Automatically runs integrity validation and backs up data overnight",
                        checked = autoBackup,
                        onCheckedChange = { viewModel.setAutoBackup(it) }
                    )
                }
            }

            // 5. Appearance Section
            item(key = "appearance_section") {
                SettingsCard(title = "Appearance", icon = Icons.Default.SettingsBrightness, iconTint = PurpleAccent) {
                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(
                            label = "System",
                            selected = theme == "auto",
                            onClick = { viewModel.setTheme("auto") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            label = "Light",
                            selected = theme == "light",
                            onClick = { viewModel.setTheme("light") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            label = "Dark",
                            selected = theme == "dark",
                            onClick = { viewModel.setTheme("dark") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. About App Card
            item(key = "about_section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "My Expenses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Modern India Edition • INR Native • UPI & SMS Intelligence",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun NavigationSettingTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SwitchSettingTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun SnapshotItemCard(
    snapshot: SettingsBackupSnapshot,
    onRestore: () -> Unit
) {
    val dateStr = remember(snapshot.lastModified) {
        val dt = Instant.ofEpochMilli(snapshot.lastModified).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault()).format(dt)
    }
    val sizeKb = snapshot.sizeBytes / 1024

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = GreenAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "✓ SQLite Verified",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenAccent
                        )
                    }
                }
                Text(
                    text = "${snapshot.name} (${sizeKb} KB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onRestore) {
                Text(
                    text = "Restore",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        modifier = modifier
    )
}
