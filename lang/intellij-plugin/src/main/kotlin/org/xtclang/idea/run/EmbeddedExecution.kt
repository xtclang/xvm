package org.xtclang.idea.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.LazyFileHyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.lsp.BuildResponse
import org.xtclang.idea.lsp.CompilerSettings
import org.xtclang.idea.lsp.RunExitedParams
import org.xtclang.idea.lsp.RunOutputParams
import org.xtclang.idea.lsp.XtcLanguageServer
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded build and run: the language server's compiler builds into the workspace's embedded output
 * directory, and its execution worker runs the result. This is the counterpart of building and running
 * with Gradle, chosen by [XtcBuildSettings].
 */
internal object EmbeddedExecution {
    /** The project's Ecstasy language server, started if it is not running yet. */
    fun server(project: Project): CompletableFuture<XtcLanguageServer> =
        LanguageServerManager
            .getInstance(project)
            .getLanguageServer(CompilerSettings.SERVER_ID)
            .thenCompose { item ->
                item?.initializedServer
                    ?: CompletableFuture.failedFuture<LanguageServer>(IllegalStateException("The Ecstasy language server is not available"))
            }.thenApply { it as XtcLanguageServer }

    /** Builds always start from saved files, so the artifacts on disk match the sources. */
    fun saveAll() {
        ApplicationManager.getApplication().invokeAndWait { FileDocumentManager.getInstance().saveAllDocuments() }
    }

    /** The file a diagnostic URI names, or null for a URI that is not a local file. */
    fun file(uri: String): Path? = runCatching { Path.of(URI(uri)) }.getOrNull()

    /** Module outcomes and diagnostics as `file:line:column` lines, which [DiagnosticLinks] links to sources. */
    fun describe(build: BuildResponse): List<String> =
        buildList {
            build.modules.forEach { module ->
                add(
                    when {
                        module.succeeded -> "  ✓ ${module.name}${module.artifact?.let { " -> $it" }.orEmpty()}"
                        module.skipped -> "  - ${module.name} (not built: a dependency failed)"
                        else -> "  ✗ ${module.name}"
                    },
                )
            }
            build.diagnostics.forEach { document ->
                val file = file(document.uri)?.toString() ?: document.uri
                document.diagnostics.forEach { diagnostic ->
                    val start = diagnostic.range.start
                    val code = diagnostic.code?.let { " [$it]" }.orEmpty()
                    add("$file:${start.line + 1}:${start.character + 1}: ${severity(diagnostic.severity)}: ${diagnostic.message}$code")
                }
            }
            if (build.cancelled) add("Build cancelled.")
        }

    /** LSP severities: 1 error, 2 warning, 3 information, 4 hint. */
    private fun severity(severity: Int?): String =
        when (severity) {
            2 -> "warning"
            3 -> "info"
            4 -> "hint"
            else -> "error"
        }
}

/** Links the `file:line:column:` lines of [EmbeddedExecution.describe] in a run console. */
internal class DiagnosticLinks(
    private val project: Project,
) : Filter {
    override fun applyFilter(
        line: String,
        entireLength: Int,
    ): Filter.Result? {
        val match = LOCATION.find(line) ?: return null
        val (path, row, column) = match.destructured
        val start = entireLength - line.length + match.range.first
        // the link covers `file:line:column`, not the separator after it
        return Filter.Result(
            start,
            start + match.value.length - 2,
            LazyFileHyperlinkInfo(project, path, row.toInt() - 1, column.toInt() - 1),
        )
    }

    private companion object {
        val LOCATION = Regex("""^(.+?\.x):(\d+):(\d+): """)
    }
}

/** Links [XtcBuildSettings.SETTINGS_PAGE] in a run console or the Build tool window to that settings page. */
internal object BuildSettingsLink : Filter {
    override fun applyFilter(
        line: String,
        entireLength: Int,
    ): Filter.Result? {
        val offset = line.indexOf(XtcBuildSettings.SETTINGS_PAGE)
        if (offset < 0) return null
        val start = entireLength - line.length + offset
        return Filter.Result(
            start,
            start + XtcBuildSettings.SETTINGS_PAGE.length,
            HyperlinkInfo { project -> ShowSettingsUtil.getInstance().showSettingsDialog(project, XtcBuildConfigurable::class.java) },
        )
    }
}

/**
 * Routes a project's run notifications to their consoles. The server reports by the run ID the client
 * chose, so a session is registered before its run is requested.
 */
@Service(Service.Level.PROJECT)
class EmbeddedRunSessions {
    interface Session {
        fun output(text: String)

        fun exited(
            exitCode: Long?,
            error: String?,
        )
    }

    private val sessions = ConcurrentHashMap<String, Session>()

    fun register(
        runId: String,
        session: Session,
    ) {
        sessions[runId] = session
    }

    fun remove(runId: String) {
        sessions.remove(runId)
    }

    fun output(params: RunOutputParams) {
        sessions[params.runId]?.output(params.text)
    }

    fun exited(params: RunExitedParams) {
        sessions.remove(params.runId)?.exited(params.exitCode, params.error)
    }

    /** A stopped or restarted language server takes its execution worker, and every run, with it. */
    fun endAll(reason: String) {
        sessions.keys.toList().forEach { sessions.remove(it)?.exited(null, reason) }
    }

    companion object {
        fun getInstance(project: Project): EmbeddedRunSessions = project.getService(EmbeddedRunSessions::class.java)
    }
}
