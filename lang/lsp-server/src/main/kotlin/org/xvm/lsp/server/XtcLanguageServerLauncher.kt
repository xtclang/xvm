@file:JvmName("XtcLanguageServerLauncherKt")

package org.xvm.lsp.server

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.util.ServerLogs
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
 * - The adapter is selected at build time via: ./gradlew :lang:lsp-server:fatJar
 *   -Plsp.adapter=treesitter
 * - Default is 'treesitter' (syntax-aware, requires native library bundled in JAR)
 * - Use 'compiler' for real diagnostics from the Ecstasy compiler and its bundled XDK libraries
 * - Use 'mock' for regex-based features (no native dependencies)
 *
 * Important: This LSP server uses stdio for communication. All logging goes to stderr to keep
 * stdout clean for the JSON-RPC protocol.
 */
private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

// Static initializer runs before any SLF4J initialization to suppress
// SLF4J's informational messages that would otherwise go to stdout and
// corrupt the JSON-RPC protocol stream.
private val initBlock =
    run {
        System.setProperty("slf4j.internal.verbosity", "WARN")
    }

/** Load build properties from the embedded lsp-version.properties file. */
private fun loadBuildProperties(): Properties =
    Properties().apply {
        Thread
            .currentThread()
            .contextClassLoader
            ?.getResourceAsStream("lsp-version.properties")
            ?.use { load(it) }
    }

/** Adapter backend types for the LSP server. */
internal enum class AdapterBackend(
    val displayName: String,
) {
    MOCK("Mock"),
    TREE_SITTER("Tree-sitter"),
    COMPILER("Ecstasy Compiler"),
    ;

    companion object {
        fun fromSetting(setting: String? = null): AdapterBackend =
            when (setting?.lowercase()) {
                "mock" -> {
                    MOCK
                }

                "treesitter",
                "tree-sitter",
                -> {
                    TREE_SITTER
                }

                null,
                "compiler",
                "xtc",
                "full",
                -> {
                    COMPILER
                }

                else -> {
                    throw IllegalArgumentException(
                        "Unknown lsp.adapter '$setting'; expected treesitter, compiler or mock",
                    )
                }
            }
    }
}

/**
 * Create the appropriate adapter based on build configuration.
 *
 * @param requested The backend selected by the build properties
 * @return The configured adapter and which backend is active
 */
private fun createAdapter(requested: AdapterBackend): Pair<Adapter, AdapterBackend> =
    when (requested) {
        AdapterBackend.COMPILER -> {
            logger.info("using the Ecstasy compiler for diagnostics and document symbols")
            XdkAdapter() to AdapterBackend.COMPILER
        }

        AdapterBackend.TREE_SITTER -> {
            try {
                TreeSitterAdapter() to AdapterBackend.TREE_SITTER
            } catch (e: UnsatisfiedLinkError) {
                logger.error(
                    "tree-sitter native library not found, falling back to mock adapter",
                    e,
                )
                logger.warn(
                    "to use tree-sitter, build the native library: ./gradlew :lang:tree-sitter:buildAllNativeLibrariesOnDemand",
                )
                MockAdapter() to AdapterBackend.MOCK
            } catch (e: Exception) {
                logger.error("failed to initialize tree-sitter adapter, falling back to mock", e)
                MockAdapter() to AdapterBackend.MOCK
            }
        }

        AdapterBackend.MOCK -> {
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
    val requested = AdapterBackend.fromSetting(buildProps.getProperty("lsp.adapter"))
    val version = buildProps.getProperty("lsp.version", "unknown")

    // Create adapter based on build configuration
    val (adapter, backend) = createAdapter(requested)

    // Log startup banner prominently
    val logFile = ServerLogs.directory.resolve("server.log").toString()
    runCatching { ServerLogs.prune() }.onFailure { logger.warn("Could not prune inactive Ecstasy log sessions", it) }
    logger.info("========================================")
    logger.info("Ecstasy Language Server v$version")
    logger.info("backend: ${backend.displayName}")
    logger.info("log file: $logFile")
    logger.info("========================================")

    when (backend) {
        AdapterBackend.TREE_SITTER -> {
            logger.info(
                "tree-sitter provides: syntax highlighting, document symbols, completions, go-to-definition",
            )
        }

        AdapterBackend.COMPILER -> {
            logger.info("compiler features are negotiated with the connected editor during initialization")
            logger.info("the compiler uses the XDK libraries bundled with this server")
        }

        AdapterBackend.MOCK -> {
            logger.info("mock backend provides: basic symbol detection (regex-based)")
            if (requested == AdapterBackend.TREE_SITTER) {
                logger.warn(
                    "tree-sitter was requested but failed to initialize - check native library",
                )
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

/** Launch the server using stdio for communication. This is what VS Code and most editors use. */
fun launchStdio(
    server: XtcLanguageServer,
    input: InputStream,
    output: OutputStream,
) {
    // Own the dispatcher as well as the server: LSP4J's default cached platform-thread
    // executor survives EOF, and adapter workers can keep the JVM alive indefinitely.
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val trace = ProtocolTrace(server.clientTrace)
    val lifecycle = ProtocolLifecycle()
    try {
        val launcher: Launcher<LanguageClient> =
            object : Launcher.Builder<LanguageClient>() {
                override fun wrapMessageConsumer(consumer: MessageConsumer): MessageConsumer =
                    lifecycle.wrap(
                        trace.wrap(
                            super.wrapMessageConsumer(consumer),
                            received = consumer is RemoteEndpoint,
                        ),
                        received = consumer is RemoteEndpoint,
                    )
            }.setLocalService(server)
                .setRemoteInterface(LanguageClient::class.java)
                .setInput(input)
                .setOutput(output)
                .setExecutorService(executor)
                .create()
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
        trace.close()
        executor.shutdownNow()
        server.close()
    }
}
