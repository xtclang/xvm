package org.xtclang.idea.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.xvm.lsp.adapter.MockXtcCompilerAdapter
import org.xvm.lsp.adapter.TreeSitterAdapter
import org.xvm.lsp.adapter.XtcCompilerAdapter
import org.xvm.lsp.server.XtcLanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Properties
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Factory for creating XTC Language Server connections.
 * The server runs in-process using piped streams for communication.
 */
class XtcLanguageServerFactory : LanguageServerFactory {
    private val logger = logger<XtcLanguageServerFactory>()

    private val buildInfo: String by lazy {
        runCatching {
            Properties()
                .apply {
                    XtcLanguageServerFactory::class.java
                        .getResourceAsStream("/lsp-version.properties")
                        ?.use { load(it) }
                }.let { props ->
                    "v${props.getProperty("lsp.version", "?")} built ${props.getProperty("lsp.build.time", "?")}"
                }
        }.getOrDefault("unknown")
    }

    override fun createConnectionProvider(project: Project) =
        XtcLspConnectionProvider().also {
            logger.info("Creating XTC LSP connection provider - $buildInfo")
        }

    override fun createLanguageClient(project: Project) = LanguageClientImpl(project)

    override fun getServerInterface(): Class<out LanguageServer> = LanguageServer::class.java
}

/**
 * In-process LSP server connection using piped streams.
 *
 * Runs the XTC Language Server in the same JVM as IntelliJ,
 * communicating via piped input/output streams. This avoids subprocess
 * management and JAR path resolution issues.
 *
 * Adapter Selection:
 * The adapter is selected at build time via: ./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter
 * Default is 'mock' (regex-based, no native dependencies).
 */
class XtcLspConnectionProvider : StreamConnectionProvider {
    private val logger = logger<XtcLspConnectionProvider>()

    private val buildProps: Properties by lazy {
        Properties().apply {
            XtcLspConnectionProvider::class.java
                .getResourceAsStream("/lsp-version.properties")
                ?.use { load(it) }
        }
    }

    /**
     * Create the appropriate adapter based on build configuration.
     */
    private fun createAdapter(): Pair<XtcCompilerAdapter, String> {
        val adapterType = buildProps.getProperty("lsp.adapter", "mock")
        return when (adapterType.lowercase()) {
            "treesitter", "tree-sitter" -> {
                try {
                    TreeSitterAdapter() to "TreeSitterAdapter"
                } catch (e: UnsatisfiedLinkError) {
                    logger.warn("Tree-sitter native library not found, falling back to mock adapter", e)
                    MockXtcCompilerAdapter() to "MockXtcCompilerAdapter (fallback - tree-sitter native lib missing)"
                } catch (e: Exception) {
                    logger.warn("Failed to initialize Tree-sitter adapter, falling back to mock", e)
                    MockXtcCompilerAdapter() to "MockXtcCompilerAdapter (fallback - tree-sitter init failed)"
                }
            }
            else -> MockXtcCompilerAdapter() to "MockXtcCompilerAdapter"
        }
    }

    private lateinit var clientInput: PipedInputStream
    private lateinit var clientOutput: PipedOutputStream
    private var server: XtcLanguageServer? = null
    private var serverFuture: Future<Void>? = null

    @Volatile
    private var alive = false

    override fun start() {
        logger.info("Starting XTC LSP Server (in-process)")

        // Create piped streams for bidirectional communication
        val serverInput = PipedInputStream()
        val serverOutput = PipedOutputStream()
        clientOutput = PipedOutputStream(serverInput)
        clientInput = PipedInputStream(serverOutput)

        // Create adapter - currently hardcoded to Mock, will support tree-sitter later
        val adapter = MockXtcCompilerAdapter()
        val adapterName = adapter::class.simpleName ?: "Unknown"

        // Create and start the language server
        server =
            XtcLanguageServer(adapter).also { srv ->
                LSPLauncher.createServerLauncher(srv, serverInput, serverOutput).also { launcher ->
                    srv.connect(launcher.remoteProxy)
                    serverFuture = launcher.startListening()
                }
            }
        alive = true

        logger.info("XTC LSP Server started (in-process) with adapter: $adapterName")
    }

    override fun getInputStream(): InputStream = clientInput

    override fun getOutputStream(): OutputStream = clientOutput

    override fun isAlive() = alive && serverFuture?.isDone != true

    override fun stop() {
        logger.info("Stopping XTC LSP Server")
        alive = false

        // Graceful shutdown with timeout
        server?.let { srv ->
            runCatching { srv.shutdown()?.get(2, TimeUnit.SECONDS) }
                .onFailure { logger.debug("Server shutdown: ${it.message}") }
            runCatching { srv.exit() }
        }

        serverFuture?.cancel(true)
        serverFuture = null

        // Close streams (ignore errors - pipe may already be broken)
        runCatching { clientOutput.close() }
        runCatching { clientInput.close() }

        server = null
        logger.info("XTC LSP Server stopped")
    }
}
