# The javajit Runtime: What It Is, Where It Is Going, and What Must Not Be Repeated

Written 2026-08-25 at user request, from a full source read of
`javatools/src/main/java/org/xvm/javajit/` (~20,400 LOC, 43 files plus
`builders/`) and `javatools_jitbridge/` (132 files), the JIT milestone commits
on `origin/master`, and the branch audits
(`jit-implications.md`, `jit-global-owner-classification.md`,
`plans/jit-xvm-owner-refactor.md`). Every claim is EVIDENCE (file:line, commit,
comment) unless explicitly marked INFERENCE. The actionable issue inventory
lives in `plans/master-issue-submissions.md` rows 21-24 (launch rows J21-J24);
the shape pins live in `JitConcurrencyHazardCensusTest`.

## 1. What the JIT runtime is

An Ecstasy-to-JVM-bytecode compiler plus a thin execution shell. It does NOT
interpret ops; it translates XVM structures into real Java classfiles and lets
the JVM run them.

The pipeline, end to end (`JitConnector`):

1. `Xvm.create(repo)` boots "Container -1": `NativeTypeSystem` merges the
   `ecstasy`/`turtle`/`_native` modules into one FileStructure
   (NativeTypeSystem.java:99-105) and serves core classes from the
   `javatools_jitbridge` jar - hand-written Java classes (`nObject`,
   `nService`, `nType`, `nFunction`, enum bridges, arrays, numbers), some used
   as-is, some parsed and AUGMENTED with generated Ecstasy methods woven in
   (`augmentNativeClass`, :211-252).
2. `loadModule(app)` builds an app `TypeSystem` via `Linker.link()`, which
   splices the shared ecstasy ModuleStructure into the app FileStructure
   (Linker.java:379-384).
3. `invoke0(...)` binds `ScopedValue.where(Ctx.Current, new Ctx(xvm,
   container))`, then loads the generated module class through the type
   system's classloaders - and THIS is where compilation happens:
   `ModuleLoader.findClass` calls `typeSystem.genClass(...)` and
   `defineClass` on demand (ModuleLoader.java:85-107). Classfile generation is
   lazy inside JVM class loading.
4. The generated `run(Ctx)` executes as ordinary Java bytecode.

