/*
 * Build file for the javatools_launcher project.
 *
 * This project builds native launcher executables for supported platforms.
 * Supports both current-platform-only builds and cross-compilation for all platforms.
 */

plugins {
    base
}

val xdkDistribution = XdkDistribution(project)
val sourceDir = file("src/main/c")
val nativeBuildDir = layout.buildDirectory.dir("native").get().asFile

// Cross-compilation configuration
data class CrossCompileConfig(
    val os: String,
    val arch: String,
    val compiler: String,
    val compilerArgs: List<String> = emptyList(),
    val linkerArgs: List<String> = emptyList(),
    val available: Boolean = true
) {
    val launcherFileName = XdkDistribution(project).launcherFileName(os, arch)
    val platformDefine = "-D${os.uppercase()}"
}

// Helper function to find Windows cross-compiler
fun findWindowsCompiler(): String? {
    val possiblePaths = listOf(
        "/usr/bin/x86_64-w64-mingw32-gcc",
        "/usr/local/bin/x86_64-w64-mingw32-gcc",
        "/opt/homebrew/bin/x86_64-w64-mingw32-gcc", // Homebrew ARM64 Mac
        "/usr/local/Homebrew/bin/x86_64-w64-mingw32-gcc", // Homebrew Intel Mac
        "C:/msys64/mingw64/bin/gcc.exe", // MSYS2 on Windows
        "C:/MinGW/bin/gcc.exe" // Plain MinGW on Windows
    )
    return possiblePaths.find { File(it).exists() }
}

// Helper function to determine the best compiler and flags for each platform
fun createCrossCompileTargets(): List<CrossCompileConfig> {
    val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
    val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    
    return listOf(
        // Linux targets
        if (isLinux) {
            // Native Linux build
            CrossCompileConfig("linux", "amd64", "gcc", listOf("-std=c99", "-Wall", "-O2"), listOf("-static-libgcc"))
        } else if (isMacOS) {
            // Cross-compile Linux on macOS (using clang, not gcc which doesn't exist)
            CrossCompileConfig("linux", "amd64", "clang", listOf("-std=c99", "-Wall", "-O2"), emptyList(),
                available = false) // Disable for now - too complex
        } else {
            CrossCompileConfig("linux", "amd64", "gcc", listOf("-std=c99", "-Wall", "-O2"), listOf("-static-libgcc"),
                available = File("/usr/bin/gcc").exists())
        },
        
        CrossCompileConfig("linux", "arm64", "aarch64-linux-gnu-gcc", listOf("-std=c99", "-Wall", "-O2"), listOf("-static-libgcc"),
            available = File("/usr/bin/aarch64-linux-gnu-gcc").exists() || File("/usr/local/bin/aarch64-linux-gnu-gcc").exists()),
        
        // macOS targets (only available on macOS)
        CrossCompileConfig("macos", "amd64", "clang", 
            if (isMacOS) listOf("-std=c99", "-Wall", "-O2", "-target", "x86_64-apple-macos11") else listOf("-std=c99", "-Wall", "-O2"), 
            listOf("-framework", "Foundation"),
            available = isMacOS),
        CrossCompileConfig("macos", "arm64", "clang", 
            if (isMacOS) listOf("-std=c99", "-Wall", "-O2", "-target", "arm64-apple-macos11") else listOf("-std=c99", "-Wall", "-O2"), 
            listOf("-framework", "Foundation"),
            available = isMacOS),
        
        // Windows targets - check multiple possible MinGW locations
        CrossCompileConfig("windows", "amd64", 
            findWindowsCompiler() ?: "x86_64-w64-mingw32-gcc",
            listOf("-std=c99", "-Wall", "-O2", "-municode"), 
            listOf("-static-libgcc", "-static-libstdc++", "-municode"),
            available = findWindowsCompiler() != null)
    )
}

// Define all supported cross-compilation targets
val crossCompileTargets = createCrossCompileTargets()

// Helper function to get platform-specific source files
fun getPlatformSources(targetOs: String): Array<String> {
    val commonSources = fileTree(sourceDir) { 
        include("launcher.c")
    }.files.map { it.absolutePath }
    
    val platformSources = fileTree(sourceDir) {
        include("os_${targetOs}.c")
        if (targetOs != "windows") {
            include("os_unux.c")  // Unix-like systems (macOS and Linux)
        }
    }.files.map { it.absolutePath }
    
    return (commonSources + platformSources).toTypedArray()
}

// Task to compile native launcher for current platform
val compileNativeLauncher by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile native launcher for current platform (${XdkDistribution.currentOsName}_${XdkDistribution.currentArch})"
    
    val currentOs = XdkDistribution.currentOsName
    val currentArch = XdkDistribution.currentArch
    val outputFile = File(nativeBuildDir, xdkDistribution.launcherFileName(currentOs, currentArch))
    
    outputs.file(outputFile)
    inputs.files(fileTree(sourceDir) { include("*.c", "*.h") })
    
    doFirst {
        nativeBuildDir.mkdirs()
    }
    
    // Find the cross-compile config for current platform
    val config = crossCompileTargets.find { it.os == currentOs && it.arch == currentArch }
        ?: throw GradleException("No cross-compile configuration found for $currentOs $currentArch")
    
    if (!config.available) {
        throw GradleException("Cross-compiler not available for ${config.os} ${config.arch}: ${config.compiler}")
    }
    
    val allSources = getPlatformSources(currentOs)
    val compilerCommand = listOf(config.compiler) + config.compilerArgs + config.platformDefine + 
                         listOf("-o", outputFile.absolutePath) + config.linkerArgs + allSources
    
    commandLine(compilerCommand)
}

