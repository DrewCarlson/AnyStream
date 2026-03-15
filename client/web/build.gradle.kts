import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("spotless")
    alias(libsCommon.plugins.jsPlainObjects)
    alias(libsCommon.plugins.serialization)
    alias(libsCommon.plugins.kotlinVite)
    alias(libsCommon.plugins.metro)
}

val localProperties = gradleLocalProperties(rootDir, providers)

kotlinVite {
    val anystreamUrl = localProperties.getProperty("anystream.serverUrl", "http://localhost:8888")
    environment["ANYSTREAM_SERVER_URL"] = anystreamUrl
}

kotlin {
    js(IR) {
        useEsModules()
        browser {
            binaries.executable()
        }
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
                optIn("kotlin.time.ExperimentalTime")
            }
        }
        named("jsMain") {
            dependencies {
                implementation(projects.client.core)
                implementation(projects.client.presentation)
                implementation(libsCommon.coroutines.core)
                implementation(libsCommon.ktor.client.js)

                implementation(libsClient.compose.html.core)
                implementation(libsClient.compose.runtime)
                implementation(libsClient.routingCompose)

                implementation(libsClient.kotlinjs.web)
                implementation(libsClient.kotlinjs.browser)
                implementation(devNpm("jquery", "3.7.1"))
                implementation(devNpm("bootstrap", "5.3.8"))
                implementation(devNpm("bootstrap-icons", "1.13.1"))
                implementation(devNpm("@fontsource/open-sans", "5.2.7"))
                implementation(devNpm("@popperjs/core", "2.11.8"))
                implementation(devNpm("video.js", "8.6.1"))
                implementation(devNpm("@videojs/http-streaming", "3.17.4"))
                implementation(devNpm("mpd-parser", "1.3.1"))
                implementation(devNpm("mux.js", "6.3.0"))
                implementation(devNpm("qrcode", "1.5.4"))
                implementation(devNpm("sass", "1.79.6"))
            }
        }
    }
}
