import nu.studer.gradle.jooq.JooqGenerate

plugins {
    id("multiplatform-lib")
    alias(libsServer.plugins.jooq)
}

kotlin {
    jvm()

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                inputs.dir(layout.buildDirectory.dir("generated-src/jooq/main"))
                dependsOn("generateJooq")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs(
                "src",
                layout.buildDirectory.dir("generated-src/jooq/main")
            )
            dependencies {
                implementation(libsCommon.serialization.core)
                implementation(libsCommon.serialization.json)
                api(libsCommon.datetime)
                api(libsServer.qbittorrent.models)
            }
        }
    }
}

dependencies {
    jooqGenerator(libsServer.jdbc.sqlite)
    jooqGenerator(projects.server.dbModels.jooqGenerator)
}

val dbFile = layout.buildDirectory.file("anystream-reference.db").get().asFile
val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
val migrationPath = projects.server.dbModels.dependencyProject.file("src/main/resources/db/migration")

val flywayMigrate by tasks.registering(FlywayMigrateTask::class) {
    driver.set("org.sqlite.JDBC")
    url.set(dbUrl)
    migrationsLocation = layout.projectDirectory.dir(migrationPath.absolutePath)
    if (dbFile.createNewFile()) {
        inputs.file(dbFile)
    }
}

jooq {
    version.set(libsServer.versions.jooq.get())
    configurations {
        create("main") {
            jooqConfiguration.anystreamConfig(dbUrl)
        }
    }
}

tasks.getByName<JooqGenerate>("generateJooq") {
    inputs.dir(migrationPath)
    allInputsDeclared.set(true)
    dependsOn("flywayMigrate")
    doLast {
        val dbClassesTree = fileTree(layout.buildDirectory.dir("generated-src/jooq/main")) {
            include("anystream/db/**")
        }
        delete {
            delete(dbClassesTree)
        }
    }
}
