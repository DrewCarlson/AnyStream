/**
 * AnyStream
 * Copyright (C) 2021 Drew Carlson
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
package anystream.modules

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText


@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(StatusPages) {
        if (environment.config.property("ktor.deployment.development").getString().toBoolean()) {
            exception<Throwable> { error ->
                call.respondText(
                    status = HttpStatusCode.InternalServerError,
                    contentType = ContentType.Text.Plain,
                    text = error.stackTraceToString()
                )
            }
        }
    }
}
