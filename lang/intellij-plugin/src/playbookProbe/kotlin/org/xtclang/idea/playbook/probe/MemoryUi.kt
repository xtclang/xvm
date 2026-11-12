package org.xtclang.idea.playbook.probe

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.management.ObjectName

/**
 * Reports where the driven IDE's memory, and its language server's, goes, for a run's record: the IDE's native
 * memory summary, which it tracks with -XX:NativeMemoryTracking=summary, and the language server's heap, metaspace
 * and largest classes on its heap, which jcmd reads from its process.
 */
object MemoryUi {
    /** The jar the plugin starts the language server from (XtcLspServerSupportProvider.LSP_SERVER_JAR). */
    private const val SERVER_JAR = "xtc-lsp-server.jar"

    @JvmStatic
    fun report(): String =
        buildString {
            val self = ProcessHandle.current().pid()
            appendLine("== IDE, pid $self: ${resident(self)}")
            appendLine(diagnostic("vmNativeMemory", "summary"))
            ProcessHandle
                .allProcesses()
                .filter { process ->
                    process
                        .info()
                        .commandLine()
                        .orElse("")
                        .contains(SERVER_JAR)
                }.forEach { server ->
                    val pid = server.pid()
                    appendLine("== language server, pid $pid: ${resident(pid)}")
                    listOf("GC.heap_info", "VM.metaspace", "GC.class_histogram").forEach { command ->
                        val lines = jcmd(pid, command).lines()
                        appendLine(if (command == "GC.class_histogram") lines.take(30).joinToString("\n") else lines.joinToString("\n"))
                    }
                }
        }

    /** The process's resident and swapped memory, from /proc, or nothing where /proc does not have it. */
    private fun resident(pid: Long): String =
        runCatching {
            Files.readAllLines(Path.of("/proc/$pid/status")).filter { it.startsWith("VmRSS") || it.startsWith("VmSwap") }.joinToString("; ")
        }.getOrDefault("")

    private fun diagnostic(
        operation: String,
        vararg arguments: String,
    ): String =
        runCatching {
            ManagementFactory.getPlatformMBeanServer().invoke(
                ObjectName("com.sun.management:type=DiagnosticCommand"),
                operation,
                arrayOf<Any>(arguments),
                arrayOf(Array<String>::class.java.name),
            ) as String
        }.getOrElse { "$operation failed: $it" }

    private fun jcmd(
        pid: Long,
        command: String,
    ): String =
        runCatching {
            val jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd").toString()
            val process = ProcessBuilder(jcmd, pid.toString(), command).redirectErrorStream(true).start()
            val output = process.inputReader().readText()
            process.waitFor(5, TimeUnit.MINUTES)
            output
        }.getOrElse { "jcmd $command failed: $it" }
}
