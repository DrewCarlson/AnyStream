import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import kotlin.apply

plugins {
    id("multiplatform-lib")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

compose {
    resources {
        publicResClass = true
        generateResClass = always
    }
}

kotlin {

    if (hasAndroidSdk) {
        (targets.getByName("android") as KotlinMultiplatformAndroidLibraryTarget).apply {
            androidResources.enable = true
        }
    }

    configure(
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        )
    ) {
        val main by compilations.getting
        main.cinterops.create("observer")
    }
    sourceSets {
        all {
            languageSettings {
                optIn("kt.mobius.gen.ExperimentalCodegenApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kt.mobius.compose.ExperimentalMobiusktComposeApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(projects.client.core)
                api(projects.client.dataModels)
                api(projects.client.presentation)
                implementation(libsClient.coil.compose)
                implementation(libsClient.coil.ktor)
                implementation(libsClient.koin.compose)
                implementation(libsClient.haze.core)
                implementation(libsClient.haze.materials)
                implementation(libsClient.compose.lifecycle)
                implementation(libsClient.compose.bundle)
                implementation(libsClient.compose.viewmodel)
                implementation(libsClient.compose.backhandler)
                implementation(libsClient.compose.runtime)
                implementation(libsClient.compose.foundationjb)
                implementation(libsClient.compose.materialjb)
                implementation(libsClient.compose.materialjb.icons)
                api(libsClient.compose.resources)
            }
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libsClient.zxing.core)
            }
        }

        if (hasAndroidSdk) {
            named("androidMain") {
                dependsOn(jvmCommonMain)
                dependencies {
                    implementation(libsClient.compose.ui.tooling)
                    implementation(libsClient.compose.ui.tooling.preview)
                    implementation(libsClient.bundles.camerax)
                    implementation(libsClient.bundles.media3)
                    implementation(libsAndroid.androidx.activity.ktx)
                    implementation(libsAndroid.androidx.activity.compose)
                }
            }
        }

        named("jvmMain") {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libsClient.compose.desktop)
                implementation(libsClient.vlcj)
            }
        }
    }
}
