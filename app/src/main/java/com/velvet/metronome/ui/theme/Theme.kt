package com.velvet.metronome.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Composition-local exposing the active palette to all UI components. */
val LocalPalette = staticCompositionLocalOf<MetronomePalette> { MetronomePalette.Beige }

/** Composition-local exposing the active theme choice (so the picker can show
 *  which swatch is selected). */
val LocalThemeChoice = compositionLocalOf<ThemeChoice> { ThemeChoice.BEIGE }

@Composable
fun MetronomeTheme(
    themeChoice: ThemeChoice,
    content: @Composable () -> Unit,
) {
    val palette = themeChoice.toPalette()
    val isDark = themeChoice == ThemeChoice.DARK

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.tabBrown,
            onPrimary = palette.background,
            background = palette.background,
            surface = palette.background,
            onBackground = palette.bpmText,
            onSurface = palette.bpmText,
        )
    } else {
        lightColorScheme(
            primary = palette.tabBrown,
            onPrimary = palette.background,
            background = palette.background,
            surface = palette.background,
            onBackground = palette.bpmText,
            onSurface = palette.bpmText,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    // v0.8.2: override LocalTextStyle so every Text composable inherits the
    // bundled BittFontFamily (RobotoFlex) — devices with custom system fonts
    // (One UI, MIUI, etc.) no longer alter our typography.
    val baseTextStyle = LocalTextStyle.current.copy(fontFamily = BittFontFamily)
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalThemeChoice provides themeChoice,
        LocalTextStyle provides baseTextStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MetronomeTypography,
            content = content,
        )
    }
}
