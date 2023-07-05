plugins {
    id("multiplatform-lib")
    id("org.jetbrains.compose")
    id("dev.icerock.mobile.multiplatform-resources")
}

compose {
    kotlinCompilerPlugin.set(libs.jbcompose.compiler.get().toString())
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
    targets.remove(js())

    listOf(iosX64(),
        iosArm64(),
        iosSimulatorArm64()).forEach {
        val main by it.compilations.getting
        val observer by main.cinterops.creating
    }

    sourceSets {
        configureFramework {
            baseName = "AnyStreamCore"
            export(projects.anystreamClientCore)
            export(projects.anystreamDataModels)
            export(libs.mobiuskt.core)
            export(libs.mobiuskt.coroutines)
            freeCompilerArgs += listOf(
                "-linker-option", "-framework", "-linker-option", "Metal",
                "-linker-option", "-framework", "-linker-option", "CoreText",
                "-linker-option", "-framework", "-linker-option", "CoreGraphics",
            )
        }

        val commonMain by getting {
            dependencies {
                api(projects.anystreamClientCore)
                api(projects.anystreamDataModels)
                api(libs.mobiuskt.core)
                api(libs.mobiuskt.coroutines)

                implementation(libs.kamel.image)
                implementation(libs.koin.compose)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
//                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
//                implementation(compose.components.resources)

                api(libs.resources.compose)
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependsOn(commonMain)
                dependencies {
                    implementation(libs.compose.ui.tooling)
                    implementation(libs.compose.ui.tooling.preview)
                    implementation(libs.bundles.exoplayer)
                }
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
            }
        }
    }
}

multiplatformResources {
    multiplatformResourcesPackage = "anystream"
    multiplatformResourcesClassName = "SharedRes"
    iosBaseLocalizationRegion = "en"
}
