import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
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

abstract class ListRemotePublicationsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>
    
    @TaskAction
    fun listRemotePublications() {
        // REMOVED: GitHubProtocol functionality moved to org.xtclang.build.git convention plugin
        // MIGRATION: Use 'resolveGitHubPackages' task from org.xtclang.build.git convention plugin
        logger.warn("[build-logic] resolvePackages functionality moved to org.xtclang.build.git convention plugin")
        logger.warn("[build-logic] No packages found for project '${projectName.get()}'.")
    }
}

abstract class DeleteRemotePublicationsTask : DefaultTask() {
    @get:Input
    abstract val deletePackageNames: ListProperty<String>
    
    @get:Input
    abstract val deletePackageVersions: ListProperty<String>
    
    @TaskAction
    fun deleteRemotePublications() {
        // REMOVED: GitHubProtocol functionality moved to org.xtclang.build.git convention plugin
        // MIGRATION: Use 'deleteGitHubPackages' task from org.xtclang.build.git convention plugin
        logger.warn("[build-logic] deletePackages functionality moved to org.xtclang.build.git convention plugin")
        logger.warn("[build-logic] deleteNames: ${deletePackageNames.get()}, deleteVersions: ${deletePackageVersions.get()}")
    }
}

abstract class ConfigureCacheCompatibleMavenPublicationTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations
    
    @get:Input
    abstract val projectGroupId: Property<String>
    
    @get:Input 
    abstract val projectArtifactId: Property<String>
    
    @get:Input
    abstract val projectVersion: Property<String>
    
    @get:InputFile
    abstract val distZipFile: RegularFileProperty
    
    @TaskAction
    fun configureAndPublish() {
        // Create temporary POM file
        val pomContent = generatePomXml()
        val tempPomFile = File.createTempFile("pom", ".xml")
        tempPomFile.writeText(pomContent)
        
        try {
            // Use mvn install directly to avoid Maven publication configuration issues
            execOperations.exec {
                commandLine(
                    "mvn", "install:install-file",
                    "-Dfile=${distZipFile.get().asFile.absolutePath}",
                    "-DpomFile=${tempPomFile.absolutePath}",
                    "-DgroupId=${projectGroupId.get()}",
                    "-DartifactId=${projectArtifactId.get()}",
                    "-Dversion=${projectVersion.get()}",
                    "-Dpackaging=zip"
                )
            }
            logger.lifecycle("[${projectArtifactId.get()}] Published ${projectArtifactId.get()}:${projectVersion.get()} to local repository")
        } finally {
            tempPomFile.delete()
        }
    }
    
    private fun generatePomXml(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${projectGroupId.get()}</groupId>
    <artifactId>${projectArtifactId.get()}</artifactId>
    <version>${projectVersion.get()}</version>
    <packaging>zip</packaging>
    <name>xdk</name>
    <description>XTC Language Software Development Kit (XDK) Distribution Archive</description>
    <url>https://xtclang.org</url>
    <licenses>
        <license>
            <name>The XDK License</name>
            <url>https://github.com/xtclang/xvm/tree/master/license</url>
        </license>
    </licenses>
    <developers>
        <developer>
            <id>xtclang-workflows</id>
            <name>XTC Team</name>
            <email>noreply@xtclang.org</email>
        </developer>
    </developers>
</project>"""
    }
}