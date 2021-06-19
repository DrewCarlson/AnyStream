/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.client.navigation


data class NavigationModel(
    val isAuthenticated: Boolean,
    val navigationStack: List<NavigationTarget>,
    val pendingTarget: NavigationTarget? = null,
) {
    val currentTarget: NavigationTarget?
        get() = navigationStack.lastOrNull()
}

sealed class NavigationTarget {
    object Home : NavigationTarget()

    object Login : NavigationTarget()
    data class Signup(
        val inviteCode: String? = null,
    ) : NavigationTarget()

    data class MediaPlayer(
        val mediaRefId: String
    ) : NavigationTarget()
}