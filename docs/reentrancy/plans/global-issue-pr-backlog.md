# Global Issue And PR Submission Backlog

This is a filing-prep document only. Nothing here has been pushed to GitHub,
and no issue or PR should be filed without manual review.

This file is deliberately broader than
[master-issue-submissions.md](master-issue-submissions.md). The master file is
for the 18 critical bugs that should be filed against `origin/master` as small
fixes with deterministic red-on-master tests. This file tracks the next layer:
reentrancy enablement, compiler/LSP blockers, diagnostic authority, nullness
contracts, duplication/invariant cleanup, and reviewable modernization PRs.

Authoritative status lives in
[../must-audit-backlog.md](../must-audit-backlog.md). If this file disagrees
with the board, update the board first and then reconcile this filing backlog.

## Relationship To The Master Bug File

| Track | Document | Purpose |
| --- | --- | --- |
| Category A master bugs | `master-issue-submissions.md` | Small critical fixes, each with a master-portable patch and red-on-master proof. |
| Reentrancy PR sequence | `github-issue-breakdown.md` | Large branch split into reviewable implementation PRs. |
| Global issue/PR backlog | this file | Broader issues and PR-sized tracks that are not all single master bugs but block a reusable compiler/runtime. |

## Reviewer Framing

The broader work is not a request to modernize Java for taste. The problem is
that the codebase often represents ownership, absence, diagnostics, lifecycle,
and payload shape through mutable convention rather than through Java types.
That convention breaks down when a JVM hosts more than one compiler request,
container, test run, LSP request, debugger evaluation, or JIT classloader.

Several rows below will not have a single "red on master" unit test in the same
way as the 18 master bugs. Their proof standard should be different:

- source-shape gates for banned patterns,
- focused behavioral tests for representative sites,
- request/session identity tests for compiler/LSP diagnostics,
- owner-sweep/world snapshot tests for runtime state,
- compile-time checks from generics, sealed types, records, and nullness
  annotations,
- before/after IntelliJ or javac warning counts when the issue is tool-facing.

## Suggested Filing/PR Order

Status of the type-system proof:

- **Sealed hierarchy proof:** implemented for the selected closed families.
  Current branch has 48 sealed declarations, exactly one main-source
  `non-sealed` hatch, and the old worst-case `TerminalTypeConstant` family no
  longer carries 48 blind defining-constant casts. Open sealing work is
  opportunistic cascade conversion, `Op` (package split), and the deliberately
  unsealed `TypeComposition` root.
- **Generic/typed-boundary proof:** audited and designed, not globally
  implemented. The generics audit classifies 288 `unchecked`/`rawtypes`
  warnings and shows that the proposed typed boundaries would remove about 215
  of them, but the implementation remains split into the PR rows below.

