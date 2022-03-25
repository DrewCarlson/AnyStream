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
package anystream.util

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class SinglePageApp(
    var defaultFile: String = "index.html",
    var staticFilePath: String = "",
    var ignoreBasePath: String = "",
    var useResources: Boolean = false,
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
                            if (configuration.useResources) {
                                val urlPathString = path.subList(1, path.lastIndex)
                                    .plus(path.last())
                                    .joinToString(File.separator)
                                val content = call.resolveResource(urlPathString, configuration.staticFilePath)
                                if (content == null) {
                                    call.resolveResource(configuration.defaultFile, configuration.staticFilePath)?.let {
                                        call.respond(it)
                                    }
                                } else {
                                    call.respond(content)
                                }
                            } else {
                                val file = staticFileMap.getOrPut(path.last()) {
                                    val urlPathString = path.subList(1, path.lastIndex)
                                        .fold(configuration.staticFilePath) { out, part -> "$out/$part" }
                                    File(urlPathString, path.last()).apply { check(exists()) }
                                }
                                call.respondFile(file)
                            }
                            return@intercept finish()
                        } catch (e: IllegalStateException) {
                            // No local resource, fall to other handlers
                        }
                    } else {
                        if (configuration.useResources) {
                            call.resolveResource(configuration.defaultFile, configuration.staticFilePath)?.let {
                                call.respond(it)
                            }
                        } else {
                            call.respondFile(defaultFile)
                        }
                        return@intercept finish()
                    }
                }
            }

            return configuration
        }
    }
}

private fun ApplicationCall.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = application.environment.classLoader,
    mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }
): OutgoingContent? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = (resourcePackage.orEmpty().split('.', '/', '\\') + path.split('/', '\\'))
        .normalizePathComponents()
        .joinToString("/")

    for (url in classLoader.getResources(normalizedPath).asSequence()) {
        resourceClasspathResource(url, normalizedPath, mimeResolve)?.let { content ->
            return content
        }
    }

    return null
}

private fun resourceClasspathResource(url: URL, path: String, mimeResolve: (String) -> ContentType): OutgoingContent? {
    return when (url.protocol) {
        "file" -> {
            val file = File(url.path.decodeURLPart())
            if (file.isFile) LocalFileContent(file, mimeResolve(file.extension)) else null
        }
        "jar" -> {
            if (path.endsWith("/")) {
                null
            } else {
                val zipFile = findContainingJarFile(url.toString())
                val content = JarFileContent(zipFile, path, mimeResolve(url.path.extension()))
                if (content.isFile) content else null
            }
        }
        "jrt" -> {
            URIFileContent(url, mimeResolve(url.path.extension()))
        }
        else -> null
    }
}

private fun findContainingJarFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

        return File(url.substring(9, jarPathSeparator).decodeURLPart())
    }

    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}

private fun String.extension(): String {
    val indexOfName = lastIndexOf('/').takeIf { it != -1 } ?: lastIndexOf('\\').takeIf { it != -1 } ?: 0
    val indexOfDot = indexOf('.', indexOfName)
    return if (indexOfDot >= 0) substring(indexOfDot) else ""
}
