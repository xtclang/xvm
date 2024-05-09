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
        val distributionTasks = listOf("distTar", "distZip", "withLaunchersDistTar", "withLaunchersDistZip")
        val binaryLauncherNames = listOf("xcc", "xec")

        fun isDistributionArchiveTask(task: Task): Boolean {
            return task.group == DISTRIBUTION_TASK_GROUP && task.name in distributionTasks
        }
    }

    init {
        logger.info("""
            $prefix Configuring XVM distribution: '$this'
            $prefix   Name        : '$distributionName'
            $prefix   Version     : '$distributionVersion'
            $prefix   Current OS  : '$currentOs'
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

    fun configScriptFilename(installDir: Provider<Directory>): RegularFile {
        val config = if (currentOs.isMacOsX) {
            "cfg_macos.sh"
        } else if (currentOs.isLinux) {
            "cfg_linux.sh"
        } else if (currentOs.isWindows) {
            "cfg_windows.bat"
        } else {
            throw UnsupportedOperationException("Cannot find launcher config script for currentOs: $currentOs")
        }
        return installDir.get().file(config)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun launcherFileName(): String {
        return if (currentOs.isMacOsX) {
            "macos_launcher"
        } else if (currentOs.isLinux) {
            "linux_launcher"
        } else if (currentOs.isWindows) {
            "windows_launcher.exe"
        } else {
            throw UnsupportedOperationException("Cannot build distribution for currentOs: $currentOs")
        }
    }

    fun resolveLauncherFile(dir: Provider<Directory>): RegularFile {
        return dir.get().file("bin/${launcherFileName()}")
    }

    /*
     * Helper for jreleaser etc.
     */
    fun osClassifier(): String {
        val arch = System.getProperty("os.arch")
        return when {
            currentOs.isMacOsX -> "macos_$arch"
            currentOs.isLinux -> "linux_$arch"
            currentOs.isWindows -> "windows_$arch"
            else -> throw UnsupportedOperationException("Cannot build distribution for currentOs: $currentOs")
        }
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
