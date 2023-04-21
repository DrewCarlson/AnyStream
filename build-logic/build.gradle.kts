plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.agp)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp)
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.8.0")
}

repositories {
    maven("https://repo1.maven.org/maven2/")
    gradlePluginPortal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
}