rootProject.name = "anystream"

include(
    ":anystream-client-android",
    ":anystream-client-core",
    ":anystream-client-web",
    ":anystream-data-models",
    ":anystream-server",
    ":libs:preferences",
    ":libs:routing-compose",
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
