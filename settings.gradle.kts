rootProject.name = "AnyStream"

include(
    ":client:android",
    ":client:core",
    ":client:desktop",
    ":client:web",
    ":client:ui",
    ":client:data-models",
    ":server:application",
    ":server:db-models",
    ":server:db-models:testing",
    ":server:db-models:jooq-generator",
    ":server:library-manager",
    ":server:metadata-manager",
    ":server:shared",
    ":server:stream-service",
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
