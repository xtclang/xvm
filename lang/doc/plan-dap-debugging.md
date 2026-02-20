# XTC Debug Adapter Protocol (DAP): Implementation Plan

This document details how DAP-based debugging works end-to-end for XTC/Ecstasy, how the existing runtime debugger infrastructure maps to DAP, and the concrete implementation plan for connecting the scaffolded `dap-server` module to the XTC runtime. It covers the full lifecycle from "user clicks a line number in VS Code" through to "breakpoint fires and variables are displayed."

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [How Line Breakpoints Work End-to-End](#2-how-line-breakpoints-work-end-to-end)
3. [Existing XTC Debug Infrastructure](#3-existing-xtc-debug-infrastructure)
4. [DAP Protocol Lifecycle](#4-dap-protocol-lifecycle)
5. [DAP-to-Runtime Bridge: The `DapDebugger`](#5-dap-to-runtime-bridge-the-dapdebugger)
6. [Stack Frames, Variables, and Scopes](#6-stack-frames-variables-and-scopes)
7. [Expression Evaluation](#7-expression-evaluation)
8. [Exception Breakpoints](#8-exception-breakpoints)
9. [XTC-Specific Considerations](#9-xtc-specific-considerations)
10. [DAP and LSP Coordination](#10-dap-and-lsp-coordination)
11. [Implementation Phases](#11-implementation-phases)

---

## 1. Architecture Overview

```
  IDE (VS Code / IntelliJ)
  ┌────────────────────────────────────────┐
  │  Editor         │  Debug Panel         │
  │  ┌──────────┐   │  ┌───────────────┐   │
  │  │ .x files │   │  │ Call Stack    │   │
  │  │ red dots │   │  │ Variables     │   │
  │  │ (bp gutter)  │  │ Watch         │   │
  │  └──────────┘   │  │ Breakpoints   │   │
  │       │         │  └───────┬───────┘   │
  │       │ LSP     │          │ DAP       │
  └───────┼─────────┼──────────┼───────────┘
          │         │          │
    ┌─────▼──────┐  │  ┌───────▼────────┐
    │ LSP Server │  │  │ DAP Server     │
    │ (lsp-server│  │  │ (dap-server)   │
    │  module)   │  │  │                │
    └────────────┘  │  └───────┬────────┘
                    │          │  Debugger interface
                    │  ┌───────▼────────┐
                    │  │ XTC Runtime    │
                    │  │ ┌────────────┐ │
                    │  │ │ Interpreter│ │
                    │  │ │ Frame/Fiber│ │
                    │  │ │ Op[] array │ │
                    │  │ └────────────┘ │
                    │  └────────────────┘
```

**Key insight**: LSP and DAP are **separate protocols** running in **separate processes**. The IDE manages two independent connections:

- **LSP** (Language Server Protocol): Provides code intelligence — syntax highlighting, completions, navigation, diagnostics. Runs continuously while the editor is open.
- **DAP** (Debug Adapter Protocol): Provides debugging — breakpoints, stepping, variable inspection. Runs only during debug sessions.

They do **not** need to communicate with each other directly. The IDE coordinates both.

---

## 2. How Line Breakpoints Work End-to-End

This is the core question: "How does a red dot in the IDE gutter end up pausing XTC execution?"

### Step-by-Step Flow

```
Step 1: User clicks gutter at line 42 of MyService.x
        IDE displays red dot (purely UI — no protocol involved yet)

Step 2: User presses F5 (Start Debugging)
        IDE spawns the DAP server process
        IDE sends: initialize → launch → setBreakpoints → configurationDone

Step 3: setBreakpoints request arrives at DAP server
        {
          "source": { "path": "/project/src/MyService.x" },
          "breakpoints": [{ "line": 42 }]
        }

Step 4: DAP server translates file path + line 42 to runtime coordinates
        → Find the MethodStructure whose source spans line 42
        → Use MethodStructure.calculateLineNumber() to find the iPC
           where Nop with accumulated line count == 42
        → Register a BreakPoint(className, lineNumber) in the Debugger

Step 5: DAP server responds to IDE
        { "breakpoints": [{ "id": 1, "verified": true, "line": 42 }] }
        (or verified: false if the line has no executable code)

Step 6: XTC runtime executes MyService code
        ServiceContext.execute() runs the op loop:
          iPC = aOp[iPC].process(frame, iPCLast = iPC)

        At every Nop (LINE_N) op, when debugger is active:
          Nop.process() → context.getDebugger().checkBreakPoint(frame, iPC)

        The DapDebugger checks: does this frame+iPC match any registered breakpoint?
        It uses MethodStructure.calculateLineNumber(iPC) to get the source line.

Step 7: Breakpoint matches! DapDebugger suspends the fiber
        → Blocks the current ServiceContext thread on a CountDownLatch/lock
        → Sends DAP "stopped" event to IDE:
          { "event": "stopped", "body": { "reason": "breakpoint", "threadId": 1 } }

Step 8: IDE receives "stopped" event
        → Requests stackTrace, scopes, variables
        → Renders call stack, local variables, watches
        → Highlights line 42 in the editor with yellow background

Step 9: User inspects variables, evaluates expressions, then clicks "Continue"
        IDE sends: continue request
        → DapDebugger releases the latch
        → Interpreter loop resumes from iPC + 1
```

### Source Line ↔ Bytecode Mapping

The XTC compiler embeds line information directly in the bytecode via `Nop` ops (opcodes `LINE_1` through `LINE_N`). Each Nop carries a delta to the current line number. To find the source line for any program counter:

```java
// MethodStructure.calculateLineNumber(int iPC)
public int calculateLineNumber(int iPC) {
    int nLine = 1;
    Op[] aOp = getOps();
    for (int i = 0; i <= iPC; i++) {
        Op op = aOp[i].ensureOp();
        if (op instanceof Nop nop) {
            nLine += nop.getLineCount();
        }
    }
    return nLine == 1 ? 0 : nLine;
}
```

This is how the debugger maps between "line 42 in the editor" and "iPC 137 in the bytecode."

---

## 3. Existing XTC Debug Infrastructure

The XTC runtime already has a **complete console debugger**. This is the foundation — the DAP server is essentially a protocol translator that exposes this existing functionality over JSON-RPC.

### 3.1 The `Debugger` Interface

**File**: `javatools/src/main/java/org/xvm/runtime/Debugger.java`

A clean 4-method interface:

```java
public interface Debugger {
    // Called by assert:debug to activate the debugger
    int activate(Frame frame, int iPC);

    // Called at every Nop (LINE_N) op when debugger is active
    int checkBreakPoint(Frame frame, int iPC);

    // Called when a frame returns (for step-out detection)
    void onReturn(Frame frame);

    // Called when an exception is thrown (for exception breakpoints)
    int checkBreakPoint(Frame frame, ExceptionHandle hEx);
}
```

The return value is either a positive iPC (next instruction to execute) or a negative `Op.R_*` control value. When the debugger wants to pause, it blocks the thread and returns `iPC + 1` when resumed.

### 3.2 `DebugConsole` — What Already Works

**File**: `javatools/src/main/java/org/xvm/runtime/DebugConsole.java` (~2590 lines)

The existing console debugger is a full implementation of the `Debugger` interface:

| Feature | Status | DAP Mapping |
|---------|--------|-------------|
| Line breakpoints (`B+ <line>`) | Fully implemented | `setBreakpoints` |
| Conditional breakpoints (`BC <line> <cond>`) | Fully implemented | `setBreakpoints` with `condition` |
| Exception breakpoints (`BE+ <type>`) | Fully implemented | `setExceptionBreakpoints` |
| Break on all exceptions (`BE+ *`) | Fully implemented | `setExceptionBreakpoints` filters |
| Step over (`S` / `N`) | Fully implemented | `next` |
| Step into (`S+` / `I`) | Fully implemented | `stepIn` |
| Step out (`S-` / `O`) | Fully implemented | `stepOut` |
| Run to line (`SL <line>`) | Fully implemented | `goto` / `stepIn` with target |
| Continue (`R`) | Fully implemented | `continue` |
| Frame navigation (`F <n>`) | Fully implemented | `stackTrace` |
| Variable inspection (`X <n>`, `D <n>`) | Fully implemented | `variables` / `scopes` |
| Expression evaluation (`E <expr>`) | Fully implemented | `evaluate` |
| Watch expressions (`WO`, `WR`) | Partial (WE TODO) | `evaluate` in watch context |
| Stack traces | Fully implemented | `stackTrace` |
| Source display | Fully implemented | `source` |

**Step modes** (enum `StepMode`):
- `None` — running, only breakpoints trigger
- `StepOver` — stop at next Nop in same frame
- `StepInto` — stop at next Nop in any associated frame
- `StepOut` — stop when frame returns
- `StepLine` — run to specific line
- `NaturalCall` / `NaturalReturn` — internal states for debugger-initiated eval

### 3.3 Debug Hooks in the Execution Loop

**File**: `javatools/src/main/java/org/xvm/runtime/ServiceContext.java`

The interpreter loop has three debug hook points:

1. **At every `Nop` op** (line mapping): `Nop.process()` calls `checkBreakPoint(frame, iPC)`
2. **On frame return** (`R_RETURN`): calls `getDebugger().onReturn(frame)` for step-out detection
3. **On exception** (`R_EXCEPTION`): calls `getDebugger().checkBreakPoint(frame, hException)` for exception breakpoints

The debugger flag (`isDebuggerActive()`) is checked at each hook. When active, it also **disables the yield mechanism** (`MAX_OPS_PER_RUN` check is skipped), ensuring the debugger can step through code without being preempted.

### 3.4 `assert:debug` — Language-Level Breakpoints

XTC has a built-in `assert:debug` statement that compiles to an `Assert` op with `m_nConstructor == A_IGNORE`. When executed:

```java
// Assert.java
if (m_nConstructor == A_IGNORE) {
    return frame.f_context.getDebugger().activate(frame, iPC);
}
```

This is equivalent to putting a `debugger;` statement in JavaScript. The DAP server should treat this as a `"reason": "step"` stopped event.

### 3.5 Source Text Availability

`MethodStructure` stores the full source text of each method via a `Source` object:
- `getSourceText()` — raw source
- `getSourceLineNumber()` — 0-based file offset
- `getSourceLineCount()` — method line count
- `getSourceLines(first, count, trim)` — render specific lines
- Source file name from `ClassStructure`

This means the DAP server can provide source text even if the original `.x` file is not available on disk (e.g., for library code).

### 3.6 Frame and Variable Introspection

**`Frame`** (`javatools/src/main/java/org/xvm/runtime/Frame.java`):
- `f_function` — the `MethodStructure` being executed
- `f_aOp` — the bytecode op array
- `f_ahVar` — register/variable values (`ObjectHandle[]`)
- `f_aInfo` — type info per register (`VarInfo[]`)
- `f_framePrev` — caller frame (linked list for stack traces)
- `m_iPC` — current program counter
- `getVarInfo(nVar)` — type/name info for a register
- `getStackTrace()` / `getStackTraceArray()` — human-readable stack

**`VarInfo`** (inner class of Frame):
- `getType()` — the `TypeConstant` of the variable
- `getName()` — variable name (from constant pool)
- `isStandardVar()`, `isDynamicVar()`, `isFuture()` — variable style

**`Fiber`**:
- `getFrame()` — current frame for this fiber
- `traceCaller()` — follow cross-service call chain
- `FiberStatus` — Initial, Running, Waiting, Paused, Terminating

---

## 4. DAP Protocol Lifecycle

### 4.1 Session Initialization

```
IDE → DAP:  initialize({ clientID: "vscode", ... })
DAP → IDE:  { capabilities: { supportsConfigurationDoneRequest: true, ... } }

IDE → DAP:  launch({ program: "MyApp.xtc", ... })
            -- or --
            attach({ port: 9229, ... })
DAP → IDE:  initialized event

IDE → DAP:  setBreakpoints({ source: {...}, breakpoints: [...] })
IDE → DAP:  setExceptionBreakpoints({ filters: [...] })
IDE → DAP:  configurationDone
```

### 4.2 Capabilities to Advertise

```kotlin
Capabilities().apply {
    supportsConfigurationDoneRequest = true
    supportsConditionalBreakpoints = true        // DebugConsole BC command
    supportsEvaluateForHovers = true             // EvalCompiler
    supportsSetVariable = false                  // not yet (runtime is immutable-heavy)
    supportsStepBack = false
    supportsRestartRequest = false
    supportsExceptionInfoRequest = true          // DebugConsole has full exception info
    supportsExceptionFilterOptions = true
    supportsTerminateRequest = true
    supportsSingleThreadExecutionRequests = true // XTC services are single-threaded

    exceptionBreakpointFilters = arrayOf(
        ExceptionBreakpointsFilter().apply {
            filter = "all"
            label = "All Exceptions"
            default_ = false
        },
        ExceptionBreakpointsFilter().apply {
            filter = "uncaught"
            label = "Uncaught Exceptions"
            default_ = true
        }
    )
}
```

### 4.3 Stopped Events

When the debugger hits a breakpoint or step target, it sends:

```json
{
  "event": "stopped",
  "body": {
    "reason": "breakpoint",   // or "step", "exception", "pause", "entry"
    "threadId": 1,
    "allThreadsStopped": true
  }
}
```

`allThreadsStopped: true` because `DebugConsole.checkBreakPoint()` is `synchronized`, freezing all services while debugging. This matches the existing behavior.

---

## 5. DAP-to-Runtime Bridge: The `DapDebugger`

The central implementation task: create a new `Debugger` implementation that translates between DAP JSON-RPC and the XTC runtime.

### 5.1 Architecture

```kotlin
/**
 * DAP-aware Debugger implementation that replaces DebugConsole
 * for IDE debug sessions.
 */
class DapDebugger(
    private val client: IDebugProtocolClient
) : Debugger {

    // Breakpoint storage (mirrors DebugConsole's m_setLineBreaks)
    private val lineBreakpoints = ConcurrentHashMap<String, MutableSet<DapBreakPoint>>()
    private val exceptionBreakpoints = ConcurrentHashMap<String, Boolean>()
    private var breakOnAllExceptions = false

    // Step state (mirrors DebugConsole's m_stepMode, m_frame)
    @Volatile private var stepMode = StepMode.None
    @Volatile private var stepFrame: Frame? = null

    // Thread suspension (replaces DebugConsole's synchronized blocking)
    private val suspendLatch = ConcurrentHashMap<Long, CountDownLatch>()
    private val suspendedFrames = ConcurrentHashMap<Long, Frame>()
}
```

### 5.2 The `checkBreakPoint` Implementation

This is where the magic happens — called at every `Nop` op:

```kotlin
override fun checkBreakPoint(frame: Frame, iPC: Int): Int {
    val method = frame.f_function
    val lineNumber = method.calculateLineNumber(iPC)
    val sourceName = getSourceName(frame)

    var shouldStop = false
    var reason = "breakpoint"

    // Check step mode first (mirrors DebugConsole.checkBreakPoint logic)
    when (stepMode) {
        StepMode.StepOver -> {
            if (frame === stepFrame) {
                shouldStop = true
                reason = "step"
            }
        }
        StepMode.StepInto -> {
            if (frame.f_fiber.isAssociated(stepFrame!!.f_fiber)) {
                shouldStop = true
                reason = "step"
            }
        }
        StepMode.StepLine -> {
            if (frame === stepFrame && iPC == stepTargetPC) {
                shouldStop = true
                reason = "step"
            }
        }
        StepMode.None -> {
            // Check line breakpoints
            val bps = lineBreakpoints[sourceName]
            if (bps != null) {
                for (bp in bps) {
                    if (bp.matches(lineNumber)) {
                        shouldStop = true
                        break
                    }
                }
            }
        }
        else -> {}
    }

    if (shouldStop) {
        return suspendAndNotify(frame, iPC, reason)
    }

    return iPC + 1
}

private fun suspendAndNotify(frame: Frame, iPC: Int, reason: String): Int {
    val threadId = getThreadId(frame)
    val latch = CountDownLatch(1)
    suspendLatch[threadId] = latch
    suspendedFrames[threadId] = frame

    // Notify IDE that execution stopped
    client.stopped(StoppedEventArguments().apply {
        this.reason = reason
        this.threadId = threadId.toInt()
        this.allThreadsStopped = true
    })

    // Block the runtime thread until IDE sends continue/step
    latch.await()

    // Clean up
    suspendedFrames.remove(threadId)
    return iPC + 1
}
```

### 5.3 Breakpoint Registration

When `setBreakpoints` arrives from the IDE:

```kotlin
override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
    val sourcePath = args.source?.path ?: return emptyBreakpointsResponse()
    val sourceKey = normalizeSourcePath(sourcePath)

    // Clear existing breakpoints for this source
    lineBreakpoints.remove(sourceKey)

    val verifiedBreakpoints = args.breakpoints?.mapIndexed { index, sbp ->
        val verified = canBreakAtLine(sourcePath, sbp.line)
        val bp = DapBreakPoint(
            id = nextBreakpointId(),
            line = sbp.line,
            condition = sbp.condition,
            verified = verified
        )

        if (verified) {
            lineBreakpoints
                .getOrPut(sourceKey) { ConcurrentHashMap.newKeySet() }
                .add(bp)
        }

        Breakpoint().apply {
            id = bp.id
            isVerified = bp.verified
            line = bp.line
            source = args.source
            if (!bp.verified) {
                message = "No executable code at line ${sbp.line}"
            }
        }
    }?.toTypedArray() ?: emptyArray()

    // Enable the debugger in the runtime if we have any breakpoints
    updateDebuggerActive()

    return CompletableFuture.completedFuture(
        SetBreakpointsResponse().apply { breakpoints = verifiedBreakpoints }
    )
}
```

### 5.4 Breakpoint Validation

A line is "breakable" if there's a `Nop` (LINE_N) op at that line in some method:

```kotlin
private fun canBreakAtLine(sourcePath: String, line: Int): Boolean {
    // Find all MethodStructures in the module whose source file matches
    val methods = findMethodsInSource(sourcePath)

    for (method in methods) {
        val sourceOffset = method.sourceLineNumber  // 0-based offset in file
        val methodLine = line - sourceOffset         // line relative to method start

        val ops = method.ops ?: continue
        var currentLine = 1
        for (op in ops) {
            if (op is Nop) {
                currentLine += op.lineCount
                if (currentLine == methodLine) return true
            }
        }
    }
    return false
}
```

---

## 6. Stack Frames, Variables, and Scopes

### 6.1 Stack Trace Response

When the IDE requests `stackTrace`:

```kotlin
override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> {
    val threadId = args.threadId.toLong()
    val topFrame = suspendedFrames[threadId] ?: return emptyStackResponse()

    val frames = mutableListOf<StackFrame>()
    var frame: Frame? = topFrame
    var frameId = 0

    while (frame != null && !frame.isNative) {
        val method = frame.f_function
        val lineNumber = method.calculateLineNumber(frame.m_iPC)
        val sourceFile = getSourceFileName(frame)

        frames.add(StackFrame().apply {
            id = registerFrame(frameId, frame!!)
            name = method.identityConstant.pathString
            line = lineNumber + method.sourceLineNumber
            column = 1
            source = Source().apply {
                this.name = sourceFile
                this.path = resolveSourcePath(frame!!)
            }
        })

        frame = frame.f_framePrev
        frameId++
    }

    // Follow cross-service calls via Fiber.traceCaller()
    // (mirrors DebugConsole's stack trace rendering)

    return CompletableFuture.completedFuture(
        StackTraceResponse().apply {
            stackFrames = frames.toTypedArray()
            totalFrames = frames.size
        }
    )
}
```

### 6.2 Scopes and Variables

DAP uses a three-level hierarchy: **stackFrame → scopes → variables**.

```kotlin
override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
    val frame = getRegisteredFrame(args.frameId)

    return CompletableFuture.completedFuture(ScopesResponse().apply {
        scopes = arrayOf(
            Scope().apply {
                name = "Locals"
                variablesReference = registerVariableScope(frame, ScopeType.LOCAL)
                expensive = false
            },
            Scope().apply {
                name = "this"
                variablesReference = registerVariableScope(frame, ScopeType.THIS)
                expensive = false
            }
        )
    })
}

override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> {
    val (frame, scopeType) = getVariableScope(args.variablesReference)

    val variables = when (scopeType) {
        ScopeType.LOCAL -> {
            // Enumerate registers in the current frame
            // Mirrors DebugConsole's variable rendering logic
            val vars = mutableListOf<Variable>()
            val aInfo = frame.f_aInfo
            val ahVar = frame.f_ahVar

            for (i in aInfo.indices) {
                val info = frame.getVarInfo(i) ?: continue
                val handle = ahVar[i] ?: continue
                val name = info.name ?: "var$i"
                val type = info.type.valueString
                val value = formatValue(handle)

                vars.add(Variable().apply {
                    this.name = name
                    this.value = value
                    this.type = type
                    // If the variable is expandable (object with properties)
                    this.variablesReference = if (isExpandable(handle)) {
                        registerChildVariables(handle)
                    } else 0
                })
            }
            vars
        }
        ScopeType.THIS -> {
            // Enumerate properties of the "this" target
            formatObjectProperties(frame.f_hTarget)
        }
    }

    return CompletableFuture.completedFuture(
        VariablesResponse().apply {
            this.variables = variables.toTypedArray()
        }
    )
}
```

### 6.3 Value Formatting

The existing `DebugConsole` has extensive value rendering logic. The DAP adapter should reuse it:

- **Primitive types** (Int, String, Boolean): Display the value directly
- **Collections/Arrays**: Show `Array<String>(3)` with expandable children
- **Objects**: Show `MyClass {...}` with expandable properties via `ClassComposition.FieldInfo`
- **Refs/Vars**: Dereference and display the referent
- **Futures**: Show completion status

---

## 7. Expression Evaluation

The existing `EvalCompiler` is extremely powerful — it can evaluate arbitrary Ecstasy expressions in the context of a suspended frame, with access to all local variables.

### 7.1 DAP `evaluate` Request

```kotlin
override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> {
    val frame = getFrameForEvaluation(args.frameId)
    val expression = args.expression

    // Wrap expression the same way DebugConsole does
    val wrappedExpr = """{
        return {Object r__ = {
            return $expression;
        }; return r__.toString();};
    }"""

    val compiler = EvalCompiler(frame, wrappedExpr)
    val lambda = compiler.createLambda(frame.poolContext().typeString())

    if (lambda == null) {
        val errors = compiler.errors.joinToString("\n") { it.messageText }
        return CompletableFuture.completedFuture(
            EvaluateResponse().apply {
                result = "Error: $errors"
                variablesReference = 0
            }
        )
    }

    // Execute the lambda and capture the result
    // This requires running it in the runtime's execution context
    val result = executeEvalLambda(frame, lambda, compiler.args)

    return CompletableFuture.completedFuture(
        EvaluateResponse().apply {
            this.result = result.displayValue
            this.type = result.typeName
            this.variablesReference = if (result.isExpandable) {
                registerChildVariables(result.handle)
            } else 0
        }
    )
}
```

### 7.2 Eval Contexts

DAP sends different `context` values:
- `"watch"` — Watch panel expressions (re-evaluated on every stop)
- `"repl"` — Debug console input
- `"hover"` — Mouse hover in editor (simple identifier lookups)
- `"clipboard"` — Copy value request

For hover context, optimize by first checking if the expression is a simple variable name and looking it up in the frame registers directly, without invoking `EvalCompiler`.

---

## 8. Exception Breakpoints

### 8.1 Exception Filters

```kotlin
override fun setExceptionBreakpoints(
    args: SetExceptionBreakpointsArguments
): CompletableFuture<SetExceptionBreakpointsResponse> {
    val filters = args.filters?.toSet() ?: emptySet()

    breakOnAllExceptions = "all" in filters
    breakOnUncaughtExceptions = "uncaught" in filters

    // Also support specific exception types via filterOptions
    specificExceptionTypes.clear()
    args.filterOptions?.forEach { option ->
        option.condition?.let { typeName ->
            specificExceptionTypes.add(typeName)
        }
    }

    updateDebuggerActive()

    return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse())
}
```

### 8.2 Exception Checking

The `Debugger.checkBreakPoint(Frame, ExceptionHandle)` hook fires on every exception:

```kotlin
override fun checkBreakPoint(frame: Frame, hEx: ExceptionHandle): Int {
    if (frame.isNative) return Op.R_NEXT

    val shouldBreak = when {
        breakOnAllExceptions -> true
        specificExceptionTypes.any { hEx.matches(it) } -> true
        breakOnUncaughtExceptions && isUncaught(frame, hEx) -> true
        else -> false
    }

    if (shouldBreak) {
        currentException = hEx
        suspendAndNotify(frame, Op.R_EXCEPTION, "exception")
    }

    return Op.R_EXCEPTION  // let exception propagate naturally
}
```

### 8.3 Exception Info

When stopped on an exception, the IDE requests `exceptionInfo`:

```kotlin
override fun exceptionInfo(args: ExceptionInfoArguments): CompletableFuture<ExceptionInfoResponse> {
    val hEx = currentException ?: return emptyExceptionInfo()

    return CompletableFuture.completedFuture(ExceptionInfoResponse().apply {
        exceptionId = hEx.type.valueString
        description = hEx.toString()
        breakMode = if (breakOnAllExceptions) "always" else "unhandled"
    })
}
```

---

## 9. XTC-Specific Considerations

### 9.1 Services as "Threads"

XTC services are **single-threaded actors**. Each service has its own `ServiceContext` with one or more `Fiber`s. For DAP, map:

| XTC Concept | DAP Concept |
|-------------|-------------|
| `ServiceContext` | Thread |
| `Fiber` | (hidden, or shown as sub-threads) |
| `Frame` | StackFrame |
| `Container` | (not mapped — internal concept) |

```kotlin
override fun threads(): CompletableFuture<ThreadsResponse> {
    val runtime = Runtime.INSTANCE // or however the runtime is accessed
    val threads = mutableListOf<Thread>()

    for (context in runtime.allServiceContexts) {
        threads.add(Thread().apply {
            id = context.hashCode()
            name = context.serviceName ?: "Service-${context.hashCode()}"
        })
    }

    return CompletableFuture.completedFuture(
        ThreadsResponse().apply { this.threads = threads.toTypedArray() }
    )
}
```

### 9.2 Freezing All Services During Debug

The current `DebugConsole` uses `synchronized` on the singleton, effectively freezing all services when the debugger is active. This is intentional — XTC services communicate via async messages, and allowing other services to continue while one is paused could cause timeouts and deadlocks.

The DAP implementation should maintain this behavior. When any service hits a breakpoint:
1. Report `allThreadsStopped: true`
2. Freeze container time via `container.freezeTime()` (already implemented)
3. Block all other service contexts from executing

### 9.3 Future JIT Considerations

The JIT compiler (`javatools/src/main/java/org/xvm/javajit/`) currently has **no debugger support** (`Assert.java` JIT path: `"Debugger support for jit is not yet implemented"`). The DAP adapter should:

1. Initially require interpreter mode for debugging (flag in launch config)
2. Design the `Debugger` interface abstraction to be backend-agnostic
3. When JIT adds debug support, it will need to:
   - Emit JDWP-compatible line number tables (already partially done via `code.lineNumber(bctx.lineNumber)`)
   - Instrument breakpoint check calls at `Nop` op sites
   - Bridge JDWP events back to the `Debugger` interface

### 9.4 The `@Debug` Annotation

Code annotated with `@Debug` is only available when the `TypeSystem` is created in debug mode (`"debug".defined`). The DAP launcher should ensure the runtime creates containers with debug mode enabled, so that `@Debug`-annotated code is included.

---

## 10. DAP and LSP Coordination

### 10.1 What the IDE Handles Automatically

The IDE already coordinates DAP and LSP — they are independent protocols:

| Feature | Protocol | Details |
|---------|----------|---------|
| Syntax highlighting | LSP (semantic tokens) | Always active |
| Breakpoint gutter marks | IDE native | Stored in editor state |
| Breakpoint validation | DAP (`setBreakpoints`) | Validates on debug start |
| "Go to Definition" during debug | LSP | No DAP involvement |
| Hover during debug | DAP (`evaluate`) | IDE prefers DAP when debugging |
| Code completion during debug | LSP | No DAP involvement |
| Inline values during debug | DAP (`evaluate`) | IDE evaluates inline decorations |

### 10.2 Where They Could Optionally Share

While not required, there are optimization opportunities:

1. **Source mapping**: Both LSP and DAP need to map source files to internal structures. They could share a workspace index.
2. **Type information**: LSP's type analysis could provide better hover info during debug than DAP's raw `evaluate`.
3. **Breakpoint validation**: LSP knows which lines have code (from tree-sitter parse); it could prevalidate breakpoints before the debug session starts.

These are **nice-to-have optimizations**, not blocking requirements.

### 10.3 Launch Configurations

The IDE extension (VS Code `launch.json` / IntelliJ run configuration) specifies:

```json
{
    "type": "xtc",
    "request": "launch",
    "name": "Debug MyApp",
    "program": "${workspaceFolder}/src/MyApp.xtc",
    "args": [],
    "stopOnEntry": false,
    "debugMode": true,
    "xdkPath": "${env:XDK_HOME}"
}
```

The DAP server receives these in the `launch` request and uses them to:
1. Locate the XDK and runtime
2. Start the XTC runtime with debug mode enabled
3. Load the specified module
4. Begin execution

---

## 11. Implementation Phases

### Phase 0: IDE-Side DAP Wiring - COMPLETE ✅

**Goal**: Wire up the LSP4IJ DAP extension point so IntelliJ can launch and connect to the DAP server.

> **Completed**: 2026-02-12 on branch `lagergren/lsp-extend4`

1. **Created `XtcDebugAdapterFactory`** (`intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/`)
   - `XtcDebugAdapterFactory` — LSP4IJ `DebugAdapterDescriptorFactory`, registered via
     `com.redhat.devtools.lsp4ij.debugAdapterServer` extension point in `plugin.xml`
   - `XtcDebugAdapterDescriptor` — launches DAP server out-of-process with provisioned Java 25

2. **Shared infrastructure extracted**
   - `PluginPaths.kt` — shared JAR resolution for both LSP (`xtc-lsp-server.jar`) and DAP
     (`xtc-dap-server.jar`). Searches plugin `bin/` directory (not `lib/` — avoids classloader
     conflicts with LSP4IJ's bundled lsp4j). Error messages include all searched paths.
   - Both LSP and DAP use `JreProvisioner` for Java 25 JRE resolution/download.

3. **Module renamed** `debug-adapter` → `dap-server` for consistency with `lsp-server`

4. **Architecture documented**
   - KDoc on `XtcDebugAdapterDescriptor` explains: out-of-process/JBR 21 compatibility, LSP vs DAP
     process lifecycle differences, and why the LSP `AtomicBoolean` notification guard is not needed
     for DAP (user-initiated sessions, no concurrent spawn race condition).
   - `lsp-processes.md` updated with DAP architecture, correct class names, JAR locations.
   - `PLAN_IDE_INTEGRATION.md` documents LSP4IJ design choice over IntelliJ built-in LSP.

**What's NOT done yet**: The DAP server itself (`dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServer.kt`)
is still a stub. It needs to be connected to the XTC runtime's `Debugger` interface (Phase 1 below).

### Phase 1: Minimal Viable Debugger (2-3 weeks)

**Goal**: Set a breakpoint, hit it, see the call stack.

1. **Create `DapDebugger` implementing `Debugger`**
   - `checkBreakPoint()` that checks line breakpoints and blocks on match
   - Use `CountDownLatch` for suspension
   - Send `stopped` events to DAP client

2. **Wire `DapDebugger` into XTC runtime**
   - Replace `DebugConsole.INSTANCE` with `DapDebugger` when launched via DAP
   - Requires making the debugger instance configurable (currently hardcoded singleton)
   - Key change: `ServiceContext.getDebugger()` returns the DAP debugger

3. **Implement core DAP handlers in `XtcDebugServer`**
   - `launch` — start XTC runtime, load module
   - `setBreakpoints` — register line breakpoints
   - `configurationDone` — begin execution
   - `threads` — return service list
   - `stackTrace` — walk `Frame.f_framePrev` chain
   - `continue` — release the latch

4. **Source path resolution**
   - Map IDE file paths to `MethodStructure` source names
   - Handle both workspace files and library sources

### Phase 2: Variable Inspection (1-2 weeks)

**Goal**: See local variables and "this" when stopped.

1. **Implement `scopes` handler** — locals + this
2. **Implement `variables` handler** — enumerate frame registers
3. **Value formatting** — reuse/adapt `DebugConsole` rendering logic
4. **Expandable variables** — objects, arrays, collections
5. **Variable reference management** — track references across requests

### Phase 3: Stepping (1 week)

**Goal**: Step over, step into, step out.

1. **`next` (step over)** — set `StepMode.StepOver`, release latch
2. **`stepIn`** — set `StepMode.StepInto`, release latch
3. **`stepOut`** — set `StepMode.StepOut`, release latch
4. **`onReturn` hook** — detect step-out completion

### Phase 4: Expression Evaluation (1 week)

**Goal**: Evaluate expressions in debug console and watches.

1. **Bridge to `EvalCompiler`** — reuse existing infrastructure
2. **Handle async eval** — `EvalCompiler` can trigger `R_CALL` continuations
3. **Watch expressions** — re-evaluate on every stop
4. **Hover evaluation** — optimize for simple variable lookups

### Phase 5: Exception Breakpoints + Polish (1 week)

**Goal**: Full exception debugging, conditional breakpoints.

1. **Exception breakpoints** — `setExceptionBreakpoints` handler
2. **Exception info** — `exceptionInfo` handler
3. **Conditional breakpoints** — bridge to `EvalCompiler` for condition evaluation
4. **Breakpoint validation** — verify lines and report `verified: false`
5. **Clean shutdown** — proper `disconnect`/`terminate` handling

### Phase 6: Multi-Service Debugging (1-2 weeks)

**Goal**: Debug across XTC services.

1. **Thread enumeration** — list all active `ServiceContext`s
2. **Cross-service stack traces** — follow `Fiber.traceCaller()` across service boundaries
3. **Service-specific stepping** — step into async service calls
4. **Container/time freezing** — ensure proper freeze/unfreeze

### Total Estimated Effort: 7-10 weeks

This is significantly reduced from a from-scratch implementation because:
- **`DebugConsole`** already has all the debugger logic (stepping, breakpoints, variable inspection, eval)
- **`Debugger` interface** is clean and well-defined
- **Source mapping** is already embedded in bytecode
- **`EvalCompiler`** already supports arbitrary expression evaluation
- The DAP server is primarily a **protocol translation layer**

---

## Key Files Reference

| File | Role |
|------|------|
| `javatools/src/main/java/org/xvm/runtime/Debugger.java` | Interface to implement |
| `javatools/src/main/java/org/xvm/runtime/DebugConsole.java` | Reference implementation (~2590 lines) |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java` | Interpreter loop with debug hooks |
| `javatools/src/main/java/org/xvm/runtime/Frame.java` | Execution frame, variables, stack traces |
| `javatools/src/main/java/org/xvm/runtime/Fiber.java` | Fiber/coroutine, cross-service tracing |
| `javatools/src/main/java/org/xvm/asm/Op.java` | Base op class, all 256 opcodes |
| `javatools/src/main/java/org/xvm/asm/op/Nop.java` | Line mapping ops (LINE_1..LINE_N) |
| `javatools/src/main/java/org/xvm/asm/op/Assert.java` | `assert:debug` hook |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java` | Source text, line calculation |
| `javatools/src/main/java/org/xvm/compiler/EvalCompiler.java` | Runtime expression evaluation |
| `lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServer.kt` | DAP server stub |
| `lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServerLauncher.kt` | DAP stdio launcher |
