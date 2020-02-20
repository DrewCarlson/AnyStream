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
package anystream.frontend.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.soywiz.kmem.toInt
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Img


@Composable
fun MovieCard(
    title: String,
    posterPath: String?,
    overview: String,
    releaseDate: String?,
    isAdded: Boolean,
    //onPlayClicked: () -> Unit,
    //onBodyClicked: () -> Unit
) {
    val isOverlayVisible = remember { mutableStateOf(false) }
    Div(
        attrs = {
            classes("p-3")
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
            }
        }
    ) {
        Div(
            attrs = {
                classes("card", "movie-card")
                onMouseEnter { isOverlayVisible.value = true }
                onMouseLeave { isOverlayVisible.value = false }
            }
        ) {

            CardOverlay(isAdded, isOverlayVisible.value)

            Img(
                src = "https://image.tmdb.org/t/p/w200${posterPath}",
                attrs = {
                    classes("rounded")
                    attr("loading", "lazy")
                    style {
                        height(300.px)
                        width(200.px)
                    }
                }
            )
        }
    }
}

@Composable
private fun CardOverlay(
    isAdded: Boolean,
    isOverlayVisible: Boolean,
) {
    Div(
        attrs = {
            classes("rounded", "h-100", "w-100")
            style {
                position(Position.Absolute)
                property("cursor", "pointer")
                property("z-index", 1)
                opacity(isOverlayVisible.toInt())
                property("transition", "opacity 0.15s ease-in-out")
            }
        }
    ) {
        Div(
            attrs = {
                classes("rounded", "border", "h-100", "w-100", "p-3", "border-white")
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                    flexDirection(FlexDirection.Column)
                    property("z-index", 3)
                    position(Position.Absolute)
                }
            }
        ) {
            I(
                attrs = {
                    classes("bi-three-dots-vertical")
                    style {
                        display(DisplayStyle.Flex)
                        alignSelf(AlignSelf.FlexEnd)
                        color(Color.RGB(255, 255, 255))
                    }
                }
            )
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                    }
                }
            ) {
                I(
                    attrs = {
                        classes("bi-play-circle-fill")
                        style {
                            property("margin", auto)
                            fontSize(36.px)
                            color(Color.RGB(255, 255, 255))
                        }
                    }
                )
            }
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        alignSelf(AlignSelf.FlexEnd)
                    }
                }
            ) {
                I(
                    attrs = {
                        classes(if (isAdded) "bi-check-lg" else "bi-plus-lg")
                        style {
                            color(Color.RGB(255, 255, 255))
                        }
                    }
                )
            }
        }

        Div(
            attrs = {
                classes("rounded", "h-100", "w-100")
                style {
                    backgroundColor(Color.RGB(0, 0, 0))
                    position(Position.Absolute)
                    property("z-index", 2)
                    opacity(.7)
                }
            }
        )
    }
}