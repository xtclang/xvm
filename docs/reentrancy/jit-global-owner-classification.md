# JIT Global Owner Classification

This document classifies the remaining JIT/global-static ownership issues for
the runtime reentrancy work. It is based on the current tree, especially:

- [jit-implications.md](jit-implications.md)
- [scoped-value.md](scoped-value.md)
- [plans/jit-xvm-owner-refactor.md](plans/jit-xvm-owner-refactor.md)
- [must-audit-backlog.md](must-audit-backlog.md)
- [fixed-in-this-branch.md](fixed-in-this-branch.md)

The interpreter PR and the JIT owner work have different runtime roots. The
interpreter path is rooted at `org.xvm.runtime.Container`,
`NativeTemplates`, `ClassTemplate`, `ObjectHandle`, and `Frame`. The JIT path
is rooted at `org.xvm.javajit.Xvm`, `TypeSystem`, `TypeSystemLoader`,
`ModuleLoader`, `Container`, and `Ctx`.

That means the interpreter ownership changes can break the JIT only through
shared surfaces: the launcher `Connector` API, `ConstantPool` scoping, ASM
metadata and JIT-name helpers, and the bridge jar packaging. They do not make
generated JIT statics or bridge static singletons safe by themselves.

## Top Conclusions

| Item | Classification | Interpreter PR status | Conclusion |
| --- | --- | --- | --- |
| `Xvm` startup owner publication | MUST FIX | OUT OF SCOPE FOR INTERPRETER PR | `Xvm` still passes `this` into `NativeTypeSystem.create(this, repo)` before final owner fields are assigned. Fix in a JIT owner lifecycle PR with an owner/state shell. |
| Generated static injected values | MUST FIX for JIT reentrancy if reachable | OUT OF SCOPE FOR INTERPRETER PR | `CommonBuilder.assembleCLInit(...)` can emit `ctx.inject(...)` into a classloader-static field. That is wrong for container-specific resources unless classloader and container ownership are proven one-to-one. |
| Generated `$INSTANCE` and `$scN` fields | MUST AUDIT | OUT OF SCOPE FOR INTERPRETER PR | Classloader statics are valid only for immutable type-system-owned values. They need owner assertions and two-container tests before claiming JIT ownership safety. |
| Bridge static singletons initialized through `Ctx.get()` | MUST AUDIT, likely MUST FIX before JIT reentrancy claim | OUT OF SCOPE FOR INTERPRETER PR | Bridge enum/enumeration statics are classloader-wide and some choose metadata from the first active `Ctx`. Prove classloader ownership or move them to owner-scoped tables. |
| `Ctx.Current` | MUST AUDIT | OUT OF SCOPE FOR INTERPRETER PR | A `ScopedValue<Ctx>` is the right shape for dynamic generated-code execution, but generated class initialization also depends on it. |
| `Ctx.MD_inject` and builder field-name statics | SHOULD FIX | OUT OF SCOPE FOR INTERPRETER PR | These are not owner-bearing values, but they are mutable public/process statics and should become final immutable constants. |
| Local JIT constructor escapes fixed by this branch | DONE | Done in current branch | `BuildContext`, `JitMethodDesc`/`JitCtorDesc`, `ArrayBuilder`, and `nLongBasedArray` constructor hazards are already covered by focused tests. |

## Classification Matrix

