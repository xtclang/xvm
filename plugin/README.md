# Ecstasy Gradle Plugin

A comprehensive Gradle plugin that teaches Gradle the Ecstasy programming language, enabling seamless compilation, testing, and execution of XTC projects.
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

The Ecstasy Gradle Plugin integrates the Ecstasy language into Gradle's build ecosystem by providing:

- **Source Set Integration**: XTC source directories alongside Java/Kotlin code
- **Dependency Management**: Transitive dependencies between XTC modules
- **Execution Modes**: Attached/detached child processes, build-scoped embedding or an opt-in persistent worker
- **Embedding API**: Owned compiler/runtime sessions with request isolation and shutdown
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

Select `ExecutionMode` through the `executionMode` property or a task's `--mode` option.
The plugin default is ATTACHED; this repository's manualTests build defaults to DIRECT.

| Mode | Host and lifetime | Supported use |
|---|---|---|
| `ATTACHED` | A child JVM per launch; Gradle waits for completion | Default isolated compile/run/test execution |
| `DETACHED` | A background child process | Long-running applications; output goes to configured files |
| `DIRECT` | An isolated embedding session in the Gradle JVM, closed at build completion | Reuse for compile, interpreter run/xUnit and the experimental JIT subset |
| `PERSISTENT` | A separate Java worker reused by successive builds | Opt-in compile and interpreter run/xUnit; JIT is currently rejected |

DIRECT and PERSISTENT submit requests through the embedding API. They retain the runtime host,
while each compilation and application gets fresh mutable state, diagnostics, repository context
and output destinations. Requests serialize within each host. They do not repeatedly invoke
compiler/runner launchers or reuse application containers.

```kotlin
import org.xtclang.plugin.launchers.ExecutionMode

xtcCompile {
    executionMode.set(ExecutionMode.DIRECT)
}
xtcRun {
    executionMode.set(ExecutionMode.PERSISTENT)
}
```

PERSISTENT is off by default. Select it for one task with `runXtc --mode=PERSISTENT`, or for the
build's task conventions with `-PxtcDefaultExecutionMode=PERSISTENT`,
`XTC_EXECUTION_MODE=PERSISTENT`, or `-PxtcPersistentRuntime=true`. The mode property takes
precedence over the environment variable; either takes precedence over the boolean convenience
selector. Explicit task configuration overrides these conventions. `xtcPersistentRuntime=false`
does not prohibit an explicit PERSISTENT mode selection.

Workers live under the consumer root's `.gradle/xtc-workers`. They use the selected Java toolchain
and task JVM arguments. Runtime/plugin/core-module content, Java identity, JVM options and
inherited environment determine compatibility. Each worker loads private runtime copies; project
outputs are read afresh per request. A lost execution is reported without automatic replay.

`stopXtcWorker` stops idle workers for that consumer root, refusing to interrupt another build's
lease. Defaults are `xtcPersistentStartupTimeout=PT30S`, `xtcPersistentIdleTimeout=PT10M` and
`xtcPersistentShutdownTimeout=PT30S`; override these with ISO-8601 durations. DIRECT instead
requires compatible JVM startup options on the Gradle JVM itself. Configuration/build caches
remain enabled normally; persistence adds no mandatory heap flags.

See the [implementation and verification plan](doc/plans/embedded-runtime-plan.md#persistent-keep-the-host-warm-across-builds)
for ownership, opt-in integration testing and remaining memory/platform/concurrency work, and the
[merge plan](doc/plans/embedded-runtime-pr-plan.md#pr-10--optional-persistent-execution-across-builds)
for the independently reviewable scope. There is no automatic all-modes or persistent-worker
integration sweep in the manual CI aggregates.

### Debugging

The plugin supports debugging XTC code through standard Java debugging tools.

#### In-Process Debugging (DIRECT)

When using `DIRECT`, you can debug directly in your IDE by attaching to the Gradle daemon:

1. Start Gradle with debug enabled:
   ```bash
   ./gradlew compileXtc --no-daemon -Dorg.gradle.debug=true
   ```

2. Gradle will wait for debugger connection on port 5005

3. Attach your IDE debugger to `localhost:5005`

This allows stepping through both plugin code and javatools (compiler/runtime) code.

#### Remote Debugging (ATTACHED)

For forked processes, use standard JDWP arguments with `jvmArgs`:

```kotlin
xtcRun {
    executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.ATTACHED)
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
    executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.ATTACHED)
    jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000")
}
```

#### Debugging Tips

- **In-Process (DIRECT)**: Best for debugging plugin code and compiler internals
- **Forked (ATTACHED)**: Best for debugging XTC application code in isolation
- **Detached Mode**: Not recommended for debugging (process runs in background)
- **Multiple Modules**: When running multiple modules sequentially, debugger will attach to each execution

### Programmatic API Access

`DirectRuntimeBuildService` owns an `IsolatedRuntime` for the build. The PERSISTENT build service
owns a worker connection, and that worker owns the same `IsolatedRuntime` adapter. Its implementation
loader reads the selected XDK and exposes the shared `RuntimeExecutor`/`RuntimeOutput` boundary.
Gradle objects and loggers never enter the worker protocol or remain in its runtime session.

The implementation calls `EmbeddingSupport.compile(...)` and `EmbeddingSupport.run(...)` with
request-specific inputs. Close request controls after execution; close the session before closing
its implementation loader. The [embedding plan](doc/plans/embedded-runtime-plan.md) describes the
public API, isolation, shutdown and measured within-build performance.

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
    executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.DIRECT)
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
    executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.DIRECT)

    // DIRECT requires any JVM startup options to be set on the Gradle JVM itself.
}

