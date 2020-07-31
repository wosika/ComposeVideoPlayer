package com.halilibo.composevideoplayer

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.ui.geometry.Size
import androidx.ui.graphics.Color
import com.halilibo.composevideoplayer.util.FlowDebouncer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoFrameMetadataListener
import com.google.android.exoplayer2.video.VideoListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

class VideoPlayerController(
    private val context: Context,
    initialSource: VideoPlayerSource,
    override val coroutineContext: CoroutineContext = Dispatchers.Main
) : MediaPlaybackControls, CoroutineScope, VideoPlayerState {

    private var source: VideoPlayerSource = initialSource

    internal var playerViewBackgroundColor: Color? = null
        set(value) {
            field = value
            if(value != null) {
                playerView?.setBackgroundColor(value.value.toInt())
            }
        }

    private var playerView: PlayerView? = null
        set(value) {
            field = value
            if(value != null) {
                playerViewBackgroundColor?.value?.toInt()?.let { value.setBackgroundColor(it) }
            }
        }

    private var mediaPositionTrackerJob: Job? = null

    override fun play() {
        if(exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
        }
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun playPauseToggle() {
        if(exoPlayer.isPlaying) pause()
        else play()
    }

    override fun quickSeekForward() {
        if(quickSeekDirection.value.direction != QuickSeekDirection.None) {
            // Currently animating
            return
        }
        val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(exoPlayer.duration)
        exoPlayer.seekTo(target)
        updateDurationAndPosition()
        quickSeekDirection.value = QuickSeekAction.forward()
    }

    override fun quickSeekRewind() {
        if(quickSeekDirection.value.direction != QuickSeekDirection.None) {
            // Currently animating
            return
        }
        val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
        exoPlayer.seekTo(target)
        updateDurationAndPosition()
        quickSeekDirection.value = QuickSeekAction.rewind()
    }

    override fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        updateDurationAndPosition()
    }

    internal fun setSource(source: VideoPlayerSource) {
        this.source = source
        prepare()
    }

    internal fun enableGestures(isEnabled: Boolean) {
        gesturesEnabled.value = isEnabled
    }

    private var initializedWithSource = true

    private val exoPlayer = SimpleExoPlayer.Builder(context)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.EventListener {

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        // this@VideoPlayerController.isPlaying.value = isPlaying
                    }

                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        super.onPlayerStateChanged(playWhenReady, playbackState)
                        this@VideoPlayerController.isPlaying.value = playWhenReady
                        this@VideoPlayerController.playbackState.value = PlaybackState.of(playbackState)
                    }
                })

                addVideoListener(object: VideoListener {
                    override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
                        super.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio)

                        this@VideoPlayerController.videoSize.value =
                                Size(width.toFloat(), height.toFloat())
                    }
                })
            }

    private val previewExoPlayer = SimpleExoPlayer.Builder(context)
        .build()
        .apply {
            playWhenReady = false
        }

    private val previewSeekDebouncer = FlowDebouncer<Long>(200L)
    init {
        launch {
            previewSeekDebouncer.collect { position ->
                Log.d("VideoPlayer", "preview seeking to $position")
                previewExoPlayer.seekTo(position)
            }
        }
    }


    fun enableControls(enabled: Boolean) { controlsEnabled.value = enabled }

    fun showControls() {
        controlsVisible.value = true
    }

    fun hideControls() { controlsVisible.value = false }

    private fun updateDurationAndPosition() {
        duration.value = exoPlayer.duration.coerceAtLeast(0)
        currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0)
        secondaryProgress.value = exoPlayer.bufferedPosition.coerceAtLeast(0)
    }

    private fun prepare() {
        fun createVideoSource(): MediaSource {
            val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context,
                Util.getUserAgent(context, context.packageName))

            return when (val source = source) {
                is VideoPlayerSource.Raw -> {
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(RawResourceDataSource.buildRawResourceUri(source.resId))
                }
                is VideoPlayerSource.Network -> {
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(Uri.parse(source.url))
                }
            }
        }

        mediaPositionTrackerJob?.cancel()

        mediaPositionTrackerJob = launch {
            repeat(1000000) {

                updateDurationAndPosition()

                delay(250)
            }
        }

        exoPlayer.prepare(createVideoSource())
        previewExoPlayer.prepare(createVideoSource())
    }

    internal fun onDispose() {
        mediaPositionTrackerJob?.cancel()
        exoPlayer.release()
    }

    internal fun playerViewAvailable(playerView: PlayerView) {
        this.playerView = playerView
        playerView.player = exoPlayer
        playerView.setBackgroundColor(context.getColor(android.R.color.black))
        if (initializedWithSource) {
            initializedWithSource = false
            prepare()
        }
    }

    internal fun previewPlayerViewAvailable(playerView: PlayerView) {
        Log.d("VideoPlayer", "preview player available")
        playerView.player = previewExoPlayer
    }

    internal fun previewSeekTo(position: Long) {
        // position is very accurate. Thumbnail doesn't have to be.
        // Roll to the nearest "even" integer.
        val seconds = position.toInt() / 1000
        val nearestEven = (seconds - seconds.rem(2)).toLong()
        previewSeekDebouncer.put(nearestEven * 1000)
    }

    override val isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val controlsVisible: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val controlsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val gesturesEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val duration: MutableStateFlow<Long> = MutableStateFlow(1L)
    override val currentPosition: MutableStateFlow<Long> = MutableStateFlow(1L)
    override val secondaryProgress: MutableStateFlow<Long> = MutableStateFlow(1L)

    override val videoSize: MutableStateFlow<Size> = MutableStateFlow(Size(1920f,1080f))
    override val draggingProgress: MutableStateFlow<DraggingProgress?> = MutableStateFlow(null)
    override val playbackState: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.IDLE)
    override val quickSeekDirection: MutableStateFlow<QuickSeekAction> = MutableStateFlow(QuickSeekAction.none())
}