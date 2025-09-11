import ExecAsyncHandle.Companion.execAsync
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import javax.inject.Inject

abstract class KotlinJsViteExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory,
) {
    /**
     * Enable or disable all kotlin-js-vite functionality and tasks.
     */
    var enabled = objects.property<Boolean>().convention(true)

    /**
     * Vite version to use if auto installed (enabled default).
     */
    var viteVersion: String = "6.3.6"

    /**
     * Enable or disable automatic Vite dependency handling, enabled by default.
     */
    var addViteDependency: Boolean = true

    /**
     *  Custom environment variables to pass to Vite dev server.
     */
    var environment: MutableMap<String, String> = mutableMapOf()

    /**
     * Optional path to a vite.config.js file in the project.
     * Defaults to projectDir/vite.config.js if present.
     */
    var configFilePath: RegularFileProperty = objects.fileProperty()
        .convention { project.file("vite.config.js") }
}

open class KotlinJsVite : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("kotlinJsVite", KotlinJsViteExtension::class.java)
        if (!extension.enabled.get()) {
            return
        }
        val kmp = checkNotNull(project.extensions.findByType<KotlinMultiplatformExtension>()) {
            "kotlin-js-vite requires the Kotlin Multiplatform Gradle plugin to be applied."
        }

        project.afterEvaluate {
            extension.configureViteTasks(project, kmp.targets.filterIsInstance<KotlinJsIrTarget>())
        }
    }

    private fun KotlinJsViteExtension.configureViteTasks(project: Project, targets: List<KotlinJsIrTarget>) {
        targets.forEach { target ->
            val mainCompilation = target.compilations.getByName("main")
            if (addViteDependency) {
                mainCompilation.dependencies {
                    implementation(devNpm("vite", viteVersion))
                }
            }
            mainCompilation.binaries.executable().forEach { binary ->
                if (target.isBrowserConfigured) {
                    configureViteTask<ViteServeTask>(project, target.name, mainCompilation, binary)
                    configureViteTask<ViteDistTask>(project, target.name, mainCompilation, binary)
                }
            }
        }
    }

    private inline fun <reified T : BaseViteTask> KotlinJsViteExtension.configureViteTask(
        project: Project,
        targetName: String,
        compilation: KotlinJsIrCompilation,
        binary: JsIrBinary,
    ) {
        val mode = when (binary.mode) {
            KotlinJsBinaryMode.DEVELOPMENT -> "Development"
            KotlinJsBinaryMode.PRODUCTION -> "Production"
        }
        val taskNameSuffix = if (T::class == ViteServeTask::class) "Serve" else "Dist"
        project.tasks.register<T>("${targetName}Browser${mode}${taskNameSuffix}") {
            description = "Build '${project.path}:${targetName}' with Vite."

            val npm = compilation.npmProject
            workingDir.set(npm.dist)
            viteScript.set(npm.nodeJsRoot.rootPackageDirectory.map { it.file("node_modules/vite/bin/vite.js") })
            nodeExecutable.set(npm.nodeJs.executable)
            configFile.set(configFilePath.get())
            env.set(environment)
            if (this is ViteDistTask) {
                publicDir.set(project.layout.buildDirectory.dir("processedResources/${targetName}/${compilation.name}/public"))
                outputDir.set(project.layout.buildDirectory.dir("vite/${targetName}/${binary.name}"))
            }

            dependsOn(
                npm.nodeJsRoot.npmInstallTaskProvider,
                binary.linkSyncTask,
            )
        }
    }
}

abstract class BaseViteTask(
    @Internal
    protected val execOperations: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val nodeExecutable: Property<String>

    @get:InputFiles
    abstract val viteScript: RegularFileProperty

    @get:Optional
    @get:InputFiles
    abstract val configFile: RegularFileProperty

    @get:InputDirectory
    abstract val workingDir: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    abstract val publicDir: DirectoryProperty

    @get:Input
    abstract val env: MapProperty<String, String>
}

/**
 * Task to run the Vite dev server against the compiled Kotlin/JS browser output.
 */
abstract class ViteServeTask @Inject constructor(
    execOperations: ExecOperations
) : BaseViteTask(execOperations) {

    private val isContinuous = project.gradle.startParameter.isContinuous

    init {
        group = "kotlin browser"
    }

    @TaskAction
    fun doExecute() {
        val runner = ViteProcessRunner(
            execOperations = execOperations,
            configure = {
                workingDir(this@ViteServeTask.workingDir.asFile.get())
                executable(nodeExecutable.get())
                args(
                    viteScript.asFile.get().absolutePath,
                    "--config",
                    configFile.asFile.orNull?.absolutePath ?: "vite.config.js",
                )

                environment(env.get())
            }
        )

        if (isContinuous) {
            val deploymentRegistry = project.serviceOf<DeploymentRegistry>()
            val deploymentHandle = deploymentRegistry.get("vite", ProcessDeploymentHandle::class.java)
            if (deploymentHandle == null) {
                logger.lifecycle("Starting Vite server in continuous mode")
                deploymentRegistry.start(
                    "vite",
                    DeploymentRegistry.ChangeBehavior.BLOCK,
                    ProcessDeploymentHandle::class.java,
                    runner,
                )
            }
        } else {
            runner.start().waitForResult()
        }
    }
}

abstract class ViteDistTask @Inject constructor(
    execOperations: ExecOperations
) : BaseViteTask(execOperations) {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "kotlin browser"
    }

    @TaskAction
    fun doExecute() {
        val runner = ViteProcessRunner(
            execOperations = execOperations,
            configure = {
                workingDir(this@ViteDistTask.workingDir.asFile.get())
                executable(nodeExecutable.get())
                args(
                    viteScript.asFile.get().absolutePath,
                    "build",
                    "--config",
                    configFile.asFile.orNull?.absolutePath ?: "vite.config.js",
                    "--outDir",
                    outputDir.asFile.get(),
                    "--emptyOutDir",
                )

                environment(env.get() + ("VITE_PUBLIC_DIR" to publicDir.asFile.get().absolutePath))
            }
        )

        runner.start().waitForResult()
    }
}

internal class ViteProcessRunner(
    private val execOperations: ExecOperations,
    private val configure: ExecSpec.() -> Unit,
) {
    fun start(): ExecAsyncHandle {
        val handle = execOperations.execAsync("Vite") { exec ->
            configure(exec)
        }
        handle.start()
        return handle
    }
}

internal open class ProcessDeploymentHandle @Inject constructor(
    private val runner: ViteProcessRunner,
) : DeploymentHandle {
    private var process: ExecAsyncHandle? = null

    override fun isRunning(): Boolean = process?.isAlive() == true

    override fun start(deployment: Deployment) {
        process = runner.start()
    }

    override fun stop() {
        process?.abort()
    }
}