package com.example.ui.commitment

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Commitment
import com.example.ui.common.AppIcons
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartialSettleSheet(
    commitment: Commitment,
    accounts: List<Account>,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (partialAmount: Double, txDate: Long, description: String?, newDueDate: Long?, chosenAccountId: Long?) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val numberFormat = remember { DecimalFormat("#,##0.##") }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.US) }

    val remaining = commitment.remaining
    val remainingFormatted = numberFormat.format(remaining)

    var amountText by remember {
        mutableStateOf(if (remaining > 0) remainingFormatted.replace(",", "") else "")
    }
    var txDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var descriptionText by remember { mutableStateOf("") }
    var selectedAccountId by remember {
        mutableStateOf(commitment.accountId ?: accounts.firstOrNull()?.id)
    }

    // Due date choices: 0 = Keep existing, 1 = New date
    var dueDateChoice by remember { mutableStateOf(0) }
    var newDueDate by remember {
        mutableStateOf(commitment.dueDate ?: (System.currentTimeMillis() + 7 * 86400000L))
    }

    var errorText by remember { mutableStateOf<String?>(null) }

    val currentDueDateLabel = when {
        commitment.dueDate != null -> dateFormat.format(Date(commitment.dueDate))
        commitment.dayOfMonth != null -> "Day ${commitment.dayOfMonth}"
        else -> "None"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Partial Settlement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${commitment.name} • $currencySymbol$remainingFormatted remaining of $currencySymbol${numberFormat.format(commitment.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount Field
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorText = null
                },
                label = { Text("Partial Amount ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errorText != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partial_amount_input"),
                supportingText = if (errorText != null) {
                    { Text(errorText!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("partial_error_text")) }
                } else null
            )

            // Account selection (if multiple accounts exist)
            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (commitment.direction == "OUTGOING") "Pay from account" else "Receive into account",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            val isSelected = acc.id == selectedAccountId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable { selectedAccountId = acc.id }
                                    .testTag("partial_account_chip_${acc.id}")
                            ) {
                                Text(
                                    text = acc.name,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Payment Date Picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Payment Date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = txDate }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    txDate = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("partial_date_picker"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dateFormat.format(Date(txDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(AppIcons.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Optional Description
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Note / Description (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partial_description_input")
            )

            // Due Date Choice Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Remaining Due Date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 0: Keep current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (dueDateChoice == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                width = if (dueDateChoice == 0) 1.5.dp else 0.dp,
                                color = if (dueDateChoice == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { dueDateChoice = 0 }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("chip_keep_date"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keep ($currentDueDateLabel)",
                            fontSize = 12.sp,
                            fontWeight = if (dueDateChoice == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (dueDateChoice == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Option 1: New date
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (dueDateChoice == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                width = if (dueDateChoice == 1) 1.5.dp else 0.dp,
                                color = if (dueDateChoice == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                dueDateChoice = 1
                                val cal = Calendar.getInstance().apply { timeInMillis = newDueDate }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        newDueDate = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("chip_new_date"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (dueDateChoice == 1) "New: ${dateFormat.format(Date(newDueDate))}" else "New date",
                            fontSize = 12.sp,
                            fontWeight = if (dueDateChoice == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (dueDateChoice == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_partial_btn")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val parsedAmount = amountText.replace(",", "").toDoubleOrNull()
                        if (parsedAmount == null || parsedAmount <= 0.0 || parsedAmount > remaining) {
                            errorText = "Enter an amount up to $currencySymbol$remainingFormatted"
                            return@Button
                        }

                        val chosenNewDueDate = if (dueDateChoice == 1) newDueDate else null
                        onConfirm(
                            parsedAmount,
                            txDate,
                            descriptionText.trim().ifBlank { null },
                            chosenNewDueDate,
                            selectedAccountId
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("confirm_partial_btn")
                ) {
                    Text("Confirm Partial")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
