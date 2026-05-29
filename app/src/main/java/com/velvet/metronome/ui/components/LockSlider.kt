package com.velvet.metronome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.velvet.metronome.ui.theme.LocalPalette

/**
 * Lock toggle — tap-only black circle in the left margin.
 *
 * The padlock glyph uses the "variant 1" geometry that won the v0.8 sampler:
 * the unlocked state is the closed shackle minus its right leg, leaving a clean
 * half-circle arc that terminates above the body's right side.
 */
@Composable
fun LockSlider(
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(palette.lockKnob)
            .clickable { onLockedChange(!locked) },
        contentAlignment = Alignment.Center,
    ) {
        LockGlyph(locked = locked, tint = palette.background)
    }
}

@Composable
private fun LockGlyph(locked: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 3.4f.dp.toPx()

        // Body — solid filled rounded rectangle.
        val bodyW = w * 0.74f
        val bodyH = h * 0.42f
        val bodyX = (w - bodyW) / 2f
        val bodyTop = h - bodyH - 1.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyX, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(2.5f.dp.toPx()),
        )

        // Shackle.
        val shackleW = bodyW * 0.62f
        val shackleLeft = (w - shackleW) / 2f
        val arcRect = Rect(
            left = shackleLeft,
            top = bodyTop - shackleW,
            right = shackleLeft + shackleW,
            bottom = bodyTop,
        )
        val legTopY = bodyTop
        val legBottomY = bodyTop + bodyH * 0.18f
        val leftLegX = shackleLeft
        val rightLegX = shackleLeft + shackleW

        val strokeStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val path = Path().apply {
            if (locked) {
                // Closed: left leg → arc → right leg back to body.
                moveTo(leftLegX, legBottomY)
                lineTo(leftLegX, legTopY)
                arcTo(arcRect, 180f, 180f, false)
                lineTo(rightLegX, legBottomY)
            } else {
                // Open: left leg + half-circle only — right leg omitted.
                moveTo(leftLegX, legBottomY)
                lineTo(leftLegX, legTopY)
                arcTo(arcRect, 180f, 180f, false)
            }
        }
        drawPath(path = path, color = tint, style = strokeStyle)
    }
}
