/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
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
package anystream.media.processor

import anystream.media.file.FileNameParser
import anystream.models.MediaKind
import anystream.models.MediaLink
import anystream.models.api.MediaLinkMatchResult
import anystream.models.api.MetadataMatch

interface MediaFileProcessor {

    val mediaKinds: List<MediaKind>

    val fileNameParser: FileNameParser

    suspend fun findMetadataMatches(mediaLink: MediaLink, import: Boolean): MediaLinkMatchResult

    suspend fun importMetadataMatch(mediaLink: MediaLink, metadataMatch: MetadataMatch)

    suspend fun findMetadata(mediaLink: MediaLink, remoteId: String): MetadataMatch?

    /**
     * Calculates the [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
     * between two string.
     *
     * All params are converted to lowercase and filtered to letters and digits only.
     *
     * @param source The first string.
     * @param target The second string.
     * @return The Levenshtein distance between the two strings.
     */
    fun scoreString(source: String, target: String): Int {
        val s1 = source.lowercase().filter { it.isLetterOrDigit() }
        val s2 = target.lowercase().filter { it.isLetterOrDigit() }

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                when {
                    i == 0 -> dp[i][j] = j
                    j == 0 -> dp[i][j] = i
                    else -> dp[i][j] = minOf(
                        dp[i - 1][j - 1] + costOfSubstitution(s1[i - 1], s2[j - 1]),
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                    )
                }
            }
        }

        return dp[s1.length][s2.length]
    }
}

private fun costOfSubstitution(a: Char, b: Char): Int {
    return if (a == b) 0 else 1
}
