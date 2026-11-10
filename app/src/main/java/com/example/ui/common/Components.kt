package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.LocalOnThemeChange
import com.example.ui.theme.LocalThemeSetting
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
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class NavTab(val title: String) {
    DASHBOARD("Dashboard"),
    EXPENSES("Expenses"),
    LISTS("Lists"),
    SETTINGS("Settings")
}

val ThemeAutoModeIcon: ImageVector = ImageVector.Builder(
    name = "ThemeAutoMode",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(11f, 1f); horizontalLineTo(13f); verticalLineTo(4f); horizontalLineTo(11f); close()
        moveTo(11f, 20f); horizontalLineTo(13f); verticalLineTo(23f); horizontalLineTo(11f); close()
        moveTo(1f, 11f); horizontalLineTo(4f); verticalLineTo(13f); horizontalLineTo(1f); close()
        moveTo(20f, 11f); horizontalLineTo(23f); verticalLineTo(13f); horizontalLineTo(20f); close()
        moveTo(4.93f, 3.51f); lineTo(6.34f, 4.93f); lineTo(4.93f, 6.34f); lineTo(3.51f, 4.93f); close()
        moveTo(19.07f, 4.93f); lineTo(20.49f, 3.51f); lineTo(19.07f, 2.1f); lineTo(17.66f, 3.51f); close()
        moveTo(4.93f, 17.66f); lineTo(6.34f, 19.07f); lineTo(4.93f, 20.49f); lineTo(3.51f, 19.07f); close()
        moveTo(19.07f, 19.07f); lineTo(20.49f, 20.49f); lineTo(19.07f, 21.9f); lineTo(17.66f, 20.49f); close()
        moveTo(12f, 6f)
        curveTo(8.69f, 6f, 6f, 8.69f, 6f, 12f)
        curveTo(6f, 15.31f, 8.69f, 18f, 12f, 18f)
        curveTo(15.31f, 18f, 18f, 15.31f, 18f, 12f)
        curveTo(18f, 8.69f, 15.31f, 6f, 12f, 6f)
        close()
        moveTo(8f, 12f)
        curveTo(8f, 9.79f, 9.79f, 8f, 12f, 8f)
        verticalLineTo(16f)
        curveTo(9.79f, 16f, 8f, 14.21f, 8f, 12f)
        close()
    }
}.build()

val ScheduleClockIcon: ImageVector = ImageVector.Builder(
    name = "ScheduleClock",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(11.99f, 2f)
        curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
        close()
        moveTo(12f, 20f)
        curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        close()
        moveTo(12.5f, 7f)
        horizontalLineTo(11f)
        verticalLineTo(13f)
        lineTo(16.25f, 16.15f)
        lineTo(17f, 14.92f)
        lineTo(12.5f, 12.25f)
        verticalLineTo(7f)
        close()
    }
}.build()

val CommitmentRecurringIcon: ImageVector = AppIcons.Sync

val CommitmentIouIcon: ImageVector = ImageVector.Builder(
    name = "CommitmentIouExchange",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6.99f, 11f)
        lineTo(3f, 15f)
        lineTo(6.99f, 19f)
        verticalLineTo(16f)
        horizontalLineTo(14f)
        verticalLineTo(14f)
        horizontalLineTo(6.99f)
        verticalLineTo(11f)
        close()
        moveTo(21f, 9f)
        lineTo(17.01f, 5f)
        verticalLineTo(8f)
        horizontalLineTo(10f)
        verticalLineTo(10f)
        horizontalLineTo(17.01f)
        verticalLineTo(13f)
        lineTo(21f, 9f)
        close()
    }
}.build()

val HEADER_ICON_ROW_SPACING: Dp = 12.dp

