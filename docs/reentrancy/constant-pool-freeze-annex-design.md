# ConstantPool freeze-on-publish + runtime-annex — design/plan

**Status: DESIGN / plan (no code).** This is the real structural fix that
`markRuntimePublished` retrofits (see `constant-pool-state-audit.md` "Fundamental
Design Flaw" and `toolconnector-api-proposal.md` §4.4). It closes TWO issues with
one architecture:

- **#10 — concurrent reads of shared upstream/child pools** (multi-container / LSP /
  `ToolConnector`): today only the app pool is `markRuntimePublished`; extending the
  marker to more pools is more retrofit. An immutable base needs no marker at all.
- **#2 — the unbounded-growth memory leak**: the shared pool interns per-run types +
  `TypeInfo` and never evicts, so a long-running host climbs without limit. **Measured
  (`SharedPoolGrowthCharacterizationTest`):** after a one-time first-run warmup (~2000
  constants), each DISTINCT-typed run adds **~1-2 constants permanently** to the shared
  native pool that never evict — small per run, but **monotone and unbounded** (a
  very-long-lived host seeing many distinct types climbs without bound). A scoped,
  evictable annex releases them with their scope. So this is a *slow* leak, not a fast
  OOM — real, but its priority is "eventual, for a truly long-lived diverse-workload
  host," not "imminent."

They are the same fix because both are consequences of one broken property: **the
runtime keeps interning into a process-shared, never-frozen, never-evicted pool.**

## 1. What exists today (the retrofit is a proto-base+annex)

The branch already built most of the read machinery — it just isn't a *true* base
+ evictable annex yet:

- `markRuntimePublished` (`ConstantPool.java:416`) snapshots `f_listConst` into a
  volatile array **`m_aconstMirror`** (`:424`) — a proto **frozen base**.
- `readConstant(i)` (`:105-108`) reads the mirror for `i < mirrorCount`, else
  `getConstantWhileRegistering(i)` — a proto **annex** read path.
- Late runtime interning (during a thread-scoped `openRuntimeSynthesisWindow`)
  `appendToRuntimeMirror` (`:436`) — a proto **annex write**.

**Where it falls short of the target:**
1. The "base" is not frozen: late registrations append to BOTH `f_listConst` (`:261`)
   AND the mirror, so the underlying pool keeps growing — the base is a snapshot of a
   still-mutating list, not an immutable value.
2. The annex is not **owned or evictable**: late constants are appended to the
   process-shared pool's mirror forever → the leak (#2).
3. There is no type-level distinction: a published (frozen) pool and a mutable builder
   pool are the same Java type, so "register after publish" is a runtime guard, not a
   compile error.

So this design *completes* the existing mechanism rather than replacing it.

## 2. Target architecture

Two representations with a hard phase boundary, and a scoped overlay:

```
  compile / link phase        FREEZE          runtime phase
  ------------------          ------          -------------
  MutablePoolBuilder   --->   FrozenPool  +   RuntimeAnnex (per owner scope)
  (single owner,              (immutable,     (mutable, OWNED, EVICTABLE;
   register freely)           shared, base)    overlays the base by index)
```

- **FrozenPool (base).** Produced by freezing the builder at publication. Immutable:
  the constant array, lookup maps, and NakedRef/derived state never change again.
  **Concurrent reads need no synchronization and no marker** — immutability is the
  guarantee. Shared safely by every container/fiber.
- **RuntimeAnnex (overlay).** Runtime type-calculus that must intern a new constant
  (a parameterized type, function type, etc. not present in the base) writes into an
  annex **owned by a scope** (a `Container`, or a run, or a `TypeSystem`). The annex
  is append-only, indexed **above** the base (`base.size() + k`), and dedups
  base-then-annex. It is **released when its owner scope is disposed**.
- **Read path.** `getConstant(i)` = `i < base.size ? base[i] : annex[i - base.size]`.
  Base reads are lock-free (immutable); annex reads are within one owner scope (light
  synchronization at most, never process-global contention).
- **Write path.** Pre-freeze: into the builder (the compiler/linker phase, single
  owner — no concurrency). Post-freeze (runtime interning): into the current scope's
  annex, never into the base.

## 3. How it fixes #10 and #2

- **#10 (concurrent shared-pool reads):** the base is immutable → any number of
  containers read it concurrently with zero synchronization; the `markRuntimePublished`
  marker/mirror become unnecessary for the base. Each container's annex is its own →
  no cross-container race. Upstream/library pools are frozen once at boot and shared
  read-only forever.
- **#2 (leak):** per-run/per-container interned types live in that scope's annex;
  disposing the scope drops the annex → the interned constants and their `TypeInfo`
  are released. The base stays bounded (it never grows post-freeze). A long-running
  host no longer accumulates dead modules' constants.

## 4. The hard problem: `TypeInfo` ⇄ annex coupling (the crux)

`TypeInfo` is derived and cached ON `TypeConstant`s (`m_typeinfo`). A `TypeInfo`
built during a run can reference annex-interned types (e.g. `TypeInfo` for
`Array<UserClass>` where `UserClass` is annex). So caching interacts with annex
lifetime:

- **Rule: a `TypeInfo` lives in the scope of the most-derived pool of any type it
  references.** If every referenced type is in the base, the `TypeInfo` is permanent
  (cache on the base constant). If any referenced type is annex, the `TypeInfo` is
  **annex-scoped** and must be dropped when that annex is evicted.
