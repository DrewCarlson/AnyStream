import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("de.undercouch.download")
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(projects.anystreamClientUi)
                implementation(projects.libs.desktopVideoplayer)
                implementation(libs.jna)
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

fun getLibvlcForHost(): String {
    val (osName, osArch) = getOSAndArch()
    return when {
        osName.contains("win") && osArch.contains("64") -> "win64"
        osName.contains("mac") && osArch.contains("x86_64") -> "macos-intel64"
        osName.contains("mac") && osArch.contains("aarch64") -> "macos-arm64"
        else -> throw IllegalStateException("Unsupported OS/architecture: $osName/$osArch")
    }
}

tasks {
    val libvlcUrl = buildString {
        append("https://github.com/DrewCarlson/libvlc-bin/releases/download/")
        append(libs.versions.vlc.get())
        append('/')
        append(getLibvlcForHost())
        append(".zip")
    }

    val downloadLibvlc by registering(de.undercouch.gradle.tasks.download.Download::class) {
        val outFile = buildDir.resolve("libvlc-${getLibvlcForHost()}.zip")
        src(libvlcUrl)
        dest(outFile)
        enabled = !outFile.exists()
    }

    val unpackLibvlc by registering(Copy::class) {
        dependsOn(downloadLibvlc)
        from(zipTree(downloadLibvlc.get().dest))
        into("build/compose/tmp/prepareAppResources/libvlc")
    }

    kotlin.jvm().compilations["main"].compileTaskProvider.dependsOn(unpackLibvlc)
}

compose {
    kotlinCompilerPlugin.set(libs.jbcompose.compiler.get().toString())
    desktop {
        application {
            mainClass = "MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "AnyStream"
                packageVersion = "1.0.0"

                appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            }
        }
    }
}