| # | Candidate issue or PR | Type | First reviewable slice | Evidence source | Readiness |
| --- | --- | --- | --- | --- | --- |
| 1 | Request-owned diagnostic session | Compiler/LSP blocker | Add `DiagnosticEvent`, `DiagnosticContext`, `DiagnosticSink`, and an `ErrorListener` bridge without changing rendering. | `logging-diagnostics-audit.md` | Needs design PR. |
| 2 | Explicit diagnostic probes instead of silent `BLACKHOLE` | Compiler/LSP blocker | Wrap speculative probes with named reasons; add a source-shape inventory test for direct `BLACKHOLE`. | `logging-diagnostics-audit.md` | Needs source classification. |
| 3 | Stop deep `ErrorList` authority | Compiler/LSP blocker | Route direct `new ErrorList(...)` sites through request/session-owned branches where possible. | `logging-diagnostics-audit.md` | Needs per-site PRs. |
| 4 | Nullness annotations for required owner/API boundaries | API discipline | Add high-signal `@NotNull`/`@Nullable` on guarded constructors, owners, and public helpers; keep runtime guards. | `nullness-annotation-audit.md` | Ready as low-risk first wave. |
| 5 | Typed absence/failure results instead of overloaded null | API discipline | Pick one family, e.g. eval compile or runtime reflection parse, and split "not found" from "failed". | `nullness-annotation-audit.md` | Needs per-family tests. |
| 6 | Empty immutable collections for "none" | API discipline | Convert cold collection getters where null only means empty; add read-only/empty-contract tests. | `array-element-exposure-audit.md`, `nullness-annotation-audit.md` | Should follow collection audit rows. |
| 7 | Repo-owned logging facade | Diagnostics | Add facade and sink shape only; no broad print-site migration. | `logging-strategy.md` | Ready as standalone PR after must fixes. |
| 8 | Side-effect-free display path | Debugger/LSP blocker | Add display contract and one small migrated family; gate banned lazy-building calls. | `plans/side-effect-free-tostring.md` | Needs staged PRs. |
| 9 | Remove `ConstantPool.withPool(...)` completely | Ownership cleanup | Replace remaining scoped bridges with explicit owner parameters, then delete the bridge. | `ambient-context-audit.md`, `scoped-value.md` | Should-fix before reentrancy claim. |
| 10 | Complete world X-ray diagnostics | Reentrancy proof | Add fiber/frame/register snapshot slice after current container diff slice. | `ownership-diagnostics.md` | Slice 1 already done. |
| 11 | Type service/fiber response payloads | Runtime type safety | Replace raw `CompletableFuture`/response payloads in one service/fiber path with a typed or sealed payload. | `lint-parallelism-risk-audit.md` | Needs design and tests. |
| 12 | Type op-info cache keys | Runtime/metadata cache safety | Introduce a typed `OpInfoKey` or equivalent for one cache family. | `lint-parallelism-risk-audit.md` | Needs proof of owner confinement. |
| 13 | Type native-template reflection | Runtime startup safety | Replace raw `Class` template maps with `Class<? extends ClassTemplate>` and `asSubclass(...)`. | `lint-parallelism-risk-audit.md` | Good small PR candidate. |
| 14 | Fallthrough and legacy state-machine cleanup | Control-flow safety | Convert one proven non-cascade switch to arrow/extracted transitions; shrink suppressions. | `lint-parallelism-risk-audit.md`, `modern-java-syntax-audit.md` | Opportunistic. |
| 15 | Duplicate invariant extraction | Maintainability/reentrancy | Use IntelliJ duplicate-code findings to extract one semantically identical helper or typed record. | `modern-java-syntax-audit.md` | Needs report export/source reading. |
| 16 | Resident compiler/LSP request ownership model | Architecture | Define request-owned compiler state, diagnostics, pools, and cancellation surface before implementation. | `presentation.md`, `compiler-lexer-parser-this-escape.md` | Needs design doc. |
| 17 | `xvm-reentrant` vehicle decision | Project vehicle | Keep upstream PRs small; create/maintain a reentrant integration repo only as the proof branch/fallback vehicle. | `github-issue-breakdown.md` | User decision. |

## 1. Request-owned diagnostic session

**Issue title:** Make compiler/runtime diagnostics request-owned instead of
listener-owned.

**Status/category:** Should fix; compiler/LSP blocker.

**Explanation:** A resident compiler or LSP cannot rely on diagnostics that are
owned by whichever parser, type helper, runtime helper, or linker happened to
allocate an `ErrorList`. The host needs one request-owned diagnostic session
with URI/document version, phase, module, owner, container, service/frame, and
cause information. `ErrorListener` can remain as a compatibility bridge, but it
should not be the primary diagnostic authority.

**Evidence:** `logging-diagnostics-audit.md` shows `ErrorListener` has no
request id, owner id, container id, document version, phase id, related spans,
structured context, or Java cause field.

**Failure mode:** A single compiler decision can produce source diagnostics,
suppressed branch diagnostics, stdout/stderr messages, and Java exceptions with
no shared identity. An LSP client cannot know which document version or request
the message belongs to once incremental compiles overlap.

**Minimal first PR:** Add value types only: `DiagnosticEvent`,
`DiagnosticContext`, `DiagnosticSink`, `SourceSpan`, and an
`ErrorListenerBridge`. Do not migrate every call site in the first PR.

