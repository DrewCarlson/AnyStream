rootProject.name = "anystream"

include(":server")
include(":api-client")
include(":data-models")
include(":client")
include(":client-web")
include(":client-android")
include(":preferences")
include(":ktor-permissions")
include(":torrent-search")

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
