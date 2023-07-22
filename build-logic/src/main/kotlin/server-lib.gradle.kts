import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("jvm")
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

