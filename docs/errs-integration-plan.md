# Integrating the embedding diagnostics work

Plan prepared on 2026-09-22 from `lagergren/errs` at `a8213cf04`, against the local
`origin/master` reference at `4a1eae6f7`, which is also the merge base. This comparison contains
72 commits and changes 139 files: 6,632 insertions and 984 deletions. The remote was not refreshed
for this assessment. These numbers describe the source branch, not the proposed PR sizes.

Sources: [the design and investigation](errs.md), [the failure audit](errs-audit.md), the commit
bodies, and the current compiler, embedding API, adapter, server and build configuration.
There are no `errs.log` or `errs-audit.log` files in this checkout; the corresponding records are
the two Markdown files above.

## Recommendation

**Current execution order:** harden the existing branch before extracting PRs. The first pass is
reliable compiler diagnostics and the existing LSP features; project compilation across multiple
files is deferred. The PR slices below describe eventual integration, not the current work queue.

### Branch hardening design

This is a large change because the reporting contract, embedding API, build and LSP document
lifecycle meet here. Use the existing serialized compiler worker with an asynchronous adapter
entry point. Keep the synchronous entry point for direct embedding callers and existing fast
adapters. Each document analysis has a distinct identity; newer edits, close and shutdown invalidate
older work. Only a current analysis may enter the cache or publish versioned diagnostics.
Cancellation is a canceled operation, not a successful result containing no diagnostics.

The alternatives are project-wide compilation and a hybrid syntax/semantic adapter. Both add useful
capabilities, but neither is required to establish correctness of the current diagnostics path.
The selected first pass retains the existing compiler and AST features, supplies their XDK test
dependency explicitly, removes runtime initialization from compiler metrics, and tests listener
decorators and cached diagnostic replay. Protocol tests control execution order to exercise stale
results and close/reopen without relying on timing. No PR extraction starts until this gate passes.

- [x] Inspect the current branch and identify concrete integration gaps.
- [x] Confirm first-pass scope: diagnostics and existing LSP features.
- [x] Select the serialized compiler/asynchronous document lifecycle design.
- [x] Establish a fresh compiler-backed baseline with no skipped required tests.
- [x] Make the XDK a declared test dependency and exercise compiler-only changes in CI.
- [x] Fix and test listener/embedding reporting defects found by the hardening pass.
- [x] Fix and test async analysis, supersession, close/reopen and shutdown.
- [x] Verify current feature behavior and report remaining limitations accurately.
- [x] Run the relevant suites and configuration-cache checks; record remaining work before extraction.

Land the compiler diagnostics foundation and a tested embedding consumer first. Keep the existing
outline and semantic navigation work in later PRs, so accepting the listener changes does not
require accepting a particular LSP implementation. Investigate the remaining suppressed
diagnostics separately, with a reproducer and a decision for each source shape.

Use twelve bounded PRs below. Three can start independently; the compiler changes form a short
stack; the adapter changes follow the public API they consume. The unit of review is an observable
contract, not one historical phase or one commit. A PR that migrates an interface may legitimately
touch many files, but should change only that interface's contract and its required consumers.

The main branch is the source of patches and evidence. Do not replay all 72 commits: several
introduce mechanisms that subsequent commits remove, and some mix functional work with unrelated
renaming. Reconstruct the final implementation for each slice and verify that intermediate state.

## What is already present

- Diagnostics distinguish source names, end positions and message parameters during deduplication.
- A host can use a stateful collector; reporting and the decision to abort are separate operations.
  Branching, merging and named silences have explicit behavior.
- Compiler boundaries require a listener, and the ambient lookup through file structures is gone.
  TypeInfo builds record diagnostics for later replay; an `ErrorList` filters repeat reports.
- `compileModule(Source, repository, errs)` preserves the AST, file structure and compilation pool
  when those stages were reached. XDK auto-configuration includes both `lib` and `javatools`.
- The adapter reports real syntax and semantic diagnostics. It also has an outline, hover,
  highlights, folding, selection, symbols from previously compiled documents, and limited same-file
  definition and references. The latter came in `956d56f41`; older “not implemented” passages are stale.

This is a useful embedding foundation. It does not yet establish that every relevant compiler
diagnostic reaches every editor request, or that arbitrary project files can be compiled in isolation.

## First hardening pass on this branch

