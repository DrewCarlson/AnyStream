plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.apply {
                useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
                useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
            }
        }
        val commonMain by getting {
            kotlin.srcDirs("src")
            dependencies {
                implementation(projects.dataModels)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.json)
                implementation(libs.ktor.client.serialization)
                implementation(libs.korio)
            }
        }
        val commonTest by getting {
            kotlin.srcDirs("test")
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":data-models"))
                implementation(libs.ktor.client.okhttp)
            }
        }

        val jvmTest by getting {
            kotlin.srcDirs("testJvm")
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(project(":data-models"))
                implementation(libs.ktor.client.js)
            }
        }

        val jsTest by getting {
            kotlin.srcDirs("testJs")
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
