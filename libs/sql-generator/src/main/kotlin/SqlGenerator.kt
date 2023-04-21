/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.codegen

import anystream.sql.codegen.GenerateSqlSelect
import anystream.sql.codegen.JoinTable
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

class SqlGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenerateSqlSelect::class.qualifiedName!!)
        symbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                val packageName = symbol.packageName.asString()
                val className = symbol.simpleName.asString().asTableName()

                val columnList = generateSqlSelect(symbol, className)
                val file = codeGenerator.createNewFile(
                    dependencies = Dependencies(false, symbol.containingFile!!),
                    packageName = packageName,
                    fileName = "${className}_GeneratedSql",
                    extensionName = "kt",
                )

                val joinedColumnListValue = generateJoinedTableColumnList(symbol)

                file.writer().use { writer ->
                    writer.appendLine("package $packageName")
                    writer.appendLine()
                    writer.appendLine("const val ${"${className}_SELECT".uppercase()} = \"\"\"")
                    writer.append("SELECT ")
                    writer.append(columnList)
                    joinedColumnListValue.forEach { (_, columns) ->
                        writer.append(", ")
                        writer.appendLine(columns)
                    }
                    writer.append("FROM $className $className LEFT JOIN ")

                    joinedColumnListValue.forEach { (name, _) ->
                        writer.append("$name $name on $name.${className}Id = ${className}_id")
                    }
                    writer.appendLine("\"\"\"")
                }
            } else {
                logger.error("Annotation GenerateSqlSelect is only applicable to classes", symbol)
            }
        }
        return emptyList()
    }

    private fun generateSqlSelect(declaration: KSClassDeclaration, prefix: String): String {
        return declaration.getAllProperties()
            .filterNot { prop ->
                prop.annotations.any {
                    it.annotationType.resolve()
                        .declaration
                        .qualifiedName
                        ?.asString() == JoinTable::class.qualifiedName
                }
            }
            .joinToString(",\n") { prop ->
                val propName = prop.simpleName.asString()
                // escape sqlite reserved keywords
                val cleanPropName = when (SqliteKeywords.contains(propName.lowercase())) {
                    true -> "'$propName'"
                    false -> propName
                }
                "$prefix.$cleanPropName ${prefix}_$propName"
            }
    }

    private fun generateJoinedTableColumnList(declaration: KSClassDeclaration): Map<String, String> {
        return declaration.getAllProperties()
            .filter { prop ->
                prop.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == JoinTable::class.qualifiedName
                }
            }
            .associate { prop ->
                val joinedTableName = prop.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == JoinTable::class.qualifiedName
                }
                    .arguments
                    .first { it.name?.asString() == "table" }
                    .value as String

                val joinedTableClass =
                    (prop.type.resolve().arguments.first().type?.resolve()?.declaration as? KSClassDeclaration)
                        ?: throw IllegalStateException("Joined table property type must be a List of data class.")

                joinedTableName to generateSqlSelect(joinedTableClass, joinedTableName)
            }
    }

    private fun String.asTableName() = replaceFirstChar { it.lowercase() }.substringBeforeLast("Db")
}

class SqlGeneratorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SqlGenerator(environment.codeGenerator, environment.logger)
    }
}
