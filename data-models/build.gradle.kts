plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
                api(libs.qbittorrent.models)
            }
        }

        val jvmMain by getting {
        }
    }
}
