/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.libs

import org.w3c.dom.HTMLVideoElement

// https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/types/video.js/index.d.ts

@JsModule("video.js")
@JsNonModule
@JsName("videojs")
external object VideoJs {
    fun default(
        elementId: String,
        options: VjsOptions = definedExternally,
    ): VjsPlayer

    fun default(
        element: HTMLVideoElement,
        options: VjsOptions = definedExternally,
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

    fun dispose()

    fun on(event: String, callback: () -> Unit)
}

@Suppress("UnsafeCastFromDynamic")
fun VjsOptions(configure: VjsOptions.() -> Unit): VjsOptions {
    val options = js("{}")
    configure(options)
    return options
}

@Suppress("UnsafeCastFromDynamic")
fun VjsControlBarOptions(configure: ControlBarOptions.() -> Unit): ControlBarOptions {
    val options = js("{}")
    configure(options)
    return options
}

external interface VjsOptions {
    var preload: String
    var src: String?
    var poster: String?
    var controls: Boolean?
    var autoplay: Any?
    var errorDisplay: Any?
    var controlBar: Any? // videojs.ControlBarOptions
    var aspectRatio: String?
    var bigPlayButton: Boolean?
    var textTrackSettings: Any? // videojs.TextTrackSettingsOptions
    var defaultVolume: Float?
    var fill: Boolean?
    var fluid: Boolean?
    var height: Float?
    var html5: Any?
    var inactivityTimeout: Float?
    var language: String?
    var languages: Map<String, Any>? // { [code: string]: videojs.LanguageTranslations }
    var liveui: Boolean?
    var muted: Boolean?
    var loop: Boolean?
    var nativeControlsForTouch: Boolean?
    var loadingSpinner: Boolean?
    var notSupportedMessage: String?
    var playbackRates: Array<Float>
    // plugins?: Partial<VideoJsPlayerPluginOptions> | undefined;
    var responsive: Boolean?
    var sourceOrder: Boolean?
    // sources?: videojs.Tech.SourceObject[] | undefined;
    var techOrder: Array<String>?
    // userActions?: videojs.UserActions | undefined;
    var width: Float?
}

external interface ControlBarOptions {
    var volumePanel: Boolean? // VolumePanelOptions | boolean | undefined;
    var playToggle: Boolean?
    var captionsButton: Boolean?
    var chaptersButton: Boolean?
    var subtitlesButton: Boolean?
    var remainingTimeDisplay: Boolean?
    var progressControl: Boolean? // ProgressControlOptions | boolean | undefined;
    var fullscreenToggle: Boolean?
    var playbackRateMenuButton: Boolean?
    var pictureInPictureToggle: Boolean?
    var currentTimeDisplay: Boolean?
    var timeDivider: Boolean?
    var durationDisplay: Boolean?
    var liveDisplay: Boolean?
    var seekToLive: Boolean?
    var customControlSpacer: Boolean?
    var descriptionsButton: Boolean?
    var subsCapsButton: Boolean?
    var audioTrackButton: Boolean?
}
