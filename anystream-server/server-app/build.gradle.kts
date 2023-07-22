import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("server-lib")
    alias(libsCommon.plugins.serialization)
    application
    id("com.github.johnrengelman.shadow")
}

application {
    applicationName = "anystream"
    mainClass.set("io.ktor.server.netty.EngineMain")
}

distributions.configureEach {
    distributionBaseName.set("anystream-server")
}

tasks.withType<ShadowJar> {
    val clientWeb = projects.anystreamClientWeb.dependencyProject
    dependsOn(clientWeb.tasks.getByName("jsBrowserDistribution"))
    archiveFileName.set("anystream.jar")
    archiveBaseName.set("anystream")
    archiveClassifier.set("anystream")
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
    from(rootProject.file("anystream-client-web/build/distributions")) {
        into("anystream-client-web")
    }
}

val testGenSrcPath = "build/generated-kotlin"

kotlin {
    sourceSets["test"].kotlin.srcDirs(testGenSrcPath)
    sourceSets.all {
        languageSettings {
            optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
    target {
        compilations.getByName("test") {
            compileTaskProvider.get().doFirst {
                file(testGenSrcPath).also { if (!it.exists()) it.mkdirs() }
                val configFile = file("$testGenSrcPath/config.kt")
                if (!configFile.exists()) {
                    val resources = file("src/test/resources").absolutePath
                    configFile.createNewFile()
                    configFile.writeText(buildString {
                        appendLine("package anystream.test")
                        appendLine("const val RESOURCES = \"${resources.replace('\\', '/')}\"")
                    })
                }
            }
        }
    }
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverLibraryManager)
    implementation(projects.anystreamServer.serverMetadataManager)
    implementation(projects.anystreamServer.serverShared)
    implementation(projects.anystreamServer.serverStreamService)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsServer.bundles.icu4j)

    implementation(libsCommon.ktor.serialization)
    implementation(libsCommon.ktor.server.core)
    implementation(libsCommon.ktor.server.netty)
    implementation(libsCommon.ktor.server.sessions)
    implementation(libsCommon.ktor.server.metrics)
    implementation(libsCommon.ktor.server.partialContent)
    implementation(libsCommon.ktor.server.defaultHeaders)
    implementation(libsCommon.ktor.server.cachingHeaders)
    implementation(libsCommon.ktor.server.contentNegotiation)
    implementation(libsCommon.ktor.server.autoHeadResponse)
    implementation(libsCommon.ktor.server.compression)
    implementation(libsCommon.ktor.server.callLogging)
    implementation(libsCommon.ktor.server.statusPages)
    implementation(libsCommon.ktor.server.cors)
    implementation(libsCommon.ktor.server.auth)
    implementation(libsCommon.ktor.server.authJwt)
    implementation(libsCommon.ktor.server.websockets)
    implementation(libsCommon.ktor.server.permissions)

    implementation(libsCommon.ktor.client.core)
    implementation(libsCommon.ktor.client.cio)
    implementation(libsCommon.ktor.client.logging)
    implementation(libsCommon.ktor.client.contentNegotiation)

    implementation(libsServer.bouncyCastle)

    implementation(libsServer.logback)

    implementation(libsServer.jdbc.sqlite)
    implementation(libsServer.jdbi.core)
    implementation(libsServer.jdbi.sqlobject)
    implementation(libsServer.jdbi.kotlin)
    implementation(libsServer.jdbi.kotlin.sqlobject)

    implementation(libsServer.kjob.core)
    implementation(libsServer.kjob.jdbi)

    implementation(libsServer.jaffree)

    implementation(libsServer.tmdbapi)

    implementation(libsCommon.koin.core)
    implementation(libsCommon.koin.ktor)
    implementation(libsCommon.koin.slf4j)

    implementation(libsServer.qbittorrent.client)
    implementation(libsServer.torrentSearch)
    testImplementation(libsCommon.ktor.server.tests)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks.getByName<JavaExec>("run") {
    val clientWeb = projects.anystreamClientWeb.dependencyProject
    dependsOn(clientWeb.tasks.getByName("jsBrowserDevelopmentExecutableDistribution"))
    environment(
        "WEB_CLIENT_PATH",
        properties["webClientPath"] ?: environment["WEB_CLIENT_PATH"]
        ?: clientWeb.buildDir.resolve("developmentExecutable").absolutePath
    )
    environment(
        "DATABASE_URL",
        properties["databaseUrl"] ?: environment["DATABASE_URL"]
        ?: "sqlite:${rootDir.resolve("anystream.db")}"
    )
}
