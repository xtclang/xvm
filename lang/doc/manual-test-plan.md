# Ecstasy Language Server - Manual Test Plan

This document describes how to manually test every feature implemented in the Ecstasy Language Server and IntelliJ plugin.

## Feature Implementation Status

> See [plan-ide-integration.md](plans/plan-ide-integration.md) for the canonical feature implementation matrix comparing Mock, Tree-sitter, and Compiler adapter capabilities.

---

## Pre-Test Setup

### 1. Build with Specific Adapter

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Build with tree-sitter adapter (the shipped default)
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Or opt into XdkAdapter - compiler diagnostics and semantic IDE features
./gradlew :lang:lsp-server:build -Plsp.adapter=compiler

# Or with mock adapter (no native dependencies)
./gradlew :lang:lsp-server:build -Plsp.adapter=mock
```

**The compiler adapter uses the core/bootstrap libraries bundled with the server.** Gradle builds
them through the composite module dependencies and packages them as resources. No external XDK or
`XDK_HOME` setting is required for compiler analysis. A Kotlin host API can now supply additional
dependency artifacts/source indices and explicit source roots/edges for automatic recompilation.
VS Code exposes source roots/edges through `xtc.compiler.sourceModules`; automatic project discovery
remains separate work. The ordinary fixtures below need only the bundled libraries; the
source-project checks use the explicit settings shown in the XdkAdapter playbook.

It is also the slow one, deliberately: the first compilation in a session takes about a second
(class loading, reading the XDK, a JIT still warming up) and then settles to about 60ms. If the
first file you open seems to hang for a moment, that is what it is.

### 2. Launch in Your Editor

**IntelliJ:**
```bash
./gradlew :lang:runIntellijPlugin
```

**VS Code:**
```bash
# Build and install the extension
cd lang/vscode-extension
npm install && npm run compile
npx vsce package          # creates xtc-language-*.vsix
code --install-extension xtc-language-*.vsix

# Ensure the LSP server fat JAR exists
ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar

# Open a folder with .x files
code /path/to/xtc-project
```

The extension starts the LSP server automatically when a `.x` file is opened.
Requires `JAVA_HOME` or `XTC_JAVA_HOME` pointing to Java 25+.

### 3. Verify Which Adapter is Active

The server runs out of process and writes its own log to `~/.xtc/logs/lsp-server.log`, for both
editors. It announces itself on startup:

```
========================================
Ecstasy Language Server v<version>
backend: Tree-sitter
log file: /Users/you/.xtc/logs/lsp-server.log
========================================
```

The `backend:` line is the answer:
- `backend: Tree-sitter` - tree-sitter is active
- `backend: XTC Compiler` - the real compiler is active
- `backend: Mock` - mock adapter is active
- `backend: Mock` **together with** `tree-sitter was requested but failed to initialize` -
  tree-sitter was asked for and fell back. The two lines together are the fallback; the `backend:`
  line alone does not say whether it was chosen or settled for

Clients can also ask over JSON-RPC (`xtc/healthCheck`), which answers with `adapter` set to
`TreeSitter`, `XDK` or `Mock`.

**IntelliJ:**
1. Help → Show Log in Finder/Explorer for the IDE-side log, or read the server log directly
2. Find the banner above
3. For Tree-sitter, also verify: `"semantic tokens ENABLED (23 types, 10 modifiers)"` in the log
4. For IntelliJ plugin runs from this repo using Tree-sitter, also verify the startup command line in the IDE log:
   - `XTC LSP command configured`
   - `-Dxtc.lsp.semanticTokens=true`
   This confirms you are exercising the branch's semantic-token path rather than a stale fallback.

**VS Code:**
1. Open Output panel (Ctrl+Shift+U / Cmd+Shift+U)
2. Select "Ecstasy Language Server" from the dropdown
3. Find the same banner

**With the compiler adapter**, every compilation also logs what it cost and what the compiler is
holding on to, which is the quickest way to tell it is really running:

```
XdkAdapter - compile: uri=file:///X.x, 106 bytes, 1 diagnostic(s), 3 symbol(s), queue=1,
             waited 160us, compiled in 64ms [modules=24, constants=207196, invalidations=10, heap=281MB]
```

`queue` is how many documents are waiting: compilations are serialised, because the compiler was
not written for two at once. `waited` growing while you type is the sign that serialising has
started to hurt.

### 4. Create Test File

Create a file named `TestModule.x` with this content:

```xtc
module TestModule {
    class Person {
        String name;
        Int age;

        String getName() {
            return name;
        }

        void setAge(Int newAge) {
            age = newAge;
        }
    }

    interface Greeter {
        void greet();
    }

    service UserService {
        Person createUser(String name) {
            return new Person();
        }
    }

    // Test: ERROR markers are detected as diagnostics
    // ERROR: This is a test error message
}
```

---

## Test Cases by Feature

> **"Both adapters" means mock and tree-sitter**, which is how this document was written when
> there were two. The compiler adapter now answers diagnostics (§7), the outline (§6), hover
> (§2), go-to-definition (§4), find-references (§5), document highlights (§8), selection ranges
> (§9) and folding (§10), plus type hierarchy. Definition queries span the active module;
> references also span configured source graphs, including unopened consumers. Workspace-symbol
> search covers completed module sessions.
>
> Compiler completion and signature help are available for the supported cursor contexts, along
> with type-definition, type/method implementation lookup, static call hierarchy, resolved-name
> semantic tokens, read/write highlights, bounded inlay hints and validated local/private-parameter
> rename, plus ordinary instance-method override rename over explicit source graphs (versioned-edit
> clients; see section I). Formatting, code actions,
> code lenses, document links and linked editing are not advertised. Definition/type-definition
> and inherited implementation bodies can also resolve into explicitly host-indexed dependencies;
> ordinary editor launch does not configure those artifacts. Compiler mode stays Java-only.
>
> Use the [XdkAdapter playbook](#xdkadapter-playbook) for a complete compiler run, including fixtures
> that compile and precise expectations for compiler-only features. §7a adds diagnostic stress checks.

### Compiler module sessions and hierarchy

With the compiler backend, create `Project.x` containing `module Project { class Base {} }` and
`Project/Child.x` containing `class Child extends Base {}`.

| Check | Action | Expected |
|---|---|---|
| Member diagnostics | Open Child.x; change Base to Missing in its extends clause | A compiler diagnostic points into Child.x, with no generic internal error on Project.x. |
| Sibling invalidation | Restore Child.x; rename Base in the open Project.x buffer | Child.x is reanalysed and its diagnostic updates even without an edit there. |
| Unsaved member | Open a new `Project/pkg/Added.x` buffer containing `class Added extends Base {}` | It joins the module without being saved; no temporary source files appear on disk. |
| Cross-file navigation | Navigate from Base in Child.x; find references on the Base declaration | Definition points into Project.x; references include Child.x. Highlights remain document-local. |
| Hierarchy | Prepare hierarchy on Base and expand its subtypes; inspect Child's supertype | Child and Base point to their own files. A generic parent retains its type arguments. |
| Close overlay | Introduce an error in Child.x, then discard and close its buffer while Project.x stays open | The disk version replaces the overlay; obsolete diagnostics clear. |
| Membership | Create an invalid member on disk, then delete it | File notifications refresh the module and clear the deleted file's diagnostics. |
| Broken syntax | Remove a member's closing brace, then restore it | Current outlines/folding remain available, including sibling files. Semantic navigation clears until correction. |
| Incomplete statement | Type `console.` inside a method and remove the closing braces | The method/module outline and enclosing selection ranges survive; compiler diagnostics remain, and no semantic definition is invented for the broken expression. |

These checks exercise module source files, not workspace dependency builds or conditional-mixin
hierarchy. Dependency source navigation needs an explicit host-supplied artifact/source index;
the [host API checks](#dependency-host-api-checks) below cover that boundary. The XdkAdapter playbook
also covers method-implementation lookup.

### 1. Syntax Highlighting (TextMate)

**Provider:** TextMate grammar (NOT tree-sitter or LSP)
**Status:** ✅ Done
**Works with:** Both adapters (independent of LSP)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 1.1 | Keywords | Look at `module`, `class`, `interface`, `service`, `return` | Different color from identifiers |
| 1.2 | Types | Look at `String`, `Int`, `Person` | Type color |
| 1.3 | Strings | Add `"hello"` literal | String color |
| 1.4 | Comments | Add `// comment` and `/* block */` | Comment color |
| 1.5 | Numbers | Add `42`, `3.14` | Number color |
| 1.6 | Editor color scheme sanity | Open a `.x` file in IntelliJ | Editor background matches the active theme (not a solid white fallback) |
| 1.7 | TextMate + semantic token layering | Open a `.x` file with types, methods, and annotations | Base TextMate colors remain sane; semantic tokens refine symbols instead of washing out the theme |

**Note:** Tree-sitter supplies syntax-based semantic tokens. The opt-in compiler adapter additionally
classifies resolved usage sites as properties, locals or parameters and supplies semantic modifiers;
see X41 in the compiler playbook. TextMate remains the lexical coloring layer.

---

### 2. Hover Information

**LSP Method:** `textDocument/hover`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Hover mouse over a symbol (or Ctrl+Q for Quick Documentation)
- *VS Code:* Hover mouse over a symbol

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 2.1 | Class hover | Hover over `Person` in declaration | Shows `class Person` |
| 2.2 | Method hover | Hover over `getName` | Shows `method getName` |
| 2.3 | Property hover | Hover over `name` property | Shows `property name` |
| 2.4 | Interface hover | Hover over `Greeter` | Shows `interface Greeter` |
| 2.5 | Service hover | Hover over `UserService` | Shows `service UserService` |

---

### 3. Code Completion

**LSP Method:** `textDocument/completion`
**Status:** ⚠️ Partial (Mock not context-aware; tree-sitter scope-aware in BODY context)
**Works with:** All three (the compiler adds the type, which no grammar can)

**How to trigger:**
- *IntelliJ:* Ctrl+Space (Basic Completion), or type and wait for auto-popup
- *VS Code:* Ctrl+Space, or type and wait for auto-popup

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 3.1 | Keyword completion | Type `cla` + Ctrl+Space | ✅ | ✅ |
| 3.2 | Type completion | Type `Str` + Ctrl+Space | ✅ | ✅ |
| 3.3 | Document symbols | Type `Per` + Ctrl+Space | ✅ | ✅ |
| 3.4 | Built-in types | Type `Int` + Ctrl+Space | ✅ | ✅ |
| 3.5 | After dot (member) | Type `person.` + Ctrl+Space | ❌ | ✅ |
| 3.6 | Context filtering | Inside method vs class level | ❌ | ⚠️ |
| 3.7 | Module-level `@Inject` in body | Module: `@Inject Console console;`. Inside `void run() { co<Ctrl+Space> }` | ❌ | ✅ |
| 3.8 | Function-local variable | Inside method: `String greeting = "hi"; gr<Ctrl+Space>` | ❌ | ✅ |
| 3.9 | Method parameter | Inside `Int square(Int amount) { am<Ctrl+Space> }` | ❌ | ✅ |
| 3.10 | Class member from method body | Inside class with `Int total;` and a method, type `to<Ctrl+Space>` in the method body | ❌ | ✅ |
| 3.11 | Scope ordering | Type `<Ctrl+Space>` inside a method that mixes local var, parameters, class members, module-level decls | ❌ | ✅ locals/params first, then class members, module-level decls, built-in types, keywords |

---

### 4. Go to Definition

**LSP Method:** `textDocument/definition`
**Status:** ✅ Done (scope-aware same-file + cross-file via workspace index)
**Works with:** All three (compiler: resolved identities across the current module, including closed member files)

**How to trigger:**
- *IntelliJ:* Ctrl+Click on a symbol, or Ctrl+B, or F12
- *VS Code:* Ctrl+Click on a symbol, or F12

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 4.1 | Class reference | Ctrl+Click `Person` in return type | Jumps to `class Person` |
| 4.2 | Method reference | Ctrl+Click `getName` call | Jumps to method |
| 4.3 | Property reference | Ctrl+Click `name` in `return name;` | Jumps to property |
| 4.4 | Cross-file type | Ctrl+Click on a type defined in another file | Jumps to definition in other file |
| 4.5 | Method parameter | Ctrl+Click on a parameter usage inside its method body | Jumps to the parameter declaration in the signature, NOT to any same-named workspace symbol |
| 4.6 | Function-local variable | Method declaring `String s = "hello";` and using `s` later. Ctrl+Click on `s` | Jumps to the local declaration, NOT to any same-named class field or workspace symbol |
| 4.7 | Local shadowing class member | Class with `Boolean whitespace;`. Method declaring `function Boolean(Char) whitespace = ...;` and using `whitespace(test)`. Ctrl+Click on the `whitespace` call | Jumps to the local function-typed variable, NOT to the class field |
| 4.8 | Inner block shadows outer | `void run() { Int x = 1; if (cond) { Int x = 2; x.toString(); } }`. Ctrl+Click on the inner `x` | Jumps to the inner-block declaration, NOT the outer one |
| 4.9 | Forward reference not resolved | Method body where a usage of `name` precedes a local declaration of `name`. Ctrl+Click on the usage | Resolves to module-level / outer-scope / workspace `name`, NOT the forward-declared local |
| 4.10 | Doc-commented target | Ctrl+Click on a class/method/property preceded by a `/** ... */` doc comment | Cursor lands on the declaration line (e.g. `class Foo {`), NOT on the `/**` opener |

