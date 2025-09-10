import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    alias(libsCommon.plugins.jsPlainObjects)
    alias(libsCommon.plugins.spotless)
    alias(libsCommon.plugins.serialization)
}

val localProperties = gradleLocalProperties(rootDir, providers)

kotlin {
    js(IR) {
        useCommonJs()
        browser {
            binaries.executable()
            commonWebpackConfig {
                cssSupport {
                    enabled.set(false)
                }
            }
            runTask {
                val anystreamUrl = localProperties.getProperty("anystream.serverUrl", "http://localhost:8888")
                mainOutputFileName.set("main.bundle.js")
                devtool = "source-map"
                sourceMaps = true
                devServerProperty.set(
                    DevServer(
                        open = false,
                        port = 3000,
                        static = mutableListOf(
                            layout.buildDirectory.dir("processedResources/js/main").get().asFile.absolutePath
                        ),
                        proxy = mutableListOf(
                            DevServer.Proxy(
                                target = anystreamUrl,
                                secure = anystreamUrl.startsWith("https"),
                                context = mutableListOf("/api"),
                                changeOrigin = true,
                            )
                        )
                    )
                )
            }
            webpackTask {
                mainOutputFileName.set("main.bundle.js")
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
                implementation(devNpm("postcss-import", "16.1.1"))
                implementation(devNpm("bootstrap-icons", "1.13.1"))
                implementation(devNpm("tailwindcss", "4.1.13"))
                implementation(devNpm("@tailwindcss/postcss", "4.1.13"))
                implementation(devNpm("@fontsource/open-sans", "5.2.6"))
                implementation(devNpm("@popperjs/core", "2.11.8"))
                implementation(devNpm("video.js", "8.6.1"))
                implementation(devNpm("@videojs/http-streaming", "3.9.1"))
                implementation(devNpm("mpd-parser", "1.3.1"))
                implementation(devNpm("mux.js", "6.3.0"))
                implementation(devNpm("webworkify-webpack-dropin", "1.1.9"))
                implementation(devNpm("file-loader", "6.2.0"))
                implementation(devNpm("webpack-bundle-analyzer", "4.10.2"))
                implementation(devNpm("qrcode", "1.5.4"))
                implementation(devNpm("style-loader", "4.0.0"))
                implementation(devNpm("css-loader", "7.1.2"))
                implementation(devNpm("postcss-loader", "8.2.0"))
                implementation(devNpm("postcss", "8.5.6"))
                implementation(devNpm("autoprefixer", "10.4.21"))
                implementation(devNpm("terser-webpack-plugin", "5.3.14"))
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
