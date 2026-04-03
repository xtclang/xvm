# Implementation Plan: AST-Aware Document Formatting for XTC

## Status: Steps 1–4 DONE — Core formatter implemented and tested

## Overview

This plan describes how to upgrade the XTC LSP server's `textDocument/formatting` and
`textDocument/rangeFormatting` handlers from trivial cleanup (trailing whitespace, final
newline) to full AST-aware re-indentation using tree-sitter. When the user presses
`Ctrl+Alt+L` (IntelliJ) or `Shift+Alt+F` (VS Code), the formatter will fix indentation
across the entire file — or a selected range — to match XTC conventions.

## What Was Implemented

### Core: `computeLineIndent` + `countIndentDepth` (structural depth)

The document formatter uses **structural depth counting** — it counts the number of
`indentParentTypes` ancestors in the AST to determine the nesting level. This is critical
because the formatter must work on badly-misindented files where reference lines have
wrong indentation; reading indent from the source would propagate errors.

```kotlin
private fun countIndentDepth(node: XtcNode): Int {
    var depth = 0
    generateSequence(node) { it.parent }.forEach { n ->
        if (n.type in indentParentTypes) depth++
    }
    return depth
}
```

Indent for a line = `countIndentDepth(node) * config.indentSize`, with special rules for
closing delimiters, case labels, continuation lines, and comments.

### Overrides: `formatDocument` + `formatRange`

Both override the base `AbstractXtcCompilerAdapter` implementation. When a parsed tree
exists, they walk every line (or the requested range), call `computeLineIndent`, and emit
edits where actual != desired. Falls back to the base (trailing whitespace + final newline
only) when no tree is available.

### Always-insert final newline

