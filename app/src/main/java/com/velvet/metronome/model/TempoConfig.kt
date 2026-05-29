package com.velvet.metronome.model

/**
 * A live tempo configuration — what the engine is set to (or about to be set to).
 *
 * [Preset] is the persisted form (TempoConfig + slot id). The engine, the
 * "currently playing" UI box and the "queued" UI box all use TempoConfig.
 */
data class TempoConfig(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val barsPerPhrase: Int = 4,
    val subdivision: Int = 1,
) {
    companion object {
        fun from(p: Preset) = TempoConfig(p.bpm, p.beatsPerBar, p.barsPerPhrase, p.subdivision)
    }
}

fun Preset.toConfig() = TempoConfig.from(this)
fun Preset.withConfig(c: TempoConfig) =
    copy(bpm = c.bpm, beatsPerBar = c.beatsPerBar, barsPerPhrase = c.barsPerPhrase, subdivision = c.subdivision)
