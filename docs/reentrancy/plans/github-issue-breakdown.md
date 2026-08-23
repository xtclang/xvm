# Reentrancy Branch PR Breakdown

This document is an action plan for submitting the large
`lagergren/lazy-instance` hardening branch as separate reviewable PRs. It is
not only an issue taxonomy. Each section below is shaped so it can become a
GitHub PR description and reviewer checklist.

Primary source documents:

- [../must-audit-backlog.md](../must-audit-backlog.md)
- [../must-fix-races.md](../must-fix-races.md)
- [../fixed-in-this-branch.md](../fixed-in-this-branch.md)
- [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md)
- [../master-container-isolation-bug-reports.md](../master-container-isolation-bug-reports.md)
- [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md)
- [../constant-adoption-clone-audit.md](../constant-adoption-clone-audit.md)
- [../this-escape-tally.md](../this-escape-tally.md)
- [xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md)

## Suggested PR Sequence

The sequence separates unrelated ownership problems and keeps each review
focused on one state model. Commit hashes are branch-local guideposts, not a
promise that every listed commit can be cherry-picked without splitting by
source area.

The review branches should usually be cut as logical issue branches, not as a
literal replay of every exploratory commit in `lagergren/lazy-instance`.
Several branch commits belong together because they prove one cause/effect/fix
chain. In those cases, cherry-pick with `--no-commit`, resolve the source/doc
scope for that one issue, and commit the final result as one reviewable change.
Keep the original branch commit hashes in the PR body as provenance, but do not
make reviewers reconstruct the design from discovery-order commits.

This branch should be treated as the integration/proof branch, not as the final
review unit. The first submission strategy is small, test-backed PRs that each
explain one broken state model and its replacement. A fork is a fallback only if
those narrow fixes are rejected despite concrete master failures, preserved
semantics, and clear owner/performance reasoning.

| Order | PR title | Main reason | Depends on |
| --- | --- | --- | --- |
| 1 | Add container-owned native template lookup | Creates the owner lookup surface that removes process-global template pointers. | None |
| 2 | Owner-scope core runtime value handles | Removes ownerless string, bit, number, root, Ref/Var, const, exception, tuple, future, and native enum value factories. | PR 1 |
| 3 | Owner-scope runtime metadata caches | Moves template/type/method/signature/helper metadata from JVM globals to owner-local lazy state. | PR 1, PR 2 |
| 4 | Encapsulate legitimate process-wide runtime resources | Keeps true process resources process-wide but removes mutable/public/thread-local owner leaks. | PR 1 where owner lookup is needed |
| 5 | Fix enum singleton lifecycle and public enum publication | Closes PR #534's natural enum publication shape and the split singleton lifecycle state. | PR 1, PR 2 |
| 6 | Harden shared runtime and ASM caches | Covers small proven cache/hash/op/manual-lazy hazards that are not template `INSTANCE` work. | PR 1 through PR 5 |
| 7 | Remove runtime and ASM constructor escapes | Removes constructor-time owner publication and virtual constructor dispatch in runtime/ASM/tooling code. | PR 1 through PR 6 |
| 8 | Add same-JVM stress and ownership diagnostics | Adds the proof harness for repeated direct execution and parallel container validation. | PR 1 through PR 7 |
| 9 | Remove semantic ambient `ConstantPool` lookup | Replaces `getCurrentPool()` semantic owner selection with explicit owners. | PR 8 useful for stress, otherwise independent |
| 10 | Harden constant adoption owner transfer | Prevents shallow-cloned constants from carrying runtime/helper state across pools. | PR 5 and PR 8 recommended |
| 10b | Guard ConstantPool registration publication | Separates same-thread recursive registration from public cross-thread observation while the later transaction design is prepared. | PR 10 recommended |
| 11 | Fix method, parameter, and handle owner-copy hazards | Repairs method/parameter/delegated copies, constrains direct cross-owner handle masks, and fixes same-owner `GenericHandle` inflated-ref view backing. | PR 10 |
| 12 | Keep compiler reentrancy cleanup separate | Moves lexer/parser/AST constructor and compiler-owner work out of runtime review. | Independent after shared ASM API changes |
| 13 | Keep JIT ownership cleanup separate | Keeps JIT lifecycle, generated static fields, and `Ctx.Current` review separate from interpreter runtime. | PR 9/10 for shared ASM safety |
| 14 | Add build, lint, and source-shape gates | Turns fixed patterns into regressions that fail early. | After the relevant patterns are clean |

### Commit Folding Guidance

Use this as the first pass when preparing actual PR branches from `master`.

| Review slice | Fold together | Keep separate |
| --- | --- | --- |
| Ambient current-pool removal | `be0270e0d`, `e856d85ce`, `d58ebfea0`, `5d5773979`, `5fce7b9ae`, `4c6521dd9`, `c93b5ad61`, `2716435f1`, `84fa61534` | Constant adoption, clone/copy fixes, JIT `Ctx.Current` policy |
| Constant adoption hardening | `09f244211`, `e569d27db`, `e6f78a210`, `d1d0683e3`, `a0c1fe936` | Parameter/method clone fixes and `ObjectHandle.cloneAs(...)` design |
| ConstantPool registration publication guard | Current registration-completion guard wave | Clone-free adoption, runtime pool freeze, and the later private transaction/worklist rewrite |
| Shared runtime/ASM cache hardening | Runtime op-cache commits, `JumpNFirst` atomic state, `JumpNSample`, guard descriptor/process cleanup, `MethodStructure.Code` first-publication diagnostics, and the `TypeConstant` TypeHandle owner-cache slice | Native-template `INSTANCE` migration, enum lifecycle state, broad ConstantPool freeze, full immutable `ResolvedCode` refactor |
| Parameter, method, and handle-copy fixes | `7f82e0a1e`, `ed7220bee`, the `GenericHandle.maskAs(...)` cross-owner guard slice, and the same-owner `GenericHandle.cloneAs(...)` inflated-ref backing slice | Constant adoption validator, broad compiler clone cleanup, base `ObjectHandle.cloneAs(...)` subclass audit |
| Constructor-escape removal in shared ASM/runtime | `1249e2a0f`, `47d7ab30e`, `93189541f`, `16915ebe7`, `7b7fc2036`, `70bf202ef` where source areas match | JIT constructor escapes and compiler/parser-only cleanup |
| JIT ownership cleanup | `36c24a974`, `cb81116cb` plus the separate JIT plan work | Interpreter runtime template ownership |
| Documentation-only hardening studies | `f0a6a71b1` and any uncommitted plan/audit docs | Source changes unless the PR is explicitly mixed for review proof |

If a folded slice becomes too large, split by invariant rather than package
name. For example, "explicit receiver pool for semantic type operations" and
"file-owned diagnostics" are both current-pool work, but they can be separate
PRs if reviewers want smaller diffs. Do not split a test away from the source
change it proves.

### Acceptance Proof Standard

The smoke test is necessary, but it is not sufficient proof for reentrancy.
Each extracted PR should carry the proof appropriate to its failure mode:

- Source-shape proof: tests or lint guards fail if the old unsafe pattern comes
  back, such as mutable `INSTANCE`, ownerless runtime factories,
  `ConstantPool.getCurrentPool()` semantic lookup, owner-bearing decoded-op
  cache fields, `TypeConstant.m_handle`, or constructor `this` escapes.
- Red-on-master proof: every must-fix should have a behavioral test that fails
  on master, or a source-shape test that fails on master when reproducing the
  full runtime failure would require a heavy integration harness.
- Runtime ownership proof: same-JVM sequence and parallel-container stress must
  run with ownership diagnostics enabled, and the validator must assert that
  handles, templates, compositions, Type handles, and runtime caches are owned
  by the expected container.
- Late mutation proof: runtime-published pools should run with late-registration
  diagnostics so cache-looking code cannot grow or rewrite a pool during
  execution unnoticed.
- Java memory model proof: every shared state cell must be final immutable
  state, `AtomicReference`, `ConcurrentMap.computeIfAbsent(...)`,
  synchronized/volatile publication, or explicitly documented as confined.
  Plain lazy fields remain must-audit unless confinement is proven.
- Equivalence/performance proof: document the old cache behavior and show the
  replacement preserves it per owner. Hot paths must not gain deep graph copies
  or avoidable locks. Any extra footprint must be owner-local and intentional,
  such as one cache entry per executing container instead of one unsafe JVM
  global entry.

Performance validation should compare practical scenarios, not just isolated
micro timings: same-JVM direct sequence versus forked execution, parallel
container stress throughput, and focused cache-heavy paths such as native
template lookup, Type handles, decoded switch caches, and `GenericHandle`
access-view cloning. A performance regression is acceptable only when it is
explained as the cost of removing invalid global sharing and there is no
simpler owner-local representation.

## Verification Command Conventions

Use configuration-cache-compatible commands for proposed PR verification. If a
clean build is needed, run `./gradlew clean` by itself first, then run the
verification task in a separate command.

Common targeted lint command:

```bash
./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --configuration-cache \
  --console=plain --warning-mode=all
```

Common unit-test shape:

```bash
./gradlew :javatools:test --tests '<test class or pattern>' \
  --configuration-cache \
  --console=plain --warning-mode=all
```

Common same-JVM stress shape:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --configuration-cache \
  --console=plain --warning-mode=all