**Proof/tests:** Unit tests should assert event fields and bridge conversion.
Console rendering remains a subscriber or adapter.

## 2. Explicit diagnostic probes instead of silent `BLACKHOLE`

**Issue title:** Require every suppressed compiler diagnostic probe to name its
reason.

**Status/category:** Should fix; compiler/LSP blocker.

**Explanation:** `ErrorListener.BLACKHOLE` is currently used both for legitimate
speculative probes and for "no listener was passed". Those are different
states. A failed candidate-ranking probe should be visible as a suppressed
probe decision; a missing diagnostic session should be a bug at public
compiler/LSP boundaries.

**Evidence:** Broad scan on 2026-08-25 found about 80 main-source
`ErrorListener.BLACKHOLE` uses. Representative families include
`StatementExpression`, `ArrayAccessExpression`, `InvocationExpression`,
`LambdaExpression`, `NameExpression`, `TypeConstant`, `TypeCollector`,
`MethodStructure`, `xRTType`, and `StageMgr`.

**Failure mode:** Tests cannot assert why diagnostics were suppressed, and the
host cannot distinguish "expected failed fit preview" from "the compiler had no
diagnostic session".

**Minimal first PR:** Introduce `DiagnosticProbe` or named branch helpers such
as `diagnostics.probe("candidate-ranking")`. Add a source-shape test that direct
`BLACKHOLE` use is allowed only in a small legacy allowlist with comments.

**Proof/tests:** Source-shape test plus a focused compiler probe test that
records a suppressed attempt reason without surfacing it as a user error.

## 3. Stop deep `ErrorList` authority

**Issue title:** Do not create diagnostic collectors deep inside compiler and
runtime helpers.

**Status/category:** Should fix; compiler/LSP blocker.

**Explanation:** A helper may need a branch or bounded collector, but it should
derive that collector from the active request/session. Local `new ErrorList`
allocation severs request identity and makes it impossible to attach the
diagnostic to a document version, compiler phase, owner, container, or runtime
operation.

**Evidence:** Broad scan on 2026-08-25 found 10 main-source `new ErrorList(...)`
sites. Particularly important examples are `ModuleInfo.Node.errs()`,
`EvalCompiler.createLambda(...)`, `Parser.parseModuleNameIgnoreEverythingElse()`,
`TypeConstant` capped-method preview, `ConstantPool` implicit metadata startup,
`xModule.resolveClassOrType(...)`, `xRTCompiler.CompilerAdapter`,
`xIntLiteral` fallback parsing, and `javajit.Linker.errors`.

**Failure mode:** Failures become local lists, null returns, stdout/stderr, or
generic Java exceptions instead of structured events with a single owner.

**Minimal first PR:** Pick one family, likely parser/module-name probe or
native compiler adapter, and pass a diagnostic branch from the entry point.

**Proof/tests:** Assert that diagnostics from the migrated path carry request
or operation context and are still rendered as before.

## 4. Nullness annotations for required owner/API boundaries

**Issue title:** Annotate required owner and constructor inputs as non-null.

**Status/category:** Should fix; API discipline.

**Explanation:** The project already has JetBrains annotations on the
compile-only classpath, but main-source annotation usage is tiny. Required
constructor inputs and owner parameters are guarded at runtime but not visible
to tools, so IntelliJ/nullness analysis keeps reporting impossible nullable
receiver states. That noise makes it harder to convert mutable lifecycle fields
to final owner-passed state.

**Evidence:** `nullness-annotation-audit.md` found roughly 700 `return null`
sites, 6849 null checks/assignments, and only 17 annotation hits in main Java
sources.

**Failure mode:** The source fails to say which values must exist after
construction. Reviewers and tools cannot tell required state from optional
state.

**Minimal first PR:** Add `@NotNull`/`@Nullable` at high-signal boundaries
listed in `nullness-annotation-audit.md`: `Lazy`, `Scope`,
`TransientThreadLocal`, `BlockingQueueAdapter`, `MainContainer`, `Fiber`,
`NativeTemplates`, `ConstHeap`, `OwnershipDiagnostics`, compiler front-end
constructors, and selected runtime factories. Keep runtime guards.

