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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackState(
    @SerialName("_id")
    val id: String,
    val mediaReferenceId: String,
    val mediaId: String,
    val userId: String,
    val position: Double,
    val runtime: Double,
    val updatedAt: Long = 0L,
) {
    val completedPercent: Float
        get() {
            return (position / runtime)
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}
