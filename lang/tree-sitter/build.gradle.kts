import de.undercouch.gradle.tasks.download.Download
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.zip.GZIPInputStream

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.download)
    base
}

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
val dslProject = project(":dsl")

// =============================================================================
// Platform Detection
// =============================================================================

val osName: String = System.getProperty("os.name").lowercase()
val osArch: String = System.getProperty("os.arch")

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

// Pre-built library location (committed to source control)
val prebuiltNativeDir: Directory = layout.projectDirectory.dir("src/main/resources/native/$nativePlatformDir")
val prebuiltLibrary: RegularFile = prebuiltNativeDir.file("libtree-sitter-xtc.$nativeLibExt")
val prebuiltHashFile: RegularFile = prebuiltNativeDir.file("libtree-sitter-xtc.inputs.sha256")
val prebuiltVersionFile: RegularFile = prebuiltNativeDir.file("libtree-sitter-xtc.version")

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
// Zig Compiler Download (for Cross-Compilation)
// =============================================================================
// Zig enables building native libraries for ALL platforms from ANY host machine.
// Download once, build everywhere - no platform-specific toolchains needed.

val zigVersion = "0.13.0"
val zigDir: Provider<Directory> = layout.buildDirectory.dir("zig")

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
 */
val downloadZig by tasks.registering(Download::class) {
    group = "zig"
    description = "Download Zig compiler for cross-compilation"
    enabled = zigPlatformSupported

    src("https://ziglang.org/download/$zigVersion/zig-$zigPlatform-$zigVersion.$zigArchiveExt")
    dest(zigDir.map { it.file("zig-$zigPlatform-$zigVersion.$zigArchiveExt") })
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
    description = "Extract Zig compiler from archive"
    dependsOn(downloadZig)
    enabled = zigPlatformSupported

    archiveFile.set(layout.file(downloadZig.map { it.dest }))
    outputDir.set(zigDir)
    archiveExt.set(zigArchiveExt)
}

val zigExe: Provider<String> = zigDir.map {
    it.dir("zig-$zigPlatform-$zigVersion").file(zigExeName).asFile.absolutePath
}

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

// =============================================================================
// Native Build Task (Configuration Cache Compatible)
// =============================================================================

/**
 * Task to build the native tree-sitter library.
 * Uses injected ExecOperations for configuration cache compatibility.
 */
