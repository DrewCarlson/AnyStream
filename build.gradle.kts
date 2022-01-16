import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

@Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")
plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.composejb) apply false
    alias(libs.plugins.shadowjar) apply false
    alias(libs.plugins.ksp) apply false
}

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.4")
    }
}

allprojects {
    yarn.lockFileDirectory = rootDir.resolve("gradle/kotlin-js-store")

    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

val rootIosTestTask = rootProject.tasks.create("runIosX64Tests") {
    doLast { shutdownSimulator() }
}

tasks.create("shutdownSimulator") {
    doFirst { shutdownSimulator() }
}

subprojects {
    afterEvaluate {
        val kotlin = extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()

        // Silence release sourceset warning for every module that targets Android
        kotlin?.sourceSets?.removeAll { it.name == "androidAndroidTestRelease" }

        // Setup alternative test iOS test configurations
        kotlin?.targets
            ?.takeIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
            ?.also { tasks.findByName("allTests")?.dependsOn(rootIosTestTask) }
            ?.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>()
            ?.filter { it.name == "iosX64" && it.publishable }
            ?.forEach { target ->
                val testExecutable = target.binaries.getTest("debug")
                val executable = testExecutable.outputFile
                tasks.create("runIosX64Tests") {
                    rootIosTestTask.dependsOn(this)
                    dependsOn(testExecutable.linkTaskName)
                    doFirst {
                        if (executable.exists()) {
                            runSimulatorTests(executable.absolutePath)
                        }
                    }
                }
            }
    }
}
