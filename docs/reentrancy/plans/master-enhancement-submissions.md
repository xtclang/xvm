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
| E13 | Latent typing hazards: shapes that permit a defect no caller commits | tiny, per-item | Low | the existing signatures | [static-typing-campaign.md](static-typing-campaign.md) |
| E14 | Static typing campaign: replace `Object`-rooted dispatch with real types | 5 independent slices | Low → Medium-High | the existing class trees; javac generics + sealed | [static-typing-campaign.md](static-typing-campaign.md) |
| E15 | Dispatch native calls through the receiver instead of past it | mechanism + 1 file per commit | Low / Low-Medium | `ObjectHandle`, `CallChain`, the existing `getTemplate(Class<T>)` | [static-typing-campaign.md](static-typing-campaign.md) (T8) |
| E16 | Split native FUNCTION dispatch from native METHOD dispatch (retire the nullable receiver) | 3 templates + 12 call sites | Low | `ClassTemplate`, `asm/op/Call_*` | (this file) |
| E17 | Separate the colliding `A_*` / `R_*` integer protocols | 10 constants, one file | Low | `Op` | (this file) |
| E18 | Close the closeable switches; ratchet the rest (format completeness) | one test + 11 small switches | Low | `Constant.Format`, the sealed trees | (this file) |
| E19 | Retire String dispatch for native methods | 76 sites, 542 labels — NOT one PR | High | `MethodStructure`, the template tree | (this file) |
| E20 | `Format` vs class hierarchy: pin the 4 divergences, then audit 154 sites | step 1 is one test; steps 2-3 are 6 small PRs | Low (step 1) | `Constant.Format`, the 4 divergent pairs | (this file) |
| E21 | Type `getDefiningConstant()` as the identity/pseudo union | 17 files; deletes a 33-site workaround layer | Medium | `TypeConstant`, `TerminalTypeConstant` | (this file) |
| E22 | **The real one**: `ObjectHandle` as calling convention — 1,439 casts (50%) | multi-PR programme; needs a design decision | High | `ClassTemplate`'s 37-method protocol, `ObjectHandle` | (this file) |
| E23 | Bind natives to typed handlers instead of String dispatch (744 labels) | framework is 1 PR; then per-template | Medium | `ClassTemplate`, every template | (this file) |
| E24 | `null` as an absent argument (60% of 392 sites); asking callers to restate resolved data | one small record + binder change | Low | `ClassTemplate.markNativeMethod` | (this file) |
| E25 | Generify the delegate hierarchy — 134 casts, BLOCKED on splitting `xRTDelegate`'s dual role | split first, then mechanical | Medium | `xRTDelegate` and 20 implementations | (this file) |
| E26 | What is left of `unchecked`/`rawtypes` — 92 + 59, and why most are not trivial | trivial part 1 PR; two hierarchy changes separate | Low / Medium | `ServiceContext` message hierarchy | (this file) |
| E27 | Op-info cache: raw `EnumMap` whose key silently names the value type | 1 PR, per-op-class migration | independent | `OpInfoKey<V>` + generic get/set |
| E30 | Storage operations belong on the handle; the `<H>` parameter is the symptom | per-handle, then one deleting commit | supersedes E25's parameter | measured: 105/132 bodies need only the handle |

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

## Filing Index

One row per enhancement, with what an agent needs to raise it as its own PR on `master`. Unlike the
bug list, several of these have real prerequisites - taking them out of order produces a change that
compiles and then fails, or one that is three times the size it needed to be.

**Independent** means it can be branched from `master` today and touches nothing another row is
mid-way through.

| ID | Enhancement | Size | Depends on | Notes for the filer |
| --- | --- | --- | --- | --- |
| E1 | Owner-local instance caches | medium | independent | |
| E2 | Seal the hierarchies, exhaustive dispatch | large | independent | do per-hierarchy, not all at once |
| E3 | ConstantPool explicit-pool threading | large | its two halves are ordered: pool threading, then freeze-on-publish | |
| E4 | Embedding API + container model | large | independent | |
| E5 | Side-effect-free `toString()` | medium | independent | E9 is *not* a prerequisite |
| E6 | AST clone eradication | medium | independent | |
| E7 | Frozen shared metadata, refuse unsafe view cloning | medium | independent | |
| E8 | Diagnostics: named exceptions, logging/JFR | large | independent | |
| E9 | Constructor `this`-escape elimination | medium | independent | |
| E10 | Switch-fallthrough gate, arrow-form migration | medium | independent | |
| E11 | Retire stringly-typed and cast-based dispatch | large | superseded in practice by **E23** | file E23 instead unless you want the survey |
| E12 | The gates that make the rest verifiable | small | independent | worth doing early; it is what keeps the others honest |
| E13 | Latent typing hazards | small | independent | a survey, not a change |
| E14 | Static typing campaign (the umbrella) | - | umbrella for E15-E25 | not a PR on its own |
| E15 | Dispatch native calls through the receiver | large | independent, but **E23 subsumes most of it** | |
| E16 | Split native FUNCTION from native METHOD dispatch | medium | independent | |
| E17 | Separate the colliding `A_*` / `R_*` protocols | small | independent | prerequisite for the 135 int-constant switches in **E18** |
| E18 | Close the closeable switches; ratchet the rest | small (step 1) | step 1 independent; the int-constant part needs **E17** | step 1 is one test file, land it alone |
| E19 | Retire String dispatch for native methods | large | **E23** is the built version of this | do not file both |
| E20 | `Format` vs the class hierarchy | small (step 1) | step 1 independent; steps 2-3 need step 1 | **do not start at the audit**; the section says why |
| E21 | Type `getDefiningConstant()` as the identity/pseudo union | medium | independent | deletes a 33-site workaround layer |
| E22 | `ObjectHandle` as calling convention; operator protocol | framework 1 PR, then per-template | framework first, then templates in parallel | **resolved**: option C, swept - 115 overrides to 22 |
| E23 | Bind natives to typed handlers | framework 1 PR, then per-template | framework first, then each template independently | **the framework PR must land first**; templates are then parallel |
| E24 | `null` as an absent argument | small | independent | can land before or after E23 |
| E25 | Generify the delegate hierarchy | medium | **the `xRTDelegate` split**, and that needs `@NativeTemplate` | see the ordering below |

### The one chain that matters

Everything above is independent except this, and taking it out of order does not work:

```
@NativeTemplate annotation        (small, independent - file this first)
        |
        v
split xRTDelegate                 (concrete class KEEPS its name; the BASE is renamed)
        |
        v
E25 generify xRTDelegate<H>       (134 casts)
```

The annotation is required because the Ecstasy class a template serves is derived from its **file
name**, so without it the concrete class cannot be renamed and the split cannot be expressed. The
split is required because `xRTDelegate` is both the protocol base and the object-array
implementation, so a generic base cannot compile its own defaults.

Each of the three is a reasonable PR on its own, and the first two are useful even if the third is
never done - the split alone turned master bug 36 into a compile error.

### Filing any of these

1. Branch from `master`. None of these needs a campaign branch, and several were developed on one
   only because that is where they were found.
2. Check the row's dependency column before starting. E20 and E23 in particular name an order, and
   both sections explain what goes wrong if it is ignored.
3. Where a row says a step is "one test file", that step is worth filing by itself - it is the part
   that keeps the rest from regressing.
4. Several sections record a **negative result** - something measured that did not pay. Keep those
   in the PR description if you re-derive them; they are the reason the change is scoped the way
   it is.

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

## E14 — Static typing campaign: replace `Object`-rooted dispatch with real types

Landed on `lagergren/lazy-instance`. Deliberately written as **independently portable slices** -
each lands alone, each is verified the same way, and none depends on adopting the others. Design
record: [static-typing-campaign.md](static-typing-campaign.md).

**Measured effect on master's shape:** casts 2952 → 2840; the `Object`-rooted API surface (where a
type was never expressed, as opposed to merely widened) 398 → 265, driven by
`Map<Object, …>` declarations falling 140 → 50.

**Verification rule for every slice below — learned the expensive way.** A narrowing or typing
change is **not verified until `xdk:installDist` passes**. The unit suite is not sufficient: it
missed a `ClassConstant` narrowing regression that sat green across four commits, and it missed
three silent map-read misses in E14.5. Compiling `lib_ecstasy` is what exercises these paths.

### E14.1 — Covariant `getComponent()` on identity constants

**What.** `createClass` already returned `ClassStructure`, `createMethod` a `MethodStructure` - the
type was known at creation and thrown away at lookup. Seven identity constants now restate it:
`MethodConstant → MethodStructure`, `PropertyConstant`, `ModuleConstant`, `PackageConstant`,
`TypedefConstant`, `MultiMethodConstant`, plus the AST declaration statements
(`MethodDeclarationStatement`, `TypeCompositionStatement`, `PropertyDeclarationStatement`).

**Yield.** ~120 casts deleted. **Width:** small, declarative. **Port difficulty:** Low.

**Carry the caveat with the patch.** `ClassConstant` must **NOT** be narrowed - it can name a
`TypedefStructure`, and narrowing it produced
`ClassCastException: TypedefStructure cannot be cast to ClassStructure` inside
`ConstantPool.getImplicitlyImportedComponent` while compiling the core library.
`IdentityComponentNarrowingTest` pins both the sound narrowings and the two that must stay
`Component`, so a repeat attempt fails with the reason. **The general rule: "nearly every caller
casts to X" is not grounds to narrow; the subclass must ALWAYS produce X.**

### E14.2 — `ValueConstant<V>`

**What.** The class declared `public abstract Object getValue()` while all 26 overriders narrowed
covariantly - the type existed and was discarded at exactly one point. Now `ValueConstant<V>`.

**Two decisions that come with it.** `MatchAnyConstant` **leaves** the hierarchy: it is a wildcard
marker, not a value (its `getValue()` returned `"_"` under a comment admitting there is no correct
answer, it has zero callers, and `CaseManager.covers()` special-cases it first). And
`EnumValueConstant.getValue()` now throws instead of returning null for an impossible ordinal.

**Width:** 20 subclasses, one line each. **Port difficulty:** Low. Only 6 call sites had a
statically-`ValueConstant` receiver, all in `CaseManager`; verified bytecode-identical by `javap`.