abstract class BuildNativeLibraryTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val treeSitterCli: Property<String>

    @get:Input
    abstract val workDir: Property<File>

    @get:Input
    abstract val outputPath: Property<String>

    @get:InputFile
    abstract val grammarFile: RegularFileProperty

    @get:InputFile
    abstract val scannerFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun build() {
        val osName = System.getProperty("os.name").lowercase()
        val cmd = if (osName.contains("windows")) "where" else "which"
        val compiler = listOf("cc", "clang", "gcc").firstOrNull { c ->
            try { ProcessBuilder(cmd, c).start().waitFor() == 0 } catch (_: Exception) { false }
        } ?: throw GradleException("""
            No C compiler found. Install one of:
            - macOS: xcode-select --install
            - Linux: apt install build-essential
            - Windows: Install Visual Studio Build Tools or MinGW

            Or use pre-built binaries from CI.
        """.trimIndent())

        logger.lifecycle("Building native library with: $compiler")

        execOps.exec {
            workingDir(workDir.get())
            environment("CC", compiler)
            executable(treeSitterCli.get())
            args("build", "--output", outputPath.get())
        }

        logger.lifecycle("Native library built: ${outputFile.get().asFile.absolutePath}")
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
    dependsOn(copyGrammarFiles, extractTreeSitterCli)
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
// Native Library Building
// =============================================================================

/**
 * Build native tree-sitter library from source.
 */
val buildTreeSitterLibrary by tasks.registering(BuildNativeLibraryTask::class) {
    group = "tree-sitter"
    description = "Build native tree-sitter library"
    dependsOn(validateTreeSitterGrammar)
    enabled = treeSitterPlatformSupported

    treeSitterCli.set(treeSitterCliExe)
    workDir.set(generatedDir.map { it.asFile })
    grammarFile.set(grammarJsFile)
    scannerFile.set(scannerCFile)
    outputPath.set(nativeLibFile.map { it.asFile.absolutePath })
    outputFile.set(nativeLibFile)
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
        val ext = when {
            targetTriple.get().contains("macos") -> "dylib"
            targetTriple.get().contains("windows") -> "dll"
            else -> "so"
        }

        val outputFile = outputLib.get().asFile
        outputFile.parentFile.mkdirs()

        logger.lifecycle("Cross-compiling for ${targetPlatform.get()} (${targetTriple.get()})...")

        execOps.exec {
            executable(zigPath.get())
            args(
                "cc",
                "-shared",
                "-fPIC",
                "-target", targetTriple.get(),
                "-I", includeDir.get().asFile.absolutePath,
                parserC.get().asFile.absolutePath,
                scannerC.get().asFile.absolutePath,
                "-o", outputFile.absolutePath
            )
        }

        logger.lifecycle("Built: ${outputFile.absolutePath}")
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

// Register cross-compile tasks for all platforms
crossCompileTargets.forEach { (platform, zigTarget) ->
    val ext = libExtForPlatform(platform)
    val taskName = "buildNativeLibrary_${platform.replace("-", "_")}"

    tasks.register<ZigCrossCompileTask>(taskName) {
        group = "tree-sitter"
        description = "Cross-compile native library for $platform using Zig"
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

/**
 * Build native libraries for all platforms using Zig.
 */
val buildAllNativeLibraries by tasks.registering {
    group = "tree-sitter"
    description = "Build native libraries for all platforms using Zig"
    enabled = zigPlatformSupported

    crossCompileTargets.keys.forEach { platform ->
        dependsOn("buildNativeLibrary_${platform.replace("-", "_")}")
    }
}

/**
 * Copy all cross-compiled libraries to resources directories for committing to source control.
 */
val copyAllNativeLibrariesToResources by tasks.registering {
    group = "tree-sitter"
    description = "Copy all cross-compiled native libraries to resources"
    dependsOn(buildAllNativeLibraries)

    // Capture values at configuration time
    val crossBuildDir = layout.buildDirectory.dir("native-cross")
    val resourcesNativeDir = layout.projectDirectory.dir("src/main/resources/native")
    val grammarFileValue = grammarJsFile.map { it.asFile }
    val scannerFileValue = scannerCFile.map { it.asFile }

    inputs.dir(crossBuildDir)
    outputs.dir(resourcesNativeDir)

    doLastTask {
        crossCompileTargets.keys.forEach { platform ->
            val ext = libExtForPlatform(platform)
            val srcLib = crossBuildDir.get().dir(platform).file("libtree-sitter-xtc.$ext").asFile
            val destDir = resourcesNativeDir.dir(platform).asFile
            val destLib = File(destDir, "libtree-sitter-xtc.$ext")
            val hashFile = File(destDir, "libtree-sitter-xtc.inputs.sha256")
            val versionFile = File(destDir, "libtree-sitter-xtc.version")

            if (srcLib.exists()) {
                destDir.mkdirs()
                srcLib.copyTo(destLib, overwrite = true)

                // Compute and write hash
                val digest = MessageDigest.getInstance("SHA-256")
                listOf(grammarFileValue.get(), scannerFileValue.get()).forEach { file ->
                    if (file.exists()) digest.update(file.readBytes())
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                hashFile.writeText(hash)

                // Write version info
                val gitCommit = try {
                    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                        .redirectErrorStream(true).start()
                    val result = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0) result else "unknown"
                } catch (_: Exception) { "unknown" }

                Properties().apply {
                    setProperty("input.hash", hash)
                    setProperty("git.commit", gitCommit)
                    setProperty("built.at", Instant.now().toString())
                    setProperty("platform", platform)
                    setProperty("cross.compiled", "true")
                    versionFile.outputStream().use { store(it, "Tree-sitter native library build info") }
                }

                logger.lifecycle("Copied: $platform -> ${destLib.absolutePath}")
            } else {
                logger.warn("Not found: ${srcLib.absolutePath}")
            }
        }

        logger.lifecycle("Commit the updated libraries to source control!")
    }
}

// =============================================================================
// Staleness Detection
// =============================================================================

/**
 * Check if pre-built native library is stale (inputs have changed).
 * Computes SHA-256 of grammar.js + scanner.c and compares to stored hash.
 */
val checkNativeLibraryStaleness by tasks.registering {
    group = "tree-sitter"
    description = "Check if pre-built native library needs rebuilding"
    dependsOn(copyGrammarFiles)

    inputs.file(grammarJsFile)
    inputs.file(scannerCFile)

    // Capture values at configuration time
    val grammarFileValue = grammarJsFile.map { it.asFile }
    val scannerFileValue = scannerCFile.map { it.asFile }
    val prebuiltLibValue = prebuiltLibrary.asFile
    val hashFileValue = prebuiltHashFile.asFile

    doLastTask {
        // Inline hash computation (can't use top-level functions for config cache compatibility)
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(grammarFileValue.get(), scannerFileValue.get()).forEach { file ->
            if (file.exists()) digest.update(file.readBytes())
        }
        val currentHash = digest.digest().joinToString("") { "%02x".format(it) }

        when {
            !prebuiltLibValue.exists() -> {
                logger.warn("No pre-built native library found at: ${prebuiltLibValue.absolutePath}")
                logger.warn("   Run: ./gradlew :lang:tree-sitter:buildTreeSitterLibrary :lang:tree-sitter:copyNativeLibraryToResources")
            }
            !hashFileValue.exists() -> {
                logger.warn("Pre-built library exists but no hash file found.")
                logger.warn("   Cannot verify if library is up-to-date. Consider rebuilding.")
            }
            else -> {
                val storedHash = hashFileValue.readText().trim()
                if (storedHash != currentHash) {
                    logger.warn("Pre-built native library is STALE!")
                    logger.warn("   Input hash changed: $storedHash -> $currentHash")
                    logger.warn("   Run: ./gradlew :lang:tree-sitter:buildTreeSitterLibrary :lang:tree-sitter:copyNativeLibraryToResources")
                } else {
                    logger.lifecycle("Pre-built native library is up-to-date (hash: ${currentHash.take(12)}...)")
                }
            }
        }
    }
}

/**
 * Copy built native library to resources directory for committing to source control.
 * Also generates hash and version files to track when inputs change.
 */
val copyNativeLibraryToResources by tasks.registering(Copy::class) {
    group = "tree-sitter"
    description = "Copy native library to resources for source control"
    dependsOn(buildTreeSitterLibrary)

    from(nativeLibFile)
    into(prebuiltNativeDir)

    // Capture values at configuration time for doLast
    val grammarFileValue = grammarJsFile.map { it.asFile }.get()
    val scannerFileValue = scannerCFile.map { it.asFile }.get()
    val hashFileValue = prebuiltHashFile.asFile
    val versionFileValue = prebuiltVersionFile.asFile
    val destDirValue = prebuiltNativeDir.asFile
    val platformValue = nativePlatformDir

    doLastTask {
        // Inline hash computation (can't use top-level functions for config cache compatibility)
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(grammarFileValue, scannerFileValue).forEach { file ->
            if (file.exists()) digest.update(file.readBytes())
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }

        // Write hash file
        hashFileValue.parentFile.mkdirs()
        hashFileValue.writeText(hash)

        // Inline git commit (can't use top-level functions for config cache compatibility)
        val gitCommit = try {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0) result else "unknown"
        } catch (_: Exception) { "unknown" }

        // Write version file
        Properties().apply {
            setProperty("input.hash", hash)
            setProperty("git.commit", gitCommit)
            setProperty("built.at", Instant.now().toString())
            setProperty("platform", platformValue)
            versionFileValue.outputStream().use { store(it, "Tree-sitter native library build info") }
        }

        logger.lifecycle("Copied library to: ${destDirValue.absolutePath}")
        logger.lifecycle("Tree-sitter native library: hash=${hash.take(12)}... commit=$gitCommit platform=$platformValue")
        logger.lifecycle("  Commit these files to source control!")
    }
}

// =============================================================================
// Ensure Native Library is Up-to-Date
// =============================================================================

/**
 * Task that ensures the pre-built native library is up-to-date.
 * If stale/missing and a C compiler is available, rebuilds automatically.
 * If stale/missing and no C compiler, FAILS the build.
 *
 * This is the task that consumers should depend on.
 */
abstract class EnsureNativeLibraryTask : DefaultTask() {

    @get:InputFile
    abstract val grammarFile: RegularFileProperty

    @get:InputFile
    abstract val scannerFile: RegularFileProperty

    @get:Input
    abstract val prebuiltLibPath: Property<String>

    @get:Input
    abstract val hashFilePath: Property<String>

    @get:Input
    abstract val versionFilePath: Property<String>

    private fun computeHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(grammarFile.get().asFile, scannerFile.get().asFile).forEach { file ->
            if (file.exists()) {
                digest.update(file.readBytes())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun logVersionInfo(versionFile: File, currentHash: String) {
        if (versionFile.exists()) {
            val props = Properties().apply { load(versionFile.inputStream()) }
            val builtAt = props.getProperty("built.at", "unknown")
            val gitCommit = props.getProperty("git.commit", "unknown")
            val builtPlatform = props.getProperty("platform", "unknown")
            logger.lifecycle("Tree-sitter native library: hash=${currentHash.take(12)}... commit=$gitCommit platform=$builtPlatform built=$builtAt")
        } else {
            logger.lifecycle("Tree-sitter native library: hash=${currentHash.take(12)}... (no version info)")
        }
    }

    @TaskAction
    fun execute() {
        val prebuiltLib = File(prebuiltLibPath.get())
        val hashFile = File(hashFilePath.get())
        val versionFile = File(versionFilePath.get())
        val currentHash = computeHash()

        // Check if up-to-date
        val isUpToDate = prebuiltLib.exists() && hashFile.exists() &&
            hashFile.readText().trim() == currentHash

        if (isUpToDate) {
            logVersionInfo(versionFile, currentHash)
            return
        }

        // Library is stale or missing - fail with helpful message
        // We don't auto-rebuild because that would require downloading Zig
        val storedHash = if (hashFile.exists()) hashFile.readText().trim() else "none"
        val message = buildString {
            appendLine("Pre-built tree-sitter native library is STALE or MISSING!")
            appendLine()
            if (!prebuiltLib.exists()) {
                appendLine("  Library not found: ${prebuiltLib.absolutePath}")
            } else {
                appendLine("  Input hash changed: $storedHash -> $currentHash")
            }
            appendLine()
            appendLine("To rebuild all platform libraries, run:")
            appendLine("  ./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources")
            appendLine()
            appendLine("Then commit the updated libraries to source control.")
        }
        throw GradleException(message)
    }
}

// Map platform directory name to Zig target triple
val currentZigTarget: String = crossCompileTargets[nativePlatformDir] ?: "unsupported"

val ensureNativeLibraryUpToDate by tasks.registering(EnsureNativeLibraryTask::class) {
    group = "tree-sitter"
    description = "Ensure native library is up-to-date, failing if stale"
    // Only depend on copyGrammarFiles to get files for hashing
    // Do NOT depend on extractZig - we don't want to download Zig unless we actually need to rebuild
    dependsOn(copyGrammarFiles)
    enabled = treeSitterPlatformSupported

    grammarFile.set(grammarJsFile)
    scannerFile.set(scannerCFile)
    prebuiltLibPath.set(prebuiltLibrary.asFile.absolutePath)
    hashFilePath.set(prebuiltHashFile.asFile.absolutePath)
    versionFilePath.set(prebuiltVersionFile.asFile.absolutePath)
}

// =============================================================================
// Consumable Configuration for LSP Server
// =============================================================================

/**
 * Expose pre-built native library for consumption by other projects.
 * Depends on ensureNativeLibraryUpToDate to guarantee freshness.
 */
val nativeLibraryElements by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-library"))
    }
    outgoing {
        artifact(prebuiltLibrary) {
            builtBy(ensureNativeLibraryUpToDate)
        }
    }
}
