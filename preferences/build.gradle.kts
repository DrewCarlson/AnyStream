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
    if (hasAndroidSdk) {
        android()
    }
    jvm()
    ios()
    js(IR) {
        browser()
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.RequiresOptIn")
            }
        }
        named("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
    }
}
