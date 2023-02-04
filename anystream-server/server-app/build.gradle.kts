import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
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
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
        compilations.getByName("test") {
            compileKotlinTask.doFirst {
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

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.bundles.icu4j)

    implementation(libs.ktor.serialization)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.partialContent)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.cachingHeaders)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.autoHeadResponse)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.authJwt)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.permissions)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.contentNegotiation)

    implementation(libs.bouncyCastle)

    implementation(libs.logback)

    implementation(libs.jdbc.sqlite)
    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    implementation(libs.kjob.core)
    implementation(libs.kjob.jdbi)

    implementation(libs.jaffree)

    implementation(libs.tmdbapi)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.slf4j)

    implementation(libs.qbittorrent.client)
    implementation(libs.torrentSearch)
    testImplementation(libs.ktor.server.tests)
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
