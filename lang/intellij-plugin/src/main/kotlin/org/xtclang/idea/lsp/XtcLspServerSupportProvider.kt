package org.xtclang.idea.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.lsp.jre.JreProvisioner
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared build properties loaded once at class initialization time.
 * This avoids concurrent getResourceAsStream() calls which can cause
 * "Inflater wants input" errors in IntelliJ's PluginClassLoader.
 */
private object LspBuildProperties {
    private val logger = logger<LspBuildProperties>()

    val properties: Properties =
        Properties().apply {
            LspBuildProperties::class.java.getResourceAsStream("/lsp-version.properties")?.use { load(it) }
                ?: logger.error("lsp-version.properties not found in plugin resources!")
        }

    val version: String get() = properties.getProperty("lsp.version", "?")
    val adapter: String get() = properties.getProperty("lsp.adapter", "mock")
    val buildTime: String get() = properties.getProperty("lsp.build.time", "?")
    val buildInfo: String get() = "v$version built $buildTime"
}

/**
 * Factory for creating XTC Language Server connections.
 *
 * The server runs OUT-OF-PROCESS as a separate Java process because:
 * 1. jtreesitter requires Java 25+ (Foreign Function & Memory API)
 * 2. IntelliJ uses JBR 21 which doesn't support FFM
 * 3. Out-of-process allows using a provisioned Java toolchain via Foojay
 *
 * See doc/plans/lsp-processes.md for architecture details.
 */
class XtcLanguageServerFactory : LanguageServerFactory {
    private val logger = logger<XtcLanguageServerFactory>()

    override fun createConnectionProvider(project: Project) =
        XtcLspConnectionProvider(project).also {
            logger.info("Creating XTC LSP connection provider (out-of-process) - ${LspBuildProperties.buildInfo}")
        }

    override fun createLanguageClient(project: Project) = LanguageClientImpl(project)

    override fun getServerInterface(): Class<out LanguageServer> = LanguageServer::class.java
}

/**
 * Out-of-process LSP server connection using LSP4IJ's OSProcessStreamConnectionProvider.
 *
 * JRE Provisioning:
 * - Uses Foojay Disco API to download Eclipse Temurin JRE 25
 * - Caches in ~/.xtc/jre/temurin-25-jre/
 * - Shows progress notification during first-time download
 *
 * See doc/plans/lsp-processes.md for architecture details.
 */
