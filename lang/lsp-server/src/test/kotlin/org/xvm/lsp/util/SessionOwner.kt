package org.xvm.lsp.util

import java.nio.file.Path

/**
 * Owns log sessions as a server does, in a process of its own, for [ServerLogsTest]. With `server` it starts
 * [ServerLogs], which holds its own session, prunes the log root and prints the session's directory; otherwise it
 * holds the session its first input line names and prints whether it could. It holds them until its input ends.
 */
object SessionOwner {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader()
        val held =
            if (args.single() == "server") {
                ServerLogs.prune()
                println(ServerLogs.directory)
                null
            } else {
                SessionLock.hold(Path.of(input.readLine())).also { println(it != null) }
            }
        System.out.flush()
        input.readText()
        held?.channel()?.close()
    }
}