### E14.3 — Retire raw types where they hid real structure

**What.** `VersionTree.Node` was raw in 40 places because its children were a hand-managed sparse
array - null-padded, packed from zero, doubled on growth, `arraycopy`-shifted, with
`kids.length` meaning **capacity** rather than size. Four static helpers reimplemented `List`.
Converting to `List<Node<V>>` deletes all four, removes the `(Node<V>[]) new Node[]` unchecked cast
that existed only because Java cannot create generic arrays, and lets every consumer drop its
null-padding logic. `ListMap.EMPTY` (a raw public constant) becomes a generic `empty()` factory -
still a `ListMap`, because the type system depends on its iteration order.

**Width:** one data structure plus a handful of sites. **Port difficulty:** Low-Medium.

**Not finished, deliberately.** `-Xlint:rawtypes` is not yet enabled: the initial grep found 15
sites, the lint found 60+, and ~40 remain. A half-enabled fatal lint just blocks the build.

### E14.4 — Typed accessor pairs where a PREDICATE establishes the type

**What.** A seam the earlier study explicitly filed under "cannot be typed". That verdict is wrong:
these can be typed, just not by narrowing a return. In `TerminalTypeConstant`, 31 sites read

```java
if (!isSingleDefiningConstant()) {
    TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
```

The guard already proved the type; the cast is the caller re-asserting it. One accessor,
`ensureResolvedTypedef()`, does both - 31 casts become 1.

**Width:** one file. **Port difficulty:** Low. **Generalisable:** wherever a boolean predicate
establishes a type, an accessor that asserts the predicate and returns the narrow type replaces
every cast behind it.

### E14.5 — `Nid`: type the nested-identity union

**What.** The nid was a bare `Object` holding five unrelated things, used as the key of the maps
type resolution runs on. In one place the union was written out **in a comment**, because there was
no type for it:

```java
private final Object f_enid; // String | PropertyConstant | NestedIdentity
```

`Nid` is that comment. Only what we do not own is wrapped: `SignatureConstant`,
`IdentityConstant` and `NestedIdentity` carry the marker directly - no wrapper, no `equals` change,
no conversion where one is already held. `String` and `int` become `ByName`/`ByLambda` records,
which makes the union's safety **structural** (no two record types are ever equal) rather than the
accident that `String` and `Integer` happen not to equal each other.

**Width:** the largest slice - 4 map families, ~71 locals and parameters, 12 files declaring
nid-keyed maps. **Port difficulty:** Medium-High. **This is the one to read the warning on:**

> javac type-checks map **writes** only. `Map.get`, `containsKey` and `remove` take `Object`, so a
> `map.get("Referent")` against a `Map<Nid, V>` compiles and silently returns null. Typing the maps
> caught every wrong write and **zero** wrong reads. Three silent read misses were found only by
> building the XDK. Where a read passes through a method you control, **type that method's
> parameter** - doing so to `getFieldInfo` converted five silent misses into five compile errors at
> once.

Land `NestedIdentityContractTest` **first and separately**: it pins mutual inequality, key
collision and non-collision, and it is what makes the rest safe rather than lucky.

---

## E15 — Dispatch native calls through the receiver instead of past it

**What it is.** Every native call arrives at the template as

```java
hTarget.getTemplate().invokeNative1(frame, method, hTarget, hArg, iReturn)
```

The handle produces the template and then passes **itself back as a parameter**, and the
template's first act is to cast that parameter back to the type it already was. **162 of the
runtime's handle casts sit inside `invokeNative*` for that reason - of which 83 are actually
convertible; see quirk 6.**

`ObjectHandle` gains four defaults that reproduce exactly the call above; `CallChain` dispatches
through the receiver. A handle that overrides one gets `this` already correctly typed - no cast,
no type parameter, nothing to erase.

**Width.** Two commits' worth of enabling change (`ObjectHandle` + `CallChain`), then **one
template file per commit**, each independently revertible. `xRegEx` is converted as the worked
example.

**Port difficulty.** Low for the mechanism, Low-Medium per file. **Reuses** the existing
`getTemplate(Class<T>)` accessor (~100 uses already) for template-owned state.

### Why NOT `ClassTemplate<H extends ObjectHandle>` — settled, do not retry

`getTemplate()` erases, so nothing can prove the returned template's `H` matches `hTarget`'s type.
This is not speculation: it is the identical failure that made `ComponentTemplateHandle<C>` remove
**zero** casts in E14, and it would fail the same way after a far larger edit.

### Scope: exactly four methods, and the line that fixes it there

**107 methods take an `ObjectHandle hTarget`.** A default for each would drag the whole
`ClassTemplate` API onto `ObjectHandle` and make it the god object the runtime already struggles
with. The line is:

> **Dispatch belongs on the receiver; operations belong on the template.**

`invokeNative1/N/NN/Get` are dispatch - "invoke this method on this object" - and the receiver is
exactly what gets cast. `buildHashCode`, `buildStringValue`, `invokeAdd`, `getFieldValue`,
`setPropertyCapacity` are things the template *does to* a handle; a handle should not know how to
hash itself in Ecstasy terms. Those 219 casts stay, correctly.

### ⚠️ Quirks a porter must know

**1. The handle tree and the template tree are DIFFERENT shapes, so `super` changes meaning.**
`RegExHandle extends ObjectHandle` while `xRegEx extends xConst`. Before the change,
`xRegEx.invokeNative1`'s `super` call walked the **template** chain into `xConst`. After it,
`RegExHandle.invokeNative1`'s `super` call walks the **handle** chain into `ObjectHandle`, whose
default delegates to `getTemplate().invokeNative1(...)` - which, because `xRegEx` no longer
overrides it, lands on `xConst` after all. The chain is preserved, but *by a different route*.

**2. Therefore CONVERSION ORDER MATTERS.** The handle tree is deep - 26 handles extend
`ObjectHandle` directly, but **12 extend `GenericHandle`, 9 `ServiceHandle`, 7 `RefHandle`, 7
`DelegateHandle`**. If a subclass handle is converted while its superclass handle is not, its
`super` call falls through `ObjectHandle`'s default to the template chain. If the superclass IS
converted, `super` hits the superclass handle instead. **Both can be correct, but they are not the
same route** - so convert a handle hierarchy top-down, or verify each conversion's `super`
behaviour explicitly.

**3. Handle classes are `static` nested, so they cannot see the outer template instance.**
Template-owned helpers are reached through the existing typed accessor:

```java
return frame.assignValue(iReturn, getTemplate(xRegEx.class).makeHandle(regex, nFlags));
```

This is also the answer to the per-container-state question: `f_container` and the owner-local
`Lazy.Bound` caches stay on the template and are reached the same way. One indirection per moved
method.

**4. The mechanism is separately verifiable, and should be landed separately.** With no handle
overriding anything, behaviour is identical by construction. Land `ObjectHandle` + `CallChain`
alone, confirm `xdk:installDist` and the suite, and only then start converting files. That
separation is what makes a bisect meaningful if a later conversion misbehaves.

**5. Performance is unmeasured.** A virtual call on `ObjectHandle` replaces one on
`ClassTemplate` - same count, different receiver, megamorphic either way. It should be measured on
the first few conversions rather than assumed, because this is the interpreter's hot path.

**6. Roughly HALF the casts are on handles shared by several templates, and those cannot be
converted at all.** This is the biggest correction to the plan and it is not visible from a cast
count. `IntNHandle` is declared in `xIntLiteral` but cast by **three** templates (`xIntN`,
`xUnconstrainedInteger`, `xIntLiteral`); `LongLongHandle` by two; `ClassHandle` by three. A handle
serving N templates cannot host N different dispatch implementations - deciding *which* template's
natives to run is exactly what `getTemplate()` does, so for those the dispatch correctly stays
where it is.

Measured with a parser that handles multi-line signatures (earlier counts in this campaign
disagreed by 3x because they did not):

| | Casts |
| --- | --- |
| Inside `invokeNative*` | **162** |
| Handle serves ONE template - convertible | **83** |
| Handle SHARED across templates - blocked | **79** |

So E15's realistic ceiling is **83 casts**, not the 151 an earlier estimate in this document
claimed. Check `grep -l "(XHandle) hTarget"` before starting a file: more than one template file in
the result means skip it.

**7. A native can be dispatched with a NULL target, and you cannot call a method on null.** All
twelve `Call_*` ops - `Call_00` through `Call_TN` - invoke natives as

```java
invokeNativeN(frame, function, null, ahArg, m_nRetValue);
```

That is the *function* path: a static function has no receiver. Templates on that path guard for
it explicitly - `BaseBinaryFP:143` reads
`double d = hTarget == null ? 0 : ((FloatHandle) hTarget).getValue();`, and `BaseDecFP:162` does
the same.

**Receiver dispatch cannot serve that path at all**, so any template whose natives are reachable
with a null target must keep its dispatch on the template. `BaseBinaryFP` and `BaseDecFP` are the
top two entries on the convertible list *and both are disqualified by this*. `xRegEx` converted
cleanly precisely because it has no null-target path - its natives are all instance methods.

**The check before converting a file is therefore two greps, not one:**

```bash
grep -l "(XHandle) hTarget" …        # more than one template file  -> shared handle, skip
grep -c "hTarget == null" <file>     # non-zero                     -> null-target path, skip
```

### Ranked by yield inside `invokeNative*` (convertible only)

| File | Handle | Casts |
| --- | --- | --- |
| `xTuple` | `TupleHandle` | 6 |
| `xRegEx` | `RegExHandle` | 5 (**done** - the worked example) |

`BaseBinaryFP` (9) and `BaseDecFP` (9) headed this list until quirk 7 disqualified them, and
`xUnconstrainedInteger` (10) and `BaseInt128` (7) until quirk 6 did. **Every one of the four
highest-yield candidates turned out to be blocked**, which is the honest summary of this
enhancement: the mechanism is sound and the worked example is real, but the population it can
actually reach is much smaller than the raw cast count suggests, and only a per-file check
establishes which.

`xUnconstrainedInteger` (10) and `BaseInt128` (7) were the top of the earlier ranking and are both
**blocked** - their handles are shared. That is the correction in one line.

