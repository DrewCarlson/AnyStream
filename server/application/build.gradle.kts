import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("server-lib")
    application
    id("com.gradleup.shadow")
}

application {
    applicationName = "anystream"
    mainClass.set("anystream.ApplicationKt")
}

distributions.configureEach {
    distributionBaseName.set("anystream-server")
}
val web = evaluationDependsOn(":client:web")

tasks.withType<ShadowJar> {
    dependsOn(web.tasks.getByName("jsBrowserProductionDist"))
    archiveFileName.set("anystream.jar")
    archiveBaseName.set("anystream")
    archiveClassifier.set("anystream")
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
    from(web.layout.buildDirectory.file("vite/js/productionExecutable")) {
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
    implementation(projects.client.dataModels)
    implementation(projects.server.dbModels)
    implementation(projects.server.libraryManager)
    implementation(projects.server.metadataManager)
    implementation(projects.server.shared)
    implementation(projects.server.streamService)

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
    implementation(libsCommon.ktor.server.forwardedHeader)
    implementation(libsCommon.ktor.server.contentNegotiation)
    implementation(libsCommon.ktor.server.autoHeadResponse)
    implementation(libsCommon.ktor.server.compression)
    implementation(libsCommon.ktor.server.callLogging)
    implementation(libsCommon.ktor.server.statusPages)
    implementation(libsCommon.ktor.server.cors)
    implementation(libsCommon.ktor.server.auth)
    implementation(libsCommon.ktor.server.authJwt)
    implementation(libsCommon.ktor.server.websockets)
    implementation(libsServer.ktor.server.permissions)

    implementation(libsCommon.ktor.client.core)
    implementation(libsCommon.ktor.client.cio)
    implementation(libsCommon.ktor.client.logging)
    implementation(libsCommon.ktor.client.contentNegotiation)

    implementation(libsServer.bouncyCastle)

    implementation(libsServer.logback)

    implementation(libsServer.bundles.jooq)
    implementation(libsServer.jdbc.sqlite)

    //implementation(libsServer.kjob.core)

    implementation(libsServer.jaffree)

    implementation(libsServer.tmdbapi)

    implementation(libsServer.imageio.webp)

    implementation(libsCommon.koin.core)
    implementation(libsServer.koin.ktor)
    implementation(libsServer.koin.slf4j)

    implementation(libsServer.qbittorrent.client)
    implementation(libsServer.torrentSearch)
    implementation(libsCommon.ktor.client.apache)
    testImplementation(libsCommon.ktor.server.tests)
    testImplementation(projects.server.dbModels.testing)
}

tasks.getByName<JavaExec>("run") {
    dependsOn(web.tasks.getByName("jsBrowserDevelopmentDist"))
    environment(
        "WEB_CLIENT_PATH",
        properties["webClientPath"] ?: environment["WEB_CLIENT_PATH"]
        ?: web.layout.buildDirectory.dir("vite/js/developmentExecutable").get().asFile.absolutePath
    )
    environment(
        "DATABASE_URL",
        properties["databaseUrl"] ?: environment["DATABASE_URL"]
        ?: rootDir.resolve("anystream.db")
    )
}
