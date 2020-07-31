package com.halilibo.composevideoplayer

import androidx.compose.*
import androidx.ui.core.Alignment
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Modifier
import androidx.ui.foundation.ContentColorAmbient
import androidx.ui.foundation.drawBackground
import androidx.ui.graphics.Color
import androidx.ui.layout.Stack
import androidx.ui.layout.aspectRatio
import androidx.ui.layout.fillMaxWidth
import com.halilibo.composevideoplayer.MediaControlButtons
import kotlinx.coroutines.ExperimentalCoroutinesApi

val VideoPlayerControllerAmbient = ambientOf<VideoPlayerController> { error("VideoPlayerController is not initialized") }

@ExperimentalCoroutinesApi
@Composable
fun VideoPlayer(
        source: VideoPlayerSource,
        backgroundColor: Color = Color.Black,
        controlsEnabled: Boolean = true,
        controlsVisible: Boolean = true,
        gesturesEnabled: Boolean = true,
        modifier: Modifier = Modifier
): MediaPlaybackControls {
    val context = ContextAmbient.current
    val controller = remember {
        VideoPlayerController(context, source)
    }

    onPreCommit(source) {
        controller.setSource(source)
    }

    onPreCommit(controlsEnabled, gesturesEnabled, controlsVisible) {
        controller.enableControls(controlsEnabled)
        controller.enableGestures(gesturesEnabled)
        if(controlsVisible) controller.showControls() else controller.hideControls()
    }

    onCommit(backgroundColor) {
        controller.playerViewBackgroundColor = backgroundColor
    }

    val videoSize by controller.videoSize.collectAsState()

    Providers(
            ContentColorAmbient provides Color.White,
            VideoPlayerControllerAmbient provides controller
    ) {
        Stack(modifier = Modifier.fillMaxWidth()
                .drawBackground(color = backgroundColor)
                .aspectRatio(videoSize.width / videoSize.height)
                + modifier) {

            PlayerSurface(modifier = Modifier.gravity(Alignment.Center)) {
                controller.playerViewAvailable(it)
            }

            MediaControlGestures(modifier = Modifier.matchParentSize())
            MediaControlButtons(modifier = Modifier.matchParentSize())
            ProgressIndicator(modifier = Modifier.gravity(Alignment.BottomCenter))
        }
    }

    onDispose {
        controller.onDispose()
    }

    return controller
}