```

## Reusable PR Description Template

````markdown
# Summary

## Why?
- <Reviewer-facing problem in the old design.>
- <Why it is wrong even without parallel execution.>
- <Why it blocks same-JVM/reentrant/parallel containers.>

## How?
- <Exact source scope included.>
- <Owner/state model after this PR.>
- <Key commits or commit families from `lagergren/lazy-instance`.>

## Out Of Scope
- <Related work deliberately left to later PRs.>

## Equivalence
- <Semantic behavior that must stay the same.>
- <Cache/performance behavior that must stay the same.>
- <Any intentional allocation or startup timing change.>

## Verification
```bash
<focused unit test command>
<source scan or lint command>
<stress command if this PR needs one>
```

## Documentation
- <Docs updated in this PR.>

## Reviewer Checklist
- [ ] Owner is explicit in APIs or receiver state.
- [ ] No process-global owner-bearing cache remains in this scope.
- [ ] No constructor publishes `this` or calls overridable behavior in this scope.
- [ ] Tests fail on the old design or source-shape tests reject the old API.
- [ ] Cache granularity is preserved or the performance change is justified.
````

## PR 1: Add Container-Owned Native Template Lookup

### PR Title

Add container-owned native template lookup

### Reviewer-Facing Problem Statement

Native templates were represented by mutable process-global `INSTANCE` fields
assigned from constructors. A native template is container-owned state: it
contains a `Container`, `ConstantPool`, native metadata, template caches, type
compositions, and handle factories. A JVM-global pointer to that object is
wrong even for single-threaded code because the API hides ownership and
constructor assignment publishes a partially initialized object.

This blocks same-JVM and parallel containers because the last container to
assign `INSTANCE` wins. Later code for container A can read container B's
template, pool, composition, or metadata.

### Exact Scope Included

- Add the `NativeTemplateRef<T>` key type.
- Add the per-container `NativeTemplates` lookup table.
- Add `Container.nativeTemplates()` and typed owner lookup helpers needed by
  the migration.
- Change native-template startup so templates are resolved through the active
  `Container` instead of a process-global field.
- Remove constructor assignments for the converted template scope.
- Remove dead `fInstance` constructor role plumbing when it is only supporting
  old `INSTANCE` behavior.

### Explicit Out Of Scope

- Public enum singleton publication and `SingletonConstant` lifecycle changes.
- Runtime value-handle factory migration that can be reviewed separately.
- Static runtime metadata cache migration beyond what is needed for the
  template lookup table.
- Ambient `ConstantPool.getCurrentPool()` cleanup.
- Compiler and JIT constructor cleanup.

### Source Areas / Branch Commits

Commit families:

- `4a629c660 Fix native template startup races`
- `2f6742e59 Move resource templates into NativeTemplates`
- `93c36fbf6 Remove leaf native template INSTANCE fields`
- `aade77699 Remove array leaf template INSTANCE fields`
- `80999b393 Remove unused template INSTANCE fields`
- `5f5117e07 Remove remaining fInstance template roles`
- `dc95e08e6 Remove dead fInstance constructor flags`

Primary source areas:

- `javatools/src/main/java/org/xvm/runtime/NativeTemplateRef.java`
- `javatools/src/main/java/org/xvm/runtime/NativeTemplates.java`
- `Container`, `NativeContainer`, `InterpreterConnector`
- native templates under `javatools/src/main/java/org/xvm/runtime/template`

### Tests And Verification Commands

```bash
rg -n "INSTANCE\\s*=\\s*this" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java

rg -n --pcre2 \
  "^\\s*(public|protected|private)?\\s*static\\s+(?!final\\b).*\\bINSTANCE\\b" \
  javatools/src/main/java/org/xvm/runtime/template

./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplatesTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplateOldPatternTest \
  --configuration-cache --console=plain --warning-mode=all
```

Run the targeted lint command from the top of this document if this PR touches
constructor startup or owner-local lazy fields.

### Semantic / Performance Equivalence Notes

- The cache key changes from "whole JVM" to "owning container".
- Warm containers still resolve each native template once and reuse it.
- `NativeTemplates` stores `Lazy` cells in a `ConcurrentHashMap` and resolves
  from `Lazy.get()` to avoid recursive `computeIfAbsent` during bootstrap.
- Leaf templates without external static readers do not need named
  `NativeTemplates` accessors.

### Documentation Updates Included

- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) native
  template and `fInstance` sections.
- Update [../must-fix-races.md](../must-fix-races.md) mutable template
  `INSTANCE` status.
- Update [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md)
  with the container-owned replacement shape.
- Keep [../state-inventory.md](../state-inventory.md) scans current.

### Review Checklist / Acceptance Criteria

- No converted template assigns `INSTANCE = this`.
- Converted templates do not expose mutable own `INSTANCE` fields.
- Template access starts from `Container`, `Frame`, or another owner-bearing
  object.
- The PR preserves one-template-per-container caching.
- Tests include a deterministic old-pattern proof for last-writer-wins or
  constructor publication.

### Dependency / Order

This is the first code PR. PRs 2, 3, 5, and most runtime owner plumbing depend
on the owner lookup surface created here.

## PR 2: Owner-Scope Core Runtime Value Handles

### PR Title

Owner-scope core runtime value handles

### Reviewer-Facing Problem Statement

Several template classes exposed static runtime handles or ownerless factories
for values whose `TypeComposition` belongs to a runtime owner. Examples include
strings, bits, primitive numbers, native enum values, refs, vars, exceptions,
tuple/future helpers, and root support objects.

This was wrong even in a single-threaded JVM because the call site could not
name the owner of the handle it was manufacturing. It blocks repeated same-JVM
execution because a handle from run N can survive in a static field and be
returned during run N+1.

### Exact Scope Included

- Remove ownerless core value factories where the result is an owner-bearing
  `ObjectHandle`.
- Replace static common handles with final owner-local lazy caches.
- Add owner-required factories that take `Frame`, `Container`,
  `ClassTemplate`, or an existing owner handle.
- Replace static handle equality checks with pure value predicates where
  appropriate.
- Keep native enum value handles owner-local for `xBoolean`, `xNullable`,
  `xOrdered`, and `xBit`.

### Explicit Out Of Scope

- Natural enum singleton publication through `xEnum.ensure*`, covered by PR 5.
- Large reflection/resource metadata bundles, covered by PR 3.
- Constant adoption and `HandleConstant` movement, covered by PR 10.
- JIT generated value handles and generated `$INSTANCE` policy.

### Source Areas / Branch Commits

Commit families:

- `e7a007914 Owner-scope string handles`
- `04bc82957 Owner-scope Ref and Var templates`
- `8072777f4 Owner-scope primitive number templates`
- `8c718b47a Owner-scope decimal and float templates`
- `fd9e8f81e Owner-scope checked integer templates`
- `56fef43c6 Owner-scope root support templates`
- `9dbfc5fc1 Owner-scope const and exception templates`
- `89c170434 Owner-scope native enum value handles`
- `cc833b115 Owner-scope Bit value handles`
- `a9c24beb2 Owner-scope future template`
- `bdc71ce27 Owner-scope Tuple void handle`

Primary source areas:

- `xString`, `xBit`, numeric templates
- `xBoolean`, `xNullable`, `xOrdered`
- `xRef`, `xVar`, `Identity`, `Proxy`, `xObject`
- `xConst`, `xException`
- `xFuture`, `xTuple`, `xRTFunction`, `xRTType`
- call sites that now pass `Frame` or `Container`

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplateOldPatternTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplatesTest \
  --configuration-cache --console=plain --warning-mode=all

rg -n "xString\\.(EMPTY_STRING|ZERO|ONE)|xString\\.makeHandle\\(" \
  javatools/src/main/java/org/xvm

rg -n "xBit\\.(ZERO|ONE)|xBoolean\\.(TRUE|FALSE)|xNullable\\.NULL|xOrdered\\.(LESSER|EQUAL|GREATER)" \
  javatools/src/main/java/org/xvm
```

Use the direct stress command with modules that exercise strings, arrays,
numbers, and reflection after PR 8 is available.

### Semantic / Performance Equivalence Notes

- Common values remain cached once per owner template.
- Uncached non-small values allocate the same handle classes as before.
- Passing an owner is the intended API change; a hidden global owner is removed.
- `xString`, `xBit`, and native enum helper paths should not add per-use
  allocation for cached values.

### Documentation Updates Included

- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) sections for
  owner-scoped strings, root support, primitive numbers, native enum values,
  bit handles, tuple, future, Ref/Var, const, and exception.
- Update [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md)
  static handle examples.
- Update [../must-fix-races.md](../must-fix-races.md) static runtime metadata
  and raw handle status where affected.

### Review Checklist / Acceptance Criteria

- New handle factory APIs require an explicit owner.
- Removed ownerless overloads fail at compile time if reintroduced at old call
  sites.
- Common value caches are owner-local and final/lazy.
- Tests show the old static factory could return a foreign-owner handle.
- No public API returns a cached process-global runtime handle for this scope.

### Dependency / Order

Depends on PR 1. Should land before PR 5 because natural enum publication uses
owner-local enum templates and factories.

## PR 3: Owner-Scope Runtime Metadata Caches

### PR Title

Owner-scope runtime template metadata caches

### Reviewer-Facing Problem Statement

Runtime templates and `Utils` cached owner-derived metadata in mutable static
fields: type constants, compositions, method structures, signatures, template
references, constructor methods, array handles, and helper bundles.

These fields were not JVM constants. They were values computed from a
particular `Container`, `ConstantPool`, template, or runtime handle. The old
shape could build mixed-owner metadata even without parallel execution if
nested startup populated related fields from different owners.

### Exact Scope Included

- Move unkeyed owner-derived metadata to final owner-local `Lazy` fields on the
  owning template.
- Move related helper state into immutable owner-local info records, especially
  for `Utils` runtime metadata.
- Move keyed metadata into owner-owned maps or owner-owned lazy map values.
- Replace static template/helper references in collections, reflection,
  filesystem, crypto, IO, net, web, service, and resource helpers.
- Keep pure process-global literal data as immutable static final data.

### Explicit Out Of Scope

- Legitimate process resources such as timers, terminals, and watch daemons,
  covered by PR 4.
- Public enum singleton publication, covered by PR 5.
- ConstantPool ambient owner cleanup, covered by PR 9.
- Broad generic API cleanup unless needed for typed owner access in this PR.

### Source Areas / Branch Commits

Commit families:

- `ec3e3774b Owner-scope Utils runtime metadata`
- `3fba906a8 Owner-scope array delegate views`
- `515524aa3 Owner-scope ListMap template`
- `b0f6a19b0 Owner-scope signature metadata`
- `4e40327dc Owner-scope reflect property handles`
- `faf8a1930 Owner-scope property template handles`
- `e0bdceeae Owner-scope file package template caches`
- `366bb3eb2 Owner-scope method template handles`
- `a2ef0b7cc Owner-scope filesystem templates`
- `9213768e8 Owner-scope atomic number templates`
- `f78c0f0a7 Owner-scope leaf metadata caches`
- `35cee3515 Finalize constant-shaped static fields`

Primary source areas:

- `Utils`
- `xArray` and native array delegate/view templates
- `xListMap`, `xTuple`, `xFuture`
- reflection templates for method, property, signature, type, file, package,
  module, class, component, and service-control metadata
- filesystem, crypto, net, IO, temporal, and web templates

### Tests And Verification Commands

