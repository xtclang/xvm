# LLVM JIT Study for XVM / Ecstasy

Investigation date: 2026-08-13

This study covers the current XVM Java runtime and the in-progress Ecstasy-to-JVM-bytecode JIT, then evaluates whether an LLVM-based JIT or compiler can be added. The short answer is yes, but the practical route is a staged backend and runtime-ABI effort, not a direct replacement of the current Java classfile builder.

Related notes:

- Current implementation map: [jit-current-anatomy.md](jit-current-anatomy.md)
- LLVM and runtime-interop appendix: [llvm-jit-appendix.md](llvm-jit-appendix.md)
- LLVM object ABI notes: [llvm-object-abi-notes.md](llvm-object-abi-notes.md)
- Performance and fiber runtime strategy: [performance-runtime-strategy.md](performance-runtime-strategy.md)
- LLVM compiler scope plan: [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)
- AST-vs-XTC feasibility analysis: [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md)
- Runtime port and self-hosting study: [runtime-port-self-hosting-study.md](runtime-port-self-hosting-study.md)

Second-pass review (2026-08-13): this study's conclusions are challenged, corrected, and extended in [second-opinion-review.md](second-opinion-review.md), [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md) (LLVM-as-JIT precedents, tiering, OSR, footprint contradiction), and [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md) (roots, fibers, deopt). Note in particular: the "fall back to existing execution" language below is phase-dependent — no interpreter fallback exists today, and once a native heap exists the fallback tier must be a native-world method-IR interpreter, not the Java interpreter.

## Executive Conclusion

XTC can support LLVM-based JIT code generation. The repository already treats the portable XVM binary as the deployable execution artifact, and the XVM design documentation explicitly says that the portable binary was designed as input for JIT and AOT compilation, while efficient interpretation was a non-requirement (`doc/x.md:600`). That makes bytecode-to-native a legitimate direction even though the current bytecode is not a pleasant compiler IR.

The current JIT is not AST-based. It is a lazy Ecstasy-to-Java-classfile compiler selected by `xtc run -J`, routed through `JitConnector`, and triggered by Java class loading. Method bodies are lowered from `MethodStructure.getOps()` through `BuildContext`; individual `Op` classes expose JIT hooks through `computeTypes(BuildContext)` and `build(BuildContext, CodeBuilder)`. This is a direct XTC-bytecode lowering model.

LLVM should not be bolted directly onto those `Op.build` methods as another per-op code emission path. That would duplicate the current backend entanglement. The right shape is:

1. Preserve XTC bytecode as the first LLVM input.
2. Introduce a backend-neutral XTC lowering layer that converts `Op[]`, type-flow information, call chains, register shapes, guards, and constants into a small compiler IR or structured lowering API.
3. Keep the existing JVM-classfile JIT as one backend.
4. Add an LLVM ORC backend through a native sidecar library with a stable C ABI, invoked from Java through the JDK Foreign Function & Memory API or JNI.
5. Start with whitelisted leaf methods and expand only after object layout, exceptions, services, safepoints, and deoptimization have real runtime contracts.

The fastest useful prototype is a leaf-function LLVM accelerator. It would compile selected bytecode methods using primitive and simple reference signatures, call back to Java for unsupported operations, and run under a new `LlvmConnector` or under the Java-JIT connector as a delegated backend. The long-term architecture can evolve into a full native runtime, but that is a much larger project than adding LLVM code generation.

The largest hidden risk is compiled code touching XTC objects. LLVM does not make this problem go away. Early LLVM code should manipulate non-primitive XTC values only through an opaque `xvm_ref` / helper-call ABI owned by the runtime. Direct native loads and stores into object fields become viable only after the runtime defines object layout, type metadata, write barriers, safepoints, root reporting, and identity rules. In other words: the compiler decides when an operation is eligible for direct lowering, but object semantics belong in the runtime ABI.

The performance story depends on treating the opaque-handle bridge as a migration tool, not as the end state. Fast execution comes from native code over a typed method IR, unboxed values, runtime-owned object layouts, compact fiber continuation frames, and a scheduler/GC contract that compiled code can cooperate with cheaply.

