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
package anystream.util

import io.ktor.server.application.*
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.*
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin

inline fun <reified T : Any> Route.koinGet(): T = get()

inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.koinGet(): T =
    context.getKoin().get()
