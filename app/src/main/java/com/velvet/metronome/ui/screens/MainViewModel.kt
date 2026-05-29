package com.velvet.metronome.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velvet.metronome.audio.MetronomeEngine
import com.velvet.metronome.data.PresetRepository
import com.velvet.metronome.data.SettingsRepository
import com.velvet.metronome.model.Preset
import com.velvet.metronome.model.TempoConfig
import com.velvet.metronome.model.defaultPresets
import com.velvet.metronome.model.resolvedEndInBar
import com.velvet.metronome.model.toConfig
import com.velvet.metronome.service.startMetronomePlayback
import com.velvet.metronome.service.stopMetronomePlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * v2-rev4 main-screen state.
 *
 *   Bank = a pair of presets (a setlist). 4 banks × 2 presets = 8 saveable.
 *   Within a bank, swap is MANUAL: tap the non-active box to queue it; engine
 *   swaps at end of CURRENT bar (rev4: was end-of-phrase before).
 *
 *   playingBoxIndex tracks which box is currently audible.
 *   pendingBankIndex non-null while a bank switch is queued.
 *
 *   swapArmed handshake (rev4): when the engine reports a swap, we don't apply
 *   the visual state change immediately — we *arm* it. The change (toggle
 *   playingBoxIndex, promote pendingBank, etc.) commits atomically on the very
 *   next (bar=0, beat=0) beat event. This prevents the "stale lit cell after
 *   swap" bug where the old preset's last beat coordinates leaked into the new
 *   active box.
 *
 *   Lock mode: edits and selection changes blocked; play/stop and bank switching
 *   remain interactable. Box backgrounds stay full opacity. Only the non-selected
 *   box's cells are greyed (alpha 0.4). Selected box keeps green outline.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val presetRepo = PresetRepository(application)
    private val settingsRepo = SettingsRepository(application)

    data class UiState(
        val bank: List<Preset> = defaultPresets(),
        val selectedBankIndex: Int = 0,
        val pendingBankIndex: Int? = null,
        val playingBoxIndex: Int = 0,
        val selectedBoxIndex: Int = 0,
        /** Set when the user has tapped the non-active box during playback to
         *  queue it as the next swap target. Used for the green-border indicator
         *  during play. Null otherwise. Cleared when the swap actually fires. */
        val queuedBoxIndex: Int? = null,
        val pendingSwap: TempoConfig? = null,
        val swapArmed: Boolean = false,
        val swapArmedBankIndex: Int? = null,
        val isPlaying: Boolean = false,
        val locked: Boolean = false,
        val editingBox: Int? = null,
        val bar: Int = 0,
        val beat: Int = 0,
        val subIndex: Int = 0,
        val tick: Long = 0L,
    ) {
        val pair: List<Preset>
            get() = bank.drop(selectedBankIndex * 2).take(2)
        val pendingPair: List<Preset>?
            get() = pendingBankIndex?.let { bank.drop(it * 2).take(2) }
        val top: Preset get() = pair[0]
        val bottom: Preset get() = pair[1]
        val bankTransitioning: Boolean get() = pendingBankIndex != null
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            MetronomeEngine.beat.collect { bp ->
                var newBankToPersist: Int? = null
                _ui.update { s ->
                    if (s.swapArmed && bp.bar == 0 && bp.beat == 0) {
                        val bankIdx = s.swapArmedBankIndex
                        newBankToPersist = bankIdx
                        val finalSelected = bankIdx ?: s.selectedBankIndex
                        val finalPlayingIdx = if (bankIdx != null) 0 else (1 - s.playingBoxIndex)
                        s.copy(
                            bar = bp.bar,
                            beat = bp.beat,
                            subIndex = bp.subIndex,
                            tick = bp.tick,
                            selectedBankIndex = finalSelected,
                            pendingBankIndex = null,
                            playingBoxIndex = finalPlayingIdx,
                            swapArmed = false,
                            swapArmedBankIndex = null,
                        )
                    } else {
                        s.copy(bar = bp.bar, beat = bp.beat, subIndex = bp.subIndex, tick = bp.tick)
                    }
                }
                newBankToPersist?.let { settingsRepo.setSelectedBankIndex(it) }
            }
        }
        viewModelScope.launch {
            MetronomeEngine.isPlaying.collect { playing ->
                _ui.update { s ->
                    s.copy(
                        isPlaying = playing,
                        pendingSwap = if (!playing) null else s.pendingSwap,
                        pendingBankIndex = if (!playing) null else s.pendingBankIndex,
                        playingBoxIndex = if (!playing) 0 else s.playingBoxIndex,
                        swapArmed = if (!playing) false else s.swapArmed,
                        swapArmedBankIndex = if (!playing) null else s.swapArmedBankIndex,
                    )
                }
            }
        }
        viewModelScope.launch {
            MetronomeEngine.swap.collect { _ ->
                // Arm — do NOT apply visual changes yet. The matching (0,0) beat
                // event in the beat collector commits the swap atomically. Also
                // clears queuedBoxIndex so the green border drops when swap fires.
                _ui.update { s ->
                    s.copy(
                        swapArmed = true,
                        swapArmedBankIndex = s.pendingBankIndex,
                        pendingSwap = null,
                        queuedBoxIndex = null,
                    )
                }
            }
        }
        viewModelScope.launch {
            presetRepo.presets.collect { bank ->
                _ui.update { it.copy(bank = bank) }
                // First-launch race fix: if the user hit Play before the DataStore
                // emitted the real bank (engine got defaults), re-sync the engine
                // to the actually-loaded preset so the grid shape and click pattern
                // match the persisted setlist.
                val s = _ui.value
                if (s.isPlaying) {
                    val playing = if (s.playingBoxIndex == 0) s.top else s.bottom
                    MetronomeEngine.setBpm(playing.bpm)
                    MetronomeEngine.setBeatsPerBar(playing.beatsPerBar)
                    MetronomeEngine.setBarsPerPhrase(playing.barsPerPhrase)
                    MetronomeEngine.setSubdivision(playing.subdivision)
                }
            }
        }
        viewModelScope.launch {
            settingsRepo.visualOffsetMs.collect { ms ->
                MetronomeEngine.setVisualOffsetMs(ms)
            }
        }
        viewModelScope.launch {
            val idx = settingsRepo.selectedBankIndex.first()
            _ui.update { it.copy(selectedBankIndex = idx) }
        }
    }

    // --- Lock ---

    fun setLocked(locked: Boolean) {
        _ui.update {
            it.copy(locked = locked, editingBox = if (locked) null else it.editingBox)
        }
    }

    // --- Edit mode ---

    fun toggleEditing(boxIndex: Int) {
        if (_ui.value.locked) return
        _ui.update { s ->
            s.copy(editingBox = if (s.editingBox == boxIndex) null else boxIndex)
        }
    }

    // --- BPM nudges ---

    fun adjustBoxBpm(boxIndex: Int, delta: Int) {
        if (_ui.value.locked) return
        mutateBoxPreset(boxIndex) { p ->
            p.copy(bpm = (p.bpm + delta).coerceIn(MetronomeEngine.BPM_MIN, MetronomeEngine.BPM_MAX))
        }
        val s = _ui.value
        val p = if (boxIndex == 0) s.top else s.bottom
        if (s.isPlaying && boxIndex == s.playingBoxIndex) {
            MetronomeEngine.setBpm(p.bpm)
        } else if (s.isPlaying && s.pendingSwap != null && boxIndex != s.playingBoxIndex) {
            MetronomeEngine.queuePreset(
                p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision, p.resolvedEndInBar(),
            )
            _ui.update { it.copy(pendingSwap = p.toConfig()) }
        } else if (!s.isPlaying && boxIndex == 0) {
            MetronomeEngine.setBpm(p.bpm)
        }
    }

    // --- Phrase shape edits ---

    fun adjustBoxBeatsPerBar(boxIndex: Int, delta: Int) {
        if (_ui.value.locked) return
        mutateBoxPreset(boxIndex) { p ->
            p.copy(beatsPerBar = (p.beatsPerBar + delta).coerceIn(MetronomeEngine.BEATS_MIN, MetronomeEngine.BEATS_MAX))
        }
        applyPhraseShape(boxIndex)
    }

    fun adjustBoxBarsPerPhrase(boxIndex: Int, delta: Int) {
        if (_ui.value.locked) return
        mutateBoxPreset(boxIndex) { p ->
            val newBars = (p.barsPerPhrase + delta).coerceIn(MetronomeEngine.BARS_MIN, MetronomeEngine.BARS_MAX)
            val clampedEnd = if (p.endInBar > newBars) 0 else p.endInBar
            p.copy(barsPerPhrase = newBars, endInBar = clampedEnd)
        }
        applyPhraseShape(boxIndex)
    }

    fun adjustBoxEndInBar(boxIndex: Int, delta: Int) {
        if (_ui.value.locked) return
        mutateBoxPreset(boxIndex) { p ->
            val newEnd = (p.endInBar + delta).coerceIn(0, p.barsPerPhrase)
            p.copy(endInBar = newEnd)
        }
    }

    fun setBoxSubdivision(boxIndex: Int, subdivision: Int) {
        if (_ui.value.locked) return
        val v = if (subdivision == 2) 2 else 1
        mutateBoxPreset(boxIndex) { it.copy(subdivision = v) }
        // v0.8: subdivision changes are ONLY applied via queuePreset (= takes
        // effect at the next swap), never pre-set out-of-band while playing.
        // Pre-setting caused "subdivision sounds like 16ths/32nds" — see
        // BUGS_AND_ISSUES.md § "Subdivision change ends up at unexpected rate".
        applyPhraseShape(boxIndex)
    }

    private fun applyPhraseShape(boxIndex: Int) {
        val s = _ui.value
        val p = if (boxIndex == 0) s.top else s.bottom
        if (!s.isPlaying) {
            // Stopped — push live so the next Play uses the right shape.
            if (boxIndex == 0) {
                MetronomeEngine.setBeatsPerBar(p.beatsPerBar)
                MetronomeEngine.setBarsPerPhrase(p.barsPerPhrase)
                MetronomeEngine.setSubdivision(p.subdivision)
                MetronomeEngine.setBpm(p.bpm)
            }
        } else if (boxIndex == s.playingBoxIndex) {
            // Currently playing box was edited — queue the new shape; the swap
            // applies subdivision+shape atomically.
            MetronomeEngine.queuePreset(
                p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision, p.resolvedEndInBar(),
            )
            _ui.update { it.copy(pendingSwap = p.toConfig()) }
        } else if (s.pendingSwap != null) {
            // Other box was edited and already queued — re-queue.
            MetronomeEngine.queuePreset(
                p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision, p.resolvedEndInBar(),
            )
            _ui.update { it.copy(pendingSwap = p.toConfig()) }
        }
    }

    private fun mutateBoxPreset(boxIndex: Int, transform: (Preset) -> Preset) {
        _ui.update { s ->
            val absoluteSlotIndex = s.selectedBankIndex * 2 + boxIndex
            val newBank = s.bank.toMutableList()
            newBank[absoluteSlotIndex] = transform(newBank[absoluteSlotIndex])
            s.copy(bank = newBank)
        }
        viewModelScope.launch { presetRepo.save(_ui.value.bank) }
    }

    // --- Playback ---

    fun togglePlay() {
        val app = getApplication<Application>()
        if (_ui.value.isPlaying) {
            app.stopMetronomePlayback()
            _ui.update {
                it.copy(
                    pendingSwap = null,
                    playingBoxIndex = 0,
                    pendingBankIndex = null,
                    swapArmed = false,
                    swapArmedBankIndex = null,
                    queuedBoxIndex = null,
                )
            }
            return
        }
        // v0.7: suspend on DataStore so we never start the engine with the
        // initial-defaults bank when the user has saved different presets. If
        // the flow already has a cached value, `.first()` is essentially instant.
        viewModelScope.launch {
            // v0.8 cold-boot fix: read the persisted bank synchronously AND
            // resolve the box to play from the FRESH bank, not from a possibly
            // half-updated _ui.value. All four engine fields are pushed before
            // start() — the previous fix only guaranteed BPM was fresh.
            val freshBank = presetRepo.presets.first()
            val capturedSelected = _ui.value.selectedBoxIndex
            val capturedBankIdx = _ui.value.selectedBankIndex
            val pair = freshBank.drop(capturedBankIdx * 2).take(2)
            val playing = if (capturedSelected == 0) pair[0] else pair[1]
            MetronomeEngine.setBpm(playing.bpm)
            MetronomeEngine.setBeatsPerBar(playing.beatsPerBar)
            MetronomeEngine.setBarsPerPhrase(playing.barsPerPhrase)
            MetronomeEngine.setSubdivision(playing.subdivision)
            _ui.update {
                it.copy(
                    bank = freshBank,
                    playingBoxIndex = capturedSelected,
                    pendingSwap = null,
                    pendingBankIndex = null,
                    swapArmed = false,
                    swapArmedBankIndex = null,
                    queuedBoxIndex = null,
                )
            }
            app.startMetronomePlayback()
        }
    }

    /** Double-tap on the queued box: force the swap to fire at the end of the
     *  CURRENT bar (overrides the preset's `endInBar`). Useful for the user to
     *  speed up a transition without changing the preset. */
    fun onBoxDoubleTap(boxIndex: Int) {
        val s = _ui.value
        if (!s.isPlaying) return
        if (s.pendingBankIndex != null) return
        if (boxIndex == s.playingBoxIndex) return
        val p = if (boxIndex == 0) s.top else s.bottom
        // endInBar = 0 / engine endBar = -1 (any bar) → swap at end of current bar.
        MetronomeEngine.queuePreset(p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision, endInBar = 0)
        _ui.update { it.copy(pendingSwap = p.toConfig(), queuedBoxIndex = boxIndex) }
    }

    fun onBoxTap(boxIndex: Int) {
        val s = _ui.value
        if (!s.isPlaying) {
            if (s.locked) return
            _ui.update { it.copy(selectedBoxIndex = boxIndex) }
            return
        }
        if (boxIndex == s.playingBoxIndex && s.pendingBankIndex != null) {
            // Cancel pending bank transition.
            _ui.update {
                it.copy(
                    pendingBankIndex = null,
                    pendingSwap = null,
                    swapArmed = false,
                    swapArmedBankIndex = null,
                    queuedBoxIndex = null,
                )
            }
            return
        }
        if (s.pendingBankIndex != null) return
        if (boxIndex == s.playingBoxIndex) return
        val p = if (boxIndex == 0) s.top else s.bottom
        val current = if (s.playingBoxIndex == 0) s.top else s.bottom
        // Single tap: respect the CURRENT preset's endInBar (defaulting to end of phrase).
        MetronomeEngine.queuePreset(
            p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision, current.resolvedEndInBar(),
        )
        _ui.update { it.copy(pendingSwap = p.toConfig(), queuedBoxIndex = boxIndex) }
    }

    // --- Bank selection ---

    fun selectBank(bankIndex: Int) {
        val idx = bankIndex.coerceIn(0, 3)
        val s = _ui.value
        if (idx == s.selectedBankIndex && s.pendingBankIndex == null) return
        if (!s.isPlaying) {
            // v0.9.1: UI flips IMMEDIATELY so green underline + box contents
            // recompose without blocking on JNI setters. Engine setters and
            // DataStore persistence run async on viewModelScope.
            _ui.update {
                it.copy(
                    selectedBankIndex = idx,
                    pendingBankIndex = null,
                    editingBox = null,
                    playingBoxIndex = 0,
                    selectedBoxIndex = 0,
                    swapArmed = false,
                    swapArmedBankIndex = null,
                )
            }
            viewModelScope.launch {
                settingsRepo.setSelectedBankIndex(idx)
                val newTop = _ui.value.top
                MetronomeEngine.setBpm(newTop.bpm)
                MetronomeEngine.setBeatsPerBar(newTop.beatsPerBar)
                MetronomeEngine.setBarsPerPhrase(newTop.barsPerPhrase)
                MetronomeEngine.setSubdivision(newTop.subdivision)
            }
            return
        }
        val newBankFirst = s.bank.drop(idx * 2).take(2)[0]
        val current = if (s.playingBoxIndex == 0) s.top else s.bottom
        _ui.update { it.copy(pendingBankIndex = idx, pendingSwap = newBankFirst.toConfig(), editingBox = null) }
        MetronomeEngine.queuePreset(
            newBankFirst.bpm, newBankFirst.beatsPerBar, newBankFirst.barsPerPhrase, newBankFirst.subdivision,
            current.resolvedEndInBar(),
        )
    }
}
