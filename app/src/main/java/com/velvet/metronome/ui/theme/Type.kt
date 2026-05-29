package com.velvet.metronome.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography uses [BittFontFamily] (bundled RobotoFlex) so the look is identical
 * on every device — no OEM font override (Samsung One UI, MIUI, etc.).
 */
val MetronomeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BittFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        color = BpmText,
    ),
    labelLarge = TextStyle(
        fontFamily = BittFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        color = BpmText,
    ),
    bodyLarge = TextStyle(
        fontFamily = BittFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = BpmText,
    ),
)
