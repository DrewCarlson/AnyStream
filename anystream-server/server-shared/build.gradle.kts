plugins {
    id("server-lib")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.anystreamDataModels)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.ktor.client.core)

    implementation(libs.bouncyCastle)

    implementation(libs.logback)

    implementation(libs.jdbi.core)

    implementation(libs.jaffree)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
