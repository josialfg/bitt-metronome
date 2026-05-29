package com.velvet.metronome.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.velvet.metronome.MainActivity
import com.velvet.metronome.R
import com.velvet.metronome.audio.MetronomeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Foreground service that owns playback lifecycle.
 *
 *   ACTION_START → startForeground(notification) + engine.start()
 *   ACTION_STOP  → engine.stop() + stopForeground + stopSelf
 *
 * The service observes [MetronomeEngine.isPlaying] so it self-stops if the
 * audio thread bails out (stream error, OS audio reroute, etc.).
 *
 * Notification text reflects [MetronomeEngine.currentBpm] and
 * [MetronomeEngine.currentTimeSignature] and updates on every preset swap.
 */
class MetronomeService : Service() {

    companion object {
        const val ACTION_START = "com.velvet.metronome.action.START"
        const val ACTION_STOP  = "com.velvet.metronome.action.STOP"
        const val CHANNEL_ID   = "metronome_playback"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context) =
            Intent(context, MetronomeService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context) =
            Intent(context, MetronomeService::class.java).setAction(ACTION_STOP)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Refresh notification when BPM / beats / bars change (delta button, queue swap, preset edit while playing).
        scope.launch {
            combine(
                MetronomeEngine.currentBpm,
                MetronomeEngine.currentBeatsPerBar,
            ) { bpm, beats -> bpm to beats }
                .collect { (bpm, beats) ->
                    val nm = NotificationManagerCompat.from(this@MetronomeService)
                    if (nm.areNotificationsEnabled()) {
                        try {
                            nm.notify(NOTIFICATION_ID, buildNotification(bpm, beats))
                        } catch (_: SecurityException) { /* permission revoked mid-flight */ }
                    }
                }
        }

        // Self-stop when engine reports !playing (after we've seen it true at least once).
        scope.launch {
            MetronomeEngine.isPlaying
                .drop(1)
                .collect { playing ->
                    if (!playing) {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification(
                    MetronomeEngine.currentBpm.value,
                    MetronomeEngine.currentBeatsPerBar.value,
                )
                startForeground(NOTIFICATION_ID, notification)
                if (!MetronomeEngine.start()) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                MetronomeEngine.stop()
                // The isPlaying collector will fire stopSelf, but be explicit.
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            description = "Persistent notification while the metronome is running"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(bpm: Int, beatsPerBar: Int): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_metronome_notification)
            .setContentTitle("$bpm BPM")
            .setContentText("$beatsPerBar/4")
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}

/** Convenience for callers that don't want to deal with API-level differences. */
fun Context.startMetronomePlayback() {
    ContextCompat.startForegroundService(this, MetronomeService.startIntent(this))
}

fun Context.stopMetronomePlayback() {
    startService(MetronomeService.stopIntent(this))
}
