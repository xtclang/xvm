# Javac Lint Parallelism Risk Audit

This started as a docs-only background audit of `javac` lint diagnostics other
than `this-escape` that could plausibly affect parallel runtime ownership,
reentrancy, stale owner data, or same-JVM repeated execution. The branch now
also fixes the four `overrides` diagnostics because they were small and carried
real cache-key correctness risk.

This note is independent from the main `ClassTemplate` `this-escape` work, but
it records source fixes when a lint category was proven relevant to same-JVM
cache or ownership safety.

## Sources

Primary existing log:

```text
/tmp/xvm-current-this-escape.log
```

Cross-check logs used only to see whether the same categories appeared in
nearby lint reruns:

```text
/tmp/xvm-reentrancy-audit/root-build-xlint-all-rerun-after-clean.log
/tmp/xvm-reentrancy-audit/root-build-final-lint.log
/tmp/xvm-lint-wave5.log
/tmp/xvm-lint-wave6.log
/tmp/xvm-lint-wave7.log
/tmp/xvm-utils-this-escape-lint.log
```

Read-only commands used:

```bash
rg -o 'warning: \[[A-Za-z0-9_.-]+\]' \
  /tmp/xvm-current-this-escape.log \
  /tmp/xvm-reentrancy-audit/root-build-xlint-all-rerun-after-clean.log \
  /tmp/xvm-reentrancy-audit/root-build-final-lint.log \
  /tmp/xvm-lint-wave5.log \
  /tmp/xvm-lint-wave6.log \
  /tmp/xvm-lint-wave7.log \
  /tmp/xvm-utils-this-escape-lint.log |
  sed -E 's/.*warning: \[([^]]+)\]/\1/' | sort | uniq -c | sort -nr

rg -n 'warning: \[(rawtypes|unchecked|fallthrough|try|serial|classfile|overrides|cast)\]' \
  /tmp/xvm-current-this-escape.log
```

Small source slices were read around representative warning sites. No lint
compile was run for this audit.

## Categories Seen

Current log category counts from `/tmp/xvm-current-this-escape.log`:

| Category | Count | Parallelism relevance |
| --- | ---: | --- |
| `rawtypes` | 201 | High when the site is runtime, ASM, constant/type, compiler, JIT, or async-service state. |
| `unchecked` | 144 | High for the same sites as `rawtypes`; often the paired symptom. |
| `fallthrough` | 112 | High when the switch is a runtime lifecycle, service/frame, compiler, JIT, or owner-construction state machine. |
| `this-escape` | 80 | Out of scope for this note. |
| `serial` | 10 | Usually style/build hygiene for this parallelism topic. |
| `try` | 9 | Important guard-shape signal for `ConstantPool.withPool(...)`; usually not itself a bug. |
| `overrides` | 4 | Fixed in this branch after checking structural key semantics. |
| `classfile` | 4 | External dependency/classpath hygiene. |
| `cast` | 1 | Style-only unless hiding a real owner type distinction. |

Aggregate occurrences across all listed logs, including duplicate reruns:

```text
859 rawtypes
608 unchecked
444 fallthrough
416 this-escape
 66 try
 41 serial
 25 classfile
 16 overrides
  4 cast
```

The aggregate numbers are not unique-site counts. They are useful only as a
stability check that the same non-`this-escape` categories recur across logs.

## Likely Must-Fix For Parallelism

These categories are likely must-fix when they touch owner-bearing runtime
state, service/fiber async boundaries, constant/type metadata, compiler
request state, JIT owner state, or same-JVM reused tool state. The category is
not automatically a must-fix everywhere.

