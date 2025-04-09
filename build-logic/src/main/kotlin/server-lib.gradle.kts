import dev.zacsweers.redacted.gradle.RedactedPluginExtension
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
    kotlin("plugin.serialization")
    id("dev.zacsweers.redacted")
    id("dev.drewhamilton.poko")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.io.path.ExperimentalPathApi")
    }
    jvmToolchain(JAVA_TARGET.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JAVA_TARGET
    targetCompatibility = JAVA_TARGET
}

extensions.getByType<RedactedPluginExtension>().apply {
    replacementString.set("***")
}

sourceSets {
    main { java.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin")) }

    val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
    val libsServer = extensions.getByType<VersionCatalogsExtension>().named("libsServer")
    test {
        dependencies {
            testImplementation(libsServer.findLibrary("mockk").get())

            testImplementation(libsCommon.findLibrary("kotest-runner-junit5").get())
            testImplementation(libsCommon.findLibrary("kotest-assertions-core").get())
            testImplementation(libsCommon.findLibrary("kotest-property").get())

            testImplementation(kotlin("test"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

afterEvaluate {
    spotless {
        kotlin {
            target("**/**.kt")
            licenseHeaderFile(rootDir.resolve("licenseHeader.txt"))
            //val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
            //ktlint(libsCommon.findVersion("ktlint").get().requiredVersion)
            //    .setEditorConfigPath(rootDir.resolve(".editorconfig"))
        }
    }
}
