package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalIsDarkTheme

private val HeroRimBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x80FFFFFF), // Faint top highlight edge
        Color(0x35FFFFFF),
        Color(0x18FFFFFF)
    )
)

private val DarkRimBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x38FFFFFF),
        Color(0x22FFFFFF),
        Color(0x18FFFFFF)
    )
)

private val LightRimBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x280F172A), // Crisp hairline outline on white
        Color(0x1C0F172A),
        Color(0x220F172A)
    )
)

private val HeroTopHighlightColors = listOf(
    Color(0x00FFFFFF),
    Color(0x80FFFFFF),
    Color(0xB0FFFFFF),
    Color(0x80FFFFFF),
    Color(0x00FFFFFF)
)

private val DarkSelectedPillBorderBrush = Brush.verticalGradient(
    listOf(Color(0x9993C5FD), Color(0x443B82F6))
)

private val LightSelectedPillBorderBrush = Brush.verticalGradient(
    listOf(Color(0xCC3B82F6), Color(0x661D4ED8))
)

private val DarkUnselectedPillBorderBrush = Brush.verticalGradient(
    listOf(Color(0x35FFFFFF), Color(0x18FFFFFF))
)

private val LightUnselectedPillBorderBrush = Brush.verticalGradient(
    listOf(Color(0x280F172A), Color(0x1C0F172A))
)

private val DarkAtmosphereBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF090E17),
        Color(0xFF0E1726),
        Color(0xFF090E17)
    )
)

private val LightAtmosphereBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF1F4F9),
        Color(0xFFE9EDF5),
        Color(0xFFF1F4F9)
    )
)

private val CachedCornerShapes = mapOf(
    8.dp to RoundedCornerShape(8.dp),
    10.dp to RoundedCornerShape(10.dp),
    12.dp to RoundedCornerShape(12.dp),
    14.dp to RoundedCornerShape(14.dp),
    16.dp to RoundedCornerShape(16.dp),
    20.dp to RoundedCornerShape(20.dp),
    24.dp to RoundedCornerShape(24.dp)
)

private val HeroTopHighlightBrush = Brush.horizontalGradient(HeroTopHighlightColors)

/**
 * Layered Elevated Floating Card design system.
 * Casts a soft drop shadow, provides a solid-enough surface tone that never washes out,
 * and frames the card with a distinct hairline edge so outlines are clearly visible
 * in both Light and Dark mode.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 5.dp,
    tint: Color? = null,
    isHero: Boolean = false
): Modifier = composed {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val shape = remember(cornerRadius) { CachedCornerShapes[cornerRadius] ?: RoundedCornerShape(cornerRadius) }

    val isHeroCard = isHero || (tint != null && tint == MaterialTheme.colorScheme.primary)
    val effectiveElevation = if (isHeroCard) 8.dp else if (elevation < 4.dp) 5.dp else elevation

    // Solid surface colors - strictly avoiding washed-out translucent white
    val baseSurfaceColor = if (isHeroCard) {
        if (isDark) Color(0xFF1D4ED8) else Color(0xFF1E40AF)
    } else if (tint != null) {
        tint
    } else {
        // Distinct lighter surface tone in dark mode (0xFF152238 vs bg 0xFF090E17); solid pure white in light mode
        if (isDark) Color(0xFF152238) else Color(0xFFFFFFFF)
    }

    // Soft, realistic drop shadows with calibrated ambient and spot colors
    val ambientShadowColor = if (isHeroCard) {
        if (isDark) Color(0x80000000) else Color(0x331E3A8A)
    } else if (isDark) {
        Color(0x66000000)
    } else {
        Color(0x1F0F172A) // 12% slate-navy ambient
    }

    val spotShadowColor = if (isHeroCard) {
        if (isDark) Color(0xB3000000) else Color(0x4D1E3A8A)
    } else if (isDark) {
        Color(0xB3000000)
    } else {
        Color(0x2B0F172A) // 17% slate-navy spot
    }

    // Hairline border - razor-sharp visible edges in both light and dark
    val rimBorderBrush = remember(isHeroCard, isDark) {
        if (isHeroCard) {
            HeroRimBorderBrush
        } else if (isDark) {
            DarkRimBorderBrush
        } else {
            LightRimBorderBrush
        }
    }

    this
        .shadow(
            elevation = effectiveElevation,
            shape = shape,
            clip = false,
            ambientColor = ambientShadowColor,
            spotColor = spotShadowColor
        )
        .clip(shape)
        .background(baseSurfaceColor)
        .then(
            if (isHeroCard) {
                // Single faint highlight along the top edge of the hero card (cached via drawWithCache)
                Modifier.drawWithCache {
                    val highlightInset = cornerRadius.toPx()
                    val strokeW = 1.5.dp.toPx()
                    val startOffset = Offset(highlightInset, strokeW / 2f)
                    val endOffset = Offset(size.width - highlightInset, strokeW / 2f)
                    onDrawWithContent {
                        drawContent()
                        drawLine(
                            brush = HeroTopHighlightBrush,
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = strokeW
                        )
                    }
                }
            } else {
                Modifier
            }
        )
        .border(width = 1.dp, brush = rimBorderBrush, shape = shape)
}

/**
 * Raised child layer for list items, sources (Bank Account, Cash), and nested rows inside cards.
 * Ensures rows read as distinct raised floating layers rather than flat stripes.
 */
