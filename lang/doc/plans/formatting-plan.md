# Implementation Plan: LSP `textDocument/onTypeFormatting` for XTC

## Overview

This plan describes how to implement on-type formatting in the XTC language server. The feature auto-adjusts indentation as the developer types, providing immediate feedback for `\n` (Enter), `}` (closing brace), and `;` (statement end). The implementation uses tree-sitter AST context from the `TreeSitterAdapter` to determine correct indentation levels.

## Current State

The plumbing is already in place:

| Component | File | Status |
|-----------|------|--------|
| LSP handler | `XtcLanguageServer.kt:1306` | Complete -- delegates to adapter |
| Adapter interface | `XtcCompilerAdapter.kt:883` | Complete -- `onTypeFormatting()` signature defined |
| Base adapter stub | `AbstractXtcCompilerAdapter.kt:366` | Returns `emptyList()` with `NOT IMPLEMENTED` warning |
| Capability registration | `XtcLanguageServer.kt:337-393` | Commented out -- needs `DocumentOnTypeFormattingOptions` |
| Client-side wiring | LSP4IJ | Built-in handlers activate automatically when server advertises the capability |

The `onTypeFormatting` request flow is:

```
Client types trigger char
  -> LSP4IJ sends textDocument/onTypeFormatting
    -> XtcLanguageServer.onTypeFormatting() (line 1306)
      -> adapter.onTypeFormatting(uri, line, column, ch, options)
        -> returns List<TextEdit>
          -> client applies edits
```

## XTC Indentation Conventions

All rules are derived from the XTC standard library source code (`lib_ecstasy/`):

| Context | Rule | Example |
|---------|------|---------|
| Base unit | 4 spaces, no tabs ever | -- |
| Declaration bodies | +4 from declaration keyword | `class Foo {\n    ...` |
| Method bodies | +4 from method declaration | `void foo() {\n    ...` |
| Nested blocks | +4 from enclosing statement | `if (...) {\n    ...` |
| Continuation lines | +8 from declaration start | `const Foo\n        implements Bar {` |
| Switch/case | `case` at same indent as `switch` | `switch (...) {\ncase X:` |
| Case body | +4 from `case` | `case X:\n    stmt;` |
| Annotations | Same indent as annotated element | `@Override\nvoid foo()` |
| Comment dividers | Same indent as surrounding content | `// ----- constructors ---` |
| K&R braces | Opening `{` on same line as declaration | `class Foo {` |
| Closing `}` | Same indent as the line containing `{` | 4-space indent if `{` was at 4 |

## Changes Required

### 1. Enable the Capability (XtcLanguageServer.kt)

In `buildServerCapabilities()` (around line 362), add the `DocumentOnTypeFormattingOptions`:

```kotlin
// After documentRangeFormattingProvider:
documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions("\n").apply {
    moreTriggerCharacter = listOf("}", ";")
}
```

The first constructor argument is the "first trigger character" (required by LSP spec), and `moreTriggerCharacter` lists additional triggers. The `\n` trigger is the primary one -- LSP4IJ's `LSPServerSideOnTypeFormattingEnterHandler` activates for it. The `}` and `;` triggers activate `LSPServerSideOnTypeFormattingTypedHandler`.

Also uncomment the line in `logClientCapabilities` (line 311):

```kotlin
td?.onTypeFormatting?.let { "onTypeFormatting" }, // treesitter: auto-indent
```

### 2. Override `onTypeFormatting` in TreeSitterAdapter.kt

Replace the inherited stub from `AbstractXtcCompilerAdapter` with a real implementation. This is the core of the feature.

#### Method Signature

```kotlin
override fun onTypeFormatting(
    uri: String,
    line: Int,
    column: Int,
    ch: String,
    options: XtcCompilerAdapter.FormattingOptions,
): List<XtcCompilerAdapter.TextEdit>
```

**Parameter semantics from LSP spec:**
- `line` / `column`: The cursor position *after* the trigger character was inserted. For `\n`, this is the beginning of the new line (line = new line number, column = 0 unless auto-indent already applied). For `}` and `;`, this is the position right after the typed character.
- `ch`: The trigger character (`"\n"`, `"}"`, or `";"`)
- `options.tabSize`: The editor's tab size (should be 4 for XTC)
- `options.insertSpaces`: Always true for XTC

#### Available Context

