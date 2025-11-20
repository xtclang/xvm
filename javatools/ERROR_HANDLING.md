# Launcher Error Handling Architecture

This document describes the error handling, logging, and abort mechanisms in the XVM launcher tools (Compiler, Runner, Disassembler).

## Table of Contents
1. [Overview](#overview)
2. [Core Components](#core-components)
3. [Error Flow Paths](#error-flow-paths)
4. [Using Error Handling (Plugin Integration)](#using-error-handling-plugin-integration)
5. [Examples](#examples)

---

## Overview

The launcher tools use an **error accumulation pattern** where errors are logged and tracked, then checked at specific checkpoints. This allows compilation/execution to continue collecting multiple errors before deciding whether to abort.

### Key Principles

1. **`log()` never throws** - it only logs and updates worst severity
2. **Errors accumulate** in `m_sevWorst` field
3. **`checkErrors()` decides** whether to abort based on accumulated severity
4. **`logAndAbort()` throws immediately** - use for unrecoverable errors
5. **`LauncherException` propagates** abort decisions up the call stack

---

## Core Components

### 1. Severity Levels

```java
public enum Severity {
    NONE,    // No error
    INFO,    // Informational message
    WARNING, // Warning, but not an error
    ERROR,   // Error that should stop execution
    FATAL    // Fatal error, immediate stop
}
```

**Printing Threshold**: `WARNING` and above (unless verbose mode)
**Abort Threshold**:
- Base Launcher: `ERROR` and above
- Compiler (Stickler mode): `WARNING` and above

### 2. Logging Methods

#### Basic Logging
```java
protected void log(Severity sev, String template, Object... params)
```
- Logs a message with SLF4J-style `{}` placeholders
- Updates `m_sevWorst` if this severity is worse
- Prints to console if severity ≥ `isBadEnoughToPrint()` threshold
- **Never throws exceptions**

#### Logging with Throwable
```java
protected void log(Severity sev, Throwable cause, String template, Object... params)
```
- Same as basic logging, but includes exception information
- If template is null/empty, uses exception message
- Otherwise appends exception message to formatted template
- **Never throws exceptions**

#### Logging ErrorList
```java
protected void log(ErrorList errs)
```
- Logs all errors from an `ErrorList`
- Delegates to basic `log()` for each error
- **Never throws exceptions**

### 3. Abort Methods

#### Explicit Abort
```java
protected LauncherException logAndAbort(Severity sev, String template, Object... params)
```
- Logs the error message
- **Immediately throws LauncherException**
- Use when error is unrecoverable and you want explicit abort

#### Explicit Abort with Throwable
```java
protected LauncherException logAndAbort(Severity sev, Throwable cause, String template, Object... params)
```
- Logs error with exception information
- **Immediately throws LauncherException with cause**
- Use for wrapping IOException, etc.

### 4. Error Checking

#### Check Accumulated Errors
```java
protected void checkErrors()
```
- Checks if `m_sevWorst` meets abort threshold (`isBadEnoughToAbort()`)
- **Throws LauncherException if threshold exceeded**
- **Does nothing if errors are within acceptable range**
- This is the primary checkpoint mechanism

#### Flush and Check
```java
protected void flushAndCheckErrors(Node[] nodes)
```
- Flushes deferred errors from AST nodes
- Calls `checkErrors()`
- Used in Compiler after each compilation phase

### 5. LauncherException

```java
public static class LauncherException extends RuntimeException {
    private final boolean error;
    private final int exitCode;
}
```

- Extends `RuntimeException` (unchecked exception)
- Used to signal that launcher should abort
- Contains exit code for process termination
- **Should propagate up to top-level error handler**

---

## Error Flow Paths

### Path 1: Normal Error Accumulation (Compiler)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Error Occurs                                             │
│    log(ERROR, "Syntax error: {}", detail)                   │
│    → Updates m_sevWorst = ERROR                             │
│    → Prints to console                                      │
│    → Returns normally                                       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. More Errors Occur                                        │
│    log(WARNING, "Unused variable")                          │
│    log(ERROR, "Type mismatch")                              │
│    → m_sevWorst remains ERROR                               │
│    → All printed to console                                 │
│    → Execution continues                                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Checkpoint Reached                                       │
│    flushAndCheckErrors(nodes)                               │
│    → Flushes deferred errors from nodes                     │
│    → Calls checkErrors()                                    │
│    → isBadEnoughToAbort(ERROR) = true                       │
│    → **THROWS LauncherException**                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Exception Caught by Launcher.run()                      │
│    → Returns exit code from LauncherException               │
│    → Process terminates with non-zero exit                  │
└─────────────────────────────────────────────────────────────┘
```

### Path 2: Immediate Fatal Error

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Unrecoverable Error Occurs                               │
│    try {                                                    │
│        repo.storeModule(module);                            │
│    } catch (IOException e) {                                │
│        log(FATAL, e, "I/O error: {}", file);                │
│        // DO NOT use logAndAbort here!                      │
│        // Let checkErrors() handle it                       │
│    }                                                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Checkpoint Detects Fatal Error                           │
│    checkErrors()                                            │
│    → isBadEnoughToAbort(FATAL) = true                       │
│    → **THROWS LauncherException**                           │
└─────────────────────────────────────────────────────────────┘
```

### Path 3: Explicit Abort (Use Sparingly)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Logic Error Detected                                     │
│    if (missingRequiredOption) {                             │
│        throw logAndAbort(ERROR, "Missing: {}", opt);        │
│    }                                                        │
│    → Logs error                                             │
│    → **IMMEDIATELY THROWS LauncherException**               │
└─────────────────────────────────────────────────────────────┘
```

**⚠️ Warning**: Only use `logAndAbort()` when:
- Error is unrecoverable and continuing makes no sense
- NOT inside a try block that catches `Exception`/`Throwable` (will cause double-wrapping)
- You want to abort immediately without collecting more errors

### Path 4: Runner/Disassembler Pattern

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Error During Execution                                   │
│    try {                                                    │
│        connector.invoke0(method, args);                     │
│        return connector.join();                             │
│    } catch (IOException e) {                                │
│        log(FATAL, e, "I/O exception: {}", file);            │
│        return 1; // Error exit code                         │
│    } catch (Exception e) {                                  │
│        // Won't catch LauncherException                     │
│        log(FATAL, e, "Unexpected error");                   │
│        return 1;                                            │
│    }                                                        │
└─────────────────────────────────────────────────────────────┘
```

**Key Pattern**: Log errors and return error code. `LauncherException` propagates past these catches.

---

## Using Error Handling (Plugin Integration)

### Console Integration

Plugins should provide a `Console` implementation to capture log output:

```java
public class CustomConsole implements Console {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void log(Severity sev, String sMsg) {
        // Capture for IDE/build tool display
        messages.add(sev.desc() + ": " + sMsg);
    }

    @Override
    public void out(Object o) {
        // Handle stdout
    }

    @Override
    public void err(Object o) {
        // Handle stderr
    }
}
```

**Usage**:
```java
Console console = new CustomConsole();
CompilerOptions opts = /* ... */;
Compiler compiler = new Compiler(opts, console, null);
int exitCode = compiler.run();
```

### ErrorListener Integration

Plugins should provide an `ErrorListener` to capture compiler errors:

```java
public class CustomErrorListener implements ErrorListener {
    private Severity worstSeverity = Severity.NONE;

    @Override
    public boolean log(ErrorInfo err) {
        // Capture error details
        String code = err.getCode();
        Severity sev = err.getSeverity();
        String message = err.toString();

        // Track worst severity
        if (sev.compareTo(worstSeverity) > 0) {
            worstSeverity = sev;
        }

        // Return false to continue (don't abort from ErrorListener)
        return false;
    }

    public boolean hasErrors() {
        return worstSeverity.compareTo(Severity.ERROR) >= 0;
    }
}
```

**Usage**:
```java
Console console = new CustomConsole();
CustomErrorListener errorListener = new CustomErrorListener();
CompilerOptions opts = /* ... */;
Compiler compiler = new Compiler(opts, console, errorListener);

try {
    int exitCode = compiler.run();
    if (exitCode != 0 || errorListener.hasErrors()) {
        // Handle compilation failure
    }
} catch (LauncherException e) {
    // Compilation aborted
    // Error already logged via Console/ErrorListener
}
```

### Gradle Plugin Pattern

```java
public class CompileTask extends DefaultTask {

    @TaskAction
    public void compile() {
        // Create console that feeds into Gradle's logging
        Console console = new Console() {
            @Override
            public void log(Severity sev, String sMsg) {
                switch (sev) {
                    case INFO -> getLogger().info(sMsg);
                    case WARNING -> getLogger().warn(sMsg);
                    case ERROR, FATAL -> getLogger().error(sMsg);
                }
            }
            // ...
        };

        // Create error listener
        CustomErrorListener errorListener = new CustomErrorListener();

        // Run compiler
        CompilerOptions opts = buildOptions();
        Compiler compiler = new Compiler(opts, console, errorListener);

        try {
            int exitCode = compiler.run();
            if (exitCode != 0) {
                throw new GradleException("Compilation failed");
            }
        } catch (LauncherException e) {
            throw new GradleException("Compilation aborted: " + e.getMessage(), e);
        }
    }
}
```

---

## Examples

### Example 1: Compiler Checkpoints

```java
// Compiler.process() method
protected int process() {
    // Phase 1: Parse and populate namespace
    var mapCompilers = populateNamespace(allNodes, repoLib);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    // Phase 2: Link modules
    linkModules(compilers, repoLib);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    // Phase 3: Resolve names
    resolveNames(compilers);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    // Phase 4: Validate
    validateExpressions(compilers);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    // Phase 5: Generate code
    generateCode(compilers);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    // Phase 6: Emit modules
    emitModules(allNodes, repoOutput);
    flushAndCheckErrors(allNodes);  // ← Checkpoint: abort if errors

    return 0; // Success
}
```

### Example 2: Error During I/O

```java
try {
    repo.storeModule(module);
} catch (IOException e) {
    // Log the error with exception details
    log(FATAL, e, "I/O exception storing module: {}", moduleName);
    // Return or continue - checkErrors() will abort at next checkpoint
    return null;
}
```

### Example 3: Validation Error

```java
if (setMethods.size() != 1) {
    // Log the error
    log(ERROR, "{} method {} in module {}",
        setMethods.isEmpty() ? "Missing" : "Ambiguous",
        quoted(methodName),
        quoted(moduleName));
    // Return error code - don't throw
    return 1;
}
```

### Example 4: xRTCompiler (Runtime Compiler)

The xRTCompiler overrides logging to capture errors in a list instead of aborting:

```java
public class CompilerAdapter extends Compiler {
    private final List<String> m_log = new ArrayList<>();
    private Severity m_sevWorst = Severity.NONE;

    @Override
    protected void log(Severity sev, String msg, Object... params) {
        // Update worst severity
        if (sev.compareTo(m_sevWorst) > 0) {
            m_sevWorst = sev;
        }
        // Capture error message
        if (sev.compareTo(Severity.WARNING) >= 0) {
            m_log.add(sev.desc() + ": " + Console.formatTemplate(msg, params));
        }
    }

    public boolean hasErrors() {
        return m_sevWorst.compareTo(Severity.ERROR) >= 0;
    }

    public List<String> getErrors() {
        return m_log;
    }
}
```

The runtime catches `LauncherException` and converts it to an error list:

```java
try {
    String sMissing = compiler.partialCompile(fReenter);
    // ... compilation logic ...
    return completeCompilation(frame, compiler, null, aiReturn);
} catch (Exception e) {
    // Catches LauncherException from checkErrors()
    return completeCompilation(frame, compiler, e, aiReturn);
}
```

---

## Best Practices

### ✅ DO

1. **Use `log()` for all errors** - let error accumulation work
2. **Call `checkErrors()` at logical checkpoints** - after phases complete
3. **Return error codes** from `process()` methods
4. **Let `LauncherException` propagate** - don't catch it unless at top level
5. **Provide `Console` and `ErrorListener`** when using launcher tools programmatically
6. **Use `log(sev, throwable, template, params)`** for exception logging

### ❌ DON'T

1. **Don't use `logAndAbort()` inside try blocks** that catch `Exception`/`Throwable`
2. **Don't catch `LauncherException`** in the middle of processing
3. **Don't call `log()` then immediately throw** - let checkpoints handle it
4. **Don't assume `log(FATAL, ...)` will abort** - it only logs
5. **Don't manually check `m_sevWorst`** - use `checkErrors()`

### 🔄 Migration from Old Pattern

**Old (log threw exception)**:
```java
log(FATAL, "Error occurred");  // Would throw
// Never reached
```

**New (log accumulates, checkpoint throws)**:
```java
log(FATAL, "Error occurred");  // Just logs
// Execution continues...
checkErrors();  // This throws if FATAL
```

---

## Summary

The XVM launcher error handling provides:

1. **Error accumulation** - collect multiple errors before aborting
2. **Explicit checkpoints** - `checkErrors()` decides when to abort
3. **Clean separation** - `log()` never throws, `logAndAbort()` always throws
4. **Pluggable output** - `Console` and `ErrorListener` for IDE/build tool integration
5. **Exception propagation** - `LauncherException` carries abort decision up the stack

This architecture enables both CLI usage (immediate console output) and programmatic usage (capture errors for IDE display) with the same codebase.