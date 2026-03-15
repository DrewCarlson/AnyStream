package anystream

import anystream.data.MetadataDbQueries
import anystream.db.MediaLinkDao
import anystream.db.SessionsDao
import anystream.db.converter.JooqConverterProvider
import anystream.di.DATA_PATH
import anystream.di.ServerScope
import anystream.di.TRANSCODE_PATH
import anystream.jobs.GenerateVideoPreviewJob
import anystream.media.LibraryService
import anystream.metadata.MetadataService
import anystream.service.search.SearchService
import anystream.service.stream.StreamService
import anystream.service.user.UserService
import anystream.util.SqlSessionStorage
import app.moviebase.tmdb.Tmdb3
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.sessions.SessionStorage
import kotlinx.coroutines.CoroutineScope
import org.jooq.DSLContext
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
    val fs: FileSystem
    val ffprobe: Provider<FFprobe>
    val ffmpeg: Provider<FFmpeg>
    val db: DSLContext
    val http: HttpClient
    val tmdb3: Tmdb3
    val qbittorrentClient: QBittorrentClient
    val libraryService: LibraryService
    val streamService: StreamService
    val queries: MetadataDbQueries
    val metadataService: MetadataService
    val searchService: SearchService
    val mediaLinkDao: MediaLinkDao
    val sessionsDao: SessionsDao
    val userService: UserService
    val generateVideoPreviewJob: GenerateVideoPreviewJob
    val sessionStorage: SqlSessionStorage

    @Named(DATA_PATH)
    val dataPath: Path

    @Named(TRANSCODE_PATH)
    val transcodePath: Path

    @Provides
    fun provideFileSystem(): FileSystem {
        return FileSystems.getDefault()
    }

    @Provides
    fun provideFfprobe(config: AnyStreamConfig): FFprobe {
        return FFprobe.atPath(config.ffmpegPath)
    }

    @Provides
    fun provideFfmpeg(config: AnyStreamConfig): FFmpeg {
        return FFmpeg.atPath(config.ffmpegPath)
    }

    @Named(DATA_PATH)
    @Provides
    fun provideDataPath(config: AnyStreamConfig): Path {
        return config.dataPath
    }

    @Named(TRANSCODE_PATH)
    @Provides
    fun provideTranscodePath(config: AnyStreamConfig): Path {
        return config.transcodePath
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
    fun provideQBittorrentClient(config: AnyStreamConfig): QBittorrentClient {
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
