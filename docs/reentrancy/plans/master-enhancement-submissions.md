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

> **⚠️ Branch commit hashes below are PRE-REBASE and no longer resolve.** On 2026-08-28 this branch
> was rebased onto `origin/master` `82683bcd2`, which rewrote all 297 commits. The short hashes are
> kept as stable *identifiers* (they remain reachable via the `backup/pre-rebase-master` tag), but
> the **authoritative** reference is the commit SUBJECT, which survives a rebase. Every cited hash is
> mapped to its subject in the [appendix table](#appendix-commit-hash-resolution-table) at the end of
> this document; resolve one with
> `git log --oneline master..HEAD --fixed-strings --grep '<subject>'`.
> Hashes of MASTER commits (`82683bcd2`, `61e555a68`, `cc183520c`, `145f12f51`) are unaffected and
> still resolve normally.

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
| E9 | Constructor `this`-escape elimination + fatal lint | wide, local fixes | Low-Medium | master's own PR #539 precedent; javac lint | [lint-parallelism-risk-audit.md](../lint-parallelism-risk-audit.md) |
| E10 | Switch-fallthrough gate + arrow-form migration | small | Low | javac lint | [lint-parallelism-risk-audit.md](../lint-parallelism-risk-audit.md) |
| E11 | Retire stringly/cast-based dispatch (typed payloads, generics, nullness) | wide | Medium | the existing class trees; jetbrains annotations already on the classpath | [generics-api-audit.md](../generics-api-audit.md), [nullness-annotation-audit.md](../nullness-annotation-audit.md) |
| E12 | The gates that make the rest verifiable (test infrastructure) | additive | Low | JUnit; the built XDK | (this file) |

Recommended landing order: **E12 → E9/E10 → E5 → E1 → E4 → E2/E11 → E3 → E6/E7 → E8.**

E12 (the gates) first because it is purely additive and makes every later slice
judgeable by whether it keeps them green. E9/E10 next: both are small, and a lint that
is fatal from the start prevents the regressions the later, wider slices could
otherwise introduce. Then E5, which is self-contained and makes debugging everything
after it safer. E2 and E11 are the two halves of the same idea (seal the hierarchies;
give the payloads types) and are best landed adjacent. E3 comes last of the structural
set because its freeze-on-publish half depends on the ambient-pool elimination half and
benefits from E2's sealing.

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

**The two headline trees are the ConstantPool's constant hierarchy and the AST nodes.**

- **ConstantPool constants** — `TypeConstant` and its ~20 subtypes, plus
  `IdentityConstant`/`ValueConstant` and the condition/pseudo/frame-dependent families. This is the
  structure everything else in the compiler and runtime dispatches over, and the one the audit calls
  the most brittle on master (see [constant-pool-state-audit.md](../constant-pool-state-audit.md)).
- **AST nodes** — the `BinaryAST` tree (plus closing the `NodeType` factory) and the parser AST.

Also covered: `Component`, the composition subtree, `MethodBody`'s target payload, `RegisterInfo`.

**What it is.** Master models all of those as OPEN class hierarchies and dispatches over them with
instanceof trees and `switch` statements carrying a silent `default`. This branch made every such
tree `sealed ... permits` and converted the dispatch sites to **exhaustive pattern switches**, then
gated switch fallthrough as a build error.

**Design flaw removed.** "Python in Java": promoting every type distinction to a
runtime cast + instanceof/`default` means the compiler cannot prove a dispatch is
complete. A newly-added subtype, or a case the author forgot, compiles cleanly and
fails (or silently mis-handles) at runtime. **Sealing + exhaustive switches turn
every such gap into a compile error** — and turning them on **surfaces the
incompleteness bugs that were already there**. That is the high-value part.

**Incompleteness this actually surfaced** (the evidence a reviewer should weigh — these are master
defects found BY the conversion, not invented by it):

| Surfaced defect | Became |
|---|---|
| `ConstantPool.f_implicits` is a plain `HashMap` written from concurrent service threads (verbatim on master) | bug-list row **19** — implicit-identity cache written from concurrent service threads |
| Delegation synthesis publishes method code BEFORE assembly (`publish` at master `:2958`/`:3009` vs assembly at `:2980`/`:3135`) | bug-list row **20** — delegation synthesis publishes half-built method code |
| `MethodBody`'s untyped target payload had no coherent hash/equality contract until it was typed as a sealed union | part of bug-list row **11** — hash/equality contracts |
| Silent-`default` identity dispatch sites that never handled several real subtypes | fixed in-place by the conversion waves ("retire the remaining silent-default identity dispatch sites"; "close the last three non-sealed hatches") |

Each of those was invisible to the instanceof/`default` style precisely because a missing case is
indistinguishable from a deliberate fall-through. Expect the same on master: **the port's yield is
the compile errors**, and each one should be triaged as a potential bug rather than silenced with a
`default`.

**Width.** Very wide — every subtype declaration gains a `permits`, every dispatch
site becomes a pattern switch. But it is compiler-guided and mechanical: you
cannot miss a site, because the build fails until each is exhaustive.

**Master port spec.**
1. Confirm master's Java level supports `sealed` + pattern switch (JEP 409/441).
   It does; no toolchain change.
2. Port in the branch's wave order, one tree per slice, each independently reviewable. Commits are
   named by SUBJECT (the rebase rewrote all hashes — resolve with
   `git log --oneline --all --grep '<subject>'`):
   *"Seal the TypeConstant tree"* → *"Seal the IdentityConstant and ValueConstant trees"* →
   *"Seal the Component tree"* → *"Seal the BinaryAST tree and close the NodeType factory"* →
   *"Seal condition, pseudo, frame-dependent, and TypeInfo families"* →
   *"Seal the delegating composition subtree; finalize composition leaves"* →
   *"Seal-convert wave E: the parser AST tree"* →
   *"Type the MethodBody target payload as a sealed union"* →
   *"Seal RegisterInfo and make the JIT null-check dispatch exhaustive"*.
3. Convert the dispatch sites in lockstep: *"Replace silent-default dispatch with exhaustive pattern
   switches"*, *"Make planCodeGen exhaustive over the defining-constant union"*, *"Convert
   assert-guarded cascades to checked pattern switches"*, *"Retire the relation-calculus format
   switches too"*, *"Retire the remaining silent-default identity dispatch sites"*, *"Close the last
   three non-sealed hatches"*.
4. Land the guard last: *"Gate switch fallthrough as a build error"*. Until then it stays a warning
   so slices can land incrementally.
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
forced overloads). **Slice 1 is now complete:** the `getValueString` type leaves
(`a03a998f1`, function types render structurally as `Function<…>`; the pretty
`function R(P)` form was DROPPED — consistent with the runtime's `reflect/Type.x`
rendering — after a production-use audit confirmed SAFE: function-only output change,
no parse/cache-key/equality/serialization consumer), the
`ObjectHandle`/`ClassComposition` handle roots (`a03a998f1`), and
`ExceptionHandle.toString` (`6c1c7c686`, non-forcing `peekField`).