The implementation has access to:
- `parsedTrees[uri]` -- the `XtcTree` for the file (may be slightly stale since the edit that typed the trigger character may not have been re-parsed yet via `compile()`)
- `tree.source` -- the full text of the document as of the last `compile()` call
- `tree.nodeAt(line, column)` -- the smallest AST node at a position
- Node navigation: `node.parent`, `node.children`, `node.prevSibling`, `node.nextSibling`
- Node metadata: `node.type`, `node.startLine`, `node.startColumn`, `node.text`
- Field access: `node.childByFieldName("body")`, `node.childByFieldName("name")`

**Important timing note:** The LSP spec says `onTypeFormatting` is sent *after* the character is inserted but the document change notification (`didChange` / `compile()`) may arrive before or after the formatting request. The tree may not reflect the just-typed character. The implementation must be tolerant of this -- use the tree for structural context (what is the enclosing construct?) but derive the actual indentation from AST depth rather than parsing the exact current line.

### 3. Algorithm Design

#### 3.1 Common Infrastructure

```kotlin
companion object {
    /** XTC always uses 4-space indentation. */
    private const val INDENT_SIZE = 4

    /** Continuation indent is 8 spaces (double the normal indent). */
    private const val CONTINUATION_INDENT_SIZE = 8

    /** Node types that represent declaration bodies (class-like constructs). */
    private val CLASS_BODY_TYPES = setOf("class_body")

    /** Node types that represent statement blocks. */
    private val BLOCK_TYPES = setOf("block")

    /** Node types that increase indentation for their children. */
    private val INDENT_PARENT_TYPES = setOf(
        "class_body",
        "block",
        "case_clause",
    )

    /** Declaration node types that use continuation indentation for extends/implements. */
    private val DECLARATION_TYPES = setOf(
        "class_declaration",
        "interface_declaration",
        "mixin_declaration",
        "service_declaration",
        "const_declaration",
        "enum_declaration",
    )

    /** Node types for control-flow statements whose body is a block. */
    private val CONTROL_FLOW_TYPES = setOf(
        "if_statement",
        "for_statement",
        "while_statement",
        "do_statement",
        "try_statement",
        "catch_clause",
        "using_statement",
    )
}
```

#### 3.2 Core Helper: `computeIndentLevel`

Walk from a given node up to the root, counting indent-increasing ancestors:

```kotlin
private fun computeIndentLevel(node: XtcNode): Int {
    var level = 0
    var current = node.parent
    while (current != null) {
        if (current.type in INDENT_PARENT_TYPES) {
            level++
        }
        current = current.parent
    }
    return level
}
```

This gives the structural indentation depth for any node. Multiply by `INDENT_SIZE` (or use `options.tabSize`) to get the column offset.

However, a simpler and more robust approach is to read the indentation of the reference line (the line containing the opening `{` or the parent declaration) directly from the source text. This avoids issues with the tree not perfectly modeling every indentation nuance.

#### 3.3 Core Helper: `getLineIndent`

Read the leading whitespace of a given line from the document source:

```kotlin
private fun getLineIndent(source: String, lineNumber: Int): Int {
    val lines = source.split("\n")
    if (lineNumber < 0 || lineNumber >= lines.size) return 0
    return lines[lineNumber].takeWhile { it == ' ' }.length
}
```

#### 3.4 Core Helper: `makeIndentEdit`

Create a `TextEdit` that replaces the leading whitespace of a line:

```kotlin
private fun makeIndentEdit(
    line: Int,
    currentIndent: Int,
    desiredIndent: Int,
): XtcCompilerAdapter.TextEdit {
    return XtcCompilerAdapter.TextEdit(
        range = XtcCompilerAdapter.Range(
            start = XtcCompilerAdapter.Position(line, 0),
            end = XtcCompilerAdapter.Position(line, currentIndent),
        ),
        newText = " ".repeat(desiredIndent),
    )
}
```

#### 3.5 Trigger: `\n` (Enter Key)

This is the most important trigger. When the user presses Enter, the editor creates a new line and the LSP server must determine the correct indentation.

**Algorithm:**

