package com.velvet.metronome.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velvet.metronome.data.SettingsRepository
import com.velvet.metronome.model.Preset
import com.velvet.metronome.ui.components.BottomActionRow
import com.velvet.metronome.ui.components.LockSlider
import com.velvet.metronome.ui.components.NowPlayingIndicator
import com.velvet.metronome.ui.components.NudgeRow
import com.velvet.metronome.ui.components.PresetTabsGrid
import com.velvet.metronome.ui.components.PresetTabsRow
import com.velvet.metronome.ui.components.TempoBox
import com.velvet.metronome.ui.theme.LocalPalette
import com.velvet.metronome.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

// Set this to `true` while diagnosing the "tap-upper-queues-bottom" report.
// Off by default — flip locally if you need to verify state indices on device.
private const val SHOW_DEBUG_LABELS = false

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
) {
    val palette = LocalPalette.current
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val visualOffsetMs by settingsRepo.visualOffsetMs.collectAsState(initial = 0)
    val keepInBackground by settingsRepo.keepInBackground.collectAsState(initial = true)
    val themeChoice by settingsRepo.themeChoice.collectAsState(initial = ThemeChoice.BEIGE)
    val scope = rememberCoroutineScope()

    val displayedBar  = if (state.isPlaying) state.bar  else -1
    val displayedBeat = if (state.isPlaying) state.beat else -1

    val banks = remember(state.bank) {
        (0..3).map { idx ->
            val pair = state.bank.drop(idx * 2).take(2)
            pair[0] to pair[1]
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // v0.9.2: tablets (smallestWidth ≥ 600dp) always use the landscape-style
    // layout (lock + bank grid + NOW PLAYING in a left column, boxes centered),
    // and the whole composition is width-capped + centered so it doesn't stretch
    // edge-to-edge on a large screen — "locked to the center" as in phone
    // landscape. Phones are unaffected: the cap (900dp) exceeds their width.
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val useLandscape = isLandscape || isTablet

    val topIsPlaying    = state.isPlaying && state.playingBoxIndex == 0
    val bottomIsPlaying = state.isPlaying && state.playingBoxIndex == 1

    // Green border rule (v0.7+):
    //   - Not playing → border on `selectedBoxIndex` (initial-pick visual).
    //   - Playing AND user tapped non-active to queue → border on `queuedBoxIndex`.
    //   - Otherwise → no border.
    val topSelected = when {
        !state.isPlaying -> state.selectedBoxIndex == 0
        state.queuedBoxIndex == 0 -> true
        else -> false
    }
    val bottomSelected = when {
        !state.isPlaying -> state.selectedBoxIndex == 1
        state.queuedBoxIndex == 1 -> true
        else -> false
    }

    val pendingPair = state.pendingPair
    val transitioningTop = state.bankTransitioning && state.playingBoxIndex == 1
    val transitioningBottom = state.bankTransitioning && state.playingBoxIndex == 0
    val topPreset = if (transitioningTop && pendingPair != null) pendingPair[0] else state.top
    val bottomPreset = if (transitioningBottom && pendingPair != null) pendingPair[0] else state.bottom

    val boxColor = palette.currentBox

    // For the NOW PLAYING indicator pill.
    val nowPlayingBpm: Int? = when {
        !state.isPlaying -> null
        state.playingBoxIndex == 0 -> state.top.bpm
        else -> state.bottom.bpm
    }
    val nowQueuedBpm: Int? = when {
        !state.isPlaying -> null
        state.queuedBoxIndex == 0 -> state.top.bpm
        state.queuedBoxIndex == 1 -> state.bottom.bpm
        state.pendingBankIndex != null -> pendingPair?.get(0)?.bpm
        else -> null
    }

    val topDebug = if (SHOW_DEBUG_LABELS) "P:${state.playingBoxIndex} S:${state.selectedBoxIndex} Q:${state.queuedBoxIndex ?: "-"}" else null
    val bottomDebug = topDebug // Same info on both for now.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            // v0.8.2: hard outer horizontal margin so no element can reach the
            // physical screen edge on narrow phones. Combined with the per-box
            // padding inside, this guarantees a safe gutter on either side.
            .padding(horizontal = 8.dp),
    ) {
        if (useLandscape) {
            // Cap + center the landscape composition. On phones the cap is a
            // no-op; on tablets it keeps the layout locked to the screen center.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 900.dp),
            ) {
            LandscapeLayout(
                state = state,
                banks = banks,
                topPreset = topPreset,
                bottomPreset = bottomPreset,
                topIsPlaying = topIsPlaying,
                bottomIsPlaying = bottomIsPlaying,
                topSelected = topSelected,
                bottomSelected = bottomSelected,
                transitioningTop = transitioningTop,
                transitioningBottom = transitioningBottom,
                displayedBar = displayedBar,
                displayedBeat = displayedBeat,
                boxColor = boxColor,
                topDebug = topDebug,
                bottomDebug = bottomDebug,
                nowPlayingBpm = nowPlayingBpm,
                nowQueuedBpm = nowQueuedBpm,
                viewModel = viewModel,
                visualOffsetMs = visualOffsetMs,
                keepInBackground = keepInBackground,
                themeChoice = themeChoice,
                onVisualOffsetChange = { ms -> scope.launch { settingsRepo.setVisualOffsetMs(ms) } },
                onKeepInBgChange = { v -> scope.launch { settingsRepo.setKeepInBackground(v) } },
                onThemeChange = { t -> scope.launch { settingsRepo.setThemeChoice(t) } },
            )
            }
        } else {
            PortraitLayout(
                state = state,
                banks = banks,
                topPreset = topPreset,
                bottomPreset = bottomPreset,
                topIsPlaying = topIsPlaying,
                bottomIsPlaying = bottomIsPlaying,
                topSelected = topSelected,
                bottomSelected = bottomSelected,
                transitioningTop = transitioningTop,
                transitioningBottom = transitioningBottom,
                displayedBar = displayedBar,
                displayedBeat = displayedBeat,
                boxColor = boxColor,
                topDebug = topDebug,
                bottomDebug = bottomDebug,
                nowPlayingBpm = nowPlayingBpm,
                nowQueuedBpm = nowQueuedBpm,
                viewModel = viewModel,
                visualOffsetMs = visualOffsetMs,
                keepInBackground = keepInBackground,
                themeChoice = themeChoice,
                onVisualOffsetChange = { ms -> scope.launch { settingsRepo.setVisualOffsetMs(ms) } },
                onKeepInBgChange = { v -> scope.launch { settingsRepo.setKeepInBackground(v) } },
                onThemeChange = { t -> scope.launch { settingsRepo.setThemeChoice(t) } },
            )
        }
    }
}

