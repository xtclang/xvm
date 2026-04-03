# 9 Tree-Sitter Improvements: Implementation Plans

## Overview

These 9 features use only the existing tree-sitter infrastructure — no semantic model
needed. Each section describes what to change, in which files, and the exact approach.

---

## 1. Context-Aware Completion

### Current State

`TreeSitterAdapter.getCompletions()` (line 350) returns a flat union of keywords +
built-in types + same-file declarations + imports — regardless of cursor context.
The `CompletionParams.context.triggerCharacter` (available in LSP4J) is not passed to
the adapter. Trigger characters are configured as `".", ":", "<"` (XtcLanguageServer
line 409).

### Goal

Filter and augment completions based on AST context at cursor position:

| Context | Trigger | What to show |
|---------|---------|-------------|
| After `.` | `.` | Same-class members (methods, properties) extracted from AST siblings |
| After `import ` | typed | Qualified names from workspace index |
| In type position | typed | Only types (classes, interfaces, enums, mixins, services, consts, modules) |
| After `extends`/`implements` | typed | Only types |
| Inside annotation `@` | `@` | Known annotation names |
| Default | typed | Current behavior (keywords + types + locals + imports) |

### Implementation

**Step 1**: Pass trigger character and context from LSP handler to adapter.

In `XtcCompilerAdapter.kt`, add `triggerCharacter: String? = null` parameter to
`getCompletions()`.

In `XtcLanguageServer.kt` completion handler (line 698), pass
`params.context?.triggerCharacter` through.

**Step 2**: Add context detection in `TreeSitterAdapter.getCompletions()`.

```kotlin
// Determine cursor context by examining the AST node at (line, column-1)
val contextNode = parsedTrees[uri]?.nodeAt(line, maxOf(0, column - 1))
val context = classifyCompletionContext(contextNode, triggerCharacter)
```

The `classifyCompletionContext()` method walks the node's ancestry:
- If parent is `member_expression` and we're after the `.` → MEMBER context
- If parent is `import_statement` → IMPORT context
- If parent is `type_expression` or after `extends`/`implements`/`:` → TYPE context
- If parent is `annotation` → ANNOTATION context
- Otherwise → DEFAULT

**Step 3**: Filter completions by context.

- MEMBER: Collect declarations from the enclosing class body (sibling methods/properties
  of the current method). Walk up to `class_body`/`module_body`, extract children that
  are declarations.
- IMPORT: Query workspace index with the partial path typed so far, return qualified
  names.
- TYPE: Filter to only `CompletionKind.CLASS`, `INTERFACE`, `MODULE` + add workspace
  index type symbols.
- ANNOTATION: Return known annotation names (extract from `@` nodes in the file + common
  ones like `Override`, `Abstract`, `Lazy`).
- DEFAULT: Current behavior.

**Step 4**: Add snippet completions for common patterns.

When in a statement position (inside a block, not after `.`), offer structure snippets:
- `if` → `if ($1) {\n    $2\n}`
- `for` → `for ($1) {\n    $2\n}`
- `switch` → `switch ($1) {\n    case $2:\n        $3\n}`

This requires adding a `snippetText` field to `CompletionItem` and setting
`InsertTextFormat.Snippet` in the LSP handler.

### Files Modified

| File | Change |
|------|--------|
| `XtcCompilerAdapter.kt` | Add `triggerCharacter` param to `getCompletions()` |
| `TreeSitterAdapter.kt` | Rewrite `getCompletions()` with context detection + filtering |
| `XtcLanguageServer.kt` | Pass trigger character from `CompletionParams` |
| `AbstractXtcCompilerAdapter.kt` | Update base signature |
| `MockXtcCompilerAdapter.kt` | Update base signature (test adapter) |

### Effort: Medium-Large (touches adapter interface)

---

## 2. Go-To-Definition for Imports

### Current State

