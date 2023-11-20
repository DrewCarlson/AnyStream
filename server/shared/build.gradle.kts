plugins {
    id("server-lib")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.client.dataModels)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsCommon.ktor.client.core)

    implementation(libsServer.bouncyCastle)

    implementation(libsServer.logback)

    implementation(libsServer.jdbi.core)

    implementation(libsServer.jaffree)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
