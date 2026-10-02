# Ecstasy Language Server - Manual Test Plan

The current catalog has **163 scenarios**: X1–X158, CFG1–CFG3 and 7a.8/7a.9.
X155 adds conditional generic composition rename and Undo; its editor execution is pending.
X156 adds whole-return-expression extraction and Undo/Redo; its editor execution is pending.
Both editor runners show the current scenario ID and description beside the completed/remaining
counts. Long descriptions are shortened in the status bar; hovering shows the full description.
IntelliJ also includes it in the test window title. Subset selection still uses unchanged X IDs.
X150/X151/X152 include fitted lambda arguments, inferred local names and ordinary enclosing
values. X153 checks implementation dispatch and X154 mutation classification. X87 also checks
member completion in a primary-constructor default with its missing closing delimiter.

The current full VS Code attempt passes **157/159**; corrected X152 passes separately, giving
**158/159** across receipts. X130 still fails on the tracked upstream Explorer Cut cleanup error.
IntelliJ's full attempt and continuation together pass **all 159 scenarios plus START**, with
zero IDE errors; the full attempt's X105 popup timeout remains a stability follow-up despite two
selected passes. These are combined receipts, not single clean full runs. See
[the current acceptance record](../../docs/errs-integration-plan.md#l64l65-full-editor-acceptance-2026-10-02).

The preceding selected L64 run passes X97/X108/X150/X151/X152 in both editors: 63 variants covering argument
values, recursive bounds, templates, declaration names and enclosing instances. VS Code
`run-C2gpLR` and IntelliJ `run-16005944962074733631` record zero editor errors. The backend
gate passes 65 Java and 410 LSP tests with no failures or skips. See the
[L64 receipt and remaining boundaries](../../docs/errs-integration-plan.md#l64-real-source-completion-continuation-2026-10-01).

X146/X147 add dependency refresh and late-report ownership and pass in both editors; see the
selected reliability receipt below. The previous full IntelliJ run passes all 150 then-existing
cases plus startup, with zero IDE errors. The previous full VS Code run passes 149/150;
X130's intermittent post-Paste Explorer repaint failure remains tracked separately. Neither
the later selected passes nor the extension-free probe's latest non-reproduction close it.
X124/X131/X134/X142/X143 previously passed in both editors across
selected runs and a focused IntelliJ X142 correction; see the follow-up receipt below. X140/X141 add explicit UTF-16 navigation and runtime server
trace switching; X136/X137/X140/X141 pass in both editors, plus IntelliJ startup with zero IDE errors.
See the [protocol receipt and open limits](../../docs/errs-integration-plan.md#protocol-hardening-batch-l80l81-2026-09-30).
X136–X139 previously added language-service settings, effective state, transport restarts, live
formatting and save ownership; X118/X132/X135–X139 already pass in both editors.
The preceding watcher batch expanded the catalog to 140 scenarios with X130 batch Move/Undo/Redo, X131 lazy
resolvers, X132 save/range formatting, X133 linked editing, X134 external source watching and X135
server-log show/hide. X124 now exercises missing external roots without manual refresh.
VS Code passes all 19 selected cases across `run-iGx1M2` (18) and `run-YMDUeW` (corrected X130).
IntelliJ `run-17174738471798629344` passes START and 18 selected cases with zero IDE errors;
X130 then passes in `run-1746762976235942700` with START and zero IDE errors. These are selected receipts, not a full 140-case checkpoint.
The [current acceptance record](../../docs/errs-integration-plan.md#watcher-move-and-log-view-acceptance-follow-up-2026-09-30)
keeps failed runs, corrections and manual limits visible.

Earlier L69–L71/PLAT2c receipts used explicit VFS refresh for X124 and eager IntelliJ action edits;
the current automatic watch ownership and undo-aware lazy-action bridge supersede those paths.
The [2026-09-29 demo record](../../docs/errs-integration-plan.md#native-intellij-demo-continuation-2026-09-29)
retains the preceding 128-case selection and its resumed/focused receipts. All 128 cases plus START
have passing native receipts across those runs, with zero IDE errors; VS Code `run-b59XBq` passes
all 128 in one run. Historical 113-case full-run receipts below remain historical evidence.

An earlier L64 checkpoint expanded shared X97 to 24 variants and X108 to 13. Those additions accept compiler-validated
`0`, empty-string, `True` and `Null` arguments and recursive written formal names. Both drivers
check exact edits, successful repair and completion metadata; X97 also checks candidate
documentation and active parameters. VS Code `run-yOWnUW` and IntelliJ
`run-4259780076879058977` pass all 37 variants; IntelliJ also passes START with zero IDE errors.
That [checkpoint](../../docs/errs-integration-plan.md#l64-completion-and-signature-batch-2026-09-29)
records the 337 backend/protocol tests, shared catalog hash and selected-run limits.

L56–L59 add startup synchronization guards, written formal bounds, compound argument fitting and
inferred lambda/destructured-type hints. The combined JVM checks pass; expanded shared X42/X97/X108
pass in VS Code `run-APcBZB`. The later fixture-spacing checkpoint passes all 113 VS Code cases
in `run-aOarm7`. IntelliJ passes all 113 scenarios together in `run-6034631232732848040`, with zero
IDE errors; its separate startup-editing and focus-recovery checks also pass. Both harnesses show
live case progress. See the
[current validation record](../../docs/errs-integration-plan.md#native-startup-rename-deadlock-and-execution-tracing-2026-09-28).

The later L55/L61/L62 batch passes the real teaching-workspace memory/rename acceptance,
declaration lookup and companion-directory rename checks. Selected X4/X102/X103/X104 pass in
both clients; VS Code verifies Undo and IntelliJ verifies native reverse rename. See the
[current batch receipts](../../docs/errs-integration-plan.md#teaching-workspace-declarations-and-resource-moves-l55l61l62).

C28/L53/L54 adds empty/qualified type slots, generic/multiple-return headers and native IntelliJ
workspace/refactoring assertions. The [active validation record](../../docs/errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54)
distinguishes full backend results, selected VS Code checks and selected native IntelliJ receipts.
Earlier VS Code checkpoints remain valid historical evidence: C27/L51 `run-9deeaR`, L50
`run-92TmWV`, and L47–L49 `run-psziUN`. They are not IntelliJ execution receipts.

The P1–P4 `ast.partial` package refactor changes compiler organization, not editor behavior or
scenario data. Existing recovery/completion/signature/header cases still apply in both editors.
P1–P4, the AST5 shared-syntax extraction and AST1/AST3 helper consolidation use focused
compiler/adapter regressions; they add no new native-run receipt or scenario change. AST1/AST3
pass 200 tests covering scope, calls, captures, clone isolation, navigation and rename.
See the [package validation record](../../docs/errs-integration-plan.md#next-checkpoint-isolate-partial-ast-syntax).

This document describes how to manually test every feature implemented in the Ecstasy Language Server and IntelliJ plugin.

## Server process lifecycle acceptance

Run this with the shipped Tree-sitter backend as well as compiler mode. Start from a recorded
process baseline so existing old servers and another IDE's workspace are not counted as leaks.

1. Open an Ecstasy project and several `.x` files. In IntelliJ's Language Servers panel, record
   the XTC server PID. Check `ps -axo pid,ppid,command | grep '[x]tc-lsp-server.jar'` on macOS/Linux.
2. Restart that server several times through the panel. Each previous PID must disappear;
   the final active server must still provide completion and diagnostics.
3. Close the project during a fresh server startup, then reopen it. Cancelled startup must
   not leave an extra server behind. Repeat once after the workspace finishes indexing.
4. Quit the test IDE. Its recorded server PIDs must disappear within ten seconds. Reopening
   the IDE must not be needed to reap them. Do not terminate another workspace's server.

The focused JVM/provider regressions pass; this installed-IDE acceptance is still pending.
See [the lifecycle diagnosis](../../docs/errs-lsp-process-lifecycle.md) for the failure mechanisms
and the distinction between fixed code and previously orphaned processes.

## IntelliJ startup editing acceptance (L56)

Run in a fresh compiler-mode project before waiting for the Language Servers panel to report ready.
The unit tests cover notification permutations; these steps exercise the actual client/editor path.

1. Open a valid `.x` module and immediately type a deliberate unknown name in a method body.
   Problems must eventually show the diagnostic for the current buffer. Repair it while startup
   is still progressing; stale initial text must not bring the diagnostic back.
2. During a fresh startup, replace a long module containing several foldable methods with a short
   valid module. Folds must use the shortened text, with no line/offset exception in `idea.log`.
3. Close and reopen that file during startup, then edit it again. The final buffer must receive
   current diagnostics and folds; a delayed close must not retire the reopened document.
4. Repeat with a bulk replacement and with an unsaved edit made before the server initializes.
   Record the server PID, final document version, diagnostic state and IDE-error log.

The dedicated native startup test passes all five phases in `run-1799467324333192176`, with no
IDE failures. It uses real unsaved editor transactions before server initialization completes;
ordinary feature cases still wait for readiness. Run it independently with:

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
    --tests '*CompilerPlaybookTest.startupEditing' \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

The report includes `startup-editing.json` (PID/version/text stamps and phase results) and
`server-trace/` (one execution trace per child process). X103's forward/reverse file rename also
passes after removing the transport read lock that caused a real EDT deadlock. This regression
was unrelated to focus loss. X42/X97/X108 contain the shared L57–L59 semantic variants.

## Current development batch: workspace queries and editing

For the new workspace queries, add an unopened Consumer subclass overriding Library's method.
From Library, check implementations and subtype hierarchy, then incoming calls from Consumer.
Edit Consumer on disk and verify a previously opened hierarchy does not silently reuse stale edges.
Rename an inline Library class, static function or static property and inspect the versioned edits
in both modules. In a compiling file with an unused ordinary import and a used aliased import,
request import cleanup: only a removal that preserves compilation/bindings should be offered.
Sort adjacent imports and verify comments remain in place.

For compiler editing features, format a file containing CRLF, string spaces and URL comments;
only indentation/outer whitespace should change. Format a selected range, then type a closing
brace and check its indentation. An unterminated string must yield no formatting edits. Ctrl-click
an HTTP(S) URL in a comment/string. Check the module Run lens uses the normal client run action.
Linked editing on a local should include that declaration's uses while ignoring an equal-spelled
local in another method. Semantic highlighting should include lexical comments/literals/keywords
as well as resolved names; inlays retain their existing compiler-derived scope.

## Current development batch: discovery and complete XDK

Backend verification passes, and focused VS Code X94–X98 pass in `run-KoAP6K` (five passed,
98 not-selected). Native IntelliJ execution is deferred; both drivers compile. In a fresh
compiler-mode workspace, set `xtc.compiler.sourceModules` to `null` (or leave it unconfigured in
IntelliJ). Create `Library.x` with `module Library { static Int answer() = 42; }` and `Consumer.x` with
`module Consumer { package lib import Library; Int run() = lib.answer(); }`. Opening only Consumer
should resolve `answer`; workspace symbol search should find Library's declaration. Add a separate
broken module and verify Library remains searchable. Create/remove another module and wait for its
file watcher notification; its workspace symbols should appear/disappear. Set `sourceModules` to
`[]` to disable discovery, then `null` to restore it. Explicit graph scenarios below still apply.

Also compile `module UsesXml { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }`
without external XDK paths. XML must resolve from the production bundle, while rename on `Document`
remains unavailable because it is a binary library declaration. The full XDK, including XML/JSONDB
and the other distribution libraries, is now shared by production and compiler tests.

## Acceptance checklist for the 2026-09-30 batch

Scope: `9f2c5ae8c` (external watches), `c748f9831` (native moves), `3888c15f2`
(resolve/action application), `448b7f8a1` (save/sync/ranges), `19ba6422d` (labels and upstream
markers), and the planned settings work in `4ccbda4d9`. Read the result as a checklist, not a
passing receipt. Backend, protocol and native UI evidence are separate. The current catalog has
140 shared scenarios; X130–X135 are added by this batch, and X124 has stronger assertions.

Run in a disposable compiler-mode workspace using the build/run instructions below. Keep user
projects out of destructive move/rename tests. For each editor record commit, catalog hash, selected
IDs, pass/fail/not-run, IDE errors, language-server errors and logs. A timeout is a maximum wait
for a response/state transition, not a prescribed pause. Do not silently retry a completed edit
or Undo against an already modified fixture.

| Check | Exact exercise and required result | Existing automation / remaining evidence |
| --- | --- | --- |
| External resource creation | Run X124 with the editor continuously focused. Create missing `generated/assets` and the resource from an external terminal; delete/recreate it, then switch to a missing replacement root. Problems must update without opening those folders or invoking Refresh. | Strengthened X124 passes in both drivers without manual refresh. |
| External source changes | Put `Library.x` outside the workspace, configure Library and a local Consumer explicitly, open only Consumer, then change Library's return type on disk from Int to String and back. Problems must appear/clear and Definition must still find Library. Repeat after deleting/recreating the external source directory. | X134 passes in both drivers for unopened external source edit, delete and recreation. Directory deletion/recreation remains an additional manual check. |
| Watch ownership and idle work | Configure two modules sharing an external root. Remove one, verify the other still updates; replace the last reference, then edit the retired root. Close/reopen the project and restart the server. Verify no obsolete diagnostic publication, duplicate subscription, growing server-process count or repeated idle compiler jobs. | Lease/coalescing/disposal unit tests; manual lifecycle/idle observation still required. A scheduled VFS refresh is not itself a compilation. |
| Batch container move | Run X130: select both module containers, move them into `destination`, keep members/resources closed, verify the consumer, then Undo once and Redo once. All paths and bytes must match the shared fixture in each state. | X130 passes in both drivers; IntelliJ drives native Move with project Undo/Redo and VS Code drives Explorer batch Cut/Paste with its Undo/Redo. Explorer drag/drop is a separate manual check. |
| Single-file and package moves | Run X128's file/package Rename and X118's explicit-graph module rename. Also move one source to an existing sibling directory through native Move; verify references, resources and one Undo/Redo. | X118/X128 shared; extra cross-directory single-file UI exercise remains manual. |
| Collision and invalid destinations | Repeat Move with an existing file, existing directory, missing destination parent, target inside its own source, overlapping parent/child selection and duplicate target names. Cancel the dialog. Each rejected/canceled operation must leave every source, setting and path unchanged. | `FileMoveTargetsTest` covers filesystem validation; dialog/error presentation and cancellation still need manual checks. |
| Source changes during proof | Start a move/rename on a larger fixture, edit an affected open buffer or create a destination collision while proof is pending, or cancel the operation. A stale result must not overwrite the new text/path. The next fresh operation must work. | Existing snapshot/epoch regressions and X57/X118; move-specific concurrent timing is not claimed by X130. Use tracing or deterministic backend barriers when the race cannot be reproduced manually. |
| Deliberately refused operations | Try explicit-graph module relocation and a cross-package move requiring qualification changes; also try a read-only bundled-library declaration. Verify the reason, unchanged files/settings and responsive IDE. | Existing refusal tests/X101 and manual checks. Normal LSP null responses cannot veto arbitrary host file moves; compiler preflight claims apply to registered native actions. |
| Lazy action listing and application | Run X105, X122 and X127. Opening/canceling intentions or resolving an action must not edit the document. Selecting the import/cleanup action must clear the intended diagnostic; one Undo and Redo must restore exact text and Problems. | Shared cases pass in both installed clients, including IntelliJ lazy-action application and Undo/Redo. |
| Other lazy resolvers | Run X131, then inspect the native Run lens, comment URL, inferred type/parameter inlays and workspace symbol navigation. Initial/deferred properties must agree, positions and command arguments survive, and source edits invalidate old handles. | X131 is protocol acceptance. Existing feature UI scenarios cover presentation; it is not evidence that a tooltip/popup was displayed for every resolved property. |
| Resolver lifetime | Resolve handles after a dependency/configuration change, close/reopen or server restart; send a foreign/evicted handle through a protocol test. Require a stale/invalid response, no stale edit and no leaked graph retained by handles. Eager clients must still receive complete payloads. | Resolve-store/service regressions; X127/X131 cover source-edit expiration only. Inspect test coverage before counting the other transitions complete. |
| Normal save and formatting | Run X132, save a dirty file and verify disk/Problems; exercise whole-file, selection and on-type formatting with editor preferences both enabled and disabled. Saving must not unexpectedly reformat or block behind compilation. | X132 checks overlapping/disjoint range responses and negotiated default save hooks; existing formatting scenarios cover normal UI. Native save-on-large-compilation remains a manual responsiveness check. |
| Incremental text transport | Run `DocumentSynchronizationTest`: sequential/mixed full-and-range changes, UTF-16 surrogate pairs, CRLF/bare CR, old versions, invalid batch atomicity and Full-mode refusal. The packaged stdio test also negotiates incremental transport. Change the plugin's Full/Incremental preference with an unsaved file. | Backend/packaged tests and shared X137 cover the wiring. Full remains default. This is not incremental compiler support. |
| Opt-in save edits and range failures | Test `xtcDocumentSync.formatOnSave` independently from incremental transport; verify no extra compilation, unchanged server buffer until client didChange, malformed/reversed/out-of-document ranges and duplicate/conflicting formatting edits. Cancel/stale requests must not apply edits. | Backend tests plus X132's default mode. Opt-in native save UI, conflict/stale/cancel coverage and real transport coverage must have their own receipts; do not infer them from a default no-op. |
| Terminology and compatibility | Inspect server panels, status bar, commands, Move/Rename dialogs, startup/error messages, templates and playbook progress. Labels should say Ecstasy; `.x`, `.xtc`, `xtc.*`, environment names and class/file prefixes remain technical identifiers. | Packaging/manifest checks plus visual inspection. Search production workarounds for `// TODO LSP4IJ:`; removal conditions must name the behavior to revalidate. |
| Configuration UI follow-up | Verify Apply/Cancel/Reset, user/project inheritance, multi-root scope, invalid-value retention, restart-required settings, live refresh and absence of duplicate format-on-save. | X136–X139 cover the implemented local settings subset. Physical dialog navigation, multi-root and fresh-IDE persistence remain manual checks in the settings acceptance section below. |

Focused automated gate after compilation/unit checks: CFG2, X57, X101, X105, X118,
X122–X134 in each host, plus the existing native lens/link/inlay/workspace-navigation checks
when their display path changed. Run both newly added and modified tests. A full catalog checkpoint
and longer idle/restart/memory runs remain the submission gate under L82; selected cases alone
cannot establish full playbook completion.

Backend command for the save/sync/range checks (no editor window):

```bash
./gradlew :lang:lsp-server:test --tests '*DocumentSynchronizationTest' \
  -Plsp.adapter=compiler -PincludeBuildLang=true -PincludeBuildAttachLang=true \
  --rerun-tasks --no-build-cache
```

Configuration examples belong in a custom client's initialization payload, **not** in an editor's
settings file until UI5 wires a setting to them:

```json
{"initializationOptions":{"xtcDocumentSync":{"incremental":true,"formatOnSave":false}}}
```

Full transport and no server save edits are the defaults. Native editor format-on-save may already
invoke normal formatting; enabling a second save-formatting path must not apply formatting twice.

## Feature Implementation Status

> See [plan-ide-integration.md](plans/plan-ide-integration.md) for the canonical feature implementation matrix comparing Mock, Tree-sitter, and Compiler adapter capabilities.

The [active compiler completion checklist (L55–L83)](../../docs/errs-integration-plan.md#full-compiler-lsp-completion-checklist)
tracks the remaining implementation and validation work. All 25 project-defined adapter
capabilities have compiler implementations, many with explicit bounds; this is not full LSP
coverage. Pull diagnostics and token range/delta have passing checkpoints. All six lazy-resolve
operations, broader native moves and save/sync/formatting additions have passing backend and selected
editor receipts. L80/L81 also have a validated bounded protocol checkpoint; optional negotiation,
partial results and broader acceptance remain open. General refactorings, monikers, inline completion/values, colors and notebooks
still have implementation gaps. Use the [absent-feature inventory](plans/plan-ide-integration.md#compiler-completeness-snapshot)
to distinguish an unsupported feature from a failed playbook case.

The earlier 113-scenario IntelliJ checkpoint passed, plus startup; that receipt does not cover
the current 148-scenario catalog. That earlier complete native run
includes the 50 newly added cases and X20/X81/X82 assertion additions, with zero IDE errors.
The [L60 validation checklist](../../docs/errs-integration-plan.md#intellij-parity-backlog-l60)
records receipts and actual client gaps. L60 is complete. L55 passes both its 24-root memory
regression and the real 25-root teaching workspace with one and five unsaved buffers at 512 MiB.
L56 now passes a separate native test of edits, replacement and close/reopen during initialization, including current
diagnostics/folds. The normal feature readiness wait is not used as that evidence.

---

## Pre-Test Setup

### 1. Build with Specific Adapter

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Build with tree-sitter adapter (the shipped default)
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Or opt into XdkAdapter - compiler diagnostics and semantic IDE features
./gradlew :lang:lsp-server:build -Plsp.adapter=compiler

# Or with mock adapter (no native dependencies)
./gradlew :lang:lsp-server:build -Plsp.adapter=mock
```

**The compiler adapter uses the full matching XDK library set bundled with the server.** Gradle builds
them through the composite module dependencies and packages them as resources. No external XDK or
`XDK_HOME` setting is required for compiler analysis. A Kotlin host API can now supply additional
dependency artifacts/source indices and explicit source roots/edges for automatic recompilation.
Workspace folders automatically supply discovered source roots/import edges. VS Code exposes an
explicit override through `xtc.compiler.sourceModules` (`null` restores discovery, `[]` disables it).
The ordinary fixtures below need only bundled libraries; the source-project checks also exercise
the explicit settings shown in the XdkAdapter playbook.

It is also the slow one, deliberately: the first compilation in a session takes about a second
(class loading, reading the XDK, a JIT still warming up) and then settles to about 60ms. If the
first file you open seems to hang for a moment, that is what it is.

### 2. Launch in Your Editor

**IntelliJ:**
```bash
./gradlew :lang:runIntellijPlugin
```

**VS Code:**
```bash
# Build and install the extension
cd lang/vscode-extension
npm install && npm run compile
npx vsce package          # creates xtc-language-*.vsix
code --install-extension xtc-language-*.vsix

# Ensure the LSP server fat JAR exists
ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar

# Open a folder with .x files
code /path/to/xtc-project
```

The extension starts the LSP server automatically when a `.x` file is opened.
Requires `JAVA_HOME` or `XTC_JAVA_HOME` pointing to Java 25+.

### 3. Verify Which Adapter is Active

The server runs out of process and writes its own log to `~/.xtc/logs/lsp-server.log`, for both
editors. It announces itself on startup:

```
========================================
Ecstasy Language Server v<version>
backend: Tree-sitter
log file: /Users/you/.xtc/logs/lsp-server.log
========================================
```

The `backend:` line is the answer:
- `backend: Tree-sitter` - tree-sitter is active
- `backend: Ecstasy Compiler` - the real compiler is active
- `backend: Mock` - mock adapter is active
- `backend: Mock` **together with** `tree-sitter was requested but failed to initialize` -
  tree-sitter was asked for and fell back. The two lines together are the fallback; the `backend:`
  line alone does not say whether it was chosen or settled for

Clients can also ask over JSON-RPC (`xtc/healthCheck`), which answers with `adapter` set to
`TreeSitter`, `XDK` or `Mock`.

**IntelliJ:**
1. Help → Show Log in Finder/Explorer for the IDE-side log, or read the server log directly
2. Find the banner above
3. For Tree-sitter, also verify: `"semantic tokens ENABLED (23 types, 10 modifiers)"` in the log
4. For IntelliJ plugin runs from this repo using Tree-sitter, also verify the startup command line in the IDE log:
   - `XTC LSP command configured`
   - `-Dxtc.lsp.semanticTokens=true`
   This confirms you are exercising the branch's semantic-token path rather than a stale fallback.

**VS Code:**
1. Open Output panel (Ctrl+Shift+U / Cmd+Shift+U)
2. Select "Ecstasy Language Server" from the dropdown
3. Find the same banner

**With the compiler adapter**, every compilation also logs what it cost and what the compiler is
holding on to, which is the quickest way to tell it is really running:

```
XdkAdapter - compile: uri=file:///X.x, 106 bytes, 1 diagnostic(s), 3 symbol(s), queue=1,
             waited 160us, compiled in 64ms [modules=24, constants=207196, invalidations=10, heap=281MB]
```

`queue` is how many documents are waiting: compilations are serialised, because the compiler was
not written for two at once. `waited` growing while you type is the sign that serialising has
started to hurt.

### 4. Create Test File

Create a file named `TestModule.x` with this content:

```xtc
module TestModule {
    class Person {
        String name;
        Int age;

        String getName() {
            return name;
        }

        void setAge(Int newAge) {
            age = newAge;
        }
    }

    interface Greeter {
        void greet();
    }

    service UserService {
        Person createUser(String name) {
            return new Person();
        }
    }

    // Test: ERROR markers are detected as diagnostics
    // ERROR: This is a test error message
}
```

---

## Test Cases by Feature

> **"Both adapters" means mock and tree-sitter**, which is how this document was written when
> there were two. The compiler adapter now answers diagnostics (§7), the outline (§6), hover
> (§2), go-to-definition (§4), find-references (§5), document highlights (§8), selection ranges
> (§9) and folding (§10), plus type hierarchy. Definition queries span the discovered/configured source graph;
> references also span discovered/configured source graphs, including unopened consumers. Workspace-symbol
> search compiles unopened modules on demand.
>
> Compiler completion and signature help are available for the supported cursor contexts, along
> with type-definition, type/method implementation lookup, static call hierarchy, resolved-name
> semantic tokens, read/write highlights, bounded inlay hints and validated local/private-parameter
> rename, plus graph-backed type, static-member, ordinary method/property-family and alias rename
> (versioned-edit clients; see section I). Bounded formatting, proven import cleanup, module run
> lenses, HTTP(S) links and local linked editing are advertised with the limits listed above. Definition/type-definition
> and inherited implementation bodies can also resolve into explicitly host-indexed dependencies;
> the bundled XDK supplies matching read-only source targets automatically, while additional
> host dependency artifacts require explicit configuration. Compiler mode stays Java-only.
>
> Use the [XdkAdapter playbook](#xdkadapter-playbook) for a complete compiler run, including fixtures
> that compile and precise expectations for compiler-only features. §7a adds diagnostic stress checks.

### Compiler module sessions and hierarchy

With the compiler backend, create `Project.x` containing `module Project { class Base {} }` and
`Project/Child.x` containing `class Child extends Base {}`.

| Check | Action | Expected |
|---|---|---|
| Member diagnostics | Open Child.x; change Base to Missing in its extends clause | A compiler diagnostic points into Child.x, with no generic internal error on Project.x. |
| Sibling invalidation | Restore Child.x; rename Base in the open Project.x buffer | Child.x is reanalysed and its diagnostic updates even without an edit there. |
| Unsaved member | Open a new `Project/pkg/Added.x` buffer containing `class Added extends Base {}` | It joins the module without being saved; no temporary source files appear on disk. |
| Cross-file navigation | Navigate from Base in Child.x; find references on the Base declaration | Definition points into Project.x; references include Child.x. Highlights remain document-local. |
| Hierarchy | Prepare hierarchy on Base and expand its subtypes; inspect Child's supertype | Child and Base point to their own files. A generic parent retains its type arguments. |
| Close overlay | Introduce an error in Child.x, then discard and close its buffer while Project.x stays open | The disk version replaces the overlay; obsolete diagnostics clear. |
| Membership | Create an invalid member on disk, then delete it | File notifications refresh the module and clear the deleted file's diagnostics. |
| Broken syntax | Remove a member's closing brace, then restore it | Current outlines/folding remain available, including sibling files. Semantic navigation clears until correction. |
| Incomplete statement | Type `console.` inside a method and remove the closing braces | The method/module outline and enclosing selection ranges survive; compiler diagnostics remain, and no semantic definition is invented for the broken expression. |

These checks exercise module source files, not workspace dependency builds or conditional-mixin
hierarchy. Dependency source navigation needs an explicit host-supplied artifact/source index;
the [host API checks](#dependency-host-api-checks) below cover that boundary. The XdkAdapter playbook
also covers method-implementation lookup.

### 1. Syntax Highlighting (TextMate)

**Provider:** TextMate grammar (NOT tree-sitter or LSP)
**Status:** ✅ Done
**Works with:** Both adapters (independent of LSP)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 1.1 | Keywords | Look at `module`, `class`, `interface`, `service`, `return` | Different color from identifiers |
| 1.2 | Types | Look at `String`, `Int`, `Person` | Type color |
| 1.3 | Strings | Add `"hello"` literal | String color |
| 1.4 | Comments | Add `// comment` and `/* block */` | Comment color |
| 1.5 | Numbers | Add `42`, `3.14` | Number color |
| 1.6 | Editor color scheme sanity | Open a `.x` file in IntelliJ | Editor background matches the active theme (not a solid white fallback) |
| 1.7 | TextMate + semantic token layering | Open a `.x` file with types, methods, and annotations | Base TextMate colors remain sane; semantic tokens refine symbols instead of washing out the theme |

**Note:** Tree-sitter supplies syntax-based semantic tokens. The opt-in compiler adapter additionally
classifies resolved usage sites as properties, locals or parameters and supplies semantic modifiers;
see X41 in the compiler playbook. TextMate remains the lexical coloring layer.

---

### 2. Hover Information

**LSP Method:** `textDocument/hover`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Hover mouse over a symbol (or Ctrl+Q for Quick Documentation)
- *VS Code:* Hover mouse over a symbol

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 2.1 | Class hover | Hover over `Person` in declaration | Shows `class Person` |
| 2.2 | Method hover | Hover over `getName` | Shows `method getName` |
| 2.3 | Property hover | Hover over `name` property | Shows `property name` |
| 2.4 | Interface hover | Hover over `Greeter` | Shows `interface Greeter` |
| 2.5 | Service hover | Hover over `UserService` | Shows `service UserService` |

---

### 3. Code Completion

**LSP Method:** `textDocument/completion`
**Status:** ⚠️ Partial (Mock not context-aware; tree-sitter scope-aware in BODY context)
**Works with:** All three (the compiler adds the type, which no grammar can)

**How to trigger:**
- *IntelliJ:* Ctrl+Space (Basic Completion), or type and wait for auto-popup
- *VS Code:* Ctrl+Space, or type and wait for auto-popup

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 3.1 | Keyword completion | Type `cla` + Ctrl+Space | ✅ | ✅ |
| 3.2 | Type completion | Type `Str` + Ctrl+Space | ✅ | ✅ |
| 3.3 | Document symbols | Type `Per` + Ctrl+Space | ✅ | ✅ |
| 3.4 | Built-in types | Type `Int` + Ctrl+Space | ✅ | ✅ |
| 3.5 | After dot (member) | Type `person.` + Ctrl+Space | ❌ | ✅ |
| 3.6 | Context filtering | Inside method vs class level | ❌ | ⚠️ |
| 3.7 | Module-level `@Inject` in body | Module: `@Inject Console console;`. Inside `void run() { co<Ctrl+Space> }` | ❌ | ✅ |
| 3.8 | Function-local variable | Inside method: `String greeting = "hi"; gr<Ctrl+Space>` | ❌ | ✅ |
| 3.9 | Method parameter | Inside `Int square(Int amount) { am<Ctrl+Space> }` | ❌ | ✅ |
| 3.10 | Class member from method body | Inside class with `Int total;` and a method, type `to<Ctrl+Space>` in the method body | ❌ | ✅ |
| 3.11 | Scope ordering | Type `<Ctrl+Space>` inside a method that mixes local var, parameters, class members, module-level decls | ❌ | ✅ locals/params first, then class members, module-level decls, built-in types, keywords |

---

### 4. Go to Definition

**LSP Method:** `textDocument/definition`
**Status:** ✅ Done (scope-aware same-file + cross-file via workspace index)
**Works with:** All three (compiler: resolved identities across the current module, including closed member files)

**How to trigger:**
- *IntelliJ:* Ctrl+Click on a symbol, or Ctrl+B, or F12
- *VS Code:* Ctrl+Click on a symbol, or F12

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 4.1 | Class reference | Ctrl+Click `Person` in return type | Jumps to `class Person` |
| 4.2 | Method reference | Ctrl+Click `getName` call | Jumps to method |
| 4.3 | Property reference | Ctrl+Click `name` in `return name;` | Jumps to property |
| 4.4 | Cross-file type | Ctrl+Click on a type defined in another file | Jumps to definition in other file |
| 4.5 | Method parameter | Ctrl+Click on a parameter usage inside its method body | Jumps to the parameter declaration in the signature, NOT to any same-named workspace symbol |
| 4.6 | Function-local variable | Method declaring `String s = "hello";` and using `s` later. Ctrl+Click on `s` | Jumps to the local declaration, NOT to any same-named class field or workspace symbol |
| 4.7 | Local shadowing class member | Class with `Boolean whitespace;`. Method declaring `function Boolean(Char) whitespace = ...;` and using `whitespace(test)`. Ctrl+Click on the `whitespace` call | Jumps to the local function-typed variable, NOT to the class field |
| 4.8 | Inner block shadows outer | `void run() { Int x = 1; if (cond) { Int x = 2; x.toString(); } }`. Ctrl+Click on the inner `x` | Jumps to the inner-block declaration, NOT the outer one |
| 4.9 | Forward reference not resolved | Method body where a usage of `name` precedes a local declaration of `name`. Ctrl+Click on the usage | Resolves to module-level / outer-scope / workspace `name`, NOT the forward-declared local |
| 4.10 | Doc-commented target | Ctrl+Click on a class/method/property preceded by a `/** ... */` doc comment | Cursor lands on the declaration line (e.g. `class Foo {`), NOT on the `/**` opener |

**Tree-sitter notes:**
- Resolution order is: enclosing-scope locals/parameters → class/module members → same-file top-levels → cross-file workspace index.
- Cross-file definition uses workspace index fallback only when scope-aware resolution finds nothing.
- Import-path-based resolution is not yet implemented.

---

### 5. Find References

**LSP Method:** `textDocument/references`
**Status:** ⚠️ Partial
**Works with:** Tree-sitter, and the compiler (current module, by resolved identity). Mock limited

**How to trigger:**
- *IntelliJ:* Alt+F7 (Find Usages), or right-click → Find Usages, or Shift+F12
- *VS Code:* Shift+F12, or right-click → Find All References

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 5.1 | Find class usages | Right-click `Person` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.2 | Find method usages | Right-click `getName` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.3 | Find property usages | Right-click `name` → Find Usages / Find All References | ⚠️ decl only | ✅ |

**Mock limitation:** Returns only the declaration, not actual usages.

---

### 6. Document Structure / Outline

**LSP Method:** `textDocument/documentSymbol`
**Status:** ✅ Done
**Works with:** All three adapters

**How to trigger:**
- *IntelliJ:* Alt+7 (Structure tool window), Ctrl+F12 (File Structure popup)
- *VS Code:* Ctrl+Shift+O (Go to Symbol in File), or Outline panel in sidebar

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 6.1 | Structure view | IntelliJ: Alt+7; VS Code: Outline panel | Hierarchical outline |
| 6.2 | File structure popup | IntelliJ: Ctrl+F12; VS Code: Ctrl+Shift+O | Popup with all symbols |
| 6.3 | Breadcrumbs | Look at editor top (VS Code) or bottom (IntelliJ) | `TestModule > Person > getName` |
| 6.4 | Outline on the compiler adapter | Same as 6.1, built with `-Plsp.adapter=compiler` | Same hierarchy. It comes from the parsed AST, not the compiled structures, so it appears for a file that does not compile |
| 6.5 | Outline of a file with errors | Give a non-void method a bare `return;`, then Alt+7 / Outline | `COMPILER-41: Return is supposed to be non-void.`, and the outline still lists every declaration. A structure view that empties out on a typo is the bug this guards |

---

### 7. Diagnostics / Error Detection

**LSP Method:** `textDocument/publishDiagnostics`
**Status:** ✅ Done (compiler adapter) / ⚠️ Partial (others)
**Works with:** Different behavior per adapter

**How to trigger:** Diagnostics appear automatically as you type (push-based).
- *IntelliJ:* Red/yellow squiggly underlines; Alt+Enter for quick fixes; F2 to jump to next error
- *VS Code:* Red/yellow squiggly underlines; Ctrl+Shift+M (Problems panel); F8 to jump to next error

| # | Test | Steps | Mock | Tree-sitter | Compiler |
|---|------|-------|:----:|:-----------:|:--------:|
| 7.1 | Syntax error (missing brace) | Delete a `}` | ❌ | ✅ | ✅ with the compiler's own code |
| 7.2 | Unmatched braces | Add `{` without `}` | ⚠️ | ✅ | ✅ |
| 7.3 | ERROR comment marker | Add `// ERROR: message` | ✅ | N/A | N/A |
| 7.4 | WARN comment marker | Add `// WARN: message` | ✅ | N/A | N/A |
| 7.5 | Semantic error (undefined var) | `Int y = x + 1;` with no `x` in scope | ❌ | ❌ | ✅ `COMPILER-38: Name "x" is unresolvable.` |
| 7.6 | Module-level property getter parses cleanly | At module scope (outside any class) write `Int val2.get() = 43;`. Same form inside a class body should also parse | N/A | ✅ no diagnostic | ✅ no diagnostic |
| 7.7 | Package-level property getter parses cleanly | Inside `package util { Int answer.get() = 42; }` | N/A | ✅ no diagnostic | ✅ no diagnostic |
| 7.8 | Type error | `String s = 1;` | ❌ | ❌ | ✅ `COMPILER-43: Type mismatch: "String" expected, "IntLiteral" found.` |
| 7.9 | Wrong argument count | Call a one-argument method with two | ❌ | ❌ | ✅ `COMPILER-56: Could not find a matching method or function "f" ...` - the compiler reports no *matching* method rather than a count |
| 7.10 | Codes are the compiler's | Any of 7.1-7.9 | - | - | The code shown is the one `xcc` prints for the same file: `PARSER-*`, `COMPILER-*`, `VERIFY-*` |

**Notes:**
- Mock: Detects `// ERROR:` and `// WARN:` comment markers (testing convenience)
- Tree-sitter: Real syntax error detection via parsing (doesn't use comment markers by design)
- Comment markers: N/A for tree-sitter and the compiler, because both report real problems
- Compiler: the same diagnostics `xcc` would print, at the same spans. A row it disagrees with
  `xcc` about is a bug worth reporting either way - the two are meant to be the same compiler

---

### 7a. Compiler Adapter Specifics

**Status:** ✅ Done
**Works with:** Compiler adapter only (`-Plsp.adapter=compiler`)

These are the behaviours that only exist because the adapter runs the real compiler. Nothing here
is observable under mock or tree-sitter.

**Prerequisite:** a server built with `-Plsp.adapter=compiler`; its module resources must be present.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 7a.1 | No external XDK | Unset `XDK_HOME`, restart the server, open a complete module | Real compiler diagnostics; correcting the source clears them |
| 7a.2 | Invalid external XDK | Set `XDK_HOME` to a nonexistent directory, restart and edit the file | The bundled libraries still supply compiler analysis |
| 7a.3 | Cold start | Watch the log on the first `.x` file opened in a session | Compare the first and subsequent `EmbeddingSupport.compileModule` durations in the server trace, including queue wait separately. Workspace diagnostics may compile before the first editor request; a first editor compile is not necessarily cold |
| 7a.4 | Steady state | Edit the same file ten times, watching `compiled in` | Settles to tens of milliseconds. A number that keeps climbing means something is accumulating - compare `footprint` across the run |
| 7a.5 | Queue depth and contents | Type quickly across two or three open files | In the execution trace, inspect `queueSize` and FIFO `queuedJobs`, plus `debouncingJobs` and `runningJobs`. Job IDs, operation names and URIs show what is waiting and running; `waitMs` and `runMs` separate delay from execution. |
| 7a.6 | Superseded edit | Type continuously for several seconds without pausing | Log shows `superseded before it started, skipped` or `superseded after ..., abandoned`. No diagnostics are published for text that has already been replaced - a squiggle under an identifier you have finished typing is the failure this prevents |
| 7a.7 | Memory over a session | Leave the server up, edit for a while, watch `heap=` in the `footprint` on each line | Normal allocation/GC produces a sawtooth. Record sustained growth across repeated collections or after closing files; a single increasing sequence is not proof of retained compiler state. |
| 7a.8 | A diagnostic no other adapter can find | See the duplicate-annotation file below | One `WARNING VERIFY-75`, the annotation is ignored |
| 7a.9 | A file that has gone badly wrong | Paste a hundred lines of non-Ecstasy text into a `.x` file | Diagnostics stop at a hundred serious errors rather than filling the panel with consequences of the first one |
| 7a.10 | References follow meaning, not spelling | Two classes each with a property `x`; Shift+F12 on one | Only that class's `x`. A text search cannot do this, and neither can a grammar |

Execution trace files are `~/.xtc/logs/lsp-trace-<pid>-<process-start>.jsonl`; native IntelliJ runs
save them in `run-*/server-trace/`. They include compiler queue counts and lists, javatools phase
and API timings, process/thread IDs, and server request-to-reply latency. Use the
[trace guide](../lsp-server/README.md#compiler-queue-and-api-timing) for field meanings and controls.
The service-started notification uses the title **Ecstasy Language Server Started**, with separate
**Version**, **Adapter** and **Process ID** lines in the normal themed body style. Its balloon fades
after roughly eight seconds unless being interacted with; the notification and server log remain
available afterward.
| 7a.11 | Definition of a method call | F12 on `p.sum()` | Jumps to `sum`'s declaration. The name in a call resolves to nothing by itself - which method it is depends on the target and the arguments - so this is the compiler's answer, not a name match |
| 7a.12 | Definition of something from the core library | F12 on `Int` or `Console` | Nothing happens. It resolves perfectly well and this document has nowhere to point at; jumping to another mention of `Int` in the same file would be worse than doing nothing |
| 7a.13 | Hover shows a type | Hover over a variable in an expression | The declaration, and the type the compiler decided. On a document that does not compile the type may be absent - an expression only has one once it has been validated |
| 7a.14 | Compiler completion and call hints | Run X6–X19 in the [XdkAdapter playbook](#xdkadapter-playbook) | Accessible members, visible scope and applicable call candidates come from compiler validation; completing the expression clears normal diagnostics. |

**7a.8 - the duplicate annotation.** This is the case worth keeping, because it is invisible
everywhere else. No grammar can find it: it needs the compiler to lay `Derived` over `Base` and
notice the annotation was already there.

```xtc
module DupAnno {
    class Base {
        @Atomic Int x = 1;
    }
    class Derived extends Base {
        @Atomic @Override Int x = 2;     // VERIFY-75: duplicates the base property's annotation
    }
}
```

Expected: `WARNING VERIFY-75`, naming the annotation, the property and the derived class, and
saying the annotation on the derived property is ignored.

> Exactly one warning, not two. The compiler reports this one twice internally - once when the
> type is laid out, once when a later stage asks for the same type again - and the adapter
> collects through an `ErrorList`, which is what filters the repeat. Two identical warnings in the
> Problems panel means that filtering has been lost.

> Compiling the same file with `xcc` on **master** prints nothing at all - the warning is
> produced and then discarded before anyone sees it. This is a real bug in master, recorded in
> [docs/errs.md](../../docs/errs.md); the adapter showing it is the fix working, not a false
> positive.

---

### 8. Document Highlight

**LSP Method:** `textDocument/documentHighlight`
**Status:** ✅ Done
**Works with:** All three (the compiler matches resolved identity within the document)

**How to trigger:**
- *IntelliJ:* Click on any identifier — other occurrences highlight automatically
- *VS Code:* Click on any identifier — other occurrences highlight automatically

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 8.1 | Class highlight | Click on `Person` | All `Person` occurrences highlighted |
| 8.2 | Property highlight | Click on `name` | All `name` occurrences highlighted |
| 8.3 | Method highlight | Click on `getName` | All `getName` occurrences highlighted |
| 8.4 | No highlight on whitespace | Click on empty space | No highlights |
| 8.5 | Write highlight | Click on `x` in `Int x = 42;` | Declaration site shows as **write** highlight (different color/style from reads) |
| 8.6 | Read highlight | Click on `x` in `return x;` | Usage site shows as **read** highlight |
| 8.7 | Assignment write | Click on `age` in `age = newAge;` | Assignment target shows as **write** highlight |

Rows 8.5–8.7 describe Tree-sitter's read/write classification. The compiler also distinguishes reads
and writes by resolved identity, but declarations receive TEXT highlights; an assignment or compound
assignment target receives WRITE. Use X41 for the compiler-specific expectations.

---

### 9. Selection Ranges (Smart Select)

**LSP Method:** `textDocument/selectionRange`
**Status:** ✅ Done
**Works with:** Tree-sitter, and the compiler (it expands out through the parsed tree)

**How to trigger:**
- *IntelliJ:* Ctrl+W (Expand) / Ctrl+Shift+W (Shrink)
- *VS Code:* Shift+Alt+Right (Expand) / Shift+Alt+Left (Shrink)

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 9.1 | Expand from identifier | Place cursor on `name`, expand | ❌ | ✅ selects `name` → `String name` → class body → class → module |
| 9.2 | Expand from method body | Place cursor inside `return name;`, expand | ❌ | ✅ selects statement → method body → method → class |
| 9.3 | Shrink back | After expanding, shrink | ❌ | ✅ reverses the chain |

---

### 10. Folding Ranges

**LSP Method:** `textDocument/foldingRange`
**Status:** ✅ Done
**Works with:** All three (the compiler folds blocks and declarations that span more than a line)

**How to trigger:**
- *IntelliJ:* Click the fold/unfold arrows in the editor gutter (left margin); Ctrl+Shift+Minus (fold all) / Ctrl+Shift+Plus (unfold all)
- *VS Code:* Click fold arrows in gutter; Ctrl+Shift+[ (fold) / Ctrl+Shift+] (unfold); Ctrl+K Ctrl+0 (fold all) / Ctrl+K Ctrl+J (unfold all)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 10.1 | Class fold | Click fold arrow next to `class Person {` | Class body collapses |
| 10.2 | Method fold | Click fold arrow next to `String getName() {` | Method body collapses |
| 10.3 | Import fold | Add 3+ import statements, fold | Import block collapses |
| 10.4 | Nested fold | Fold method inside class | Method folds independently |
| 10.5 | Fold all | Ctrl+Shift+Minus / Ctrl+K Ctrl+0 | All regions collapse |
| 10.6 | Consecutive line comments | Add 3+ consecutive `//` comments | Fold arrow appears; comments collapse as one region |
| 10.7 | Non-adjacent comments | Add `//` comments separated by code | Each group folds independently |

---

### 11. Rename Symbol

**LSP Method:** `textDocument/prepareRename` + `textDocument/rename`
**Status:** ✅ Done (same-file only)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Shift+F6 on an identifier, or right-click → Refactor → Rename
- *VS Code:* F2 on an identifier, or right-click → Rename Symbol

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 11.1 | Rename class | Place cursor on `Person`, press F2/Shift+F6, type `Employee` | All `Person` occurrences renamed |
| 11.2 | Rename method | Place cursor on `getName`, rename to `fetchName` | All occurrences updated |
| 11.3 | Rename property | Place cursor on `name`, rename to `fullName` | All occurrences updated |
| 11.4 | Prepare rename | Press F2/Shift+F6 on `Person` | Identifier range highlighted, old name shown |
| 11.5 | Cancel rename | Press Escape during rename | No changes applied |

---

### 12. Code Actions

**LSP Method:** `textDocument/codeAction`
**Status:** ✅ Done (organize imports + remove unused imports)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Alt+Enter on an import line, or lightbulb icon in gutter
- *VS Code:* Ctrl+. (Quick Fix menu), or click lightbulb icon

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 12.1 | Organize imports (unsorted) | Add unsorted imports: `import b; import a;`, trigger code action | Imports sorted alphabetically |
| 12.2 | No action (already sorted) | With sorted imports, open code actions | No "Organize Imports" offered |
| 12.3 | No action (single import) | With 1 import, open code actions | No action offered |
| 12.4 | Remove unused import | Add `import foo.Unused;` where `Unused` is never referenced, trigger code action | "Remove unused import 'Unused'" action offered |
| 12.5 | Used import not flagged | Add `import foo.Bar;` and use `Bar` in code, trigger code action | No "Remove unused import" for `Bar` |

---

### 13. Document Formatting

**LSP Method:** `textDocument/formatting` + `textDocument/rangeFormatting`
**Status:** Implemented with bounded formatting rules; not a complete style formatter.
**Works with:** Tree-sitter and compiler adapters (Mock only cleans whitespace).

Compiler mode uses the Java compiler lexer to indent brace blocks and parenthesis/bracket
continuations, trim trailing whitespace and optionally insert the final newline. It preserves
multiline literals/comments and refuses edits if lexing fails or the token stream would change.
It does not normalize operator spacing, align declarations, wrap long expressions, reorder code,
or implement every Ecstasy layout convention. `maxLineWidth` is not a wrapping implementation.
Range and save-time requests use these same rules. See X102/X107/X132 and the adapter matrix.

**How to trigger (full document):**
- *IntelliJ:* Ctrl+Alt+L (Reformat Code)
- *VS Code:* Shift+Alt+F (Format Document)

**How to trigger (selection only):**
- *IntelliJ:* Select text, then Ctrl+Alt+L
- *VS Code:* Select text, then Ctrl+K Ctrl+F (Format Selection)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13.1 | Remove trailing whitespace | Add spaces at end of a line, format | Trailing whitespace removed |
| 13.2 | Insert final newline | Remove final newline from file, format | Final newline added |
| 13.3 | Range format | Select 2-3 lines with trailing spaces, format selection | Only selected lines cleaned |
| 13.4 | No-op on clean file | Format a correctly indented file with no trailing whitespace and the configured final newline | No changes |

---

### 13a. On-Type Formatting (Auto-Indent)

**LSP Method:** `textDocument/onTypeFormatting`
**Status:** ✅ Done
**Works with:** Tree-sitter and compiler adapters, with different formatting breadth.

Tree-sitter mode uses AST context for auto-indentation. Compiler mode uses the Java lexer for
current-line indentation/whitespace; it does not use Tree-sitter. Trigger characters are `Enter`,
`}`, and `;`. The cases below describe intended editing behavior; bounded compiler formatting
and native IntelliJ Enter handling must be checked separately (X102/X107).

**How it works:** Automatic — indentation is adjusted immediately when you type a trigger
character. No manual action needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13a.1 | Indent after `{` in class | Type `class Foo {` then Enter | New line indented +4 from class keyword |
| 13a.2 | Indent after `{` in method | Inside a class, type `void foo() {` then Enter | New line indented +4 from method |
| 13a.3 | Indent after `{` in if | Inside a method, type `if (True) {` then Enter | New line indented +4 from if |
| 13a.4 | Outdent `}` for class | Type `}` to close a class body | `}` aligns with the `class` keyword |
| 13a.5 | Outdent `}` for method | Type `}` to close a method body | `}` aligns with the method declaration |
| 13a.6 | Outdent `}` for if block | Type `}` to close an if block | `}` aligns with the `if` keyword |
| 13a.7 | Maintain indent after statement | After `x = 1;` press Enter | New line at same indent level |
| 13a.8 | Continuation + `{` | Type `implements Closeable {` then Enter | Body indent from declaration start (+4), not from continuation (+8) |
| 13a.9 | Module body indent | Type `module myapp {` then Enter | New line indented +4 |
| 13a.10 | No indent inside string | Press Enter inside a string literal | No indentation adjustment |
| 13a.11 | Nested constructs (3+ levels) | Class > method > if > Enter after `{` | Correct cumulative indent (e.g. 12 for 3 levels) |
| 13a.12 | After `}` line | Press Enter after a `}` line | New line at same indent as `}` |
| 13a.13 | Large file performance | Open a `.x` file > 1000 lines, type normally | < 5ms per formatting request (check LSP log) |
| 13a.14 | Doc comment continuation | Type `/**` then Enter | New line gets ` * ` prefix aligned with `/**` |
| 13a.15 | Doc comment mid-line | Press Enter on a ` * existing text` line inside `/** */` | New line gets ` * ` prefix |
| 13a.16 | Indented doc comment | Inside a class (indent 4), type `/**` then Enter | New line gets `     * ` (4 spaces + ` * `) |
| 13a.17 | Block comment continuation | Type `/*` then Enter | New line gets ` * ` prefix |
| 13a.18 | No continuation after `*/` | Press Enter after a `*/` line | Normal indentation (no ` * ` prefix) |
| 13a.19 | IntelliJ auto-close brace skeleton | In IntelliJ, type `void bepa() {` and press Enter | Creates an indented blank body line and leaves the auto-inserted `}` aligned with `void`, not under the method name |
| 13a.20 | Repeated Enter inside fresh method | After 13a.19, press Enter again on the blank body line | Next line stays at method-body indent instead of drifting to 8/12 spaces |

**High-value log checks while running 13a tests:**
- `textDocument/onTypeFormatting: ... ch='\n'`
- `onTypeFormatting[enter]: ... reason=... desiredIndent=... currentIndent=...`
- `textDocument/onTypeFormatting: 1 edits [...]`
- For IntelliJ auto-close skeleton cases: `onTypeFormatting[enter]: auto-close skeleton ... bodyIndent=... closingIndent=...`

---

### 13b. Code Style Settings (IntelliJ)

**Provider:** IntelliJ plugin (`XtcLanguageCodeStyleSettingsProvider`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `editor.tabSize` / `editor.insertSpaces`)

Code Style settings for XTC appear under Settings > Editor > Code Style > Ecstasy.

**How to access:**
- *IntelliJ:* Settings/Preferences > Editor > Code Style > Ecstasy

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13b.1 | Settings page exists | Open Settings > Editor > Code Style | "Ecstasy" appears in the language list |
| 13b.2 | Default indent size | Open Code Style > Ecstasy > Tabs and Indents | Indent: 4, Continuation indent: 8, Tab size: 4, Use tab character: unchecked |
| 13b.3 | Code preview | Look at the preview pane | XTC code sample with classes, methods, switch/case |
| 13b.4 | Change indent size | Set indent to 2, look at preview | Preview re-indents with 2-space indent |
| 13b.5 | Change continuation indent | Set continuation indent to 4 | Preview adjusts `implements` line indent |
| 13b.6 | Tab character toggle | Check "Use tab character" | Preview switches from spaces to tabs |
| 13b.7 | Right margin | Check the right margin value | Should default to 120 |
| 13b.8 | Settings persist | Change indent to 3, close and reopen Settings | Indent still shows 3 |
| 13b.9 | Reset to defaults | Click "Reset" or "Set from..." > "Ecstasy" | Values revert to 4/8/4/false |

---

### 13c. Code Style → LSP Server Round-Trip (IntelliJ)

**Provider:** `XtcLanguageClient` (workspace/configuration) + `XtcLanguageServer`
**Status:** ✅ Done
**Works with:** IntelliJ Code Style and VS Code `xtc.formatting.*` preferences; clients that omit workspace formatting configuration fall back to LSP `FormattingOptions`.

IntelliJ Code Style settings are forwarded to the LSP server via `workspace/configuration`
at startup. Changes are pushed via `workspace/didChangeConfiguration`. The server uses
these settings for formatting. Project-level `xtc-format.toml` loading is not implemented.

**How to verify the config flow:**
1. Open an IntelliJ instance running the Ecstasy plugin
2. Check the LSP server log for `workspace/configuration: parsed config` — this
   confirms the server received the settings

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13c.1 | Default config flows to server | Open a `.x` file, check LSP log | Log shows `workspace/configuration: parsed config` with `indentSize=4` |
| 13c.2 | Custom indent flows to server | Set Code Style indent to 2, restart LSP server | Log shows `indentSize=2` |
| 13c.3 | 2-space indent affects formatting | Set Code Style indent to 2, type `module Foo {` then Enter | New line indented by 2 spaces (not 4) |
| 13c.4 | Nested indent with custom config | Set indent to 3, type class inside module, then method, press Enter after `{` | Indent at 9 (3 * 3 levels) |
| 13c.5 | No TextMate interference | Open a `.x` file with custom indent | Syntax highlighting works normally (no white background, correct colors) |
| 13c.6 | VS Code configuration | Set `xtc.formatting.indentSize: 2`, then format an unindented nonblank body line | Compiler formatter uses 2-space indentation. `editor.tabSize` alone does not override the workspace formatting configuration. |
| 13c.7 | Auto-close brace honors custom indent | Set indent to 2, type `void foo() {` then Enter in IntelliJ | Blank body line indents to 4 spaces and the auto-inserted `}` aligns to 2 spaces |

**How to restart the LSP server** (to pick up changed Code Style settings):
- *IntelliJ:* Open the LSP4IJ Language Servers panel → right-click "Ecstasy Language Server" → Restart

**Note:** The `workspace/didChangeConfiguration` notification is sent when IntelliJ detects
a configuration change, which should propagate settings without a manual server restart.
If settings don't update immediately, restart the server as a workaround.

---

### 14. Signature Help

**LSP Method:** `textDocument/signatureHelp`
**Status:** ✅ Done (tree-sitter only, same-file)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* Type `(` after a method name, or press Ctrl+P inside argument list
- *VS Code:* Type `(` after a method name, or press Ctrl+Shift+Space inside argument list

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 14.1 | Show params on `(` | Type `createUser(` | ❌ | ✅ shows `String name` |
| 14.2 | Active param on `,` | Type `method(arg1,` | ❌ | ✅ highlights second param |
| 14.3 | No help outside call | Place cursor on a variable | ❌ | ✅ returns null (no popup) |

---

### 15. Document Links

**LSP Method:** `textDocument/documentLink`
**Status:** ✅ Done (URLs in comments and string literals)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* URLs appear as clickable links inside comments and string literals (Ctrl+Click)
- *VS Code:* URLs appear as clickable underlined text inside comments and string literals (Ctrl+Click)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15.1 | URL in line comment | Add `// see https://example.com for more` | URL is underlined; Ctrl+Click opens browser |
| 15.2 | URL in block comment | Add `/* docs at https://docs.xtclang.org */` | URL is underlined and clickable |
| 15.3 | URL in doc comment | Add `/** Reference: https://example.com */` above a class | URL is underlined and clickable |
| 15.4 | URL in string literal | Add `String url = "https://api.example.com/v1";` | URL inside the string is clickable |
| 15.5 | URL in template string | Add `String s = $"see https://x.test/{name}";` | URL is clickable; the interpolation continues to work |
| 15.6 | Trailing punctuation trimmed | Add `// see https://example.com.` (period at end) | The link target is `https://example.com` without the trailing `.` |
| 15.7 | URL in parens | Add `// (see https://example.com)` | Match excludes the closing `)`; target is the URL only |
| 15.8 | Multiple URLs in one comment | Add `// links: https://a.test and https://b.test` | Both URLs are independently clickable |
| 15.9 | No link on `import` | Add `import ecstasy.text.String;` | Import path is **not** underlined as a document link (Ctrl+Click on the type name still navigates via go-to-definition) |

**Notes:**
- Import-path document links were intentionally removed in PR #446. Cmd/Ctrl-click navigation on imports still works via `textDocument/definition` (it knows the difference between a package component and a type component, and degrades gracefully when nothing resolves).
- The URL matcher trims sentence-final punctuation (`.,;:!?}`) but stops at whitespace, quotes, angle brackets, or `)` / `]` — so an in-prose URL like `(https://example.com)` keeps the URL clean.
- The `documentLinkProvider` capability is still advertised, so the IntelliJ plugin doesn't need to renegotiate when additional non-URL providers are added (e.g. file-path links in resource strings, doc-comment cross-references) later.

---

### 15a. Extra source roots (`xtcSourceRoots`)

**LSP Method:** `initialize` — `initializationOptions.xtcSourceRoots`
**Status:** ✅ Done (added in PR #446)
**Works with:** Any client that sends initialization options

Lets users index `.x` source roots that live outside the open workspace folders. Three input channels (in priority order, all merged then deduplicated):
1. LSP `initializationOptions.xtcSourceRoots` — a JSON array of path strings.
2. System property `xtc.sourceRoots` — path-separator-delimited (`:` on Unix, `;` on Windows).
3. Environment variable `XTC_SOURCE_ROOTS` — same delimiter rules.

Workspace folders take precedence; extra roots are merged in. Non-existent paths are dropped with a warning at LSP startup.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15a.1 | env var resolves cross-tree imports | Open a project that imports a module whose sources live in another checkout. Launch LSP with `XTC_SOURCE_ROOTS=/path/to/xtclang2/lib_json/src/main/x` set | Cmd-click on the imported type navigates into the external source tree |
| 15a.2 | system property resolves cross-tree imports | Same as 15a.1 but pass `-Dxtc.sourceRoots=/path/to/lib_json/src/main/x` to the LSP JVM (via plugin advanced settings or VS Code launch config) | Same — imported type resolves |
| 15a.3 | initializationOptions resolves cross-tree imports | Configure the IntelliJ plugin (or VS Code extension) to send `initializationOptions: { xtcSourceRoots: ["/path/to/lib_json/src/main/x"] }` | Same — imported type resolves |
| 15a.4 | nonexistent path drops + warns | Set `XTC_SOURCE_ROOTS=/this/does/not/exist:/valid/path/lib_x/src/main/x` | LSP startup log warns about the missing path; the valid path still indexes |
| 15a.5 | path-separator handling | On macOS/Linux: `XTC_SOURCE_ROOTS=/a/path:/another/path`. On Windows: `XTC_SOURCE_ROOTS=C:\a;C:\b` | Both paths are added to the indexer |
| 15a.6 | dedup with workspace folders | Set the env var to a folder that's already in the workspace | Folder appears once in the index, not twice |

**Notes:**
- Without this feature, an `import json.xtclang.org;` from a project that doesn't have the `lib_json` source files in its workspace would fail to resolve via Cmd-click.
- This does **not** index `.xtc` binaries. A module that exists only in compiled form is still invisible to navigation. Resolving that would require either an XTC binary reader plus a virtual decompiled view, or shipping `.x` sources alongside the install distribution. Out of scope.
- The IntelliJ plugin doesn't yet expose `xtcSourceRoots` as a settings panel; for now the env var or system property is the practical path. Plugin-side work is a follow-up.

---

## Adapter Comparison Summary

> See [plan-ide-integration.md](plans/plan-ide-integration.md#adapter-capability-matrix) for the canonical adapter comparison matrix.

---

## Troubleshooting

### IntelliJ Theme / Sandbox Looks Wrong

If IntelliJ suddenly shows `.x` files with a white fallback background, washed-out colors,
or obviously stale plugin behavior:

```bash
./gradlew --stop
rm -rf lang/.intellijPlatform/sandbox
rm -rf lang/.intellijPlatform/localPlatformArtifacts
```

Then rerun `:lang:intellij-plugin:runIde`.

This should not be required for normal development, but it is a useful reset when plugin
auto-reload or stale sandbox state has clearly contaminated the test run.

### Tree-sitter Not Loading

If you see `"fallback - tree-sitter native lib missing"` in logs:

```bash
# 1. Verify native library exists
ls lang/tree-sitter/src/main/resources/native/darwin-arm64/  # macOS ARM
ls lang/tree-sitter/src/main/resources/native/linux-x64/     # Linux

# 2. Rebuild if missing
./gradlew :lang:tree-sitter:buildAllNativeLibraries
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources

# 3. Verify not stale
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate
```

### LSP Not Connecting

1. Check LSP4IJ plugin is installed in IntelliJ
2. Check `.x` files are associated with the Ecstasy language
3. Look for errors in: Help → Show Log in Finder/Explorer
4. Try: File → Invalidate Caches / Restart

### No Syntax Highlighting

**IntelliJ:**
```bash
# Verify TextMate bundle is present
ls lang/intellij-plugin/build/idea-sandbox/*/plugins/intellij-plugin/lib/textmate/
# Should contain: xtc.tmLanguage.json, package.json, language-configuration.json
```

**VS Code:**
1. Check the extension is installed: Extensions panel → search "XTC"
2. Verify `.x` files are associated: look for "XTC" in the status bar language indicator
3. If missing: `code --install-extension lang/vscode-extension/xtc-language-*.vsix`

### VS Code LSP Not Starting

1. Open Output panel → select "Ecstasy Language Server"
2. If no output channel exists, the extension failed to activate
3. Check `JAVA_HOME` or `XTC_JAVA_HOME` points to Java 25+
4. Verify the fat JAR exists: `ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar`
5. Try Developer Tools: Help → Toggle Developer Tools → Console tab

---

---

## Out-of-Process LSP Server Tests

The LSP server runs as a separate Java process (requires Java 25+). These tests verify
the process management and health monitoring.

### Prerequisites

- Java 25+ installed and available via `JAVA_HOME` or on PATH
- XTC project with `.x` files

### Test: Server Startup

```bash
./gradlew :lang:intellij-plugin:runIde
```

**Expected in console:**
```
[XTC-LSP] Ecstasy Language Server v0.4.4-SNAPSHOT
[XTC-LSP] Backend: Tree-sitter
[XTC-LSP] TreeSitterAdapter ready: native library loaded and verified
[XTC-LSP] XtcParser health check PASSED: parsed test module successfully
```

**Expected in IDE:**
- Notification title: **Ecstasy Language Server Started**. The body has separate `Version: …`,
  `Adapter: treesitter` and `Process ID: …` lines (or `Adapter: compiler` for a compiler build).

### Test: Health Check

1. Open an `.x` file in the IDE
2. Look for console output:
   - `Native library: extracted libtree-sitter-xtc.dylib to ...`
   - `Native library: successfully loaded XTC tree-sitter grammar (FFM API)`
   - `XtcParser health check PASSED`

### Test: Crash Recovery

1. Find the LSP server process: `ps aux | grep xtc-lsp-server`
2. Kill it: `kill -9 <pid>`
3. Verify notification appears: "Ecstasy Language Server Crashed"
4. Click "Restart Server"
5. Verify server restarts (new notification)

### Test: Version Display

1. After LSP starts, check notification shows correct version
2. Version should NOT be "?" - should show actual version like "v0.4.4-SNAPSHOT"

### Test: Native Library Not Found

1. Temporarily rename/remove native libraries from JAR
2. Start IDE
3. Verify error notification about native library
4. Verify fallback to mock adapter (or fail-fast error)

### Test: Java Version Too Low

> **NOTE:** With IntelliJ 2026.1+ (JBR 25), this test is no longer relevant since the
> IDE always ships with a Java 25+ runtime. The LSP server uses IntelliJ's JBR directly
> via `JavaProcessCommandBuilder`.

---

### 16. Comment Toggling (IntelliJ)

**Provider:** IntelliJ plugin (`XtcCommenter`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `language-configuration.json` for this)

This is a client-side editing feature — the LSP server handles comment *formatting*
(alignment, continuation on Enter), but toggling comment delimiters is purely an IDE action.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 16.1 | Line comment | Place cursor on a line, press Ctrl+/ | `// ` inserted at start of line |
| 16.2 | Uncomment | On a `//`-commented line, press Ctrl+/ | `// ` removed |
| 16.3 | Multi-line comment | Select 3 lines, press Ctrl+/ | All 3 lines get `// ` prefix |
| 16.4 | Multi-line uncomment | Select 3 commented lines, press Ctrl+/ | `// ` removed from all 3 |
| 16.5 | Block comment | Select text, press Ctrl+Shift+/ | Selection wrapped in `/* */` |
| 16.6 | Block uncomment | With cursor inside `/* */`, press Ctrl+Shift+/ | `/* */` removed |

---

### 17. Live Templates (IntelliJ)

**Provider:** IntelliJ plugin (`liveTemplates/XTC.xml`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `snippets/xtc.json` separately)

Live templates are code snippets triggered by abbreviation + Tab. Press Ctrl+J to
see all available templates. The same snippets are available in VS Code (type the
prefix and select from the completion popup).

**Available templates:**

| Prefix | Expansion | Category |
|--------|-----------|----------|
| `mod` | `module name { }` | Declaration |
| `cls` | `class MyClass { }` | Declaration |
| `iface` | `interface MyInterface { }` | Declaration |
| `svc` | `service MyService { }` | Declaration |
| `mix` | `mixin MyMixin into Base { }` | Declaration |
| `enu` | `enum MyEnum { Value1, Value2 }` | Declaration |
| `con` | `const MyConst(params);` | Declaration |
| `pkg` | `package json import json.xtclang.org;` | Declaration |
| `meth` | `void myMethod() { }` | Method |
| `run` | `void run() { @Inject Console console; }` | Method |
| `runa` | `void run(String[] args=[]) { ... }` | Method |
| `construct` | `construct(params) { }` | Method |
| `prop` | `String name;` | Property |
| `roprop` | `@RO Boolean empty.get() = size == 0;` | Property |
| `lazy` | `private @Lazy String value.calc() { }` | Property |
| `if` | `if (condition) { }` | Control flow |
| `ife` | `if (condition) { } else { }` | Control flow |
| `ifv` | `if (Value value := get(key)) { }` | Control flow |
| `fori` | `for (Int i : 0 ..< count) { }` | Control flow |
| `forr` | `for (Int x : 1..100) { }` | Control flow |
| `fore` | `for (Element item : collection) { }` | Control flow |
| `while` | `while (condition) { }` | Control flow |
| `switch` | `switch (value) { case 0: }` | Control flow |
| `try` | `try { } catch (Exception e) { }` | Control flow |
| `using` | `using (resource) { }` | Control flow |
| `assert` | `assert condition;` | Control flow |
| `assertm` | `assert condition as "message";` | Control flow |
| `sout` | `@Inject Console console; console.print();` | Common |
| `print` | `console.print();` | Common |
| `inject` | `@Inject Console console;` | Common |
| `lambda` | `(params) -> expr` | Common |
| `cond` | `conditional Value find() { }` | Common |
| `doc` | `/** description */` | Comment |
| `todo` | `// TODO` | Comment |
| `webapp` | Full @WebApp module with web service | Skeleton |
| `websvc` | `@WebService("/") service { @Get ... }` | Web |
| `hello` | Complete Hello World module | Skeleton |

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 17.1 | Module template | Type `mod` + Tab | Expands to `module name { }` with cursor on name |
| 17.2 | Class template | Type `cls` + Tab | Expands to `class MyClass { }` |
| 17.3 | For loop (range) | Type `fori` + Tab | Expands to `for (Int i : 0 ..< count) { }` |
| 17.4 | For loop (inclusive) | Type `forr` + Tab | Expands to `for (Int x : 1..100) { }` |
| 17.5 | For-each | Type `fore` + Tab | Expands to `for (Element item : collection) { }` |
| 17.6 | Console print | Type `sout` + Tab | Expands to `@Inject Console console;` + `console.print();` |
| 17.7 | If conditional assign | Type `ifv` + Tab | Expands to `if (Value value := get(key)) { }` |
| 17.8 | Hello World | Type `hello` + Tab | Expands to complete Hello World module |
| 17.9 | WebApp skeleton | Type `webapp` + Tab | Expands to full @WebApp module with web service |
| 17.10 | Template list | Press Ctrl+J in editor | Shows all XTC templates with descriptions |
| 17.11 | Tab navigation | Type `meth` + Tab, fill return type, Tab, fill name | Cursor moves through variables in order |
| 17.12 | Inject template | Type `inject` + Tab | Expands to `@Inject Type name;` |
| 17.13 | Mixin template | Type `mix` + Tab | Expands to `mixin Name into Base { }` |
| 17.14 | Conditional method | Type `cond` + Tab | Expands to `conditional Value find() { }` |

---

### 18. Code Lens (Run Actions)

**LSP Method:** `textDocument/codeLens`
**Status:** ✅ Done
**Works with:** Tree-sitter and compiler adapters (both IntelliJ and VS Code)

Code lenses appear as inline annotations above module declarations. LSP4IJ (IntelliJ)
and VS Code render them automatically from the LSP server response — no plugin code needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 18.1 | Run lens on module | Open a `.x` file with `module myapp { }` | "▶ Run myapp" appears above the module declaration |
| 18.2 | No lens on class | Open a file with only `class Foo { }` (no module) | No code lens appears |
| 18.3 | Lens position | Check the lens annotation position | Aligned with the `module` keyword line |
| 18.4 | Multiple files | Open two `.x` files with different modules | Each shows its own module's Run lens |

---

### 19. Semantic Tokens

**LSP Method:** `textDocument/semanticTokens/full`
**Status:** ✅ Done (enabled by default)
**Works with:** Tree-sitter and compiler adapters (both IntelliJ and VS Code)

Semantic tokens layer on top of TextMate highlighting, providing AST-aware coloring
that TextMate's regex patterns cannot achieve. The server logs `semantic tokens ENABLED`
at startup to confirm they're active.

**How to verify:**
- *IntelliJ:* Open a `.x` file — types, methods, properties, and annotations should
  have distinct colors. Check LSP server log for `semantic tokens ENABLED`.
- *VS Code:* Same — semantic tokens are automatically layered on top of TextMate.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 19.1 | Types colored distinctly | Open file with `String name;` and `Int count;` | `String` and `Int` have type color (different from `name`/`count`) |
| 19.2 | Methods vs properties | Open file with `void foo()` and `String name;` | `foo` has method color, `name` has property color |
| 19.3 | Annotations as decorators | Add `@Override` or `@Inject` | Annotation name has decorator color |
| 19.4 | Deprecated strikethrough | Add `@Deprecated class Old {}` | `Old` shown with strikethrough |
| 19.5 | new Foo() as type | Write `new Person()` | `Person` colored as type, not method |
| 19.6 | Method call coloring | Write `getName()` | `getName` colored as method call |
| 19.7 | Static modifier | Add `static void helper()` | `helper` may show italic (static modifier) |
| 19.8 | Enum members | Write `enum Color { Red, Green, Blue }` | `Red`, `Green`, `Blue` colored as enum members |
| 19.9 | Parameter highlighting | Write `void foo(Int count)` | `count` has parameter color |
| 19.10 | Namespace coloring | `module myapp` declaration | `myapp` colored as namespace |
| 19.11 | Server log confirmation | Check LSP server log at startup | Shows `semantic tokens ENABLED (23 types, 10 modifiers)` |

---

## XdkAdapter Playbook

Run this section with the opt-in **compiler** backend in either editor. Tree-sitter remains the
shipped default. These checks cover the current compiler feature surface, including semantic
answers that a syntax parser cannot supply. They do not require running the test program.

### Automated VS Code run

Run the compiler playbook from the repository root:

```bash
./gradlew :lang:vscode-extension:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

This builds the extension and its bundled compiler, runs the server and packaged-JAR regression
suites, then launches a real VS Code extension host. It reads the fixtures below directly, creates
a separate workspace/profile, and runs one case for every X1–X145 row plus the configuration and
compiler-diagnostic checks. Missing case IDs, a wrong backend, failures and skipped editor cases
fail the run. The editor cases run on every invocation; Gradle may reuse unchanged host-test results.
The test window's status bar shows completed/selected cases, remaining cases and the current case,
including failure and skip counts. Focused selections use their own total rather than the full
catalog. The ordinary `testVscodeExtension` smoke suite shares this display, labelled **Ecstasy
tests**; the catalog is labelled **Ecstasy playbook**. Both appear at the left of the status bar.
Long test names are abbreviated there and shown in full in the tooltip. Smoke runs also use fresh
workspaces/profiles and retain logs under `build/reports/extension-tests/run-*/`.
To force fresh host results as well, add `:lang:lsp-server:test --rerun` and
`:lang:lsp-server:compilerStdioTest --rerun` to the command.

Reports and failing source buffers remain under
`lang/vscode-extension/build/reports/compiler-playbook/run-*/`. `latest-run.txt` identifies the
latest directory; `results.txt` is readable and `results.json` includes the commit, dirty paths,
VS Code version, case durations/failures and host-test XML evidence. VS Code's logs are under that
run's `logs/`. The short temporary profile is removed after shutdown. Your normal editor profile
and manually prepared scratch workspace are separate.

These checks exercise editor providers, real document edits and filesystem watchers. Raw LSP
requests additionally verify hierarchy snapshot identity, cancellation, versioned rename and
named nonexistent-file overlays. After reverting/closing a tab, the runner uses the language client's
synchronization provider to send `didClose` for any retained hidden text model; reopening sends `didOpen`.
They do not drive every menu/key or inspect rendered pixels:
popup/hierarchy layout, theme appearance, physical keyboard interaction and a prolonged interactive
memory/GC soak remain manual. The report identifies those limits and maps 7a.1–7a.14 and the host-only
API checks to their automated coverage. This command covers the **XdkAdapter** playbook, not the
separate debugger, IntelliJ or Tree-sitter playbooks.

After assembling with the same compiler flag, `npm run test:playbook` from `lang/vscode-extension`
reruns just the editor suite. It reports existing host-test evidence without rebuilding/rerunning it.
See the [extension testing notes](../vscode-extension/README.md#testing) for display/`xvfb` requirements.

The host suite also includes `CompilerEmissionAuditTest`: it compiles atomic operations through
qualified, singleton, enclosing-instance and generic receivers, deserializes their binary ASTs,
checks source/dependency diagnostic delivery, and inspects dense-switch conversion metadata. These
are compiler-output checks that the editor UI cannot establish. To run them without launching VS Code:

```bash
./gradlew :lang:lsp-server:test --tests org.xvm.lsp.adapter.CompilerEmissionAuditTest \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

### Automated IntelliJ run

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

The Starter/Driver suite launches the packaged plugin in IDEA 2026.2.3 with Ultimate features
disabled. The catalog now has 128 scenarios. The preceding 113-case suite has a clean full-run
receipt; the [current demo record](../../docs/errs-integration-plan.md#native-intellij-demo-continuation-2026-09-29)
distinguishes resumed coverage from an uninterrupted full run. Native
completion checks now keep sole candidates visible in the disposable test profile, verify exact
candidate sets and accept the actual edit. The same checks cover constructor and argument-value
completion. X1/X92 inspect native Structure/folding, X4 uses Find/Highlight Usages, and error/warning
cases verify received compiler metadata, Problems rows, Next Problem navigation and clearing.
X45/X46 check unopened dependencies, unchanged consumer text versions and unsaved library bytes.
X91–X96 cover simple, qualified, parameterized/compound and class/interface composition headers,
registered formals, empty generic arguments and whole-token replacement.
X106–X108 cover function/sequence leaves, trailing dots, empty type operands and declaration
headers. Discovery/refactoring cases use the same per-scenario workspace scope as VS Code.
X103 additionally reverses its file rename with the member open and verifies diagnostics clear.

X20 now checks that the native popup retains the full candidate label without an invented active
argument. X81/X82 inspect Property-kind metadata from the native completion request as well as
candidates and edits. All 50 formerly missing cases now have test bodies. Protocol-only invariants
are checked through the installed client, while visible actions still use the native UI.
Nonexistent/deleted-file diagnostics are observed in its verbose trace because LSP4IJ drops these
from its VFS diagnostic store. X57 reproduced LSP4IJ's stale-edit application and now passes
through the guarded Ecstasy Rename handler. The new subset has 50/50 individual passing receipts;
X29 now passes with current signature metadata. Problems-row clicking and visual layout remain manual.
X31/X32 also pass in VS Code (`run-KfBbW1`). At empty invalid argument slots, VS Code may offer
the extension's static snippets; X32 checks the compiler response separately and rejects any
non-snippet editor proposals.
X33–X35 and X99–X108 have passing native receipts across the checkpoint and
focused X105 rerun. Execution details and the fixes found during validation are in the
[active validation record](../../docs/errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).
The later full checkpoint `run-6034631232732848040` passes all 113 scenarios plus START, with
zero IDE errors and zero JUnit failures/errors/skips. Gradle completes in 6 minutes 40 seconds.
Corrections found during validation include X41's PSI read action, the session timeout, focus
handling, and X30's document-readiness check after restart. The driver allows 30 minutes overall
while retaining individual bounded waits. Separate startup acceptance also verifies disappearance
of the untouched information balloon. See the
[L60 receipts](../../docs/errs-integration-plan.md#intellij-parity-backlog-l60).

Parameter Info checks keep every overload's text assertion, but only enabled overload rows
may bold the active argument. Inactive rows are dimmed according to `activeSignature`.
The 7a.9 driver creates its own `Broken.x` empty-module baseline from shared data before
inserting the invalid declarations, then checks native Problems rows and diagnostic clearing.

Results are under `lang/intellij-plugin/build/reports/compiler-playbook/run-*/results.json`.
`ide-paths.txt` identifies the separate IDE profile/log directory. A graphical desktop is
required. See the [IntelliJ test and configuration instructions](../intellij-plugin/README.md#compiler-playbook-in-intellij).

During an automated run, leave its isolated editor window to the harness. The driver selects tabs,
invokes active-editor commands and closes buffers; concurrent manual navigation can change those
targets or cancel requests even without typing. The title and status bar show completed/selected
cases, remaining cases and the current case. When focus is lost during popup inspection, the
harness activates the IDE without moving the pointer and reopens the inspection only if the
document's modification stamp is unchanged. Accepted completion edits, quick fixes, renames and
file moves are outside the replay path. Typing in the fixture during recovery fails the check.
Automatic activation can redirect typing into the test IDE; use an isolated desktop for concurrent
work. Ordinary file opening and caret movement request no focus. Other native controls may still
use the mouse. Readiness polling is 100 ms; operation deadlines remain unchanged.
The disposable profile disables autosave and automatic completion/sole-candidate insertion;
personal settings and shipped plugin defaults are unchanged. Cleanup closes the IDE after a
failed check; the recorded failures were not IDE crashes.
CFG2 deliberately submits a cyclic dependency graph and expects an error notification while the
last valid graph remains usable. That notification is an expected negative test; until its scoped
cleanup is implemented it may remain visible over later cases.

While compiler feature coverage is growing, run the IntelliJ playbook at occasional checkpoints;
it is not required for every compiler change. Keep shared scenarios and the native consumer code
current, and state explicitly when a new IntelliJ case has not yet had a native run.

### Focused VS Code reruns

For a correction limited to one scenario, select exact case IDs. This builds the extension and
runs only the selected editor cases; it does not schedule the LSP unit or packaged stdio suites.
For example, rerun the class/interface header case:

```bash
./gradlew :lang:vscode-extension:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler \
    -PcompilerPlaybookCases=X95
```

Use `-PcompilerPlaybookCases=X94,X95,X96` for the related type-completion group. Without that property the existing full
playbook plus host checks run. With an assembled extension, the direct equivalent from
`lang/vscode-extension` is `npm run test:playbook -- --cases=X95`.
Unknown, duplicate or empty IDs fail before VS Code opens. Reports state `focused`, list selected
IDs and mark every excluded case `not-selected`; every selected case must pass. Existing host XML
is supporting evidence with timestamps, not a claim that focused runs executed it. Use full runs
for broad changes and checkpoints, and focused runs for bounded fixes after a full regression pass.
IntelliJ has the equivalent `-PintellijPlaybookCases=X34,X99,X100,X101,X102,X103,X104,X105,X107,X108`.
It always runs startup, reports unselected implementations as `not-run`, and requires every selected
case to execute. Native runs remain occasional checkpoints.

### Shared editor scenarios

Both drivers read [the shared scenario data](../test-fixtures/compiler-playbook/scenarios.json)
for all 148 scenarios: X1–X143, CFG1–CFG3 and 7a.8–7a.9. The catalog owns titles, source-module
configuration, fixture selectors, edits, cursor/definition anchors, variants, expectations and
manual-check notes. Base programs remain the canonical fixtures below; bounded replacement
programs also live in the shared scenario values. A `§` marks an offset;
`${0}` templates substitute literal values without evaluating code.

Native TypeScript and Kotlin code still performs editor actions and assertions. VS Code executes
all 148 cases. IntelliJ now has assertions for the same 148, plus a separate startup check.
Current selected native pass receipts are recorded above; implementation is not validation. A missing driver implementation
must be called `not-implemented`, not an unsupported IDE feature. `not-run` means an implemented
case was unselected or prevented from running, such as after an earlier failure. Partial coverage never appears
as a full pass. A failed implemented check fails the Gradle task.
These counts describe implemented assertions. Selected native execution receipts are recorded
separately above; unselected cases remain `not-run`, and the complete native checkpoint is pending.

Both reports include the shared file, SHA-256 and complete ID list. Both drivers compare catalog
IDs with the manual table; VS Code also checks exact registration order, and IntelliJ checks that
every case declared full/partial executes. To add a scenario, add its data and VS Code consumer,
then implement its native IntelliJ actions or record the exact missing assertions in its coverage
entry. Keep client-specific protocol and UI mechanics in the drivers.

### Launch and confirm the backend

From the repository root, choose one command. Keep `-Plsp.adapter=compiler` on the editor launch
task too: a later build without it can restore the default backend.

```bash
# IntelliJ sandbox
./gradlew :lang:runIntellijPlugin \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler

# Or VS Code Extension Development Host
./gradlew :lang:vscode-extension:runCode \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

Open a scratch folder in that editor window. Confirm `backend: Ecstasy Compiler` in
`~/.xtc/logs/lsp-server.log`; the health-check adapter name is `XDK`. The matching XDK libraries
are bundled, so no `XDK_HOME`, extracted distribution or separate compiler installation is needed.
Confirm the backend in the log even when colors look familiar: compiler semantic tokens cover
resolved names, while the editor's TextMate colors and snippets also remain available.

Use editor action search if a shortcut conflicts with your keymap. The VS Code actions are listed
in the [keybindings reference](#keybindings-reference); IntelliJ provides Quick Documentation,
Go to Declaration, Find Usages, Basic Completion and Parameter Info. For hierarchy, use the
client's LSP type-hierarchy view (VS Code: **Show Type Hierarchy**). If the installed client does
not expose an action, record that case as **not exercised**, not a server pass.

Create the files below exactly as named. `|` in a test row marks the cursor; **do not type it**.
Undo each temporary edit before the next row and wait for diagnostics to settle. A clean baseline
is important: parse failures can suppress normal semantic answers throughout a module.

### A. Diagnostics, navigation and structure

Save this as `Navigation.x`:

```xtc
module Navigation {
    class Holder {
        Int value = 1;
        Int read() {
            Int value = 2;
            return value + this.value;
        }
    }

    String text(Object input) {
        Object value = input;
        if (value.is(String)) {
            return value;
        }
        return value.toString();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X1 | Open the saved file; inspect Problems, Outline, folding and Expand Selection inside `return value;`. | No errors. Outline includes the module, class, property and methods; folding follows their blocks; selection grows through enclosing syntax. |
| X2 | In `read`, change `Int value = 2;` to `String value = 2;`, then undo. Separately change `input` to `missing` in the initializer in `text`. | Real compiler diagnostics identify the type mismatch and unresolved name at their source spans, with compiler codes. Each clears after correction without saving. `// ERROR: test` alone produces no diagnostic. |
| X3 | Hover `value` in `text`'s `return value;`, then in `return value.toString();`. | The first use has narrowed type `String`; the second has `Object`. Go to Definition from either reaches the same local declaration. |
| X4 | In `read`, use Go to Definition, Find References and occurrence highlighting on the bare `value` in `return value + this.value;`. Repeat on `this.value`. | The local and property lead to different declarations and separate reference sets despite identical spelling. Highlights stay within the document and distinguish reads from writes; X41 checks an assignment target. |
| X5 | Run Go to Definition on `String`. Then remove the final closing brace and inspect Outline, folding and selection; restore it. | The matching bundled library opens read-only at the String declaration. Recoverable syntax retains surrounding structure with a parser diagnostic; stale semantic targets are not reused. A badly broken header may leave structural gaps or a cursor-only selection. |

For the error-listener regression, also run **7a.8** with `DupAnno.x`: the duplicate inherited
annotation produces exactly one `VERIFY-75` warning. Rows **7a.1–7a.7** cover bundled-library startup,
timings, queued/superseded edits and memory. Timings depend on hardware and module size; record them
rather than treating the illustrative numbers as pass thresholds.

**Problems view checks (X2 and 7a.8).** In VS Code, open **View → Problems** (Cmd+Shift+M on macOS,
Ctrl+Shift+M elsewhere). Clear the text filter, enable both Errors and Warnings, and disable
“Active File Only” while comparing files.

1. Make each X2 edit. Check that the row belongs to Navigation.x, has **Error** severity, the
   compiler message/code and a line/column matching the marked source range. Click the row or use
   **Go to Next Problem** (F8); the editor must select that source location.
2. Correct it without saving. The row, red squiggle and error count must clear after analysis.
3. Open DupAnno.x from 7a.8. Check exactly one **Warning** row with code `VERIFY-75`. This warning
   selects the derived property's `x` declaration. The compiler reports a structure, and the adapter
   maps that exact declaration to its source token. Remove only the derived `@Atomic`: the warning
   row/count must clear without saving. Undo to restore one warning. Binary-only structure
   diagnostics retain a document-level fallback.
4. Leave the warning in DupAnno.x and introduce an X2 error in Navigation.x. Both file groups and
   severities must remain visible; correcting one must not remove the other's diagnostic.

The automated cases check diagnostic severity, code, source range, unsaved clearing and the
editor's next-problem command. They open the Problems view; inspecting its rendered rows, severity
icons, filters, counts and clicking a row remains a visual/manual check.

### B. Typed and scope-aware completion

Save this as `Editing.x`:

```xtc
module Editing {
    class Box<T> {
        T item;
        private T itemMethod() = item;
        String choose(String first, String second) = first;
        Int choose(Int first, Int second) = first;
        T pair(T first, T second) = first;
        <U> U generic(U first, U second) = first;
        void inspect(T itemLocal) {}
    }

    class Tools {
        static Int itemFunction() = 1;
        static Int itemConstant = 2;
        Int itemProperty = 3;
        Int itemMethod() = 4;
        private static Int itemHidden() = 5;
        static class itemType {}
        private static class itemPrivate {}
    }

    String getValue() = "text";
    void run(Box<String> box, String itemParameter, Object value) {}
}
```

Use **Trigger Completion / Basic Completion**, not just the popup triggered by typing. Replace
the body of `run` for X6–X10 and X12; for X11 use `Box.inspect`. Undo after each row.

These rows deliberately contain unfinished names. For example, `.si` is the partially typed
`.size` in X7/X8/X14. `COMPILER-36: Could not find name "si" within "String"` is expected at that
point: `String` is the receiver's type, and `si` is not one of its members. (`COMPILER-38` is the
related `Name "..." is unresolvable` diagnostic.) The completion probe
offers `size` while normal compilation still reports the unfinished source. The test checks that
the diagnostic clears after accepting the completion; it is not a failure merely to see it while
the runner is typing.

| # | Temporary body / action | Expected result |
|---|-------------------------|-----------------|
| X6 | `box.|;`, then `box.it|;` | Public members include `item` with substituted type `String`. The prefix filters to matching names; private `itemMethod` is absent. Accepting `item` replaces only `it`, preserving the receiver and semicolon. A bare property access is not a valid statement; change the accepted text to `String selected = box.item;` to confirm diagnostics clear. |
| X7 | `Int size = getValue().si|;` | `size` is offered from the expression receiver's `String` type. Accept it: `Int size = getValue().size;` has no errors. |
| X8 | `if (value.is(String)) { Int size = value.si|; }` | `size` is available because of flow narrowing. Accept it and check diagnostics clear. Undo the guard too when finished. |
| X9 | `Int itemLocal = 1; Int itemUnassigned; item|; Int itemLater = 2;` | Offers `itemLocal` and `itemParameter`; excludes the unreadable unassigned variable and the later declaration. |
| X10 | Put the cursor in the empty `run` body and invoke completion without typing a prefix. | Visible parameters such as `itemParameter` appear. Accepting one inserts at the cursor without deleting a brace or nearby text. Undo the insertion afterward. |
| X11 | In `Box.inspect`: `ite|;`. Then try `{ Int itemClosed = 1; } Int item = 2; ite|;`. | First: implicit `item`, private `itemMethod` and parameter `itemLocal`, with formal type `T`. Second: local `item` has type `Int`, the property is shadowed, and closed-scope `itemClosed` is absent. |
| X12 | In `run`: `Tools.item|;` | Offers static `itemFunction`, `itemConstant` and nested `itemType`; excludes instance `itemProperty`/`itemMethod` and private `itemHidden`/`itemPrivate`. |
| X13 | In `run`: `Strin|;`. Then add module import `import ecstasy.text.StringBuffer as Buffer;` and try `Buffe|;`. Replace the import with `import ecstasy.text.*;` and try `StringBuffe|;`. | Implicit `String`, explicit alias `Buffer`, then wildcard-imported `StringBuffer` appear. Restore the file between import variants. |
| X14 | Repeat X7 with `/* 😀 */` before the statement on the same line and the file saved with CRLF line endings. | The accepted completion still replaces exactly `si`. No shifted edit, damaged emoji or extra character. |

### C. Selected signatures and incomplete-call candidates

Keep `Editing.x`. Replace `run`'s body with the call in each row. Invoke **Trigger Parameter Hints /
Parameter Info** at `|`; leave the closing `)` in place, as an editor normally does.

| # | Call / action | Expected result |
|---|---------------|-----------------|
| X15 | `box.choose("x", |);`, then `box.choose(1, |);` | The first offers the `String` overload, the second the `Int` overload. The second parameter is active. These are applicable **candidates**, not final overload selections. |
| X16 | `box.pair(second = "x", first = |);` | Signature types are `String` from `Box<String>`; `first` is active despite being the second written argument. Insert `"y"`: diagnostics clear and the now-valid call uses the compiler-selected signature. Go to Definition on `pair` reaches its declaration. |
| X17 | `box.generic("x", |);`, then `box.generic(|);` | The first infers `String` for the expected second parameter. Without an argument, the type remains formal `U`, not an invented `Object`. |
| X18 | `box.choose(True, |);`, `box.pair(unknown = |);`, and `box.pair(first = "x", first = |);`, one at a time | No applicable signature for incompatible types, an unknown label or a duplicate label. Earlier hints must not remain visible as the answer to the new request. |
| X19 | Inside `Box.inspect`: `pair(itemLocal, |);`. Then add `static String join(String first, String second) = first;` to `Tools` and try `Tools.join("x", |);` in `run`. | Implicit-instance and static calls both show applicable candidates with the second parameter active. Fill in the missing values and confirm diagnostics clear. |
| X20 | Use `box.pair(second = "x", |);`. Then complete `box.choose("x", "y");` and navigate from `choose`; repeat with `box.choose(1, 2);`. | After a named argument without a new label, no guessed parameter is highlighted. Complete calls select and navigate to the correct distinct overloads. |

The candidate label/documentation does not mean an unfinished overload has been selected. Ecstasy
allows trailing commas in valid calls: such a call can already have a selected signature.
Empty final positional slots and pending named values also offer compiler-fitted readable locals,
parameters and implicit properties/constants, including direct final typed argument prefixes (X77–X82). Normal diagnostics may
remain while these deliberately incomplete calls
still provide useful hints; accepting a valid value clears them.

### D. Module files, overlays and type hierarchy

Create this layout in the scratch folder. Save both files, then close `Child.x` while keeping
`Project.x` open.

```text
Project.x
Project/
    Child.x
```

`Project.x`:

```xtc
module Project {
    interface Named {}
    class Base<Element> implements Named {
        Element echo(Element value) = value;
        Int item = 1;
    }
}
```

`Project/Child.x`:

```xtc
class Child extends Base<String> {
    String answer() = echo("member");
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X21 | From `Base` in the root, Find References and search workspace symbols for `Child` (Go to Symbol in Workspace). Open Child and navigate from `Base` and `echo`. | References and symbol search include the closed member. Definitions land on the correct names in Project.x. Search covers analysed module sessions, not every unopened module in the workspace. |
| X22 | Show Type Hierarchy on `Child`; expand supertypes. Show it on `Base` and `Named`; expand subtypes. | Direct links are `Child → Base<String> → Named`, with correct file locations. Expanding Base's subtypes finds Child even if its tab is closed. Generic arguments remain visible. |
| X23 | Keep both files open. Change Base's name to `Renamed` in the root without saving, then undo. | Child's diagnostic changes without an edit there, then clears after undo. No generic internal error is added to the root merely because the member has an error. |
| X24 | Add `void inspect() { ite|; }` in Child and complete. Change root property to `String item = "overlay";` without saving; repeat completion in Child. | `item` changes from `Int` to `String` using the unsaved root. Undo the root change: completion returns `Int`. No temporary source files are written. |
| X25 | Create a named, unsaved `Project/pkg/Added.x` buffer with `class Added extends Base<String> {}`. | The new member and implicit package join the current module; diagnostics and Base's hierarchy reflect Added. An anonymous Untitled buffer without this URI is not this test. |
| X26 | With Project.x still open, introduce an error in Child, then discard changes and close Child. Reopen it. | The saved disk version replaces the overlay; its obsolete diagnostic clears. Navigation and hierarchy use the restored source. |
| X27 | With Project.x open, create `Project/Bad.x` on disk containing `class Bad extends Missing {}`; wait, then delete it. | Watched-file notifications refresh membership; the closed file's diagnostic appears and then clears. Run in a workspace containing these files so the client watches them. |
| X28 | Open hierarchy on Child. Temporarily replace the member file with `class Child {}`, wait for analysis, then expand the old item and reopen hierarchy. Undo. | The old item cannot return edges from the previous compilation; a fresh hierarchy no longer claims Base as parent. Restoring the file restores the edge. `Object` is an interface, so `extends Object` is not a valid replacement fixture. |

### E. Edits, cancellation and capability boundaries

| # | Action | Expected result |
|---|--------|-----------------|
| X29 | In Editing.x, alternate rapidly between X15's String and Int arguments and request hints/completion. Finish with a valid call. Repeat while editing a module sibling. | The final answer and diagnostics match the latest text. Superseded queries do not resurrect old types, offsets or errors. |
| X30 | Start a completion/hint request, dismiss it and close the document; reopen it. Repeat around a language-server restart. | No response repopulates a closed document, no hanging UI, and the reopened file gives current answers. Dismissing a popup does not guarantee the client sends cancellation; protocol cancellation is also covered by the automated stdio tests. |
| X31 | Inspect compiler-mode capabilities, then format a module with an unindented body. | Declaration lookup, formatting, range formatting, code actions and code lenses are advertised; formatting produces edits. Document colors, monikers and inline values are not advertised. Native formatting is exercised further in X102/X107. |
| X32 | Try completion in `box.pair(unknown = \|);`, `box.pair(first = "x", first = \|);`, `box.pair(True, \|);`, `box.pair("x", "y", \|);`, and `missing(\|);`. | Unknown or duplicate labels, incompatible or excess arguments, and unresolved calls offer no argument values. Valid argument-value insertion, including positions before a later written argument, is covered by the positive completion scenarios. |

### F. Type-definition and implementation lookup

Save this as `Lookups.x`. Use **Go to Type Definition** (IntelliJ: **Go to Type Declaration**),
and **Go to Implementations / Go to Implementation(s)** from the editor action menu.

```xtc
module Lookups {
    interface Mapper<T> { T map(T value); }
    class TextMapper implements Mapper<String> {
        @Override String map(String value) = value;
        String map(Int value) = value.toString();
    }
    class Child extends TextMapper {
        @Override String map(String value) = value;
    }
    class Inherited extends TextMapper {}
    class Unrelated { String map(String value) = value; }
    TextMapper make() = new TextMapper();
    void run(Mapper<String> mapper, TextMapper|Unrelated value) {
        mapper.map("text");
        value.toString();
        if (value.is(TextMapper)) { value.toString(); }
        make();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X33 | Go to Type Definition on `mapper` in `mapper.map("text")`, then on `make` in its call and declaration. | The variable navigates to `Mapper`, not its `String` argument; the method navigates to its `TextMapper` return type. |
| X34 | Go to Type Definition on `value` before the `if`, then inside the narrowed branch. | Before narrowing, two targets: `TextMapper` and `Unrelated`. Inside, only `TextMapper`. A chooser/peek list instead of a direct jump is normal for multiple targets. |
| X35 | Go to Type Definition on `value` in `Mapper`'s method signature, then on the written `String` in TextMapper. | The formal value type leads to the declaration of `T`; the bundled `String` type opens its matching read-only declaration. No same-spelled local substitute is returned. |
| X36 | Find Implementations on `Mapper`. | `TextMapper`, `Child` and `Inherited`, once each. `Unrelated` is excluded despite its matching method shape. These are nominal declaration-level results, not a search for every structurally compatible class. |
| X37 | Find Implementations on the interface's `map`, then on the call `mapper.map("text")`. Repeat on Child's override. | Interface/call: the String bodies in TextMapper and Child. The Int overload and Unrelated's method are excluded; Inherited adds no duplicate body. Child's override resolves to its own body. |
| X38 | Return to the two-file Project fixture in section D. Go to Type Definition on the return-type `Child` after adding `Child make() = new Child();` to the root. Find Implementations on Base's `echo`; repeat after adding two blank lines before Child without saving, then after a broken member edit and correction. | Type-definition reaches the closed/member source at its current position. The inherited method points to the actual Base body once. A parse failure clears semantic targets until correction; old offsets are never reused. |

Implementation lookup requires a successful current module compilation. Concrete classes can be
their own implementation target. Interface default bodies and mixin bodies from composed hosts
can be source targets; an unused mixin is not an implementation of its `into` constraint.
A user-written override inside an anonymous class is also a method target, although the synthetic
class is not listed as a named type implementation.
Property/accessor implementation checks are in section J. Concrete delegation is covered in
section K; synthetic redirect targets and unknown runtime receivers remain outside lookup.

### G. Call hierarchy, semantic highlighting and inlay hints

Save this as `Consumers.x`. Enable semantic highlighting and inlay hints in the editor. For call
hierarchy use **Show Call Hierarchy** (VS Code) or the client's incoming/outgoing call view.
In IntelliJ, record an unavailable LSP action as not exercised. Semantic token colors depend on
the theme; VS Code's **Developer: Inspect Editor Tokens and Scopes** shows the actual token kind.

```xtc
module Consumers {
    static Int leaf(Int input, Int extra = 2) = input + extra;
    static String leaf(String text) = text;
    Int run(Int seed) {
        var number = leaf(1);
        val label = leaf("text");
        number += leaf(input = 2, extra = 3);
        function Int() fn = () -> leaf(seed);
        return number + label.size + fn();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X39 | Show incoming calls for the Int `leaf`, then outgoing calls for `run`. | Incoming groups two sites under `run` and one under `<lambda>`. Outgoing `run` lists the Int and String overload separately; the lambda's call is not attributed to `run`. |
| X40 | Expand the lambda's outgoing calls. Inspect the dynamic `fn()` call. | The lambda leads to Int `leaf`; `fn()` does not invent a statically selected edge. Call ranges navigate to the caller's source. |
| X41 | Inspect tokens for `leaf`, `seed`, `number` and the `number +=` target. Select `number` to highlight occurrences. | Method, parameter and variable kinds reflect resolved identities. Static/declaration/modification modifiers are present where applicable. Highlights distinguish the write and subsequent read. Theme colors may coincide. |
| X42 | Inspect inline hints in `run`; compare positional and named calls. Then use the shared `inferred` source with a destructured pair and `(value) -> value`. | `number: Int` and `label: String` inferred-type hints; `input:` and `text:` before positional values. The named call has no redundant hints and omitted default `extra` has none. Explicit declarations get no inferred-type hint. The original fixture also has an inferred `Int` lambda return (three type hints total); the additional shared source has two destructured types plus lambda parameter/return types (four total). |
| X43 | Keep hierarchy items open, insert a blank line before `run`, then reopen hierarchy. Break the module with an unfinished declaration, correct it and close/reopen the file. | Fresh results use current ranges. Old hierarchy items do not resolve against the edited snapshot. A parse failure clears semantic answers; correction restores them. |
| X44 | In the section D two-file fixture, add `static Int target(Int n) = n;` to Project and `Int callTarget() = target(1);` to Child, then close Child and show incoming calls on `target`. | `callTarget` and its call site point to the closed Child source. An unsaved member edit moves the result; stale positions are not reused. |

Also unavailable: separate Go to Declaration, document links and linked editing. The bounded
rename checks below exercise recompilation plus binding comparison. The interactive fixtures
do not configure dependency artifacts; source navigation through the host API is tested separately
below. Workspace-wide reference/implementation searches and external or conditional-mixin hierarchy
remain open. No Tree-sitter fallback runs in compiler mode. Check the
[capability matrix](plans/plan-ide-integration.md#adapter-capability-matrix) when those limits change.

Record the commit, editor/version, confirmed backend, case ID, source/unsaved edits, expected and
actual result, and relevant server-log lines for failures. Mark unsupported client actions and
unrun rows explicitly. The packaged compiler regression suite complements the interactive pass:

```bash
./gradlew :lang:lsp-server:compilerStdioTest --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

### Dependency host API checks

Binary artifact/source-index replacement has no editor setting or JSON-RPC endpoint. Source module
configuration is available separately in the following section. A Kotlin host can
export a successful `Compilation.toDependency()` and call
`XtcLanguageServer.replaceCompilerDependencies(listOf(dependency))`. Direct adapter hosts use
`replaceDependencies(...)` and reschedule the returned scope keys themselves. Binary-only artifacts
load with `XdkDependency.fromBinary(bytes)` and intentionally supply no source targets.

Run the host regression checks to verify the integration boundary without pretending an ordinary
editor launch configures it:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkDependencyTest' \
    --tests 'org.xvm.lsp.server.XdkLanguageServerTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

Expect dependency definition/type-definition and inherited generic method-body locations to point
to the exported sources. Replacing a library invalidates direct and transitive consumers, cancels
queued compilation/cursor work and preserves unrelated successful sessions. Server tests replace
an Int-returning library with a String-returning one: consumer diagnostics appear at the unchanged
document version, then clear when the original artifact is restored. Binary-only replacement removes
source links; compiling the dependency's current source uses that source rather than its old index.
The source-project checks below separately exercise builds from unsaved sources. Neither API
establishes a persistent workspace reference index.

### Automatic source recompilation host checks

For VS Code with the compiler build, open the folder containing Library.x and Consumer.x and add
this to workspace settings (`.vscode/settings.json`):

```json
{
  "xtc.compiler.sourceModules": [
    { "name": "Library", "uri": "Library.x" },
    { "name": "Consumer", "uri": "Consumer.x", "dependencies": ["Library"] }
  ]
}
```

No restart is needed. Relative URIs require one workspace folder; use absolute file URIs for
multi-root workspaces. Other clients can supply `initializationOptions.xtcCompiler` or the
`xtc.compiler` configuration section. In IntelliJ, use LSP4IJ's Ecstasy Language Server
**Configuration** JSON with nested `xtc.compiler.sourceModules`, as shown in the
[IntelliJ instructions](../intellij-plugin/README.md#compiler-source-module-configuration).
These are IDE-wide server settings; there is no dedicated XTC project graph UI.
The explicit setting overrides automatic workspace discovery. Use `null` to restore discovery
or `[]` to disable it. An embedding host can also register the graph directly:

```kotlin
server.replaceCompilerSourceModules(
    listOf(
        XdkSourceModule("Library", "file:///workspace/lsp-project/Library.x"),
        XdkSourceModule("Consumer", "file:///workspace/lsp-project/Consumer.x", setOf("Library")),
    ),
)
```

Create these two files on disk:

```xtc
// Library.x
module Library { static Int value() = 1; }
```

```xtc
// Consumer.x
module Consumer { package lib import Library; Int run() = lib.value(); }
```

| # | Action | Expected result |
|---|--------|-----------------|
| X45 | Open Consumer.x without opening Library.x. Navigate from `value`. | Both modules compile; Consumer has no errors and definition points into Library.x. No separate Gradle build is needed. |
| X46 | Open Library.x and change its method to `static String value() = "text";` without saving. | Consumer gains a type error without an edit/version change there. Disk still contains the Int version. |
| X47 | Replace Library's method with `MissingType broken;`, then restore the original method. | The original compiler error belongs to Library. Consumer reports `DEPENDENCY-FAILED` and has no stale navigation; correction clears both files. |
| X48 | Make the unsaved String change again, then discard and close Library.x. Reopen it. | Consumer recovers using the Int version on disk; reopening uses current text. Closing an overlay does not retain its unsaved artifact. |
| X49 | While Library is closed, delete its root on disk, then restore it; ensure watched-file notifications reach the server. | Library reports `SOURCE-UNAVAILABLE`; Consumer reports `DEPENDENCY-FAILED`. Restoring the file clears both without editing Consumer. |
| X50 | Open an unsaved Library/Extra.x with `class Extra { MissingType broken; }`, then discard/close it. Repeat with a saved member and disk deletion. | The member owns its compiler diagnostic. Consumer blocks, then recovers when the invalid member disappears; removed diagnostics clear. |
| X51 | Make rapid valid/invalid edits in Library while querying Consumer, then leave a valid Int method. Keep an unrelated module open. | Final diagnostics/navigation use the latest inputs; obsolete requests cannot restore older facts. The unrelated module remains available. |
| X52 | Add Bridge.x with `module Bridge { package lib import Library; static Int value() = lib.value(); }`; register Bridge depending on Library and change Consumer's edge/import to Bridge. Repeat X46–X47. | Changes propagate Library → Bridge → Consumer. A broken Bridge blocks Consumer; correction restores the chain. |

There is a 100 ms edit debounce; compiler cancellation remains cooperative. Source cycles and
overlapping roots are rejected during configuration. Blocked consumers currently expose no
semantic or structural views until their dependencies recover. Hosts must supply accurate edges;
the server does not infer a project graph from unresolved imports or build-tool files.

Automated adapter/server coverage of this setup, including uncooperative late compiler results,
cursor cancellation, snapshot timing and binary replacement:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkProjectTest' \
    --tests 'org.xvm.lsp.server.XdkProjectServerTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

### Configuration update checks

After X45–X52, set `xtc.compiler.sourceModules` to `[]`: Consumer should report its unavailable
Library dependency. Restore the list and expect diagnostics to clear without editing Consumer.
Introduce a cycle by adding Consumer as a dependency of Library: expect a configuration error and
the old working graph to remain active. Restore valid settings, repeat the same list, and verify
that existing semantic results remain available. These checks exercise the ordinary editor bridge;
the host-only binary-artifact section above still requires a Kotlin host.

### H. Compiler-validated rename

Use the compiler backend and an editor that advertises `workspace.workspaceEdit.documentChanges`.
If it does not, record this section as unsupported by that client; the compiler server deliberately
omits rename. Create `Rename.x`:

```xtc
module Rename {
    Int value = 10;
    private Int pick(Int input) = input;
    Int run() {
        Int local = pick(input = 1);
        function Int() captured = () -> local;
        return captured() + value;
    }
}
```

Use F2 (VS Code) or Shift+F6 (IntelliJ). Wait for diagnostics to clear before each case and undo
accepted edits before continuing. These are bounded semantic edits, not general member refactoring.

| # | Action | Expected result |
|---|--------|-----------------|
| X53 | Rename `local` to `renamed`. | Declaration and captured use change; `value` and unrelated names do not. Recompilation has no errors. |
| X54 | Rename `input` to `number`, first at its declaration, then after undo at `input = 1`. | Declaration, method body and named label all change; `=` and argument value remain intact. |
| X55 | Rename `local` to `value`. | No edits: the untouched property use would silently bind to the local even though compilation would succeed. |
| X56 | Without registering Rename.x in a source graph, try renaming `pick`, module `Rename`, property `value`, or a public method's parameter. | Rename unavailable. Public/lambda/constructor parameters, properties and module names remain unsupported. Ordinary instance methods require the explicit graph and checks in section I. |
| X57 | Start a rename and edit another file in the same module, close/reopen the target, or change its version before applying. | Pending work is canceled or rejected as changed; an edit for an old open-buffer version is not applied. Fast machines may need the controlled server regression below to exercise this race. |
| X58 | Introduce a syntax error, try rename, fix it and retry. Try an invalid identifier or an existing local name. | Broken/unsupported/conflicting requests give no edits or temporary diagnostics. A valid rename works again after correction. |

#### Additional L62 rename checks

Shared cases **X109–X117** now carry these sources, expected edits and file destinations for
both editor drivers. Each starts with other consumers closed and checks a single Undo against
every original source and path. Selected native validation passed (receipts below). For manual
exploration, use a scratch workspace with automatic source discovery. Save
`RenameLibrary.x` and `RenameConsumer.x`, then wait for clean diagnostics:

```xtc
module RenameLibrary {
    class Box {
        construct(Int input, Int count = 1) { total = input + count; }
        Int total;
        Int pick(Int input, Int count = 1) = input + count;
    }
}
```

```xtc
module RenameConsumer {
    package lib import RenameLibrary;
    lib.Box make() = new lib.Box(count = 2, input = 1);
    Int use(lib.Box box) = box.pick(count = 2, input = 1);
}
```

- Rename the `pick` parameter `input` to `value`. Its declaration, body and named caller change;
  the constructor parameter stays unchanged. Undo, close RenameConsumer.x, and repeat to cover closed callers.
- Rename `input` at the constructor's named call. Its constructor declaration/body change, while
  the `pick` parameter stays unchanged. Undo. Rename to the already-used `count`: no edit is offered.
- Change the module declaration/import to `RenameLibrary.example.org`. Rename the module's `RenameLibrary`
  token to `Renamed`: the domain and local alias `lib` stay unchanged, and the root file moves.
- In a separate `App.x`, use `module App { void accept(tools.deep.Box value) {} }` and create
  `App/tools/deep/Box.x` containing `class Box {}`. Rename `tools` to `helpers`: only the package
  directory moves, uses change, and definition reaches the moved Box.x. Undo restores the tree.
- For composition variants, use the small fixtures in `XdkResourceRenameTest`: rename `read`
  through an interface delegate, a mixin override (including `super()`), and an `@Lazy` property.
  All written contract names/uses change and the resulting module compiles. Binary contracts and
  method-value escapes remain refused.

Explicit source settings now use the native Rename action through `xtc/rename`, advertised as
`experimental.xtcRenameProposal = 1`. The response contains versioned edits plus the exact before
and after source graphs. It does not install either graph. Both clients reject a mismatched graph
or a stale settings snapshot before accepting the edit. Standard `textDocument/rename` continues
to refuse graph changes for clients without this persistence integration.
An explicit graph must contain every consumer you intend to update. An omitted source module
is outside the proof and will not be renamed; its mere presence beside a configured root does
not cause rejection. The new X119–X121 cases cover primary-constructor properties, lambda
parameters and escaped method values. Unsupported composition routes remain refused.
Project `xtc/rename` responses also describe this input boundary in `scope`; absolute registered
roots may be outside workspace folders. A scope receipt does not authorize applying stale edits.

- **VS Code:** keep the explicit graph in workspace settings (`.vscode/settings.json`, or the
  `settings` object of a saved `.code-workspace`). Rename edits only `xtc.compiler.sourceModules`
  in the same WorkspaceEdit as source text and moves. Comments and unrelated settings survive.
  Open settings documents feed the compiler immediately, including the interval after saving
  before VS Code refreshes its configuration cache. Native Rename follows
  `files.refactoring.autoSave` (default true); **Save All** also persists the settings and sources.
  Undo/Redo restore the graph along with the files. Global/folder-only configurations are refused.
  If Rename edits a file it also moves, disabling refactoring auto-save refuses the proposal
  before any edits: VS Code cannot reliably restore that unsaved resource history. Text-only
  renames and moves of unedited companions remain available. The harness checks both refusal
  and the enabled-policy transaction; it uses refactoring metadata like the native action.
- **IntelliJ:** open **Settings > Languages & Frameworks > Ecstasy Compiler**. Select automatic
  discovery, or disable it and add module names, root URIs/relative paths and comma-separated
  dependencies. **Apply** saves through LSP4IJ's project settings and refreshes the running server.
  The resource-root column accepts a JSON array such as `["assets/templates", "shared/resources"]`.
  Leave it blank for compiler layout deduction; `[]` explicitly disables resources. Relative paths
  use the project root. Custom build-defined paths must currently be entered explicitly; automatic
  Gradle model import remains planned. In VS Code, use the same `resourceRoots` array within
  `xtc.compiler.sourceModules`. Change/delete/recreate a referenced resource on disk and check
  dependency diagnostics refresh; a resource file is not an extra XTC source root.
  Blank fields and duplicate roots are refused. **Reset** discards pending edits. The legacy LSP4IJ
  Configuration JSON remains the fallback until a project override exists. Rename and native
  Undo/Redo update that same effective store; a changed graph or changed settings owner refuses
  stale history. Project settings do not change other projects' global configuration.


X118 checks persistence, root/edge replacement, Undo, Redo and second Undo in both drivers.
The VS Code driver also changes the real settings document during reply conversion and expects
a stale-settings refusal without source edits. Run with `-PcompilerPlaybookMultiRoot=true`
to open a saved two-folder `.code-workspace`; X118 additionally attempts a source-graph override
in the second folder and verifies VS Code rejects that unsupported scope, then exercises native Rename/Undo/Redo.
These subchecks pass in multi-root `run-ZOoaXw` (X118/CFG1–CFG3) and single-folder
`run-nFKwOD` (X118). Earlier X118 receipts do not cover them. Independent graph edits during
Undo/Redo remain separate native stress coverage; the new race is during Rename conversion.
All X109–X118 have passing selected receipts: IntelliJ `run-6245646041474423108`, VS Code
`run-3CeLiB` (X109–X117) and `run-7Xbo86` (X118 plus CFG1–CFG3 after the save-cache correction).
See the [validation record](../../docs/errs-integration-plan.md#shared-rename-and-native-settings-validation).

### I. Configured-graph references and method rename

Save these three files in one scratch folder. Register `Contracts` at `Contracts.x`, `Uses` at
`Uses.x` depending on `Contracts`, and `Dormant` at `Dormant.x` depending on `Contracts`, using
`xtc.compiler.sourceModules` as in section G. Start with only Contracts.x open.

```xtc
module Contracts {
    interface Mapper<T> { T map(T value); }
    class Base {
        Int pick(Int value) = value;
        Int choose(Object value) = 0;
    }
}
```

```xtc
module Uses {
    package api import Contracts;
    class Mapper implements api.Mapper<String> {
        @Override String map(String value) = value;
    }
    String run(api.Mapper<String> contract, Mapper impl) = contract.map("a") + impl.map("b");
    Int choose(api.Base box) = box.choose(1);
    conditional Int library(String text) = text.indexOf('a');
    class Named { @Override String toString() = "name"; }
}
```

```xtc
module Dormant {
    package api import Contracts;
    String run(api.Mapper<String> mapper) = mapper.map("c");
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X59 | Find References on the interface's `map` in Contracts.x, with Uses.x and Dormant.x unopened. | The declaration and the two calls through `api.Mapper<String>` appear. The concrete override and `impl.map` have their own identity and are excluded from this exact reference query. |
| X60 | Rename that `map` to `convert`; inspect the preview, apply, then undo. | Five edits across all three files: the contract, concrete override, and all three calls. No diagnostics after recompilation. Closed files have null edit versions; open buffers carry their current versions. |
| X61 | Rename Contracts.Base's `pick` to `choose`. | No edit. The unchanged `box.choose(1)` would select a different overload even though the edited graph compiles. |
| X62 | Open Uses.x, invoke signature help inside `indexOf('a')`, then try renaming `indexOf`. Separately try renaming Named's `toString` override. | The bundled XDK supplies the Char overload's signature; binary `String.indexOf` cannot be renamed. The source override also cannot be renamed because its contract belongs to the bundled XDK. These are resolved binary targets, not missing dependencies. |
| X63 | Add a second `mapper.map("d")` call to Dormant's return expression without saving, query references/rename, then temporarily replace its body with `MissingType broken;`. Restore the fixture. | Queries include the unsaved call and rename uses its current buffer version. An incomplete configured graph gives no reference list or rename edit; proof compilations add no diagnostics of their own. Restoring it restores results. |

Each query captures and compiles the entire explicit graph, including closed members and transitive
consumers. References use exact compiler identities, including source uses of bundled binary members;
method rename follows ordinary instance-method override families and checks all written bindings,
selected calls and dispatch chains before returning edits. Binary ancestors,
mixin/delegating/capped chains and unknown bindings fail closed. Ordinary `super(...)` calls retain
their selected source body through the attempt's call-binding collector. Function-valued calls
expose validated signature types separately, without a selected method target. Instance-property
families/accessors, constructors and public-parameter renames remain outside this proof. The current
batch additionally proves inline-type and static-member renames over the same graph.
Preparing a method rename identifies a candidate; the final graph proof can still reject it.
An explicit configuration limits the graph to its listed modules. Automatic discovery instead scans
workspace folders; neither mode proves closure over external clients of an exported API. Keep every
source consumer in the configured/discovered graph.

Controlled regressions in `XdkProjectQueryLifecycleTest` and `XdkCursorServerTest` cover changes in
another module, close/reopen, configuration/dependency replacement, canceled/superseded queries and
late disk changes before returning results. These races are hard to trigger reliably by hand.

For closed-member input checks, version conversion, cancellation and the repeated retention workload:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkRenameTest' \
    --tests 'org.xvm.lsp.adapter.XdkProjectQueryTest' \
    --tests 'org.xvm.lsp.adapter.XdkProjectQueryLifecycleTest' \
    --tests 'org.xvm.lsp.server.XdkRenameServerTest' \
    --tests 'org.xvm.lsp.server.XdkCursorServerTest' \
    --tests 'org.xvm.lsp.adapter.XdkRetentionTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

The retention test repeatedly replaces dependencies, rebuilds an explicit source graph, performs
rename/cursor proofs and closes/reopens the consumer. It reports actual latency and checks release
of compiler results, AST roots and pools after close and shutdown. This is a bounded automated
workload; record interactive and prolonged editor tests separately.

### J. Property and accessor implementations

Save this as `Properties.x`. Use **Go to Implementations** on the indicated property or accessor
name. A property query lists its effective getter/setter bodies or backing-field declarations;
an accessor query follows only that accessor. The query is declaration-level: a read of a property
has the same implementation set as its declaration, rather than selecting only its getter.

```xtc
module Properties {
    interface Named<T> { T name; }
    class Stored implements Named<String> { @Override String name = "stored"; }
    class Computed implements Named<String> { @Override String name.get() = "computed"; }
    class Inherited extends Stored {}
    class Unrelated { String name = "other"; }
    String read(Named<String> value) = value.name;

    class Base {
        Int value {
            Int get() = 1;
            void set(Int value) {}
        }
    }
    class Child extends Base { @Override Int value.get() = 2; }
    interface Defaulted { @RO String label { @Override String get() = "default"; } }
    class DefaultUser implements Defaulted {}
    class FieldUser implements Defaulted { @Override String label = "field"; }

    interface Missing { @RO String absent; }
    class Forward(Missing target) delegates Missing(target) {}
    class Delayed { @Lazy Int later.calc() = 1; }
    Int size(String text) = text.size;
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X64 | Find Implementations on `name` in `Named`, then in `value.name`. | Stored's `name` field and Computed's `get` body. Inherited adds no duplicate; Unrelated is excluded. |
| X65 | Query Base's `value`, then its `get` and `set` separately. Query Defaulted's `label`. | Property: Base's getter/setter and Child's getter. Getter: the two getter bodies only. Setter: Base's setter only. Defaulted: its default getter and FieldUser's field. |
| X66 | Query Missing's `absent`, Delayed's `later`, and `size` in `text.size`. | No invented target for an abstract/interface-delegated property, binary-only `@Lazy` getter/native storage or bundled binary source. |
| X67 | Create `Properties/Member.x` containing `class Member implements Named<String> { @Override String name.get() = "member"; }`. Close its tab and query Named's `name`. Open the member, insert two blank lines without saving, break its declaration, then restore it. | The closed member adds its getter. Unsaved positions move by two lines; parse failure clears implementation results; correction restores them. |

Lookup requires successful module analysis and uses copied source identities. It does not enable
property rename, infer a runtime delegate receiver or search every configured module for additional
implementations. Source Ref/Var annotation accessors are covered by X72 below; binary-only
accessors such as `@Lazy.get` still require an explicit host source index to navigate.

### K. Delegation, function calls and broader cursor contexts

Create Advanced.x:

```xtc
module Advanced {
    interface Api<T> { T map(T value); @RO T name; }
    class Engine implements Api<String> {
        @Override String map(String value) = value;
        @Override String name.get() = "engine";
    }
    class Forward(Engine target) delegates Api<String>(target) {}
    class Outer(Forward target) delegates Api<String>(target) {}
    String read(Outer forward) = forward.map(forward.name);
    class Base<T> { T pick(T value) = value; }
    class Child extends Base<String> { @Override String pick(String value) = super(value); }
    Int apply(function Int(Int) fn) = fn(42);
    Int pair(Int first, Int second) = first;
    Int edit(String word, Boolean flag) { return 1 + word.size; }
    annotation Trace<Referent> into Var<Referent> {
        @Override Referent get() = super();
        @Override void set(Referent value) { super(value); }
    }
    class Measured {
        @Trace Int count = 1;
        @Trace Int computed {
            @Override Int get() = 3;
            @Override void set(Int value) {}
        }
    }
    Int readMeasured(Measured meter) = meter.count + meter.computed;
    Int applyPair(function Int(Int, String) fn) = fn(1, "x");
    class Packet<T> { construct(T first, T second) {} }
    Packet<String> buildPacket() = new Packet<String>("a", "b");
    annotation Marked into Object {}
    class Envelope<T> { class Part { construct(T first, T second) {} } }
    void constructions(Envelope<String> outer, Packet<String> packet, String text, Int textNumber) {
        outer.new Part("a", text);
        packet.new("a", text);
        new @Marked Packet<String>("a", text);
        Packet<String> inferred = new Packet("a", text);
        new Packet("a", text);
        new String[2](text);
    }
    Object literal(String word) = [word.size];
    interface CursorReader { String read(); }
    void anonymousConstructions(String text, Int textNumber) {
        new Packet<String>("anonymous", text) { String read() = text; };
        new CursorReader("a", text) {
            construct(String first, String second) {}
            @Override String read() = text;
        };
    }
    void take(Int first, String second) {}
    void values(Int number, String text, Boolean flag, function Int(Int, String) fn) {
        Int textNumber = 1;
        take(1, text);
        fn(1, text);
        new Packet<String>("a", text);
    }
    void choose(Int value) {}
    void choose(String value) {}
    void alternatives(Int number, String text, Boolean flag) { choose(text); }
    void prefixes(Int valueNumber, String valueText, Boolean valueFlag) { choose(valueText); }
    class ArgumentValues {
        private String valueText = "text";
        static String valueConstant = "constant";
        Int valueNumber = 1;
        static void takeValue(String value) {}
        void inspectValues() { takeValue(valueText); }
        static void inspectConstants() { takeValue(valueConstant); }
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X68 | Find Implementations on `forward.map` and `forward.name`. | Engine's written `map` and `get` bodies, reached through both delegation layers. No generated forwarding target. Interface-typed and cyclic delegates remain unresolved. |
| X69 | Go to Definition on `super(value)`. Try Prepare Rename there. | Base's `pick` body; the `super` keyword cannot be renamed. Configured-graph method-family rename preserves this call target without editing the keyword. |
| X70 | Request signature help inside `fn(42)` and Go to Definition on `fn`. | `Int fn(Int)` with no guessed parameter name; definition reaches the function parameter, without claiming its runtime callable target. |
| X71 | In `edit`, replace `1 + word.size` with `1 + word.`, `flag ? word. : 0`, `flag ? 0 : word.`, and `pair(word., 2)` in turn. Complete after the dot, then restore the original. | `size` is offered in the original lexical context, including when another argument follows the cursor. No stale result or fabricated value for the incomplete expression. |
| X72 | Find Implementations on `meter.count` and `meter.computed`. | `count` reaches Trace's written `get` and `set`; `computed` reaches its explicit `get` and `set`, which precede the annotation. Property queries return the combined accessor set, not a read/write-specific target. Native annotation storage has no invented source body. |
| X73 | In `applyPair`, replace the call with `fn()` and then `fn(1, )`; request signature help after `(` and after the comma. Try `fn(True, )`, then restore the original. | `Int fn(Int, String)` with the corresponding parameter highlighted, no invented names/defaults/runtime target. The incompatible Boolean argument produces no candidate. Normal diagnostics clear after restoring the complete call. |
| X74 | In `buildPacket`, replace the construction with `new Packet<String>()`, then `new Packet<String>("a", )`, then `new Packet<String>(second = "b", first = )`; request help at each missing argument, then restore. | `new Packet(String first, String second)`; active parameter is respectively first, second, first. Written arguments fit a candidate, but an unfinished call does not select a constructor. Specialized constructions are exercised separately in X83–X85. |
| X75 | In `edit`, replace `1 + word.size` with `(word.si`, `pair((word.si`, and `word[word.si` in turn, leaving the semicolon and method/module braces. Trigger completion after `si`, then restore. | `size` is offered despite missing `)`/`]`. The edit replaces only `si`; Problems continues showing the normal compiler errors until the source is repaired. No missing operand or value is invented. |
| X76 | In `edit`, replace `1 + word.size` with `pair((pair(1, `, leaving the semicolon and braces. Request signature help after the comma, then restore. | The innermost `Int pair(Int first, Int second)` candidate highlights `second` despite the missing call/group delimiters. Problems clears after restoring the original source. Both drivers also reject overlapping semantic-token ranges before and during recovery, including anonymous construction type names. |
| X77 | In `values`, remove `text` from each of the three calls in turn and invoke completion at the empty slot. Also try `take(first = 1, second = )` and `new Packet<String>(second = "b", first = )`. Accept `text`, then restore before the next edit. | The compiler offers compatible `text`, the bundled module properties `simpleName` and `qualifiedName`, and the empty-string literal `""`, with an insertion at the cursor. `number`, `flag` and `fn` are excluded; VS Code may also show its independent snippets. The completed source compiles and Problems clears. Ordinary methods, function values and explicitly parameterized constructors use compiler argument fitting. |
| X78 | In `alternatives`, remove `text` from `choose(text)` and complete inside `choose()`. Request signature help before accepting `number`. Restore and repeat with `text`. | Both `number` and `text` are offered, along with compatible module properties `simpleName` and `qualifiedName` and literals `0` and `""`; `flag` is excluded. Signature help retains both overload candidates until a value is inserted. Either accepted completion compiles and clears Problems. |
| X79 | In `values`, shorten `text` to `te` in each of the three calls and invoke completion at its end. Also try `take(first = 1, second = te)` and `new Packet<String>(second = "b", first = te)`. Accept `text`, restoring between edits. | Only compatible `text` is offered; same-prefix `Int textNumber` is excluded. The edit replaces exactly `te`, preserving the label, commas and closing delimiter. The completed source compiles and Problems clears. |
| X80 | In `prefixes`, shorten `choose(valueText)` to `choose(va)` and invoke completion after `va`. Request signature help, then accept `valueNumber`; restore and repeat accepting `valueText`. | Both compatible names are offered; `valueFlag` is excluded. Both overload signatures remain until acceptance. Exactly `va` is replaced, the completed call compiles and Problems clears. |
| X81 | In `ArgumentValues.inspectValues`, shorten `takeValue(valueText)` to `takeValue(va)`, then try `takeValue(value = va)`. Complete after `va`, accept `valueText`, and restore between edits. | The compatible property `valueText` and constant `valueConstant` are offered with String types; `valueNumber` is excluded. Exactly `va` is replaced, preserving the label and delimiter. Acceptance clears Problems. |
| X82 | In `ArgumentValues.inspectConstants`, shorten `takeValue(valueConstant)` to `takeValue(va)` and invoke completion. Accept `valueConstant`. | Only the compatible constant is offered; instance properties require an instance receiver. The completed call restores the original source and clears Problems. |
| X83 | In `constructions`, shorten the final `text` to `te` in `outer.new Part`, `packet.new`, and `new @Marked Packet<String>` in turn. Request completion and signature help; accept `text`, then restore. | String constructor parameters and active parameter `second`; only `text` fits the prefix, not `textNumber`. Acceptance replaces exactly `te` and clears Problems. |
| X84 | Shorten `text` in the two constructions with omitted `<String>`. Request completion and signature help, then accept `text`. | The declaration's `Packet<String>` expected type constrains completion to `text`. The standalone construction also permits `textNumber`: its provisional String signature can change as more arguments determine the omitted class type. Neither unfinished call selects an overload. |
| X85 | Replace `new String[2](text)` with `new String[2](te)`, then `new String[2](supply = te)`. Request help and accept `text`. | Parameter `supply` is active (index 1), since `[2]` supplies `size`. Only the compatible String value is offered. Acceptance clears Problems. |
| X86 | In `literal`, replace `[word.size]` with `(1, word.si`, `Tuple<Int, Int>:(1, word.si`, `[word.si`, and `["key"=word.si` in turn, leaving the semicolon. Complete `size`, then restore. | Member completion survives missing tuple/list/map closers and replaces only `si`. Accepting the member alone leaves delimiter diagnostics; restoring the complete expression clears Problems. |
| X87 | Before the module's final brace, add `Int declaredSize(String word) = word.si`, `Int declaredSize = "x".si`, or `void defaults(Int size = Int64.Ma {}` in turn. Complete at the prefix, then repair the missing `;` or `)`, and remove the added declaration. | `size`/`MaxValue` is offered in the declaration's real compiler context. The missing terminator remains a normal diagnostic until repaired. Method defaults must still be constants. Shorthand constructor defaults have backend coverage. |
| X88 | In `anonymousConstructions`, shorten `text` to `te` in `new Packet<String>("anonymous", text)`. Request signature help and completion, then accept `text`. | The inherited constructor has String parameters and active parameter `second`. Only `text` fits; acceptance clears Problems. The anonymous method still captures its enclosing `text`. |
| X89 | Shorten `text` in `new CursorReader("a", text)` to `te`. Request help/completion, accept `text`, then repeat after deleting the constructor call's `)` before `{`. Restore the fixture. | The constructor declared inside the anonymous body supplies `new CursorReader(String first, String second)`, without a generated class suffix. Completion replaces only `te`. Missing-`)` diagnostics remain until the delimiter is restored. |
| X90 | In `constructions`, replace `new String[2](text)` with `new String[te](text)`. Request completion and signature help inside the brackets, then accept `textNumber`. Repeat with `new String[te` before the semicolon, then restore `](text)` and the fixture. | Only the Int value `textNumber` fits the size prefix; the String value `text` is excluded. Help shows the fixed-size Array constructor with `Int size` active at index 0. Acceptance replaces only `te`. A missing bracket remains a diagnostic until repaired. IntelliJ verifies the full candidate set, visible size highlight and exact accepted edit; the disposable test profile disables sole-candidate auto-insertion. |
| X91 | In `Editing.x`, temporarily use `module Editing { String StringValue = "x"; void damaged(Str value) {} Int later = 1; }`. Request completion just after `Str`, then accept `String`. Repeat with `void damaged(Int first, Str second) {}`, `Str property;`, and `Str damaged() = "x";` in the same module, then restore the fixture. | Compiler type candidates include `String`, exclude the value `StringValue`, and replace only the three prefix characters. No call signature is shown in the declaration header. Each repaired source clears diagnostics. Both native drivers use these same inputs and assertions. |
| X92 | In `Editing.x`, use a multiline `module Editing` with `void damaged(Int) {` on line 2, `Int hidden = 1;` on line 3, its closing brace on line 4, and `Int later = 1;` afterward. Repeat with the header `void damaged(Int value, Str)`. Repair either header to `void damaged(Int value)` and restore the fixture. | Diagnostics remain while the parameter header is incomplete. Outline includes `Editing`, `damaged` and `later`; `hidden` does not leak from the skipped body. The method folds through its actual closing brace. Repair clears diagnostics. IntelliJ verifies native diagnostics, Problems rows, Structure inclusions/exclusions and the exact fold at zero-based lines 1–3 for both variants. |
| X93 | In the same temporary `Editing.x` module, complete `ecstasy.text.Str` as a parameter type, property type and return type (return `new StringBuffer()`). Accept `StringBuffer`. Then use the X93 source below (also in the shared catalog): `Owner` extends `Base` and declares public/private/protected nested types, a typedef and a value; `Alias` imports `Owner`. Complete `Owner.Ite` and `Alias.Ite`, accepting `ItemPublic` and `ItemAlias`. Restore the fixture. | The bundled XDK qualifier resolves; only `Str` or `Ite` is replaced. Owner/alias candidates include inherited `ItemBase`, public `ItemPublic` and typedef `ItemAlias`, and exclude `ItemPrivate`, `ItemProtected`, `ItemValue` and enclosing `StringValue`. No call signature appears. Every accepted edit compiles and clears diagnostics. Both native drivers use the shared variants and assertions. |
| X94 | In temporary `Editing.x`, complete `Str` in `List<Str>`, `Map<Int, List<Str>>`, `Map<Str, Int>`, `List<(Int \| Str)>`, `Object + Str` and `Object - Str` parameter headers. Also try `List<Str>` property/return types and `List<ecstasy.text.Str>`. Use the shared X94 variants. Finally remove both `>` from the nested `Map` header, accept `String`, then restore `>>`. | Only the selected leaf token changes; generic arguments, compound operators and qualifiers stay intact. Values such as `StringValue` are excluded and no call signature appears. Complete accepted headers clear Problems. Missing `>` still reports an error after acceptance; adding the closers clears it. Type suggestions establish visibility, not generic-constraint compatibility. Both drivers consume the same nine variants. |
| X95 | In temporary `Editing.x`, use the shared X95 replacement program. Complete the marked type in class `extends`, interface `extends`, `implements`, `delegates`, `incorporates` and mixin `into` headers. Also try `Owner.Nes`, `List<Str>` and the missing-`>` variant. Accept the selected entry, then restore the repaired declaration. | Completion replaces only the final token and offers visible types rather than values. Complete accepted headers clear Problems; accepting `String` with a missing `>` leaves an error until the closer is restored. No signature appears in a type slot. Both drivers consume the same nine variants; native IntelliJ assertions pass. |
| X96 | In temporary `Editing.x`, use the shared X96 replacement program. Complete registered `Element` in a type prefix and empty generic slot, `String` before a later generic argument, and `Item`/`Alias` after `Owner<String>`. Put the cursor inside `String`, `StringBuffer` and `Item`, then accept the selected entry. | All eight variants preserve surrounding syntax and clear Problems after acceptance. Empty slots insert at the cursor; mid-token edits replace the whole identifier without duplicating its suffix. Parameterized aliases retain their substituted compiler type. No signature appears. Both editor drivers consume the same data; native IntelliJ assertions pass. |
| X97 | In temporary `Editing.x`, run the shared argument-context variants: later/named arguments, groups, qualified properties, function values, construction, operators and missing operands. Accept the specified variable or literal in each row. | Fitting rejects incompatible values, preserves surrounding syntax and exact edits, retains signature help and clears diagnostics after acceptance. Literal rows verify Value kind and sort metadata; method rows verify documentation and active parameters. The 30 shared variants include character, decimal, binary, array, map and tuple insertions; see the latest L64 receipt for validation. |
| X98 | Complete `Li|st<String>` and `Li|<String>`, including a nested `Map` argument. | Only the base identifier is replaced; `<String>` and nested delimiters survive. The accepted source compiles. VS Code passes in `run-KoAP6K`; the IntelliJ consumer compiles and awaits its native checkpoint. |
| X99 | In an isolated discovered workspace, create `LiveLibrary.x` with `module LiveLibrary { static Int value() = 1; }` and `LiveConsumer.x` with `module LiveConsumer {}`. Add `package lib import LiveLibrary; Int run() = lib.value();` to the consumer without saving. Change the library result to `String`, then discard it. | Definition reaches `value`; consumer Problems updates for the incompatible unsaved dependency and clears on discard. Neither buffer is saved by the server. |
| X100 | Create a healthy module with `class Base {}` and `class Child extends Base {}`, plus an independent module. Break the neighbor with `Missing broken;`, then request Base's subtypes. | Child remains navigable. Exact references return no complete answer while the graph is broken. Repairing the neighbor restores full graph queries. |
| X101 | Open `module LibrarySource { package xml import xml.xtclang.org; void accept(xml.Document document, String text) {} }`. Go to Definition on `Document` and `String`, and Type Definition on `text`. | Matching XDK source opens at the declaration token, read-only. Library symbols cannot be renamed; the VS Code formatting provider returns no edits. IntelliJ Reformat opens **Clear Read-Only Status**: cancel it, then verify the buffer/disk bytes and read-only status are unchanged. The native Rename action displays the server’s rejection. Missing/ambiguous source metadata gives no guessed target. |
| X102 | In a source graph, declare Base's `Int value` getter and Child's `@Override Int value` getter; use both properties. Rename `value` to `amount`. | Both declarations and uses change. `get`, `set` and setter parameters keep their names. Binary contracts and annotation/delegation families remain unavailable. |
| X103 | Create `MoveType.x` using `Item` and `Item.Nested`, `MoveType/Item.x` declaring `Item`, and `MoveType/Item/Nested.x` declaring `Nested`. Rename `Item` to `Renamed`, then undo. | Source edits, `Item.x` → `Renamed.x` and the companion directory move apply together. Both directions compile and preserve the nested source. Existing destination files/directories block the move. Simple discovery-managed module roots can also be renamed; explicitly configured module roots remain refused. |
| X104 | Import `ecstasy.text.StringBuffer as Buffer`; use `Buffer` in a type and construction. Rename the alias to `Builder`. | Three alias tokens change; `StringBuffer` and nested aliases remain unchanged. |
| X105 | Use unresolved `Document` in a type header; apply the import quick fix for `xml.xtclang.org`. Then try unresolved `Widget` declared in a separate discovered source module. | Only imports whose complete graph compiles are offered; applying clears diagnostics. Source imports also add the discovered dependency edge. Explicit graphs do not silently gain dependencies. |
| X106 | In Editing.x, complete `Str` inside `function Str(Int)`, `function void(Str)`, `function (Int, Str)(Int)` and `Function<<Str>, <Int>>` parameter types. Repeat with a missing function/sequence closer. | Only the written leaf token is replaced. Complete forms compile; missing delimiters remain diagnostics until repaired. No callable signature is invented for a declaration header. The same eight variants are implemented in both drivers. |
| X107 | Complete a trailing `ecstasy.text.` type, edit `te` inside `ecstasy.text.StringBuffer`, and fill empty union/intersection/difference operands. | Insert only into an empty slot or replace only the selected qualifier token; preserve the remaining suffix and compile the accepted source. |
| X108 | Complete type names in multiple-return lists, generic method/class headers, package compositions and written `Element`/`Other` formals, including `Chain<Element>` recursion. | Preserve exact ranges and restore compilation. Recursive constraints are explicitly labelled written syntax with no invented formal identity. The 17 shared variants include compound/nullable/immutable recursion and a cursor inside its own bound; see the latest L64 receipt for validation. |
| X109 | In the shared `X109` fixture, public parameter rename updates a closed caller and preserves another overload. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X110 | In the shared `X110` fixture, override parameter slots join differently named declarations and callers. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X111 | In the shared `X111` fixture, constructor parameter rename starts at a named argument. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X112 | In the shared `X112` fixture, delegation rename joins the contract and concrete receiver. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X113 | In the shared `X113` fixture, mixin method rename preserves contextual super. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X114 | In the shared `X114` fixture, mixin property rename updates declarations and access. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X115 | In the shared `X115` fixture, annotated property rename preserves its Lazy implementation. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X116 | In the shared `X116` fixture, qualified module rename preserves its domain and alias and moves its companion. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X117 | In the shared `X117` fixture, implicit package rename moves the nested directory. Keep every other fixture file closed before Rename. | Compare all files with the shared expected contents; preserve unrelated overloads/aliases; verify moved paths, then Undo once and verify every original path and source. |
| X118 | Repeat X116 with explicit sourceModules settings containing Library.example.org and Consumer → Library.example.org. | Rename changes the root URI, module name and dependency edge in settings; Undo and Redo restore the matching graph, source contents and resource paths together. Save All in VS Code persists the settings document with the sources. IntelliJ uses the Ecstasy Compiler project settings page (Reset/Apply) and verifies the same project graph follows native history. |
| X119 | Rename the primary constructor property input in Library.Box with Consumer closed. | Header, constructor label and property access change together; Other.input stays unchanged. Undo restores every source. |
| X120 | Rename a lambda input captured by a nested lambda. | Declaration and nested capture change; sibling lambda input stays unchanged. Undo restores the source. |
| X121 | Rename a method input while a closed consumer stores its method value and also calls it by name. | Direct label and selected declaration change; positional function calls and Other.pick stay unchanged. Undo restores both files. |
| X122 | At Box in each shared Actions variant, apply Implement or Override, then Undo/Redo/Undo. Cover ordinary signatures, a missing implementation diagnosed at construction, a conditional multiple return with a parameterized type/default, a generic method, an existing named call through a descendant, atomic implementation of all required members, a bundled Iterator contract, a compound return and fresh literal-default repair. Finish with an already implemented contract. | Both editors discover the action, compare the complete generated source and compile it. Native history restores exact source; the construction diagnostic returns after Undo and clears after Redo. No duplicate implementation is offered. |

| X123 | Replace Navigation.x with the shared broken source, observe Problems, pull diagnostics twice with the returned result ID, repair the source, and pull using the old ID. Restore the fixture. | Both editors display and clear the compiler error. The installed connection returns full, unchanged, then empty full reports with a new result ID. |
| X124 | Configure a nonexistent nested external resource directory for Assets.x; test Reset/Apply, empty roots, create/delete/recreate data.txt, then replace the configured root with another missing directory and create it. Keep the editor focused; do not open or manually refresh the external directory. | Missing-resource diagnostics clear and return through automatic native watchers; settings preserve the selected roots. Old receipts with manual VFS refresh do not count for this version. |
| X125 | Hover a generic echo call; request signature help before an existing positional/named argument; complete direct and chained `.tr()`; navigate the narrowed JsonObject variable's type. | Hover identifies echo, active parameters are 1/0, completion offers trim, and type definition opens bundled Map.x. |
| X126 | Request full tokens, insert a leading newline, request a delta and a range when negotiated, then close/reopen and request using the old ID. | Applying edits reconstructs the full result; empty ranges are empty; retired IDs return full data. Unsupported client operations are refused explicitly. |
| X127 | Request an unused-import action, resolve its edit when negotiated, edit the source, then try resolving the old handle. | Initial lazy actions omit edits; resolve returns versioned edits without changing the title; obsolete handles are refused. Eager-only clients retain complete actions. |
| X128 | Rename Box.x to Crate.x and an implicit tools package folder to util using the IntelliJ project-tree Rename action (Shift+F6), or the VS Code file Rename action. | Compiler-proven declaration/reference edits and the move apply together; root and member diagnostics remain clear. IntelliJ invokes the registered handler and actual Rename dialog. Raw VFS moves occur too early for LSP4IJ's before-listener to obtain proof and are not equivalent coverage. |
| X129 | Import an evaluated Gradle model with processed resources; refresh to empty roots and back; try a malformed report; enable an explicit empty-resource override and refresh again; reset to the build model. | Diagnostics follow the imported paths, malformed reports retain the last valid import, refresh preserves explicit settings, and resetting restores the model. Both drivers exercise the installed client configuration path. |

| X130 | Select the two shared module containers, Move them into the destination directory, then Undo and Redo once. | Both modules, member files and embedded resources follow the move; the unchanged consumer still resolves its imports. IntelliJ uses the Community Move handler/dialog; VS Code uses Explorer Cut/Paste and native Undo/Redo once each. Its host repaint exception still fails the case even when the move assertions succeed. |

| X131 | Request code lenses, document links, inlay hints and workspace symbols; resolve deferred properties when negotiated; edit the source and retry old handles. | Stable positions/labels, preserved command arguments, complete resolved payloads and explicit stale-handle refusal. Both drivers use their installed client connection; existing UI cases cover presentation/navigation. |
| X132 | Request formatting for overlapping and disjoint line ranges, then negotiated pre-save hooks with default options. | Formatting edits are sorted and deduplicated; default save hooks return no edits. Both clients retain the unchanged buffer. Incremental UTF-16/CRLF patches and opt-in save edits have backend regression coverage. |
| X133 | Use two methods with same-spelled locals. Request linked editing on one local, on a type and on a parameter; introduce a source error, then repair it. | Exactly that local declaration/use pair is linked; unrelated bindings, nonlocal symbols and invalid-source results stay unlinked. Both drivers assert the installed protocol; native linked-typing presentation remains separate. |
| X134 | Configure an unopened Library.x outside the workspace and a local consumer. Change its return type externally, restore it, delete it and recreate it. | Consumer Problems appears/clears from OS file events without opening Library or manually refreshing VFS in either host. |
| X135 | Use the server-log actions to open/hide the docked log twice, then return to the source. | The log view opens and hides without modifying source. Physical shortcut dispatch, remapped shortcuts and alternate docking are manual checks. |
| X136 | Open the language-service settings, change inlays, Reset/Cancel, then Apply; inspect effective configuration. | Live presentation changes preserve source and compiler graph ownership; report includes PID, adapter and queue names/count. IntelliJ drives the actual settings components; VS Code uses native configuration and the contributed report command. |
| X137 | Leave a buffer unsaved; select Incremental text synchronization, then Full. | Each applied transport change restarts the existing connection and restores current unsaved text. This is text transport, not incremental compilation. |
| X138 | Change indentation to 2, format, then supply an invalid indentation value. | Valid change applies without restarting; invalid settings retain the previous formatter. Effective status identifies native save-hook availability. |
| X139 | Enable native format-on-save and save an unindented source. In IntelliJ also leave a second dirty document closed, then Save All. In VS Code also select server save formatting with native formatting disabled. | Open and closed IntelliJ documents are indented without formatting-error notifications or a file-cache conflict. VS Code suppresses the server hook when native formatting is enabled; IntelliJ uses native Actions on Save because LSP4IJ has no server save-edit hook. |
| X140 | Open the shared source with an emoji before an `Int` reference on the same line. Hover and prepare rename on the final `value`. | UTF-16 is explicit; hover resolves `Int` and rename selects exactly `value`, without shifting after the surrogate pair. |
| X141 | Switch runtime server trace between `messages` and `verbose`, then request hover. Restore the editor trace setting. | Both log the method and timing; only verbose adds correlation metadata. No source buffer or request payload appears in server trace notifications. |
| X142 | Open configured `FoldedInitializer.x`; hover and navigate from `Int copy = value`, find references, rename `value` to `number`, then Undo. | The declaration, folded initializer and method body share one semantic identity; all three rename together, compile cleanly and restore on Undo. |
| X143 | Configure `PartialSymbols.x` with 130 classes. Compare ordinary workspace symbols with a request carrying a partial-result token. | Ordered progress batches contain at most 64 symbols each; their combined names match the ordinary result exactly and the final response is empty. |
| X144 | Apply a current versioned text edit through the installed client, Undo it, then send a two-document edit with one stale version. | Current edit and native Undo succeed. A stale target refuses the whole batch and preserves both current buffers. Drivers invoke the installed application handler; packaged tests separately exercise server-to-client transport. |
| X145 | Replace ProgressWork with the shared workload and request references. Verify the progress source/activity text, cancel, then check hover; repeat and restart while pending. | Cancellation terminates only the request; progress disappears. Restart preserves unsaved text, retires the pending reader and exits the old PID. Manual VS Code Cancel-control checks use 20,000 methods; automatic IntelliJ Cancel, the ordinary VS Code callback and restart use 5,000. A request finishing too early fails as unexercised. Historical model-cancellation receipts and current visible-control acceptance are distinguished in the L81 section below. |
| X146 | Open RefreshConsumer with inferred `var value = lib.make()`. Change only RefreshLibrary from returning Int to String, then restore it. | Both clients receive provider refreshes and show the changed inferred-type hint. Consumer text/version stays unchanged. IntelliJ reads the native cached inlay result; VS Code observes the registered provider's refresh events and result. |
| X147 | Hold an older settings report while requesting a newer one, changing settings, restarting or closing the settings page. Complete the old reply last. | Older reports cannot overwrite current or disposed UI state. VS Code delays a real server reply; IntelliJ drives the real settings component with controlled asynchronous report data and an EDT completion barrier. Restart retires the old PID and preserves source. |
| X148 | Open the shared Extract.x fixture, select exactly `42` in the return statement, and choose Extract literal to local variable. Undo, Redo, then Undo. Repeat with a string/character literal; try selecting a call or only part of a literal. | The action inserts `val extractedValue = 42;` immediately before the return, which uses that local. Existing identifiers force a fresh suffix. Exact text, indentation and diagnostics survive Undo/Redo. Unsupported selections offer no extraction. Selected X148 acceptance passes in both editors. |
| X149 | Open the shared Naming.x variants. Complete `str` in the middle of `strange` to `string`; repeat in a parameter, then complete `st` with another `string` already present. Undo each acceptance. | The whole declaration token is replaced. The collision variant proposes `string1`. No reference or rename is claimed. Both drivers pass exact source, diagnostics and one Undo (receipts in docs/errs-integration-plan.md). |
| X150 | Open the shared Templates.x variants. Complete `cl` with `class declaration`, then `if`, `for`, `do` and `try` with their block templates, and `interface` with its declaration template. Inspect the selected name/condition, press Tab to reach the body, and Undo. Also try `mo` in an empty file and completion inside a string, comment, member access and argument. | Templates appear only in the corresponding file/member/statement context. Placeholder navigation leaves exact indentation and source unchanged; Undo restores the prefix. IntelliJ may first restore the placeholder selection without changing text, then undo the insertion. Comments, strings and value/type positions receive no syntax templates. Clients without snippet support receive plain text without placeholder syntax. Both editor cases pass; syntax exclusions and minimal-client behavior have backend/protocol coverage. IntelliJ uses the documented UP18 indentation constraint. |
| X151 | Open the shared MissingNames.x variants. Invoke completion after the space following the property or parameter type, before the initializer or closing parenthesis. Accept `string1`, `string`, `list`, `stringArray`, `fn` and `value` across named, nullable, array, function and compound types, then Undo each insertion. Also try an empty name at EOF, an already written name, a comment and a bare local expression. | Completion inserts only the suggested name at the empty cursor range, preserving the written type, whitespace and remaining source. Collision checks include existing document identifiers. Accepted variants compile; one Undo restores the incomplete source. Existing names, comments and ambiguous expressions acquire no empty declaration-name slot. X151 passes in both editors; EOF, UTF-16/CRLF and refusal controls pass in parser/backend/protocol tests (receipts in docs/errs-integration-plan.md). |

| X152 | Open the shared SemanticValues.x variants. Complete `thi` to `this.Owner`, then `this.Ow` to `this.Owner` inside a nested class call. Complete an empty indexed callback argument with `0`. Undo each acceptance. | Only an enclosing instance that fits the actual call is offered. Qualified insertion replaces only the selected member. Indexed functions retain their function parameter type. Every accepted document compiles and Undo restores the exact incomplete source. Static-boundary and incompatible-type refusals have backend coverage. |


For a project using the updated Gradle plugin, run `./gradlew exportXtcLspModel` in that project's
root to export `.gradle/xtc/lsp-model.json`. Run `./gradlew prepareXtcLspModel` to process resources
and export the model together. These tasks do not compile application modules; declared source
generators may run to establish source membership. In IntelliJ, open **Languages & Frameworks >
Ecstasy Compiler**; in VS Code, invoke **Ecstasy: Configure Compiler Paths**. Inspect the model origin,
main/test owner, source roots, processed resource roots and missing outputs. Use **Refresh Gradle
model**, **Prepare generated resources**, **Open build file** and reset controls. An explicit
source/resource override must survive refresh. Missing or malformed reports must not crash the
server. Invoking the real Gradle wrapper from these controls is a manual check; X129 covers the
import/settings lifecycle and TestKit covers the actual producer tasks. Automatic IntelliJ Gradle
sync and nested/composite build aggregation are still follow-ups.

**Current hardening batch:** updated X76/X118 and new X123 pass in both editors with shared
scenario SHA-256 `959c3e71f68b00f58e6cc5cc22e275b20623442600175975ed1ab36a718567d3`.
Receipts: VS Code `run-S6wn4Y/results.json`; IntelliJ `run-5250369345097873268/results.json`
under each editor's `build/reports/compiler-playbook/`. IntelliJ also passes START and reports
zero IDE failures. This does not establish a full pass of the current 128-case catalog.
See the [platform demo](../../demo.md) for a real-code tour and its current blockers.

X122 has eleven shared variants, all passing in VS Code `run-IsPGqC` and IntelliJ
`run-658720962975078754`. The IntelliJ run also passes X105, which shares the native intention
helper. Both compare complete source and use native Undo/Redo/Undo; neither receipt represents
a full-catalog rerun. Details belong to the L63 library/complete-repair batch in the integration plan.
Member generation uses `refactor.rewrite` so the class intention remains available without a
diagnostic at the class name. Library contracts stay read-only while implementations are inserted
in user sources. Unvalidated computed defaults and unsupported type/constant spellings still refuse
an action. The final variant checks that `read` is not offered again; other inherited overrides may
legitimately remain available.

For X93's nested-type and alias variants, temporarily replace `Editing.x` with this source.
Complete immediately after `Owner.Ite`, accept `ItemPublic`, then replace that type with
`Alias.Ite` and accept `ItemAlias`. Restore the original fixture afterward.

```text
module Editing {
    import Owner as Alias;
    class Base { class ItemBase {} }
    class Owner extends Base {
        class ItemPublic {}
        private class ItemPrivate {}
        protected class ItemProtected {}
        static Int ItemValue = 1;
        typedef String as ItemAlias;
    }
    String StringValue = "x";
    void damaged(Owner.Ite value) {}
    Int later = 1;
}
```

Argument-value suggestions cover visible readable locals/parameters and implicit properties/constants
in empty final positional slots, pending named values and direct final bare-name prefixes. They use
compiler inference, conversions and receiver-specific types. Locals retain flow narrowing; ordinary
property reads do not gain that narrowing. Lexically visible enclosing/imported property names
are checked by ordinary read validation and argument fitting; arbitrary enclosing-instance enumeration and literal synthesis remain unsupported.
X97 fits qualified/grouped values, compound operators and empty/prefix operand slots against the
complete argument expression and later written arguments. Type-valued and receiver-rewritten
function fallbacks preserve visible signature parameter mapping. Outside a call, missing operand
slots provide lexical completion without an argument-fit claim. X83–X87 add specialized
constructors and bounded declaration/literal recovery.
X88–X89 add anonymous superclass forwarding and constructors declared inside retained bodies, including
interface implementations and captured locals. Cursor analysis prepares declaration signatures but does
not validate capture behavior or emit unfinished bodies. X91–X92 add simple unqualified member/return
and method-parameter type prefixes, including empty parameter type slots in API tests. Incomplete
names/signatures are never invented. X93 adds flat qualified type prefixes, imported qualifiers,
visibility, inherited types and final-token edits. X94 adds written leaf names inside parameterized
and compound headers, nullable/array wrappers (API tests), and bounded missing angle/group closers.
X95 adds class/interface composition type slots, qualified/generic leaves and missing-angle repair.
Candidates are visible types; the full generic constraints are checked by normal compilation.
X96 adds registered formals, empty generic slots, parameterized qualifiers and whole-final-token edits.
The focused X94–X96 run `run-MdzjLq` passes all three cases; 98 other cases are not selected.
X98 adds generic base-name completion while preserving written type arguments.
X106 adds function-parameter/return and sequence leaf types. X107/X108 add trailing dots, empty type
operands, qualifier-middle edits, and generic/multiple-return headers. Unregistered header formals
provide bound-labelled completion/hover after compiler constraint resolution, including sibling
bounds and qualified children. Cyclic/unresolved bounds still shadow outer names without fabricated
type identities; module/package compositions have bounded
compiler recovery. X90 adds empty/final-prefix single-dimensional
size slots, including a missing `]`; fitting uses the real Array constructor's Int parameter. The
prefix query does not validate a following supplier. Types without an element default still require
a supplier when compiled normally. Multidimensional construction and unfinished declaration names
remain unsupported; no literal value or declaration token is invented.

## VS Code Extension Playbook

A self-contained QA runbook for verifying the **VS Code extension** against the same feature surface the IntelliJ plugin is tested against in sections 1–19. Use this whenever you ship a `.vsix` (release, snapshot, or local build) and want end-to-end confidence that nothing regressed for VS Code users. Headless regression coverage of the file-association pipeline is provided by `:lang:vscode-extension:testVscodeExtension` (see the [extension README](../vscode-extension/README.md#testing)); the playbook below covers everything that test can't, which is the interactive LSP / DAP / UI surface.

### Setup

```bash
# 1. Build a fresh .vsix from this checkout
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true

# 2. Install (or upgrade) it
code --install-extension lang/vscode-extension/xtc-language-0.4.4.vsix

# 3. Confirm the LSP server JAR is present (built as part of step 1)
ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar

# 4. Open a workspace with .x files (TestModule.x from section 3 is sufficient)
code /path/to/xtc-project
```

> **Alternative — no install.** `./gradlew :lang:vscode-extension:runCode -PincludeBuildLang=true -PincludeBuildAttachLang=true` launches VS Code in Extension Development Host mode against the build tree with `src/test/fixtures/hello.x` open. Useful for verifying without touching the user profile.

### Keybindings reference

| Action | macOS | Linux / Windows |
|--------|-------|-----------------|
| Command Palette | `Cmd+Shift+P` | `Ctrl+Shift+P` |
| Go to Definition | `F12` or `Cmd+Click` | `F12` or `Ctrl+Click` |
| Peek Definition | `Opt+F12` | `Alt+F12` |
| Find References | `Shift+F12` | `Shift+F12` |
| Rename Symbol | `F2` | `F2` |
| Quick Fix / Code Actions | `Cmd+.` | `Ctrl+.` |
| Hover | mouse hover | mouse hover |
| Trigger Completion | `Ctrl+Space` | `Ctrl+Space` |
| Trigger Parameter Hints | `Cmd+Shift+Space` | `Ctrl+Shift+Space` |
| Format Document | `Shift+Opt+F` | `Shift+Alt+F` |
| Format Selection | `Cmd+K Cmd+F` | `Ctrl+K Ctrl+F` |
| Outline | View → Outline | View → Outline |
| Toggle Line Comment | `Cmd+/` | `Ctrl+/` |
| Toggle Block Comment | `Shift+Opt+A` | `Shift+Alt+A` |
| Fold / Unfold | `Cmd+Opt+[` / `]` | `Ctrl+Shift+[` / `]` |
| Restart Language Server | Cmd Palette → "Ecstasy: Restart Language Server" | Cmd Palette → "Ecstasy: Restart Language Server" |
| Show Server Output | Cmd Palette → "Ecstasy: Show Language Server Output" | same |

### Pre-flight checks

| # | Check | How | Pass condition |
|---|-------|-----|----------------|
| P1 | Extension is loaded | Extensions panel → search "Ecstasy" | "Ecstasy Language Support" listed with version `0.4.4`+ |
| P2 | `.x` files map to Ecstasy language | Open any `.x` file | Status bar (bottom right) reads "Ecstasy" |
| P3 | LSP server started | Status bar (bottom right) | Shows `✓ Ecstasy` (green check). `⟳ Ecstasy` = starting, `⚠ Ecstasy` = error, `✗ Ecstasy` = stopped |
| P4 | Adapter selection visible | Output panel (`Cmd/Ctrl+Shift+U`) → "Ecstasy Language Server" channel | `Backend: TreeSitter` (or `Backend: Mock` if `-Plsp.adapter=mock` build) |
| P5 | Semantic tokens enabled | Same output channel | `semantic tokens ENABLED (23 types, 10 modifiers)` |
| P6 | Java runtime detected | Same output channel at startup | `Java home: /path/to/java` (Java 25+) |
| P7 | Snippets registered | Type `mod` then `Tab` in a `.x` file | Expands to a `module` declaration skeleton |

If P3 stays `⟳`/`⚠`/`✗`, click the status bar item to restart the server. If that fails, jump to [Troubleshooting → VS Code LSP Not Starting](#vs-code-lsp-not-starting).

### Feature playbook

This table maps every numbered feature from sections 1–19 to the exact VS Code action and verification surface. The numeric IDs (e.g. `3.5`) match the test-case IDs in the per-feature sections above — refer there for fine-grained subtests and expected outputs. For compiler mode, use the [XdkAdapter playbook](#xdkadapter-playbook); unsupported compiler features in this table are not expected to pass.

| § | Feature | VS Code action | Where to verify | Notes |
|----|---------|---------------|-----------------|-------|
| 1 | Syntax highlighting (TextMate) | Open `TestModule.x` | Editor view — keywords/strings/comments colored | Active even before LSP connects (P3 still pending). If absent on a `.x` file, P2 failed. |
| 2 | Hover | Hover mouse over a symbol; or `Cmd+K Cmd+I` for keyboard | Tooltip popup | See §2 for expected content per symbol kind. |
| 3 | Completion | Type a prefix → `Ctrl+Space` | Completion popup | Subtests 3.1–3.11 — each row in §3 applies verbatim. |
| 4 | Go to Definition | `F12` or `Cmd+Click` on a symbol | Editor jumps to declaration | Subtests 4.1–4.10 — see §4. `Opt+F12` peeks instead of jumping. |
| 5 | Find References | `Shift+F12` on a symbol | "References" peek view | §5 covers both same-file and cross-file expectations. |
| 6 | Outline | View → Outline (or Cmd+Shift+O for symbols-in-file) | Outline panel populates | §6 — module / class / method hierarchy. All three adapters. |
| 7 | Diagnostics | Save a `.x` file with a known syntax error | Problems panel (Cmd+Shift+M) + red squigglies | §7. The mock adapter reports fewer diagnostics than tree-sitter; the compiler adapter reports semantics neither can see. |
| 7a | Compiler adapter specifics | Build with `-Plsp.adapter=compiler`; watch the server output channel | Problems panel + the `compile:` lines in the log | §7a — bundled-library startup without `XDK_HOME`, cold vs steady timing, queue depth and superseded edits. |
| 8 | Document highlight | Click on an identifier | Other same-name occurrences in file get a subtle highlight box | §8. |
| 9 | Selection ranges | Place cursor in expression → `Shift+Opt+Cmd+→` (macOS) or `Shift+Alt+→` | Selection expands outward through AST nodes | §9. |
| 10 | Folding ranges | Click the gutter triangles or `Cmd+Opt+[` | Block / method / class folds | §10 — verify all listed scopes fold correctly. |
| 11 | Rename symbol | Place cursor on symbol → `F2` → type new name → Enter | All in-file references rename atomically | §11. Cross-file rename is TODO (see Future Enhancements). |
| 12 | Code actions | Place cursor on a diagnostic → `Cmd+.` | Quick-fix menu appears | §12 — varies by adapter. |
| 13 | Document formatting | `Shift+Opt+F` (whole) or `Cmd+K Cmd+F` (selection) | Reformatted source per `xtc.formatting.*` settings | §13. `editor.formatOnSave: true` to verify continuous formatting. |
| 13a | On-type formatting (auto-indent) | Press Enter inside a class / method / `{}` block | Cursor indents to the correct level | §13a. Requires `editor.formatOnType: true` (default for `[xtc]`). |
| 13b | Code style settings | Edit `xtc.formatting.indentSize`, `tabSize`, etc. in `Cmd+,` | Subsequent formatting honors the new values | §13b. The Ecstasy-specific UI section is under **Settings → Extensions → Ecstasy**. |
| 13c | Settings → LSP round-trip | Same as 13b, then trigger a format | LSP server picks up new options via `workspace/configuration` | §13c. Check server log for `Updated formatting options: ...`. |
| 14 | Signature help | Inside a method-call argument list, `Cmd+Shift+Space` | Parameter-list overlay | §14. |
| 15 | Document links | URLs / file paths in comments | Cmd+Click activates them | §15. |
| 15a | Extra source roots (`xtc.sourceRoots`) | Set the setting (string array), restart server, Cmd+Click into an imported external module | Definition jumps into the external tree | §15a subtests apply verbatim. **VS Code uses the `xtc.sourceRoots` setting key**, not env vars or system props (the env-var / sysprop paths are tested separately in §15a.1–.2). |
| 16 | Comment toggling | `Cmd+/` (line) or `Shift+Opt+A` (block) | Lines / blocks comment-toggle | §16 in the IntelliJ-marked section also applies to VS Code via the language-configuration commentary mapping. |
| 17 | Snippets | Type a prefix (`mod`, `cls`, `svc`, `mix`, `con`, `meth`, `run`, `if`, `ife` …) then `Tab` | Expansion appears with tab stops | §17 is IntelliJ-specific in framing, but every snippet in `lang/vscode-extension/snippets/xtc.json` is the VS Code counterpart. |
| 18 | Code lens (run actions) | Open a module/class with a `void run()` | `Run` / `Debug` codelens above the method signature | §18. Powered by the LSP server, identical contract. |
| 19 | Semantic tokens | Open any `.x` file with `editor.semanticHighlighting.enabled: true` (default for `[xtc]`) | Types / methods / properties / annotations colored distinctly | §19. Subtests 19.1–19.11 apply verbatim. |

### VS Code-specific concerns (not covered by the IntelliJ sections)

These have no IntelliJ analogue (or are surfaced differently). They round out the QA pass.

| # | Concern | How to verify | Pass condition |
|---|---------|--------------|----------------|
| V1 | File association on stale profile | Open a workspace where `files.associations` in user `settings.json` maps `"*.x"` to `"plaintext"` or another language | Status bar still shows "Ecstasy" within ~1s; the extension's `ensureXtcLanguageAssociation` hook overrides via `setTextDocumentLanguage`. |
| V2 | File association on tab restore | Close VS Code with a `.x` file open in a non-active tab → reopen the workspace → click the tab | Tab loads with `Ecstasy` language, not the default. Verifies the `onDidChangeActiveTextEditor` listener. |
| V3 | Status bar lifecycle | Watch the bottom-right status item during server startup, idle, restart | Transitions `⟳ Ecstasy` → `✓ Ecstasy` on start; `✓` → `⟳` → `✓` on Cmd-Palette "Ecstasy: Restart Language Server"; `✗ Ecstasy` on crash (LSP server JAR removed or JVM killed) |
| V4 | Output channel routing | Open Output panel → dropdown | Two channels: `Ecstasy Language Server` (LSP traffic) and `Log (Extension Host)` (extension's own `console.log`/warn/error). No errors in either under normal use. |
| V5 | Trace setting | Set `xtc.trace.server: "verbose"` in `settings.json` → restart server | `Ecstasy Language Server` output channel now shows every JSON-RPC frame |
| V6 | Java discovery fallback | Unset `JAVA_HOME`, leave `xtc.java.home` empty → restart VS Code | Extension finds Java via `jdk-utils` (SDKMAN, mise, Homebrew, Gradle cache, etc.) — log line `Java home: …` at startup |
| V7 | Java auto-download | On a machine with no Java 25+: same as V6, but `jdk-utils` finds nothing | Progress notification `Downloading Java 25 JRE for Ecstasy Language Support`; subsequent restart uses the cached JRE silently |
| V8 | Tasks (Gradle build / test / clean / run) | In a workspace with a `build.gradle.kts`, open Terminal → Run Task… | "Build", "Test", "Clean" tasks listed under "xtc"; selecting runs `./gradlew build/test/clean` |
| V9 | Custom task (`xtc.runModule`) | Add a `tasks.json` entry of type `"xtc"` with `moduleName: "TestModule"` → Terminal → Run Task → pick it | Runs `./gradlew runXtc -PmoduleName=TestModule` (or `xtc run TestModule` if `useGradle: false`) |
| V10 | DAP launch from defaults | Open a `.x` with `void run()` → press `F5` | Launches "Debug Ecstasy Module"; auto-detects the module name from `module Foo {}` declaration |
| V11 | DAP launch from `launch.json` | Add a `"type": "xtc"` config → set breakpoint → `F5` | Stops at breakpoint; variables / call stack visible in Debug panel |
| V12 | Create-project command | Cmd Palette → "Ecstasy: Create New Project" → pick `class` / `library` / `service`, pick a folder, enter a name | Terminal labeled "XTC" opens and runs `xtc init <name> --type <kind> --dir <folder>`; new project skeleton appears |
| V13 | Extension uninstall is clean | Uninstall via Extensions panel → reload | No leftover language association for `.x`; the LSP server JVM is terminated (visible in `jps` / Activity Monitor) |
| V14 | `.vsix` re-install replaces cleanly | `code --install-extension xtc-language-*.vsix --force` from a session that already has the extension loaded | Extension reloads; status bar reconnects; no duplicate output channels |
| V15 | `.x` file icon | Look at any `.x` file in the Explorer view | Custom Ecstasy file icon shown (`icons/xtc-file.png`), not the generic document glyph |
| V16 | Marketplace icon | Extensions panel → click the extension entry | Marketplace icon `icons/xtc.png` (256×256 Ecstasy logo) renders at the top of the details page |

### Headless regression test (automated)

The single automated check that exercises the manifest end-to-end:

```bash
./gradlew :lang:vscode-extension:testVscodeExtension \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

It downloads a pinned VS Code build into `.vscode-test/`, loads the extension from the build tree, opens `src/test/fixtures/hello.x`, and asserts `editor.document.languageId === 'xtc'`. Runs in <30 s after the first cached download.

This is the dedicated automated gate for V1/V2 (file association). The compiler shared scenarios also automate many provider/host workflows; the visual and physical-input checks in this section remain manual. Wire `testVscodeExtension` into CI when you want a continuous canary for the manifest pipeline (needs `xvfb` on headless Linux runners — the wrapper auto-detects).

---

## Additional feature acceptance and remaining gaps

### 20. Linked Editing Ranges

The compiler adapter implements linked ranges for repeated, rename-eligible **local variables**
in one successful source snapshot. X133 now checks identity separation, nonlocal refusals, invalid
source and recovery in both installed transports. Linked editing has no proposed-name proof;
use Rename for binding-preserving refactoring. It does not link arbitrary same-spelled names or
cross-file occurrences.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 20.1 | Local identity | Use X133's two methods with same-spelled locals; request linked ranges on the first local. | Only its declaration and use are returned; second method untouched. |
| 20.2 | Single occurrence | Declare an unused local; request linked ranges. | No linked range set requiring multiple occurrences. Backend coverage exists; add this variant to shared acceptance when extending X133. |
| 20.3 | Unsupported symbols | Request linked ranges for a parameter, type/class, property, keyword or literal. | Compiler returns no linked ranges. Tree-sitter's heuristic scope is different. X133 includes type and parameter; remaining token classes are manual/adapter tests. |
| 20.4 | Broken/repaired source | Introduce an unresolved type, request links, then repair it. | Broken semantic snapshots yield no linked edits; local ranges return after repair. |
| 20.5 | Native presentation | Enable the editor's linked-editing feature if supported; type an innocuous local-name change and undo it. | Check synchronized occurrences, cursor/selection and exact restoration. If this host only exposes the protocol, record UI support as unavailable; do not substitute textual multi-cursor selection. |

### Existing-feature coverage audit (2026-09-30)

All adapter feature families have implementation checks, but the shared catalog is not a claim
of every native interaction or every language form. The following checks supplement existing
sections instead of silently counting provider responses as complete UI acceptance:

| Feature family | Existing shared evidence | Additional manual or targeted acceptance still needed |
| --- | --- | --- |
| Diagnostics and Problems | X1/X2/X23/X27/X46–X52/X123, 7a.8/7a.9 | Inspect error/warning presentation, source code and related-location navigation in Problems; verify closed/deleted URIs, duplicate-free warnings, configuration failures without modal loops, and refresh without changing buffer versions. Long editing/idle/restart runs are distinct from one successful correction. |
| Completion and signature UI | X6–X20/X70–X98/X106–X108/X125 | Keyboard and mouse acceptance, Escape without edits, overload selection/active parameter after retrigger, IME/non-ASCII identifiers and end-of-file/no-final-newline boundaries. Existing UTF-16/CRLF and syntax-recovery variants remain mandatory; untested forms are not implied by a fitting result. |
| Navigation, symbols and hierarchy | X3–X5/X21–X22/X28/X33–X44/X59/X63–X69/X99–X101/X131 | Native workspace-symbol search/navigation for closed roots, multiple declaration targets, back navigation, ambiguous/missing source attachments, inherited generic/compound edges and stale closed-file handles. Do not infer a complete conditional-mixin hierarchy or dynamic call graph. |
| Highlights, folds and selection | X1/X4/X41/X92 and L56 startup test | Exact nested selection expansion/shrink, empty/recovered document fallback, folding boundaries after closing/reopening or deleting text, and theme/read/write visual distinctions. Startup race assertions are a separate harness test, not the normal readiness wait. |
| Inlay hints | X42/X131 | Enable/disable through native controls, inspect tooltip rendering for locals/parameters/lambdas, verify no misleading names for unnamed function calls, named arguments or omitted defaults. A protocol tooltip does not prove the UI renders it. |
| Formatting | X31/X132; backend token-preservation tests; sections 13–13c | Apply full/range/on-type edits through native actions; verify literal and multiline-string/comment bytes, CRLF, tabs, final-newline policy, untouched lines and idempotence. Check real supported settings; a max-line-width control does not establish wrapping. These variants are not all shared-driver scenarios yet. |
| Document links | X131 plus lexical unit tests; section 15 | Click HTTP(S) URLs in comments/strings; verify exact ranges and absence of invented import/file/unsupported-scheme links. Malformed/partial literals must not create arbitrary targets. External browser launching is a manual action, separate from resolving a URI. |
| Code lenses / Run | X131 verifies lens identity and command arguments; section 18 | Verify native lens placement and invocation target, no lens for an ordinary class-only member, and current arguments after moving a file. Successful persistent execution/rerun is **R1–R8 work**, not established by showing a Run lens. |
| Semantic tokens | X41/X126, existing token tests; section 19 | Inspect theme fallback, enable/disable behavior, UTF-16/range clipping, edits/close/restart with old result IDs and refresh. Audit per-token claims (e.g. deprecated tags) against actual modifiers rather than claiming every row for every adapter. |
| Rename, actions and resource edits | X53–X63/X102–X105/X109–X122/X127–X130 | Collision/capture refusals, cancellation, stale documents, graph persistence, closed consumers, one Undo/Redo and source text plus filesystem bytes; see the batch matrix. Explorer drag/drop and arbitrary third-party edit application are separate host paths. |
| Discovery, paths and build settings | CFG1–CFG3/X99/X100/X124/X129/X134 | Multiple roots, Unicode/space-containing paths, symlinks, project close/reopen and external shared watch leases. Gradle-import failure, missing generated resources and no-Gradle projects must retain usable last-valid inputs and show actionable status. |
| Generic editor integration | Separate sections 1, 16, 17 and VS Code V-cases | File association, TextMate fallback, comment/uncomment, templates/snippets, keybindings, plugin reload and Community-only startup. Compiler shared scenarios do not replace these checks or a default Tree-sitter smoke run. |
| Reliability and configuration | X29/X30/X51/X57 plus process/retention suites | Cross-platform/remote filesystems, large-workspace latency/heap bounds, prolonged run/stop/crash cycles, cancellation under queue pressure, and UI1–UI7 settings acceptance remain explicit L82 gates. |

A catalog entry's `coverage: full` means the driver implements that **case's stated assertions**;
it does not mean the entire feature family is exhaustive. Manual checks should record pass/fail/
unavailable/not-run and a reason. Promote repeatable missing variants into shared data and both
drivers as they are implemented; keep genuine visual/OS/runtime checks explicitly separate.

### Semantic Tokens: Current Scope and Follow-ups

Tree-sitter supplies the default syntax-based tokens; broader heuristic usage-site classification
remains a possible enhancement. The opt-in compiler adapter now supplies resolved-name tokens and
modifiers independently, with no Tree-sitter fallback or combined adapter. Broader syntax coverage
must preserve the distinction between compiler facts and lexical coloring.

### Cross-File References (partly done)

Tree-sitter supplies cross-file go-to-definition, workspace symbols and import links through its
workspace index. The compiler supplies definition and references across the current module by
resolved identity, including closed member files; workspace symbols compile unopened graph members on demand.
Explicit host-indexed dependency sources also supply definition/type-definition and inherited
method-body targets. Reference queries now compile the complete configured source graph; they
include unopened consumers and source uses of binary XDK members without inventing source targets.
Still remaining:

- Persistent reference indexing and consumer discovery outside workspace roots
- Broader member rename and external-consumer closure

### Full Compiler Integration (partly done)

Done - see §6, §7, §7a and [module sessions and hierarchy](#compiler-module-sessions-and-hierarchy):
- Semantic error detection, with the compiler's own codes and spans
- Document outline, from the parsed AST
- Typed hover and identity-based definition/references across a module
- Unsaved member overlays, sibling invalidation and diagnostics at each file's URI/version
- Direct extends/implements hierarchy between source types, including generic parents
- Bounded scope/member completion, generic call-site signatures and incomplete-call candidates
- Type-definition and nominal type/method implementation lookup
- Static call hierarchy, resolved-name tokens, read/write highlights and bounded inlay hints
- Explicit dependency artifacts/source indices, consumer invalidation and server diagnostic refresh
- Workspace discovery and automatic dependency recompilation for discovered/configured roots/edges, including unsaved overlays
- Cross-module implementations and type/call hierarchy over the complete source graph
- Proven inline-type/static-member rename and ordinary-import cleanup
- Java-lexer formatting, URL links and lexical tokens, module run lenses and local linked editing
- Initialization/live source-graph settings, exposed in VS Code as `xtc.compiler.sourceModules`

Still to come:
- Broader Java parser recovery, incomplete-expression contexts and callable forms
- Persistent indexing and measured incremental work across larger module graphs
- Binary source attachment, conditional-mixin hierarchy and broader implementation targets
- Wider member/workspace rename: unsupported composition routes and automatic external-consumer
  enumeration. X119–X121 add primary-constructor properties, lambda parameters and method-value
  escapes. The L62 extension also covers public/explicit-constructor parameters, supported
  composition families, qualified modules and implicit package directories. Both clients implement graph persistence through native Rename. Shared X109–X118
  pass in selected runs in both editors. VS Code edited-file moves with refactoring auto-save
  disabled remain refused. Consumers outside the graph remain unknown
- Diagnostic-driven quick fixes and refactorings

L61 declaration acceptance: X4 now also requests Go to Declaration for the shadowed local and
property and checks that their distinct declaration targets match definition. VS Code uses its
declaration provider; IntelliJ checks the request through the installed client transport beside
the existing native navigation actions. Backend/protocol cases cover multiple inherited contracts,
property overrides, import aliases, closed consumers, indexed libraries and stale source.
Combined backend/protocol validation and selected X4/X102/X103/X104 runs pass in both clients.
See the [L55/L61/L62 receipts](../../docs/errs-integration-plan.md#teaching-workspace-declarations-and-resource-moves-l55l61l62).

### Rename extension batch acceptance

X119–X121 are shared by both drivers and add primary-header properties, lambda captures and
escaped method values. At that checkpoint the catalog contained 126 cases; X119–X121 pass in VS Code
`run-c7xgn7` and IntelliJ `run-14634841832018763989`. The latter also passes X57, X118 and
CFG1–CFG3 plus START with zero IDE errors. VS Code's single-folder settings correction passes
X118 and CFG1–CFG3 in `run-Z5w8sz`. No full 126-case receipt is claimed. Source-graph scope receipts on
`xtc/rename` describe configured/discovered inputs, including explicitly registered external
roots. Unknown external consumers are not discovered by rename. Register them in sourceModules
before renaming; an unreadable registered source refuses the proposal rather than reducing scope.

### Server log shortcut acceptance

Press **Ctrl+Alt+X, then L** (macOS: **Control+Option+X, then L**) in either editor.
IntelliJ opens **Language Servers** with the Ecstasy **Log** tab selected; repeat the shortcut
to hide that tool window. VS Code selects **Ecstasy Language Server** in Output; repeat to hide
that output view. Remap the actions in native Keymap/Keyboard Shortcuts settings. Showing logs
does not start another server, clear history or change the logging level.

- Open the log from a source editor and verify recent queue/job, compile and request timing lines.
- Toggle closed/open; manually close the panel and invoke the shortcut again.
- Select a different VS Code output channel, then invoke the shortcut: select Ecstasy output.
- Move/dock the view and verify the shortcut still opens/hides it. In IntelliJ, a visible
  Language Servers window is hidden even if its selected tab is Settings or Trace.
- Stop/restart the server and verify retained logs remain accessible without a source edit.
- Verify the chord does not modify the source and a custom keymap binding works.

Shared X135 passes in both editors for view open/hide and unchanged source. Physical
shortcut dispatch and log-tab selection remain explicit manual checks.

Known native-host regression to watch: VS Code 1.139.1 can throw an Explorer `Data tree node not
found` while clearing Cut decorations after a successful batch Paste. If it occurs in X130,
inspect actual paths before doing anything else; never replay an already applied move. Verify
Explorer refresh and its Undo/Redo separately and retain the failed receipt. This is tracked in
[the watcher/move acceptance record](../../docs/errs-integration-plan.md#watcher-move-and-log-view-acceptance-follow-up-2026-09-30).


### Language-service settings acceptance (UI1–UI7)

Selected X118/X132/X135–X139 pass in VS Code `run-1aSPTX` and IntelliJ
`run-3866544261762285778` (also START; zero IDE errors). The [settings validation record](../../docs/errs-integration-plan.md#editor-settings-implementation-batch-ui1ui7-2026-09-30)
retains the earlier failures and fixes. These receipts cover seven selected scenarios, not all 144.

IntelliJ Community: Settings → Languages & Frameworks → **Ecstasy Language Service Defaults**
(application) or **Ecstasy Language Service** (project). The project page inherits defaults until
unchecked. Use Apply, Reset, Cancel and re-open to verify persistence. Transport applies with a
restart; inlays and Code Style apply live. Server save formatting is disabled with an explanation;
use Settings → Tools → Actions on Save → Reformat code, then the native Save action. X139 deliberately
starts with `Int` at column 0 and must indent it four spaces when saved. The effective view is read-only and refreshed on
request. Compiler source/resource paths stay in **Ecstasy Compiler**, now with native path choosers.

VS Code: run **Ecstasy: Open Language Service Settings**, or filter Settings by
`@ext:xtclang.xtc-language`. Connection options are user/workspace scoped, not folder scoped.
Use **Ecstasy: Show Effective Language Service Configuration** for configured values and origin,
running capabilities, queue and bundled read-only libraries. **Ecstasy: Show Effective Compiler
Paths** remains the detailed source/build-model view. Trace and logs use the existing controls.
Legacy formatter tab width and line width are explicitly deprecated: they do not implement wrapping.

Additional manual acceptance: change an application default while a project inherits it, then add
and remove a project override; reopen the IDE and verify persistence. Edit a compiler graph through
rename/Undo while the service page is open and ensure unrelated fields survive Apply. Verify user
versus workspace VS Code precedence, two workspace folders and folder-specific native save/inlay
preferences. Check invalid JSON retains a running service, native Reset/Cancel never saves a draft,
and restart never saves a dirty source. Remote filesystems, alternate keymaps and restricted
workspace build execution remain explicit boundaries, not assertions covered by these local runs.


Protocol hardening acceptance (L80/L81): X136/X137/X140/X141 pass in VS Code `run-5eCFZV` and
IntelliJ `run-15914309414363009017` (also START, zero IDE errors). X140 checks a method-body reference
after an emoji; the subsequent L83 implementation is exercised separately by X142 below.
For a large configured project, request workspace diagnostics or references
and observe the work-done progress notification. Cancel the request: its progress must end, while
another reader and document analysis remain usable. Check no stuck progress after restart/close.
Unit tests control the queue and delayed creation acknowledgements deterministically; short editor
fixtures do not guarantee a visible progress popup. Bounded partial-result streaming is covered by X143 below.
Check dependency changes refresh supported inlays/lenses/folding without editing the consumer.
Legacy/minimal-client capability shapes and pre-initialize/shutdown errors are protocol tests, not
features exercised by changing the modern IDE's capability declaration.

### Protocol and lifecycle coverage map

This map covers the L80/L81 protocol batch and the accompanying state-ownership fixes. A shared
scenario is automated in both editors only where named below. Controlled backend tests exercise
interleavings that normal editor actions cannot reliably force. Manual rows remain acceptance
work; they are not passing automated receipts. The current catalog has 152 cases. The earlier
protocol checkpoint reran X136/X137/X140/X141 plus IntelliJ START; follow-up receipts are recorded below.

| Change | Automated regression evidence | Editor/playbook coverage and limits |
|---|---|---|
| UTF-16 hover and rename ranges | `ClientPresentationTest`, `XdkPresentationTest` | X140 passes in both editors after an astral character; folded property initializers have the separate X142 regression. |
| Legacy roots, minimal-client hover/outlines/symbol kinds and optional diagnostic metadata | `ClientPresentationTest`; negotiated clients in `XdkLanguageServerTest`, `XdkModuleServerTest`, `XdkProjectServerTest` and `XdkFeatureResolveProtocolTest` | Modern editors cannot exercise every legacy capability shape. X140 and existing outline/diagnostic cases cover modern clients; protocol tests cover the reduced shapes. |
| Progress creation, token ownership, cancellation, late acknowledgements and one terminal event | `ConnectionProgressTest` controls replies and races completion/cancel/close | Manual P1/P2 below. Short compiler fixtures do not guarantee a visible progress popup. |
| Pending readers canceled independently of shared analysis; immediate retirement on close | `RequestOwnershipTest` uses an analysis future that can ignore cancellation | X137 covers connection restart with an unsaved buffer; P2 covers pending UI work. The selected editor case does not force the backend race. |
| Pre-initialize, duplicate initialize and shutdown request rules | `ProtocolLifecycleTest`, `XdkStdioTest`; `LspProcessLifecycleTest` covers child-process termination | X137 passes repeated transport restarts; invalid wire order is tested over packaged stdio, not sent through a conforming IDE. |
| Negotiated, coalesced refresh outside compiler locks | `ClientNotificationsTest`, `XdkPullDiagnosticsTest`, `XdkSemanticTokenProtocolTest` | Shared X146 passes dependency refresh and inferred hints in an untouched consumer in both editors. P3 retains broader per-provider visual acceptance. |
| Runtime `off` / `messages` / `verbose` trace without source payloads | `ClientNotificationsTest` | X141 passes messages/verbose in both editors; switching off and post-close behavior are controlled unit tests. |
| IntelliJ diagnostic-cache retirement after unlocked snapshot lookup | `DiagnosticResultMessagesTest` forces close/cancel during that lookup | Existing diagnostic/reopen scenarios cover normal editor flow; the precise race is unit-tested. |
| Tree-sitter scan starvation and native parser/disposal ownership | `WorkspaceIndexerTest` concurrent scans/parser requests run without skips | This is the shipping Tree-sitter adapter, not an XdkAdapter feature. Compiler playbook passes do not validate its UI behavior. |
| Retired VS Code connections and stale IntelliJ settings reports | X136/X137 pass in both editors; new X147 controls late report completion | X147 passes publication ownership after newer reports, settings changes, restart and native UI disposal in both editors, including a native restart between reply receipt and EDT publication. Broader multi-window interaction remains manual. |
| Negotiated configuration requests and post-close formatting replies | `ClientPresentationTest`, `EditorFormattingStateTest`, `FormattingConfigRoundTripTest` | X136 covers effective settings; earlier X138/X139 cover live formatting and save ownership. |
| Stalled watcher replies, late registration/removal and disconnect | `ResourceFileWatchersTest` controls acknowledgement deadlines and retry ownership | X124/X134 exercise ordinary external resource/source watching; they do not force client RPC stalls. |
| Compiler path drafts invalidated by settings/model/new-dialog changes | VS Code `compiler-paths.test.ts` | Actual settings writes and disposal are tested inside the extension host; no automated picker-click race is claimed. |
| Open buffers own index entries through scan/watch/close races | `WorkspaceIndexerTest` uses read barriers, including equivalent file URI spellings | Tree-sitter-specific; compiler playbook cases cannot establish this behavior. |
| Final provider inventory and optional link/signature/pull-diagnostic fields | `CapabilityContractTest` covers every adapter flag; `CapabilityNegotiationTest` tests absent/false/true fields; `DiagnosticPresentationTest` tests independent push/pull and related-report shapes; `XdkStdioTest` exercises rich/reduced sessions | X131 retains modern-client link/resolve coverage. Reduced initialization profiles are protocol tests, not a new UI case; no editor capability is changed midway through a session. |
| Action literals, preferred metadata, completion kinds and legacy command edits | `CapabilityNegotiationTest` | X131 covers negotiated modern-client actions. Legacy/minimal clients, stale handles and client refusal are protocol regressions. |
| Partial batches and actual initial scan progress | `PartialResultsTest`, `IndexingProgressTest`, `ConnectionProgressTest`, packaged `XdkStdioTest` | X143 compares every streamed workspace symbol with the ordinary response. Visible long-operation cancellation remains P1. |
| Detached constant-initializer facts | `SemanticModelTest`, `XdkInitializerTest` | X142 covers initializer hover/definition/references/rename/Undo in both drivers. |

Pending manual acceptance in both editors:

1. **P1 — Visible progress and cancel.** Open a large configured compiler project, enable verbose
   LSP tracing and open the server log using Control+Option+X, then L on macOS (Ctrl+Alt+X, then L
   elsewhere). Check that initialization advertises `window.workDoneProgress`. Request references
   on a widely used source declaration. For a request lasting long enough, verify progress is
   created only after initialization, begins after creation acknowledgement and ends on completion.
   Check that its detail identifies the source/workspace, then updates with the active compiler
   operation/source and queued count. Concurrent reference and workspace-check notifications are
   separate requests and may show the same shared compiler work; they do not imply duplicate compiles.
   Repeat and cancel through the editor's progress UI. A subsequent hover/reference request must
   still succeed. If all requests finish before progress appears, record this as unexercised.
2. **P2 — Disconnect with pending work.** While P1 or a workspace diagnostic request is running,
   restart the service using the same action as X137, then close/reopen the project. Unsaved text
   must survive restart, old progress must disappear, requests must resume, and old server PIDs
   must exit. Do not infer the race was exercised if the request finished before the action.
3. **P3 — Refresh without editing the consumer.** In the configured Library/Consumer graph, let
   Library expose `static Int make() = 1;` and let a Consumer method use
   `var value = lib.make();` with native inlay hints enabled. Change Library to
   `static String make() = "one";`, leaving Consumer untouched. Its inferred-type hint and semantic
   presentation must update. Inspect trace for refresh requests only for negotiated providers;
   inlay/lens/folding/diagnostic/token refresh replies must not leave the compiler queue blocked.
   Revert Library and verify the consumer returns to its original presentation.
4. **P4 — Late connection/settings callbacks.** Repeat X137's two transport changes while a
   request is pending. Check the displayed adapter/PID belongs to the newest connection and there
   is one live server afterward. In IntelliJ's Ecstasy Compiler settings, request two effective
   path reports around a configuration change, then close/reopen the page. A late old report must
   not overwrite the current report or a disposed page. Record actual overlap; a fast normal run
   alone is not evidence of out-of-order completion handling.

The disk-index/open-buffer, long-lived path-picker and stalled watcher-registration fixes and
their deterministic regressions are explicit in the [state audit](../../docs/errs-audit.md#mutable-state-and-deprecated-api-audit-2026-09-30-checkpoint).
Partial-result streaming and L83 initializer facts are implemented; their acceptance and remaining
limits are recorded below. Generic IntelliJ server-initiated text-edit checking is implemented;
X144 passes both editors. The catalog now contains 152 cases; the receipts below retain their original scope.

### Constant-folded initializer acceptance (X142)

Open `X142/FoldedInitializer.x` with its module configured in the compiler source graph. In
`Int copy = value`, hover `value`, go to its declaration, and find references: the declaration,
initializer and method-body reference must all appear. Rename to `number`; all three occurrences
must change and compilation must remain clean. Undo must restore the original source. Both editor
drivers pass these steps, including native IntelliJ rename/Undo. Backend tests
also cover expression types, semantic-token availability, nonconstant initializers and rejected probes.

### Partial-result acceptance (X143)

Both editor drivers create a configured module containing 130 classes, request its workspace
symbols normally and with a partial-result token, and compare every name in order. Progress batches
must contain at most 64 items and the final response must be empty. Unit tests additionally cover
cancellation between batches, stale publication, disconnect and backend failure. Acceptance results
pass in both editors. Initial Tree-sitter scanning now reports the actual scan lifetime;
its begin/report/end and cancellation ownership have controlled regressions.


Follow-up receipt (2026-09-30): VS Code `run-4Vo2G3` passes X124/X131/X134/X142/X143. IntelliJ
`run-6087249141816329018` passes START and X124/X131/X134/X143; corrected X142 passes with START
in `run-1763480386953487411`, with zero IDE failures and Ultimate disabled. Both drivers use catalog
hash `cbb3c633a3006b394cfffd86d3ec790783124b61562f128a8b97ab072deb47a8` (148 cases). This is selected
acceptance, not a full-catalog rerun. The [validation record](../../docs/errs-integration-plan.md#follow-up-validation-receipt-2026-09-30)
keeps initial failures, fixture/driver corrections and the unreproduced first native text mismatch.
Generic native applyEdit version checking is covered by X144 below. P1/P2 have X145's native
progress-model coverage; physical Cancel-button selection and the broader P3/P4 checks remain manual.

Current native ownership/progress acceptance: X144 and the 5,000-method X145 pass both clients
after the lexical performance fix. Catalog receipts follow below. VS Code cancellation invokes
the workbench-owned token; selecting and clicking that particular Cancel button remains manual.

Current IntelliJ coverage spans all 150 catalog cases plus startup across the full run
`run-11435582978373867143`, resumed `run-5480270557660243469`, and corrected selection
`run-5152567950207711961`. The last run passes X57/X126/X127/X132/X138/X139/X143 plus START,
zero IDE errors, and exits successfully. This is not a single clean full-catalog run: the original
extra-character failure, shutdown conflict, formatter notifications and harness failures remain
documented in the integration plan. X139 now proves closed-tab formatting; cleanup discards dirty
fixtures only after their scenario finishes. X143 uses a scoped wire listener so console rollover
cannot hide partial-result batches. VS Code `run-SAWG42` finishes with 126 passes and 24 failures,
including spurious incomplete-query cancellation by delayed directory watches and an X130 Explorer
focus failure before its move. X144 and the current 5,000-method X145 pass (X145: 15.7 seconds).
The server watcher correction and Explorer focus correction are undergoing editor revalidation;
the failed receipt remains in the integration plan.


VS Code X130 host limitation: 1.140.0 can complete a native batch Paste and then throw
`Data tree node not found` from Explorer's `itemsCopied` while clearing the old Cut highlighting.
The driver verifies Move, Undo, Redo and resource contents without replaying Paste, then reports the
original host error as a failure. A focused pass does not close this intermittent defect; the full
`run-06Z6tq` reproduces it after successful semantic/resource assertions. Track separately from
compiler move correctness and recheck with a future VS Code fix.


Corrected full VS Code `run-06Z6tq` passes **149/150** with only the X130 host repaint failure
above. All formerly failing completion/signature cases pass. X144/X145 pass with current workload;
X145 takes 16.0 seconds. This is explicitly not a clean full-catalog receipt.

Final IntelliJ receipt: `run-1843149430446112481` passes **all 150 scenarios plus START** in one
uninterrupted run, with zero IDE errors and successful shutdown (IDE 2026.2.3, Ultimate disabled,
LSP4IJ 0.21.0). It uses the same catalog hash as the full VS Code run above. X139's open and
closed Save All checks pass, and completed-fixture cleanup no longer causes a shutdown conflict.
Earlier aborted runs remain recorded for diagnosis, rather than being relabelled as passes.

The ordinary VS Code suite passes **23/23** in `extension-tests/run-Ts46BQ`, using a fresh profile
and workspace and restoring settings through the editor API. Its new shared status reporter shows
`Ecstasy tests: completed/total, remaining` at bottom left; the compiler runner uses
`Ecstasy playbook` with the same counter. X144/X145 pass in `run-aBMhCu` after sharing the reporter;
this selected run does not replace the full catalog's X130 failure. Hover the counter for the full
test title. Physical Cancel-button selection and the broader P3/P4 checks remain manual.


### Selected reliability receipt (2026-10-01)

The catalog has **152 cases**. VS Code `run-Ki54bo` passes X118/X129/X130/X136/X137/X139/X146/X147.
IntelliJ `run-13013331047107199721` passes all of those except X147, which exposes a stale report
after a service-only settings override. Corrected `run-5612866093768251189` passes X136/X137/X147
plus startup, with zero IDE errors and successful shutdown. Thus both new scenarios pass both
editors, while the initial failure remains evidence of the bug, not a clean full-catalog run.
X147 also completes an old reply just before restarting on the same EDT turn, so publication must
reject the retired connection even though the settings are unchanged.

P3's dependency/inlay refresh and P4's controlled stale-report checks now have automation. Broader
provider visual checks, physical Cancel-button selection and multiple-window interactions remain
manual. X130's selected pass and extension-free probe's `not-reproduced` result do not close its
earlier intermittent full-run Explorer failure.

For a realistic compiler workload, use `lang/scripts/compiler-workload.py` with the recipe in the
[reliability receipt](../../docs/errs-integration-plan.md#reliability-validation-receipt-2026-10-01).
It tests unsaved overlays, cancellation, outlines, hover, diagnostics and repeated server lifetimes
against `../platform`, preserving the checkout and retaining queue/API/heap traces. Its 30-cycle
baseline is separate from editor interaction, prolonged soak and release performance targets.


### X130 controlled Explorer-refresh reproduction (2026-10-01)

The diagnostic probe now uses the shared X130 nested layout. Compile the extension tests, then run
from `lang/vscode-extension`:

```bash
node scripts/run-vscode-tests.cjs --explorer-move-probe --refresh-during-move
```

The development extension is empty: Ecstasy must be absent and no language server is launched.
A public `onWillRenameFiles` participant waits for Refresh Explorer and supplies no edit. Select
both source directories, Cut, select the destination, and Paste. This reproduces the stale Cut-node
repaint exception on VS Code 1.140.0. The script still checks one native Undo, one Redo and all file
contents before rethrowing; a nonzero exit records the bug, not a passing acceptance case.
Omit `--refresh-during-move` for the ordinary control. Each attempt uses a fresh profile/workspace.

Normal X130 writes `X130-move-trace.json` with native command/window/file events and the real
compiler request/reply. The probe writes `move-trace.json` beside `results.json`. Event listeners
only observe; the explicitly requested probe participant is the sole forced interleaving.
Native test actions restore window focus through VS Code only when needed, without moving the
pointer. Already executed edits, Paste, Undo and Redo are never replayed.

`run-czIsNj` and `run-GSxFkH` independently reproduce the host exception without Ecstasy.
`run-LjhgZP` passes X118–X129, then fails X130 on that exception after all history/content checks.
X130 alone passes in `run-QLBWYV`; it is not evidence of a host fix. The
[full diagnosis](../../docs/errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01)
records the failed focus attempt, correction, upstream source and remaining repair task.


### Final L80 protocol checks (2026-10-01)

The current response/provider capability audit is complete; see the
[producer inventory](../../docs/errs-audit.md#l80-final-capability-contract-audit-2026-10-01).
The shared catalog remains 152 cases. This server-only change is validated through focused backend
and packaged stdio tests; earlier native receipts are preserved, not claimed as fresh runs.

For protocol acceptance, run `CapabilityContractTest`, `CapabilityNegotiationTest`,
`DiagnosticPresentationTest` and the packaged `XdkStdioTest` optional-signature/link cases. Clients
without tooltip support must receive the target without a tooltip, including after lazy resolve.
Clients without per-signature parameter support must still highlight the selected overload's
parameter through `SignatureHelp.activeParameter`. Pull clients must not inherit related-information
support from push diagnostics or from related-document support. Error text retains external source
identity when related information is suppressed. The tests include supported and reduced clients.

For the next normal editor acceptance run, retain the existing signature-help cases, X123 diagnostics
and X131 resolve checks in both drivers. These exercise the installed clients' negotiated behavior;
they cannot substitute for the reduced-client protocol tests. L81 manual checks and the X130 host
failure remain open. Exact test receipts are in the
[integration plan](../../docs/errs-integration-plan.md#l80-final-capability-contract-audit-2026-10-01).


### L81 detailed progress and upstream acceptance (2026-10-01)

X145 now checks that reference progress names its source. The editor supplies the Ecstasy
service/source name once; the operation title is simply `Finding references`, without another
prefix. Its ordinary VS Code run exercises the real SDK cancellation callback with 5,000 methods
and leaves the Notifications panel closed; only the explicit visible-control run opens that panel.
To exercise the visible button separately, build the compiler extension, then run with Node available:

```bash
./gradlew :lang:vscode-extension:assemble \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
cd lang/vscode-extension
node scripts/run-vscode-tests.cjs --playbook --cases=X145 --cancel-ui
```

When prompted, click **Cancel on “Finding references”**, not the independent workspace-check
notification. This manual workload uses the shared 20,000-method `uiMethods` setting so there
is time to act. Missing the click or completing before cancellation is a failure; the report records
`visible-control` separately from `native-token`. IntelliJ's driver activates the actual displayed
control through its accessibility action, bound to the target task and project, without moving the
pointer; it uses the normal 5,000-method workload. Both still verify subsequent hover, progress
removal, unsaved restart and old-PID exit.
The IntelliJ popup keeps the IDE's standard width; there is no Ecstasy-specific size override.
The 20,000-method IntelliJ diagnostic attempt exposed a 21-second bulk-replacement UI freeze;
that failed receipt and the separate L82 large-file investigation remain in the integration plan.

X146 now observes all five refresh families in VS Code and all providers actually negotiated by
IntelliJ; dependency changes must update the untouched consumer in both directions. Broader visual
provider presentation and simultaneous windows/project close remain P2–P4 manual checks.
The [L81 receipt](../../docs/errs-integration-plan.md#l81-progress-refresh-and-transport-checkpoint-2026-10-01)
retains failed attempts separately from passing refresh/restart cases. IntelliJ
`run-6551466376631163236` passes visible Cancel and restart in 7.2 seconds with zero IDE errors;
VS Code's manual visible-control mode still needs a successful click receipt.

All upstream compatibility issues are collected in [errs-upstream-issues.md](../../docs/errs-upstream-issues.md).
Source `TODO LSP4IJ:` / `TODO VSCODE:` markers carry matching UP IDs and removal conditions.
UP15's malformed-parameter classification and UP16's X130 host repaint remain defects; a passing
reader-recovery or Move/Undo/Redo check is not evidence that those defects are repaired.

### Large-file IntelliJ diagnostic (L82 / UP17)

The optional `CompilerPlaybookTest.largeFileEditing` probe reproduces the recorded UI freeze.
It is disabled by default, including in ordinary full playbook runs. The
[integration plan](../../docs/errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01)
contains its exact Gradle command, failed receipts and plain-document control measurements.
It can bring the disposable IDE forward without moving the pointer to install highlighting.
Do not edit that fixture during the measurement.

For a manual check, open its generated `parity/LARGE_FILE/LargeFile.x` and replace the original
20,000-method source with the first 5,000 methods, retaining the module's closing brace. Wait for
semantic colors before replacing: the compiler replying does not establish that the IDE applied
its highlights. Check typing, repaint and navigation responsiveness, then Undo and verify the
source and highlighting recover. Record UI and server times separately. A freeze or missing
decoration fails this acceptance; a passing bounded X145 does not override it. UP17 remains open.

Startup notification presentation: the bold service title appears once. Beneath it, Version,
Adapter and PID share one compact line with dimmed, theme-aware labels and normal value text.
Verify all three values are readable without truncation or an expansion arrow, then leave the
balloon untouched and check that it fades after eight seconds. The values remain available in
Notifications and the server log. This is presentation acceptance, not a compiler capability.

Large-file semantic response checks can run independently of native decoration: use the
`compiler-workload.py --semantic-methods 5000 20000` command in the
[L67 measurement receipt](../../docs/errs-integration-plan.md#l67l82-semantic-response-measurements-2026-10-01).
It checks exact reference/hint counts and server process exit after compilation, and records
one-line versus whole-file hints, tokens, hover, queue/API phases and transport timings. Keep cold
project-query compilation distinct from warmed responses. This does not replace the decorated
IntelliJ probe, native tooltip/theme checks or a prolonged editing/retention workload.

### Native project lifetime checks (L81)

These optional checks open disposable native windows and exercise real close/reopen behavior:

```bash
./gradlew :lang:intellij-plugin:testCompilerPlaybook --tests '*CompilerPlaybookTest.projectLifecycle' \
  -Plsp.adapter=compiler -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache
```

After building the VS Code extension, run from `lang/vscode-extension`:

```bash
node scripts/run-vscode-tests.cjs --project-lifecycle
```

Both reuse X145's shared workload with different types in the two projects. Watch one close while
references are pending, then reopen with a new compiler PID. The other must keep its unsaved text,
original PID, correct hover and reference count. IntelliJ uses two frames in one IDE and saves the
closing project. VS Code uses two separate normal application instances and restores the closing
window's dirty buffer through hot exit; this is not shared-Electron-process coverage. Its disposable
extensions directory contains the local extension and a test controller because development windows
intentionally have no persistent backup path. Neither driver kills a compiler to satisfy the checks.

IntelliJ trusts only the generated fixture path inside its disposable IDE, avoiding a blocking trust
dialog without changing normal trust defaults. The commands do not move the pointer. Passing receipts
are IntelliJ `run-3852388644684425549` and VS Code `project-lifecycle/run-zI8fT9`; see the
[lifetime record](../../docs/errs-integration-plan.md#l81-native-projectwindow-lifetime-batch-2026-10-01).
VS Code visible Cancel still needs a passing control-click receipt: `run-6JOJx7` failed because the
request finished before the UI tool could select the correct native window; no cancellation was faked.

Keep longer workload sessions bounded and announce their expected duration. The latest platform run
completed two 1,200-cycle sessions with preserved source hashes and compiler retirement, then was
deliberately stopped before the third session ran. Post-GC heap rose about 15 MiB per completed session;
its ownership remains an investigation, not completed memory acceptance. See the
[L82 receipt](../../docs/errs-integration-plan.md#l82-bounded-extended-workload-checkpoint-2026-10-01).
Combined suites and the full native catalogs are deferred to a separate checkpoint.
The final IntelliJ lifetime rerun `run-11163643850362008619` passes with zero IDE errors and checks
the reopened document before any edit is replayed; startup plus the lifecycle case take 38.3 seconds.

X105 extension (selected acceptance passes in both editors): replace the fixture with
`module AutoImports { Document value; }`, place the caret after `Doc`, and accept Document.
The whole identifier must be replaced once and
`import xml.Document;` inserted atomically; Problems clears. Repeat with Widget from ImportLibrary.
Both drivers use the same completion variants and exact expected resulting documents; their
earlier X105 passes establish only quick fixes.

L65 manual-module check (backend regression coverage passes; these manual actions remain available): in
`manualTests/src/main/x/jit/jit_tests/basic/mixinTests.x`, use Go to Implementation on
`t5.Root.self`; verify the written `Base.self` body and no generated redirect location. In
`delegationTests.x`, `ReportableAsString.showText` reaches `ReportableString.showText`; the
interface-valued delegate field does not invent another executable source body.

L62 manual-module check (backend regression coverage passes): rename `t5.Root.self` to `copySelf`
in mixinTests; Root, Base, Mix and the test5 call change together, while t6 stays unchanged. Undo restores exact
text. In condMixinTests rename the first `MixS.size` to `width`; only its corresponding test1 call
changes, not the independent t2 composition. A rename colliding with another default method must
be refused. Go to Definition on Element in a conditional-incorporation clause reaches the
corresponding mixin formal.


L64 closure additions (execution pending): X150 now includes a function argument lambda. Accept
`(arg1) -> TODO()`, verify that TODO() is selected, Tab reaches the end of the expression and Undo
restores the empty argument. This is a scaffold; replace TODO() before executing the program.
X151 adds unnamed val/var locals with string, integer and constructor initializers; accept the
syntax-derived name, verify the following source and Undo. X152 adds ordinary return expressions
with `thi` and `this.Ow`; verify the real enclosing instance, exact replacement and Undo.

L65 closure additions (execution pending):

| ID | Manual actions | Expected result |
| --- | --- | --- |
| X153 | Open each shared Dispatch.x variant. Use Go to Implementation on `box.value`, then `text.size()`, then the interface-valued delegate call. | The covariant property reaches its written getter; the conditional mixin reaches its written method. A runtime-only delegate has no guessed target. Both drivers check exact source positions; IntelliJ follows the native navigation action. |
| X154 | Open shared Access.x. Inspect tokens and highlight usages of the destructured `left`/`right`, incremented `box.value` and `values[index]` assignment. | Destructured variables and the incremented property are writes. The receiver and index expressions remain reads. Both drivers check exact positions and modification flags; IntelliJ also checks native token consumption and highlights. |


| Case | Manual action | Expected result |
| --- | --- | --- |
| X155 | Open shared Conditional.x. Rename the conditional Textual.size method to width, then Undo once. | The declaration and Box<String> call change together. String.size and Box<Int>.value stay unchanged. The source remains free of diagnostics, and Undo restores the exact original source. Both drivers implement the case; the current batch has not run yet. |

| X156 | Select the complete `input + step()` return expression in shared Extract.x and apply “Extract expression to local variable”. | An explicitly typed local is inserted immediately before the return. The selected expression appears once, the return reads the new local, and diagnostics remain clear. Undo/Redo/Undo restore the exact expected sources. Both drivers implement the case; this batch has not executed it yet. |

| X157 | Place the cursor on `value` in the shared Extract.x local and apply “Inline returned local variable”. | The initializer replaces the sole adjacent return read, its declaration disappears, diagnostics stay clear and Undo/Redo/Undo restore exact source. Both drivers implement this case; editor execution is pending. |

| X158 | Open the shared ImportLinks.x with LinkLibrary.x. Inspect module/type import links and linked editing at `Crate`. | Link ranges name `LinkLibrary` and `Crate`, both target the actual library source, and the alias links only its three written occurrences. Both drivers resolve links, check ranges and open the target source. Modifier-click appearance remains a manual observation; automated execution is pending. |

L67 index replacement check: with an unopened consumer, navigate a member from a host-indexed
binary. Replace its source index with another source location, remove the index while retaining
the binary, then configure editable source for that module. Navigation must follow the replacement,
be absent without an index, and prefer the configured source. Previously prepared hierarchy
handles must expire at each change. `XdkIndexLifecycleTest` now exercises this sequence through
one adapter (alongside graph removal); this is backend coverage, not a new editor receipt.
