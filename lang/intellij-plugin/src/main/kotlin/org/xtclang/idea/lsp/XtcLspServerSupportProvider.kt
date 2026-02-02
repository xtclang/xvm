package org.xtclang.idea.lsp

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Factory for creating XTC Language Server connections.
 *
 * The server runs OUT-OF-PROCESS as a separate Java process because:
 * 1. jtreesitter requires Java [XtcLspConnectionProvider.MIN_JAVA_VERSION]+ (Foreign Function & Memory API)
 * 2. IntelliJ uses JBR 21 which doesn't support FFM
 * 3. Out-of-process allows using a newer Java toolchain
 *
 * See doc/plans/PLAN_OUT_OF_PROCESS_LSP.md for architecture details.
 */
class XtcLanguageServerFactory : LanguageServerFactory {
    private val logger = logger<XtcLanguageServerFactory>()

    private val buildInfo: String by lazy {
        val stream = XtcLanguageServerFactory::class.java.getResourceAsStream("/lsp-version.properties")
        if (stream == null) {
            logger.error(
                "lsp-version.properties not found in plugin resources! " +
                    "This indicates a build issue - the file should be copied from lsp-server during build.",
            )
            return@lazy "ERROR: version properties not bundled"
        }

        runCatching {
            Properties().apply { stream.use { load(it) } }.let { props ->
                val version = props.getProperty("lsp.version")
                val buildTime = props.getProperty("lsp.build.time")
                if (version == null || buildTime == null) {
                    logger.warn("lsp-version.properties is missing expected keys: version=$version, buildTime=$buildTime")
                }
                "v${version ?: "?"} built ${buildTime ?: "?"}"
            }
        }.getOrElse { e ->
            logger.error("Failed to parse lsp-version.properties", e)
            "ERROR: ${e.message}"
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
 * Out-of-process LSP server connection.
 *
 * Spawns the XTC Language Server as a separate Java process with Java 24+,
 * communicating via stdio. This enables jtreesitter/FFM support regardless
 * of IntelliJ's JBR version.
 *
 * Java Runtime Resolution:
 * 1. System property `xtc.lsp.java.home` (explicit override)
 * 2. Environment variable `XTC_JAVA_HOME` (XDK toolchain)
 * 3. Environment variable `JAVA_HOME` (system default)
 * 4. Fail with helpful error message
 *
 * The Java runtime MUST be version [MIN_JAVA_VERSION]+ for tree-sitter to work.
 */
class XtcLspConnectionProvider(
    private val project: Project,
) : StreamConnectionProvider {
    private val logger = logger<XtcLspConnectionProvider>()

    companion object {
        /**
         * Minimum Java version required for the out-of-process LSP server.
         *
         * This is determined by jtreesitter's Foreign Function & Memory (FFM) API requirements:
         * - jtreesitter 0.26+ requires Java 23+
         *
         * Update this constant when upgrading jtreesitter to a version with different requirements.
         */
        const val MIN_JAVA_VERSION = 23

        /** JSON-RPC protocol version */
        private const val JSONRPC_VERSION = "2.0"

        /** Request ID used for the shutdown request during server stop */
        private const val SHUTDOWN_REQUEST_ID = 99999
    }

    private val buildProps: Properties by lazy {
        Properties().apply {
            val stream = XtcLspConnectionProvider::class.java.getResourceAsStream("/lsp-version.properties")
            if (stream == null) {
                logger.error(
                    "lsp-version.properties not found in plugin resources! " +
                        "Cannot determine LSP server version. This indicates a build issue.",
                )
            } else {
                stream.use { load(it) }
            }
        }
    }

    private var process: Process? = null
    private var stderrForwarder: Thread? = null
    private var processMonitor: Thread? = null

    @Volatile
    private var alive = false

    @Volatile
    private var intentionalShutdown = false

    override fun start() {
        logger.info("Starting XTC LSP Server (out-of-process)")

        val javaPath = findJavaExecutable()
        val serverJar = findServerJar()

        logger.info("Using Java: $javaPath")
        logger.info("Using LSP server JAR: $serverJar")

        // Build the command line
        val command =
            listOf(
                javaPath.toString(),
                "--enable-native-access=ALL-UNNAMED", // FFM API for tree-sitter native libraries
                "-Dapple.awt.UIElement=true", // macOS: no dock icon
                "-Djava.awt.headless=true", // No GUI components
                "-Xms32m", // Modest initial heap
                "-Xmx256m", // Cap memory usage
                "-jar",
                serverJar.toString(),
            )

        logger.info("Launching: ${command.joinToString(" ")}")

        // Start the process
        val processBuilder =
            ProcessBuilder(command)
                .redirectErrorStream(false) // Keep stderr separate for logging
                .apply {
                    project.basePath?.let { directory(File(it)) }
                }

        intentionalShutdown = false

        process =
            processBuilder.start().also { proc ->
                // Start stderr forwarder immediately
                stderrForwarder = startStderrForwarder(proc)
                // Start process monitor to detect crashes
                processMonitor = startProcessMonitor(proc)
            }

        alive = true

        val version = buildProps.getProperty("lsp.version", "?")
        val adapterType = buildProps.getProperty("lsp.adapter", "mock")
        logger.info("XTC LSP Server process started (v$version, adapter=$adapterType, pid=${process?.pid()})")

        showNotification(
            title = "XTC Language Server Started",
            content = "Out-of-process server (v$version, adapter=$adapterType)",
            type = NotificationType.INFORMATION,
        )
    }

    /**
     * Find a Java [MIN_JAVA_VERSION]+ executable for running the LSP server.
     *
     * Resolution order:
     * 1. System property `xtc.lsp.java.home`
     * 2. Environment variable `XTC_JAVA_HOME`
     * 3. Environment variable `JAVA_HOME`
     *
     * @throws IllegalStateException if no suitable Java is found
     */
    private fun findJavaExecutable(): Path {
        val candidates =
            listOfNotNull(
                System.getProperty("xtc.lsp.java.home"),
                System.getenv("XTC_JAVA_HOME"),
                System.getenv("JAVA_HOME"),
            )

        for (javaHome in candidates) {
            val executable = resolveJavaExecutable(Path.of(javaHome))
            if (executable != null && Files.isExecutable(executable)) {
                val version = getJavaVersion(executable)
                logger.info("Found Java $version at $executable")
                if (version != null && version >= MIN_JAVA_VERSION) {
                    return executable
                } else {
                    logger.warn("Java at $executable is version $version (need $MIN_JAVA_VERSION+), trying next")
                }
            }
        }

        // If no JAVA_HOME is set, check if 'java' is on PATH and meets version requirement
        val pathJava = findJavaOnPath()
        if (pathJava != null) {
            val version = getJavaVersion(pathJava)
            if (version != null && version >= MIN_JAVA_VERSION) {
                logger.info("Using java from PATH: $pathJava (version $version)")
                return pathJava
            }
        }

        val errorMsg =
            """
            No Java $MIN_JAVA_VERSION+ runtime found for XTC Language Server.

            The tree-sitter adapter requires Java $MIN_JAVA_VERSION+ (Foreign Function & Memory API).

            Please set one of:
            - System property: -Dxtc.lsp.java.home=/path/to/java$MIN_JAVA_VERSION+
            - Environment variable: XTC_JAVA_HOME=/path/to/java$MIN_JAVA_VERSION+
            - Environment variable: JAVA_HOME=/path/to/java$MIN_JAVA_VERSION+

            You can download a suitable JDK from:
            - https://adoptium.net/ (Eclipse Temurin)
            - https://sdkman.io/ (SDK Manager)

            Tried: ${candidates.ifEmpty { listOf("(none set)") }}
            """.trimIndent()

        logger.error(errorMsg)
        showNotification(
            title = "XTC Language Server Error",
            content = "No Java $MIN_JAVA_VERSION+ runtime found. Set JAVA_HOME or XTC_JAVA_HOME to a Java $MIN_JAVA_VERSION+ installation.",
            type = NotificationType.ERROR,
        )

        throw IllegalStateException(errorMsg)
    }

    private fun resolveJavaExecutable(javaHome: Path): Path? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = if (isWindows) "java.exe" else "java"
        val candidate = javaHome.resolve("bin").resolve(executable)
        return if (Files.exists(candidate)) candidate else null
    }

    private fun findJavaOnPath(): Path? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = if (isWindows) "java.exe" else "java"
        val pathEnv = System.getenv("PATH") ?: return null

        val separator = if (isWindows) ";" else ":"
        for (dir in pathEnv.split(separator)) {
            val candidate = Path.of(dir, executable)
            if (Files.isExecutable(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun getJavaVersion(javaExecutable: Path): Int? =
        try {
            val process =
                ProcessBuilder(javaExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse version from output like:
            // openjdk version "24" 2025-03-18
            // openjdk version "21.0.1" 2023-10-17
            val versionRegex = """version "(\d+)""".toRegex()
            versionRegex
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        } catch (e: Exception) {
            logger.debug("Failed to get Java version from $javaExecutable", e)
            null
        }

    /**
     * Find the LSP server JAR in the plugin's bin directory.
     *
     * IMPORTANT: The JAR must be in bin/, NOT lib/. If placed in lib/, IntelliJ
     * loads its bundled lsp4j classes which conflict with LSP4IJ's lsp4j.
     */
    private fun findServerJar(): Path {
        // The JAR is bundled with the plugin at bin/xtc-lsp-server.jar
        // We need to find our plugin's installation directory

        // Try to find via class loader resource - our class is in lib/, JAR is in bin/
        val classUrl = javaClass.protectionDomain?.codeSource?.location
        if (classUrl != null) {
            val pluginLibDir = Path.of(classUrl.toURI()).parent
            val pluginDir = pluginLibDir.parent // Go from lib/ to plugin root
            val serverJar = pluginDir.resolve("bin").resolve("xtc-lsp-server.jar")
            if (Files.exists(serverJar)) {
                return serverJar
            }
            logger.debug("LSP server JAR not at expected location: $serverJar")
        }

        // Fallback: search in typical plugin locations
        val pluginPaths =
            listOf(
                System.getProperty("idea.plugins.path"),
                System.getProperty("user.home") + "/.local/share/JetBrains/IntelliJIdea2025.1/plugins",
                System.getProperty("user.home") + "/Library/Application Support/JetBrains/IntelliJIdea2025.1/plugins",
            ).filterNotNull()

        for (pluginsDir in pluginPaths) {
            val serverJar = Path.of(pluginsDir, "intellij-plugin", "bin", "xtc-lsp-server.jar")
            if (Files.exists(serverJar)) {
                return serverJar
            }
        }

        throw IllegalStateException(
            """
            LSP server JAR not found.

            Expected at: <plugin-dir>/bin/xtc-lsp-server.jar (NOT lib/ to avoid classloader conflicts)

            This is a plugin packaging issue. Please report it.
            """.trimIndent(),
        )
    }

    /**
     * Start a daemon thread to forward the server's stderr to the console.
     * This makes LSP server logs visible in Gradle's runIde output.
     */
    private fun startStderrForwarder(proc: Process): Thread =
        Thread({
            proc.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    // Forward to System.err so it appears in Gradle console during runIde
                    System.err.println("[XTC-LSP] $line")

                    // Also log via IntelliJ logger for production log files
                    when {
                        line.contains("ERROR") || line.contains("Exception") ->
                            logger.error("LSP: $line")
                        line.contains("WARN") ->
                            logger.warn("LSP: $line")
                        else ->
                            logger.info("LSP: $line")
                    }
                }
            }
        }, "XTC-LSP-stderr-forwarder").apply {
            isDaemon = true
            start()
        }

    /**
     * Start a daemon thread to monitor the server process for unexpected termination.
     * Shows a notification with restart option if the server crashes.
     */
    private fun startProcessMonitor(proc: Process): Thread =
        Thread({
            try {
                val exitCode = proc.waitFor()
                if (!intentionalShutdown && alive) {
                    // Server crashed unexpectedly
                    logger.error("XTC LSP Server crashed with exit code $exitCode")
                    alive = false
                    showCrashNotification(exitCode)
                }
            } catch (e: InterruptedException) {
                // Monitor was interrupted during shutdown - normal
                Thread.currentThread().interrupt()
            }
        }, "XTC-LSP-process-monitor").apply {
            isDaemon = true
            start()
        }

    /**
     * Show a notification when the server crashes with an option to restart.
     */
    private fun showCrashNotification(exitCode: Int) {
        val notification =
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("XTC Language Server")
                .createNotification(
                    "XTC Language Server Crashed",
                    "The language server terminated unexpectedly (exit code $exitCode). " +
                        "Some language features may not work until the server is restarted.",
                    NotificationType.ERROR,
                ).addAction(
                    NotificationAction.createSimple("Restart Server") {
                        restartServer()
                    },
                )

        notification.notify(project)
    }

    /**
     * Restart the LSP server by stopping and starting it again.
     */
    private fun restartServer() {
        logger.info("Restarting XTC LSP Server...")
        runCatching {
            // Use LSP4IJ's LanguageServerManager to restart
            LanguageServerManager.getInstance(project).stop("xtc")
        }.onSuccess {
            // LSP4IJ will automatically restart when needed
            showNotification(
                title = "XTC Language Server",
                content = "Server restart initiated. Open an XTC file to reconnect.",
                type = NotificationType.INFORMATION,
            )
        }.onFailure { e ->
            logger.error("Failed to restart LSP server", e)
            showNotification(
                title = "XTC Language Server Error",
                content = "Failed to restart server: ${e.message}",
                type = NotificationType.ERROR,
            )
        }
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

    override fun getInputStream(): InputStream =
        process?.inputStream
            ?: throw IllegalStateException("LSP server process not started")

    override fun getOutputStream(): OutputStream =
        process?.outputStream
            ?: throw IllegalStateException("LSP server process not started")

    override fun isAlive(): Boolean {
        val proc = process ?: return false
        return alive && proc.isAlive
    }

    override fun stop() {
        logger.info("Stopping XTC LSP Server")
        intentionalShutdown = true
        alive = false

        val proc = process ?: return

        if (proc.isAlive) {
            runCatching {
                sendShutdownRequest(proc)
                sendExitNotification(proc)
                proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            }.onFailure { e ->
                logger.warn("Error during LSP server shutdown", e)
            }

            if (proc.isAlive) {
                logger.warn("LSP server did not exit gracefully, forcing termination")
                proc.destroyForcibly()
            }
        }

        processMonitor?.interrupt()
        processMonitor = null
        stderrForwarder?.interrupt()
        stderrForwarder = null
        process = null

        logger.info("XTC LSP Server stopped")
    }

    private fun sendShutdownRequest(proc: Process) {
        val request =
            buildJsonObject {
                put("jsonrpc", JSONRPC_VERSION)
                put("id", SHUTDOWN_REQUEST_ID)
                put("method", "shutdown")
            }.toString()
        sendJsonRpcMessage(proc, request)
    }

    private fun sendExitNotification(proc: Process) {
        val notification =
            buildJsonObject {
                put("jsonrpc", JSONRPC_VERSION)
                put("method", "exit")
            }.toString()
        sendJsonRpcMessage(proc, notification)
    }

    private fun sendJsonRpcMessage(
        proc: Process,
        message: String,
    ) {
        val header = "Content-Length: ${message.length}\r\n\r\n"
        proc.outputStream.write((header + message).toByteArray())
        proc.outputStream.flush()
    }
}
