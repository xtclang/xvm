package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.OptionTag
import org.xtclang.idea.lsp.XtcFileOperations
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * How the project builds and runs Ecstasy modules, like IntelliJ's "Build and run using" for Gradle projects.
 * The embedded compiler is the default, also in a project with a Gradle build.
 */
enum class XtcBuildMode(
    val label: String,
) {
    AUTOMATIC("Automatic (Gradle when the project has a Gradle build)"),
    EMBEDDED("Embedded compiler"),
    GRADLE("Gradle"),
    ;

    override fun toString() = label
}

/** Has [handler]'s errors, a Gradle run's, tell [project] to import its Gradle build once Gradle finds no task of the run. */
internal fun missingTask(
    handler: ProcessHandler,
    project: Project,
) {
    val settings = XtcBuildSettings.getInstance(project)
    handler.addProcessListener(
        object : ProcessListener {
            override fun onTextAvailable(
                event: ProcessEvent,
                outputType: Key<*>,
            ) {
                if (outputType == ProcessOutputTypes.STDERR) settings.missingTask(project, event.text)
            }
        },
    )
}

/** Shared with the project, as the choice belongs to the project rather than to one developer. */
@Service(Service.Level.PROJECT)
@State(name = "EcstasyBuild", storages = [Storage("ecstasy-build.xml")])
class XtcBuildSettings : SerializablePersistentStateComponent<XtcBuildSettings.Options>(Options()) {
    var mode: XtcBuildMode
        get() = state.mode
        set(value) {
            updateState { it.copy(mode = value) }
        }

    /**
     * Why the language server says this project builds and runs with Gradle, whatever the chosen mode, as the server
     * builds and runs only with the bundled XDK: what each of its folders that does so does, such as `builds with XDK
     * 0.5.0, not the bundled XDK 0.4.4` or `replaces the bundled libraries json.xtclang.org with its own modules`. None
     * while the server builds and runs the project's modules. Not saved: each server says it again.
     */
    @Volatile
    var gradleReasons: List<String> = emptyList()
        private set

    /** Whether this project was told that it builds and runs with Gradle. */
    private val told = AtomicBoolean()

    /**
     * Takes what the server says: the bundled [libraries] that the project's own modules replace, as the XVM repository's
     * replace them all, and, by folder, why folders build and run with Gradle, its [reasons]; the first time they make
     * builds use Gradle instead of the chosen mode, says why.
     */
    fun replaced(
        project: Project,
        libraries: List<String>,
        reasons: Collection<String>,
    ) {
        gradleReasons = gradleReasons(libraries, reasons)
        if (gradleReasons.isNotEmpty() && resolve(mode, project.basePath?.let(Path::of)) != XtcBuildMode.GRADLE &&
            told.compareAndSet(false, true)
        ) {
            Notification(
                XtcFileOperations.NOTIFICATIONS,
                "Ecstasy builds and runs use Gradle",
                "${gradleOnly(gradleReasons)}, so Build, Run and Debug use Gradle.",
                NotificationType.INFORMATION,
            ).notify(project)
        }
    }

    /**
     * The Gradle project of each of this project's source modules, by module name, as its path in the build tree, which
     * the language server says from the imported Gradle model: a module's runXtc and testXtc run in it, the only way to
     * reach an included build of a composite build. Not saved: each server says it again.
     */
    @Volatile
    var gradleProjects: Map<String, String> = emptyMap()

    /** Whether this project was told to import its Gradle build, after Gradle found no task of a module's run. */
    private val toldImport = AtomicBoolean()

    /**
     * Says once, when [text] of a Gradle run's output is Gradle finding no such task while no imported model names the
     * module's project, as at a composite build's root, to import the Gradle build.
     */
    fun missingTask(
        project: Project,
        text: String,
    ) {
        if (gradleProjects.isEmpty() && MISSING_TASK.containsMatchIn(text) && toldImport.compareAndSet(false, true)) {
            Notification(
                XtcFileOperations.NOTIFICATIONS,
                "Import the Gradle build to run its Ecstasy modules",
                "Gradle has no runXtc or testXtc at this build's root, as in a composite build, whose included builds a " +
                    "task's bare name does not reach. Refresh the Gradle model in $COMPILER_PAGE, and Run, Debug and " +
                    "tests use the task of each module's own project.",
                NotificationType.INFORMATION,
            ).notify(project)
        }
    }

    data class Options(
        // Explicit bindings preserve immutable fields in IntelliJ's XML serializer.
        @field:OptionTag @JvmField val mode: XtcBuildMode = XtcBuildMode.EMBEDDED,
    )

