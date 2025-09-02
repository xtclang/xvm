import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
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
        if (file.exists()) {
            return file.readText().trim()
        }
        return ""
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
                throw buildException("No GPG signing key or password found in CI build, and no manual way to set them.")
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
        const val JAVATOOLS_INSTALLATION_NAME : String = "javatools.jar"
        const val GPGKEY_FILENAME = "xtclang-gpgkey.asc"

        private const val CI = "CI"

        val isCiEnabled = System.getenv(CI) == "true"
        val currentOs: OperatingSystem = OperatingSystem.current()
        val currentArch: String = normalizeArchitecture(System.getProperty("os.arch"))
        val distributionTasks = listOf(
            "distTar",
            "distZip",
            "withLaunchersDistTar",
            "withLaunchersDistZip"
        )
        val binaryLauncherNames = listOf("xcc", "xec")
        
        // Platform-specific constants for simple substitution
        private object PlatformSyntax {
            data class ScriptSyntax(
                val comment: String,
                val varPrefix: String,
                val varSuffix: String,
                val ifNotEmpty: String,
                val ifExists: String,
                val ifNotEqual: String,
                val ifStart: String,
                val ifEnd: String,
                val exec: String,
                val pathSep: String,
                val scriptExt: String
            )
            
            val UNIX = ScriptSyntax(
                comment = "#",
                varPrefix = "\${",
                varSuffix = "}",
                ifNotEmpty = "[ -n \"\$XDK_HOME\" ]",
                ifExists = "[ -e \"\$XDK_CMD\" ]",
                ifNotEqual = "[ \"\$xdk_id\" != \"\$app_id\" ]",
                ifStart = "if CONDITION; then",
                ifEnd = "fi",
                exec = "exec \"\$XDK_CMD\" \"\$@\"",
                pathSep = "/",
                scriptExt = ""
            )
            
            val WINDOWS = ScriptSyntax(
                comment = "rem",
                varPrefix = "%",
                varSuffix = "%",
                ifNotEmpty = "defined XDK_HOME",
                ifExists = "exist \"%XDK_CMD%\"",
                ifNotEqual = "not \"%XDK_CMD%\"==\"%APP_HOME%\\bin\\%APP_BASE_NAME%.bat\"",
                ifStart = "if CONDITION (",
                ifEnd = ")",
                exec = "\"%XDK_CMD%\" %*\n    exit /b %ERRORLEVEL%",
                pathSep = "\\",
                scriptExt = ".bat"
            )
        }
        
        // Unix/POSIX delegation logic template  
        object DelegationTemplates {
            // Simple file comparison using ls -i (inode number) - works on all Unix systems
            const val UNIX_FILE_ID_FUNCTION = """
# get file inode for comparison - works on all Unix/POSIX systems
get_file_id() {
    ls -i "${'$'}1" 2>/dev/null | cut -d' ' -f1
}"""
            
            private const val TEMPLATE = """
{{COMMENT}} delegate to the command in XDK_HOME if there is one
{{IF_NOT_EMPTY}}
    set "XDK_CMD={{VAR}}XDK_HOME{{VAR_END}}{{PATH_SEP}}bin{{PATH_SEP}}{{VAR}}APP_BASE_NAME{{VAR_END}}{{SCRIPT_EXT}}"
    {{IF_EXISTS}}
{{COMPARISON}}
        {{IF_NOT_EQUAL}}
            {{EXEC}}
        {{IF_END}}
    {{IF_END}}
    {{COMMENT}} switch to using the libs etc. from the XDK at XDK_HOME
    APP_HOME={{VAR}}XDK_HOME{{VAR_END}}
{{IF_END}}"""

            fun forPlatform(isWindows: Boolean): String {
                val s = if (isWindows) PlatformSyntax.WINDOWS else PlatformSyntax.UNIX
                
                val comparison = if (isWindows) "" else 
                    "        xdk_id=\$(get_file_id \"\$XDK_CMD\")\n        app_id=\$(get_file_id \"\${APP_HOME}/bin/\$APP_BASE_NAME\")"
                
                val script = TEMPLATE
                    .replace("{{COMMENT}}", s.comment)
                    .replace("{{IF_NOT_EMPTY}}", s.ifStart.replace("CONDITION", s.ifNotEmpty))
                    .replace("{{IF_EXISTS}}", s.ifStart.replace("CONDITION", s.ifExists))  
                    .replace("{{IF_NOT_EQUAL}}", s.ifStart.replace("CONDITION", s.ifNotEqual))
                    .replace("{{IF_END}}", s.ifEnd)
                    .replace("{{VAR}}", s.varPrefix)
                    .replace("{{VAR_END}}", s.varSuffix)
                    .replace("{{PATH_SEP}}", s.pathSep)
                    .replace("{{SCRIPT_EXT}}", s.scriptExt)
                    .replace("{{EXEC}}", s.exec)
                    .replace("{{COMPARISON}}", comparison)
                
                return if (isWindows) script else "$UNIX_FILE_ID_FUNCTION\n\n$script"
            }
        }

        fun isDistributionArchiveTask(task: Task): Boolean {
            return task.group == DISTRIBUTION_TASK_GROUP && task.name in distributionTasks
        }

        // Normalize architecture names to consistent values (Docker platform naming)
        fun normalizeArchitecture(arch: String): String {
            return when (arch.lowercase()) {
                "amd64", "x86_64", "x64" -> "amd64"
                "aarch64", "arm64" -> "arm64"
                "arm" -> "arm"
                else -> arch.lowercase()
            }
        }

        // Get OS name in consistent format
        fun getOsName(): String {
            return when {
                currentOs.isMacOsX -> "macos"
                currentOs.isLinux -> "linux"
                currentOs.isWindows -> "windows"
                else -> throw UnsupportedOperationException("Unsupported OS: $currentOs")
            }
        }

        // Get all supported OS×Architecture combinations (Docker platform naming)
        fun getSupportedPlatforms(): List<Pair<String, String>> {
            return listOf(
                "linux" to "amd64",
                "linux" to "arm64",
                "macos" to "arm64",   // Apple Silicon
                "macos" to "amd64",   // Intel Mac (if needed)
                "windows" to "amd64"
            )
        }

        // Check if a platform combination is supported
        fun isPlatformSupported(os: String, arch: String): Boolean {
            return getSupportedPlatforms().contains(os to arch)
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
         * Generate module path arguments for XTC launchers (-L arguments).
         * 
         * @param isWindows true for Windows batch files, false for Unix shell scripts
         * @return formatted module path arguments string
         */
        fun generateXtcModulePathArgs(isWindows: Boolean): String {
            val envVarFormat = if (isWindows) "%XDK_HOME:APP_HOME=%" else "\${XDK_HOME:-\$APP_HOME}"
            val pathSeparator = if (isWindows) "\\" else "/"
            val lineContinuation = if (isWindows) " " else " \\\n        "
            
            return REQUIRED_XTC_MODULES.joinToString(lineContinuation) { module ->
                val path = module.toPath(isWindows)
                "-L \"$envVarFormat$pathSeparator$path\""
            }
        }
        
        /**
         * Replace jar paths in script content, handling both Unix and Windows path separators.
         * Replaces `/lib/originalName` with `/javatools/strippedName` and Windows equivalents.
         * 
         * @param scriptContent the script content to modify
         * @param originalName the original jar name with version
         * @param strippedName the jar name without version
         * @return modified script content with updated jar paths
         */
        fun replaceJarPaths(scriptContent: String, originalName: String, strippedName: String): String {
            return scriptContent
                .replace("/lib/$originalName", "/javatools/$strippedName")
                .replace("\\lib\\$originalName", "\\javatools\\$strippedName")
        }

        /**
         * Add XTC module paths to launcher script.
         */
        fun injectXtcModulePaths(scriptContent: String, mainClassName: String, isWindows: Boolean): String {
            val modulePathArgs = generateXtcModulePathArgs(isWindows)
            val mainClass = mainClassName.substringAfterLast('.')
            
            // Just append module paths after the main class - works on both platforms
            return scriptContent.replace("org.xvm.tool.$mainClass", "org.xvm.tool.$mainClass $modulePathArgs")
        }
        
        /**
         * Find insertion point for delegation logic in script content.
         * @return insertion index, or -1 if not found
         */
        private fun findDelegationInsertionPoint(content: String, isWindows: Boolean): Int {
            return if (isWindows) {
                content.indexOf("set \"CLASSPATH=")
            } else {
                // Look for the APP_HOME resolution line (avoid PWD escaping issues)
                val patterns = listOf(
                    "APP_HOME=\$( cd -P",
                    "CLASSPATH=\$APP_HOME"
                )
                patterns.firstNotNullOfOrNull { pattern ->
                    val index = content.indexOf(pattern)
                    if (index >= 0) {
                        // Find end of line
                        val lineEnd = content.indexOf('\n', index)
                        if (lineEnd >= 0) lineEnd + 1 else index
                    } else null
                } ?: -1
            }
        }
        
        /**
         * Cross-platform XDK_HOME delegation logic injection for launcher scripts.
         * Implements proper delegation to XDK_HOME installations with infinite recursion prevention.
         */
        fun injectXdkHomeDelegation(content: String, isWindows: Boolean): String {
            val delegationLogic = DelegationTemplates.forPlatform(isWindows)
            val insertionPoint = findDelegationInsertionPoint(content, isWindows)
            
            return if (insertionPoint > 0) {
                val beforeInsertion = content.take(insertionPoint)
                val afterInsertion = content.substring(insertionPoint)
                "$beforeInsertion\n$delegationLogic\n\n$afterInsertion"
            } else {
                content // If we can't find insertion point, return unchanged
            }
        }
        
        /**
         * Fix path resolution to use APP_HOME consistently after XDK_HOME delegation.
         */
        fun fixPathResolution(content: String, isWindows: Boolean): String =
            content.replace(
                if (isWindows) "%XDK_HOME:APP_HOME=%" else "\${XDK_HOME:-\$APP_HOME}", 
                if (isWindows) "%APP_HOME%" else "\$APP_HOME"
            )
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
    val distributionVersion: String get() = buildString {
        append(project.version)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun launcherFileName(os: String = getOsName(), arch: String = currentArch): String {
        val extension = if (os == "windows") ".exe" else ""
        return "${os}_launcher_$arch$extension"
    }

    /*
     * Helper for jreleaser etc.
     */
    fun osClassifier(os: String = getOsName(), arch: String = currentArch): String {
        return "${os}_$arch"
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
    
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