The following working-tree changes address the initial gaps found at `a8213cf04`. They are local
changes, not extracted PRs or remote CI results.

| Area | Behavior established in this pass | Evidence |
|---|---|---|
| Listener composition | `tee` retains error-code queries; branches retain the parent's abort request; derived silences suppress reports while preserving cancellation. | `ErrorListenerCancelTest` |
| Embedding failures | Expected launcher/compiler aborts do not invent internal errors. An unexpected exception is reported even after a source error. Already-cancelled work does not parse; empty buffers report a source problem. | `EmbeddingDiagnosticsTest` |
| Passive metrics | `footprint(compilation)` measures that compilation's pool without starting the interpreter. `footprint()` reports repository/heap counts with no pool selected. | `EmbeddingFootprintTest` |
| Build inputs | The existing `javatools` Java dependency supplies the compiler. A test-only `xtc` configuration consumes `libs.xdk.ecstasy` and `libs.javatools.bridge`, including transitive modules. The fixture configures a repository over those resolved directories. | Gradle task graph and compiler-consumer suites |
| Test selection | Compiler/library/build changes get a focused LSP consumer lane. Lang validation runs the full suite. A required XML gate rejects missing suites, zero tests, failures, errors and skips. This is separate from IDE publishing selection. | `.github/workflows/commit.yml`; XML gate exercised locally |
| Document lifecycle | Notifications schedule compiler work asynchronously. New edits coalesce pending work and cancel prior operations. Only the current request may cache or publish. Close/reopen and shutdown invalidate previous lifetimes. | `XdkAdapterLifecycleTest`, `XdkLanguageServerTest` |
| Protocol behavior | Diagnostics carry document versions; stale versions are ignored. Features await their document analysis and return `ContentModified` if it is superseded. Outline queries no longer start duplicate compilations. | `XdkLanguageServerTest` |
| Feature claims | Compiler mode advertises its implemented features; completion, rename, formatting and other unavailable operations are omitted. Selection ranges preserve one response per requested cursor even without an AST. | Capability and selection tests |
| TypeInfo replay | The warning replay test now verifies that successive requests return the same cached `TypeInfo` and hear the same diagnostics. | `TypeInfoDiagnosticsTest` |

The test dependencies require neither archive unpacking in `lang`, an `installDist` prerequisite,
nor a manually configured `XDK_HOME`. They use the compiled-module artifacts the composite already
publishes. The production adapter continues to support the existing `XDK_HOME` configuration path.

### Remaining limitations and the next hardening work

Keep these explicit before deciding the branch is ready to split:

1. **Navigation accuracy within one document.** Highlights still match spelling throughout the
   document. Local definitions/references approximate identity by name within a method and can
   confuse sibling scopes; a local definition can point at its first use. Add shadowing/sibling-scope
   reproducers and expose the original declaration identity from the compiler before calling these
   operations semantically complete. Constructor-generated properties also lack declaration spans.
2. **Incomplete source.** Some parser failures leave no AST, so outline/navigation disappear until
   the buffer parses. The selection fallback preserves protocol shape but does not recover syntax.
   Reproduce ordinary typing states and decide compiler recovery versus a hybrid syntax adapter.
3. **Diagnostic source attribution.** `Site.In` is currently placed at the requested document URI;
   structure-only and nowhere diagnostics use `(0,0)`. Add foreign-source and Unicode/line-ending
   cases before extending publication beyond the current single-module buffer.
4. **Compiler failures with no listener path.** Continue the suppression audit using specific source
   reproducers. This pass fixes decorator propagation and the embedding catch policy, not every
   historical probe/silence or runtime failure sink listed in `errs.md` and `errs-audit.md`.
5. **Operational validation.** Exercise the real editor/stdio transport, unavailable/misconfigured
   XDK startup, long editing sessions and shutdown during slow compiler stages. The new protocol
   tests invoke the actual server service with a captured client; they do not launch an IDE.
6. **Project compilation is deferred by scope.** Member files, source roots, module ownership,
   dependency repositories and unsaved overlays need a separate design. Workspace symbols currently
   cover completed analyses of open documents only. Compiler cancellation remains cooperative.

The implementation is in [XdkAdapter](../lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt),
[the document service](../lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt)
and [EmbeddingSupport](../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java).
The build/CI contract is in [the test task](../lang/lsp-server/build.gradle.kts) and
[the commit workflow](../.github/workflows/commit.yml).

