package org.xvm.lsp.util

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/**
 * Owns log sessions and directories as a server does, in a process of its own, for [ServerLogsTest] and
 * [ProcessDirectoriesTest]. With `server` it starts [ServerLogs], which holds its own session, prunes the log root and
 * prints the session's directory. Otherwise it takes the directory its first input line names: `share` shares it, as
 * the servers of one build share their libraries'; `process` holds it by the lock [SessionLock.beside] it, as a server
 * holds its library source copies, and then reads every file in it, as a compile reads a module's resources to
 * fingerprint them; `processes` holds each directory that its input lines name, up to an empty line, that way, and
 * prints for each whether it could; `guard` locks the file that its input line names, as a server takes the guard of a
 * kind of directories ([SessionLock.guard]); any other mode holds it as a log session. Each prints whether it could, and
 * holds on until its input ends.
 */
object SessionOwner {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader()
        val held: List<FileLock?> =
            when (args.single()) {
                "server" -> {
                    ServerLogs.prune()
                    println(ServerLogs.directory)
                    emptyList()
                }

                "share" -> {
                    val directory = Path.of(input.readLine())
                    listOf(SessionLock.share(SessionLock.beside(directory)).also { println(it != null) })
                }

                "process" -> {
                    val directory = Path.of(input.readLine())
                    listOf(
                        SessionLock.hold(directory, SessionLock.beside(directory)).also { lock ->
                            Files.walk(directory).use { paths -> paths.filter(Files::isRegularFile).forEach(Files::readAllBytes) }
                            println(lock != null)
                        },
                    )
                }

                "processes" ->
                    generateSequence { input.readLine()?.takeIf(String::isNotEmpty) }
                        .map { Path.of(it) }
                        .map { directory -> SessionLock.hold(directory, SessionLock.beside(directory)).also { println(it != null) } }
                        .toList()

                "guard" -> listOf(FileChannel.open(Path.of(input.readLine()), CREATE, WRITE).lock().also { println(true) })

                else -> listOf(SessionLock.hold(Path.of(input.readLine())).also { println(it != null) })
            }
        System.out.flush()
        input.readText()
        held.forEach { it?.channel()?.close() }
    }
}
