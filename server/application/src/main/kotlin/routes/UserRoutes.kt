/*
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
package anystream.routes

import anystream.config.AnyStreamConfig
import anystream.data.UserSession
import anystream.db.SessionsDao
import anystream.di.ServerScope
import anystream.json
import anystream.models.*
import anystream.models.api.*
import anystream.oauthRedirectUrls
import anystream.service.user.OidcProviderService
import anystream.service.user.UserService
import anystream.util.OidcRedirectUrl
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.URLBuilder
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.drewcarlson.ktor.permissions.withAnyPermission
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val PAIRING_SESSION_SECONDS = 60

@SingleIn(ServerScope::class)
@ContributesIntoSet(
    scope = ServerScope::class,
    binding = binding<RoutingController>(),
)
@Inject
class UserRoutes(
    private val userService: UserService,
    private val sessionsDao: SessionsDao,
    private val config: AnyStreamConfig,
    private val oidcProviderService: OidcProviderService,
) : RoutingController {
    override fun init(parent: Route) {
        parent.apply {
            route("/users") {
                post { createUser() }
                get("/auth-types") { getAuthProviderTypes() }

                authenticate {
                    withAnyPermission(Permission.Global) {
                        route("/invite") {
                            get { getInviteCodes() }
                            post { createInviteCode() }
                            delete("/{inviteCode}") { deleteInviteCode() }
                        }
                    }
                }

                if (config.oidc.enable && config.oidc.provider != null) {
                    authenticate(config.oidc.provider.name) {
                        route("/oidc") {
                            get("/login") {
                                // automatically redirect to auth provider
                            }

                            get("/callback") { getOidcCallback() }
                        }
                    }
                }

                route("/session") {
                    authenticate {
                        get { getSession() }
                    }

                    get("/adopt") { getAdoptSession() }

                    authenticate(optional = true) {
                        post { createSession() }
                    }

                    post("/paired") { createPairedSession() }

                    authenticate {
                        delete { deleteSession() }
                    }
                }

                authenticate {
                    withAnyPermission(Permission.Global) {
                        get { listUsers() }
                    }

                    route("/{user_id}") {
                        withAnyPermission(Permission.Global) {
                            get { getUser() }
                        }

                        put { updateUser() }

                        withAnyPermission(Permission.Global) {
                            delete { deleteUser() }
                        }
                    }
                }
            }

            webSocket("/ws/users/pair") {
                wsCreatePairSession()
            }
        }
    }

    suspend fun RoutingContext.createUser() {
        val body = runCatching { call.receiveNullable<CreateUserBody>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)
        val createSession = call.parameters["createSession"]?.toBoolean() ?: true

        val result = userService.createUser(body)
        if (createSession && result is CreateUserResponse.Success) {
            call.sessions.getOrSet {
                UserSession(
                    userId = checkNotNull(result.user.id),
                    permissions = result.permissions,
                )
            }
        }
        call.respond(result)
    }

    suspend fun RoutingContext.getInviteCodes() {
        val session = call.sessions.get<UserSession>()!!
        call.respond(
            userService.getInvites(
                session.userId.takeIf {
                    session.permissions.contains(Permission.Global)
                },
            ),
        )
    }

    suspend fun RoutingContext.createInviteCode() {
        val session = call.sessions.get<UserSession>()!!
        val permissions = runCatching { call.receiveNullable<Set<Permission>>() }
            .getOrNull() ?: setOf(Permission.ViewCollection)

        val inviteCode = userService.createInviteCode(session.userId, permissions)
        if (inviteCode == null) {
            call.respond(InternalServerError)
        } else {
            call.respond(inviteCode)
        }
    }

    suspend fun RoutingContext.deleteInviteCode() {
        val session = call.sessions.get<UserSession>()!!
        val inviteCode = call.parameters["inviteCode"]
            ?: return call.respond(BadRequest)

        // Only allow user's without global permission to delete
        // their own InviteCodes.
        val result = userService.deleteInvite(
            inviteCode = inviteCode,
            byUserId = session.userId.takeIf {
                session.permissions.contains(Permission.Global)
            },
        )
        call.respond(if (result) OK else NotFound)
    }

    suspend fun RoutingContext.getAuthProviderTypes() {
        val options = mutableListOf<AuthProviderType>(AuthProviderType.Internal)
        if (config.oidc.enable && config.oidc.provider != null) {
            options.add(
                AuthProviderType.Oidc(
                    providerName = config.oidc.provider.name,
                ),
            )
        }
        call.respond(options)
    }

    suspend fun RoutingContext.getOidcCallback() {
        if (config.oidc.provider == null) return
        val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()!!
        val body = oidcProviderService.getUserInfo(principal.accessToken)

        val username = config.oidc.provider.usernameFields
            .firstNotNullOf { body[it]?.jsonPrimitive?.contentOrNull }
            .lowercase()
        val groups = body[config.oidc.provider.groupsField]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

        var isNewUser = false
        val session = run {
            val existingUser = userService.getUserByName(username)
            if (existingUser == null) {
                val result = userService.createOidcUser(username, groups)
                isNewUser = true
                (result as? CreateUserResponse.Success)?.let {
                    UserSession(checkNotNull(it.user.id), it.permissions)
                }
            } else {
                val result = userService.createOidcSession(username)
                (result as? CreateSessionResponse.Success)?.let {
                    UserSession(checkNotNull(it.user.id), it.permissions)
                }
            }
        } ?: run {
            // TODO: Forward oidc session/user create error
            return call.respond(InternalServerError)
        }

        call.sessions.set(session)
        val customRedirect = oauthRedirectUrls.remove(principal.state)
        if (customRedirect == null) {
            call.respondRedirect("/")
        } else {
            val redirectUrl = URLBuilder(customRedirect)
                .apply { parameters["isNewUser"] = isNewUser.toString() }
            call.attributes.put(OidcRedirectUrl, redirectUrl)
            call.respond(HttpStatusCode.TemporaryRedirect)
        }
    }

    suspend fun RoutingContext.getSession() {
        val userSession = call.sessions.get<UserSession>()!!
        call.respond(
            CreateSessionResponse.Success(
                user = userService.getUser(userSession.userId)!!.toPublic(),
                permissions = userSession.permissions,
            ),
        )
    }

    suspend fun RoutingContext.getAdoptSession() {
        val sessionToken = call.request.queryParameters["as_user_session"]
            ?: return call.respond(Unauthorized)
        val redirectLocation = call.request.queryParameters["redirect"]

        val sessionData: UserSession = sessionsDao
            .find(sessionToken)
            ?.run(json::decodeFromString)
            ?: return call.respond(NotFound)

        call.sessions.set(sessionData)
        // TODO: do same thing as oidc
        call.respondRedirect(redirectLocation ?: "/")
    }

    suspend fun RoutingContext.createSession() {
        val body = runCatching { call.receiveNullable<CreateSessionBody>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)

        val result = userService.createSession(body, call.principal())
        if (result is CreateSessionResponse.Success) {
            call.sessions.set(
                UserSession(
                    checkNotNull(result.user.id),
                    result.permissions,
                ),
            )
        }
        if (result == null) {
            call.respond(Forbidden)
        } else {
            call.respond(result)
        }
    }

    suspend fun RoutingContext.createPairedSession() {
        val pairingCode = call.parameters["pairingCode"]!!
        val secret = call.parameters["secret"]!!

        val result = userService.verifyPairingSecret(pairingCode, secret)
        if (result is CreateSessionResponse.Success) {
            call.sessions.set(
                UserSession(
                    userId = checkNotNull(result.user.id),
                    permissions = result.permissions,
                ),
            )
        }

        if (result == null) {
            call.respond(Forbidden)
        } else {
            call.respond(result)
        }
    }

    suspend fun RoutingContext.deleteSession() {
        call.sessions.clear<UserSession>()
        call.respond(OK)
    }

    suspend fun RoutingContext.getUser() {
        val userId = call.parameters["user_id"]!!
        val user = userService.getUser(userId)?.toPublic()
        if (user == null) {
            call.respond(NotFound)
        } else {
            call.respond(user)
        }
    }

    suspend fun RoutingContext.updateUser() {
        val session = call.sessions.get<UserSession>()!!
        val userId = call.parameters["user_id"]!!
        val body = runCatching { call.receiveNullable<UpdateUserBody>() }
            .getOrNull() ?: return call.respond(UnprocessableEntity)

        if (userId == session.userId) {
            val success = userService.updateUser(userId, body)
            call.respond(if (success) OK else InternalServerError)
        } else {
            call.respond(Forbidden)
        }
    }

    suspend fun RoutingContext.deleteUser() {
        val userId = call.parameters["user_id"]!!
        val result = userService.deleteUser(userId)
        call.respond(if (result) OK else NotFound)
    }

    suspend fun RoutingContext.listUsers() {
        call.respond(userService.getUsers().map(User::toPublic))
    }

    suspend fun DefaultWebSocketServerSession.wsCreatePairSession() {
        val pairingCode = UUID.randomUUID().toString().lowercase()
        val startingJson = json.encodeToString<PairingMessage>(PairingMessage.Started(pairingCode))
        send(Frame.Text(startingJson))

        var tick = 0
        var finalMessage = userService.getPairingMessage(pairingCode, true)!!
        while (finalMessage == PairingMessage.Idle) {
            delay(1.seconds)
            tick++
            finalMessage = userService.getPairingMessage(pairingCode, false) ?: finalMessage
            if (tick >= PAIRING_SESSION_SECONDS) {
                send(Frame.Text(json.encodeToString<PairingMessage>(PairingMessage.Failed)))
                return close()
            }
        }

        val finalJson = json.encodeToString(finalMessage)
        send(Frame.Text(finalJson))
        close()
    }
}