## Current JIT Model

The present JIT is selected at the command line with `-J` / `--jit`, described as "Enable the JIT-to-Java back-end" in `javatools/src/main/java/org/xvm/tool/LauncherOptions.java:75`. `Runner` chooses either `JitConnector` or `InterpreterConnector` in `javatools/src/main/java/org/xvm/tool/Runner.java:346`.

The JIT connector builds a separate Java-JIT XVM:

- `JitConnector` constructs `Xvm` (`javatools/src/main/java/org/xvm/javajit/JitConnector.java:35`).
- Module load links a `TypeSystem` through `xvm.createLinker().addModule(module).link()` (`JitConnector.java:42`).
- Startup loads `_native.mgmt.nMainInjector`, invokes native resource setup, and creates a Java-JIT container (`JitConnector.java:58`).
- Invocation installs a `Ctx` scoped value and reflectively invokes a generated module class (`JitConnector.java:81`).
- Generated classes are dumped under `./jasm/...` on every invocation (`JitConnector.java:160`), which is useful for development but not a production lifecycle.

The generated class path is lazy:

- `ModuleLoader.findClass` asks `TypeSystem.genClass` for class bytes when a class name in the module package is requested (`javatools/src/main/java/org/xvm/javajit/ModuleLoader.java:84`).
- `TypeSystem.genClass` deduces the XVM artifact from the generated Java class name, asks the appropriate builder to assemble a class, and emits a JVM classfile using the JDK Class-File API (`javatools/src/main/java/org/xvm/javajit/TypeSystem.java:298`).
- `CommonBuilder.generateCode` only compiles bodies for a hardcoded allowlist (`JIT_LIST`), with additional per-method exclusions (`javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:3814`).

Method code generation is bytecode-driven:

- `BuildContext.assembleCode` starts from `Op[] ops = methodStruct.getOps()` (`javatools/src/main/java/org/xvm/javajit/BuildContext.java:304`).
- `preprocess` walks op addresses, computes type flow, handles guard/finally metadata, and boxes refs when needed (`BuildContext.java:377`).
- `process` iterates the same op array and calls `op.build(this, code)` (`BuildContext.java:553`).
- The base `Op` type defaults `computeTypes` to a type-matrix follow and defaults `build` to `UnsupportedOperationException` (`javatools/src/main/java/org/xvm/asm/Op.java:419`).

This is not an AST compiler. It is closer to an XTC-bytecode-to-JVM backend with a lot of semantic recovery at lowering time.

## Current Runtime Boundary

The Java interpreter and the Java-JIT runtime are related but separate execution models.

The interpreter path builds a `Runtime`, `NativeContainer`, and `MainContainer`, then invokes methods through `m_containerMain.invoke0` (`javatools/src/main/java/org/xvm/api/InterpreterConnector.java:32`). The main interpreter loop in `ServiceContext.execute` repeatedly calls `aOp[iPC].process(frame, iPCLast)`, handles negative control codes such as `R_CALL`, `R_RETURN`, `R_EXCEPTION`, `R_BLOCK`, and yields after `MAX_OPS_PER_RUN` (`javatools/src/main/java/org/xvm/runtime/ServiceContext.java:507`). Interpreter frames carry Java `ObjectHandle` registers, guard state, continuations, call chains, and fiber state (`javatools/src/main/java/org/xvm/runtime/Frame.java:63`).

The Java-JIT path instead uses `org.xvm.javajit.Ctx` and the classes in `javatools_jitbridge`. `Ctx` carries container access, constant lookup, injection, and multi-return slots (`javatools/src/main/java/org/xvm/javajit/Ctx.java:20`). `nObject` is the root helper for generated objects and encodes owner/container, immutability, construction, and native bits in `$meta` (`javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:40`). The native type system maps core Ecstasy types to reserved Java bridge classes (`javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java:264`).

