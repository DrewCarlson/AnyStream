import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    alias(libsCommon.plugins.multiplatform) apply false
    alias(libsCommon.plugins.jvm) apply false
    alias(libsCommon.plugins.serialization) apply false
    alias(libsClient.plugins.composejb) apply false
    alias(libsServer.plugins.shadowjar) apply false
    alias(libsCommon.plugins.ksp) apply false
    alias(libsCommon.plugins.kover)
    alias(libsCommon.plugins.downloadPlugin) apply false
}

buildscript {
    repositories {
        maven("https://repo1.maven.org/maven2/")
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath(libsCommon.agp)
        classpath(libsCommon.atomicfu.plugin) {
            exclude("org.jetbrains.kotlin", "kotlin-gradle-plugin-api")
        }
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
        // maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }

    System.getenv("GITHUB_REF")?.let { ref ->
        if (ref.startsWith("refs/tags/v")) {
            version = ref.substringAfterLast("refs/tags/v")
        }
    }
}

val moduleGroupFolders = listOf("libs", "anystream-server")

subprojects {
    if (moduleGroupFolders.contains(name)) {
        return@subprojects
    }

    apply(plugin = "org.jetbrains.kotlinx.kover")
    kover {}
}
