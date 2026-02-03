package org.xtclang.idea.lsp.jre

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.Decompressor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo

/**
 * Provisions a Java Runtime Environment (JRE) for the XTC LSP server.
 *
 * ## Why This Is Needed
 *
 * The XTC LSP server uses tree-sitter for parsing, which requires the Foreign Function & Memory
 * (FFM) API introduced in Java 22 and finalized in Java 25. However, IntelliJ IDEA runs on
 * JetBrains Runtime (JBR) 21, which doesn't support FFM. Therefore, the LSP server must run
 * as an out-of-process Java application using a separate Java 25+ runtime.
 *
 * ## Resolution Strategy
 *
 * The provisioner finds a suitable JRE using this priority order:
 *
 * 1. **Registered JDKs**: Checks IntelliJ's Project SDK table (`ProjectJdkTable`) for any
 *    Java 25+ SDK already registered by the user. This is the preferred path as it uses
 *    an existing installation without any downloads.
 *
 * 2. **Cached JRE**: Checks the IDE's system cache directory for a previously downloaded
 *    Temurin JRE at `{PathManager.getSystemPath()}/xtc-jre/temurin-25-jre/`.
 *
 * 3. **Download from Foojay**: If no suitable JRE is found, downloads Eclipse Temurin JRE 25
 *    from the [Foojay Disco API](https://api.foojay.io/). This is the same API used by
 *    Gradle's toolchain auto-provisioning. The download happens once and is cached for
 *    future use.
 *
 * ## Cache Location
 *
 * Downloaded JREs are stored in IntelliJ's system directory (`PathManager.getSystemPath()`),
 * typically at:
 * - macOS: `~/Library/Caches/JetBrains/IntelliJIdea2025.1/xtc-jre/`
 * - Linux: `~/.cache/JetBrains/IntelliJIdea2025.1/xtc-jre/`
 * - Windows: `%LOCALAPPDATA%\JetBrains\IntelliJIdea2025.1\xtc-jre\`
 *
 * This location is managed by the IDE and will be cleaned during "Invalidate Caches".
 *
 * ## Failure Handling
 *
 * To prevent infinite retry loops (e.g., if LSP4IJ keeps restarting a failing server),
 * a failure marker file is created on provisioning failure. Subsequent attempts will
 * fail fast until the marker is cleared via [clearFailure] or by deleting the marker file.
 *
 * @param cacheDir Directory for caching downloaded JREs (defaults to IDE system path)
 * @param version Target Java major version (defaults to [TARGET_VERSION])
 */
