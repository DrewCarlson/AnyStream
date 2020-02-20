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
import drewcarlson.qbittorrent.models.Torrent
import drewcarlson.qbittorrent.models.Torrent.State.*
import drewcarlson.qbittorrent.models.ConnectionStatus
import drewcarlson.qbittorrent.models.GlobalTransferInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.*
import io.kvision.core.AlignItems
import io.kvision.core.Container
import io.kvision.core.Widget
import io.kvision.data.dataContainer
import io.kvision.dropdown.cmLink
import io.kvision.dropdown.contextMenu
import io.kvision.form.check.CheckBox
import io.kvision.html.*
import io.kvision.modal.Modal
import io.kvision.panel.*
import io.kvision.state.observableListOf
import io.kvision.state.observableState
import io.kvision.utils.px
import kotlin.math.roundToInt


class DownloadsPage(
    private val client: AnyStreamClient
) : VPanel(), CoroutineScope {

    override val coroutineContext = Default + SupervisorJob()
    private val scope: CoroutineScope = this
    private val torrentsObservable = observableListOf<Torrent>()
    private val info = client.globalInfoChanges()
        .transform { emit(it as GlobalTransferInfo?) }
        .onStart {
            emit(null)
            emit(client.getGlobalTransferInfo())
        }
        .shareIn(scope, SharingStarted.WhileSubscribed())

    init {
        observeTorrents()
        addGlobalStats()
        dataContainer(torrentsObservable, { torrent, _, _ ->
            createTorrentRow(torrent)
        })
    }

    private fun observeTorrents() {
        client.torrentListChanges()
            .debounce(2000)
            .onStart { emit(emptyList()) }
            .mapLatest { client.getTorrents() }
            .onEach { torrents ->
                torrentsObservable.clear()
                torrentsObservable.addAll(torrents)
            }
            .launchIn(scope)
    }

    private fun Container.addGlobalStats(): HPanel {
        return hPanel(
            info.observableState,
            spacing = 8
        ) { state ->
            if (state == null) {
                div(classes = setOf("spinner-grow", "text-primary")) {
                    span(className = "visually-hidden")
                }
            } else {
                icon(
                    when (state.connectionStatus) {
                        ConnectionStatus.CONNECTED -> "fas fa-wifi"
                        ConnectionStatus.FIREWALLED -> "fas fa-shield-alt"
                        ConnectionStatus.DISCONNECTED -> "fas fa-plug"
                    }
                ) {
                    title = state.connectionStatus.name
                }
                label("Upload: ${state.upInfoSpeed}")
                label("Download: ${state.dlInfoSpeed}")
            }
        }
    }

    private fun Container.createTorrentRow(torrent: Torrent): HPanel {
        return hPanel(
            spacing = 8,
            alignItems = AlignItems.CENTER
        ) {
            createTorrentContextMenu(this, torrent)
            button("", "fas fa-trash", style = ButtonStyle.DANGER) {
                size = ButtonSize.SMALL
                onClick { showDeleteTorrent(torrent) }
            }
            button("", "fas fa-pause", style = ButtonStyle.SECONDARY) {
                size = ButtonSize.SMALL
                onClick {
                    scope.launch {
                        client.pauseTorrent(torrent.hash)
                    }
                }
            }
            button("", "fas fa-play", style = ButtonStyle.PRIMARY) {
                size = ButtonSize.SMALL
                onClick {
                    scope.launch {
                        client.resumeTorrent(torrent.hash)
                    }
                }
            }
            button("", "fas fa-tv", style = ButtonStyle.INFO) {
                size = ButtonSize.SMALL
                onClick {
                    //Router.navigate("/play/${torrent.hash}")
                }
            }
            label("${(torrent.progress * 100).roundToInt()}%")
            label(torrent.name) {
                icon(stateIcon(torrent)) {
                    title = torrent.state.name
                    padding = 4.px
                }
            }
        }
    }

    private fun createTorrentContextMenu(widget: Widget, torrent: Torrent) {
        widget.contextMenu {
            header(align = Align.LEFT) {
                marginLeft = 8.px
                marginRight = 8.px
                content = if (torrent.name.length <= 20) {
                    torrent.name
                } else {
                    "${torrent.name.take(20)}..."
                }
            }
            cmLink(
                label = "Files",
                icon = "fas fa-folder-open"
            ) {
                onClick { showTorrentFiles(torrent) }
            }
        }
    }

    private fun showTorrentFiles(torrent: Torrent) {
        scope.launch {
            val files = client.getTorrentFiles(torrent.hash)
            val modal = Modal("Files for ${torrent.name}")
            modal.add(VPanel() {
                ul {
                    files.forEach { file ->
                        li {
                            label("Name: ${file.name}")
                            label("Size: ${file.size}")
                            label("Progress: ${file.progress}")
                        }
                    }
                }
            })
            modal.show()
        }
    }

    private fun showDeleteTorrent(torrent: Torrent) {
        val deleteFilesBox = CheckBox(false, label = "Delete Files")
        val modal = Modal("Confirm Delete")
        modal.add(Label("Are you sure you would like to delete \"${torrent.name}\"?"))
        modal.add(deleteFilesBox)
        modal.addButton(Button("Cancel", style = ButtonStyle.SECONDARY).apply {
            onClick { modal.hide() }
        })
        modal.addButton(Button("Confirm", style = ButtonStyle.DANGER).apply {
            onClick {
                modal.hide()
                scope.launch {
                    client.deleteTorrent(torrent.hash, deleteFiles = deleteFilesBox.value)
                }
            }
        })
        modal.show()
    }

    private fun stateIcon(torrent: Torrent): String {
        return when (torrent.state) {
            PAUSED_DL,
            QUEUED_UP -> "fas fa-pause"
            STALLED_DL -> "fas fa-binoculars"
            CHECKING_UP,
            STALLED_UP,
            FORCED_UP,
            ALLOCATING,
            CHECKING_DL,
            META_DL,
            FORCED_DL,
            CHECKING_RESUME_DATA,
            DOWNLOADING -> "fas fa-play"
            UPLOADING -> "fas fa-upload"
            MOVING -> "fas fa-file-import"
            MISSING_FILES,
            ERROR,
            UNKNOWN -> "fas fa-exclamation-triangle"
            PAUSED_UP -> "fas fa-check"
        }
    }
}
