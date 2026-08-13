# LLVM JIT Appendix

Investigation date: 2026-08-13

This appendix captures LLVM, Java interop, and XVM runtime details that are useful for future implementation work.

## External Technical Notes

LLVM ORC is the current JIT framework to target. The ORC design documentation describes ORCv2 as the modern API and lists support for runtime object linking, LLVM IR compilation layers, eager and lazy compilation, custom compilers and program representations, concurrent JIT compilation, removable code, and composability.

LLJIT is the practical starting point for an LLVM-IR-based JIT. The LLJIT C API exposes builders, JIT instances, adding LLVM IR modules or object files, symbol lookup, data layout, and access to transform/linking layers.

JITLink and ORC's ObjectLinkingLayer matter if XVM needs runtime registration for exception handling, thread-local variables, static initializers, or custom memory management of generated code.

LLVM exception handling uses explicit exceptional control flow, typically `invoke` plus landing-pad/personality support. This is relevant to XTC guards and finally blocks, but it should not be used casually to unwind through Java frames.

LLVM statepoints and stack maps can support runtime root reporting, safepoints, patching, and deoptimization metadata. They are tools for a runtime, not a runtime by themselves.

OpenJDK JEP 454 finalized the Foreign Function & Memory API in JDK 22. FFM provides `Linker`, `SymbolLookup`, `FunctionDescriptor`, `MemorySegment`, `Arena`, and related APIs for Java-to-native calls and off-heap memory access. Second-pass resolution: the repo baseline is already JDK 25 (`version.properties`), so FFM is unconditionally available — the FFM-vs-JNI decision point discussed below is settled in FFM's favor, and the JNI notes remain only for the deep-JVM-interaction cases.

## Candidate LLVM Lowering Pipeline

A durable LLVM backend should not emit LLVM IR directly from every `Op` in isolation. It should use a pipeline like this:

1. Load `MethodStructure`, `MethodBody`, call chain, and `Op[]`.
2. Reuse or recreate the current `TypeMatrix` and `RegisterInfo` decisions.
3. Build a control-flow graph with explicit block boundaries, normal edges, exceptional edges, and finally edges.
4. Convert XTC registers to typed virtual registers.
5. Normalize multi-slot and nullable shapes into explicit values.
6. Lower high-level XVM operations into a small compiler IR.
7. Run local simplification on that compiler IR.
8. Emit LLVM IR.
9. Run a small LLVM optimization pipeline.
10. Add the module to ORC/LLJIT and publish the compiled symbol.

The pipeline should keep method identity stable: module, type system, callable type, method id, specialization id, and version/invalidation key.

## Data Representation Sketch

Representation should start from the current Java-JIT shapes, with Java-specific descriptors replaced by ABI descriptors.

| XVM/JIT concept | Initial LLVM/native representation |
| --- | --- |
| `Boolean` | `i1` or ABI-promoted `i8`/`i32`, normalized at boundaries |
| small ints and `Char` | sign/zero-extended LLVM integers with explicit Ecstasy semantics |
| `Int64` / `UInt64` | `i64` |
| `Float32` / `Float64` | `float` / `double` |
| nullable primitive | value plus null/default tag, matching `JitFlavor` semantics |
| `Int128` / `UInt128` | `i128` internally if useful, lowered to two `i64` ABI slots if needed |
| `Dec32` / `Dec64` / `Dec128` | runtime helper or explicit packed representation; avoid pretending IEEE floats are enough |
| object reference | opaque handle or native object pointer |
| `Ref` / `Var` | boxed location handle with load/store helper ABI |
| multi-return | direct first result plus result area or `Ctx` slots |
| `TypeConstant` / constants | stable metadata handle resolved by Java or native metadata table |

The initial ABI should be boring and explicit. Do not optimize representation until fallback, exceptions, and tests are working.

## Function ABI Proposal

For a first prototype:

