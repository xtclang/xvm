# XTC LSP Server: Next Steps Implementation Plan

This document covers all LSP features — implemented and planned — along with IDE client
integration strategies for IntelliJ (via LSP4IJ), VS Code, and other editors.

> **Last Updated**: 2026-02-12 (§13 expanded with multi-IDE market data and priority ranking)

---

## Table of Contents

### LSP Server Features (IDE-independent)

1. [Current Status](#1-current-status)
2. [Semantic Tokens](#2-semantic-tokens)
3. [Cross-File Navigation](#3-cross-file-navigation)
4. [Context-Aware Completion](#4-context-aware-completion)
5. [Workspace Symbol Search](#5-workspace-symbol-search)
6. [Type / Call Hierarchy](#6-type--call-hierarchy)
7. [Smart Inlay Hints](#7-smart-inlay-hints)
8. [Additional LSP Features](#8-additional-lsp-features)
9. [Shared Infrastructure](#9-shared-infrastructure)
10. [Dependency Graph & Implementation Order](#10-dependency-graph--implementation-order)

### IDE Client Integration

11. [IntelliJ (LSP4IJ)](#11-intellij-lsp4ij)
12. [VS Code](#12-vs-code)
13. [Multi-IDE Strategy](#13-multi-ide-strategy)

---

## 1. Current Status

The LSP server uses an adapter pattern (`XtcCompilerAdapter`) with two backends:
**TreeSitterAdapter** (current default) and **MockXtcCompilerAdapter** (fallback).

### Implemented (TreeSitter — returning real data)

| LSP Method | Notes |
|------------|-------|
| `textDocument/hover` | AST-based symbol lookup, markdown formatting |
| `textDocument/completion` | Keywords, built-in types, document symbols, imports. Trigger chars: `.` `:` `<` |
| `textDocument/definition` | Same-file only (name matching) |
| `textDocument/references` | Same-file only (text matching) |
| `textDocument/documentSymbol` | Full declaration tree via tree-sitter queries |
| `textDocument/documentHighlight` | All occurrences of identifier under cursor |
| `textDocument/selectionRange` | Smart expand/shrink via AST traversal |
| `textDocument/foldingRange` | Declarations, blocks, comments, imports |
| `textDocument/formatting` | Trailing whitespace removal, final newline |
| `textDocument/rangeFormatting` | Same as above, range-scoped |
| `textDocument/rename` | Same-file text replacement, with `prepareRename` validation |
| `textDocument/codeAction` | "Organize Imports" only |
| `textDocument/documentLink` | Import statement locations (target unresolved) |
| `textDocument/signatureHelp` | Same-file method parameters. Trigger chars: `(` `,` |
| `textDocument/publishDiagnostics` | Syntax errors from tree-sitter parse |

### Stub / Not Implemented (advertised but returning empty/null)

| LSP Method | Status | Blocker |
|------------|--------|---------|
| `textDocument/semanticTokens/full` | Returns `null` | Needs tree-sitter query mapping |
| `textDocument/inlayHint` | Returns `emptyList()` | Needs workspace index for param names |
| `workspace/symbol` | Returns `emptyList()`, capability not advertised | Needs workspace index |

### Not Registered (capability commented out in server)

| LSP Method | Requires |
|------------|----------|
| `textDocument/declaration` | Compiler (distinguish declaration vs definition) |
| `textDocument/typeDefinition` | Compiler (resolve type of expression) |
| `textDocument/implementation` | Compiler (find implementations of interface/abstract) |
| `typeHierarchy/prepare`, `supertypes`, `subtypes` | Workspace index (tree-sitter can extract extends/implements) |
| `callHierarchy/prepare`, `incomingCalls`, `outgoingCalls` | Workspace index + syntactic call graph |
| `textDocument/codeLens` | Compiler (reference counts, run buttons) |
| `textDocument/onTypeFormatting` | Tree-sitter (auto-indent) |
| `textDocument/linkedEditingRange` | Tree-sitter (rename tag pairs) |
| `textDocument/colorProvider` | Low priority |
| `textDocument/inlineValue` | Compiler + debugger |
| `textDocument/diagnostic` (pull-based) | Compiler |

---

## 2. Semantic Tokens

**LSP Methods:** `textDocument/semanticTokens/full`, `textDocument/semanticTokens/full/delta`, `textDocument/semanticTokens/range`

**Impact:** Highest. Semantic tokens overlay TextMate highlighting with type-aware coloring — distinguishing class names from variable names, parameters from properties, annotations from keywords. This is the single most visible upgrade to editor experience.

**Current state:** `TreeSitterAdapter.getSemanticTokens()` returns `null`. The `SemanticTokens` data class already exists in `XtcCompilerAdapter.kt:794`. The `semanticTokensFull()` handler in `XtcLanguageServer.kt` already delegates to the adapter — only the computation and capability registration are missing.

### 2.1 What Tree-Sitter Can Classify (No Compiler Needed)

**Tier 1 — High confidence (directly from parse tree position):**

| Tree-Sitter Node Context | LSP Token Type | Modifiers |
|--------------------------|----------------|-----------|
| `class_declaration > type_name` | `class` | `declaration` |
| `interface_declaration > type_name` | `interface` | `declaration` |
| `enum_declaration > type_name` | `enum` | `declaration` |
| `mixin_declaration > type_name` | `interface` | `declaration` |
| `service_declaration > type_name` | `class` | `declaration` |
| `const_declaration > type_name` | `struct` | `declaration`, `readonly` |
| `method_declaration > identifier` | `method` | `declaration` + modifiers from siblings |
| `constructor_declaration` | `method` | `declaration` |
| `property_declaration > identifier` | `property` | `declaration` + modifiers |
| `variable_declaration > identifier` | `variable` | `declaration` |
| `parameter > identifier` | `parameter` | `declaration` |
| `module_declaration > qualified_name` | `namespace` | `declaration` |
| `package_declaration > identifier` | `namespace` | `declaration` |
| `annotation > identifier` | `decorator` | — |
| `type_expression` children (return types, param types, extends clauses) | `type` | — |
| Access modifier keywords (`public`, `static`, `abstract`) | `modifier` | — |

**Tier 2 — Heuristic (usually correct):**

| Pattern | Heuristic | LSP Token Type |
|---------|-----------|----------------|
| `identifier` followed by `(` | Method/function call | `method` |
| `expr.identifier` not followed by `(` | Property access | `property` |
| `expr.identifier` followed by `(` | Method invocation | `method` |
| `UpperCamelCase` in non-declaration context | Type reference | `type` |

**NOT achievable without compiler:** Distinguishing class vs. interface at usage sites, `deprecated` modifier, `defaultLibrary` modifier, cross-file type classification, distinguishing variable vs. parameter at usage sites.

### 2.2 Token Legend Design

```kotlin
object XtcSemanticTokenLegend {
    val TOKEN_TYPES: List<String> = listOf(
        "namespace",      // 0  — modules, packages
        "type",           // 1  — generic type references
        "class",          // 2  — class names
        "enum",           // 3  — enum type names
        "interface",      // 4  — interface names
        "struct",         // 5  — const classes (Ecstasy const ~ immutable struct)
        "typeParameter",  // 6  — generic type parameters <T>
        "parameter",      // 7  — method parameters
        "variable",       // 8  — local variables
        "property",       // 9  — class properties/fields
        "enumMember",     // 10 — enum values
        "event",          // 11 — (unused)
        "function",       // 12 — function literals, lambdas
        "method",         // 13 — methods, constructors
        "macro",          // 14 — (unused)
        "keyword",        // 15 — language keywords
        "modifier",       // 16 — access modifiers
        "comment",        // 17 — comments
        "string",         // 18 — string literals
        "number",         // 19 — numeric literals
        "regexp",         // 20 — (unused)
        "operator",       // 21 — operators
        "decorator",      // 22 — annotations (@Inject, @Override)
    )

    val TOKEN_MODIFIERS: List<String> = listOf(
        "declaration",    // bit 0 — at declaration site
        "definition",     // bit 1 — body/implementation exists
        "readonly",       // bit 2 — immutable (val, const)
        "static",         // bit 3 — static member
        "deprecated",     // bit 4 — @Deprecated
        "abstract",       // bit 5 — abstract declaration
        "async",          // bit 6 — @Future
        "modification",   // bit 7 — write access
        "documentation",  // bit 8 — in doc context
        "defaultLibrary", // bit 9 — from ecstasy stdlib
    )
}
```

### 2.3 Encoding Format

The `data` field is a flat `int[]` where every 5 consecutive integers represent one token:

```
[deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
```

**Critical rule:** When `deltaLine == 0`, `deltaStartChar` is *relative* to the previous token. When `deltaLine > 0`, `deltaStartChar` is *absolute* (column on the new line).

**Example for `class Person`:**
```
Token: class   → [0, 0, 5, 15, 0]    (keyword, no modifiers)
Token: Person  → [0, 6, 6, 2, 1]     (class, declaration modifier)
```

### 2.4 Implementation Plan

**Files to modify:**

| File | Change |
|------|--------|
| `XtcLanguageConstants.kt` or new `XtcSemanticTokenLegend.kt` | Token legend definition |
| `TreeSitterAdapter.kt` | Implement `getSemanticTokens()` |
| `XtcQueryEngine.kt` | Add semantic token query execution |
| `XtcQueries.kt` | Add query patterns for annotations, type expressions |
| `XtcLanguageServer.kt` | Uncomment and configure `semanticTokensProvider` in capabilities |

**Phase 1:** Declaration-site tokens + type references in syntactic positions + annotations. Register `full` only. Gives ~60-70% of the benefit.

**Phase 2:** Heuristic usage-site tokens (method calls, property access, UpperCamelCase). Add `range` support.

**Phase 3 (compiler):** Override tree-sitter tokens with compiler-resolved classifications. Add `delta` encoding. Use `workspace/semanticTokens/refresh` on compiler analysis completion.

### 2.5 Lessons from Other Implementations

- **rust-analyzer:** Defines ~30 custom token types. Two-phase rendering: fast syntax pass, then semantic enrichment. Delta encoding essential for large files.
- **kotlin-language-server:** Only ~15 token types. Proves a modest set is already dramatically better than TextMate alone.
- **gopls:** Conservative — standard types only. Maximum theme compatibility.
- **Universal:** Start with `full` only; don't tokenize everything (TextMate already handles literals/comments); prefer fewer correct tokens over many incorrect ones; target <50ms for full computation.

---

## 3. Cross-File Navigation

**LSP Methods:** `textDocument/definition`, `textDocument/references`

**Impact:** Foundational. Every developer expects Ctrl+Click to jump to definitions and Shift+F12 to find all usages. Cross-file navigation is the gateway to "this feels like a real IDE."

**Current state:** `TreeSitterAdapter` supports same-file go-to-definition (by name matching) and same-file find-references (by text occurrence). Cannot resolve imports or cross-file jumps.

### 3.1 What Requires Compiler vs. What Doesn't

| Case | Tree-Sitter Alone | With Symbol Index | Requires Compiler |
|------|-------------------|-------------------|-------------------|
| Local variable definition | Yes (name match in scope) | — | — |
| Same-file class/method jump | Yes | — | — |
| Imported type → definition | No | **Yes** (match import path to file) | — |
| Method call → definition | No | Partial (if method name is unique) | Overload resolution |
| Inherited member → definition | No | No | Type hierarchy + resolution |
| Generic type parameter binding | No | No | Full type inference |
| Find references (same file) | Yes (text match) | — | — |
| Find references (cross-file) | No | **Yes** (workspace-wide name search) | Semantic filtering |

### 3.2 Architecture: Workspace Symbol Index

The enabling infrastructure is a **workspace symbol index** that maps symbol names to their declarations across all files. This index serves cross-file navigation, workspace symbols, and completion.

```kotlin
data class IndexedSymbol(
    val name: String,
    val qualifiedName: String,
    val kind: SymbolKind,
    val uri: DocumentUri,
    val range: Range,
    val selectionRange: Range,
    val containerName: String?,
    val visibility: Visibility,
    val supertypes: List<String>?,
    val members: List<MemberInfo>?,
    val documentation: String?,
)
```

**Index structure — two-level with trie for prefix matching:**

- **Primary:** Trie-based name index for O(k) prefix search
- **Secondary:** `uri → symbols` for file-level operations, `qualifiedName → symbol` for import resolution
- **Update strategy:** On `didChange` (debounced 300ms), remove all symbols for the changed file, re-parse with tree-sitter, re-add. On `didSave`, immediate re-index.

### 3.3 Import Resolution Strategy

XTC imports follow the pattern `import module.package.TypeName;`. Resolution approach:

1. Parse all import statements from `XtcQueryEngine.findImports(tree)`
2. For each import path, split into segments: `["module", "package", "TypeName"]`
3. Look up in the workspace index by qualified name
4. If found, return the `uri` and `range` from the index entry
5. For wildcard imports (`import module.package.*`), index all exported symbols from that package

**File discovery:** On workspace open, recursively scan for `*.x` files. Parse each with tree-sitter. Extract declarations. Build the index. Report progress via `WorkDoneProgress`.

### 3.4 Go-to-Definition Enhancement

```kotlin
override fun findDefinition(uri: String, line: Int, column: Int): Location? {
    // 1. Try same-file definition (existing tree-sitter logic)
    val localResult = findLocalDefinition(uri, line, column)
    if (localResult != null) return localResult

    // 2. Get the identifier at cursor
    val symbol = findSymbolAt(uri, line, column) ?: return null

    // 3. Check imports in the current file
    val imports = queryEngine.findImports(getTree(uri))
    for (importPath in imports) {
        if (importPath.endsWith(".${symbol.name}")) {
            val indexed = workspaceIndex.findByQualifiedName(importPath)
            if (indexed != null) return indexed.location
        }
    }

    // 4. Fallback: search workspace index by name
    val candidates = workspaceIndex.findByName(symbol.name)
    return candidates.firstOrNull()?.location
}
```

### 3.5 Find-References Enhancement

```kotlin
override fun findReferences(uri: String, line: Int, column: Int, includeDeclaration: Boolean): List<Location> {
    val symbol = findSymbolAt(uri, line, column) ?: return emptyList()

    // 1. Same-file references (existing logic)
    val localRefs = findLocalReferences(uri, symbol.name)

    // 2. Cross-file: search all indexed files for the identifier
    val crossFileRefs = workspaceIndex.findAllOccurrences(symbol.name)
        .filter { it.uri != uri }  // exclude current file (already covered)

    return if (includeDeclaration) localRefs + crossFileRefs
           else (localRefs + crossFileRefs).filter { !it.isDeclaration }
}
```

### 3.6 Implementation Phases

**Phase 1 (tree-sitter + index):** Build workspace symbol index on startup. Resolve imports to files for go-to-definition. Cross-file find-references by name matching.

**Phase 2 (smarter resolution):** Use scope analysis to reduce false positives in references. Rank definition candidates by import relevance.

**Phase 3 (compiler):** Semantic name resolution. Overload disambiguation. Inherited member resolution.

---

## 4. Context-Aware Completion

**LSP Method:** `textDocument/completion`, `completionItem/resolve`

**Impact:** High. Member completion after `.` and `:` is what makes an IDE feel "smart." Without it, the LSP server is just a fancy syntax highlighter.

**Current state:** `TreeSitterAdapter.getCompletions()` returns keywords (43), built-in types (70+), and visible declarations in the current file. Trigger characters `.`, `:`, `<` are registered. No member completion after `.`.

### 4.1 Implementation Tiers

**Tier 1 — No Type Info (current + enhancements):**
- Keywords and built-in types (done)
- Visible declarations in current file (done)
- **New:** All type names from the workspace symbol index
- **New:** Import path completion (after `import `)
- **New:** Snippet completion (e.g., `for` → for-loop template, `if` → if-block template)

**Tier 2 — Partial Type Info (tree-sitter + workspace index):**
- **Member completion after `.`** using heuristic type resolution:
  1. Identify the expression before `.` using tree-sitter
  2. For constructor calls (`new Foo().`), look up `Foo` in the index → return its members
  3. For typed variables (`String name; name.`), look up `String` → return its members
  4. For method calls where return type is in the index, follow one level
- **After `:`** — XTC interface delegation, suggest available interfaces
- **After `extends`/`implements`/`incorporates`** — suggest types from index

**Tier 3 — Full Type Info (compiler):**
- Full type inference through expression chains
- Overload-aware method completion
- Generic type parameter inference
- Expected-type-based ranking
- Smart completions (postfix, auto-casts)

### 4.2 Heuristic Type Resolution for `.` Completion

Without a type checker, we can resolve the receiver type in common cases:

```kotlin
fun resolveReceiverType(tree: XtcTree, line: Int, column: Int): String? {
    val node = tree.findNodeAt(line, column - 1) // node before the dot

    // Case 1: new TypeName()
    if (node.parent?.type == "new_expression") {
        return node.parent.childOfType("type_expression")?.text
    }

    // Case 2: Variable with explicit type annotation
    val varDecl = findEnclosingVariableDeclaration(node)
    if (varDecl?.typeAnnotation != null) {
        return varDecl.typeAnnotation.text
    }

    // Case 3: Parameter with type
    val param = findParameterDeclaration(node.text)
    if (param?.typeAnnotation != null) {
        return param.typeAnnotation.text
    }

    // Case 4: Property with type
    val prop = workspaceIndex.findMember(node.text, enclosingType)
    if (prop?.returnType != null) {
        return prop.returnType
    }

    return null // Cannot determine type
}
```

Then look up members from the workspace index:

```kotlin
fun getMemberCompletions(typeName: String): List<CompletionItem> {
    val typeSymbol = workspaceIndex.findByName(typeName).firstOrNull() ?: return emptyList()
    val members = typeSymbol.members ?: return emptyList()

    // Include inherited members by walking supertypes
    val allMembers = members.toMutableList()
    typeSymbol.supertypes?.forEach { superName ->
        workspaceIndex.findByName(superName).firstOrNull()?.members?.let {
            allMembers.addAll(it)
        }
    }

    return allMembers.map { member ->
        CompletionItem(
            label = member.name,
            kind = member.kind.toCompletionKind(),
            detail = member.signature,
            insertText = if (member.kind == METHOD) "${member.name}($1)" else member.name,
        )
    }
}
```

### 4.3 Completion Resolve

Use `completionItem/resolve` to lazily load documentation and auto-import edits:

```kotlin
override fun resolveCompletionItem(item: CompletionItem): CompletionItem {
    val symbolId = item.data?.asString ?: return item
    val symbol = workspaceIndex.findByQualifiedName(symbolId) ?: return item

    return item.apply {
        documentation = MarkupContent("markdown", symbol.documentation ?: "")
        // Add auto-import if the type isn't already imported
        if (needsImport(symbol)) {
            additionalTextEdits = listOf(createImportEdit(symbol.qualifiedName))
        }
    }
}
```

### 4.4 Trigger Character Handling

```kotlin
fun getCompletions(uri: String, line: Int, column: Int, triggerChar: String?): List<CompletionItem> {
    return when (triggerChar) {
        "." -> getMemberCompletions(resolveReceiverType(tree, line, column))
        ":" -> getInterfaceCompletions()  // after "delegates" or for type constraints
        "<" -> getTypeParameterCompletions()
        else -> getGeneralCompletions(uri, line, column)
    }
}
```

---

## 5. Workspace Symbol Search

**LSP Method:** `workspace/symbol`

**Impact:** Medium-high. `Ctrl+T` (Go to Symbol in Workspace) is a power-user feature used constantly for navigation. Currently returns empty.

**Current state:** `XtcCompilerAdapter.findWorkspaceSymbols()` returns `emptyList()`. The `symbol()` handler in `XtcLanguageServer.kt` already delegates to this method.

### 5.1 Index Strategy

The workspace symbol index (shared with cross-file navigation) provides the data. The search layer adds fuzzy matching.

**Core search algorithm — three-level matching:**

1. **Exact match** (highest score): Query string exactly matches symbol name
2. **CamelCase match**: Query characters match word-boundary letters (`HM` → `HashMap`, `gOD` → `getOrDefault`)
3. **Subsequence match**: Query characters appear in order (`hmap` → `HashMap`)

```kotlin
override fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
    if (query.isBlank()) return emptyList()

    return workspaceIndex.search(query, limit = 200)
        .map { indexed -> indexed.toSymbolInfo() }
}
```

### 5.2 Fuzzy Matching

**CamelCase matching** is the most important strategy for code symbols:

```kotlin
fun camelCaseMatch(query: String, candidate: String): Double? {
    val humps = extractHumps(candidate)  // "HashMap" → ["Hash", "Map"]
    // Match query chars against hump initials, with partial hump matching
    // Returns null if no match, or a score (higher = better)
}

fun extractHumps(name: String): List<String> {
    // Split at uppercase boundaries: "getOrDefault" → ["get", "Or", "Default"]
}
```

**Scoring factors:**
- Position of first match character (earlier = better)
- Whether matches fall on word boundaries
- Contiguity of matched characters
- Length ratio between query and candidate

### 5.3 Index Maintenance

- **Startup:** Parallel scan of all `*.x` files, parse with tree-sitter, extract declarations
- **File changes:** Debounced re-indexing (300ms) on `didChange`, immediate on `didSave`
- **File watcher:** Register for `**/*.x` changes via `workspace/didChangeWatchedFiles`
- **Progress reporting:** Use `WorkDoneProgress` during initial indexing

### 5.4 Persistent Cache (Optimization)

Save the index to disk (JSON or binary) with file checksums. On next startup, load cache, verify checksums, re-index only changed files. Reduces startup time from O(n) full parses to O(changed files).

---

## 6. Type / Call Hierarchy

**LSP Methods:**
- Type: `typeHierarchy/prepareTypeHierarchy`, `typeHierarchy/supertypes`, `typeHierarchy/subtypes`
- Call: `callHierarchy/prepare`, `callHierarchy/incomingCalls`, `callHierarchy/outgoingCalls`

**Impact:** Medium. Power-user features for understanding code structure. Particularly valuable for Ecstasy's rich type system (classes, interfaces, mixins, services, const).

### 6.1 Type Hierarchy

**The three-step protocol:**

1. **Prepare** — Client sends cursor position. Server resolves the type, returns `TypeHierarchyItem[]` with name, kind, URI, range, and opaque `data` for later resolution.
2. **Supertypes** — Client sends a `TypeHierarchyItem`. Server returns its parents (extends, implements, incorporates).
3. **Subtypes** — Client sends a `TypeHierarchyItem`. Server returns all types that extend/implement it.

**Data structure — Type Declaration Index:**

```kotlin
data class TypeEntry(
    val name: String,
    val qualifiedName: String,
    val kind: TypeKind,            // CLASS, INTERFACE, MIXIN, SERVICE, CONST, ENUM
    val uri: DocumentUri,
    val range: Range,
    val selectionRange: Range,
    val supertypes: List<String>,  // qualified names: extends, implements, incorporates
)

class TypeHierarchyIndex {
    private val types: MutableMap<String, TypeEntry>            // qualifiedName → entry
    private val subtypeMap: MutableMap<String, MutableSet<String>>  // parent → children

    fun getSupertypes(fqn: String): List<TypeEntry>
    fun getSubtypes(fqn: String): List<TypeEntry>
    fun getAllSupertypes(fqn: String): List<TypeEntry>  // transitive
}
```

**Building the index from tree-sitter:**

For each type declaration, extract the `extends`/`implements`/`incorporates` clauses. These are syntactically determinable — the supertype names appear as children of the declaration node. Build the forward map (type → supertypes) and reverse map (type → subtypes) simultaneously.

**XTC-specific considerations:**
- **Mixins** (`incorporates`): A type can incorporate multiple mixins, which contribute methods and properties. The hierarchy should show these.
- **Conditional mixins** (`incorporates conditional`): These apply conditionally and should be marked as such in the display.
- **Services**: Services are types. They participate in the hierarchy like classes.
- **Const classes**: Immutable classes. Shown in hierarchy like regular classes.

### 6.2 Call Hierarchy

**The three-step protocol (symmetric with type hierarchy):**

1. **Prepare** — Resolve the function/method at cursor. Return `CallHierarchyItem`.
2. **Incoming calls** — Who calls this function? Return `CallHierarchyIncomingCall[]` with caller items and call-site ranges.
3. **Outgoing calls** — What does this function call? Return `CallHierarchyOutgoingCall[]` with callee items and call-site ranges.

**Data structure — Call Graph Index:**

```kotlin
data class CallSite(
    val callerFqn: String,
    val calleeFqn: String,
    val callRange: Range,
    val callKind: CallKind,        // DIRECT, VIRTUAL, CONSTRUCTOR, LAMBDA, PROPERTY
)

class CallGraphIndex {
    private val outgoingCalls: MutableMap<String, MutableList<CallSite>>  // caller → sites
    private val incomingCalls: MutableMap<String, MutableList<CallSite>>  // callee → sites
}
```

**Syntactic approximation (tree-sitter):**

Use tree-sitter queries to find all call expressions (`method_call`, `function_call`, `new_expression`). For each, determine:
- The enclosing function (the caller)
- The called function name (the callee — may be approximate without type resolution)

This gives useful results even without the compiler: "show all places that call a method named `process`" is valuable even with some false positives.

**Virtual dispatch:** When `obj.method()` is called, record the call to the statically known type's method. At query time, combine with the type hierarchy to show all possible dispatch targets.

### 6.3 Incremental Updates

When a file changes:
1. Remove all type entries and call sites from that file
2. Re-parse and re-extract
3. Reverse maps update automatically (remove old entries, add new)
4. Cross-file references may become stale — validate on query (lazy reconciliation)

### 6.4 Implementation Phases

**Phase 1:** Type hierarchy using tree-sitter-extracted `extends`/`implements`/`incorporates` clauses. No call hierarchy yet.

**Phase 2:** Call hierarchy with syntactic approximation. Direct method calls and constructor calls.

**Phase 3 (compiler):** Precise call resolution with virtual dispatch. Lambda/closure calls. Property getter/setter calls.

---

## 7. Smart Inlay Hints

**LSP Method:** `textDocument/inlayHint`, `inlayHint/resolve`

**Impact:** Medium-high for developer experience. Inferred type annotations and parameter names reduce cognitive load. IntelliJ users expect these.

**Current state:** `TreeSitterAdapter.getInlayHints()` returns `emptyList()`. The capability is already advertised.

### 7.1 Types of Inlay Hints

#### Parameter Name Hints (Tier 1 — needs method signature only)

```
// Source:
processOrder("SKU-123", 5, true)
// Displayed:
processOrder(/* sku: */ "SKU-123", /* quantity: */ 5, /* expedite: */ true)
```

**Requirements:** Resolved method signature (parameter names). Does NOT require type inference.

**When to show (heuristics):**
- Show for literal arguments (numbers, booleans, strings, null) — meaning is unclear
- Show for arguments where variable name doesn't match parameter name
- Do NOT show when argument name already matches parameter name
- Do NOT show for single-parameter functions
- Do NOT show for obvious patterns (setters: `setName("foo")`)

#### Inferred Type Hints — Tier 1 (syntactically determinable)

```
// Source:
var items = new ArrayList<String>();   // hint: ": ArrayList<String>"
var name = "hello";                     // hint: ": String"
var count = 42;                         // hint: ": Int"
```

These are determinable from the literal type or constructor call without type inference.

#### Inferred Type Hints — Tier 2+ (requires method signature lookup)

```
// Source:
var size = list.size();                 // hint: ": Int" (needs return type of size())
```

#### Inferred Type Hints — Tier 3 (requires full type inference)

```
// Source:
var result = items.filter { it.isActive }.map { it.name }
// hint: ": List<String>" — requires generic type propagation through chain
```

### 7.2 Implementation

```kotlin
override fun getInlayHints(uri: String, range: Range): List<InlayHint> {
    val hints = mutableListOf<InlayHint>()
    val tree = getTree(uri) ?: return emptyList()

    // 1. Parameter name hints (Tier 1)
    for (call in findCallExpressionsInRange(tree, range)) {
        val signature = resolveMethodSignature(call) ?: continue
        for ((i, arg) in call.arguments.withIndex()) {
            if (i >= signature.parameters.size) break
            val param = signature.parameters[i]
            if (shouldShowParameterHint(arg, param)) {
                hints.add(InlayHint(
                    position = arg.startPosition,
                    label = "${param.name}:",
                    kind = InlayHintKind.Parameter,
                    paddingRight = true,
                ))
            }
        }
    }

    // 2. Type hints for var/val declarations (Tier 1)
    for (varDecl in findVarDeclsInRange(tree, range)) {
        if (varDecl.hasExplicitType) continue
        val inferredType = inferSimpleType(varDecl.initializer)
        if (inferredType != null) {
            hints.add(InlayHint(
                position = varDecl.nameEndPosition,
                label = ": $inferredType",
                kind = InlayHintKind.Type,
                paddingLeft = true,
            ))
        }
    }

    return hints
}

fun inferSimpleType(expr: Node?): String? = when {
    expr == null -> null
    expr.type == "integer_literal" -> "Int"
    expr.type == "string_literal" -> "String"
    expr.type == "decimal_literal" -> "Dec"
    expr.type == "boolean_literal" -> "Boolean"
    expr.type == "new_expression" -> expr.childOfType("type_expression")?.text
    else -> null  // Tier 2+: would need method signature lookup
}
```

### 7.3 Performance Considerations

Inlay hints are **the most performance-sensitive** LSP feature:
- Requested on every scroll (visible range changes)
- Requested on every edit
- Dozens of hints per viewport

**Mitigations:**
- Only compute for the requested `range` (the visible viewport) — never the whole file
- Cache hints per file version; invalidate on `didChange`
- Debounce recomputation (100-200ms after last keystroke)
- Use `inlayHint/resolve` for lazy tooltip/location loading
- Target < 10ms for Tier 1 hints, < 50ms for Tier 2

### 7.4 Clickable Type Hints

Use `InlayHintLabelPart` so type names in hints link to their definitions:

```kotlin
InlayHint(
    position = pos,
    label = listOf(
        InlayHintLabelPart(": "),
        InlayHintLabelPart("Map", location = mapTypeLocation),
        InlayHintLabelPart("<"),
        InlayHintLabelPart("String", location = stringTypeLocation),
        InlayHintLabelPart(", "),
        InlayHintLabelPart("Int", location = intTypeLocation),
        InlayHintLabelPart(">"),
    ),
    kind = InlayHintKind.Type,
)
```

### 7.5 Implementation Phases

**Phase 1:** Parameter name hints (needs method signature lookup from workspace index). Type hints for literals and constructor calls (purely syntactic).

**Phase 2:** Type hints for simple method calls (needs return type from index). Clickable label parts.

**Phase 3 (compiler):** Full type inference for chained expressions, generic propagation, implicit conversion hints.

---

## 8. Additional LSP Features

Beyond the six high-priority features above, the following LSP capabilities are either partially
implemented or should be on the roadmap.

### 8.1 Document Link Resolution (Medium Priority)

**Current state**: `TreeSitterAdapter.getDocumentLinks()` extracts import statement locations but
returns `target = null` — the links appear in the editor but don't navigate anywhere.

**What's needed**: Once the workspace symbol index (§9.1) and import resolution (§9.3) are built,
document links can resolve `import module.package.TypeName` to the file URI where `TypeName` is
declared. This is a straightforward wire-up once the index exists.

### 8.2 Code Lens (Medium Priority)

**LSP Method**: `textDocument/codeLens`

Code lens shows actionable inline annotations above declarations — reference counts
("3 references"), "Run Test", "Debug", "Implement". Useful for XTC services (show callers),
test methods (run/debug buttons), and interfaces (show implementations).

**Tree-sitter tier**: Reference counts can be approximated once the workspace index exists.
Run/debug buttons need integration with the DAP server and run configurations.

**Compiler tier**: Precise reference counts, virtual dispatch resolution, test discovery.

### 8.3 On-Type Formatting (Low Priority)

**LSP Method**: `textDocument/onTypeFormatting`

Auto-indent when pressing Enter or `}`. Tree-sitter provides enough AST context to determine
correct indentation level. Lower priority because most editors have basic auto-indent built in.

### 8.4 Linked Editing Range (Low Priority)

**LSP Method**: `textDocument/linkedEditingRange`

When renaming an identifier, all related occurrences update simultaneously in real-time
(before committing the rename). Tree-sitter can identify the declaration and its same-file usages.

### 8.5 Pull Diagnostics (Low Priority, Compiler)

**LSP Method**: `textDocument/diagnostic`

Pull-based diagnostic model (client requests diagnostics on demand, vs push-based
`publishDiagnostics`). Only useful once the compiler adapter is online — tree-sitter already
pushes syntax errors via `publishDiagnostics`.

### 8.6 Type Definition / Implementation / Declaration (Compiler)

- `textDocument/typeDefinition` — jump to the type of an expression (e.g., from a variable to
  its class definition). Requires type inference.
- `textDocument/implementation` — find all implementations of an interface or abstract method.
  Requires type hierarchy index + compiler resolution.
- `textDocument/declaration` — distinguish between declaration site and definition site.
  XTC's single-file-per-type model makes this less important than in C/C++.

### 8.7 Features NOT Planned

| Feature | Why Not |
|---------|---------|
| Color Provider | XTC has no color literal syntax |
| Inline Values | Compiler + debugger integration; DAP handles this directly |
| Moniker | Cross-repository symbol linking; far future |

---

## 9. Shared Infrastructure

All features in §2-§8 depend on common infrastructure that should be built first.

### 9.1 Workspace Symbol Index

The core data structure that enables cross-file features. Stores declarations extracted by tree-sitter from all `*.x` files in the workspace.

**Key operations:**
- `addSymbol(symbol)` / `removeSymbolsForUri(uri)` — index maintenance
- `findByName(name)` — exact name lookup
- `findByQualifiedName(fqn)` — import resolution
- `prefixSearch(prefix)` — completion and workspace symbols
- `search(query, limit)` — fuzzy matching for workspace symbol search

**Indexing pipeline:**
1. Workspace open → scan `*.x` files → parse with tree-sitter → extract declarations → build index
2. `didOpen` / `didChange` → re-index the changed file (debounced)
3. `didChangeWatchedFiles` → handle external changes (git checkout, etc.)

### 9.2 Member Info in Index

For each type in the index, store its members (methods, properties, constructors) with signatures. This enables:
- `.` completion
- Parameter name hints
- Basic type inference (return types)

```kotlin
data class MemberInfo(
    val name: String,
    val kind: SymbolKind,
    val signature: String,          // e.g., "(String key, Int value): Boolean"
    val returnType: String?,
    val parameters: List<ParameterInfo>?,
    val isStatic: Boolean,
    val visibility: Visibility,
)
```

### 9.3 Import Resolution

A cross-cutting concern: map import paths to workspace files. Used by:
- Go-to-definition (resolve imported types)
- Completion (auto-import edits)
- Cross-file references (determine which file defines a symbol)

### 9.4 Type Hierarchy Index

Built from tree-sitter-extracted `extends`/`implements`/`incorporates` clauses. Used by:
- Type hierarchy feature directly
- Member completion (inherited members)
- Call hierarchy (virtual dispatch resolution)

---

## 10. Dependency Graph & Implementation Order

```
                 Workspace Symbol Index
                 (tree-sitter-based, all files)
                          |
          ┌───────────────┼───────────────┐
          |               |               |
     Import           Member Info     Type Hierarchy
     Resolution       (signatures)    Index
          |               |               |
    ┌─────┼─────┐    ┌───┼────┐      ┌───┼────┐
    |     |     |    |   |    |      |   |    |
  Go-to  Find  WS   .   Param Call   Type  Call
  Def    Refs  Sym  Comp Hints Hier  Hier  Hier
```

Note: Semantic Tokens Tier 1 is **independent** of the workspace index — it uses only the local
tree-sitter AST. This means it can be built in parallel with the index, giving users the most
visible improvement as early as possible.

### Recommended Build Order

**Sprint 1 — Foundation + Immediate Visual Impact (parallel tracks):**

*Track A — Workspace Symbol Index:*
1. Build `WorkspaceSymbolIndex` with tree-sitter extraction
2. Implement startup workspace scanning with `WorkDoneProgress` reporting
   (critical UX — first-time indexing of a large project can take seconds, users must see progress)
3. Wire up incremental re-indexing on `didChange` (debounced 300ms) and `didSave`

*Track B — Semantic Tokens (no index dependency):*
4. **Semantic Tokens Tier 1** — declaration-site + type positions + annotations + modifiers.
   Purely tree-sitter AST classification. Most visible improvement to users. Register `full` only.

**Sprint 2 — Cross-File Features (index now available):**
5. **Workspace Symbol Search** (wire index to `workspace/symbol` with fuzzy matching)
6. **Cross-file Go-to-Definition** (import resolution → index lookup)
7. **Document Link Resolution** (wire import `target` URIs — trivial once index exists)

**Sprint 3 — Completion & References:**
8. **Cross-file Find References** (workspace-wide name search)
9. **Context-aware Completion** (member completion after `.` with heuristic type resolution)
10. **Import path completion**

**Sprint 4 — Hierarchy & Hints:**
11. **Type Hierarchy** (from tree-sitter extends/implements extraction)
12. **Inlay Hints — Parameter Names** (from method signature index)
13. **Inlay Hints — Tier 1 Types** (literals, constructors)

**Sprint 5 — Polish & Enrichment:**
14. Semantic Tokens Tier 2 (heuristic usage-site tokens)
15. Call Hierarchy (syntactic approximation)
16. Persistent index cache for fast startup
17. `completionItem/resolve` for auto-import and documentation

**Sprint 6 — Additional LSP Features:**
18. **Code Lens** — reference counts (once index exists), run/debug buttons (once DAP is wired)
19. **On-Type Formatting** — auto-indent via tree-sitter AST context

### The Compiler Adapter Milestone

The tree-sitter adapter reaches its ceiling around Sprint 5 — cross-file features work but are
name-based (no type resolution), completion can't resolve overloads, and diagnostics are
syntax-only. The next major capability leap requires a **compiler adapter** that connects the
LSP server to the XTC compiler (`javatools`).

What the compiler adapter unlocks:
- **Full type inference** for completion, inlay hints, and hover (chain resolution, generics)
- **Semantic name resolution** for definition/references (overload disambiguation, inherited members)
- **Semantic diagnostics** beyond syntax errors (type mismatches, unresolved references, unused imports)
- **Delta encoding** for semantic tokens (compiler knows what changed semantically)
- **Type definition / implementation / declaration** (all require resolved types)

What it looks like architecturally:
- A new `CompilerAdapter` implementing `XtcCompilerAdapter` (alongside `TreeSitterAdapter`)
- Wraps the XTC compiler's `FileStructure` / `TypeCompositionStatement` / `MethodStructure` APIs
- Runs the compiler in "analysis mode" (parse + resolve, no code gen)
- Falls back to tree-sitter for features the compiler doesn't cover yet (folding, formatting)
- Incremental: only re-analyze changed files and their dependents

This is a significant engineering effort (likely its own multi-sprint plan) but is the path to
parity with mature language servers like rust-analyzer or gopls.

---

## 11. IntelliJ (LSP4IJ)

The IntelliJ plugin uses **LSP4IJ** (Red Hat's LSP/DAP client) rather than IntelliJ's built-in
LSP support. See `PLAN_IDE_INTEGRATION.md § Design Decision: LSP4IJ over IntelliJ Built-in LSP`
for the rationale. In short: IntelliJ's built-in LSP has no DAP support, no code lens, no call/type
hierarchy, and no LSP console.

### 11.1 What LSP4IJ Provides Automatically

LSP4IJ translates standard LSP responses into IntelliJ UI with no plugin-side code:

| LSP Feature | IntelliJ Integration | Notes |
|-------------|---------------------|-------|
| Hover | Quick Documentation popup (Ctrl+Q) | Renders markdown |
| Completion | Code completion popup | Supports `completionItem/resolve` |
| Definition | Ctrl+Click / Ctrl+B navigation | |
| References | Find Usages (Alt+F7) | |
| Document Symbol | Structure view, breadcrumbs | |
| Document Highlight | Occurrence highlighting | |
| Folding Range | Code folding gutter | |
| Formatting | Code > Reformat (Ctrl+Alt+L) | |
| Rename | Shift+F6 refactor dialog | |
| Code Action | Alt+Enter intention actions | |
| Signature Help | Parameter info popup | |
| Inlay Hints | Inline hints in editor | |
| Semantic Tokens | Semantic highlighting overlay | |
| Code Lens | Inline annotations above declarations | **Not available in built-in LSP** |
| Type Hierarchy | Ctrl+H hierarchy view | **Not available in built-in LSP** |
| Call Hierarchy | Ctrl+Alt+H hierarchy view | **Not available in built-in LSP** |
| Selection Range | Ctrl+W / Ctrl+Shift+W expand/shrink | |
| On-Type Formatting | Auto-indent on Enter | **Not available in built-in LSP** |
| Document Link | Clickable import paths | |
| LSP Console | View > Tool Windows > Language Servers | Protocol-level debugging |
| DAP Client | Debug tool window | **Not available in built-in LSP** |

### 11.2 IntelliJ-Specific Plugin Features (Beyond LSP)

These are features that require IntelliJ-specific code in the plugin, beyond what LSP provides:

| Feature | Status | Notes |
|---------|--------|-------|
| File type registration (`.x`) | Done | `plugin.xml` + `XtcFileType` |
| TextMate grammar (syntax highlighting) | Done | Bundled `.tmLanguage.json` |
| Run configurations | Done | `XtcRunConfigurationType` |
| New Project wizard | Done | `XtcModuleBuilder` |
| JRE provisioning (Foojay) | Done | `JreProvisioner` |
| DAP extension point | Done | `XtcDebugAdapterFactory` |
| Line marker (gutter icons) | Not done | Run/debug icons for `module` declarations |
| Color settings page | Not done | Customizable semantic token colors |
| Intentions beyond code actions | Not done | XTC-specific quick fixes |
| Inspections | Not done | Would duplicate LSP diagnostics — avoid unless needed |
| Live templates / snippets | Not done | `for`, `if`, `switch` templates |

### 11.3 LSP4IJ-Specific Considerations

**Duplicate server spawn (LSP4IJ issue #888)**: LSP4IJ may call `start()` concurrently when
multiple `.x` files are opened, spawning duplicate LSP server processes that are killed within
milliseconds. Harmless but requires an `AtomicBoolean` guard on notifications.
See TODO in `XtcLspConnectionProvider`.

**DAP session lifecycle**: Unlike LSP (auto-start on file open), DAP sessions are user-initiated
(one `startServer()` per debug action). No race condition, no notification guard needed.

**LSP Console**: LSP4IJ provides `View > Tool Windows > Language Servers` for protocol-level
debugging. This is invaluable during development — shows all JSON-RPC request/response pairs.

---

## 12. VS Code

The VS Code extension lives in `lang/vscode-extension/` (scaffolded, not yet functional).

### 12.1 Architecture

```
VS Code Extension (TypeScript/Node.js)
├── package.json           — extension manifest, contributes, activationEvents
├── src/extension.ts       — activation, LanguageClient setup
├── syntaxes/xtc.tmLanguage.json — TextMate grammar (shared with IntelliJ)
└── tree-sitter-xtc.wasm   — tree-sitter grammar (optional, for local parsing)

       │ stdio (JSON-RPC)
       ▼
LSP Server Process (Java 25)    — same xtc-lsp-server.jar as IntelliJ
DAP Server Process (Java 25)    — same xtc-dap-server.jar as IntelliJ
```

### 12.2 LSP Client: `vscode-languageclient`

VS Code's standard LSP client library (`vscode-languageclient`) handles all protocol translation.
The extension only needs to:

1. **Find/provision Java 25** — same Foojay strategy as IntelliJ, but implemented in TypeScript
   (or shell out to a bundled provisioner script)
2. **Locate the server JAR** — bundled in the extension's `bin/` directory
3. **Create a `LanguageClient`** — point it at the server process

```typescript
const serverOptions: ServerOptions = {
    command: javaPath,
    args: ['-jar', serverJarPath],
    options: { cwd: workspaceFolder }
};

const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'xtc' }],
};

const client = new LanguageClient('xtc', 'XTC Language Server', serverOptions, clientOptions);
client.start();
```

All LSP features (hover, completion, definition, etc.) work automatically — `vscode-languageclient`
maps them to VS Code's extension API.

### 12.3 DAP Client: `vscode.debug`

VS Code has **first-class DAP support** built in. The extension registers a debug adapter via
`package.json`:

```json
{
    "contributes": {
        "debuggers": [{
            "type": "xtc",
            "label": "XTC Debug",
            "program": "./bin/xtc-dap-server.jar",
            "runtime": "java",
            "languages": ["xtc"]
        }]
    }
}
```

Or for more control, use a `DebugAdapterDescriptorFactory`:

```typescript
vscode.debug.registerDebugAdapterDescriptorFactory('xtc', {
    createDebugAdapterDescriptor(session) {
        return new vscode.DebugAdapterExecutable(javaPath, ['-jar', dapServerJarPath]);
    }
});
```

### 12.4 Feature Parity with IntelliJ

| Feature | VS Code | IntelliJ (LSP4IJ) |
|---------|---------|-------------------|
| LSP features | All (via vscode-languageclient) | All (via LSP4IJ) |
| DAP debugging | Built-in | Via LSP4IJ DAP client |
| TextMate grammar | Built-in support | Via TextMate bundle plugin |
| Tree-sitter highlighting | Via WASM (optional) | N/A (server-side) |
| JRE provisioning | See §12.5 | `JreProvisioner.kt` (done) |
| Code lens | Built-in support | Via LSP4IJ |
| Semantic tokens | Built-in support | Via LSP4IJ |

**Key difference**: VS Code has native DAP support, making debug integration simpler than IntelliJ
(which requires LSP4IJ). The LSP server and DAP server JARs are identical — only the thin client
wrapper differs.

### 12.5 JRE Provisioning for VS Code

The IntelliJ plugin uses `JreProvisioner.kt` (Kotlin, IntelliJ APIs). VS Code needs its own
approach. Options in order of preference:

| Approach | Pros | Cons |
|----------|------|------|
| **Per-platform extension builds** | No runtime download, instant startup | Larger extension (~40MB per platform), must publish 5 variants |
| **Shell provisioner script** | Simple, reuse Foojay API, language-agnostic | Platform-specific scripts (bash + PowerShell), error handling is fragile |
| **TypeScript Foojay client** | Full control, progress reporting, VS Code API integration | Significant code (~300 lines), duplicates IntelliJ provisioner logic |
| **Require user-installed Java 25** | Zero extension code | Bad UX, most users don't have Java 25 |

**Recommended**: Start with **require user-installed Java 25** (simplest, gets the extension
working) with a `xtc.javaHome` setting. Then add **per-platform builds** that bundle the JRE
for zero-config experience. The per-platform approach is how `rust-analyzer` and other VS Code
extensions handle native dependencies.

### 12.6 VS Code Extension Roadmap

1. **Phase 1**: Basic LSP — TextMate grammar + `LanguageClient` pointing at `xtc-lsp-server.jar`.
   Require `xtc.javaHome` setting or `JAVA_HOME` pointing at Java 25+.
2. **Phase 2**: JRE bundling — per-platform extension builds with bundled Temurin JRE 25
3. **Phase 3**: DAP debugging — register debug adapter, `launch.json` configuration
4. **Phase 4**: Polish — snippets, task definitions, status bar, settings UI

---

## 13. Multi-IDE Strategy

Because the LSP and DAP servers are standalone Java processes communicating over stdio, they work
with **any editor** that supports LSP/DAP. The servers are entirely IDE-independent. This means
adding a new editor is primarily a **configuration problem**, not a coding problem.

### 13.1 IDE Market Share & Prioritization

Data from the 2025 Stack Overflow Developer Survey (49,000+ respondents) and the JRebel 2025
Java Developer Productivity Report. Respondents can select multiple editors, so percentages
sum to more than 100%.

#### Overall Developer Usage

| Rank | IDE/Editor | Usage % | Trend | LSP | DAP |
|------|-----------|---------|-------|-----|-----|
| 1 | **VS Code** | **75.9%** | Growing | Native | Native |
| 2 | Visual Studio | ~29% | Stable | Native | Native |
| 3 | **IntelliJ IDEA** | **~27%** | Stable/growing | Via LSP4IJ | Via LSP4IJ |
| 4 | Notepad++ | ~24% | Declining | No | No |
| 5 | Vim | 24.3% | Growing | Plugin (`coc.nvim`, `vim-lsp`) | Plugin (`vimspector`) |
| 6 | Cursor | 18% | New, fast adoption | Native (VS Code fork) | Native (VS Code fork) |
| 7 | Android Studio | ~16% | Stable | Via LSP4IJ | Via LSP4IJ |
| 8 | **Neovim** | **14%** | Growing fast | **Native** (built-in since 0.5) | Plugin (`nvim-dap`) |
| 9 | **Sublime Text** | **~11%** | Declining | Plugin (`LSP` package, mature) | Plugin (`SublimeDebugger`) |
| 10 | **Eclipse** | **~9.4%** | Declining fast | **Native** (LSP4E) | **Native** (LSP4E Debug) |
| 11 | **Emacs** | **<5%** (est.) | Declining | **Native** (`eglot`, built-in since 29) | Plugin (`dap-mode`, `dape`) |
| 12 | **Zed** | **<3%** (est.) | Growing fast | **Native** (first-class) | **Native** (shipped 2025) |
| 13 | **Helix** | **<1%** (est.) | Growing | **Native** (first-class) | Experimental (built-in) |
| 14 | **Kate** | **<1%** (est.) | Stable/niche | **Native** (built-in) | **Native** (built-in) |

#### Java/JVM-Specific Usage (JRebel 2025)

Particularly relevant for XTC/Ecstasy as a JVM-adjacent language:

| Rank | IDE | Java Usage % | Trend |
|------|-----|-------------|-------|
| 1 | **IntelliJ IDEA** | **84%** | Up from 71% (2024) — dominant |
| 2 | **VS Code** | **31%** | Stable, secondary editor for many |
| 3 | **Eclipse** | **28%** | Down from 39% (2024) — significant decline |

42% of Java developers use more than one IDE. 68% of IntelliJ users also use VS Code.

#### Admiration / Satisfaction Ratings

| IDE/Editor | Admiration % |
|-----------|-------------|
| **Neovim** | **~83%** (highest of any editor) |
| VS Code | 62.6% |
| Vim | 59.3% |
| IntelliJ IDEA | 58.2% |

Neovim users are disproportionately influential: they write blog posts, create tutorials, and
evangelize tools. High satisfaction means high amplification.

### 13.2 Priority Ranking (After IntelliJ + VS Code)

| Priority | Editor | Est. Reach | Effort | ROI | Rationale |
|----------|--------|-----------|--------|-----|-----------|
| **1** | **Neovim** | 14% | Very low (~20 lines Lua) | **Very High** | Highest satisfaction, tree-sitter synergy, influential community |
| **2** | **Helix** | <1% | Minimal (~10 lines TOML) | **High** | Trivial effort, tree-sitter native, early adopters amplify |
| **3** | **Eclipse** | 9.4% (28% Java) | Medium (small plugin) | **High** | Second-largest Java audience, same LSP4J types |
| **4** | **Sublime Text** | ~11% | Low (settings JSON) | Medium | Existing TextMate grammar works directly |
| **5** | **Zed** | <3% | Low (extension/TOML) | Medium-High | Fastest-growing new editor, native LSP+DAP, tree-sitter native |
| **6** | **Emacs** | <5% | Low-medium (elisp) | Medium | Passionate community, `eglot` now built-in |
| **7** | **Vim** | 24.3% | Medium (plugin config) | Medium | Large base but migrating to Neovim |
| **8** | **Kate** | <1% | Minimal (settings) | Low | Trivial but tiny audience |

### 13.3 Effort Estimate Per Editor

| Editor | New Code Required | Reuses | Deliverable |
|--------|------------------|--------|-------------|
| **Neovim** | ~20 lines Lua | LSP server, DAP server, tree-sitter grammar | `ftplugin/xtc.lua` + PR to `nvim-lspconfig` + `nvim-treesitter` registration |
| **Helix** | ~10 lines TOML | LSP server, DAP server, tree-sitter grammar | `languages.toml` snippet + upstream PR to `helix-editor/helix` |
| **Eclipse** | ~200-500 lines Java | LSP server, DAP server, TextMate grammar | Eclipse marketplace plugin via LSP4E |
| **Sublime Text** | ~20 lines JSON | LSP server, DAP server, TextMate grammar | Sublime Text package or config guide |
| **Zed** | ~30 lines config | LSP server, DAP server, tree-sitter grammar | Zed extension or `languages.toml` |
| **Emacs** | ~50-100 lines elisp | LSP server, DAP server, tree-sitter grammar | `xtc-mode.el` with eglot/dap-mode config (MELPA submission) |
| **Vim** | ~30 lines config | LSP server, DAP server | Config examples for `coc.nvim` and `vim-lsp` |
| **Kate** | ~10 lines config | LSP server, DAP server | Settings config snippet |

Editors 1, 2, 4, 5, 7, 8 are **configuration-only** — no new compiled code required. Only
Eclipse (3) and Emacs (6) need actual development work, and even those are modest.

### 13.4 Protocol Support Details

#### Eclipse (Priority 3)

Eclipse has built-in LSP support via **Eclipse LSP4E** (`org.eclipse.lsp4e`):

- Register a content type for `.x` files
- Point `LanguageServerDefinition` at `java -jar xtc-lsp-server.jar`
- DAP: Eclipse has built-in DAP support via **Eclipse LSP4E Debug**
- Compatibility advantage: both LSP4E and our server use Eclipse LSP4J types

#### Neovim (Priority 1)

Neovim has built-in LSP client (`vim.lsp`) since 0.5:

```lua
-- LSP configuration (~10 lines)
vim.lsp.start({
    name = 'xtc',
    cmd = { 'java', '-jar', '/path/to/xtc-lsp-server.jar' },
    root_dir = vim.fs.dirname(vim.fs.find({ '.git', 'build.gradle.kts' }, { upward = true })[1]),
})
```

DAP via `nvim-dap` (4,500+ GitHub stars, community standard):

```lua
-- DAP configuration (~10 lines)
require('dap').adapters.xtc = {
    type = 'executable',
    command = 'java',
    args = { '-jar', '/path/to/xtc-dap-server.jar' },
}
```

Tree-sitter grammar can be registered with `nvim-treesitter` for syntax highlighting,
providing the same grammar already used by the LSP server.

Ideal deliverable: submit config to `nvim-lspconfig` (community repo) for one-line setup,
and register grammar with `nvim-treesitter`.

#### Helix (Priority 2)

Helix uses `languages.toml` for all language configuration — no plugins needed:

```toml
[[language]]
name = "xtc"
scope = "source.xtc"
file-types = ["x"]
language-servers = ["xtc-lsp"]
indent = { tab-width = 4, unit = "    " }

[language-server.xtc-lsp]
command = "java"
args = ["-jar", "/path/to/xtc-lsp-server.jar"]
```

DAP support is experimental but built-in. Submit upstream PR to `helix-editor/helix`.

#### Zed (Priority 5)

Zed has first-class LSP and DAP support (DAP shipped 2025). Built by the creators of Atom
and Tree-sitter — the tree-sitter-native architecture aligns perfectly with the XTC toolchain.
Languages can be added via extensions that configure LSP/DAP servers.

#### Sublime Text (Priority 4)

The `LSP` package is mature and well-maintained. `SublimeDebugger` provides DAP support.
Both installable via Package Control. The existing TextMate grammar (`.tmLanguage.json`)
works directly for syntax highlighting — no additional work needed.

#### Emacs (Priority 6)

`eglot` is now built into Emacs 29+ (making LSP support effectively native). `lsp-mode`
is the more feature-rich alternative. For DAP, `dap-mode` works with `lsp-mode`, and the
newer `dape` package works independently. An `xtc-mode.el` package would provide major mode
+ LSP/DAP configuration.

### 13.5 Shared Assets

The `lang/dsl/` module generates shared language assets that all editors can use:

| Asset | Generated By | Used By |
|-------|-------------|---------|
| TextMate grammar (`.tmLanguage.json`) | `lang/dsl/` | VS Code, IntelliJ, Sublime, Zed, others |
| Tree-sitter grammar | `lang/tree-sitter/` | Neovim, Helix, Zed, Emacs, LSP server |
| Vim syntax file | `lang/dsl/` (planned) | Vim, Neovim (fallback) |
| Emacs major mode | `lang/dsl/` (planned) | Emacs (fallback) |

### 13.6 What's IDE-Specific vs Shared

```
SHARED (IDE-independent, reused across ALL editors):
├── lang/lsp-server/     — LSP server JAR (stdio, Java 25)
├── lang/dap-server/     — DAP server JAR (stdio, Java 25)
├── lang/dsl/            — Language model → TextMate, tree-sitter, vim, emacs
└── lang/tree-sitter/    — Grammar + native libs (WASM for VS Code, .so/.dylib for server)

IDE-SPECIFIC (thin wrappers — always editor-specific):
├── lang/intellij-plugin/  — IntelliJ (LSP4IJ, JRE provisioning, run configs)
├── lang/vscode-extension/ — VS Code (vscode-languageclient, launch.json)
└── (future configs)       — Neovim, Helix, Zed, etc. (just config files, no code)
```

The architectural principle: **servers are source of truth, IDE plugins are thin wrappers.**
Adding a new editor means writing a small configuration/wrapper — the LSP and DAP servers
provide all the intelligence.

### 13.7 The Shared Bottleneck: DAP Server

The DAP server (Phases 1-6, ~7-10 weeks, see `plan-dap-debugging.md`) is the shared bottleneck
for debugging support across all editors. Once the DAP server is functional, **every editor gets
debugging support simultaneously** through the same `xtc-dap-server.jar`. This makes investing
in the shared DAP implementation the highest-leverage work for multi-IDE support.

### 13.8 Recommended Rollout Plan

**Wave 1 (alongside VS Code extension completion):**
Ship Neovim + Helix configs. Combined ~30 lines of configuration. Validates the tree-sitter
grammar in its native habitat and reaches the most enthusiastic editor communities.

**Wave 2 (after VS Code extension is stable):**
Sublime Text config + Zed extension. Low effort, broadens reach.

**Wave 3 (dedicated sprint):**
Eclipse plugin. Only editor requiring real development work that serves a large Java audience.

**Wave 4 (community contributions welcome):**
Emacs package, Vim configs, Kate settings. Publish config snippets and invite community PRs.