// ──────────────────────────────── PORTRAIT ────────────────────────────────

@Composable
private fun PortraitLayout(
    state: MainViewModel.UiState,
    banks: List<Pair<Preset, Preset>>,
    topPreset: Preset,
    bottomPreset: Preset,
    topIsPlaying: Boolean,
    bottomIsPlaying: Boolean,
    topSelected: Boolean,
    bottomSelected: Boolean,
    transitioningTop: Boolean,
    transitioningBottom: Boolean,
    displayedBar: Int,
    displayedBeat: Int,
    boxColor: androidx.compose.ui.graphics.Color,
    topDebug: String?,
    bottomDebug: String?,
    nowPlayingBpm: Int?,
    nowQueuedBpm: Int?,
    viewModel: MainViewModel,
    visualOffsetMs: Int,
    keepInBackground: Boolean,
    themeChoice: ThemeChoice,
    onVisualOffsetChange: (Int) -> Unit,
    onKeepInBgChange: (Boolean) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
) {
    // squareSize is computed at the ROOT of the layout so callers below can size
    // the boxes consistently.
    val boxHorizontalPadding = 18.dp
    val lockCircleSize = 56.dp
    // Approximate vertical budget reservations (lock-row, action-row, gaps).
    val topRowHeight = 90.dp
    val bottomRowHeight = 88.dp
    val midGap = 10.dp
    // v1.0.1: 64 → 80 dp. The real nudge-row height (~60 dp: 32 sp readout +
    // vertical padding) plus midGap exceeded the old reserve by a few dp, so the
    // box column overflowed and the bottom nudge row's BPM (e.g. `300`) was
    // clipped at the top by ~1-5 px. The larger reserve shrinks each box's
    // height-constrained `squareSize` enough to give the nudge rows headroom.
    val nudgeRowReserve = 80.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidth = this.maxWidth
        val totalHeight = this.maxHeight
        val boxesArea = totalHeight - topRowHeight - bottomRowHeight
        val perBoxHeight = (boxesArea - midGap) / 2 - nudgeRowReserve
        val perBoxWidth = totalWidth - boxHorizontalPadding * 2
        val squareSize = minOf(perBoxWidth, perBoxHeight).coerceAtLeast(80.dp)
        // v1.0: distance from the column's right edge to the (centered, often
        // height-constrained) box's right edge. The top tab + NOW PLAYING group is
        // end-padded by this so its right edge lands ON the box right edge — the
        // group stays INSIDE the box margin instead of running to the screen edge.
        val sideSlack = boxHorizontalPadding + (perBoxWidth - squareSize) / 2

        Column(modifier = Modifier.fillMaxSize()) {
            // Top row: 3 zones — [left gutter: lock] [4 bank tabs] [right gutter:
            // NOW PLAYING]. v1.0.2: each gutter zone is `sideSlack` wide (the
            // box's side slack), so the tabs span EXACTLY the box width and sit
            // INSIDE the box, evenly spaced. The lock centers in the left gutter
            // and NOW PLAYING centers in the right gutter as its mirror — both
            // OUTSIDE the box horizontal margin. Gutter is clamped to ≥ lock
            // width so the lock never clips when the box is width-constrained.
            val gutterWidth = maxOf(sideSlack, lockCircleSize)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(gutterWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    LockSlider(
                        locked = state.locked,
                        onLockedChange = viewModel::setLocked,
                        modifier = Modifier.size(lockCircleSize),
                    )
                }
                PresetTabsRow(
                    banks = banks,
                    selectedIndex = state.selectedBankIndex,
                    onSelect = viewModel::selectBank,
                    modifier = Modifier.weight(1f),
                    spacing = 2.dp,
                )
                Box(
                    modifier = Modifier.width(gutterWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    NowPlayingIndicator(
                        playingBpm = nowPlayingBpm,
                        queuedBpm = nowQueuedBpm,
                        height = 78.dp,
                        modifier = Modifier.width(lockCircleSize),
                    )
                }
            }

            // Two stacked square tempo boxes.
            //
            // v0.9.1: the `<< < BPM > >>` nudge row is hoisted OUT of TempoBox
            // and rendered here as a sibling. The wrapper Box.fillMaxWidth()
            // screen-centers the row so all 5 elements always render — without
            // the hoist, `>>` was clipped on narrow phones because TempoBox's
            // outer Column has `Modifier.width(squareSize)`.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = boxHorizontalPadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(midGap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxSlot(
                        boxIndex = 0,
                        preset = topPreset,
                        squareSize = squareSize,
                        isPlaying = topIsPlaying,
                        selected = topSelected,
                        transitioning = transitioningTop,
                        state = state,
                        displayedBar = displayedBar,
                        displayedBeat = displayedBeat,
                        boxColor = boxColor,
                        debugLabel = topDebug,
                        viewModel = viewModel,
                    )
                    NudgeRow(
                        bpm = topPreset.bpm,
                        enabled = !state.locked,
                        onNudge = { d -> viewModel.adjustBoxBpm(0, d) },
                        onSlideBpm = { steps -> viewModel.adjustBoxBpm(0, steps) },
                    )
                    BoxSlot(
                        boxIndex = 1,
                        preset = bottomPreset,
                        squareSize = squareSize,
                        isPlaying = bottomIsPlaying,
                        selected = bottomSelected,
                        transitioning = transitioningBottom,
                        state = state,
                        displayedBar = displayedBar,
                        displayedBeat = displayedBeat,
                        boxColor = boxColor,
                        debugLabel = bottomDebug,
                        viewModel = viewModel,
                    )
                    NudgeRow(
                        bpm = bottomPreset.bpm,
                        enabled = !state.locked,
                        onNudge = { d -> viewModel.adjustBoxBpm(1, d) },
                        onSlideBpm = { steps -> viewModel.adjustBoxBpm(1, steps) },
                    )
                }
            }

            BottomActionRow(
                isPlaying = state.isPlaying,
                locked = state.locked,
                visualOffsetMs = visualOffsetMs,
                onVisualOffsetChange = onVisualOffsetChange,
                keepInBackground = keepInBackground,
                onKeepInBackgroundChange = onKeepInBgChange,
                themeChoice = themeChoice,
                onThemeChange = onThemeChange,
                onTogglePlay = viewModel::togglePlay,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

// ─────────────────────────────── LANDSCAPE ────────────────────────────────

@Composable
private fun LandscapeLayout(
    state: MainViewModel.UiState,
    banks: List<Pair<Preset, Preset>>,
    topPreset: Preset,
    bottomPreset: Preset,
    topIsPlaying: Boolean,
    bottomIsPlaying: Boolean,
    topSelected: Boolean,
    bottomSelected: Boolean,
    transitioningTop: Boolean,
    transitioningBottom: Boolean,
    displayedBar: Int,
    displayedBeat: Int,
    boxColor: androidx.compose.ui.graphics.Color,
    topDebug: String?,
    bottomDebug: String?,
    nowPlayingBpm: Int?,
    nowQueuedBpm: Int?,
    viewModel: MainViewModel,
    visualOffsetMs: Int,
    keepInBackground: Boolean,
    themeChoice: ThemeChoice,
    onVisualOffsetChange: (Int) -> Unit,
    onKeepInBgChange: (Boolean) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LockSlider(
                locked = state.locked,
                onLockedChange = viewModel::setLocked,
                modifier = Modifier.size(56.dp),
            )
            PresetTabsGrid(
                banks = banks,
                selectedIndex = state.selectedBankIndex,
                onSelect = viewModel::selectBank,
            )
            // NOW PLAYING indicator below the tab grid in landscape.
            NowPlayingIndicator(
                playingBpm = nowPlayingBpm,
                queuedBpm = nowQueuedBpm,
                height = 78.dp,
                modifier = Modifier.width(54.dp),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            val nudgeRowReserve = 56.dp
            val gap = 10.dp
            val perBoxWidth = (this.maxWidth - gap) / 2
            val squareSize = minOf(perBoxWidth, this.maxHeight - nudgeRowReserve).coerceAtLeast(80.dp)

            // v0.9.1: landscape now stacks each box + its nudge row vertically
            // because TempoBox no longer renders the nudge row internally.
            // v0.9.2: each stack is constrained to `squareSize` width. Without
            // this the NudgeRow's Box(fillMaxWidth) grabbed the full Row width and
            // pushed the SECOND box off-screen — only one box (one "setlist") was
            // visible in landscape.
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.width(squareSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxSlot(
                        boxIndex = 0, preset = topPreset, squareSize = squareSize,
                        isPlaying = topIsPlaying, selected = topSelected,
                        transitioning = transitioningTop, state = state,
                        displayedBar = displayedBar, displayedBeat = displayedBeat,
                        boxColor = boxColor, debugLabel = topDebug,
                        viewModel = viewModel,
                    )
                    NudgeRow(
                        bpm = topPreset.bpm,
                        enabled = !state.locked,
                        onNudge = { d -> viewModel.adjustBoxBpm(0, d) },
                        onSlideBpm = { steps -> viewModel.adjustBoxBpm(0, steps) },
                    )
                }
                Column(
                    modifier = Modifier.width(squareSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxSlot(
                        boxIndex = 1, preset = bottomPreset, squareSize = squareSize,
                        isPlaying = bottomIsPlaying, selected = bottomSelected,
                        transitioning = transitioningBottom, state = state,
                        displayedBar = displayedBar, displayedBeat = displayedBeat,
                        boxColor = boxColor, debugLabel = bottomDebug,
                        viewModel = viewModel,
                    )
                    NudgeRow(
                        bpm = bottomPreset.bpm,
                        enabled = !state.locked,
                        onNudge = { d -> viewModel.adjustBoxBpm(1, d) },
                        onSlideBpm = { steps -> viewModel.adjustBoxBpm(1, steps) },
                    )
                }
            }
        }

        BottomActionRow(
            isPlaying = state.isPlaying,
            locked = state.locked,
            visualOffsetMs = visualOffsetMs,
            onVisualOffsetChange = onVisualOffsetChange,
            keepInBackground = keepInBackground,
            onKeepInBackgroundChange = onKeepInBgChange,
            themeChoice = themeChoice,
            onThemeChange = onThemeChange,
            onTogglePlay = viewModel::togglePlay,
            modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 16.dp),
            vertical = true,
        )
    }
}

