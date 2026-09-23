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

**Current execution order:** harden the existing branch before extracting PRs. The initial scope
was reliable compiler diagnostics and existing LSP features. The subsequently approved
[eighth pass](#eighth-pass-module-sessions-and-hierarchy-2026-09-22) now adds permanent final-TypeInfo
regressions, module sessions with overlays, cross-file navigation and direct type hierarchy.
The [ninth pass](#ninth-pass-java-parser-recovery-2026-09-22) keeps compiler mode Java-only and retains
recovered syntax for structural features. The [tenth pass](#tenth-pass-bounded-incomplete-analysis-2026-09-22)
adds a separate compiler-only probe for intact receivers and arguments in one trailing incomplete
statement. The [eleventh pass](#eleventh-pass-copied-call-and-member-facts-2026-09-23) copies
selected call signatures and bounded receiver-member candidates. Completion/signature requests now
reach the server, including typed member prefixes and calls with existing closing parentheses.
Broader scope/incomplete-source analysis and cross-module indexing remain open. The numbered
passes below preserve chronology; the PR slices describe eventual integration, not a current work queue.

### Remaining work to establish the full API POC, 2026-09-23

Execution checklist (complete each item with the evidence specified below):

- [x] **1. Diagnostic audit.** Closed the `@Parsed` constructor family and classified the remaining
  inspected suppression/failure cases. Fixed false runtime-annotation metadata errors and lost
  non-module-root diagnostics; retained negative controls. See the
  [audit evidence and scope limits](errs-audit.md#annotation-metadata-and-module-source-follow-up-2026-09-23).
- [x] **2. Cursor/module partial analysis — bounded scope complete.** Analyze supported incomplete sites in unchanged module
  snapshots, including source overlays and positions before the end of the file.
  - [x] Explicit cursor before existing closing braces/semicolons; preserve following declarations.
  - [x] Module member sites using the same unsaved root/member snapshot; copy shared semantic facts.
  - [x] Simple assignment/initializer values, single return values and final nested call arguments;
    retain the actual enclosing syntax and validate its compiler context. Broader expression forms
    remain explicitly unavailable.
  - [x] Adapter cursor requests share the compiler worker; edits, superseding requests, close and
    cancellation invalidate running/queued work without replacing normal diagnostics.
  - [x] Server document-version checks and request cancellation through publication.
- [x] **3. Completion and signature help — bounded compiler-backed POC complete.**
  - [x] Qualified receiver completions and unfinished qualified-call candidates through real requests.
  - [x] Selected instantiated signatures and named/default argument mapping for resolved calls.
  - [x] Adapter/server cancellation, module invalidation and packaged stdio regressions.
  - [x] Typed member prefixes with original-token replacement edits; incomplete calls with
    editor-inserted closing parentheses.
  - [x] Bare-name scope completion and implicit/static receivers, including readable locals,
    narrowed types, shadowing, imported/enclosing type names and static member access checks.
  - [x] Candidate applicability, inferred expected argument types and incomplete named-argument
    mapping. An unfinished call still has no selected overload; missing arguments cannot prove one.
- [ ] **4. Other semantic consumer probes.** Type-definition, implementation lookup, call hierarchy,
  semantic classification/inlay hints and module-local rename validation.
- [ ] **5. Dependency/source boundary.** Prove identities, available source locations and dependency
  invalidation across two modules.
- [ ] **6. Lifetime and compatibility.** Sustained edits/cancellation/retention, output comparison,
  migration examples and the completed API requirements matrix.

The eleventh pass establishes selected-call provenance and bounded partial receiver facts. It does
not yet establish that the embedding/AST surface is sufficient for every intended editor consumer.
The completion criterion is a working consumer and negative cases for each required fact, with its
owner, lifetime, diagnostic behavior and unsupported cases documented. Declaring a method or
throwing UnsupportedOperationException is not evidence that its required compiler facts exist.

Existing evidence covers compiler diagnostics, cancellation, bundled libraries, module overlays,
current-module definitions/references, declared type hierarchy, structural recovery and immutable
semantic copying. Keep that evidence; concentrate further work on these gaps:

| Priority / area | What is still unproven or absent | POC acceptance evidence |
|---|---|---|
| 1. Diagnostic audit — complete within the documented scope | Runtime `@Parsed` metadata and non-module source diagnostics are fixed. Historical capture counts are not an exhaustive audit; bound-generic binary-AST generation still needs a reproducer. | `TypeInfoFinalCompositionTest` retains valid/invalid annotation controls; `EmbeddingDiagnosticsTest` pins positioned file/in-memory root diagnostics and discovery fallback. The audit classifies the remaining inspected families separately. |
| 2. Cursor-based incomplete analysis — bounded scope complete | Explicit cursors and module overlays support standalone statements, simple assignment/initializer values, single returns and final nested call arguments. Adapter/server ownership and cancellation now cover delivery; compound/conditional prefixes and arguments following the cursor remain unavailable. | `XdkPartialAnalysisTest`, `XdkCursorRequestTest`, `XdkCursorServerTest` and packaged stdio cover unchanged source, overlays, lexical context, positions and stale-result rejection. Compiler mode remains Java-only. |
| 3. Completion and signature help — bounded POC complete | Scope/member completion, imported type names, static lookup and incomplete-call candidate fitting now have consumers. Candidate-specific expected types and named-argument mappings are copied. Broader syntax, enclosing-instance member completion, type-valued receiver fallbacks and function-valued/receiver-rewritten calls remain outside this slice. An unfinished call never claims a selected overload. | Adapter/stdio requests cover declaration order, assignment state, narrowing, imports, access checks, generic receivers/methods, overload filtering, named slots, module overlays and token edits. Completed calls retain exact compiler selection. |
| 4. Other semantic consumers | Types lack an explicit copied declaration link; calls lack an explicit caller association; occurrences classify declaration/reference, not read/write or modifiers. Override/implementation and safe rename relationships still need proof. | Small compiler-backed consumers for type-definition, implementation lookup, incoming/outgoing calls, semantic tokens/inlay hints and module-local rename conflict checks. Record which facts can be copied from existing compiler structures and which need a small compiler hook. Include captures, shadowing, overloads and generated declarations. |
| 5. Dependency/source boundary | Module navigation works; cross-module/library source lookup and persistent workspace indexing do not. Snapshot IDs intentionally expire between attempts. | A two-module fixture proves dependency identity, available source locations and invalidation when the dependency changes. Specify host source/repository ownership and index keys; missing library sources must remain unavailable. A production workspace index can follow separately. |
| 6. Lifetime and compatibility | Current concurrency/retention measurements are bounded; the new collector and future partial requests need sustained exercise. Public listener signatures and result record shapes have migration implications. | Repeated multi-file edits, cancellation, close/reopen and shutdown release attempts/ASTs/pools; copied queries remain pool-free on other threads. Record latency observations, compare compiled output against the base, and finalize constructor/record-pattern and listener migration examples. |

Use these consumers to close an API requirements matrix: required fact, existing accessor or new
hook, compiler versus Kotlin ownership, complete/partial/unavailable behavior and a regression
that exercises it. Freeze the proposed embedding/AST surface only when each row has evidence or
an explicit scope decision. Formatting, editor polish, a production persistent index and full
implementations of every LSP handler need not delay that API decision. Unsupported protocol
capabilities remain unadvertised, and Tree-sitter stays the shipped default.

The next step is task 4's other semantic consumers, starting with type-definition and implementation
lookup, followed by dependency/source boundaries and sustained validation before extracting PRs.

The runtime-annotation metadata correction should be extracted as an independent compiler fix,
with its annotation application and reporting-TypeInfo controls. The structured `EMB-6` module-root
diagnostic belongs with the embedding diagnostics slice. Neither requires new AST state.

Task 1 validation: a fresh compiler test run reported 474 tests with 40 existing skips; LSP tests
reported 568 with three existing skips; all seven packaged compiler-stdio tests ran. That is 1,006
executed tests, zero failures/errors, including every added regression. `:xdk:installDist` and
`spotlessCheck` also passed. The commands used both lang inclusion flags and `-Plsp.adapter=compiler`.

Task 2's first slice adds `analyzeIncomplete(Source, cursor, ...)` and
`analyzeIncomplete(ModuleInfo, sourceFile, cursor, ...)`. Both use the shared partial pipeline;
the source overload remains the convenience case. Cursors are compiler Source position tokens
for the exact input text. Standalone member/call sites before closing braces or a semicolon retain
the rest of the file and validate in the original lexical/flow context. Module parsing uses the
existing assembly and overlay hooks. Only the selected cursor's `PARSER-30` is deferred through
assembly; other syntax errors, host budgets and cancellation prevent semantic work. Validation
replays the diagnostic internally, without duplicate host delivery or emitting the damaged method.
The copied Kotlin partial view includes shared module facts and selects the site source's ranges.
No XdkAdapter capability is enabled by this slice; the remaining task-2 checkboxes still apply.

Task 2 first-slice validation: the same full command completed with 474 compiler tests (40 existing
skips), 577 LSP tests (three existing skips), and seven executed packaged compiler-stdio tests.
All 1,015 executed tests passed; the 19 partial-analysis cases had no skips. XDK installation and
`spotlessCheck` passed. The new cases cover preserved suffix declarations, module overlays and copied
cross-file facts, unrelated-file syntax errors, real narrowing, UTF-16/CRLF positions, cancellation,
budgets, exactly-once diagnostics and normal compilation continuing to reject incomplete input.

Task 2's second slice preserves actual assignment and return nodes around an `IncompleteExpression`.
The new node owns the existing incomplete-site marker as a normal AST child. It has no implicit
type, reports no successful type fit, and returns validation failure after inspecting the intact
prefix in its enclosing compiler context. This avoids reconstructing assignment scope in the
adapter; a shadowing regression compares the receiver binding with ordinary compilation. Typed
and inferred declarations, existing-variable assignments and single returns have real consumers.

Nested calls retain their preceding arguments and optional written closing parentheses, while the
partial result selects the innermost unfinished operation. Enclosing calls never choose an overload
from a fabricated argument type. This includes named final arguments and assignment/return values
containing such calls. The syntax traversal follows child links with streams: parent links are not
installed yet at this point. Ordinary AST cloning owns the wrapper and nested children; no clone
reset or mutable semantic cache is added. See the [AST placement inventory](errs.md#incomplete-statements-for-explicit-partial-analysis).

Limits remain explicit: compound assignments, binary/conditional prefixes, multiple return values,
and arguments following an incomplete argument are not covered. The original trailing-EOF overload
keeps its standalone-statement contract. No completion/signature capability is advertised yet.
Second-slice validation: 474 compiler tests (40 existing skips), 593 LSP tests (three existing
skips) and seven packaged compiler-stdio tests completed with zero failures/errors. All 1,031
executed tests passed, including all 35 partial-analysis cases. XDK installation and
`spotlessCheck` passed.

The adapter now supplies an internal `analyzeAtAsync(uri, position)` consumer on its existing
serialized compiler worker. It translates UTF-16 editor positions to compiler Source tokens and
uses the current module request's frozen overlays. Only copied partial facts leave that worker;
cursor probes do not replace the module cache or its diagnostics. A newer cursor in the same
document supersedes older work. Any edit or close in the module, explicit cancellation and shutdown
retire affected requests, including queued work. Tests deliberately let canceled compiler work
finish to verify that its facts cannot complete a canceled future. Module-root edits invalidate
member cursors, and position tests cover CRLF, supplementary characters and source escapes.

That adapter slice closed request ownership. The following protocol slice adds server cancellation
and current-document checks before delivering facts.

Adapter-slice validation: a fresh full LSP run reported 600 tests with three existing skips, and
all seven packaged compiler-stdio tests ran. All 604 executed tests passed, including seven cursor
request regressions and all 35 partial-analysis cases; `spotlessCheck` passed. The compiler sources
are unchanged from the preceding successful 434-executed-test compiler run.

Task 3's first protocol slice adds asynchronous completion/signature methods with synchronous
defaults for the other adapters. XdkAdapter schedules partial inspection on its existing compiler
worker and maps copied results with cancellation propagation. Requests coalesce by document and
feature, so completion and signature help cannot cancel one another. The server waits for the
shared module analysis without owning its cancellation, captures the document identity, and
invalidates cursor requests on edits, module changes, close/reopen and shutdown. It checks that
identity again while completing the response. Tests include a backend that ignores cancellation
and attempts to finish after the request has been invalidated.

`XdkCursorQueries` presents accessible instance members, preserving overload signatures and generic
receiver substitution. Signature help uses the actual selected call signature and written-argument
mapping where available. Unfinished qualified calls display candidate signatures, explicitly without
overload selection. Ordinary positional source slots can highlight a candidate parameter; named
partial arguments and positions without a proven mapping show the full signature label without
parameter metadata. This avoids the [LSP default-to-parameter-zero behavior](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/signatureHelp.md)
creating a false highlight. Conditional return labels omit the compiler's hidden Boolean flag.

Completion and signature help are now advertised in the opt-in compiler backend. Tree-sitter stays
the shipped default. No compiler, embedding API or AST changes were needed for this protocol slice.

| Required fact | Existing source and owner | Proven consumer / remaining boundary |
|---|---|---|
| Receiver members and generic substitution | Worker-built `PartialSemanticModel` in Kotlin | Qualified and implicit members, static functions/constants and nested types retain overloads, receiver substitution and access checks. |
| Visible lexical scope and type names | Attempt-owned `CursorBinding`, captured from compiler Context and normal name lookup | Readable locals/parameters, declaration order, shadowing, narrowed types, imports and enclosing types; Kotlin receives copied facts, never Context. |
| Typed prefix text and replacement span | Original parser `Token`, copied to `PartialSemanticModel.MemberPrefix` | Decoded prefix filters candidates; the original UTF-16 token range supplies a completion edit, including escaped identifiers. No adapter source scanning or rewriting. |
| Selected signature and source parameter mapping | Attempt-owned `InvocationBinding`, copied into `SemanticModel.CallSite` | Signature help for resolved qualified/unqualified, generic, named/default and nested calls. No selection is inferred for failed calls. |
| Incomplete call candidates and argument slots | Ordinary compiler argument fitting, captured in `CursorBinding.Candidate` | Candidate-specific generic signatures, written argument mappings and expected types; positional and named insertion slots. Rejected candidates do not become source diagnostics. No best-overload selection is claimed. |
| Current document/module lifetime | Adapter request identity and server `Document` identity | Cancellation reaches query work; edit/close/shutdown reject late responses without canceling shared analysis or replacing diagnostics. |

The cursor syntax follow-up supports `receiver.|`, `receiver.pre|` and `receiver.method(|)` at
supported statement/final-argument boundaries. Complete valid calls use their copied selected
signatures; unsuccessful calls can expose candidates through a separate cursor probe. The scope/call
follow-up below closes the bounded task-3 POC. A cursor within an identifier,
further member/call syntax after that identifier, compound/conditional value prefixes and arguments
following the cursor remain unsupported.

Protocol-slice validation: the full LSP suite ran 619 tests with three existing skips; all nine
packaged compiler-stdio tests ran. All 625 executed tests passed with zero failures/errors, including
the other adapters' existing behavior. `spotlessCheck` and `git diff --check` passed. No Java sources
or Gradle build logic changed in this slice, and remote CI was not inspected.

The cursor syntax follow-up extends the existing explicit-cursor overloads; no new embedding
entry point is needed. The parser recognizes typed member tokens in both qualified name chains
and postfix expressions, and inspects calls before consuming an existing closing parenthesis.
It preserves the original text and following declarations. Only the explicit probe treats a
selected operation as incomplete; ordinary compilation and its diagnostics keep their behavior.
The trailing-EOF convenience overload retains its existing contract.

`IncompleteStatement` gains one final optional token reference, a constructor overload and
`getMemberName()`. This is syntax provenance, owned by the parser/AST; it holds no binding,
validation context or cache and needs no custom cloning/reset. Kotlin copies its decoded text
and raw UTF-16 span, filters copied members and returns an explicit replacement edit. See the
[AST placement inventory](errs.md#incomplete-statements-for-explicit-partial-analysis).

Tests cover simple and expression receivers, assignments/returns/nested calls, module overlays,
flow narrowing, escaped identifiers, CRLF/supplementary characters, listener budgets/cancellation
and exactly-once diagnostics. Packaged stdio tests apply a completion edit and require normal
diagnostics to clear, then distinguish auto-closed call candidates from a successfully selected
signature. XTC permits a trailing comma in a complete call; such a call keeps its selected signature.

Cursor-syntax validation: 474 compiler tests (40 existing skips), 641 LSP tests (three existing
skips) and all 11 packaged compiler-stdio tests completed with zero failures/errors: 1,083 executed
tests passed. All 55 partial-analysis cases and nine completion/signature adapter tests ran without
skips. XDK installation, `spotlessCheck` and `git diff --check` passed. No Gradle build logic changed.

The scope/call follow-up captures immutable `CursorBinding` records in an attempt-owned collector.
Compiler, StageMgr and root/nested validation contexts forward the collector explicitly; ordinary
compilation uses its disabled instance. Publishing keeps only surviving site identities and clears
scratch entries. `PartialAnalysis.cursorBindings()` is additive; its previous three/four-argument
constructors remain, but record-pattern consumers must account for the new fifth component.

Scope capture uses the compiler's existing variable/assignment/branch lookup. Parameters are
initialized before capture even when no preceding expression has referenced them. Unassigned locals
are omitted as values but still shadow outer members. Bare prefixes and empty statement insertion
anchors retain their exact edit ranges. `CursorScope` enumerates implicit imports, source imports
and enclosing type declarations, then asks normal contextual lookup to resolve each name. Static
receiver completion and implicit members use access-adjusted TypeInfo on the compiler worker.

`PartialCallResolver` reuses ordinary argument fitting through a small AstNode helper. It fits each
candidate independently using cloned argument syntax and a child context, allowing missing
parameters without fabricating their values. Generic method inference and named-argument ordering
come from existing compiler routines. Records retain declaration identity separately from the
instantiated signature; a specialized method identity is not necessarily a source declaration.
Pending formals remain formal rather than becoming fabricated Object expectations. Trial mismatch
diagnostics are speculative; TypeInfo inspection reports through the supplied listener, and all
work observes cancellation. Neither candidates nor their trial calls enter selected-call records.

Kotlin copies candidate signatures, conversion flags and written parameter mappings. The final
`name=` token at a missing argument is retained as syntax, so each candidate can identify the right
parameter. `expectedTypeAt` exposes that candidate's expected type; it does not choose an overload.
A trailing slot after named arguments has no parameter highlight until its label is known.
Broader expression recovery, enclosing-instance member enumeration, arbitrary type-valued receiver
fallbacks, constructors, function values and receiver-to-argument rewrites remain explicit limits.

Scope/call validation: 474 compiler tests (40 existing skips), 660 LSP tests (three existing skips)
and all 12 packaged compiler-stdio tests completed with zero failures/errors: 1,103 executed tests
passed. This includes 58 partial-analysis cases, ten scope-completion cases, six incomplete-call
cases and nine existing completion/signature cases, all without skips. Collector tests reject
discarded/retried sites and verify immutable publication; copied expected-type queries run on a
separate thread without a compiler pool. A captured-lambda candidate has an ordinary-compilation
control. XDK installation, `spotlessCheck` and `git diff --check` passed. No Gradle logic changed.

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

Use the twenty-three bounded PRs below, including module support, hierarchy, parser recovery and
separate compiler/consumer slices for call and member facts.
Four foundations can start independently; the compiler changes form a short
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
- `compileModule(ModuleInfo, repository, errs)` reuses the CLI's module assembly with overridable
  source text and membership. Single-source input shares the same downstream pipeline.
- The adapter reports real syntax and semantic diagnostics. It also has an outline, hover,
  highlights, folding, selection, symbols from current completed modules, and cross-file definition
  and references within those modules. Source types support direct extends/implements hierarchy.
  New unsaved members, per-file versions, sibling invalidation and stale hierarchy items have tests.

This is a useful embedding foundation. It does not yet establish that every relevant compiler
diagnostic reaches every editor request, or that arbitrary project files can be compiled in isolation.

## First hardening pass on this branch

Commit `9e951df3e` addresses the initial gaps found at `a8213cf04`. The user has committed and
pushed this first pass; the results below describe local verification, not remote CI results.

| Area | Behavior established in this pass | Evidence |
|---|---|---|
| Listener composition | `tee` retains error-code queries; branches retain the parent's abort request; derived silences suppress reports while preserving cancellation. | `ErrorListenerCancelTest` |
| Embedding failures | Expected launcher/compiler aborts do not invent internal errors. An unexpected exception is reported even after a source error. Already-cancelled work does not parse; empty buffers report a source problem. | `EmbeddingDiagnosticsTest` |
| Passive metrics | `footprint(compilation)` measures that compilation's pool without starting the interpreter. `footprint()` reports repository/heap counts with no pool selected. | `EmbeddingFootprintTest` |
| Build inputs | The existing `javatools` Java dependency supplies the compiler. A test-only `xtc` configuration consumes `libs.xdk.ecstasy` and `libs.javatools.bridge`, including transitive modules. The fixture configures a repository over those resolved directories. | Gradle task graph and compiler-consumer suites |
| Test selection | Compiler/library/build changes get a focused LSP consumer lane. Lang validation runs the full suite. A required XML gate rejects missing suites, zero tests, failures, errors and skips. This is separate from IDE publishing selection. | `.github/workflows/commit.yml`; XML gate exercised locally |
| Document lifecycle | Notifications schedule compiler work asynchronously. New edits coalesce pending work and cancel prior operations. Only the current request may cache or publish. Close/reopen and shutdown invalidate previous lifetimes. | `XdkAdapterLifecycleTest`, `XdkLanguageServerTest` |
| Protocol behavior | Diagnostics carry document versions; stale versions are ignored. Features await their document analysis and return `ContentModified` if it is superseded. Outline queries no longer start duplicate compilations. | `XdkLanguageServerTest` |
| Feature claims | Compiler mode advertises bounded completion/signature help and its existing features; rename, formatting and other unavailable operations are omitted. Selection ranges preserve one response per requested cursor even without an AST. | Capability, cursor and selection tests |
| TypeInfo replay | The warning replay test now verifies that successive requests return the same cached `TypeInfo` and hear the same diagnostics. | `TypeInfoDiagnosticsTest` |

The test dependencies require neither archive unpacking in `lang`, an `installDist` prerequisite,
nor a manually configured `XDK_HOME`. They use the compiled-module artifacts the composite already
publishes. At that point the production adapter still used the existing `XDK_HOME` configuration
path; the third pass below replaces that LSP dependency with bundled resources.

### Second pass: local navigation identity

Continue on the existing branch with local-variable definitions, references and highlights. This
is a bounded compiler-accessor/adapter change: expose `VariableDeclarationStatement.getRegister`,
normalize narrowed uses through the existing `Register.getOriginalRegister`, and compare those
original registers by object identity. Register value equality and register indices are not stable
source identities across methods. Keep constants as the identity for module members. Add name-token
accessors for property/method/type declarations and an invoked-expression accessor so callers can
select identifier spans and distinguish a callee from its arguments without source heuristics.

Prefer this to recreating lexical scope in the adapter or adding a new compiler symbol index.
Return the declaration name span, respect `includeDeclaration`, and use the same target matching
for highlights. Unresolved names must not fall back to unrelated names or enclosing calls.
Regression fixtures cover sibling scopes, separate methods, declaration-site queries, narrowed
locals, call arguments and a local shadowing a property. Parameters and generated declarations
remain separate source-mapping work if their identity is not retained on a source node.

- [x] Demonstrate the navigation defects with source-level regressions.
- [x] Expose the declaration register and replace spelling-based matching.
- [x] Share semantic matching with highlights and verify exact source spans.
- [x] Run compiler-consumer and broader LSP tests; update remaining limits.

The second pass adds nine compiler-backed navigation tests and one server-service test. The latter
checks protocol conversion and `includeDeclaration` through the real adapter. CI requires the new
navigation suite alongside the existing compiler-consumer suites. Full forced test results for
the second and third passes are recorded below.

### Third pass: diagnostic fidelity and compiler reporting audit

The approved next pass is diagnostics first, followed by the remaining declaration mapping.
Preserve the source URI of positioned diagnostics in adapter results. When the source differs from
the document being compiled, publish a document-level diagnostic with the original location as
related information. This keeps publication ownership and clearing tied to the current document;
publishing directly into other documents requires the deferred project-compilation design. Never
reinterpret an unnamed or relative foreign source as a position in the current buffer.

The packaged server must be self-contained. Consume the compiled XDK module variants as production
resources, load them from the classpath and use the same resources in tests. The earlier fat JAR
bundled compiler classes but zero `.xtc` libraries; the test-only dependency concealed that gap.
No external installation, `XDK_HOME`, archive extraction or `installDist` prerequisite is needed.
Remove the adapter's blanket `IllegalStateException` catch: a damaged package is an analysis failure,
not an instruction to install an XDK. Validate UTF-16 positions, line endings and incomplete-edit recovery.
Audit compiler-reachable silences and failure sinks by caller and source reproducer, preserving
deliberate speculation. Verify the actual stdio launcher and current feature claims.

- [x] Preserve diagnostic source attribution through publication and test source positions.
- [x] Bundle compiler libraries and verify independence from external XDK configuration.
- [x] Triage compiler reporting paths and fix reproduced repository diagnostic losses.
- [x] Exercise the real stdio transport and repeated edit/close/shutdown behavior.
- [x] Complete method/constructor parameter mappings and remaining type-name span accuracy.
- [x] Reconcile historical documentation and record verification and remaining limitations.

A semantic API is the follow-on design: one versioned compilation snapshot exposing resolved
types, symbol identities, declaration/use locations and callable signatures, with explicit
unresolved/partial results. It should present facts computed by the compiler and define their
lifetime, rather than duplicate type checking in the LSP adapter. Completion on incomplete text,
cross-file identities and project overlays remain separate requirements. The diagnostics pass
precedes implementing that API.

The third pass also fixes stdio `exit`: the notification previously logged and left the process
alive even after a successful `shutdown`. The production launcher now supplies the process-exit
callback; embedded service tests can observe the exit status without terminating their JVM.
`FileRepository` and `DirRepository` now propagate unreadable/corrupt-module failures instead of
printing them to stdout and returning no module. `EMB-5` includes the retained exception in its
message. Repeated reads must not turn a failed load into a cached absence. Missing modules still
return no result; corrupt modules in a searched repository now fail visibly.

The packaged compiler was exercised with `XDK_HOME` unset, set to a nonexistent path, and with a
bootstrap resource deliberately removed from a temporary copy of the JAR. The first two compile
normally; the damaged package produces `ANALYSIS-FAILED`. The stdio check sends 100 rapid edits,
waits for correction, queries symbols, closes/reopens the document and shuts down during queued
edits. It passed after reproducing the exit hang. This is a process/transport test, not a claim of
interactive IDE or multi-hour memory validation.

### Remaining limitations and the next hardening work

This list records the boundaries after the first three passes. The
[eighth pass](#eighth-pass-module-sessions-and-hierarchy-2026-09-22) supersedes the deferred
module/cross-file work and selected final-type checks; its remaining-boundaries paragraph is current.

1. **Remaining declaration mapping.** Locals, method parameters, constructor-generated properties,
   lambda parameters and lambda capture chains now have source associations, including narrowing
   and mutable capture dereferencing. Qualified type segments retain their individual identities.
   Type-parameter declarations and anonymous-class capture origins now have focused coverage,
   including mutable/nested captures and shadowing. Cross-file targets remain unavailable; the
   snapshot leaves unsupported associations unresolved rather than guessing.
2. **Incomplete source.** Some parser failures leave no AST, so outline/navigation disappear until
   the buffer parses. The selection fallback preserves protocol shape but does not recover syntax.
   Empty text, missing braces, incomplete member access and an unterminated string now have recovery
   tests. Compiler recovery versus a hybrid syntax adapter remains an explicit design decision.
3. **Diagnostic source attribution.** Foreign-source diagnostics retain navigable related locations;
   unnamed/relative foreign sources use a document-level message identifying the limitation.
   Structure-only and nowhere diagnostics still use `(0,0)`. Publishing directly into dependency
   documents requires project ownership/versioning rules, not just URI grouping.
4. **Compiler failures with no listener path.** Continue the suppression audit using specific source
   reproducers. This pass fixes decorator propagation and the embedding catch policy, not every
   historical probe/silence or runtime failure sink listed in `errs.md` and `errs-audit.md`. The
   fresh audit classifies 37 distinct messages from the measured XDK build and adds generic and
   serialized-dependency regressions. No new lost diagnostic was reproduced. Selected final-type
   checks remain open; the historical set of 70 is not claimed to be closed.
5. **Operational validation.** Actual stdio transport, bundled-library startup and shutdown now have
   successful process checks, and the VS Code extension-host smoke test passes with the compiler
   backend. A bounded 180-analysis workload released all tracked ASTs, pools and snapshots. Visual
   editor review, multi-hour behavior and latency inside slow compiler stages remain unmeasured.
6. **Project compilation is deferred by scope.** Member files, source roots, module ownership,
   dependency repositories and unsaved overlays need a separate design. Workspace symbols currently
   cover completed analyses of open documents only. Compiler cancellation remains cooperative.

The implementation is in [XdkAdapter](../lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt),
[the document service](../lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt)
and [EmbeddingSupport](../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java).
The build/CI contract is in [the test task](../lang/lsp-server/build.gradle.kts) and
[the commit workflow](../.github/workflows/commit.yml).

### Readiness review before extracting PRs, 2026-09-22

The diagnostic/lifecycle gate passes, including the eighth pass's module consumer. This is evidence
for the integrated branch, not proof that every extracted intermediate PR works. The module tests
found and fixed a further fatal-forwarding defect in Launcher. The following table reflects the
current readiness checks; cross-module support and completion remain separate milestones.

| Priority | Work | Evidence and completion condition |
|---|---|---|
| Done; carry into L1 | Reject unintended backend fallback | Tree-sitter remains the shipped/default backend, including when the embedded setting is absent. Compiler selection is opt-in. Unknown settings now fail explicitly instead of silently selecting Mock; explicit Mock and existing compiler aliases remain supported. Unit and packaged-startup regressions pass. |
| Bounded investigation complete; retain regressions in C4 | Generic/external-type diagnostic ownership | Generic instantiations and a serialized external base preserve `VERIFY-75`; silent first lookup preserves replay. The fresh TypeInfo capture classifies 37 distinct messages by source shape and caller. Permanent final-composition tests inspect fifteen deserialized types and selected member/inheritance substitutions, plus a fresh property and invalid-override control. No new TypeInfo host diagnostic loss was reproduced. The historical 70-message survey and `@Parsed` constructor family remain open. See `errs-audit.md`. |
| Done for the selected module scope; carry into E2/L4/L5 | Source bindings and snapshot boundaries | Type-parameter declarations, aliases, overloads, nested generic types, anonymous captures, mutable/nested capture chains and shadowing have regressions. Clones cannot expose the original helper maps. Per-source views share one immutable identity domain; different compilations remain distinct. Cross-file navigation now has adapter/server tests. The AST inventory in `errs.md` explains placement and lifecycle. |
| Done; carry into L1/L4/L5/L6 | Semantic queries through the packaged server | Real stdio tests assert hover, definition, references, highlights and advertised capabilities after correction and close/reopen. They cover module diagnostics, cross-file definition and type hierarchy with stale-item rejection, plus resources, invalid backend selection and shutdown. |
| Bounded checks complete; longer/manual checks open | Interactive use, latency and retention | Seven VS Code extension-host checks pass with the compiler backend, including hover. The 180-analysis workload retained 0/180 ASTs, pools and snapshots after close/shutdown/GC; warmed median was 58.6 ms and p95 78.1 ms. One editor compilation logged 2.1 ms queue wait and 360.5 ms compilation. These are local samples, not latency guarantees or multi-hour/visual validation. |

Agreed API compatibility policy (2026-09-22): `log(ErrorInfo)`
changes from `boolean` to `void`, null listeners are rejected, and ambient listener setters/lookups
are removed. These are deliberate compatibility changes, not additive APIs. The
`getConstantPool()` to `ensureRuntimePool()` rename retains a deprecated delegating alias with
the same runtime-initialization behavior. The return-type change cannot preserve compatibility
through an ordinary overload: C1 must be released as an explicitly breaking change, with callers
and implementors recompiled and migrated to separate reporting from cancellation. Do not claim
source or binary compatibility for C1/C2. Each slice must state its policy and pass tests on its own
intermediate state; the integrated branch passing does not satisfy that gate. Compiled-XDK output equivalence, with
only the known timestamp normalized, also remains a per-slice gate where compiler behavior is touched.

Documentation was reconciled in this review: the LSP and lang READMEs now select `compiler` and
describe the bundled XDK; the IDE matrix reflects snapshot-backed queries and their limits;
`errs.md` distinguishes old work lists from completed hardening; and the research-fork tier document
is explicitly historical. Its Kotlin lexer/parser, member index, flow-analysis APIs, coverage numbers
and schedules do not describe the current snapshot. `errs-audit.md` remains the classified failure
backlog; its historical counts are not fresh measurements of this worktree.

Deferred capabilities are still visible outages when choosing the compiler backend: incomplete
syntax can prevent an assembled module AST; recovered per-file syntax supports structural features,
while navigation requires semantic results for the current module's source files, with no
cross-module index or library-source lookup. Completion/signature help have the bounded support
described above; rename, semantic tokens, formatting, code actions, document links and method
implementation lookup remain unavailable.
The snapshot has declared/instantiated call signatures and direct type hierarchy; explicit partial
inspection copies bounded receiver-member candidates. A persistent workspace identity scheme,
broader incomplete-call inference and incremental compilation remain open. These are feature
boundaries, not reasons to delay the listener foundation indefinitely.

Runtime/Container failure listeners, JIT/debugger sinks, diagnostic thread/fiber origins, SLF4J/JFR
listener sinks and a concurrent `ErrorList` remain outside the compile-only scope. Filing the
historical upstream `VERIFY-75` issue is optional publication work; the branch already fixes and
tests that warning loss.

## Proposed PRs and dependencies

“Independent” means no dependency on another proposed PR's behavior. Extraction conflicts may still
occur because the source commits were written on top of the whole branch. Commit IDs identify
provenance, not a promise that an unedited cherry-pick compiles.

| ID | Scope | Prerequisites |
|---|---|---|
| I1 | Preserve distinct diagnostics and name in-memory sources | Independent |
| I2 | Guard ambient constant-pool reads | Independent |
| I3 | Establish compiler-consumer test wiring without requiring IDE builds | Independent foundation; activate required suites as they land |
| R1 | Propagate repository read failures and preserve retry behavior | Independent; embedding regression joins E1 |
| C1 | Define the host listener contract and reporting API | Independent of ownership work; coordinate public API compatibility |
| C2 | Require explicit listeners and explicit reasons for silence | C1 |
| C3 | Scope parser/resolver reporting and statement validation state | C2 |
| E1 | Return useful compilation results through the embedding API | I1, C2, R1; I3 for compiled-XDK tests |
| E2 | Expose resolved source bindings, lambda origins and qualified segments | E1; I3 for direct compiler-consumer tests |
| C4 | Replay TypeInfo diagnostics and remove ambient listener ownership | C2, C3; E1 and I3 for the downstream regression tests |
| L1 | Connect the diagnostic-only XDK adapter and prove publication | I1, I3, C4, E1 |
| L2 | Complete cancellation and document lifecycle handling | L1; includes the listener cancellation decorator |
| L3 | Add the outline and structural AST features | E1, L2 |
| L4 | Snapshot semantic facts in Kotlin and use them for navigation and hover | E2, L3; I2 for ambient-pool handling |
| E3 | Compile source trees with host text/membership and member cancellation | E1, C3; L2's listener decorator for cancellation; I3 for compiled-XDK tests |
| L5 | Add module sessions, per-file publication and cross-file navigation | E3, L2, L4 |
| L6 | Copy direct inheritance edges and support source type hierarchy | L5 |
| C5 | Recover syntax and expose per-file partial source results | E3; L5 for the structural LSP consumer |
| C6 | Analyze a bounded incomplete statement through an explicit API | C5 and E2; I3 for compiler-consumer tests; no new LSP capability |
| E4 | Return attempt-owned selected-call bindings without new AST fields | E2 and C6; retain existing result constructors and document record-pattern changes |
| L7 | Copy selected calls and inspect bounded partial receiver members | E4, C6 and L4; no new protocol capability |
| C7 | Extend partial analysis to source cursors, module overlays and value contexts | C6 and E3; no new protocol capability |
| L8 | Connect bounded completion/signature requests with cancellation and version checks | C7, L7 and L5 |
| C8 | Capture cursor scope and resolve visible type names without AST caches | C7 and E4; I3 for direct compiler consumers |
| C9 | Fit incomplete-call candidates and preserve named argument slots | C8; reuse compiler argument fitting without changing full-call selection |
| L9 | Consume scope, static lookup and candidate-specific expected types | C8, C9 and L8 |

Suggested landing order: I1, I2 and R1 first; I3 alongside C1; then C2, C3, E1, C4, L1 and L2.
E2, L3 and L4 can follow without delaying the diagnostics milestone; E3, L5 and L6 extend it
additively. C5 follows with structural recovery; C6 isolates the explicit partial-semantic probe.
E4 and L7 separate compiler provenance collection from the Kotlin call/member models. C7 extends
the partial compiler API; L8 proves the asynchronous editor consumer and protocol delivery.
C8/C9 isolate live scope capture from tentative call fitting; L9 adds their Kotlin/protocol consumers.
These are twenty-six eventual PRs, not twenty-six simultaneous open branches.
Keep only the next few ready for review, and update dependent patches
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

All eighteen guarded reads predate this branch. The earlier FileStructure null-pool fix is already
in the base via `5effa757d` (#548); it is not an additional fix to extract here. C4 removes that
listener lookup altogether. The [listener write-up](errs-error-listeners.md#ambient-constant-pools-pre-existing-defects-versus-branch-changes)
records the provenance and distinguishes reproduced failures from preventative guards.

Use `MethodBodyAmbientPoolTest` and `ConstantPoolAmbientTest`, including the bound-pool precedence
case. Compare compiled XDK output with the baseline after removing only the known timestamp
difference. This PR guards null ambient reads; it does not make the compiler concurrent or replace
ambient pool ownership.

### I3 — Establish compiler-consumer test wiring

**Contract:** a clean supported build runs the compiler-backed consumer tests against the XDK it
just built, and compiler-only PRs exercise that consumer.

Use IDE lifecycle separation from `7097892b6`. Reassess the default attachment change in
`566bc0c4a` as build policy rather than importing it automatically. Include the first hardening
pass's test dependency and shared fixture. The final branch names the configuration `compilerModules`
and loads its classpath resources through `XdkLibraries`; those production pieces accompany L1.
I3 can initially configure its test repository directly from the resolved module artifacts.
The javatools dependency from `bb3c4c62c` can enter here when the first embedding test needs it.

Consume the existing compiled-module variants directly; do not add packaging, extraction or
installation tasks. The dependency direction remains compiler -> compiled XDK -> embedding tests;
do not make javatools tests depend on compiling the XDK with their own `check` lifecycle.

Make CI selection cover `javatools`, the embedding API and relevant build/dependency changes,
not just `lang` paths. The required suite must assert that expected classes executed with zero
skips. Optional standalone tests can still use assumptions, but cannot satisfy the required gate.
Test configuration-cache storage and reuse with a real task. IDE packaging and publication remain
separate consumers with their existing explicit selection.

### R1 — Propagate repository read failures

**Contract:** a corrupt module produces a failure with its path and cause, including on repeated
reads; absence remains an ordinary missing-module result.

Take the third pass's `FileRepository` and `DirRepository` changes and their focused tests.
Replace stdout-and-null handling with `UncheckedIOException`, let unexpected runtime failures
propagate, and update cache state only after successful reads. Invalidate the old directory-cache
format, which could remember a corrupt module as absent. Verify header and payload failures,
repeated reads and recovery after replacing a corrupt module.

This changes repository failure policy and needs its own review: a corrupt module in a searched
directory now fails visibly. The embedding-level test and `EMB-5` message change belong to E1;
R1 itself does not depend on the new embedding API or an LSP consumer.

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
differs only by return type. The agreed policy is an explicitly breaking release boundary for
C1/C2; its release notes must list the migration and recompilation requirements. Selecting the
release/version remains a submission decision. Do not describe the branch as wholly additive.

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

Preserve the deprecated delegating `getConstantPool()` alias when adding the clearer
`ensureRuntimePool()` name, as agreed for this slice. The absence of in-tree callers does not establish that a
public method has no external callers. Document the runtime initialization side effect explicitly.
Do not bring `footprint()` across unchanged.

Verify success, semantic failure with retained AST/pool, parse failure, no configured XDK, missing
bootstrap modules, and correct named-source diagnostics. Distinguish expected compilation aborts
from internal failures: include the hardening pass's separate `LauncherException` catch and
regression for an unexpected failure following a source diagnostic. Include the empty-buffer fix
and the named `Compilation.forFile` factory. Test cancellation separately in L2.
Include `EmbeddingRepositoryFailureTest` and the `EMB-5` message correction so R1's retained
exception reaches the host's displayed diagnostic with the failing module path.

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
TypeInfo, and appearing once in an `ErrorList`. Carry the first pass's assertion that successive
requests return the same `TypeInfo` instance, proving replay without a rebuild. Serious-error cases
rebuild by design and do not prove replay. Check behavior after invalidation and with a fresh sink.

Include `TypeInfoFinalCompositionTest` and its test-only XML/JSONDB module variants. They retain
the deeper audit's final metadata checks and invalid-override control, without changing production
reporting policy or enlarging the LSP's production module bundle.

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
Include the third pass's production `compilerModules` resources, generated module index and
`XdkLibraries` loader. Tests must use those same bundled resources. The server must not require
`XDK_HOME`, a developer installation, archive unpacking or an `installDist` dependency.

Carry the compiler-backed `LanguageClient` publication tests from `XdkLanguageServerTest`;
`LspIntegrationTest` uses tree-sitter or mock, and `LspRoundTripTest` exercises the compiler/mapping
rather than the server.
Cover clean -> error -> corrected, two URIs, `VERIFY-75` exactly once, source spans, startup with
unset/invalid `XDK_HOME`, and a damaged bundled bootstrap resource. Include Unicode/line-ending
span cases and incomplete-edit recovery. Structure-only and nowhere diagnostics
currently map to `(0,0)`; document that fallback rather than claiming precise locations for them.

Carry the third pass's source attribution: retain the source URI for `Site.In`, and represent
foreign-source diagnostics with a document-level message and related location. Unnamed/relative
foreign sources must not borrow offsets in the current buffer. Verify clearing on close. Advertised server
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
Include the production launcher's exit callback and shutdown/exit status tests. Verify the packaged
stdio process terminates after shutdown and exit; service-level tests alone missed this hang.

### L3 — Add the outline and structural AST features

**Contract:** the cached analysis supplies positions and structural editor features without
recompilation, and document close releases the retained tree.

Take the adapter/`XdkSymbols` portion of `91d1b08e1` and the `XdkAst`/structural portion of
`956d56f41`: document symbols, containing declaration, folding, selection, declaration
hover and symbol search over cached documents. The result API already carries the AST from E1.

Tests should cover nested declarations, failed validation with a usable AST, a failed parse with
no AST, repeated/shared nodes and cleanup. Keep highlights with L4's semantic matching rather than
extracting the superseded spelling-based implementation. Symbols from compiled documents are not
a complete workspace index.

### E2 — Retain compiler source bindings

**Contract:** compiler consumers can read resolved targets and their source associations without
restarting resolution or changing runtime register identity.

Take the Java accessors from `956d56f41` and the later declaration-register/name, invocation and
method-parameter associations. Include constructor-generated property identities, the final-name
token accessor, and the fifth pass's qualified segment recording and context-owned `LambdaBindings`.
Keep snapshot/query logic out of javatools. Direct binding tests through I3 must cover nested and
mutable captures, inferred/typed parameters, narrowing, unresolved prefixes and clone ownership;
extract these from the consumer cases without requiring the L4 snapshot implementation.

### L4 — Snapshot semantic facts and add limited navigation

**Contract:** an immutable Kotlin snapshot supplies supported same-file semantic answers without
touching compiler objects on request threads or matching names by spelling.

Use the final `SemanticModel`, builder and `XdkAdapter` consumer in the existing LSP module.
Do not introduce a separate library, add Kotlin to javatools, or land the superseded
`XdkResolution` walker. Build the snapshot on the compiler worker and preserve L2's document/version
ownership. It exposes structural types, declared signatures, symbol identities and source spans,
with explicit partial/unresolved results and IDs scoped to one snapshot. Keep structural AST
features with L3.

Pin property identity across classes, overload-selected calls, types, constructors and unresolved
names. Carry `XdkNavigationTest` and the server navigation regression: declarations, sibling scopes,
separate methods, narrowing, shadowing, call arguments, `includeDeclaration` and exact name spans.
Original-register identity connects supported locals and narrowed method parameters to declarations;
property identity connects constructor-generated properties to their source parameters. Return no
definition when a source association is unavailable. Include `SemanticModelTest` in the required
zero-skip gate. Type-parameter declarations and anonymous-class captures now have regressions.
Cross-file targets remain follow-ups before rename can be supported safely.

### E3 — Compile module source snapshots

**Contract:** a host can compile the same module tree as the CLI while supplying immutable source
text and membership, and cancellation reaches member parsing without inventing an error.

Extract `compileModule(ModuleInfo, ...)`, the shared Source/tree pipeline and `compile(File, ...)`
delegation. Include the protected `readSource` and `sourceEntries` hooks, `SourceEntry`, cancellable
`Node.parse(errs)` and valid-token abort checks in Lexer. Preserve default disk discovery and
resource context, including resource-only and empty implicit package directories. Require a fresh
ModuleInfo per attempt. The single-source convenience remains a special case of compilation.

Keep Kotlin editor URIs, versions and sessions out of this API slice. Adapt `CompilerProjectTest`
and `EmbeddingDiagnosticsTest` to verify module/member entry points, new virtual members, source
attribution, parse failure and cancellation. Carry the Launcher fatal-forwarding correction with
its direct regression here (or extract it independently against the old listener API): a host must
receive the original structured FATAL before the CLI exception. No semantic AST fields are needed.

### L5 — Own module analyses and publish by source

**Contract:** one edit replaces all current views of a module; diagnostics and navigation refer to
the correct source/version, including unchanged siblings and closed members.

Extract `XdkSources`, module-scoped request/cache ownership, per-source immutable semantic views and
the server's publication/clearing rules. Preserve a single compiler worker. Source names map through
canonical paths to editor URIs; no temporary source files or dependency builds are introduced.
One snapshot identity domain supplies cross-file definitions/references; document highlights and
structural ranges stay local. Workspace symbols include closed members of current completed modules.

Carry module session/server tests for unsaved members, sibling invalidation, parser failure recovery,
watched creation/deletion, cancellation, per-file versions, close/overlay removal and cross-file
navigation. Include the corresponding packaged-stdio case without its L6 hierarchy assertions.
Document conventional root discovery and the absence of a persistent cross-module index.

### L6 — Support direct source type hierarchy

**Contract:** prepare/supertype/subtype queries use copied direct extends/implements relationships
from a successful module compilation, and reject items from obsolete compilations.

Extract the type-declaration/supertype facts, generic parent types, reverse subtype index and
`XdkHierarchy` queries. Include capability selection and string token conversion through real LSP
JSON-RPC. Test generic parents, interfaces, cross-file source locations and stale items. The extractor
reads existing class contributions; it must not build TypeInfo or validate from request threads.
External library source, conditional mixins and method implementation lookup remain explicit limits.

### C5 — Retain recovered syntax without semantic compilation

**Contract:** malformed statements and unfinished bodies can retain surrounding syntax while
parsing errors still prevent semantic compilation. Cancellation, budgets and speculative rollback
keep their existing control-flow meaning.

Extract the ninth pass's parser boundary recovery, `ModuleInfo.getParsedSources()` and
`Compilation.sourceTrees()`. Keep the three-argument construction API, and document the additional
record component's effect on record-pattern consumers. The embedding parser uses a stage-local
collector: the launcher's decision to stop subsequent compilation stages must not stop recovery
after the first ordinary error. The host's budget and cancellation still stop parsing.

Carry `ParserRecoveryTest`, `XdkRecoveryTest` and the packaged structural-query regression. The
small adapter change reads per-source trees for outlines/folding/selection only; it does not infer
semantic facts from omitted statements or reuse an earlier version's types. Compare valid compiler
outputs and preserve exact ranges around real closing braces. No Tree-sitter fallback is included.

### C6 — Analyze a bounded incomplete statement explicitly

**Contract:** an opt-in, single-source partial-analysis attempt may validate intact receiver and
argument expressions in one trailing standalone statement at EOF. It exposes no compiled module
and does not select an unfinished call's overload or invent its result/arguments.

Extract `Parser.forPartialAnalysis`, `IncompleteStatement` and
`EmbeddingSupport.analyzeIncomplete`/`PartialAnalysis`. Keep normal compilation's parse-error gate.
The statement must fail method-body validation before emission. Retain the parser's rollback,
ordinary invocation parsing and source positions, and the host's cancellation/error budget across
phase boundaries. Replay the EOF internally without delivering it twice to the host. Include the
receiver/flow/argument consumer tests, clone/speculation regressions and repository-failure control.
This API is additive and does not change the `Compilation` record shape. Integrating a richer
copied model or advertising completion/signature help belongs to subsequent consumer work.

### E4 — Return selected-call provenance from the compilation attempt

**Contract:** completed method invocations expose the compiler's instantiated signature and written
argument-to-visible-parameter mapping, without adding state or clone rules to InvocationExpression.
Extract InvocationBinding and its attempt-owned collector, explicit compiler/stage/context
forwarding, successful-validation capture, and immutable maps on Compilation/PartialAnalysis.
Keep old constructors, but document the changed record-pattern arity. Default compiler clients
use a no-op collector. Verify named/default/generic calls, nested compilation paths, surviving-node
identity, failed calls and immutable publication with direct compiler-consumer regressions.
Function values, partial application and receiver-to-argument rewrites remain explicitly absent.

### L7 — Copy call facts and bounded partial receiver members

**Contract:** compiler-worker extraction produces compiler-free Kotlin call/partial-site models.
Completed calls copy E4's inferred signature and source mapping; incomplete calls expose accessible
same-named receiver candidates and source argument slots without claiming overload selection.
Receiver TypeInfo inspection is explicit, reports through its supplied listener and respects
cancellation. Preserve access checks, receiver substitution, immutable collection boundaries and
foreign-compilation ID rejection. Keep ordinary snapshot extraction passive. These APIs prepare
completion/signature help; protocol advertisement and module lifecycle integration stay separate.

### C7 — Preserve cursor and module context during partial analysis

**Contract:** the single-source convenience entry point and module entry point share the partial
pipeline without changing source text. Preserve following declarations and real assignment/return
contexts, including final nested arguments. Extract the explicit cursor overloads, the attempt-owned
ModuleInfo parsing function and `IncompleteExpression`. Include typed member-token retention and
inspection before existing closing parentheses. Keep ordinary compilation's error gate, original
source spans, diagnostic budgets and standard AST child copying. Carry the module-overlay,
shadowing, nested-prefix, complete-source controls and unsupported-syntax tests. There is no editor
capability in this slice.

### L8 — Deliver bounded completion and signature help through the server

**Contract:** asynchronous cursor work returns copied facts for the current document/module lifetime,
and protocol cancellation reaches that work without canceling shared compilation. Extract adapter
cursor scheduling, `XdkCursorQueries`, additive async adapter entry points, copied member prefixes
and completion edits, per-signature parameter metadata, server invalidation and the capability
changes. Verify other adapters retain their sync behavior. Carry access/generic/named/default/nested-call
tests, deterministic cancellation tests and
the packaged stdio consumers. Keep unsupported scope/syntax and incomplete overload inference
explicit in the capability document. Its token and closing-parenthesis hooks belong to C7.

### C8 — Capture cursor scope without retaining validation contexts

**Contract:** explicit name/empty-statement cursors return readable variables and their narrowed
types, while shadowing and type-name resolution follow the compiler. Extract CursorBinding and
collector propagation, Context capture, CursorScope, implicit-import name enumeration and the
name syntax anchors. Keep old constructors and document PartialAnalysis record-pattern arity.
Direct consumers must verify declaration/import order, narrowing, budgets, cancellation and
discarded-clone/retry cleanup independently of the LSP implementation.

### C9 — Inspect incomplete-call candidates using ordinary argument fitting

**Contract:** candidate signatures can infer from written arguments and map named parameters,
without selecting an unfinished call or fabricating missing values. Extract PartialCallResolver,
the AstNode fitting observer, candidate records and pending-label syntax. Verify generic receiver
and method inference, named ordering, invalid names, conversions and capture-bearing trial arguments;
full-call selection and compiled outputs must retain their independent controls.

### L9 — Consume copied scope and candidate-specific call facts

**Contract:** completion and signature help use only copied worker facts. Extract scope/type/static
queries, copied candidates, expected-type and named-slot mapping, capability documentation and
adapter/stdio regressions. Preserve module invalidation and cancellation. Keep missing syntax and
callable forms explicit; one surviving candidate is still not a compiler-selected call.

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

The fourth branch pass makes the packaged stdio checks permanent regression tests and adds
source cases for the four priority TypeInfo families in the audit. All four preserve the expected
warning once; the missing-assignment-operator case reports an invalid operation during validation.
Caller tracing identifies the later assignment lookup as code generation and the superclass
constructor lookup as following an explicit reporting call. No production listener migration is
justified by these examples; generic/external-type cases and the broader suppression survey remain.
The fifth pass below implements the first semantic snapshot from compiler-owned facts and migrates
the existing LSP queries to it. Completion and project-wide compilation still need source-recovery
and ownership contracts. Continue hardening this branch before extracting the PR slices.

The stdio harness is a separate JUnit/Gradle task consuming `fatJar` as a declared input. It
launches the production main in a child JVM and talks through an LSP4J client. Keep the existing
build-time backend selection: run this task with `-Plsp.adapter=compiler`, without a test-only
launcher or runtime override. Unit tests exclude its tag; compiler/LSP validation runs the task
explicitly and checks its XML for zero skips. Cover unset/invalid `XDK_HOME`, rapid edits,
correction, close/reopen, missing bootstrap resources and exit with/without shutdown. Use bounded
waits, capture stderr in the test directory and always terminate child processes on failure.

Run it locally with:

```bash
./gradlew :lang:lsp-server:compilerStdioTest \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

The first semantic slice should expose immutable facts from one compilation: source spans,
resolved types, symbol identities, declaration/use relationships and callable signatures. Symbol
identities belong to that snapshot; the LSP associates it with a document version and rejects stale
queries. Extract facts while compiler state is owned by the serialized worker, so ordinary queries
do not build TypeInfo or mutate a retained AST on request threads. Start by replacing the current
same-file navigation and typed-hover plumbing, with explicit unresolved/partial results. Capture
origins and qualified-name segments are the next source associations to add. Completion, rename
and project indexing are consumers with further requirements, not capabilities implied by merely
introducing a model. This API is proposed, not implemented in the fourth pass.

Fourth-pass verification on 2026-09-22: a forced run of `test compilerStdioTest` executed all
113 tasks. XML reports 484 LSP tests (zero failures/errors, three existing skips) and four stdio
tests (zero failures/errors/skips). The required gate covers 69 in-process compiler-consumer tests
plus those four process tests. Configuration-cache storage and reuse passed. The workflow XML
gate was exercised locally, and actionlint has the same 89 existing findings as the parent commit.
The fourth pass was committed and pushed as `45fa0ab13`; remote CI was not inspected.

### Fifth pass: semantic snapshots and their LSP consumer

The semantic model and builder live in Kotlin in the existing LSP server module, independent of LSP
protocol types. There is no new library or Gradle dependency, and javatools remains Java-only.
The `Compilation.semanticSnapshot()` Kotlin extension copies facts on the compiler worker after
compilation; the Java embedding API and three-part compilation result stay compatible. Each snapshot
has its own identity and immutable type, symbol, signature and occurrence data; it retains no AST,
pool, register or TypeConstant. Types include their displayed form and structural relationships.
Source names and unresolved facts are explicit nullable values.
Complete, partial and unavailable snapshots distinguish successful compilation from usable partial
analysis and a missing parse. Snapshot queries never compile or build TypeInfo.

The adapter creates one snapshot per analysis and uses it for hover, definition, references and
highlights; existing request/version ownership still rejects stale analyses. Structural outline,
folding and selection retain the AST in this pass. The duplicate `XdkResolution` walker is removed.
Capture associations live in a `LambdaBindings` helper owned by the existing lambda compilation
context; AST cloning has no new state to copy or reset. Small AST accessors expose these bindings
and qualified-type segment identities retained during resolution. Runtime register identity is
unchanged, and no source association is reconstructed by matching spellings. Coverage includes
nested/mutable captures, lambda parameters, qualified and inherited type names, unresolved names,
distinct compilation identities and concurrent snapshot queries. Completion and project compilation
remain outside this pass.

Fifth-pass verification on 2026-09-22 used `--rerun-tasks --no-build-cache` for compiler, LSP and
packaged-server tests. JUnit XML reports 462 compiler tests (zero failures/errors, 40 existing
skips), 497 LSP tests (zero failures/errors, three existing skips), and four packaged-server tests
(zero failures/errors/skips). The required compiler-consumer subset contains 82 in-process tests
plus those four process tests, all with zero skips. `spotlessCheck` and `git diff --check` pass.
This pass's changes remain local; remote CI was not inspected.

1. **Make the selected final-type checks permanent regressions.** The latest capture classifies
   37 distinct messages; the historical 70-message survey remains separate. Fresh-JVM probes of
   serialized modules now verify the array assigned-properties, XODB anonymous map overrides and
   XML virtual cursor metadata with reporting listeners and no diagnostics. Preserve those member
   types and inherited chains in tests, plus the fresh-source property case and an invalid-override
   control. The
   refreshed lexical count is 121 no-argument calls across compiler, asm, runtime, JIT and API
   directories; this is not a call-graph classification. Historical totals of 124/126 and the
   estimate of 53 compile-time sites are not a current migration scope. Instrument chosen cases.
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
4. **Extend the semantic model where a consumer needs it.** The first snapshot now supplies
   resolved types, symbol identities, declaration/use spans, declared signatures and explicit
   partial results. Type-parameter declarations and anonymous-class captures are now covered.
   Add project compilation, cross-file source ownership and identities stable across snapshots
   before claiming safe rename. Build cross-file definitions/references and a workspace index on
   that foundation. Type hierarchy and find-implementations then need copied resolved supertype
   relationships and a reverse index for subtypes; call hierarchy needs resolved call edges.
   Signature help and semantic tokens can be earlier consumers where validated source provides
   enough information; completion still depends on the incomplete-source decision and resolved
   member/call-site facts. Keep model/query code in the existing Kotlin LSP module until a separate
   consumer justifies extracting a library.
5. **Continue the failure audit by reachable failure path.** The refreshed audit corrects the
   `Exception`/`Error` distinction and separates speculation, commented code and bootstrap output
   from actual compiler sinks. Repository stdout losses now have fixes and source-level host tests.
   Use its remaining classifications to select the next reproducer; neither broad catches nor
   print statements alone prove a lost diagnostic. Runtime-only cases get independent fixes.

### Sixth pass: readiness work, 2026-09-22

The compiler remains opt-in; Tree-sitter is the shipped default. The launcher rejects unknown
backend settings and its missing-property fallback now agrees with the build default. The old
`getConstantPool()` name delegates to `ensureRuntimePool()` with deprecation documentation.
The agreed breaking listener policy and each-PR verification gate appear above.

Source bindings now include class/method formals (including abstract methods), typedef aliases,
anonymous-class captures, nested lambdas inside anonymous classes, mutable captures and shadowing.
The [AST inventory](errs.md#ast-changes-for-embedding-and-lsp-ownership-and-placement) records every
affected AST-package class and separates passive node facts, helper ownership and reporting changes.
`AnonymousClassBindings` replaces the existing captured-register map with a result whose fields
are final; it adds no separate nullable helper cache or clone reset logic.

A temporary workload ran 30 successive versions of six small complete modules (180 analyses) in a
warmed test JVM. It weakly tracked each AST, pool and copied semantic snapshot, closed the six
documents, shut down the adapter and requested GC ten times, 100 ms apart. All 180 of each became
collectible. End-to-end `compileAsync(...).join()` latency was median 58.6 ms, p95 78.1 ms, maximum
95.5 ms; this includes snapshot copying and queue wait. The measurement helper was removed from
the ordinary test suite: GC timing and machine speed are observations, not stable CI assertions.
This does not measure large-module cancellation, multiple clients or hours of editing.

The real VS Code extension-host harness ran against the compiler package and passed seven checks,
including file association, activation, packaged JARs and hover in `hello.x`. Its server log confirms
the compiler backend, zero diagnostics, 2.1 ms queue wait and 360.5 ms compilation for that fixture.
The extension host and test editor exited successfully. No visual inspection is claimed: UI
automation was unavailable. The packaged stdio suite separately tests edits, correction,
close/reopen and semantic queries; the editor smoke test is narrower.

Sixth-pass verification used a forced run of `:javatools:test`, `:lang:lsp-server:test` and
`:lang:lsp-server:compilerStdioTest` with `--rerun-tasks --no-build-cache`. JUnit XML reported
462 compiler cases (0 failures/errors, 40 existing skips), 508 LSP cases (0 failures/errors,
3 existing skips) and 5 packaged-server cases (0 failures/errors/skips). The required subset is
93 in-process cases plus those 5 process cases, all with zero skips. Backend selection is included
in the workflow's required class list and XML gate. The final launcher default-selection cleanup
was checked again with its unit tests and the packaged suite (8 cases, zero failures/skips).
The final `:xdk:installDist`, `spotlessCheck` and `git diff --check` also passed. Documentation
checks found no missing local targets across 57 links; the AST inventory covers all 50 changed
AST-package files, including the new helpers. No remote CI results were consulted.

### Seventh pass: API feasibility probes, 2026-09-22

`CompilerProjectTest` exercises a real module root and member file. `compileModule(ModuleInfo, ...)`
reuses CLI source-tree assembly, while `ModuleInfo.readSource(File)` lets a host supply unsaved text
for discovered files. The Source entry point and tree entry point share the compilation pipeline;
`compile(File, ...)` delegates to it for both a module file and a directory. A fresh ModuleInfo is
required per attempt. Canonicalizing overlay keys matters: the first probe exposed the macOS
`/var` versus `/private/var` alias, which otherwise caused the disk text to win silently.

The Kotlin extractor now builds per-source semantic views together, sharing an immutable symbol/type
table. Declaration locations include source names; occurrences use source-plus-range keys. Tests
cover cross-file definitions/references, overload selection, same-named parameters, equal offsets
in different files, and snapshots surviving the next compilation. The single-source convenience
delegates to the same extractor and rejects multi-source input instead of assigning it one URI.

The compiler APIs also expose a generic superclass with its actual arguments, enough to derive a
subtype relation in the fixture. A public-access TypeInfo lists inherited members with substituted
parameter/return types and excludes a private method. Resolved invocation identities and expression
result types are available. These are API probes, not implementations of advertised hierarchy or
completion features. The semantic extractor itself still does not build TypeInfo.

Remaining boundaries are explicit:

- The test overlay provider rejects files absent from disk with `UnsupportedOperationException`.
  New/deleted unsaved-file discovery and a stable editor-URI mapping need a project input model.
- The CLI loader returns no linked AST when a member fails parsing. The probe checks the member
  diagnostic and absence of a stale successful tree. Error-tolerant project analysis remains open.
- IDs are shared within one compilation, not stable across edits. Project-wide publication,
  invalidation, reverse indexes, library source navigation and rename are not implemented.
- `InvocationExpression` exposes the selected method and invoked expression, but no public
  source-argument-to-parameter mapping. Named/default arguments, method type substitutions,
  partial application and the active argument still need a focused call-site contract.
- The installed XdkAdapter remains single-document and the capability matrix remains unchanged.
  No new AST hooks were required for these probes.

The [embedding API summary](errs.md#embedding-api-changes-for-editor-hosts) collects the changes
made throughout the branch. The compiler-consumer workflow includes the project probes and requires
nonzero test execution with zero skips.

Verification forced `:javatools:test`, `:lang:lsp-server:test` and
`:lang:lsp-server:compilerStdioTest` with `--rerun-tasks --no-build-cache`: 462 compiler cases
(40 existing skips), 519 LSP cases (3 existing skips) and 5 packaged-server cases (zero skips),
all with zero failures/errors. The required subset ran 104 in-process and 5 process cases without
skips. A subsequent file-entry cancellation fix permits an aborted result without asserting that
an error exists; its final focused run passed all 12 project probes and 4 embedding diagnostic
cases without skips. The installed XDK build, `spotlessCheck` and `git diff --check` passed.
Local documentation checks also resolved 41 file links and heading anchors.

### Eighth pass: module sessions and hierarchy, 2026-09-22

The three follow-ups are implemented together on this branch:

1. `TypeInfoFinalCompositionTest` reloads compiled modules through test-only Gradle module variants.
   It checks the seven Array translator compositions, the XML cursor and seven XODB compositions,
   with substituted member types and inherited chains. A fresh anonymous-property case and an
   invalid-override control verify that silence does not hide a real source error. No additional
   TypeInfo reporting sweep was needed; the `@Parsed` constructor family remains a separate audit.
2. XdkAdapter owns one compilation session per module. A fresh immutable source snapshot combines
   disk membership with open overlays; new unsaved members and implicit packages need no temporary
   files. `ModuleInfo.sourceEntries` exposes source membership alongside `readSource`. Member parsing
   observes the host's cancellation request, and Lexer polls abort at token boundaries even when
   the input is valid. An edit replaces the entire module analysis; the server retains each open
   document's version, publishes per source, refreshes watched files and clears removed publications.
3. Per-source semantic snapshots now drive module-wide definition/reference queries. Structural
   views stay source-local. Copied direct extends/implements edges and a reverse index implement
   prepare/supertypes/subtypes; generic parent arguments are retained. Hierarchy items carry a
   compilation token, and obsolete items return no result. This is the ninth advertised compiler
   capability; Tree-sitter remains the default and does not advertise this feature.

The sibling-edit test found a further listener defect: `Launcher.log(ErrorInfo)` called console
reporting before its host delegate. Console reporting throws for FATAL, so an unresolved superclass
in a member became EMB-5 on the root and its original COMPILER-30 never reached the host. Forwarding
now precedes that abort. `LauncherErrorHandlingTest` and the real module server test pin the fix.

Tests cover member URI aliases, overlays in new packages, sibling invalidation/cancellation, parser
failure recovery, source-specific outlines, cross-file navigation, versioned publication, file
creation/removal, close/reopen behavior, and hierarchy tokens through protocol conversion. The
packaged stdio suite also exercises module diagnostics, cross-file definition and hierarchy.

Remaining boundaries: conventional module root discovery; non-file inputs remain single-source;
no cross-module dependency build, persistent workspace index or library-source navigation; no
method implementation lookup or conditional-mixin hierarchy; incomplete parsing still removes the
linked module AST. Source/resource paths remain rooted in the original filesystem; the overlay
contract covers source text and membership, not unsaved resource assets. Compilation remains
serialized. No new semantic AST fields were needed for this pass.

Verification forced `:javatools:test`, `:lang:lsp-server:test` and
`:lang:lsp-server:compilerStdioTest` with `--rerun-tasks --no-build-cache`. JUnit XML reports
463 compiler cases (40 existing skips), 533 LSP cases (3 existing skips) and 6 packaged-server cases
(zero skips), all with zero failures/errors: 959 executed cases passed. The new TypeInfo, module,
cancellation and fatal-forwarding regressions executed without skips. The compiler-consumer
workflow now requires the new suites through its existing nonzero/zero-skip XML gate.

A final source-snapshot parity fix preserves resource-only/empty directories as implicit packages.
Its focused rerun passed all 8 module-session and 3 module-server tests, with zero skips, and
`:xdk:installDist` and `spotlessCheck` passed in the same run. Real tasks stored and reused the
configuration cache while developing the new test-only module configuration. The changes remain
local; remote CI was not inspected. No new output-equivalence or long-running editor measurement
is claimed; these remain independent extraction/operational gates.
Final documentation checks resolved all 61 local links and heading anchors across the seven
updated Markdown files; `git diff --check` passed.

### Ninth pass: Java parser recovery, 2026-09-22

Compiler mode stays Java-only, as requested. This pass uses the compiler parser's existing
statement-boundary recovery helpers instead of introducing a Tree-sitter fallback.

Declaration and statement loops retain completed syntax around malformed input. Recovery rewinds
the failed statement before scanning balanced delimiters, always consumes input, and stops at a
statement/block boundary. Missing closing braces report an error while preserving completed module
and method headers. Speculation still abandons its branch immediately; cancellation and error
budgets stop recovery. Malformed expressions are omitted rather than replaced by invented values.

`Compilation.sourceTrees()` exposes the available per-file syntax, including recovered trees.
`parsed()` remains the assembled tree and is absent after source-loading/parsing errors. No file
structure, pool or semantic compilation is created from such a result. ModuleInfo's passive
`getParsedSources()` accessor reads what its loading attempt retained without parsing or linking
again. The adapter uses these trees for current outlines, folding and selection; semantic snapshots
still require the assembled tree. A broken member therefore need not erase its siblings' outlines.
These changes add no AST fields or Kotlin dependency to javatools.

The original three-argument Compilation constructor is retained. Adding the `sourceTrees` record
component changes the record shape: consumers using three-component record patterns must migrate.
This compatibility detail must accompany the extracted API change.

The single-source embedding regression also exposed a listener-policy mismatch. Feeding the
launcher's abort state directly into the parser stopped at its first ordinary error, before it
could return recovered syntax. A stage-local stateful collector forwards diagnostics to the
launcher and observes the host's cancellation/budget. The launcher still rejects the next stage.

This uncovered an unterminated-string loop already present at base `4a1eae6f7`: the lexer repeatedly
reported the same missing-terminator error at EOF. Deduplication could prevent the error budget
from ever advancing. The lexer now reports once and exits that scan. The regression uses an
unlimited listener and an observer that fails on repetition. Structural traversal also carries
the source root explicitly: recovered syntax has not acquired compilation parent pointers yet.

This is bounded syntax recovery, not completion or semantic analysis of invalid source. A malformed
declaration header may be omitted; unmatched nested delimiters and lexer failures can still limit
what is retained. The invalid statement has no placeholder expression or inferred type. Richer
recovery nodes and call-site facts remain separate work.

The complete local run forced all three test tasks with task-specific `--rerun` and
`--no-build-cache`, reusing unchanged build dependencies. JUnit XML reports 471 compiler cases
(40 existing skips), 537 LSP cases (3 existing skips) and 7 packaged-server cases (zero skips), all
with zero failures/errors: 972 executed cases passed. All 8 parser/lexer recovery cases, 3 new
embedding/adapter recovery cases and the packaged recovery case executed without skips. The
workflow requires `XdkRecoveryTest` in its compiler-consumer lane. The final XDK distribution build
and `spotlessCheck` passed. This local verification followed the preceding module work pushed as
`c33b013eb`. No remote CI was inspected.

### Tenth pass: bounded incomplete analysis, 2026-09-22

The approved first step is a narrow Java-only proof of incomplete-expression analysis. It adds
`analyzeIncomplete(Source, input, errs)`, returning a distinct `PartialAnalysis` with per-source
syntax, incomplete sites and an optional owning pool. It has no module/file output or successful
compilation claim; the existing compilation API still rejects parse errors.

The parser's explicit partial mode retains a standalone trailing `receiver.` or unfinished call
at EOF. The opening token, intact callee/receiver, complete arguments, top-level commas and actual
source end are retained without rewriting the source. The ordinary parser keeps its existing
argument-list path; single-argument parsing is shared. Speculation cannot keep partial sites.
Returns, assignments and incomplete nested arguments are not reinterpreted as standalone calls.
Other serious syntax/lexer errors prevent semantic analysis, and complete input has no probe site.

Method-body validation actually runs inside the compiler's code-generation phase. The new
`IncompleteStatement` therefore validates its intact children in the real method context and then
fails validation at the retained EOF boundary. The damaged method is not emitted. Earlier valid
methods may have been processed, but the partial result exposes no compiled artifact. The incomplete
operation has no value or type; overload selection, expected parameter types, active-parameter
mapping and member enumeration are not attempted. Lambda/unbound arguments remain syntax-only.

This syntax node belongs in the AST because the real lexical/flow context exists at statement
validation. Its mutable fields are normal child references that validation may replace. Existing
AST adoption/cloning handles them, with a clone-isolation regression; no Context, callback, lookup
map or separate semantic cache is retained. See the [AST placement inventory](errs.md#incomplete-statements-for-explicit-partial-analysis).

Parser diagnostics reach the host immediately. Only the recognized EOF boundary is allowed through
to the isolated compiler stage state. Validation replays EOF internally to stop progress, while a
per-attempt UID set prevents duplicate host delivery even to a bare callback. EmbeddingCompiler
checks host cancellation between phases as well as inside compiler work. Unexpected repository
failures still report EMB-5 after the original parse diagnostic.

The consumer tests exercise injected Console, parameter identity, flow-narrowed receiver types,
ordinary/named arguments, nested-call commas, UTF-16/CRLF positions, empty/unfinished argument lists,
unknown receivers, unsupported syntax, fresh attempts, cancellation, budgets and exactly-once
diagnostic delivery. The damaged method has no binary AST. These tests invoke the embedding API
from the existing Kotlin LSP test module using its normal compiler dependencies.

XdkAdapter does not yet call this probe and advertises no additional capabilities. Integrating
copied partial facts, widening beyond a single-source EOF statement, and implementing completion
or signature help remain separate work. No Tree-sitter fallback or Kotlin dependency in javatools
was added. This pass does not extend the TypeInfo audit or start cross-module indexing.

Verification forced all three test tasks with task-specific `--rerun` and `--no-build-cache`.
JUnit XML reports 474 compiler cases (40 existing skips), 547 LSP cases (3 existing skips) and
7 packaged-server cases (zero skips), with zero failures/errors: 985 executed cases passed.
All 10 partial-analysis consumer tests, 10 parser-recovery tests and 2 embedding repository-failure
tests executed without skips. The same successful run built the XDK distribution and passed
`spotlessCheck`; Gradle stored its configuration cache. The workflow's compiler-consumer lane
requires the new suite with nonzero execution and zero skips. No remote CI or output-equivalence
comparison was run. Changes were verified locally after the structural recovery commit `60054eb4c`.

### Eleventh pass: copied call and member facts, 2026-09-23

The preceding incomplete-analysis pass was committed and pushed as `d24f1be1f`. This pass supplies
consumer models inside `lang/lsp-server`; it does not enable completion or signature help yet.

Completed method calls copy the compiler-selected declaration, instantiated signature and each
written argument's visible parameter index. Capture happens after generic inference, using the
signature the compiler just resolved. Named argument order and omitted defaults are preserved
without inventing source spans for compiler-generated arguments. Function-valued calls, partial
applications and receiver-to-argument rewrites deliberately have no selected-call record yet.

These facts belong to the compilation attempt. `InvocationExpression` adds no semantic field,
nullable cache, lazy holder or clone override. An explicit collector travels from Compiler through
StageMgr and method validation Contexts. Successful validation supplies immutable records;
revalidation invalidates an earlier entry. Publication filters by surviving node identity and
successful validation, then clears all scratch entries. Speculative clones cannot inherit another
node's binding or remain retained through this collector. Ordinary compiler constructors select a
no-op collector; embedding attempts collect facts and return immutable maps. Existing result
constructors remain, but the additional record components change record-pattern arity; extracted
API changes must document that compatibility limit.

The explicit partial-analysis copier may inspect receiver TypeInfo on the compiler worker with its
own reporting listener. It copies accessible instance methods/properties, receiver substitutions,
lexical method identity, argument spans/labels/types and top-level comma positions. For unfinished
calls, same-named accessible members are candidates, never a selected overload or an inferred
argument-to-parameter assignment. Argument index means the source argument slot, not a parameter
index. Failed or canceled inspection cannot publish fabricated candidates. The ordinary semantic
snapshot remains passive, and both copied models contain no compiler objects.

Remaining scope: integrate partial results into the adapter's versioned module lifecycle; widen
beyond a single standalone EOF statement; model implicit receivers, static/type-literal lookup,
function values and partial application; select applicable incomplete-call overloads and expected
parameter types. No new LSP capability is advertised. The next agreed investigations remain the
bounded `@Parsed` TypeInfo audit and sustained editing/retention checks.

Verification: forced compiler and LSP tests report 474 and 556 cases respectively, with 40 and 3
existing skips and zero failures/errors. All 9 new call/member cases, all 10 partial-analysis cases
and all 13 semantic-model cases executed without skips. Packaged-server tests initially rejected
the test command's missing compiler-backend flag; rerunning with `-Plsp.adapter=compiler` passed all
7 cases with no skips. Together, 994 executed cases passed. The XDK distribution build,
`spotlessCheck` and `git diff --check` passed. The workflow requires the new consumer suite to run
with zero skips. No remote CI was inspected.

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

Commands for the hardening pass (Gradle supplies the compiler's bundled module dependencies):

```bash
./gradlew :lang:lsp-server:test :javatools:test :lang:lsp-server:fatJar \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler \
    --rerun-tasks --no-build-cache

./gradlew spotlessCheck
git status --short
```

If a clean verification is necessary, run `./gradlew clean` alone and wait before those commands.
Do not combine `clean` with another task.

The fresh pre-change baseline ran 26 compiler-consumer tests with zero failures/errors/skips.
The first pass reported 458 compiler tests, 457 LSP tests and a required compiler-consumer subset
of 42 tests. The second pass reported 458 compiler tests, 467 LSP tests and 52 required consumer
tests. Fresh third-pass results on 2026-09-22, read from JUnit XML:

| Suite | Tests reported | Failures/errors | Skipped |
|---|---:|---:|---:|
| `javatools` | 462 | 0 | 40 |
| `lang:lsp-server` | 479 | 0 | 3 |
| Required compiler-consumer subset of the LSP suite | 64 | 0 | 0 |

The javatools skips are 36 existing disabled tests and four opt-in project-generator integration
tests. The LSP skips are three existing disabled navigation/inlay-hint tests. They do not include
the required compiler-consumer subset. The third pass's final run used
`--rerun-tasks --no-build-cache`, executed all 114 tasks and stored the configuration cache. A
subsequent `fatJar spotlessCheck` run reused it. `spotlessCheck` and `git diff --check` passed. The workflow's XML
gate passed locally; `actionlint` reported the same 89 existing shellcheck findings on HEAD and
the modified workflow, with no new findings. Remote CI for these changes and an interactive
editor session have not been run. No grade-based quality claims are made.

## Completion criteria

The diagnostics milestone is complete when the required CI suite runs from clean inputs, an
embedding consumer receives attributable compiler diagnostics without initializing a runtime for
logging, and the real server correctly handles open, edit, correction, supersession and close.
Known single-module and source-location limits must be visible in the usage documentation.

Full workspace/editor support is a later milestone: partial syntax, cross-module ownership and
indexing, safe rename and integrated completion/signature help. Module overlays, cross-file source
mapping and direct source type hierarchy are implemented in the eighth pass; bounded call/member
facts are supplied in the eleventh pass. The suppressed-diagnostic and failure
audits remain explicit backlogs until their cases are classified; neither “seven phases complete”
nor a green adapter test run closes those investigations.
