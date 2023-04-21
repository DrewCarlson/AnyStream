import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework

fun KotlinMultiplatformExtension.configureFramework(block: Framework.() -> Unit) {
    iosArm64 { binaries { framework(configure = block) } }
    iosSimulatorArm64 { binaries { framework(configure = block) } }
    iosX64 { binaries { framework(configure = block) } }
}