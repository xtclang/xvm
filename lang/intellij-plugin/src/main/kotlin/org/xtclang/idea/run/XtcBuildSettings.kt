package org.xtclang.idea.run

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Files
import java.nio.file.Path

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

/** Shared with the project, as the choice belongs to the project rather than to one developer. */
@Service(Service.Level.PROJECT)
@State(name = "EcstasyBuild", storages = [Storage("ecstasy-build.xml")])
class XtcBuildSettings : SerializablePersistentStateComponent<XtcBuildSettings.Options>(Options()) {
    data class Options(
        // Explicit bindings preserve immutable fields in IntelliJ's XML serializer.
        @field:OptionTag @JvmField val mode: XtcBuildMode = XtcBuildMode.EMBEDDED,
    )

    var mode: XtcBuildMode
        get() = state.mode
        set(value) {
            updateState { it.copy(mode = value) }
        }

    companion object {
        private val GRADLE_BUILD_FILES = listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle")

        /** Where the mode is chosen; consoles link it to the page ([BuildSettingsLink]). */
        internal const val SETTINGS_PAGE = "Settings | Build, Execution, Deployment | Ecstasy"

        fun getInstance(project: Project): XtcBuildSettings = project.getService(XtcBuildSettings::class.java)

        /** The mode builds and runs use: [XtcBuildMode.EMBEDDED] or [XtcBuildMode.GRADLE], never automatic. */
        fun effective(project: Project): XtcBuildMode = resolve(getInstance(project).mode, project.basePath?.let(Path::of))

        /** Automatic means Gradle exactly when [root] has a Gradle build, as in the VS Code extension. */
        internal fun resolve(
            mode: XtcBuildMode,
            root: Path?,
        ): XtcBuildMode =
            when {
                mode != XtcBuildMode.AUTOMATIC -> mode
                hasGradleBuild(root) -> XtcBuildMode.GRADLE
                else -> XtcBuildMode.EMBEDDED
            }

        /** Whether [root] has its own Gradle build. */
        internal fun hasGradleBuild(root: Path?): Boolean = root != null && GRADLE_BUILD_FILES.any { Files.isRegularFile(root.resolve(it)) }

        /** Shown by embedded builds and runs in a project with a Gradle build, whose own settings do not apply. */
        internal fun gradleNotUsed(root: Path?): String? =
            if (hasGradleBuild(root)) {
                "This project has a Gradle build, but embedded mode builds and runs with the language server's compiler " +
                    "and XDK; Gradle settings such as runXtc arguments, JVM options and the build's XDK version do not " +
                    "apply. Choose Gradle in $SETTINGS_PAGE to use Gradle."
            } else {
                null
            }
    }
}
