package com.example.ui.lists

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import com.example.ui.common.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.domain.util.Formatters
import com.example.ui.common.AppTopHeader
import com.example.ui.common.HeaderIconButton
import com.example.ui.common.CategoryEmojiBadge
import com.example.ui.common.getCategoryEmoji
import com.example.ui.common.getCategoryIcon
import com.example.ui.common.liquidGlass
import com.example.ui.common.liquidGlassAtmosphere
import com.example.ui.common.parseColorHex
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import java.util.Locale

private val PALETTE_COLORS = listOf(
    "#EF4444", "#F97316", "#F59E0B", "#EAB308",
    "#84CC16", "#22C55E", "#10B981", "#059669",
    "#14B8A6", "#06B6D4", "#0EA5E9", "#3B82F6",
    "#6366F1", "#8B5CF6", "#A855F7", "#D946EF",
    "#EC4899", "#F43F5E", "#D97706", "#B45309",
    "#64748B", "#475569", "#334155", "#1E293B"
)

private val CURATED_CATEGORY_EMOJIS = listOf(
    "🍽️", "☕", "🛒", "🚗", "⛽", "🧾", "⚡", "📶",
    "🎬", "🎵", "🏥", "💪", "🎓", "🧸", "🐾", "🎁",
    "🏠", "🛍️", "👕", "✈️", "💰", "🐖", "📈", "🛡️",
    "🧮", "🔁", "📱", "💊", "👶", "🎮", "☂️"
)

