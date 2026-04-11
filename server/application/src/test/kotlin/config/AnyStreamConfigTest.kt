/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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

import net.mamoe.yamlkt.Yaml
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the layered defaulting behavior of [AnyStreamConfig]:
 *   1. Constructor literal defaults when no env or YAML is present.
 *   2. Environment variable defaults applied through the constructor `env()` lookups.
 *   3. YAML values overriding both env and literal defaults via kotlinx-serialization.
 *   4. Round-trip encode/decode for the full config tree.
 *
 * Each test substitutes [envLookup] with a fake map so we never have to mutate the JVM process environment.
 */
class AnyStreamConfigTest {
    private val yaml = Yaml { encodeDefaultValues = true }

    private val fakeEnv = mutableMapOf<String, String>()

    @BeforeTest
    fun installFakeEnv() {
        fakeEnv.clear()
        envLookup = { fakeEnv[it] }
    }

    @AfterTest
    fun restoreEnvLookup() {
        envLookup = System::getenv
    }

    // ---- Literal defaults --------------------------------------------------------------------------------

    @Test
    fun `defaults are applied when no env or yaml is provided`() {
        val config = AnyStreamConfig()

        assertEquals(8888, config.port)
        assertEquals("0.0.0.0", config.host)
        assertNull(config.baseUrl)
        assertTrue(config.web.enable)
        assertNull(config.web.path)
        assertEquals(Path("./anystream"), config.paths.data)
        assertEquals(Path("/tmp"), config.paths.transcode)
        assertEquals("jdbc:sqlite:./anystream/anystream.db", config.databaseUrl)
        assertEquals("", config.tmdbApiKey)
        assertNull(config.qbittorrent)
        assertFalse(config.oidc.enable)
        assertNull(config.oidc.provider)
        assertTrue(
            config.libraries.tv.directories
                .isEmpty(),
        )
        assertTrue(
            config.libraries.movies.directories
                .isEmpty(),
        )
        assertTrue(
            config.libraries.music.directories
                .isEmpty(),
        )
    }

    // ---- Env-var defaults --------------------------------------------------------------------------------

    @Test
    fun `PORT env overrides default`() {
        fakeEnv["PORT"] = "9000"
        assertEquals(9000, AnyStreamConfig().port)
    }

    @Test
    fun `HOST env overrides default`() {
        fakeEnv["HOST"] = "127.0.0.1"
        assertEquals("127.0.0.1", AnyStreamConfig().host)
    }

    @Test
    fun `BASE_URL env overrides default null`() {
        fakeEnv["BASE_URL"] = "https://stream.example.com"
        assertEquals("https://stream.example.com", AnyStreamConfig().baseUrl)
    }

    @Test
    fun `WEB_PATH env populates web path`() {
        fakeEnv["WEB_PATH"] = "/srv/anystream/web"
        assertEquals(Path("/srv/anystream/web"), AnyStreamConfig().web.path)
    }

    @Test
    fun `DATA_PATH env populates paths data`() {
        fakeEnv["DATA_PATH"] = "/var/lib/anystream"
        assertEquals(Path("/var/lib/anystream"), AnyStreamConfig().paths.data)
    }

    @Test
    fun `TRANSCODE_PATH env populates paths transcode`() {
        fakeEnv["TRANSCODE_PATH"] = "/var/cache/anystream"
        assertEquals(Path("/var/cache/anystream"), AnyStreamConfig().paths.transcode)
    }

    @Test
    fun `FFMPEG_PATH env populates paths ffmpeg`() {
        fakeEnv["FFMPEG_PATH"] = "/opt/ffmpeg"
        assertEquals(Path("/opt/ffmpeg"), AnyStreamConfig().paths.ffmpeg)
    }

    @Test
    fun `DATABASE_URL env is wrapped with jdbc sqlite prefix`() {
        fakeEnv["DATABASE_URL"] = "/var/lib/anystream/anystream.db"
        assertEquals(
            "jdbc:sqlite:/var/lib/anystream/anystream.db",
            AnyStreamConfig().databaseUrl,
        )
    }

    @Test
    fun `database url default tracks data path env override`() {
        // databaseUrl default interpolates the data path; an env-derived data path should flow through to the
        // synthesized jdbc url when DATABASE_URL itself is unset.
        fakeEnv["DATA_PATH"] = "/var/lib/anystream"
        assertEquals(
            "jdbc:sqlite:/var/lib/anystream/anystream.db",
            AnyStreamConfig().databaseUrl,
        )
    }

    @Test
    fun `blank env var is treated as unset`() {
        fakeEnv["PORT"] = ""
        fakeEnv["HOST"] = "   "
        val config = AnyStreamConfig()
        assertEquals(8888, config.port)
        assertEquals("0.0.0.0", config.host)
    }

