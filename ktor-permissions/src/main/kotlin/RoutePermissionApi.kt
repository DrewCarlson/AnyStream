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
package drewcarlson.ktor.permissions

import io.ktor.application.*
import io.ktor.routing.*

/**
 * Routes defined in [build] will only be invoked when the
 * [io.ktor.auth.Principal] contains [permission].
 *
 * If a global permission is defined, [io.ktor.auth.Principal]s with
 * that permission will ignore the requirement.
 */
fun <P : Any> Route.withPermission(permission: P, build: Route.() -> Unit) =
    authorizedRoute(all = setOf(permission), build = build)

/**
 * Routes defined in [build] will only be invoked when the
 * [io.ktor.auth.Principal] contains all of [permissions].
 *
 * If a global permission is defined, [io.ktor.auth.Principal]s with
 * that permission will ignore the requirement.
 */
fun <P : Any> Route.withAllPermissions(vararg permissions: P, build: Route.() -> Unit) =
    authorizedRoute(all = permissions.toSet(), build = build)

/**
 * Routes defined in [build] will only be invoked when the
 * [io.ktor.auth.Principal] contains any of [permissions].
 *
 * If a global permission is defined, [io.ktor.auth.Principal]s with
 * that permission will ignore the requirement.
 */
fun <P : Any> Route.withAnyPermission(vararg permissions: P, build: Route.() -> Unit) =
    authorizedRoute(any = permissions.toSet(), build = build)

/**
 * Routes defined in [build] will only be invoked when the
 * [io.ktor.auth.Principal] does not contain any of [permissions].
 *
 * If a global permission is defined, [io.ktor.auth.Principal]s with
 * that permission will ignore the requirement.
 */
fun <P : Any> Route.withoutPermissions(vararg permissions: P, build: Route.() -> Unit) =
    authorizedRoute(none = permissions.toSet(), build = build)

private fun <P : Any> Route.authorizedRoute(
    any: Set<P>? = null,
    all: Set<P>? = null,
    none: Set<P>? = null,
    build: Route.() -> Unit
): Route {
    val description = listOfNotNull(
        any?.let { "anyOf (${any.joinToString(" ")})" },
        all?.let { "allOf (${all.joinToString(" ")})" },
        none?.let { "noneOf (${none.joinToString(" ")})" }
    ).joinToString(",")
    return createChild(AuthorizedRouteSelector(description)).also { route ->
        application
            .feature(PermissionAuthorization)
            .interceptPipeline(route, any, all, none)
        route.build()
    }
}
