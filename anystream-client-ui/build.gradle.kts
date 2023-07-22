plugins {
    id("multiplatform-lib")
    id("org.jetbrains.compose")
}

compose {
    kotlinCompilerPlugin.set(libsClient.jbcompose.compiler.get().toString())
    /*android {
        useAndroidX = true
    }*/
}

if (hasAndroidSdk) {
    configure<com.android.build.gradle.LibraryExtension> {
        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
        sourceSets["main"].res.srcDirs("src/androidMain/res")
        sourceSets["main"].resources.srcDirs("src/commonMain/resources")
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
        val observer by main.cinterops.creating
        binaries {
            framework {
                baseName = "AnyStreamCore"
                export(projects.anystreamClientCore)
                export(projects.anystreamDataModels)
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
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.anystreamClientCore)
                api(projects.anystreamDataModels)
                api(libsClient.mobiuskt.core)
                api(libsClient.mobiuskt.coroutines)

                implementation(libsClient.kamel.image)
                implementation(libsClient.koin.compose)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                api(compose.components.resources)
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependsOn(commonMain)
                dependencies {
                    implementation(libsAndroid.compose.ui.tooling)
                    implementation(libsAndroid.compose.ui.tooling.preview)
                    implementation(libsAndroid.bundles.media3)
                    implementation(libsAndroid.androidx.activity.ktx)
                    implementation(libsAndroid.androidx.activity.compose)
                }
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(libsClient.vlcj)
            }
        }

        configureCommonIosSourceSets()
    }
}