fun Modifier.raisedChildLayer(
    cornerRadius: Dp = 10.dp,
    elevation: Dp = 2.dp
): Modifier = composed {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val shape = remember(cornerRadius) { CachedCornerShapes[cornerRadius] ?: RoundedCornerShape(cornerRadius) }

    val surfaceColor = remember(isDark) { if (isDark) Color(0xFF1E2D44) else Color(0xFFFFFFFF) }
    val borderColor = remember(isDark) { if (isDark) Color(0x2EFFFFFF) else Color(0x1E0F172A) }
    val ambientShadow = remember(isDark) { if (isDark) Color(0x44000000) else Color(0x120F172A) }
    val spotShadow = remember(isDark) { if (isDark) Color(0x70000000) else Color(0x1C0F172A) }

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = ambientShadow,
            spotColor = spotShadow
        )
        .clip(shape)
        .background(surfaceColor)
        .border(width = 1.dp, color = borderColor, shape = shape)
}

/**
 * Liquid Glass Pill for filter chips, tags, and small interactive badges.
 */
fun Modifier.liquidGlassPill(
    selected: Boolean = false,
    selectedColor: Color? = null,
    cornerRadius: Dp = 20.dp
): Modifier = composed {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val shape = remember(cornerRadius) { CachedCornerShapes[cornerRadius] ?: RoundedCornerShape(cornerRadius) }

    val surfaceColor = remember(selected, selectedColor, isDark) {
        if (selected) {
            selectedColor ?: if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
        } else {
            if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        }
    }

    val borderBrush = remember(selected, isDark) {
        if (selected) {
            if (isDark) DarkSelectedPillBorderBrush else LightSelectedPillBorderBrush
        } else {
            if (isDark) DarkUnselectedPillBorderBrush else LightUnselectedPillBorderBrush
        }
    }

    this
        .shadow(
            elevation = if (selected) 2.dp else 1.dp,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color(0x40000000) else Color(0x120F172A),
            spotColor = if (isDark) Color(0x60000000) else Color(0x180F172A)
        )
        .clip(shape)
        .background(surfaceColor)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}

/**
 * Screen-level backdrop gradient to give depth to floating layers.
 */
fun Modifier.liquidGlassAtmosphere(): Modifier = composed {
    val isDark = LocalIsDarkTheme.current || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val bgBrush = remember(isDark) {
        if (isDark) DarkAtmosphereBrush else LightAtmosphereBrush
    }
    this.background(bgBrush)
}
