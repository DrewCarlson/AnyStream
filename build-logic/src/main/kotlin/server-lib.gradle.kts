import gradle.kotlin.dsl.accessors._6b241bd533b162fe31c6478cde91b5b0.spotless
import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
}

kotlin {
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

    val libsServer = extensions.getByType<VersionCatalogsExtension>().named("libsServer")
    test {
        dependencies {
            implementation(libsServer.findLibrary("mockk").get())
        }
    }
}

afterEvaluate {
    spotless {
        kotlin {
            target("**/**.kt")
            licenseHeaderFile(rootDir.resolve("licenseHeader.txt"))
            val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
            //ktlint(libsCommon.findVersion("ktlint").get().requiredVersion)
            //    .setEditorConfigPath(rootDir.resolve(".editorconfig"))
        }
    }
}