---

## E16 — Split native FUNCTION dispatch from native METHOD dispatch

**The null is not data; it is a mode flag.** Every native call reaches a template through one
signature, and "this is a function, so there is no receiver" is encoded by passing `null`:

```java
// asm/op/Call_1N.java and eleven siblings
invokeNativeN(frame, function, null, ahArg, m_nRetValue);

// runtime/template/numbers/BaseBinaryFP.java:143
// hTarget could be null for a native function call
double d = hTarget == null ? 0 : ((FloatHandle) hTarget).getValue();
```

That comment is the design: one method serving two different operations, told apart at run time by
a null check that every implementer must remember to write.

**The callers are already perfectly disjoint**, which is what makes the fix clean rather than
speculative:

| Caller | Passes a target | Passes null |
| --- | --- | --- |
| the twelve `Call_*` ops (function path) | **0** | **12** |
| `CallChain` (method path) | all | **0** |

So the two paths never mix. They are two operations wearing one signature.

### The fix

Give the function path its own methods, and the receiver stops being nullable:

```java
// method - the receiver is guaranteed present, by construction
int invokeNative1(Frame, MethodStructure, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);
int invokeNativeN(Frame, MethodStructure, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn);

// function - there is no receiver, so none is named
int invokeNativeFunction1(Frame, MethodStructure, ObjectHandle hArg, int iReturn);
int invokeNativeFunctionN(Frame, MethodStructure, ObjectHandle[] ahArg, int iReturn);
```

`Call_*` calls the function form; `CallChain` calls the method form. Nothing passes null, nothing
checks for it, and a template that only serves functions implements only the function form -
`xRTFunction` currently expresses that with three `assert hTarget == null` statements, which the
split makes unnecessary.

### Width: three templates

Only **3** templates carry the guard at all: `BaseBinaryFP` (`:143`), `BaseDecFP` (`:162`) and
`xRTFunction` (three asserts). Plus twelve one-line call-site changes in `asm/op/Call_*` and the
new signatures on `ClassTemplate`. **Port difficulty: Low.**

### Why it matters beyond tidiness

1. **It removes a whole class of "forgot the null check".** Today every native template author must
   know that `hTarget` may be null and remember to guard. The two that do are the two that happen
   to serve both paths; nothing tells anyone else.
2. **It unblocks E15's quirk 7.** With `hTarget` non-nullable, receiver dispatch becomes available
   to `BaseBinaryFP` (9 casts) and `BaseDecFP` (9) - the two highest-yield candidates that quirk 7
   disqualified. **+18 casts, and more importantly the disqualification stops being structural.**
3. **The nullability is currently invisible.** The parameter is `ObjectHandle hTarget` with no
   annotation; the only statement of the contract is a comment in one template. Splitting the
   method states it in the signature, where it cannot be missed or drift.

**Land it before E15's remaining conversions**, not after: it changes which files qualify.

---

## E17 — Separate the two colliding integer protocols (`A_*` and `R_*`)

**A one-file, ten-line change that removes a silent-wrong-answer hazard.** `Op` defines two
unrelated integer protocols over *exactly the same values*:

| Argument protocol | | Result protocol | |
| --- | --- | --- | --- |
| `A_STACK` | -1 | `R_NEXT` | -1 |
| `A_IGNORE` | -2 | `R_RETURN` | -2 |
| `A_IGNORE_ASYNC` | -3 | `R_EXCEPTION` | -3 |
| `A_DEFAULT` | -4 | `R_RETURN_EXCEPTION` | -4 |
| `A_THIS` | -5 | `R_CALL` | -5 |
| `A_TARGET` | -6 | `R_RETURN_CALL` | -6 |
| `A_PUBLIC` | -7 | `R_REPEAT` | -7 |
| `A_PROTECTED` | -8 | `R_BLOCK` | -8 |
| `A_PRIVATE` | -9 | `R_PAUSE` | -9 |
| `A_STRUCT` | -10 | `R_RESET` | -10 |

**Ten for ten.** One says *which argument register*, the other says *what happened during
execution*, and they are told apart only by which parameter position a value happens to occupy. A
value that crosses from one protocol to the other is not rejected - it is silently read as a
perfectly valid value of the other protocol. No exception, no crash, a wrong answer.

**The fix: renumber `R_*` out of the range.** `R_NEXT = -101`, and so on.

**Why this is safe, verified rather than assumed:**

- **`A_*` cannot move** - it is part of the binary format. A constant argument is encoded as
  `CONSTANT_OFFSET - i` (`Op.java:1198`, `:1270`) and `AstNode` sizes an array as
  `new RegisterAST[-Op.CONSTANT_OFFSET]`.
- **`R_*` can** - they are runtime-only return codes with no serialization path.
- **No code depends on their values.** Checked for range comparisons (`R_x < y`), ordering, and
  arithmetic: **zero** of each. Every one of the ~51 uses is an equality test or a `switch` arm.
  (Beware a naive grep here: `R_NEXT ->` in an arrow switch looks like arithmetic on `R_NEXT`.)

**Width:** ten constant values in one file. **Port difficulty:** Low. **Risk:** low, but it is the
kind of change that wants the full `xdk:installDist` run, since the interpreter is the only thing
that exercises these.

**Why it is worth its own PR.** It costs almost nothing and it closes a hazard that no amount of
review would reliably catch, because the mistake it prevents is invisible at the call site: both
protocols are `int`, so the compiler has nothing to say. Typing them properly - two enums - would
be better still, but the values are on the interpreter's hottest path and both are stored in `int`
fields throughout, so disjoint numbering buys most of the safety at none of the cost. **Do the
renumbering now; leave the enums for whenever the hot path is being reworked anyway.**

---

## E18 — Close the switches that CAN be closed; ratchet the ones that cannot

A survey of every `switch` with a throwing `default` - 488 of them, 57% of the codebase's 857
`IllegalStateException` throws. Sorted by whether the domain is closeable, because the answer
differs per family and lumping them together is why this has never been tackled:

| Domain switched on | Switches | Closeable? |
| --- | --- | --- |
| open values (`i`, `regId`, `getBitLength()`) | 162 | **No** - genuinely open; the default is correct |
| int constants (`getOpCode()`, `reg.flavor()`) | 135 | Only after the ints become enums (see E17) |
| enum constants (`format`, `getId()`) | 131 | **Sometimes** - see below |
| String literals | 78 | Needs a typed replacement (see E19) |
| type patterns | 11 | **Yes** - sealed + exhaustive switch, cheapest win |

### The enum family: a ratchet, not a `default` removal

The instinct is "it switches on an enum, so drop the default and let javac verify". That is wrong
for the biggest ones. `ConstantPool.disassemble` switches on `Constant.Format`, which has **107**
constants, and handles **104**. The three it omits are omitted *correctly*:

- `DeferredValue` and `CastType` - their `assemble()` methods **throw**; they are compiler-internal
  placeholders that are never persisted, so `disassemble` must never see them.
- `ResponseSender` - referenced nowhere in the tree at all. It cannot be deleted, because
  `Format.ordinal()` **is** the binary encoding (`out.writeByte(getFormat().ordinal())`), so
  removing it would shift every later format. Same reserved-slot situation as `OP_NEWC_T` in E15.

So the default is load-bearing and the switch cannot be closed. **But the invariant behind it can
be tested**, and that test is what would have caught the `TimeZone` bug (E14/row 32) years ago:

> Every `Format` whose constant class has a working `assemble()` must be handled by
> `ConstantPool.disassemble`.

That is derived from the code rather than hand-maintained, so it cannot drift the way the two
parallel switches did. `LiteralFormatPlumbingTest` already does this for the literal formats
specifically; generalising it to all 107 is a small, self-contained PR.

**Width:** one test. **Port difficulty:** Low. **Value:** it closes the *class* of bug that row 32
was an instance of, rather than the instance.

### The type-pattern family: 11 switches, closeable today

Eleven switches already dispatch on type patterns (`switch (constClass)`,
`switch (argRaw)`, `switch (m_arg)`). Where the switched type is sealed, the `default -> throw`
can be deleted and javac verifies exhaustiveness instead. This is the cheapest item in the whole
survey and it is the shape E14.5 used successfully in `appendNestedIdentity` and `DebugConsole`.

---

## E19 — Retire String dispatch for native methods

**76 `switch (method.getName())` sites carrying 542 `case "…"` labels.** Every native method
invocation resolves by hashing and comparing a String at run time, and every one of those 76
switches ends in a throwing default because the compiler cannot know the set is complete.

This is the purest instance of the pattern this campaign exists to remove: a closed set - the
native methods a template implements - expressed as open text.

**The shape of a fix** (not yet designed in detail, deliberately): the mapping from
`MethodStructure` to "which native" is fixed at link time, when `markNative()` runs, not at every
invocation. Resolving it once onto the `MethodStructure` - an index, or a handle to the
implementation - would turn 542 String comparisons per-invocation into a table lookup, and make
the "did I cover every native?" question answerable at build time rather than by a runtime throw.

**Width: large.** 76 sites across the template tree. **This one should NOT be attempted as a single
PR**; it is listed here so the measurement is recorded and so nobody starts it without knowing the
size. The right first step is one template converted as a worked example, exactly as E15 did.

---

## E20 — The two taxonomies: `Format` vs the Java class hierarchy

This one is not gratuitous, and understanding why matters for fixing it safely.

`Constant.Format` is the **wire tag**. `Constant.assemble` writes
`out.writeByte(getFormat().ordinal())`, so a format is a slot in the binary encoding. The Java
class hierarchy is the **behaviour-reuse axis**. Those are genuinely orthogonal, and in four places
they deliberately disagree:

| Subclass | extends | but its format is | not |
| --- | --- | --- | --- |
| `FormalTypeChildConstant` | `PropertyConstant` | `FormalTypeChild` | `Property` |
| `NativeRebaseConstant` | `ClassConstant` | `NativeClass` | `Class` |
| `RecursiveTypeConstant` | `TerminalTypeConstant` | `RecursiveType` | `TerminalType` |
| `CastTypeConstant` | `IntersectionTypeConstant` | `CastType` | `IntersectionType` |

