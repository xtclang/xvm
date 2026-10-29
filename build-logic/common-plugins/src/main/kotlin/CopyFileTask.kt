import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

/**
 * Copies one file without claiming ownership of its destination directory. The copy must have the source's bytes,
 * and a jar or zip must read as a whole archive ([requireWholeArchive]); otherwise it is deleted and the task fails,
 * since a source that another build was writing while it was copied leaves a copy that a later build would take as
 * up to date.
 */
@DisableCachingByDefault(because = "Copying a single file is cheaper than caching it")
abstract class CopyFileTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun copy() {
        val source = sourceFile.get().asFile
        val target = outputFile.get().asFile
        source.copyTo(target, overwrite = true)
        if (Files.mismatch(source.toPath(), target.toPath()) != -1L) {
            target.delete()
            throw GradleException(
                "$source changed while it was copied to $target: another build was probably writing it. The copy is " +
                    "deleted; run the build again.",
            )
        }
        if (target.extension in setOf("jar", "zip")) {
            requireWholeArchive(target, "The copy of ${source.name}")
        }
    }
}
