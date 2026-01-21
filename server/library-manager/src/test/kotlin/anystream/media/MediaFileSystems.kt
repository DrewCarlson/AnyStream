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

// ============================================================================
// Edge Case Fixtures
// ============================================================================

/**
 * Creates a movie directory with edge case file names:
 * - Special characters in names
 * - Missing year in filename
 * - Year in title (e.g., "2001: A Space Odyssey")
 * - Very long filenames
 * - Unicode characters
 */
fun FileSystem.createEdgeCaseMovieDirectory(): Pair<Path, List<Path>> {
    val moviesRoot = getPath("/movies-edge").createDirectory()
    val files = listOf(
        // Missing year
        "The Matrix" to "The Matrix.mp4",
        // Year in title
        "2001 A Space Odyssey (1968)" to "2001 A Space Odyssey (1968).mkv",
        // Special characters
        "Se7en (1995)" to "Se7en (1995).mp4",
        "Who Framed Roger Rabbit (1988)" to "Who Framed Roger Rabbit (1988).avi",
        // Apostrophe in title
        "Schindler's List (1993)" to "Schindler's List (1993).mp4",
        // Colon in title (often problematic)
        "Star Wars Episode IV - A New Hope (1977)" to "Star Wars Episode IV - A New Hope (1977).mkv",
        // Very long filename
        "The Assassination of Jesse James by the Coward Robert Ford (2007)" to
            "The Assassination of Jesse James by the Coward Robert Ford (2007).mp4",
        // Numbers only title
        "1917 (2019)" to "1917 (2019).mp4",
        // Title with dots
        "Mr. Smith Goes to Washington (1939)" to "Mr. Smith Goes to Washington (1939).mp4",
        // Title with ampersand
        "Fast & Furious (2009)" to "Fast & Furious (2009).mp4",
    ).map { (dir, file) ->
        moviesRoot.resolve(dir).createDirectory().resolve(file).createFile()
    }
    return moviesRoot to files
}

/**
 * Creates a TV directory with edge case scenarios:
 * - Missing season folders (files directly in show folder)
 * - Out-of-order episode numbers
 * - Special episodes (Season 0/Specials)
 * - Alternative episode naming conventions
 * - Episodes with no season number in filename
 */
fun FileSystem.createEdgeCaseTvDirectory(): Pair<Path, List<Path>> {
    val tvRoot = getPath("/tv-edge").createDirectory()
    val files = mutableListOf<Path>()

    // Show with specials (Season 0)
    val showWithSpecials = tvRoot.resolve("Game of Thrones (2011)").createDirectory()
    val specialsSeason = showWithSpecials.resolve("Specials").createDirectory()
    files.add(specialsSeason.resolve("Game of Thrones (2011) - S00E01 - Inside the Episode.mp4").createFile())
    files.add(specialsSeason.resolve("Game of Thrones (2011) - S00E02 - Making of Season 1.mp4").createFile())
    val season1 = showWithSpecials.resolve("Season 01").createDirectory()
    files.add(season1.resolve("Game of Thrones (2011) - S01E01 - Winter Is Coming.mp4").createFile())
    files.add(season1.resolve("Game of Thrones (2011) - S01E02 - The Kingsroad.mp4").createFile())

    // Show with files directly in show folder (no season subfolders)
    val flatShow = tvRoot.resolve("Miniseries Example (2020)").createDirectory()
    files.add(flatShow.resolve("Miniseries Example - E01 - Part One.mp4").createFile())
    files.add(flatShow.resolve("Miniseries Example - E02 - Part Two.mp4").createFile())
    files.add(flatShow.resolve("Miniseries Example - E03 - Part Three.mp4").createFile())

    // Show with out-of-order episodes (gaps in numbering)
    val gappyShow = tvRoot.resolve("Cancelled Show (2018)").createDirectory()
    val gappySeason = gappyShow.resolve("Season 01").createDirectory()
    files.add(gappySeason.resolve("Cancelled Show (2018) - S01E01.mp4").createFile())
    files.add(gappySeason.resolve("Cancelled Show (2018) - S01E03.mp4").createFile()) // E02 missing
    files.add(gappySeason.resolve("Cancelled Show (2018) - S01E05.mp4").createFile()) // E04 missing

    // Show with alternative naming (1x01 format)
    val altNamingShow = tvRoot.resolve("Old Format Show").createDirectory()
    val altSeason = altNamingShow.resolve("Season 1").createDirectory()
    files.add(altSeason.resolve("Old Format Show - 1x01 - Pilot.mp4").createFile())
    files.add(altSeason.resolve("Old Format Show - 1x02 - Second Episode.mp4").createFile())

    // Show with absolute episode numbering
    val absoluteShow = tvRoot.resolve("Anime Show (2015)").createDirectory()
    files.add(absoluteShow.resolve("Anime Show (2015) - 001.mp4").createFile())
    files.add(absoluteShow.resolve("Anime Show (2015) - 002.mp4").createFile())
    files.add(absoluteShow.resolve("Anime Show (2015) - 003.mp4").createFile())

    // Show with double episodes
    val doubleEpShow = tvRoot.resolve("Double Episode Show (2010)").createDirectory()
    val doubleEpSeason = doubleEpShow.resolve("Season 01").createDirectory()
    files.add(doubleEpSeason.resolve("Double Episode Show (2010) - S01E01E02 - Pilot.mp4").createFile())
    files.add(doubleEpSeason.resolve("Double Episode Show (2010) - S01E03 - Regular Episode.mp4").createFile())

    return tvRoot to files
}