The formatter unconditionally ensures every XTC file ends with `\n`, regardless of the
`insertFinalNewline` LSP option. This matches the XTC code style rule ("ALWAYS add a
newline at the end of every file").

### Design choice: case labels at switch indent

In XTC, `case` labels are formatted at the **same indent as the `switch` keyword**, not
indented one level deeper (unlike the XDK standard library which currently uses +4).
This is an intentional style decision.

## Current State (Before This Work)

The `AbstractXtcCompilerAdapter.formatContent()` did only:

1. **Trailing whitespace removal** — strip spaces/tabs after the last non-whitespace character
2. **Final newline insertion** — ensure the file ends with `\n` (when option is set)

Meanwhile, `TreeSitterAdapter.handleEnter()` already contained a complete indentation engine
for on-type formatting. The document formatter now applies similar rules across every line.

## XTC Indentation Rules (Reference)

All rules derived from `lib_ecstasy/` source conventions:

| Context | Rule | Example |
|---------|------|---------|
| Module/class/interface body | +indent from declaration | `module Foo {\n    ...` |
| Method/function body | +indent from declaration | `void foo() {\n    ...` |
| Nested blocks (if, for, while, try) | +indent from statement | `if (...) {\n    ...` |
| Continuation lines | +continuation from declaration | `const Foo\n        implements Bar` |
| Continuation ending with `{` | +indent from declaration (not continuation) | `implements Bar {\n    body` |
| Switch/case | `case` at same indent as `switch` | `switch {\ncase X:` |
| Case body | +indent from `case` | `case X:\n    stmt;` |
| Annotations | Same indent as annotated element | `@Override\nvoid foo()` |
| Closing `}` | Same indent as the line containing `{` | matches declaration indent |
| Closing `)` | Same indent as the line containing `(` | matches opening line |
| Lambda arrow body | +indent from arrow line | `e ->\n    body` |
| Doc comment lines | ` * ` aligned with opening `/**` | `/**\n * text\n */` |
| Block comment lines | ` * ` aligned with opening `/*` | `/*\n * text\n */` |
| Line comments | Same indent as surrounding code | `// comment` |
| String literals | Never modified | preserve exact whitespace |
| Blank lines | Preserve (don't add or remove) | keep author's intent |
| Final newline | Always ensure file ends with `\n` | XTC convention |

## Algorithm

### Core: `computeLineIndent(tree, lineIndex, lines, config)`

For each line, determines the correct indentation using AST structural depth:

1. **Blank lines** → 0 (empty line, no trailing whitespace)
2. **String literal interior** → SKIP (never modify)
3. **Comment interior** → `countIndentDepth(commentAncestor) * indentSize + 1`
4. **Closing `}`** → `countIndentDepth(ownerNode) * indentSize`
5. **Closing `)`** → `countIndentDepth(enclosingConstruct) * indentSize`
6. **Case labels** → `countIndentDepth(switchNode) * indentSize`
7. **Continuation keywords** → `countIndentDepth(declNode) * indentSize + continuationIndentSize`
8. **General case** → `countIndentDepth(node) * indentSize`

### Why structural depth, not source-based indent

The original plan proposed reading indent from reference lines in the source:
`getLineIndent(source, ownerLine) + indentSize`. This fails when the input is
misformatted: if the reference line is at the wrong indent, the computed indent
for dependent lines is also wrong.

Structural depth counting walks the AST ancestry and counts how many indent-parent
nodes exist above a given position. This is independent of the source's actual
whitespace, so it works correctly even on completely unindented files.

## Implementation Order + Status

| Step | Description | Status |
|------|-------------|--------|
| 1 | Core `computeLineIndent` + `countIndentDepth` methods in `TreeSitterAdapter` | ✅ Done |
| 2 | Override `formatDocument` + `formatRange` in `TreeSitterAdapter` | ✅ Done |
| 3 | Always-insert final newline (unconditional, not gated on LSP option) | ✅ Done |
| 4 | Unit tests: `DocumentFormattingTest.kt` — 21 tests across 10 categories | ✅ Done |
| 5 | Integration test: format a real `lib_ecstasy/` file and assert 0 edits | Pending |
| 6 | Update capabilities audit / manual test plan | Pending |

## Testing (Implemented)

### Unit Tests: `DocumentFormattingTest.kt`

21 tests in 10 nested test classes:

| Category | # | Tests |
|----------|---|-------|
| Basic indentation | 3 | Top-level module, nested class, method body |
| Control flow | 1 | if/for/while blocks |
| Continuation lines | 1 | extends/implements at continuation indent |
| Switch/case | 1 | case at switch level |
| Closing delimiters | 1 | `}` matches opening construct |
| Comments | 3 | Doc comment, block comment, line comment |
| String literals | 1 | Multiline string content preserved |
| Blank lines | 1 | Preserved, trailing whitespace removed |
| Final newline | 2 | Always inserted; no duplicate |
| Idempotent | 2 | Correct file → 0 edits; double-format stable |
| Range formatting | 1 | Only requested lines formatted |
| Lambda arrow | 1 | Body indented after `->` |
| Mixed issues | 1 | Multiple problems fixed in one pass |
| Performance | 1 | 1000+ line file under 500ms |

### Test Helper: `applyEdits`

Applies TextEdits to source in reverse document order (bottom-to-top, right-to-left) to
preserve positions. Used by all tests to verify the formatter output matches expectations.

## Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Override `formatDocument()` and `formatRange()` with AST-aware logic; add `computeLineIndent()`, `countIndentDepth()`, `buildFormattingEdits()` |
| `DocumentFormattingTest.kt` | New — 21 unit tests |

## Files NOT Modified

| File | Reason |
|------|--------|
| `XtcLanguageServer.kt` | Already calls `adapter.formatDocument()` — transparent upgrade |
| `AbstractXtcCompilerAdapter.kt` | Retained as fallback; TreeSitterAdapter overrides |
| `XtcCompilerAdapter.kt` | Interface unchanged |
| `XtcFormattingConfig.kt` | Already supports the needed config values |

## Relationship to On-Type Formatting

The document formatter and on-type formatter share the same indentation rules but
apply them differently:

| Aspect | On-Type Formatting | Document Formatting |
|--------|-------------------|-------------------|
| Trigger | Single keystroke (`\n`, `}`, `)`) | Manual action (`Ctrl+Alt+L`) |
| Scope | One line (the line just typed) | Entire file or selection |
| Tree state | May be slightly stale (pre-edit) | Current (post-compile) |
| Approach | Previous-line heuristics + AST context | Pure AST structural depth |
| Indent source | Reads from source lines | Counts AST ancestors |
| Edits | 0-1 TextEdit per invocation | 0-N TextEdits (one per misindented line) |

Some helpers are shared: `makeIndentEdit`, `isInsideStringLiteral`,
`isContinuationLine`, and the node type sets (`indentParentTypes`,
`declarationTypes`, `controlFlowTypes`, etc.).

## Known Limitations

1. **Severely misindented input may confuse tree-sitter's error recovery.** When source
   is so badly misformatted that tree-sitter can't build a correct AST, the formatter's
   output won't be perfect. This is inherent to any AST-based approach — a second format
   pass after the first fixes most of these cases.

2. **Multi-line parameter/argument lists** don't have a dedicated alignment rule. They
   get the general structural depth indent, which may not match the "align with opening
   paren" convention some developers prefer.

3. **`case_clause` depth counting** depends on tree-sitter parsing the switch correctly.
   When the case label is extremely far from the switch braces in the source, the parser
   may not associate them, and the special case-at-switch rule won't fire.

## Future Extensions (Not in This Plan)

These are **separate features** that build on the document formatter but are out of scope:

1. **Brace style enforcement** — Move `{` to same line as declaration (K&R) or separate line (Allman). Requires inserting/removing newlines, not just adjusting indent.

2. **Blank line normalization** — Enforce exactly one blank line between methods, two between sections. Requires inserting/removing lines.

3. **Import ordering** — Sort and group import statements. Already has a code action (`organizeImports`); could be integrated into the formatter.

4. **Max line width enforcement** — Break long lines at `maxLineWidth`. Requires understanding expression structure to find good break points.

5. **Trailing comma enforcement** — Add/remove trailing commas in multi-line lists. Minor style preference.

6. **Format on save** — Automatically format when saving. Requires editor-side configuration (IntelliJ: "Reformat code" in Actions on Save; VS Code: `editor.formatOnSave`). The LSP server doesn't need changes for this — the editor calls `textDocument/formatting` on save.

7. **Multi-line parameter alignment** — Align continuation lines in parameter/argument lists with the opening paren column. Requires tracking paren positions rather than pure depth counting.
