package com.mdmesh.core.action

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Plays a loud locate tone even when silent; auto-stops after a duration. Single active ring. */
@Singleton
class RingController @Inject constructor(@ApplicationContext private val context: Context) {

    // Every started ringtone, not just the latest: `Ringtone.stop()` on a remotely-played instance
    // (the common case once the app isn't foregrounded) is `IRingtonePlayer.stopAsync()` — fire and
    // forget. Two `start()` calls landing close together (e.g. two `device.ring` commands delivered
    // in the same check-in batch) can each spawn their own remote player, so `stop()` must silence
    // every instance this controller ever created, not only the most recently assigned one.
    private val activeRingtones = mutableListOf<Ringtone>()
    private var savedVolume: Int = -1
    private val main = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    @Synchronized
    fun start(durationMs: Long) {
        main.removeCallbacks(autoStop)
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Capture the pre-ring volume only on the idle->ringing transition. Re-entering start()
        // while already ringing must NOT overwrite it with the already-maxed volume — that would
        // make stop() "restore" the alarm stream to max forever instead of the real original level.
        if (activeRingtones.isEmpty()) {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        } else {
            stopRingtones()
        }
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val rt = RingtoneManager.getRingtone(context, uri).apply {
            @Suppress("DEPRECATION")
            streamType = AudioManager.STREAM_ALARM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }
        activeRingtones += rt
        main.postDelayed(autoStop, durationMs)
    }

    @Synchronized
    fun stop() {
        main.removeCallbacks(autoStop)
        stopRingtones()
        if (savedVolume >= 0) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            savedVolume = -1
        }
    }

    private fun stopRingtones() {
        activeRingtones.forEach { runCatching { it.stop() } }
        activeRingtones.clear()
    }
}
