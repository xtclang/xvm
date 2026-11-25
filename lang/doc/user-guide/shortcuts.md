# Shortcuts

Every shortcut that the guide names, IntelliJ beside VS Code, by area; click an area to open it. They are
the editors' default Windows keymaps, which IntelliJ's Linux keymap shares. A name in bold is a command or
menu item without a key of its own.

On macOS, most shortcuts use Cmd in place of Ctrl; each editor's keymap reference lists the rest. The
Ecstasy chord <kbd>Ctrl+Alt+X</kbd>, then a letter is <kbd>Control+Option+X</kbd>, then the letter, Build is <kbd>Cmd+F9</kbd>, and
running the module at the cursor is <kbd>Ctrl+Shift+R</kbd>.

<details>
<summary><b>Ecstasy commands</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Choose the build mode | <kbd>Ctrl+Alt+X</kbd>, then <kbd>B</kbd> | <kbd>Ctrl+Alt+X</kbd>, then <kbd>B</kbd> |
| Switch the language adapter: compiler or Tree-sitter | <kbd>Ctrl+Alt+X</kbd>, then <kbd>A</kbd> | <kbd>Ctrl+Alt+X</kbd>, then <kbd>A</kbd> |
| Show or hide the language server's log | <kbd>Ctrl+Alt+X</kbd>, then <kbd>L</kbd> | <kbd>Ctrl+Alt+X</kbd>, then <kbd>L</kbd> |
| Restart the language server | <kbd>Ctrl+Alt+X</kbd>, then <kbd>R</kbd> | <kbd>Ctrl+Alt+X</kbd>, then <kbd>R</kbd> |
| Open the Command Palette, for the **Ecstasy:** commands | | <kbd>Ctrl+Shift+P</kbd> |

</details>

<details>
<summary><b>Editing</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Open completion | <kbd>Ctrl+Space</kbd> | <kbd>Ctrl+Space</kbd> |
| Quick documentation | <kbd>Ctrl+Q</kbd>, or hover | <kbd>Ctrl+K</kbd> <kbd>Ctrl+I</kbd>, or hover |
| Parameter info | <kbd>Ctrl+P</kbd> | <kbd>Ctrl+Shift+Space</kbd> |
| List the templates | <kbd>Ctrl+J</kbd> | |
| Go to a template's next placeholder | <kbd>Tab</kbd> | <kbd>Tab</kbd> |
| Reformat the file or selection | <kbd>Ctrl+Alt+L</kbd> | <kbd>Shift+Alt+F</kbd> |
| Comment out lines | <kbd>Ctrl+/</kbd> | <kbd>Ctrl+/</kbd> |
| Comment out a selection as a block | <kbd>Ctrl+Shift+/</kbd> | |
| Extend the selection | <kbd>Ctrl+W</kbd> | <kbd>Shift+Alt+Right</kbd> |
| Shrink the selection | <kbd>Ctrl+Shift+W</kbd> | <kbd>Shift+Alt+Left</kbd> |
| Show the compiler's message for a problem | <kbd>Ctrl+F1</kbd>, at the problem | <kbd>F8</kbd>, the next problem |
| Open the list of problems | **Problems** window | <kbd>Ctrl+Shift+M</kbd> |

</details>

<details>
<summary><b>Navigation</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Open the declaration of what's under the cursor | <kbd>Ctrl+B</kbd> or <kbd>Ctrl</kbd>+click | <kbd>F12</kbd> or <kbd>Ctrl</kbd>+click |
| Open the declaration of its type | <kbd>Ctrl+Shift+B</kbd> | **Go to Type Definition** |
| List the implementations of an interface method | <kbd>Ctrl+Alt+B</kbd> | <kbd>Ctrl+F12</kbd> |
| Find every use, in all modules | <kbd>Alt+F7</kbd> | <kbd>Shift+Alt+F12</kbd> |
| See who calls a method | <kbd>Ctrl+Alt+H</kbd> | **Show Call Hierarchy** |
| See a class's supertypes and subtypes | <kbd>Ctrl+H</kbd> | **Show Type Hierarchy** |
| Find a class, method or property by name | <kbd>Ctrl+Alt+Shift+N</kbd> | <kbd>Ctrl+T</kbd> |
| The file's outline | <kbd>Ctrl+F12</kbd> | **Outline** view |

</details>

<details>
<summary><b>Refactoring</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Rename | <kbd>Shift+F6</kbd> | <kbd>F2</kbd> |
| Move a file to another package | <kbd>F6</kbd> | Cut and paste, or drag, in the Explorer |
| Code actions: extract, inline, safe delete, quick fixes, doc comments | <kbd>Alt+Enter</kbd> | <kbd>Ctrl+.</kbd> |
| The refactorings of a selection | <kbd>Alt+Enter</kbd> | <kbd>Ctrl+Shift+R</kbd> |
| Organize imports | <kbd>Ctrl+Alt+O</kbd> | <kbd>Shift+Alt+O</kbd> |

</details>

<details>
<summary><b>Building and running</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Build | <kbd>Ctrl+F9</kbd> | <kbd>Ctrl+F9</kbd> |
| Run the module at the cursor | <kbd>Ctrl+Shift+F10</kbd> | <kbd>Ctrl+Shift+F10</kbd> |
| Run the selected run configuration | <kbd>Shift+F10</kbd> | |
| End the program's input | **Send EOF** | <kbd>Ctrl+D</kbd> on an empty line |

</details>

<details>
<summary><b>Debugging</b></summary>

| Action | IntelliJ | VS Code |
|--------|----------|---------|
| Debug | <kbd>Shift+F9</kbd> | <kbd>F5</kbd> |
| Continue | <kbd>F9</kbd> | <kbd>F5</kbd> |
| Step over | <kbd>F8</kbd> | <kbd>F10</kbd> |
| Step into | <kbd>F7</kbd> | <kbd>F11</kbd> |
| Step out | <kbd>Shift+F8</kbd> | <kbd>Shift+F11</kbd> |
| Step into one of the line's calls | <kbd>Shift+F7</kbd>, Smart Step Into | **Step Into Target**, in the editor's context menu |
| Step into the XDK's code | <kbd>Alt+Shift+F7</kbd>, Force Step Into | |
| Run to the cursor's line | <kbd>Alt+F9</kbd> | **Run to Cursor**, in the editor's context menu |
| Stop a running program where it is | **Pause Program** | <kbd>F6</kbd> |
| Open the breakpoints | <kbd>Ctrl+Shift+F8</kbd> | **Breakpoints** view |
| Evaluate an expression in the paused frame | <kbd>Alt+F8</kbd> | <kbd>Ctrl+Shift+Y</kbd>, the Debug Console |

</details>

---

[Contents](README.md) · [Features](features.md)
