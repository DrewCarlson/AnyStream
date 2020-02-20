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
package anystream.frontend

import anystream.client.AnyStreamClient
import anystream.torrent.search.TorrentDescription2
import io.ktor.client.features.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.kvision.html.ButtonSize
import io.kvision.html.ButtonStyle
import io.kvision.html.button
import io.kvision.modal.Modal
import io.kvision.modal.ModalSize
import io.kvision.state.observableListOf
import io.kvision.table.cell
import io.kvision.table.row
import io.kvision.table.table
import io.kvision.toast.Toast


class TorrentSearchResultsModal(
    private val client: AnyStreamClient,
    private val movieId: String,
    title: String,
    private val fetchResults: suspend () -> List<TorrentDescription2>
) : Modal(
    caption = title,
    size = ModalSize.XLARGE,
    scrollable = true
) {

    private val sources = observableListOf<TorrentDescription2>()

    init {
        GlobalScope.launch {
            try {
                sources.addAll(fetchResults())
                if (sources.isEmpty()) {
                    hide()
                    Toast.warning("No torrents found for \"$title\"")
                } else {
                    show()
                }
            } catch (e: ClientRequestException) {
                e.printStackTrace()
                hide()
                Toast.error("Error searching for \"$title\"")
            }
        }
        table(
            sources,
            classes = setOf("table", "table-hover"),
            headerNames = listOf("", "Provider", "Name", "Seeds", "Peers")
        ) { state ->
            state.onEach { source ->
                row {
                    cell {
                        button(
                            text = "",
                            icon = "fas fa-plus",
                            style = ButtonStyle.PRIMARY
                        ) {
                            size = ButtonSize.SMALL
                            onClick { addTorrent(source) }
                        }
                    }
                    cell(source.provider)
                    cell(source.title)
                    cell(source.seeds.toString())
                    cell(source.peers.toString())
                }
            }
        }
    }

    private fun addTorrent(description: TorrentDescription2) {
        GlobalScope.launch {
            try {
                client.downloadTorrent(description, movieId)
                hide()
                Toast.success("Torrent added")
            } catch (e: ClientRequestException) {
                e.printStackTrace()
                Toast.error("Failed to add torrent")
            }
        }
    }
}
