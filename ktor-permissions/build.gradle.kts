plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.auth)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.server.serialization)
    testImplementation(libs.serialization.json)
}
