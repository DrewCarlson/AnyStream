import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
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
        useEsModules()
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
                implementation(devNpm("vite", "6.3.6"))
                implementation(devNpm("esbuild", "0.25.9"))
                implementation(devNpm("esbuild-loader", "4.3.0"))
                implementation(devNpm("bootstrap-icons", "1.13.1"))
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
            }
        }
    }
}

tasks.register<Task>("viteServe") {
    dependsOn(
        rootProject.tasks["kotlinNpmInstall"],
        "jsDevelopmentExecutableCompileSync",
    )
    group = "build"
    description = "Build and serve the web client with vite."

    val nodeJs = rootProject.extensions.getByType<NodeJsRootExtension>()
    val nodeExecutable = nodeJs.requireConfigured().executable

    inputs.dir(project.layout.projectDirectory.dir("src"))
    doFirst {
        val processBuilder = ProcessBuilder(
            nodeExecutable,
            rootProject.layout.buildDirectory.file("js/node_modules/vite/bin/vite.js").get().asFile.absolutePath,
            "--config",
            project.layout.projectDirectory.file("vite.config.js").asFile.absolutePath,
            "--host",
        )
            .directory(rootProject.layout.buildDirectory.dir("js/packages/AnyStream-client-web/kotlin").get().asFile)
            .apply { environment()["ANYSTREAM_SERVER_URL"] = localProperties.getProperty("anystream.serverUrl", "http://localhost:8888") }

        if (project.gradle.startParameter.isContinuous) {
            val deploymentRegistry = serviceOf<DeploymentRegistry>()
            val deploymentHandle = deploymentRegistry.get("vite", ProcessDeploymentHandle::class.java)

            if (deploymentHandle == null) {
                deploymentRegistry.start(
                    "vite",
                    DeploymentRegistry.ChangeBehavior.BLOCK,
                    ProcessDeploymentHandle::class.java,
                    processBuilder
                )
            }
        } else {
            processBuilder.start()
        }
    }

}

@OptIn(DelicateCoroutinesApi::class)
open class ProcessDeploymentHandle @Inject constructor(
    private val processBuilder: ProcessBuilder,
) : DeploymentHandle {
    private var process: Process? = null
    override fun isRunning(): Boolean {
        return process != null
    }

    override fun start(deployment: Deployment) {
        process = processBuilder.start()

        GlobalScope.launch {
            try {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lines().forEach { line ->
                        println(line)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun stop() {
        process?.destroyForcibly()
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
