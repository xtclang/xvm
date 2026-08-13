# Minimal Cleanroom Runtime Study

Investigation date: 2026-08-13 (second pass)

Question under study: how big is the XVM runtime today, and what is the smallest possible cleanroom effort — in any language, disregarding speed entirely — that yields a *complete* runtime able to load and execute today's `.xtc` modules?

Short answer: the interpreter runtime proper is ~63k lines of Java plus roughly half of the 114k-line `asm` layer that it leans on for loading and type identity. A cleanroom interpreter-only replacement is a **45–75k line project in a GC'd language**; calibrated against the one directly comparable effort (the Kotlin compiler fork: ~230k converged lines in ~6 months, single developer plus agents), that is **roughly 2–4 weeks to hello-world, 3–6 months to "runs the XDK and most of the TCK," and 6–10 months to full conformance** — with the schedule dominated not by code volume but by semantic ambiguity, since the Java code is the only complete specification of runtime behavior.

Related notes:

- Runtime port and self-hosting study: [runtime-port-self-hosting-study.md](runtime-port-self-hosting-study.md)
- Runtime port scope plan: [runtime-port-scope-plan.md](runtime-port-scope-plan.md)
- XTC v2 and method IR: [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md)
- Second-pass review: [second-opinion-review.md](second-opinion-review.md)

## How Big Is the Runtime Today?

Measured on this worktree (`wc -l`, Java source):

| Component | Lines | Files | Needed by a minimal runtime? |
| --- | ---: | ---: | --- |
| `org.xvm.runtime` core (frames, fibers, services, containers) | 19,812 | 31 | yes — reimplement |
| `org.xvm.runtime.template.*` (native class impls) | 42,760 | 176 | partially — see native surface below |
| — of which `template/_native` (I/O, fs, net, crypto, mgmt, …) | 24,742 | — | small subset at first |
| `org.xvm.asm` total (format, constants, structures, ops) | 113,933 | ~420 | read-only slice only |
| — of which `asm/constants` (type/constant algebra) | 47,149 | 97 | the resolution half, not the assembly half |
| — of which `asm/op` (op decode + interpreter `process()` + JIT `build()`) | 25,357 | 215 | decode + process semantics only |
| — of which `asm/ast` (BAST, unused by execution) | 5,658 | 53 | no |
| `org.xvm.javajit` + `javatools_jitbridge` (Java JIT world) | 38,834 | ~200 | no |
| `org.xvm.compiler` (source compiler) | 66,487 | — | no |

What the runtime must *execute*: `lib_ecstasy` is 67,599 lines of Ecstasy across 319 files, `javatools_bridge`'s `_native` module is 5,616 lines, and the turtle bootstrap (`mack.x`) closes the metacircular knot. The de-facto host-intrinsic surface is the **544 `markNativeMethod`/`markNativeProperty` calls** across the templates — that number, not the 63k lines, is the honest measure of the native contract a new runtime must satisfy, because everything not marked native is ordinary Ecstasy code the interpreter runs.

So the reference implementation a cleanroom effort must be behavior-compatible with is roughly: 63k (runtime) + ~40–50k (the loading/identity/type-resolution half of `asm`) ≈ **~110k lines of Java**, of which a large fraction is mechanical (per-opcode handlers, per-numeric-type templates, per-constant-kind deserializers).

## Interpreter vs. Compiler: Isolating the Mixed Layers

How big is the interpreter runtime compared to the Java XTC compiler? The headline packages are almost exactly the same size — but the honest comparison requires splitting the shared `asm` layer, whose classes serve the compiler, the interpreter, and the JIT simultaneously.

**Cleanly separable packages** (`javatools` totals 272,881 lines):

| Concern | Package(s) | Lines |
| --- | --- | ---: |
| Source compiler (source → `.xtc`) | `org.xvm.compiler` | 66,487 |
| — of which source AST + semantic analysis | `compiler/ast` (93 files) | 54,425 |
| — of which lexer/parser/driver | `compiler` top level | 12,062 |
| Interpreter runtime | `org.xvm.runtime` (incl. templates) | 63,308 |
| Java JIT world | `org.xvm.javajit` + `javatools_jitbridge` | 38,834 |
| Shared format/identity layer | `org.xvm.asm` (all) | 113,933 |
| Launcher/tooling | `org.xvm.tool` (+ `api`) | 7,846 |

**Inside `asm`** (113,933 lines), by sub-area:

| Sub-area | Lines | Real owner |
| --- | ---: | --- |
| Structures, pool, file format (`asm` top level, 55 files) | 35,769 | mixed: `assemble`/`registerConstants`/pool-`optimize` halves are compiler-side; `disassemble`/resolve halves are loader/runtime-side |
| Constant/type algebra (`asm/constants`, 97 files) | 47,149 | genuinely shared: `isA`, `TypeInfo`, member resolution serve compile-time checking *and* runtime dispatch; only the assemble/register methods are compiler-side |
| Op classes (`asm/op` + `Op*` bases, 229 files) | 32,884 | three-way mixed — split below |
| BinaryAST (`asm/ast`, 53 files) | 5,658 | compiler-produced; execution ignores it (debugger eval only) |

