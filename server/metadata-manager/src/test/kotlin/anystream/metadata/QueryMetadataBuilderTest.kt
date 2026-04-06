/*
 * AnyStream
 * Copyright (C) 2026 AnyStream Maintainers
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
package anystream.metadata

import anystream.models.MediaKind
import anystream.models.api.QueryMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QueryMetadataBuilderTest :
    FunSpec({

        test("build with minimal fields sets mediaKind and defaults") {
            val result = QueryMetadataBuilder(MediaKind.MOVIE).build()

            result.mediaKind shouldBe MediaKind.MOVIE
            result.providerId shouldBe null
            result.query shouldBe null
            result.metadataId shouldBe null
            result.year shouldBe null
            result.extras shouldBe null
            result.cacheContent shouldBe false
            result.firstResultOnly shouldBe false
        }

        test("build with all fields set") {
            val extras = QueryMetadata.Extras.TvShowExtras(seasonNumber = 2, episodeNumber = 5)
            val result = QueryMetadataBuilder(MediaKind.TV)
                .apply {
                    providerId = "tmdb"
                    query = "Breaking Bad"
                    metadataId = "1396"
                    year = 2008
                    this.extras = extras
                    cacheContent = true
                    firstResultOnly = true
                }.build()

            result.mediaKind shouldBe MediaKind.TV
            result.providerId shouldBe "tmdb"
            result.query shouldBe "Breaking Bad"
            result.metadataId shouldBe "1396"
            result.year shouldBe 2008
            result.extras shouldBe extras
            result.cacheContent shouldBe true
            result.firstResultOnly shouldBe true
        }

        test("build preserves TV extras with season and episode") {
            val result = QueryMetadataBuilder(MediaKind.TV)
                .apply {
                    extras = QueryMetadata.Extras.TvShowExtras(
                        seasonNumber = 3,
                        episodeNumber = 10,
                    )
                }.build()

            val tvExtras = result.extras?.asTvShowExtras()
            tvExtras shouldBe QueryMetadata.Extras.TvShowExtras(
                seasonNumber = 3,
                episodeNumber = 10,
            )
        }

        test("build preserves TV extras with season only") {
            val result = QueryMetadataBuilder(MediaKind.TV)
                .apply {
                    extras = QueryMetadata.Extras.TvShowExtras(
                        seasonNumber = 1,
                    )
                }.build()

            val tvExtras = result.extras?.asTvShowExtras()
            tvExtras?.seasonNumber shouldBe 1
            tvExtras?.episodeNumber shouldBe null
        }
    })