// Advanced: Custom module path (overrides automatic resolution)
// Only use if you need non-standard module locations
xtcCompile {
    modulePath.from(files("custom/modules"))
}
```

### Runtime Configuration

**Extension**: `XtcRuntimeExtension`

**Note**: In most cases, you don't need to configure the module path manually. The plugin automatically resolves:
- All dependencies declared with `xtcModule(...)` in your build file
- Compiled XTC modules from all project dependencies (including composite builds)
- Build output directories from the current project and its dependencies

**Minimal Configuration** (recommended for most projects):
```kotlin
xtcRun {
    module {
        moduleName.set("myapp.xtc")
        methodName.set("run")
        moduleArgs("arg1", "arg2")
    }
}
```

**Full Configuration** (for advanced scenarios):
```kotlin
xtcRun {
    module {
        moduleName.set("myapp.xtc")
        methodName.set("run")
        moduleArgs("arg1", "arg2")
    }

    // Custom module path (only if you need to override automatic resolution)
    modulePath.from(files("runtime/modules"))

    // Execution mode
    executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.DIRECT)
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
    moduleName("myapp.xtc")
}
```

**Skipping Tests**:

The plugin supports standard Gradle/Maven conventions for skipping tests during development:

| Flag | Effect |
|------|--------|
| `-PskipTests` | Skips all XTC tests (`testXtc`) across all projects (recommended) |
| `-PskipAllTests` | Skips both Java tests (`test`) and XTC tests (`testXtc`) |
| `-x :project:testXtc` | Excludes a specific project's XTC test task only (not subprojects) |
| `-x test` | Excludes Java tests only |

> **Note**: The `-x` flag only excludes the specifically named task, not tasks in subprojects.
> Use `-PskipTests` to skip all XTC tests across all projects in a multi-project build.

**Examples**:
```bash
# Skip all XTC tests during rapid iteration (recommended)
./gradlew build -PskipTests

# Skip all tests (Java and XTC) during development
./gradlew build -PskipAllTests

# Exclude a specific project's test task only
./gradlew build -x :myproject:testXtc
```

### Launcher Configuration

| Scenario | Mode | Notes |
|---|---|---|
| Isolated execution | ATTACHED | Plugin default; a child JVM per launch |
| Reuse within a build | DIRECT | Attach a debugger to the Gradle JVM |
| Reuse across builds | PERSISTENT | Opt-in worker; separate JVM options and idle/explicit shutdown |
| Debug XTC in a child JVM | ATTACHED | Supply JDWP options through `jvmArgs` |
| Background application | DETACHED | Continues independently of the build |

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

The Ecstasy Gradle Plugin is fully compatible with Gradle's standard performance features:

- **Configuration Cache**: Dramatically speeds up subsequent builds by caching configuration phase
- **Build Cache**: Reuses outputs from previous builds or shared across machines
- **Parallel Execution**: Allows independent tasks to run concurrently; DIRECT/PERSISTENT serialize requests per host

These features are fully supported and should be enabled in your `gradle.properties`:
```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

### XTC-Specific Optimizations

Measure DIRECT for builds that perform many compile/run requests. PERSISTENT additionally retains
its host across builds; it is experimental and opt-in. Neither mode avoids work that is still
request-specific, such as application linking and metadata construction. Up-to-date/cache hits
can dominate builds that have no XTC work to perform. See the measured results and boundaries in
the [embedding plan](doc/plans/embedded-runtime-plan.md).

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
       executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.ATTACHED)
       jvmArgs("-Xmx1g")  // Verify memory settings
   }
   ```
2. Enable verbose logging to see process output
3. Try in-process mode first to isolate the issue:
   ```kotlin
   executionMode.set(org.xtclang.plugin.launchers.ExecutionMode.DIRECT)
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