`TreeSitterAdapter.getDocumentLinks()` (line 1396) returns `DocumentLink` objects with
`target = null`. The import path text is extracted via `queryEngine.findImportLocations()`.
The workspace index has `findByName(name)` which returns `IndexedSymbol` with
`uri` and `location`.

### Goal

When the user Ctrl+clicks an import path, navigate to the imported type's source file.

### Implementation

**Step 1**: In `getDocumentLinks()`, resolve each import path to a file URI.

```kotlin
// After extracting importPath from the query
val simpleName = importPath.substringAfterLast(".")
val indexed = if (indexReady.get()) workspaceIndex.findByName(simpleName) else emptyList()
val targetUri = indexed.firstOrNull()?.uri  // prefer first match
```

**Step 2**: Populate the `target` field.

```kotlin
XtcCompilerAdapter.DocumentLink(
    range = Range(...),
    target = targetUri,  // was: null
    tooltip = "import $importPath",
)
```

**Step 3**: For qualified paths like `ecstasy.collections.HashMap`, try the full
qualified name first via `workspaceIndex.findByName("HashMap")`, then filter results
whose container matches the path prefix.

### Edge Cases

- Import not found in index → keep `target = null` (shows tooltip only)
- Multiple matches → pick the one whose qualified container best matches the import path
- Index not ready yet → keep `target = null`

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Update `getDocumentLinks()` to resolve targets via workspace index |

### Effort: Small

---

## 3. Read/Write Highlight Distinction

### Current State

`TreeSitterAdapter.getDocumentHighlights()` (line 512) returns all occurrences with
`HighlightKind.TEXT`. The `HighlightKind` enum already has `READ` and `WRITE` values.

### Goal

Assignment targets show as WRITE highlights; all other references show as READ.

### Implementation

**Step 1**: For each found location, check if the identifier is an assignment target.

```kotlin
locations.map { loc ->
    val node = tree.nodeAt(loc.startLine, loc.startColumn)
    val kind = if (node != null && isAssignmentTarget(node)) {
        HighlightKind.WRITE
    } else {
        HighlightKind.READ
    }
    DocumentHighlight(range = ..., kind = kind)
}
```

**Step 2**: Implement `isAssignmentTarget()`.

Walk up the parent chain from the node. If we find an `assignment_statement` or
`assignment_expression` where the node is under the `left` child field, it's a WRITE.
Also check for:
- `variable_declaration` with this identifier as the `name` field → WRITE (declaration site)
- `for_each_statement` with this identifier as the binding → WRITE
- `parameter` node → WRITE (declaration site)

```kotlin
private fun isAssignmentTarget(node: XtcNode): Boolean {
    val parent = node.parent ?: return false
    return when (parent.type) {
        "assignment_statement", "assignment_expression" ->
            parent.childByFieldName("left")?.let { isOrContains(it, node) } ?: false
        "variable_declaration" ->
            parent.childByFieldName("name") == node
        "parameter" ->
            parent.childByFieldName("name") == node
        else -> false
    }
}
```

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Update `getDocumentHighlights()`, add `isAssignmentTarget()` |

### Effort: Small

---

## 4. Auto-Import Code Action

### Current State

`getCodeActions()` only returns "Organize Imports". Syntax error diagnostics are
published via `collectSyntaxErrors()` which finds `isError` / `isMissing` AST nodes.

### Goal

When an identifier in the source doesn't match any local declaration but matches a
symbol in the workspace index, offer a "Add import for X" code action.

### Implementation

**Step 1**: In `getCodeActions()`, collect unresolved identifiers in the requested range.

An "unresolved" identifier is one where:
1. It appears in a type position or expression position
2. It doesn't match any declaration in the same file
3. It matches a symbol in the workspace index

