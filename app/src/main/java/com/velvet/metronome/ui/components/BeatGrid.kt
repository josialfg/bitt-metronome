package com.velvet.metronome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.velvet.metronome.ui.theme.CellFill
import com.velvet.metronome.ui.theme.CellOutline
import kotlinx.coroutines.delay

/**
 * Dynamic beat grid: rows = bars within phrase, columns = beats within bar.
 *
 * Beat advances left → right within a row; at end of row, wraps to the next
 * row down (= next bar). The mockup's 6×8 maps to 6 columns (beats) × 8 rows
 * (bars). A cell is "lit" briefly each time pulseKey changes — subdivisions
 * re-light the same cell.
 *
 * Pass currentBar = -1 to render the grid with no highlight (playback stopped).
 */
@Composable
fun BeatGrid(
    beatsPerBar: Int,
    barsPerPhrase: Int,
    currentBar: Int,
    currentBeat: Int,
    pulseKey: Long,
    modifier: Modifier = Modifier,
    outlineColor: Color = CellOutline,
    fillColor: Color = CellFill,
    cellCornerRadius: Dp = 2.dp,
    pulseDurationMs: Long = 80L,
) {
    val rows = barsPerPhrase.coerceAtLeast(1)
    val cols = beatsPerBar.coerceAtLeast(1)

    // Brief on-pulse so consecutive ticks on the same cell still read as
    // "re-lit". Keyed on pulseKey + a separate lit flag so the off-state
    // happens in between ticks.
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(pulseKey, currentBar, currentBeat) {
        if (currentBar >= 0 && currentBeat >= 0 && pulseKey > 0L) {
            lit = true
            delay(pulseDurationMs)
            lit = false
        } else {
            lit = false
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        Canvas(modifier = Modifier.matchParentSize()) {
            val padX = size.width * 0.06f
            val padY = size.height * 0.06f
            val innerW = size.width - padX * 2f
            val innerH = size.height - padY * 2f
            val gapX = innerW * 0.04f / (cols + 1)
            val gapY = innerH * 0.04f / (rows + 1)
            val cellW = (innerW - gapX * (cols - 1)) / cols
            val cellH = (innerH - gapY * (rows - 1)) / rows
            val cornerPx = cellCornerRadius.toPx()
            val strokePx = 1.5f.dp.toPx()

            // Inset the filled rect by half a stroke so the lit cell's visible edge
            // matches the unlit cell's stroke edge — keeps the cell "size" constant
            // when toggling on/off, no perceived bloom.
            val inset = strokePx / 2f
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x = padX + col * (cellW + gapX)
                    val y = padY + row * (cellH + gapY)
                    val isActive = lit && row == currentBar && col == currentBeat
                    if (isActive) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(x + inset, y + inset),
                            size = Size(cellW - strokePx, cellH - strokePx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                        )
                    } else {
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = Offset(x + inset, y + inset),
                            size = Size(cellW - strokePx, cellH - strokePx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
            }
            // silence "unused" on w/h
            @Suppress("UNUSED_EXPRESSION") (w to h)
        }
    }
}
