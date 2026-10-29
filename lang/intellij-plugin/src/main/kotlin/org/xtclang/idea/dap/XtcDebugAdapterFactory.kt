package org.xtclang.idea.dap

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.client.DAPClient
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig
import com.redhat.devtools.lsp4ij.settings.ServerTrace
import org.xtclang.idea.run.EmbeddedExecution
import org.xtclang.idea.run.XtcAttachConfiguration
import org.xtclang.idea.run.XtcRunConfiguration

/**
 * The Ecstasy debug adapter for LSP4IJ's DAP client, registered with the `com.redhat.devtools.lsp4ij.debugAdapterServer`
 * extension point. The adapter is a debug session in the Ecstasy language server, which builds and runs the module
 * with its embedded compiler and execution worker, as Run does; see [LanguageServerDebugProcessHandler].
 *
 * Debugging goes through the Ecstasy Application run configuration ([XtcRunConfiguration]), whose Debug this adapter
 * serves; its Run stays with the configuration's own embedded, Gradle or command-line run. The Ecstasy Attach
 * configuration ([XtcAttachConfiguration]) attaches instead, to a program run with the launcher's `--debug`. `.x` files are debuggable,
 * which lets line breakpoints be set in them, but LSP4IJ's own DAP run configuration is not created for them.
 */
class XtcDebugAdapterFactory : DebugAdapterDescriptorFactory() {
    override fun createDebugAdapterDescriptor(
        options: RunConfigurationOptions,
        environment: ExecutionEnvironment,
    ): DebugAdapterDescriptor = XtcDebugAdapterDescriptor(options, environment, serverDefinition)

    override fun isDebuggableFile(
        file: VirtualFile,
        project: Project,
    ): Boolean = file.extension == "x"

    override fun canRun(executorId: String): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID

    override fun prepareConfiguration(
        configuration: RunConfiguration,
        file: VirtualFile,
        project: Project,
    ): Boolean = false

    companion object {
        /** The adapter's ID in `plugin.xml`. */
        const val SERVER_ID = "xtcDebugAdapter"
    }
}

/**
 * One debug launch of an Ecstasy Application configuration: its "server" is a process that carries the session over
 * the language server's connection, ready at once, and its launch arguments are the configuration's module, method
 * and arguments, whether it stops at its first line, and whether evaluations run the whole program, in the form the
 * language server's `launch` takes them.
 */
class XtcDebugAdapterDescriptor(
    options: RunConfigurationOptions,
    environment: ExecutionEnvironment,
    serverDefinition: DebugAdapterServerDefinition?,
) : DebugAdapterDescriptor(options, environment, serverDefinition) {
    private val project = environment.project

    override fun startServer(): ProcessHandler =
        LanguageServerDebugProcessHandler(DebugSessions.getInstance(project)) {
            // the session builds the text the server holds, so the editor's edits must have reached it
            EmbeddedExecution.server(project).thenCombine(EmbeddedExecution.editsSent(project)) { server, _ -> server }
        }

    override fun getDapParameters(): Map<String, Any> {
        (environment.runProfile as? XtcAttachConfiguration)?.let { return it.dapParameters() }
        val parameters = mutableMapOf<String, Any>("type" to "xtc", "request" to "launch")
        (environment.runProfile as? XtcRunConfiguration)?.let { configuration ->
            configuration.moduleName.takeIf { it.isNotBlank() }?.let { parameters["module"] = it }
            configuration.moduleUri.takeIf { it.isNotBlank() }?.let { parameters["moduleUri"] = it }
            configuration.methodName.takeIf { it.isNotBlank() }?.let { parameters["method"] = it }
            parameters["args"] = configuration.embeddedRequest().args
            if (configuration.stopOnEntry) parameters["stopOnEntry"] = true
            if (configuration.evaluationRunsProgram) parameters["evaluationRunsProgram"] = true
        }
        return parameters
    }

    override fun getDebugMode(): DebugMode = DebugMode.LAUNCH

    // TODO LSP4IJ: UP64 — DebugMode.ATTACH replaces the adapter's process with a NopProcessHandler and reaches the
    // adapter by socket only, so an adapter whose transport is its process handler can only be launched; only the
    // client is made in ATTACH mode.

    /**
     * The client of an Ecstasy Attach configuration ([XtcAttachConfiguration]) sends `attach`, and on stop
     * `disconnect`, which detaches; the descriptor's own mode stays [DebugMode.LAUNCH], so that LSP4IJ starts
     * [startServer], which carries the session over the language server's connection.
     */
    override fun createClient(
        debugProcess: DAPDebugProcess,
        dapParameters: Map<String, Any>,
        isDebug: Boolean,
        debugMode: DebugMode,
        serverTrace: ServerTrace,
        parentClient: DAPClient?,
    ): DAPClient =
        super.createClient(
            debugProcess,
            dapParameters,
            isDebug,
            if (environment.runProfile is XtcAttachConfiguration) DebugMode.ATTACH else debugMode,
            serverTrace,
            parentClient,
        )

    override fun getServerReadyConfig(debugMode: DebugMode): ServerReadyConfig = ServerReadyConfig(0)

    override fun getFileType(): FileType? = FileTypeManager.getInstance().getFileTypeByExtension("x")

    override fun isDebuggableFile(
        file: VirtualFile,
        project: Project,
    ): Boolean = file.extension == "x"
}
