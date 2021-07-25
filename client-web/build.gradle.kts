import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    js(IR) {
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
                    proxy = mutableMapOf(
                        "/api/*" to mapOf<String, Any>(
                            "target" to "http://localhost:8888",
                            "ws" to true
                        )
                    ),
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
                useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
                useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
            }
        }
        named("jsMain") {
            dependencies {
                implementation(projects.client)
                implementation(libs.coroutines.core)
                implementation(libs.ktor.client.js)

                implementation(compose.web.core)
                implementation(compose.web.widgets)
                implementation(compose.runtime)
                implementation("app.softwork:routing-compose")

                implementation(libs.kotlinjs.extensions)
                implementation(npm("bootstrap", "5.0.2"))
                implementation(npm("bootstrap-icons", "1.5.0"))
                implementation(devNpm("file-loader", "6.2.0"))
                implementation(devNpm("webpack-bundle-analyzer", "4.4.2"))
            }
        }
    }
}
