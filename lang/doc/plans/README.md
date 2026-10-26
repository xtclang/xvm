# Ecstasy Language Support Plans

## Active Plans

| Document | Description |
|----------|-------------|
| [idea-specific](./idea-specific.md) | IntelliJ IDEA plugin enhancements (XDK SDK, run configs, templates) |
| [vscode-specific](./vscode-specific.md) | VS Code extension enhancements (snippets, settings, DAP) |
| [plan-embedded-execution](./plan-embedded-execution.md) | Embedded build, Run and debugging (R1–R8, and the debugger features beyond them, R9–R13); first Run/Build delivery in both IDEs |
| [plan-bounded-evaluation](./plan-bounded-evaluation.md) | Decided and done, tiers 1 and 2: bounded evaluation in the debugger, by tier (paths, pure expressions, code on the stopped fiber with a deadline), for the Debug Console, watches, conditions, logpoints and R10 |
| [lsp-feature-tiers](./lsp-feature-tiers.md) | Historical research-fork feature tiers; see the IDE integration plan for current status |
| [plugin-stability-followups](./plugin-stability-followups.md) | Deferred follow-ups from `lsp/vscode2`: LSP/DAP server source review and IntelliJ Platform-fixture (Tier B) tests. Resolved: tree-sitter download race, `runCode` CLI guard, CI wiring for the headless test, IntelliJ-side naming + verifier sweeps |

## Completed Plans

| Document | Description |
|----------|-------------|
| [plan-tree-sitter](./plan-tree-sitter.md) | Tree-sitter integration — all phases complete |
| [existing-tree-sitter-functionality-missing](./existing-tree-sitter-functionality-missing.md) | Current tree-sitter implemented-vs-remaining gap summary |
| [document-formatting-plan](./document-formatting-plan.md) | Canonical current-status doc for formatting |

## Retained Design Notes

| Document | Description |
|----------|-------------|
| [formatting-plan](./formatting-plan.md) | Historical detailed design/rationale for on-type formatting and config architecture |

## Summary Docs

| Document | Description |
|----------|-------------|
| [plan-ide-integration](./plan-ide-integration.md) | What's implemented across the language tooling stack |
