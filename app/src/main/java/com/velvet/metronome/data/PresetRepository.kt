package com.velvet.metronome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.velvet.metronome.model.Preset
import com.velvet.metronome.model.defaultPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

/**
 * Persists 8 presets.
 *
 * v2 format:  "v2|slot:bpm:beats:bars:sub;slot:bpm:beats:bars:sub;..."
 * v1 format:  "slot:bpm:ts;slot:bpm:ts;..."  (still decoded for migration; ts becomes beatsPerBar)
 *
 * On parse failure or count mismatch, falls back to defaults.
 */
class PresetRepository(private val context: Context) {

    private val key = stringPreferencesKey("presets_ordered")

    val presets: Flow<List<Preset>> = context.presetDataStore.data.map { prefs ->
        prefs[key]?.let(::decode) ?: defaultPresets()
    }

    suspend fun save(list: List<Preset>) {
        require(list.size == 8) { "Must save exactly 8 presets, got ${list.size}" }
        context.presetDataStore.edit { it[key] = encode(list) }
    }

    private fun encode(list: List<Preset>): String =
        "v2|" + list.joinToString(";") {
            "${it.slot}:${it.bpm}:${it.beatsPerBar}:${it.barsPerPhrase}:${it.subdivision}:${it.endInBar}"
        }

    private fun decode(raw: String): List<Preset>? = runCatching {
        if (raw.startsWith("v2|")) {
            raw.removePrefix("v2|").split(";").map { entry ->
                val p = entry.split(":").map(String::toInt)
                Preset(
                    slot = p[0], bpm = p[1], beatsPerBar = p[2],
                    barsPerPhrase = p[3], subdivision = p[4],
                    endInBar = p.getOrElse(5) { 0 },
                )
            }
        } else {
            raw.split(";").map { entry ->
                val p = entry.split(":").map(String::toInt)
                Preset(slot = p[0], bpm = p[1], beatsPerBar = p[2], barsPerPhrase = 4, subdivision = 1)
            }
        }
    }.getOrNull()?.takeIf { it.size == 8 }
}
