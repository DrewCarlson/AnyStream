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
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

private const val PRINCIPAL_OBJECT_MISSING = "Principal missing, is the route wrapped in `authenticate {  }`?"
private const val PRINCIPAL_PERMISSIONS_MISSING_ALL = "Principal '%s' is missing required permission(s) %s"
private const val PRINCIPAL_PERMISSIONS_MISSING_ANY = "Principal '%s' is missing all possible permission(s) %s"
private const val PRINCIPAL_PERMISSIONS_MATCHED_EXCLUDE = "Principal '%s' has excluded permission(s) %s"
private const val EXTRACT_PERMISSIONS_NOT_DEFINED = "Principal permission extractor must be defined, ex: `extract { (it as Session).permissions }`."

class PermissionAuthorization internal constructor(
    private val configuration: Configuration
) {

    class Configuration internal constructor() {
        internal var globalPermission: Any? = null
        internal var extractPermissions: (Principal) -> Set<Any> =
            { throw NotImplementedError(EXTRACT_PERMISSIONS_NOT_DEFINED) }

        /**
         * Define the Global permission to ignore route specific
         * permission requirements when attached to the [Principal].
         */
        fun <P : Any> global(permission: P) {
            globalPermission = permission
        }

        /**
         * Define how to extract the user's permission sent from
         * the [Principal] instance.
         *
         * Note: This should be a fast value mapping function,
         * do not read from an expensive data source.
         */
        fun <P : Any> extract(body: (Principal) -> Set<P>) {
            extractPermissions = body
        }
    }

    fun <P : Any> interceptPipeline(
        pipeline: ApplicationCallPipeline,
        any: Set<P>? = null,
        all: Set<P>? = null,
        none: Set<P>? = null
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, AuthorizationPhase)

        pipeline.intercept(AuthorizationPhase) {
            val principal = checkNotNull(call.authentication.principal()) {
                PRINCIPAL_OBJECT_MISSING
            }
            val activePermissions = configuration.extractPermissions(principal)
            configuration.globalPermission?.let {
                if (activePermissions.contains(it)) {
                    return@intercept
                }
            }
            val denyReasons = mutableListOf<String>()
            all?.let {
                val missing = all - activePermissions
                if (missing.isNotEmpty()) {
                    denyReasons += PRINCIPAL_PERMISSIONS_MISSING_ALL.format(principal, missing.joinToString(" and "))
                }
            }
            any?.let {
                if (any.none { it in activePermissions }) {
                    denyReasons += PRINCIPAL_PERMISSIONS_MISSING_ANY.format(principal, any.joinToString(" or "))
                }
            }
            none?.let {
                if (none.any { it in activePermissions }) {
                    val permissions = none.intersect(activePermissions).joinToString(" and ")
                    denyReasons += PRINCIPAL_PERMISSIONS_MATCHED_EXCLUDE.format(principal, permissions)
                }
            }
            if (denyReasons.isNotEmpty()) {
                val message = denyReasons.joinToString(". ")
                call.application.log.warn("Authorization failed for ${call.request.path()}. $message")
                call.respond(Forbidden)
                finish()
            }
        }
    }


    companion object Feature :
        ApplicationFeature<ApplicationCallPipeline, Configuration, PermissionAuthorization> {
        override val key = AttributeKey<PermissionAuthorization>("PermissionAuthorization")

        val AuthorizationPhase = PipelinePhase("PermissionAuthorization")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): PermissionAuthorization {
            return PermissionAuthorization(Configuration().also(configure))
        }
    }
}
