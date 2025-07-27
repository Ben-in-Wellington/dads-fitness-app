// feature_audio/src/main/java/com/di/feature_audio/RadioRepository.kt

package com.di.feature_audio

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val player: ExoPlayer
) {
    private var bound = false

    /* -------- expose player state -------- */
    private val _isPlaying = MutableStateFlow(false)
    val  isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying          // <- push change
                }
            }
        )
    }

    /* -------- commands -------- */
    fun play(station: RadioStation) {
        maybeBind()
        if (player.isPlaying) player.stop()

        val item = MediaItem.Builder()
            .setUri(station.stream)
            .setMediaId(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(station.name).build()
            )
            .build()

        player.setMediaItem(item)
        player.prepare()
        player.play()                                     // callback will fire
    }

    fun stop() {
        if (player.isPlaying) player.stop()               // callback will fire
    }

    /* -------- util -------- */
    private fun maybeBind() {
        if (bound) return
        val i = Intent(ctx, RadioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(i) else ctx.startService(i)
        bound = true
    }
}