| Area | Status | Evidence | Required closure |
| --- | --- | --- | --- |
| `Xvm` constructor calls `NativeTypeSystem.create(this, repo)` | MUST FIX | `Xvm.java:47` passes the partially constructed facade; `Xvm.java:50-66` assigns `nativeContainer`, `ecstasyLoader`, `bridgeLoader`, and `ecstasyPool` only afterward. | Introduce a JIT owner state/shell as described in `plans/jit-xvm-owner-refactor.md`. Do not make the final fields mutable to silence the warning. |
| `TypeSystem.xvm` direct owner field | MUST FIX as part of the same JIT PR | `TypeSystem.java:120-122` stores `xvm`, generates the type-system name, and creates the loader during construction. `Container.java:41` copies `typeSystem.xvm`. | Move startup services to a prebuilt owner state. Keep the public `Xvm` facade complete before generated execution can observe it. |
| ASM JIT-name helpers reaching through `TypeSystem.xvm` | MUST FIX as part of the same JIT PR | `PropertyConstant.ensureJitPropertyName(...)`, `SignatureConstant.ensureJitMethodName(...)`, and `TypeConstant.buildJitClassName(...)` call `ts.xvm...`. | Add small owner APIs on `TypeSystem` and route these helpers through them so ASM metadata does not force a partially constructed `Xvm` facade. |
| `Ctx.Current` dynamic execution context | MUST AUDIT | `Ctx.java:66-75` defines and reads the scoped value. `JitConnector.java:82-83` binds `new Ctx(xvm, container)`. | Keep `ScopedValue` for dynamic execution, but add owner consistency checks such as `ctx.xvm == ctx.container.xvm` and class-init owner checks. |
| Generated `$scN` static constants | MUST AUDIT | `CommonBuilder.java:657-720` emits `<clinit>`, calls `Ctx.get()`, resolves constants through `ctx.getConstant(className, index)`, and stores them in static final fields. | Generated `<clinit>` must assert that the active `Ctx` belongs to the class's owning `TypeSystemLoader` or native type system. Add two-container tests that force class initialization from both containers. |
| Generated singleton `$INSTANCE` fields | MUST AUDIT, with MUST FIX subcases | `CommonBuilder.java:578-581` emits the field; `CommonBuilder.java:790-804` constructs and stores the singleton during `<clinit>`; `Builder.java:364-389` reads those statics. | Prove that every generated singleton stored in a static is type-system/classloader-owned and immutable, or route it through `Ctx.container` or a container-owned table. Any service, resource, or injector-dependent singleton is a MUST FIX. |
| Static `@Inject` constant properties | MUST FIX if reachable | `CommonBuilder.java:737-747` emits `ctx.inject(...)` followed by `putstatic(...)` for injected constant properties. | Do not cache container-specific injection results in generated class statics. Either reject this shape for now or store/cache by JIT container. |
| Instance and local injection through `Ctx` | DONE for current model, still audit in stress | `BuildContext.java:1906-1912` resolves injected local refs from the active `Ctx`; `CommonBuilder.java:1327-1345` resolves instance injected properties through the method `Ctx` and stores on the instance. | Keep routing through `Ctx.inject(...)`. Add two-container tests with different injectors to prove no class static captures the resource. |
| `Ctx.MD_inject` | SHOULD FIX | `Ctx.java:186-187` declares a mutable public static `MethodTypeDesc`. | Make it `public static final`. It is not owner-bearing, so it is not an interpreter PR blocker. |
| Bridge jar loading model | MUST AUDIT | `javatools_jitbridge/build.gradle.kts:10-17` says the jar is a binary blob, not normal classpath use. `NativeTypeSystem.java:61-71` creates a bridge `URLClassLoader` for resource parsing, and `NativeTypeSystem.java:160-202` returns class bytes for definition by JIT loaders. | Keep the bridge jar off the normal runtime classpath. Add tests or diagnostics showing bridge classes are defined by the intended JIT `ModuleLoader`, not the app/system loader. |
| Bridge injector ownership | DONE for current code shape, needs stress | `nMainInjector.java:24-30` stores one `Xvm` and one instance `HashMap` per injector; `nMainInjector.java:49-55` registers console supplier; `Ctx.java:162-165` asks the active container's injector. | Keep supplier maps instance-owned. Stress with two containers with different injectors and assert resource lookup is container-local. |
| Bridge `TerminalConsole` | MUST AUDIT | `TerminalConsole.java:24-25` calls `super(Ctx.get())`; `TerminalConsole.java:44-48` delegates to interpreter terminal process resources. | Safe only if construction occurs inside a bound JIT invocation and terminal IO is intentionally process-wide. Add a source-shape/behavior test for construction outside `Ctx`. |
| Bridge enum/enumeration statics | MUST AUDIT, likely MUST FIX before JIT reentrancy claim | `Boolean.java:20-31`, `eBoolean.java:14-20`, `eNullable.java:10-17`, `eOrdered.java:10-23`, `Array.java:154-174`, and `FPNumber.java:521-536` create static enum/enumeration values, sometimes from `Ctx.get()` or `$ctx()`. | Either prove these statics are native-type-system/classloader-owned for one `Xvm`, or move metadata/singletons behind a type-system or container owner table. Reject `static ... Ctx.get()` outside an allowlist. |
| `nObject.$ctx()`, `$xvm()`, and `$owner()` | MUST AUDIT | `nObject.java:26-36` recovers the current context and `Xvm`; `nObject.java:54-64` resolves owner by current `Xvm` plus encoded owner id. | Add tests that static native objects used under a second `Ctx` resolve the intended owner, not the first class initializer's owner. |
| Classloader/type-system sharing | MUST AUDIT | `TypeSystem.java:82-102` documents shared modules; `TypeSystemLoader.java:68-80` delegates to owned and shared module loaders; `ModuleLoader.java:85-101` defines classes and records class bytes. | Document which generated classes can be shared. Generated statics may hold only classloader/type-system-owned immutable state. |
| `ModuleLoader.loadedClasses` debug map | SHOULD FIX, MUST AUDIT if dumping is concurrent | `ModuleLoader.java:100` writes to `loadedClasses`; `ModuleLoader.java:123-166` swaps and iterates a plain `HashMap` during dump. | Make dumping snapshot-based or synchronized before parallel JIT dumping. This is debug state, not interpreter PR scope. |
| `NativeTypeSystem` native-name caches | MUST AUDIT | `NativeTypeSystem.java:138-143` has concurrent maps keyed by ASM identities/types; `NativeTypeSystem.java:267-314` fills them from the native pool. | Keep these metadata-only. Do not store `Ctx`, `Container`, injector results, handles, or service instances in these maps. |
| JIT name caches on shared ASM constants | MUST AUDIT | `TypeConstant.java:7143-7154`, `SignatureConstant.java:731-746`, `PropertyConstant.java:367-385`, and `MethodConstant.java:287-325` cache JIT names on ASM metadata. This branch clears some copied JIT caches during constant adoption. | Prove the cached name is valid for the owner pool/module loader that owns the constant, or key/cache by owner. Keep adoption clearing tests. |
| `ConstantPool.m_setJitPrimitives` | SHOULD FIX soon | `ConstantPool.java:2189-2223` lazily builds an unmodifiable set into a plain transient field. | Owner-local and not a global JIT singleton, but use a final `Lazy` or concurrent publication if runtime/JIT compilation uses one pool concurrently. |
| `NativeNames.reservedMethodName` | SHOULD FIX | `NativeNames.java:21-22` initializes a mutable static final `HashMap`; readers treat it as read-only. | Build an immutable `Map` after static initialization. Not owner-bearing, but process-global mutable state is avoidable. |
| `EnumerationBuilder.NAMES`, `EnumerationBuilder.VALUES`, `EnumValueBuilder.NAME` | SHOULD FIX | These are mutable static strings used as field-name constants. | Make them `static final`. Not owner-bearing. |
| `JitConnector` lifecycle fields | MUST AUDIT for concurrent reuse | `JitConnector.java:42-67` mutates `module`, `ts`, and `container`; `JitConnector.java:81-183` invokes and dumps through that state. | Define connector instances as single-load/single-run or synchronize lifecycle. Same-JVM stress should use separate connectors unless reuse semantics are added. |
| Interpreter `OwnershipDiagnostics` hook | OUT OF SCOPE FOR JIT | `Connector.java:108-120` returns `null` by default; `InterpreterConnector.java:135-137` returns an interpreter `MainContainer`. | Do not force JIT into interpreter diagnostics. Add a JIT-specific dump rooted at `Xvm`. |
| Local JIT constructor escape cleanup | DONE | `BuildContext.forMethod(...)` and `forProperty(...)` bind `TypeMatrix` after construction; `JitCtorDesc` passes implicit params as constructor data; `ArrayBuilder` reads the supplied `TypeSystem`; `nLongBasedArray` writes packed state directly. | Keep `JitConstructorEscapeTest` and lint shape tests. |
| JIT helper state cleared on constant adoption | DONE for branch scope | `TypeConstant.setContaining(...)` clears `m_sJitName`; adoption tests cover JIT helper state in type/signature constants. | Keep the branch tests and continue the broader JIT-name owner audit separately. |

