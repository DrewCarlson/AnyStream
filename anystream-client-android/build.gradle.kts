plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 31
    defaultConfig {
        minSdk = 24
        targetSdk = 31
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
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
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

        named("release") {
        }
    }
    kotlinOptions.freeCompilerArgs += listOf(
        "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        "-opt-in=coil.annotation.ExperimentalCoilApi",
        //"-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
    )
}

dependencies {
    implementation(projects.anystreamClientCore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.core)
    implementation(libs.androidx.leanback.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.bundles.compose)
    implementation(libs.coil)
    implementation(libs.bundles.exoplayer)
    implementation(libs.zxing.core)
    implementation(libs.quickie.bundled)
    implementation(libs.anrWatchdog)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.okhttp)

    debugImplementation(libs.leakcanary)
}