That split matters. LLVM integration should reuse and formalize the Java-JIT runtime ABI concepts where possible, not try to make native code impersonate the interpreter's `Frame` and `ObjectHandle` machinery from day one.

## Compiled Code and XTC Object Manipulation

This is the central runtime boundary for LLVM.

The current project has at least two object worlds:

- The interpreter uses Java `ObjectHandle` subclasses. A `Frame` stores registers as `ObjectHandle[]`, and handles carry `TypeComposition`, mutability, service/pass-through behavior, and template-specific Java fields.
- The Java JIT bridge uses generated Java classes rooted at `nObject`, with `$meta` bits for owner/container id, immutability, construction state, and native-class private flags. `Ctx` carries additional object return slots (`o0`, `o1`, ...), and bridge classes such as `nRef`, `Array`, and `String` encode Java-specific layouts.

Those worlds are already not the same ABI. LLVM cannot safely treat either as a native object layout:

- Java object layout is not a stable language ABI.
- HotSpot can move objects, and JNI/FFM references have scoped lifetimes.
- Interpreter `ObjectHandle` values contain runtime/template state, not user-object fields in a layout LLVM can reason about.
- Java-JIT bridge objects expose fields, but those are Java fields managed by the JVM, not portable native offsets.
- `Ref`, `Var`, arrays, strings, services, immutability, and type tests all have language semantics beyond "load from pointer plus offset".

The first LLVM backend should therefore use this rule:

> Native code may pass, compare, and return XTC references as opaque handles, but any semantic operation on an object goes through a runtime helper unless the ABI explicitly marks that object representation as native-direct.

For an early Java-hosted sidecar, that implies:

- Primitive `JitFlavor` values can be unboxed into LLVM scalars.
- Opaque references should be represented as `xvm_ref` tokens, not raw Java object addresses.
- Object identity, type tests, field/property access, `Ref`/`Var` get/set, array/string access, allocation, freezing, boxing, unboxing, dispatch, exception creation, and service interaction are helper calls or bailouts.
- Helper calls are part of the runtime ABI and must have status returns for exception, block, suspend, and bailout states.
- Any helper returning a reference must define rooting/lifetime for the native frame.

This is not just an implementation detail. It decides what code can be compiled:

| Operation kind | Early LLVM with opaque references | Later native runtime |
| --- | --- | --- |
| Primitive arithmetic | Direct LLVM scalar ops | Direct LLVM scalar ops |
| Reference pass-through | Pass `xvm_ref` token | Pass pointer or compressed ref |
| Identity compare | Helper or token compare if ABI guarantees uniqueness | Direct compare if layout guarantees identity |
| Type test | Helper | Inline fast path plus helper slow path |
| Field read/write | Helper | Direct offset access plus barriers/checks |
| Array/string element access | Helper for first prototype | Direct for native-layout arrays/strings |
| `Ref`/`Var` get/set | Helper | Direct only for native-layout ref boxes |
| Allocation | Helper | Runtime allocator plus safepoint/accounting |
| Service/fiber interaction | Excluded or helper status return | Scheduler ABI and safepoints |

The likely migration path is:

1. **Opaque Java-hosted handles**: LLVM manipulates `xvm_ref` tokens and calls Java/Kotlin runtime helpers. This is safest and good enough for leaf methods with mostly primitive work.
2. **Selective native containers**: specific immutable data types, arrays, strings, or numeric boxes gain runtime-defined native layouts. LLVM can inline operations only for those representations.
3. **Native object heap**: ordinary objects move to a runtime-owned heap with headers, type descriptors, field tables, write barriers, safepoints, and root maps.
4. **Legacy adapters**: Java `ObjectHandle` / Java-JIT bridge objects remain behind adapters until retired.

The design pressure is therefore to name object operations in the neutral method IR. The IR should not say "load Java field `$referent`" or "read `ObjectHandle.m_clazz`". It should say `ref.get`, `field.load`, `type.is_a`, `array.load`, or `object.freeze`, and let the backend/runtime choose direct lowering, helper call, or bailout.

