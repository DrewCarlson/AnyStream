/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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

import anystream.client.AnyStreamClient
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import observer.ObserverProtocol
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

class AvPlayerHandle(
    private val client: AnyStreamClient,
) : BasePlayerHandle() {

    val player = AVPlayer()

    private var currentMediaId: String? = null
    private var updateStateJob: Job? = null

    override fun play() {
        emitPlayWhenReady(true)
        player.play()
    }

    override fun pause() {
        emitPlayWhenReady(false)
        player.pause()
    }

    override fun seekTo(position: Duration) {
        player.seekToTime(CMTimeMakeWithSeconds(position.toDouble(SECONDS), 1))
    }

    override fun skipTime(time: Duration) {
        val currentPos = CMTimeGetSeconds(player.currentTime()).seconds
        val newPos = CMTimeMakeWithSeconds((currentPos + time).toDouble(SECONDS), 1)
        player.seekToTime(newPos)
    }

    override fun loadMediaLink(mediaLinkId: String) {
        if (mediaLinkId == currentMediaId) {
            return
        }
        currentMediaId = mediaLinkId
        scope.launch {
            val handle = client.playbackSession(mediaLinkId) { state ->
                println("[player] $state")
            }
            val url = handle.playbackUrl.await()
            val startPosition = handle.initialPlaybackState.await().position
            println("[player] $url")
            val observer = StatusObserver { status, error ->
                updateStateJob?.cancel()
                println("[player] error = ${error?.localizedDescription()}")
                if (status == AVPlayerStatusReadyToPlay) {
                    val currentItem = player.currentItem!!
                    val time = CMTimeMakeWithSeconds(startPosition.toDouble(SECONDS), 1)
                    val zero = kCMTimeZero.readValue()
                    player.seekToTime(time, zero, zero)
                    if (playWhenReadyFlow.value) {
                        player.play()
                    }
                    emitDuration(CMTimeGetSeconds(currentItem.duration()).seconds)
                    emitProgress(startPosition)
                    updateStateJob = scope.launch {
                        while (true) {
                            val progress = CMTimeGetSeconds(currentItem.currentTime())
                            emitProgress(progress.seconds)
                            emitBufferProgress(currentItem.currentBufferedProgress() ?: ZERO)
                            delay(PLAYER_STATE_UPDATE_INTERVAL)
                        }
                    }
                }
            }
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
            try {
                while (true) {
                    val currentItem = player.currentItem
                    if (currentItem != null && player.status == AVPlayerStatusReadyToPlay) {
                        val currentTime = CMTimeGetSeconds(currentItem.currentTime()).seconds
                        handle.update.tryEmit(currentTime)
                        emitProgress(currentTime)
                    }
                    delay(PLAYER_STATE_REMOTE_UPDATE_INTERVAL)
                }
            } finally {
                newItem.removeObserver(observer, forKeyPath = "status")
            }
        }
    }

    override fun dispose() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        super.dispose()
    }

    /**
     * Extract the [Duration] marking the buffered progress for the
     * current playback process.
     */
    fun AVPlayerItem.currentBufferedProgress(): Duration? {
        val progress = CMTimeGetSeconds(currentTime())
        return loadedTimeRanges
            .filterIsInstance<NSValue>()
            .map { value ->
                value.CMTimeRangeValue.useContents {
                    val startSeconds = CMTimeGetSeconds(start.readValue())
                    val durationSeconds = CMTimeGetSeconds(this.duration.readValue())
                    startSeconds..(startSeconds + durationSeconds)
                }
            }
            .firstOrNull { progress in it }
            ?.endInclusive
            ?.seconds
    }
}

private class StatusObserver(
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
            else -> {
                println("Unknown player status: ${value.status}")
                null
            }
        }
        println("Player Status: $status")
    }
}
