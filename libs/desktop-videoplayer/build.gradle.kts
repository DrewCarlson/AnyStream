plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "20"
    modules("javafx.swing", "javafx.controls")
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.common)
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
