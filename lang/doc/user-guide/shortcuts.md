# Shortcuts

Every shortcut that the guide names, IntelliJ beside VS Code, by area; click an area to open it. They are
the editors' default Windows keymaps, which IntelliJ's Linux keymap shares. A name in bold is a command or
menu item without a key of its own.

On macOS, most shortcuts use Cmd in place of Ctrl; each editor's keymap reference lists the rest. The
Ecstasy chord **Ctrl+Alt+X, then a letter** is **Control+Option+X, then the letter**, Build is Cmd+F9, and
running the module at the cursor is Ctrl+Shift+R.

<details>
<summary><b>Ecstasy commands</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Choose the build mode | Ctrl+Alt+X, then B | Ctrl+Alt+X, then B |
| Switch the language adapter: compiler or Tree-sitter | Ctrl+Alt+X, then A | Ctrl+Alt+X, then A |
| Show or hide the language server's log | Ctrl+Alt+X, then L | Ctrl+Alt+X, then L |
| Restart the language server | Ctrl+Alt+X, then R | Ctrl+Alt+X, then R |
| Open the Command Palette, for the **Ecstasy:** commands | | Ctrl+Shift+P |

</details>

<details>
<summary><b>Editing</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Open completion | Ctrl+Space | Ctrl+Space |
| Quick documentation | Ctrl+Q, or hover | Ctrl+K Ctrl+I, or hover |
| Parameter info | Ctrl+P | Ctrl+Shift+Space |
| List the templates | Ctrl+J | |
| Go to a template's next placeholder | Tab | Tab |
| Reformat the file or selection | Ctrl+Alt+L | Shift+Alt+F |
| Comment out lines | Ctrl+/ | Ctrl+/ |
| Comment out a selection as a block | Ctrl+Shift+/ | |
| Extend the selection | Ctrl+W | Shift+Alt+Right |
| Shrink the selection | Ctrl+Shift+W | Shift+Alt+Left |
| Show the compiler's message for a problem | Ctrl+F1, at the problem | F8, the next problem |
| Open the list of problems | **Problems** window | Ctrl+Shift+M |

</details>

<details>
<summary><b>Navigation</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Open the declaration of what's under the cursor | Ctrl+B or Ctrl+click | F12 or Ctrl+click |
| Open the declaration of its type | Ctrl+Shift+B | **Go to Type Definition** |
| List the implementations of an interface method | Ctrl+Alt+B | Ctrl+F12 |
| Find every use, in all modules | Alt+F7 | Shift+Alt+F12 |
| See who calls a method | Ctrl+Alt+H | **Show Call Hierarchy** |
| See a class's supertypes and subtypes | Ctrl+H | **Show Type Hierarchy** |
| Find a class, method or property by name | Ctrl+Alt+Shift+N | Ctrl+T |
| The file's outline | Ctrl+F12 | **Outline** view |

</details>

<details>
<summary><b>Refactoring</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Rename | Shift+F6 | F2 |
| Move a file to another package | F6 | Cut and paste, or drag, in the Explorer |
| Code actions: extract, inline, safe delete, quick fixes, doc comments | Alt+Enter | Ctrl+. |
| The refactorings of a selection | Alt+Enter | Ctrl+Shift+R |
| Organize imports | Ctrl+Alt+O | Shift+Alt+O |

</details>

<details>
<summary><b>Building and running</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Build | Ctrl+F9 | Ctrl+F9 |
| Run the module at the cursor | Ctrl+Shift+F10 | Ctrl+Shift+F10 |
| Run the selected run configuration | Shift+F10 | |
| End the program's input | **Send EOF** | Ctrl+D on an empty line |

</details>

<details>
<summary><b>Debugging</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Debug | Shift+F9 | F5 |
| Continue | F9 | F5 |
| Step over | F8 | F10 |
| Step into | F7 | F11 |
| Step out | Shift+F8 | Shift+F11 |
| Step into one of the line's calls | Shift+F7, Smart Step Into | **Step Into Target**, in the editor's context menu |
| Step into the XDK's code | Alt+Shift+F7, Force Step Into | |
| Run to the cursor's line | Alt+F9 | **Run to Cursor**, in the editor's context menu |
| Stop a running program where it is | **Pause Program** | F6 |
| Open the breakpoints | Ctrl+Shift+F8 | **Breakpoints** view |
| Evaluate an expression in the paused frame | Alt+F8 | Ctrl+Shift+Y, the Debug Console |

</details>

---

[Contents](README.md) · [Features](features.md)
