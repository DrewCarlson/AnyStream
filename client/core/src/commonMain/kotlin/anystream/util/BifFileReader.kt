/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
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
package anystream.util

import dev.drewhamilton.poko.Poko
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe

private const val BIF_HEADER_WIDTH = 64

/**
 * BIF Spec: https://developer.roku.com/docs/developer-program/media-playback/trick-mode/bif-file-creation.md
 */
@Poko
class BifFileReader private constructor(
    val header: Header,
    private val indices: List<Index>,
    private val source: Source,
) : AutoCloseable {
    @Poko
    class Header(
        val version: Int,
        val imageCount: Int,
        val frameIntervalMs: Int,
    )

    @Poko
    class Index(
        val timestampMs: Int,
        val offset: Int,
    )

    @Poko
    class Frame(
        val index: Int,
        val timestampMs: Int,
        val bytes: ByteArray,
    )

    companion object {
        fun open(pathString: String): BifFileReader = open(Path(pathString))

        fun open(path: Path): BifFileReader {
            val fs = SystemFileSystem
            check(fs.exists(path)) { "File not found: $path" }

            return open(fs.source(path).buffered())
        }

        fun open(source: Source): BifFileReader {
            return source.peek().use { peekSource ->
                val header = readHeader(peekSource)
                val indices = mutableListOf<Index>()
                val applyTimestampMultiplier = header.frameIntervalMs > 0
                while (true) {
                    val timestamp = peekSource.readIntLe()
                    val offset = peekSource.readIntLe()
                    val adjustedTimestamp = if (applyTimestampMultiplier && timestamp > -1) {
                        timestamp * header.frameIntervalMs
                    } else {
                        timestamp
                    }
                    indices.add(Index(adjustedTimestamp, offset))
                    if (timestamp == -1) {
                        break
                    }
                }

                BifFileReader(header, indices, source)
            }
        }

        private fun readHeader(source: Source): Header {
            // Skip BIF file signature
            source.skip(8)
            // Consume BIF details
            val version = source.readIntLe()
            val imageCount = source.readIntLe()
            val frameIntervalMs = source.readIntLe()
            // Skip reserved bytes
            source.skip(BIF_HEADER_WIDTH - 20L)

            return Header(
                version = version,
                imageCount = imageCount,
                frameIntervalMs = frameIntervalMs,
            )
        }
    }

    fun readFrame(index: Int): Frame {
        require(index < indices.lastIndex) {
            "Invalid $index for image count ${header.imageCount}"
        }
        return source.peek().use { peekSource ->
            val targetFrame = indices[index]
            peekSource.skip(targetFrame.offset.toLong())

            val bytes = if (index == (indices.lastIndex - 1)) {
                val lastFrameSize = indices.last().offset - targetFrame.offset
                peekSource.readByteArray(lastFrameSize)
            } else {
                val nextOffset = indices[index + 1].offset
                val length = nextOffset - targetFrame.offset
                peekSource.readByteArray(length)
            }
            Frame(index, targetFrame.timestampMs, bytes)
        }
    }

    override fun close() {
        source.close()
    }
}