# Continuation: Execution Strategy Unification and DRY Refactoring

## Current State

The multi-module bundled `.xtc` support is functionally implemented on branch
`lagergren/multi-module-repository`. The javatools code (Bundler, BundledFileRepository,
DirRepository changes, LauncherOptions.BundlerOptions) is clean and tested. The plugin
and XDK build integration works but has significant code quality issues.

## Problems to Fix

### 1. XtcBundleTask is Inconsistent with Other Tasks

`XtcBundleTask` extends `XtcDefaultTask` instead of `XtcLauncherTask`, which means it
duplicates infrastructure that already exists in `XtcLauncherTask`:

| Duplicated in XtcBundleTask | Already in XtcLauncherTask |
|------------------------------|---------------------------|
| `executionMode` property | `executionMode` (line 59) |
| `verbose` property | `verbose` (line 57) |
| `projectDirectory` provider | `projectDirectory` (line 64) |
| `javaToolsClasspath` | `javaToolsConfig` (line 70) |
| `resolveJavaExecutable()` via System.getProperty | `toolchainExecutable` provider |

**The `resolveJavaExecutable()` in XtcBundleTask uses `System.getProperty("java.home")`
which completely bypasses Gradle's Java toolchain management. All other tasks use
`toolchainExecutable` from `XtcLauncherTask` which properly resolves through the
`JavaToolchainService`.**

### 2. Three Nearly Identical Strategy Factory Methods

Each task has its own factory method that constructs the same strategies:

```java
// XtcBundleTask:124
private ExecutionStrategy createStrategy(final ExecutionMode mode) { ... }

// XtcCompileTask:294
private ExecutionStrategy createCompileStrategy() { ... }

// XtcRunTask:162
private ExecutionStrategy createStrategy() { ... }
```

All three do the same thing: switch on ExecutionMode, create DirectStrategy or
AttachedStrategy with the same parameters. Only difference is DETACHED support
(Run supports it, Compile/Bundle throw).

### 3. Four Nearly Identical Execute Methods in ForkedStrategy

`ForkedStrategy` has four `execute()` methods that all follow the exact same pattern:

```
1. Build options from task
2. Build ProcessBuilder
3. Configure I/O
4. Start process
5. Copy streams if needed
6. Wait for process
7. Join stream threads
8. Return exit code
9. Handle IOException and InterruptedException
```

The ONLY differences are:
- Step 1: which `buildXxxOptions()` method to call
- Step 2: For bundle, uses raw parameters instead of `XtcLauncherTask` convenience method
- Step 3: For bundle, uses `configureIOForBundle()` instead of `configureIO(pb, task)`
- Error handling is inconsistent (compile throws, run returns -1, bundle returns EXIT_CODE_ERROR)

### 4. Duplicated Error Handling in DirectStrategy

`execute(XtcRunTask)` and `execute(XtcTestTask)` have identical error handling code
(LauncherException catch + generic Exception catch). `execute(XtcCompileTask)` and
`execute(XtcBundleTask)` only catch generic Exception.

### 5. Inconsistent Exit Codes and Error Handling

| Context | IOException | InterruptedException |
|---------|------------|---------------------|
| ForkedStrategy compile | `throw failure(e, ...)` | `EXIT_CODE_ERROR` (1) |
| ForkedStrategy run | `return -1` | `return -1` |
| ForkedStrategy test | `return -1` | `return -1` |
| ForkedStrategy bundle | `return EXIT_CODE_ERROR` | `return EXIT_CODE_ERROR` |

These should all use `EXIT_CODE_ERROR` consistently.

## Refactoring Plan

### Step 1: Make XtcBundleTask Extend XtcLauncherTask

**Why**: XtcLauncherTask provides all the infrastructure XtcBundleTask needs:
- `executionMode`, `verbose`, `projectDirectory` properties
- `javaToolsConfig` (replaces `javaToolsClasspath`)
- `toolchainExecutable` for proper Java toolchain resolution
- `resolveJavaTools()` for classpath
- Strategy factory infrastructure

