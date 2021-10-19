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
package anystream.frontend.libs

import org.w3c.dom.HTMLVideoElement


@JsModule("video.js")
@JsNonModule
@JsName("videojs")
external object VideoJs {
    fun default(
        elementId: String,
        options: VjsOptions,
    ): VjsPlayer

    fun default(
        element: HTMLVideoElement,
        options: VjsOptions,
    ): VjsPlayer
}

external class VjsPlayer {
    fun src(src: String?)
    fun src(): String?

    fun play()
    fun pause()

    fun paused(): Boolean

    fun controls(show: Boolean)
    fun controls(): Boolean

    fun show()
    fun hide()

    fun supportsFullscreen(): Boolean
    fun isFullscreen(): Boolean
    fun isFullscreen(isFullscreen: Boolean)

    fun isInPictureInPicture(): Boolean
    fun requestPictureInPicture()
    fun exitPictureInPicture()

    fun volume(): Float
    fun volume(volume: Float)

    fun muted(): Boolean
    fun muted(muted: Boolean)

    fun currentTime(): Double
    fun currentTime(currentTime: Double)

    fun duration(): Double
    fun duration(duration: Double)

    fun on(event: String, callback: () -> Unit)
}

class VjsOptions {
    var preload: String = "auto"
    var src: String? = null
    var poster: String? = null
    var controls: Boolean = false
    var autoplay: Any? = null
}

