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
    implementation(projects.anystreamServer.serverShared)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)

    implementation(libs.logback)

    implementation(libs.flyway.core)
    implementation(libs.fastObjectPool)
    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
