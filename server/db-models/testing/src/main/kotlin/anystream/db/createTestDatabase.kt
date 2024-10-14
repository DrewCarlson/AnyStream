/**
 * AnyStream
 * Copyright (C) 2024 AnyStream Maintainers
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
package anystream.db

import anystream.models.User
import anystream.util.ObjectId
import io.kotest.core.spec.DslDrivenSpec
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.datetime.Clock
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.SingleConnectionDataSource
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.sql.Connection
import kotlin.properties.ReadOnlyProperty


fun DslDrivenSpec.bindTestDatabase(): ReadOnlyProperty<Nothing?, DSLContext> {
    lateinit var connection: Connection
    return bindForTest(
        create = {
            val (con, db) = createTestDatabase()
            connection = con
            db
        },
        cleanup = { connection.close() }
    )
}

/**
 * Create an in-memory database and run app migrations.
 */
fun createTestDatabase(): Pair<Connection, DSLContext> {
    val connection = SQLiteConnectionPoolDataSource().apply {
        url = "jdbc:sqlite::memory:"
        config = SQLiteConfig().apply {
            enforceForeignKeys(true)
        }
    }.connection
    val dataSource = SingleConnectionDataSource(connection)
    runMigrations(dataSource).shouldBeTrue()
    return connection to DSL.using(dataSource, SQLDialect.SQLITE)
}

fun createUserObject(index: Int = 0): User {
    return User(
        id = ObjectId.get().toString(),
        username = "test$index",
        displayName = "Test$index",
        passwordHash = "test-pw-hash",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
}
