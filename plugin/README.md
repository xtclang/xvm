# XTC Gradle Plugin

A comprehensive Gradle plugin that teaches Gradle the XTC programming language, enabling seamless compilation, testing, and execution of XTC projects.
This documents describes how it works, and goes into some technical detail, but also serves as a usage guide. There are several XTC project examples
on GitHub that will show you the basics of setting up and XTC build and executin environemnt with the build DSL. If you are just looking for the
simplest possible XTC "HellWorld" setup, take a look at the git@

## Table of Contents`
`
- [Overview](#overview)
- [Architecture](#architecture)
  - [Core Components](#core-components)
  - [Launcher System](#launcher-system)
  - [Compiler Daemon](#compiler-daemon)
- [Configuration](#configuration)
  - [Basic Setup](#basic-setup)
  - [Compilation Configuration](#compilation-configuration)
  - [Runtime Configuration](#runtime-configuration)
  - [Launcher Configuration](#launcher-configuration)
- [Build Lifecycle](#build-lifecycle)
- [Module Path Resolution](#module-path-resolution)
- [Configuration Cache Compatibility](#configuration-cache-compatibility)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Overview

The XTC Gradle Plugin integrates the XTC language into Gradle's build ecosystem by providing:

- **Source Set Integration**: XTC source directories alongside Java/Kotlin code
- **Dependency Management**: Transitive dependencies between XTC modules
- **Multiple Launchers**: Native binaries, forked JVMs, compiler daemon, or in-process execution
- **Configuration Cache**: Full support for Gradle's configuration cache
- **Incremental Compilation**: Smart up-to-date checking for fast rebuilds
- **Flexible Module Path**: Custom module path resolution for complex project structures

## Architecture

### Core Components

#### 1. **XtcPlugin** (`org.xtclang.plugin.XtcPlugin`)
The main plugin entry point that:
- Applies Java plugin as a foundation
- Creates XTC-specific configurations for dependencies
- Registers source sets and compilation tasks
- Sets up the launcher framework
- Configures project extensions

#### 2. **Source Set Support**
Each Gradle source set (`main`, `test`, etc.) gets:
- An `xtc` source directory (e.g., `src/main/xtc`)
- A compilation task (`compileXtc`, `compileTestXtc`)
- Output directories under `build/xtc/<sourceSet>/`
- Dependency configurations for XTC modules

#### 3. **Task System**
Key task types:
- **XtcCompileTask**: Compiles XTC source files to `.xtc` modules
- **XtcRunTask**: Executes XTC applications
- **XtcDisassembleTask**: Disassembles XTC modules for debugging
- **XtcLauncherTask**: Abstract base for all launcher-based tasks

### Launcher System

The plugin supports three distinct launcher types, each optimized for different scenarios:

#### 1. **NativeBinaryLauncher** (Recommended for Production)
**File**: `org.xtclang.plugin.launchers.NativeBinaryLauncher`

Executes XTC tools using native platform binaries (GraalVM-compiled).

**Advantages:**
- Fastest startup time (~10-50ms)
- Minimal memory overhead
- No JVM warmup required
- Native OS integration

**Disadvantages:**
- Platform-specific binaries required
- Limited debugging capabilities
- Must be pre-built for each platform

**Use When:**
- Building production artifacts
- CI/CD pipelines with time constraints
- Cross-platform builds (with proper binaries)

**Configuration:**
```kotlin
xtcCompile {
    useNativeLauncher.set(true)  // Enable native launcher
}
```

#### 2. **CompilerDaemonLauncher** (Default - Recommended for Development)
**File**: `org.xtclang.plugin.launchers.CompilerDaemonLauncher`

Uses a persistent compiler daemon (Gradle Build Service) to eliminate JVM startup overhead. This is the default launcher.

**Advantages:**
- No JVM startup overhead after first compilation
- ClassLoader reuse across compilations
- JIT-compiled hot paths stay warm
- Thread-safe with proper lifecycle management
- Configuration cache compatible

**Disadvantages:**
- Shares memory with Gradle daemon
- Less isolation than forked processes

**Use When:**
- Iterative development with frequent compilations (default)
- Multi-module projects with many compilation tasks
- Build performance is critical

**Configuration:**
```kotlin
xtcCompile {
    useCompilerDaemon.set(true)  // Enable compiler daemon (default)
}
```

**Implementation Details:**
The daemon uses Gradle's Build Service API to maintain a single compiler instance across all tasks in a build. The service lifecycle is managed by Gradle:
- **Created**: On first compilation task
- **Reused**: Across all subsequent compilation tasks
- **Destroyed**: When Gradle daemon terminates

**Performance Impact:**
In a typical multi-module project:
- **First compilation**: Same as JavaExecLauncher
- **Subsequent compilations**: 30-60% faster (eliminates ~1-2s JVM startup per compilation)
- **Clean builds**: 40-50% faster in projects with 10+ modules

#### 3. **JavaExecLauncher** (Fallback - Isolation Mode)
**File**: `org.xtclang.plugin.launchers.JavaExecLauncher`

Forks a new Java process for each compilation, providing maximum isolation. This is the fallback when the compiler daemon is disabled.

**Advantages:**
- Complete isolation from Gradle JVM
- Independent JVM arguments and classpath
- Supports debugging with JDWP
- Java toolchain integration

**Disadvantages:**
- Slower than daemon (~1-2s JVM startup per compilation)
- Higher memory usage (separate JVM)
- JIT compiler starts cold each time

**Use When:**
- Compiler daemon is disabled
- Memory isolation is critical
- Compiler stability issues need isolation

**Configuration:**
```kotlin
xtcCompile {
    useCompilerDaemon.set(false)  // Disable daemon, use JavaExec instead
    jvmArgs("-Xmx2g", "-Xms512m")  // Custom JVM arguments
}
```

### Compiler Daemon

The Compiler Daemon is a persistent XTC compiler instance that eliminates JVM startup overhead by reusing the same compiler across all compilation tasks in a build.

#### Architecture

**Build Service Registration**:
```java
// Registered in XtcLauncherTask constructor
this.compilerServiceProvider = project.getGradle().getSharedServices()
    .registerIfAbsent("xtcCompilerDaemon", XtcCompilerService.class, spec -> {
        // Service is shared across all projects in build
    });
