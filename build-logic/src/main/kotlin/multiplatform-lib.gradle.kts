import com.android.build.gradle.LibraryExtension
import dev.zacsweers.redacted.gradle.RedactedPluginExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    id("com.diffplug.spotless")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    id("dev.zacsweers.redacted")
    id("dev.drewhamilton.poko")
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 36
        defaultConfig {
            minSdk = 23
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        namespace = "anystream.${project.name.replace("-", "")}"
        compileOptions {
            sourceCompatibility = JAVA_TARGET_ANDROID
            targetCompatibility = JAVA_TARGET_ANDROID
        }
        packaging {
            resources.excludes.add("META-INF/versions/*/*.bin")
        }
    }
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
    extensions.getByType<RedactedPluginExtension>().apply {
        replacementString.set("***")
    }
}

val enableJsTarget = project.name != "ui"

kotlin {
    jvmToolchain(JAVA_TARGET.majorVersion.toInt())
    if (enableJsTarget) {
        js(IR) {
            browser {
                testTask {
                    useKarma {
                        useFirefoxHeadless()
                    }
                }
            }
        }
    }
    jvm()
    if (hasAndroidSdk) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JVM_TARGET_ANDROID)
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    applyDefaultHierarchyTemplate()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }

        val libsCommon = project.extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
        val libsAndroid = project.extensions.getByType<VersionCatalogsExtension>().named("libsAndroid")

        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/$name/kotlin")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libsCommon.findLibrary("redacted-annotations").get())
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
    }
}

afterEvaluate {
    if (extensions.findByName("ksp") != null) {
        tasks.withType<KotlinCompilationTask<*>>().all {
            if (name != "kspCommonMainKotlinMetadata") {
                dependsOn("kspCommonMainKotlinMetadata")
            }
        }
    }
}