**Tree-sitter notes:**
- Resolution order is: enclosing-scope locals/parameters → class/module members → same-file top-levels → cross-file workspace index.
- Cross-file definition uses workspace index fallback only when scope-aware resolution finds nothing.
- Import-path-based resolution is not yet implemented.

---

### 5. Find References

**LSP Method:** `textDocument/references`
**Status:** ⚠️ Partial
**Works with:** Tree-sitter, and the compiler (current module, by resolved identity). Mock limited

**How to trigger:**
- *IntelliJ:* Alt+F7 (Find Usages), or right-click → Find Usages, or Shift+F12
- *VS Code:* Shift+F12, or right-click → Find All References

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 5.1 | Find class usages | Right-click `Person` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.2 | Find method usages | Right-click `getName` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.3 | Find property usages | Right-click `name` → Find Usages / Find All References | ⚠️ decl only | ✅ |

**Mock limitation:** Returns only the declaration, not actual usages.

---

### 6. Document Structure / Outline

**LSP Method:** `textDocument/documentSymbol`
**Status:** ✅ Done
**Works with:** All three adapters

**How to trigger:**
- *IntelliJ:* Alt+7 (Structure tool window), Ctrl+F12 (File Structure popup)
- *VS Code:* Ctrl+Shift+O (Go to Symbol in File), or Outline panel in sidebar

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 6.1 | Structure view | IntelliJ: Alt+7; VS Code: Outline panel | Hierarchical outline |
| 6.2 | File structure popup | IntelliJ: Ctrl+F12; VS Code: Ctrl+Shift+O | Popup with all symbols |
| 6.3 | Breadcrumbs | Look at editor top (VS Code) or bottom (IntelliJ) | `TestModule > Person > getName` |
| 6.4 | Outline on the compiler adapter | Same as 6.1, built with `-Plsp.adapter=compiler` | Same hierarchy. It comes from the parsed AST, not the compiled structures, so it appears for a file that does not compile |
| 6.5 | Outline of a file with errors | Give a non-void method a bare `return;`, then Alt+7 / Outline | `COMPILER-41: Return is supposed to be non-void.`, and the outline still lists every declaration. A structure view that empties out on a typo is the bug this guards |

---

### 7. Diagnostics / Error Detection

**LSP Method:** `textDocument/publishDiagnostics`
**Status:** ✅ Done (compiler adapter) / ⚠️ Partial (others)
**Works with:** Different behavior per adapter

**How to trigger:** Diagnostics appear automatically as you type (push-based).
- *IntelliJ:* Red/yellow squiggly underlines; Alt+Enter for quick fixes; F2 to jump to next error
- *VS Code:* Red/yellow squiggly underlines; Ctrl+Shift+M (Problems panel); F8 to jump to next error

| # | Test | Steps | Mock | Tree-sitter | Compiler |
|---|------|-------|:----:|:-----------:|:--------:|
| 7.1 | Syntax error (missing brace) | Delete a `}` | ❌ | ✅ | ✅ with the compiler's own code |
| 7.2 | Unmatched braces | Add `{` without `}` | ⚠️ | ✅ | ✅ |
| 7.3 | ERROR comment marker | Add `// ERROR: message` | ✅ | N/A | N/A |
| 7.4 | WARN comment marker | Add `// WARN: message` | ✅ | N/A | N/A |
| 7.5 | Semantic error (undefined var) | `Int y = x + 1;` with no `x` in scope | ❌ | ❌ | ✅ `COMPILER-38: Name "x" is unresolvable.` |
| 7.6 | Module-level property getter parses cleanly | At module scope (outside any class) write `Int val2.get() = 43;`. Same form inside a class body should also parse | N/A | ✅ no diagnostic | ✅ no diagnostic |
| 7.7 | Package-level property getter parses cleanly | Inside `package util { Int answer.get() = 42; }` | N/A | ✅ no diagnostic | ✅ no diagnostic |
| 7.8 | Type error | `String s = 1;` | ❌ | ❌ | ✅ `COMPILER-43: Type mismatch: "String" expected, "IntLiteral" found.` |
| 7.9 | Wrong argument count | Call a one-argument method with two | ❌ | ❌ | ✅ `COMPILER-56: Could not find a matching method or function "f" ...` - the compiler reports no *matching* method rather than a count |
| 7.10 | Codes are the compiler's | Any of 7.1-7.9 | - | - | The code shown is the one `xcc` prints for the same file: `PARSER-*`, `COMPILER-*`, `VERIFY-*` |

