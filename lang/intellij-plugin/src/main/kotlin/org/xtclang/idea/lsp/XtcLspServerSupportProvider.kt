package org.xtclang.idea.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.xvm.lsp.adapter.MockXtcCompilerAdapter
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
            Properties().apply {
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
 */
class XtcLspConnectionProvider : StreamConnectionProvider {

    private val logger = logger<XtcLspConnectionProvider>()

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

        // Create and start the language server
        server = XtcLanguageServer(MockXtcCompilerAdapter()).also { srv ->
            LSPLauncher.createServerLauncher(srv, serverInput, serverOutput).also { launcher ->
                srv.connect(launcher.remoteProxy)
                serverFuture = launcher.startListening()
            }
        }
        alive = true

        logger.info("XTC LSP Server started (in-process)")
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
