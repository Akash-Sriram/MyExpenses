package org.totschnig.myexpenses.compose.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.PREDEFINED_NAME_BANK
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CCARD
import org.totschnig.myexpenses.model2.Account

private val PRESET_COLORS = listOf(
    Color(0xFF0D5EAF), // Deep Blue (HDFC / SBI)
    Color(0xFF00796B), // Teal / Emerald
    Color(0xFFE65100), // Vibrant Orange (ICICI)
    Color(0xFF6A1B9A), // Purple (Axis / UPI)
    Color(0xFFC2185B), // Ruby / Magenta
    Color(0xFF2E7D32)  // Forest Green (Cash)
)

@Composable
fun ModernOnboardingScreen(
    availableAccountTypes: List<AccountType>,
    onSaveAndFinish: (account: Account) -> Unit,
    onRestoreBackup: () -> Unit
) {
    var accountName by remember { mutableStateOf("Primary Account") }
    var selectedTypeName by remember { mutableStateOf(PREDEFINED_NAME_BANK) }
    var openingBalanceText by remember { mutableStateOf("0") }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS[0]) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepOneAccountSetup(
                accountName = accountName,
                onAccountNameChange = { accountName = it },
                selectedTypeName = selectedTypeName,
                onTypeChange = { selectedTypeName = it },
                openingBalanceText = openingBalanceText,
                onBalanceChange = { openingBalanceText = it },
                selectedColor = selectedColor,
                onColorSelected = { selectedColor = it },
                onFinish = {
                    val resolvedType = availableAccountTypes.find { it.name == selectedTypeName }
                        ?: availableAccountTypes.firstOrNull()

                    val balanceMinor = try {
                        val clean = openingBalanceText.replace(",", "").trim()
                        (clean.toDouble() * 100).toLong()
                    } catch (e: Exception) {
                        0L
                    }

                    val account = Account(
                        label = accountName.trim().ifEmpty { "Primary Account" },
                        currency = "INR",
                        openingBalance = balanceMinor,
                        description = "",
                        type = resolvedType,
                        color = selectedColor.toArgb()
                    )
                    onSaveAndFinish(account)
                },
                onRestore = onRestoreBackup
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepOneAccountSetup(
    accountName: String,
    onAccountNameChange: (String) -> Unit,
    selectedTypeName: String,
    onTypeChange: (String) -> Unit,
    openingBalanceText: String,
    onBalanceChange: (String) -> Unit,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onFinish: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Icon & Welcome
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to MyExpenses",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "100% Private & Offline Expense Manager",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Account Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "CREATE YOUR PRIMARY ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = accountName,
                    onValueChange = onAccountNameChange,
                    label = { Text("Account Name") },
                    leadingIcon = {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick preset pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("HDFC Bank", "SBI", "ICICI", "Axis Bank", "Cash", "Savings").forEach { suggestion ->
                        FilterChip(
                            selected = accountName.equals(suggestion, ignoreCase = true),
                            onClick = {
                                onAccountNameChange(suggestion)
                                if (suggestion.equals("Cash", ignoreCase = true)) {
                                    onTypeChange(PREDEFINED_NAME_CASH)
                                    onColorSelected(PRESET_COLORS[5])
                                } else {
                                    onTypeChange(PREDEFINED_NAME_BANK)
                                }
                            },
                            label = { Text(suggestion, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Type Selector
                Text(
                    text = "Account Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        Triple(PREDEFINED_NAME_BANK, "Bank", Icons.Default.AccountBalance),
                        Triple(PREDEFINED_NAME_CASH, "Cash", Icons.Default.Money),
                        Triple(PREDEFINED_NAME_CCARD, "Credit Card", Icons.Default.CreditCard)
                    )

                    types.forEach { (typeKey, label, icon) ->
                        FilterChip(
                            selected = selectedTypeName == typeKey,
                            onClick = { onTypeChange(typeKey) },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opening Balance
                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            onBalanceChange(input)
                        }
                    },
                    label = { Text("Opening Balance") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Accent Color Selector
                Text(
                    text = "Account Accent Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PRESET_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 1.dp,
                                    color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(color) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Get Started Button
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        // Restore Migration Divider & Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = " ALREADY HAVE A BACKUP? ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore from Existing Backup", fontSize = 14.sp)
        }
    }
}
