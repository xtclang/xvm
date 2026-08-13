# Current XVM JIT Anatomy

Investigation date: 2026-08-13

This appendix records how the current in-repository JIT works and which details matter for a possible LLVM backend.

Second-pass verification (2026-08-13): the claims below were re-verified against source; corrections and additions are marked inline and consolidated in [second-opinion-review.md](second-opinion-review.md).

## High-Level Shape

The current JIT is an Ecstasy-to-Java-classfile compiler. It is not an AST compiler and it is not a native-code compiler. It compiles XTC bytecode ops from `MethodStructure.getOps()` into JVM bytecode using the JDK Class-File API.

The repository also has a Java interpreter runtime. The interpreter is the default execution path; the Java-JIT path is selected explicitly and uses a separate bridge runtime under `org.xvm.javajit` and `javatools_jitbridge`.

## Command and Connector Selection

`javatools/src/main/java/org/xvm/tool/LauncherOptions.java:75` adds `-J` / `--jit` as "Enable the JIT-to-Java back-end".

`javatools/src/main/java/org/xvm/tool/Runner.java:329` creates a connector for a module run. The backend split is at `Runner.java:346`:

```java
return isJit ? new JitConnector(repo) : new InterpreterConnector(repo);
```

This is already a useful seam for a future `LlvmConnector` or for a backend selector under `JitConnector`.

## Java-JIT Connector

`javatools/src/main/java/org/xvm/javajit/JitConnector.java` is the external API adapter for the Java-JIT backend.

Important flow:

- Constructor creates a Java-JIT `Xvm` (`JitConnector.java:35`).
- `loadModule` loads the app module and links a `TypeSystem` through `xvm.createLinker().addModule(module).link()` (`JitConnector.java:42`).
- `start` loads `_native.mgmt.nMainInjector`, calls `addNativeResources`, and creates the container (`JitConnector.java:58`).
- `invoke0` installs `Ctx.Current` with a new `Ctx(xvm, container)` (`JitConnector.java:81`).
- `invoke0Impl` loads the generated module class, creates it with a `Ctx`, constructs a String array for command-line arguments, and reflectively invokes `run` or `run$p` (`JitConnector.java:86`).
- The `finally` block deletes and recreates `./jasm/...` dumps for generated classes (`JitConnector.java:160`).

The always-on `jasm` dump is a development behavior that should be decoupled before any production JIT work.

## XVM and Native Type System

`javatools/src/main/java/org/xvm/javajit/Xvm.java:31` identifies the class as the Ecstasy-to-Java JIT implementation.

The constructor:

- creates the native/core type system (`Xvm.java:46`)
- registers it (`Xvm.java:49`)
- creates a hidden native container (`Xvm.java:50`)
- records loaders and pools for Ecstasy and `_native` (`Xvm.java:52`)

`javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java` combines `Ecstasy`, `Turtle`, and `_native` modules (`NativeTypeSystem.java:82`). It loads `javatools_jitbridge` classes (`NativeTypeSystem.java:60`) and maps core Ecstasy types to reserved Java bridge names (`NativeTypeSystem.java:264`). It can also augment native bridge classes with generated Ecstasy methods using classfile transformation (`NativeTypeSystem.java:214`).

LLVM implication: this is the closest thing today to a compiled-code runtime ABI. LLVM should either reuse its concepts or replace them deliberately with backend-neutral equivalents.

## Lazy Class Generation

Generated Java class names are resolved by class loading.

`javatools/src/main/java/org/xvm/javajit/ModuleLoader.java:84` overrides `findClass`. If the requested class name starts with the module prefix:

1. strip the prefix
2. call `typeSystem.genClass(this, suffix)`
3. define the returned bytes as a Java class
4. store bytes for later dumping

`javatools/src/main/java/org/xvm/javajit/TypeSystem.java:298` performs the actual classfile generation:

1. derive the XVM artifact from the class-name convention
2. get or create a builder for the artifact
3. build a class, enum, class-of-class, or exception shape
4. emit the JVM classfile with stack maps, fixed short jumps, and dead labels dropped

LLVM implication: ORC lazy materialization can mirror this laziness at symbol granularity. Native code does not need to pretend to be Java classes; it can materialize functions keyed by module/type/method descriptors.

## Method Generation

The path to method body generation is in `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java`.

`generateCode` (`CommonBuilder.java:3814`) gates actual body generation behind:

- `JIT_LIST` (`CommonBuilder.java:3886`)
- `NO_JIT_LIST` (`CommonBuilder.java:3982`)
- `NO_JIT_METHODS` (`CommonBuilder.java:3985`)

