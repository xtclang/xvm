# Tree-sitter LSP Functionality

This document now focuses on the **tree-sitter-specific** capabilities and limitations.
It is no longer the canonical full adapter matrix; that information lives in:

- [`../../doc/plans/PLAN_IDE_INTEGRATION.md`](../../doc/plans/PLAN_IDE_INTEGRATION.md)
- [`../../doc/plans/existing-tree-sitter-functionality-missing.md`](../../doc/plans/existing-tree-sitter-functionality-missing.md)

## What tree-sitter provides well

Tree-sitter is responsible for the syntax/structure layer used by the default LSP adapter:

- fast incremental parsing
- robust parsing of incomplete/invalid code
- document symbols / outline
- same-file navigation and references by syntax/name
- selection ranges and folding ranges
- syntax-aware completion context classification
- syntax-aware code actions such as organize imports, auto-import candidates, and doc comments
- semantic-token classification from AST context
- on-type formatting and document/range formatting

In this repo, the concrete implementation is the default `TreeSitterAdapter` in
[`lang/lsp-server`](../../lsp-server/).

## What tree-sitter still cannot do alone

Tree-sitter does not provide semantic compilation. Without a compiler-backed adapter it cannot:

- resolve exact receiver types across files
- compute truly semantic member completions
- resolve declarations/implementations with full type awareness
- produce semantic diagnostics
- provide compiler-grade refactorings and quick fixes

So the tree-sitter adapter remains a high-quality **syntax/structure** engine, not a full
semantic engine.

## Current branch-specific reality

Compared to the older version of this document, the current branch now includes:

- context-aware completions
- workspace-index-backed auto-imports and import links
- read/write document highlight distinction
- semantic tokens enabled by default
- richer logging around completion, code actions, definition, semantic tokens, and formatting
- IntelliJ-side Enter repair to complement LSP on-type formatting where the editor does not
  reliably send the first Enter-after-`{` through LSP

## Canonical references

Use these documents instead of duplicating status here:

- [`../../doc/plans/PLAN_TREE_SITTER.md`](../../doc/plans/PLAN_TREE_SITTER.md)
  - implementation history and architecture
- [`../../doc/plans/PLAN_IDE_INTEGRATION.md`](../../doc/plans/PLAN_IDE_INTEGRATION.md)
  - current cross-adapter feature matrix
- [`../../doc/plans/existing-tree-sitter-functionality-missing.md`](../../doc/plans/existing-tree-sitter-functionality-missing.md)
  - current tree-sitter implemented vs remaining gaps
