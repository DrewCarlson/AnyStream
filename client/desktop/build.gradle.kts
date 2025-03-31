import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("de.undercouch.download")
}

kotlin {
    jvmToolchain(21)
    jvm {
        withJava()
    }
    sourceSets {
        named("jvmMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(projects.client.ui)
                implementation(libsClient.jna)
            }
        }
    }
}

fun getOSAndArch(): Pair<String, String> {
    return Pair(
        System.getProperty("os.name").lowercase(),
        System.getProperty("os.arch").lowercase(),
    )
}

fun getLibvlcForHost(): String? {
    val (osName, osArch) = getOSAndArch()
    return when {
        osName.contains("win") && osArch.contains("64") -> "win64"
        osName.contains("mac") && osArch.contains("x86_64") -> "macos-intel64"
        osName.contains("mac") && osArch.contains("aarch64") -> "macos-arm64"
        else -> {
            System.err.println("Unsupported OS/architecture: $osName/$osArch")
            return null
        }
    }
}

tasks {
    val downloadLibvlc by registering(de.undercouch.gradle.tasks.download.Download::class) {
        val outFile = layout.buildDirectory.file("libvlc-${getLibvlcForHost()}.zip")
        src(
            buildString {
                append("https://github.com/DrewCarlson/libvlc-bin/releases/download/")
                append(libsClient.versions.vlc.get())
                append('/')
                append(getLibvlcForHost().orEmpty())
                append(".zip")
            }
        )
        dest(outFile)
        enabled = !getLibvlcForHost().isNullOrBlank() && !outFile.get().asFile.exists()
    }

    val unpackLibvlc by registering(Copy::class) {
        dependsOn(downloadLibvlc)
        from(zipTree(downloadLibvlc.get().dest))
        into("build/compose/tmp/prepareAppResources/libvlc")
        enabled = !getLibvlcForHost().isNullOrBlank()
    }

    kotlin.jvm().compilations["main"].compileTaskProvider.dependsOn(unpackLibvlc)
}

compose {
    resources {
        generateResClass = always
    }
    desktop {
        application {
            mainClass = "MainKt"

            nativeDistributions {
                modules(
                    "java.instrument",
                    "java.net.http",
                    "jdk.jfr",
                    "jdk.jsobject",
                    "jdk.unsupported",
                    "jdk.unsupported.desktop",
                    "jdk.xml.dom",
                )
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "AnyStream"
                packageVersion = "1.0.0"

                appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

                val iconsRoot = rootProject.file("client/ui/src/jvmMain/resources/images")
                windows {
                    iconFile.set(iconsRoot.resolve("as_icon.ico"))
                }
                macOS {
                    iconFile.set(iconsRoot.resolve("as_icon.icns"))
                }
                linux {
                    iconFile.set(iconsRoot.resolve("as_icon.png"))
                }
            }
        }
    }
}
