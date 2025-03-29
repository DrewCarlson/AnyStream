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
package anystream.media

import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile


fun FileSystem.createMovieDirectory(): Pair<Path, Map<Path, List<Path>>> {
    val moviesRoot = getPath("/movies").createDirectory()
    val fileMap = mapOf(
        "2 Fast 2 Furious (2003)" to listOf("mp4"),
        "Alice in Wonderland (1951)" to listOf("mkv", "en.srt"),
        "Conquest Of The Planet Of The Apes (1972)" to listOf("avi", "en.srt", "es.sub"),
        "Futureworld (1976)" to listOf("webm", "en.ass"),
        "It's a Mad, Mad, Mad, Mad World (1963)" to listOf("mov", "de.ssa"),
        "The Last Man on Earth (1964)" to listOf("wmv")
    )

    val createdFiles = fileMap.map { (name, extensions) ->
        val root = moviesRoot.resolve(name).createDirectory()
        val files = extensions.map { extension ->
            root.resolve("$name.$extension").createFile()
        }
        root to files
    }.toMap()

    return moviesRoot to createdFiles
}

fun FileSystem.createTvDirectory(): Pair<Path, Map<Path, Map<Path, List<Path>>>> {
    val tvRoot = getPath("/tv").createDirectory()
    val fileMap = mapOf(
        "Burn Notice" to mapOf(
            "Season 01" to listOf(
                "S01E01 - Burn Notice.mp4",
                "S01E02 - Identity.mp4",
            ),
            "Season 02" to listOf(
                "S02E01 - Breaking and Entering.mp4",
                "S02E02 - Turn and Burn.mp4"
            )
        ),
        "Doctor Who (2005)" to mapOf(
            "Season 01" to listOf(
                "S01E01 - Rose.mp4",
                "S01E01 - Rose.en.srt",
                "S01E02 - The End of the World.mp4",
                "S01E02 - The End of the World.en.srt",
            ),
            "Season 02" to listOf(
                "S02E01 - New Earth.mkv",
                "S02E02 - Tooth and Claw.mkv"
            )
        )
    )

    val createdFiles = fileMap.map { (showName, seasonDirectories) ->
        val showDirectory = tvRoot.resolve(showName).createDirectory()
        val showFiles = seasonDirectories.map { (seasonName, files) ->
            val seasonDirectory = showDirectory.resolve(seasonName).createDirectory()
            val seasonFiles = files.map { name ->
                seasonDirectory.resolve("$showName - $name").createFile()
            }
            seasonDirectory to seasonFiles
        }.toMap()
        showDirectory to showFiles
    }.toMap()

    return tvRoot to createdFiles
}