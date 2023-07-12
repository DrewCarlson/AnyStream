plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<com.android.build.gradle.LibraryExtension> {
        compileSdk = 31
        defaultConfig {
            minSdk = 23
            targetSdk = 31
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        namespace = "anystream.models"
    }
}

kotlin {
    if (hasAndroidSdk) {
        android()
    }
    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
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