**The op classes, method-by-method.** Each op class mixes three consumers in one file: the compiler/format concern (constructor-from-`DataInput`, `write(DataOutput)`, `registerConstants`, `simulate(Scope)`, opcode tables), the interpreter concern (`process(Frame)`), and the JIT concern (`build(BuildContext, CodeBuilder)`, `computeTypes`). Measured by brace-matching method bodies against those signatures across all 229 files:

| Op-class concern | Method-body lines |
| --- | ---: |
| Compiler/format (ctor-from-stream, write, register, simulate, opcodes) | ~3,800 |
| Interpreter (`process` and exception continuation) | ~3,500 |
| JIT (`build`, `computeTypes`) | ~2,000 |
| Shared helpers (argument access, `toString`, jump targeting, …) | ~7,900 |
| Non-method scaffolding (fields, imports, javadoc, declarations) | ~15,700 |

**Bottom line.** Attributing the shared layers to their consumers: the *effective* compiler is ≈ 66.5k (compiler pkg) + 5.7k (BAST) + roughly the assembly half of `asm` ≈ **95–105k lines**, and the *effective* interpreter runtime is ≈ 63.3k (runtime pkg) + the interpreter slice of the ops + the load/resolve/type-dispatch half of `asm` ≈ **100–110k lines**. They are the same order of magnitude — close to 1:1 — with the type/constant algebra (≈47k) as the piece both genuinely share. Two practical consequences: (1) the interpreter-only cleanroom budget above does not need the compiler's ~100k at all, only the shared algebra's runtime half; and (2) any XTC v2 / module-model split ([xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md)) should be measured against this decomposition — today's `asm` is exactly the layer where compiler-side assembly state and runtime-side identity are fused, and the 229 op files fuse all three concerns plus wire format in single classes.

## What "Complete" Means

Disregarding speed does not shrink the semantic surface. A complete runtime must:

1. Load v1 `.xtc` files: file structure, recursive constant pool (97 constant kinds), component tree, method structures, op decode.
2. Resolve runtime type identity: assignability (`isA` over the full type algebra — union/intersection/difference, immutable, access-limited, parameterized, virtual child, formal/generic types, auto-narrowing), member resolution and call chains (the `TypeInfo` equivalent), and type comparison across modules.
3. Interpret the 215 ops: frames, registers, guards/finally, multi-return, tuples, futures/deferred values.
4. Implement services and fibers: per-service execution, fiber suspension (`R_BLOCK`/`R_REPEAT`/`R_PAUSE` equivalents), reentrancy policy (`Concurrent`/`Synchronized`/`Critical` plus per-frame concurrency safety), timeouts, and the cross-boundary rules: immutable → pass by reference, `@AutoFreezable` → freeze, otherwise → proxy.
5. Provide object semantics: mutability/freezing, `maskAs`/`revealAs` (references are (identity, type-view) pairs), `Ref`/`Var` including inflated properties, singleton/constant heap.
6. Boot the metacircular library: ecstasy + mack + `_native`, injection registry (~25 resource kinds: console, clocks, timers, rnd, storage/dirs, network, keystore, crypto, linker, repository, compiler, properties, hash…), container creation.
7. Implement the 544-member native surface — or the subset the target corpus actually touches, with clean "unsupported injection" failures for the rest.
8. Pass the conformance corpus: TCK (`tck/`), `manualTests`, and differential runs against the Java interpreter.

Explicitly cuttable, still "complete" for running programs: the JIT (both of them), the debugger, `ConstantPool.optimize()` and all of the assembly/write half of `asm`, BAST, the source compiler, and most perf caches. Deferrable natives: crypto, net/web, cert management (fail explicitly at injection time); nested-container injection masking can come after single-container execution works.

## Why This Is Cheaper Than It Looks — Four Accelerants