Technology: the JDK Class-File API (`java.lang.classfile.*`) - not the ASM
library ("asm" in this codebase always means XVM's own `org.xvm.asm` IR).
Generated code uses `invokedynamic` + `StringConcatFactory` for string
templates (BuildContext.java:3278-3288) and `MethodHandle` constants for
function values. `Ctx` is the per-logical-thread execution context (multi-
return scratch slots, constant lookup, injection), reached ambiently via
`ScopedValue<Ctx> Ctx.Current`.

Completeness: codegen is allowlist-gated. `CommonBuilder.JIT_LIST`
(CommonBuilder.java:4012-4117) enumerates ~100 core-library classes that
compile for real; everything else gets a STUB body returning a default value
with a stderr "Skipping code gen" line. ~25 methods are individually
blacklisted with cataloged TODO reasons (`NO_JIT_METHODS`). A launcher-level
smoke test works (`xec -J EchoTest` runs and exits 0); the dedicated
`manualTests/src/main/x/jit` suite is not wired into the default source set.
97 TODOs in javajit, 113 in the bridge; memory accounting is all stubs; the
`Future` classfile shape is declared but produced by no builder.

## 2. Relationship to the interpreter

Two disjoint execution worlds over one shared compile-time model.

- Disjoint: nothing in `org.xvm.javajit` uses `Frame`, `ServiceContext`,
  `ObjectHandle`, `ClassComposition`, `CallChain`, or interpreter
  `org.xvm.runtime.Container`. The JIT's execution state is generated classes
  plus `Ctx`. There is no mechanism to pass objects between an interpreter
  container and a JIT container. The single runtime-level dependency is the
  bridge `TerminalConsole` delegating console IO to the interpreter's process
  globals (`xTerminalConsole.CONSOLE_OUT/IN`, TerminalConsole.java:44-71).
- Shared: the ENTIRE `org.xvm.asm` layer - FileStructure/ModuleStructure,
  ConstantPool, TypeConstant/TypeInfo/MethodInfo, the isA relation calculus.
  Section 4 maps these edges precisely, because they are the thread-unsafe
  surface this branch has been hardening.
- Selection: both backends implement `org.xvm.api.Connector`;
  `xec -J`/`--jit` picks `JitConnector` over `InterpreterConnector`
  (Runner.java:352-354). The interpreter is the default; the JIT is opt-in
  per run.

## 3. Is it intended to replace the interpreter?

EVIDENCE:

- README.md:96-100 describes the project as "a proof-of-concept interpreted
  runtime; a JIT compiler targeting the JVM (in development)". Labeling the
  interpreter proof-of-concept is the strongest in-repo statement of
  direction.
- 28 "Java JIT project milestone" commits on master over 15 months (May 2025
  through 2026-08-17), plus active daily-cadence branches (`origin/JIT` is 15
  commits ahead, last 2026-08-20). One milestone message says "aligning the
  JIT and interpreter execution".
- A full naming/specialization design spec exists (`doc/jit_class_names.txt`).
- NOTHING in the repo says "replace", "successor", or "deprecate the
  interpreter". No completion target, no measured coverage fraction. The
  evidence is silent on a formal decision.

INFERENCE (asked directly for a judgment): yes, the trajectory reads as
intended replacement - the README labeling, the sustained milestone cadence,
and the shared Connector abstraction all point that way. But two facts
temper the concern:

1. The interpreter is today the only complete runtime, and the JIT compiles a
   curated slice of lib_ecstasy with everything else stubbed. Replacement is
   not close.
2. The hardest parts of a runtime - service/fiber semantics, async
   boundaries, container ownership, memory accounting - are exactly the parts
   the JIT has NOT built. There is no scheduler, no executor, no thread
   creation anywhere in javajit or the bridge. `nService.callLater` executes
   IMMEDIATELY on the caller's thread (nService.java:29-39); futures and
   timeouts throw `UnsupportedOperationException`. The `Ctx` javadocs promise
   virtual-thread fibers with memory-accounting-driven parking, all attached
   to TODO stubs.

So "Java-bytecode jitting may not be the only way to go" remains a live
architectural question: the bet that XTC's service/fiber/container semantics
can be realized efficiently in generated JVM bytecode is UNPROVEN in this
repo - the semantics simply are not implemented yet. What is proven is only
that straight-line code and a curated core library can be compiled and run.
The decision point (and the concurrency danger, section 5) arrives when the
scheduler lands.

## 4. Exactly which thread-unsafe runtime/compiler code the JIT depends on

This is the dependency map for later patching. The JIT does NOT sit on the
interpreter runtime (`org.xvm.runtime`), so none of this branch's
handle/Frame/ServiceContext hardening protects or constrains it. It sits
DIRECTLY on the shared `org.xvm.asm` layer - the code this branch spent weeks
fencing - through these exact edges:

| # | JIT call site | Shared unsafe surface it touches | When it runs |
|---|---|---|---|
| 1 | `CommonBuilder.assembleCLInit` - `pool.register(type)` (builders/CommonBuilder.java:706), `type.ensureTypeInfo()` (:725) | ConstantPool interning + the lazy TypeInfo build machinery (relation caches, invalidation lists) | LAZILY, inside JVM class loading, at first use of each generated class - i.e. at RUNTIME |
| 2 | `TypeSystem.genClass` builders consuming `typeInfo.getMethods()` etc. | TypeInfo/MethodInfo lazy calculus and its caches | Same: runtime class loading |
| 3 | `Linker.link()` - `removeChild`/`addChild` splice of the shared ecstasy ModuleStructure into the app FileStructure (Linker.java:379-384), `synthesizeChildren()` (:335) | Component child maps (`m_childByName`) and FileStructure identity - the exact structures this branch converted to copy-on-write publication | Link time (per TypeSystem creation) |
| 4 | `NativeTypeSystem` boot - module merge (:99-105), `registerNativeClasses` pool mutation via `ensureParameterizedTypeConstant` (:280-306) | ConstantPool interning on the merged native pool | Xvm boot (single-threaded today) |
| 5 | JIT name caches ADDED to shared ASM objects: `TypeConstant.m_sJitName` (TypeConstant.java:8378, built :7111-7126), `SignatureConstant.m_sJitName` (:988, built :731-742 with a per-Xvm suffix counter), `ConstantPool.m_setJitPrimitives` (:4474) | New lazy, non-volatile mutable state ON constants shared across pools and across Xvms | Lazily during codegen |
| 6 | `OpCondJump.buildUnary` - `cond.evaluate(Ctx.get().container)` (OpCondJump.java:513-526) | Conditional-constant evaluation against pool/container state, baked into generated bytecode at build time | Runtime class loading |
| 7 | `nMainInjector.addNativeResources` - `ensureEcstasyTypeConstant` (:50-51); bridge statics (`Array.Mutability`, `ContainerControl`, `TerminalConsole`) calling `ensure*Constant` on the ecstasy pool | ConstantPool interning from BRIDGE class initializers | JVM `<clinit>` of bridge classes - whenever the first load happens |
| 8 | `Ctx.getConstant(className, index)` (Ctx.java:148-157) | Pool constant lookup by baked-in index through the ambient container's type system | Every generated `<clinit>` |

Two consequences worth stating plainly:

- **The interpreter-side fences will fire on the JIT.** This branch's
  publication marker rejects post-publication `pool.register` outside a
  synthesis window; edges 1, 5, and 7 register constants lazily at runtime by
  design. When the JIT is run against this branch's javatools, those paths
  need the same treatment fiber execution got (`openRuntimeSynthesisWindow`
  is thread-scoped precisely so one execution plane can cover every pool it
  derives into) - or the structural frozen-pool snapshot when that lands.
  This is the integration point to patch, not bypass.
- **The patch recipe already exists.** Detached build -> assemble ->
  first-wins publish (delegation synthesis), copy-on-write child maps
  (`Component.publishRuntimeChild`), thread-scoped synthesis windows, and
  owner-passed pools are exactly the shapes edges 1/3/5 need. Row 24 of the
  submissions doc records this as the fix strategy.

## 5. The concurrency/reentrancy issues the JIT has TODAY

Full detail with file:line evidence: submissions rows 21-24. Summary, with
the classification the user asked for:

**Already a problem in master, single-threaded:**

- Class initialization on any thread without a bound `Ctx` dies with
  `NoSuchElementException` (every generated `<clinit>` starts with
  `Ctx.get()`; so do the bridge singletons).
- The first container to trigger a class's `<clinit>` permanently donates its
  constants (`$scN`), injected values (`@Inject` statics), and singleton
  identities (`$INSTANCE`) to every later container sharing the loaders -
  sequential same-JVM reuse is broken by construction, the exact scenario
  that started this branch's investigation on the interpreter side.
- `JitConnector` is single-use and unconditionally deletes/rewrites a
  cwd-relative `./jasm` dump tree on every invocation.
- Visible-on-read bridge bugs: `Ordered.enumeration$get` and
  `FPNumber.Rounding.enumeration$get` return `eBoolean.$INSTANCE`.

**Becomes a correctness/security problem the moment anything is concurrent:**

- Every `<clinit>`/bridge-static initialization race; non-final `$INSTANCE`
  fields; raw shared `$values` arrays.
- Classloading races: loaders not parallel-capable, direct `findClass`
  delegation bypassing per-name locks, `defineClass` duplicate-definition
  `LinkageError`, plain-HashMap `loadedClasses`.
- `Xvm.typeSystems` name-collision window (mutex keyed by module-shape string
  while probing a shared name space); `moduleLoaders` writer-locked but
  reader-unlocked sparse arrays.
- Concurrent lazy classgen = concurrent unguarded `pool.register` and
  TypeInfo builds (edges 1/2 above).
- Cross-Xvm pollution through `m_sJitName` caches and the shared
  ModuleRepository FileStructure splice.
- `nType` captures its creating `Ctx` in an instance field with unsynchronized
  lazy caches; the moment the `TODO: cache type -> nType` lands this is a
  cross-fiber leak of per-thread scratch state.
- `OpCondJump` bakes one container's conditional-constant answer into shared
  generated bytecode (latent only while `Container.isSpecified` hardcodes
  debug/test to true).

**Fixed on this branch already:** `Xvm` boot constructor escape (factory +
volatile Boot record, `JitConstructorEscapeTest`); JIT failure misreporting
(`JitFailurePropagationTest`); `NativeNames`/`Ctx.MD_inject`/builder statics
frozen. **Unit tests pinning the open hazards:**
`JitConcurrencyHazardCensusTest` (ratchet-style shape pins for the Ctx
validation gap, the loader registry, the builder skip sets, and the shared
constant name caches; bridge-side pins need the bridge jar on a test
classpath and are part of the J21/J22 issue asks, as is the two-container red
harness specified in `jit-global-owner-classification.md:149-171`).

## 6. Do-not-duplicate rules (the point of this document)

The JIT is young enough to fix cheaply. The interpreter cleanup gives the
exact rules, each of which the JIT currently violates:

1. **No ambient owner state.** `Ctx.Current`/`$ctx()`/`$xvm()`/`$owner()` is
   `withPool` reborn. Pass `Ctx` explicitly through the bridge API surface;
   assert `ctx.xvm == ctx.container.xvm` at construction.
2. **No owner state in classloader statics.** Generated `$scN`/injected/
   `$INSTANCE` state must be container-scoped (per-container tables reached
   via Ctx, or per-container loaders), or first-capture must fail loudly.
3. **No unguarded mutation of published pools.** Runtime-lazy classgen must
   intern under the same publication discipline as fiber execution: marker +
   thread-scoped synthesis window now, frozen-pool snapshot as the closure.
4. **No check-then-create over shared registries without one lock.**
   `ensureTypeSystem` name probing and loader `findClass` delegation need the
   JVM's own classloading locks (`registerAsParallelCapable` + `loadClass`)
   and single-mutex name registration.
5. **No lazy non-volatile caches on shared metadata.** `m_sJitName` and
   friends must be volatile/CAS and scoped to the owner that computed them.
6. **Two-container tests before the scheduler.** The fiber scheduler is
   unbuilt; every rule above becomes load-bearing the day it lands. The
   two-container/parallel-load red harness must exist FIRST - that is the
   cheapest moment in this runtime's history to enforce ownership.
