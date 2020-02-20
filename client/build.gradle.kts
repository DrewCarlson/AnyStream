import com.android.build.gradle.LibraryExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 29
        defaultConfig {
            minSdk = 23
            targetSdk = 29
        }
        sourceSets {
            named("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
            }
        }
    }
}

kotlin {
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    if (hasAndroidSdk) {
        android()
    }

    sourceSets {
        all {
            languageSettings.apply {
                useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
                useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
            }
        }
        val commonMain by getting {
            dependencies {
                api(projects.dataModels)
                api(projects.apiClient)
                api(libs.coroutines.core)
                api(libs.mobiuskt.core)
                api(libs.mobiuskt.extras)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)

                api(libs.ktor.client.core)
                implementation(libs.ktor.client.json)
                implementation(libs.ktor.client.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependencies {
                    implementation("androidx.core:core-ktx:1.6.0-beta02")
                }
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