```kotlin
private fun buildAutoImportActions(uri: String, range: Range): List<CodeAction> {
    val tree = parsedTrees[uri] ?: return emptyList()
    if (!indexReady.get()) return emptyList()

    val localNames = queryEngine.findAllDeclarations(tree, uri).map { it.name }.toSet()
    val imports = queryEngine.findImports(tree).map { it.substringAfterLast(".") }.toSet()

    // Find identifiers in type positions that aren't locally declared
    val candidates = mutableListOf<Pair<String, XtcNode>>()
    collectUnresolvedTypeNames(tree.root, localNames + imports, candidates)

    return candidates.flatMap { (name, node) ->
        val indexed = workspaceIndex.findByName(name)
            .filter { it.kind in typeKinds }
        indexed.map { symbol ->
            val importText = buildImportStatement(symbol)
            buildAddImportAction(uri, tree, name, importText)
        }
    }
}
```

**Step 2**: `collectUnresolvedTypeNames()` walks the AST looking for `type_name` or
`identifier` nodes in type positions whose text doesn't match any local name.

**Step 3**: `buildAddImportAction()` creates a TextEdit that inserts the import
statement at the appropriate location (after existing imports, or at the top of the
module body).

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Add `buildAutoImportActions()` to `getCodeActions()` |

### Effort: Medium

---

## 5. Better Semantic Tokens

### Current State

`SemanticTokenEncoder.walkNode()` (line 82) classifies nodes by their direct AST type.
Enum members, constructor calls (vs method calls), and `@Deprecated` annotations are
not distinguished.

### Goal

| Improvement | Token Type | How to Detect |
|-------------|-----------|---------------|
| Enum values | `enumMember` (index 10) | Identifiers inside `enum_body` |
| Constructor calls (`new Foo()`) | `type` + `declaration` mod | `new_expression` parent |
| Deprecated declarations | any + `deprecated` mod | `@Deprecated` annotation sibling |
| Static methods/properties | any + `static` mod | `static` keyword in modifiers |
| Abstract declarations | any + `abstract` mod | `abstract` keyword in modifiers |

### Implementation

**Step 1**: Add enum value classification.

In `walkNode()`, add a case for nodes inside enum bodies:

```kotlin
"enum_value" -> classifyEnumValue(node)
```

```kotlin
private fun classifyEnumValue(node: XtcNode) {
    val name = node.childByFieldName("name") ?: node.children.firstOrNull {
        it.type == "identifier"
    } ?: return
    emitToken(name, "enumMember", SemanticTokenLegend.modifierBitmask("declaration", "readonly"))
    markClassified(name)
}
```

**Step 2**: Distinguish constructor calls in `new` expressions.

Currently `classifyCallExpression()` emits `method` for call targets. When the call
is inside a `new_expression`, emit `type` instead:

```kotlin
private fun classifyCallExpression(node: XtcNode) {
    val isNew = node.parent?.type == "new_expression"
    val funcNode = node.childByFieldName("function") ?: return
    if (isNew) {
        emitToken(funcNode, "type", 0)
    } else {
        emitToken(funcNode, "method", 0)
    }
    markClassified(funcNode)
}
```

**Step 3**: Detect `@Deprecated` annotation.

In `classifyTypeDeclaration()`, `classifyMethodDeclaration()`, etc., scan sibling
annotation nodes for `@Deprecated`:

```kotlin
private fun hasDeprecatedAnnotation(node: XtcNode): Boolean =
    node.children.any { child ->
        child.type == "annotation" &&
            (child.childByFieldName("name")?.text == "Deprecated" ||
             child.text.contains("@Deprecated"))
    }
```

Add the `deprecated` modifier bitmask when detected.

**Step 4**: The `static` and `abstract` modifiers are already detected by
`buildModifiers()` (line 280). Verify they're being applied correctly and propagated
to the token emission.

### Files Modified

| File | Change |
|------|--------|
| `SemanticTokenEncoder.kt` | Add enum value, new-expression, deprecated detection |

### Effort: Small (incremental additions to existing classifier)

---

## 6. Remove Unused Import

### Current State

`getCodeActions()` offers "Organize Imports" (sort). There is no detection of unused
imports.

### Goal

Offer "Remove unused import 'X'" when an imported name doesn't appear elsewhere in the
source file.

### Implementation

