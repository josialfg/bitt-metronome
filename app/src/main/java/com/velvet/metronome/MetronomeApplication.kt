package com.velvet.metronome

import android.app.Application
import com.velvet.metronome.audio.ClickWavGenerator

class MetronomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Generate placeholder click.wav on first launch (per brief).
        ClickWavGenerator.ensureClickWav(this)
    }
}
