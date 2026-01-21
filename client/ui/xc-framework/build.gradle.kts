plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    configure(
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        )
    ) {
        binaries {
            framework {
                baseName = "AnyStreamCore"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal",
                    "-linker-option", "-framework", "-linker-option", "CoreText",
                    "-linker-option", "-framework", "-linker-option", "CoreGraphics",
                )
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.client.ui)
                implementation(libsClient.compose.runtime)
                implementation(libsClient.compose.foundationjb)
                implementation(libsClient.compose.materialjb)
                implementation(libsClient.compose.ui)
            }
        }
    }
}
