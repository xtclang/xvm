# XTC LSP Feature Tiers: Tree-Sitter Now, Semantic Model Next, Full Compiler Later

## Purpose

This document maps every LSP feature to one of three implementation tiers:

1. **Tier 1 — Tree-sitter (now)**: What we can implement today with the existing
   tree-sitter adapter, workspace index, and semantic token encoder.
2. **Tier 2 — Semantic Model (next)**: What becomes possible once the research-fork
   `lsp-compiler` (Kotlin lexer + parser + SemanticModel) is integrated.
3. **Tier 3 — Full Compiler (later)**: What requires the complete XTC compilation
   pipeline including bytecode emission and runtime support.

Each feature lists its current state, what can be improved at each tier, and the
specific APIs or data structures needed.

---

## Quick Reference: Current State

### Advertised LSP Capabilities (XtcLanguageServer.buildServerCapabilities)

| Capability | Advertised | Implementation | Tier for "Done" |
|------------|-----------|----------------|-----------------|
| textDocumentSync (Full) | Yes | Full reparse on every change | 1 (incremental in T2) |
| completionProvider | Yes | Keywords + built-ins + same-file symbols + imports | 1+ (member completion in T2) |
| hoverProvider | Yes | Symbol name + kind + type signature | 1+ (type info in T2) |
| definitionProvider | Yes | Same-file + cross-file via workspace index | 1+ (semantic in T2) |
| referencesProvider | Yes | Same-file text matching + cross-file index | 1+ (scope-aware in T2) |
| documentSymbolProvider | Yes | Tree-sitter query extraction | 1 (done) |
| documentHighlightProvider | Yes | Same-file text matching | 1+ (scope-aware in T2) |
| documentFormattingProvider | Yes | AST-aware re-indentation | 1 (done) |
| documentRangeFormattingProvider | Yes | AST-aware range re-indentation | 1 (done) |
| documentOnTypeFormattingProvider | Yes | Auto-indent on Enter, }, ), ; | 1 (done) |
| codeActionProvider | Yes | Organize imports | 1+ (more actions in T2) |
| renameProvider + prepare | Yes | Same-file text-based rename | 1+ (scope-aware in T2) |
| foldingRangeProvider | Yes | Declaration + comment + import folding | 1 (done) |
| selectionRangeProvider | Yes | AST-based expand/shrink selection | 1 (done) |
| signatureHelpProvider | Yes | Same-file method parameter info | 1+ (cross-file in T2) |
| semanticTokensProvider | Yes | 10 token types, 4 modifiers | 1 (done) |
| documentLinkProvider | Yes | Import statement highlighting | 1 (done) |
| inlayHintProvider | Yes | Stub (returns empty) | 2 |
| declarationProvider | No | Stub (returns null) | 2 |
| typeDefinitionProvider | No | Stub (returns null) | 2 |
| implementationProvider | No | Stub (returns empty) | 2 |
| typeHierarchyProvider | No | Stub | 2 |
| callHierarchyProvider | No | Stub | 2 |
| codeLensProvider | No | Stub | 2-3 |
| linkedEditingRangeProvider | No | Stub | 2 |
| workspaceSymbolProvider | Yes | Cross-file fuzzy search via WorkspaceIndex | 1 (done) |

---

## Tier 1: What Tree-Sitter Can Still Do (Implement Now)

These features use only the tree-sitter parse tree, workspace index, semantic token
encoder, and XTC query engine. No semantic model needed.

### 1.1 DONE — Already Implemented

| Feature | Status | Quality |
|---------|--------|---------|
| Document symbols (outline) | Done | Good — all declaration types |
| Document formatting (re-indent) | Done | Good — structural depth counting |
| Range formatting | Done | Good — scoped to range |
| On-type formatting (Enter, }, ), ;) | Done | Good — 6 trigger types |
| Folding ranges | Done | Good — declarations, comments, imports |
| Selection ranges | Done | Good — AST walk from leaf to root |
| Semantic tokens | Done | Good — 10 types, 4 modifiers |
| Same-file go-to-definition | Done | Text-based name matching |
| Cross-file go-to-definition | Done | WorkspaceIndex (by name, prefers types) |
| Same-file find references | Done | Text-based name matching |
| Workspace symbol search | Done | Fuzzy matching (exact/prefix/camelCase/subsequence) |
| Document highlights | Done | All occurrences of identifier text |
| Completion (keywords + types + locals) | Done | Keywords, built-in types, same-file symbols, imports |
| Signature help (same-file) | Done | Parameter info for same-file methods |
| Same-file rename | Done | Text-based identifier replacement |
| Code action: organize imports | Done | Sort import statements |
| Document links (imports) | Done | Highlight import paths |
| Syntax error diagnostics | Done | Tree-sitter error/missing nodes |

