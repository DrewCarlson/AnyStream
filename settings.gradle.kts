rootProject.name = "anystream"

include(
    ":anystream-client-android",
    ":anystream-client-api",
    ":anystream-client-core",
    ":anystream-client-web",
    ":anystream-data-models",
    ":anystream-server",
    ":libs:preferences",
    ":libs:torrent-search",
)

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

includeBuild("libs/routing-compose") {
    dependencySubstitution {
        substitute(module("app.softwork:routing-compose"))
            .using(project(":"))
    }
}
