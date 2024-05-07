/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
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

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.slf4j.Logger
import javax.sql.DataSource

fun runMigrations(connectionString: String, logger: Logger? = null): Boolean {
    val flyway = Flyway.configure()
        .loggers("slf4j")
        .dataSource(connectionString, null, null)
        .load()
    return try {
        flyway.migrate()
        true
    } catch (e: FlywayException) {
        logger?.error("Database migrations failed", e)
        false
    }
}

fun runMigrations(dataSource: DataSource, logger: Logger? = null): Boolean {
    val flyway = Flyway.configure()
        .loggers("slf4j")
        .dataSource(dataSource)
        .load()
    return try {
        flyway.migrate()
        true
    } catch (e: FlywayException) {
        logger?.error("Database migrations failed", e)
        false
    }
}
