package com.di.feature_audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.di.feature_audio.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RadioService : MediaSessionService() {

    @Inject lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        session = MediaSession.Builder(this, player).build()

        // DO NOT call setMediaSession(session) in Media3 <= 1.3.1
        // Apple M3: It is only available in 1.4.0+

        // Show initial notification immediately for foreground service requirements
        val notif = buildInitialNotification()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Radio Playing")
            .setContentText(session.player.currentMediaItem?.mediaMetadata?.title ?: "")
            .setSmallIcon(R.drawable.ic_radio)
            .setOngoing(true)
            .build()

        if (startInForegroundRequired) {
            startForeground(NOTIF_ID, notification)
        } else {
            // Just update existing notification if already in foreground
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, notification)
        }
    }

    private fun buildInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("Radio service ready")
            .setContentText("Waiting for playback...")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Radio Playback", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "radio_playback"
        const val NOTIF_ID = 10
    }
}