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

#### 2. **JavaClasspathLauncher** (Default - Recommended for Development)
**File**: `org.xtclang.plugin.launchers.JavaClasspathLauncher`

Invokes javatools classes directly, either in-process or in a forked JVM based on the `fork` setting.

**Advantages:**
- Direct type-safe calls (`Compiler.launch(args)`) - no reflection
- Full IDE debugging support
- Instant startup when fork=false (~0ms)
- Complete isolation when fork=true
- Configuration cache compatible

**Disadvantages:**
- When fork=false: shares Gradle daemon JVM
- When fork=true: slower startup (~1-2s JVM launch)

**Use When:**
- Development builds (default)
- Need direct debugging of compiler code
- Building custom ErrorListener integrations
- Balance between performance and isolation

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
    // OR
    fork.set(true)   // Separate process (isolation)
}
```

**Performance Characteristics:**
- **fork=false**: Instant startup, shared memory with Gradle daemon
- **fork=true**: ~1.5s JVM startup, complete isolation

#### 3. **JavaExecLauncher** (Legacy - Explicit Isolation)
**File**: `org.xtclang.plugin.launchers.JavaExecLauncher`

Forks a new Java process for each execution using Gradle's `javaexec`. This launcher is less commonly used but available for explicit isolation needs.

**Advantages:**
- Complete isolation from Gradle JVM
- Independent JVM arguments and classpath
- Supports debugging with JDWP
- Java toolchain integration

**Disadvantages:**
- Slower than alternatives (~1-2s JVM startup)
- Higher memory usage (separate JVM)
- JIT compiler starts cold each time

**Use When:**
- Need complete process isolation
- Running with custom JVM arguments
- Debugging with JDWP remote debugging

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
    useNativeLauncher.set(false)  // Use JavaClasspathLauncher (default)
    fork.set(false)                // In-process execution (default)

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

### Launcher Configuration

**Decision Matrix**:

| Scenario | Launcher | Configuration |
|----------|----------|---------------|
| Fast development builds | JavaClasspath | `fork=false` (default) |
| CI/CD production builds | Native | `useNativeLauncher=true` |
| Debugging compiler | JavaClasspath | `fork=false` |
| Memory isolation | JavaClasspath | `fork=true` |
| Fastest startup | Native | `useNativeLauncher=true` |

**Selection Logic** (in `XtcLauncherTask.createLauncher()`):
```
if (useNativeLauncher) → NativeBinaryLauncher
else → JavaClasspathLauncher (with fork setting)
    if (fork=false) → In-process execution (DEFAULT)
    if (fork=true) → Separate process
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

**Use Native Launcher for CI**:
```kotlin
if (System.getenv("CI") != null) {
    xtcCompile {
        useNativeLauncher.set(true)  // Fastest cold startup
    }
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

### Native Launcher Not Found

**Error**: `Native launcher executable not found`

**Solution**:
1. Verify XDK distribution includes native binaries
2. Check platform compatibility
3. Fallback to JavaClasspath launcher:
   ```kotlin
   xtcCompile {
       useNativeLauncher.set(false)
       fork.set(false)  // Or true for isolation
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
2. **Capture Early**: Capture all configuration state in task constructors, but it's better to find a way where this doesn't matter
3. **No Project in Actions**: Never access `Project` in `@TaskAction` methods
4. **Newlines**: Always add newline at end of files (enforced by `CLAUDE.md`)
5. **Final State**: Fields are final if they don't MUST be anything else. Try to create all state as final during construction

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
