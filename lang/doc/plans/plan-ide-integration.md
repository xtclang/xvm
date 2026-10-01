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


> **Last Updated**: 2026-09-30 (native edit ownership, progress/cancellation and full editor acceptance)

The P1–P4 compiler organization checkpoint moves the four incomplete-syntax nodes into
`org.xvm.compiler.ast.partial` and updates the adapter's imports. It changes no advertised LSP
capability or default adapter. Backend validation and the broader AST separation follow-ups are
recorded in the [integration plan](../../../docs/errs-integration-plan.md#next-checkpoint-isolate-partial-ast-syntax).
AST5 additionally shares read-only cursor/argument syntax queries in that package, with no capability
change or new compiler state. AST1 and AST3 consolidate scope collection and capture projection
in the existing compiler helpers, preserving the adapter's public results. Their focused
200-test validation and commit map are recorded in the
[follow-up receipt](../../../docs/errs-integration-plan.md#scope-and-capture-helper-follow-ups-ast1-and-ast3).

This document describes the language tooling implemented in the `lang/` directory and what remains to be done.

The current L64 batch adds syntax-labelled recursive formal completion, compiler-validated argument
literals, copied candidate documentation and stable completion/signature ordering. Shared X97/X108
contain the corresponding editor assertions. The batch passes 337 backend/protocol tests and all
37 selected variants in both editors; IntelliJ also passes START with zero IDE errors. This is a
bounded extension, not closure of L64's remaining snippets, imports and callable/recovery contexts.

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
| `TreeSitterAdapter` | Tree-sitter grammar | Syntax, structure and workspace index | **DEFAULT** - Implemented |
| `XdkAdapter` | The XTC compiler, via `EmbeddingSupport` | Module diagnostics/navigation, bounded completion/signatures, type and implementation lookup, hierarchy, tokens, hints and explicit dependency source indices | **Opt-in** (`-Plsp.adapter=compiler`); Tree-sitter remains the shipped default |

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
diagnostic refresh. Evaluated Gradle import and a path-picker/origin view remain planned, not
implemented; see [PLAT2](../../../docs/errs-integration-plan.md#resource-configuration-and-build-model-integration-plat2--l67).
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
| Go-to-definition (cross-file) | - | Via workspace index | **Done** - resolved identities within a module, across the complete discovered/configured source graph and into dependencies with host-supplied source indices |
| Find references (same file) | Decl only | By name | **Done** - by identity, not by name |
| Find references (cross-file) | - | - | **Done** - exact identities across the current module or the complete configured source graph, including unopened consumers and binary-member uses |
| Completions | Keywords | Context-aware keywords/types/locals/members/imports | **Partial** - visible locals/parameters, narrowed types, implicit members, imported/enclosing types and static functions/constants; qualified dot/prefix and bare-name/empty statement completion with exact token edits; compiler-fitted locals/parameters and implicit properties/constants in empty final positional and pending named argument slots, including qualified/grouped values and slots before later arguments; member/return and parameter-header type prefixes use the enclosing compiler scope; flat and parameterized qualifiers use visible nested types with substituted aliases; registered formals and empty generic slots complete; mid-token edits replace the entire final identifier, including generic base names before written type arguments; import-producing completion for public source/bundled types uses whole-graph proof and atomic additional edits (backend/protocol tests and the new X105 variants pass in both editors); syntax name suggestions for written explicit-type declarations and contextual keywords/eleven templates now have passing backend and shared X149/X150 coverage in both editors; empty property/parameter names after complete named types, including primary constructors and EOF, have passing backend/protocol coverage and shared X151 acceptance in both editors |
| Syntax errors | Markers | Full | **Done** - the compiler's own codes and spans |
| Semantic errors | - | - | **Done** - the reason this adapter exists |
| Hover (signature) | Basic | Basic | **Done** - declaration plus the resolved type |
| Document highlights | Text match | AST identifiers with READ/WRITE distinction | **Done** - by resolved identity; READ/WRITE distinguished, compound targets shown as WRITE |
| Selection ranges | - | AST walk-up | **Done** - AST walk-up; zero-width cursor range if no AST is available |
| Folding ranges | Braces | AST nodes | **Done** - blocks and declarations; exact closing-brace columns prevent swallowing following declarations |
| Document links | Regex | AST nodes + best-effort import targets | **Partial** - HTTP(S) URLs inside Java-lexer comments/literals; no guessed import targets |
| Signature help | - | Same-file | **Partial** - selected signatures; fitted incomplete method/function/constructor calls, including specialized constructors and bounded declaration/tuple/literal recovery. Methods/constructors retain named mappings; function types have unnamed parameters. Constructor class types use explicit, required-type or provisional argument inference; array suppliers include dimension offsets and single-dimensional bracket slots fit the size parameter |
| Rename (same file) | Text | AST | **Partial** - locals/lambda/private ordinary-method parameters, captures and named labels; positional method-value escapes; graph-backed public/explicit-constructor parameter slots, types, static members, method/property families and explicit aliases; client versioned-edit support required. The L62 extension has backend and selected shared acceptance in both editors |
| Rename (cross-file) | - | - | **Partial** - types/packages and companion directories, qualified discovery-managed modules, implicit package directories, static members and source method/property families, including supported mixin/delegate/annotation routes; public parameter slots join override declarations and named callers; primary-header properties join generated constructor labels and property uses. Full graph compilation and binding/dispatch proof remain mandatory. Explicit graph changes have guarded native client persistence/Undo through xtc/rename (X118 passes in both editors; VS Code edited-file moves require files.refactoring.autoSave); standard LSP clients still refuse them. Project proposals include a scope receipt; registered absolute roots can include external consumers. The explicit graph is a declared proof boundary: omitted consumers, even inside workspace roots, remain unknown and are not automatically refused |
| Code actions | Organize imports | Organize imports + auto-import + doc-comments | **Partial** - compiler-proven unused-import removal, contiguous import sorting and unresolved public-type imports; individual/all-required implement/override at a class name for source and read-only binary/XDK contracts, including generic/conditional, compound and qualified types with imports; validated constants and fresh literal-default repair; complete compilation and binding/dispatch proof, versioned edits |
| Document formatting | Trailing WS | Structural re-indent + whitespace cleanup | **Partial** - Java-lexer brace/parenthesis/bracket indentation and outer whitespace; all token spellings preserved; no expression wrapping |
| Range formatting | Trailing WS in range | Structural formatting in range | **Partial** - same token-preserving formatter, bounded to selected lines |
| On-type formatting | - | Structural formatting on trigger characters | **Partial** - current-line indentation/whitespace on configured trigger characters |
| Workspace symbols | - | Fuzzy search (4-tier) | **Done** - on-demand substring search across discovered/configured sources including unopened modules; independent healthy modules survive a broken neighbor |
| Semantic tokens | - | Lexer-based (18 contexts) | **Partial** - Java lexical comments/literals/keywords plus resolved names, declarations, readonly/static/abstract modifiers and writes |
| Code lenses | - | Run action on module declarations | **Done** - module Run action through the existing client command |
| Linked editing | - | Same-file identifiers | **Partial** - resolved rename-eligible local-variable occurrences in one successful source snapshot; no proposed-name proof |
| Inlay hints | - | - | **Partial** - inferred local/destructured types, lambda parameters/returns and selected positional parameter names after successful compilation; named arguments/defaults omitted |
| Go-to-declaration (separate LSP request) | - | - | **Done** - local/import-alias declarations, inherited method/property contracts with multiple targets, and indexed library sources |
| Go-to-type-definition | - | - | **Done** - copied source type identities, narrowed/parameterized/nullable/relational types, formals and selected-call returns; module and host-indexed dependency sources |
| Find implementations | - | - | **Partial** - compiler composition targets across the complete source graph, including unopened source consumers; generic/inherited/mixin/delegated methods and property accessors; no invented binary source target |
| Type hierarchy (supertypes/subtypes) | - | - | **Done** - direct declared extends/implements edges across the complete source graph; generic parents retained, digest-bound handles reject stale closed files |
| Call hierarchy (callers/callees) | - | - | **Partial** - static selected source calls across the complete source graph, with method/lambda ownership and incoming/outgoing grouping; digest-bound handles reject stale sources |

#### Compiler completeness snapshot

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
| Extract/inline/safe-delete refactorings and general missing-declaration fixes | Bounded proven rename, import cleanup, public-type imports and proven implement/override | L62–L63 |
| Pull document/workspace diagnostics | Implemented for negotiated compiler clients: result IDs, related/closed documents, refresh and removal reports. Shared X123 and updated X76/X118 pass in both editors; push remains for other clients. PLAT1's source-location crash is fixed. The native demo also corrected a closed standalone-member pull gap; X27/X123 pass after that correction. | L68 implemented; demo receipts and nine pull-diagnostic tests |
| Semantic-token range/delta requests | Negotiated range/delta with bounded result history; backend/protocol and both host checks pass | L69 / X126 |
| Completion/action/lens/link/inlay/workspace-symbol resolve requests | All six endpoints implemented with bounded revision guards. IntelliJ selects and applies lazy actions through its undo-aware bridge. Backend/protocol and selected acceptance pass in both editors. | L70 / X127, X131 |
| File-operation pre-edit requests; explicit create/delete notifications | All six hooks pass backend/protocol checks; native file/package Rename passes. Batch native Move/Undo/Redo and resource assertions pass X130, but the current full VS Code run fails that case on the host's post-Paste Explorer repaint. IntelliJ passes. Cross-package qualification and explicit graph replacement remain refused. | L71 / X128, X130 |
| Save-time edits, incremental sync, multiple-range formatting | Negotiated save hooks, opt-in incremental UTF-16 updates and multiple-range formatting implemented; default Full/no save edits preserved. Backend/packaged transport and selected X132 pass in both editors. | L72 / X132 |
| Server-side `workspace/executeCommand` | Module Run lenses invoke an existing client command | L73 |
| Cross-project monikers | Detached identities scoped to compiler snapshots/graphs | L74 |
| Server-provided document content/refresh | Matching bundled/host-indexed source files, opened read-only | L75 |
| Inline completion | Ordinary completion popup | L76 |
| Document colors and color presentations | Ordinary token coloring; no color-value provider | L77 |
| Notebook synchronization | File/module document sessions | L78 |
| Debug inline values | Compiler type/parameter inlay hints; no runtime values | L79 |
| Application work-done progress, refresh, partial results and trace controls | Negotiated progress/create/cancel, lifecycle gating, coalesced refresh and runtime trace are implemented and tested. Bounded partial-result batches, actual Tree-sitter scan progress and shared X143 pass. X145 verifies native progress-model cancellation and restart during pending work in both editors. IntelliJ visible Cancel passes; VS Code physical-button selection and broader P3/P4 checks remain manual. | L81 / X141, X143, X145; [coverage map](../manual-test-plan.md#protocol-and-lifecycle-coverage-map) |
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
not additional LSP capabilities or a completed L81 progress/trace-controls implementation.
Dynamic watcher registration now waits for `initialized` and negotiated support. The final L80 producer/capability audit is complete; broader progress/refresh manual
acceptance remains under L81.

**Implementation and validation are separate.** The shared playbook now has 128 cases with
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
Multidimensional construction, unfinished declaration names, missing map entries and
unterminated literal contents remain unsupported. Explicit cursor queries now recover missing
value operands within call arguments.
Remaining limits: non-type cursors inside identifiers, further member/call syntax after a typed
prefix and arbitrary enclosing-instance enumeration. Qualified/grouped/compound arguments and
slots before later written arguments use full compiler validation. Type-valued receiver functions
and receiver-to-argument rewrites retain visible signature mappings. Lexical enclosing/imported
property candidates require ordinary readable-value validation and argument fitting. Argument
completion does not synthesize literals or enumerate arbitrary enclosing instances.

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
or infer conditional-mixin hierarchy edges. Implementation lookup uses compiler composition facts.

Module-root discovery follows the source-file/same-name-directory layout. Non-file URIs remain
single-source inputs. Workspace folders supply a discovered module/dependency graph; explicit
source settings override it. Queries compile that graph on demand without a persistent index. Tree-sitter remains the shipped default. See the
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
- IntelliJ Platform IDE caching is enabled under `lang/.intellijPlatform/ides`

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
   - Keep Tree-sitter as the shipped default and compiler mode opt-in
   - Compiler mode stays Java-only; improve Java parser recovery without a Tree-sitter fallback
   - A combined adapter was an earlier proposal and is not the current implementation plan

### Long-term (Advanced Features)

8. **Refactoring support (cross-file)**
   - Compiler rename already covers bounded locals/private parameters and graph-backed source types, static members, ordinary method/property families and aliases, including simple member-file moves
   - Extend target/resource scope and harden large-graph proof memory (L55/L62)
   - Extract method/variable, inline and safe delete have no implementation (L63)

9. **Code actions (semantic)**
   - Organize imports is implemented in Tree-sitter and bounded by compiler proof in XdkAdapter
   - Auto-import is implemented in Tree-sitter; XdkAdapter offers proven unresolved public-type imports
   - ~~Generate doc comment~~ ✅ COMPLETE (tree-sitter)
   - XdkAdapter generates individual/all-required inherited methods from source and read-only XDK/binary contracts at a class name, with compiler-selected generic/conditional, qualified and compound signatures, atomic imports and safe defaults; whole-graph proof permits the intended call/descendant changes and preserves other bindings; generated bodies use `TODO()`. Both actions use `refactor.rewrite`, so they appear as class intentions without requiring a diagnostic at that location
   - Unvalidated computed/named defaults, unsupported constant kinds and annotated/unrenderable type spellings remain withheld; missing implementations use fresh declaration analysis, never failed-compilation TypeInfo. The current batch validation is recorded separately from previous X122 receipts
   - Broader semantic fixes, doc generation and extract/inline/safe-delete remain unimplemented (L63)

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
Cross-package qualification rewrites and explicit source-graph relocation remain refused.
Ordinary LSP pre-operation null replies cannot veto arbitrary host file moves.

PLAT2c/L67 now imports the evaluated Gradle model in both hosts and exposes effective source/resource
paths and origin. Explicit overrides survive refresh; invalid model files retain the last valid host
import. Gradle export/prepare actions run in the host, never in LSP. Both `.gradle` and `.gradle.kts`
are handled by Gradle itself. TestKit, importer/server tests and shared X129 pass.
Automatic IntelliJ Gradle-sync refresh and aggregation of nested/composite build roots remain
follow-ups; each exported root has an explicit refresh action.
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
selected acceptance passes in both editors. Explicit-graph relocation and cross-package
qualification rewriting remain refusals. Search `// TODO LSP4IJ:` in the plugin for removable upstream compatibility bridges.

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
Tree-sitter as the shipping adapter remain defaults. Incremental transport does not mean incremental
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
The shared playbook and both native drivers include the additions; final combined acceptance is
pending. Ambiguous local syntax and arbitrary nested value/body synthesis remain explicit limits.

L65 adds property-accessor redirect traversal and explicit non-written implementation refusals.
Shared X153 covers covariant/conditional targets and runtime-only delegation; X154 covers more
read/write classification. Binary-source overload selection refuses absent or ambiguous metadata.
The L65 backend gate passes 78 tests without failures or skips. Concrete validated source types
now contribute conditional-mixin implementations. Full native acceptance remains pending; no
runtime target enumeration is claimed.