**E5 is now essentially COMPLETE on this branch.** Every display site the inventory flagged is
pure — the remaining ones landed in `4f35e55a6` (ParamInfo, TerminalTypeConstant, PropertyBody),
`23e2307d6` (Annotation, Contribution), `a700cca30` (MethodStructure, BinaryAST) and `63ee73a86`
(xRTType, xFuture, xEnum) — and the enforcement ratchet `DisplayPurityTest` (`beecae682`) now runs
with an EMPTY baseline, so any display method that acquires an impure callee fails the build. Only
the inventory's SUSPECT/racy-read rows (`Frame`/`FiberQueue`/`ServiceContext` short forms, JavaJIT)
remain, and those are read-only rather than mutating.

Two findings worth carrying to master with the port: (1) most of the impurity was **incidental, not
inherent** — caching a resolution (`ensureResolvedConstant`, `getAnnotationClass`) or interning a
comparison key (`getImplicitlyImportedIdentity`, `clzInject`) — so the information could be kept by
reading without storing and comparing by NAME, rather than exiled to a forced variant; and (2) the
genuinely-deferred state (lazy annotation split, field layout, `EnumInfo`, source normalization) is
peekable, which is why the pure renderings lose nothing once the state exists.

**The API this adds to master** (small and self-contained — no framework, no base class, no
annotations; three kinds of method plus markers). The key insight for a reviewer: **most of the
impurity was incidental, not inherent** — a cached resolution, or an interned key used only as a
comparison target — so the displayed information is KEPT, not dropped:

1. *Peek accessors* (non-forcing reads of deferred state, each with a "not computed" sentinel):
   `Lazy.Bound.isComputed()` (already on master via PR #539),
   `ClassComposition.isFieldLayoutComputed()` (package-private), `GenericHandle.peekField(String)`
   (protected), `PropertyStructure.peekPropertyAnnotations()`,
   `MethodStructure.Source.peekLineCount()`, `xEnum.peekNameByOrdinal(int)`.
2. *Resolve-without-store*: `Annotation.peekAnnotationName()` / private `peekAnnotationClass()` and
   an inlined resolve in `TerminalTypeConstant.getValueString()` — versus `getAnnotationClass()` /
   `ensureResolvedConstant()`, which write the resolution back. Corollary: compare annotation classes
   **by name**, which removes the `getImplicitlyImportedIdentity`/`clz*()` interning outright.
3. *Explicit forced variants*: `TypeInfo.toString(boolean fRuntime)` (master's pre-existing arity),
   `Argument.toIdString(Frame, …)`, `OpVar.getName(Frame, …)`.
4. *Markers*: `const:#n`, `name:#n`, `<text deferred>`, `<enum ordinal=N>`, `line-count=<deferred>`.
5. *Two gates*: `DisplayPurityTest` (static banned-callee scan, empty baseline) **and**
   `DisplayPurityRuntimeTest` (empirical — renders 100+ live objects and asserts the shared
   `ConstantPool` does not grow, with a negative control proving the instrument works). The static
   gate is textual and cannot see impurity behind a helper; the empirical one is what catches it.

Provenance note (verified against `origin/master` `82683bcd2`): `TypeInfoReal` is **master's own**
class, not a branch invention — upstream `78b9ae951` split `TypeInfo` into the interface class plus
the `TypeInfoReal` implementation, and it predates this branch's fork point (`145f12f51`). The E5
change makes `TypeInfo.toString()` abstract so every subclass must supply a pure header; master has
exactly ONE subclass (`TypeInfoReal`), the same set this branch's `sealed … permits TypeInfoReal`
enumerates, so that re-abstraction ports cleanly. Sealing (E2) only makes the guarantee
compiler-enforced; it is not a prerequisite for E5.

Behavior changes a master reviewer should know about: function types render structurally
(`Function<…>`) instead of `function R(P)` — which matches what the runtime's `reflect/Type.x` already
prints; a typedef'd annotation/constraint renders under its typedef name; and `BinaryAST.toString`
no longer nags to stderr (its `reportUnimplemented` helper and static set are deleted — note this
class is expected to be retired anyway). No consumer parses, caches, keys, or serializes any of these
strings (audited).

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

**What `FrozenArray` is.** Not a JDK type — this branch created it
(`javatools_utils/src/main/java/org/xvm/util/FrozenArray.java`, introduced by
"Add FrozenArray, the stage-3 shared-metadata representation"). It is a
`public final class FrozenArray<T> implements Iterable<T>`: an immutable,
index-addressable wrapper over a Java array, for metadata shared across threads,
containers, and interned constants. API: `adopt(T[])` (wrap, taking ownership)
vs `copyOf(T[])` (wrap a private copy), `get/size/isEmpty/iterator/stream/copy/
contentEquals`, and `unsafeArray()`.

Three design choices a porter must not "fix":
- **Deliberately not a `List`** — and not for the reason the original javadoc
  gave (corrected 2026-08-28). Extending `AbstractList` would *not* permit
  mutation; its mutators already throw. The two real reasons: (a) **zero-copy
  interop with `T[]`** is the point — 115 hot sites need the actual array, and
  no `List` yields one without `toArray()` copying per call, while
  `Arrays.asList` avoids the copy only by being live-writable, which is
  precisely the hazard removed (Families A and B each killed one); and (b)
  `List` **mandates element-wise `equals`/`hashCode`**, which this type
  deliberately refuses. Only weakly: `List` advertises mutators, so read-only
  would be a runtime (`UOE`) rather than compile-time contract.
- **`unsafeArray()` is an intentional escape hatch, not an oversight.** Hot
  consumers — hashing, serialization, `System.arraycopy`, the JIT build path —
  would take a measurable hit from a per-call copy. Callers must not write to
  or hand out the result. `FrozenArrayEscapeRatchetTest` pins the escape count
  as a **down-only ceiling (115)**, so the contract cannot erode silently and
  tier-2 sites can tighten incrementally.
- **No `equals`/`hashCode` override.** Wrapper identity is not element
  equality, and the owning constants already hash their elements through
  `unsafeArray()`. `contentEquals` is the explicit opt-in.

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
5. Land the **`CallChain` encapsulation** (`ec43d7dfb`) — it stands on its own
   and is worth porting whether or not Family C is ever converted. It routes 28
   open-coded accesses to `f_aMethods` (`.length` ×12, `[0]` ×11, `[nDepth]`
   ×5) through two accessors, `head()` and `bodyAt(int)`, both `protected` so
   the nested `FieldAccessChain` subclass inherits them. It also fixes a real
   inconsistency it exposed: `getTop()` guarded the empty chain but
   `getProperty()` indexed `[0]` unguarded, so it threw `ArrayIndexOutOfBounds`
   where its siblings returned null, and empty chains ARE constructible. Master
   carries the same shape, so this is portable as a standalone cleanup.

**Family C (MethodBody chains) — deliberately NOT in this port.** Evaluated and
deferred; see `array-element-exposure-audit.md`. Two things a porter should know
so the decision is not silently re-litigated:
- Step 5 above **invalidated the width half of the deferral rationale** ("`CallChain`
  must keep raw-array indexing"). The `CallChain` field conversion is now a
  2-method change, not 28 sites.
- But `CallChain` was never the exposure. `f_aMethods` is `protected final`,
  per-composition, and does not escape. The genuine Family C exposure is
  **`MethodInfo`** — interned in `TypeInfo`, shared across containers — whose
  `getChain()` returns `m_aBody` raw to 10 call sites and whose
  `ensureOptimizedMethodChain()` returns the `m_aBodyResolved` cache raw to 4
  more. Ledger rows 44/51 made that cache safely *published*; they did not make
  it *immutable*. The sharpest instance is `BuildContext.callChain`, a
  `public final MethodBody[]` holding the escaped array: `public final` would be
  genuinely safe on a `FrozenArray` and is not safe on a raw array. All 10
  consumers are verified read-only, so this is defense-in-depth, not a live bug
  — which is why it stays deferred, with the escape ratchet standing in.
- `PropertyInfo.m_aBody` is `MethodInfo`'s **twin** (already `final`, one write,
  5 read-only consumers, no lazy cache) and is the only trivial conversion left
  in the whole codebase. If Family C is ever resumed, the two land together.
- **Two known gaps in `FrozenArray` itself**, both recorded in the stage-4 survey
  in [array-element-exposure-audit.md](../array-element-exposure-audit.md):
  (a) it is generic, so **9 primitive-array escapes are unclosable and invisible
  to the ratchet** — proposed fix is `FrozenByteArray`/`FrozenCharArray`/
  `FrozenIntArray`, three of which would close `ConstantPool`-interned constants
  and two of which would *remove* a per-call defensive clone; and (b) the seven
  `MethodStructure`/`Annotation`/`Parameter` array fields are non-final for
  load-bearing protocol reasons (two-phase disassembly, `registerConstants`
  in-place rewriting, clone-then-owner-fixup) that tie them to **E3/E6/E9**
  rather than to E7.

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

---

## E9 — Constructor `this`-escape elimination, with the lint made fatal

**What it is.** A constructor that calls an overridable method, or hands `this` to anything, lets a
subclass (or another thread) observe a half-built object. This branch swept those out of the ASM and
runtime construction paths and then **turned the compiler check on permanently**: the build adds
`-Xlint:this-escape` unconditionally, so under `-Werror` a new escape fails the build.

**Design flaw removed.** Partially-constructed objects escaping into overridable code or into shared
state. The failure is a field that is null "impossibly", or a subclass hook running before its own
fields exist — intermittent, and traced back to construction only with difficulty.

**Relationship to master.** Master already fixed the *utility* classes (PR #539,
"Fix concrete utility this-escape hazards"), which shows the concern is accepted. What master does
NOT have is (a) the same treatment for the ASM/runtime constructors and (b) the lint itself. Compare
`build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts`: this branch adds
`-Xlint:this-escape` outside the `lintSnapshot` guard; master has no such line.

**Width.** ~23 commits, but each site is small and local (extract a private helper, initialise fields
directly, make the class final where nothing extends it).

**Master port spec.** Land the lint LAST: fix the escapes first with the lint as a warning, then flip
it on so it cannot regress. Worked example already ported: `VersionTree`'s constructors called the
overridable `clear()`/`put()`/`putAll()`; they now initialise fields directly and use a private
`putInternal`, with `AsmConstructorEscapeTest` pinning that a subclass's `clear()` is not called
during construction.

**Gate.** The lint itself, once fatal, is the gate; `AsmConstructorEscapeTest` covers the behavioural
half.

---

## E10 — Switch-fallthrough gate, and arrow-form migration

**What it is.** In Java an accidental missing `break` and a deliberate cascade are syntactically
identical. This branch classified **every** fallthrough site in the composite build (84: 81 main + 3
test), marked the intentional ones, fixed four missing markers, and then made `-Xlint:fallthrough`
fatal — so a forgotten `break` now fails the build instantly. Arrow-form (`case X ->`) migration
shrinks the remaining suppression list over time, because arrow form *cannot* fall through.

**Design flaw removed.** A state machine that silently skips or repeats a stage. In runtime/compiler
code those stages are owner-sensitive, so the corruption appears far from the missing `break`.

**Width.** Small: two build-config lines plus the marker/annotation pass.

**Master port spec.** Same order as E9 — classify and mark first (the reviewed suppressions are the
documentation), then turn the lint fatal. Master has no `-Xlint:fallthrough` line at all.

**Evidence it is honest, not cosmetic.** Re-verified 2026-08-28 by deleting all 55 suppressions and
letting the fatal lint identify the real ones: **54 are genuine cascades, 1 was stale**. None of the
54 switches on a sealed hierarchy — they are enums (`Composition` ×9, `Token.Id` ×5, `Format` ×3),
state-machine stages, chars and Strings. So this is complementary to E2, not a subset of it: sealing
fixes CLASS dispatch, this covers protocol machines over scalars.

---

## E11 — Retire stringly-typed and cast-based dispatch

**What it is.** Dispatch decided by `String` comparison, by `instanceof` cascades over an untyped
payload, or by casting `Object` and hoping — replaced with typed payloads (sealed unions, records)
and real generics, so the compiler checks what the code previously asserted at runtime.

**Design flaw removed.** This is the "Python in Java" complaint stated precisely: when a type
distinction is expressed as a string or an `Object` cast, the compiler cannot prove the dispatch is
complete or the payload well-formed, so a wrong branch is indistinguishable from a right one until it
fails at runtime — often as a `ClassCastException` far from the decision.

**Width.** ~17 commits. Notable: `MethodBody`'s target payload typed as a sealed union (which
surfaced the hash/equality gap that became bug-list row 11), the `DefiningConstant` union conversion
done at a file boundary to avoid a 150-call-site return-type change, and the format-switch retirement
in `TerminalTypeConstant`.

**Relationship to E2.** E2 seals the hierarchies so switches can be exhaustive; E11 is the other half
— giving the *payloads* types so there is something meaningful to switch on. They are best landed
adjacent, and both are bug-finding: the compile errors are the yield.

**Also in scope: nullness annotations.** `org.jetbrains.annotations` is already a `compileOnly`
dependency on master and used in a handful of places. The branch's position, worth stating: prefer
making a value **total** over annotating it as nullable — a no-op implementation
(`ErrorListener.BLACKHOLE`) or `Optional` for genuine absence removes the question, and `@NotNull`/
`@Nullable` document what remains.

---

## E12 — The gates that make the rest verifiable

**What it is.** The test infrastructure this branch built so the other enhancements could be landed
with evidence rather than assertion. Portable independently of any of them, and arguably the safest
thing to land on master first, because it is additive.

| Gate | What it pins |
|---|---|
| `TypeComparisonCorpusTest` | interning identity, order-sensitive parameterized-type equality, `isA` relations, nullable widening — the properties a pool/type change could silently break |
| `DisplayPurityTest` | static banned-callee scan of display bodies, with a baseline that must shrink (E5's ratchet) |
| `DisplayPurityRuntimeTest` | EMPIRICAL purity: renders 100+ live objects and asserts the shared pool does not grow, **with a negative control** proving the instrument works |
| `SharedPoolGrowthCharacterizationTest` | measures and PINS the shared-pool leak, flipping to assert-bounded when E3 Part B lands |
| `AsmConstructorEscapeTest` | E9's behavioural half |
| `DirRepositoryConcurrentScanTest` | the concurrent-scan race (bug row 27; PR #547) |
| `FileStructureErrorListenerTest` | the ambient-pool NPE (bug row 29; PR #548) |
| same-JVM sequential/nested stress harness | cross-container bleed, the E1 failure mode |

**Why it matters as its own entry.** Two of these turned "we think this is a bug" into "here is a
red test on master" (rows 27 and 29), and one (the negative control) exists specifically so a green
result cannot be mistaken for a working instrument. A reviewer can adopt the gates without adopting
any of the refactors, and then judge each refactor by whether it keeps them green.

**Master port spec.** Each test is standalone; the two that need built XDK modules guard with
`assumeTrue`, and must be fed modules the running JVM can parse (a `.xtc` from a different format
version fails to parse and would make the test vacuously green).

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

---

## E13 — Latent typing hazards: shapes that permit a defect no caller commits

Companion to the bug list, and deliberately separate from it. These are **not** defects — every
current caller is correct, and filing them as bugs would overclaim. They are shapes where the
type system was in a position to prevent a class of defect and had been prevented from doing so,
so the next caller is one slip away. Master carries each one identically.

Found by the static-typing campaign
([static-typing-campaign.md](static-typing-campaign.md)); the classification rule lives there.

### E13.1 — `Component.unlinkSibling(Map kids, Object id, …)`

**What it is.** The map and the key were erased together:

```java
protected void unlinkSibling(Map kids, Object id, Component child, Component sibling)
```

The body does `kids.put(id, ...)`, so nothing checks that the key belongs to the map.

**Why it matters more than an ordinary raw type.** A mismatch does not throw. It inserts, say,
a `String` key into a `MethodConstant`-keyed map — no exception, no stack trace, just a child
that is silently invisible to every later lookup. That is strictly worse than the
`ClassCastException` a cast would have given, which at least names the moment.

**Why it is NOT a bug.** All three callers audited: `Component.removeChild` passes
`Map<String, Component>` + `String`; `MultiMethodStructure.removeChild` passes
`Map<MethodConstant, MethodStructure>` + `MethodConstant`; `FileStructure` throws
`UnsupportedOperationException`. Every pair matches today.

**Fix (landed in the branch).**

```java
protected <K, C extends Component> void unlinkSibling(
        Map<K, C> kids, K id, Component child, C sibling)
```

Verified by compiling a deliberate mismatch against the built classes - javac rejects it with
*"inference variable K has incompatible bounds"*. One localised `@SuppressWarnings("unchecked")`
remains on the VALUE side, where a `Component` sibling chain meets a caller's narrower map; that
is the one thing the key/value pairing cannot express, and it is one documented line rather than
an erased map. The walk also stops reassigning its own `sibling` parameter.

**Width.** One method, one override, three callers. Source- and binary-compatible.

---

## Appendix: commit-hash resolution table

The 2026-08-28 rebase onto `origin/master` `82683bcd2` rewrote all 297 branch commits, so the
short hashes used as identifiers throughout this document no longer resolve on the branch.
Subjects DO survive a rebase, so resolve any row with:

```
git log --oneline master..HEAD --fixed-strings --grep '<subject>'
```

(The pre-rebase hashes remain reachable via the `backup/pre-rebase-master` tag.)

| Cited hash | Commit subject (authoritative) |
|---|---|
| `01b5123ea` | Should-fix #4: ClassStructure.getTypeParams returns an immutable Map view |
| `029ff4138` | Refuse view cloning of live-lifecycle arrays, tuples, functions |
| `03555b779` | Embedding API 1/N: expose the run-completion future (issue 543 1a/1b) |
| `08553d149` | Use owner lazy class composition helpers |
| `0aa9a86cd` | Harden owner-sensitive lazy caches |
| `0af827c72` | Retire Cloneable from the structure family |
| `0bbfc98c4` | Refuse view cloning of array delegates |
| `0d0a7ddcc` | Make TypeInfoReal member-map getters read-only views |
| `0fb5d2cf0` | Finish owner-local lazy cache diagnostics |
| `1608848a4` | AST clone eradication 2/N: copy constructors for the Statement family |
| `178988f7d` | Measure + pin the #2 shared-pool leak (frozen-annex Phase C gate) |
| `1e1bacaaa` | Record sealed stages 1-4, default-deny landing, and PR 16 narrative |
| `1ea9c0632` | AST clone eradication 1/N: deepCopy walk over a parity-checked copy bridge |
| `1f1b5de6e` | Safely publish class field layouts |
| `23e2307d6` | Display purity slice 3a: Annotation + Contribution (ratchet 7 -> 4) |
| `2485d9bac` | Remove container construction owner escapes |
| `25371b397` | Retire Cloneable from Token, Source, and MethodStructure.Source |
| `2716435f1` | Restrict ambient current pool lookup |
| `33aa1aa06` | Safely publish class composition helpers |
| `35a55b81f` | Array stage 3, Family A: freeze SignatureConstant type storage |
| `3d1647463` | Synchronize runtime container registry lookup |
| `3e4305fe2` | Delete the ConstantPool ambient-pool bridge entirely |
| `4740995de` | Make runtime pool publication guard unconditional |
| `4c6521dd9` | Use metadata owners instead of current pool |
| `4f35e55a6` | Display purity slice 2: ASM constants (ratchet 10 -> 7) |
| `4f72cbb3f` | Build the foreign-reference detector (world X-ray slice 3) |
| `566a69464` | Freeze static constant-pool metadata |
| `5d5773979` | Use receiver pool for function compatibility |
| `5f5117e07` | Remove remaining fInstance template roles |
| `5fce7b9ae` | Use explicit pool for nested identity resolution |
| `632cac927` | Guard HandleConstant against raw cross-container serving |
| `63ee73a86` | Display purity slice 5: runtime templates - BASELINE NOW EMPTY |
| `64922284e` | Type-comparison corpus: a working gate for pool/type-system changes |
| `6c1c7c686` | Side-effect-free toString: ExceptionHandle reads text without forcing the layout |
| `73398ef28` | Record Lazy.ofOwner follow-up for converter map views |
| `78b9ae951` | UPSTREAM (not a branch commit): the PR that introduced `TypeInfoReal`, squash-merged into master - see the E5 provenance note |
| `7ce5662d1` | Refuse view cloning of register-bound refs |
| `80999b393` | Remove unused template INSTANCE fields |
| `8382e0268` | Make TypeInfo.toString() pure (header only); full dump on explicit overload |
| `84fa61534` | Remove current pool lookup getter |
| `86ce6a23f` | Embedding API 3/N: XtcEngine first-class in-memory compile + run |
| `87f751125` | Array stage 3, Family B: freeze ParameterizedTypeConstant parameter storage |
| `8ba3e0184` | Safely publish optimized metadata chains |
| `8cd0d92ac` | Fence runtime-published pool mutation |
| `8d9474c91` | Safely publish TypeInfoReal caches |
| `93c36fbf6` | Remove leaf native template INSTANCE fields |
| `9c5e351f3` | Publish normalized type cache safely |
| `9d99789d3` | Use owner lazy property struct views |
| `9f5773910` | Narrow ambient constant pool runtime scopes |
| `a03a998f1` | Side-effect-free toString: pure getValueString type leaf + ObjectHandle root |
| `a31f37ebf` | Avoid native container startup this escape |
| `a3e0a4c5e` | Close the clone-view family: hoist the freeze cell, tuples and functions share views |
| `a54565af3` | AST clone eradication 5/5: Cloneable, clone(), and the bridge are gone |
| `a700cca30` | Display purity slice 3b: MethodStructure + BinaryAST (ratchet 4 -> 2) |
| `a87ad44a1` | XtcEngine: compile(SourceUnit...) instead of compile(Map) |
| `a9e7d58c0` | Side-effect-free toString: the two AMBIENT op-display roots |
| `aade77699` | Remove array leaf template INSTANCE fields |
| `ac339e9d4` | AST clone eradication 3/N: copy constructors for the Expression family |
| `ac428aeca` | Enforce the container model: one root per runtime, everything else nested |
| `b834d3353` | Update Lazy.ofOwner follow-up: API rides with PR #539 |
| `be0270e0d` | Deprecate ambient current pool lookup |
| `beecae682` | Display purity: land the enforcement ratchet + name/conformance rules |
| `c66230c28` | Adopt master's reviewed Lazy.Bound (PR #539); replace branch Lazy.Owner |
| `c93b5ad61` | Use file-owned diagnostics instead of current pool |
| `cb91df6d1` | Add FrozenArray, the stage-3 shared-metadata representation |
| `cd432c873` | Record the sealing campaign's designed end state |
| `d2165e4f8` | Share freeze state across object views |
| `d360c0033` | AST clone eradication 4/N: type expressions, Parameter, VersionOverride, EvalStatement |
| `d58ebfea0` | Use receiver pool for folded numeric ranges |
| `db4ae7900` | Default-deny view cloning of mutable handles |
| `dc3e0d90d` | Name every message-less UnsupportedOperationException in the runtime |
| `dc95e08e6` | Remove dead fInstance constructor flags |
| `dd94d6425` | Share the lazy initialization guard across views |
| `e36efeea0` | Should-fix #5 (1/2): read-only views for genuinely-live leaky getters |
| `e78d7100b` | Should-fix #5 (2/2): asm map getters - inherent-freeze where possible |
| `e856d85ce` | Require explicit pool for type substitutability |
| `eaf3214d6` | Safely publish property metadata caches |
| `f4df60ed1` | Refuse view cloning of Mutable arrays |
| `f5d3e45eb` | Embedding API 2/N: Java host can create and run nested containers (the linchpin) |
