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
import anystream.models.api.CreateSessionError.*
import anystream.models.api.CreateUserError.PasswordError
import anystream.models.api.CreateUserError.UsernameError
import anystream.util.logger
import anystream.util.withAnyPermission
import com.mongodb.MongoQueryException
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import io.ktor.http.cio.websocket.*
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import org.bouncycastle.crypto.generators.BCrypt
import org.bouncycastle.util.encoders.Hex
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration

private const val SALT_BYTES = 128 / 8
private const val BCRYPT_COST = 10
private const val PAIRING_SESSION_SECONDS = 60

private val pairingCodes = ConcurrentHashMap<String, PairingMessage>()

fun Route.addUserRoutes(mongodb: CoroutineDatabase) {
    val users = mongodb.getCollection<User>()
    val credentialsDb = mongodb.getCollection<UserCredentials>()
    val inviteCodeDb = mongodb.getCollection<InviteCode>()
    route("/users") {

        post {
            val body = call.receiveOrNull<CreateUserBody>()
                ?: return@post call.respond(UnprocessableEntity)
            val createSession = call.parameters["createSession"]?.toBoolean() ?: true

            val usernameError = when {
                body.username.isBlank() -> UsernameError.BLANK
                body.username.length < USERNAME_LENGTH_MIN -> UsernameError.TOO_SHORT
                body.username.length > USERNAME_LENGTH_MAX -> UsernameError.TOO_LONG
                else -> null
            }
            val passwordError = when {
                body.password.isBlank() -> PasswordError.BLANK
                body.password.length < PASSWORD_LENGTH_MIN -> PasswordError.TOO_SHORT
                body.password.length > PASSWORD_LENGTH_MAX -> PasswordError.TOO_LONG
                else -> null
            }

            if (usernameError != null || passwordError != null) {
                return@post call.respond(CreateUserResponse.error(usernameError, passwordError))
            }

            val username = body.username.lowercase()
            if (users.findOne(User::username eq username) != null) {
                return@post call.respond(
                    CreateUserResponse.error(
                        UsernameError.ALREADY_EXISTS,
                        null
                    )
                )
            }

            val id = body.inviteCode
            val inviteCode = if (id.isNullOrBlank()) {
                null
            } else {
                inviteCodeDb.findOneById(id)
            }

            if (inviteCode == null && users.countDocuments() > 0L) {
                return@post call.respond(Forbidden)
            }

            val permissions = inviteCode?.permissions ?: setOf(Permissions.GLOBAL)

            val user = User(
                id = ObjectId.get().toString(),
                username = username,
                displayName = body.username
            )

            val salt = Random.nextBytes(SALT_BYTES)
            val passwordBytes = body.password.toByteArray()
            val hashedPassword = BCrypt.generate(passwordBytes, salt, BCRYPT_COST)

            val credentials = UserCredentials(
                id = user.id,
                password = hashedPassword.toUtf8Hex(),
                salt = salt.toUtf8Hex(),
                permissions = permissions
            )
            try {
                // TODO: Ensure all or clear completed
                users.insertOne(user)
                credentialsDb.insertOne(credentials)
                if (inviteCode != null) {
                    inviteCodeDb.deleteOneById(inviteCode.value)
                }
                if (createSession) {
                    call.sessions.getOrSet {
                        UserSession(userId = user.id, credentials.permissions)
                    }
                }

                call.respond(CreateUserResponse.success(user, credentials.permissions))
            } catch (e: MongoQueryException) {
                logger.error("Failed to insert new user", e)
                call.respond(InternalServerError)
            }
        }

        authenticate {
            withAnyPermission(Permissions.GLOBAL) {
                route("/invite") {
                    get {
                        val session = call.sessions.get<UserSession>()!!

                        val codes = if (session.permissions.contains(Permissions.GLOBAL)) {
                            inviteCodeDb.find().toList()
                        } else {
                            inviteCodeDb
                                .find(InviteCode::createdByUserId eq session.userId)
                                .toList()
                        }
                        call.respond(codes)
                    }

                    post {
                        val session = call.sessions.get<UserSession>()!!
                        val permissions = call.receiveOrNull()
                            ?: setOf(Permissions.VIEW_COLLECTION)

                        val inviteCode = InviteCode(
                            value = Hex.toHexString(Random.nextBytes(InviteCode.SIZE)),
                            permissions = permissions,
                            createdByUserId = session.userId
                        )

                        inviteCodeDb.insertOne(inviteCode)
                        call.respond(inviteCode)
                    }

                    delete("/{invite_code}") {
                        val session = call.sessions.get<UserSession>()!!
                        val inviteCodeId = call.parameters["invite_code"]
                            ?: return@delete call.respond(BadRequest)

                        val result = if (session.permissions.contains(Permissions.GLOBAL)) {
                            inviteCodeDb.deleteOneById(inviteCodeId)
                        } else {
                            inviteCodeDb.deleteOne(
                                InviteCode::value eq inviteCodeId,
                                InviteCode::createdByUserId eq session.userId
                            )
                        }
                        call.respond(if (result.deletedCount == 0L) NotFound else OK)
                    }
                }
            }
        }

        route("/session") {
            authenticate(optional = true) {
                post {
                    val body = call.receiveOrNull<CreateSessionBody>()
                        ?: return@post call.respond(UnprocessableEntity)

                    if (body.username.run { isBlank() || length !in USERNAME_LENGTH_MIN..USERNAME_LENGTH_MAX }) {
                        return@post call.respond(CreateSessionResponse.error(USERNAME_INVALID))
                    }

                    val username = body.username.lowercase()
                    if (pairingCodes.containsKey(body.password)) {
                        val session = call.principal<UserSession>()
                            ?: return@post call.respond(CreateSessionResponse.error(USERNAME_INVALID))

                        val user = users
                            .findOne(User::username eq username)
                            ?: return@post call.respond(NotFound)
                        return@post if (session.userId == user.id) {
                            pairingCodes[body.password] = PairingMessage.Authorized(
                                secret = Random.nextBytes(28).toUtf8Hex(),
                                userId = session.userId
                            )
                            call.respond(CreateSessionResponse.success(user, session.permissions))
                        } else {
                            pairingCodes[body.password] = PairingMessage.Failed
                            call.respond(CreateSessionResponse.error(PASSWORD_INCORRECT))
                        }
                    }

                    if (body.password.run { isBlank() || length !in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX }) {
                        return@post call.respond(CreateSessionResponse.error(PASSWORD_INVALID))
                    }

                    val user = users.findOne(User::username eq username)
                        ?: return@post call.respond(CreateSessionResponse.error(USERNAME_NOT_FOUND))
                    val auth = credentialsDb.findOne(UserCredentials::id eq user.id)
                        ?: return@post call.respond(InternalServerError)

                    val saltBytes = auth.salt.utf8HexToBytes()
                    val passwordBytes = body.password.toByteArray()
                    val hashedPassword =
                        BCrypt.generate(passwordBytes, saltBytes, BCRYPT_COST).toUtf8Hex()

                    if (hashedPassword == auth.password) {
                        call.sessions.set(UserSession(user.id, auth.permissions))
                        call.respond(CreateSessionResponse.success(user, auth.permissions))
                    } else {
                        call.respond(CreateSessionResponse.error(PASSWORD_INCORRECT))
                    }
                }
            }

            post("/paired") {
                val pairingCode = call.parameters["pairingCode"]!!
                val secret = call.parameters["secret"]!!

                val pairingMessage = pairingCodes.remove(pairingCode)
                if (pairingMessage == null || pairingMessage !is PairingMessage.Authorized) {
                    return@post call.respond(NotFound)
                } else {
                    if (pairingMessage.secret == secret) {
                        val user = users.findOneById(pairingMessage.userId)!!
                        val userCredentials = credentialsDb.findOneById(pairingMessage.userId)!!
                        call.sessions.set(UserSession(user.id, userCredentials.permissions))
                        call.respond(
                            CreateSessionResponse.success(
                                user,
                                userCredentials.permissions
                            )
                        )
                    } else {
                        call.respond(Unauthorized)
                    }
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
                    call.respond(users.find().toList())
                }
            }

            route("/{user_id}") {
                withAnyPermission(Permissions.GLOBAL) {
                    get {
                        val userId = call.parameters["user_id"]!!
                        call.respond(users.findOneById(userId) ?: NotFound)
                    }
                }

                put {
                    val session = call.sessions.get<UserSession>()!!
                    val userId = call.parameters["user_id"]!!
                    val body = call.receiveOrNull<UpdateUserBody>()
                        ?: return@put call.respond(UnprocessableEntity)

                    if (userId == session.userId) {
                        val user = users.findOneById(userId)
                            ?: return@put call.respond(NotFound)
                        val updatedUser = user.copy(
                            displayName = body.displayName
                        )
                        users.updateOneById(userId, updatedUser)
                        // TODO: Update password
                        call.respond(OK)
                    } else {
                        call.respond(InternalServerError)
                    }
                }

                withAnyPermission(Permissions.GLOBAL) {
                    delete {
                        val userId = call.parameters["user_id"]!!

                        if (ObjectId.isValid(userId)) {
                            val result = users.deleteOneById(userId)
                            credentialsDb.deleteOneById(userId)
                            call.respond(if (result.deletedCount == 0L) NotFound else OK)
                        } else {
                            call.respond(BadRequest)
                        }
                    }
                }
            }
        }
    }
}

fun Route.addUserWsRoutes(
    mongodb: CoroutineDatabase,
) {
    webSocket("/ws/users/pair") {
        val pairingCode = UUID.randomUUID().toString().lowercase()
        val startingJson = json.encodeToString<PairingMessage>(PairingMessage.Started(pairingCode))
        send(Frame.Text(startingJson))

        pairingCodes[pairingCode] = PairingMessage.Idle

        var tick = 0
        var finalMessage: PairingMessage = PairingMessage.Idle
        while (finalMessage == PairingMessage.Idle) {
            delay(Duration.seconds(1))
            tick++
            finalMessage = pairingCodes[pairingCode] ?: return@webSocket close()
            println(pairingCodes.toList())
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

private fun String.utf8HexToBytes(): ByteArray =
    toByteArray().run(Hex::decode)

private fun ByteArray.toUtf8Hex(): String =
    run(Hex::encode).toString(Charsets.UTF_8)

