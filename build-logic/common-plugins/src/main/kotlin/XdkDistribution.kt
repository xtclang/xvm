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
        logger.info("$prefix Configuring publication '$name' for project '${project.name}'.")
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
            logger.info("$prefix Signing is disabled. Will not try to resolve any keys.")
            return false
        }
        val password = (project.findProperty("signing.password") ?: System.getenv("GPG_SIGNING_PASSWORD") ?: "") as String
        val key = (project.findProperty("signing.key") ?: System.getenv("GPG_SIGNING_KEY") ?: readKeyFile()) as String
        if (key.isEmpty() || password.isEmpty()) {
            logger.warn("$prefix WARNING: Could not resolve a GPG signing key or a passphrase.")
            if (XdkDistribution.isCiEnabled) {
                throw buildException("No GPG signing key or password found in CI build, and no manual way to set them.")
            }
            return false
        }
        logger.info("$prefix Signature: In-memory GPG keys successfully configured.")
        assert(key.isNotEmpty() && password.isNotEmpty())
        useInMemoryPgpKeys(key, password)
        return true
    }

    resolveGpgSecret()
    val publishing = project.extensions.getByType(PublishingExtension::class.java)
    val publications = publishing.publications
    return sign(publications).also {
        if (publications.isEmpty()) {
            logger.warn("$prefix WARNING: No publications found, but signature are still enabled.")
        } else {
            logger.info("$prefix Signature: Configured sign tasks publications in '${project.name}', publications: ${publications.map { it.name }}.")
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
        logger.warn("$prefix WARNING: No GitHub token found, either in config or environment. publishRemote won't work.")
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
            logger.info("$prefix Configured '$name' package repository for project '${project.name}'.")
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
         * Inject XTC module paths into a generated launcher script.
         * 
         * @param scriptContent the original script content
         * @param scriptName the script name (xcc, xec, etc.)
         * @param mainClassName the main class name (e.g., org.xvm.tool.Compiler)
         * @param isWindowsBatch true for .bat files, false for Unix shell scripts
         * @return modified script content with module paths injected
         */
        fun injectXtcModulePaths(
            scriptContent: String, 
            scriptName: String, 
            mainClassName: String, 
            isWindowsBatch: Boolean
        ): String {
            val modulePathArgs = generateXtcModulePathArgs(isWindowsBatch)
            val mainClassSimple = mainClassName.substringAfterLast('.')
            
            val targetPattern = if (isWindowsBatch) {
                "org.xvm.tool.$mainClassSimple"
            } else {
                "        org.xvm.tool.$mainClassSimple \\"
            }
            
            val replacement = if (isWindowsBatch) {
                "org.xvm.tool.$mainClassSimple $modulePathArgs"
            } else {
                "        org.xvm.tool.$mainClassSimple \\\n        $modulePathArgs \\"
            }
            
            return scriptContent.replace(targetPattern, replacement)
        }
    }

    init {
        logger.info("""
            $prefix Configuring XVM distribution: '$this'
            $prefix   Name        : '$distributionName'
            $prefix   Version     : '$distributionVersion'
            $prefix   Target OS   : '${getOsName()}'
            $prefix   Target Arch : '$currentArch'
            $prefix   Platform    : '${getOsName()}_$currentArch'
            $prefix   Environment:
            $prefix       CI             : '$isCiEnabled' (CI property can be overwritten)
            $prefix       GITHUB_ACTIONS : '${System.getenv("GITHUB_ACTIONS") ?: "[not set]"}'
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
