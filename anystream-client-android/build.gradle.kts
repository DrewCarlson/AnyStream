plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "anystream.android"
    compileSdk = 33
    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libsAndroid.versions.composeCompiler.get()
    }
    packaging {
        resources.excludes.add("META-INF/versions/*/*.bin")
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
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
    }
}

dependencies {
    implementation(projects.anystreamClientCore)
    implementation(projects.anystreamClientUi)
    implementation(libsAndroid.androidx.core.ktx)
    implementation(libsAndroid.androidx.appcompat.core)
    implementation(libsAndroid.androidx.leanback.core)
    implementation(libsAndroid.androidx.activity.ktx)
    implementation(libsAndroid.androidx.activity.compose)
    implementation(libsAndroid.androidx.lifecycle.viewmodel.compose)
    implementation(libsAndroid.bundles.compose)
    implementation(libsAndroid.coil)
    implementation(libsAndroid.bundles.exoplayer)
    implementation(libsAndroid.zxing.core)
    implementation(libsAndroid.quickie.bundled)
    implementation(libsClient.okhttp)
    implementation(libsCommon.ktor.client.cio)
    implementation(libsCommon.koin.core)
    implementation(libsAndroid.koin.android)
    implementation(libsAndroid.koin.android.compat)
    implementation(libsAndroid.koin.androidx.compose)

    debugImplementation(libsAndroid.anrWatchdog)
    debugImplementation(libsAndroid.leakcanary)

    androidTestImplementation(libsCommon.coroutines.test) {
        // conflicts with mockito due to direct inclusion of byte buddy
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
    }
}
