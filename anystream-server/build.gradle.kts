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

val testGenSrcPath = "build/generated-kotlin"

kotlin {
    jvm {
        withJava()
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
    sourceSets {
        named("jvmMain") {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
            dependencies {
                implementation(projects.anystreamDataModels)

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
                implementation(libs.ktor.server.permissions)

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
                implementation(libs.torrentSearch)
            }
        }

        named("jvmTest") {
            kotlin.srcDirs("src/test/kotlin", testGenSrcPath)
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(libs.ktor.server.tests)
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
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