/**
 * Creates an empty directory for testing empty library handling.
 */
fun FileSystem.createEmptyDirectory(): Path {
    return getPath("/empty").createDirectory()
}

/**
 * Creates a directory with mixed media (movies and TV in same folder).
 */
fun FileSystem.createMixedMediaDirectory(): Pair<Path, List<Path>> {
    val mixedRoot = getPath("/mixed").createDirectory()
    val files = mutableListOf<Path>()

    // Movie file
    files.add(mixedRoot.resolve("The Matrix (1999).mp4").createFile())

    // TV episode
    files.add(mixedRoot.resolve("Breaking Bad - S01E01 - Pilot.mp4").createFile())

    // Movie in subfolder
    val movieFolder = mixedRoot.resolve("Inception (2010)").createDirectory()
    files.add(movieFolder.resolve("Inception (2010).mkv").createFile())

    // TV show in subfolder
    val tvFolder = mixedRoot.resolve("The Office").createDirectory()
    val tvSeason = tvFolder.resolve("Season 01").createDirectory()
    files.add(tvSeason.resolve("The Office - S01E01 - Pilot.mp4").createFile())

    return mixedRoot to files
}

/**
 * Creates a directory with duplicate/alternate versions of files.
 */
fun FileSystem.createDuplicateFilesDirectory(): Pair<Path, List<Path>> {
    val dupeRoot = getPath("/duplicates").createDirectory()
    val files = mutableListOf<Path>()

    val movieFolder = dupeRoot.resolve("Blade Runner (1982)").createDirectory()
    // Multiple versions of the same movie
    files.add(movieFolder.resolve("Blade Runner (1982) - Theatrical Cut.mp4").createFile())
    files.add(movieFolder.resolve("Blade Runner (1982) - Director's Cut.mp4").createFile())
    files.add(movieFolder.resolve("Blade Runner (1982) - Final Cut.mkv").createFile())

    val tvFolder = dupeRoot.resolve("Star Trek TNG").createDirectory()
    val tvSeason = tvFolder.resolve("Season 01").createDirectory()
    // Same episode in different qualities
    files.add(tvSeason.resolve("Star Trek TNG - S01E01 - Encounter at Farpoint.mp4").createFile())
    files.add(tvSeason.resolve("Star Trek TNG - S01E01 - Encounter at Farpoint.720p.mp4").createFile())
    files.add(tvSeason.resolve("Star Trek TNG - S01E01 - Encounter at Farpoint.1080p.mkv").createFile())

    return dupeRoot to files
}

/**
 * Creates a directory structure for testing deeply nested paths.
 */
fun FileSystem.createDeepNestedDirectory(): Pair<Path, Path> {
    var current = getPath("/deep").createDirectory()
    repeat(10) { i ->
        current = current.resolve("level_$i").createDirectory()
    }
    val file = current.resolve("deeply_nested_movie.mp4").createFile()
    return getPath("/deep") to file
}

