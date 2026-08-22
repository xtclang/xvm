# Reentrancy And Runtime State Safety

This folder documents the runtime state-safety problem behind the native
template startup work on branch `lagergren/lazy-instance`.

The core issue is ownership. JVM `static` fields are process-global, but XVM
templates, handles, type constants, method structures, enum values,
compositions, services, frames, and constant pools are usually owned by a
container, pool, template, service, or fiber. When owner-scoped values are
stored in mutable globals or unsynchronized first-use fields, parallel
containers can observe the wrong owner, a half-initialized object, or an
impossible lifecycle state.

Start here:

- [must-fix-races.md](must-fix-races.md): the high-priority inventory of
  patterns that are actually racy or semantically broken under parallel startup.
- [fixed-in-this-branch.md](fixed-in-this-branch.md): the master-to-branch
  delta showing exactly which broken sites this PR fixes, which are must-fix,
  and how the replacement preserves cache behavior.
- [native-template-startup-safety.md](native-template-startup-safety.md): the
  detailed design for replacing mutable native-template `INSTANCE` fields,
  static metadata caches, enum raw-handle escapes, and `SingletonConstant`
  split lifecycle state.
- [state-inventory.md](state-inventory.md): the broader source inventory of
  mutable/racy/state-design smells, including must-fix and should-fix
  categories with scan commands.
- [must-audit-backlog.md](must-audit-backlog.md): repository-wide state that is
  not yet proven broken but depends on unproven owner, threading, or reset
  assumptions.
- [runtime-ownership-hardening-ledger.md](runtime-ownership-hardening-ledger.md):
  the consolidated ledger of the concrete ownership failures, branch fixes,
  remaining assertions, and recommended one-commit fix order.
- [ownership-diagnostics.md](ownership-diagnostics.md): how to dump
  container-owned runtime state during stress runs and detect owner mismatches
  or cross-container sharing.
- [constant-adoption-clone-audit.md](constant-adoption-clone-audit.md): why
  `Constant.adoptedBy(...)` exists, which clone/adoption sites were checked,
  and which shallow-copied runtime-state fields this branch hardens for
  multi-container runtime safety.
- [clone-usage-audit.md](clone-usage-audit.md): repository-wide Java
  `clone()`/`Cloneable` audit covering component, AST, constant-pool, runtime
  handle, metadata-chain, and defensive-array clone sites.
- [constant-pool-state-audit.md](constant-pool-state-audit.md): `ConstantPool`
  state inventory, ambient current-pool lookup sites, must-fix vs should-fix
  classification, and recommended guards.
- [constant-pool-hostile-state-audit.md](constant-pool-hostile-state-audit.md):
  background catalog of ConstantPool/constant adoption, clone, ambient-owner,
  mutation, and cache patterns that remain hostile to reentrant execution.
- [ambient-context-audit.md](ambient-context-audit.md): focused audit of
  `ThreadLocal`, `TransientThreadLocal`, `ScopedValue`, `ConstantPool`
  current-pool lookup, `ServiceContext`, `TypeConstant`, and JIT `Ctx.Current`
  ambient-owner mechanisms.
- [jit-implications.md](jit-implications.md): how the separate JIT runtime
  path models containers, classloaders, generated static fields, and ownership
  risks.
- [scoped-value.md](scoped-value.md): whether a single `ScopedValue` runtime
  scope can replace legacy mutable statics, and where it is only a bridge.
- [remaining-finstance-constructors.md](remaining-finstance-constructors.md):
  the removed `fInstance` constructor convention and the replacements for the
  final five semantic uses.
- [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md): classification of
  remaining `if (field == null) field = ...` sites in runtime/asm and the
  same-JVM stress verification backlog.
- [this-escape-tally.md](this-escape-tally.md): clean-build
  `javac -Xlint:this-escape` output and broad risk buckets.
- [this-escape-removal-audit.md](this-escape-removal-audit.md): which
  remaining constructor escapes should be removed immediately, which are
  mechanical cleanup, and which need owner/confinement proof first.
- [lint-parallelism-risk-audit.md](lint-parallelism-risk-audit.md): non
  `this-escape` javac lint categories that can still hide same-JVM ownership,
  cache-key, state-machine, or erased-payload bugs.
- [stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md):
  concrete failures found by the parallel manual-test runner that are adjacent
  to, but distinct from, the Java static owner-cache work.
- [plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md):
  backlog plan for extending the stress harness to run repeated `Launcher` and
  Gradle plugin direct-mode executions in one JVM, validate owner separation,
  and benchmark the speedup that safe reentrant execution should unlock.
- [plans/xvm-memory-model-hygiene.md](plans/xvm-memory-model-hygiene.md):
  separate follow-up plan for the remaining JIT `Xvm.java` lifecycle refactor,
  ConstantPool owner/mutation hazards, clone/copy owner bugs, ambient context,
  and build gates needed before XVM can be treated as reentrant by default.

The stress-discovered issue file is deliberately broader than the native
template design. It also records branch-adjacent failures found while validating
this work: the adopted `SingletonConstant` runtime-state leak behind the
parallel `TestProps` failure, the `StringBuffer` chunk invariant, the
`xRTCompiler` unmodifiable diagnostic list, the `xException` canonical
formatter lookup, and the concurrent Gradle/XTC output race that can masquerade
as runtime state corruption when two heavy manual-test builds write the same
checkout at the same time.

The distinction is intentional:

- Must fix: a parallel container or fiber can observe the wrong object,
  construction state, stale state, or a mixed lifecycle snapshot.
- Should fix soon: the current code may rely on thread confinement or
  historical startup ordering, but the design is fragile and blocks safe
  reentrancy, future parallel runtime work, and incremental/parallel compiler
  work.
- Should fix: the code is not necessarily broken today, but it hides ownership,
  weakens final-field reasoning, or teaches APIs to expose mutable state.

`Lazy` is useful, but it is not the universal answer. Use final `Lazy` for
immutable owner-derived memoization. Use `ConcurrentMap` for keyed caches.
Use `AtomicReference<State>` or a lock for lifecycle state. Use immutable
collections or defensive copies for public constants. Move runtime-owned data
to the owner that actually owns it. For native templates, the preferred
direction is a central `NativeTemplates` table owned by each `Container`, not
hundreds of public or private per-class `INSTANCE` fields.
