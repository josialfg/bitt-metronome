package com.velvet.metronome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.metronome.audio.MetronomeEngine
import com.velvet.metronome.ui.theme.BittFontFamily
import com.velvet.metronome.ui.theme.LocalPalette
import com.velvet.metronome.ui.theme.MetronomePalette
import com.velvet.metronome.ui.theme.ThemeChoice

/**
 * Bottom action row — v0.8.
 *  Portrait: [Settings] … [Play/Stop]   (theme moved INTO settings sheet)
 *  Landscape (vertical=true): same buttons stacked, Play/Stop at the bottom.
 *
 * Play/Stop pill has a fixed width so toggling between "Play" / "Stop" doesn't
 * change the layout size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomActionRow(
    isPlaying: Boolean,
    locked: Boolean,
    visualOffsetMs: Int,
    onVisualOffsetChange: (Int) -> Unit,
    keepInBackground: Boolean,
    onKeepInBackgroundChange: (Boolean) -> Unit,
    themeChoice: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val palette = LocalPalette.current
    var settingsOpen by remember { mutableStateOf(false) }
    val settingsSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (vertical) {
        Column(
            modifier = modifier
                .padding(horizontal = 4.dp)
                .width(112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                enabled = !locked,
                onClick = { settingsOpen = true },
                tint = palette.bpmText,
            ) { SettingsGlyph(it) }
            Spacer(Modifier.weight(1f))
            PlayStopButton(isPlaying = isPlaying, onClick = onTogglePlay)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = !locked,
                onClick = { settingsOpen = true },
                tint = palette.bpmText,
            ) { SettingsGlyph(it) }
            Spacer(Modifier.weight(1f))
            PlayStopButton(isPlaying = isPlaying, onClick = onTogglePlay)
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = settingsSheet,
            containerColor = palette.background,
        ) {
            SettingsContent(
                visualOffsetMs = visualOffsetMs,
                onVisualOffsetChange = onVisualOffsetChange,
                keepInBackground = keepInBackground,
                onKeepInBackgroundChange = onKeepInBackgroundChange,
                themeChoice = themeChoice,
                onThemeChange = onThemeChange,
            )
        }
    }
}

@Composable
private fun IconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color,
    glyph: @Composable (Color) -> Unit,
) {
    val palette = LocalPalette.current
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.lockKnob.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        glyph(effectiveTint)
    }
}

@Composable
private fun SettingsGlyph(tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 2.dp)
                    .background(tint),
            )
        }
    }
}

/** v0.8: fixed width so Play/Stop don't visually jitter between states. */
@Composable
private fun PlayStopButton(isPlaying: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    // Full opacity regardless of lock state — play/stop is the one thing that
    // MUST stay clearly tappable even when the rest is locked.
    val bg = if (isPlaying) palette.playingDot else palette.tabBrown
    Box(
        modifier = Modifier
            // v1.0: 108 → 76 dp. Old pill fit ~3× "Play"; this fits ~2×. Still a
            // FIXED width so Play↔Stop never resizes/jitters between states.
            .width(76.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isPlaying) "Stop" else "Play",
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = BittFontFamily,
                color = palette.background,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    visualOffsetMs: Int,
    onVisualOffsetChange: (Int) -> Unit,
    keepInBackground: Boolean,
    onKeepInBackgroundChange: (Boolean) -> Unit,
    themeChoice: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
) {
    val palette = LocalPalette.current
    var sliderValue by remember(visualOffsetMs) { mutableIntStateOf(visualOffsetMs) }

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Settings",
            style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )

        // Visual offset slider.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Visual offset", style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text("$sliderValue ms", style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText, fontSize = 14.sp, fontFeatureSettings = "tnum"))
            }
            Text(
                "Delay cell highlight after click. Adjust until sound and visual feel synced.",
                style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText.copy(alpha = 0.6f), fontSize = 12.sp),
            )
            Slider(
                value = sliderValue.toFloat(),
                onValueChange = { sliderValue = it.toInt() },
                onValueChangeFinished = { onVisualOffsetChange(sliderValue) },
                valueRange = 0f..MetronomeEngine.VISUAL_OFFSET_MAX_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = palette.tabBrown,
                    activeTrackColor = palette.tabBrown,
                    inactiveTrackColor = palette.lockKnob.copy(alpha = 0.25f),
                ),
            )
        }

        // Theme picker — moved here from the standalone bottom-bar button (v0.8).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme", style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ThemeSwatch(MetronomePalette.Beige, "Beige", themeChoice == ThemeChoice.BEIGE) { onThemeChange(ThemeChoice.BEIGE) }
                ThemeSwatch(MetronomePalette.Dark,  "Dark",  themeChoice == ThemeChoice.DARK)  { onThemeChange(ThemeChoice.DARK) }
                ThemeSwatch(MetronomePalette.Light, "Light", themeChoice == ThemeChoice.LIGHT) { onThemeChange(ThemeChoice.LIGHT) }
            }
        }

        // Keep in background toggle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep playing in background", style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text("If off, playback stops when you leave the app.", style = TextStyle(fontFamily = BittFontFamily, color = palette.bpmText.copy(alpha = 0.6f), fontSize = 12.sp))
            }
            Switch(
                checked = keepInBackground,
                onCheckedChange = onKeepInBackgroundChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.background,
                    checkedTrackColor = palette.tabBrown,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

// v0.8.2: the lock-icon sampler is gone — variant 1 won the selection. The
// LockGlyph in LockSlider hard-codes that geometry. The DataStore key
// `lock_variant` is retained in SettingsRepository for backward compatibility
// but no longer read or written.

@Composable
private fun ThemeSwatch(
    palette: MetronomePalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val displayPalette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) displayPalette.tabBrown else displayPalette.bpmText.copy(alpha = 0.3f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.currentBox),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = TextStyle(
                fontFamily = BittFontFamily,
                color = if (selected) displayPalette.bpmText else displayPalette.bpmText.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
