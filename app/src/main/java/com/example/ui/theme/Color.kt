package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Navy & Amber Palette matching attached designs
val Navy900 = Color(0xFF07111E)
val Navy800 = Color(0xFF0E1C2F) // Primary container / header / dark accents
val Navy700 = Color(0xFF1B2A41)
val Navy600 = Color(0xFF29486D)
val SlateBlue = Color(0xFF426086)
val SoftBlue = Color(0xFFBAC7E1)
val LightBlueBg = Color(0xFFD3E4FF)

// Amber / Orange accents
val Amber500 = Color(0xFFF59E0B)
val Amber400 = Color(0xFFFFB95F) // Tertiary accent
val Amber100 = Color(0xFFFFDDB8)
val AmberDark = Color(0xFFB87500)

// Semantic colors
val IncomeGreen = Color(0xFF059669) // Income green text/accent
val IncomeGreenBg = Color(0xFFD1FAE5)
val IncomeGreenBar = Color(0xFF34D399)

val ExpenseRed = Color(0xFFDC2626) // Expense red text/accent
val ExpenseRedBg = Color(0xFFFEE2E2)
val ExpenseRedContainer = Color(0xFFFFDAD6)

val TransferGray = Color(0xFF64748B) // Transfer neutral
val TransferGrayBg = Color(0xFFE2E8F0)

// Neutral surfaces
val SurfaceLight = Color(0xFFF7F9FB)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF2F4F6)
val SurfaceContainer = Color(0xFFECEEF0)
val SurfaceContainerHigh = Color(0xFFE6E8EA)
val SurfaceContainerHighest = Color(0xFFE0E3E5)

val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF334155)
val TextMutedLight = Color(0xFF64748B)

val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFFCBD5E1)
val TextMutedDark = Color(0xFF94A3B8)

val TextPrimary: Color
    @Composable
    get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable
    get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

val TextMuted: Color
    @Composable
    get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)

val OutlineVariant = Color(0xFFC5C6CD)
