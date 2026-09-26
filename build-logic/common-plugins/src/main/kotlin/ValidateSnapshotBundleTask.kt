import java.net.URI
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Validates a local snapshot bundle before any Maven publishing action writes it. */
@DisableCachingByDefault(because = "Publication policy must be checked on every invocation")
abstract class ValidateSnapshotBundleTask : DefaultTask() {
    @get:Input
    abstract val publicationVersion: Property<String>

    @get:Input
    abstract val repositoryUrl: Property<String>

    @TaskAction
    fun validate() {
        val version = publicationVersion.get()
        if (!version.endsWith("-SNAPSHOT")) {
            throw GradleException("Snapshot bundles require a -SNAPSHOT version; got $version")
        }
        val location = repositoryUrl.get()
        if (location.isBlank()) {
            throw GradleException("Set -Porg.xtclang.publish.snapshotBundleRepo to a local Maven repository directory")
        }
        val isLocalFile = try {
            val uri = URI.create(location)
            uri.scheme.equals("file", ignoreCase = true) &&
                uri.authority.isNullOrEmpty() && Path.of(uri).isAbsolute
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!isLocalFile) {
            throw GradleException("Snapshot bundle repository must be a local file URI without a remote host")
        }
    }
}
