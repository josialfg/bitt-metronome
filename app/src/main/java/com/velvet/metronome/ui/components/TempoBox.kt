package com.velvet.metronome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.metronome.model.Preset
import com.velvet.metronome.ui.theme.CellFill
import com.velvet.metronome.ui.theme.CellOutline
import com.velvet.metronome.ui.theme.BittFontFamily
import com.velvet.metronome.ui.theme.LocalPalette

private val DotSize = 8.dp

/**
 * One tempo box + its BPM nudge row.
 *
 *  preset            preset being rendered
 *  isPlaying         engine producing ticks for THIS box (green dot)
 *  greyedOut         dim the *grid only* (BPM text stays full color)
 *  bankTransitioning render with a darker overlay — used on the upper box while
 *                    a bank switch is queued but hasn't fired yet
 *  editMode          replace grid with the inline edit panel; hide dot + pencil
 *  enabled           disable interactive controls (lock mode)
 *  onSlideBpm        called with accumulated drag delta (dx, dy); caller applies
 *                    its own units → BPM mapping
 */
@Composable
fun TempoBox(
    preset: Preset,
    squareSize: Dp,
    pulseKey: Long,
    currentBar: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    isActiveBox: Boolean,
    selected: Boolean,
    greyedOut: Boolean,
    bankTransitioning: Boolean,
    editMode: Boolean,
    enabled: Boolean,
    boxColor: Color,
    onToggleEdit: () -> Unit,
    onAdjustBeats: (Int) -> Unit,
    onAdjustBars: (Int) -> Unit,
    onAdjustEndInBar: (Int) -> Unit,
    onToggleSubdivision: () -> Unit,
    onBoxTap: () -> Unit,
    onBoxDoubleTap: () -> Unit,
    /** Optional one-line debug overlay (e.g. "P:0 S:1 Q:0"). Null hides it. */
    debugLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val boxAlpha = if (bankTransitioning) 0.55f else 1f
    val cellAlpha = when {
        bankTransitioning -> 0.55f
        greyedOut -> 0.4f
        else -> 1f
    }
    val boxShape = RoundedCornerShape(14.dp)
    // Constrain the column to the square's width so the nudge row doesn't bloat
    // the TempoBox horizontally (was causing the second box in landscape to be
    // pushed off-screen in rev6).
    Column(
        modifier = modifier.width(squareSize),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(squareSize)
                .then(
                    if (selected) Modifier.border(2.dp, palette.playingDot, boxShape)
                    else Modifier
                )
                .clip(boxShape)
                .background(boxColor.copy(alpha = boxAlpha))
                // v0.8: pointerInput handles single + double tap. Single = queue
                // the box (waits for the configured endInBar). Double = force swap
                // at end of current bar.
                .pointerInput(editMode) {
                    if (editMode) return@pointerInput
                    detectTapGestures(
                        onTap = { onBoxTap() },
                        onDoubleTap = { onBoxDoubleTap() },
                    )
                },
        ) {
            if (editMode) {
                EditPanel(
                    preset = preset,
                    onAdjustBeats = onAdjustBeats,
                    onAdjustBars = onAdjustBars,
                    onAdjustEndInBar = onAdjustEndInBar,
                    onToggleSubdivision = onToggleSubdivision,
                    onClose = onToggleEdit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            } else {
                BeatGrid(
                    beatsPerBar = preset.beatsPerBar,
                    barsPerPhrase = preset.barsPerPhrase,
                    currentBar = currentBar,
                    currentBeat = currentBeat,
                    pulseKey = pulseKey,
                    outlineColor = CellOutline.copy(alpha = cellAlpha),
                    fillColor = CellFill.copy(alpha = cellAlpha),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(DotSize)
                        .clip(CircleShape)
                        .background((if (isPlaying) palette.playingDot else palette.idleDot).copy(alpha = cellAlpha)),
                )

                if (enabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 28.dp, end = 6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        EditIcon(tint = palette.cellOutline.copy(alpha = cellAlpha))
                    }
                }

                // v0.8 debug label (top-left). When non-null, shows state info
                // helpful for diagnosing the "tap-upper-queues-bottom" report.
                debugLabel?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            fontFamily = BittFontFamily,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 9.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 6.dp, top = 4.dp),
                    )
                }
            }
        }

    }
}

/**
 * v0.9.1: hoisted out of [TempoBox] so its parent can give it a screen-wide
 * container that center-anchors the row. Inside TempoBox the outer Column has
 * `Modifier.width(squareSize)` which clipped the trailing `>>` on narrow phones.
 *
 * Caller wraps this in `Box(Modifier.fillMaxWidth(), contentAlignment = Center)`
 * so all 5 elements always render even when `squareSize < natural row width`.
 */
