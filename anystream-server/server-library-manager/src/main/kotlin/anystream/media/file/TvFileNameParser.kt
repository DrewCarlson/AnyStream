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

class TvFileNameParser : FileNameParser {

    private val yearRegex = "\\s\\((\\d{4})\\)\$".toRegex()
    private val seasonFolderRegex = "^(?:[S|s]eason )?(\\d{1,2})\$".toRegex()
    private val episodeIndexRegex = "\\b[sS](\\d{1,2})[eE](\\d{1,3})\\b".toRegex()
    private val episodeNumberRegex = "() - (\\d{1,3}) - ".toRegex() // NOTE: Keep empty group
    private val simpleEpisodeIndexRegex = "\\b(\\d{1,3})[xX](\\d{1,3})\\b".toRegex()

    override fun parseFileName(fileName: String): ParsedFileNameResult {
        if (seasonFolderRegex.containsMatchIn(fileName)) {
            val matchResult = checkNotNull(seasonFolderRegex.find(fileName))
            val seasonNumber = matchResult.groupValues[1].toInt()
            return ParsedFileNameResult.Tv.SeasonFolder(seasonNumber)
        }

        val matchResult = when {
            episodeIndexRegex.containsMatchIn(fileName) -> checkNotNull(episodeIndexRegex.find(fileName))
            episodeNumberRegex.containsMatchIn(fileName) -> checkNotNull(episodeNumberRegex.find(fileName))
            simpleEpisodeIndexRegex.containsMatchIn(fileName) -> checkNotNull(simpleEpisodeIndexRegex.find(fileName))
            else -> {
                val match = yearRegex.find(fileName)
                val year = match?.groupValues?.lastOrNull()?.toIntOrNull()

                val name = if (year == null) {
                    fileName
                } else {
                    fileName.replace(yearRegex, "")
                }.trim()
                return ParsedFileNameResult.Tv.ShowFolder(
                    name = name,
                    year = year,
                )
            }
        }

        val (seasonNumber, episodeNumber) = matchResult.groupValues.drop(1)
        return ParsedFileNameResult.Tv.EpisodeFile(seasonNumber.toIntOrNull(), episodeNumber.toInt())
    }
}
