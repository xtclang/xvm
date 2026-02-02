# XTC Language Support Plans

This directory contains implementation plans and status documentation for XTC language tooling.

## Documents

| Document | Description |
|----------|-------------|
| [PLAN_IDE_INTEGRATION](./PLAN_IDE_INTEGRATION.md) | **What's implemented and what's next** - summary of current state |
| [PLAN_TREE_SITTER](./PLAN_TREE_SITTER.md) | Tree-sitter grammar - main plan and roadmap |
| [PLAN_OUT_OF_PROCESS_LSP](./PLAN_OUT_OF_PROCESS_LSP.md) | Out-of-process LSP server architecture |
| [tree-sitter/](./tree-sitter/) | Tree-sitter implementation details and task docs |

## Quick Start

**To understand what's built:** Read [PLAN_IDE_INTEGRATION](./PLAN_IDE_INTEGRATION.md)

**To work on the tree-sitter grammar:** Read [PLAN_TREE_SITTER](./PLAN_TREE_SITTER.md) and [tree-sitter/](./tree-sitter/)

## Compiler Integration

For detailed plans on compiler modifications to enable full semantic LSP support: *Internal documentation*

Topics covered:
- Architecture analysis of the XTC compiler
- Quick path: State externalization approach
- Parallel path: Modern rewrite approach (recommended)
- Component-by-component implementation plans