## Proposed PRs and dependencies

“Independent” means no dependency on another proposed PR's behavior. Extraction conflicts may still
occur because the source commits were written on top of the whole branch. Commit IDs identify
provenance, not a promise that an unedited cherry-pick compiles.

| ID | Scope | Prerequisites |
|---|---|---|
| I1 | Preserve distinct diagnostics and name in-memory sources | Independent |
| I2 | Guard ambient constant-pool reads | Independent |
| I3 | Establish compiler-consumer test wiring without requiring IDE builds | Independent foundation; activate required suites as they land |
| C1 | Define the host listener contract and reporting API | Independent of ownership work; coordinate public API compatibility |
| C2 | Require explicit listeners and explicit reasons for silence | C1 |
| C3 | Scope parser/resolver reporting and statement validation state | C2 |
| E1 | Return useful compilation results through the embedding API | I1, C2; I3 for compiled-XDK tests |
| C4 | Replay TypeInfo diagnostics and remove ambient listener ownership | C2, C3; E1 and I3 for the downstream regression tests |
| L1 | Connect the diagnostic-only XDK adapter and prove publication | I1, I3, C4, E1 |
| L2 | Complete cancellation and document lifecycle handling | L1; includes the listener cancellation decorator |
| L3 | Add the outline and structural AST features | E1, L2 |
| L4 | Expose resolved targets and add limited semantic navigation | L3; I2 before testing queries with no ambient pool |

Suggested landing order: I1 and I2 first; I3 alongside C1; then C2, C3, E1, C4, L1 and L2.
L3 and L4 can follow without delaying the diagnostics milestone. These are twelve PRs, not twelve
simultaneous open branches. Keep only the next few ready for review, and update dependent patches
after their prerequisites land.

### I1 — Preserve distinct diagnostics and name in-memory sources

**Contract:** two different diagnostic spans or parameter values survive collection, and two named
unsaved documents cannot suppress each other's diagnostics.

Take the UID correction from `19e567e55` and the `Source(text, name)` support from `fad701097`.
Keep existing source constructors. Reconstruct the small dedup/source tests against master's
listener API; the current `CompilerDiagnosticsTest` also uses helpers that belong to C1.

Verify different end positions, colliding parameter hashes, two named buffers, and the unnamed
positive control. No LSP, runtime or TypeInfo ownership changes belong here. This is a bug fix
with an additive source constructor; genuinely distinct diagnostics become visible.

### I2 — Guard ambient constant-pool reads

**Contract:** using or describing a constant outside an ambient pool scope does not throw merely
because the calling thread has no pool; a bound pool still takes precedence over the fallback.

Take the final combined state of `cae4f9452` and `610873fb6`, including `currentOr`, `poolInUse`
and their consumers. Do not land the first MethodBody workaround and then replace it in a second
PR. Review each fallback for ownership, particularly cross-pool operations and the connector.

Use `MethodBodyAmbientPoolTest` and `ConstantPoolAmbientTest`, including the bound-pool precedence
case. Compare compiled XDK output with the baseline after removing only the known timestamp
difference. This PR guards null ambient reads; it does not make the compiler concurrent or replace
ambient pool ownership.

### I3 — Establish compiler-consumer test wiring

**Contract:** a clean supported build runs the compiler-backed consumer tests against the XDK it
just built, and compiler-only PRs exercise that consumer.

Use IDE lifecycle separation from `7097892b6`. Reassess the default attachment change in
`566bc0c4a` as build policy rather than importing it automatically. Include the first hardening
pass's `compilerTestModules` configuration and shared test fixture. The javatools dependency from
`bb3c4c62c` can enter here when the first embedding test needs it.

Consume the existing compiled-module variants directly; do not add packaging, extraction or
installation tasks. The dependency direction remains compiler -> compiled XDK -> embedding tests;
do not make javatools tests depend on compiling the XDK with their own `check` lifecycle.

Make CI selection cover `javatools`, the embedding API and relevant build/dependency changes,
not just `lang` paths. The required suite must assert that expected classes executed with zero
skips. Optional standalone tests can still use assumptions, but cannot satisfy the required gate.
Test configuration-cache storage and reuse with a real task. IDE packaging and publication remain
separate consumers with their existing explicit selection.

### C1 — Define the host listener contract and reporting API

