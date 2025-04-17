plugins {
    id("multiplatform-lib")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

compose {
    /*android {
        useAndroidX = true
    }*/

    resources {
        publicResClass = true
        generateResClass = always
    }
}

if (hasAndroidSdk) {
    configure<com.android.build.gradle.LibraryExtension> {
        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
        sourceSets["main"].res.srcDirs("src/androidMain/res")

        // needed for @Preview
        buildFeatures {
            compose = true
        }
        composeOptions {
        }
    }
}

kotlin {
    configure(
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        )
    ) {
        val main by compilations.getting
        main.cinterops.create("observer")
    }
    sourceSets {
        all {
            languageSettings {
                optIn("kt.mobius.gen.ExperimentalCodegenApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kt.mobius.compose.ExperimentalMobiusktComposeApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.client.core)
                api(projects.client.dataModels)
                api(projects.client.presentation)
                implementation(libsClient.coil.compose)
                implementation(libsClient.coil.ktor)
                implementation(libsClient.koin.compose)
                implementation(libsClient.haze.core)
                implementation(libsClient.haze.materials)
                implementation(libsClient.compose.lifecycle)
                implementation(libsClient.compose.bundle)
                implementation(libsClient.compose.viewmodel)
                implementation(libsClient.compose.backhandler)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                api(compose.components.resources)
            }
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libsClient.zxing.core)
            }
        }

        if (hasAndroidSdk) {
            named("androidMain") {
                dependsOn(jvmCommonMain)
                dependencies {
                    implementation(libsClient.compose.ui.tooling)
                    implementation(libsClient.compose.ui.tooling.preview)
                    implementation(libsClient.bundles.camerax)
                    implementation(libsClient.bundles.media3)
                    implementation(libsAndroid.androidx.activity.ktx)
                    implementation(libsAndroid.androidx.activity.compose)
                }
            }
        }

        named("jvmMain") {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(compose.desktop.common)
                implementation(libsClient.vlcj)
            }
        }
    }
}
