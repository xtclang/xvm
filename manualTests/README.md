# XTC Manual Tests

This is the **manualTests** project - a comprehensive testing suite and demonstration of the XTC Gradle plugin.

> **Important:** This is not your "Hello world" app playground. Please use another project directory and/or GitHub repository for that, with the XDK as either an included build, or as published artifacts.

## Purpose

ManualTests is a standalone XTC project which demonstrates and tests the complete XTC build lifecycle. It serves several purposes:

1. **Integration Testing**: Ensures we don't accidentally break external dependencies to the XDK artifacts
2. **Build Lifecycle Validation**: Verifies that the build lifecycle works as expected
3. **Plugin Testing**: Tests the XTC Gradle plugin in realistic scenarios
4. **Documentation**: Shows best practices for XTC project setup

## Project Structure

This project depends only on the XDK and demonstrates:

- **Plugin Application**: How to apply the XTC Gradle plugin
- **Dependencies Management**: Using version catalogs and dependency resolution
- **Source Sets Configuration**: Organizing XTC source code
- **Task Configuration**: Setting up compilation and runtime tasks
- **Build Lifecycle**: Following Gradle best practices

## Plugin Configuration

The XTC plugin adds two main extensions to your project:

### `xtcCompile` Extension

Contains common configuration for all XTC compilation tasks in the project:

```kotlin
xtcCompile {
    showVersion = false        // Display XTC runtime version
    verbose = false           // Run in verbose mode  
    debug = false            // Enable debugging (suspends and waits for debugger)
    fork = true              // Run in separate process (recommended for production)
    rebuild = false          // Force recompilation regardless of input changes
}
```

### `xtcRun` Extension  

Contains common configuration for all XTC runner tasks:

```kotlin
xtcRun {
    showVersion = false           // Display version information
    verbose = true               // Run in verbose mode
    useNativeLauncher = false    // Use native XTC launcher (requires local XDK installation)
    
    // JVM arguments for forked processes
    jvmArgs("-showversion", "--enable-preview")
    
    // Default module to run
    module {
        moduleName = "EchoTest"
        methodName = "run"      // Entry point method
        moduleArgs("Hello", "World")  // Arguments passed to module
    }
}
```

## Available Tasks

The XTC plugin automatically creates tasks for each source set:

### Compilation Tasks

- `compileXtc` - Compiles the main source set
- `compileTestXtc` - Compiles the test source set  
- `compile<SourceSet>Xtc` - Compiles any custom source sets

### Runtime Tasks

- `runXtc` - Runs modules defined in the `xtcRun` extension
- Custom run tasks can be created as needed

## Custom Task Examples

### Sequential Test Runner

```kotlin
val runTwoTestsInSequence by tasks.registering(XtcRunTask::class) {
    group = "application"
    verbose = true
    
    module {
        moduleName = "EchoTest"
        moduleArg("Hello")
        moduleArg(provider { System.getProperty("user.name") ?: "unknown user" })
    }
    moduleName("TestArray")  // Shorthand for simple modules
}
```

### Parameterized Test Runner

```kotlin
val runOne by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Runs one test as given by the property 'testName', or a default test if not set."
    
    module {
        moduleName = resolveTestNameProperty() // Resolved from -PtestName=...
    }
}
```

Usage: `./gradlew manualTests:runOne -PtestName="TestArray"`

### Parallel Test Runner

```kotlin
val runParallel by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Run all known tests in parallel through the parallel test runner."
    
    module {
        moduleName = "Runner"
        verbose = false
        moduleArgs(
            "TestAnnotations", "TestArray", "TestCollections",
            // ... all test module names
        )
    }
}
```

## Key Gradle Concepts for XTC

### Build Cache and Task Up-to-Date Checking

XTC respects Gradle's caching semantics:

- **Incremental Builds**: Tasks run only when inputs change
- **Build Cache**: Reuses outputs across builds and machines when inputs are identical  
- **Up-to-Date Checking**: Skips tasks when nothing has changed

**Important**: `./gradlew clean && ./gradlew build` is an **anti-pattern**. If your build requires this to work properly, that's a bug that should be reported.

### Debugging and Development

For debugging XTC compilation or runtime:

```kotlin
xtcCompile {
    debug = true        // Suspends and waits for debugger
    debugPort = 5005    // Port for debugger connection
    debugSuspend = true // Wait for debugger before proceeding
}
```

For development builds:

```kotlin
xtcCompile {
    fork = false  // Run in build process thread (enables IDE debugging but is brittle)
}
```

### I/O Stream Redirection

You can redirect standard input/output for XTC tasks:

```kotlin
xtcRun {
    stdin = System.`in`     // Redirect stdin (useful for interactive debugging)
    // stdout and stderr can also be redirected
}
```

## Dependencies and Version Catalogs

This project uses Gradle's version catalog mechanism:

```kotlin
plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xtc)
}

dependencies {
    xdk(libs.xdk)  // XDK dependency resolved from version catalog
}
```

The version catalog is located at `gradle/libs.versions.toml` in the root directory.

## Source Sets Configuration

XTC source sets work like standard Gradle source sets:

```kotlin
sourceSets {
    main {
        xtc {
            // Include/exclude patterns
            exclude("**/archive/**")
            exclude("**/TestSimple.x")
        }
    }
}
```

## Running the Tests

From the command line:

```bash
# Compile all XTC sources
./gradlew :manualTests:build

# Run default modules  
./gradlew :manualTests:runXtc

# Run custom test tasks
./gradlew :manualTests:runTwoTestsInSequence
./gradlew :manualTests:runParallel  
./gradlew :manualTests:runOne -PtestName="TestArray"

# Run all test tasks
./gradlew :manualTests:runAllTestTasks
```

## Best Practices

1. **Use Gradle Build Cache**: Enable with `--build-cache` or in `gradle.properties`
2. **Leverage Incremental Builds**: Don't use `clean` unless necessary
3. **Use Providers**: For lazy evaluation and configuration cache compatibility
4. **Follow Task Dependencies**: Let Gradle determine build order
5. **Use Version Catalogs**: For consistent dependency management

## Troubleshooting

### Getting More Information

```bash
# See why tasks are re-run, skipped, or cached
./gradlew build --info

# See task inputs and outputs  
./gradlew build --info --debug

# Scan for performance issues
./gradlew build --scan
```

### Common Issues

- **Tasks not up-to-date**: Check if inputs are properly declared
- **Build cache misses**: Verify task inputs are deterministic
- **Configuration cache issues**: Ensure no Project references during execution

## Configuration Cache Compatibility  

This project is fully compatible with Gradle's configuration cache:

```bash
# Enable configuration cache
./gradlew build --configuration-cache

# Test different cache scenarios
./gradlew build --no-configuration-cache --no-build-cache --no-daemon
```

See the [Configuration Cache Testing Guide](../test-configuration-cache.sh) for comprehensive testing scenarios.
