plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 30
    defaultConfig {
        minSdk = 23
        targetSdk = 30
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        named("main") {
            java.srcDir("src/main/kotlin")
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.get()
    }
    signingConfigs {
        named("debug") {
            storeFile = file("DevSigningKey")
            storePassword = "password"
            keyAlias = "key0"
            keyPassword = "password"
        }
    }
    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.findByName("debug")
        }
    }
}

dependencies {
    implementation(projects.client)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.core)
    implementation(libs.androidx.leanback.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose)
    implementation(libs.accompanist.coil)
    implementation(libs.bundles.exoplayer)
    implementation(libs.zxing.core)
    implementation(libs.quickie.bundled)
    implementation(libs.anrWatchdog)
}
