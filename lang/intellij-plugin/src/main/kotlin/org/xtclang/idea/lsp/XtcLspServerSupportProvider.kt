package org.xtclang.idea.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Factory for creating XTC Language Server instances.
 *
 * The LSP server JAR is located via:
 * 1. XDK_HOME environment variable (preferred)
 * 2. Bundled with the plugin (fallback)
 *
 * Communication is via stdio (stdin/stdout).
 */
class XtcLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return XtcStreamConnectionProvider()
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl {
        return LanguageClientImpl(project)
    }

    override fun getServerInterface(): Class<out LanguageServer> {
        return LanguageServer::class.java
    }
}

/**
 * Provides the connection to the XTC LSP server process.
 * Launches the server as a subprocess and communicates via stdio.
 */
class XtcStreamConnectionProvider : StreamConnectionProvider {

    private var process: Process? = null

    override fun start() {
        val command = buildCommand()
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(false)

        process = processBuilder.start()
    }

    override fun getInputStream(): InputStream {
        return process?.inputStream
            ?: throw IllegalStateException("LSP server process not started")
    }

    override fun getOutputStream(): OutputStream {
        return process?.outputStream
            ?: throw IllegalStateException("LSP server process not started")
    }

    fun getErrorStream(): InputStream? {
        return process?.errorStream
    }

    override fun stop() {
        process?.let { p ->
            p.outputStream.close()
            p.destroy()
            if (p.isAlive) {
                p.destroyForcibly()
            }
        }
        process = null
    }

    override fun isAlive(): Boolean {
        return process?.isAlive == true
    }

    private fun buildCommand(): List<String> {
        val serverJar = findLspServerJar()
        val javaExe = findJavaExecutable()

        return listOf(javaExe, "-jar", serverJar)
    }

    private fun findLspServerJar(): String {
        // Try XDK_HOME first
        val xdkHome = System.getenv("XDK_HOME")
        if (xdkHome != null) {
            val xdkJar = File(xdkHome, "lib/xtc-lsp-all.jar")
            if (xdkJar.exists()) {
                return xdkJar.absolutePath
            }
        }

        // Try relative to plugin installation (for bundled JAR)
        val pluginPath = javaClass.protectionDomain.codeSource?.location?.toURI()?.let {
            File(it).parentFile
        }
        if (pluginPath != null) {
            val bundledJar = File(pluginPath, "xtc-lsp-all.jar")
            if (bundledJar.exists()) {
                return bundledJar.absolutePath
            }
        }

        throw IllegalStateException(
            """
            XTC LSP server not found.

            Please ensure one of:
            1. Set XDK_HOME environment variable to your XTC installation
            2. The xtc-lsp-all.jar is bundled with the plugin

            Expected locations:
            - ${'$'}XDK_HOME/lib/xtc-lsp-all.jar
            """.trimIndent()
        )
    }

    private fun findJavaExecutable(): String {
        // Try JAVA_HOME first
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            val javaExe = if (System.getProperty("os.name").lowercase().contains("win")) {
                File(javaHome, "bin/java.exe")
            } else {
                File(javaHome, "bin/java")
            }
            if (javaExe.exists()) {
                return javaExe.absolutePath
            }
        }

        // Fallback to 'java' in PATH
        return "java"
    }
}
