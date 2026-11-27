package org.totschnig.myexpenses.compose.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.model.CurrencyUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Credit Card Billing Cycle, Due Date Countdown, and 30% Utilization Monitor Card.
 * Designed for Indian Credit Cards (e.g. Coral Credit Card, Amazon Pay CC, Amazon Paylater).
 */
@Composable
fun CreditCardCycleCard(
    accountLabel: String,
    currentBalancePaise: Long, // negative for money owed / spend
    currencyUnit: CurrencyUnit,
    detail: CreditCardDetail?,
    onUpdateStatement: (() -> Unit)? = null,
    onRecordPayment: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val limitPaise = detail?.creditLimit?.takeIf { it > 0 }
        ?: 0L

    // For credit cards, currentBalance in MyExpenses is usually negative when spent (e.g. -15,000.00)
    val spentPaise = if (currentBalancePaise < 0) -currentBalancePaise else currentBalancePaise
    val utilizationPercent = if (limitPaise > 0) {
        min(100.0, (spentPaise.toDouble() / limitPaise.toDouble()) * 100.0)
    } else 0.0

    val utilizationFraction = (utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f)

    // 30% rule for CIBIL/Credit score health
    val utilizationColor = when {
        utilizationPercent <= 30.0 -> Color(0xFF2E7D32) // Healthy Green (< 30%)
        utilizationPercent <= 70.0 -> Color(0xFFF57C00) // Amber / Warning
        else -> MaterialTheme.colorScheme.error          // High risk (> 70%)
    }

    val today = LocalDate.now()
    val statementDay = detail?.statementDayOfMonth ?: 15
    val dueDay = detail?.dueDayOfMonth ?: 5

    val daysToStatement = calculateDaysUntilDayOfMonth(statementDay, today)
    val daysToDue = calculateDaysUntilDayOfMonth(dueDay, today)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Account & Credit Limit Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = accountLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Billing Cycle Monitor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (limitPaise > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Limit: ${formatPaise(limitPaise, currencyUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 30% Credit Limit Utilization Gauge
            if (limitPaise > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Utilization: ${String.format(java.util.Locale.US, "%.1f", utilizationPercent)}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = utilizationColor
                        )
                        Text(
                            text = "30% Safe Benchmark",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (utilizationPercent > 30.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { utilizationFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceDim
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spent: ${formatPaise(spentPaise, currencyUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Available: ${formatPaise(max(0L, limitPaise - spentPaise), currencyUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Cycle Timeline / Due Date Countdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Statement Date
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Statement Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${statementDay}th of month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (daysToStatement == 0) "Today!" else "in $daysToStatement days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Due Date
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (daysToDue <= 3) Icons.Default.Warning else Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (daysToDue <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Payment Due",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${dueDay}th of month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (daysToDue == 0) "Due Today!" else "in $daysToDue days",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (daysToDue <= 3) FontWeight.Bold else FontWeight.Normal,
                            color = if (daysToDue <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Actions: Update Statement / Record Payment
            if (onUpdateStatement != null || onRecordPayment != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onUpdateStatement != null) {
                        OutlinedButton(
                            onClick = onUpdateStatement,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Update Statement", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (onRecordPayment != null) {
                        TextButton(
                            onClick = onRecordPayment,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pay Bill", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun calculateDaysUntilDayOfMonth(targetDay: Int, now: LocalDate): Int {
    val currentDay = now.dayOfMonth
    return if (targetDay >= currentDay) {
        targetDay - currentDay
    } else {
        val nextMonth = now.plusMonths(1)
        val validDay = min(targetDay, nextMonth.lengthOfMonth())
        val targetDate = nextMonth.withDayOfMonth(validDay)
        ChronoUnit.DAYS.between(now, targetDate).toInt()
    }
}

private fun formatPaise(paise: Long, currencyUnit: CurrencyUnit): String {
    val major = BigDecimal(paise).movePointLeft(currencyUnit.fractionDigits)
    return "${currencyUnit.symbol} ${major.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
}
