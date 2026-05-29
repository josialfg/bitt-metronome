package com.velvet.metronome.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.velvet.metronome.R

/**
 * Bundled font family for the entire app. RobotoFlex is a *variable* font — one
 * .ttf covers every weight from Thin (100) to Black (900) and italic. Bundling
 * it means the rendering looks the same on every device (Samsung's One UI font,
 * Pixel's Roboto, etc. all get overridden).
 *
 * Defined weights — match what the typography uses + cover light, normal,
 * semi-bold, bold, italic per v0.8.2 brief.
 */
val BittFontFamily = FontFamily(
    Font(R.font.roboto_flex, weight = FontWeight.Light,    style = FontStyle.Normal),
    Font(R.font.roboto_flex, weight = FontWeight.Normal,   style = FontStyle.Normal),
    Font(R.font.roboto_flex, weight = FontWeight.Medium,   style = FontStyle.Normal),
    Font(R.font.roboto_flex, weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(R.font.roboto_flex, weight = FontWeight.Bold,     style = FontStyle.Normal),
    Font(R.font.roboto_flex, weight = FontWeight.Light,    style = FontStyle.Italic),
    Font(R.font.roboto_flex, weight = FontWeight.Normal,   style = FontStyle.Italic),
)
