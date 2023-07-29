import com.android.build.gradle.LibraryExtension
import org.gradle.kotlin.dsl.*

plugins {
    kotlin("multiplatform")
}

apply(plugin = "kotlinx-atomicfu")

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 33
        defaultConfig {
            minSdk = 23
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        namespace = "anystream.${project.name.substringAfter("anystream-").replace("-", "")}"
        compileOptions {
            sourceCompatibility = JAVA_TARGET
            targetCompatibility = JAVA_TARGET
        }
    }
}

val enableJsTarget = project.name != "anystream-client-ui"

kotlin {
    jvmToolchain(JAVA_TARGET.majorVersion.toInt())
    if (enableJsTarget) {
        js(IR) {
            browser {
                testTask(Action {
                    useKarma {
                        useFirefoxHeadless()
                    }
                })
            }
        }
    }
    jvm()
    if (hasAndroidSdk) {
        androidTarget {
            compilations.all {
                kotlinOptions {
                    jvmTarget = JAVA_TARGET.majorVersion
                }
            }
        }
    }
    // Note: Workaround build script errors when configuring frameworks
    // "Could not create task of type 'KotlinNativeLink'."
    if (project.name != "anystream-client-ui") {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
    }

    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }

        val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
        val libsAndroid = extensions.getByType<VersionCatalogsExtension>().named("libsAndroid")

        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/$name/kotlin")
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (hasAndroidSdk) {
            val androidMain by getting {
                dependencies {
                    implementation(libsAndroid.findLibrary("androidx-core-ktx").get())
                }
            }

            val androidUnitTest by getting {
                dependencies {
                    implementation(libsAndroid.findLibrary("androidx-test-runner").get())
                    implementation(kotlin("test"))
                    implementation(kotlin("test-junit"))
                }
            }
        }

        sourceSets.findByName("jvmTest")?.apply {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        if (enableJsTarget) {
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }

        // Note: Workaround build script errors when configuring frameworks
        // "Could not create task of type 'KotlinNativeLink'."
        if (project.name != "anystream-client-ui") {
            configureCommonIosSourceSets()

            configure(
                listOf(
                    iosArm64(),
                    iosSimulatorArm64(),
                    iosX64(),
                ),
            ) {
                compilations.configureEach {
                    compilerOptions.configure {
                        freeCompilerArgs.add("-Xallocator=custom")
                    }
                }
            }
        }
    }
}

if (tasks.any { it.name == "kspCommonMainKotlinMetadata" }) {
    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
        if (name != "kspCommonMainKotlinMetadata") {
            dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}
