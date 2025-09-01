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

    val qbittorrent = QbittorrentCredentials(
        url = config.property("app.qbittorrent.url").getString(),
        user = config.property("app.qbittorrent.user").getString(),
        password = config.property("app.qbittorrent.password").getString(),
    )

    val oidc = Oidc(
        enable = config.property("app.oidc.enable").getString().toBoolean(),
        provider = Oidc.Provider(
            name = config.property("app.oidc.provider.name").getString(),
            authorizeUrl = config.property("app.oidc.provider.authorize_url").getString(),
            accessTokenUrl = config.property("app.oidc.provider.access_token_url").getString(),
            userInfoUrl = config.property("app.oidc.provider.user_info_url").getString(),
            clientId = config.property("app.oidc.provider.client_id").getString(),
            clientSecret = config.property("app.oidc.provider.client_secret").getString(),
            adminGroup = config.property("app.oidc.provider.admin_group").getString(),
            viewerGroup = config.property("app.oidc.provider.viewer_group").getString(),
            groupsField = config.property("app.oidc.provider.groups_field").getString(),
            usernameFields = config.property("app.oidc.provider.username_fields").getList(),
            scopes = config.property("app.oidc.provider.scopes").getList(),
        )
    )

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

    data class Oidc(
        val enable: Boolean,
        val provider: Provider,
    ) {
        data class Provider(
            val name: String,
            val authorizeUrl: String,
            val accessTokenUrl: String,
            val userInfoUrl: String,
            val clientId: String,
            val clientSecret: String,
            val adminGroup: String,
            val viewerGroup: String,
            val groupsField: String,
            val usernameFields: List<String>,
            val scopes: List<String>,
        )
    }

    data class QbittorrentCredentials(
        val url: String,
        val user: String,
        val password: String,
    )
}
