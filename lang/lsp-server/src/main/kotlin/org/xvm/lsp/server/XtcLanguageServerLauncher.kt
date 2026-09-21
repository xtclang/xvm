@file:JvmName("XtcLanguageServerLauncherKt")

package org.xvm.lsp.server

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.io.InputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Launcher for the Ecstasy Language Server.
 *
 * Usage:
 * - For stdio communication: `java -jar xtc-lsp.jar`
 * - For socket communication: `java -jar xtc-lsp.jar --socket 5007`
 *
 * Adapter Selection:
 * - The adapter is selected at build time via: ./gradlew :lang:lsp-server:fatJar -Plsp.adapter=treesitter
 * - Default is 'treesitter' (syntax-aware, requires native library bundled in JAR)
 * - Use 'compiler' for real diagnostics from the XTC compiler (needs an XDK on XDK_HOME)
 * - Use 'mock' for regex-based features (no native dependencies)
 *
 * Important: This LSP server uses stdio for communication. All logging goes to stderr
 * to keep stdout clean for the JSON-RPC protocol.
 */

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

// Static initializer runs before any SLF4J initialization to suppress
// SLF4J's informational messages that would otherwise go to stdout and
// corrupt the JSON-RPC protocol stream.
private val initBlock =
    run {
        System.setProperty("slf4j.internal.verbosity", "WARN")
    }

/**
 * Load build properties from the embedded lsp-version.properties file.
 */
private fun loadBuildProperties(): Properties =
    Properties().apply {
        Thread
            .currentThread()
            .contextClassLoader
            ?.getResourceAsStream("lsp-version.properties")
            ?.use { load(it) }
    }

/**
 * Adapter backend types for the LSP server.
 */
private enum class AdapterBackend(
    val displayName: String,
) {
    MOCK("Mock"),
    TREE_SITTER("Tree-sitter"),
    COMPILER("XTC Compiler"),
}

/**
 * Create the appropriate adapter based on build configuration.
 *
 * @param adapterType The adapter type from build properties: "mock", "treesitter", or "compiler"
 * @return The configured adapter and which backend is active
 */
private fun createAdapter(adapterType: String): Pair<Adapter, AdapterBackend> =
    when (adapterType.lowercase()) {
        "compiler", "xtc", "full" -> {
            logger.info("using the XTC compiler for diagnostics and document symbols")
            XdkAdapter() to AdapterBackend.COMPILER
        }

        "treesitter", "tree-sitter" -> {
            try {
                TreeSitterAdapter() to AdapterBackend.TREE_SITTER
            } catch (e: UnsatisfiedLinkError) {
                logger.error("tree-sitter native library not found, falling back to mock adapter", e)
                logger.warn(
                    "to use tree-sitter, build the native library: ./gradlew :lang:tree-sitter:buildAllNativeLibrariesOnDemand",
                )
                MockAdapter() to AdapterBackend.MOCK
            } catch (e: Exception) {
                logger.error("failed to initialize tree-sitter adapter, falling back to mock", e)
                MockAdapter() to AdapterBackend.MOCK
            }
        }

        else -> {
            MockAdapter() to AdapterBackend.MOCK
        }
    }

fun main(
    @Suppress("UNUSED_PARAMETER") args: Array<String>,
) {
    // Ensure init block runs
    @Suppress("UNUSED_EXPRESSION")
    initBlock

    // Load build properties to determine adapter type
    val buildProps = loadBuildProperties()
    val adapterType = buildProps.getProperty("lsp.adapter", "mock")
    val version = buildProps.getProperty("lsp.version", "unknown")

    // Create adapter based on build configuration
    val (adapter, backend) = createAdapter(adapterType)

    // Log startup banner prominently
    val logFile = "${System.getProperty("user.home")}/.xtc/logs/lsp-server.log"
    logger.info("========================================")
    logger.info("Ecstasy Language Server v$version")
    logger.info("backend: ${backend.displayName}")
    logger.info("log file: $logFile")
    logger.info("========================================")

    when (backend) {
        AdapterBackend.TREE_SITTER -> {
            logger.info("tree-sitter provides: syntax highlighting, document symbols, completions, go-to-definition")
        }

        AdapterBackend.COMPILER -> {
            logger.info("the compiler provides: syntax and semantic diagnostics, document symbols")
            logger.info("not yet from the compiler: completion, go-to-definition, references, formatting")
            logger.info("an XDK is required; without one, files open but report XDK-UNAVAILABLE")
        }

        AdapterBackend.MOCK -> {
            logger.info("mock backend provides: basic symbol detection (regex-based)")
            if (adapterType.lowercase() in listOf("treesitter", "tree-sitter")) {
                logger.warn("tree-sitter was requested but failed to initialize - check native library")
            }
        }
    }

    // Create the server
    val server = XtcLanguageServer(adapter, ::exitProcess)

    // Launch with stdio
    try {
        launchStdio(server, System.`in`, System.out)
    } finally {
        // EOF can replace the exit notification when the IDE closes or crashes.
        server.exit()
    }
}

/**
 * Launch the server using stdio for communication.
 * This is what VS Code and most editors use.
 */
fun launchStdio(
    server: XtcLanguageServer,
    input: InputStream,
    output: OutputStream,
) {
    // Own the dispatcher as well as the server: LSP4J's default cached platform-thread
    // executor survives EOF, and adapter workers can keep the JVM alive indefinitely.
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    try {
        val launcher: Launcher<LanguageClient> =
            LSPLauncher.createServerLauncher(server, input, output, executor) { it }
        server.connect(launcher.remoteProxy)
        launcher.startListening().get()
    } catch (e: Exception) {
        when (e) {
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                logger.error("server interrupted", e)
            }

            else -> {
                logger.error("server error", e)
            }
        }
    } finally {
        executor.shutdownNow()
        server.close()
    }
}
