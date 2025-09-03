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
package anystream.screens.settings.library

import androidx.compose.runtime.*
import anystream.client.AnyStreamClient
import anystream.models.Library
import anystream.util.bootstrapIcon
import anystream.util.get
import anystream.util.tooltip
import app.softwork.routingcompose.Router
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.dom.*

@Composable
fun LibrariesScreen() {
    val router = Router.current
    val client = get<AnyStreamClient>()
    val scope = rememberCoroutineScope()
    var editLibrary by remember { mutableStateOf<Library?>(null) }
    val libraries by produceState(emptyList<Library>()) {
        value = try {
            client.library.getLibraries()
        } catch (e: Throwable) {
            emptyList()
        }
    }
    Div({ classes("vstack", "h-100", "w-100", "gap-1", "p-2") }) {
        H3 { Text("Libraries") }
        Div({ classes("d-flex", "gap-1") }) {
            /*Button({
                classes("btn", "btn-primary")
                //onClick { modal?.show() }
            }) {
                //I({ classes("bi", "bi-folder-plus", "pe-1") })
                Text("Add Library")
            }*/
            Button({
                classes("btn", "btn-primary")
                onClick {
                    scope.launch {
                        libraries.forEach { library ->
                            client.library.scanLibrary(library.id)
                        }
                    }
                }
            }) {
                //I({ classes("bi", "bi-folder-plus", "pe-1") })
                Text("Scan Library Files")
            }
        }
        Div({ classes("table-responsive") }) {
            Table({ classes("table", "table-hover") }) {
                Tbody {
                    libraries.forEach { library ->
                        Tr {
                            Th({ scope(Scope.Row) }) {
                                Div({ classes("hstack", "gap-3") }) {
                                    FolderAction("Edit Library", "gear-wide") {
                                        editLibrary = library
                                    }
                                    I({
                                        tooltip(library.name)
                                        classes("pe-1", "bi", library.mediaKind.bootstrapIcon)
                                    })
                                    Text(library.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editLibrary?.let {
        EditLibraryModal(
            library = it,
            onClosed = { editLibrary = null }
        )
    }
}

@Composable
fun FolderAction(
    title: String,
    icon: String,
    onClick: () -> Unit,
) {
    A(null, {
        style { cursor("pointer") }
        onClick { onClick() }
    }) {
        I({
            tooltip(title)
            classes("bi", "bi-$icon")
        })
    }
}
