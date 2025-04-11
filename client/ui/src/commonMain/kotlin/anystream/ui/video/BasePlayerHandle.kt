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

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("MemberVisibilityCanBePrivate")
abstract class BasePlayerHandle : PlayerHandle {

    private val skipInterval = 10.seconds

    protected val scope = CoroutineScope(Default + SupervisorJob() + CoroutineName("PlayerHandle"))

    private val _progressFlow = MutableStateFlow(0.seconds)
    private val _durationFlow = MutableStateFlow(0.seconds)
    private val _bufferProgressFlow = MutableStateFlow(0.seconds)
    private val _playWhenReady = MutableStateFlow(true)
    private val _stateFlow = MutableStateFlow(PlayerHandle.State.IDLE)

    final override val progressFlow: StateFlow<Duration> = _progressFlow.asStateFlow()
    final override val durationFlow: StateFlow<Duration> = _durationFlow.asStateFlow()
    final override val bufferProgressFlow: StateFlow<Duration> = _bufferProgressFlow.asStateFlow()
    final override val playWhenReadyFlow: StateFlow<Boolean> = _playWhenReady.asStateFlow()
    final override val stateFlow: StateFlow<PlayerHandle.State> = _stateFlow.asStateFlow()

    final override val progressPercentFlow: StateFlow<Float> =
        combine(progressFlow, durationFlow) { progress, duration ->
            if (progress == ZERO || duration == ZERO) {
                0f
            } else {
                (progress / duration).toFloat()
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0f)

    final override val bufferProgressPercentFlow: StateFlow<Float> =
        combine(bufferProgressFlow, durationFlow) { progress, duration ->
            if (progress == ZERO || duration == ZERO) {
                0f
            } else {
                (progress / duration).toFloat()
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0f)

    override val canSkipBackward: StateFlow<Boolean> =
        _progressFlow
            .map { it > ZERO }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override val canSkipForward: StateFlow<Boolean> =
        combine(_durationFlow, _progressFlow) { duration, progress -> duration - progress }
            .map { remaining -> remaining >= skipInterval }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun seekToPercent(percent: Float) {
        val duration = durationFlow.value
        val durationMs = duration.inWholeMilliseconds
        val target = (durationMs * percent).toDouble().milliseconds
        seekTo(target.coerceIn(ZERO, duration))
    }

    override fun skipBackward() {
        if (canSkipBackward.value) {
            skipTime(-skipInterval)
        }
    }

    override fun skipForward() {
        if (canSkipForward.value) {
            skipTime(skipInterval)
        }
    }

    protected fun emitProgress(progress: Duration) {
        _progressFlow.update { progress }
    }

    protected fun emitDuration(duration: Duration) {
        _durationFlow.update { duration }
    }

    protected fun emitBufferProgress(bufferProgress: Duration) {
        _bufferProgressFlow.update { bufferProgress }
    }

    protected fun emitState(state: PlayerHandle.State) {
        _stateFlow.update { state }
    }

    protected fun emitPlayWhenReady(playWhenReady: Boolean) {
        _playWhenReady.update { playWhenReady }
    }

    override fun dispose() {
        scope.cancel()
    }
}