```
1. Let prevLine = line - 1 (the line the cursor was on before pressing Enter)
2. Get the tree node at (prevLine, lastNonWhitespaceColumn)
3. Walk up to find the nearest structural ancestor
4. Determine base indent:

   a. If previous line ends with '{':
      - Find the node that owns the '{' (the block or class_body parent)
      - desiredIndent = indent of line containing '{' + INDENT_SIZE

   b. If previous line ends with ':' and we're inside a case_clause:
      - desiredIndent = indent of case line + INDENT_SIZE

   c. If cursor is between '{' and '}' (the '}' is on the current or next line):
      - This is the "sandwich" case -- user pressed Enter between braces
      - Return TWO edits:
        (1) Indent the cursor line at indent + INDENT_SIZE
        (2) Add a new line with the closing '}' at indent level
      - Note: Most editors handle the '}' placement via autoClosingPairs,
        so we may only need to set the cursor line indent

   d. If previous line is a continuation keyword line (extends, implements,
      incorporates, delegates) that doesn't end with '{':
      - desiredIndent = indent of the declaration start line + CONTINUATION_INDENT_SIZE
      - This keeps continuation lines aligned

   e. If previous line is a continuation keyword line that DOES end with '{':
      - desiredIndent = indent of the declaration start line + INDENT_SIZE
      - Body starts at normal indent relative to the declaration, not the continuation

   f. Otherwise (normal statement):
      - desiredIndent = indent of previous non-empty line (maintain current level)
      - Refine by checking the structural ancestor:
        if the ancestor is a block or class_body, use ancestor's indent + INDENT_SIZE

5. Return a TextEdit that sets the new line's leading whitespace to desiredIndent
```

**Finding the previous line's trailing character:** Since the tree may be stale, use the source text directly:

```kotlin
val prevLineText = lines[prevLine].trimEnd()
val endsWithOpenBrace = prevLineText.endsWith("{")
val endsWithColon = prevLineText.endsWith(":")
```

**The brace-sandwich detection** uses the tree to check if the node at the cursor position is a `}` or if the next non-whitespace character on the current line is `}`:

```kotlin
val currentLineText = lines.getOrNull(line)?.trim() ?: ""
val isBraceSandwich = endsWithOpenBrace && (currentLineText == "}" || currentLineText.startsWith("}"))
```

#### 3.6 Trigger: `}` (Closing Brace)

When the user types `}`, outdent the current line to match the line where the corresponding `{` appears.

**Algorithm:**

```
1. Get the node at (line, column) -- this should be the '}' itself or near it
2. Walk up to find the enclosing block or class_body node
3. The opening '{' is on the line where that block/class_body starts
   (for class_body, it's the parent declaration's line; for block, check
   the block's startLine or the parent statement's line)
4. desiredIndent = indent of the line containing the opening '{'
5. Read the current indent of the line containing '}'
6. If currentIndent != desiredIndent, return a TextEdit to fix it
7. If they match, return emptyList() (no change needed)
```

**Finding the matching `{`:** The tree-sitter AST directly models this. A `block` node spans from `{` to `}`. The `block`'s `startLine` tells us where the `{` is. But we want the indent of the *statement* that contains the block, not the block itself:

```kotlin
// For a block that is the body of an if_statement, for_statement, etc.
// the desired indent matches the control-flow statement's line indent
val blockParent = blockNode.parent
val refLine = when (blockParent?.type) {
    in CONTROL_FLOW_TYPES -> blockParent.startLine
    in DECLARATION_TYPES -> blockParent.startLine
    "method_declaration", "constructor_declaration" -> blockParent.startLine
    else -> blockNode.startLine  // standalone block
}
```

#### 3.7 Trigger: `;` (Semicolon)

This trigger is lower priority and handles fewer cases. Its main purpose is to correct indentation after a continuation-style statement completes.

**Algorithm:**

```
1. Get the current line's text
2. If the current line is NOT the first line of a statement (i.e., it's a
   continuation from a multi-line expression), no action needed -- the
   indent was already set when Enter was pressed
3. If the line is a simple single-line statement and its indent doesn't
   match the expected indent for its position in the AST, correct it
4. In practice, most ';' cases require no action -- return emptyList()
```

The `;` trigger is primarily useful for catching edge cases where the `\n` handler couldn't determine the correct indent at the time (because the statement wasn't complete yet). Given the complexity-to-value ratio, the initial implementation can simply return `emptyList()` for `;` and add logic later based on user feedback.

### 4. Detailed Implementation

#### 4.1 Main Dispatch

