import de.undercouch.gradle.tasks.download.Download
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.download)
    base
}

// Git commit hash (configuration-cache safe using providers.exec)
val gitCommitShort: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

// =============================================================================
// Tree-sitter Native Library Management
// =============================================================================
// This subproject handles all tree-sitter native tooling:
// - Downloading the tree-sitter CLI (platform-specific binary)
// - Validating generated grammar (tree-sitter generate)
// - Testing parser on XDK corpus files
// - Building native parser library (.so/.dylib/.dll)
// - Staleness detection for pre-built libraries
// =============================================================================

// Depend on DSL project for generated grammar.js and scanner.c
val dslProject: Project = project(":dsl")

// =============================================================================
// Platform Detection
// =============================================================================

val osName: String = System.getProperty("os.name")?.lowercase() ?: ""
val osArch: String = System.getProperty("os.arch") ?: ""

val treeSitterPlatform: String = when {
    osName.contains("mac") && osArch in listOf("aarch64", "arm64") -> "macos-arm64"
    osName.contains("mac") -> "macos-x64"
    osName.contains("linux") && osArch in listOf("amd64", "x86_64") -> "linux-x64"
    osName.contains("linux") && osArch in listOf("aarch64", "arm64") -> "linux-arm64"
    else -> "unsupported"
}

val treeSitterPlatformSupported = treeSitterPlatform != "unsupported"

val nativeLibExt: String = when {
    osName.contains("windows") -> "dll"
    osName.contains("mac") -> "dylib"
    else -> "so"
}

val nativePlatformDir: String = when {
    osName.contains("windows") && osArch in listOf("amd64", "x86_64") -> "windows-x64"
    osName.contains("mac") && osArch in listOf("aarch64", "arm64") -> "darwin-arm64"
    osName.contains("mac") && osArch in listOf("amd64", "x86_64") -> "darwin-x64"
    osName.contains("linux") && osArch in listOf("aarch64", "arm64") -> "linux-arm64"
    osName.contains("linux") && osArch in listOf("amd64", "x86_64") -> "linux-x64"
    else -> throw GradleException("Unsupported platform: $osName/$osArch")
}

// =============================================================================
// Directory Layout
// =============================================================================

val treeSitterCliVersion: String = libs.versions.tree.sitter.cli.get()
val treeSitterCliDir: Provider<Directory> = layout.buildDirectory.dir("tree-sitter-cli")
val generatedDir: Provider<Directory> = layout.buildDirectory.dir("generated")
val nativeOutputDir: Provider<Directory> = layout.buildDirectory.dir("native")

// =============================================================================
// Persistent Cache Directories (in ~/.gradle/caches/)
// =============================================================================
// Native libraries are cached persistently in the Gradle user home directory.
// This avoids checking binaries into source control while still providing fast
// incremental builds. The cache is keyed by a hash of the inputs (grammar.js,
// scanner.c, CLI version).
//
// First build: Downloads Zig (~45MB) + compiles (~20s)
// Subsequent builds: Instant (uses cache)

val gradleUserHome: File = gradle.gradleUserHomeDir
val zigCacheDir: File = File(gradleUserHome, "caches/zig")
val nativeLibCacheDir: File = File(gradleUserHome, "caches/tree-sitter-xtc")

// =============================================================================
// Shared File Providers (reused across tasks)
// =============================================================================

val grammarJsFile: Provider<RegularFile> = generatedDir.map { it.file("grammar.js") }
val scannerCFile: Provider<RegularFile> = generatedDir.map { it.dir("src").file("scanner.c") }
val treeSitterCliExe: Provider<String> = treeSitterCliDir.map { it.file("tree-sitter").asFile.absolutePath }
val nativeLibFile: Provider<RegularFile> = nativeOutputDir.map { it.file("libtree-sitter-xtc.$nativeLibExt") }

// =============================================================================
// Tree-sitter CLI Download
// =============================================================================

/**
 * Download tree-sitter CLI gzipped binary for the current platform.
 */
val downloadTreeSitterCliGz by tasks.registering(Download::class) {
    group = "tree-sitter"
    description = "Download tree-sitter CLI gzipped binary"
    enabled = treeSitterPlatformSupported

    src("https://github.com/tree-sitter/tree-sitter/releases/download/v$treeSitterCliVersion/tree-sitter-$treeSitterPlatform.gz")
    dest(treeSitterCliDir)
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
}

/**
 * Extract the tree-sitter CLI from the downloaded gzip file.
 * Uses Java's built-in GZIPInputStream for platform independence.
 */
