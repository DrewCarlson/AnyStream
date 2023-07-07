plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.swing", "javafx.controls")
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        all {
            explicitApi()
        }
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(projects.anystreamClientCore)
                implementation(libs.vlcj)
                implementation(libs.vlcj.javafx)
            }
        }
    }
}

compose {
    kotlinCompilerPlugin.set(libs.jbcompose.compiler.get().toString())
}