```c
typedef struct XtcCtx XtcCtx;
typedef struct XtcRef XtcRef;

typedef enum XtcStatus {
    XTC_OK = 0,
    XTC_BAILOUT = 1,
    XTC_EXCEPTION = 2,
    XTC_BLOCKED = 3
} XtcStatus;

typedef struct XtcResultArea {
    uint64_t i[8];
    XtcRef  *o[8];
    void    *overflow_i;
    void    *overflow_o;
} XtcResultArea;
```

Compiled functions can use one of two conventions:

- direct-return convention for pure scalar leaf methods
- status-return convention for anything that can bail out, throw, block, or return multiple values

Example status-return shape:

```c
XtcStatus xtc_fn(XtcCtx *ctx, XtcResultArea *results, ...args);
```

This mirrors the current `Ctx` multi-return design while staying independent of Java object layout.

## Calls and Dispatch

Calls need three tiers:

1. Direct call to another compiled function when the callee symbol and specialization are known.
2. Inline-cache or dispatch helper for virtual calls where receiver shape is stable.
3. Runtime fallback helper for unresolved calls, formal types, virtual constructors, services, or interpreter-only behavior.

The current Java-JIT class naming and specialization scheme can inform symbol keys:

- module name or module id
- type-system id
- callable JIT type
- instance JIT type where needed
- method id
- method signature and return shape
- specialization layer and shape

LLVM symbols should not reuse Java class names as the canonical identity, but they can include a sanitized variant for debugging.

## Exceptions, Guards, and Finally

Current Java-JIT lowering handles finally by preprocessing op ranges and converting returns/jumps/exceptions through synthetic state (`BuildContext.java:315`). LLVM has native exceptional control flow, but the Ecstasy runtime should choose one of two approaches:

### Status-return unwinding

Each compiled function returns a status. Exceptional state lives in `XtcCtx` or a result area. Callers branch to cleanup/finally blocks explicitly.

Advantages:

- simple Java/native interop
- no native exception crossing Java frames
- easy fallback to interpreter
- easier to make debugger and service status explicit

Disadvantages:

- less idiomatic LLVM
- more branches in generated code
- needs careful optimization to avoid overhead everywhere

### Native EH personality

Compiled functions use LLVM `invoke`, landing pads, and an XVM personality function. JITLink/runtime registration handles EH metadata. Java boundaries catch or convert native exceptions/status.

Advantages:

- natural optimized exceptional control flow
- cleaner source-level shape for complex guards

Disadvantages:

- harder platform work
- risky interaction with JVM frames
- requires strong object/exception ABI and metadata registration

Recommendation: use status-return unwinding for prototypes and leaf-to-medium methods; revisit native EH after a stable native runtime ABI exists.

## Memory and GC Options

### JVM-owned objects with native handles

Java owns objects. Native code receives opaque handles or stable ids and calls helper functions for field access, type checks, allocation, and method calls.

Pros:

- safest with existing runtime
- easiest fallback
- avoids building GC first

Cons:

- helper calls can dominate performance
- native code cannot optimize object layout
- handle lifetime/pinning rules need care

### Native XVM heap

Compiled code uses native object pointers and a native heap. Java becomes a loader/control plane, or is gradually moved out of the runtime path.

Pros:

- best long-term performance path
- enables LLVM optimization of object access
- aligns with an eventual native runtime/AOT story

Cons:

- requires object layout, GC/rooting, barriers, safepoints, memory accounting, debugger metadata, and service/fiber integration
- much larger project

### Hybrid

Start with handles for arbitrary objects and native unboxed representations for primitives, arrays, strings, and small immutable runtime types that have fixed layouts.

This is the recommended middle ground.

## Safepoints and Deoptimization

A compiled method that can allocate, call into the runtime, block, or loop for a long time needs safepoints. For early LLVM work:

- insert explicit poll calls at loop headers and call sites
- forbid compiled code from blocking directly
- require all runtime helper calls to be safepoints unless annotated otherwise
- keep enough register metadata to reconstruct interpreter state on bailout

For later work:

