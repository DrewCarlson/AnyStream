/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import io.ktor.server.config.*
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class AnyStreamConfig(
    config: ApplicationConfig,
    private val fs: FileSystem,
) {

    val disableWebClient: Boolean = config.property("app.disableWebClient").getString().toBoolean()
    val webClientPath: String? = config.propertyOrNull("app.webClientPath")?.getString()

    val dataPath: Path = config.propertyOrNull("app.dataPath")?.getString().let { path ->
        val pathOrDefault = path.orEmpty().ifBlank {
            Path(System.getProperty("user.home"), "anystream").absolutePathString()
        }
        fs.getPath(pathOrDefault).createDirectories()
    }
    private val configPath = dataPath.resolve("config").createDirectories()

    val databaseUrl: String = run {
        val databaseUrl = config.property("app.databaseUrl").getString()
            .ifBlank { configPath.resolve("anystream.db").absolutePathString() }
        "jdbc:sqlite:$databaseUrl"
    }

    val transcodePath: Path = config.property("app.transcodePath").getString()
        .run(fs::getPath)
        .createDirectories()

    val ffmpegPath: Path = config.propertyOrNull("app.ffmpegPath")?.getString().let { path ->
        val pathOrDefault = path
            ?.run(fs::getPath)
            ?.takeIf { it.exists() }
            ?: findInstalledFfmpeg()
        checkNotNull(pathOrDefault) {
            "Failed to find FFmpeg, please ensure `app.ffmpegPath` or `FFMPEG_PATH` is configured correctly."
        }
        pathOrDefault
    }
    val tmdbApiKey: String = config.property("app.tmdbApiKey").getString()
    val qbittorrentUrl: String = config.property("app.qbittorrentUrl").getString()
    val qbittorrentUser: String = config.property("app.qbittorrentUser").getString()
    val qbittorrentPass: String = config.property("app.qbittorrentPassword").getString()

    private fun findInstalledFfmpeg(): Path? {
        return listOf(
            "/usr/bin",
            "/usr/local/bin",
            "/usr/lib/jellyfin-ffmpeg",
            "C:\\Program Files\\ffmpeg\\bin",
        ).map(fs::getPath)
            .firstOrNull { path ->
                path.exists() && (path.resolve("ffmpeg").exists() || path.resolve("ffmpeg.exe").exists())
            }
    }
}
