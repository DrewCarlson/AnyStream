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
package anystream.routes

import anystream.data.UserSession
import anystream.json
import anystream.models.*
import anystream.models.api.*
import anystream.service.user.UserService
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import org.drewcarlson.ktor.permissions.withAnyPermission
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val PAIRING_SESSION_SECONDS = 60

fun Route.addUserRoutes(userService: UserService) {
    route("/users") {
        post {
            val body = runCatching { call.receiveNullable<CreateUserBody>() }
                .getOrNull() ?: return@post call.respond(UnprocessableEntity)
            val createSession = call.parameters["createSession"]?.toBoolean() ?: true

            val result = userService.createUser(body)
            if (result == null) {
                call.respond(Forbidden)
            } else {
                if (createSession && result is CreateUserResponse.Success) {
                    call.sessions.getOrSet {
                        UserSession(
                            userId = result.user.id,
                            permissions = result.permissions,
                        )
                    }
                }
                call.respond(result)
            }
        }

        authenticate {
            withAnyPermission(Permission.Global) {
                route("/invite") {
                    get {
                        val session = call.sessions.get<UserSession>()!!
                        call.respond(
                            userService.getInvites(
                                session.userId.takeIf {
                                    session.permissions.contains(Permission.Global)
                                }
                            )
                        )
                    }

                    post {
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

                    delete("/{inviteCode}") {
                        val session = call.sessions.get<UserSession>()!!
                        val inviteCode = call.parameters["inviteCode"]
                            ?: return@delete call.respond(BadRequest)

                        // Only allow user's without global permission to delete
                        // their own InviteCodes.
                        val result = userService.deleteInvite(
                            inviteCode = inviteCode,
                            byUserId = session.userId.takeIf {
                                session.permissions.contains(Permission.Global)
                            }
                        )
                        call.respond(if (result) NotFound else OK)
                    }
                }
            }
        }

        route("/session") {
            authenticate(optional = true) {
                post {
                    val body = runCatching { call.receiveNullable<CreateSessionBody>() }
                        .getOrNull() ?: return@post call.respond(UnprocessableEntity)

                    val result = userService.createSession(body, call.principal())
                    if (result is CreateSessionResponse.Success) {
                        call.sessions.set(UserSession(result.user.id, result.permissions))
                    }
                    if (result == null) {
                        call.respond(Forbidden)
                    } else {
                        call.respond(result)
                    }
                }
            }

            post("/paired") {
                val pairingCode = call.parameters["pairingCode"]!!
                val secret = call.parameters["secret"]!!

                val result = userService.verifyPairingSecret(pairingCode, secret)
                if (result is CreateSessionResponse.Success) {
                    call.sessions.set(
                        UserSession(
                            userId = result.user.id,
                            permissions = result.permissions,
                        )
                    )
                }

                if (result == null) {
                    call.respond(Forbidden)
                } else {
                    call.respond(result)
                }
            }

            authenticate {
                delete {
                    call.sessions.clear<UserSession>()
                    call.respond(OK)
                }
            }
        }

        authenticate {
            withAnyPermission(Permission.Global) {
                get {
                    call.respond(userService.getUsers())
                }
            }

            route("/{user_id}") {
                withAnyPermission(Permission.Global) {
                    get {
                        val userId = call.parameters["user_id"]?.toIntOrNull()!!
                        call.respond(userService.getUser(userId) ?: NotFound)
                    }
                }

                put {
                    val session = call.sessions.get<UserSession>()!!
                    val userId = call.parameters["user_id"]?.toIntOrNull()!!
                    val body = call.receiveOrNull<UpdateUserBody>()
                        ?: return@put call.respond(UnprocessableEntity)

                    if (userId == session.userId) {
                        val success = userService.updateUser(userId, body)
                        call.respond(if (success) OK else InternalServerError)
                    } else {
                        call.respond(Forbidden)
                    }
                }

                withAnyPermission(Permission.Global) {
                    delete {
                        val userId = call.parameters["user_id"]?.toIntOrNull()!!
                        val result = userService.deleteUser(userId)
                        call.respond(if (result) OK else NotFound)
                    }
                }
            }
        }
    }
}

fun Route.addUserWsRoutes(userService: UserService) {
    webSocket("/ws/users/pair") {
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
                return@webSocket close()
            }
        }

        val finalJson = json.encodeToString(finalMessage)
        send(Frame.Text(finalJson))
        close()
    }
}