- use stack maps to describe live values at safepoints
- use patchpoints for inline caches and deoptimization sites
- consider LLVM statepoints if a moving native GC is introduced

The project should not depend on advanced LLVM GC machinery until there is a concrete XVM memory model to plug into it.

## Java Interop Choices

### FFM-hosted sidecar

Java loads `libxvmllvmjit` and calls exported C functions through FFM.

Best for:

- Java-hosted prototype
- explicit native handles
- ordinary downcalls into native JIT API

Watch points:

- JDK 22+ baseline or conditional build path
- native access flags and packaging
- callback/upcall overhead
- stable lifetime for memory segments and engine handles

### JNI-hosted sidecar

Java calls JNI wrappers around a C++ ORC engine.

Best for:

- compatibility with older JDK baselines
- native code that needs direct JVM interaction
- mature existing JNI infrastructure

Watch points:

- more glue and crash risk
- local/global reference handling
- thread attachment

### Native host embeds JVM

An XVM native process owns the runtime and embeds JVM only for Java-based compiler/tooling services.

Best for:

- long-term native runtime
- minimizing Java runtime involvement

Watch points:

- largest architecture change
- packaging and lifecycle complexity
- requires clear ownership of module loading and runtime state

Recommendation: use FFM if the build can assume JDK 22+, otherwise JNI. Keep the native side C ABI stable even if the implementation is C++ ORC.

## AOT Path

An LLVM backend can become an AOT compiler if the lowering layer is not tied to live ORC execution. Requirements:

- stable object file format and relocation model
- serialized metadata for constants, type ids, and method tables
- runtime loader for compiled artifacts
- versioning against `.xtc` module identity
- platform target triple and data-layout handling
- debug info strategy

Do not optimize for AOT first, but avoid choices that make it impossible.

## Prototype Candidate

A first prototype should compile a deliberately narrow subset:

- module-level or static functions
- no allocation except possibly result boxing through helpers
- no services, futures, blocking, debugger hooks, or native waits
- primitive integer/boolean/float arguments and returns
- local variables, simple branches, loops
- comparisons and arithmetic
- direct call to another compiled function only when already materialized
- bailout for unsupported op

Expected source files touched:

- new native sidecar under a separate Gradle project or `javatools_llvmjit`
- Java facade under `org.xvm.llvmjit` or `org.xvm.javajit.llvm`
- backend registry on connector path
- small lowering layer near `org.xvm.javajit` but not dependent on `java.lang.classfile.CodeBuilder`
- tests comparing interpreter/JVM-JIT/LLVM results for selected methods

## Open Questions to Resolve Before Implementation

1. What is the minimum supported JDK for the repo after the JIT work? This determines FFM vs JNI default.
2. Should LLVM live as an optional native dependency or a mandatory build dependency?
3. Is generated native code allowed to depend on Java object handles, or should the project start defining native layouts now?
4. What are the first benchmark programs that justify LLVM over JVM classfile generation?
5. How much of `JitFlavor` is stable enough to become backend-neutral ABI vocabulary?
6. What is the unload/invalidation unit: method, type system, module, container, or process?
7. What debugger behavior is required for compiled frames?
8. How should Ecstasy service/fiber suspension be represented at compiled-code boundaries?
9. Is AOT a hard requirement for the first design, or only a future extension?

## Source Links

- LLVM ORC design and implementation: https://llvm.org/docs/ORCv2.html
- LLVM JITLink and ORC ObjectLinkingLayer: https://llvm.org/docs/JITLink.html
- LLVM exception handling: https://llvm.org/docs/ExceptionHandling.html
- LLVM garbage collection safepoints: https://llvm.org/docs/Statepoints.html
- LLVM stack maps and patch points: https://llvm.org/docs/StackMaps.html
- LLVM LLJIT C API reference: https://llvm.org/docs/doxygen/group__LLVMCExecutionEngineLLJIT.html
- OpenJDK JEP 454, Foreign Function & Memory API: https://openjdk.org/jeps/454