@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    testTag: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isGreyed: Boolean = false,
    badgeCount: Int? = null,
    badgeTestTag: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(containerColor, CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        if ((badgeCount ?: 0) > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .testTag(badgeTestTag ?: "dashboard_bell_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount!! > 99) "99+" else badgeCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = if (badgeCount > 9) 8.sp else 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 9.sp,
                    modifier = Modifier.testTag(if (badgeTestTag != null) "${badgeTestTag}_text" else "dashboard_bell_badge_text")
                )
            }
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isGreyed: Boolean = false,
    badgeCount: Int? = null,
    badgeTestTag: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
) {
    HeaderIconButton(
        onClick = onClick,
        testTag = testTag,
        contentDescription = contentDescription,
        modifier = modifier,
        isGreyed = isGreyed,
        badgeCount = badgeCount,
        badgeTestTag = badgeTestTag,
        containerColor = containerColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (isGreyed) 0.38f else 1.0f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun CompactThemeToggle(
    currentTheme: String = LocalThemeSetting.current,
    onThemeChange: (String) -> Unit = LocalOnThemeChange.current,
    modifier: Modifier = Modifier
) {
    val activeTheme = if (currentTheme.isBlank()) "AUTO" else currentTheme.uppercase()
    val nextTheme = when (activeTheme) {
        "AUTO" -> "LIGHT"
        "LIGHT" -> "DARK"
        else -> "AUTO"
    }
    val description = when (activeTheme) {
        "LIGHT" -> "Light Theme"
        "DARK" -> "Dark Theme"
        else -> "Auto Theme"
    }

    HeaderIconButton(
        onClick = { onThemeChange(nextTheme) },
        testTag = "compact_theme_toggle",
        contentDescription = description,
        modifier = modifier
    ) {
        when (activeTheme) {
            "LIGHT" -> {
                Icon(
                    imageVector = AppIcons.LightMode,
                    contentDescription = "Light Theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            "DARK" -> {
                Icon(
                    imageVector = AppIcons.DarkMode,
                    contentDescription = "Dark Theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                Text(
                    text = "A",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppTopHeader(
    title: String = "SilentSums",
    subtitle: String,
    currentTheme: String = LocalThemeSetting.current,
    onThemeChange: (String) -> Unit = LocalOnThemeChange.current,
    bellBadgeCount: Int? = null,
    onBellClick: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind {
                drawLine(
                    color = if (isDark) Color(0x20FFFFFF) else Color(0x18CBD5E1),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .testTag("app_top_header")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.CurrencyRupee,
                        contentDescription = "SilentSums Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HEADER_ICON_ROW_SPACING)
            ) {
                if (actions != null) {
                    actions()
                }

                if (onBellClick != null) {
                    HeaderIconButton(
                        icon = ScheduleClockIcon,
                        onClick = onBellClick,
                        testTag = "dashboard_bell_btn",
                        contentDescription = "Dues and Recurring Commitments",
                        badgeCount = bellBadgeCount,
                        badgeTestTag = "dashboard_bell_badge",
                        tint = if ((bellBadgeCount ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CompactThemeToggle(
                    currentTheme = currentTheme,
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}

val ListsRoundedGridIcon: ImageVector = ImageVector.Builder(
    name = "ListsRoundedGrid",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        fun roundedSquare(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
            moveTo(left + r, top)
            horizontalLineTo(right - r)
            quadTo(right, top, right, top + r)
            verticalLineTo(bottom - r)
            quadTo(right, bottom, right - r, bottom)
            horizontalLineTo(left + r)
            quadTo(left, bottom, left, bottom - r)
            verticalLineTo(top + r)
            quadTo(left, top, left + r, top)
            close()
        }
        roundedSquare(3f, 3f, 10.5f, 10.5f, 2f)
        roundedSquare(13.5f, 3f, 21f, 10.5f, 2f)
        roundedSquare(3f, 13.5f, 10.5f, 21f, 2f)
        roundedSquare(13.5f, 13.5f, 21f, 21f, 2f)
    }
}.build()

@Composable
fun AppBottomNav(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = if (isDark) Color(0x20FFFFFF) else Color(0x18CBD5E1),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val icon = when (tab) {
                    NavTab.DASHBOARD -> AppIcons.Dashboard
                    NavTab.EXPENSES -> AppIcons.ReceiptLong
                    NavTab.LISTS -> ListsRoundedGridIcon
                    NavTab.SETTINGS -> AppIcons.Tune
                }

                val interactionSource = remember { MutableInteractionSource() }

                val selectedPillColor = MaterialTheme.colorScheme.primaryContainer
                val selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) selectedPillColor else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) selectedIconColor else unselectedColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else unselectedColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

fun getCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("food") || lower.contains("dine") || lower.contains("restaurant") -> AppIcons.Restaurant
        lower.contains("transport") || lower.contains("car") || lower.contains("fuel") -> AppIcons.DirectionsCar
        lower.contains("travel") || lower.contains("trip") || lower.contains("flight") -> AppIcons.Flight
        lower.contains("house") || lower.contains("home") || lower.contains("rent") -> Icons.Default.Home
        lower.contains("loan") || lower.contains("emi") || lower.contains("bank") -> AppIcons.AccountBalance
        lower.contains("salary") || lower.contains("income") || lower.contains("pay") -> AppIcons.Payments
        lower.contains("shop") || lower.contains("grocer") -> AppIcons.ShoppingBag
        lower.contains("personal") || lower.contains("care") -> Icons.Default.Face
        else -> AppIcons.Widgets
    }
}

sealed class CategoryGlyph {
    data class Emoji(val text: String) : CategoryGlyph()
    data class Letter(val char: String) : CategoryGlyph()
}

fun resolveCategoryGlyph(name: String, iconText: String? = null): CategoryGlyph {
    val trimmed = iconText?.trim()
    if (!trimmed.isNullOrEmpty()) {
        return CategoryGlyph.Emoji(trimmed)
    }
    val lower = name.lowercase()
    val keywordEmoji = when {
        lower.contains("coffee") || lower.contains("cafe") -> "☕"
        lower.contains("food") || lower.contains("dine") || lower.contains("dining") || lower.contains("restaurant") -> "🍽️"
        lower.contains("grocer") -> "🛒"
        lower.contains("transport") || lower.contains("fuel") || lower.contains("car") || lower.contains("cab") || lower.contains("auto") || lower.contains("petrol") || lower.contains("diesel") -> "🚗"
        lower.contains("bill") -> "🧾"
        lower.contains("utilit") || lower.contains("electric") || lower.contains("power") -> "⚡"
        lower.contains("internet") || lower.contains("wifi") || lower.contains("broadband") -> "📶"
        lower.contains("entertain") || lower.contains("movie") || lower.contains("cinema") -> "🎬"
        lower.contains("music") || lower.contains("song") || lower.contains("audio") -> "🎵"
        lower.contains("health") || lower.contains("doctor") || lower.contains("med") || lower.contains("hospital") || lower.contains("clinic") || lower.contains("pharmacy") -> "🏥"
        lower.contains("fit") || lower.contains("gym") || lower.contains("workout") -> "💪"
        lower.contains("educat") || lower.contains("school") || lower.contains("college") || lower.contains("course") || lower.contains("tuition") -> "🎓"
        lower.contains("kid") || lower.contains("child") || lower.contains("baby") || lower.contains("toy") -> "🧸"
        lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> "🐾"
        lower.contains("gift") || lower.contains("present") -> "🎁"
        lower.contains("rent") || lower.contains("home") || lower.contains("house") || lower.contains("flat") || lower.contains("pg") -> "🏠"
        lower.contains("shop") || lower.contains("mall") || lower.contains("store") || lower.contains("lifestyle") -> "🛍️"
        lower.contains("cloth") || lower.contains("wear") || lower.contains("apparel") -> "👕"
        lower.contains("travel") || lower.contains("trip") || lower.contains("flight") || lower.contains("vacation") -> "✈️"
        lower.contains("salary") || lower.contains("wage") || lower.contains("pay") || lower.contains("earning") -> "💰"
        lower.contains("loan") || lower.contains("emi") -> "💰"
        lower.contains("saving") -> "🐖"
        lower.contains("invest") || lower.contains("stock") || lower.contains("mutual") || lower.contains("share") -> "📈"
        lower.contains("insuran") || lower.contains("lic") -> "🛡️"
        lower.contains("tax") || lower.contains("gst") || lower.contains("itr") -> "🧮"
        lower.contains("subscript") || lower.contains("recurring") || lower.contains("media") -> "🔁"
        else -> null
    }
    if (keywordEmoji != null) {
        return CategoryGlyph.Emoji(keywordEmoji)
    }
    val firstLetter = name.trim().take(1).uppercase()
    return CategoryGlyph.Letter(if (firstLetter.isNotEmpty()) firstLetter else "?")
}

fun getCategoryGlyphString(name: String, iconText: String? = null): String {
    return when (val glyph = resolveCategoryGlyph(name, iconText)) {
        is CategoryGlyph.Emoji -> glyph.text
        is CategoryGlyph.Letter -> glyph.char
    }
}

fun getCategoryEmoji(name: String, iconText: String? = null): String {
    return getCategoryGlyphString(name, iconText)
}

@Composable
fun CategoryEmojiBadge(
    name: String,
    iconText: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    fontSize: TextUnit = 18.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val glyph = resolveCategoryGlyph(name, iconText)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        when (glyph) {
            is CategoryGlyph.Emoji -> {
                Text(
                    text = glyph.text,
                    fontSize = fontSize,
                    textAlign = TextAlign.Center
                )
            }
            is CategoryGlyph.Letter -> {
                Text(
                    text = glyph.char,
                    fontSize = fontSize * 0.85f,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

fun parseColorHex(hex: String, defaultColor: Color = Navy800): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or colorInt)
        } else if (clean.length == 8) {
            Color(colorInt)
        } else {
            defaultColor
        }
    } catch (_: Exception) {
        defaultColor
    }
}

@Composable
fun CrashRecoveryBanner(
    crashLog: String,
    onShareLog: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCopyLog: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var copyConfirmationText by remember { mutableStateOf<String?>(null) }

    Surface(
        color = Color(0xFFFEF3C7),
        contentColor = Color(0xFF92400E),
        modifier = modifier
            .fillMaxWidth()
            .testTag("crash_recovery_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SilentSums stopped unexpectedly last time. ",
                        fontSize = 12.sp,
                        color = Color(0xFF78350F)
                    )
                    Text(
                        text = "Share log",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { onShareLog(crashLog) }
                            .testTag("crash_share_log_button")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy log",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SilentSums Crash Report", crashLog)
                                clipboard.setPrimaryClip(clip)
                                val confirmation = "Crash log copied — paste it in a Play Store review to help us fix it."
                                copyConfirmationText = confirmation
                                Toast.makeText(context, confirmation, Toast.LENGTH_LONG).show()
                                onCopyLog?.invoke(crashLog)
                            }
                            .testTag("crash_copy_log_button")
                    )
                    Text(
                        text = " to help us fix it.",
                        fontSize = 12.sp,
                        color = Color(0xFF78350F)
                    )
                }
            }

            if (copyConfirmationText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = copyConfirmationText!!,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF92400E),
                    modifier = Modifier
                        .padding(start = 26.dp)
                        .testTag("crash_copy_confirmation_text")
                )
            }
        }
    }
}

