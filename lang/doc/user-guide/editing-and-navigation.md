# Editing and navigation

## The language server

The plugin or extension starts the language server when you open an Ecstasy file. Its first analysis
of a project takes some seconds; after that, it rechecks what you change.

- **VS Code** shows the server's state in the status bar: a spinning `Ecstasy` while it starts,
  `✓ Ecstasy · Compiler` when it is ready, and a warning or error icon when it has failed, which you
  can click to restart it.
- **IntelliJ** shows it in LSP4IJ's **Language Servers** window, which also traces what the editor
  and the server say to each other.

The server's default *adapter* analyzes code with the compiler. The other adapter, Tree-sitter,
understands only syntax: it is a fallback for a project that the compiler has trouble with. Switch
with **Ctrl+Alt+X, then A**; see [Settings that matter](settings-and-troubleshooting.md#settings-that-matter).

## Reading code

The compiler colors names by what they are: fields, parameters, locals, types and static members.
Inlay hints show the parameter names of a call whose arguments don't say what they are, and the
inferred types of locals. A line above each declaration counts its references in the project, tests
included, and a click lists them.

<table><tr><td width="50%">

![IntelliJ: Eights.x with parameter name hints such as numCards, and reference counts above each declaration](images/intellij-inlay-hints.png)
*IntelliJ: inlay hints name a call's parameters, such as `numCards:`, and the line above each declaration counts its references.*

</td><td width="50%">

![VS Code: Eights.x with parameter name hints and reference counts](images/vscode-inlay-hints.png)
*VS Code: hints name `deal`'s parameters, `that` and `numCards`, and the line above each declaration counts its references.*

</td></tr></table>

Hover a name, or press Ctrl+Q (IntelliJ) or Ctrl+K Ctrl+I (VS Code), for its declaration and doc
comment, as the compiler sees it. This works for the XDK's own types too.

<table><tr><td width="50%">

![IntelliJ quick documentation for INITIAL_HAND_SIZE, its type and doc comment](images/intellij-hover.png)
*IntelliJ: quick documentation for `INITIAL_HAND_SIZE`.*

</td><td width="50%">

![VS Code hover over INITIAL_HAND_SIZE showing its type and doc comment, with reference counts and parameter hints in the code](images/vscode-hover.png)
*VS Code: the hover of `INITIAL_HAND_SIZE`.*

</td></tr></table>

With the cursor on a name, its uses in the file are highlighted. Methods and doc comments fold.
Extend selection grows the selection by the syntax around it: Ctrl+W and Ctrl+Shift+W in IntelliJ,
Shift+Alt+Right and Shift+Alt+Left in VS Code.

The file's outline is IntelliJ's **File Structure** popup (Ctrl+F12) and **Structure** window, and
VS Code's **Outline** view and breadcrumbs.

## Writing code

After a dot, completion lists the members of the expression's type as the compiler resolves it,
with their types. As you type a name, it offers the types, variables and keywords in scope. In an
argument list it also offers the values in scope that fit the parameter. Choosing a type from another
module adds its import.

<table><tr><td width="50%">

![IntelliJ completion list after hand., listing Hand's members with their types](images/intellij-completion.png)
*IntelliJ: completion after `hand.`.*

</td><td width="50%">

![VS Code completion list after hand., listing Hand's members](images/vscode-completion.png)
*VS Code: completion after `hand.`.*

</td></tr></table>

Inside a call, Ctrl+P (IntelliJ) or Ctrl+Shift+Space (VS Code) shows the parameters of the method it
calls, with the one at the cursor in bold. It also opens by itself as you type the call's `(`.

<table><tr><td width="50%">

![IntelliJ parameter info for deal, with the current parameter in bold](images/intellij-parameter-info.png)
*IntelliJ: parameter info in a call of `deal`.*

</td><td width="50%">

![VS Code parameter hints inside a call, with the current parameter in bold](images/vscode-parameter-hints.png)
*VS Code: parameter hints in a call of `deal`, with its doc comment.*

</td></tr></table>

Templates write common declarations. In IntelliJ, Ctrl+J lists the live templates, such as `cls`,
`meth`, `fori` and `sout`; in VS Code, the snippets complete with the other suggestions, such as
`meth` for a method. Tab moves through a template's placeholders.

**Formatting** goes through the language server's formatter, with your indentation settings
(IntelliJ's **Settings | Editor | Code Style | Ecstasy**, VS Code's `xtc.formatting.*` settings).
Reformat a file or a selection with Ctrl+Alt+L (IntelliJ) or Shift+Alt+F (VS Code). Enter and `}`
indent as you type, and typing `{` and Enter closes the block. To format on save, use IntelliJ's
**Actions on Save | Reformat code**, or VS Code's `editor.formatOnSave`.

Ctrl+/ comments out the selected lines with `//`; in IntelliJ, Ctrl+Shift+/ wraps a selection in
`/* */`.

## Errors and warnings

The compiler checks the editor's text as you type, not the saved file, so an error shows as soon as
you make it. Warnings show too, such as an assertion that can never fail.

<table><tr><td width="50%">

![IntelliJ: popCrd underlined as an error, with the COMPILER-56 message and a fix that creates the method](images/intellij-error-message.png)
*IntelliJ: a misspelled `popCard` is an error as soon as it is typed; Ctrl+F1 shows the compiler's message, with a fix.*

</td><td width="50%">

![VS Code: the COMPILER-56 message for popCrd, shown under the line](images/vscode-error-message.png)
*VS Code: F8 shows the compiler's message, COMPILER-56, under the line.*

</td></tr></table>

Every problem of the project is in IntelliJ's **Problems** window and VS Code's **Problems** view
(Ctrl+Shift+M), each a click away from its code. That includes files you haven't opened: change a
method's signature, and its callers in other files and in the test modules are listed as errors
until you fix them. IntelliJ also draws such files red in the Project view.

<table><tr><td width="50%">

![IntelliJ Problems window with the file's problems and the Project Errors tab](images/intellij-problems.png)
*IntelliJ: the Problems window lists the file's errors and warnings, and Project Errors the rest of the project's.*

</td><td width="50%">

![VS Code: Card.x with rank renamed to rnk, unsaved; the Explorer marks the files that break, and the Problems view lists their errors](images/vscode-problems.png)
*VS Code: renaming `Card.rank` without saving breaks its uses in files that aren't open, and the test modules that depend on them; the Problems view lists them all.*

</td></tr></table>

## Navigating

| To | IntelliJ | VS Code |
|----|----------|---------|
| Open the declaration of what's under the cursor | Ctrl+B or Ctrl+click | F12 or Ctrl+click |
| Open the declaration of its type | Ctrl+Shift+B | **Go to Type Definition** |
| List the implementations of an interface method | Ctrl+Alt+B | Ctrl+F12 |
| Find every use, in all modules | Alt+F7 | Shift+Alt+F12 |
| See who calls a method | Ctrl+Alt+H | **Show Call Hierarchy** |
| See a class's supertypes and subtypes | Ctrl+H | **Show Type Hierarchy** |
| Find a class, method or property by name | Ctrl+Alt+Shift+N | Ctrl+T |

<table><tr><td width="50%">

![IntelliJ call hierarchy of cardMatches, called by the player and both strategies](images/intellij-call-hierarchy.png)
*IntelliJ: Ctrl+Alt+H on `cardMatches` lists its callers: the player, both strategies' `choose` and the tests.*

</td><td width="50%">

![VS Code call hierarchy of cardMatches in the side bar](images/vscode-call-hierarchy.png)
*VS Code: **Show Call Hierarchy** on `cardMatches` lists its callers in the side bar: the player, both strategies and the tests.*

</td></tr></table>

<table><tr><td width="50%">

![IntelliJ Find usages of cardMatches in the Find window](images/intellij-find-usages.png)
*IntelliJ: Alt+F7 on `cardMatches` finds its uses, including those in the tests, in the Find window.*

</td><td width="50%">

![VS Code References view: 9 results for cardMatches in 4 files](images/vscode-find-references.png)
*VS Code: Find All References on `cardMatches` finds 9 uses in 4 files, including the tests.*

</td></tr></table>

Navigation reaches into the XDK as well: going to `HashMap`'s declaration opens the `ecstasy`
module's source, read-only. Its names are links too, as the language server compiles the library's
sources when the first one opens.

<table><tr><td width="50%">

![IntelliJ with the XDK's HashMap.x open, read-only](images/intellij-xdk-sources.png)
*IntelliJ: Go to Declaration on `HashMap` opens the XDK's source, read-only.*

</td><td width="50%">

![The XDK's HashMap source, opened read-only from a Go to Definition](images/vscode-xdk-sources.png)
*VS Code: Go to Definition on `HashMap` opens the XDK's source, read-only; the status bar shows the server compiling the library's sources.*

</td></tr></table>

---

[← Getting started](getting-started.md) · [Contents](README.md) · [Refactoring →](refactoring.md)
