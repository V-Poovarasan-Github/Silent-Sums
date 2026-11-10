package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HelpSection {
    DASHBOARD,
    EXPENSES,
    LISTS,
    ALL
}

typealias HelpScreenContext = HelpSection

data class GestureHelpItem(
    val icon: String,
    val screen: String,
    val gesture: String,
    val result: String,
    val section: HelpSection = HelpSection.ALL
) {
    val context: HelpSection
        get() = section

    /**
     * Body text with any leading "<gesture>:" prefix stripped so the rendered body never
     * repeats its own bold title label (AF2 no duplicate-prefix render).
     */
    val body: String
        get() {
            val prefix = "$gesture:"
            return if (result.startsWith(prefix, ignoreCase = true)) {
                result.substring(prefix.length).trim()
            } else {
                result.trim()
            }
        }

    /**
     * Full rendered line combining gesture label + body for AF2 word-count and token linting.
     */
    val fullLine: String
        get() = "$gesture: $body"
}

val AUDITED_AFFORDANCES: List<GestureHelpItem> = listOf(
    // Dashboard (8 canonical items)
    GestureHelpItem(
        icon = "💳",
        screen = "Dashboard",
        gesture = "Balance Card",
        result = "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "📊",
        screen = "Dashboard",
        gesture = "Summary, Source, Card, or Category",
        result = "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "🏦",
        screen = "Dashboard",
        gesture = "Sources Order",
        result = "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "💳",
        screen = "Dashboard",
        gesture = "Pay Credit Card Due?",
        result = "Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due..",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "🎯",
        screen = "Dashboard",
        gesture = "Monthly Budgets",
        result = "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "📈",
        screen = "Dashboard",
        gesture = "Trend Bars",
        result = "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "🧭",
        screen = "Dashboard",
        gesture = "Chart Controls",
        result = "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "🗂️",
        screen = "Dashboard",
        gesture = "Top Filters",
        result = "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
        section = HelpSection.DASHBOARD
    ),
    GestureHelpItem(
        icon = "🔔",
        screen = "Dashboard",
        gesture = "Header Icons",
        result = "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto.",
        section = HelpSection.DASHBOARD
    ),

    // Expenses (7 canonical items)
    GestureHelpItem(
        icon = "🗑️",
        screen = "Ledger",
        gesture = "Delete & Undo",
        result = "Delete & Undo: Swipe left, tap red bin to delete, or tap UNDO.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "➗",
        screen = "Ledger",
        gesture = "Split Cards",
        result = "Split Cards: Tap card to expand lines inline; tap ✎ to edit.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "➕",
        screen = "Ledger",
        gesture = "Entry & Split Math (+)",
        result = "Entry & Split Math (+): Keypad solves math; enter Total first for splits.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "📎",
        screen = "Ledger",
        gesture = "Media & Categories",
        result = "Media & Categories: Tap 📷 or 🎤 for media; tap category to re-tag.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "✏️",
        screen = "Ledger",
        gesture = "Edit Row",
        result = "Edit Row: Tap any row to edit amount, account, date, notes, or media.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "🔍",
        screen = "Ledger",
        gesture = "Search & Scratchpad (🧮)",
        result = "Search & Scratchpad (🧮): Search text, tags, or amounts; tap 🧮 for calculator.",
        section = HelpSection.EXPENSES
    ),
    GestureHelpItem(
        icon = "📥",
        screen = "Ledger",
        gesture = "Filtered Export",
        result = "Filtered Export: Top download icon saves only filtered rows to your data folder.",
        section = HelpSection.EXPENSES
    ),

    // Lists (6 canonical items)
    GestureHelpItem(
        icon = "📑",
        screen = "Lists",
        gesture = "4 Tabs",
        result = "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
        section = HelpSection.LISTS
    ),
    GestureHelpItem(
        icon = "🗄️",
        screen = "Lists",
        gesture = "Delete vs. Archive",
        result = "Delete vs. Archive: Unused items delete; used items archive to protect history.",
        section = HelpSection.LISTS
    ),
    GestureHelpItem(
        icon = "🏷️",
        screen = "Lists",
        gesture = "Categories",
        result = "Categories: Paste any emoji, pick from 24 colors, and reorder with arrows.",
        section = HelpSection.LISTS
    ),
    GestureHelpItem(
        icon = "🏛️",
        screen = "Lists",
        gesture = "Sources",
        result = "Sources: Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.",
        section = HelpSection.LISTS
    ),
    GestureHelpItem(
        icon = "🧩",
        screen = "Lists",
        gesture = "Custom Fields",
        result = "Custom Fields: Add Text, Number, Dropdown, or Date fields per transaction type.",
        section = HelpSection.LISTS
    ),
    GestureHelpItem(
        icon = "🎯",
        screen = "Lists",
        gesture = "Budgets",
        result = "Budgets: Set monthly limits for a Category, a Source/Card, or All Spend.",
        section = HelpSection.LISTS
    )
)

