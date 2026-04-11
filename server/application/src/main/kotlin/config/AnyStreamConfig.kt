/*
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
package anystream.config

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Lookup function for environment variables. Swappable so tests can inject a fake without mutating the
 * JVM-global process environment via reflection. Production callers should never reassign this.
 */
internal var envLookup: (String) -> String? = System::getenv

private fun env(name: String): String? = envLookup(name)?.takeIf { it.isNotBlank() }

@Poko
@Serializable
class AnyStreamConfig(
    val port: Int = env("PORT")?.toIntOrNull() ?: 8888,
    val host: String = env("HOST") ?: "0.0.0.0",
    @SerialName("base_url")
    val baseUrl: String? = env("BASE_URL"),
    val web: WebConfig = WebConfig(),
    val paths: PathsConfig = PathsConfig(),
    @Serializable(DatabaseUrlSerializer::class)
    @SerialName("database_url")
    val databaseUrl: String = env("DATABASE_URL")?.let { "jdbc:sqlite:$it" }
        ?: "jdbc:sqlite:${paths.data}/anystream.db",
    val qbittorrent: QbittorrentCredentials? = null,
    val oidc: Oidc = Oidc(enable = false),
    val libraries: LibrariesConfig = LibrariesConfig(),
) {
    @Poko
    @Serializable
    class Oidc(
        val enable: Boolean = false,
        val provider: Provider? = null,
    ) {
        @Poko
        @Serializable
        class Provider(
            val name: String,
            @SerialName("endpoint")
            val endpoint: String,
            @SerialName("client_id")
            val clientId: String,
            @SerialName("client_secret")
            val clientSecret: String,
            @SerialName("admin_group")
            val adminGroup: String = "anystream-admin",
            @SerialName("viewer_group")
            val viewerGroup: String = "anystream-viewer",
            @SerialName("groups_field")
            val groupsField: String = "groups",
            @SerialName("username_fields")
            val usernameFields: List<String> = listOf("preferred_username", "username"),
            val scopes: List<String> = listOf("openid", "profile", "email", "groups"),
        )
    }

    @Poko
    @Serializable
    class QbittorrentCredentials(
        val url: String,
        val user: String,
        val password: String,
    )

    @Poko
    @Serializable
    class LibrariesConfig(
        val tv: LibraryConfig = LibraryConfig(),
        val movies: LibraryConfig = LibraryConfig(),
        val music: LibraryConfig = LibraryConfig(),
    )

    @Poko
    @Serializable
    class LibraryConfig(
        val directories: List<String> = emptyList(),
    )

    @Poko
    @Serializable
    class WebConfig(
        val enable: Boolean = true,
        @Serializable(PathSerializer::class)
        val path: Path? = env("WEB_PATH")?.let(::Path),
    )

    @Poko
    @Serializable
    class PathsConfig(
        @Serializable(DataPathSerializer::class)
        @SerialName("data_path")
        val data: Path = env("DATA_PATH")?.let(::Path) ?: Path("./anystream"),
        @Serializable(PathSerializer::class)
        @SerialName("transcode_path")
        val transcode: Path = env("TRANSCODE_PATH")?.let(::Path) ?: Path("/tmp"),
        @Serializable(PathSerializer::class)
        @SerialName("ffmpeg_path")
        val ffmpeg: Path = env("FFMPEG_PATH")?.let(::Path)
            ?: findInstalledFfmpeg()
            ?: data.resolve("ffmpeg"),
    )
}

private fun findInstalledFfmpeg(fs: FileSystem = FileSystems.getDefault()): Path? {
    return listOf(
        "/usr/bin",
        "/usr/local/bin",
        "/usr/lib/jellyfin-ffmpeg",
        "C:\\Program Files\\ffmpeg\\bin",
    ).map(fs::getPath)
        .firstOrNull { path ->
            path.exists() && (
                path.resolve("ffmpeg").exists() ||
                    path.resolve("ffmpeg.exe").exists()
            )
        }
}
