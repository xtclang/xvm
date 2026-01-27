@file:JvmName("XtcLanguageServerLauncherKt")

package org.xvm.lsp.server

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.MockXtcCompilerAdapter
import org.xvm.lsp.adapter.TreeSitterAdapter
import org.xvm.lsp.adapter.XtcCompilerAdapter
import java.io.InputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.util.Properties

/**
 * Launcher for the XTC Language Server.
 *
 * Usage:
 * - For stdio communication: `java -jar xtc-lsp.jar`
 * - For socket communication: `java -jar xtc-lsp.jar --socket 5007`
 *
 * Adapter Selection:
 * - The adapter is selected at build time via: ./gradlew :lang:lsp-server:fatJar -Plsp.adapter=treesitter
 * - Default is 'mock' (regex-based, no native dependencies)
 * - Use 'treesitter' for syntax-aware features (requires native library)
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
private fun createAdapter(adapterType: String): Pair<XtcCompilerAdapter, AdapterBackend> =
    when (adapterType.lowercase()) {
        "compiler", "xtc", "full" -> {
            // TODO: Replace with XtcCompilerAdapter when parallel compiler integration is ready
            // See PLAN_LSP_PARALLEL_LEXER.md for the integration roadmap
            throw UnsupportedOperationException(
                "XTC Compiler adapter not yet implemented. " +
                    "Use 'treesitter' for syntax-aware features or 'mock' for basic testing.",
            )
        }
        "treesitter", "tree-sitter" -> {
            try {
                TreeSitterAdapter() to AdapterBackend.TREE_SITTER
            } catch (e: UnsatisfiedLinkError) {
                logger.error("Tree-sitter native library not found, falling back to mock adapter", e)
                logger.warn("To use tree-sitter, build the native library: ./gradlew :lang:dsl:buildTreeSitterLibrary")
                MockXtcCompilerAdapter() to AdapterBackend.MOCK
            } catch (e: Exception) {
                logger.error("Failed to initialize Tree-sitter adapter, falling back to mock", e)
                MockXtcCompilerAdapter() to AdapterBackend.MOCK
            }
        }
        else -> MockXtcCompilerAdapter() to AdapterBackend.MOCK
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
    logger.info("========================================")
    logger.info("XTC Language Server v$version")
    logger.info("Backend: ${backend.displayName}")
    logger.info("========================================")

    when (backend) {
        AdapterBackend.TREE_SITTER -> {
            logger.info("Tree-sitter provides: syntax highlighting, document symbols, completions, go-to-definition")
        }
        AdapterBackend.COMPILER -> {
            logger.info("XTC Compiler provides: full semantic analysis, type inference, cross-file navigation")
        }
        AdapterBackend.MOCK -> {
            logger.info("Mock backend provides: basic symbol detection (regex-based)")
            if (adapterType.lowercase() in listOf("treesitter", "tree-sitter")) {
                logger.warn("Tree-sitter was requested but failed to initialize - check native library")
            }
        }
    }

    // Create the server
    val server = XtcLanguageServer(adapter)

    // Launch with stdio
    launchStdio(server, System.`in`, System.out)
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
    val launcher: Launcher<LanguageClient> =
        LSPLauncher.createServerLauncher(
            server,
            input,
            output,
        )

    val client: LanguageClient = launcher.remoteProxy
    server.connect(client)

    runCatching {
        launcher.startListening().get()
    }.onFailure { e ->
        when (e) {
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                logger.error("Server interrupted", e)
            }
            else -> logger.error("Server error", e)
        }
    }
}
