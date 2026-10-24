package com.animexstream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.animexstream.app.data.Provider

/** The provider currently selected, readable anywhere in the tree. */
val LocalSelectedProvider = compositionLocalOf { Provider.MOVIEBOX }

/** The accent colour of the currently-selected provider. */
val LocalProviderAccent = compositionLocalOf { Color(Provider.MOVIEBOX.accent) }
val LocalProviderAccentSoft = compositionLocalOf { Color(Provider.MOVIEBOX.accentSoft) }

/**
 * Zangetsu-styled dark theme (deep near-black backgrounds) whose primary and
 * secondary colours follow the selected provider, read from the composition
 * local so changing the provider recomposes the tree WITHOUT destroying the
 * navigation host.
 */
@Composable
fun AnimeXTheme(content: @Composable () -> Unit) {
    val provider = LocalSelectedProvider.current
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
