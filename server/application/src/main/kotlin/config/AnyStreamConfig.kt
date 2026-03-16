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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.Path

@Serializable
class AnyStreamConfig(
    val baseUrl: String? = null,
    val disableWebClient: Boolean = false,
    @Serializable(PathSerializer::class)
    val webClientPath: Path? = null,
    @Serializable(DataPathSerializer::class)
    val dataPath: Path = Path("./anystream"),
    @Serializable(PathSerializer::class)
    val transcodePath: Path = Path("/tmp"),
    @Serializable(FfmpegPathSerializer::class)
    val ffmpegPath: Path = FfmpegPathSerializer.findInstalledFfmpeg() ?: Path("./ffmpeg"),
    @Serializable(DatabaseUrlSerializer::class)
    val databaseUrl: String = "jdbc:sqlite:$dataPath/anystream.db",
    val tmdbApiKey: String = "",
    val qbittorrent: QbittorrentCredentials? = null,
    val oidc: Oidc = Oidc(enable = false),
    val libraries: LibrariesConfig = LibrariesConfig(),
) {
    @Serializable
    class Oidc(
        val enable: Boolean = false,
        val provider: Provider? = null,
    ) {
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

    @Serializable
    class QbittorrentCredentials(
        val url: String,
        val user: String,
        val password: String,
    )

    @Serializable
    class LibrariesConfig(
        val tv: LibraryConfig = LibraryConfig(),
        val movies: LibraryConfig = LibraryConfig(),
        val music: LibraryConfig = LibraryConfig(),
    )

    @Serializable
    class LibraryConfig(
        val directories: List<String> = emptyList(),
    )
}