## Classloader And Container Rule

The JIT can intentionally share generated Java classes through
`TypeSystemLoader` and `ModuleLoader`. That is a valid performance model only
if the static state in those classes is owned by that same classloader/type
system. The rule for future JIT work should be:

- Static generated fields may cache immutable metadata owned by the generated
  classloader/type system.
- Static generated fields must not cache `Ctx`, JIT `Container`, injector
  results, services, resource handles, mutable runtime objects, or values whose
  semantics depend on the active container.
- If generated code needs container-owned state, it must obtain it from
  `Ctx.container`, `Ctx.inject(...)`, or a container-owned table.
- Bridge classes are shared code. Bridge resource state still belongs to the
  active `Ctx`/container unless explicitly documented as process-wide, such as
  terminal IO.

## Can Interpreter Changes Break JIT?

Yes, but only through shared surfaces:

- `ConstantPool.withPool(...)` and explicit-pool changes can break JIT linking
  or bridge startup because JIT code still uses shared `ConstantPool` and ASM
  metadata.
- ASM owner/adoption changes can affect JIT name caches, type relation helpers,
  and generated class names.
- `Connector` lifecycle changes can break `JitConnector` because the launcher
  chooses it through the same `Runner` path.
- Build/distribution changes can break bridge discovery because the bridge jar
  is disk-probed rather than a normal runtime dependency.

