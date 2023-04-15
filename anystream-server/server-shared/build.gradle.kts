plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(projects.anystreamDataModels)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.ktor.client.core)

    implementation(libs.bouncyCastle)

    implementation(libs.logback)

    implementation(libs.jdbi.core)

    implementation(libs.jaffree)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
