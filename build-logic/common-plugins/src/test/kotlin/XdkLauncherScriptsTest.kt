import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class XdkLauncherScriptsTest {
    @TempDir
    lateinit var root: File

    @TestFactory
    fun launchersKeepTheirCommandArgumentsAndModulePaths() =
        listOf("unix" to "\n", "windows-lf" to "\n", "windows-crlf" to "\r\n").map { (platform, newline) ->
            dynamicTest(platform) {
                val windows = platform.startsWith("windows")
                val directory = File(root, platform).apply { mkdirs() }
                val suffix = if (windows) ".bat" else ""
                val source = if (windows) {
                    ":execute\nset CLASSPATH=%APP_HOME%\\lib\\javatools-1.2.3.jar\n" +
                        "\"%JAVA_EXE%\" -classpath \"%CLASSPATH%\" org.xvm.tool.Launcher %*\n"
                } else {
                    "APP_HOME=\$( cd -P . && pwd )\nCLASSPATH=\$APP_HOME/lib/javatools-1.2.3.jar\n" +
                        "org.xvm.tool.Launcher \\\n        \"\$@\"\n"
                }
                File(directory, "xtc$suffix").writeText(source.replace("\n", newline))
                if (!windows) File(directory, "xtc").setExecutable(true)
                XdkDistribution.modifyLauncherScripts(directory, "1.2.3", setOf(File("javatools-1.2.3.jar")))

                val home = if (windows) "%APP_HOME%" else "\$APP_HOME"
                val sep = if (windows) "\\" else "/"
                val userArgs = if (windows) "%*" else "\"\$@\""
                val paths = listOf("lib", "javatools${sep}javatools_turtle.xtc", "javatools${sep}javatools_bridge.xtc")
                    .joinToString(" ") { "-L \"$home$sep$it\"" }
                mapOf("xtc" to "", "xcc" to "build", "xec" to "run").forEach { (name, command) ->
                    val script = File(directory, "$name$suffix")
                    val content = script.readText().replace("\r\n", "\n")
                    val invocation = if (command.isEmpty()) "$userArgs $paths" else "$command $paths $userArgs"
                    assertTrue(content.replace("\\\n        ", "").contains("org.xvm.tool.Launcher $invocation"), name)
                    assertTrue(content.contains("$home${sep}javatools${sep}javatools.jar"), name)
                    if (windows) {
                        assertFalse(content.contains("\\\n"), "Batch commands must not use Unix line continuations")
                    } else {
                        assertTrue(script.canExecute(), name)
                    }
                }
            }
        }
}
