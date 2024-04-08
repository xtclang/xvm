import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

import org.gradle.api.logging.Logger

/**
 * Common build logic that does build system agnostic process interaction.
 *
 * TODO: Update to support more modes, and not just waitFor (block until exit).
 *  Then we can e.g. start an XTC application in the background from the build system, and then exit the build,
 *  with the spawned process still running. We could then initiate a new "build" where we stop the background
 *  process. For example, for the platform this is done with a curl//cookies line. We could abstract this to
 *  "./gradlew down" or something similar. We can also use this logic outside the build system to perform
 *  the same kinds of operations, both in a command line based standalone system, and in e,g. integration tests.
 */

// An exit value from a git process execution + its output
data class ProcessResult(val execResult: Pair<Int, String>, val failure: Throwable? = null) {
    @Suppress("MemberVisibilityCanBePrivate")
    val exitValue: Int = execResult.first
    val output: String = execResult.second
    fun lines(): List<String> = output.lines()
    fun isSuccessful(): Boolean = exitValue == 0
    fun rethrowFailure() {
        if (failure != null) {
            throw failure
        }
    }
}

fun spawn(command: String, vararg args: String, throwOnError: Boolean = true, logger: Logger? = null): ProcessResult {
    fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
        return this.bufferedReader(charset).use { it.readText() }
    }

    fun pathFor(command: String): String {
        val path = System.getenv("PATH")
            .split(File.pathSeparator)
            .map { File(it, command) }
            .filter { it.exists() && it.canExecute() }
            .map { it.canonicalFile } // follow symlinks.
            .firstOrNull()

        if (path == null) {
            throw IllegalStateException("Cannot find executable '$command' in PATH: ${System.getenv("PATH")}")
        }
        return path.absolutePath
    }

    val commandPath = pathFor(command)
    val commandLine = listOf(commandPath, *args)
    val builder = ProcessBuilder(commandLine).redirectErrorStream(true)

    logger?.info("[processes] Spawning process: '${commandLine.joinToString(" ")}'")

    val processResult = try {
        val process = builder.start() // can throw IOException
        val exitValue = process.waitFor()
        val result = process.inputStream.readTextAndClose()
        var failure: IllegalStateException? = null
        if (exitValue != 0) {
            failure = IllegalStateException("""
                    |Process: '$command' ($commandLine) failed with exit value $exitValue.
                    |$result
                """.trimIndent()
            )
        }
        ProcessResult(exitValue to result.trim(), failure)
    } catch (t: Throwable) {
        t.printStackTrace(System.err)
        ProcessResult(-1 to "", IllegalStateException("Process: '$command' ($commandLine) unexpectedly failed at runtime.", t))
    }

    return processResult.also {
        if (throwOnError) {
            it.rethrowFailure()
        }
    }
}
