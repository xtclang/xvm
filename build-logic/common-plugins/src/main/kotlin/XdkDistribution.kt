import org.gradle.api.Project
import org.gradle.api.Task
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