Each reuses its parent's behaviour while needing its own slot on disk. That is a reasonable thing
to want. **The defect is that nothing marks where the two axes diverge**, so the codebase spells
the same intent two ways and they are not the same predicate:

```java
constId.getFormat() == Format.Property     // excludes FormalTypeChildConstant
constId instanceof PropertyConstant        // INCLUDES FormalTypeChildConstant
```

**154 `instanceof`/`case` sites** sit on those four superclasses, against **28** sites using the
format equality. Every one of the 182 picked a spelling, and nothing in the source says the choice
was load-bearing.

### This is live, not theoretical

Rewriting one such site - `ClassStructure`'s generic-parameter visitor - from the format check to
the "obviously equivalent" `instanceof` makes the Ecstasy library fail to compile with
`COMPILER-145: Unresolvable type parameter(s): OuterType`, because a formal type child is then
mistaken for a generic type parameter. The full Java unit suite stays green; only
`xdk:installDist` catches it.

### The incremental path

**Step 1 - pin it (one test file, landable today).** `ConstantFormatHierarchyTest` asserts the
divergence set is exactly those four, and separately that every constant whose format is
`isTypeable()` is an identity or pseudo constant. A fifth divergence then has to be a decision
rather than a surprise. Master-portable as-is: it names no type that master lacks.

**Step 2 - name the predicate (one small PR per pair).** The reason sites guess is that neither
spelling says what is meant. Give each divergent parent an intention-revealing query -
`isFormalTypeChild()`, `isNativeRebase()` - so a site can say which axis it is on instead of
encoding it in a choice of syntax.

**Step 3 - audit per superclass (four PRs, sized 75 / 63 / 8 / 8 sites).** With step 2 in place
each site becomes a local yes/no question, reviewable independently. Do the two small ones first;
they are 16 sites total and will calibrate the review rule for the large ones.

**Step 4 - stop it recurring.** New constant classes are rare, and step 1's ratchet already fails
on a new divergence. That is the durable part: the invariant is checked rather than remembered.

**Order matters.** Do NOT start at step 3. An audit without step 2 has to re-derive intent at every
site from surrounding context, which is exactly how the `COMPILER-145` failure above gets written.

---

## E21 — Type `getDefiningConstant()` as the identity/pseudo union

`TypeConstant.getDefiningConstant()` returns `Constant`, though its own javadoc says it always
produces an identity or a pseudo constant. Because the return type does not say so,
`TerminalTypeConstant` had grown a private `definingConstant()` / `asDefining()` layer that
re-asserted it at **33 call sites**, and `TypeConstant` carried another copy of the same check
inline.

**The fix.** Declare the union as the return type. `DefiningConstant` is a sealed interface
permitting exactly `IdentityConstant` and `PseudoConstant`, which is what the method actually
returns, so both re-derivation layers delete.

Two producers read their constant out of a `Constant`-typed field and cannot prove the narrowing
statically; they funnel through one checked `DefiningConstant.of()`. The reverse direction needs no
cast at all - an exhaustive switch over the union's two branches, both of which extend `Constant`:

```java
default Constant asConstant() {
    return switch (this) {
        case IdentityConstant constId -> constId;
        case PseudoConstant   constId -> constId;
    };
}
```

**What it does not do.** It does not remove the 53 casts on `getDefiningConstant()` results. Those
cast to *subtypes* of the union, and only pattern-matching removes them. The win is the deleted
workaround layer and an API that states its own invariant - measured honestly, the total cast count
moved by one.

**Soundness.** The invariant is `TerminalTypeConstant`'s own constructor guard, `Format.isTypeable()`:
every format it admits is implemented by a class in one of the two branches. Verified empirically
before relying on it, by instrumenting both producers and running a full `xdk:installDist` plus the
unit suite with the probe in place - nothing fired.

**Width:** 17 files. **Independent.** **Port difficulty:** medium, and it is mostly mechanical once
the return type changes; the compiler finds every site.

## E22 — The actual architecture: `ObjectHandle` is the calling convention

**Why the earlier items kept under-delivering, stated plainly.** Narrowing a producer's return type
(E21 and its predecessors) can only remove a cast when a *method result* is cast to *exactly* the
narrowed type. A census of all 2,879 casts in `javatools` says that is the minority case:

| what is being cast | count | share |
| --- | --- | --- |
| **a variable, field, or parameter** | **1,879** | **65%** |
| a method result | 1,000 | 35% |

For the 65%, the type was destroyed at the **signature and storage** layer, long before any
accessor. No return-type narrowing can reach them. That is structural, not an oversight, and it is
why E21 deleted a 33-site workaround layer yet moved the cast total by one.

### Where the mass actually is

**1,439 casts - half of every cast in `javatools` - target a handle type.** Their sources:

| source | count |
| --- | --- |
| an `ObjectHandle`-typed local or parameter (`hTarget`, `hArg`) | 987 |
| a field read | 192 |
| an element of an `ObjectHandle[]` | 191 |

Because the runtime's calling convention is untyped: **801** `ObjectHandle hTarget` parameters,
**323** `ObjectHandle hArg`, **245** `ObjectHandle[] ahArg`. `ClassTemplate` declares a **37-method
receiver-typed protocol** (`invokeAdd`, `invokeSub`, `invokeNative1`, …), every one taking
`ObjectHandle hTarget`, and the first act of nearly every override is to cast it back to the type
the template already knows it must be.

### Two shapes, both costed

**A - dispatch on the receiver.** Move the protocol onto the handle so `this` carries the type.
Already half-built: four `invokeNative*` entry points delegate this way, and `xRegEx` is the worked
example - **6 receiver casts to 1**.

- addressable: **322** receiver casts, in files where the handle is 1:1 with the template
- **blocked: 269**, where one handle serves many templates (`JavaLong` alone backs a dozen integer
  templates, 63 casts; `ViewHandle` 30; `IntNHandle` 29; `GenericHandle` 25). This blocker is
  structural - a shared handle cannot host one template's operator bodies.
- cost: the operation bodies move onto handle classes, which cuts against "dispatch on the
  receiver, operations on the template".

**B - make the template generic**, `ClassTemplate<H extends ObjectHandle>`, so overrides declare
`invokeAdd(Frame, IntNHandle, …)`.

- **184** override bodies become cast-free
- but **122** dispatch sites hold the template as `ClassTemplate<?>` and would each need an
  **unchecked** cast

B therefore trades ~322 *checked* casts for 122 *unchecked* ones. That is not obviously a win, and
unchecked casts are the more dangerous kind. My earlier one-line prediction that a generic template
"fails identically" was too strong - it does not fail, it trades - but it is not free either.

### Resolved: option C, prototyped and swept

The decision was taken and the work done, so this section is no longer a question.

**C - bind a typed handler per operator, per template** - was chosen because it is the only one of
the three that adds no unchecked casts and no raw types, and because the per-template table makes
the shared-handle case a non-issue: the binding belongs to the template, not to the handle class,
so `JavaLong` backing a dozen integer types stops mattering.

The objection to C was that it replaces a plain virtual call with a table lookup on the VM's hottest
path. **Measured, that objection is wrong**: megamorphic, with varying data, a virtual call plus two
casts costs 4.02-4.31 ns/op and the bound table 4.04-4.18 ns/op - within noise.

Swept across the tree: **115 operator overrides down to 22**, and the whole-tree cast total from
2,879 to 2,706. The 22 that remain are deliberate - `xRTType`'s operands are polymorphic, and the
`xChecked*` family is unreachable code, naming Ecstasy types that exist nowhere.

**To file this on master:** the seam and the `OperatorBinding` types are one PR that changes no
behaviour, since an unbound operator falls through to exactly today's path. Each template after that
is independent. Do the framework first.

---

## E23 — Bind natives to typed handlers instead of dispatching them by String

The concrete answer to E22: the runtime knows a native's identity and its argument types at link
time, and throws both away.

```java
markNativeMethod("names", STRING, null);      // STRING is {"text.String"}
```

This locates the `MethodStructure` by name and parameter type, then records `m_fNative = true` — a
boolean. Every invocation afterwards re-derives the identity with `switch (method.getName())` and
re-derives the argument type with a hand-written cast:

```java
case "names": StringHandle hPathString = (StringHandle) hArg;
```

**744 `case` labels across four protocols** do this: `invokeNativeN` 266, `invokeNative1` 193,
`invokeNativeGet` 186, `invokeNativeNN` 93, `invokeNativeSet` 6.

### The recipe

**1. Make the declared type carry its Java representation.** `STRING` names a `text.String` but not
the `StringHandle` representing it. `NativeType<H>` carries both, so the conversion can happen once,
as a checked `Class.cast`, instead of as an unchecked cast in each body.

**2. Return the structure `markNativeMethod` marked.** It already found it. Note it may return a
*synthetic override* it created rather than the method originally found, so the binder must key on
what came back, not on its own lookup.

**3. Bind in the fall-through, never before the switch.** The dispatch seam goes in the base
`ClassTemplate.invokeNative*` arm that previously only raised `"Unknown native method"`. A
template's existing switch still runs first, so an unmigrated native behaves exactly as before and
migration is per-native with no flag day. This is the property that makes the whole thing portable
in small pieces.

**4. Keep the arity out of the stored type.** `BoundN`/`BoundNN` dispatch from
`(hTarget, ahArg[], returns)`; per-arity typing lives in `bind` factories. A three-argument form is
one factory, not a new map. Measured `ahArg[i]` usage: 88 / 55 / 23 / 10 / 3 / 1 / 1, so zero-, one-
and two-argument forms cover nearly everything.

**5. Bind only where the existing body casts the argument UNCONDITIONALLY.** Where it tests with
`instanceof` first, the declaration does not settle the representation — see below. 21 sites in the
runtime cast an argument conditionally.

**6. For a type with several representations, name what they SHARE.** An Ecstasy `Int` is a
`JavaLong` or a `LongLongHandle` depending on magnitude, so no handle class describes it.
`IntegralValue` — `fitsLong(signed)` / `longValue()` — is implemented by both, and
`NativeType.ofShared` declares against it. This unblocks the **29** natives with an `INT` parameter.
It cannot be a *sealed* union (permits need one package; there is no named module and the two live
in different packages) and it must not be an abstract tier under `ObjectHandle` (`JavaLong` also
backs `Char`, `Bit`, `Boolean`). Name what the handle **carries**, not what it **is**.

