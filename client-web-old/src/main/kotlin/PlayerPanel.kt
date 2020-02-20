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
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.w3c.dom.HTMLVideoElement
import io.kvision.core.Position
import io.kvision.core.onEvent
import io.kvision.event.eventFlow
import io.kvision.html.customTag
import io.kvision.navbar.NavbarType
import io.kvision.navbar.nav
import io.kvision.navbar.navLink
import io.kvision.navbar.navbar
import io.kvision.panel.SimplePanel
import io.kvision.utils.perc

private const val MOUSE_IDLE_DELAY_MS = 800L

class PlayerPanel(
    private val mediaRefId: String,
    private val client: AnyStreamClient
) : SimplePanel(), CoroutineScope {

    override val coroutineContext = Default + SupervisorJob()

    private val controlsVisibleState =
        eventFlow("mousemove")
            .transformLatest {
                emit(true)
                delay(MOUSE_IDLE_DELAY_MS)
                emit(false)
            }
            .stateIn(this, WhileSubscribed(), true)

    init {
        val videoTag = customTag(
            elementName = "video",
            attributes = mapOf(
                "autoplay" to "",
                "controls" to "",
            )
        ) {
            position = Position.ABSOLUTE
            width = 100.perc
            height = 100.perc
        }
        navbar(type = NavbarType.FIXEDTOP) {
            visible = controlsVisibleState.value
            controlsVisibleState
                .onEach { controlsVisible ->
                    if (controlsVisible) fadeIn() else fadeOut()
                }
                .launchIn(this@PlayerPanel)

            nav(rightAlign = true) {
                navLink(label = "", icon = "fas fa-times") {
                    onClick { window.history.back() }
                }
            }
        }

        launch {
            val updateProgress = client.playbackSession(mediaRefId) { state ->
                videoTag.onEvent {
                    loadedmetadata = {
                        (videoTag.getElement() as HTMLVideoElement).currentTime = state.position.toDouble()
                    }
                }
                videoTag.setAttribute("src", "${window.location.protocol}//${window.location.host}/api/stream/$mediaRefId/direct")
                launch {
                    // TODO: Cache movie data so the poster can be set immediately
                    val movie = client.getMovie(state.mediaId)
                    videoTag.setAttribute("poster", "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces${movie.backdropPath}")
                }
            }
            merge(
                videoTag.eventFlow("seeked").debounce(1_000L),
                videoTag.eventFlow("timeupdate").sample(8_000L)
            ).onEach { (_, _) ->
                val currentTime = (videoTag.getElement() as HTMLVideoElement).currentTime
                updateProgress(currentTime.toLong())
            }.launchIn(this)
        }
    }
}
