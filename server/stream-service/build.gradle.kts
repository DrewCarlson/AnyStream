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

    implementation(libsServer.logback)

    implementation(libsServer.bundles.jooq)

    //implementation(libsServer.kjob.core)

    implementation(libsServer.jaffree)
}