### What it costs, measured

- A ten-label string switch is **6.1–6.2 ns/op**; the bound lookup plus two checked `Class.cast`
  calls is **4.7 ns/op**. The typed path is ~1.4 ns/op **faster** — a cached-hash lookup with an
  identity compare beats hashing and comparing a String, and the JIT elides the cast checks on a
  hit. A partially migrated template pays both, transiently.
- **The map key is a `Component`, whose `equals` is a deep recursive structural comparison.** It is
  never reached: lookups hit by reference (confirmed by running every binding against an
  `IdentityHashMap`), so the `key == ek` short-circuit answers first, and even on a collision
  `equals` short-circuits in `isBodyIdentical` on the cached identity-constant hash before the
  recursive half. Worth stating explicitly in any port — it looks alarming and is not.

### End state, and what survives

A migrated template loses its switch. Whether it loses the *method* depends on the template:

- `xOSFileStore`, `xRTRandom` — not holding a guard, so `invokeNative1` is **deleted**.
- `xOSStorage` — a service, so three overrides survive holding **only** the async context guard,
  with no dispatch left in them. That guard is a cross-cutting concern the binding does not address.

Done so far: `xOSStorage` (10 case labels to 0), `xOSFileStore`, `xRTRandom`. **Width: large — do
not attempt as one PR.** Steps 1–4 are one PR that changes no behaviour; each template after that is
independent.

---

## E24 — `null` as an absent argument, and restating what the code already resolved

Two adjacent smells, both found while building E23, both with a mechanical fix.

### `null` meaning "no filter"

`markNativeMethod(sName, asParamType, asRetType)` treats both arrays as optional filters for
choosing among overloads, and spells "no filter" as `null`. Across **392 declarations**:

| | count |
| --- | --- |
| `null` for **both** — the name alone identifies the method | **140** |
| `null` for the parameters only | 58 |
| `null` for the returns only | 38 |
| both named | 156 |

So **60%** of these call sites pass at least one argument that is not a value but an absence, and
the reader cannot tell from a call whether a `null` means "any" or "none" without opening the
callee.

**Why overloads alone do not fix it**, and why this ended up as `null`: both filters are
`String[]`, so a parameters-only overload is erasure-identical to a returns-only one. That is worth
stating in any port, because it is the reason the obvious fix was not taken.

**The fix is to name the combination.** A small record with intention-revealing factories —
`BY_NAME`, `params(...)`, `returns(...)`, `of(...)` — makes each call say which filters apply, and
confines the `null` the underlying lookup still expects to one place that documents it as the only
deliberately-nullable thing in the type. Callers write no nulls at all.

This shape is worth looking for generally: **an optional parameter whose absence is encoded as
`null`, where the overloads that would express it collide under erasure.**

### Making a caller restate what the code already resolved

`markNativeMethod` locates the `MethodStructure`, which carries the parameter types. A typed
binding that *also* asks the caller to name those types therefore rejects the 198 declarations that
pass `null` for them — for no reason, since the types are on the structure either way.

The rule: **ask the caller only for what nothing else knows.** Here that is the Java handle class;
the Ecstasy type comes from the resolved structure. Applying it doubled the population the binding
can reach and deleted the per-template type constants, so declarations read closer to what they
mean.

### On sealing, and whether to move packages for it

A sealed union lets a `switch` be verified exhaustive with no `default`. That only pays when
consumers must genuinely **distinguish** the members. When they instead want one operation from
whichever member they have, a plain interface with that operation is better: it removes the switch
entirely rather than checking it, and it needs no package surgery.

Both unions found here are the second kind, so **no package move is warranted**:

- **`IntegralValue`** (`JavaLong` | `LongLongHandle`) — consumers want the value, not the
  representation. Sealing would need `JavaLong` moved out of `runtime` into `template.numbers`,
  which would also be a lie: `JavaLong` backs `Char`, `Bit` and `Boolean` too.
- **The deferred handles** (`DeferredCallHandle` and its three subclasses) — already one package,
  so sealing is free, and still buys nothing: all 20 sites test the base and then call the virtual
  `proceed`/`addContinuation`. Nothing distinguishes the four.

Related and separable: `ObjectHandle.proceed` has a base body of
`throw new IllegalStateException("Not deferred")` and is guarded by `instanceof DeferredCallHandle`
at 20 sites — a partial method, which is a union hiding in a hierarchy. `compareTo` is the only
other one on `ObjectHandle`. Small, self-contained, and independent of everything above.

---

## E25 — Generify the delegate hierarchy (attempted, blocked, and why)

The best remaining yield-per-effort target by measurement, and it does not work yet. Recording the
attempt so the next person does not repeat it.

### The target

`xRTDelegate` declares a nine-method storage protocol - `extractArrayValueImpl`,
`assignArrayValueImpl`, `createCopyImpl`, `insertElementImpl`, `deleteElementImpl`,
`deleteRangeImpl`, `checkWrite`, `checkWriteInPlace`, `createCopy` - each taking a
`DelegateHandle`. Twenty implementations across the hierarchy open by casting it to the handle type
they actually store: **134 `(XHandle) hTarget` casts**, about 5% of every cast in `javatools`, in
one bounded hierarchy.

Unlike `ClassTemplate` (E22), the typed path exists. The `Impl` methods are `protected` and called
only from `xRTDelegate`'s own entry points, which already do the conversion once:

```java
public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
    DelegateHandle hDelegate = (DelegateHandle) hTarget;      // once, here
    ...
    : extractArrayValueImpl(frame, hDelegate, lIndex, iReturn);
}
```

So `xRTDelegate<H extends DelegateHandle>` with a `Class<H>` supplied by each subclass would let the
entry points hand the implementations an already-typed handle, with one checked `Class.cast` and no
unchecked casts anywhere. Only **15** external references would need an explicit `<?>`.

### What blocks it

**`xRTDelegate` is both the abstract base and the concrete Object-array template.** Its default
implementations construct a `GenericArrayDelegate`:

```java
return new GenericArrayDelegate(hDelegate.getComposition(), ahValue, mutability);
```

Under `xRTDelegate<H>` that does not compile, because the default cannot know `H` is
`GenericArrayDelegate` - and for every subclass it is not. `NativeTemplates.delegate()` returns
`xRTDelegate` as a live template, so the concrete role is real and cannot simply be removed.

**The prerequisite is a split**: an abstract generic base holding the protocol, and a concrete
`xRTGenericDelegate extends xRTDelegate<GenericArrayDelegate>` holding today's defaults, with the
template registration pointed at the latter. That is a larger change than the generification itself,
and it should land first and separately.

**A second, smaller obstacle** worth knowing: every handle class is a static nested class of its own
template - `DelegateHandle` inside `xRTDelegate`, `CharArrayHandle` inside `xRTCharDelegate` - so
each class names its own nested class in its own declaration. That does compile, but only qualified:
`class xRTCharDelegate extends xRTDelegate<xRTCharDelegate.CharArrayHandle>`. Verified working;
noting it because the unqualified form fails with a bare "cannot find symbol" that reads like the
class is missing.

### The split, sized

Confirmed the shape, which is better than it first looked. The 12 object-array method bodies are
~207 lines, and the hierarchy underneath is only a few roots:

| classes | root |
| --- | --- |
| 25 | `xRTView` |
| 9 | `LongBasedDelegate` |
| 6 | `ByteBasedDelegate` |
| 3 | `LongLongDelegate` |
| 1 each | `xRTFloat64Delegate`, `xRTCharDelegate`, `xRTStringDelegate`, `xRTSlicingDelegate` |

So making the moved methods abstract does **not** cost 23 implementations: one on `xRTView` covers
25 classes and one on `xRTSlicingDelegate` covers the rest. Both are fixed-size, and the runtime
already rejects mutation on them before the delegate is reached - verified: deleting from a slice
raises `SizeLimited: Fixed size array`, inserting raises `ReadOnly`, both well before
`deleteRangeImpl`.

The registration also needs repointing: `NativeTemplateRef.of("_native.collections.arrays.RTDelegate",
xRTDelegate.class)` would name the new concrete class, and `NativeTemplates.delegate()` /
`isDelegate()` follow.

**Do this edit by hand.** Two scripted attempts at it have now damaged `xRTDelegate.java` - the
second removed 791 lines where ~207 were intended, because brace-matching a file with this many
nested classes and multi-line signatures is not reliable enough to trust. Both were caught and
reverted, but the file is 1000+ lines with nested handle classes and the method boundaries do not
survive naive scanning.

### The split cannot rename the concrete class

Templates are discovered by scanning the template directory, and the Ecstasy class a Java template
serves is derived from its FILE NAME:

```java
String sSimpleName   = sName.substring(1, sName.length() - 6);   // xRTDelegate.class -> RTDelegate
String sQualifiedName = sPackage + sSimpleName;
mapTemplateClasses.put(sQualifiedName, Class.forName(sClass));
```

So a new `xRTGenericDelegate` maps to an Ecstasy `RTGenericDelegate`, which does not exist; the
registration loop skips a template whose Ecstasy class is missing, and it is never instantiated.
Repointing `RT_DELEGATE` at it compiles and then fails at run time.

**The concrete class must keep the name `xRTDelegate`.** The abstract base is what gets a new name -
`xRTDelegateBase` or similar - and the other delegates change their `extends` to it. That is the
opposite of the obvious direction, and cheaper: nine `extends` clauses and a new base holding the
protocol, rather than moving twelve method bodies out of a thousand-line file.

Verified the naming rule by reading `NativeContainer.scanNativeDirectory`; an abstract base is
skipped twice over, both because its Ecstasy class is absent and because the loop tests
`Modifier.isAbstract`.

### Status: the split is done for the three core storage methods

`xRTGenericDelegate` now holds the object-array implementations of
`extractArrayValueImpl`, `assignArrayValueImpl` and `createCopyImpl`; `xRTDelegate` declares them
abstract and is itself abstract; the binding to the Ecstasy class is declared with
`@NativeTemplate` rather than taken from the file name.

