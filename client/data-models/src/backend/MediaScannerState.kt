/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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
package anystream.models.backend

import anystream.models.MediaLink
import kotlinx.serialization.Serializable

@Serializable
sealed class MediaScannerState {

    @Serializable
    data class Active(
        val mediaLinkGids: Set<String> = emptySet(),
    ) : MediaScannerState()

    @Serializable
    object Idle : MediaScannerState() {
        override fun toString(): String = "Idle"
    }
}

@Serializable
sealed class MediaScannerMessage {

    @Serializable
    data class Scanning(
        val link: MediaLink,
    ) : MediaScannerMessage()

    @Serializable
    data class ScanComplete(
        val link: MediaLink,
        val updatedLinkGids: List<String>,
    ) : MediaScannerMessage()

    @Serializable
    object Idle : MediaScannerMessage()
}
