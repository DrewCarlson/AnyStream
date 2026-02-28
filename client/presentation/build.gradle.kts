plugins {
    id("multiplatform-lib")
    kotlin("plugin.compose")
    alias(libsCommon.plugins.ksp)
    alias(libsClient.plugins.metro)
    alias(libsClient.plugins.composejb)
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.client.core)
                api(projects.client.dataModels)
                //api(libsClient.compose.resources)
                api(libsClient.compose.runtime)
                api(libsClient.molecule)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }
    }
}
