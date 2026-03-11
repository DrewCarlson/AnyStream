plugins {
    id("com.diffplug.spotless")
}

enableSpotlessPlugin(
    enableComposeRules = project.name != "web" && plugins.hasPlugin("org.jetbrains.compose")
)