class JreProvisioner(
    private val cacheDir: Path = Path.of(PathManager.getSystemPath(), "xtc-jre"),
    private val version: Int = TARGET_VERSION,
) {
    companion object {
        /** Target Java version for the LSP server (must match TreeSitterAdapter.MIN_JAVA_VERSION). */
        const val TARGET_VERSION = 25
    }

    private val logger = logger<JreProvisioner>()
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    private val jreDir: Path get() = cacheDir.resolve("temurin-$version-jre")
    private val failureMarker: Path get() = cacheDir.resolve(".provision-failed-$version")
    private val isWindows get() = "windows" in System.getProperty("os.name").lowercase()

    val javaPath: Path?
        get() = findSystemJava() ?: findCachedJava()

    private fun findCachedJava(): Path? {
        val exe = jreDir.resolve(if (isWindows) "bin/java.exe" else "bin/java")
        return exe.takeIf { it.exists() && it.isExecutable() }?.also {
            logger.info("Found cached JRE: $it")
        }
    }

    private fun findSystemJava(): Path? {
        val javaSdk = JavaSdk.getInstance()
        return ProjectJdkTable
            .getInstance()
            .getSdksOfType(javaSdk)
            .firstNotNullOfOrNull { sdk ->
                val majorVersion =
                    javaSdk
                        .getVersion(sdk)
                        ?.maxLanguageLevel
                        ?.feature() ?: 0
                if (majorVersion >= version) {
                    javaSdk
                        .getVMExecutablePath(sdk)
                        ?.let { Path.of(it) }
                        ?.takeIf { it.exists() }
                        ?.also { logger.info("Found registered JDK $majorVersion: $it") }
                } else {
                    null
                }
            }
    }

    fun isProvisioned(): Boolean = javaPath != null

    /** Returns true if provisioning previously failed (prevents infinite retry loops). */
    fun hasFailedBefore(): Boolean = failureMarker.exists()

    /** Clear failure marker to allow retry (e.g., after user intervention). */
    fun clearFailure() {
        failureMarker.deleteIfExists()
        if (jreDir.exists()) FileUtil.delete(jreDir)
    }

    fun provision(onProgress: ((Float, String) -> Unit)? = null): Path {
        javaPath?.let {
            logger.info("Using cached JRE: $it")
            return it
        }

        if (hasFailedBefore()) {
            error("JRE provisioning previously failed. Delete $failureMarker to retry.")
        }

        logger.info("No cached JRE found, will download from Foojay")
        onProgress?.invoke(0.1f, "Finding JRE package...")

        val (os, arch, archiveType) = platform()
        logger.info("Detected platform: os=$os, arch=$arch, archiveType=$archiveType")

        val pkg = findPackage(os, arch, archiveType) ?: error("No JRE found for Java $version on $os-$arch")
        logger.info("Found package: ${pkg.filename} from ${pkg.links.downloadRedirect}")

        onProgress?.invoke(0.2f, "Downloading ${pkg.filename}...")
        val archive = download(pkg.links.downloadRedirect)

        onProgress?.invoke(0.8f, "Extracting...")
        runCatching {
            extract(archive, archiveType)
        }.onFailure { e ->
            Files.createFile(failureMarker)
            if (jreDir.exists()) FileUtil.delete(jreDir)
            throw e
        }
        archive.deleteIfExists()

        onProgress?.invoke(1.0f, "Done")
        val java = javaPath
        if (java == null) {
            Files.createFile(failureMarker)
            error("Java executable not found after extraction at $jreDir")
        }
        logger.info("JRE provisioned successfully: $java")
        return java
    }

    private fun platform(): Triple<String, String, String> {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        logger.info("Raw system properties: os.name=$osName, os.arch=$osArch")

        val os =
            osName.lowercase().let {
                when {
                    "mac" in it -> "macos"
                    "linux" in it -> "linux"
                    "windows" in it -> "windows"
                    else -> error("Unsupported OS: $osName")
                }
            }
        val arch =
            osArch.lowercase().let {
                when (it) {
                    "aarch64", "arm64" -> "aarch64"
                    "amd64", "x86_64" -> "x64"
                    else -> error("Unsupported arch: $osArch")
                }
            }
        return Triple(os, arch, if (os == "windows") "zip" else "tar.gz")
    }

    private fun findPackage(
        os: String,
        arch: String,
        archiveType: String,
    ): JrePackage? {
        val params =
            listOf(
                "version" to version,
                "distribution" to "temurin",
                "package_type" to "jre",
                "operating_system" to os,
                "architecture" to arch,
                "archive_type" to archiveType,
                "javafx_bundled" to false,
                "latest" to "available",
            )
        val url = "https://api.foojay.io/disco/v3.0/packages?${params.joinToString("&") { "${it.first}=${it.second}" }}"
        logger.info("Querying Foojay API: $url")

        val response =
            http.send(
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        logger.info("Foojay API response: status=${response.statusCode()}")
        if (response.statusCode() != HTTP_OK) {
            logger.warn("Foojay API error: ${response.body()}")
            return null
        }

        val packages = json.decodeFromString<ApiResponse>(response.body()).result
        logger.info("Foojay returned ${packages.size} package(s)")
        return packages.firstOrNull()
    }

    private fun download(url: String): Path {
        logger.info("Downloading JRE from: $url")
        val response =
            http.send(
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )

        if (response.statusCode() != HTTP_OK) {
            error("Download failed: HTTP ${response.statusCode()}")
        }

        cacheDir.createDirectories()
        val archive = cacheDir.resolve("download.tmp")
        response.body().use { input ->
            Files.copy(input, archive)
        }
        val size = Files.size(archive)
        logger.info("Downloaded ${size / 1024 / 1024} MB to $archive")
        return archive
    }

    private fun extract(
        archive: Path,
        archiveType: String,
    ) {
        logger.info("Extracting $archiveType archive to $jreDir")
        jreDir.createDirectories()
        val decompressor =
            when (archiveType) {
                "tar.gz" -> Decompressor.Tar(archive)
                "zip" -> Decompressor.Zip(archive)
                else -> error("Unsupported archive: $archiveType")
            }
        decompressor.extract(jreDir)

        // JRE archives have a nested root dir like "jdk-25.0.1+9-jre/" - flatten it
        flattenSingleSubdirectory()
        logger.info("Extraction complete")
    }

    /**
     * If jreDir contains exactly one subdirectory and no bin/, move the subdirectory's contents up.
     * This handles JRE archives that have a versioned root directory like "jdk-25.0.1+9-jre/".
     */
    private fun flattenSingleSubdirectory() {
        val entries = jreDir.listDirectoryEntries()
        val binDir = jreDir.resolve("bin")

        // Already flat - bin/ exists at top level
        if (binDir.exists()) {
            logger.info("Archive already flat (bin/ exists at top level)")
            return
        }

        // Find single subdirectory containing bin/
        val nestedDir = entries.singleOrNull { it.isDirectory() && it.resolve("bin").exists() }
        if (nestedDir == null) {
            logger.warn("Could not find nested JRE directory to flatten. Contents: ${entries.map { it.fileName }}")
            return
        }

        logger.info("Flattening nested directory: ${nestedDir.fileName}")
        nestedDir.listDirectoryEntries().forEach { child ->
            val target = jreDir.resolve(child.fileName)
            child.moveTo(target)
        }
        nestedDir.deleteIfExists()
    }

    @Serializable
    private data class ApiResponse(
        val result: List<JrePackage>,
    )

    @Serializable
    data class JrePackage(
        val id: String,
        @SerialName("archive_type") val archiveType: String,
        val filename: String,
        val links: Links,
    ) {
        @Serializable
        data class Links(
            @SerialName("pkg_download_redirect") val downloadRedirect: String,
        )
    }
}
