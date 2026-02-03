package org.xtclang.idea.lsp.jre

import com.intellij.openapi.diagnostic.logger
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
import kotlin.io.path.isExecutable

/**
 * Provisions a JRE for the XTC LSP server using the Foojay Disco API.
 * Downloads Eclipse Temurin JRE 25 if not already cached in ~/.xtc/jre/.
 */
class JreProvisioner(
    private val cacheDir: Path = Path.of(System.getProperty("user.home"), ".xtc", "jre"),
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
    private val isWindows get() = "windows" in System.getProperty("os.name").lowercase()

    val javaPath: Path?
        get() {
            val exe = jreDir.resolve(if (isWindows) "bin/java.exe" else "bin/java")
            logger.info("Checking for cached JRE at: $exe")
            return exe.takeIf { it.exists() && it.isExecutable() }
        }

    fun isProvisioned(): Boolean = javaPath != null

    fun provision(onProgress: ((Float, String) -> Unit)? = null): Path {
        javaPath?.let {
            logger.info("Using cached JRE: $it")
            return it
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
        extract(archive, archiveType)
        archive.deleteIfExists()

        onProgress?.invoke(1.0f, "Done")
        val java = javaPath ?: error("Java executable not found after extraction at $jreDir")
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
        decompressor
            .removePrefixPath("")
            .extract(jreDir)
        logger.info("Extraction complete")
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
