import gradle.kotlin.dsl.accessors._6b241bd533b162fe31c6478cde91b5b0.spotless
import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
    kotlin("plugin.serialization")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.io.path.ExperimentalPathApi")
    }
    jvmToolchain(JAVA_TARGET.majorVersion.toInt())
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = JAVA_TARGET.majorVersion
            }
        }
    }
}

java {
    sourceCompatibility = JAVA_TARGET
    targetCompatibility = JAVA_TARGET
}

sourceSets {
    main { java.srcDir(buildDir.resolve("generated/ksp/$name/kotlin")) }

    val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
    val libsServer = extensions.getByType<VersionCatalogsExtension>().named("libsServer")
    test {
        dependencies {
            implementation(libsServer.findLibrary("mockk").get())

            implementation(libsCommon.findLibrary("kotest-runner-junit5").get())
            implementation(libsCommon.findLibrary("kotest-assertions-core").get())
            implementation(libsCommon.findLibrary("kotest-property").get())

            implementation(kotlin("test"))
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