**Contract:** a host collector reports what it has heard accurately; reporting does not itself
decide control flow; branch and merge work with a custom listener.

Combine the final relevant changes from `3896e7409`, `920cc3858`, `34b9f8ea2`, `0b4261ca0`,
`e5154fb24`, `8f3870386`, and the `tee` portion of `14189a716`. Include `Site`, severity helpers,
named budgets, the default `ErrorList` constructor and the final named-silence helper API needed
by C2. Migrate the implementations and control-flow readers required to compile this contract.
Leave broad report-site spelling changes to C2, and keep the legacy silence entry points until
their callers migrate there. Adding the new helpers does not require deleting the old names first.

Tests: `ErrorListenerSiteTest`, `ErrorListenerBranchTest`, `ErrorListenerAbortTest`, collector
state, tee forwarding and budget propagation. Keep a test demonstrating why a bare lambda is
insufficient. Document that `collecting` does not deduplicate, while `ErrorList` does.

This PR has an intentional public API break: `boolean log(ErrorInfo)` becomes `void`, and `RUNTIME`
stops throwing from inside reporting. Java cannot preserve the old method using an overload that
differs only by return type. Decide the compatible release boundary before extraction; if the
current release requires binary compatibility, defer this breaking slice or design an explicit
versioned bridge. Do not describe the branch as wholly additive.

### C2 — Require explicit listeners and explicit reasons for silence

**Contract:** compilation boundaries do not invent a listener when none was supplied; reporting
paths pass the listener onward; a probe and an incomplete-result cascade are chosen deliberately.

Use `b1c71fb4e`, `469cecf1b`, `63bd09bb9`, `6d342f915`, `01e816622`, `9165c00b0`, `dc58262cd`
and their final corrections. Include the report-site migration/deprecation from `8ac04ec93` and
`c60bdb26d`, using the final import style from `716c7f118` in touched code. Retain the deprecated
array-shaped overloads; removing them buys nothing for this milestone.

Keep this review focused on the propagated listener and the reason for each silence. The spread
across AST files is required by that contract; formatting elsewhere and unnamed-catch conversion
are not. Remove the silent default from `ResolutionCollector` with its implementor check.

Verify boundary rejection, probe silence, branch-kept diagnostics, cascade behavior, a clean XDK
build and timestamp-normalized output equivalence. Null rejection and removal of the collector
default are compatibility changes even where they expose misuse. List them in the release notes.

### C3 — Scope parser/resolver reporting and statement validation state

**Contract:** speculative parser work reports only when kept, and exceptional exits restore the
reporting destination and validation state.

Take `01e0161be`, the parser portion of `259f8135e`, `fe778a92b`, and the retained final portions
of `9bbeb8348` and `9cdd84519`. Include `ValidationScope`, parser/resolver `Reporting` scopes,
and the listener lifetime fixes for `EvalCompiler` and `ModuleInfo.Node`.

Do not recreate the temporary `FileStructure.reportingTo` API from `d23120b50`: C4 deletes that
ownership path. Bring only the scope machinery the final parser and resolver actually use.
The unrelated catch-variable/TestNumber edits mixed into `259f8135e` stay out.

Use `ParserAttemptTest`, nested-scope and exceptional-exit checks, and the existing labeled-loop
and try/finally exercises. `Reporting` still allows a null inactive destination for `NameResolver`;
describe that lifecycle accurately and verify that reporting occurs inside its active scope.
A final holder does not by itself establish a globally non-null or thread-safe listener.

### E1 — Return useful compilation results through the embedding API

**Contract:** a named in-memory compilation reports through its caller's listener and returns the
artifacts it actually produced, including partial progress on failure.

Take `53b13d7a4`, the embedding bootstrap configuration from `bb3c4c62c`, `60a451a3f`, and the
`Compilation.parsed()` support from `91d1b08e1`. Keep `compile(String, ...)` and other existing
entry points as delegating APIs. Add the new `Source` overload and result API without moving LSP
types into javatools.

Preserve a deprecated delegating `getConstantPool()` alias if compatibility is required when adding
the clearer `ensureRuntimePool()` name. The absence of in-tree callers does not establish that a
public method has no external callers. Document the runtime initialization side effect explicitly.
Do not bring `footprint()` across unchanged.

