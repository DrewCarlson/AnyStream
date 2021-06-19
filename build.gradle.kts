plugins {
    kotlin("multiplatform") version "1.5.10" apply false
    kotlin("jvm") version "1.5.10" apply false
    kotlin("plugin.serialization") version "1.5.10" apply false
    id("org.jetbrains.compose") version "0.5.0-build225" apply false
}

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.0-beta04")
    }
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers/")
    }
}
