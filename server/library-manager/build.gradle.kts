plugins {
    id("server-lib")
}

dependencies {
    implementation(projects.client.dataModels)
    implementation(projects.server.dbModels)
    implementation(projects.server.metadataManager)
    implementation(projects.server.shared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)

    implementation(libsServer.logback)

    implementation(libsServer.jaffree)

    implementation(libsServer.qbittorrent.client)
    implementation(libsServer.torrentSearch)
    testImplementation(projects.server.dbModels.testing)
    testImplementation(libsCommon.ktor.server.tests)
    testImplementation(libsServer.jdbc.sqlite)
}
