import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject
import java.io.File

abstract class DeleteLocalPublicationsTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations
    
    @get:Input
    abstract val userHomePath: Property<String>
    
    @get:Input
    abstract val projectName: Property<String>
    
    @TaskAction
    fun deletePublications() {
        val repoDir = File(userHomePath.get(), ".m2/repository/org/xtclang/${projectName.get()}")
        if (!repoDir.exists()) {
            logger.warn("[build-logic] No local publications found in '${repoDir.absolutePath}'.")
            return
        }

        val xtclangDir = repoDir.parentFile
        require(xtclangDir.exists() && xtclangDir.isDirectory) {
            "Illegal state: parent directory of '$repoDir' does not exist."
        }
        logger.lifecycle("[build-logic] Deleting all local publications in '${repoDir.absolutePath}'.")
        fileSystemOperations.delete { delete(repoDir) }
        val xtclangFiles = xtclangDir.listFiles()
        if (xtclangFiles == null || xtclangFiles.isEmpty()) {
            logger.lifecycle("[build-logic] Deleting empty parent directory '${xtclangDir.absolutePath}'.")
            fileSystemOperations.delete { delete(xtclangDir) }
        }
    }
}

abstract class ListLocalPublicationsTask : DefaultTask() {
    @get:Input
    abstract val userHomePath: Property<String>
    
    @get:Input
    abstract val projectName: Property<String>
    
    @get:Input
    abstract val projectGroup: Property<String>
    
    @TaskAction
    fun listPublications() {
        logger.lifecycle("[build-logic] '${name}' Listing local publications (and their artifacts) for project '${projectGroup.get()}:${projectName.get()}':")
        val repoDir = File(userHomePath.get(), ".m2/repository/org/xtclang/${projectName.get()}")
        if (!repoDir.exists()) {
            logger.warn("[build-logic] WARNING: No local publications found on disk at: '${repoDir.absolutePath}'.")
        } else {
            logger.lifecycle("[build-logic] Local publications found at: '${repoDir.absolutePath}'.")
        }
        logger.warn("[build-logic] Publication listing functionality moved to org.xtclang.build.git convention plugin")
    }
}