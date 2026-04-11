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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

object FfmpegPathSerializer : PathSerializer() {
    private val fs = FileSystems.getDefault()

    override fun deserialize(decoder: Decoder): Path {
        val path = decoder.decodeString()
        val pathOrDefault = Path(path)
            .takeIf { it.exists() }
            ?: findInstalledFfmpeg()
        checkNotNull(pathOrDefault) {
            "Failed to find FFmpeg, please ensure `app.ffmpeg_path` is configured correctly."
        }
        return pathOrDefault
    }

    fun findInstalledFfmpeg(): Path? {
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
}

object DataPathSerializer : PathSerializer() {
    override fun deserialize(decoder: Decoder): Path {
        return try {
            val value = decoder.decodeString()
            if (value.isBlank()) {
                throw SerializationException()
            }
            Path(decoder.decodeString())
        } catch (_: SerializationException) {
            Path(System.getProperty("user.home"), "anystream")
        }.toAbsolutePath().normalize()
    }
}

open class PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Path,
    ) {
        encoder.encodeString(value.normalize().absolutePathString())
    }

    override fun deserialize(decoder: Decoder): Path {
        return Path(decoder.decodeString()).toAbsolutePath().normalize()
    }
}

object DatabaseUrlSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = serialDescriptor<String>()

    override fun deserialize(decoder: Decoder): String {
        val value = Path(decoder.decodeString()).normalize().absolutePathString()
        return "jdbc:sqlite:$value"
    }

    override fun serialize(
        encoder: Encoder,
        value: String,
    ) {
        encoder.encodeString(value.removePrefix("jdbc:sqlite:"))
    }
}
