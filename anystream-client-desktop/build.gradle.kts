import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":anystream-client-ui"))
            }
        }
    }
}

compose {
    kotlinCompilerPlugin.set(libs.jbcompose.compiler.get().toString())
    desktop {
        application {
            mainClass = "MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "AnystreamDesktopApplication"
                packageVersion = "1.0.0"
            }
        }
    }
}
