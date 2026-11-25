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

## Index

Every page of the guide with its sections, and the areas of the lookup pages:

<!-- Made from the pages' headings and areas: keep it in step when one changes. -->

- [Getting started](getting-started.md)
  - [Requirements](getting-started.md#requirements)
  - [Install the IntelliJ plugin](getting-started.md#install-the-intellij-plugin)
  - [Install the VS Code extension](getting-started.md#install-the-vs-code-extension)
  - [Open or create a project](getting-started.md#open-or-create-a-project)
    - [Create a project](getting-started.md#create-a-project)
    - [The card game in the screenshots](getting-started.md#the-card-game-in-the-screenshots)
  - [Your first five minutes](getting-started.md#your-first-five-minutes)
- [Editing and navigation](editing-and-navigation.md)
  - [The language server](editing-and-navigation.md#the-language-server)
  - [Reading code](editing-and-navigation.md#reading-code)
    - [Colors, hints and reference counts](editing-and-navigation.md#colors-hints-and-reference-counts)
    - [Documentation](editing-and-navigation.md#documentation)
    - [Uses, folding, selection and outline](editing-and-navigation.md#uses-folding-selection-and-outline)
  - [Writing code](editing-and-navigation.md#writing-code)
    - [Completion](editing-and-navigation.md#completion)
    - [Parameter info](editing-and-navigation.md#parameter-info)
    - [Templates](editing-and-navigation.md#templates)
    - [Formatting and comments](editing-and-navigation.md#formatting-and-comments)
  - [Errors and warnings](editing-and-navigation.md#errors-and-warnings)
    - [As you type](editing-and-navigation.md#as-you-type)
    - [In the whole project](editing-and-navigation.md#in-the-whole-project)
  - [Navigating](editing-and-navigation.md#navigating)
    - [The XDK's sources](editing-and-navigation.md#the-xdks-sources)
- [Refactoring](refactoring.md)
  - [Rename](refactoring.md#rename)
  - [Move](refactoring.md#move)
  - [Extract, inline and safe delete](refactoring.md#extract-inline-and-safe-delete)
  - [Quick fixes and imports](refactoring.md#quick-fixes-and-imports)
- [Building and running](building-and-running.md)
  - [Build modes](building-and-running.md#build-modes)
  - [Build](building-and-running.md#build)
  - [Run](building-and-running.md#run)
    - [Program input](building-and-running.md#program-input)
    - [Embedded and Gradle runs](building-and-running.md#embedded-and-gradle-runs)
  - [Gradle settings in embedded runs](building-and-running.md#gradle-settings-in-embedded-runs)
- [Testing with xunit](testing.md)
  - [Write a test](testing.md#write-a-test)
  - [Find and run tests](testing.md#find-and-run-tests)
  - [Read the results](testing.md#read-the-results)
  - [Rerun and debug tests](testing.md#rerun-and-debug-tests)
  - [Tests and the build mode](testing.md#tests-and-the-build-mode)
  - [When tests don't show or don't run](testing.md#when-tests-dont-show-or-dont-run)
- [The debugger](debugging.md)
  - [Start debugging](debugging.md#start-debugging)
  - [Attach to a program run outside the editor](debugging.md#attach-to-a-program-run-outside-the-editor)
  - [Breakpoints](debugging.md#breakpoints)
    - [Conditions, hit counts and logpoints](debugging.md#conditions-hit-counts-and-logpoints)
    - [Function, data and exception breakpoints](debugging.md#function-data-and-exception-breakpoints)
    - [Breakpoints while you edit](debugging.md#breakpoints-while-you-edit)
  - [Step through the program](debugging.md#step-through-the-program)
  - [Look at the program's state](debugging.md#look-at-the-programs-state)
  - [Evaluate expressions and run code](debugging.md#evaluate-expressions-and-run-code)
- [Settings, troubleshooting and limitations](settings-and-troubleshooting.md)
  - [Settings that matter](settings-and-troubleshooting.md#settings-that-matter)
  - [Troubleshooting](settings-and-troubleshooting.md#troubleshooting)
    - [The language server](settings-and-troubleshooting.md#the-language-server)
    - [Common problems](settings-and-troubleshooting.md#common-problems)
  - [Known limitations](settings-and-troubleshooting.md#known-limitations)
    - [Editing](settings-and-troubleshooting.md#editing)
    - [Refactoring](settings-and-troubleshooting.md#refactoring)
    - [Building, running and testing](settings-and-troubleshooting.md#building-running-and-testing)
    - [Debugging](settings-and-troubleshooting.md#debugging)
- [Features](features.md)
  - [Editing](features.md#editing)
  - [Navigation](features.md#navigation)
  - [Refactoring](features.md#refactoring)
  - [Build and run](features.md#build-and-run)
  - [Tests](features.md#tests)
  - [Debugging](features.md#debugging)
- [Shortcuts](shortcuts.md)
  - [Ecstasy commands](shortcuts.md#ecstasy-commands)
  - [Editing](shortcuts.md#editing)
  - [Navigation](shortcuts.md#navigation)
  - [Refactoring](shortcuts.md#refactoring)
  - [Building and running](shortcuts.md#building-and-running)
  - [Debugging](shortcuts.md#debugging)

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