**How**: XtcBundleTask needs to extend `XtcLauncherTask<E>`. This requires:
1. An extension type `E extends XtcLauncherTaskExtension` — create a minimal
   `XtcBundlerExtension` or use a generic one
2. Constructor takes `(ObjectFactory, Project, E)` like other launcher tasks
3. Implement `getJavaLauncherClassName()` returning `XTC_BUNDLER_CLASS_NAME`
4. Remove all duplicated properties (executionMode, verbose, projectDirectory,
   javaToolsClasspath, resolveJavaExecutable)

**Concern**: XtcLauncherTask requires `Project` in the constructor and captures
source sets, XDK contents dir, module dependencies, etc. XtcBundleTask doesn't
need most of this. Two options:

- **Option A**: Accept the overhead — XtcBundleTask gets some unused captured providers.
  Simple, no infrastructure changes. The extra captures are harmless.
- **Option B**: Extract a lighter-weight base between XtcDefaultTask and XtcLauncherTask
  that provides just the execution infrastructure. More refactoring but cleaner.

Recommendation: **Option A** for now. The overhead is negligible.

### Step 2: Move Strategy Factory to Base Class

Move the strategy creation into `XtcLauncherTask`:

```java
// In XtcLauncherTask:
protected ExecutionStrategy createStrategy() {
    return createStrategy(getExecutionMode().get());
}

protected ExecutionStrategy createStrategy(final ExecutionMode mode) {
    return switch (mode) {
        case DIRECT -> new DirectStrategy(logger);
        case ATTACHED -> new AttachedStrategy(logger, resolveToolchainExecutable());
        case DETACHED -> new DetachedStrategy(logger, resolveToolchainExecutable());
    };
}

private String resolveToolchainExecutable() {
    var executable = toolchainExecutable.getOrNull();
    if (executable == null) {
        throw failure("Java toolchain not configured for forked execution");
    }
    return executable;
}
```

Tasks that don't support DETACHED can override or restrict:

```java
// In XtcCompileTask:
@Override
protected ExecutionStrategy createStrategy(final ExecutionMode mode) {
    if (mode == ExecutionMode.DETACHED) {
        throw new UnsupportedOperationException("DETACHED not supported for compile");
    }
    return super.createStrategy(mode);
}
```

Delete `createCompileStrategy()`, `createStrategy()`, and `resolveJavaExecutable()`
from XtcCompileTask, XtcRunTask, and XtcBundleTask.

### Step 3: Unify ForkedStrategy Execute Methods

Replace four nearly identical execute methods with a single template method:

```java
// In ForkedStrategy:
private int executeForked(final XtcLauncherTask<?> task, final LauncherOptions options) {
    try {
        var pb = buildProcess(task, options.toCommandLine());
        var shouldCopyStreams = configureIO(pb, task);
        var process = pb.start();
        var streamThreads = shouldCopyStreams ? copyProcessStreams(process) : List.<Thread>of();
        var exitCode = waitForProcess(process);
        for (var thread : streamThreads) {
            thread.join();
        }
        return exitCode;
    } catch (IOException e) {
        logger.error("[plugin] Failed to start forked process", e);
        return EXIT_CODE_ERROR;
    } catch (InterruptedException e) {
        logger.error("[plugin] Forked process was interrupted", e);
        Thread.currentThread().interrupt();
        return EXIT_CODE_ERROR;
    }
}

@Override
public int execute(final XtcCompileTask task) {
    logger.info("[plugin] execute(XtcCompileTask): {}", getDesc());
    return executeForked(task, optionsBuilder().buildCompilerOptions(task));
}

@Override
public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
    logger.info("[plugin] execute(XtcRunTask): {}", getDesc());
    var moduleName = runConfig.getModuleName().get();
    var moduleArgs = runConfig.getModuleArgs().get();
    return executeForked(task, optionsBuilder().buildRunnerOptions(task, moduleName, moduleArgs));
}

@Override
public int execute(final XtcTestTask task, final XtcRunModule runConfig) {
    logger.info("[plugin] execute(XtcTestTask): {}", getDesc());
    var moduleName = runConfig.getModuleName().get();
    var moduleArgs = runConfig.getModuleArgs().get();
    return executeForked(task, optionsBuilder().buildTestRunnerOptions(task, moduleName, moduleArgs));
}

@Override
public int execute(final XtcBundleTask task) {
    logger.info("[plugin] execute(XtcBundleTask): {}", getDesc());
    return executeForked(task, optionsBuilder().buildBundlerOptions(task));
}
```

