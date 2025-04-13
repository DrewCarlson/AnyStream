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

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import anystream.client.AnyStreamClient
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class Media3PlayerHandle(
    context: Context,
    private val client: AnyStreamClient,
) : BasePlayerHandle() {

    val player = ExoPlayer.Builder(context).build()

    private var updateStateJob: Job? = null
    private var currentMediaLinkId: String? = null

    init {
        player.addListener(PlayerListener())
        if (player.isCommandAvailable(Player.COMMAND_PREPARE)) {
            player.prepare()
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updateStateJob?.cancel()
            if (state == Player.STATE_READY) {
                updateStateJob = scope.launch(Dispatchers.Main) {
                    while (true) {
                        emitProgress(player.currentPosition.milliseconds)
                        emitBufferProgress(player.bufferedPosition.milliseconds)
                        delay(PLAYER_STATE_UPDATE_INTERVAL)
                    }
                }
            }
            val handleState = when (state) {
                Player.STATE_READY -> PlayerHandle.State.READY
                Player.STATE_BUFFERING -> PlayerHandle.State.BUFFERING
                Player.STATE_IDLE -> PlayerHandle.State.IDLE
                Player.STATE_ENDED -> PlayerHandle.State.ENDED
                else -> error("[Media3PlayerHandle] unhandled player state: $state")
            }
            emitState(handleState)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            emitPlayWhenReady(playWhenReady)
        }
    }

    override fun play() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun skipTime(time: Duration) {
        player.seekTo(
            (player.currentPosition + time.inWholeMilliseconds)
                .coerceIn(0L, player.duration)
        )
    }

    override fun seekTo(position: Duration) {
        player.seekTo(player.currentMediaItemIndex, position.inWholeMilliseconds)
    }

    override fun dispose() {
        player.release()
        super.dispose()
    }

    override fun loadMediaLink(mediaLinkId: String) {
        if (mediaLinkId == currentMediaLinkId) {
            return
        }
        currentMediaLinkId = mediaLinkId
        scope.launch {
            val handle = client.playbackSession(mediaLinkId) { state ->
                println("[PlayerHandle] $state")
                emitDuration(state.runtime)
                emitProgress(state.position)
            }

            val url = handle.playbackUrl.await()
            val startPosition = handle.initialPlaybackState.await().position
            println("[PlayerHandle] $url")
            withContext(Dispatchers.Main) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.seekTo(player.currentMediaItemIndex, startPosition.inWholeMilliseconds)
                player.play()
            }

            var lastSentProgress: Duration = startPosition
            progressFlow.collect { progress ->
                if (progress < lastSentProgress) {
                    lastSentProgress = progress
                } else if ((progress - lastSentProgress) >= PLAYER_STATE_REMOTE_UPDATE_INTERVAL) {
                    lastSentProgress = progress
                    handle.update.tryEmit(progress)
                }
            }
        }
    }
}
