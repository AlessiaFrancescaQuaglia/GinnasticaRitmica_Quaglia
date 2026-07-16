package com.example.ginnasticaritmica

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.core.net.toUri

//MEDIA MANAGER: Estrazione della logica hardware (Audio) dal Fragment visivo.
class MusicManager(
    private val context: Context,
    private val onPrepared: () -> Unit,
    private val onCompletion: () -> Unit,
    private val onError: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String) {
        stop() // Ferma eventuali riproduzioni precedenti in corso

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, url.toUri())

                setOnErrorListener { _, _, _ ->
                    onError()
                    stop()
                    true
                }

                setOnPreparedListener {
                    start()
                    onPrepared() // Avvisa il Fragment che la musica è partita
                }

                setOnCompletionListener {
                    onCompletion() // Avvisa il Fragment che la traccia è finita
                    stop()
                }

                prepareAsync()
            }
        } catch (_: Exception) {
            onError()
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }
}