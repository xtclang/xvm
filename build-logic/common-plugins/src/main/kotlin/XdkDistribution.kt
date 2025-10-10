import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * XDK distribution configuration helper.
 * Configuration-cache compatible - extracts values at construction time without holding project references.
 */
class XdkDistribution(
    val distributionName: String,
    val distributionVersion: String,
    private val targetArch: String
) {
    companion object {
        const val DISTRIBUTION_TASK_GROUP = "distribution"
        const val JAVATOOLS_PREFIX_PATTERN = "**/javatools*"

        private const val CI = "CI"

        // These need to be computed at execution time to be configuration cache compatible
        val currentOs: OperatingSystem = OperatingSystem.current()
        fun getCurrentArch(project: Project): String = normalizeArchitecture(project.providers.systemProperty("os.arch").get())
        val distributionTasks = listOf(
            "distTar",
            "distZip",
            "withNativeLaunchersDistTar",
            "withNativeLaunchersDistZip"
        )
        val binaryLauncherNames = listOf("xcc", "xec", "xtc")

        fun isDistributionArchiveTask(task: Task): Boolean {
            return task.group == DISTRIBUTION_TASK_GROUP && task.name in distributionTasks
        }

        // Normalize architecture names to consistent values (Docker platform naming)
        fun normalizeArchitecture(arch: String): String = when (arch.lowercase()) {
            "amd64", "x86_64", "x64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            "arm" -> "arm"
            else -> arch.lowercase()
        }

        // Get OS name in consistent format
        fun getOsName(): String = when {
            currentOs.isMacOsX -> "macos"
            currentOs.isLinux -> "linux"
            currentOs.isWindows -> "windows"
            else -> throw UnsupportedOperationException("Unsupported OS: $currentOs")
        }

        /**
         * Required XTC modules that must be in the module path for launchers to work.
         * This matches the native launcher's module path setup.
         */
        val REQUIRED_XTC_MODULES = listOf(
            XtcModulePath("lib"),                              // Main library directory
            XtcModulePath("javatools", "javatools_turtle.xtc"), // Mack library
            XtcModulePath("javatools", "javatools_bridge.xtc")  // Native bridge library
        )

        /**
         * Generate launcher arguments with module paths from REQUIRED_XTC_MODULES.
         */
        private fun generateLauncherArgs(isWindows: Boolean): String {
            val pathSep = if (isWindows) "\\" else "/"
            val varPrefix = if (isWindows) "%APP_HOME%" else "${'$'}APP_HOME"
            val modulePaths = REQUIRED_XTC_MODULES.joinToString(" ") { module ->
                "-L \"$varPrefix$pathSep${module.toPath(isWindows)}\""
            }
            val appBaseName = if (isWindows) "%APP_BASE_NAME%" else "${'$'}APP_BASE_NAME"
            return "org.xvm.tool.Launcher $appBaseName $modulePaths"
        }

        /**
         * Script template strings for platform-specific launcher modifications.
         * Note: Dollar signs are intentionally escaped for shell script generation.
         * IntelliJ warnings about string templates are intentional - do not simplify!
         */
        val SCRIPT_TEMPLATES = mapOf(
            "windows" to mapOf(
                "xdk_home_delegation" to """
                        |@rem Setup the command line
                        |
                        |if defined XDK_HOME if exist "%XDK_HOME%\" (
                        |    rem === if a same-named script is in XDK_HOME, use it instead of this script ===
                        |    set "XDK_CMD=%XDK_HOME%\bin\%APP_BASE_NAME%"
                        |    if exist "%XDK_CMD%" (
                        |        for %%F in ("%XDK_CMD%") do set "XDK_ID=%%~fF"
                        |        for %%F in ("%~f0") do set "APP_ID=%%~fF"
                        |        if /I not "%XDK_ID%"=="%APP_ID%" (
                        |            "%XDK_ID%" %*
                        |            goto end
                        |        )
                        |    )
                        |    rem === use the libraries specified by XDK_HOME ===
                        |    set "APP_HOME=%XDK_HOME%"
                        |)
                        |
                        |if not exist %APP_HOME%\javatools\javatools.jar (
                        |    echo Unable to locate a valid XDK in "%APP_HOME%"; set XDK_HOME to the "xdk" directory containing "bin\", "lib\", and "javatools\"
                        |    goto fail
                        |)
                        |
                        |set CLASSPATH=%APP_HOME%\javatools\javatools.jar
                        |
                        |
                        |@rem Execute xec
                        |""".trimMargin(),
                "launcher_args" to generateLauncherArgs(isWindows = true)
            ),
            "unix" to mapOf(
                "xdk_home_delegation" to """
                        |
                        |# for any Linux/macOS/Unix/POSIX system, build a unique file id for comparison
                        |get_file_id() {
                        |    inode=${'$'}(ls -di "${'$'}1" 2>/dev/null | awk '{print ${'$'}1}')
                        |    device=${'$'}(df "${'$'}1" 2>/dev/null | awk 'NR==2 {print ${'$'}1}')
                        |    if [ -n "${'$'}inode" ] && [ -n "${'$'}device" ]; then
                        |        printf '%s:%s\n' "${'$'}device" "${'$'}inode"
                        |    else
                        |        echo "failed to get file id for ${'$'}1"
                        |        exit 1
                        |    fi
                        |}
                        |
                        |if [ -n "${'$'}XDK_HOME" ]; then
                        |    # delegate to the command in XDK_HOME if there is one
                        |    XDK_CMD="${'$'}XDK_HOME/bin/${'$'}APP_BASE_NAME"
                        |    if [ -e "${'$'}XDK_CMD" ]; then
                        |        xdk_id=${'$'}(get_file_id "${'$'}XDK_CMD")
                        |        app_id=${'$'}(get_file_id "${'$'}app_path")
                        |        if [ "${'$'}xdk_id" != "${'$'}app_id" ]; then
                        |            exec "${'$'}XDK_CMD" "${'$'}@"
                        |        fi
                        |    fi
                        |
                        |    # switch to using the libs etc. from the XDK at XDK_HOME
                        |    APP_HOME="${'$'}XDK_HOME"
                        |fi
                        |""".trimMargin(),
                "launcher_args" to generateLauncherArgs(isWindows = false)
            )
        )


        /**
         * Strip version suffix from jar names for distribution.
         * Configuration cache compatible - no script context references.
         */
        fun stripVersionFromJarName(jarName: String, version: String): String {
            return jarName.replace(Regex("(.*)-${Regex.escape(version)}\\.jar"), "$1.jar")
        }

        /**
         * Create a configuration-cache compatible rename transformer for distribution.
         * This avoids script object references by using a static method.
         */
        fun createRenameTransformer(version: String): (String) -> String = { jarName ->
            stripVersionFromJarName(jarName, version)
        }

        /**
         * Insert text after a marker line in the content.
         */
        private fun insertAtMarker(content: String, marker: String, textToInsert: String): String {
            val insertPoint = content.indexOf(marker)
            if (insertPoint < 0) return content

            val lineEnd = content.indexOf('\n', insertPoint)
            val endOfLine = if (lineEnd >= 0) lineEnd + 1 else content.length
            val before = content.take(endOfLine)
            val after = content.substring(endOfLine)
            return before + textToInsert + after
        }

        /**
         * Configuration cache compatible script modification logic.
         * This static method avoids script object references.
         */
        fun modifyLauncherScripts(
            outputDir: File,
            artifactVersion: String,
            javaToolsFiles: Set<File>
        ) {
            // Process scripts in the output directory
            listOf(
                File(outputDir, "xec") to false,      // Unix shell script
                File(outputDir, "xec.bat") to true    // Windows batch file
            ).forEach { (script, isWindows) ->
                if (script.exists()) {
                    var content = script.readText()
                    
                    // Replace jar paths with version-stripped equivalents  
                    javaToolsFiles.forEach { jar ->
                        val originalName = jar.name
                        val strippedName = stripVersionFromJarName(originalName, artifactVersion)
                        content = content.replace("/lib/$originalName", "/javatools/$strippedName")
                            .replace("\\lib\\$originalName", "\\javatools\\$strippedName")
                    }
                    
                    // Add XDK_HOME delegation and XTC module paths using templates directly
                    val templates = if (isWindows) SCRIPT_TEMPLATES["windows"]!! else SCRIPT_TEMPLATES["unix"]!!
                    
                    // Insert XDK_HOME delegation at platform-specific location
                    val insertMarker = if (isWindows) ":execute" else "APP_HOME=\$( cd -P"
                    content = insertAtMarker(content, insertMarker, templates["xdk_home_delegation"]!!)

                    // Add XTC module paths
                    content = content.replace("org.xvm.tool.Launcher", templates["launcher_args"]!!)
                    
                    script.writeText(content)
                }
            }
            
            // Create additional scripts for xcc and xtc by copying modified xec scripts
            listOf("xcc", "xtc").forEach { toolName ->
                val unixOriginal = File(outputDir, "xec")
                val windowsOriginal = File(outputDir, "xec.bat")
                
                if (unixOriginal.exists()) {
                    val newScript = File(outputDir, toolName)
                    newScript.writeText(unixOriginal.readText().replace("Launcher \$APP_BASE_NAME", "Launcher $toolName"))
                    newScript.setExecutable(true)
                }
                
                if (windowsOriginal.exists()) {
                    File(outputDir, "$toolName.bat").writeText(
                        windowsOriginal.readText().replace("Launcher %APP_BASE_NAME%", "Launcher $toolName"))
                }
            }
        }
    }

    fun launcherFileName(os: String = getOsName(), arch: String = targetArch): String {
        val extension = if (os == "windows") ".exe" else ""
        return "${os}_launcher_$arch$extension"
    }

    fun osClassifier(os: String = getOsName(), arch: String = targetArch): String = "${os}_$arch"

    override fun toString(): String = "$distributionName-$distributionVersion"

    /**
     * Module path configuration for XTC launchers.
     * Replicates the behavior from os_unux.c lines 84-91 in the native launcher.
     */
    data class XtcModulePath(val directory: String, val fileName: String = "") {
        fun toPath(isWindows: Boolean): String {
            val separator = if (isWindows) "\\" else "/"
            return if (fileName.isEmpty()) directory else "$directory$separator$fileName"
        }
    }
}