- **Eviction hook already exists:** `invalidateTypeInfos(...)` (`ConstantPool.java`)
  is the mechanism; annex disposal invalidates every `TypeInfo` that referenced an
  annex type. The cache key/owner must record annex-membership so eviction is precise
  (don't invalidate base-only `TypeInfo`s).
- **Simplest correct v1:** cache annex-referencing `TypeInfo` in the annex itself
  (keyed by the annex TypeConstant), never on the base constant. Then evicting the
  annex drops those `TypeInfo`s wholesale, and the base constant's permanent
  `TypeInfo` cache is untouched. Refine later if base constants need per-scope views.

This coupling is the single biggest reason this is a design, not a hunk.

## 5. Other hard problems / open questions

- **Cross-pool index identity.** `Constant.m_iPos`/`getConstantPool()` (`Constant.java:405,489`)
  assume one pool with one index space. The annex must present a unified index over
  base+annex without renumbering the base. Decide: annex indices are `base.size + k`,
  and `getConstantPool()` for an annex constant returns the annex (which delegates to
  the base for reads) — or a `ScopedPool` view wrapping (base, annex).
- **Dedup across base+annex.** `register()`/`ensureConstantLookup` must check the base
  lookup first (hit → return base constant, no annex growth), then the annex. Interning
  a type already in the base must NOT copy it into the annex (or the "leak" returns via
  duplication).
- **Adoption.** Constant adoption across pools (`constant-adoption-clone-audit.md`)
  must adopt into the correct layer — a base→base adoption stays base; a runtime
  adoption of a base constant into a scope goes to that scope's annex.
- **Serialization boundary.** The base serializes to `.xtc`; the annex is
  runtime-only and MUST never serialize (it is per-run interning). `assemble`/`writeTo`
  operate on the base only.
- **Freeze trigger & idempotence.** `markRuntimePublished` becomes `freeze()`: it
  produces the immutable base and installs an empty annex for the scope. Re-freeze is
  a no-op. The destructive-phase guards (`optimize`/`replaceModule`/disassembly) become
  "cannot run on a frozen base" by TYPE, not by runtime assert.
- **Annex concurrency.** One container's annex can still be touched by multiple fibers
  → it needs the current windowed append discipline (append element-then-count-release),
  but scoped to one owner, not the process — far less contention than today.
- **Compiler phase unaffected.** Pre-freeze building is single-owner and already works;
  the `MutablePoolBuilder` is essentially today's `ConstantPool` minus the runtime
  read paths.

## 6. Type-system support (make illegal states unrepresentable)

- Split the Java types: **`MutablePoolBuilder`** (has `register(...)`, no runtime read
  mirror) vs **`FrozenPool`** (read-only, `getConstant` only) vs a **`ScopedPool`**
  view (FrozenPool base + one RuntimeAnnex). "Register after publish" then does not
  compile — the current runtime `assertMutableBeforeRuntimePublished` guard disappears
  because the call site cannot exist.
- Pursue the sealed constant/op hierarchies (`sealed-hierarchy-audit.md`,
  `generics-api-audit.md`) so constant-kind handling is exhaustive rather than
  `instanceof` + runtime cast.

## 7. Phased implementation plan (each phase independently gated)

- **Phase A — freeze the base for real.** Make `markRuntimePublished` produce a truly
  immutable base (freeze the constant array + lookup maps; forbid ALL post-freeze base
  mutation by construction, not just registration). Reads already use the mirror; this
  makes the mirror the authoritative immutable base and stops the base list growing.
  *Gate:* existing pool/TypeInfo tests + the hammer/parallel stress; no behavior change
  for single-run.
- **Phase B — introduce the scope-owned annex.** Route post-freeze interning into a
  per-scope `RuntimeAnnex` instead of appending to the shared mirror. Unified read over
  base+annex; dedup base-first. *Gate:* two-container test proving each container's
  interning is isolated; the existing synthesis-window stress.
- **Phase C — evict the annex (closes #2).** Tie the annex to its owner scope
  (`Container`/run); dispose drops it; annex-referencing `TypeInfo` invalidated via
  `invalidateTypeInfos`. *Gate:* a leak test — run N distinct modules over one host,
  assert base size and heap are bounded (the #2 repro becomes a regression test).
- **Phase D — type-system split.** Introduce `MutablePoolBuilder`/`FrozenPool`/
  `ScopedPool`; delete the runtime `assertMutable...` guards as they become
  unrepresentable; pursue sealed hierarchies. *Gate:* compile-time — the illegal calls
  no longer exist; full suite green.

Phases A–C deliver the correctness/leak fix; Phase D is the type-system hardening that
prevents regression. A and B can land without D.

## 8. Relationship to existing branch work

This consolidates, not discards: the read mirror (Phase-A base), the thread-scoped
synthesis windows (Phase-B annex write discipline), `invalidateTypeInfos` (Phase-C
eviction hook), the frozen constant families A/B (the same freeze-on-publish principle
applied to arrays), and the publication marker (becomes the `freeze()` trigger). The
`markRuntimePublished` retrofit was the bridge; this is the destination it was bridging
to. It also removes the reason to ever extend the marker to more pools (#10) — a frozen
base needs none.