| Category | Why it can matter | Representative sites | Proper fix or guard |
| --- | --- | --- | --- |
| `rawtypes` / `unchecked` | Erased types hide the owner or payload contract. In runtime paths, that can let a future, response, cache, constant, or handle carry the wrong owner without a compile-time boundary. | `runtime/Fiber.java`: raw `CompletableFuture` and unchecked pending-request map casts around service request tracking. | Parameterize futures and pending maps, or replace the `Object` union with a typed holder. Keep the documented service-thread confinement and add assertions if callbacks can move threads. |
| `rawtypes` / `unchecked` | Service response dispatch crosses fibers and service contexts. Raw `Response`, raw futures, and unchecked conversions make it harder to prove that tuple, single-handle, and multi-return payloads are not mixed. | `runtime/ServiceContext.java`: raw `Response`, raw `CompletableFuture`, unchecked `assignFutureResult` and response construction sites. | Use typed response variants or a sealed payload shape. Parameterize every `CompletableFuture` by the actual payload and keep owner validation on native/service boundaries. |
| `rawtypes` / `unchecked` | Heterogeneous op-info caches can retain derived info across an `Op`. If an `Op` or cached info is reused across owners, erased enum/cache types hide the key contract. | `runtime/ServiceContext.java:226-244`: raw `Enum`, `EnumMap`, and `WeakReference` in `getOpInfo`/`setOpInfo`. | Introduce a typed `OpInfoKey<E extends Enum<E>>` or another owner-aware key wrapper. If the cache is owner-local by construction, assert and document that confinement. |
| `rawtypes` / `unchecked` | Reflective native template discovery is owner startup code. Raw `Class` weakens the proof that every loaded class is a `ClassTemplate` subclass before instantiation. | `runtime/NativeContainer.java`: raw `Class` maps and unchecked reflective conversion; unchecked `System.getProperties()` key-set cast. | Use `Map<String, Class<? extends ClassTemplate>>`, `Class.forName(...).asSubclass(ClassTemplate.class)`, and a typed instantiation helper. Use typed property iteration instead of double-casting the key set. |
| `rawtypes` / `unchecked` | Reentrancy guards and constant/type caches are central to avoiding stale owner data. A raw updater or unchecked type-info conversion can hide an invalid value in exactly the path meant to prevent recursion or owner mixing. | `asm/constants/TypeConstant.java:8265`: raw `TransientThreadLocal` updater for the `isA` in-progress set. `TypeConstant`, `TypeInfoReal`, `ClassStructure`, and `ConstantPool` also have unchecked conversion sites in type metadata paths. | Prefer typed holders or `VarHandle` wrappers. If erasure is unavoidable for `AtomicReferenceFieldUpdater`, isolate it in one helper with a narrow suppression, runtime type asserts, and focused recursion/parallel type-info tests. |
| `rawtypes` / `unchecked` | Version/module/type trees are long-lived ASM structures that same-JVM compilation and module loading can reuse. Raw nodes and unchecked assignments make it harder to prove values are not from another module graph. | `asm/VersionTree.java`, `asm/ModuleStructure.java`, `runtime/template/_native/reflect/xRTModuleTemplate.java`. | Make tree nodes generic end-to-end, or isolate unavoidable array-erasure casts behind checked constructors. Add tests that build independent module graphs in one JVM and check they do not share mutable node/value state. |
| `fallthrough` | Switch fallthrough is a state-machine edge. In owner startup, frame dispatch, annotation construction, service execution, parser/compiler phases, and JIT generation, an unintended edge can skip initialization, repeat work under the wrong owner, or leave mixed lifecycle state. | `runtime/ClassTemplate.java:2294-2382` construction-stage switch, `runtime/ServiceContext.java:568/639/746`, `runtime/Utils.java:1511/1552/1581`, `runtime/template/_native/collections/arrays/xRTDelegate.java:535`, compiler/parser/name-resolution switches, and JIT `BuildContext`/`AugmentingBuilder` sites. | Do not mass-add `break`. For each site, prove whether the edge is intentional. Prefer explicit `continue`, `return`, or extracted stage methods. If fallthrough remains intentional, use a local suppression with an owner/state comment and add tests around the transition. |

The highest-risk slice is the intersection of:

- runtime package,
- service/fiber/future response flow,
- owner-bearing handles or constants,
- `ConstantPool`/`TypeConstant`/`TypeInfo` metadata,
- native-template startup,
- compiler/JIT state reused by same-JVM tooling,
- and state-machine switches that control lifecycle progression.