    @Test
    fun `non-numeric PORT env falls back to default`() {
        fakeEnv["PORT"] = "not-a-number"
        assertEquals(8888, AnyStreamConfig().port)
    }

    @Test
    fun `multiple env vars combine into a single config instance`() {
        fakeEnv["PORT"] = "9001"
        fakeEnv["HOST"] = "127.0.0.1"
        fakeEnv["BASE_URL"] = "https://stream.example.com"
        fakeEnv["DATA_PATH"] = "/var/lib/anystream"
        fakeEnv["FFMPEG_PATH"] = "/opt/ffmpeg"

        val config = AnyStreamConfig()

        assertEquals(9001, config.port)
        assertEquals("127.0.0.1", config.host)
        assertEquals("https://stream.example.com", config.baseUrl)
        assertEquals(Path("/var/lib/anystream"), config.paths.data)
        assertEquals(Path("/opt/ffmpeg"), config.paths.ffmpeg)
    }

    // ---- YAML decoding -----------------------------------------------------------------------------------

    @Test
    fun `empty yaml decodes to defaults`() {
        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), "{}")

        assertEquals(8888, config.port)
        assertEquals("0.0.0.0", config.host)
        assertNull(config.baseUrl)
    }

    @Test
    fun `yaml top level fields override defaults`() {
        val source =
            """
            port: 9000
            host: 127.0.0.1
            base_url: "https://stream.example.com"
            tmdb_api_key: deadbeef
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertEquals(9000, config.port)
        assertEquals("127.0.0.1", config.host)
        assertEquals("https://stream.example.com", config.baseUrl)
        assertEquals("deadbeef", config.tmdbApiKey)
    }

    @Test
    fun `yaml port overrides PORT env`() {
        fakeEnv["PORT"] = "9000"
        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), "port: 7777")
        assertEquals(7777, config.port)
    }

    @Test
    fun `yaml omitted field falls back to env default`() {
        fakeEnv["PORT"] = "9000"
        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), "host: 127.0.0.1")
        assertEquals(9000, config.port)
        assertEquals("127.0.0.1", config.host)
    }

    @Test
    fun `yaml nested paths object decodes serial names`() {
        val source =
            """
            paths:
              data_path: /var/lib/anystream
              transcode_path: /var/cache/anystream
              ffmpeg_path: /opt/ffmpeg
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertEquals(Path("/var/lib/anystream").toAbsolutePath(), config.paths.data)
        assertEquals(Path("/var/cache/anystream").toAbsolutePath(), config.paths.transcode)
        assertEquals(Path("/opt/ffmpeg").toAbsolutePath(), config.paths.ffmpeg)
    }

    @Test
    fun `yaml partial paths object uses env for missing fields`() {
        fakeEnv["TRANSCODE_PATH"] = "/var/cache/anystream"
        val source =
            """
            paths:
              data_path: /var/lib/anystream
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertEquals(Path("/var/lib/anystream").toAbsolutePath(), config.paths.data)
        assertEquals(Path("/var/cache/anystream"), config.paths.transcode)
    }

    @Test
    fun `yaml web section decodes enable and path`() {
        val source =
            """
            web:
              enable: false
              path: /srv/anystream/web
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertFalse(config.web.enable)
        assertEquals(Path("/srv/anystream/web").toAbsolutePath(), config.web.path)
    }

    @Test
    fun `yaml qbittorrent decodes credentials`() {
        val source =
            """
            qbittorrent:
              url: "https://qbt.example.com"
              user: admin
              password: secret
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        val qbt = assertNotNull(config.qbittorrent)
        assertEquals("https://qbt.example.com", qbt.url)
        assertEquals("admin", qbt.user)
        assertEquals("secret", qbt.password)
    }

    @Test
    fun `yaml libraries decodes media kind directories`() {
        val source =
            """
            libraries:
              tv:
                directories:
                  - /media/TV
              movies:
                directories:
                  - /media/Movies
                  - /media/Movies-2
              music:
                directories: []
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertEquals(listOf("/media/TV"), config.libraries.tv.directories)
        assertEquals(listOf("/media/Movies", "/media/Movies-2"), config.libraries.movies.directories)
        assertTrue(
            config.libraries.music.directories
                .isEmpty(),
        )
    }

    @Test
    fun `yaml oidc decodes provider with snake_case fields`() {
        val source =
            """
            oidc:
              enable: true
              provider:
                name: my-provider
                endpoint: "https://auth.example.com"
                client_id: anystream
                client_secret: super-secret
                admin_group: admins
                viewer_group: viewers
                groups_field: roles
                username_fields:
                  - preferred_username
                  - email
                scopes:
                  - openid
                  - profile
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertTrue(config.oidc.enable)
        val provider = assertNotNull(config.oidc.provider)
        assertEquals("my-provider", provider.name)
        assertEquals("https://auth.example.com", provider.endpoint)
        assertEquals("anystream", provider.clientId)
        assertEquals("super-secret", provider.clientSecret)
        assertEquals("admins", provider.adminGroup)
        assertEquals("viewers", provider.viewerGroup)
        assertEquals("roles", provider.groupsField)
        assertEquals(listOf("preferred_username", "email"), provider.usernameFields)
        assertEquals(listOf("openid", "profile"), provider.scopes)
    }

    @Test
    fun `oidc provider falls back to documented defaults when optional fields are omitted`() {
        val source =
            """
            oidc:
              enable: true
              provider:
                name: minimal
                endpoint: "https://auth.example.com"
                client_id: id
                client_secret: secret
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)
        val provider = assertNotNull(config.oidc.provider)

        assertEquals("anystream-admin", provider.adminGroup)
        assertEquals("anystream-viewer", provider.viewerGroup)
        assertEquals("groups", provider.groupsField)
        assertEquals(listOf("preferred_username", "username"), provider.usernameFields)
        assertEquals(listOf("openid", "profile", "email", "groups"), provider.scopes)
    }

    @Test
    fun `database_url yaml value is wrapped with jdbc prefix on decode`() {
        // The DatabaseUrlSerializer adds the jdbc:sqlite: prefix during decode regardless of the YAML form,
        // so callers should always supply a bare filesystem path in YAML.
        val source =
            """
            database_url: /var/lib/anystream/anystream.db
            """.trimIndent()

        val config = yaml.decodeFromString(AnyStreamConfig.serializer(), source)

        assertEquals("jdbc:sqlite:/var/lib/anystream/anystream.db", config.databaseUrl)
    }

    // ---- Round-trip --------------------------------------------------------------------------------------

    @Test
    fun `encode then decode preserves a fully populated config`() {
        val original = AnyStreamConfig(
            port = 9001,
            host = "127.0.0.1",
            baseUrl = "https://stream.example.com",
            web = AnyStreamConfig.WebConfig(enable = false, path = Path("/srv/web")),
            paths = AnyStreamConfig.PathsConfig(
                data = Path("/var/lib/anystream"),
                transcode = Path("/var/cache/anystream"),
                ffmpeg = Path("/opt/ffmpeg"),
            ),
            tmdbApiKey = "deadbeef",
            qbittorrent = AnyStreamConfig.QbittorrentCredentials(
                url = "https://qbt.example.com",
                user = "admin",
                password = "secret",
            ),
            oidc = AnyStreamConfig.Oidc(
                enable = true,
                provider = AnyStreamConfig.Oidc.Provider(
                    name = "p",
                    endpoint = "https://auth.example.com",
                    clientId = "id",
                    clientSecret = "sec",
                ),
            ),
            libraries = AnyStreamConfig.LibrariesConfig(
                tv = AnyStreamConfig.LibraryConfig(listOf("/media/TV")),
                movies = AnyStreamConfig.LibraryConfig(listOf("/media/Movies")),
                music = AnyStreamConfig.LibraryConfig(listOf("/media/Music")),
            ),
        )

        val encoded = yaml.encodeToString(AnyStreamConfig.serializer(), original)
        val decoded = yaml.decodeFromString(AnyStreamConfig.serializer(), encoded)

        assertEquals(original.port, decoded.port)
        assertEquals(original.host, decoded.host)
        assertEquals(original.baseUrl, decoded.baseUrl)
        assertEquals(original.web.enable, decoded.web.enable)
        assertEquals(original.web.path?.toAbsolutePath(), decoded.web.path)
        assertEquals(original.paths.data.toAbsolutePath(), decoded.paths.data)
        assertEquals(original.paths.transcode.toAbsolutePath(), decoded.paths.transcode)
        assertEquals(original.paths.ffmpeg.toAbsolutePath(), decoded.paths.ffmpeg)
        assertEquals(original.tmdbApiKey, decoded.tmdbApiKey)
        assertEquals(original.qbittorrent?.url, decoded.qbittorrent?.url)
        assertEquals(original.qbittorrent?.user, decoded.qbittorrent?.user)
        assertEquals(original.qbittorrent?.password, decoded.qbittorrent?.password)
        assertEquals(original.oidc.enable, decoded.oidc.enable)
        assertEquals(original.oidc.provider?.name, decoded.oidc.provider?.name)
        assertEquals(original.oidc.provider?.endpoint, decoded.oidc.provider?.endpoint)
        assertEquals(original.oidc.provider?.clientId, decoded.oidc.provider?.clientId)
        assertEquals(original.oidc.provider?.clientSecret, decoded.oidc.provider?.clientSecret)
        assertEquals(original.libraries.tv.directories, decoded.libraries.tv.directories)
        assertEquals(original.libraries.movies.directories, decoded.libraries.movies.directories)
        assertEquals(original.libraries.music.directories, decoded.libraries.music.directories)
    }
}
