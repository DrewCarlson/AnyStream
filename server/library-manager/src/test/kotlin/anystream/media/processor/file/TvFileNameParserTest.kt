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
package anystream.media.processor.file

import anystream.media.file.ParsedFileNameResult
import anystream.media.file.TvFileNameParser
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.path.Path
import kotlin.test.assertIs

class TvFileNameParserTest {

    private val fileNameParser = TvFileNameParser()

    @Test
    fun `should parse season folder with number`() {
        val fileName = Path("1")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.SeasonFolder>(result)
        assertEquals(1, result.seasonNumber)
    }

    @Test
    fun `should parse season folder with padded number`() {
        val fileName = Path("01")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.SeasonFolder>(result)
        assertEquals(1, result.seasonNumber)
    }

    @Test
    fun `should parse season folder with Season and number`() {
        val fileName = Path("Season 1")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.SeasonFolder>(result)
        assertEquals(1, result.seasonNumber)
    }

    @Test
    fun `should parse season folder with Season and padded number`() {
        val fileName = Path("Season 01")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.SeasonFolder>(result)
        assertEquals(1, result.seasonNumber)
    }

    @Test
    fun `should parse simple episode file`() {
        val fileName = Path("Friends.S1E1.mkv")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.EpisodeFile>(result)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `should parse simple episode file with padded numbers`() {
        val fileName = Path("Friends.S01E01.mkv")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.EpisodeFile>(result)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `should parse show folder`() {
        val fileName = Path("Friends (1994)")
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.Tv.ShowFolder>(result)
        assertEquals("Friends", result.name)
        assertEquals(1994, result.year)
    }
}