@Composable
fun NudgeRow(
    bpm: Int,
    enabled: Boolean,
    onNudge: (Int) -> Unit,
    onSlideBpm: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // v1.0: scale the whole row down when the available width is tight — e.g.
        // the per-box column in landscape on narrow phones (phone 2), where the
        // trailing `>>` was wrapping to a second line. The reference (300.dp)
        // sits just above the row's natural width (~264.dp) so any width ≥ ref
        // stays at full size; below it everything shrinks proportionally and the
        // row always fits on one line. Floor keeps it legible.
        val scale = (maxWidth / 300.dp).coerceIn(0.6f, 1f)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NudgeArrow("<<", enabled, scale) { onNudge(-5) }
            NudgeArrow("<",  enabled, scale) { onNudge(-1) }
            BpmReadout(bpm = bpm, enabled = enabled, scale = scale, onSlideBpm = onSlideBpm)
            NudgeArrow(">",  enabled, scale) { onNudge(+1) }
            NudgeArrow(">>", enabled, scale) { onNudge(+5) }
        }
    }
}

@Composable
private fun NudgeArrow(label: String, enabled: Boolean, scale: Float, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        text = label,
        color = if (enabled) palette.bpmText else palette.bpmText.copy(alpha = 0.35f),
        fontSize = 26.sp * scale,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp * scale, vertical = 2.dp),
    )
}

@Composable
private fun BpmReadout(bpm: Int, enabled: Boolean, scale: Float, onSlideBpm: (Int) -> Unit) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    // 1 BPM per 16 dp of motion. Accumulates dx + (-dy): right/up → positive.
    val pxPerStep = with(density) { 16.dp.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            // v1.0: scales with the row; base 112.dp comfortably fits a 3-digit
            // BPM at 32.sp bold (the phone-1 portrait `300` hair-clip). maxLines=1
            // + softWrap=false guarantee one line at any scale.
            .width(112.dp * scale)
            .padding(vertical = 8.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onDrag = { _, dragAmount ->
                        // Right & Up = increase; Left & Down = decrease.
                        accumulated += dragAmount.x - dragAmount.y
                        val steps = (accumulated / pxPerStep).toInt()
                        if (steps != 0) {
                            onSlideBpm(steps)
                            accumulated -= steps * pxPerStep
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bpm.toString(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = BittFontFamily,
                color = palette.bpmText,
                fontSize = 32.sp * scale,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

@Composable
private fun EditIcon(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val s = size.minDimension
        val stroke = 1.6f.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(s * 0.20f, s * 0.78f),
            end = Offset(s * 0.78f, s * 0.20f),
            strokeWidth = stroke,
        )
        drawCircle(
            color = tint,
            radius = stroke * 1.1f,
            center = Offset(s * 0.14f, s * 0.86f),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.72f, s * 0.10f),
            size = Size(s * 0.20f, s * 0.20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
private fun EditPanel(
    preset: Preset,
    onAdjustBeats: (Int) -> Unit,
    onAdjustBars: (Int) -> Unit,
    onAdjustEndInBar: (Int) -> Unit,
    onToggleSubdivision: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        // Stepper rows scroll when the box is too small to show all of them.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EditRow(
                label = "Beat(s)",
                value = preset.beatsPerBar.toString(),
                onMinus = { onAdjustBeats(-1) },
                onPlus = { onAdjustBeats(+1) },
            )
            EditRow(
                label = "Bar(s)",
                value = preset.barsPerPhrase.toString(),
                onMinus = { onAdjustBars(-1) },
                onPlus = { onAdjustBars(+1) },
            )
            EditRow(
                label = "End in Bar",
                value = if (preset.endInBar == 0) "—" else preset.endInBar.toString(),
                onMinus = { onAdjustEndInBar(-1) },
                onPlus = { onAdjustEndInBar(+1) },
            )
            // Subdivision: tap row to toggle Off ↔ 8ths.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleSubdivision)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Subdivision",
                    style = TextStyle(fontFamily = BittFontFamily, color = CellOutline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CellOutline.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (preset.subdivision == 2) "8ths" else "Off",
                        style = TextStyle(
                            fontFamily = BittFontFamily,
                            color = CellOutline,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        // Done button stays pinned to the bottom of the panel.
        Text(
            "Done",
            style = TextStyle(
                fontFamily = BittFontFamily,
                color = CellOutline,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CellOutline.copy(alpha = 0.18f))
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}


@Composable
private fun EditRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = BittFontFamily, color = CellOutline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        StepperBtn("-", onClick = onMinus)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                style = TextStyle(
                    fontFamily = BittFontFamily,
                    color = CellOutline,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        StepperBtn("+", onClick = onPlus)
    }
}

@Composable
private fun StepperBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(CellOutline.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = BittFontFamily,
                color = CellOutline,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