## Should-Fix Or Guard

These categories should still be cleaned up, but the diagnostic alone is not a
parallelism blocker.

| Category | Representative sites | Why not automatically must-fix | Proper fix or guard |
| --- | --- | --- | --- |
| `try` | `asm/MethodStructure.java:666`, `asm/FileStructure.java:181`, `runtime/Container.java:117`, `runtime/NativeContainer.java:104`, `runtime/MainContainer.java:193`, `javajit/NativeTypeSystem.java:108`, `javajit/JitConnector.java:64`. | These are mostly `try (var ignore = ConstantPool.withPool(pool))` lexical owner scopes. The resource is intentionally unused because construction and close bind/unbind the current pool. Removing the scope would be dangerous. | Keep the scope. Add `ConstantPool.assertCurrentPool(...)` at owner boundaries where practical. Prefer a helper such as `ConstantPool.withPool(pool, action)` or a narrow `@SuppressWarnings("try")` with an ownership comment over deleting the resource. |
| `overrides` | Fixed in this branch for `asm/VersionTree.java`, `asm/Register.java`, `asm/Register.ShadowRegister`, and `asm/constants/ChildInfo.java`. | Equals-without-hashCode is not a direct data race, but it is a real cache-key contract bug. `HashMap`/`HashSet` use the hash bucket before `equals(...)`, so equal objects with different identity hashes can be duplicated or missed even without threads. | Structural equality was intended, so each type now implements `hashCode()` from the same fields as `equals(...)`. The hashes are recomputed instead of cached because these are mutable assembly/compiler metadata objects without a proven freeze point. |
| `serial` | `runtime/ObjectHandle.java:669`, `runtime/gc/SegFault.java:6`, `tool/Launcher.java:969`, `compiler/CompilerException.java:10`, `asm/constants/MapConstant.java:355`, `type/Decimal.java`, JIT bridge exceptions. | Serialization metadata is not part of the runtime owner model unless these objects are serialized across same-JVM or external tool boundaries. | Add `serialVersionUID` where serialization is intentional. Mark non-serializable fields `transient` or stop implementing `Serializable` where it is accidental. |
| `classfile` | acme4j jar warnings about missing `SuppressFBWarnings` annotation metadata. | The warnings are from external class files, not XVM runtime ownership code. | Build hygiene: add the missing annotation dependency if warning-free builds matter, or suppress/exclude external classfile lint. |
| `cast` | `asm/ConstantPool.java:2444`: redundant cast to `ClassConstant`. | A redundant cast by itself does not change owner or reentrancy behavior. | Remove during nearby cleanup unless the cast is intentionally documenting an invariant; then replace it with an assertion or helper name. |
| `rawtypes` / `unchecked` in tests | GC tests use raw `ReferenceQueue`, `Reclaim`, and related helpers. | Test-only raw types can obscure test intent but do not directly publish runtime owner state. | Parameterize tests when touching them so future race tests fail for the right reason. |

## Concrete Site Notes

### Service And Fiber Futures

`Fiber` and `ServiceContext` have repeated raw `CompletableFuture`, raw
`Response`, and unchecked response assignment warnings. These are important
because futures are the runtime's cross-service handoff mechanism. The current
code comments in `Fiber` say pending counters and uncaptured maps are service
thread confined, which is a useful guard. The lint debt remains relevant
because erasure hides whether a future completes with one `ObjectHandle`, a
tuple, an array of handles, or an exception wrapper.

Recommended direction:

- type message futures by payload,
- type response objects by return shape,
- avoid raw `CompletableFuture` local variables,
- move unchecked casts into one checked boundary when Java's type system cannot
  express the union,
- and assert service-thread confinement where callback timing matters.

### Runtime Op Info Cache

`ServiceContext.getOpInfo` and `setOpInfo` use raw `Enum`, `EnumMap`, and
`WeakReference`. This can be benign if op info is service-local and categories
are never mixed. It is still an owner-risk shape because the type system does
not encode the category class or the owner scope of the cached value.

