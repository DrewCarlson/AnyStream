import com.android.build.gradle.LibraryExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

apply(plugin = "kotlinx-atomicfu")

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 33
        defaultConfig {
            minSdk = 23
            targetSdk = 33
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        namespace = "anystream.client"
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

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
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }
    // jvm()
    if (hasAndroidSdk) {
        android {
            compilations.all {
                kotlinOptions {
                    jvmTarget = JavaVersion.VERSION_11.majorVersion
                }
            }
        }
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries {
            framework {
                baseName = "AnyStreamCore"
                export(projects.anystreamDataModels)
                export(libs.mobiuskt.core)
                export(libs.mobiuskt.coroutines)
            }
        }
    }

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }
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
                    implementation(libs.androidx.core.ktx)
                    implementation(libs.ktor.client.cio)
                    implementation(libs.objectstore.secure)
                }
            }

            val androidUnitTest by getting {
                dependencies {
                    implementation(libs.androidx.test.runner)
                    implementation(kotlin("test"))
                    implementation(kotlin("test-junit"))
                }
            }
        }

        /*val jvmMain by getting {
            dependencies {
                implementation(libs.objectstore.fs)
                implementation(libs.ktor.client.cio)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }*/

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.objectstore.secure)
            }
        }

        val iosTest by creating {
            dependsOn(commonTest)
        }

        sourceSets.filter { sourceSet ->
            sourceSet.name.run {
                startsWith("iosX64") ||
                        startsWith("iosArm") ||
                        startsWith("iosSimulator")
            }
        }.forEach { sourceSet ->
            if (sourceSet.name.endsWith("Main")) {
                sourceSet.dependsOn(iosMain)
            } else {
                sourceSet.dependsOn(iosTest)
            }
        }
    }
}
