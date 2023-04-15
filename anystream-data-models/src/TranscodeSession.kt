/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.models

import kotlinx.serialization.Serializable

@Serializable
data class TranscodeSession(
    val token: String,
    val mediaLinkId: String,
    val mediaPath: String,
    val outputPath: String,
    val ffmpegCommand: String,
    val runtime: Double,
    val segmentCount: Int,
    val segmentLength: Int,
    val startSegment: Int,
    val endSegment: Int,
    val startTime: Double,
    val endTime: Double,
    val lastTranscodedSegment: Int,
    val state: State,
    val transcodedSegments: List<Int>,
) {
    enum class State {
        IDLE, RUNNING, COMPLETE
    }

    fun isSegmentComplete(segment: Int): Boolean {
        return transcodedSegments.contains(segment)
    }

    fun isRunning(): Boolean = state == State.RUNNING
}
