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
package anystream.service.stream

import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult
import com.github.kokorin.jaffree.process.CommandSender
import kotlinx.coroutines.future.await

internal fun FFmpeg.buildCommandString(): String {
    val buildArguments = FFmpeg::class.java.getDeclaredMethod("buildArguments")
        .apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    return (buildArguments(this) as List<String>).joinToString(
        separator = " ",
        prefix = "ffmpeg ",
    ) { if (it.contains(' ')) "\"$it\"" else it }
}

suspend fun FFmpeg.executeAwait(): FFmpegResult {
    return executeAsync().toCompletableFuture().await()
}

suspend fun FFmpeg.executeAwait(commandSender: CommandSender): FFmpegResult {
    return executeAsync(commandSender).toCompletableFuture().await()
}
