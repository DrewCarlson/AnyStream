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
package anystream.components

import androidx.compose.runtime.*
import anystream.util.Bootstrap
import anystream.util.newElementId
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.Event

@Composable
fun Modal(
    id: String,
    title: String? = null,
    bodyAttrs: (AttrsScope<HTMLDivElement>.() -> Unit)? = null,
    contentAttrs: (AttrsScope<HTMLDivElement>.() -> Unit)? = null,
    size: String? = null,
    scrollable: Boolean = false,
    onHide: (() -> Unit)? = null,
    header: (@Composable ElementScope<HTMLDivElement>.(labelId: String) -> Unit)? = null,
    footer: (@Composable ElementScope<HTMLDivElement>.() -> Unit)? = null,
    body: @Composable ElementScope<HTMLDivElement>.(modalRef: Bootstrap.ModalInstance) -> Unit,
) {
    val labelId = remember(id) { "modal_${newElementId()}" }
    var modal by remember(id) { mutableStateOf<Bootstrap.ModalInstance?>(null) }
    Div({
        id(id)
        tabIndex(-1)
        classes("modal", "fade")
        attr("aria-hidden", "true")
        ref { ref ->
            val callback = { _: Event ->
                onHide?.invoke()
                Unit
            }
            ref.addEventListener("hide.bs.modal", callback)
            onDispose { ref.removeEventListener("hide.bs.modal", callback) }
        }
    }) {
        DisposableEffect(Unit) {
            modal = Bootstrap.Modal.getOrCreateInstance(scopeElement)
            onDispose { modal = null }
        }
        Div({
            classes("modal-dialog")
            if (scrollable) {
                classes("modal-dialog-scrollable")
            }
            size?.let { classes("modal-$size") }
        }) {
            Div({
                classes("modal-content")
                contentAttrs?.invoke(this)
            }) {
                if (title != null) {
                    Div({ classes("modal-header") }) {
                        H5({
                            classes("modal-title")
                            id(labelId)
                        }) { Text(title) }
                    }
                } else if (header != null) {
                    Div({ classes("modal-header") }) {
                        header(labelId)
                    }
                }

                Div({
                    classes("modal-body")
                    bodyAttrs?.invoke(this)
                }) {
                    modal?.let { body(it) }
                }

                if (footer != null) {
                    Div({ classes("modal-footer") }, content = footer)
                }
            }
        }
    }
}

@Composable
fun ModalCloseButton() {
    Button({
        classes("btn-close-white")
        type(ButtonType.Button)
        attr("data-bs-dismiss", "modal")
        attr("aria-label", "Close")
    })
}
