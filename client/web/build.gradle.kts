import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("vite-serve")
    id("org.jetbrains.compose")
    alias(libsCommon.plugins.jsPlainObjects)
    alias(libsCommon.plugins.spotless)
    alias(libsCommon.plugins.serialization)
}

val localProperties = gradleLocalProperties(rootDir, providers)

kotlinJsVite {
    environment["ANYSTREAM_SERVER_URL"] = localProperties.getProperty("anystream.serverUrl", "http://localhost:8888")
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
                optIn("kt.mobius.compose.ExperimentalMobiusktComposeApi")
            }
        }
        named("jsMain") {
            dependencies {
                implementation(projects.client.core)
                implementation(projects.client.presentation)
                implementation(libsCommon.coroutines.core)
                implementation(libsCommon.ktor.client.js)

                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation(libsClient.routingCompose)

                implementation(libsClient.kotlinjs.web)
                implementation(libsClient.kotlinjs.browser)
                implementation(devNpm("bootstrap-icons", "1.13.1"))
                implementation(devNpm("@fontsource/open-sans", "5.2.6"))
                implementation(devNpm("@popperjs/core", "2.11.8"))
                implementation(devNpm("video.js", "8.6.1"))
                implementation(devNpm("@videojs/http-streaming", "3.9.1"))
                implementation(devNpm("mpd-parser", "1.3.1"))
                implementation(devNpm("mux.js", "6.3.0"))
                implementation(devNpm("qrcode", "1.5.4"))
                implementation(devNpm("postcss", "8.5.6"))
                implementation(devNpm("autoprefixer", "10.4.21"))
            }
        }
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
