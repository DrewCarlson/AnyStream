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

kotlin {
    jvmToolchain(JAVA_TARGET.majorVersion.toInt())
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }
    jvm()
    if (hasAndroidSdk) {
        android {
            compilations.all {
                kotlinOptions {
                    jvmTarget = JAVA_TARGET.majorVersion
                }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

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
                    implementation(libs.findLibrary("androidx-core-ktx").get())
                }
            }

            val androidUnitTest by getting {
                dependencies {
                    implementation(libs.findLibrary("androidx-test-runner").get())
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

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
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

if (tasks.any { it.name == "kspCommonMainKotlinMetadata" }) {
    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
        if (name != "kspCommonMainKotlinMetadata") {
            dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}