val extractTreeSitterCli by tasks.registering {
    group = "tree-sitter"
    description = "Extract tree-sitter CLI from gzip"
    dependsOn(downloadTreeSitterCliGz)
    enabled = treeSitterPlatformSupported

    val gzFile = treeSitterCliDir.map { it.file("tree-sitter-$treeSitterPlatform.gz").asFile }
    val outputFile = treeSitterCliDir.map { it.file("tree-sitter").asFile }

    inputs.file(gzFile)
    outputs.file(outputFile)

    doLast {
        val input = gzFile.get()
        val output = outputFile.get()

        GZIPInputStream(input.inputStream().buffered()).use { gzIn ->
            output.outputStream().buffered().use { out ->
                gzIn.copyTo(out)
            }
        }

        output.setExecutable(true)
        logger.lifecycle("Extracted tree-sitter CLI to: ${output.absolutePath}")
    }
}

// =============================================================================
// Tree-sitter Runtime Library Download
// =============================================================================
// Download tree-sitter source to build the runtime library (libtree-sitter).
// This is needed by jtreesitter at runtime in addition to our grammar library.

val treeSitterRuntimeVersion: String = libs.versions.tree.sitter.cli.get()
val treeSitterSourceDir: Provider<Directory> = layout.buildDirectory.dir("tree-sitter-source")

/**
 * Download tree-sitter source code for building the runtime library.
 */
val downloadTreeSitterSource by tasks.registering(Download::class) {
    group = "tree-sitter"
    description = "Download tree-sitter source for runtime library build"

    src("https://github.com/tree-sitter/tree-sitter/archive/refs/tags/v$treeSitterRuntimeVersion.tar.gz")
    dest(treeSitterSourceDir.map { it.file("tree-sitter-$treeSitterRuntimeVersion.tar.gz") })
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
}

/**
 * Extract tree-sitter source code.
 */
