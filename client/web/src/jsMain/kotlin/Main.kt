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
package anystream

fun main() {
    require<Any>("./scss/anystream.scss")
    require<Any>("@popperjs/core/dist/umd/popper.min.js")
    require<Any>("bootstrap/dist/js/bootstrap.min.js")
    require<Any>("bootstrap-icons/font/bootstrap-icons.css")
    require<Any>("@fontsource/open-sans/index.css")
    require<Any>("video.js/dist/video-js.min.css")
    webApp()
}

external fun <T> require(module: String): T
