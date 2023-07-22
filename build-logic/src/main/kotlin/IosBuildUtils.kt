import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.configureCommonIosSourceSets() {
    val iosMain by sourceSets.creating {
        dependsOn(sourceSets.getByName("commonMain"))
    }

    val iosTest by sourceSets.creating {
        dependsOn(sourceSets.getByName("commonTest"))
    }

    sourceSets.filter { sourceSet ->
        sourceSet.name.run {
            startsWith("iosX64") ||
                    startsWith("iosArm") ||
                    startsWith("iosSimulator")
        }
    }.forEach { sourceSet ->
        if (sourceSet.name.endsWith("Main")) {
            sourceSet.dependsOn(iosMain)
        } else {
            sourceSet.dependsOn(iosTest)
        }
    }
}