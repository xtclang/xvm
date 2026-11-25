<!-- The screenshots in images/ come from the editor tours: each one's tour step names it and its crop (GuideImage in the IntelliJ
     tour's TourScript.kt, guide in the VS Code tour's script.ts), and lang/intellij-plugin/tour/guide.py rebuilds them from a tour run. -->
# Ecstasy Language Tooling: User Guide

This guide is for developers who are new to the Ecstasy (XTC) tools in IntelliJ IDEA and VS Code. It
covers installing them, then editing, navigating, refactoring, building, running, testing and debugging
Ecstasy code, and says which features each editor has.

Both editors use the same two servers, which ship with the plugin and the extension:

- The **language server** (LSP) analyzes your code with the Ecstasy compiler as you type. It powers
  errors, completion, navigation and refactoring. It can also build, run and test your modules with
  its own compiler, which is the *embedded* build mode.
- The **debug adapter** (DAP) runs inside the language server and lets you debug Ecstasy programs:
  breakpoints, stepping, variables, watches and evaluation.

So the features behave the same in both editors. Where they differ, it is in the editor's own
interface, and the guide says where.

**Start here:** [Getting started](getting-started.md) installs the plugin or extension, opens a first
project and walks through your first five minutes.

## Contents

1. [Getting started](getting-started.md): requirements, installing the IntelliJ plugin or the VS Code
   extension, a first project and your first five minutes.
2. [Editing and navigation](editing-and-navigation.md): the language server, reading and writing code,
   errors and warnings, and finding your way around, including the XDK's sources.
3. [Refactoring](refactoring.md): rename, move, extract, inline, safe delete, quick fixes and imports,
   each checked by the compiler before anything changes.
4. [Building and running](building-and-running.md): the build modes, building, running a module, and
   which Gradle settings an embedded run takes.
5. [Testing with xunit](testing.md): writing tests, finding, running and debugging them, reading the
   results, and tests in each build mode.
6. [The debugger](debugging.md): starting and attaching, breakpoints, stepping, the program's state,
   evaluating expressions and running code.
7. [Settings, troubleshooting and limitations](settings-and-troubleshooting.md): the settings that
   matter, what to do when something doesn't work, and the known limitations.

## Look things up

- [Features](features.md): what each editor supports, area by area.
- [Shortcuts](shortcuts.md): every shortcut in the guide, IntelliJ beside VS Code.

## Conventions

The screenshots use the card game in [`lang/test-fixtures/tour/cardGame`](../../test-fixtures/tour/cardGame).
The shortcuts are those of the editors' default Windows keymaps, which IntelliJ's Linux keymap shares.
On macOS, most shortcuts use Cmd in place of Ctrl; each editor's keymap reference lists the rest. The
Ecstasy chord <kbd>Ctrl+Alt+X</kbd>, then a letter is <kbd>Control+Option+X</kbd>, then the letter on macOS.

## More

- New to the language itself? The [Ecstasy wiki](https://github.com/xtclang/xvm/wiki) introduces it.
- For the details behind each feature, see the reference documents:
  [IntelliJ plugin](../../intellij-plugin/README.md), [VS Code extension](../../vscode-extension/README.md),
  [language server](../../lsp-server/README.md), [configuration reference](../configuration.md) and the
  [manual test plan](../manual-test-plan.md), with examples to try and their expected results.
- Found a problem? Report it as an [issue](https://github.com/xtclang/xvm/issues);
  [Troubleshooting](settings-and-troubleshooting.md#troubleshooting) says how to export the server's
  logs to attach.
