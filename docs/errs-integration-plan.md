# Integrating the embedding diagnostics work

Plan prepared on 2026-09-22 from `lagergren/errs` at `a8213cf04`, against the local
`origin/master` reference at `4a1eae6f7`, which is also the merge base. This comparison contains
72 commits and changes 139 files: 6,632 insertions and 984 deletions. The remote was not refreshed
for this assessment. These numbers describe the source branch, not the proposed PR sizes.

Sources: [the design and investigation](errs.md), [the failure audit](errs-audit.md), the commit
bodies, and the current compiler, embedding API, adapter, server and build configuration.
There are no `errs.log` or `errs-audit.log` files in this checkout; the corresponding records are
the two Markdown files above.

## Anonymous constructor cursor support

Implemented on `lagergren/errs` after `185ff84b1`; these are working changes until the next
requested commit. This completes the next constructor API investigation before array-dimension
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

**Remaining constructor limits:** array-dimension cursor slots and multidimensional construction.
The latter is not implemented by the ordinary compiler either. Anonymous bodies must be retained
by the parser; this does not repair arbitrary body errors or infer missing declaration names/types.
Next: single-dimensional bracket argument fitting, then bounded declaration-header recovery.

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
implemented by C18/L31 above. Constructor cursors inside array dimensions,
multidimensional construction, unfinished declaration names/types, missing operands/map entries,
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
   Compilation/PartialAnalysis expose them while retaining old constructors. Both records now have
   six components: Java record patterns must add `functionBindings`, as demonstrated by
   `EmbeddingApiCompatibilityTest`; preserving constructors does not preserve pattern arity.
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
| 3. Completion and signature help — bounded POC complete | Scope/member completion, imported type names, static lookup and incomplete-call candidate fitting now have consumers. Candidate-specific expected types and named-argument mappings are copied. C11/L23 extends signatures to incomplete function values and ordinary constructors, including explicit class type substitution. C12/L24 retains missing enclosing call/group/index closers around a cursor. C13/L25 completes readable locals/parameters in empty final positional and pending named slots using compiler fitting. C14/L26 adds direct final bare-name prefixes with exact token replacement. C15/L28 adds implicit property/constant values with compiler read validation. Literal synthesis and fitting inside qualified/compound/grouped expressions or before later arguments remain follow-ups. C16/L29 adds specialized constructors and class inference; C17/L30 adds bounded declaration/tuple/literal recovery. C18/L31 adds anonymous construction without capture/body emission. Missing operands, unfinished declaration names/types, array-dimension cursors, enclosing-instance member completion, type-valued receiver fallbacks and receiver-rewritten calls remain outside the proven scope. Completed function calls have separate signature facts in E5/L20. An unfinished call never claims a selected overload. | Adapter/stdio requests cover declaration order, assignment state, narrowing, imports, access checks, generic receivers/methods, overload filtering, named slots, module overlays and token edits. Completed calls retain exact compiler selection. |
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
| `Compilation` record | Three-, four- and five-argument constructors remain. Current six-component pattern includes module, file, ast, sourceTrees, callBindings and functionBindings. Prefer accessors/`forFile(...)` for clients not needing deconstruction |
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
| C18 | Anonymous construction ownership and constructor fitting | Working changes after `185ff84b1`; anonymous-constructor section above | C16 and cursor/listener foundations |
| L31 | Anonymous-constructor labels, consumers and ownership controls | Same working changes; X88–X89 | C18, L29/L30, L16 |
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
C15/L28 to **57**, C16/L29/C17/L30 to **61**, and C18/L31 to **63**. L18 extends property lookup after L10/L13. During current development,
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
