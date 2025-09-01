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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    val baseUrl: String = config.property("app.baseUrl").getString()
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

    val qbittorrent = config.property("app.qbittorrent").getAs<QbittorrentCredentials>()

    val oidc = config.property("app.oidc").getAs<Oidc>()

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

    @Serializable
    class Oidc(
        val enable: Boolean,
        val provider: Provider,
    ) {
        @Serializable
        class Provider(
            val name: String,
            @SerialName("authorize_url")
            val authorizeUrl: String,
            @SerialName("access_token_url")
            val accessTokenUrl: String,
            @SerialName("user_info_url")
            val userInfoUrl: String,
            @SerialName("client_id")
            val clientId: String,
            @SerialName("client_secret")
            val clientSecret: String,
            @SerialName("admin_group")
            val adminGroup: String,
            @SerialName("viewer_group")
            val viewerGroup: String,
            @SerialName("groups_field")
            val groupsField: String,
            @SerialName("username_fields")
            val usernameFields: List<String>,
            val scopes: List<String>,
        )
    }

    @Serializable
    data class QbittorrentCredentials(
        val url: String,
        val user: String,
        val password: String,
    )
}
