package org.totschnig.myexpenses.compose.commitments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Formats minor units (paise) to formatted Indian Rupee string.
 * e.g. 1062900L -> "₹10,629", 143673L -> "₹1,436.73"
 */
fun Long.toRupeeString(): String {
    val isNegative = this < 0
    val absValue = kotlin.math.abs(this)
    val rupees = absValue / 100
    val paise = absValue % 100
    val sign = if (isNegative) "-" else ""
    return if (paise == 0L) {
        "$sign₹%,d".format(rupees)
    } else {
        "$sign₹%,d.%02d".format(rupees, paise)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM")

fun formatCommitmentDate(epochMillis: Long, short: Boolean = false): String {
    if (epochMillis <= 0L) return "—"
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (short) date.format(shortDateFormatter) else date.format(dateFormatter)
}

fun getDueDaysText(dueDateMillis: Long): Pair<String, Color> {
    if (dueDateMillis <= 0L) return Pair("No deadline", Color.Gray)
    val today = LocalDate.now()
    val dueDate = Instant.ofEpochMilli(dueDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        daysBetween < 0 -> Pair("${kotlin.math.abs(daysBetween)}d overdue", Color(0xFFD32F2F))
        daysBetween == 0L -> Pair("Deadline today", Color(0xFFE65100))
        daysBetween == 1L -> Pair("Deadline tomorrow", Color(0xFFF57C00))
        daysBetween <= 7L -> Pair("Pay by ${dueDate.format(shortDateFormatter)} (${daysBetween}d)", Color(0xFFE65100))
        else -> Pair("Pay by ${dueDate.format(shortDateFormatter)}", Color(0xFF1565C0))
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Copied to clipboard") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
}

@Composable
fun StatusBadge(
    isPaid: Boolean,
    dueDateMillis: Long = 0L,
    labelOverride: String? = null,
    modifier: Modifier = Modifier
) {
    if (isPaid) {
        Box(
            modifier = modifier
                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = labelOverride ?: "✓ Paid",
                color = Color(0xFF2E7D32),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        val (text, color) = getDueDaysText(dueDateMillis)
        val displayText = labelOverride ?: text
        val bgColor = color.copy(alpha = 0.12f)
        Box(
            modifier = modifier
                .background(bgColor, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = displayText,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PayerBadge(
    payer: String,
    modifier: Modifier = Modifier
) {
    val isDad = payer.equals("DAD", ignoreCase = true) || payer.equals("EXTERNAL", ignoreCase = true)
    val text = if (isDad) "Paid by Dad" else payer
    val color = if (isDad) Color(0xFF7B1FA2) else Color(0xFF1565C0)
    val bgColor = color.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
