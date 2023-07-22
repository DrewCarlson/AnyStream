dependencyResolutionManagement {
    versionCatalogs {
        create("libsServer") {
            from(files("../gradle/libsServer.versions.toml"))
        }
        create("libsCommon") {
            from(files("../gradle/libsCommon.versions.toml"))
        }
        create("libsClient") {
            from(files("../gradle/libsClient.versions.toml"))
        }
    }
}

pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2/")
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
    }
}

rootProject.name = "build-logic"
