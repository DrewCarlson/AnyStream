import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.flywaydb.core.Flyway

// For various reasons, don't use the flyway gradle plugin
// https://github.com/flyway/flyway/issues/3550
@CacheableTask
abstract class FlywayMigrateTask : DefaultTask() {

    @get:Input
    abstract val driver: Property<String>

    @get:Input
    abstract val url: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract var migrationsLocation: Directory

    // Output is required to make the task cacheable
    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty().convention(
        project.layout.buildDirectory.file(url.map { it.substringAfterLast(":") })
    )

    @TaskAction
    fun run() {
        Flyway.configure()
            .driver(driver.get())
            .dataSource(url.get(), null, null)
            .locations("filesystem:${migrationsLocation.asFile}")
            .load()
            .migrate()
    }
}