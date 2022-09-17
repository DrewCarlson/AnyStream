import com.android.build.gradle.LibraryExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 33
        defaultConfig {
            minSdk = 23
            targetSdk = 31
        }
        namespace = "anystream.prefs"
    }
}

kotlin {
    if (hasAndroidSdk) {
        android()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
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
