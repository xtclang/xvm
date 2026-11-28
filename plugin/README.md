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
- [IDE Model](#ide-model)
- [Configuration Cache Compatibility](#configuration-cache-compatibility)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Overview

The Ecstasy Gradle Plugin integrates the Ecstasy language into Gradle's build ecosystem by providing:

- **Source Set Integration**: XTC source directories alongside Java/Kotlin code
- **Dependency Management**: Transitive dependencies between XTC modules
- **Execution Modes**: A forked JVM for each task (the default), the Gradle daemon, or a detached background JVM
- **Programmatic API Access**: A runtime bridge compiled against javatools' own types, with typed requests
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
- An `x` source directory (e.g., `src/main/x`)
- A compilation task (`compileXtc`, `compileTestXtc`)
- Output directories under `build/xtc/<sourceSet>/`
- Dependency configurations for XTC modules

#### 3. **Task System**
Key task types:
- **XtcCompileTask**: Compiles XTC source files to `.xtc` modules
- **XtcRunTask**: Executes XTC applications
- **XtcTestTask**: Executes XUnit tests for a module
- **XtcLauncherTask**: Abstract base for all launcher-based tasks

### Launcher System

The plugin runs every XTC tool (the compiler, the runner and the test runner) through an execution strategy in
`org.xtclang.plugin.launchers`, one for each execution mode: `AttachedStrategy`, `DirectStrategy` and
`DetachedStrategy`.

**Execution Modes** (`executionMode` on a task or extension, or the Gradle property `xtcDefaultExecutionMode`):

1. **`ATTACHED`**, the default: a forked JVM for each task, whose output goes to the build's
   - Isolated from the Gradle daemon: a compiler crash or a large heap stays in that JVM
   - About a second of JVM startup for each task
   - Its JVM can be debugged with JDWP options, see [Debugging](#debugging)

2. **`DIRECT`**: in the Gradle daemon, through an isolated classloader for each javatools runtime and build
   - No JVM startup, and the compiler stays loaded for the rest of the build
   - A compile takes the library modules, such as ecstasy, that an earlier compile of the build loaded from
     memory, as long as their files have the same content. The compiles of a build run one at a time
   - One debugger session steps from the plugin into javatools, see [Debugging](#debugging)
   - A compiler crash or an out-of-memory error takes the daemon down, and runs and tests in the daemon share the
     interpreter's JVM-wide state

3. **`DETACHED`**: a forked JVM that keeps running after Gradle exits, for background services
   - Output redirected to timestamped log files
   - Returns immediately without waiting

**Implementation Details:**
`ATTACHED` and `DETACHED` start `java -cp javatools.jar` with the tool's main class, such as `org.xvm.tool.Compiler`.
`DIRECT` loads javatools.jar and the plugin's runtime bridge (`org.xtclang.plugin.runtime.impl`) into an isolated
classloader, which `DirectRuntimeBuildService` keeps for the rest of the build. The bridge is compiled against
javatools' types through a `compileOnly` dependency, see [Programmatic API Access](#programmatic-api-access):
```kotlin
// plugin/build.gradle.kts
dependencies {
    compileOnly(libs.javatools)  // Type information only, not bundled
}
```

**Configuration:**
```kotlin
import org.xtclang.plugin.launchers.ExecutionMode

xtcCompile {
    executionMode = ExecutionMode.DIRECT
}

xtcRun {
    executionMode = ExecutionMode.DETACHED
}
```

### Debugging

The plugin supports debugging XTC code through standard Java debugging tools.

#### Stepping from the Plugin into javatools (`DIRECT`)

A `DIRECT` task runs javatools in the Gradle daemon, so one debugger session sees the plugin and the compiler or
runtime it calls:

1. Start Gradle with debug enabled:
   ```bash
   ./gradlew compileXtc --rerun -PxtcDefaultExecutionMode=DIRECT -Dorg.gradle.debug=true
   ```

2. Gradle waits for a debugger connection on port 5005

3. Attach your IDE debugger to `localhost:5005`

This allows stepping through both plugin code and javatools (compiler/runtime) code.

#### Debugging a Forked JVM (`ATTACHED`, `DETACHED`)

A forked task's JVM takes standard JDWP options from `jvmArgs`:

```kotlin
xtcRun {
    jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
}
```

The plugin code runs in the Gradle daemon and javatools in the forked JVM, so stepping does not cross from one into
the other: set a breakpoint where javatools starts, such as `Launcher.launch`.

#### Debugging with an Ecstasy Debugger

To debug the Ecstasy program itself, with breakpoints in its `.x` sources, a run task can run it under
the launcher's `--debug`, which an IDE's Ecstasy debugger attaches to. The Gradle properties `xtcDebug`
and `xtcDebugAgent` set it for `runXtc`, the other run tasks and `testXtc`; the `debug` and
`debugAgent` properties of `xtcRun`, `xtcTest` or a task set it in the build script:

```bash
./gradlew runXtc --module=MyApp -PxtcDebug=listen:127.0.0.1:5005,suspend
```

The program then waits for a debugger, such as VS Code's or IntelliJ's Ecstasy Attach configuration on
port 5005. The IDEs set `xtcDebug` themselves when they debug a module in Gradle mode, with `connect:` to
their own port. The XDK's javatools has the debugger's agent; `xtcDebugAgent` names a jar with another
one, which the launcher then loads instead.
A `DIRECT` run cannot be debugged so, since the agent would run in the Gradle daemon; run it with
`--mode=ATTACHED`. A debugged `testXtc` always runs, and its results are not cached.

#### Debugging Tips

- **`DIRECT`**: Best for debugging plugin code and compiler internals
- **`ATTACHED`** (the default): Best for debugging XTC application code in isolation
- **Detached Mode**: Not recommended for debugging (process runs in background)
- **Multiple Modules**: When running multiple modules sequentially, debugger will attach to each execution

### Programmatic API Access

In `DIRECT` mode the plugin calls javatools through its Java API rather than its command line, with typed requests
(`DirectCompileRequest`, `DirectRunRequest`, `DirectTestRequest`) built from the task's settings.

#### Architecture

**Compile-Time Types**: the runtime bridge, `org.xtclang.plugin.runtime.impl`, is compiled against javatools:
```kotlin
// Plugin declares compile-only dependency
dependencies {
    compileOnly(libs.javatools)  // Provides types at compile time
}
```

**Runtime Loading**: `DirectRuntimeBuildService` loads the resolved javatools.jar
(`XtcJavaToolsRuntime.resolveRuntime`) and the bridge into an isolated classloader, once for each javatools runtime,
and keeps it for the rest of the build.

**Invocation**: when it creates a runtime, the plugin looks up the bridge's `executeCompile`, `executeRun` and
`executeTest` by reflection; the bridge then calls javatools' compiler, `Runner` and `TestRunner` directly.

#### Benefits

- **Compile-Time Safety**: The bridge's calls into javatools are type-checked when the plugin builds
- **Debugging**: One debugger session steps from the plugin into the compiler, see [Debugging](#debugging)
- **No JVM Startup**: The compiler stays loaded for the rest of the build
- **Isolation**: Each javatools runtime has its own classloader, apart from the plugin's and Gradle's

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
}
```

### Compilation Configuration

**Extension**: `XtcCompilerExtension`

**Note**: Module path is automatically resolved from your `xtcModule(...)` dependencies. Manual configuration is rarely needed.

```kotlin
import org.xtclang.plugin.launchers.ExecutionMode

xtcCompile {
    // Compiler verbosity
    verbose.set(true)

    // Show XTC version during compilation
    showVersion.set(true)

    // Execution mode: ATTACHED (a forked JVM, the default), DIRECT (the Gradle daemon) or DETACHED
    executionMode = ExecutionMode.ATTACHED

    // JVM arguments of the forked JVM (not used in DIRECT mode)
    jvmArgs("-Xmx2g", "-Xms512m")
}
```

`jvmArgs(...)` and `jvmArg(...)` add JVM arguments, and `setJvmArgs(...)` (or `jvmArgs.set(...)`) replaces them, on an
extension and on a task alike, as on Gradle's own `JavaExec` and `Test` tasks. A task starts from its extension's. The
list holds only the build script's arguments: the launched JVM also gets, before them, `-ea` while `enableAssertions`
is true, its default, and the plugin's own options, such as `--enable-native-access=ALL-UNNAMED`, which no
`setJvmArgs` removes. Turn assertions off with `enableAssertions = false` (`enableAssertions.set(false)`), as Gradle's
`enableAssertions` does.

```kotlin
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
    // The module to run - this is usually all you need! Its method is run by default
    moduleName("myapp")
}
```

**Full Configuration** (for advanced scenarios):
```kotlin
xtcRun {
    // A module, its method and its arguments
    module {
        moduleName = "myapp"
        methodName = "run"  // the default
        moduleArgs("arg1", "arg2")
    }

    // Custom module path (only if you need to override automatic resolution)
    modulePath.from(files("runtime/modules"))

    // JVM arguments of the forked JVM (not used in DIRECT mode)
    jvmArgs("-Xmx1g")

    // Execution mode
    executionMode = ExecutionMode.ATTACHED  // A forked JVM (default)
}
```

A task that names its own modules (`moduleName`, `moduleNames`, `module { }`) runs those instead of its extension's
(`xtcRun`'s, or `xtcTest`'s for a test task); a task that names none runs the extension's. Within the extension or a
task, those calls add, and `setModuleNames(...)`/`setModules(...)` replace.

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
    moduleName("myapp")
}
```

**Running Chosen Tests**:

`testXtc` runs all of the test module's tests unless its options choose some, by their paths in the
module; each option can be given more than once, and xunit runs the tests that any of them selects:

| Option | Effect |
|--------|--------|
| `--test-package=<path>` | Runs the tests of a package, such as `tests` |
| `--test-class=<path>` | Runs the tests of a class, such as `tests.MathTest` |
| `--test-method=<path>` | Runs one test method, such as `tests.MathTest.adds`, or a module-level test by its name |
| `--test-report=json` | Also prints xunit's `json` report, an event per line, for a tool that reads the execution from the output, such as an IDE; xunit refuses a report it does not know |

```bash
./gradlew testXtc --test-class=tests.MathTest --test-method=tests.OtherTest.works
./gradlew testXtc --module=MyAppTest --test-report=json
```

The editors' test runs in Gradle mode use these options.

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

**Common Scenarios**:

| Scenario | Configuration | Description |
|----------|---------------|-------------|
| Default, memory isolation | `ATTACHED` (default) | A forked JVM for each task, apart from the Gradle daemon |
| Fast local iteration | `DIRECT` | In the Gradle daemon, no JVM startup |
| Debugging compiler/plugin | `DIRECT` | Attach to the Gradle daemon |
| Debugging XTC code | `ATTACHED` + jvmArgs | JDWP remote debugging |
| Background services | `DETACHED` | Runs after Gradle exits |

**Execution Flow**:
```
executionMode
  ├─ ATTACHED (DEFAULT) → AttachedStrategy: forked JVM, wait for completion
  ├─ DIRECT             → DirectStrategy: in the Gradle daemon, isolated classloader
  └─ DETACHED           → DetachedStrategy: forked JVM, background execution
```

## Build Lifecycle

### Standard Build Flow

1. **Configuration Phase**:
   - Plugin creates source sets and tasks
   - Captures configuration-time data for cache compatibility
   - Registers javatools dependency

2. **Compilation Phase** (`compileXtc`):
   - Resolves full module path (XDK + dependencies + source sets)
   - Resolves javatools.jar
   - Runs the compiler in the task's execution mode
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

## IDE Model

`exportXtcLspModel` writes what the Ecstasy editors' language server needs to know of the build to
`.gradle/xtc/lsp-model.json` at the root project, one entry per source set: its sources, module
roots, resources, project dependencies and module path, its project's `xtcRun` and `xtcTest` run
settings, and the XDK that its project compiles and runs with. `prepareXtcLspModel` also processes
the resources first. The editors run either task when they import the build; neither compiles a
module.

A project's XDK is its `xdk` entry, the same in each of its source sets:

```json
"xdk": {
  "version": "0.4.4",
  "libraries": "file:/home/me/app/build/xtc/xdk/lib/",
  "modules": ["0f3a…", "…"],
  "javatools": "9c41…"
}
```

`version` is the version that the project's `xdkDistribution` or `xdk` dependency resolves to,
and `libraries` the directory where the plugin extracts that XDK's `ecstasy.xtc` and other core
modules for `compileXtc`, `runXtc` and `testXtc`. For an XDK from a repository, or of a file
dependency, which names no version and so has none, the export reads its distribution archive,
without extracting it, and gives the SHA-256 of each module file in it, in order, as `modules`,
and the revision of its `javatools.jar`, the compiler and runtime, as `javatools`: the SHA-256 of
a line `<name>=<SHA-256>` for each file in the jar, in name order, with `build-info.properties`
taken without its blank lines and its `git.` lines, which name the commit and branch the jar was
built at and so change with every commit, as javatools' own build leaves them out of its
classpath's normalization. A language server that compiles and runs modules itself, with the XDK
it bundles, compares those with its own to tell whether the project builds with the same XDK,
which a snapshot version alone cannot tell, as every build of it has the same. Where the export
cannot say what the XDK holds, the entry says why instead of `modules` and `javatools`:

- `project`: the path in the build tree of the project that makes the XDK, for one that a build
  of the build tree makes, as a composite build's included XDK is. The export neither builds it nor
  reads what an earlier build extracted, which may be another XDK by now.
- `problem`: why the import could not resolve the dependency, or fetch or read the archive. No such
  failure fails the export. For a dependency that does not resolve, `version` is the version it
  asks for. The export of a problem leaves a new token in `build/xtc/lsp/<source set>.xdk-retry`,
  which the next export reads as Gradle configures the build, so that it configures it again and
  resolves the XDK anew, rather than reuse a configuration cache entry that kept the failure.

A project without an XDK dependency, whose core modules come over its module path, has no `xdk`
entry, as an older plugin's model has none. `extractXdk` syncs the distribution into its
directory, so that the modules of an XDK extracted there before do not stay on the module path.

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
- **Parallel Execution**: Compiles multiple modules concurrently

These features are fully supported and should be enabled in your `gradle.properties`:
```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

### XTC-Specific Optimizations

**Run in the Gradle Daemon** (`DIRECT`, opt-in): no JVM startup, and the compiler stays loaded for the build:
```kotlin
xtcCompile {
    executionMode = ExecutionMode.DIRECT
}
```

Or for every task of a build, `-PxtcDefaultExecutionMode=DIRECT`. A compiler crash or an out-of-memory error then
takes the daemon down, see [Launcher System](#launcher-system).

**Optimize for CI Builds**:
```kotlin
xtcCompile {
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

**Error**: `Compilation failed with exit code: 1`

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

### Forked JVM Issues

**Error**: Forked process fails or hangs

**Solution**:
1. Check JVM arguments are valid:
   ```kotlin
   xtcRun {
       jvmArgs("-Xmx1g")  // Verify memory settings
   }
   ```
2. Enable verbose logging to see process output
3. Try `DIRECT` mode to isolate the issue:
   ```kotlin
   executionMode = ExecutionMode.DIRECT
   ```
4. For debugging, add JDWP args and attach debugger

### Module Path Conflicts

**Error**: `Duplicate module name: "..."`

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
4. **Newlines**: Always add newline at end of files (required by `AGENTS.md`)
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
