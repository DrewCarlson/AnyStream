plugins {
    id("server-lib")
}

buildscript {
    dependencies {
        classpath(libsServer.jdbc.sqlite)
    }
}

dependencies {
    implementation(projects.server.dbModels)
    implementation(projects.client.dataModels)
    implementation(projects.server.shared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)

    implementation(libsServer.logback)

    api(libsServer.jooq)

    implementation(libsServer.jdbc.sqlite)
}