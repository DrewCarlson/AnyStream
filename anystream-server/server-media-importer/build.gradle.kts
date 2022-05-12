import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

tasks.withType<KotlinCompile>().all {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs + listOf("-opt-in=kotlin.RequiresOptIn")
            }
        }
    }
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverMetadataManager)
    implementation(projects.anystreamServer.serverShared)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.logback)

    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    implementation(libs.jaffree)

    implementation(libs.qbittorrent.client)
    implementation(libs.torrentSearch)
    testImplementation(libs.ktor.server.tests)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
