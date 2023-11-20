plugins {
    id("server-lib")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.client.dataModels)
    implementation(projects.server.dbModels)
    implementation(projects.server.shared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsServer.logback)

    implementation(libsServer.jdbi.core)
    implementation(libsServer.jdbi.sqlobject)
    implementation(libsServer.jdbi.kotlin)
    implementation(libsServer.jdbi.kotlin.sqlobject)

    implementation(libsServer.kjob.core)
    implementation(libsServer.kjob.jdbi)

    implementation(libsServer.jaffree)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
