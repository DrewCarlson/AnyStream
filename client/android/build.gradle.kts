plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    alias(libsCommon.plugins.spotless)
}

android {
    namespace = "anystream.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libsAndroid.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes.add("META-INF/versions/*/*.bin")
            pickFirsts.add("META-INF/AL2.0")
            pickFirsts.add("META-INF/LGPL2.1")
        }
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
        "-opt-in=kt.mobius.compose.ExperimentalMobiusktComposeApi",
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
    implementation(projects.client.core)
    implementation(projects.client.ui)
    implementation(libsAndroid.androidx.core.ktx)
    implementation(libsAndroid.androidx.appcompat.core)
    implementation(libsAndroid.androidx.leanback.core)
    implementation(libsAndroid.androidx.activity.ktx)
    implementation(libsAndroid.androidx.activity.compose)
    implementation(libsAndroid.androidx.lifecycle.viewmodel.compose)
    implementation(libsClient.compose.ui.ui)
    implementation(libsClient.compose.ui.tooling.preview)
    implementation(libsClient.compose.livedata)
    implementation(libsClient.compose.foundation)
    implementation(libsClient.compose.material)
    implementation(libsClient.compose.icons)
    implementation(libsClient.coil.compose)
    implementation(libsAndroid.bundles.exoplayer)
    implementation(libsAndroid.zxing.core)
    implementation(libsAndroid.quickie.bundled)
    implementation(libsAndroid.okhttp)
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

afterEvaluate {
    spotless {
        kotlin {
            target("**/**.kt")
            licenseHeaderFile(rootDir.resolve("licenseHeader.txt"))
            //ktlint(libsCommon.versions.ktlint.get())
            //    .setEditorConfigPath(rootDir.resolve(".editorconfig"))
        }
    }
}