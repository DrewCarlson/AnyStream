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
package anystream.util

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SinglePageApp(
    var defaultFile: String = "index.html",
    var staticFilePath: String = "",
    var ignoreBasePath: String = ""
) {

    companion object Feature : ApplicationFeature<Application, SinglePageApp, SinglePageApp> {
        override val key = AttributeKey<SinglePageApp>("SinglePageApp")

        override fun install(pipeline: Application, configure: SinglePageApp.() -> Unit): SinglePageApp {
            val configuration = SinglePageApp().apply(configure)
            val defaultFile = File(configuration.staticFilePath, configuration.defaultFile)
            val staticFileMap = ConcurrentHashMap<String, File>()

            pipeline.routing {
                static("/") {
                    staticRootFolder = File(configuration.staticFilePath)
                    files("./")
                    default(configuration.defaultFile)
                }

                get("/*") {
                    call.respondRedirect("/")
                }
            }

            pipeline.intercept(ApplicationCallPipeline.Features) {
                if (!call.request.uri.startsWith(configuration.ignoreBasePath)) {
                    val path = call.request.uri.split("/")
                    if (path.last().contains(".")) {
                        try {
                            val file = staticFileMap.getOrPut(path.last()) {
                                val urlPathString = path.subList(1, path.lastIndex)
                                    .fold(configuration.staticFilePath) { out, part -> "$out/$part" }
                                File(urlPathString, path.last())
                                    .apply { check(exists()) }
                            }
                            call.respondFile(file)
                            return@intercept finish()
                        } catch (e: IllegalStateException) {
                            // No local resource, fall to other handlers
                        }
                    } else {
                        call.respondFile(defaultFile)
                        return@intercept finish()
                    }
                }
            }

            return configuration
        }
    }
}