val COMMON_TASKS_AFFORDANCES: List<GestureHelpItem> = listOf(
    GestureHelpItem(
        icon = "⚙️",
        screen = "Settings",
        gesture = "Cycle Anchor Day",
        result = "Cycle Anchor Day: Set your month start day (1–28) for budgets and cycles.",
        section = HelpSection.ALL
    ),
    GestureHelpItem(
        icon = "🛡️",
        screen = "Settings",
        gesture = "Privacy & Lock",
        result = "Privacy & Lock: Mask amounts in public, or lock with PIN and biometrics.",
        section = HelpSection.ALL
    ),
    GestureHelpItem(
        icon = "🎛️",
        screen = "Settings",
        gesture = "UI & Reminders",
        result = "UI & Reminders: Toggle Dashboard budgets, ? help buttons, or bill due alerts.",
        section = HelpSection.ALL
    ),
    GestureHelpItem(
        icon = "💾",
        screen = "Settings",
        gesture = "Auto & Manual Backup",
        result = "Auto & Manual Backup: Saves daily to your data folder and safety net.",
        section = HelpSection.ALL
    ),
    GestureHelpItem(
        icon = "🔄",
        screen = "Settings",
        gesture = "Restore & Import",
        result = "Restore & Import: Import a CSV file or Restore from CSV Backup anytime.",
        section = HelpSection.ALL
    ),
    GestureHelpItem(
        icon = "🧪",
        screen = "Settings",
        gesture = "Sample Data Mode",
        result = "Sample Data Mode: Runs in an isolated sandbox so real data stays untouched.",
        section = HelpSection.ALL
    )
)

object HelpGesturesCatalog {
    val AUDITED_AFFORDANCES: List<GestureHelpItem> = com.example.ui.dashboard.AUDITED_AFFORDANCES
    val COMMON_TASKS_AFFORDANCES: List<GestureHelpItem> = com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpGesturesSheet(
    section: HelpSection = HelpSection.ALL,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = when (section) {
        HelpSection.ALL -> COMMON_TASKS_AFFORDANCES + AUDITED_AFFORDANCES
        else -> AUDITED_AFFORDANCES.filter { it.section == section }
    }
    val titleText = "Tips & gestures"
    val subtitleText = when (section) {
        HelpSection.DASHBOARD -> "Quick tips for navigating your Dashboard."
        HelpSection.EXPENSES -> "Quick tips for filtering, adding, and managing entries."
        HelpSection.LISTS -> "Quick tips for organizing categories, sources, and budgets."
        HelpSection.ALL -> "Quick tips for preferences, security, and backups."
    }

    val configuration = LocalConfiguration.current
    val maxScrollHeight = (configuration.screenHeightDp * 0.65f).dp.coerceAtLeast(280.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("help_gestures_sheet")
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("help_gestures_title")
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxScrollHeight)
                    .verticalScroll(rememberScrollState())
                    .testTag("help_gestures_scroll_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (section == HelpSection.ALL) {
                    val groupedSections = listOf(
                        Triple("Settings & Common Tasks", "help_section_header_settings", COMMON_TASKS_AFFORDANCES),
                        Triple("Dashboard", "help_section_header_dashboard", AUDITED_AFFORDANCES.filter { it.section == HelpSection.DASHBOARD }),
                        Triple("Expenses (Ledger)", "help_section_header_expenses", AUDITED_AFFORDANCES.filter { it.section == HelpSection.EXPENSES }),
                        Triple("Lists", "help_section_header_lists", AUDITED_AFFORDANCES.filter { it.section == HelpSection.LISTS })
                    )
                    var runningIndex = 0
                    groupedSections.forEachIndexed { groupIdx, (headerTitle, headerTag, groupItems) ->
                        if (groupIdx > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (groupIdx == 0) 2.dp else 4.dp, bottom = 2.dp)
                                .testTag(headerTag)
                        )
                        groupItems.forEach { item ->
                            val rowIdx = runningIndex++
                            HelpGestureRowCard(index = rowIdx, item = item)
                        }
                    }
                } else {
                    items.forEachIndexed { index, item ->
                        HelpGestureRowCard(index = index, item = item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("help_gestures_close_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got it", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HelpGestureRowCard(
    index: Int,
    item: GestureHelpItem
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("help_gesture_row_$index"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.icon, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.gesture,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
