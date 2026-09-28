import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValidateCredentialsTaskTest {
    @Test
    fun snapshotSubstringDoesNotBypassReleaseApproval() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("validateCredentials", ValidateCredentialsTask::class.java).get()
        task.projectName.set("fixture")
        task.projectVersion.set("1.2.3-SNAPSHOT-final")
        task.allowRelease.set(false)
        task.githubUsername.set("")
        task.githubPassword.set("")
        val failure = assertThrows(GradleException::class.java) { task.validate() }
        assertEquals("Release publishing requires -Porg.xtclang.allowRelease=true", failure.message)
    }
}
