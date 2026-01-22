plugins {
    id("server-lib")
}

dependencies {
    implementation(projects.client.dataModels)
    implementation(projects.server.dbModels)
    implementation(projects.server.shared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)
    implementation(libsCommon.coroutines.jdk8)
    implementation(libsCommon.coroutines.slf4j)

    implementation(libsCommon.ktor.client.core)

    implementation(libsServer.logback)

    implementation(libsServer.jooq)
    implementation(libsServer.jdbc.sqlite)

    implementation(libsServer.tmdbapi)

    testImplementation(projects.server.dbModels.testing)
    testImplementation(libsCommon.ktor.client.cio)
    testImplementation(libsCommon.ktor.client.logging)
    testImplementation(kotlin("reflect"))
}
