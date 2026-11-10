package com.example.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.ExpressionEvaluator
import java.text.DecimalFormat

enum class CalculatorMode {
    USE_AMOUNT,
    SCRATCHPAD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorBottomSheet(
    initialExpression: String = "",
    mode: CalculatorMode = CalculatorMode.USE_AMOUNT,
    currencySymbol: String = com.example.domain.util.Formatters.currentCurrencySymbol(),
    onDismiss: () -> Unit,
    onApplyAmount: ((Double) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val decimalFormat = remember { DecimalFormat("#,##0.##") }

    var expression by remember {
        val clean = initialExpression.trim()
            .replace(",", "")
            .replace(currencySymbol, "")
            .replace("₹", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
        mutableStateOf(if (clean == "0" || clean == "0.00") "" else clean)
    }

    val liveResult: Double? = remember(expression) {
        if (expression.isBlank()) null
        else ExpressionEvaluator.evaluate(expression)
    }

    val liveResultText = remember(expression, liveResult) {
        when {
            expression.isBlank() -> ""
            liveResult == null -> {
                if (expression.contains("/ 0") || expression.contains("/0")) "Cannot divide by zero"
                else "..."
            }
            else -> "= $currencySymbol${decimalFormat.format(liveResult)}"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("calculator_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mode == CalculatorMode.SCRATCHPAD) "Calculator (Scratchpad)" else "Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("calculator_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close calculator")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (expression.isEmpty()) "0" else expression,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("calculator_expression_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = liveResultText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (liveResultText.contains("Cannot")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("calculator_result_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keypad
            fun append(str: String) {
                expression += str
            }

            fun deleteLast() {
                if (expression.isNotEmpty()) {
                    expression = if (expression.endsWith(" ")) {
                        expression.dropLast(3)
                    } else {
                        expression.dropLast(1)
                    }
                }
            }

            fun clear() {
                expression = ""
            }

            fun toggleSign() {
                val evaluated = ExpressionEvaluator.evaluate(expression)
                if (evaluated != null) {
                    val negated = -evaluated
                    expression = if (negated % 1.0 == 0.0) {
                        negated.toLong().toString()
                    } else {
                        negated.toString()
                    }
                } else {
                    if (expression.startsWith("-")) {
                        expression = expression.removePrefix("-")
                    } else if (expression.isNotBlank()) {
                        expression = "-($expression)"
                    }
                }
            }

            fun applyEquals() {
                val evaluated = ExpressionEvaluator.evaluate(expression)
                if (evaluated != null) {
                    expression = if (evaluated % 1.0 == 0.0) {
                        evaluated.toLong().toString()
                    } else {
                        decimalFormat.format(evaluated).replace(",", "")
                    }
                }
            }

            val keySpacing = 8.dp

            // Row 1: C, (, ), ⌫
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("C", Modifier.weight(1f), isSpecial = true, testTag = "calculator_key_c") { clear() }
                CalcKey("(", Modifier.weight(1f), isSpecial = true, testTag = "calculator_key_lparen") { append("(") }
                CalcKey(")", Modifier.weight(1f), isSpecial = true, testTag = "calculator_key_rparen") { append(")") }
                CalcIconKey(AppIcons.Backspace, Modifier.weight(1f), isSpecial = true, testTag = "calculator_key_backspace") { deleteLast() }
            }

            Spacer(modifier = Modifier.height(keySpacing))

            // Row 2: 7, 8, 9, ÷
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("7", Modifier.weight(1f), testTag = "calculator_key_7") { append("7") }
                CalcKey("8", Modifier.weight(1f), testTag = "calculator_key_8") { append("8") }
                CalcKey("9", Modifier.weight(1f), testTag = "calculator_key_9") { append("9") }
                CalcKey("÷", Modifier.weight(1f), isOperator = true, testTag = "calculator_key_divide") { append(" / ") }
            }

            Spacer(modifier = Modifier.height(keySpacing))

            // Row 3: 4, 5, 6, ×
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("4", Modifier.weight(1f), testTag = "calculator_key_4") { append("4") }
                CalcKey("5", Modifier.weight(1f), testTag = "calculator_key_5") { append("5") }
                CalcKey("6", Modifier.weight(1f), testTag = "calculator_key_6") { append("6") }
                CalcKey("×", Modifier.weight(1f), isOperator = true, testTag = "calculator_key_multiply") { append(" * ") }
            }

            Spacer(modifier = Modifier.height(keySpacing))

            // Row 4: 1, 2, 3, -
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("1", Modifier.weight(1f), testTag = "calculator_key_1") { append("1") }
                CalcKey("2", Modifier.weight(1f), testTag = "calculator_key_2") { append("2") }
                CalcKey("3", Modifier.weight(1f), testTag = "calculator_key_3") { append("3") }
                CalcKey("-", Modifier.weight(1f), isOperator = true, testTag = "calculator_key_minus") { append(" - ") }
            }

            Spacer(modifier = Modifier.height(keySpacing))

            // Row 5: +/-, 0, ., +
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("+/-", Modifier.weight(1f), testTag = "calculator_key_sign") { toggleSign() }
                CalcKey("0", Modifier.weight(1f), testTag = "calculator_key_0") { append("0") }
                CalcKey(".", Modifier.weight(1f), testTag = "calculator_key_dot") { append(".") }
                CalcKey("+", Modifier.weight(1f), isOperator = true, testTag = "calculator_key_plus") { append(" + ") }
            }

            Spacer(modifier = Modifier.height(keySpacing))

            // Row 6: %, =
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                CalcKey("%", Modifier.weight(1f), isOperator = true, testTag = "calculator_key_percent") { append("%") }
                CalcKey("=", Modifier.weight(3f), isOperator = true, isPrimaryAction = true, testTag = "calculator_key_equals") { applyEquals() }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Confirmation Button
            if (mode == CalculatorMode.USE_AMOUNT) {
                Button(
                    onClick = {
                        val result = liveResult ?: ExpressionEvaluator.evaluate(expression)
                        if (result != null && result > 0.0) {
                            onApplyAmount?.invoke(result)
                            onDismiss()
                        }
                    },
                    enabled = liveResult != null && liveResult > 0.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("calculator_apply_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    val finalVal = liveResult ?: 0.0
                    Text(
                        text = if (finalVal > 0.0) "Use this amount ($currencySymbol${decimalFormat.format(finalVal)})" else "Enter an amount",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        val result = liveResult ?: ExpressionEvaluator.evaluate(expression)
                        val textToCopy = if (result != null) {
                            if (result % 1.0 == 0.0) result.toLong().toString() else decimalFormat.format(result)
                        } else {
                            expression
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Calculator Result", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied $textToCopy to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = expression.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("calculator_copy_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Copy to clipboard", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    text: String,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    isOperator: Boolean = false,
    isPrimaryAction: Boolean = false,
    testTag: String = "",
    onClick: () -> Unit
) {
    val bgColor = when {
        isPrimaryAction -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        isSpecial -> MaterialTheme.colorScheme.secondaryContainer
        else -> null
    }

    val contentColor = when {
        isPrimaryAction -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isSpecial -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .liquidGlassPill(
                selected = isPrimaryAction || isOperator || isSpecial,
                selectedColor = bgColor,
                cornerRadius = 14.dp
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun CalcIconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    testTag: String = "",
    onClick: () -> Unit
) {
    val bgColor = if (isSpecial) MaterialTheme.colorScheme.secondaryContainer else null
    val contentColor = if (isSpecial) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .height(56.dp)
            .liquidGlassPill(
                selected = isSpecial,
                selectedColor = bgColor,
                cornerRadius = 14.dp
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
