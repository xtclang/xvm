# PLAN: Out-of-Process LSP Server with JRE Provisioning

**Goal**: Run the XTC LSP server as a separate process with Java 25, enabling full tree-sitter
support regardless of IntelliJ's JBR version.

**Status**: ✅ COMPLETE (2026-02-03, updated 2026-02-12 with DAP wiring)

**Risk**: Medium (significant plugin architecture change, external dependencies)
**Prerequisites**: Working LSP server (see [PLAN_TREE_SITTER.md](./PLAN_TREE_SITTER.md))

**Related**: This plan implements "Task: Out-of-Process LSP Server" from PLAN_TREE_SITTER.md

---

## Background

### The Problem

All versions of jtreesitter require Java 22+ (Foreign Function & Memory API).
IntelliJ 2025.1 ships with JBR 21. Tree-sitter **cannot work in-process**.

See [PLAN_TREE_SITTER.md § Critical: Java Version Compatibility](./PLAN_TREE_SITTER.md#️-critical-java-version-compatibility-issue)

### The Solution

Run the LSP and DAP servers as separate processes with their own JRE:

```
┌──────────────────────────────────────────────────────────────┐
│                  IntelliJ Plugin (JBR 21)                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  XtcLanguageServerFactory (LSP4IJ)                     │  │
│  │    └─ XtcLspConnectionProvider                         │  │
│  │        └─ JreProvisioner.javaPath (resolution order):  │  │
│  │            1. ProjectJdkTable: registered Java 25+ SDK │  │
│  │            2. Gradle cache: ~/.gradle/caches/xtc-jre/  │  │
│  │            3. Download: Foojay API → Temurin JRE 25    │  │
│  │                                                        │  │
│  │  XtcDebugAdapterFactory (LSP4IJ DAP)                   │  │
│  │    └─ XtcDebugAdapterDescriptor                        │  │
│  │        └─ (same JreProvisioner + PluginPaths)          │  │
│  │                                                        │  │
│  │  PluginPaths.findServerJar("xtc-lsp-server.jar")       │  │
│  │  PluginPaths.findServerJar("xtc-dap-server.jar")       │  │
│  │    → <plugin-dir>/bin/ (NOT lib/ — avoids classloader  │  │
│  │      conflicts with LSP4IJ's bundled lsp4j)            │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                            │ stdin/stdout (JSON-RPC)
                            ▼
┌──────────────────────────────────────────────────────────────┐
│              LSP Server Process (Java 25)                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  XtcLanguageServerLauncher                             │  │
│  │    └─ XtcLanguageServer                                │  │
│  │        └─ TreeSitterAdapter (jtreesitter + FFM API)    │  │
│  └────────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│              DAP Server Process (Java 25)                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  XtcDebugServerLauncher                                │  │
│  │    └─ XtcDebugServer (IDebugProtocolServer)            │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## Design Decisions

### JRE Source: Foojay Disco API

The [Foojay Disco API](https://github.com/foojayio/discoapi) is the standard for programmatic
JDK/JRE discovery, used by Gradle Toolchains and many IDE plugins.

**Why Foojay?**
- Aggregates all major distributions (Adoptium, Azul, Oracle, etc.)
- Stable API with versioning
- Returns checksums for verification
- Supports filtering by version, architecture, OS, package type

**Preferred Distribution**: Eclipse Temurin (Adoptium)
- Most widely used open-source distribution
- Predictable release schedule
- Long-term support

### JRE Resolution Strategy

The provisioner (`JreProvisioner.kt`) finds a suitable JRE using this priority order:

1. **Registered JDKs**: Checks IntelliJ's `ProjectJdkTable` for any Java 25+ SDK
2. **Gradle Cache**: `{GRADLE_USER_HOME}/caches/xtc-jre/temurin-25-jre/`
3. **Foojay Download**: Eclipse Temurin JRE 25 (only if no JRE found)

### JRE Cache Location

```
{GRADLE_USER_HOME}/caches/
└── xtc-jre/
    ├── temurin-25-jre/
    │   ├── bin/
    │   │   └── java
    │   ├── lib/
    │   └── ...
    ├── temurin-25-jre.json       # Package metadata (for update checks)
    └── .provision-failed-25      # Failure marker (prevents retry loops)
```

Default `GRADLE_USER_HOME` is `~/.gradle`, so the default cache path is `~/.gradle/caches/xtc-jre/`.

**Why Gradle user home (not IDE system path)?**
- Persists across IDE sessions and cache invalidation
- Won't be cleared by IntelliJ's "Invalidate Caches"
- Consistent with Gradle's own JDK toolchain cache location
- Survives plugin and IDE updates

### Java Version: 25

**Target**: Java 25 (GA since September 2025)

**Rationale**:
- Java 25 has stable FFM API
- Eclipse Temurin provides production-ready 25 binaries
- Gradle 9.x supports Java 25 toolchain

---

## Implementation Status

> **Last Updated**: 2026-02-03

### Phase 1: LSP Server Standalone Mode - COMPLETE ✅

- [x] Update `lsp-server/build.gradle.kts` to Java 25 toolchain
- [x] Verify fat JAR runs standalone: `java -jar xtc-lsp-server.jar`
- [x] Add proper exit handling for stdio mode

### Phase 2: JRE Provisioner - COMPLETE ✅

- [x] Create `JreProvisioner.kt` in intellij-plugin (~200 lines)
- [x] Implement Foojay Disco API client (inline, no separate file)
- [x] Download with progress notification
- [x] Extract archive using IntelliJ's `Decompressor.Tar`/`Decompressor.Zip`
- [ ] ~~Verify checksum~~ (deferred - Foojay API checksums optional)

### Phase 3: Plugin Integration - COMPLETE ✅

- [x] Modify `XtcLspServerSupportProvider` for out-of-process
- [x] Find lsp-server.jar in plugin bin/ directory
- [x] Launch server process with provisioned JRE
- [x] Handle startup failures gracefully with notification

### Phase 4: Polish - PENDING

- [ ] Settings UI for JRE location override
- [ ] Manual JRE refresh action
- [ ] Diagnostics action (check JRE, server status)

---

## Foojay Disco API Integration

### API Endpoint

```
GET https://api.foojay.io/disco/v3.0/packages
```

### Query Parameters

| Parameter | Value | Notes |
|-----------|-------|-------|
| `version` | `25` | Major version only |
| `distribution` | `temurin` | Eclipse Adoptium |
| `architecture` | `aarch64` or `x64` | Detected at runtime |
| `operating_system` | `macos`, `linux`, `windows` | Detected at runtime |
| `archive_type` | `tar.gz` or `zip` | tar.gz for Unix, zip for Windows |
| `package_type` | `jre` | Minimal runtime, not full JDK |
| `javafx_bundled` | `false` | Not needed |
| `latest` | `available` | Get latest available build |

### Example Request

```
GET https://api.foojay.io/disco/v3.0/packages?version=25&distribution=temurin&architecture=aarch64&operating_system=macos&archive_type=tar.gz&package_type=jre&javafx_bundled=false&latest=available
```

### Example Response

```json
{
  "result": [
    {
      "id": "...",
      "archive_type": "tar.gz",
      "distribution": "temurin",
      "major_version": 25,
      "java_version": "25+36",
      "operating_system": "macos",
      "architecture": "aarch64",
      "package_type": "jre",
      "filename": "OpenJDK25U-jre_aarch64_mac_hotspot_25_36.tar.gz",
      "links": {
        "pkg_download_redirect": "https://api.foojay.io/disco/v3.0/ids/.../redirect",
        "pkg_info_uri": "https://api.foojay.io/disco/v3.0/ids/..."
      },
      "checksum": "sha256:abc123...",
      "checksum_type": "sha256",
      "size": 48234567
    }
  ]
}
```

### Download Flow

1. **Query API** → Get package metadata including download URL and checksum
2. **Check cache** → If `~/.gradle/caches/xtc-jre/temurin-25-jre/` exists and version matches, skip
3. **Download** → Use redirect URL, show progress in notification
4. **Verify** → SHA-256 checksum validation
5. **Extract** → tar.gz (Unix) or zip (Windows) to cache directory
6. **Validate** → Run `java -version` to confirm working

---

## Platform Detection

### Kotlin Implementation

```kotlin
object PlatformDetector {
    data class Platform(
        val os: String,           // macos, linux, windows
        val arch: String,         // aarch64, x64
        val archiveType: String,  // tar.gz, zip
        val executableName: String // java, java.exe
    )

    fun detect(): Platform {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val os = when {
            osName.contains("mac") -> "macos"
            osName.contains("linux") -> "linux"
            osName.contains("windows") -> "windows"
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }

        val arch = when {
            osArch == "aarch64" || osArch == "arm64" -> "aarch64"
            osArch == "amd64" || osArch == "x86_64" -> "x64"
            else -> throw UnsupportedOperationException("Unsupported arch: $osArch")
        }

        val archiveType = if (os == "windows") "zip" else "tar.gz"
        val executableName = if (os == "windows") "java.exe" else "java"

        return Platform(os, arch, archiveType, executableName)
    }
}
```

---

## JreProvisioner Design

### Public API

```kotlin
/**
 * Provisions a JRE for the XTC LSP server.
 *
 * Downloads Eclipse Temurin JRE via Foojay Disco API if not cached.
 * Extracts to ~/.xtc/jre/ and returns path to java executable.
 */
class JreProvisioner(
    private val cacheDir: Path = Path.of(System.getProperty("user.home"), ".xtc", "jre"),
    private val targetVersion: Int = 25,
    private val distribution: String = "temurin"
) {
    /**
     * Ensures JRE is provisioned and returns path to java executable.
     *
     * @param progress Optional callback for download progress (0.0 to 1.0)
     * @return Path to java executable
     * @throws JreProvisioningException if provisioning fails
     */
    suspend fun provision(progress: ((Float) -> Unit)? = null): Path

    /**
     * Checks if JRE is already provisioned.
     */
    fun isProvisioned(): Boolean

    /**
     * Returns path to java executable if provisioned, null otherwise.
     */
    fun getJavaExecutable(): Path?

    /**
     * Deletes cached JRE, forcing re-download on next provision().
     */
    fun clearCache()
}
```

### Error Handling

```kotlin
sealed class JreProvisioningException(message: String, cause: Throwable? = null)
    : Exception(message, cause) {

    /** Network error during API call or download */
    class NetworkError(message: String, cause: Throwable? = null)
        : JreProvisioningException(message, cause)

    /** No JRE available for this platform */
    class UnsupportedPlatform(val os: String, val arch: String)
        : JreProvisioningException("No JRE available for $os-$arch")

    /** Checksum verification failed */
    class ChecksumMismatch(val expected: String, val actual: String)
        : JreProvisioningException("Checksum mismatch: expected $expected, got $actual")

    /** Extraction failed */
    class ExtractionError(message: String, cause: Throwable? = null)
        : JreProvisioningException(message, cause)

    /** Downloaded JRE doesn't work */
    class ValidationError(message: String)
        : JreProvisioningException(message)
}
```

---

## IntelliJ Plugin Integration

### LSP4IJ-Based Architecture

The plugin uses LSP4IJ (not IntelliJ's built-in LSP) for both LSP and DAP support.
See `PLAN_IDE_INTEGRATION.md § Design Decision: LSP4IJ over IntelliJ Built-in LSP`.

**Key classes:**

| Class | Role |
|-------|------|
| `XtcLanguageServerFactory` | LSP4IJ `LanguageServerFactory` — creates connection providers |
| `XtcLspConnectionProvider` | Extends `OSProcessStreamConnectionProvider` — launches LSP server |
| `XtcDebugAdapterFactory` | LSP4IJ `DebugAdapterDescriptorFactory` — creates DAP descriptors |
| `XtcDebugAdapterDescriptor` | Extends `DebugAdapterDescriptor` — launches DAP server |
| `PluginPaths` | Shared utility — finds server JARs in plugin `bin/` directory |
| `JreProvisioner` | Shared — resolves/downloads Java 25+ JRE |

### Server JAR Location

Server JARs are placed in `<plugin-dir>/bin/`, **not** `lib/`. If placed in `lib/`, IntelliJ
loads their bundled lsp4j classes which conflict with LSP4IJ's own lsp4j. The `bin/` directory
is not on IntelliJ's classloader path.

```
<plugin-dir>/
├── lib/                        # IntelliJ classloader path (plugin classes)
│   └── xtc-intellij-plugin.jar
└── bin/                        # Out-of-process server JARs (NOT on classloader)
    ├── xtc-lsp-server.jar
    └── xtc-dap-server.jar
```

`PluginPaths.findServerJar(jarName)` resolves JARs using `PluginManagerCore` with a
classloader-based fallback. On failure, the exception lists all searched paths.

### Progress Notification

During JRE download, users see:

```
┌─────────────────────────────────────────────────┐
│ 🔄 Downloading Java Runtime for XTC...          │
│ ████████████░░░░░░░░░░░░░░░░░  42%  (19/45 MB)  │
└─────────────────────────────────────────────────┘
```

After completion:

```
┌─────────────────────────────────────────────────┐
│ ✓ XTC Language Server ready (Java 25)           │
└─────────────────────────────────────────────────┘
```

---

## Build Changes

The LSP server toolchain version is configured in `version.properties` (`org.xtclang.kotlin.jdk`).
Both `lsp-server/build.gradle.kts` and `dap-server/build.gradle.kts` read this property.

The IntelliJ plugin bundles server JARs in `bin/` via the `prepareSandbox` task in
`intellij-plugin/build.gradle.kts`. JRE provisioning uses Java's built-in `HttpClient`
and IntelliJ's `Decompressor.Tar`/`Decompressor.Zip` — no Ktor or Commons Compress.

---

## File Structure

```
intellij-plugin/src/main/kotlin/org/xtclang/idea/
├── PluginPaths.kt                        # Shared: find server JARs in plugin bin/
├── dap/
│   └── XtcDebugAdapterFactory.kt         # DAP factory + descriptor (out-of-process)
├── lsp/
│   ├── XtcLspServerSupportProvider.kt    # LSP factory + connection provider (out-of-process)
│   └── jre/
│       └── JreProvisioner.kt             # JRE resolution + Foojay download (~350 lines)
├── project/                              # New Project wizard
└── run/                                  # Run configurations
```

`JreProvisioner.kt` handles:
- Querying Foojay Disco API for JRE packages
- Downloading with progress callback
- Extracting using IntelliJ's built-in `Decompressor.Tar`/`Decompressor.Zip`
- Caching in `~/.gradle/caches/xtc-jre/temurin-25-jre/`
- Failure markers to prevent retry loops
- Periodic update checks (every 7 days)

---

## Testing Strategy

### Unit Tests

```kotlin
class JreProvisionerTest {
    @Test
    fun `provision downloads JRE when not cached`() { ... }

    @Test
    fun `provision uses cache when available`() { ... }

    @Test
    fun `provision validates checksum`() { ... }

    @Test
    fun `provision handles network failure gracefully`() { ... }
}

class FoojayClientTest {
    @Test
    fun `parses API response correctly`() { ... }

    @Test
    fun `handles empty result list`() { ... }

    @Test
    fun `selects latest version`() { ... }
}

class PlatformDetectorTest {
    @Test
    fun `detects macOS ARM correctly`() { ... }

    @Test
    fun `detects Windows x64 correctly`() { ... }
}
```

### Integration Tests

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Test standalone server with Java 25
export JAVA_HOME=/path/to/java24
$JAVA_HOME/bin/java -jar lang/lsp-server/build/libs/xtc-lsp-server-fat.jar

# Test IntelliJ plugin
./gradlew :lang:intellij-plugin:runIde
# Open .x file, verify tree-sitter adapter loads
```

### Manual Testing Checklist

- [ ] First launch on clean system (no ~/.gradle/caches/xtc-jre/)
- [ ] Progress notification shows during download
- [ ] Server starts after download completes
- [ ] Subsequent launches use cached JRE (no download)
- [ ] Clear cache and re-download works
- [ ] Offline mode shows appropriate error
- [ ] Settings UI allows custom JRE path

---

## Fallback Behavior

If JRE provisioning fails:

1. **Show notification** with error details and retry option
2. **Fall back to mock adapter** (same as current behavior)
3. **Log detailed error** for troubleshooting

```kotlin
private suspend fun ensureJreProvisioned(): Path? {
    return try {
        provisioner.provision { progress ->
            // Update notification
        }
    } catch (e: JreProvisioningException) {
        LOG.warn("JRE provisioning failed, falling back to mock adapter", e)

        Notifications.Bus.notify(
            Notification(
                "XTC",
                "XTC Language Server",
                "Could not download Java runtime: ${e.message}. " +
                "Using basic syntax support. " +
                "<a href='retry'>Retry</a> | <a href='settings'>Settings</a>",
                NotificationType.WARNING
            ).apply {
                addAction(NotificationAction.create("Retry") { _, notification ->
                    notification.expire()
                    // Retry provisioning
                })
            },
            project
        )

        null  // Signal to use mock adapter
    }
}
```

---

## Security Considerations

### Checksum Verification

Always verify SHA-256 checksum before extraction:

```kotlin
private fun verifyChecksum(file: Path, expected: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }

    if (!actual.equals(expected, ignoreCase = true)) {
        throw JreProvisioningException.ChecksumMismatch(expected, actual)
    }
}
```

### HTTPS Only

All Foojay API calls and downloads use HTTPS.

### No Code Execution During Download

The downloaded archive is only extracted, never executed, until checksum is verified
and extraction is complete.

---

## Process Lifecycle Management

### CRITICAL: No Orphan Processes

The LSP server process **must never** be left running after the plugin terminates. This section documents
the mechanisms to ensure clean process cleanup in all scenarios.

### Shutdown Scenarios

| Scenario | Cleanup Mechanism |
|----------|-------------------|
| IDE closes normally | `Disposer` hierarchy invokes `dispose()` |
| IDE crashes | JVM shutdown hook + OS process termination |
| Project closed | `ProjectManagerListener.projectClosed()` |
| Plugin disabled | `DynamicPluginListener.pluginUnloaded()` |
| User requests restart | Explicit `shutdown`/`exit` → restart |
| LSP server crashes | Monitor thread detects, logs, optionally restarts |

### Implementation: Server Process Manager

```kotlin
@Service(Service.Level.PROJECT)
class XtcLspServerManager(private val project: Project) : Disposable {

    private var serverProcess: Process? = null
    private val processLock = ReentrantLock()

    /**
     * Starts the LSP server process if not already running.
     * Returns the process's stdin/stdout streams for LSP communication.
     */
    fun ensureServerRunning(javaPath: Path, serverJar: Path): LspStreams {
        processLock.withLock {
            serverProcess?.let { proc ->
                if (proc.isAlive) {
                    return LspStreams(proc.outputStream, proc.inputStream)
                }
            }

            val process = ProcessBuilder(
                javaPath.toString(),
                "-jar",
                serverJar.toString()
            )
                .directory(project.basePath?.let { File(it) })
                .redirectErrorStream(false) // Separate stderr for logging
                .start()

            serverProcess = process

            // Forward stderr to console (visible during runIde) and IDE logs
            startStderrForwarder(process)

            return LspStreams(process.outputStream, process.inputStream)
        }
    }

    /**
     * Sends LSP shutdown request, then exit notification, then kills process.
     * Called automatically via Disposer when project closes or IDE exits.
     */
    override fun dispose() {
        processLock.withLock {
            serverProcess?.let { proc ->
                if (proc.isAlive) {
                    try {
                        // Send LSP shutdown request (waits for response)
                        sendShutdownRequest(proc)

                        // Send LSP exit notification (no response expected)
                        sendExitNotification(proc)

                        // Wait briefly for graceful exit
                        if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                            LOG.warn("LSP server did not exit gracefully, forcing termination")
                            proc.destroyForcibly()
                        }
                    } catch (e: Exception) {
                        LOG.warn("Error during LSP server shutdown", e)
                        proc.destroyForcibly()
                    }
                }
                serverProcess = null
            }
        }
    }

    private fun sendShutdownRequest(proc: Process) {
        // JSON-RPC: {"jsonrpc":"2.0","id":1,"method":"shutdown"}
        val request = """{"jsonrpc":"2.0","id":99999,"method":"shutdown"}"""
        val header = "Content-Length: ${request.length}\r\n\r\n"
        proc.outputStream.write((header + request).toByteArray())
        proc.outputStream.flush()

        // Wait for response (blocks until server acknowledges)
        // Timeout handled by caller's waitFor()
    }

    private fun sendExitNotification(proc: Process) {
        // JSON-RPC: {"jsonrpc":"2.0","method":"exit"}
        val notification = """{"jsonrpc":"2.0","method":"exit"}"""
        val header = "Content-Length: ${notification.length}\r\n\r\n"
        proc.outputStream.write((header + notification).toByteArray())
        proc.outputStream.flush()
    }

    private fun startStderrForwarder(proc: Process) {
        Thread({
            proc.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    // Print to System.err so it appears in Gradle console during runIde
                    System.err.println("[XTC-LSP] $line")

                    // Also log via IntelliJ logger for production log files
                    when {
                        line.contains("ERROR") || line.contains("Exception") ->
                            LOG.error("LSP: $line")
                        line.contains("WARN") ->
                            LOG.warn("LSP: $line")
                        else ->
                            LOG.info("LSP: $line")
                    }
                }
            }
        }, "XTC-LSP-stderr-forwarder").apply {
            isDaemon = true // Won't prevent JVM exit
            start()
        }
    }

    data class LspStreams(
        val toServer: OutputStream,    // stdin
        val fromServer: InputStream    // stdout
    )

    companion object {
        private val LOG = Logger.getInstance(XtcLspServerManager::class.java)

        fun getInstance(project: Project): XtcLspServerManager =
            project.service()
    }
}
```

### JVM Shutdown Hook (Belt and Suspenders)

In addition to `Disposer`, register a JVM shutdown hook for crash scenarios:

```kotlin
class XtcLspServerSupportProvider : LspServerSupportProvider {

    init {
        // Register shutdown hook at class load time
        Runtime.getRuntime().addShutdownHook(Thread({
            ProcessHandle.current().children().forEach { child ->
                child.info().command().ifPresent { cmd ->
                    if (cmd.contains("xtc-lsp-server.jar")) {
                        LOG.info("Shutdown hook: terminating orphan LSP server ${child.pid()}")
                        child.destroyForcibly()
                    }
                }
            }
        }, "XTC-LSP-shutdown-hook"))
    }
}
```

### Process Invisibility

The LSP server runs as a **background daemon process**:

| Aspect | Implementation |
|--------|----------------|
| No console window (Windows) | `ProcessBuilder.redirectErrorStream(false)` + no `START` command |
| No dock icon (macOS) | JVM property `-Dapple.awt.UIElement=true` |
| Process name | Identifiable as `java -jar xtc-lsp-server.jar` in process list |
| Stderr | Captured and logged at DEBUG level, never shown to user |
| Stdout | LSP JSON-RPC only, consumed by LSP4IJ |

Updated command line for invisibility:

```kotlin
val process = ProcessBuilder(
    javaPath.toString(),
    "-Dapple.awt.UIElement=true",     // macOS: no dock icon
    "-Djava.awt.headless=true",        // No GUI components
    "-Xms32m",                         // Modest initial heap
    "-Xmx256m",                        // Cap memory usage
    "-jar",
    serverJar.toString()
)
```

### LSP Lifecycle Protocol

The plugin follows standard LSP lifecycle:

```
┌─────────────┐                           ┌─────────────┐
│   Plugin    │                           │ LSP Server  │
└──────┬──────┘                           └──────┬──────┘
       │                                         │
       │  ──── Start Process ────────────────▶  │
       │                                         │
       │  ──── initialize request ───────────▶  │
       │  ◀─── initialize response ───────────  │
       │                                         │
       │  ──── initialized notification ─────▶  │
       │                                         │
       │         ... normal operation ...        │
       │                                         │
       │  ──── shutdown request ─────────────▶  │  ◀── Project/IDE closing
       │  ◀─── shutdown response (null) ──────  │
       │                                         │
       │  ──── exit notification ────────────▶  │
       │                                         │
       │              Process exits              │
       └─────────────────────────────────────────┘
```

**Key Points:**
- `initialize` is a **request** (expects response) - establishes capabilities
- `initialized` is a **notification** (no response) - signals ready for work
- `shutdown` is a **request** (expects response) - server prepares to exit
- `exit` is a **notification** (no response) - server must exit immediately

### Handling Server Crashes

If the server process dies unexpectedly:

```kotlin
class ServerHealthMonitor(
    private val project: Project,
    private val process: Process,
    private val onCrash: () -> Unit
) {
    private val monitorThread = Thread({
        try {
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                LOG.warn("LSP server exited with code $exitCode")
                ApplicationManager.getApplication().invokeLater {
                    Notifications.Bus.notify(
                        Notification(
                            "XTC",
                            "XTC Language Server",
                            "Language server stopped unexpectedly. " +
                            "<a href='restart'>Restart</a>",
                            NotificationType.WARNING
                        ).apply {
                            addAction(NotificationAction.create("Restart") { _, notification ->
                                notification.expire()
                                onCrash()
                            })
                        },
                        project
                    )
                }
            }
        } catch (e: InterruptedException) {
            // Normal shutdown, ignore
        }
    }, "XTC-LSP-monitor").apply {
        isDaemon = true
        start()
    }

    fun stop() {
        monitorThread.interrupt()
    }
}
```

### Testing Process Cleanup

Manual verification checklist:

- [ ] Close project → `ps aux | grep xtc-lsp` shows no process
- [ ] Close IDE normally → no orphan processes
- [ ] `kill -9` the IDE process → shutdown hook cleans up server
- [ ] Disable plugin → server stops
- [ ] Multiple projects open → each has own server, all cleaned up on close
- [ ] Server crashes → notification shown, restart works

---

## Logging Strategy

### CRITICAL: All LSP Logs Must Be Visible

The LSP server **already has extensive `logger.info` calls** throughout `XtcLanguageServer.kt`:

- `didOpen`, `didClose`, `didChange`, `didSave` events
- `hover`, `completion`, `definition`, `references`, `documentSymbol` requests
- Timing information for all operations (e.g., "compiled in 12.3ms")
- Initialization, shutdown, and exit lifecycle events

**RESOLVED**: The logback.xml now defaults to INFO level with configurable override via
`-Dxtc.logLevel` or `XTC_LOG_LEVEL` environment variable. Logs go to both stderr (for LSP4IJ panel)
and a rolling file at `~/.xtc/logs/lsp-server.log`.

### Log Streams

| Stream | Content | Handling |
|--------|---------|----------|
| **stdout** | LSP JSON-RPC messages | Consumed by LSP4IJ (protocol) - NEVER touch |
| **stderr** | Log messages via SLF4J/Logback | **Must appear in Gradle console during runIde** |

### Solution: Dual-Appender Logging (IMPLEMENTED)

**lsp-server/src/main/resources/logback.xml** uses INFO by default with two appenders:

- **STDERR appender**: `%d{HH:mm:ss} %-5level %logger{0} - %msg%n` (for LSP4IJ's Language Servers panel)
- **FILE appender**: `%d{HH:mm:ss.SSS} %-5level %logger{0} - %msg%n` (rolling log at `~/.xtc/logs/lsp-server.log`)

The `%logger{0}` pattern displays just the simple class name (e.g., `XtcLanguageServer`, `TreeSitterAdapter`).
No hardcoded `[ClassName]` brackets in the log messages -- logback handles class name display.

Log level is configurable via: `-Dxtc.logLevel=DEBUG`, `XTC_LOG_LEVEL=DEBUG`, or `-Plog=DEBUG`.
Default is INFO. LSP4J logger is set to ERROR (not WARN).

### Expected Console Output During runIde

With INFO level (default), stderr output visible in the Gradle console:

```
10:23:45 INFO  XtcLanguageServer - initialize: ========================================
10:23:45 INFO  XtcLanguageServer - initialize: XTC Language Server v0.4.4 (pid=12345)
10:23:45 INFO  XtcLanguageServer - initialize: Backend: TreeSitter
10:23:45 INFO  XtcLanguageServer - initialize: ========================================
10:23:45 INFO  XtcLanguageServer - connect: connected to language client
10:23:45 INFO  XtcLanguageServer - initialize: workspace folders: [file:///path/to/project]
10:23:45 INFO  XtcLanguageServer - initialize: client capabilities: hover, completion, definition, references
10:23:45 INFO  XtcLanguageServer - initialize: XTC Language Server initialized
10:23:46 INFO  XtcLanguageServer - textDocument/didOpen: file:///path/to/Hello.x (1234 bytes)
10:23:46 INFO  XtcLanguageServer - textDocument/didOpen: compiled in 13.2ms, 0 diagnostics
10:23:47 INFO  XtcLanguageServer - textDocument/hover: file:///path/to/Hello.x at 10:15
10:23:47 INFO  XtcLanguageServer - textDocument/hover: found symbol in 1.8ms
```

### Plugin Side: Forwarding stderr to Gradle Console

The plugin must forward the LSP server's stderr to `System.err` so it appears in the `runIde` console:

```kotlin
private fun startStderrForwarder(proc: Process) {
    Thread({
        proc.errorStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                // Forward to Gradle console (appears during runIde)
                System.err.println("[XTC-LSP] $line")

                // Also log via IntelliJ logger for production log files
                LOG.info("LSP: $line")
            }
        }
    }, "XTC-LSP-stderr-forwarder").apply {
        isDaemon = true
        start()
    }
}
```

### Runtime Log Level Override

Allow verbose logging via system property without rebuilding:

```kotlin
// Plugin passes log level when starting server
val logLevel = System.getProperty("xtc.logLevel", "INFO")

val process = ProcessBuilder(
    javaPath.toString(),
    "-Dxtc.logLevel=$logLevel",               // Log level for LSP server
    "-Dapple.awt.UIElement=true",
    "-Djava.awt.headless=true",
    "-jar",
    serverJar.toString()
)
```

Or use logback's built-in property substitution in `logback.xml`:

```xml
<logger name="org.xvm.lsp" level="${xtc.logLevel:-INFO}"/>
```

Then pass: `-Dxtc.logLevel=DEBUG` for verbose logging.

### Viewing Logs Summary

| Scenario | How to View Logs |
|----------|------------------|
| Development (`runIde`) | Gradle console - logs prefixed with `[XTC-LSP]` |
| Production (installed) | `Help > Diagnostic Tools > Show Log in Finder` |
| Verbose mode | Add `-Dxtc.logLevel=DEBUG` to Gradle or server launch |
| Standalone test | `java -jar xtc-lsp-server.jar` → logs go to stderr |

### LSP4IJ Built-in Log View

LSP4IJ also provides its own view:
- `View > Tool Windows > Language Servers`
- Shows LSP request/response pairs per server
- Useful for protocol-level debugging

---

## Open Questions

1. **Should we support custom JRE path in settings?**
   - Allows users with corporate proxies to manually provision
   - Adds complexity to UI

2. **Should we check for JRE updates periodically?**
   - Could get newer patch versions automatically
   - Adds complexity, potential for breakage

3. **Should we support offline mode with bundled JRE?**
   - Alternative: ship JRE in plugin for air-gapped environments
   - Significant size increase (~40MB per platform)

**Recommendation**: Start simple (download-on-first-use only), add settings/bundling later if needed.

---

## Implementation Order

1. **lsp-server Java 25 toolchain** - Update build, verify fat JAR works
2. **PlatformDetector** - Simple utility, no dependencies
3. **FoojayClient** - API client with tests
4. **JreProvisioner** - Core download/cache logic
5. **ArchiveExtractor** - tar.gz/zip extraction (reuse from tree-sitter)
6. **XtcLspServerSupportProvider** - Integrate provisioner
7. **Progress notification** - UX polish
8. **Settings UI** - Optional JRE path override
9. **Documentation** - Update README, troubleshooting guide

---

## Success Criteria

- [ ] LSP server runs with Java 25 and tree-sitter adapter
- [ ] JRE automatically downloaded on first .x file open
- [ ] Download shows progress notification
- [ ] Cached JRE reused on subsequent launches
- [ ] Graceful fallback to mock adapter on failure
- [ ] Works on all 5 platforms (darwin-arm64, darwin-x64, linux-x64, linux-arm64, windows-x64)
- [ ] < 60 second total time from first open to working LSP (on fast connection)

---

## References

- [Foojay Disco API Documentation](https://github.com/foojayio/discoapi)
- [Foojay API Explorer](https://api.foojay.io/swagger-ui/)
- [LSP4IJ Documentation](https://github.com/redhat-developer/lsp4ij)
- [PLAN_TREE_SITTER.md](./PLAN_TREE_SITTER.md) - Tree-sitter integration context