The interpreter native-template changes should not directly break generated
JIT bytecode because the JIT does not use interpreter `Frame`, interpreter
`Container`, `ObjectHandle`, `ClassTemplate`, `TypeComposition`, or
`NativeTemplates` as its execution model. The existing JIT smoke proves only
that the launcher-level JIT path still starts and runs a simple program. It
does not prove generated static ownership, bridge singleton ownership, or
parallel JIT reentrancy.

## Recommended Guards And Tests

Add these to a JIT-focused follow-up, not to the interpreter owner PR:

- Source-shape tests that reject `NativeTypeSystem.create(this`, `new
  TypeSystem(this`, `typeSystem.xvm.createModuleLoader`, `public final Xvm xvm`
  in `TypeSystem`, and `ts.xvm.` in the ASM JIT-name helpers.
- `Ctx` construction checks that reject an `Xvm` and `Container` from different
  JIT owners.
- Generated `<clinit>` debug assertions that compare the active `Ctx` owner to
  the generated class's owning `TypeSystemLoader` or native type system.
- A two-container same-JVM JIT test with different injectors that forces
  generated `$scN`, `$INSTANCE`, injected static properties, bridge enum
  singletons, arrays, exceptions, and console injection.
- A parallel variant of that test to exercise JVM class initialization races.
- A bridge source-shape test that rejects `static ... Ctx.get()` unless the
  site is documented as classloader-owned and tested.
- A JIT ownership dump rooted at `Xvm` that lists containers, type systems,
  module loaders, generated class names, classloader identities, `$scN` fields,
  `$INSTANCE` fields, bridge singleton owner ids, and injected resource owners.
- A bridge classloader test that proves `org.xtclang...` classes used by JIT
  execution are defined by the intended JIT `ModuleLoader`, not by the system
  loader or ordinary app classpath.

Suggested verification commands for the JIT PR:

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

## PR Split

1. Interpreter ownership PR:
   - Keep the interpreter runtime owner fixes, adoption fixes, and
     interpreter `OwnershipDiagnostics`.
   - Keep the JIT constructor-cleanup tests already in this branch.
   - Do not attempt the `Xvm` startup refactor or generated-static policy in
     this PR.

2. JIT owner lifecycle PR:
   - Introduce the `XvmState` or equivalent owner shell.
   - Remove `TypeSystem.xvm` as the startup-time service path.
   - Route ASM JIT-name helpers through `TypeSystem` owner APIs.
   - Add startup, owner consistency, and source-shape tests.

3. JIT generated-static and bridge PR:
   - Prove or replace generated `$INSTANCE` and `$scN` ownership.
   - Remove or justify static `Ctx.get()` bridge initialization.
   - Fix generated static injection caching.
   - Add two-container and parallel JIT ownership tests.

4. JIT cleanup PR:
   - Make `Ctx.MD_inject`, builder field-name strings, dump lists, and
     reserved-name maps immutable.
   - Make debug dumping snapshot/synchronization safe.
   - Harden owner-local JIT lazy caches where concurrent use is expected.
