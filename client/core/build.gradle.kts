plugins {
    id("multiplatform-lib")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.client.dataModels)
                implementation(libsCommon.coroutines.core)
                implementation(libsCommon.serialization.core)
                implementation(libsCommon.serialization.json)

                implementation(libsCommon.ktor.client.core)
                implementation(libsCommon.ktor.client.contentNegotiation)
                implementation(libsCommon.ktor.client.websockets)
                implementation(libsCommon.ktor.serialization)
                implementation(libsCommon.ktor.client.logging)

                api(libsCommon.koin.core)
                api(libsClient.objectstore.core)
                api(libsClient.objectstore.json)
                api(libsCommon.coroutines.core)

                api(libsCommon.ktor.client.core)
                api(libsCommon.ktor.client.websockets)
                implementation(libsCommon.ktor.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependencies {
                    implementation(libsCommon.ktor.client.cio)
                    implementation(libsClient.objectstore.secure)
                }
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libsCommon.ktor.client.js)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libsCommon.ktor.client.darwin)
                implementation(libsClient.objectstore.secure)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libsClient.objectstore.fs)
                implementation(libsCommon.ktor.client.cio)
            }
        }
    }
}
