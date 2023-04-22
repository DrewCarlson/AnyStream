import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("jvm")
}

kotlin {
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

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    test {
        dependencies {
            implementation(libs.findLibrary("mockk").get())
        }
    }
}

