plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.serialization)
}