Recommended direction:

- introduce a typed op-info key that carries the enum class,
- include the owner scope in the key if `Op` instances can be shared across
  containers or constant pools,
- or document and assert the cache's service/container confinement.

### Native Template Reflection

`NativeContainer` reflects template classes during native-template startup.
Raw `Class` warnings here are not just cosmetic because this code installs
owner-local templates into the native container. The proper fix is to make the
reflection boundary prove the subtype once:

```java
Class<? extends ClassTemplate> clz =
        Class.forName(name).asSubclass(ClassTemplate.class);
```

Then carry `Class<? extends ClassTemplate>` through the map and instantiation
helper. That localizes reflection failure and keeps owner startup types explicit.

### Constant And Type Metadata

Warnings in `TypeConstant`, `TypeInfoReal`, `ClassStructure`, `ConstantPool`,
`ModuleStructure`, and `VersionTree` need a different bar than ordinary
collection warnings. These structures represent constant pools, type metadata,
and module/version graphs. Same-JVM repeated compilation or direct execution
can reuse JVM classes while expecting each pool/module graph to stay isolated.

Recommended guards:

- prove caches are pool-local or key them by the owning `ConstantPool`,
- clear transient runtime/helper state during adoption/clone paths,
- narrow unavoidable generic-array/updater casts to one helper,
- and add repeated same-JVM compile/load tests for independent module graphs.

### Structural Hash Contracts And `Constant.m_iHash`

The fixed `overrides` warnings are the small visible part of a larger hash-key
rule: never let structural equality and structural hashing diverge, and never
cache a hash on a mutable object unless the object has a real frozen state.

The current `Constant` class has a special cached-hash mechanism:

- zero means "not cached yet";
- unresolved constants return zero and do not populate the cache;
- once `containsUnresolved()` is false, the computed hash is cached;
- a real computed hash of zero is represented by a non-zero sentinel.

That design was almost certainly trying to solve two legitimate problems at
once: constant hash lookup is hot, and unresolved constants can still change
the data observed by `equals(...)`. The bad part was that the mechanism looked
like unexplained mutable magic and the cache field was a plain `int`. This
branch names the sentinels and makes `m_iHash` volatile, so racing readers
either recompute or observe a safely published cached hash after the resolution
check.

Do not copy this pattern to mutable compiler metadata. It is only tolerable for
constants because the cache refuses to store while unresolved. The better
future architecture is to make resolution/adoption return immutable or
explicitly frozen constant snapshots and then cache a final or safely published
hash on those snapshots. Until that exists, mutable metadata such as
`VersionTree`, `Register`, and `ChildInfo` should recompute from their equality
fields.

### Fallthrough State Machines

Most observed `fallthrough` sites look intentional from nearby comments, but
that does not make them low-risk. The warning is useful because many of these
switches are lifecycle machines:

- class construction and validation stages,
- annotation argument construction,
- service frame return/call/exception dispatch,
- mutable-vs-constant write checks,
- parser/name-resolution/compiler phases,
- and JIT build phases.

Recommended review rule:

- accidental fallthrough gets `break`, `return`, or `continue`;
- intentional fallthrough gets an explicit state-transition shape or a narrow
  suppression with a comment naming the source and target states;
- tests should cover the transition before and after a deferred call or
  same-JVM repeated run, because that is where stale stage fields usually show.

### `ConstantPool.withPool(...)` Try Resources

The `try` diagnostics should not be "fixed" by deleting the try-with-resources
binding. These sites often create a scoped current-pool owner for code that
still depends on transitional ambient lookup. That is relevant to this audit
because stale current-pool state is one of the documented same-JVM hazards.

Recommended direction:

- keep the lexical scope,
- add `ConstantPool.assertCurrentPool(pool, "...")` inside high-risk owner
  boundaries,
- prefer an API that consumes a lambda if the unused resource warning becomes
  an error,
