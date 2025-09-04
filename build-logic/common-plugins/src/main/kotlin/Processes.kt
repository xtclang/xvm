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

fun spawn(command: String, vararg args: String, env: Map<String, String> = emptyMap(), workingDir: File? = null, throwOnError: Boolean = true, logger: Logger? = null, redirectErrorStream: Boolean = true): ProcessResult {
    fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
        return this.bufferedReader(charset).use { it.readText() }
    }

    // note: this seems like a really bad idea -- to poorly re-implement OS functionality in a
    //       build script. we missed the Windows file extension thing (which would have shown up
    //       the first time anyone ever tried to build on Windows), but there are dozens of other
    //       potential nightmare scenarios, such as if the command has file separators in it e.g.
    //       it might already specify a path; so the question is: why are we doing this at all?
    fun pathFor(command: String): String {
        // on Windows, there are a dozen or so extensions that the OS will automatically
        // add to the name when looking for it in the PATH (e.g. "git" will become "git.exe"
        // but could also be something stupid like "git.js") so if we're running on Windows
        // and the command doesn't already have an extension, then we have to search for the
        // dozen or so combinations of the command and its extensions
        val candidates = if (System.getProperty("os.name").lowercase().contains("win") && !command.contains('.')) {
            (System.getenv("PATHEXT")
                    ?.split(';')
                    ?.filter { it.isNotBlank() }
                    ?.map { it.lowercase() }
                    ?: listOf(".exe", ".bat", ".cmd"))
                    .map { ext -> "$command$ext" }
        } else {
            listOf(command)
        }

        val found = (System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList())
                .flatMap { dir -> candidates.map { File(dir, it) } }
                .firstOrNull { it.exists() && it.canExecute() }

        if (found == null) {
            throw IllegalStateException("Cannot find executable '$command' in PATH: ${System.getenv("PATH")}")
        }

        return found.canonicalPath
    }

    val pathedCommand = try {
        pathFor(command)
    } catch (e : Exception) {
        // couldn't find the command, so let the OS find it normally
        command
    }

    val commandLine = listOf(pathedCommand, *args)
    val builder = ProcessBuilder(commandLine).redirectErrorStream(redirectErrorStream)

    // Add environment variables if provided
    env.forEach { (key, value) ->
        builder.environment()[key] = value
    }
    
    // Set working directory if provided
    workingDir?.let { builder.directory(it) }

    logger?.info("[processes] Spawning process: '${commandLine.joinToString(" ")}'${if (env.isNotEmpty()) " (with ${env.size} env vars)" else ""}")

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

/**
 * Execute multiple commands and return their results.
 * 
 * @param commands Map of command names to command lists (first element is command, rest are args)
 * @param env Environment variables to set for all commands
 * @param workingDir Working directory for all commands
 * @param throwOnError If true, throws exception on first command failure; if false, continues with all commands
 * @param logger Logger for command output
 * @return Map of command names to their ProcessResults
 */
fun spawn(
    commands: Map<String, List<String>>, 
    env: Map<String, String> = emptyMap(), 
    workingDir: File? = null, 
    throwOnError: Boolean = true, 
    logger: Logger? = null
): Map<String, ProcessResult> {
    return commands.mapValues { (name, command) ->
        logger?.info("[processes] Executing command '$name': ${command.joinToString(" ")}")
        spawn(command.first(), *command.drop(1).toTypedArray(), env = env, workingDir = workingDir, throwOnError = throwOnError, logger = logger)
    }
}
