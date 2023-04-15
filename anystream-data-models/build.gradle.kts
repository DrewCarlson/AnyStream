plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "anystream.models"
        compileSdk = 33
        defaultConfig {
            minSdk = 23
            targetSdk = 33
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    if (hasAndroidSdk) {
        android {
            compilations.all {
                kotlinOptions {
                    jvmTarget = JavaVersion.VERSION_11.majorVersion
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
                api(libs.datetime)
                api(libs.qbittorrent.models)
            }
        }

        val commonTest by getting

        val iosMain by creating {
            dependsOn(commonMain)
        }

        val iosTest by creating {
            dependsOn(commonTest)
        }

        sourceSets.filter { sourceSet ->
            sourceSet.name.run {
                startsWith("iosX64") ||
                        startsWith("iosArm") ||
                        startsWith("iosSimulator")
            }
        }.forEach { sourceSet ->
            if (sourceSet.name.endsWith("Main")) {
                sourceSet.dependsOn(iosMain)
            } else {
                sourceSet.dependsOn(iosTest)
            }
        }
    }
}