- and use a narrow suppression only with an ownership-scope explanation.

## Recommended Priority

1. Audit `rawtypes`/`unchecked` in `runtime/Fiber.java`,
   `runtime/ServiceContext.java`, `runtime/ObjectHandle.java`,
   `runtime/template/annotations/xFuture.java`, and native templates that wrap
   Java futures or file/watch handles.
2. Audit `rawtypes`/`unchecked` in `TypeConstant`, `TypeInfoReal`,
   `ConstantPool`, `ClassStructure`, `ModuleStructure`, and `VersionTree` for
   pool/module owner separation under same-JVM repeated compile/load.
3. Review `fallthrough` sites in runtime and compiler/JIT lifecycle switches.
   Convert accidental edges; document or restructure intentional edges.
4. Preserve and harden `ConstantPool.withPool(...)` try scopes. Treat the lint
   warning as a prompt to make owner scope explicit, not as a prompt to remove
   the guard.
5. Fixed in this branch: `overrides` warnings after checking hash-key usage,
   especially for ASM objects that can sit in maps across compiler or runtime
   requests. Keep this lint enabled so new structural equality classes must
   either implement matching `hashCode()` or explicitly use identity semantics.
6. Handle `serial`, `classfile`, `cast`, and test-only raw warnings as cleanup
   unless a concrete same-JVM serialization or keying failure appears.

## Summary Classification

Must-fix candidate categories for parallelism:

- `rawtypes` and `unchecked` on owner-bearing runtime, async service, constant,
  type, module, compiler, and JIT paths.
- `fallthrough` on lifecycle or state-machine switches.

Should-fix / guard categories:

- `try`, because the underlying owner-scope guard is important but the unused
  resource diagnostic is usually intentional.
- no current `overrides` diagnostics; future hits should be fixed or justified
  immediately because they can corrupt ordinary hash caches.

Style-only for this audit unless tied to a concrete owner boundary:

- `serial`,
- `classfile`,
- `cast`,
- and test-only `rawtypes`/`unchecked`.

## Row 133 Fallthrough Classification (2026-08-24)

The composite build now compiles with `-Xlint:fallthrough` fatal (cumulative
with the this-escape gate in the shared Java convention). The full enumeration
- run with `-Porg.xtclang.java.maxWarnings` raised, because javac's default
`-Xmaxwarns 100` truncates naive sweeps - found 84 sites: 81 in main sources,
3 in GC stress tests.

Classification outcome: 84/84 INTENTIONAL, 0 suspicious.

- 79 sites already carried the codebase's explicit `// fall through` /
  `// break through` marker; 4 got a missing marker added
  (`Lexer.eatIntegerLiteral`, `Parser.parseTypeCompositionComponent`,
  `NameExpression.getMeaning`, and the GC stress switch); 1 is an
  unreachable structural fall (`xTerminalConsole` print/readLine, where the
  inner switch covers the full result contract - a future explicit `default:`
  throw would harden it).
- The sites the audit feared most were all proven documented protocol:
  `ServiceContext.execute`'s `R_RETURN_CALL -> R_CALL` and
  `R_RETURN_EXCEPTION -> R_EXCEPTION` implement pop-then-call exactly as
  `Op` documents; `ClassTemplate.proceed` maintains its `ixStep++` invariant
  at every fall; `Utils`' stage machines set `stageNext` before every
  commented fall; `ClassStructure`'s contribution walks toggle only local
  `fAllowInto`/`fCheck` flags.
- 35 methods across 33 files carry method-level
  `@SuppressWarnings("fallthrough")` as the reviewed-and-proven marker.
  Class-level suppression is banned: it would blind the gate for future
  edits of exactly the files where a missed `break` is most dangerous.

The gate converts the comment convention into a machine-checked invariant:
an unsuppressed fall - the actual bug class, the future forgotten `break` -
is now a build error everywhere the convention applies. The long-term
reduction is migrating statement switches to arrow form (`case X ->`), which
cannot fall through; genuine cascades stay as gated statement switches.
