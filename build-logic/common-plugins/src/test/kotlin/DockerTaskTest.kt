import java.io.File
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DockerTaskTest {
    @TempDir
    lateinit var root: File

    @Test
    fun failedDockerCommandPreservesAnExistingSourceContextArchive() {
        val directory = File(root, "project").apply { mkdirs() }
        val originalArchive = File(directory, "xdk-dist.zip").apply { writeText("user-owned archive") }
        File(directory, "Dockerfile").writeText("FROM scratch\n")
        val distribution = File(root, "distribution.zip").apply { writeText("build distribution") }
        val project = ProjectBuilder.builder().withProjectDir(directory).build()
        val task = project.tasks.register("buildDocker", DockerTask::class.java).get().apply {
            platforms.set(listOf("linux/amd64"))
            action.set("load")
            distZipUrl.set(distribution.absolutePath)
            jdkVersion.set(25)
            gitCommit.set("fixture")
            gitBranch.set("fixture")
            projectVersion.set("1.2.3")
            hostArch.set("amd64")
            allowEmulation.set(false)
            dockerProgress.set("plain")
            dockerCommand.set(File(root, "deliberately-missing-docker").absolutePath)
            baseImage.set("fixture/image")
            dockerDir.set(directory)
            buildMarkerFile.set(File(root, "build-marker"))
        }

        assertThrows(GradleException::class.java) { task.buildDockerImage() }
        assertTrue(originalArchive.isFile, "A failed build must preserve the source context")
        assertEquals("user-owned archive", originalArchive.readText())
        assertFalse(task.buildMarkerFile.get().asFile.exists())
        assertFalse(File(task.temporaryDir, "context").exists(), "Private staging must be cleaned after failure")
    }
}
