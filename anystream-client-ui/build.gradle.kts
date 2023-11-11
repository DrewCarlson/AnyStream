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
        sourceSets["main"].resources.srcDirs("src/commonMain/resources")
        sourceSets["main"].res.srcDirs("src/androidMain/res", "src/commonMain/resources")

        // needed for @Preview
        buildFeatures {
            compose = true
        }
        composeOptions {
            kotlinCompilerExtensionVersion = libsAndroid.versions.composeCompiler.get()
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

        compilations.configureEach {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xallocator=custom")
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
                api(projects.anystreamClientCore)
                api(projects.anystreamDataModels)
                api(libsClient.mobiuskt.core)
                api(libsClient.mobiuskt.coroutines)
                api(libsClient.mobiuskt.compose)

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
                    implementation(libsClient.compose.ui.tooling)
                    implementation(libsClient.compose.ui.tooling.preview)
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
