# Master Enhancement Submission Drafts

Companion to [master-issue-submissions.md](master-issue-submissions.md). That
file lists **minimal master bugs** — concrete, individually-filable defects whose
fix is a few lines. **This** file lists the **conceptually-clear, horizontally-
wide enhancements**: design improvements from `lagergren/lazy-instance` that make
master safer and more provable, but that necessarily touch many files at once
(sometimes unavoidably). Each entry states what it is, the design flaw it removes,
how wide it is, and — the point of this document — **exactly what is required to
reimplement it on master, reusing the master subsystems we did NOT refactor away
in this branch.**

This is a planning document only. Nothing here is filed; nothing is pushed. Each
enhancement lands as its own reviewed slice (or sequence of slices), independent
of adopting the whole branch.

Baseline for source references: `origin/master` `82683bcd2` (advanced from
`61e555a68` on 2026-08-27 by PR #377 (LSP/IntelliJ/VS Code support) and PR #539
(`cc183520c`, "Fix concrete utility this-escape hazards" — which is where the
owner-scoped `Lazy.Bound` helper landed; see E1). Re-verify the commit ranges
below against the branch before porting; they are the source material, not
patches. The "reuses master subsystem" claims were re-checked against `82683bcd2`.

## Reviewer Framing

The common thread is the same one the bug list names, escalated from "point
defect" to "design property": master repeatedly defers an invariant Java could
enforce to **mutable shared state, raw arrays, `ObjectHandle`/metadata casts,
ambient thread-local ownership, silent-default dispatch, or process-static
singletons**. Each enhancement below picks one such deferral and makes the
invalid state unrepresentable using a mechanism master's own Java version already
provides — `sealed` hierarchies + pattern switches, `Lazy.Bound` owner-scoped
caches, explicit pool parameters, copy constructors instead of `Cloneable`,
frozen shared metadata, nested containers. None of them is a "house style"
request; each removes a class of latent corruption, cross-container leak, or
undiagnosable crash.

Two of these enhancements are *force multipliers for finding master bugs*:
sealing + exhaustive dispatch (E2) and explicit-pool threading (E3) make the
compiler surface missing cases and ownership gaps that the cast-to-`Object`,
instanceof-tree, silent-default style keeps hidden. The incompleteness bugs they
surface graduate into individual rows on the bug list.

## Enhancement Index

| # | Enhancement | Width | Port difficulty | Reuses (un-refactored master subsystem) | Design doc |
| --- | --- | --- | --- | --- | --- |
| E1 | Owner-local instance caches (retire static `INSTANCE`/`fInstance`) | wide, mechanical | Medium | master's `Lazy.Bound`/`ofBound` (PR #539), `Container` as owner key | [jit-xvm-owner-refactor.md](jit-xvm-owner-refactor.md) |
| E2 | Seal the type/component/AST hierarchies + exhaustive dispatch | very wide, compiler-guided | Medium-High | Java `sealed`+pattern switch; the existing class trees | (this file + bug list for the surfaced cases) |
| E3 | ConstantPool: explicit-pool threading + freeze-on-publish + annex | wide (part A) / focused (part B) | High | The existing pool, read-mirror/publication-marker retrofit | [constant-pool-freeze-annex-design.md](constant-pool-freeze-annex-design.md), [transactional-constant-registration-plan.md](transactional-constant-registration-plan.md), [constant-pool-state-audit.md](constant-pool-state-audit.md) |
| E4 | Embedding API + container-model enforcement (one root, nested children) | focused | Medium | `NativeContainer`/`Container`/`NestedContainer`, `ServiceContext.callLater`, `ModuleRepository` | [instructions-port-run-compile-api-to-master.md](../instructions-port-run-compile-api-to-master.md), [toolconnector-api-proposal.md](../toolconnector-api-proposal.md) |
| E5 | Side-effect-free `toString()`/display path | wide, mostly transitive | Low-Medium | Existing `toString(boolean)` arity where present | [side-effect-free-tostring.md](side-effect-free-tostring.md), [tostring-purity-enhancement-scope.md](tostring-purity-enhancement-scope.md) |
| E6 | AST clone eradication (`Cloneable` → copy constructors) | wide, mechanical | Medium | The AST node classes; parity-checked copy bridge | [clone-free-adoption-plan.md](clone-free-adoption-plan.md) |
| E7 | Frozen shared metadata + refuse unsafe view cloning | wide | Medium-High | `TypeConstant`/`SignatureConstant` storage, handle view machinery | [xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md) |
| E8 | Diagnostics: named exceptions, world X-ray, unified logging/JFR | additive | Low | Existing exception sites; JFR | [unified-logging-jfr-telemetry.md](unified-logging-jfr-telemetry.md) |

Recommended landing order: **E5 → E1 → E4 → E2 → E3 → E6/E7 → E8.** Rationale in
each entry's "Ordering." E5 first because it is small, self-contained, and makes
debugging every later slice safer; E3 last of the structural set because the
freeze-on-publish half depends on the ambient-pool elimination half and benefits
from E2's sealing.

---

## E1 — Owner-local instance caches (retire the static `INSTANCE`/`fInstance` role)

**What it is.** Templates cached their singleton behavior in **process-static**
fields (`INSTANCE`, `fInstance` flags). One static instance per template is fine
in a single-container process; with nested containers and runtime reuse it is a
process-shared cell that leaks across containers and races on first use. This
branch removed those statics and routed per-owner state through **owner-local
lazy caches** keyed by the owning container.

**Design flaw removed.** Static singleton state in a component that is supposed to
be per-container. The compiler cannot see the owner, so cross-container bleed and
first-use races appear only at runtime, intermittently — the branch's namesake
problem.

**Width.** Wide but mechanical: leaf native templates, array leaf templates, and
the `ClassTemplate` role fields. Every removed static has a small, local
replacement.

**Master port spec.**
1. Reuse master's owner-scoped lazy helper DIRECTLY: master merged it after code
   review as `Lazy.Bound` / `Lazy.ofBound` (PR #539, commit `cc183520c`, on master
   `82683bcd2`) — a plain `value` field VarHandle-published, not this branch's
   original `Lazy.Owner` / `Lazy.ofOwner` (which used an `AtomicReference`). This
   branch has ALREADY adopted master's reviewed `Lazy.Bound` verbatim (commit
   `c66230c28`), so E1 no longer adds any `Lazy` machinery — it just uses the
   helper master already ships. (Historical: the branch's own helper landed in
   `b834d3353`, `73398ef28`; superseded by master's `Bound`.)
2. Delete the static `INSTANCE`/`fInstance` fields and the dead constructor flags
   (branch commits `93c36fbf6`, `aade77699`, `80999b393`, `dc95e08e6`,
   `5f5117e07`).
3. Replace each former static read with an owner-local `Lazy` cell keyed by the
   container that owns the template (commits `0fb5d2cf0`, `0aa9a86cd`,
   `08553d149`, `9d99789d3`, `dd94d6425`).
4. The owner key is the `Container` — a master concept we did not change.

**Ordering / dependencies.** Cleaner after E4 (a well-defined container owner),
but does not require it: the container already exists on master; this only changes
where the cache hangs. Independent of E2/E3.

**Gate.** The same-JVM sequential/nested stress harness
([same-jvm-launcher-stress.md](same-jvm-launcher-stress.md)) — the harness that
made the cross-container bleed reproducible.

---

## E2 — Seal the hierarchies and make dispatch exhaustive

**What it is.** Master models its core trees (`TypeConstant`, `IdentityConstant`
/`ValueConstant`, `Component`, `BinaryAST`, the composition subtree, the parser
AST, `MethodBody`'s target payload, `RegisterInfo`) as open class hierarchies and
dispatches over them with instanceof trees and `switch` statements that carry a
silent `default`. This branch made every such tree `sealed ... permits` and
converted the dispatch sites to **exhaustive pattern switches**, then gated switch
fallthrough as a build error.

**Design flaw removed.** "Python in Java": promoting every type distinction to a
runtime cast + instanceof/`default` means the compiler cannot prove a dispatch is
complete. A newly-added subtype, or a case the author forgot, compiles cleanly and
fails (or silently mis-handles) at runtime. **Sealing + exhaustive switches turn
every such gap into a compile error** — and turning them on **surfaces the
incompleteness bugs that were already there**. That is the high-value part: the
missing cases the compiler now flags become concrete fixes (several graduated onto
the bug list).

**Width.** Very wide — every subtype declaration gains a `permits`, every dispatch
site becomes a pattern switch. But it is compiler-guided and mechanical: you
cannot miss a site, because the build fails until each is exhaustive.

**Master port spec.**
1. Confirm master's Java level supports `sealed` + pattern switch (JEP 409/441).
   It does; no toolchain change.
2. Port in the branch's wave order, one tree per slice, each independently
   reviewable: `TypeConstant` (`298067019`), `IdentityConstant`/`ValueConstant`
   (`cace6570a`), `Component` (`07ed937b3`), `BinaryAST` + close the `NodeType`
   factory (`dc39387bd`), condition/pseudo/frame-dependent/`TypeInfo` families
   (`e59d4f82d`), the composition subtree (`6ba9dc8a2`), the parser AST
   (`2ac9c107c`), `MethodBody` sealed union (`63bf6713a`), `RegisterInfo` +
   exhaustive JIT null-dispatch (`b9b0c48cf`).
3. Convert the dispatch sites in lockstep: `78111b85f`, `4ce7e6942` (planCodeGen),
   `10e246135` (assert-guarded cascades → checked pattern switches), `10ba2869d`
   (relation-calculus format switches), `ab5788584` (last non-sealed hatches).
4. Land the guard last: gate switch fallthrough as a build error (`1b19e4d9f`).
   Until then it stays a warning so slices can land incrementally.
5. **Each missing case the compiler flags is a bug** — file it on the bug list
   with its own red proof rather than papering it with a `default`.

**Reuses.** Only master's own class trees + the JDK. No branch-only subsystem.

**Ordering.** After E5 (debugging is safer), before/with E3 (sealed
`TypeConstant`/pool trees make the pool work cleaner). Independent of E1/E6.

**Gate.** Full build with fallthrough-as-error is itself the gate; add the sealed
end-state census (`cd432c873`, `1e1bacaaa`) as the review artifact.

---

## E3 — ConstantPool: explicit-pool threading, then freeze-on-publish + annex

The single most brittle structure on master (see
[constant-pool-state-audit.md](constant-pool-state-audit.md)). Two separable
enhancements; land **part A first**.

**Part A — eliminate the ambient "current pool".** Master resolves constants
against a thread-local "current pool" (`ConstantPool.getCurrentPool()` ambient
lookup). Under nested containers / concurrent compile the ambient pool is the
wrong pool or a racing one. The branch replaced ambient lookups with **explicit
pool parameters** threaded from the receiver/owner.

- *Design flaw:* ambient ownership — the pool a resolution uses is a hidden
  thread-local, not a parameter, so the compiler cannot see which pool is meant.
- *Master port:* thread an explicit pool through the resolution APIs
  (`e856d85ce` type substitutability, `d58ebfea0` folded ranges, `5d5773979`
  function compatibility, `5fce7b9ae` nested identity), move diagnostics/metadata
  onto file/metadata owners (`c93b5ad61`, `4c6521dd9`), then narrow, deprecate,
  restrict, and finally delete the ambient getter (`9f5773910` → `2716435f1` →
  `be0270e0d` → `84fa61534`) and remove the ambient-pool bridge (`3e4305fe2`).
  Reuses the existing pool; changes only how callers reach it. Wide but
  mechanical, and each step compiles independently.

**Part B — freeze-on-publish + scope-owned annex.** Once a pool is published to
the runtime it must stop mutating; today it keeps a read-mirror + publication
marker as a *retrofit*. The branch's design: a `FrozenPool` base with lock-free
reads after publish, plus a scope-owned, evictable `RuntimeAnnex` for the small
amount of runtime-synthesized constants. Closes both the concurrent shared-pool
read hazard and the unbounded shared-pool leak.

- *Design flaw:* a mutable, process-shared, untyped `ArrayList<Constant>` read by
  many fibers with a bolted-on mirror.
- *Master port:* master has **no** read-mirror/publication-marker retrofit —
  `markRuntimePublished`/`m_aconstMirror`/`isRuntimePublished` are branch-only
  (verified: absent from master's `ConstantPool.java`; added in this branch, ledger
  row 58, and consistent with row 20's "master has no marker"). Port that retrofit
  first (or build the freeze design directly on the raw pool) rather than assuming
  it exists on master — the design doc's Phases A–D. Reuse the branch's regression
  gates:
  `TypeComparisonCorpusTest` (`64922284e`) and the shared-pool growth
  characterization test (`178988f7d`, which *pins* the leak and flips to
  assert-bounded at Phase C). Also port the "safely publish" hardening family
  (`1f1b5de6e`, `33aa1aa06`, `eaf3214d6`, `8d9474c91`, `8ba3e0184`, `566a69464`,
  `8cd0d92ac`, `9c5e351f3`, `4740995de`).

**Ordering.** Part A is independent and lower-risk — land it first. Part B depends
on Part A (a frozen pool with no ambient escape hatch) and reads cleaner after E2
seals `TypeConstant`. This is the last of the structural set.

**Gate.** Type-comparison corpus + shared-pool-growth test + the same-JVM stress
harness. Do NOT trust unit-green alone: `xdk:installDist` is the real
type-resolution gate.

---

## E4 — Embedding API + container-model enforcement

**What it is.** Enforce **one root container per runtime; everything else is a
nested child**; expose the run-completion `CompletableFuture`; let a Java host
create and run nested containers; and provide `XtcEngine` for first-class
in-memory compile + run. This is the substrate the LSP/tooling `ToolConnector`
wants (a long-running host app in container 0 spawning throwaway nested children).

**Design flaw removed.** Master conflates "the runtime" with "the one container,"
and swallows the run-completion signal, so issue 543's spurious crashes and the
inability to run repeated/nested workloads follow directly.

**Width.** Focused — a handful of runtime classes plus the new API surface.

**Master port spec.**
1. Reuse master's `NativeContainer`/`Container`/`NestedContainer` and
   `ServiceContext.callLater` unchanged — do not refactor them.
2. Expose the run-completion future (`03555b779`, issue 543 1a/1b).
3. Add the Java host → nested container create+run path (`f5d3e45eb`, the
   linchpin) via `NestedContainer.createForHost`.
4. Enforce single-root: one root per runtime, everything else nested
   (`ac428aeca`); remove container-construction owner/`this` escapes (`2485d9bac`,
   `a31f37ebf`); synchronize the container registry lookup (`3d1647463`).
5. Add `XtcEngine` as a new class (`86ce6a23f`) with `compile(SourceUnit...)` +
   assemble-round-trip + nested run (`a87ad44a1`); reuse master's
   `ModuleRepository` for the compiled-state sink (no disk special-casing).
6. Guard `HandleConstant` against raw cross-container serving (`632cac927`).

**Ordering.** Independent; land after E5. Gives E1 a clean owner.

**Gate.** `XtcEngineTest` (in-memory compile + nested run) + the sequential/nested
stress harness. Full runbook:
[instructions-port-run-compile-api-to-master.md](../instructions-port-run-compile-api-to-master.md).

---

## E5 — Side-effect-free `toString()` / display path

**What it is.** Rendering a value for a debugger (implicit no-arg `toString()`)
must not mutate the world. Master's display methods force lazy caches, intern
constants, compute+cache method chains, and read ambient fiber/frame state — so
merely inspecting a value in the Variables view changes it. The fix is a pure/
forced split: the implicit renderer shows already-computed state only; the full
dump moves to an explicit method Java never calls implicitly.

**Status.** Landed on this branch so far: the two ASM-constants leaves
(`TypeInfoReal.toString`, `MethodInfo.toString`, commit `8382e0268`, scope in
[tostring-purity-enhancement-scope.md](tostring-purity-enhancement-scope.md); the
`TypeInfo` case reused master's *pre-existing* `toString(boolean fRuntime)` arity —
no-arg = pure header, boolean overload = full dump), and the two AMBIENT op-display
roots (`Argument.toIdString`, `OpVar.getName`, commit `a9e7d58c0` — pure `const:#n`
/`name:#n` markers, no ambient fiber read, plus explicit `Frame`-parameterized
forced overloads). **Still open:** the `getValueString` type leaves and the
`ObjectHandle`/`ClassComposition` handle roots — the `getValueString` split changes
function-type output (`function R(P)` → structural `Function<…>`) in error messages
and logging, so it needs a `Type.dump()`-style production-use audit before landing.

**Master port spec.** Follow the 7-slice migration in
[side-effect-free-tostring.md](side-effect-free-tostring.md). Only ~6 root sites
matter; 80+ flagged rows go pure transitively once the roots are fixed:
`Argument.toIdString` + `OpVar.getName` (clear all 33 op AMBIENT rows via a
`Frame`-parameterized forced overload), `ParameterizedTypeConstant`/
`TerminalTypeConstant.getValueString` (the type leaves), `ObjectHandle.toString` +
`ClassComposition.toString` (the handle base). Classes without a spare boolean
overload get an explicit `describeForced()`/`dump()` method (master already has a
`dump()` plane on `XvmStructure`). Gate with the existing unit suites +
`installDist`; land `DisplayPurityTest` with the banned-callee ratchet.

**Ordering.** First. Small, self-contained, makes every later slice safer to
debug.

---

## E6 — AST clone eradication (`Cloneable` → copy constructors)

**What it is.** Master's AST (and several structures) implement `Cloneable` with
`clone()`, and constant "adoption" falls back to cloning. `clone()` is the
notorious anti-pattern — no constructor invariants, shallow by default, silent
mis-ownership. This branch replaced it with **explicit copy constructors** and a
`deepCopy` walk, validated against a parity-checked copy bridge before deleting
`Cloneable` entirely, and made constant adoption clone-free.

**Design flaw removed.** `clone()` bypasses construction, so a cloned AST/constant
can keep a hidden reference to the wrong owner/outer component — a cross-container
aliasing bug the compiler cannot see.

**Width.** Wide (every AST node family + adoption paths) but mechanical and
parity-checked.

**Master port spec.**
1. Land the parity-checked copy bridge + `deepCopy` walk first (`1ea9c0632`), so
   each subsequent slice is proven byte-parity against the old `clone()`.
2. Add copy constructors family by family: Statement (`1608848a4`), Expression
   (`ac339e9d4`), type expressions/`Parameter`/`VersionOverride`/`EvalStatement`
   (`d360c0033`).
3. Make constant adoption clone-free per constant family (the `*-clone-free`
   commit series, plan in
   [clone-free-adoption-plan.md](clone-free-adoption-plan.md)).
4. Retire `Cloneable` from the structure family (`0af827c72`, `25371b397`) and
   finally delete `Cloneable`/`clone()`/the bridge (`a54565af3`).

**Reuses.** The AST node classes themselves — only their copy mechanism changes.

**Ordering.** Independent; any time after E5.

---

## E7 — Frozen shared metadata + refuse unsafe view cloning

**What it is.** Shared type metadata (parameter arrays on
`ParameterizedTypeConstant`, type storage on `SignatureConstant`) was mutable and
shared; handle "views" could be cloned in ways that alias live-lifecycle state
across owners. This branch introduced `FrozenArray` (a shared, immutable stage-3
metadata representation), froze the parameter/signature storage onto it, shared
freeze/lifecycle state across views, and made view cloning of mutable/live handles
**default-deny**.

**Design flaw removed.** Raw mutable arrays as shared metadata + permissive view
cloning = cross-container aliasing and mid-race mutation of interned metadata.

**Width.** Wide across the constants + handle-view surface.

**Master port spec.**
1. Add `FrozenArray` (`cb91df6d1`) — a new leaf type, no master refactor needed.
2. Freeze `SignatureConstant` (`35a55b81f`) then `ParameterizedTypeConstant`
   (`87f751125`) storage onto it; hoist the shared freeze cell (`a3e0a4c5e`,
   `d2165e4f8`).
3. Make view cloning of mutable arrays/tuples/functions/refs default-deny
   (`f4df60ed1`, `029ff4138`, `0bbfc98c4`, `7ce5662d1`, `db4ae7900`).
4. Also land the smaller **read-only getter views** here (encapsulate leaky
   getters that hand out live collections): `e36efeea0`, `e78d7100b`,
   `01b5123ea` (`ClassStructure.getTypeParams` immutable `Map` view — note the
   `ListMap`-is-load-bearing caveat), `0d0a7ddcc`.

**Reuses.** The existing constant/handle classes; only their storage
representation and clone policy change. Backlog:
[xvm-memory-model-hygiene.md](xvm-memory-model-hygiene.md).

**Ordering.** After E3 Part A (explicit pools) so freezing does not fight ambient
resolution. Overlaps E6 (both are aliasing hygiene).

---

## E8 — Diagnostics: named exceptions, world X-ray, unified logging/JFR

**What it is.** Additive, non-behavioral improvements to observability: give every
message-less `UnsupportedOperationException` in the runtime a real message
(`dc3e0d90d`); the foreign-reference / "world X-ray" detector (`4f72cbb3f`) that
finds cross-container handle escapes; and a unified logging + JFR telemetry plane
([unified-logging-jfr-telemetry.md](unified-logging-jfr-telemetry.md)).

**Design flaw removed.** Failures today are undiagnosable (blank exceptions,
print-and-continue, no structured trace).

**Width.** Additive — no behavior change, so low risk.

**Master port spec.** Each is independent and can land alone. The named-exception
pass is pure mechanical text. The world X-ray detector is a new diagnostic class
that reuses existing container/handle traversal. Unified logging/JFR is a new
plane layered over existing log sites — no refactor of the sites themselves.

**Ordering.** Any time; the world X-ray detector is most useful landed early
(before E1/E3/E4) because it makes cross-container escapes visible while porting
the structural enhancements.

---

## Notes on portability

- Every entry reuses a master subsystem rather than importing this branch's
  refactor of it: master's own `Lazy.Bound` (E1 — landed on master by PR #539), the
  class trees (E2), the pool itself
  (E3; its read-mirror/publication retrofit is branch-only and ports with the
  slice), the container classes (E4), the `toString(boolean)`
  arity / `dump()` plane (E5), the AST nodes (E6), the constant/handle classes
  (E7). None requires adopting the whole branch.
- Where an enhancement surfaces a concrete defect (E2's missing cases, E3's
  ambient-pool races, E4's issue-543 crashes), that defect graduates to
  [master-issue-submissions.md](master-issue-submissions.md) with its own red
  proof; this file tracks the design change, that file tracks the bug.
- Re-verify every commit hash against the branch before porting; these are source
  references, not prepared patches.