## How This Can Become Fast

Opaque references and helper calls are not the desired steady state. They are a correctness bridge that lets LLVM enter the system before a native runtime is complete. The fast path has to move through several gates:

1. **Typed method IR**: the backend receives normalized control flow, precise types, representation choices, safepoints, and call descriptors. It should not rediscover all semantics from raw ops in hot code.
2. **Unboxed values**: booleans, integers, floats, nullable primitives, small tuples, and XVM primitives stay in registers or fixed result areas. Boxing is a boundary operation, not the default representation.
3. **Helper-call elimination**: object operations begin as helpers, then graduate to guarded inline sequences when the runtime publishes a native layout and barrier contract.
4. **Direct hot layouts**: arrays, strings, numeric boxes, refs/vars, and ordinary objects get compact runtime-owned layouts. LLVM can emit offset loads/stores only for those layouts.
5. **Specialization**: generic, relational, nullable, and virtual-call-heavy code needs method specializations keyed by type/layout/version facts. Invalidations go through the runtime code cache.
6. **Compact fibers**: compiled code runs as ordinary native code inside non-suspending regions, but loop headers, calls, allocation, and service operations are explicit safepoints. If a safepoint blocks, live values are spilled into a compact runtime continuation frame.
7. **Small runtime kernel**: production execution should not load the Kotlin compiler, Java interpreter, or AST machinery. It should load typed metadata, native/AOT code cache, runtime libraries, scheduler, allocator, and intrinsic table.

The mature execution shape should look like this:

```text
typed method IR
  -> representation planning
  -> native code with safepoint maps and layout guards
  -> direct native object/array/string access on proven layouts
  -> runtime helpers only for slow paths, uncommon traps, services, allocation, and deopt
```

For fiber-style execution, the key is to avoid giving every Ecstasy fiber a large native stack. A future XVM fiber should be mostly heap/runtime metadata: current method, state, mailbox/waits, timeout/context tokens, and a compact stack of continuation frames. Native compiled functions can use the machine stack while they are running, but they must not suspend at arbitrary instructions. Suspension happens only at safepoints where the compiler has emitted enough metadata to spill live values into the fiber frame.

That model allows very fast straight-line and loop code because the common path is not an interpreter dispatch loop and not a helper call per operation. It also keeps footprint low because suspended fibers retain compact value arrays/frames rather than OS-thread stacks.

## Why XTC Bytecode Is Still the Right First Input

The unorthodox choice to JIT bytecode rather than ASTs is awkward but defensible for this project:

- `.xtc` modules are the persistent executable artifact. ASTs may not exist when a deployed module is loaded.
- The current linker, type system, call-chain logic, constants, and method bodies already operate on `MethodStructure`, `TypeConstant`, `TypeInfo`, and `Op[]`.
- The runtime semantics that matter to execution are present in bytecode plus type metadata.
- The current JIT investment is already in bytecode-level type recovery, register representation, call signatures, and guard/finally lowering.
- The project documentation says the portable binary was designed for native compilation and AOT (`doc/x.md:605`).

The caveat is important: XTC bytecode is not a great optimizing IR. An LLVM backend should not translate it one op at a time forever. It should reconstruct a control-flow graph, typed virtual registers, exception edges, and call/return shapes, then emit LLVM IR from that normalized representation.

AST-to-LLVM can be considered later for AOT builds when source is available, but it should not be the first implementation path. It would bypass deployed `.xtc` modules, duplicate semantic analysis, and produce a second behavior source to keep consistent with the bytecode runtime.

## LLVM Fit

LLVM ORC is the appropriate LLVM JIT infrastructure. LLVM's ORC documentation describes a modular JIT API with JIT-linking, LLVM IR compilation layers, eager and lazy compilation, custom compilers and program representations, concurrent compilation, and removable code. LLJIT is the ready-made ORC stack for LLVM IR modules, while lower layers allow more custom materialization.

For XVM, ORC gives useful building blocks:

- Lazy materialization by symbol maps well to lazy generated module/class/method loading.
- JITDylibs map reasonably to XVM modules, containers, or type systems.
- ORC can accept LLVM IR modules, object files, or custom materialization units.
- JITLink's object-linking layer supports runtime linking of relocatable objects and runtime registration needs such as exception handling metadata.
- LLVM IR can represent primitive arithmetic, structured control flow, calls, object pointers, and exceptions.

LLVM does not solve the hard XVM runtime problems by itself. The hard work is the XVM ABI:

- object representation and metadata
- object handle rooting and lifetime
- object field access, mutation, and write barriers
- generic specialization and JIT shape
- constant pool access
- multi-return values
- virtual dispatch and call-chain semantics
- `Ref` / `Var`
- guards, finally blocks, and exception propagation
- service/fiber suspension points
- allocation, immutability, memory accounting, and garbage collection
- deoptimization and stack reconstruction if optimized native frames can call back into interpreted execution

## Recommended Architecture

### Phase 1: sidecar LLVM accelerator

Add a native library, for example `libxvmllvmjit`, owned by the Java process. Java remains the host for module loading, linking, metadata, and unsupported execution. The native library owns an ORC `LLJIT` or custom ORC session.

Java-facing API:

- `create_engine(target, options) -> engine_handle`
- `define_module(engine, serialized_metadata) -> module_handle`
- `compile_method(engine, method_key, method_ir_or_metadata) -> compiled_handle`
- `lookup(engine, method_key) -> function_pointer`
- `release(engine, compiled_handle)`

The Java side should hide this behind an `LlvmRuntime` service. The repo baseline is JDK 25 (second-pass verification), so the Foreign Function & Memory API is unconditionally available and is the default; JNI is only relevant where native code must call deeply into the JVM or hold Java object handles. Note the measurement caveat in [second-opinion-review.md](second-opinion-review.md): helper calls from native code back into Java are FFM *upcalls*, the most expensive boundary crossing — object-heavy sidecar benchmarks measure that boundary, not the architecture.

Initial compiled function contract:

- Input: `Ctx*` or opaque context handle, followed by primitive/reference arguments in the same conceptual order used by `JitMethodDesc`.
- Return: first scalar result directly when possible; additional results through `Ctx` slots or an out-parameter result area.
- Failure: return a status code or throw only through a documented exception bridge. Early leaf functions should avoid exceptions and use status returns for bailout.
- References: opaque stable handles at first, or runtime-owned native object pointers later; never unmanaged raw Java object pointers with arbitrary lifetime.

The first whitelist should be smaller than the current JVM JIT allowlist: arithmetic, comparisons, branches, simple local register movement, simple calls to already-compiled leaf functions, and immutable string/array primitives only after their representation is fixed.

### Phase 2: backend-neutral lowering

Create a neutral lowering layer between `Op[]` and backend emitters:

- method CFG with explicit normal and exceptional edges
- typed virtual registers and stack/local slots
- `JitFlavor`-like representation shapes independent of Java `ClassDesc`
- explicit `Ref` boxing/unboxing operations
- explicit call descriptors and return descriptors
- explicit guard/finally regions
- metadata references to type constants and constant-pool entries

The current Java backend can continue emitting classfiles from this layer, or the neutral layer can initially be used only by LLVM while the Java backend remains as-is. The long-term maintenance win comes from making both backends share the same type-flow and semantic lowering decisions.

### Phase 3: LLVM backend as peer to Java JIT

Introduce an execution connector or backend selector:

- `InterpreterConnector`: compatibility and debugger-friendly execution.
- `JitConnector`: existing JVM classfile backend.
- `LlvmConnector`: new native backend, initially delegating unsupported functions to interpreter or JVM JIT.

The connector should compile at method granularity. Direct class-level generation is natural for the JVM but not necessary for LLVM; LLVM can materialize functions and type metadata separately.

### Phase 4: full native runtime pieces

Only after leaf methods work should the project expand into:

- native object layout or stable object-handle tables
- native allocation and memory accounting
- native service/fiber scheduling safepoints
- exception personality or status-return unwinding
- stack maps, patchpoints, and deoptimization metadata
- cross-module invalidation/unloading
- AOT object emission

## Interpreter Integration

The interpreter should not be rewritten before LLVM proves useful. It is valuable as an oracle and compatibility path, but it is a poor host ABI for native compiled frames.

Recommended interpreter modifications are narrow:

- Add a `CompiledMethod` or `MethodExecutor` abstraction attached to `MethodStructure` or call-chain resolution.
- Let call ops ask a backend registry whether a callee has a compiled entry.
- Marshal only supported argument and return shapes for early prototypes.
- If the compiled method returns a bailout/unsupported status, fall back to the existing `Frame` path.
- Add counters in call dispatch or `ServiceContext.execute` to identify hot methods if adaptive compilation is desired.

Avoid trying to map every interpreter `Frame` into an LLVM stack frame. The interpreter frame has `ObjectHandle[]` registers, guard state, continuations, futures, debug state, and fiber scheduling assumptions. A native frame needs a smaller and more explicit contract.

The Java-JIT runtime is the better integration target. `Ctx`, `JitFlavor`, `JitTypeDesc`, `JitMethodDesc`, `nObject`, and the native bridge classes are already close to a compiled-code ABI. The LLVM backend should either share those definitions or replace them with backend-neutral equivalents.

## Key Design Decisions

### Backend API

Do not add `buildLlvm(...)` to every `Op` as the end state. It may be acceptable for a short prototype, but it will grow into the same backend coupling already present in `Op.build(BuildContext, CodeBuilder)`. Prefer one of these:

- a neutral `XtcLoweringContext` that op-specific code populates
- a separate bytecode-to-CFG builder that pattern-matches existing `Op` subclasses
- an MLIR dialect or custom small IR if the project wants a durable multi-backend compiler layer

The simplest maintainable first step is a custom small IR. MLIR is plausible later but would add more toolchain surface area than necessary for proving LLVM viability.

### Object Access ABI

LLVM should not get a private object model. It should consume an object model supplied by the runtime:

- `xvm_ref` identity and lifetime
- type descriptor lookup
- field layout lookup or helper dispatch
- mutability and construction-state checks
- owner/container accounting
- reference rooting for native frames
- write barriers and safepoint rules
- bailout metadata for live references

For the Java-hosted prototype, direct object access should be forbidden. The backend may only pass references through, compare references where the ABI says token equality is identity equality, and call helpers. This keeps the first prototype honest: LLVM acceleration is useful for primitive-heavy methods and small call chains, while object-heavy code stays interpreted or helper-bound.

For a native runtime, the same helper names can become inlineable ABI operations. A generated field load can then lower to "load from offset after type/layout guard" rather than "call helper", but only because the runtime owns the layout and GC contract.

### Exceptions

The current Java JIT preprocesses `GuardAll`, jumps, returns, and finally blocks into JVM bytecode control flow. LLVM can model exception edges with `invoke`, landing pads, and personality functions, but Ecstasy exceptions are language-level objects and Java exceptions are currently involved in the JVM backend. Early LLVM code should avoid native exception unwinding across Java frames. Use status returns for bailouts and map exceptions at the runtime boundary until a real personality/landing-pad story exists.

### Garbage Collection and Safepoints

There are two viable memory strategies:

- JVM-owned objects through handles, with native code treating references as opaque. This is safest initially but limits optimization and can make FFM/JNI transitions expensive.
- Native XVM heap with explicit object layout and safepoints. This enables real native performance but requires stack maps, root reporting, barriers, and object lifetime rules.

LLVM has statepoint and stack map infrastructure that can help describe live roots and patch/deopt metadata, but those facilities do not replace the runtime design.

The distinction is decisive:

