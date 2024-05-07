import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("multiplatform-lib")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs(
                "src",
                buildDir.resolve("generated-src/jooq/main")
            )
            dependencies {
                implementation(libsCommon.serialization.core)
                implementation(libsCommon.serialization.json)
                api(libsCommon.datetime)
                api(libsServer.qbittorrent.models)
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    inputs.files(fileTree(layout.buildDirectory.dir("generated-src")).files)
    dependsOn(":server:db-models:movePojos")
}
