# Tree-Sitter Functionality Status

> **Status: Updated for PR #424 branch state (2026-04-09)**
> This document replaces the older "missing functionality" plan. Most of the originally
> planned tree-sitter-only improvements are now implemented; the remaining gaps are much
> narrower and are called out explicitly below.

## Implemented in the current branch

The tree-sitter adapter now provides:

- Context-aware completions with distinct `DEFAULT`, `BODY`, `MEMBER`, `TYPE`,
  `IMPORT`, and `ANNOTATION` contexts.
- Workspace-index-backed type/import completion enrichment when the background
  index is ready.
- Read/write document highlight distinction (`READ` vs `WRITE`) for declarations
  and assignment targets.
- Resolved import document links with best-effort target URIs from the workspace
  index.
- Auto-import code actions for unresolved type names found in the workspace index.
- Doc-comment generation code actions for declarations.
- On-type formatting for Enter / `}` / `)` with configuration-aware indentation.
- Extra on-type handling for IntelliJ auto-close brace shapes (`{}` and skeleton
  block cases), plus an IntelliJ-side Enter repair fallback in the plugin.
- Semantic tokens enabled by default for the tree-sitter adapter.
- Selection ranges, linked editing ranges, folding ranges, code lenses, signature
  help, document symbols, same-file rename, and workspace symbol search.

## Still incomplete or intentionally limited

### Completions

- Member completions are still syntax-level, not semantic.
  They operate on enclosing AST/class structure, not resolved receiver type.
- No snippet completions are currently emitted.
- Cross-file semantic member completion still requires a future compiler-backed
  adapter.

### Definitions / References / Links

- Import/document-link target resolution is best-effort by simple-name lookup in
  the workspace index. It is not yet fully qualified-name aware.
- Same-file navigation is solid; cross-file navigation depends on workspace index
  quality, not semantic compiler resolution.

### Code actions

- Auto-import is implemented, but only for names the workspace index can see.
- There is still no semantic quick-fix layer for compiler diagnostics.
- Organize Imports exists, but import insertion/ordering is still syntax-level.

### Formatting

- LSP on-type formatting is implemented and heavily tested.
- IntelliJ does not reliably route the first Enter-after-`{` path through LSP, so
  the IntelliJ plugin now includes a local Enter repair that respects IntelliJ code
  style settings.
- Document/range formatting exists and is AST-aware, but a fully green round-trip
  across all editors still depends on the `workspace/configuration` path being
  stable in every client.

### Workspace indexing

- Background indexing and workspace symbol search are implemented.
- Incremental re-indexing is still conservative and not yet fully minimal.

## What this PR added beyond the older tree-sitter plan

This branch did more than just "finish the 9 tree-sitter features". It also added:

- Shared adapter logging with much richer request/decision tracing for completion,
  code actions, definition, semantic tokens, document changes, and on-type formatting.
- Formatting config round-trip tests covering LSP `workspace/configuration`.
- IntelliJ plugin-side Enter handling for the brace/empty-block indentation path
  that LSP4IJ does not reliably send through `textDocument/onTypeFormatting`.
- Manual test-plan updates for semantic tokens vs TextMate behavior, stale sandbox
  recovery, and IntelliJ-specific indentation cases.

## Remaining work worth tracking separately

- Compiler-backed semantic completions, navigation, rename, and quick fixes.
- Fully qualified import resolution in document links / code actions.
- Cleaner cross-editor formatting config propagation.
- More incremental workspace re-indexing.
- Better automated coverage for IntelliJ-side editor behavior beyond pure LSP tests.