    companion object {
        private val GRADLE_BUILD_FILES = listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle")

        /** Where the mode is chosen; consoles link it to the page ([BuildSettingsLink]). */
        internal const val SETTINGS_PAGE = "Settings | Languages & Frameworks | Ecstasy | Build and Run"

        /** Where the Gradle model is imported, with its Refresh Gradle model button. */
        private const val COMPILER_PAGE = "Settings | Languages & Frameworks | Ecstasy | Compiler"

        // Gradle's answer to a task that no project of the build has, such as a bare runXtc at a composite build's root
        private val MISSING_TASK = Regex("Task '(run|test)Xtc' not found")

        /**
         * [task] of [module]'s Gradle project, by its path in the build tree from [projects], such as
         * `:xdk:lib-json:testXtc`, which reaches a composite build's included builds; the bare name when no imported
         * model names the module's project.
         */
        fun gradleTask(
            projects: Map<String, String>,
            module: String?,
            task: String,
        ): String = module?.let(projects::get)?.let { taskOf(it, task) } ?: task

        /** [task] of every project that the imported model names, as for every test module; the bare name without one. */
        fun gradleTasks(
            projects: Map<String, String>,
            task: String,
        ): List<String> =
            projects.values
                .distinct()
                .sorted()
                .map { taskOf(it, task) }
                .ifEmpty { listOf(task) }

        // a root project's path is ":", whose task is ":runXtc"
        private fun taskOf(
            project: String,
            task: String,
        ) = if (project == ":") ":$task" else "$project:$task"

        fun getInstance(project: Project): XtcBuildSettings = project.getService(XtcBuildSettings::class.java)

        /** The mode builds and runs use: [XtcBuildMode.EMBEDDED] or [XtcBuildMode.GRADLE], never automatic. */
        fun effective(project: Project): XtcBuildMode =
            getInstance(project).let { resolve(it.mode, project.basePath?.let(Path::of), it.gradleReasons) }

        /**
         * Gradle while the server says the project builds and runs with it, for [reasons] ([gradleReasons]); else
         * automatic means Gradle exactly when [root] has a Gradle build, as in the VS Code extension.
         */
        internal fun resolve(
            mode: XtcBuildMode,
            root: Path?,
            reasons: List<String> = emptyList(),
        ): XtcBuildMode =
            when {
                reasons.isNotEmpty() -> XtcBuildMode.GRADLE
                mode != XtcBuildMode.AUTOMATIC -> mode
                hasGradleBuild(root) -> XtcBuildMode.GRADLE
                else -> XtcBuildMode.EMBEDDED
            }

        /**
         * Why the project builds and runs with Gradle, from what the server says: its [reasons], one for each folder that
         * does, or else, from a server that names no folder, that the project's own modules replace the bundled
         * [libraries]; none while there is neither.
         */
        internal fun gradleReasons(
            libraries: List<String>,
            reasons: Collection<String>,
        ): List<String> =
            reasons.distinct().ifEmpty {
                listOfNotNull(
                    libraries.joinToString().takeIf { it.isNotEmpty() }?.let { "replaces the bundled libraries $it with its own modules" },
                )
            }

        /** That the project does what [reasons] say, and that the server builds and runs only with the bundled XDK. */
        internal fun gradleOnly(reasons: List<String>): String =
            "This project ${reasons.joinToString(" and ")}, and the language server builds and runs only with the bundled XDK"

        /** Whether [mode] can apply while the project builds and runs with Gradle for [reasons]: embedded cannot. */
        internal fun available(
            mode: XtcBuildMode,
            reasons: List<String>,
        ): Boolean = mode != XtcBuildMode.EMBEDDED || reasons.isEmpty()

        /** Why the embedded compiler is not available while the project builds and runs with Gradle for [reasons]. */
        internal fun unavailable(reasons: List<String>): String =
            "Not available while this project ${reasons.joinToString(" and ")}: the language server builds and runs only " +
                "with the bundled XDK, so builds and runs use Gradle. A choice of the embedded compiler is kept, and applies " +
                "again once that changes."

        /** Whether [root] has its own Gradle build. */
        internal fun hasGradleBuild(root: Path?): Boolean = root != null && GRADLE_BUILD_FILES.any { Files.isRegularFile(root.resolve(it)) }

        /**
         * Shown by embedded builds and runs in a project with a Gradle build, of whose own settings only the run settings
         * that the imported Gradle model carries apply; the run's output says which it took.
         */
        internal fun gradleNotUsed(root: Path?): String? =
            if (hasGradleBuild(root)) {
                "This project has a Gradle build, but embedded mode builds and runs with the language server's compiler " +
                    "and XDK, not the build's. Of the build's settings, only xtcRun's method, arguments and JVM options and " +
                    "xtcTest's JVM options apply, once its Gradle model is imported. Choose Gradle in $SETTINGS_PAGE to use Gradle."
            } else {
                null
            }
    }
}
