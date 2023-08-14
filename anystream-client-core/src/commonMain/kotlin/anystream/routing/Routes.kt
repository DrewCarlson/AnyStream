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
package anystream.routing

sealed class Routes {
    abstract val path: String

    data object Login : Routes() {
        override val path: String = "login"
    }

    data object Home : Routes() {
        override val path: String = "home"
    }

    data object PairingScanner : Routes() {
        override val path: String = "pairing-scanner"
    }

    data object Movies : Routes() {
        override val path: String = "movies"
    }

    data object Tv : Routes() {
        override val path: String = "tv"
    }

    data class Details(
        val mediaRefId: String,
    ) : Routes() {
        override val path: String = "details/$mediaRefId"
    }

    data class Player(
        val mediaLinkId: String,
    ) : Routes() {
        override val path: String = "player/$mediaLinkId"
    }
}
