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
package anystream.db.mappers

import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.enums.EnumStrategy
import org.jdbi.v3.core.enums.Enums

fun Jdbi.registerMappers() {
    config.get(Enums::class.java).setEnumStrategy(EnumStrategy.BY_ORDINAL)

    registerArgument(InstantMappers.Argument)
    registerColumnMapper(InstantMappers.Column)

    registerArgument(PermissionMappers.Argument)
    registerColumnMapper(PermissionMappers.Column)
    registerArgument(PermissionMappers.SetArgument)
    registerColumnMapper(PermissionMappers.SetColumn)
}
