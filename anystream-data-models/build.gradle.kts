plugins {
    id("multiplatform-lib")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(libsCommon.serialization.core)
                implementation(libsCommon.serialization.json)
                api(libsCommon.datetime)
                api(libsServer.qbittorrent.models)
            }
        }
    }
}