**It paid for itself on the first compile.** Making those three abstract produced three errors,
one per class that had been inheriting storage it does not own - the same shape as master bug 36,
found by the compiler rather than by a user.

### The remaining nine are not mechanical

Moving the other nine (`createDelegate`, `callEquals`, `compareIdentity`, `getPropertyCapacity`,
`setPropertyCapacity`, `fill`, `insertElementImpl`, `deleteElementImpl`, `deleteRangeImpl`) was
attempted and reverted. The move itself is fine - the fallout is not, and it needs a decision per
method rather than a sweep:

- **`createDelegate` is overloaded.** There are two, `(Container, TypeConstant, int)` and
  `(Container, TypeConstant, int, ObjectHandle[], Mutability)`. Only one is object-array specific;
  making the wrong one abstract produces errors that look unrelated.
- **Visibility differs.** Several classes already declare `callEquals` and `compareIdentity` with a
  wider visibility than `protected`, so a `protected abstract` declaration in the base is not
  overridable by them.
- **Some are already implemented where the fallout lands.** `xRTView` and `xRTSlicingDelegate`
  already define `getPropertyCapacity`, `setPropertyCapacity` and `fill`; adding them again is a
  duplicate-method error, so the fallout set has to be computed per class rather than assumed.
- **Five of the nine are not storage at all.** `callEquals`, `compareIdentity`, `fill` and the two
  capacity methods are derived operations that could be implemented generically over the storage
  protocol. Making them abstract forces every delegate to reimplement comparison and filling, which
  is worse than what is there now. They should be rewritten generically in the base, not moved.

So the sequence below still holds, but step 1 should be read as: move the four remaining STORAGE
methods, and rewrite the five derived ones over the storage protocol rather than moving them.

### Sequence

1. Split the concrete Object-array delegate out of `xRTDelegate`, leaving the base abstract.
2. Then generify, which is mechanical: 10 constructors, 9 protocol signatures, 20 overrides,
   15 wildcard sites.

Attempting step 2 alone gets roughly two thirds of the way and then stalls on the defaults.

---

## E26 — What is left of `unchecked` and `rawtypes`, and why

The build now makes fifteen javac lint categories fatal. Two remain off because they are not clean:
**92 `unchecked`** and **59 `rawtypes`** in `javatools`.

**First, a measurement warning.** javac caps reported warnings at 100 by default. The build's
`maxWarnings` property produces no `-Xmaxwarns` flag when set to 0, so it does NOT lift that cap -
it leaves javac's own. Counting under the cap gave 63/25 and moved as fixes landed, because
removing one warning let a suppressed one surface. Pass an explicit large value
(`-Porg.xtclang.java.maxWarnings=100000`) or the numbers are wrong.

### The raw types, by what is actually raw

| count | type | trivial? |
| --- | --- | --- |
| 11 | `CompletableFuture` | **no** - see below |
| 8 | `List` | mixed |
| 4 each | `VersionTree`, `Iterator`, `Function`, `Comparable` | mixed |
| 3 each | `Entry`, `Collection`, `ArrayList` | mostly yes |
| 2 each | `Map`, `LinkedHashMap`, `EnumMap`, `CaseManager` | `EnumMap` no |
| 1 | `WeakReference` | tied to `EnumMap` |

### The two that prove the point

Neither has a trivial fix, and in both cases the raw type is a symptom rather than the defect.

**`CompletableFuture` (11 sites).** The message base class declares

```java
public final CompletableFuture f_future;
```

and it is raw because different requests carry different result types - the same field is read as
`CompletableFuture<ObjectHandle>` at one call site and `CompletableFuture<ObjectHandle[]>` at
another. Parameterising the field is impossible without first making the request hierarchy generic
in its result type. **That refactor is the fix; the raw type is the symptom.** The four remaining
`unchecked` warnings on `new Response(...)` are downstream of the same root - the constructor cannot
infer `T` from a raw field.

**`EnumMap` (2 sites, plus the `WeakReference`).** `ServiceContext.setOpInfo` builds

```java
new EnumMap(category.getClass())
```

`EnumMap<K extends Enum<K>, V>` needs a concrete enum class, and the category's type varies per op,
so no type argument exists to write. The options are a plain `Map<Enum<?>, ...>` - typed, at the
cost of `EnumMap`'s array backing - or leaving it raw. This is a design choice, not a cleanup.

### What was trivially fixable, and done

`Response` was generic but used raw in the response queue, the `respond` parameter and the
`processResponses` local, all of which only ever call `run()`; `Response<?>` fits exactly. Done.

### The rule this suggests

Where a raw type has a trivial fix it is usually a local oversight. Where it does not, it is almost
always naming a place where a **type parameter is missing one level up** - a field, a base class, a
container - and the raw use is how the code copes. Those are worth reading as findings rather than
as lint debt: each one points at a hierarchy that has not been told what it holds.

**Filing:** the trivial fixes are one small PR. The `CompletableFuture` and `EnumMap` cases should
be filed as their own enhancements, since each is a hierarchy change with its own risk.

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

## E27 — The op-info cache: a raw `EnumMap` whose key silently names the value type

**Depends on:** nothing. Independently implementable.

### What is there

```java
private final Map<Op, EnumMap> f_mapOpInfo = new WeakHashMap<>();      // raw EnumMap

public Object getOpInfo(Op op, Enum<?> category) {
    EnumMap<?, ?> mapByCategory = f_mapOpInfo.get(op);
    ...
    WeakReference<?> ref = (WeakReference<?>) mapByCategory.get(category);
    return ref == null ? null : ref.get();
}

public void setOpInfo(Op op, Enum<?> category, Object info) {
    f_mapOpInfo.computeIfAbsent(op, (op_) -> new EnumMap(category.getClass()))
               .put(category, new WeakReference(info));
}
```

and at every call site:

```java
CallChain       chain     = (CallChain)       context.getOpInfo(this, Category.Chain);
TypeComposition clazzPrev = (TypeComposition) context.getOpInfo(this, Category.Composition);
TypeConstant    typePrev  = (TypeConstant)    context.getOpInfo(this, Category.Type);
```

### Why it cannot be typed as written

Each `Op` subclass declares its **own** `Category` enum:

```java
OpVar:        enum Category {Composition, Type}
OpInvocable:  enum Category {Chain, Composition}
```

`EnumMap<K extends Enum<K>, V>` needs a single concrete enum class, and the key class varies per
op, so there is no type argument to write - which is why it is raw. That is the honest constraint,
and it is why this row sat as "a design choice, not a cleanup".

But the real defect is not the `EnumMap`. It is that **each category constant already implies a
value type** - `Chain` means `CallChain`, `Composition` means `TypeComposition`, `Type` means
`TypeConstant` - and that correspondence exists only in the caller's head and its cast.
`setOpInfo(this, Category.Chain, someTypeComposition)` compiles today.

### The design

Stop encoding the category as an enum and let the key carry its value type - the same shape as
`NativeType<H>` in the native binding work:

```java
/**
 * A key into the per-op info cache. Each constant names both the slot and the type of value cached
 * in it, so the cache needs no cast and cannot be asked for, or given, the wrong type.
 */
public record OpInfoKey<V>(int index, String name, Class<V> type) {
    public OpInfoKey {
        requireNonNull(name);
        requireNonNull(type);
    }
    public static <V> OpInfoKey<V> of(int index, String name, Class<V> type) {
        return new OpInfoKey<>(index, name, type);
    }
}
```

Declared once per op class, beside the ops that use them:

```java
// OpInvocable
protected static final OpInfoKey<CallChain>       CHAIN       = OpInfoKey.of(0, "chain", CallChain.class);
protected static final OpInfoKey<TypeComposition> COMPOSITION = OpInfoKey.of(1, "composition", TypeComposition.class);

// OpVar
protected static final OpInfoKey<TypeComposition> COMPOSITION = OpInfoKey.of(0, "composition", TypeComposition.class);
protected static final OpInfoKey<TypeConstant>    TYPE        = OpInfoKey.of(1, "type", TypeConstant.class);
```

The API becomes generic in the value:

```java
public <V> V getOpInfo(Op op, OpInfoKey<V> key);
public <V> void setOpInfo(Op op, OpInfoKey<V> key, V info);
```

and every call site loses its cast:

```java
CallChain chain = context.getOpInfo(this, CHAIN);          // no cast, and no way to get this wrong
context.setOpInfo(this, CHAIN, clazz);                     // now a compile error
```

### Storage: keep the array, drop the enum

`EnumMap` was the right instinct - it is an array indexed by ordinal, and this is the op dispatch
path. Keep exactly that property without the enum by using the key's own `index`:

```java
private final Map<Op, WeakReference<?>[]> f_mapOpInfo = new WeakHashMap<>();
```

`get` is an array index and a `Class::cast`; `set` grows the slot array on demand. This is **faster**
than the current code, which does an `EnumMap.get` plus an unchecked `WeakReference` cast, and it
removes the per-op `EnumMap` allocation in `computeIfAbsent`.

The `Class<V>` in the key is what makes the read checked rather than unchecked, and it is affordable
here in a way it is not in the delegate hierarchy: this cast is not erased away by a bridge method,
so it is a real check that would otherwise be missing.

### Weak-reference semantics are unchanged

Values stay behind `WeakReference` for the reason the current comment gives - the cache is keyed by
`Op` in a `WeakHashMap` and must not retain compositions or chains. The array holds
`WeakReference<?>`; `getOpInfo` resolves and casts through `key.type()`.

### Migration

Mechanical and per-op-class, one class at a time:

1. Add `OpInfoKey`, and the generic `get`/`setOpInfo` overloads **alongside** the existing
   `Enum<?>` ones so nothing breaks.
2. Convert one op class: replace its `Category` enum with typed keys, drop the casts at its call
   sites.
3. When no caller of the `Enum<?>` overloads remains, delete them, the raw field, and the
   `EnumMap` import.

Step 2 is where the value shows up: `OpInvocable` and `OpVar` between them account for the casts,
and each conversion is a handful of lines.

### Verification

There is no behavioural change to assert, so the proof is the compiler: after step 2,
`context.setOpInfo(this, CHAIN, clazz)` where `clazz` is a `TypeComposition` must fail to compile.
Worth adding as a negative test in the same style as the existing audit examples.

