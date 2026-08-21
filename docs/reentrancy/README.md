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
- [ownership-diagnostics.md](ownership-diagnostics.md): how to dump
  container-owned runtime state during stress runs and detect owner mismatches
  or cross-container sharing.
- [jit-implications.md](jit-implications.md): how the separate JIT runtime
  path models containers, classloaders, generated static fields, and ownership
  risks.
- [scoped-value.md](scoped-value.md): whether a single `ScopedValue` runtime
  scope can replace legacy mutable statics, and where it is only a bridge.
- [stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md):
  concrete failures found by the parallel manual-test runner that are adjacent
  to, but distinct from, the Java static owner-cache work.

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