**Proof/tests:** Compile both projects. If no analyzer is added yet, proof is
source-shape plus no behavior change.

## 5. Typed absence/failure results instead of overloaded null

**Issue title:** Stop using null for both absence and failure.

**Status/category:** Should fix; API discipline.

**Explanation:** Null currently means not found, parse failed, not computed,
listener disabled, cache empty, lifecycle cleared, or error already reported,
depending on the call site. APIs should separate true absence from failure and
from "diagnostic already emitted".

**Evidence:** Representative hotspots include `EvalCompiler.createLambda(...)`
returning null after logging to a local `ErrorList`, `xModule.resolveClassOrType`
parsing with a local `ErrorList`, `StageMgr` null listeners becoming
`BLACKHOLE`, and repository paths that had to be fixed to separate corrupt
module files from missing modules.

**Failure mode:** Callers inspect side channels or guess from context. That is
fragile in normal code and unworkable for LSP/incremental compile where the host
needs a typed outcome.

**Minimal first PR:** Pick one family and introduce a typed result, for example
`EvalCompileResult` or a runtime reflection parse result. Do not sweep every
nullable lookup at once.

**Proof/tests:** Tests should show failure, absence, and success as distinct
states.

## 6. Empty immutable collections for "none"

**Issue title:** Return empty immutable collections where absence means none.

**Status/category:** Should fix; API discipline.

**Explanation:** A null collection return forces every caller to branch and
often creates duplicated defensive code. When the only meaning is "no entries",
`List.of()`, `Set.of()`, `Map.of()`, or a stable empty array is safer and easier
to reason about.

**Evidence:** The read-only collection/array audits already found many exposed
collection contracts, including getters whose read-only wrappers were formerly
created only inside assertions.

**Failure mode:** Callers can confuse "not initialized", "no entries", and
"failed". They also risk mutating returned internal collections when wrappers or
copies are missing.

**Minimal first PR:** Convert a small cold getter family where null only means
empty and add tests asserting empty plus read-only behavior.

**Proof/tests:** Unit tests for null-free empty results and mutation refusal.

## 7. Repo-owned logging facade

**Issue title:** Add a repo-owned diagnostic logging facade before migrating
print sites.

**Status/category:** Nice-to-have after must fixes; diagnostics.

**Explanation:** Runtime/ASM/compiler call sites should not depend directly on
SLF4J or any other backend. A tiny `XtcLog`/`LogCat`/`LogSink` facade lets CLI,
Gradle, LSP, tests, and JFR attach sinks without rewriting call sites later.

**Evidence:** `logging-strategy.md` records the disabled-cost requirements and
the repo-owned facade design.

**Failure mode:** Direct stdout/stderr and ad hoc logging cannot be collected,
filtered, correlated, or routed by host tools.

**Minimal first PR:** Add the facade and a default CLI sink. Do not convert the
whole tree.

**Proof/tests:** Unit tests for disabled-cost gate behavior and sink fan-out.

## 8. Side-effect-free display path

**Issue title:** Make debugger/display rendering side-effect-free.

**Status/category:** Should fix; debugger/LSP blocker.

**Explanation:** `toString()`, `getValueString()`, and related display helpers
often force lazy caches, build TypeInfo, resolve types, or intern constants.
That means looking at a value can mutate the state being debugged.

**Evidence:** `plans/side-effect-free-tostring.md` inventories the risky calls.
The presentation already uses this as a debugger/LSP requirement.

**Failure mode:** Debugger hover/watch output changes owner/cache state and can
hide or create races.

**Minimal first PR:** Add a pure display contract and migrate one small family,
returning deferred markers when forced data is not already computed.

**Proof/tests:** Source-shape gate banning known lazy-building callees from
pure display methods, plus focused display tests.

## 9. Remove `ConstantPool.withPool(...)` completely

**Issue title:** Delete ambient pool scoping after explicit owner conversion.