## E28 — `compareIdentity` is a binary method wearing a unary signature

**Depends on:** nothing. Independently implementable. Closely related to master bugs 37 and 38.

### The complaint, stated precisely

```java
// ClassTemplate
public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
```

Every delegate implements this by casting **both** arguments to its own handle type:

```java
CharArrayHandle h1 = (CharArrayHandle) hValue1;
CharArrayHandle h2 = (CharArrayHandle) hValue2;
```

This signature promises to accept any two handles and then throws `ClassCastException` for most
pairs. That is not a typing accident - it is a **partial function with a total signature**, and it
produced master bugs 37 and 38.

### Why generics cannot fix this, honestly

The instinct is `compareIdentity(H h1, H h2)` on `xRTDelegate<H>`. It does not work, and it is worth
recording why so nobody re-attempts it:

The only caller is `xRef.CompareReferents`, which holds two `ObjectHandle`s and obtains the template
from the *first* one:

```java
hArray1.getDelegate().getTemplate().compareIdentity(hArray1.getDelegate(), hArray2.getDelegate());
```

There is no static relationship between "the template derived from handle 1" and "the type of
handle 2". Expressing that needs a dependent type - "these two values have the same runtime type" -
which Java has no way to state. Any `H` written here is bound by handle 1 alone, and handle 2 is
still an unrelated `DelegateHandle`. This is the classic **binary method problem**, and it is
genuinely not solvable by parameterizing the receiver.

So the runtime test cannot be removed. What can be removed is the *lie*.

### What is achievable, and worth doing

Make the operation total, and move it to the value that knows its own type:

```java
// DelegateHandle
public boolean isIdenticalTo(DelegateHandle that);

// CharArrayHandle
@Override
public boolean isIdenticalTo(DelegateHandle that) {
    return that instanceof CharArrayHandle other
        && Arrays.equals(m_achValue, other.m_achValue);
}
```

Three things improve, none of them cosmetic:

1. **The signature stops lying.** `isIdenticalTo` returns a `boolean` for every argument. There is
   no input for which it throws, so there is no bug 37 to find.
2. **The dispatch is the language's, not a hand-rolled one.** Today the receiver is chosen by
   `getTemplate()` on handle 1 and the "type check" is a cast. As a virtual method on the handle,
   the receiver is chosen by the JVM and the check is an `instanceof` the compiler can see.
3. **It is where the data is.** The handles hold the storage (`m_achValue`, `m_abValue`,
   `m_alValue`); the template holds none. Asking the template about the handle's contents is
   already backwards - see E29 for the same inversion in `ByteView`.

The `instanceof` remains, and that is the honest end state: one checked test, in the one place the
concrete type is known, instead of two unchecked casts in a method that should never have accepted
those arguments.

### Why not generify `ObjectHandle` itself

The self-typed (CRTP) form is the textbook answer to a binary method, and it does work in principle:

```java
abstract class ObjectHandle<SELF extends ObjectHandle<SELF>> {
    public abstract boolean isIdenticalTo(SELF that);
}
class CharArrayHandle extends DelegateHandle<CharArrayHandle> { ... }   // no cast at all
```

Every per-delegate cast disappears; the one dynamic entry point (`xRef.CompareReferents`, holding
two `ObjectHandle<?>`) needs a single unchecked crossing, exactly like `xRTDelegate.narrow()`.

It is still the wrong change, and the reason is measured, not aesthetic:

| | `ObjectHandle<SELF>` | `DelegateHandle<SELF>` |
| --- | --- | --- |
| bare tokens in `javatools` | **4,120** | 272 |
| files touched | **394** | 36 (6 outside the arrays package) |
| `ObjectHandle[]` / `DelegateHandle[]` | **718** | **0** |
| uses as a type argument | 99+ | 1 |

**Correction (verified, not assumed):** the arrays are *not* the blocker. An earlier draft of this
section claimed `ObjectHandle[]` would become a raw array plus a suppression at all 718 sites. That
is wrong. Java forbids creating `List<String>[]`, but an **unbounded wildcard** type is reifiable,
so `new ObjectHandle<?>[10]` is legal and compiles clean under `-Xlint:all`:

```java
static abstract class OH<SELF extends OH<SELF>> { abstract boolean same(SELF o); }
OH<?>[] arr = new OH<?>[10];     // compiles, no warning, no suppression
```

So all 718 become `ObjectHandle<?>[]` mechanically. The cost is churn, not lost type safety.

What remains against it is scale and yield: ~4,120 sites across 394 files, plus CRTP plumbing
through 101 handle subclasses (intermediates such as `ByteArrayHandle` must themselves stay
parameterized so `BitArrayHandle extends ByteArrayHandle<BitArrayHandle>` can bind), to make
cast-free roughly twenty methods that a single `instanceof` already makes *safe*. The win is real
but thin, and it touches every file in the runtime at once - which is precisely the kind of change
that cannot be reviewed or bisected.

A wrapper class - `ObjectHandleArray` instead of `ObjectHandle[]` - does not change this arithmetic,
because there is no generic-array problem to solve. It is a separate proposal with separate merits
(a named type for the calling convention, with methods and invariants instead of a bare array), and
one serious cost: it adds an object and an indirection to frame registers and argument passing,
which is the hottest path in the interpreter. Worth considering on its own terms, not as a way to
enable `ObjectHandle<T>`.

### Attempted 2026-08-31, and why it was reverted

`DelegateHandle<SELF>` was implemented far enough to compile down to two errors, and those two
errors are the finding. **The handle hierarchy cannot express a self type**, for a concrete reason:

```
ByteArrayHandle          <- instantiated directly (ByteBasedDelegate, 2 sites)
   BitArrayHandle        <- extends it, and is a template handle type in its own right
```

`xRTInt8Delegate` and `xRTUInt8Delegate` bind `ByteArrayHandle` **itself** as their handle type,
while `BitBasedDelegate` binds `BitArrayHandle`, a narrowing of it. A self type must satisfy
`H extends ByteArrayHandle<H>`, which plain `ByteArrayHandle` does not - it would have to be its own
`SELF` *and* the supertype of another binding. That is the standard CRTP wart: a class that is at
once a concrete leaf and an intermediate. Java has no way to write it.

The escape is to relax the template bound to `xRTDelegate<H extends DelegateHandle<?>>` and keep the
self type only on the handle hierarchy. That works, and it costs **262 sites** turning bare
`DelegateHandle` into `DelegateHandle<?>` across the package and six files outside it - to delete
roughly 23 casts that the `instanceof` guards already make *safe*. The guards fixed the actual
defect (master bugs 37 and 38); CRTP would only change their syntax.

**Conclusion: the CRTP form is not worth doing - but the diagnosis was wrong about why.** The cost
was never `ByteArrayHandle`. It was making `DelegateHandle` *itself* generic, which forces all 273
bare references to become `DelegateHandle<?>`. `ByteArrayHandle` merely made that visible first.

### What does work (prototyped and verified 2026-08-31)

Leave the handle hierarchy alone and put the self type on a one-method capability interface. Then
`DelegateHandle` stays non-generic and the `<?>` churn is **zero**:

```java
interface SameAs<SELF> { boolean sameAs(SELF that); }

// DelegateHandle - NOT generic
@SuppressWarnings("unchecked")
public final boolean isIdenticalTo(DelegateHandle that) {
    return this == that
        || (getClass() == that.getClass() && ((SameAs<DelegateHandle>) this).sameAs(that));
}

class CharArrayHandle extends DelegateHandle implements SameAs<CharArrayHandle> {
    public boolean sameAs(CharArrayHandle that) { ... }        // no cast
}
```

`ByteArrayHandle` stops being a problem entirely: it implements `SameAs<ByteArrayHandle>` once, and
`BitArrayHandle` overrides `sameAs(ByteArrayHandle)` with a single narrowing - which
`getClass() == that.getClass()` has already proved safe. A class cannot implement the interface
twice with different arguments, and it does not need to.

The unchecked cast is backed by two things, not hope: the `getClass()` equality immediately before
it, and a compiler-emitted bridge that checkcasts anyway -

```
public boolean sameAs(java.lang.Object);
     2: checkcast     #8    // class CharArrayHandle
     5: invokevirtual #19   // Method sameAs:(LCharArrayHandle;)Z
```

Verified end to end in a standalone prototype: compiles clean under `-Xlint:all`, and every
previously-crashing pair (`byte` vs `bit`, `char` vs `slice`) answers `false`.

**Cost:** one interface, `sameAs` on ~19 handles, one narrowing in `BitArrayHandle`, and nine
templates lose their `compareIdentity` override. **No** type parameter on `DelegateHandle`, no `<?>`
anywhere, and the operation becomes total - which is what makes bugs 37 and 38 unwritable rather
than merely fixed.

Handle classes are pure Java internals - `NativeContainer` binds Ecstasy names from
`Class<? extends ClassTemplate>`, never from handle class names, and no handle name appears in any
string or reflective lookup - so none of this is visible to XTC or affects language compatibility.

### Implemented on the working branch 2026-08-31

Done for the delegate family: `SameAs` added, `xRTDelegate.compareIdentity` is a `final` forwarder,
nine template overrides deleted, eight handles gained a cast-free `sameAs`, and
`LongLongDelegate`'s override turned out to be byte-identical to `LongBasedDelegate`'s on the same
handle and simply disappeared. Handle casts in the package: 142 -> 128. 671 tests, 0 failures.

### How far the same defect actually reaches - measured, and it stops here

Thirteen templates outside this package open `compareIdentity` the same way, so the obvious next
step is to lift `SameAs` to `ObjectHandle`/`ClassTemplate`. **That is not a bug fix, because none of
the other twelve is reachable.** The delegate family was uniquely exposed for a specific reason:
one Ecstasy type maps to *several* Java handle classes there - a `Char[]` may be a
`CharArrayHandle`, a `SliceHandle`, or a `ViewHandle` - so a legal comparison of two values of the
same Ecstasy type can hand a template a handle it did not create.

Everywhere else the mapping is one-to-one. Sweeping every template that casts both operands, against
the subclasses of the type it casts to:

| template | casts to | subclasses of that type |
| --- | --- | --- |
| `xString` | `StringHandle` | none |
| `xTuple` | `TupleHandle` | none |
| `xArray` | `ArrayHandle` | none |
| `BaseBinaryFP` | `FloatHandle` | none |
| `BaseDecFP` | `DecimalHandle` | none |
| `BaseInt128` | `LongLongHandle` | none |
| `xUnconstrainedInteger` | `IntNHandle` | none |
| `xBit`, `xChar`, `xConstrainedInteger` | `JavaLong` | none |
| `xEnum` | `EnumHandle` | `BooleanHandle` - still an `EnumHandle`, so the cast holds |

With no subclass there is no second representation, so the cast can only fail if two *different*
Ecstasy types reach one template. That does not happen: the runtime compares templates before
asking either of them. Verified directly - `Object a = 'x'; Object b = "y"; &a == &b` answers
`False` on clean master rather than raising, as does the `Int`/`String` pair.

**So lifting `SameAs` to `ObjectHandle` is a signature-honesty change, not a fix.** It is still
worth doing - twelve methods would stop promising to accept pairs they cannot handle - but it should
be proposed and reviewed as a design change, and it must not be bundled into a crash fix. Filed as
this row; deliberately kept out of PR #564.

*(Superseded by the attempt recorded above: `DelegateHandle<SELF>` looked like the contained
version - 272 tokens, no array uses, six external files - but the hierarchy will not accept a self
type. The forwarder below is still worth doing, without the type parameter.)*

### Migration

`ClassTemplate.compareIdentity` stays (other templates use it) and, for delegates, forwards:

```java
// xRTDelegate
@Override
public final boolean compareIdentity(ObjectHandle h1, ObjectHandle h2) {
    return h1 instanceof DelegateHandle d1 && h2 instanceof DelegateHandle d2
        && d1.isIdenticalTo(d2);
}
```

One `final` forwarder, then each handle gains `isIdenticalTo` and each template loses its
`compareIdentity`. Per-handle, independently testable, and it deletes the guarded-cast boilerplate
added for bug 38 rather than leaving it in twenty places.

## E29 — Capability interfaces ask the template about the handle's own data

**Depends on:** nothing. Same inversion as E28.

`BitView` and `ByteView` are queried on the template and then handed the handle back:

```java
DelegateHandle hDelegate = hArray.getDelegate();
ClassTemplate  tDelegate = hDelegate.getTemplate();
if (tDelegate instanceof ByteView hView) {
    return hView.getBytes(hDelegate, ofStart, cSize, fReverse);
}
throw new UnsupportedOperationException("unsupported delegate: " + hDelegate);
```

`hDelegate` and `tDelegate` are obtained separately, so nothing relates the template's handle type
to the handle being passed. Every implementation therefore re-opens the handle by casting - about
35 casts across eight classes - and a mismatched pair is an `UnsupportedOperationException` at run
time rather than a compile error. Generifying `ByteView<H>` does **not** help: callers would hold
`ByteView<?>` and could not pass their `DelegateHandle` at all.

The fix is the same inversion as E28 - put the capability on the handle, which owns the bytes:

```java
// ByteArrayHandle and friends
public byte[] getBytes(long ofStart, long cBytes, boolean fReverse);
public byte   extractByte(long of);
public void   assignByte(long of, byte bValue);
```

Then the call site is `hDelegate.getBytes(ofStart, cSize, fReverse)` - no capability query, no cast,
and "this delegate does not support bytes" becomes a class that does not implement the interface,
which the compiler enforces at every call. The `UnsupportedOperationException` and its
"unsupported delegate" message disappear with it.

Note the `// TODO: add an "assignBytes" method to the ByteView interface` already sitting in
`xByteArray.setBytes`, where the caller loops calling `assignByte` one byte at a time because the
interface exposes no bulk form. Moving the capability onto the handle is the point at which that
TODO becomes a one-line method on the class that owns the array.

## E30 — The storage operations are on the wrong object, and the type parameter is the symptom

**Depends on:** nothing. Supersedes the type-parameter half of E25. Same inversion as E28 and E29.

### The complaint

After E25, `xRTDelegate` carries a type parameter and callers must write `xRTDelegate<?>`:

```java
ClassTemplate tDelegate = hDelegate.getTemplate();
((xRTDelegate<?>) tDelegate).fill(hDelegate, cSize, hValue);
```

Wildcards spread from there, and two of them cannot be removed at all -
`NativeTemplateRef` keys on `Class<T>`, and `Class<xRTDelegate<?>>` is unwritable in Java, because
a class literal is always raw in its own type argument. Reaching for `Class::cast` does not help;
the type simply cannot be named.

That friction is a signal, not an inconvenience to be absorbed.

### Why it happens

The wildcard exists because a *third party* is being asked to operate on a value whose type it
cannot see. `xArray` holds a `DelegateHandle`, fetches its template, and hands the handle back to
it. Nothing relates the two, so the type argument has nowhere to come from - exactly the shape
already recorded for `ByteView` in E29 and for `compareIdentity` in E28.

### Measured - and the first measurement was wrong

An initial pass counted *field* access and reported 105 of 132 bodies as needing only the handle.
That metric was wrong: it missed **unqualified calls to overridable template methods**
(`setValue(...)`, `isSet(...)`), which are the whole point. Re-measured against every non-static
`public`/`protected` method the templates declare:

| | count |
| --- | --- |
| bodies that are pure handle state | 89 |
| bodies calling an **overridable template method** | 23 |

The 23 call `isSet`, `setValue`, `getValue`, `storage`, `makeElementHandle`, `makeBitHandle`,
`getBits`, `reverse`. These are not misplaced storage logic. They are an **element codec**, and the
template owns them for a real reason: one handle class serves many element types.

| handle class | templates using it |
| --- | --- |
| `ViewHandle` | 12 |
| `LongArrayHandle` | 7 (`Int16/32`, `UInt16/32`, `Nibble`, `Int64`, `Int128`) |
| `BitArrayHandle` | 3 |
| `ByteArrayHandle` | 2 |
| `CharArrayHandle`, `DoubleArrayHandle`, `GenericArrayDelegate`, `SliceHandle`, `StringArrayHandle` | 1 each |

A `LongArrayHandle` holding `Int16`s packs four per `long`; one holding `Int64`s packs one. The
handle cannot answer `extractValue` because **it does not know its own element width** - the
template does. So the naive form of this row is wrong for roughly two thirds of the storage, and
`<?>` is the price of a real constraint rather than of a misplacement.

So the storage operations are already pure functions of the handle. The handles hold the arrays -
`m_abValue`, `m_achValue`, `m_alValue`, `m_ahValue` - and the template holds none of it. Asking the
template to operate on the handle's array was backwards from the start.

### The design

Move the storage protocol onto `DelegateHandle` and delete the type parameter:

```java
// DelegateHandle - no type parameter anywhere
public abstract int  extractValue(Frame frame, long lIndex, int iReturn);
public abstract int  assignValue(Frame frame, long lIndex, ObjectHandle hValue);
public abstract void insertElement(ObjectHandle hElement, long lIndex);
public abstract void deleteElement(long lIndex);
public abstract void deleteRange(long lIndex, long cDelete);
public abstract DelegateHandle fill(int cSize, ObjectHandle hValue);
```

Call sites become plain virtual dispatch:

```java
hDelegate.fill(cSize, hValue);              // was: ((xRTDelegate<?>) template).fill(h, ...)
hDelegate.extractValue(frame, lIndex, iReturn);
```

What this deletes, rather than relocates:

- the `<H>` parameter on `xRTDelegate`, and every `xRTDelegate<?>` that followed from it - including
  the two that cannot currently be written at all;
- `narrow()` and its `@SuppressWarnings("unchecked")`;
- the `fill`/`fillImpl` wrapper pair, and the same split on the other five - the erased wrapper only
  exists to cross from the template's view to the handle's;
- the remaining `(XxxHandle) hTarget` casts, because the receiver *is* the handle.

The template keeps what is genuinely template-scoped: `createDelegate`, `createBitViewDelegate`, and
the Ecstasy-facing native dispatch. Those need `f_container` and a `TypeComposition`, and they are
factories - the one thing a template legitimately is.

### Relationship to E25

E25 is not wasted: parameterizing the hierarchy is what made the storage protocol explicit and
forced every implementation to state which representation it serves, which is how master bugs 36,
37 and 38 were found. It was a scaffold. This row removes it, and the end state is *more* typed than
either the original or E25 - no wildcards, no unchecked cast, no erased wrapper layer, and a
compiler-enforced "this handle implements the storage protocol" in place of "this template can be
cast to something that operates on that handle".

### What would actually make this work

The blocker is that the handle hierarchy is coarser than the element types. Make it match, and every
handle becomes 1:1 with a template:

```java
abstract class LongArrayHandle extends DelegateHandle { long[] m_alValue; }   // storage only
final class Int16ArrayHandle  extends LongArrayHandle { /* codec: 4 per long */ }
final class Int64ArrayHandle  extends LongArrayHandle { /* codec: 1 per long */ }
```

Then the codec lives with the storage it describes, the whole protocol moves onto the handle, and
the type parameter, `narrow()`, its suppression and every `<?>` are deleted rather than relocated.

**Cost, stated honestly:** roughly fifteen new handle classes, each small (a constructor plus the
codec methods its template used to hold). `LongArrayHandle`, `ByteArrayHandle` and
`xRTView.ViewHandle` become abstract, which also removes the concrete-and-extended wart that
defeated the self-typed attempt recorded above.

**Do not start this as a sweep.** Prove it on `LongArrayHandle` alone - the worst case, seven
templates - and confirm the codec really is per-element-type and not per-instance. If that one works
the rest are mechanical; if it does not, the row is dead and `<?>` stays, which is an acceptable
answer.

### Migration for the part that is already safe

Independent of the above, the five 1:1 handles (`CharArrayHandle`, `DoubleArrayHandle`,
`GenericArrayDelegate`, `SliceHandle`, `StringArrayHandle`) can take their operations today, and
`SameAs` (E28, implemented) is the worked precedent - it was clean precisely because identity is
pure handle state with no codec in it. That alone does not remove the type parameter, so it buys
readability, not the `<?>` reduction.
