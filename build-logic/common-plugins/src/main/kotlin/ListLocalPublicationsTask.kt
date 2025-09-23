import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to list local Maven publications for a project.
 * Centralized implementation to avoid duplication across build scripts.
 */
abstract class ListLocalPublicationsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val includeZip: Property<Boolean>

    init {
        // Default to not including zip files (most projects don't need them)
        includeZip.convention(false)
    }

    @TaskAction
    fun listPublications() {
        val userHome = System.getProperty("user.home")
        val repoDir = File(userHome, ".m2/repository/org/xtclang/${projectName.get()}")

        if (!repoDir.exists()) {
            logger.lifecycle("[${projectName.get()}] No local publications found")
            return
        }

        logger.lifecycle("[${projectName.get()}] Local Maven publications in ${repoDir.absolutePath}:")
        repoDir.walkTopDown().forEach { file ->
            val extensions = mutableListOf("jar", "pom")
            if (includeZip.get()) {
                extensions.add("zip")
            }

            if (file.isFile && file.extension in extensions) {
                val relativePath = file.relativeTo(repoDir)
                logger.lifecycle("[${projectName.get()}]   $relativePath")
            }
        }
    }
}