Verify success, semantic failure with retained AST/pool, parse failure, no configured XDK, missing
bootstrap modules, and correct named-source diagnostics. Distinguish expected compilation aborts
from internal failures: include the hardening pass's separate `LauncherException` catch and
regression for an unexpected failure following a source diagnostic. Include the empty-buffer fix
and the named `Compilation.forFile` factory. Test cancellation separately in L2.

Document the complete-module source requirement and the current concurrency guarantee. The class
javadoc now requires serialization of compilations sharing the configured repository; carry that
narrower contract with the API rather than advertising unverified concurrency.

### C4 — Replay TypeInfo diagnostics and remove ambient listener ownership

**Contract:** asking for a cached TypeInfo does not lose a real warning because another caller
built it first, and a file structure cannot redirect a later request's diagnostics.

Combine `14189a716`, `ff20ca0bc`, `0af497641`, `a49b326f5` and `e383a818a`, plus any prerequisite
cleanup from `ed8d3f278` that survives in the final code. Go directly to an immutable recorded list
and deletion of the file/pool listener lookup and compilation-wide silence. Do not ship the
intermediate mutable-listener recording or the park/restore/scoped-park sequence.

The two converted TypeInfo sites intentionally differ: `StatementBlock.resolveReservedName`
reports through its caller; the `RelOpExpression.testFit` path uses `PROBE`. Keep the remaining
no-argument runtime/probe calls; this is not a bulk conversion of every TypeInfo caller.

Use `TypeInfoDiagnosticsTest` through the I3 harness, and the final `FileStructureErrorListenerTest`.
Pin `VERIFY-75` reaching a listener, reaching a later listener from a genuinely cached warning-only
TypeInfo, and appearing once in an `ErrorList`. Strengthen the cached-path test to demonstrate no
rebuild; the present test only checks that the later caller hears the warning. Serious-error cases
rebuild by design and do not prove replay. Check behavior after invalidation and with a fresh sink.

Compare compiled XDK output and classify any change in emitted diagnostics. `VERIFY-75` newly
appearing is an intended user-visible fix, not an output-equivalence failure. Record its reproducer
with this PR; file the separate upstream issue only when explicitly authorized.

### L1 — Connect the diagnostic-only XDK adapter and prove publication

**Contract:** opening and correcting a complete module publishes the compiler's codes, severities
and locations through the real language-server service.

Extract the minimal adapter from `bb3c4c62c`, use `ErrorList` immediately as in `26c8fa9c1`, and
include the shaded SLF4J provider fix from `535b9d80e`. Include backend selection/help and the
matching portions of the manual test plan. Keep compilation serialized. Do not bring the later
AST feature walk, cancellation claims or runtime-backed footprint logging into this PR.

Add a compiler-backed `LanguageClient` publication test; the current `LspIntegrationTest` uses
tree-sitter or mock, and `LspRoundTripTest` exercises the compiler/mapping rather than the server.
Cover clean -> error -> corrected, two URIs, `VERIFY-75` exactly once, source spans, and an
unavailable XDK. Include Unicode/line-ending span cases. Structure-only and nowhere diagnostics
currently map to `(0,0)`; document that fallback rather than claiming precise locations for them.

Check source attribution: `toDiagnostic` currently uses the requested URI even for `Site.In`,
ignoring the site's source name. Prove the single-document case and define how foreign-source
diagnostics will be represented before project compilation is added. Advertised server
capabilities must reflect the selected adapter's implemented features.

### L2 — Complete cancellation and document lifecycle handling

**Contract:** newer edits and closes supersede work without publishing or caching stale results.

Take the listener decorator and its branch/merge tests from `d92f93fb3`. Rework the service and
adapter lifecycle around document versions/open instances using the hardening pass's implementation.
Advance the version at notification receipt, schedule compilation asynchronously, and check the
version and open state when committing the result. Keep a single compiler worker and coalesce
superseded queued work. Closing removes cached ASTs, symbols and version state while ensuring old
work cannot become current after a reopen.

Use deterministic barriers or a controlled executor in protocol tests: pause the old compilation,
deliver a new edit, finish both in a chosen order, and assert only the new version is published.
Also cover close while queued/running, reopen at the same URI, cancellation without a user error,
and two independent documents. An empty diagnostic list for a corrected document must still
publish; a canceled operation must not. Avoid timing-dependent assertions about how often a race
happens. Stage-boundary cancellation can remain coarse after these semantics are correct.

### L3 — Add the outline and structural AST features

