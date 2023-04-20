@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
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

sourceSets {
    main { java.srcDir(buildDir.resolve("generated/ksp/$name/kotlin")) }
}

dependencies {
    ksp(projects.libs.sqlGenerator)
    implementation(projects.libs.sqlGeneratorApi)
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

tasks.named("formatKotlinMain").configure {
    dependsOn("kspKotlin")
}

tasks.named("lintKotlinMain").configure {
    dependsOn("kspKotlin")
}
