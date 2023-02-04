package anystream.jobs

import anystream.AnyStreamConfig
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import io.ktor.server.application.*
import kjob.core.KJob
import org.koin.ktor.ext.get

fun Application.registerJobs() {
    val config = get<AnyStreamConfig>()
    val kjob = get<KJob>()
    val ffmpeg = { get<FFmpeg>() }
    GenerateVideoPreviewJob.register(kjob, ffmpeg, config.dataPath, get())
    RefreshMetadataJob.register(kjob, get(), get())
}