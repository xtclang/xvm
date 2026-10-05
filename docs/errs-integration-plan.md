# Integrating the embedding diagnostics work

Latest feature checkpoint: [L74 artifact identities](#l74-artifact-identities-2026-10-05) passes
backend, packaged transport and selected editor acceptance. L75 document content/refresh is next.

Upstream defects and compatibility bridges are tracked in [errs-upstream-issues.md](errs-upstream-issues.md).

Plan prepared on 2026-09-22 from `lagergren/errs` at `a8213cf04`, against the local
`origin/master` reference at `4a1eae6f7`, which is also the merge base. This comparison contains
72 commits and changes 139 files: 6,632 insertions and 984 deletions. The remote was not refreshed
for this assessment. These numbers describe the source branch, not the proposed PR sizes.

Sources: [the design and investigation](errs.md), [the failure audit](errs-audit.md), the commit
bodies, and the current compiler, embedding API, adapter, server and build configuration.
There are no `errs.log` or `errs-audit.log` files in this checkout; the corresponding records are
the two Markdown files above.

## Full compiler LSP completion checklist

**Process-lifecycle fix (2026-09-27):** `8e976f868` guards IntelliJ startup/cancellation;
`eefc1b8e6` closes server resources and exits on transport loss. The independent master
extraction boundary, upstream reproduction and 15/15 child-process regressions are recorded in
[the lifecycle diagnosis](errs-lsp-process-lifecycle.md). Installed IDE restart/project-close
acceptance remains to be recorded. This fix adds no compiler or AST requirement.
The isolated master branch is published as `lagergren/fix-lsp-process-lifecycle`,
commit `cf54e2a19` on `ce3ab1d81`, in `build/lsp-process-lifecycle`. Its nine-file diff keeps
master's dependencies and passes 455 tests (three existing skips), including all 20 new lifecycle
regressions. [PR #653](https://github.com/xtclang/xvm/pull/653) targets `master`, with review
requested from `ggleyzer`; see the diagnosis for the exact size and checks.

Current inventory: updated 2026-10-02, extending the native/tracing checkpoint `86fc15348` and the
L55/L61/L62 implementation and validation batches recorded below. This is the active task list;
dated records retain their historical scope and results. Checkboxes distinguish completed acceptance from
implemented-but-unverified work and planned features. Compiler API changes get separate C-series
extraction boundaries when their implementations establish what is required.

**We have broad editor support, not full LSP coverage.** XdkAdapter implements all 25 entries
in our `AdapterCapability` enum, plus compiler diagnostics and document/workspace synchronization.
That enum is a project abstraction, not a list of every LSP feature. Completion, signatures,
rename, actions, formatting, semantic tokens, hints and hierarchies still have explicit limits.
Separate go-to-declaration now passes backend, protocol and selected editor validation. Several
other protocol features still have no handler.
The [adapter matrix and absent-feature inventory](../lang/doc/plans/plan-ide-integration.md#compiler-completeness-snapshot)
separate those states. Tree-sitter remains the shipped default; compiler mode remains opt-in.

The accepted [embedded execution and debugging plan](../lang/doc/plans/plan-embedded-execution.md)
extends the overall XTC tooling goal with R1–R8. Both IDEs should use one compile/build contract
and a supervised persistent execution worker, with a fresh application container per run.
The current branch can remain compiler-focused; runtime delivery is a separate extraction track.
Compiler source/artifact revisions, cancellation and ownership must support that track now.

The protocol inventory is checked against Microsoft's
[LSP 3.18 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/),
[3.18 method model](https://raw.githubusercontent.com/microsoft/language-server-protocol/gh-pages/_specifications/lsp/3.18/metaModel/metaModel.json)
and the installed LSP4J 1.0.0 service interfaces. LSP capabilities are optional: an unadvertised
optional feature is not by itself a protocol violation. Here, **full implementation** means
finishing the applicable editor features and validating the protocol contract. Any deliberately
excluded feature must remain explicitly marked out of scope, never silently counted as done.
Debugging execution/stepping belongs to DAP; LSP inline values are listed separately below.

### Immediate hardening order

Complete these changes in sequence, keep useful local commit boundaries, then run combined
backend/protocol validation and one announced, selected native checkpoint. Focused reproductions
needed to identify a failure can run earlier; a complete playbook after every edit is unnecessary.

- [x] **L55 — Large-workspace proof memory and rename refusal.** Reproduce the 24-module
  teaching workspace with only the target open and with multiple buffers open. Identify why
  the native property rename returned no edit and what retains compiler pools/facts across
  before/after proof. Bound peak memory without weakening binding/dispatch checks. Acceptance:
  successful and rejected renames under a declared heap budget, unchanged diagnostics, and
  release of all query-owned compiler objects after success, cancellation and failure.
- [x] **L56 — Editing during IntelliJ startup.** Reproduce ordinary edits while initial
  `didOpen` is pending; trace document text/version ordering through LSP4IJ and the server.
  Fix confirmed synchronization or stale-result delivery, including shortened-document folds.
  Acceptance: typing, bulk replacement and close/reopen during startup converge to current
  diagnostics/folds without a test-only readiness wait or IDE exceptions.
- [x] **L57 — Semantic formals in unfinished headers.** Resolve written but unregistered class
  and method formals through real compiler facts, with constraints, shadowing and qualifier
  substitution. Bound-labelled completion/hover and virtual-child lookup are implemented. Acceptance:
  positive completion/hover/type facts where justified, negative unresolved constraints, and
  no fabricated components, mutable AST cache or retained compiler context.
- [x] **L58 — Missing value operands and remaining callable recovery.** Extend recovery and
  compiler fitting beyond the proven qualified/grouped argument cases: missing/compound value
  operands, enclosing-instance members, type-valued receiver fallback and receiver-rewritten
  calls. Cover each syntax form with positive, rejection and repair cases; preserve ordinary
  compiler diagnostics and exact edits. Lexical enclosing/imported properties are covered;
  literal synthesis and arbitrary enclosing-instance enumeration remain in L64.
- [x] **L59 — Inferred-type presentation.** Broaden hover/inlay displays beyond successful
  inferred locals and selected positional parameter names: destructuring and lambda
  parameters/returns now use actual compiler types. Shared scenarios and current-version
  invalidation checks pass; partial source never invents an inferred type.
- [x] **L60 — Native parity checkpoint.** The complete 113-case suite passes together in
  `run-6034631232732848040`, with zero IDE errors. All 50 newly added cases, X20/X81/X82 and
  X93–X98 are included.
  Every missing IntelliJ case already has a VS Code implementation. Track each case in
  `scenarios.json`; X20's signature display discrepancy
  and X81/X82's Property-kind assertions are resolved. Use native editor actions for user-visible
  behavior and the installed client transport for protocol-only assertions such as stale handles,
  cancellation and edit versions. Label those layers explicitly. Record assertions implemented,
  cases selected and cases passed separately. Full parity is required work; a selected passing
  subset does not close L60; the complete run above does. Run the broader checkpoint occasionally,
  not after each change.

### L56 startup synchronization implementation (2026-09-28)

LSP4IJ 0.21 sends `didOpen` on the common executor, while change/close notifications use its
ordered dispatcher. Its initial text can also predate listener attachment. The IntelliJ
transport now holds early full-text changes behind opening, uses the latest queued client
version, and refreshes an otherwise stale opening snapshot from the actual buffer. Existing
opened-document identities distinguish reopen from a delayed close. Folding replies carry a
request-time editor identity/stamp check before reaching LSP4IJ's line-to-offset conversion.
No second document version counter, compiler context or AST state is introduced.

The server also waits for `initialized` before requesting dynamic file watchers, honors the
client's registration capability and logs rejected registration futures. This closes that
bounded initialization ordering item from L80, not the whole capability audit.

Deterministic regressions cover typing/replacement before open, pre-listener edits, close/reopen,
stale shortened-document folds, delayed closes while a reopened buffer is still pending, and watcher
negotiation. All eight client middleware tests and the capability-registration test pass. Native
startup acceptance is now recorded below; the ordinary feature readiness wait is not that proof.

### Native startup, rename deadlock and execution tracing (2026-09-28)

The separate `CompilerPlaybookTest.startupEditing` check makes real, unsaved editor transactions
before initial startup or restarted-server initialization completes. Five phases cover cold open,
unknown-name insertion, immediate repair, long-to-short replacement and close/reopen plus editing.
It checks current client version/text, published and installed editor diagnostics, exact shortened
folds, unchanged disk text, replacement PID and exit of the previous process. These are editor
transactions, not a claim to simulate physical typing. The probe is test-only; the shipping plugin
has no readiness barrier. `run-1799467324333192176` passes START/STARTUP with zero IDE failures.

A real X103 freeze was reproduced in `run-18230526726141991629`, after seven feature passes.
The EDT held the VFS rename write lock while LSP4IJ's `onFileRenameAfter` waited for didOpen.
Our startup-message snapshot callback requested a blocking IDE read action on that notification
thread. This was a production plugin lock cycle, unrelated to desktop focus or compiler speed.
The hook now reads the existing opened-document identity and the Document API's immutable text
without a read action; it adds no mutable document cache. X103's forward and reverse resource
rename both pass in `run-8812860837009261601`, with no IDE failures. The five-phase startup check
also passes after this fix. The later full native checkpoint is recorded below under L60.

The next full run, `run-5653317395986057876`, passed 61 scenarios plus START, including X103,
before Starter's default ten-minute whole-IDE timeout terminated it during X86. The resulting
JMX connection refusal was a consequence of that shutdown, not an X86 assertion failure or a
repeated rename deadlock. There were no reported IDE errors; 51 scenarios were not reached.
The driver now allows 30 minutes for the whole session and retains its individual bounded waits.
The corrected runner compiled and reused the configuration cache. Its first rerun,
`run-13531221189698116807`, passed 17 scenarios plus START before X17 found both the active and
focused IDE windows null. No IDE errors were reported; 95 scenarios were not reached. This is
a separate desktop-focus interruption; it did not close L60.

The harness now restores interrupted popup focus with `AppIcon` window/application activation
and component focus without moving the pointer. On macOS the window overload calls
`Desktop.requestForeground(false)`; the no-argument application overload adds the required
`requestForeground(true)`. The window-only version timed out at X94 in
`run-8104497655710049205` after 30 passing scenarios (zero IDE errors), so it did not close L60.
With explicit application activation, X94 passes in `run-11175036742022156624` and the dedicated
focus regression passes in `run-4951684034310159112`, both without IDE errors.
`PopupInspection` captures a document modification
stamp and refuses to reopen after any edit; completion acceptance, quick fixes, rename application
and file moves remain outside the replay path. A dedicated native regression deliberately
interrupts completion/Parameter Info and rejects recovery after accepted completion/rename edits.
Restoration and reopening are logged separately. The test IDE's title and status bar show the
selected total, completed/remaining counts, current case and focus-restoration state. VS Code's
playbook has the corresponding status-bar progress display. These are test-only helpers.

Harness waits now poll every 100 ms instead of the Driver default of one second, retaining each
failure deadline. A single IDE-side call checks focus ownership, and synchronous test scrolling
avoids the Driver helper's fixed 200 ms pause. Assertions are preserved. X16/X17/X20/X34/X97/X103/X105
pass in `run-793185410831092997`, with zero IDE failures. Their combined scenario time fell from
93.846 seconds in the earlier full run to 32.630 seconds in this selection (about 2.9 times faster).
This is an observed comparison with different session histories, not a controlled benchmark.
The dedicated focus regression passes in `run-9052387951904069468` with zero IDE failures. Its
earlier rename failure was a fixture mistake: a public method parameter is intentionally ineligible
for rename. The corrected private-method fixture exercises a real rename and verifies that its
completed edit is never replayed. An interrupted full run, `run-9402596203997157027`, passed eleven
scenarios before the modification-stamp guard stopped X12 after an external edit. It is not a
passing checkpoint. Automatic activation can still redirect user typing into the test IDE; an
isolated desktop avoids that interference.
The same seven VS Code cases pass with the status-bar display enabled in `run-blew7d`
(seven passed, zero skipped, 16 seconds of scenario execution).

Queue tracing now records both counts and ordered human-readable jobs, keeping debounced, queued
and running work distinct. Nested javatools spans, process/thread IDs, compilation times and LSP
receive-to-reply durations correlate the work without source text. See the
[trace fields and controls](../lang/lsp-server/README.md#compiler-queue-and-api-timing).
The clean startup run produced five per-process trace files, 491 API spans and a maximum observed
API concurrency of one; this is evidence for that run, not a global concurrency guarantee.
IntelliJ's service-started balloon uses the platform smart fadeout timer (eight seconds, paused
while interacting); notification history remains available. The initial smart-only call waited
for an input event to start its clock, leaving the balloon visible during pointer-free test runs.
It now also schedules the timer immediately, and the native startup check verifies disappearance
without mouse or keyboard input.

The first full run with explicit application activation, `run-17709873479397394537`, completed
all 113 scenarios: 112 passed and X30 failed, with zero IDE errors. X30 assumed that a replacement
server PID implied that LSP4IJ had finished reopening its documents. Faster polling exposed the
missing readiness check; the scenario now waits for didOpen through the ordinary open helper
before accessing the new synchronizer. This run took 6 minutes 42 seconds including Gradle and
startup, but its failed X30 means it is not a clean checkpoint.
The following run, `run-9607396768138373685`, passed 22 scenarios before timing out on X83's
editor-focus step after window activation; it reported no IDE errors. Component focus now goes
through `IdeFocusManager.requestFocus` on the next Swing event, and timeout messages include
window/owner state. This follows the platform's
[replacement for its deprecated focus-settling helper](https://github.com/JetBrains/intellij-community/blob/f7eb985738a2329c5c9baabc9d1d5067e70acc45/platform/ide-core/src/com/intellij/openapi/wm/IdeFocusManager.java).
The separate startup run `run-12431789842449790480` passes all five phases and confirms that the
untouched startup balloon disappears, with zero IDE errors.

**Final checkpoint: `run-6034631232732848040` passes all 113 shared scenarios plus START in one
session, with zero IDE errors and zero JUnit failures/errors/skips.** Gradle succeeds in
6 minutes 40 seconds and reuses the configuration cache; scenario execution totals 367.160 seconds.
X30 passes with document readiness after restart. This closes L60. The separate startup/balloon
and deliberate focus-recovery receipts remain additional checks, not part of the 113-case total.

Commit boundaries for this checkpoint:

| Commit | Future PR scope |
| --- | --- |
| `a75e2f5a2` | L56 transport read-lock fix for the IntelliJ VFS rename deadlock. |
| `24b6b03ff` | Queue/API/protocol tracing and JVM/stdio regressions, including normal Gradle test logging and IntelliJ trace configuration. |
| `51579aa59` | Startup balloon timeout, preserving notification history. |
| `7aa642b79` | Test-fixture operator spacing and matching anchors. |
| `4d7f0f22b` | Pointer-free popup recovery, faster native waits, restart readiness, progress in both editor harnesses, and native startup/focus acceptance tests. |

The native startup and balloon acceptance tests share harness support in `4d7f0f22b`; carry those
tests with the corresponding fixes when extracting PRs, or land the harness support first.
The spacing cleanup changes shared data and backend fixture strings together; X16's argument
position and the stdio signature-help offset move with their anchors. Deliberately unformatted
formatter inputs and caret markers remain intact. Documentation and matching manual examples
follow in the documentation checkpoint. Validation below covers the integrated tree; each
extracted PR still needs independent checks. No compiler/AST API is added by this batch.

Validation after fixture spacing: the complete LSP JVM run executed 1,255 tests with three
existing disabled tests. Three old-spacing assertions failed; their corrections passed a
47-test rerun of XdkArrayDimensionTest, XdkRenameTest, XdkWorkspaceRefactoringTest and
CompilerBoundaryRequirementsTest. Initial XML is retained in
`lang/lsp-server/build/reports/fixture-spacing-initial-jvm`; this is combined evidence, not a
claim that the initial run was green. All 67 packaged stdio cases and 40 IntelliJ unit tests pass.
VS Code passes all 113 scenarios in `run-aOarm7`; no cases are skipped or unselected. Kotlin lint,
Spotless and integration-driver compilation pass. No Java compiler source changed in this batch.
The ordinary VS Code extension suite also passes all eight tests, including the changed project
fixtures and 40 error/repair cycles (observed p50 511 ms, p95 825 ms). Its Gradle invocation took
34 seconds and stored the configuration cache.

### L57 written formal bounds implementation (2026-09-28)

Recovered method/class headers now retain their real `Parameter` syntax, including constraints.
`CursorScope` resolves disposable constraint copies through the compiler, follows sibling bounds,
rejects cycles/unresolved bounds and keeps written names shadowing enclosing names. Qualified
lookup uses a resolved bound and ordinary parameterized child resolution. `CursorBinding.Formal`
exposes a source name and upper bound without inventing a method/class or formal identity.
Original `CursorBinding` constructors remain available; its record component list gains formals.

The Kotlin snapshot copies only name, bound type and source range. Completion explicitly labels
these as type parameters with bounds; partial hover uses the same facts. Query results do not
replace normal diagnostics. Recovery nodes use final child lists and independently clone their
written parameters, following the existing syntax-only node ownership pattern.

All eleven written-formal regressions pass: exact edits/repair, shadowing, sibling bounds,
unresolved/cyclic constraints, hover in a retained header, virtual-child substitution and negative
typedef/static-child controls. The snapshot copier also rejects direct constraint cycles without
rejecting legal recursion through concrete generic arguments. These bounds are not proof that an incomplete declaration's body or
inheritance is valid, nor an exact concrete type for a formal. Bound resolution of unfinished
self-referential constraints (for example `T extends Chain<T>`) remains conservative: no fabricated
formal identity is created. Unrelated parse errors, including an unclosed outer body away from
the selected cursor, can still suppress the partial query; L64 retains that recovery work.

### L58 compound operands and callable fallbacks implementation (2026-09-28)

The explicit cursor parser retains an absent primary operand without consuming its delimiter.
`PartialArgument` locates one cursor within an operator expression and replaces only that hole
on a disposable clone. Candidate enumeration may leave the whole argument unbound, but every
suggestion must fit the complete operator expression and all other written arguments. Lambda
and nested-call boundaries keep their own contexts. Ordinary parsing/emission is unchanged.

Call inspection follows the compiler's fallback from an instance method to functions on a
`Type<T>` bound or receiver-rewritten functions. Candidate facts explicitly identify the injected
receiver; the host hides that parameter and adjusts source argument mappings. Lexical property
names now include enclosing/import scopes, but ordinary read validation and complete argument
fitting remain authoritative for accessibility, shadowing and conversions.

The focused combined recovery suite passes 108 cases with zero skips. It includes whole-operand
validation, named-slot replacement, lexical enclosing/imported properties, callable fallbacks and
repair. Ambiguous grouping that parses as a tuple does not invent a different argument boundary.
Outside a call, a missing operand still receives lexical completion without a fit claim. Literal
synthesis remains separate: no literal candidates or guessed expected types are invented here.
No new mutable AST field is required; parser ownership and copied candidate metadata carry the
additional information. The existing four-argument Candidate constructor remains available.

### L59 inferred presentation implementation (2026-09-28)

The detached semantic model now copies validated lambda function signatures and source arrow
ranges. Inferred lambda parameters are marked from the compiler's existing names-only syntax;
synthetic capture parameters never become source hints. Return hints use the validated function
return tuple, while explicit parameter types remain unannotated. Destructured local declarations
reuse their independently validated registers and inferred type syntax. Failed compilations
suppress inferred hints, and replacement/close discard the previous snapshot.

The sole Java AST addition is `LambdaExpression.getOperator()`, exposing its existing written
arrow token. Resolution, presentation and new state stay in the Kotlin snapshot/builder; there is
no new lambda field, clone-reset rule or retained validation context. Four hint/range/hover and
failure/repair regressions pass. Shared X42 now checks the existing fixture plus a destructured
pair and inferred lambda signature in both editor drivers; the existing fixture now has three
type hints because its lambda return is included.

### L56–L59 commit and extraction map

All development remains on `lagergren/errs`. Keep each implementation with its validation
corrections; the first checkpoint in a pair is not independently validated for extraction.

| Scope | Implementation | Validation correction / shared acceptance |
|---|---|---|
| L56 — startup ordering and stale folds | `5bae37534` | `17fd2dc58`; client unit tests plus server `initialized` negotiation |
| L57 — written formal bounds | `2c790a6d9` | `a39aaa435`; X108 variants in `64658640f` |
| L58 — compound values and callable fallbacks | `4633331cc` | `fef2c090e`, `0a2f86e5f`; X97 variants in `64658640f` |
| L59 — inferred lambda/destructured types | `a51872e03` | X42 data and both drivers in `64658640f`; builder formatting accompanies `a39aaa435` |

`64658640f` is shared test data/driver work, split by X42/X97/X108 when extracting. L56 can form
an IntelliJ/client protocol change independently of compiler recovery. L57 depends on retained
header syntax; L58 depends on cursor argument fitting; L59 depends on the Kotlin semantic snapshot
and existing lambda register provenance. Each extracted PR still needs its own build and tests.
Selected VS Code X42/X97/X108 pass in `run-APcBZB` (three passed, 110 not selected), including
the added formal/operand/hint variants. Both editor drivers compile.
At that checkpoint, the complete native run and live startup editing acceptance remained open.
The dedicated startup run now passes; the newer native/tracing section above records that proof
and the remaining full-run gate. Older receipts do not validate later expanded variants.

### L56–L59 combined validation receipt (2026-09-28)

The final combined invocation passes in **7m22s**, with configuration-cache storage and
`spotlessCheck` passing. JUnit XML records:

| Suite | Executed / passed | Existing disabled tests |
|---|---:|---:|
| Java compiler and embedding API | 105 | 4 |
| Full LSP server unit/adapter suite | 1,252 | 3 |
| Packaged stdio and process lifecycle | 66 | 0 |
| IntelliJ plugin unit suite | 40 | 0 |

The disabled cases are the existing Source/Lexer fixtures and Tree-sitter navigation/inlay
placeholders; no new compiler regression was skipped. IntelliJ unit tests ran earlier in this
batch and remained up to date in the final invocation. Both native-driver Kotlin and VS Code
TypeScript compilation pass. No native IntelliJ UI run is claimed for this batch.

Selected VS Code **X42, X97, X108: 3 passed, 110 not selected**, receipt `run-APcBZB`, shared
catalog SHA-256 `b513d6915ab66b317cfc86fa42783f669826e496ca74cf0060c907ce7cf79eeb`.
The receipt records `64658640f` plus the constructor-order correction subsequently committed as
`0a2f86e5f`. The combined rerun passes that generic-constructor regression and the rest of the suite.

```bash
./gradlew :javatools:test --tests 'org.xvm.compiler.*' --tests 'org.xvm.api.*' \
  :lang:lsp-server:test :lang:lsp-server:compilerStdioTest \
  :lang:intellij-plugin:test :lang:intellij-plugin:compileIntegrationTestKotlin \
  :lang:vscode-extension:testCompilerPlaybook spotlessCheck \
  -PcompilerPlaybookCases=X42,X97,X108 -Plsp.adapter=compiler \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache --continue --console=plain
```

L57–L59's bounded implementation work is complete. L64 retains recursive unfinished bounds,
further recovery/member enumeration and literal synthesis. L56's startup-editing acceptance
subsequently passed in its dedicated native run; L60's complete 113-case native run also passes,
including the newly expanded variants. These are separate from the backend and selected
VS Code receipts above.

### Finish the existing editor features

- [x] **L61 — Go-to-declaration.** Implement `textDocument/declaration`, add an explicit adapter
  capability and advertise it only when implemented. Define declaration versus selected-body
  behavior for interfaces/overrides, aliases, locals and indexed libraries; preserve multiple
  source targets where required. The compiler adapter now has a plural declaration query and
  explicit advertised capability.
  Local and imported aliases keep their own declarations; overriding methods/properties return
  all inherited written contracts copied from TypeInfo. Definition and implementation retain
  their existing behavior. Indexed sources remain read-only. Backend/protocol regressions and
  shared X4 assertions pass in both clients. No AST API changes or retained compiler objects
  are needed: declaration relations live in detached Kotlin facts.
- [x] **L62 — Rename scope and resource edits (compiler scope closed; UP23 host exception).** Extend beyond the current proven source
  types/static members/ordinary method and property families/aliases/locals. Audit constructor
  names, module/package/directory moves, companion directories, annotation/mixin/delegation
  dispatch and public-parameter contracts. Keep binary declarations read-only and reject
  incomplete known graphs. Consumers outside an explicit graph are unknown, not refused. Add collision, changed binding, changed dispatch and undo tests.
  The first checkpoint added declared package/type companion-directory moves and simple
  discovery-owned module renames, with closed import updates and a proposed graph proof.
  Text edits precede minimal file/directory operations; destinations are never overwritten.
  X103 now exercises a nested companion file in both clients and VS Code undo; backend tests
  exercise reverse renames, constructor type uses and resource capability negotiation.
  That initial backend/editor validation passed. The following extension was implemented in three
  separate commits before a combined backend run (76 LSP tests and 10 Java API tests passed, none
  skipped). Shared X109–X118 now have passing selected runs in both editors:
  1. Public parameter slots, override callers and explicit constructor labels: backend validated.
     The subsequent rename batch adds primary-header properties, lambda parameters and escaped
     method values; binary contracts retain conservative refusal. See its separate receipts below.
  2. Supported annotation/mixin/delegation families: backend validated.
     Compiler dispatch provenance follows existing into/capped/delegate metadata, including the
     declared receiver property, without generating forwarding methods. Unknown routes and binary
     contracts still refuse edits. The existing `MethodBody.getIntoMethodInfo()` accessor is now
     public for worker inspection; no AST state or API is added.
  3. Qualified modules and implicit package-directory moves: backend validated.
     Domain suffixes and local import aliases are preserved. Explicit module
     roots have a host API proposal containing edits and the replacement source graph. Neither
     proposal creation nor a rejected standard rename mutates configuration. Both native clients now
     use `xtc/rename` with before/after graph checks. VS Code edits the workspace settings document
     in the WorkspaceEdit (including unsaved configuration and Save All); IntelliJ persists its
     existing LSP4IJ configuration with native Undo/Redo. Standard LSP rename still refuses graph
     replacement for other clients. Shared X118 and settings/protocol regression tests pass.
     VS Code edited-file moves require its default `files.refactoring.autoSave = true`;
     disabling that policy refuses the proposal before changing sources or settings.
  The later closure receipt below defines supported compiler scope and deliberate refusals.
  Unknown external consumers remain outside the graph; editor defects stay in the upstream
  register. The `construct` keyword is never renamed.
  The October 2 continuation inspects validated concrete source types as well as formal declarations
  when collecting method/property families. Conditional adoption therefore participates in rename
  proof; X155 passes source rename and Undo in both editor drivers. The October 2 continuation
  acceptance receipt below records the combined backend and selected editor validation.
  X161–X163 subsequently add bounded cross-package type moves and explicit source/resource graph
  relocation with persisted Undo/Redo; all pass selected acceptance in both editors. The
  [graph-relocation receipt](#l62-explicit-source-graph-relocation-2026-10-02) records the exact
  boundaries and UP19 connection repair. The October 3 continuation adds bounded union alternatives
  and recursive written contracts at callable sites (X164/X165). The next slice adds bounded
  generic/formal/annotated operands and nested union delegation (X166–X168); see its receipt below.
  The combined-relocation continuation below adds simultaneous type/module rename and move plus
  interacting type moves. The October 4 batch adds captured empty package destinations and
  token-preserving commented qualifications (X173–X176, selected acceptance in both editors).
  The ownership closure below adds cross-module moves, overlapping move trees and exact
  annotation/type proof. Uncaptured destinations and runtime/generated routes without written
  contracts remain deliberate refusals. UP23 is an explicit VS Code overlapping-move Undo
  exception, accepted as a separate follow-up before continuing to L63.
- [x] **L63 — Semantic quick fixes and refactorings, bounded closure.** Individual and all-required-member
  implement/override actions are implemented at a class name for inherited source and read-only
  binary/XDK contracts. Compiler-selected signatures include generic/conditional/multiple returns,
  parameterized, relational/nullable/immutable and qualified cross-module types with atomic imports.
  Validated primitive defaults (including folded expressions) and Boolean/Null constants can be
  rendered; fresh declaration repair accepts supported literal tokens and requires a complete
  proposed compilation. Unvalidated computed/named defaults, unsupported constant kinds and
  annotated/otherwise unrenderable types remain refusals. Every edit proves the inserted methods'
  intended call/descendant changes while preserving unrelated bindings. Bodies use `TODO()`.
  Shared X122 now has eleven variants; the latest implementation/validation status is recorded in
  the [library and complete-repair batch](#l63-library-and-complete-repair-batch). Older seven-variant
  receipts do not establish the new coverage. Whole-return-expression extraction and adjacent
  single-use typed returned-local inline preserve the written expected type and relocated bindings;
  X156/X157 pass in both editors. The October 4 batch adds complete explicitly typed local
  initializers, adjacent same-written-type initializer inline and compiler-proven unused constant
  local removal (X177–X180; acceptance below). Complete return/typed-initializer expressions can now
  also move to a private helper in the same type using explicit stable inputs (X181–X184; current
  batch acceptance below). Same-owner missing-method creation is implemented from fresh resolved
  declarations, compiler-established locals/typed initializers, proven same-owner instance receivers
  and named enclosing-class static qualifiers (X185–X200), plus explicit public repairs in another
  writable ordinary class of the same module, including companions (X201–X204; receipts below).
  The eight-area October 4 continuation adds bounded implementations for generic signatures,
  receivers/arguments, missing declarations, wider extraction/inline and private-member safe delete
  (X221–X242). The final combined gate passes 498 tests, and every new case has a passing
  selected-run receipt in both editors. The [closure table](#l63-bounded-closure-and-acceptance-2026-10-04)
  distinguishes supported forms from deliberate refusals. Arbitrary control-flow extraction,
  parameter-substituting inline and externally visible safe deletion are not claimed.
  Doc-comment generation and reference/test lenses remain separate subfeatures. Semantic
  transformations require compiler evidence and versioned multi-file edit validation.
- [x] **L64 — Completion/signature breadth and presentation, bounded closure.** Supported recursive
  bounds, fitted scalar/collection/lambda values, enclosing instances, declaration names,
  contextual templates, imports and callable/signature presentation are implemented and audited.
  The [closure table](#l64-closure-audit-and-acceptance-gate) records supported forms and deliberate
  exclusions; arbitrary value/body synthesis and ambiguous local syntax are not claimed. All
  associated shared cases have passing receipts in both editors. The intermittent native X105
  popup timeout remains an acceptance-stability follow-up below.
- [x] **L65 — Navigation, hierarchy and semantic classification, bounded closure.** Audited
  conditional composition, accessor/method redirects, native/synthetic refusals, static/dynamic
  call boundaries and binary source ambiguity. X153/X154 pass in both editors. Runtime target
  enumeration and inferred conditional type-hierarchy edges remain explicit static-model limits;
  no executable target or source location is invented.
- [x] **L66 — Structural and editing breadth, bounded closure.** Resolved module/type/wildcard
  container links, lexical local/lambda/explicit-alias linked ranges, typedef outlines and strict
  damaged-source selections are implemented. The lexer formatter supports configured indentation,
  operator continuations, safe expression/list wrapping and standalone comment margins. X158 and
  X243–X250 pass in both editors; the [closure receipt](#l66-bounded-closure-and-acceptance-2026-10-05)
  records the supported forms, parser/refactoring refusals and literal/layout limits. IntelliJ
  formatting Redo is repaired locally under UP24. This is not a complete Ecstasy pretty-printer.
- [x] **L67 — Workspace indexing and dependencies at scale, bounded closure.** Exact detached
  per-module reuse now includes editor/diagnostic builds; source edits rebuild only their affected
  closure. Captured inputs no longer retain compiler trees during graph compilation. 33/129-module
  controls, binary/source-index replacement and ambiguity controls pass; X251 passes in both editors.
  First references on the 20,000-method fixtures fall from 8–11 s to 0.4–0.6 s with one compile.
  The [closure receipt](#l67-module-navigation-index-2026-10-05) records the unchanged-heap evidence,
  canonical URI fix, source-availability boundaries and measured decision against disk persistence.
  Complete references and fresh edit proofs retain their requirements beside broken neighbors;
  prolonged-session budgets remain L82 work.

### L12, project settings and L62–L68 hardening batch (2026-09-29)

Implementation checkpoints are committed separately. The combined receipt below validates this
bounded batch; broader family gaps and the real-platform blockers remain open.

- **L12:** reproduced the X76 overlap with an anonymous `new Packet<String>(...) { ... }`.
  Its generated class name borrowed the entire parameterized type span and was copied as a
  source declaration alongside the real `Packet` and `String` references. Semantic copying now
  retains the anonymous identity without assigning it a fabricated declaration. A regression
  covers complete source and unrelated missing nested call delimiters. No AST API changed.
  Backend and selected X76 acceptance now pass in both editors.
- **L16/L27:** added Settings > Languages & Frameworks > Ecstasy Compiler for discovery or
  explicit module roots/dependencies. It saves through LSP4IJ's project store and notifies the
  existing client listener. Global JSON remains the fallback. Configuration requests and rename
  history select the same owner; a newly installed project override invalidates old global rename
  history. Pure settings tests cover discovery versus an empty graph, unrelated setting retention,
  duplicate roots and blank fields. Native settings/rename/Undo/Redo acceptance passes in X118.
- **L68:** compiler clients advertising diagnostic pull negotiate one channel. Document/workspace
  requests share version guards, per-document result IDs, related reports, empty removal reports
  and refresh support. Closed roots compile on the existing serialized worker without installing
  overlays or ASTs. A bounded detached cache compares complete source membership/text, graph and
  artifact revisions. Push-only clients retain the existing path. Tests cover IDs, repairs, close,
  closed roots, cancellation, stale requests and packaged stdio. Selected native acceptance passes
  in both editors; real-platform failures discovered afterward are tracked separately below.
- **L68 IntelliJ decoding correction:** LSP4J 1.0.0's method adapter selects the outer
  diagnostic report, but its nested `relatedDocuments` map uses an ambiguous generic Either
  decoder. A valid full/unchanged related report terminated the client reader with
  `JsonParseException: Ambiguous Either type`; subsequent output backed up in LSP4IJ's pipes.
  Rename then waited for an unread reply (the measured server preparation was 3.2 ms), and
  later requests triggered restarts/startup errors. The IntelliJ launcher now registers a narrow
  adapter for that exact nested report type, reusing LSP4J's discriminator predicates. Regression
  tests use the real method/response metadata, reproduce the pinned decoder failure, and cover
  full/unchanged outer and related reports, round-trip serialization and unknown-kind rejection.
  No related-diagnostics capability is disabled, and no action replay is used to hide the failure.
- **Shared editor coverage:** X76 now asserts ordered, positive, non-overlapping semantic token
  ranges in complete and incomplete source. X118 exercises the real IntelliJ project settings
  component's Reset/Apply, blank-cell refusal and relative-root editing before native
  rename/Undo/Redo (VS Code asserts workspace persistence). Explicit graph cases keep the actual
  project root; discovery cases alone temporarily move the server workspace into their fixture.
  New X123 checks installed editor error/clear delivery plus full/unchanged/repaired diagnostic responses
  on each installed connection. Both selected drivers pass with the receipts below.
  Those selected receipts did not establish a complete 128-case pass; the later native-demo
  continuation below records complete resumed IntelliJ coverage and a full VS Code pass.
- **L67 diagnostic indexing:** per-root cached results now include source membership/text and
  dependency artifact revisions. Unchanged roots reuse detached diagnostics/artifacts; a changed
  library rebuilds its consumers. Removed roots drop their cache entries. Concurrent document and
  workspace pulls own separate queue requests. Tests measure a 21-root graph's cold/warm compile
  counts, changed-closure recompilation, removed-root eviction and cancellation isolation. This is
  bounded in-memory reuse; persistent indexing and the broader navigation index remain open.

#### Checkpoint/extraction map

| Commit | Scope | Extraction dependency |
|---|---|---|
| `2edbe2015` | L12 anonymous declaration copying and regression | Existing semantic snapshot/anonymous constructor support |
| `dc742c93f` | L16/L27 project settings UI and shared configuration ownership | Existing IntelliJ graph rename/Undo implementation |
| `73c21f2e6` | L68 negotiated pull diagnostics and closed-root queries | Existing compiler worker, source graph and versioned request lifecycle |
| `1670455bc` | Shared X76/X118/X123 coverage, plus initial test corrections | The preceding three scopes and existing editor drivers |
| `bab36f378` | L67 per-root diagnostic reuse and independent pull cancellation | L68; keeps in-memory inputs/artifacts only |
| `7665d91e9` | Catalog order/fixture corrections and safe proposal retry | Shared playbook checkpoint; no retry of accepted edits |
| `9f1f1dc75` | L62 transitive rename regression distinguishes proven annotations from an incomplete graph | Existing annotation dispatch and rename proof; no new production rename scope |
| `98f8de56e` | L64 packaged completion expectations include validated empty-string literals | Earlier L64 literal support; all three stdio argument variants |
| `f5476d305` | L68 equivalent file URIs retain diagnostic ranges and result IDs | Pull reports and workspace root identity |
| `2d43692fe` | Remove misleading first-editor cold-compilation marker | L67/pull compilation may precede editor compilation; API timings remain in the trace |
| `fa2c81cd7` | L68 IntelliJ nested diagnostic decoding and protocol regression | Negotiated related pull reports; preserves LSP4IJ compatibility adapters |
| `4553f43af` | Stronger native project-settings acceptance and rename readiness | Shared X118; invokes accepted actions once and preserves the actual explicit-graph project root |

#### Combined validation receipt

At `4553f43af`, the following command completed successfully:

```bash
./gradlew :lang:lsp-server:test --tests '*Xdk*' --tests '*SemanticModelTest' --rerun \
  :lang:intellij-plugin:test --rerun \
  :lang:lsp-server:compilerStdioTest --tests '*XdkStdioTest' --rerun \
  spotlessCheck :lang:spotlessCheck -Plsp.adapter=compiler \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache --continue --quiet
```

JUnit XML totals: **861 compiler/backend + 54 stdio + 50 IntelliJ unit tests = 965**;
zero failures, errors or skips. Root/lang Spotless also pass. No Java production change was
made in this batch. Installed editor acceptance uses shared scenario SHA-256
`959c3e71f68b00f58e6cc5cc22e275b20623442600175975ed1ab36a718567d3`:

- VS Code: `lang/vscode-extension/build/reports/compiler-playbook/run-S6wn4Y/results.json`;
  X76, X118 and X123 pass.
- IntelliJ: `lang/intellij-plugin/build/reports/compiler-playbook/run-5250369345097873268/results.json`;
  START and X76/X118/X123 pass with zero IDE failures. X118 completes in 4.54 seconds.

This is selected current-catalog coverage. Keep the earlier failed decoder receipts as failure
evidence; do not relabel them. The first shared playbook checkpoint needs its follow-up fixture,
URI, decoding and native-readiness corrections during extraction.

The broader family boundaries remain explicit: L62 still excludes unproven dispatch/binary and
external-consumer routes; at this checkpoint L63 had no extract/inline/safe-delete/missing-declaration
implementation (the October 2/4 continuations below add bounded local transformations);
L64's last batch covers its recorded literal/formal/presentation forms; L65 still cannot enumerate
runtime-selected targets; the October 2/5 L66 continuations below add import links and bounded
expression/list wrapping; L67 adds
in-memory diagnostic reuse, not a persistent or universal incremental semantic index. These are
implementation gaps, not scenarios that an editor driver can truthfully mark implemented.

### Platform demo blockers (2026-09-29)

**Platform bug-fix pass.** These were found while preparing
[root `demo.md`](../demo.md), using packaged server code at `4553f43af` and sibling platform
`b8be627b7`. No platform files were changed during discovery or revalidation. The preceding
965-test receipt and selected native passes do not cover this workload; the implementation and
new acceptance evidence are recorded below.

- [x] **PLAT1 — Source-location collection crashes on the common module.** Initialize compiler
  mode with workspace root `platform` and explicit roots `auth.xqiz.it` at
  `auth/src/main/x/auth.x`, plus `common.xqiz.it` at `common/src/main/x/common.x` depending on
  auth. Call `workspace/diagnostic` with `previousResultIds: []` (no open documents required).
  Both this two-root graph and the full eleven-main-module graph return JSON-RPC InternalError:

  ```text
  NullPointerException: Cannot invoke TypeExpression.getSource() because this.type is null
    CompositionNode.getSource(CompositionNode.java:75)
    XdkAst.declarationLocations$visit(XdkAst.kt:43)
    XdkProjectQueries.diagnostics(XdkProjectQueries.kt:113)
  ```

  This is a failed request, not observed JVM termination. `declarationLocations` asks every
  visited node for its source, including non-declarations. `CompositionNode.getSource` assumes
  a type exists when neither a parent source nor condition supplies one. `CompositionNode.Default`
  legitimately has no type; common's `model.x` contains `enum ModuleKind default(Generic)`.
  `CompositionSourceTest` now reproduces the same NPE on the unadopted parsed enum. The accessor
  now returns no source when neither a parent nor child can supply one, and returns the original
  source after adoption. No field, cloning responsibility or LSP-specific API is added. A closed
  diagnostic regression combines the enum with a missing embedded template, checks the ordinary
  `PARSER-24` report, and verifies repair. Both focused tests pass; broader/platform acceptance is recorded below. Audit `rootsBySource`, folding/selection and error-location
  callers for the same assumption. Add regression coverage that returns ordinary compiler
  diagnostics after an early failure, then re-run real common/full-platform pulls. Do not hide
  the exception by dropping all diagnostics or disabling related reports.
- [x] **PLAT2 — Gradle resource roots are absent from the source model.** A separate one-root
  workspace pull for `stub.xqiz.it` at `stub/src/main/x/stub.x` returns
  `PARSER-24: Invalid path: "./not-deployed.html".` The file exists at
  `stub/src/main/resources/not-deployed.html`. This is an ordinary compilation diagnostic,
  not a crash. `XdkSources` constructs `ModuleInfo(root, false)` and the configured source graph
  currently carries root/dependencies, without resource roots. Investigate the existing embedding
  resource API and Gradle source-set mapping; support resource inputs and their invalidation
  through the same ownership model rather than copying assets into source directories. Add a
  minimal source/resource fixture and packaged diagnostic regression. Then audit common's
  `_webModule.txt` / `_createAuthenticator.txt` and kernel's `/cfg.json` resources; their outcomes
  remain unverified because PLAT1 interrupts the common-dependent graph first.
- [x] **PLAT3 — Real-source presentation/recovery discrepancies.** The explicit auth+githubCLI
  graph compiles cleanly (seven document reports). With these sources open, packaged requests
  expose the following non-crashing cases to minimize after PLAT1/PLAT2:

  | Request and source anchor | Observed result | Next check |
  |---|---|---|
  | Hover on `sendRequest` in `Repositories.listRepositories` | Displays `method listRepositories`, although definition correctly finds `GithubGateway.sendRequest` | Occurrence/symbol versus enclosing-declaration selection |
  | Signature help after `GET, ` in that completed `sendRequest` call | Correct full signature text, empty `parameters`, active index 0 | Selected multi-file call facts and parameter mapping; incomplete `createRequest(method, gr, path, content)` correctly has four parameters and active index 1 |
  | Replace `console.readLine(prompt).trim()` with `.tr()` at the end of `tr` | Empty completion list | Chained call receiver recovery; the same module's `response.st` correctly offers `status` |
  | Type definition on `repo` after `assert repo.is(JsonObject)` | No locations | Determine expected alias/relational-type targets; simple `response` correctly opens bundled `ResponseIn.x` |

  Do not label every empty result a compiler defect before inspecting its semantic contract.
  Add focused regressions for confirmed bugs and update the demo's expected results afterward.

**PLAT1 checkpoint:** `db328f8a1`; focused parsed-tree and closed-diagnostic regressions pass.

**PLAT2 checkpoint:** `7bce2da9b`; server and both plugins compile. The three resource-input
regressions pass after normalizing the test's macOS temporary-directory alias; resource-only
watched deletion/repair and delayed registration tests also pass. Six VS Code configuration tests
pass. Broader validation and packaged resource acceptance are recorded below.

**PLAT3 implementation checkpoint:** hover selects the resolved occurrence and substituted call
signature; whitespace before written arguments keeps their compiler parameter mapping. Inferred
nominal types now intern declaration targets before immutable fact tables are frozen. Parser
recovery retains a member prefix before existing call parentheses and keeps the written arguments;
the existing partial semantic bridge validates its original callee. Four minimized platform
regressions and all 25 parser recovery tests pass. Combined/platform acceptance is recorded below.
The accompanying Java run executed 511 tests and skipped 40 unrelated fixture-dependent tests;
the affected parser, partial syntax, cursor-binding and composition-source classes had no skips.

**Real-platform acceptance at `b9b153298`:** the rebuilt packaged server returns all 49 document
reports for eleven main modules with **zero diagnostics**, after explicitly setting platformUI's
resource roots to its actual `platformUI/gui/dist` directory. Its build adds that nonconventional
generated input with `sourceSets.main.resources.srcDir(guiDistDir)`. The generated `spa` directory
already exists in this checkout; the server neither builds it nor copies it. Without that setting,
the same graph correctly reports only the missing `/spa` resource. Common's embedded templates,
stub's HTML and kernel's `cfg.json` now resolve by convention.

Packaged real-source requests also confirm the corrected `sendRequest` hover, four signature
parameters with `group` active, chained `.tr()` completion offering `trim`, and narrowed `repo`
type-definition navigation to bundled `Map.x`. Existing five OAuth subtypes, property overrides,
six callers and bundled `ResponseIn` navigation still work. `demo.md` includes the exact resource
setting. This is protocol acceptance, not a new native platform IDE run. Shared editor-driver
coverage for the resource column/external watchers and these exact real-source variants remains a
follow-up; existing X118/X123 must not be cited as that evidence.

**PLAT2 behavior:** `resourceRoots` is an optional ordered
list on each source module, carried through server configuration and native rename round trips.
Explicit roots replace defaults, `[]` disables resources, and omitted/null uses the compiler's
existing layout deduction. IntelliJ's existing module table adds a JSON-array resource column;
VS Code's settings schema exposes the same field. The richer path picker/origin view and Gradle
model import remain PLAT2c work. Resource fingerprints include contents and filesystem metadata;
uncertain lexing/interpolations conservatively capture the resource trees. Modules with no possible
path tokens avoid that scan. This is conservative filesystem validation, not a persistent resource index. Large resource trees
can make snapshot capture expensive; measuring this and narrowing the observed read set remain
part of L67 performance work.
Workspace resource events and external-root subscriptions refresh affected consumers, and resource
inputs also participate in delayed-watcher/stale-result checks. New tests cover conventional/custom
roots, unchanged reuse, same-size/same-timestamp edits, creation/deletion, explicit-empty roots,
configuration ownership and delayed watcher registrations. See the combined receipt below.

**Combined validation receipt (2026-09-29):** the forced batch exercised 878 compiler/backend,
70 packaged-stdio and 51 IntelliJ unit tests. It found one semantic regression (nullable type
navigation also offered the `Nullable` marker), one obsolete unsupported-boundary expectation,
and two new-test setup errors (canonical temporary paths and pull-diagnostic negotiation).
Nullable navigation now explicitly unwraps the marker; the negative cursor test still rejects a
mid-token call name, while completed prefixes are covered positively. The focused recheck passes
all 113 affected backend tests and the new packaged resource test, with zero failures/errors/skips.
The other 875 backend, 69 stdio and all 51 IntelliJ tests passed in the initial batch. Root/lang
Spotless checks and six VS Code configuration tests pass. The Java run passed 511 and skipped 40
fixture-dependent tests; the 39 directly affected parser/partial/cursor/source tests had no skips.
No native IDE run was performed for this batch. Gradle XML for the two rerun tasks now contains
only the focused recheck; this receipt distinguishes that from the original broader run.

Future extraction map for this batch:

| Commit | PR scope and dependency |
| --- | --- |
| `db328f8a1` | General compiler source-accessor correction plus parsed-tree regression; the adapter diagnostic regression travels with the embedding/LSP integration. |
| `7bce2da9b` | Resource input model, server invalidation/watchers, both editor configuration stores and regression coverage; depends on the existing source-graph/diagnostic foundation. |
| `b9b153298` | Platform presentation/recovery fixes. Parser/partial validation and its Java regression form the compiler slice; semantic-model/hover/signature code and adapter tests form the dependent LSP slice. Includes the PLAT2 fixture's canonical-path correction. |
| `28e9e785c` | Required validation follow-up: nullable target unwrapping, corrected regression setup/expectations, clean platform receipt and generated-resource demo configuration. Carry the semantic correction with the PLAT3 LSP slice and the resource test setup with PLAT2. |

#### Native IntelliJ demo continuation (2026-09-29)

The requested complete demo uses IDEA 2026.2.3, LSP4IJ 0.21.0 and the compiler adapter, with
Ultimate disabled: 128 shared scenarios plus START. Resume selections retain earlier passing
cases; they are not a claim of one uninterrupted green session. Reports are under
`lang/intellij-plugin/build/reports/compiler-playbook/`.

| Run | Result and interpretation |
| --- | --- |
| `run-11475125043251847668` | 47 passed including START; X77 failed; 81 unrun. X77's exact candidates omitted the newly supported empty-string literal. |
| `run-5298967654461762165` | START and corrected X77 pass. X78's corresponding empty-slot expectation also now includes empty-string and zero literals. |
| `run-4536966772905399493` | 48 passed including START; X79 failed; 80 unrun. Reopening completion after focus loss retained IDEA's completion phase and broadened it to second-invocation word suggestions. |
| `run-246814967855164574` | START, X78 and X79 pass after the test probe resets completion state before reopening an unapplied popup. |
| `run-17920019611681328192` | Separate focus-recovery regression passes, including exact argument candidates after deliberate focus loss and refusal to replay completed insertions/renames. |
| `run-5817849844866938583` | 73 passed including START; X122's popup disappeared during inspection; 55 unrun. Desktop focus was interrupted; the successful server reply does not prove the precise UI race. |
| `run-1469002789213532574` | START and X122 pass without changes. X105's bundled import passes, but its source-import menu does not appear; subsequent typing/backspaces change the fixture and correctly trip the replay guard. |
| `run-8956398297835414472` | All 54 selected scenarios execute: 51 pass, X14/X27/X31 fail, plus START passes. Zero IDE failures. |
| `run-12379175007830760477` | START and X123 pass after the diagnostic correction; X105's source-import menu still fails without fixture edits. |
| `run-2139618945479802957`, `run-3818964206800652880` | X105 also reproduces with its initial bundled-import menu missing. Failure capture initially contains server timing logs only; native protocol tracing needs project settings and an instantiated console. |
| `run-15219420768851272063` | START, X14, X27 and X31 pass; zero IDE failures. The focused Gradle task and lang Spotless check succeed. |
| `run-4150656664524065941` | START passes; X105 fails. Captured native wire traffic proves a cancelled diagnostic quick-fix request and a successful import response on the separate intention path. Zero IDE failures. |
| `run-12618281251279779358` | START, X105, X122 and X123 pass after the diagnostic result-ID correction; zero IDE failures. The focused Gradle task and lang Spotless check succeed. |
| `run-5742519770640114134` | Final code: START and X60/X77/X78/X105/X122/X123 pass, with zero IDE failures. Rechecks the shared-server invalidation correction and the final sorted completion expectations after the full VS Code run. |

The last batch found two harness issues and one production diagnostic bug:

- **X14:** save returned before the disk bytes were visible to the test. The eventual file retained
  the exact emoji/completion text and CRLF bytes. Wait for those same exact bytes with a bounded
  deadline instead of asserting immediately after requesting save.
- **X31:** move `declarationProvider` from unsupported to supported in the shared expectations;
  declaration lookup was implemented in L61. Neither editor should assert the old capability set.
- **X27:** a closed member of an open standalone module was missing from document pull diagnostics
  when the source graph was empty. Include its current owning module analysis, as workspace pulls
  already do, and exclude unrelated standalone modules. Nine pull-diagnostic tests pass, including
  a new creation/repair/deletion and equivalent-URI regression. Push behavior is unchanged.

**Result: all 128 shared scenarios have passing native receipts across these resumed runs, plus
START. This is not one uninterrupted full-suite checkpoint.** Both X105 imports now pass through
the installed client's native menu; X122 member generation and X123 pull diagnostics also pass
with the client correction. X14/X27/X31 pass in their focused recheck.
The harness now captures client console and queued protocol messages to `client-trace-<ID>.log`
on a failure, before IDE cleanup, with verbose tracing in the disposable project settings.

- [x] **X105 / LSP4IJ cancelled quick-fix recovery:** correct and regress the native diagnostic-action
  lifecycle. In `run-4150656664524065941`, request 24 asks for the diagnostic's quick fixes and is
  cancelled after document diagnostic response 25. Request 26 then receives the correct versioned
  import edit in 489 ms, but it belongs to LSP4IJ's general intention path. Inspection of the pinned
  0.21.0 classes confirms `LSPIntentionCodeActionSupport.isValidCodeAction` excludes `quickfix`,
  while `LSPLazyCodeActions` retains its cancelled future instead of reloading it. Reopening the
  menu cannot repair that state. Its automatic pulls omit `previousResultId`; repeated identical
  full reports replace and cancel lazy quick fixes without refreshing the unchanged annotations.
  `DiagnosticResultMessages` now carries result IDs across automatic pulls of the same editor
  snapshot, allowing the server to return `unchanged`. The connection owns this cache; edits,
  close/reopen, cancellation and errors cannot seed or reuse an obsolete result. Explicit callers
  retain their own result-ID policy. Seven new transport tests and eight existing startup-message
  tests pass, and the native X105/X122/X123 recheck passes. Native menus, quick-fix kinds and
  cancellation of obsolete edits remain intact.
- [ ] **Demo notification cleanup:** CFG2 intentionally rejects a cyclic graph and verifies the
  previous valid configuration still works. After asserting rejection, dismiss only that expected
  test notification so it does not linger over subsequent cases. Keep ordinary user configuration
  errors visible; do not globally suppress server errors in the harness.
- [ ] **Popup inspection race:** handle a popup disappearing between its presence check and row
  read, while retaining the document-change guard and never replaying an accepted edit.

This run does not add PLAT2e's missing resource-settings or precise PLAT3 native scenarios. The
separate startup-editing test was not rerun; the full VS Code result is recorded below. The scenario/capability
corrections belong with their feature's shared acceptance slice; completion/save synchronization
belongs with the IntelliJ test driver. The closed-member fix and regression belong with L68;
the result-ID transport correction and its tests belong with the dependent IntelliJ client slice.

The first full VS Code follow-up, `run-tXQ7fz` on VS Code 1.139.1, ran all 128 cases:
125 passed, with X60/X77/X78 failing. X77/X78 received the correct candidate sets, but their shared
literal expectations were appended instead of sorted. The corrected data passes both cases in
`run-kbQPh9`; assertions remain exact. X60 still fails in that focused run and in diagnostic captures
`run-3I5wLj` and `run-vf5R2T`.

X60 exposes a production invalidation bug introduced with the PLAT2 resource work: opening closed
rename consumers and delivering their unsaved edits can classify a source buffer as a disk resource
under the shared default resource root. Refreshing its dependency after the consumer cancels the
consumer's new analysis without replacing it. Its outline remains empty, and automatic diagnostic
pulls repeatedly receive cancellation. A direct server regression reproduces that cancellation.
Source-buffer edits now use source dependency scopes; disk events retain resource invalidation,
ordered before their consumers. All 22 focused pull-diagnostic, project-server and resource-input
tests pass after the fix. Carry this correction and regression with PLAT2's resource-input slice.
VS Code `run-GyEveY` passes X60/X77/X78; X60 completes in 2.355 seconds instead of timing out after
30 seconds. The initial lifecycle capture is preserved in `run-vf5R2T/client-lifecycle.log`.
The final full VS Code run, `run-b59XBq`, passes **all 128 scenarios in one uninterrupted run**,
with no skipped cases, failures or extra suite errors. It uses VS Code 1.139.1 and shared-catalog
SHA-256 `6771c98e25cec3043c64c2b0d74499a59891a1b3b7fefd627827fba712d63f45`.
X60 completes in 1.992 seconds. IntelliJ `run-5742519770640114134` uses the same catalog hash and
passes START plus X60/X77/X78/X105/X122/X123 on the final code, with zero IDE failures and zero
JUnit failures/errors/skips. This completes the focused cross-editor recheck; the earlier native
128-case coverage remains a collection of resumed receipts.

- [x] **Combined validation task wiring:** the earlier attempt to run `:lang:vscode-extension:testCompilerPlaybook`
  with `:lang:spotlessCheck spotlessCheck` in one invocation was rejected before testing. Gradle
  reported an undeclared dependency between `:lang:spotlessKotlinGradle` and the VS Code
  `copyLicense` output under `lang`; that failure was not a test result. The L69–L71 batch
  below fixes both copy tasks to declare individual files. `packageExtension` and both formatting
  checks now pass together in the combined invocation.

The initial broad backend run passed 1,412 tests with zero failures/errors and three pre-existing
disabled Tree-sitter placeholders (inlay types, cross-file rename and shadow-aware references).
All 858 Xdk tests ran without skips. All 70 packaged protocol tests passed without skips. These
receipts precede the X60 production correction. The final broad recheck now passes 1,413 backend
tests (859 Xdk tests), with the same three disabled Tree-sitter placeholders and zero failures/errors.
All 70 packaged protocol tests also pass again with zero skips.

Future extraction map for this validation batch:

| Commit | PR scope and dependency |
| --- | --- |
| `0659cbdf9` | L68 closed standalone-member pull diagnostics and creation/repair/deletion regression; depends on the pull-diagnostic lifecycle and URI identity support. |
| `23701b9c5` | PLAT2 resource-input follow-up: separate buffer edits from disk-resource invalidation, preserve dependency ordering, and regress the cross-file rename cancellation. Carry with `7bce2da9b`. |
| `b013606be` | IntelliJ pull-diagnostic compatibility: connection-scoped previous-result IDs, shared document snapshots, and transport regressions. Depends on the existing launcher/startup-message integration and server unchanged reports. |
| `3e40589b7` | Editor acceptance: native focus/save synchronization, failure trace capture, and shared X31/X77/X78 expectations. Carry the expectations with their feature slices and the native helpers with the IntelliJ playbook driver. |

The receipts above validate the final integrated tree. They do not establish independent green
checkpoints for intermediate commits or extracted PRs; each extracted PR still needs its own checks.

#### Next implementation batch (L69–L71 / PLAT2c, 2026-09-29)

Five separate implementation commits precede combined validation:

1. Acceptance: correct single-file VS Code task outputs; expire CFG2's expected cyclic-graph
   notification after asserting rejection; add shared X124 external-resource Apply/Reset/repair
   and X125 exact platform regressions. Validation receipts and corrections are recorded below.
2. L69: negotiated semantic-token range/delta and bounded result lifetime; backend, packaged
   protocol and shared X126 checks pass in both hosts.
3. L70: versioned lazy completion documentation/code-action edit resolution is written;
   selected properties require client support. Initial queries still compute compiler proof and
   detached facts; resolve delays payload conversion/transfer, not a second compiler pass.
   Bounded connection-local handles reject edits, dependency/configuration changes, close/reopen,
   and foreign connections. Shared X127 exercises each client's negotiated transport contract.
   IntelliJ requests eager action edits because LSP4IJ 0.21.0 loses Redo for resolved edits;
   completion documentation resolve remains enabled. Backend/protocol and shared checks pass.
   Code lens/link/inlay/workspace-symbol resolvers remain a separate L70 follow-up.
4. L71: negotiated pre/post file hooks, compiler-proven file/package renames (including combined
   batch proof and required companion moves), and binding-preserving container moves are written.
   Duplicate small-file watcher/operation events coalesce by contents; directory/large-file events
   always propagate without scanning entire trees on the notification thread. X128 drives both
   hosts' file Rename actions. Backend/protocol and shared checks pass.
   Cross-package relocations needing rewritten qualification, collisions, overlapping operations,
   symlinks and explicit root/settings changes remain conservative refusals. `willRenameFiles`
   cannot veto the host's move: a null result means no safe automatic reference update.
5. PLAT2c: evaluated Gradle inputs and origin-aware effective paths in both hosts are written.
   `exportXtcLspModel` exports `.gradle/xtc/lsp-model.json`; `prepareXtcLspModel` also runs resource
   processing. The model carries source-set/project ownership, generated sources, original and
   processed resources, source dependencies and binary module paths. Both hosts import the same
   schema and retain explicit overrides. IntelliJ's Community settings page and VS Code's native
   commands show effective paths, missing outputs and origin, with refresh/prepare/open-build/reset
   actions. Gradle runs only through an explicit host action, never inside the server. Build-script
   parsing is not used. New TestKit, server, importer and shared X129 tests pass.
   Automatic IntelliJ Gradle-sync import and nested/composite build aggregation beyond each
   exported root remain follow-ups; use explicit refresh or import each workspace-root report.

Master fix extraction: early resource-root capture is already fixed by `5d88b64d4` (#651).
The remaining Copy-destination mismatch is independently fixed in
[`ee1792753`, PR #655](https://github.com/xtclang/xvm/pull/655), based on master `a96bb0f75`
and merged as `6f8f1f897` on 2026-09-29.
Its three source-set regressions fail before the fix; all 57 plugin tests pass afterwards,
including six configuration-cache TestKit cases, with zero skips. Spotless passes.
Carry the corresponding provider wiring in this batch, but do not extract it again with the
LSP model bridge: #655 is already merged. These receipts validate the standalone fix, not this batch.

The new catalog has 134 scenarios. The previous 128-case receipts remain historical evidence;
new cases and changes are not validated by those receipts. X118 now carries ordered custom
resource roots through native module rename and Undo in both hosts.

Extraction checkpoints (each still requires independent validation when split):

| Commit | Future PR scope |
|---|---|
| `581e09197` | Resource/settings and platform acceptance; includes the VS Code single-output task fix. |
| `5322d0bda` | L69 negotiated token range/delta, report lifetime and X126. |
| `ec6976526` | L70 completion documentation/action edit resolve, detached handles and X127. |
| `b420f0f19` | L71 file-operation participation, combined rename proof and X128. |
| `18f5cb8a2` | PLAT2c/L67 evaluated Gradle input bridge, both host path views/importers and X129. |
| `4208d5ec7` | L71 validation corrections: logical snapshot roots and early symlink refusal; carry with `b420f0f19`. |
| `db63c64d8` | Carry the action-kind and workspace fixture fixes with L70, the TypeScript/macOS model fixtures with PLAT2c. |
| `d5de386f2` | IntelliJ diagnostic-refresh/Rename lock ordering; carry with native pull-diagnostic integration. |
| `409b356d3` | L70 IntelliJ capability compatibility and regressions: eager action edits preserve Redo with LSP4IJ 0.21.0. |
| `57feaf3df` | L71 native Rename preflight and real chooser/dialog coverage; includes X124 VFS fixture and X127/X129 ordering/setup corrections in the same driver file. Split those fixture hunks with their owning acceptance slices. |
| `68e537a` | VS Code selected-acceptance corrections; carry X118 resource ownership with rename/settings, X125 hover with platform acceptance, X127 graph setup with L70, and X129 order/settings with PLAT2c. |

The first combined pass found a shadowed Kotlin action-kind local, a TypeScript test API newer
than the configured target library, and a TestKit path assertion that did not canonicalize macOS's
`/var` alias. The second combined pass passes all 21 plugin tests, 60 IntelliJ unit tests,
71 packaged-server protocol tests, TypeScript compilation and formatting. The backend run has
1,439 tests, five failures and three pre-existing disabled Tree-sitter tests. Corrections under
validation cover two file-operation defects (symlink identity lost before refusal, and proposed
container destinations required to exist), one more macOS path expectation, and two code-action
fixtures that lacked a project workspace. The final combined recheck passes all 1,436 enabled
backend tests (three existing Tree-sitter placeholders disabled), all 71 packaged-server protocol
tests, 60 IntelliJ unit tests, native-driver compilation, VS Code packaging and both formatting
checks. The focused compiler input run passes all 96 `ModuleInfo` tests with no skips. All 16
VS Code extension tests also pass, including the two new model importer tests.

VS Code 1.139.1 `run-KEzKf8` passes CFG2/X105/X122/X124/X126/X128 and exposes four fixture failures.
`run-AW9amt` passes X118/X125/X127/X129 after correcting relative resource paths, reading rendered
hover Markdown, explicitly configuring the action fixture graph and rereading current settings.
All ten selected cases therefore have passing receipts across two runs; this is not a full
134-case run. Both use catalog SHA-256
`ed7da902a5b5b3e2f256be4a52c3a94c17f400a4ff082e7cd14ee1ef14c1da33`.

Native `run-10979214218498979471` passes START, then deadlocks during X118 module rename. The IDE
and blocked test worker were stopped after capturing thread stacks; this is failed acceptance,
not a skipped/green case. The EDT holds the write lock in `XtcRenameEdit.apply`, and LSP4IJ's
`AbstractLSPFileListener.before` waits for `willRenameFiles`. Its message worker is blocked inside
our `XtcLanguageClient.refreshDiagnostics` acquiring a read lock; the compiler worker is idle.
Moving diagnostic/cache work onto an ordered shared-pool executor breaks that transport lock
cycle while preserving notification order. Native `run-15252624690665022121` then passes START
and X118, including Rename/Undo/Redo (6.36 seconds for X118).

That recheck exposes a separate X128 host defect: IntelliJ's `PersistentFSImpl.renameFile` moves
the physical file before publishing the VFS before-event. LSP4IJ 0.21.0 sends `willRenameFiles`
from that event, so the compiler sees the old path missing and the new path already present and
correctly refuses reference edits. Raw VFS rename is therefore not a usable preflight boundary.
The XTC project-tree Rename handler now requests compiler proof before any disk change, then
combines the returned versioned edits and requested file move in the existing guarded Undo
transaction. X128 invokes the registered handler and submits its actual Rename dialog for both
a `.x` file and an implicit package directory. Direct low-level VFS moves still provide
notifications but cannot promise reference updates; cross-directory moves and arbitrary container
actions need a corresponding host preflight entry point before being advertised as native support.
No compiler refusal was weakened. The selected native recheck passes as recorded below.

The wider recheck `run-4597434703547124054` again passes X118 (6.85 seconds), then fails X122
Redo after a lazily resolved member-generation action. LSP4IJ's
`LSPLazyCodeActionIntentionAction` wraps resolved edits in
`DocumentUtil.writeInRunUndoTransparentAction`; eager edits use the ordinary command path.
The client now omits `edit` from its code-action resolve capabilities until that upstream path
supports reliable Undo/Redo. The server's resolver and VS Code negotiation stay enabled;
completion documentation resolve remains enabled in IntelliJ. Two client-capability regressions
cover this selective negotiation. X127 checks the negotiated eager fallback in IntelliJ, not a
native lazy-action acceptance claim. `run-13167310945496563336` also records an X105 intention
popup failure with lazy import edits; the server returned the expected action. Recheck X105/X122
after applying the capability correction, without replaying any previously applied edits.

Final native receipts: `run-8616709537315408794` passes START and
CFG2/X105/X118/X122/X125/X126/X127/X129, with zero IDE errors. X124 and X128 initially fail:
the external fixture was not loaded/refreshed in VFS, and Rename's competing-handler chooser
was entered under a plain read action. `run-431060674485649448` passes START, X124 (9.32 seconds)
and X128 (4.91 seconds) after correcting those driver boundaries. X128 exercises the actual
scope chooser and name dialog, then asserts both source/reference edits and the resulting paths.
All ten selected native cases therefore have passing receipts across two final runs; this is
not a new full 134-case run. Both runs use the catalog hash recorded above. The plugin has 62
passing unit tests, zero skips, including the two new capability regressions.
The final IntelliJ unit rerun and read-only root/lang `spotlessCheck` tasks pass. These receipts
validate the integrated `lagergren/errs` branch; each extracted PR still requires independent
validation.

X124 establishes external-resource reactions after native VFS refresh, with the fixture directory
loaded first; it does not establish autonomous OS watching of arbitrary roots unknown to VFS.
Track that distinction instead of treating synthetic LSP notifications as editor coverage:

- [x] PLAT2/L67 bounded watch validation: implementation now owns external source/resource VFS watch leases per
  connection, coalesces asynchronous refreshes on the shared scheduler, observes missing roots
  through their nearest existing parent, and releases subscriptions on replacement/disposal.
  X124 in both drivers now creates an unopened nested root and replaces it through settings;
  IntelliJ no longer primes or refreshes the fixture manually. Unit tests cover shared leases,
  delayed refresh, replacement, disposal and wire-pattern decoding. X124/X134 pass in both editors;
  see the [watcher receipts](#watcher-move-and-log-view-acceptance-follow-up-2026-09-30).
- [x] L70 bounded validation: all remaining standard resolve endpoints now use bounded detached handles:
  code-lens commands (including arguments), document-link targets/tooltips, inlay tooltips and
  workspace-symbol ranges. Client property negotiation keeps eager fallbacks. Handles expire on
  edits, dependency/configuration changes, close/reopen and connection disposal. Shared X131
  exercises the installed connections; backend tests cover deferred payloads and stale handles.
  LSP4IJ 0.21.0 remains the latest release (upstream API checked 2026-09-30). A supported client
  command now resolves only the selected action and applies it through the existing document-epoch
  guard and global undo command. The native resolve capability is restored; X105/X122/X127/X131
  pass in the watcher acceptance batch. No edits occur while listing or explicitly resolving.
- [x] L71 bounded validation: Community Move now preflights multiple source files/module containers,
  validates destination collisions before proof and application, and applies VFS parent moves in
  the same global undo command as reference edits. LSP4IJ 0.21's RenameFile implementation ignores
  destination parents, so this path explicitly performs them. Shared X130 covers discovered batch
  containers, closed members/resources and one Undo/Redo. Explicit graph relocation and moves
  needing qualification rewriting remain refused. Unit tests and selected X128/X130 pass in both
  editors; the watcher receipts retain the initial failures and final corrections.

The container-move proof needs a protected `ModuleInfo(File, String)` constructor for host-supplied
logical source identities. It avoids filesystem discovery when replaying immutable text/membership
at a proposed destination. `XdkSources` obtains the module name from snapshot text; ordinary CLI
constructors keep their existing discovery behavior. This belongs at the compiler input boundary,
not in AST nodes, and adds no mutable fields. Carry it with L71's proof fixes when extracting PRs.

#### Resource configuration and build-model integration (PLAT2 / L67)

The accepted direction is one compiler input model shared by both hosts, with visible ownership.
IntelliJ treats imported Gradle configuration as authoritative and directs users to edit the build
file ([content roots](https://www.jetbrains.com/help/idea/content-roots.html),
[Gradle import](https://www.jetbrains.com/help/idea/work-with-gradle-projects.html)). VS Code Java
also supports unmanaged folders with a classpath configuration UI
([Java project management](https://code.visualstudio.com/docs/java/java-project)). For XTC:

- **Build-managed projects:** import evaluated source sets, resource roots, source dependencies
  and generated inputs from the build model. This must handle both `.gradle` and `.gradle.kts`,
  plugins and Provider-based configuration; never infer arbitrary build behavior by parsing script
  text. The build model is authoritative by default. Offer **Open build file** and **Refresh build
  configuration** from the paths view; do not silently rewrite a build script.
  Include task-produced roots and resource processing (copy/filter/rename), with an explicit
  refresh/build-generated-resources action and a clear missing-output state. Merely importing
  `srcDirs` is insufficient when the compiler consumes transformed task outputs.
- **Unmanaged projects:** no build file is required. Discover ordinary source/layout defaults,
  allow explicit per-module source/resource paths and dependencies, and persist project/workspace
  settings. An explicit override also remains available in a build-managed project, labelled as an
  override which can diverge from the build. Reimport must not silently overwrite that choice.
- **Effective configuration:** show each path, its module/source-set owner and its origin
  (build model, explicit project setting or convention). Unset resource roots mean use the
  available model/default; an explicit empty list means none. Preserve order/precedence, distinguish
  main/test/generated roots, allow paths outside the workspace, and validate missing/duplicate
  paths without turning a temporary generated-output absence into an IDE startup crash.
- **IntelliJ:** extend the existing **Languages & Frameworks > Ecstasy Compiler** page, with
  resource-root editing for explicit configuration. The eventual build-managed view should explain
  ownership and offer refresh/reset-to-build controls. Use Community APIs only; no separate
  proprietary project service or duplicate LSP configuration store.
- **VS Code:** expose the same schema in workspace/folder settings and provide an **XTC: Configure
  Compiler Paths** command using native picker/input controls. Show effective roots and origins
  through a small read-only view/output until a richer view is justified; a custom webview is not
  required. Multi-root workspaces must retain folder ownership rather than relying on process cwd.
- **Compiler boundary:** send resolved immutable inputs to the LSP/embedding layer. That layer
  must not execute Gradle itself, depend on either IDE, or copy resources into source directories.
  Resource contents/membership and root configuration participate in cache identities, stale
  result rejection, watched-file invalidation and rename configuration round trips.

Implementation sequence and acceptance:

- [x] **PLAT2a (current bug-fix batch):** explicit ordered resource roots in the host/server
  configuration, compiler layout fallback, resource-aware cache/watch behavior, and conventional
  plus custom-directory regressions. Exercise create/edit/delete and unchanged reuse.
- [x] **PLAT2b (current bug-fix batch):** editable IntelliJ project paths and equivalent VS Code
  configuration schema; retain custom roots through Apply/Reset and module rename/Undo. Add shared
  manual steps and focused settings/protocol tests.
- [ ] **PLAT2c / L67 (follow-up):** evaluated Gradle/XTC project-model bridge and origin-aware
  effective configuration UI in both hosts, including the VS Code command. Reuse an existing
  imported IDE model where complete; determine the portable XTC/Gradle model contract for VS Code.
  Test custom and generated resource roots, no-build-file projects, reimport and explicit overrides.
  Manual paths are an interim supported route, not evidence this bridge already exists.
- [x] **PLAT2d (acceptance):** run the full platform main graph with conventional resources, plus
  a fixture whose build config selects a different directory. Verify missing/generated roots and
  resource-only changes without recompiling unrelated consumers or returning stale diagnostics.
- [ ] **PLAT2e (native acceptance follow-up):** add shared scenarios for the resource-root column
  and externally watched roots in both drivers, including Apply/Reset, resource repair and
  rename/Undo preservation. Add the precise PLAT3 call-prefix/hover/narrowed-type variants. The
  current backend/settings/protocol regressions and manual instructions do not count as native
  editor evidence.

**Checked alternatives for the demo:** auth+githubCLI workspace diagnostics have no errors;
`OAuthProvider` has five source subtypes and property implementations; `sendRequest` has six
cross-file callers; bundled `ResponseIn` type navigation works. Scope/value and incomplete-call
completion return `group` and other String-compatible values with correct replacement ranges;
`response.st` offers `status`, and the `Str defaultValue` header offers `String`. Source files
were read-only throughout the probes; edits used LSP buffer overlays. These are packaged protocol
observations, not a new native IntelliJ playbook receipt. The full main-module configuration and
manual targets are in `demo.md`; do not remove its blocker notice until that configuration passes.

### L64 completion and signature batch (2026-09-29)

Implement four checkpoints before combined validation:

| Checkpoint | Commit | Extraction placement |
| --- | --- | --- |
| Written recursive constraints | `3b3bc893a` | Compiler cursor facts, Kotlin copying and header regressions; include resolver-owner correction from validation. |
| Literal arguments | `1c1052158` | Compiler argument probes, immutable literal facts and adapter/protocol Value support; include record-pattern and mid-token recovery corrections. |
| Documentation and presentation | `65bfe5b3a` | Detached documentation, deterministic ordering and signature metadata; include passive method-comment accessor and source copying. |
| Shared editor acceptance | `6e7932b61` | X97/X108 data and native metadata assertions in both drivers; include TypeScript optional-field corrections. |
| Validation corrections and receipts | `fd54249f8` | Distribute resolver, parser, comment-accessor, compatibility and driver corrections into their preceding slices; retain the combined backend/native evidence. |

These development commits require their validation corrections when extracted; no independent
green result is claimed for an intermediate checkpoint.

1. [x] Preserve written recursive formal constraints in incomplete declarations. Known, visible
   class type arguments may guard recursion; direct/sibling cycles, unknown names and unresolved
   formal-member lookup remain refused. Syntax-only constraints have no fabricated type identity.
2. [x] Offer literal argument values only after ordinary compiler fitting and full argument validation.
3. [x] Copy candidate documentation, provide deterministic completion ordering and improve
   overload/active-argument presentation without claiming an incomplete overload was selected.
4. [x] Extend shared scenarios and both drivers. X97 adds eight literal variants (24 total),
   including named/compound/function/constructor slots; X108 adds two recursive-bound variants
   (13 total). Native proposals expose Value kind, exact edits, detail and sort metadata. Native
   Parameter Info responses supply documentation and active indices without a competing request.

- [x] Run combined backend/protocol checks and compile both editor drivers: 337 tests pass,
  with zero failures/errors/skips; see the receipt below.
- [x] Finish selected native X97/X108 acceptance in both clients. VS Code and IntelliJ pass all
  37 variants; IntelliJ also passes START. This is not full-catalog coverage.

Checkpoint 1 extends `CursorBinding.Formal` with optional written-constraint text. Its existing
two-argument constructor remains; record-pattern users need the new component. A recursive written
constraint has no `TypeConstant`, and the detached Kotlin model preserves that distinction in
completion/hover. This is display/name completion only, not a bound for member lookup or fitting.
No AST field, invented formal declaration, parser mode or retained Context is added. Unrelated
parse errors and unclosed outer bodies away from the cursor remain separate recovery limitations.
All four checkpoints were implemented before the first combined test run.

Checkpoint 2 proposes `True`, `False`, `Null`, `0` and the empty string through the same compiler
trial-argument path as source variables. Every proposal must fit and validate the whole argument,
including operators, named slots and other written arguments. Qualified member slots exclude
literals. `CursorBinding`/`CallFacts` add immutable literal-spelling lists while retaining previous
constructors; record-pattern consumers must include the new component. The detached model copies
only strings, and the LSP exposes them as Value completions with the existing exact token edit.
This covers argument slots, not arbitrary expressions, collection synthesis or snippets.

Checkpoint 3 copies existing compiler component documentation into detached symbols and carries
it through completion and selected/candidate signatures. Completion ordering explicitly prefers
locals, then properties, methods, types/modules and literal/keyword proposals; ties use source
labels and rendered signatures, not snapshot IDs. Exact-fit signatures precede converting ones,
with deterministic labels and an explicit conversion note. Candidate-specific named mappings and
default/required parameter descriptions remain visible without claiming overload selection.
Adapter and packaged-stdio tests cover the new metadata and pass after the corrections below.

The first combined execution caught required integration corrections: the API-compatibility record
pattern needs the literal component; existing exact suggestion sets need the newly valid literals;
and TypeScript must narrow optional scenario metadata. Method components do not currently carry
their source comments, so `MethodDeclarationStatement.getDocumentation()` exposes decoded existing
comment text without state or caching. Kotlin copies it while visiting source declarations; binary
documentation is used only when the existing component provides it.

The new regressions also exposed two implementation defects: type-goal `NameResolver` requires a
`NameResolving` syntax owner, so guarded-bound lookup now uses a disposable named-type reference
parented to the real scope; mid-token argument names now apply the existing recovery-boundary test
at the written token end while retaining the actual cursor and full replacement token. Parser
speculation/cancellation guards and ordinary parsing are unchanged. The focused literal tests now
pass; the corrected combined backend validation is recorded below. No temporary debug output is retained.

Validation receipt, 2026-09-29:

- **47 Java tests**: cursor bindings, partial syntax ownership, parser recovery, binding snapshots
  and embedding compatibility. **289 adapter/server tests**: scope/value/operand completion,
  incomplete calls/constructors, written/header formals, partial analysis, presentation, semantic
  snapshots and asynchronous cursor request lifecycle. All have zero failures/errors/skips.
- **1 packaged-stdio test** verifies documentation, Value kind, sort text and candidate-specific
  active/default parameter metadata over the real server connection. Zero failures/errors/skips.
- The first corrected combined command still encountered Java test variable shadowing and one
  TypeScript numeric narrowing error. The adapter/server and packaged-stdio tasks passed in that
  command. Java tests and TypeScript compilation then passed after those test/driver-only fixes;
  compiler/server production source was unchanged between these last two commands.
- Both native drivers compile. Root/lang Spotless checks and changed TypeScript ESLint pass.
  Test tasks used `--rerun --no-build-cache`; counts come from the JUnit XML results.
- VS Code `run-yOWnUW` passes X97 (24 variants) and X108 (13 variants), with 125 other IDs not
  selected. Catalog SHA-256: `04d45b017030ee8b32016f3cea6e19370ed44d38f617df21cede25574e875ad9`.
  The catalog remains 127 IDs; its 122 X-series rows match the manual plan in order.
- IntelliJ `run-4259780076879058977` passes START, X97 and X108 using the same catalog hash,
  with 125 other IDs not selected, zero IDE errors and one passing JUnit test (zero failures,
  errors or skips). It runs IntelliJ 2026.2.3 with Ultimate disabled and LSP4IJ 0.21.0.
  Neither editor receipt is a full-current-catalog acceptance claim.

This four-step batch is complete. L64 stays open for additional declaration/callable contexts,
keyword/snippet/import edits, arbitrary enclosing-instance enumeration, more literal forms and
broader damaged-bound recovery. Syntax-labelled recursive names do not authorize semantic member
lookup. L62/L63/L65–L67 and the explicit earlier-checkpoint follow-ups retain their own scopes.

### Earlier checkpoint follow-ups still open

These historical records must not disappear behind the newer L62–L82 feature scopes:

- [x] **L12 semantic-token overlap acceptance:** the generated anonymous class declaration overlap
  is fixed; a backend regression and strengthened shared X76 now cover it. Selected X76 passes
  in both VS Code and IntelliJ. Track classification breadth separately under L65.
- [x] **L16/L27 IntelliJ configuration UI acceptance:** a project source-graph page is implemented,
  sharing LSP4IJ storage with configuration requests and rename history. Reset/Apply, validation and
  native graph Undo/Redo pass in X118. Global JSON remains a fallback.
- [x] **L60 150-case native checkpoint:** `run-1843149430446112481` passes all 150 cases plus
  startup in one uninterrupted IntelliJ run, with zero IDE errors and successful shutdown. This
  supersedes the historical 113-case pass and resumed 128-case coverage. Newly added X146/X147
  need their own receipts; the latest full VS Code catalog remains 149/150 because of X130.
  The cross-editor gate and future expanded-catalog checkpoints belong to L82.

Earlier feature milestones remain complete only within their recorded scope. Their language and
scale extensions are tracked under L62–L67; they are not evidence of universally complete support.

### Implement the missing protocol operations

These are absent operations or optional extensions to working base features. They are not
evidence that existing push diagnostics, full tokens or eagerly populated responses are broken.

#### Investigation status and next decisions (updated 2026-09-30)

Every missing feature in the capability matrix has an open task below or in L63. The inventory
audit established the current implementation boundary; it is **not** a completed design for
every operation. An unchecked task must not be reported as investigated, implemented or tested
merely because its protocol method is known. Implementation should start with the specific
investigation below and record its conclusion beside the task. L82 supplies the common
backend/protocol/editor, cancellation, stale-result and performance acceptance requirements.

| Scope | Investigation already recorded | Next investigation before implementation |
|---|---|---|
| L63 semantic fixes/refactorings | Import/member/declaration repairs, bounded statement/expression extraction, local/private-member inline and private-member safe delete pass complete-graph binding/dispatch proof and selected acceptance in both editors. | [Bounded closure](#l63-bounded-closure-and-acceptance-2026-10-04) records supported/refused forms. Arbitrary control flow, argument substitution and unknown external consumers require a separate expansion; they are not advertised as supported. |
| L68 pull diagnostics | Negotiated pull/push, result IDs, related/closed documents and invalidation pass backend, stdio and selected acceptance in both editors. PLAT1's source-location crash is fixed. | Retain broader workload coverage rather than treating the selected fixtures as universal proof. |
| L69 token range/delta | Negotiated range/delta and bounded result history pass backend/protocol and X126 in both hosts. | Measure representative workspace payload/cache costs under L82. |
| L70 lazy resolve | All six resolve endpoints have detached revision guards; backend and X105/X122/X127/X131 checks pass. IntelliJ has a selected-action bridge preserving normal Undo/Redo. | Preserve eager fallback for clients without the relevant capabilities; broaden stale-application acceptance under L80/L82. |
| L71 file operations | Six negotiated pre/post hooks and compiler-proven file/package/container operations; backend and selected X128/X130 checks pass in both editors. | Bounded same-module type moves now rewrite package qualifications; X161 Move/Undo/Redo passes in both editors. Explicit graph relocation now uses the host proposal/persistence path (X162/X163). Retain the VS Code file-operation refusal limitation. |
| L72 save/sync/formatting | Negotiated save hooks, opt-in incremental patches and multiple-range formatting pass backend and selected X132/X137–X139 checks; Full remains default. | Broaden workspace/save ownership coverage; IntelliJ uses native save formatting because LSP4IJ lacks `willSaveWaitUntil`. Save edits remain version guarded and independent of compilation. |
| L73 server commands | Run lenses invoke client commands; negotiated legacy code actions have a bounded one-use resolve/apply command. | Broader server commands and embedded Run remain separate scopes; define typed commands, edit failure handling and cancellation. Embedded execution depends on the accepted R2–R5 service design, not another command-line assembly path. |
| L74 monikers | Implemented and validated: normalized artifact identities, source/binary equality, overload/generic calls, replacement, private/local visibility and shared X252/X253. | Bounded to current successful semantic snapshots. Parsed-only library document enrichment remains with L75; no LSIF exporter or native moniker browser is claimed. |
| L75 document content | Matching indexed sources open as read-only files. | Establish client support and URI/revision ownership for virtual or archived sources; define refresh and stale-content behavior. |
| L76 inline completion | No inline provider exists. | Decide useful compiler/snippet use cases and client support first; no generative service is implied. Implement and test the agreed scope or record an explicit exclusion. |
| L77 colors | No color-value provider exists. | Decide which XTC values have unambiguous color meaning and reversible source edits. Implement that scope or record why it is inapplicable. |
| L78 notebooks | Current ownership is file/module based; there are no notebook sessions. | Decide whether XTC notebooks are a product requirement, then define cell/module identity and execution order before synchronization. Record an explicit exclusion if out of scope. |
| L79 debug inline values | Compiler inlay hints are not runtime values; DAP remains a stub. | Depend on R6–R7 real sessions, stack/source mapping and stop-state ownership; define evaluation safety before exposing values. |
| L80 negotiation | Current producer/provider audit is complete, including link-tooltip, per-signature parameter and pull-related-info gates. Generic IntelliJ text-edit guarding and shared X144 retain their passing receipts. | Generic resource/snippet/confirmation edits remain refused; native Rename/Move owns resource edits. New producers must extend negotiation and tests; host/release acceptance remains under L81/L82. |
| L81 progress/trace/refresh | Partial batches and actual Tree-sitter scan progress join owned progress/cancellation, refresh and trace. X143 and X145 pass both hosts, including cancellation and restart during pending work. | X146/X147 now automate bounded P3/P4 refresh and late reports; acceptance follows this batch. IntelliJ visible Cancel passes; VS Code button selection and broader multi-window/settings interaction remain manual. |
| L83 initializer facts | Detached successful initializer facts are implemented with no new AST fields; backend regressions and shared X142 are added. | Backend and shared X142 pass; preserve the explicit eight-component record-pattern migration note. |

L76–L79 require explicit scope decisions; their presence in this inventory does not make notebooks,
color editing or every optional protocol extension mandatory for the compiler-only release.
An exclusion must state its reason and keep the corresponding capability unadvertised. L62 and
L64–L67 separately track gaps inside already implemented feature families; R1–R8 track execution
and debugging. Neither feature counts nor a selected passing playbook establish total completeness.

- [x] **L68 — Pull diagnostics.** Implemented `textDocument/diagnostic`, `workspace/diagnostic`
  and `workspace/diagnostic/refresh`, result IDs/unchanged reports, related documents,
  cancellation and closed-file reporting. Negotiation preserves push for other clients. The
  combined batch and selected X123 pass in both editors, including the nested-report decoding
  correction. PLAT1's real-platform source-location crash is fixed; the later native demo found
  and corrected a standalone closed-member pull gap, recorded above.
- [x] **L69 — Semantic token range/delta.** Negotiated
  range/delta handlers use detached, connection-local result IDs, a bounded history (128 reports /
  one million integers), full fallback after eviction/foreign IDs/close/restart, and negotiated
  refresh on semantic changes. Range reports preserve UTF-16 coordinates. Unit, compiler-service,
  packaged transport and shared X126 pass; representative workload measurements remain in L82.
- [x] **L70 — Lazy resolve operations.**
  Completion/action/lens/link/inlay/workspace-symbol resolve now use bounded revision-guarded
  handles and negotiated eager fallbacks. IntelliJ's selected-action bridge restores lazy action
  edits with document epochs and normal Undo/Redo. Backend/protocol and selected
  X105/X122/X127/X131 acceptance pass in both editors; see the 2026-09-30 follow-up receipt.
- [x] **L71 — File-operation participation (within the documented safety boundaries).**
  All six hooks negotiate independently. Native IntelliJ Rename and batch Move preflight before
  mutation, then apply references and VFS moves in one undo command. Backend/protocol and selected
  X118/X128/X130 acceptance pass in both editors, including native dispatch and project Undo fixes.
  Bounded cross-package qualification rewriting (X161) and explicit source-graph relocation
  (X162/X163) are implemented; ordinary LSP null replies cannot veto moves.
- [x] **L72 — Save hooks, incremental sync and multiple-range formatting.**
  Negotiated `willSave`/`willSaveWaitUntil` and `textDocument/rangesFormatting` are implemented.
  Initialization options `xtcDocumentSync: {incremental: true, formatOnSave: true}` opt into those
  behaviors independently; both flags default to false. Full synchronization remains the shipping
  contract, and malformed incremental batches cannot replace content or advance its version.
  Sequential patches use UTF-16 and CRLF/bare-CR aware positions. Save formatting uses the existing
  formatter and editor configuration without waiting for compiler analysis or applying edits itself.
  Multiple ranges use one snapshot, deduplicate identical expanded edits and refuse conflicts.
  Backend and packaged-stdio tests pass; shared X132 passes in both editors for multi-range requests
  and negotiated default save hooks. Editor settings for the opt-in options and a native multi-range
  UI are not introduced: hosts can already invoke standard formatting/save preferences.
- [ ] **L73 — Server commands and edit application.** Implement an explicit
  `workspace/executeCommand` registry if server-run actions are required, with negotiated
  `workspace/applyEdit` and failure handling. Current module Run lenses use a client command;
  the only negotiated server command resolves/applies a one-use legacy code action. Broader commands remain open. Do not conflate running XTC with debugging.
  Embedded Run commands must route to the shared build/execution service defined by R2–R5,
  rather than assembling another CLI command or duplicating compilation in a protocol handler.
- [x] **L74 — Cross-project symbol identities.** `textDocument/moniker` supplies normalized
  artifact-based import/export/local identities with scheme-level uniqueness. Recompilation,
  checkout relocation, overload/generic calls, distinct same-name artifacts, source/binary parity,
  replacement, visibility and stale/canceled responses pass regression tests; X31/X252/X253 pass
  selected acceptance in both editors. Snapshot-local IDs remain internal. Parsed-only library
  document enrichment remains with L75; see the [L74 contract and receipt](#l74-artifact-identities-2026-10-05).
- [ ] **L75 — Read-only document content.** Add LSP 3.18 `workspace/textDocumentContent` and
  its refresh request for clients supporting server-provided library/virtual documents.
  Existing matching XDK sources use read-only file views. Define URI/revision ownership and
  invalidation; do not make binary-backed targets editable. Add current artifact-backed monikers
  for matching library source views, which currently have parsed structure but no semantic snapshot.

### Additional LSP features with no implementation

These need explicit language/product scope as well as code. Keep them open until implemented
and tested, or record a deliberate exclusion from the full XTC editor target.

- [ ] **L76 — Inline completion.** Implement `textDocument/inlineCompletion` for justified
  compiler/snippet suggestions and trigger/selection behavior. Ordinary completion is separate;
  this does not imply adding a generative service.
- [ ] **L77 — Color support.** Define supported XTC color values, then implement
  `textDocument/documentColor` and `textDocument/colorPresentation`, including exact round-trip
  edits. No color provider is currently advertised.
- [ ] **L78 — Notebook documents.** Define XTC cell/module semantics and implement
  `notebookDocument/didOpen`, `didChange`, `didSave`, `didClose` and notebook synchronization
  capabilities. Current source trees/file overlays do not constitute notebook support.
- [ ] **L79 — Debug inline values.** Define a debugger integration, then implement
  `textDocument/inlineValue` and `workspace/inlineValue/refresh`. Compiler type inlay hints do
  not provide runtime values. DAP breakpoints, stepping and evaluation remain a separate project.
  The accepted R6–R7 runtime/DAP bridge is the dependency for those values.

### Protocol correctness and the completion gate

- [x] **L80 — Initialization and capability negotiation.** The 2026-10-01 audit covers all
  current method/response producers, adapter provider gates, workspace roots/UTF-16, markup,
  symbols, diagnostic metadata, action kinds/forms, resolve fields, edits, watcher failures and
  lifecycle cleanup. It fixes missing link-tooltip, per-signature parameter and pull-related-info
  gates, and limits the rename-proposal advertisement to compiler mode. See the
  [producer inventory](errs-audit.md#l80-final-capability-contract-audit-2026-10-01) and
  [validation receipt](#l80-final-capability-contract-audit-2026-10-01). Generic IntelliJ
  resource/snippet/confirmation edits remain intentionally refused; native Rename/Move owns
  its supported resource operations. Future producers must extend negotiation and its tests.
- [ ] **L81 — Progress, refresh, tracing and transport lifecycle.** Implementation is in place;
  broader native acceptance remains explicit below. The final audit adds late progress-creation
  retirement, all-five-provider refresh regressions, malformed-request recovery and independent
  connection ownership. Progress identifies the source/workspace and updates from compiler queue
  metadata. See the [L81 checkpoint](#l81-progress-refresh-and-transport-checkpoint-2026-10-01)
  and [upstream register](errs-upstream-issues.md) for remaining acceptance and UP15's error-code gap.
  - [x] Negotiated creation/cancel, partial results, trace levels and connection/request ownership.
  - [x] Shutdown/exit, initialization retry, unknown/malformed requests and two real server connections.
  - [x] All five refresh providers: negotiation, coalescing, refusal, pending replies and close.
  - [x] Audit current client-request producers; no additional showDocument/message-choice/folder
    request is needed for the currently implemented features.
  - [x] IntelliJ visible Cancel button, continued hover, pending restart and old-process exit.
  - [ ] VS Code visible Cancel button; its real SDK cancellation callback is covered separately.
  - [x] Overlapping project close/reopen and multiple native windows: IntelliJ uses two frames in
    one IDE; VS Code uses two normal installed-extension instances and real hot-exit restoration.
    See the native lifetime receipt below. Shared-Electron-process windows remain separate coverage.
- [ ] **L82 — Completion evidence and API closure.** For every applicable task, require a
  meaningful backend regression, advertised-capability/protocol test and shared editor scenario
  where observable. Cover supported, rejected, canceled and stale requests. Re-run the combined
  compiler/LSP/stdio suites, retention/peak-memory workload and VS Code playbook after the code
  batch; run selected native cases first and a full implemented IntelliJ checkpoint before
  submission. Update this checklist, the capability matrix, playbook, AST/API ownership record
  and commit extraction map together. Validate each later extracted PR independently.
  Establish explicit response-time and memory targets using representative project sizes, and
  include prolonged editing/restart/process-leak workloads on supported platforms. Record
  packaging, source attachment and failure-recovery acceptance in both clients.
  - [ ] Investigate IntelliJ bulk replacement of heavily decorated large files: the 20,000-method
    X145 attempt recorded a 21.3-second EDT freeze in `RangeMarkerTree.documentChanged` /
    `IntervalTreeImpl.maxEndOf` during `DocumentImpl.setText`. Preserve the failed receipt below;
    a passing bounded progress test does not establish large-file responsiveness. The focused
    [L82 investigation](#l82-large-file-intellij-freeze-investigation-2026-10-01) reproduces the
    platform cost without an LSP client (UP17). Diagnosis is complete for the interval-tree
    bottleneck; its repair and decorated-editor acceptance remain open.

- [x] **L83 — Semantic facts for constant-folded property initializers.** Successful temporary
  initializer probes now export detached constant targets, source spans, types and invocation
  provenance before disposal. Failed probes publish nothing; no clone AST or new mutable AST field
  is retained. Backend tests cover hover, definition, references, exact semantic token classification,
  rename and rejected/nonconstant controls. Shared X142 passes in both editors, including native
  rename/Undo. X140 remains the independent UTF-16 method-body check. The additive accessor and
  retained constructors do not preserve old Java record-pattern arity: consumers must include the
  eighth `initializerBindings` component under the explicit unreleased-API migration policy.

L80/L81 follow-up status: negotiated action/command forms, completion kinds, file-operation formats,
partial batches and actual asynchronous scan progress pass their regressions. Generic IntelliJ server
text edits have an ownership guard; X144 and X145 progress/cancel/restart acceptance pass both clients.
The current batch is validated: IntelliJ passes all 150 cases plus startup in one run; VS Code passes
149/150, with X130's host Explorer repaint exception still failing the test. L80's final producer
audit is now complete; L81 retains broader manual acceptance scope. L82 retains that host failure,
representative-workspace
performance targets, peak-memory and prolonged lifecycle gates. Their unchecked headings do not
mean the implemented protocol paths above are absent.

### Editor configuration and feature controls (UI1–UI7)

This is part of LSP product completeness, alongside L67/L72/L80–L82. The broader checklist remains
open; the [editor settings checkpoint](#editor-settings-implementation-batch-ui1ui7-2026-09-30)
records the implemented subset and its acceptance separately. Audited 2026-09-30 against the VS Code
manifest/client and IntelliJ `CompilerProjectConfigurable`, `CompilerSettings`, code style and
connection provider. Use **Ecstasy** in visible labels; preserve stable `xtc.*` setting/command IDs.

VS Code supports graphical extension settings through `contributes.configuration`, plus JSON
editing and user/workspace/folder scopes. Use its Settings editor first, and commands with pickers
for graph/import workflows; a custom webview is justified only if a graph editor actually needs
one. IntelliJ should extend its Community-compatible Ecstasy settings pages, with application
and project scope. See [VS Code configuration contributions](https://code.visualstudio.com/api/references/contribution-points#contributes.configuration)
and [IntelliJ settings](https://plugins.jetbrains.com/docs/intellij/settings-guide.html).

| Area | Current implementation | Planned user control and ownership |
| --- | --- | --- |
| Backend and feature availability | Adapter selected by packaged build/runtime configuration; Tree-sitter ships by default. | Show active adapter/version and a feature matrix with unavailable reasons. Assess a restart-required backend selector only for packages containing the required implementations; never silently fall back or imply an unavailable backend can be enabled. |
| Source graph, resources and discovery | VS Code `xtc.compiler.sourceModules` plus configure/show/refresh/prepare commands; IntelliJ Ecstasy Compiler table, discovery switch and effective-input display. | Improve path pickers and ordered resource lists; preserve omitted/null versus empty semantics. Show origin (Gradle model, discovery, explicit override), dependencies, validation errors and reset-to-model. Work without Gradle and support multiple roots without ambiguous relative paths. |
| Build import and generated inputs | Both plugins have explicit Gradle model refresh/preparation. | Expose import state, last refresh, pending generated inputs, cancellation and actionable failure. Use evaluated Gradle inputs; never infer paths by reading build-script text or run builds on every keystroke. |
| Libraries, XDK and external sources | Bundled XDK is implicit/read-only; build models provide inputs; VS Code `xtc.sourceRoots` is machine-overridable. | Show effective read-only libraries and source attachments; add ordered host library/source overrides only where the backend has a supported contract. Distinguish source indexing from compiler module dependencies. Keep the bundled XDK usable without any external installation. |
| JVM, startup and lifecycle | VS Code `xtc.java.home`; IntelliJ uses JBR. Restart/status/log actions exist. | Display the effective runtime, server PID/version and restart reason. Add advanced JVM options only with validation and a clear restart boundary. Machine paths must not leak into shared project settings; preserve one server per project/connection owner. |
| Incremental text synchronization | L72 accepts `initializationOptions.xtcDocumentSync.incremental`; both plugins now expose a Full/Incremental preference with restart. | This changes text transport, not incremental compilation. Full remains the default; X137 checks restarts and unsaved buffers. |
| Formatting and save behavior | Whole/range/on-type formatting; VS Code five `xtc.formatting.*` fields; IntelliJ Ecstasy Code Style. L72 save formatting is initialization-only. | Prefer each editor's existing format-on-save/on-type and language-specific formatting controls. Define a single owner so editor formatting and `willSaveWaitUntil` never format twice. Audit which existing fields actually affect output: a visible max-line-width value must not promise line wrapping the formatter does not implement. Server-side save formatting stays optional. |
| Presentation features | VS Code `xtc.inlayHints.enabled`; IDE/LSP4IJ controls and an environment/system-property semantic-token switch. | Use native completion, parameter info, inlay, semantic highlighting, lens, hover, folding and navigation preferences where available. Add Ecstasy-specific options only for missing useful controls, with supported-adapter gating and live refresh. Do not add a toggle for every LSP method. |
| Diagnostics and analysis | Compiler diagnostics, push/pull negotiation and automatic reanalysis work; editor Problems filtering is available. | Prefer native severity/filter controls; expose analysis/status and refresh/rebuild commands. Any future debounce, excludes or on-save analysis option must define stale-diagnostic behavior and graph coverage. No switch may bypass rename/type proof or silently suppress internal failures. |
| Refactoring, imports and file operations | Guarded rename/move/action paths and graph settings persistence exist. | Explain refusal reasons and relevant configuration; respect native preview/confirmation/Undo. Source-graph proposals need explicit persisted edits. Do not make unsafe operations available through a preference. |
| Tracing, timing and notifications | VS Code `xtc.trace.server`; LSP4IJ logs; compile/API/queue timing and transient startup notifications. | Add discoverable log/queue/status views, trace level and bounded log retention/export, including human-readable queued jobs. Keep source content out of routine logs. Prefer IDE notification controls; advanced startup-notification preference only if needed. |
| Run/debug and future providers | Run configuration scaffolding exists; persistent runtime/DAP and several LSP providers remain separate work. | R5 owns target/arguments/working directory/environment/Stop/Rerun UI; R6–R8 own debugger controls. Notebook, color and inline completion settings follow implemented capabilities, not placeholders advertised as working. |

- [ ] **UI1 — Settings contract and inventory.** Map every manifest option, settings page, startup
  property/environment option and client capability to its consumer, default, owner, scope,
  availability and live/restart behavior. Identify inert/duplicate fields and migration needs.
  Treat negotiated protocol properties (pull/push, lazy resolve, token delta) as automatic
  compatibility decisions unless a diagnostic override has a concrete use.
- [ ] **UI2 — Scope and persistence.** Define shared semantics for application/user defaults,
  project/workspace overrides and per-folder/resource values. Retain existing source-graph
  precedence. Publish immutable validated settings, keep the last valid configuration on errors,
  and avoid overwriting concurrent rename/Undo changes. Add multi-root, missing-path, relative-URI,
  remote filesystem and restricted/untrusted-workspace cases before offering those paths.
- [ ] **UI3 — IntelliJ Community UI.** Extend existing Compiler and Code Style settings; add a
  small language-service section for relevant advanced settings and status. Use path choosers,
  actionable validation, Apply/Reset/Cancel and inheritance indicators. Reuse LSP4IJ controls where
  they already work. No Ultimate-only APIs, duplicate settings owner or second compiler process.
- [ ] **UI4 — VS Code settings and commands.** Group settings in the native Settings UI with
  descriptions, constraints and appropriate scopes. Use language-overridable formatting controls
  and machine-local runtime paths. Improve existing graph/path commands with pickers and effective
  settings output; keep JSON editing as the advanced escape hatch. Audit actual client behavior
  before claiming per-folder support from schema scope alone.
- [ ] **UI5 — Apply and restart behavior.** Wire L72 transport options into both clients, live
  presentation/formatting changes into refresh, and compiler-input changes into graph replacement.
  Restart once when a connection-time option changes, preserving buffers and pending user edits;
  ensure save hooks cannot double-format or wait for compilation. Separate incremental transport
  from the future compiler-incrementality workstream in labels and help.
- [ ] **UI6 — Effective configuration and support view.** Show configured versus effective values,
  source of each value, active adapter, read-only XDK, capability availability, current compilation
  and queue. Link failures to the relevant setting/log. No modal UI for normal background activity.
- [ ] **UI7 — Acceptance and documentation.** Add shared scenarios for change/apply/cancel/reset,
  live updates, restart-required settings, persistence across restart, both adapters, multi-root
  precedence, invalid configuration retention and no duplicate save formatting. Cover Community
  IntelliJ and VS Code Settings/commands, not merely direct protocol injection. Update the feature
  matrix and manual playbook with exact locations; mark backend-only options explicitly.

Sequence: UI1/UI2 first, then UI3/UI4 as separate client commits, UI5/UI6, and shared UI7
acceptance. UI5's incremental transport depends on L72 validation; Run/debug controls depend on
R5–R8. This does not add every prospective preference to the current four-code-commit batch.

### Embedded Run and debugging track (R1–R8)

The [execution plan](../lang/doc/plans/plan-embedded-execution.md) records the source audit,
recommended process model, compiler API implications and full acceptance cases. Its task list
is part of overall professional XTC tooling completeness; these tasks are not silently added
to the compiler-only PR:

- [ ] **R1:** controlled repeat-run/runtime API baseline, including native-pool growth.
- [ ] **R2:** immutable build results and compiler-proven runnable targets, including unsaved sources.
- [ ] **R3:** asynchronous embedding/lib_runner sessions, arguments, console/input and reliable stop.
- [ ] **R4:** supervised persistent execution worker, bounded retention and crash/close recovery.
- [ ] **R5:** shared IntelliJ Community and VS Code Run/Stop/Rerun behavior and scenarios.
- [ ] **R6:** DAP lifecycle backed by real run sessions, with honest advertised capabilities.
- [ ] **R7:** session-scoped runtime debugging, source maps, breakpoints, stacks, variables and stepping.
- [ ] **R8:** runtime/client parity, repeated-run/debug soak and independent release validation.

Implementation order is R1/R2, then R3, R4/R5, R6, R7 and R8. Compiler-only operations must
continue to avoid runtime startup. Existing `EmbeddingSupport` connector reuse and lib_runner
child containers are the foundation; existing IDE shell commands and the DAP stub do not satisfy
this track. In particular, the stub's verified breakpoint response is not real breakpoint support.

Current native automation inventory: **128 shared cases with driver assertions**, plus startup.
The [native demo continuation](#native-intellij-demo-continuation-2026-09-29) records the current
execution and corrections; it does not claim an uninterrupted full-current-catalog pass.
The previously missing 50 case bodies and X20/X81/X82 assertions are now written, compiled and
included in the clean 113-case native checkpoint `run-6034631232732848040`. Completion still requires
explicit evidence per feature; neither 24 capability flags nor a selected passing playbook
is a percentage of total LSP completeness.

### IntelliJ parity backlog (L60)

All 50 scenarios below now have IntelliJ test bodies consuming the same shared values as VS Code.
The checkboxes track **validated completion** and stay open until each case has a passing receipt.
The new driver uses native actions for navigation, hover, hierarchy, completion and accepted
renames; raw protocol assertions use the installed LSP4IJ connection for metadata, stale handles,
negative answers and cancellation. Diagnostics for nonexistent/deleted files are verified in the
client's verbose trace because LSP4IJ drops publications without a virtual file. That proves
protocol delivery, not a Problems row for a nonexistent file.

L60 checkpoint validation: **all its 113 scenarios pass together** in `run-6034631232732848040`, including
all 50 previously missing cases, X20/X81/X82 and X93–X98. The IDE-error gate is clean, and the
JUnit XML reports no failures, errors or skips. Earlier failures and their corrections are
retained below. Each run writes live `progress.jsonl` and final `results.json`.

| Native receipt | Selection | Result |
|---|---|---|
| `run-7413518377273090944` | X23/X31/X32 calibration | All three plus startup passed |
| `run-10871344394554883413` | All 50 missing cases | 24 passed, 26 failed; startup passed |
| `run-4538794847979158115` | 26 failures after shared harness fixes | 13 passed, 13 failed; cumulative 37/50 |
| `run-4120946877197382622` | Remaining 13 plus X20/X81/X82 | 13 passed, 3 failed; cumulative 47/50 plus the three partials |
| `run-16001945336035417449` | X29/X38/X57 | X38 passed; X29 failed; X57 confirmed stale edit application; cumulative 48/50 |
| `run-9071736322591460056` | X29/X93–X98 | X93 stopped at a native focus timeout; no new passing evidence |
| `run-4543155797199587357` | X29/X53/X54/X57/X60 | Four rename cases passed with the guarded native handler; X29 lost popup focus; cumulative 49/50 |
| `run-11234394973446044506` | X29/X57 | Stronger X57 passed, including retained-result rejection after close/reopen; X29 lost popup focus |
| `run-8500347901939309793` | X29 | Focus diagnostics confirmed no active/focused IntelliJ window; assertion remains open |
| `run-18411873168955014239` | X29/X93–X98 | X93 passed; X94 exposed the nested five-second diagnostic wait |
| `run-8792341337801244441` | X29/X94–X98 | X94/X95/X96 passed; X97 stopped on a diagnostic-read timeout |
| `run-1044769987103417109` | X29/X98 | X98 reached completion but its cleanup Escape action was disabled |
| `run-12954008312871336066` | X29/X97/X98 | X97 lost native popup focus; no new pass |
| `run-16615690562699412632` | X29 | Passed, including current metadata in the native popup; cumulative 50/50 |
| `run-11352896574808044817` | X97/X98 | X97 isolated persistent diagnostic-read cancellation in the test driver; X98 not run |
| `run-14036438527565625821` | X97/X98 | Both passed, including exact accepted edits and diagnostic recovery |
| `run-1810526138998768503` | All 113 | 41 scenarios plus startup passed; X101's assertion expected the old rename-hint wording; 71 scenarios not reached |
| `run-13752339197998578922` | Remaining 72, including X101 | X101 and six more scenarios plus startup passed; X78 lost desktop focus; 64 scenarios not reached |
| `run-14394639979205435362` | Remaining 65 | Startup passed; X78 again lost desktop focus; 64 scenarios not reached |
| `run-8616039572581576888` | Remaining 65 | Startup passed; X78 exposed an incorrect bold-argument expectation for inactive overloads; 64 scenarios not reached |
| `run-1735653890304371063` | Remaining 65 | X78/X80/X81/X82 and startup passed; 7a.9 exposed its missing file fixture; 60 scenarios not reached |
| `run-10987416205556113042` | Remaining 61 | All 61 scenario assertions and startup passed, but the Gradle task failed on an IDE read-access violation in X41's harness inspection |
| `run-9044375786228422295` | All 113 after the X41 correction | 37 scenarios and startup passed; X108 lost desktop focus; 75 scenarios not reached |
| `run-14909426972982808602` | X41/X108 | Both and startup passed; zero IDE errors, zero JUnit failures/errors/skips; Gradle succeeded |
| `run-5653317395986057876` | All 113 with tracing and spaced fixtures | 61 scenarios and startup passed; Starter's ten-minute session limit terminated the IDE during X86; 51 scenarios not reached, no reported IDE errors. Whole-run limit corrected to 30 minutes |
| `run-13531221189698116807` | All 113 with the corrected session limit | 17 scenarios and startup passed; X17 lost desktop focus (`active=null`, `focused=null`); 95 scenarios not reached, no reported IDE errors |
| `run-793185410831092997` | X16/X17/X20/X34/X97/X103/X105 with faster waits and visible progress | All seven passed; zero IDE errors; combined scenario time 32.630 seconds |
| `run-9052387951904069468` | Dedicated focus recovery | Completion/signature interruption and completed-edit replay rejection passed; zero IDE errors |
| `run-8104497655710049205` | All 113 with window-only activation | 30 scenarios passed; X94 timed out restoring focus; 82 not reached; zero IDE errors |
| `run-11175036742022156624` / `run-4951684034310159112` | X94 / dedicated focus recovery with application activation | Both passed; zero IDE errors |
| `run-17709873479397394537` | All 113 with application activation | 112 passed; X30 exposed a restart-readiness assumption; zero IDE errors |
| `run-9607396768138373685` | All 113 with restart readiness corrected | 22 passed; X83 timed out waiting for editor focus; 90 not reached; zero IDE errors |
| `run-12431789842449790480` | Startup editing and untouched notification expiry | All five startup phases and balloon disappearance passed; zero IDE errors |
| `run-17503400761898076918` / `run-3387470687976244966` | X30/X83 / dedicated focus recovery through the IDE focus manager | Both selections passed; zero IDE errors; deprecated focus scheduling subsequently replaced with the recommended Swing event dispatch |
| `run-6034631232732848040` | All 113 with progress, faster waits, focus recovery and restart readiness | All 113 plus START passed in one session; zero IDE errors and zero JUnit failures/errors/skips; Gradle succeeded in 6m 40s. L60 complete |

X97's diagnostic-read failure was a harness transport problem. Driver 262's `RefProducer`
calls `toString()` when exporting highlighters; their descriptions evaluate lazy quick fixes.
LSP4IJ's cancelled quick-fix future kept throwing even though the editor displayed diagnostics.
Retrying the same object export could never repair it. The harness now installs a tiny test-only
reader that copies severity/message/offset under an IDE read action and returns JSON values.
It reads the actual installed annotations, without requesting diagnostics separately or forcing
quick-fix evaluation. The reader and its generated helper classes are packaged from integration
test output into the disposable test IDE only; the shipping plugin is unchanged. Popup cleanup
also dismisses whatever is present without requiring an enabled Escape action.
The first full checkpoint reached X101 with the correct visible rejection, but its assertion
still expected LSP4IJ's old unprefixed text. The guarded XTC rename handler prefixes the same
server reason with `Rename failed:`. The native assertion now checks that complete displayed
message; the shared protocol reason and the no-dialog/no-edit requirements are unchanged.
X101 passes with the corrected assertion. The next two runs stopped at X78 with both the
active and focused IDE windows null; screenshots show another application in the foreground.
The resumed runs completed those outstanding scenario assertions. They do not establish a
clean 113-case pass: the 61-case batch's IDE-error gate correctly rejected an unguarded PSI
read, and the subsequent full run lost desktop focus at X108. X41 and X108 then passed together
with no IDE errors. Those runs used the earlier fail-on-focus-loss policy. The harness now
restores focus without pointer input and only reopens unapplied inspections while their source
is unchanged; the dedicated regression and current full-run evidence are recorded above.

The resumed run found three more harness defects, fixed in `6d7e5b7e5`:

- Signature assertions now use `activeSignature`. LSP4IJ intentionally dims inactive overloads;
  the harness checks every row's text and the exact bold argument only for enabled rows. When
  no overload is selected, all rows remain enabled. Per-signature parameter overrides still apply.
- 7a.9 now creates `Broken.x` from its shared empty-module baseline and restores that baseline
  after checking bounded diagnostics and native Problems rows. It is not a manual-plan fixture.
- X41 reads PSI and obtains highlight support inside an IDE read action. Its scenario assertions
  previously passed, but IntelliJ correctly logged a threading violation and failed the run.

These changes affect only the integration driver. Root Spotless, Kotlin checks and the final
focused Gradle run pass. The feature assertions and IDE-error gate remain enforced separately.

Harness corrections cover URI comparison without changing round-tripped items, Gson numeric
hash differences, popup cleanup, independent viewport positioning, required write/undo contexts,
and transport of non-serializable protocol errors through Driver JMX. Diagnostic waits now
share their full timeout, and annotation reads inspect installed document/editor highlighters
without the daemon's cancellable collection pass. Completion cleanup closes transient popups
without requiring Escape to remain enabled after the lookup changes. Native navigation checks
actual editor destinations for every chooser entry: opening a closed target can legitimately
invalidate the previously completed request cache. X38 passes with that check.

Native runs also reproduced real client defects. LSP4IJ validates semantic caches only against
the requesting file's PSI stamp, so X24 retained an old completion type and X38/X67 retained old
member locations after another source changed. The client now invalidates completed semantic
results on fresh compiler diagnostic publications; X24, X38, X47 and X67 pass with the fix. This adds
no mutable state or compiler/AST hooks. The Ecstasy Parameter Info renderer preserves ambiguous
candidate labels (X20 passes) and honors per-signature active-parameter overrides. X29 now passes: the renderer reads the retriggered response
instead of the original popup object when its parameter metadata changes.

X57 reproduced stale application through LSP4IJ 0.21.0's workspace-edit applicator in a valid
write/undo context: the server's versioned response was correct, but the client ignored that
version. A native Ecstasy rename handler and immutable request snapshot now pass X53/X54/X57/X60.
The snapshot checks buffer stamps, open/close identities, paths and server identity inside the
write command before applying any returned edit. It uses the installed client connection and
Community platform UI; no second mutable version counter or compiler/AST hook is added.
This check is deliberately conservative: changing any open Ecstasy buffer can retire the
request. X57's additional retained-result close/reopen assertion passes as well.
Closed-file edits with null versions retain the protocol's existing limitations;
this is not a filesystem transaction or a guard for every LSP4IJ workspace-edit entry point.
The shared X31/X32 expectations were also stale: formatting, code actions and code lenses now
exist, and several former negative completion contexts are supported. Both drivers now test the
current capability boundary and invalid argument slots. VS Code `run-KfBbW1` passes X31/X32.
The preceding VS Code run exposed 37 static extension snippets at empty argument slots; X32
now separately asserts the compiler's empty response and the absence of non-snippet editor
proposals. This preserves the distinction between semantic candidates and IDE suggestions.

Extraction groups for this work (commit IDs will be added at validated checkpoints):

1. `262a7defe` — installed-client protocol/trace inspection, per-case workspaces and reusable native actions. Subsequent harness fixes will be recorded separately.
2. `dfb7c4ab1` — fifty parity cases, shared X31/X32 corrections, X81/X82 completion-kind checks and native harness corrections. Its X57 regression depends on the guarded rename in `eded39f0a`; the cache and Parameter Info fixes below are also needed for passing native assertions. Keep these together when extracting the IntelliJ parity PR, or split individual regressions with their production fix.
3. `5119c682f` and `594c49328` — Ecstasy Parameter Info rendering for missing metadata (X20), per-signature active slots, and current metadata after retrigger (X29). X29 passes in `run-16615690562699412632`; extract both fixes with the parity assertions.
4. `93a82ea8b` — client semantic-cache invalidation after dependency analysis (X24/X38/X47/X67).
5. `eded39f0a` — guarded native symbol rename after LSP4IJ stale application was reproduced; X53/X54/X57/X60 pass. Plugin unit tests: 26 tests, zero failures/errors/skips; root `spotlessCheck` passes. The shared native regression bodies are in the parity-case group.
6. `912abf3b8` — L55 dependency-closure and detached proof-fact retention fix, with all four 24-root outcomes. Teaching-workspace acceptance is completed by the later L55 batch below.
7. `8e976f868` and `eefc1b8e6` — independent process-lifecycle fixes. Extracted and independently tested as `cf54e2a19` on local branch `lagergren/fix-lsp-process-lifecycle`, based on master `ce3ab1d81`; see the [master extraction boundary](errs-lsp-process-lifecycle.md).
8. `73290ac14` and `6d7e5b7e5` — diagnostic-value probe, popup cleanup, guarded rename-hint assertion, active-overload rendering checks, bounded-error fixture and guarded PSI reads. Native harness-only follow-ups; keep with the parity driver, separate from production lifecycle fixes. The receipts above distinguish scenario assertions, IDE-error checks and interrupted full runs.

- [x] X3 — narrowed hover and declaration identity.
- [x] X5 — library navigation exclusions and parser-recovery outline.
- [x] X14 — UTF-16 completion edits and CRLF bytes.
- [x] X21 — closed-member references and workspace symbols.
- [x] X22 — cross-file generic type hierarchy.
- [x] X23 — unsaved root changes and sibling diagnostics.
- [x] X24 — completion against repeated unsaved root types.
- [x] X25 — unsaved nonexistent member open/close lifecycle.
- [x] X26 — discard/reopen restores disk semantics.
- [x] X27 — file watcher creation/deletion and diagnostic ownership.
- [x] X28 — rejection of obsolete type-hierarchy handles.
- [x] X29 — rapid edits and concurrent cursor requests.
- [x] X30 — cancellation, close/reopen and server restart.
- [x] X31 — initialized capabilities and formatting provider.
- [x] X32 — absent compiler completions distinguished from IDE suggestions.
- [x] X36 — exact nominal implementation targets.
- [x] X37 — overloaded method implementation identities/ranges.
- [x] X38 — cross-file semantic targets through moving/broken overlays.
- [x] X39 — incoming/outgoing call hierarchy and grouped sites.
- [x] X40 — lambda ownership and absent dynamic-call edges.
- [x] X41 — semantic-token kinds/modifiers and read/write highlights.
- [x] X42 — inferred-type/parameter inlay labels, kinds and positions.
- [x] X43 — obsolete call-hierarchy handles and recovery.
- [x] X44 — incoming calls from closed members/current overlays.
- [x] X47 — dependency diagnostic ownership and stale-target removal.
- [x] X48 — discarded dependency overlay restores the disk artifact.
- [x] X49 — dependency deletion/restoration notifications.
- [x] X50 — unsaved/saved dependency members and consumer invalidation.
- [x] X51 — rapid dependency changes preserve unrelated sessions.
- [x] X52 — transitive source changes and diagnostic ownership.
- [x] X53 — local rename includes captured uses and exact edits.
- [x] X54 — private-parameter rename includes named labels.
- [x] X55 — rename rejects capture of an untouched property.
- [x] X56 — unsupported rename targets produce no edits.
- [x] X57 — edit versions, stale client rejection and close/reopen.
- [x] X58 — broken-source recovery and invalid/conflicting rename rejection.
- [x] X59 — exact references in unopened configured consumers.
- [x] X60 — generic override rename across unsaved/versioned documents.
- [x] X61 — graph rename rejects changed overload binding.
- [x] X62 — bundled member signatures/hover and binary rename rejection.
- [x] X63 — current consumer overlays and incomplete-graph rejection.
- [x] X64 — generic property/accessor implementation identity.
- [x] X65 — distinct getter/setter/default-field implementation sets.
- [x] X66 — no invented abstract/delegated/annotated/binary targets.
- [x] X67 — closed accessor positions and parse-recovery navigation.
- [x] X68 — chained delegation to written method/getter bodies.
- [x] X69 — super definition, outgoing calls and rename rejection.
- [x] X72 — annotation accessors and explicit override composition.
- [x] CFG2 — rejected cyclic configuration preserves the valid graph.
- [x] CFG3 — unchanged settings preserve semantics/document versions.

The three previously partial assertion sets pass in `run-4120946877197382622`:

- [x] X20 — preserve the ambiguous signature label in the native Parameter Info display,
  while suppressing unsupported parameter/active-argument metadata. Record any upstream fix.
- [x] X81 — inspect Property completion-kind metadata in addition to candidates/type/edit.
- [x] X82 — inspect Property completion-kind metadata in addition to candidates/edit.

L60 is complete: all the above assertions are implemented and the complete 113-case native suite
passes together on the integrated branch in `run-6034631232732848040`. A client limitation must be
reported explicitly; it cannot silently remove an expected shared assertion.

## Large-graph proof memory checkpoint (L55)

Implementation commit: **912abf3b8**.

The independent 24-root regression reproduced `OutOfMemoryError` in the existing 512 MiB
Gradle test heap. Two separate retention paths were confirmed:

- Graph queries reopened every previously compiled root as a dependency. This deserialized
  unrelated modules repeatedly and also allowed undeclared imports to resolve according to
  traversal order. Proof repositories now include only the configured source dependency closure,
  while retaining host binary inputs and their transitive dependencies. The regression proves
  both rejection of an undeclared import and successful rename after declaring that dependency.
- Before/after proof facts stored raw compiler constants and dispatch chains, retaining every
  root's pool. Extraction now copies declaration locations and artifact identity keys while the
  worker owns the compiler objects. Binary keys are established by compiler constant equality
  against an artifact revision/table index; they are not display strings. Generated non-lambda
  method identities additionally preserve their compiler parent and artifact signature.
  Unknown identities remain incomparable across attempts, conservatively refusing the proof.

Public auto-import candidates are copied during extraction, before releasing compiler state.
Local rename proofs receive the matching dependency declarations and revisions as well. The
binding and dispatch comparisons still cover untouched names, overload selection and override
chains. This is Kotlin adapter work: no new embedding API, Java AST state or error-listener
change is needed. Keep the query, fact extraction, comparison and retention tests together when
extracting the L55 PR, after the graph query/rename slices.

The four 24-root outcomes pass under **536,870,912 bytes**: successful rename, rejected rename,
cancellation after the first edited-root attempt, and an injected compiler failure. Weak-reference
checks release respectively 144, 74, 75 and 75 compilation/pool/AST objects. Combined validation
passed: 1,219 server tests (three existing skips), 26 IntelliJ unit tests and root Spotless.
The local-rename dependency association regression found by the first full run is fixed.
The original small independent roots establish a repeatable memory regression. The later
teaching-workspace acceptance below also passes with only the target and with multiple unsaved
buffers open, including unchanged live diagnostics.

`XdkTeachingWorkspaceTest` now builds the actual native teaching workspace from the
manual and shared catalog. It exercises successful and colliding property renames with only
the unsaved target open and with four additional unsaved buffers, checks that proof queries do
not replace live diagnostics or write files, and checks collection of query-owned compiler
objects while the adapter stays alive. The current catalog has 25 roots: the original 24 plus
7a.9's initially empty `Broken` module. All four teaching-workspace regressions pass in the
combined run, including both buffer counts under **536,870,912 bytes**. Playbook runs were
deferred until all three implementation checkpoints were committed.

The real workspace additionally exposed two conservative refusal causes. A simple
`delegates Api(target)` name was never validated as an ordinary `NameExpression`; the compiler
stores its selected property in the existing composition contribution. Kotlin extraction now
copies that identity for successful compilations. Separately, dynamic `super()` bindings in an
unchanged independent module could veto a rename elsewhere. Proof facts now retain their source
module grouping: both attempts still compile every root, while binding/dispatch comparison covers
all edited modules and their transitive consumers. Dependency-closure repositories and unchanged
captured inputs prove the independence of excluded roots. A regression requires refusal when
such an unproven binding occurs in an affected consumer; no binding/dispatch check is dropped
inside that closure. These are adapter fixes, with no new AST fields or embedding methods.

## L62 parameter, composition and resource extension (2026-09-28)

All implementation stays on `lagergren/errs`. The three implementation checkpoints were committed
before running tests; validation corrections are recorded separately. This is an integrated-branch
receipt, not evidence that any extracted PR passes independently.

| Review unit | Implementation checkpoint | Required validation corrections from `98bfc2f71` |
|---|---|---|
| Public/override parameter slots and explicit constructor labels | `ddccde961` | Partial signatures omit unproven owner slots; constructor calls preserve caller ownership; call hierarchy accepts absent callers |
| Source composition families and generated-method provenance | `aaadbc134` | Public passive `MethodBody.getIntoMethodInfo`; contextual mixin-super provenance; preserve bundled binary identities; Kotlin API/inspection corrections |
| Qualified module names, implicit package directories and explicit graph proposals | `4aee81873` | Only source-owned packages acquire directory identities; binary-only packages retain artifact identities |
| Unified compilation, persistent execution and future DAP plan | `714a219e5` | Documentation only; R1–R8 stay separate from compiler delivery |

Extraction must group by these review units: `98bfc2f71` contains shared-file corrections and is
not a separate user feature. Constructor collector/publication changes belong with the embedding
provenance API, including compatibility and identity-snapshot tests. The Kotlin consumers/tests
depend on that final API. Composition uses the existing compiler TypeInfo objects only on the worker;
the getter visibility change belongs with its consumer and documented ownership. Resource proposals
depend on the complete graph replay and versioned edit checks. No separate semantic library or
runtime initialization is introduced.

Validation: `BindingIdentitySnapshotTest` and `EmbeddingApiCompatibilityTest` passed **10 tests**;
the nine selected LSP classes passed **76 tests**, with zero failures/errors/skips. Those classes
cover parameter/resource/project/local rename, server resource capability checks, rename requirements,
semantic snapshots (including partial source), call hierarchy, and completion/signatures. Test tasks
were forced to execute with `--rerun` and build caching disabled; the final run reused Gradle's
configuration cache. `spotlessCheck` and LSP ktlint checks passed. No new native editor run was made.

Remaining acceptance and scope:

- [x] Move the added manual rename cases into shared scenario data and both editor drivers:
  **X109–X117** cover public/constructor parameter slots, override slots, delegation, mixin
  methods/properties, Lazy properties, qualified modules and implicit packages. Both drivers
  compare every file, start with closed consumers and perform one Undo.
- [x] Run selected X109–X118 cases in both editors with closed consumers, resource operations,
  exact before/after contents and native history. The receipts below include every new variant;
  existing X103 is not used as substitute evidence.
- [x] Implement explicit graph persistence in both native Rename paths through `xtc/rename`.
  The protocol includes expected/replacement graphs and versioned source/resource edits. Clients
  validate settings, preserve unrelated options and restore the graph on Undo/Redo. VS Code
  supports workspace settings and unsaved configuration; Save All persists it. IntelliJ updates
  LSP4IJ's existing global configuration store and guards both history directions. Generic
  standard LSP clients continue to refuse graph changes. X118 and settings/server tests pass.
  VS Code follows its native refactoring save policy and refuses edited-file moves when
  `files.refactoring.autoSave` is disabled; it never overrides that user preference.
- [x] Audit primary-constructor properties, lambda parameters, escaped method values, dispatch
  routes and omitted consumers. `XdkRenameBoundaryTest` adds focused acceptance/refusal fixtures;
  all eight tests pass. The findings below retain explicit follow-ups.

### Shared rename and native settings validation

| Review unit | Implementation checkpoint | Extraction boundary |
| --- | --- | --- |
| Shared rename acceptance | `0a82d44fc` | Scenario data X109–X118, both drivers, fixture setup and manual rows; keep final action IDs, refactoring metadata and history assertions with these tests |
| Graph persistence | `3e3cbf405` | Proposal DTO/extension, adapter local/project routing, both native clients and settings/server tests; include final settings guards and VS Code resource-history refusal |
| Remaining scope audit | `a2f84748d` | Boundary regression class and audit documentation; include canonical temporary-path correction |

Validation follow-up **`2e39ee8bd`** belongs with these units, not a new feature PR. The native persistence
unit depends on the resource proposal/proof API from the preceding L62 implementation batch.
The adapter keeps returning standard edits to generic clients only when no graph replacement is
required. Extracted PRs must still build and pass independently.

Validation after the three implementation commits:

- Compiler/LSP: **54 tests** across parameter, resource, project and local rename, protocol, and
  boundary classes. The first run exposed only a macOS `/var` versus `/private/var` assertion;
  canonicalizing that fixture path and rerunning all eight boundary tests passed. No skips.
- IntelliJ settings: **4 tests**, no failures/errors/skips, including changed graphs, malformed
  intervening settings, duplicate graph entries, unrelated options and both history directions.
- IntelliJ native: `run-6245646041474423108` passed **X109–X118, X53 and X57**, plus startup,
  with zero reported IDE errors. The first run exposed a harness action-ID mistake (`Undo`
  instead of `$Undo`); correcting Undo/Redo action IDs made the full selected batch pass.
- VS Code: `run-3CeLiB` passed **X109–X117, X53, X57 and X103**. X118 exposed a settings-cache
  delay after saving; reading the live settings document fixed it. `run-7Xbo86` then passed
  **X118 and CFG1–CFG3**, including resource moves, closed consumers, graph persistence, Undo,
  Redo, second Undo and refusal when refactoring auto-save is disabled.

The resource-history investigation reproduced a VS Code limitation: applying a plain WorkspaceEdit
that edits and moves the same dirty source can restore its path without restoring its text.
Reordering moves ahead of text appeared to fix Undo but broke Redo, so that workaround was removed.
VS Code's native Rename uses `files.refactoring.autoSave` (default true); the harness now applies
refactoring metadata to match it. The adapter refuses edited-file moves if that setting is false.
It does not change the setting, save files from the provider or install a private undo stack.
Text-only renames and moves of unedited companions do not need this guard. Supporting the disabled
policy safely remains a follow-up, not passing coverage. The opened settings document also stays
authoritative immediately after save, before the asynchronous configuration cache catches up. The relevant editor behavior is in
[VS Code's Rename action](https://github.com/microsoft/vscode/blob/main/src/vs/editor/contrib/rename/browser/rename.ts)
and [bulk-edit save policy](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/contrib/bulkEdit/browser/bulkEditService.ts).

Final `spotlessCheck`, LSP/plugin ktlint and TypeScript lint pass. ESLint reports only five existing
unused-argument warnings in unrelated playbook files. No Gradle build logic was changed; the final
editor and plugin checks reused or successfully stored the configuration cache.

These are selected receipts against the integrated branch, not a new full 123-case run. The compiler
adapter remains opt-in; Tree-sitter remains the shipped default.

### Rename boundary audit

| Boundary | Current implementation | Remaining limit |
| --- | --- | --- |
| Primary-constructor property parameters | Existing header `Parameter.resolvedTarget` and persisted synthetic/shorthand constructor flags join property uses and generated constructor labels. Detached owner identity preserves call selection. | Explicit constructors keep their own written parameter contracts; generic/default, collision and cross-module cases are in the new validation batch. |
| Lambda parameters | Written register/source identity plus capture normalization supports local replay, including lambda values that escape. Generated lambda methods are not exported as parameter contracts. | Incomplete analysis and binding changes still refuse edits. |
| Escaped method values | Function-value calls cannot have named arguments: `InvocationExpression.testFunction` emits `COMPILER-141`. The previous requirement for callable-origin propagation was unnecessary for parameter spelling. Selected method-value references and direct named callers still undergo full replay. | Unknown runtime targets and reflective strings are not inferred from a function type. |
| Composition routes | Method dispatch accepts written Explicit/Default/Declared/Abstract, follows FromInto/Capped/Delegating, and refuses unknown, recursive or over-depth routes. Implicit, Union, SansCode, Field and Native are explicitly unsupported. Property chains accept Explicit/Declared/Default/FromInto/Delegating only, and every member must remain source-owned. | Exhaustive compiler fixtures for every generated route are still missing. Keep binary/native contracts read-only and do not turn generated forwarding bodies into source identities. |
| External consumers | `sourceModules` is the explicit consumer manifest and accepts absolute file roots outside workspace folders. `xtc/rename.scope` reports configured/discovered boundary, dependency-ordered modules, captured source URIs and input revision. | Omitted consumers are unknown and remain untouched, even beside a configured root. Hosts must enumerate every intended consumer. This is not whole-program rename or automatic external repository discovery. |

Remaining rename work:

- [ ] Add fixtures for each unsupported composition route before enabling source-owned routes;
  keep native/binary contracts read-only.
- [ ] Support VS Code edited-file resource history with refactoring auto-save disabled, or retain
  the documented pre-edit refusal (the current policy).
- [x] Add saved multi-root workspace, folder-override refusal and in-flight settings-change acceptance: September 29 X118/CFG1–CFG3 receipts below. Intervening configuration edits during native Undo/Redo remain separate history stress coverage.
- [ ] Define optional host discovery/indexing of external repositories if automatic enumeration
  beyond explicit sourceModules is required. The configured boundary itself is implemented.

This batch changes no Java AST node or compiler pipeline; it consumes existing compiler facts.

## Remaining rename implementation batch (2026-09-28)

Implement items 1–5 in separate commits before combined validation. New tests cover success,
refusal, unrelated binding preservation and configured boundaries; implementation checkmarks do
not claim a test receipt until the batch runs.

- [x] **1 — Configuration guards:** shared pure VS Code graph validation handles saved multi-root
  JSONC, rejects absent/global-only graphs, folder overrides and malformed/duplicate entries;
  captures workspace topology and rechecks settings after asynchronous edit conversion. Tests
  exercise history against intervening graph changes and preservation of unrelated preferences.
  IntelliJ adds matching cross-project root and dependency-edge history regressions.
- [x] **2 — Primary constructor properties:** join named arguments of compiler-marked synthetic
  shorthand constructors to their resolved properties; include written class-header properties in
  dispatch proof. A detached primary-constructor identity preserves selected calls across replay
  without inventing a declaration or adding AST state. Six regressions cover all three entry
  sites, closed consumers, generics/defaults and collisions/capture. Explicit constructor
  parameters retain their separate written contracts. The batch regression run passes.
- [x] **3 — Lambda parameters:** reuse original written register/source bindings, including nested
  capture normalization. Route lambda parameters through local source replay rather than inventing
  generated method slots. Seven regressions cover typed/inferred parameters, both entry sites,
  nested capture, sibling shadowing, return/pass escapes, name capture and standalone compilation.
  No AST state/API change; the batch regression run passes.
- [x] **4 — Escaped method values/composition:** remove the overly conservative escape guard:
  `InvocationExpression.testFunction` rejects named function-value arguments (`COMPILER-141`).
  Keep full binding/dispatch replay for selected methods and direct labels. Add stored/returned/
  passed/cross-module cases and unrelated same-named members, an independent compiler diagnostic regression, and
  delegated parameter families with differently named receiver parameters. Enumerate unsupported
  implicit/union/generated/native bodies explicitly; these are not source-owned contracts and
  remain refused. A runtime callable-origin propagation API is unnecessary for parameter spelling.
  The batch regression run passes; exhaustive compiler fixtures for every generated route remain separate.
- [x] **5 — External consumers:** reuse `sourceModules` as the explicit consumer manifest, including
  absolute roots outside workspace folders. Project proposals now carry an immutable scope receipt
  (configured/discovered boundary, dependency-ordered modules, captured source URIs and input
  revision); `xtc/rename` exposes the detached receipt. This is not a whole-world guarantee or a
  stale-edit authorization. Five regressions cover closed external roots, unsaved external buffers,
  missing consumers, disk changes during proof and discovery boundaries; server coverage checks
  the wire receipt. The batch regression run passes.

Shared X119–X121 exercise the new semantic cases through both editors, including Undo and
closed consumers, and are included in the combined validation selection. The catalog now has
126 cases. All five checkpoints preceded the combined validation below.

### Checkpoint and validation map

| Item | Implementation commit | Extraction notes |
| --- | --- | --- |
| 1 configuration guards | `9e319b21f` | Include the final single-folder settings distinction and all-folder override recheck in the validation correction. |
| 2 primary constructor properties | `fd9e047c2` | Existing compiler flags/source associations only; include formatting corrections and shared X119. |
| 3 lambda parameters | `330d18d08` | Include shared X120; no Java AST changes. |
| 4 method values and dispatch slots | `91f0d14fd` | Include parameter-family expansion/proof comparison corrections, corrected bound-method fixture and shared X121. |
| 5 external consumer scope | `86fb4c3a2` | Also contains shared X119–X121 wiring; allocate those hunks to the corresponding semantic PRs. |

The implementation commits deliberately precede testing. Validation correction `4937f4382` must
be included when extracting PRs; individual checkpoint commits are not claimed independently green.
Its Kotlin-only formatting hunks belong with their corresponding implementation slices. Its
parameter proof changes belong with item 4 and its VS Code guard changes belong with item 1.
This batch adds no Java compiler/AST API or mutable AST state.

Combined backend validation passes **94 LSP tests** and **six IntelliJ settings tests**, with
zero failures/errors/skips, plus **five pure VS Code settings tests**. Kotlin/TypeScript and both
editor drivers compile. Root Spotless and Kotlin lint pass; ESLint has zero errors and the five
pre-existing unused test-argument warnings.

VS Code X57/X119–X121 and CFG1–CFG3 passed in `run-c7xgn7`; X118 exposed that single-folder
workspace settings are also reported as folder values. The corrected scope guard passes X118
plus CFG1–CFG3 in `run-Z5w8sz`. X118 still tests the intentional pre-edit auto-save refusal.
The initial cross-module method-value fixture also exposed compiler ambiguity for an overloaded
bare bound method reference; the passing case uses a compiler-resolved non-overloaded member
and proves that another same-named member remains unchanged. Existing selected-overload direct
call tests remain green. The adapter does not invent a binding for compiler-ambiguous source.

IntelliJ X57/X118–X121 and CFG1–CFG3 plus START pass in
`run-14634841832018763989`: nine successes and zero IDE errors, on IntelliJ 2026.2.3 with
Ultimate disabled and LSP4IJ 0.21.0. Native saved multi-root/settings-race acceptance is still a follow-up; pure settings history regressions are not a native
workspace acceptance receipt. No full 126-case playbook run is claimed.

### Tooling warning audit

Before the Spotless/ktfmt migration below, the build already used Kotlin 2.4.20.
`dependencyInsight --configuration ktlint --dependency kotlin-compiler-embeddable` identified
ktlint 1.8.0's private Kotlin 2.2.21 as the source of the JVM Unsafe warnings. Each formatting/check worker could emit it, independently of Gradle's log
level. As of this audit, [ktlint 1.8.0 is the latest stable release](https://github.com/ktlint/ktlint/releases)
and the newer 2.0.0-ALPHA-4 changes coordinates, APIs and formatting behavior. A future upgrade
should migrate the supported tool/plugin together and review formatting churn, rather than force
an untested compiler version into ktlint's dependency graph. No warning suppression or dependency
override is introduced in the rename batch.

The September 29 follow-up reproduced the call directly with both cached compiler artifacts on
Corretto JDK 25. Their `org.jetbrains.kotlin.com.intellij.util.containers.Unsafe` class files have
identical SHA-256 hashes, and both emit the same warning when invoking `objectFieldOffset`.
Upgrading just the embedded compiler to 2.4.20 therefore does not remove this dependency on Unsafe.
The project compiler remains 2.4.20; 2.2.21 belonged to ktlint's separate dependency graph.
Compilation depended on ktlint checks, explaining the warning during native playbook builds.

[ktlint's CLI launcher already supplies the JDK workaround](https://github.com/ktlint/ktlint/pull/3040),
but [Gradle plugin 14.2.0 invokes the engine in its own worker](https://github.com/JLLeitschuh/ktlint-gradle/blob/v14.2.0/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/tasks/BaseKtLintCheckTask.kt),
bypassing that launcher. Its public worker settings expose heap size, not arbitrary JVM arguments.
A direct probe with `--sun-misc-unsafe-memory-access=allow` emits no warning. This is a scoped
compatibility workaround, not removal of the deprecated API; an upstream parser fix is still needed.
One considered alternative was to keep the stable ktlint rules and replace the Gradle integration
with declared-input/output CLI tasks that set the flag only for the formatter JVM. The user selected
Spotless with ktfmt instead; the migration below supersedes that custom-launcher proposal.
Global JVM suppression and forced compiler overrides are unnecessary.
The separate JNA `System.load` warning concerns native-access opt-in, not this Unsafe call.

## Next checkpoint: isolate partial AST syntax

The deeper access/ownership audit selects a bounded move: put the four syntax nodes
`IncompleteStatement`, `IncompleteExpression`, `IncompleteDeclarationStatement` and
`IncompleteTypeCompositionStatement` in `org.xvm.compiler.ast.partial`. Keep `CursorScope`,
`PartialArgument`, `PartialCallResolver` and `PartialConstructionResolver` package-private in
`org.xvm.compiler.ast`. These are semantic validation/inference helpers, not syntax nodes; moving
all eight would unnecessarily export ordinary compiler implementation details. P1–P4 are implemented
and validated after the member-generation batch; the combined validation is recorded below.

- [x] Inventory package-private/protected access and reflective child traversal, including accesses
  on other AST instances and ownership of cloned headers and anonymous construction shells.
- [x] Define a narrow compiler-owned boundary rather than public forwarding methods for every
  validation or inference operation. The audit found that a wholesale child-access rewrite is
  unnecessary; the existing registered-field mechanism can support the move.
- [x] **P1 — Extract semantics in the existing package.** Introduce a compiler-internal public
  `PartialQueries` service with `inspect(site, context, required, errors)`,
  `declarationBinding(site, lexicalScope, writtenFormals, errors)` and `argumentCall(site)`.
  Move the existing inspection orchestration into it, keeping the final incomplete-source
  diagnostic in the node. Contexts remain attempt-local arguments; no new cache or AST state.
  Pass header scope and immutable formals explicitly, removing the helper-only header accessors.
  Keep the cursor-presence predicate beside the service for ordinary property validation. Add a read-only `NewExpression.getArguments()` using `List.copyOf(args)` to replace
  the direct structural field access already exposed by the partial node's leading-argument query.
- [x] **P2 — Support registered cross-package child fields.** In `AstNode.fieldsForNames`, enable
  reflective access only for explicitly registered child fields crossing the package boundary
  inside the same compiler module. Fail explicitly if access cannot be established. Add a real
  cross-package fixture covering inherited fields, traversal, dumps, clone, replacement and
  removal; keep malformed-field tests. No public fields, module opening flags or descriptor
  framework are needed. Final header lists must still never be assigned reflectively.
- [x] **P3 — Move only the four nodes.** Update Parser, embedding result/collector types, the Kotlin
  consumers and tests together. Keep `prepareConstruction`, `Construction`, `Expression.validate`,
  inference helpers and `NamedTypeExpression` representation fields at their existing visibility.
  In the two retained resolvers, call package-private `probeCallCandidate` through an `AstNode`-
  typed reference: that method is not inherited by a subclass in another package.
- [x] **P4 — Validate and document ownership/API boundaries.** Update AST ownership notes and
  extraction map, then run field-model, parser recovery, cursor binding, identity snapshot,
  partial adapter, call/constructor/argument, delimiter and header suites. Include listener and
  cancellation boundaries and representative complete-source compilation. A native IDE rerun
  is not required solely for package organization if these regressions pass.

| Boundary | Implementation decision and regression requirement |
|---|---|
| Validation/inference access | Retain the four helpers in the ordinary AST package. The service orchestrates real validation; it does not duplicate compiler semantics in the LSP. |
| Syntax access | One ordinary constructor-arguments accessor replaces direct `creation.args` access. Pass scope/formals into the service instead of adding public representation getters. The relevant `NamedTypeExpression` constructors are already public; its fields/parenting were the actual blocker. |
| Child replacement | Inspection uses existing `AstNode.replaceChild`; no extra setters or stored contexts. |
| Reflective traversal | Cross-package access is granted at explicit field registration, not by widening all AST fields. Exercise the same registered fields through clone, traversal and replacement. |
| Clone ownership | Statement/expression nodes keep normal deep cloning. Both header nodes keep fresh construction and adoption of copied children with final `List.copyOf` lists and deferred compiler-stage traversal. Original and clone parentage/binding identities must stay separate. |
| Construction ownership | Anonymous construction prepares its attempt-owned class shell, not a detached clone. Collectors publish only bindings reachable from the current attempt's roots. |
| Public embedding types | `PartialAnalysis` and `CursorBinding.Collector.begin/record` expose the partial site type. These classes are new on this branch: put their final package in the initial extracted PRs rather than adding compatibility aliases for an unpublished branch API. |

Acceptance also includes ordinary parsing creating no partial markers, partial nodes never emitting
code, and no new mutable AST field. `IncompleteTypeCompositionStatement` remains a subclass of
`TypeCompositionStatement`; its protected constructor and access on its own instances already work
across packages. The nodes represent incomplete source syntax for embedding hosts generally, not
an LSP dependency inside the compiler.

### Partial AST implementation checkpoints

| Step | Local commit | Extraction requirement |
| --- | --- | --- |
| P1 semantic boundary | `829eb41de` | Introduces stateless `PartialQueries`, explicit scope/formal arguments and the read-only constructor-argument snapshot. |
| P2 registered fields | `29e8a64d4` | Same-module cross-package access and three real AST regressions; retain existing malformed-field tests. This is a prerequisite for P3. |
| P3 syntax package | `42ca7f932` | Moves the four classes and all Java/Kotlin consumers. Include P4's correction restoring the two protected expression-validation overrides. |
| P4 verification and ownership audit | `a1857b8df` | Combined validation and documentation below; the preceding implementation checkpoints were deliberately committed before testing. |

`PartialQueries` deliberately remains in the parent `ast` package. Java subpackages do not share
package access, and protected access does not let the moved nodes validate arbitrary receiver or
argument expressions. At P4 its three public static operations formed the compiler-internal
boundary; AST5 below moves the read-only operation into `PartialSyntax`, leaving `inspect` and
`declarationBinding`. Ordinary inference helpers, construction preparation and type representation
remain non-public.
Moving this service too would require additional public bridges back to the same package.

The four nodes' own child fields are private. Explicit registration grants access only for AST
classes and declaring classes in the same module as `AstNode`; the ordinary package's field access
is unchanged. No module-opening flag is added. Immutable header lists still use fresh construction
and adoption during clone, never reflective reassignment. `NewExpression.getArguments()` returns a
list snapshot whose elements are compiler-owned syntax, not detached immutable semantic values.

Validation on 2026-09-29 passes **502 tests**, with zero failures, errors or skips:

- **98 Java tests**: parser recovery, cursor bindings, identity snapshots, AST field registration and
  traversal (including three new cross-package cases), embedding compatibility/repository failures,
  footprint and all selected listener boundary/branch/cancellation suites.
- **395 LSP tests**: partial analysis, declaration/type/qualified/generic/compound headers and header
  slots; incomplete method/function/constructor calls; anonymous and specialized constructors;
  argument values/contexts/property initializers; delimiter recovery; call-site facts and diagnostics.
  `XdkPartialAnalysisTest` also recompiles cursor-selected complete source through the ordinary
  compiler and verifies non-emission for incomplete source.
- **Nine additional Java tests**: `CompilerDiagnosticsTest` and `ValidationScopeTest`, including
  exceptional-exit restoration of callback listeners and statement contexts.
- Root and lang `spotlessCheck` pass. Java and Kotlin main/test sources compile. The runs used
  task-local `--rerun` and `--no-build-cache`; XML counts were checked, including `skipped=0`.
  No IntelliJ/VS Code native playbook rerun is claimed for this structural refactor.

The first combined command stopped at compilation because P3 accidentally made two
`IncompleteExpression` validation overrides private while encapsulating fields. P4 restores their
original protected visibility; all test counts above are from the corrected source. No compiler
access was widened to resolve that failure. The Java/Kotlin compiler notes about existing unchecked
and deprecated code are separate from the removed formatter Unsafe warning.

### Broader AST separation audit and follow-up plan

This audit compares the branch with merge-base `4a1eae6f7` of local `origin/master`, through P3.
There are **61 changed Java files under `compiler/ast`: 49 existing files and 12 new files**.
The number of changed files is not the number of LSP-specific node extensions. The complete
placement inventory and rationale are in [errs.md](errs.md#broader-ast-placement-inventory).
This is a source/access/lifetime audit, not a code-quality grade or a claim that every compiler
change belongs in a tooling package.

The natural next steps are bounded extractions from existing nodes into existing compiler helpers.
A second semantic library, Kotlin inside javatools, or a generic public AST-internals facade is
unnecessary. `partial` names incomplete syntax; complete-program capture and invocation facts do
not belong there simply because the LSP consumes them.

- [x] **AST5 — Extract shared partial-syntax queries into `ast.partial`.** The class-level
  decision to retain `PartialArgument` hid a useful method-level boundary: its `of`/`cursor` selection
  uses only public syntax APIs; its trial replacement uses protected `setParent`/`introduceParentage`.
  Group the read-only argument-slot selection, `PartialQueries.argumentCall`,
  `PartialQueries.isWithin` and Parser's cursor-position tree walk in a small `partial.PartialSyntax`
  helper. Parser retains its explicit-cursor/mode/rollback guards. Property validation and the root
  semantic helpers call the syntax helper; `IncompleteStatement.getArgumentCall()` delegates within
  its own package, removing that operation from the public semantic bridge. An immutable selected
  argument result (cursor node plus index) can cross the package boundary without exposing compiler
  internals. Keep speculative argument construction/adoption in the root helper; do not make AST
  parenting public. Prefer one selection result used directly by the resolver over duplicate wrapper
  records. Preserve the distinct queries: any partial descendant, a descendant at an exact cursor,
  and a direct argument slot that must not cross lambda/ordinary call/construction boundaries.
  Nine dedicated regressions cover labeled/grouped/compound arguments, nested call boundaries,
  array dimensions, exact cursor offsets, original/clone ownership and unchanged speculative
  source slots. The shared result is `PartialSyntax.ArgumentCursor`; no wrapper record, retained
  state or additional AST parenting API is introduced. Combined validation is recorded below.

- [x] **AST1 — Move cursor-scope collection out of `Context`.** Package-private
  `CursorScope.capture(Context)` now serves the only three callers: `PartialQueries`,
  `PartialCallResolver` and `PartialConstructionResolver`. `getNameMap` and `collectVariables`
  remain protected; same-package access suffices. Lazy parameter-register initialization still
  precedes collection, preserving flow readability, sorted names, reserved-name filtering and
  receiver/function scope. The branch-added public `Context.cursorBinding()` convenience method
  is removed from the unpublished API; no lang consumer used it. No state or visibility is added.
  Focused regressions cover unassigned locals, parameters, lambdas, nested scopes and repeated
  cursor queries. Keep this extraction with the cursor-scope implementation when splitting PRs.
- [ ] **AST2 — Separate candidate-result copying from ordinary argument fitting.** The branch-added
  `AstNode.probeCallCandidate` builds immutable `CursorBinding.Candidate` results after the ordinary
  fitter chooses an ordering and inferred signature. Move that query orchestration to the existing
  `PartialCallResolver` if the callback overload of `collectMatchingMethods` can become package-private
  with no other exposure. Keep the fitting algorithm on `AstNode`; do not duplicate it or add a
  public entry for its many working collections. The overload is currently private, so this is an
  explicit internal-access tradeoff, not a mechanical package move. Test named/default/generic and
  converting candidates, cancellation and cloned-argument isolation. Keep the small existing hook
  if extraction makes the call contract harder to understand.
- [x] **AST3 — Consolidate anonymous capture projection in its existing helper.**
  `NewExpression.getCaptureOrigins()` now delegates map construction to package-private
  `AnonymousClassBindings.propertyOrigins(Component)`, passing the existing anonymous-class
  component. The node retains all validation, component-existence and expression-owner checks.
  Only synthetic properties map to original enclosing registers; the result remains immutable.
  The helper already owns capture registers, so this adds no state, getter or public mutation API.
  Anonymous captures, clone rejection, shadowing and rename/reference provenance pass the combined
  regression batch. The public result shape is unchanged. Keep this extraction with anonymous
  capture provenance when splitting PRs; these complete-program facts remain in the root package.
- [ ] **AST4 — Audit a unified declaration-provenance result before migrating fields.**
  `Parameter.m_arg` and `NameResolver.m_resolvedNames` are branch-added semantic state. A future
  attempt-owned collector could publish declaration/qualified-segment facts alongside call facts
  and remove node-specific state, but it must also work during declaration-only queries, lazy
  parameter allocation, failed/retried resolution, generated methods and speculative clones.
  First write lifecycle/clone regressions and map all writers/readers. Proceed only if collector
  plumbing and publication filtering are simpler than the present ownership. This is an investigation,
  not a promised mechanical move or a prerequisite for submitting the bounded partial-node PR.

The following stay in their current owners:

- Passive name/operator/import/register/selected-method accessors: they expose existing syntax or
  compiler decisions. Replacing them with an external visitor would expose more representation or
  repeat resolution; Kotlin already performs index construction and LSP policy.
- `NewExpression.prepareConstruction`, stage/context forwarding, property initializer routing,
  and `Statement` validation bookkeeping: these share the actual compiler phase and source owner.
  A detached probe or parallel implementation would change anonymous-class and cancellation behavior.
- `LambdaBindings` binding and `AnonymousClassBindings` mutation: these depend on live register
  allocation, lambda parameter representation and capture maps. Public read access already returns
  snapshots; a subpackage move would widen write access. A future complete provenance collector
  should address lifetime first, rather than hide the same mutable implementation behind wrappers.
- Error-listener propagation, `ValidationScope`, `Reporting` and code-generation corrections:
  these fix ordinary compilation and are independent compiler/error-listener extraction PRs.
- Parser cursor selection, delimiter recovery and speculative rollback: they share the token stream,
  grammar and listener branches. Moving them to an LSP parser would duplicate language rules. A pure
  syntax helper is appropriate when a concrete group needs neither parser state nor private internals;
  AST5 extracts the cursor-containment query on that basis.

### Shared partial-syntax implementation (AST5)

`ast.partial.PartialSyntax` now owns five read-only entry points: `contains`, `containsAt`,
`argument`, `valueCursor` and `argumentCall`. Its immutable `ArgumentCursor` result identifies
an existing site and its written slot; it is not a detached snapshot of mutable compiler syntax.
Parser retains its explicit-cursor, recovery, cancellation and speculation policy. Its containment
check delegates to the exact-position search, while property initialization asks whether any
partial descendant exists. Both searches can cross call/scope boundaries.

Argument selection deliberately has a different traversal boundary: ordinary calls, construction
and lambdas own their own arguments/scopes; array dimensions are excluded from positional call
slots. The original selection behavior is preserved. An incomplete call nested in a value belongs
to that inner call, not its enclosing argument. Labels, groups and compound values retain the
selected inner cursor and original slot.

`PartialArgument` remains package-private in `ast`, now as a stateless trial-construction utility.
It consumes the shared selection directly, clones the replaced syntax, and uses existing protected
parenting operations to attach a speculative expression to its lexical scope without installing it
in the source argument list. Later arguments and original source bindings remain unchanged.
`PartialQueries` now exposes only semantic inspection and declaration binding. Its former
`argumentCall` method is removed from this unpublished branch API; `IncompleteStatement` retains
its existing accessor and delegates inside its own package. No classic AST visibility is widened.

Validation on 2026-09-29 passes **258 tests**, with zero failures, errors or skips:

- **37 Java tests**: nine new `PartialSyntaxTest` cases, all 24 `ParserRecoveryTest` cases and
  four binding-identity publication cases. The parser suite includes cancellation, budgets,
  speculation rollback and ordinary parsing without partial markers.
- **221 LSP tests**: partial analysis; argument completion/context/property initializers; missing
  delimiters; incomplete method/function/constructor calls; specialized and anonymous constructors.
- Root and lang `spotlessCheck` pass; Java and Kotlin main/test sources compile. Test tasks used
  `--rerun --no-build-cache`, and XML counts confirm zero skips. The initial combined command
  completed the LSP suite but failed Java test compilation because one new assertion called an
  expression-only method on a statement. The assertion now checks the containing expression;
  the corrected Java suites and formatting checks pass in the follow-up run. Production source
  was unchanged between those two runs. No native IDE execution was requested or performed.

This checkpoint belongs with P1–P4 when extracting the partial-query/compiler-recovery PR. It
changes neither advertised capabilities nor playbook scenarios. Native editor reruns are unnecessary
for this extraction; the selected backend regressions exercise its behavior directly.

### Scope and capture helper follow-ups (AST1 and AST3)

Both extractions are complete and were validated together after implementation. They remove query
orchestration from ordinary compiler classes without new fields, caches, clone rules or widened
access. `CursorScope` still needs protected context APIs in `ast`; `AnonymousClassBindings` owns
complete-program capture provenance, not partial syntax. Moving either into `partial` would weaken
those boundaries rather than improve them.

| Step | Commit | Future PR placement |
| --- | --- | --- |
| AST5 shared partial syntax | `b16fdd52c` | Include with P1–P4 partial-query/compiler recovery. |
| AST1 cursor-scope capture | `d271be86e` | Include with cursor-scope collection and its three query consumers; omit the intermediate public `Context.cursorBinding()` API from extracted PRs. |
| AST3 anonymous-property projection | `bb5bd9e95` | Include with anonymous capture provenance and its passive AST getters. Independent of the partial-node package move. |

Validation on 2026-09-29 passes **200 tests**, with zero failures, errors or skips:

- **4 Java tests** in `BindingIdentitySnapshotTest`.
- **196 LSP tests** across `XdkScopeCompletionTest`, `XdkPartialAnalysisTest`,
  `XdkArgumentCompletionTest`, `XdkIncompleteCallTest`, `XdkIncompleteFunctionTest`,
  `XdkIncompleteConstructorTest`, `XdkAnonymousConstructorTest`, `SemanticModelTest`,
  `XdkNavigationTest`, `XdkRenameTest` and `CompilerRenameRequirementsTest`.
- Existing regressions cover lazy parameter allocation, unassigned and narrowed locals, nested
  scopes, static/instance visibility, repeated queries, anonymous and mutable captures, shadowing,
  clone rejection, source identities, references and rename proofs. Both test tasks were forced
  with `--rerun --no-build-cache`; counts come from the fresh JUnit XML results.
- Root and lang `spotlessCheck` pass. Java/Kotlin main and test compilation pass. No new native
  editor run or playbook scenario change is claimed for these internal extractions.

AST2 remains conditional on a simpler internal fitting contract; AST4 remains a lifecycle
investigation. Neither is required to submit the bounded package/provenance changes, and neither
is recorded as implemented. Independent extracted PRs must still run their own validation.

## Composition audit follow-up

`CompilerDispatchRoutesTest` inspects real TypeInfo chains for written/default overrides,
delegation (including its receiver property), and union dispatch. Existing project tests additionally
exercise source-owned generic overrides, mixins/super and delegated method/parameter rename.
Union dispatch remains refused: its independent callable targets do not constitute a written
override family. Field accessors and shorthand constructors already have dedicated property and
primary-parameter handling; making their generated method identities renameable would bypass those
proofs. Implicit/SansCode/Native and further capped/conditional routes still need dedicated fixtures;
this checkpoint does not claim exhaustive enumeration or enable them by body-category guesswork.
All three chain fixtures pass in the 67-case compiler/refactoring batch (zero failures/errors/skips).

The same validation caught a repair-proof regression introduced by `4937f4382`: a parameter in
an unresolved signature has a written declaration before it has a method slot. The later resolved
slot is the same parameter, not rebinding. `1db6200cc` compares written parameter declarations on
both sides of repair only. Keep that fix with the earlier slot-proof changes during extraction;
it is independent of member generation. The bundled Document import and parameter-rename tests pass.

## Native configuration acceptance follow-up

The VS Code launcher now accepts `-PcompilerPlaybookMultiRoot=true` for a saved two-folder
workspace. X118 exercises the same real compiler proposal and native Rename/Undo/Redo in either
layout, verifies that VS Code rejects a folder override of the workspace-scoped graph, and edits the actual
settings document during the real client's edit conversion. The refusal must leave every source
unchanged. This is client race coverage, not a mocked compiler reply. IntelliJ's project-owned
settings have no VS Code workspace/folder override hierarchy; its existing X118 and pure history
guards remain the corresponding coverage.

Validation on 2026-09-29:

- Compiler/refactoring batch: **67 tests**, zero failures/errors/skips. This includes the three
  dispatch fixtures, import repair, parameter rename and project-query lifecycle regressions.
- Expanded member-action suite: **17 tests**, zero failures/errors/skips. Three were added after
  that batch: cross-module contracts, descendant-chain refusal and broken-neighbor refusal.
  There are 70 distinct JVM cases across these focused runs, not a full-suite receipt.
- Saved multi-root VS Code: X118 and CFG1–CFG3 pass in `run-ZOoaXw`.
- Single-folder VS Code: X118 passes in `run-nFKwOD`, including the new in-flight settings guard.
- Root Spotless, LSP ktlint, TypeScript compilation and changed TypeScript ESLint checks pass.
  The real multi-root Gradle task stores its configuration-cache entry successfully.
- No IntelliJ rerun or native member-generation action-menu acceptance is claimed. Intervening
  independent graph edits during editor Undo/Redo remain a separate native history stress case;
  these receipts cover in-flight Rename settings edits and ordinary grouped Undo/Redo.

Extraction map for this checkpoint:

| Review scope | Commits to keep together |
| --- | --- |
| Partial AST boundary audit (documentation only) | `54facc488`; the move is deferred, with explicit prerequisites. |
| VS Code configuration/native coverage | `581daf039` + `1f20a7255`. |
| Compiler dispatch route fixtures | `92f86b741` + its formatting hunk in `a1e7360d8`. |
| L63 ordinary implement/override generation | `1df7643d2` + member-action corrections in `a1e7360d8` + `c8fcbd665`. |
| Existing repair-proof regression | `1db6200cc`, retained with earlier slot-proof commit `4937f4382`; independent of L63. |

The capability matrix, manual playbook, main compiler notes and audit describe the same limits.
These review slices still need independent validation when extracted.

## Teaching workspace, declarations and resource moves (L55/L61/L62)

All three implementation checkpoints were committed before running playbooks. Validation then
produced separate corrections for each scope; keep the following pairs together when extracting PRs:

| Scope | Implementation | Validation corrections |
|---|---|---|
| L55 teaching-workspace rename/memory acceptance | `d3c5d194b` | `3de9ef126` — delegate binding, affected-consumer proof boundary and live-buffer/retention acceptance |
| L61 declaration lookup | `1b72571d2` | `89e2bb461` — opt-in capabilities, compiler fixtures and installed-client request check |
| L62 companion/module resource moves | `bd760df33` | `eda441174` — preserve package aliases, reject alias-triggered module rename, reverse moves and editor undo |

L55 builds on `912abf3b8` and the graph-query/rename slices. L61 uses existing TypeInfo and detached
semantic facts. L62 depends on the proven graph rename, source replay and resource-operation
transport slices, including L55's proof-boundary correction. No new Java AST, embedding or
error-listener API is added by this batch. Extracted PRs still require independent validation.
The teaching fixture reads L62's new X103 companion data: move those shared catalog/workspace
setup hunks into L55's fixture preparation when extracting, keeping the new resource-rename
assertions with L62. These are review boundaries, not independent whole-commit cherry-picks.

Validation on 2026-09-28:

- Full server suite: **1,276 cases, 1,273 executed, three existing skips**, zero failures/errors.
  The four teaching-workspace cases pass, including the one/five-buffer workloads at 512 MiB.
- Full stdio suite: **67 executed**, zero failures/errors/skips. IntelliJ harness and VS Code
  TypeScript compilation also pass. Gradle configuration cache was stored and reused.
- The final module-alias guard and expanded constructor/companion fixture pass all **eight**
  focused `XdkResourceRenameTest` cases after the combined suite; no cases skipped.
- VS Code **X4/X102/X103/X104** pass in `run-x622U4`. X4 calls the declaration provider; X103
  applies ordered text/file/directory edits and uses the real Undo command to restore them.
- IntelliJ **START plus X4/X102/X103/X104** pass in `run-5042254046644785897`, with **zero IDE
  errors** and JUnit failures/errors/skips. Total Gradle time was 46 seconds. Declaration is
  asserted through the installed client's transport beside native navigation; X103 performs
  native forward/reverse rename with the member open and checks the nested companion file.
- Root Spotless and both changed Kotlin modules' ktlint checks pass. These are selected editor
  receipts, not a new full 113-case playbook run.

The first selected VS Code run (`run-ysmVRJ`) passed three cases but X103's query was cancelled
by a late `workspace/didChangeWatchedFiles` notification from fixture creation. Queue/reply tracing
identified that cancellation. The harness now retries only the unapplied query, then applies its
result exactly once. It never retries an accepted edit or rename. The same source combination
also passes the backend regression; no compiler cancellation guard was weakened.

L55 and L61 acceptance are complete. At this earlier checkpoint, L62's resource extension and
constructor/composition negative audit were validated, but the remaining scope included public-parameter caller closure,
annotation/mixin/delegation families, qualified or explicit-host module renames and implicit
package directory moves. Binary targets remain read-only; external consumers outside the captured
workspace graph were not proven. The subsequent L62 extension above records the implementation,
validation and remaining client/scope work. L63 remains the next new feature family.

## Header slots and native editor parity (C28/L53/L54)

The integrated batch is implemented on `lagergren/errs`; backend and VS Code validation pass.
All 13 selected native IntelliJ cases have passing receipts across the checkpoint and focused
rerun below. This is not a complete run of the 63 implemented native scenarios.
C28/L53 cover trailing qualified dots, selected qualifier tokens, empty compound operands,
ordinary multiple-return declarations, generic method headers/constraints, class constraints,
and module/package composition headers. Completion retains the original selected token or a
zero-width empty slot. No source text is manufactured. Written but unregistered formals shadow
outer names without acquiring fabricated type identities. Syntax recovery remains compiler-only.

AST ownership: `Parser` owns grammar boundaries and cursor selection. The two incomplete
statement nodes retain immutable sets of written formal names solely to prevent shadowed lookups;
`CursorScope` resolves candidates using existing compiler scopes. A root module retains its written
qualified name and registers only the real module namespace/core import so header queries have a
scope. `TypeCompositionStatement` honors explicit child deferral in its register/resolve passes,
so unfinished compositions and body declarations stay unregistered; recovery cannot emit code.
The snapshot copier also checks raw method-formal constraints before reading derived type facts: a
resolved formal identity alone does not guarantee its constraint was resolved. This check never
resumes validation or builds TypeInfo.
No new mutable AST field, semantic cache, callback or retained Context is introduced. Recovery
clone paths preserve the immutable names and independently clone syntax children.

L54 adds native X34 multi-target type navigation, X99 live import/dependency edits, X100 healthy
hierarchy with a broken neighbor, X101 library edit guards, and X102–X105 native refactoring/quick
fix application. Both editors consume new X107/X108 header variants. IntelliJ case selection uses
`-PintellijPlaybookCases=X34,X99,...`; selected-but-unexecuted cases remain `not-run`, and every
selected implementation must execute. Coverage metadata describes assertions, never a passing run.

The first focused native run (`run-5679669588027986266`) edited the first document before its
LSP4IJ `didOpen` completed. Diagnostics did not catch up and old multiline folding ranges reached
the shortened document (`LSPFoldingRangeBuilder` reported an out-of-range line). The harness now
waits for the client's document-open future before replacing a fixture; it does not send an extra
LSP request. `run-6298499906607184732` subsequently passed X106/X107/X108 with no IDE internal
failures, before exposing two navigation-driver mistakes (wrong occurrence and stale focused
editor). This is not a production fix for editing during LSP4IJ startup: keep that client race as
a follow-up requiring a controlled reproduction and an upstream/client-side fix if reproduced in
ordinary editing. Header validation and client startup behavior are separate evidence.

L54 also includes the configuration boundary exposed by native X99 after X101: the Java JSON
writer omitted `sourceModules: null`, so returning from an explicit graph to automatic discovery
could silently retain the old graph. The IntelliJ launcher now preserves explicit nulls only inside
`workspace/didChangeConfiguration` parameters, leaving unrelated protocol omission rules unchanged.
A wire-format regression checks both behaviors. Native refactoring actions run asynchronously so
modal dialogs remain operable; Reformat asserts/cancels the read-only gate and Rename asserts the
actual server rejection hint. These are client/harness changes with no compiler or AST state.

Native X100 also exposed a server lock cycle: a synchronous hierarchy request held the document
publication lock while awaiting the serial compiler worker, which was completing another query
and waiting for that same lock. Analysis and query publication callbacks now dispatch off the
compiler worker before acquiring the lock. Document identity checks and diagnostic ordering remain
under the existing lock; no compiler/AST fields are added. A bounded two-case regression covers
both analysis publication and cursor-result publication ahead of queued navigation.

X103 exposed a second lock cycle during JSON-RPC cancellation: LSP4J held its request-map lock
while result cleanup tried to enter the document lock, and a concurrent publication held the
document lock while completing a response through that request map. Result cleanup and backend
cancellation now dispatch asynchronously too. A third bounded regression verifies cancellation
returns while navigation holds the document lock; it fails against synchronous cleanup.

The X103 round trip then identified a missing file-operation capability. LSP4IJ 0.21.0 skips
rename VFS events unless `workspace.fileOperations.didRename` or `willRename` is advertised;
its skipped path includes the old-URI close/new-URI open sequence. An open renamed member
therefore left a stale overlay and produced a duplicate declaration after the reverse move.
Compiler mode now advertises and handles `workspace/didRenameFiles` for local `*.x` files,
refreshing discovery and both source locations through the existing filesystem refresh path.
A server regression checks diagnostic relocation and clearing without watcher notifications;
native X103 retains the reverse rename with the member open.

The discovery/refactoring drivers now use the same per-scenario workspace boundary. IntelliJ
sends the workspace-folder change through its installed client's transport, clears the explicit
module graph, and restores both afterward. Previously it included all 24 teaching modules while
VS Code isolated each scenario. The broad native graph returned no property rename edit even
though its roots compiled individually (DupAnno emitted its expected warning). Direct probes of
that larger proof also exhausted the normal test heap, including with only the target document
open. The reason for the native refusal and the proof's peak memory remain an explicit follow-up;
scoped scenario success must not be treated as evidence that large-graph refactoring is solved.

Future extraction: keep C28 parser/syntax ownership and Java regressions together; L53 carries the
semantic snapshot constraint guard, adapter tests, shared X107/X108 and editor consumers. L54 follows L47–L50 and contains the native
workspace/refactoring assertions, fixture setup, selection infrastructure and the explicit-null
configuration transport fix with its wire regression. Keep the server callback fix and its three
regressions as a separable L54 prerequisite on L48; it also protects VS Code clients. Each extracted PR
still requires independent validation. Integrated checkpoint `0a6007986` contains these changes;
the extraction boundaries are mapped below.
Keep the file-rename capability/notification handler and diagnostic regression with L50b/L54.

Validation on 2026-09-26: the full Java suite reports 536 tests (496 executed, 40 existing skips),
including all 24 parser recovery tests. The full LSP suite reports 1,210 tests (1,207 executed,
three existing skips); all 51 packaged stdio cases pass. There are no failures or errors. The
120-cycle retention workload issued 960 edits and released all 2,550 observed compiler objects.
The XDK rebuild, root Spotless and both editor compilations pass. Focused VS Code
X94/X96/X106/X107/X108 pass in `run-p2mjxQ` (five passed, 108 not selected; catalog SHA-256
`89e43ccb0b349e9d9ed4713b9b3eb6b1da5861a2a5f28c373a7aafa01044362f`).

After the callback fix, the full LSP run covered 1,212 tests: 1,207 passed, three existing skips,
and two old MockAdapter assertions failed because they assumed synchronous publication. Those two
assertions now wait for the diagnostic notification. A focused rerun of all 22 language-server
contract tests plus both deadlock regressions passes (24/24, no skips); at that checkpoint,
production code was unchanged from the full run. The two regressions also fail against the old
callback behavior. Packaged stdio passes 51/51, and both IntelliJ configuration wire tests pass.
The retention run releases all 2,554 observed objects across 120 cycles/960 edits (p50 218ms, p95 231ms).
Full-run XML is retained under `lang/lsp-server/build/reports/c28-l54-full-suite`; later focused XML
is under `lang/lsp-server/build/test-results/test`. These are complementary receipts, not a claim
that the original full invocation exited successfully.

After the cancellation and file-notification fixes, all 165 server-package tests pass with no
skips, as do all 51 packaged stdio cases. The remaining immediate diagnostic assertion in
`LspIntegrationTest` now waits for publication, matching the other asynchronous server tests.
Root Spotless and Kotlin lint pass. Native `run-13429802706095243790` passed startup plus
X33/X34/X35, X99/X100/X101/X102/X103/X104 and X106/X107/X108 with no IDE internal failures.
X105 stopped on a canceled daemon highlight read. The driver now retries only wrapped IntelliJ
`ProcessCanceledException` reads for a bounded interval; cancellation is never reported as an
empty diagnostic list. Its intention selector also distinguishes the action list from its preview.
The focused X105 rerun `run-6447227996108229738` passes startup and both bundled/source auto-import
variants, with no IDE internal failures. Together these receipts cover all 13 selected cases;
the earlier combined invocation itself failed. Both use catalog SHA-256
`89e43ccb0b349e9d9ed4713b9b3eb6b1da5861a2a5f28c373a7aafa01044362f`.
At that historical checkpoint, IntelliJ assertion coverage was 60 full, three partial and 50 unimplemented scenarios; the active L60 inventory above supersedes it.

## Current integrated commit map

These checkpoints are integrated through `0a6007986`; all changes remain together on `lagergren/errs`.
They are extraction sources, not a claim that cherry-picking each subset is already independently green.

| Commit | Future slice | Keep together when extracting |
|---|---|---|
| `d6039532d` | L47 live discovery | Incremental catalog, buffer/close/folder lifecycle, explicit-null configuration fix, diagnostic invalidation and X99 |
| `d6039532d` | L48 detached graph queries | Revision/cache ownership, healthy partial navigation, complete-reference boundary, source-over-binary shadow guard and X100 |
| `d6039532d` | L49 matching XDK sources | Gradle source variant and server resource consumer, binary/source revision pairing, declaration lookup, read-only boundaries, X5/X35/X101 |
| `82204932a` | L50a property families | `CompilerPropertyRelations`, property facts in the copier, family closure and dispatch comparison, property rename guards and X102 |
| `82204932a` | L50b member-file type moves | In-memory replay membership, renamed source identities in binding proof, `WorkspaceEdit.renames`, protocol resource capabilities/order and X103 |
| `82204932a` | L50c explicit aliases | Both `ImportStatement` accessors, `CompilerImportAliases`, detached alias facts, prepare/rename routing and X104 |
| `82204932a` | L50d auto-import repair | Public candidate index, partial repair facts, dependency ordering/explicit graph guards, preservation of known bindings, range-specific query cancellation and X105 |
| `a5955fd2f` | C27/L51 function and sequence headers | Parser header flag/traversal, ownership/ordinary-parser controls, adapter positive/negative expectations, shared X106 and both consumers |
| `a5955fd2f` | L52 native navigation parity | X33/X35 actions, X101 read-only navigation, catalog coverage metadata and test fixture setup |
| `0a6007986` | C28 header-slot recovery | Parser cursor/return-list recovery, immutable written formal names, root-module namespace and child deferral, clone ownership and Java regressions |
| `0a6007986` | L53 semantic header consumers | Snapshot raw-constraint guard, adapter positive/negative expectations, shared X107/X108 and both editor consumers; follows C28 |
| `0a6007986` | L48/L54 callback lifecycle | Asynchronous analysis/query publication, result cleanup and cancellation; three lock-cycle regressions and diagnostic assertions that await publication |
| `0a6007986` | L50b/L54 file-rename lifecycle | Compiler file-operation capability, rename notification refresh, diagnostic relocation regression and native X103 round trip with an open member |
| `0a6007986` | L54 native workspace parity | X34 chooser and X99–X105 assertions, per-case workspace folders, selected-case execution, popup/readiness fixes and explicit-null configuration serialization with wire tests |

L50a–d share `XdkProjectQueries`, `XdkRename` and the refactoring test file: split their corresponding
hunks, not whole files. All require L45's graph proof; L50d additionally needs L47 automatic discovery.
C27/L51 must include removal of the old unsupported-function-header expectations. L52 follows L49
and can be extracted independently of C27/L51. Each slice carries its associated manual/feature
updates and must pass its own checks after extraction. Native receipts for L52/X106 are recorded in C28/L53/L54 above; extraction must preserve the
distinction between implemented assertions and executed checks.
C28/L53 and L54 share the scenario catalog and IntelliJ driver: extract their corresponding hunks
and preserve catalog/coverage counts at each stage. The callback and file-notification fixes can be
separate prerequisite PRs; the final native checkpoint depends on both. Carry the current validation
record and documented startup/large-graph follow-ups with the associated slices.

## Function/sequence header completion and native parity (C27/L51/L52)

Step 5 extends visible-type completion to written leaf identifiers inside function parameter types,
function return types (including multiple returns), and type-sequence arguments such as
`Function<<Str>, <Int>>`. The same leaf-selection path retains the existing owned name node and
preserves the full token replacement range. Bounded missing function/sequence closers allow the
query but remain ordinary compilation diagnostics until repaired. No partial callable signature or
new declaration is invented. Generic constraints still belong to normal compilation.

`Parser` threads its existing explicit-header flag through function parameter/return and sequence
parsing. Function returns are represented by `Parameter` children, so leaf selection traverses those
children's types as well as direct parameter types. These grammar and child-ownership decisions
belong in the parser. There are no AST fields, cached contexts, clone overrides or listener changes.
Parser regressions check a single cursor report, independent adoption/cloning, unchanged source and
ordinary parsing; adapter regressions check token edits, no header signature and preserved diagnostics.

Shared X106 owns eight variants consumed by both editors. IntelliJ additionally implements X33/X35
through LSP4IJ's native `LSP.GotoTypeDefinition` action (verified against the bundled plugin's action
registration), and adds definition/type-definition plus read-only source checks to X101. At this checkpoint X101 was
explicitly partial because native formatting/rename rejection was not yet asserted. The catalog then
had 111 cases: IntelliJ implements 50 fully and four partially; 57 remain unimplemented. This is
implementation coverage, not a claim that a native run passed. Native runs remain occasional.

Future PR map: C27 contains the parser flag/leaf traversal and parser tests, following C22/C24/C26;
L51 contains its adapter tests, shared X106 and both consumers; L52 contains X33/X35/X101 native
assertions and setup, following L49 library sources. L50 is commit `82204932a`; the integrated
workspace/source checkpoint is `d6039532d`. Every extracted slice still needs independent checks.

Validation on 2026-09-26: all 23 parser recovery tests pass with no skips. The complete LSP suite
reports 1,189 tests: 1,186 executed and three pre-existing skipped Tree-sitter/future cases, zero
failures. All 51 packaged stdio cases pass. The 120-cycle retention workload issued 960 edits and
released all 2,556 observed compiler objects; no retained attempts/roots/pools remain. Root Spotless
and both editor compilations pass. Focused VS Code X33/X35/X94/X98/X101/X106 all pass in `run-9deeaR` (six passed,
105 not selected; catalog SHA-256 `e1fe190c9d500db23ba681dc954e894322eefc2d8198fd6e718a5414921e42a1`).
Both editor drivers compile after the final LSP4IJ action-ID and read-only check changes.
No native IntelliJ run is claimed for this checkpoint.

These were the remaining boundaries at the C27 checkpoint. C28/L53 above now covers trailing dots,
empty type operands, qualifier-token edits, generic-method/module/package headers and ordinary
multiple-return declarations. Written but unregistered header formals are tracked for shadowing;
they do not yet have usable semantic type identities. L54 adds the workspace/refactoring and
multi-target chooser assertions; its native receipts are recorded separately from implementation.

## Broader refactoring checkpoint (L50)

Step 4 extends the existing whole-graph proof to source instance-property/accessor families,
simple member-file type moves, explicit import aliases and unresolved-name type imports.
L50 is committed as `82204932a` on `lagergren/errs`; L47–L49 remain commit `d6039532d`.

- Property families come from compiler-composed `PropertyInfo` bodies, including written getters
  and setters. Renaming the property preserves accessor names and setter parameter names. The proof
  compares property dispatch as well as method dispatch, so accidentally creating an override is rejected.
- A type whose name matches its member `.x` filename can move to a sibling filename. Replay proves
  changed source membership without touching disk. Versioned text edits precede `RenameFile`, which
  requires the client's resource-operation capability and never requests overwrite. Module roots,
  destination collisions and companion-directory moves are excluded.
- Explicit, differently spelled, unconditional import aliases use resolved compiler identity and
  lexical import ownership. Renaming an alias changes only its declaration and uses; nested aliases
  with the same spelling and the imported declaration remain distinct.
- Quick fixes search public type declarations in the source graph and bundled XDK. Each candidate
  must repair the complete graph and preserve every previously resolved occurrence/call and known
  dispatch chain. Failed attempts are copied without resuming validation or inspecting their TypeInfo.
  New source edges are proved in dependency order and offered only with automatic discovery; explicit
  graph settings remain authoritative. Cycles, other unresolved errors and inaccessible types yield
  no action. Ambiguous names produce separate, independently proved choices. Proof work is capped at
  32 candidates per request; there is no speculative literal or member-import synthesis.

**AST placement:** `ImportStatement.getAliasToken()` exposes its existing written token;
`getImportedIdentity()` reads an existing resolver result without initiating resolution. These are
passive source/identity facts owned by the import node. No new AST field, listener, clone override,
cache or retained context is introduced. Alias scope selection, graph proof, property relationships
and edit construction stay in the Kotlin LSP library. This is the only Java change in L50.

**Extraction:** L50 follows L45 graph refactoring and L47 live discovery. Keep the two import
accessors with alias tests/copying; keep file replay and protocol capability negotiation with file
move tests; keep property facts with dispatch comparisons; keep auto-import candidate generation
with partial-fact and complete repair proofs. Shared X102–X105 and documentation follow this slice.
Validation on 2026-09-26: 49 focused refactoring, rename, protocol and snapshot-purity tests pass
with no skips; after the code-action concurrency fix, all 9 lifecycle tests pass without skips.
Both editor drivers compile, Kotlin checks and root Spotless pass. VS Code X102–X105 all pass in
`run-92TmWV` (four passed, 106 not selected; catalog SHA-256 `7c145f2c974bf4891b3e2f79117c66f780371c17240dd8ab11653f2ba9452639`).
Native IntelliJ assertions for these four refactoring cases are not yet implemented.

The editor pass found that VS Code's generic execute-rename command round trip regroups file
operations before text edits. X103 therefore invokes the registered Rename provider and applies its
ordered WorkspaceEdit, matching the real provider path. It verifies the physical file move and
recompilation, not just returned protocol objects. Background code actions now use range-specific
query keys so they cannot cancel a quick fix elsewhere in the file; a blocked-worker regression
covers both requests completing. Cancellation from document/lifecycle changes still retires them.

## Live workspace and source-navigation checkpoint (L47–L49)

Requested order: finish 1–3, validate them together and commit the checkpoint before implementing
4–5. All work stays on `lagergren/errs`; steps 1–3 are committed locally as `d6039532d` after pushed checkpoint `d046db4a8`.

1. [x] **L47 live discovery:** parse changed buffer headers incrementally, restore disk on close,
   add buffer-only module roots, handle workspace-folder notifications, refresh current-version
   consumer diagnostics, and preserve explicit `sourceModules` overrides. Duplicate/cyclic edited
   graphs retire old facts and publish `SOURCE-GRAPH` until corrected; rejected edited headers stay
   in the catalog so correction in a different file works. Explicit JSON nulls survive configuration
   conversion, so `sourceModules: null` actually restores discovery after an explicit graph.
2. [x] **L48 detached navigation cache:** reuse copied graph results by complete source/URI/configuration/
   binary digest; reread disk membership before reuse. Healthy modules retain navigation/hierarchy
   with a broken independent neighbor. References and refactoring still require a complete graph.
3. [x] **L49 matching bundled sources:** a consumable `xtc-sources` Gradle variant supplies sources for
   the same XDK dependency bundle as the binaries. Binary identity, compiler source paths and debug
   spans identify declarations; no global name matching. Materialized library files are read-only,
   outside workspace compilation, and excluded from rename/format edits.
4. [x] Validate and commit L47–L49 as `d6039532d` before the next features.
5. [x] Broader proven refactoring (L50): property/accessor families, file-moving type rename, aliases,
   and unresolved-name imports; retain explicit unsupported boundaries when proof is incomplete.
6. [x] Extend function/sequence header forms and shared IntelliJ assertions (C27/L51/L52); native
   execution and further syntax forms remain explicitly tracked above.

No Java compiler or AST changes are needed for L47–L49. Discovery retains immutable parsed headers;
query reuse retains detached semantic models only. Source attachment reads existing compiler metadata
and uses the Java parser for written declaration tokens. It does not put resolver state on AST nodes.

Future extraction: L47 follows L43 discovery and server lifecycle; L48 follows L44 graph identity joins;
L49 follows the bundled-library variant and semantic source-location API. The Gradle plugin's new
source artifact and the server consumer belong together in L49. Tests/docs follow their feature.
Final verification on 2026-09-26:

- Java: 535 reported / 495 executed / 40 existing skips; Gradle plugin: 19 passed.
- Full LSP suite: 1,169 reported / 1,166 executed / three existing skips, zero failures.
  After the final source-shadowing guard, 42 focused graph/boundary cases pass; after preserving
  configuration nulls, all 13 configuration/project-server cases pass. These later runs add two
  regression tests to the full-suite baseline; they are not reported as another full-suite run.
- Packaged stdio: all 51 pass. Both editor drivers compile; Kotlin checks and root Spotless pass.
  The resource/source-variant task stores and reuses the configuration cache.
- VS Code X5, X35 and X99–X101: all five pass, 101 not selected, in
  `lang/vscode-extension/build/reports/compiler-playbook/run-psziUN/results.json` (catalog SHA-256
  `cabf674637cce938c61412f4a253dcb7e071dda89e194a43f3a79181249754f5`). Native IntelliJ is deferred.

The first editor run exposed a real null-configuration conversion bug; the passing rerun includes
its fix. It also corrected a harness command name and VS Code's empty formatting-result convention.
The full-suite source-navigation expectation updates check actual matching declaration text, and the
snapshot-purity test still recursively rejects retained compiler objects. Full-suite XML is retained
locally under `/private/tmp/errs-l47-full-results` before focused runs replace Gradle's report files.

Bounds: query reuse is process-local, not a persistent workspace database. Broken dependencies also
exclude their consumers; healthy partial results are not a proof of complete references. Source
attachment covers matching bundled artifacts only; host binaries still need the existing explicit
source index. Ambiguous/missing debug metadata gives no target. Opening a materialized library file
is a source viewer, not a new editable module with full semantic analysis. Shared X99–X101 cover live
imports, partial navigation and library source targets; native coverage remains separately reported.

## Remaining functionality order

Work continues on `lagergren/errs`; the pushed checkpoint before the current batch is `5c334f919`.
These are additive functional steps, separate from the occasional native IntelliJ verification.

1. [x] Class/interface/type-composition header recovery and type completion — verified below.
2. [x] Registered class/method formals, empty generic arguments, parameterized qualifiers and
   middle-of-final-identifier replacement — C24/L41 below. Unregistered header formals stay deferred.
3. [x] Qualified/grouped argument values and slots before later arguments — C25/L42 below.
4. [x] Automatic workspace source discovery and on-demand indexing — L43 below. Unsaved import-edge
   and dynamic workspace-folder refresh, plus persistent indexing, remain follow-ups.
5. [x] Broader compiler-proven rename/import actions and bounded editor features — L45/L46 below.

## Five-area functionality batch

Requested after checkpoint `5c334f919`: implement the following sequentially, with a separate
commit per area and a combined verification pass after all five implementations. The commits in
this batch are development checkpoints until that final pass; no intermediate green result is implied.

1. [x] Argument contexts implemented in `837fae19c`: qualified/grouped values and slots before later arguments (C25/L42).
2. [x] Workspace source discovery, unopened-module symbols and the complete bundled XDK implemented (L43).
3. [x] Workspace navigation, implementations and hierarchy implemented (L44).
4. [x] Broader proven refactoring and import actions implemented (L45).
5. [x] Generic base editing and compiler-only editor features implemented (C26/L46).
6. [x] Combined compiler/LSP/protocol verification, focused shared editor scenarios, formatting;
   update the final evidence and commit map. Native IntelliJ remains an occasional checkpoint.

C25 adds passive `IncompleteStatement.getArgumentCall()` and a package-local `PartialArgument`
record to derive a cursor's containing argument/call from existing child syntax, preserving labels
and parentheses. These belong beside syntax traversal: no context, resolver or validation cache is
stored on a node. Candidate enumeration uses ordinary unbound arguments, while proposed names replace
only the cursor in disposable argument copies and fit alongside all later arguments. Qualified
property reads use normal compiler validation. No mutable AST field is added. L42 copies the
containing call's facts while retaining the inner cursor's exact replacement token. Tests were written alongside each area and first run after all five implementation commits, as requested.

| Area / extraction slice | Development commit | Contents and future PR boundary |
|---|---|---|
| 1 / C25, L42 | `837fae19c` | Java syntax-derived argument probes (C25); Kotlin copied call contexts and adapter tests (L42) |
| 2 / L43 | `1cfd3539d` | Workspace discovery/configuration and unopened symbol queries; shared complete XDK dependency bundle and read-only binary guard |
| 3 / L44 | `705552d25` | Compiler-identity joins for cross-module implementations and type/call hierarchy, digest-bound handles |
| 4 / L45 | `8752b6b23` | Proven inline-type/static-member rename and import cleanup; asynchronous versioned code-action transport |
| 5 / C26, L46 | `987e01fa0` | Generic-base parser selection (C26); Java-lexer editor features, shared X97/X98 and both editor consumers (L46) |

Extraction must include the final validation fixes and formatting commits recorded below; these
five checkpoints deliberately preceded testing. L44 requires L43 and existing copied semantics;
L45 uses L43's complete source graph and existing rename proofs. C26 is a small additive parser
change; L46 editor helpers do not need a Tree-sitter parser. The current map adds seven slices to
the previous 79 (86 in total); historical counts below describe their respective checkpoints.

Validation follow-up: **`1139f8cf0`**. It belongs with the five development commits above;
do not extract the checkpoints without their corresponding corrections:

| Slice | Required parts of `1139f8cf0` |
|---|---|
| C25 | `PartialArgument` array exclusion and stack-local argument lists |
| L42 | `SemanticModelBuilder` inner receiver/member preservation and argument-context expectations |
| L43 | Canonical discovery/library fixtures and formatting of the discovery/index helpers |
| L44 | `XdkWorkspaceNavigation` source-URI normalization and alias/call-range regressions |
| L45 | Non-null declaration-source local, valid import/rename fixtures and versioned-action formatting |
| C26 | Parser clone/source-ancestry regression |
| L46 | Generic-base/header expectations, lexical-token invalidation control, stdio variants, both catalog guards and editor/helper formatting |

Formatting-only hunks in shared files follow the feature that introduced those hunks. The documentation
commit following this validation commit updates the capability matrix, both editor READMEs, playbook,
error-listener/AST audit and this map. No new extraction group is needed for validation or documentation.

### Combined verification and follow-up fixes

The final combined backend run (2026-09-26) passes in **5m42s**:

- Java: **535 cases, 495 executed, 40 existing skips**, zero failures/errors. All 23 parser
  recovery tests execute; the skips remain in the existing source/lexer/ASM/project-creator suites.
- LSP: **1,164 cases, 1,161 executed, three existing skips**, zero failures/errors. The skips are
  the two non-XDK cross-file navigation cases and one future inlay-hint case.
- Packaged stdio: **51 passed, zero skips**, including six generic-header UTF-16 edit variants.
- Both editor drivers compile; Kotlin checks, TypeScript compilation, root Spotless and
  `git diff --check` pass. No native IntelliJ run is performed.

Validation fixed the inner cursor's receiver/member facts while copying its outer call context,
kept multidimensional array syntax outside ordinary positional-argument fitting, and normalized
workspace query URI aliases without losing the source view's original output URI. Parser clone
coverage now establishes normal source ancestry before deriving token text. Fixture expectations
follow the new argument/generic behavior, preserve the compiler's complete written callee range,
and import an actual XTC `ecstasy.maps.HashMap` declaration. Formatting changes follow the owning
slice. Both catalog guards now recognize X97/X98. VS Code X94–X98 pass in
`lang/vscode-extension/build/reports/compiler-playbook/run-KoAP6K/results.json`: **five passed,
98 not-selected, zero failures/not-run**, 27 seconds including Gradle and IntelliJ driver compilation.
Catalog SHA-256: `05a7f2a4145d68d6968da4d515e84384eb369f52037a8f09d28d6988eb992c76`.
This is a focused editor pass; the backend results above came from the preceding full run.

Remaining functional work after these bounded implementations:

- [x] Refresh source dependency edges for unsaved import changes and dynamic workspace folders (L47).
- [x] Reuse graph query results and support healthy partial graphs for navigation/hierarchy (L48).
- [x] Attach matching bundled XDK source ranges (L49); host binaries retain the explicit source-index API.
- [x] Prove instance-property/accessor-family rename, file-moving type rename, import-alias rename
  and unresolved-name auto-import (L50, bounded as recorded above).
- [ ] Extend the remaining cursor/header forms and inferred displays only from concrete compiler facts.
- [ ] Complete the separate native IntelliJ parity checkpoint; implemented shared scenarios do not
  establish a native pass, and 57 catalog cases remain unimplemented in that driver.

### C26/L46 editing features

The parser now retains a selected generic base name even when its written type arguments follow
the cursor, so `Li|st<String>` replaces `List` and preserves `<String>`. This is a selection change
in `declarationTypePrefix`, with independent-clone coverage; it adds no AST fields or new semantic
API. Constraint legality remains the responsibility of normal compilation after acceptance.

The Java lexer supplies bounded brace/parenthesis/bracket indentation, trailing-whitespace/final-
newline edits, range/on-type formatting and HTTP(S) links inside comments/literals. Multiline token
contents are untouched. Formatting refuses lexical errors and verifies that every token's kind and
raw spelling survive the complete proposed edit. It does not implement expression wrapping,
continuation alignment for generic headers or unbraced control-flow layout. Semantic tokens add
lexical comments/literals/keywords while resolved names retain priority. Existing compiler-proven
inlays remain unchanged; additional inference displays still need concrete compiler facts.

Module code lenses use the existing `xtc.runModule` client command. Linked editing is limited to
resolved, rename-eligible locals in one successful source snapshot; unrelated equal spellings,
binaries and parameters do not acquire linked ranges. Linked typing has no proposed-name input,
so it is not a substitute for rename's compile-and-binding proof.

Shared X97 (nine argument-context variants) and X98 (three generic-base variants) are consumed by
both editors. The catalog contains 103 scenarios: X1–X98, CFG1–3 and 7a.8–9. IntelliJ implements
47 full and three partial cases, with 53 not implemented, plus separate startup; native execution
is deferred. Compiler/adapter/protocol coverage passes in the combined validation below. Native IntelliJ
execution is distinct from compilation of its driver.

### L45 broader refactoring

Whole-graph rename now proposes inline source types, static functions and static properties in
addition to ordinary override families. Source identity, rather than spelling, determines edits;
all source modules are recompiled and every written binding/selected call/ordinary dispatch chain
is compared before returning a versioned edit. Binary targets, constructors and member-file type
renames requiring file moves remain excluded. General instance-property families and import-alias
renames still need additional proof coverage.

Java-parser import ranges propose unused-import removal and contiguous import sorting. Conditional
and wildcard imports are excluded, comments are not moved or discarded, and only edits that compile
and preserve the graph's written bindings and method chains are offered. At most 32 removal probes
run per request. Auto-import of unresolved names remains a separate follow-up. Code actions now have
an asynchronous adapter seam and the same workspace-version checks and versioned protocol edits as
rename; unsupported clients receive no unversioned compiler edit fallback.

Compiler implementation facts are now included in graph extraction (needed by L44); the earlier
rename-only builder deliberately omitted them. The new adapter and protocol regressions pass in the combined verification below.

### L44 whole-graph navigation

Compiler constants establish cross-module symbol aliases on the serialized worker. A Kotlin-only
join then merges detached semantic tables, direct type edges, implementation targets and selected
call sites. It does not match display names or infer runtime dispatch. Definition/type lookup can
compile an unopened source; implementation and type/call hierarchy queries include unopened source
consumers. Bundled binary declarations remain non-navigable without source metadata.

Hierarchy handles contain a digest of the exact graph configuration, source membership/text and
binary revisions. A later request recompiles the graph, verifies the digest and resolves the original
selection in the new detached view; edited closed files invalidate old handles even before watcher
notifications. Compiler objects stay inside a query. Queries currently recompile on demand and
require a complete successful graph; caching and partial-graph hierarchy are later optimizations.
The new regression covers unrelated same-name declarations, overload separation, incoming/outgoing
calls, unopened implementations, URI aliases and stale handles; it passes in the combined run.

### L43 discovery and bundled libraries

Workspace folders are scanned for Java-parser module declarations and source import edges. Generated
folders and symlinks are excluded; bundled library names never enter the editable source graph.
An incomplete header retains its previously known module name/dependencies. Duplicate names,
overlapping roots and cycles are rejected before replacing the active graph. Watched-file changes
refresh discovery and retire old requests. Explicit configurations remain authoritative; `[]` clears
and disables discovery, `null` restores automatic discovery, and absent settings preserve the current
mode. VS Code defaults to `null`; IntelliJ's absent configuration uses initial workspace discovery.

Workspace symbol requests compile unopened graph members on the compiler worker and return copied
source declarations. Independent healthy modules remain searchable when a neighboring module fails.
This is on-demand indexing, not a persistent database. Discovery currently refreshes at startup and
watched-file changes; unsaved changes to import edges and dynamic workspace-folder changes remain
follow-ups. Ordinary unsaved edits still use the existing versioned overlays and dependency graph.

The previous production dependency roots were ecstasy and the native bridge, with eleven resources
in the generated bundle through transitive dependencies. That was not the full distribution. A
shared `xdk-libraries` Gradle catalog bundle now supplies both the distribution and LSP through their
existing module configurations, producing 24 artifacts. TypeInfo tests read fresh copies of those same production resources;
there is no larger test-only repository. Every bundled module name is reserved against workspace
source/artifact replacement. These binaries resolve types and signatures but have no invented source
location or rename target. The three bootstrap-name checks in `XdkLibraries` are minimum health
assertions, not a module-path whitelist. No installDist dependency or archive extraction is introduced.

Discovery, complete-library resolution and binary-boundary regressions pass. Gradle stores and
reuses configuration-cache entries with the shared bundle. All tests consume the production resources.

## Generic type completion batch

C24 is committed as `1f843896f` and L41 as `45e3a0998`, after `885293f82`. All work stays on `lagergren/errs`. The extraction map has **79 groups**.
The four forms were implemented together before targeted validation, as requested.

- Registered class formals are offered in member/return/parameter and nested type headers. A valid
  generic method exposes its formals during body completion without duplicate local/type entries.
  Normal contextual lookup still decides shadowing. An unfinished generic method or type header
  does not acquire invented type parameters or a compiler component.
- Empty generic slots work at the first or later argument, including nested `>>`/`>>>` closers,
  whitespace and actual EOF. They retain only an empty cursor marker; no type argument is fabricated.
- A complete parameterized qualifier such as `Owner<String>.Ali` resolves through a disposable AST
  copy and compiler staging/TypeInfo. Nested children and aliases keep their substituted type;
  hidden/unknown/value qualifiers return no enclosing-scope fallback.
- A cursor inside a final identifier filters on its typed prefix and replaces the entire original
  token, preserving qualifiers and generic suffixes. Qualifier-middle edits and generic base-name
  edits such as `Lis|<String>` remain outside this slice.

**AST and API placement:** no fields or cloning rules are added to ordinary AST nodes.
`IncompleteStatement.getCompletionPrefix()` derives text from the cursor and original token on
source-adopted syntax. `CursorScope` owns stack-local qualifier copies, resolvers and cancellable
collecting PROBE listeners; retained syntax remains unresolved/unvalidated. `CursorBinding.NamedType`
adds a `type()` component so consumers preserve qualifier substitution instead of deriving a naked
identity type. The old two-argument constructor and existing accessors remain, but source record
patterns with two components must migrate; this is recorded as part of the embedding API policy.
Kotlin copies the resulting type and drops duplicate formal entries by compiler symbol identity.
No compiler objects escape into the copied LSP model.

| Slice | Commit | Additive extraction contents | Prerequisites |
|---|---|---|---|
| C24 | `1f843896f` | Generic-slot and mid-token parser selection, formal/parameterized scope lookup, substituted candidate type, passive prefix accessor and parser ownership tests | C22, C23 |
| L41 | `45e3a0998` | Copied type/prefix consumption, formal deduplication, adapter and UTF-16 stdio tests, shared X96 and both editor consumers, capability/playbook updates | C24, L39; L40 for focused editor validation |

Shared X96 has eight acceptance variants. The catalog now contains 101 scenarios; IntelliJ
implements 45 full and three partial scenarios plus START, with 53 unimplemented. This describes
implemented checks, not native execution. Native IntelliJ is deliberately deferred for this batch.
**Targeted validation (2026-09-26):** 22 parser tests and 143 related LSP tests pass with zero
failures/errors/skips. The packaged stdio test passes four request/acceptance/repair variants,
including CRLF and UTF-16 offsets. Both editor drivers compile and Kotlin/TypeScript checks pass.
VS Code X94–X96 pass in `lang/vscode-extension/build/reports/compiler-playbook/run-MdzjLq/results.json`:
**three passed, 98 not-selected, zero failures/not-run**, 24 seconds for the Gradle invocation.
Catalog SHA-256: `d8472c18b0994bf6d79c536c276236af9f360709476667da3d97808f9f108eea`.
Root Spotless and `git diff --check` pass. These are focused results; the prior C23 full-suite and
retention counts are historical, not rerun for this batch.

Final review tightened formal-register recognition to require the actual formal register identity;
a `Type<String>` or `Type<Other>` value shadowing a formal must remain a value candidate. The
final focused generic-header suite passes **35 tests, zero failures/errors/skips** after that guard.
Each shadowing fixture is first compiled with a valid body. The initially self-shadowing
`Type<Element> Element` fixture was replaced with `Type<Other> Element`; production diagnostics
were not suppressed. The editor and stdio results above precede this register-only tightening;
they were not needlessly rerun. The final focused invocation took 20 seconds.

## Class and interface composition headers

C23 is committed as `ef2b0b870` and L39 as `9ff95ec35`, after checkpoint `69e125d01`.
The independent L40 harness follow-up is `0b82e609f`. All remain on `lagergren/errs`; no extracted
branch or PR is created. That checkpoint had **77 distinct groups**.

- [x] Retain the written type name and body when a bounded header fails, including member-file roots.
- [x] Query written type prefixes and empty composition slots in `extends`, `implements`, `delegates`,
  ordinary `incorporates` and `into`, using the real enclosing compiler scope.
- [x] Reuse qualified/generic leaf selection and bounded missing-type-closer handling.
- [x] Add immutable cursor ownership, explicit independent cloning, listener-stop controls and
  unsaved member/root overlay tests. No partial class component, inheritance facts or body members register.
- [x] Add shared X95 with nine completion/acceptance/repair variants in both editor consumers.
- [x] Complete compiler, LSP, packaged transport, VS Code and formatting validation; compile the
  IntelliJ consumer without launching another native run.

**AST placement:** `IncompleteTypeCompositionStatement` is a syntax-only subtype of the existing
class declaration. That shape lets ordinary module assembly keep an incomplete member-file root
and lets structural consumers retain the written class/interface category, name, body and range.
It explicitly is not a component node. Registration, name resolution and validation do not traverse
its body, so neither a fabricated superclass nor body declarations enter the enclosing component.
`TypeCompositionStatement` gains only a protected constructor for this written recovery metadata.

The new cursor list is **final and immutable** (`List.copyOf`). Its clone constructs a fresh recovery
node, clones/adopts the real body and cursor children, and preserves the existing parent/stage
contract. Reflective traversal reads the final list but never replaces it during cloning. There
are no new mutable fields, lazy caches, retained contexts or resolvers. This small specialized copy
is appropriate because the node cannot validate or emit executable code. Parser tests verify
independent child ownership and reject list mutation.

**Bounds:** suggestions establish visible type identities, not whether a type is legal after a
particular composition keyword or satisfies a generic constraint. Normal compilation validates
acceptance. Header type variables, conditional-incorporation constraints, module/package headers,
trailing dots, parameterized qualifiers, empty generic slots and mid-token positions remain outside
this historical slice; C24 above adds the last three forms. Recovery needs a brace, semicolon or
actual EOF boundary; header expressions containing their own braces may still limit structural recovery. No Tree-sitter fallback or public result
shape is added.

| Slice | Commit | Additive extraction contents | Prerequisites |
|---|---|---|---|
| C23 | `ef2b0b870` | Type-composition recovery syntax, immutable clone path, parser boundaries, enclosing type lookup and parser/ownership tests | C22 |
| L39 | `9ff95ec35` | Adapter/member-overlay/stdio proof, shared X95, both editor consumers and capability/playbook updates | C23, L37 |

**Validation (2026-09-26):** Java **493 executed, 40 existing skips**; LSP **1,097 executed, three
existing skips**; packaged stdio **50 passed, zero skips**. Every suite has zero failures/errors.
All 20 new header adapter/API tests and all 21 parser recovery tests execute. The parser suite was
rerun after strengthening the body/cursor clone assertions. The bounded retention workload runs
120 cycles and 960 edits: **2,443 weak references, zero retained**, rebuild p50/p95 **209/223 ms**
including debounce. These timings are not a performance SLA.

VS Code **100 passed**, including all nine X95 variants, in
`lang/vscode-extension/build/reports/compiler-playbook/run-yySAUn/results.json`.
Catalog SHA-256: `b132177b05a08545d899710b2d7a40e1eb1610339ae44cf176d6e1835862598b`.
The preceding `run-rZ5zap` passed 99 cases and failed X95 because the fixture tried to extend a
virtual nested class from outside its owner. Making that fixture base static corrected the input;
no compiler error was suppressed. The previously documented semantic-token overlap warning at
Advanced.x line 43, column 13 remains a separate L12 follow-up. IntelliJ driver compilation,
Kotlin/TypeScript checks, root Spotless and `git diff --check` pass. No native IntelliJ run is claimed.

## Focused playbook validation

The user requested finer test granularity after the X95 fixture correction. L40 is a separate
host-only follow-up after the C23/L39 full verification above. `testCompilerPlaybook` accepts
`-PcompilerPlaybookCases=X95` or comma-separated exact IDs. The selected mode builds the extension
and runs those editor cases without scheduling all LSP/stdio tests; the default keeps the full
check graph. Direct npm runs accept `--cases=X95`. Invalid/empty/duplicate selections fail before
launch, and reports distinguish `focused`, selected IDs, `not-selected` and unexpected `not-run`.
Existing host XML carries its timestamp and is explicitly historical supporting evidence.

| Slice | Commit | Additive extraction contents | Prerequisites |
|---|---|---|---|
| L40 | `0b82e609f` | VS Code case selector, configuration-cache-compatible task property, focused coverage reporting and runbook commands | L16/L32 shared playbook harness; independent of C23 |

Focused verification passes: `run-ZVvJ2o` and final `run-VqiqcS` each execute **X95 only**, with
**one passed, 99 not-selected, zero not-run/failures**. The final Gradle run takes **11 seconds**
and explicitly reuses its configuration cache. The selected editor case takes about five seconds.
Default/all selection, comma-separated ordering, and rejection of unknown, duplicate, empty and
malformed selections were checked without launching an editor. A TypeScript compile check required
an explicit `.js` suffix on the lazy import; the final compiled launcher and focused run pass.
No LSP/stdio test tasks occur in the focused graph. This adds no shipped LSP capability and does
not change the IntelliJ native runner.

## Branch modernization checkpoints

The approved M1–M12 review items are implemented as twelve separate local commits on
`lagergren/errs`. These refine code already introduced or changed by this branch; they add no
new feature or public API. The subsequently authorized B1–B4 and S1–S4 are also implemented
as separate local commits, alongside the additive child-traversal compatibility cleanup.
Native IntelliJ verification remains separate from the functional work listed above.

M1–M12 validation on 2026-09-26: Java **478 executed, 40 existing skips**; LSP **1,074 executed,
three existing skips**; packaged stdio **48 passed, zero skips**. Every suite has zero failures/errors.
Root Spotless, Kotlin formatting/checks and LSP compilation pass. The child cursor implements both
Iterable and Iterator, making Kotlin's direct `asSequence()` call ambiguous; M10 uses the simpler
`toList()` snapshot and includes that compilation fix in its own commit.

| Item | Commit | Scope and extraction placement |
|---|---|---|
| M1 | `5fea5eede` | Remove stale imports and sort new imports; fold each hunk into its owning compiler/embedding/host-test slice. |
| M2 | `10feb188e` | Establish required listener fields at their constructor assignments; C2/C3 listener ownership. |
| M3 | `09d8d64d6` | Use the existing severity comparison API; C1 listener contract and its C3/parser and E1/diagnostic consumers. |
| M4 | `54a1ab890` | Bind checked types with Java patterns and assertion return values; owning parser/diagnostic tests, including C20/C22 recovery tests. |
| M5 | `5541108a4` | Construct ordered repository/diagnostic views directly; E1 repository helper and C1/E1 diagnostic tests. |
| M6 | `9073751ac` | Collect trial argument clones directly into the mutable destination needed by fitting; C9/C11/C16 resolver slices. |
| M7 | `9045d639a` | Forward queued module diagnostics through `ErrorList.logTo`, then clear; C2/C3 pipeline ownership. |
| M8 | `b95dc7f6b` | Name Kotlin semantic-model, module-analysis and cursor-query arguments; take with each owning LSP model/consumer slice. |
| M9 | `a1d1a0c70` | State required source variable/property symbol lookups with `getValue`; L25/L28 argument-value consumers. |
| M10 | `1eaa68fd3` | Build read-only child/declaration lists and share the semantic view map; L3 structure and L6/L11 hierarchy consumers. |
| M11 | `6732308cf` | Map outline categories directly from compiler tokens; L3. |
| M12 | `7187af900` | Name written-accessor and source-overlay tuple components; L18/L22 implementation lookup and L5 source snapshots. |
| Iterator compatibility | `50411afa4` | Add repeatable `AstNode.childNodes()` views and six ownership/mutation compatibility tests; E1/E2 AST access, with each LSP reader in its owning semantic/structure slice. Preserve the existing mutation cursor API. |
| B1 | `fd4bceb81` | Return an immutable submission from the lifecycle lock; L2/L5 adapter scheduling, preserving cancellation outside the lock. |
| B2 | `ef23fb8e2` | Publish detached, unmodifiable identity maps for invocation/cursor facts; owning E2/C9/C11 binding APIs and their identity regression tests. |
| B3 | `3ba1d5e21` | Make dependency-ordered transitive invalidation explicit; L5 project lifecycle, including a shuffled diamond-graph regression. |
| B4 | `2319578a7` | Group immutable cursor call facts behind the existing nine-component record; owning C9/C11/C16/C18 fact APIs and LSP consumers. Retain all existing constructors, components and accessors. |
| S1 | `035df1099` | Express independent fixture checks and per-cursor reports with `forEach`; fold each hunk into its owning listener/parser/recovery slice. |
| S2 | `a790ed9b1` | Use list endpoint methods where nonempty input is established; owning diagnostic/type-inference slices. |
| S3 | `0b689126d` | Map written name segments directly to an immutable binding list; owning E2 name-binding API. |
| S4 | `197b42427` | Simplify test array conversion and repeated diagnostic reporting; owning bundler/listener tests. |

Final host checkpoint validation after B1–B4/S1–S4 and the fold fix: Java **491 executed,
40 existing skips**; LSP **1,077 executed, three existing skips**; packaged stdio **49 passed,
zero skips**. All have zero failures/errors. LSP compilation, Kotlin checks and root Spotless
pass. This includes the new graph/identity/cursor compatibility and folding regressions.
The retention workload performs **120 cycles, 960 edits**, observing **2,447 weak references,
zero retained**, with rebuild p50/p95 **216/228 ms**, including debounce. This is a bounded
regression workload, not a performance guarantee. VS Code **99/99 passed**, with zero failures
and errors, including X92's unchanged fold boundary and all nine X94 variants. Report:
`lang/vscode-extension/build/reports/compiler-playbook/run-saXpCM/results.json`, catalog SHA-256
`a58f0e0c42402e5233741c996a79cd48a22337817c665cd2b45b0c103c1a87b2`.
The combined host/editor verification took **7m30s** and stored its configuration cache.
Changed Markdown file targets and `git diff --check` pass. IntelliJ runtime evidence and its
remaining cases are recorded separately below; this checkpoint does not claim a complete native run.

Keep these commits separate on the integrated branch for review. During future extraction, apply
each hunk with the feature that introduced its target code; do not make an early diagnostic PR
depend on later cursor or host features just to cherry-pick a whole cleanup commit. These twelve
checkpoints therefore do not add feature groups to the extraction map. Each extracted PR
still requires independent validation.

## Precise compiler fold boundaries

Commit `2e98860e1` fixes a shipped integration defect exposed by native X92. The compiler retained
the correct method extent, but the adapter discarded its columns. LSP4IJ 0.21.0 interpreted a
line-only end after the method's brace by scanning forward to the module's brace, installing a
native fold at lines **1–4** instead of **1–3** and swallowing the following declaration.

The adapter model and LSP transport now preserve optional character boundaries. Compiler folds
keep the heading line visible and end immediately before a real closing brace. An unfinished body
still ends at its actual EOF; no missing delimiter or earlier endpoint is invented. The source text
is read once per query, without mutating Source or adding AST fields, caches or clone rules.
This uses the existing AST/source API and standard
[LSP folding character fields](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#foldingRange).
Tree-sitter/mock ranges retain their previous line-only behavior.

Extraction: fold the model, adapter and transport change into **L3**, with the recovery regressions
in their owning C20/L35 host slice. The existing native assertion belongs to **L38**. This does not
add a feature group. Keep the shared X92 expected boundary unchanged.

Validation: six selected folding/recovery/project-graph cases and two packaged stdio cases pass
without skips; full Java and formatting checks pass. Regressions cover complete and incomplete
headers, following declarations on the closing-brace line, emoji/UTF-16, LF/CRLF and true EOF.
The native rerun verifies both X92 header variants with the strict **1–3** fold boundary and
retained Structure entries; see the [native checkpoint](#intellij-native-assertion-parity) below.
LSP4IJ's inspected implementation also rejects some ranges at
EOF; server tests retain the correct protocol boundary rather than disguising that client behavior.

## Parameterized and compound declaration types

Compiler and server-test portions are committed in `3571d265d` on `lagergren/errs`, after pushed
checkpoint `450e65686`. Shared/editor consumers are in `3c68c2dfe`, alongside the separately
extractable native assertion work below; this documentation records both scopes.

- [x] Complete written leaf names in nested generic arguments, union/intersection/difference
  types and grouped/nullable/array/immutable wrappers in property/return and parameter headers.
- [x] Carry header recovery explicitly through the parser's type grammar; tolerate bounded
  missing `>` and grouping `)` only around an actual selected prefix.
- [x] Reuse the original leaf node, cursor target, compiler scope lookup and existing result API.
  Do not register an unfinished declaration, retain a resolver or add an AST field/clone rule.
- [x] Add parser ownership/strict-parse controls, 33 adapter/API cases, UTF-16/CRLF transport
  variants, nested-generic retention queries and shared X94 with both native consumers.
- [x] Record final compiler/LSP/stdio/VS Code verification below; native IntelliJ is the separate checkpoint.

**API/AST placement:** the parser owns recognizing a written leaf inside type syntax. Only that
original `NamedTypeExpression` moves to the existing `IncompleteStatement` target; the unfinished
generic/compound owner is discarded. The retained declaration still owns the real full source
extent. Ordinary adoption and cloning establish independent parentage. `CursorScope` and
`CursorBinding.NamedType` need no change. Header mode is a call-stack argument, not mutable parser
state. Normal parsing remains strict, and the cursor probe reports one `INCOMPLETE_EXPRESSION`.

**Bounds:** suggestions enumerate visible types; they do not prove constraints on the enclosing
generic/compound type. An accepted name leaves missing delimiters and invalid constraints visible
until repaired. Empty generic slots/operands, function/sequence-type interiors, generic base-name
prefixes (`Lis|<String>`), parameterized qualifiers (`Outer<String>.Ite|`), trailing dots,
mid-token cursors, formal-type candidates, generic-method/multi-return and type-composition
headers remain follow-ups.
No source text, delimiter token, formal scope or declaration name is fabricated.

| Future PR | Contents | Prerequisites |
|---|---|---|
| C22 | Parser type-grammar recovery, written-leaf selection, strict parsing/ownership tests and embedding contract | C21 |
| L37 | Parameterized/compound adapter/API/stdio/retention proof, shared X94, both native consumers and capability/playbook updates | C22, L36 |

These add two groups, bringing the extraction map to **73** before the separate IntelliJ parity
work below. C22 and the L37 server tests come from `3571d265d`; L37's shared X94 and editor
consumers come from `3c68c2dfe`. Preserve that compiler/host boundary during extraction; each
extracted PR must pass independently.

Initial feature validation on 2026-09-26, before the modernization/folding checkpoint above:

- Java: **478 executed, 40 existing skips**, zero failures/errors. All 19 parser recovery tests run.
- LSP: **1,074 executed, three existing skips**, zero failures/errors. All 33 new adapter/API cases
  and the preceding 19 unqualified/22 qualified header cases run without skips.
- Packaged stdio: **48 passed, zero skips**, including generic/compound leaf edits after emoji/CRLF.
- Retention: **120 cycles, 960 edits, 2,445 weak references, zero retained**; rebuild p50/p95
  **215/231 ms**, including debounce. This bounded workload is not a performance SLA.
- VS Code: **99 passed**, zero failures/errors; all nine X94 variants pass. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-IpxRm6/results.json`.
  Catalog SHA-256: `a58f0e0c42402e5233741c996a79cd48a22337817c665cd2b45b0c103c1a87b2`.
- IntelliJ integration compilation and Kotlin checks pass. Native validation of the expanded driver
  is the separate L38 checkpoint below.
- XDK preparation, root `spotlessCheck`, TypeScript/Kotlin checks and `git diff --check` pass.
  The extraction map has 74 distinct IDs; changed local Markdown links resolve. No Gradle logic
  changed. The combined verification took **9m9s** and stored its configuration cache.

The existing X76 semantic-token overlap warning at Advanced.x line 43, column 13 was observed
again. It remains the previously recorded L12 follow-up; it is not a header-completion failure.

```bash
./gradlew :xdk:installDist spotlessCheck --console=plain
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook :lang:intellij-plugin:compileIntegrationTestKotlin :lang:intellij-plugin:ktlintCheck spotlessCheck --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The first parser-only assertion incorrectly expected parent links before AST adoption; its clone
check now uses the declaration root, matching the existing parser contract. No production
workaround was needed.

The next compiler scope at that checkpoint was class/interface/type-composition headers;
[C23/L39](#class-and-interface-composition-headers) records the implementation above. Other unsupported
forms remain explicit follow-ups.

## IntelliJ native assertion parity

This is the historical L38 checkpoint. The [L60 inventory](#intellij-parity-backlog-l60) and
[current native validation](#native-startup-rename-deadlock-and-execution-tracing-2026-09-28)
supersede its coverage counts and focus policy.

Host-only follow-up L38 is in `3c68c2dfe`, independent of C22. The same commit's X94 catalog and
consumer hunks belong to L37. The user requested a background audit
and implementation of the remaining native gaps. Coverage labels describe assertions implemented,
while recorded native runs separately establish runtime evidence. Keep both visible in the report.
At this checkpoint the implemented catalog contained **43 full, three partial and 53 unimplemented** scenarios,
plus START. This adds 11 native cases and completes assertions in 14 previous partials. Integration
compilation and ktlint pass with configuration-cache reuse. Runtime validation is incomplete:
implemented coverage must not be read as a single successful native run.

New native cases: X1, X70, X73, X76–X82 and 7a.9. Strengthened cases: X2, X4, X6, X16, X45–X46,
X83–X85, X88–X90, X92 and 7a.8. The harness reads actual UI state or results already received by
LSP4IJ rather than sending replacement test-only protocol requests. The disposable IDE disables
sole-candidate insertion, automatic completion popups and autosave; shipped plugin defaults stay
unchanged. Remaining partials are X20's LSP4IJ ambiguous-parameter presentation and X81/X82's
unchecked completion Property-kind metadata.

Native checkpoint evidence (both reports have zero IDE internal failures):

- Latest in this checkpoint, after `2e98860e1`: **19 passed, one failed, 27 not-run and 53 not-implemented**, including
  START in the pass count. Report:
  `lang/intellij-plugin/build/reports/compiler-playbook/run-469432121529144568/results.json`.
  X92 passes both incomplete-header variants: Structure retains `Editing`, `damaged` and `later`,
  excludes `hidden`, and the native fold is exactly **1–3**. Repair clears Problems. X18 then
  fails explicitly because the IDE lost focus during its native popup check. The run does not
  establish the remaining 27 implemented cases; X93/X94 still await native execution.
- Earlier expanded run: **28 passed, one partial (X20), one failed, 17 not-run and
  53 not-implemented**. Report:
  `lang/intellij-plugin/build/reports/compiler-playbook/run-2648196190918026067/results.json`.
  This verifies the strengthened constructor candidate/edit checks X83–X85 and X88–X90.
  X92's Structure checks passed, but its fold was **1–4**, exposing the production defect now fixed
  by `2e98860e1`. Keep this evidence separate from the latest run.

Both reports use catalog SHA-256
`a58f0e0c42402e5233741c996a79cd48a22337817c665cd2b45b0c103c1a87b2`.
The harness no longer calls Driver `ensureFocused()`, whose `toFront()` implementation clicks
the title bar and whose input-focus probe injects a key event. Conditional programmatic focus is
limited to completion and Parameter Info; ordinary file opening and caret movement request no
focus. At this checkpoint popup polling failed on focus loss; it now restores focus and reopens
only unapplied inspections while the source is unchanged. Cleanup closes the IDE after failure;
neither recorded failure was an IDE crash.

The planned follow-up was to finish the implemented cases before adding another assertion batch,
then add a reusable single/multiple/empty-target navigation helper followed by X5, X33–X38,
X64–X68 and X72. Other missing families at that time were workspace lifecycle/configuration
(16 cases), hierarchies/stale snapshots (7), hover/tokens/inlays/binary contracts (4), rename/
workspace edits/cross-consumer references (10), and negative-capability/CRLF checks (3). The shared
catalog now implements these cases; L60 records execution evidence. These historical counts were
harness scope, not missing compiler features.

| Future PR | Contents | Prerequisites |
|---|---|---|
| L38 | Native completion-list control, diagnostics/navigation/structure assertions, additional shared-case consumers and honest coverage metadata | L32/L33 and the compiler scenarios each native assertion consumes; independent of C22 |

With L38 the extraction map has **74 groups**. No shipped plugin or compiler behavior is changed
by this test-only work. Community/free IntelliJ functionality remains the requirement.

## Qualified declaration type prefixes

Implemented on `lagergren/errs` in `2478bf7fb`, after pushed checkpoint `7e9511eda`.

- [x] Retain flat dotted type syntax in property/return and method-parameter headers.
- [x] Resolve module/package/class qualifiers and imported aliases through compiler NameResolver.
- [x] Enumerate inherited/nested types and typedefs through contextual TypeInfo; filter inaccessible
  children and hidden qualifier ancestors, while retaining same-owner private access.
- [x] Replace only the final written identifier; keep diagnostics and cached ordinary analysis separate.
- [x] Cover parser ownership/cloning, listener budgets/cancellation, unsaved root/member overlays,
  missing names/delimiters, UTF-16/CRLF stdio and retention.
- [x] Add shared X93, native VS Code/IntelliJ consumers, manual steps and adapter capability updates.
- [x] Record final compiler/LSP/stdio and VS Code verification; native IntelliJ X93 is deferred
  to an occasional checkpoint at the user’s request.

**API/AST placement:** `IncompleteStatement.forDeclarationType` owns the original
`NamedTypeExpression` through its existing target child; the final written token identifies the
replacement range. No new node field, clone rule, compiler component or public result shape is
needed. Ordinary AST adoption/cloning preserves source ownership. `CursorScope` creates stack-local
NameResolvers and uses TypeInfo for candidate discovery/access. It does not validate the retained
type or populate its resolver cache. Existing `CursorBinding.NamedType` and Kotlin copying suffice.
See the [AST inventory](errs.md#ast-changes-for-embedding-and-lsp-ownership-and-placement).

**Bounds:** the cursor must end the final written identifier in a flat qualified name. Empty trailing
`Name.` slots, mid-token cursors, parameterized/compound qualifiers or types, generic-method,
multi-return and type-composition headers remain follow-ups. Qualifiers must resolve to concrete
module/package/class/type identities; values and unresolved names yield no candidates. A still-missing
parameter name or delimiter remains a compiler diagnostic after accepting a type. No source repair,
method scope or AST-level semantic cache is introduced.

| Future PR | Contents | Prerequisites |
|---|---|---|
| C21 | Parser qualified-prefix recognition, syntax factory/ownership, compiler qualifier and visible-child lookup, parser tests and embedding contract | C20 and its cursor/listener foundations |
| L36 | Adapter/API visibility and overlay regressions, stdio/retention controls, shared X93, both native consumers and capability/playbook documentation | C21, L35; catalog L32 and native signature checks L33 |

These add two groups, bringing the extraction map to **71**. Commit `2478bf7fb` contains both
groups; separate compiler and host changes by the responsibilities above during extraction.
Keep C21 and L36 independently buildable on their listed prerequisites when extracting future PRs.

Validation on 2026-09-26:

- Java: **477 executed, 40 existing skips**, zero failures/errors. All 18 parser recovery cases
  execute, including qualified target ownership and independent cloned syntax.
- LSP: **1,041 executed, three existing skips**, zero failures/errors. All 22 new qualified-header
  adapter/API cases execute; the preceding 19 header cases remain green.
- Packaged stdio: **45 passed, zero skips**, including qualified edits after an emoji with CRLF.
- Retention: **120 cycles, 960 edit requests, 2,450 weak references, zero retained**;
  rebuild p50/p95 **216/234 ms**, including debounce. This bounded workload is not an SLA.
- VS Code: **98 passed**, zero failures/errors, including X93. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-1FeJMb/results.json`.
  Catalog SHA-256: `b8be9aec409b39a5264efd67a829fb8223a4b98ffe9e01bf6a8e7fa7cabd1c02`.
- IntelliJ: the integration driver compiles and passes Kotlin checks. **Native execution was not
  run for this slice**, per the requested occasional-checkpoint cadence. X93's native assertions
  are implemented but unverified; the last native execution at that checkpoint covers X1–X92 as
  recorded below. The catalog then declared 19 full, 15 partial and 64 unimplemented cases, plus
  startup; see [native assertion parity](#intellij-native-assertion-parity) for later evidence.
- XDK preparation, root `spotlessCheck`, TypeScript/Kotlin checks and `git diff --check` pass.
  Added local Markdown links resolve; the 15 canonical manual fixtures remain unique and the
  extraction table contains all 71 distinct groups. No Gradle configuration changed.

The first editor run (`run-xjcQxP`) passed X93 but failed X53–X55 and X57–X60 while manual
navigation overlapped the scripted UI. With the window left to the harness, all seven passed
without production or test-code changes. This supports treating those failures as UI interference;
it is not evidence that every possible navigation race has been audited. The manual playbook now
explains that active-editor actions require exclusive control during automation. Production stale
result and version checks remain unchanged.

```bash
./gradlew :xdk:installDist spotlessCheck --console=plain
./gradlew :lang:intellij-plugin:compileIntegrationTestKotlin :lang:intellij-plugin:ktlintCheck :lang:vscode-extension:testCompilerPlaybook spotlessCheck --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The preceding combined run executed the full Java, LSP and stdio suites before the first editor
run. Its queued IntelliJ task never started. The final command above took **2m41s**, reused the
passing host results, executed VS Code and compiled the changed IntelliJ driver. Its configuration
cache was stored; no build logic changed. Only documentation was finalized afterward. No CI run
was consulted.
Next: parameterized/compound type headers, then class/interface/type-composition headers. Native
IntelliJ outline/folding and other declared parity gaps remain separate host-driver work.

## Unfinished declaration headers

Implemented on `lagergren/errs` in `68a291c0f`, after pushed checkpoint `b17a9adec`.

- [x] Preserve a written method name and source extent when its parameter header is malformed.
- [x] Retain simple unqualified property/return and method-parameter type prefixes, including
  empty parameter type slots, without fabricating names or signatures.
- [x] Resolve type candidates through the real enclosing compiler scope: implicit imports,
  explicit/wildcard imports, aliases, nested types, typedefs and shadowing.
- [x] Preserve following declarations and folds; discard body declarations and stale semantics.
- [x] Add parser diagnostic/budget/cancellation/clone controls, embedding ownership checks,
  adapter overlay invalidation, UTF-16/CRLF stdio and retention coverage.
- [x] Add shared X91–X92, both editor consumers and manual steps. At this checkpoint IntelliJ X92
  checked diagnostics, Problems rows and repair; the later [native parity work](#intellij-native-assertion-parity)
  adds and verifies its Structure and exact folding assertions.
- [x] Record final Java/LSP/stdio/editor verification.

**API/AST placement:** `IncompleteDeclarationStatement` is syntax, not a compiler component.
It owns the written name (if present), declaration kind, original source range and selected cursor
child. Metadata is final. The child list uses the existing AST adoption/clone mechanism so each
attempt/clone owns its children; there is no new cache or compiler `Context` field.
`IncompleteStatement.isTypeCompletion()` derives its answer from parentage. The enclosing real
class supplies name resolution through `CursorScope`/`NameResolver`; no partial method/property,
parameter register, synthetic declaration name or method context is registered. Existing
`CursorBinding.NamedType` facts suffice. Kotlin copies those facts and retains outline/fold ranges.
The [AST inventory](errs.md#ast-changes-for-embedding-and-lsp-ownership-and-placement) records all hooks.

**Bounds:** this first header slice does not complete qualified/compound types, method type
parameters, generic-method headers, type-composition/extends headers or parameter/declaration
names. Return types in multi-return lists are outside this path. A malformed method body is
skipped as source extent, not exposed as valid members. Recovery stops at a body, semicolon,
enclosing brace or EOF; headers with no separating boundary can still consume following syntax.
Nested/default header expressions containing their own braces are not a recovery guarantee.
Normal compilation still reports the malformed header; cursor probes leave published diagnostics
unchanged, and neither path emits the incomplete declaration.

| Future PR | Contents | Prerequisites |
|---|---|---|
| C20 | Parser header boundaries, IncompleteDeclarationStatement, derived cursor kind, CursorScope name resolution, parser tests and embedding contract documentation | Existing C12/C14 cursor/listener foundations and C17 declaration recovery |
| L35 | Copied type-only completion, outline/folding, adapter/embedding/stdio/retention tests, shared X91–X92, both editor consumers and docs | C20, L32; native empty-signature assertions use L33 |

These add two groups to the extraction map, bringing it to **69**. Commit `68a291c0f` contains
both groups; split compiler and host changes by the responsibilities above during extraction.
Each extracted PR must pass on its own prerequisites.
C19/L34 remain the preceding independent array-dimension slice in `0b800c392`.

Validation on 2026-09-25:

- Java: **477 executed, 40 existing skips**, zero failures/errors; all 18 parser recovery cases execute.
- LSP: **1,019 executed, three existing skips**, zero failures/errors; all 19 header adapter/API cases execute.
- Packaged stdio: **43 passed, zero skips**, including header edits after an emoji with CRLF line endings.
- Retention: **120 cycles, 960 edit requests, 2,441 weak references, zero retained**;
  rebuild p50/p95 **213/223 ms**, including debounce. This bounded workload is not an SLA.
- XDK distribution preparation and root `spotlessCheck` pass. No Gradle files changed;
  the corrected combined validation run reuses its configuration cache.
- VS Code: **97 passed**, including X91–X92. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-mLsWNJ/results.json`.
- IntelliJ: **19 passed including START, 15 partial, 64 not implemented**, zero failures,
  not-run cases or IDE failures. X91 checks native candidates, absent parameter hints and exact
  acceptance; X92 checks diagnostics, Problems rows and clearing. Report:
  `lang/intellij-plugin/build/reports/compiler-playbook/run-17455356535475190797/results.json`.
  Native JUnit: one executed test, zero failures/errors/skips; Ultimate remains disabled.
- Both reports contain identical scenario IDs and SHA-256:
  `6f4b5b9939beb173f00cbeada93ae0e2aabf53e2e44a1efd7820928a6e84102c`.
- Kotlin/TypeScript checks pass. Added local Markdown links resolve and `git diff --check` passes.

```bash
./gradlew :xdk:installDist spotlessCheck --console=plain
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook :lang:intellij-plugin:testCompilerPlaybook spotlessCheck --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The successful combined run took 10m56s and reused its configuration cache. Java tests executed
successfully in the preceding run and were up to date on the final run; LSP, stdio and both editor
tasks executed. The first stdio run found a test assertion dereferencing the valid null result
for a header's signature help; that assertion was corrected before the successful run.
Only documentation and constructor continuation whitespace were finalized afterward. No CI run
was consulted and no Gradle configuration changed.

Next after this bounded slice: qualified/compound and type-composition header recovery, with
compiler access/type-formal resolution controls before expanding completion. Native IntelliJ
outline/folding and other declared parity gaps remain separate host-driver work.

## Array dimension cursors

Implemented on `lagergren/errs` in `0b800c392`, after checkpoint `3f46568af`.
`ec85fcb98` and `3f46568af` contain the preceding native-signature implementation and commit map.

- [x] Retain empty/final-name size slots and a missing `]`, without changing source text.
- [x] Fit the actual fixed-size Array constructor's `Int size` parameter; exclude unrelated
  copy/mutability overloads using the declaration behind specialized TypeInfo methods.
- [x] Reuse compiler readability, flow narrowing, properties, substitutions and argument fitting.
- [x] Isolate the type parser's dimension lookahead diagnostics; the owning parse reports the hole.
- [x] Add parser ownership, embedding cancellation, adapter, stdio and retention controls.
- [x] Add shared X90 and both editor consumers; advertise `[` for compiler signature help.
- [x] Record final Java/LSP/protocol/editor validation below.

**API/AST placement:** no new public API shape, AST field or clone obligation. The existing
`IncompleteStatement` target, argument children, source token and `isCall()` represent dimension
syntax. Ordinary indexing remains distinct. Type lookahead and delimiter recovery belong in Parser;
constructor selection and argument fitting remain in `PartialConstructionResolver`. Kotlin uses
the existing copied signature/value facts unchanged. See the [AST inventory](errs.md#ast-changes-for-embedding-and-lsp-ownership-and-placement).

**Bounds:** missing brackets recover at statement/outer-delimiter boundaries. A following supplier
after a written `]` is parsed to preserve the cursor but is outside its prefix proof.
Normal compilation still validates that supplier and rejects omitted defaults (including String).
Multidimensional construction stays unsupported. Prefixes before later dimensions and compound/member
expressions keep ordinary scope/member completion, without size filtering. No literal values,
operands or declaration names are invented. The following header slice is recorded above.

| Future PR | Contents | Prerequisites |
|---|---|---|
| C19 | Parser dimension lookahead/recovery, IncompleteStatement delimiter interpretation, constructor identity filter, ParserRecoveryTest, embedding contract documentation | C16 and the existing C12/C14 cursor/listener foundations |
| L34 | Array adapter/embedding tests, stdio/retention controls, bracket trigger, shared X90 and both editor consumers, docs | C19, L29, L32; native signature assertions also need L33 |

These add two groups to the extraction map, bringing it to **67**. Commit `0b800c392` contains
both groups; split it by the responsibilities above during extraction. Do not transplant the integrated
green status to either extracted PR: each must pass on its own prerequisite stack.

Validation on 2026-09-25:

- Java: **474 compiler tests executed, 40 existing skips**; **118 utility tests executed,
  two existing skips**. Zero failures/errors. The parser suite includes a non-deduplicating
  listener assertion, independent cloned children and preserved following declarations.
- LSP: **1,000 tests executed, three existing skips**, including all **14 array cases**.
  Packaged stdio: **41 passed, zero skips**. Zero failures/errors across both tasks.
- Retention: **120 cycles, 960 edit requests, 2,431 weak references, zero retained**.
  Rebuild p50/p95: **213/226 ms**, including debounce; this is a bounded workload, not an SLA.
- VS Code: **95 passed**, including X90. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-7c1NT0/results.json`.
- IntelliJ: **18 passed including START, 14 partial, 64 not implemented**; zero failed/not-run
  cases and zero IDE failures. X90 verifies native parameter rendering, exact acceptance and
  diagnostic repair; its unobserved candidate-list assertion is explicitly partial. Report:
  `lang/intellij-plugin/build/reports/compiler-playbook/run-12473595055646003979/results.json`.
- Both reports contain identical scenario IDs and SHA-256:
  `3259c90c684294b5cc6fbfccdb29fccd4cdea4ff41580f6eff419172ad89b44b`.
  The native JUnit suite has one executed test, zero failures/errors/skips.
- Kotlin/TypeScript checks and root `spotlessCheck` pass. No Gradle files changed; the parser
  check reused its configuration cache and the combined run stored its task graph. The existing
  X76 semantic-token overlap warning at Advanced.x line 43, column 13 remains an L12 follow-up.

```bash
./gradlew :xdk:installDist --console=plain
./gradlew :javatools:test :javatools_utils:test :lang:vscode-extension:testCompilerPlaybook :lang:intellij-plugin:testCompilerPlaybook spotlessCheck --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The second command completed successfully in 10m54s. These test tasks executed; the counts above
come from their XML/JSON reports. Only documentation was finalized afterward.

## Native IntelliJ signature help

Implemented on `lagergren/errs` in `ec85fcb98`, after `4dfbeff2e`. This is the first item
in the agreed order, before array-dimension cursors and unfinished declaration headers.

- [x] Drive X15–X20 using the shared overload, named-argument, generic, rejection and receiver inputs.
- [x] Check constructor signatures for X74, X83–X85 and X88–X89, including specialized,
  omitted-type, array-initializer and anonymous construction with missing closers.
- [x] Read the future created by the native Parameter Info action, then assert the visible
  parameter text and bold argument. Empty results require a new completed native request.
- [x] Leave a valid popup open before each rejected call and check that the stale hint disappears.
- [x] Use IntelliJ read actions for PSI inspection and scroll the caret into view before opening hints.
- [x] Complete final native/VS Code validation and record the reports below.

Coverage becomes **30** feature scenarios plus startup: **17** full, **13** partial, and **64**
not implemented. X16/X20 declaration navigation and X83–X85/X88–X89 completion acceptance remain
explicitly partial. The catalog records exact missing checks, without claiming unsupported IDE
features. Tests still require Ultimate to remain unloaded throughout.

**Observed client limitation:** in X20 the compiler retains the full signature label and sends
an empty parameter list to avoid the protocol's default-to-zero highlight. LSP4IJ 0.21.0 renders
this as `<no parameters>`. Its pinned `LSPParameterInfoHandler.updateUI` uses only parameter
metadata, not the signature label. The driver checks the response and absence of a bold argument;
it does not call this correct label presentation. A future client/upstream fix must preserve the
signature label without inventing a parameter. Normal named-argument highlighting works.

All changes are test infrastructure and documentation; no compiler, embedding, AST or production
plugin hook is added, and no dependency or Gradle change is required.

| Group | Scope | Prerequisites |
|---|---|---|
| L33 | Native Parameter Info action, response/popup checks, shared coverage updates and documentation | L32 and its prerequisite stack |

The extraction map now has **65** groups. Keep this change as an additive follow-up to L32;
`ec85fcb98` contains the implementation. `019d3f811` remains the shared-catalog implementation
and `4dfbeff2e` its commit-map checkpoint.

Validation on 2026-09-25:

- VS Code: **94 passed**, `lang/vscode-extension/build/reports/compiler-playbook/run-ZCoHww/results.json`.
- IntelliJ: **18 passed including START, 13 partial, 64 not implemented**, zero failed/not-run
  entries and zero IDE failures. Report:
  `lang/intellij-plugin/build/reports/compiler-playbook/run-6920611126834570657/results.json`.
- Both reports have identical IDs and catalog SHA-256:
  `a14a909fad947d1d96828e0c95dbd76e9bd661c642703f0e67b6f3ee6ee3e40d`.
- The VS Code task's prerequisites reran: **986 LSP tests executed, three existing skips**, and
  **38 packaged compiler stdio tests**, zero failures/errors. IntelliJ JUnit XML reports one
  executed suite, zero failures/errors/skips. Kotlin checks and root `spotlessCheck` passed.
- The native task reused its configuration cache on the preceding successful run; the final
  combined command stored its new task graph. No Gradle files changed. Shared source inputs,
  expected results and manual notes are identical to L32; only IntelliJ coverage metadata changed.
- The existing VS Code semantic-token overlap warning at Advanced.x line 43, column 13 remains
  recorded under L12; it is separate from signature help. Markdown file links and `git diff --check`
  pass. Test-only PSI access and off-screen-caret failures found while building the driver were
  corrected before these successful runs.

```bash
./gradlew :lang:vscode-extension:testCompilerPlaybook :lang:intellij-plugin:testCompilerPlaybook spotlessCheck --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

Order at the L33 checkpoint (array dimensions are now implemented above):

1. Completion/signature fitting inside single-dimensional array brackets; add compiler/API,
   adapter and editor controls. Multidimensional construction remains outside the proven compiler scope.
2. Bounded recovery of unfinished declaration headers, with source ownership and diagnostic controls.

Native parity follow-ups remain: X20 label presentation; completion acceptance for the constructor
cases; function-value/nested-call parameter hints; type/implementation lookup, references/rename,
hierarchies, tokens/inlay hints, and lifecycle cases. These are separate from new compiler API work.

## Shared editor scenario data

Implemented on `lagergren/errs` in `019d3f811` (L32). The preceding C18/L31 checkpoint was
committed and pushed first: `cd1732d42` implements anonymous constructors; `c7976f970` maps them.

- [x] Move all 94 scenario definitions into `lang/test-fixtures/compiler-playbook/scenarios.json`:
  X1–X89, CFG1–CFG3 and 7a.8–7a.9, including titles, edits, anchors, variants, expected results,
  fixture selectors, module graphs and remaining manual checks.
- [x] Make VS Code consume the catalog without removing its existing provider/protocol assertions.
  A type-only JSON import checks field names and shapes during TypeScript compilation.
- [x] Extend the native IntelliJ driver from seven feature scenarios to eighteen; keep startup
  separate. Reuse the same completion/scope/import/recovery inputs and expectations.
- [x] Report every IntelliJ case: `passed`, `partial`, `not-implemented`, `not-run` or `failed`.
  Each partial/unimplemented entry names the missing native assertions. A driver gap is not a
  claim that IntelliJ or LSP4IJ lacks that LSP capability.
- [x] Open IntelliJ's Problems tool window for X2 and 7a.8, select Current File, verify visibility,
  compare row locations/counts with the checked editor diagnostics, and verify clearing.
- [x] Declare the catalog as a Gradle input for TypeScript compilation and both playbook tasks;
  include its IDs and SHA-256 in both reports so runs against different data are distinguishable.
- [x] Finish the final editor replay and configuration-cache verification after Problems integration.

Source programs still come from the manual playbook; selectors live in the catalog. A `§` marks a
cursor offset. Variant rows have named columns, and `${0}` substitutions insert literal text;
there is no shared executable scripting language. Editor actions and protocol handling stay in
TypeScript/Kotlin. The original seven common edit/anchor checks reject missing or ambiguous
matches; both drivers also reject catalog/manual ID drift and missing native registrations.

At the L32 checkpoint, IntelliJ covered X7–X13, X71, X75, X86–X87 and CFG1 fully at the scenario assertion level (**12**),
and X2, X4, X6, X45–X46 and 7a.8 partially (**6**), plus START. The remaining **76** entries are
explicitly unimplemented. Next native work is signature/active-parameter inspection, type and
implementation choosers, references/rename, hierarchy, semantic tokens/inlay hints, and lifecycle
or raw-protocol cases. Each exact gap is recorded beside its shared scenario. Visual layout,
physical key use, Problems-row clicking and long editing sessions remain manual where listed.

No production server, plugin, compiler, embedding API or AST behavior changes. All new Kotlin
remains in `integrationTest`; the test still asserts that Ultimate stays unloaded. No dependency
is added. The JSON is not bundled into the production extension.

| Group | Scope | Prerequisites |
|---|---|---|
| L32 | Complete shared catalog, native consumers, explicit coverage reports, Problems checks, task inputs and playbook docs | L16, L27 and L31 with their prerequisite stacks |

L32 brought the extraction map to **64** groups. Shared data is complete; native IntelliJ parity
is explicitly incomplete. Keep L32 together when extracting this test-infrastructure PR. Its full
catalog reaches X89, so extraction follows L31 and its prerequisite compiler/adapter stack;
sharing data adds no new production API requirement.

Initial expanded validation on 2026-09-25: all 94 VS Code cases passed in
`lang/vscode-extension/build/reports/compiler-playbook/run-DmL7Ao/results.json`. IntelliJ startup
and all eighteen implemented/partial scenarios passed in
`lang/intellij-plugin/build/reports/compiler-playbook/run-12241983404891814641/results.json`,
with Ultimate disabled and no IDE failures.

Final replay, including Problems row checks and the completion-helper cleanup: all 94 VS Code
cases passed in `lang/vscode-extension/build/reports/compiler-playbook/run-rQuTm3/results.json`.
IntelliJ startup and all eighteen implemented/partial scenarios passed in
`lang/intellij-plugin/build/reports/compiler-playbook/run-7796454822837157094/results.json`:
13 `passed` entries including START, six `partial` entries, 76 `not-implemented` entries, zero
failed/not-run entries and zero IDE failures. Both reports identify the same catalog SHA-256,
`8098316aadfbde0ade8bdf32cc35fdf1b4d6eb2cc3c2cf4dfa8f49868a98ef72`. Gradle reported
`Configuration cache entry reused`. TypeScript compilation and Kotlin checks passed; review
confirmed that all 94 VS Code bodies retain their pre-migration assertion calls. Markdown file
links, `git diff --check` and the pre-push root `spotlessCheck` passed. Host/compiler tests were
up-to-date in this final replay;
this test-infrastructure change does not claim another fresh host-test run.

Command (repeat with `--info` for configuration-cache evidence):

```bash
./gradlew :lang:vscode-extension:testCompilerPlaybook :lang:intellij-plugin:testCompilerPlaybook --max-workers=1 -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

## Anonymous constructor cursor support

Implemented on `lagergren/errs` in `cd1732d42`, after `185ff84b1`.
This completes the next constructor API investigation before array-dimension
cursors and unfinished declaration headers.

- [x] Prepare the anonymous declaration's existing source-owned class shell for cursor lookup.
- [x] Fit constructors declared in the body and accessible superclass constructors, including
  generic, annotated and abstract bases and interface implementations.
- [x] Preserve required-type inference, named arguments, original edits and body capture syntax.
- [x] Exclude the class shell's provisional default from superclass signature suggestions; retain
  the real zero-argument default for interface implementations.
- [x] Display the written construction type for anonymous constructor signatures.
- [x] Add source ownership, non-emission, access, invalidation, cancellation and recovery controls;
  extend retention, packaged-stdio and manual/automated cases X88–X89.
- [x] Complete backend/editor verification and record the results below.

`PartialConstructionResolver` uses `NewExpression.prepareConstruction` on the actual anonymous
syntax of the **partial-analysis attempt**. That is necessary because class preparation attaches
components to the enclosing method: a detached expression clone would leave a component without
source ownership. The existing `anon` child owns the prepared declaration. Normal constructor
probes still use clones, and argument fits still use cloned arguments and discarded child contexts.
No AST field, cloning rule, collector component or public API signature changes are needed.

Preparation resolves class shape and constructor signatures. It stops before forwarding-constructor
creation, capture analysis and bytecode emission. The source method remains incomplete, so the
normal emission gate rejects it. Candidate identities refer either to the retained source-owned
class or its superclass; Kotlin copies them during the worker-owned attempt as before. The host
gets no compiler context or generated capture parameters. Written anonymous methods can still
capture locals when the repaired document goes through normal compilation.

Private/protected access comes from the compiler's prepared target TypeInfo. A base may be abstract
or an interface if the retained anonymous body supplies its required members. An unimplemented
abstract body, unavailable type, incompatible written argument or invalid argument label yields no
constructor suggestions. Both own and superclass possibilities remain provisional while arguments
are missing; normal validation chooses the constructor later. Signature fitting does not claim
that every expression in the retained body is valid or that capture analysis has completed.

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C18 | `PartialConstructionResolver` anonymous ownership and constructor targets; embedding contract documentation | C16 and existing cursor/listener foundations |
| L31 | Written constructor labels; anonymous/specialized adapter controls, retention, stdio and X88–X89 | C18, L29/L30 and L16 |

These bring the extraction map to **63** groups. Keep both on the integrated errs branch; each
future extraction must pass with its listed prerequisites. No extracted-PR validation is claimed.

**Constructor limits at this checkpoint:** array-dimension slots (now covered by C19/L34 above)
and multidimensional construction.
The latter is not implemented by the ordinary compiler either. Anonymous bodies must be retained
by the parser; this does not repair arbitrary body errors or infer missing declaration names/types.
The next step was bracket argument fitting. Bounded declaration-header recovery remains next now.

**Validation:** the 22 new anonymous and 18 specialized constructor tests pass with
no skips. All 473 executed Java tests pass (40 existing skips), as do Kotlin/TypeScript checks and
root `spotlessCheck`. The full LSP suite passes all 986 executed tests (three existing skips).
The retention workload covers 120 cycles/960 edit requests and releases all 2,421 observed objects;
rebuild p50/p95 was 219/280 ms, including debounce, on this contended machine.

All 38 packaged-stdio tests pass on a sequential retry. The initial run had one initialization
timeout in an existing array-supplier case, before opening a document, under heavy machine load;
that failure is preserved under
`lang/lsp-server/build/reports/anonymous-constructor-first-stdio-xml/`. The first editor run
(`run-VALAMC`) passed X88/X89 but exposed an edit-anchor collision with X77/X79. The anonymous
fixture now uses a distinct first argument. All **94 editor cases** pass without skips in
`lang/vscode-extension/build/reports/compiler-playbook/run-jkg2Y4/results.json` (VS Code 1.139.1).
The final invocation reused the successful backend checks and Gradle configuration cache; it
recompiled TypeScript and reran every editor case. XML evidence is under
`javatools/build/test-results/test/` and `lang/lsp-server/build/test-results/{test,compilerStdioTest}/`.
These are extension-host/provider assertions; visual appearance and physical interaction remain
the manual portion of the playbook. IntelliJ's separate eight-case suite was not rerun for this slice.

Separate presentation follow-up under L12: the successful editor run logged an overlapping
semantic-token warning at line 43, column 13 during X76. Reproduce the token ranges for that
incomplete-call edit and add a non-overlap assertion; the existing editor assertions do not check it.

## Specialized constructors and declaration/literal cursor recovery

Implemented on `lagergren/errs` in `46d6c1442`, after the pushed property checkpoint `ace732d5c`
and map update `cd4ad0d64`. Keep implementation on this integrated branch until the playbook
checkpoint is accepted.

- [x] Reuse normal constructor preparation for qualified/implicit inner classes, virtual `new`,
  annotated types and formal constructors.
- [x] Carry expected assignment/return types without storing them on the AST; infer provisional
  class parameters from written constructor arguments.
- [x] Fit parenthesized array initializers after their written dimensions, including named suppliers
  and element types without a default value.
- [x] Retain tuple, typed-tuple, collection and map closers around a cursor hole.
- [x] Retain expression-bodied declarations and property initializers missing a terminator, and
  method/shorthand-constructor defaults missing `)` before their body.
- [x] Validate incomplete property initializers in their source-owned context instead of publishing
  facts from disposable constant-evaluation clones.
- [x] Add source/clone ownership, no-emission, rejection, cancellation, overlay and retention controls;
  extend protocol coverage and manual/automated editor cases X83–X87.
- [x] Complete the expanded backend and editor validation and record measured results below.

`NewExpression.prepareConstruction` factors the existing validation prefix into a stack-owned
result record. Normal compilation calls the same code. The partial resolver clones syntax and
enters discarded child contexts, then uses the existing constructor argument fitter. A pending array
supplier suppresses only the premature “element has no default” check; dimension arguments still
must fit. Required-type inference constrains candidates. Argument-derived inference is provisional:
`new Box("x", ...)` can display String parameters yet accept an Int argument if the ordinary compiler
accepts it and revises the omitted class type. Tests compile each accepted insertion to verify this.

`IncompleteExpression` passes its required type through a callback-based `Statement.validate`
overload to `IncompleteStatement`; the callback is synchronous and never retained. This preserves
the existing statement context/break lifecycle. `getLeadingArguments()` exposes the constructor's
existing dimension children; Kotlin copies their ranges and an argument offset, so `[2](...)` starts
at constructor parameter 1. There are no new Java record components or mutable AST fields.

Declaration/literal recovery extends Parser's existing zero-width closing markers and keeps the
original tokens, source text and later declarations. It requires an explicit owned cursor hole;
ordinary parsing, error budgets and speculative rollback remain strict. A property initializer
containing that hole cannot become a constant. During explicit cursor analysis it uses the existing
source-owned initializer path directly, with the attempt's collectors. Its failed validation prevents
emission. The normal constant-evaluation clone path is unchanged. This also reaches shorthand
constructor defaults that pass through generated property initialization.

**Remaining limits at this checkpoint:** anonymous-class construction was deferred and is now
implemented by C18/L31 above; C19/L34 adds single-dimensional size cursors. Multidimensional
construction, unfinished declaration names/types, missing operands/map entries,
unterminated literal contents and arbitrary delimiter repair remain unsupported. No literal value
is synthesized. Existing final-slot argument-completion restrictions, enclosing-instance enumeration,
receiver-to-argument rewrites and project discovery/indexing remain separate work. Tree-sitter is
still the shipped default; compiler mode remains Java-only.

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C16 | Constructor Parser hunks; `NewExpression` preparation; `PartialConstructionResolver`; required-type threading through Statement/Incomplete nodes; leading-dimension accessor; parser constructor controls | C11, C13–C15 and existing cursor/listener foundations |
| L29 | Kotlin dimension mapping, specialized-constructor tests, constructor protocol/retention cases and X83–X85 | C16, L23/L25/L26/L28, L16 editor runner |
| C17 | Declaration/tuple/literal Parser hunks; `IncompleteStatement.isWithin`; source-owned property initializer handling; embedding contract docs and parser recovery controls | C12 and the existing cursor collectors; independent of C16's constructor semantics |
| L30 | Literal/declaration adapter tests, recovery protocol/retention cases and X86–X87 | C17, L24 and L16; shared fixtures must be split when extracting |

These add four groups to 57, bringing the future extraction map to **61**. Parser,
IncompleteStatement, protocol, retention and playbook files contain hunks from both slices; do not
cherry-pick the mixed commit `46d6c1442` as an independent PR. Each extracted group still needs its
own prerequisite build and tests. The integrated branch's results do not establish that.

**Backend verification (2026-09-25):** JUnit XML reports 513 Java tests (473 executed, 40 existing
skips), 967 LSP tests (964 executed, three existing skips) and 36 packaged-stdio tests, with zero
failures/errors. All 36 specialized-constructor and literal/declaration cases executed. Full LSP XML
was preserved in `lang/lsp-server/build/reports/constructor-recovery-full-xml` before the focused
18-case recovery rerun added exact boundary-diagnostic and non-emission assertions; that rerun also
passed without skips. The 120-cycle/960-edit retention workload, including array and literal probes,
tracked 2,402 weak references and retained zero; rebuild p50 was 213 ms and p95 223 ms including
debounce. These are bounded measurements, not an interactive soak or a cursor-latency guarantee.

All **92 VS Code editor cases** passed with zero failures/skips on VS Code 1.139.1, including
X83–X87. Report: `lang/vscode-extension/build/reports/compiler-playbook/run-K32lGT/results.json`.
The fixture's new constructor class is named `Envelope` to distinguish it from the existing
`Outer` delegation class. No compiler implementation changed after the full green backend run.
The successful final command took 3m 14s and reused the focused 18-case recovery result; its report's
host test section therefore lists those 18 cases, while the separate full XML above records all 967.
All 36 packaged-stdio tests passed again. TypeScript compilation, Kotlin checks and root
`spotlessCheck` passed. Editor automation checks provider behavior and edits; visual presentation
and a prolonged interactive soak remain manual.

```bash
# Full backend results; the first editor attempt exposed the duplicate fixture name.
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
# Focused declaration assertions, then the complete corrected editor playbook.
./gradlew :lang:lsp-server:test --tests '*XdkLiteralRecoveryTest' :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

## Property and constant argument completion

Implemented on `lagergren/errs` in `ace732d5c`, following `e1b9eed49`. This resumes the
compiler/LSP completeness work after the IntelliJ checkpoint.

- [x] Fit implicit property/constant reads in the existing empty and typed argument slots.
- [x] Preserve accessibility, instance/static context, shadowing, generic substitution and conversions.
- [x] Copy immutable identity/type facts without AST fields or retained validation contexts.
- [x] Cover bundled module properties, unsaved inherited types, overload alternatives and invalid narrowing.
- [x] Extend protocol, cancellation/retention coverage and manual/automated playbook X81–X82.
- [x] Pass the expanded backend, packaged-stdio and editor validation.

`PartialCallResolver` enumerates the current receiver's compiler-composed properties, then validates
each proposed name as a normal read in a discarded child context. The same method/function argument
fitter used for local proposals decides whether insertion fits a candidate. No property is modeled
as a fake register. Read validation supplies the actual identity and value type, including inherited
generic substitution; Kotlin copies these into the existing partial model. Formal type properties
retain their type-parameter kind. Static contexts enumerate constants only, and even unreadable
locals shadow properties. The proposal does not modify written arguments or select an overload.

The additive Java API is `CursorBinding.Property(name, identity, type)` plus the immutable
`argumentProperties` component and `withArgumentProperties`. Three-, six-, seven- and eight-argument
constructors remain; current record patterns have nine components. Existing `argumentValues`
continues to mean accepted locals/parameters. All facts belong to the explicit cursor attempt and
are copied on the compiler worker before entering the host. **No AST node, parser hook, mutable
semantic cache or clone/reset rule changes in this slice.** The helper belongs beside compiler
validation because only the live Context can authoritatively resolve and fit these names.

TypeInfo inspection reports through the explicit host listener. Speculative read/fitting errors
stay private, but collecting probe listeners retain failure state and cancellation. Existing normal
diagnostics remain visible until an accepted edit triggers ordinary recompilation.

**Compiler behavior established by this pass:** module `simpleName`/`qualifiedName` from the bundled
XDK are valid String argument suggestions, alongside user members. Ordinary property reads do not
receive local-variable flow narrowing: `Object value` still fails a String parameter after
`if (value.is(String))`. The completion test checks the ordinary `COMPILER-150` rejection and offers
no invalid value. This slice does not change the language's narrowing rules.

**Limits:** enumeration covers the current receiver and its inherited properties, not arbitrary
enclosing-instance chains or imported constants. Existing final-slot/prefix restrictions remain.
Literal synthesis, compound/grouped argument fitting and slots before later arguments remain follow-ups.
Specialized construction is covered by C16/L29 above. Tree-sitter remains the shipped default;
compiler mode stays Java-only.

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C15 | `CursorBinding` property facts/compatibility; `PartialCallResolver` read and argument probes | C13/C14 and the existing cursor/type-info foundations |
| L28 | Kotlin fact copying, adapter/protocol/lifecycle controls, X81–X82 and capability/ownership docs | C15, L25/L26; editor runner L16 |

These add two groups to the prior 55, bringing the working plan to **57**. Keep the compiler and
host hunks from `ace732d5c` separate during extraction.
Each extracted PR still needs independent validation against its own prerequisites.

**Backend verification (2026-09-25):** JUnit XML reports 512 Java tests (472 executed, 40 existing
skips), 931 LSP tests (928 executed, three existing skips), and 29 packaged-stdio tests, all with
zero failures/errors. All 19 new property cases, the 36 existing argument cases and 12 cursor
lifecycle cases ran. The retention workload now includes property proposals: 120 cycles/960 edits,
2,402 weak references, zero retained; rebuild p50 216 ms and p95 239 ms including debounce.
These are bounded measurements, not an interactive soak or a completion latency guarantee.

All **87 VS Code editor cases** passed, including X81–X82 and the existing configuration and
diagnostic cases, with zero failures/skips. The report is
`lang/vscode-extension/build/reports/compiler-playbook/run-y5oQPu/results.json`.
The combined command passed in 6m 39s; TypeScript compilation, Kotlin formatting checks and root
`spotlessCheck` passed. A final readability-only cleanup hoists the retention test's unchanged
cursor cases out of its loop; test-source compilation and formatting checks also cover that cleanup.

```bash
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

**Follow-up implemented:** see the specialized constructor and declaration/literal recovery section
above. Automatic project discovery and persistent indexing remain separate work.

## IntelliJ compiler playbook and client configuration

Implemented and validated on `lagergren/errs` in `4e46becb6`, following `156d02687`.
This work adds no Java embedding or AST API. Keep the following future slices separate:

| Group | Scope | Prerequisites |
|---|---|---|
| I8 | IntelliJ IDEA 2026.2.3 / minimum build 262, LSP4IJ 0.21.0 and compatibility documentation | Existing IntelliJ plugin only; independent of compiler API changes |
| L27 | `XtcLanguageClient` configuration delegation, Starter/Driver task and test dependencies, compiler editor cases and playbook instructions | I8 for the pinned test APIs; L16 compiler graph configuration and the existing compiler features exercised by each case |

These add two groups to the prior 53, bringing the working plan to **55**. In the version catalog,
I8 owns the IDE/LSP4IJ bumps; L27 owns Kodein, JetBrains coroutines and Starter reporting dependencies.
Do not cherry-pick a mixed future commit as an independent PR without separating those hunks.

The old client override answered only `xtc.formatting` and returned null for compiler sections.
The new override specializes `findSettings` only for formatting. LSP4IJ owns asynchronous section
lookup, unknown/null entries, live configuration notifications and listener disposal. Its existing
XTC server Configuration JSON supplies nested `xtc.compiler.sourceModules`; these settings are
IDE-wide, not a new project-local configuration service.

Starter/Driver uses the packaged plugin and canonical Markdown fixtures. The first case set is
startup, X2, X4 definition navigation, X7 completion, X45–X46, CFG1 clear/restore and 7a.8.
Full IntelliJ playbook parity, rendered Problems tool-window checks and the interactive soak remain
follow-ups. Tests explicitly require the free Community feature set, with Ultimate disabled;
a developer's paid license must not make an acceptance run pass.

LSP4IJ 0.20 added inline completion/color presentation and improved code actions; 0.21 contains
IDE-freeze, timeout/deadlock, read-action and lifecycle fixes, including IntelliJ 2026.2 support.
Those client improvements do not advertise additional XdkAdapter capabilities. See the
[upstream release](https://github.com/redhat-developer/lsp4ij/releases/tag/0.21.0) and
[IntelliJ run instructions](../lang/intellij-plugin/README.md#compiler-playbook-in-intellij).

Validation on 2026-09-25: all eight IntelliJ editor cases passed in
`lang/intellij-plugin/build/reports/compiler-playbook/run-5890396026268646363/results.json`,
with zero IDE failures. Plugin JUnit XML reports 24 tests, zero failures/errors/skips. The new
manifest regression limits required dependencies to Community platform/Java/Gradle/TextMate
and LSP4IJ. `ktlintCheck`, root `spotlessCheck` and packaged-plugin construction pass. The ZIP's
plugin classpath contains only the plugin JAR: Starter/Driver, Kodein, coroutines and service
messages are not shipped. The bundled server remains isolated in `bin/`.
A second eight-case run passed with configuration-cache reuse; its report is
`lang/intellij-plugin/build/reports/compiler-playbook/run-1579176862811931900/results.json`.

## Recommendation

**Current workflow:** develop, fix, test and commit directly on **`lagergren/errs`**. Record which
commits belong together in future PRs in this plan, including prerequisites and portions of mixed
commits. Preparing separate branches and validating each PR against its own base is a later step,
when submission preparation is explicitly requested. Existing extraction branches and their test
receipts remain historical references; do not keep parallel implementations up to date.

Prefer one future PR group per new implementation commit when practical. Keep each fix with its
regression tests, and record its hash and validation evidence here after committing. If a change
crosses groups, record the file or behavior boundary instead of treating the whole commit as an
independent cherry-pick. Preserve the integrated history; regroup changes during later PR preparation.
The [commit grouping record](#commit-grouping-record) supplements the detailed scope descriptions.
The [PR-to-commit map](#dependencies-and-eventual-landing-order) gives every group's source hashes
and prerequisites. The [consolidation checkpoint](#consolidation-checkpoint-before-the-user-playbook-run-2026-09-24)
accounts for the earlier extraction branches. The integrated compiler extension and scratch
fixtures are now ready for the user to run the playbook; no further extraction is scheduled.

The bounded hardening and editor acceptance passes are complete. The initial scope was reliable
compiler diagnostics and existing LSP features. The subsequently approved
[eighth pass](#eighth-pass-module-sessions-and-hierarchy-2026-09-22) now adds permanent final-TypeInfo
regressions, module sessions with overlays, cross-file navigation and direct type hierarchy.
The [ninth pass](#ninth-pass-java-parser-recovery-2026-09-22) keeps compiler mode Java-only and retains
recovered syntax for structural features. The [tenth pass](#tenth-pass-bounded-incomplete-analysis-2026-09-22)
adds a separate compiler-only probe for intact receivers and arguments in one trailing incomplete
statement. The [eleventh pass](#eleventh-pass-copied-call-and-member-facts-2026-09-23) copies
selected call signatures and bounded receiver-member candidates. Completion/signature requests now
reach the server, including typed member prefixes and calls with existing closing parentheses.
Subsequent passes add scope completion, candidate argument fitting, type/implementation lookup,
static call hierarchy, tokens, hints and the explicit dependency artifact/source host API. Broader
incomplete-source analysis and persistent cross-module indexing remain open.
Bounded local/private-parameter rename is implemented in the follow-up below; L17 adds exact
configured-graph references and ordinary instance-method override rename. Wider member/workspace
rename still has explicit unsupported cases. The numbered
passes below preserve chronology; the PR slices describe eventual integration, not a current work queue.

### Editor acceptance and extraction follow-through, 2026-09-24

- [x] Commit the verified rename/lifetime/API checkpoint: `7b13e0980`.
- [x] Expose explicit source graphs through initialization/options and live editor settings; validate
  strict parsing, atomic replacement, unchanged configurations and stale response rejection.
- [x] Run actual editor diagnostics/navigation/completion/rename/recompilation and repeated editing;
  fix observed failures and distinguish UI observations from automated editor-host checks.
- [x] Freeze the documented embedding/AST contract for this scope and prepare the first independent
  local PR slices. Preserve the integrated branch and verify slices against their own base. Remote
  publication remains separate.

The editor bridge uses `xtcCompiler` initialization options and `xtc.compiler` settings. VS Code
exposes `xtc.compiler.sourceModules` and sends live updates. Relative roots require one workspace
folder; multi-root workspaces use absolute file URIs. Invalid settings retain the previous graph,
late replies (including replies superseded by malformed updates) cannot restore old settings, and
identical graphs preserve existing analysis. IntelliJ now uses LSP4IJ's existing server Configuration
JSON; a dedicated project-local source-graph settings UI remains separate (L27 above).

Editor evidence on 2026-09-24: all eight VS Code extension-host tests passed. The compiler case
exercised settings changes, unsaved dependency rebuilding at an unchanged consumer version,
dependency definition, selected signature help, incomplete-member completion, versioned parameter
rename, silent-capture rejection and close/reopen. Forty error/recovery cycles retained working
hover: median 618 ms and p95 721 ms. This is a bounded editing workload, not a multi-hour soak.
JUnit XML additionally reports 737 LSP cases (three existing skips) and 15 packaged stdio cases
(zero skips), with no failures/errors: 749 executed JUnit cases. A separate visible development-host
check used F2 on a private parameter and verified all three
edits (declaration, body reference, named argument); the fixture was then restored and closed.

The proposed Java embedding/AST contract is frozen for extraction within the bounded POC scope:
the compatibility table and requirements matrix below, together with the AST placement inventory
in `errs.md`, define its ownership and limits. Editor configuration adds no Java or AST API.
Future feature requirements can extend that contract additively; unsupported syntax, wider rename,
workspace indexing and discovery are explicit follow-ups. Each extracted slice must still pass
against its own base, including the applicable migration and output checks.

### First local extraction batch, 2026-09-24

The integrated editor/configuration checkpoint is `6372ba07d`, following the rename/API checkpoint
`7b13e0980`. Both remain local. The first three slices are committed on separate local branches,
each based directly on `4a1eae6f7`; none depends on another slice. The remote base has not been
refreshed, and no branches or PRs have been published.

| Slice | Local branch / commit | Size | Independent validation |
|---|---|---|---|
| I1 | `errs/i1-diagnostic-identity` / `e4c633c4c` | 4 files, +132 / -3 | Five tests, zero skips; `spotlessCheck` |
| I2 | `errs/i2-ambient-pools` / `d4fc67090` | 12 files, +200 / -19 | Five tests, zero skips; full XDK build; `spotlessCheck`; 24 timestamp-normalized modules match the base |
| R1 | `errs/r1-repository-failures` / `6384f2658` | 4 files, +112 / -25 | Four tests, zero skips, including the existing concurrent-scan regression; `spotlessCheck` |

Worktrees are under `build/errs-integration/{i1,i2,r1}`. The detached `base` worktree is restored to
its clean base after comparison. The reference branch remains intact. Extraction deliberately
adapts I1's tests to the original boolean listener API and omits later origin/listener helpers.
I2 combines the two pool fixes, removes a duplicate Javadoc block and uses an import in its test.
R1 omits the unrelated unnamed-catch cleanup and strengthens file-header/payload tests to check
repeated failure and recovery after replacement. Its embedding diagnostics test still belongs to E1.

The focused Gradle runs used `--rerun-tasks --no-build-cache`; JUnit XML confirmed nonzero execution
and no failures/errors/skips. I2 and the unmodified base each built the full XDK. Because emitted
modules contain absolute source paths, the output comparison then applied only I2's production
patch to the same base worktree and rebuilt all 24 modules there. Both sets were read and serialized
with the unchanged base compiler, changing only module creation timestamps to `Instant.EPOCH`.
Module membership and bytes matched exactly; source paths and other metadata were not masked.
Neither build emitted compiler warnings/errors. The local comparison receipt is
`build/errs-integration/comparison/result.json`; the normalization helper and patch are alongside it.

This closes the first extraction batch, not validation of the entire proposed stack. The second
batch below covers I3's compiler-consumer test wiring and C1's listener contract. Keep the deliberate
`boolean log` to `void log` compatibility break explicit. Remote
publication requires separate authorization and a refreshed base/conflict check. A multi-hour editor
soak, dedicated IntelliJ source-graph settings, automatic discovery, broader syntax and workspace
indexing/rename remain follow-ups to the bounded POC.

### Second local extraction batch, 2026-09-24

I3 and C1 are committed on separate local branches, each based directly on `4a1eae6f7`.
Neither requires I1, I2 or R1 to compile and pass its own checks. Worktrees are under
`build/errs-integration/{i3,c1}`; the integrated reference branch remains intact.

| Slice | Local branch / commit | Size | Independent validation |
|---|---|---|---|
| I3 | `errs/i3-compiler-consumer-tests` / `8f3a57305` | 6 files, +175 / -2 | Real compiler consumer: one test, zero skips; configuration-cache storage and reuse; `spotlessCheck` and LSP `ktlintCheck` |
| C1 | `errs/c1-listener-contract` / `d6463091c` | 18 files, +1,100 / -67 | Full javatools suite: 430 cases, 390 executed, 40 existing skips, no failures/errors; full XDK build; `spotlessCheck`; all 24 timestamp-normalized modules match the base |

I3 resolves the existing compiled-module variants into a required test repository. It adds no
archive extraction, installation dependency or binary copying. The fixture fails if required
modules are absent and uses a writable `BuildRepository` ahead of the read-only artifact directories
so `LinkedRepository` can cache loaded modules. Its consumer uses the original embedding API;
production resource bundling and `XdkLibraries` still belong to L1. Root inclusion defaults remain
unchanged. IDE lifecycle attachment is explicit, and IDE publication selection is unchanged.

Compiler-related CI paths now select the consumer test even without a `lang` change. A result gate
requires the expected suite with nonzero execution and no skips, failures or errors. Local negative
controls reject missing XML, zero tests, skipped tests, failures and errors. Workflow validation
adds no actionlint findings relative to the base; the existing ShellCheck findings remain. This
is local workflow validation, not a remote CI run. Later slices must extend the required-suite list
when they introduce further compiler consumers.

C1 includes the reporting/state contract, source sites, severity helpers, named budgets and silence,
branch/merge behavior and tee forwarding. Its 25 new listener/migration tests and the existing 24
launcher error-handling tests all execute without skips. A migration regression checks that both
legacy structure reports and new `Site.At` reports still acquire the branch's source location.
The legacy structure overload delegates through the new site API for that purpose. Legacy positional
reporting, `BLACKHOLE` and the existing null/ambient listener policy remain until C2/C4; parser scope
ownership remains for C3. This avoids importing those later changes just to compile C1.

The standalone C1 README documents the source/binary break, recompilation, collector versus lambda
state, deduplication, budgets, serialized callbacks and the nonthrowing `RUNTIME` policy. An explicit
breaking release/version still needs to be selected before publication. The output comparison
applied only C1 production changes to the same base worktree used for the original baseline and
serialized both sets with the unchanged base compiler. Only creation timestamps changed; the local
receipt is `build/errs-integration/comparison/c1-result.json`. The base source was restored clean.

The full C1 patch also applied cleanly over I3 and passed its forced compiler-consumer run:
one test, zero skips/failures/errors. The receipt is
`build/errs-integration/comparison/i3-c1-result.json`. I3 was then restored to its own clean commit;
the combined check does not introduce a dependency between the two standalone branches.

C2 follows in the batch below. Validate each later slice against its actual prerequisites,
including the required I3 consumer. No remote branches or PRs have been published.

### Third local extraction batch: C2, 2026-09-24

**C2 is committed as `f0b0db3a4` on `errs/c2-explicit-listeners`, based on C1's `d6463091c`.**
Its worktree is `build/errs-integration/c2`. The slice changes 73 files, +821 / -512; the file count
comes from migrating reporting and speculative-fit calls throughout the compiler. It builds and
passes independently of I1, I2, R1, I3 and the later embedding/ownership changes.

C2 requires supplied listeners at embedding, launcher, compiler and staging boundaries, removes
silent null coalescing from validation/reporting paths, and makes each resolution collector supply
its destination. Both in-tree implementations, `SimpleCollector` and `NameResolver`, implement the
required method. Probes use `silent(PROBE)`; incomplete TypeInfo work derives `silence(CASCADE)`
at affected calls without rebinding the caller's parameter. Explicit discards cover callers with
no external diagnostic consumer. The old positional report overloads remain deprecated and retain
C1's branch source anchoring. Production callers now use the site/severity API.

The AST changes belong to compiler fit, conversion and validation decisions shared by all hosts.
They add no persistent AST state or LSP implementation. Existing field names and parser/resolver
ownership are preserved for C3. File/pool ambient reporting, including the fallback in
`XvmStructure.log` and compilation-wide file silence, remains for C4. This is an explicit boundary
of the slice; it does not claim TypeInfo replay or complete failure delivery before C4/E1.

Validation:

- Seven new boundary regressions reject missing listeners before compilation, file access or
  launcher dispatch, check stage-manager destination retention, require a collector implementation
  and prevent AST reporting from silently accepting null. The focused listener/launcher run has
  56 tests, zero skips/failures/errors, including C1's kept-branch, probe and cascade checks.
- Full javatools XML: 437 cases, **397 executed**, 40 existing skips, zero failures/errors.
  The forced run used `--rerun-tasks --no-build-cache`; `spotlessCheck` passed. A temporary Gradle
  init script enabled `-Xlint:deprecation` with the build's existing `-Werror`: production reporting
  has no deprecated calls. The deliberate legacy-overload migration test suppresses that warning.
- The full XDK builds successfully. Applying C1+C2 production changes at the same source path as
  the C1 comparison yields **24 identical modules** after normalizing only creation timestamps
  with the unchanged base serializer. Receipt: `build/errs-integration/comparison/c2-result.json`.
- The complete C1+C2 patch applies over I3 and its required compiler consumer executes successfully:
  one test, zero skips/failures/errors. Receipt:
  `build/errs-integration/comparison/i3-c1-c2-result.json`. Base and I3 source worktrees were restored
  clean after validation. No editor launch or remote CI run was needed.

The extracted `javatools/README.md` records the additional compatibility changes: null rejection,
removal of `BLACKHOLE`/`BlackholeErrorListener`, the now-abstract collector method and removal of
the reporting-listener parameter from `Expression.testFitAsType`. These require the same explicit
breaking release boundary as C1; the release/version is still a publication decision.

C3 follows in the fourth batch below. Remote publication still requires authorization and a
refreshed base/conflict check.

### Fourth local extraction batch: C3, 2026-09-24

**C3 is committed as `e2a481b45` on `errs/c3-reporting-scopes`, based on C2's `f0b0db3a4`.**
The slice changes 18 files: 875 insertions and 158 deletions. Its worktree is
`build/errs-integration/c3`. This is a local extraction from the integrated
`lagergren/errs` reference at `9c432f778`; nothing has been published.

The slice includes parser attempts backed by listener branches, shared parser/resolver `Reporting`
scopes, the grouped `ValidationScope` value, and construction-owned diagnostic buffers for
`EvalCompiler` and `ModuleInfo.Node`. It excludes FileStructure reporting scopes/adoption, TypeInfo
ownership changes, unrelated catch-variable edits and broad listener-field renaming. Successful
`Node.logErrors` forwarding still drains its buffer.

Extraction exposed gaps in the historical implementation that this slice closes:

- All four loop/try validation owners restore their previous context/listener value in `finally`,
  including failed validation and the empty infinite-for early return. The common
  `Statement.validate` context is restored as well. The immutable pair remains on the statement
  because lazy label variables and jump callbacks need that active compiler context; a final
  mutable holder shared by cloned AST nodes would complicate ownership.
- Parser attempts forward listener state queries to their branch, so nested attempts retain the
  caller's abort request. Kept warnings merge; discarded attempts restore tokens and recovery;
  throwing host callbacks cannot leave the parser reporting into an abandoned branch.
- `ValidationScope` rejects a missing context/listener. `Reporting` deliberately permits a null
  inactive destination for a resolver, but its active scopes require a listener. Neither helper
  makes compiler objects safe to share across threads.

At the end of this extraction pass, these follow-up fixes existed only in C3. The synchronization
pass below now also includes them in `lagergren/errs`; the extracted commit remains unchanged.
The lexer's original listener remains independent of parser scopes. A module-name-only caller
that wants no lexical diagnostics must construct the parser with explicit discard reporting.

Validation on the final slice:

- Full Java suite: **458 cases, 418 executed, 40 pre-existing skips**, with zero failures/errors.
  The six C3-related classes cover 115 cases with zero skips, including the existing
  `ModuleInfoTest` cases. The full suite was forced to execute; final review changes were rerun.
- `spotlessCheck` and `git diff --check` pass. No Gradle dependency or distribution-extraction
  plumbing is introduced. C3's new unit tests do not require installed XDK modules.
- All **24** timestamp-normalized XDK modules match C2 byte for byte at the same source path.
  The existing `loop.x` and `exceptions.x` exercises compile and run with exit zero and no failure
  markers, covering lazy `.first`, `.count`, `.exception` and nested try/finally behavior.
- The complete C1+C2+C3 patch applies over I3, and the required `CompilerConsumerTest` executes:
  **1 test, 0 failures/errors/skips**. Both temporary verification worktrees are restored afterward.

Ignored receipts live under `build/errs-integration/comparison/`: `c3-tests.json`, `c3-result.json`,
`c3-exercises.json` and `i3-c1-c2-c3-result.json`. The extracted README states the additional parser
API break: `Parser.SafeLookAhead`/`keepResults()` becomes `Parser.Attempt`/`keep()`. Migrate source
clients and recompile at the already-required breaking release boundary before publication.

**Future PR preparation order:** E1, useful embedding compilation results, follows the already
explored slices, with I1/C2/R1 and I3's consumer evidence as specified below. C4 follows with the
ambient-listener and TypeInfo-replay work. Both are already implemented on `errs`; this is a proposed
grouping and validation order, not an instruction to resume extraction now.

### Synchronize extraction improvements back into errs, 2026-09-24

The extraction work began after the editor-configuration checkpoint `6372ba07d` at 09:51
Stockholm time on September 24. The four following commits on `lagergren/errs` recorded extraction
evidence only; implementation work for I1/I2/R1, I3/C1, C2 and C3 happened in their local worktrees.
Most extracted code was already present on `errs`, but leaving newly discovered fixes and tests
only in those worktrees was a workflow error.

Commit `855ec569f` on `lagergren/errs` includes the missing improvements while retaining its later
embedding, parser recovery, semantic and LSP implementation. Its changes belong to these future PRs:

| Slice | Reconciliation with the integrated branch |
|---|---|
| I1 | Identity/source fixes already present. Existing `CompilerDiagnosticsTest` covers both named and unnamed document cases, so the extraction-only duplicate fixture is not added. |
| I2 | Pool behavior and tests already present; retain the small duplicate-comment/import cleanup. |
| R1 | Repository behavior already present; add assertions for repeated failures and recovery after replacing corrupt files. |
| I3 | Keep the integrated Gradle module bundling and broader consumer/stdio checks. Add its legacy embedding compilation smoke test and wrapper/version compiler triggers; require the new suite in the existing XML gate. |
| C1 | Fix legacy structure-report dispatch to preserve branch source attribution; add executable migration/budget regressions. |
| C2 | Explicit-listener behavior and cascade handling already present; add the seven boundary regressions. |
| C3 | Add validation cleanup on exceptional/early exits, nested parser state-query forwarding, non-null active validation pairs and scope regressions. Keep final source-node buffers and their drain behavior. Include parser/lexical reporting and scope-lifetime documentation. |
| C4 | Remove the unused `Reporting.adoptFrom` method and describe Reporting as parser/resolver-only after removing FileStructure's ambient listener ownership. This part of the Reporting cleanup requires C4, even though it shares a file with C3. |

All implementation changes above are directly in `lagergren/errs`. The extracted branches retain
their commits and historical independent validation. Future fixes and tests belong on `errs`, with
their commit assignments recorded here. Do not port each new change into the old extraction
branches. Reconstruct and independently validate the PR groups when submission preparation begins.

Validation on the integrated branch: the full XDK rebuild and 134 focused Java cases pass with
zero failures/errors/skips. Forced full runs pass: Java has **508 cases, 468 executed and 40
existing skips**; LSP has **738 cases, 735 executed and 3 existing skips**. Both have zero
failures/errors, and the added `CompilerConsumerTest` executes once with no skips. `spotlessCheck`,
LSP `ktlintCheck`, `git diff --check` and local `actionlint -shellcheck=` pass. All 17 workflow-required
compiler suite names resolve to actual test classes. Receipts are under
`build/errs-integration/comparison/errs-sync-{focused,tests}.json`. No editor was launched and no
remote CI was queried.

### Consolidation checkpoint before the user playbook run, 2026-09-24

The source of truth is `lagergren/errs` at `c97ea9e6f`, following the main reconciliation
`855ec569f` and workflow correction `e0e321f82`. Comparing all seven extracted commits with the
integrated tree found two further omissions: the README migration guide and a test's isolated
runtime listener. `c97ea9e6f` carries both, plus the collecting-listener documentation and resolver
comment cleanup. The guide describes the final C4 ownership contract, not the intermediate C2
ambient fallback. No later parser, embedding or LSP feature was replaced with an older slice.

| Historical extraction | Equivalent integrated commits / disposition |
|---|---|
| I1 `e4c633c4c` | `19e567e55`, `fad701097`. Its standalone NamedSourceDiagnosticsTest is covered by CompilerDiagnosticsTest's named/unnamed controls; do not duplicate it. |
| I2 `d4fc67090` | `cae4f9452`, `610873fb6`, cleanup in `855ec569f`. Pool regressions are present. Later ConstantPool/TypeConstant changes belong to their own groups. |
| R1 `6384f2658` | `ddc0b063d`, strengthened retry/replacement assertions in `855ec569f`. The remaining repository difference is the unnamed catch variable. |
| I3 `8f3a57305` | `7097892b6`, `9e951df3e`, `ddc0b063d`, `855ec569f`. Retain current compilerModules/XdkLibraries bundling and the broader CI gate; its legacy CompilerConsumerTest is present. The extracted pre-LSP fixture wiring is intentionally superseded. |
| C1 `d6463091c` | The C1 source group below plus `855ec569f` for structure dispatch/migration tests and `c97ea9e6f` for isolated runtime testing and migration guidance. |
| C2 `f0b0db3a4` | The C2 source group below plus `855ec569f` for boundary regressions and `c97ea9e6f` for migration guidance. Current tests use the final API and retain later fatal-forwarding coverage. |
| C3 `e2a481b45` | The C3 source group below plus `855ec569f` for exceptional-exit/state-query regressions and `c97ea9e6f` for documentation. All scope test classes are present; current parser recovery, module hooks and source bindings remain intact. |

The standalone branches remain historical references. Their old commits are not extra prerequisites
to merge into `errs`, and their older build/API forms must not overwrite the integrated versions.
Review included every changed test file; the only absent test class is the equivalent I1 fixture
identified above. Formatting/import differences do not require copying old files over current ones.

Validation: `c97ea9e6f` has 13 executed listener/resolver tests, zero failures/errors/skips, and
passing `spotlessCheck`/`git diff --check`. The full Java/LSP/XDK evidence for `855ec569f` is recorded
above; it is not presented as a new full run. The source-map check covers all 97 commits from
`4a1eae6f7` through `c97ea9e6f`: 68 touch implementation/build/tests and 29 are documentation-only.
Every implementation commit is assigned below or explicitly deferred; every mapped source hash is
an ancestor of the integrated checkpoint. The local receipt is
`build/errs-integration/comparison/errs-pr-source-map.json`. This verifies classification, not that
each future PR already builds independently.

### User playbook workspace prepared, 2026-09-24

The complete branch and PR map are committed through `a6dbb444d`. The compiler extension was
assembled from that checkpoint with `-Plsp.adapter=compiler`. The packaged `compilerStdioTest`
run executed 15 cases with zero failures/errors/skips. VS Code was not launched: the user will
perform the interactive checks.

Local scratch files are under `build/errs-playbook`, with all ten saved XdkAdapter playbook
fixtures, explicit Library/Consumer source settings, semantic highlighting/inlay hints enabled,
Auto Save disabled and `RESULTS.txt` marking X1–X58, 7a.1–7a.14 and configuration checks as NOT RUN.
`build/open-errs-playbook.command` opens an isolated VS Code development profile using the assembled
extension. Original fixture copies are under `build/errs-playbook-baseline`.

A separate stdio baseline check used the extension's actual server JAR without XDK_HOME, confirmed
the XDK backend, and opened every fixture. Nine had no diagnostics; DupAnno had exactly one
WARNING VERIFY-75. All ten returned nonempty outlines and the server shut down normally. The
receipt at `build/errs-playbook-baseline/verification.json` records the JAR digest and results.
These are fixture/setup checks, not manual feature passes. The playbook's stale source-setting and
read/write-highlight descriptions were corrected; those documentation portions accompany L16 and
L12 respectively. Interactive findings must be fixed and tested on `errs` before PR preparation.

### Automated compiler playbook, 2026-09-24

Commit **`31a02c74d` (L16)** adds `:lang:vscode-extension:testCompilerPlaybook`, using the existing
TypeScript/Mocha extension-host runner. It registers X1–X58 against fixtures read from the manual
playbook, adds configuration/diagnostic checks, uses an isolated workspace/profile and records
per-case results and manual limitations. The aggregate task also runs server and packaged-JAR tests
for deterministic cancellation, retention and host-only dependency APIs. The prepared user workspace
above remains separate; automated provider results do not count as visual/manual passes.

Future PR placement: keep the complete runner and report format with **L16**, after all compiler
features it exercises are present. Its navigation, cursor, semantic-consumer and rename cases
correspond to the earlier L groups, but the full runner is an integrated acceptance gate and cannot
be cherry-picked onto those earlier partial implementations. No compiler/AST API changes are needed
for the automation itself. Two findings from the first complete run have focused regressions:

- **L8, `ee9de8c4a`:** `XtcTextDocumentService.refreshForFile` ignores filesystem/save notifications for an
  already open buffer. Its overlay is authoritative; `didChange` propagates edits and `didClose`
  restores disk/membership. Delayed creation/save events previously canceled current completion,
  signature and rename requests. `XdkCursorServerTest` covers all three with controlled pending work;
  closed-file watcher coverage remains in `XdkModuleServerTest` and X27/X49/X50.
- **L10, `e53dc6266`:** `SemanticModelBuilder` copies a parameter declaration's type from the resolved method
  signature when no register exists, as in an abstract interface method. X35 and
  `XdkSemanticLookupTest` cover navigation to the formal type and the library-only negative control.
  This uses existing method/parameter metadata; it adds no AST fields or Java API.

The four new regression executions failed before the fixes and passed afterward (34 focused tests,
zero failures/errors/skips). Keep these two small fixes with their respective future PR groups;
keep the complete automated acceptance runner with L16.

Final validation on the working tree at `f2b7c4f9e`: **63/63** editor cases passed in VS Code
1.139.0 (X1–X58, CFG1–CFG3, 7a.8–7a.9). The supporting XDK suites executed **247 adapter/server
tests plus 15 packaged-JAR tests**, with zero failures/errors/skips. The broader server task
reported 742 cases with zero failures/errors and three existing disabled non-XDK cases. The final
aggregate run reused the unchanged host-test results; its editor cases ran again. `spotlessCheck`,
`:lang:lsp-server:ktlintCheck`, TypeScript compilation and configuration-cache reuse passed.
The final local report is
`lang/vscode-extension/build/reports/compiler-playbook/run-dC2D2P/results.json`; its text companion
lists the visual/manual checks still outstanding. Reports include dirty paths because these changes
were not yet committed at validation time. The playbook also corrects X6's bare-expression expectation
and X28's invalid `extends Object` example. The runner and documentation form the L16 checkpoint
commit titled `Automate the complete bounded compiler playbook`.

A subsequent recorded run passed all 63 editor cases again. Its report is
`lang/vscode-extension/build/reports/compiler-playbook/run-UdYn7d/results.json` and its window-only
video is `playbook.mp4` in the same directory (89 seconds). The supporting host results were reused.
The recording shows the automated provider workload; it does not turn outstanding visual/menu/key
checks into manual passes. The recording launcher was temporary and is not a shipped runner option.

### Broader API proof after the bounded checkpoint, 2026-09-24

Continue on `errs`, using the existing explicit source graph. The accepted order is:

- [x] **1. Cross-module references and member/override rename.** Prove reference closure, compiler
  identity across artifacts, override relationships and binding preservation across all affected
  configured modules. Include closed sources, unsaved overlays, dependency changes, overloads,
  silent capture and stale/canceled requests. Complete for ordinary instance methods within the
  configured graph; unsupported cases are recorded below. No new compiler hooks were needed.
- [x] **2. Remaining semantic cases.** Ordinary properties, concrete/chained delegation, selected
  `super(...)` source bodies and separate function-value signature facts have implementations and
  focused consumers. L22 adds written Ref/Var annotation accessor targets. Runtime function targets,
  interface-valued delegates and native annotation storage remain explicitly unknown; see the
  post-L18 and annotation follow-up records.
- [x] **3. Broader incomplete source.** Binary/conditional expressions and following arguments
  retain real compiler context and original positions. Missing delimiters and arbitrary syntax
  recovery are not made semantically complete by this change.
- [x] **4. Remaining hardening.** Structure ownership and the bound-generic binary-AST reproducer
  are fixed. The full LSP suite, packaged stdio, editor acceptance and 120-cycle retention workload
  pass; results and remaining scope limits are recorded below.

Automatic discovery and a production persistent index are later work. The completed bounded POC
remains the baseline; broader queries must distinguish complete configured-graph evidence from
unknown external consumers. Record new implementation commits and future PR boundaries here.

#### Post-L18 hardening

All work remains on `lagergren/errs`. The previous implementation/mapping checkpoint is already
pushed as `714042da5` / `87eee85b1`. The following source assignments refer to implementation
checkpoint `570a7e870`; no extracted PR is claimed independently green.

1. **Delegation:** `CompilerImplementations` follows compiler property and method signatures
   through statically concrete receiver types. Cycles and paths beyond 64 links return no target.
   Interface-typed receivers remain unresolved. Queries never call forwarding-method generation.
2. **Call provenance:** `InvocationBinding` collects separate immutable function-call facts;
   Compilation/PartialAnalysis expose them while retaining old constructors. At that checkpoint
   both records had six components: Java record patterns added `functionBindings`, as demonstrated by
   `EmbeddingApiCompatibilityTest`; preserving constructors does not preserve pattern arity.
   L62 subsequently adds `constructorBindings` as Compilation's seventh component; PartialAnalysis
   retains its existing shape.
   `MethodInfo` exposes
   the written super body using the existing compiler algorithm. Kotlin copies selected super
   calls for navigation/hierarchy/rename comparison and function signatures for help. The `super`
   keyword is excluded from rename edits; function calls do not invent runtime hierarchy edges.
3. **Incomplete syntax:** the explicit cursor parser retains holes within binary/conditional
   expressions and consumes following arguments. Real validation establishes scope/inference;
   an incomplete value still fails validation and cannot emit code. EOF-only behavior is retained.
4. **Hardening:** source-owned Site.At diagnostics map to declaration tokens, including closed
   members and overlays. PropertyInfo reports duplicate annotations against the contributed
   property instead of its inherited base. Binary-only sites retain a document fallback. A
   generic function reference (`function Int(Int) make() = id`) first exposed incorrect hidden
   parameter retention, then the documented binary-AST TODO. NameExpression now emits the bound
   type and BindFunctionAST consistently. The retention workload grows to 120 cycles / 960 edits.

Future additive extraction units from `570a7e870` (split by the boundaries below):

| ID | Files / boundary | Prerequisites |
|---|---|---|
| L19 | `CompilerImplementations` delegation and lookup/output-preservation tests; playbook X68 | L18, L13; editor runner L16 |
| E5 | `InvocationBinding`, `InvocationExpression`, `MethodInfo.getSuperMethod`, embedding result maps and call-fact tests | E4 |
| L20 | Kotlin function signatures, super navigation/hierarchy and rename safety; X69–X70 | E5, L7/L11/L15; graph regression L17 |
| C10 | Parser holes in compound/conditional expressions and following arguments; X71 | C7/C9; editor consumer L8/L9 |
| I4 | NameExpression bound-generic type and binary-AST correction, serialization regressions | Independent compiler fix; I3/E1 for embedding test harness |
| I5 | PropertyInfo duplicate/superfluous warning points at contributed identity | Independent diagnostic-site fix; C4 for replay tests |
| L21 | Source-structure diagnostic positioning and 7a.8 acceptance | L1/L5, I5 |

The longer retention workload belongs with L2/L14/L15, not a new production API. The common
boundary/purity test contains L19 and I4 sections and must be divided accordingly on extraction.
All seven units are additive to the existing 35 groups (42 total); each needs its own build/test
validation when extracted. Documentation and test fixtures travel with the feature they describe.

Validation of the integrated implementation checkpoint `570a7e870`:

- Full LSP suite: **791 cases, 788 executed, three existing skips**, zero failures/errors.
- Packaged compiler stdio: **16 cases**, zero failures/errors/skips.
- Java parser, MethodInfo and API compatibility suites: **29 cases**, zero failures/errors/skips.
- Automated VS Code: **76 cases passed** (X1–X71, CFG1–CFG3, 7a.8–7a.9).
  Report: `lang/vscode-extension/build/reports/compiler-playbook/run-PCAd5o/results.json`.
  The first run passed 75/76: X69 expected null for rejected Prepare Rename, while the server
  correctly returned its protocol error. The corrected assertion passed in the complete rerun.
- Retention: **120 cycles / 960 edit requests / 2,400 weak references**, all released after close
  and shutdown. Rebuild p50 **217 ms**, p95 **231 ms**, including debounce. This is a repeatable
  bounded workload, not a multi-hour editor soak or performance guarantee.
- TypeScript compilation, Kotlin formatting/checks, root `spotlessCheck` and `git diff --check`
  pass. The Java record-pattern migration example was updated to six components and rerun.

The editor run checks provider results, diagnostic navigation and replacement behavior. Visual
chooser/theme layout remains manual. Ref/Var annotation dispatch, unknown runtime call targets,
incomplete function/constructor candidates, missing enclosing delimiters, wider workspace indexing
and automatic discovery remain explicit follow-ups rather than claims of this API checkpoint.

#### Ref/Var annotation accessor follow-up

The next remaining semantic case after `570a7e870` is implemented in `abbc89f88`:

- [x] Resolve written Ref/Var annotation getter/setter targets from the host's existing nested
  method chains. Preserve annotation order, explicit accessor precedence and generic substitution.
- [x] Prove inherited and concrete-delegated targets, dependency source-index replacement and
  binary-only negative cases. Native annotation storage has no invented written accessor.
- [x] Verify that inspection emits no new diagnostics, preserves compiled bytes and publishes
  snapshots containing no compiler objects. No extra PropertyClassType TypeInfo is constructed.
- [x] Add the X72 editor/manual-playbook case and update the adapter capability documents.
- [x] Complete the integrated server, packaged stdio and 77-case editor verification run.

The production change is confined to `CompilerImplementations.kt`. Existing compiler composition
already supplies the required facts; no embedding API, AST field, clone rule or listener contract
changes. Lookup retains the existing compiler-worker cancellation boundary and copies only IDs and
source locations. A property query returns the combined accessor set, not a read/write-specific
dispatch result. Binary-only accessors need a host source index; native/generated storage remains
unavailable. Property rename, capped redirects and workspace-wide implementation discovery stay
outside this slice.

**Future PR L22:** include the Kotlin annotation-chain consumer, the annotation cases in
`XdkSemanticLookupTest` and `XdkDependencyTest`, the annotated-output/purity fixture in
`CompilerBoundaryRequirementsTest`, and playbook X72 with its capability documentation.
It depends on L18/L13; the concrete-delegation regression also needs L19, and editor acceptance
uses L16's runner. This is the 43rd extraction group, assigned to the annotation-lookup portion of
`abbc89f88`; development remains on `lagergren/errs`.

Focused verification: **46 cases**, zero failures/errors/skips (27 lookup, 11 dependency and
eight boundary tests). Integrated verification also passes:

- Full LSP suite: **795 cases, 792 executed, three existing skips**, zero failures/errors.
- Packaged compiler stdio: **16 cases**, zero failures/errors/skips.
- VS Code: **77 cases passed**, including X72. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-AX3mSM/results.json`.
- Retention: **120 cycles / 960 edit requests / 2,401 weak references**, zero retained after
  close/shutdown; rebuild p50 **216 ms**, p95 **227 ms**, including debounce.
- TypeScript compilation, Kotlin formatting/checks, root `spotlessCheck` and `git diff --check` pass.

Command: `./gradlew :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain`.
This is integrated-branch evidence; L22 must still be validated against its own extraction base.

The incomplete function/constructor follow-up is implemented below. The subsequent bounded
atomic binary-AST/ToIntExpression audit and bounded missing-delimiter recovery are recorded after
it. Automatic discovery and a production persistent index remain later work.

#### Incomplete function and constructor signature help

Implemented on `lagergren/errs` in `abbc89f88`, after `4ceb56477`:

- [x] Function-valued callees expose full parameter/return types while positional arguments are
  missing. Written arguments use normal compiler validation; candidates retain active slots and
  expected types, without guessed parameter names, defaults or runtime method targets.
- [x] Prove generic function properties, flow-narrowed nullable functions, function-producing
  expressions and captured lambda arguments. Reject unreadable/non-function callees, incompatible
  values, named function arguments and excess written arguments.
- [x] Ordinary `new Type(...)` calls expose fitted constructor overloads, declared parameter names,
  defaults, explicit class type substitution and reordered/pending named slots. Inaccessible,
  abstract, unknown and incompatible constructions return no candidate.
- [x] Preserve invalid argument results when the full invocation validator checks return fit.
  `make(fn)(1, )` must not publish a shortened validated function signature. The one-line
  `InvocationExpression.testFunction` fix combines validity instead of overwriting it; regressions
  include a valid complete call and invalid arity/type calls with no function bindings.
- [x] Add packaged stdio diagnostic/correction cases and editor/manual-playbook X73–X74.

**Ownership and compatibility.** `CursorBinding.FunctionCandidate` copies the function type and
written argument mappings into immutable attempt-owned records. The collector's existing surviving-
syntax filter and cleanup still apply. Kotlin copies types and ranges on the compiler worker and
shares its signature copier with complete function calls; no compiler Context or object survives
in the semantic model. `CursorBinding` retains its three- and six-argument constructors, but now
has seven record components. Record-pattern users must add `functions`; the compatibility suite
contains an executable migration example.

`Parser` shares its existing partial-argument loop with ordinary constructor syntax. The existing
`NewExpression` type prefix is an `IncompleteStatement` target child, and written arguments remain
children of the incomplete site. `PartialCallResolver` validates trial clones in child contexts
while lexical/flow information exists, then uses the normal argument fitter for constructors.
No new AST fields, mutable semantic caches or clone/reset rules are added. Private speculative
listeners collect error state so unreadable callees and invalid types are rejected; cancellation
propagates, explicit TypeInfo errors reach the host, and cursor results never replace normal
document diagnostics.

**Limits.** No overload winner is claimed for a partial call. Function types do not establish
named arguments or runtime targets. Virtual/inner/array/annotated construction, omitted constructor
class-type inference and wider missing-delimiter recovery were outside this checkpoint's proof.
C16/L29 and C17/L30 above extend that coverage; C13–C15 supply argument-value completion.

**Future extraction boundaries (source commit `abbc89f88`):**

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C11 | `CursorBinding.FunctionCandidate`, constructor cursor syntax in `Parser`, `PartialCallResolver` and the `IncompleteStatement` call site; record compatibility regression | C7/C9; preserve old constructors and document seven-component patterns |
| L23 | `PartialSemanticModel`, shared function-signature copier, `XdkCursorQueries`, function/constructor adapter tests, packaged stdio, X73–X74 and capability docs | C11, L9, E5/L20; expression-callee regression needs I6; editor runner L16 |
| I6 | Preserve argument failure in `InvocationExpression.testFunction`; positive/negative function-call regression in `CompilerCallSiteTest` | Independent production fix; I3/E1 embedding test harness; no-invalid-binding assertion also needs E5 |

L22 remains a separate extraction group. These three units bring the planned total to **46**.
These are portions of one integrated source commit; each extracted PR must still pass independently.

**Verification:** the focused function/constructor/existing-incomplete/call-site run passes **32
cases**, with zero failures/errors/skips. Java parser/API compatibility passes **29 cases**,
also with zero skips. The full LSP suite passes **810 cases, 807 executed, three existing skips**;
packaged compiler stdio passes **18 cases**. The 120-cycle/960-edit retention workload releases all
**2,401 weak references** after close/shutdown (rebuild p50 **211 ms**, p95 **217 ms**, including
debounce). All **79 editor cases pass**, including X73–X74; report:
`lang/vscode-extension/build/reports/compiler-playbook/run-ZugND6/results.json`. TypeScript compilation, Kotlin formatting/checks,
root `spotlessCheck` and `git diff --check` pass.

Validation commands:

```bash
./gradlew :javatools:test --tests 'org.xvm.compiler.Parser*' --tests org.xvm.api.EmbeddingApiCompatibilityTest :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
./gradlew :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The first editor run passed 78/79: X73's `fn(` locator matched the earlier, complete one-parameter
call instead of the edited two-parameter call. The test now targets the edited occurrence.
Compiler and server suites passed before this test-only correction.

#### Atomic binary-AST and switch-conversion audit

Implemented in `d0809cd83` after the pushed `abbc89f88` / `a0ec10840` checkpoint, on
`lagergren/errs`. This closes the selected post-validation metadata investigation, not every
historical silent-TypeInfo site or all numeric runtime behavior.

- [x] Reproduce atomic prefix/postfix increment/decrement ASTs with zero return values despite
  an `Int` source result. `SequentialAssignExpression` now supplies its validated result types.
- [x] Reproduce module/singleton property operations failing with `EMB-5` in code generation.
  Atomic operator lookup now handles all four existing property access plans, including singleton
  and enclosing-instance owners. The receiver type used for lookup also supplies the AST Var type,
  preserving concrete generic referents for sequential and compound assignments.
- [x] Reproduce enclosing-instance assignment ASTs with an `Int64` owner instead of `Counter`.
  `NameExpression.generateAssignable` now uses the already-created outer register's type.
- [x] Compile and deserialize atomic operations through generic receivers and source/serialized
  dependencies. A duplicate annotation warning reaches the host exactly once in both source and
  dependency cases; invalid operations still produce compiler source diagnostics, without `EMB-5`.
- [x] Exercise dense switches over Bit, Nibble, Char and all twelve signed/unsigned integer types.
  Inspect emitted JumpInt, extraction/conversion and offset operations after serialization, and
  check that the binary AST retains the original condition type. Enum switches remain on their
  non-ordinal path. No missing diagnostic or conversion-metadata failure was reproduced.

**Why these changes belong in the AST implementation.** They correct existing code generation
for validated expressions. A computed `NameExpression.getAtomicRefType(Context)` helper reuses the
existing owner/access plan; it adds no mutable field, clone/reset obligation or embedding result
component. Operator lookup and AST construction must agree on that same type. No Kotlin dependency
is added to javatools, no host-only state is stored on nodes, and no public listener contract or
silence policy changes. `ToIntExpression` is unchanged because the probes do not justify a reporting
migration. The result/owner defects are also visible in local `origin/master` source at
`4a1eae6f7430bda4204f0b4ec8248f942d58d6ba`; they were not introduced by the LSP collectors.
That is a source comparison, not an execution claim against a freshly fetched master build.

**Future PR I7:** the emission changes in `NameExpression`, `SequentialAssignExpression` and
`AssignmentStatement`, plus `CompilerEmissionAuditTest` and this audit documentation. Production
changes are independent of the embedding API. The source-level tests use I3/E1's compiled-library
and embedding harness; the exact warning-replay controls require C4. Keep the switch controls with
the audit even though they need no production change. This is the **47th** planned extraction group;
production/test commit is `d0809cd83`. The audit documentation is in `aa65860d0` alongside the
cursor recovery checkpoint. The preceding L22/C11/L23/I6 mapping remains at `abbc89f88`.

Focused verification passes **56 cases**, zero failures/errors/skips: 36 new emission-audit cases,
12 existing TypeInfo diagnostic cases and eight compiler-boundary cases. Full verification passes:

- Java: **509 cases, 469 executed, 40 existing skips**, zero failures/errors. The skips are 36
  explicitly disabled tests and four opt-in project-creation integration tests; no missing-XDK skip.
- LSP: **846 cases, 843 executed, three existing skips**, zero failures/errors.
- Packaged compiler stdio: **18 cases**, zero failures/errors/skips.
- Retention: **120 cycles / 960 edit requests / 2,401 weak references**, zero retained after
  close/shutdown; rebuild p50 **212 ms**, p95 **225 ms**, including debounce.
- Kotlin checks, root `spotlessCheck` and `git diff --check` pass. The full command completes in
  3m 55s. Gradle supplies freshly rebuilt compiler/core artifacts; unchanged build dependencies
  can be cached, but these regression test tasks executed.

VS Code was not relaunched for this compiler-output audit. The preceding 79-case editor result
belongs to the incomplete-signature checkpoint above; it is not claimed as a new editor run.

```bash
./gradlew :lang:lsp-server:test --tests org.xvm.lsp.adapter.CompilerEmissionAuditTest --tests org.xvm.lsp.adapter.TypeInfoDiagnosticsTest --tests org.xvm.lsp.adapter.CompilerBoundaryRequirementsTest -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
./gradlew :javatools:test :lang:lsp-server:test :lang:lsp-server:compilerStdioTest spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The next implementation step, missing-delimiter recovery for explicit cursor analysis, is recorded
below. Specialized constructor forms, automatic discovery and persistent indexing remain separate
later work.

#### Missing-delimiter cursor recovery

Implemented in `aa65860d0` on `lagergren/errs`, after the `abbc89f88` / `a0ec10840` checkpoint.
Keep this separate from the I7 emission fixes in `d0809cd83` when extracting PRs.

- [x] Reproduce loss of completion/signature facts under unclosed grouping parentheses and array
  indexes, including nested calls. The initial adapter regression run failed 12 of 14 cases.
- [x] Retain missing call/group/index closing delimiters only around an existing explicit cursor
  hole at a statement or outer-delimiter boundary. Consume real closers normally and leave other
  delimiters to the enclosing parser. `]` is now a supported cursor boundary.
- [x] Retain standalone/assignment/return statements without a trailing semicolon at a closing
  brace. A cursor at EOF also retains unfinished block containers under the same cursor diagnostic.
- [x] Keep ordinary diagnostics, cached results, original source positions and following declarations.
  Cursor recovery never creates an operand, gives the hole a type or emits the incomplete method.
  Unrelated syntax errors, cancellation, exhausted budgets and failed speculation remain barriers.
- [x] Add adapter and embedding checks for method/function/constructor signatures, nested/mixed
  delimiters, flow narrowing and unsaved module overlays. Add a packaged stdio repair round trip and
  playbook rows X75–X76 for completion, parameter highlighting and diagnostic clearing.

**Placement and compatibility.** This is parser recovery using the existing `IncompleteExpression`
and `IncompleteStatement` children. The parser queries their ownership instead of storing cursor
state on AST nodes. Missing ends use zero-width tokens at existing source positions; grouping and
index validation keep the normal contexts, and the existing hole prevents emission. No AST class,
mutable field, clone/reset rule, listener interface or public record component is added. The explicit
EOF attempt uses `PARSER-30` for its unfinished suffix; normal parsing still reports missing braces.
Kotlin remains a consumer and compiler mode remains Java-only. Tree-sitter is still the shipped default.

**Bounded scope.** Missing operands, declaration headers, tuple/literal delimiters and unrelated
syntax errors are not repaired. The cursor must still be at a supported boundary, not inside a
token or before further member syntax. This does not implement expected-type argument completion,
enclosing-instance member enumeration or additional specialized constructor forms. C13–C15,
C16/L29 and C17/L30 above record the later argument, constructor and declaration/literal extensions.

| Future group | Files / responsibility | Prerequisites |
|---|---|---|
| C12 | `Parser`, `ParserRecoveryTest`, embedding API recovery documentation | C7/C10 cursor syntax; no public API shape change |
| L24 | `XdkDelimiterRecoveryTest`, updated partial-analysis/completion negative controls, packaged stdio, X75–X76 and capability/playbook docs | C12, L8/L9; function/constructor controls C11/L23; editor runner L16 |

Both source groups are in `aa65860d0`; extract the compiler and host portions separately as listed
above. These bring the plan to **49** extraction groups, including I7. I7's production/tests are in
`d0809cd83`; extract its audit documentation from `aa65860d0` with those changes.

**Verification:** all 22 new delimiter adapter/embedding cases pass, as do the 12 parser-recovery
cases and the existing partial-analysis coverage. The full results are:

- Java: **511 cases, 471 executed, 40 existing skips**, zero failures/errors.
- LSP: **868 cases, 865 executed, three existing skips**, zero failures/errors.
- Packaged compiler stdio: **19 cases**, zero failures/errors/skips, including edit/repair recovery.
- VS Code: **81 cases passed**, including X75–X76, with no skipped editor cases. Report:
  `lang/vscode-extension/build/reports/compiler-playbook/run-05MGlN/results.json`.
- Retention: **120 cycles / 960 edit requests / 2,403 weak references**, zero retained after
  close/shutdown; rebuild p50 **214 ms**, p95 **247 ms**, including debounce.
- TypeScript compilation, Kotlin checks, root `spotlessCheck` and `git diff --check` pass.

Two old negative controls expected no facts for unclosed grouping or a compound statement missing
its semicolon. Those are now supported; the negative controls use actual missing-operand damage,
and positive cases pin the newly recovered forms. No ordinary compilation success is inferred from
the cursor-only recovery. The Java/LSP skips are the existing disabled/opt-in cases, not missing XDK
inputs. Test tasks executed; unchanged build dependencies were reused.

```bash
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
./gradlew :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

The first full run passed Java and stdio and exposed the obsolete compound-statement expectation;
the second command passed the corrected full LSP suite and all editor cases in 5m 48s. This is
integrated-branch evidence; C12 and L24 must still pass on their respective extraction bases.

#### Argument-value completion

Implemented on `lagergren/errs` in `f2896916b`, following `6b084731e`.
Empty final positional slots (`call(|)`, `call(1, |)`) and pending named values (`call(name=|)`)
now offer readable visible locals and parameters that fit at least one incomplete-call candidate.
Ordinary methods, function values and ordinary constructors share this behavior. No overload is
selected until normal compilation validates the completed source.

- [x] Preserve compiler ownership of compatibility: `PartialCallResolver` probes each proposed
  source name through the existing method/constructor fitter or function argument validator.
  This includes generic inference/substitution and implicit conversions; comparing copied type
  names or expected types in Kotlin would not reproduce those rules.
- [x] Capture accepted variables in immutable, attempt-owned `CursorBinding.argumentValues`.
  Kotlin copies symbol/type facts on the compiler worker and creates zero-width insertion edits.
- [x] Pin readability, narrowing, lexical shadowing, overload alternatives, invalid slots,
  original arguments/source/diagnostics, unselected call facts and unsaved module signatures.
- [x] Exercise cancellation of argument queries and alternate member/argument completion in the
  120-cycle compiler-object retention workload.
- [x] Add packaged stdio insertion/correction tests and manual/automated playbook X77–X78.
- [x] Record the completed full-suite/editor verification below.

**AST placement and lifetime.** Proposals are temporary `NameExpression`/`LabeledExpression`
syntax with the incomplete site's lexical parentage, then cloned into isolated trial contexts by
the existing fitter. They are never installed as source children or recorded as selected complete
calls. The existing collector filters surviving source syntax and clears trial entries. The
incomplete AST node gains no fields, caches, accessors or clone/reset obligations. Fitting remains
in `PartialCallResolver` because it needs the live compiler Context; presentation remains in Kotlin.

**Listener behavior.** Candidate mismatches use cancellable silent PROBE listeners. Each trial's
local error state still rejects invalid proposals; it cannot erase ordinary compilation errors or
turn an unfinished call into a successful compilation. Explicit target TypeInfo inspection retains
the host listener. The existing request lifecycle discards cancelled/stale results.

**Compatibility.** `CursorBinding` now has eight components, adding `argumentValues` after
`functions`. Its previous three-, six- and seven-argument constructors remain callable and supply
an empty value list. Record patterns must add the eighth component; the executable compatibility
test covers both constructors and the new pattern. This is an explicit pattern migration, not an
unqualified claim of source compatibility.

**Limits.** Only locals and parameters are proposed; literals, implicit properties/constants and
other expression synthesis are deferred. Typed prefixes keep existing scope/member completion
without argument-type filtering. Empty slots before later written arguments, positional slots
after named arguments without another label, and specialized construction forms remain outside
this proof. No rewritten source or guessed function parameter names are needed.

**Future extraction boundaries:**

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C13 | `CursorBinding.argumentValues`, `PartialCallResolver` proposed-value fitting and constructor/pattern compatibility regression | C8/C9/C11; preserve old constructors and document eight-component patterns |
| L25 | Copied argument-value members, cursor insertion edits, `XdkArgumentCompletionTest`, cursor cancellation/retention controls, stdio, X77–X78 and capability/playbook docs | C13, L8/L9/L23; editor runner L16 |

These add two groups, bringing the working plan to **51**. Source commit `f2896916b` contains
the C13 compiler portion and L25 host/tests/documentation portion. Each extracted PR still needs
its own passing prerequisites and tests.

The editor run also exposed an intermittent X25 assertion during fixture setup. Module membership
changes can expire a hierarchy item between prepare and subtype requests. X25 now uses the runner's
bounded wait and prepares a fresh item on each attempt until the unsaved member appears. X28 still
requires stale items to return no results. This test-only synchronization in `f2896916b` belongs
to **L16**, independently of C13/L25; it does not change hierarchy production behavior.

**Verification (2026-09-24):**

| Suite | Reported | Executed | Existing skips | Failures/errors |
|---|---:|---:|---:|---:|
| Full Java suite | 511 | 471 | 40 | 0 |
| Full LSP suite | 885 | 882 | 3 | 0 |
| Packaged stdio | 22 | 22 | 0 | 0 |
| VS Code playbook | 83 | 83 | 0 | 0 |

Backend counts come from JUnit XML; editor counts come from the playbook JSON report. All 16
argument-completion tests and ten cursor lifecycle tests ran.
The retention workload alternates member and argument-value queries over 120 cycles/960 edit
requests: **2,403 weak references, zero retained**, rebuild p50 **215 ms**, p95 **231 ms**, including
debounce. These are bounded lifecycle measurements, not a prolonged editor soak or completion SLA.

The Java suite ran with the first command; the second reran LSP/stdio after the new lifecycle
controls and a protocol assertion correction. Its editor run exposed X25's synchronization issue
and two fixture assertions: X73 needed a unique anchor after adding another `fn` call, and X77
needed to exclude VS Code's independent snippets from compiler-value assertions.
After correcting those test assumptions, the final invocation passed every X1–X78/CFG/diagnostic
case in 2m 1s, reusing unchanged host-test results and the configuration cache. The full report is
`lang/vscode-extension/build/reports/compiler-playbook/run-NhSfk3/results.json` (83 passed, no
errors/skips). `spotlessCheck`, `git diff --check` and the new documentation links also pass.

```bash
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
./gradlew :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

Typed prefixes are implemented in the follow-up below. C15/L28 and C16/L29/C17/L30 above record
later property values, specialized construction and declaration/literal recovery. Automatic
discovery and persistent indexing remain separate work.

#### Typed argument-prefix completion

**Implementation (2026-09-25, `dc3218d81`).** A direct final argument prefix
such as `take(te|)` or `take(value=te|)` now retains its call's fitting context. Previously it became
an ordinary NAME site and offered every matching visible name, even incompatible values. The
parser retains the original prefix token separately from complete earlier arguments and the
optional named label. Existing compiler trial fitting supplies compatible locals/parameters;
Kotlin copies the decoded spelling and original UTF-16 token range into its existing prefix model.
Accepting a completion replaces exactly that token. Candidate overloads remain alternatives.

- [x] Retain direct positional/named prefixes for methods, function values and ordinary constructors.
- [x] Filter proposals through compiler fitting and preserve exact token replacement ranges.
- [x] Cover same-prefix incompatible names, overload alternatives, inference/conversions, narrowing,
  unreadable/shadowed variables, unknown/duplicate labels and incompatible earlier arguments.
- [x] Cover immutable source/argument/call facts, escaped identifiers, UTF-16/CRLF, unsaved root
  signatures, cancellation and composition with missing enclosing delimiters.
- [x] Extend packaged stdio acceptance and playbook X79–X80, including diagnostics clearing.
- [x] Record final full-suite/editor verification below.

**AST placement and lifetime.** `IncompleteStatement` adds one final `Token argumentPrefix`,
`forArgumentPrefix(...)` and `getArgumentPrefix()`. `IncompleteExpression.getSite()` exposes
its existing syntax child. These describe source ownership and a replacement span; they belong in
the parser/AST. The selected prefix is not validated as a source argument. The parser promotes its
direct NAME hole to the owning CALL, so only one cursor site is published. Complete earlier
arguments and the receiver remain actual children and follow normal cloning; prefix/label tokens
follow the node's existing token-sharing convention. There is no phase-assigned semantic state,
new Context retention, clone-reset hook or source rewriting. Fitting stays in `PartialCallResolver`,
where the live Context is available; editor presentation stays in Kotlin.

**Compatibility and listeners.** Existing constructors remain unchanged. `CursorBinding` stays
at eight components; this step adds no record-pattern migration. It reuses `argumentValues` and the
existing attempt-owned collector. The explicit probe still reports `PARSER-30` once; private,
cancellable PROBE errors reject unsuitable values without reaching normal document diagnostics.
The query never publishes a selected complete call or emits the incomplete method.

**Limits.** Fitting applies only to a direct final bare-name prefix at its end, in the same supported
slots as empty argument completion. Qualified/grouped/compound expressions and prefixes before
later written arguments retain ordinary scope/member completion without argument-type filtering.
Literals, implicit properties/constants, specialized construction forms and arbitrary expression
synthesis remain outside this pass. Tree-sitter remains the shipped default; compiler mode uses
Java analysis exclusively.

**Future extraction boundaries:**

| Group | Files / responsibility | Prerequisites |
|---|---|---|
| C14 | Parser promotion; `IncompleteStatement` token/factory/getter; `IncompleteExpression` syntax accessor; `PartialCallResolver` prefix filter; parser clone/source regressions | C13, C12 for missing-delimiter composition; additive API only |
| L26 | Copied prefix/range and replacement edits; adapter, cancellation/retention and stdio tests; X79–X80; capability and ownership docs | C14, L25, L24; editor runner L16 |

These add two groups, bringing the working plan to **53**. Source commit `dc3218d81` contains
the C14 compiler portion and L26 host/tests/documentation portion described above.
The integrated branch remains `lagergren/errs`; each future PR still needs its own prerequisites
and independently passing checks.

**Verification (2026-09-25):** the initial ten typed-prefix regressions failed against the preceding
checkpoint, then passed with the parser/fitter integration. The full verification results are:

| Suite | Reported | Executed | Existing skips | Failures/errors |
|---|---:|---:|---:|---:|
| Full Java suite | 512 | 472 | 40 | 0 |
| Full LSP suite | 911 | 908 | 3 | 0 |
| Packaged stdio | 27 | 27 | 0 | 0 |
| VS Code playbook | 85 | 85 | 0 | 0 |

Backend counts come from JUnit XML; editor counts come from its JSON report.
All 36 argument-completion tests, 27 delimiter-recovery tests and
11 cursor-lifecycle tests ran. The retention workload now alternates member, empty-argument and
typed-argument queries over 120 cycles/960 edit requests: **2,402 weak references, zero retained**,
rebuild p50 **215 ms**, p95 **241 ms**, including debounce. These remain bounded measurements,
not a prolonged editor soak or completion latency guarantee.

The full command passed in 7m 23s, including X1–X80, configuration and diagnostic cases. The
editor report is `lang/vscode-extension/build/reports/compiler-playbook/run-lv52nI/results.json`.
X79 checks method/function/constructor and named-value replacement; X80 preserves overload
alternatives and excludes an incompatible same-prefix value. Both verify that acceptance clears
diagnostics. `spotlessCheck`, Kotlin formatting checks, TypeScript compilation, `git diff --check`
and added local documentation links pass.

```bash
./gradlew :javatools:test :lang:vscode-extension:testCompilerPlaybook spotlessCheck -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler --console=plain
```

**Follow-through:** implicit property/constant fitting is implemented in
[C15/L28](#property-and-constant-argument-completion). Specialized construction and bounded
declaration/tuple/literal recovery are implemented in C16/L29/C17/L30 above; automatic discovery
and persistent indexing stay separate.

#### Configured-graph implementation evidence

Step 1 is implemented and validated on the integrated branch. References compile every
configured module in dependency order from a captured set of
source texts and memberships. Each attempt opens fresh serialized dependencies; source declarations
join by their compiler-associated source location and binary members by exact compiler constant.
No spelling/arity match, persistent index or compiler object is retained. References preserve exact
target identity; method rename separately computes the complete ordinary override family from
compiler-composed chains, including abstract/generic contracts.

Rename recompiles all proposed sources and compares every recorded occurrence binding, selected
call and dispatch chain against their original declaration sites with translated offsets. Tests
include edits that compile yet capture an untouched overload call or merge two independent abstract
contracts. Both must be rejected. A graph containing errors gives no results; proof diagnostics do
not replace normal editor diagnostics. All work stays on the serialized compiler worker. Source,
configuration, dependency and client cancellation retire queries; server publication checks every
open document's lifetime and preserves versions for all edited files. Closed source text and
membership are recaptured before returning to catch delayed filesystem notifications.

The bundled XDK is always in the compilation repository chain. Tests resolve the real
`String.indexOf(Char, Int)` signature and source references across modules, then reject renaming
that binary method and a source override of `Object.toString`. The binary source-location absence
is separate from successful type/member resolution. An additional external artifact fixture checks
that the policy is not special-cased to the core library.

The missing fact found so far is `super(...)`: compilation succeeds, but its predefined function
register has no copied method invocation binding or declaration site for the all-binding proof.
It safely rejects rename and is tracked in step 2. Properties/accessors, static functions,
constructors, mixin/delegating/capped chains and consumers outside the configured graph remain
unsupported. Preparing rename is a candidate check; the worker can reject its final proof.
Bodyless parameters had another Kotlin copying gap: their resolved method signature and original
span now supply a declaration identity even without a body register. No Java embedding/AST API,
AST state or clone rule changed.

Future PR placement is **L17**, after L13/L14/L15 (and L16 for editor cases). Keep graph identity/
dispatch collection, bodyless parameter copying, adapter/server lifecycle guards and their tests
together. Playbook X59–X63 and the Problems-view additions follow the complete acceptance runner.
The checkpoint is `332003f0a` (`Validate references and method rename across configured source
modules`), recorded in L17 below. Extraction still requires independent validation.

The Problems-view acceptance also pins a remaining diagnostic-location limit: `VERIFY-75`
arrives once with warning severity and its compiler code, but uses a file-level `(0,0)` range.
`Site.At` retains the compiler structure rather than a source span, and the current adapter maps
that case to the requesting document. Source errors with `Site.In` retain exact ranges. A follow-up
must associate source-owned structure diagnostics with their actual declaration/file (including
closed members) without guessing from message text or falsely locating binary structures. This is
not fixed by marking the warning as a visual pass; the playbook records the limitation explicitly.

Validation on 2026-09-24: the complete LSP suite reports 769 cases (766 executed, three existing
skips, zero failures/errors); all 15 packaged compiler stdio cases pass with zero skips. The query
suite has 11 cases, query lifecycle has eight, cursor server has 24 and rename server has three.
The isolated VS Code run passes all 68 cases (X1–X63, CFG1–CFG3 and 7a.8–7a.9), including real
bundled-XDK resolution, binary rename rejection, generic override edits and Problems diagnostics.
Its report is `lang/vscode-extension/build/reports/compiler-playbook/run-FE1WFH/results.json`.
The final `testCompilerPlaybook spotlessCheck` invocation reused the configuration cache and passed;
unchanged host/protocol test results were reused for that editor rerun. Problems icons, counts,
filter rendering and mouse interaction remain manual checks. This is integrated-branch evidence;
L17 still needs its own clean validation when extracted.


#### Property/accessor implementation evidence

The current Kotlin implementation copies ordinary property fields and getter/setter implementations
from compiler composition. Generic contracts, default getters replaced by fields, inherited bodies,
separate accessor queries, composed mixins and unrelated-name controls have adapter consumers.
Closed member locations follow unsaved overlays and disappear after failed compilation. Inherited
dependency accessors are copied from linked compiler identities even when no written call registered
them in the consumer pool; the artifact source index supplies locations, and binary-only artifacts
do not invent them. Source-index replacement invalidates the old result.

A property query returns the declaration-level union of effective getter/setter targets. It does
not select a runtime receiver or distinguish reads from writes. The existing worker-only collector
reads `PropertyInfo`/`PropertyBody`, accessor structures and field identities; it skips delegating,
capped and Ref/Var-annotated dispatch. Mixin accessors missing from a host method table come from
its compiler-ordered property composition. No separate Ref/Var TypeInfo is built: that probe can
introduce additional override checks that ordinary source compilation did not request. No Java API,
AST field/accessor or cloning change is needed. Byte-output and compiler-object-exclusion checks
include the new property path. Packaged stdio and playbook X64–X67 exercise protocol consumers.

The implementation checkpoint is `714042da5`. Future PR placement is **L18**, based on L10/L13;
its editor acceptance follows L16. Property rename,
delegation resolution, annotation dispatch, function-valued calls and workspace implementation
search remain separate requirements. Delegation targets are the next semantic investigation.

Validation on 2026-09-24: the full LSP suite reports 776 cases (773 executed, three existing skips,
zero failures/errors), and all 16 packaged stdio tests pass without skips. This includes 22 semantic
lookup cases, ten dependency cases and the compiled-output/purity regression. All 72 editor cases
pass (X1–X67, CFG1–CFG3 and 7a.8–7a.9); the report is
`lang/vscode-extension/build/reports/compiler-playbook/run-iURYTJ/results.json`. Kotlin checks,
TypeScript compilation and `spotlessCheck` pass. Gradle reused the configuration cache during the
focused iterations and stored the final aggregate entry successfully. Visual chooser/peek rendering
remains manual. L18 still requires independent validation when extracted; this evidence is for errs.

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
- [x] **4. Other semantic consumer probes — bounded consumers and gaps established.**
  - [x] Type-definition and implementation lookup within the current module, using copied type
    identities and actual compiler method chains; no new Java embedding/AST API.
  - [x] Static call hierarchy, resolved-name semantic tokens, read/write highlights and inferred-type/
    selected-parameter hints, including negative controls and immutable copying.
  - [x] Module-local rename probes established that recompilation alone misses silent capture.
    The follow-up now binds named labels and implements bounded local/private-parameter rename.
- [x] **5. Dependency/source boundary — explicit host API proven.** Identities, source locations
  and dependency replacement now have real adapter/server consumers.
  - [x] Serialized dependency identities match source declarations by compiler equality, separate
    overloads/modules, and reflect a replacement repository's changed/removed API.
  - [x] Copy a versioned dependency/source association into host-owned artifacts; wire repository
    replacement and reverse invalidation into the adapter, including transitive dependencies and
    pending cursor/compile cancellation. Server reanalysis preserves current document versions.
  - The automatic recompilation pass below now adds source-overlay builds for explicit roots/edges.
    Explicit editor configuration is now available; automatic discovery and a persistent workspace
    reference index remain separate.
- [x] **6. Lifetime and compatibility — bounded integrated API gate.** Repeated edits/cancellation/
  retention, output comparison, migration examples and the API requirements matrix.
  - [x] Repeated fresh snapshots, concurrent pool-free queries, recursive compiler-object exclusion,
    and byte-identical output with/without semantic inspection after timestamp normalization.
  - [x] Repeat source-graph rebuilds, repository replacement, cursor/rename cancellation and close/
    reopen; verify release of weakly observed compiler attempts/roots/pools and record latency.
  - [x] Executable integrated listener, constructor, record-pattern and pool-alias migration examples.
  - Extraction gate: compare each PR against its own base and rerun migration/examples independently.
    A prolonged interactive soak is still open; the bounded workload does not establish one.

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
| 1. Diagnostic audit — complete within the documented scope | Runtime `@Parsed` metadata and non-module source diagnostics are fixed. I4 fixes bound-generic typing/binary-AST failures. I7 fixes reproduced atomic AST result/owner errors; bounded ToIntExpression metadata probes pass without a reporting change. Historical capture counts are not an exhaustive audit. | `TypeInfoFinalCompositionTest` retains valid/invalid annotation controls; `EmbeddingDiagnosticsTest` pins positioned file/in-memory root diagnostics and discovery fallback; `CompilerBoundaryRequirementsTest` checks bound-generic artifact serialization; `CompilerEmissionAuditTest` checks atomic/switch output and diagnostic controls. |
| 2. Cursor-based incomplete analysis — bounded scope complete | Explicit cursors and module overlays support standalone statements, simple assignment/initializer values, single returns and final nested call arguments. Adapter/server ownership and cancellation now cover delivery; binary/conditional prefixes and arguments following an incomplete member expression are now covered by C10. | `XdkPartialAnalysisTest`, `XdkCursorRequestTest`, `XdkCursorServerTest` and packaged stdio cover unchanged source, overlays, lexical context, positions and stale-result rejection. Compiler mode remains Java-only. |
| 3. Completion and signature help — bounded POC complete | Scope/member completion, imported type names, static lookup and incomplete-call candidate fitting now have consumers. Candidate-specific expected types and named-argument mappings are copied. C11/L23 extends signatures to incomplete function values and ordinary constructors, including explicit class type substitution. C12/L24 retains missing enclosing call/group/index closers around a cursor. C13/L25 completes readable locals/parameters in empty final positional and pending named slots using compiler fitting. C14/L26 adds direct final bare-name prefixes with exact token replacement. C15/L28 adds implicit property/constant values with compiler read validation. L58 adds full compound-argument validation alongside the qualified/grouped/later-argument cases; literal synthesis remains a follow-up. C16/L29 adds specialized constructors and class inference; C17/L30 adds bounded declaration/tuple/literal recovery. C18/L31 adds anonymous construction without capture/body emission. C19/L34 adds single-dimensional array-size fitting and missing-bracket recovery. C20/L35 adds unqualified declaration type prefixes and structural header recovery; C21/L36 adds visible flat qualified type prefixes; C22/L37 adds written leaf names inside parameterized/compound types and bounded missing type closers. C23/L39 adds class/interface composition header recovery and visible-type queries without registering partial inheritance. C24/L41 adds registered formals, empty generic slots, parameterized qualifiers with substituted types and whole-final-token edits. C28/L53 adds trailing dots, qualifier-token edits, empty type operands, generic method/ordinary multiple-return declarations and module/package headers. L57 adds bound-labelled completion/hover and virtual-child lookup for written header formals, without fabricated semantic identities. L58 adds missing operands, lexical enclosing/imported property names, type-valued receiver functions and receiver-rewritten calls. Unfinished declaration names, arbitrary enclosing-instance enumeration and broader formal-member recovery remain outside the proven scope. C26 covers generic base-name prefixes; C27/L51 adds written function/sequence leaf types. Completed function calls have separate signature facts in E5/L20. An unfinished call never claims a selected overload. | Adapter/stdio requests cover declaration order, assignment state, narrowing, imports, access checks, generic receivers/methods, overload filtering, named slots, module overlays and token edits. Completed calls retain exact compiler selection. |
| 4. Other semantic consumers | Lookup, static call hierarchy, resolved-name tokens and bounded hints have consumers. Bounded rename includes named-label references and all-binding validation. L17 adds configured-graph exact references and ordinary instance-method override rename with dispatch checks. L18 adds ordinary property/accessor implementation targets; L19 adds concrete delegation, E5/L20 adds super-call facts, and L22 adds written Ref/Var annotation accessor targets. Native annotation storage and unknown runtime targets remain outside the proven surface. | `XdkSemanticLookupTest`, `XdkCallHierarchyTest`, `XdkPresentationTest`, `CompilerRenameRequirementsTest`, `XdkProjectQueryTest`, `XdkDependencyTest` and packaged stdio. Rename is advertised only for bounded targets and clients supporting versioned document edits. |
| 5. Dependency/source boundary — host API complete | `XdkDependency` binds bytes/source locations to a revision. Explicit source roots/edges now add automatic dependency builds with overlays. Editor configuration is available; automatic discovery and persistent indexing remain open. | `XdkDependencyTest` and `XdkLanguageServerTest` prove artifact replacement; `XdkProjectTest` and `XdkProjectServerTest` exercise source rebuilding, cancellation and unchanged consumer versions; `CompilerConfigurationTest` and the VS Code suite cover editor settings. |
| 6. Lifetime and compatibility | Integrated repeated-workload and migration regressions are complete; prolonged editor use and independent extracted-PR validation remain open. | `XdkRetentionTest` observes compiler results/roots/pools across graph rebuilds, repository replacement, cursor/rename queries and close/reopen. `EmbeddingApiCompatibilityTest` exercises listener and record migration; `CompilerBoundaryRequirementsTest` verifies copied facts and unchanged emitted bytes. |

Use these consumers to close an API requirements matrix: required fact, existing accessor or new
hook, compiler versus Kotlin ownership, complete/partial/unavailable behavior and a regression
that exercises it. Freeze the proposed embedding/AST surface only when each row has evidence or
an explicit scope decision. Formatting, editor polish, a production persistent index and full
implementations of every LSP handler need not delay that API decision. Unsupported protocol
capabilities remain unadvertised, and Tree-sitter stays the shipped default.

### Automatic dependency recompilation task list, 2026-09-23

Follow-on order agreed after automatic recompilation (bounded implementation complete):

- [x] Copy named argument labels and their exact token spans into attempt-owned invocation facts;
  resolve them to the selected source parameter in Kotlin. No additional AST state.
- [x] Prove bounded local/private-parameter rename by recompiling proposed edits and comparing all
  recorded bindings, including untouched references and selected calls. Require current snapshots,
  cancellation and versioned edits; keep wider member/override/workspace rename unavailable.
- [x] Exercise repeated source-graph rebuilds, dependency replacement, cursor/rename cancellation,
  close/reopen and compiler-object retention; record actual measurements.
- [x] Finalize compatibility examples, API ownership/limits, capabilities and manual playbook.
  Tests against each extracted PR's own base remain an extraction gate.

These steps use the explicit source graph and existing immutable snapshots. Automatic project
discovery is not a prerequisite.

This implementation extends the artifact host API to explicitly configured source modules.
The host supplies module names, source roots and dependency edges; automatic project discovery
remains separate. Kotlin owns scheduling and immutable input/artifact caches on the existing
serialized compiler worker. No Gradle invocation runs on an editor change.

- [x] **1. Capture inputs.** Snapshot disk membership/text and unsaved overlays for the dependency
  closure before compilation. Closing an overlay restores disk content; deleted sources stay absent.
- [x] **2. Rebuild dependencies and consumers.** Compile in dependency order, reuse only artifacts
  whose source and dependency inputs still match, and invalidate direct/transitive consumers.
  Reject cyclic/ambiguous source configurations before changing live state.
- [x] **3. Coalesce and cancel.** Debounce edits, retire obsolete queued/running compilations and
  cursor probes, and reject stale publication while preserving unrelated module sessions.
- [x] **4. Publish failures and recovery.** Keep diagnostics at each source URI/current version.
  A failed dependency must not silently reuse its previous successful artifact; correction must
  restore consumers without requiring an edit in them.
- [x] **5. Verify the full loop.** Two-module and transitive fixtures cover unsaved edits, member
  changes, dependency failures, deletion/restoration, close/reopen, rapid edits and stale results.
  Update capability/API notes and the manual playbook with the supported setup and limits.

The following rename/lifetime pass closes the bounded integrated API gate. Broad project discovery,
member/workspace rename and persistent indexing remain later work. PR extraction still requires
validation of every intermediate slice on its own prerequisites.

### Bounded rename, lifetime and compatibility, 2026-09-23

No discovery infrastructure was needed. The host's explicit graph and immutable module snapshots
provide the ownership boundary for this pass. Tree-sitter remains the shipped default.

| Required fact / behavior | Owner and implementation | Evidence / supported boundary |
|---|---|---|
| Written label to selected parameter | Java `InvocationBinding.Argument.label()` copies `Label(name, startPosition, endPosition)` before argument conversion; Kotlin uses selected method identity and visible register index | `CompilerRenameRequirementsTest`, `XdkRenameTest`: exact label spans, overloads, reordered/default and generic parameters |
| Module import identity | Kotlin copier reads the package's existing linked imported module | Imports bypass ordinary type-name resolution; dependency-backed rename now compares their compiler identities without spelling guesses or new AST hooks |
| Edit closure | Kotlin `XdkRename` selects local/private ordinary-method parameter occurrences across copied module views | Captures stay associated; shadowed lambda parameters stay unchanged. Public/lambda/constructor parameters, method-value escapes and member/override/workspace rename are excluded |
| Meaning preservation | Two fresh worker-only compilations replay the same immutable membership/text and dependencies, then compare every recorded occurrence and selected-call edge | Successful compilation alone is insufficient: silent capture of an untouched property is rejected. Unknown bindings or a failed attempt reject the edit. External constants are compared by compiler equality only on the worker; no compiler object escapes the proof |
| Current edits | Adapter retires rename work with module/dependency changes; server checks request lifetime and returns `documentChanges` with open-document versions | Closed files use a null version and their captured text/membership is rechecked before return. `XdkRenameServerTest`, request cancellation tests and packaged stdio cover the protocol. No temporary proof diagnostics or proposed source are installed |
| Ownership and release | Attempt collectors and temporary proofs remain on the compiler worker; source/artifact caches retain only detached inputs | `XdkRetentionTest`: 120 close/reopen cycles, 960 coalesced edits, alternating binary dependencies, source builds, accepted/canceled rename and partial cursor probes |
| Migration | Integrated Java client examples in `EmbeddingApiCompatibilityTest` | Stateful listener reporting/abort queries, rejected null listener, original constructors, current record patterns and deprecated runtime-pool alias |

The full-suite retention sample observed 482 weak references to results, source roots and pools;
zero remained reachable after document close or after shutdown and requested GC. Rebuild latency
was median 214 ms and p95 231 ms, including 100 ms debounce. The earlier focused run observed 481
references, median 212 ms and p95 239 ms; the count varies with cancellation timing. These are local,
bounded measurements, not a latency guarantee, retained-byte census or multi-hour editor soak.
The snapshot purity/output regression also checks pool-free copied values and identical emitted
bytes with/without semantic inspection after timestamp normalization.

Validation: JUnit XML reports 479 compiler tests (40 existing skips), 733 LSP tests (three existing
skips), and 15 packaged stdio tests (zero skips), with no failures/errors: **1,184 executed tests**.
The new rename, retention and migration regressions all ran. `spotlessCheck` passed. The stdio
client reconstructs an omitted `WorkspaceEdit.changes` as an empty map; its assertion permits that
while requiring actual versioned `documentChanges`. No remote CI or interactive editor run was made
in that pass; the editor follow-through above adds later evidence.

The canonical capability matrix and playbook now include the bounded rename policy and X53–X58.
The later editor follow-through covers the visible parameter rename and automated editor workload.
Per-PR output comparison and migration tests must still run
against each extracted slice's own prerequisites; the integrated result cannot establish that.

#### Compatibility and migration contract

| API change | Policy / required migration |
|---|---|
| `boolean log(ErrorInfo)` to `void log(ErrorInfo)` | Deliberate source/binary break. Recompile listener implementations and clients; report first, then ask `isAbortDesired()`. No return-type-only Java compatibility overload is possible |
| Null/ambient listeners | Supply an explicit non-null listener at reporting boundaries. Use `ErrorListener.collecting(...)` or `ErrorList` for stateful host reporting; derived `silence(PROBE)` for intentional speculation. Removed ambient setters/lookups have no compatibility shim |
| Runtime pool name | Deprecated `getConstantPool()` still delegates to `ensureRuntimePool()` and retains its runtime-initialization behavior. Compiler clients use `Compilation.pool()` |
| `Compilation` record | Three- through seven-argument constructors remain. Current eight-component pattern includes module, file, ast, sourceTrees, callBindings, functionBindings, constructorBindings and initializerBindings. Prefer accessors/`forFile(...)` for clients not needing deconstruction |
| `PartialAnalysis` record | Three-, four- and five-argument constructors remain; current six-component pattern adds callBindings, cursorBindings and functionBindings to sourceTrees, sites, pool |
| `CursorBinding` record | Three-, six-, seven- and eight-argument constructors remain; the nine-component pattern adds argumentProperties after argumentValues. Function candidates expose types/positional mappings; argumentValues contains accepted locals/parameters and argumentProperties contains accepted property/constant identities and validated types. |
| `InvocationBinding.Argument` record | Three- and four-argument constructors remain. Current five-component pattern includes `label`; positional and legacy construction has a null label. A legacy `named=true` is not proof that a label span was supplied |
| LSP rename | Additive, bounded and negotiated through client `documentChanges` support. No unversioned fallback. The default adapter method retains existing synchronous adapters; compiler rename runs asynchronously |

For a collecting host, replace boolean-report control flow with separate reporting and state checks:

```java
ErrorListener errors = ErrorListener.collecting(hostDiagnostics::add);
errors.error(code, site, arguments);
if (errors.isAbortDesired()) {
    return;
}
```

A bare `ErrorListener` lambda implements only `log`; it does not remember serious errors or abort
state. Use the stateful factory for the compiler pipeline. The migration test demonstrates this
without bootstrapping the runtime. Use `Compilation.forFile(file)` for partial file results rather
than positional nulls. Constructors retained for compatibility do not promise unchanged record
pattern arity, serialization shape or equality semantics across releases.

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
| Incomplete call candidates and argument slots | Ordinary compiler argument fitting, captured in `CursorBinding.Candidate` | Method and ordinary-constructor candidates, explicit constructor class type substitution, written mappings/expected types and positional/named slots. No best-overload selection is claimed. |
| Compatible argument values | `PartialCallResolver` trial fitting into immutable `CursorBinding.argumentValues`/`argumentProperties`; original prefix token on the incomplete call | Visible readable locals/parameters and implicit properties/constants in empty final positional or pending named slots, including direct final bare-name prefixes; compiler read validation, inference/conversions remain authoritative. Kotlin supplies copied symbols/types and insertion or exact token replacement edits, without selecting an overload. |
| Incomplete function signatures | Trial callee/argument validation, captured in `CursorBinding.FunctionCandidate` | Full function parameter/return types and positional mappings without method targets, names or defaults; private speculative errors reject candidates without replacing document diagnostics. |
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

### Automatic source dependency recompilation, 2026-09-23

`XtcLanguageServer.replaceCompilerSourceModules(...)` installs an immutable source graph described
by `XdkSourceModule(name, uri, dependencies)`. The host supplies exact compiler module names,
conventional file roots and direct source dependency edges. Binary dependencies can still be
installed separately. Duplicate names/roots, overlapping source trees, reserved core module names
and cyclic source graphs are rejected before changing live state. A compiled name that disagrees
with the configured name reports `PROJECT-MODULE` at the source root.

The server now refreshes source consumers automatically after open/change/close/save and watched
file events. It preserves each open document's version, including unchanged consumers. Compilation
captures the entire transitive source closure before compiling any module, then works in dependency
order on the existing compiler worker. Unsaved buffers override disk; closing a buffer restores
disk membership/text. No temporary files or Gradle builds are needed.

The cache retains immutable input values, detached artifacts, diagnostics and source URI sets;
it retains no dependency AST, pool or compilation. Reuse requires equal source membership/text/URI
aliases and dependency revisions. Current target modules still produce fresh semantic/structural
views. Closing sessions prunes unused artifacts; replacing source configuration or shutting down
clears them. A 100 ms debounce coalesces edits before worker dispatch; edits cancel queued/running
consumer compilations and cursor requests. Publication and cache writes reject obsolete attempts.

Unreadable/deleted roots report `SOURCE-UNAVAILABLE`. Invalid dependency source publishes the
compiler's original diagnostics, and affected consumers report `DEPENDENCY-FAILED` without using
an older artifact. Blocked consumers expose no semantic or structural views until their inputs
recover. Correction rebuilds consumers without requiring an edit in them. Shared dependency
diagnostics remain while another open consumer owns them. Artifact-only external diagnostics keep
the existing related-information policy.

This completes an explicit host-configured build loop, not automatic project discovery. Initialization
and live editor settings now supply these roots (see the editor follow-through above).
Cyclic multi-module compilation, a persistent workspace reference index and stage-level incremental
compilation remain unsupported. Hosts must keep declared source
dependency edges accurate; missing source edges are not inferred from unresolved imports. Filesystem
changes require the normal client notifications. Compiler mode remains Java-only and opt-in.

Evidence lives in `XdkProjectTest` and `XdkProjectServerTest`: input snapshot timing, artifact reuse,
rapid-edit coalescing, cancellation, failed libraries, transitive changes, binary replacement,
unsaved members, source deletion/restoration, shared diagnostics and close/reopen. See the manual
playbook for a reproducible two-module host setup (X45–X52).

Validation: the full LSP suite ran 713 tests with three existing skips; all 14 packaged compiler-stdio
tests passed. That is **724 executed tests with zero failures/errors**. Two additional regressions
then extended the project suites to six adapter and seven server cases; all 13 passed in the final
focused run without skips. Kotlin formatting, `spotlessCheck` and `git diff --check` passed. No Java
or Gradle implementation changed. Interactive editor checks and multi-hour retention measurements
were not run; the latter remains in task 6.

### Versioned dependency/source host API, 2026-09-23

`Compilation.toDependency()` pairs the successful compilation's emitted bytes with copied source
declarations. Serialization fixes each declaration's constant-table index. `XdkDependency` retains
only bytes and immutable Kotlin locations; a SHA-256 revision covers both the artifact and its
source index. A copied `SymbolKey(module, revision, index)` is stable across consumer attempts using
that exact artifact. It is not a name-based identity or a key that survives rebuilding a library.
Snapshot-local symbol/type UUIDs remain separate. Binary-only artifacts created with
`XdkDependency.fromBinary(bytes)` deliberately have no source locations.

Each attempt opens fresh compiler structures and a fresh input repository. Artifact constants are
used only to match identities by compiler equality. Semantic metadata is read through the matching
constant in the **linked consumer pool**: reading it from an unlinked artifact reproduced a missing
core-class failure and is now covered by the dependency tests. Existing AST accessors and constant
equality suffice; **no new Java API, AST field or clone rule** was needed.

The host installs a complete immutable artifact set with
`XtcLanguageServer.replaceCompilerDependencies(...)`. Under the server's publication lock, the
adapter replaces that set, retires affected analyses/cursors, then the server recompiles open
consumers with their current text/version. Successful linked-module sets identify direct and
transitive consumers. Pending and failed attempts are conservatively invalidated because their
import sets may be incomplete. Unrelated successful sessions remain available. Reinstalling the
same revisions is a no-op; duplicate module names and attempts to replace bundled core/bootstrap
libraries are rejected before state changes.

For an adapter-only host, `XdkAdapter.replaceDependencies(...)` performs invalidation and returns
the affected scope keys; the host schedules their new analyses and publications. A host may query
definitions/type-definitions into matching dependency sources. Current-module implementation
chains can also return an inherited dependency body when its source index exists. This does not
search for implementations/references throughout all dependency sources, and does not add external
call/type hierarchy. Missing source stays unavailable.

Host usage, after compiling the library on its serialized compiler worker:

```kotlin
val dependency = libraryCompilation.toDependency()
server.replaceCompilerDependencies(listOf(dependency))
```

This artifact API alone does not discover projects, watch binary repositories or track edited
dependency source. Its subsequent source-graph integration above now handles source builds and
overlays for explicitly configured modules. Artifact-only hosts still own matching source/artifact
versions and must replace them together. Old artifact ranges are not a promise about independently
edited text. Standard editor launch supplies bundled libraries plus any explicit source modules in editor
settings. Binary artifacts still require the host API; automatic project discovery remains open.

Evidence: nine `XdkDependencyTest` cases cover detached keys, mutable-byte isolation, overloads,
generics, inherited bodies, source-less artifacts, multi-file sources, source-module precedence,
transitive replacement, removals/restoration, duplicate rejection and compile/cursor cancellation.
The server regression changes a dependency return type, publishes the resulting consumer error at
the unchanged document version, restores the old artifact and verifies both diagnostic clearing
and dependency-source navigation. The earlier worker-only boundary probes remain as lower-level
controls. See L13 for extraction ownership.

Validation: 702 LSP tests (three existing skips) and all 14 packaged compiler-stdio tests completed
with zero failures/errors: **713 executed tests passed**. All nine dependency cases and twelve
server cases ran without skips. `spotlessCheck` and `git diff --check` passed. This pass changed no
Java or Gradle code; the preceding compiler-suite result remains the compiler baseline, not a new
compiler-suite run. No interactive editor run is claimed.

### Remaining consumer verification, 2026-09-23

Lookup verification adds instantiated generic return types, covariant overrides and retained
snapshot results across close/reopen. Static call hierarchy now copies each selected call's nearest
source method/lambda owner. Lambda calls remain lambda-owned; anonymous methods do not become
factory calls. Incoming/outgoing results group actual sites, work across module members and reject
old item IDs after an edit. Function-value dispatch, runtime virtual targets, constructor edges,
property initializers/accessors and source-less dependencies are not inferred.

Resolved-name semantic tokens classify compiler identities, declaration/static/abstract/readonly
modifiers and assignment usage. Assignment receivers remain reads; compound and increment targets
are read/write (LSP highlights show WRITE). Unresolved names have no fabricated token. Inlay hints
show inferred local types after successful compilation and selected positional parameter names,
excluding explicit named arguments and synthetic defaults. Failed inference cannot present its
temporary `Object` type as an inferred hint. The shared token legend is only protocol data; no native parser
or Tree-sitter fallback is loaded.

| Required fact | Owner / API | Evidence and limits |
|---|---|---|
| Source caller | Existing method component, `LambdaExpression.getLambda()`, parent links; copied Kotlin callable table | `XdkCallHierarchyTest`: overloads, recursion, lambdas, anonymous methods, static interface selection, module files, stale IDs. Complete module only. No added AST fields/accessors. |
| Read/write usage and modifiers | Existing assignment/lvalue/sequence syntax, Register/Component metadata; Kotlin copying | `XdkPresentationTest`: direct/compound/increment writes, receiver reads, captures, shadowing, declarations and static methods. Partial snapshots classify only facts actually resolved. |
| Inferred local types | Existing `VariableTypeExpression` child and validated register type | Successful compilation required for type hints; explicit types and failed-inference placeholders omitted. Copied hints need no compiler pool. Lambda return hints remain absent. |
| Written named-argument status | New `InvocationBinding.Argument.named` component, captured with the existing source mapping before argument rewrites | Selected overload and named/default controls. Necessary because validation rewrites the written arguments. No new AST state or clone obligation. |
| Rename closure and conflict detection | Attempt-owned argument label spans, selected method/index, existing parameter registers and linked import identities; Kotlin recompile-and-compare proof | `CompilerRenameRequirementsTest`, `XdkRenameTest` and server/stdio tests: captured locals, private named parameters, untouched binding comparison, closed-member input checks, cancellation and versioned edits. Public/lambda/constructor parameters, method escapes and member/workspace rename remain unavailable. |
| Cross-module identity/source join | Compiler `IdentityConstant.equals` and existing declaration tokens; detached Kotlin artifact/source index | `CompilerBoundaryRequirementsTest` supplies the worker-level proof. The subsequent `XdkDependencyTest` verifies consumer source locations and revisioned keys across attempts. Never use display strings or snapshot UUIDs as persistent keys. |
| Detached lifetime and emission | Immutable Kotlin facts; explicit reporting inspection on the compiler worker | Twenty retained generations queried concurrently; recursive graph check excludes compiler objects. Inspection leaves emitted bytes unchanged after normalizing only the compilation timestamp. This is not an extracted-PR/base comparison or a heap-leak measurement. |

Rename must compare bindings of **unmodified** references too: changing `local` to `value` can
silently capture a previously resolved property use. A successful compile is insufficient. Named
argument labels needed source-to-parameter associations at this milestone; the original
`named` flag did not supply those spans. The rename follow-up now supplies immutable label records. Member/override rename
also needs an explicit affected-source boundary. The later bounded consumer is recorded above.

Dependency source lookup is possible with existing compiler identities while the worker owns both
source and consumer compilations. A source index must detach that association and tie it to the
exact dependency artifact/source revision. Fresh repositories in the probe prove changed-input
compilation, not automatic invalidation of existing adapter sessions. The subsequent dependency
host API pass above supplies the detached association and adapter/server invalidation consumer.

Compatibility: `new InvocationBinding.Argument(start, end, index)` remains supported and describes
a positional argument. Producers retaining an explicit label use the four-argument constructor;
record-pattern consumers must migrate from `Argument(var start, var end, var index)` to
`Argument(var start, var end, var index, var named)`. This branch's new tooling records should be
extracted in their final shape; preserving a constructor does not preserve record-pattern arity.

Validation: 474 compiler tests (40 existing skips), 692 LSP tests (three existing skips) and all
14 packaged compiler-stdio tests completed with zero failures/errors: **1,137 executed tests**.
The 15 lookup, five call-hierarchy, five presentation, three rename and four boundary/lifetime
cases all ran without skips. `spotlessCheck` and `git diff --check` passed. The manual playbook now
has X1–X44; its new Consumers module compiles with `xcc`. Interactive editor actions have not been
run in this pass. No Gradle logic or backend default changed.

### Type-definition and implementation consumer pass, 2026-09-23

Type-definition copies declaration links from existing type identities. Modifiers unwrap;
parameterized values navigate to the nominal type rather than its generic arguments. Nullable,
union and intersection operands can contribute multiple available source targets; a difference
type follows its positive operand. Aliased values follow their resolved type while a written alias
or formal parameter can point to its own declaration. Selected calls expose instantiated return
types; method declarations expose their declared returns. Missing library source and unresolved
names return no invented target.

Implementation lookup inspects successful source TypeInfo explicitly through the host listener.
It copies nominal ancestor relationships for concrete source types and method-chain identities for
actual source bodies. Generic overrides, inherited bodies and interface defaults are preserved;
overloads and unrelated same-named methods are separate. Anonymous classes contribute their
user-written method bodies while synthetic type declarations are excluded from type-implementation
results. Captured values keep their source type targets. Unadopted mixins are not implementations
of their `into` constraint; mixin bodies are copied through composed hosts. Type lookup is
nominal/declaration-level, not structural assignability or a search over generic instantiations.
Property/accessor implementations, synthetic redirects/delegation and conditional compositions
remain outside the proven surface. This initial lookup pass did not cover dependency targets;
the subsequent dependency host API pass adds indexed definition/type-definition and inherited-body
links, without searching every dependency's implementations.

`semanticSnapshots()` remains passive. `semanticSnapshots(errors)` opts into implementation
inspection on the serialized worker; serious inspection failures/cancellation discard its edges.
The same listener receives any TypeInfo diagnostics before publication. Request threads query
immutable Kotlin maps of IDs and source locations, without compiler objects or ambient pools.
There are **no new Java embedding methods, AST fields/accessors or clone rules** in this pass.
The adapter's existing single-target type-definition method remains; its additive plural hook
carries union results through the existing LSP handler. Edits use the existing module invalidation
and request-version checks. Tree-sitter remains default; only XdkAdapter advertises the two features.

Evidence: `XdkSemanticLookupTest` covers type targets, narrowing, generics, method chains, default and
mixin/anonymous bodies, captured values, unrelated overloads, closed members, overlays, parse failure, cancellation and queries
on another thread without a constant pool. Packaged stdio covers capabilities and cross-file lookup.
The [manual playbook](../lang/doc/manual-test-plan.md#xdkadapter-playbook) includes matching user steps.

Validation: a fresh full LSP run reported 672 tests with three existing skips and zero failures or
errors; all 13 packaged compiler-stdio tests passed. That is 682 executed tests, including all
12 focused semantic-lookup cases. `spotlessCheck` and `git diff --check` passed. All four baseline
playbook modules compiled through the installed compiler. No Java or Gradle logic changed in this
lookup pass. The preceding scope/call work and initial playbook were pushed as `672bc130b`.

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

This historical list records the boundaries after the first three passes, not the current backlog.
Later passes supersede its module deferrals, parser-recovery decision and final-type investigation.
Use the execution checklist at the top for current work and the capability matrix for current limits.

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
readiness checks at that date; subsequent completion and dependency milestones are recorded above.

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

**Current capability limits (updated 2026-09-24).** Deferred capabilities are still visible outages
when choosing the compiler backend: incomplete
syntax can prevent an assembled module AST; recovered per-file syntax supports structural features,
while navigation requires current semantic results. Host-indexed dependencies now supply definition,
type-definition and inherited-body links, but not a persistent cross-module reference/implementation
index or external hierarchy. Completion/signature help have the bounded support
described above; formatting, code actions, document links and linked editing remain unavailable.
Bounded local/private-parameter rename and configured-graph ordinary instance-method override
rename are available to clients supporting versioned document edits. Exact references span all
configured sources. Binary contracts and unsupported dispatch chains fail closed; ordinary `super`
calls participate in binding comparison without renaming the keyword. Discovery of outside consumers
and wider rename remain outside the proven surface. Static call hierarchy, resolved-name tokens and
bounded inlay hints now have consumers.
Type-definition and nominal type/method/property implementation lookup now
have module and indexed-dependency consumers as described above, including written Ref/Var
annotation accessors and concrete delegation. Native annotation storage, synthetic redirects and
interface-valued/cyclic delegate targets remain unavailable.
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

### Commit grouping record

The detailed PR sections below identify the earlier source commits and the intended final behavior.
This table records the later integrated commits that extend those groups. Rows with several IDs
require splitting by the stated responsibility during PR preparation, with tests accompanying the
behavior they exercise. The table below is the PR-to-commit map; this record explains mixed-commit boundaries.
A hash appearing in several PR rows means portions of that commit, not repeated cherry-picks.
Keep the final implementation and the later corrections together; do not replay superseded designs.

| Commits on `lagergren/errs` | Future PR groups | Grouping boundary |
|---|---|---|
| `9e951df3e` | C1, E1, I3, L1, L2 | Branch/silence abort state and tee queries belong to C1; passive footprint and failure handling to E1; dependency/CI wiring to I3; diagnostics/capabilities to L1; document versions, cancellation and lifecycle tests to L2. |
| `ddc0b063d` | R1, E1, E2, I3, L1, L2, L3, L4 | Repository production/tests go to R1, embedding failure diagnostics to E1, Java source accessors to E2, build/test wiring to I3/L1, source attribution and bundled resources to L1, process exit handling to L2, structural ranges to L3 and navigation to the final L4 snapshot. Do not restore the superseded XdkResolution walker. |
| `45fa0ab13` | E1, C4, L1, L2 | Split empty-buffer regression, TypeInfo regressions, packaged startup/protocol checks and shutdown/lifecycle checks with their respective behavior. The later C4 tests extend the same suite. |
| `f98b0fe87` | E1, E2, C4, L1, L3, L4 | Deprecated runtime-pool alias goes to E1; qualified/formal/lambda/anonymous source bindings to E2; metadata contract and replay regressions to C4; backend selection to L1; structural extraction to L3; immutable Kotlin semantics to L4. |
| `c33b013eb` | C4, E3, L5, L6 | Final-TypeInfo fixtures and their test-only module variants go to C4; Java module input, source hooks and fatal forwarding to E3; Kotlin module sessions/publication to L5; direct hierarchy facts and requests to L6. |
| `60054eb4c`, `d24f1be1f` | C5, C6 | Normal parser recovery, per-file results and structural consumers form C5. Explicit incomplete-analysis APIs, parser sites and direct compiler-consumer tests form C6. |
| `91b5182b8` | E4, L7 | Attempt-owned Java call provenance and immutable result maps go to E4; copied Kotlin call/member models and their consumer tests go to L7. |
| `28e9fd540` | C4, E3, C7, L8 | Annotation TypeInfo diagnostics and their regressions go to C4; module-root diagnostic forwarding goes to E3; cursor/module partial-analysis APIs go to C7. The Kotlin partial-model extension for module sources accompanies L8 because it requires C7. |
| `32ac93a5a`, `24b8f5910`, `c97c36a88` | C7, L8 | Combine compiler value-context, typed-prefix and closing-parenthesis support in C7. Combine serialized cursor requests, completion/signature consumers and server tests in L8. |
| `672bc130b` | C8, C9, L9 | Separate cursor-scope capture from incomplete-call argument fitting; put copied Kotlin scope/candidate queries and protocol consumers in L9. |
| `cdd9bf67f` | E4, L10, L11, L12 | Written named-argument provenance belongs to E4; type/implementation lookup, call hierarchy and tokens/hints form their respective consumer PRs. Assign boundary and rename-requirement probes to the source-binding/snapshot behavior they verify. |
| `39f7862bb` | L13 | Versioned dependency artifacts, source lookup, repository replacement and their adapter/server regressions. |
| `266b48784` | L14 | Configured source graphs, automatic dependency recompilation and consumer invalidation, with project/server regressions. |
| `7b13e0980` | E4, L15; compatibility/lifetime checks with their owning APIs | Label provenance and preserved InvocationBinding constructors belong to E4; bounded rename and its protocol tests belong to L15. Split EmbeddingApiCompatibilityTest by the API each assertion covers; keep the combined retention workload after L14/L15. |
| `6372ba07d` | L16 | Editor initialization/settings, configuration validation and VS Code acceptance; rename acceptance additionally requires L15. |
| `855ec569f` | I2, R1, I3, C1, C2, C3, C4 | Split the synchronization fixes and regressions by the [assignment table](#synchronize-extraction-improvements-back-into-errs-2026-09-24). In particular, defer the unused Reporting method removal to C4. I1 already had equivalent coverage and receives no new code from this commit. |
| `c97ea9e6f` | C1, C2, C3, C4 | Runtime-listener test isolation and collecting documentation go to C1; resolver comment cleanup to C3. Split the restored README migration sections across C1/C2/C3/C4. |
| `5c0a3dce8`, `a663b5511`, `9c432f778`, `643ca65f0`, `e0e321f82` | Planning history | These record extraction evidence and the corrected development workflow. They are not implementation commits to cherry-pick into compiler PRs. |

Documentation changes within implementation commits accompany the relevant capability or API;
consolidate historical progress notes into accurate final documentation for each future PR. Add
new commit assignments here as development continues on `errs`. The existing extraction hashes
above identify old candidate patches, not additional changes to merge into the integrated branch.

### Dependencies and eventual landing order

| ID | Scope | Source commits on `errs` (take only the assigned portions) | Prerequisites |
|---|---|---|---|
| I1 | Preserve distinct diagnostics and name in-memory sources | `19e567e55`, `fad701097` | Independent |
| I2 | Guard ambient constant-pool reads | `cae4f9452`, `610873fb6`, `855ec569f` | Independent |
| I3 | Establish compiler-consumer test wiring without requiring IDE builds | `bb3c4c62c`, `566bc0c4a`, `7097892b6`, `9e951df3e`, `ddc0b063d`, `855ec569f` | Independent foundation; activate required suites as they land |
| R1 | Propagate repository read failures and preserve retry behavior | `ddc0b063d`, `855ec569f` | Independent; embedding regression joins E1 |
| C1 | Define the host listener contract and reporting API | `3896e7409`, `920cc3858`, `34b9f8ea2`, `0b4261ca0`, `e5154fb24`, `8f3870386`, `14189a716`, `469cecf1b`, `dc58262cd`, `9e951df3e`, `855ec569f`, `c97ea9e6f` | Independent of ownership work; coordinate public API compatibility |
| C2 | Require explicit listeners and explicit reasons for silence | `b1c71fb4e`, `469cecf1b`, `63bd09bb9`, `6d342f915`, `01e816622`, `9165c00b0`, `dc58262cd`, `8ac04ec93`, `c60bdb26d`, `716c7f118`, `d3d28348f`, `9bbeb8348`, `855ec569f`, `c97ea9e6f` | C1 |
| C3 | Scope parser/resolver reporting and statement validation state | `01e0161be`, `259f8135e`, `fe778a92b`, `9bbeb8348`, `9cdd84519`, `855ec569f`, `c97ea9e6f` | C2 |
| E1 | Return useful compilation results through the embedding API | `53b13d7a4`, `bb3c4c62c`, `60a451a3f`, `91d1b08e1`, `535b9d80e`, `9e951df3e`, `ddc0b063d`, `45fa0ab13`, `f98b0fe87`, `3ccf9efe4` | I1, C2, R1; I3 for compiled-XDK tests |
| E2 | Expose resolved source bindings, lambda origins and qualified segments | `956d56f41`, `ddc0b063d`, `f98b0fe87` | E1; I3 for direct compiler-consumer tests |
| C4 | Replay TypeInfo diagnostics and remove ambient listener ownership | `ed8d3f278`, `14189a716`, `ff20ca0bc`, `0af497641`, `a49b326f5`, `e383a818a`, `f98b0fe87`, `c33b013eb`, `28e9fd540`, `855ec569f`, `c97ea9e6f` | C2, C3; E1 and I3 for the downstream regression tests |
| L1 | Connect the diagnostic-only XDK adapter and prove publication | `bb3c4c62c`, `535b9d80e`, `26c8fa9c1`, `849bb7a04`, `9e951df3e`, `ddc0b063d`, `45fa0ab13`, `f98b0fe87` | I1, I3, C4, E1 |
| L2 | Complete cancellation and document lifecycle handling | `d92f93fb3`, `9e951df3e`, `ddc0b063d`, `45fa0ab13` | L1; includes the listener cancellation decorator |
| L3 | Add the outline and structural AST features | `91d1b08e1`, `956d56f41`, `ddc0b063d`, `f98b0fe87` | E1, L2 |
| L4 | Snapshot semantic facts in Kotlin and use them for navigation and hover | `956d56f41`, `ddc0b063d`, `f98b0fe87` | E2, L3; I2 for ambient-pool handling |
| E3 | Compile source trees with host text/membership and member cancellation | `c33b013eb`, `28e9fd540` | E1, C3; L2's listener decorator for cancellation; I3 for compiled-XDK tests |
| L5 | Add module sessions, per-file publication and cross-file navigation | `c33b013eb` | E3, L2, L4 |
| L6 | Copy direct inheritance edges and support source type hierarchy | `c33b013eb` | L5 |
| C5 | Recover syntax and expose per-file partial source results | `60054eb4c` | E3; L5 for the structural LSP consumer |
| C6 | Analyze a bounded incomplete statement through an explicit API | `d24f1be1f` | C5 and E2; I3 for compiler-consumer tests; no new LSP capability |
| E4 | Return attempt-owned selected-call bindings without new AST fields | `91b5182b8`, `cdd9bf67f`, `7b13e0980` | E2 and C6; retain existing result constructors and document record-pattern changes |
| L7 | Copy selected calls and inspect bounded partial receiver members | `91b5182b8` | E4, C6 and L4; no new protocol capability |
| C7 | Extend partial analysis to source cursors, module overlays and value contexts | `28e9fd540`, `32ac93a5a`, `c97c36a88` | C6 and E3; no new protocol capability |
| L8 | Connect bounded completion/signature requests with cancellation and version checks | `28e9fd540`, `32ac93a5a`, `24b8f5910`, `c97c36a88` | C7, L7 and L5 |
| C8 | Capture cursor scope and resolve visible type names without AST caches | `672bc130b` | C7 and E4; I3 for direct compiler consumers |
| C9 | Fit incomplete-call candidates and preserve named argument slots | `672bc130b` | C8; reuse compiler argument fitting without changing full-call selection |
| L9 | Consume scope, static lookup and candidate-specific expected types | `672bc130b` | C8, C9 and L8 |
| L10 | Copy type-definition and compiler implementation targets | `cdd9bf67f` | L5, L6 and L7; no C8/C9 dependency |
| L11 | Copy source callers and expose static call hierarchy | `cdd9bf67f` | L5 and L7; no new Java/AST API |
| L12 | Classify resolved names and expose bounded inlay hints | `cdd9bf67f` | L6 and L7; carry written named-argument status in E4 |
| L13 | Export versioned dependency source indices and replace host repositories | `39f7862bb` | L4, L8 and L10; no new Java/AST API |
| L14 | Rebuild configured source dependencies and refresh consumers automatically | `266b48784` | L5 and L13; Kotlin host scheduling only |
| L15 | Validate local/private-parameter rename and publish versioned edits | `7b13e0980` | E4 label provenance, L4/L5/L8; L13/L14 for dependency invalidation coverage |
| L16 | Configure source graphs from editor initialization and settings | `6372ba07d` | L14; rename acceptance cases additionally require L15 |
| L17 | Query references and validate method override rename across the configured graph | `332003f0a` | L13/L14/L15 and L4/L5; editor acceptance additionally requires L16 |
| L18 | Copy ordinary property/accessor implementations and indexed dependency targets | `714042da5` | L10/L13; editor acceptance additionally requires L16; independent of L17 |
| L19 | Follow concrete delegation to written source targets | `570a7e870` (delegation; post-L18 assignment above) | L18/L13 |
| E5 | Add function signature facts and selected super bodies | `570a7e870` (compiler call facts) | E4 |
| L20 | Consume function signatures and super calls for navigation/hierarchy/rename proof | `570a7e870` (Kotlin call-fact consumers) | E5, L7/L11/L15; graph consumer L17 |
| C10 | Retain compound/conditional cursor holes and later arguments | `570a7e870` (cursor parser recovery) | C7/C9; editor consumer L8/L9 |
| I4 | Correct bound-generic function types and binary AST emission | `570a7e870` (NameExpression fix and regressions) | Independent production fix; I3/E1 test harness |
| I5 | Attribute annotation warnings to the contributed property | `570a7e870` (PropertyInfo diagnostic ownership) | Independent production fix; C4 replay tests |
| L21 | Position source-structure diagnostics at declaration tokens | `570a7e870` (source diagnostic positioning) | L1/L5, I5 |
| L22 | Copy written Ref/Var annotation accessor implementations | `abbc89f88` (annotation lookup); follow-up above | L18/L13; concrete-delegation proof L19; editor runner L16 |
| C11 | Capture incomplete function candidates and ordinary constructor syntax | `abbc89f88` (incomplete signatures); follow-up above | C7/C9; constructor and record-pattern migration proof |
| L23 | Deliver function/constructor signature help while typing | `abbc89f88` (incomplete signatures); follow-up above | C11, L9, E5/L20, I6; editor runner L16 |
| I6 | Preserve argument errors when fitting function-call returns | `abbc89f88` (`InvocationExpression.testFunction` and call-site regression) | Independent production fix; I3/E1 harness and E5 binding assertion |
| I7 | Preserve atomic result/receiver types and handle singleton/outer owners; audit switch metadata | `d0809cd83` production/tests; `aa65860d0` audit documentation | Independent production fix; I3/E1 test harness and C4 warning-replay controls |
| C12 | Retain missing enclosing delimiters around an explicit cursor | `aa65860d0` compiler portion; delimiter recovery above | C7/C10; no public API shape change |
| L24 | Prove delimiter recovery through the adapter, protocol and editor | `aa65860d0` host portion; delimiter recovery above | C12, L8/L9, C11/L23; editor runner L16 |
| C13 | Fit proposed visible argument values in the compiler | `f2896916b` compiler portion; argument-value completion above | C8/C9/C11; constructor and eight-component record-pattern migration proof |
| L25 | Complete compatible locals/parameters at missing argument slots | `f2896916b` host portion; argument-value completion above | C13, L8/L9/L23; editor runner L16; X25 synchronization in the same commit belongs to L16 |
| C14 | Retain typed argument prefixes in their call fitting context | `dc3218d81` compiler portion; typed argument-prefix completion above | C13, C12; additive syntax token/factory/accessors and compiler prefix filter |
| L26 | Replace typed argument prefixes with compiler-fitted values | `dc3218d81` host portion; typed argument-prefix completion above | C14, L25, L24; editor runner L16; X79–X80 and lifecycle/protocol controls |
| C15 | Fit implicit property/constant reads as argument values | `ace732d5c`; property argument completion above | C13/C14; additive immutable facts, preserved constructors and compiler probes |
| L28 | Copy property argument facts and verify editor acceptance | `ace732d5c`; property argument completion above | C15, L25/L26; L16 runner; X81–X82, protocol and lifecycle controls |
| C16 | Specialized constructor preparation and cursor fitting | `46d6c1442` (compiler constructor hunks); specialized-constructor section above | C11, C13–C15 |
| L29 | Specialized-constructor copying and editor consumers | `46d6c1442` (constructor consumers); X83–X85 | C16, L23/L25/L26/L28, L16 |
| C17 | Declaration/tuple/literal recovery and source-owned initializers | `46d6c1442` (compiler recovery hunks); recovery section above | C12 and cursor collectors |
| L30 | Declaration/literal recovery consumers and editor controls | `46d6c1442` (recovery consumers); X86–X87 | C17, L24, L16 |
| C18 | Anonymous construction ownership and constructor fitting | `cd1732d42` compiler portion; anonymous-constructor section above | C16 and cursor/listener foundations |
| L31 | Anonymous-constructor labels, consumers and ownership controls | `cd1732d42` host portion; X88–X89 | C18, L29/L30, L16 |
| L32 | Complete shared editor catalog and native consumers | `019d3f811`; shared-scenario section above | L16, L27, L31 |
| L33 | Native method/constructor signature help and rejected-call clearing | `ec85fcb98`; native-signature section above | L32 and compiler signature slices |
| C19 | Array-dimension cursor parsing and constructor fitting | `0b800c392` compiler portion | C16 and C12/C14 foundations |
| L34 | Array-dimension adapter/protocol/editor proof, shared X90 | `0b800c392` host portion | C19, L32/L33 |
| C20 | Syntax-only unfinished headers and enclosing type lookup | `68a291c0f` compiler portion | C12/C14, C17 |
| L35 | Header completion/structure consumers and shared X91–X92 | `68a291c0f` host portion | C20, L32/L33 |
| C21 | Qualified header syntax and compiler-visible nested type lookup | `2478bf7fb` compiler portion; qualified-header section above | C20 |
| L36 | Qualified-header API/adapter/editor proof, shared X93 | `2478bf7fb` host portion; qualified-header section above | C21, L35 |
| C22 | Parameterized/compound header leaf selection and bounded type closers | `3571d265d` compiler portion; parameterized-header section above | C21 |
| L37 | Parameterized/compound header API/editor proof, shared X94 | `3571d265d` server tests; `3c68c2dfe` X94 catalog/consumer hunks; parameterized-header section above | C22, L36 |
| L38 | IntelliJ native assertion parity and additional shared consumers | `3c68c2dfe` native helpers, assertions and coverage metadata; native-parity section above | L32/L33 and consumed compiler scenarios |
| I8 | Target the released IntelliJ free feature set and update LSP4IJ | `4e46becb6` IDE/LSP4IJ catalog and compatibility documentation | Existing IntelliJ plugin; independent of Java embedding changes |
| L27 | IntelliJ compiler configuration and automated playbook | `4e46becb6` client, integration test source set/task and playbook | I8, L16 and the existing compiler features exercised by each case |

Suggested landing order: I1, I2 and R1 first; I3 alongside C1; then C2, C3, E1, C4, L1 and L2.
E2, L3 and L4 can follow without delaying the diagnostics milestone; E3, L5 and L6 extend it
additively. C5 follows with structural recovery; C6 isolates the explicit partial-semantic probe.
E4 and L7 separate compiler provenance collection from the Kotlin call/member models. C7 extends
the partial compiler API; L8 proves the asynchronous editor consumer and protocol delivery.
C8/C9 isolate live scope capture from tentative call fitting; L9 adds their Kotlin/protocol consumers.
L10, L11 and L12 can follow their listed dependencies independently of the later completion/scope slices.
L13 follows with an explicit artifact host API; L14 adds automatic rebuilding for configured source
modules; L16 exposes those roots/edges through editor configuration. Automatic project/build
discovery stays separate. L17 adds configured-graph references and method rename. These are
thirty-five checkpoint PR groups; the seven post-L18 units, L22 and C11/L23/I6 bring the working
plan to 46; I7 brings it to 47, C12/L24 to 49, C13/L25 to 51, C14/L26 to 53, I8/L27 to 55,
C15/L28 to **57**, C16/L29/C17/L30 to **61**, C18/L31 to **63**, L32 to **64**, L33 to **65**, C19/L34 to **67**, C20/L35 to **69**, C21/L36 to **71**, C22/L37 to **73**, and L38 to **74**. L18 extends property lookup after L10/L13. During current development,
maintain their commit assignments on `errs`. Once submission preparation is requested, prepare only
the next few for review and update dependent patches after their prerequisites land.

For the future merge project, use these milestones. Order within each milestone follows the
prerequisites above; these are not batches to open simultaneously.

| Milestone | PR groups | Result |
|---|---|---|
| Compiler diagnostics | I1, I2, R1, I3, C1, C2, C3, E1, C4, L1, L2 | Host listener contract, useful compilation outcomes and reliable diagnostic publication/lifecycle. |
| Module semantics | E2, L3, L4, E3, L5, L6 | Source bindings, immutable snapshots, module overlays/navigation and direct hierarchy. |
| Incomplete editing | C5, C6, E4, L7, C7, L8, C8, C9, L9 | Recovery, partial compiler facts, bounded completion and signature help. |
| Other semantic consumers | L10, L11, L12, L18 | Type/implementation lookup, property/accessor targets, call hierarchy and semantic presentation; these can land once their listed prerequisites are ready. |
| Constructor and declaration cursor extensions | C16, L29, C17, L30 | Specialized constructor fitting and bounded declaration/tuple/literal recovery, after the listed completion/recovery prerequisites. |
| Anonymous construction | C18, L31 | Source-owned class preparation, own/super constructor candidates and editor consumers, after the specialized-constructor foundations. |
| Source projects and editing | L13, L14, L15, L16, L17 | Versioned dependencies, automatic recompilation, bounded rename, editor configuration and configured-graph reference/method-rename proof. |

When preparing each PR, record its actual base, new branch/commit hashes and validation alongside
its source group. Keep code, regression tests and applicable migration notes together. Resolve any
API/build dependencies exposed by extraction before submission. The C1/C2 breaking release policy
still applies; the source map does not turn those changes into compatible additions.

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

### L10 — Type-definition and implementation lookup

**Contract:** copied type identities and compiler method chains support source navigation in the
current module. Keep the no-argument snapshot copier passive; a separate reporting overload
inspects TypeInfo on the worker and returns no implementation set on cancellation or serious
inspection errors. Preserve the adapter's single-target type-definition hook while adding a
multi-target hook for unions/intersections. Advertise capabilities only for XdkAdapter. Extract
adapter/protocol tests and the playbook together; no new compiler/embedding/AST code is required.

### L11 — Static source call hierarchy

**Contract:** selected calls belong to their actual source method/lambda, with grouped incoming and
outgoing ranges across current module files. Extract the copied callable/owner facts, `XdkCalls`,
opaque snapshot item IDs and protocol round-trip tests. Keep anonymous methods separate from their
factory. Runtime dispatch, constructors, property accessors and dependency source edges stay out.
No Java/AST changes belong in this slice.

### L12 — Semantic presentation consumers

**Contract:** resolved names drive tokens and read/write highlights; successful inference and
selected call mappings drive hints. Copy assignment usage and existing register/component modifiers
in Kotlin. Include `InvocationBinding.Argument.named` in E4's final provenance shape; preserve the
three-argument constructor and document record-pattern migration there. Carry adapter/stdio tests,
failed-inference controls and capability/playbook updates here. L15 separately advertises bounded rename.

The rename, serialized dependency and lifetime probes are acceptance evidence for the relevant
source-binding/snapshot slices (E2/L4/L7/L10), not a claim that a rename engine or workspace index
has shipped at that historical stage. L15 now adds the bounded rename consumer. During extraction split their fixtures along those actual dependencies and rerun each
PR in isolation.

### L13 — Versioned dependency artifacts and source lookup

**Contract:** source locations are bound to their exact emitted dependency revision. Export a
detached constant-index/source map, reopen compiler repositories per attempt, and copy external
declaration keys/locations through linked consumer identities. Carry explicit host replacement,
reverse invalidation and versioned server reanalysis together with cancellation regressions.
Keep repository discovery, build-tool integration, automatic dependency source overlays and a
persistent workspace reference index out of this PR. No compiler/AST changes belong here.

### L14 — Automatic recompilation of configured source modules

**Contract:** explicit source roots/edges own a repeatable disk/overlay snapshot. Rebuild dependencies
before consumers on the serialized compiler worker, reuse only matching detached artifacts, debounce
edits and retire stale compiler/cursor work. Publish dependency failures and recovery at current
document versions without retaining a previous successful artifact as current. Carry close/deletion,
transitive invalidation, shared publication and cancellation tests with the server integration.
No Java/AST changes, project discovery or persistent cross-module reference index belong here.

## Changes to hold out of the initial integration

| Change | Disposition |
|---|---|
| Superseded ambient-listener scope, `d23120b50` and FileStructure portions of `9cdd84519` | Do not extract the temporary ownership design. C3 retains parser/resolver scopes; C4 removes file/pool listener ownership directly. |
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

### L15 — Bounded semantic rename

**Contract:** locals and private ordinary-method parameters in a complete module; labels bind by
selected method/index. Carry immutable label provenance with E4, retaining old constructors. Kotlin
owns eligibility, import association, exact source replay, two temporary attempts, all-binding/call
comparison and stale/cancel checks. The server returns versioned document changes only to capable
clients. Include adapter/server/stdio regressions and playbook X53–X58; exclude member/workspace
rename and unsupported targets. The combined retention workload follows L14/L15, while its component
lifecycle checks accompany the respective prerequisites.

### L16 — Editor source configuration

**Contract:** explicit module names, file roots and dependency edges enter through `xtcCompiler`
initialization options, `xtc.compiler` configuration or direct settings notifications. Invalid graphs
retain the previous configuration, obsolete replies cannot replace newer settings, and unchanged
graphs preserve cached analysis. VS Code provides the workspace setting and live notification;
other clients can use the same protocol. IntelliJ has no dedicated graph settings UI yet. Include
strict parsing, relative/multi-root policy, server regressions and actual VS Code acceptance. No
Java/AST changes, automatic discovery or binary repository settings belong here.

### L17 — Configured-graph references and method rename

**Contract:** exact references and ordinary instance-method override rename over all explicitly
configured source modules, including unopened/transitive consumers and unsaved buffers. Temporary
compiler attempts supply constant identities and dispatch chains; successful before/after
compilation plus unchanged occurrence/call/dispatch bindings are required for edits. Source
membership/text, cancellation and every open-buffer version must remain current. Bundled/library
binary contracts cannot be renamed; unsupported chains, `super` and incomplete graphs fail closed.
No persistent workspace index, automatic discovery or outside-consumer closure is claimed.

Prerequisites: L13/L14 for artifact/source graph ownership, L15 for replay and binding comparison,
L4/L5 for copied semantics and lifecycle, L16 for the editor configuration/acceptance cases. Include
`XdkProjectQueries`, `CompilerMethodRelations`, the bodyless parameter copy correction, asynchronous
reference API/default, server guards, graph/lifecycle/version tests and X59–X63. All additions are
Kotlin/TypeScript; the Java embedding/AST contract is unchanged. Problems-view assertions accompany
the full playbook acceptance rather than modifying diagnostic production.

### L18 — Property and accessor implementation lookup

**Contract:** ordinary source property queries return compiler-associated getter/setter bodies or
written backing fields; accessor declarations preserve separate getter/setter relationships.
Generic contracts, inherited/default bodies, composed mixins, closed member overlays and indexed
dependency targets have positive and negative consumers. Binary-only targets have no invented
location. Inspection runs on the compiler worker with the host listener and publishes only copied
IDs and locations. It must preserve emitted bytes and avoid generating forwarding methods or
building an extra Ref/Var TypeInfo. Delegation, annotation dispatch and property rename are excluded.

Prerequisites: L10's implementation copier and L13's revisioned dependency source associations;
L16's playbook runner for X64–X67. Include the linked-identity copying fix, adapter/dependency tests,
output/purity regression and packaged stdio case. No L17 graph-query or Java/AST change is required.

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
Known module, dependency and source-location limits must be visible in the usage documentation.

Full workspace/editor support is a later milestone: broader partial syntax, project dependency
discovery, persistent cross-module indexing and member/workspace rename. Module overlays, module source
navigation, direct source hierarchy, bounded completion/signature help, lookup/call/presentation
consumers, an explicit versioned dependency host API and automatic builds for configured source
modules and bounded local/private-parameter rename are implemented. Repeated lifecycle and
executable compatibility checks establish the bounded integrated API POC gate; prolonged interactive
use and independent extracted-PR validation remain open. The bounded diagnostic audit has classified its
inspected families and fixed demonstrated defects, including bound-generic emission (I4) and
atomic result/owner metadata (I7). C12/L24 extends cursor recovery to enclosing call/group/index
delimiters; C13/L25 adds compiler-fitted local/parameter values at missing argument slots, and
C14/L26 extends those slots to direct final bare-name prefixes. C15/L28 adds compiler-validated
implicit property/constant values.
Unexamined historical suppressions and the remaining documented syntax/API limits
stay open; a green integrated run does not establish independent extracted-PR readiness.

## L63 next implementation batch

Implement as four separate checkpoints, then validate together:

1. [x] Shared X122 implement/override action selection, exact generated text, native Undo/Redo,
   and duplicate-implementation refusal in both drivers. This adds the 127th catalog scenario;
   all seven variants pass in both native drivers; receipts are recorded below.
2. [x] Fresh declaration-only analysis for member generation beside missing-implementation errors.
3. [x] Broader compiler-selected signatures: parameterized types, conditional/multiple returns,
   default arguments and generic methods.
4. [x] Prove intentional dispatch changes for existing calls and derived classes.

No tests were run between these four implementation checkpoints. Each extracted PR still needs its own
validation; the combined batch cannot establish independent mergeability.

Checkpoint 2 adds `EmbeddingSupport.analyzeDeclarations(Source/ModuleInfo, repository, listener)`.
It returns an Optional declaration-analysis record only after successful parsing/linking/name
resolution/turtle injection. The existing compiler shares that exact phase prefix; body validation
and code generation are unchanged. No new mutable AST fields are added, and no compiled artifact
is returned by declaration analysis. The LSP uses a fresh source tree and repository after a failed
normal attempt, retains its known bindings and adds header-derived member candidates. A proposed
edit still requires complete graph compilation. Declaration errors and cancellation withhold facts.

Checkpoint 3 renders nested parameterized types, conditional/multiple returns, method type
parameters and constraints from compiler identities. Validated String/Char/integer defaults use
the compiler's literal spelling. Computed/unvalidated defaults, annotated/relational types and
unresolvable cross-module type spellings remain refusals. Every candidate still requires the
whole-graph compilation and binding/dispatch proof. Validation is recorded below.

Checkpoint 4 admits inherited call and named-parameter rebinding only to the one inserted method.
Every changed compiler dispatch chain must become the exact original chain when that method is
removed; this includes descendants, existing descendant overrides, overloads and property chains.
Other known source bindings remain identical. The fixtures include a closed dependent module and
mixed overload/property/named-argument consumers. Header and failed-body occurrence views are
combined for auto-import discovery, so the new declaration pass cannot hide unresolved names.
All four checkpoints are implemented and validated together below.

### L63 batch extraction map and validation

| Review unit | Implementation checkpoint | Validation correction scope |
|---|---|---|
| Shared member-action acceptance | `a2043fb8b` | Expanded X122 now contains seven variants, including diagnostic repair, conditional/parameterized/default signatures, method generics and descendant calls. Both drivers check exact source and Undo/Redo/Undo. |
| Fresh declaration analysis | `020a1a5a4` | Canonical source-path assertion and Kotlin formatting; the additive embedding API and shared compiler phase prefix belong together. No AST changes. |
| Signature rendering | `4c841f6c9` | Kotlin formatting; method generic bridge acceptance belongs with the proof correction below. |
| Inherited dispatch proof | `985d2b50c` | Normalize a generic cap only when the compiler independently supplies its identical written chain, including the descendant chain. Add the inferred generic call/named-argument/descendant regression. |

The first combined run executed 84 LSP tests: 81 passed, two generic-action assertions exposed
the extra compiler cap chains, and one assertion needed macOS canonical source paths. Corrections
retain the strict proof: no bridge with receivers, unsupported dispatch, reordered members or
unproven written chain is accepted. An additional combined generic-call fixture exposed inherited
caps owned by the base type; both the base and descendant written chains are now required.
The final batch executed **85 LSP tests and 15 compiler API/listener tests**, with zero failures,
errors or skips. Root Spotless and LSP/IntelliJ ktlint checks pass. These are selected regressions,
not a full-suite claim. The 28 member-action tests passed again after the action-kind correction.
Both editor drivers compile; changed TypeScript ESLint checks pass.

VS Code X122 passes all seven shared variants in `run-CNyxJ0` and, after the action-kind
correction, `run-jWHRIT` (VS Code 1.139.1). Its provider
acceptance and native history checks include diagnostic restoration. IntelliJ's initial native
run `run-12998117592561284886` timed out opening the first intention menu following a stale
source request. At that checkpoint the driver waited for the exact current action before opening the menu;
the same timeout recurred in `run-16582387419624869264`. Inspecting pinned LSP4IJ 0.21.0's
`LSPIntentionCodeActionSupport.isValidCodeAction` established the real blocker: it excludes
`quickfix` from ordinary intentions. Implementing an inherited method is a class intention even
when the class has no diagnostic (or construction fails elsewhere), so both Implement and Override
now use `refactor.rewrite`. A focused regression checks this classification.

IntelliJ X122 passes all seven variants in `run-17411706440244793137`; startup editing passes in
`run-18012328890729377114` and pointer-free focus recovery passes in `run-392160183217588603`.
All three native JUnit tests pass with zero failures/errors/skips and every receipt has zero IDE
errors. Both editors used the same shared-scenario SHA-256
`af6b0a305c7e33c98dd768d7f3f70b13e819b3e70dc6d41417adcbe610eafdb1`.
The full 127-case catalog is not rerun here.

**Validation correction commit: `fc92ee79b`.** Extract its shared-scenario/driver changes with
checkpoint 1; its declaration-path assertion and builder formatting with checkpoint 2; signature
formatting with checkpoint 3; and generic bridge proof/test changes with checkpoint 4. Its
`XdkProjectQueries` action-kind correction is required for native member-action acceptance.
The earlier four implementation commits are review checkpoints, not independently green PRs.

Generation limits at that checkpoint (superseded by the following batch): binary contracts;
computed/unvalidated defaults; annotated, relational or unresolvable cross-module type spellings; and composition routes without a complete dispatch
proof. Generated bodies deliberately remain `TODO()`. Extract/inline/safe-delete and general
missing-declaration fixes remain separate L63 tasks.

## L63 library and complete-repair batch

The four implementation checkpoints were committed separately and then tested together:

1. [x] Atomic “Implement all required members”, with complete graph and per-family dispatch proof.
2. [x] Bundled XDK and binary contracts with read-only dependency identities.
3. [x] Qualified cross-module and compound type spellings, including required imports.
4. [x] Defaults during fresh declaration repair; audit computed defaults before supporting them.

Checkpoint 1 groups required methods in one class edit only when every required method is renderable. The proof checks each
new method against its own original family, reconstructs all original chains after removing the
insertions and preserves unrelated bindings. A compiler-derived required-method count prevents an
unsupported remainder from being mislabeled “all”: compilation alone can accept an implicitly
abstract class. A dedicated refusal regression covers that case without constructing the class.
Tests cover multiple missing overloads, an instantiated class, descendants, named calls and an
existing implementation. Shared X122 adds the atomic diagnostic-repair variant. Testing began only
after all four implementation commits were present.

Checkpoint 2 copies artifact/revision identities for bundled and binary contracts instead of requiring
a writable source declaration. The member proof permits only the selected family to rebind to its
new user-source override; dependency bytes and declarations remain read-only. Added regressions
cover indexed and binary-only inputs, bundled Iterator implementation and inherited concrete calls.
The combined validation receipt follows below.

Checkpoint 3 renders compiler-selected relational/nullable and immutable types recursively and
uses collision-avoiding module qualifiers for foreign classes. Generated package imports and method
bodies form one versioned edit; bulk repairs deduplicate imports. The proof translates old source
positions through both insertions and still requires unchanged unrelated bindings and dispatch.
Annotated and otherwise unsupported spellings remain refusals. Added cases cover nullable/union,
immutable/nested signatures, foreign-type collisions and shared imports in an atomic repair.

Checkpoint 4 audits default ownership: the normal compiler validates parameter defaults during
code generation, after declaration analysis has stopped. Fresh repair therefore reads only the
existing parser-owned String/Char/integer literal tokens through existing structural getters; it
does not evaluate or transplant arbitrary initializer expressions. Full proposed compilation must
validate both declarations. No new Java/AST API, mutable field or failed-compiler resumption is needed.
Already validated primitive constants can be emitted safely even when originally computed (`1 + 2`
becomes `3`); this corrects the earlier blanket statement that every computed default was refused.
Validated Boolean/Null singleton defaults use explicit XDK identities to avoid scope shadowing.
Unvalidated computed/named defaults and unsupported constant kinds remain deliberate refusals.

### L63 library/complete-repair validation and extraction

The four implementation checkpoints were committed before the first test run, as requested:

| Slice | Commit | Extraction boundary |
|---|---|---|
| Atomic all-required implementation | `4990aec90` | Multi-member dispatch/binding proof, one insertion, backend and shared bulk case; follows the earlier L63 generation/proof commits. |
| Read-only XDK/binary contracts | `4091a1c47` | Artifact identities and permitted source rebinding; indexed/binary-only and real Iterator cases. Depends on the generalized member proof. |
| Qualified/compound signatures | `778dd60d7` | Recursive type spelling, collision-avoiding module aliases, atomic imports and translated proof positions. |
| Safe defaults in declaration repair | `ecde342f7` | Existing parser literal accessors and validated constant rendering; no new Java/AST API. Signed-literal correction must accompany this slice. |
| Missing-feature/partial-package design | `327ef4f4e` | Tracking only; the ast.partial package move is not implemented. Independent of the member-generation code. |

Apply the four code slices in order. Their validation corrections also belong in the extracted
PRs: binary support legitimately adds inherited `toString` actions, so tests/refusal scenarios must
select the intended member; fresh negative literals have a unary AST node; newly inserted package
aliases contribute their own inherited dispatch chains. Only those new package owners inside the
import insertion are excluded from the old-owner comparison. Every previous owner and reference
still participates, including a property use whose name collides with the proposed module alias.
The general auto-import query retains its single-edit plan; member imports use the new combined plan.

**Validation correction commit: `3b8f7987b`.** Extract the required-method count and refusal test
with slice 1; binary action selectors/refusals with slice 2; package-owner proof and the retained
auto-import edit shape with slice 3; and signed literal handling with slice 4. Formatting accompanies
each touched implementation file. The expanded shared X122 data and native driver corrections
form the editor acceptance unit. These implementation checkpoints are not independently green PRs.

The corrected combined run passes **114 LSP tests** (including 46 member-action and 11 dependency
cases) and **15 compiler API/listener tests**, with zero failures/errors/skips. After the final
all-required guard, **47 member-action tests** pass again, including the added refusal regression. Both editor drivers
compile. Root Spotless, LSP/IntelliJ ktlint checks and changed TypeScript ESLint pass. The shared
catalog remains 127 IDs, and its manual-plan row order agrees. X122 now contains eleven variants.
VS Code `run-IsPGqC` passes the selected case, including all eleven variants and native Undo/Redo;
126 unrelated cases were not selected. IntelliJ `run-658720962975078754` passes START, all eleven
X122 variants and X105 auto-import acceptance, with zero IDE failures. Its one selected JUnit test
has zero failures/errors/skips; 125 other catalog cases were not selected. IntelliJ 2026.2.3 runs
with Ultimate disabled and LSP4IJ 0.21.0. Both editors consumed shared-scenario SHA-256
`ff1e2349b61baaae016d91c34a70b732f14aef92428e704a607388796a307fa6`.
This is not a full-catalog or independent-extracted-PR validation claim.

The first IntelliJ attempts exposed two harness problems. The generic list reader did not expose
the labels visibly rendered in the intention menu; the driver now reads the list's accessible names.
The extra protocol readiness probe could also supersede the native intention's own code-action
request. Trace evidence showed that cancellation followed by no fresh request when reopening the
menu. Removing the competing probe lets the native menu own both the request and readiness check.
Only the no-action refusal still queries the protocol directly, retrying canceled/stale responses.
Missing menus may be reopened only while the captured document modification stamp is unchanged;
selection is cleared before positioning the caret, and applied edits/renames are never replayed.
X105 verifies the shared helper still handles auto-import intentions. No mouse movement is used.

## Spotless and ktfmt migration

Historical checkpoint: the ktfmt choice below is superseded by the
[alignment with master's Spotless/ktlint setup](#spotlessktlint-alignment-with-master-2026-10-01).
Do not extract the ktfmt migration as a new PR against current master.

The completed L63 library/complete-repair batch was committed, validated and pushed through
`c958ef667` before starting this separate tooling change. The ast.partial migration remains planned.

| Checkpoint | Commit | Extraction boundary |
|---|---|---|
| Formatter integration | `63edf658c` | Replace the ktlint plugin/engine pins with the existing Spotless 8.10.2 plugin and ktfmt 0.64; configure coverage and local/CI task behavior centrally; format the eight lang build scripts. |
| Source formatting | `59289aba6` | Apply ktfmt's four-space Kotlin style to 269 source/test files and remove three obsolete ktlint suppressions. No intended runtime or compiler behavior changes. |

Extract these two commits together as a tooling PR: enabling the formatter without its initial
source migration does not pass formatting checks. The application Kotlin compiler remains 2.4.20.
All four Kotlin modules participate, including unit tests, IntelliJ integration tests and currently
excluded DAP source. Generated/synced build outputs are outside the formatter targets. The lang
root owns all eight Gradle scripts and aggregates source checks/applications from the four modules.
There are no remaining ktlint dependencies, task references or suppressions in active build/source
files. Historical ktlint validation receipts in this document remain records of their original runs.

Local Kotlin compilation depends on `spotlessApply`; CI compilation depends on `spotlessCheck`.
Local `check` applies first, with the actual per-format checks ordered afterward. Explicit
`spotlessCheck` stays read-only. The standard task declares its source inputs and output state;
no custom formatter launcher or global JVM warning suppression was added. ktfmt handles formatting,
not ktlint-specific naming and other lint rules. No replacement static-analysis tool was introduced.

Validation after both implementation slices:

- **50 DSL tests, 69 selected LSP tests and 46 IntelliJ unit tests**, zero failures/errors/skips.
  The LSP selection covers member generation, project-query lifetime and formatting configuration.
  DAP sources and the IntelliJ native playbook driver compile. No native editor replay was needed
  for this formatter-only migration.
- Root Java/XTC Spotless and aggregate lang Spotless checks pass. A second real DAP compilation
  with the same combined formatting-check command reused the configuration cache successfully.
- Temporary malformed probes in a lang-root Gradle script and an IntelliJ integration-test source
  both failed aggregate `spotlessCheck` as expected and remained byte-for-byte unchanged. CI-mode
  compilation also rejected the integration-test probe without applying formatting. Both probes
  were removed; the final checks are green.
- The four multiline strings changed by formatting preserve their contents after their existing
  `trimMargin`/`trimIndent` handling. Subsequent checks/compilation left all 277 changed Kotlin
  source/build-script fingerprints unchanged.
- No terminally deprecated `Unsafe.objectFieldOffset` warning appeared in these Spotless/ktfmt
  runs. The test batch still reports the existing two Kotlin test-source warnings and JVM CDS
  warning; this migration does not claim to eliminate unrelated warnings.

Current formatting commands and behavior are documented in [lang/README.md](../lang/README.md#kotlin-formatting).

## Spotless/ktlint alignment with master (2026-10-01)

Master commit `d604988b5` (#656) replaced the separate ktlint Gradle plugin with Spotless
8.10.3 calling the ktlint 1.8.0 engine. The errs branch now uses those same versions and default
rules, replacing its temporary ktfmt 0.64 setup. Kotlin remains 2.4.20. This is the formatter
portion of master's dependency cleanup, not a claim that all dependency updates from #656 were
imported here.

The existing broader errs targets remain: all eight lang Gradle scripts and Kotlin source/test
trees, including IntelliJ integration tests. Generated build outputs remain excluded. Local
compilation/check applies formatting; CI and explicit `spotlessCheck` only check it. No global
JVM warning suppression or custom formatter launcher is introduced.

Keep the mechanical reformat separate from L81's progress lifecycle changes. Future PR extraction
must retain master's formatter configuration and format each extracted source against it; the old
ktfmt integration/source-format commits are historical checkpoints, not independent PR candidates.

Validation: all **50 DSL tests and 80 IntelliJ unit tests** pass with zero failures/errors/skips
(JUnit XML timestamps 2026-10-01 08:50–08:52 UTC). LSP sources/tests, DAP sources and the IntelliJ
native driver compile. Root/lang Spotless checks pass, and repeating the combined check/compile
command reuses the configuration cache. The eight changed raw strings retain their
contents after the existing `trimIndent`/`trimMargin` calls. The manual adjustments preserve TODO
markers and clarify comment placement, one mixed condition and two expressions; the IntelliJ
serialized uppercase field retains a narrow naming-rule suppression. No Unsafe warning appeared.
The existing two Kotlin test-source warnings and Gradle daemon metaspace warning are unrelated.
No editor replay is needed for this formatting-only checkpoint; L81 acceptance follows separately.

L72 implementation checkpoint (2026-09-30): source synchronization and save hooks are connection
options, not compiler APIs. No additional AST or embedding changes were necessary. The implementation
preserves the existing formatter and compiler queue; save hooks never enqueue a compilation. Shared
X132 and backend regression tests are written; execution is batched after the four implementation
commits. Specification: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/ .

Terminology/compatibility checkpoint (2026-09-30): visible server/command/status/template labels
use Ecstasy; class names, setting IDs, environment variables and the existing notification preference
ID remain stable. LSP4IJ workarounds carry searchable `// TODO LSP4IJ:` markers explaining the
upstream gap and removal condition, including its bundled LSP4J diagnostic-union decoder.

Playbook audit follow-up (2026-09-30): the manual playbook now contains a batch acceptance
matrix and an existing-feature coverage audit, separating native presentation, provider/transport
assertions, manual OS/lifecycle checks and planned UI. New X133 checks linked local identity and
refusal/recovery; X134 checks unopened external source edits/deletion/restoration through each host.
A packaged stdio regression now covers L72 incremental negotiation, sequential patches, save edits
and rangesFormatting. The catalog has 139 cases; new acceptance is pending. Historical 113/126/134
receipts remain historical, and do not establish this catalog's success.

## Watcher, move and log-view acceptance follow-up (2026-09-30)

The first selected VS Code run (`run-SyLmsh`) passed 15 of 18 cases and failed X124, X128 and
X130. These failures were investigated rather than attributed to focus or hidden by larger timeouts:

- **X124:** a recursive subscription below a nonexistent resource root missed the creation of its
  parent directories. Each external root now also has a flat subscription for the relevant child
  of the nearest existing parent. Membership notifications replace that plan as directories appear;
  they invalidate resource owners even when the event identifies an ancestor of the configured root.
  IntelliJ's VFS bridge owns only the explicit recursive roots, never those broader flat parents.
  VS Code `run-zrXkq2` passes strengthened X124 and external-source X134 without synthetic events.
- **X128/X130:** late Created notifications for files already compiled canceled pending rename
  proofs. File refresh now compares relevant source membership/content and resource fingerprints
  against immutable compiler inputs. Checks make no compiler calls and are bounded to small trees;
  larger trees, failures or uncertain inputs conservatively take normal invalidation. Actual changes
  still invalidate the affected dependency closure. A failed current analysis always takes that path:
  X134 exposed why an older reusable build cannot suppress recreation of a deleted dependency.
  Empty graph rediscovery leaves resolve handles alone. Vanished module containers no longer throw from resource-watch ownership lookup.
- **X130 harness:** editor Undo in an unchanged Consumer document does not own a pure folder move.
  The VS Code driver now uses Explorer's batch Cut/Paste and its actual Undo/Redo source. It checks
  both selected containers, resources and unchanged source contents. `run-VrU7eg` passes X128/X130.
  This does not remove VS Code's inability to veto its underlying file operation on an LSP refusal.

Both plugins now provide **Ctrl+Alt+X, then L** to reveal/hide the existing server log view (macOS:
Control+Option+X, then L). IntelliJ uses Community tool-window APIs and LSP4IJ's Log-tab selector;
VS Code uses its existing Output channel and actual view visibility, including manual close or
another selected channel. No visibility shadow flag, separate process or editable log file is used.
Shared X135 verifies the open/hide actions and source preservation; physical key dispatch,
custom keymaps, retained logs and alternate docking remain manual acceptance checks. The catalog
now contains 140 cases. This implements quick log access within UI6, not the entire settings plan.

Formatting clarification: `XdkLexical.format` implements lexer-based, token-preserving indentation
and outer whitespace cleanup. Whole/range/on-type/save hooks share that implementation. Full style
formatting, operator spacing, declaration alignment, wrapping and `xtc-format.toml` loading are not
implemented; L66/UI1 retain those boundaries. Adapter API comments and manual sections 13/13a/13c
now distinguish actual behavior from future formatter work.

Backend validation passes 1,449 enabled tests with three existing disabled Tree-sitter placeholders,
72 packaged-stdio tests and 67 IntelliJ unit tests. After the final source-owner/recreation correction,
24 focused backend tests and all 72 packaged-stdio tests pass again with zero failures/errors/skips.
The focused tests include deterministic paused rename proofs: unchanged late Created notifications
preserve the proof, while a real edit invalidates it. TypeScript and the IntelliJ driver compile.
Selected native validation and final checks are recorded below.

The first combined 19-case VS Code follow-up (`run-VIYUfJ`) passed 17 cases, including X135. It
caught the deleted-dependency recreation regression described above. X130's compiler proof and
physical batch move completed, but VS Code's own Explorer then threw `Data tree node not found`
while clearing Cut decorations after Paste. The run remains a failed receipt. Pre-mutation tree
selection now refreshes/retries stale nodes; Paste and applied edits are never replayed. Keep
this upstream Explorer race on the manual acceptance list even when a later focused run passes.
The new deterministic rename-race test also initially omitted `workspace.fileOperations.willRename`
from its capabilities; the server correctly refused the unnegotiated call. The test now negotiates
that operation before pausing the compiler proof.

The next selected VS Code run (`run-iGx1M2`) passes 18 of 19, including X124, repaired X134 and
X135. X130 moves only one container because expanding the selection can enter an expanded folder.
The harness now collapses Explorer first and checks the actual selected paths through Copy Path
before Cut. The failed receipt remains recorded; this is a selection correction, not a compiler or
file-operation retry. Paste, Undo and Redo still each execute once.

Native IntelliJ `run-17174738471798629344` passes START and 18 of the 19 selected cases with
zero IDE errors. X124/X134 establish automatic external root watching; X105/X122/X127 exercise
the selected lazy-action path; X131/X132/X133/X135 cover their declared protocol/view scope.
X130 fails before mutation because the dispatch assertion selected another Move handler. A focused
reproduction (`run-1884447924306396980`) also exposed a test setup dependency: workspace-folder
notification ran before any source editor started the language client. X130 now opens its consumer
and awaits that client before changing workspace roots. Handler diagnostics name each candidate;
the final Move correction and receipt follow below.

The isolated native dispatch diagnosis (`run-6493518307517078304`) found that the manifest used
`moveHandler` instead of IntelliJ's actual `refactoring.moveHandler`. The handler never registered;
ordinary Java/file delegates won. The corrected manifest has a regression comparing its qualified
name with `MoveHandlerDelegate.EP_NAME`, and all six manifest tests pass. The next run
(`run-4728671447859791254`) reached the real Move dialog and moved both containers: its compiler
proof took 389 ms. It then spent 45 seconds awaiting Undo from an unchanged Consumer editor.
X130 now uses project-level Undo/Redo for this file-only operation and checks history availability
before dispatch. No compiler wait was increased, and applied mutations are never retried.

Final selected acceptance: VS Code `run-iGx1M2` passes the other 18 cases and `run-YMDUeW`
passes corrected X130. IntelliJ `run-17174738471798629344` passes START and those 18 cases;
`run-1746762976235942700` passes START and corrected X130 with zero IDE errors. X130 itself now
takes **4.98 seconds**, including Move/Undo/Redo; the focused Gradle invocation takes 39 seconds
including 21.54 seconds of IDE startup. All 19 selected IDs have passing receipts in both hosts:
CFG2, X57, X101, X105, X118, X122–X135. This is selected acceptance across recorded runs, not one
uninterrupted full-catalog run. Catalog SHA-256:
`026502a0304f85b0545e8af6c0743fea30671ee067d4c90df65edc2e308577fc` (140 shared IDs).
L70–L72's bounded implementation batch is accepted; UI1–UI7, broader semantic scope, full-catalog
submission checks and the manual lifecycle/shortcut/host-race checks remain open.

The final gate passes **68 IntelliJ unit tests** and **16 VS Code extension tests**, including
command registration and the 40-cycle compiler error/recovery workload. Root/lang read-only
Spotless checks and ESLint on the changed TypeScript sources pass. Test results have zero
failures/errors/skips; the three pre-existing disabled placeholders belong only to the earlier
full backend receipt. No remote push was performed for this batch.

### L70–L72 and watch/log extraction checkpoints

| Concern | Commits to retain together | Extraction notes |
| --- | --- | --- |
| External input watching and delayed-event correctness | `9f2c5ae8c`, `b895325a7` | Native watch leases, missing-parent subscriptions, bounded immutable input comparison, failed-analysis repair and deterministic rename-race regressions. The correction is required by X124/X128/X134; source/resource ownership is shared backend behavior. |
| Native Move, file-operation history and acceptance | `c748f9831`, `52a61224f`, `a8e29266a` | Keep the nullable platform signature and actual `refactoring.moveHandler` registration with the implementation. X130 uses native host actions and the correct project/Explorer undo histories. It also needs the watcher correctness slice above. |
| Remaining lazy resolve and native selected-action application | `3888c15f2` | All six bounded resolvers; IntelliJ action epoch/Undo bridge. Retain shared X105/X122/X127/X131 and the current capability-negotiation tests. |
| Save/sync/multiple-range protocol | `448b7f8a1`, relevant `3e228726e` additions | Default Full/no-save-edit contract, independent opt-ins, atomic UTF-16 patches, one-snapshot formatting and packaged transport regression. No new compiler/AST API. |
| UI configuration plan | `4ccbda4d9` | UI1–UI7 remain planned except the quick log access below. This documentation is not proof of implemented settings. |
| Ecstasy labels and upstream markers | `19ba6422d` | Keep technical identifiers stable. Include the later symbol-rename `TODO LSP4IJ:` comment from the acceptance documentation checkpoint. |
| Shared coverage and manual scope audit | `3e228726e`, current acceptance documentation | X133 linked editing, X134 external source watching, exact automation/manual boundaries and failed-run records. Distribute relevant assertions with their production slice when extracting. |
| Quick server-log access | `5ff316797` | Both host commands/keybindings, shared X135 and manual shortcut/docking checks. Production depends on the existing server consoles, not on new compiler semantics. |

These are integrated-branch checkpoints and dependency guidance. Each extracted PR still needs its
own build, meaningful regressions and relevant selected editor acceptance; the passing integrated
branch does not establish independent green commits. Broader L62/L64–L67, L73–L82 and R1–R8 remain
separate scopes, and Tree-sitter remains the shipped default.

## Editor settings implementation batch (UI1–UI7, 2026-09-30)

Five local checkpoints implement the settings contract, IntelliJ page, VS Code controls,
apply/restart wiring, and effective-state/shared acceptance in that order. Tests are written
with each slice and run together after all five commits. This does not broaden the formatter,
add compiler incrementality, switch the packaged adapter, or implement Run/DAP.

### UI1/UI2 consumer and ownership audit

| Existing setting / source | Consumer and scope | Change contract / finding |
| --- | --- | --- |
| `xtc.java.home` | VS Code `findJavaExecutable`, machine-overridable; IntelliJ uses its JBR/LSP4IJ runtime controls | Restart; never copy an absolute machine path into portable defaults. |
| `xtc.trace.server` | VS Code language client tracing; IntelliJ already has LSP4IJ trace controls | Live client tracing; do not add another trace owner. |
| `xtc.inlayHints.enabled` | VS Code previously sent an initialization field with no server consumer | Fix live presentation filtering; IntelliJ uses native inlay controls and the equivalent service preference. |
| `xtc.formatting.indentSize`, `continuationIndentSize`, `insertSpaces` | VS Code configuration response / IntelliJ common Code Style; server `FormattingConfig` | Live validated settings; formatting notifications were missing in VS Code. |
| `xtc.formatting.tabSize` | Serialized but not retained in `FormattingConfig` | Deprecate this ineffective legacy field; native editor tab width controls rendering and request fallback. |
| `xtc.formatting.maxLineWidth` / Code Style right margin | Retained configuration, no line-wrapping implementation | Explicitly label as reserved/visual guidance, never promise wrapping. |
| IntelliJ custom continuation field, SMART_TABS, KEEP_INDENTS_ON_EMPTY_LINES | Custom continuation field is unused; common continuation field is read | Expose only supported formatter controls; keep source-compatible serialized fields where needed. |
| `xtc.compiler.sourceModules` / IntelliJ Compiler page | Existing graph owner and guarded rename/Undo; window/project scope | Live; null means discovery, empty means no source modules. Preserve the existing graph precedence. |
| Gradle evaluated build model | Existing explicit import/prepare commands, then server graph inputs | Live; no build-script parsing or implicit build on typing. |
| `xtc.sourceRoots`, `xtc.sourceRoots` JVM property, `XTC_SOURCE_ROOTS` | SourceRootResolver: initialization list, property, environment | Restart; source attachments are not module dependencies. Missing roots remain diagnosable inputs. |
| `xtc.logLevel` / `XTC_LOG_LEVEL` | Server process startup logging | Restart; host log view reuses the same process. |
| `xtc.lsp.semanticTokens` / `XTC_LSP_SEMANTIC_TOKENS` | Server advertised capabilities; IntelliJ launcher override | Restart, preserve current defaults; native highlighting preferences remain editor owned. |
| `xtc.trace.directory`, `xtc.trace.level` | Existing execution trace output / harness launcher | Developer tracing, not a portable project preference. |
| Packaged adapter / XDK | Build properties and bundled inputs | Read-only effective state, no selector for an implementation absent from the package. |
| Pull/push, resolve, delta, save and resource edit capabilities | Negotiation with each client | Automatic compatibility decisions, not preferences that bypass safety checks. |

The new language-service preferences are Full/Incremental **text transport**, editor/server save
formatting ownership, and inlay visibility. Defaults remain Full, editor-owned saving and visible
inlays. Native format-on-save takes precedence when enabled, suppressing the server save edit.
IntelliJ uses `xtc.languageService` in the existing LSP4IJ settings store, with field-level global
inheritance and project overrides. Its page compares only that section before merging into the
latest content, so a concurrent compiler graph rename/Undo cannot be overwritten. Reset to
inheritance removes that section. VS Code uses native user/workspace settings; connection options
are window-scoped, never a misleading per-folder switch. Presentation can be resource scoped.
Invalid values are rejected before persistence/start; a running valid connection is retained.
Neither remote filesystem execution nor restricted-workspace build execution is newly enabled.


### UI3–UI7 implementation boundaries and checkpoints

| Slice | Local checkpoint | Scope |
| --- | --- | --- |
| 1: UI1/UI2 | `574a27ab3` | Immutable preferences, ownership audit, strict values, inherited service fields and guarded section replacement. |
| 2: UI3 | `b8c8fb904` | IntelliJ application/project pages, supported formatting controls and compiler path choosers; service-only overrides do not claim compiler graph ownership. |
| 3: UI4 | `fb80b423d` | VS Code Settings schema/scopes, path chooser, settings command and explicit deprecation of inert formatter options. |
| 4: UI5 | `cebf00b36` | Serialized VS Code restart/start, coalesced IntelliJ transport restart, live hints/formatting and last-valid/versioned formatting configuration. |
| 5: UI6/UI7 | `a7f83e9f0` | Effective configuration/queue API and views, packaged protocol assertions, shared X136–X139 and manual acceptance updates. |

LSP4IJ source-bytecode inspection found `DocumentContentSynchronizer.documentSaved` sends only
`didSave`; no native `willSaveWaitUntil` implementation exists. The IntelliJ save-owner control is
therefore disabled with a searchable `TODO LSP4IJ:` comment and an explanation. Its connection keeps
server save edits off. The client also clears LSP4IJ's incorrectly advertised save-hook capabilities.
Native Actions on Save is tested separately. VS Code checks native save
ownership per document in its `willSaveWaitUntil` middleware, so changing a language/folder preference
cannot introduce duplicate save edits during an existing connection.

Both settings stores preserve compiler graph fields. IntelliJ compares only the service section
before saving into the latest project content; invalid external settings retain a project's last
valid immutable preferences. Its project compiler page preserves an existing service section while
changing graph ownership. VS Code rejects invalid connection settings before stopping a running
server. The effective report separates configured values from negotiated server values and obtains
queue metadata without waiting for the compiler worker or invoking Java compiler APIs.

Combined validation found and corrected an inherited no-op formatting setter in `XdkAdapter`:
the status report accepted editor indentation while all three formatting entry points still used
request defaults. A final atomic holder now retains the immutable preference; a regression checks
document/range/on-type formatting after changes and reset. Explicit presentation notifications
also preserve pending source-graph replies and cached compilation without adding compiler work.

VS Code `run-1aSPTX` passes X118/X132/X135–X139 (seven selected cases). Earlier receipts retain the
initial failures: `run-NQhAKs` masked failures during restart cleanup; `run-SblieN` and `run-sCRIFj`
exposed formatting and restart assertions; `run-on3vZt` includes accidental external typing;
`run-PNAR7e` isolated the cached empty document-symbol response. Its captured `jcmd` dump shows an
idle compiler worker, and the server trace records a 62 ms compile. Reusing the harness's registered
symbol-provider helper avoids VS Code's execute-command cache: X137 now takes 3.3 seconds for both
restarts, with each connection ready in about 0.7 seconds and its unsaved document in 1.4 seconds.
Fixture cleanup preserves the original failure instead of replacing it with a cleanup exception.

IntelliJ's first selection, `run-12398066746544159936`, passes START/X118/X132/X135/X136 and fails
X137–X139, with zero IDE failures. It identified a PID-before-initialization readiness assumption,
incorrect save-hook advertisement, and a low-level single-document save that bypasses Actions on
Save. The corrected selection waits for the wrapper's started state and invokes native Save All.
`run-3866544261762285778` passes START and all seven selected cases with zero IDE failures; X137
takes 3.9 seconds and X139 takes 1.8 seconds. These seven-case receipts do not establish a new
full 144-case catalog pass.

The combined backend run passes 1,453 enabled tests with three existing disabled placeholders;
the packaged process suite passes all 72 tests. The editor suites pass 73 IntelliJ unit tests
and 20 VS Code extension tests. The final focused backend run passes 27 tests with zero skips,
failures or errors (`EditorFormattingStateTest`, `CompilerConfigurationTest`,
`FormattingConfigRoundTripTest*`, `XdkEditingTest`). Root and `:lang:spotlessCheck` pass. TypeScript
compilation passes; ESLint reports zero errors and five existing unused-fixture-argument warnings
in untouched playbook files. The native launcher’s shutdown-supervisor message is normal teardown;
its JUnit result and IDE failure report are clean.

Remaining broader UI items are explicit: advanced JVM controls, build progress/cancel
presentation, source attachment editors, log export/retention and remote/untrusted-workspace coverage.
X136–X139 exercise the local shipped controls, not those planned extensions.


### Protocol hardening batch (L80/L81, 2026-09-30)

Accepted order: (1) capability/wire-format corrections, (2) long-operation progress,
(3) cancellation and transport lifecycle, (4) negotiated refresh and runtime trace,
(5) shared editor acceptance and documentation. Each slice gets a local commit with tests;
the combined validation runs after implementation.

1. Capability checkpoint: explicit UTF-16; legacy workspace URI/path fallback; negotiated
   hover markup, flat/hierarchical outlines, symbol-kind fallback and push diagnostic metadata.
   Definition/reference Locations, plain completion inserts and string signature documentation
   are already legal baseline formats; no snippet or location-link support is claimed. Existing
   versioned/resource edit guards and code-action `context.only` filtering remain in force.
2. Progress: client request tokens and post-handshake server-created tokens for long compiler
   queries, with terminal success/failure/cancellation notifications.
3. Lifecycle: retire requests waiting for shared analysis without cancelling another request's
   compilation; reject invalid transport lifecycle requests and clean up owned pending work.
4. Refresh/trace: only negotiated implemented providers; coalesce notifications outside compiler
   locks; runtime off/messages/verbose tracing without source text.
5. Acceptance: protocol regression tests plus shared editor checks; update evidence and extraction
   map after the batched run. Broader L80/L81/L82 remain open until their other audits are complete.


Protocol batch extraction checkpoints:

| Commit | Slice |
| --- | --- |
| `78f949700` | L80 client presentation and workspace-root fallback |
| `47ebab51b` | L81 negotiated progress and query-owned cancellation |
| `1e285d18e` | L81 pending readers and transport lifecycle |
| `494b18c06` | L81 coalesced refresh and runtime trace |
| `5fd5fd74b` | X140 UTF-16 ranges; X141 runtime trace in both installed clients |
| `fe54a9712` | Diagnostic cache retirement and native indexing/parser lifetime fixes |
| `0d4a847e9` | Retired VS Code connection callbacks and late IntelliJ settings reports |
| `ec7dcbf6d` | Negotiated configuration requests and formatting-settings retirement |
| `6169886a9` | Rich-client fixtures, nested flat-symbol regression and packaged lifecycle errors |
| `f3417dcbd` | Both catalog guards, focused Unicode regressions and corrected X140 fixture |

Progress is wired to references, rename proposals, code actions and workspace diagnostics;
client-provided initialization tokens cover synchronous compiler discovery. Server-created tokens
wait for `initialized`, client support and creation acknowledgement. Fast work avoids a popup.
This does not yet stream partial result batches or report Tree-sitter's asynchronous initial scan.
Refresh is negotiated for diagnostics, semantic tokens, inlays, lenses and folding; no runtime
inline-value provider is claimed. Lifecycle gating is at the real transport, with direct server
embedding retained for host/tests. L80 still needs the full code-action literal/kind, completion
kind, diagnostic-tag and workspace-edit failure-handling audit; L81 partial results and L82 broader
acceptance remain open.

Validation: the full backend run executed 1,476 tests: 1,472 passed, three existing placeholders
were disabled, and the new flat-outline test failed because its regex mock never produced the
expected nested child. Its corrected explicit nested-symbol fixture passes. The final focused
`ClientPresentationTest` / `XdkPresentationTest` run passes all 13 tests, including two added
ASCII/astral-character variants for hover and exact rename ranges. This is a full run followed by
focused corrections, not a second full-suite receipt. The initial run stopped while old simulated
clients waited for unnegotiated diagnostic versions is not a passing receipt.

All 73 packaged tests pass, including requests before initialization, duplicate initialization,
successful compilation afterward, and requests after shutdown. All 74 IntelliJ unit tests pass;
integration-driver compilation and fresh nonincremental JVM compilation pass. Root and lang
read-only formatting checks pass. TypeScript compilation passes; ESLint has zero errors and five
existing unused-fixture-argument warnings. All 20 VS Code extension tests pass, including 40
error/recovery cycles with measured end-to-end p50 610 ms and p95 615 ms on this local run.

Both completeness guards now include X140/X141. The first VS Code launch was rejected by the old
X139 catalog limit before opening an editor. VS Code `run-MgB6pD` then passed X136/X137/X141 but
exposed the property-initializer gap recorded as L83. X140 now isolates encoding with a method-body
reference. VS Code `run-5eCFZV` passes all four selected cases. IntelliJ
`run-15914309414363009017` passes START plus X136/X137/X140/X141, with zero IDE failures and Ultimate
disabled. X137 takes about 3.0 seconds in VS Code and 4.1 seconds in IntelliJ; X140/X141 each take
about 1.3 seconds in IntelliJ. These are selected receipts, not a full 146-case catalog rerun.

The mutable-state/deprecation audit and remaining deterministic race checks are recorded in
[errs-audit.md](errs-audit.md#mutable-state-and-deprecated-api-audit-2026-09-30-checkpoint).
No new AST fields or compiler hooks were required by this protocol batch. L83 requires separate
compiler ownership work before constant-folded initializer occurrences can be claimed complete.

Pre-push documentation audit: the [protocol/lifecycle coverage map](../lang/doc/manual-test-plan.md#protocol-and-lifecycle-coverage-map)
now names each regression class, shared editor case and pending manual P1–P4 check. Both plugin
READMEs describe the current 146-case catalog. Stale L68–L72 validation rows are reconciled with
their recorded passing receipts; L80/L81 and L83 retain their actual open work. The listener and
process-lifecycle writeups distinguish optional wire metadata and query cancellation from compiler
analysis ownership. Historical catalog/test counts remain attached to their original runs.

Recommended next order: close the three explicit state-audit follow-ups (stalled watcher replies,
stale path-editor drafts, disk-index/open-buffer ownership) in separate commits; preserve detached
facts for L83; then complete the remaining L80 capability audit and L81 partial-result work. Add
controlled regressions and shared editor cases where observable, batch validation, and reserve a
full catalog/long-workload run for the L82 submission gate.

### Concurrency and initializer follow-up batch (2026-09-30)

- `966b19c89`: bounded watcher acknowledgements, late registration cleanup and disconnect retirement.
- `239c39f72`: VS Code compiler path draft invalidation before settings writes.
- `6601c7689`: Tree-sitter disk/overlay ownership, including initial scan and close/reopen.
- `4cffd28ae`: detached successful constant-initializer facts, additive embedding accessor,
  Kotlin semantic copying and shared X142. See the AST ownership record in `errs.md`.

Each slice includes regressions; execution is deliberately batched after L80/L81 implementation.
X142 is added to both editor drivers, bringing the catalog to 147 scenarios. This is code coverage
inventory, not a new passing acceptance receipt.

### L80/L81 follow-up implementation

- `5c088321a`: negotiated completion/action presentation and one-use legacy action commands, plus
  the explicit edit-application audit in `errs-audit.md`.
- `94b1347bc`: client-supplied partial-result tokens now stream bounded batches for references, workspace
  symbols/diagnostics, document symbols, definition/declaration/type/implementation locations and
  type/call hierarchy relations. Responses contain only the remainder, so delivered items are not
  duplicated. No token keeps the existing full-result response. Batches come from one validated
  snapshot; this does not make compilation incremental or expose intermediate compiler state.
- Publication runs on a connection-owned dispatcher outside compiler/document locks. Cancellation,
  source/configuration revision changes and disconnect stop further batches. Failure before
  publication sends none. Controlled barriers cover cancellation between batches.
- Workspace initialization has an additive future-returning adapter entry point; Tree-sitter now
  reports its actual scan lifetime rather than immediately ending progress after scheduling it.
  Reports carry file counts/percentages and cancellation retires the owned scan. Automatic token
  creation waits for `initialized`; client-supplied initialization tokens work during initialization.
- Shared X143 compares streamed and ordinary symbol results in both editors. X142/X143 bring the
  catalog to 148. Full combined backend/transport validation and selected native acceptance follow
  these implementation commits. Generic IntelliJ server-initiated edit version validation remains
  the documented upstream boundary; do not mark all of L80 complete from this batch alone.

L83 compatibility correction: the accessor/constructor API is additive, but Java record
patterns must add the eighth `initializerBindings` component. `EmbeddingApiCompatibilityTest`
now checks every retained three- through seven-argument constructor and the current pattern.
This uses the existing unreleased-record migration policy; it is not source-compatible for old
record deconstruction. `InvocationBinding.Facts` likewise adds its initializer map while retaining
its prior constructors. The first combined run caught this stale migration test and an ES2020
fixture using `replaceAll`; both are corrected before rerunning.


### Follow-up validation receipt (2026-09-30)

The full compiler run passes 512 tests; 40 skips are the 36 explicitly disabled tests and four
opt-in project-creator integration tests. None is skipped for missing compiled XDK modules.
The full backend run executed 1,500 tests: 1,494 passed, three existing placeholders were disabled,
and three new fixture assertions failed. Those assertions are corrected (a static initializer
control, nullable preferred metadata, and the explicit watcher retry deadline). The focused rerun
passes all 51 tests without skips. This is a full run followed by focused corrections, not a second
full-suite receipt. All 74 packaged stdio tests and 74 IntelliJ unit tests pass without skips.
The stdio suite includes actual partial-reference and workspace-diagnostic notifications with
empty final responses, in addition to controlled publication/cancellation regressions.

The retention workload completes 120 edit cycles / 960 requests, releasing all 2,470 tracked
compiler objects. Rebuild latency on this local run is p50 220 ms / p95 235 ms including debounce.
VS Code extension acceptance passes all 23 tests, including the three draft-ownership tests and
40 error/recovery cycles (p50 609 ms / p95 617 ms). An earlier run timed out waiting for corrected
consumer diagnostics; its trace contains an unexpected Consumer edit before the dependency test.
The input's contents were not captured, so that failed run is not attributed to a particular cause.
The successful rerun adds exact fixture-ownership assertions and diagnostic details on timeout.

VS Code `run-4Vo2G3` (1.140.0) passes X124/X131/X134/X142/X143. Initial launch guards caught misplaced
shared catalog entries and missing X142/X143 manual-table rows; both are corrected. The catalog
contains 148 cases, hash `cbb3c633a3006b394cfffd86d3ec790783124b61562f128a8b97ab072deb47a8`.
This is selected acceptance, not a new full-catalog receipt. IntelliJ acceptance follows below.
Root and lang read-only formatting checks pass; TypeScript compiles. ESLint has zero errors and
five pre-existing unused-fixture-argument warnings in unchanged playbook files.

IntelliJ 2026.2.3 / LSP4IJ 0.21.0, with Ultimate disabled, passes START and X124/X131/X134/X143
in `run-6087249141816329018`. X142 first failed an exact post-rename text assertion; the isolated
rerun, with actual text added to failures, passed that assertion and exposed an incorrect `Undo`
action ID. The driver now uses `$Undo`, waits for exact restoration and checks diagnostics. Final
`run-1763480386953487411` passes START and X142 (4.4 seconds), with zero IDE failures. The first text
mismatch was not reproduced and its actual buffer was not retained; it is not attributed to a
compiler defect or external input. Preserve this caveat for the full L82 catalog run.
Failure trace capture also reads available wrapper traces without waiting 45 seconds for a server
that per-case cleanup has already stopped. No production edit semantics were changed for this fix.
These selected/focused receipts establish all five selected cases, not a new full 148-case run.

Remaining order: generic native applyEdit version ownership under L80; visible long-operation and
cancellation acceptance under L81; then L82's full catalog and representative performance/lifecycle
workload. Broader unimplemented LSP/runtime/editor-configuration tasks remain in their numbered lists.


Follow-up correction commits for later extraction:

| Commit | Keep with |
| --- | --- |
| `f9b7f81f7` | Concurrency slices `966b19c89` / `239c39f72` / `6601c7689`: removal retry ownership, equivalent file URI keys, and draft disposal order, with controlled regressions |
| `e1b069075` | L83 `4cffd28ae`: retained-constructor/current-pattern compatibility tests, valid nonconstant control and exact initializer semantic token assertions |
| `188445074` | Shared validation for L83/L80/L81: X142 native Undo and fixture checks, X142/X143 catalog ordering, nullable capability assertion and actual packaged partial-result messages |

When extracting independent PRs, split the shared validation commit by its owning feature and retain
both the scenario catalog and manual-table entries with each editor case. The integration receipts
above validate this combined branch; each extracted PR still requires its own build and tests.

### Native edit ownership and visible progress batch (2026-09-30)

1. **L80 generic text edits — implementation checkpoint.** `XtcLanguageClient.applyEdit` now
   uses the existing transport's actual sent versions, text and opened-document identities. There
   is no second version counter or private synchronizer reflection. Preparation checks every target;
   the single native Undo command rechecks connection, URI, document stamp and incarnation before
   writing any target. Cancellation/disposal retires pending applications. Closed documents accept
   only unversioned edits. Reopened version numbers at or below the retired ceiling are ambiguous
   and refused. Resource operations, snippets, confirmation annotations, overlapping edits and
   invalid UTF-16 boundaries are refused; native guarded Rename/Move remains the resource route.
   LSP4IJ's old/new text-edit representations are normalized at the boundary without replacing IDE
   libraries. Unit regressions and shared **X144** cover version proof and current/stale application
   with Undo. Catalog: **149** cases. Tests are written; acceptance is pending the batched run.
2. **L81 visible progress/cancel/disconnect** follows in a separate commit.
3. **L82 validation** follows both implementation commits: compiler/backend/packaged transport,
   full editor catalogs and a longer editing/restart workload. Preserve the prior unexplained
   X142 and consumer-diagnostic failures as unresolved until this evidence is collected.

This slice changes client ownership only; it adds no compiler, embedding or AST API. It belongs
with the L80 native-client extraction slice, after the startup-message ownership bridge.

**L81 acceptance implementation checkpoint:** shared **X145** uses a 5,000-method compiler
fixture, not a delayed fake response. Both drivers require a references request to remain pending
when native progress becomes visible. IntelliJ observes/cancels the status bar's actual progress
model and opens the background-task panel. VS Code observes the SDK's actual workbench progress reporter/token and cancels that
token through the same callback used by the Cancel button. The driver opens the progress list;
it does not automate selection of a particular notification's button. Both require cancellation, a successful later hover, restart with a pending reader,
unsaved-text preservation, disappearance of progress and old-PID termination. A workload that
finishes before these actions fails as unexercised. Catalog: **150** scenarios. Compilation is
checked during implementation; the full validation batch follows the two separate commits.
No production progress delay or test RPC was introduced.

### L80/L81 batched validation history

- Implementation commits: `992b47f26` (native generic edit ownership, X144), `7a312dd9d`
  (native progress/cancel/restart scenario X145). They remain separate extraction slices.
- Forced compiler/backend batch: Java **512 passed**, 40 known disabled/opt-in skips; backend
  **1,497 passed**, three disabled placeholders; packaged stdio **74 passed**; IntelliJ unit
  **80 passed**. No failures/errors or missing-XDK skips. This precedes the lexical performance fix.
- Retention at the standard 120 cycles: 960 edit requests, 2,473 weak references, zero retained;
  rebuild p50 **222 ms**, p95 **238 ms**, including debounce. An extended 360-cycle run follows.
- VS Code `run-4fS87C`: X144 passes. X145 correctly received ContentModified from a delayed
  fixture-create watch, before progress began. The harness now reissues only that read with
  unchanged-source proof; it never repeats the mutation.
- Progress observer failures `run-64QWrf`, `run-LFE6zj`, `run-M0BlfU` were harness failures:
  the observer must use the bundled SDK's real progress objects, and match the actual human title
  **Ecstasy: finding references**, not the protocol method name. They are not passing receipts.
- VS Code `run-qwm8wB`: X145 **passes**, 87.1 seconds for 1,500 methods, including native
  cancellation, subsequent hover, pending restart, old-PID exit and unsaved-buffer preservation.
  Cancellation uses the workbench-owned token callback; choosing/clicking its particular button
  remains a manual rendering check.
- A live `jcmd` sample during this fixture identified quadratic lexical processing: every token
  rescanned all source line breaks in `XdkRename.offset`; semantic token output also split the whole
  source per token. The fix builds immutable per-call line indexes once. A large CR/LF/CRLF and
  emoji regression checks exact token spans under a generous timeout. This changes adapter
  presentation/resource scanning only, without new compiler/AST state or APIs.

Full editor catalogs, the lexical fix's tests and the extended workload are still in progress.

IntelliJ selected receipts: `run-9059436229437082618` passes START/X144; X145 finishes before
cancellation takes effect after the performance fix (an unexercised timing case, not a pass).
`run-5136524067786146169` passes START/X145 with 5,000 methods in **7,622 ms** and zero IDE errors.
The first 360-cycle Gradle run had a test-report output collision with a concurrent focused
`:lsp-server:test`; it is invalid evidence and is being rerun with exclusive ownership of that task.

`3987e26c1` is the independent L82 lexical performance slice. Its three large lexical regressions,
eight presentation tests and three resource-input tests pass with zero skips. This can be extracted
after the lexical presentation implementation; it does not require the generic client edit guard.

The exclusive extended retention run passes: **360 cycles**, **2,880 edit requests**, **7,346 weak
references**, zero retained, rebuild p50 **223 ms**, p95 **255 ms** (includes debounce),
271.3 seconds, zero skips; Gradle configuration cache stored successfully. The previous report
collision is not counted.

Full IntelliJ `run-11435582978373867143` passes 74 checks (including START), then X105's
no-replay guard stops the run. The captured editor contains `2Document` instead of `Document`;
no import action accounts for this extra character. Its origin is not established. The guard is
retained and its diagnostic now includes bounded before/after text. X105 and the 76 unrun cases
continue in a fresh selected run; the original receipt is not relabelled as a clean full run.

The resumed IntelliJ receipt `run-5480270557660243469` passes 75 checks including START and
X105/X142, but fails X57/X126 while cleanup requires an already stopped server to be running,
and X143 while reading partial results from a rolling console buffer. Its shutdown also exposes
a File Cache Conflict for X127 and formatting-error notifications. These are failures, even though
the IDE error collector reports none: notifications and modal dialogs need their own acceptance.
The harness now checks the captured connection on close, observes X143's token directly through
LSP4IJ's lifecycle listener, and discards only the completed scenario's dirty documents.

The formatting notifications expose a separate production bug in LSP4IJ 0.21.0:
`LSPFormattingSupport.format` accepts a nullable editor, then dereferences it while applying the
reply. Save All reaches it for dirty, closed tabs. `XtcFormattingService` retains native asynchronous
formatting/Undo and LSP4IJ request/cancellation support but applies replies against a captured
document, with a current-text check. Its `TODO LSP4IJ:` comment identifies the upstream removal
condition. X139 now explicitly includes a dirty, closed tab alongside the open document. This
client workaround belongs in its own extraction slice; it changes no embedding or AST API.

Post-lexical-fix full backend verification passes **1,500 tests**, with three existing disabled
placeholders; packaged stdio passes **74**, with zero skips. Final native regression and VS Code
catalog receipts remain pending.

`8254048a0` fixes closed-document formatting and adds its native X139 regression. IntelliJ
`run-5152567950207711961` passes START and X57/X126/X127/X132/X138/X139/X143, zero IDE errors,
with a successful Gradle exit. Combined with the full and resumed receipts above, all **150**
catalog cases have passed. This is combined coverage, not a single clean full run. The prior
`run-4858840074776310971` passed its six native checks but its launcher failed while scanning an
IDE-only lifecycle-listener probe; test discovery now includes only `*Test.class`. Configuration
cache works with the real native task. Plugin units pass **80/80** after the formatter correction;
root and lang Spotless checks pass.

`cff4c1283` is the native harness correction slice: scenario-scoped dirty-document cleanup,
close observation without demanding restart, bounded no-replay failure messages, a scoped
partial-result listener, and test discovery excluding IDE-only probes. Keep the listener and test
discovery changes together when extracting it.

`6b7b4dacb` is the independent L82 workload-configuration slice. The longer retention workload
is reproducible without editing test sources:

```bash
./gradlew :lang:lsp-server:test --tests '*XdkRetentionTest' \
  -Plsp.retentionCycles=360 -Plsp.adapter=compiler \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache
```

Run it with exclusive ownership of `:lang:lsp-server:test`; concurrent invocations share Gradle's
test-result directory. The default remains 120 cycles. These sampled weak-reference and latency
results do not establish a peak-memory budget or performance targets for large platform projects.

Full VS Code `run-SAWG42` finishes **126 passed / 24 failed**. X144 passes, and X145 now
passes with the current 5,000-method workload in **15,736 ms**. The failed cases are
X7/X8/X10/X32/X71/X73/X74/X76/X77/X80/X82/X83/X84/X87/X88/X90/X91/X93/X94/X95/X96/X97/X107
and X130. This is not a clean catalog receipt.

The server trace identifies spurious cursor invalidation: delayed directory-created notifications
arrive during incomplete-source completion/signature requests. The no-op watcher check treated
every failed analysis as stale and compared unsaved sources with disk instead of their current
overlays. It could therefore send ContentModified even though effective compiler inputs had not
changed. The correction compares current captured inputs with authoritative open text, while still
invalidating real membership, closed-source and resource changes. Older fallback builds after a
failed dependency lookup remain conservative so recreating a dependency still repairs consumers.
Controlled server regressions hold a cursor in flight while delivering directory notifications;
unchanged inputs preserve its result, and changed closed inputs retire it. All **38** focused watcher,
cursor and rename regressions pass with zero skips. Editor revalidation follows.

X130 is separate: Explorer's reveal command did not guarantee keyboard focus, so Copy Path read
the active Consumer editor rather than both selected source directories. The exact-selection guard
stopped before the move. The harness now focuses Explorer before its repeatable selection phase;
Cut/Paste/Undo/Redo remain single applications.


`f8fa59c86` is the separate L82 watcher-correction slice: compare current failed analyses against
open overlays and retain conservative dependency-recreation behavior. It includes the controlled
in-flight cursor tests and independently exercises closed-source and resource changes. The corrected
focused run passes **38/38**. VS Code `run-s4TD4a` passes all 23 formerly failing cursor cases;
X130 completes its move but fails in VS Code 1.140.0's `itemsCopied` repaint of removed Cut nodes.
The harness now retains that error, verifies Move/Undo/Redo/resource contents, and then reports it;
it does not replay Paste or count a repaint exception as a pass. Fresh focused `run-SlqjAz` passes
X130 without the repaint exception. The complete current catalog is running again.


Additional validation commits: `abd8b1a66` keeps the native progress-model observer corrections
with L81/X145. `636cad4b8` keeps explicit Explorer focus and post-move failure reporting with
X130's native resource-operation acceptance. Neither changes production adapter capabilities.


Current full VS Code `run-06Z6tq`: **149 passed / 1 failed**, catalog hash
`4bea60d80c9662dc45c932b2643f5aa9c2fc1df6ad6783d9eafb01e81dc1740c` (150 cases).
All 23 incomplete-query failures are corrected. X144 passes in 11,571 ms and X145 in 15,966 ms.
X130 verifies Move/Undo/Redo and every resource before reporting the host repaint exception; do
not describe this as a clean full pass or retry the completed mutation.

- [x] **L82 X130 isolation:** reproduce the post-Paste `itemsCopied` failure without Ecstasy,
  using a controlled public Explorer refresh during rename participation. Two independent fresh
  runs reproduce it; the normal X118–X130 sequence also reproduces it. See the X130 diagnosis below.
- [ ] **L82 X130 upstream repair:** report the reproduction and verify a VS Code fix. The latest
  release and inspected upstream source retain the unguarded repaint. X130 remains a failed host
  acceptance test when it reproduces; compiler Move/Undo/Redo/resource correctness is recorded
  separately. No upstream issue or PR has been submitted.


Final current-server backend verification: **1,504 passed**, three existing disabled placeholders
(1,507 total), and **74 packaged stdio tests passed**, zero stdio skips. Root and lang Spotless
checks pass. The included standard retention workload completes 120 cycles / 960 edit requests /
2,452 sampled weak references, zero retained; p50 228 ms, p95 400 ms including debounce while
editor tests were also running. Use the earlier exclusive 360-cycle receipt for isolated timing.

The ordinary VS Code extension smoke run exposes a separate harness isolation bug: unlike the
playbook it reused a profile and edited repository fixtures, then restored settings by writing the
file directly after the configuration API had loaded it. The next settings update reports unsaved
or externally modified settings (22 passed / 1 failed). All editor runs now get fresh workspaces
and profiles; compiler acceptance restores settings through the API only. Its rerun follows the
native IntelliJ catalog so two GUI harnesses never compete for focus.


Full IntelliJ `run-11631573891218503883` aborts at X103 after **71 passed / 1 failed**, leaving
79 cases unrun (startup is counted separately). The native driver observed an `OpenedDocument`
before LSP4IJ attached its synchronizer; its remote API incorrectly declared that value non-null.
The readiness predicate now waits for the synchronizer and successful didOpen completion within
the existing timeout. No action is replayed. The full run repeats after this harness correction;
zero IDE fatal errors in the failed attempt do not make its incomplete catalog a pass.


The next native full run, `run-2198032552241181704`, passes X103 and reaches **74 passed / 1 failed**
at X105 (76 unrun). Source is unchanged and the server returns the correct Document import action.
The subsequent native intention request targets the old Find Usages preview (`HealthyGraph.x`),
not `AutoImports.x`. Merely focusing an editor component can leave that tool window active. The
harness now activates the native editor area and checks both active-tool-window state and editor
data-context identity before invoking actions. It still uses pointer-free focus and refuses to
replay edits. Resume covers X105 and all unrun cases, plus X100/X103 to reproduce the preceding
native context. The earlier partial receipts remain explicitly incomplete.


`05841a78a` is the native document-readiness/action-context correction slice. Resumed
`run-12646132691690647657` passes **79 selected cases plus START**, zero IDE fatal errors and a
successful Gradle exit. It includes X100/X103/X105 together, X127 cleanup, X130 Move/Undo/Redo,
X139 open/closed Save All, and X145 progress/cancel/restart. Combined with the previous current-server
run, all 150 catalog cases have passing evidence. A final uninterrupted native catalog follows;
the earlier aborted full runs are not counted as clean full passes.


`586c5585c` is the independent VS Code smoke-isolation slice. Fresh isolated
`extension-tests/run-jgVHtx` passes **23/23**, including 40 error/recovery cycles (p50 609 ms,
p95 615 ms for each edit/recovery/hover cycle). Settings-draft ownership now passes after the
compiler workload, and no settings file is written into repository fixtures. Root/lang Spotless
checks and TypeScript compilation pass. Playbook behavior keeps its existing isolated workspace;
this extends that ownership to the ordinary extension suite.

### Final native and batch receipt (2026-09-30)

The uninterrupted IntelliJ `run-1843149430446112481` passes **all 150 catalog cases plus START**,
with **zero IDE errors** and successful Gradle/IDE shutdown. IDE 2026.2.3 runs with Ultimate
disabled and LSP4IJ 0.21.0. Its catalog hash is
`4bea60d80c9662dc45c932b2643f5aa9c2fc1df6ad6783d9eafb01e81dc1740c`.
This replaces combined coverage as the final native checkpoint without rewriting the failed runs
above. X105 passes in 4,165 ms, X127 in 4,961 ms, X139 in 14,008 ms, X144 in 5,201 ms and
X145 in 9,902 ms. In particular, open/closed Save All and completed-fixture cleanup no longer
produce the formatting notifications or shutdown File Cache Conflict from the failed run.

| Validation | Result and limits |
| --- | --- |
| Compiler/backend | 1,504 passed; three existing disabled tests; no failures/errors |
| Packaged stdio | 74 passed; no failures/errors/skips |
| IntelliJ unit suite | 80 passed; no failures/errors/skips |
| Java compiler suite | 512 passed; 40 known disabled/opt-in skips, no missing-XDK skips; unchanged since the earlier forced run |
| Extended retention | 360 cycles, 2,880 edit requests, 7,346 sampled weak references, zero retained; not a peak-memory or large-project performance claim |
| Full IntelliJ catalog | 150/150 plus startup; zero IDE errors; successful shutdown |
| Full VS Code catalog | **149/150, one failure: X130** in `run-06Z6tq`; Explorer's Cut-highlight repaint still throws after Move/Undo/Redo/resource assertions succeed |
| VS Code smoke suite | 23/23 in `extension-tests/run-Ts46BQ`; includes 40 error/recovery cycles (p50 513 ms, p95 613 ms) |
| VS Code selected reporter check | X144/X145 pass in `run-aBMhCu`; 148 other cases are not selected, not newly rerun |

`0f4bcd1ab` is the separate test-progress slice. The ordinary VS Code smoke runner previously
had no status counter; it now shares the playbook's reporter. Both show completed/total, remaining,
current test, failures and skips in the left status bar, with full titles in the tooltip. Mocha owns
the counts; there is no second mutable result counter. Keep this with editor test infrastructure,
after the corresponding runner files, when extracting PRs.

The implementation batch and its regressions are complete. The full cross-editor gate remains
**open**, because X130 is a failed native test, not a passing test with an ignored warning.
Physical Cancel-button selection, the broader P3/P4 manual acceptance, representative response-time
and peak-memory targets, and prolonged supported-platform lifecycle workloads remain separate L82
work. Extracted PRs must still pass independently; these integrated-branch receipts do not prove that.

### Reliability follow-up batch (X130, P3/P4, L82, 2026-09-30)

The next four separate checkpoints are authorized: isolate X130 without Ecstasy, automate P3/P4
where the installed hosts expose the necessary state, make representative workload measurements
reproducible, and reconcile completed versus remaining scope. Validation is batched afterward.

1. **X130 host isolation — implementation checkpoint.** The pinned VS Code bundle's
   `ExplorerView.itemsCopied` unconditionally rerenders previous Cut items. Paste's `finally`
   invokes it after the bulk move, when those nodes can already be absent. There is no public
   extension API to repair that tree. `explorer-move.ts` shares the exact guarded selection and
   single Cut/Paste path between X130 and an isolated plain-text reproduction. The reproduction
   loads an empty extension, asserts Ecstasy is absent, then records Move/Undo/Redo and contents.
   An unreproduced attempt does not establish a fix. X130 still reports the original exception;
   no retries, vendor patches, suppressed failures or substituted file-only moves are introduced.

   After compiling the extension tests, run from `lang/vscode-extension`:

   ```bash
   node scripts/run-vscode-tests.cjs --explorer-move-probe
   ```

   Results and host logs remain in `build/reports/explorer-probe/run-*/`. It exits unsuccessfully
   on a reproduced error; `not-reproduced` is explicitly distinct from proving X130 fixed.
   The standalone reproduction and X130 will run in the final validation batch. An upstream
   issue/PR has not been submitted.

2. **P3/P4 automation — implementation checkpoint.** Shared X146 changes only a dependency's
   return type and verifies provider refresh plus updated inferred hints in its untouched consumer,
   then reverses the change. X147 orders old report completion after a newer request, settings
   change, restart or UI disposal. VS Code's real status command previously published its reply
   unconditionally; it now checks request generation and connection ownership. IntelliJ adds the
   captured settings identity and retired-connection checks to its existing revision guard.
   Its asynchronous module reader is a constructor dependency, keeping the real UI testable without
   modifying global services or adding compiler state. Native publication tests wait for the EDT
   callback, not a sleep. The shared catalog grows to **152** cases; tests are written and execution
   is deferred until all four checkpoints are complete. Physical button selection and broad
   multi-project/window interaction remain separate from these bounded assertions.

3. **Representative workload — implementation checkpoint (2026-10-01).**
   `lang/scripts/compiler-workload.py` launches the packaged server against the explicit eleven-module
   platform graph from `lang/test-fixtures/compiler-workload/platform.json`. It checks clean workspace
   diagnostics, repeatedly edits unsaved overlays, cancels reference requests, reads outlines/hover
   and diagnostics, and reopens the graph in fresh server processes. Alternating graceful shutdown
   and EOF must exit without forced cleanup being counted as success. Source hashes prove that
   the checkout is unchanged. A concurrent status sampler records human-readable queued/running
   jobs and heap usage; the existing trace retains compiler API timings and submission/execution
   order. `xtc/languageServiceStatus` adds heap used/committed/max values with a packaged regression.
   Sampling neither forces GC nor enters the compiler worker. Sampled peak heap is a lower bound,
   not RSS, retained heap, or a multi-hour editor-soak claim.

   Build the compiler fat JAR, then run (choose a new report directory for each attempt):

   ```bash
   ./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler \
     -PincludeBuildLang=true -PincludeBuildAttachLang=true
   python3 lang/scripts/compiler-workload.py --workspace ../platform \
     --jar lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar \
     --cycles 10 --restarts 2 --heap 2g \
     --output lang/lsp-server/build/reports/platform-workload/run-01
   ```

   Use the actual fat-JAR filename produced by the build if its version differs. Resource overrides
   are explicit graph inputs; the tool never guesses them from `.kts` text. CLI timeouts are failure
   bounds, not latency targets. A failing workload retains its report, stderr and samples. Run it
   without competing compiler/editor tests; validation and measured baselines follow the fourth
   checkpoint, before deciding useful response-time or memory budgets.

4. **Checklist reconciliation (2026-10-01).** The 150-case L60 checkpoint is now checked off
   against the uninterrupted native receipt; the 152-case catalog has two newly written scenarios,
   not two additional passes yet. L68–L72 and L83 retain their established bounded completion.
   The remaining implementation work is distinguished from acceptance below; adding tests does
   not silently expand the API's proven language scope.

   | Scope | Implemented and proven | Concrete remaining work |
   | --- | --- | --- |
   | L62 rename | Recorded source families, primary/ordinary parameter slots, lambdas, escaped method values, packages/modules/companions, bounded cross-package qualification, host-persisted graph relocation and bounded union/cyclic callable-site proof | Unsupported qualification syntax, unproven empty namespaces, overlapping move trees, unsupported annotation constants/type shapes and wider generated routes; characterize each refused route before extending proof. External consumers omitted from the configured graph remain an explicit unknown boundary. |
   | L63 semantic actions | Import fixes, compiler-proven implement/override including bundled contracts, whole-return/typed-initializer extraction, adjacent same-type returned/initializer local inline and unused constant local removal | Private same-owner expression helpers are implemented in the latest batch below. General statement/context extraction, missing-declaration fixes, broader inline and global safe delete remain separate transformations. Each needs its own side-effect/capture/caller-closure design and positive/refusal tests. |
   | L64 completion/signatures | Import edits, syntax names/templates, guarded bounds and compiler-fitted literals/values; latest continuation adds wrapped names, enclosing-instance arguments and real platform anonymous-body recovery | Latest continuation below gives the exact supported forms, evidence and conservative exclusions. Remaining expansion includes inferred/ambiguous local names, arbitrary value synthesis and general special-this enumeration outside calls; these are not counted as implemented. |
   | L65 navigation/classification | Source/bundled navigation, recorded hierarchy/composition relations and resolved tokens | Conditional/synthetic/native/redirect routes and ambiguous binary source metadata need individual fixtures. Runtime function targets cannot be invented by a static hierarchy. |
   | L66 editing/structure | October 5 bounded closure: configured continuations/expression-list wrapping, standalone comment margins, resolved wildcard/source links, local/lambda/alias linked ranges and strict damaged-source structure | Conditional import source syntax is unsupported by the parser. Callable parameters/members require Rename. Literal splitting, comment reflow, declaration alignment and general pretty-printing remain outside the safe formatter; see the closure receipt below. |
   | L67 scale | October 5 bounded closure: exact per-root navigation reuse, editor/diagnostic seeding, fresh compiler-attempt ownership, 33/129-module controls and X251 in both editors | In-memory reuse removes duplicate compilation; disk persistence is not justified by the measured workload. Warm requests still capture current inputs. Agreed budgets and prolonged sessions remain L82 release work. |
   | L80 capability contract | Current method/producer inventory completed on 2026-10-01; optional presentation gates corrected, with exhaustive adapter-provider and rich/reduced-client checks | Revisit negotiation when a producer adds snippets, location links, tags or other optional fields. Generic native resource/snippet/confirmation edits remain deliberately refused. L81/L82 manual/release evidence is separate. |
   | L81 lifecycle | Trace, owned progress/cancel, partial results, refresh, shutdown, X145 pending restart and X146/X147 refresh/report ownership | Physical Cancel-button selection and broader per-provider visual/multiple-window interactions remain manual acceptance rather than absent server implementations. See the following validation receipt. |
   | L82 release evidence | Previous backend/compiler suite, full native 150-case checkpoint, 360-cycle retention receipt, current stdio/plugin/selected editor tests and 30-cycle platform baseline | Intermittent X130 host failure; agreed response-time/heap targets, prolonged editing/restart/process-leak runs and supported-platform/packaging acceptance. Later extracted PRs still need independent validation. |

   L73–L79 remain explicit missing/optional scopes with their existing investigation tasks. R1–R8
   still own reusable execution and DAP. This reconciliation closes no unimplemented feature by
   renaming it a test task. Validation is the next action; no broad editor suite ran between these
   four implementation checkpoints.


### Reliability validation receipt (2026-10-01)

Implementation checkpoints, kept separate for later extraction:

| Commit | Scope |
| --- | --- |
| `32e80a612` | Shared native Explorer move and extension-free host reproduction |
| `7d94c86bd` | Late report ownership and shared X146/X147 in both editors |
| `62e6a0ec8` | Packaged platform workload, queue/API/heap evidence |
| `557dbd894` | Reconciled implementation and acceptance checklist |
| `3db16d923` | Observation-only Explorer wait and cleanup of controlled delayed replies; keep with test infrastructure |
| `43da5db69` | Native settings/restart publication correction and regression; keep with `7d94c86bd` |
| `f8980976c` | Compiler trace aggregation and telemetry formatting; keep with `62e6a0ec8` |

The batched build passes TypeScript compilation, native-driver compilation, **74 packaged stdio
and 80 IntelliJ unit tests**, with zero failures/errors/skips. These reran on October 1. The earlier
backend/compiler receipts remain historical evidence; the new server change only adds nonblocking
heap telemetry, covered by the packaged status test.

VS Code `run-Ki54bo` passes **8/8 selected cases**: X118, X129, X130, X136, X137, X139,
X146 and X147. X146 verifies dependency-driven refresh and changed hints without a consumer edit;
X147 exercises late real replies, settings changes and retirement of the old connection/PID.
This does not replace the earlier 149/150 full-catalog receipt. In particular, X130's intermittent
host failure remains open even though this selected attempt passes.

The extension-free Explorer probe `run-lqt47d` records **not-reproduced** on VS Code 1.140.0,
with Ecstasy absent and Move/Undo/Redo/content assertions completed. An earlier probe
`run-xNCQiR` stopped because it read the filesystem before Undo completed. The probe now polls
only observations within a failure bound; each mutation still executes once. Neither attempt
establishes an upstream reproduction or a fix. The observed bundled-host repaint path remains the
lead; preserve the original X130 failure and obtain reliable extension-free reproduction before
claiming an upstream defect is independently proven.

Platform `platform-workload/run-01` passes **30 overlay cycles and 30 cancellations across three
server processes**, using eleven configured modules and fifty source files. Each cold workspace
pull returns 49 diagnostic documents without errors. All child processes exit themselves: graceful
shutdown, expected EOF exit, then graceful shutdown. Source hashes are unchanged. The report
records the tested JAR hash and per-session traces, samples, outcomes and timings.

| Measurement | Observed per-session range |
| --- | --- |
| Cold workspace diagnostics | 4.85–4.93 seconds |
| Edit to outline, including debounce | p50 330–338 ms; p95 352–366 ms |
| Warm hover | p50 0.76–0.77 ms; p95 5.25–5.64 ms |
| Cached document diagnostics | p50 0.55–0.65 ms; p95 1.78–1.89 ms |
| `compileModule(tree)` | p50 204–208 ms; p95 663–679 ms; maximum 1.03 seconds |
| Sampled peak used heap with a 2 GiB cap | 707–930 MiB |
| Concurrent traced compiler API threads | Maximum one in each process |

The trace captures one queued diagnostic job at its largest observed queue; the periodic sampler
misses that brief interval and reports zero. Both retain readable job identities. Do not equate
sampled queue size with all submissions, sampled heap with absolute peak/RSS/retained heap, or
thirty cycles with a prolonged soak. These are a reproducible local baseline, not release budgets
or cross-platform guarantees. L67/L82 still require agreed budgets and longer supported-platform
editing/restart workloads.


IntelliJ `run-13013331047107199721` passes seven selected cases plus START, then fails X147:
the stale report guard observed compiler-graph settings, but a service-only project override retained
the inherited graph owner. Consequently the old report could still publish after a transport change.
The final guard captures both compiler and effective service settings, and the actual connections
at request start, then rechecks all of them on the EDT. This also closes the handoff window between
reply arrival and UI publication. The shared X147 driver explicitly restarts in that window without
changing settings; no synthetic server identity replaces the real connection.

Focused IntelliJ `run-5612866093768251189` passes X136/X137/X147 plus START, with zero IDE errors
and successful shutdown. Together the two selected receipts cover all eight requested cases,
including X146's native cached inlay refresh. The initial failure remains recorded; this is not
an uninterrupted eight-case or full 152-case run. Both editors use catalog hash
`0aedab6aa74999cb21bf41a3d820d05bd45cc6012db4e5fb65646d17573f9374`.

The four-checkpoint scope now has implementation and bounded acceptance evidence. P3/P4's new
controlled provider/report assertions are automated and pass; physical Cancel-button selection,
broader per-provider visual and multiple-window acceptance, intermittent X130 reproduction, final
capability audit and prolonged supported-platform measurements remain open. No AST/embedding change
or newly advertised language feature was needed for this batch.


Final read-only root/lang Spotless checks and `git diff --check` pass. The plugin unit suite reruns
against the final publication guard: **80 passed, zero failures/errors/skips**. All work stays on
`lagergren/errs`; these local checkpoint commits have not been pushed by this batch. No upstream
issue, remote branch or PR was created. Final process inventory finds no surviving test editor,
playbook runner, workload or language-server processes.


### X130 isolated host defect and harness focus correction (2026-10-01)

The original failure is now reproduced independently of Ecstasy. The empty-extension probe uses
X130's actual nested directory layout and unchanged shared source/resource data. It asserts that
Ecstasy is absent and launches no compiler or LSP client. With `--refresh-during-move`, its sole
rename participant waits for the public Refresh Explorer command and returns no edit. This forces
an Explorer refresh between Cut and the filesystem move without sleeps or patched host code.

```bash
./gradlew :lang:vscode-extension:npmCompile \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true
cd lang/vscode-extension
node scripts/run-vscode-tests.cjs --explorer-move-probe --refresh-during-move
```

The diagnostic probe exits **nonzero** when the host bug reproduces. Its `results.json` and
`move-trace.json` retain the exception and completed Move/Undo/Redo/content checks. Omitting
`--refresh-during-move` runs the same nested fixture without the controlled refresh. It does not
turn an unreproduced attempt into a claim of a fix.

**Cause:** Cut keeps references to Explorer tree items. Refresh rebuilds those items. Paste moves
the directories using the current model, then clears Cut highlighting using the obsolete items.
`ExplorerView.itemsCopied` unconditionally calls `tree.rerender` for them, which throws because their
old node identities no longer exist. The exception escapes Paste's cleanup; the following reset of
its move/copy flag is also skipped. The host repair should guard/reconcile stale repaint targets and
ensure that cleanup resets its state even if repaint fails. That repair has not been implemented or
validated in an upstream checkout here.

[VS Code 1.140.0](https://github.com/microsoft/vscode/releases/tag/1.140.0) is the latest released
version checked on October 1. The same unguarded code remains at upstream commit
[`5e8e57c65bbd4ee459f4cabc5697a9a3a5f3aeda`](https://github.com/microsoft/vscode/blob/5e8e57c65bbd4ee459f4cabc5697a9a3a5f3aeda/src/vs/workbench/contrib/files/browser/views/explorerView.ts#L900).
An extension has no supported API to replace those private tree nodes or repair Paste's cleanup.
Intercepting native Paste, changing clipboard/Cut state during a move, replacing the native move
with a file-only edit or swallowing the exception would change behavior or hide the defect. No such
workaround is installed. There is no compiler/embedding prerequisite to the upstream repair.

The normal X130 trace proves that our `workspace/willRenameFiles` reply is exactly
`{"documentChanges":[]}`. The server neither moves files nor duplicates the requested resource
operations. In `run-LjhgZP`, the reply arrives at 496 ms, the native move notification at 506 ms,
and the host cleanup exception at 512 ms. Undo and Redo each execute once, deliver their expected
move notifications, and restore all source/resource contents. This isolates the repaint exception
from compiler move correctness without declaring the case green.

| Attempt | Result |
| --- | --- |
| `compiler-playbook/run-haOXDr` | 7/13 passed; six native Undo failures, including X130. Kept as a failed run. No completed action replayed. |
| `explorer-probe/run-D9AfiJ` | Nested fixture with Ecstasy absent, no controlled refresh: Move/Undo/Redo/content checks pass; repaint defect not reproduced. |
| `compiler-playbook/run-QLBWYV` | X130 alone passes with empty compiler edit and native Move/Undo/Redo. |
| `explorer-probe/run-czIsNj` | Controlled refresh reproduces the exact `itemsCopied` exception without Ecstasy; all move/history/content checks complete. |
| `compiler-playbook/run-LjhgZP` | X118–X129 pass; X130 reproduces the original host repaint error after all move/history/content assertions. **12/13, not green.** |
| `explorer-probe/run-GSxFkH` | A second fresh controlled attempt reproduces the same host exception; all move/history/content checks complete. |

The first run exposed a separate harness focus weakness: focusing the editor pane does not ensure
that the window owns desktop focus. Native Undo can consequently dispatch to a DOM input or fail
to select Explorer's undo history. The harness now checks `window.state.focused`, activates through
VS Code's own Focus Window command only when needed, and waits for focus before dispatching the
native action once. It never moves the mouse. The corrected X118–X129 sequence passes, and X130's
Undo/Redo assertions pass even in the repaint-failing run. Public command/file/window events and
the actual LSP request/reply are recorded for X130; observers neither supply edits nor delay rename.
The explicit diagnostic probe's refresh participant is separate from these observers.

All changes in this slice are test infrastructure and documentation. TypeScript compilation and
read-only root/lang Spotless checks are the build gates; no Java, Kotlin, compiler or production
plugin behavior changes require a new backend or IntelliJ run. X130's upstream fix remains open;
additional LSP implementation can proceed with this independently proven host defect tracked.


Extraction checkpoints: `f83707d9b` contains the native-focus test correction;
`b8ea5fcd3` contains the observed command/file/LSP trace and controlled host reproduction, and
uses that focus helper. Keep both with the editor test infrastructure. Final TypeScript compilation,
root/lang Spotless checks and `git diff --check` pass. No test/editor/LSP processes remain. These
commits are local on `lagergren/errs`; this slice creates no remote issue, branch or PR and performs
no push. The failed host receipts remain failed.


### L80 final capability contract audit (2026-10-01)

The current-producer audit is complete. The [detailed inventory](errs-audit.md#l80-final-capability-contract-audit-2026-10-01)
maps all provider families to their negotiation choices and regression coverage. Three actual gaps
are fixed: document-link tooltips on both response paths, per-signature active-parameter metadata
with a legacy selected-overload fallback, and related information in every pull-diagnostic report
shape. The compiler-only experimental rename proposal is no longer advertised by other adapters.
No embedding or AST changes, new mutable state, or editor-private workarounds are needed.

`CapabilityContractTest` checks all 25 adapter flags separately, a featureless adapter and the real
compiler's provider inventory. `CapabilityNegotiationTest` adds absent/false/true link and signature
cases. `DiagnosticPresentationTest` covers independent push/pull flags, related documents in full
and unchanged reports, and full/unchanged workspace reports. Packaged stdio adds rich and reduced
presentation sessions; existing semantic tests now declare the capabilities their assertions need.

Validation: the corrected focused backend run passes **119/119, zero failures/errors/skips**
(XML timestamps 2026-10-01 08:17:11–08:17:19 UTC). The full packaged run executes all **76 cases**:
75 pass; the new reduced-client assertion unboxes a correctly absent optional field. After making
that assertion nullable, both rich/reduced packaged variants pass **2/2, zero failures/errors/skips**
(08:20:08 UTC). This is full-suite coverage followed by a focused test correction, not a second
complete packaged run. The first backend attempt's 117/119 result was likewise corrected after
two new fixtures expected noncanonical `file:///` spelling. All failures were fixture assertions;
the receipts are retained rather than silently omitted. Root/lang `spotlessCheck` and
`git diff --check` pass.

The 152-case shared catalog is unchanged: reduced initialize capabilities are protocol fixtures,
not settings that a running modern editor can toggle. Existing X131 drivers accept negotiated
link targets without demanding optional tooltips. No desktop harness is rerun for this server-only
response-shaping batch. The manual coverage map now identifies the additional protocol cases.

Extraction checkpoint: `84f1f8e63` contains the L80 response-negotiation correction and tests.
Keep it together after the earlier L80 presentation/resolve/pull-diagnostic slices. The exhaustive provider test and compiler-only
experimental gate belong with it. The X130 probe/focus commits remain a separate harness/upstream
slice. L80 completion does not close L81 manual checks, L82 release/scale evidence, the upstream
Explorer repair, or any explicitly missing language feature.


### L81 progress, refresh and transport checkpoint (2026-10-01)

The progress-creation deadline previously timed out the RPC future itself. A client that successfully
created its token later could retain that registration until disconnect. The server now races a local
deadline without completing the RPC future. A late successful acknowledgement receives a
noncancellable begin/end pair to retire it; it cannot reclaim cancellation ownership of the query.
Closed connections send no late progress. Controlled tests cover completion, cancellation, timeout,
late acknowledgement and independent use of identical tokens on separate connections.

Operation titles are now simply `Finding references`, `Checking workspace`, and similar. The
editor supplies the Ecstasy service/source identity, avoiding `Ecstasy Language Server: Ecstasy:`.
Progress also identifies the requested source or workspace. Once a second it samples copied
compiler queue metadata and shows the active operation/source, queued count and pending debounce,
for example `src/Consumer.x · Compiler: compiling Library.x; 2 jobs queued`. The compiler description
is shared worker activity, not a claim that each visible progress item is compiling independently.
Reference and workspace-diagnostic requests retain separate cancellation tokens. No AST traversal,
compiler invocation, fake percentage or new compiler mutable field is involved. Reporting stops on
completion/disconnect; scanning retains its existing actual file-count reports. IntelliJ owns the
shared progress popup size; the requested optional widening was declined rather than changing
global IDE sizing or reaching into private UI state.

All five refresh families (diagnostics, semantic tokens, inlays, code lenses, folding) have controlled
negotiation, coalescing, failed-reply and close regressions. X146 observes all five actual VS Code SDK
refresh events and all providers negotiated by the installed IntelliJ client. It preserves untouched
consumer text/version and checks inferred types through dependency edit/revert. X147 continues to
exercise out-of-order settings replies and connection retirement.

The packaged server regressions reject unknown methods and wrongly typed parameters, then perform
normal semantic requests through the same reader. Two real server processes open the same URI with
different source and reuse the same progress/partial-result tokens; shutting down one leaves the
other's semantic state and transport healthy. This does not claim multi-window IDE acceptance.
LSP4J's wrong `ParseError` classification remains explicit as **UP15** in the
[upstream register](errs-upstream-issues.md); recovery passing does not fix classification.

Client-request audit: production uses negotiated `workspace/configuration`, guarded
`workspace/applyEdit`, watcher registration/removal, work-done creation and negotiated refresh
requests. Workspace roots arrive through initialization/folder notifications. There is no current
`window/showDocument`, `window/showMessageRequest` or `workspace/workspaceFolders` request producer
to add merely for protocol coverage. Existing log/show-message notifications and `$/setTrace` /
`$/logTrace` retain their own contracts. No compiler/embedding API expansion was needed.

Validation: **42 focused backend tests and 78 packaged transport/process tests pass, with zero
failures/errors/skips**, after correcting the new malformed-input expectation and test-only driver
compilation/expectation issues. The full packaged receipt is dated 2026-10-01 09:21 UTC; it supersedes
the earlier 77/78 result. The 36-test pre-notification-detail run is not counted as new coverage.
The final notification/queue changes pass all 16 focused progress, label, indexing and trace tests
at 09:37 UTC; the corrected IntelliJ driver compiles.

Native attempts retained for diagnosis:

- VS Code `run-aGrz1Y`: X146/X147 pass (1.13/1.28 seconds). X145's opt-in visible-control attempt
  fails after no Cancel click was delivered; the UI tool selected a different running Code instance.
  This is not a passing Cancel-button result.
- IntelliJ `run-5525893729888553317`: START/X146/X147 pass, zero IDE failures. X145 fails because
  its 5,000-method workload completes before the old selector activates the visible control.
  Larger diagnostic attempts below separated selector problems from workload duration.
- IntelliJ `run-2755943839968156953` and `run-3713040687649832760` time out locating Cancel.
  The latter diagnosis found that this IDE uses `ProgressPanel`, while the harness searched for
  the legacy `ProgressComponent.MyComponent`. The corrected driver selects the current panel's
  public Cancel control by its unique full task title and invokes its accessibility action; it
  neither reflects into private widgets nor cancels the model directly.
- VS Code `run-qPLyRY` verifies the simplified title and source detail, then fails after no
  visible Cancel click is delivered. It is not a passing cancellation receipt.
- IntelliJ `run-9280218228617766650` activates the visible Cancel control, then fails because
  the standalone driver lacks `ResponseErrorCode` at runtime. Its source could compile against
  the IDE plugin APIs, but those APIs are not supplied by the driver JVM. A runtime-only JSON-RPC
  dependency now supports its named error-code assertions without changing the packaged plugin.
- IntelliJ `run-704976644057533068` passes START and every X145 assertion (49.7 seconds), but
  **fails overall** because the IDE records a 21,282 ms UI freeze. Four retained thread dumps show
  range-marker updates on the EDT during the harness's `DocumentImpl.setText` replacement of the
  20,000-method fixture, not a wait for a compiler reply. This remains an L82 scale investigation;
  it has not been isolated sufficiently to assign upstream ownership. The now-working automated
  button selector returns to the original 5,000-method workload; freeze detection stays enabled.

The catalog remains 152 cases. VS Code's ordinary X145 uses the SDK's real cancellation callback;
`--cancel-ui` explicitly requires a visible-control click and records that mode in `results.json`.
VS Code's manual visible-control workload is 20,000 generated methods to allow time for a click.
Automatic IntelliJ control activation and restart use 5,000. A workload finishing before
cancellation is exercised remains a failure. Remaining native receipts will be
recorded separately; broad multi-window/close-overlap, UP15, X130/UP16 and L82 release/scale acceptance
are not closed by these tests.

Final bounded native receipt: IntelliJ `run-6551466376631163236` passes START and X145, with
**zero IDE failures**. X145 takes **7,204 ms**, including actual visible-control activation,
subsequent hover, pending restart, preserved unsaved text and old-PID exit. This closes IntelliJ's
visible Cancel acceptance only; it does not erase the larger-fixture freeze or validate VS Code's
manual-click mode.

VS Code `run-EXa2qJ` passes X145 in **4,868 ms**, including the source-detail assertion, actual SDK
callback cancellation and restart. It records `progressCancellation: native-token`; the physical
button remains unverified. Its exit code is zero. VS Code's own normal utility-process exit handler
supplies the literal `signal: "unknown"`, which does not indicate an Ecstasy crash.

Notification presentation follow-up: the IntelliJ startup title is retained and its body lists
Version, Adapter and Process ID on separate escaped HTML lines, using the native body style.
The eight-second fade remains unchanged. The ordinary VS Code X145 no longer opens Notifications:
that exposed the host's empty “No new notifications” panel immediately after automatic cancellation.
Only explicit `--cancel-ui` acceptance opens/hides that panel. These small presentation changes
are compiled/formatted; the native receipt above precedes them.

Checkpoint extraction map:

| Commit | Scope | Extraction relationship |
| --- | --- | --- |
| `a3b700997` | Spotless with ktlint, aligned with master | Keep the mechanical formatter migration separate from compiler/LSP behavior. |
| `67b4cf2ea` | L81 progress ownership, live compiler activity and backend/transport regressions | Follow the earlier connection-owned progress/lifecycle implementation. No compiler AST or embedding API changes. |
| `b724b39bb` | Editor progress/cancellation/refresh acceptance, named protocol codes and startup presentation | Follow `67b4cf2ea`: progress-title and source-detail assertions require its server behavior. The JSON-RPC dependency belongs only to the standalone IntelliJ test runtime. |

The accompanying upstream-tracking/documentation checkpoint adds UP01–UP16 and the source markers,
records the validation above, and preserves the open acceptance and large-file findings. These three
L81 checkpoints are one tested integrated slice; each future extracted PR still needs its own
independent validation. No remote publication is part of this local checkpoint.

## L82 large-file IntelliJ freeze investigation (2026-10-01)

Remote verification before this investigation: the fetched `origin/lagergren/errs` and local HEAD
both point to `7765ba4c10d534216dcc7d83817ab181aad92d14`; the checkpoint was already pushed and the
tree was clean. This investigation adds an opt-in native probe, not a full playbook rerun or a
compiler/AST change. The issue is tracked as **UP17 — IntelliJ Platform**.

The original X145 failure (`run-704976644057533068`) has four EDT dumps in
`RangeMarkerTree.documentChanged` → `updateAffectedNodes` → `IntervalTreeImpl.removeNode` →
`correctMaxUp` → `maxEndOf`, called by the harness's `DocumentImpl.setText`. The UI thread is
RUNNABLE, not waiting for a compiler lock or reply. Server tracing puts module compilation at
7.9 seconds and the complete compile job at 9.3 seconds, before the later 21.3-second UI freeze.
Some large semantic replies are also slow; that is a separate L82 server-response investigation.

Inspection of the pinned IDE 262.10968.63 bytecode explains the hot path: the range-tree update
first marks affected nodes invalid, then removes them individually. Each removal recalculates
ancestor maxima. `maxEndOf` recursively visits children when a node is temporarily invalid, so
many removals repeatedly traverse large invalid subtrees. The measured scaling below is roughly
quadratic over this range. `DocumentImpl.replaceString` already trims unchanged prefixes/suffixes;
adding that logic to our driver would duplicate the IDE's existing optimization.

The retained `LargeFileProbe.plain` control creates an unattached `DocumentImpl` containing
880,000 characters and strongly retains evenly spaced ordinary range markers. It replaces the
last three quarters of the text and checks that exactly the unaffected quarter of markers remains
valid. There is no editor, PSI file, language client or compiler attached to that document.

| Ordinary markers | First run | Final run |
| --- | ---: | ---: |
| 0 | 40 ms | 40 ms |
| 20,000 | 326 ms | 355 ms |
| 40,000 | 1,177 ms | 1,286 ms |
| 80,000 | 5,558 ms | 6,097 ms |
| 80,000, `DocumentUtil.executeInBulk` | Not measured | 5,064 ms |

Bulk-update mode still incurs seconds of tree work; it is not a repair. These are local diagnostic
measurements, not portable response-time thresholds or a claim about all IntelliJ releases.

Native receipts under `lang/intellij-plugin/build/reports/compiler-playbook`:

- `run-14369876559296903585`: the decorated 20,000-to-5,000-method replacement takes **24,262 ms**.
  `large-file.jfr` records 1,986 of 2,143 sampled EDT execution stacks (92.7%) at `maxEndOf`;
  `thread-sample.txt` and the IDE freeze dumps show the same path. The run fails. Its initial
  inventory helper also logged a missing read action; that probe defect was corrected separately.
- `run-5865295688126037148`: native replacement takes 59 ms, but its inventory contains **zero
  semantic highlighters**. The Gradle run passes; it does **not** validate decorated-editor
  responsiveness. A new readiness guard requires native highlighting before replacement.
- `run-12466134132242302471`: with that guard, the file has **80,005 highlighters** with LSP
  keyword/property/namespace/type/number/method attributes. Replacement leaves 20,005 and takes
  **14,591 ms**. Assertions complete, but the run **fails overall on the IDE freeze gate**, with
  no probe read-action error. Ordinary document markers are counted separately from markup-model
  highlighters; the latter own the large tree. Receipts are in `large-file-editing.jsonl` and
  `results.json`, with the original freeze detection enabled.

No production workaround discards highlighting, changes text-edit semantics, or suppresses freezes.
The fix belongs in the platform's bulk range-tree update; its marker-validity and performance
regressions should accompany an upstream repair. Prepare an upstream report from this independent
control before considering a local compatibility workaround. Separate follow-ups remain: large
semantic response time, decoration installation cost, representative project workloads and memory.
The bounded 5,000-method X145 remains the progress/cancellation test, not a large-file acceptance gate.

Run only this diagnostic, explicitly opting into a possible UI freeze:

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
  --tests '*CompilerPlaybookTest.largeFileEditing' -PintellijLargeFileProbe=true \
  -Plsp.adapter=compiler -PincludeBuildLang=true -PincludeBuildAttachLang=true \
  --rerun-tasks --no-build-cache
```

It writes each control result before proceeding, requests native editor focus without moving the
pointer for highlighting readiness, and preserves the IDE-error gate. The ordinary playbook does
not run it unless explicitly enabled. The probe and its Gradle flag belong together in the future
IntelliJ acceptance PR; they introduce no production dependency or embedding API requirement.

Startup notification presentation follow-up: the original three-line table exceeded the native
collapsed-content height and ellipsized the PID. The two-row layout fit but implied inconsistent
grouping. The final design keeps the native title and puts **Version · Adapter · PID** on one
compact metadata line, with equal-weight values, theme-aware dimmed labels and escaped dynamic
text. The standard width and eight-second fade remain unchanged. Keep this small production
presentation change separate from UP17's diagnostic probe when extracting commits/PRs.

Final presentation validation: `run-4384519003946621793` passes START and STARTUP, including the
untouched-balloon fade assertion, edits during initialization, bulk replacement, restart and
close/reopen. STARTUP takes 23.5 seconds; `results.json` reports zero IDE failures. This receipt
uses the final single-line metadata layout. It does not repair or override UP17's failed diagnostic.

Checkpoint separation: `aeff9562f` contains only the startup notification presentation change.
The following **Isolate IntelliJ large-file range-marker freezes** commit contains the opt-in
probe, its Gradle/mode wiring, UP17 evidence and these documentation updates. Extract the first
with plugin presentation work and the second with IntelliJ acceptance/performance diagnostics;
neither requires a compiler or AST change.

Next work after this checkpoint, in priority order:

1. Profile the large-file semantic requests that remain slow after compilation completes.
   Separate waiting, semantic computation and response serialization for hover, inlay hints and
   semantic tokens before selecting a fix. Retain measurements and targeted regressions for any
   change; UP17's UI freeze is independent of this server-side investigation.
2. Complete L81 native acceptance: actual VS Code Cancel-button activation, then overlapping
   project close/reopen and multiple windows, with unsaved-state and process-retirement checks.
3. Complete the L82 retention/peak-memory and prolonged editing/restart workloads, then a combined
   backend/protocol/editor checkpoint. Keep failed upstream gates explicit rather than treating
   partial acceptance as a fully green release.

UP17 remains a separate platform repair/report task. The minimal reproduction and CPU profile
are retained; no upstream issue has been submitted and no production workaround is claimed.

### L67/L82 semantic-response measurements (2026-10-01)

The packaged workload now accepts `--semantic-methods 5000 20000`. It generates disposable
modules with ordinary references and inferred locals, waits for diagnostics, and checks hover,
reference counts, one-line/full-file hints and tokens through stdio. Each size/shape has its own
process. Reports preserve the JAR hash, PID/exit, sampled heap, query timings and compiler trace.
No editor or source checkout is changed by this mode.

```bash
python3 lang/scripts/compiler-workload.py \
  --jar lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar \
  --semantic-methods 5000 20000 --cycles 2 --timeout 240 \
  --output lang/lsp-server/build/reports/semantic-workload/example
```

`lsp-query` trace phases separate analysis readiness, execution dispatch/lock wait, backend
completion and conversion/publication. Protocol `reply-ready` and `writeMs` measure serialization
plus transport output, not pure JSON serialization. Backend completion includes callback scheduling;
compiler queue/API spans identify actual compiler execution within it. No source/payload logging or
compiler lifetime changes are introduced.

The baseline uses the prior packaged server: 5,000 inferred locals require 7.5–8.0 seconds for
either a one-line or whole-file hint request. At 20,000 locals the first one-line request takes
132.7 seconds. Its 40-second JFR contains 3,314 execution samples, 3,307 in semantic position/range
comparison; the call path repeatedly enters `occurrenceAt` from hint tooltip construction.
The baseline was deliberately interrupted during a further repetition; it is not a passing suite.
Plain 20,000-method token requests take 12.2 seconds cold and 4.8 seconds repeated. The source has
a lexical-by-semantic overlap scan, and hints build every tooltip before range filtering.

Keep the workload and phase tracing as a separate diagnostic commit. The following presentation
fix and regression/measurement receipt belong to the compiler adapter implementation slice.

The presentation fix filters hint positions before rendering, uses the already-selected declaration
facts for inferred tooltips, and checks lexical overlap through sorted semantic starts/prefix maximum
ends. Nested/duplicate ranges and adjacent half-open boundaries are preserved, including single-line
files. The index is request-local immutable data; no retained cache, AST field or compiler API changes.

`semantic-workload/indexed/results.json` passes all four size/shape sessions, two query cycles each,
and verifies all four processes exit normally. Local repeated-request measurements:

| Input/request | Before | After |
| --- | ---: | ---: |
| 5,000 inferred locals, one-line hints | 7,523 ms | 3.0 ms |
| 5,000 inferred locals, whole-file hints | 7,801 ms | 22.8 ms |
| 20,000 inferred locals, one-line hints | 132,714 ms (first request) | 11.7 ms first / 7.3 ms repeated |
| 20,000 inferred locals, whole-file hints | Remaining baseline interrupted | 87.1 ms first / 66.4 ms repeated |
| 20,000 plain methods, full tokens | 4,824 ms | 146.8 ms |

At 20,000 inferred locals, the repeated whole-file hint request spends about 34 ms in backend work
and 25 ms serializing/writing; tokens spend 124 ms in backend work and 28 ms serializing/writing.
Hover remains 2–3 ms warmed. First references still builds the separate project-analysis snapshot
(about 10 seconds including another compilation); repeated references take 207 ms. This cold cost
is retained as a separate L67 follow-up, not attributed to serialization or considered fixed.
Sampled peak heap is 752 MiB for that case, not retained heap/RSS or an agreed memory budget.

Fifteen focused presentation, overlap/range/tooltip, trace and lazy-resolve tests pass with zero
failures/errors/skips. The large-file workload is an opt-in diagnostic; no brittle wall-clock
assertion is added to unit tests. Native editor application cost, UP17, prolonged L82 workloads and
the combined editor checkpoint remain separate gates. Extract diagnostic commit `3ee97b161` with
tracing/workload infrastructure and the presentation fix with compiler-adapter presentation.

### L81 native project/window lifetime batch (2026-10-01)

Two additional opt-in drivers reuse X145's shared source/module/workload data. IntelliJ's
`CompilerPlaybookTest.projectLifecycle` opens two actual project frames in the same IDE, applies
different project-local compiler graphs, closes one while compiler work and a reference request
are pending, and reopens it. The other connection must keep its PID, unsaved text and correct
references/hover. The closed project's pending future and process must retire. Native close saves
that project's source; reopening must recover its contents and start a different compiler process.

VS Code's `--project-lifecycle` launcher uses two native windows with separate disposable profiles
and extension hosts. One quits its single-window instance through the native command while work is pending, then
reopens using the same profile. Its unsaved buffer must come back from actual hot-exit backup;
the sibling's buffer, request and PID must remain intact. All compiler processes must exit with
their hosts. This is separate-instance window evidence, not two windows sharing one Electron process.
The drivers do not kill servers to make lifecycle assertions pass and do not move the pointer.

Native validation now passes:

- IntelliJ `run-3852388644684425549`: START and START_PROJECTS pass; zero IDE errors. Closing PID
  62874 retires its pending reader; reopening starts PID 62895. Primary PID 62872 and both source
  contents are preserved. The generated second project's exact path is trusted in the disposable
  IDE before opening it; normal project-trust defaults remain unchanged. The probe uses a write-intent
  action for VFS refresh/editor setup, and query positions come from source anchors.
- VS Code `project-lifecycle/run-zI8fT9`: primary PID 65779 keeps its unsaved source and 5,001
  references while secondary PID 65762 closes during pending work. Real hot-exit restoration keeps
  the reopened source dirty and starts PID 66383. All three processes exit with their native hosts.
  The driver installs the local extension and a test-only controller into an isolated extensions
  directory. VS Code deliberately omits persistent backup paths in extension-development windows;
  earlier development-host attempts (`run-VFX6e3`, `run-dSJ2k0`) failed restoration and are retained.
  Normal installed-extension windows exercise the actual backup path; no exit failure is suppressed.
- VS Code visible Cancel attempt `compiler-playbook/run-6JOJx7` fails as unexercised: the accessibility
  tool selected another running Code instance, so no click was made. The request completed before
  cancellation. This does not close the visible-control gate or indicate a server cancellation defect.

Driver implementation `7364e0af8` and the following trust/normal-profile correction belong together
in the future editor-acceptance PR. The semantic fix remains the separate `4acdbdb59` slice; memory
instrumentation is `78023c53b`. Broader shared-process windows, supported platforms and visible VS Code
Cancel remain explicitly outside these passing receipts.

### L82 memory-workload instrumentation (2026-10-01)

The existing real-project workload adds opt-in `--sample-rss` (macOS/Linux `ps`, at most once per
second) and `--gc-every N` (owned-server `jcmd GC.run` between edit cycles). It records post-GC heap
checkpoints separately from request latency, sampled RSS/heap peaks, and periodic cycle progress.
Post-GC heap includes intended live caches; `XdkRetentionTest` remains the object-reachability gate.
The two-cycle `platform-workload/memory-smoke-authorized` control passes, records approximately
85.2 MB used heap at both GC checkpoints and a 1.05 GB sampled RSS peak, preserves source hashes
and observes normal child exit. The first sandboxed sampling attempt failed on `ps` access and is
retained as a failed receipt. Longer workloads and combined validation follow this tooling commit.

### L82 bounded extended-workload checkpoint (2026-10-01)

`platform-workload/extended-edit-restart/results.json` retains two completed 1,200-cycle sessions
against 11 configured platform modules and 50 source files. Every cycle cancels a pending reference
request, then checks symbols, hover and diagnostics. Session 0 exits normally with code 0; session 1
exits with the expected code 1 after transport EOF without shutdown. Both have 1,200 successful
cancellations, no sampling errors and at most one compiler API thread active. Source hashes are
unchanged. Median compile time is about 210–212 ms; warm hover p95 is below 0.8 ms.

The initially planned third session was deliberately interrupted at initialization after the user
questioned the run duration. Its cleanup PID was verified gone, but it is excluded from acceptance.
The overall JSON therefore remains `failed`/interrupted (launcher exit 130); do not relabel the whole
three-session run as passing. Completed session records preserve their independent passing results.

| Completed session | Post-GC heap at cycle 100 | Post-GC heap at cycle 1,200 | Sampled peak RSS |
| --- | ---: | ---: | ---: |
| Normal shutdown | 83.1 MiB | 97.7 MiB | 1.06 GiB |
| Transport EOF | 83.1 MiB | 97.8 MiB | 1.28 GiB |

- [ ] Identify the retained heap growth: capture comparable post-GC histograms/heap dominators at
  bounded early/late checkpoints and distinguish live caches, instrumentation retention and obsolete
  compiler state. Growth repeats in both processes; this is not yet a plateau or a proven leak.
- [ ] Re-run the combined compiler/LSP/stdio/plugin gate and current full editor catalogs in a
  separate time-bounded checkpoint. The present checkpoint retains the 15 focused semantic/trace
  regression results and native lifetime receipts; it does not claim a new full-suite pass.
- [ ] Retain VS Code visible Cancel, UP16/UP17 repairs and supported-platform acceptance as open.

Native trust/real-backup validation is commit `9553844fd`, following driver implementation
`7364e0af8`. The final IntelliJ assertion opens and checks the reopened document without replaying
the earlier edit. Keep that assertion and this receipt with the editor acceptance/workload slice;
the semantic presentation fix and tracing instrumentation remain separate extraction commits.

Final focused rerun `run-11163643850362008619` passes START and START_PROJECTS in 38.3 seconds,
with zero IDE errors. Primary PID 68649 remains intact while closed PID 68659 retires; reopening
starts PID 68669 and reads the preserved document without rewriting it. Root and lang
`spotlessCheck` pass. No full editor playbook or combined backend suite was launched for this final
harness-only assertion change. No owned workload process remained after the shortened run.

### Functionality continuation: L64 import completion

First of four separate scope checkpoints, with validation batched after implementation.
Unqualified prefixes of at least two characters can offer public source or bundled types with
an eager name edit and disjoint `additionalTextEdits`. Up to eight deterministic candidates are
proved against the configured source graph; the selected replacement and imports must compile
completely and preserve known bindings. Discovery may add a proven import edge; an explicit
source graph is never changed by completion. Cancellation follows cursor analysis and the proof.
No embedding or AST API was added. Declaration-name, keyword/snippet contexts and the other
L64 exclusions remain open; this is not a whole-family completion claim.

Tests added: atomic bundled import, middle-token/CRLF/UTF-16 edits, ambiguous source choices,
private and undeclared-dependency refusals, unrelated-error/member-site refusals, protocol edits,
and cancellation before/during/after the second stage. Shared X105 and both drivers now also
accept bundled/source import completions. Their older receipts cover quick fixes only; the new
variants now have the selected editor receipts below.

### Functionality continuation: L65 redirect lookup

Implementation lookup now follows existing `FromInto` and capped/narrowing method metadata to
a written body, with method-instance cycle guards. It does not generate optimized forwarding
bodies or guess runtime delegate receivers. Real `manualTests` mixin and delegation modules are
loaded as standalone test modules; covariant self-return lookup and interface-valued delegation
have explicit source-location assertions. No AST/embedding API change. Validation was batched after the implementation checkpoints; see the receipt below.
Dynamic receivers, unsupported property forwarding and missing binary source remain boundaries.

### Functionality continuation: L62 composition families

Method families now copy written contracts from the same compiler dispatch traversal used for
call provenance, rather than treating raw capped/into identities as editable declarations. The
complete-graph before/after proof and binary/unknown-route refusals remain mandatory. Tests reuse
manual mixin and conditional-mixin programs for covariant family rename/reverse rename, unrelated
composition preservation and existing-default collision refusal. No compiler API addition. These
tests and the existing composition/refactoring regressions run in the combined gate.

### Functionality continuation: L63 literal extraction

A new `refactor.extract` action extracts an exactly selected integer, string or character literal
that is the sole expression of a return statement in a block. Java parser/token ranges establish
the selection and statement boundary. It inserts an inferred immutable local immediately before
that return, chooses a name absent from the source identifiers, retains indentation/line endings,
and requires a complete proposed graph with all existing bindings and dispatch preserved.
The source literal is retained exactly; calls, compound expressions, empty/partial selections,
expression bodies and same-line statements are outside this first boundary. No AST API/state was
added. General extract-local, extract-method, missing-declaration, inline and safe-delete remain
separate tasks requiring their own evaluation-order/capture/caller-closure proof.

Tests cover primitive literal forms, Unicode/CRLF, fresh-name collisions, unchanged disk sources,
unsupported expressions and broken known neighbors. New shared X148 and both editor drivers
exercise the action, exact resulting source, diagnostics and native Undo/Redo. Both clients pass
the selected acceptance recorded below.

The first combined gate exposed two real proof gaps in those manual modules: predefined `this`
registers lacked stable receiver identity, and conditional incorporation formals were mistaken for
new declarations. Receiver proof now derives class/access identity from existing registers;
conditional names refer to the incorporated mixin's registered property. This also repairs Go to
Definition on the conditional formal. No compiler/AST change or new mutable builder field.

Functionality checkpoint map (local, not pushed):

| Slice | Commits | Validated scope |
| --- | --- | --- |
| L64 imports | `4d511f9d2`, `a21c1ae20`; X105 portions of `83a2e5b02` | Atomic public source/bundled imports, exact replacement, explicit-graph/access/error refusals, cancellation and originating-compilation guards; both editors accept the new X105 variants. |
| L65 redirects | `54e657755`, `7c3a6eecc` | Real manual-module lookups plus direct FromInto/capped route assertions and existing lookup refusals. |
| L62 composition | `1343d71df`, `23d15a1ea` | Covariant/conditional manual-module renames, reverse edits, conditional-formal navigation and a genuine colliding-default refusal; wider rename/member-action regressions. |
| L63 literal extraction | `ef2bbb22e`; extraction/catalog portions of `83a2e5b02` | Eleven positive/refusal tests and X148 acceptance/Undo/Redo in both editors. |

`83a2e5b02` is a shared acceptance correction: retain its X105 expected-import-document changes
with L64, and its X148 ordering/catalog/selection tests and parser block-class correction with L63.
The first implementation commits alone are not independently validated PRs; extract each complete
slice with its corrections, then rerun its own gate.

The combined regression gate passed **188 tests, zero failures/errors/skips**, plus IntelliJ
integration-harness compilation and LSP/IntelliJ Spotless checks. A subsequent **34-test**
lookup/dispatch gate covers the FromInto fix: a composition entry can be abstract while its
constraint supplies a written body. The final **28-test** lifecycle/protocol gate covers completion
proof retirement by edit, close, configuration change and cancellation. All passed without skips.
VS Code TypeScript compilation and the ordered **153-case** shared/manual catalog audit pass.
These focused runs supersede the failed development attempts; they do not claim a new full suite.

Editor receipts:

- VS Code `run-mWdgTG`: X105 and X148 pass (5.4s and 0.5s case execution). This exercises the
  installed extension providers, application of additional edits, and native Undo/Redo.
- VS Code `run-Gw4RBe`: final X105 replay passes (5.2s), including exact expected documents
  with the originating-compilation guard in place. Zero failures or extension errors.
- IntelliJ `run-8199814129240125831`: X148 passes (4.5s). X105 exposed the harness's old assumption
  that completion only replaces the prefix. It actually replaces the full token and inserts imports.
- IntelliJ `run-4498968462723872053`: corrected X105 passes (9.6s), with zero IDE failures. Both
  drivers now share exact expected post-completion documents. The failed X105 receipt remains
  recorded and is superseded only for that case.

The four unintentionally deleted IntelliJ project files were restored from HEAD at the user's
request; those deletions are not part of any commit. No embedding/AST changes or new mutable
builder fields were needed for this batch. Broader declaration/snippet completion, dynamic or
unsupported composition routes and general semantic transformations remain on the task list;
none of L62–L65 is marked wholly complete. Performance/heap work remains deferred.


### L64 declaration names and syntax templates (2026-10-01)

The Kotlin compiler adapter now reads the existing Java parser's declaration tokens and block
ownership to offer syntax completions. Properties, parameters and explicit-type local declarations
with a written name token can suggest a lower-camel-case name from their written named type,
including qualified/generic type names and acronym word boundaries. A name already used anywhere
in the document receives a fresh numeric suffix. This deliberately conservative spelling check is
not a binding, symbol rename, or guarantee about inherited names. The whole selected identifier is
replaced, including a suffix after the caret. Inferred types, absent names and new method/type names
are not guessed.

File, type-body and statement boundaries have separate keyword sets. Six initial templates cover
module/class declarations, void methods, if blocks, while loops and return values. Templates are
limited to vacant slots; existing conditions/bodies/terminators are not duplicated. The Java lexer
excludes comments and literals; expression/type/argument/member positions cannot acquire statement
templates. Accessor/anonymous/lambda-specific template grammars remain excluded. These are labelled
syntax templates, not compiler-proven semantic edits; users fill their placeholders before compiling.

Snippet-capable clients receive tab stops; other clients receive literal source defaults. The server
negotiates completion insertion format and AsIs indentation, and provides relative body indentation
to clients that only support their default indentation adjustment. This follows the
[LSP completion contract](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/completion.md).
The templates are detached immutable completion values, built on the existing compiler worker and
retired with its cursor request. No compiler/embedding API, AST node, mutable field or clone hook was
added. Parsing and lexing remain covered by compiler API timing; source offsets are indexed once.

Shared X149/X150 and both drivers cover exact name replacement, collision suffixes, template
insertion, first/final snippet stops and insertion Undo. IntelliJ can first undo the caret movement
back to the selected placeholder; the driver allows only that text-preserving step before requiring
one Undo to restore the prefix. Backend checks cover syntax exclusions,
Unicode/line endings, cancellation, unchanged cached diagnostics and reduced-client negotiation.
Validation passed: the completion/presentation/cursor regression gate ran **142 tests**, followed by
**25 tests** for the final syntax, protocol and cancellation/boundary changes. IntelliJ capability
negotiation adds **4 passing tests**. All had zero failures/errors/skips. TypeScript compilation,
IntelliJ integration-harness compilation, LSP/IntelliJ Spotless and the ordered **155-case**
shared/manual catalog audit pass. This is focused validation, not a new full-suite run.

Editor receipts:

- VS Code `run-m80ahV`: X149 passes (1.8s); X150 exposed the driver's unconditional indentation
  adjustment. Applying the provider's snippet edit with its `keepWhitespace` policy fixes that.
  Final `run-rGXX4j`: X150 passes (1.8s), including exact final caret position, source and Undo.
  These checks use the installed extension provider and native snippet engine, not physical
  completion-popup selection.
- IntelliJ `run-9591212347662100514`: X149 passes (3.9s); X150 exposes **UP18**, LSP4IJ advertising
  AsIs insertion but adjusting snippet indentation anyway. The plugin now advertises only its
  implemented AdjustIndentation mode; the server supplies relative template indentation.
- IntelliJ `run-10874571275660252562`: X150 passes (4.1s), with zero IDE failures. This uses native
  completion selection, live-template navigation and Undo. Intermediate failures came from using
  EditorTab instead of the template-navigation action and expecting caret movement to be
  undo-transparent; both driver assumptions are corrected without changing IDE settings.

UP18 has a searchable `TODO LSP4IJ:` and an explicit removal gate in errs-upstream-issues.md.
The new cases are accepted; the whole L64 family remains open. Remaining work includes missing
declaration-name recovery, arbitrary enclosing-instance enumeration, additional callable/literal
forms and damaged recursive-bound contexts.


### L64 empty declaration-name recovery (2026-10-01)

Continuation of the declaration/template checkpoint `5826d831e`. Explicit cursor parsing now retains
an absent name after a complete named type in a property, method/ordinary-constructor parameter or
primary-constructor parameter. Qualified and parameterized named types retain their final type
spelling. Slots before initializers, commas, closing delimiters and EOF preserve the full original
source and following declarations; EOF ranges include whitespace up to the cursor.

The existing `partial.IncompleteStatement` owns the written type child and cursor position. Its new
factory/accessor derive the missing-name classification without adding fields, a fabricated name
token or a clone-reset rule. Existing incomplete declaration/type nodes skip semantic binding for
these sites. Their parameters, signatures and properties remain unregistered and cannot emit code.
The Kotlin snapshot copies a distinct DECLARATION_NAME kind and type spelling; the existing syntax
proposal policy chooses a name and an empty-range edit. This does not claim type resolution, rename
support or an inferred type. No source repair/reparse or host-side grammar is introduced.

Ordinary parsing continues to report the missing name. Error budgets, cancellation and parser
speculation keep their existing behavior. Name suggestions reuse the conservative document-wide
collision check from `5826d831e`. Existing written names, comments/literals, missing type delimiters
and ambiguous bare local expressions do not acquire an empty declaration slot. Empty names after
compound/decorated/inferred types and missing method/type declaration names remain follow-ups.

Tests cover ordinary/partial parsing, original source and sibling retention, independent clone
ownership, error-listener stopping, non-emission, exact UTF-16/CRLF insertion coordinates, unchanged
cached diagnostics and successful compilation after accepting supported suggestions. Shared X151
covers collision/property, ordinary parameter and generic primary-constructor cases in both editors,
including exact source and native Undo.

Validation passes: **42 Java parser tests and 148 LSP adapter/protocol tests**, zero failures/errors/
skips. Root and LSP/IntelliJ Spotless, IntelliJ integration-harness compilation, TypeScript
compilation and the ordered **156-case** shared/manual catalog audit also pass. Selected X151 passes
in VS Code `run-oIIFhk` (2.3s, zero recorded extension errors) and IntelliJ
`run-13747944655313090541` (4.9s after START, zero IDE failures). The VS Code driver uses the installed
completion provider and editor edit/Undo; IntelliJ uses native completion acceptance. No full
playbook rerun is claimed. The slice needed no corrections after its first combined test run.

Future extraction: keep the parser and three `ast.partial` changes together with the Java recovery
tests; layer the copied Kotlin syntax fact, completion policy and protocol tests on top. The shared
scenario, both driver registrations and catalog/manual updates travel with that adapter slice.


### L64 real-source completion continuation (2026-10-01)

These local checkpoints remain together on errs:

| Commit | Scope for future extraction |
| --- | --- |
| `5826d831e` | Kotlin syntax names/templates, negotiated snippets, shared X149/X150 and the UP18 client constraint |
| `fb0925569` | Parser-owned empty property/parameter name slots, partial syntax API and shared X151 |
| `ea722b18b` | Wrapped names, literal/enclosing-instance arguments, recursive bounds, contextual templates, platform anonymous-body recovery and expanded shared acceptance |

The third commit builds on the first two. Its compiler/API, adapter and editor-test portions must
be extracted in the groups below and validated independently; the integrated commit is not a
claim that every portion is independently cherry-pickable. No remote operation was requested
for this checkpoint.

- Wrapped declaration-name types retain nullable, array, immutable, annotation, function and
  compound syntax without registering an absent name. Kotlin proposes `stringArray`, `fn` or
  `value` where appropriate and keeps existing collision/UTF-16 rules. No new mutable AST fields.
- Eleven templates cover the original six plus interface/service declarations, for ranges,
  do loops and try/catch. Anonymous member bodies and block lambdas use their actual grammar;
  break/continue respect intervening callable boundaries. Templates are syntax, not semantic edits.
- Argument proposals include `0.0`, a space character, `#00`, empty arrays/maps/tuples, plus the
  original five values. Fitting uses actual compiler conversions and validates the entire call.
  A proposal token carries its immutable spelling because a zero-width source span is empty;
  that fixes an existing numeric-conversion gap. Empty map trials resolve their written Map type
  through normal staging. Tests also compile every accepted proposal.
- Lexical enclosing instances (`this`, `this.Owner`, and qualified `this.Ow` completion) use the
  same argument fitter. Static boundaries, incompatible arguments and inaccessible receivers
  refuse proposals. Immutable `argumentExpressions` keep these facts separate from literals;
  old constructors remain, while CursorBinding/CallFacts record patterns gain a component.
- Wrapped and compound recursive bounds inspect every operand without manufacturing a formal
  identity. A cursor inside its own guarded bound retains the complete written bound and tests
  a disposable candidate substitution. Direct cycles and unknown/inaccessible operands remain
  rejected. Written/incomplete constraints are labelled as such.
- A checked-in copy of platform's CircularBuffer class (`b8be627`) exposed two real gaps:
  member cursors before operators/ternary conditions, and anonymous bodies invisible to initial
  partial inspection. Read-only deferred-body access and source-owned anonymous validation keep
  the real cursor and enclosing receiver. Incomplete body methods/constructions cannot emit.
  Existing capture analysis for complete programs is unchanged; no new mutable node field exists.
  Indexed, escaped, returned and narrowed callable values have dedicated regression coverage.

Shared acceptance now contains 157 ordered cases. X97 has 30 variants, X108 has 17, X150 has six,
X151 has seven, and new X152 has three enclosing-instance/indexed-function variants. Both drivers
consume these rows, including exact post-edit source, diagnostics, snippet stops where applicable
and native Undo.

The backend gate passes **65 Java tests and 410 LSP tests**, with zero failures/errors/skips.
Root and LSP/IntelliJ Spotless checks, IntelliJ integration-harness compilation and TypeScript
compilation pass. The ordered shared/manual catalog has 157 cases, with hash
`57e076c1566b84453531ae434bd699fb4c61d9052a5bdc03b9eac7f61efa466e`.
VS Code `run-C2gpLR` passes all five selected cases (63 variants, approximately 40 seconds),
with zero recorded extension errors. It uses installed providers, editor edits, the native snippet
engine and Undo; this is not physical completion-popup selection. IntelliJ
`run-4606554214556777779` stopped during X97 after source text changed during popup inspection;
the harness refused to replay the action. The user reported typing in the focused test window.
Replacement IntelliJ `run-16005944962074733631` passes START and all five selected cases
(63 variants, approximately 68 seconds after startup), with zero IDE failures. It exercises
native completion acceptance, parameter hints, snippet stops and Undo. No full playbook rerun
is claimed.

L64 remains explicitly bounded: ambiguous empty local declarations (`value ;`, `String ;`),
name inference from `val`/`var`, naming a new method/type outside a template, arbitrary nested
literal/lambda synthesis, unguarded/unknown recursive constraints and repair of unrelated syntax
errors are not advertised. Qualified enclosing-instance *argument* proposals do not claim a new
general-purpose enumeration of every special `this` spelling outside calls. The implementation
list must retain any requested expansion of these boundaries instead of treating an empty result
as tested support.

Extraction: keep the parser/partial-node and copied Kotlin naming changes together; keep the
literal spelling token, fitting and ordinary-compilation checks together; keep CursorBinding's
expression component with compiler/adapter producers and compatibility tests; keep deferred-body
access, actual-body validation and the platform fixture together. Shared scenario/driver and
manual/capability changes accompany the respective feature slices. Each extracted PR still needs
its own independent gate.


### L64 closure batch: ordinary enclosing expressions

The compiler now probes lexical enclosing instances at ordinary value cursors, including returns,
initializers and operators. Normal name validation and the actual required type decide availability;
static boundaries and invalid receivers refuse proposals. The argument fitter reuses the same
spellings and proposal construction. `CursorBinding.enclosingExpressions` is an immutable list
separate from whole-call argument facts; existing constructors remain and record patterns add the
new component. No mutable AST state is introduced. X152 gains two native ordinary-expression
variants. Regression and editor execution are deferred to the combined closure-batch gate.


### L64 closure batch: local declaration names

Existing named local declarations now derive name suggestions from literal or construction
initializer syntax for `val`/`var`. Missing inferred names before `=` retain their real initializer
in `partial.IncompleteLocalDeclaration`, with final syntax children and independent cloning.
The node cannot declare a register, infer a semantic type or emit code. The Kotlin naming policy
uses written clues (text/number/flag or the constructor type); arbitrary calls and Null provide
no useful clue. Bare assignments remain assignments, and ambiguous empty typed locals remain
refused. X151 adds literal and constructor initializer cases; combined validation follows.


### L64 closure batch: expected-type value templates

Function-typed parameters supply possible lambda arities. Disposable lambda ASTs with inferred
parameter names and an explicit TODO() body must pass the same whole-call fit/argument validation
as other insertion proposals, including named slots, constructors and function values. Kotlin
presents accepted spellings as snippets with a selected TODO() body and a plain-text fallback.
No function-type parsing or semantic inference is duplicated in the host. Empty collection values
already use expected-type fitting; arbitrary nested element generation is excluded. X150 gains
a lambda placeholder/Undo case in both editors. Combined execution follows the closure audit.


### L64 closure audit and acceptance gate

Implementation checkpoints: `b3159f7c6` (ordinary enclosing values), `965531afb` (local naming),
`c2ff62441` (fitted lambda templates). Keep later validation corrections with their corresponding
slices. None of these intermediate commits has yet passed independently.

| Original L64 requirement | Implementation / acceptance | Explicit boundary |
| --- | --- | --- |
| Recursive bounds | Written guarded constraints and selected self-bound leaves; X108 | Unknown/inaccessible names, direct cycles and unrelated syntax damage refuse completion |
| Value synthesis | Compiler-fitted scalar/empty collection literals; X97; lambda templates in X150 | No arbitrary nested values, captured body generation or runtime execution |
| Enclosing instances | Ordinary and whole-call proposals use compiler validation; X152 | Static boundaries and incompatible expected types refuse proposals |
| Declaration names | Written types and literal/construction clues for inferred locals; X149/X151 | Bare ambiguous typed locals and missing names without useful initializer clues remain conservative; new methods/types use templates |
| Keywords/templates | Parser-owned contexts, callable/loop boundaries and negotiated snippets; X150 | No text-only grammar reconstruction |
| Callable/signature breadth | Escaped/indexed/returned/narrowed functions, constructors and named/default arguments; backend and X97/X152 | Unresolved runtime identity is not a statically selected target |
| Documentation/ranking | Copied documentation, stable ordering, overload-specific active parameters; X97 | No claim that an incomplete overload has been selected |
| Import edits/client formats | X105 atomic imports; exact ranges and minimal/rich-client protocol tests | Version/access/graph proof refusals remain |

The platform CircularBuffer fixture now also exercises the real nullable callback parameter for
a lambda argument. Shared X150 has seven variants, X151 ten and X152 five. Both drivers assert
exact insertion, diagnostics, placeholder selection/final stop where applicable, and Undo.
Protocol tests separately prove plain-text fallback without snippet-marker leakage. Final validation
is pending; the top-level L64 checkbox is not closed by this pre-validation inventory.

L64 backend gate passes **68 Java tests and 418 LSP tests**, zero failures/errors/skips;
TypeScript and IntelliJ integration-driver compilation also pass. Validation corrected the new
partial node's required dump method, a parser test that assumed parent links before adoption,
and optional shared-scenario fields in TypeScript. These corrections accompany the implementation
commits on extraction. Full GUI acceptance is deferred until the requested L65 follow-up and its
shared scenarios are complete.

### L65 closure: written implementation routes and classification

Accessor lookup now follows the existing method redirect walker for capped, delegating and into
entries, without asking optimization to generate forwarding bodies. Explicit/native/synthetic
methods are checked for a real written executable body. Conditional-mixin invocation, covariant
property getters and runtime-only delegation have shared X153 fixtures and native navigation
checks. X154 checks destructuring writes, incremented properties and indexed receiver/index reads,
including native semantic-token consumption and occurrence highlighting in IntelliJ.

Binary-source overload selection has a directly tested pure helper: a unique namespace can resolve
without debug lines, while overloads require exactly one matching source span. Missing, overlapping
or unmatched spans return no target. Bundled-source identity/revision ownership and read-only
behavior are unchanged. The source/dispatch tests exercise real compiler chains; native/runtime-only
bodies remain explicit refusals.

Validation exposed a real conditional-instantiation gap: inspecting only `Box<T>` omitted the
body activated on `Box<String>`. The Kotlin worker now also inspects validated expression types
owned by source classes, deduplicated with their formal declarations. TypeInfo still supplies the
actual chains; no mixin adoption, receiver type or target is guessed, and no AST API was added.
The L65 gate passes **78 tests**, zero failures/errors/skips; both driver compilations and root/
LSP/IntelliJ Spotless checks pass. The new shared catalog has **159 cases** with SHA-256
`37a901c8879ab18e63e8ce5322bbdeb8f2c762e409585deb16fe790f911087b9`.
Keep `23d439232` together with this validation/fix checkpoint when extracting L65. Full native
acceptance follows this checkpoint; it is not yet a passing receipt.


### L64/L65 broad-gate corrections (2026-10-02)

The first full LSP gate ran 1,626 tests and found three failures; it prevented either GUI suite
from starting. Three pre-existing disabled Tree-sitter tests are separate from those failures.
The corrections are:

- Exclude a bare formal type parameter from concrete-source implementation inspection. Its
  underlying constraint is a class, but adding private accessibility to the formal itself produces
  VERIFY-29. Parameterized source classes still use their validated substitutions.
- Deduplicate incomplete sites by AST identity before and after compiler validation. A primary
  constructor's written default and generated property getter can expose the same cursor twice.
  Publication previously made the single-cursor adapter refuse completion. No new AST state or
  clone-selection policy is needed; the original identity and its binding are preserved.
- Replace an obsolete rejection expectation for mid-token member completion with exact whole-name
  replacement assertions, including every overload of the matching method.

The primary-constructor default is now the fourth shared X87 variant in both editor drivers.
The regression checks original source text, one bound surviving cursor, no method emission,
completion and unchanged cached compilation. This is an embedding publication correction, so keep
it with the L64 surviving-cursor/recovery slice rather than the unrelated L65 dispatch changes.
The correction gate passes **61 Java tests and 105 LSP tests**, zero failures/errors/skips;
both editor drivers compile. L65's formal-type correction is `474bad3d5`; it belongs with
`23d439232` and `bf2a46e14` on extraction. The updated 159-case shared catalog SHA-256 is
`e82a13e5ec307a6613c2dd465ba2c9d8e49951fb19cedc8f31178bb4a41af808`.
Full GUI acceptance follows; no passing full-current-revision backend receipt is implied by the
focused correction gate.


### L64/L65 full editor acceptance (2026-10-02)

- VS Code `run-DVOyyh` stopped before cases: the new semantic cases were registered beside older
  related cases instead of in shared catalog order. Registration was split without duplicating
  assertions or relaxing the order guard.
- Full VS Code `run-qsTyyb`: **157/159 passed**, zero extension errors. X130 reproduced UP16
  after Move/Undo/Redo/resource assertions. X152 exposed literal backslash-n sequences in the two
  new ordinary-enclosing fixtures. X87, X150/X151 and X153/X154 all passed.
- Shared X152 sources now contain real newlines. A backend test consumes all five actual shared
  variants, asserts exact edits and compiles each accepted result; all five enclosing-value tests
  pass. The first selected rerun `run-uvN3g8` failed native Undo focus. The driver now explicitly
  selects the tested document before its one Undo command; it never replays an edit or Undo.
- VS Code `run-nQpXCs`: **X152 passed**, all five variants, zero extension errors. Across the full
  and selected receipts, **158/159** pass; X130 remains failed. This is not a single clean full run.
- Full IntelliJ `run-8615971651239916856`: **78 scenarios plus START passed**, X105 failed and
  80 cases were not reached; zero IDE errors. The trace returned the correct Document import
  action in 324 ms, but the native intention list never appeared. Do not classify this as a
  compiler action failure or dismiss it as harmless.
- Isolated IntelliJ `run-3538437822704380758`: START and X105 pass, zero IDE errors.
- IntelliJ continuation `run-6888630689085749199`: **81 selected scenarios plus START pass**,
  zero IDE errors, covering X105 again and every case not reached in the full attempt. The union
  contains **all 159 shared scenarios plus START**. No completed edit/rename was replayed in place;
  the continuation used a fresh isolated workspace. This is complete combined coverage, not a
  single uninterrupted full pass.

The JNA warning comes from the Starter test JVM's intentional native integration. The GUI test task
now explicitly enables native access (`ad5e0b2ce`, a separate harness configuration slice). A real X105 run with `--info` shows the JVM option, no
restricted-native warning and successful configuration-cache storage. The continuation also has
no warning. The IDE/server JVM policies and dependency versions are unchanged.

L64/L65's bounded implementation and shared acceptance scope is complete. Remaining acceptance work:
- [ ] UP16: repair/report the independent VS Code Explorer Cut cleanup defect; X130 stays failed
  whenever it reproduces.
- [ ] Reproduce the full-run IntelliJ X105 intention-popup timeout with its preceding UI state.
  Two selected passes do not establish that the intermittent host/harness behavior is fixed.
- [ ] Obtain one uninterrupted full IntelliJ receipt after that stability work. The combined
  receipts above already cover every case, but do not establish a single clean run.

The current shared catalog still has 159 cases; its SHA-256 is
`76d95695cf5a515d9122cd7b38f0fb4da4a7704560ef2fec99c311fde0878b0a`.
Keep the fixture, registration and native Undo-focus corrections with their L64/L65 acceptance
slices when extracting PRs. The cursor-identity correction is `162d2f8d1`; the formal-type fix is
`474bad3d5`. Neither the integration branch nor these receipts establish independently green
extracted PRs.


### Depth-first functionality continuation (2026-10-02)

Requested order: L62, L63, L66, then L67, with reviewable implementation commits and batched
validation. A compiler or editor boundary is not closed merely by adding a scenario or documenting
its refusal. The existing X105 popup stability, VS Code X130 host failure and L82 release gates
remain separate from functionality implementation.

- [x] L62 concrete composition inspection: reuse the successful-source type enumeration from L65
  for method and property rename relations. Conditional bodies present only after validated generic
  substitution now enter the complete-graph proof. The helper remains in the Kotlin compiler
  adapter; no AST fields or Java API are added.
- [x] Write method/property fact regressions, declaration/call-site rename and reverse rename,
  concrete-host collision refusal, and shared X155 in both drivers.
- [x] Run the new L62 regressions with existing rename, implementation and semantic action tests: 122 tests pass, zero failures/errors/skips; both editor drivers compile.
- [x] Validate the L62 composition and workspace boundary audit below; broader resource relocation
  and unsupported dispatch identities remain separate implementation work.
- [x] L63 whole-return-expression extraction and adjacent single-use typed returned-local inline,
  with relocation/type/binding proof and positive/refusal tests.
- [x] L63 complete typed-initializer extraction, adjacent same-written-type initializer inline
  and compiler-proven unused constant local removal; October 4 validation below.
- [x] L63 private same-owner expression helpers with stable inputs and explicit parameter/call proof;
  X181–X184 and the current batch acceptance below record the boundary.
- [ ] L63 remaining transformations: wider expression/statement extraction, mutable captures, broader
  inline, global safe delete and missing declarations; each needs its own semantic proof.
- [x] L66 resolved module/type import source links and explicit lexical alias linked editing.
- [x] L66 damaged structure, wildcard links, conditional-source refusal, lexical lambda/same-name
  aliases and bounded formatter rules; October 5 closure and acceptance below.
- [x] L67 bounded source/binary replacement and graph/index lifecycle regression; fix lost host
  binary source URIs and lambda facts. Twenty backend tests pass.
- [x] L67 scale closure: measured module-level navigation reuse and bounded compiler ownership;
  October 5 receipt below records the decision against speculative disk persistence.

The shared catalog now contains 163 cases (X1–X158 and five existing CFG/warning cases).
Earlier 159-case receipts remain historical evidence; X155–X158 now pass in both editors (receipt below).


L62 validation correction (October 2): formal interface declarations require
`getSingleUnderlyingClass(true)` when identifying their owner; the first concrete-inspection
batch mistakenly used the class-only variant. Existing interface rename/member-action regressions
caught it. After correction, all **122** selected compiler-adapter tests pass with zero
failures/errors/skips. IntelliJ integration-driver compilation and VS Code TypeScript compilation
also pass. No editor UI run is claimed yet.

The same audit enables written, non-synthetic `SansCode` method contracts in dispatch provenance:
a class method without a body is a valid source declaration even though implementation lookup
must not fabricate executable code for it. Real compiler tests verify that distinction and rename
its override family. X155 now combines a bodyless generic host contract with a conditionally adopted
implementation. Generated/implicit accessor identities, union targets and native/binary contracts
retain their separate proof/refusal policies. Extract these corrections with `8630dde4b`.

`72bfc3b9c` independently adds scenario descriptions beside IDs in both editor progress displays,
with complete tooltip text and unchanged ID-based selection. It belongs to playbook infrastructure.


### L63 returned-expression extraction (2026-10-02)

`XdkLocalExtraction` extends the literal-only action to exactly selected complete return
expressions in statement blocks. For calls, compound expressions and closures it copies the
method's written single return type into the new local declaration, preserving contextual typing.
The expression remains exact source text and is evaluated once, immediately before the original
return. Conditional/multiple returns, inferred lambda-body return types, partial expressions,
expression-bodied methods and same-line siblings remain refused.

`XdkRename.Plan` can describe exact text relocated into an insertion. Binding proof translates
references, declarations and call sites through that relocation before comparing them with the
recompiled graph. It verifies that the inserted substring is exactly the old substring. This is
necessary because an unchanged expression can bind to different names after a move; a dedicated
compiler test moves the same text into another method's parameter scope and requires refusal even
though both programs compile. No compiler/AST API or mutable AST state is added.

New tests cover calls/compound values, contextual numeric/function types, repeated parameter
references, captures, short-circuit expressions and rejected conditional/partial selections.
Shared X156 in both drivers checks exact applied source, diagnostics and Undo/Redo. L63 remains
open for other expression contexts, extract method, missing declarations, inline and safe delete.
Compilation/execution of this continuation is pending; the earlier 122-test L62 receipt does not
validate these changes.

### L63 adjacent returned-local inline (2026-10-02)

A typed local immediately followed by its sole `return local` use can now be inlined. The written
local and return types must match; the initializer keeps its exact text, evaluation count and order.
The proposed complete graph must preserve every binding and dispatch chain except the deliberately
removed declaration/type/read sites. The relocation proof handles replacement destinations as well
as extraction insertions. Repeated reads, intervening writes/statements, comments, same-line returns,
inferred locals and different expected types are refused. No compiler AST API or mutable AST state
was added. General inline, extract method, missing declarations and safe delete remain open.

Validation: 31 real compiler tests passed, zero failures/errors/skips: 16 extraction, 14 inline and
one adversarial relocation/capture proof. Both editor drivers compile. Shared X157 checks the actual
action, exact source, diagnostics and Undo/Redo/Undo in both editors; X155–X157 editor execution is
still pending. This also validates returned-expression extraction commit `3a4c03fd9`.

### L66 resolved import sources and lexical alias editing (2026-10-02)

The worker now copies resolved source destinations for package/module imports and non-wildcard,
non-conditional explicit imports. XdkAdapter returns those links only for matching source text;
missing/binary-only declarations cannot acquire a guessed path. Matching bundled sources remain
read-only. Explicit import aliases link only the uses already proven to belong to that lexical
import, including separately shadowed alias names. No Java AST accessor or mutable field was added.
Shared X158 resolves links, checks exact ranges/alias uses and opens the target source in both
editor drivers. Wildcard/conditional links, broader linked scopes and formatter wrapping/layout
remain open. Validation: 15 source-link/editing/import completion tests pass, zero failures/errors/skips; both editor drivers compile. X158 editor execution is pending.

### L67 detached graph retention and replacement audit (2026-10-02)

The graph-view join dropped `lambdas` when reconstructing each immutable SemanticModel. It now
preserves that per-document list alongside imports, source links, expressions and function calls.
A real compiler snapshot regression checks every one of those copied fields. No shared compiler
objects or new mutable cache state were introduced.

The replacement regression walks one live adapter through indexed binary source v1, source index
v2, binary-only metadata, explicit source authority for the same module, and an empty graph. It
checks fresh definition destinations and refuses old call-hierarchy handles at every transition.
It exposed a real gap: unopened workspace navigation discarded host-supplied binary source URIs,
although navigation from a compiled editor snapshot retained them. The graph view now captures
the same indexed source mapping; binary-only artifacts still expose no guessed location.
Existing dependency, live graph and closed-file navigation tests accompany it: 20 tests passed,
zero failures/errors/skips. Large-graph budgets, avoiding redundant cold graph
compilation and the evidence-based choice of incremental/persistent indexing remained open at this
checkpoint; the October 5 L67 closure below records the measured resolution.

Extraction map for this continuation:

| Slice | Local commits | Validation boundary |
|---|---|---|
| L62 concrete conditional/bodyless families | `8630dde4b` + `83f7fb434` | Keep together; the latter fixes interface-owner and SansCode handling. 122 backend tests passed. |
| IDE scenario descriptions | `72bfc3b9c` | Both drivers compile; X IDs/filtering stay unchanged. |
| L63 returned-expression extraction and inline | `3a4c03fd9` + `c35a7c031` | Shared relocation proof; 31 backend tests passed. X156/X157 editor runs pending. |
| L66 import sources and lexical aliases | `eb045abec` | 15 backend tests passed, both drivers compile. X158 editor run pending. |
| L67 snapshot join/replacement | `e1c6731b8` | Dedicated regression; include the L66 source-link field when extracting together. |

### L62/L63/L66/L67 continuation acceptance (2026-10-02)

Implementation checkpoint `e1c6731b8` passes the combined 204-test LSP regression gate: 20 suites,
zero failures/errors/skips. This includes dispatch/rename, extraction/inline/capture proof,
import/source links, detached semantic models and graph/dependency replacement. LSP and IntelliJ
Spotless checks pass. Both editor drivers compile.

Both selected editor runs use the same 163-case catalog, SHA-256
`299ebd0d214e35f7f9ff14bbb8de7e9c0203481c9ed4e14187819930d039addc`:

- VS Code `run-Um9auo`: X155–X158 all pass, no selected failures. The interrupted earlier
  `run-A634WV` has no results file and is not counted as evidence.
- IntelliJ `run-13592235693442712133`: START and X155–X158 all pass, no IDE errors;
  JUnit reports one passing suite test without skips. IDEA 2026.2.3, LSP4IJ 0.21.0,
  Ultimate features disabled.

X155 proves conditional/bodyless rename plus Undo; X156 and X157 apply extraction/inline and
check exact source, clear diagnostics and Undo/Redo/Undo; X158 resolves import links, checks lexical
alias ranges and opens the target source. Both running harnesses now use shared scenario titles
in progress text/tooltips while preserving X IDs and selection syntax. Pixel-level appearance and
modifier-click presentation remain manual checks, not claims made from provider assertions.

This is selected new-feature acceptance, not another full 163-case run. The existing X105 popup
stability, VS Code X130 upstream failure and L82 release gates remain open. The checklist above
continues to distinguish the completed bounded slices from broader L62/L63/L66/L67 scope.

### L62 composition and workspace boundary audit (2026-10-02)

This checkpoint tests the remaining composition questions against the existing proof instead of
assuming a missing playbook case implies missing compiler support. No production or Java AST change
has been needed for the cases below. All 78 tests across 11 audit suites pass with zero
failures/errors/skips; both editor drivers compile and LSP/IntelliJ Spotless checks pass.
Shared X159/X160 pass in both editors; the receipt below retains the first blocked IntelliJ attempt.

| Boundary | Implementation and evidence |
|---|---|
| Conditional member without a formal host declaration | New `XdkConditionalRenameTest` covers methods and properties from a concrete receiver, both in one module and instantiated only in a closed consumer. The dependency source index preserves the selected written mixin identity; a synthetic host declaration is unnecessary. Shared X159 adds cross-file rename and Undo. |
| Mutually exclusive conditional families | New regression renames the String-constrained method while preserving the same-named Number-constrained method and its call. Joining by spelling or generic host alone would be incorrect. |
| Conditional composition collision | New regression requires refusal when renaming one interface method would merge distinct contracts in a conditional composition. It separately compiles the proposed graph, so compilation success cannot stand in for dispatch equivalence. |
| Nested generic delegation | New `XdkDelegatedRenameTest` covers methods and properties through two delegate layers across source modules. All written interface/implementation declarations and uses join the family. Shared X160 adds closed-consumer property rename and Undo. |
| Capped, into, default, bodyless, annotated and ordinary delegate routes | Existing `CompilerDispatchRoutesTest`, `XdkManualCompositionRenameTest`, `XdkResourceRenameTest` and `XdkParameterRenameTest` remain in the audit gate. Written identities come from compiler metadata, never method names. |
| Constructor and primary property parameters | Existing parameter/primary-parameter/resource regressions cover named labels and type uses. `construct` itself is a keyword, not a user-renameable method name. |
| External consumers and current source membership | `XdkExternalRenameTest` covers configured external roots, unsaved consumers, missing registered roots, intervening disk edits and discovery scope receipts. `XdkRenameBoundaryTest` confirms that omitted consumers remain outside the declared graph. |
| Read-only and unsupported identities | Binary/XDK contracts remain read-only. The October 3 continuation below proves bounded union calls and recursive written contracts; generated accessor/runtime/native bodies still do not independently establish editable written callable contracts. |
| Resource operations | Existing file/resource tests cover companion directories, implicit packages, discovered container relocation, collisions, symlinks and overlapping operations. Explicit root relocation and moves that change package qualification remain open. |

Remaining implementation work in L62:

- [x] Bounded cross-package type/file moves: derive old/new ownership from compiler identities,
  rewrite qualified references/imports, and prove the complete proposed graph. Preserve companion
  sources/resources, aliases, closed consumers and old-package sibling type references.
- [x] Accept shared X161 Move/Undo/Redo in both editors; see the implementation receipt below.
- [x] Implement simultaneous type/module rename plus move and interacting type qualifications
  against the complete requested batch; see the combined-relocation continuation for validation.
- [x] Extend relocation through captured empty package directories and preserve comments/whitespace
  in qualified names. Compiler replay confirms every destination identity, including unused types.
  X173–X176 pass selected acceptance in both editors; see the October 4 receipt below.
- [ ] Design final ownership for cross-module type moves and overlapping parent/child/companion
  operations. Uncaptured or nonexistent destination directories remain refused.
- [x] Implement explicit graph relocation through `xtc/renameFiles`, with source/resource roots,
  version checks and persisted editor Undo/Redo (X162/X163). Selected acceptance is recorded below.
  Standard `willRenameFiles` still refuses a host configuration change.
- [x] Represent bounded union alternatives and recursive written contracts at each callable site,
  including receiver/delegate provenance and finite back edges. Validation is recorded in the
  October 3 continuation below. Generated constructors remain construction identities, not new
  editable method declarations.
- [x] Extend callable proof to bounded parameterized/formal/annotated union operands and union
  dispatch nested under generated delegation. Preserve substitutions and every alternative; do not
  infer runtime-only implementations. See the substituted-receiver receipt below.
- [ ] Characterize unsupported annotation constant kinds, dependent/dynamic type shapes and
  annotations around whole relational types before extending their proof. Wider escaped-value and
  generated accessor/constructor transformations remain separate audits.

Omitted consumers are an explicit project configuration boundary, not a feature that can be
completed by guessing other repositories. Binary contracts remain read-only by design. The audit
does not close the broader L62 task or the existing editor stability/release gates.

Validation and extraction receipt for `30fa27215`:

- Backend: **78 tests across 11 suites**, zero failures/errors/skips. Both editor drivers compile;
  LSP/IntelliJ Spotless checks pass.
- VS Code `run-RGeVCM`: **X159/X160 pass**, zero extension errors (1.8 s and 1.2 s).
- IntelliJ `run-14936839178671622673`: START passed, X159 failed waiting for its native Rename
  dialog, and X160 was not reached. The saved screenshot shows macOS local-network permission
  dialogs covering the editor despite Java reporting editor focus. No edit had been submitted.
- Fresh IntelliJ `run-17726972701009018455`: **START/X159/X160 pass**, zero recorded IDE errors;
  X159 took 3.0 s and X160 1.8 s. The system prompt did not recur and no permission choice was made.
  JUnit confirms one passing suite test without failures/errors/skips.
- Both editors used the same **165-case** catalog, SHA-256
  `a4f584905f27342771bcc73ce553c14cd3260e8ab6ef51fbcc8baa9c6cdcb5e1`.

Keep the tests, shared scenarios and driver registration together when extracting this L62 audit
commit. It depends on the earlier conditional/bodyless proof corrections; it adds coverage, not
new compiler API. These are selected acceptance runs, not a new full-catalog receipt.


### L62 cross-package type relocation (2026-10-02)

A same-name class file can move between compiler-proven package namespaces within its source
module, including back to the module namespace. The existing `willRenameFiles` pipeline now
rewrites qualified type names, import clauses, constructor/static calls and old-package sibling
type references in the moved source tree. Explicit aliases retain their written names. Closed
configured consumers participate; omitted consumers remain outside the graph. The companion
directory moves with the type, including nested source files and resources.

`CompilerTypeNames` reads existing Java AST accessors while the attempt owns its constant pool.
The retained records contain source spans, strings and detached proof identities only. No Java
AST accessor, field, clone hook or embedding API is added. `XdkTypeMoves` uses compiler namespace
ownership to plan prefix edits. The proof may discard only static namespace/type occurrences
inside those explicit prefix edits; all retained type/member bindings, constructor and method
calls, import targets and dispatch chains must match the before graph after source remapping.
Successful compilation alone is insufficient: a regression moves a class between packages whose
same-named static methods both compile, and requires refusal when its bare call changes target.

Both existing editor paths consume the same result; no additional production editor move protocol
is needed. IntelliJ preflights its registered native Move action before applying a global undo
command. VS Code's existing file-operation participation adds the compiler edit to the host's
workspace move. A null LSP pre-operation reply still cannot veto arbitrary VS Code file moves.
Shared X161 checks an explicit two-module graph, a closed consumer, an explicit import alias,
constructor/static calls, old-package sibling references, companion sources/resources, Undo and
Redo. Both editor drivers pass selected acceptance.

Backend validation: 66 tests across nine suites pass without failures/errors/skips; both editor
drivers compile and LSP/IntelliJ formatting checks pass. Two initial edge-case fixtures failed
before any move because they omitted required `static` modifiers; corrected fixtures pass.
The final 66-test gate also covers module aliases when selecting the destination namespace.
The broader rename/proof gate passes **118 tests across 17 suites**, zero failures/errors/skips,
plus root/LSP/IntelliJ Spotless checks. It exposed an over-broad import comparison: organize imports
intentionally removes unused clauses, so `2ad97134f` restricts import-target preservation to file
relocation. The existing `XdkRenameServerTest` caught the regression and now passes. The editor
receipts below precede this correction; their relocation path and results are unchanged.

Limits remain explicit: this slice preserves the file/type basename and module. It requires a
compiler-proven destination namespace and refuses existing destination/companion collisions,
unsupported qualification syntax and conflicting edit plans. Proposed compilation checks access,
imports and resources; proof checks identities even when the proposed graph compiles. Explicit
source-graph relocation is implemented in the following checkpoint; union/generated/cyclic
callable target-set proof remains separate L62 work. This is not a declaration that L62 or general Move refactoring is complete.


Selected editor acceptance and extraction map:

- `03c4494a1` + `2ad97134f`: detached compiler type/namespace facts, cross-package move planning, narrow
  qualification/import binding proof, companion relocation and backend regressions. This slice
  belongs with the graph proof/file-operation backend work; it adds no Java API dependency.
- `a1f5b4097`: shared X161, both drivers, catalog registration and the manual playbook row.
  Keep the catalog's ASCII escape restoration in the following receipt commit with this change;
  it removes serialization-only noise and changes no scenario values.
- VS Code 1.140.0 `run-KieFUI`: **X161 passes** in 2,460 ms; zero extension errors. This is native
  workspace file-operation participation and editor Undo/Redo, not an Explorer Cut/Paste run and
  not evidence that the separate X130/UP16 Explorer issue is resolved.
- IntelliJ 2026.2.3 / LSP4IJ 0.21.0 `run-15583928886685829346`: **START and X161 pass**; X161 takes
  4,789 ms, with zero recorded IDE errors and Ultimate disabled. JUnit confirms one passing suite
  test, zero failures/errors/skips. The driver invokes the actual Move dialog and global Undo/Redo.
- Both use the same **166-case** catalog, SHA-256
  `36d257c300986d7e4efed0eeb7173542266c9f02b4f9165e8a46a09534866cd3`.

These are selected acceptance runs, not new full-catalog receipts. The following checkpoint adds
explicit source-graph relocation through the host proposal/persistence/Undo path. Richer static
callable identities and the bounded relocation follow-ups listed above remain tracked.


### L62 explicit source-graph relocation (2026-10-02)

The custom `xtc/renameFiles` request returns a complete move proposal: versioned text edits,
requested and companion file operations, graph before/after values and the existing proof scope.
Compiler mode advertises `xtcFileMoveProposal: 1`. Ordinary `workspace/willRenameFiles` continues
to return only additional edits and refuses transactions requiring settings changes. The server
never installs proposal settings or writes source files itself.

IntelliJ's native Move command captures settings and source versions before requesting the proof,
then persists settings and file operations in its existing guarded global Undo command. VS Code's
file-operation middleware uses the same proposal, adds a JSON settings edit, and removes the
requested operations from its reply because the editor already owns those moves. Settings scopes,
source versions and workspace topology are checked across asynchronous conversion. Moving the
workspace itself or its settings container is outside this transaction.

Module containers and same-basename module roots can move within the explicit graph. Companion
trees move with their root file. Ordered custom resource roots follow moved directories; `[]`
stays disabled. If only a module root moves, resolved default resource roots left behind are
materialized as explicit paths so the new location does not change resource lookup. Dependency
names and unrelated settings are preserved, and the graph before value enables exact Undo.

Resource proof uses a proposed-path view over the captured original files. It does not write
scratch trees or mutate compiler ASTs. Detached hashes compare embedded strings/bytes and
File/Directory/FileStore contents and names before/after the move; timestamp metadata is excluded.
The parser lowers `$path`/`#path` into literal nodes, so those includes are checked as well as
`FileExpression`. Shared resource consumers are checked across all configured modules, even when
there is no source-dependency edge. A regression caught the initially missing lowered-literal
case: both programs compiled while the new lookup selected different fallback bytes.

The proof refuses uncaptured incoming resources, embedded source files whose text is being edited,
collisions, overlapping requests (including implicit companions) and existing unsafe bindings.
Before replay, every source member must still belong to its proposed module companion tree.
The initial VS Code selection passed all four assertions but its log exposed a nested companion-only
request that violated this invariant and threw from `XdkSources`; the added regression now requires
safe refusal before construction. That run is not counted as clean acceptance. No Java AST accessor, mutable
field, cloning responsibility or embedding API change is needed.

Shared X162 covers an explicit two-module container move, ordered resource roots, a closed
consumer and disabled resources. X163 covers a root-file move, its companion and default resources
left behind. Both drivers assert source/resource contents, persisted paths, clean diagnostics and
one Undo/Redo. X118 and X161 are included in selected regression acceptance for the shared settings
and existing type-move paths. This is a focused gate, not a full-catalog rerun.

Still outside this slice: combined root rename plus parent change, discovered root-file moves
that need host-persisted resources, standalone resource-only graph relocation, moving workspace
settings ownership, and richer union/generated/cyclic callable identities. Omitted consumers and
binary contracts retain their existing boundaries. VS Code participation cannot veto arbitrary
host moves when the compiler refuses; X130/UP16 remains a separate upstream Explorer issue.

Local extraction map (keep dependent slices in this order):

- `827638a44`: proposal protocol/capability, graph relocation, resource overlay and detached
  value proof, backend/protocol regressions. No Java API changes.
- `3ad6d2121`: IntelliJ and VS Code graph persistence, source/settings guards and Undo integration.
- `8e42559e9`: shared X162/X163, both editor drivers, catalog registration and manual steps.
- `3cab52e7b`: module-ownership/overlap guards and regression for nested companion requests.
  Extract this correction with the backend slice, not as optional editor cleanup.
- `9145781c0`: IntelliJ descendant document lifecycle repair (UP19), pinned API/event-selection
  tests and post-Redo editing acceptance. Keep this with the IntelliJ graph-move integration.

Validation: **80 backend tests across seven suites**, **15 IntelliJ unit tests across three suites**,
zero failures/errors/skips. Both driver compilations and root/LSP/IntelliJ Spotless checks pass.
VS Code 1.140.0 `run-WvHL3O` passes **X118/X161/X162/X163** (2,820 / 1,676 / 1,666 / 1,772 ms),
with zero reported extension errors and no compiler internal errors in the saved server log.
The earlier `run-cuY1mp` also passed its assertions, but logged the corrected companion-request
exception; it is deliberately not the clean receipt. The report's `errors` array contains test
failures, so the separate server-log check matters.
The catalog contains **168 scenarios**, SHA-256
`69810cbc5be843deafa7a4be8b058b94b1028baa72ad717b148509eed59b248a`.
IntelliJ 2026.2.3 / LSP4IJ 0.21.0 `run-16019291377503835349` passes **START and
X118/X161/X162/X163** (21,487 / 6,660 / 3,267 / 5,851 / 6,215 ms). JUnit confirms one passing
suite test, zero failures/errors/skips; the report has zero IDE failures, and the saved IDE/server
logs contain no internal errors. Ultimate remains disabled.

The initial IntelliJ run `run-12819902949651520401` passed the other three cases but timed out
after X162's Undo: LSP4IJ retained descendant connections under the old directory URI. UP19's
client-scoped bridge now retires those connections before the path changes and reconnects current
buffers asynchronously through the public API. Only the package-private disconnect operation
requires reflection; map/lock internals are untouched. The current trace closes/opens App.x on
every Move/Undo/Redo and delivers post-Redo unsaved edits to the final URI. Both graph-move cases
also verify that an introduced error appears and clears when the original source is restored.
See [the upstream register](errs-upstream-issues.md#up19-directory-moves-retain-old-document-connections)
for the implementation boundary and removal gate. This does not close broad rapid-edit/multi-window
lifecycle acceptance or L62 as a whole.

The next checkpoint implements the bounded callable target-set proof described below. Combined
rename/move, unsupported qualification syntax and interacting batch plans remain separately
tracked relocation extensions.


### L62 union and recursive callable proof (2026-10-03)

A selected `MethodConstant` may name one written declaration even when the call's receiver is a
union or a recursively delegating class. Capturing only that declaration loses alternatives and
receiver provenance. The adapter now captures each explicit receiver and selected method from the
existing `NameExpression`/invocation APIs while its compilation pool is owned by the worker. It
detaches union alternatives, ordered written contracts, delegate properties and finite cycle
back edges before releasing the attempt. Both occurrence and call edges compare these facts
through the proposed source edit. A present but untranslatable callable proof refuses the edit.
No new Java AST state, compiler public API or optimized runtime method generation is required.

For plain source receiver classes, a union call joins otherwise independent written method
families. Rename from either declaration or the call updates all contracts and configured
consumers; unrelated same-named methods stay unchanged. Nested alternatives and cross-module
closed consumers are covered. Receiver class renames may reorder union operands without changing
the unordered target set. Recursive delegation retains its written interface contract and closing
edge; rename can preserve that finite route without claiming a concrete runtime implementation.
Go to Implementation still refuses runtime-only recursive/interface delegate targets.

Generated-method audit: shorthand constructors and implicit virtual-child constructors do not
become independently renameable methods. `isCtorOrValidator` does not classify an implicit body
without a `MethodStructure`; the audit verifies its original declaration identity instead.
`construct` is syntax, not a user-selected name. Existing primary-property/named-label, lambda and
escaped-value support remains attached to written
identities (shared X119/X120/X121). Native methods and generated field accessors do not gain
invented editable bodies. At this checkpoint flat dispatch rejected union routes. The substituted-receiver continuation
below replaces that restriction with structured branches shared by site and declaration proofs;
it still does not flatten alternatives into one override chain.

Shared X164 renames independent methods coupled by a closed cross-module union consumer. Shared
X165 renames a written interface contract through mutual delegation. Both drivers check exact
changed files, diagnostics and Undo. Backend tests additionally exercise reverse rename, nested
unions, reordered receivers, collisions, generic refusal and deliberately damaged detached
proofs (lost alternative, changed receiver, missing source, removed delegate/back edge).

Remaining L62 callable boundaries:

- [x] Bounded parameterized/formal/annotated union operands now have detached substitution/type
  proof; see the substituted-receiver continuation below for supported shapes and validation.
- [x] Union dispatch below generated delegation retains nested alternative routes.
- [ ] Audit wider escaped-value compositions; implicit calls through a generated delegate are
  covered by the continuation, not every implicit receiver shape.
- [ ] Broader generated accessor/constructor transformations need written source semantics; merely
  exposing generated runtime bodies is not sufficient.
- Binary/XDK contracts remain read-only; consumers outside the configured graph are not inferred.

Local extraction map (initial checkpoints need the subsequent site-proof correction):

| Slice | Commit | Extraction requirement |
| --- | --- | --- |
| Union target sets and rename family closure | `6c5ac33b2` | Keep with the site-specific proof correction; a method constant alone is insufficient. |
| Recursive written-contract routes | `649961788` | Keep cycle anchors in both dispatch relations and callable sites. |
| Generated constructor audit | `0c3c59c25` | Include the corrected virtual-child fixture from validation. |
| Shared X164/X165 and editor registration | `f63350357` | Keep both drivers and catalog updates together. |
| Callable-site proof and validation correction | `22330f587` | Required with the union/cycle slices: receiver routes, call/occurrence edges, cycle relations, fail-closed translation and corrected audit fixtures. |
| Catalog registration order | `29a3d8c47` | Keep with X164/X165: numeric catalog order and matching VS Code registration. |

Backend validation: **202 tests across 19 suites**, zero failures/errors/skips. Both editor
drivers compile. VS Code 1.140.0 `run-VqFiDW` passes **X119/X120/X121/X164/X165**
(1,747 / 978 / 1,056 / 1,190 / 983 ms), zero reported extension failures and no internal-error
markers in the saved compiler server log. The initial VS Code attempts stopped on catalog/driver
registration order before executing scenarios (the second is `run-0jvdZx`); these are failed
setup attempts, not accepted runs. IntelliJ 2026.2.3 / LSP4IJ 0.21.0
`run-12344641320847299097` passes **START and X119/X120/X121/X164/X165**
(20,532 / 3,401 / 1,607 / 1,820 / 1,922 / 1,583 ms), with Ultimate disabled. JUnit
confirms one passing suite test, zero failures/errors/skips; there are no reported IDE failures
or internal-error markers in the saved IDE/server logs. Root/LSP/IntelliJ Spotless checks pass.
The current catalog has **170 scenarios**, SHA-256
`82824c412b3e5636a0ce2335de726d0e6f6021bb1db1b4dd75e85c09b59fa555`.
No full-catalog or complete-L62 acceptance is claimed.


### L62 substituted receivers and nested union delegation (2026-10-03)

Implementation checkpoints, followed by one combined regression gate:

| Slice | Commit | Scope |
| --- | --- | --- |
| Receiver type proof | `f68f5df1e` | Detach generic arguments, nested type shapes, annotations and supported annotation values; translate source identities inside types during edits. |
| Nested union routes | `edc139e96` | Keep branch receivers and dispatch separately through delegate layers, in both callable-site and declaration dispatch proofs. |
| Readable fixtures | `7b0756e5c` | Multiline Kotlin raw-string fixtures; caret lookup and edit application use real line/column coordinates. |
| Shared acceptance | `89d6af021` | X166 generic union, X167 annotated union and X168 nested union delegation; both editor drivers, closed consumers and Undo. |
| Validation correction | `d57d5a215` | Required with both implementation slices: selected identities with nested substitutions, relational delegate access and typed cycle anchors; valid annotation fixtures and fresh-pool regressions. |
| Native harness repair | `61a95d9ee` | Test-only modal-safe focus dispatch; retain UP20 evidence and focused native acceptance. Independently extractable from the compiler proofs. |

The compiler's existing type/annotation and MethodBody APIs provide the required facts. No Java
AST field or public embedding accessor is added. Detached type structure records parameter order,
source/binary declaration identities and annotation constructor values; a printed type name is
not an equivalence key. Source type renames translate nested declaration anchors through the same
edit plan as ordinary name uses. Relational union/intersection operands compare as alternatives;
difference operands retain order. Unsupported type shapes or annotation constants remain unproven.

Dispatch now carries union branches alongside the written contract set. That contract set joins
rename families, while the branches independently preserve each receiver, delegated property path
and recursive closing edge. The shared dispatcher is no longer a flat-chain-only API. Member
generation cannot erase a branch via its ordinary composed-member bridge. Go to Implementation
continues to require a written executable target; this does not infer runtime targets.

The backend additions cover generic and annotated receiver rename, nested substitutions, source
type argument rename, formal arguments, two delegate layers, generic delegates, implicit calls,
a recursive alternative and a colliding branch. Adversarial facts remove/change type arguments,
annotation values, alternative targets and delegated receivers, requiring proof rejection. Large
source fixtures use `"""...""".trimIndent()`; no line-zero assumptions remain in these three suites.

Validation found three additional boundaries: a relational receiver cannot be wrapped in a
private class-access view; a method declaration can retain a formal return even when its receiver
has a concrete signature; and a delegation cycle can close on a union, not only a class. The fixes
retain the relational view, resolve the selected method by compiler identity in receiver context,
and preserve a typed cycle anchor. Closing-contract inspection walks only the finite MethodBody
tree. There is no name-only lookup or repeated traversal around a delegate cycle.

Remaining refusals include unsupported annotation constants (for example aggregate/floating
values), dependent/dynamic type shapes and uncharacterized annotations around whole relational
types. Wider escaped-value and generated accessor/constructor transformations remain open.
Binary contracts stay read-only and omitted source consumers remain unknown.

The combined gate passes **228 tests across 21 suites**, with zero failures/errors/skips, and both
editor drivers compile. Root/LSP/IntelliJ Spotless checks pass. VS Code 1.140.0 `run-oSVuMJ`
passes **X164–X168** (1,985 / 1,064 / 1,011 / 1,042 / 1,127 ms), with zero reported extension
errors and no compiler internal-error markers in the saved log.

The first IntelliJ attempt, `run-6126665495647161203`, passed START and X164, then stalled at
X165's unsubmitted Rename dialog. Live thread evidence identified the Driver's two-step modality
race during a focus observation; the compiler was idle and preparation took about 1 ms. The test
IDE was stopped after capturing evidence, so this is a failed receipt. The test-only repair is
tracked as [UP20](errs-upstream-issues.md#up20-test-driver-focus-calls-race-with-a-newly-opened-modal-dialog).
Repaired IntelliJ 2026.2.3 / LSP4IJ 0.21.0 `run-907034856191389577` passes **START and
X164–X168** (20,510 / 3,726 / 1,642 / 1,651 / 1,592 / 1,707 ms), with Ultimate disabled.
Dedicated focus/replay regression `run-7549109475022481665` passes START and START_FOCUS
(20,372 / 6,578 ms). JUnit reports **two passing suite tests**, zero failures/errors/skips.
Both runs have no reported IDE failures or internal-error markers in saved IDE/server logs.
Root/LSP/IntelliJ formatting checks pass after the harness repair. No compiler behavior changed
after the 228-test backend gate.

That checkpoint had **173 scenarios**, SHA-256
`fa2c3d1f8dd1cf3161ed561723af9af46462b0752e05c06b7492f738a6638e1e`.
This is selected validation, not complete L62 or full-catalog acceptance.


### L62 combined rename and relocation (2026-10-03)

Four local implementation checkpoints preceded the combined validation gate:

| Slice | Commit | Scope |
| --- | --- | --- |
| Type rename plus move | `679bb8269` | Combine source-identity rename with namespace prefix edits, companion paths and final-graph proof. Prefix insertion/token replacement remain separate for proof and coalesce for editor application. |
| Module-root rename plus relocation | `c61003890` | Rename declarations/imports and graph dependency names together, retain domain suffixes and preserve default/custom/disabled resources. |
| Interacting batch moves | `345c32b87` | Compute final type qualifications against all requested destinations together; preserve mutual references, require disjoint edits and reject the whole batch on failure. |
| Editor acceptance | `88df8e6db` | Single-file New name field in IntelliJ Move, shared X169–X172, both drivers and manual steps. |

This extends the same-module, compiler-proven destination namespace path. It does not create a
namespace from a guessed directory or rename the `construct` keyword. Existing-source/destination
collisions, symlinks and overlapping companion/parent-child operations remain refusals. All known
source consumers participate in compilation and binding/dispatch proof; unconfigured consumers
remain unknown. Explicit module graph replacement still requires a host that persists the proposal.

Both editors share the existing `xtc/renameFiles` protocol. IntelliJ exposes a new basename for one
selected source; its batch Move preserves individual basenames. The protocol can express a batch
with individually renamed entries. X172 checks refusal on each installed client connection without
applying a host move; VS Code's inability to veto arbitrary Explorer moves is unchanged.

No Java AST field, accessor or embedding API is added. Larger new test fixtures use raw multiline
Kotlin strings. Validation and the required corrections are recorded below; prior receipts do not
cover this batch.

Validation uncovered [compiler issue #667](https://github.com/xtclang/xvm/issues/667): an inline
`util.Taken` plus `App/util/Taken.x` crashes the ordinary compiler on master `7a4e29e57`, without
any LSP involved. `Component` permits conditional siblings; source registration failed to reject
two unconditional declarations, and `NamedTypeExpression.calculateDefaultType()` subsequently
cast their `CompositeComponent` to `ClassStructure`.

The independent compiler fix reports existing `Compiler.DUPLICATE_NAME` (`COMPILER-148`) at the
new name token during registration and defers the invalid subtree. The assembler's conditional
sibling mechanism is unchanged. `CompilerDuplicateTypeTest` fails its two duplicate cases on
unmodified master and passes all four cases after the fix, including same-named nested types in
different scopes and complementary conditional declarations with nested children. These tests
need no XDK artifacts and skip nothing. Master plus the local fix passes 108 selected Java tests;
its CLI now emits `COMPILER-148` instead of a stack trace. Wider overlapping conditional cases
are not claimed fixed. Keep this compiler repair/test separate from the LSP move guard when
extracting PRs; no master fix PR has been opened.

The LSP guard independently checks compiler-resolved destination children before replay, so an
inline collision refuses the complete move proposal without requiring an invalid compilation.
The combined regression gate passes 222 backend and seven IntelliJ unit tests with zero
failures/errors/skips; both drivers compile. After the compiler repair, nine targeted Java and
46 affected LSP tests pass. Selected editor acceptance is recorded below.

Validation also corrected declaring-model selection across consumer snapshots, preserved explicit
import aliases while renaming their imported targets, and coalesced same-position qualification
insertions/name replacements for clients. The fresh reverse-refactoring test keeps the existing
uncaptured-resource refusal: moving a root back into its pinned resource directory is not proven
when that incoming tree was outside the resource snapshot. Host Undo instead restores the stored
transaction, including the original resource policy, and is tested separately in X170.

Final selected acceptance for this continuation:

- VS Code `run-TjE279`: X161/X163/X169–X172 all pass, zero extension errors and no compiler
  internal-error markers in the saved language-server log.
- IntelliJ `run-14394987299477639656`: START and those six cases all pass; one JUnit test,
  zero failures/errors/skips and no IDE failures. The saved server/IDE logs contain no internal
  error markers. Closed source/resource contents are checked on disk after Move, Undo and Redo.
- Both runs use shared catalog SHA-256
  `dbd180cf17d9cde7e5e9820bf4277c9caaa1874af314aec89e9ee62b47414c73` (177 scenarios total).
  These six selected cases do not establish full-catalog acceptance.
- The first IntelliJ run, `run-8269964736389146880`, failed X169 with stale closed-file contents;
  five other selected cases passed. Preserve it as the UP21 failure receipt, not a successful gate.
- Root, LSP and IntelliJ read-only Spotless checks pass. All tested changes remain local on errs.

Required correction/extraction map:

| Commit | Keep with | Reason |
| --- | --- | --- |
| `494a18f6c` | `679bb8269`, `c61003890`, `345c32b87` and X169 data | Canonical declaration selection, explicit/implicit imports, inline collision guard, canonical resource assertions and the documented reverse-resource refusal. |
| `2ce6029cf` | IntelliJ portions of `88df8e6db` | Fix the New name field's Swing name collision; UP21 persistence for only affected closed documents, ordered Undo/Redo, and stronger native disk assertions. |
| `2793efdd9` | Independent compiler fix for issue #667 | Eleven-line structure-registration guard plus `CompilerDuplicateTypeTest`; validated directly on master and on errs. No LSP or embedding API dependency. |

The only Java AST change in this continuation is the independent duplicate-declaration repair.
Structure registration is its natural home: it rejects invalid declarations before ambiguous
assembler siblings reach name/type resolution. It adds no AST fields, accessors, partial/LSP hooks,
cloning responsibilities or embedding API. The relocation and host repairs remain Kotlin-side.

At this October 3 checkpoint, L62 remained open for unproven empty namespaces and commented
qualifications (subsequently addressed for captured packages below), cross-module type ownership,
specialized qualifications, overlapping companion operations, uncaptured incoming
resources, unsupported annotation/type/callable routes and unconfigured consumers. Binary
contracts remain read-only; compiler refusal cannot veto arbitrary VS Code Explorer moves.


### L62 empty destinations and source trivia (2026-10-04)

The duplicate-declaration repair is now extracted as [PR #668](https://github.com/xtclang/xvm/pull/668).
Its `8f1104bfe` corresponds to errs compiler checkpoint `2793efdd9`; `583cdd81d` adds the
manual-suite runner and removes the artificial conditional-parser test. Errs now carries the
same three actual-source Java tests and five CLI scenarios: duplicate companion, duplicate
inline, inline-only, companion-only, and distinct enclosing scopes. The earlier four-test receipt
above describes the previous test version; conditional AST mutation is no longer a regression
claim. Keep this fixture/runner checkpoint with the independent compiler fix during extraction.

`runDuplicateTypes` uses the consumer's resolved XDK and participates in `check`, `runSequential`
and `runParallel`. Configuration-cache reuse and combined validation pass; see the receipt below.
The preceding L62 checkpoints through `50b113961` were pushed before this continuation.

Empty existing destination directories now extend the nearest compiler-proven module/package
through captured implicit package directories. Explicit companion sources cannot be guessed past.
After replay, every moved declaration must have its expected module and full type path, including
unused types; normal binding/call/resource proof still applies. Nonexistent destinations, foreign
modules and class-owned directories remain refusals. Backend regressions cover nested empty
destinations, explicit empty packages and all three ownership refusals. Validation was batched
after the implementation checkpoints; see the receipt below.

Qualified-name edits now use the compiler lexer to change identifier/dot tokens while preserving
intervening comments and whitespace. Common suffixes and explicit import aliases remain intact.
Call-site translation skips trivia left by a removed prefix; declaration/binding/dispatch proof
is unchanged. Regressions include import aliases, closed consumers, nested types, constructor
and static calls, prefix insertion/removal, Unicode, CRLF and a binding-changing refusal. No
AST or embedding API change is needed. Specialized names outside identifier/dot syntax still
refuse. Validation was batched after the implementation checkpoints; see the receipt below.


### L62 empty-destination batch acceptance and extraction

The four implementation checkpoints preceded the combined test iteration. The extraction map
also includes the Gradle import correction required when bringing the manual runner into errs:

| Checkpoint | Extraction group |
| --- | --- |
| `a1b266cd0` + `102560ccb` | Independent compiler issue #667 / PR #668 regression fixtures, runner, Java test cleanup and errs-specific Gradle import. Keep with `2793efdd9`; no LSP dependency. |
| `a58a10830` | Empty captured package planning, explicit post-replay destination proof and ownership regressions. |
| `7a81b0a90` | Lexer-based qualification spelling, prefix-trivia call-site translation and binding-preservation regressions. |
| `8cdc60dd0` | Shared X173–X176 data, empty-directory setup in both drivers, manual steps and capability updates. |

Acceptance correction `6675be43a` carries formatter output, restores the catalog's existing
JSON Unicode escaping and adjusts IntelliJ's test comparison for LF-normalized document buffers.
Closed sources/resources still compare exact disk text. X174 uses CRLF input; its open IntelliJ
buffer is compared with LF normalization, matching the IDE document model.
Keep these file-specific corrections with the respective implementation/editor groups above.
No new mutable AST state, compiler accessor or embedding API was introduced.

Validation receipts:

- **155 LSP tests** across rename/move suites and **103 Java compiler tests** pass with zero
  failures/errors/skips. This includes all 21 `XdkTypeMoveTest` cases, six added in this batch.
- All **five manual CLI scenarios** pass on errs. `runDuplicateTypes` executes again with
  configuration cache explicitly reused (4 seconds); logs are under
  `manualTests/build/reports/duplicate-types/run-10035154598908356788`. The broader manual
  sequential/parallel runtime suites were not rerun in this batch.
- Both editor drivers compile. VS Code **`run-ElUMIl`** passes X169/X171/X173–X176 with zero
  failures and zero extension errors.
- IntelliJ **`run-13159045223223510909`** passes START and the same six cases, with zero IDE
  failures; JUnit reports one test, zero failures/errors/skips. Closed-file disk assertions
  participate in each successful Move/Undo/Redo case.
- Both use the 181-scenario shared catalog SHA-256
  `e4c39da9ecef44abf0ac5bad3c08c5044ecc2a78f2433a0b841d3d0289024eda`. Neither is a full-catalog
  run. Saved logs contain no compiler internal-error, ClassCastException or NullPointerException
  markers. X175 is a proposal refusal check; VS Code Explorer veto remains unsupported.
- Root, LSP and IntelliJ read-only Spotless checks pass, as does `git diff --check`.

This finishes the agreed L62 batch, not every remaining L62 exclusion. Cross-module ownership,
overlapping companion operations, uncaptured resources and unsupported callable/type/annotation
routes remain explicit follow-ups. Binary declarations stay read-only and unconfigured consumers
remain outside the declared proof scope. L63 is the next planned functionality slice.


### L63 local transformation batch (2026-10-04)

Implement in separate checkpoints, then run the combined backend and selected editor gate:

1. [x] Extract an entire explicitly typed local initializer into an immediately preceding local,
   preserving its written expected type and evaluation order. Reject inference, property/conditional
   initializers, partial selections and declaration annotations/comments that are not understood.
2. [x] Inline an adjacent single-use local into another explicitly typed initializer with the
   same written expected type, preserving every moved reference/call and the evaluation count.
3. [x] Remove an unused plain local only with compiler-proven constant, side-effect-free
   initialization; preserve comments and reject runtime evaluation or Ref/Var construction.
4. [x] Shared editor cases, exact edits, diagnostics and Undo/Redo; update capability/playbook docs.

No new Java AST fields or accessors were added. Detached compiler evidence and the existing
whole-graph binding/dispatch proof gate the edits. Extract-method, missing declarations, global
safe delete and wider evaluation contexts remain separate L63 transformations. Cross-module
ownership stays in L62: it must account for dependency edges, import changes, visibility and
resource ownership, in addition to the type's final path.

Unused-local removal captures validated `Expression.isConstant()` and `hasSideEffects()` evidence
on the serialized compiler worker and retains only declaration source locations. It also rejects
Ref/Var annotations, runtime initializers, uses/writes, inference and comments inside deleted syntax.
Leading/trailing comments remain intact. The compiler must accept the complete proposed graph and
the existing removal proof must preserve every unaffected binding/call/dispatch edge. No AST API
change is required. Validation is recorded below; these facts are collected only for semantic
action proof, so ordinary rename/navigation does not add this AST walk.

X177–X180 now share source, selection, expected edits and descriptions across both editor drivers.
The catalog has 185 scenarios. All four implementation checkpoints preceded the combined
backend and selected editor validation below.


L63 validation exposed an independent compiler defect: `probe() == 1 && False` passes
validation but throws `IllegalStateException` in `CondOpExpression.generateArgument()` while
emitting code. The runtime/False case (`UandF`) was absent from both value and conditional-jump
emission, although validation deliberately keeps the runtime left operand for its effects. The
existing `origin/master` source has the same omission. The assignment fast path also compared
the left operand twice instead of inspecting the right operand.

The isolated repair adds the missing false-result emission while evaluating the left operand
exactly once, and corrects that operand comparison. `conditionalEffects.x` is a standalone manual
module, wired into both sequential and parallel module lists. It checks both input values across
value, branch, nested argument and constant-left/right forms (22 runtime assertions of one call
and the expected result). Keep the compiler/fixture/list change separate from the LSP actions
when extracting a master fix. No LSP-only AST fields/accessors are involved. The final fixture
fails in the retained unmodified-master CLI artifact (`7a4e29e57`) with that code-generation stack,
then compiles and passes all 22 runtime checks with this repair. The current `origin/master`
source comparison confirms the same missing case; no remote issue or PR was created in this batch.


### L63 local transformation acceptance and extraction

| Extraction group | Commits | Scope |
| --- | --- | --- |
| Typed initializer extraction | `326f1faaa` | Shared plain-local syntax boundary, contextual type preservation and 22 combined initializer tests with the next group. |
| Adjacent initializer inline | `39b6be560` | Same-written-type, single-use, adjacent relocation with existing binding/call/dispatch proof. |
| Unused constant local removal | `c4b0130bd` | Detached validated constant/side-effect facts, complete-graph deletion proof and 15 removal/refusal tests. |
| Shared editor coverage | `7dc0f7c65` | X177–X180, both drivers, descriptions/manual steps and capability updates. |
| Acceptance corrections | `888e89bf6` | Spotless output, test line wrapping, restrict unused-local evidence capture to action proof, and register VS Code cases in catalog order. Distribute these file-specific corrections with the corresponding groups above. |
| Independent ordinary compiler fix | `554f86e63` + `0550380b4` | Missing `UandF` emission, assignment operand correction and executable `TestConditionalEffects` manual module. The follow-up uses an instance counter because a module property is constant. No LSP dependency. |

Validation on 2026-10-04:

- **129 LSP tests pass**, zero failures/errors/skips: 22 new initializer cases, 15 removal cases,
  16 existing extraction, 14 existing inline, 47 member-action, 11 server rename, two relocation
  proof and two code-action tests. Both editor drivers compile.
- `:manualTests:runOne -PtestName=TestConditionalEffects` compiles the normal manual source set
  and passes all **22 runtime checks**; a second real run passes with configuration cache reused.
  Both manual sequential/parallel module lists include it, but those entire suites were not rerun.
- VS Code **`run-nDHZOH`** passes X156/X157/X177–X180, zero failures/extension errors. The earlier
  **`run-OKOdXS`** failed the catalog-order setup assertion before running any case; the registration
  correction is included above, and that failure is not counted as acceptance.
- IntelliJ **`run-232740531754002686`** passes START and the same six cases, zero IDE failures;
  JUnit reports one test, zero failures/errors/skips. X177–X179 use native intentions and Undo/Redo;
  X180 checks refusal through the installed connection without applying an edit. Ultimate remains
  disabled. VS Code uses its extension-host provider/edit/history commands.
- Both use catalog SHA-256
  `c369eab0aa4278bb0f2714fd518a605c6317d97191931e817e49263b7f4c7788` (185 scenarios).
  These are selected runs, not a full-catalog rerun. Saved logs contain no compiler internal-error,
  `ClassCastException` or `NullPointerException` markers.
- Root, LSP and IntelliJ read-only Spotless checks and `git diff --check` pass.

The initial backend run passed 115/116 and exposed the independent conditional-expression
compiler bug above. The first combined rerun passed all 129 backend tests but rejected the manual
fixture's constant module counter; the corrected instance-based fixture then passed. Preserve
these distinctions when extracting the commits. L63 remains partial for general extraction,
extract method, broader inline, missing declarations and global safe delete. Cross-module
ownership remains the separate L62 graph/import/visibility/resource transformation.


### L63 private helper extraction batch (2026-10-04)

1. [x] Capture detached compiler type identities and effectively-final, non-reference register
   evidence for action proof. Reuse existing Register/AST APIs; add no mutable AST fields.
2. [x] Extract a complete return expression or explicitly typed local initializer to a private
   same-owner helper. Pass stable inputs explicitly, preserve written types and exact moved text,
   and prove parameter rebinding, argument order, selected calls and unaffected dispatch.
3. [x] Add supported/refused backend fixtures and compiling counterexamples for swapped helper
   arguments and redirected moved calls. Existing local-extraction assertions identify their action
   title now that multiple extract actions can coexist.
4. [x] Add shared X181–X184, both editor drivers, full-selection refusal checks and manual steps.
5. [x] Run the combined backend/formatting gate and selected cases in both IDEs; receipts below.

Same-owner generic types and stable local/parameter values are candidates. Mutable/register-ref
captures, method-owned generic parameters, conditional returns, async calls, lambda/anonymous-class
creation, reference-taking, partial selections and inferred initializers remain refusals. General
statement extraction and new mutable-capture protocols require separate semantic work. Types are
compared through compiler identities, never display strings. New helpers must preserve all old
binding/call edges and may add only their own dispatch chain. This remains a bounded L63 slice;
missing declarations, broader inline and global safe delete remain open. Cross-module ownership
remains in L62. Validation follows all four implementation checkpoints.


### L63 private helper acceptance and extraction

| Extraction group | Commits | Notes |
| --- | --- | --- |
| Detached capture/type evidence | `db986edcd` | Compiler-owned register/type inputs copied to immutable proof identities and source locations only during semantic action proof. Includes stability/normal-query controls. |
| Private helper planning and proof | `2d29188bb` | Exact whole-expression relocation, explicit stable inputs, same-owner private helper, signature/argument/call/binding/dispatch validation. |
| Semantic tests | `a3bc940c7` | Supported/refused sources and two compiling-but-incorrect argument/call counterexamples. Existing local-extraction assertions select their action by title. |
| Shared editor scenarios | `13817b4d4` | X181–X184, both drivers, full-selection refusal checks, catalog and manual steps. |
| Acceptance corrections | `6acfcfab5` | Query-local parser ownership map, skip typeless method formals in the existing return-type reader, stateful-call/anonymous-class regressions and formatter output. Keep the relevant corrections with the groups above. |

Validation on 2026-10-04:

- **151 backend tests pass**, zero failures/errors/skips. This includes 18 method-extraction
  scenarios, two detached-fact tests, two adversarial proof tests and the preceding 129-test local,
  member-action, rename-server, relocation and code-action selection. Both editor drivers compile.
- The first substantive run was 137/149: eleven missing-action failures traced to unadopted parser
  parent links, plus the typeless-formal null dereference in the existing extract-local reader.
  The corrections passed all 58 affected tests before the final expanded 151-test gate. These are
  branch LSP implementation defects, not additional ordinary compiler defects on master.
- VS Code **`run-ZhuPaV`** passes X156/X177/X181–X184, zero failures/extension errors.
- IntelliJ **`run-5839432120705984532`** passes START and the same six cases, zero IDE failures;
  JUnit reports one test, zero failures/errors/skips. Ultimate remains disabled.
- X181–X183 verify exact helper signatures/bodies, clean diagnostics and Undo/Redo/Undo. X184
  queries the entire selected expression through the installed connection and verifies refusal
  with unchanged source. Existing X156/X177 confirm local extraction still coexists with the
  new extract-method action.
- Both use the **189-scenario** catalog SHA-256
  `e22c795324e6b4086391b280a7250a3eb055cde5959725c59ff2d01eaa7c9163`.
  These are selected runs, not full-catalog reruns. Saved logs contain no compiler internal-error,
  `NullPointerException` or `ClassCastException` markers.
- Root, LSP and IntelliJ read-only Spotless checks and `git diff --check` pass.

Generated helpers use a fresh `extractedMethod` name with a numeric suffix when needed; ordinary
rename can change it afterward. The expression text remains unchanged inside its helper.
Only stable value reads are passed early; state-changing calls stay in their original order inside
the expression. General statement extraction, mutable/reference captures, lambda/anonymous-class
creation, async calls and method-owned formals remain outside this proof. No new Java AST API,
mutable field, parent mutation or embedding entry point was introduced. Missing-method declaration
fixes are the next planned L63 capability; broader inline and global safe delete remain open, and
cross-module ownership retains its separate L62 scope.


### L63 missing-method quick fixes (2026-10-04)

- [x] Inspect only a fresh successful declaration analysis, not failed-validation TypeInfo.
  Reuse the compiler-type signature renderer; copy only strings and source positions into action facts.
- [x] Generate a private same-owner method for an unqualified synchronous call used as a statement
  or the complete return expression. Parameters come from resolved enclosing-method parameters
  or supported explicitly typed literals; return types come from the enclosing declaration.
  Reject existing overload/property names, local shadowing, method-owned formals and closure boundaries.
- [x] Compile the entire proposed graph, preserve all known binding and dispatch facts and verify
  that the selected call binds to the newly inserted declaration. Publish a versioned edit only
  while captured inputs remain current. The body is `TODO()`; no source file is written by the query.
- [x] Add backend positive/refusal regressions and shared X185–X188 with both editor drivers.
  Drivers assert error-before, clean-after and diagnostic restoration/clearing on Undo/Redo.
- [x] Finish the combined backend gate and selected acceptance in both editors; receipts below.

This slice uses existing embedding/declaration/AST accessors entirely from Kotlin. No Java AST
field, clone obligation or new embedding entry point is needed. Untyped numeric literals are
refused rather than exposing `IntLiteral`/`FPLiteral` compiler implementation types. Other literals
use `LiteralExpression.getImplicitType` within the fresh attempt's constant-pool scope. Unsupported
type spellings, cross-owner creation, computed/named arguments, generic methods, conditional
returns, nested-expression expected types and missing type/property declarations remain open.
The continuations below add compiler-established locals, typed initializer results and bounded
instance/class qualifiers. L63 also retains broader inline/extraction and global safe delete;
cross-module ownership remains L62.

| Extraction group | Commits | Scope |
| --- | --- | --- |
| Compiler evidence, repair action and backend regressions | `46d742b8f` | Fresh declaration candidates, existing renderer reuse, detached facts, full graph proof and selected-call target check. |
| Shared editor acceptance | `b761766e4` | X185–X188, both drivers, initial errors and Undo/Redo diagnostics. Catalog grows to 193 cases. |
| Declaration normalization and proof regression | `2507fbf51` | Resolve parser type wrappers through compiler APIs, use canonical rendered signatures and reject a compiling edit that redirects an existing overload. Keep this with the compiler evidence change when extracting. |
| Broad-selection action deduplication | `bdb27ecbc` | Repeated compatible calls in a broad selection yield one repair; the backend regression selects across both calls. |

The initial 108-test gate passed 100 tests; eight creation tests correctly withheld actions because
resolved declaration types were still wrapped in `UnresolvedTypeConstant`. Normalizing through
`resolveTypedefs()` fixes the candidate capture. The next run generated all supported actions;
assertions were then aligned with resolved spelling (`Int64` for `Int`, including compound types).
The focused 29-test gate passes without failures/errors/skips. The proof regression now also
checks a compiling but unrelated overload redirection. The combined gate passes all **180 tests**,
zero failures/errors/skips; both editor drivers compile. The subsequent broad-selection guard
passes the 29 affected tests again. Native editor acceptance passes in the final receipt below.

Native acceptance progress:

- VS Code **`run-dzR5d9`** passes X122/X181/X185–X188, zero extension errors. Existing
  packaged-protocol XML listed in that report is historical; the selected run did not rerun it.
- IntelliJ **`run-3835897731901718389`** passed START/X181, then X185 timed out on an intention
  menu containing only the scratch-file action. The server had completed the request in about
  226 ms; there was no IDE error/crash. The driver incorrectly selected the identifier as for
  expression extraction. Diagnostic quick fixes now place only a caret on the error, matching
  X105 and the manual steps. **`f62a45373`** is a harness correction, with no production bridge
  or new upstream workaround. Focused **`run-5829423385480751052`** passes START/X185 and
  diagnostic Undo/Redo. The six-case continuation `run-14888381295818547127` still failed X185,
  showing that the caret correction alone was insufficient. The UP07 fix below resolves the
  sequence failure.


#### L63 missing-method final acceptance and remaining work

- **180 backend tests pass**, zero failures/errors/skips: 28 missing-method cases, one fresh
  declaration/repair proof (including a compiling overload-redirection counterexample), and the
  preceding 151-test local/member/extraction/rename-server selection. The subsequent broad-selection
  deduplication passes all 29 affected tests again. Both editor drivers compile.
- **89 IntelliJ unit tests pass**, zero failures/errors/skips, including three new UP07
  annotation-refresh/data-restoration tests and the existing diagnostic-result/action bridge tests.
- VS Code **`run-dzR5d9`** passes **X122/X181/X185–X188**, zero extension errors.
- IntelliJ **`run-16733856986922464734`** passes **START and the same six cases** in one run,
  zero IDE failures; JUnit reports one test, zero failures/errors/skips. Ultimate is disabled.
  In particular X181 → X185 now passes after previously failing twice; X185–X187 verify native
  generation, exact text, diagnostics and Undo/Redo/Undo. X188 verifies refusal through the
  installed connection.
- Both use the **193-scenario** catalog SHA-256
  `d57b3270eac87e87689ecca1db3fbf07700267555b2b98a0431f0e300db5937d`.
  These are selected runs, not full-catalog acceptance. No new packaged-stdio run is claimed.
- Read-only root/LSP/IntelliJ Spotless and `git diff --check` pass.

Native validation also required **`f3de29b57`**, extending the existing UP07 bridge: equal full
diagnostic reports replaced/canceled lazy fixes while their unchanged annotations kept the old
actions. The client now gives each full report a presentation-only data revision and strips that
wrapper before action requests. Original server data, messages and ranges are preserved; the
helper adds no mutable state. This is separately extractable with the earlier UP07 client bridge.
It is tagged `TODO LSP4IJ` and documented in [upstream issues](errs-upstream-issues.md).
The tested correction covers automatic document pulls; workspace/related-report replacement
retains a separate UP07 native-coverage follow-up.

For future PRs, keep **`46d742b8f` + `2507fbf51` + `bdb27ecbc`** together for the complete backend
slice; the first checkpoint alone lacks declaration-wrapper normalization. Editor acceptance needs
**`b761766e4` + the catalog correction in `2507fbf51` + `f62a45373`**, plus the UP07 dependency
**`f3de29b57`**. Each extracted PR still needs independent validation.

Remaining L63 work stays explicit:

- [x] Extend method creation to compiler-proven local arguments and typed-initializer result contexts; see the continuation below.
- [x] Add qualified same-owner instance calls with compiler-proven receiver identity; see the receiver continuation below.
- [x] Add named enclosing-class static qualifiers; see the class-qualified continuation below.
- [x] Add bounded same-module cross-owner creation, including writable companions; see the destination continuation below.
- [x] Extend cross-owner local/initializer evidence and bounded cross-module source destinations; see the continuations below.
- [ ] Prove generic/conditional signatures and named/computed arguments; preserve current refusals
  until scope, types and proposed-graph bindings are proven.
- [ ] Add missing type/property declarations with equivalent ownership and compiler proof.
- [ ] Broader statement extraction/inline and global safe delete remain separate refactorings.

No Java AST field/API, clone burden or embedding entry point was added by this batch.


### L63 local arguments and typed initializer repairs (2026-10-04)

- [x] Extend missing private-method creation to prior block locals with compiler-established types,
  including `var`/`val` when their initializer has already validated successfully.
- [x] Use an explicit local initializer's compiler-resolved type as the method's result type.
  Support the renderer's existing ordinary, parameterized and compound type forms.
- [x] Copy local type spellings and declaration locations while the compiler worker owns the
  attempt. Do not retain registers, AST nodes or constants and do not resume failed validation
  or request its TypeInfo. Fresh declaration analysis still establishes owner/member eligibility.
- [x] Require the completed proposed graph to bind every local argument to its exact original
  declaration, in addition to the new-method target and existing binding/dispatch/currentness proof.
- [x] Add positive/refusal backend tests and a compiling argument-redirection counterexample.
- [x] Add shared X189–X192 and both editor drivers, including exact edits, initial errors,
  diagnostic clearing and Undo/Redo/Undo. The shared catalog now contains 197 cases.
- [x] Record the combined backend and selected native acceptance receipts below.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Local evidence, result context, binding proof and regressions | `f95f5ddf6` | Extends the preceding missing-method backend slice; no new Java compiler/AST/embedding API or mutable field. |
| Shared native scenarios | `0bae2f596` + `c1d87431d` | X189 typed local, X190 inferred local with static typed initializer, X191 parameterized result, X192 inferred-result refusal; both existing drivers and their catalog boundary checks. |

The first 47-test run exposed eight withheld actions and one proof setup failure: fresh declaration
analysis intentionally does not resolve body-local type expressions. Reading only those fresh
expressions was insufficient. Copying resolved register types from the original attempt fixes
explicit local and initializer contexts. Inferred locals additionally require a successfully
validated initializer so the compiler's temporary `Object` placeholder is never treated as
inference evidence. The subsequent **47 tests pass**, zero failures/errors/skips, and both editor
drivers compile. These are implementation findings in the Kotlin consumer, not a new master
compiler bug.

This supports prior ordinary block locals, including enclosing blocks, and whole explicitly typed
local initializers using ordinary `=`. Calls used as statements or whole returns remain supported.
A signature is only a proposal until the entire graph compiles and binding proof passes.
Uninitialized/out-of-scope/later locals, inferred result types (`var result = missing(...)`),
nested expressions, assignments to existing variables, Ref/Var annotations and unsupported type
spellings remain conservative refusals. Conditional/loop-bound locals and flow-narrowed signatures
need separate scope/type evidence before broadening the current block-local rule.

Next L63 scope after the receiver/class-qualifier continuations below: cross-owner
method creation, generic/conditional signatures and named or
computed arguments; missing type/property declarations; broader extraction/inline/global safe delete.


Validation continuation: the combined code-action/extraction/local/member/rename-server gate
passes **198 tests**, zero failures/errors/skips, including 45 missing-method cases and two
compiler repair proofs. Both editor drivers compile. The first VS Code launch stopped before
editor tests in `:xdk:lib-webauth:compileXtc`: a repository scan read a temporary `.xtc` while
another Gradle invocation was building shared outputs, producing `EOFException`. That failed
build is not native acceptance evidence. Subsequent editor launches run sequentially after the
backend gate to avoid overlapping writes to composite-build outputs.

The next VS Code launch caught the harness's explicit catalog boundary still ending at X188,
before opening an editor. Both drivers' catalog checks now include X1–X192. Keep that correction
with the shared-scenario commit when extracting this batch.


Final acceptance for this continuation:

- **198 backend tests pass**, zero failures/errors/skips. This includes all 47 focused cases
  (45 missing-method tests and two repair proofs) plus the preceding extraction/local/member/
  rename-server/code-action regressions. In particular, an otherwise compiling repair that switches
  a local argument to a different declaration is rejected.
- VS Code **`run-mRmX6Y`** passes **X181/X185/X189–X192**, zero extension errors. The six cases
  complete in eight seconds; compiler-established local signatures, explicit initializer results,
  exact source and diagnostic Undo/Redo/Undo all pass.
- IntelliJ **`run-1236802406694405307`** passes **START and the same six cases**, zero IDE
  failures. JUnit reports one native test with zero failures/errors/skips; Ultimate is disabled.
  The X181 → X185 sequence also retains the UP07 equal-report regression.
- Both use the **197-scenario** catalog SHA-256
  `8b50080872522162ca75585fcbf4552858371eb1d9f1a4a7f481d0907c9e7f5c`.
  These are selected native runs, not full-catalog acceptance. No new packaged-stdio or IntelliJ
  production-unit suite run is claimed; neither plugin's production code changed in this slice.
- Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass. Accepted editor logs contain
  no `NullPointerException`, `ClassCastException`, `NoSuchMethodError` or `EMB-5` matches.

Extract `f95f5ddf6` after the preceding missing-method backend group. Keep `0bae2f596` and
`c1d87431d` together for complete scenario registration and catalog validation; native IntelliJ
acceptance retains the preceding UP07 client dependency. Each future extracted PR still needs
independent validation. No new AST compatibility or embedding entry point is needed.


### L63 same-owner receiver repairs (2026-10-04)

- [x] Accept `this`, `this:private` and plain parameter/local receivers when their validated compiler
  type has the exact enclosing class identity. Compare compiler identities, never rendered names.
- [x] Copy only qualifying callee source spans with existing local-type evidence. `Inputs` is an
  immutable Kotlin handoff; it retains no registers, AST nodes or constants. Failed validation is
  not resumed and its TypeInfo is not queried.
- [x] Generate an instance method for an explicit instance receiver, including inside a static
  caller. Qualified calls bypass unrelated local-name shadowing; existing member names still refuse.
- [x] Select/prove the leaf member token. A selection on the receiver alone must not offer creation.
  The complete proposed graph must bind that token to the inserted method and preserve existing
  receiver/argument bindings and dispatch. Merely compiling with a different instance is insufficient.
- [x] Add positive/refusal backend tests, including same-short-name/different-owner classes and a
  compiling `peer` → `this` redirection counterexample.
- [x] Add shared X193–X196 and both editor drivers, including diagnostic Undo/Redo and refusals.
  The shared catalog now has 201 cases (X1–X196, CFG1–CFG3 and 7a.8/7a.9).
- [x] Record combined backend and selected native acceptance below.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Receiver evidence and repair proof | `8288f7399` | Extends the local/initializer missing-method slice; receiver identity, instance/static choice, leaf spans and backend regressions. |
| Shared editor acceptance | `2fe49703d` | X193 explicit `this`, X194 inferred local receiver in a static caller, X195 different owner refusal, X196 public `this` view refusal; both drivers and catalog checks. |

The first focused gate passes 67 tests with zero failures/errors/skips. The final combined gate
also includes the subsequently added receiver-only selection refusal. No Java AST field/API,
clone obligation, new embedding entry point, or plugin production bridge is needed.

At this checkpoint, other-owner, computed/chained, type-qualified and `super` receivers remained
refusals. Explicit public/protected/struct `this` views are not evidence for creating a private method. Signatures
remain bounded by the preceding argument/result renderer and owner checks; this is not cross-owner
or general missing-declaration support. The continuation below adds type-qualified same-owner
static calls before cross-owner editing and visibility policy. Generic/conditional signatures,
named/computed arguments, missing types/properties and broader refactorings remain separate L63 work.


The combined backend gate passes **219 tests**, zero failures/errors/skips: 65 missing-method
cases, three repair proofs, and the preceding 151 code-action/member/local/extraction/rename-server
regressions. Both editor drivers compile. The backend and editor Gradle invocations run sequentially
to avoid writing composite XDK outputs concurrently.


Final acceptance:

- **219 backend tests pass**, zero failures/errors/skips, including all 68 focused missing-method
  cases/proofs and the existing local/extraction/member/rename-server regressions.
- VS Code **`run-nvquYD`** passes **X181/X185/X190/X193–X196**, zero extension errors. The seven
  cases complete in nine seconds and verify exact generated text, diagnostic Undo/Redo/Undo and refusals.
- IntelliJ **`run-15968094076069950349`** passes **START and the same seven cases**, zero IDE
  failures. JUnit reports one native test with zero failures/errors/skips; Ultimate is disabled.
- Both use the **201-scenario** catalog SHA-256
  `47aeb686ed81ed76507c03bf87b0cf65c1a9ac42dc64c7e7f340912931cea9b5`.
  These are selected runs, not full-catalog acceptance. Existing host-check XML in native reports
  is historical unless covered by the fresh backend gate; no new packaged-stdio or IntelliJ
  production-unit suite run is claimed.
- Accepted editor logs contain no `NullPointerException`, `ClassCastException`, `NoSuchMethodError`
  or `EMB-5` matches. Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass.

For extraction, place `8288f7399` after the preceding local/initializer backend group and keep
`2fe49703d` with the shared editor coverage. Native acceptance retains the existing UP07 client
correction. These commits add no new Java AST/embedding API, dependency or plugin production change;
each future extracted PR must still pass independently.


### L63 class-qualified static repairs (2026-10-04)

- [x] Accept named class qualifiers, including fully qualified names, only when the validated
  compiler target is the exact enclosing ordinary class identity. Names alone prove nothing.
- [x] Copy dispatch as an immutable enum alongside each qualifying callee span. Generate static
  methods for class qualifiers and instance methods for instance receivers, independently of
  whether the enclosing caller is static. No compiler objects escape the worker.
- [x] Require the completed proposed graph to resolve the selected call to the inserted declaration
  with the requested dispatch. Preserve all known receiver/argument bindings and existing calls.
- [x] Add regressions for both caller kinds, qualified names, typed initializer/local arguments,
  multiple compatible sites, class-name shadowing, unrelated owners, existing overloads, runtime
  Class/Type values, explicit generic qualifiers and singleton qualifiers. A repair redirected to
  an existing method on another owner must be rejected even when it compiles.
- [x] Add X197–X200 to shared data, both drivers and their catalog boundary checks. The catalog
  contains 205 cases: X1–X200, CFG1–CFG3 and 7a.8/7a.9.
- [x] Record combined backend and selected native acceptance below.

This uses the existing `NameExpression.getResolvedTarget()`, class identity and semantic modifier
APIs. No Java AST field/accessor, clone burden, embedding entry point, dependency or plugin
production change is needed. Failed body validation is never resumed and its TypeInfo is not
queried; declaration/member eligibility still comes from a fresh successful declaration pass.

The initial focused gate exposed a malformed shadowing fixture: `Box Box` makes the parameter
shadow its own type annotation and produces COMPILER-136 before the missing call. Qualifying the
parameter type (`Missing.Box Box`, or `Extract.Box Box` in X200) tests the intended valid instance
receiver; the corrected test passes. No production guard was weakened for that fixture.

Remaining L63 scope at that checkpoint: cross-owner creation with explicit destination/visibility policy,
computed/chained receivers, runtime Class/Type values, explicit generic and singleton qualifier
rules, generic/conditional signatures and named/computed arguments, missing types/properties,
broader extraction/inline and global safe delete. Current unsupported forms remain refusals.


The combined backend gate passes **232 tests**, zero failures/errors/skips: 77 missing-method
cases, four repair proofs and the preceding 151 extraction/local/member/rename-server/code-action
regressions. Both editor drivers compile. Backend and editor Gradle runs are sequential so shared
XDK build outputs are not written concurrently.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Class evidence, dispatch proof and backend regressions | `c36eb87c6` | Extends `8288f7399` after the local/initializer missing-method slice; existing compiler APIs only. |
| Shared editor coverage | `165c21d79` | X197–X200, both existing action drivers and complete X1–X200 catalog checks. |

Final acceptance:

- **232 backend tests pass**, zero failures/errors/skips, including all 81 focused missing-method
  tests/proofs. Both editor drivers compile.
- VS Code **`run-3Vm6C9`** passes **X181/X185/X193/X197–X200**, zero extension errors. The seven
  cases finish in ten seconds, checking exact edits, diagnostic Undo/Redo/Undo and refusals.
- IntelliJ **`run-11432411846060652594`** passes **START and the same seven cases**, zero IDE
  failures. JUnit reports one native test with zero failures/errors/skips; Ultimate is disabled.
  The X181 → X185 sequence retains the UP07 unchanged-report quick-fix regression.
- Both use the **205-scenario** catalog SHA-256
  `95df5eed7bfb7d761f9aed380600f5574de9bd31660384c502609d53d8805915`.
  These are selected runs, not full-catalog acceptance. Existing host-check XML outside the fresh
  backend gate is historical; no new packaged-stdio or IntelliJ production-unit run is claimed.
- Accepted IntelliJ/VS Code server logs contain no `NullPointerException`, `ClassCastException`,
  `NoSuchMethodError` or `EMB-5` matches. Root/LSP/IntelliJ read-only Spotless and `git diff --check`
  pass. No upstream workaround was added.

Extract the backend commit after `8288f7399`; keep the shared coverage with the corresponding
playbook group and existing UP07 client dependency. Each extracted PR still requires independent
validation. The continuation below implements bounded same-module cross-owner missing-method creation
with proven writable destinations and an explicit visibility policy.


### L63 cross-owner source destinations (2026-10-04)

- [x] Resolve a parameter/local receiver or named class qualifier to an ordinary source class in
  the same module, including companion files. Match compiler identities within the failed attempt;
  hand off only destination source locations and dispatch to fresh declaration analysis.
- [x] Name cross-owner actions **Create public method 'name' in 'Owner'** and generate explicit
  `public` instance/static stubs. Existing same-owner actions remain private. No implicit visibility
  escalation or edits to another owner are hidden behind the old private-action title.
- [x] Require the destination to be in the captured configured source tree and writable. Publish
  edits against its URI/version, preserving the caller when the destination is another file.
  The full proposed graph must compile and the existing snapshot/currentness guards still apply.
- [x] Prove the inserted target, dispatch, public access and exact parameter/return type identities.
  Reuse detached `ProofIdentity` type structures and source-location translation; rendered spelling
  and mere successful compilation are insufficient when destination names shadow signature types.
- [x] Add backend destination/refusal tests, a compiling changed-return-type counterexample, and
  server tests for open/closed companion versions. Binary/read-only, interface, generic, const and
  other-module destinations remain explicit refusals.
- [x] Add shared X201–X204 and both drivers: another source owner, a closed static companion with
  diagnostic Undo/Redo, destination type shadowing refusal and interface refusal. X195 specifically
  checks that another receiver never creates a private method in the caller. Catalog: 209 cases.
- [x] Complete the combined backend gate and selected native runs; record receipts and commits.

That checkpoint supported whole returns and statement calls, with resolved enclosing-method
parameters or supported explicitly typed literals; zero arguments work. The next continuation
adds earlier block locals and explicitly typed local initializer results.
Existing renderer support includes ordinary, parameterized, compound and same-module source types.
The destination is an ordinary, non-generic, non-synthetic class in the caller's module. Its source
may be inline or in a captured companion file. Existing members/overloads, including inherited
names, prevent offering a new method. Bodies contain `TODO()` and require implementation.

The temporary `CompilerMissingMethod` record follows the existing compiler member-action boundary:
it owns type constants only inside the worker/attempt; `captureRenameFacts` detaches their identity
structures before publication. Signature facts cover source method declarations, not every binary
method encountered during analysis. No new Java AST field, clone burden, embedding entry point or
new plugin production bridge is needed; failed TypeInfo is never queried or validation resumed.
Native companion acceptance did require a correction to the existing diagnostic bridge, detailed below.

The first focused gate found two test problems: the refusal fixture accidentally supplied a valid
overload, and AssertJ selected primitive unboxing for a closed document's deliberately null version.
The fixture now uses an incompatible overload and the assertion explicitly accepts nullable `Int`.
Neither finding required weakening the production proof.

Remaining L63 tasks:

- [x] Carry detached local argument/result type identities into cross-owner typed initializer
  repairs. Implemented in the continuation below; same-owner support remains covered by regression tests.
- [x] Extend destination resolution to another configured source module with dependency direction,
  existing imports, visibility and source/binary ownership proved before edits; see the cross-module continuation below.
- [x] Prove insertion of missing destination imports without changing graph ownership or dependency direction
  (destination-import continuation below; final acceptance recorded there).
- [ ] Prove generic destinations/substitution, computed/chained receivers, runtime Class/Type
  values, explicit generic/singleton qualifiers, named/computed arguments and conditional signatures.
- [ ] Add missing types/properties, broader extraction/inline and global safe delete as independent
  compiler-proven transformations.


The combined backend gate passes **256 tests**, zero failures/errors/skips: 21 new cross-owner
cases, 77 existing missing-method cases, five compiler repair proofs and the broader extraction/
local/member/rename-server/code-action regressions. Both editor drivers compile. The versioned
server tests cover the caller at version 7 and a destination either closed (null version) or open
at version 13; only the destination appears in the edit.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Source destination and signature proof | `92eb89974` | Extends `c36eb87c6`; writable same-module destinations, explicit public action, detached signature proof, backend and versioned server regressions. |


The first native IntelliJ gate passed the preceding five selected cases but failed X202 before
applying its action. Focused repetitions exposed an exception in the branch's diagnostic bridge:
copying a report with `relatedDocuments` through the shared Gson bypassed our UP06 discriminator
and terminated the client message reader. The copy now constructs the root report explicitly,
preserving decoded companion reports. The new regression fails before the repair with the exact
`Ambiguous Either type` error; all **90 IntelliJ unit tests** pass after it, zero failures/errors/skips.
The later disposal exception is tracked separately as open **UP22**; removing our trigger does not
prove the upstream callback lifecycle repaired. See the [upstream register](errs-upstream-issues.md#up06up07-companion-report-copy-correction-2026-10-04).


Final acceptance and extraction additions:

- VS Code **`run-hOIsOh`** passes **X181/X185/X195/X197/X201–X204**, zero extension errors or
  test failures. The eight selected cases finish in about ten seconds.
- IntelliJ **`run-18176000411609180309`** passes **START and the same eight cases**, zero IDE
  failures; JUnit reports one passing suite test without failures/errors/skips. Ultimate is disabled.
  X202 completes companion apply and diagnostic Undo/Redo/Undo in **3,989 ms**. The accepted run
  has one server start and no severe, ambiguous-union, disposed-parent or compiler internal-error
  log markers. An intermediate post-fix run applied the edit but exposed an active-tab-only test
  locator; the driver now selects each tab before reading it, without replaying edits.
- Both editors use the **209-scenario** catalog SHA-256
  `880cac9c23f6581f825bebbfa5e2a6444fcdad76df80a77facd9521da34d9148`.
  These are selected acceptance runs, not a full catalog rerun. No new packaged-stdio gate is claimed.
- Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass. Backend production code remains
  at the tested commit; the diagnostic-copy fix additionally passes the full 90-test IntelliJ unit gate.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Existing IntelliJ diagnostic bridge correction | `124ae9c5a` | Depends on the UP07 bridge `f3de29b57`; preserve decoded full/unchanged companion reports, fail-before/pass-after unit regression, and separate UP22 upstream evidence. Independently extractable from missing-method generation. |
| Shared editor coverage | `0155a137a` | X201–X204 and both action drivers, with companion tab assertions and caller preservation; combines with the manual rows in this documentation checkpoint. |

Extract backend `92eb89974` after `c36eb87c6`. The native companion acceptance also needs
`124ae9c5a`; its transport correction belongs with the IntelliJ integration group. Keep shared
coverage and matching manual rows together when splitting PRs. Each extracted PR must still pass
independently. The next bounded L63 task is detached cross-owner local-argument and typed-initializer
result evidence before attempting cross-module dependency/import/ownership policy.


### L63 cross-owner local arguments and initializer results (2026-10-04)

- [x] Capture compiler-established local register types during the failed repair attempt and detach
  them through the existing identity collector. Keep rendered type text and identity together;
  no compiler type from the failed pool enters fresh declaration analysis.
- [x] Extend explicit public source-destination repairs to earlier block-local arguments and whole
  explicitly typed local initializers. Combine detached body identities with fresh declaration
  parameter/literal types and require exact final signature identities and argument bindings.
- [x] Add backend coverage for typed/var/val/separately assigned locals, instance/static/local
  receivers, empty/mixed arguments, companion files, source/parameterized/compound types and
  invalid/inferred/out-of-scope contexts. Add compiling parameter-widening, return-narrowing and
  argument-rebinding counterexamples to the proof tests.
- [x] Add shared X205–X208 and both editor drivers: inferred local argument, closed-companion
  parameterized initializer, shadowed result-type refusal and inferred-result refusal. Catalog: 213.
- [x] Complete the combined backend and selected editor gates and record receipts/commit mapping.

`renameFacts(includeMissingMethods = true)` copies the failed attempt's local facts only on the
repair path, while its model and constant-to-source associations are still owned by the same
worker. The existing shared identity collector produces detached `ProofIdentity` structures.
Fresh declaration types and detached local types have explicit sealed alternatives; no nullable
compiler-pool cache or new AST field/API is introduced. The complete proposed graph must still
compile, retain existing bindings and prove the inserted public method's signature/dispatch.

A local must precede the call in a containing block. Inferred locals need a validated, fitting
initializer; explicitly typed locals need their resolved register or fresh declaration type.
Uninitialized/out-of-scope/invalid locals fail the final graph proof. An initializer's declared
result type must be explicit; `var result = missing(...)` supplies no return-type evidence.
Destination shadowing is refused even when a narrower generated result would still fit the caller.

Remaining L63 tasks: cross-module destination ownership/dependency/import policy; generic,
computed/chained/runtime-type receivers and broader signatures; missing types/properties;
wider extraction/inline and global safe delete. UP22 remains an independent upstream lifecycle
follow-up. This continuation does not claim those tasks complete.


The combined backend gate passes **275 tests**, zero failures/errors/skips, across 14 classes.
It includes 39 cross-owner repair cases, 77 existing missing-method cases, six proof tests and the
existing local/extraction/member/server/code-action regressions. Both editor drivers compile.
No production fix was required after this first gate. Selected native acceptance follows below.


Final acceptance:

- **275 backend tests pass**, zero failures/errors/skips. Both drivers compile; no Java AST or
  IntelliJ/VS Code production change is needed in this slice.
- VS Code **`run-RpTVz1`** passes **X181/X185/X190/X202/X205–X208**, zero extension errors or
  test failures. The eight selected cases finish in eleven seconds.
- IntelliJ **`run-14627770467027596087`** passes **START and the same eight cases**, zero IDE
  failures. JUnit reports one passing suite test without failures/errors/skips; Ultimate is disabled.
  X206 completes companion apply and diagnostic Undo/Redo/Undo in **3,850 ms**. The accepted run
  starts one server, with no severe, ambiguous-union, disposed-parent or compiler internal-error
  log markers. UP22 remains independently open.
- Both use the **213-scenario** catalog SHA-256
  `f68713b3213859b9bc75f75719a8f3560a221de6a1289621c23339d9d3fb19fc`.
  These are selected native runs, not full-catalog acceptance. No new IntelliJ production-unit or
  packaged-stdio run is claimed; historical host-check entries do not count as new validation.
- Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Cross-owner body type proof | `272963cf0` | Extends `92eb89974`; detach failed-attempt local types through the existing identity collector, support explicit initializer results and retain exact signature/local-binding proof. Includes backend and compiling-counterexample regressions. |
| Shared editor coverage | `f400bc02b` | X205–X208 in both existing drivers; pair with their manual rows in this documentation checkpoint. Companion acceptance retains the existing diagnostic bridge fix `124ae9c5a`. |

Keep backend, shared scenarios and matching manual rows together when extracting their future
PRs; each extraction still needs independent validation. The next bounded L63 scope is destination
selection in another configured source module, with explicit dependency direction, source/binary
ownership, imports and public visibility proved before offering an edit.


### L63 cross-module missing-method destinations (2026-10-04)

- [x] Capture detached destination locations, insertion positions and existing module-import aliases
  from successfully compiled configured source modules. Match dependency compiler identities through
  their source index only when they have a corresponding reachable project-owned destination.
- [x] Support explicit public instance/static methods in ordinary non-generic classes, including
  closed dependency companions. Render parameter/result and compiler-established local types in the
  destination context; no caller alias, new import or new dependency edge is introduced.
- [x] Verify the generated signature against the exact source declaration selected by the completed
  caller, across snapshot-local symbol IDs. Preserve the whole-graph, known-binding, dispatch and
  local-argument proof; return a versioned edit of the destination only.
- [x] Add backend controls for source graph direction, binary/indexed/read-only targets, existing
  members, unsupported owners, broken closed consumers, destination imports, local/initializer type
  evidence, compatible-but-wrong signatures and open/closed destination document versions.
- [x] Add shared X209–X212 and both editor drivers, including closed-module/companion apply with
  Undo/Redo/Undo, reverse-dependency refusal and differing caller/destination import aliases.
- [x] Complete the combined backend and selected native gates; record receipts and extraction commits.

The existing graph compiler owns its mutable build-artifact and destination maps within one worker
invocation; neither map is shared between requests. Destinations contain source data only. Compiler
classes are reassociated from each freshly reopened repository and never retained with their AST or
constant pool. Failed-body locals carry detached identity plus destination-specific rendered text.
No Java AST change, embedding API extension, plugin production workaround or mutable cache is added.

An existing module-level package import can supply a third-module signature type; the final graph
and exact identity proof reject shadowing. A caller-owned type requiring a reverse dependency is
refused. A binary source index alone cannot authorize an edit, even when its source is writable and
inside the workspace. Only reachable, successfully compiled source destinations enter this path.
Consumers outside the configured graph remain outside the proof's coverage.

Remaining L63 work: adding missing destination imports; generic/substituted owners and conditional
signatures; computed/chained/runtime-type/singleton receivers and named/computed arguments; missing
type/property declarations; broader statement extraction/inline and global safe delete. UP22 is an
independent upstream lifecycle follow-up. This bounded slice does not complete L63.


The first combined run executed 296 tests and exposed 17 new-path failures with one root cause:
the new dependency member-collision check requested `TypeInfo` from the reopened artifact pool,
whose system-module fingerprints were not linked. This was an integration mistake in this slice,
not evidence of a separate master regression. The check now constructs the receiver type through
the fresh declaration attempt's linked caller pool. The focused follow-up passes all 41 tests
(19 cross-module, seven signature-proof and 15 server cases), zero failures/errors/skips.
Local type spelling is computed only for external destinations referenced by this failed attempt,
not for every class in every source dependency.


The final combined backend gate passes **296 tests in 15 classes**, zero failures/errors/skips,
including 19 cross-module cases, seven signature-proof tests, 38 same-module cross-owner cases,
77 original missing-method cases and 15 server cases. Both editor drivers compile. Existing
local/extraction/member/code-action regressions are included. Selected native acceptance follows.


Final acceptance:

- **296 backend tests pass**, zero failures/errors/skips; both editor drivers compile.
- VS Code **`run-B54Y1R`** passes **X201/X202/X205/X206/X209–X212**, zero extension errors or
  test failures. The eight cases finish in fifteen seconds.
- IntelliJ **`run-14965910820534049086`** passes **START and the same eight cases**, zero IDE
  failures. JUnit records one passing suite test, zero failures/errors/skips. Ultimate is disabled.
  X209/X210/X212 complete in 5,690/5,612/4,992 ms, including exact source and diagnostic Undo/Redo.
  The accepted run starts one server and contains no ERROR/SEVERE, ambiguous-union,
  disposed-synchronizer or ProcessCanceledException markers.
- The first native attempt, **`run-6617035480724888358`**, passed START and the four existing
  cases but stopped at X209 before applying an edit. Overlapping full diagnostic pulls cancelled
  a stale lazy action during a synchronous remote popup call. The harness now queues discovery
  through the normal UI action path; bounded retries still cannot replay an accepted edit. The
  production diagnostic bridge is unchanged. Evidence and removal obligations remain under
  [UP07](errs-upstream-issues.md#up07-native-cancellation-during-cross-module-quick-fix-discovery-2026-10-04).
- Both editors use the **217-scenario** catalog SHA-256
  `b26e3dcd0d89e0f0981d50d293477cde14ae6e2c6670c97fd2e308da81121f2e`.
  These are selected runs, not full-catalog acceptance. No new IntelliJ production-unit or
  packaged-stdio run is claimed; older host XML entries remain historical evidence.


Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Cross-module source destination and signature proof | `a75834c93` | Extends `272963cf0`; detached source ownership, existing destination imports, fresh linked-pool member inspection, exact cross-snapshot signature proof and backend/server regressions. |
| Native popup discovery cancellation | `67f44cd33` | Independently extractable harness correction and UP07 evidence; queues cancellable intention inspection, retaining bounded retries and no edit replay. No plugin production API change. |
| Shared editor coverage and manual steps | `bab7048bd` | X209–X212, both drivers, extra dependency fixtures, matching manual rows and catalog counts in one commit. Depends on the backend slice and retains the existing diagnostic bridge correction `124ae9c5a`; native acceptance uses `67f44cd33`. |

This documentation checkpoint records the capability boundaries and receipts. Keep the shared
scenario/manual rows together when extracting PRs; every extracted PR still requires independent
validation. All work remains on `lagergren/errs`; no remote operation is part of this checkpoint.
The next bounded L63 step is adding required destination imports when the dependency already exists,
while retaining alias-collision, ownership, complete-graph and exact-signature proof.


### L63 destination import insertion (2026-10-04)

- [x] Plan destination module aliases from its existing imports and host-configured dependencies
  (plus the bundled XDK). Reserve names across its source tree and reuse the member-action alias
  allocator. Never borrow the caller's dependency edges.
- [x] Carry required imports with fresh/detached signature spelling. Insert only imports actually
  used by the method's parameter/result types, deduplicated across repeated and compound types.
- [x] Apply imports and method as one versioned workspace edit, including root-plus-companion edits. Adjust
  the inserted-declaration proof for preceding import edits and retain exact signature/binding proof.
- [x] Add regressions for import permission, aliases, compound/local types, closed companions,
  unused dependencies, CRLF and destination document versions. Add shared X213–X215 and both drivers.
- [x] Finish combined backend and selected editor acceptance; record receipts and extraction commits.

The package import belongs inside the destination module root; a companion admits only one
top-level declaration. When the method belongs in a companion, the same atomic workspace edit
changes both files and carries each document's own version (or null for a closed file). Existing source/import names are reserved before selecting aliases; complete proposed
compilation and binding preservation remain mandatory. No source graph, AST node or embedding API
is changed. Reopened library pools still supply identity indexes only; TypeInfo uses the fresh
caller's linked pool. Imports and detached local type spelling contain no compiler-owned objects.

Final validation:

- **310 backend tests pass** in 15 classes, zero failures/errors/skips. The batch includes 28
  cross-module missing-method cases, eight proof tests and 19 server tests, plus the existing
  extraction, local, member-action and code-action regressions. Both editor drivers compile.
- VS Code **`run-u5kDXk`** passes **X122 and X209–X215**, zero test failures or extension errors;
  eight cases complete in 25 seconds. X214 verifies exact root and companion contents through
  Undo/Redo/Undo. The catalog has **220 scenarios**, SHA-256
  `49bb9eb0b8c900f5a08e4ab0ace289150e2eec2b196f96db13bce5ded46b09ac`.
- The initial backend batch exposed invalid top-level package insertion in companions. The repair
  now writes imports into the module root and refuses the entire action if that root is read-only.
  A separate fixture used an invalid two-component module name; the collision regression now uses
  valid `Types.one.org` and `Types.two.org` modules. The final backend batch includes both corrections.
- IntelliJ's first attempt, `run-11810269263038848645`, passed START/X209 and stopped at X210's
  newly added extra-file comparison. That check opened the shared `Library.x` instead of the
  case-relative `X210/Library.x`; the driver now prefixes the scenario directory. The method edit
  had already passed its exact-source check. This was a harness path error, with zero IDE failures.
- The next attempt, `run-4191909342836480788`, passes START/X209–X212 but X213's native popup
  remains absent after a successful server action reply and overlapping diagnostic refresh/cancellation.
  The harness now applies existing workspace-case tab isolation to all independent discovered
  fixtures. [UP07](errs-upstream-issues.md#up07-explicit-graph-quick-fix-transition-2026-10-04)
  retains the production transition with unrelated broken tabs as unfinished acceptance.
- Tab isolation alone passes X213 in `run-4352512895301591077` but X214 still loses its popup
  before applying an edit. Explicit-graph fixtures now install their graph before opening the
  caller, and unchanged fixture text is checked rather than rewritten. Discovery fixtures still
  open first because their workspace-folder notification requires a started server; the setup
  regression caught in `run-14011335717437201168` established that ordering requirement.
- IntelliJ **`run-4588144426201480586`** passes **START and X122/X209–X215**, zero IDE failures.
  X213/X214 complete in 4,659/5,693 ms, including exact root/companion contents and diagnostic
  Undo/Redo/Undo. JUnit records one passing suite test with zero failures/errors/skips. Ultimate
  is disabled; the accepted IDE log has no ERROR/SEVERE or ProcessCanceledException markers.
  Both editors use the same 220-case catalog hash above.

These are selected acceptance receipts, not a full catalog, fresh IntelliJ production-unit or
packaged-stdio gate. UP07's broader production graph-replacement race remains open; the fixture
isolation/setup correction does not claim to repair it.

Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass.

| Extraction group | Commit | Scope |
| --- | --- | --- |
| Missing-method destination imports | `f6a27ed75` | Extends `a75834c93`; detached import plans, shared alias/rendering helpers, atomic root/companion edits, exact shifted-signature proof, backend and server regressions. No Java AST or embedding API change. |
| Shared editor acceptance and fixture setup | `fc3408a61` | X213–X215, both drivers, atomic extra-file assertions, matching manual rows/catalog counts and UP07 evidence. Includes fixture path correction, tab isolation and explicit graph-before-open setup; preserve discovery server startup before workspace notifications. Depends on the backend slice. |

Keep scenario data, both drivers and manual rows together when extracting. Each extracted PR still
requires independent validation. This checkpoint is local on `lagergren/errs`; it does not push or
open a PR. The next bounded L63 implementation area is generic destination ownership/substitution.

### Consolidated remaining L63 scope (2026-10-04)

L62's compiler closure and selected acceptance are recorded below. The user explicitly accepted
carrying UP23 as a VS Code host limitation and continuing with this eight-part L63 batch.
Each area retains its own implementation checkpoint and tests; combined validation follows the batch.

The destination-import slice left **eight work areas**. All now have bounded implementations,
regression tests and selected acceptance in both editors. Checked entries mean the supported forms
in the [closure table](#l63-bounded-closure-and-acceptance-2026-10-04), not arbitrary transformations.
The first four extend missing-method support; the last four are other L63 transformations.

- [x] Generic destinations: prove receiver substitution and which declaration form belongs in
  generic owners, including outer formals; retain exact types and writable-source ownership.
- [x] Broader signatures: method formals/constraints and conditional returns, including compiler
  evidence for the intended result context. Do not guess a signature from diagnostic text.
- [x] Broader receivers: computed/chained, runtime Class/Type and singleton forms, where the compiler
  can establish a concrete source owner and correct instance/static dispatch.
- [x] Broader arguments: named and computed arguments, with proven types, name mapping and bindings.
- [x] Missing type/property declarations: independent creation actions with ownership, visibility,
  initialization and complete proposed-graph proof.
- [x] Broader extraction: statement selections and additional evaluation contexts with control-flow,
  return, capture and evaluation-order preservation.
- [x] Broader inline: methods and wider local/property contexts with side-effect, capture and
  evaluation-order preservation.
- [x] Global safe delete: prove references across the configured graph and reject unknown external
  ownership/consumers; offer only deletions whose complete proposed graph remains valid.

Independent UP07/UP22 host integration follow-ups, wider LSP scopes and release/soak acceptance are
tracked separately; they are not hidden within this eight-area L63 count.


### L62 ownership and relocation closure batch (2026-10-04)

L62 takes priority over the pending eight-part L63 batch. Four local implementation checkpoints
precede combined backend/protocol/editor acceptance:

| Checkpoint | Scope |
| --- | --- |
| `762f99f44` | Reassign captured companion sources across configured modules, add capture-free module imports and propose required dependency changes. |
| `267e535a8` | Apply independently relocated children before their parent; coalesce children following the exact parent mapping. IntelliJ accepts the ordered transaction. |
| `68b2fd73f` | Capture incoming resource membership and bytes, replay proposed lookup paths and reject changed input snapshots. |
| `e187a6359` | Copy exact numeric/compound annotation values and nested/service receiver ownership into detached rename proof. |

Validation corrections belong with these checkpoints: new package-import declarations acquire
inherited methods and are excluded only from the *additional declaration* dispatch comparison;
every existing binding, import terminal and callable route still compares. Destination modules
must capture their own resource roots even when the incoming source introduces their first resource
expression. No Java AST state, public compiler accessor or embedding API is added.

Shared X216–X220 cover cross-module imports/dependency persistence and companions, cyclic proposal
refusal, overlapping parent/child moves, incoming resource preservation, and numeric annotation
family rename. Both drivers implement the same fixtures with native history checks where applicable;
X217 checks the installed protocol refusal and does not claim that VS Code can veto Explorer moves.
The acceptance receipt below records the combined gate, selected editor results and UP23 exception.

The intended closure boundaries are explicit:

- Edit only configured/discovered writable source owners and their registered consumers. Binary
  declarations and unregistered consumers do not acquire writable ownership from source indexes.
- Preserve all bindings, dispatch and embedded resource values after complete proposed-graph
  compilation. Cycles, inaccessible dependencies, collisions and capture refuse the whole proposal.
- Existing compiler-owned destination namespaces are required. Symlinks, occupied destinations,
  incoming paths under moving sources and transactions needing temporary staging remain refused.
- Native/generated/dynamic routes without a proven written contract remain refused. Deferred values,
  runtime handles and filesystem annotation objects are not guessed from display text.
- Compound annotation constants work in validated method-body type expressions. The ordinary
  compiler rejects the tested array/map/range/tuple expressions in annotated method headers with
  COMPILER-30; static property references there also remain unresolved. Typed numeric literals work
  in headers. The body and typed-literal regression fixtures exercise valid compiler inputs; this
  batch does not claim a compiler header-expression recovery fix.

L62 validation correction `79bfe6658` belongs with these four checkpoints when extracting PRs.
Keep its fresh-import dispatch exclusion, destination resource snapshots and exact annotation
regressions with their corresponding implementation slices; the four earlier commits alone are
not the validated end state.

Acceptance:

- Combined backend/protocol gate: **178 tests passed**, zero failures/errors/skips. IntelliJ
  Move target/document-lifecycle unit tests: **7 passed**. Both editor drivers compiled.
- After the final resource capture/refusal guard, the affected type move, graph move and callable
  proof suites ran again: **58 passed**, zero failures/errors/skips.
- IntelliJ `run-8069330970170232500`: X169/X173/X216–X220 all pass, including native Move/Rename,
  companions/resources, graph settings and Undo/Redo. Startup passes too.
- VS Code `run-S7CfWP`: six selected cases pass; **X218 fails during native Undo**. The earlier
  `run-VfDauh` records parent-first apply failure. Neither receipt is an all-green VS Code run.
  [UP23](errs-upstream-issues.md#up23-overlapping-vs-code-file-moves-retain-the-hosts-order) records
  the host's forward inverse ordering; X218 deliberately retains its failing assertion.
- Root/LSP/IntelliJ read-only Spotless and `git diff --check` pass. This is selected acceptance,
  not a new full-catalog or release/soak receipt.

The user explicitly chose **record UP23 and continue to L63**. L62's compiler implementation
is closed within the boundaries above; VS Code overlapping-move Undo remains an open upstream
acceptance item. No safe native Undo is advertised for that operation. The catalog now contains
225 scenarios (X1–X220, CFG1–CFG3 and 7a.8–7a.9).

### L63 generic destinations continuation (2026-10-04)

The first checkpoint removes the blanket generic-class destination exclusion. Concrete caller
types remain concrete in the new signature; equal actual types do not justify guessing an owner
formal. The shared type renderer now spells exact class-formal property identities owned by the
destination or its lexical enclosing classes. Unrelated same-named formals remain refused.
Fresh linked declarations, complete graph repair and exact public-signature proof remain required.
Regressions cover generic closed companions, enclosing class formals and unrelated formal capture.
Validation is batched with subsequent L63 work; this note is not a passing receipt or closure of
the full generic-substitution/signature scope. No compiler AST/API changes are needed.

### L63 method signature continuation (2026-10-04)

Missing-method proposals now declare the caller's method formals and their compiler-resolved
constraints in the new method. Exact signature comparison alpha-renames these binders by ordinal
and compares all constraints/uses; unrelated owner formals still cannot escape by spelling.
Whole conditional-return calls retain the conditional modifier and payload, including cross-owner
creation. Typed initializer and statement calls keep their own ordinary result contexts. Private
proposals now receive the same exact signature check as public proposals. Tests cover constrained
formals, nested generic results and conditional results in private and companion destinations.
This separate checkpoint awaits the combined L63 validation gate.

### L63 receiver continuation (2026-10-04)

Validated computed and property-chain receivers now supply their concrete source class, alongside
register receivers and direct class names. The failed attempt contributes only detached ownership
evidence; it is never resumed for TypeInfo. Source const/service owners and written singletons
can receive methods with compiler-established instance dispatch. Unknown runtime Class/Type values,
binary owners and ambiguous relational receivers remain refusals. Tests cover returned/constructed
receivers, property chains and static const/service singletons. Combined validation is pending.

### L63 argument continuation (2026-10-04)

Named call arguments now supply parameter names, while generated positional names avoid label
collisions. Duplicate labels refuse creation. Validated argument expressions contribute detached
exact types and destination import spellings; proposals do not move, duplicate or evaluate them.
Complete graph compilation and binding/signature checks still gate publication. Tests cover
arithmetic, calls, property reads, mixed named/positional inputs and duplicate-label refusal.
The four shared missing-method checkpoints now receive one combined backend gate before further
refactorings build on them. Native editor acceptance remains batched with the remaining L63 scope.

### L63 missing declaration continuation (2026-10-04)

Adds independent class and read-only-property creation actions. A bare, zero-argument unknown
constructor can propose a same-module class. An unresolved whole return value can propose a
same-owner getter with an explicit TODO body and the enclosing result type. These actions never
guess constructor fields, external ownership or a writable property's initialization policy.
The proposed graph must compile, preserve all previously resolved bindings and bind the selected
use to the inserted declaration; getter use types must match the original return type exactly.
Regression tests cover successful repairs, instance getters, static-property refusal, unsupported
constructor requirements and unrelated source errors. Static properties are compiler constants,
require an initializer and cannot contain a custom getter (VERIFY-57/60); a quick fix does not
invent an initializer. Header and body snapshots may both represent the same source, so discovery
combines their candidates and deduplicates before compiling the proposal.

The combined missing-method/member/declaration/extraction backend gate passes **247 tests**, zero
failures/errors/skips. This includes all seven missing-declaration regressions. Editor acceptance
and shared scenarios for this batch remain pending.

### L63 statement and nested-expression extraction (2026-10-04)

Extraction now accepts contiguous expression statements and nested expressions such as call
arguments, conditions and inferred initializers. It preserves call order, stable captures,
compiler-selected result types and all existing graph bindings/dispatch. Statement helpers return
void. Intervening control flow, local declarations, mutable captures, closures, async calls and
unsupported ref/super operations remain explicit refusals; no data-flow result is invented.

Source type spelling reuses resolved module imports plus the compiler's `ECSTASY_MODULE` and
`X_PKG_IMPORT` constants. This is source naming, not an XDK module-path allowlist. Extraction tests
cover the same imported type from configured source and compiled binary dependencies. The new
module-import lookup also replaces duplicated missing-method import discovery.

All **26 extraction tests** pass as part of the **247-test** combined gate above, with no skips.
`b5632cc7a` corrects and validates missing-declaration checkpoint `1ff71acfe`; keep them together
when extracting that PR. New editor scenarios and native acceptance remain pending for the batch.

### L63 wider constant-local inline (2026-10-04)

A compiler-proven constant local with exactly one read can now be inlined across intervening
statements and into nested expressions. The copied expression is parenthesized, retains its exact
compiler type and must preserve every remaining binding and dispatch chain. Declaration comments,
contextual type widening, repeated reads, writes, reference storage and deferred side effects
refuse the action. The existing adjacent single-evaluation inline stays available.

All **21 local-inline regressions** pass with zero failures/errors/skips. This is the local-variable
part of the broader-inline area; method/property inline and graph-wide safe delete still need their
own implementation and acceptance. The extraction checkpoint is `161111d69`.

### L63 selected method and constant-property inline (2026-10-04)

A selected call to a private, same-owner, zero-parameter method containing one returned expression
can be expanded at its existing evaluation site. A private static constant read can similarly use
its initializer. Both actions retain the declaration and parenthesize the copied expression.
Complete compilation verifies the exact use type, every copied binding/call and unchanged dispatch.
Caller name capture, public/parameterized/multi-statement/recursive methods, qualified receivers,
closures, annotations and instance-property initialization/getters remain refused. No AST state or
embedding API is added.

The **10 member-inline** and **21 local-inline** regressions pass, zero failures/errors/skips.
The local-inline checkpoint is `3fde8fca2`. Shared editor scenarios/native acceptance remain pending.

### L63 configured-graph safe delete (2026-10-04)

Adds a separate versioned safe-delete action for unreferenced private methods and static constants.
Reference discovery includes every captured source view and call target. Complete graph compilation
must succeed after deletion; every surviving edge and dispatch chain remains identical. Only the
selected declaration's exact, supported singleton chain can disappear. Public/protected APIs,
instance properties, annotations, unresolved/dynamic routes, comment loss and broken graph neighbors
remain refused. This does not claim discoverability of consumers outside configured ownership.

All **10 safe-delete regressions** pass, zero failures/errors/skips, including parameter/body-edge
removal, retained live calls, known closed consumers and unknown public consumers. The selected
method/property inline checkpoint is `de86a5afd`. Shared editor cases and the final combined gate
remain pending; L63 is not yet marked accepted.

### L63 shared acceptance preparation (2026-10-04)

X221–X240 cover all eight implementation areas, including successful edits and explicit refusals,
exact source text, error/clear restoration and Undo/Redo/Undo. Both editor drivers use these same
fixtures and selection offsets. VS Code now converts the selection end through document offsets
for multi-line statement selections. The catalog has **245 scenarios**; X218 remains the explicit
UP23 upstream failure and is not part of this new text-edit acceptance selection.

All **20 shared-scenario backend tests** pass, zero failures/errors/skips, and both editor drivers
compile. This validates fixture content and expected edits before native runs. The final combined
regression gate and native acceptance remain pending.

| Extraction group | Commit(s) | Scope |
| --- | --- | --- |
| Generic owners | `ef4b298e5` | Exact owner/outer formal rendering, concrete generic destination signatures. |
| Broader signatures | `0baa91a85` | Method formals/constraints and conditional return shape. |
| Broader receivers | `fbd5247ba` | Validated computed/chained and singleton receiver ownership. |
| Broader arguments | `2e521970c` + `a1d52a42d` | Named/computed expression types and corrected acceptance/refusal fixtures. |
| Missing declarations | `1ff71acfe` + `b5632cc7a` | Missing class/instance-property creation, deduplicated header/body attempts and static-constant refusal. |
| Broader extraction | `161111d69` | Statement/nested-expression extraction and shared compiler module-alias rendering. |
| Broader inline | `3fde8fca2` + `de86a5afd` | Constant locals and selected private method/constant-property uses. |
| Safe delete | `d3ef9d715` | Complete configured-graph proof for unused private methods/constants. |

Keep the associated correction and shared acceptance commits with these groups when extracting.
Each future PR must validate independently; these local commits have not been pushed.

### L63 acceptance corrections (2026-10-04)

The expanded regression gate ran **494 tests** and found one regression: generic-method parameter
rename was refused because the newly detached generic binder and the first visible value parameter
shared `Parameter(method, 0)`. Generic binders now use a separate `MethodFormal` identity; signature
alpha-equivalence remains exact. The existing parameter-rename test reproduced the defect before
this correction and passes afterward.

The affected follow-up gate passes **185 tests**, zero failures/errors/skips: generic/missing-method
proof, parameter/server rename, type moves, inline/delete and **22 shared scenarios**. It also checks
that static runtime initializers are neither duplicated nor discarded. Both editor drivers compile;
formatting passes. Native acceptance is the next gate. The catalog now has **247 scenarios**,
including X241/X242 runtime-initializer refusals. Restore the existing JSON escape style when
extracting the fixture commit; its temporary unescaped Unicode rewrite was unrelated to this scope.

### L63 bounded closure and acceptance (2026-10-04)

All eight implementation areas are accepted within the following explicit boundaries. The compiler
adapter still requires complete proposed-graph compilation, exact types/bindings/dispatch and
versioned edits. No new Java AST state, compiler public accessor or embedding API was needed.

| Area | Supported | Deliberate refusal |
| --- | --- | --- |
| Generic destinations | Exact destination/lexical outer formals; concrete caller types in generic owners. | Guessing an inverse actual-to-formal substitution or using an unrelated same-named formal. |
| Broader signatures | Method formals and constraints, exact conditional result shape, ordinary typed initializer/statement contexts. | Unproven expected results or type/constraint spellings. Generic binders have identities distinct from visible runtime parameter slots. |
| Broader receivers | Compiler-validated computed/chained receivers, writable source owners and singleton instance dispatch. | Runtime Class/Type values without an exact source owner, ambiguous relational receivers and binary destinations. |
| Broader arguments | Named labels and compiler-typed computed expressions, copied without evaluation or relocation. | Duplicate labels or expressions without a proven usable declaration type. |
| Missing declarations | Bare zero-argument same-module classes and same-owner instance getters for unresolved whole return values. | Invented constructor fields/arguments, ownership, generic signatures or static-property initializers. |
| Extraction | Nested expressions and contiguous expression statements, private helpers with stable inputs and exact result types/import aliases. | Local-declaration/control-flow outputs, mutable captures, closures, async/ref/super operations. |
| Inline | Existing adjacent single-use locals; wider single-read compiler constants; selected private same-owner zero-parameter returned-expression calls and compile-time constant property reads. | Parameter substitution, recursion, qualified receivers, capture, annotations, multiple statements or runtime initialization. Member declarations are retained. |
| Safe delete | Unreferenced private ordinary methods and compiler-proven static constant values after configured-graph reference/call proof. | Public/protected APIs, unknown external consumers, instance/runtime initialization, annotations/comment loss or unsupported dynamic routes. |

A static property is not necessarily a compile-time value. The detached constant-property set
requires an actual resolved, nondeferred constant and no runtime initializer method. X241/X242
and backend regressions verify that inline cannot repeat initialization and delete cannot discard it.
The implicit `ecstasy` alias uses `ECSTASY_MODULE`/`X_PKG_IMPORT`; other type spellings come from
resolved imports. This is not a module-path whitelist. Source and binary dependency fixtures both
verify imported `lib.Value` extraction.

Final validation:

- Combined backend/protocol gate: **498 tests in 31 suites passed**, zero failures/errors/skips,
  including the existing rename/move/proof suites and all 22 new shared fixtures. The earlier
  494-test run found the generic-binder/value-parameter identity collision; this final run includes
  its correction and must be used instead of that failed receipt.
- VS Code `run-d056KI`: X222–X242 pass; X221 was canceled during a provider lookup before any edit.
  `run-RaIOul` reproduced that cancellation. The harness now retries only that read-only lookup
  within its existing deadline; canceled queries cannot satisfy refusal checks. Edits and history
  are never replayed. `run-TGD4Wh` passes X221. All three editor error arrays are empty; the first two
  receipts remain failed runs, not all-green receipts.
- IntelliJ `run-9429307608964703578`: startup and X221–X238 pass. X239 timed out because checking
  the dependency file changed the selected tab; the caller diagnostic locator still expected the
  caller editor. The harness now selects the caller before inspecting its diagnostics.
  `run-13805036479960116063` passes startup and X239–X242. Both runs have no IDE failures.
- These receipts collectively cover **X221–X242 in both editors**, including exact text, diagnostics,
  refusal controls and Undo/Redo/Undo where edits apply. VS Code uses installed provider/action APIs
  and native history; IntelliJ drives its installed actions. This is selected acceptance, not a new
  full-catalog, physical-menu-parity or release/soak run. The catalog contains **247 scenarios**.
- Final root/LSP/IntelliJ read-only Spotless checks and `git diff --check` pass.

Commit extraction must include the acceptance corrections with their implementation groups:

| Correction/acceptance commit | Keep with |
| --- | --- |
| `bf2be7268` | Shared X221–X240, both drivers, manual rows and backend fixture checks, split with the relevant eight implementation groups above. |
| `2e42006f1` | Generic signatures: distinguish `MethodFormal` from `Parameter`, including existing parameter-rename regression coverage. |
| `f18c4e847` | Inline/delete constant-initializer proof and X241/X242; keep the explicit ignored-call proof correction with those transformations. Preserve the existing JSON escape style. |
| `780aa76ac` | Editor acceptance: complete catalog assertion, canceled read-only provider lookup retry, caller-tab selection after dependency inspection and precise X239 instructions. |

Each extracted PR must pass independently. L63's bounded implementation is closed; UP07/UP22/UP23,
wider protocol scopes and release/soak gates remain separate. In particular this text-edit batch
neither changes nor claims to repair VS Code's overlapping file-move Undo failure (UP23).

### L66 import source continuation (2026-10-05)

Wildcard imports now link their resolved container. The initial conditional-import fixture exposed
that the current parser does not construct conditional imports from source at all: its latent AST
COMPILER-29 warning is not a supported source path. X244 explicitly verifies ordinary parser
diagnostics and no guessed links. This slice does not add conditional compilation syntax.
Missing/ambiguous/binary-only source targets still have no guessed link. Shared X243/X244 and backend
regressions cover these forms; validation is batched after all four L66 implementation slices.

`ImportStatement.getQualifiedNameTokens()` exposes an immutable copy of the existing written name
tokens, excluding alias/star. This is syntax ownership in the AST, not LSP state or a new cache; it
lets wildcard links retain exact source ranges without reparsing names. No mutable field or clone
responsibility is added.

### L66 linked editing continuation (2026-10-05)

Lambda parameters and normalized nested captures now join lexical local variables in document-only
linked editing. Callable parameter slots, constructor/property contracts and members remain on the
complete graph Rename path because named callers can be outside this document. Explicit aliases
whose name equals the imported terminal are recognized from token ownership. Unsupported conditional
source syntax has no complete semantic snapshot or linked ranges. Read-only indexed sources refuse editing.
Shared X245–X247 and backend regressions cover exact ranges, sibling separation, UTF-16/CRLF, broken
replacement and close. Combined testing remains after all four L66 slices. Import checkpoint:
`b5bf3ebe9`. No AST field or new semantic cache is introduced.

### L66 structural recovery continuation (2026-10-05)

Selections now omit duplicate AST spans and select the most specific source-owned child at an
overlapping/boundary position, without escaping its parent's written extent. Typedefs now appear
in the structural outline using their existing AST name and span. Shared X248 exercises damaged
headers, calls, list/tuple literals, folding, strict selection expansion and repair; backend tests
repeat with LF/CRLF/bare CR and UTF-16 comments, then close the document. Existing parser recovery
is reused; no Java AST change is introduced here. Linked-editing checkpoint: `2383ed9ec`. All
validation remains batched after the formatter slice.

### L66 formatting continuation (2026-10-05)

The Java-lexer formatter now uses the existing indentation/continuation/margin settings for operator
continuations and width wrapping at expression or delimited-list token boundaries. Standalone block
comment margins shift together; relative interior layout remains unchanged. Literal/template bytes
are never rewritten. Every proposal is re-lexed to verify identical code/literal tokens and comment
content apart from leading margins. On-type indentation does not introduce line wrapping. Range
formatting changes only the requested lines and keeps their original line-ending convention.

Shared X249/X250 check installed format actions, exact output, repeat stability and Undo/Redo/Undo;
backend regressions cover LF/CRLF/bare CR, range boundaries, margin changes, literal preservation
and lexical refusal. Typedef/selection checkpoint: `db8fefb53`. The four implementation checkpoints
now precede one combined backend gate and selected editor acceptance. Arbitrary comment reflow,
literal splitting, brace relocation and type-argument wrapping remain outside these safe rules.


### L66 bounded closure and acceptance (2026-10-05)

All four implementation slices were committed before batched testing. The supported/refused boundary
is now explicit:

| Area | Supported and checked | Deliberate boundary |
| --- | --- | --- |
| Import links | Compiler-resolved source modules/types and wildcard containers, with exact written ranges. | Missing/ambiguous/binary-only targets have no guessed source. Conditional import source syntax fails in the existing parser; X244 checks the refusal. |
| Linked editing | Source-local variables, lexical lambda parameters/nested captures and explicit import aliases, including aliases with the same terminal spelling. | Callable parameter slots, primary-constructor/property contracts and members require graph Rename. Broken/replaced/closed or read-only snapshots provide no editable linked ranges; linked editing does not prove a proposed new name. |
| Structure | Typedef outlines and source-owned, distinct, strictly nested selections; damaged header/call/list/tuple structure with repair. | Existing Java parser recovery remains authoritative; no synthetic semantic targets or new recovery grammar is added. |
| Formatting | Configured block/continuation indentation, bounded expression/list wrapping, standalone comment margins, range isolation, line-ending preservation and native history. | No literal/template rewriting, comment reflow, declaration alignment, operator-spacing normalization, type-argument wrapping or arbitrary brace relocation. Unbreakable tokens can exceed the target width. Lexically invalid source refuses edits; on-type requests do not wrap. |

Four-space block and eight-space continuation indentation are defaults, not embedded formatter
constants. IntelliJ supplies Ecstasy Code Style (including its right margin); VS Code supplies
`xtc.formatting.*`, with `editor.tabSize` defining tab width. Standard LSP options provide the fallback
when no editor configuration exists. Custom two-space/six-space-continuation and tab wrapping now have
explicit compile/idempotence regressions. `maxLineWidth` is active for the compiler adapter and is no
longer presented as an unimplemented/deprecated setting. No `xtc-format.toml` support is claimed.

Validation:

- Combined backend gate: **129 passed**, zero failures/errors/skips; both editor drivers compiled.
  The initial failures found an invalid typedef fixture and unsupported conditional-import grammar;
  those fixtures were corrected instead of adding or claiming unsupported language syntax.
- Final focused formatter gate: **7 passed**, including custom indentation/tabs and compact-wrap
  idempotence. Packaged stdio formatting/range/save and recovered folding: **2 passed**, zero skips.
  The first stdio invocation omitted `-Plsp.adapter=compiler` and failed its adapter precondition;
  the corrected invocation passed both tests.
- VS Code `run-BFaPcC`: ten of twelve selected cases passed; X249/X250 exposed a fixture newline
  assumption. `run-zy8tI6` then exposed the native no-edit command's `undefined` result. Fixtures now
  preserve their existing newline, and the driver checks an empty protocol edit list plus repeat
  native Format and Undo/Redo/Undo. `run-3DR6vE` passes both corrected cases. The extension suite
  `run-DzVUde` passes **23 tests**, including settings metadata and 40 compiler error/recovery cycles.
- IntelliJ `run-12440566880428489550`: ten of twelve selected cases plus START passed. X249/X250
  exposed a real standalone-formatting Redo defect in the platform's undo-transparent async path.
  The local repair is `60b5acf9e` and [UP24](errs-upstream-issues.md#up24-asynchronous-intellij-formatting-loses-redo).
  `run-14744074393597958558` passes START plus X249/X250/X139/X132, including exact native history and
  closed-document save formatting. Both runs recorded zero IDE failures.
- Final root/LSP/IntelliJ read-only Spotless checks and `git diff --check` pass.
- Collectively **X132/X138/X139/X158/X243–X250 pass in both editors**. This is selected acceptance in
  the **255-case catalog**, not a full-catalog rerun, cross-platform release or soak claim. Earlier
  failed receipts remain intact. UP07/UP22/UP23 and unrelated release gates remain separate.

Commit extraction map:

| Implementation checkpoint | Extraction group | Required acceptance corrections |
| --- | --- | --- |
| `b5bf3ebe9` | L66 import links and the additive `ImportStatement` token accessor | Include the later conditional-source refusal correction. The original commit title's conditional-link wording must not be used as a source-language support claim. |
| `2383ed9ec` | L66 lexical lambda and same-name alias linked editing | Include formatted predicates/tests and shared X245–X247. |
| `db8fefb53` | L66 typedef outline and strict recovered structure | Include valid `typedef Int as Alias` fixtures, selection driver typing and X248. |
| `9f27e5293` | L66 configured continuation/wrapping/comment-margin formatter | Include nesting at each wrap boundary, custom-indent/tab tests, active margin setting/UI, shared newline policy and native idempotence/history corrections. |
| `60b5acf9e` | IntelliJ asynchronous formatting history / UP24 | Keep with native formatting acceptance; compiler-only extraction does not need this editor repair. |

Acceptance checkpoint `998267de6` contains the corrections above and this receipt. Extracted
PRs must include their applicable corrections and pass independently. The only new Java AST API in
L66 is an immutable copy of existing import-name tokens; all other implementation stays in the LSP
or editor libraries. No new mutable AST state, semantic cache or clone responsibility is introduced.

### L67 module navigation index (2026-10-05)

Implementation sequence; combined validation follows the checkpoints:

- [x] Keep one detached navigation build per configured root, keyed by exact source/resource inputs
  and dependency artifact/source-index revisions. Reuse unaffected roots across graph changes,
  evict removed roots, fence stale publication, and keep complete references unavailable beside
  failed modules. Refactoring proofs continue to compile independently.
- [x] Seed the index from editor and diagnostic compilation so the first graph lookup does not
  compile the same successful module again.
- [x] Exercise scale, compiler-object release, binary/source-index replacement, ambiguous library
  source matches and editor-visible replacement scenarios (X251); execution and measurements below.
- [x] Run the combined backend and selected editor acceptance batch, update capability/playbook
  receipts and commit extraction mapping, and decide whether disk persistence is justified.


The 129-module control exposed an in-query heap failure under the unchanged test heap. `ModuleInfo`
caches its parsed source tree, so compiling the graph's captured `XdkSources` directly retained
all earlier ASTs/pools until the query returned. A post-query collection test alone missed this.
Compilation now receives a fresh replay of the immutable captured inputs in navigation, diagnostics
and editor paths. Dependency compilation also consumes intermediate analyses lazily, retaining only
the target editor AST. The regression checks compiler-object release halfway through navigation and
diagnostic graph compilation as well as after completion. No compiler AST or embedding API changed.


Extraction map (keep the validation corrections with their owning slices):

| Slice | Commits | Notes |
| --- | --- | --- |
| L67 detached navigation reuse | `788e84ea1`, `0af29295b`, `b2490aa2e`, `765fd1eb7` | Module keys, editor/diagnostic seeding, one atomic publication generation and fresh attempt-owned ModuleInfo inputs. Include the hardening correction; the initial checkpoint alone retains compiler trees during graph compilation. |
| L67 acceptance | `570e45bba`, `b2490aa2e`, `765fd1eb7`, `9893f57c1` | X251 in both drivers, exact compile counts, resources, 33/129-module scale and compiler-object release during/after queries. |
| L63 assertion correction | `55070033e` | Reject an inaccessible import while accepting and compiling a valid local type-creation fix. The earlier assertion predated that L63 feature. |


Measured decision: retain the in-memory per-module index; disk persistence is not justified by this
workload. Source edits rebuild their changed closure; host binary/source-index changes conservatively
invalidate every key containing that available repository input. Warm requests still capture current
source/resource inputs, so closed-file detection does not depend on watcher delivery. This is module
reuse, not an incremental compiler or a guarantee for arbitrarily large repositories. Agreed latency/
heap budgets and prolonged sessions remain L82 release work.

Packaged-server measurement (`lang/scripts/compiler-workload.py --semantic-methods 5000 20000
--cycles 2`, same machine and heap, before `l67-before/results.json`, after
`l67-canonical/results.json` under `lang/lsp-server/build/reports/semantic-workload/`):

| Fixture | First references before | First references after | Total compiler invocations before → after |
| --- | --- | --- | --- |
| 5,000 methods, plain | 1,022 ms | 114 ms | 2 → 1 |
| 5,000 methods, inferred locals | 1,314 ms | 155 ms | 2 → 1 |
| 20,000 methods, plain | 8,089 ms | 407 ms | 2 → 1 |
| 20,000 methods, inferred locals | 10,784 ms | 638 ms | 2 → 1 |

All response-content assertions pass; traces show one active compiler API thread. These are observed
runs, not timing thresholds. The first candidate measurement (`l67-after`) still compiled twice and
exposed editor `file:///` versus canonical module `file:/` cache keys. `765fd1eb7` corrects the key and
adds the real-client URI regression; only the `l67-canonical` receipt establishes duplicate removal.

Backend acceptance: 120 tests plus 78 packaged-protocol tests pass with zero failures/errors/skips;
final canonical-key/lifecycle selection adds a clean 19-test rerun. The 33/129-module controls compile
once per initial root, zero times when unchanged, and exactly twice after editing the library with one
consumer. Isolated observations were 1.9 s / 38 ms and 7.4 s / 331 ms cold/warm respectively; the
combined gate observed 3.0 s / 90 ms and 10.6 s / 548 ms under concurrent build/test load. The heap
limit was not raised. Both during-query and post-query compiler-object release controls pass.


Selected editor acceptance:

- VS Code `run-8dtGVL/results.json`: X45/X59/X63/X143/X251 all pass, zero failures.
  `run-IfkEa7` failed the pre-run catalog-order assertion; the registration correction retains that
  assertion and places X251 after X250. No semantic case ran in that initial attempt.
- IntelliJ `run-3343499825664248739/results.json`: the same five scenarios plus startup pass,
  no IDE failures. X251 exercises native document edits and diagnostic delivery, with references and
  workspace-symbol assertions through the installed LSP4IJ client; panel presentation remains manual.
- Catalog: **256 scenarios** (X1–X251 plus CFG1–CFG3 and 7a.8/7a.9). This is selected acceptance,
  not a new full-catalog GUI receipt. Existing UP23 host Undo and other recorded host limits remain.
- Kotlin formatting, TypeScript compilation and `git diff --check` pass. No Gradle configuration,
  Java embedding API, compiler AST fields or production explicit-GC calls were added.

### L74 artifact identities (2026-10-05)

`textDocument/moniker` now has a compiler-only provider. The `ecstasy-artifact-v1` scheme
uses a SHA-256 digest of the normalized emitted module plus its normalized constant-table index.
Normalization removes build timestamps and checkout directories in a private deserialized copy;
module versions, code, signatures, embedded resources and relative source/debug data otherwise
participate in identity. Unchanged recompilation and relocating a checkout keep
IDs; changing an artifact changes its symbol IDs. Identical normalized artifacts intentionally
share IDs across projects. Equal names in different artifacts do not establish identity.
Source-index metadata is not part of this portable key, so attaching/removing matching sources
cannot change binary identity. Existing source-index revisions and fresh rename proofs are unchanged.

Public/protected declarations with externally visible owners export their identity; consumers
import the same identity. Private components, method-local types and their members are local. The uniqueness is `scheme`, not a claim
of an independently registered global identifier. Missing emitted entries, register locals,
parameters, lambdas, unresolved names and unsuccessful compilations return no moniker. The feature
is an exact artifact identity API, not a fuzzy symbol search or an LSIF exporter. Compiler-generated
routes without an exact emitted declaration remain unavailable. No Java/AST API was added.

Extraction runs on the serialized compiler worker; retained tables contain only immutable scalar
facts. The request uses the existing document/configuration guards, cancellation and optional
partial-result path. Shared X252/X253 and updated X31 exercise the connected clients in both IDEs;
there is no native moniker browser in either editor, and protocol checks must not be described as
native moniker UI coverage. Backend tests cover source/binary matching, overloads, unchanged
recompilation, checkout relocation, distinct same-name artifacts, visibility, incomplete source,
closed graph views and replacement. Selected acceptance passes in both editors; receipts below.

Companion views share an immutable identity table per compilation, including after graph joining.
Queries need a current semantic snapshot: parsed-only library source views still return no moniker
when queried directly. Resolved imports in consumers have binary identities; semantic enrichment of
read-only library documents is tracked with L75. This is not a claim of complete library-document
language support.

L74 validation: **137 distinct backend/protocol unit cases** pass across the recorded selections,
plus all **64 packaged `XdkStdioTest` cases**. The final visibility/table-sharing check reruns 39
backend cases and the packaged moniker case successfully. All have zero failures/errors/skips.
LSP and IntelliJ Spotless checks, TypeScript compilation and IntelliJ harness compilation pass.
VS Code `run-EGMh6B` passes X31/X252/X253. IntelliJ `run-10438075944069511128` passes the same three
plus START, with no IDE failures. Its first run (`run-11826261276184041200`) passed X31 but failed
the two new cases because the test driver decoded `Moniker[]` as an object; adding the method to
its existing collection-response set fixes both. This was a harness omission, not an LSP4IJ defect.
The final method-local visibility refinement has backend/packaged coverage; it did not trigger
another GUI run. No full-catalog run is claimed. The catalog has **258 cases**, SHA-256
`7f4170148797ce5828bcb36ec96dd35ca9c4a0670768b7b7dcc81826535f91d5`.

L74 extraction map (keep together after the L67 navigation/artifact foundation):

| Slice | Local commits | Contents |
|-------|---------------|----------|
| Portable identity model | `71fc8da4f` | Normalize a private artifact copy, associate actual compiler bindings, share source/binary identities, add backend tests. |
| Protocol endpoint | `d58d4aa63` | Compiler-only capability, cancellable/version-guarded moniker requests, partial results, negotiation and packaged tests. |
| Shared scenarios and contract | `4b9bc5b28` | X252/X253 in both drivers, update X31 and adapter/playbook/tracking docs. |
| Identity boundaries and memory | `6a8c9acc1`, `febb6431a` | Generic imports, exclude formal properties, classify method-local declarations, share module tables. |
| IntelliJ driver completion | `784c7d534` | Decode moniker list responses through the existing remote collection bridge. |

Next compiler feature: L75 document content/refresh, starting with client support and URI/revision
ownership. L73's broader Run/command service remains with R2–R5; it does not justify an unused
command registry in this compiler-only slice. L76–L79 still need their recorded product/runtime
decisions. L81/L82 retain the outstanding cross-platform and release acceptance work.
