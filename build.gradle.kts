import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    alias(libsCommon.plugins.multiplatform) apply false
    alias(libsCommon.plugins.jvm) apply false
    alias(libsCommon.plugins.serialization) apply false
    alias(libsCommon.plugins.compose) apply false
    alias(libsClient.plugins.composejb) apply false
    alias(libsServer.plugins.shadowjar) apply false
    alias(libsCommon.plugins.kover)
    alias(libsCommon.plugins.downloadPlugin) apply false
    alias(libsCommon.plugins.redacted) apply false
    alias(libsCommon.plugins.poko) apply false
    alias(libsCommon.plugins.kotlinVite) apply false
    alias(libsCommon.plugins.spotless) apply false
}

buildscript {
    repositories {
        maven("https://repo1.maven.org/maven2/")
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath(libsCommon.agp)
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencyLocking {
        lockAllConfigurations()
        lockFile = file("gradle/gradle-buildscript.lockfile")
    }
}

allprojects {
    plugins.withType<NodeJsRootPlugin> {
        the<YarnRootExtension>().lockFileDirectory = rootDir.resolve("gradle/kotlin-js-store")
    }

    repositories {
        maven("https://repo1.maven.org/maven2/")
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }

    System.getenv("GITHUB_REF")?.let { ref ->
        if (ref.startsWith("refs/tags/v")) {
            version = ref.substringAfterLast("refs/tags/v")
        }
    }
}

kover {
    merge {
        subprojects {
            it.path.startsWith(":server:") ||
                it.path.startsWith(":client:")
        }
    }
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        val lockFileName = if (project == rootProject) {
            "gradle-root.lockfile"
        } else {
            "gradle-${project.path.removePrefix(":").replace(":", "-")}.lockfile"
        }
        lockFile = rootProject.file("gradle/lockfiles/$lockFileName")

        // Cannot be easily validated since gradle fails on locked dependencies
        // that are missing from configuration on use.
        ignoredDependencies.addAll("org.jetbrains.compose.desktop:desktop-jvm-*")
        ignoredDependencies.addAll("org.jetbrains.skiko:skiko-awt-runtime-*")
    }
}

tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "$path must be run from the command line with the `--write-locks` flag"
        }
    }
    doLast {
        allprojects.forEach { proj ->
            proj.configurations
                .filter { it.isCanBeResolved }
                .forEach {
                    it.incoming.resolutionResult
                        .rootComponent
                        .get()
                }
        }
    }
}
