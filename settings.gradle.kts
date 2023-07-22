rootProject.name = "anystream"

include(
    ":anystream-client-android",
    ":anystream-client-core",
    ":anystream-client-desktop",
    ":anystream-client-web",
    ":anystream-client-ui",
    ":anystream-data-models",
    ":anystream-server:server-app",
    ":anystream-server:server-db-models",
    ":anystream-server:server-library-manager",
    ":anystream-server:server-metadata-manager",
    ":anystream-server:server-shared",
    ":anystream-server:server-stream-service",
    ":libs:preferences",
    ":libs:sql-generator",
    ":libs:sql-generator-api",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libsServer") {
            from(files("gradle/libsServer.versions.toml"))
        }
        create("libsCommon") {
            from(files("gradle/libsCommon.versions.toml"))
        }
        create("libsClient") {
            from(files("gradle/libsClient.versions.toml"))
        }
        create("libsAndroid") {
            from(files("gradle/libsAndroid.versions.toml"))
        }
    }
}