### 1.2 CAN DO NOW — Improvements Using Tree-Sitter

These require no new infrastructure — just better use of what we have.

#### 1.2.1 Enhanced Completion (tree-sitter)

**What**: Add context-aware completion filtering.
- After `.`: suggest member names from the same class (extracted from AST siblings)
- After `import`: suggest package/module paths from workspace index
- In type position: filter to only type-like completions (classes, interfaces, enums)
- Snippet completions: `if () {}`, `for () {}`, `switch () {}`, `try {} catch {}`

**How**: Check the AST node at cursor position to determine context. The tree-sitter
grammar already distinguishes `member_expression`, `import_statement`, type positions,
etc. Use `tree.nodeAt(line, col)` to determine which completion set to offer.

**Effort**: Medium. Add `getCompletionContext()` that examines the cursor's AST ancestry,
then filter/augment the completion list accordingly.

#### 1.2.2 Smarter Document Highlights

**What**: Distinguish read vs write occurrences of a variable.
- Assignment targets → `HighlightKind.WRITE`
- All other references → `HighlightKind.READ`

**How**: When the identifier appears as the left-hand side of an `assignment_statement`
or in a `var_declaration`, mark it as WRITE. Tree-sitter provides `assignment_statement`
nodes with a `left` field. Check if the highlighted identifier is under the `left` child.

**Effort**: Small. Add a check in `getDocumentHighlights()` for the parent node type.

#### 1.2.3 More Code Actions

**What**:
- **Generate doc comment**: Insert a `/** */` skeleton above a method/class declaration
- **Add missing import**: When an identifier matches a workspace index symbol, offer
  to add the import (similar to "auto-import" in IntelliJ)
- **Convert string to template**: Detect string concatenation and offer to convert to
  template string `$"...{expr}..."`
- **Remove unused import**: Detect imports whose name doesn't appear in the file

**How**: For auto-import, cross-reference unresolved identifiers (text not matching any
local declaration) against the workspace index. For unused imports, check if the imported
simple name appears anywhere else in the file text.

**Effort**: Medium per action. Each is a self-contained code action builder.

#### 1.2.4 Breadcrumb / Sticky Headers Support

**What**: Return the chain of containing declarations for any position (module > class >
method). This feeds IntelliJ's breadcrumb bar and VS Code's sticky scroll.

**How**: Walk `tree.nodeAt(line, col)` up through ancestors, collecting declaration nodes.
This is already how `findDeclarationNode()` works; just collect the full chain instead of
stopping at the first match.

**Effort**: Small. Exposed via `textDocument/documentSymbol` (already implemented) with
hierarchical nesting.

#### 1.2.5 Better Semantic Tokens

