package org.totschnig.myexpenses.compose.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Stack

/**
 * Robust BigDecimal calculator engine supporting +, -, *, / operations and parentheses.
 */
object CalculatorEngine {

    fun calculate(expression: String): BigDecimal? {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        if (sanitized.isBlank()) return null

        return try {
            val tokens = tokenize(sanitized)
            if (tokens.isEmpty()) return null
            val postfix = infixToPostfix(tokens)
            evaluatePostfix(postfix)
        } catch (_: Exception) {
            null
        }
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        sb.append(expr[i])
                        i++
                    }
                    tokens.add(sb.toString())
                    continue
                }
                c == '+' || c == '*' || c == '/' || c == '(' || c == ')' -> {
                    tokens.add(c.toString())
                }
                c == '-' -> {
                    // Check if it's unary minus (at start or after another operator / '(')
                    val isUnary = tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/", "(")
                    if (isUnary) {
                        // Read the number following the unary minus
                        i++
                        val sb = StringBuilder("-")
                        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                            sb.append(expr[i])
                            i++
                        }
                        tokens.add(sb.toString())
                        continue
                    } else {
                        tokens.add("-")
                    }
                }
            }
            i++
        }
        return tokens
    }

    private fun precedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/" -> 2
        else -> 0
    }

    private fun infixToPostfix(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val stack = Stack<String>()

        for (token in tokens) {
            if (token.toDoubleOrNull() != null) {
                output.add(token)
            } else if (token == "(") {
                stack.push(token)
            } else if (token == ")") {
                while (stack.isNotEmpty() && stack.peek() != "(") {
                    output.add(stack.pop())
                }
                if (stack.isNotEmpty() && stack.peek() == "(") {
                    stack.pop()
                }
            } else if (token in listOf("+", "-", "*", "/")) {
                while (stack.isNotEmpty() && precedence(stack.peek()) >= precedence(token)) {
                    output.add(stack.pop())
                }
                stack.push(token)
            }
        }

        while (stack.isNotEmpty()) {
            val top = stack.pop()
            if (top != "(" && top != ")") {
                output.add(top)
            }
        }
        return output
    }

    private fun evaluatePostfix(postfix: List<String>): BigDecimal? {
        val stack = Stack<BigDecimal>()

        for (token in postfix) {
            val num = token.toBigDecimalOrNull()
            if (num != null) {
                stack.push(num)
            } else if (token in listOf("+", "-", "*", "/")) {
                if (stack.size < 2) return null
                val b = stack.pop()
                val a = stack.pop()
                val res = when (token) {
                    "+" -> a.add(b)
                    "-" -> a.subtract(b)
                    "*" -> a.multiply(b)
                    "/" -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) return null
                        a.divide(b, MathContext.DECIMAL64)
                    }
                    else -> return null
                }
                stack.push(res)
            }
        }

        return if (stack.size == 1) {
            val res = stack.pop().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            if (res.scale() < 0) res.setScale(0) else res
        } else null
    }
}

/**
 * Modern Material 3 Interactive Calculator Keypad for Expenses.
 */
@Composable
fun CalculatorKeypad(
    modifier: Modifier = Modifier,
    initialAmount: BigDecimal? = null,
    onAmountCommitted: (BigDecimal) -> Unit,
    onClose: () -> Unit = {}
) {
    var expression by remember {
        mutableStateOf(initialAmount?.stripTrailingZeros()?.toPlainString() ?: "")
    }

    val liveResult = remember(expression) {
        if (expression.any { it in "+-×÷/*" }) {
            CalculatorEngine.calculate(expression)
        } else {
            null
        }
    }

    fun appendToken(token: String) {
        val sanitized = when (token) {
            "." -> {
                val lastOperatorIndex = expression.indexOfLast { it in "+-×÷/*" }
                val currentOperand = if (lastOperatorIndex >= 0) {
                    expression.substring(lastOperatorIndex + 1)
                } else expression
                if (currentOperand.contains(".")) "" else "."
            }
            "+", "-", "×", "÷" -> {
                if (expression.isEmpty()) {
                    if (token == "-") "-" else ""
                } else if (expression.last() in "+-×÷/*") {
                    expression = expression.dropLast(1)
                    token
                } else {
                    " $token "
                }
            }
            else -> token
        }
        expression += sanitized
    }

    fun backspace() {
        if (expression.isNotEmpty()) {
            if (expression.endsWith(" ")) {
                expression = expression.trimEnd().dropLast(1).trimEnd()
            } else {
                expression = expression.dropLast(1)
            }
        }
    }

    fun commitResult() {
        val result = liveResult ?: expression.toBigDecimalOrNull()
        if (result != null) {
            onAmountCommitted(result)
            onClose()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Live Expression & Evaluation Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (expression.isBlank()) "0" else expression,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (liveResult != null) {
                        Text(
                            text = "= ${liveResult.toPlainString()}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                IconButton(onClick = ::backspace) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Keypad Grid Rows
            val rows = listOf(
                listOf("C", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", "00", ".", "=")
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (btn in row) {
                        val isOp = btn in listOf("÷", "×", "-", "+", "=")
                        val isAction = btn in listOf("C", "(", ")")

                        val colors = when {
                            btn == "=" -> ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                            isOp -> ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            isAction -> ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            else -> ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        ElevatedButton(
                            onClick = {
                                when (btn) {
                                    "C" -> expression = ""
                                    "=" -> commitResult()
                                    else -> appendToken(btn)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = colors,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(
                                text = btn,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isOp || isAction) FontWeight.Bold else FontWeight.Medium
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
