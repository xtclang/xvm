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
import org.xtclang.idea.lsp.jre.JreProvisioner
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

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

    private val buildInfo: String by lazy {
        javaClass.getResourceAsStream("/lsp-version.properties")?.use { stream ->
            runCatching {
                Properties().apply { load(stream) }.let { props ->
                    val version = props.getProperty("lsp.version")
                    val buildTime = props.getProperty("lsp.build.time")
                    "v${version ?: "?"} built ${buildTime ?: "?"}"
                }
            }.getOrElse { e ->
                logger.error("Failed to parse lsp-version.properties", e)
                "ERROR: ${e.message}"
            }
        } ?: run {
            logger.error("lsp-version.properties not found in plugin resources!")
            "ERROR: version properties not bundled"
        }
    }

    override fun createConnectionProvider(project: Project) =
        XtcLspConnectionProvider(project).also {
            logger.info("Creating XTC LSP connection provider (out-of-process) - $buildInfo")
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

    /** Holds the provisioned java path once available. */
    private val provisionedJavaPath = AtomicReference<Path?>()

    private val buildProps: Properties by lazy {
        Properties().apply {
            javaClass.getResourceAsStream("/lsp-version.properties")?.use { load(it) }
                ?: logger.error("lsp-version.properties not found in plugin resources!")
        }
    }

    init {
        // Check if JRE is already provisioned (instant check, no download)
        provisioner.javaPath?.let { java ->
            logger.info("Using cached JRE: $java")
            configureCommandLine(java)
        }
        // If not provisioned, commandLine will be configured during start() with progress
    }

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

        val version = buildProps.getProperty("lsp.version", "?")
        val adapterType = buildProps.getProperty("lsp.adapter", "mock")
        logger.info("XTC LSP Server process started (v$version, adapter=$adapterType, pid=$pid)")

        showNotification(
            title = "XTC Language Server Started",
            content = "Out-of-process server (v$version, adapter=$adapterType)",
            type = NotificationType.INFORMATION,
        )
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

        // Log level: check -Dlsp.logLevel (case-insensitive), default to INFO
        val logLevel = System.getProperty("lsp.logLevel")?.uppercase() ?: "INFO"

        val commandLine =
            GeneralCommandLine(
                javaPath.toString(),
                "--enable-native-access=ALL-UNNAMED", // FFM API for tree-sitter native libraries
                "-Dapple.awt.UIElement=true", // macOS: no dock icon
                "-Djava.awt.headless=true", // No GUI components
                "-Dlsp.logLevel=$logLevel", // Pass log level to LSP server
                "-Xms32m", // Modest initial heap
                "-Xmx256m", // Cap memory usage
                "-jar",
                serverJar.toString(),
            ).apply {
                project.basePath?.let { withWorkDirectory(it) }
            }

        setCommandLine(commandLine)
        provisionedJavaPath.set(javaPath)

        val version = buildProps.getProperty("lsp.version", "?")
        val adapterType = buildProps.getProperty("lsp.adapter", "mock")
        logger.info("XTC LSP command configured (v$version, adapter=$adapterType): ${commandLine.commandLineString}")
    }

    /**
     * Find the LSP server JAR in the plugin's bin directory.
     *
     * IMPORTANT: The JAR must be in bin/, NOT lib/. If placed in lib/, IntelliJ
     * loads its bundled lsp4j classes which conflict with LSP4IJ's lsp4j.
     */
    private fun findServerJar(): Path {
        // Try to find via class loader resource - our class is in lib/, JAR is in bin/
        javaClass.protectionDomain?.codeSource?.location?.let { classUrl ->
            val pluginLibDir = Path.of(classUrl.toURI()).parent
            val pluginDir = pluginLibDir.parent // Go from lib/ to plugin root
            val serverJar = pluginDir.resolve("bin/xtc-lsp-server.jar")
            if (Files.exists(serverJar)) return serverJar
            logger.debug("LSP server JAR not at expected location: $serverJar")
        }

        // Fallback: search in typical plugin locations
        listOfNotNull(
            System.getProperty("idea.plugins.path"),
            "${System.getProperty("user.home")}/.local/share/JetBrains/IntelliJIdea2025.1/plugins",
            "${System.getProperty("user.home")}/Library/Application Support/JetBrains/IntelliJIdea2025.1/plugins",
        ).map { Path.of(it, "intellij-plugin", "bin", "xtc-lsp-server.jar") }
            .firstOrNull { Files.exists(it) }
            ?.let { return it }

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
