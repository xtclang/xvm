import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.io.File

/**
 * Configure all maven publications with some mandatory and helpful information.
 *
 * TODO: Add some generic XML point out more information about the build, like maybe
 *   SHA commit etc.
 */
fun PublishingExtension.configureMavenPublications(project: Project) = project.run {
    publications.withType<MavenPublication>().configureEach {
        logger.info("[build-logic] Configuring publication '$name' for project '${project.name}'.")
        pom {
            licenses {
                license {
                    name = "The XDK License"
                    url = "https://github.com/xtclang/xvm/tree/master/license"
                }
            }
            developers {
                developer {
                    name = "The XTC Language Organization"
                    email = "info@xtclang.org"
                    organization = "xtclang.org"
                    organizationUrl = "https://xtclang.org"
                }
            }
            // see https://central.sonatype.org/publish/requirements/#scm-information
            scm {
                connection = "scm:git:git://github.com/xtclang/xvm.git"
                developerConnection = "scm:git:ssh://github.com/xtclang/xvm.git"
                url = "https://github.com/xtclang/xvm/tree/master"
            }
        }
    }
}

fun SigningExtension.mavenCentralSigning(): List<Sign> = project.run {
    fun readKeyFile(): String {
        val file = File(gradle.gradleUserHomeDir, XdkDistribution.GPGKEY_FILENAME)
        if (!file.exists()) return ""
        return file.readText().trim()
    }

    fun resolveGpgSecret(): Boolean {
        val sign = getXdkPropertyBoolean("org.xtclang.signing.enabled", isRelease())
        if (!sign) {
            logger.info("[build-logic] Signing is disabled. Will not try to resolve any keys.")
            return false
        }
        
        val password = (project.findProperty("signing.password") ?: System.getenv("GPG_SIGNING_PASSWORD") ?: "") as String
        val key = (project.findProperty("signing.key") ?: System.getenv("GPG_SIGNING_KEY") ?: readKeyFile()) as String
        
        if (key.isEmpty() || password.isEmpty()) {
            logger.warn("[build-logic] WARNING: Could not resolve a GPG signing key or a passphrase.")
            if (XdkDistribution.isCiEnabled) {
                throw GradleException("[distribution] No GPG signing key or password found in CI build, and no manual way to set them.")
            }
            return false
        }
        
        logger.info("[build-logic] Signature: In-memory GPG keys successfully configured.")
        assert(key.isNotEmpty() && password.isNotEmpty())
        useInMemoryPgpKeys(key, password)
        return true
    }

    resolveGpgSecret()
    val publishing = project.extensions.getByType(PublishingExtension::class.java)
    val publications = publishing.publications
    return sign(publications).also {
        if (publications.isEmpty()) {
            logger.warn("[build-logic] WARNING: No publications found, but signature are still enabled.")
        } else {
            logger.info("[build-logic] Signature: Configured sign tasks publications in '${project.name}', publications: ${publications.map { it.name }}.")
        }
    }
}

/**
 * Add resolution logic for the GitHub maven package repository. We use that to keep
 * SNAPSHOT publications after every commit to master (optionally to another branch, if
 * you modify the build action accordingly). Will return false and do nothing if we
 * cannot resolve credentials from GITHUB_TOKEN or the xtclang properties from any
 * property file.
 */
fun PublishingExtension.mavenGitHubPackages(project: Project): Boolean = project.run {
    val gitHubToken = project.getXtclangGitHubMavenPackageRepositoryToken()
    if (gitHubToken.isEmpty()) {
        logger.warn("[build-logic] WARNING: No GitHub token found, either in config or environment. publishRemote won't work.")
        return false
    }

    repositories {
        maven {
            name = "GitHub"
            url = uri("https://maven.pkg.github.com/xtclang/xvm")
            credentials {
                username = "xtclang-bot"
                password = gitHubToken
            }
            logger.info("[build-logic] Configured '$name' package repository for project '${project.name}'.")
        }
    }

    return true
}

// TODO: Add sonatype repository for mavenCentral once we have recovered the credentials (tokens) and
//  have manually verified that we can publish artifacts there.