```

**Service Lifecycle**:
1. **Initialization**: First compilation task creates the service
2. **Compilation**: Service loads XTC compiler from javatools classpath
3. **Reuse**: Subsequent tasks reuse the same compiler instance
4. **Cleanup**: Service destroyed when Gradle daemon terminates

**Thread Safety**:
The service uses proper synchronization to handle parallel compilation tasks:
```java
public synchronized XtcCompileResult compile(
    List<String> args,
    FileCollection javaToolsClasspath,
    OutputStream stdout,
    OutputStream stderr
) {
    // Thread-safe compilation execution
}
```

#### Configuration Cache Support

The daemon is fully compatible with Gradle's configuration cache:
- Service provider captured as `Provider<XtcCompilerService>` at configuration time
- ClassLoader and configuration stored in serialized task state
- Service automatically restored from cache on subsequent builds

#### Performance Characteristics

**Startup Overhead** (per compilation):
- **JavaExecLauncher**: ~1.5s (JVM startup + classloading)
- **CompilerDaemonLauncher**: ~0.05s (no startup, warm JIT)
- **Improvement**: ~1.45s saved per compilation

**Memory Usage**:
- **Shared**: Service shares Gradle daemon heap (~50-100MB for compiler)
- **Isolated**: JavaExecLauncher creates separate JVM (~200-400MB each)

**JIT Compilation**:
- **First Compilation**: Interpreted mode (slower)
- **Subsequent**: JIT-compiled hot paths (30-50% faster execution)

#### Usage Guidelines

**When to Enable**:
- Multi-module projects (5+ XTC modules)
- Frequent recompilations during development
- Build performance is a priority

**When to Disable**:
- Compiler instability (crashes affecting build)
- Memory constraints on build machine
- Debugging compiler-specific issues

**Configuration**:
```kotlin
// Enable daemon (default)
xtcCompile {
    useCompilerDaemon.set(true)
}

// Disable for isolation
xtcCompile {
    useCompilerDaemon.set(false)  // Use JavaExecLauncher instead
}
```

## Configuration

### Basic Setup

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("org.xtclang.xtc-plugin") version "X.Y.Z"
}

// Optional: Configure compilation
xtcCompile {
    verbose.set(true)
    useCompilerDaemon.set(true)
}
```

### Compilation Configuration

**Extension**: `XtcCompilerExtension`

**Note**: Module path is automatically resolved from your `xtcModule(...)` dependencies. Manual configuration is rarely needed.

