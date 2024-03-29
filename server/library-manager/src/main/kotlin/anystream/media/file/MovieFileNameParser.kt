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
package anystream.media.file

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class MovieFileNameParser : FileNameParser {

    private val yearRegex = "\\s\\((\\d{4})\\)\$".toRegex()

    override fun parseFileName(path: Path): ParsedFileNameResult {
        val fileName = if (path.isDirectory()) {
            path.name
        } else {
            path.nameWithoutExtension
        }
        val match = yearRegex.find(fileName)
        val year = match?.groupValues?.lastOrNull()?.toIntOrNull()

        val name = if (year == null) {
            fileName
        } else {
            fileName.replace(yearRegex, "")
        }.trim()
        return ParsedFileNameResult.MovieFile(
            name = name,
            year = year,
        )
    }
}
