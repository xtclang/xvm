package org.xtclang.idea.dap

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig
import org.xtclang.idea.PluginPaths
import org.xtclang.idea.lsp.jre.JreProvisioner

/**
 * Factory for creating XTC Debug Adapter (DAP) descriptors.
 *
 * Registered via the `com.redhat.devtools.lsp4ij.debugAdapterServer` extension point.
 * LSP4IJ uses this factory to create DAP sessions when users launch debug configurations
 * for `.x` files.
 *
 * The DAP server runs out-of-process (same as the LSP server) using a provisioned JRE,
 * since it may require Java 25+ features.
 */
class XtcDebugAdapterFactory : DebugAdapterDescriptorFactory() {
    private val logger = logger<XtcDebugAdapterFactory>()

    override fun createDebugAdapterDescriptor(
        options: RunConfigurationOptions,
        environment: ExecutionEnvironment,
    ): DebugAdapterDescriptor {
        logger.info("Creating XTC DAP descriptor")
        return XtcDebugAdapterDescriptor(options, environment, serverDefinition)
    }

    override fun isDebuggableFile(
        file: VirtualFile,
        project: Project,
    ): Boolean = file.extension == "x"
}

/**
 * Descriptor that launches the XTC DAP server as an out-of-process Java application.
 *
 * The DAP server communicates over stdio (JSON-RPC), matching the architecture of the
 * LSP server. JRE provisioning reuses the same [JreProvisioner] infrastructure.
 *
 * ## Out-of-process and JBR 21 compatibility
 *
 * Both LSP and DAP servers require Java 25+ (for jtreesitter's Foreign Function & Memory API),
 * but IntelliJ runs on JBR 21 which lacks FFM support. This works because the plugin itself
 * (running in JBR 21) only launches the server as a *child process* using a provisioned Java 25
 * JRE — the plugin never loads server classes into IntelliJ's JVM.
 *
 * ## LSP vs DAP process lifecycle
 *
 * The LSP and DAP servers use different LSP4IJ base classes with different process models:
 *
 * - **LSP** ([org.xtclang.idea.lsp.XtcLspConnectionProvider]): Extends
 *   `OSProcessStreamConnectionProvider`. We configure a `GeneralCommandLine` and call
 *   `setCommandLine()` — LSP4IJ owns the process lifecycle, calling `start()`/`stop()` as needed.
 *   LSP4IJ may auto-start the server concurrently when multiple `.x` files are opened, causing
 *   duplicate processes (see TODO in `XtcLspConnectionProvider` re: LSP4IJ issue #888). This
 *   requires an `AtomicBoolean` guard to suppress duplicate "server started" notifications.
 *
 * - **DAP** (this class): Extends `DebugAdapterDescriptor`. We override [startServer] and return
 *   an `OSProcessHandler` — we create and own the process directly. DAP sessions are always
 *   user-initiated (one `startServer()` call per "Debug" action), so there is no concurrent
 *   spawn race condition and no `AtomicBoolean` guard is needed.
 */
class XtcDebugAdapterDescriptor(
    options: RunConfigurationOptions,
    environment: ExecutionEnvironment,
    serverDefinition: com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition?,
) : DebugAdapterDescriptor(options, environment, serverDefinition) {
    private val logger = logger<XtcDebugAdapterDescriptor>()
    private val provisioner = JreProvisioner()

    override fun startServer(): ProcessHandler {
        val javaPath =
            provisioner.javaPath
                ?: throw ExecutionException("JRE not provisioned. Open an .x file first to trigger JRE download.")

        val serverJar = findDapServerJar()
        val logLevel = System.getProperty("xtc.logLevel")?.uppercase() ?: "INFO"

        val commandLine =
            GeneralCommandLine(
                javaPath.toString(),
                "-Dapple.awt.UIElement=true",
                "-Djava.awt.headless=true",
                "-Dxtc.logLevel=$logLevel",
                "-jar",
                serverJar.toString(),
            )

        logger.info("Starting XTC DAP server: ${commandLine.commandLineString}")
        return OSProcessHandler(commandLine)
    }

    override fun getDapParameters(): Map<String, Any> {
        val params =
            mutableMapOf<String, Any>(
                "type" to "xtc",
                "request" to "launch",
            )
        // Pass the working directory if available from the run configuration
        environment.project.basePath?.let { params["cwd"] = it }
        return params
    }

    override fun getDebugMode(): DebugMode = DebugMode.LAUNCH

    override fun getServerReadyConfig(debugMode: DebugMode): ServerReadyConfig = ServerReadyConfig("XTC Debug Adapter")

    override fun getFileType(): FileType? = FileTypeManager.getInstance().getFileTypeByExtension("x")

    override fun isDebuggableFile(
        file: VirtualFile,
        project: Project,
    ): Boolean = file.extension == "x"

    companion object {
        private const val DAP_SERVER_JAR = "xtc-dap-server.jar"
    }

    private fun findDapServerJar() = PluginPaths.findServerJar(DAP_SERVER_JAR)
}
