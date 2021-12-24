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

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SinglePageApp(
    var defaultFile: String = "index.html",
    var staticFilePath: String = "",
    var ignoreBasePath: String = ""
) {

    companion object Plugin : ApplicationPlugin<Application, SinglePageApp, SinglePageApp> {
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

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val hasIgnoreBasePath = configuration.ignoreBasePath.isNotBlank()
                val handleRequestPath = !call.request.uri.startsWith(configuration.ignoreBasePath)
                if (!hasIgnoreBasePath || (hasIgnoreBasePath && handleRequestPath)) {
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