```bash
rg -n --pcre2 \
  "^\\s*(?:public|protected|private)?\\s*static\\s+(?!final\\b)(?:Map<[^;=()]+>|TypeConstant|TypeComposition|ClassTemplate|ClassComposition|MethodStructure|MethodConstant|SignatureConstant|ArrayConstant|ArrayHandle|ObjectHandle|StringHandle|TupleHandle|EnumHandle|BooleanHandle|xEnum|x[A-Z][A-Za-z0-9_]*)\\s+(?!INSTANCE\\b)[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java

./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplateOldPatternTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.OwnershipDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all
```

After PR 8 lands, run direct same-JVM stress with ownership validation over
reflection, arrays, tuples, and services.

### Semantic / Performance Equivalence Notes

- The old one-time metadata lookup remains one-time, but per owner.
- Grouped info records reduce split-field mixed-owner risk without adding
  repeated lookup cost.
- `Map.copyOf(...)` is appropriate for owner-local maps after construction.
- Do not replace hot per-handle memoization with object-heavy `Lazy` unless the
  cache is owner-bearing and shared.

### Documentation Updates Included

- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) static
  runtime metadata table.
- Update [../must-fix-races.md](../must-fix-races.md) static metadata count and
  status.
- Update [../manual-lazy-cache-audit.md](../manual-lazy-cache-audit.md) for any
  manual lazy sites converted in this PR.
- Update [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md)
  for grouped owner-local metadata bundles.

### Review Checklist / Acceptance Criteria

- Scanned runtime-template/`Utils` owner-shaped non-final statics are gone for
  this scope.
- Related metadata is computed from one owner and published as one lazy value or
  immutable bundle.
- Call sites pass `Frame`, `Container`, or owner template explicitly.
- Repeated calls reuse the same owner-local cache.
- Tests demonstrate a mixed-owner old pattern and the owner-local replacement.

### Dependency / Order

Depends on PR 1 and usually PR 2. Land before broad same-JVM ownership stress
so the diagnostics have the main owner-local metadata surface available.

## PR 4: Encapsulate Legitimate Process-Wide Runtime Resources

### PR Title

Encapsulate process-wide runtime resources and owner-bind callbacks

### Reviewer-Facing Problem Statement

Some state is intentionally process-wide: terminal input, Java timers, OS watch
daemon infrastructure, weak diagnostic registries, and TLS key-manager callback
state. The old code often represented that state as public mutable statics,
duplicate mutable snapshots, inconsistent weak-map synchronization, or
thread-local owner-bearing state.

That is bad even single-threaded because unrelated code can replace process
resources or callbacks can retain the first owner they observed. In parallel or
same-JVM execution, daemon and pooled Java threads can deliver events for one
container while carrying state from another.

### Exact Scope Included

- Make the LocalClock scheduler a private final process resource with an
  accessor/scheduling API.
- Keep one OS watch daemon but derive the event owner from the registered
  storage handle for each event.
- Remove TLS key-manager `ThreadLocal` key-store selection and use explicit
  route aliases/state.
- Hide terminal/JLine process state behind one synchronized holder and remove
  duplicate `DebugConsole` mutable statics.
- Synchronize `Runtime.findContainer(...)` on the same weak-registry monitor as
  registration/snapshotting.

### Explicit Out Of Scope

- Changing process resources into per-container resources.
- Terminal behavior or console UX changes.
- Service scheduler redesign.
- Template metadata cache migration except owner lookup needed by callbacks.

### Source Areas / Branch Commits

Commits:

- `026713d35 Encapsulate LocalClock scheduler`
- `f76a4c5a0 Owner-bind OS storage watch events`
- `2bf17f7e7 Remove TLS key manager thread-local state`
- `caa26c311 Encapsulate terminal console state`
- `3d1647463 Synchronize runtime container registry lookup`

Primary source areas:

- `xLocalClock`, `xNanosTimer`
- `xOSStorage.WatchDaemon`
- `xRTServer.SimpleKeyManager`
- `xTerminalConsole`, `DebugConsole`
- `Runtime`

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.runtime.template._native.web.xRTServerTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.RuntimeTest \
  --configuration-cache --console=plain --warning-mode=all

rg -n "ThreadLocal|public static .*TIMER|public static .*TERMINAL|public static .*READER|s_daemonWatch" \
  javatools/src/main/java/org/xvm/runtime
```

Run same-JVM stress with `TestFiles` and service-heavy modules after PR 8.

### Semantic / Performance Equivalence Notes

- Keep one daemon `Timer`, not one per container.
- Keep one OS watch daemon holder, not one watch thread per container.
- Keep terminal/JLine state process-wide because system input/output is
  process-wide.
- TLS alias lookup happens during handshake callbacks and uses existing route
  maps.
- Weak diagnostic registry lifetime remains weak; synchronization changes only
  internal consistency.

### Documentation Updates Included

- Update [../runtime-ownership-hardening-ledger.md](../runtime-ownership-hardening-ledger.md).
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) sections for
  LocalClock, OSStorage, xRTServer, runtime registry, and terminal/debug
  console state.
- Update [../ownership-diagnostics.md](../ownership-diagnostics.md) allowlist
  for legitimate process-wide resources.

### Review Checklist / Acceptance Criteria

- No public mutable terminal/timer process globals remain in scope.
- Daemon or TLS callbacks derive owner state from explicit event/route/handle
  state.
- Weak registry reads and writes use one synchronization model.
- Tests prove the removed thread-local/global shape cannot return unnoticed.

### Dependency / Order

Can land after PR 1 if callback code needs owner lookup. Otherwise independent
of native-template metadata migration. Should land before long same-JVM stress
is used as a confidence signal.

## PR 5: Fix Enum Singleton Lifecycle And Public Enum Publication

### PR Title

Fix enum singleton lifecycle and public enum publication

### Reviewer-Facing Problem Statement

Enum singleton initialization had two related problems. First,
`SingletonConstant` represented one logical lifecycle through several mutable
fields. Second, public/native enum paths could publish a natural enum
construction struct where callers expected the finalized enum singleton value.

This is wrong without parallelism because a caller can observe a mixed
initialization snapshot or a raw construction struct. Parallel fibers and
same-JVM containers make the timing nondeterministic and can expose handles
owned by the wrong runtime.

### Exact Scope Included

- Replace split `SingletonConstant` mutable fields with one
  `AtomicReference<InitState>` state machine.
- Preserve same-fiber recursion through an initializing placeholder.
- Preserve other-fiber waiting through a shared completion future.
- Close public/native raw enum publication paths using:

  - `xEnum.ensureEnumByName(Frame, String)`;
  - `xEnum.ensureEnumByOrdinal(Frame, int)`;
  - `Utils.ensureInitializedEnum(Frame, EnumHandle)`;
  - `Utils.assignInitializedEnum(Frame, EnumHandle, int)`.

- Make raw `xEnum` lookup helpers protected/internal and remove public raw
  value-list access.
- Include `SingletonConstant.copyForAdoption(...)` fresh-state handling here
  only if the PR's tests assert adopted singleton behavior. Otherwise put that
  hook in PR 10 and limit this PR's stress claims accordingly.

### Explicit Out Of Scope

- Broad native-template `INSTANCE` removal except enum templates already
  migrated by PR 1/2.
- Generic constant adoption hardening for non-singleton constants.
- Compiler enum diagnostics.
- JIT generated enum singleton ownership.

### Source Areas / Branch Commits

Commit families:

- `4a629c660 Fix native template startup races`
- `89c170434 Owner-scope native enum value handles`
- `68b1bbc6e Close reflection enum publication helpers`
- `9a7519db8 Close remaining enum publication paths`
- `09f244211 Fix adopted constant runtime state ownership` for singleton
  adoption if included here

Primary source areas:

- `SingletonConstant`
- `xEnum`
- `xBoolean`, `xNullable`, `xOrdered`
- `xEnumValue`, `xEnumeration`
- `xRTType`, `xRTTypeTemplate`, `xRTComponentTemplate`
- `xRTClassTemplate`, `xRTPropertyClassTemplate`, `xRTMethod`
- service/future/array mutability enum call sites

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.runtime.SingletonConstantTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.NativeTemplatesTest \
  --configuration-cache --console=plain --warning-mode=all

rg -n "getEnumByName|getEnumByOrdinal|getValues\\(" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/MainContainer.java \
  javatools/src/main/java/org/xvm/runtime/Utils.java
```

After PR 8, run:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestProps,TestReflection \
  --configuration-cache --console=plain --warning-mode=all
