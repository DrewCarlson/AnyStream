import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    js(IR) {
        useCommonJs()
        browser {
            binaries.executable()
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            runTask {
                outputFileName = "main.bundle.js"
                devtool = "eval-cheap-module-source-map"
                devServer = DevServer(
                    open = false,
                    port = 3000,
                    static = mutableListOf("$buildDir/processedResources/js/main")
                )
            }
            webpackTask {
                outputFileName = "main.bundle.js"
                //devtool = "cheap-module-eval-source-map"
            }
        }
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.RequiresOptIn")
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
                implementation(devNpm("bootstrap-icons", "1.7.2"))
                implementation(devNpm("@fontsource/open-sans", "4.5.0"))
                implementation(devNpm("@popperjs/core", "2.11.0"))
                implementation(devNpm("video.js", "7.17.0"))
                implementation(devNpm("webworkify-webpack-dropin", "1.1.9"))
                implementation(devNpm("file-loader", "6.2.0"))
                implementation(devNpm("webpack-bundle-analyzer", "4.4.2"))
                implementation(devNpm("qrcode", "1.5.0"))
            }
        }
    }
}

// Override webpack version to fix dev-server error: https://youtrack.jetbrains.com/issue/KT-49124
rootProject.extensions
    .configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
        versions.webpackCli.version = "4.9.1"
    }
