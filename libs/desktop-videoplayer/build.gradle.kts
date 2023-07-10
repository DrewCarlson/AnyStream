plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
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
            }
        }
    }
}

compose {
    kotlinCompilerPlugin.set(libs.jbcompose.compiler.get().toString())
}
