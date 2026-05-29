package com.velvet.metronome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.velvet.metronome.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val keepInBackgroundKey = booleanPreferencesKey("keep_in_background")
    private val visualOffsetMsKey = intPreferencesKey("visual_offset_ms")
    private val selectedBankKey = intPreferencesKey("selected_bank_index")
    private val themeKey = stringPreferencesKey("theme_choice")
    private val lockVariantKey = intPreferencesKey("lock_variant")

    val keepInBackground: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[keepInBackgroundKey] ?: true
    }

    val visualOffsetMs: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[visualOffsetMsKey] ?: 0
    }

    val selectedBankIndex: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[selectedBankKey] ?: 0).coerceIn(0, 3)
    }

    val themeChoice: Flow<ThemeChoice> = context.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "DARK"  -> ThemeChoice.DARK
            "LIGHT" -> ThemeChoice.LIGHT
            else    -> ThemeChoice.BEIGE
        }
    }

    suspend fun setKeepInBackground(value: Boolean) {
        context.settingsDataStore.edit { it[keepInBackgroundKey] = value }
    }

    suspend fun setVisualOffsetMs(ms: Int) {
        context.settingsDataStore.edit { it[visualOffsetMsKey] = ms.coerceIn(0, 250) }
    }

    suspend fun setSelectedBankIndex(index: Int) {
        context.settingsDataStore.edit { it[selectedBankKey] = index.coerceIn(0, 3) }
    }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        context.settingsDataStore.edit { it[themeKey] = choice.name }
    }

    val lockVariant: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[lockVariantKey] ?: 1).coerceIn(1, 10)
    }

    suspend fun setLockVariant(variant: Int) {
        context.settingsDataStore.edit { it[lockVariantKey] = variant.coerceIn(1, 10) }
    }
}