1. **A written format spec now exists.** The research fork contains `xtc-bytecode-and-constant-pool-spec.md` (258 KB) plus per-construct specs (constructors, fbind/mbind, switch tables, scope/exception). A 2023-era cleanroom would have reverse-engineered `FileStructure.disassemble`; a 2026 one reads a spec and cross-checks two independent implementations (Java `javatools` and the fork's Kotlin `XtcReader`).
2. **The loader is demonstrably small.** The fork's read-only path — `XtcReader` (937 lines) + `XtcConstant` (1,131) + `BinaryModuleLoader` (1,112) + pool model — lands around 4–5k lines of Kotlin. Component 1 above is a solved-size problem.
3. **The type algebra has a second implementation to crib from.** The fork's `Type.kt` (2.3k) + `TypeRelations.kt` (1.5k) + `MemberInfo.kt`/`MemberIndex` (2.7k) reimplement the assignability/member-resolution core in ~6.5k lines — versus 11k+ for Java's `TypeConstant`+`TypeInfo` alone. If the cleanroom constraint is "clean of `javatools`" rather than "clean of everything," reusing or porting these erases the highest-risk component.
4. **The conformance oracle is free.** The Java interpreter runs today; differential testing (same module, compare stdout/exit/exceptions) can gate every week of development. The fork proved this workflow: its compiler reached zero semantic bytecode differences against the Java compiler across 10,587 XDK methods by grinding a convergence harness. The identical method works for a runtime.

## Component Budget (Cleanroom, GC'd Language)

| Component | Estimate (lines) | Notes |
| --- | ---: | --- |
| `.xtc` loader + constant model | 5–8k | fork-calibrated |
| Runtime type system (isA, TypeInfo/call chains, formal types) | 8–15k | the hard, subtle part |
| Interpreter core (frames, 215 ops, guards/finally, deferred values) | 10–15k | mechanical after the first 40 ops |
| Services, fibers, scheduler, proxies, freezing, pass-through | 5–8k | subtle: reentrancy + wait mechanics |
| Object model: handles, Ref/Var, masking, singletons, const heap | 4–6k | |
| Native surface, minimal profile (console/clock/rnd/fs/arrays/numbers/strings/reflection core) | 12–20k | the long tail; grows with corpus |
| Injection/container bootstrap | 2–3k | |
| **Total** | **~45–75k** | vs. ~110k-line Java reference |

The numeric tower deserves one note: Ecstasy's full menu (Int8..Int128, UInt*, Dec32/64/128, Float*) is tedious rather than hard in a "disregard speed" runtime — implement wide/decimal types over host bignum/decimal libraries and move on.

## Schedule, Calibrated

Calibration point: the Kotlin fork went from zero to a fully convergent compiler front end + backend (~230k lines including 93k of tests, 2,302 test methods) in roughly six months of single-developer-plus-agents work. A minimal runtime is a *smaller* semantic surface than a full compiler front end (no parser, no inference engine, no emitter) but has more irreducible behavioral ambiguity (scheduling, proxy semantics, exception edges are under-documented).

| Tier | Content | Wall-clock (1 dev + agents) |
| --- | --- | --- |
| A: hello-world | loader, type-system skeleton, ~60 ops, console injection, single service | 2–4 weeks |
| B: XDK boots | full op set, services/fibers/proxies/freeze, core natives, most TCK green | 3–6 months cumulative |
| C: complete | reflection, full injection surface, nested containers, TCK + manualTests parity, differential-tested | 6–10 months cumulative |

Tier B is the decision-quality milestone: at that point the project knows every place the Java runtime's behavior is surprising, which is most of what a native-runtime effort needs to learn.

The dominant schedule risks are not volume: they are (1) **spec-by-implementation** — dozens of behaviors (op edge cases, implicit conversions, fiber wake ordering, exception text) defined only by what `javatools` does, discovered one failing test at a time; and (2) **the native long tail** — each new corpus program can pull in another `_native` template. Mitigations: differential harness from week one; corpus-driven native implementation (implement what tests demand, stub the rest loudly).

## Language Choice

The single biggest simplifier is a host with garbage collection — it deletes the entire memory-management workstream (the interpreter's objects are host objects, as today's `ObjectHandle`s are JVM objects).

- **Kotlin — recommended.** Reuses the fork's loader and type-relations code directly, matches the team's current tooling, and *is* the "Track A Kotlin reference runtime" already called for in [runtime-port-self-hosting-study.md](runtime-port-self-hosting-study.md) — but buildable now against v1 `.xtc`, without waiting for the frozen module model. Coroutines or Loom virtual threads map cleanly onto fibers.
- **Go — the interesting second option.** Goroutine-per-fiber + channel mailboxes is an almost embarrassing fit for the service model, and it would produce genuinely independent semantics (valuable for a three-way differential oracle). Cost: reimplementing the type algebra without the fork's Kotlin to lean on.
- **TypeScript/Python** — fastest sketching, but the numeric tower (64/128-bit ints) and test-suite wall-clock make them poor hosts for the conformance grind.
- **Rust/C++/Zig — wrong for this study's question.** Without GC they reintroduce the lifetime problem this tier exists to avoid. They are the *native kernel* languages, and that is a different document.

## Strategic Payoff — Why This Study Changes the Plan Ordering

The first-pass plans sequence the Kotlin reference runtime *after* the frozen module model ([runtime-port-scope-plan.md](runtime-port-scope-plan.md) Phase 1 → Phase 2). This study argues the dependency points the other way: a v1-consuming cleanroom interpreter is a 3–6 month project that (a) forces the discovery of every implicit runtime contract — exactly the input the module-model and runtime-ABI designs need; (b) becomes the second conformance oracle that the native runtime will require anyway (see the deopt argument in [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md)); (c) un-blocks the Kotlin compiler's unproven runtime lane (Kotlin-compiled modules currently mis-execute on the Java runtime in ways nobody has root-caused — a second runtime bisects compiler bugs from runtime quirks); and (d) delivers the LSP evaluation engine.

Build it against v1 now; migrate it to the frozen model when the model exists. The reference runtime is the cheapest hard-information purchase available to this whole program.
