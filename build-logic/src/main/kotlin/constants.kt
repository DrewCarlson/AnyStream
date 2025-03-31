import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Use JDK 21 as main toolchain version and compatability target for server modules.
val JAVA_TARGET = JavaVersion.VERSION_21
val JVM_TARGET = JvmTarget.JVM_21

// Use 17 as compatibility target for android modules.
val JAVA_TARGET_ANDROID = JavaVersion.VERSION_17
val JVM_TARGET_ANDROID = JvmTarget.JVM_17