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

import io.ktor.util.cio.readChannel
import io.ktor.utils.io.read
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.io.path.fileSize

private const val BIF_VERSION = 0
private val BIF_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x42, 0x49, 0x46, 0x0D, 0x0A, 0x1A, 0x0A
)

private const val BIF_HEADER_WIDTH = 64
private const val BIF_INDEX_END = 1
private const val BIF_INDEX_WIDTH = 8

/**
 * BIF Spec: https://developer.roku.com/docs/developer-program/media-playback/trick-mode/bif-file-creation.md
 */
class BifFileBuilder(
    val path: Path,
    val frameCount: Int,
) {
    private val raf = RandomAccessFile(path.toString(), "rw")

    // The current frame index to write
    private var frameIndex = 0

    // The end offset of the frame index table (i.e. start offset of frame data)
    private var dataOffset = BIF_HEADER_WIDTH + (BIF_INDEX_WIDTH * (frameCount + BIF_INDEX_END))

    // Buffer to for writing little endian ints
    private val intBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

    init {
        writeHeader()
    }

    private fun writeHeader() {
        raf.setLength(dataOffset.toLong())
        raf.write(BIF_SIGNATURE)
        raf.writeIntLE(BIF_VERSION)
        raf.writeIntLE(frameCount)
        raf.writeIntLE(5_000)
        // Remaining header width is reserved, fill with zeros
        raf.write(ByteArray(BIF_HEADER_WIDTH - raf.filePointer.toInt()))
    }

    suspend fun appendFrame(path: Path) {
        // Move pointer to the data position offset and write file contents
        raf.seek(dataOffset.toLong())
        val readChannel = path.readChannel()
        readChannel.read { buffer ->
            raf.channel.write(buffer)
        }
        readChannel.cancel(null)

        // Move pointer to index table entry for this frame
        val indexTableEntryOffset = BIF_HEADER_WIDTH + (frameIndex * BIF_INDEX_WIDTH)
        raf.seek(indexTableEntryOffset.toLong())
        // Write entry contents
        raf.writeIntLE(frameIndex)
        raf.writeIntLE(dataOffset)
        // Update index and data offset position
        frameIndex += 1
        dataOffset += path.fileSize().toInt()
    }

    fun save(): Boolean {
        // Move pointer to final index table entry
        val indexTableEntryOffset = BIF_HEADER_WIDTH + (frameIndex * BIF_INDEX_WIDTH)
        raf.seek(indexTableEntryOffset.toLong())
        // End Index Table: empty index byte, frame data end offset
        raf.writeIntLE(-1)
        raf.writeIntLE(dataOffset)

        raf.close()

        return true
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        intBuffer.putInt(value)
        write(intBuffer.array())
        intBuffer.rewind()
    }
}

