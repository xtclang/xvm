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
with <kbd>Ctrl+Alt+X</kbd>, then <kbd>A</kbd>; see [Settings that matter](settings-and-troubleshooting.md#settings-that-matter).

## Reading code

### Colors, hints and reference counts

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

### Documentation

Hover a name, or press <kbd>Ctrl+Q</kbd> (IntelliJ) or <kbd>Ctrl+K</kbd> <kbd>Ctrl+I</kbd> (VS Code), for its declaration and doc
comment, as the compiler sees it. This works for the XDK's own types too.

URLs in comments and strings are links, and so is an import's name, to the source it imports: <kbd>Ctrl</kbd>+click
one to open it.

<table><tr><td width="50%">

![IntelliJ quick documentation for INITIAL_HAND_SIZE, its type and doc comment](images/intellij-hover.png)
*IntelliJ: quick documentation for `INITIAL_HAND_SIZE`.*

</td><td width="50%">

![VS Code hover over INITIAL_HAND_SIZE showing its type and doc comment, with reference counts and parameter hints in the code](images/vscode-hover.png)
*VS Code: the hover of `INITIAL_HAND_SIZE`.*

</td></tr></table>

### Uses, folding, selection and outline

With the cursor on a name, its uses in the file are highlighted. Methods and doc comments fold.
Extend selection grows the selection by the syntax around it: <kbd>Ctrl+W</kbd> and <kbd>Ctrl+Shift+W</kbd> in IntelliJ,
<kbd>Shift+Alt+Right</kbd> and <kbd>Shift+Alt+Left</kbd> in VS Code.

The file's outline is IntelliJ's **File Structure** popup (<kbd>Ctrl+F12</kbd>) and **Structure** window, and
VS Code's **Outline** view and breadcrumbs.

## Writing code

### Completion

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

### Parameter info

Inside a call, <kbd>Ctrl+P</kbd> (IntelliJ) or <kbd>Ctrl+Shift+Space</kbd> (VS Code) shows the parameters of the method it
calls, with the one at the cursor in bold. It also opens by itself as you type the call's `(`.

<table><tr><td width="50%">

![IntelliJ parameter info for deal, with the current parameter in bold](images/intellij-parameter-info.png)
*IntelliJ: parameter info in a call of `deal`.*

</td><td width="50%">

![VS Code parameter hints inside a call, with the current parameter in bold](images/vscode-parameter-hints.png)
*VS Code: parameter hints in a call of `deal`, with its doc comment.*

</td></tr></table>

### Templates

Templates write common declarations. In IntelliJ, <kbd>Ctrl+J</kbd> lists the live templates, such as `cls`,
`meth`, `fori` and `sout`; in VS Code, the snippets complete with the other suggestions, such as
`meth` for a method. <kbd>Tab</kbd> moves through a template's placeholders.

### Formatting and comments

Formatting goes through the language server's formatter, with your indentation settings
(IntelliJ's **Settings | Editor | Code Style | Ecstasy**, VS Code's `xtc.formatting.*` settings, or an
`.editorconfig` file; see [Formatting settings](#formatting-settings)).
Reformat a file or a selection with <kbd>Ctrl+Alt+L</kbd> (IntelliJ) or <kbd>Shift+Alt+F</kbd> (VS Code). <kbd>Enter</kbd> and `}`
indent as you type, and typing `{` and <kbd>Enter</kbd> closes the block. To format on save, use IntelliJ's
**Actions on Save | Reformat code**, or VS Code's `editor.formatOnSave`.

Formatting wraps long lines of code at the right margin. To have it refill the paragraphs of doc and block comments
that run past the margin too, turn on IntelliJ's **Wrapping and Braces | Comments | Wrap at right margin**, or VS
Code's `xtc.formatting.wrapComments`. Paragraphs, `@param` and `@return` lines, list items and indented or fenced
code keep their layout, a paragraph that fits stays as it is, and a word or URL is never broken.

<kbd>Ctrl+/</kbd> comments out the selected lines with `//`; in IntelliJ, <kbd>Ctrl+Shift+/</kbd> wraps a selection in
`/* */`.

### Formatting settings

The formatter takes each setting from the first of these that sets it:

1. The `.editorconfig` files of the file being formatted.
2. Your editor's Ecstasy settings: IntelliJ's **Settings | Editor | Code Style | Ecstasy**, or VS Code's
   `xtc.formatting.*` settings, which apply to your user or to the workspace, not to one folder. The language server
   asks the editor for them when it starts, and again when you change them.
3. The defaults: 4 spaces per level, 8 for continuation lines, a margin of 120 columns, and comments left as they are.

| Setting | IntelliJ's Code Style page | VS Code | `.editorconfig` |
|---|---|---|---|
| Indentation | **Tabs and Indents: Indent** | `xtc.formatting.indentSize` | `indent_size` |
| Continuation lines | **Tabs and Indents: Continuation indent** | `xtc.formatting.continuationIndentSize` | `ij_ecstasy_continuation_indent_size`, `ij_continuation_indent_size` or `continuation_indent_size` |
| Tabs or spaces | **Tabs and Indents: Use tab character** | `xtc.formatting.insertSpaces` | `indent_style`: `space` or `tab` |
| Tab width | **Tabs and Indents: Tab size** | `editor.tabSize` | `tab_width` |
| Right margin | **Wrapping and Braces: Hard wrap at** | `xtc.formatting.maxLineWidth` | `max_line_length`, or `off` for none |
| Refill comments | **Wrapping and Braces: Comments, Wrap at right margin** | `xtc.formatting.wrapComments` | `ij_ecstasy_wrap_comments`: `true` or `false` |
| Trailing whitespace | What the editor sends with the request | What the editor sends with the request | `trim_trailing_whitespace`: `true` or `false` |
| Final newline | What the editor sends with the request | What the editor sends with the request | `insert_final_newline`: `true` or `false` |
| Line endings | **Reformat Code** sets the file's, as **File \| File Properties \| Line Separators** does | **Format Document** sets the document's | `end_of_line`: `lf`, `crlf` or `cr` |
| Formatter markers | **Code Style \| General: Turn formatter on/off with markers in code comments** | `xtc.formatting.formatterTagsEnabled` | `ij_formatter_tags_enabled`: `true` or `false` |
| Off and on tags | The same page's **Off** and **On** | `xtc.formatting.formatterOffTag`, `xtc.formatting.formatterOnTag` | `ij_formatter_off_tag`, `ij_formatter_on_tag` |
| Tags as regular expressions | The same page's **Enable regular expressions in formatter markers** | `xtc.formatting.formatterTagsAcceptRegexp` | `ij_formatter_tags_accept_regexp` |

**Formatter markers.** With the markers on, formatting leaves the lines from a comment with the off tag through the
next comment with the on tag, or to the end of the file, as they are, both when you reformat and as you type:

```ecstasy
// @formatter:off
static Int[] Identity = [1, 0,
                         0, 1];
// @formatter:on
```

A tag is text the comment contains or, with regular expressions on, a pattern it has a match of.

**`.editorconfig`.** For each file it formats, the language server reads the `.editorconfig` files from the file's
folder up, stopping at one that says `root = true`. It reads them again at every format, so a saved change applies the
next time you format or type. A nearer file wins over one further up, a later section over an earlier one, and what
they set wins over your editor's settings; a property set to `unset` gives it back to the editor. Sections take the
EditorConfig globs: `*`, `**`, `?`, `[abc]`, `[!abc]`, `{a,b}` and `{1..9}`.

The server reads the standard `indent_style`, `indent_size` (`tab` indents by the tab width), `tab_width`,
`trim_trailing_whitespace` and `insert_final_newline`, and the widely used `max_line_length`. For the settings the
standard has no name for, it reads the names IntelliJ gives them in `.editorconfig`, so a file that IntelliJ exports
from its code style works in both editors, and for the continuation indent also the EditorConfig wiki's
`continuation_indent_size`. A formatting edit cannot change a file's line endings,
as both editors give the text it inserts the document's own, so `end_of_line` is applied by the editor, to the `lf`,
`crlf` or `cr` that the server reads. In IntelliJ, **Reformat Code** of a whole file converts it, as **File | File
Properties | Line Separators** does, which saves the file, and a format on save writes it with them; IntelliJ's own
EditorConfig support gives the separator only to new files. As with that action, a file whose line separator, as the
status bar shows it, is already the one asked for keeps its line endings, mixed ones too, and a read-only file keeps
them. In VS Code, **Format Document**, and
`editor.formatOnSave`, converts the document (VS Code has no `cr`). `charset` stays with the editor, and other
properties are not read. A value the formatter cannot use, such as `indent_size = 0` or `indent_style = spaces`, is
skipped, with a warning in the language server's log.

```ini
root = true

[*.x]
indent_style = space
indent_size = 4
max_line_length = 120
ij_ecstasy_continuation_indent_size = 8
ij_ecstasy_wrap_comments = true
```

In IntelliJ, the formatter reads `.editorconfig` files only while IntelliJ applies them itself, that is, while
**Code Style | General | Enable EditorConfig support** is on and the bundled EditorConfig plugin is enabled. In VS Code
it always reads them: VS Code has no such switch, and its EditorConfig extension applies the files only to VS Code's
own typing, such as the <kbd>Tab</kbd> key. A document that is not a file on disk, such as an unsaved new one, takes
the editor's settings.

## Errors and warnings

### As you type

The compiler checks the editor's text as you type, not the saved file, so an error shows as soon as
you make it. Warnings show too, such as an assertion that can never fail.

<table><tr><td width="50%">

![IntelliJ: popCrd underlined as an error, with the COMPILER-56 message and a fix that creates the method](images/intellij-error-message.png)
*IntelliJ: a misspelled `popCard` is an error as soon as it is typed; <kbd>Ctrl+F1</kbd> shows the compiler's message, with a fix.*

</td><td width="50%">

![VS Code: the COMPILER-56 message for popCrd, shown under the line](images/vscode-error-message.png)
*VS Code: <kbd>F8</kbd> shows the compiler's message, `COMPILER-56`, under the line.*

</td></tr></table>

### In the whole project

Every problem of the project is in IntelliJ's **Problems** window and VS Code's **Problems** view
(<kbd>Ctrl+Shift+M</kbd>), each a click away from its code. That includes files you haven't opened: change a
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
| Open the declaration of what's under the cursor | <kbd>Ctrl+B</kbd> or <kbd>Ctrl</kbd>+click | <kbd>F12</kbd> or <kbd>Ctrl</kbd>+click |
| Open the declaration of its type | <kbd>Ctrl+Shift+B</kbd> | **Go to Type Definition** |
| List the implementations of an interface method | <kbd>Ctrl+Alt+B</kbd> | <kbd>Ctrl+F12</kbd> |
| Find every use, in all modules | <kbd>Alt+F7</kbd> | <kbd>Shift+Alt+F12</kbd> |
| See who calls a method | <kbd>Ctrl+Alt+H</kbd> | **Show Call Hierarchy** |
| See a class's supertypes and subtypes | <kbd>Ctrl+H</kbd> | **Show Type Hierarchy** |
| Find a class, method or property by name, the XDK's too | <kbd>Ctrl+Alt+Shift+N</kbd>, twice to include the XDK's | <kbd>Ctrl+T</kbd> |

<table><tr><td width="50%">

![IntelliJ call hierarchy of cardMatches, called by the player and both strategies](images/intellij-call-hierarchy.png)
*IntelliJ: <kbd>Ctrl+Alt+H</kbd> on `cardMatches` lists its callers: the player, both strategies' `choose` and the tests.*

</td><td width="50%">

![VS Code call hierarchy of cardMatches in the side bar](images/vscode-call-hierarchy.png)
*VS Code: **Show Call Hierarchy** on `cardMatches` lists its callers in the side bar: the player, both strategies and the tests.*

</td></tr></table>

<table><tr><td width="50%">

![IntelliJ Find usages of cardMatches in the Find window](images/intellij-find-usages.png)
*IntelliJ: <kbd>Alt+F7</kbd> on `cardMatches` finds its uses, including those in the tests, in the Find window.*

</td><td width="50%">

![VS Code References view: 9 results for cardMatches in 4 files](images/vscode-find-references.png)
*VS Code: Find All References on `cardMatches` finds 9 uses in 4 files, including the tests.*

</td></tr></table>

### The XDK's sources

Navigation reaches into the XDK as well: going to `HashMap`'s declaration opens the `ecstasy`
module's source, read-only. In VS Code its names are links too, as the language server compiles the library's
sources when the first one opens; IntelliJ doesn't follow them yet, as LSP4IJ 0.30.1 doesn't navigate from a
read-only library source.

<table><tr><td width="50%">

![IntelliJ with the XDK's HashMap.x open, read-only](images/intellij-xdk-sources.png)
*IntelliJ: Go to Declaration on `HashMap` opens the XDK's source, read-only.*

</td><td width="50%">

![The XDK's HashMap source, opened read-only from a Go to Definition](images/vscode-xdk-sources.png)
*VS Code: Go to Definition on `HashMap` opens the XDK's source, read-only; the status bar shows the server compiling the library's sources.*

</td></tr></table>

---

[← Getting started](getting-started.md) · [Contents](README.md) · [Refactoring →](refactoring.md)