```

### Semantic / Performance Equivalence Notes

- Singleton initialization can suspend, recurse, abort, and retry; it is not a
  normal synchronous `Lazy`.
- The CAS state machine preserves waiter and recursion semantics while making
  every observed state coherent.
- Native enum value factories remain cached on owner templates.
- Returning `ObjectHandle` from public enum helper paths is intentional because
  deferred singleton completion cannot be represented by raw `EnumHandle`.

### Documentation Updates Included

- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) singleton and
  natural enum sections.
- Update [../must-fix-races.md](../must-fix-races.md) raw enum handle and split
  lifecycle status.
- Update [../native-template-startup-safety.md](../native-template-startup-safety.md)
  if startup enum publication details are included.
- Update [../constant-adoption-clone-audit.md](../constant-adoption-clone-audit.md)
  only if singleton adoption is included.

### Review Checklist / Acceptance Criteria

- `SingletonConstant` exposes one coherent lifecycle state object.
- Public/native enum paths use initialized/deferred helpers, not raw protected
  lookup primitives.
- Raw enum handle lists are not publicly exposed.
- Tests cover concurrent initialization, waiting, same-fiber recursion, and any
  adopted singleton state included in scope.
- Review text distinguishes native enum value handles from natural enum
  singleton publication.

### Dependency / Order

Depends on PR 1 and PR 2. Should land before PR 10 if singleton adoption stays
with the generic adoption hardening PR; otherwise PR 10 should treat singleton
adoption as already handled.

## PR 6: Harden Shared Runtime And ASM Caches

### PR Title

Harden shared runtime and ASM caches

### Reviewer-Facing Problem Statement

The branch fixes several small but real cache and publication hazards that are
not the native-template `INSTANCE` problem. These include owner-bearing
frame-constant caches on shared decoded op objects, manual lazy null caches,
structural hash contract violations, constant hash publication, and a
process-global diagnostic `HashSet`.

Each is bad even single-threaded because the state model is hidden or violates
ordinary Java contracts. Under same-JVM or parallel execution, the same shapes
can preserve wrong-owner constants, publish stale values, or corrupt maps and
sets.

### Exact Scope Included

- Remove runtime-executed `Op` caches that write `Frame` constants back onto
  shared decoded op objects.
- Move runtime switch tables for `JumpVal`/`JumpVal_N` from shared decoded op
  fields into executing-container cache state.
- Make `JumpNFirst`'s deliberate decoded-op `assert:once` state atomic so
  concurrent first execution has exactly one winner.
- Remove `JumpNSample`'s runtime interval operand cache from the shared decoded
  op. The current `Frame` still supplies the interval handle; the fixed op just
  clamps that value per execution instead of letting the first invocation
  determine every later sample rate.
- Keep assembly-time op metadata behavior intact.
- Replace high-risk manual lazy null caches identified in this branch:

  - `xRegEx.RegExHandle` compiled pattern;
  - `FSNodeConstant` path literal cache and adoption clearing;
  - native `OSFileNode.created` owner mismatch.

- Add structural `hashCode()` implementations matching existing `equals(...)`
  for mutable metadata classes found by lint.
- Make `Constant.hashCode()` cache publication explicit with volatile/sentinel
  names.
- Replace the `TypeConstant` recursion diagnostic `HashSet` with a concurrent
  process-wide diagnostic set.
- Safely publish optimized `MethodInfo` and `PropertyInfo` runtime chains,
  preserving the existing top-level cache shape while preventing partially
  built arrays and nested-property-id cache poisoning.
- Safely publish `TypeInfoReal` derived runtime metadata caches: immutable
  property-name snapshots, synchronized expanding method-signature caches,
  synchronized/volatile delegate view publication, volatile abstractness
  readiness, and synchronized successful child-newability publication.
- Safely publish `PropertyInfo` helper cells for ref annotations, injected and
  implicitly-assigned flags, base Ref/Var type, and getter/setter method
  constants while preserving the old one-helper-per-owned-property cache shape.
- Safely publish `ClassComposition` field layout, field-name, and synthetic
  initializer helper cells under the inception composition, so access views
  share owner-local final `Lazy.Owner` caches instead of copying stale layout
  side fields or racing clone-local duplicate lazy cells.
- Replace `PropertyComposition`'s mutable struct-view cache with final
  `Lazy.Owner` state and final role fields, preserving lazy struct-view
  allocation and shared call-chain maps.

### Explicit Out Of Scope

- Broad ConstantPool registration/freeze redesign.
- Generic constant adoption redesign beyond the cache clearing needed in this
  PR.
- Full manual lazy cache audit across compiler/runtime.
- JIT generated cache policy.

### Source Areas / Branch Commits

Commit families:

- `7872eb186 Remove frame-owned op runtime caches`
- `0aa9a86cd Harden owner-sensitive lazy caches`
- `4fb44ee74 Fix TypeConstant recursion diagnostic state`
- `9456d6727 Tighten lint and constructor escape audit`
- `35cee3515 Finalize constant-shaped static fields`
- parts of `09f244211 Fix adopted constant runtime state ownership` if
  `FSNodeConstant` cache clearing is included here rather than PR 10

Primary source areas:

- `asm/op/JumpCond`, `asm/op/JumpNCond`
- `asm/op/JumpVal`, `asm/op/JumpIsA`, `asm/op/JumpVal_N`
- `asm/op/JumpNFirst`
- `asm/op/JumpNSample`
- `runtime/Container`
- `OpTest`, `OpCondJump`
- `xRegEx`
- `FSNodeConstant`
- native filesystem `OSFileNode`
- `VersionTree`, `Register`, `ChildInfo`
- `Constant`
- `TypeConstant`
- `MethodInfo`, `PropertyInfo`
- `TypeInfoReal`
- `ClassComposition`
- `PropertyComposition`

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.asm.OpRuntimeCacheTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.template.text.RegExHandleTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.asm.ConstantHashCodeCacheTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.asm.constants.TypeConstantRecursionDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.asm.RegisterHashCodeTest \
  --tests org.xvm.asm.VersionTest \
  --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test \
  --tests org.xvm.asm.constants.MethodInfoTest \
  --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.ClassCompositionSafePublicationTest \
  --configuration-cache --console=plain --warning-mode=all
```

Run the common targeted lint command to ensure the `overrides` diagnostics for
the hash-contract fixes are gone.

### Semantic / Performance Equivalence Notes

- Removed condition/common-type op caches only saved a local
  `Frame.getConstant(...)` array lookup; semantic type/condition resolution
  still uses the current frame owner.
- `JumpVal`/`JumpVal_N` do not lose their switch-table cache. They still build
  the same case-handle arrays, maps, range lists, wildcard masks, algorithms,
  and frame-derived type constants. The cache owner changes from "first
  execution of this decoded op in the JVM" to "the executing container".
- One-container execution keeps effectively the same table shape and hot cache
  behavior, with one container cache entry replacing the old op fields.
  Multi-container execution intentionally keeps one table per container because
  the handles and type constants are owner-bearing and cannot be shared safely.
- `JumpNFirst` intentionally remains keyed by the decoded op because it backs
  `assert:once`. Moving it to a container cache would make the assertion run
  once per container. The branch changes only the race mechanics: one final
  `AtomicBoolean` replaces one plain boolean.
- Hash codes for mutable metadata are recomputed rather than cached to avoid
  stale hashes after mutation.
- `Constant.hashCode()` keeps the old resolved-only cache behavior without
  adding per-constant `AtomicInteger`, `Lazy`, or wrapper allocation.
- The recursion set remains process-wide diagnostic state; only its collection
  implementation changes.
- `MethodInfo.m_aBodyResolved` keeps the old one optimized chain per owned
  method cache. The only added steady-state cost is a volatile read; the
  synchronized block is first-publication only. Capped-chain redirection stays
  outside the monitor to avoid recursive cache lock ordering.
- `PropertyInfo.m_chainGet` and `m_chainSet` keep the old top-level getter and
  setter caches with no new map allocation per property. Non-null nested ids no
  longer write into the unkeyed slots; if nested ids become hot, a separate
  keyed owner-local cache is the correct follow-up.
- `PropertyInfo` helper cells keep the old cached identity behavior: one
  annotation snapshot, one base-ref type, one getter id, one setter id, and one
  boxed boolean result per owned property. The branch does not add per-call
  annotation-array clones or remove owner-pool interning. It only adds the
  missing publication edge and detaches the cached annotation snapshot from the
  source property structure array.
- `TypeInfoReal` keeps the old one-cache-per-type-info shape for name lookups,
  signature lookups, delegate views, abstractness readiness, and successful
  child-newability validation. Property-name maps become immutable snapshots
  because callers were read-only already, and mutation would have corrupted
  owner metadata. Method-signature maps remain expanding caches because
  `getMethodBySignature(...)` stores substitutable/runtime lookup hits; the
  replacement changes the backing from a plain unsynchronized `HashMap` to a
  safely published synchronized `HashMap` wrapper.
- `ClassComposition` field-name arrays keep the old cached-array API and
  per-owner `StringHandle` elements. The branch routes the first access through
  a final `Lazy.Owner`; it does not add per-call clones or handle allocation.
  Access views now consistently reuse the inception array instead of
  timing-dependently duplicating it.
- `ClassComposition` field layout remains lazy and one-per-inception. The field
  map preserves insertion-order iteration for storage layout, and only the map
  shape is frozen; `FieldInfo` objects remain the same runtime metadata objects
  because transient initializer metadata is still recorded there.
- `ClassComposition` synthetic auto-initializers remain lazy and are still not
  allocated for fieldless classes. Access views now share the inception
  `Lazy.Owner`, which is equivalent because the field layout and struct type are
  inception-owned and shared by all views.
- `PropertyComposition` struct views remain lazy and still share the same
  method/getter/setter call-chain maps as the inception property composition.
  This is intentionally `Lazy.Owner`, not `Lazy.of(() -> ...)`, so construction
  does not install a supplier that captures `this` before the owner is complete.
- Failed virtual-child newability checks remain retryable, matching master.
  Only successful completion is cached and published.

### Documentation Updates Included

- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) sections for
  op caches, structural hash contracts, constant hash publication, manual lazy
  cache hardening, and TypeConstant diagnostics.
- Update [../manual-lazy-cache-audit.md](../manual-lazy-cache-audit.md).
- Update [../lint-parallelism-risk-audit.md](../lint-parallelism-risk-audit.md)
  for hash and lint findings.
- Update [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md)
  TypeConstant diagnostic status.
- Update [../runtime-metadata-op-cache-classification.md](../runtime-metadata-op-cache-classification.md)
  for optimized method/property chain cache publication and `TypeInfoReal`
  derived-cache publication.

### Review Checklist / Acceptance Criteria

- Runtime-executed op objects do not retain frame-owned constants in this
  scope.
- Manual lazy fixes preserve old first-use caching or intentionally compute on
  demand when owner mismatch made caching wrong.
- Every new `hashCode()` matches the existing `equals(...)` fields.
- Constant hash publication is explicit and low footprint.
- Optimized method/property chain first publication has a Java memory-model
  edge and preserves the old hot cache behavior.
- `PropertyInfo` helper first publication has a Java memory-model edge and
  preserves the old helper cache identities without leaking source annotation
  arrays.
- `TypeInfoReal` derived caches publish completed state and keep the old cache
  identity, signature-cache expansion, and retry semantics.
- `ClassComposition` helper caches are inception-owned, safely published, and
  shared by access views without eager allocation.
- `PropertyComposition` struct-view caching has no mutable nullable cache field
  and preserves one lazy owner-local struct view.
- Tests would fail or source-shape checks would detect the old cache/write-back
  pattern.

### Dependency / Order

Should land after PR 1 through PR 5 so owner-local runtime state has clear
boundaries. Can land before PR 8 so stress diagnostics validate the hardened
cache state.

## PR 7: Remove Runtime And ASM Constructor Escapes

### PR Title

Remove runtime and ASM constructor escapes

### Reviewer-Facing Problem Statement

Runtime and ASM constructors published `this`, registered partially
constructed owners, or called overridable methods before construction
completed. This is not style-only. It weakens Java final-field reasoning and
allows reentrant code, diagnostics, callbacks, or subclasses to observe default
fields and incomplete owner graphs.

### Exact Scope Included

