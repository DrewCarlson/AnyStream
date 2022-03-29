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
package anystream.frontend.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import kotlin.random.Random

@Composable
fun ModalToggle(
    id: String
) {
    Button({
        classes("btn", "btn-primary")
        attr("data-bs-toggle", "modal")
        attr("data-bs-target", "#$id")
    }) {
        Text("modal")
    }
}

@Composable
fun Modal(
    id: String,
    title: String? = null,
    staticBackdrop: Boolean = false,
    closeButton: Boolean = true,
    header: (@Composable ElementScope<HTMLDivElement>.(labelId: String) -> Unit)? = null,
    footer: (@Composable ElementScope<HTMLDivElement>.() -> Unit)? = null,
    body: @Composable ElementScope<HTMLDivElement>.() -> Unit,
) {
    val labelId = remember { Random.nextLong(0, Long.MAX_VALUE).toString() }
    Div({
        id(id)
        tabIndex(-1)
        classes("modal", "fade")
        attr("aria-hidden", "true")
        if (staticBackdrop) {
            attr("data-bs-backdrop", "static")
        }
    }) {
        Div({ classes("modal-dialog", "modal-dialog-scrollable") }) {
            Div({ classes("modal-content") }) {
                if (title != null) {
                    Div({ classes("modal-header") }) {
                        H5({
                            classes("modal-title")
                            id(labelId)
                        }) { Text(title) }
                        if (closeButton) {
                            Button({
                                classes("btn-close")
                                type(ButtonType.Button)
                                attr("data-bs-dismiss", "modal")
                                attr("aria-label", "Close")
                            })
                        }
                    }
                } else if (header != null) {
                    Div({ classes("modal-header") }) {
                        header(labelId)
                        if (closeButton) {
                            Button({
                                classes("btn-close")
                                type(ButtonType.Button)
                                attr("data-bs-dismiss", "modal")
                                attr("aria-label", "Close")
                            })
                        }
                    }
                }

                Div({ classes("modal-body") }) {
                    body()
                }

                if (footer != null) {
                    Div({ classes("modal-footer") }) {
                        footer()
                    }
                }
            }
        }
    }
}
