# Plugin stability follow-ups

Items surfaced during the `lsp/vscode2` work that were explicitly deferred to keep the PR scoped. None of them are blockers, but they're worth tracking so they don't get lost.

## IntelliJ plugin

### 1. Source files not yet audited for "XTC → Ecstasy" naming

**Status (2026-10-16):** audited; no user-facing "XTC" remains in the five files below.
`XtcIntelliJLanguage` displays "Ecstasy" and the run producer names configurations `Run <module>`.
The other literals are log messages and the `/icons/xtc.svg` resource path; "XTC" survives only in KDoc.

Scope of the naming pass in this branch was limited to files touched during the `PluginManagerCore` fix plus run-config / wizard / TextMate provider. The following weren't opened, but a future pass should:

- `XtcEnterHandlerDelegate.kt` (auto-indent on Enter)
- `XtcCommenter.kt`
- `XtcRunConfigurationProducer.kt`
- `XtcIconProvider.kt`
- `XtcIntelliJLanguage.kt`

Likely candidates: user-facing labels, dialog messages, exception messages. Rule: user-facing names say "Ecstasy" — including the server, shown as "Ecstasy Language Server" by LSP4IJ, the VS Code output channel, the IntelliJ notification group, and diagnostics (source `Ecstasy`). Identifiers stay `xtc`, because settings, tasks, launch configurations, themes and keybindings refer to them: the VS Code language ID, `xtc.*` settings and commands, the `xtc` task, debugger and problem-matcher names, VS Code task `source` (users reference tasks as `xtc: Build`), the `.xtc` module extension, the `xtc` command-line tool, `source.xtc` grammar scopes, and `xtc` Markdown code fences.

## Out of scope (mentioned for completeness)

### 2. `lang/lsp-server/` / `lang/dap-server/` source quality

Not audited as part of this branch. Both are covered by `:lang:lsp-server:test` and the per-feature manual test plan, so there's no immediate quality signal that something is wrong. Worth a focused review session on its own.

### 3. Cross-IDE integration test parity — Tier B (Platform fixtures)

Tier A — the manifest / live-template / bundled-resource parity tests — landed in this branch (`PluginManifestTest`, `LiveTemplateRegistrationTest`, `BundledResourcesTest`). What remains is Tier B: tests that need IntelliJ Platform test fixtures (`BasePlatformTestCase` / `LightProjectDescriptor`) to verify *runtime* registration of extension points, not just manifest declarations.

Candidate Tier B coverage:

* **File-type registration runtime test** — load the plugin into a test IDE instance, then assert `FileTypeManager.getFileTypeByExtension("x")` returns the Ecstasy/TextMate file type. Counterpart to VS Code's `extension.test.ts`'s runtime languageId check.
* **LSP startup smoke test** — actually spin up LSP4IJ + the LSP server JVM and send a hover. Heavyweight; the IntelliJ Platform test fixtures aren't designed for this.

Effort: half day to set up the platform-test plumbing (BasePlatformTestCase / fixtures, IntelliJ test dependencies), then the tests themselves are modest. Defer to its own branch.

## Resolved during `lsp/vscode2`

These were on the deferred list at the start of the branch but were resolved in-branch — kept here briefly as historical context.

* **Tree-sitter `extractTreeSitterSource` race** — `downloadTreeSitterSource` was missing an `outputs.file(...)` declaration so Gradle's UP-TO-DATE check didn't invalidate when the tar.gz went missing externally. Fixed by declaring the output and dropping the redundant custom `onlyIf`. Verified by deleting the tar.gz between builds: download correctly re-runs.
* **`:lang:intellij-plugin:verifyPlugin` clean run** — verifier reports `Compatible` against both IU-261.25134.12 and IU-262.6228.19 with zero internal-API / deprecated / experimental-API hits after the `PluginManagerCore → PluginManager.findEnabledPlugin` migration.
* **`:lang:vscode-extension:runCode` PATH preflight** — the task now resolves the `code` CLI against `$PATH` before invoking it and emits a clear "open Cmd Palette → Shell Command: Install 'code' command in PATH" message when missing, instead of letting `Exec` fail with `exit code 127`.
* **`:lang:vscode-extension:testVscodeExtension` wired into CI** — added to `.github/workflows/commit.yml`'s lang validation block under the same `paths-filter: lang/**` gate as the tree-sitter / LSP tests / IntelliJ `verifyPlugin`. Installs `xvfb` on the Ubuntu runner only if not already present (guarded by `command -v xvfb-run`); the cross-platform launcher script auto-detects and wraps the test runner.
