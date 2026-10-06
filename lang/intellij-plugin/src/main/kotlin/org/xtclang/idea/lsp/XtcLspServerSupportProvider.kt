package org.xtclang.idea.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.PluginPaths
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared build properties loaded once at class initialization time. This avoids concurrent
 * getResourceAsStream() calls which can cause "Inflater wants input" errors in IntelliJ's
 * PluginClassLoader.
 */
private object LspBuildProperties {
    private val logger = logger<LspBuildProperties>()

    val properties: Properties =
        Properties().apply {
            LspBuildProperties::class.java.getResourceAsStream("/lsp-version.properties")?.use {
                load(it)
            } ?: logger.error("lsp-version.properties not found in plugin resources!")
        }

    val version: String
        get() = properties.getProperty("lsp.version", "?")

    val adapter: String
        get() = properties.getProperty("lsp.adapter", "compiler")

    val buildTime: String
        get() = properties.getProperty("lsp.build.time", "?")

    val buildInfo: String
        get() = "v$version built $buildTime"
}

/**
 * Factory for creating Ecstasy Language Server connections.
 *
 * The server runs OUT-OF-PROCESS as a separate Java process for classloader isolation (avoids lsp4j
 * version conflicts with LSP4IJ) and crash/memory isolation. It uses IntelliJ's own JBR 25 runtime
 * via LSP4IJ's [JavaProcessCommandBuilder].
 */
class XtcLanguageServerFactory : LanguageServerFactory {
    private val logger = logger<XtcLanguageServerFactory>()

    override fun createConnectionProvider(project: Project) =
        XtcLspConnectionProvider(project).also {
            logger.info(
                "Creating Ecstasy LSP connection provider (out-of-process) - ${LspBuildProperties.buildInfo}",
            )
        }

    override fun createLanguageClient(project: Project) = XtcLanguageClient(project)

    override fun createClientFeatures(): LSPClientFeatures = XtcClientFeatures()

    override fun getServerInterface(): Class<out LanguageServer> = XtcLanguageServer::class.java
}

/**
 * Out-of-process LSP server connection using LSP4IJ's [OSProcessStreamConnectionProvider].
 *
 * Uses [JavaProcessCommandBuilder] to resolve IntelliJ's JBR java binary and build the server
 * command line. We use [OSProcessStreamConnectionProvider] (not the simpler
 * [com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider]) because it leverages
 * IntelliJ's [com.intellij.execution.process.OSProcessHandler] for proper process lifecycle
 * management and stderr capture in LSP4IJ's Language Servers panel.
 *
 * The server JAR is in `bin/` (not `lib/`) to avoid classloader conflicts with LSP4IJ's bundled
 * lsp4j.
 */
