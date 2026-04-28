# VS Code Extension Enhancement Plan

> **Created**: 2026-04-03
> **Status**: Planning
> **Scope**: Making the VS Code extension feel mature and production-ready

## Guiding Principle

**Keep logic in Kotlin, minimize TypeScript.** The LSP server (Kotlin) is shared
across all editors. Every feature implemented there benefits IntelliJ, VS Code,
Neovim, Emacs, and any other LSP client. The VS Code extension's TypeScript code
should be a thin shell -- just enough to wire VS Code APIs to the LSP server and
register VS Code-specific contributions (snippets, tasks, settings UI).

The current `extension.ts` is 136 lines. Ideally it stays under 300.

---

## Current State

### What works
- TextMate syntax highlighting (generated from DSL)
- LSP client connects to out-of-process server via stdio
- All 17 LSP features work (hover, completion, definition, references, symbols,
  formatting, code actions, rename, signature help, folding, etc.)
- "XTC: Create New Project" command
- File icon for `.x` files
- Language configuration (brackets, comments, indentation rules)

### What's missing
- Semantic tokens not enabled (commented out in extension.ts)
- No VS Code settings exposed
- No task provider (build/run/test)
- No debug adapter (DAP) integration
- No snippets
- No workspace/configuration bridge (IntelliJ has this, VS Code doesn't)
- Not published to VS Code Marketplace

---

## 1. Enable Semantic Tokens

### Problem

The extension uses TextMate grammar for highlighting. The LSP server already
implements `textDocument/semanticTokens/full` via `SemanticTokenEncoder`, but
the VS Code client has it disabled (commented out in `extension.ts`).

TextMate can't distinguish between a type name and a variable name when they're
both identifiers. Semantic tokens can.

### Solution

Uncomment and enable semantic tokens in `extension.ts`:

```typescript
const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'xtc' }],
    synchronize: {
        fileEvents: vscode.workspace.createFileSystemWatcher('**/*.x')
    }
    // No special initialization needed -- VS Code automatically uses
    // semantic tokens when the server advertises the capability.
};
```

The server already advertises `semanticTokensProvider` in its capabilities.
VS Code will layer semantic tokens on top of TextMate automatically -- TextMate
provides the base colors, semantic tokens override where they have better info.

**Verify:** that the `SemanticTokenEncoder` token types and modifiers match
VS Code's standard semantic token types. The standard types are: `namespace`,
`type`, `class`, `enum`, `interface`, `struct`, `typeParameter`, `parameter`,
`variable`, `property`, `enumMember`, `decorator`, `function`, `method`,
`keyword`, `comment`, `string`, `number`, `operator`.

### Effort: Trivial (remove comments, test)
### Priority: **Critical** -- biggest visual quality improvement

---

## 2. Bridge workspace/configuration to VS Code Settings

### Problem

The IntelliJ plugin bridges Code Style settings to the LSP server via
`workspace/configuration` requests. The VS Code extension doesn't do this --
the server falls back to hardcoded defaults.

### Solution

Add VS Code settings in `package.json` and configure the client to respond to
`workspace/configuration` requests:

**Add to `package.json` contributes:**
```json
"configuration": {
    "title": "XTC",
    "properties": {
        "xtc.formatting.indentSize": {
            "type": "number",
            "default": 4,
            "description": "Number of spaces per indentation level"
        },
        "xtc.formatting.continuationIndentSize": {
            "type": "number",
            "default": 8,
            "description": "Number of spaces for continuation lines"
        },
        "xtc.formatting.tabSize": {
            "type": "number",
            "default": 4,
            "description": "Tab size"
        },
        "xtc.formatting.insertSpaces": {
            "type": "boolean",
            "default": true,
            "description": "Insert spaces instead of tabs"
        },
        "xtc.formatting.maxLineWidth": {
            "type": "number",
            "default": 120,
            "description": "Maximum line width"
        },
        "xtc.server.logLevel": {
            "type": "string",
            "default": "INFO",
            "enum": ["DEBUG", "INFO", "WARN", "ERROR"],
            "description": "LSP server log level"
        },
        "xtc.xdkPath": {
            "type": "string",
            "default": "",
            "description": "Path to XDK installation (auto-detected if empty)"
        }
    }
}
```

The `vscode-languageclient` library automatically handles `workspace/configuration`
requests by reading from `vscode.workspace.getConfiguration()`. We just need to
ensure the section name matches what the server requests (`xtc.formatting`).

In `extension.ts`, add middleware if the section mapping isn't automatic:
```typescript
const clientOptions: LanguageClientOptions = {
    // ... existing options ...
    middleware: {
        workspace: {
            configuration: async (params, token, next) => {
                const result = await next(params, token);
                // VS Code automatically reads from settings -- this middleware
                // is only needed if we need to transform the values.
                return result;
            }
        }
    }
};
```

### Effort: Small (package.json settings + verify client behavior)
### Priority: **High** -- formatting quality depends on correct settings

---

## 3. Snippets (No TypeScript Needed)

### Problem

No code snippets for common XTC patterns. Users have to type everything manually.

### Solution

Add a `snippets/xtc.json` file and register it in `package.json`. This is pure
JSON -- no TypeScript code.

**Add to `package.json` contributes:**
```json
"snippets": [
    {
        "language": "xtc",
        "path": "./snippets/xtc.json"
    }
]
```

**Create `snippets/xtc.json`:**
```json
{
    "Module": {
        "prefix": "mod",
        "body": ["module ${1:name} {", "    $0", "}"],
        "description": "XTC module declaration"
    },
    "Class": {
        "prefix": "cls",
        "body": ["class ${1:Name} {", "    $0", "}"],
        "description": "XTC class declaration"
    },
    "Interface": {
        "prefix": "iface",
        "body": ["interface ${1:Name} {", "    $0", "}"],
        "description": "XTC interface declaration"
    },
    "Service": {
        "prefix": "svc",
        "body": ["service ${1:Name} {", "    $0", "}"],
        "description": "XTC service declaration"
    },
    "Mixin": {
        "prefix": "mixin",
        "body": ["mixin ${1:Name} into ${2:Base} {", "    $0", "}"],
        "description": "XTC mixin declaration"
    },
    "Enum": {
        "prefix": "enum",
        "body": ["enum ${1:Name} {", "    ${2:VALUE}", "}"],
        "description": "XTC enum declaration"
    },
    "Const": {
        "prefix": "const",
        "body": ["const ${1:Name}(${2:params});"],
        "description": "XTC const declaration"
    },
    "Method": {
        "prefix": "meth",
        "body": ["${1:void} ${2:name}(${3}) {", "    $0", "}"],
        "description": "XTC method declaration"
    },
    "Property": {
        "prefix": "prop",
        "body": ["${1:Type} ${2:name};"],
        "description": "XTC property declaration"
    },
    "For Loop": {
        "prefix": "fori",
        "body": ["for (Int ${1:i} : 0 ..< ${2:count}) {", "    $0", "}"],
        "description": "XTC indexed for loop"
    },
    "For Each": {
        "prefix": "fore",
        "body": ["for (${1:Type} ${2:item} : ${3:iterable}) {", "    $0", "}"],
        "description": "XTC for-each loop"
    },
    "If": {
        "prefix": "if",
        "body": ["if (${1:condition}) {", "    $0", "}"],
        "description": "XTC if statement"
    },
    "If-Else": {
        "prefix": "ife",
        "body": ["if (${1:condition}) {", "    $2", "} else {", "    $0", "}"],
        "description": "XTC if-else statement"
    },
    "Switch": {
        "prefix": "switch",
        "body": ["switch (${1:expr}) {", "    case ${2:value}:", "        $0", "}"],
        "description": "XTC switch statement"
    },
    "Try-Catch": {
        "prefix": "try",
        "body": ["try {", "    $1", "} catch (${2:Exception} e) {", "    $0", "}"],
        "description": "XTC try-catch block"
    },
    "Console Print": {
        "prefix": "sout",
        "body": ["@Inject Console console;", "console.print($0);"],
        "description": "Print to console"
    },
    "Import": {
        "prefix": "imp",
        "body": ["import ${1:module.Type};"],
        "description": "XTC import statement"
    },
    "Doc Comment": {
        "prefix": "doc",
        "body": ["/**", " * ${1:description}", " */"],
        "description": "Documentation comment"
    },
    "TODO": {
        "prefix": "todo",
        "body": ["// TODO $0"],
        "description": "TODO comment"
    }
}
```

### Effort: Small (JSON only, no TypeScript)
### Priority: **High** -- big productivity boost, zero maintenance
### Status: **Done** (2026-04-03) — `snippets/xtc.json` with 30+ snippets matching the IntelliJ live templates

---

## 4. Task Provider (Build/Run/Test)

### Problem

VS Code has no way to build, run, or test XTC projects. Users must manually
open terminals and type Gradle commands.

### Solution

Register VS Code tasks via `package.json` (no TypeScript needed for basic tasks):

**Add to `package.json` contributes:**
```json
"taskDefinitions": [
    {
        "type": "xtc",
        "required": ["task"],
        "properties": {
            "task": {
                "type": "string",
                "description": "The XTC task to run",
                "enum": ["build", "run", "test", "clean"]
            },
            "module": {
                "type": "string",
                "description": "Module name (for run/test)"
            },
            "method": {
                "type": "string",
                "description": "Method to invoke (default: run)"
            }
        }
    }
],
"problemMatchers": [
    {
        "name": "xtc",
        "owner": "xtc",
        "fileLocation": ["relative", "${workspaceFolder}"],
        "pattern": {
            "regexp": "^(.+):(\\d+):(\\d+):\\s+(error|warning):\\s+(.+)$",
            "file": 1,
            "line": 2,
            "column": 3,
            "severity": 4,
            "message": 5
        }
    }
]
```

For a more polished experience, add a `TaskProvider` in TypeScript (~50 lines)
that auto-detects whether the project uses Gradle or standalone XDK and provides
appropriate default tasks. But the `package.json` approach works without any TS.

### Alternative: XDK Direct Run

For non-Gradle projects, add a task that invokes `javatools.jar` directly:
```json
{
    "label": "XTC: Run Module (XDK)",
    "type": "shell",
    "command": "java",
    "args": ["-jar", "${config:xtc.xdkPath}/javatools/javatools.jar",
             "run", "-L", "${config:xtc.xdkPath}/lib",
             "${input:moduleName}"]
}
```

This mirrors the XDK run mode proposed for IntelliJ in `idea-specific.md`.

### Effort: Small-medium
### Priority: **High** -- essential for workflow integration

---

## 5. Debug Adapter (DAP) Integration

### Problem

The DAP server exists (`lang/dap-server/`) but is not wired to the VS Code
extension. Users cannot set breakpoints or debug XTC code.

### Solution

**Step 1:** Ensure the DAP server JAR is built and packaged:
- Add fatJar task to `lang/dap-server/build.gradle.kts`
- Add copy task in `lang/vscode-extension/build.gradle.kts` to copy to `server/`

**Step 2:** Register debug contributions in `package.json`:
```json
"debuggers": [
    {
        "type": "xtc",
        "label": "XTC Debug",
        "program": "./server/dap-server.jar",
        "runtime": "java",
        "languages": ["xtc"],
        "configurationAttributes": {
            "launch": {
                "required": ["module"],
                "properties": {
                    "module": {
                        "type": "string",
                        "description": "XTC module to debug"
                    },
                    "method": {
                        "type": "string",
                        "description": "Method to invoke",
                        "default": "run"
                    },
                    "xdkPath": {
                        "type": "string",
                        "description": "Path to XDK installation"
                    }
                }
            }
        },
        "initialConfigurations": [
            {
                "type": "xtc",
                "request": "launch",
                "name": "Debug XTC Module",
                "module": "${command:AskForModuleName}"
            }
        ],
        "configurationSnippets": [
            {
                "label": "XTC: Launch Module",
                "description": "Debug an XTC module",
                "body": {
                    "type": "xtc",
                    "request": "launch",
                    "name": "Debug ${1:module}",
                    "module": "${1:mymodule}"
                }
            }
        ]
    }
],
"breakpoints": [
    { "language": "xtc" }
]
```

**Step 3:** Add DAP adapter factory in `extension.ts` (~30 lines of TypeScript):
```typescript
const debugAdapterFactory = new class implements vscode.DebugAdapterDescriptorFactory {
    createDebugAdapterDescriptor(session: vscode.DebugSession) {
        const dapJar = context.asAbsolutePath(path.join('server', 'dap-server.jar'));
        return new vscode.DebugAdapterExecutable(javaExecutable, ['-jar', dapJar]);
    }
};
context.subscriptions.push(
    vscode.debug.registerDebugAdapterDescriptorFactory('xtc', debugAdapterFactory)
);
```

### Effort: Medium (mostly DAP server-side work)
### Priority: **Medium** -- important but DAP server needs more implementation first

---

## 6. Status Bar Item

### Problem

No visual indicator that the Ecstasy language server is running or what adapter is
active.

### Solution

Add a status bar item in `extension.ts` (~15 lines):

```typescript
const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
statusBar.text = '$(gear~spin) XTC';
statusBar.tooltip = 'Ecstasy Language Server starting...';
statusBar.show();

client.onReady().then(() => {
    statusBar.text = '$(check) XTC';
    statusBar.tooltip = 'Ecstasy Language Server running';
});
```

The server could also send the adapter name (`TreeSitter`, `XDK`) via a custom
notification so the status bar shows which backend is active.

### Effort: Trivial
### Priority: **Low** -- nice polish

---

## 7. Marketplace Publishing

### Problem

The extension is only available as a `.vsix` file from GitHub releases. Users
can't discover or install it from the VS Code Marketplace.

### Steps

1. Create publisher account at https://marketplace.visualstudio.com/manage
2. Generate Personal Access Token (PAT) from Azure DevOps
3. Add marketplace metadata to `package.json`:
   ```json
   "galleryBanner": { "color": "#1e1e1e", "theme": "dark" },
   "icon": "icons/xtc-marketplace.png",
   "badges": []
   ```
4. Publish: `vsce publish`
5. Add CI step to auto-publish on release tags

### Priority: **Medium** -- important for adoption, but not blocking development

---

## Implementation Order

| Phase | Items | TypeScript? | Effort | Impact |
|-------|-------|-------------|--------|--------|
| **Phase 1** | Enable semantic tokens | 1 line change | Trivial | Critical |
| **Phase 2** | Snippets + settings | JSON only | Small | High |
| **Phase 3** | workspace/configuration bridge | ~10 lines TS | Small | High |
| **Phase 4** | Task provider | JSON + ~50 lines TS | Small | High |
| **Phase 5** | DAP integration | ~30 lines TS | Medium | Medium |
| **Phase 6** | Status bar + polish | ~15 lines TS | Trivial | Low |
| **Phase 7** | Marketplace publishing | No code | Small | Medium |

**Total new TypeScript:** ~100 lines across all phases.
**Everything else:** JSON configuration + Kotlin in the LSP/DAP servers.

---

## Key Files

| File | Purpose |
|------|---------|
| `lang/vscode-extension/package.json` | Extension manifest -- most features are pure JSON here |
| `lang/vscode-extension/src/extension.ts` | Thin TypeScript shell (136 lines today, ~250 target) |
| `lang/vscode-extension/snippets/xtc.json` | Code snippets (new) |
| `lang/vscode-extension/build.gradle.kts` | Build orchestration |
| `lang/lsp-server/` | All intelligence lives here (Kotlin) |
| `lang/dap-server/` | Debug adapter (Kotlin, needs work) |