- In Java-hosted mode, the JVM decides object movement and reclamation. XVM can account requested bytes through hooks, but it cannot make object layout compact, guarantee exact object size, or let LLVM directly update fields.
- In hybrid mode, some `xvm_ref` values point to native objects and some wrap Java objects. This is useful for migration but creates cross-heap rooting and cycle problems.
- In native mode, the XVM runtime owns allocation, headers, roots, barriers, and GC. This is the only mode that can combine low footprint, exact container accounting, high fiber counts, and fast object-heavy native code.

The recommended native memory design is exact GC, not conservative native-stack scanning. Roots should come from fiber records, compiled safepoint maps or shadow stacks, module constants, service queues, native handle scopes, and code-cache metadata. Allocation should use per-service or per-worker nurseries with a bump-pointer fast path and a slow path for quota checks, GC, large objects, and hard-limit behavior.

Early native GC can be simple stop-the-world mark/sweep or copying collection, but the ABI should leave room for a generational collector. Direct field stores require write barriers from the first moment object references can cross generations or service/container ownership boundaries.

### Services and Fibers

Compiled code must not block the XVM scheduler invisibly. The early whitelist should exclude operations that can suspend, wait on futures, perform service sends, or need debugger interaction. Later phases need explicit poll/safepoint operations in lowered IR and a convention for returning to the scheduler.

For performance, the long-term scheduler contract should be stackless at suspension points:

- non-suspending compiled regions run on the native stack
- safepoints have spill maps for live primitive/reference values
- blocking helpers return a status that materializes a compact continuation frame
- resumes jump back through a generated resume stub or re-enter the method IR at a safepoint
- service sends and waits never park an OS thread per Ecstasy fiber

This is more compiler/runtime work than mapping Ecstasy fibers to host threads, but it is the route to high fiber counts and low memory use.

### AOT

An LLVM JIT path can later support AOT by sharing the bytecode-to-IR lowering and changing the final ORC/object emission path. The runtime ABI must be designed with relocation, symbol lookup, module identity, and versioning in mind from the start.

## Feasibility Verdict

Feasible: yes.

Recommended first implementation: LLVM sidecar JIT for whitelisted bytecode methods, hosted by Java, using a narrow runtime ABI and fallback to existing execution.

Recommended performance direction: do not stop at the sidecar. Use it to validate lowering, then move hot execution to a native runtime ABI with compact fibers, direct layouts for hot objects, AOT/JIT code caching, and helper calls only on slow paths.

Not recommended as first implementation:

- direct AST-to-LLVM
- replacing the interpreter wholesale
- native object heap and GC as phase one
- adding a second backend hook directly to all `Op` classes without a neutral lowering layer

Primary success criteria for a prototype:

- Compile and execute a small method subset from `MethodStructure.getOps()`.
- Produce results identical to interpreter/JVM JIT for selected unit tests.
- Support primitive `JitFlavor` shapes, nullable primitive shape, and multi-return through a documented result area.
- Fall back cleanly on unsupported ops.
- Keep generated native code unloadable by module or test run.

Primary success criteria for the fast runtime:

- no per-op dispatch in hot compiled code
- no mandatory helper call for primitive arithmetic, local control flow, or proven-layout field/array access
- no OS stack allocated per suspended Ecstasy fiber
- bounded metadata footprint for modules and specializations
- code cache can unload cold module/specialization code
- GC/root maps cover native frames at every safepoint

## External Sources Consulted

- LLVM ORC design and implementation: https://llvm.org/docs/ORCv2.html
- LLVM JITLink and ORC ObjectLinkingLayer: https://llvm.org/docs/JITLink.html
- LLVM Kaleidoscope IR generation tutorial: https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl03.html
- LLVM Kaleidoscope JIT/optimizer tutorial: https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl04.html
- LLVM exception handling: https://llvm.org/docs/ExceptionHandling.html
- LLVM garbage collection safepoints: https://llvm.org/docs/Statepoints.html
- LLVM stack maps and patch points: https://llvm.org/docs/StackMaps.html
- LLVM LLJIT C API reference: https://llvm.org/docs/doxygen/group__LLVMCExecutionEngineLLJIT.html
- OpenJDK JEP 454, Foreign Function & Memory API: https://openjdk.org/jeps/454