class XtcLspConnectionProvider(
    private val project: Project,
) : OSProcessStreamConnectionProvider() {
    private val logger = logger<XtcLspConnectionProvider>()
    private val support = project.getService(ServerSupportLogs::class.java)
    private val launch = support.begin("Resolving the bundled Ecstasy server and Java runtime.\n")
    private val lifetime = ConnectionLifetime({ super.start() }, { super.stop() })

    companion object {
        private const val LSP_SERVER_JAR = "xtc-lsp-server.jar"
        private const val LANGUAGE_SERVER_ID = "xtcLanguageServer"
        private const val SEMANTIC_TOKENS_SYSTEM_PROPERTY = "xtc.lsp.semanticTokens"
        private const val SEMANTIC_TOKENS_ENV = "XTC_LSP_SEMANTIC_TOKENS"

        /** Ensures we only show the "started" notification once per IDE session. */
        private val startNotificationShown = AtomicBoolean(false)

        /**
         * Resolve the LSP server JAR from a plugin directory. Returns the path to
         * `bin/xtc-lsp-server.jar` if it exists, or null otherwise.
         */
        internal fun resolveServerJar(pluginDir: Path): Path? = PluginPaths.resolveInBin(pluginDir, LSP_SERVER_JAR)
    }

    init {
        addLogErrorHandler { support.append(launch, it) }
        try {
            configureCommand()
        } catch (failure: Exception) {
            support.append(launch, "Startup failed: ${failure.message}\n")
            throw failure
        }
    }

    private fun configureCommand() {
        val serverJar = findServerJar()

        // Log level: system property > environment variable > INFO default
        val logLevel =
            System.getProperty("xtc.logLevel")?.uppercase()
                ?: System.getenv("XTC_LOG_LEVEL")?.uppercase()
                ?: "INFO"
        val semanticTokens =
            System.getProperty(SEMANTIC_TOKENS_SYSTEM_PROPERTY)
                ?: System.getenv(SEMANTIC_TOKENS_ENV)
                ?: "true"

        // JavaProcessCommandBuilder resolves IntelliJ's JBR java binary automatically
        // and handles debug port configuration from LSP4IJ's per-server settings.
        val commands =
            JavaProcessCommandBuilder(project, LANGUAGE_SERVER_ID)
                .setJar(serverJar.toString())
                .create()

        // Insert JVM args before -jar (JavaProcessCommandBuilder doesn't support custom VM args)
        val jarIndex = commands.indexOf("-jar")
        commands.addAll(
            jarIndex,
            ServerRuntimeSettings.getInstance().launchArguments() +
                listOf(
                    "-Dapple.awt.UIElement=true", // macOS: no dock icon
                    "-Djava.awt.headless=true", // No GUI components
                    "-Dxtc.logLevel=$logLevel", // Pass log level to LSP server
                    "-D$SEMANTIC_TOKENS_SYSTEM_PROPERTY=$semanticTokens",
                ) +
                listOf("xtc.trace.directory", "xtc.trace.level", "xtc.logs.directory").mapNotNull { key ->
                    System.getProperty(key)?.let { "-D$key=$it" }
                },
        )

        // Convert to GeneralCommandLine for OSProcessStreamConnectionProvider.
        // OSProcessStreamConnectionProvider uses IntelliJ's OSProcessHandler which
        // captures stderr and feeds it to LSP4IJ's Language Servers panel log tab.
        val commandLine =
            GeneralCommandLine(commands).apply {
                project.basePath?.let { withWorkDirectory(it) }
            }
        setCommandLine(commandLine)
        support.append(launch, "Command: ${commandLine.commandLineString}\n")

        logger.info(
            "Ecstasy LSP command configured (v${LspBuildProperties.version}, " +
                "adapter=${LspBuildProperties.adapter}, semanticTokens=$semanticTokens): ${commandLine.commandLineString}",
        )
    }

    override fun getInitializationOptions(rootUri: VirtualFile?): Any =
        LanguageServiceSettings
            .validated(project)
            // TODO LSP4IJ: UP02 — native willSaveWaitUntil is absent; keep server save edits disabled.
            .copy(saveFormatting = "editor")
            .initializationOptions()

    override fun start() {
        logger.info("Starting Ecstasy LSP Server (out-of-process via JBR)")
        // TODO LSP4IJ: UP01 — make OS process start/stop atomic and reject starts after stop/disposal.
        // LSP4IJ starts on a pooled thread: project disposal or cancellation can stop the
        // provider first. Its OS provider otherwise starts even after its stop flag is set.
        if (project.isDisposed) lifetime.stop()
        try {
            lifetime.start()
            pid?.let { support.process(launch, it) }
        } catch (failure: Exception) {
            support.append(launch, "Startup failed: ${failure.message}\n")
            throw failure
        }

        logger.info(
            "Ecstasy LSP Server process started (v${LspBuildProperties.version}, adapter=${LspBuildProperties.adapter}, pid=$pid)",
        )

        if (startNotificationShown.compareAndSet(false, true)) {
            val labelColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
            showNotification(
                title = "Ecstasy Language Server Started",
                content =
                    listOf(
                        "Version" to LspBuildProperties.version,
                        "Adapter" to LspBuildProperties.adapter,
                        "PID" to pid.toString(),
                    ).joinToString(
                        separator = "&nbsp;&nbsp;·&nbsp;&nbsp;",
                        prefix = "<small>",
                        postfix = "</small>",
                    ) { (label, value) ->
                        "<font color='$labelColor'>$label</font>&nbsp;${StringUtil.escapeXmlEntities(value)}"
                    },
                type = NotificationType.INFORMATION,
            )
        }
    }

    override fun stop() {
        logger.info("Stopping Ecstasy LSP Server")
        lifetime.stop()
        support.append(launch, "\nConnection stopped.\n")
        logger.info("Ecstasy LSP Server stopped")
    }

    private fun findServerJar(): Path = PluginPaths.findServerJar(LSP_SERVER_JAR)

    private fun showNotification(
        title: String,
        content: String,
        type: NotificationType,
    ) {
        object : Notification("XTC Language Server", title, content, type) {
            override fun setBalloon(balloon: Balloon) {
                super.setBalloon(balloon)
                // The IDE timer pauses during interaction and hides only the balloon, keeping
                // the startup details in Notifications and the log for later inspection.
                (balloon as? BalloonImpl)?.apply {
                    startSmartFadeoutTimer(8_000)
                    // Smart fadeout alone waits for input before starting its clock. Also
                    // schedule it now so an untouched startup balloon disappears.
                    startFadeoutTimer(8_000)
                }
            }
        }.notify(project)
    }
}
