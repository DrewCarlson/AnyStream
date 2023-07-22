@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("server-lib")
    alias(libsCommon.plugins.ksp)
}

sourceSets {
    main { java.srcDir(buildDir.resolve("generated/ksp/$name/kotlin")) }
}

dependencies {
    ksp(projects.libs.sqlGenerator)
    implementation(projects.libs.sqlGeneratorApi)
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverShared)

    implementation(libsCommon.datetime)
    implementation(libsCommon.serialization.json)
    implementation(libsCommon.coroutines.core)

    implementation(libsServer.logback)

    implementation(libsServer.flyway.core)
    implementation(libsServer.fastObjectPool)
    implementation(libsServer.jdbi.core)
    implementation(libsServer.jdbi.sqlobject)
    implementation(libsServer.jdbi.kotlin)
    implementation(libsServer.jdbi.kotlin.sqlobject)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks.named("formatKotlinMain").configure {
    dependsOn("kspKotlin")
}

tasks.named("lintKotlinMain").configure {
    dependsOn("kspKotlin")
}