val extractTreeSitterSource by tasks.registering {
    group = "tree-sitter"
    description = "Extract tree-sitter source"
    dependsOn(downloadTreeSitterSource)

    val tarGzFile = treeSitterSourceDir.map { it.file("tree-sitter-$treeSitterRuntimeVersion.tar.gz").asFile }
    val outputDir = treeSitterSourceDir.map { it.asFile }

    inputs.file(tarGzFile)
    outputs.dir(outputDir)

    doLast {
        val input = tarGzFile.get()
        val outDir = outputDir.get()

        GZIPInputStream(input.inputStream().buffered()).use { gzIn ->
            TarArchiveInputStream(gzIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val outFile = File(outDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        outFile.outputStream().buffered().use { out -> tarIn.copyTo(out) }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }
        logger.lifecycle("Extracted tree-sitter source to: ${outDir.absolutePath}")
    }
}

// Path to extracted tree-sitter source lib directory
val treeSitterLibSrc: Provider<Directory> = treeSitterSourceDir.map {
    it.dir("tree-sitter-$treeSitterRuntimeVersion/lib")
}

// =============================================================================
// Zig Compiler Download (for Cross-Compilation)
// =============================================================================
// Zig enables building native libraries for ALL platforms from ANY host machine.
// Download once, build everywhere - no platform-specific toolchains needed.

val zigVersion: String = libs.versions.zig.get()
// Use persistent cache directory instead of build directory
val zigDir: File = File(zigCacheDir, zigVersion)

// Detect host platform for Zig download (format: {os}-{arch})
val zigPlatform: String = when {
    osName.contains("mac") && osArch in listOf("aarch64", "arm64") -> "macos-aarch64"
    osName.contains("mac") && osArch in listOf("amd64", "x86_64") -> "macos-x86_64"
    osName.contains("linux") && osArch in listOf("amd64", "x86_64") -> "linux-x86_64"
    osName.contains("linux") && osArch in listOf("aarch64", "arm64") -> "linux-aarch64"
    osName.contains("windows") && osArch in listOf("amd64", "x86_64") -> "windows-x86_64"
    else -> "unsupported"
}

val zigPlatformSupported = zigPlatform != "unsupported"
val zigArchiveExt = if (osName.contains("windows")) "zip" else "tar.xz"
val zigExeName = if (osName.contains("windows")) "zig.exe" else "zig"

/**
 * Download Zig compiler archive for the current platform.
 * Downloads to persistent cache in ~/.gradle/caches/zig/<version>/
 */
val downloadZig by tasks.registering(Download::class) {
    group = "zig"
    description = "Download Zig compiler for cross-compilation"
    enabled = zigPlatformSupported

    src("https://ziglang.org/download/$zigVersion/zig-$zigPlatform-$zigVersion.$zigArchiveExt")
    dest(File(zigDir, "zig-$zigPlatform-$zigVersion.$zigArchiveExt"))
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
}

/**
 * Extract the Zig compiler from the downloaded archive.
 * Uses Gradle's zipTree for .zip files, and Apache Commons Compress for .tar.xz files.
 * Pure Java implementation - no system executables required.
 */
abstract class ExtractZigTask @Inject constructor(
    private val fsOps: FileSystemOperations,
    private val archiveOps: ArchiveOperations
) : DefaultTask() {

    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val archiveExt: Property<String>

    @TaskAction
    fun extract() {
        val archive = archiveFile.get().asFile
        val outDir = outputDir.get().asFile

        if (archiveExt.get() == "zip") {
            fsOps.copy {
                from(archiveOps.zipTree(archive))
                into(outDir)
            }
        } else {
            // Use Apache Commons Compress for .tar.xz extraction (pure Java)
            extractTarXz(archive, outDir)
        }

        logger.lifecycle("Extracted Zig compiler to: ${outDir.absolutePath}")
    }

    private fun extractTarXz(archive: File, outDir: File) {
        outDir.mkdirs()

        archive.inputStream().buffered().use { fileIn ->
            XZCompressorInputStream(fileIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val outFile = File(outDir, entry.name)

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile.mkdirs()
                            outFile.outputStream().buffered().use { out ->
                                tarIn.copyTo(out)
                            }
                            // Preserve executable permission
                            if (entry.mode and 0b001_000_000 != 0) {
                                outFile.setExecutable(true)
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }
}

val extractZig by tasks.registering(ExtractZigTask::class) {
    group = "zig"
    description = "Extract Zig compiler from archive (cached in ~/.gradle/caches/zig/)"
    dependsOn(downloadZig)
    enabled = zigPlatformSupported

    archiveFile.set(layout.file(downloadZig.map { it.dest }))
    outputDir.set(layout.dir(provider { zigDir }))
    archiveExt.set(zigArchiveExt)
}

val zigExe: String = File(zigDir, "zig-$zigPlatform-$zigVersion/$zigExeName").absolutePath

// =============================================================================
// Copy Grammar Files from DSL Project
// =============================================================================

/**
 * Copy generated grammar.js and scanner.c from the DSL project.
 */
val copyGrammarFiles by tasks.registering(Copy::class) {
    group = "tree-sitter"
    description = "Copy grammar files from DSL project"
    dependsOn(dslProject.tasks.named("generateTreeSitter"), dslProject.tasks.named("generateScannerC"))

    val dslGenerated = dslProject.layout.buildDirectory.dir("generated")
    from(dslGenerated.map { it.file("grammar.js") })
    from(dslGenerated.map { it.dir("src").file("scanner.c") }) {
        into("src")
    }
    into(generatedDir)
}

/**
 * Generate tree-sitter.json config file for ABI version 15 support.
 * This eliminates the warning about missing config file.
 */
val generateTreeSitterConfig by tasks.registering {
    group = "tree-sitter"
    description = "Generate tree-sitter.json config for ABI 15"
    dependsOn(copyGrammarFiles)

    val outputFile = generatedDir.map { it.file("tree-sitter.json") }
    val xdkVersion = project.version.toString()

    inputs.property("version", xdkVersion)
    outputs.file(outputFile)

    doLast {
        val configJson = """
            {
              "grammars": [
                {
                  "name": "xtc",
                  "camelcase": "XTC",
                  "scope": "source.xtc",
                  "path": ".",
                  "file-types": ["x", "xtc"]
                }
              ],
              "metadata": {
                "version": "$xdkVersion",
                "license": "Apache-2.0",
                "description": "XTC (Ecstasy) grammar for tree-sitter",
                "links": {
                  "repository": "https://github.com/xtclang/xvm"
                }
              }
            }
        """.trimIndent()
        outputFile.get().asFile.writeText(configJson + "\n")
    }
}

// =============================================================================
// Grammar Validation
// =============================================================================

/**
 * Validate that the generated tree-sitter grammar compiles.
 * This runs `tree-sitter generate` which validates grammar.js and produces parser.c
 */
val validateTreeSitterGrammar by tasks.registering(Exec::class) {
    group = "tree-sitter"
    description = "Validate tree-sitter grammar compiles"
    dependsOn(copyGrammarFiles, extractTreeSitterCli, generateTreeSitterConfig)
    enabled = treeSitterPlatformSupported

    workingDir(generatedDir)
    executable(treeSitterCliExe.get())
    args("generate")
}

// =============================================================================
// Parse Testing
// =============================================================================

/**
 * Test parsing XTC files from the XDK libraries using tree-sitter CLI.
 * Uses the 675+ .x files in lib_* directories as a comprehensive test corpus.
 *
 * Supports filtering via -PtestFiles=pattern to test specific files.
 * Shows timing information sorted by parse time.
 */
abstract class TreeSitterParseTestTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val cliPath: Property<String>

    @get:Input
    abstract val workDir: Property<File>

    @get:Input
    abstract val libDirs: ListProperty<File>

    @get:Input
    abstract val rootDir: Property<File>

    @get:Input
    @get:Optional
    abstract val fileFilter: Property<String>

    @get:Input
    @get:Optional
    abstract val showTiming: Property<Boolean>

    data class ParseResult(val relativePath: String, val success: Boolean, val timeMs: Long)

    @TaskAction
    fun run() {
        val filter = fileFilter.orNull
        val showTimingInfo = showTiming.getOrElse(true)

        var xtcFiles = libDirs.get().flatMap { libDir ->
            libDir.walkTopDown()
                .filter { it.isFile && it.extension == "x" }
                .toList()
        }

        // Apply filter if specified
        if (!filter.isNullOrBlank()) {
            val patterns = filter.split(",").map { it.trim() }
            xtcFiles = xtcFiles.filter { file ->
                val relativePath = file.relativeTo(rootDir.get()).path
                patterns.any { pattern ->
                    relativePath.contains(pattern, ignoreCase = true) ||
                    file.name.contains(pattern, ignoreCase = true)
                }
            }
            logger.lifecycle("Filter '$filter' matched ${xtcFiles.size} files")
        }

        if (xtcFiles.isEmpty()) {
            logger.warn("No .x files found matching filter")
            return
        }

        // Warmup: parse the first file once to factor out tree-sitter CLI startup time.
        // The CLI loads the grammar shared library and initializes internal state on first run,
        // which adds ~1 second overhead that would skew timing for the first file.
        val warmupFile = xtcFiles.first()
        execOps.exec {
            workingDir(workDir.get())
            executable(cliPath.get())
            args("parse", "--quiet", warmupFile.absolutePath)
            isIgnoreExitValue = true
        }

        logger.lifecycle("Testing tree-sitter parser on ${xtcFiles.size} XTC files...")

        val results = xtcFiles.map { file ->
            val startNanos = System.nanoTime()
            val result = execOps.exec {
                workingDir(workDir.get())
                executable(cliPath.get())
                args("parse", "--quiet", file.absolutePath)
                isIgnoreExitValue = true
            }
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val relativePath = file.relativeTo(rootDir.get()).path
            ParseResult(relativePath, result.exitValue == 0, elapsedMs)
        }

        val passed = results.count { it.success }
        val failed = results.size - passed
        val failures = results.filter { !it.success }.map { it.relativePath }

        logger.lifecycle("Tree-sitter parse results: $passed passed, $failed failed")

        if (failures.isNotEmpty()) {
            logger.lifecycle("Failed files (first 20):")
            failures.take(20).forEach { logger.lifecycle("  - $it") }
            if (failures.size > 20) {
                logger.lifecycle("  ... and ${failures.size - 20} more")
            }
        }

        // Show timing information if requested or if filter is active (smaller dataset)
        if (showTimingInfo || (!filter.isNullOrBlank() && xtcFiles.size <= 50)) {
            logger.lifecycle("")
            logger.lifecycle("Parse timing (slowest first):")
            results.sortedByDescending { it.timeMs }.forEach { r ->
                val status = if (r.success) "OK" else "FAIL"
                logger.lifecycle("  %5dms [%4s] %s".format(r.timeMs, status, r.relativePath))
            }
            val totalMs = results.sumOf { it.timeMs }
            val avgMs = if (results.isNotEmpty()) totalMs / results.size else 0
            val sortedTimes = results.map { it.timeMs }.sorted()
            val medianMs = if (sortedTimes.isNotEmpty()) {
                val mid = sortedTimes.size / 2
                if (sortedTimes.size % 2 == 0) (sortedTimes[mid - 1] + sortedTimes[mid]) / 2 else sortedTimes[mid]
            } else 0
            logger.lifecycle("")
            logger.lifecycle("Total: ${totalMs}ms, Average: ${avgMs}ms, Median: ${medianMs}ms per file")
        }
    }
}

val testTreeSitterParse by tasks.registering(TreeSitterParseTestTask::class) {
    group = "tree-sitter"
    description = "Test tree-sitter parsing on XDK library files. Use -PtestFiles=pattern to filter. Timing shown by default; use -PshowTiming=false to disable."
    dependsOn(validateTreeSitterGrammar)
    enabled = treeSitterPlatformSupported

    // Use XdkPropertiesService to find composite root (works for included builds)
    val compositeRoot = XdkPropertiesService.compositeRootDirectory(projectDir)

    cliPath.set(treeSitterCliExe)
    // NOTE: Setting workDir to the generated directory allows tree-sitter CLI to find the grammar
    // via tree-sitter.json in the current directory. This produces harmless warnings about
    // "not configured any parser directories" because there's no global ~/.config/tree-sitter/config.json,
    // but the CLI still works correctly by discovering the grammar from the working directory.
    workDir.set(generatedDir.map { it.asFile })
    rootDir.set(compositeRoot)

    // Find all lib_* directories (the XDK standard library)
    val xdkLibDirs = compositeRoot.listFiles { f ->
        f.isDirectory && f.name.startsWith("lib_")
    }?.toList() ?: emptyList()
    libDirs.set(xdkLibDirs)

    // Support filtering via -PtestFiles=pattern (comma-separated patterns)
    fileFilter.set(providers.gradleProperty("testFiles"))
    showTiming.set(providers.gradleProperty("showTiming").map { it.toBoolean() })
}

// =============================================================================
// Zig Cross-Compilation
// =============================================================================
// Build native libraries for all platforms from any host using Zig.

/**
 * Task to cross-compile native library using Zig.
 */
abstract class ZigCrossCompileTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val zigPath: Property<String>

    @get:Input
    abstract val targetTriple: Property<String> // e.g., "aarch64-macos"

    @get:Input
    abstract val targetPlatform: Property<String> // e.g., "darwin-arm64"

    @get:InputFile
    abstract val parserC: RegularFileProperty

    @get:InputFile
    abstract val scannerC: RegularFileProperty

    @get:InputDirectory
    abstract val includeDir: DirectoryProperty

    @get:OutputFile
    abstract val outputLib: RegularFileProperty

    @TaskAction
    fun compile() {
        val outputFile = outputLib.get().asFile
        outputFile.parentFile.mkdirs()

        val platform = targetPlatform.get()
        val triple = targetTriple.get()
        logger.lifecycle("[zig] Starting cross-compile for $platform ($triple)...")

        val duration = measureTime {
            execOps.exec {
                executable(zigPath.get())
                args(
                    "cc",
                    "-shared",
                    "-fPIC",
                    "-target", triple,
                    "-I", includeDir.get().asFile.absolutePath,
                    parserC.get().asFile.absolutePath,
                    scannerC.get().asFile.absolutePath,
                    "-o", outputFile.absolutePath
                )
            }
        }

        logger.lifecycle("[zig] Finished $platform in $duration -> ${outputFile.name}")
    }
}

// Cross-compilation target mapping
val crossCompileTargets = mapOf(
    "darwin-arm64" to "aarch64-macos",
    "darwin-x64" to "x86_64-macos",
    "linux-x64" to "x86_64-linux-gnu",
    "linux-arm64" to "aarch64-linux-gnu",
    "windows-x64" to "x86_64-windows-gnu"
)

// Library extension for each platform
fun libExtForPlatform(platform: String): String = when {
    platform.startsWith("darwin") -> "dylib"
    platform.startsWith("windows") -> "dll"
    else -> "so"
}

// Register cross-compile tasks for XTC grammar library
crossCompileTargets.forEach { (platform, zigTarget) ->
    val ext = libExtForPlatform(platform)
    val taskName = "buildNativeLibrary_${platform.replace("-", "_")}"

    tasks.register<ZigCrossCompileTask>(taskName) {
        group = "tree-sitter"
        description = "Cross-compile XTC grammar library for $platform using Zig"
        dependsOn(extractZig, validateTreeSitterGrammar)
        enabled = zigPlatformSupported

        zigPath.set(zigExe)
        targetTriple.set(zigTarget)
        targetPlatform.set(platform)
        parserC.set(generatedDir.map { it.file("src/parser.c") })
        scannerC.set(generatedDir.map { it.file("src/scanner.c") })
        includeDir.set(generatedDir.map { it.dir("src") })
        outputLib.set(layout.buildDirectory.file("native-cross/$platform/libtree-sitter-xtc.$ext"))
    }
}

// =============================================================================
// Tree-sitter Runtime Library Cross-Compilation
// =============================================================================
// Build libtree-sitter (the core runtime) for all platforms.
// jtreesitter requires this at runtime alongside our grammar library.

/**
 * Task to cross-compile the tree-sitter runtime library using Zig.
 */
abstract class ZigBuildRuntimeTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val zigPath: Property<String>

    @get:Input
    abstract val targetTriple: Property<String>

    @get:Input
    abstract val targetPlatform: Property<String>

    @get:InputDirectory
    abstract val libSrcDir: DirectoryProperty

    @get:OutputFile
    abstract val outputLib: RegularFileProperty

    @TaskAction
    fun compile() {
        val outputFile = outputLib.get().asFile
        outputFile.parentFile.mkdirs()

        val platform = targetPlatform.get()
        val triple = targetTriple.get()
        val libSrc = libSrcDir.get().asFile

        logger.lifecycle("[zig] Building tree-sitter runtime for $platform ($triple)...")

        val libC = File(libSrc, "src/lib.c")
        if (!libC.exists()) {
            throw GradleException("tree-sitter lib.c not found at: ${libC.absolutePath}")
        }

        val duration = measureTime {
            execOps.exec {
                executable(zigPath.get())
                args(
                    "cc",
                    "-shared",
                    "-fPIC",
                    "-target", triple,
                    "-I", File(libSrc, "include").absolutePath,
                    "-I", File(libSrc, "src").absolutePath,
                    libC.absolutePath,
                    "-o", outputFile.absolutePath
                )
            }
        }

        logger.lifecycle("[zig] Finished tree-sitter runtime $platform in $duration -> ${outputFile.name}")
    }
}

// Register cross-compile tasks for tree-sitter runtime library
crossCompileTargets.forEach { (platform, zigTarget) ->
    val ext = libExtForPlatform(platform)
    val taskName = "buildTreeSitterRuntime_${platform.replace("-", "_")}"

    tasks.register<ZigBuildRuntimeTask>(taskName) {
        group = "tree-sitter"
        description = "Cross-compile tree-sitter runtime for $platform using Zig"
        dependsOn(extractZig, extractTreeSitterSource)
        enabled = zigPlatformSupported

        zigPath.set(zigExe)
        targetTriple.set(zigTarget)
        targetPlatform.set(platform)
        libSrcDir.set(treeSitterLibSrc)
        outputLib.set(layout.buildDirectory.file("native-cross/$platform/libtree-sitter.$ext"))
    }
}

/**
 * Build all native libraries (grammar + runtime) for all platforms using Zig.
 */
val buildAllNativeLibraries by tasks.registering {
    group = "tree-sitter"
    description = "Build grammar and runtime libraries for all platforms using Zig"
    enabled = zigPlatformSupported

    crossCompileTargets.keys.forEach { platform ->
        val safePlatform = platform.replace("-", "_")
        dependsOn("buildNativeLibrary_$safePlatform")
        dependsOn("buildTreeSitterRuntime_$safePlatform")
    }
}

/**
 * Populate the persistent cache with native libraries for all platforms.
 * This is useful for CI to warm the cache, or for developers building all platforms locally.
 * Libraries are stored in ~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/
 */
val populateNativeLibraryCache by tasks.registering {
    group = "tree-sitter"
    description = "Build and cache native libraries for all platforms"
    dependsOn(buildAllNativeLibraries)

    // Capture providers and values at configuration time (not script references)
    val crossBuildDir = layout.buildDirectory.dir("native-cross")
    val grammarFileValue = grammarJsFile.map { it.asFile }
    val scannerFileValue = scannerCFile.map { it.asFile }
    val cliVersionValue = treeSitterCliVersion
    val zigVersionValue = libs.versions.zig.get()
    val cacheDirValue = nativeLibCacheDir
    // Capture the map with pre-computed extensions to avoid script function reference
    val platformsWithExtensions = crossCompileTargets.keys.associateWith { libExtForPlatform(it) }

    inputs.dir(crossBuildDir)

    doLastTask {
        platformsWithExtensions.forEach { (platform, ext) ->
            // Compute hash (same algorithm as BuildNativeLibraryOnDemandTask)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(cliVersionValue.toByteArray())
            digest.update(zigVersionValue.toByteArray())
            digest.update(platform.toByteArray())
            listOf(grammarFileValue.get(), scannerFileValue.get()).forEach { file ->
                if (file.exists()) digest.update(file.readBytes())
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }

            val cacheDir = File(cacheDirValue, "$hash/$platform")
            cacheDir.mkdirs()

            // Copy XTC grammar library to cache
            val srcGrammarLib = crossBuildDir.get().dir(platform).file("libtree-sitter-xtc.$ext").asFile
            val destGrammarLib = File(cacheDir, "libtree-sitter-xtc.$ext")
            if (srcGrammarLib.exists()) {
                srcGrammarLib.copyTo(destGrammarLib, overwrite = true)
                logger.lifecycle("Cached grammar lib: $platform (hash: ${hash.take(12)}...)")
            } else {
                logger.warn("Grammar lib not found: ${srcGrammarLib.absolutePath}")
            }

            // Copy tree-sitter runtime library to cache
            val srcRuntimeLib = crossBuildDir.get().dir(platform).file("libtree-sitter.$ext").asFile
            val destRuntimeLib = File(cacheDir, "libtree-sitter.$ext")
            if (srcRuntimeLib.exists()) {
                srcRuntimeLib.copyTo(destRuntimeLib, overwrite = true)
                logger.lifecycle("Cached runtime lib: $platform (hash: ${hash.take(12)}...)")
            } else {
                logger.warn("Runtime lib not found: ${srcRuntimeLib.absolutePath}")
            }
        }

        logger.lifecycle("")
        logger.lifecycle("Native libraries cached in: ${cacheDirValue.absolutePath}")
        logger.lifecycle("Cache will be used automatically on subsequent builds.")
    }
}

// =============================================================================
// On-Demand Native Library Build with Persistent Caching
// =============================================================================
// Native libraries are built on first use and cached in ~/.gradle/caches/tree-sitter-xtc/
// The cache is keyed by a hash of: grammar.js + scanner.c + CLI version + platform
//
// First build:  Download Zig (~45MB) + compile (~20s)
// Subsequent:   Instant (cache hit)
//
// This replaces the old approach of checking in pre-built binaries to source control.

/**
 * Task that ensures native libraries are available, building on-demand if needed.
 * Uses a persistent cache in ~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/
 */
abstract class BuildNativeLibraryOnDemandTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:InputFile
    abstract val grammarFile: RegularFileProperty

    @get:InputFile
    abstract val scannerFile: RegularFileProperty

    @get:InputDirectory
    abstract val parserSrcDir: DirectoryProperty

    @get:Input
    abstract val cliVersion: Property<String>

    @get:Input
    abstract val zigVersion: Property<String>

    @get:Input
    abstract val platform: Property<String>

    @get:Input
    abstract val zigTarget: Property<String>

    @get:Input
    abstract val libExtension: Property<String>

    @get:Input
    abstract val zigExePath: Property<String>

    @get:Input
    abstract val treeSitterLibSrcPath: Property<String>

    @get:Input
    abstract val cacheDir: Property<String>

    @get:OutputFile
    abstract val grammarLibOutput: RegularFileProperty

    @get:OutputFile
    abstract val runtimeLibOutput: RegularFileProperty

    private fun computeHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(cliVersion.get().toByteArray())
        digest.update(zigVersion.get().toByteArray())
        digest.update(platform.get().toByteArray())
        listOf(grammarFile.get().asFile, scannerFile.get().asFile).forEach { file ->
            if (file.exists()) digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @TaskAction
    fun execute() {
        val hash = computeHash()
        val platformDir = platform.get()
        val ext = libExtension.get()
        val cacheRoot = File(cacheDir.get())
        val cachedDir = File(cacheRoot, "$hash/$platformDir")

        val cachedGrammarLib = File(cachedDir, "libtree-sitter-xtc.$ext")
        val cachedRuntimeLib = File(cachedDir, "libtree-sitter.$ext")

        val grammarOutput = grammarLibOutput.get().asFile
        val runtimeOutput = runtimeLibOutput.get().asFile

        // Check cache
        if (cachedGrammarLib.exists() && cachedRuntimeLib.exists()) {
            logger.lifecycle("Using cached native libraries (hash: ${hash.take(12)}...)")
            grammarOutput.parentFile.mkdirs()
            cachedGrammarLib.copyTo(grammarOutput, overwrite = true)
            cachedRuntimeLib.copyTo(runtimeOutput, overwrite = true)
            return
        }

        logger.lifecycle("Building native libraries (hash: ${hash.take(12)}...)")
        logger.lifecycle("  This will take ~20s on first build. Libraries will be cached for future builds.")

        cachedDir.mkdirs()

        // Build grammar library
        val parserC = File(parserSrcDir.get().asFile, "parser.c")
        val scannerC = scannerFile.get().asFile
        val includeDir = parserSrcDir.get().asFile

        logger.lifecycle("  Building libtree-sitter-xtc.$ext for $platformDir...")
        execOps.exec {
            executable(zigExePath.get())
            args(
                "cc", "-shared", "-fPIC",
                "-target", zigTarget.get(),
                "-I", includeDir.absolutePath,
                parserC.absolutePath,
                scannerC.absolutePath,
                "-o", cachedGrammarLib.absolutePath
            )
        }

        // Build runtime library
        val treeSitterLibSrc = File(treeSitterLibSrcPath.get())
        val libC = File(treeSitterLibSrc, "src/lib.c")
        if (!libC.exists()) {
            throw GradleException("tree-sitter lib.c not found at: ${libC.absolutePath}")
        }

        logger.lifecycle("  Building libtree-sitter.$ext for $platformDir...")
        execOps.exec {
            executable(zigExePath.get())
            args(
                "cc", "-shared", "-fPIC",
                "-target", zigTarget.get(),
                "-I", File(treeSitterLibSrc, "include").absolutePath,
                "-I", File(treeSitterLibSrc, "src").absolutePath,
                libC.absolutePath,
                "-o", cachedRuntimeLib.absolutePath
            )
        }

        // Copy to output locations
        grammarOutput.parentFile.mkdirs()
        cachedGrammarLib.copyTo(grammarOutput, overwrite = true)
        cachedRuntimeLib.copyTo(runtimeOutput, overwrite = true)

        logger.lifecycle("Native libraries built and cached successfully.")
    }
}

// Map platform directory name to Zig target triple
val currentZigTarget: String = crossCompileTargets[nativePlatformDir] ?: "unsupported"

// =============================================================================
// Multi-Platform Native Library Build with Caching
// =============================================================================
// Build native libraries for ALL platforms using Zig cross-compilation.
// This ensures the fatJar works on any platform without needing native builds.

/**
 * Task that builds native libraries for ALL platforms using Zig cross-compilation.
 * Libraries are cached in ~/.gradle/caches/tree-sitter-xtc/<hash>/<platform>/
 */
abstract class BuildAllNativeLibrariesOnDemandTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:InputFile
    abstract val grammarFile: RegularFileProperty

    @get:InputFile
    abstract val scannerFile: RegularFileProperty

    @get:InputDirectory
    abstract val parserSrcDir: DirectoryProperty

    @get:Input
    abstract val cliVersion: Property<String>

    @get:Input
    abstract val zigVersion: Property<String>

    @get:Input
    abstract val zigExePath: Property<String>

    @get:Input
    abstract val treeSitterLibSrcPath: Property<String>

    @get:Input
    abstract val cacheDir: Property<String>

    @get:Input
    abstract val platforms: MapProperty<String, String> // platform -> zigTarget

    @get:Input
    abstract val platformExtensions: MapProperty<String, String> // platform -> extension

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun computeHash(platform: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(cliVersion.get().toByteArray())
        digest.update(zigVersion.get().toByteArray())
        digest.update(platform.toByteArray())
        listOf(grammarFile.get().asFile, scannerFile.get().asFile).forEach { file ->
            if (file.exists()) digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @TaskAction
    fun execute() {
        val platformMap = platforms.get()
        val extMap = platformExtensions.get()
        val cacheRoot = File(cacheDir.get())
        val outDir = outputDir.get().asFile
        val zigPath = zigExePath.get()
        val treeSitterLibSrc = File(treeSitterLibSrcPath.get())
        val parserC = File(parserSrcDir.get().asFile, "parser.c")
        val scannerC = scannerFile.get().asFile
        val includeDir = parserSrcDir.get().asFile

        logger.lifecycle("Building native libraries for ${platformMap.size} platforms...")

        platformMap.forEach { (platform, zigTarget) ->
            val ext = extMap[platform] ?: error("No extension for platform: $platform")
            val hash = computeHash(platform)
            val cachedDir = File(cacheRoot, "$hash/$platform")
            val cachedGrammarLib = File(cachedDir, "libtree-sitter-xtc.$ext")
            val cachedRuntimeLib = File(cachedDir, "libtree-sitter.$ext")

            val platformOutDir = File(outDir, platform)
            platformOutDir.mkdirs()
            val grammarOutput = File(platformOutDir, "libtree-sitter-xtc.$ext")
            val runtimeOutput = File(platformOutDir, "libtree-sitter.$ext")

            // Check cache
            if (cachedGrammarLib.exists() && cachedRuntimeLib.exists()) {
                logger.lifecycle("  $platform: Using cached (hash: ${hash.take(8)}...)")
                cachedGrammarLib.copyTo(grammarOutput, overwrite = true)
                cachedRuntimeLib.copyTo(runtimeOutput, overwrite = true)
                return@forEach
            }

            logger.lifecycle("  $platform: Building with Zig ($zigTarget)...")
            cachedDir.mkdirs()

            val duration = measureTime {
                // Build grammar library
                execOps.exec {
                    executable(zigPath)
                    args(
                        "cc", "-shared", "-fPIC",
                        "-target", zigTarget,
                        "-I", includeDir.absolutePath,
                        parserC.absolutePath,
                        scannerC.absolutePath,
                        "-o", cachedGrammarLib.absolutePath
                    )
                }

                // Build runtime library
                val libC = File(treeSitterLibSrc, "src/lib.c")
                execOps.exec {
                    executable(zigPath)
                    args(
                        "cc", "-shared", "-fPIC",
                        "-target", zigTarget,
                        "-I", File(treeSitterLibSrc, "include").absolutePath,
                        "-I", File(treeSitterLibSrc, "src").absolutePath,
                        libC.absolutePath,
                        "-o", cachedRuntimeLib.absolutePath
                    )
                }
            }
            logger.lifecycle("  $platform: Built in $duration")

            // Copy to output
            cachedGrammarLib.copyTo(grammarOutput, overwrite = true)
            cachedRuntimeLib.copyTo(runtimeOutput, overwrite = true)
        }

        logger.lifecycle("All native libraries ready.")
    }
}

// Output directory for all platform libraries
val nativeLibOutputDir: Provider<Directory> = layout.buildDirectory.dir("native-out")

/**
 * Build native libraries for ALL platforms on-demand with caching.
 * This is the task that consumers should depend on.
 */
val buildAllNativeLibrariesOnDemand by tasks.registering(BuildAllNativeLibrariesOnDemandTask::class) {
    group = "tree-sitter"
    description = "Build native libraries for all platforms (cached in ~/.gradle/caches/)"
    dependsOn(copyGrammarFiles, extractZig, extractTreeSitterSource, validateTreeSitterGrammar)
    enabled = zigPlatformSupported

    grammarFile.set(grammarJsFile)
    scannerFile.set(scannerCFile)
    parserSrcDir.set(generatedDir.map { it.dir("src") })
    cliVersion.set(treeSitterCliVersion)
    this.zigVersion.set(libs.versions.zig)
    zigExePath.set(zigExe)
    treeSitterLibSrcPath.set(treeSitterLibSrc.map { it.asFile.absolutePath })
    cacheDir.set(nativeLibCacheDir.absolutePath)
    platforms.set(crossCompileTargets)
    platformExtensions.set(crossCompileTargets.keys.associateWith { libExtForPlatform(it) })
    outputDir.set(nativeLibOutputDir)
}

// =============================================================================
// Consumable Configuration for LSP Server
// =============================================================================

/**
 * Expose native libraries for ALL platforms for consumption by other projects.
 * Libraries are built on-demand using Zig cross-compilation and cached.
 */
val nativeLibraryElements by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-library"))
    }
    outgoing {
        // Expose the entire native-out directory containing all platforms
        artifact(nativeLibOutputDir) {
            builtBy(buildAllNativeLibrariesOnDemand)
        }
    }
}