class XtcLspConnectionProvider(
    private val project: Project,
) : OSProcessStreamConnectionProvider() {
    private val logger = logger<XtcLspConnectionProvider>()
    private val provisioner = JreProvisioner()

    companion object {
        /** Ensures we only show the "started" notification once per IDE session. */
        private val startNotificationShown = AtomicBoolean(false)

        /**
         * Resolve the LSP server JAR from a plugin directory.
         * Returns the path to `bin/xtc-lsp-server.jar` if it exists, or null otherwise.
         */
        internal fun resolveServerJar(pluginDir: Path): Path? {
            val serverJar = pluginDir.resolve("bin/xtc-lsp-server.jar")
            return if (Files.exists(serverJar)) serverJar else null
        }
    }

    /** Holds the provisioned java path once available. */
    private val provisionedJavaPath = AtomicReference<Path?>()

    init {
        // Check if JRE is already provisioned (instant check, no download)
        provisioner.javaPath?.let { java ->
            logger.info("Using cached JRE: $java")
            configureCommandLine(java)
        }
        // If not provisioned, commandLine will be configured during start() with progress
    }

    // TODO: Remove AtomicBoolean notification guard once LSP4IJ fixes duplicate server spawning.
    //  LSP4IJ may call start() concurrently for multiple .x files, spawning extra processes
    //  that are killed within milliseconds. Harmless, but we guard notifications with a static
    //  AtomicBoolean to avoid duplicates. See: https://github.com/redhat-developer/lsp4ij/issues/888

    override fun start() {
        // If command line not yet configured, provision JRE with progress
        if (provisionedJavaPath.get() == null && !provisioner.isProvisioned()) {
            provisionJreWithProgress()
        }

        // Verify we have a valid configuration
        if (provisionedJavaPath.get() == null) {
            val msg = "JRE provisioning failed - cannot start LSP server"
            logger.error(msg)
            showNotification("XTC Language Server Error", msg, NotificationType.ERROR)
            return
        }

        logger.info("Starting XTC LSP Server (out-of-process via OSProcessStreamConnectionProvider)")
        super.start()

        logger.info("XTC LSP Server process started (v${LspBuildProperties.version}, adapter=${LspBuildProperties.adapter}, pid=$pid)")

        if (startNotificationShown.compareAndSet(false, true)) {
            showNotification(
                title = "XTC Language Server Started",
                content = "Out-of-process server (v${LspBuildProperties.version}, adapter=${LspBuildProperties.adapter}, pid=$pid)",
                type = NotificationType.INFORMATION,
            )
        }
    }

    override fun stop() {
        logger.info("Stopping XTC LSP Server")
        super.stop()
        logger.info("XTC LSP Server stopped")
    }

    private fun provisionJreWithProgress() {
        ProgressManager.getInstance().run(
            object : Task.WithResult<Path?, Exception>(
                project,
                "Downloading Java Runtime for XTC...",
                true, // cancellable
            ) {
                override fun compute(indicator: ProgressIndicator): Path? {
                    indicator.isIndeterminate = false
                    return runCatching {
                        provisioner.provision { progress, message ->
                            indicator.fraction = progress.toDouble()
                            indicator.text = message
                        }
                    }.onSuccess(::configureCommandLine)
                        .onFailure { e ->
                            logger.error("JRE provisioning failed", e)
                            showNotification(
                                "XTC Language Server Error",
                                "Could not download Java runtime: ${e.message}",
                                NotificationType.ERROR,
                            )
                        }.getOrNull()
                }
            },
        )
    }

    private fun configureCommandLine(javaPath: Path) {
        val serverJar = findServerJar()

        logger.info("Using Java: $javaPath")
        logger.info("Using LSP server JAR: $serverJar")

        // Log level: check -Dxtc.logLevel (case-insensitive), default to INFO
        val logLevel = System.getProperty("xtc.logLevel")?.uppercase() ?: "INFO"

        // FFM API is finalized since Java 22. The --enable-native-access flag is unnecessary
        // on Java 22+ and may trigger experimental feature consent dialogs on older JVMs.
        // We target Java 25, so no flag is needed.
        val commandLine =
            GeneralCommandLine(
                javaPath.toString(),
                "-Dapple.awt.UIElement=true", // macOS: no dock icon
                "-Djava.awt.headless=true", // No GUI components
                "-Dxtc.logLevel=$logLevel", // Pass log level to LSP server
                "-jar",
                serverJar.toString(),
            ).apply {
                project.basePath?.let { withWorkDirectory(it) }
            }

        setCommandLine(commandLine)
        provisionedJavaPath.set(javaPath)

        logger.info(
            "XTC LSP command configured (v${LspBuildProperties.version}, " +
                "adapter=${LspBuildProperties.adapter}): ${commandLine.commandLineString}",
        )
    }

    /**
     * Find the LSP server JAR in the plugin's bin directory.
     *
     * IMPORTANT: The JAR must be in bin/, NOT lib/. If placed in lib/, IntelliJ
     * loads its bundled lsp4j classes which conflict with LSP4IJ's lsp4j.
     */
    private fun findServerJar(): Path {
        // Primary: use PluginManagerCore to find the plugin directory (works for all IDE versions)
        PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))?.let { plugin ->
            resolveServerJar(plugin.pluginPath)?.let { return it }
            logger.warn("LSP server JAR not at expected location: ${plugin.pluginPath}/bin/xtc-lsp-server.jar")
            logger.warn("Plugin directory contents: ${plugin.pluginPath.toFile().listFiles()?.map { it.name }}")
        }

        // Fallback: find via classloader (our class is in lib/, JAR is in bin/)
        javaClass.protectionDomain?.codeSource?.location?.let { classUrl ->
            val pluginDir = Path.of(classUrl.toURI()).parent.parent
            resolveServerJar(pluginDir)?.let { return it }
            logger.warn("LSP server JAR not found via classloader either: $pluginDir/bin/xtc-lsp-server.jar")
        }

        throw IllegalStateException(
            """
            LSP server JAR not found.
            Expected at: <plugin-dir>/bin/xtc-lsp-server.jar (NOT lib/ to avoid classloader conflicts)
            This is a plugin packaging issue. Please report it.
            """.trimIndent(),
        )
    }

    private fun showNotification(
        title: String,
        content: String,
        type: NotificationType,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("XTC Language Server")
            .createNotification(title, content, type)
            .notify(project)
    }
}
