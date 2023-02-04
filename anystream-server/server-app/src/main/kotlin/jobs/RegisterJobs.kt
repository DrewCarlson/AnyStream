/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