// Create individual cross-compilation tasks for each target
val crossCompileTasks = crossCompileTargets.map { config ->
    val taskName = "compile${config.os.replaceFirstChar { it.uppercase() }}${config.arch.replaceFirstChar { it.uppercase() }}"
    
    taskName to tasks.register(taskName, Exec::class) {
        group = "cross-compile"
        description = "Cross-compile launcher for ${config.os} ${config.arch}"
        
        val outputFile = File(nativeBuildDir, config.launcherFileName)
        outputs.file(outputFile)
        inputs.files(fileTree(sourceDir) { include("*.c", "*.h") })
        
        onlyIf { 
            if (!config.available) {
                logger.warn("Cross-compiler not available for ${config.os} ${config.arch}: ${config.compiler}")
                false
            } else {
                true
            }
        }
        
        doFirst {
            nativeBuildDir.mkdirs()
            logger.lifecycle("Cross-compiling launcher for ${config.os} ${config.arch} using ${config.compiler}")
        }
        
        val allSources = getPlatformSources(config.os)
        val compilerCommand = listOf(config.compiler) + config.compilerArgs + config.platformDefine + 
                             listOf("-o", outputFile.absolutePath) + config.linkerArgs + allSources
        
        commandLine(compilerCommand)
    }
}.toMap()

// Task to cross-compile for all available platforms
val crossCompileAll by tasks.registering {
    group = "cross-compile"
    description = "Cross-compile launchers for all available platforms"
    
    val availableTasks = crossCompileTasks.filter { (_, task) ->
        crossCompileTargets.find { config -> 
            task.name.lowercase().contains(config.os) && task.name.lowercase().contains(config.arch)
        }?.available == true
    }
    
    dependsOn(availableTasks.values)
    
    doLast {
        val availableTargets = crossCompileTargets.filter { it.available }
        val unavailableTargets = crossCompileTargets.filter { !it.available }
        
        logger.lifecycle("Successfully cross-compiled for ${availableTargets.size} platforms:")
        availableTargets.forEach { config ->
            logger.lifecycle("  ✅ ${config.os} ${config.arch} (${config.compiler})")
        }
        
        if (unavailableTargets.isNotEmpty()) {
            logger.lifecycle("Skipped ${unavailableTargets.size} platforms due to missing cross-compilers:")
            unavailableTargets.forEach { config ->
                logger.lifecycle("  ⚠️ ${config.os} ${config.arch} (${config.compiler} not found)")
            }
            logger.lifecycle("")
            logger.lifecycle("To enable Windows cross-compilation:")
            logger.lifecycle("  macOS: brew install mingw-w64")
            logger.lifecycle("  Linux: sudo apt-get install gcc-mingw-w64-x86-64")
            logger.lifecycle("  Windows: Install MSYS2 or MinGW-w64")
        }
    }
}

// Task to copy the compiled executable to the final location (current platform only)
val copyExecutables by tasks.registering(Copy::class) {
    group = "build"
    description = "Copy compiled executable to build/bin"
    
    dependsOn(compileNativeLauncher)
    
    from(nativeBuildDir)
    include("*_launcher_*")
    into(layout.buildDirectory.dir("bin"))
    
    doLast {
        val currentOs = XdkDistribution.currentOsName
        val currentArch = XdkDistribution.currentArch
        val launcherFileName = xdkDistribution.launcherFileName(currentOs, currentArch)
        
        logger.lifecycle("Built native launcher for current platform: $launcherFileName")
    }
}

// Task to copy all cross-compiled executables
val copyAllExecutables by tasks.registering(Copy::class) {
    group = "cross-compile"
    description = "Copy all cross-compiled executables to build/bin"
    
    dependsOn(crossCompileAll)
    
    from(nativeBuildDir)
    include("*_launcher_*")
    into(layout.buildDirectory.dir("bin"))
    
    doLast {
        val copiedFiles = layout.buildDirectory.dir("bin").get().asFile.listFiles()
            ?.filter { it.name.contains("launcher") }
            ?.map { it.name }
            ?: emptyList()
        
        logger.lifecycle("Copied ${copiedFiles.size} cross-compiled launchers:")
        copiedFiles.forEach { fileName ->
            logger.lifecycle("  📦 $fileName")
        }
    }
}

val assemble by tasks.existing {
    dependsOn(copyExecutables)
    doLast {
        logger.info("Finished building and assembling native launcher executable for current platform.")
    }
}

val xtcLauncherBinaries by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(copyExecutables)
}
