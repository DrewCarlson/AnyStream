plugins {
    id("multiplatform-lib")
    id("com.google.devtools.ksp")
}

dependencies {
    add("kspCommonMainMetadata", libsClient.mobiuskt.codegen)
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("kt.mobius.gen.ExperimentalCodegenApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.client.core)
                api(projects.client.dataModels)
                api(libsClient.mobiuskt.core)
                api(libsClient.mobiuskt.compose)
                api(libsClient.mobiuskt.coroutines)
                implementation(libsClient.mobiuskt.codegen.api)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libsClient.mobiuskt.test)
            }
        }
    }
}