This eliminates ~80 lines of duplicated try/catch/process management and makes error
handling consistent.

**Note**: Once XtcBundleTask extends XtcLauncherTask, `buildProcess(task, args)` works
uniformly. No more special `buildProcess()` overload with raw parameters. The separate
`configureIOForBundle()` method can be removed.

### Step 4: Unify DirectStrategy Error Handling

Run and Test have identical `LauncherException` catch blocks. Consider extracting:

```java
private int executeDirect(final String desc, final DirectExecution execution) {
    logger.info("[plugin] Invoking {} directly in current thread (no fork)", desc);
    try {
        return execution.execute();
    } catch (final Launcher.LauncherException e) {
        logger.error("[plugin] Direct {} execution failed: {}", desc, e.getMessage());
        return e.getExitCode();
    } catch (final Exception e) {
        logger.error("[plugin] Direct {} execution failed", desc, e);
        return EXIT_CODE_ERROR;
    }
}
```

### Step 5: Clean Up configureIO

Once XtcBundleTask extends XtcLauncherTask, remove `configureIOForBundle()` entirely.
All tasks go through `configureIO(pb, task)`.

## Files to Modify

| File | Change |
|------|--------|
| `plugin/.../tasks/XtcBundleTask.java` | Extend XtcLauncherTask, remove duplicated properties |
| `plugin/.../tasks/XtcLauncherTask.java` | Add `createStrategy()` and `resolveToolchainExecutable()` |
| `plugin/.../tasks/XtcCompileTask.java` | Delete `createCompileStrategy()`, `resolveJavaExecutable()` |
| `plugin/.../tasks/XtcRunTask.java` | Delete `createStrategy()`, `resolveJavaExecutable()` |
| `plugin/.../launchers/ForkedStrategy.java` | Collapse four execute methods into `executeForked()` template |
| `plugin/.../launchers/DirectStrategy.java` | Unify error handling |
| `plugin/.../launchers/ForkedStrategy.java` | Remove `buildProcess()` raw-parameter overload, `configureIOForBundle()` |
| `plugin/.../XtcPluginConstants.java` | Already has `XTC_BUNDLER_CLASS_NAME` |

May also need:
| `plugin/.../XtcBundlerExtension.java` | New: minimal extension for bundle task (or reuse existing) |
| `xdk/build.gradle.kts` | Update bundle task registration if constructor changes |

## Verification After Refactoring

```bash
# 1. Plugin compiles
./gradlew javatools:jar

# 2. Javatools tests pass (includes BundlerTest)
./gradlew javatools:test

# 3. XDK builds with bundled xdk.xtc
./gradlew xdk:installDist

# 4. Config cache works
./gradlew xdk:installDist --info  # Should say "Configuration cache entry stored"

# 5. Manual verification of bundle
ls -la xdk/build/install/xdk/lib/xdk.xtc
```

## Order of Operations

1. Create `XtcBundlerExtension` (minimal, or identify existing reusable type)
2. Make `XtcBundleTask` extend `XtcLauncherTask<XtcBundlerExtension>`
3. Move `createStrategy()` + `resolveToolchainExecutable()` to `XtcLauncherTask`
4. Update `XtcCompileTask`, `XtcRunTask` to use inherited factory
5. Collapse `ForkedStrategy` execute methods into `executeForked()` template
6. Unify `DirectStrategy` error handling
7. Remove `configureIOForBundle()`, raw-parameter `buildProcess()` overload
8. Fix `xdk/build.gradle.kts` task registration if needed
9. Compile and test
