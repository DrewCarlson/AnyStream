import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        named("jvmMain") {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
            dependencies {
                implementation(projects.dataModels)

                implementation(libs.serialization.json)
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.jdk8)

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.sessions)
                implementation(libs.ktor.server.metrics)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.authJwt)
                implementation(libs.ktor.server.serialization)
                implementation(libs.ktor.server.websockets)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.json)
                implementation(libs.ktor.client.serialization)

                implementation(libs.bouncyCastle)

                implementation(libs.logback)

                implementation(libs.kmongo.coroutine.serialization)

                implementation(libs.jaffree)

                implementation(libs.tmdbapi)

                implementation(libs.qbittorrent.client)
                implementation(projects.torrentSearch)
                implementation(projects.ktorPermissions)
            }
        }

        named("jvmTest") {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.ktor.server.tests)
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-XXLanguage:+InlineClasses",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }
}

tasks.withType<ShadowJar> {
    manifest {
        archiveFileName.set("server.jar")
        attributes(
            mapOf(
                "Main-Class" to application.mainClass.get()
            )
        )
    }
}