```kotlin
xtcCompile {
    // Compiler verbosity
    verbose.set(true)

    // Show XTC version during compilation
    showVersion.set(true)

    // Launcher configuration (defaults are usually fine)
    useNativeLauncher.set(false)
    useCompilerDaemon.set(true)  // Default - recommended for development

    // JVM arguments (for JavaExecLauncher when daemon is disabled)
    jvmArgs("-Xmx2g", "-Xms512m")
}

// Advanced: Custom module path (overrides automatic resolution)
// Only use if you need non-standard module locations
xtcCompile {
    modulePath.from(files("custom/modules"))
}
```

### Runtime Configuration

**Extension**: `XtcRunExtension`

**Note**: In most cases, you don't need to configure the module path manually. The plugin automatically resolves:
- All dependencies declared with `xtcModule(...)` in your build file
- Compiled XTC modules from all project dependencies (including composite builds)
- Build output directories from the current project and its dependencies

**Minimal Configuration** (recommended for most projects):
```kotlin
xtcRun {
    // Main module and method - this is usually all you need!
    module.set("myapp.xtc")
    method.set("run")

    // Optional: Program arguments
    programArgs("arg1", "arg2")
}
```

**Full Configuration** (for advanced scenarios):
```kotlin
xtcRun {
    // Main module and method
    module.set("myapp.xtc")
    method.set("run")

    // Program arguments
    programArgs("arg1", "arg2")

    // Custom module path (only if you need to override automatic resolution)
    modulePath.from(files("runtime/modules"))

     // JVM arguments (for JavaExecLauncher when daemon is disabled)
    jvmArgs("-Xmx1g")
}
```

**Composite Build Example**:
For a composite build with multiple projects, the default configuration just works:
```kotlin
// In project A that depends on project B and C
dependencies {
    xtcModule(project(":projectB"))
    xtcModule(project(":projectC"))
    xtcModule("org.xtclang:lib-json:1.0.0")
}

// Default xtcRun configuration automatically includes:
// - XDK core libraries (ecstasy.xtc, etc.)
// - lib-json.xtc from Maven
// - All .xtc files from projectB's build/xtc/main/
// - All .xtc files from projectC's build/xtc/main/
// - All .xtc files from this project's build/xtc/main/
xtcRun {
    module.set("projectA.xtc")
    method.set("run")
    // That's it! No module path configuration needed.
}
```

### Launcher Configuration

**Decision Matrix**:

| Scenario | Launcher | Configuration |
|----------|----------|---------------|
| Fast development builds | CompilerDaemon | `useCompilerDaemon=true, fork=false` |
| CI/CD production builds | Native | `useNativeLauncher=true` |
| Debugging compiler | JavaExec | `fork=true` |
| Memory isolation | JavaExec | `fork=true` |
| Fastest startup | Native | `useNativeLauncher=true` |
| Multi-module optimization | CompilerDaemon | `useCompilerDaemon=true` |

**Selection Logic** (in `XtcLauncherTask.createLauncher()`):
```
if (useNativeLauncher) → NativeBinaryLauncher
else if (useCompilerDaemon && !fork) → CompilerDaemonLauncher
else if (fork) → JavaExecLauncher
else → BuildThreadLauncher (not recommended)
```

## Build Lifecycle

### Standard Build Flow

1. **Configuration Phase**:
   - Plugin creates source sets and tasks
   - Captures configuration-time data for cache compatibility
   - Registers compiler daemon service

2. **Compilation Phase** (`compileXtc`):
   - Resolves full module path (XDK + dependencies + source sets)
   - Selects appropriate launcher
   - Compiles XTC sources to `build/xtc/main/`
   - Validates module dependencies

3. **Test Compilation** (`compileTestXtc`):
   - Compiles test sources with test dependencies
   - Links against main module output

4. **Packaging** (`jar`):
   - Includes compiled XTC modules in JAR
   - Preserves module structure

### Task Dependencies

```
compileJava
    ↓
compileXtc → processResources → classes → jar
    ↓
compileTestXtc → test
```

### Incremental Compilation

The plugin uses Gradle's up-to-date checking based on:
- **Input files**: XTC source files
- **Input configuration**: Module path, compiler args, launcher settings
- **Output files**: Generated `.xtc` modules

Change any input → task re-executes.

## Module Path Resolution

The module path determines where the XTC compiler and runtime look for dependencies.

**Important**: The plugin automatically resolves the module path for you. You rarely need to configure it manually.

### Automatic Resolution (Default Behavior)

The plugin automatically builds the module path from:

1. **XDK Contents**: Core XTC libraries (ecstasy.xtc, etc.)
   - Resolved from XDK configuration
   - Always included first

2. **XTC Module Dependencies**:
   - All dependencies declared with `xtcModule(...)` in your build file
   - Includes project dependencies (composite builds)
   - Includes external dependencies (Maven/local)
   - Transitive dependencies automatically included
   - One configuration per source set

3. **Project Build Outputs**:
   - Compiled `.xtc` modules from the current project's build directories
   - Build outputs from all dependent projects (for composite builds)
   - Output directories for each source set

**This means**:
- For **single projects**: Just declare dependencies, the plugin handles the rest
- For **composite builds**: All project dependencies are automatically discovered and included
- For **runtime**: `xtcRun` includes everything compiled by your dependencies

### Manual Override (Advanced)

Only specify a custom module path if you need non-standard module locations:

1. **Custom Module Path** (when explicitly specified):
   - User-provided directories/modules
   - **Overrides** automatic dependency resolution
   - Use only for special aggregator projects or custom layouts

### Example Resolution

For a project with dependencies:
```kotlin
dependencies {
    xtcModule("org.xtclang:lib-json:1.0.0")
    testXtcModule("org.xtclang:lib-test:1.0.0")
}
```

**Module Path** (main compilation):
```
[
  xdk/contents/lib/ecstasy.xtc,
  build/dependencies/lib-json.xtc,
  build/xtc/main/
]
```

**Module Path** (test compilation):
```
[
  xdk/contents/lib/ecstasy.xtc,1wa 
  build/dependencies/lib-json.xtc,
  build/dependencies/lib-test.xtc,
  build/xtc/main/,
  build/xtc/test/
]
```

### Composite Build Module Path

**Example**: Multi-project build with shared dependencies

```
root/
  ├── projectA/
  │   ├── src/main/xtc/
  │   └── build.gradle.kts
  ├── projectB/
  │   ├── src/main/xtc/
  │   └── build.gradle.kts
  └── projectC/
      ├── src/main/xtc/
      └── build.gradle.kts
```

**Project A's build.gradle.kts**:
```kotlin
dependencies {
    xtcModule(project(":projectB"))
    xtcModule(project(":projectC"))
}

// No module path configuration needed!
// Automatically includes:
// - XDK core libraries
// - projectB/build/xtc/main/*.xtc
// - projectC/build/xtc/main/*.xtc
// - projectA/build/xtc/main/*.xtc (this project)
```

**Running the application**:
```bash
# Compiles all dependencies and runs projectA
./gradlew :projectA:runXtc

# Module path automatically includes all three projects
```

### Custom Module Path (Advanced)

**Only use this for special cases** like aggregator projects or non-standard layouts:

```kotlin
xtcCompile {
    // Provide custom module directories
    modulePath.from(
        files("custom/modules"),
        layout.buildDirectory.dir("collected-modules")
    )
}
```

**Warning**: This **disables** automatic `xtcModule` dependency resolution. You must manually ensure all required modules are in the specified paths.

## Configuration Cache Compatibility

The plugin is fully compatible with Gradle's configuration cache through careful design:

### Principles

1. **No Project Access During Execution**:
   - All project state captured at configuration time
   - Stored in Provider/Property types

2. **Lazy Configuration**:
   - Use `Provider<T>` and `Property<T>` for deferred values
   - Avoid calling `.get()` during configuration

3. **Serializable State**:
   - No lambda captures
   - No script object references
   - Injected services for execution

### Implementation Patterns

**Configuration-Time Capture**:
```java
// Captured at construction (configuration phase)
this.xdkContentsDir = XtcProjectDelegate.getXdkContentsDir(project);
this.sourceSetNames = sourceSets.stream().map(SourceSet::getName).toList();
this.useCompilerDaemonValue = ext.getUseCompilerDaemon().get();

// Used at execution (no Project access)
@TaskAction
public void executeTask() {
    File xdkDir = xdkContentsDir.get().getAsFile();
    boolean useDaemon = useCompilerDaemonValue;
    // ... compilation logic
}
```

**Injected Services**:
```java
@Inject
public abstract ExecOperations getExecOperations();

@TaskAction
public void executeTask() {
    // Use injected service, not project.exec()
    getExecOperations().javaexec(spec -> {
        // ...
    });
}
```

### Validation