@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
    initialTab: ListsTab = ListsTab.CATEGORIES,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialTab) {
        viewModel.selectTab(initialTab)
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var blockedAccountFromDelete by remember { mutableStateOf<Pair<Account, Int>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.getDatabase(context) }

    var showAddFieldDialog by remember { mutableStateOf(false) }
    var fieldToEdit by remember { mutableStateOf<CustomField?>(null) }

    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<com.example.data.model.BudgetEntity?>(null) }
    var budgetToDelete by remember { mutableStateOf<com.example.data.model.BudgetEntity?>(null) }

    var showArchivedCategories by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    val appSettingsRepo = remember(context) { com.example.data.datastore.AppSettingsRepository(context) }
    val appSettings by appSettingsRepo.settingsFlow.collectAsState(initial = com.example.data.datastore.AppSettings())
    val currencySymbol by appSettingsRepo.currencySymbol.collectAsState()
    Formatters.updateCurrentCurrencySymbol(currencySymbol)

    Column(
        modifier = modifier
            .fillMaxSize()
            .liquidGlassAtmosphere()
    ) {
        AppTopHeader(
            title = "SilentSums",
            subtitle = "Lists & Metadata",
            actions = {
                if (appSettings.helpVisible) {
                    HeaderIconButton(
                        onClick = { showHelpSheet = true },
                        testTag = "help_gestures_btn",
                        contentDescription = "Help and gestures"
                    ) {
                        Text(
                            text = "?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )

        // Segmented Tabs: Categories | Sources | Fields | Budgets
        val tabs = listOf(
            ListsTab.CATEGORIES to "Categories (${state.activeCategories.size})",
            ListsTab.ACCOUNTS to "Sources (${state.accountsWithBalances.size})",
            ListsTab.FIELDS to "Fields (${state.customFields.size})",
            ListsTab.BUDGETS to "Budgets (${state.activeBudgets.size})"
        )

        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { (tab, title) ->
                val isSelected = state.selectedTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            when (state.selectedTab) {
                ListsTab.CATEGORIES -> {
                    // Data Preservation Rule info banner
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Amber400.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Amber500,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Data Preservation Rule",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Archiving categories keeps your historical ledgers, analytics, and CSV exports completely valid without altering past records.",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Active Categories Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Categories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Button(
                                onClick = { showAddCategoryDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_category_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(state.activeCategories, key = { it.id }) { cat ->
                        val count = state.categoryTransactionCounts[cat.id] ?: 0
                        CategoryItemRow(
                            category = cat,
                            txCount = count,
                            onMoveUp = { viewModel.moveCategoryUp(cat) },
                            onMoveDown = { viewModel.moveCategoryDown(cat) },
                            onEdit = { categoryToEdit = cat },
                            onArchive = { viewModel.archiveCategory(cat) },
                            onDelete = { categoryToDelete = cat }
                        )
                    }

                    // Archived Categories Section
                    if (state.archivedCategories.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showArchivedCategories = !showArchivedCategories },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Archived Categories (${state.archivedCategories.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                        Icon(
                                            imageVector = if (showArchivedCategories) AppIcons.ExpandLess else AppIcons.ExpandMore,
                                            contentDescription = null,
                                            tint = TextMuted
                                        )
                                    }

                                    AnimatedVisibility(visible = showArchivedCategories) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            state.archivedCategories.forEach { archCat ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(parseColorHex(archCat.color))
                                                        )
                                                        Text(
                                                            text = archCat.name,
                                                            fontSize = 13.sp,
                                                            color = TextMuted
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            onClick = { viewModel.restoreCategory(archCat) },
                                                            shape = RoundedCornerShape(16.dp),
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Restore", fontSize = 11.sp)
                                                        }
                                                        IconButton(
                                                            onClick = { categoryToDelete = archCat },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Delete",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
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
                    }
                }

                ListsTab.ACCOUNTS -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sources Ledger",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Button(
                                onClick = { showAddAccountDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_account_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Source", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(state.accountsWithBalances, key = { it.account.id }) { item ->
                        AccountItemCard(
                            accountWithBalance = item,
                            onEdit = { accountToEdit = item.account },
                            onArchive = { viewModel.archiveAccount(item.account) },
                            onRestore = { viewModel.restoreAccount(item.account) },
                            onDelete = {
                                coroutineScope.launch {
                                    val count = viewModel.getAccountTransactionCount(item.account.id)
                                    if (count > 0) {
                                        blockedAccountFromDelete = item.account to count
                                    } else {
                                        accountToDelete = item.account
                                    }
                                }
                            },
                            hideBalances = state.hideBalances,
                            currencySymbol = currencySymbol
                        )
                    }
                }

                ListsTab.FIELDS -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Ledger Fields",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Button(
                                onClick = { showAddFieldDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_field_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Field", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(state.customFields, key = { it.id }) { field ->
                        CustomFieldCard(
                            field = field,
                            onEdit = { fieldToEdit = field },
                            onDelete = { viewModel.deleteCustomField(field) }
                        )
                    }
                }

                ListsTab.BUDGETS -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Monthly Spending Limits",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text("Caps by category, source, or overall", fontSize = 11.sp, color = TextMuted)
                            }
                            Button(
                                onClick = { showAddBudgetDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_budget_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Budget", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (state.activeBudgets.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("empty_budgets_card")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(AppIcons.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Text("No Budgets Defined", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Text(
                                        "Set monthly spending limits for categories, credit cards, or overall spending to track headroom in real-time.",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Button(
                                        onClick = { showAddBudgetDialog = true },
                                        modifier = Modifier.padding(top = 8.dp).testTag("add_first_budget_button")
                                    ) {
                                        Text("Create First Budget")
                                    }
                                }
                            }
                        }
                    } else {
                        items(state.activeBudgets, key = { it.id }) { budget ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlass(12.dp)
                                    .testTag("budget_item_${budget.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = budget.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            softWrap = true,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val targetDesc = if (budget.targetName.isNotBlank()) "${budget.scopeType} • ${budget.targetName}" else "${budget.scopeType} Total"
                                        Text(
                                            text = targetDesc,
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            Formatters.formatMoney(budget.limitAmount, isMasked = state.hideBalances, currencySymbol = currencySymbol),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        IconButton(onClick = { budgetToEdit = budget }, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp).testTag("edit_budget_${budget.id}")) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Budget", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { budgetToDelete = budget }, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp).testTag("delete_budget_${budget.id}")) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Budget", tint = ExpenseRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Dialogs
    if (showAddCategoryDialog) {
        CategoryEditDialog(
            title = "Add Category",
            initialName = "",
            initialColor = PALETTE_COLORS.first(),
            initialIconText = null,
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, color, iconText ->
                coroutineScope.launch(Dispatchers.IO) {
                    val maxOrder = state.activeCategories.maxOfOrNull { it.sortOrder } ?: 0
                    db.categoryDao().insert(
                        Category(
                            name = name.trim(),
                            color = color,
                            sortOrder = maxOrder + 1,
                            iconText = iconText
                        )
                    )
                }
                showAddCategoryDialog = false
            }
        )
    }

    val editingCat = categoryToEdit
    if (editingCat != null) {
        key(editingCat.id) {
            CategoryEditDialog(
                title = "Edit Category",
                initialName = editingCat.name,
                initialColor = editingCat.color,
                initialIconText = editingCat.iconText,
                onDismiss = { categoryToEdit = null },
                onConfirm = { name, color, iconText ->
                    coroutineScope.launch(Dispatchers.IO) {
                        db.categoryDao().update(
                            editingCat.copy(
                                name = name.trim(),
                                color = color,
                                iconText = iconText
                            )
                        )
                    }
                    categoryToEdit = null
                }
            )
        }
    }

    if (showAddAccountDialog) {
        AccountEditDialog(
            title = "Add Source",
            initialName = "",
            initialOpeningBalance = 0.0,
            initialAccountType = "BANK",
            currencySymbol = currencySymbol,
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { name, opBal, accType ->
                viewModel.addAccount(name, opBal, accType)
                showAddAccountDialog = false
            }
        )
    }

    accountToEdit?.let { acc ->
        AccountEditDialog(
            title = "Edit Source",
            initialName = acc.name,
            initialOpeningBalance = acc.openingBalance,
            initialAccountType = acc.accountType,
            currencySymbol = currencySymbol,
            onDismiss = { accountToEdit = null },
            onConfirm = { name, opBal, accType ->
                viewModel.updateAccount(acc, name, opBal, accType)
                accountToEdit = null
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete \"${cat.name}\"? Associated transactions will have their category removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    blockedAccountFromDelete?.let { (acc, count) ->
        AlertDialog(
            onDismissRequest = { blockedAccountFromDelete = null },
            title = { Text("Cannot Delete Source", fontWeight = FontWeight.Bold) },
            text = {
                Text("\"${acc.name}\" has $count transaction${if (count == 1) "" else "s"} and cannot be permanently deleted. Would you like to archive it instead?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archiveAccount(acc)
                        blockedAccountFromDelete = null
                    },
                    modifier = Modifier.testTag("archive_account_instead_button")
                ) {
                    Text("Archive Instead", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockedAccountFromDelete = null }) { Text("Cancel") }
            },
            modifier = Modifier.testTag("account_delete_guard_dialog")
        )
    }

    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Source?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete \"${acc.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(acc, onBlocked = { count ->
                            blockedAccountFromDelete = acc to count
                        })
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddFieldDialog) {
        FieldEditDialog(
            title = "Add Custom Field",
            initialName = "",
            initialType = "TEXT",
            initialOptions = "",
            initialAppliesTo = null,
            onDismiss = { showAddFieldDialog = false },
            onConfirm = { name, type, opts, applies ->
                viewModel.addCustomField(name, type, opts, applies)
                showAddFieldDialog = false
            }
        )
    }

    fieldToEdit?.let { f ->
        FieldEditDialog(
            title = "Edit Custom Field",
            initialName = f.name,
            initialType = f.type,
            initialOptions = f.options,
            initialAppliesTo = f.appliesToType,
            onDismiss = { fieldToEdit = null },
            onConfirm = { name, type, opts, applies ->
                viewModel.updateCustomField(f, name, type, opts, applies)
                fieldToEdit = null
            }
        )
    }

    if (showAddBudgetDialog) {
        BudgetEditDialog(
            title = "Add Budget Limit",
            initialName = "",
            initialScopeType = "CATEGORY",
            initialTargetName = state.activeCategories.firstOrNull()?.name ?: "",
            initialLimit = 5000.0,
            categories = state.activeCategories,
            accounts = state.accountsWithBalances.map { it.account },
            currencySymbol = currencySymbol,
            onDismiss = { showAddBudgetDialog = false },
            onConfirm = { name, scope, target, limit ->
                viewModel.upsertBudget(name, scope, target, limit)
                showAddBudgetDialog = false
            }
        )
    }

    budgetToEdit?.let { b ->
        BudgetEditDialog(
            title = "Edit Budget: ${b.name}",
            initialName = b.name,
            initialScopeType = b.scopeType,
            initialTargetName = b.targetName,
            initialLimit = b.limitAmount,
            categories = state.activeCategories,
            accounts = state.accountsWithBalances.map { it.account },
            currencySymbol = currencySymbol,
            onDismiss = { budgetToEdit = null },
            onConfirm = { name, scope, target, limit ->
                viewModel.upsertBudget(name, scope, target, limit)
                budgetToEdit = null
            }
        )
    }

    budgetToDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("Delete Budget", fontWeight = FontWeight.Bold) },
            text = { Text("Remove the budget limit for '${b.name}'? Past transactions will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteBudget(b.id)
                        budgetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showHelpSheet) {
        com.example.ui.dashboard.HelpGesturesSheet(
            section = com.example.ui.dashboard.HelpSection.LISTS,
            onDismiss = { showHelpSheet = false }
        )
    }
}

@Composable
fun CategoryItemRow(
    category: Category,
    txCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${category.name}, Category, $txCount ledger transactions"
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    val catColor = parseColorHex(category.color)
                    CategoryEmojiBadge(
                        name = category.name,
                        iconText = category.iconText,
                        color = catColor,
                        size = 24.dp,
                        fontSize = 13.sp
                    )

                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(AppIcons.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(AppIcons.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = TextSecondary)
                    }
                    IconButton(onClick = onArchive, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(AppIcons.Archive, contentDescription = "Archive", modifier = Modifier.size(16.dp), tint = Amber500)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$txCount ledger transactions",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AccountItemCard(
    accountWithBalance: com.example.domain.model.AccountWithBalance,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    hideBalances: Boolean = false,
    currencySymbol: String = Formatters.currentCurrencySymbol()
) {
    val acc = accountWithBalance.account
    val badgeLabel = when (acc.accountType) {
        "CREDIT_CARD" -> "Card"
        "CASH" -> "Cash"
        else -> "Bank"
    }
    val badgeColor = when (acc.accountType) {
        "CREDIT_CARD" -> Amber500
        "CASH" -> IncomeGreen
        else -> MaterialTheme.colorScheme.primary
    }

    val currentBalanceFormatted = Formatters.formatMoney(accountWithBalance.currentBalance, isMasked = hideBalances, currencySymbol = currencySymbol)
    val cardDescription = if (hideBalances) {
        "${acc.name}, $badgeLabel account, hidden balance"
    } else {
        "${acc.name}, $badgeLabel account, balance $currentBalanceFormatted"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = acc.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (acc.archived) TextMuted else TextPrimary,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1
                        )
                    }
                    if (acc.archived) {
                        Surface(
                            color = TextMuted.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Archived",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Opening: ${Formatters.formatMoney(acc.openingBalance, isMasked = hideBalances, currencySymbol = currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Current: ${Formatters.formatMoney(accountWithBalance.currentBalance, isMasked = hideBalances, currencySymbol = currencySymbol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accountWithBalance.currentBalance >= 0) IncomeGreen else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = TextSecondary)
                }
                if (acc.archived) {
                    IconButton(onClick = onRestore, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp)) {
                        Icon(AppIcons.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp), tint = IncomeGreen)
                    }
                } else {
                    IconButton(onClick = onArchive, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp)) {
                        Icon(AppIcons.Archive, contentDescription = "Archive", modifier = Modifier.size(18.dp), tint = Amber500)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize().size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CustomFieldCard(
    field: CustomField,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = field.type,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = TextSecondary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize().size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (field.options.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Options: ${field.options}",
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (field.appliesToType != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Applies to: ${field.appliesToType}",
                    fontSize = 11.sp,
                    color = Amber500,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String,
    initialColor: String,
    initialIconText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconText: String?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedColor by remember(initialColor) {
        mutableStateOf(if (initialColor.isNotBlank()) initialColor else PALETTE_COLORS.first())
    }
    var selectedEmoji by remember(initialIconText) { mutableStateOf(initialIconText ?: "") }
    var customEmojiText by remember(initialIconText) {
        mutableStateOf(if (initialIconText != null && !CURATED_CATEGORY_EMOJIS.contains(initialIconText)) initialIconText else "")
    }

    val liveEmoji = when {
        selectedEmoji.isNotBlank() -> selectedEmoji
        customEmojiText.isNotBlank() -> customEmojiText.trim()
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Inline badge as leading element of the Category Name field row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val parsedColor = parseColorHex(selectedColor)
                    val displayLetter = name.trim().take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(parsedColor.copy(alpha = 0.18f))
                            .testTag("category_sheet_inline_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveEmoji.isNotBlank()) {
                            Text(
                                text = liveEmoji,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = if (displayLetter.isNotEmpty()) displayLetter else "?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_name_input")
                    )
                }

                Text("Select Tag Color", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PALETTE_COLORS) { hex ->
                        val col = parseColorHex(hex)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                // Select Icon: LazyRow of curated emojis
                Text("Select Icon", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CURATED_CATEGORY_EMOJIS) { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (selectedEmoji == emoji) {
                                        selectedEmoji = ""
                                    } else {
                                        selectedEmoji = emoji
                                        customEmojiText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                // Custom emoji input
                OutlinedTextField(
                    value = customEmojiText,
                    onValueChange = {
                        customEmojiText = it
                        selectedEmoji = it.trim()
                    },
                    label = { Text("or paste your own emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val iconToSave = selectedEmoji.trim().ifBlank { customEmojiText.trim().ifBlank { null } }
                        onConfirm(name.trim(), selectedColor, iconToSave)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AccountEditDialog(
    title: String,
    initialName: String,
    initialOpeningBalance: Double,
    initialAccountType: String = "BANK",
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, openingBalance: Double, accountType: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var openingBalText by remember { mutableStateOf(if (initialOpeningBalance != 0.0) initialOpeningBalance.toString() else "") }
    var selectedType by remember { mutableStateOf(initialAccountType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name (e.g. HDFC Bank, Cash)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingBalText,
                    onValueChange = { openingBalText = it },
                    label = { Text("Opening Balance ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Source Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("BANK" to "Bank", "CASH" to "Cash", "CREDIT_CARD" to "Credit Card").forEach { (type, label) ->
                        val isSel = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = type }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val ob = openingBalText.toDoubleOrNull() ?: 0.0
                        onConfirm(name.trim(), ob, selectedType)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditDialog(
    title: String,
    initialName: String,
    initialType: String,
    initialOptions: String,
    initialAppliesTo: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, options: String, appliesTo: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(initialType) }
    var options by remember { mutableStateOf(initialOptions) }
    var appliesTo by remember { mutableStateOf(initialAppliesTo ?: "ALL") }

    val types = listOf("TEXT", "NUMBER", "DROPDOWN", "DATE")
    var expandedType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Field Name (e.g. Payment Mode)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType }
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Field Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false }
                    ) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    selectedType = t
                                    expandedType = false
                                }
                            )
                        }
                    }
                }

                if (selectedType == "DROPDOWN") {
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("Options (comma-separated)") },
                        placeholder = { Text("UPI, Card, Cash, Net Banking") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val applies = if (appliesTo == "ALL") null else appliesTo
                        onConfirm(name.trim(), selectedType, options.trim(), applies)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditDialog(
    title: String,
    initialName: String,
    initialScopeType: String,
    initialTargetName: String,
    initialLimit: Double,
    categories: List<Category>,
    accounts: List<Account>,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, scopeType: String, targetName: String, limit: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var scopeType by remember { mutableStateOf(initialScopeType.ifBlank { "CATEGORY" }) }
    var targetName by remember { mutableStateOf(initialTargetName) }
    var limitText by remember { mutableStateOf(if (initialLimit > 0.0) String.format(Locale.US, "%.2f", initialLimit) else "") }
    var expandedTarget by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Scope selector
                Text("Scope", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CATEGORY" to "Category", "ACCOUNT" to "Source", "ALL" to "All Spend").forEach { (type, label) ->
                        val isSel = scopeType.equals(type, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    scopeType = type
                                    targetName = when (type) {
                                        "CATEGORY" -> categories.firstOrNull()?.name ?: ""
                                        "ACCOUNT" -> accounts.firstOrNull()?.name ?: ""
                                        else -> ""
                                    }
                                    if (name.isBlank() || name == "Overall Budget" || categories.any { it.name == name } || accounts.any { it.name == name }) {
                                        name = when (type) {
                                            "CATEGORY" -> targetName
                                            "ACCOUNT" -> targetName
                                            else -> "Overall Budget"
                                        }
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else TextPrimary
                                )
                            }
                        }
                    }
                }

                // Target selector (if CATEGORY or ACCOUNT)
                if (scopeType.equals("CATEGORY", ignoreCase = true) && categories.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = targetName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Category") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { expandedTarget = !expandedTarget },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedTarget = true }
                                .testTag("budget_target_dropdown")
                        )
                        DropdownMenu(
                            expanded = expandedTarget,
                            onDismissRequest = { expandedTarget = false }
                        ) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        targetName = c.name
                                        if (name.isBlank() || categories.any { it.name == name }) {
                                            name = c.name
                                        }
                                        expandedTarget = false
                                    }
                                )
                            }
                        }
                    }
                } else if (scopeType.equals("ACCOUNT", ignoreCase = true) && accounts.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = targetName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Source") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { expandedTarget = !expandedTarget },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Source")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedTarget = true }
                                .testTag("budget_target_dropdown")
                        )
                        DropdownMenu(
                            expanded = expandedTarget,
                            onDismissRequest = { expandedTarget = false }
                        ) {
                            accounts.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a.name) },
                                    onClick = {
                                        targetName = a.name
                                        if (name.isBlank() || accounts.any { it.name == name }) {
                                            name = a.name
                                        }
                                        expandedTarget = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Budget Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Budget Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("budget_name_input")
                )

                // Limit Amount
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Limit ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitText.toDoubleOrNull() ?: 0.0
                    val finalName = name.ifBlank { if (targetName.isNotBlank()) targetName else "Overall Budget" }
                    if (limit > 0.0) {
                        onConfirm(finalName.trim(), scopeType, targetName.trim(), limit)
                    }
                },
                enabled = (limitText.toDoubleOrNull() ?: 0.0) > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("save_budget_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