**Contract:** the cached analysis supplies positions and structural editor features without
recompilation, and document close releases the retained tree.

Take the adapter/`XdkSymbols` portion of `91d1b08e1` and the `XdkAst`/structural portion of
`956d56f41`: document symbols, containing declaration, folding, selection, highlights, declaration
hover and symbol search over cached documents. The result API already carries the AST from E1.

Tests should cover nested declarations, failed validation with a usable AST, a failed parse with
no AST, repeated/shared nodes and cleanup. Describe highlights as text matching where that remains
the implementation. Symbols from compiled documents are not a complete workspace index.

### L4 — Expose resolved targets and add limited semantic navigation

**Contract:** read-only compiler accessors expose existing resolution results, and the adapter uses
them for supported same-file targets without inventing an answer where identity is unavailable.

Take the two Java accessors and `XdkResolution` from `956d56f41`, plus typed hover and their tests.
Document unresolved/unvalidated nodes and result lifetime. These accessors can be extracted as a
smaller independent compiler PR if useful, with compiler-level tests; the Kotlin consumer still
depends on L3.

Pin property identity across classes, overload-selected calls, types, constructors and unresolved
names. Add local declaration/use tests with sibling scopes, narrowing and shadowing: the current
implementation groups locals by spelling inside a method and chooses the first matching name as
the declaration. Do not call that exact resolution or reuse it for rename. Return no target for
unsupported cases or explicitly retain the best-effort limitation. Constructor-generated
properties and cross-file targets remain separate follow-ups.

## Changes to hold out of the initial integration

| Change | Disposition |
|---|---|
| `Origin` POC, `3a836cd80` | Defer until a consumer needs it. Thread name is not request identity; never add it to the dedup key. |
| Runtime-backed footprint, part of `535b9d80e` | Keep the original runtime-starting implementation out. The hardening pass's passive overload and regression tests can accompany E1 or form a small additive follow-up. Retain queue/compile timing and the SLF4J packaging fix. |
| Unnamed-catch and TestNumber sweep inside `259f8135e` | Optional independent mechanical PR. It changes no reporting policy and must not enlarge the parser review. |
| Broad line wrapping, `17d4a15a5` | Apply house style to touched lines; omit a separate cosmetic history replay. |
| Interface-method behavior tests, `7ee2fd8f3` | Independent test-only PR if still useful, or include only the cases that validate a compiler slice's behavior. |
| Historical status/document-only commits | Consolidate into accurate current usage/limitations accompanying each PR. Preserve the investigation records without replaying every progress update. |
| Runtime/Container failure ownership, SLF4J/JFR listener sinks, concurrent ErrorList | Deferred until a running-code or concurrent-reporting consumer needs them. They do not block compiler diagnostics. |

Any net change not assigned to a slice or to this table must be classified during extraction before
the source branch is considered fully integrated. Equality to the original branch tip is not the
acceptance criterion: deliberately deferred POCs and newly corrected integration behavior differ.

## Next investigations after the diagnostic milestone

1. **Triage suppressed TypeInfo diagnostics together with their callers.** The latest appendix
   records 70 distinct suppressed ERROR messages, with three source shapes examined, and roughly
   53 remaining compile-time sites needing a decision. Older sections say 126 total sites; do not
   treat that historical total as the migration scope. Recount and instrument the chosen revision.
   Group by code, type and compilation stage; retain the initiating caller and suppression reason.
   For each group, prove either a real missed diagnostic or why incomplete/provisional structures
   make it spurious. Fix real losses in small PRs with source reproducers. Never turn all no-argument
   `ensureTypeInfo()` calls into reporting calls in one sweep.
2. **Design compilation of an actual project with unsaved files.** Start with a module root and
   a class in another source file, edit that class without saving, and require diagnostics for the
   overlay text at the correct URI. Decide module discovery, dependency repositories, overlay
   lifetime, invalidation and results by source. A symbol index alone cannot supply missing
   compilation context. This is the next substantial embedding API requirement.
3. **Design incomplete-source support.** `console.` currently produces a parser diagnostic and
   no AST. Choose deliberately between compiler recovery/partial-analysis support and a hybrid
   adapter using tree-sitter for current syntax and the compiler for semantic results. Prefer the
   hybrid for near-term editor usability, while recording what the compiler API still lacks.
   Never silently apply old semantic ranges to changed text. Completion follows this decision.