Run with `--configuration-cache` to enable. Note that configuration cache should *always* be eanbled
in the gradle.properties for all projects, including the XVM build, to ensure a much faster build
process, and to force the programmer (or your AI) to create compatible code.
1 
```bash
./gradlew compileXtc --configuration-cache
```

Gradle will report any violations:
- `Project.getLogger()` calls during execution
- Direct project property access
- Non-serializable task state

## Performance Optimization

### Build Time Optimization

1. **Use Compiler Daemon** (default):
   ```kotlin
   xtcCompile {
       useCompilerDaemon.set(true)
   }
   ```
   - **Impact**: 30-60% faster multi-module builds

2. **Enable Configuration Cache**:
   ```bash
   ./gradlew build --configuration-cache
   ```
   - **Impact**: 2-10x faster subsequent builds (no configuration phase)

3. **Enable Build Cache**:
   ```bash
   ./gradlew build --build-cache
   ```
   - **Impact**: Reuse outputs from previous builds or other machines

4. **Parallel Execution**:
   ```bash
   ./gradlew build --parallel
   ```
   - **Impact**: Compile multiple modules concurrently

5. **Use Native Launcher for CI**:
   ```kotlin
   if (System.getenv("CI") != null) {
       xtcCompile {
           useNativeLauncher.set(true)
       }
   }
   ```

### Memory Optimization

1. **Increase Gradle Daemon Memory**:
   ```properties
   # gradle.properties
   org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
   ```

2. **Limit Parallel Workers**:
   ```properties
   # gradle.properties
   org.gradle.workers.max=4
   ```

3. **Disable Daemon for Memory-Constrained Environments**:
   ```kotlin
   xtcCompile {
       useCompilerDaemon.set(false)
       fork.set(true)
   }
   ```

## Troubleshooting

### Compilation Failures

**Error**: `XTC Compilation Failed (exit code 1)`

**Solution**:
1. Enable verbose logging:
   ```kotlin
   xtcCompile {
       verbose.set(true)
   }
   ```
2. Check module path resolution in logs
3. Verify dependencies are available
4. Check for XTC syntax errors in source files

### Configuration Cache Issues

**Error**: `Configuration cache problems found`

**Solution**:
1. Ensure no custom task code accesses `Project` during execution
2. Use injected services instead of direct project access
3. Capture configuration at task creation time
4. Report issues to plugin maintainers

### Compiler Daemon Issues

**Symptom**: Build fails with "Could not create service"

**Solution**:
1. Disable daemon temporarily:
   ```kotlin
   xtcCompile {
       useCompilerDaemon.set(false)
       fork.set(true)
   }
   ```
2. Check Gradle daemon memory (`gradle.properties`)
3. Restart Gradle daemon: `./gradlew --stop`

### Native Launcher Not Found

**Error**: `Native launcher executable not found`

**Solution**:
1. Verify XDK distribution includes native binaries
2. Check platform compatibility
3. Fallback to JavaExec launcher:
   ```kotlin
   xtcCompile {
       useNativeLauncher.set(false)
       fork.set(true)
   }
   ```

### Module Path Conflicts

**Error**: `Duplicate module on path`

**Solution**:
1. Check dependency tree: `./gradlew dependencies --configuration xtcModule`
2. Exclude transitive dependencies:
   ```kotlin
   dependencies {
       xtcModule("org.xtclang:lib-json:1.0.0") {
           exclude(group = "org.xtclang", module = "lib-net")
       }
   }
   ```
3. Use custom module path to override

## Contributing

When modifying the plugin, follow these guidelines:

### Code Style

1. **Configuration Cache First**: Always design for configuration cache compatibility
2. **Capture Early**: Capture all configuration state in task constructors, but it's better to find a way where this doesn't matter.
3. **No Project in Actions**: Never access `Project` in `@TaskAction` methods
4. **Newlines**: Always add newline at end of files (enforced by `CLAUDE.md`)
5. **Final State**: Fields are final if they don't MUST be anything else. Try to create all state as final during construction.

### Testing

1. Run tests: `./gradlew plugin:test`
2. Test configuration cache: `./gradlew compileXtc --configuration-cache`
3. Test all launcher types
4. Verify multi-module scenarios

### Documentation

Update this README when:
- Adding new configuration options
- Introducing new launcher types
- Changing module path resolution
- Adding performance optimizations

---

**Plugin Version**: 1.0.0-SNAPSHOT
**XDK Version**: See `libs.versions.toml`
**Gradle Version**: 9.1.0+
**Java Version**: 25+