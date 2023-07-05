plugins {
    id("multiplatform-lib")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

apply(plugin = "kotlinx-atomicfu")

dependencies {
    add("kspCommonMainMetadata", libs.mobiuskt.updateGenerator)
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

tasks.findByName("lintKotlinCommonMain")?.apply {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.findByName("formatKotlinCommonMain")?.apply {
    dependsOn("kspCommonMainKotlinMetadata")
}

kotlin {
    configureFramework {
        baseName = "AnyStreamCore"
        export(projects.anystreamDataModels)
        export(libs.mobiuskt.core)
        export(libs.mobiuskt.coroutines)
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/$name/kotlin")
            dependencies {
                api(projects.anystreamDataModels)
                implementation(libs.atomicfu)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.client.logging)

                api(libs.koin.core)
                api(libs.objectstore.core)
                api(libs.objectstore.json)
                api(libs.coroutines.core)
                api(libs.mobiuskt.core)
                api(libs.mobiuskt.extras)
                api(libs.mobiuskt.coroutines)
                implementation(libs.mobiuskt.updateGenerator.api)

                api(libs.ktor.client.core)
                api(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.mobiuskt.test)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependencies {
                    implementation(libs.ktor.client.cio)
                    implementation(libs.objectstore.secure)
                }
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.objectstore.secure)
            }
        }

        val jvmMain by getting  {
            dependencies {
                implementation(libs.objectstore.fs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}
