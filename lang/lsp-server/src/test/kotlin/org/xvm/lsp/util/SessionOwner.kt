package org.xvm.lsp.util

import java.nio.file.Files
import java.nio.file.Path

/**
 * Owns log sessions and directories as a server does, in a process of its own, for [ServerLogsTest] and
 * [ProcessDirectoriesTest]. With `server` it starts [ServerLogs], which holds its own session, prunes the log root and
 * prints the session's directory. Otherwise it takes the directory its first input line names: `share` shares it, as
 * the servers of one build share their libraries'; `process` holds it by the lock [SessionLock.beside] it, as a server
 * holds its library source copies, and then reads every file in it, as a compile reads a module's resources to
 * fingerprint them; any other mode holds it as a log session. Each prints whether it could, and holds on until its
 * input ends.
 */
object SessionOwner {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader()
        val held =
            when (args.single()) {
                "server" -> {
                    ServerLogs.prune()
                    println(ServerLogs.directory)
                    null
                }

                "share" -> {
                    val directory = Path.of(input.readLine())
                    SessionLock.share(SessionLock.beside(directory)).also { println(it != null) }
                }

                "process" -> {
                    val directory = Path.of(input.readLine())
                    SessionLock.hold(directory, SessionLock.beside(directory)).also { lock ->
                        Files.walk(directory).use { paths -> paths.filter(Files::isRegularFile).forEach(Files::readAllBytes) }
                        println(lock != null)
                    }
                }

                else -> SessionLock.hold(Path.of(input.readLine())).also { println(it != null) }
            }
        System.out.flush()
        input.readText()
        held?.channel()?.close()
    }
}
