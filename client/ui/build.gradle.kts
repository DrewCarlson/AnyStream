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
        binaries {
            framework {
                baseName = "AnyStreamCore"
                export(projects.client.core)
                export(projects.client.dataModels)
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal",
                    "-linker-option", "-framework", "-linker-option", "CoreText",
                    "-linker-option", "-framework", "-linker-option", "CoreGraphics",
                )
            }
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material.ExperimentalMaterialApi")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kt.mobius.compose.ExperimentalMobiusktComposeApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.client.core)
                api(projects.client.dataModels)
                api(libsClient.mobiuskt.core)
                api(libsClient.mobiuskt.coroutines)
                api(libsClient.mobiuskt.compose)

                implementation(libsClient.coil.compose)
                implementation(libsClient.coil.ktor)
                implementation(libsClient.koin.compose)
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

        configureCommonIosSourceSets()
    }
}