class XdkDistribution(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        const val DISTRIBUTION_TASK_GROUP = "distribution"
        const val JAVATOOLS_PREFIX_PATTERN = "**/javatools*"
        const val GPGKEY_FILENAME = "xtclang-gpgkey.asc"

        private const val CI = "CI"

        val isCiEnabled = System.getenv(CI) == "true"
        val currentOs: OperatingSystem = OperatingSystem.current()
        val currentArch: String = normalizeArchitecture(System.getProperty("os.arch"))
        val distributionTasks = listOf(
            "distTar",
            "distZip",
            "withNativeLaunchersDistTar",
            "withNativeLaunchersDistZip"
        )
        val binaryLauncherNames = listOf("xcc", "xec", "xtc")

        // Removed delegateForPlatform - now handled by templates

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

        // Get all supported OS×Architecture combinations (Docker platform naming)
        fun getSupportedPlatforms(): List<Pair<String, String>> = listOf(
            "linux" to "amd64",
            "linux" to "arm64",
            "macos" to "arm64",   // Apple Silicon
            "macos" to "amd64",   // Intel Mac (if needed)
            "windows" to "amd64"
        )

        // Check if a platform combination is supported
        fun isPlatformSupported(os: String, arch: String): Boolean = 
            getSupportedPlatforms().contains(os to arch)

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
         * Script template strings for platform-specific launcher modifications
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
                "launcher_args" to """org.xvm.tool.Launcher %APP_BASE_NAME% -L "%APP_HOME%\lib" -L "%APP_HOME%\javatools\javatools_turtle.xtc" -L "%APP_HOME%\javatools\javatools_bridge.xtc""""
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
                "launcher_args" to """org.xvm.tool.Launcher ${'$'}APP_BASE_NAME -L "${'$'}APP_HOME/lib" -L "${'$'}APP_HOME/javatools/javatools_turtle.xtc" -L "${'$'}APP_HOME/javatools/javatools_bridge.xtc""""
            )
        )

        /**
         * Generate module path arguments for XTC launchers (-L arguments).
         *
         * @param isWindows true for Windows batch files, false for Unix shell scripts
         * @return formatted module path arguments string
         */
        // Removed generateXtcModulePathArgs - XTC module paths are now handled in templates
        
        // Removed getPlatformFormatting - now handled by templates

        /**
         * Replace jar paths in script content, handling both Unix and Windows path separators.
         * Replaces `/lib/originalName` with `/javatools/strippedName` and Windows equivalents.
         *
         * @param scriptContent the script content to modify
         * @param originalName the original jar name with version
         * @param strippedName the jar name without version
         * @return modified script content with updated jar paths
         */
        // Removed replaceJarPaths - jar paths are now handled in templates

        /**
         * Add XTC module paths to launcher script and inject script name as first argument.
         */
        // Removed injectXtcModulePaths - module paths are now handled in templates

        /**
         * Find insertion point for delegation logic in script content.
         * @return insertion index, or -1 if not found
         */
        // Removed findDelegationInsertionPoint - delegation is now handled in templates

        /**
         * Cross-platform XDK_HOME delegation logic injection for launcher scripts.
         * Implements proper delegation to XDK_HOME installations with infinite recursion prevention.
         */
        // Removed injectXdkHomeDelegation - XDK_HOME delegation is now handled in templates

        /**
         * Fix path resolution to use APP_HOME consistently after XDK_HOME delegation.
         */
        // Removed fixPathResolution - path resolution is now handled in templates

        /**
         * Strip version suffix from jar names for distribution.
         * Configuration cache compatible - no script context references.
         */
        fun stripVersionFromJarName(jarName: String, version: String): String {
            return jarName.replace(Regex("(.*)\\-${Regex.escape(version)}\\.jar"), "$1.jar")
        }

        /**
         * Create a configuration-cache compatible rename transformer for distribution.
         * This avoids script object references by using a static method.
         */
        fun createRenameTransformer(version: String): (String) -> String = { jarName ->
            stripVersionFromJarName(jarName, version)
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
                    
                    if (isWindows) {
                        // Insert XDK_HOME delegation for Windows - place it right after :execute line
                        val insertPoint = content.indexOf(":execute")
                        if (insertPoint >= 0) {
                            val lineEnd = content.indexOf('\n', insertPoint)
                            val endOfLine = if (lineEnd >= 0) lineEnd + 1 else content.length
                            val before = content.substring(0, endOfLine)
                            val after = content.substring(endOfLine)
                            content = before + templates["xdk_home_delegation"] + after
                        }
                        
                        // Add XTC module paths to Windows script
                        content = content.replace("org.xvm.tool.Launcher", templates["launcher_args"]!!)
                    } else {
                        // Insert XDK_HOME delegation for Unix - place it right after APP_HOME calculation
                        val insertPoint = content.indexOf("APP_HOME=\$( cd -P")
                        if (insertPoint >= 0) {
                            val lineEnd = content.indexOf('\n', insertPoint)
                            val endOfLine = if (lineEnd >= 0) lineEnd + 1 else content.length
                            val before = content.substring(0, endOfLine)
                            val after = content.substring(endOfLine)
                            content = before + templates["xdk_home_delegation"] + after
                        }
                        
                        // Add XTC module paths to Unix script  
                        content = content.replace("org.xvm.tool.Launcher", templates["launcher_args"]!!)
                    }
                    
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

    init {
        logger.info("""
            [build-logic] Configuring XVM distribution: '$this'
            [build-logic]   Name        : '$distributionName'
            [build-logic]   Version     : '$distributionVersion'
            [build-logic]   Target OS   : '${getOsName()}'
            [build-logic]   Target Arch : '$currentArch'
            [build-logic]   Platform    : '${getOsName()}_$currentArch'
            [build-logic]   Environment:
            [build-logic]       CI             : '$isCiEnabled' (CI property can be overwritten)
            [build-logic]       GITHUB_ACTIONS : '${System.getenv("GITHUB_ACTIONS") ?: "[not set]"}'
        """.trimIndent())
    }

    @Suppress("MemberVisibilityCanBePrivate") // No it can't, IntelliJ
    val distributionName: String get() = project.name // Default: "xdk"

    @Suppress("MemberVisibilityCanBePrivate") // No it can't, IntelliJ
    val distributionVersion: String get() = project.version.toString()

    @Suppress("MemberVisibilityCanBePrivate")
    fun launcherFileName(os: String = getOsName(), arch: String = currentArch): String {
        val extension = if (os == "windows") ".exe" else ""
        return "${os}_launcher_$arch$extension"
    }

    /*
     * Helper for jreleaser etc.
     */
    fun osClassifier(os: String = getOsName(), arch: String = currentArch): String = "${os}_$arch"

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
