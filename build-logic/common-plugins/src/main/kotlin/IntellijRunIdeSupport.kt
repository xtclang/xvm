import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

abstract class RunIdeEnvironmentReportTask : DefaultTask() {
    @get:Input
    abstract val ideVersion: Property<String>

    @get:Input
    abstract val sinceBuild: Property<String>

    @get:Input
    abstract val lsp4ijVersion: Property<String>

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val semanticTokensEnabled: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sandboxDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mavenLocalRoot: DirectoryProperty

    @get:Input
    abstract val pluginNames: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lspLogFile: RegularFileProperty

    init {
        doNotTrackState("Runtime reporting task reads live sandbox state and should never be state-tracked")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun report() {
        val sandbox = sandboxDir.get().asFile
        val ideaLog = sandbox.resolve("log/idea.log")
        val systemDir = sandbox.resolve("system")
        val sandboxIsReused = systemDir.exists() && systemDir.listFiles().orEmpty().isNotEmpty()
        val xtcArtifacts = mavenLocalRoot.get().asFile.resolve("org/xtclang")

        logger.lifecycle("[runIde] ─── Version Matrix (gradle/libs.versions.toml) ───")
        logger.lifecycle("[runIde]   IntelliJ IDEA: ${ideVersion.get()} (sinceBuild=${sinceBuild.get()})")
        logger.lifecycle("[runIde]   LSP4IJ:        ${lsp4ijVersion.get()}")
        logger.lifecycle("[runIde]   XTC plugin:    ${pluginVersion.get()}")
        logger.lifecycle("[runIde]   LSP semantic tokens in IDE: ${semanticTokensEnabled.get()}")

        logger.lifecycle("[runIde] ─── Sandbox ───")
        logger.lifecycle("[runIde]   Path:      ${sandbox.absolutePath}")
        logger.lifecycle(
            "[runIde]   Status:    ${if (sandboxIsReused) "reused (existing sandbox with IDE caches/indices)" else "fresh (new sandbox - first-run indexing will be slower)"}",
        )
        logger.lifecycle("[runIde]   Plugins:   ${pluginNames.get()}")
        logger.lifecycle("[runIde]   IDE log:   ${ideaLog.absolutePath}")
        logger.lifecycle("[runIde]              tail -f ${ideaLog.absolutePath}")

        logger.lifecycle("[runIde] ─── mavenLocal XTC Artifacts ───")
        logger.lifecycle("[runIde]   ${xtcArtifacts.absolutePath}")
        if (xtcArtifacts.exists()) {
            xtcArtifacts.listFiles()?.sorted()?.forEach { artifact ->
                val versions = artifact.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                logger.lifecycle("[runIde]   ${artifact.name}: ${versions.joinToString(", ")}")
            }
        }

        logger.lifecycle("[runIde] ─── Reset Commands ───")
        logger.lifecycle("[runIde]   Nuke sandbox (keeps IDE download):  ./gradlew :lang:intellij-plugin:clean")
        logger.lifecycle("[runIde]   Nuke cached IDE + metadata:         rm -rf lang/.intellijPlatform/localPlatformArtifacts")

        lspLogFile.orNull?.asFile?.let { logFile ->
            logger.lifecycle("[runIde] LSP log:  ${logFile.absolutePath} (tailing to console)")
        }
    }
}

abstract class StartLogTailTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val logFile: RegularFileProperty

    @get:Input
    abstract val threadName: Property<String>

    @get:Input
    abstract val linePrefix: Property<String>

    init {
        doNotTrackState("Runtime log tail task manages background thread state and should never be state-tracked")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun startTail() {
        val file = logFile.orNull?.asFile ?: return
        val name = threadName.get()

        Thread.getAllStackTraces().keys.firstOrNull { it.name == name }?.interrupt()

        val startSize = if (file.exists()) file.length() else 0L
        val prefix = linePrefix.get()
        thread(isDaemon = true, name = name) {
            runCatching {
                while (!file.exists() && !Thread.currentThread().isInterrupted) {
                    Thread.sleep(500)
                }
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(startSize)
                    while (!Thread.currentThread().isInterrupted) {
                        val line = raf.readLine()
                        if (line != null) {
                            logger.lifecycle("$prefix$line")
                        } else {
                            Thread.sleep(200)
                        }
                    }
                }
            }
        }
    }
}

abstract class StopLogTailTask : DefaultTask() {
    @get:Input
    abstract val threadName: Property<String>

    init {
        doNotTrackState("Runtime log tail stop task manages background thread state and should never be state-tracked")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun stopTail() {
        Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName.get() }?.interrupt()
    }
}
