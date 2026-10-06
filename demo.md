# Compiler-adapter demo in `../platform`

Use `lagergren/errs` in this repository. This is a manual tour of implemented feature families,
not a claim that every XTC construct or LSP operation is complete. Checked against platform
`8df5449` on 2026-10-06; use the named code anchors below rather than fixed line numbers.

**Start with `auth` and `githubCLI` for a short tour.** The full eleven-module graph also compiles
cleanly through the packaged server after configuring platformUI's generated resources below:
49 document reports, no diagnostics. The source-location/resource and presentation fixes are tracked in
[PLAT1–PLAT3](docs/errs-integration-plan.md#platform-demo-blockers-2026-09-29).

## Launch

From this repository's root, with `../platform` already checked out:

```bash
./gradlew :lang:intellij-plugin:runIde \
  --args="\"$PWD/../platform\"" \
  -Plsp.adapter=compiler -Pxtc.intellij.semanticTokens=true \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

This builds the plugin and bundled compiler/XDK, publishes the current local XDK/plugin artifacts
needed by consumer builds, and launches the pinned IntelliJ sandbox with platform as its project.
No separate `installDist`, plugin installation, XDK download or Ultimate subscription is needed.
Keep the terminal open; close the sandbox IDE when finished. This does not start the UI test driver.

Before opening `.x` files, open **Settings → Languages & Frameworks → Ecstasy Compiler**.
Select **Source modules** in the left-hand list. Uncheck **Use Gradle model or automatic source
discovery**, remove any old rows, and **Add module** twice:

| Module | Root URI or relative path | Dependencies |
|---|---|---|
| `auth.xqiz.it` | `auth/src/main/x/auth.x` | leave empty |
| `githubCLI.xqiz.it` | `githubCLI/src/main/x/githubCLI.x` | leave empty |

Click **Apply**, then **OK**. Roots are relative to the opened platform project. Bundled XDK
modules resolve automatically; do not enter them as source dependencies. Open the files below;
confirm the Language Servers tool window shows Ecstasy running with the compiler adapter.
Open **View → Tool Windows → Problems**, and **Structure** beside the editor.
Source-graph changes apply live; wait for analysis to finish. Transport/JVM changes separately
require a restart. **Libraries and sources** manages ordered binary paths and matching read-only
source attachments; **Build import** imports evaluated Gradle inputs or prepares generated resources.

Use **Find Action** (`⇧⌘A` on the macOS keymap) and the action names below; this avoids keymap
variations. Put the caret *inside the named identifier*. Restore each edit with **Undo** before
the next example. IntelliJ may save automatically; finish by checking `git -C ../platform diff`.

## Real-code tour

Paths below are relative to platform. Open these three files:

- **R:** `githubCLI/src/main/x/githubCLI/Repositories.x`
- **G:** `githubCLI/src/main/x/githubCLI.x`
- **O:** `auth/src/main/x/auth/OAuthProvider.x`

| Show | Exact action | What to look for |
|---|---|---|
| Outline, folding and semantic colors | In **R**, open Structure; select `listRepositories`. Collapse/expand its body using the gutter. | Real class/method/property ranges; names, comments and literals colored. |
| Cross-file definition | In **R**'s `listRepositories`, invoke **Go to Declaration or Usages** on `sendRequest`. | Jumps to `GithubGateway.sendRequest` in **G**. IntelliJ's usual declaration action invokes LSP definition here. |
| Cross-file references | At that declaration in **G**, invoke **Find Usages**. | Six calls in **R**, including `listRepositories`, `setRepository`, `readme`, `listDir`, `listBranches` and `setBranch`. Close **R** first to show unopened-file lookup. |
| Call hierarchy | On `sendRequest` in **G**, invoke **Call Hierarchy**; switch between callers and callees. | Incoming methods above; outgoing `send` and `createRequest`. This is statically selected calls, not runtime tracing. |
| Type hierarchy | On the top-level `OAuthProvider` name in **O**, invoke **Type Hierarchy** and select subtypes. | `Amazon`, `Apple`, `Github`, `Google`, `Unknown`. Select `Amazon` and inspect its supertype. |
| Implementations and property families | On the first `@RO String authorizationUrl` in **O**, invoke **Go to Implementation(s)**. | Written overrides in the provider subclasses. Open a target from the chooser. |
| Workspace symbols | Invoke **Go to Symbol**, search `OAuthProvider`, then `sendRequest`. | Compiler-backed symbols with file locations, beyond the current editor. |
| Bundled XDK type sources | In **G**, find `HttpStatus status = response.status;`. On `response`, invoke **Go to Type Declaration**. | Opens the bundled `web/ResponseIn.x` declaration read-only. On `String` in a declaration, normal definition lookup also reaches bundled source. |
| Read/write highlights | In **R**, place the caret on `repositoryName` in `repositoryName = repository;`. | The declaration, write and reads in URL interpolations are related by identity. |
| Inlay hints | In **G**, inspect `readLine(..., "xtclang")`, `new Uri(...)` and calls in `send`. Enable LSP inlay hints in editor settings if hidden. | Parameter labels; inferred-type hints appear where inference supplies a type. The packaged request returns 26 hints in this file. |
| URLs and source links | In **O**, follow the RFC 6749 comment's HTTPS link. Follow `import json.JsonObject;` at the top of **R**. | Opens the RFC or the resolved read-only library source. Compiler-backed module/type/alias and wildcard-container links require current, resolved source facts. |
| Expand selection | In **G**, put the caret inside `createRequest(method, group, path, content)`; invoke **Extend Selection** repeatedly, then **Shrink Selection**. | Expression, enclosing call and larger AST ranges. |
| Run entry point | Open the module declaration at the top of **G** and inspect its **Run** lens. | Existing client run integration. Do not execute this example during the tour: it asks for credentials and contacts GitHub. Reusable embedded execution and DAP are still planned. |

Definition, references, subtype/property targets, incoming calls, bundled type navigation, highlights,
inlay hints, links, outline and folds above were checked through the packaged server. This platform
session has **not** been driven through native IntelliJ; the shared playbook has separate native
receipts. The PLAT3 packaged recheck now shows the correct use-site hover, four signature parameters
with `group` active, and `trim` completion before existing parentheses on the chained receiver.

The October 6 recheck uses platform `8df5449`: all eleven modules produce 49 clean diagnostic
reports. It confirms six `sendRequest` references/incoming callers, five OAuth subtypes, four
`authorizationUrl` implementations, 26 hints in **G**, `status` completion and a four-parameter
incomplete signature with `group` active. The deliberate `Int value` mismatch reports compiler
errors; restoring the original overlay clears them. Rename returns exactly three versioned edits.
All platform source hashes are unchanged, and the server shuts down normally.

That check exposed and fixed a graph-navigation bug: implicit directories such as `common/tools`
contribute synthetic `package tools {}` syntax with no filename. Its semantic facts remain in the
joined graph, but it cannot itself be a file target. The minimal compiler-adapter regression and
shared X117 now cover references through such directories. Local request/results evidence is in
`lang/lsp-server/build/reports/platform-demo/2026-10-06-final/`; this remains packaged-server
evidence, not a claim that this entire manual tour ran through IntelliJ's menus.

## Completion, signature help and live repair

Work in **G**. Undo after each numbered experiment; `|` below marks the caret, not text to insert.

1. In `send`, change `response.status` to `response.st|`; invoke **Basic Completion**, accept
   `status`. Show that only the member token changes and the diagnostic clears.
2. In `sendRequest`, change its return statement to
   `return send(createRequest(method, gr|, path, content));`. Invoke **Basic Completion** and
   **Parameter Info**. The compiler offers `group: String`; signature help selects parameter
   `group`, with `content` marked optional. Accept `group` and show Problems clear.
3. Replace that return statement with `return send(createRequest(method, |);`.
   Invoke the same actions. Compatible values include `group` and `path`; signature help still
   identifies `group` despite the missing enclosing delimiter. The source remains erroneous until
   repaired: Undo to the original and watch Problems clear.
4. In `readLine`, shorten the parameter type to `Str| defaultValue`. Complete `String`.
   This demonstrates type-header completion rather than variable-name completion.
5. In `readLine`, temporarily replace `String value =` with `Int value =`. Inspect the compiler
   error in Problems and jump to its source span. Restore it and watch the error disappear without
   a manual build. Error severity, compiler code and message come from the compiler; pull versus
   push transport does not create a second set of Problems entries.
6. In the same method, rename local `value` to `answer` using **Rename**. Inspect all three local
   occurrences, then **Undo** and **Redo**, finally Undo to restore. This is a small refactoring
   example; whole-graph public/module renames have stricter proof and source-graph requirements.

Member/type-header completion and argument recovery in steps 1–4 were checked against this real source. For constructor,
generic/compound-header, literal, lambda and specialized-constructor variants, use the exact
programs in [the shared manual playbook](lang/doc/manual-test-plan.md#xdkadapter-playbook).
They are implemented bounded cases, not a promise of recovery for every malformed expression.

## Actions and presentation: short temporary edits

These are manual follow-on demonstrations, not yet verified in this platform IDE session.
Make one edit at a time and Undo it afterward.

- **Organize imports:** add an unused `import ecstasy.collections.HashMap;` beside **R**'s imports;
  invoke **Optimize Imports** / the LSP **Organize imports** action. Show the compiler-proven
  unused import removal. Sorting applies to contiguous import groups.
- **Implement/override:** inside **G**'s module, before its final `}`, add the following:

  ```xtc
  interface DemoNamed {
      String name();
  }
  class DemoGreeter implements DemoNamed {
  }
  ```

  Put the caret on `DemoGreeter`, invoke **Show Context Actions**, and choose the offered
  **Implement** action for `name`. Inspect the `@Override` method and `TODO()` body, then Undo.
  Existing-member override generation and unresolved public-type imports use the same action menu;
  the precise supported/refused variants are [X105/X122 in the playbook](lang/doc/manual-test-plan.md).
- **Formatting:** remove the leading indentation from a few lines inside **G**'s `readLine` body;
  select those lines and invoke **Reformat Code**. Repeat for the whole file, then Undo. Press Enter
  inside a block to show on-type indentation. The formatter preserves token spellings.
  Code Style controls indentation, tabs, spacing, blank lines and bounded
  expression/list wrapping at the right margin; this is not a grammar-complete pretty-printer.
- **Hover:** use **Quick Documentation** on `sendRequest` in **R**; it should show that method's
  substituted signature, not the enclosing `listRepositories`. On narrowed `repo` after the
  `JsonObject` assertion, **Go to Type Declaration** should reach bundled `Map.x`.
- **Linked editing:** the server returns the three ranges for local `value` in **G**'s `readLine`.
  Automatic linked typing depends on the client UI; **Rename** is the dependable manual entry point.
  Do not confuse ordinary occurrence highlighting with linked editing.
- **Separate declaration lookup:** the server also implements `textDocument/declaration`, including
  inherited written contracts. The normal IntelliJ definition action is not independent proof of
  that request; use shared X4 for its dedicated client/protocol checks.

## Full platform tour

Keep the first two graph rows and add these rows in Ecstasy Compiler settings:

| Module | Root relative to platform | Source dependencies (comma separated) |
|---|---|---|
| `common.xqiz.it` | `common/src/main/x/common.x` | `auth.xqiz.it` |
| `stub.xqiz.it` | `stub/src/main/x/stub.x` | |
| `challenge.xqiz.it` | `challenge/src/main/x/challenge.x` | `common.xqiz.it` |
| `host.xqiz.it` | `host/src/main/x/host.x` | `common.xqiz.it, challenge.xqiz.it, stub.xqiz.it` |
| `proxy_manager.xqiz.it` | `proxy/src/main/x/proxy.x` | `common.xqiz.it` |
| `platformDB.xqiz.it` | `platformDB/src/main/x/platformDB.x` | `common.xqiz.it` |
| `platformCLI.xqiz.it` | `platformCLI/src/main/x/platformCLI.x` | `auth.xqiz.it, common.xqiz.it` |
| `platformUI.xqiz.it` | `platformUI/src/main/x/platformUI.x` | `common.xqiz.it, challenge.xqiz.it, auth.xqiz.it` |
| `kernel.xqiz.it` | `kernel/src/main/x/kernel.x` | `common.xqiz.it, platformDB.xqiz.it` |

For the platformUI row, set **Resource roots** to `["platformUI/gui/dist"]` before applying.
Its Gradle build adds that generated directory with `sourceSets.main.resources.srcDir(guiDistDir)`;
the embedded `Directory:/spa` is `platformUI/gui/dist/spa`. It already existed in the checked demo
workspace. If missing, build it from platform's root with `./gradlew :platformUI:buildGui` first.
Other modules can leave this column blank: the compiler finds their conventional
`src/main/resources` directories. This explicit graph keeps the tour independent of build import.
For build-owned inputs, use **Build import → Refresh Gradle model**, then inspect **Show effective
paths and import details**. **Prepare generated resources** runs the model's preparation tasks;
it supports cancellation and refuses overlapping imports. Manual source/library overrides stay in
effect after import; **Reset to build model** restores model/discovery ownership. Paths use
project-root-relative URI syntax here; no assets need copying into `src/main/x`.

Then demonstrate these real relationships, with all roots clean before trying rename/actions:

1. `common/src/main/x/common/HostManager.x`: implementations of the interface and
   `ensureAccountHomeDirectory` lead to `host/src/main/x/host/HostManager.x` across modules.
2. `common/src/main/x/common/AppHost.x`: subtype hierarchy includes `WebHost`, `DbHost`,
   `KernelHost`; inspect their `activate`, `deactivate` and `close` overrides.
3. In host's `HostManager.checkLoad`, complete `stats.` and inspect its `CircularBuffer<Int>`
   type. Navigate to `common/src/main/x/common/utils/CircularBuffer.x`; inspect substituted
   generic element types, `add`, and incoming calls to `indexFor` from `getElement`/`setElement`.
4. Make and undo a signature mismatch in the common interface; show diagnostics updating in its
   host consumer. Preview a supported family rename, inspect all proposed files, apply and Undo.
   Never count an empty/refused result as completed refactoring support.

The [adapter feature matrix](lang/doc/plans/plan-ide-integration.md#adapter-capability-matrix) and
[remaining task list](docs/errs-integration-plan.md#full-compiler-lsp-completion-checklist)
record the boundaries. Extract/inline/safe-delete have bounded compiler-proven implementations;
use the shared playbook's exact examples to demonstrate those proof/refusal boundaries. A full
pretty-printer, runtime call graph, reusable Run worker and debugger remain outside this tour.

Use **Toggle Ecstasy Language Server Log** to show/hide the docked log, or **Export Ecstasy Server
Logs** for a support ZIP. **Ecstasy Server Runtime and Logs** configures Java, heap, VM options and
retention; **Ecstasy Language Service** controls live presentation/transport preferences. The export
and effective-state view identify the actual log directory and server process. Queue size, ordered
job names, compile/API times and reply latency are traced; no platform application is executed by
the demo. Use the **Run** lens only when intentionally ready for that application's side effects.
