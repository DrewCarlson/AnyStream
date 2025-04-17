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
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit.SECONDS


// Same as MediaPlayerComponentDefaults.EMBEDDED_MEDIA_PLAYER_ARGS
private val PLAYER_ARGS = listOf(
    "--video-title=vlcj video output",
    "--no-snapshot-preview",
    "--quiet",
    "--intf=dummy",
)

class VlcjPlayerHandle(
    private val client: AnyStreamClient,
) : BasePlayerHandle() {
    private val mediaPlayerFactory = MediaPlayerFactory(PLAYER_ARGS)
    val mediaPlayer = mediaPlayerFactory
        .mediaPlayers()
        .newEmbeddedMediaPlayer()

    private var currentMediaLinkId: String? = null

    init {
        mediaPlayer.events().addMediaPlayerEventListener(VlcjEventHandler())
    }

    override fun play() {
        emitPlayWhenReady(true)
        mediaPlayer.controls().play()
    }

    override fun pause() {
        emitPlayWhenReady(false)
        mediaPlayer.controls().pause()
    }

    override fun seekTo(position: Duration) {
        mediaPlayer.controls().setTime(position.inWholeMilliseconds)
        emitProgress(mediaPlayer.status().time().milliseconds)
    }

    override fun skipTime(time: Duration) {
        mediaPlayer.controls().skipTime(time.inWholeMilliseconds)
        emitProgress(mediaPlayer.status().time().milliseconds)
    }

    override fun loadMediaLink(mediaLinkId: String) {
        if (mediaLinkId == currentMediaLinkId) {
            return
        }
        currentMediaLinkId = mediaLinkId

        val handle = client.playbackSession(scope, mediaLinkId) { state ->
            println("[player] $state")
        }
        scope.launch {
            val url = handle.playbackUrl.await()
            val startPosition = handle.initialPlaybackState.await().position
            emitProgress(startPosition)
            println("[player] $url")
            if (startPosition == ZERO) {
                check(mediaPlayer.media().prepare(url))
            } else {
                check(mediaPlayer.media().prepare(url, ":start-time=${startPosition.toDouble(SECONDS)}"))
            }

            mediaPlayer.controls().setPause(playWhenReadyFlow.value)
            if (playWhenReadyFlow.value) {
                mediaPlayer.controls().play()
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

    override fun dispose() {
        mediaPlayer.release()
        mediaPlayerFactory.release()
        super.dispose()
    }

    private inner class VlcjEventHandler : MediaPlayerEventListener {
        override fun playing(mediaPlayer: MediaPlayer?) {
            emitState(PlayerHandle.State.READY)
            emitPlayWhenReady(true)
        }

        override fun paused(mediaPlayer: MediaPlayer?) {
            emitState(PlayerHandle.State.READY)
            emitPlayWhenReady(false)
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            if (newCache == 100f) {
                emitState(PlayerHandle.State.BUFFERING)
            } else {
                emitState(PlayerHandle.State.READY)
            }
        }

        override fun finished(mediaPlayer: MediaPlayer?) {
            emitState(PlayerHandle.State.ENDED)
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            emitProgress(newTime.milliseconds)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
            emitDuration(newLength.milliseconds)
        }

        override fun mediaChanged(mediaPlayer: MediaPlayer?, media: MediaRef?) {
        }

        override fun opening(mediaPlayer: MediaPlayer?) {
        }

        override fun stopped(mediaPlayer: MediaPlayer?) {
        }

        override fun forward(mediaPlayer: MediaPlayer?) {
        }

        override fun backward(mediaPlayer: MediaPlayer?) {
        }

        override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
        }

        override fun seekableChanged(mediaPlayer: MediaPlayer?, newSeekable: Int) {
        }

        override fun pausableChanged(mediaPlayer: MediaPlayer?, newPausable: Int) {
        }

        override fun titleChanged(mediaPlayer: MediaPlayer?, newTitle: Int) {
        }

        override fun snapshotTaken(mediaPlayer: MediaPlayer?, filename: String?) {
        }

        override fun videoOutput(mediaPlayer: MediaPlayer?, newCount: Int) {
        }

        override fun scrambledChanged(mediaPlayer: MediaPlayer?, newScrambled: Int) {
        }

        override fun elementaryStreamAdded(mediaPlayer: MediaPlayer?, type: TrackType?, id: Int) {
        }

        override fun elementaryStreamDeleted(mediaPlayer: MediaPlayer?, type: TrackType?, id: Int) {
        }

        override fun elementaryStreamSelected(mediaPlayer: MediaPlayer?, type: TrackType?, id: Int) {
        }

        override fun corked(mediaPlayer: MediaPlayer?, corked: Boolean) {
        }

        override fun muted(mediaPlayer: MediaPlayer?, muted: Boolean) {
        }

        override fun volumeChanged(mediaPlayer: MediaPlayer?, volume: Float) {
        }

        override fun audioDeviceChanged(mediaPlayer: MediaPlayer?, audioDevice: String?) {
        }

        override fun chapterChanged(mediaPlayer: MediaPlayer?, newChapter: Int) {
        }

        override fun error(mediaPlayer: MediaPlayer?) {
        }

        override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
        }
    }
}
