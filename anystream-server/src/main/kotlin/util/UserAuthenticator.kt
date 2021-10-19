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

import anystream.models.PASSWORD_LENGTH_MAX
import anystream.models.PASSWORD_LENGTH_MIN
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.util.encoders.Hex
import kotlin.random.Random


typealias PasswordString = String
typealias SaltString = String

object UserAuthenticator {
    private const val SALT_BYTES = 16
    private const val BCRYPT_COST = 10

    fun hashPassword(password: String): Pair<PasswordString, SaltString> {
        require(password.length in PASSWORD_LENGTH_MIN..PASSWORD_LENGTH_MAX) {
            "Expected password to be in ${PASSWORD_LENGTH_MIN}..${PASSWORD_LENGTH_MAX} but was ${password.length}"
        }
        val salt = Random.nextBytes(SALT_BYTES)
        val hashedBytes = OpenBSDBCrypt.generate(password.encodeToByteArray(), salt, BCRYPT_COST)
        return hashedBytes to Hex.encode(salt).decodeToString()
    }

    fun verifyPassword(checkPassword: String, hashedPassword: String): Boolean {
        return OpenBSDBCrypt.checkPassword(hashedPassword, checkPassword.toCharArray())
    }
}
