# XTC Gradle Plugin

A comprehensive Gradle plugin that teaches Gradle the XTC programming language, enabling seamless compilation, testing, and execution of XTC projects.
This documents describes how it works, and goes into some technical detail, but also serves as a usage guide. There are several XTC project examples
on GitHub that will show you the basics of setting up and XTC build and execution environment with the build DSL. If you are just looking for the
simplest possible XTC "HelloWorld" setup, take a look at the examples repository.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [Core Components](#core-components)
  - [Launcher System](#launcher-system)
  - [Debugging](#debugging)
  - [Programmatic API Access](#programmatic-api-access)
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
- **Multiple Launchers**: Native binaries, forked JVMs, or in-process execution
- **Programmatic API Access**: Direct method calls to compiler without reflection
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
- **XtcTestTask**: Executes XUnit tests for a module
- **XtcDisassembleTask**: Disassembles XTC modules for debugging
- **XtcLauncherTask**: Abstract base for all launcher-based tasks

### Launcher System

The plugin uses **JavaClasspathLauncher** for all XTC tool execution, providing optimal performance and flexibility.

#### JavaClasspathLauncher
**File**: `org.xtclang.plugin.launchers.JavaClasspathLauncher`

Invokes javatools classes directly, either in-process or in a forked JVM based on the `fork` setting. Supports detached background processes for long-running applications.

**Execution Modes:**

1. **In-Process (fork=false)** - Default for compilation
   - Instant startup (~0ms)
   - Shares Gradle daemon JVM
   - Full IDE debugging support
   - Configuration cache compatible

2. **Forked Process (fork=true)** - For runtime isolation
   - Complete isolation from Gradle JVM
   - Independent JVM arguments
   - ~1-2s JVM startup time
   - Supports JDWP remote debugging

3. **Detached Process (detach=true)** - For background services
   - Automatically enables forking
   - Process continues after Gradle exits
   - Output redirected to timestamped log file
   - Returns immediately without waiting

**Implementation Details:**
The plugin has compile-time access to javatools types through a `compileOnly` dependency:
```kotlin
// plugin/build.gradle.kts
dependencies {
    compileOnly(libs.javatools)  // Type information only, not bundled
}
```

At runtime, the plugin loads javatools.jar dynamically:
```java
// Direct invocation with full type safety
XtcJavaToolsRuntime.withJavaTools(javaToolsJar, logger, () -> {
    Compiler.launch(args);  // No reflection!
    return result;
});
```

**Configuration:**
```kotlin
xtcCompile {
    fork.set(false)  // In-process (fast, default)
}

xtcRun {
    fork.set(true)    // Separate process (isolation)
    // OR
    detach.set(true)  // Background process (fork automatically enabled)
}
```

**Benefits:**
- Direct type-safe calls (`Compiler.launch(args)`) - no reflection
- Full IDE debugging support (fork=false)
- JDWP remote debugging support (fork=true)
- Configuration cache compatible
- Single launcher for all scenarios

### Debugging

The plugin supports debugging XTC code through standard Java debugging tools.

#### In-Process Debugging (fork=false)

When using `fork=false` (default for compilation), you can debug directly in your IDE by attaching to the Gradle daemon:

1. Start Gradle with debug enabled:
   ```bash
   ./gradlew compileXtc --no-daemon -Dorg.gradle.debug=true
   ```

2. Gradle will wait for debugger connection on port 5005

3. Attach your IDE debugger to `localhost:5005`

This allows stepping through both plugin code and javatools (compiler/runtime) code.

#### Remote Debugging (fork=true)

For forked processes, use standard JDWP arguments with `jvmArgs`:

```kotlin
xtcRun {
    fork.set(true)
    jvmArgs(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
    )

    module {
        moduleName = "MyApp"
    }
}
```

**JDWP Parameters:**
- `transport=dt_socket`: Use TCP/IP sockets
- `server=y`: Listen for debugger connection
- `suspend=y`: Wait for debugger before starting (use `suspend=n` to start immediately)
- `address=5005`: Port number for debugger connection

**Steps:**
1. Run the task: `./gradlew runXtc`
2. The process will suspend and wait for debugger
3. Attach your IDE debugger to `localhost:5005`
4. Debug your XTC code as it executes

**Example with Different Port:**
```kotlin
xtcCompile {
    fork.set(true)
    jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000")
}
```

#### Debugging Tips

- **In-Process (fork=false)**: Best for debugging plugin code and compiler internals
- **Forked (fork=true)**: Best for debugging XTC application code in isolation
- **Detached Mode**: Not recommended for debugging (process runs in background)
- **Multiple Modules**: When running multiple modules sequentially, debugger will attach to each execution

### Programmatic API Access

The plugin can directly call javatools methods without reflection, providing significant benefits:

#### Architecture

**Compile-Time Types**:
```kotlin
// Plugin declares compile-only dependency
dependencies {
    compileOnly(libs.javatools)  // Provides types at compile time
}
```

**Runtime Loading**:
```java
// Plugin loads javatools.jar dynamically at runtime
XtcJavaToolsRuntime.ensureJavaToolsInClasspath(
    projectVersion, javaToolsConfig, xdkFileTree, logger);
```

**Direct Invocation**:
```java
// Call compiler directly - no reflection!
Compiler.launch(args);
Runner.launch(args);
Disassembler.launch(args);
```

#### Benefits

**Developer Experience**:
- **IDE Integration**: Full autocomplete and type checking for javatools APIs
- **Compile-Time Safety**: Catches API misuse at compile time
- **Refactoring**: Safe renames across plugin and javatools
- **Debugging**: Step through compiler code directly in IDE

**Performance**:
- **No Reflection Overhead**: Direct method calls
- **JIT Optimization**: HotSpot can inline across plugin/javatools boundary
- **In-Process Execution**: Zero overhead when fork=false

**Maintainability**:
- **Clear API Surface**: Explicit dependencies make architecture obvious
- **Type Safety**: Compiler verifies all javatools calls
- **Easy Testing**: Direct method calls simplify unit tests

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
    fork.set(false)  // In-process execution (default)
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

    // Launcher configuration
    fork.set(false)  // In-process execution (default)

    // JVM arguments (only used when fork=true)
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

    // JVM arguments (only used when fork=true)
    jvmArgs("-Xmx1g")

    // Execution mode
    fork.set(false)  // In-process (default)
}
```

### Test Configuration

**Extension**: `XtcTestExtension`

**Note**: In most cases, you don't need to configure the module path manually. The plugin automatically resolves:
- All dependencies declared with `xtcModule(...)` in your build file
- Compiled XTC modules from all project dependencies (including composite builds)
- Build output directories from the current project and its dependencies

**Minimal Configuration** (recommended for most projects):
```kotlin
xtcTest {
    // Test module - this is usually all you need!
    module.set("myapp.xtc")
}
```

### Launcher Configuration

**Common Scenarios**:

| Scenario | Configuration | Description |
|----------|---------------|-------------|
| Fast development builds | `fork=false` (default) | In-process, instant startup |
| Debugging compiler/plugin | `fork=false` | Attach to Gradle daemon |
| Debugging XTC code | `fork=true` + jvmArgs | JDWP remote debugging |
| Memory isolation | `fork=true` | Separate JVM process |
| Background services | `detach=true` | Runs after Gradle exits |
| CI/CD builds | `fork=false` | Fastest for compilation |

**Execution Flow**:
```
JavaClasspathLauncher
  ├─ if (fork=false) → In-process execution (DEFAULT)
  ├─ if (fork=true, detach=false) → Forked process, wait for completion
  └─ if (detach=true) → Forked process, background execution
```

## Build Lifecycle

### Standard Build Flow

1. **Configuration Phase**:
   - Plugin creates source sets and tasks
   - Captures configuration-time data for cache compatibility
   - Registers javatools dependency

2. **Compilation Phase** (`compileXtc`):
   - Resolves full module path (XDK + dependencies + source sets)
   - Loads javatools.jar into plugin classloader
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
  xdk/contents/lib/ecstasy.xtc,
  build/dependencies/lib-json.xtc,
  build/dependencies/lib-test.xtc,
  build/xtc/main/,
  build/xtc/test/
]
```

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

// Used at execution (no Project access)
@TaskAction
public void executeTask() {
    File xdkDir = xdkContentsDir.get().getAsFile();
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

Run with `--configuration-cache` to enable. Note that configuration cache should *always* be enabled
in the gradle.properties for all projects, including the XVM build, to ensure a much faster build
process, and to force the programmer (or your AI) to create compatible code.

```bash
./gradlew compileXtc --configuration-cache
```

Gradle will report any violations:
- `Project.getLogger()` calls during execution
- Direct project property access
- Non-serializable task state

## Performance Optimization

The XTC Gradle Plugin is fully compatible with Gradle's standard performance features:

- **Configuration Cache**: Dramatically speeds up subsequent builds by caching configuration phase
- **Build Cache**: Reuses outputs from previous builds or shared across machines
- **Parallel Execution**: Compiles multiple modules concurrently

These features are fully supported and should be enabled in your `gradle.properties`:
```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

### XTC-Specific Optimizations

**Use In-Process Execution** (default):
```kotlin
xtcCompile {
    fork.set(false)  // In-process execution - instant startup
}
```

**Optimize for CI Builds**:
```kotlin
// CI/CD builds work best with default settings (fork=false)
xtcCompile {
    fork.set(false)  // In-process, fastest compilation
    verbose.set(false)  // Reduce log noise
}
```

**Adjust Memory for Large Projects**:
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g
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

### Fork Mode Issues

**Error**: Forked process fails or hangs

**Solution**:
1. Check JVM arguments are valid:
   ```kotlin
   xtcRun {
       fork.set(true)
       jvmArgs("-Xmx1g")  // Verify memory settings
   }
   ```
2. Enable verbose logging to see process output
3. Try in-process mode first to isolate the issue:
   ```kotlin
   fork.set(false)
   ```
4. For debugging, add JDWP args and attach debugger

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
2. **Capture Early**: Capture all configuration state in task constructors, but it's better to find a way where this doesn't matter
3. **No Project in Actions**: Never access `Project` in `@TaskAction` methods
4. **Newlines**: Always add newline at end of files (enforced by `CLAUDE.md`)
5. **Final State**: Fields are final if they don't MUST be anything else. Try to create all state as final during construction

### Testing

1. Run tests: `./gradlew plugin:test`
2. Test configuration cache: `./gradlew compileXtc --configuration-cache`
3. Test all launcher types
4. Verify multi-module scenarios

### Profiling

When profiling XTC compilation or execution, you have several options ranging from simple to advanced. These techniques are particularly useful for understanding where time is spent during the first compilation of large modules like lib-ecstasy.

#### 1. Gradle Build Scans (Recommended for Quick Analysis)

Build scans provide a cloud-hosted timeline view of your build with task timing, dependency resolution, and performance insights.

**Usage:**
```bash
./gradlew build --scan
```

After the build completes, you'll receive a URL to view the detailed scan online. This shows:
- Task execution times
- Dependency resolution performance
- Configuration phase timing
- Build cache effectiveness

**Best for**: Quick overview of build performance, identifying slow tasks, sharing results with team.

#### 2. Gradle Built-in Profiler

Gradle includes a local profiler that generates an HTML report with detailed timing information.

**Usage:**
```bash
./gradlew build --profile
```

**Output**: `build/reports/profile/profile-<timestamp>.html`

The report includes:
- Task execution breakdown
- Dependency resolution timing
- Configuration vs. execution time
- Project-level performance metrics

**Best for**: Local analysis, CI/CD integration, offline viewing.

#### 3. Java Flight Recorder (JFR) - Production-Grade Profiling

JFR is built into the JVM and provides low-overhead (typically <1%) method-level profiling with rich data about:
- CPU usage by method
- Memory allocations
- Thread activity
- I/O operations
- JVM internals (GC, JIT compilation)

**Usage:**
```bash
./gradlew build \
  -Dorg.gradle.jvmargs="-XX:StartFlightRecording=filename=recording.jfr,dumponexit=true,settings=profile"
```

**Analyzing Results:**

Option A: Command-line (basic text output):
```bash
# Print summary
java -version  # Requires JDK 9+
jdk.jfr.tool.Main print recording.jfr

# Or with JDK 11+:
jfr print recording.jfr
```

Option B: JDK Mission Control (GUI - recommended):
```bash
# Download JMC from https://jdk.java.net/jmc/
# Then open the .jfr file
jmc
```

**Advanced Options:**
```bash
# Custom duration limit (60 seconds)
-XX:StartFlightRecording=filename=recording.jfr,duration=60s,settings=profile

# Maximum size limit (100MB)
-XX:StartFlightRecording=filename=recording.jfr,maxsize=100m,settings=profile

# Custom settings (default or profile)
# 'default' = ~1% overhead, 'profile' = ~2% overhead with more detail
-XX:StartFlightRecording=filename=recording.jfr,settings=default
```

**Best for**: Deep method-level analysis, production environments, finding hot paths, memory allocation analysis.

#### 4. Async-Profiler - Advanced CPU/Memory Profiling

Async-profiler is a low-overhead sampling profiler that generates flame graphs showing where time is spent. It supports:
- CPU profiling (method execution time)
- Allocation profiling (heap allocations)
- Lock profiling (contention analysis)
- Native code profiling (JNI calls)

**Setup:**
```bash
# Download from https://github.com/async-profiler/async-profiler/releases
# Extract to a location, e.g., ~/tools/async-profiler

# macOS example:
wget https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-2.9-macos.zip
unzip async-profiler-2.9-macos.zip -d ~/tools/
```

**Usage:**

CPU Profiling (generates interactive HTML flame graph):
```bash
./gradlew build \
  -Dorg.gradle.jvmargs="-agentpath:$HOME/tools/async-profiler-2.9-macos/lib/libasyncProfiler.so=start,event=cpu,file=profile.html"
```

Allocation Profiling (track heap allocations):
```bash
./gradlew build \
  -Dorg.gradle.jvmargs="-agentpath:$HOME/tools/async-profiler-2.9-macos/lib/libasyncProfiler.so=start,event=alloc,file=alloc-profile.html"
```

**Advanced Options:**
```bash
# Customize sampling interval (default 10ms)
-agentpath:/path/to/libasyncProfiler.so=start,event=cpu,interval=1ms,file=profile.html

# Generate both flame graph and collapsed stacks
-agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html,collapsed

# Profile specific Java packages only
-agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html,include='org/xvm/*'
```

**Reading Flame Graphs:**
- X-axis: Proportion of samples (wider = more time spent)
- Y-axis: Call stack depth (bottom = entry point, top = leaf methods)
- Colors: Different packages/classes
- Click to zoom into specific code paths

**Best for**: Finding CPU hotspots, memory allocation patterns, visual analysis, performance optimization.

#### 5. GC Profiling

If you suspect garbage collection is impacting build time, enable GC logging:

**Java 9+ (Unified Logging):**  
```bash
./gradlew build \
  -Dorg.gradle.jvmargs="-Xlog:gc*:file=gc.log:time,level,tags"
```

**Analysis:**
```bash
# View GC events
cat gc.log

# Summary statistics
grep "Pause" gc.log | awk '{sum+=$NF; count++} END {print "Average GC pause:", sum/count "ms"}'
```

**Best for**: Diagnosing memory issues, tuning GC parameters, identifying excessive allocations.

#### Choosing the Right Tool

| Tool | Setup Effort | Detail Level | Best Use Case |
|------|--------------|--------------|---------------|
| Build Scan | None | Task-level | Quick overview, team sharing |
| `--profile` | None | Task-level | Local analysis, CI reports |
| JFR | Minimal | Method-level | Production profiling, comprehensive analysis |
| Async-profiler | Download | Method-level | Performance optimization, flame graphs |
| GC Logging | Minimal | GC events | Memory/GC tuning |

**Recommended Workflow:**
1. Start with `--profile` or `--scan` to identify slow tasks
2. Use JFR for detailed method-level analysis of slow tasks
3. Use async-profiler when optimizing specific hot paths
4. Enable GC logging if memory pressure is suspected

### Documentation

Update this README when:
- Adding new configuration options
- Introducing new launcher types
- Changing module path resolution
- Adding performance optimizations