- Move `NativeContainer` startup work behind `NativeContainer.create(...)`.
- Move `Container` helper and runtime registry publication after construction.
- Make `ConstHeap` owner-explicit instead of constructor-capturing the owner.
- Replace `xRef.RefHandle` and `xOSFileNode.NodeHandle` constructor publication
  with factories.
- Replace constructor-time assertion dispatch in `CallChain` and `xRTMethod`
  with static/private checks.
- Replace `ClassTemplate` implicit-field construction hooks with constructor
  metadata.
- Replace `Op` constructor virtual shape predicates with constructor data and
  private helpers.
- Replace `MethodInfo`/`PropertyInfo` body-owner constructor callbacks with
  static factories and non-virtual owned body copies.
- Make `MethodBody.equals(...)` and `hashCode()` cycle-safe for `FromInto`,
  `Implicit`, and `Union` targets by comparing stable method target shape
  instead of recursively walking owner metadata graphs.
- Remove ASM metadata owner-stealing and constructor hooks in
  `FileStructure`, `ClassStructure`, `MethodStructure`, `PropertyStructure`,
  `VersionTree`, `PropertyConstant`, and `TypeInfoReal`.
- Fix `ModuleInfo` constructor resource-dir lookup.
- Include utility constructor-dispatch fixes if they are not submitted on the
  separate `lagergren/fix-utils-this-escape` branch.

### Explicit Out Of Scope

- Compiler `Lexer`/`Parser`/AST constructor cleanup, covered by PR 12.
- JIT lifecycle work, covered by PR 13.
- Template `INSTANCE` migration, already covered by PR 1 through PR 3.
- Broad ConstantPool freeze/registration redesign.

### Source Areas / Branch Commits

Commit families:

- `a31f37ebf Avoid native container startup this escape`
- `2485d9bac Remove container construction owner escapes`
- `bbd4ec5e6 Move handle initialization out of constructors`
- `21218f85d Remove runtime constructor assertion escapes`
- `d3867fb81 Remove Op constructor shape this-escapes`
- `93189541f Remove MethodInfo body constructor escapes`
- `1249e2a0f Remove ASM metadata constructor escapes`
- `47d7ab30e Prove ASM constructor hook escapes are removed`
- `7b7fc2036 Remove ModuleInfo resource constructor escape`
- `cf0d58656 Remove utility constructor this-escapes` if included
- `9456d6727 Tighten lint and constructor escape audit`

Primary source areas:

- `NativeContainer`, `Container`, `MainContainer`, `NestedContainer`,
  `ConstHeap`
- `xRef`, `xOSFileNode`
- `CallChain`, `xRTMethod`, `ClassTemplate`
- `Op`, `OpGeneral`, `OpCondJump`, `OpTest`, `OpInPlace`, `OpIndex`,
  `OpPropInPlace`, `OpVar`
- `MethodInfo`, `MethodBody`, `PropertyInfo`, `PropertyBody`
- `FileStructure`, `ClassStructure.SimpleTypeResolver`, `MethodStructure`,
  `PropertyStructure`, `VersionTree`, `PropertyConstant`, `TypeInfoReal`
- `ModuleInfo`
- optional utility classes: `PackedInteger`, `HasherReference`, `ListSet`

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.api.InterpreterConnectorTest \
  --tests org.xvm.runtime.RuntimeThisEscapeConstructionTest \
  --tests org.xvm.runtime.template.reflect.RefHandleConstructionTest \
  --tests org.xvm.runtime.RuntimeTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.asm.OpRuntimeCacheTest \
  --tests org.xvm.asm.AsmConstructorEscapeTest \
  --tests org.xvm.asm.constants.MethodInfoTest \
  --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest \
  --tests org.xvm.tool.ModuleInfoTest \
  --configuration-cache --console=plain --warning-mode=all
```

If utility fixes are included:

```bash
./gradlew :javatools_utils:test --tests org.xvm.util.UtilityConstructorEscapeTest \
  --configuration-cache --console=plain --warning-mode=all
```

Run the common targeted lint command and confirm no diagnostics for the touched
runtime/ASM/tooling groups.

### Semantic / Performance Equivalence Notes

- Factories publish after construction but preserve old cache locations.
- `NativeContainer.create(...)` must return a fully initialized native
  container in the same startup order as before.
- `ConstHeap` keeps the same `ConcurrentHashMap` cache; methods receive the
  owner explicitly.
- Opcode shape data is constructor-only and should not add hot runtime fields.
- Method/property info factories preserve final body arrays and owner-linked
  body graphs.

### Documentation Updates Included

- Update [../this-escape-tally.md](../this-escape-tally.md) targeted-delta
  counts and remaining warnings.
- Update [../this-escape-removal-audit.md](../this-escape-removal-audit.md).
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) constructor
  escape sections.
- Update [../ownership-diagnostics.md](../ownership-diagnostics.md) for
  container registry and owner graph validation if touched.

### Review Checklist / Acceptance Criteria

- Constructors in scope do not publish `this` to owner-visible state.
- Constructors in scope do not call overridable hooks or public mutation APIs.
- Owner-linked child metadata is built before final owner fields are assigned.
- Lint output for touched groups is clean.
- Tests include hook-detecting subclasses or parallel owner-copy proofs where
  old construction timing mattered.

### Dependency / Order

Land after PR 1 through PR 6. It can be split into two PRs if review size is
still high: runtime/container/handle constructors first, then ASM/op/tooling
constructors.

## PR 8: Add Same-JVM Stress And Ownership Diagnostics

### PR Title

Add same-JVM stress and runtime ownership diagnostics

### Reviewer-Facing Problem Statement

The target failure mode is repeated runtime execution in one JVM. A normal unit
test or process-forked manual test can pass while stale process-global runtime
state survives across direct launcher runs.

Without ownership diagnostics, wrong-owner values often surface later as
misleading XTC-level failures. The branch needs a structural way to validate
that templates, handles, compositions, constant pools, and service contexts
belong to the current container.

### Exact Scope Included

- Add `OwnershipDiagnostics.dump(...)`, `validate(...)`, and `assertValid(...)`
  rooted at one or more interpreter `Container` instances.
- Add lightweight handle boundary validation controlled by
  `xvm.runtime.validateOwnership`.
- Expose completed diagnostic containers through production-safe hooks:

  - `Connector.diagnosticContainer()`;
  - `InterpreterConnector.diagnosticContainer()`;
  - `Runner.diagnosticContainer()`.

- Add direct same-JVM sequence stress for repeated `Runner.run()` calls through
  Gradle direct execution.
- Validate a bounded recent-container window after successful direct runs.
- Keep `manualTests:runParallelStress` as the aggressive parallel-container
  runner.

### Explicit Out Of Scope

- Functional runtime owner fixes not required for the diagnostics to compile.
- Benchmark claims without forked-mode comparison.
- Parallel direct-mode plugin stress before serial direct stress is stable.
- JIT ownership dump.

### Source Areas / Branch Commits

Commits:

- `833d8d72f Document runtime ownership diagnostics`
- `804022ceb Validate runtime ownership diagnostics`
- `f2d3c6ef4 Add direct same-JVM sequence stress`
- `64d2f448e Validate direct same-JVM runtime ownership`
- `cc4da9374 Plan same-JVM launcher stress`
- `0fb5d2cf0 Finish owner-local lazy cache diagnostics`
- `b0bb868c0 Document ownership regression tests`

Primary source areas:

- `OwnershipDiagnostics`
- `Connector`, `InterpreterConnector`, `Runner`
- direct executor / Gradle plugin direct-mode hooks where touched by this
  branch
- `manualTests/build.gradle.kts`
- ownership diagnostic tests

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.runtime.OwnershipDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all

CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --configuration-cache --console=plain --warning-mode=all

CI=true ./gradlew :manualTests:runParallelStress \
  -PstressIterations=2 \
  -PstressModules=TestReflection,TestArray,TestServices,TestTuples \
  --configuration-cache --console=plain --warning-mode=all
```

Use shorter module lists during PR iteration and record the exact final module
set in the PR body.

### Semantic / Performance Equivalence Notes

- Default diagnostics must not force lazy cache warmup.
- Forced lazy dumping is for explicit diagnostic runs and failure artifacts.
- Success-path validation should not build the full textual dump.
- Long stress loops must use a bounded recent-container window, not strong
  retention of every completed container.
- The current container must always be included in the validation window.

### Documentation Updates Included

- Update [../ownership-diagnostics.md](../ownership-diagnostics.md).
- Update [same-jvm-launcher-stress.md](same-jvm-launcher-stress.md).
- Update [../stress-discovered-runtime-issues.md](../stress-discovered-runtime-issues.md)
  with any failures found by the stress harness.
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) proof points.

### Review Checklist / Acceptance Criteria

- Diagnostics identify owner mismatches, constant-pool mismatches, and illegal
  cross-container sharing.
- Native parent sharing and true process resources are narrowly allowlisted.
- Failure messages include enough owner context to locate the first bad
  boundary.
- Stress tasks exercise repeated same-JVM execution, not only forked process
  execution.
- The harness does not create false confidence by running multiple unrelated
  Gradle builds against shared outputs concurrently.

### Dependency / Order

Best after PR 1 through PR 7 so the primary runtime owner graph exists. It
should land before PR 9 and PR 10 if those PRs rely on ownership stress for
proof.

## PR 9: Remove Semantic Ambient `ConstantPool` Lookup

### PR Title

Remove semantic ambient `ConstantPool` lookup

### Reviewer-Facing Problem Statement

`ConstantPool.getCurrentPool()` let semantic code choose an owner from hidden
thread-local state. Helpers that looked like receiver-owned methods or pure
type predicates created constants, resolved metadata, or routed diagnostics
through whichever pool happened to be bound on the Java thread.

This is wrong even single-threaded because nested calls can install another
pool or no pool. It blocks reentrant runtime and same-JVM execution because
worker threads, service callbacks, and async callbacks do not have an inherent
current pool.

### Exact Scope Included

- Replace semantic current-pool usage with explicit owner parameters or
  owner-bearing receiver state.
- Keep `ConstantPool.withPool(...)` only as a transitional boundary bridge.
- Add boundary assertions where an explicit owner and ambient bridge both
  exist.
- Remove `getCurrentPool()` so semantic code cannot silently pick an owner from
  thread-local state.
- Add source-shape tests that reject reintroducing the getter and reject new
  semantic callers outside the scoped bridge allowlist.
