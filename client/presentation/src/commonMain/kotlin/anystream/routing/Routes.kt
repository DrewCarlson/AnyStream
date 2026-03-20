/*
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

import anystream.models.LibraryId
import anystream.models.MediaLinkId
import anystream.models.MetadataId
import dev.drewhamilton.poko.Poko

sealed class Routes {
    abstract val path: String

    data object Welcome : Routes() {
        override val path: String = "welcome"
    }

    data object Login : Routes() {
        override val path: String = "login"
    }

    data object SignUp : Routes() {
        override val path: String = "signup"
    }

    data object Home : Routes() {
        override val path: String = "home"
    }

    data object PairingScanner : Routes() {
        override val path: String = "pairing-scanner"
    }

    @Poko
    class Library(
        val libraryId: LibraryId,
    ) : Routes() {
        override val path: String = "library/${libraryId.value}"
    }

    data class Details(
        val metadataId: MetadataId,
    ) : Routes() {
        override val path: String = "details/${metadataId.value}"
    }

    data class Player(
        val mediaLinkId: MediaLinkId,
    ) : Routes() {
        override val path: String = "player/${mediaLinkId.value}"
    }

    data object Profile : Routes() {
        override val path: String = "profile"
    }

    companion object {
        val LOGIN_STACK = listOf(Welcome, Login)

        fun isOnboardingRoute(route: Routes): Boolean {
            return when (route) {
                Login,
                SignUp,
                Welcome,
                -> true

                is Details,
                Home,
                is Library,
                PairingScanner,
                is Player,
                Profile,
                -> false
            }
        }
    }
}
