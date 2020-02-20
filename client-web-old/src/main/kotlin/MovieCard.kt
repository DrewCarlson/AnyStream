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

import io.kvision.core.*
import io.kvision.html.*
import io.kvision.panel.*
import io.kvision.utils.auto
import io.kvision.utils.pt
import io.kvision.utils.px

class MovieCard(
    title: String,
    posterPath: String?,
    overview: String,
    releaseDate: String?,
    isAdded: Boolean,
    onPlayClicked: () -> Unit,
    onBodyClicked: () -> Unit
) : VPanel(classes = setOf("p-3")) {

    init {
        div(classes = setOf("card", "movie-card")) {
            onClick { onBodyClicked() }
            val overlayDiv = div(
                classes = setOf("rounded", "h-100", "w-100")
            ) {
                setStyle("cursor", "pointer")
                position = Position.ABSOLUTE
                zIndex = 1
                visible = false

                flexPanel(
                    justify = JustifyContent.SPACEBETWEEN,
                    alignItems = AlignItems.CENTER,
                    direction = FlexDirection.COLUMN,
                    classes = setOf("rounded", "border", "h-100", "w-100", "p-3")
                ) {
                    zIndex = 3
                    position = Position.ABSOLUTE
                    addCssClass("border-white")

                    options(alignSelf = AlignItems.FLEXEND) {
                        icon("fas fa-ellipsis-v") {
                            color = Color.name(Col.WHITE)
                        }
                    }

                    div {
                        icon("fas fa-play-circle") {
                            margin = auto
                            fontSize = 36.px
                            color = Color.name(Col.WHITE)
                        }
                        onClick { onPlayClicked() }
                    }

                    options(alignSelf = AlignItems.FLEXEND) {
                        icon(if (isAdded) "fas fa-check" else "fas fa-plus") {
                            color = Color.name(Col.WHITE)
                        }
                    }
                }

                div(classes = setOf("rounded", "h-100", "w-100")) {
                    background = Background(Color.name(Col.BLACK))
                    position = Position.ABSOLUTE
                    zIndex = 2
                    opacity = .45
                }
            }
            image(
                src = "https://image.tmdb.org/t/p/w200${posterPath}",
                classes = setOf("rounded")
            ) {
                setAttribute("loading", "lazy")
                height = 300.px
                width = 200.px
            }
            onEvent {
                mouseenter = { _ -> overlayDiv.fadeIn(150) }
                mouseleave = { _ -> overlayDiv.fadeOut(150) }
            }
        }

        vPanel(noWrappers = true, classes = setOf("py-2")) {
            width = 200.px
            link(label = title, url = "#") {
                fontSize = 12.pt
                color = Color.name(Col.WHITE)
                whiteSpace = WhiteSpace.NOWRAP
                overflow = Overflow.HIDDEN
                textOverflow = TextOverflow.ELLIPSIS
            }
            span(
                content = releaseDate?.split("-")?.firstOrNull() ?: "",
                classes = setOf("text-muted")
            ) {
                whiteSpace = WhiteSpace.NOWRAP
                overflow = Overflow.HIDDEN
                textOverflow = TextOverflow.ELLIPSIS
            }
        }
        /*div(className = "col-md-8") {
            vPanel(
                classes = setOf("card-body", "h-100"),
                noWrappers = true
            ) {
                h5(title, className = "card-title")
                p(overview, className = "card-text") {
                    overflow = Overflow.HIDDEN
                    setStyle("display", "-webkit-box")
                    setStyle("-webkit-line-clamp", "6")
                    setStyle("-webkit-box-orient", "vertical")
                }
                hPanel(justify = JustifyContent.SPACEBETWEEN) {
                    marginTop = auto
                    label(
                        content = if (releaseDate.isNullOrBlank()) "" else "Released $releaseDate",
                        className = "text-muted"
                    )

                    hPanel {
                        initActions()
                    }
                }
            }
        }*/
    }
}
