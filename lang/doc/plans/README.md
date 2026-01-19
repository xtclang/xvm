# XTC Language Support Plans

This directory contains implementation plans and status documentation for XTC language tooling.

## Documents

| Document | Description |
|----------|-------------|
| [PLAN_IDE_INTEGRATION](./PLAN_IDE_INTEGRATION.md) | **What's implemented and what's next** - summary of current state |
| [PLAN_TREE_SITTER](./PLAN_TREE_SITTER.md) | Tree-sitter grammar development and coverage |

## Quick Start

**To understand what's built:** Read [PLAN_IDE_INTEGRATION](./PLAN_IDE_INTEGRATION.md)

**To work on the tree-sitter grammar:** Read [PLAN_TREE_SITTER](./PLAN_TREE_SITTER.md)

## Compiler Integration

For detailed plans on compiler modifications to enable full semantic LSP support, see the research repository:

**[xtc-language-support-research](https://github.com/xtclang/xtc-language-support-research)**

That repository contains:
- Architecture analysis of the XTC compiler
- Quick path: State externalization approach
- Parallel path: Modern rewrite approach (recommended)
- Component-by-component implementation plans
