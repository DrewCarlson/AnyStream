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

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration


interface PlayerHandle {

    enum class State {
        IDLE, BUFFERING, READY, ENDED;
    }

    val progressFlow: StateFlow<Duration>
    val durationFlow: StateFlow<Duration>
    val bufferProgressFlow: StateFlow<Duration>
    val progressPercentFlow: StateFlow<Float>
    val bufferProgressPercentFlow: StateFlow<Float>
    val playWhenReadyFlow: StateFlow<Boolean>
    val stateFlow: StateFlow<State>
    val canSkipForward: StateFlow<Boolean>
    val canSkipBackward: StateFlow<Boolean>

    fun loadMediaLink(mediaLinkId: String)

    fun play()
    fun pause()
    fun seekTo(position: Duration)
    fun seekToPercent(percent: Float)
    fun skipTime(time: Duration)
    fun skipForward()
    fun skipBackward()

    fun togglePlaying() {
        if (playWhenReadyFlow.value) {
            pause()
        } else {
            play()
        }
    }

    fun dispose()
}
