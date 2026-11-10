package com.example.ui.commitment

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import com.example.ui.common.AppIcons
import com.example.ui.common.CommitmentIouIcon
import com.example.ui.common.CommitmentRecurringIcon
import com.example.ui.common.ScheduleClockIcon
import com.example.ui.common.getCategoryGlyphString
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.domain.commitment.CommitmentGroups
import com.example.domain.commitment.CommitmentItem
import com.example.domain.util.Formatters
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.ui.common.getCategoryGlyphString
import com.example.ui.common.liquidGlass
import com.example.ui.theme.IncomeGreen
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommitmentsPopup(
    groups: CommitmentGroups,
    accounts: List<Account>,
    categories: List<Category>,
    accountBalances: Map<Long, Double>,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    hideBalances: Boolean = false,
    onDismiss: () -> Unit,
    onRecordAndMarkPaid: (Commitment) -> Unit,
    onRecordPartialPayment: ((Commitment, Double, Long, String?, Long?, Long?) -> Unit)? = null,
    onSkipCycle: (Commitment) -> Unit,
    onSettleIou: (Commitment, recordTransaction: Boolean, accountId: Long?) -> Unit,
    onSaveCommitment: (Commitment) -> Unit,
    onDeleteCommitment: (Commitment) -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.85f).dp
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val numberFormat = remember { DecimalFormat("#,##0.##") }

    var editingCommitment by remember { mutableStateOf<Commitment?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var settlingIou by remember { mutableStateOf<Commitment?>(null) }
    var partialSettleCommitment by remember { mutableStateOf<Commitment?>(null) }
    var selectedSettleAccountId by remember { mutableStateOf<Long?>(null) }

    if (showAddSheet || editingCommitment != null) {
        key(editingCommitment?.id ?: "new_commitment") {
            AddEditCommitmentSheet(
                commitment = editingCommitment,
                accounts = accounts,
                categories = categories,
                currencySymbol = currencySymbol,
                onDismiss = {
                    showAddSheet = false
                    editingCommitment = null
                },
                onSave = { saved ->
                    onSaveCommitment(saved)
                }
            )
        }
    }

    if (partialSettleCommitment != null) {
        PartialSettleSheet(
            commitment = partialSettleCommitment!!,
            accounts = accounts,
            currencySymbol = currencySymbol,
            onDismiss = { partialSettleCommitment = null },
            onConfirm = { partialAmount, txDate, description, newDueDate, chosenAccountId ->
                onRecordPartialPayment?.invoke(
                    partialSettleCommitment!!,
                    partialAmount,
                    txDate,
                    description,
                    newDueDate,
                    chosenAccountId
                )
                partialSettleCommitment = null
            }
        )
    }

    // Settle IOU Dialog
    if (settlingIou != null) {
        val iou = settlingIou!!
        val defaultAccId = iou.accountId ?: accounts.firstOrNull()?.id
        if (selectedSettleAccountId == null && defaultAccId != null) {
            selectedSettleAccountId = defaultAccId
        }

        AlertDialog(
            onDismissRequest = { settlingIou = null },
            title = { Text("Settle IOU: ${iou.name}") },
            text = {
                Column {
                    val dirText = if (iou.direction == "OUTGOING") "paying" else "receiving"
                    val txText = if (iou.direction == "OUTGOING") "Expense" else "Income"
                    val settleAmount = iou.remaining
                    val amtStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(settleAmount)}"
                    Text("Amount: $amtStr ($dirText)")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Choose whether to record an actual $txText transaction into your account, or just mark it settled without affecting balances.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Account for transaction:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.take(3).forEach { acc ->
                            val isSel = selectedSettleAccountId == acc.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { selectedSettleAccountId = acc.id }
                            ) {
                                Text(
                                    acc.name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSettleIou(iou, true, selectedSettleAccountId)
                        settlingIou = null
                    },
                    modifier = Modifier.testTag("settle_and_record_btn")
                ) {
                    Text("Settle & Record")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onSettleIou(iou, false, null)
                            settlingIou = null
                        },
                        modifier = Modifier.testTag("settle_only_btn")
                    ) {
                        Text("Settle Only")
                    }
                    TextButton(onClick = { settlingIou = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .heightIn(max = maxHeight)
                    .liquidGlass(24.dp, elevation = 8.dp)
                    .clickable(enabled = false) {}
                    .testTag("commitments_popup"),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    // Top Bar (Single Row: Clock Icon + Title + Add + Close)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = ScheduleClockIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Dues & Recurring",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            softWrap = true,
                            maxLines = 2
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .clickable(onClick = { showAddSheet = true })
                                .testTag("popup_add_commitment_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Commitment",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable(onClick = onDismiss)
                                .testTag("popup_close_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Content List
                    val totalItems = groups.overdue.size + groups.next7Days.size +
                            groups.later.sumOf { it.second.size } + groups.noDueDate.size

                    if (totalItems == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.RequestQuote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No dues or recurring commitments.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap '+' above to add one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Overdue Section
                            if (groups.overdue.isNotEmpty()) {
                                item {
                                    SectionHeader("OVERDUE", MaterialTheme.colorScheme.error, groups.overdue.size)
                                }
                                items(groups.overdue, key = { it.commitment.id }) { item ->
                                    CommitmentRow(
                                        item = item,
                                        accountMap = accountMap,
                                        categoryMap = categoryMap,
                                        accountBalances = accountBalances,
                                        currencySymbol = currencySymbol,
                                        hideBalances = hideBalances,
                                        onRecordAndMarkPaid = { onRecordAndMarkPaid(item.commitment) },
                                        onPartialSettle = { partialSettleCommitment = item.commitment },
                                        onSkipCycle = { onSkipCycle(item.commitment) },
                                        onSettle = { settlingIou = item.commitment },
                                        onEdit = { editingCommitment = item.commitment },
                                        onDelete = { onDeleteCommitment(item.commitment) }
                                    )
                                }
                            }

                            // Next 7 Days Section
                            if (groups.next7Days.isNotEmpty()) {
                                item {
                                    SectionHeader("NEXT 7 DAYS", Color(0xFFD97706), groups.next7Days.size)
                                }
                                items(groups.next7Days, key = { it.commitment.id }) { item ->
                                    CommitmentRow(
                                        item = item,
                                        accountMap = accountMap,
                                        categoryMap = categoryMap,
                                        accountBalances = accountBalances,
                                        currencySymbol = currencySymbol,
                                        hideBalances = hideBalances,
                                        onRecordAndMarkPaid = { onRecordAndMarkPaid(item.commitment) },
                                        onPartialSettle = { partialSettleCommitment = item.commitment },
                                        onSkipCycle = { onSkipCycle(item.commitment) },
                                        onSettle = { settlingIou = item.commitment },
                                        onEdit = { editingCommitment = item.commitment },
                                        onDelete = { onDeleteCommitment(item.commitment) }
                                    )
                                }
                            }

                            // Later Sections (grouped by month)
                            groups.later.forEach { (monthLabel, itemsInMonth) ->
                                item {
                                    SectionHeader(monthLabel.uppercase(), MaterialTheme.colorScheme.primary, itemsInMonth.size)
                                }
                                items(itemsInMonth, key = { it.commitment.id }) { item ->
                                    CommitmentRow(
                                        item = item,
                                        accountMap = accountMap,
                                        categoryMap = categoryMap,
                                        accountBalances = accountBalances,
                                        currencySymbol = currencySymbol,
                                        hideBalances = hideBalances,
                                        onRecordAndMarkPaid = { onRecordAndMarkPaid(item.commitment) },
                                        onPartialSettle = { partialSettleCommitment = item.commitment },
                                        onSkipCycle = { onSkipCycle(item.commitment) },
                                        onSettle = { settlingIou = item.commitment },
                                        onEdit = { editingCommitment = item.commitment },
                                        onDelete = { onDeleteCommitment(item.commitment) }
                                    )
                                }
                            }

                            // No Due Date Section
                            if (groups.noDueDate.isNotEmpty()) {
                                item {
                                    SectionHeader("NO DUE DATE", MaterialTheme.colorScheme.outline, groups.noDueDate.size)
                                }
                                items(groups.noDueDate, key = { it.commitment.id }) { item ->
                                    CommitmentRow(
                                        item = item,
                                        accountMap = accountMap,
                                        categoryMap = categoryMap,
                                        accountBalances = accountBalances,
                                        currencySymbol = currencySymbol,
                                        hideBalances = hideBalances,
                                        onRecordAndMarkPaid = { onRecordAndMarkPaid(item.commitment) },
                                        onPartialSettle = { partialSettleCommitment = item.commitment },
                                        onSkipCycle = { onSkipCycle(item.commitment) },
                                        onSettle = { settlingIou = item.commitment },
                                        onEdit = { editingCommitment = item.commitment },
                                        onDelete = { onDeleteCommitment(item.commitment) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommitmentRow(
    item: CommitmentItem,
    accountMap: Map<Long, Account>,
    categoryMap: Map<Long, Category> = emptyMap(),
    accountBalances: Map<Long, Double>,
    currencySymbol: String,
    hideBalances: Boolean = false,
    onRecordAndMarkPaid: () -> Unit,
    onPartialSettle: () -> Unit,
    onSkipCycle: () -> Unit,
    onSettle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = item.commitment
    val isRecurring = c.type == "RECURRING"
    val isOutgoing = c.direction == "OUTGOING"
    val isIncomingRecurring = isRecurring && !isOutgoing
    val numberFormat = remember { DecimalFormat("#,##0.##") }

    val payingAccount = c.accountId?.let { accountMap[it] }
    val balance = c.accountId?.let { accountBalances[it] } ?: 0.0
    val isSufficient = balance >= c.amount
    val shortAmount = if (!isSufficient) c.amount - balance else 0.0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(16.dp)
            .testTag("commitment_row_${c.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isPartial = c.paidSoFar > 0.0

            // 1. CONDITIONAL HEADER
            if (isPartial) {
                // Partial State: Row 1 = [partially settled] chip flush left, math string flush right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commitment_chip_header_${c.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "partially settled",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    val remainingStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(c.remaining)}"
                    val totalStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(c.amount)}"
                    Text(
                        text = "$remainingStr left of $totalStr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncomingRecurring) IncomeGreen else if (isOutgoing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("commitment_math_text_${c.id}")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Partial State: Row 2 = TitleRow (icon24-top + title, NO amount on this row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commitment_title_row_${c.id}"),
                    verticalAlignment = Alignment.Top
                ) {
                    val typeIcon = when {
                        isIncomingRecurring -> AppIcons.ArrowUpward
                        isRecurring -> CommitmentRecurringIcon
                        else -> CommitmentIouIcon
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isIncomingRecurring -> IncomeGreen.copy(alpha = 0.15f)
                                    isOutgoing -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = when {
                                isIncomingRecurring -> "Incoming Recurring"
                                isRecurring -> "Recurring"
                                else -> "IOU"
                            },
                            tint = when {
                                isIncomingRecurring -> IncomeGreen
                                isOutgoing -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = c.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Unpaid State: Row 1 = TitleRow (icon24-top + title + amount on ONE row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commitment_title_row_${c.id}"),
                    verticalAlignment = Alignment.Top
                ) {
                    val typeIcon = when {
                        isIncomingRecurring -> AppIcons.ArrowUpward
                        isRecurring -> CommitmentRecurringIcon
                        else -> CommitmentIouIcon
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isIncomingRecurring -> IncomeGreen.copy(alpha = 0.15f)
                                    isOutgoing -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = when {
                                isIncomingRecurring -> "Incoming Recurring"
                                isRecurring -> "Recurring"
                                else -> "IOU"
                            },
                            tint = when {
                                isIncomingRecurring -> IncomeGreen
                                isOutgoing -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = c.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val amtStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(c.amount)}"
                    Text(
                        text = amtStr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isIncomingRecurring) IncomeGreen else if (isOutgoing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. META LINE (Column sibling at fillMaxWidth — sits on the FULL left rail, NOT indented under icon!)
            val cat = c.categoryId?.let { categoryMap[it] }
            val catGlyph = if (cat != null) getCategoryGlyphString(cat.name, cat.iconText) else ""
            val fullMetaText = buildString {
                if (isIncomingRecurring) {
                    append("I receive · ")
                }
                append(item.dueLabel)
                if (cat != null) {
                    append(" • ")
                    if (catGlyph.isNotBlank()) {
                        append("$catGlyph ")
                    }
                    append(cat.name)
                }
            }
            Text(
                text = fullMetaText,
                fontSize = 12.sp,
                fontWeight = if (item.isOverdue) FontWeight.Bold else FontWeight.Normal,
                color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_meta_line_${c.id}")
            )

            // 3. ACCOUNT LINE (Column sibling at fillMaxWidth — sits on the FULL left rail)
            if (payingAccount != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = payingAccount.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commitment_account_line_${c.id}")
                )
            }

            // 4. FUNDS CHIP ROW (renders ONLY on OUTGOING recurring items when paying account exists)
            if (isRecurring && isOutgoing && payingAccount != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSufficient) Color(0xFF10B981).copy(alpha = 0.12f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSufficient) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSufficient) Color(0xFF059669) else Color(0xFFD97706),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val shortStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(shortAmount)}"
                    Text(
                        text = if (isSufficient) "Funds sufficient in ${payingAccount.name}"
                        else "Short by $shortStr in ${payingAccount.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSufficient) Color(0xFF059669) else Color(0xFFD97706)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. SETTLE ROW: [Record & mark paid / Record & mark received / Mark settled / Settle remaining] flush left, [pen][bin-red] flush right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_settle_row_${c.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val remainingBtnStr = if (hideBalances) Formatters.MASK_VALUE else "$currencySymbol${numberFormat.format(c.remaining)}"
                val settleLabel = if (isRecurring) {
                    if (c.paidSoFar > 0.0) "Settle remaining $remainingBtnStr"
                    else if (isOutgoing) "Record & mark paid"
                    else "Record & mark received"
                } else {
                    if (c.paidSoFar > 0.0) "Settle remaining $remainingBtnStr"
                    else "Mark settled"
                }
                val settleBtnTag = if (isRecurring) "record_paid_btn_${c.id}" else "mark_settled_btn_${c.id}"
                val settleOnClick = if (isRecurring) onRecordAndMarkPaid else onSettle

                Button(
                    onClick = settleOnClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag(settleBtnTag)
                ) {
                    Text(settleLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_commitment_btn_${c.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_commitment_btn_${c.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. ACTION ROW:
            // Recurring: [Partial] [Skip cycle] (each weight 1f, 8.dp gutter)
            // IOU: [Partial] alone (fillMaxWidth)
            if (isRecurring) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commitment_action_row_${c.id}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPartialSettle,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .testTag("partial_settle_btn_${c.id}")
                    ) {
                        Text("Partial", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onSkipCycle,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .testTag("skip_cycle_btn_${c.id}")
                    ) {
                        Text("Skip cycle", fontSize = 12.sp)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onPartialSettle,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .testTag("partial_settle_btn_${c.id}")
                ) {
                    Text("Partial", fontSize = 12.sp)
                }
            }
        }
    }
}