```kotlin
override fun onTypeFormatting(
    uri: String,
    line: Int,
    column: Int,
    ch: String,
    options: XtcCompilerAdapter.FormattingOptions,
): List<XtcCompilerAdapter.TextEdit> {
    val tree = parsedTrees[uri]
    if (tree == null) {
        logger.info("onTypeFormatting: no parsed tree for {}", uri.substringAfterLast('/'))
        return emptyList()
    }

    val indentSize = if (options.insertSpaces) options.tabSize else INDENT_SIZE

    return when (ch) {
        "\n" -> handleEnter(tree, line, column, indentSize)
        "}"  -> handleCloseBrace(tree, line, column, indentSize)
        ";"  -> emptyList() // Phase 2: handleSemicolon(tree, line, column, indentSize)
        else -> emptyList()
    }
}
```

#### 4.2 `handleEnter` Implementation

```kotlin
private fun handleEnter(
    tree: XtcTree,
    line: Int,
    column: Int,
    indentSize: Int,
): List<XtcCompilerAdapter.TextEdit> {
    val source = tree.source
    val lines = source.split("\n")

    // line is the NEW line (after Enter). Previous line is line - 1.
    val prevLineIndex = line - 1
    if (prevLineIndex < 0 || prevLineIndex >= lines.size) return emptyList()

    val prevLine = lines[prevLineIndex]
    val prevTrimmed = prevLine.trimEnd()
    val prevIndent = prevLine.takeWhile { it == ' ' }.length

    // Determine desired indent based on what the previous line ends with
    val desiredIndent = when {
        // Previous line ends with '{' -> indent one level deeper
        prevTrimmed.endsWith("{") -> prevIndent + indentSize

        // Previous line ends with ':' inside a case_clause -> indent for case body
        prevTrimmed.endsWith(":") && isInsideCaseClause(tree, prevLineIndex) ->
            prevIndent + indentSize

        // Previous line is a continuation keyword (extends, implements, etc.)
        // that doesn't end with '{' -> maintain continuation indent
        isContinuationLine(prevTrimmed) && !prevTrimmed.endsWith("{") ->
            findDeclarationIndent(tree, prevLineIndex) + CONTINUATION_INDENT_SIZE

        // Previous line has a continuation keyword ending with '{' -> body indent
        isContinuationLine(prevTrimmed) && prevTrimmed.endsWith("{") ->
            findDeclarationIndent(tree, prevLineIndex) + indentSize

        // Previous line ends with '}' -> maintain the brace's indent level
        prevTrimmed.endsWith("}") -> prevIndent

        // Default: look at AST context to determine indent
        else -> computeDesiredIndent(tree, prevLineIndex, prevIndent, indentSize)
    }

    // Only emit an edit if the desired indent differs from what the editor
    // would naturally provide (column 0 for a bare newline, or editor's auto-indent)
    val currentIndent = if (line < lines.size) {
        lines[line].takeWhile { it == ' ' }.length
    } else {
        0
    }

    if (desiredIndent == currentIndent) return emptyList()

    return listOf(makeIndentEdit(line, currentIndent, desiredIndent))
}
```

#### 4.3 `handleCloseBrace` Implementation

```kotlin
private fun handleCloseBrace(
    tree: XtcTree,
    line: Int,
    column: Int,
    indentSize: Int,
): List<XtcCompilerAdapter.TextEdit> {
    val source = tree.source
    val lines = source.split("\n")
    if (line < 0 || line >= lines.size) return emptyList()

    val currentLine = lines[line]
    val currentIndent = currentLine.takeWhile { it == ' ' }.length

    // Find the enclosing block/class_body in the AST
    val node = tree.nodeAt(line, column) ?: return emptyList()

    // Walk up to find the block or class_body that this '}' closes
    val enclosingBlock = generateSequence(node) { it.parent }
        .firstOrNull { it.type in BLOCK_TYPES || it.type in CLASS_BODY_TYPES }
        ?: return emptyList()

    // Find the reference line -- the line of the construct that owns the block
    val ownerNode = enclosingBlock.parent
    val refLine = when (ownerNode?.type) {
        in DECLARATION_TYPES,
        "method_declaration",
        "constructor_declaration" -> ownerNode.startLine
        in CONTROL_FLOW_TYPES -> ownerNode.startLine
        else -> enclosingBlock.startLine
    }

    val desiredIndent = getLineIndent(source, refLine)

    if (desiredIndent == currentIndent) return emptyList()

    return listOf(makeIndentEdit(line, currentIndent, desiredIndent))
}
```

