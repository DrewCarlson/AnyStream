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
package anystream.frontend.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import anystream.client.AnyStreamClient
import drewcarlson.qbittorrent.models.ConnectionStatus
import drewcarlson.qbittorrent.models.Torrent
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import org.jetbrains.compose.web.attributes.Scope
import org.jetbrains.compose.web.attributes.scope
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.math.roundToInt

@Composable
fun DownloadsScreen(client: AnyStreamClient) {
    val globalInfoState = client.globalInfoChanges().collectAsState(null)
    val torrents = client.torrentListChanges()
        .debounce(2000)
        .mapLatest { client.getTorrents() }
        .onStart { emit(client.getTorrents()) }
        .collectAsState(emptyList())
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
        }
    }) {
        Div({
            style {
                classes("p-2", "bg-dark")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Row)
                alignItems(AlignItems.Center)
                property("gap", 12.px)
            }
        }) {
            val globalInfo = globalInfoState.value
            val (statusName, iconClass) = when (globalInfo?.connectionStatus) {
                ConnectionStatus.CONNECTED -> "Connected" to "bi-wifi"
                ConnectionStatus.FIREWALLED -> "Firewalled" to "bi-shield-exclamation"
                ConnectionStatus.DISCONNECTED -> "Disconnected" to "bi-plug"
                else -> "Loading" to "bi-hourglass-split"
            }
            I({ classes(iconClass) })
            Span { Text(statusName) }
            globalInfo?.run {
                Span { Text("Upload: $upInfoSpeed") }
                Span { Text("Download: $dlInfoSpeed") }
            }
        }
        Div({
            classes("table-responsive")
        }) {
            Table({
                classes("table", "table-dark", "table-striped", "table-hover")
            }) {
                Thead { TorrentHeader() }
                Tbody {
                    torrents.value.forEach { torrent ->
                        TorrentRow(torrent)
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentHeader() {
    Tr {
        Th({ scope(Scope.Col) }) { Text("Name") }
        Th({ scope(Scope.Col) }) { Text("Size") }
        Th({ scope(Scope.Col) }) { Text("Progress") }
        Th({ scope(Scope.Col) }) { Text("Status") }
        Th({ scope(Scope.Col) }) { Text("Seeds") }
        Th({ scope(Scope.Col) }) { Text("Peers") }
        Th({ scope(Scope.Col) }) { Text("Down Speed") }
        Th({ scope(Scope.Col) }) { Text("Up Speed") }
        Th({ scope(Scope.Col) }) { Text("ETA") }
        Th({ scope(Scope.Col) }) { Text("Ratio") }
        Th({ scope(Scope.Col) }) { Text("Availability") }
    }
}

@Composable
private fun TorrentRow(torrent: Torrent) {
    Tr {
        Td {
            I({ classes(stateIcon(torrent)) })
            Span { Text(torrent.name) }
        }
        Td {
            Text(torrent.size.toString())
        }
        Td {
            Text("${(torrent.progress * 100).roundToInt()}%")
        }
        Td {
            Text(torrent.state.toString())
        }
        Td {
            Text("${torrent.connectedSeeds} (${torrent.seedsInSwarm})")
        }
        Td {
            Text("${torrent.connectedLeechers} (${torrent.leechersInSwarm})")
        }
        Td {
            Text(torrent.dlspeed.toString())
        }
        Td {
            Text(torrent.uploadSpeed.toString())
        }
        Td {
            Text(torrent.eta.toString())
        }
        Td {
            Text(torrent.ratio.toString())
        }
        Td {
            Text(torrent.availability.toString())
        }
    }
}


private fun stateIcon(torrent: Torrent): String {
    return when (torrent.state) {
        Torrent.State.PAUSED_DL,
        Torrent.State.QUEUED_UP -> "bi-pause-fill"
        Torrent.State.STALLED_DL -> "bi-binoculars-fill"
        Torrent.State.CHECKING_UP,
        Torrent.State.STALLED_UP,
        Torrent.State.FORCED_UP,
        Torrent.State.ALLOCATING,
        Torrent.State.CHECKING_DL,
        Torrent.State.META_DL,
        Torrent.State.FORCED_DL,
        Torrent.State.CHECKING_RESUME_DATA,
        Torrent.State.DOWNLOADING -> "bi-play-fill"
        Torrent.State.UPLOADING -> "bi-cloud-upload-fill"
        Torrent.State.MOVING -> "fi-file-earmark-arrow-up-fill"
        Torrent.State.MISSING_FILES,
        Torrent.State.ERROR,
        Torrent.State.UNKNOWN -> "bi-exclamation-triangle-fill"
        Torrent.State.PAUSED_UP -> "bi-check-circle-fill"
    }
}
