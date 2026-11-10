package com.example.ui.common

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.domain.model.FilterState
import com.example.domain.util.Formatters
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun FilterChipsRow(
    filterState: FilterState,
    categories: List<Category>,
    accounts: List<Account>,
    onFilterChange: (FilterState) -> Unit,
    modifier: Modifier = Modifier,
    availableMonths: List<Pair<Int, Int>>? = null, // Pair(month 1..12, year)
    lazyListState: LazyListState = rememberLazyListState(),
    onClearAll: (() -> Unit)? = null
) {
    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }

    val effectiveYears = if (filterState.selectedYears.isNotEmpty()) filterState.selectedYears
    else if (filterState.selectedYear != null) setOf(filterState.selectedYear!!)
    else emptySet()

    val effectiveMonths = if (filterState.selectedMonths.isNotEmpty()) filterState.selectedMonths
    else if (filterState.selectedMonth != null) setOf(filterState.selectedMonth!!)
    else emptySet()

    val effectiveCatIds = if (filterState.selectedCategoryIds.isNotEmpty()) filterState.selectedCategoryIds
    else if (filterState.selectedCategoryId != null) setOf(filterState.selectedCategoryId!!)
    else emptySet()

    val effectiveAccIds = if (filterState.selectedAccountIds.isNotEmpty()) filterState.selectedAccountIds
    else if (filterState.selectedAccountId != null) setOf(filterState.selectedAccountId!!)
    else emptySet()

    val currentType = filterState.selectedType.uppercase().ifBlank { "ALL" }
    val hasTransferRoute = filterState.transferRouteFromAccountId != null ||
            filterState.transferRouteToAccountId != null ||
            !filterState.transferRouteLabel.isNullOrBlank()

    val availableYears = remember(availableMonths) {
        if (!availableMonths.isNullOrEmpty()) {
            availableMonths.map { it.second }.distinct().sortedDescending()
        } else {
            val curYear = Calendar.getInstance().get(Calendar.YEAR)
            (0..3).map { curYear - it }
        }
    }

    val monthsToDisplay = remember(availableMonths, effectiveYears) {
        if (!availableMonths.isNullOrEmpty()) {
            val filtered = if (effectiveYears.isNotEmpty()) {
                availableMonths.filter { it.second in effectiveYears }
            } else {
                availableMonths
            }
            val distinctMonths = filtered.map { it.first }.distinct().sorted()
            if (distinctMonths.isNotEmpty()) distinctMonths
            else {
                val curM = Calendar.getInstance().get(Calendar.MONTH) + 1
                (1..curM).toList()
            }
        } else {
            val curM = Calendar.getInstance().get(Calendar.MONTH) + 1
            (1..curM).toList()
        }
    }

    val isAnyMenuOpen = showYearMenu || showMonthMenu || showTypeMenu || showCategoryMenu || showAccountMenu

    val isDateRangeActive = filterState.startDate != null || filterState.endDate != null
    val isYearActive = effectiveYears.isNotEmpty()
    val isMonthActive = effectiveMonths.isNotEmpty()
    val isTypeActive = currentType != "ALL"
    val isCatVisible = true
    val isCatActive = effectiveCatIds.isNotEmpty()
    val isAccActive = effectiveAccIds.isNotEmpty()

    val currentActiveKeys = buildSet {
        if (isDateRangeActive) add("date_range")
        if (isYearActive) add("year")
        if (isMonthActive) add("month")
        if (hasTransferRoute) add("transfer_route")
        if (isTypeActive) add("type")
        if (isCatActive) add("category")
        if (isAccActive) add("account")
    }

    val allVisibleKeys = buildList {
        if (isDateRangeActive) add("date_range")
        add("year")
        add("month")
        if (hasTransferRoute) add("transfer_route")
        add("type")
        if (isCatVisible) add("category")
        add("account")
    }

    var activationOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var frozenChipOrder by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val isReducedMotion = FilterAutoRevealHelper.isReducedMotion(context)

    // Reconcile activation order when not frozen in an open popup menu
    val committedActivationOrder = remember(isAnyMenuOpen, activationOrder, currentActiveKeys, allVisibleKeys) {
        if (!isAnyMenuOpen) {
            FilterAutoRevealHelper.reconcileActivationOrder(
                existingOrder = activationOrder,
                currentlyActiveKeys = currentActiveKeys,
                allVisibleKeys = allVisibleKeys
            )
        } else {
            activationOrder
        }
    }

    val currentCommittedOrder = remember(committedActivationOrder, currentActiveKeys, allVisibleKeys) {
        FilterAutoRevealHelper.computeCommittedOrder(
            activationOrder = committedActivationOrder,
            currentlyActiveKeys = currentActiveKeys,
            allVisibleKeys = allVisibleKeys
        )
    }

    val displayedChipOrder = if (isAnyMenuOpen) {
        frozenChipOrder ?: currentCommittedOrder
    } else {
        currentCommittedOrder
    }

    // Sync state and auto-reveal leading cluster outside composition body
    LaunchedEffect(committedActivationOrder, currentCommittedOrder, isAnyMenuOpen) {
        if (!isAnyMenuOpen) {
            if (activationOrder != committedActivationOrder) {
                activationOrder = committedActivationOrder
            }
            if (frozenChipOrder != null) {
                frozenChipOrder = null
            }
            FilterAutoRevealHelper.autoRevealLeadingCluster(lazyListState, isReducedMotion)
        } else if (frozenChipOrder == null) {
            frozenChipOrder = currentCommittedOrder
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("filter_chips_row")
            .padding(top = 0.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(displayedChipOrder, key = { it }) { chipKey ->
            Box(
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = FilterAutoRevealHelper.getPlacementSpec(isReducedMotion)
                )
            ) {
                when (chipKey) {
                    "clear_all" -> {
                        Surface(
                            onClick = {
                                activationOrder = emptyList()
                                if (onClearAll != null) {
                                    onClearAll()
                                } else {
                                    onFilterChange(FilterState())
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("filter_clear_all")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear all filters",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Clear All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    "date_range" -> {
                        if (filterState.startDate != null || filterState.endDate != null) {
                            val sdf = remember { SimpleDateFormat("d MMM", Locale.US) }
                            val rangeLabel = remember(filterState.startDate, filterState.endDate) {
                                when {
                                    filterState.startDate != null && filterState.endDate != null -> {
                                        val s = sdf.format(Date(filterState.startDate))
                                        val e = sdf.format(Date(filterState.endDate))
                                        if (s == e) s else "$s – $e"
                                    }
                                    filterState.startDate != null -> "From ${sdf.format(Date(filterState.startDate))}"
                                    else -> "Until ${sdf.format(Date(filterState.endDate!!))}"
                                }
                            }
                            FilterChip(
                                selected = true,
                                onClick = { onFilterChange(filterState.copy(startDate = null, endDate = null)) },
                                label = {
                                    Text(
                                        text = rangeLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear range",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_date_range")
                            )
                        }
                    }

                    "year" -> {
                        Box {
                            val isYearActive = effectiveYears.isNotEmpty()
                            val yearLabel = when {
                                effectiveYears.isEmpty() -> "Year: All"
                                effectiveYears.size == 1 -> "Year: ${effectiveYears.first()}"
                                else -> "Years (${effectiveYears.size})"
                            }

                            FilterChip(
                                selected = isYearActive,
                                onClick = { showYearMenu = true },
                                label = {
                                    Text(
                                        text = yearLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isYearActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isYearActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isYearActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_year")
                            )

                            DropdownMenu(
                                expanded = showYearMenu,
                                onDismissRequest = { showYearMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Years", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        onFilterChange(filterState.copy(selectedYear = null, selectedYears = emptySet()))
                                    },
                                    trailingIcon = {
                                        if (effectiveYears.isEmpty()) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )

                                availableYears.forEach { yr ->
                                    val isChecked = yr in effectiveYears
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Text(yr.toString())
                                            }
                                        },
                                        onClick = {
                                            val newYears = if (isChecked) effectiveYears - yr else effectiveYears + yr
                                            onFilterChange(
                                                filterState.copy(
                                                    selectedYears = newYears,
                                                    selectedYear = if (newYears.size == 1) newYears.first() else null
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "month" -> {
                        Box {
                            val isMonthActive = effectiveMonths.isNotEmpty()
                            val monthLabel = when {
                                effectiveMonths.isEmpty() -> "Month: All"
                                effectiveMonths.size == 1 -> "Month: ${Formatters.getMonthName(effectiveMonths.first())}"
                                else -> "Months (${effectiveMonths.size})"
                            }

                            FilterChip(
                                selected = isMonthActive,
                                onClick = { showMonthMenu = true },
                                label = {
                                    Text(
                                        text = monthLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isMonthActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isMonthActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isMonthActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_month")
                            )

                            DropdownMenu(
                                expanded = showMonthMenu,
                                onDismissRequest = { showMonthMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Months", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        onFilterChange(filterState.copy(selectedMonth = null, selectedMonths = emptySet()))
                                    },
                                    trailingIcon = {
                                        if (effectiveMonths.isEmpty()) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )

                                monthsToDisplay.forEach { m ->
                                    val isChecked = m in effectiveMonths
                                    val mName = Formatters.getMonthName(m)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Text(mName)
                                            }
                                        },
                                        onClick = {
                                            val newMonths = if (isChecked) effectiveMonths - m else effectiveMonths + m
                                            onFilterChange(
                                                filterState.copy(
                                                    selectedMonths = newMonths,
                                                    selectedMonth = if (newMonths.size == 1) newMonths.first() else null
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "transfer_route" -> {
                        if (hasTransferRoute) {
                            val routeLabel = filterState.transferRouteLabel?.takeIf { it.isNotBlank() } ?: run {
                                val fromName = accounts.firstOrNull { it.id == filterState.transferRouteFromAccountId }?.name ?: "Unknown"
                                val toName = accounts.firstOrNull { it.id == filterState.transferRouteToAccountId }?.name ?: "Unknown"
                                "$fromName → $toName"
                            }
                            FilterChip(
                                selected = true,
                                onClick = {
                                    onFilterChange(
                                        filterState.copy(
                                            transferRouteFromAccountId = null,
                                            transferRouteToAccountId = null,
                                            transferRouteLabel = null
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        text = routeLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear route",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_transfer_route")
                            )
                        }
                    }

                    "type" -> {
                        Box {
                            val isTypeActive = currentType != "ALL"
                            val typeLabel = if (!isTypeActive) {
                                "Type: All"
                            } else {
                                "Type: ${currentType.lowercase().replaceFirstChar { it.uppercase() }}"
                            }

                            FilterChip(
                                selected = isTypeActive,
                                onClick = { showTypeMenu = true },
                                label = {
                                    Text(
                                        text = typeLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isTypeActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isTypeActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_type")
                            )

                            DropdownMenu(
                                expanded = showTypeMenu,
                                onDismissRequest = { showTypeMenu = false }
                            ) {
                                val typeOptions = listOf(
                                    "ALL" to "All Types",
                                    "EXPENSE" to "Expense",
                                    "INCOME" to "Income",
                                    "TRANSFER" to "Transfer"
                                )
                                typeOptions.forEach { (typeKey, display) ->
                                    val isSelected = currentType == typeKey
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = null,
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Text(
                                                    text = display,
                                                    fontWeight = if (typeKey == "ALL" || isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            onFilterChange(
                                                if (typeKey == "TRANSFER") {
                                                    filterState.copy(
                                                        selectedType = typeKey
                                                    )
                                                } else {
                                                    filterState.copy(
                                                        selectedType = typeKey,
                                                        transferRouteFromAccountId = null,
                                                        transferRouteToAccountId = null,
                                                        transferRouteLabel = null
                                                    )
                                                }
                                            )
                                            showTypeMenu = false
                                        },
                                        modifier = Modifier.testTag("type_option_${typeKey.lowercase()}")
                                    )
                                }
                            }
                        }
                    }

                    "category" -> {
                        Box {
                            val isCatActive = effectiveCatIds.isNotEmpty()
                            val allCatsSelected = categories.isNotEmpty() && effectiveCatIds.size == categories.size
                            val catLabel = when {
                                effectiveCatIds.isEmpty() -> "Category: All"
                                allCatsSelected -> "All Categories (${categories.size})"
                                effectiveCatIds.size == 1 -> {
                                    val id = effectiveCatIds.first()
                                    val name = if (id == -1L) "Uncategorized" else (categories.firstOrNull { it.id == id }?.name ?: "1")
                                    "Cat: $name"
                                }
                                else -> "Categories (${effectiveCatIds.size})"
                            }

                            FilterChip(
                                selected = isCatActive,
                                onClick = { showCategoryMenu = true },
                                label = {
                                    Text(
                                        text = catLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCatActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (effectiveCatIds.size == 1) {
                                    val id = effectiveCatIds.first()
                                    val cat = categories.firstOrNull { it.id == id }
                                    if (cat != null) {
                                        @Composable {
                                            CategoryEmojiBadge(
                                                name = cat.name,
                                                iconText = cat.iconText,
                                                color = parseColorHex(cat.color),
                                                size = 18.dp,
                                                fontSize = 11.sp
                                            )
                                        }
                                    } else null
                                } else null,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCatActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_category")
                            )

                            val screenHeightDp = LocalConfiguration.current.screenHeightDp.coerceAtLeast(480)
                            val maxCategoryMenuHeight = (screenHeightDp * 0.45f).dp
                            val filteredCategories = remember(categories, categorySearchQuery) {
                                if (categorySearchQuery.isBlank()) {
                                    categories
                                } else {
                                    val q = categorySearchQuery.trim()
                                    categories.filter { it.name.contains(q, ignoreCase = true) }
                                }
                            }

                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = {
                                    showCategoryMenu = false
                                    categorySearchQuery = ""
                                },
                                modifier = Modifier
                                    .heightIn(max = maxCategoryMenuHeight)
                                    .widthIn(min = 230.dp, max = 280.dp)
                                    .testTag("category_dropdown_menu")
                            ) {
                                OutlinedTextField(
                                    value = categorySearchQuery,
                                    onValueChange = { categorySearchQuery = it },
                                    placeholder = { Text("Search categories...", fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = if (categorySearchQuery.isNotEmpty()) {
                                        {
                                            IconButton(
                                                onClick = { categorySearchQuery = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear category search",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("category_search_input")
                                )

                                // O1: Header toggle row (not a list row)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("category_select_all_header"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val allChecked = categories.isNotEmpty() && effectiveCatIds.size == categories.size
                                    Text(
                                        text = if (allChecked) "Selected All (${categories.size})" else "Select All (${categories.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Checkbox(
                                        checked = allChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                val allIds = categories.map { it.id }.toSet()
                                                onFilterChange(
                                                    filterState.copy(
                                                        selectedCategoryIds = allIds,
                                                        selectedCategoryId = if (allIds.size == 1) allIds.first() else null
                                                    )
                                                )
                                            } else {
                                                onFilterChange(
                                                    filterState.copy(
                                                        selectedCategoryIds = emptySet(),
                                                        selectedCategoryId = null
                                                    )
                                                )
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("category_select_all_toggle")
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("All Categories", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        onFilterChange(filterState.copy(selectedCategoryId = null, selectedCategoryIds = emptySet()))
                                    },
                                    trailingIcon = {
                                        if (effectiveCatIds.isEmpty()) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )

                                filteredCategories.forEach { cat ->
                                    val isChecked = cat.id in effectiveCatIds
                                    val dotColor = parseColorHex(cat.color)

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                CategoryEmojiBadge(
                                                    name = cat.name,
                                                    iconText = cat.iconText,
                                                    color = dotColor,
                                                    size = 20.dp,
                                                    fontSize = 12.sp
                                                )
                                                Text(cat.name)
                                            }
                                        },
                                        onClick = {
                                            val newSet = if (isChecked) effectiveCatIds - cat.id else effectiveCatIds + cat.id
                                            onFilterChange(
                                                filterState.copy(
                                                    selectedCategoryIds = newSet,
                                                    selectedCategoryId = if (newSet.size == 1) newSet.first() else null
                                                )
                                            )
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showCategoryMenu = false
                                        categorySearchQuery = ""
                                    },
                                    modifier = Modifier.testTag("category_menu_done")
                                )
                            }
                        }
                    }

                    "account" -> {
                        Box {
                            val isAccActive = effectiveAccIds.isNotEmpty()
                            val allAccsSelected = accounts.isNotEmpty() && effectiveAccIds.size == accounts.size
                            val accLabel = when {
                                effectiveAccIds.isEmpty() -> "Source: All"
                                allAccsSelected -> "All Sources (${accounts.size})"
                                effectiveAccIds.size == 1 -> {
                                    val name = accounts.firstOrNull { it.id == effectiveAccIds.first() }?.name ?: "1"
                                    "Source: $name"
                                }
                                else -> "Sources (${effectiveAccIds.size})"
                            }

                            FilterChip(
                                selected = isAccActive,
                                onClick = { showAccountMenu = true },
                                label = {
                                    Text(
                                        text = accLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isAccActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAccActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("filter_chip_source")
                            )

                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                // O1: Header toggle row (not a list row)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("account_select_all_header"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val allChecked = accounts.isNotEmpty() && effectiveAccIds.size == accounts.size
                                    Text(
                                        text = if (allChecked) "Selected All (${accounts.size})" else "Select All (${accounts.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Checkbox(
                                        checked = allChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                val allIds = accounts.map { it.id }.toSet()
                                                onFilterChange(
                                                    filterState.copy(
                                                        selectedAccountIds = allIds,
                                                        selectedAccountId = if (allIds.size == 1) allIds.first() else null
                                                    )
                                                )
                                            } else {
                                                onFilterChange(
                                                    filterState.copy(
                                                        selectedAccountIds = emptySet(),
                                                        selectedAccountId = null
                                                    )
                                                )
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("account_select_all_toggle")
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("All Sources", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        onFilterChange(filterState.copy(selectedAccountId = null, selectedAccountIds = emptySet()))
                                    },
                                    trailingIcon = {
                                        if (effectiveAccIds.isEmpty()) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )

                                accounts.forEach { acc ->
                                    val isChecked = acc.id in effectiveAccIds
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Text(acc.name)
                                            }
                                        },
                                        onClick = {
                                            val newSet = if (isChecked) effectiveAccIds - acc.id else effectiveAccIds + acc.id
                                            onFilterChange(
                                                filterState.copy(
                                                    selectedAccountIds = newSet,
                                                    selectedAccountId = if (newSet.size == 1) newSet.first() else null
                                                )
                                            )
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showAccountMenu = false
                                    },
                                    modifier = Modifier.testTag("account_menu_done")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
