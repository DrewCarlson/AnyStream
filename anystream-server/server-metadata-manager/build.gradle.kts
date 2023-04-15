plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverShared)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.ktor.client.core)

    implementation(libs.logback)

    implementation(libs.jdbc.sqlite)
    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    implementation(libs.tmdbapi)

    testImplementation(libs.ktor.client.cio)
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
