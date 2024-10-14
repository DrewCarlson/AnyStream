plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libsCommon.agp)
    implementation(libsCommon.kotlin.gradle.plugin)
    implementation(libsCommon.ksp)
    implementation(libsCommon.serialization.plugin)
    implementation(libsCommon.spotless.plugin)
    implementation(libsCommon.kotlin.gradle.conventions)
    implementation(libsCommon.atomicfu.plugin)
}

repositories {
    maven("https://repo1.maven.org/maven2/")
    gradlePluginPortal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
}