When a class/method is not allowed, the builder logs a skip message and emits a default return (`CommonBuilder.java:3854`). This means many generated methods are still stubs.

The allowlist includes simple tests, a subset of `ecstasy`, some collections, exceptions, numeric classes, a small part of reflection, text classes, `Duration`, and `_native.io.TerminalConsole`. There are explicit TODO exclusions for virtual constructors, service support, maps, ranges, generic lambda signatures, and several conversion paths.

## Bytecode-Level Lowering

`javatools/src/main/java/org/xvm/javajit/BuildContext.java` is the method body lowering context.

The key method is `assembleCode` (`BuildContext.java:304`):

```java
Op[] ops = methodStruct.getOps();

enterMethod(code);
preprocess(code, ops);
process(code, ops);
exitMethod(code);
```

`preprocess` (`BuildContext.java:377`) walks the XTC ops before emission. It:

- tracks `GuardAll`, `FinallyStart`, `FinallyEnd`, `Jump`, and `OpReturn`
- rewrites jump/return behavior around finally blocks
- clears labels and guarded ops because the same ops can be compiled more than once for different formal types
- calls `op.computeTypes(this)` for reached ops and scope-changing ops (`BuildContext.java:502`)
- boxes parameter refs when `MoveRef` or `MoveVar` requires it

`process` (`BuildContext.java:553`) then iterates op addresses and calls `op.build(this, code)`.

The base op API is in `javatools/src/main/java/org/xvm/asm/Op.java:419`:

- `computeTypes(BuildContext)` defaults to following the type matrix
- `build(BuildContext, CodeBuilder)` defaults to throwing unsupported
- note that `build` returns an `int` control directive, not `void`: `-1` means continue, and a positive op address means "skip/eliminate all ops up to that address" — the lowering hook has peephole/dead-range elision built in

Coverage count from this worktree (second pass):

- 215 op classes under `javatools/src/main/java/org/xvm/asm/op`
- 81 define `build` directly; six shared base classes (`OpGeneral`, `OpCondJump`, `OpIndex`, `OpInPlace`, `OpInPlaceAssign`, `OpTest`) cover another ~86, for roughly 78% effective coverage
- uncovered as whole families: all tuple-form calls/invokes (20 classes), all property in-place ops (17), tuple/multi var ops, and `Return_T`
- an unimplemented op does not fall back: the `UnsupportedOperationException` from `Op.build` aborts generation of the entire class, and there is no interpreter fallback or mixed-mode execution anywhere in the process
- methods outside the `JIT_LIST` allowlist are silently stubbed to emit a default return — a correctness hazard for anything run outside the allowlist

LLVM implication: the current backend knows too much at the per-op Java-code-emission layer. A second backend needs a neutral lowering layer, not another copy of the same coupling.

## Type and Shape Specialization

`doc/jit_class_names.txt` is the main design note for current JIT class naming and specialization. It defines:

- layer-one composition specialization
- JIT Call Class and JIT Instance Class naming
- Java-Primitive, XVM-Primitive, and JIT-Primitive shapes
- layer-two and layer-three specialization for primitive-shaped generic parameters
- generalized, specialized, and central classes/interfaces
- `$box` and `$unbox` layer-three conversion support

`javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java` contains the implementation hooks:

- `getCallableClassDesc` and `getInstanceeClassDesc` (`TypeConstant.java:7116`)
- `ensureJitClassName` (`TypeConstant.java:7136`)
- primitive-shape checks (`TypeConstant.java:7214`)
- layer-two specialization checks (`TypeConstant.java:7246`)
- `getJitDesc` (`TypeConstant.java:7297`)

`javatools/src/main/java/org/xvm/javajit/JitTypeDesc.java` maps Ecstasy primitive types to Java primitive or multi-slot representations:

- many narrow integer and character-like types use `int`
- `Int64` and `UInt64` use `long`
- `Float32` uses `float`
- `Float64` uses `double`
- `Dec32`, `Dec64`, `Dec128`, `Int128`, and `UInt128` are XVM primitives

`javatools/src/main/java/org/xvm/javajit/JitFlavor.java` names the representation categories: specific references, widened references, primitives, nullable primitives, XVM primitives, nullable XVM primitives, refs, and always-null.

LLVM implication: `JitFlavor` is Java-named, but the abstraction is not JVM-specific. An LLVM backend should keep the representation-shape concept and replace `ClassDesc` with LLVM/native ABI types.

## Java-JIT Runtime Bridge

