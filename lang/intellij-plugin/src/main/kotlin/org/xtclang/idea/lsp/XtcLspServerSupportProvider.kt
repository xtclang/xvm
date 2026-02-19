package org.xtclang.idea.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.PluginPaths
import org.xtclang.idea.lsp.jre.JreProvisioner
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
        private const val LSP_SERVER_JAR = "xtc-lsp-server.jar"

        /** Ensures we only show the "started" notification once per IDE session. */
        private val startNotificationShown = AtomicBoolean(false)

        /**
         * Resolve the LSP server JAR from a plugin directory.
         * Returns the path to `bin/xtc-lsp-server.jar` if it exists, or null otherwise.
         */
        internal fun resolveServerJar(pluginDir: Path): Path? = PluginPaths.resolveInBin(pluginDir, LSP_SERVER_JAR)
    }

    /** Holds the provisioned java path once available. */
    private val provisionedJavaPath = AtomicReference<Path?>()

    // No init {} block -- JRE resolution calls ProjectJdkTable.getInstance() which is
    // prohibited on EDT. All JRE resolution is deferred to start() which runs off EDT.

    // TODO: Remove AtomicBoolean notification guard once LSP4IJ fixes duplicate server spawning.
    //  LSP4IJ may call start() concurrently for multiple .x files, spawning extra processes
    //  that are killed within milliseconds. Harmless, but we guard notifications with a static
    //  AtomicBoolean to avoid duplicates. See: https://github.com/redhat-developer/lsp4ij/issues/888

    override fun start() {
        // Try to find an already-provisioned JRE (cached or system SDK).
        // This is done here (not in init {}) because findSystemJava() calls
        // ProjectJdkTable.getInstance() which is prohibited on EDT.
        if (provisionedJavaPath.get() == null) {
            provisioner.javaPath?.let { java ->
                logger.info("Using cached JRE: $java")
                configureCommandLine(java)
            }
        }
        // If still not configured, download JRE with progress dialog
        if (provisionedJavaPath.get() == null) {
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

        // The server performs a health check during initialize() and logs the result.
        // Server-side: adapter.healthCheck() validates native lib, then workspace indexing begins.
        // TODO: Send xtc/healthCheck from client via LanguageServerManager for diagnostic logging
        //   once LSP4IJ exposes a post-initialization hook for custom requests.
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

        // Log level: system property > environment variable > INFO default
        val logLevel =
            System.getProperty("xtc.logLevel")?.uppercase()
                ?: System.getenv("XTC_LOG_LEVEL")?.uppercase()
                ?: "INFO"

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

    private fun findServerJar(): Path = PluginPaths.findServerJar(LSP_SERVER_JAR)

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
