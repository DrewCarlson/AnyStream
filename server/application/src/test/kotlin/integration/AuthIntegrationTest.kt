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
package anystream.integration

import anystream.integration.IntegrationTestScope.Companion.SESSION_HEADER
import anystream.models.InviteCode
import anystream.models.Permission
import anystream.models.api.CreateSessionBody
import anystream.models.api.CreateSessionResponse
import anystream.models.api.CreateUserBody
import anystream.models.api.CreateUserResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class AuthIntegrationTest :
    FunSpec({

        test("first user signup grants global permissions and returns a session header") {
            integrationTest {
                val response = client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateUserBody(
                            username = "admin",
                            password = "supersecret",
                            inviteCode = null,
                        ),
                    )
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<CreateUserResponse>().shouldBeInstanceOf<CreateUserResponse.Success>()
                body.permissions shouldContain Permission.Global
                body.user.username shouldBe "admin"
                response.headers[SESSION_HEADER] shouldBe response.headers[SESSION_HEADER]
                checkNotNull(response.headers[SESSION_HEADER]) { "expected session header" }
            }
        }

        test("second signup without an invite code is rejected") {
            integrationTest {
                // create the first (admin) user
                client
                    .post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateUserBody("admin", "supersecret", null))
                    }.status shouldBe HttpStatusCode.OK

                val response = client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserBody("eveuser", "tryingtojoin", null))
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<CreateUserResponse>().shouldBeInstanceOf<CreateUserResponse.Error>()
                body.reason shouldBe CreateUserResponse.ErrorReason.SignupDisabled
            }
        }

        test("invited user signup succeeds with invite-issued permissions") {
            integrationTest {
                // admin signup
                val adminSignup = client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserBody("admin", "supersecret", null))
                }
                val adminSession = checkNotNull(adminSignup.headers[SESSION_HEADER])

                // create an invite as the admin
                val inviteResponse = client.post("/api/users/invite") {
                    withSession(adminSession)
                    contentType(ContentType.Application.Json)
                    setBody(setOf(Permission.ViewCollection))
                }
                inviteResponse.status shouldBe HttpStatusCode.OK
                val invite = inviteResponse.body<InviteCode>()
                invite.permissions shouldContain Permission.ViewCollection

                // sign up using the invite
                val signupResponse = client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserBody("viewer", "alsosecret", invite.secret))
                }
                signupResponse.status shouldBe HttpStatusCode.OK
                val signupBody = signupResponse
                    .body<CreateUserResponse>()
                    .shouldBeInstanceOf<CreateUserResponse.Success>()
                signupBody.permissions shouldBe setOf(Permission.ViewCollection)
                signupBody.user.username shouldBe "viewer"
            }
        }

        test("login with valid credentials returns a usable session, logout invalidates it") {
            integrationTest {
                // signup
                client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserBody("admin", "supersecret", null))
                }

                // login
                val loginResponse = client.post("/api/users/session") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateSessionBody("admin", "supersecret"))
                }
                loginResponse.status shouldBe HttpStatusCode.OK
                loginResponse.body<CreateSessionResponse>().shouldBeInstanceOf<CreateSessionResponse.Success>()
                val session = checkNotNull(loginResponse.headers[SESSION_HEADER])

                // session-protected endpoint succeeds with the session
                val sessionInfo = client.get("/api/users/session") { withSession(session) }
                sessionInfo.status shouldBe HttpStatusCode.OK

                // logout
                val logoutResponse = client.delete("/api/users/session") { withSession(session) }
                logoutResponse.status shouldBe HttpStatusCode.OK

                // subsequent request with the same session is unauthorized
                val afterLogout = client.get("/api/users/session") { withSession(session) }
                afterLogout.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("login with wrong password is forbidden") {
            integrationTest {
                client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserBody("admin", "supersecret", null))
                }

                val response = client.post("/api/users/session") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateSessionBody("admin", "wrong-password"))
                }

                // The server replies with an Error body (200) for known credential errors;
                // an unknown user yields Forbidden. Either way, the body should not be Success.
                if (response.status == HttpStatusCode.OK) {
                    response.body<CreateSessionResponse>().shouldBeInstanceOf<CreateSessionResponse.Error>()
                } else {
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        test("unauthenticated requests to protected endpoints get 401") {
            integrationTest {
                client.get("/api/users/session").status shouldBe HttpStatusCode.Unauthorized
                client.get("/api/users/invite").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })
