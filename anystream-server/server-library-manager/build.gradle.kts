plugins {
    id("server-lib")
    alias(libsCommon.plugins.serialization)
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverMetadataManager)
    implementation(projects.anystreamServer.serverShared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsServer.logback)

    implementation(libsServer.jdbi.core)
    implementation(libsServer.jdbi.sqlobject)
    implementation(libsServer.jdbi.kotlin)
    implementation(libsServer.jdbi.kotlin.sqlobject)

    implementation(libsServer.jaffree)

    implementation(libsServer.qbittorrent.client)
    implementation(libsServer.torrentSearch)
    testImplementation(libsCommon.ktor.server.tests)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
