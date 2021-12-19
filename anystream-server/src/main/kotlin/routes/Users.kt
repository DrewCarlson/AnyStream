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
package anystream.routes

import anystream.data.UserSession
import anystream.json
import anystream.models.*
import anystream.models.api.*
import anystream.service.user.UserService
import org.drewcarlson.ktor.permissions.withAnyPermission
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.cio.websocket.*
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import org.bson.types.ObjectId
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val PAIRING_SESSION_SECONDS = 60

fun Route.addUserRoutes(userService: UserService) {
    route("/users") {
        post {
            val body = call.receiveOrNull<CreateUserBody>()
                ?: return@post call.respond(UnprocessableEntity)
            val createSession = call.parameters["createSession"]?.toBoolean() ?: true

            val result = userService.createUser(body)
            if (result == null) {
                call.respond(Forbidden)
            } else {
                val success = result.success
                if (createSession && success != null) {
                    call.sessions.getOrSet {
                        UserSession(
                            userId = success.user.id,
                            permissions = success.permissions,
                        )
                    }
                }
                call.respond(result)
            }
        }

        authenticate {
            withAnyPermission(Permissions.GLOBAL) {
                route("/invite") {
                    get {
                        val session = call.sessions.get<UserSession>()!!
                        call.respond(
                            userService.getInvites(
                                session.userId.takeIf {
                                    session.permissions.contains(Permissions.GLOBAL)
                                }
                            )
                        )
                    }

                    post {
                        val session = call.sessions.get<UserSession>()!!
                        val permissions = call.receiveOrNull()
                            ?: setOf(Permissions.VIEW_COLLECTION)

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
                                session.permissions.contains(Permissions.GLOBAL)
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
                    val body = call.receiveOrNull<CreateSessionBody>()
                        ?: return@post call.respond(UnprocessableEntity)

                    val result = userService.createSession(body, call.principal())
                    val success = result?.success
                    if (success is CreateSessionSuccess) {
                        call.sessions.set(UserSession(success.user.id, success.permissions))
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
                val success = result?.success
                if (success is CreateSessionSuccess) {
                    call.sessions.set(
                        UserSession(
                            userId = success.user.id,
                            permissions = success.permissions,
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
            withAnyPermission(Permissions.GLOBAL) {
                get {
                    call.respond(userService.getUsers())
                }
            }

            route("/{user_id}") {
                withAnyPermission(Permissions.GLOBAL) {
                    get {
                        val userId = call.parameters["user_id"]!!
                        call.respond(userService.getUser(userId) ?: NotFound)
                    }
                }

                put {
                    val session = call.sessions.get<UserSession>()!!
                    val userId = call.parameters["user_id"]!!
                    val body = call.receiveOrNull<UpdateUserBody>()
                        ?: return@put call.respond(UnprocessableEntity)

                    if (userId == session.userId) {
                        val success = userService.updateUser(userId, body)
                        call.respond(if (success) OK else InternalServerError)
                    } else {
                        call.respond(Forbidden)
                    }
                }

                withAnyPermission(Permissions.GLOBAL) {
                    delete {
                        val userId = call.parameters["user_id"]!!

                        if (ObjectId.isValid(userId)) {
                            val result = userService.deleteUser(userId)
                            call.respond(if (result) OK else NotFound)
                        } else {
                            call.respond(BadRequest)
                        }
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

