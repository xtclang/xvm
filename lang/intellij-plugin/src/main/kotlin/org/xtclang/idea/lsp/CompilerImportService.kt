package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
internal class CompilerImportService(
    project: Project,
) : Disposable {
    val model =
        CompilerImport(
            read = { CompilerWorkspaceModels.read(CompilerWorkspaceModels.roots(project)) },
            validate = { CompilerBuildModel.parse(it) },
        )

    private val disposed = AtomicBoolean()
    private val active = AtomicReference<ProgressIndicator?>()

    fun <T> run(
        indicator: ProgressIndicator,
        operation: () -> T,
    ): T {
        if (disposed.get()) throw ProcessCanceledException()
        check(active.compareAndSet(null, indicator)) { "An Ecstasy compiler import is already running" }
        try {
            // Closing the project may have raced with claiming this operation.
            if (disposed.get()) indicator.cancel()
            indicator.checkCanceled()
            return operation()
        } finally {
            active.compareAndSet(indicator, null)
        }
    }

    override fun dispose() {
        disposed.set(true)
        active.get()?.cancel()
    }
}
