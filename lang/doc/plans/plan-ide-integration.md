# Ecstasy Language Support Implementation

C27/L51 completes written type prefixes inside function parameters/returns and type-sequence
arguments, with bounded missing-closer recovery and no invented header signatures. Shared X106 is
implemented in both editors. IntelliJ now asserts X33/X35 navigation and the complete X101
read-only checks; native execution receipts are tracked separately in the active validation plan.

Live workspace/source navigation (L47–L49): unsaved headers and workspace-folder changes refresh the
compiler graph; detached graph queries are reused, and healthy modules remain navigable beside a
broken neighbor. Matching bundled XDK declarations open read-only source files. Complete reference
and refactoring proofs still fail closed. This adds no AST state or compiler listener changes.
See [scope, ownership and validation](../../../docs/errs-integration-plan.md#live-workspace-and-source-navigation-checkpoint-l47l49).


> **Last Updated**: 2026-10-06 (configuration responsiveness and selected acceptance)

The latest [L82 acceptance record](../../../docs/errs-integration-plan.md#l82-upstream-isolation-and-full-catalog-acceptance-2026-10-06)
separates native harness corrections, the production unchanged-library-settings invalidation bug,
individual upstream bridge replacement tests and full-catalog outcomes. These repairs add no LSP
capability or Java embedding/AST API. VS Code covers all 277 scenarios across recorded runs:
276 pass and X218 retains the UP23 Undo failure. IntelliJ completes all 277 in one uninterrupted
released-dependency run: 275 pass, X254/X257 retain UP25/UP26 partial status, zero failures and
zero IDE errors. The subsequent
[configuration responsiveness batch](../../../docs/errs-integration-plan.md#l82-configuration-responsiveness-2026-10-06)
reduces 56-buffer graph-reset dispatch from roughly 8.5 seconds to 0.23 seconds and preserves
successful independent module analyses. It uses operation-local scope maps and existing lifecycle
ownership, with no new AST state, embedding API or advertised capability. Real platform checks
retain all 49 documents' symbols/diagnostics and avoid 11 unnecessary recompilations per unrelated
graph change. Hardware-wide response budgets, long-duration release evidence and UP17's
decorated-editor cost remain open. X147/X148/X259 pass with the repaired server in both editors;
IntelliJ also passes START with zero IDE errors. This is selected follow-up acceptance, not a
new full-catalog run.

The P1–P4 compiler organization checkpoint moves the four incomplete-syntax nodes into
`org.xvm.compiler.ast.partial` and updates the adapter's imports. It changes no advertised LSP
capability or default adapter. Backend validation and the broader AST separation follow-ups are
recorded in the [integration plan](../../../docs/errs-integration-plan.md#next-checkpoint-isolate-partial-ast-syntax).
AST5 additionally shares read-only cursor/argument syntax queries in that package, with no capability
change or new compiler state. AST1 and AST3 consolidate scope collection and capture projection
in the existing compiler helpers, preserving the adapter's public results. Their focused
200-test validation and commit map are recorded in the
[follow-up receipt](../../../docs/errs-integration-plan.md#scope-and-capture-helper-follow-ups-ast1-and-ast3).

Post-rebase validation and the Node 24 / VS Code 1.140.0 baseline are recorded in the
[complete acceptance receipt](../../../docs/errs-integration-plan.md#modern-vs-code-baseline-and-complete-rebase-acceptance-2026-10-05).
No advertised LSP capability changed. Known UP23/UP25/UP26 limits remain explicit; IntelliJ
fixture setup corrections are distinct from production behavior.

This document describes the language tooling implemented in the `lang/` directory and what remains to be done.

The L64 closure batch adds ordinary enclosing-instance proposals, useful inferred-local names and
compiler-fitted lambda argument snippets. L65 follows property redirects and conditional mixin
bodies on validated concrete source types, while refusing non-written targets and ambiguous binary
source spans. Shared X150–X154 carry the new native checks. Backend gates and all L64/L65 shared cases pass. The full-catalog runs retain VS Code X130 and
intermittent IntelliJ X105 acceptance issues; exact receipts and remaining boundaries are tracked in
[the closure record](../../../docs/errs-integration-plan.md#l64-closure-audit-and-acceptance-gate).

The accepted [embedded Run and debugging plan](plan-embedded-execution.md) defines the shared
compile/build and execution contracts for both clients, a persistent execution worker and fresh
application containers per run. R1–R8 track that separate implementation. Current Run commands
still launch Gradle/CLI processes; the DAP server remains a transport stub. Compiler/LSP completion
does not imply completion of either execution or debugging. Current compiler changes must retain
artifact/source revisions and cancellation boundaries suitable for those future consumers.

## What's Implemented

### 1. Language Model DSL (`lang/dsl/`)

A Kotlin DSL that defines the complete Ecstasy language model and generates editor support files:

**Source:** `XtcLanguage.kt` - Complete language definition including:
- Keywords (reserved and context-sensitive)
- Operators with precedence and associativity
- Built-in types
- Token patterns for lexical analysis
- AST concept definitions

**Generators:**
| Generator | Output | Purpose |
|-----------|--------|---------|
| `TextMateGenerator` | `xtc.tmLanguage.json` | Syntax highlighting for VS Code, IntelliJ, Sublime |
| `TreeSitterGenerator` | `grammar.js`, `highlights.scm` | Incremental parsing, structural queries |
| `VimGenerator` | `xtc.vim` | Vim syntax highlighting |
| `EmacsGenerator` | `xtc-mode.el` | Emacs major mode |
| `SublimeSyntaxGenerator` | `xtc.sublime-syntax` | Sublime Text highlighting |
| `VSCodeConfigGenerator` | `language-configuration.json` | Bracket matching, comments, folding |

**Generated Files:** See `lang/generated-examples/`

### 2. LSP Server (`lang/lsp-server/`)

A Language Server Protocol implementation providing IDE features:

**Server:** `XtcLanguageServer.kt`, `XtcLanguageServerLauncher.kt`

**Adapter Architecture:**

The LSP server uses a pluggable adapter pattern to support different backends:

```
                     XtcLanguageServer
                            │
                      Adapter (interface)
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
   MockAdapter       TreeSitter-         XdkAdapter
   (adapter.mock)    Adapter             (adapter.xdk)
   (regex-based)     (adapter.treesitter)(the XTC compiler)
```

All adapters extend `AbstractAdapter` which provides:
- Per-adapter `[displayName]` prefixed logging via `logPrefix`
- "Not yet implemented" defaults for all optional LSP features (with full input parameter logging)
- Shared formatting logic (trailing whitespace removal, final newline insertion)
- Utility method for position-in-range checking

`Adapter` defines the shared API, including synchronous defaults for its asynchronous entry points.
Concrete adapters implement supported features; `AbstractAdapter` supplies traceable stubs for
the optional operations.

| Adapter | Backend | LSP Feature Coverage | Status |
|---------|---------|----------------------|--------|
| `MockAdapter` | Regex patterns | Syntax-level features without an AST | Implemented |
| `TreeSitterAdapter` | Tree-sitter grammar | Syntax, structure and workspace index | Explicit syntax-only alternative - Implemented |
| `XdkAdapter` | The XTC compiler, via `EmbeddingSupport` | Module diagnostics/navigation, bounded completion/signatures, type and implementation lookup, hierarchy, tokens, hints and explicit dependency source indices | **DEFAULT** (`lsp.adapter=compiler`); Tree-sitter remains explicitly selectable |

**`XdkAdapter` is no longer a placeholder.** It compiles through the embedding API and reports
what the compiler actually says - syntax *and* semantics, with the compiler's own codes, messages
and spans - which is the thing no grammar can do: `COMPILER-38: Name "NoSuchTypeAnywhere" is
unresolvable` is not a syntax error and tree-sitter cannot find it. It also supplies the outline
and the symbol under the cursor.

It also answers the position questions across a compiled module: hover with the type the compiler
decided, go-to-definition and find-references by what a name *means* - two properties called `x`
on different classes are different things - plus document-local highlights, folding and selection.
The Kotlin semantic snapshot supplies types, symbol identities, declared signatures, inheritance and
source occurrences without retaining compiler objects. The compiler exposes the binding facts;
lambda capture associations live in a helper owned by the lambda compilation context.

The compiler backend supplies bounded member completion and signature help through explicit cursor
analysis and copied selected-call facts. Type-definition, type/method implementation lookup,
static call hierarchy, resolved-name semantic tokens, read/write highlights and bounded inlay hints
also use copied facts. Bounded local/private-parameter rename recompiles and checks bindings,
including named labels, before returning versioned edits. Whole-graph proofs extend rename to
inline source types, static members and ordinary instance-method families. The Java lexer supplies
bounded token-preserving formatting, URL links and lexical highlighting. Module run lenses,
local linked editing and proven ordinary-import cleanup are implemented with the limits below. A trailing `.` still produces a
normal syntax diagnostic; a separate cursor probe can inspect its intact receiver without
accepting or emitting the damaged expression. Compiler mode stays Java-only.
The full matching XDK library set is bundled with the server and treated as read-only; no external
installation is required. The three bootstrap checks are minimum health assertions, not a whitelist.

Configured source modules also carry ordered resource roots. Conventional Gradle resources use
compiler layout deduction; custom/unmanaged roots use explicit project settings in both hosts.
Resource contents, creation/deletion and configuration participate in cache invalidation and
diagnostic refresh. Evaluated Gradle import, source/resource path pickers and effective origin reports are implemented; see [PLAT2](../../../docs/errs-integration-plan.md#resource-configuration-and-build-model-integration-plat2--l67).
Hover identifies resolved occurrences, completed calls retain parameter mapping between written
arguments, and inferred/narrowed nominal types retain type-definition targets. Member completion
can retain a prefix before existing call parentheses, including chained receivers.

Navigation includes type-parameter declarations and anonymous-class captures. Module sessions
combine disk sources with unsaved overlays, including new member files, and build per-source views
in one identity domain. Matching bundled library declarations navigate to read-only source targets. Workspace
symbols compile discovered/configured modules on demand and search by case-insensitive substring,
including unopened sources. Healthy independent modules remain searchable when a neighbor fails.
Edits invalidate live views and closing the last open member releases its editing session. An explicit
host API can supply compiled dependency artifacts and detached source indices. Revisioned keys
associate dependency declarations across consumer attempts; these are not stable identities across
dependency rebuilds or a persistent workspace reference index.

The current verification, reporting audit and remaining work are recorded in
[errs-integration-plan.md](../../../docs/errs-integration-plan.md).

The adapters provide different capabilities: tree-sitter maintains error-tolerant syntax results;
the compiler supplies validated semantic facts. A combined adapter has not been implemented.

**Note:** TreeSitterAdapter requires Java 25+ (FFM API). The IntelliJ plugin runs the LSP server
out-of-process for classloader and crash isolation (IntelliJ 2026.2 runs on JBR 25).

#### Adapter capability matrix

The **Compiler (XdkAdapter)** column describes the current implementation. The optional
features in [XdkAdapter.capabilities](../../lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt)
are filtered into the server's
[advertised capabilities](../../lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt).
Diagnostics and document synchronization are provided separately. Unimplemented compiler features
are not advertised; inherited adapter stubs or basic formatting helpers do not enable them.
**Done** below means implemented within the scope written in that row; it is not a claim of
support for every XTC construct, optional LSP extension or native editor presentation.

L83 now retains detached successful constant-initializer facts before temporary-method disposal.
Hover, navigation, references, token classification and rename use those facts through the Kotlin
semantic model, with no new AST fields. Backend regressions and shared X142 pass in both editors,
including native rename/Undo; X140 remains the separate UTF-16 method-body check.
See the [L83 task and ownership record](../../../docs/errs-integration-plan.md#protocol-correctness-and-the-completion-gate).

| Feature | Mock | Tree-sitter | Compiler (XdkAdapter) |
|---------|------|-------------|----------|
| Syntax highlighting | - | TextMate + semantic tokens (lexer) | TextMate plus Java lexical tokens and compiler-resolved names |
| Document symbols | Full | Full | **Done** - from the AST, with real ranges |
| Go-to-definition (same file) | By name | By name | **Done** - semantic, incl. method calls |
| Go-to-definition (cross-file) | - | Via workspace index | **Done** - resolved identities within a module, across the complete discovered/configured source graph and into dependencies with host-supplied source indices or configured matching source attachments |
| Find references (same file) | Decl only | By name | **Done** - by identity, not by name |
| Find references (cross-file) | - | - | **Done** - exact identities across the current module or the complete configured source graph, including unopened consumers, implicit-package companions and binary-member uses; synthetic unnamed package views contribute semantic facts but never file targets |
| Completions | Keywords | Context-aware keywords/types/locals/members/imports | **Partial** - visible locals/parameters, narrowed types, implicit members, imported/enclosing types and static functions/constants; qualified dot/prefix and bare-name/empty statement completion with exact token edits; compiler-fitted locals/parameters and implicit properties/constants in empty final positional and pending named argument slots, including qualified/grouped values and slots before later arguments; member/return and parameter-header type prefixes use the enclosing compiler scope; flat and parameterized qualifiers use visible nested types with substituted aliases; registered formals and empty generic slots complete; mid-token edits replace the entire final identifier, including generic base names before written type arguments; import-producing completion for public source/bundled types uses whole-graph proof and atomic additional edits (backend/protocol tests and the new X105 variants pass in both editors); syntax name suggestions for written explicit-type declarations and contextual keywords/eleven templates now have passing backend and shared X149/X150 coverage in both editors; empty property/parameter names after complete named types, including primary constructors and EOF, have passing backend/protocol coverage and shared X151 acceptance in both editors; latest additions include ordinary enclosing-instance values, inferred-local naming clues and compiler-fitted lambda templates, with passing backend and expanded shared X150–X152 receipts in both editors |
| Syntax errors | Markers | Full | **Done** - the compiler's own codes and spans |
| Semantic errors | - | - | **Done** - the reason this adapter exists |
| Hover (signature) | Basic | Basic | **Done** - declaration plus the resolved type |
| Document highlights | Text match | AST identifiers with READ/WRITE distinction | **Done** - by resolved identity; READ/WRITE distinguished, compound/destructured targets shown as WRITE; indexed receivers and indices remain READ (X154) |
| Selection ranges | - | AST walk-up | **Done** - strictly nested source-owned AST spans with duplicates removed; zero-width cursor range if no AST is available |
| Folding ranges | Braces | AST nodes | **Done** - blocks and declarations; exact closing-brace columns prevent swallowing following declarations |
| Document links | Regex | AST nodes + best-effort import targets | **Partial** - HTTP(S) URLs inside Java-lexer comments/literals plus compiler-resolved module and explicit type/alias import sources; wildcards link their resolved container; unsupported conditional source syntax and stale text suppress semantic links |
| Signature help | - | Same-file | **Partial** - selected signatures; fitted incomplete method/function/constructor calls, including specialized constructors and bounded declaration/tuple/literal recovery. Methods/constructors retain named mappings; function types have unnamed parameters. Constructor class types use explicit, required-type or provisional argument inference; array suppliers include dimension offsets and single-dimensional bracket slots fit the size parameter |
| Rename (same file) | Text | AST | **Partial** - locals/lambda/private ordinary-method parameters, captures and named labels; positional method-value escapes; graph-backed public/explicit-constructor parameter slots, types, static members, method/property families and explicit aliases; client versioned-edit support required. The L62 extension has backend and selected shared acceptance in both editors |
| Rename (cross-file) | - | - | **Partial** - cross-module type ownership/import/dependency proposals, ordered overlapping source moves, captured incoming resources and exact numeric/compound annotation proof (L62 compiler closure; VS Code overlapping-move Undo remains UP23), types/packages and companion directories, combined type/module rename-and-move and interacting type batches, captured empty destinations and commented qualifications (X169–X176; latest receipts below), qualified discovery-managed modules, implicit package directories, static members and source method/property families, including supported mixin/delegate/annotation routes, union alternatives and recursive written contracts (X164/X165), plus bounded generic/formal/annotated operands and nested union delegation (X166–X168; current receipt below); public parameter slots join override declarations and named callers; primary-header properties join generated constructor labels and property uses. Full graph compilation and binding/dispatch proof remain mandatory. Explicit graph changes have guarded native client persistence/Undo through xtc/rename (X118 passes in both editors; VS Code edited-file moves require files.refactoring.autoSave); standard LSP clients still refuse them. Project proposals include a scope receipt; registered absolute roots can include external consumers. The explicit graph is a declared proof boundary: omitted consumers, even inside workspace roots, remain unknown and are not automatically refused |
| Code actions | Organize imports | Organize imports + auto-import + doc-comments | **Partial** - compiler-proven unused-import removal, contiguous import sorting and unresolved public-type imports; individual/all-required implement/override at a class name for source and read-only binary/XDK contracts, including generic/conditional, compound and qualified types with imports; validated constants and fresh literal-default repair; whole-return/typed-initializer extraction, adjacent same-type returned/initializer local inline and unused constant local removal; private same-owner expression helpers with stable inputs (X181–X184); missing private methods from resolved declaration/local types, typed initializer contexts and proven same-owner instance receivers and named enclosing-class static qualifiers (X185–X200), plus explicit public repairs in other writable ordinary classes of the same module, including companions, earlier compiler-typed locals and explicit initializer results (X201–X208), plus reachable configured source dependencies using existing aliases or adding imports for already available destination dependencies (X209–X215; receipts below); generic owners/formals, conditional repairs, computed receivers and named arguments; missing class/instance-property stubs; statement/nested-expression extraction; constant-local and selected private member inline; private-method/constant safe delete (X221–X242, selected acceptance passes in both editors); complete compilation and binding/dispatch proof, versioned edits |
| Document formatting | Trailing WS | Structural re-indent + whitespace cleanup | **Partial** - Java-lexer nesting/operator continuations, token-boundary expression/list wrapping at the configured margin and standalone comment margins; code/literal tokens preserved; no comment reflow or literal splitting |
| Range formatting | Trailing WS in range | Structural formatting in range | **Partial** - same token-preserving formatter, bounded to selected lines |
| On-type formatting | - | Structural formatting on trigger characters | **Partial** - current-line indentation/whitespace on configured trigger characters |
| Workspace symbols | - | Fuzzy search (4-tier) | **Done** - on-demand substring search across discovered/configured sources including unopened modules; independent healthy modules survive a broken neighbor |
| Semantic tokens | - | Lexer-based (18 contexts) | **Partial** - Java lexical comments/literals/keywords plus resolved names, declarations, readonly/static/abstract modifiers and writes |
| Code lenses | - | Run action on module declarations | **Done** - module Run action through the existing client command |
| Linked editing | - | Same-file identifiers | **Partial** - resolved locals/lambda parameters and explicit aliases (including same-spelling aliases) within one successful source snapshot; callable parameter slots require Rename; no proposed-name proof |
| Inlay hints | - | - | **Partial** - inferred local/destructured types, lambda parameters/returns and selected positional parameter names after successful compilation; named arguments/defaults omitted |
| Go-to-declaration (separate LSP request) | - | - | **Done** - local/import-alias declarations, inherited method/property contracts with multiple targets, and indexed library sources |
| Go-to-type-definition | - | - | **Done** - copied source type identities, narrowed/parameterized/nullable/relational types, formals and selected-call returns; module and host-indexed dependency sources |
| Find implementations | - | - | **Partial** - compiler composition targets across the complete source graph, including unopened source consumers; generic/inherited/mixin/delegated methods and property accessors; no invented binary source target |
| Type hierarchy (supertypes/subtypes) | - | - | **Done** - direct declared extends/implements edges across the complete source graph; generic parents retained, digest-bound handles reject stale closed files |
| Call hierarchy (callers/callees) | - | - | **Partial** - static selected source calls across the complete source graph, with method/lambda ownership and incoming/outgoing grouping; digest-bound handles reject stale sources |

#### Compiler completeness snapshot

The October 6 platform recheck fixes unnamed implicit-package views in the joined navigation
index, without changing the compiler/AST API or advertised capabilities. The regression fails
before the fix; shared X117 now checks root/companion references before native Rename/Undo in
both editors. Selected native checks pass on released dependencies. See the
[diagnosis and receipts](../../../docs/errs-audit.md) and [real-project tour](../../../demo.md).

Source audit at `511195564` (2026-09-27): **all 24 project-defined adapter capabilities have
compiler implementations**, plus push diagnostics and document/workspace synchronization.
This covers the usual editor feature families, but several are bounded and some LSP operations
are entirely absent. L61 adds the 25th capability, explicit declaration lookup, with backend,
protocol and selected editor validation. The enum does not include all of LSP; substantial
semantic and protocol work remains.

The active [full completion checklist, L55–L83](../../../docs/errs-integration-plan.md#full-compiler-lsp-completion-checklist)
is the task source of truth. It distinguishes implementation work, confirmed reliability gaps,
investigations and optional features requiring a scope decision. The protocol inventory uses
LSP 3.18 and the installed LSP4J 1.0.0 interfaces. Unadvertised optional features do not by
themselves violate LSP; an inherited empty method does not count as an implementation.

L61 declaration lookup passes combined backend/protocol checks and X4 in both editors. It returns
local or import-alias declarations and inherited written member contracts, preserving multiple targets.

| Protocol gaps and pending acceptance | What exists today | Task |
|---|---|---|
| Extract/inline/safe-delete refactorings and general missing-declaration fixes | Bounded proven rename, import cleanup, public-type imports, implement/override, whole-return/typed-initializer extraction, adjacent same-type returned/initializer local inline and compiler-proven unused constant local removal; private expression helpers with explicit stable inputs | L62–L63 |
| Pull document/workspace diagnostics | Implemented for negotiated compiler clients: result IDs, related/closed documents, refresh and removal reports. Shared X123 and updated X76/X118 pass in both editors; push remains for other clients. PLAT1's source-location crash is fixed. The native demo also corrected a closed standalone-member pull gap; X27/X123 pass after that correction. | L68 implemented; demo receipts and nine pull-diagnostic tests |
| Semantic-token range/delta requests | Negotiated range/delta with bounded result history; backend/protocol and both host checks pass | L69 / X126 |
| Completion/action/lens/link/inlay/workspace-symbol resolve requests | All six endpoints implemented with bounded revision guards. IntelliJ selects and applies lazy actions through its undo-aware bridge. Backend/protocol and selected acceptance pass in both editors. | L70 / X127, X131 |
| File-operation pre-edit requests; explicit create/delete notifications | All six hooks pass backend/protocol checks; native file/package Rename passes. X130's Move/Undo/Redo/resource assertions pass the October 5 full VS Code run; its earlier intermittent Explorer repaint failure remains UP16. X218 still fails VS Code overlapping-move Undo (UP23). IntelliJ supports the ordered transaction. Bounded cross-package qualification passes X161; explicit graph replacement uses `xtc/renameFiles` and shared X162/X163. | L71 / X128, X130 |
| Save-time edits, incremental sync, multiple-range formatting | Negotiated save hooks, opt-in incremental UTF-16 updates and multiple-range formatting implemented; default Full/no save edits preserved. Backend/packaged transport and selected X132 pass in both editors. | L72 / X132 |
| Server-side `workspace/executeCommand` | Module Run lenses invoke an existing client command | L73 |
| Cross-project monikers | Artifact-based import/export/local identities; backend and selected acceptance pass in both editors | L74 |
| Server-provided document content/refresh | Negotiated revision-owned bundled virtual content and refresh; protected file fallback in IntelliJ (UP25); host source indexes remain file locations | L75 |
| Inline completion | Compiler names/values, automatic ambiguity suppression and explicit alternatives; selected-range and revision guards | L76; native IntelliJ invocation/selection remains UP26 |
| Document colors and color presentations | Ordinary token coloring; no color-value provider | L77 |
| Notebook synchronization | File/module document sessions | L78 |
| Debug inline values | Compiler type/parameter inlay hints; no runtime values | L79 |
| Application work-done progress, refresh, partial results and trace controls | Negotiated progress/create/cancel, lifecycle gating, coalesced refresh and runtime trace are implemented and tested. Bounded partial-result batches, actual Tree-sitter scan progress and shared X143 pass. X145 verifies native progress-model cancellation and restart during pending work in both editors. Both editors pass visible Cancel, X146/X147 refresh/report ownership and X259 rendered settings/restart overlap. Shared-process native window lifetime passes; UP15 retains its upstream classification gap. | L81 / X141, X143, X145–X147, X259; [coverage map](../manual-test-plan.md#protocol-and-lifecycle-coverage-map) |
| Constant-folded property initializer facts | Detached initializer facts survive constant folding; backend and shared X142 pass in both editors | L83 |

Every absent feature above has an explicit task and a
[next investigation step](../../../docs/errs-integration-plan.md#investigation-status-and-next-decisions-updated-2026-09-30).
The inventory is complete for this list; detailed implementation designs are still pending for
several operations. Optional language/product features require an explicit implementation or
exclusion decision, not an assumption that every protocol extension is mandatory.

The remaining limits within implemented features are tracked separately: malformed/header/value
completion and signatures (L57/L58/L64), inferred displays (L59), rename proof and editable target
scope (L55/L62), hierarchy/classification (L65), formatting/links/linked editing (L66), and index
scale/source metadata (L67). L56 adds client startup ordering and stale-fold guards. L57 resolves
written formal bounds without synthetic components; L58 extends whole-argument fitting and callable
fallbacks; L59 copies validated lambda signatures and destructured local types. Combined validation
and remaining native acceptance are recorded in the integration plan. L56's dedicated five-phase
native startup test now passes; X103 also passes after fixing the transport snapshot read-lock/VFS
write-lock deadlock on reverse rename. Execution tracing includes queue sizes and ordered job
lists, compiler/API durations and server request-to-reply times. These are diagnostics improvements,
not additional language capabilities. L81 progress/trace controls and bounded native acceptance are now complete.
Dynamic watcher registration now waits for `initialized` and negotiated support. The final L80 producer/capability audit and L81 bounded progress/refresh acceptance are complete;
full-catalog, scale and other-platform release evidence remain under L82.

**Current combined checkpoint (2026-10-05):** 2,991 backend tests pass (44 existing skips).
VS Code exercises all 264 scenarios: 263 pass, X218 retains UP23. After the UP27 refresh and UP03
redundant-preflight bridges, IntelliJ covers the same catalog across two segments: 262 pass and
two remain partial (UP25/UP26), with zero IDE errors/freeze dumps, including the 14-minute
continuation. A corrected X185 popup-harness failure prevents calling this one uninterrupted clean
run. Generic upstream VFS waits remain. The client suite passes 98 tests. Recursive typedef
handling, bounded worker retention and these client repairs require no new AST/embedding APIs. See the
[L82 evidence and next tasks](../../../docs/errs-integration-plan.md#l82-combined-regression-and-retention-checkpoint-2026-10-05).

**Historical acceptance (2026-09-29):** the shared playbook then had 128 cases with
assertions in both drivers. The [current IntelliJ demo record](../../../docs/errs-integration-plan.md#native-intellij-demo-continuation-2026-09-29)
tracks resumed execution and focused corrections; it does not replace the historical full-run receipt.
All 128 cases have passing receipts across resumed runs. The final X105/X122/X123 native recheck
passes after the client carries diagnostic result IDs across automatic pulls of the same editor
snapshot, preserving lazy quick fixes on unchanged reports. VS Code `run-b59XBq` passes all 128 cases
in one complete run after the X60 source/resource invalidation correction and shared expectation
sorting. The accompanying 1,413 backend and 70 protocol tests pass; three existing Tree-sitter
placeholders remain disabled. IntelliJ `run-5742519770640114134` passes START and
X60/X77/X78/X105/X122/X123 on the final code with zero IDE errors.
X119–X121 pass in selected runs in both editors, alongside X57/X118 and configuration controls;
see the [batch receipts](../../../docs/errs-integration-plan.md#checkpoint-and-validation-map). The ten L62 additions, X109–X118, pass in selected editor runs; see the
[rename receipts](../../../docs/errs-integration-plan.md#shared-rename-and-native-settings-validation).
The preceding 113-case suite includes the 50 completed parity cases and X20/X81/X82 assertion
sets. That complete suite passes in `run-6034631232732848040`, with zero IDE errors and
zero JUnit failures/errors/skips. X57 now verifies guarded native symbol
rename after reproducing LSP4IJ's stale-edit bug; other edit entry points remain an audit item.
X29 and X93–X98 are included in that full checkpoint.
All preceding 113 scenario assertions have passing receipts across runs. The resumed 61-case batch
failed its separate IDE-error gate on a test-driver PSI read, now corrected; X41/X108 pass with
no IDE errors. Later full checkpoints were interrupted by desktop focus and then Starter's
default ten-minute session timeout. The driver now permits 30 minutes overall; its first rerun
lost focus at X17 after 17 passing scenarios. The harness now restores popup focus without pointer
input, guards against replay after edits and uses shorter polling intervals. The dedicated
focus-recovery regression and seven selected native cases pass with no IDE errors. Both editor
harnesses show current-case and completed/remaining counts. The final full run also passes X30
with document readiness after restart and closes L60. Separate startup acceptance verifies all
five edit/restart phases and disappearance of the untouched information balloon.
See L60/L82 and the [validation record](../../../docs/errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).

Configured-graph queries compile a captured source snapshot on the serialized worker and leave live
diagnostics untouched. Graph proofs now discard each root's compiler objects after copying
declaration/artifact comparison keys and dispatch facts. A 24-root memory regression covers
success, rejection, cancellation and failure at 512 MiB. The real 25-root teaching workspace
also passes with one and five unsaved buffers, unchanged diagnostics and released compiler objects.
References distinguish overloads and concrete overrides; they do not expand to an entire override
family. Method rename does expand that family, including generic interface
contracts, then rejects changed bindings or dispatch relationships. Exact references and refactoring
require a complete graph; navigation can retain healthy independent modules beside a broken one.
Edits, close, settings/repository changes and cancellation retire outstanding queries;
closed source text/membership is checked again before returning. Preparing rename checks only a
candidate; the final proof can reject it. Binary contracts (including bundled XDK methods),
instance-property/accessor families, constructors and mixin/delegating/capped chains remain
outside method rename. Inline types and static functions/properties use direct-identity proofs. Ordinary `super(...)` calls retain the selected written body and participate
in binding comparison, while the keyword itself is never renamed. This proves closure over discovered/configured sources only. Discovery scans workspace folders at
startup, watched-file changes, unsaved import-edge changes, close and workspace-folder changes.
Persistent indexing and large-graph proof memory remain follow-ups. See playbook X59–X63 and X99–X105.

Type-definition returns all available source targets for union/intersection operands, unwraps
modifiers and follows aliases for value types without navigating into generic argument types.
A written type or formal parameter points to its own declaration. Bundled library types have no
source target. Implementation lookup uses declaration identities and compiler method chains;
it does not match by spelling or arity. It requires successful compilation. Type results are
nominal declaration-level implementations (including a concrete type itself), not a search for
structurally assignable types or generic instantiations. Ordinary properties expose effective written
getter/setter bodies or backing fields; accessor declarations retain separate chains. A property
use has the declaration-level set, not a read/write-specific dispatch result. Ref/Var annotation
accessors use the host's existing nested method chains, including annotation order and explicit
property overrides. Native annotation storage, synthetic redirect targets and binary implementation targets without source metadata remain unavailable. Delegation follows compiler-selected signatures through concrete receiver types; interface-
valued or cyclic delegates have no proven target, and no forwarding code is generated for lookup. An inherited dependency body in a current source type's method chain can resolve when
the host supplies its declaration source index.
An explicit reporting inspection runs on the compiler worker; request threads use immutable
copied locations. See the [manual playbook](../manual-test-plan.md#xdkadapter-playbook).

The Kotlin host API exports successful compilations with `Compilation.toDependency()` and installs
the complete artifact set with `XtcLanguageServer.replaceCompilerDependencies(...)`. Replacements
invalidate affected consumers, including transitive imports, cancel pending work and republish
diagnostics at current document versions. Unrelated successful sessions survive. Binary-only
artifacts have no invented source targets. Editor launch supplies bundled XDK modules and explicit source modules from
`xtcCompiler` initialization options or `xtc.compiler` settings. VS Code exposes the live workspace
setting `xtc.compiler.sourceModules`; IntelliJ uses LSP4IJ's existing server Configuration JSON
with nested `xtc.compiler.sourceModules`. Those settings are IDE-wide; a dedicated project graph UI remains separate. `replaceCompilerSourceModules(...)` now
provides automatic source dependency builds for explicit roots/edges, including unsaved overlays,
100 ms edit debouncing, transitive invalidation and per-document diagnostic versions. Failed
dependencies block consumers without reusing old artifacts; corrections restore them automatically.
Unsaved import-edge discovery, binary source attachment and persistent workspace indexing
remain open. Cyclic source graphs are rejected. See the
[dependency verification record](../../../docs/errs-integration-plan.md#versioned-dependencysource-host-api-2026-09-23).

Call hierarchy includes written anonymous methods and recursive/overloaded calls. It requires a
successful source graph and source locations at both ends. Runtime dispatch expansion,
function-value calls, constructors, property initializer/accessor edges and binary declarations
without source metadata remain outside this slice. Inlay hints do not invent parameter names for unresolved candidates or
infer lambda return annotations. These features load no native parser. Rename compares pre/post-edit
bindings for every recorded occurrence and selected call, rejecting silent capture. Named labels
resolve to the selected source parameter. Public/lambda/constructor parameters and method-value
escapes are excluded. Unsupported bindings or unsuccessful compilation produce no edits. Source,
dependency and document-version changes cancel stale proofs; closed file text/membership is checked
again before returning edits. The full scope and manual cases are in the rename playbook.

Semantic results can be partial when validation fails. Parse errors prevent semantic compilation,
but `Compilation.sourceTrees()` retains available per-file syntax for outline, folding and selection.
Statement-boundary recovery omits malformed statements and preserves surrounding declarations;
missing braces retain completed method/module headers. Malformed method parameter headers retain
their written name and body extent for outline/folding, without exposing body locals or registering
a method signature. Unbounded headers and lexer failures can still leave gaps. Selection ranges retain one response per cursor, with a cursor-only fallback if no
syntax covers that position. An edit invalidates the old analysis; queries do not reuse semantic
positions from an older document version. No Tree-sitter fallback is used in compiler mode.

Property/return and method-parameter type prefixes use the enclosing compiler name resolver.
Unqualified prefixes include empty parameter type slots. Flat qualified prefixes such as
`ecstasy.text.Str` resolve the written qualifier, enumerate visible nested types through TypeInfo,
and replace only the final identifier. Module/package/class names and import aliases work, including
inherited nested types and typedef candidates. Hidden qualifier ancestors and value names are
excluded; same-owner private types remain available. Unsaved module overlays are respected.
Written leaf names inside nested generics and compound types also complete, including grouped,
nullable, array and immutable wrappers, with bounded missing angle/group closers. This enumerates
visible types; normal compilation validates constraints on the whole type. Registered class/method
formals, empty generic arguments and complete parameterized qualifiers now work, including
substituted typedef types. Mid-token queries replace the whole final identifier, including generic
base names before written type arguments. Shared X91–X98 and X106–X108 cover completion, structure and diagnostic repair. Trailing dots,
empty type operands, selected qualifier tokens, generic-method/multiple-return declarations,
constraints and module/package compositions now recover. Unregistered header formals shadow outer
names without becoming invented type candidates. Native workspace/refactoring assertions are
implemented for X99–X105. Selected native X33–X35 and X99–X108 have passing receipts across the
checkpoint and focused rerun recorded in the
[integration plan](../../../docs/errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).
This does not establish a complete native-suite pass. The run also drove server cancellation,
publication and file-rename lifecycle fixes; no additional AST state was needed for those fixes.
Class/interface composition headers retain the written name and body for structural queries.
Type prefixes in `extends`, `implements`, `delegates`, ordinary `incorporates` and `into` use the
real enclosing scope, including empty composition slots and qualified/generic leaf prefixes.
No incomplete class component, superclass or body declarations register. Visibility suggestions do
not prove valid inheritance or constraints; accepting a name leaves those normal diagnostics intact.
Shared X95 covers nine edit/repair variants in both drivers and now has passing native IntelliJ receipts.
No declaration names are fabricated.

The separate embedding `analyzeIncomplete` probe can validate intact receivers and ordinary
arguments in one standalone incomplete statement. The original overload handles trailing EOF;
explicit-cursor overloads also handle sites before closing braces/semicolons and in module member
files, using unsaved root/member overlays without altering source text. They retain simple
assignment/initializer and single return contexts, plus final nested call arguments; enclosing
calls do not choose an overload from a missing argument type. Consumer tests verify real
method scope, flow narrowing and source positions without selecting an overload or emitting the
damaged method. XdkAdapter's internal asynchronous cursor API now invokes this probe on its compiler
worker, returning copied facts without replacing normal diagnostics. It coalesces cursor requests
per document and feature and invalidates work on module edits, close, cancellation and shutdown.
Completion/signature handlers now consume it. The server propagates cancellation to cursor work,
leaves shared compilation intact and checks the captured document/module lifetime before completing
the response. Both capabilities are advertised for the opt-in compiler backend and tested over stdio.
Its explicit Kotlin copier now supplies accessible instance methods/properties, receiver-substituted
candidate signatures, argument spans/labels/types and a source argument slot based on top-level
commas. Completed method calls separately copy the compiler's selected instantiated signature and
written argument-to-parameter mapping. Incomplete overloads remain candidates; the first displayed
signature is not a compiler-selected overload. Unknown parameter mappings retain the full label
but omit parameter metadata so the client cannot default to a fabricated parameter-zero highlight.

Cursor inspection supports `receiver.|`, `receiver.pre|` and `receiver.method(|)` at supported
statement/final-argument boundaries. Completion filters by the decoded member token and replaces
its original UTF-16 span, including escaped identifiers. Calls with an editor-inserted closing
parenthesis can show candidates even when normal validation fails; successfully resolved calls
keep their exact selected signatures. Probe diagnostics do not replace normal diagnostics.
Bare-name and empty statement completion capture readable locals/parameters, declaration order,
shadowing and flow narrowing from the live compiler Context. Implicit members and static functions,
constants and nested types use access-adjusted TypeInfo. Type-name completion follows normal lookup
for explicit/wildcard/implicit imports and enclosing declarations. Module overlays share this scope.

Incomplete qualified, implicit and static calls fit written arguments using the compiler's ordinary
conversion, generic inference and named ordering rules. Copied candidates expose their expected
parameter types and written argument mapping. A pending `name=|` selects the named parameter for
each candidate; a slot after named arguments without a new label has no highlight. Even a single
candidate does not claim final overload selection. Unresolved formals remain formal.

Explicit cursor analysis now retains binary/conditional expressions and arguments following an
incomplete member expression. Completed function-valued calls expose signature types and written
argument slots separately from selected-method calls; their runtime targets and parameter names
are not guessed. Incomplete function calls validate a trial copy of the callee and written positional
arguments, preserving the full function signature for the missing slots. This covers narrowed and
generic function properties, function-producing expressions and captured lambda arguments. Named
arguments cannot be inferred from a function type. Ordinary constructor candidates reuse compiler
argument fitting, including overloads, defaults, named slots and explicit class type arguments.
Missing enclosing call/group parentheses and index brackets now retain the cursor's original
syntax and scope at statement/outer-delimiter boundaries. An explicit cursor at EOF also retains
missing block braces. This uses the existing incomplete syntax children, with no new AST state or
public API components; normal compiler diagnostics remain cached and visible. X75–X76 cover the
editor behavior. X77–X82 cover argument-value insertion, typed-prefix replacement, implicit
properties/constants and overload alternatives: empty final positional slots, pending named values
and direct final bare-name prefixes
offer compatible readable locals/parameters and implicit properties/constants, including
generic inference, substitutions and conversions. Property reads retain compiler access and
static-context checks, and shadowing locals take precedence even when unreadable. Ordinary
properties do not acquire local-variable flow narrowing. The compiler probes proposed names in trial
contexts; Kotlin consumes immutable accepted-value facts. X83–X85 extend constructor support to
qualified/implicit inner, virtual, annotated and formal types, required-type/provisional argument
inference and parenthesized array suppliers. Dimensions remain written arguments before the active
supplier slot. X86–X87 retain tuple/typed-tuple/list/set/map closers, declaration value terminators
and parameter-default closers before a body. Property initializers containing a cursor hole use their
source-owned validation context. No AST fields or clone-remapping rules are added. X88–X89 prepare
source-owned anonymous declarations, fit own/superclass constructors and preserve captured-local
syntax without capture analysis or emission. X90 adds empty/final-prefix single-dimensional array
size cursors with real constructor fitting, original-token replacement, active size hints and missing
bracket recovery. A written supplier after the cursor is parsed but is not validated by that prefix
query. Normal compilation still checks suppliers and element defaults. Compiler mode advertises `[` as
a signature-help trigger. X91–X96 add the bounded declaration-header recovery described above.
Multidimensional construction, ambiguous empty local declarations, missing map entries and
unterminated literal contents remain unsupported. Explicit cursor queries now recover missing
value operands within call arguments.
Remaining limits include non-type cursors inside identifiers and further member/call syntax after a typed
prefix. Qualified/grouped/compound arguments and
slots before later written arguments use full compiler validation. Type-valued receiver functions
and receiver-to-argument rewrites retain visible signature mappings. Lexical enclosing/imported
property candidates require ordinary readable-value validation and argument fitting. Argument
completion includes compiler-fitted scalar/empty collection literals, lexical enclosing instances
and lambda arity templates. Ordinary enclosing-instance completion uses the same compiler checks.
Arbitrary nested values and generated lambda bodies remain outside this boundary.

The snapshot records resolved types, type parameters, declaration/use ranges (including captures),
declared and selected-call signatures, written argument mappings and direct inheritance edges. The
compiler adapter compiles a module
root and its member tree together, taking unsaved source overlays ahead of disk. New unsaved member
files and implicit packages participate without temporary files. Source membership and text are
captured per attempt; member edits invalidate the module, and diagnostics publish with each open
file's own version. Closing an overlay reanalyses remaining members from disk; filesystem events
refresh membership and clear removed-file diagnostics.

Definitions and references share identities across one module compilation. Hierarchy items carry a
compilation token; items from before an edit return no results. Hierarchy includes declared `extends` and `implements` across the discovered/configured source
graph, with digest-bound handles for unopened files. It does not discover binary library sources
or infer conditional-mixin hierarchy edges. Implementation lookup uses compiler composition facts, including method/property redirects and
conditional bodies activated on validated concrete source types. Native/synthetic bodies and
unresolved runtime delegation do not acquire invented source targets (X153).

Module-root discovery follows the source-file/same-name-directory layout. Non-file URIs remain
single-source inputs. Workspace folders supply a discovered module/dependency graph; explicit
source settings override it. Queries compile that graph on demand without a persistent index. Compiler mode is the shipped default. See the
[module and recovery hardening results](../../../docs/errs-integration-plan.md#ninth-pass-java-parser-recovery-2026-09-22).

**Data Model:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/`
- `CompilationResult` - Compilation output with diagnostics and symbols
- `Diagnostic` - Error/warning/info with location
- `Location` - File position
- `SymbolInfo` - Symbol metadata (name, kind, signature, location)

### 3. IntelliJ Plugin (`lang/intellij-plugin/`)

An IntelliJ IDEA plugin providing XTC support:

**Core Components:**
- **`XtcLanguageServerFactory`** / **`XtcLspConnectionProvider`** - Out-of-process LSP server integration via LSP4IJ
- **`XtcNewProjectWizard`** / **`XtcNewProjectWizardStep`** - New Project wizard
- **`XtcRunConfiguration`** / **`XtcRunConfigurationType`** / **`XtcRunConfigurationProducer`** - Run configurations
- **`XtcTextMateBundleProvider`** - TextMate grammar for syntax highlighting
- **`XtcIconProvider`** - XTC file icons
- **`XtcEnterHandlerDelegate`** - IntelliJ-local Enter repair for brace/empty-block indentation
- **`XtcLanguageCodeStyleSettingsProvider`** / **`XtcCodeStyleSettings`** - IntelliJ code-style integration consumed by editor-side indentation repair

**LSP Server Launch:**
- Uses LSP4IJ's `ProcessStreamConnectionProvider` to launch the LSP server as a separate process
- `JavaProcessCommandBuilder` resolves the JBR 25 runtime automatically (no custom JRE provisioning)
- Out-of-process architecture provides classloader and crash isolation

**Build Configuration:**
- Targets IntelliJ IDEA 2026.2.3 (minimum build 262), using its free Community feature set
- Uses LSP4IJ 0.21.0; no Ultimate subscription is required
- Opt-in Starter/Driver compiler playbook runs with Ultimate features explicitly disabled
- Use `-PintellijLocalPath=/path` to use a local IntelliJ installation instead
- Plugin bytecode target is Java 25
- Searchable-options indexing is disabled by default for ordinary builds
- IDEs are extracted into the Gradle user home and shared by all checkouts (CI extracts them under `lang/.intellijPlatform/ides`)

### 4. VS Code Extension (`lang/vscode-extension/`)

A VS Code extension stub with:
- Extension manifest (`package.json`)
- Language configuration
- TextMate grammar inclusion
- LSP client setup (scaffolded)

### 5. Tree-sitter Integration ✅ COMPLETE

Full tree-sitter support for fast, incremental parsing:

**Grammar:** Generated by `TreeSitterGenerator` -> `grammar.js`
- 100% coverage: All 692 XTC files from `lib_*` parse successfully
- External scanner for template strings and TODO freeform text

**Native Libraries (`lang/tree-sitter/`):**
- Zig cross-compilation for all 5 platforms (darwin-arm64, darwin-x64, linux-x64, linux-arm64, windows-x64)
- On-demand build with persistent caching in `~/.gradle/caches/tree-sitter-xtc/`
- All platforms bundled in LSP server fatJar

**Kotlin Bindings:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/`
- `XtcParser` - Parser wrapper with FFM-based native library loading
- `XtcTree` - Parse tree
- `XtcNode` - Tree node
- `XtcQueryEngine` - Pattern matching queries
- `XtcQueries` - Predefined queries for declarations, references
- `TreeSitterLibraryLookup` - Custom library lookup for bundled native libs

## What Remains To Be Done

### Short-term (Complete LSP Features)

1. ~~**Wire up TreeSitterAdapter in LSP server**~~ ✅ COMPLETE
   - TreeSitterAdapter is now the default adapter
   - Out-of-process LSP server runs with JBR 25 (FFM API for tree-sitter)

2. ~~**Implement semantic tokens (Phase 1)**~~ ✅ COMPLETE
   - `SemanticTokenEncoder` classifies 18 AST contexts via single-pass O(n) tree walk
   - `TreeSitterAdapter.getSemanticTokens()` implemented and wired
   - Server advertises capability when `lsp.semanticTokens=true` (default)
   - Token types: keyword, decorator, comment, string, number, operator, type (heuristic),
     method (call-site heuristic), class/interface/enum/property/variable/parameter/namespace

   **Compiler tokens -- bounded implementation complete:**
   - Resolved type/property/local/parameter names and module-file identities
   - Declaration, readonly/static/abstract and modification modifiers where established
   - Java lexical tokens cover comments, literals and keywords; broader semantic classifications/modifiers remain follow-ups

3. **Complete VS Code extension**
   - Finish LSP client integration
   - Add commands (new project, run, build)
   - Package and test

4. **Polish IntelliJ plugin**
   - Test project wizard with `xtc init`
   - Verify run configurations work
   - Build and test plugin ZIP
   - Test out-of-process LSP server launch on all platforms
   - Continue reducing IntelliJ/TextMate/LSP overlap issues and stale sandbox failure modes
   - Keep the startup diagnostics in `XtcEditorStartupActivity` until the runIde sandbox behavior is fully stable
   - If sandbox editor/theme issues recur, log both UI theme and editor color scheme together instead of forcing only one side
   - Document or automate sandbox reset for stale color/folding state during local plugin development

5. **Highlighting and completion follow-up**
   - Improve semantic-token granularity for declaration names vs type references
   - Distinguish field/property references from locals and parameters more consistently
   - Improve generic/type-parameter highlighting in both semantic tokens and TextMate fallback
   - Continue aligning semantic-token classifications with TextMate scopes so the two paths do not fight each other in IntelliJ/LSP4IJ
   - Add focused regression tests for `TestModule.x`-style cases:
     - declaration-start completion should prefer `class` over `Class`
     - module/package hover should resolve
     - return type / parameter / property coloring should not collapse into one broad scope
   - Review auto-import suggestions for built-in/meta-types like `Class`, `Module`, and `Type` so they do not produce noisy quick-fix candidates in declaration contexts

### Medium-term (Compiler Integration)

6. **Extend the compiler adapter**
   - Diagnostics, bundled libraries, semantic snapshots, module overlays and cross-file navigation are implemented
   - Preserve regression coverage for type-parameter declarations and anonymous-class captures
   - Versioned dependency artifacts/source indices and consumer invalidation now have an explicit host API
   - Explicit source roots/edges now enable automatic dependency builds and consumer diagnostic refresh
   - Workspace discovery and on-demand graph queries cover unopened sources, unsaved import-edge changes and dynamic folders; persistent indexing and proof memory remain
   - Direct source type hierarchy, type-definition and actual method-chain implementation lookup are implemented
   - Static selected-call hierarchy, resolved-name tokens, read/write highlights and bounded hints are implemented
   - Scope, imported types, static lookup and bounded incomplete-call fitting now have compiler-backed consumers
   - Bounded rename now has named-label and binding checks; lifecycle and migration regressions cover the integrated API. Validate extracted PRs independently
   - Extend the documented syntax/callable limits only with compiler evidence

   The selected implementation uses the existing javatools compiler. The older research-fork
   rewrite schedules are not the current integration plan.

7. **Compiler recovery and adapter policy**
   - Ship compiler mode by default; retain explicit Tree-sitter and mock selections
   - Compiler mode stays Java-only; improve Java parser recovery without a Tree-sitter fallback
   - A combined adapter was an earlier proposal and is not the current implementation plan

### Long-term (Advanced Features)

8. **Refactoring support (cross-file)**
   - Compiler rename already covers bounded locals/private parameters and graph-backed source types, static members, ordinary method/property families and aliases, including simple member-file moves
   - Extend target/resource scope and harden large-graph proof memory (L55/L62)
   - The October 2 L62 audit adds conditional members without host contracts, closed-consumer
     source identities, independent conditional families and nested generic method/property delegation
     to regression coverage. Shared X159/X160 cover cross-file rename and Undo in both drivers;
     both pass in VS Code `run-RGeVCM` and IntelliJ `run-17726972701009018455`, alongside 78 passing
     backend audit tests. Bounded cross-package type moves now rewrite qualifications (X161 passes in both editors). Explicit graph relocation is implemented through the host settings transaction;
     bounded union/cyclic written-callable proof is implemented (X164/X165; current acceptance below).
     Parameterized/annotated alternatives and wider generated routes remain open; binary contracts remain read-only
   - Whole-return-expression extraction and adjacent single-use typed returned-local inline are implemented with binding proof; X156/X157 pass in both editors
   - Complete typed local initializers can also be extracted or receive an adjacent same-written-type local inline; unused locals can be removed only with compiler-proven constant, side-effect-free initialization (X177–X180, acceptance below)
   - Private same-owner expression helpers with explicit stable inputs are implemented (X181–X184, acceptance below)
   - Missing private same-owner methods can be proposed from resolved declaration parameters, supported literal types, compiler-established block locals and explicit return/typed-initializer contexts, including proven same-owner instance receivers and named enclosing-class static qualifiers (X185–X200), plus explicit public repairs in other writable ordinary classes of the same module, including companions, earlier compiler-typed locals and explicit initializer results (X201–X208), plus reachable configured source dependencies using existing aliases or adding imports for already available destination dependencies (X209–X215; receipts below)
   - Wider expression/statement extraction, other missing declarations, mutable captures, broader inline and global safe delete remain unimplemented (L63)

9. **Code actions (semantic)**
   - Organize imports is implemented in Tree-sitter and bounded by compiler proof in XdkAdapter
   - Auto-import is implemented in Tree-sitter; XdkAdapter offers proven unresolved public-type imports
   - ~~Generate doc comment~~ ✅ COMPLETE (tree-sitter)
   - XdkAdapter generates individual/all-required inherited methods from source and read-only XDK/binary contracts at a class name, with compiler-selected generic/conditional, qualified and compound signatures, atomic imports and safe defaults; whole-graph proof permits the intended call/descendant changes and preserves other bindings; generated bodies use `TODO()`. Both actions use `refactor.rewrite`, so they appear as class intentions without requiring a diagnostic at that location
   - Unvalidated computed/named defaults, unsupported constant kinds and annotated/unrenderable type spellings remain withheld; missing implementations use fresh declaration analysis, never failed-compilation TypeInfo. The current batch validation is recorded separately from previous X122 receipts
   - Broader semantic fixes, compiler doc generation and the remaining extraction/inline/safe-delete scope remain unimplemented (L63)

9. **Debugging (DAP)**
   - Debug Adapter Protocol integration
   - Breakpoints, stepping, variable inspection

## Architecture Principle

**CLI is source of truth**: IDE plugins shell out to `xtc` CLI commands.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         IDE Plugins (Thin Wrappers)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │   IntelliJ   │  │    VS Code   │  │     Zed     │  │   Eclipse   │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘  └──────┬──────┘   │
│         │                 │                 │                 │         │
│         └─────────────────┴─────────────────┴─────────────────┘         │
│                                   │                                     │
│                          (shell out to CLI)                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│                           ┌───────▼───────┐                             │
│                           │   xtc CLI     │                             │
│                           │  init | run   │                             │
│                           │  build | test │                             │
│                           └───────┬───────┘                             │
├───────────────────────────────────┼─────────────────────────────────────┤
│         ┌─────────────────────────┼─────────────────────────┐           │
│         │                         │                         │           │
│  ┌──────▼──────┐          ┌───────▼───────┐         ┌───────▼───────┐   │
│  │ Initializer │          │   Compiler    │         │    Runner     │   │
│  │  (templates)│          │   (xcc)       │         │    (xec)      │   │
│  └─────────────┘          └───────────────┘         └───────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                           ┌───────────────┐                             │
│                           │  LSP Server   │ ◄── IDE language features   │
│                           │ (hover, etc.) │                             │
│                           └───────────────┘                             │
└─────────────────────────────────────────────────────────────────────────┘
```

## Design Decision: LSP4IJ over IntelliJ Built-in LSP

The IntelliJ plugin uses Red Hat's [LSP4IJ](https://github.com/redhat-developer/lsp4ij) (`com.redhat.devtools.lsp4ij`) rather than IntelliJ's built-in LSP support (`com.intellij.modules.lsp` / `ProjectWideLspServerDescriptor`).

### Why LSP4IJ

**DAP support.** IntelliJ has no built-in DAP (Debug Adapter Protocol) client. LSP4IJ provides a DAP client via the `debugAdapterServer` extension point, which is required for `lang/dap-server/` integration. Without it, we would need to write thousands of lines of IntelliJ-specific debug infrastructure (`XDebugProcess`, `XBreakpointHandler`, `ProcessHandler`, variable tree rendering, stack frame mapping, expression evaluation) -- the exact opposite of IDE independence.

**LSP feature coverage.** LSP4IJ supports LSP features that IntelliJ's built-in LSP (as of 2026.1) does not:

| Feature | LSP4IJ | Built-in LSP |
|---------|--------|-------------|
| Code Lens | Yes | No |
| Call Hierarchy | Yes | No |
| Type Hierarchy | Yes | No |
| On-Type Formatting | Yes | No |
| Selection Range | Yes | No |
| Semantic Tokens | Full | Limited |
| LSP Console (debug traces) | Yes | No |
| DAP Client | Yes | No |

Server support depends on the adapter: Tree-sitter supplies run code lenses, the compiler supplies
direct source type hierarchy, and call hierarchy remains unimplemented. See the
[adapter matrix](#adapter-capability-matrix),
[`lsp-feature-tiers.md`](./lsp-feature-tiers.md) for the LSP capability tiering and
[`idea-specific.md`](./idea-specific.md) for IntelliJ-specific follow-up work.

**Standard protocol types.** LSP4IJ uses Eclipse LSP4J types (`org.eclipse.lsp4j.services.LanguageServer`, `IDebugProtocolServer`) -- the same library our LSP and DAP servers use. IntelliJ's built-in LSP uses internal IntelliJ types.

### What LSP4IJ Does Not Affect

IDE independence is preserved either way. The shared, IDE-independent code is:

```
lang/lsp-server/     -- LSP server (Eclipse LSP4J, stdio)
lang/dap-server/  -- DAP server (Eclipse LSP4J debug, stdio)
lang/dsl/            -- Language model, generates TextMate/tree-sitter/vim/emacs
lang/tree-sitter/    -- Grammar + native libs
```

The IntelliJ plugin (`lang/intellij-plugin/`) is inherently IntelliJ-specific. The choice between LSP4IJ and built-in LSP only affects which IntelliJ API the thin wrapper calls. The servers are unchanged.

### Costs

| Concern | Assessment |
|---------|-----------|
| User installs extra plugin | Minor -- one dependency (`com.redhat.devtools.lsp4ij`) |
| Duplicate server spawn race condition | Known LSP4IJ issue ([#888](https://github.com/redhat-developer/lsp4ij/issues/888)), harmless -- extras killed in milliseconds |
| Third-party maintenance risk | LSP4IJ is actively maintained by Red Hat, releases every ~2 weeks |

### Reference

The `xtc-intellij-plugin-dev` reference repo demonstrates IntelliJ's built-in LSP in ~29 lines. That is intentional -- it serves as a minimal "getting started" example. The production plugin requires DAP support, advanced LSP features, and the LSP Console, which are only available through LSP4IJ.

## Known Issues and Follow-ups

> **Last Updated**: 2026-04-09

### DAP Integration (Blocking for Debug Support)

1. **DAP server JAR not packaged into sandbox** -- The `plugin.xml` registers the
   `debugAdapterServer` extension point and the factory/descriptor classes compile, but
   `dap-server` has no fat JAR task, no consumable configuration, and no `copyDapServerToSandbox`
   task. At runtime, `PluginPaths.findServerJar("xtc-dap-server.jar")` will always throw
   `IllegalStateException`. To ship DAP support:
   - Add a `fatJar` task in `lang/dap-server/build.gradle.kts`
   - Add a `dapServerElements` consumable configuration
   - Add a `dapServerJar` consumer configuration in `intellij-plugin/build.gradle.kts`
   - Add a `copyDapServerToSandbox` task mirroring the LSP copy pattern
   - Wire `prepareSandbox` and `runIde` to depend on it

2. **DAP server launch** -- The DAP descriptor's `startServer()` needs to use
   `JavaProcessCommandBuilder` (matching the LSP connection provider pattern) to launch
   the DAP server out-of-process with the IDE's JBR 25.

3. **Formatting config / editor path split** -- LSP on-type formatting is implemented and
   well tested, but IntelliJ does not always send the first Enter-after-`{` path through
   `textDocument/onTypeFormatting`. The plugin now includes a local Enter repair that
   respects IntelliJ code style settings; keep treating that as an IntelliJ-specific
   complement to the LSP formatter, not as the primary formatting engine.

4. **Root composite configuration-cache reuse still misses with lang attached** --
   isolated `:lang:intellij-plugin:build` now reuses configuration cache with
   IntelliJ IDE caching enabled, but the outer root `./gradlew build` graph still
   misses on `JavaRuntimeMetadataValueSource`. This appears tied to the IntelliJ
   Platform Gradle plugin / composite-build runtime metadata path.

### Tree-sitter / Semantic Tokens

~~3. `XtcNode.text` byte-vs-char offset~~ -- FIXED: Added UTF-8 aware substring extraction.
~~4. `SemanticTokensVsTextMateTest` native memory leak~~ -- FIXED: Uses `.use {}` now.
~~5. `SemanticTokenEncoder.nodeKey` collision~~ -- FIXED: Key now includes node type hash.
~~8. Semantic tokens crash on rename~~ -- FIXED: `XtcParser.parse()` was passing `oldTree`
for incremental parsing without calling `Tree.edit()`, producing nodes with stale byte
offsets. Now always does full reparse (still sub-ms). Defensive bounds checking added to
`XtcNode.text`.
~~9. EDT violation in LSP connection setup~~ -- FIXED: `XtcLspConnectionProvider.init {}` called
`ProjectJdkTable.getInstance()` (prohibited on EDT). Moved to `start()` which runs off EDT.
~~10. Pipeline logging gaps~~ -- FIXED: `XtcQueryEngine.executeQuery()` now logs query name,
all find methods log per-match details (symbol kind, name, location). `TreeSitterAdapter`
methods (`getFoldingRanges`, `getSemanticTokens`, `getCodeActions`, `getDocumentLinks`) now
consistently log their inputs and results.
~~11. Unicode characters garbled in logs~~ -- FIXED: Replaced Unicode arrows (`U+2192`) and
em-dashes (`U+2014`) with ASCII `->` and `--` in all logger output, test display names, and
annotations. The 3-byte UTF-8 characters were rendering as `a-hat` in log viewers using
ISO-8859-1/Latin-1 encoding.

### Build System

~~6. Windows IDE path~~ -- FIXED: Updated to 2026.1.
~~7. Composite build property isolation~~ -- FIXED: `project.findProperty()` and
`providers.gradleProperty()` only see the included build's own `gradle.properties`,
which doesn't exist for `lang/`. Properties like `lsp.semanticTokens`, `lsp.adapter`,
`lsp.buildSearchableOptions`, and `log` were silently falling back to hardcoded defaults.
Fixed by using `xdkProperties` which resolves through `XdkPropertiesService` (loads from
composite root's `gradle.properties` at settings time).

## Related Documentation

- **[plan-tree-sitter.md](./plan-tree-sitter.md)** - Tree-sitter grammar status and development guide
- **[lsp-feature-tiers.md](./lsp-feature-tiers.md)** - Historical research-fork proposal; its APIs, coverage figures and schedules do not describe the current compiler integration
- **[idea-specific.md](./idea-specific.md)** - IntelliJ-specific roadmap beyond standard LSP behavior
- **[vscode-specific.md](./vscode-specific.md)** - VS Code-specific roadmap beyond standard LSP behavior
- *Internal documentation* - Comprehensive architecture analysis and compiler modification plans

The current acceptance batch adds shared X124 external-resource configuration/repair and X125 precise
platform regressions in both editors. See the [current receipts](../../../docs/errs-integration-plan.md#next-implementation-batch-l69l71--plat2c-2026-09-29);
prior 128-case receipts do not cover these additions.

L69 range/delta is now implemented with negotiated capabilities, bounded detached token history,
close/restart retirement and semantic refresh. Shared X126 and backend/transport regressions pass.
Full tokens remain available to clients without range/delta capabilities.

L70 implements all six lazy resolve endpoints with bounded detached handles, stable identities,
stale-result rejection and negotiated eager fallbacks. Completion insertion/type details remain
eager; compiler proof occurs before offering an action. IntelliJ resolves only the selected action
and applies its version-checked edit through a normal undo command. This supersedes the temporary
eager-action workaround for LSP4IJ 0.21.0's undo-transparent application. Backend and selected
VS Code/IntelliJ acceptance pass, including X105/X122/X127/X131.

L71 pre/post file-operation handlers negotiate independently. Compiler proof covers bounded
member/package renames, combined batches and safe container moves. IntelliJ's Rename/Move
handlers preflight before disk mutation and apply references/paths in one global undo command;
raw VFS changes still cannot promise reference updates. X128 and X130 drive the real host actions.
Same-name type files can now move across compiler-proven package namespaces with qualification
rewrites, companion resources and binding proof; X161 Move/Undo/Redo passes in both editors. Explicit
source-graph relocation now uses the guarded host proposal/settings path (X162/X163).
Ordinary LSP pre-operation null replies cannot veto arbitrary host file moves.

PLAT2c/L67 now imports the evaluated Gradle model in both hosts and exposes effective source/resource
paths and origin. Explicit overrides survive refresh; invalid model files retain the last valid host
import. Gradle export/prepare actions run in the host, never in LSP. Both `.gradle` and `.gradle.kts`
are handled by Gradle itself. TestKit, importer/server tests and shared X129 pass.
Compiler import progress is cancellable in both hosts, with one active import per project/folder.
Pending, cancelled or failed builds cannot replace the last accepted model via a file watcher;
successful retries can. The paths view includes the last outcome/time. Project/folder closure
cancels its owned import. Generated files are not rolled back, and failed-output retention lasts
for the current editor session. See the UI5/UI6 import continuation and manual cancellation steps.
Automatic IntelliJ Gradle-sync refresh and successful VS Code Gradle-task refresh now follow an
initial import. A shared evaluated init script aggregates nested/composite build roots, including
roots without the Ecstasy plugin. Exported-report watchers retain accepted inputs after malformed
output, and import identities reject late daemon output after cancellation. Project/folder removal
retires the owner; explicit overrides survive. X263–X265 cover these paths in both clients; see the
[batch receipt](../../../docs/errs-integration-plan.md#compiler-workspace-synchronization-batch-ui5ui6-2026-10-05).
Earlier X124 receipts used explicit native VFS refresh. The strengthened case creates previously
missing external roots without opening them or manually refreshing; those earlier receipts remain
historical and do not prove automatic watch ownership.

External-root follow-up (2026-09-30): source roots join resource roots in dynamic external watch
registrations. IntelliJ owns the native watch leases and refreshes those roots asynchronously
while focused. Missing-root creation, settings replacement and disposal have new unit/driver
coverage. Backend and selected X124/X134 pass in both editors without manual VFS refresh.

L71 native Move follow-up (2026-09-30): the Community Move delegate preflights one or multiple
source/container moves before disk mutation. Parent changes are applied through VFS because
LSP4IJ 0.21 only applies the new basename. References and paths share a global undo command.
Shared X130 covers two discovered module containers and embedded resources with Undo/Redo;
selected acceptance passes in both editors. The October 2 X161 continuation adds bounded type
relocation with qualification rewriting; selected Move/Undo/Redo acceptance passes in both editors. Explicit-graph relocation now has a host-persisted proposal and X162/X163 Undo/Redo coverage. Search `// TODO LSP4IJ:` in the plugin for removable upstream compatibility bridges.

L70 follow-up (2026-09-30, selected acceptance passed): codeLens/documentLink/inlayHint/workspaceSymbol
resolve endpoints now preserve stable identities and reject obsolete detached handles. Inlay
tooltips expose compiler-derived declaration/call signatures. IntelliJ now resolves the selected
code action through a client command and version-checks its edit in a normal undo command,
working around LSP4IJ's undo-transparent application. X105/X122/X127 and new shared X131 are
passing acceptance gates in the current receipt; earlier eager-action receipts remain historical.

Editor settings follow-up: [UI1–UI7](../../../docs/errs-integration-plan.md#editor-configuration-and-feature-controls-ui1ui7)
now tracks the full settings inventory, common scope/precedence semantics, Community IntelliJ
pages, VS Code native Settings and commands, live/restart behavior, effective capability/status
views and shared acceptance. Both plugins now expose L72 transport preferences; VS Code also exposes
save ownership, while IntelliJ uses native Actions on Save. Formatting-on-save has a single owner.

The [2026-09-30 acceptance checklist](../manual-test-plan.md#acceptance-checklist-for-the-2026-09-30-batch)
now separates shared automation from remaining manual/native, protocol-lifetime and settings-UI
checks for the complete follow-up batch. X130–X132 alone are not full acceptance evidence.

Both editors now offer **Ctrl+Alt+X, then L** (macOS: Control+Option+X, then L) for quick log
show/hide within UI6. IntelliJ selects LSP4IJ's Log tab in Language Servers; VS Code reveals its
Ecstasy Language Server Output channel. X135 covers the actions; physical shortcut dispatch and
custom docking/keymaps remain manual checks. No separate log process or editable file is created.
The [current acceptance record](../../../docs/errs-integration-plan.md#watcher-move-and-log-view-acceptance-follow-up-2026-09-30)
distinguishes the 140-case catalog from selected passing runs.


### Editor settings checkpoint (2026-09-30)

UI1–UI7 now have a bounded implementation: shared preference semantics, IntelliJ application/project
pages in the existing LSP4IJ store, VS Code native settings, transport restart wiring, live Code Style
and inlay refresh, and effective configuration/queue views. X136–X139 extend the catalog to 144 cases.
Selected X118/X132/X135–X139 pass in both editors (plus IntelliJ startup and zero IDE errors), as
recorded in the [settings receipt](../../../docs/errs-integration-plan.md#editor-settings-implementation-batch-ui1ui7-2026-09-30).
This is not a full-catalog rerun. Full synchronization, native editor save ownership and
compiler as the shipping adapter remain defaults (changed from Tree-sitter on October 6). Incremental transport does not mean incremental
compilation. Invalid formatting settings and late replies cannot replace the last valid snapshot.

LSP4IJ 0.21.0 does not implement native `willSaveWaitUntil`; its incorrect capability flag is cleared,
its server-save control is disabled and
native Actions on Save remains the supported path. VS Code guards the server save hook against each
document's current native format-on-save preference. The formatter still does not wrap, align or
normalize operator spacing. Broader UI1–UI7 work (advanced runtime controls, richer build/import
status, source attachment editing, log export/retention and remote-workspace acceptance) is not
claimed complete by this checkpoint.


L80/L81 protocol hardening adds shared X140/X141 (146 total scenarios): UTF-16 ranges after astral
characters and runtime server trace switching. X136/X137/X140/X141 pass in both editors, plus
IntelliJ startup with zero IDE errors. See the [protocol validation record](../../../docs/errs-integration-plan.md#protocol-hardening-batch-l80l81-2026-09-30).
Server progress, cancellation, lifecycle and refresh have controlled protocol
regressions; partial results and visible long-operation/cancel acceptance remain separately tracked.

Previous full-catalog checkpoint: 150 scenarios, including X142 initializer navigation/rename,
X143 partial workspace symbols, X144 guarded client edits and X145 progress/cancel/restart. IntelliJ passes all 150 plus
startup in `run-1843149430446112481`, with zero IDE errors and successful shutdown. Full VS Code
`run-06Z6tq` passes 149/150; X130's host repaint exception remains a failure after successful
Move/Undo/Redo/resource assertions. No completed mutation is replayed or failure hidden.

Generic IntelliJ server text edits now validate transmitted document versions/epochs and recheck
all targets within one native Undo command. Resource/snippet/confirmation edits remain refused by
this generic path. Save All uses a document-based formatting service for dirty closed tabs, avoiding
LSP4IJ 0.21.0's nullable-editor reply bug; shared X139 proves both open and closed documents.
Delayed no-op directory watches preserve incomplete queries against current open overlays; real
closed-source/resource changes still invalidate them. These corrections add no embedding or AST API.
See the [final batch receipt](../../../docs/errs-integration-plan.md#final-native-and-batch-receipt-2026-09-30)
and [audit](../../../docs/errs-audit.md#native-save-all-and-fixture-cleanup-follow-up-2026-09-30).

Reliability follow-up (2026-10-01): X146/X147 bring the catalog to 152, with new provider-refresh
and late-report assertions passing in both editors after the native service-settings correction.
VS Code effective-configuration publication now rejects superseded requests/settings/connections; IntelliJ report publication also checks
captured settings and connection lifetime. An isolated Explorer reproduction runs without Ecstasy
to investigate X130 without hiding its failure. The opt-in packaged platform workload records
latency, queue names/counts, sampled heap and process exit. Thirty cycles across three processes pass;
the measured local baseline does not establish release budgets or prolonged soak acceptance.
No new language capability, embedding API or AST field is added.

See the [reliability validation receipt](../../../docs/errs-integration-plan.md#reliability-validation-receipt-2026-10-01)
for the eight selected cases, native initial failure and focused rerun. The final IntelliJ guard
rechecks graph/service settings and original connection identity on the EDT, including restarts
between reply arrival and publication. The extension-free Explorer probe did not reproduce X130;
the intermittent full-run failure remains open even though the selected attempt passes.


X130 isolation update (2026-10-01): two controlled Explorer-refresh probes now reproduce the exact
post-Paste exception with Ecstasy absent. The normal X118–X130 sequence passes twelve cases and
fails only X130's host repaint after successful Move/Undo/Redo/resource checks; its compiler reply
is an empty edit. No compiler or production client change is warranted by that defect. A native
window-focus guard fixes separate harness Undo failures. The upstream repair remains open and
X130 is not relabeled as passing. See the
[diagnosis and reproduction](../../../docs/errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01).


L80 final audit (2026-10-01): all current provider/response families have a recorded capability
contract. Link tooltips and per-signature active parameters now honor client support; legacy
signature highlighting uses the selected overload. Pull diagnostic related information is gated
independently of push and related-document support. Compiler-only rename proposals are no longer
advertised by other adapters. No language feature, embedding API or AST node is added. Exhaustive
provider inventory and rich/reduced protocol tests cover the changes; the shared catalog remains
152 and no new native receipt is claimed. See the
[audit and validation](../../../docs/errs-integration-plan.md#l80-final-capability-contract-audit-2026-10-01).
L80 is complete for current producers; L81/L82 acceptance and X130's upstream Explorer failure remain
open. Future optional response fields require new negotiation checks.


L81 follow-up (2026-10-01): connection-owned progress now retires late successful create replies,
shows file/workspace and live compiler queue activity, and stops periodic reports on completion or
close. All five refresh providers have controlled lifecycle regressions; X146 observes each host's
negotiated native refresh handlers. Malformed-request recovery and two-process same-URI/token isolation
are covered over real stdio. This adds no language capability or AST/embedding API. See the
[L81 checkpoint](../../../docs/errs-integration-plan.md#l81-progress-refresh-and-transport-checkpoint-2026-10-01)
for exact validation: IntelliJ visible Cancel passes; VS Code physical-click remains open.
Two native IntelliJ project frames and two separate normal VS Code instances pass overlapping
close/reopen, sibling reply ownership, source preservation and process-exit checks. VS Code restores
the closed window's dirty buffer from actual hot-exit backup; shared-Electron-process windows remain
separate coverage. See the [native lifetime receipt](../../../docs/errs-integration-plan.md#l81-native-projectwindow-lifetime-batch-2026-10-01).
The larger IntelliJ fixture's bulk-replacement freeze is
retained as an L82 scale investigation. Upstream defects and
removable bridges are centralized in the [UP register](../../../docs/errs-upstream-issues.md).

L82 scale diagnosis: UP17 is now independently reproduced in IntelliJ's range-marker tree without
an LSP client. The decorated native replacement still fails the freeze gate; the opt-in
[probe and receipts](../../../docs/errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01)
separate marker-update time from compiler time. Large-file responsiveness remains open; no
advertised language capability or AST/embedding API changes as a result of this diagnosis.

L67 server response follow-up: inferred-hint rendering and lexical/semantic token merging now
avoid repeated whole-model scans. A packaged 20,000-local fixture measures a one-line hint reply at
11.7 ms (7.3 ms repeated), versus 132.7 seconds before; repeated full tokens take about 147–169 ms.
The [measurement receipt](../../../docs/errs-integration-plan.md#l67l82-semantic-response-measurements-2026-10-01)
separates compilation, query work and output. Cold project queries still compile a separate project
snapshot, and native highlight application/UP17 remains independent. These are local measurements,
not agreed cross-platform latency or memory budgets; no new compiler or AST API is introduced.

The extended platform workload has two completed 1,200-cycle edit/cancel sessions with process
retirement and unchanged sources. Its planned third session was deliberately interrupted, so this
is partial acceptance, not an overall green run. Repeated post-GC growth from about 83 to 98 MiB
still needs ownership analysis. Full combined/native validation remains a separate checkpoint;
see the [L82 receipt](../../../docs/errs-integration-plan.md#l82-bounded-extended-workload-checkpoint-2026-10-01).

L65 implementation lookup also follows existing compiler into/capped method redirects to written
source bodies, without body generation. Two manual-module and 27 existing lookup regression
tests pass; those manual-module editor actions have not been run in the native harness.

L63 now also produces `refactor.extract` for an exactly selected integer/string/character literal
returned from a block: atomic immutable-local insertion, fresh source name and complete graph proof.
General expression extraction and other semantic transformations remain open. Shared X148 includes
both editor drivers and Undo/Redo; selected acceptance passes in both editors.

Conditional-incorporation formal names now navigate to the actual mixin formal rather than being
classified as declarations. Rename proof retains predefined receiver class/access identity. Both
manual-module positive rename examples, wider regression and new editor cases pass; exact
receipts and extraction commit groups are in docs/errs-integration-plan.md.


L64 adds declaration-name suggestions from written named types and Java-parser-owned file/member/
statement keyword/template contexts. Templates support negotiated snippet stops with literal defaults
for minimal clients. That initial checkpoint (`5826d831e`) needs no new AST/embedding API. Shared X149/X150 cover both drivers;
both editor cases pass. IntelliJ constrains its advertised indentation mode for LSP4IJ UP18.
The next L64 continuation retains empty property/parameter name slots after complete named types,
including primary constructors and EOF. It reuses the existing partial cursor node, adding a
factory/accessor but no AST fields or semantic bindings. Shared X151 passes in both editors;
42 Java and 148 LSP tests pass without skips.
Ambiguous local-name slots, compound/inferred-name suggestions and broader template contexts remain outside this boundary; see the L64 continuation in docs/errs-integration-plan.md.


The L64 real-source continuation extends name proposals to wrapped/compound types, adds typed
scalar and collection arguments, enclosing-instance argument proposals, and guarded self-bound
completion. Anonymous body and block-lambda templates use compiler syntax; loop control words
respect callable boundaries. Platform's actual CircularBuffer class covers anonymous enclosing
receivers, array dimensions and callback signature metadata. Shared X97/X108/X150/X151 are
extended and X152 adds enclosing/indexed-call acceptance in both editors. All five selected
cases pass in both editors, alongside 65 Java and 410 LSP tests with no failures or skips.
Exact receipts and remaining exclusions are recorded in docs/errs-integration-plan.md; completion remains partial
for arbitrary damaged syntax and inferred declaration names.


The L64 closure batch extends enclosing-instance completion to ordinary expressions, inferred
local naming to useful written initializer clues, and lambda argument snippets to arities accepted
by the whole-call fitter. Empty collections continue to use compiler-validated plain values.
The shared playbook and both native drivers include the additions, with passing receipts in both editors. Ambiguous local syntax and arbitrary nested value/body synthesis remain explicit limits.

L65 adds property-accessor redirect traversal and explicit non-written implementation refusals.
Shared X153 covers covariant/conditional targets and runtime-only delegation; X154 covers more
read/write classification. Binary-source overload selection refuses absent or ambiguous metadata.
The L65 backend gate passes 78 tests without failures or skips. Concrete validated source types
now contribute conditional-mixin implementations. X153/X154 pass in both editors; no runtime target enumeration is claimed. Full-catalog
acceptance issues and combined receipts are recorded in the integration plan.


October 2 L62 continuation: compiler rename method/property families now include validated concrete
source types, sharing L65's source-type enumeration. Conditional generic adoption is checked by the
same before/after graph proof. Shared X155 adds rename/Undo coverage to both editor drivers; execution
is pending. Existing union/runtime/binary and configured-consumer boundaries remain explicit. This
adds no advertised LSP capability and does not close L62 or the editor stability follow-ups.

L62 follow-up also supports written, non-synthetic bodyless class method contracts (`SansCode`) for rename family proof. Such declarations still have no executable implementation target. The 122-test rename/action/lookup regression batch and both driver compilations pass; X155 editor execution is pending.


October 2 L63 continuation: extract local accepts complete return expressions with a written single
expected return type, in addition to primitive literals. Compiler proof tracks the original bindings
and calls through exact text relocation; arbitrary subexpressions and conditional/multiple returns
remain refused. X156 is implemented in both drivers with exact edits and Undo/Redo. Execution of
this continuation is pending; general extraction and the other semantic transformations remain open.

October 2 L63 continuation: whole-return-expression extraction and adjacent returned-local inline
are implemented. Both preserve written expected types and compiler-proven relocated bindings.
Inline requires one read, the next statement to be that return, and matching written types;
comments, intervening statements and unproven cases are refused. 31 focused backend tests pass,
both drivers compile, and shared X156/X157 implement action/diagnostics/Undo/Redo acceptance.
Those editor cases have not yet run. General extract/inline, extract method, missing declarations
and safe delete remain open.

October 2 continuation validation: 204 combined backend tests pass without failures or skips.
Shared X155–X158 pass in both editors (VS Code `run-Um9auo`; IntelliJ
`run-13592235693442712133`, including START, zero IDE errors, Ultimate disabled). This validates
the new conditional/bodyless rename, returned-expression extraction/inline and import source-link/
alias-editing behavior. Earlier pending notes above record the implementation checkpoint, not the
current acceptance state. Binary source-index replacement tests also fixed missing source URIs in
unopened graph navigation; binary-only metadata remains usable without invented source locations.
Broader transformation, formatting, graph-scale and release-gate tasks remain open.


L62 graph relocation follow-up: compiler mode advertises `xtcFileMoveProposal: 1` for complete
file-move proposals with source/resource graph replacement. Both hosts persist settings and paths
through their existing guarded Undo/Redo logic. Containers and same-name module roots are supported;
root-only moves pin resolved default resources left behind. Resource value proof includes lowered
string/byte literals and every configured resource consumer. X162/X163 share these assertions in
both drivers; selected receipts are in the
[integration plan](../../../docs/errs-integration-plan.md#l62-explicit-source-graph-relocation-2026-10-02).
Standard `willRenameFiles` cannot save settings and still refuses graph changes. VS Code cannot veto
an arbitrary host move with an empty participation reply. Combined root rename/move is extended
in the October 3 continuation below. Resource-only graph relocation and moving workspace
configuration ownership remain unsupported. No AST API changes.
Selected X118/X161/X162/X163 passes in both hosts. IntelliJ required UP19's descendant-connection
bridge for directory Move/Undo/Redo; post-Redo unsaved edits also pass without stale synchronizers.
The bridge and its removal gate are recorded in the [upstream register](../../../docs/errs-upstream-issues.md).


### L62 callable alternatives and recursive contracts (October 3)

Compiler mode now detaches receiver/dispatch proof at explicit callable sites, using existing
compiler APIs. Plain source union receivers join all written method contracts for rename, including
nested unions and closed cross-module consumers. Recursive delegates retain finite interface
contracts and back edges; this permits proven rename without guessing a runtime implementation.
Generated constructors/accessors are not independent editable methods. The following
substituted-receiver continuation extends this checkpoint to bounded generic/formal/annotated
union operands and nested generated delegation. Shared X164/X165 cover rename and Undo in both editor drivers. X119/X120/X121/X164/X165
pass in VS Code `run-VqFiDW` and IntelliJ `run-12344641320847299097` (START also passes; zero
IDE failures and no internal-error log markers). The backend gate passes 202 tests without
failures/errors/skips; formatting checks pass. See the October 3 integration-plan receipt for
exact validation and extraction dependencies. L62 remains partially implemented.


### L62 substituted receivers and nested union delegation (October 3)

Compiler mode retains detached type arguments, source formal identities, annotation identities and
supported constant arguments at union operands. Each nested delegate branch retains its receiver,
written contracts, delegate properties and typed cycle anchors. Source type renames translate
anchors inside the type structure; printed type names are not proof keys. All implementation is
on the LSP side using existing compiler APIs, with no new AST state.

Shared X166–X168 exercise generic, annotated and nested-delegate rename through closed consumers
and Undo in both drivers. The combined backend gate passes 228 tests without failures/errors/skips;
both drivers compile and formatting checks pass. X164–X168 pass in VS Code `run-oSVuMJ` and
IntelliJ `run-907034856191389577` (START also passes). IntelliJ's first attempt stalled in the
test Driver's modal-focus race, repaired as UP20; dedicated focus/replay regression
`run-7549109475022481665` also passes. There are no recorded IDE failures or internal-error
markers in the successful editor logs. Unsupported annotation constants, dependent/dynamic
type shapes, annotations around whole relational types and wider generated transformations remain
open. L62 remains partial; this does not change read-only binary contracts or infer omitted consumers.


### L62 combined rename and relocation (October 3)

A type may change its basename while moving to another compiler-proven namespace in the same
module. Prefix edits and source-identity rename are checked together, including closed consumers,
import aliases, constructor/static calls and companion sources/resources. Batch type planning sees
all final destinations so mutual references do not receive competing independent rewrites.

An explicitly configured module root can change its basename and parent in one proposal. The
module declaration, imports, dependency names, root and companion paths move together; ordered
custom resource roots, disabled resources and default roots left behind retain their meaning.
Both hosts apply version-checked edits/settings through existing Undo/Redo transactions. IntelliJ
Move now offers a New name field for one selected source. Relocation adds no AST state or embedding API.

Shared X169–X172 cover these paths and an installed-connection whole-batch refusal. All four,
plus X161/X163, pass in VS Code `run-TjE279` and IntelliJ `run-14394987299477639656` (START
also passes). The gate passes 222 backend and seven IntelliJ unit tests; both drivers compile.
IntelliJ's UP21 bridge persists only affected closed documents through apply/Undo/Redo so compiler
queries see their current content. Pre-existing unsaved closed buffers refuse before application.
The independent duplicate-declaration fix for compiler issue #667 adds a normal registration
check, with no AST state or embedding API; its regression fails on master and passes with the fix.
Cross-module type moves, uncaptured destinations, specialized qualifications and overlapping companion operations remain
outside this slice. Protocol refusal cannot veto arbitrary VS Code Explorer moves. L62 stays partial.


### L62 empty-package and trivia continuation (2026-10-04)

The compiler adapter now plans same-module type moves into captured empty package directories
and preserves comments/whitespace in qualified import, type and call names. Replay must prove
every moved declaration's destination identity and preserve all existing bindings, dispatch and
resources. Cross-module ownership, uncaptured/nonexistent directories, class-owned destinations,
specialized names and overlapping companion moves remain unsupported. No new AST/embedding
API is required. Shared X173–X176 cover empty nested packages, aliases, closed consumers,
companions/resources, prefix removal, inline collisions, interacting batches and Undo/Redo in
both editor drivers. Catalog: 181 scenarios. Selected acceptance is recorded below.

The independent compiler duplicate-declaration repair is [PR #668](https://github.com/xtclang/xvm/pull/668).
Errs includes its corrected three Java tests and five CLI manual cases, wired to the manual
check/sequential/parallel tasks. The previous artificial conditional-parser test is removed.

Selected acceptance passes X169/X171/X173–X176 in VS Code `run-ElUMIl` and IntelliJ
`run-13159045223223510909` (plus START), with zero editor errors. The combined gate passes
155 LSP tests, 103 Java tests and five manual CLI scenarios with no failures or skips; the
manual task reuses configuration cache. Both drivers compile. These are selected receipts,
not a full 181-case run. The extraction map and remaining exclusions are recorded in
[the integration plan](../../../docs/errs-integration-plan.md#l62-empty-destination-batch-acceptance-and-extraction).


### L63 typed initializers and unused locals (2026-10-04)

The compiler adapter now extracts whole explicitly typed local initializers and inlines a local
into an adjacent, single-use typed initializer with the same written expected type. It can also
remove an unused plain local whose initializer the compiler proves constant and free of side
effects. Complete-graph compilation and binding/dispatch proof remain mandatory. Comments and
contextual numeric/function types are preserved; inference, changed type contexts, intervening
statements, runtime initialization and Ref/Var annotations remain refusals. The deletion facts
are immutable source locations captured on the compiler worker, with no retained AST/constants
or new Java AST API. Shared X177–X180 cover native application and Undo/Redo plus installed
refusal checks. All 129 backend tests pass without failures or skips; X156/X157/X177–X180 pass
in VS Code `run-nDHZOH` and IntelliJ `run-232740531754002686` (plus START, zero IDE failures).
These are selected runs from the 185-case catalog. The independent ordinary-compiler `&& False`
code-generation repair has a failing-master CLI reproduction and 22 passing manual runtime
checks; keep its two commits separate in the extraction map. L63 remains partial for wider contexts,
extract-method, missing declarations, broader inline and global safe delete.


### L63 private expression helpers (2026-10-04)

Complete return expressions and explicitly typed local initializers can be extracted to private
helpers in the same type. Stable locals/parameters become explicit typed inputs; compiler
identities prove their types, moved bindings/calls, argument-to-parameter mapping and unchanged
existing dispatch. The compiler's effectively-final/non-reference register evidence prevents
reading mutable captured values early. Written expected types and exact moved expression text
are preserved. Generic owners and ordinary implicit instance calls are supported; method formals,
mutable/reference-backed captures, lambda/anonymous-class creation, async calls, conditional returns
and general statement extraction remain refused.

The implementation stays in Kotlin. Existing `Register.isEffectivelyFinal()`, `isVar()` and type
APIs suffice; there are no new Java AST fields, accessors, parent mutation or embedding entry points.
A local ownership map is necessary because a fresh parser tree has not adopted parent links. The
existing local-extraction reader also now skips typeless method-formal parser parameters instead
of dereferencing their missing type. Only detached type identities/source locations survive the
compiler worker. All 151 selected backend tests pass without failures/errors/skips. X156/X177/
X181–X184 pass in VS Code `run-ZhuPaV` and IntelliJ `run-5839432120705984532` (plus START,
zero IDE failures); both verify exact edits, diagnostics and Undo/Redo, while X184 verifies refusal
through the installed connection. These are selected runs from the 189-case catalog, not full
suite reruns. The integration plan records the failed first run, fixes and extraction commit map.


### L63 missing-method repair boundary (2026-10-04)

Compiler mode now proposes **Create private method** for supported unqualified calls in modules
and classes. It uses fresh resolved declarations, compiler type rendering and a complete proposed
graph proof, including the selected call's new declaration target. Existing overload/property
names, unqualified local shadowing, cross-module/computed receivers, computed/named arguments,
method formals and conditional returns are refused. The continuations below add compiler-established local arguments
and bounded instance/class qualifiers.
Untyped numeric literals are not runtime type evidence.
No new AST API/state is required. X185–X188 exercise both drivers with error/clear and Undo/Redo
checks; X122/X181/X185–X188 pass in VS Code `run-dzR5d9` and IntelliJ
`run-16733856986922464734`, with zero editor errors. UP07 also needed a client correction for
equal full reports that replaced lazy fixes without refreshing their annotations. The combined
gate passes 180 backend and 89 IntelliJ unit tests. These are selected runs. Details and remaining L63 scope are in
[the integration plan](../../../docs/errs-integration-plan.md#l63-missing-method-quick-fixes-2026-10-04).


The L63 continuation adds compiler-established block-local arguments (including validated `var`/`val`
initializers) and explicit local-initializer result types. Every local use must resolve to the exact
original declaration after the repair. Detached evidence uses existing compiler accessors; no AST
fields/API or failed TypeInfo inspection is added. X189–X192 extend both shared drivers with typed
locals, inferred locals plus a static typed initializer, parameterized results and inferred-result
refusal. See the [continuation and acceptance receipts](../../../docs/errs-integration-plan.md#l63-local-arguments-and-typed-initializer-repairs-2026-10-04).

The continuation passes 198 backend tests and X181/X185/X189–X192 in VS Code `run-mRmX6Y`
and IntelliJ `run-1236802406694405307` (plus START), zero editor failures. Both use the 197-case
catalog; these are selected runs. IntelliJ Ultimate is disabled. No plugin production changes
were needed in this continuation.


### L63 same-owner receiver boundary (2026-10-04)

Qualified creation now accepts `this`, `this:private` and plain parameter/local receivers when
validated compiler identities prove the receiver has the enclosing class type. It creates a private
instance method even inside a static caller. Full graph compilation, unchanged receiver/argument
bindings and the inserted declaration target are mandatory. X193–X196 add exact-edit/diagnostic
Undo/Redo coverage and other-owner/public-view refusals in both shared drivers. Computed/chained
and other-owner receivers remain outside this slice; the next continuation adds class qualifiers.
See the [scope, commits and acceptance receipts](../../../docs/errs-integration-plan.md#l63-same-owner-receiver-repairs-2026-10-04).

This receiver continuation passes 219 backend tests and X181/X185/X190/X193–X196 in VS Code
`run-nvquYD` and IntelliJ `run-15968094076069950349` (plus START), zero editor failures. Both
use the 201-case catalog; these are selected runs. IntelliJ Ultimate is disabled.


### L63 class-qualified static boundary (2026-10-04)

Named enclosing-class qualifiers, including fully qualified names, now generate static methods
using compiler-resolved class identity. Runtime Class/Type values, explicit generic/singleton
qualifiers remain refusals. Other owners were outside that slice; the next continuation adds
bounded same-module destinations. Instance parameters that shadow a class spelling
retain instance dispatch. The completed graph must prove both dispatch and the inserted target,
and preserve known bindings. All evidence is detached Kotlin data using existing compiler APIs.
X197–X200 exercise these boundaries in both shared drivers. The combined backend gate passes
232 tests without failures/errors/skips. See the [current scope, commits and acceptance receipts](../../../docs/errs-integration-plan.md#l63-class-qualified-static-repairs-2026-10-04).


X181/X185/X193/X197–X200 pass in VS Code `run-3Vm6C9` and IntelliJ
`run-11432411846060652594` (plus START), zero editor errors. Both use the 205-case catalog; these
are selected runs. IntelliJ Ultimate is disabled. Root/LSP/IntelliJ read-only Spotless passes.


### L63 cross-owner destination boundary (2026-10-04)

**Create public method … in …** supports another writable ordinary class in the same module,
including a closed companion file. Compiler identity establishes the destination; the complete
proposed graph must prove public access, dispatch, exact parameter/return type identities and
unchanged bindings. The edit uses the destination's document version. A destination that changes
a signature type through shadowing is refused even if the program would still compile.

That slice initially used enclosing-method parameters or supported literals, with whole-return/
statement result contexts. The next continuation adds cross-owner locals/typed initializers;
generic/interface owners, other modules and broader receiver/signature forms remain open. X201–X204 exercise the
new actions and refusals in both drivers; X195 retains its specific private-action refusal.
No new Java AST/embedding API is needed. Companion acceptance corrected the existing IntelliJ
diagnostic bridge to preserve decoded related reports (UP06/UP07); UP22 records the separate
upstream disposal failure. See the
[scope and acceptance record](../../../docs/errs-integration-plan.md#l63-cross-owner-source-destinations-2026-10-04).


Final selected acceptance: **256 backend tests** and **90 IntelliJ unit tests** pass without
failures/errors/skips. X181/X185/X195/X197/X201–X204 pass in VS Code `run-hOIsOh` and IntelliJ
`run-18176000411609180309` (plus START), zero editor failures; Ultimate is disabled. The shared
catalog has 209 cases. The native report-copy failure, its regression and the separate open UP22
lifecycle defect are documented in the [upstream register](../../../docs/errs-upstream-issues.md#up06up07-companion-report-copy-correction-2026-10-04).
These are selected acceptance runs; no full-catalog rerun or new packaged-stdio gate is claimed.


### L63 local types across source owners (2026-10-04)

Public missing-method repairs also accept compiler-established earlier block locals and whole
explicitly typed local initializers in the same module (X205–X208). Detached type identity must
survive destination lookup, alongside local binding and the existing complete-graph proof.
Inferred result contexts and changed/shadowed signature identities are refused. No Java AST or
plugin production API is added. Cross-module ownership/import policy and broader receiver forms
remain separate tasks. See the [scope and validation record](../../../docs/errs-integration-plan.md#l63-cross-owner-local-arguments-and-initializer-results-2026-10-04).


Selected acceptance: **275 backend tests** pass without failures/errors/skips. X181/X185/X190/
X202/X205–X208 pass in VS Code `run-RpTVz1` and IntelliJ `run-14627770467027596087` (plus START),
zero editor failures. Both use the 213-case catalog; Ultimate is disabled. Root/LSP/IntelliJ
read-only Spotless passes. These are selected runs, not a full-catalog or new packaged-protocol gate.
The [integration plan](../../../docs/errs-integration-plan.md#l63-cross-owner-local-arguments-and-initializer-results-2026-10-04)
records the proof boundary, receipts and commit extraction map.


### L63 cross-module destination boundary (2026-10-04)

Public missing-method repairs extend to reachable configured source dependencies and their closed
companions (X209–X212), using detached ownership evidence from successful module compilations.
Fresh compiler identities select the destination; signature rendering uses that module's names and
existing module-level imports. Local arguments and typed initializer results retain their exact
compiler identities. The final complete graph must select the inserted public method and preserve
existing bindings, with signature checks against the actual source declaration across module snapshots.

No import/graph change is proposed. Binary source indexes, read-only targets, reverse dependencies,
missing destination imports and unsupported owner/receiver/signature shapes remain refusals. Broader
L63 scope remains open. See the [scope and validation record](../../../docs/errs-integration-plan.md#l63-cross-module-missing-method-destinations-2026-10-04).


Selected acceptance: **296 backend tests** pass without failures/errors/skips. VS Code `run-B54Y1R`
and IntelliJ `run-14965910820534049086` pass X201/X202/X205/X206/X209–X212; IntelliJ also passes
START with Ultimate disabled. Both report zero editor failures and use the 217-case shared catalog.
The native driver queues cancellable popup discovery through IntelliJ's ordinary action path;
UP07's broader delivery-route audit remains open. No new full-catalog, packaged-protocol or
IntelliJ production-unit gate is claimed.


### L63 destination import insertion (2026-10-04)

Missing-method repairs may add required destination imports when that source module already has the
configured dependency (or the module is bundled in the XDK). They reuse existing aliases, reserve
source names across companions and insert only imports required by the signature. The method and
imports form one versioned workspace edit with complete graph and exact identity proof, including
source-position changes introduced by imports. Imports go in the module root; companion methods
receive a separate document edit in the same transaction. Shared X213–X215 cover atomic Undo/Redo, companion
alias collisions and refusal when only the caller has the dependency. No AST/embedding change is needed.

The integration plan now consolidates the remaining L63 work into eight explicit areas: generic
owners, broader signatures, receivers, arguments, missing types/properties, extraction, inline and
global safe delete. Each retains its own proof and acceptance requirements.

Validation: **310 backend tests pass**, zero failures/errors/skips. VS Code `run-u5kDXk` and IntelliJ
`run-4588144426201480586` pass **X122/X209–X215** (IntelliJ also START, Ultimate disabled), zero
editor failures, using the same 220-case catalog. X214 proves the import in the module root and
method in its closed companion are one native Undo/Redo transaction. IntelliJ fixture isolation
and graph-before-open setup avoid unrelated case churn; UP07's broader production transition
remains open. These are selected receipts, not full-catalog or fresh packaged-stdio acceptance.


### L62 ownership and relocation closure batch

Cross-module source moves now propose module imports and explicit graph dependency changes;
standard clients without graph persistence refuse that transaction. Ordered parent/child operations
and captured incoming resource trees extend relocation proof. Detached receiver proof includes
numeric and compound annotation values plus nested/service type ownership. Existing bindings,
dispatch and embedded resource values must still match after full-graph compilation.

Shared X216–X220 have selected acceptance below, including the explicit UP23 exception. Existing namespace,
noncyclic graph, read-only binary and known-consumer boundaries remain explicit. Runtime-only
contracts and unresolved compiler type expressions remain refusals. See the
[L62 closure record](../../../docs/errs-integration-plan.md#l62-ownership-and-relocation-closure-batch-2026-10-04)
for commit grouping and validation. No new AST or embedding API is needed.

L62 selected acceptance: IntelliJ `run-8069330970170232500` passes X169/X173/X216–X220.
VS Code `run-S7CfWP` passes six and fails X218 native Undo; do not claim all-green or full
VS Code Move parity. UP23 records the host ordering defect. The user accepted this separate
host limitation and continuation to L63. Backend/protocol: 178 passed, final affected rerun 58
passed; IntelliJ unit gate 7 passed. Scope boundaries and extraction grouping are in the
[L62 closure receipt](../../../docs/errs-integration-plan.md#l62-ownership-and-relocation-closure-batch-2026-10-04).


### L63 eight-area implementation batch (2026-10-04)

The current batch adds exact generic-owner/method-formal and conditional missing signatures,
compiler-validated receivers/named or computed arguments, missing class/instance-property stubs,
statement/nested-expression extraction, wider constant-local and selected private member inline,
and configured-graph safe deletion of unused private methods/constants. No compiler AST state or
embedding API has been added. Source type spelling uses actual module imports plus the compiler's
implicit module constants; both source and binary dependency types are covered.

The implementations retain explicit ownership, initialization, control-flow/capture, type and
binding/dispatch refusals. They are bounded refactorings, not arbitrary source transformations or
proof of unknown external consumers. Shared X221–X242 bring the catalog to **247 scenarios** and
pass selected acceptance in both editors. The final combined backend/protocol gate passes
**498 tests**, zero failures/errors/skips. L63 is closed within its documented supported/refused
forms; this does not claim arbitrary refactorings or a full-catalog rerun. UP23 remains open.
See the [closure and acceptance record](../../../docs/errs-integration-plan.md#l63-bounded-closure-and-acceptance-2026-10-04).

### L66 structural/editing batch (2026-10-05)

X243–X250 add resolved wildcard links and conditional-source refusal, lexical lambda/same-name alias
ranges, typedef outlines and damaged-source structure, plus continuation/wrapping/comment-margin
formatting. That checkpoint had 255 cases. X132/X138/X139/X158/X243–X250 have passing selected
receipts in both editors. Backend/protocol checks pass; the IntelliJ run exposed and repaired the
standalone formatting Redo defect tracked as UP24. Conditional import source syntax is rejected by
the existing parser. L66 is closed within its documented formatter/refactoring boundaries; see the
[closure receipt](../../../docs/errs-integration-plan.md#l66-bounded-closure-and-acceptance-2026-10-05).


### L67 detached navigation reuse (2026-10-05)

X251 brings the current shared catalog to 256 cases and covers repeated graph lookup, unsaved
changes, broken independent neighbors and configured-root removal/restoration in both editor
drivers. The backend reuses exact per-module semantic builds seeded by editor/diagnostic compilation;
complete references and edit proofs keep their existing requirements. No AST/embedding API changes,
disk persistence or incremental compiler are introduced. See the
[L67 receipt](../../../docs/errs-integration-plan.md#l67-module-navigation-index-2026-10-05) for measurements and acceptance.

L67 is closed within this measured boundary: 120 backend and 78 packaged-protocol tests pass,
followed by the canonical-key regression selection (19 tests). X45/X59/X63/X143/X251 pass in both
editors. The 129-module control succeeds under the unchanged heap; 20,000-method first-reference
queries now use one compile and take 0.4–0.6 s in the recorded workload. Persistent indexing is not
justified by these measurements; prolonged release budgets remain L82.

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
At the L74 checkpoint, parsed-only library source views returned no moniker when queried directly.
L75 adds exact artifact-backed declaration identities to those views; consumer imports already had
binary identities. This is not a claim of complete library-document
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


L75 implements negotiated library document content/refresh and direct bundled declaration monikers.
VS Code uses its standard read-only virtual provider; IntelliJ retains protected file views until
LSP4IJ implements the provider (UP25). X254 explicitly records this client difference. 58 distinct backend/protocol cases and the packaged content round trip pass. VS Code passes all
six selected cases; IntelliJ passes five plus START and reports X254 partial with its file-fallback
assertions successful and zero IDE failures; see the [contract and receipt](../../../docs/errs-integration-plan.md#l75-read-only-library-content-2026-10-05).

### L76 compiler inline completion

Implementation adds a 27th, compiler-only adapter capability, using existing copied cursor facts.
Unique automatic name/argument suggestions and explicit alternatives are single-range plain text;
selection context, cancellation and document versions constrain publication. Shared X255–X258
cover native accept/undo, dismissal/continued typing, ambiguity/selection and incomplete calls.
LSP4IJ's missing invocation/selection context is UP26; IntelliJ X257 is explicitly partial.
No embedding or AST changes were needed.

L76 validation: 46 backend/protocol tests and the packaged UTF-16 round trip pass, without failures
or skips. VS Code `run-B6sogF` passes X7/X31/X255–X258. IntelliJ
`run-13088584184602426732` passes START/X7/X31/X255/X256/X258, with X257 explicitly partial for
UP26 and zero IDE failures. Both drivers compile and formatting checks pass. See the
[L76 contract and commit map](../../../docs/errs-integration-plan.md#l76-compiler-inline-completion-2026-10-05).

L81 native acceptance closure (2026-10-05): both editors pass X145/X146/X147 and new X259.
VS Code now activates the real visible Cancel control through the isolated renderer, without moving
the mouse; its ordinary SDK cancellation mode stays distinct. X259 verifies installed consumer
hints across dependency edits, setting changes and restart without editing that consumer.
Both VS Code lifecycle modes pass, including two windows sharing one Electron process and restoring
unsaved source after close while work is pending. The 19 focused backend/transport tests pass.
UP15 remains upstream; full-catalog/scale/cross-platform evidence remains L82. This closes acceptance
for existing providers and adds no AST, embedding or production language capability. See the
[receipt and commit map](../../../docs/errs-integration-plan.md#l81-native-acceptance-closure-2026-10-05).
The current shared catalog has 277 scenarios (X1–X272 plus the five configuration/stress IDs).


The October 5 UI5–UI7 continuation adds shared X260–X262 for real Gradle import cancellation,
overlap refusal, failed/invalid output retention and retry. Both editors use a common gated
producer and native controls; acceptance receipts are recorded in the integration plan.
The current remaining settings work is listed explicitly in
[the UI remainder](../../../docs/errs-integration-plan.md#shared-compiler-import-acceptance-and-remaining-ui-work-2026-10-05).
L77 color support awaits an applicable recognized Ecstasy library API; the user explicitly deferred
L78 notebooks while file-based tooling is completed. L79 follows the runtime/DAP track.


UI3/UI4/UI6 now expose ordered external libraries and module-specific source attachments in both
clients. Null paths inherit evaluated Gradle binaries independently of source-graph overrides;
empty paths remove external binaries, with the bundled XDK always retained. Attachments create
read-only declaration-navigation snapshots and do not become source modules or change advertised
LSP capabilities. The source/identity checks are bounded by artifact metadata and available debug
text; matching sources are required. External attachment views do not yet export declaration
monikers or support arbitrary semantic queries inside library source text. IntelliJ uses protected
fallback files (UP25); VS Code uses the virtual content provider. Shared X266–X268 cover the controls,
persistence, navigation and invalid-input retention. See the
[contract and validation](../../../docs/errs-integration-plan.md#ordered-libraries-and-attached-sources-batch-ui3ui4ui6-2026-10-06).
Machine-local JVM settings, explicit restart, process-owned log retention and bounded live-server
export are implemented with shared X269/X270 (selected acceptance passes in both editors). They add host support controls,
not compiler semantics or a new advertised LSP capability. Broader multi-root/remote-workspace
acceptance and L82 release evidence remain; Run/DAP, color and notebooks retain
their recorded scope. See the [runtime/log contract](../../../docs/errs-integration-plan.md#machine-local-jvm-settings-and-log-support-ui5ui6-2026-10-06).


UI1–UI7 completion adds grouped VS Code settings, vertical page navigation in IntelliJ Compiler
settings, validated formatting width, explicit invalid-JDK failure, project-trust checks for Gradle imports, and saved
versus running runtime reports. Offline support ZIPs use only the project’s last recorded launch;
shared X271/X272 cover stopped/failing servers and recovery. Complete editor exit/reopen has a
separate persistence harness in both hosts. These are client settings/support features; adapter
capabilities are unchanged. Local desktop acceptance, both-adapter persistence, combined catalog
coverage and explicit host limitations are recorded in the
[UI completion receipt](../../../docs/errs-integration-plan.md#ui1ui7-completion-batch-2026-10-06).
Untrusted/virtual VS Code workspaces are explicitly unsupported; remote-host acceptance is separate
from the local desktop gate. Tree-sitter remains an explicit build alternative to default compiler.
