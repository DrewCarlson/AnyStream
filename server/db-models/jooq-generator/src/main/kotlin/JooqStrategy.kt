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
import org.jooq.codegen.DefaultGeneratorStrategy
import org.jooq.codegen.GeneratorStrategy
import org.jooq.codegen.JavaWriter
import org.jooq.codegen.KotlinGenerator
import org.jooq.meta.Definition
import org.jooq.meta.TableDefinition

class Generator : KotlinGenerator() {

    override fun printClassAnnotations(
        out: JavaWriter,
        definition: Definition?,
        mode: GeneratorStrategy.Mode
    ) {
        super.printClassAnnotations(out, definition, mode)
        if (mode == GeneratorStrategy.Mode.POJO) {
            // Make pojos serializable to be transferred by the API
            out.println("@kotlinx.serialization.Serializable")
        }
    }
}

// Customize the database to code translation performed by Jooq during code generation.
@Suppress("UNUSED")
class JooqStrategy : DefaultGeneratorStrategy() {

    override fun getJavaPackageName(definition: Definition?, mode: GeneratorStrategy.Mode?): String {
        return super.getJavaPackageName(definition, mode)
            // Simplify pojos package to fit in the shared data models structure
            .replace("db.tables.pojos", "models")
    }

    override fun getJavaClassName(definition: Definition?, mode: GeneratorStrategy.Mode?): String {
        return if (definition is TableDefinition && mode == GeneratorStrategy.Mode.DEFAULT) {
            // Make generated table class names distinct with a 'Table' suffix
            "${super.getJavaClassName(definition, mode)}Table"
        } else {
            super.getJavaClassName(definition, mode)
        }
    }
}