**Step 1**: In `getCodeActions()`, check each import for usage.

```kotlin
private fun buildRemoveUnusedImportActions(uri: String): List<CodeAction> {
    val tree = parsedTrees[uri] ?: return emptyList()
    val source = tree.source

    val importNodes = tree.root.children.filter { it.type == "import_statement" }
    if (importNodes.isEmpty()) return emptyList()

    return importNodes.mapNotNull { importNode ->
        val path = importNode.childByFieldName("path")?.text ?: return@mapNotNull null
        val simpleName = path.substringAfterLast(".")

        // Count occurrences of the simple name in the source (excluding the import line itself)
        val importLineText = source.split("\n").getOrNull(importNode.startLine) ?: ""
        val sourceWithoutImport = source.replace(importLineText, "")
        if (simpleName in sourceWithoutImport) return@mapNotNull null

        // Name not used elsewhere -> offer removal
        CodeAction(
            title = "Remove unused import '$simpleName'",
            kind = CodeActionKind.SOURCE,
            edit = WorkspaceEdit(
                changes = mapOf(uri to listOf(
                    TextEdit(
                        range = Range(
                            start = Position(importNode.startLine, 0),
                            end = Position(importNode.endLine + 1, 0), // include trailing newline
                        ),
                        newText = "",
                    )
                ))
            ),
        )
    }
}
```

Note: This is a heuristic — text search for the simple name in the source. It won't
detect imports used only as qualified references. But it catches the common case.

A more accurate approach: use `queryEngine.findAllIdentifiers(tree, simpleName, uri)`
and check if any match is outside the import statement itself.

**Step 2**: Add to `getCodeActions()` return list.

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Add `buildRemoveUnusedImportActions()` to `getCodeActions()` |

### Effort: Small

---

## 7. Fold Consecutive Line Comments

### Current State

`collectFoldingRanges()` folds `"comment"` and `"block_comment"` nodes individually.
Consecutive single-line `//` comments are separate `"comment"` nodes in tree-sitter and
each get their own fold range (typically single-line, so they're filtered out by the
`endLine > startLine` check).

### Goal

Detect runs of adjacent `//` comment lines and create a single fold range spanning
the group.

### Implementation

**Step 1**: After the existing fold collection, post-process to merge consecutive
single-line comments.

```kotlin
// In getFoldingRanges(), after collectFoldingRanges():
mergeConsecutiveLineComments(tree.root, result)
```

```kotlin
private fun mergeConsecutiveLineComments(root: XtcNode, result: MutableList<FoldingRange>) {
    // Collect all single-line comment nodes (type "comment", single line)
    val lineComments = mutableListOf<XtcNode>()
    collectLineComments(root, lineComments)

    // Sort by line number
    lineComments.sortBy { it.startLine }

    // Group consecutive comments (adjacent lines)
    var groupStart = -1
    var groupEnd = -1
    for (comment in lineComments) {
        if (groupStart < 0) {
            groupStart = comment.startLine
            groupEnd = comment.endLine
        } else if (comment.startLine <= groupEnd + 1) {
            // Adjacent or overlapping -> extend group
            groupEnd = comment.endLine
        } else {
            // Gap -> emit previous group if multi-line
            if (groupEnd > groupStart) {
                result.add(FoldingRange(groupStart, groupEnd, FoldingRange.FoldingKind.COMMENT))
            }
            groupStart = comment.startLine
            groupEnd = comment.endLine
        }
    }
    // Emit last group
    if (groupEnd > groupStart) {
        result.add(FoldingRange(groupStart, groupEnd, FoldingRange.FoldingKind.COMMENT))
    }
}
```

**Step 2**: `collectLineComments()` recursively walks the tree collecting `comment`
nodes that span exactly one line (these are `//` style comments).

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Add `mergeConsecutiveLineComments()`, call from `getFoldingRanges()` |

### Effort: Small

---

## 8. Generate Doc Comment

### Current State

