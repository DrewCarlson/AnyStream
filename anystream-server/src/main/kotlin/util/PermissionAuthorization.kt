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
package anystream.util

import anystream.models.Permissions
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

typealias Permission = String

class AuthorizationException(override val message: String) : Exception(message)

class PermissionAuthorization {

    private var extractPermissions: (Principal) -> Set<String> = { emptySet() }

    fun extract(body: (Principal) -> Set<Permission>) {
        extractPermissions = body
    }

    fun interceptPipeline(
        pipeline: ApplicationCallPipeline,
        any: Set<Permission>? = null,
        all: Set<Permission>? = null,
        none: Set<Permission>? = null
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, AuthorizationPhase)

        pipeline.intercept(AuthorizationPhase) {
            val principal = call.authentication.principal<Principal>()
                ?: throw AuthorizationException("Missing principal")
            val activePermissions = extractPermissions(principal)

            if (activePermissions.contains(Permissions.GLOBAL)) {
                return@intercept
            }

            val denyReasons = mutableListOf<String>()
            all?.let {
                val missing = all - activePermissions
                if (missing.isNotEmpty()) {
                    denyReasons += "Principal $principal is missing required permission(s) ${missing.joinToString(" and ")}"
                }
            }
            any?.let {
                if (any.none { it in activePermissions }) {
                    denyReasons += "Principal $principal is missing all possible permission(s) ${any.joinToString(" or ")}"
                }
            }
            none?.let {
                if (none.any { it in activePermissions }) {
                    denyReasons += "Principal $principal has excluded permission(s) ${(none.intersect(activePermissions)).joinToString(" and ")}"
                }
            }
            if (denyReasons.isNotEmpty()) {
                val message = denyReasons.joinToString(". ")
                logger.warn("Authorization failed for ${call.request.path()}. $message")
                call.respond(Forbidden)
                finish()
            }
        }
    }


    companion object Feature :
        ApplicationFeature<ApplicationCallPipeline, PermissionAuthorization, PermissionAuthorization> {
        override val key = AttributeKey<PermissionAuthorization>("PermissionAuthorization")

        val AuthorizationPhase = PipelinePhase("PermissionAuthorization")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: PermissionAuthorization.() -> Unit
        ): PermissionAuthorization {
            return PermissionAuthorization().also(configure)
        }
    }
}

class AuthorizedRouteSelector(private val description: String) : RouteSelector() {

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Constant

    override fun toString(): String = "(authorize ${description})"
}

fun Route.withPermission(permission: Permission, build: Route.() -> Unit) =
    authorizedRoute(all = setOf(permission), build = build)

fun Route.withAllPermissions(vararg permissions: Permission, build: Route.() -> Unit) =
    authorizedRoute(all = permissions.toSet(), build = build)

fun Route.withAnyPermission(vararg permissions: Permission, build: Route.() -> Unit) =
    authorizedRoute(any = permissions.toSet(), build = build)

fun Route.withoutPermissions(vararg permissions: Permission, build: Route.() -> Unit) =
    authorizedRoute(none = permissions.toSet(), build = build)

private fun Route.authorizedRoute(
    any: Set<Permission>? = null,
    all: Set<Permission>? = null,
    none: Set<Permission>? = null,
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