- Fix known branch sites:

  - type covariance/contravariance helpers;
  - numeric range folding;
  - function compatibility;
  - nested identity resolver-backed generic resolution;
  - method/property metadata pool helpers;
  - file-owned diagnostics;
  - TypeConstant recursion diagnostics.

### Explicit Out Of Scope

- ConstantPool freeze and registration atomicity redesign.
- Default constant adoption clone redesign.
- Broad `ThreadLocal` cleanup outside current-pool semantics.
- JIT `Ctx.Current` policy.

### Source Areas / Branch Commits

Commits:

- `4fb44ee74 Fix TypeConstant recursion diagnostic state`
- `e856d85ce Require explicit pool for type substitutability`
- `d58ebfea0 Use receiver pool for folded numeric ranges`
- `5d5773979 Use receiver pool for function compatibility`
- `5fce7b9ae Use explicit pool for nested identity resolution`
- `4c6521dd9 Use metadata owners instead of current pool`
- `c93b5ad61 Use file-owned diagnostics instead of current pool`
- `be0270e0d Deprecate ambient current pool lookup`
- `2716435f1 Restrict ambient current pool lookup`
- `84fa61534 Remove current pool lookup getter`

Primary source areas:

- `ConstantPool`
- `TypeConstant`, `SignatureConstant`, `TerminalTypeConstant`
- `ByteConstant`, `IntConstant`
- `IdentityConstant`
- `MethodBody`, `MethodInfo`, `PropertyInfo`
- `FileStructure`
- compiler call sites that already have a pool owner, such as `AstNode`

### Tests And Verification Commands

```bash
./gradlew :javatools:test \
  --tests org.xvm.asm.ConstantPoolDiagnosticsTest \
  --tests org.xvm.asm.constants.TypeConstantOwnerApiTest \
  --tests org.xvm.asm.constants.ConstantRangeOwnerTest \
  --tests org.xvm.asm.constants.NestedIdentityOwnerTest \
  --tests org.xvm.asm.constants.MethodInfoTest \
  --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest \
  --tests org.xvm.asm.FileStructureTest \
  --configuration-cache --console=plain --warning-mode=all

rg -n "getCurrentPool\\(" javatools/src/main/java/org/xvm
```

Run same-JVM direct stress with ownership validation after PR 8 if this PR
touches runtime boundary scopes.

### Semantic / Performance Equivalence Notes

- Correct old callers already had the same owner available; they now pass it
  directly.
- Constant interning remains in the same intended owner pool.
- Null checks and explicit parameters are intentional API hardening.
- `withPool(...)` remains a scoped bridge only for legacy boundaries with
  explicit owner assertions. There is no public getter that turns that bridge
  back into semantic owner selection.

### Documentation Updates Included

- Update [../ambient-context-audit.md](../ambient-context-audit.md).
- Update [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md)
  fixed and remaining current-pool sections.
- Update [../scoped-value.md](../scoped-value.md) if bridge policy changes.
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) ambient
  current-pool proof points.

### Review Checklist / Acceptance Criteria

- `getCurrentPool()` does not exist as a callable API.
- Remaining `withPool(...)` use is confined to documented transitional
  boundaries and paired with explicit owner assertions where possible.
- Receiver-owned methods use receiver owners.
- Type and metadata helpers require explicit pools where they intern or resolve
  constants.
- Tests cover no-ambient and wrong-ambient cases.
- PR text distinguishes transitional boundary bridges from semantic owner
  lookup.

### Dependency / Order

Can land after PR 8 for better stress proof, but the unit-level cleanup is
mostly independent of native-template migration. It should land before broad
ConstantPool freeze work.

## PR 10: Harden Constant Adoption Owner Transfer

### PR Title

Harden constant adoption owner transfer

### Reviewer-Facing Problem Statement

`Constant.adoptedBy(...)` changes pool ownership by shallow-cloning a constant
and reassigning the containing pool. `Object.clone()` copies final references,
locks, atomics, thread-local cells, helper maps, JIT names, and runtime handles.

The branch proved the bug with `SingletonConstant`: an adopted constant copied
the final `AtomicReference<InitState>` from another pool, letting one container
reuse another container's singleton runtime state.

The integration branch now narrows the API as well: `Constant.adoptedBy(...)`
is the final owner-transfer wrapper and reviewed special cases implement
`copyForAdoption(...)`. This keeps target-owner validation and ref reset in one
place while the broader family-by-family clone-free migration is reviewed
separately.

### Exact Scope Included

- Ensure adoption preserves serialized/logical constant value only.
- Make `Constant.adoptedBy(...)` the final owner-transfer wrapper and add the
  `copyForAdoption(...)` hook plus `AdoptionContext`.
- Reconstruct or clear owner-local runtime/helper state for:

  - `SingletonConstant` if not included in PR 5;
  - `FSNodeConstant`;
  - `FileStoreConstant`;
  - `DynamicFormalConstant`;
  - `FormalTypeChildConstant`;
  - `TypeConstant`;
  - `ParameterizedTypeConstant`;
  - `PropertyConstant`;
  - `RegisterConstant`;
  - `MethodBindingConstant`;
  - `SignatureConstant`;
  - `TypeParameterConstant`;
  - `MethodConstant`.

- Reject moving an already-owned live `HandleConstant` to another pool.
- Reject `DynamicFormalConstant` adoption when its register type is not shared
  with the destination pool, because the target pool cannot safely own or share
  that source-module type.
- Reject `RegisterConstant` adoption when the compiler register has not been
  allocated yet; otherwise the target pool would either share a moving source
  register or freeze an unstable index.
- Remove the `FrameDependentConstant` default-clone opt-in once
  `MethodBindingConstant` also reconstructs its descriptor explicitly.
- Add `ConstantAdoptionValidator` as an opt-in diagnostic at registration.
- Add source-shape coverage proving the high-risk constants use the hook instead
  of ad-hoc `adoptedBy(...)` overrides.
- Add late ConstantPool registration diagnostics as a guard, not as the full
  freeze solution.
- Prewarm the protected access type for canonical `ClassComposition` objects so
  `ensureAccess(PROTECTED)` does not register a new constant after runtime
  publication.
- Prewarm private/protected/struct access-type constants for already-known
  class/type constants before the diagnostic runtime publication marker is
  installed. This keeps first `ClassComposition` construction lazy without
  letting it register deterministic access constants after the marker.
- Reject accidental adoption of arbitrary runtime state copied by future
  shallow-clone fields, including handles, templates, type compositions, pools,
  threads, locks, atomics, and other owner-bearing mutable helpers.

### Explicit Out Of Scope

- Full clone-free removal of the transitional default-clone adoption policy for
  every constant family.
- ConstantPool list/map publication atomicity and runtime pool freeze.
- Runtime creation of genuinely new constants after publication. The
  access-type warmup only handles constants the pool already knows before the
  diagnostic marker.
- Compiler `Component`, AST, `Parameter`, and `MethodStructure` clone cleanup.
- `ObjectHandle.cloneAs(...)` redesign.
- JIT generated static ownership.

### Source Areas / Branch Commits

Commits:

- `09f244211 Fix adopted constant runtime state ownership`
- `e569d27db Harden constant adoption ownership`
- `e6f78a210 Validate constant adoption helper ownership`
- `d1d0683e3 Guard late constant pool registration`
- related `0aa9a86cd Harden owner-sensitive lazy caches`
- `a0c1fe936 Harden constant adoption runtime-state validation`

Primary source areas:

- `Constant`, `ConstantPool`, `ConstantAdoptionValidator`
- `SingletonConstant`
- `FSNodeConstant`, `FileStoreConstant`
- `TypeConstant`, `ParameterizedTypeConstant`, `SignatureConstant`,
  `TypeParameterConstant`, `DynamicFormalConstant`, `RegisterConstant`,
  `MethodBindingConstant`
- `HandleConstant`
- `ClassComposition`
- `OwnershipDiagnostics` boundary validation where adoption failures surface

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.asm.ConstantAdoptionTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.SingletonConstantTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test \
  --tests org.xvm.runtime.ClassCompositionLateRegistrationTest \
  --configuration-cache --console=plain --warning-mode=all

CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestProps \
  --configuration-cache --console=plain --warning-mode=all

CI=true ./gradlew :manualTests:runParallelStress \
  -PstressIterations=2 \
  -PstressModules=TestProps \
  --configuration-cache --console=plain --warning-mode=all
