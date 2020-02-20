import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()

            runTask {
                outputFileName = "main.bundle.js"
                devtool = "eval-cheap-module-source-map"
                devServer = KotlinWebpackConfig.DevServer(
                    open = false,
                    port = 3000,
                    proxy = mutableMapOf(
                        "/api/*" to mapOf(
                            "target" to "http://localhost:8888",
                            "ws" to true
                        )
                    ),
                    static = mutableListOf("$buildDir/processedResources/js/main")
                )
            }
            webpackTask {
                outputFileName = "main.bundle.js"
                devtool = "eval-cheap-module-source-map"
            }

            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets["main"].apply {
        dependencies {
            implementation(projects.client)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.js)

            implementation(libs.kvision.core)
            implementation(libs.kvision.routing.navigo)
            implementation(libs.kvision.bootstrap.core)
            //implementation("io.kvision:kvision-bootstrap-css:$KVISION_VERSION")
            //implementation("io.kvision:kvision-bootstrap-datetime:$KVISION_VERSION")
            implementation(libs.kvision.bootstrap.select)
            //implementation("io.kvision:kvision-bootstrap-spinner:$KVISION_VERSION")
            //implementation("io.kvision:kvision-bootstrap-upload:$KVISION_VERSION")
            //implementation("io.kvision:kvision-bootstrap-dialog:$KVISION_VERSION")
            implementation(libs.kvision.fontawesome)
            //implementation("io.kvision:kvision-richtext:$KVISION_VERSION")
            //implementation("io.kvision:kvision-handlebars:$KVISION_VERSION")
            //implementation("io.kvision:kvision-tabulator:$KVISION_VERSION")
            //implementation("io.kvision:kvision-pace:$KVISION_VERSION")
            //implementation("io.kvision:kvision-moment:$KVISION_VERSION")
            implementation(libs.kvision.datacontainer)
            implementation(libs.kvision.toast)
            implementation(libs.kvision.eventFlow)
            implementation(npm("jstree", "3.3.10"))
            implementation(devNpm("file-loader", "6.2.0"))
            implementation(devNpm("url-loader", "4.1.1"))
        }
    }
}
