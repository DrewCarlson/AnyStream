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
package anystream.db.converter

import anystream.json
import anystream.models.Permission
import kotlinx.serialization.encodeToString
import org.jooq.Converter

@Suppress("UNUSED")
class PermissionConverter : Converter<String, Permission> {
    override fun from(databaseObject: String?): Permission? =
        databaseObject?.run(json::decodeFromString)

    override fun to(userObject: Permission?): String? =
        userObject?.run(json::encodeToString)

    override fun fromType(): Class<String> = String::class.java

    override fun toType(): Class<Permission> = Permission::class.java
}

@Suppress("UNUSED")
class PermissionSetConverter : Converter<String, Set<Permission>> {
    override fun from(databaseObject: String?): Set<Permission>? =
        databaseObject?.run(json::decodeFromString)

    override fun to(userObject: Set<Permission>?): String? =
        userObject?.run(json::encodeToString)

    override fun fromType(): Class<String> = String::class.java

    @Suppress("UNCHECKED_CAST")
    override fun toType(): Class<Set<Permission>> = Set::class.java as Class<Set<Permission>>
}