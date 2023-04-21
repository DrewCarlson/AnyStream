plugins {
    id("multiplatform-lib")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
                api(libs.datetime)
                api(libs.qbittorrent.models)
            }
        }
    }
}
