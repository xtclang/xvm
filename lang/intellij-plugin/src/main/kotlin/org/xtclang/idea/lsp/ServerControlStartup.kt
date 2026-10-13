package org.xtclang.idea.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Keep CLI control with the project, so a failed language-server process can still be restarted. */
class ServerControlStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDefault || project.isDisposed || project.basePath == null) return
        try {
            val control =
                ServerControlEndpoint(
                    Path.of(System.getProperty("user.home"), ".xtc", "editor-control"),
                    { listOf(Path.of(requireNotNull(project.basePath)).toRealPath().toString()) },
                ) {
                    check(!project.isDisposed) { "Project is closing" }
                    val servers =
                        LanguageServiceAccessor.getInstance(project).startedServers.filter {
                            it.serverDefinition.id == CompilerSettings.SERVER_ID
                        }
                    check(servers.isNotEmpty()) { "Open an Ecstasy file in this project before restarting its server" }
                    servers.forEach { it.restart() }
                    CompletableFuture.allOf(*servers.map { it.initializedServer }.toTypedArray())
                }
            Disposer.register(project) { control.close() }
        } catch (failure: Exception) {
            logger<ServerControlStartup>().warn("Ecstasy CLI control unavailable", failure)
        }
    }
}
