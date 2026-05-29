package com.velvet.metronome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.velvet.metronome.data.SettingsRepository
import com.velvet.metronome.ui.navigation.MetronomeNavGraph
import com.velvet.metronome.ui.theme.MetronomeTheme
import com.velvet.metronome.ui.theme.ThemeChoice

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Result intentionally ignored — playback works without it.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Flip from the launch-time splash theme back to the runtime theme. This
        // must happen BEFORE super.onCreate so the system uses the runtime theme
        // for window setup; the splash drawable already served its purpose by
        // covering the window background until first frame.
        setTheme(R.style.Theme_Metronome)
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val repo = remember { SettingsRepository(this) }
            val theme by repo.themeChoice.collectAsState(initial = ThemeChoice.BEIGE)
            // v0.8.1: lock font scale to 1.0 so the system's Display-size /
            // accessibility text-size setting doesn't bloat sp-based layouts.
            // Layout was breaking on devices with non-default font scale (see
            // plan.md § "v0.8.1 — responsive scaling fix").
            val baseDensity = LocalDensity.current
            val fixedDensity = Density(baseDensity.density, fontScale = 1f)
            CompositionLocalProvider(LocalDensity provides fixedDensity) {
                MetronomeTheme(themeChoice = theme) {
                    MetronomeNavGraph()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(perm)
        }
    }
}
