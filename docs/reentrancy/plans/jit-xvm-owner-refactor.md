# JIT Xvm Owner Refactor Plan

## Decision

The JIT `Xvm` constructor escape is isolated from the interpreter runtime
ownership work and should be handled as a separate JIT-focused branch/PR.
The JIT does not build on interpreter `Container`, `Frame`, `ObjectHandle`,
`ClassTemplate`, `TypeComposition`, or `NativeTemplates`; it has its own
`org.xvm.javajit.Xvm`, `TypeSystem`, `TypeSystemLoader`, `ModuleLoader`,
`Container`, and `Ctx` ownership graph.

However, a compiling fix is not strictly `javatools/src/main/java/org/xvm/javajit/**`
only. Three shared ASM JIT-name helpers directly dereference
`TypeSystem.xvm`:

- `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java`

Those references force `TypeSystem` to keep a direct final `Xvm` facade field.
Keeping that field preserves the construction cycle; removing it requires
small ASM call-site changes to route through JIT owner APIs on `TypeSystem`.

Under the current background-task write scope, no source change should be
applied. The source patch is safe to prepare as a separate JIT/ASM-metadata PR
only after that scope is explicitly widened to include the three ASM JIT-name
call sites above.

## Original Failure Mode

`Xvm` currently publishes its partially constructed facade during construction:

```java
this.systemRepo       = repo;
this.nativeTypeSystem = NativeTypeSystem.create(this, repo);
```

That call path stores and calls back through the incomplete `Xvm`:

- `NativeTypeSystem.create(this, repo)` constructs the native JIT type system.
- `TypeSystem(Xvm xvm, ...)` stores `xvm` in a public final field.
- The `TypeSystem` constructor calls `xvm.generateTypeSystemName(...)`.
- `TypeSystemLoader` calls `typeSystem.xvm.createModuleLoader(...)`.
- `NativeTypeSystem.registerNativeClasses()` calls `xvm.createUniqueSuffix(...)`.
- `Xvm.createContainer(...)` then creates the native JIT `Container`, which
  stores `typeSystem.xvm`.

At those points these `Xvm` final fields are not assigned yet:

- `nativeTypeSystem`
- `nativeContainer`
- `ecstasyLoader`
- `bridgeLoader`
- `ecstasyPool`

This is bad even on one thread because constructor callbacks can observe
default/null final fields and because leaking `this` before constructor return
weakens the usual final-field publication reasoning. It is worse under
reentrant or parallel startup because a callback, class load, or owner lookup
can reach registries and generated-name caches through an owner facade whose
full startup graph is not complete.

## Replacement Design

Introduce a package-private `XvmState` owned by one JIT runtime. It should be
fully allocated before native type-system creation and own startup services:

- `ModuleRepository systemRepo`
- weak container, type-system, and module-loader registries
- container id counter
- type-system/package/name counters
- lock striping
- native core loader/pool references installed once after native startup

Move these methods from `Xvm` to `XvmState`:

- `createUniqueSuffix(...)`
- `generateTypeSystemName(...)`
- `createModuleLoader(...)`
- `ensureTypeSystem(...)`
- `createContainer(...)`
- weak-reference cleanup helpers and type-system comparison helpers

Then reshape javajit ownership as follows:

- `NativeTypeSystem.create(XvmState, ModuleRepository)` no longer accepts
  `Xvm`.
- `TypeSystem` stores `final XvmState state` instead of `public final Xvm xvm`.
- `TypeSystemLoader` creates module loaders through `typeSystem.state`.
- `Linker` stores `XvmState` and calls `state.ensureTypeSystem(...)`.
- `Container` stores `XvmState` and validates that parent/type-system state
  matches.
- `Ctx` remains the execution-time holder of the completed `Xvm` facade and
  validates that `ctx.xvm.state == ctx.container.state`.
- `Xvm` remains the public immutable facade with the same public final fields;
  its constructor creates state locally, bootstraps native pieces through that
  state, installs the core loader, then assigns final facade fields.

To keep ASM JIT-name code from reaching through `TypeSystem.xvm`, add small
public JIT owner APIs on `TypeSystem`:

```java
public String createUniqueSuffix(String name) {
    return state.createUniqueSuffix(name);
}

public String getReservedNativeName(TypeConstant type) {
    return state.nativeTypeSystem().getReservedName(type);
}
```

The required ASM follow-up is intentionally tiny:

```diff
- ts.xvm.createUniqueSuffix(sNameOrig)
+ ts.createUniqueSuffix(sNameOrig)

- ts.xvm.nativeTypeSystem.getReservedName(this)
+ ts.getReservedNativeName(this)
```

Do not make `Xvm.nativeTypeSystem`, `Xvm.nativeContainer`, or
`TypeSystem.xvm` mutable just to silence `this-escape`. That keeps the warning
quiet while preserving the unsafe lifecycle.

## Semantic And Performance Equivalence

The refactor preserves the existing JIT semantics:

- native container id remains `-1`;
- type-system naming still uses the same counters and module/package logic;
- module-loader naming and weak-map cleanup stay under the same JIT runtime
  owner, just moved from facade fields to `XvmState`;
- `ecstasyLoader`, `bridgeLoader`, and `ecstasyPool` are still derived from the
  native type-system owned loaders;
- `NativeTypeSystem` native-name caches remain type-system caches;
- generated bytecode still receives or obtains `Ctx` the same way.

The only steady-state cost is one more small owner object per `Xvm` plus a few
startup-time indirections. Generated-code hot paths should not gain locks or
new synchronization. `Ctx` may do a one-time owner consistency check at
invocation setup; generated methods still read already-held `Ctx` fields.

## Mutable Static And Owner Patterns To Audit

The JIT avoids the interpreter native-template globals, but it has its own
owner-sensitive patterns:

- Generated `$INSTANCE` fields are classloader/type-system statics. They are
  safe only for values whose owner is genuinely type-system scoped.
- Generated `$scN` constant fields initialize through `Ctx.get()`; class
  initialization under the wrong `Ctx` can cache constants from the wrong
  owner.
- `Ctx.Current` is a `ScopedValue`, which is appropriate for dynamic execution
  context but not a cache owner.
- `NativeTypeSystem.nativeByClass` and `nativeByType` are mutable concurrent
  maps keyed by ASM constants/types; they should remain native type-system
  metadata only.
- `Xvm` weak maps and counters should move into `XvmState` so bootstrap code
  does not need a completed facade.
- `ModuleLoader.loadedClasses` is a mutable `HashMap` used by debug dumping;
  parallel dumping/class loading should be audited separately.
- Process-level debug/build controls such as JIT dump lists, skip sets, and
  reserved-name tables should stay immutable or debug-only.
- JIT bridge code can touch interpreter process resources, for example console
  IO, but it should not store interpreter container-owned runtime objects.

## Tests For The Source Patch

Add focused tests under `javatools/src/test/java/org/xvm/javajit/**`:

1. A source-shape test in `JitConstructorEscapeTest` that rejects:
   - `NativeTypeSystem.create(this`
   - `new TypeSystem(this`
   - `typeSystem.xvm.createModuleLoader`
   - `public final Xvm xvm` in `TypeSystem`
   - `ts.xvm.` in the three ASM JIT-name helpers after the scope is widened.
2. A same-JVM startup smoke, guarded by an assumption that compiled system
   modules are available, that creates two `Xvm` instances and verifies:
   - native containers are distinct and both have id `-1`;
   - each native container points at its own native type system/state;
   - `ecstasyLoader`, `bridgeLoader`, and `ecstasyPool` are populated;
   - constructing `Ctx` with an `Xvm` and a container from different JIT states
     fails fast.
3. If the module repository path proves stable, extend the smoke to parallel
   startup with separate repositories per task.

Suggested commands:

```bash
./gradlew :javatools:test --tests org.xvm.javajit.JitConstructorEscapeTest --console=plain
./gradlew :javatools_jitbridge:compileJava --console=plain
./gradlew :javatools:compileJava :javatools_jitbridge:compileJava \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=1000 \
  --rerun-tasks --console=plain
```

Do not combine `clean` with any other Gradle task.

## Current Task Outcome

No source patch was kept for this background task. The partial javajit-only
state split was reverted after the out-of-scope ASM dependency was confirmed.
This document is the isolated proposal to use for a follow-up JIT owner PR with
an explicitly widened source scope.
