package org.xtclang.idea.lsp.jre

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
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull

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
 * 2. **Cached JRE**: Checks the Gradle cache directory for a previously downloaded
 *    Temurin JRE at `{GRADLE_USER_HOME}/caches/xtc-jre/temurin-25-jre/`.
 *
 * 3. **Download from Foojay**: If no suitable JRE is found, downloads Eclipse Temurin JRE 25
 *    from the [Foojay Disco API](https://api.foojay.io/). This is the same API used by
 *    Gradle's toolchain auto-provisioning. The download happens once and is cached for
 *    future use.
 *
 * ## Cache Location
 *
 * Downloaded JREs are stored in Gradle's user home (same location as Gradle toolchains):
 * - Default: `~/.gradle/caches/xtc-jre/`
 * - Override: Set `GRADLE_USER_HOME` environment variable
 *
 * This location persists across IDE sessions and won't be cleared by IntelliJ's "Invalidate Caches".
 *
 * ## Cache Validation
 *
 * A metadata file tracks the downloaded package ID. Periodically (every 7 days), the provisioner
 * checks Foojay for newer point releases and re-downloads if available.
 *
 * ## Failure Handling
 *
 * To prevent infinite retry loops (e.g., if LSP4IJ keeps restarting a failing server),
 * a failure marker file is created on provisioning failure. Subsequent attempts will
 * fail fast until the marker is cleared via [clearFailure] or by deleting the marker file.
 *
 * @param cacheDir Directory for caching downloaded JREs (defaults to GRADLE_USER_HOME/caches/xtc-jre)
 * @param version Target Java major version (defaults to [TARGET_VERSION])
 */
class JreProvisioner(
    private val cacheDir: Path = defaultCacheDir(),
    private val version: Int = TARGET_VERSION,
) {
    companion object {
        /** Target Java version for the LSP server (must match TreeSitterAdapter.MIN_JAVA_VERSION). */
        const val TARGET_VERSION = 25

        /** How often to check for newer JRE versions (in days). */
        private const val CACHE_CHECK_INTERVAL_DAYS = 7L

        /**
         * Returns the default cache directory for downloaded JREs.
         * Uses GRADLE_USER_HOME if set, otherwise ~/.gradle.
         */
        fun defaultCacheDir(): Path {
            val gradleHome =
                System.getenv("GRADLE_USER_HOME")
                    ?: Path.of(System.getProperty("user.home"), ".gradle").toString()
            return Path.of(gradleHome, "caches", "xtc-jre")
        }
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
    private val metadataFile: Path get() = cacheDir.resolve("temurin-$version-jre.json")
    private val failureMarker: Path get() = cacheDir.resolve(".provision-failed-$version")
    private val isWindows get() = "windows" in System.getProperty("os.name").lowercase()

    val javaPath: Path?
        get() {
            logger.info("[jre-resolve] Resolving Java $version+ for LSP server...")

            findSystemJava()?.let { java ->
                logger.info("[jre-resolve] SELECTED: registered JDK -> $java")
                return java
            }
            logger.info("[jre-resolve] No suitable JDK (>= $version) found in IntelliJ's Project SDK table")

            findCachedJava()?.let { java ->
                logger.info("[jre-resolve] SELECTED: cached Temurin JRE -> $java")
                return java
            }
            logger.info("[jre-resolve] No cached JRE at $jreDir")

            logger.info("[jre-resolve] No Java $version+ found -- will need to download from Foojay")
            return null
        }

    internal fun findCachedJava(): Path? {
        if (!jreDir.exists()) return null
        val javaName = if (isWindows) "java.exe" else "java"
        return Files
            .walk(jreDir, 5) // max depth 5 handles any nested structure
            .filter { it.fileName.toString() == javaName && it.parent?.fileName.toString() == "bin" }
            .filter { it.isExecutable() }
            .findFirst()
            .getOrNull()
    }

    private fun findSystemJava(): Path? {
        val javaSdk = JavaSdk.getInstance()
        val allSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk)
        logger.info("[jre-resolve] IntelliJ SDK table has ${allSdks.size} registered JDK(s):")
        allSdks.forEach { sdk ->
            val sdkVersion = javaSdk.getVersion(sdk)
            val major = sdkVersion?.maxLanguageLevel?.feature() ?: 0
            val vmPath = javaSdk.getVMExecutablePath(sdk)
            val suitable = if (major >= version) "OK" else "too old"
            logger.info("[jre-resolve]   ${sdk.name}: Java $major ($suitable) -> $vmPath")
        }
        return allSdks.firstNotNullOfOrNull { sdk ->
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
        metadataFile.deleteIfExists()
        if (jreDir.exists()) FileUtil.delete(jreDir.toFile())
    }

    fun provision(onProgress: ((Float, String) -> Unit)? = null): Path {
        // Check for system JDK first (javaPath getter already logged details)
        findSystemJava()?.let {
            logger.info("[jre-resolve] provision: using registered JDK -> $it")
            return it
        }

        if (hasFailedBefore()) {
            error("JRE provisioning previously failed. Delete $failureMarker to retry.")
        }

        val (os, arch, archiveType) = platform()
        logger.info("Detected platform: os=$os, arch=$arch, archiveType=$archiveType")

        // Check if cached JRE is still valid
        val cachedJava = findCachedJava()
        val metadata = loadMetadata()
        if (cachedJava != null && metadata != null && !shouldRefreshCache(metadata, os, arch)) {
            logger.info("Using cached JRE (package: ${metadata.packageId}): $cachedJava")
            return cachedJava
        }

        // Check for newer version or download fresh
        logger.info("Checking Foojay for latest JRE package...")
        onProgress?.invoke(0.1f, "Finding JRE package...")

        val pkg = findPackage(os, arch, archiveType) ?: error("No JRE found for Java $version on $os-$arch")
        logger.info("Found package: ${pkg.filename} (id: ${pkg.id})")

        // If cached version matches latest, just update the check timestamp
        if (cachedJava != null && metadata?.packageId == pkg.id) {
            logger.info("Cached JRE is up-to-date")
            saveMetadata(CacheMetadata(pkg.id, os, arch, Instant.now().epochSecond))
            return cachedJava
        }

        // Download new version
        logger.info("Downloading: ${pkg.filename} from ${pkg.links.downloadRedirect}")
        onProgress?.invoke(0.2f, "Downloading ${pkg.filename}...")

        // Clear old cache if exists
        if (jreDir.exists()) {
            logger.info("Removing old cached JRE")
            FileUtil.delete(jreDir.toFile())
        }

        val archive = download(pkg.links.downloadRedirect)

        onProgress?.invoke(0.8f, "Extracting...")
        runCatching {
            extract(archive, archiveType)
        }.onFailure { e ->
            Files.createFile(failureMarker)
            if (jreDir.exists()) FileUtil.delete(jreDir.toFile())
            throw e
        }
        archive.deleteIfExists()

        // Save metadata for future cache validation
        saveMetadata(CacheMetadata(pkg.id, os, arch, Instant.now().epochSecond))

        onProgress?.invoke(1.0f, "Done")
        val java = findCachedJava()
        if (java == null) {
            Files.createFile(failureMarker)
            error("Java executable not found after extraction at $jreDir")
        }
        logger.info("JRE provisioned successfully: $java")
        return java
    }

    private fun shouldRefreshCache(
        metadata: CacheMetadata,
        os: String,
        arch: String,
    ): Boolean {
        // Platform mismatch - shouldn't happen but check anyway
        if (metadata.os != os || metadata.arch != arch) {
            logger.info("Platform mismatch in cache (cached: ${metadata.os}-${metadata.arch}, current: $os-$arch)")
            return true
        }

        // Check if cache is old enough to warrant a refresh check
        val lastCheck = Instant.ofEpochSecond(metadata.lastCheckedEpoch)
        val daysSinceCheck = Duration.between(lastCheck, Instant.now()).toDays()
        if (daysSinceCheck >= CACHE_CHECK_INTERVAL_DAYS) {
            logger.info("Cache is $daysSinceCheck days old, will check for updates")
            return true
        }

        logger.info("Cache is fresh ($daysSinceCheck days old, threshold: $CACHE_CHECK_INTERVAL_DAYS days)")
        return false
    }

    private fun loadMetadata(): CacheMetadata? {
        if (!metadataFile.exists()) return null
        return runCatching {
            json.decodeFromString<CacheMetadata>(metadataFile.readText())
        }.onFailure {
            logger.warn("Failed to read cache metadata: ${it.message}")
        }.getOrNull()
    }

    private fun saveMetadata(metadata: CacheMetadata) {
        cacheDir.createDirectories()
        metadataFile.writeText(json.encodeToString(CacheMetadata.serializer(), metadata))
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
     * Flatten the top-level archive directory if there's exactly one.
     * JRE archives typically have a versioned root like "jdk-25.0.1+9-jre/" - we move its contents up.
     * The internal structure (bin/java vs Contents/Home/bin/java) doesn't matter since findCachedJava() searches.
     */
    internal fun flattenSingleSubdirectory() {
        val entries = jreDir.listDirectoryEntries()

        // If there's exactly one directory entry, flatten it
        val nestedDir = entries.singleOrNull()?.takeIf { it.isDirectory() }
        if (nestedDir == null) {
            logger.info("No single nested directory to flatten (${entries.size} entries)")
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

    @Serializable
    private data class CacheMetadata(
        val packageId: String,
        val os: String,
        val arch: String,
        val lastCheckedEpoch: Long,
    )
}