**Status/category:** Should fix before reentrancy claim.

**Explanation:** `ScopedValue` is lexically safer than open `ThreadLocal`, but
it still hides an owner in dynamic state. This branch removed semantic
`getCurrentPool()` lookup; the remaining bridge should be retired so owner
selection is visible in APIs.

**Evidence:** `ambient-context-audit.md`, `scoped-value.md`, and the backlog row
track remaining `withPool(...)` scopes.

**Failure mode:** Same-JVM reuse can still depend on whichever dynamic scope is
active instead of the explicit pool/container/request being operated on.

**Minimal first PR:** Replace one remaining scope family with explicit
`ConstantPool`, `FileStructure`, `Container`, `Frame`, or build-context
parameters.

**Proof/tests:** Source-shape test that semantic ambient lookup does not
return, plus focused owner assertions.

## 10. Complete world X-ray diagnostics

**Issue title:** Extend world snapshots from containers to execution state and
reachability paths.

**Status/category:** Reentrancy proof infrastructure.

**Explanation:** The current slice can snapshot containers, render a world
diff, and show retained containers across sequential runs. To prove reentrancy
end-to-end, diagnostics need fibers, frames, registers, services, handles,
pools, metadata caches, and path-to-root evidence for foreign-owner references.

**Evidence:** `WorldSnapshotDemoTest` prints sequential-run and multi-container
world dumps; `ownership-diagnostics.md` tracks open slices.

**Failure mode:** A wrong-owner handle or retained old world may be detected
late or indirectly, without a path showing how it stayed reachable.

**Minimal first PR:** Add fiber/frame/register snapshot in a quiesced mode.

**Proof/tests:** Demo and failure tests that print path-to-root evidence for a
deliberately injected foreign-owner reference.

## 11. Type service/fiber response payloads

**Issue title:** Replace raw service/fiber futures and responses with typed
payload shapes.

**Status/category:** Runtime type-safety and reentrancy.

**Explanation:** Futures and responses are cross-service handoff mechanisms.
Raw `CompletableFuture`, raw `Response`, and unchecked casts hide whether a
payload is one handle, many handles, a tuple, or an exception.

**Evidence:** `lint-parallelism-risk-audit.md` identifies `Fiber` and
`ServiceContext` raw/unchecked warnings as high-risk because they sit at
service/fiber async boundaries.

**Failure mode:** The compiler cannot prevent a wrong payload shape from being
completed into a future and failing later in another service/frame.

**Minimal first PR:** Introduce a sealed/typed response payload for one service
handoff path and isolate unavoidable casts at the boundary.

**Proof/tests:** Existing runtime async tests plus a payload-shape regression.

## 12. Type op-info cache keys

**Issue title:** Make runtime op-info cache keys typed and owner-aware.

**Status/category:** Runtime metadata cache safety.

**Explanation:** `ServiceContext.getOpInfo`/`setOpInfo` use raw enum/cache
types. If op info is service-local by construction, that should be encoded or
asserted. If op instances can be reused across owners, the key must include the
owner scope.

**Evidence:** `lint-parallelism-risk-audit.md` calls out raw `Enum`,
`EnumMap`, and `WeakReference` in the op-info cache.

**Failure mode:** Heterogeneous cached values can be mixed or reused under the
wrong owner without compile-time resistance.

**Minimal first PR:** Add `OpInfoKey<E extends Enum<E>>` or equivalent for one
cache family.

**Proof/tests:** Type-level compile proof plus owner-confinement assertions.

## 13. Type native-template reflection

**Issue title:** Use typed native-template class reflection during startup.

**Status/category:** Runtime startup safety.

**Explanation:** Native template startup installs owner-local runtime templates.
Raw `Class` reflection weakens the proof that every reflected class is a
`ClassTemplate` before instantiation.

**Evidence:** `lint-parallelism-risk-audit.md` identifies raw `Class` maps and
unchecked reflective conversion in native-template startup.

**Failure mode:** Reflection failure appears late or as a wrong type during
owner startup rather than at the boundary where the class is loaded.

