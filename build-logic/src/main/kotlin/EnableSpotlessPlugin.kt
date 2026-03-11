import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType


fun Project.enableSpotlessPlugin(enableComposeRules: Boolean) {
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/**.kt")
            licenseHeaderFile(rootDir.resolve("licenseHeader.txt"))
            val libsCommon = extensions.getByType<VersionCatalogsExtension>().named("libsCommon")
            ktlint(libsCommon.findVersion("ktlint").get().requiredVersion)
                .setEditorConfigPath(rootDir.resolve(".editorconfig"))
                .apply {
                    if (enableComposeRules) {
                        customRuleSets(
                            listOf(
                                libsCommon.findLibrary("ktlint-composeRules")
                                    .get()
                                    .get()
                                    .toString(),
                            ),
                        )
                    }
                }
        }
    }
}