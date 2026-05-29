package com.velvet.metronome.model

/**
 * A saved tempo configuration.
 *
 * `endInBar` semantics (v0.8):
 *   - `0` (default) = "end of phrase" — engine waits until bar = `barsPerPhrase`.
 *     The value resolves dynamically so a phrase-length edit shifts the swap point.
 *   - `1..barsPerPhrase` = swap at the end of that specific bar.
 *
 * Old data with `endInBar = 4` on a 4-bar phrase functionally matches end-of-phrase;
 * if the user later changes `barsPerPhrase` to 8 with `endInBar = 4` they get the
 * specific-bar behaviour (which is what they explicitly chose, so it's correct).
 */
data class Preset(
    val slot: Int,
    val bpm: Int,
    val beatsPerBar: Int = 4,
    val barsPerPhrase: Int = 4,
    val subdivision: Int = 1,
    val endInBar: Int = 0,
)

fun defaultPresets(): List<Preset> = (1..8).map { slot ->
    Preset(slot = slot, bpm = 120, beatsPerBar = 4, barsPerPhrase = 4, subdivision = 1, endInBar = 0)
}

/** Resolved end-bar for the engine: `endInBar = 0` → `barsPerPhrase`. */
fun Preset.resolvedEndInBar(): Int = if (endInBar == 0) barsPerPhrase else endInBar
