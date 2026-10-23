package com.animexstream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.animexstream.app.data.Provider
import com.animexstream.app.data.ProviderPrefs

/** The accent colour of the currently-selected provider, readable anywhere. */
val LocalProviderAccent = staticCompositionLocalOf { Color(Provider.ANIMEXSTREAM.accent) }
val LocalProviderAccentSoft = staticCompositionLocalOf { Color(Provider.ANIMEXSTREAM.accentSoft) }

/**
 * Zangetsu-styled dark theme (deep near-black backgrounds, red-first accents)
 * whose primary/secondary colours follow the selected provider:
 *  - AnimeXstream -> purple
 *  - Animestream -> lime
 *  - Zangetsu    -> red
 */
@Composable
fun AnimeXTheme(content: @Composable () -> Unit) {
    val provider = ProviderPrefs.selected()
    val accent = Color(provider.accent)
    val accentSoft = Color(provider.accentSoft)

    val colors = darkColorScheme(
        primary = accent,
        onPrimary = TextPrimary,
        secondary = accentSoft,
        onSecondary = TextPrimary,
        background = BgDark,
        onBackground = TextPrimary,
        surface = BgSurface,
        onSurface = TextPrimary,
        surfaceVariant = BgCard,
        onSurfaceVariant = TextSecondary,
        outline = DividerColor,
    )

    CompositionLocalProvider(
        LocalProviderAccent provides accent,
        LocalProviderAccentSoft provides accentSoft,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}
