import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

tasks.withType<KotlinCompile>().all {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    
                    
                    
                    "-Xopt-in=kotlin.RequiresOptIn",
                )
            }
        }
    }
}

dependencies {
    implementation(projects.anystreamDataModels)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)

    implementation(libs.logback)

    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