#### 4.4 Helper: `isContinuationLine`

```kotlin
private fun isContinuationLine(trimmedLine: String): Boolean {
    val stripped = trimmedLine.trimStart()
    return stripped.startsWith("extends ") ||
           stripped.startsWith("implements ") ||
           stripped.startsWith("incorporates ") ||
           stripped.startsWith("delegates ")
}
```

#### 4.5 Helper: `isInsideCaseClause`

```kotlin
private fun isInsideCaseClause(tree: XtcTree, lineIndex: Int): Boolean {
    // Check if the line at lineIndex is inside a case_clause by walking up from a
    // node on that line
    val node = tree.nodeAt(lineIndex, 0) ?: return false
    return generateSequence(node) { it.parent }
        .any { it.type == "case_clause" }
}
```

#### 4.6 Helper: `findDeclarationIndent`

Walk up from a continuation line to find the declaration that started it:

```kotlin
private fun findDeclarationIndent(tree: XtcTree, lineIndex: Int): Int {
    val node = tree.nodeAt(lineIndex, 0) ?: return 0
    val decl = generateSequence(node) { it.parent }
        .firstOrNull { it.type in DECLARATION_TYPES }
        ?: return 0
    return getLineIndent(tree.source, decl.startLine)
}
```

#### 4.7 Helper: `computeDesiredIndent`

For the general case where the previous line is a normal statement:

```kotlin
private fun computeDesiredIndent(
    tree: XtcTree,
    prevLineIndex: Int,
    prevIndent: Int,
    indentSize: Int,
): Int {
    // Find the AST node at the end of the previous line
    val prevLineText = tree.source.split("\n").getOrNull(prevLineIndex) ?: return prevIndent
    val lastNonSpace = prevLineText.indexOfLast { !it.isWhitespace() }
    if (lastNonSpace < 0) return prevIndent  // blank line, maintain indent

    val node = tree.nodeAt(prevLineIndex, lastNonSpace) ?: return prevIndent

    // Walk up to find the nearest indent-affecting ancestor
    val ancestor = generateSequence(node) { it.parent }
        .firstOrNull { it.type in INDENT_PARENT_TYPES }

    return if (ancestor != null) {
        // We're inside an indent-affecting construct
        val ancestorIndent = getLineIndent(tree.source, ancestor.startLine)
        // class_body and block start with '{' -- content is at +indentSize from the owner
        val ownerLine = ancestor.parent?.startLine ?: ancestor.startLine
        getLineIndent(tree.source, ownerLine) + indentSize
    } else {
        prevIndent  // at top level, maintain current indent
    }
}
```

### 5. Edge Cases

#### 5.1 Empty Lines

When the previous line is blank (whitespace only), maintain the indent of the last non-empty line above it, or fall back to the AST-determined indent.

#### 5.2 Nested Braces

```
class Outer {
    class Inner {
        void foo() {
            if (x) {
                |  <-- Enter here: indent = 16 (4 levels)
            }
        }
    }
}
```

The `computeIndentLevel` helper correctly counts 4 ancestors (`class_body`, `class_body`, `block`, `block`), giving `4 * 4 = 16`.

#### 5.3 Multiline Declarations with Body

```
const SynchronizedSection
        implements Closeable {
    |  <-- Enter here: indent = 4 (one level from declaration, NOT from continuation)
```

The `handleEnter` logic detects that the previous line is a continuation line ending with `{` and uses `findDeclarationIndent() + indentSize` (0 + 4 = 4), not the continuation column (8 + 4 = 12).

#### 5.4 Comments

```
class Foo {
    // This is a comment
    |  <-- Enter after comment: indent = 4
```

Comments don't affect indentation. The `computeDesiredIndent` helper sees the comment node, walks up to the `class_body` ancestor, and returns the correct indent.

#### 5.5 Annotations

```
class Foo {
    @Override
    |  <-- Enter after annotation: indent = 4 (same as annotation)
```

Annotations are at the same indent as the element they annotate. Since the annotation is inside a `class_body`, the correct indent is `class_body_owner_indent + INDENT_SIZE`.

#### 5.6 String Literals

When the cursor is inside a string literal (regular or multiline), the formatter should not adjust indentation. Detect this by checking if the node at the cursor position is a `string_literal`, `multiline_literal`, `template_literal`, or `multiline_template_literal`:

```kotlin
val insideString = generateSequence(nodeAtCursor) { it.parent }
    .any { it.type in setOf("string_literal", "multiline_literal",
                            "template_literal", "multiline_template_literal") }
if (insideString) return emptyList()
```

#### 5.7 Stale Tree

If the tree hasn't been re-parsed after the latest edit, node positions may be off by one line (because Enter added a line). Mitigate this by using `line - 1` (the previous line) for tree lookups and reading the source text for the previous line's content. The tree structure (what's nested inside what) is still valid even if line numbers are shifted by one.

#### 5.8 Switch/Case Indentation

```
switch (x) {
case Foo:      <-- same indent as switch
    stmt;      <-- +4 from case
    break;     <-- +4 from case
case Bar:      <-- same indent as switch
```

The XTC convention is that `case` labels are at the same indent as the `switch` keyword (inside the `switch_statement` node but not further indented). The `case_clause` node type is in `INDENT_PARENT_TYPES`, so statements within a case get +4. When the user presses Enter after a `case ...:` line, the handler detects `endsWith(":")` + `isInsideCaseClause` and indents by +4.

When typing a new `case` keyword after the previous case's statements, the user would be at case-body indent (+4). The editor does not auto-outdent for `case` on type -- that requires the formatter to recognize the keyword, which is best handled by the document formatter rather than on-type formatting. The `}` trigger handles the final outdent when the switch block closes.

#### 5.9 Top-Level Module Declaration

```
module MyApp {
    |  <-- indent = 4
```

The module declaration's body is a `class_body`. The indent is `module_declaration.startLine indent + INDENT_SIZE = 0 + 4 = 4`.

#### 5.10 Lambda Expressions

```
list.forEach(e -> {
    |  <-- indent = prevIndent + 4
});
```

A lambda's `block` body follows the same rules as any other block. The `handleEnter` logic sees `endsWith("{")` and returns `prevIndent + indentSize`.

### 6. Testing Strategy

#### 6.1 Unit Tests

Create `TreeSitterOnTypeFormattingTest.kt` in the test directory alongside existing adapter tests. Each test:

1. Parses a snippet of XTC code using `XtcParser`
2. Calls `onTypeFormatting()` with specific trigger character and position
3. Asserts the returned `TextEdit` list matches expected indentation

**Test cases for `\n` trigger:**

| Scenario | Previous line | Expected indent |
|----------|--------------|-----------------|
| After `{` at top level | `module Foo {` | 4 |
| After `{` in method | `    void foo() {` | 8 |
| After `{` nested 3 deep | `            if (x) {` | 16 |
| After continuation `{` | `        implements Bar {` | 4 |
| After `case Foo:` | `    case Foo:` | 8 |
| After normal statement | `        x = 1;` | 8 |
| After `}` | `    }` | 4 |
| After blank line in body | `(blank)` inside class body | 4 |
| Inside string literal | `"hello` | no edit |
| After comment | `    // comment` | 4 |

**Test cases for `}` trigger:**

| Scenario | Closing brace context | Expected indent |
|----------|----------------------|-----------------|
| Close class body | `class Foo {` | 0 |
| Close method body | `    void foo() {` | 4 |
| Close if block | `        if (x) {` | 8 |
| Close nested block | 3 levels deep | 8 |
| Close switch block | `    switch (x) {` | 4 |

**Test cases for `;` trigger:**

| Scenario | Expected |
|----------|----------|
| Normal statement | no edit (emptyList) |

#### 6.2 Integration Tests

Test with the full LSP server running against a real XTC file:

1. Open a `.x` file via `textDocument/didOpen`
2. Send `textDocument/onTypeFormatting` requests
3. Verify the response contains correct `TextEdit` objects

#### 6.3 Manual Testing in IntelliJ / VS Code

Follow this checklist in both editors:

- [ ] Type `{` then Enter inside a class -- new line indented +4
- [ ] Type `{` then Enter inside a method -- new line indented +4
- [ ] Type Enter after a normal statement -- indent maintained
- [ ] Type `}` to close a class -- brace outdented to class indent
- [ ] Type `}` to close an if block -- brace outdented to if indent
- [ ] Multi-line declaration: type `implements Foo`, Enter, then `{` -- body at correct indent
- [ ] Switch/case: Enter after `case X:` -- indent +4 from case
- [ ] Nested constructs: correct indent at 3+ nesting levels
- [ ] String literal: Enter inside string -- no indent adjustment
- [ ] Comment: Enter after `//` comment -- indent maintained
- [ ] Large file performance: < 5ms per request

### 7. Relationship to TextMate indentationRules

The `language-configuration.json` currently provides:

```json
"indentationRules": {
    "increaseIndentPattern": "^.*\\{[^}\"']*$|^.*\\([^)\"']*$",
    "decreaseIndentPattern": "^\\s*(\\}|\\)).*$"
}
```

**How they interact:**

- **VS Code:** When the server advertises `onTypeFormattingProvider`, VS Code uses the server's response *instead of* the TextMate `indentationRules` for the trigger characters. The TextMate rules remain as a fallback for characters the server doesn't handle and for when the server returns no edits.

- **IntelliJ (LSP4IJ):** LSP4IJ's `LSPServerSideOnTypeFormattingEnterHandler` and `LSPServerSideOnTypeFormattingTypedHandler` take priority when the server advertises the capability. The TextMate indentation rules are used only when LSP4IJ does not handle the character.

**Design implication:** The server's `onTypeFormatting` implementation should be strictly *better* than the regex-based TextMate rules. It has AST context, so it can handle cases the regexes cannot:

1. Continuation lines (extends/implements) -- regex can't distinguish these
2. Switch/case indentation -- regex would naively indent after `{`
3. String literal awareness -- regex has crude exclusion patterns
4. Correct nesting depth -- regex can't count matched braces across lines

The TextMate rules should remain in `language-configuration.json` as a fallback for editors that don't support `onTypeFormatting` or when the LSP server is unavailable.

### 8. Performance Considerations

The `onTypeFormatting` handler is called on every keystroke for trigger characters. It must be fast:

- **Target latency:** < 5ms per request
- **Tree access:** Reading `parsedTrees[uri]` is a `ConcurrentHashMap.get()` -- O(1)
- **Node lookup:** `tree.nodeAt(line, column)` is O(log n) in tree depth
- **Parent walking:** Walking from leaf to root is O(depth), typically < 20 nodes
- **String splitting:** `source.split("\n")` allocates; consider caching line offsets if profiling shows this is hot. For a first implementation, the split is fine since it's fast for files under 10K lines.
- **No re-parsing:** The handler never calls `parser.parse()` -- it uses the existing tree.

### 9. Implementation Order

**Phase 1: Core (this plan)**
1. Add `DocumentOnTypeFormattingOptions` to `buildServerCapabilities()` in `XtcLanguageServer.kt`
2. Uncomment the `onTypeFormatting` line in `logClientCapabilities`
3. Add helper methods to `TreeSitterAdapter`: `getLineIndent`, `makeIndentEdit`, `isContinuationLine`, `isInsideCaseClause`, `findDeclarationIndent`, `computeDesiredIndent`
4. Implement `handleEnter()` in `TreeSitterAdapter`
5. Implement `handleCloseBrace()` in `TreeSitterAdapter`
6. Override `onTypeFormatting()` with dispatch to `handleEnter` / `handleCloseBrace`
7. Write unit tests for all scenarios in section 6.1
8. Manual testing per section 6.3

**Phase 2: Refinements (future)**
1. Implement `handleSemicolon()` for post-continuation correction
2. Cache line offsets for large files
3. Handle `onTypeFormatting` for `)` (closing parenthesis in multi-line parameter lists)
4. Add support for `/**` doc comment continuation (insert ` * ` prefix)

### 10. Files Modified

| File | Change |
|------|--------|
| `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt` | Add `documentOnTypeFormattingProvider` in `buildServerCapabilities()`, uncomment logging line |
| `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/TreeSitterAdapter.kt` | Override `onTypeFormatting()` with full implementation, add helpers |
| `lang/lsp-server/src/test/kotlin/.../TreeSitterOnTypeFormattingTest.kt` | New test file for unit tests |

### 11. Files NOT Modified

| File | Reason |
|------|--------|
| `XtcCompilerAdapter.kt` | Interface already defines `onTypeFormatting()` |
| `AbstractXtcCompilerAdapter.kt` | Stub remains as fallback for other adapter implementations |
| `language-configuration.json` | TextMate rules remain unchanged as fallback |