**Notes:**
- Mock: Detects `// ERROR:` and `// WARN:` comment markers (testing convenience)
- Tree-sitter: Real syntax error detection via parsing (doesn't use comment markers by design)
- Comment markers: N/A for tree-sitter and the compiler, because both report real problems
- Compiler: the same diagnostics `xcc` would print, at the same spans. A row it disagrees with
  `xcc` about is a bug worth reporting either way - the two are meant to be the same compiler

---

### 7a. Compiler Adapter Specifics

**Status:** ✅ Done
**Works with:** Compiler adapter only (`-Plsp.adapter=compiler`)

These are the behaviours that only exist because the adapter runs the real compiler. Nothing here
is observable under mock or tree-sitter.

**Prerequisite:** a server built with `-Plsp.adapter=compiler`; its module resources must be present.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 7a.1 | No external XDK | Unset `XDK_HOME`, restart the server, open a complete module | Real compiler diagnostics; correcting the source clears them |
| 7a.2 | Invalid external XDK | Set `XDK_HOME` to a nonexistent directory, restart and edit the file | The bundled libraries still supply compiler analysis |
| 7a.3 | Cold start | Watch the log on the first `.x` file opened in a session | `first compilation in this server took ... (cold)`, around a second. Slow once is expected; slow every time is not |
| 7a.4 | Steady state | Edit the same file ten times, watching `compiled in` | Settles to tens of milliseconds. A number that keeps climbing means something is accumulating - compare `footprint` across the run |
| 7a.5 | Queue depth | Type quickly across two or three open files | `queue=` rises above 1 and falls back. `waited` staying high is the signal that one-at-a-time has become the bottleneck |
| 7a.6 | Superseded edit | Type continuously for several seconds without pausing | Log shows `superseded before it started, skipped` or `superseded after ..., abandoned`. No diagnostics are published for text that has already been replaced - a squiggle under an identifier you have finished typing is the failure this prevents |
| 7a.7 | Memory over a session | Leave the server up, edit for a while, watch `heap=` in the `footprint` on each line | Normal allocation/GC produces a sawtooth. Record sustained growth across repeated collections or after closing files; a single increasing sequence is not proof of retained compiler state. |
| 7a.8 | A diagnostic no other adapter can find | See the duplicate-annotation file below | One `WARNING VERIFY-75`, the annotation is ignored |
| 7a.9 | A file that has gone badly wrong | Paste a hundred lines of non-Ecstasy text into a `.x` file | Diagnostics stop at a hundred serious errors rather than filling the panel with consequences of the first one |
| 7a.10 | References follow meaning, not spelling | Two classes each with a property `x`; Shift+F12 on one | Only that class's `x`. A text search cannot do this, and neither can a grammar |
| 7a.11 | Definition of a method call | F12 on `p.sum()` | Jumps to `sum`'s declaration. The name in a call resolves to nothing by itself - which method it is depends on the target and the arguments - so this is the compiler's answer, not a name match |
| 7a.12 | Definition of something from the core library | F12 on `Int` or `Console` | Nothing happens. It resolves perfectly well and this document has nowhere to point at; jumping to another mention of `Int` in the same file would be worse than doing nothing |
| 7a.13 | Hover shows a type | Hover over a variable in an expression | The declaration, and the type the compiler decided. On a document that does not compile the type may be absent - an expression only has one once it has been validated |
| 7a.14 | Compiler completion and call hints | Run X6–X19 in the [XdkAdapter playbook](#xdkadapter-playbook) | Accessible members, visible scope and applicable call candidates come from compiler validation; completing the expression clears normal diagnostics. |

**7a.8 - the duplicate annotation.** This is the case worth keeping, because it is invisible
everywhere else. No grammar can find it: it needs the compiler to lay `Derived` over `Base` and
notice the annotation was already there.

```xtc
module DupAnno {
    class Base {
        @Atomic Int x = 1;
    }
    class Derived extends Base {
        @Atomic @Override Int x = 2;     // VERIFY-75: duplicates the base property's annotation
    }
}
```

Expected: `WARNING VERIFY-75`, naming the annotation, the property and the derived class, and
saying the annotation on the derived property is ignored.

> Exactly one warning, not two. The compiler reports this one twice internally - once when the
> type is laid out, once when a later stage asks for the same type again - and the adapter
> collects through an `ErrorList`, which is what filters the repeat. Two identical warnings in the
> Problems panel means that filtering has been lost.

> Compiling the same file with `xcc` on **master** prints nothing at all - the warning is
> produced and then discarded before anyone sees it. This is a real bug in master, recorded in
> [docs/errs.md](../../docs/errs.md); the adapter showing it is the fix working, not a false
> positive.

---

### 8. Document Highlight

**LSP Method:** `textDocument/documentHighlight`
**Status:** ✅ Done
**Works with:** All three (the compiler matches resolved identity within the document)

**How to trigger:**
- *IntelliJ:* Click on any identifier — other occurrences highlight automatically
- *VS Code:* Click on any identifier — other occurrences highlight automatically

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 8.1 | Class highlight | Click on `Person` | All `Person` occurrences highlighted |
| 8.2 | Property highlight | Click on `name` | All `name` occurrences highlighted |
| 8.3 | Method highlight | Click on `getName` | All `getName` occurrences highlighted |
| 8.4 | No highlight on whitespace | Click on empty space | No highlights |
| 8.5 | Write highlight | Click on `x` in `Int x = 42;` | Declaration site shows as **write** highlight (different color/style from reads) |
| 8.6 | Read highlight | Click on `x` in `return x;` | Usage site shows as **read** highlight |
| 8.7 | Assignment write | Click on `age` in `age = newAge;` | Assignment target shows as **write** highlight |

Rows 8.5–8.7 describe Tree-sitter's read/write classification. The compiler also distinguishes reads
and writes by resolved identity, but declarations receive TEXT highlights; an assignment or compound
assignment target receives WRITE. Use X41 for the compiler-specific expectations.

---

### 9. Selection Ranges (Smart Select)

**LSP Method:** `textDocument/selectionRange`
**Status:** ✅ Done
**Works with:** Tree-sitter, and the compiler (it expands out through the parsed tree)

**How to trigger:**
- *IntelliJ:* Ctrl+W (Expand) / Ctrl+Shift+W (Shrink)
- *VS Code:* Shift+Alt+Right (Expand) / Shift+Alt+Left (Shrink)

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 9.1 | Expand from identifier | Place cursor on `name`, expand | ❌ | ✅ selects `name` → `String name` → class body → class → module |
| 9.2 | Expand from method body | Place cursor inside `return name;`, expand | ❌ | ✅ selects statement → method body → method → class |
| 9.3 | Shrink back | After expanding, shrink | ❌ | ✅ reverses the chain |

---

### 10. Folding Ranges

**LSP Method:** `textDocument/foldingRange`
**Status:** ✅ Done
**Works with:** All three (the compiler folds blocks and declarations that span more than a line)

**How to trigger:**
- *IntelliJ:* Click the fold/unfold arrows in the editor gutter (left margin); Ctrl+Shift+Minus (fold all) / Ctrl+Shift+Plus (unfold all)
- *VS Code:* Click fold arrows in gutter; Ctrl+Shift+[ (fold) / Ctrl+Shift+] (unfold); Ctrl+K Ctrl+0 (fold all) / Ctrl+K Ctrl+J (unfold all)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 10.1 | Class fold | Click fold arrow next to `class Person {` | Class body collapses |
| 10.2 | Method fold | Click fold arrow next to `String getName() {` | Method body collapses |
| 10.3 | Import fold | Add 3+ import statements, fold | Import block collapses |
| 10.4 | Nested fold | Fold method inside class | Method folds independently |
| 10.5 | Fold all | Ctrl+Shift+Minus / Ctrl+K Ctrl+0 | All regions collapse |
| 10.6 | Consecutive line comments | Add 3+ consecutive `//` comments | Fold arrow appears; comments collapse as one region |
| 10.7 | Non-adjacent comments | Add `//` comments separated by code | Each group folds independently |

---

### 11. Rename Symbol

**LSP Method:** `textDocument/prepareRename` + `textDocument/rename`
**Status:** ✅ Done (same-file only)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Shift+F6 on an identifier, or right-click → Refactor → Rename
- *VS Code:* F2 on an identifier, or right-click → Rename Symbol

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 11.1 | Rename class | Place cursor on `Person`, press F2/Shift+F6, type `Employee` | All `Person` occurrences renamed |
| 11.2 | Rename method | Place cursor on `getName`, rename to `fetchName` | All occurrences updated |
| 11.3 | Rename property | Place cursor on `name`, rename to `fullName` | All occurrences updated |
| 11.4 | Prepare rename | Press F2/Shift+F6 on `Person` | Identifier range highlighted, old name shown |
| 11.5 | Cancel rename | Press Escape during rename | No changes applied |

---

### 12. Code Actions

**LSP Method:** `textDocument/codeAction`
**Status:** ✅ Done (organize imports + remove unused imports)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Alt+Enter on an import line, or lightbulb icon in gutter
- *VS Code:* Ctrl+. (Quick Fix menu), or click lightbulb icon

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 12.1 | Organize imports (unsorted) | Add unsorted imports: `import b; import a;`, trigger code action | Imports sorted alphabetically |
| 12.2 | No action (already sorted) | With sorted imports, open code actions | No "Organize Imports" offered |
| 12.3 | No action (single import) | With 1 import, open code actions | No action offered |
| 12.4 | Remove unused import | Add `import foo.Unused;` where `Unused` is never referenced, trigger code action | "Remove unused import 'Unused'" action offered |
| 12.5 | Used import not flagged | Add `import foo.Bar;` and use `Bar` in code, trigger code action | No "Remove unused import" for `Bar` |

---

### 13. Document Formatting

**LSP Method:** `textDocument/formatting` + `textDocument/rangeFormatting`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger (full document):**
- *IntelliJ:* Ctrl+Alt+L (Reformat Code)
- *VS Code:* Shift+Alt+F (Format Document)

**How to trigger (selection only):**
- *IntelliJ:* Select text, then Ctrl+Alt+L
- *VS Code:* Select text, then Ctrl+K Ctrl+F (Format Selection)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13.1 | Remove trailing whitespace | Add spaces at end of a line, format | Trailing whitespace removed |
| 13.2 | Insert final newline | Remove final newline from file, format | Final newline added |
| 13.3 | Range format | Select 2-3 lines with trailing spaces, format selection | Only selected lines cleaned |
| 13.4 | No-op on clean file | Format a file with no trailing whitespace | No changes |

---

### 13a. On-Type Formatting (Auto-Indent)

**LSP Method:** `textDocument/onTypeFormatting`
**Status:** ✅ Done
**Works with:** Tree-sitter adapter only

The LSP server uses tree-sitter AST context to auto-indent as you type. Trigger characters
are `Enter`, `}`, and `;`. This is strictly better than regex-based TextMate indentation
because it understands nesting depth, continuation lines, and string literals.

**How it works:** Automatic — indentation is adjusted immediately when you type a trigger
character. No manual action needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13a.1 | Indent after `{` in class | Type `class Foo {` then Enter | New line indented +4 from class keyword |
| 13a.2 | Indent after `{` in method | Inside a class, type `void foo() {` then Enter | New line indented +4 from method |
| 13a.3 | Indent after `{` in if | Inside a method, type `if (True) {` then Enter | New line indented +4 from if |
| 13a.4 | Outdent `}` for class | Type `}` to close a class body | `}` aligns with the `class` keyword |
| 13a.5 | Outdent `}` for method | Type `}` to close a method body | `}` aligns with the method declaration |
| 13a.6 | Outdent `}` for if block | Type `}` to close an if block | `}` aligns with the `if` keyword |
| 13a.7 | Maintain indent after statement | After `x = 1;` press Enter | New line at same indent level |
| 13a.8 | Continuation + `{` | Type `implements Closeable {` then Enter | Body indent from declaration start (+4), not from continuation (+8) |
| 13a.9 | Module body indent | Type `module myapp {` then Enter | New line indented +4 |
| 13a.10 | No indent inside string | Press Enter inside a string literal | No indentation adjustment |
| 13a.11 | Nested constructs (3+ levels) | Class > method > if > Enter after `{` | Correct cumulative indent (e.g. 12 for 3 levels) |
| 13a.12 | After `}` line | Press Enter after a `}` line | New line at same indent as `}` |
| 13a.13 | Large file performance | Open a `.x` file > 1000 lines, type normally | < 5ms per formatting request (check LSP log) |
| 13a.14 | Doc comment continuation | Type `/**` then Enter | New line gets ` * ` prefix aligned with `/**` |
| 13a.15 | Doc comment mid-line | Press Enter on a ` * existing text` line inside `/** */` | New line gets ` * ` prefix |
| 13a.16 | Indented doc comment | Inside a class (indent 4), type `/**` then Enter | New line gets `     * ` (4 spaces + ` * `) |
| 13a.17 | Block comment continuation | Type `/*` then Enter | New line gets ` * ` prefix |
| 13a.18 | No continuation after `*/` | Press Enter after a `*/` line | Normal indentation (no ` * ` prefix) |
| 13a.19 | IntelliJ auto-close brace skeleton | In IntelliJ, type `void bepa() {` and press Enter | Creates an indented blank body line and leaves the auto-inserted `}` aligned with `void`, not under the method name |
| 13a.20 | Repeated Enter inside fresh method | After 13a.19, press Enter again on the blank body line | Next line stays at method-body indent instead of drifting to 8/12 spaces |

**High-value log checks while running 13a tests:**
- `textDocument/onTypeFormatting: ... ch='\n'`
- `onTypeFormatting[enter]: ... reason=... desiredIndent=... currentIndent=...`
- `textDocument/onTypeFormatting: 1 edits [...]`
- For IntelliJ auto-close skeleton cases: `onTypeFormatting[enter]: auto-close skeleton ... bodyIndent=... closingIndent=...`

---

### 13b. Code Style Settings (IntelliJ)

**Provider:** IntelliJ plugin (`XtcLanguageCodeStyleSettingsProvider`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `editor.tabSize` / `editor.insertSpaces`)

Code Style settings for XTC appear under Settings > Editor > Code Style > Ecstasy.

**How to access:**
- *IntelliJ:* Settings/Preferences > Editor > Code Style > Ecstasy

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13b.1 | Settings page exists | Open Settings > Editor > Code Style | "Ecstasy" appears in the language list |
| 13b.2 | Default indent size | Open Code Style > Ecstasy > Tabs and Indents | Indent: 4, Continuation indent: 8, Tab size: 4, Use tab character: unchecked |
| 13b.3 | Code preview | Look at the preview pane | XTC code sample with classes, methods, switch/case |
| 13b.4 | Change indent size | Set indent to 2, look at preview | Preview re-indents with 2-space indent |
| 13b.5 | Change continuation indent | Set continuation indent to 4 | Preview adjusts `implements` line indent |
| 13b.6 | Tab character toggle | Check "Use tab character" | Preview switches from spaces to tabs |
| 13b.7 | Right margin | Check the right margin value | Should default to 120 |
| 13b.8 | Settings persist | Change indent to 3, close and reopen Settings | Indent still shows 3 |
| 13b.9 | Reset to defaults | Click "Reset" or "Set from..." > "Ecstasy" | Values revert to 4/8/4/false |

---

### 13c. Code Style → LSP Server Round-Trip (IntelliJ)

**Provider:** `XtcLanguageClient` (workspace/configuration) + `XtcLanguageServer`
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code falls back to LSP `FormattingOptions`)

IntelliJ Code Style settings are forwarded to the LSP server via `workspace/configuration`
at startup. Changes are pushed via `workspace/didChangeConfiguration`. The server uses
these settings for on-type formatting when no project-level `xtc-format.toml` is present.

**How to verify the config flow:**
1. Open an IntelliJ instance running the XTC plugin
2. Check the LSP server log for `workspace/configuration: editor formatting config:` — this
   confirms the server received the settings

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13c.1 | Default config flows to server | Open a `.x` file, check LSP log | Log shows `editor formatting config: XtcFormattingConfig(indentSize=4, ...)` |
| 13c.2 | Custom indent flows to server | Set Code Style indent to 2, restart LSP server | Log shows `indentSize=2` |
| 13c.3 | 2-space indent affects formatting | Set Code Style indent to 2, type `module Foo {` then Enter | New line indented by 2 spaces (not 4) |
| 13c.4 | Nested indent with custom config | Set indent to 3, type class inside module, then method, press Enter after `{` | Indent at 9 (3 * 3 levels) |
| 13c.5 | No TextMate interference | Open a `.x` file with custom indent | Syntax highlighting works normally (no white background, correct colors) |
| 13c.6 | VS Code fallback | Open same project in VS Code with `editor.tabSize: 2` | On-type formatting uses 2-space indent from LSP FormattingOptions |
| 13c.7 | Auto-close brace honors custom indent | Set indent to 2, type `void foo() {` then Enter in IntelliJ | Blank body line indents to 4 spaces and the auto-inserted `}` aligns to 2 spaces |

**How to restart the LSP server** (to pick up changed Code Style settings):
- *IntelliJ:* Open the LSP4IJ Language Servers panel → right-click "Ecstasy Language Server" → Restart

**Note:** The `workspace/didChangeConfiguration` notification is sent when IntelliJ detects
a configuration change, which should propagate settings without a manual server restart.
If settings don't update immediately, restart the server as a workaround.

---

### 14. Signature Help

**LSP Method:** `textDocument/signatureHelp`
**Status:** ✅ Done (tree-sitter only, same-file)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* Type `(` after a method name, or press Ctrl+P inside argument list
- *VS Code:* Type `(` after a method name, or press Ctrl+Shift+Space inside argument list

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 14.1 | Show params on `(` | Type `createUser(` | ❌ | ✅ shows `String name` |
| 14.2 | Active param on `,` | Type `method(arg1,` | ❌ | ✅ highlights second param |
| 14.3 | No help outside call | Place cursor on a variable | ❌ | ✅ returns null (no popup) |

---

### 15. Document Links

**LSP Method:** `textDocument/documentLink`
**Status:** ✅ Done (URLs in comments and string literals)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* URLs appear as clickable links inside comments and string literals (Ctrl+Click)
- *VS Code:* URLs appear as clickable underlined text inside comments and string literals (Ctrl+Click)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15.1 | URL in line comment | Add `// see https://example.com for more` | URL is underlined; Ctrl+Click opens browser |
| 15.2 | URL in block comment | Add `/* docs at https://docs.xtclang.org */` | URL is underlined and clickable |
| 15.3 | URL in doc comment | Add `/** Reference: https://example.com */` above a class | URL is underlined and clickable |
| 15.4 | URL in string literal | Add `String url = "https://api.example.com/v1";` | URL inside the string is clickable |
| 15.5 | URL in template string | Add `String s = $"see https://x.test/{name}";` | URL is clickable; the interpolation continues to work |
| 15.6 | Trailing punctuation trimmed | Add `// see https://example.com.` (period at end) | The link target is `https://example.com` without the trailing `.` |
| 15.7 | URL in parens | Add `// (see https://example.com)` | Match excludes the closing `)`; target is the URL only |
| 15.8 | Multiple URLs in one comment | Add `// links: https://a.test and https://b.test` | Both URLs are independently clickable |
| 15.9 | No link on `import` | Add `import ecstasy.text.String;` | Import path is **not** underlined as a document link (Ctrl+Click on the type name still navigates via go-to-definition) |

**Notes:**
- Import-path document links were intentionally removed in PR #446. Cmd/Ctrl-click navigation on imports still works via `textDocument/definition` (it knows the difference between a package component and a type component, and degrades gracefully when nothing resolves).
- The URL matcher trims sentence-final punctuation (`.,;:!?}`) but stops at whitespace, quotes, angle brackets, or `)` / `]` — so an in-prose URL like `(https://example.com)` keeps the URL clean.
- The `documentLinkProvider` capability is still advertised, so the IntelliJ plugin doesn't need to renegotiate when additional non-URL providers are added (e.g. file-path links in resource strings, doc-comment cross-references) later.

---

### 15a. Extra source roots (`xtcSourceRoots`)

**LSP Method:** `initialize` — `initializationOptions.xtcSourceRoots`
**Status:** ✅ Done (added in PR #446)
**Works with:** Any client that sends initialization options

Lets users index `.x` source roots that live outside the open workspace folders. Three input channels (in priority order, all merged then deduplicated):
1. LSP `initializationOptions.xtcSourceRoots` — a JSON array of path strings.
2. System property `xtc.sourceRoots` — path-separator-delimited (`:` on Unix, `;` on Windows).
3. Environment variable `XTC_SOURCE_ROOTS` — same delimiter rules.

Workspace folders take precedence; extra roots are merged in. Non-existent paths are dropped with a warning at LSP startup.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15a.1 | env var resolves cross-tree imports | Open a project that imports a module whose sources live in another checkout. Launch LSP with `XTC_SOURCE_ROOTS=/path/to/xtclang2/lib_json/src/main/x` set | Cmd-click on the imported type navigates into the external source tree |
| 15a.2 | system property resolves cross-tree imports | Same as 15a.1 but pass `-Dxtc.sourceRoots=/path/to/lib_json/src/main/x` to the LSP JVM (via plugin advanced settings or VS Code launch config) | Same — imported type resolves |
| 15a.3 | initializationOptions resolves cross-tree imports | Configure the IntelliJ plugin (or VS Code extension) to send `initializationOptions: { xtcSourceRoots: ["/path/to/lib_json/src/main/x"] }` | Same — imported type resolves |
| 15a.4 | nonexistent path drops + warns | Set `XTC_SOURCE_ROOTS=/this/does/not/exist:/valid/path/lib_x/src/main/x` | LSP startup log warns about the missing path; the valid path still indexes |
| 15a.5 | path-separator handling | On macOS/Linux: `XTC_SOURCE_ROOTS=/a/path:/another/path`. On Windows: `XTC_SOURCE_ROOTS=C:\a;C:\b` | Both paths are added to the indexer |
| 15a.6 | dedup with workspace folders | Set the env var to a folder that's already in the workspace | Folder appears once in the index, not twice |

**Notes:**
- Without this feature, an `import json.xtclang.org;` from a project that doesn't have the `lib_json` source files in its workspace would fail to resolve via Cmd-click.
- This does **not** index `.xtc` binaries. A module that exists only in compiled form is still invisible to navigation. Resolving that would require either an XTC binary reader plus a virtual decompiled view, or shipping `.x` sources alongside the install distribution. Out of scope.
- The IntelliJ plugin doesn't yet expose `xtcSourceRoots` as a settings panel; for now the env var or system property is the practical path. Plugin-side work is a follow-up.

---

## Adapter Comparison Summary

> See [plan-ide-integration.md](plans/plan-ide-integration.md#adapter-capability-matrix) for the canonical adapter comparison matrix.

---

## Troubleshooting

### IntelliJ Theme / Sandbox Looks Wrong

If IntelliJ suddenly shows `.x` files with a white fallback background, washed-out colors,
or obviously stale plugin behavior:

```bash
./gradlew --stop
rm -rf lang/.intellijPlatform/sandbox
rm -rf lang/.intellijPlatform/localPlatformArtifacts
```

Then rerun `:lang:intellij-plugin:runIde`.

This should not be required for normal development, but it is a useful reset when plugin
auto-reload or stale sandbox state has clearly contaminated the test run.

### Tree-sitter Not Loading

If you see `"fallback - tree-sitter native lib missing"` in logs:

```bash
# 1. Verify native library exists
ls lang/tree-sitter/src/main/resources/native/darwin-arm64/  # macOS ARM
ls lang/tree-sitter/src/main/resources/native/linux-x64/     # Linux

# 2. Rebuild if missing
./gradlew :lang:tree-sitter:buildAllNativeLibraries
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources

# 3. Verify not stale
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate
```

### LSP Not Connecting

1. Check LSP4IJ plugin is installed in IntelliJ
2. Check `.x` files are associated with the Ecstasy language
3. Look for errors in: Help → Show Log in Finder/Explorer
4. Try: File → Invalidate Caches / Restart

### No Syntax Highlighting

**IntelliJ:**
```bash
# Verify TextMate bundle is present
ls lang/intellij-plugin/build/idea-sandbox/*/plugins/intellij-plugin/lib/textmate/
# Should contain: xtc.tmLanguage.json, package.json, language-configuration.json
```

**VS Code:**
1. Check the extension is installed: Extensions panel → search "XTC"
2. Verify `.x` files are associated: look for "XTC" in the status bar language indicator
3. If missing: `code --install-extension lang/vscode-extension/xtc-language-*.vsix`

### VS Code LSP Not Starting

1. Open Output panel → select "Ecstasy Language Server"
2. If no output channel exists, the extension failed to activate
3. Check `JAVA_HOME` or `XTC_JAVA_HOME` points to Java 25+
4. Verify the fat JAR exists: `ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar`
5. Try Developer Tools: Help → Toggle Developer Tools → Console tab

---

---

## Out-of-Process LSP Server Tests

The LSP server runs as a separate Java process (requires Java 25+). These tests verify
the process management and health monitoring.

### Prerequisites

- Java 25+ installed and available via `JAVA_HOME` or on PATH
- XTC project with `.x` files

### Test: Server Startup

```bash
./gradlew :lang:intellij-plugin:runIde
```

**Expected in console:**
```
[XTC-LSP] Ecstasy Language Server v0.4.4-SNAPSHOT
[XTC-LSP] Backend: Tree-sitter
[XTC-LSP] TreeSitterAdapter ready: native library loaded and verified
[XTC-LSP] XtcParser health check PASSED: parsed test module successfully
```

**Expected in IDE:**
- Notification: "Ecstasy Language Server Started - Out-of-process server (v..., adapter=treesitter)"

### Test: Health Check

1. Open an `.x` file in the IDE
2. Look for console output:
   - `Native library: extracted libtree-sitter-xtc.dylib to ...`
   - `Native library: successfully loaded XTC tree-sitter grammar (FFM API)`
   - `XtcParser health check PASSED`

### Test: Crash Recovery

1. Find the LSP server process: `ps aux | grep xtc-lsp-server`
2. Kill it: `kill -9 <pid>`
3. Verify notification appears: "Ecstasy Language Server Crashed"
4. Click "Restart Server"
5. Verify server restarts (new notification)

### Test: Version Display

1. After LSP starts, check notification shows correct version
2. Version should NOT be "?" - should show actual version like "v0.4.4-SNAPSHOT"

### Test: Native Library Not Found

1. Temporarily rename/remove native libraries from JAR
2. Start IDE
3. Verify error notification about native library
4. Verify fallback to mock adapter (or fail-fast error)

### Test: Java Version Too Low

> **NOTE:** With IntelliJ 2026.1+ (JBR 25), this test is no longer relevant since the
> IDE always ships with a Java 25+ runtime. The LSP server uses IntelliJ's JBR directly
> via `JavaProcessCommandBuilder`.

---

### 16. Comment Toggling (IntelliJ)

**Provider:** IntelliJ plugin (`XtcCommenter`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `language-configuration.json` for this)

This is a client-side editing feature — the LSP server handles comment *formatting*
(alignment, continuation on Enter), but toggling comment delimiters is purely an IDE action.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 16.1 | Line comment | Place cursor on a line, press Ctrl+/ | `// ` inserted at start of line |
| 16.2 | Uncomment | On a `//`-commented line, press Ctrl+/ | `// ` removed |
| 16.3 | Multi-line comment | Select 3 lines, press Ctrl+/ | All 3 lines get `// ` prefix |
| 16.4 | Multi-line uncomment | Select 3 commented lines, press Ctrl+/ | `// ` removed from all 3 |
| 16.5 | Block comment | Select text, press Ctrl+Shift+/ | Selection wrapped in `/* */` |
| 16.6 | Block uncomment | With cursor inside `/* */`, press Ctrl+Shift+/ | `/* */` removed |

---

### 17. Live Templates (IntelliJ)

**Provider:** IntelliJ plugin (`liveTemplates/XTC.xml`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `snippets/xtc.json` separately)

Live templates are code snippets triggered by abbreviation + Tab. Press Ctrl+J to
see all available templates. The same snippets are available in VS Code (type the
prefix and select from the completion popup).

**Available templates:**

| Prefix | Expansion | Category |
|--------|-----------|----------|
| `mod` | `module name { }` | Declaration |
| `cls` | `class MyClass { }` | Declaration |
| `iface` | `interface MyInterface { }` | Declaration |
| `svc` | `service MyService { }` | Declaration |
| `mix` | `mixin MyMixin into Base { }` | Declaration |
| `enu` | `enum MyEnum { Value1, Value2 }` | Declaration |
| `con` | `const MyConst(params);` | Declaration |
| `pkg` | `package json import json.xtclang.org;` | Declaration |
| `meth` | `void myMethod() { }` | Method |
| `run` | `void run() { @Inject Console console; }` | Method |
| `runa` | `void run(String[] args=[]) { ... }` | Method |
| `construct` | `construct(params) { }` | Method |
| `prop` | `String name;` | Property |
| `roprop` | `@RO Boolean empty.get() = size == 0;` | Property |
| `lazy` | `private @Lazy String value.calc() { }` | Property |
| `if` | `if (condition) { }` | Control flow |
| `ife` | `if (condition) { } else { }` | Control flow |
| `ifv` | `if (Value value := get(key)) { }` | Control flow |
| `fori` | `for (Int i : 0 ..< count) { }` | Control flow |
| `forr` | `for (Int x : 1..100) { }` | Control flow |
| `fore` | `for (Element item : collection) { }` | Control flow |
| `while` | `while (condition) { }` | Control flow |
| `switch` | `switch (value) { case 0: }` | Control flow |
| `try` | `try { } catch (Exception e) { }` | Control flow |
| `using` | `using (resource) { }` | Control flow |
| `assert` | `assert condition;` | Control flow |
| `assertm` | `assert condition as "message";` | Control flow |
| `sout` | `@Inject Console console; console.print();` | Common |
| `print` | `console.print();` | Common |
| `inject` | `@Inject Console console;` | Common |
| `lambda` | `(params) -> expr` | Common |
| `cond` | `conditional Value find() { }` | Common |
| `doc` | `/** description */` | Comment |
| `todo` | `// TODO` | Comment |
| `webapp` | Full @WebApp module with web service | Skeleton |
| `websvc` | `@WebService("/") service { @Get ... }` | Web |
| `hello` | Complete Hello World module | Skeleton |

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 17.1 | Module template | Type `mod` + Tab | Expands to `module name { }` with cursor on name |
| 17.2 | Class template | Type `cls` + Tab | Expands to `class MyClass { }` |
| 17.3 | For loop (range) | Type `fori` + Tab | Expands to `for (Int i : 0 ..< count) { }` |
| 17.4 | For loop (inclusive) | Type `forr` + Tab | Expands to `for (Int x : 1..100) { }` |
| 17.5 | For-each | Type `fore` + Tab | Expands to `for (Element item : collection) { }` |
| 17.6 | Console print | Type `sout` + Tab | Expands to `@Inject Console console;` + `console.print();` |
| 17.7 | If conditional assign | Type `ifv` + Tab | Expands to `if (Value value := get(key)) { }` |
| 17.8 | Hello World | Type `hello` + Tab | Expands to complete Hello World module |
| 17.9 | WebApp skeleton | Type `webapp` + Tab | Expands to full @WebApp module with web service |
| 17.10 | Template list | Press Ctrl+J in editor | Shows all XTC templates with descriptions |
| 17.11 | Tab navigation | Type `meth` + Tab, fill return type, Tab, fill name | Cursor moves through variables in order |
| 17.12 | Inject template | Type `inject` + Tab | Expands to `@Inject Type name;` |
| 17.13 | Mixin template | Type `mix` + Tab | Expands to `mixin Name into Base { }` |
| 17.14 | Conditional method | Type `cond` + Tab | Expands to `conditional Value find() { }` |

---

### 18. Code Lens (Run Actions)

**LSP Method:** `textDocument/codeLens`
**Status:** ✅ Done
**Works with:** Tree-sitter adapter (both IntelliJ and VS Code)

Code lenses appear as inline annotations above module declarations. LSP4IJ (IntelliJ)
and VS Code render them automatically from the LSP server response — no plugin code needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 18.1 | Run lens on module | Open a `.x` file with `module myapp { }` | "▶ Run myapp" appears above the module declaration |
| 18.2 | No lens on class | Open a file with only `class Foo { }` (no module) | No code lens appears |
| 18.3 | Lens position | Check the lens annotation position | Aligned with the `module` keyword line |
| 18.4 | Multiple files | Open two `.x` files with different modules | Each shows its own module's Run lens |

---

### 19. Semantic Tokens

**LSP Method:** `textDocument/semanticTokens/full`
**Status:** ✅ Done (enabled by default)
**Works with:** Tree-sitter adapter (both IntelliJ and VS Code)

Semantic tokens layer on top of TextMate highlighting, providing AST-aware coloring
that TextMate's regex patterns cannot achieve. The server logs `semantic tokens ENABLED`
at startup to confirm they're active.

**How to verify:**
- *IntelliJ:* Open a `.x` file — types, methods, properties, and annotations should
  have distinct colors. Check LSP server log for `semantic tokens ENABLED`.
- *VS Code:* Same — semantic tokens are automatically layered on top of TextMate.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 19.1 | Types colored distinctly | Open file with `String name;` and `Int count;` | `String` and `Int` have type color (different from `name`/`count`) |
| 19.2 | Methods vs properties | Open file with `void foo()` and `String name;` | `foo` has method color, `name` has property color |
| 19.3 | Annotations as decorators | Add `@Override` or `@Inject` | Annotation name has decorator color |
| 19.4 | Deprecated strikethrough | Add `@Deprecated class Old {}` | `Old` shown with strikethrough |
| 19.5 | new Foo() as type | Write `new Person()` | `Person` colored as type, not method |
| 19.6 | Method call coloring | Write `getName()` | `getName` colored as method call |
| 19.7 | Static modifier | Add `static void helper()` | `helper` may show italic (static modifier) |
| 19.8 | Enum members | Write `enum Color { Red, Green, Blue }` | `Red`, `Green`, `Blue` colored as enum members |
| 19.9 | Parameter highlighting | Write `void foo(Int count)` | `count` has parameter color |
| 19.10 | Namespace coloring | `module myapp` declaration | `myapp` colored as namespace |
| 19.11 | Server log confirmation | Check LSP server log at startup | Shows `semantic tokens ENABLED (23 types, 10 modifiers)` |

---

## XdkAdapter Playbook

Run this section with the opt-in **compiler** backend in either editor. Tree-sitter remains the
shipped default. These checks cover the current compiler feature surface, including semantic
answers that a syntax parser cannot supply. They do not require running the test program.

### Automated VS Code run

Run the compiler playbook from the repository root:

```bash
./gradlew :lang:vscode-extension:testCompilerPlaybook \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

This builds the extension and its bundled compiler, runs the server and packaged-JAR regression
suites, then launches a real VS Code extension host. It reads the fixtures below directly, creates
a separate workspace/profile, and runs one case for every X1–X67 row plus the configuration and
compiler-diagnostic checks. Missing case IDs, a wrong backend, failures and skipped editor cases
fail the run. The editor cases run on every invocation; Gradle may reuse unchanged host-test results.
To force fresh host results as well, add `:lang:lsp-server:test --rerun` and
`:lang:lsp-server:compilerStdioTest --rerun` to the command.

Reports and failing source buffers remain under
`lang/vscode-extension/build/reports/compiler-playbook/run-*/`. `latest-run.txt` identifies the
latest directory; `results.txt` is readable and `results.json` includes the commit, dirty paths,
VS Code version, case durations/failures and host-test XML evidence. VS Code's logs are under that
run's `logs/`. The short temporary profile is removed after shutdown. Your normal editor profile
and manually prepared scratch workspace are separate.

These checks exercise editor providers, real document edits and filesystem watchers. Raw LSP
requests additionally verify hierarchy snapshot identity, cancellation, versioned rename and
named nonexistent-file overlays. After reverting/closing a tab, the runner uses the language client's
synchronization provider to send `didClose` for any retained hidden text model; reopening sends `didOpen`.
They do not drive every menu/key or inspect rendered pixels:
popup/hierarchy layout, theme appearance, physical keyboard interaction and a prolonged interactive
memory/GC soak remain manual. The report identifies those limits and maps 7a.1–7a.14 and the host-only
API checks to their automated coverage. This command covers the **XdkAdapter** playbook, not the
separate debugger, IntelliJ or Tree-sitter playbooks.

After assembling with the same compiler flag, `npm run test:playbook` from `lang/vscode-extension`
reruns just the editor suite. It reports existing host-test evidence without rebuilding/rerunning it.
See the [extension testing notes](../vscode-extension/README.md#testing) for display/`xvfb` requirements.

### Launch and confirm the backend

From the repository root, choose one command. Keep `-Plsp.adapter=compiler` on the editor launch
task too: a later build without it can restore the default backend.

```bash
# IntelliJ sandbox
./gradlew :lang:runIntellijPlugin \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler

# Or VS Code Extension Development Host
./gradlew :lang:vscode-extension:runCode \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

Open a scratch folder in that editor window. Confirm `backend: XTC Compiler` in
`~/.xtc/logs/lsp-server.log`; the health-check adapter name is `XDK`. The matching XDK libraries
are bundled, so no `XDK_HOME`, extracted distribution or separate compiler installation is needed.
Confirm the backend in the log even when colors look familiar: compiler semantic tokens cover
resolved names, while the editor's TextMate colors and snippets also remain available.

Use editor action search if a shortcut conflicts with your keymap. The VS Code actions are listed
in the [keybindings reference](#keybindings-reference); IntelliJ provides Quick Documentation,
Go to Declaration, Find Usages, Basic Completion and Parameter Info. For hierarchy, use the
client's LSP type-hierarchy view (VS Code: **Show Type Hierarchy**). If the installed client does
not expose an action, record that case as **not exercised**, not a server pass.

Create the files below exactly as named. `|` in a test row marks the cursor; **do not type it**.
Undo each temporary edit before the next row and wait for diagnostics to settle. A clean baseline
is important: parse failures can suppress normal semantic answers throughout a module.

### A. Diagnostics, navigation and structure

Save this as `Navigation.x`:

```xtc
module Navigation {
    class Holder {
        Int value = 1;
        Int read() {
            Int value = 2;
            return value + this.value;
        }
    }

    String text(Object input) {
        Object value = input;
        if (value.is(String)) {
            return value;
        }
        return value.toString();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X1 | Open the saved file; inspect Problems, Outline, folding and Expand Selection inside `return value;`. | No errors. Outline includes the module, class, property and methods; folding follows their blocks; selection grows through enclosing syntax. |
| X2 | In `read`, change `Int value = 2;` to `String value = 2;`, then undo. Separately change `input` to `missing` in the initializer in `text`. | Real compiler diagnostics identify the type mismatch and unresolved name at their source spans, with compiler codes. Each clears after correction without saving. `// ERROR: test` alone produces no diagnostic. |
| X3 | Hover `value` in `text`'s `return value;`, then in `return value.toString();`. | The first use has narrowed type `String`; the second has `Object`. Go to Definition from either reaches the same local declaration. |
| X4 | In `read`, use Go to Definition, Find References and occurrence highlighting on the bare `value` in `return value + this.value;`. Repeat on `this.value`. | The local and property lead to different declarations and separate reference sets despite identical spelling. Highlights stay within the document and distinguish reads from writes; X41 checks an assignment target. |
| X5 | Run Go to Definition on `String`. Then remove the final closing brace and inspect Outline, folding and selection; restore it. | The bundled library has no source target, so navigation returns no result. Recoverable syntax retains surrounding structure with a parser diagnostic; stale semantic targets are not reused. A badly broken header may leave structural gaps or a cursor-only selection. |

For the error-listener regression, also run **7a.8** with `DupAnno.x`: the duplicate inherited
annotation produces exactly one `VERIFY-75` warning. Rows **7a.1–7a.7** cover bundled-library startup,
timings, queued/superseded edits and memory. Timings depend on hardware and module size; record them
rather than treating the illustrative numbers as pass thresholds.

**Problems view checks (X2 and 7a.8).** In VS Code, open **View → Problems** (Cmd+Shift+M on macOS,
Ctrl+Shift+M elsewhere). Clear the text filter, enable both Errors and Warnings, and disable
“Active File Only” while comparing files.

1. Make each X2 edit. Check that the row belongs to Navigation.x, has **Error** severity, the
   compiler message/code and a line/column matching the marked source range. Click the row or use
   **Go to Next Problem** (F8); the editor must select that source location.
2. Correct it without saving. The row, red squiggle and error count must clear after analysis.
3. Open DupAnno.x from 7a.8. Check exactly one **Warning** row with code `VERIFY-75`. This warning
   currently has a file-level position (line 1, column 1): the compiler reports a structure without
   a source span. Following it opens the file, not the derived annotation; an annotation squiggle
   is not yet supported. Remove only the derived `@Atomic`: the warning row/count must clear
   without saving. Undo to restore one warning. Precise structure-diagnostic anchoring is a
   recorded follow-up, not a manual-test pass.
4. Leave the warning in DupAnno.x and introduce an X2 error in Navigation.x. Both file groups and
   severities must remain visible; correcting one must not remove the other's diagnostic.

The automated cases check diagnostic severity, code, source range, unsaved clearing and the
editor's next-problem command. They open the Problems view; inspecting its rendered rows, severity
icons, filters, counts and clicking a row remains a visual/manual check.

### B. Typed and scope-aware completion

Save this as `Editing.x`:

```xtc
module Editing {
    class Box<T> {
        T item;
        private T itemMethod() = item;
        String choose(String first, String second) = first;
        Int choose(Int first, Int second) = first;
        T pair(T first, T second) = first;
        <U> U generic(U first, U second) = first;
        void inspect(T itemLocal) {}
    }

    class Tools {
        static Int itemFunction() = 1;
        static Int itemConstant = 2;
        Int itemProperty = 3;
        Int itemMethod() = 4;
        private static Int itemHidden() = 5;
        static class itemType {}
        private static class itemPrivate {}
    }

    String getValue() = "text";
    void run(Box<String> box, String itemParameter, Object value) {}
}
```

Use **Trigger Completion / Basic Completion**, not just the popup triggered by typing. Replace
the body of `run` for X6–X10 and X12; for X11 use `Box.inspect`. Undo after each row.

These rows deliberately contain unfinished names. For example, `.si` is the partially typed
`.size` in X7/X8/X14. `COMPILER-36: Could not find name "si" within "String"` is expected at that
point: `String` is the receiver's type, and `si` is not one of its members. (`COMPILER-38` is the
related `Name "..." is unresolvable` diagnostic.) The completion probe
offers `size` while normal compilation still reports the unfinished source. The test checks that
the diagnostic clears after accepting the completion; it is not a failure merely to see it while
the runner is typing.

| # | Temporary body / action | Expected result |
|---|-------------------------|-----------------|
| X6 | `box.|;`, then `box.it|;` | Public members include `item` with substituted type `String`. The prefix filters to matching names; private `itemMethod` is absent. Accepting `item` replaces only `it`, preserving the receiver and semicolon. A bare property access is not a valid statement; change the accepted text to `String selected = box.item;` to confirm diagnostics clear. |
| X7 | `Int size = getValue().si|;` | `size` is offered from the expression receiver's `String` type. Accept it: `Int size = getValue().size;` has no errors. |
| X8 | `if (value.is(String)) { Int size = value.si|; }` | `size` is available because of flow narrowing. Accept it and check diagnostics clear. Undo the guard too when finished. |
| X9 | `Int itemLocal = 1; Int itemUnassigned; item|; Int itemLater = 2;` | Offers `itemLocal` and `itemParameter`; excludes the unreadable unassigned variable and the later declaration. |
| X10 | Put the cursor in the empty `run` body and invoke completion without typing a prefix. | Visible parameters such as `itemParameter` appear. Accepting one inserts at the cursor without deleting a brace or nearby text. Undo the insertion afterward. |
| X11 | In `Box.inspect`: `ite|;`. Then try `{ Int itemClosed = 1; } Int item = 2; ite|;`. | First: implicit `item`, private `itemMethod` and parameter `itemLocal`, with formal type `T`. Second: local `item` has type `Int`, the property is shadowed, and closed-scope `itemClosed` is absent. |
| X12 | In `run`: `Tools.item|;` | Offers static `itemFunction`, `itemConstant` and nested `itemType`; excludes instance `itemProperty`/`itemMethod` and private `itemHidden`/`itemPrivate`. |
| X13 | In `run`: `Strin|;`. Then add module import `import ecstasy.text.StringBuffer as Buffer;` and try `Buffe|;`. Replace the import with `import ecstasy.text.*;` and try `StringBuffe|;`. | Implicit `String`, explicit alias `Buffer`, then wildcard-imported `StringBuffer` appear. Restore the file between import variants. |
| X14 | Repeat X7 with `/* 😀 */` before the statement on the same line and the file saved with CRLF line endings. | The accepted completion still replaces exactly `si`. No shifted edit, damaged emoji or extra character. |

### C. Selected signatures and incomplete-call candidates

Keep `Editing.x`. Replace `run`'s body with the call in each row. Invoke **Trigger Parameter Hints /
Parameter Info** at `|`; leave the closing `)` in place, as an editor normally does.

| # | Call / action | Expected result |
|---|---------------|-----------------|
| X15 | `box.choose("x", |);`, then `box.choose(1, |);` | The first offers the `String` overload, the second the `Int` overload. The second parameter is active. These are applicable **candidates**, not final overload selections. |
| X16 | `box.pair(second="x", first=|);` | Signature types are `String` from `Box<String>`; `first` is active despite being the second written argument. Insert `"y"`: diagnostics clear and the now-valid call uses the compiler-selected signature. Go to Definition on `pair` reaches its declaration. |
| X17 | `box.generic("x", |);`, then `box.generic(|);` | The first infers `String` for the expected second parameter. Without an argument, the type remains formal `U`, not an invented `Object`. |
| X18 | `box.choose(True, |);`, `box.pair(unknown=|);`, and `box.pair(first="x", first=|);`, one at a time | No applicable signature for incompatible types, an unknown label or a duplicate label. Earlier hints must not remain visible as the answer to the new request. |
| X19 | Inside `Box.inspect`: `pair(itemLocal, |);`. Then add `static String join(String first, String second) = first;` to `Tools` and try `Tools.join("x", |);` in `run`. | Implicit-instance and static calls both show applicable candidates with the second parameter active. Fill in the missing values and confirm diagnostics clear. |
| X20 | Use `box.pair(second="x", |);`. Then complete `box.choose("x", "y");` and navigate from `choose`; repeat with `box.choose(1, 2);`. | After a named argument without a new label, no guessed parameter is highlighted. Complete calls select and navigate to the correct distinct overloads. |

The candidate label/documentation does not mean an unfinished overload has been selected. Ecstasy
allows trailing commas in valid calls: such a call can already have a selected signature.
Expected types currently inform candidate signatures; they do not yet drive completion of a
missing argument value. Normal diagnostics may remain while these deliberately incomplete calls
still provide useful hints.

### D. Module files, overlays and type hierarchy

Create this layout in the scratch folder. Save both files, then close `Child.x` while keeping
`Project.x` open.

```text
Project.x
Project/
    Child.x
```

`Project.x`:

```xtc
module Project {
    interface Named {}
    class Base<Element> implements Named {
        Element echo(Element value) = value;
        Int item = 1;
    }
}
```

`Project/Child.x`:

```xtc
class Child extends Base<String> {
    String answer() = echo("member");
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X21 | From `Base` in the root, Find References and search workspace symbols for `Child` (Go to Symbol in Workspace). Open Child and navigate from `Base` and `echo`. | References and symbol search include the closed member. Definitions land on the correct names in Project.x. Search covers analysed module sessions, not every unopened module in the workspace. |
| X22 | Show Type Hierarchy on `Child`; expand supertypes. Show it on `Base` and `Named`; expand subtypes. | Direct links are `Child → Base<String> → Named`, with correct file locations. Expanding Base's subtypes finds Child even if its tab is closed. Generic arguments remain visible. |
| X23 | Keep both files open. Change Base's name to `Renamed` in the root without saving, then undo. | Child's diagnostic changes without an edit there, then clears after undo. No generic internal error is added to the root merely because the member has an error. |
| X24 | Add `void inspect() { ite|; }` in Child and complete. Change root property to `String item = "overlay";` without saving; repeat completion in Child. | `item` changes from `Int` to `String` using the unsaved root. Undo the root change: completion returns `Int`. No temporary source files are written. |
| X25 | Create a named, unsaved `Project/pkg/Added.x` buffer with `class Added extends Base<String> {}`. | The new member and implicit package join the current module; diagnostics and Base's hierarchy reflect Added. An anonymous Untitled buffer without this URI is not this test. |
| X26 | With Project.x still open, introduce an error in Child, then discard changes and close Child. Reopen it. | The saved disk version replaces the overlay; its obsolete diagnostic clears. Navigation and hierarchy use the restored source. |
| X27 | With Project.x open, create `Project/Bad.x` on disk containing `class Bad extends Missing {}`; wait, then delete it. | Watched-file notifications refresh membership; the closed file's diagnostic appears and then clears. Run in a workspace containing these files so the client watches them. |
| X28 | Open hierarchy on Child. Temporarily replace the member file with `class Child {}`, wait for analysis, then expand the old item and reopen hierarchy. Undo. | The old item cannot return edges from the previous compilation; a fresh hierarchy no longer claims Base as parent. Restoring the file restores the edge. `Object` is an interface, so `extends Object` is not a valid replacement fixture. |

### E. Edits, cancellation and capability boundaries

| # | Action | Expected result |
|---|--------|-----------------|
| X29 | In Editing.x, alternate rapidly between X15's String and Int arguments and request hints/completion. Finish with a valid call. Repeat while editing a module sibling. | The final answer and diagnostics match the latest text. Superseded queries do not resurrect old types, offsets or errors. |
| X30 | Start a completion/hint request, dismiss it and close the document; reopen it. Repeat around a language-server restart. | No response repopulates a closed document, no hanging UI, and the reopened file gives current answers. Dismissing a popup does not guarantee the client sends cancellation; protocol cancellation is also covered by the automated stdio tests. |
| X31 | Try Format Document/Selection, quick fixes and code lenses with compiler mode active. | No compiler-backed support is advertised for them. Editor-native snippets or indentation may still work and do not count as compiler feature passes. |
| X32 | Try a cursor inside an identifier or `box.pa|ir(...)`, a constructor call, a call through a function value, or a call with arguments after the cursor. | These cursor contexts are outside current completion/candidate coverage. An empty answer is acceptable; a crash, stale answer or incorrect replacement edit is not. |

### F. Type-definition and implementation lookup

Save this as `Lookups.x`. Use **Go to Type Definition** (IntelliJ: **Go to Type Declaration**),
and **Go to Implementations / Go to Implementation(s)** from the editor action menu.

```xtc
module Lookups {
    interface Mapper<T> { T map(T value); }
    class TextMapper implements Mapper<String> {
        @Override String map(String value) = value;
        String map(Int value) = value.toString();
    }
    class Child extends TextMapper {
        @Override String map(String value) = value;
    }
    class Inherited extends TextMapper {}
    class Unrelated { String map(String value) = value; }
    TextMapper make() = new TextMapper();
    void run(Mapper<String> mapper, TextMapper|Unrelated value) {
        mapper.map("text");
        value.toString();
        if (value.is(TextMapper)) { value.toString(); }
        make();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X33 | Go to Type Definition on `mapper` in `mapper.map("text")`, then on `make` in its call and declaration. | The variable navigates to `Mapper`, not its `String` argument; the method navigates to its `TextMapper` return type. |
| X34 | Go to Type Definition on `value` before the `if`, then inside the narrowed branch. | Before narrowing, two targets: `TextMapper` and `Unrelated`. Inside, only `TextMapper`. A chooser/peek list instead of a direct jump is normal for multiple targets. |
| X35 | Go to Type Definition on `value` in `Mapper`'s method signature, then on the written `String` in TextMapper. | The formal value type leads to the declaration of `T`; the bundled `String` type has no source target. No same-spelled local substitute is returned. |
| X36 | Find Implementations on `Mapper`. | `TextMapper`, `Child` and `Inherited`, once each. `Unrelated` is excluded despite its matching method shape. These are nominal declaration-level results, not a search for every structurally compatible class. |
| X37 | Find Implementations on the interface's `map`, then on the call `mapper.map("text")`. Repeat on Child's override. | Interface/call: the String bodies in TextMapper and Child. The Int overload and Unrelated's method are excluded; Inherited adds no duplicate body. Child's override resolves to its own body. |
| X38 | Return to the two-file Project fixture in section D. Go to Type Definition on the return-type `Child` after adding `Child make() = new Child();` to the root. Find Implementations on Base's `echo`; repeat after adding two blank lines before Child without saving, then after a broken member edit and correction. | Type-definition reaches the closed/member source at its current position. The inherited method points to the actual Base body once. A parse failure clears semantic targets until correction; old offsets are never reused. |

Implementation lookup requires a successful current module compilation. Concrete classes can be
their own implementation target. Interface default bodies and mixin bodies from composed hosts
can be source targets; an unused mixin is not an implementation of its `into` constraint.
A user-written override inside an anonymous class is also a method target, although the synthetic
class is not listed as a named type implementation.
Property/accessor implementation checks are in section J. Synthetic delegation/redirect targets
remain outside the current lookup.

### G. Call hierarchy, semantic highlighting and inlay hints

Save this as `Consumers.x`. Enable semantic highlighting and inlay hints in the editor. For call
hierarchy use **Show Call Hierarchy** (VS Code) or the client's incoming/outgoing call view.
In IntelliJ, record an unavailable LSP action as not exercised. Semantic token colors depend on
the theme; VS Code's **Developer: Inspect Editor Tokens and Scopes** shows the actual token kind.

```xtc
module Consumers {
    static Int leaf(Int input, Int extra=2) = input + extra;
    static String leaf(String text) = text;
    Int run(Int seed) {
        var number = leaf(1);
        val label = leaf("text");
        number += leaf(input=2, extra=3);
        function Int() fn = () -> leaf(seed);
        return number + label.size + fn();
    }
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X39 | Show incoming calls for the Int `leaf`, then outgoing calls for `run`. | Incoming groups two sites under `run` and one under `<lambda>`. Outgoing `run` lists the Int and String overload separately; the lambda's call is not attributed to `run`. |
| X40 | Expand the lambda's outgoing calls. Inspect the dynamic `fn()` call. | The lambda leads to Int `leaf`; `fn()` does not invent a statically selected edge. Call ranges navigate to the caller's source. |
| X41 | Inspect tokens for `leaf`, `seed`, `number` and the `number +=` target. Select `number` to highlight occurrences. | Method, parameter and variable kinds reflect resolved identities. Static/declaration/modification modifiers are present where applicable. Highlights distinguish the write and subsequent read. Theme colors may coincide. |
| X42 | Inspect inline hints in `run`; compare positional and named calls. | `number: Int` and `label: String` inferred-type hints; `input:` and `text:` before positional values. The named call has no redundant hints and omitted default `extra` has none. Explicit declarations get no inferred-type hint. |
| X43 | Keep hierarchy items open, insert a blank line before `run`, then reopen hierarchy. Break the module with an unfinished declaration, correct it and close/reopen the file. | Fresh results use current ranges. Old hierarchy items do not resolve against the edited snapshot. A parse failure clears semantic answers; correction restores them. |
| X44 | In the section D two-file fixture, add `static Int target(Int n)=n;` to Project and `Int callTarget()=target(1);` to Child, then close Child and show incoming calls on `target`. | `callTarget` and its call site point to the closed Child source. An unsaved member edit moves the result; stale positions are not reused. |

Also unavailable: separate Go to Declaration, document links and linked editing. The bounded
rename checks below exercise recompilation plus binding comparison. The interactive fixtures
do not configure dependency artifacts; source navigation through the host API is tested separately
below. Workspace-wide reference/implementation searches and external or conditional-mixin hierarchy
remain open. No Tree-sitter fallback runs in compiler mode. Check the
[capability matrix](plans/plan-ide-integration.md#adapter-capability-matrix) when those limits change.

Record the commit, editor/version, confirmed backend, case ID, source/unsaved edits, expected and
actual result, and relevant server-log lines for failures. Mark unsupported client actions and
unrun rows explicitly. The packaged compiler regression suite complements the interactive pass:

```bash
./gradlew :lang:lsp-server:compilerStdioTest --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

### Dependency host API checks

Binary artifact/source-index replacement has no editor setting or JSON-RPC endpoint. Source module
configuration is available separately in the following section. A Kotlin host can
export a successful `Compilation.toDependency()` and call
`XtcLanguageServer.replaceCompilerDependencies(listOf(dependency))`. Direct adapter hosts use
`replaceDependencies(...)` and reschedule the returned scope keys themselves. Binary-only artifacts
load with `XdkDependency.fromBinary(bytes)` and intentionally supply no source targets.

Run the host regression checks to verify the integration boundary without pretending an ordinary
editor launch configures it:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkDependencyTest' \
    --tests 'org.xvm.lsp.server.XdkLanguageServerTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

Expect dependency definition/type-definition and inherited generic method-body locations to point
to the exported sources. Replacing a library invalidates direct and transitive consumers, cancels
queued compilation/cursor work and preserves unrelated successful sessions. Server tests replace
an Int-returning library with a String-returning one: consumer diagnostics appear at the unchanged
document version, then clear when the original artifact is restored. Binary-only replacement removes
source links; compiling the dependency's current source uses that source rather than its old index.
The source-project checks below separately exercise builds from unsaved sources. Neither API
establishes a persistent workspace reference index.

### Automatic source recompilation host checks

For VS Code with the compiler build, open the folder containing Library.x and Consumer.x and add
this to workspace settings (`.vscode/settings.json`):

```json
{
  "xtc.compiler.sourceModules": [
    { "name": "Library", "uri": "Library.x" },
    { "name": "Consumer", "uri": "Consumer.x", "dependencies": ["Library"] }
  ]
}
```

No restart is needed. Relative URIs require one workspace folder; use absolute file URIs for
multi-root workspaces. Other clients can supply `initializationOptions.xtcCompiler` or the
`xtc.compiler` configuration section; IntelliJ has no dedicated source-graph settings UI yet.
Automatic discovery remains separate. An embedding host can still register the graph directly:

```kotlin
server.replaceCompilerSourceModules(
    listOf(
        XdkSourceModule("Library", "file:///workspace/lsp-project/Library.x"),
        XdkSourceModule("Consumer", "file:///workspace/lsp-project/Consumer.x", setOf("Library")),
    ),
)
```

Create these two files on disk:

```xtc
// Library.x
module Library { static Int value()=1; }
```

```xtc
// Consumer.x
module Consumer { package lib import Library; Int run()=lib.value(); }
```

| # | Action | Expected result |
|---|--------|-----------------|
| X45 | Open Consumer.x without opening Library.x. Navigate from `value`. | Both modules compile; Consumer has no errors and definition points into Library.x. No separate Gradle build is needed. |
| X46 | Open Library.x and change its method to `static String value()="text";` without saving. | Consumer gains a type error without an edit/version change there. Disk still contains the Int version. |
| X47 | Replace Library's method with `MissingType broken;`, then restore the original method. | The original compiler error belongs to Library. Consumer reports `DEPENDENCY-FAILED` and has no stale navigation; correction clears both files. |
| X48 | Make the unsaved String change again, then discard and close Library.x. Reopen it. | Consumer recovers using the Int version on disk; reopening uses current text. Closing an overlay does not retain its unsaved artifact. |
| X49 | While Library is closed, delete its root on disk, then restore it; ensure watched-file notifications reach the server. | Library reports `SOURCE-UNAVAILABLE`; Consumer reports `DEPENDENCY-FAILED`. Restoring the file clears both without editing Consumer. |
| X50 | Open an unsaved Library/Extra.x with `class Extra { MissingType broken; }`, then discard/close it. Repeat with a saved member and disk deletion. | The member owns its compiler diagnostic. Consumer blocks, then recovers when the invalid member disappears; removed diagnostics clear. |
| X51 | Make rapid valid/invalid edits in Library while querying Consumer, then leave a valid Int method. Keep an unrelated module open. | Final diagnostics/navigation use the latest inputs; obsolete requests cannot restore older facts. The unrelated module remains available. |
| X52 | Add Bridge.x with `module Bridge { package lib import Library; static Int value()=lib.value(); }`; register Bridge depending on Library and change Consumer's edge/import to Bridge. Repeat X46–X47. | Changes propagate Library → Bridge → Consumer. A broken Bridge blocks Consumer; correction restores the chain. |

There is a 100 ms edit debounce; compiler cancellation remains cooperative. Source cycles and
overlapping roots are rejected during configuration. Blocked consumers currently expose no
semantic or structural views until their dependencies recover. Hosts must supply accurate edges;
the server does not infer a project graph from unresolved imports or build-tool files.

Automated adapter/server coverage of this setup, including uncooperative late compiler results,
cursor cancellation, snapshot timing and binary replacement:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkProjectTest' \
    --tests 'org.xvm.lsp.server.XdkProjectServerTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

### Configuration update checks

After X45–X52, set `xtc.compiler.sourceModules` to `[]`: Consumer should report its unavailable
Library dependency. Restore the list and expect diagnostics to clear without editing Consumer.
Introduce a cycle by adding Consumer as a dependency of Library: expect a configuration error and
the old working graph to remain active. Restore valid settings, repeat the same list, and verify
that existing semantic results remain available. These checks exercise the ordinary editor bridge;
the host-only binary-artifact section above still requires a Kotlin host.

### H. Compiler-validated rename

Use the compiler backend and an editor that advertises `workspace.workspaceEdit.documentChanges`.
If it does not, record this section as unsupported by that client; the compiler server deliberately
omits rename. Create `Rename.x`:

```xtc
module Rename {
    Int value = 10;
    private Int pick(Int input) = input;
    Int run() {
        Int local = pick(input=1);
        function Int() captured = () -> local;
        return captured() + value;
    }
}
```

Use F2 (VS Code) or Shift+F6 (IntelliJ). Wait for diagnostics to clear before each case and undo
accepted edits before continuing. These are bounded semantic edits, not general member refactoring.

| # | Action | Expected result |
|---|--------|-----------------|
| X53 | Rename `local` to `renamed`. | Declaration and captured use change; `value` and unrelated names do not. Recompilation has no errors. |
| X54 | Rename `input` to `number`, first at its declaration, then after undo at `input=1`. | Declaration, method body and named label all change; `=` and argument value remain intact. |
| X55 | Rename `local` to `value`. | No edits: the untouched property use would silently bind to the local even though compilation would succeed. |
| X56 | Without registering Rename.x in a source graph, try renaming `pick`, module `Rename`, property `value`, or a public method's parameter. | Rename unavailable. Public/lambda/constructor parameters, properties and module names remain unsupported. Ordinary instance methods require the explicit graph and checks in section I. |
| X57 | Start a rename and edit another file in the same module, close/reopen the target, or change its version before applying. | Pending work is canceled or rejected as changed; an edit for an old open-buffer version is not applied. Fast machines may need the controlled server regression below to exercise this race. |
| X58 | Introduce a syntax error, try rename, fix it and retry. Try an invalid identifier or an existing local name. | Broken/unsupported/conflicting requests give no edits or temporary diagnostics. A valid rename works again after correction. |

### I. Configured-graph references and method rename

Save these three files in one scratch folder. Register `Contracts` at `Contracts.x`, `Uses` at
`Uses.x` depending on `Contracts`, and `Dormant` at `Dormant.x` depending on `Contracts`, using
`xtc.compiler.sourceModules` as in section G. Start with only Contracts.x open.

```xtc
module Contracts {
    interface Mapper<T> { T map(T value); }
    class Base {
        Int pick(Int value) = value;
        Int choose(Object value) = 0;
    }
}
```

```xtc
module Uses {
    package api import Contracts;
    class Mapper implements api.Mapper<String> {
        @Override String map(String value) = value;
    }
    String run(api.Mapper<String> contract, Mapper impl) = contract.map("a") + impl.map("b");
    Int choose(api.Base box) = box.choose(1);
    conditional Int library(String text) = text.indexOf('a');
    class Named { @Override String toString() = "name"; }
}
```

```xtc
module Dormant {
    package api import Contracts;
    String run(api.Mapper<String> mapper) = mapper.map("c");
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X59 | Find References on the interface's `map` in Contracts.x, with Uses.x and Dormant.x unopened. | The declaration and the two calls through `api.Mapper<String>` appear. The concrete override and `impl.map` have their own identity and are excluded from this exact reference query. |
| X60 | Rename that `map` to `convert`; inspect the preview, apply, then undo. | Five edits across all three files: the contract, concrete override, and all three calls. No diagnostics after recompilation. Closed files have null edit versions; open buffers carry their current versions. |
| X61 | Rename Contracts.Base's `pick` to `choose`. | No edit. The unchanged `box.choose(1)` would select a different overload even though the edited graph compiles. |
| X62 | Open Uses.x, invoke signature help inside `indexOf('a')`, then try renaming `indexOf`. Separately try renaming Named's `toString` override. | The bundled XDK supplies the Char overload's signature; binary `String.indexOf` cannot be renamed. The source override also cannot be renamed because its contract belongs to the bundled XDK. These are resolved binary targets, not missing dependencies. |
| X63 | Add a second `mapper.map("d")` call to Dormant's return expression without saving, query references/rename, then temporarily replace its body with `MissingType broken;`. Restore the fixture. | Queries include the unsaved call and rename uses its current buffer version. An incomplete configured graph gives no reference list or rename edit; proof compilations add no diagnostics of their own. Restoring it restores results. |

Each query captures and compiles the entire explicit graph, including closed members and transitive
consumers. References use exact compiler identities, including source uses of bundled binary members;
method rename follows ordinary instance-method override families and checks all written bindings,
selected calls and dispatch chains before returning edits. Binary ancestors,
mixin/delegating/capped chains, `super(...)` calls and unknown bindings fail closed.
`super` uses a predefined function register rather than the copied method-invocation binding;
extending those facts is recorded with the remaining function-valued call work. Properties,
accessors, constructors, static functions and public-parameter renames are outside this pass.
Preparing a method rename identifies a candidate; the final graph proof can still reject it.
Modules outside the configuration are not discovered, and this is not a persistent index or a proof
about external clients of an exported API. Keep every source consumer in the configured graph.

Controlled regressions in `XdkProjectQueryLifecycleTest` and `XdkCursorServerTest` cover changes in
another module, close/reopen, configuration/dependency replacement, canceled/superseded queries and
late disk changes before returning results. These races are hard to trigger reliably by hand.

For closed-member input checks, version conversion, cancellation and the repeated retention workload:

```bash
./gradlew :lang:lsp-server:test \
    --tests 'org.xvm.lsp.adapter.XdkRenameTest' \
    --tests 'org.xvm.lsp.adapter.XdkProjectQueryTest' \
    --tests 'org.xvm.lsp.adapter.XdkProjectQueryLifecycleTest' \
    --tests 'org.xvm.lsp.server.XdkRenameServerTest' \
    --tests 'org.xvm.lsp.server.XdkCursorServerTest' \
    --tests 'org.xvm.lsp.adapter.XdkRetentionTest' \
    --rerun --no-build-cache \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=compiler
```

The retention test repeatedly replaces dependencies, rebuilds an explicit source graph, performs
rename/cursor proofs and closes/reopens the consumer. It reports actual latency and checks release
of compiler results, AST roots and pools after close and shutdown. This is a bounded automated
workload; record interactive and prolonged editor tests separately.

### J. Property and accessor implementations

Save this as `Properties.x`. Use **Go to Implementations** on the indicated property or accessor
name. A property query lists its effective getter/setter bodies or backing-field declarations;
an accessor query follows only that accessor. The query is declaration-level: a read of a property
has the same implementation set as its declaration, rather than selecting only its getter.

```xtc
module Properties {
    interface Named<T> { T name; }
    class Stored implements Named<String> { @Override String name = "stored"; }
    class Computed implements Named<String> { @Override String name.get() = "computed"; }
    class Inherited extends Stored {}
    class Unrelated { String name = "other"; }
    String read(Named<String> value) = value.name;

    class Base {
        Int value {
            Int get() = 1;
            void set(Int value) {}
        }
    }
    class Child extends Base { @Override Int value.get() = 2; }
    interface Defaulted { @RO String label { @Override String get() = "default"; } }
    class DefaultUser implements Defaulted {}
    class FieldUser implements Defaulted { @Override String label = "field"; }

    interface Missing { @RO String absent; }
    class Forward(Missing target) delegates Missing(target) {}
    class Delayed { @Lazy Int later.calc() = 1; }
    Int size(String text) = text.size;
}
```

| # | Action | Expected result |
|---|--------|-----------------|
| X64 | Find Implementations on `name` in `Named`, then in `value.name`. | Stored's `name` field and Computed's `get` body. Inherited adds no duplicate; Unrelated is excluded. |
| X65 | Query Base's `value`, then its `get` and `set` separately. Query Defaulted's `label`. | Property: Base's getter/setter and Child's getter. Getter: the two getter bodies only. Setter: Base's setter only. Defaulted: its default getter and FieldUser's field. |
| X66 | Query Missing's `absent`, Delayed's `later`, and `size` in `text.size`. | No invented target for an abstract/delegated property, Ref/Var annotation dispatch or bundled binary source. |
| X67 | Create `Properties/Member.x` containing `class Member implements Named<String> { @Override String name.get() = "member"; }`. Close its tab and query Named's `name`. Open the member, insert two blank lines without saving, break its declaration, then restore it. | The closed member adds its getter. Unsaved positions move by two lines; parse failure clears implementation results; correction restores them. |

Lookup requires successful module analysis and uses copied source identities. It does not enable
property rename, infer a runtime delegate receiver or search every configured module for additional
implementations. Ref/Var annotations such as `@Lazy` remain a separate semantic case.

## VS Code Extension Playbook

A self-contained QA runbook for verifying the **VS Code extension** against the same feature surface the IntelliJ plugin is tested against in sections 1–19. Use this whenever you ship a `.vsix` (release, snapshot, or local build) and want end-to-end confidence that nothing regressed for VS Code users. Headless regression coverage of the file-association pipeline is provided by `:lang:vscode-extension:testVscodeExtension` (see the [extension README](../vscode-extension/README.md#testing)); the playbook below covers everything that test can't, which is the interactive LSP / DAP / UI surface.

### Setup

```bash
# 1. Build a fresh .vsix from this checkout
./gradlew :lang:vscode-extension:build \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true

# 2. Install (or upgrade) it
code --install-extension lang/vscode-extension/xtc-language-0.4.4.vsix

# 3. Confirm the LSP server JAR is present (built as part of step 1)
ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar

# 4. Open a workspace with .x files (TestModule.x from section 3 is sufficient)
code /path/to/xtc-project
```

> **Alternative — no install.** `./gradlew :lang:vscode-extension:runCode -PincludeBuildLang=true -PincludeBuildAttachLang=true` launches VS Code in Extension Development Host mode against the build tree with `src/test/fixtures/hello.x` open. Useful for verifying without touching the user profile.

### Keybindings reference

| Action | macOS | Linux / Windows |
|--------|-------|-----------------|
| Command Palette | `Cmd+Shift+P` | `Ctrl+Shift+P` |
| Go to Definition | `F12` or `Cmd+Click` | `F12` or `Ctrl+Click` |
| Peek Definition | `Opt+F12` | `Alt+F12` |
| Find References | `Shift+F12` | `Shift+F12` |
| Rename Symbol | `F2` | `F2` |
| Quick Fix / Code Actions | `Cmd+.` | `Ctrl+.` |
| Hover | mouse hover | mouse hover |
| Trigger Completion | `Ctrl+Space` | `Ctrl+Space` |
| Trigger Parameter Hints | `Cmd+Shift+Space` | `Ctrl+Shift+Space` |
| Format Document | `Shift+Opt+F` | `Shift+Alt+F` |
| Format Selection | `Cmd+K Cmd+F` | `Ctrl+K Ctrl+F` |
| Outline | View → Outline | View → Outline |
| Toggle Line Comment | `Cmd+/` | `Ctrl+/` |
| Toggle Block Comment | `Shift+Opt+A` | `Shift+Alt+A` |
| Fold / Unfold | `Cmd+Opt+[` / `]` | `Ctrl+Shift+[` / `]` |
| Restart Language Server | Cmd Palette → "Ecstasy: Restart Language Server" | Cmd Palette → "Ecstasy: Restart Language Server" |
| Show Server Output | Cmd Palette → "Ecstasy: Show Language Server Output" | same |

### Pre-flight checks

| # | Check | How | Pass condition |
|---|-------|-----|----------------|
| P1 | Extension is loaded | Extensions panel → search "Ecstasy" | "Ecstasy Language Support" listed with version `0.4.4`+ |
| P2 | `.x` files map to Ecstasy language | Open any `.x` file | Status bar (bottom right) reads "Ecstasy" |
| P3 | LSP server started | Status bar (bottom right) | Shows `✓ XTC` (green check). `⟳ XTC` = starting, `⚠ XTC` = error, `✗ XTC` = stopped |
| P4 | Adapter selection visible | Output panel (`Cmd/Ctrl+Shift+U`) → "XTC Language Server" channel | `Backend: TreeSitter` (or `Backend: Mock` if `-Plsp.adapter=mock` build) |
| P5 | Semantic tokens enabled | Same output channel | `semantic tokens ENABLED (23 types, 10 modifiers)` |
| P6 | Java runtime detected | Same output channel at startup | `Java home: /path/to/java` (Java 25+) |
| P7 | Snippets registered | Type `mod` then `Tab` in a `.x` file | Expands to a `module` declaration skeleton |

If P3 stays `⟳`/`⚠`/`✗`, click the status bar item to restart the server. If that fails, jump to [Troubleshooting → VS Code LSP Not Starting](#vs-code-lsp-not-starting).

### Feature playbook

This table maps every numbered feature from sections 1–19 to the exact VS Code action and verification surface. The numeric IDs (e.g. `3.5`) match the test-case IDs in the per-feature sections above — refer there for fine-grained subtests and expected outputs. For compiler mode, use the [XdkAdapter playbook](#xdkadapter-playbook); unsupported compiler features in this table are not expected to pass.

| § | Feature | VS Code action | Where to verify | Notes |
|----|---------|---------------|-----------------|-------|
| 1 | Syntax highlighting (TextMate) | Open `TestModule.x` | Editor view — keywords/strings/comments colored | Active even before LSP connects (P3 still pending). If absent on a `.x` file, P2 failed. |
| 2 | Hover | Hover mouse over a symbol; or `Cmd+K Cmd+I` for keyboard | Tooltip popup | See §2 for expected content per symbol kind. |
| 3 | Completion | Type a prefix → `Ctrl+Space` | Completion popup | Subtests 3.1–3.11 — each row in §3 applies verbatim. |
| 4 | Go to Definition | `F12` or `Cmd+Click` on a symbol | Editor jumps to declaration | Subtests 4.1–4.10 — see §4. `Opt+F12` peeks instead of jumping. |
| 5 | Find References | `Shift+F12` on a symbol | "References" peek view | §5 covers both same-file and cross-file expectations. |
| 6 | Outline | View → Outline (or Cmd+Shift+O for symbols-in-file) | Outline panel populates | §6 — module / class / method hierarchy. All three adapters. |
| 7 | Diagnostics | Save a `.x` file with a known syntax error | Problems panel (Cmd+Shift+M) + red squigglies | §7. The mock adapter reports fewer diagnostics than tree-sitter; the compiler adapter reports semantics neither can see. |
| 7a | Compiler adapter specifics | Build with `-Plsp.adapter=compiler`; watch the server output channel | Problems panel + the `compile:` lines in the log | §7a — bundled-library startup without `XDK_HOME`, cold vs steady timing, queue depth and superseded edits. |
| 8 | Document highlight | Click on an identifier | Other same-name occurrences in file get a subtle highlight box | §8. |
| 9 | Selection ranges | Place cursor in expression → `Shift+Opt+Cmd+→` (macOS) or `Shift+Alt+→` | Selection expands outward through AST nodes | §9. |
| 10 | Folding ranges | Click the gutter triangles or `Cmd+Opt+[` | Block / method / class folds | §10 — verify all listed scopes fold correctly. |
| 11 | Rename symbol | Place cursor on symbol → `F2` → type new name → Enter | All in-file references rename atomically | §11. Cross-file rename is TODO (see Future Enhancements). |
| 12 | Code actions | Place cursor on a diagnostic → `Cmd+.` | Quick-fix menu appears | §12 — varies by adapter. |
| 13 | Document formatting | `Shift+Opt+F` (whole) or `Cmd+K Cmd+F` (selection) | Reformatted source per `xtc.formatting.*` settings | §13. `editor.formatOnSave: true` to verify continuous formatting. |
| 13a | On-type formatting (auto-indent) | Press Enter inside a class / method / `{}` block | Cursor indents to the correct level | §13a. Requires `editor.formatOnType: true` (default for `[xtc]`). |
| 13b | Code style settings | Edit `xtc.formatting.indentSize`, `tabSize`, etc. in `Cmd+,` | Subsequent formatting honors the new values | §13b. The Ecstasy-specific UI section is under **Settings → Extensions → Ecstasy**. |
| 13c | Settings → LSP round-trip | Same as 13b, then trigger a format | LSP server picks up new options via `workspace/configuration` | §13c. Check server log for `Updated formatting options: ...`. |
| 14 | Signature help | Inside a method-call argument list, `Cmd+Shift+Space` | Parameter-list overlay | §14. |
| 15 | Document links | URLs / file paths in comments | Cmd+Click activates them | §15. |
| 15a | Extra source roots (`xtc.sourceRoots`) | Set the setting (string array), restart server, Cmd+Click into an imported external module | Definition jumps into the external tree | §15a subtests apply verbatim. **VS Code uses the `xtc.sourceRoots` setting key**, not env vars or system props (the env-var / sysprop paths are tested separately in §15a.1–.2). |
| 16 | Comment toggling | `Cmd+/` (line) or `Shift+Opt+A` (block) | Lines / blocks comment-toggle | §16 in the IntelliJ-marked section also applies to VS Code via the language-configuration commentary mapping. |
| 17 | Snippets | Type a prefix (`mod`, `cls`, `svc`, `mix`, `con`, `meth`, `run`, `if`, `ife` …) then `Tab` | Expansion appears with tab stops | §17 is IntelliJ-specific in framing, but every snippet in `lang/vscode-extension/snippets/xtc.json` is the VS Code counterpart. |
| 18 | Code lens (run actions) | Open a module/class with a `void run()` | `Run` / `Debug` codelens above the method signature | §18. Powered by the LSP server, identical contract. |
| 19 | Semantic tokens | Open any `.x` file with `editor.semanticHighlighting.enabled: true` (default for `[xtc]`) | Types / methods / properties / annotations colored distinctly | §19. Subtests 19.1–19.11 apply verbatim. |

### VS Code-specific concerns (not covered by the IntelliJ sections)

These have no IntelliJ analogue (or are surfaced differently). They round out the QA pass.

| # | Concern | How to verify | Pass condition |
|---|---------|--------------|----------------|
| V1 | File association on stale profile | Open a workspace where `files.associations` in user `settings.json` maps `"*.x"` to `"plaintext"` or another language | Status bar still shows "Ecstasy" within ~1s; the extension's `ensureXtcLanguageAssociation` hook overrides via `setTextDocumentLanguage`. |
| V2 | File association on tab restore | Close VS Code with a `.x` file open in a non-active tab → reopen the workspace → click the tab | Tab loads with `Ecstasy` language, not the default. Verifies the `onDidChangeActiveTextEditor` listener. |
| V3 | Status bar lifecycle | Watch the bottom-right status item during server startup, idle, restart | Transitions `⟳ XTC` → `✓ XTC` on start; `✓` → `⟳` → `✓` on Cmd-Palette "Ecstasy: Restart Language Server"; `✗ XTC` on crash (LSP server JAR removed or JVM killed) |
| V4 | Output channel routing | Open Output panel → dropdown | Two channels: `XTC Language Server` (LSP traffic) and `Log (Extension Host)` (extension's own `console.log`/warn/error). No errors in either under normal use. |
| V5 | Trace setting | Set `xtc.trace.server: "verbose"` in `settings.json` → restart server | `XTC Language Server` output channel now shows every JSON-RPC frame |
| V6 | Java discovery fallback | Unset `JAVA_HOME`, leave `xtc.java.home` empty → restart VS Code | Extension finds Java via `jdk-utils` (SDKMAN, mise, Homebrew, Gradle cache, etc.) — log line `Java home: …` at startup |
| V7 | Java auto-download | On a machine with no Java 25+: same as V6, but `jdk-utils` finds nothing | Progress notification `Downloading Java 25 JRE for Ecstasy Language Support`; subsequent restart uses the cached JRE silently |
| V8 | Tasks (Gradle build / test / clean / run) | In a workspace with a `build.gradle.kts`, open Terminal → Run Task… | "Build", "Test", "Clean" tasks listed under "xtc"; selecting runs `./gradlew build/test/clean` |
| V9 | Custom task (`xtc.runModule`) | Add a `tasks.json` entry of type `"xtc"` with `moduleName: "TestModule"` → Terminal → Run Task → pick it | Runs `./gradlew runXtc -PmoduleName=TestModule` (or `xtc run TestModule` if `useGradle: false`) |
| V10 | DAP launch from defaults | Open a `.x` with `void run()` → press `F5` | Launches "Debug Ecstasy Module"; auto-detects the module name from `module Foo {}` declaration |
| V11 | DAP launch from `launch.json` | Add a `"type": "xtc"` config → set breakpoint → `F5` | Stops at breakpoint; variables / call stack visible in Debug panel |
| V12 | Create-project command | Cmd Palette → "Ecstasy: Create New Project" → pick `class` / `library` / `service`, pick a folder, enter a name | Terminal labeled "XTC" opens and runs `xtc init <name> --type <kind> --dir <folder>`; new project skeleton appears |
| V13 | Extension uninstall is clean | Uninstall via Extensions panel → reload | No leftover language association for `.x`; the LSP server JVM is terminated (visible in `jps` / Activity Monitor) |
| V14 | `.vsix` re-install replaces cleanly | `code --install-extension xtc-language-*.vsix --force` from a session that already has the extension loaded | Extension reloads; status bar reconnects; no duplicate output channels |
| V15 | `.x` file icon | Look at any `.x` file in the Explorer view | Custom Ecstasy file icon shown (`icons/xtc-file.png`), not the generic document glyph |
| V16 | Marketplace icon | Extensions panel → click the extension entry | Marketplace icon `icons/xtc.png` (256×256 Ecstasy logo) renders at the top of the details page |

### Headless regression test (automated)

The single automated check that exercises the manifest end-to-end:

```bash
./gradlew :lang:vscode-extension:testVscodeExtension \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

It downloads a pinned VS Code build into `.vscode-test/`, loads the extension from the build tree, opens `src/test/fixtures/hello.x`, and asserts `editor.document.languageId === 'xtc'`. Runs in <30 s after the first cached download.

This is the only automated gate for V1/V2 (file association); everything else in this playbook is interactive. Wire `testVscodeExtension` into CI when you want a continuous canary for the manifest pipeline (needs `xvfb` on headless Linux runners — the wrapper auto-detects).

---

## Future Enhancements

### 20. Linked Editing Ranges

Linked editing ranges enable rename-on-type: when the cursor is on an identifier,
all same-name occurrences in the file are highlighted and edited simultaneously.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 20.1 | Basic linked editing | Place cursor on a variable name used multiple times in a method → trigger linked editing (Ctrl+Shift+F2 in VS Code, or via LSP) | All occurrences highlighted; typing renames all simultaneously |
| 20.2 | Single occurrence | Place cursor on identifier used only once | No linked editing ranges returned (need 2+ occurrences) |
| 20.3 | Parameter name | Place cursor on a method parameter name used in the body | Parameter declaration and all uses linked |
| 20.4 | Class name | Place cursor on a class name that appears in the file | All same-name occurrences linked (same-file, text-based) |
| 20.5 | Non-identifier | Place cursor on a keyword or literal | No linked editing ranges |

> **Adapter support**: TreeSitter (same-file text matching). Cross-file linked editing requires compiler/SemanticModel.

### Semantic Tokens: Current Scope and Follow-ups

Tree-sitter supplies the default syntax-based tokens; broader heuristic usage-site classification
remains a possible enhancement. The opt-in compiler adapter now supplies resolved-name tokens and
modifiers independently, with no Tree-sitter fallback or combined adapter. Broader syntax coverage
must preserve the distinction between compiler facts and lexical coloring.

### Cross-File References (partly done)

Tree-sitter supplies cross-file go-to-definition, workspace symbols and import links through its
workspace index. The compiler supplies definition and references across the current module by
resolved identity, including closed member files; workspace symbols cover current module sessions.
Explicit host-indexed dependency sources also supply definition/type-definition and inherited
method-body targets. Reference queries now compile the complete configured source graph; they
include unopened consumers and source uses of binary XDK members without inventing source targets.
Still remaining:

- Reference indexing/discovery for sources outside the configured graph
- Broader member rename and external-consumer closure

### Full Compiler Integration (partly done)

Done - see §6, §7, §7a and [module sessions and hierarchy](#compiler-module-sessions-and-hierarchy):
- Semantic error detection, with the compiler's own codes and spans
- Document outline, from the parsed AST
- Typed hover and identity-based definition/references across a module
- Unsaved member overlays, sibling invalidation and diagnostics at each file's URI/version
- Direct extends/implements hierarchy between source types, including generic parents
- Bounded scope/member completion, generic call-site signatures and incomplete-call candidates
- Type-definition and nominal type/method implementation lookup
- Static call hierarchy, resolved-name tokens, read/write highlights and bounded inlay hints
- Explicit dependency artifacts/source indices, consumer invalidation and server diagnostic refresh
- Automatic dependency recompilation for explicitly configured roots/edges, including unsaved overlays
- Initialization/live source-graph settings, exposed in VS Code as `xtc.compiler.sourceModules`

Still to come:
- Broader Java parser recovery, incomplete-expression contexts and callable forms
- Automatic editor project discovery and a persistent cross-module index
- External/conditional-mixin hierarchy and broader implementation targets
- Wider member/workspace rename: properties/accessors, static functions, constructors, `super` and
  mixin/delegating/capped chains; public-parameter caller closure and consumers outside the graph
- Diagnostic-driven quick fixes and refactorings
