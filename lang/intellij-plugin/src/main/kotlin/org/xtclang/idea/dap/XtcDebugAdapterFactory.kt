package org.xtclang.idea.dap

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.DebugMode
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig
import org.xtclang.idea.lsp.jre.JreProvisioner
import java.nio.file.Files
import java.nio.file.Path

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

    /**
     * Find the DAP server JAR in the plugin's bin directory.
     * Follows the same convention as the LSP server JAR.
     */
    private fun findDapServerJar(): Path {
        PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))?.let { plugin ->
            val serverJar = plugin.pluginPath.resolve("bin/xtc-dap-server.jar")
            if (Files.exists(serverJar)) return serverJar
            logger.warn("DAP server JAR not found: $serverJar")
        }

        throw ExecutionException(
            "DAP server JAR not found. Expected at: <plugin-dir>/bin/xtc-dap-server.jar",
        )
    }
}
