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
package anystream.util

import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.MouseEvent

/**
 * [ExternalClickMask] invokes [onClickOutside] when a click occurs outside
 * of the [target] element.
 */
class ExternalClickMask(
    private val target: HTMLElement,
    private val onClickOutside: (remove: () -> Unit) -> Unit,
) {
    @Suppress("JoinDeclarationAndAssignment")
    private var listener: EventListener? = null

    init {
        listener = EventListener { event: Event ->
            if (event.currentTarget == target) return@EventListener
            check(event is MouseEvent)
            val rect = target.getBoundingClientRect()
            if (event.x !in rect.x..(rect.x + rect.width) ||
                event.y !in rect.y..(rect.y + rect.height)
            ) {
                onClickOutside {
                    window.removeEventListener("click", this.listener)
                }
            }
        }
    }

    fun attachListener() {
        window.addEventListener("click", listener)
    }

    fun detachListener() {
        window.removeEventListener("click", listener)
    }

    fun dispose() {
        detachListener()
        listener = null
    }
}
