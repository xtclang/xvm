# Features

What each editor supports, area by area; click an area to open it. "Yes" means the feature works in that
editor, through the editor's usual action for it, and each feature links to the section that says how.
[Shortcuts](shortcuts.md) has the keys.

<a name="editing"></a>
<details>
<summary><b>Editing</b></summary>

| Feature | IntelliJ | VS Code | Notes |
|---------|----------|---------|-------|
| [Errors and warnings as you type](editing-and-navigation.md#as-you-type) | Yes | Yes | The compiler checks the editor's text, saved or not |
| [Errors in files that aren't open](editing-and-navigation.md#in-the-whole-project) | Project view and Problems window | Problems view | |
| [Semantic highlighting](editing-and-navigation.md#colors-hints-and-reference-counts) | Yes | Yes | Colors from the compiler, on top of the grammar's |
| [Completion](editing-and-navigation.md#completion) | Yes | Yes | Members, names in scope and argument values, with types |
| [Inline completion](editing-and-navigation.md#completion) | Yes | Yes | A name in scope, suggested as you type |
| [Quick documentation](editing-and-navigation.md#documentation) | Yes | Yes | On hover too, and for the XDK's types |
| [Links](editing-and-navigation.md#documentation) | Yes | Yes | URLs in comments and strings, and imports' names |
| [Parameter info](editing-and-navigation.md#parameter-info) | Yes | Yes | |
| [Inlay hints](editing-and-navigation.md#colors-hints-and-reference-counts) | Yes | Yes | Parameter names and inferred types |
| [Reference counts above declarations](editing-and-navigation.md#colors-hints-and-reference-counts) | Yes | Yes | A click lists the references |
| [Templates](editing-and-navigation.md#templates) | Live templates | Snippets | |
| [Formatting](editing-and-navigation.md#formatting-and-comments) | Yes, also on type and on save | Yes, also on type and on save | Through the server's formatter |
| [Comment out lines](editing-and-navigation.md#formatting-and-comments) | Line and block comments | Line comments | |
| [Folding, extend selection](editing-and-navigation.md#uses-folding-selection-and-outline) | Yes | Yes | |
| Linked editing | Settings \| Editor \| General \| Smart Keys | The `editor.linkedEditing` setting | A local's occurrences change together as you type |

</details>

<a name="navigation"></a>
<details>
<summary><b>Navigation</b></summary>

Both editors go to a definition, a type definition and implementations, find references, highlight a
name's uses in the file, show call and type hierarchies, find a symbol by name, including the XDK's,
show the file's outline, and open the XDK's sources read-only. [Navigating](editing-and-navigation.md#navigating) has the
shortcuts.

</details>

<a name="refactoring"></a>
<details>
<summary><b>Refactoring</b></summary>

| Feature | IntelliJ | VS Code | Notes |
|---------|----------|---------|-------|
| [Rename methods, properties, locals, parameters and types](refactoring.md#rename) | Yes | Yes | Checked by recompiling everything it touches |
| [Rename a class with its file](refactoring.md#rename) | Yes | Yes | |
| [Rename or move a file, move a type to another package](refactoring.md#move) | Yes | Yes, in the Explorer | Uses follow |
| [Extract to a local or a private method](refactoring.md#extract-inline-and-safe-delete) | Yes | Yes | |
| [Inline a local, a private method or a constant](refactoring.md#extract-inline-and-safe-delete) | Yes | Yes | |
| [Safe delete of an unused member](refactoring.md#extract-inline-and-safe-delete) | Yes | Yes | |
| [Quick fixes: create a method or class, add an import, remove what's unused](refactoring.md#quick-fixes-and-imports) | Yes | Yes | |
| [Implement and override members](refactoring.md#quick-fixes-and-imports) | Yes | Yes | One member, or all that are required |
| [Organize imports](refactoring.md#quick-fixes-and-imports) | Yes | Yes | |
| [Generate a doc comment](refactoring.md#quick-fixes-and-imports) | Yes | Yes | |
| [Undo a refactoring across files](refactoring.md#rename) | Asks first, then one step | One step, without asking | |

</details>

<a name="build-and-run"></a>
<details>
<summary><b>Build and run</b></summary>

| Feature | IntelliJ | VS Code | Notes |
|---------|----------|---------|-------|
| [Embedded build mode](building-and-running.md#build-modes) | Yes, the default | Yes, the default | The language server's compiler; no Gradle |
| [Gradle build mode](building-and-running.md#build-modes) | Yes | Yes | The project's own Gradle build |
| [`xtc` command-line mode](building-and-running.md#build-modes) | Yes | No | |
| [Choose the mode](building-and-running.md#build-modes) | Yes | Yes, also from the status bar | |
| [Build](building-and-running.md#build) | Yes, in the Build window | Yes, in a terminal | |
| [Run a module](building-and-running.md#run) | Gutter Run icon, Run lens, run configurations | Run lens, editor Run button, tasks | |
| [Program input](building-and-running.md#program-input) | Run window | Terminal | |
| [Stop and rerun](building-and-running.md#run) | Yes | Yes | |

</details>

<a name="tests"></a>
<details>
<summary><b>Tests</b></summary>

| Feature | IntelliJ | VS Code | Notes |
|---------|----------|---------|-------|
| [Find xunit tests as you write them](testing.md#find-and-run-tests) | Gutter icons | Testing view and gutter icons | No restart needed |
| [Run or debug a test, class, package or module](testing.md#find-and-run-tests) | Gutter icons | Testing view, gutter, Run Test at Cursor | |
| [Results, messages and the failing line](testing.md#read-the-results) | Test runner | Testing view, Test Results | |
| [Rerun failed tests](testing.md#rerun-and-debug-tests) | Yes | Yes | |
| [Run every test module's tests](testing.md#find-and-run-tests) | Run 'All Ecstasy Tests' | Run All Tests | IntelliJ's has no Debug yet |
| [Tests with Gradle's `testXtc`](testing.md#tests-and-the-build-mode) | Yes | Yes | In Gradle mode |
| [Tests with `xtc test`](testing.md#tests-and-the-build-mode) | Yes | No | In command-line mode |

</details>

<a name="debugging"></a>
<details>
<summary><b>Debugging</b></summary>

| Feature | IntelliJ | VS Code | Notes |
|---------|----------|---------|-------|
| [Debug a module, embedded or with Gradle](debugging.md#start-debugging) | Yes | Yes | |
| [Debug a command-line run](debugging.md#start-debugging) | Yes | No | |
| [Attach to a program run with `--debug`](debugging.md#attach-to-a-program-run-outside-the-editor) | Ecstasy Attach configuration | Attach configuration | |
| [Line breakpoints with conditions](debugging.md#conditions-hit-counts-and-logpoints) | Yes | Yes | |
| [Hit counts and logpoints](debugging.md#conditions-hit-counts-and-logpoints) | Yes | Yes | |
| [Function, data and exception breakpoints](debugging.md#function-data-and-exception-breakpoints) | Yes | Yes | |
| [Step over, into, out](debugging.md#step-through-the-program) | Yes | Yes | |
| [Step into a chosen call](debugging.md#step-through-the-program) | Smart Step Into | Step Into Target | |
| [Run to cursor, restart frame, pause](debugging.md#step-through-the-program) | Yes | Yes | IntelliJ calls it Reset Frame |
| [Variables, watches, hovers, inline values](debugging.md#look-at-the-programs-state) | Yes | Yes | |
| [Hexadecimal display, Set Value](debugging.md#look-at-the-programs-state) | Yes | Yes | |
| [Evaluate expressions and run code in the paused frame](debugging.md#evaluate-expressions-and-run-code) | Evaluate Expression, Debug console | Debug Console | With completion of names |
| Loaded sources and modules | Yes | Loaded Scripts; no Modules view | |

</details>

---

[Contents](README.md) · [Shortcuts](shortcuts.md)
