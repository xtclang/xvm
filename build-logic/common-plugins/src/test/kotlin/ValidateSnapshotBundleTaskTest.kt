import java.io.File
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class ValidateSnapshotBundleTaskTest {
    @TempDir
    lateinit var root: File

    private fun task(version: String, url: String): ValidateSnapshotBundleTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("validateBundle", ValidateSnapshotBundleTask::class.java).get().apply {
            publicationVersion.set(version)
            repositoryUrl.set(url)
        }
    }

    @Test
    fun localSnapshotValidationNeedsNoCredentialsAndCreatesNoRepository() {
        val directory = File(root, "not-created")
        task("1.2.3-SNAPSHOT", directory.toURI().toString()).validate()
        assertFalse(directory.exists())
    }

    @TestFactory
    fun nonSnapshotVersionsAreRejected() =
        listOf("1.2.3", "1.2.3-SNAPSHOT-final", "1.2.3-snapshot", "SNAPSHOT").map { version ->
            dynamicTest(version) {
                assertThrows(GradleException::class.java) {
                    task(version, root.toURI().toString()).validate()
                }
            }
        }

    @TestFactory
    fun nonLocalOrMissingDestinationsAreRejected() =
        listOf("", "https://example.invalid/repository", "file://remote-host/repository",
            "relative/path", "file:relative", "file:///tmp/repository?query=invalid").map { url ->
            dynamicTest(url.ifEmpty { "missing destination" }) {
                assertThrows(GradleException::class.java) {
                    task("1.2.3-SNAPSHOT", url).validate()
                }
            }
        }
}