/**
 * Creates a directory with various subtitle file configurations.
 */
fun FileSystem.createSubtitleVariationsDirectory(): Pair<Path, List<Path>> {
    val subRoot = getPath("/subtitles").createDirectory()
    val files = mutableListOf<Path>()

    val movieFolder = subRoot.resolve("International Film (2020)").createDirectory()
    files.add(movieFolder.resolve("International Film (2020).mp4").createFile())
    // Various subtitle formats and languages
    files.add(movieFolder.resolve("International Film (2020).en.srt").createFile())
    files.add(movieFolder.resolve("International Film (2020).es.srt").createFile())
    files.add(movieFolder.resolve("International Film (2020).fr.sub").createFile())
    files.add(movieFolder.resolve("International Film (2020).de.ass").createFile())
    files.add(movieFolder.resolve("International Film (2020).ja.ssa").createFile())
    files.add(movieFolder.resolve("International Film (2020).zh.srt").createFile())
    // Subtitle with full language name
    files.add(movieFolder.resolve("International Film (2020).english.srt").createFile())
    // Forced subtitles
    files.add(movieFolder.resolve("International Film (2020).en.forced.srt").createFile())
    // SDH subtitles
    files.add(movieFolder.resolve("International Film (2020).en.sdh.srt").createFile())

    return subRoot to files
}

/**
 * Creates a directory with poster/artwork files alongside media.
 */
fun FileSystem.createMediaWithArtworkDirectory(): Pair<Path, List<Path>> {
    val artRoot = getPath("/artwork").createDirectory()
    val files = mutableListOf<Path>()

    val movieFolder = artRoot.resolve("Artistic Movie (2021)").createDirectory()
    files.add(movieFolder.resolve("Artistic Movie (2021).mp4").createFile())
    files.add(movieFolder.resolve("poster.jpg").createFile())
    files.add(movieFolder.resolve("fanart.jpg").createFile())
    files.add(movieFolder.resolve("backdrop.png").createFile())
    files.add(movieFolder.resolve("folder.jpg").createFile())

    val tvFolder = artRoot.resolve("Art Show (2019)").createDirectory()
    files.add(tvFolder.resolve("poster.jpg").createFile())
    files.add(tvFolder.resolve("banner.jpg").createFile())
    val season1 = tvFolder.resolve("Season 01").createDirectory()
    files.add(season1.resolve("Art Show (2019) - S01E01.mp4").createFile())
    files.add(season1.resolve("season01-poster.jpg").createFile())

    return artRoot to files
}

/**
 * Creates a directory simulating a partial/incomplete download.
 */
fun FileSystem.createIncompleteDownloadDirectory(): Pair<Path, List<Path>> {
    val incompleteRoot = getPath("/incomplete").createDirectory()
    val files = mutableListOf<Path>()

    val movieFolder = incompleteRoot.resolve("Downloading Movie (2022)").createDirectory()
    files.add(movieFolder.resolve("Downloading Movie (2022).mp4.part").createFile())
    files.add(movieFolder.resolve("Downloading Movie (2022).mp4.!qB").createFile()) // qBittorrent temp

    return incompleteRoot to files
}

/**
 * Creates a directory with sample/trailer files that should be ignored.
 */
fun FileSystem.createSampleFilesDirectory(): Pair<Path, List<Path>> {
    val sampleRoot = getPath("/samples").createDirectory()
    val files = mutableListOf<Path>()

    val movieFolder = sampleRoot.resolve("Sample Movie (2023)").createDirectory()
    files.add(movieFolder.resolve("Sample Movie (2023).mp4").createFile())
    files.add(movieFolder.resolve("Sample Movie (2023) - Sample.mp4").createFile())
    files.add(movieFolder.resolve("sample.mp4").createFile())
    files.add(movieFolder.resolve("Sample Movie (2023) - Trailer.mp4").createFile())
    files.add(movieFolder.resolve("RARBG.txt").createFile()) // Common junk file

    val sampleSubfolder = movieFolder.resolve("Sample").createDirectory()
    files.add(sampleSubfolder.resolve("sample-video.mp4").createFile())

    return sampleRoot to files
}