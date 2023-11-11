plugins {
    id("multiplatform-lib")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

apply(plugin = "kotlinx-atomicfu")

dependencies {
    add("kspCommonMainMetadata", libsClient.mobiuskt.codegen)
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("kt.mobius.gen.ExperimentalCodegenApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.anystreamDataModels)
                implementation(libsCommon.atomicfu)
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
                api(libsClient.mobiuskt.core)
                api(libsClient.mobiuskt.extras)
                api(libsClient.mobiuskt.coroutines)
                implementation(libsClient.mobiuskt.codegen.api)

                api(libsCommon.ktor.client.core)
                api(libsCommon.ktor.client.websockets)
                implementation(libsCommon.ktor.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libsClient.mobiuskt.test)
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