**What**: Distinguish more token types using tree-sitter grammar context.
- **Enum members** vs class instances (check parent node type)
- **Interface declarations** vs class declarations (already done but verify)
- **Constructor calls** vs method calls (check `new_expression` parent)
- **Static methods** (tree-sitter can't detect this, but annotation-based `@Static` can)
- **Deprecated** modifier (if `@Deprecated` annotation present in AST)

**How**: Examine the AST ancestry of each identifier during semantic token encoding.
The `SemanticTokenEncoder` already walks the tree; add more `when` branches for parent
node types.

**Effort**: Small per token type. Cumulative improvement to syntax highlighting.

#### 1.2.6 Formatting: Multi-Line Parameter Alignment

**What**: Align parameters in multi-line function calls/declarations with the opening
paren, rather than using pure structural depth.

**How**: When a line is inside a parenthesized construct that spans multiple lines,
detect the opening `(` column and use it + 1 as the indent. Already have paren detection
logic in `handleCloseParen()`.

**Effort**: Medium. Needs a new rule in `computeLineIndent` for lines inside multi-line
paren constructs.

#### 1.2.7 Go-To-Definition for Imports

**What**: `Ctrl+click` on an import path navigates to the imported module/class file.

**How**: Parse the import path, search the workspace index for a matching module/class
symbol, return its file location. The workspace index already supports `findByName()`.
The `DocumentLink` implementation already identifies import locations; extend it to
provide actual navigation targets.

**Effort**: Small-medium. The `target` field of `DocumentLink` is currently `null` —
populate it with the workspace index lookup result.

#### 1.2.8 Folding: Consecutive Line Comments

**What**: Fold consecutive `// ...` comment lines as a block.

**How**: In `collectFoldingRanges()`, detect runs of adjacent line comment nodes and
create a single folding range spanning the group.

**Effort**: Small.

#### 1.2.9 Formatting: Blank Line Normalization (Optional)

**What**: Ensure exactly N blank lines between methods, between classes, etc.
Configurable via `XtcFormattingConfig`.

**How**: During `buildFormattingEdits()`, track the previous non-blank line's AST context.
When transitioning between declarations, insert or remove blank lines to match the
configured count.

**Effort**: Medium. Requires line insertion/removal edits, not just indent changes.

---

## Tier 2: What the Semantic Model Enables (After lsp-compiler Integration)

The research-fork `lsp-compiler` provides:
- **Kotlin lexer** (incremental, reentrant, full trivia preservation)
- **Kotlin parser** (immutable AST, 100% corpus coverage, error recovery)
- **SymbolTable** (hierarchical scopes, all declarations registered)
- **ResolvedModel** (identifier → symbol bindings, import resolution)
- **TypedModel** (type expressions → semantic Type values)
- **ExpressionTypeResult** (expression → Type, 85.3% coverage)
- **MemberIndex** (flattened inheritance, O(1) member lookup per class)
- **FlowAnalysis** (type narrowing, null checks, reachability)
- **SemanticModel** (unified query API: `symbolAt()`, `typeOf()`, `callResolution()`)

### Integration Path

The `lsp-compiler` produces a `SemanticModel` per file (or per module). The LSP server
would call `SemanticModel.build(ast, symbolTable, ...)` after parsing, then query it
for semantic information. The tree-sitter adapter would be extended (or composed with
a semantic adapter) to use `SemanticModel` results.

**Key architectural decision**: The `SemanticModel` is immutable and thread-safe.
Multiple LSP requests can query the same model concurrently. When a file changes,
rebuild the model (incrementally in the future via `FrozenSymbolInterner.derive()`).

### 2.1 Tier 2 Features

#### 2.1.1 Type-Aware Hover

**What**: Show the resolved type of any expression on hover.
- Variable: `val name: String` → "String"
- Method call: `list.size` → "Int"
- Complex expression: `map[key]?.toString()` → "String?"

**Requires**: `SemanticModel.typeOf(expr)` for the expression at cursor position.

#### 2.1.2 Member Completion (After Dot)

**What**: After typing `.`, show only the members that exist on the receiver type.
- `myList.` → `add`, `remove`, `size`, `iterator`, `map`, `filter`, ...
- `myString.` → `size`, `indexOf`, `substring`, `toUpper`, ...

**Requires**: `SemanticModel.typeOf(receiver)` to get the receiver type, then
`MemberIndex.lookup(type, "")` to enumerate all members.

#### 2.1.3 Semantic Go-To-Definition

**What**: Navigate to the actual declaration of any symbol, across files and through
inheritance chains.
- Method call → method declaration (even if inherited from a superclass)
- Type reference → type declaration file
- Overridden method → the override chain

**Requires**: `SemanticModel.symbolAt(node)` returns the `Symbol` with its declaration
`Span` (file + line + column).

#### 2.1.4 Scope-Aware Find References

**What**: Find all references to a symbol that actually refer to the same declaration
(not just same-name text matching).
- Distinguishes `foo` the local variable from `foo` the method parameter in a different scope
- Cross-file references via resolved imports

**Requires**: Walk all files' `ResolvedModel.bindings`, filter entries whose value is
the target symbol.

#### 2.1.5 Scope-Aware Rename

**What**: Rename a symbol across all files, only changing references that resolve to
the same declaration.

**Requires**: Same as scope-aware find references, plus workspace edit generation.

#### 2.1.6 Inlay Hints

**What**: Show inferred types, parameter names, and other annotations inline.
- Variable type: `val x = 42` → shows `: Int` after `x`
- Parameter names: `foo(42, "hello")` → shows `count:` and `message:` before arguments
- Method return type: `void bar()` inferred return

**Requires**: `SemanticModel.typeOf(varDecl)` for variable types,
`SemanticModel.callResolution(call)` for parameter names.

#### 2.1.7 Go-To-Type-Definition

**What**: Navigate from a variable to the type definition of its declared/inferred type.
- `val x: HashMap<String, Int>` → Ctrl+click goes to `HashMap` class

**Requires**: `SemanticModel.typeOf(symbol)` → resolve to the type's declaration symbol.

#### 2.1.8 Find Implementations

**What**: Find all classes that implement an interface, all methods that override a
base method.

**Requires**: `MemberIndex` inheritance chain traversal across all indexed files.
Walk all classes, check if they implement/extend the target type.

#### 2.1.9 Type Hierarchy

**What**: Show the supertype/subtype hierarchy for a class.
- Supertypes: walk `extends`, `implements`, `incorporates` chains
- Subtypes: reverse index — all classes that extend/implement this type

**Requires**: `MemberIndex.contributions` for supertypes, reverse index for subtypes.

#### 2.1.10 Call Hierarchy

**What**: Show callers (incoming) and callees (outgoing) for a method.
- Incoming: all call sites that resolve to this method
- Outgoing: all calls made within this method body

**Requires**: `ExpressionTypeResult.callResolutions` for outgoing calls,
cross-file reference search for incoming calls.

#### 2.1.11 Semantic Diagnostics

**What**: Report type errors, unresolved references, unused variables, unreachable code.
- "Type 'String' is not assignable to 'Int'"
- "Unresolved reference: 'foo'"
- "Variable 'x' is never used"
- "Unreachable code after 'return'"

**Requires**: `SemanticModel.diagnostics` (parsed + resolved + typed + validated),
`FlowAnalysis.unreachableStatements`.

#### 2.1.12 Linked Editing Ranges

**What**: When renaming a local variable, all occurrences in the same scope update
simultaneously as you type (like IntelliJ's "Rename in-place").

**Requires**: Scope-aware reference collection for the symbol at cursor.

#### 2.1.13 Code Actions: Auto-Import

**What**: When an identifier is unresolved but matches a symbol in another file, offer
to add the import automatically.

**Requires**: `ResolvedModel.unresolved` list cross-referenced against workspace index.

#### 2.1.14 Code Actions: Override Methods

**What**: In a class body, offer to generate override stubs for abstract methods from
supertypes.

**Requires**: `MemberIndex` for the class, walk supertype members, filter abstract
methods not yet overridden.

#### 2.1.15 Code Actions: Extract Method / Variable

**What**: Select an expression → extract to a local variable. Select statements →
extract to a method.

**Requires**: `SemanticModel.typeOf(expr)` for the extracted expression's type,
scope analysis for captured variables.

#### 2.1.16 Incremental Recompilation

**What**: When a file changes, only re-analyze the changed file and its dependents,
not the entire workspace.

**Requires**: `FrozenSymbolInterner.derive()` for copy-on-write symbol table updates.
Dependency tracking between files (import graph).

---

## Tier 3: What Requires the Full Compiler (Later)

These features need capabilities beyond the semantic model — runtime evaluation,
bytecode analysis, or features not yet in the lsp-compiler.

### 3.1 Code Lens: Run/Debug

Show "Run" / "Debug" lenses above test methods or `main()` entry points.
Requires: XTC runtime integration, test framework detection.

### 3.2 Debugger Integration (DAP)

Step-through debugging with variable inspection, breakpoints, call stacks.
Requires: XTC runtime + Debug Adapter Protocol implementation.

### 3.3 Evaluate Expression

Evaluate arbitrary XTC expressions in debug context.
Requires: XTC runtime interpreter.

### 3.4 Full Bytecode Parity

The lsp-compiler achieves 40.4% method bytecode equivalence with the Java compiler.
Closing this gap requires completing:
- Complex generic type inference
- Full type constraint validation
- Complete op emission for all expression types

### 3.5 Conditional Compilation / Configuration Analysis

Analyze `@Conditional` annotations and `assert:debug` / `assert:test` blocks.
Requires: Build configuration context not available at analysis time.

---

## Implementation Priority (Recommended Order)

### Phase A: Quick Tree-Sitter Wins (Now, ~1-2 weeks)

| Priority | Feature | Section | Effort |
|----------|---------|---------|--------|
| 1 | Context-aware completion filtering | 1.2.1 | Medium |
| 2 | Go-to-definition for imports | 1.2.7 | Small |
| 3 | Read/write highlight distinction | 1.2.2 | Small |
| 4 | Auto-import code action | 1.2.3 | Medium |
| 5 | Better semantic token types | 1.2.5 | Small |
| 6 | Remove unused import action | 1.2.3 | Small |
| 7 | Folding consecutive line comments | 1.2.8 | Small |
| 8 | Generate doc comment action | 1.2.3 | Small |

### Phase B: Semantic Model Integration (~2-4 weeks)

| Priority | Feature | Section | Effort |
|----------|---------|---------|--------|
| 1 | Integrate lsp-compiler build | — | Large (one-time) |
| 2 | Type-aware hover | 2.1.1 | Small (once integrated) |
| 3 | Member completion (after dot) | 2.1.2 | Medium |
| 4 | Semantic diagnostics | 2.1.11 | Medium |
| 5 | Inlay hints (types + param names) | 2.1.6 | Medium |
| 6 | Semantic go-to-definition | 2.1.3 | Small |
| 7 | Scope-aware references | 2.1.4 | Medium |
| 8 | Scope-aware rename | 2.1.5 | Medium |
| 9 | Type hierarchy | 2.1.9 | Medium |
| 10 | Find implementations | 2.1.8 | Medium |
| 11 | Call hierarchy | 2.1.10 | Large |
| 12 | Auto-import with type resolution | 2.1.13 | Medium |
| 13 | Override method stubs | 2.1.14 | Medium |

### Phase C: Full Compiler Features (Future)

| Feature | Section | Depends On |
|---------|---------|------------|
| Run/Debug code lens | 3.1 | XTC runtime |
| DAP debugger | 3.2 | XTC runtime |
| Expression evaluation | 3.3 | XTC runtime |

---

## What the lsp-compiler Specifically Provides for Each Feature

| LSP Feature | lsp-compiler API | Key Classes |
|-------------|-----------------|-------------|
| Type hover | `SemanticModel.typeOf(expr)` | `ExpressionTyper`, `Type` (14 variants) |
| Member completion | `MemberIndex.lookup(type, prefix)` | `MemberInfo`, `MemberResolver` |
| Go-to-def (semantic) | `SemanticModel.symbolAt(node)` → `Symbol.span` | `SymbolResolver`, `ResolvedModel` |
| Find references (semantic) | `ResolvedModel.bindings` filtered by symbol | `SymbolResolver` |
| Inlay hints | `SemanticModel.typeOf(varDecl)`, `callResolution(call)` | `ExpressionTyper`, `ExpressionTypeResult` |
| Type definition | `SemanticModel.typeOf(symbol)` → `Type.Named.symbol.span` | `TypeResolver`, `Type.Named` |
| Implementations | `MemberIndex.contributions` + reverse index | `MemberInfo`, `MemberIndex` |
| Type hierarchy | `MemberIndex.contributions` (supers), reverse for subs | `MemberInfo` |
| Call hierarchy | `ExpressionTypeResult.callResolutions` | `ExpressionTyper` |
| Diagnostics | `SemanticModel.diagnostics` | `Validator`, `FlowAnalysis` |
| Rename (semantic) | `ResolvedModel.bindings` + `WorkspaceEdit` | `SymbolResolver` |
| Linked editing | `ResolvedModel.bindings` filtered by scope | `SymbolResolver` |
| Override stubs | `MemberIndex.abstractMembers(type)` | `MemberInfo` |
| Extract method | `SemanticModel.typeOf()` + scope capture analysis | `ExpressionTyper`, `FlowAnalysis` |
| Incremental recompile | `FrozenSymbolInterner.derive()` | `SymbolInterner` |

---

## Current Tree-Sitter Ceiling

Tree-sitter gives us **syntax-level intelligence** — excellent for:
- Structure (symbols, folding, selection, breadcrumbs)
- Formatting (indentation, whitespace)
- Syntax highlighting (semantic tokens at grammar level)
- Text-based navigation (same-name matching, workspace index)
- Simple refactoring (text-based rename, organize imports)

Tree-sitter **cannot** provide:
- Type information (what type is this expression?)
- Scope-aware resolution (which `foo` does this refer to?)
- Inheritance-based member lookup (what members does this type have?)
- Cross-file semantic navigation (go to the actual declaration, not just same-name)
- Type error detection (is String assignable to Int?)
- Flow-sensitive analysis (is this variable null here?)

The Tier 1.2 improvements squeeze the last useful features out of tree-sitter.
After those, every meaningful improvement requires the semantic model from Tier 2.