```

For local stress, enable the diagnostic properties used by this branch where
appropriate:

```bash
-Dxvm.asm.validateConstantAdoption=true
-Dxvm.asm.validateConstantPoolCurrentScope=true
-Dxvm.asm.validateConstantPoolLateRegistration=true
-Dxvm.runtime.validateOwnership=true
```

### Semantic / Performance Equivalence Notes

- Source constants keep their owner-local runtime state.
- Adopted constants start with fresh or cleared owner-local helper state and
  recompute in the destination pool after first use.
- Logical equality and interning semantics should remain unchanged.
- Live runtime handles may be initially registered when fresh/unowned; moving an
  already-owned handle to a different pool should fail.
- `DynamicFormalConstant` keeps the same serialized register index/id behavior
  for valid shared/upstream type graphs, but invalid unrelated-pool adoption now
  fails at adoption instead of publishing a bad constant graph.
- `RegisterConstant` keeps the same runtime/deserialized behavior: adopted
  constants use the register index only. Compile-time type convenience from the
  source `Register` is deliberately not copied across pools.
- `MethodBindingConstant` keeps the same descriptor and recursive method
  identity registration behavior, but no frame-dependent subclass now receives
  shallow-clone adoption by inheritance.
- The validator is diagnostic coverage, not a complete architectural fix.
- The validator is off unless explicitly enabled, so normal constant interning
  and runtime cache performance is unchanged.

### Documentation Updates Included

- Update [../constant-adoption-clone-audit.md](../constant-adoption-clone-audit.md).
- Update [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md)
  adoption and live-handle sections.
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) adoption
  proof points.
- Update [../stress-discovered-runtime-issues.md](../stress-discovered-runtime-issues.md)
  for the `TestProps` adopted singleton leak.

### Review Checklist / Acceptance Criteria

- Every touched constant subclass declares whether adoption copies, clears,
  reconstructs, or rejects non-logical state.
- Tests warm source state before adoption and prove the adopted copy does not
  share it.
- `HandleConstant` cannot silently move an already-owned live handle.
- Validator reports enough class/pool/field context to debug a future failure.
- PR text does not claim ConstantPool is fully frozen or reentrant-safe after
  this slice.

### Dependency / Order

Should land after PR 5 if enum singleton behavior is split separately, and
after PR 8 if stress proof is expected in the same PR. This PR should land
before remaining clone/copy cleanup in PR 11.

## PR 10b: Guard ConstantPool Registration Publication

### PR Title

Guard ConstantPool registration publication

### Reviewer-Facing Problem Statement

`ConstantPool.register(...)` used one public list/map as both:

- the private worklist needed to resolve recursive constant graphs; and
- the completed public pool storage used by normal readers.

Master assigns a position, appends the constant to `f_listConst`, and inserts it
into the lookup map before recursive `registerConstants(...)` and valid-pool
checks finish. That probably made recursive constant graphs easy to implement,
but it made the public API lie: "registered" could mean "visible but not
complete."

This is bad even when a single thread happens to drive registration, because
phase is hidden in mutable object state. It becomes a direct same-JVM runtime
bug when another execution owner can read the public pool while registration is
still in the recursive phase.

### Exact Scope Included

- Add an in-progress registration completion marker for newly inserted
  constants.
- Preserve same-thread recursive lookup for the registration owner.
- Make public readers in other threads wait until recursive registration and
  valid-pool checks complete.
- Preserve failed registration as failed for later public readers instead of
  returning a partial constant graph.
- Keep this as a compatibility guard, not the final transaction design.

### Explicit Out Of Scope

- Full clone-free constant adoption. That is PR 10 / the clone-free adoption
  plan.
- Runtime pool freeze and late-registration policy.
- Rewriting every `registerConstants(...)` implementation.
- Replacing the guard with a private registration transaction/worklist. That is
  tracked in
  [transactional-constant-registration-plan.md](transactional-constant-registration-plan.md).

### Source Areas / Branch Commits

Commits:

- Current registration-completion guard wave.

Primary source areas:

- `ConstantPool`
- `Constant`
- `ConstantPoolDiagnosticsTest`
- `constant-pool-must-audit-classification.md`
- `bad-design-decisions-reference.md`
- `plans/transactional-constant-registration-plan.md`

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.asm.ConstantPoolDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:jar --rerun-tasks \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --configuration-cache --console=plain --warning-mode=all
```

### Semantic / Performance Equivalence Notes

- Same-thread recursive registration still works. The test
  `registrationOwnerCanResolveInProgressConstant()` covers the reason early
  publication existed.
- Other threads no longer receive a half-registered constant. The test
  `otherThreadsWaitForRecursiveRegistrationCompletion()` covers the public
  cross-thread observation path.
- A failed registration remains failed for public readers. The test
  `failedRecursiveRegistrationStaysFailedForReaders()` prevents accidentally
  treating a partial graph as stable.
- There is no per-constant footprint after successful registration completes.
  The guard is a volatile fast-path flag plus a temporary completion object while
  a registration window is open.

### Documentation Updates Included

- Update [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md).
- Update [../constant-pool-must-audit-classification.md](../constant-pool-must-audit-classification.md).
- Update [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md).
- Update [transactional-constant-registration-plan.md](transactional-constant-registration-plan.md).

### Review Checklist / Acceptance Criteria

- The PR must not claim that `ConstantPool` is fully transactional after the
  guard.
- The tests must prove same-thread recursion still works.
- The tests must prove cross-thread public readers do not observe incomplete
  registration.
- The PR text must explain why the final target is private transactional
  registration, not a permanent completion marker.

### Dependency / Order

This should land after or together with the adoption guard PR if the branch is
split by review size. It should land before any claim that same-JVM runtime
constant-pool access is safe. The later transaction rewrite should remove the
guard once public early publication no longer exists.

## PR 11: Fix Method, Parameter, And Handle Owner-Copy Hazards

### PR Title

Fix method, parameter, and handle owner-copy hazards

### Reviewer-Facing Problem Statement

The branch documents clone/copy owner hazards that should not be bundled into
the first runtime-owner PR. The highest-risk findings are:

- `Parameter.cloneBody()` mutated the source and preserved copied deref state.
- `MethodStructure.cloneBody()` assigned cloned parameters to the original
  method owner.
- Delegated methods cloned only `Parameter[]` containers and shared mutable
  `Parameter` elements.
- `ObjectHandle.cloneAs(...)` shallow-copies runtime handles and field arrays;
  the direct cross-owner `GenericHandle.maskAs(...)` path could use that access
  view as if it were owner transfer.
- The same shallow `GenericHandle.cloneAs(...)` path shared the final field
  array and then rewrote inflated `RefHandle.$outer` entries inside that shared
  backing, so creating a same-owner struct/revealed view could mutate refs
  visible through the source view.
- The default `Constant.adoptedBy(...)` shallow clone remains a bad default for
  future owner-local fields.

### Exact Scope Included

- Replace the `Parameter` clone path with an explicit target-owner copy API.
- Fix `MethodStructure.cloneBody()` so cloned returns/parameters are contained
  by the cloned method.
- Deep-copy delegated method parameters so owner-sensitive deref state cannot
  be shared through an array clone.
- Reject direct cross-owner `GenericHandle.maskAs(...)` unless the handle graph
  is already shared with the target container.
- Keep same-owner `GenericHandle` access views sharing regular field storage,
  but store view-specific inflated refs in sparse copy-on-write overrides so
  `$outer` rebinding cannot corrupt the source view.
- Add focused owner-copy tests for method/parameter clone paths.
- Keep the base `ObjectHandle.cloneAs(...)` subclass and relocation audit as a
  follow-up; this PR fixes the proven `GenericHandle` access-view defects.

### Explicit Out Of Scope

- Native-template migration.
- Compiler AST clone redesign beyond the method/parameter owner bug.
- Full ConstantPool freeze.
- JIT bridge array clone cleanup unless submitted as a JIT-specific PR.

### Source Areas / Branch Commits

Part of this is already fixed in the branch. The safe review shape is a
method/parameter owner-copy PR, followed later by a separate handle view-copy
subclass/relocation clone audit.

Commits:

- `7f82e0a1e Fix method parameter clone ownership`
- `ed7220bee Copy delegated method parameters by owner`

Planning sources:

- [../clone-usage-audit.md](../clone-usage-audit.md)
- [xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md)
- [../must-audit-backlog.md](../must-audit-backlog.md)

Future source areas:

- `Parameter`
- `MethodStructure`
- `ClassStructure.ensureMethodDelegation(...)`
- `ObjectHandle.GenericHandle.maskAs(...)`
- `ObjectHandle.GenericHandle.cloneAs(...)`
- `OwnershipDiagnostics` view/override dump support
- `ObjectHandle` and relevant handle subclasses for the later base clone audit
- `Constant.adoptedBy(...)` only for a later adoption-contract redesign

### Tests And Verification Commands

Focused tests:

```bash
./gradlew :javatools:test --tests org.xvm.asm.AsmConstructorEscapeTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.runtime.OwnershipDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all
```

Required proof shapes:

- Clone a method with implicitly dereferenced parameters and prove source and
  copy have distinct deref registers owned by their respective methods.
- Clone a method body and assert every return/parameter reports the cloned
  method as containing structure.
- Build delegated methods and prove no `Parameter`, deref register, or owner
  state is shared with the original.
- For same-owner handle views, prove clone ref rebinding does not mutate the
  source view, and prove that referent/regular-field write-through semantics
  still match master's intended access-view behavior.
- For the cross-owner mask guard, use a non-shared synthetic handle whose
  `cloneAs(...)` throws if reached; the fixed path must return `null` before
  any shallow clone.

### Semantic / Performance Equivalence Notes

- Copies should preserve logical metadata, not owner-local helper state.
- Extra allocation at clone/construction time is acceptable when it replaces
  unsafe source sharing.
- Invocation/runtime hot paths should not gain per-use locks or deep copies.
  The current parameter fixes allocate while constructing copied metadata,
  which is the same phase that already allocated the clone.
- The `GenericHandle.maskAs(...)` guard only runs when the caller asks to mask a
  handle into a different owner. Same-owner masks and reveals preserve the old
  cheap access-view behavior.
- The same-owner `GenericHandle.cloneAs(...)` fix preserves regular field-array
  sharing. Handles without inflated-ref view overrides still use direct
  `m_aFields` access. A struct/revealed transition allocates at most one sparse
  override array plus one ref view per affected inflated field; it is not a
  deep copy of the handle graph.
- Immutable logical arrays can remain shared when the API documents that they
  are immutable and owner-free.

### Documentation Updates Included

- Update [../clone-usage-audit.md](../clone-usage-audit.md) with fixed status.
- Update [../fixed-in-this-branch.md](../fixed-in-this-branch.md) with the
  parameter and delegated method copy proof.
- Update [xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md) project
  ordering.
- Update [../must-audit-backlog.md](../must-audit-backlog.md) consolidated task
  list.

### Review Checklist / Acceptance Criteria

- Target owner is explicit in every replacement copy API.
- Copying does not mutate the source object.
- Copied owner-local helper state starts empty or is rebuilt under the target.
- Tests prove two-owner separation.
- PR does not quietly redesign unrelated compiler AST clone semantics.

### Dependency / Order

Follow PR 10. This should be a separate review from runtime native-template
ownership and can be split into method/parameter clone and runtime handle view
clone sub-PRs.

## PR 12: Keep Compiler Reentrancy Cleanup Separate

### PR Title

Remove compiler constructor escapes without changing compiler behavior

### Reviewer-Facing Problem Statement

Compiler `Lexer`, `Parser`, and AST constructors published owner/parent/stage
state or called overridable behavior during construction. This is bad even for
single-threaded compilation because a node can have parentage, component
ownership, or validation state before concrete fields are initialized.

It blocks incremental and parallel compiler work because request-local AST and
diagnostic state cannot be audited if constructors publish partially built
nodes.

### Exact Scope Included

- Remove `Lexer` constructor calls to overridable whitespace parsing.
- Make `Parser` token priming lazy while preserving `mark()`/`restore()`.
- Use factories for synthetic expression and statement nodes that attach
  parent/component/type state after construction.
- Keep `ConvertExpression` constant-folding behavior unchanged while avoiding
  constructor-time inherited helper dispatch.
- Document compiler counter race classification separately.

### Explicit Out Of Scope

