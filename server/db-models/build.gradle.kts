import nu.studer.gradle.jooq.JooqGenerate

plugins {
    id("server-lib")
    alias(libsServer.plugins.jooq)
}

buildscript {
    dependencies {
        classpath(libsServer.jdbc.sqlite)
    }
}

sourceSets {
    main {
        kotlin.srcDirs(
            layout.buildDirectory.file("generated-src/jooq/$name")
        )
    }
}

dependencies {
    jooqGenerator(libsServer.jdbc.sqlite)
    jooqGenerator(projects.server.dbModels.jooqGenerator)
    implementation(projects.client.dataModels)
    implementation(projects.server.shared)
    implementation(libsServer.bundles.jooq)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)

    implementation(libsServer.logback)

    implementation(libsServer.flyway.core)

    testImplementation(projects.server.dbModels.testing)
    testImplementation(libsServer.jdbc.sqlite)
}

kotlin {
    target {
        compilations.all {
            compileTaskProvider.configure {
                inputs.dir(layout.buildDirectory.dir("generated-src/jooq/main"))
                dependsOn("generateJooq")
            }
        }
    }
}

val dbFile = layout.buildDirectory.file("anystream-reference.db").get().asFile
val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
val migrationPath = file("src/main/resources/db/migration")

val flywayMigrate by tasks.registering(FlywayMigrateTask::class) {
    driver.set("org.sqlite.JDBC")
    url.set(dbUrl)
    migrationsLocation = layout.projectDirectory.dir(migrationPath.absolutePath)
    if (dbFile.exists()) {
        inputs.file(dbFile)
        doFirst { dbFile.delete() }
    }
}

//https://github.com/gotson/komga/blob/c97a322a5d2f2d9e3068779751ce88b5aa9a7f10/komga/build.gradle.kts
jooq {
    version.set(libsServer.versions.jooq.get())
    configurations {
        create("main") {
            jooqConfiguration.anystreamConfig(dbUrl)
        }
    }
}

val pojosTree = fileTree(layout.buildDirectory.dir("generated-src/jooq/main")) {
    include("anystream/models/**")
}

tasks.getByName<JooqGenerate>("generateJooq") {
    inputs.dir(migrationPath)
    allInputsDeclared.set(true)
    dependsOn("flywayMigrate")
    doLast {
        delete {
            delete(pojosTree)
        }
    }
}