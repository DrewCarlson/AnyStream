plugins {
    id("server-lib")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
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

    implementation(libs.kjob.core)
    implementation(libs.kjob.jdbi)

    implementation(libs.jaffree)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
