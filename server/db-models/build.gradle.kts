import nu.studer.gradle.jooq.JooqGenerate
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Logging

plugins {
    id("server-lib")
    alias(libsServer.plugins.jooq)
    alias(libsServer.plugins.flyway)
}

buildscript {
    dependencies {
        classpath(libsServer.jdbc.sqlite)
    }
}

sourceSets {
    main {
        kotlin.srcDirs(
            layout.buildDirectory.file("generated/ksp/$name/kotlin"),
            layout.buildDirectory.file("generated-src/jooq/$name")
        )
    }
}

dependencies {
    jooqGenerator(libsServer.jdbc.sqlite)
    jooqGenerator(projects.server.dbModels.jooqGenerator)
    implementation(projects.libs.sqlGeneratorApi)
    implementation(projects.client.dataModels)
    implementation(projects.server.shared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)

    implementation(libsServer.logback)

    implementation(libsServer.flyway.core)

    testImplementation(projects.server.dbModels.testing)
    testImplementation(libsServer.jdbc.sqlite)
}

val dbFile = layout.buildDirectory.file("anystream-reference.db").get().asFile
val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
val migrationPath = file("src/main/resources/db/migration")

flyway {
    url = dbUrl
    locations = arrayOf("filesystem:$migrationPath")
}

tasks.named("flywayMigrate") {
    inputs.dir(migrationPath)
    doFirst {
        delete(buildDir.resolve("generated-src"))
        delete(dbUrl.substringAfterLast(':'))
    }
}

//https://github.com/gotson/komga/blob/c97a322a5d2f2d9e3068779751ce88b5aa9a7f10/komga/build.gradle.kts
jooq {
    version.set(libsServer.versions.jooq.get())
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = Logging.DEBUG
                jdbc.apply {
                    driver = "org.sqlite.JDBC"
                    url = dbUrl
                }
                generator.apply {
                    name = "Generator"
                    strategy.name = "JooqStrategy"
                    target.packageName = "anystream.db"
                    generate.apply {
                        isDaos = false
                        isRecords = true
                        isKotlinNotNullRecordAttributes = true
                        isKotlinNotNullInterfaceAttributes = true
                        isJavaTimeTypes = false
                        // Pojos as simple data classes
                        isSerializablePojos = false
                        isImmutablePojos = true
                        isPojosToString = false
                        isPojosEqualsAndHashCode = false
                        isPojosAsKotlinDataClasses = true
                        isKotlinNotNullPojoAttributes = true
                    }
                    database.apply {
                        name = "org.jooq.meta.sqlite.SQLiteDatabase"
                        excludes = listOf(
                            // Exclude flyway migration tables
                            "flyway_.*",
                            // Exclude search meta tables
                            "searchable_content_.*",
                        ).joinToString("|")
                        forcedTypes.addAll(forcedTypes())
                    }
                }
            }
        }
    }
}

val pojosTree = fileTree(layout.buildDirectory.dir("generated-src/jooq/main")) {
    include("**/models/**")
}

tasks.create<Copy>("movePojos") {
    from(pojosTree)
    into(rootProject.file("client/data-models/build/generated-src/jooq/main"))
    finalizedBy("deleteOriginalPojos")
}

tasks.create<Delete>("deleteOriginalPojos") {
    delete(pojosTree)
}

tasks.getByName<FlywayMigrateTask>("flywayMigrate") {
    if (dbFile.exists()) {
        inputs.file(dbFile)
        doFirst { dbFile.delete() }
    }
}

tasks.getByName<JooqGenerate>("generateJooq") {
    inputs.dir(migrationPath)
    allInputsDeclared.set(true)
    dependsOn("flywayMigrate")
    doFirst { Thread.sleep(2000) }
    finalizedBy("movePojos")
}

fun forcedType(
    userType: String,
    includeExpression: String,
    converter: String? = null,
    isEnumConverter: Boolean = false
): ForcedType =
    ForcedType().apply {
        this.userType = userType
        this.includeExpression = includeExpression
        this.isEnumConverter = isEnumConverter
        this.converter = converter
    }

fun forcedTypeEnum(
    userType: String,
    includeExpression: String,
): ForcedType = forcedType(userType, includeExpression, isEnumConverter = true)

fun forcedTypes(): List<ForcedType> = listOf(
    ForcedType().apply {
        includeTypes = "DATETIME"
        userType = "kotlinx.datetime.Instant"
        binding = "anystream.db.converter.JooqInstantBinding"
        //generator = "anystream.db.converter.JooqInstantGenerator"
    },
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "kotlin.Set<anystream.models.Permission>",
        includeExpression = "invite_code.permissions",
        converter = "anystream.db.converter.PermissionSetConverter"
    ),
    forcedTypeEnum("anystream.models.StreamEncodingType", "stream_encoding.type"),
    forcedTypeEnum("anystream.models.MediaType", "media_type"),
    forcedTypeEnum("anystream.models.MediaLinkType", "media_link.type"),
    forcedTypeEnum("anystream.models.MediaKind", "media_kind"),
    forcedTypeEnum("anystream.models.Descriptor", "descriptor"),
    forcedType("kotlin.String", "searchable_content.id"),
    forcedType("kotlin.String", "searchable_content.content"),
)