No doc comment generation. The on-type formatting already handles `/** */` continuation
(inserting ` * ` on Enter inside doc comments) and skeleton creation (inserting ` * \n */`
after `/**`).

### Goal

Code action: "Generate documentation comment" appears when cursor is on or just before a
method/class/property declaration that has no doc comment. Inserts a `/** ... */` skeleton
with `@param` entries for each parameter.

### Implementation

**Step 1**: Detect declarations without doc comments.

In `getCodeActions()`, check if any declaration in the requested range lacks a preceding
doc comment:

```kotlin
private fun buildGenerateDocCommentActions(uri: String, range: Range): List<CodeAction> {
    val tree = parsedTrees[uri] ?: return emptyList()
    val source = tree.source
    val lines = source.split("\n")

    return buildList {
        // Find declarations that overlap the action range
        val declarations = queryEngine.findAllDeclarations(tree, uri)
            .filter { it.location.startLine in range.start.line..range.end.line }

        for (decl in declarations) {
            val declLine = decl.location.startLine
            // Check if the line above is a doc comment closing
            val prevLine = if (declLine > 0) lines[declLine - 1].trimEnd() else ""
            if (prevLine.endsWith("*/")) continue  // already has doc comment

            val skeleton = buildDocSkeleton(tree, decl, declLine, lines)
            add(
                CodeAction(
                    title = "Generate documentation comment",
                    kind = CodeActionKind.SOURCE,
                    edit = WorkspaceEdit(
                        changes = mapOf(uri to listOf(
                            TextEdit(
                                range = Range(
                                    start = Position(declLine, 0),
                                    end = Position(declLine, 0),
                                ),
                                newText = skeleton,
                            )
                        ))
                    ),
                )
            )
        }
    }
}
```

**Step 2**: Build the doc skeleton with `@param` entries.

```kotlin
private fun buildDocSkeleton(
    tree: XtcTree,
    decl: SymbolInfo,
    declLine: Int,
    lines: List<String>,
): String {
    val indent = lines[declLine].takeWhile { it == ' ' }
    val sb = StringBuilder()
    sb.appendLine("$indent/**")
    sb.appendLine("$indent * TODO: describe ${decl.name}")

    // Extract parameters if this is a method
    if (decl.kind == SymbolKind.METHOD || decl.kind == SymbolKind.CONSTRUCTOR) {
        val node = tree.nodeAt(declLine, lines[declLine].indexOfFirst { !it.isWhitespace() })
        val methodNode = node?.let { generateSequence(it) { it.parent }
            .firstOrNull { it.type == "method_declaration" || it.type == "constructor_declaration" }
        }
        val paramsNode = methodNode?.childByFieldName("parameters")
        paramsNode?.children
            ?.filter { it.type == "parameter" }
            ?.forEach { param ->
                val paramName = param.childByFieldName("name")?.text ?: return@forEach
                sb.appendLine("$indent * @param $paramName TODO")
            }
    }

    sb.appendLine("$indent */")
    return sb.toString()
}
```

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Add `buildGenerateDocCommentActions()` to `getCodeActions()` |

### Effort: Small-Medium

---

## 9. Multi-Line Parameter Alignment

### Current State

`computeLineIndent()` handles closing `)` by matching the structural depth of the
enclosing paren construct (line 978). Interior lines of multi-line paren constructs
(parameters, arguments) fall through to the general case which uses
`countIndentDepth(node) * config.indentSize`. This gives structural-depth-based indent
rather than aligning with the opening paren column.

### Goal

Lines inside a multi-line parenthesized construct (parameter list, argument list,
condition expression) are indented to the opening paren's structural depth + one
indent level. The closing `)` stays at the construct's depth (already handled).

Example (what we want):
```
void foo(
        Int a,       // declaration indent (8) + indent (4) = 12
        String b,    // same: 12
) {                  // back to declaration level: 8
```

Note: We do NOT align to the paren column (which would be `void foo(` = col 17) —
that's fragile and unusual for XTC. Instead, we use structural indent + continuation
indent from the config.

