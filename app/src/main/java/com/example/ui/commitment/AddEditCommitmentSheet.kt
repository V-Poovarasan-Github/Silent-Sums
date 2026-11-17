package com.example.ui.commitment

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.example.ui.common.AppIcons
import com.example.ui.common.CommitmentIouIcon
import com.example.ui.common.CommitmentRecurringIcon
import com.example.ui.common.getCategoryGlyphString
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.ui.theme.IncomeGreen
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.ui.common.CalculatorBottomSheet
import com.example.ui.common.CalculatorMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCommitmentSheet(
    commitment: Commitment?,
    accounts: List<Account>,
    categories: List<Category>,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onSave: (Commitment) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numberFormat = remember { DecimalFormat("#,##0.##") }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.US) }

    val isEditing = commitment != null
    val initialDraft = CommitmentViewModel.sessionDraft

    var type by remember { mutableStateOf(commitment?.type ?: initialDraft.type) }
    var name by remember { mutableStateOf(commitment?.name ?: initialDraft.name) }
    var amountText by remember {
        mutableStateOf(if (commitment != null && commitment.amount > 0) numberFormat.format(commitment.amount).replace(",", "") else initialDraft.amountText)
    }
    var direction by remember {
        mutableStateOf(commitment?.direction ?: initialDraft.direction)
    }
    var dayOfMonth by remember { mutableIntStateOf(commitment?.dayOfMonth ?: initialDraft.dayOfMonth) }
    var dueDate by remember { mutableStateOf(commitment?.dueDate ?: initialDraft.dueDate) }
    var selectedAccountId by remember { mutableStateOf(commitment?.accountId ?: initialDraft.selectedAccountId ?: accounts.firstOrNull()?.id) }
    var selectedCategoryId by remember { mutableStateOf(commitment?.categoryId ?: initialDraft.selectedCategoryId) }
    var descriptionText by remember { mutableStateOf(commitment?.description ?: initialDraft.descriptionText) }

    fun syncDraft() {
        if (!isEditing) {
            CommitmentViewModel.sessionDraft = CommitmentDraft(
                type = type,
                name = name,
                amountText = amountText,
                direction = direction,
                dayOfMonth = dayOfMonth,
                dueDate = dueDate,
                selectedAccountId = selectedAccountId,
                selectedCategoryId = selectedCategoryId,
                descriptionText = descriptionText
            )
        }
    }

    var showCalculator by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isUpdateSuccess by remember { mutableStateOf(false) }
    var showReminderPermissionExplanation by remember { mutableStateOf(false) }
    val settingsRepo = remember(context) { com.example.data.datastore.AppSettingsRepository(context.applicationContext) }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(commitment) {
        isUpdateSuccess = false
        errorText = null
    }

    if (showReminderPermissionExplanation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showReminderPermissionExplanation = false
                if (isUpdateSuccess) onDismiss()
            },
            title = { Text("Enable Due Reminders") },
            text = {
                Text(
                    "Allow notifications so SilentSums can alert you when recurring bills or dues are approaching.",
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("commitment_notif_rationale_text")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReminderPermissionExplanation = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            try {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: IllegalStateException) {
                                (com.example.data.DatabaseProvider.findActivity(context) as? android.app.Activity)?.let { act ->
                                    androidx.core.app.ActivityCompat.requestPermissions(
                                        act,
                                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                        1001
                                    )
                                }
                            }
                        }
                        if (isUpdateSuccess) onDismiss()
                    },
                    modifier = Modifier.testTag("commitment_notif_rationale_continue_btn")
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showReminderPermissionExplanation = false
                        if (isUpdateSuccess) onDismiss()
                    },
                    modifier = Modifier.testTag("commitment_notif_rationale_skip_btn")
                ) {
                    Text("Skip")
                }
            },
            modifier = Modifier.testTag("commitment_notif_rationale_dialog")
        )
    }

    if (showCalculator) {
        CalculatorBottomSheet(
            initialExpression = amountText,
            mode = CalculatorMode.USE_AMOUNT,
            currencySymbol = currencySymbol,
            onDismiss = { showCalculator = false },
            onApplyAmount = { evaluated ->
                amountText = if (evaluated % 1.0 == 0.0) {
                    evaluated.toLong().toString()
                } else {
                    numberFormat.format(evaluated).replace(",", "")
                }
                syncDraft()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isUpdateSuccess) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("add_edit_commitment_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (commitment == null) "New Commitment" else "Edit Commitment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        if (!isUpdateSuccess) onDismiss()
                    },
                    modifier = Modifier.testTag("commitment_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close commitment sheet")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Type Toggle (Recurring / One-time IOU)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    val isRecurring = type == "RECURRING"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRecurring) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                type = "RECURRING"
                                if (!isEditing) direction = "OUTGOING"
                                syncDraft()
                            }
                            .testTag("type_recurring_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = CommitmentRecurringIcon,
                                contentDescription = null,
                                tint = if (isRecurring) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Recurring Bill",
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRecurring) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isRecurring) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                type = "IOU"
                                if (!isEditing) direction = "OUTGOING"
                                syncDraft()
                            }
                            .testTag("type_iou_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = CommitmentIouIcon,
                                contentDescription = null,
                                tint = if (!isRecurring) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "One-time IOU",
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isRecurring) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    syncDraft()
                },
                label = {
                    Text(
                        when {
                            type == "RECURRING" && direction == "INCOMING" -> "Recurring Name (e.g. Tenant Rent, Salary)"
                            type == "RECURRING" -> "Bill Name (e.g. WiFi, Car Loan)"
                            else -> "Person / Reason (e.g. Rahul, Lunch)"
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_name_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount with Calculator Icon
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    syncDraft()
                },
                label = { Text("Amount ($currencySymbol)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_amount_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { showCalculator = true },
                        modifier = Modifier.testTag("commitment_calculator_btn")
                    ) {
                        Icon(
                            AppIcons.Calculate,
                            contentDescription = "Open Calculator",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Direction (visible on both RECURRING and IOU tabs)
            Text(
                text = "Direction",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isOutgoing = direction == "OUTGOING"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isOutgoing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            direction = "OUTGOING"
                            syncDraft()
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("iou_outgoing_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (type == "RECURRING") "I pay (outgoing)" else "I owe (Outgoing)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (isOutgoing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            direction = "INCOMING"
                            syncDraft()
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("iou_incoming_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (type == "RECURRING") "I receive (incoming)" else "Owed to me (Incoming)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (!isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Day of Month for Recurring
            if (type == "RECURRING") {
                Text(
                    text = "Day of Month: $dayOfMonth",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (d in 1..31) {
                        val isSelected = dayOfMonth == d
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { dayOfMonth = d }
                                .testTag("day_picker_$d"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Due Date for IOU (optional)
            if (type == "IOU") {
                Text(
                    text = "Due Date (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            val cal = Calendar.getInstance().apply {
                                if (dueDate != null) timeInMillis = dueDate!!
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                        set(Calendar.HOUR_OF_DAY, 9)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    dueDate = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("iou_due_date_picker"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(AppIcons.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (dueDate != null) dateFormat.format(Date(dueDate!!)) else "No due date set",
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (dueDate != null) {
                        Text(
                            text = "Clear",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.clickable { dueDate = null }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Paying/Receiving Account Picker
            Text(
                text = when {
                    type == "RECURRING" && direction == "INCOMING" -> "Receiving Account (Required)"
                    type == "RECURRING" -> "Paying Account (Required)"
                    else -> "Linked Account (Optional)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (type == "IOU") {
                    val isNone = selectedAccountId == null
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isNone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { selectedAccountId = null }
                    ) {
                        Text(
                            text = "None",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isNone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                accounts.forEach { acc ->
                    val isSelected = selectedAccountId == acc.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { selectedAccountId = acc.id }
                            .testTag("account_chip_${acc.id}")
                    ) {
                        Text(
                            text = acc.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Picker (Optional)
            Text(
                text = "Category (Optional)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isNone = selectedCategoryId == null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isNone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { selectedCategoryId = null }
                ) {
                    Text(
                        text = "None",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isNone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                categories.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    val catGlyph = getCategoryGlyphString(cat.name, cat.iconText)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { selectedCategoryId = cat.id }
                            .testTag("category_chip_${cat.id}")
                    ) {
                        Text(
                            text = if (catGlyph.isNotEmpty()) "$catGlyph ${cat.name}" else cat.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Description (optional)
            OutlinedTextField(
                value = descriptionText,
                onValueChange = {
                    descriptionText = it
                    syncDraft()
                },
                label = { Text("Description (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_description_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("commitment_error_text")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val amount = amountText.replace(",", "").toDoubleOrNull() ?: 0.0

                    val isNameBlank = trimmedName.isBlank()
                    val isAmountInvalid = amount <= 0.0

                    if (isNameBlank && isAmountInvalid) {
                        errorText = "Enter a bill name and amount"
                        return@Button
                    }
                    if (isNameBlank) {
                        errorText = "Enter a bill name"
                        return@Button
                    }
                    if (isAmountInvalid) {
                        errorText = "Enter an amount"
                        return@Button
                    }
                    if (type == "RECURRING" && selectedAccountId == null) {
                        errorText = if (direction == "INCOMING") "Please select a receiving account" else "Please select a paying account"
                        return@Button
                    }

                    if (isUpdateSuccess) return@Button
                    errorText = null
                    val toSave = (commitment ?: Commitment(
                        type = type,
                        name = trimmedName,
                        amount = amount,
                        direction = direction
                    )).copy(
                        type = type,
                        name = trimmedName,
                        amount = amount,
                        direction = direction,
                        dayOfMonth = if (type == "RECURRING") dayOfMonth else null,
                        dueDate = if (type == "IOU") dueDate else null,
                        accountId = selectedAccountId,
                        categoryId = selectedCategoryId,
                        description = descriptionText.trim().ifBlank { null }
                    )
                    try {
                        onSave(toSave)
                        CommitmentViewModel.clearSessionDraft()
                        isUpdateSuccess = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            val currentSettings = settingsRepo.cachedSettings
                                ?: kotlinx.coroutines.runBlocking {
                                    settingsRepo.settingsFlow.first()
                                }
                            if (!currentSettings.hasAskedCommitmentNotificationPermission) {
                                kotlinx.coroutines.runBlocking {
                                    settingsRepo.setHasAskedCommitmentNotificationPermission(true)
                                }
                                showReminderPermissionExplanation = true
                            }
                        }
                        coroutineScope.launch {
                            delay(1000L)
                            if (!showReminderPermissionExplanation) {
                                onDismiss()
                            }
                        }
                    } catch (e: Exception) {
                        errorText = e.message ?: "Failed to save commitment"
                    }
                },
                enabled = !isUpdateSuccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUpdateSuccess) IncomeGreen else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = if (isUpdateSuccess) IncomeGreen else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = if (isUpdateSuccess) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_commitment_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isUpdateSuccess) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (commitment == null) "Created successfully" else "Updated successfully",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(if (commitment == null) "Created" else "Updated", fontWeight = FontWeight.Bold)
                    } else {
                        Text(if (commitment == null) "Create Commitment" else "Update Commitment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