// ──────────────────────────── single-box wiring ───────────────────────────

@Composable
private fun BoxSlot(
    boxIndex: Int,
    preset: Preset,
    squareSize: Dp,
    isPlaying: Boolean,
    selected: Boolean,
    transitioning: Boolean,
    state: MainViewModel.UiState,
    displayedBar: Int,
    displayedBeat: Int,
    boxColor: androidx.compose.ui.graphics.Color,
    debugLabel: String?,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    TempoBox(
        modifier = modifier,
        preset = preset,
        squareSize = squareSize,
        pulseKey = if (isPlaying) state.tick else 0L,
        currentBar = if (isPlaying) displayedBar else -1,
        currentBeat = if (isPlaying) displayedBeat else -1,
        isPlaying = isPlaying,
        isActiveBox = isPlaying,
        selected = selected,
        greyedOut = state.locked && !isPlaying,
        bankTransitioning = transitioning,
        editMode = state.editingBox == boxIndex,
        enabled = !state.locked,
        boxColor = boxColor,
        onToggleEdit = { viewModel.toggleEditing(boxIndex) },
        onAdjustBeats = { d -> viewModel.adjustBoxBeatsPerBar(boxIndex, d) },
        onAdjustBars  = { d -> viewModel.adjustBoxBarsPerPhrase(boxIndex, d) },
        onAdjustEndInBar = { d -> viewModel.adjustBoxEndInBar(boxIndex, d) },
        onToggleSubdivision = {
            val current = if (boxIndex == 0) state.top.subdivision else state.bottom.subdivision
            viewModel.setBoxSubdivision(boxIndex, if (current == 2) 1 else 2)
        },
        onBoxTap = { viewModel.onBoxTap(boxIndex) },
        onBoxDoubleTap = { viewModel.onBoxDoubleTap(boxIndex) },
        debugLabel = debugLabel,
    )
}
