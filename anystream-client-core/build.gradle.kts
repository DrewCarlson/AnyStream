import com.android.build.gradle.LibraryExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 31
        defaultConfig {
            minSdk = 23
            targetSdk = 31
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        sourceSets {
            named("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
            }
        }
    }
}

dependencies {
    add("kspMetadata", libs.mobiuskt.updateSpec)
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
    if (name != "kspKotlinMetadata") {
        dependsOn("kspKotlinMetadata")
    }
}

kotlin {
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
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
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/$name/kotlin")
            dependencies {
                api(projects.anystreamDataModels)
                api(projects.anystreamClientApi)
                api(libs.coroutines.core)
                api(libs.mobiuskt.core)
                api(libs.mobiuskt.extras)
                api(libs.mobiuskt.coroutines)
                implementation(libs.mobiuskt.updateSpec.api)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)

                api(libs.ktor.client.core)
                api(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.mobiuskt.test)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependencies {
                    implementation(libs.androidx.core.ktx)
                }
            }

            val androidTest by getting {
                dependencies {
                    implementation(libs.androidx.test.runner)
                    implementation(kotlin("test"))
                    implementation(kotlin("test-junit"))
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
