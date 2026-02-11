@file:JvmName("XtcDebugServerLauncherKt")

package org.xvm.debug

import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandles

/**
 * Launcher for the XTC Debug Adapter Protocol (DAP) server.
 *
 * Usage: `java -jar xtc-debug-adapter.jar`
 *
 * The DAP server uses stdio for communication. All logging goes to stderr
 * to keep stdout clean for the JSON-RPC protocol.
 */

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

// Suppress SLF4J informational messages that would corrupt the JSON-RPC protocol stream.
private val initBlock =
    run {
        System.setProperty("slf4j.internal.verbosity", "WARN")
    }

fun main(
    @Suppress("UNUSED_PARAMETER") args: Array<String>,
) {
    // Ensure init block runs
    @Suppress("UNUSED_EXPRESSION")
    initBlock

    logger.info("========================================")
    logger.info("XTC Debug Adapter (DAP) Server")
    logger.info("========================================")

    val server = XtcDebugServer()
    launchStdio(server, System.`in`, System.out)
}

/**
 * Launch the DAP server using stdio for communication.
 */
fun launchStdio(
    server: XtcDebugServer,
    input: InputStream,
    output: OutputStream,
) {
    val launcher: Launcher<IDebugProtocolClient> =
        DSPLauncher.createServerLauncher(
            server,
            input,
            output,
        )

    val client: IDebugProtocolClient = launcher.remoteProxy
    server.connect(client)

    runCatching {
        launcher.startListening().get()
    }.onFailure { e ->
        when (e) {
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                logger.error("DAP server interrupted", e)
            }
            else -> logger.error("DAP server error", e)
        }
    }
}
