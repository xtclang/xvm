# Part 1: Introduction

This section provides the high-level context for understanding the XTC compiler's architectural challenges when building tooling.

## Documents

| Document | Purpose |
|----------|---------|
| [Executive Summary](./executive-summary.md) | Start here. Explains what we're building, why the current architecture blocks it, and the two-track solution |
| [No Excuse for Ignoring Modern Java](./no-excuse.md) | Context on why these architectural problems shouldn't exist in a 2020s Java codebase |

## Key Takeaways

1. **The Goal**: Build LSP support and other tooling (Gradle plugins, etc.) for XTC
2. **The Problem**: The compiler was designed for batch mode, not interactive/concurrent use
3. **The Solution**: Build an adapter layer that extracts data into clean structures
4. **The Timeline**: Working LSP in 8-12 weeks using the adapter approach

## Next Steps

After reading this section:
- If building tooling: Go to [Part 3: The LSP Solution](../3-lsp-solution/)
- If fixing the codebase: Go to [Part 2: Architectural Problems](../2-problems/)