4. **Extend semantic queries after identities are reliable.** Stable local declaration identity,
   constructor-parameter source mapping and a project source index precede safe rename and
   cross-file references. Signature help and semantic tokens can be separate earlier consumers
   of the accessors where validated source provides enough information.
5. **Work through the failure audit by reachable failure path.** Start with the three empty
   `Throwable` catches and the broad compiler/tool catches, then relevant I/O failures and live
   compiler prints. Four catches of `Exception` are broad but do not catch `Error`; the audit's
   sentence grouping all seven as able to swallow `Error` needs correction. Its five printed-site
   candidates also include a commented-out line and static bootstrap code without a caller sink.
   Confirm each site before calling it a lost diagnostic. Runtime-only cases get independent fixes.

## Extraction and verification procedure

1. Preserve `lagergren/errs` as the reference. Prepare one local branch per slice from the agreed
   base or its stated prerequisite. Do not reset the working branch or fold unrelated local files
   into these changes. `full-log.txt` was already untracked during this assessment.
2. Use the provenance commits to locate changes, then reconstruct their final relevant hunks.
   Particularly split `259f8135e` (parser vs sweep), `535b9d80e` (logging vs metrics), `d92f93fb3`
   (listener vs adapter lifecycle), `91d1b08e1` (API vs outline), and `956d56f41` (structure vs semantics).
   Do not replace shared files wholesale with the branch-tip versions.
3. Check each slice independently, then against its prerequisites. Keep an explicit list of any
   failing gate or unclassified diagnostic; later PRs must not secretly repair earlier ones.
4. Run the relevant contract tests with `--rerun-tasks --no-build-cache` and inspect JUnit XML.
   Build required XDK outputs first. For ownership/silence/pool changes, compare installed modules
   against the base with only the known build timestamp normalized. Compare diagnostics separately.
5. For Gradle changes, verify a real task both stores and reuses the configuration cache. Run
   `spotlessCheck` and inspect the worktree; local `check` can run `spotlessApply`. Include the Gradle
   plugin consumer and relevant manual compiler/runtime exercises when their paths are touched.
6. When remote publication is explicitly authorized, prepare the PR body with `/ai-dev:pr` and the
   repository's required workflow. Each PR states its prerequisite, observable behavior, compatibility
   impact, measured verification and follow-ups. This document is a landing plan, not a set of PR bodies.

Commands for the hardening pass (Gradle now supplies compiler-test module dependencies):

```bash
./gradlew :lang:lsp-server:test :javatools:test \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true \
    --rerun-tasks --no-build-cache

./gradlew spotlessCheck
git status --short
```

If a clean verification is necessary, run `./gradlew clean` alone and wait before those commands.
Do not combine `clean` with another task.

The fresh pre-change baseline ran 26 compiler-consumer tests with zero failures/errors/skips.
Fresh full-suite results on 2026-09-22, read from JUnit XML:

| Suite | Tests reported | Failures/errors | Skipped |
|---|---:|---:|---:|
| `javatools` | 458 | 0 | 40 |
| `lang:lsp-server` | 457 | 0 | 3 |
| Required compiler-consumer subset of the LSP suite | 42 | 0 | 0 |

The javatools skips are 36 existing disabled tests and four opt-in project-generator integration
tests. The LSP skips are three existing disabled navigation/inlay-hint tests. They do not include
the required compiler-consumer subset. The final forced run used `--rerun-tasks --no-build-cache --info`, completed successfully, and reported both `Reusing configuration cache` and
`Configuration cache entry reused`. `spotlessCheck` and `git diff --check` passed. The workflow's
XML gate passed locally; `actionlint` reported the same 89 existing shellcheck findings on HEAD
and the modified workflow, with no new findings. Remote CI and an interactive editor session have
not been run. No grade-based quality claims are made.

## Completion criteria

The diagnostics milestone is complete when the required CI suite runs from clean inputs, an
embedding consumer receives attributable compiler diagnostics without initializing a runtime for
logging, and the real server correctly handles open, edit, correction, supersession and close.
Known single-module and source-location limits must be visible in the usage documentation.

Full editor/project support is a later milestone: unsaved project overlays, partial syntax,
stable semantic identities and cross-file source mapping. The suppressed-diagnostic and failure
audits remain explicit backlogs until their cases are classified; neither “seven phases complete”
nor a green adapter test run closes those investigations.
