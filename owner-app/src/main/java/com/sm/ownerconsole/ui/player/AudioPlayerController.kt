// Package: com.sm.ownerconsole.ui.player
// ExoPlayer controller for streaming AAC/M4A chunks from Supabase signed URLs
package com.sm.ownerconsole.ui.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlayerController(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var currentlyPlayingUrl by mutableStateOf<String?>(null)
        private set

    init {
        initPlayer()
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            durationMs = this@apply.duration.coerceAtLeast(0L)
                        }
                        Player.STATE_ENDED -> {
                            isPlaying = false
                            currentPositionMs = 0L
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        startProgressTracker()
                    } else {
                        progressJob?.cancel()
                    }
                }
            })
        }
    }

    fun playUrl(url: String) {
        if (currentlyPlayingUrl == url && exoPlayer != null) {
            if (isPlaying) {
                exoPlayer?.pause()
            } else {
                exoPlayer?.play()
            }
            return
        }

        currentlyPlayingUrl = url
        exoPlayer?.apply {
            stop()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun release() {
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        currentlyPlayingUrl = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                exoPlayer?.let { player ->
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                delay(250L)
            }
        }
    }

    companion object {
        fun formatMs(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    }
}
