// AudioManager.kt - CORRECTED VERSION
@file:Suppress("MemberVisibilityCanBePrivate")

package com.di.feature_trainer.audio

import android.media.*
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AudioManager @Inject constructor() {

    /* ───────────────── constants ───────────────── */
    private companion object {
        const val TAG = "AudioManager"
        const val INPUT_RATE = 16_000
        private const val IN_CH = AudioFormat.CHANNEL_IN_MONO
        private const val IN_FMT = AudioFormat.ENCODING_PCM_16BIT
        const val OUTPUT_RATE = 24_000
        private const val OUT_CH = AudioFormat.CHANNEL_OUT_MONO
        private const val OUT_FMT = AudioFormat.ENCODING_PCM_16BIT

        // 1. ADD an End-Of-Stream (EOS) marker
        private val EOS_MARKER = ByteArray(0)
    }

    /* ───────────────── public flows ───────────────── */
    private val _audioOut = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioInputStream: SharedFlow<String> = _audioOut.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 1, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioError: SharedFlow<String> = _errors.asSharedFlow()

    // 2. ADD a state flow to report playback status
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /* ───────────────── state ───────────────── */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: AudioRecord? = null
    private var speaker: AudioTrack? = null
    private var recJob: Job? = null
    private var playJob: Job? = null
    private val playQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED) // Use unlimited to avoid blocking on send

    @Volatile private var recording = false
    @Volatile private var micMuted = false

    fun muteMic() { micMuted = true }
    fun unMuteMic() { micMuted = false }

    /* ───────────────── microphone ───────────────── */
    fun startRecording() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(INPUT_RATE, IN_CH, IN_FMT)
        if (minBuf <= 0) { err("Mic not supported"); return }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, INPUT_RATE, IN_CH, IN_FMT, minBuf * 3
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    err("AudioRecord init failed"); release(); return
                }
                startRecording()
            }
        } catch (e: SecurityException) {
            err("Mic permission denied: ${e.message}"); return
        }

        recJob = scope.launch {
            val buf = ByteArray(minBuf)
            recording = true
            Log.d(TAG, "🎙️  Recording started")
            try {
                while (isActive && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val n = recorder?.read(buf, 0, buf.size) ?: break
                    if (n > 0 && !micMuted) {
                        _audioOut.emit(Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
                    } else if (n < 0) {
                        err("Mic read error $n"); break
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) err("Mic error ${t.message}")
            } finally {
                stopRecordingInternal()
            }
        }
    }

    private fun stopRecordingInternal() {
        if (!recording) return
        recording = false
        recJob?.cancel()
        recorder?.runCatching {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            release()
        }
        recorder = null
        Log.d(TAG, "🎙️  Recording stopped")
    }

    /* ───────────────── speaker ───────────────── */
    fun initializePlayback() {
        if (speaker?.state == AudioTrack.STATE_INITIALIZED) return
        val minBuf = AudioTrack.getMinBufferSize(OUTPUT_RATE, OUT_CH, OUT_FMT)
        if (minBuf <= 0) { err("Speaker not supported"); return }
        val bufBytes = max(minBuf * 4, OUTPUT_RATE / 2)

        speaker = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            ).setAudioFormat(
                AudioFormat.Builder().setEncoding(OUT_FMT).setSampleRate(OUTPUT_RATE)
                    .setChannelMask(OUT_CH).build()
            ).setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply {
                if (state != AudioTrack.STATE_INITIALIZED) {
                    err("AudioTrack init failed"); release(); speaker = null; return
                }
            }

        playJob?.cancel()
        playJob = scope.launch {
            try {
                for (chunk in playQueue) {
                    val currentSpeaker = speaker ?: continue
                    // 3. CHECK for the EOS marker
                    if (chunk === EOS_MARKER) {
                        currentSpeaker.pause() // Don't stop, just pause
                        if (currentSpeaker.playState != AudioTrack.PLAYSTATE_PAUSED) {
                            currentSpeaker.flush()
                        }
                        _isSpeaking.value = false
                        Log.d(TAG, "🔊 Playback paused by EOS marker.")
                        continue
                    }

                    if (currentSpeaker.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        currentSpeaker.play()
                        _isSpeaking.value = true
                    }

                    var off = 0
                    while (off < chunk.size) {
                        val w = currentSpeaker.write(chunk, off, chunk.size - off, AudioTrack.WRITE_BLOCKING)
                        if (w < 0) { err("Write error $w"); break }
                        off += w
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) err("Speaker error ${t.message}")
            }
        }
        Log.d(TAG, "🔊 Speaker initialised")
    }

    fun playAudio(base64: String) {
        val pcm = try { Base64.decode(base64, Base64.NO_WRAP) }
        catch (e: IllegalArgumentException) { err("Bad Base64"); return }
        playQueue.trySend(pcm)
    }

    // 4. ADD this new method to signal the end of a turn
    fun signalEndOfTurn() {
        playQueue.trySend(EOS_MARKER)
    }

    fun stopPlaybackAndClear() {
        // Drain the queue to prevent old audio from playing
        while (playQueue.tryReceive().isSuccess) { /* drain */ }
        speaker?.runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                pause()
                flush()
            }
        }
        _isSpeaking.value = false
        Log.d(TAG, "🔇 Playback stopped and flushed.")
    }

    // 5. MODIFY `release` to be the only place that destroys resources
    fun release() {
        scope.cancel() // Cancel all coroutines in this scope
        stopRecordingInternal()

        // Now it's safe to release the speaker
        playJob?.cancel()
        speaker?.runCatching {
            if (state == AudioTrack.STATE_INITIALIZED) {
                stop()
                release()
            }
        }
        speaker = null
        playQueue.close()
        Log.d(TAG, "AudioManager released all resources.")
    }

    private fun err(msg: String) {
        Log.e(TAG, msg)
        _errors.tryEmit(msg)
    }
}