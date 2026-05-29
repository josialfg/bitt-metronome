package com.velvet.metronome.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velvet.metronome.data.SettingsRepository
import com.velvet.metronome.service.stopMetronomePlayback
import com.velvet.metronome.ui.screens.MainScreen
import com.velvet.metronome.ui.screens.MainViewModel

/**
 * v2 has a single screen — no nav graph, just MainScreen.
 *
 * BackgroundBehaviorEffect carries over from v1: if the user toggles
 * "keep in background" off in the bottom-sheet settings, stop playback when
 * the app is sent to the background.
 */
@Composable
fun MetronomeNavGraph() {
    val activity = LocalContext.current as ComponentActivity
    val mainVm: MainViewModel = viewModel(viewModelStoreOwner = activity)
    val mainState by mainVm.ui.collectAsState()

    val settingsRepo = remember(activity) { SettingsRepository(activity) }
    val keepInBackground by settingsRepo.keepInBackground.collectAsState(initial = true)

    BackgroundBehaviorEffect(
        keepInBackground = keepInBackground,
        isPlaying = mainState.isPlaying,
    )

    MainScreen(viewModel = mainVm)
}

@Composable
private fun BackgroundBehaviorEffect(
    keepInBackground: Boolean,
    isPlaying: Boolean,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, keepInBackground, isPlaying) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !keepInBackground && isPlaying) {
                context.stopMetronomePlayback()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
