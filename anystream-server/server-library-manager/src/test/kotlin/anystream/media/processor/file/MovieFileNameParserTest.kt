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

import anystream.media.file.MovieFileNameParser
import anystream.media.file.ParsedFileNameResult
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class MovieFileNameParserTest {

    private val fileNameParser = MovieFileNameParser()

    @Test
    fun `should parse movie file`() {
        val fileName = "The Shawshank Redemption (1994).mkv"
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.MovieFile>(result)
        assertEquals("The Shawshank Redemption", result.name)
        assertEquals(1994, result.year)
    }

    @Test
    fun `should parse movie file without year`() {
        val fileName = "Inception.mkv"
        val result = fileNameParser.parseFileName(fileName)

        assertIs<ParsedFileNameResult.MovieFile>(result)
        assertEquals("Inception", result.name)
        assertNull(result.year)
    }
}
