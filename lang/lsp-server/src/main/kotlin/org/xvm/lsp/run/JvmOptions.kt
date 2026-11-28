package org.xvm.lsp.run

import java.io.File

/**
 * JVM options that a run takes from its Gradle project, for the JVM that runs it: the execution worker's, or a test
 * run's. [accepted] go on that JVM's command line as given; [refused] are left out, each with why, as that JVM would
 * not run its own main class on the language server's class path with them, or would not run at all: a class path or
 * main class of their own, a Java debugger's agent, options that print and exit, and argument and option files, whose
 * options cannot be checked. An option that takes its value as the next item is left out with that value. [relative]
 * are the accepted options that hold a relative path, which resolves against the language server's working directory,
 * not the project's, as Gradle's own runs resolve it.
 */
internal data class JvmOptions(
    val accepted: List<String>,
    val refused: List<Refused>,
    val relative: List<String> = emptyList(),
) {
    /** An [option] left out, with its value where it takes one as the next item, and [why]. */
    data class Refused(
        val option: String,
        val why: String,
    )

    companion object {
        /** [options] split into those a run's JVM takes and those it leaves out. */
        fun of(options: List<String>): JvmOptions {
            val accepted = mutableListOf<String>()
            val refused = mutableListOf<Refused>()
            val items = options.iterator()
            for (option in items) {
                val why = refusal(option)
                when {
                    why == null -> accepted += option
                    option in VALUED && items.hasNext() -> refused += Refused("$option ${items.next()}", why)
                    else -> refused += Refused(option, why)
                }
            }
            return JvmOptions(accepted, refused, relative(accepted))
        }

        /**
         * The options of [accepted] that hold a relative path, an option whose value is the next item named with it, as
         * `-p mods`. A path is absolute as the host has it, or with a Windows drive or share, whatever the host.
         */
        private fun relative(accepted: List<String>): List<String> =
            buildList {
                val items = accepted.iterator()
                for (option in items) {
                    val (named, paths) =
                        if (option in PATH_VALUED && items.hasNext()) {
                            val value = items.next()
                            "$option $value" to valuePaths(option, value)
                        } else {
                            option to paths(option)
                        }
                    if (paths.any { it.isNotBlank() && !File(it).isAbsolute && !WINDOWS_ABSOLUTE.containsMatchIn(it) }) add(named)
                }
            }

        /** Why a run's JVM does not take [option], or null when it does. */
        private fun refusal(option: String): String? =
            when {
                option.isBlank() -> "an empty option"

                option in CLASS_PATH || option.startsWith("--class-path=") || option.startsWith("-Djava.class.path=") ->
                    "the run's JVM has the language server's class path"

                option in MAIN || option.startsWith("--module=") || option.startsWith("--source=") ->
                    "the run's JVM runs its own main class"

                DEBUG_AGENT.containsMatchIn(option) -> "a Java debugger's agent would hold the run's JVM; Debug debugs the program"

                option in EXITS || option.startsWith("--describe-module=") -> "the JVM would print and exit instead of running"

                option.startsWith("@") -> "an argument file, whose options cannot be checked"

                OPTION_FILES.any(option::startsWith) -> "an options file, whose options cannot be checked"

                else -> null
            }

        /**
         * The paths that [option] holds, of the options that take one: an agent's library, a boot class path, module path
         * or library path, a module's patch, a class data archive, a file that the JVM writes, such as a log, heap dump,
         * error or flight recording.
         */
        private fun paths(option: String): List<String> =
            when {
                PATH_VALUED.any { option.startsWith("$it=") } -> valuePaths(option.substringBefore('='), option.substringAfter('='))
                option.startsWith("-javaagent:") -> listOf(option.removePrefix("-javaagent:").substringBefore('='))
                option.startsWith("-agentpath:") -> listOf(option.removePrefix("-agentpath:").substringBefore('='))
                option.startsWith("-Xbootclasspath/a:") -> option.removePrefix("-Xbootclasspath/a:").split(File.pathSeparatorChar)
                option.startsWith("-Djava.library.path=") -> option.removePrefix("-Djava.library.path=").split(File.pathSeparatorChar)
                option.startsWith("-Xloggc:") -> listOf(option.removePrefix("-Xloggc:"))
                option.startsWith("-Xlog:") -> listOfNotNull(logFile(option.removePrefix("-Xlog:")))
                option.startsWith("-XX:StartFlightRecording") -> RECORDING.findAll(option).map { it.groupValues[1] }.toList()
                else -> FILE_OPTIONS.firstOrNull(option::startsWith)?.let { listOf(option.removePrefix(it)) }.orEmpty()
            }

        /** The paths of [value], the value of [option], one of [PATH_VALUED]: a path list, or `--patch-module`'s `module=paths`. */
        private fun valuePaths(
            option: String,
            value: String,
        ): List<String> = (if (option == "--patch-module") value.substringAfter('=') else value).split(File.pathSeparatorChar)

        /** The file that `-Xlog`'s [rest], `what:output:...`, writes to: none for standard output or error. */
        private fun logFile(rest: String): String? =
            LOG_OUTPUT
                .find(rest)
                ?.groupValues
                ?.get(1)
                ?.trim('"')
                ?.takeUnless { it.isEmpty() || it == "stdout" || it == "stderr" }

        /** `-Xlog`'s output, after its selection: `file=` and a name, quoted, or a Windows drive path, which has a colon. */
        private val LOG_OUTPUT = Regex("""^[^:]*:(?:file=)?("[^"]*"|[A-Za-z]:[\\/][^:]*|[^:]*)""")

        /** A Windows path with a drive or a share, which is absolute on any host. */
        private val WINDOWS_ABSOLUTE = Regex("""^([A-Za-z]:[\\/]|\\\\)""")

        /** The accepted options whose value is a path list, the next item or after `=`. */
        private val PATH_VALUED = setOf("-p", "--module-path", "--upgrade-module-path", "--patch-module")

        /** The options that load the JVM's own debugger agent, JDWP, by its library's name, as `-agentpath` loads it too. */
        private val DEBUG_AGENT = Regex("""^(-agentlib:jdwp|-Xrunjdwp|-agentpath:(.*[/\\])?(lib)?jdwp\.(so|dylib|dll)(=|$))""")

        private val CLASS_PATH = setOf("-cp", "-classpath", "--class-path")
        private val MAIN = setOf("-jar", "-m", "--module", "--source")
        private val EXITS =
            setOf(
                "-version",
                "--version",
                "-fullversion",
                "--full-version",
                "-h",
                "-help",
                "--help",
                "-?",
                "-X",
                "--help-extra",
                "--dry-run",
                "--list-modules",
                "--validate-modules",
                "-d",
                "--describe-module",
                "-Xshare:dump",
            )

        /** The options that read more options from a file, which a run cannot check. */
        private val OPTION_FILES = listOf("-XX:VMOptionsFile=", "-XX:Flags=")

        /** The options whose value is a file that the JVM reads or writes. */
        private val FILE_OPTIONS =
            listOf("-XX:HeapDumpPath=", "-XX:ErrorFile=", "-XX:LogFile=", "-XX:SharedArchiveFile=", "-XX:ArchiveClassesAtExit=")

        /** A flight recording's file, in `-XX:StartFlightRecording`'s settings. */
        private val RECORDING = Regex("""filename=([^,]+)""")

        /** The refused options that take their value as the next item. */
        private val VALUED = CLASS_PATH + MAIN + setOf("-d", "--describe-module")
    }
}
