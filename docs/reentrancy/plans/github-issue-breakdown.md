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
- [../logging-diagnostics-audit.md](../logging-diagnostics-audit.md)
- [../logging-strategy.md](../logging-strategy.md)
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
| 8b | Preserve runtime failure causes and VM defects | Keeps owner, pool, module, stack, worker-defect, and op-defect evidence when startup/runtime setup fails. | Independent; stronger after PR 8 diagnostics |
| 9 | Remove semantic ambient `ConstantPool` lookup | Replaces `getCurrentPool()` semantic owner selection with explicit owners. | PR 8 useful for stress, otherwise independent |
| 10 | Harden constant adoption owner transfer | Prevents shallow-cloned constants from carrying runtime/helper state across pools. | PR 5 and PR 8 recommended |
| 10b | Guard ConstantPool registration publication | Separates same-thread recursive registration from public cross-thread observation while the later transaction design is prepared. | PR 10 recommended |
| 11 | Fix method, parameter, and handle owner-copy hazards | Repairs method/parameter/delegated copies, constrains direct cross-owner handle masks, and fixes same-owner `GenericHandle` inflated-ref view backing. | PR 10 |
| 12 | Keep compiler reentrancy cleanup separate | Moves lexer/parser/AST constructor and compiler-owner work out of runtime review. | Independent after shared ASM API changes |
| 13 | Keep JIT ownership cleanup separate | Keeps JIT lifecycle, generated static fields, and `Ctx.Current` review separate from interpreter runtime. | PR 9/10 for shared ASM safety |
| 14 | Add build, lint, and source-shape gates | Turns fixed patterns into regressions that fail early. | After the relevant patterns are clean |
| 15 | Add structured diagnostics and logging discipline | Replaces stdout/stderr compiler/runtime decisions with typed diagnostics, guarded trace logging, and stable repro fixtures. | Independent; stronger after PR 8 and PR 8b |

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
| Runtime failure propagation | MainContainer cause-preservation wave, worker terminal-failure channel, op-loop VM-defect propagation, `Future.and` completion failure handling, and raw file submit failure observation | Broad exception hygiene |
| Compiler/ASM terminal failure boundaries | `b00654356 Make compiler codegen failures terminal` and the method op-assembly terminal-failure slice (`MethodStructure.assemble` with `MethodStructureAssemblyFailureTest`) | PR 15 structured diagnostics, repository requested-load cause preservation |
| JIT ownership cleanup | `36c24a974`, `cb81116cb`, the JIT unhandled-result wave, plus the separate JIT plan work | Interpreter runtime template ownership |
| Documentation-only hardening studies | `f0a6a71b1` and any uncommitted plan/audit docs | Source changes unless the PR is explicitly mixed for review proof |

If a folded slice becomes too large, split by invariant rather than package
name. For example, "explicit receiver pool for semantic type operations" and
"file-owned diagnostics" are both current-pool work, but they can be separate
PRs if reviewers want smaller diffs. Do not split a test away from the source
change it proves.

### Master-Bug PRs Versus Reentrancy Enablement

This classification answers one extraction question per item: is it a
directly fixable bug on master (wrong behavior reachable today, often even
single-threaded), portable hardening (no behavior change, converts a
convention into a machine-checked invariant), reentrancy enablement (only
matters once one JVM runs compilers/runtimes repeatedly or concurrently), or
a branch-internal repair that must never ship on its own because it fixes a
regression this branch introduced.

Category A - directly fixable master bugs, each extractable as a small
standalone PR with its red-on-master test:

| Fix | Single-threaded reachable on master? | Proof |
| --- | --- | --- |
| jsondb rollback failure retention (single hunk, applies verbatim) | Yes - any failed commit whose compensation also fails | ledger row 34 |
| Requested module loads preserve cause (`ModuleLoadException`) | Yes - any corrupt `.xtc` file | `ModuleRepositoryLoadFailureTest` |
| Method op-assembly failure terminal (no zero-op serialization) | Yes - any compiler defect at emission | `MethodStructureAssemblyFailureTest` |
| Compiler codegen failure terminal (no catch-Throwable-continue) | Yes | `CompilerCodegenFailureTest` |
| Raw file submit observes queued write failure | Yes - async I/O exists in single-service programs | `RawOSFileChannelSubmitTest` |
| `Future.and` fast-path double-read and assert-only failure path | Yes | `FutureCompletionSafetyTest` |
| JIT connector non-zero result after generated exception; `nType` exception unwrapping | Yes | `JitFailurePropagationTest` |
| `MainContainer` startup cause preservation | Yes | `RuntimeFailurePropagationTest` |
| Alarm callback registry race and cancel leak (Timer-killer) | Concurrent, but reachable in ANY timer-using program: the service thread and the shared static Timer thread both exist without parallel containers | `NativeCallbackRegistrationTest.callbackRegistryIsConcurrentTimerSafeAndLeakFree()` |
| Native callback registration rollback (clock/timer/server schedule-failure leaks) | Yes - schedule/bind failure paths | `NativeCallbackRegistrationTest` |
| Hash/equality contracts (`Register`, `VersionTree`; cycle-safe `MethodBody` target equality) | Yes for map/set misuse; the MethodBody equality stack overflow needs legal cyclic metadata | `RegisterHashCodeTest`, `VersionTest`, `MethodInfoTest` |
| Handle view-clone lifecycle desyncs (row 161): mechanism 3 fixed (atomic/injected cells) | Mechanism 3 is a master bug even without views: master's unsynchronized lazy install races two services' first assignments on one shared `@Atomic` instance and loses an update. Mechanism 2 (lazy guard, also fixed) is branch-enablement only - master shared a single inflated-ref instance. Mechanisms 1/4 (RefHandle deref desync, Mutable-array storage-pointer forks) are closed as fail-loud guards - latent, no reachable clone path on master today. Mechanism 5 (`makeImmutable` split) is fixed via the shared freeze cell and is master-shaped: the per-view flag is verbatim master, reachable whenever any view coexists with the object at freeze time. Residuals outside `GenericHandle` (TupleHandle, ArrayHandle's mutability enum, FunctionHandle) are an open must-fix row | `AtomicViewSharingTest`, `RefViewGuardTest`, `ArrayViewGuardTest`, `FreezeViewSharingTest` |
| OPEN: `ConstHeap` live `HandleConstant` served across containers (row 125 graduation) | Needs two containers over one module - but master creates sibling/nested containers itself (mgmt, injection), so this is master-reachable, not reuse-only | to be written |
| OPEN: reflection `Method.invoke` aliases the caller's tuple storage into the callee register file (array-audit graduation) | Yes - single-threaded: any reflective invoke whose target needs no extra registers; a parameter reassignment corrupts the caller's tuple, including const-heap-cached ones | to be written with the fix (mirror the xRTFunction.java:254 clone) |
| OPEN: short-hand property override rewrites the library module's shared `Parameter` constants (array-audit graduation) | Yes - single-threaded compile of a short-hand property override | to be written with the fix (`createMethodCopyingParameters` machinery already in-branch) |
| Utility constructor this-escape fixes | Yes (latent; class partly unused) | PR #539, already extracted |

Category H - portable hardening, no behavior change, ships as its own small
PR whenever wanted:

| Item | Note |
| --- | --- |
| `-Xlint:this-escape` fatal gate | zero suppressions remain; the last one (`Xvm` startup) was eliminated by the boot-factory refactor |
| `-Xlint:fallthrough` fatal gate + 35 method suppressions + 4 added markers | the classification found ZERO live fallthrough bugs on the branch or master - this is purely protection against the future forgotten `break`; be explicit about that in the PR description |
| Build-verification habit: full `xdk:installDist` (or `lib-json` compile) alongside unit suites | four masked compile regressions hid behind unit-green builds on this branch |
| Sealed-hierarchy adoption stages 0-1 (ConditionalConstant/PseudoConstant/FrameDependentConstant/TypeInfo, then BinaryAST) | no behavior change today; converts silently-defaulting format switches (39 repo-wide produce a value on an unknown format) toward compile-checked exhaustiveness so a future added format fails the build instead of silently misclassifying - migration study in `sealed-hierarchy-audit.md` |

Category B - reentrancy/same-JVM enablement. These are not observable bugs
in one-run-per-process master usage; they are exactly the work that makes
sequential re-use and parallel containers in one JVM safe, and they are the
substance of PRs 1-13. Master-parallel caveat: master itself runs parallel
services and containers, so a subset (worker terminal-failure channel,
op-loop defect propagation, runtime op caches, recursion-diagnostics set) is
master-reachable under load and can be argued into category A during review.

| Wave | Why it is reuse-enablement |
| --- | --- |
| Native template `INSTANCE` removal, owner-local metadata/caches, enum lifecycle, container-owned TypeHandles | last-writer-wins globals only bite when a second container/run exists in the JVM |
| Clone-free constant adoption + `ConstantPool` ownership (ambient-pool removal, registration guard, future freeze/transaction rewrite) | pools are per-process-single-use on master; reuse makes late mutation and wrong-owner interning visible |
| `TypeInfo`/`MethodInfo`/`PropertyInfo` owner copies and safe publication | first-publication races need concurrent first access |
| Constructor-escape removal waves | correctness hardening whose failure mode is exposure of half-built owners to other threads |
| Same-JVM stress harness, ownership diagnostics, world-state snapshot plan | the proof infrastructure itself |
| Compiler request-ownership (PR 12), JIT ownership (PR 13), compiler counters | parallel/incremental compilation enablement |

Category C - branch-internal repairs; never port, they merge into their
parent waves when those become PRs:

| Repair | Parent wave |
| --- | --- |
| `Annotation` constructor param-array aliasing restore | clone-free adoption (PR 10) |
| `NativeRebaseConstant` / `EnumValueConstant` adoption reconstruction fixes | clone-free adoption (PR 10) |
| `MethodBody` owned-copy spurious self-target fix | constructor-escape/owner-copy wave (PR 7/11) |
| Registration-guard concurrent-insert deadlock fix | registration publication guard (PR 10b) |

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
- Make timer and server native callback registration exception-safe. Keep-alive
  registration must either publish a live callback that will unregister later or
  roll back the container callback count on startup/scheduling failure.
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
- native callback rollback wave: `xLocalClock`, `xNanosTimer`, and
  `xRTServer.invokeBind(...)`
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
- Keep the old keep-alive semantics on success: pending alarms and bound
  servers still increment the owning container callback count. The change is
  rollback symmetry on failed startup/scheduling, not a cache or footprint
  change.
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

### Follow-Up Once This PR Lands

- Convert the `AbstractConverterMap` view caching (PR #539's reviewer-requested
  plain private lazy fields, the `java.util.AbstractMap` benign-race idiom) to
  `Lazy.ofOwner(...)`. The API itself no longer waits for this PR: PR #539 now
  carries `Lazy.Owner`/`ofOwner` as a purely additive backport (no master code
  touches `Lazy`), while deliberately keeping the plain fields during review.
  The plain fields are correct there — the views are per-instance, owner-free,
  and final-field-only, so racy publication is safe — but converting turns
  those manually re-verified facts into type-level guarantees (final holder
  field, compute-at-most-once, access-time owner passing) and removes the last
  documented benign-race special case so every lazy cache in `javatools_utils`
  reasons through one pattern. Pure uniformity cleanup as its own tiny diff,
  once the PR #539 review has settled.

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

- Reconstruct `AllCondition`, `AnyCondition`, `NamedCondition`, `NotCondition`,
  `PresentCondition`, `VersionMatchesCondition`, and `VersionedCondition` from
  logical predicate fields if this slice includes the condition-family
  clone-free wave. Otherwise keep that wave as the condition half of the later
  value/condition adoption PR.
- Reconstruct `UInt8ArrayConstant`, `FPNConstant`, and `Float128Constant` with
  fresh byte arrays if this slice includes the byte-array-backed value wave.
  Otherwise keep that wave as the array-backed-value part of the later
  value/condition adoption PR.
- Reconstruct immutable scalar value constants from logical scalar fields if
  this slice includes the scalar-value wave. Otherwise keep that wave as the
  scalar-value part of the later value/condition adoption PR.
- Reconstruct `ArrayConstant`, `MapConstant`, and `RangeConstant` from logical
  value containers/endpoints if this slice includes the composite-value wave.
  Keep type-family owner conversion separate: array/map type constants and
  the `MatchAnyConstant` type locator still depend on PR 3.
- Reconstruct `LiteralConstant`, `VersionConstant`, and `DecimalAutoConstant`
  from logical parsed/delegated value state if this slice includes the
  parsed-value wave.
- Reconstruct the `MatchAnyConstant` wildcard shell from its logical type key if
  this slice includes the type-keyed sentinel wave. Reject unrelated foreign
  type keys before the value is published; shared/adoptable type keys continue
  through target registration and the clone-free type-family hooks.
- Reconstruct `TerminalTypeConstant` from its defining identity if this slice
  includes the terminal-type leaf wave. Shared identities keep the same
  destination-pool interning behavior; unrelated foreign identities fail before
  publication instead of relying on `TypeConstant.setContaining(...)` assertions.
- Reconstruct `AccessTypeConstant`, `ImmutableTypeConstant`, and
  `ServiceTypeConstant` from their logical child/modifier state if this slice
  includes the single-child type-wrapper wave. Shared child types keep the same
  destination-pool interning behavior; unrelated foreign child types fail before
  publication.
- Reconstruct `UnionTypeConstant`, `IntersectionTypeConstant`, and
  `DifferenceTypeConstant` from their two logical child types if this slice
  includes the relational-type wave. Shared child types keep the same
  destination-pool interning behavior; unrelated foreign child types fail before
  publication.
- Reconstruct `VirtualChildTypeConstant`, `InnerChildTypeConstant`,
  `AnonymousClassTypeConstant`, and `PropertyClassTypeConstant` from parent plus
  child name/class/property identity if this slice includes the dependant-type
  wave. Preserve the transient virtual-origin parent when present, but reject a
  foreign origin before publication.
- Reconstruct `RecursiveTypeConstant` from its typedef identity if this slice
  includes the recursive-type wave, because inheriting terminal-type adoption
  would silently lose recursive typedef behavior.
- Reconstruct `Annotation` and `AnnotatedTypeConstant` if this slice includes
  the annotation-type wave. Annotation parameter arrays must be copied at
  construction/adoption time, already-owned runtime handle params must be
  rejected, and annotated type shells must drop the derived annotation-type
  cache so the destination pool recomputes it.
- Reconstruct `TypeSequenceTypeConstant`, and fail closed for
  `PendingTypeConstant` and `UnresolvedTypeConstant`, if this slice includes the
  transient-type wave. The sequence marker is stateless; pending and unresolved
  constants are mutable compiler/name-resolution placeholders, not completed
  pool metadata.
- Reconstruct pseudo constants if this slice includes the pseudo-family wave:
  `ThisClassConstant`, `ParentClassConstant`, and `ChildClassConstant` rebuild
  logical path shells with target-owned child identities; `KeywordConstant`
  rebuilds the per-format singleton shell; `DeferredValueConstant`,
  `ExpressionConstant`, and `UnresolvedNameConstant` fail closed before
  unresolved compiler/AST placeholder state can be copied. Copy unresolved-name
  input arrays at construction.
- Reconstruct named and type-backed identity constants if this slice includes
  the identity-family wave: `ModuleConstant`, `PackageConstant`,
  `ClassConstant`, `MultiMethodConstant`, and `TypedefConstant` rebuild
  target-owned parent/name or name/version shells before publication;
  `TypedefConstant` drops resolved recursion state; `DecoratedClassConstant`
  and `PureIdentityConstant` rebuild only for target-shareable type keys; and
  `NativeRebaseConstant` fails closed because it is a runtime-only facade, not
  serialized pool metadata.
- Reject `CastTypeConstant` adoption in that wave because it is a transient
  compiler/JIT marker and cannot be assembled into a pool.
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

- Non-constant clone removal in compiler/source metadata and runtime handle
  view code. The constant-family default-clone adoption policy is removed in
  this branch; those commits can be split as their own clone-free adoption PRs.
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
- `Make condition adoption clone-free` condition-family wave
- `Make array-backed value adoption clone-free` byte-array value wave
- `Make scalar value adoption clone-free` immutable scalar value wave
- `Make composite value adoption clone-free` array/map/range value wave
- `Make parsed value adoption clone-free` literal/version/decimal-auto wave
- `Make dependant type adoption clone-free` dependant/recursive type wave
- `Make annotation type adoption clone-free` annotation/transient type wave
- `Make pseudo constant adoption clone-free` pseudo path/placeholder wave
- `Make identity constant adoption clone-free` identity path/type-backed wave

Primary source areas:

- `Constant`, `ConstantPool`, `ConstantAdoptionValidator`
- `SingletonConstant`
- `FSNodeConstant`, `FileStoreConstant`
- `TypeConstant`, `ParameterizedTypeConstant`, `SignatureConstant`,
  `TypeParameterConstant`, `DynamicFormalConstant`, `RegisterConstant`,
  `MethodBindingConstant`
- `ConditionalConstant`, `MultiCondition`, and condition leaves if bundled
- `UInt8ArrayConstant`, `FPNConstant`, and `Float128Constant` if bundled
- `Annotation`, `AnnotatedTypeConstant`, `TypeSequenceTypeConstant`,
  `PendingTypeConstant`, and `UnresolvedTypeConstant` if bundled
- `PseudoConstant`, `ThisClassConstant`, `ParentClassConstant`,
  `ChildClassConstant`, `KeywordConstant`, `DeferredValueConstant`,
  `ExpressionConstant`, and `UnresolvedNameConstant` if bundled
- `ModuleConstant`, `PackageConstant`, `ClassConstant`, `MultiMethodConstant`,
  `TypedefConstant`, `DecoratedClassConstant`, `PureIdentityConstant`, and
  `NativeRebaseConstant` if bundled
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
- Condition constants keep the same link-time predicate behavior. Adoption still
  interns the target-owned child constants through normal registration, but the
  transient `iTest` brute-force simulation slot is not copied.
- Byte-array-backed value constants keep the same logical bytes and constant-pool
  interning behavior. The defensive copy moves from `ensureByteStringConstant(...)`
  into the constructor, so that path still performs one copy; adoption adds the
  copy that shallow clone used to skip.
- Immutable scalar value constants keep the same logical values and
  constant-pool interning behavior. Adoption still creates one target-owned
  constant, just as shallow clone did, but the code path is now explicit and
  cannot accidentally carry future owner-local scalar caches.
- Composite value constants keep the same value-child interning behavior.
  Array/set/tuple factory paths still perform one container copy because the
  copy moved into `ArrayConstant`; map/entry map construction keeps the arrays it
  already generated from the input map. Type constants are only asserted for
  logical type-string preservation here because type-family ownership is split
  out deliberately.
- Parsed/delegated values keep the same logical literal/version/decimal value.
  Literal adoption intentionally drops the parsed cache, which is recomputed on
  demand; version adoption preserves the concrete subclass; decimal-auto
  registration still adopts its delegated decimal child through
  `registerConstants(...)`.
- Match-any sentinels keep the same lookup shape. For shareable types, target
  registration still interns the type key in the destination pool and
  `ensureMatchAnyConstant(...)` returns the same cached wildcard value. For
  non-shared types, this slice intentionally tightens behavior: registration now
  throws before publication in all modes instead of relying on an assertion-only
  failure under `-ea` or silent wrong-owner state without assertions.
- Terminal type leaves keep the same logical defining identity and constant-pool
  cache behavior for shared identities. Direct foreign-identity adoption now
  throws in all modes instead of depending on an assertion-only owner check.
- Single-child type wrappers keep the same logical child/modifier value and
  constant-pool cache behavior for shared child types. Direct foreign-child
  adoption now throws in all modes instead of depending on an assertion-only
  owner check.
- Storable relational type shells keep the same logical two-child value and
  constant-pool cache behavior for shared child types. Direct foreign-child
  adoption now throws in all modes. Transient cast-type adoption also throws,
  matching the existing `assemble(...)` invariant that cast markers are not pool
  storage.
- Annotation constants keep the same logical annotation class and parameter
  values. The copy now happens at construction/adoption time instead of relying
  on a shared array container; there is no added per-read clone on the legacy
  raw `getParams()` API. Annotated type adoption still interns the annotation
  class, params, and underlying type through the destination pool, while the
  derived annotation-type cache is recomputed locally.
- The type-sequence marker has no child/cache state and is reconstructed with
  the same allocation/interner shape as shallow clone. Pending and unresolved
  types already cannot be valid assembled runtime metadata, so fail-closed
  adoption removes an invalid path rather than changing valid runtime behavior.
- Pseudo path constants keep the same auto-narrowing path value. The copy hooks
  pre-register child identities in the target pool because locator adoption does
  not rewrite the shell field when recursive registration is deferred. Keyword
  constants keep the same per-pool per-format singleton behavior. Deferred,
  expression, and unresolved-name constants already represent unfinished
  compiler state, so fail-closed adoption removes an invalid path rather than
  changing valid runtime behavior.
- Named identity constants keep the same logical path/version value and
  constant-pool interning behavior. The difference is that parent identities
  are registered in the destination pool before the shell is published, so a
  copied identity cannot retain a source-owner parent through deferred recursive
  registration. `TypedefConstant` intentionally recomputes resolved recursion
  state in the destination owner. Type-backed identity constants keep the same
  logical type key for shared/adoptable type graphs, but now fail before
  publication for foreign keys. `NativeRebaseConstant` adoption now fails in
  all modes because valid code should not register that runtime-only facade in
  a pool.
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

Remove local JIT constructor escapes and preserve JIT exception boundaries

### Reviewer-Facing Problem Statement

The JIT has a different owner model from the interpreter runtime:
`Xvm`, JIT `Container`, `TypeSystem`, `TypeSystemLoader`, `ModuleLoader`,
generated Java statics, and `Ctx.Current`. Interpreter `NativeTemplates` fixes
do not prove JIT ownership safety.

The branch fixes local JIT constructor warnings whose behavior can be preserved
mechanically and fixes two JIT exception-boundary bugs whose behavior is
independent of the larger owner lifecycle. It still leaves `Xvm` startup owner
publication for a larger lifecycle PR.

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
- Preserve JIT host-boundary failure semantics:

  - generated unhandled XTC exceptions leave a non-zero
    `JitConnector.join()` result;
  - `nType` reflective dispatch rethrows invoked `nException` and `Error`
    instead of converting them to Unsupported.

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
- JIT unhandled-result wave
- JIT bridge reflective-exception wave

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
  --tests org.xvm.javajit.JitFailurePropagationTest \
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
- Preserve successful JIT execution behavior while making generated unhandled
  exceptions observable as non-zero connector results.
- Preserve bridge Unsupported fallback for reflection/access failures while
  keeping invoked language exceptions as their original `nException`.
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
- Tests prove generated JIT exception boundaries cannot return success or
  replace invoked natural exceptions with Unsupported.
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

## PR 15: Add Structured Diagnostics And Logging Discipline

### PR Title

Add structured compiler/runtime diagnostics and logging discipline

### Reviewer-Facing Problem Statement

The current tree has several partial output channels: `ErrorListener`,
`Console`, stdout/stderr prints, direct stack traces, `BLACKHOLE` speculative
diagnostics, ownership dumps, and manual Gradle stress output. They do not form
one diagnostic contract. Compiler decisions such as constant conversion can be
printed to stderr, runtime defects can be converted to user-catchable language
exceptions, and manual reproducer modules can be overwritten after the bug is
found.

This is bad even without parallelism because a host cannot reliably distinguish
"expected speculative probe failed" from "compiler/runtime defect printed and
execution continued". It blocks same-JVM direct execution, parallel containers,
incremental compilation, and LSP because diagnostics need stable owner,
request, phase, source, and document-version identity.

### Exact Scope Included

- Introduce a structured `DiagnosticEvent`/`DiagnosticContext` model or an
  equivalent minimal first slice.
- Add an `ErrorListener` bridge that preserves the existing source diagnostic
  API while exposing structured events to embedders and tests.
- Add guarded SLF4J developer trace categories for compiler/type/runtime owner
  decisions, without changing normal output or forcing disabled-path
  allocation.
- Convert one or two low-risk direct print sites to the new model as examples.
- Add a source-shape test that prevents new production
  `System.err.println(...)`, `System.out.println(...)`, or `printStackTrace()`
  calls in compiler/runtime/JIT code outside approved console/test boundaries.
- Add a stable reproducer fixture policy for bugs currently discovered through
  `manualTests/src/main/x/TestSimple.x`.

### Explicit Out Of Scope

- Rewriting all compiler diagnostics in one PR.
- Changing user-facing compiler error text.
- Turning every trace into a logged event.
- Making stress tasks mandatory in CI.
- Changing constant-folding semantics while migrating the conversion print
  sites.

### Source Areas / Branch Commits

Primary source areas:

- `javatools/src/main/java/org/xvm/asm/ErrorListener.java`
- `javatools/src/main/java/org/xvm/tool/Launcher.java`
- `javatools/src/main/java/org/xvm/tool/Compiler.java`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java` (op-assembly
  terminal boundary, already fixed in branch)
- compiler AST conversion/type-fitting paths
- runtime/JIT host boundaries that currently print stack traces
- `manualTests/build.gradle.kts`
- stable repro fixtures under `javatools/src/test/resources`

Documentation sources:

- [../logging-diagnostics-audit.md](../logging-diagnostics-audit.md)
- [../logging-strategy.md](../logging-strategy.md)
- [../exception-hygiene-audit.md](../exception-hygiene-audit.md)

### Tests And Verification Commands

```bash
./gradlew :javatools:test --tests '*Diagnostics*' \
  --configuration-cache --console=plain --warning-mode=all

./gradlew :javatools:test --tests '*Launcher*' \
  --configuration-cache --console=plain --warning-mode=all
```

Run the production print source-shape test and a focused compiler fixture test
for any migrated diagnostic site. If the PR changes Gradle logging or
dependencies, also run the relevant task with `--configuration-cache`.

The terminal failure boundaries already fixed in the branch carry their own
red-on-master proof and can be extracted as a smaller slice ahead of this PR:

```bash
./gradlew :javatools:test \
  --tests 'org.xvm.tool.CompilerCodegenFailureTest' \
  --tests 'org.xvm.asm.MethodStructureAssemblyFailureTest' \
  --configuration-cache --console=plain --warning-mode=all
```

### Semantic / Performance Equivalence Notes

- Disabled trace/debug logging must be one static logger lookup plus an
  explicit level check. It must not allocate, build strings, call `toString()`,
  compute type names, mutate MDC, or force lazy diagnostics when disabled.
- `ErrorListener` display text and severity behavior must remain compatible
  unless the PR explicitly documents a user-facing change.
- Runtime failure propagation changes belong in PR 8b; this PR should focus on
  diagnostic representation and logging discipline.
- Reproducer migration should add tests without changing runtime semantics.

### Documentation Updates Included

- Update [../logging-diagnostics-audit.md](../logging-diagnostics-audit.md)
  with every migrated print/catch site and the replacement event.
- Update [../logging-strategy.md](../logging-strategy.md) if logger categories
  or dependency shape changes.
- Update [../bad-design-decisions-reference.md](../bad-design-decisions-reference.md)
  if new output-side failure categories are discovered.

### Review Checklist / Acceptance Criteria

- New diagnostics carry owner/request/phase/source context where that context
  exists at the call site.
- New logging is disabled-cost safe and does not introduce ambient owner state.
- No production compiler/runtime failure is reported only by stdout/stderr.
- `TestSimple.x` remains a scratch tool only; final reproducers become named
  stable tests.
- Tests assert structured values, not free-form console text, wherever possible.

## PR 16: Seal The Closed Constant, AST, And Structure Hierarchies

### PR Title

Seal the closed constant, AST, and structure hierarchies

### Reviewer-Facing Problem Statement

Seven core hierarchies - the `Constant` format families, `TypeConstant`,
`IdentityConstant`, `ValueConstant`, the serialized `BinaryAST` tree, and the
`Component` structure tree - are closed in practice: nothing outside this
repository subclasses them, their subtype sets are mirrored by hand-maintained
`Format`/`NodeType` discriminator enums, and every dispatch over them is an
`instanceof` cascade or a discriminator switch. Measured against the tree:
1,549 `instanceof` occurrences, 167 discriminator switches - of which 102
throw in their default arm at runtime, 26 have no default at all, and **39
silently produce a value** when they meet a format they never heard of
(`TerminalTypeConstant.isTuple()` answers "not a tuple" for any new format;
the `ClassConstant` namespace walkers treat every unknown identity kind as
"outermost"). There are 814 hand-written `IllegalStateException` throws and 63
"shouldn't happen" comments standing in for what the compiler could enforce.
One site (`NameExpression.getMeaning()`) needed
`@SuppressWarnings("fallthrough")` purely because the hierarchy was open -
a direct collision with this build's fatal `-Xlint:fallthrough -Werror` gate.

Sealing is not syntax. It moves "forgot a subtype" and "wrong subtype" from
runtime failures and review conventions into compile errors, at every dispatch
site simultaneously, forever. Real captured javac output against the sealed
classes:

```
error: class is not allowed to extend sealed class: ConditionalConstant
    (as it is not listed in its 'permits' clause)
```

```
error: cannot inherit from final NotCondition
```

and deleting any arm of a converted switch:

```
error: the switch expression does not cover all possible input values
```

### Exact Scope Included

- Modifier-only sealing of: `ConditionalConstant` (+`MultiCondition`),
  `PseudoConstant`, `FrameDependentConstant`, `TypeInfo`, `BinaryAST`/
  `ExprAST` (52 classes), `TypeConstant` (21), `IdentityConstant` (15, zero
  hatches), `ValueConstant` (27, one hatch), `Component` (9, two hatches).
- `NodeType.instantiate()` made exhaustive: the five unimplemented node types
  (`ReturnTStmt` among them) become explicit case arms instead of an
  invisible default throw; `readExprAST` reports statement-in-expression
  wire bytes as a corrupt-stream `IOException` instead of an incidental CCE.
- Three demonstrator rewrites, behavior preserved arm-for-arm:
  `SimulatedLinkerContext.extractRequiredConditions` (a silent
  requirement-drop becomes an exhaustive switch), `ClassConstant`'s
  `getParentClass`/`getOutermost` (silent-terminator defaults become explicit
  labeled arms with compiler-checked pattern dominance), and
  `NameExpression.getMeaning` (fallthrough suppression, empty default, and
  trailing ISE deleted).
- Retirement of the constructor-escape probe fakes on
  `PropertyConstant`/`FormalTypeChildConstant`: the fatal `-Xlint:this-escape`
  gate enforces their property at compile time, and the reworked test pins
  the surviving half (constructor parent validation still fires).
- Reflection pins for every permits list and hatch:
  `SealedConstantFamiliesTest`, `SealedAstFamiliesTest`,
  `SealedStructureFamiliesTest`.

### Exact Scope Excluded

- `Op` (root in `org.xvm.asm`, 215 leaves in `org.xvm.asm.op`: the unnamed
  module's same-package rule blocks it, and the byte-indexed factories
  already fail loudly).
- The compiler `AstNode` tree, `ObjectHandle`, `ClassTemplate` (deliberately
  open), `TypeComposition` (needs a test adapter first).
- The `DefiningConstant` union retiring `TerminalTypeConstant`'s 23 format
  switches and 48 casts (needs a 150-call-site return-type change; its own
  follow-up).
- Bulk conversion of the remaining cascades: sealing makes each one
  convertible opportunistically; converting them here would bury the
  modifier-only review.

### Equivalence

Modifier-only except the three demonstrator rewrites, whose old and new arms
are documented side-by-side in `sealed-hierarchy-audit.md` (the previously
silent arms are labeled "was the silent default" in the code). Sealing is
binary-compatible for all callers; the only affected parties are subclassers,
and the repository sweep (including `lang/`, `manualTests/`, the plugin, and
the jitbridge) proves none exist outside javatools tests, which were reworked
or given documented `non-sealed` hatches.

### Verification

- `SealedConstantFamiliesTest`, `SealedAstFamiliesTest`,
  `SealedStructureFamiliesTest` pin every permits list, the final-or-sealed
  leaf discipline, and the documented hatches.
- `SealedAstFamiliesTest` additionally pins the `NodeType` factory hole set,
  the `ReturnTStmt` behavior, and the 0..31 expression-encoding window.
- Full `:javatools:test` suite and `xdk:installDist` green after every stage.
- Branch provenance: `e59d4f82d` (stage 0), `dc39387bd` (stage 1),
  `298067019` (stage 2), `cace6570a` (stage 3), `07ed937b3` (stage 4),
  `78111b85f` (payoff rewrites).

### Review Checklist / Acceptance Criteria

- Every sealed root's permits list matches the audited tree in
  `sealed-hierarchy-audit.md`; every leaf is `final`, `sealed`, or a
  documented `non-sealed` hatch with a named test that needs it.
- The demonstrator rewrites preserve behavior arm-for-arm, including the old
  implicit `IllegalStateException` cases, now explicit.
- No new `default` arms were introduced in the converted switches - the
  absence of `default` is the mechanism, not an omission.
- The serialization factories (`ConstantPool.disassemble`,
  `NodeType.instantiate`, `Op.instantiate`) remain in sync with the sealed
  trees; the factory tests prove it.

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