`javatools/src/main/java/org/xvm/javajit/Ctx.java` is the runtime context for compiled Java code. It stores:

- owning `Xvm` and `Container`
- hot integer and object return slots `i0..i7` and `o0..o7`
- overflow return arrays `iN` and `oN`
- memory accounting hooks, currently TODO
- constant lookup
- injection
- logging

`javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java` is the root helper for generated classes. It provides:

- `$ctx` and `$xvm`
- `$meta` header bits for owner/container, immutability, construction, and native use
- `$owner`, `$ownerId`, `$xvmType`, `$type`
- immutability helpers
- `$isA`

LLVM implication: a native backend needs equivalent context, object metadata, multi-return, and type-test contracts. The first prototype can model object references as opaque handles, but a mature backend needs a defined layout or handle-table ABI.

## Interpreter Runtime

`javatools/src/main/java/org/xvm/api/InterpreterConnector.java` builds the interpreter path:

- constructs `Runtime` and `NativeContainer` (`InterpreterConnector.java:32`)
- links module structures (`InterpreterConnector.java:45`)
- creates `MainContainer` (`InterpreterConnector.java:56`)
- starts runtime/container (`InterpreterConnector.java:65`)
- invokes through `m_containerMain.invoke0` (`InterpreterConnector.java:111`)

`javatools/src/main/java/org/xvm/runtime/Frame.java` is the interpreter call frame. It stores the op array, target handles, variable handles, return register indices, guard state, exception handle, continuation, call-chain state, and debugger state.

`javatools/src/main/java/org/xvm/runtime/ServiceContext.java:507` is the interpreter execution loop. It executes `aOp[iPC].process(frame, iPCLast)` and handles negative result codes such as calls, returns, exceptions, blocks, repeats, and pauses.

LLVM implication: the interpreter is too Java-object-heavy to be the primary native execution frame. It should remain a compatibility path and a correctness oracle. Native compiled methods should integrate at call boundaries with clear marshal/fallback rules.

## Body Double-Encoding: Ops and BAST

A fact this appendix originally omitted: every compiled method body is serialized twice in the `.xtc` file — as the `Op[]` stream this JIT consumes, and as a BinaryAST (53 node classes under `javatools/src/main/java/org/xvm/asm/ast/`, lazily read by `MethodStructure.getAst()`). The only execution-stack consumer of the BAST is debugger eval (`org.xvm.compiler.EvalCompiler`); the JIT reconstructs control flow and type facts from the op stream while a higher-level typed encoding of the same body sits unread in the same file. The BAST section carries no independent version/magic. Implications for the neutral method IR are developed in [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md).

## Toolchain Baseline

The repo pins JDK 25 (`version.properties`, `org.xtclang.java.jdk=25`). The Class-File API used by the JIT is final (JEP 484, JDK 24); the real JDK floor is `ScopedValue` (`Ctx.Current`), final in JDK 25. FFM is therefore unconditionally available for any native sidecar work. The `Ctx` memory-accounting TODOs explicitly plan to "park this virtual thread and schedule a different fiber" — the Java-JIT runtime design already assumes Loom virtual threads for fibers.

## Current Limitations Relevant to LLVM

Observed from source:

- JIT method support is partial and allowlisted; non-allowlisted methods are silently stubbed to return defaults.
- Many Java-JIT builder paths throw `UnsupportedOperationException`, and any such throw aborts the whole generated class — there is no fallback path.
- Memory accounting in `Ctx` is TODO (all four methods are empty).
- Service support is not in the main JIT allowlist; the bridge `nService` is a stub whose async operations throw.
- Virtual constructors, union types, generic formal resolution, and several property/call cases remain incomplete.
- Generated class dumping is hardwired in `JitConnector.invoke0Impl`.
- The current Java-JIT runtime and interpreter runtime are not the same ABI, and they share no object model at all (zero cross-imports; Ecstasy exceptions are detected by class-name-prefix string matching plus a reflective field read at the connector boundary).

These limitations are not arguments against LLVM. They are arguments for making LLVM a staged backend behind a narrow, tested ABI.

## Takeaways for LLVM Work

1. Keep bytecode as the first input.
2. Reuse existing type metadata, call-chain analysis, and JIT shape decisions.
3. Do not attempt a whole-runtime rewrite before compiling a method subset.
4. Design a backend-neutral lowering layer before broad LLVM support.
5. Treat interpreter integration as fallback/oracle work.
6. Treat `Ctx`, `JitFlavor`, `JitTypeDesc`, `JitMethodDesc`, and `nObject` as the starting vocabulary for an eventual compiled-code ABI.
