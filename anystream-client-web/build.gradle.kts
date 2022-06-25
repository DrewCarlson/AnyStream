import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

val localProperties = gradleLocalProperties(rootDir)

kotlin {
    js(IR) {
        useCommonJs()
        browser {
            binaries.executable()
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            runTask {
                val anystreamUrl = localProperties.getProperty("anystream.serverUrl", "http://localhost:8888")
                outputFileName = "main.bundle.js"
                devtool = "eval-source-map"
                devServer = DevServer(
                    open = false,
                    port = 3000,
                    static = mutableListOf("$buildDir/processedResources/js/main"),
                    proxy = mutableMapOf(
                        "/api" to mapOf<String, Any>(
                            "ws" to true,
                            "target" to anystreamUrl,
                            "secure" to anystreamUrl.startsWith("https"),
                            "changeOrigin" to true,
                            "rejectUnauthorzied" to false,
                        )
                    ),
                )
            }
            webpackTask {
                outputFileName = "main.bundle.js"
                //devtool = "eval-source-map"
            }
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
                implementation(projects.anystreamClientCore)
                implementation(libs.coroutines.core)
                implementation(libs.ktor.client.js)

                implementation(compose.web.core)
                implementation(compose.runtime)
                implementation(libs.routingCompose)

                implementation(libs.kotlinjs.extensions)
                implementation(devNpm("bootstrap", "5.1.3"))
                implementation(devNpm("bootstrap-icons", "1.8.2"))
                implementation(devNpm("@fontsource/open-sans", "4.5.8"))
                implementation(devNpm("@popperjs/core", "2.11.5"))
                implementation(devNpm("video.js", "7.17.3"))
                implementation(devNpm("webworkify-webpack-dropin", "1.1.9"))
                implementation(devNpm("mini-css-extract-plugin", "2.6.0"))
                implementation(devNpm("file-loader", "6.2.0"))
                implementation(devNpm("webpack-bundle-analyzer", "4.5.0"))
                implementation(devNpm("qrcode", "1.5.0"))
                implementation(devNpm("sass", "1.51.0"))
                implementation(devNpm("sass-loader", "12.6.0"))
                implementation(devNpm("postcss-loader", "6.2.1"))
                implementation(devNpm("autoprefixer", "10.4.7"))
            }
        }
    }
}
