package org.xtclang.idea.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPRenameFeature
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.PluginPaths
import java.net.URI
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

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
 * Factory for creating Ecstasy Language Server connections.
 *
 * The server runs OUT-OF-PROCESS as a separate Java process for classloader isolation
 * (avoids lsp4j version conflicts with LSP4IJ) and crash/memory isolation. It uses
 * IntelliJ's own JBR 25 runtime via LSP4IJ's [JavaProcessCommandBuilder].
 */
class XtcLanguageServerFactory : LanguageServerFactory {
    private val logger = logger<XtcLanguageServerFactory>()

    override fun createConnectionProvider(project: Project) =
        XtcLspConnectionProvider(project).also {
            logger.info("Creating XTC LSP connection provider (out-of-process) - ${LspBuildProperties.buildInfo}")
        }

    override fun createLanguageClient(project: Project) = XtcLanguageClient(project)

    override fun createClientFeatures() =
        object : LSPClientFeatures() {
            init {
                // XtcRenameHandler supplies native symbol rename with stale-document checks.
                // Keep LSP4IJ's independent file-operation support enabled.
                setRenameFeature(
                    object : LSPRenameFeature() {
                        override fun isRenameSupported(file: PsiFile): Boolean = false
                    },
                )
            }

            override fun <S : LanguageServer> createLauncherBuilder(): Launcher.Builder<S> =
                object : DefaultLauncherBuilder<S>(this) {
                    private val documents =
                        DocumentStartupMessages { uri ->
                            if (project.isDisposed || serverWrapper.isDisposed) return@DocumentStartupMessages null
                            val opened = serverWrapper.getOpenedDocument(URI(uri)) ?: return@DocumentStartupMessages null
                            val document = opened.synchronizer?.document ?: return@DocumentStartupMessages null
                            // File rename waits for didOpen while holding the IDE write lock.
                            // Transport hooks must use the document's lock-free immutable text;
                            // acquiring a read action here deadlocks that rename on the EDT.
                            DocumentStartupMessages.Snapshot(opened, document.modificationStamp, document.immutableCharSequence.toString())
                        }

                    override fun wrapMessageConsumer(consumer: MessageConsumer): MessageConsumer {
                        val wrapped = super.wrapMessageConsumer(consumer)
                        return if (consumer is RemoteEndpoint) documents.incoming(wrapped) else documents.outgoing(wrapped)
                    }
                }.configureGson {
                    // configureGson replaces the base callback; retain LSP4IJ's compatibility adapters.
                    JSONUtils.configureCompatibilityAdapters(it)
                    it.registerTypeAdapterFactory(ConfigurationJson)
                }
        }

    override fun getServerInterface(): Class<out LanguageServer> = LanguageServer::class.java
}

/**
 * Out-of-process LSP server connection using LSP4IJ's [OSProcessStreamConnectionProvider].
 *
 * Uses [JavaProcessCommandBuilder] to resolve IntelliJ's JBR java binary and build
 * the server command line. We use [OSProcessStreamConnectionProvider] (not the simpler
 * [com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider]) because it
 * leverages IntelliJ's [com.intellij.execution.process.OSProcessHandler] for proper
 * process lifecycle management and stderr capture in LSP4IJ's Language Servers panel.
 *
 * The server JAR is in `bin/` (not `lib/`) to avoid classloader conflicts with
 * LSP4IJ's bundled lsp4j.
 */
class XtcLspConnectionProvider(
    private val project: Project,
) : OSProcessStreamConnectionProvider() {
    private val logger = logger<XtcLspConnectionProvider>()
    private val lifetime = ConnectionLifetime({ super.start() }, { super.stop() })

    companion object {
        private const val LSP_SERVER_JAR = "xtc-lsp-server.jar"
        private const val LANGUAGE_SERVER_ID = "xtcLanguageServer"
        private const val SEMANTIC_TOKENS_SYSTEM_PROPERTY = "xtc.lsp.semanticTokens"
        private const val SEMANTIC_TOKENS_ENV = "XTC_LSP_SEMANTIC_TOKENS"

        /** Ensures we only show the "started" notification once per IDE session. */
        private val startNotificationShown = AtomicBoolean(false)

        /**
         * Resolve the LSP server JAR from a plugin directory.
         * Returns the path to `bin/xtc-lsp-server.jar` if it exists, or null otherwise.
         */
        internal fun resolveServerJar(pluginDir: Path): Path? = PluginPaths.resolveInBin(pluginDir, LSP_SERVER_JAR)
    }

    init {
        val serverJar = findServerJar()

        // Log level: system property > environment variable > INFO default
        val logLevel =
            System.getProperty("xtc.logLevel")?.uppercase()
                ?: System.getenv("XTC_LOG_LEVEL")?.uppercase()
                ?: "INFO"
        val semanticTokens =
            System.getProperty(SEMANTIC_TOKENS_SYSTEM_PROPERTY)
                ?: System.getenv(SEMANTIC_TOKENS_ENV)
                ?: "false"

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
            listOf(
                "-Dapple.awt.UIElement=true", // macOS: no dock icon
                "-Djava.awt.headless=true", // No GUI components
                "-Dxtc.logLevel=$logLevel", // Pass log level to LSP server
                "-D$SEMANTIC_TOKENS_SYSTEM_PROPERTY=$semanticTokens", // Keep semantic tokens opt-in until client rendering is stable
            ) +
                listOf("xtc.trace.directory", "xtc.trace.level").mapNotNull { key ->
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

        logger.info(
            "XTC LSP command configured (v${LspBuildProperties.version}, " +
                "adapter=${LspBuildProperties.adapter}, semanticTokens=$semanticTokens): ${commandLine.commandLineString}",
        )
    }

    override fun start() {
        logger.info("Starting XTC LSP Server (out-of-process via JBR)")
        // LSP4IJ starts on a pooled thread: project disposal or cancellation can stop the
        // provider first. Its OS provider otherwise starts even after its stop flag is set.
        if (project.isDisposed) lifetime.stop()
        lifetime.start()

        logger.info("XTC LSP Server process started (v${LspBuildProperties.version}, adapter=${LspBuildProperties.adapter}, pid=$pid)")

        if (startNotificationShown.compareAndSet(false, true)) {
            showNotification(
                title = "Ecstasy Language Server Started",
                content = "Out-of-process server (v${LspBuildProperties.version}, adapter=${LspBuildProperties.adapter}, pid=$pid)",
                type = NotificationType.INFORMATION,
            )
        }
    }

    override fun stop() {
        logger.info("Stopping XTC LSP Server")
        lifetime.stop()
        logger.info("XTC LSP Server stopped")
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
