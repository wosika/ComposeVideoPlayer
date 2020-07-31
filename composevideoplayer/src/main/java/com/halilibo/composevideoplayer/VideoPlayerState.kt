package com.halilibo.composevideoplayer

import androidx.ui.geometry.Size
import com.google.android.exoplayer2.Player.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

@ExperimentalCoroutinesApi
interface VideoPlayerState {
    val isPlaying: StateFlow<Boolean>
    val controlsVisible: StateFlow<Boolean>
    val controlsEnabled: StateFlow<Boolean>
    val gesturesEnabled: StateFlow<Boolean>
    val duration: StateFlow<Long>
    val currentPosition: StateFlow<Long>
    val secondaryProgress: StateFlow<Long>

    val videoSize: StateFlow<Size>
    val draggingProgress: StateFlow<DraggingProgress?>
    val playbackState: StateFlow<PlaybackState>

    val quickSeekDirection: StateFlow<QuickSeekAction>
}

enum class PlaybackState(val value: Int) {

    IDLE(STATE_IDLE),
    BUFFERING(STATE_BUFFERING),
    READY(STATE_READY),
    ENDED(STATE_ENDED);

    companion object {
        fun of(value: Int): PlaybackState {
            return values().first { it.value == value }
        }
    }
}