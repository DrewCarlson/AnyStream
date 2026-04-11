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
package anystream

import anystream.config.AnyStreamConfig
import anystream.db.converter.JooqConverterProvider
import anystream.di.DATA_PATH
import anystream.di.ServerScope
import anystream.di.TRANSCODE_PATH
import anystream.media.LibraryService
import anystream.routes.RoutingControllers
import anystream.service.user.OidcProviderService
import anystream.util.SqlSessionStorage
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jooq.DSLContext
import org.jooq.ExecutorProvider
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import qbittorrent.QBittorrentClient
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import javax.sql.DataSource

@DependencyGraph(ServerScope::class)
interface ServerGraph {
    val config: AnyStreamConfig
    val scope: CoroutineScope
    val http: HttpClient
    val libraryService: LibraryService
    val sessionStorage: SqlSessionStorage
    val routingControllers: RoutingControllers
    val oidcProviderService: OidcProviderService

    @Named(DATA_PATH)
    val dataPath: Path

    @Provides
    fun provideFileSystem(): FileSystem {
        return FileSystems.getDefault()
    }

    @Provides
    fun provideFfprobe(config: AnyStreamConfig): FFprobe {
        return FFprobe.atPath(config.paths.ffmpeg)
    }

    @Provides
    fun provideFfmpeg(config: AnyStreamConfig): FFmpeg {
        return FFmpeg.atPath(config.paths.ffmpeg)
    }

    @Named(DATA_PATH)
    @Provides
    fun provideDataPath(config: AnyStreamConfig): Path {
        return config.paths.data
    }

    @Named(TRANSCODE_PATH)
    @Provides
    fun provideTranscodePath(config: AnyStreamConfig): Path {
        return config.paths.transcode
    }

    @SingleIn(ServerScope::class)
    @Provides
    fun provideDataSource(config: AnyStreamConfig): DataSource {
        return SQLiteConnectionPoolDataSource().apply {
            url = config.databaseUrl
            this.config = SQLiteConfig().apply {
                enforceForeignKeys(true)
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
            }
        }
    }

    @SingleIn(ServerScope::class)
    @Provides
    fun provideDb(dataSource: DataSource): DSLContext {
        val dbConfig = DefaultConfiguration().apply {
            setDataSource(dataSource)
            setSQLDialect(SQLDialect.SQLITE)
            set(JooqConverterProvider())
            set(ExecutorProvider { Dispatchers.IO.asExecutor() })
        }
        return DSL.using(dbConfig)
    }

    @SingleIn(ServerScope::class)
    @Provides
    fun provideHttpClient(): HttpClient {
        return HttpClient {
            install(HttpCache) {
                // TODO: Add disk catching
                publicStorage(CacheStorage.Unlimited())
            }
            install(ContentNegotiation) {
                json()
            }
        }
    }

    @Provides
    fun provideTmdb3(config: AnyStreamConfig): Tmdb3 {
        return Tmdb3(config.tmdbApiKey)
    }

    @Provides
    fun provideQBittorrentClient(config: AnyStreamConfig): QBittorrentClient? {
        if (config.qbittorrent == null) {
            return null
        }
        return QBittorrentClient(
            baseUrl = config.qbittorrent.url,
            username = config.qbittorrent.user,
            password = config.qbittorrent.password,
        )
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides config: AnyStreamConfig,
            @Provides scope: CoroutineScope,
        ): ServerGraph
    }
}
