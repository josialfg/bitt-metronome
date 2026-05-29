package com.velvet.metronome.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kotlin bridge to the Oboe-backed C++ engine (v2).
 *
 * v2 changes:
 *  - BPM range extended to 40..300
 *  - Beats per bar 2..6 (replaces fixed 3 or 4)
 *  - Bars per phrase 1..8 (replaces hardcoded 4-bar visual cycle)
 *  - Subdivision 1 or 2 (eighth-note tick between beats)
 *  - Visual offset (0..250 ms) — delays the onBeat callback relative to the
 *    audible click so the user can calibrate audio↔visual sync.
 */
object MetronomeEngine {

    init { System.loadLibrary("metronome") }

    const val BPM_MIN = 40
    const val BPM_MAX = 300
    const val BEATS_MIN = 2
    const val BEATS_MAX = 6
    const val BARS_MIN = 1
    const val BARS_MAX = 8
    const val VISUAL_OFFSET_MAX_MS = 250

    data class BeatPosition(
        val bar: Int,
        val beat: Int,
        val subIndex: Int = 0,
        /** Monotonically increasing tick counter — lets observers distinguish
         *  successive ticks at the same (bar, beat) when subdivisions repeat. */
        val tick: Long = 0L,
    )

    data class PresetSwap(
        val bpm: Int,
        val beatsPerBar: Int,
        val barsPerPhrase: Int,
        val subdivision: Int,
    )

    private val _beat = MutableStateFlow(BeatPosition(0, 0))
    val beat: StateFlow<BeatPosition> = _beat.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _swap = MutableSharedFlow<PresetSwap>(extraBufferCapacity = 8)
    val swap: SharedFlow<PresetSwap> = _swap.asSharedFlow()

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _currentBeatsPerBar = MutableStateFlow(4)
    val currentBeatsPerBar: StateFlow<Int> = _currentBeatsPerBar.asStateFlow()

    private val _currentBarsPerPhrase = MutableStateFlow(4)
    val currentBarsPerPhrase: StateFlow<Int> = _currentBarsPerPhrase.asStateFlow()

    private val _currentSubdivision = MutableStateFlow(1)
    val currentSubdivision: StateFlow<Int> = _currentSubdivision.asStateFlow()

    @Volatile private var tickCounter: Long = 0L

    fun start(): Boolean {
        tickCounter = 0
        // v0.9.2 cold-boot fix: pass the Kotlin-side preset snapshot directly INTO
        // nativeStart. The v0.9.1 belt-and-braces (calling nativeSetBpm/... right
        // before nativeStart) silently failed on cold boot: the native sEngine
        // doesn't exist until nativeStart creates it, so every pre-start setter was
        // a no-op and the engine started with C++ defaults (3/4 → 4/4). Native
        // start() now applies these before requestStart(), so the first audio
        // callback always counts the persisted meter. These StateFlow values are
        // authoritative — the Kotlin setters always update them even when the
        // native call no-ops.
        val ok = nativeStart(
            _currentBpm.value,
            _currentBeatsPerBar.value,
            _currentBarsPerPhrase.value,
            _currentSubdivision.value,
        )
        _isPlaying.value = ok
        return ok
    }

    fun stop() {
        nativeStop()
        _isPlaying.value = false
        _beat.value = BeatPosition(0, 0, 0, 0)
    }

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(BPM_MIN, BPM_MAX)
        _currentBpm.value = clamped
        nativeSetBpm(clamped)
    }

    fun setBeatsPerBar(beats: Int) {
        val clamped = beats.coerceIn(BEATS_MIN, BEATS_MAX)
        _currentBeatsPerBar.value = clamped
        nativeSetBeatsPerBar(clamped)
    }

    fun setBarsPerPhrase(bars: Int) {
        val clamped = bars.coerceIn(BARS_MIN, BARS_MAX)
        _currentBarsPerPhrase.value = clamped
        nativeSetBarsPerPhrase(clamped)
    }

    fun setSubdivision(sub: Int) {
        val v = if (sub == 2) 2 else 1
        _currentSubdivision.value = v
        nativeSetSubdivision(v)
    }

    fun setVisualOffsetMs(ms: Int) {
        nativeSetVisualOffsetMs(ms.coerceIn(0, VISUAL_OFFSET_MAX_MS))
    }

    fun queuePreset(bpm: Int, beatsPerBar: Int, barsPerPhrase: Int, subdivision: Int, endInBar: Int = 0) {
        val engineEndBar = if (endInBar > 0) endInBar - 1 else -1
        nativeQueuePreset(
            bpm.coerceIn(BPM_MIN, BPM_MAX),
            beatsPerBar.coerceIn(BEATS_MIN, BEATS_MAX),
            barsPerPhrase.coerceIn(BARS_MIN, BARS_MAX),
            if (subdivision == 2) 2 else 1,
            engineEndBar,
        )
    }

    fun version(): String = nativeGetVersion()

    // --- Native callbacks (audio thread) ---

    @Suppress("unused")
    @JvmStatic
    fun onBeat(bar: Int, beat: Int, subIndex: Int) {
        val t = ++tickCounter
        _beat.value = BeatPosition(bar, beat, subIndex, t)
    }

    @Suppress("unused")
    @JvmStatic
    fun onPresetSwapped(bpm: Int, beatsPerBar: Int, barsPerPhrase: Int, subdivision: Int) {
        _currentBpm.value = bpm
        _currentBeatsPerBar.value = beatsPerBar
        _currentBarsPerPhrase.value = barsPerPhrase
        _currentSubdivision.value = subdivision
        _swap.tryEmit(PresetSwap(bpm, beatsPerBar, barsPerPhrase, subdivision))
    }

    private external fun nativeStart(bpm: Int, beats: Int, bars: Int, sub: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetBpm(bpm: Int)
    private external fun nativeSetBeatsPerBar(beats: Int)
    private external fun nativeSetBarsPerPhrase(bars: Int)
    private external fun nativeSetSubdivision(sub: Int)
    private external fun nativeSetVisualOffsetMs(ms: Int)
    private external fun nativeQueuePreset(bpm: Int, beatsPerBar: Int, barsPerPhrase: Int, subdivision: Int, endBar: Int)
    private external fun nativeIsPlaying(): Boolean
    private external fun nativeGetVersion(): String
}
