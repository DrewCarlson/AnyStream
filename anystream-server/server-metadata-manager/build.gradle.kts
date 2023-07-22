plugins {
    id("server-lib")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverShared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsCommon.ktor.client.core)

    implementation(libsServer.logback)

    implementation(libsServer.jdbc.sqlite)
    implementation(libsServer.jdbi.core)
    implementation(libsServer.jdbi.sqlobject)
    implementation(libsServer.jdbi.kotlin)
    implementation(libsServer.jdbi.kotlin.sqlobject)

    implementation(libsServer.tmdbapi)

    testImplementation(libsCommon.ktor.client.cio)
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