- Runtime native-template/global cache changes.
- Interpreter ownership diagnostics.
- JIT lifecycle work.
- Changing compiler diagnostic policy for constant folding.
- Compiler counters unless this PR is specifically the compiler-counter PR.
- Compiler AST clone redesign beyond the constructor-escape cleanup.

### Source Areas / Branch Commits

Commits:

- `70bf202ef Remove compiler constructor escape warnings`
- `9d012df80 Clarify compiler counter race classification`

Primary source areas:

- `Lexer`
- `Parser`
- `compiler/ast` synthetic expression and statement factories
- `ConvertExpression`
- `NamedTypeExpression`
- `TypeCompositionStatement`
- compiler constructor-escape tests

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.compiler.CompilerThisEscapeConstructionTest \
  --configuration-cache --console=plain --warning-mode=all
```

Run the common targeted lint command and confirm zero `this-escape`
diagnostics for `Lexer.java`, `Parser.java`, and `compiler/ast`.

### Semantic / Performance Equivalence Notes

- Lazy parser priming must preserve token stream behavior across speculative
  parse `mark()`/`restore()` paths.
- Constructor cleanup must not change constant folding, runtime conversion, or
  diagnostic severity.
- Compiler request confinement can justify leaving some caches alone, but that
  proof must be documented before parallel compiler work depends on it.

### Documentation Updates Included

- Update [../compiler-lexer-parser-this-escape.md](../compiler-lexer-parser-this-escape.md).
- Update [../this-escape-tally.md](../this-escape-tally.md) compiler warning
  counts.
- Update [../must-audit-backlog.md](../must-audit-backlog.md) compiler backlog
  items.

### Review Checklist / Acceptance Criteria

- Hook-detecting lexer/parser tests fail on the old constructor shape.
- Synthetic AST construction separates object construction from parent/component
  publication.
- No compiler/AST `this-escape` diagnostics remain in the targeted lint output.
- Compiler behavior changes are either absent or explicitly reviewed as
  compiler behavior PRs.

### Dependency / Order

Independent of runtime PRs except for shared ASM API changes. Keep it separate
from PR 7 so reviewers are not asked to review runtime and compiler ownership
models together.

## PR 13: Keep JIT Ownership Cleanup Separate

### PR Title

Remove local JIT constructor escapes and plan JIT owner lifecycle work

### Reviewer-Facing Problem Statement

The JIT has a different owner model from the interpreter runtime:
`Xvm`, JIT `Container`, `TypeSystem`, `TypeSystemLoader`, `ModuleLoader`,
generated Java statics, and `Ctx.Current`. Interpreter `NativeTemplates` fixes
do not prove JIT ownership safety.

The branch fixes local JIT constructor warnings whose behavior can be preserved
mechanically, but leaves `Xvm` startup owner publication for a larger lifecycle
PR.

### Exact Scope Included

- Replace local constructor-time JIT owner publication where the fix is
  mechanical:

  - `BuildContext` / `TypeMatrix`;
  - `JitMethodDesc` / `JitCtorDesc`;
  - `ArrayBuilder`;
  - `nLongBasedArray`;
  - narrow fallthrough suppressions where they document intentional state
    machines.

- Document the remaining `Xvm.java` constructor escape and the required
  `XvmState` or `XvmServices` shape.
- Smoke-test launcher JIT path after shared owner API changes.

### Explicit Out Of Scope

- Interpreter native-template migration.
- JIT `Xvm` lifecycle refactor in the local-cleanup PR.
- Generated `$INSTANCE` / `$scN` ownership policy.
- JIT ownership dump implementation.
- ConstantPool freeze unless required by a shared ASM cache fix.

### Source Areas / Branch Commits

Commits:

- `36c24a974 Document remaining JIT constructor escapes`
- `cb81116cb Remove local JIT constructor escapes`

Primary source areas:

- `BuildContext`
- `TypeMatrix`
- `JitMethodDesc`
- `JitCtorDesc`
- `ArrayBuilder`
- `CommonBuilder`
- `javatools_jitbridge` `nLongBasedArray`

Future lifecycle source areas:

- `Xvm`
- `NativeTypeSystem`
- JIT `TypeSystem`
- JIT `Container`
- `TypeSystemLoader`
- `ModuleLoader`
- generated class initialization helpers

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests org.xvm.javajit.JitConstructorEscapeTest \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools_jitbridge:compileJava \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:compileJava :javatools_jitbridge:compileJava \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=1000 \
  --rerun-tasks --no-build-cache \
  --configuration-cache --console=plain --warning-mode=all
```

For launcher smoke after install:

```bash
./gradlew :xdk:installDist --configuration-cache --console=plain --warning-mode=all
xdk/build/install/xdk/bin/xec \
  -L manualTests/build/xtc/main/lib \
  -L manualTests/build/xtc/xdk/lib \
  -J EchoTest hello jit
```

### Semantic / Performance Equivalence Notes

- Preserve JIT descriptor parameter ordering and constructor implicit
  parameters.
- Preserve `TypeMatrix` live-context lookup after construction while avoiding
  construction-time observation.
- Preserve raw-storage array packed size/mutability semantics.
- Future `XvmState` work must preserve native container id `-1`, weak-map
  cleanup, name generation, module loader behavior, and hot generated-code
  performance.

### Documentation Updates Included

- Update [../jit-implications.md](../jit-implications.md).
- Update [xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md) JIT
  project ordering and proof requirements.
- Update [../this-escape-tally.md](../this-escape-tally.md) remaining JIT
  warning status.

### Review Checklist / Acceptance Criteria

- Local JIT constructor warnings are removed without lifecycle redesign.
- `Xvm.java` startup owner publication remains explicitly documented if not
  fixed.
- Tests prove descriptor equivalence.
- PR does not claim the JIT runtime is fully reentrant-safe.
- Any future generated static owner claims are tested with multiple JIT
  containers or type systems.

### Dependency / Order

Independent of interpreter runtime PRs except for shared ASM API changes. Keep
the local cleanup separate from the later `XvmState` lifecycle PR.

## PR 14: Add Build, Lint, And Source-Shape Gates

### PR Title

Add reentrancy source-shape and constructor-escape gates

### Reviewer-Facing Problem Statement

The branch removes repeated bad patterns: constructor-published `INSTANCE`,
owner-bearing JVM-global metadata, semantic ambient current-pool lookup,
manual lazy null caches, raw enum-publication paths, shallow clone owner
transfer, and constructor `this` escapes. Without gates, those patterns can
return in small future changes.

This is bad even single-threaded because the owner dependency is hidden from
the API. It blocks reentrant and same-JVM execution because one hidden global or
constructor publication can poison later containers.

### Exact Scope Included

- Enable or prepare `javac -Xlint:this-escape` for the relevant Java compile
  graph.
- Add cheap source-shape tests or scans for:

  - mutable runtime-template `INSTANCE`;
  - `INSTANCE = this`;
  - owner-bearing non-final static runtime metadata;
  - ownerless runtime handle factories;
  - any reintroduced `ConstantPool.getCurrentPool()` getter or semantic
    ambient owner lookup;
  - new `Object.clone()` adoption/copy sites;
  - raw public enum publication helpers;
  - manual lazy null caches in shared runtime/ASM owner state.

- Keep ownership validation, constant-adoption validation, and late
  registration diagnostics available in stress profiles.
- Require local suppressions to document owner, lifetime, and why construction
  publication is safe.

### Explicit Out Of Scope

- Broad cleanup for unrelated lint categories such as rawtypes, unchecked,
  fallthrough, serial, or try.
- Functional fixes discovered by the gates.
- CI policy changes that cannot be reproduced locally.
- Documentation-only audit expansion without an enforceable check.

### Source Areas / Branch Commits

Current branch preparation:

- `9456d6727 Tighten lint and constructor escape audit`
- `b0bb868c0 Document ownership regression tests`
- `be0270e0d Deprecate ambient current pool lookup`
- `2716435f1 Restrict ambient current pool lookup`
- `84fa61534 Remove current pool lookup getter`

Expected future source areas:

- Gradle Java conventions or build logic for lint flags.
- Focused source-shape tests under `javatools/src/test/java`.
- `manualTests/build.gradle.kts` stress task properties.
- CI/stress profile configuration for diagnostic system properties.

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests '*OldPattern*' \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests org.xvm.asm.ConstantPoolDiagnosticsTest \
  --configuration-cache --console=plain --warning-mode=all
```

Run the common targeted lint command. For a full validation before merging the
gate PR, run the relevant broader build/test command with configuration cache
enabled. If clean is needed, run it alone first.

### Semantic / Performance Equivalence Notes

- Gates should not change runtime semantics.
- Cheap source-shape tests should run normally.
- Expensive stress and validation can remain opt-in if they are documented and
  easy to run locally.
- Do not promote every lint category at once. The immediate target is
  `this-escape` and the reentrancy source patterns.

### Documentation Updates Included

- Update [../this-escape-tally.md](../this-escape-tally.md) with current
  warning policy and remaining exceptions.
- Update [../must-audit-backlog.md](../must-audit-backlog.md) build-gate
  backlog status.
- Update [../README.md](../README.md) if the reentrancy docs index needs the
  new gate instructions.
- Update this file if the PR sequence changes after review.

### Review Checklist / Acceptance Criteria

- Gates fail for representative old patterns.
- Suppression policy is local and owner-specific.
- Configuration-cache compatibility is preserved.
- No `clean` task is combined with other Gradle tasks in documented commands.
- The PR does not silently start enforcing unrelated lint categories.

### Dependency / Order

Land after each pattern category is clean enough for the gate to be useful.
This is the final hardening PR for the current sequence, but individual cheap
source-shape tests can land earlier with the PR that fixes that pattern.

## Cross-PR Review Checklist

Use this short checklist on every PR in the sequence:

- What is the owner of each cached or published value?
- Is that owner visible in an API parameter, receiver, or explicit owner field?
- Can the object be observed before construction completes?
- Does a lazy value capture `this` during construction?
- If a value is static, is it truly process-global and immutable?
- If a value is adopted, cloned, masked, or copied, which fields are logical
  value state and which fields are owner-local helper state?
- Does the PR include a proof that fails on the old design?
- Does the PR preserve old cache granularity or explicitly justify a
  performance/allocation change?
- Are interpreter runtime, compiler, shared ASM, ConstantPool, and JIT concerns
  kept in separate review units unless a mechanical dependency requires them
  together?
