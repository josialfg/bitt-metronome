package com.velvet.metronome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.metronome.model.Preset
import com.velvet.metronome.ui.theme.BittFontFamily
import com.velvet.metronome.ui.theme.LocalPalette

/**
 * v0.8 bank tabs row.
 *
 * Layout (left → right):
 *   - 4 brown bank tabs. Selected tab uses pure-white text + green underline.
 *     Unselected tabs use muted background-color text, no underline.
 *   - 1 NOW PLAYING indicator (rightmost) — split vertically:
 *       top half: darker bg (matches top tempo box), shows currently PLAYING BPM
 *       bottom half: lighter bg (matches bottom tempo box), shows QUEUED BPM
 *           (blank when nothing queued). No green underline on the indicator.
 *
 * The row uses [Arrangement.SpaceBetween] so the first tab touches the row's
 * left edge and the indicator touches its right edge. Caller controls the row's
 * horizontal extent (typically padded to match the boxes below).
 */
@Composable
fun PresetTabsRow(
    banks: List<Pair<Preset, Preset>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 78.dp,
    spacing: Dp = 4.dp,
) {
    // v1.0.2: 4 bank tabs only — evenly spaced (equal weight + uniform spacing).
    // NOW PLAYING is rendered by the parent in the RIGHT gutter as a mirror of
    // the lock icon, OUTSIDE the box right edge; this row spans exactly the box
    // width so the tabs sit INSIDE the box. (v1.0.1 had folded NOW PLAYING in as
    // a 5th cell — reverted.)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        banks.forEachIndexed { index, pair ->
            BankTab(
                pair = pair,
                selected = index == selectedIndex,
                height = tabHeight,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 2×2 grid arrangement for landscape — same tabs, no NOW PLAYING indicator
 *  (that one lives in the right column in landscape). */
@Composable
fun PresetTabsGrid(
    banks: List<Pair<Preset, Preset>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabWidth: Dp = 54.dp,
    tabHeight: Dp = 78.dp,
    spacing: Dp = 6.dp,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        banks.chunked(2).forEachIndexed { rowIdx, rowBanks ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowBanks.forEachIndexed { colIdx, pair ->
                    val globalIdx = rowIdx * 2 + colIdx
                    BankTab(
                        pair = pair,
                        selected = globalIdx == selectedIndex,
                        height = tabHeight,
                        onClick = { onSelect(globalIdx) },
                        modifier = Modifier.width(tabWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun BankTab(
    pair: Pair<Preset, Preset>,
    selected: Boolean,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(6.dp)
    val highBpmColor: Color = if (selected) Color.White else palette.background
    val lowBpmColor: Color = if (selected) Color.White.copy(alpha = 0.85f) else palette.background.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(palette.tabBrown)
            .clickable(onClick = onClick),
    ) {
        // v0.8.2: split-half layout matching NowPlayingIndicator. Each BPM
        // centered in its own half so the spacing reads identically across all
        // 5 cells in the row.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pair.first.bpm.toString(),
                    style = TextStyle(
                        fontFamily = BittFontFamily,
                        color = highBpmColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pair.second.bpm.toString(),
                    style = TextStyle(
                        fontFamily = BittFontFamily,
                        color = lowBpmColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(palette.playingDot),
            )
        }
    }
}

/** Rightmost split indicator: playing BPM on top, queued BPM on bottom. */
@Composable
fun NowPlayingIndicator(
    playingBpm: Int?,
    queuedBpm: Int?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .height(height)
            .clip(shape),
    ) {
        // Top half — playing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(palette.currentBox),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = playingBpm?.toString() ?: "—",
                style = TextStyle(
                    fontFamily = BittFontFamily,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        // Bottom half — queued (lighter shade so the split is visible).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(palette.currentBox.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = queuedBpm?.toString() ?: "—",
                style = TextStyle(
                    fontFamily = BittFontFamily,
                    color = Color.White.copy(alpha = if (queuedBpm == null) 0.4f else 0.85f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}
