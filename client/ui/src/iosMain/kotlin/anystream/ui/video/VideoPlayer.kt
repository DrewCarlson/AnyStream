/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.ui.video

import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import anystream.getClient
import anystream.models.PlaybackState
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import observer.ObserverProtocol
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSError
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSURL
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.UIKit.UIColor
import platform.UIKit.UIColor.Companion.blackColor
import platform.UIKit.UIView
import platform.darwin.NSObject

@Composable
internal actual fun VideoPlayer(
    modifier: Modifier,
    mediaLinkId: String,
    isPlaying: Boolean,
) {
    val client = remember { getClient() }
    var position by rememberSaveable { mutableStateOf(0L) }
    val player = remember { AVPlayer() }
    produceState<PlaybackState?>(null) {
        val initialState = MutableStateFlow<PlaybackState?>(null)
        val handle = client.playbackSession(mediaLinkId) { state ->
            println("[player] $state")
            initialState.value = state
            position = (state.position * 1000).toLong()
            value = state
        }
        val state = initialState.filterNotNull().first()
        val observer = StatusObserver { status, _ ->
            if (status == AVPlayerStatusReadyToPlay) {
                val time = CMTimeMakeWithSeconds(state.position, 1)
                val zero = kCMTimeZero.readValue()
                player.seekToTime(time, zero, zero)
                if (isPlaying) {
                    player.play()
                }
            }
        }
        val url = client.createHlsStreamUrl(mediaLinkId, state.id)
        println("[player] $url")
        val nsUrl = checkNotNull(NSURL.URLWithString(url))
        val newItem = AVPlayerItem.playerItemWithURL(nsUrl).apply {
            addObserver(
                observer = observer,
                forKeyPath = "status",
                options = NSKeyValueObservingOptionNew,
                context = null,
            )
        }
        player.replaceCurrentItemWithPlayerItem(newItem)
        launch {
            while (true) {
                val currentItem = player.currentItem
                if (currentItem != null && player.status == AVPlayerStatusReadyToPlay) {
                    val currentTime = CMTimeGetSeconds(currentItem.currentTime())
                    handle.update.tryEmit(currentTime.coerceAtLeast(0.0))
                }
                delay(PLAYER_STATE_REMOTE_UPDATE_INTERVAL)
            }
        }
        awaitDispose {
            handle.cancel()
            newItem.removeObserver(observer, forKeyPath = "status")
        }
    }
    LaunchedEffect(isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }
    val factory = remember {
        {
            UIView().apply {
                backgroundColor = blackColor
                val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)
                    .apply { backgroundColor = UIColor.blackColor.CGColor }
                layer.addSublayer(playerLayer)
            }
        }
    }
    UIKitView(
        modifier = modifier.background(Color.Black),
        factory = factory,
        onResize = { view, size ->
            view.layer.sublayers.orEmpty()
                .filterIsInstance<AVPlayerLayer>()
                .forEach { it.setFrame(size) }
        },
        interactive = false,
    )
}

class StatusObserver(
    private val handler: (status: AVPlayerStatus, error: NSError?) -> Unit,
) : NSObject(), ObserverProtocol {
    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: COpaquePointer?,
    ) {
        val value = ofObject as? AVPlayerItem ?: return
        handler(value.status, value.error)
        val status = when (value.status) {
            AVPlayerStatusFailed -> "AVPlayerStatusFailed"
            AVPlayerStatusReadyToPlay -> "AVPlayerStatusReadyToPlay"
            AVPlayerStatusUnknown -> "AVPlayerStatusUnknown"
            else -> error("Unknown player status: ${value.status}")
        }
        println("Player Status: $status")
    }
}