### Implementation

**Step 1**: In `computeLineIndent()`, between the closing-paren check (step 5) and
the case-label check (step 6), add a check for lines inside multi-line paren constructs.

```kotlin
// 5b. Interior of multi-line paren construct -> continuation indent from owner
val parenAncestor = generateSequence(node) { it.parent }.firstOrNull { ancestor ->
    val firstChild = ancestor.children.firstOrNull()
    firstChild != null && firstChild.type == "(" && ancestor.startLine < lineIndex
}
if (parenAncestor != null) {
    // The line is inside a multi-line parenthesized construct.
    // Indent = the construct's depth + one extra indent level.
    return countIndentDepth(parenAncestor) * config.indentSize + config.indentSize
}
```

This gives parameters/arguments one indent level deeper than the construct that owns
the parens (method declaration, call expression, etc.).

**Step 2**: Verify the closing `)` rule (already step 5) still works — it checks
`trimmed.startsWith(")")` BEFORE step 5b, so the closing paren gets the construct's
depth (not +indentSize). This is correct.

**Step 3**: Test cases:
- Method declaration with multi-line params: continuation at +4 from method indent
- Call expression with multi-line args: continuation at +4 from call indent
- Nested calls: each nesting level adds correctly
- Single-line parens: no change (paren open and content on same line)

### Files Modified

| File | Change |
|------|--------|
| `TreeSitterAdapter.kt` | Add step 5b in `computeLineIndent()` |
| `DocumentFormattingTest.kt` | Add tests for multi-line parameter indentation |

### Effort: Small-Medium

---

## Implementation Priority

| # | Feature | Effort | Impact | Dependencies |
|---|---------|--------|--------|-------------|
| 2 | Go-to-definition for imports | Small | High | None |
| 3 | Read/write highlight distinction | Small | Medium | None |
| 7 | Fold consecutive line comments | Small | Low | None |
| 6 | Remove unused import | Small | Medium | None |
| 5 | Better semantic tokens | Small | Medium | None |
| 8 | Generate doc comment | Small-Med | Medium | None |
| 9 | Multi-line parameter alignment | Small-Med | Medium | None |
| 4 | Auto-import code action | Medium | High | Workspace index ready |
| 1 | Context-aware completion | Med-Large | High | Changes adapter interface |

Items 2, 3, 7, 6, and 5 can be done independently in parallel. Items 8 and 9 have no
dependencies either but are slightly more work. Item 4 depends on the workspace index
being populated. Item 1 is the largest because it changes the adapter interface signature.

## Testing Strategy

Each feature gets its own nested test class, following the pattern in
`OnTypeFormattingTest.kt` and `DocumentFormattingTest.kt`:

| Feature | Test File | Key Assertions |
|---------|-----------|----------------|
| 1. Completion | `CompletionContextTest.kt` (new) | After `.` → only members; in type position → only types |
| 2. Import links | `TreeSitterAdapterTest.kt` (extend DocumentLinkTests) | target is non-null for indexed imports |
| 3. Highlights | `TreeSitterAdapterTest.kt` (extend) | Assignment targets → WRITE; reads → READ |
| 4. Auto-import | `TreeSitterAdapterTest.kt` (extend CodeActionTests) | Unresolved type name → "Add import" action |
| 5. Semantic tokens | `SemanticTokenEncoderTest.kt` (new or extend) | Enum values → enumMember; new Foo() → type |
| 6. Unused import | `TreeSitterAdapterTest.kt` (extend CodeActionTests) | Unused import → "Remove" action offered |
| 7. Folding | `TreeSitterAdapterTest.kt` (extend FoldingTests) | 3 consecutive `//` → one COMMENT fold range |
| 8. Doc comment | `TreeSitterAdapterTest.kt` (extend CodeActionTests) | Method without doc → "Generate" action with @param |
| 9. Alignment | `DocumentFormattingTest.kt` (extend) | Multi-line params → continuation indent |