**Minimal first PR:** Use `Class.forName(name).asSubclass(ClassTemplate.class)`
and carry `Class<? extends ClassTemplate>` through the map and constructor
helper.

**Proof/tests:** Startup tests plus source-shape checks for no raw native
template `Class` maps.

## 14. Fallthrough and legacy state-machine cleanup

**Issue title:** Replace non-cascade fallthrough state machines with explicit
control flow.

**Status/category:** Should audit/fix opportunistically.

**Explanation:** Fallthrough switches, labelled nested loops, and parameter
reassignment are not automatically bugs, but they hide lifecycle transitions in
control flow that tools cannot summarize. Where a switch is not a real cascade,
arrow switches or extracted methods make state transitions explicit.

**Evidence:** `lint-parallelism-risk-audit.md` found 112 fallthrough warnings in
one lint log, with runtime/service/compiler/JIT representative sites.

**Failure mode:** An unintended state edge can skip initialization, repeat work
under the wrong owner, or leave mixed lifecycle state.

**Minimal first PR:** Pick one proven non-cascade switch and convert it to an
arrow switch or extracted transition method.

**Proof/tests:** Existing behavior tests plus reduced fallthrough suppression
count.

## 15. Duplicate invariant extraction

**Issue title:** Use duplicate-code findings to remove repeated invariants.

**Status/category:** Should audit/fix opportunistically.

**Explanation:** Duplicated code is dangerous when it duplicates owner, type,
diagnostic, or lifecycle decisions. One path gets fixed; another path silently
keeps the old invariant.

**Evidence:** IntelliJ duplicate-code diagnostics and
`modern-java-syntax-audit.md` should be used as input, but every candidate must
be read on both sides before filing.

**Failure mode:** Reviewers cannot tell whether two paths intentionally differ
or accidentally drifted.

**Minimal first PR:** Choose one duplicate decision family, extract a shared
helper or typed record, and add tests at both original call sites.

**Proof/tests:** Before/after duplicate report for that family plus behavior
tests proving no semantic change.

## 16. Resident compiler/LSP request ownership model

**Issue title:** Define request-owned compiler state for resident same-JVM use.

**Status/category:** Architecture; required for a useful LSP and Gradle daemon.

**Explanation:** The compiler cannot be made fast enough for LSP/daemon use by
forking a JVM per request or module. It needs request-owned state: pools,
module graphs, diagnostics, cancellation, caches, and source/document identity.

**Evidence:** The presentation opening records years of Gradle plugin, test,
and LSP attempts blocked by the monolith and stale process state.

**Failure mode:** Every attempt at same-JVM reuse inherits old static/global
state or has to over-serialize execution until the performance benefit is lost.

**Minimal first PR:** Design-only PR or docs PR describing request state and
entry/exit lifecycle, then one small implementation slice such as diagnostic
session ownership.

**Proof/tests:** Same-JVM sequential compile smoke and LSP diagnostic
correlation tests.

## 17. `xvm-reentrant` vehicle decision

**Issue title:** Keep a reentrant integration vehicle while upstream PRs stay
small.

**Status/category:** Project decision.

**Explanation:** The preferred path is still small, reviewable upstream PRs:
the 18 master bugs first, then explicit-owner and diagnostic slices. A separate
`xvm-reentrant` repository or long-lived integration branch is useful only as a
proof vehicle and fallback when the upstream process cannot absorb the series
fast enough.

**Evidence:** The existing branch is already an integration/proof branch; the
global state and X-ray diagnostics show why the pieces must be tested together.

**Failure mode:** Without an integration vehicle, fixes that only prove their
value under same-JVM reuse stay fragmented and regress between small PRs.

**Minimal first step:** User decision: create/maintain a public or private
`xvm-reentrant` vehicle, or keep using `lagergren/lazy-instance` as the local
integration branch until upstream response is clear.

**Proof/tests:** The vehicle must run the same gates as this branch:
`:javatools:test :javatools_utils:test`, `xdk:installDist`, same-JVM stress,
and world X-ray diagnostics.
