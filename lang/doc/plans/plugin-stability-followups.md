# Plugin stability follow-ups

Items surfaced during the `lsp/vscode2` work that were explicitly deferred to keep the PR scoped. None of them are blockers, but they're worth tracking so they don't get lost.

## Build / tooling

### 1. `:lang:tree-sitter:extractTreeSitterSource` race

**Symptom:** intermittently fails with `FileNotFoundException: tree-sitter-0.26.X.tar.gz` when `build/tree-sitter-source/` is in an inconsistent state (tar.gz missing but other files present).

**Root cause:** `downloadTreeSitterSource` task has an explicit `onlyIf { !File(destPath).exists() }` predicate that's redundant with the `Download` plugin's own `overwrite(false) + onlyIfModified(true)` settings, but interacts badly with the configuration cache when the tar.gz has been deleted underneath the cache.

**Fix:** remove the explicit `onlyIf` from `downloadTreeSitterSource` in `lang/tree-sitter/build.gradle.kts:170` (current state). Let the `Download` plugin's own up-to-date checking govern. Tested locally — removing the predicate makes the race disappear.

**Effort:** ~3 lines. Trivial.

### 2. `:lang:vscode-extension:runCode` assumes `code` is on PATH

**Symptom:** the task hardcodes `commandLine("code", "--extensionDevelopmentPath=...", fixturesPath)`. On a machine where the `code` CLI isn't installed (it's an optional VS Code "shell command" install), the task fails opaquely with `exit code 127`.

**Fix:** add a one-shot existence check via `providers.exec` and emit a clear error pointing at VS Code's "Install 'code' command in PATH" command palette entry.

**Effort:** ~5 lines.

### 3. Wire `:lang:vscode-extension:testVscodeExtension` into CI

**Current state:** opt-in only — not attached to `:check`. Headless wrapper auto-detects Linux + missing DISPLAY and prepends `xvfb-run` when available; falls back with a clear warning otherwise.

**To wire into CI:** add an `apt-get install -y xvfb` step in `.github/workflows/commit.yml`'s lang validation block, then add `:lang:vscode-extension:testVscodeExtension` to the task list. Adds ~30s to lang-validation runs (mostly the first-time VS Code download — cached after).

**Effort:** ~10 lines of YAML.

## IntelliJ plugin

### 4. `:lang:intellij-plugin:verifyPlugin` post-`PluginManagerCore` audit

After replacing the `PluginManagerCore.getPlugin` internal-API usages with `PluginManager.findEnabledPlugin`, the verifier should now report zero internal-API warnings. Run `:lang:intellij-plugin:verifyPlugin` after a future release-grade build to confirm; surface any further hits in this list.

### 5. Source files not yet audited for "XTC → Ecstasy" naming

Scope of the naming pass in this branch was limited to files touched during the `PluginManagerCore` fix plus run-config / wizard / TextMate provider. The following weren't opened, but a future pass should:

- `XtcEnterHandlerDelegate.kt` (auto-indent on Enter)
- `XtcCommenter.kt`
- `XtcRunConfigurationProducer.kt`
- `XtcIconProvider.kt`
- `XtcIntelliJLanguage.kt`

Likely candidates: user-facing labels, dialog messages, exception messages. Rule remains the same as in `vscode-specific.md`: product entity → "Ecstasy", server product names ("XTC Language Server", "XTC DAP server") and internal identifiers → stay.

## Out of scope (mentioned for completeness)

### 6. `lang/lsp-server/` / `lang/dap-server/` source quality

Not audited as part of this branch. Both are covered by `:lang:lsp-server:test` and the per-feature manual test plan, so there's no immediate quality signal that something is wrong. Worth a focused review session on its own.

### 7. Cross-IDE integration test parity

VS Code now has 7 integration tests (file association, JAR bundling, activation surfaces, LSP startup, snippet expansion). IntelliJ has 1 (`LspServerJarResolutionTest`). The disjoint coverage is fine for now — each test guards the failure modes specific to its IDE — but bringing the IntelliJ side up to similar surface coverage is a worthwhile follow-up.
