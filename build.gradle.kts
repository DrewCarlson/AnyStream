import com.diffplug.gradle.spotless.SpotlessApply
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.composejb) apply false
    alias(libs.plugins.shadowjar) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.javafx) apply false
    alias(libs.plugins.downloadPlugin) apply false
}

buildscript {
    repositories {
        maven("https://repo1.maven.org/maven2/")
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath(libs.agp)
        classpath(libs.atomicfu.plugin) {
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

    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/**.kt")
            licenseHeaderFile(rootDir.resolve("licenseHeader.txt"))
        }
    }

    apply(plugin = "org.jmailen.kotlinter")

    configure<org.jmailen.gradle.kotlinter.KotlinterExtension> {
    }

    val generatedDir = File(buildDir, "generated").absolutePath
    tasks.withType<ConfigurableKtLintTask> {
        exclude { it.file.absolutePath.startsWith(generatedDir) }
    }

    tasks.withType<SpotlessApply> {
        dependsOn("formatKotlin")
    }
}
