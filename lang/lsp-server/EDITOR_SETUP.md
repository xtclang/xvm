# Testing the XTC LSP Server with External Editors

This guide explains how to build the XTC LSP server jar and connect it to
text editors like Neovim, Emacs, or VS Code — without using the IntelliJ plugin.

## Prerequisites

- **Java 25+** is required for the default tree-sitter adapter (it uses the
  Foreign Function & Memory API). Verify with `java -version`.
- If you don't have Java 25, you can build with the regex-based mock adapter
  instead (see the build step below).

## Step 1: Build the Fat JAR

The fat JAR bundles all dependencies into a single self-contained jar.

The `lang` composite build is disabled by default, so you must enable it with
Gradle properties:

```bash
./gradlew :lang:lsp-server:fatJar -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

The output jar is:

```
lang/lsp-server/build/libs/lsp-server-<version>-all.jar
```

For example: `lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar`

### Using the mock adapter (no Java 25 required)

If you don't have Java 25+, build with the mock adapter instead:

```bash
./gradlew :lang:lsp-server:fatJar -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.adapter=mock
```

The mock adapter uses regex-based parsing and has no native dependencies. It
provides basic symbol detection but not full syntax-aware features.

## Step 2: Verify the Server Starts

Run a quick smoke test by sending a raw LSP `initialize` request via stdin:

```bash
echo -ne 'Content-Length: 73\r\n\r\n{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{}}}' \
  | java --enable-native-access=ALL-UNNAMED \
         -jar lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar
```

You should see a JSON-RPC response containing the server's capabilities. The
server also writes logs to `~/.xtc/logs/lsp-server.log`.

If you built with the mock adapter, you can omit `--enable-native-access=ALL-UNNAMED`.

## Step 3: Connect to Your Editor

The LSP server communicates over **stdio** using the standard JSON-RPC protocol.
Any editor with LSP support can use it by launching:

```bash
java --enable-native-access=ALL-UNNAMED -jar /absolute/path/to/lsp-server-0.4.4-SNAPSHOT-all.jar
```

Replace the jar path with the actual absolute path on your system.

---

### Neovim (built-in LSP, no plugins needed)

Neovim has native LSP support since v0.5. Add the following to
`~/.config/nvim/init.lua`:

```lua
-- Register .x files as the 'xtc' filetype
vim.filetype.add({
  extension = {
    x = 'xtc',
  },
})

-- Start the XTC LSP server when opening .x files
vim.api.nvim_create_autocmd('FileType', {
  pattern = 'xtc',
  callback = function()
    vim.lsp.start({
      name = 'xtc-lsp',
      cmd = {
        'java',
        '--enable-native-access=ALL-UNNAMED',
        '-jar',
        -- UPDATE THIS PATH to match your build output:
        '/absolute/path/to/lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar',
      },
      root_dir = vim.fs.dirname(
        vim.fs.find({ '.git', 'build.gradle.kts' }, { upward = true })[1]
      ),
    })
  end,
})
```

Then open any `.x` file in Neovim. The LSP server will start automatically.

**Verify it's running:** Use `:LspInfo` to confirm the server is attached, or
`:lua vim.print(vim.lsp.get_clients())` to see client details.

---

### Emacs with eglot (built into Emacs 29+)

Add the following to your Emacs configuration (`~/.emacs.d/init.el` or
`~/.emacs`):

```elisp
;; Associate .x files with a major mode
(add-to-list 'auto-mode-alist '("\\.x\\'" . prog-mode))

;; Register the XTC LSP server with eglot
(with-eval-after-load 'eglot
  (add-to-list 'eglot-server-programs
               '(prog-mode . ("java"
                               "--enable-native-access=ALL-UNNAMED"
                               "-jar"
                               ;; UPDATE THIS PATH to match your build output:
                               "/absolute/path/to/lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar"))))
```

Then open a `.x` file and run `M-x eglot` to start the language server.

**With lsp-mode** (alternative to eglot):

```elisp
(require 'lsp-mode)

(add-to-list 'auto-mode-alist '("\\.x\\'" . prog-mode))

(lsp-register-client
 (make-lsp-client
  :new-connection (lsp-stdio-connection
                   '("java"
                     "--enable-native-access=ALL-UNNAMED"
                     "-jar"
                     ;; UPDATE THIS PATH:
                     "/absolute/path/to/lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar"))
  :major-modes '(prog-mode)
  :server-id 'xtc-lsp))

(add-hook 'prog-mode-hook #'lsp)
```

---

### VS Code (extension already exists in-tree)

The repository includes a VS Code extension. The quickest way to test:

```bash
./gradlew :lang:vscode-extension:runCode
```

This launches a VS Code instance with the XTC extension pre-installed.

To build and install the extension manually:

```bash
./gradlew :lang:vscode-extension:build
```

Then install the `.vsix` from `lang/vscode-extension/build/distributions/`.

---

### Sublime Text with LSP package

1. Install the [LSP](https://packagecontrol.io/packages/LSP) package via Package Control.
2. Open **Preferences > Package Settings > LSP > Settings** and add:

```json
{
  "clients": {
    "xtc-lsp": {
      "enabled": true,
      "command": [
        "java",
        "--enable-native-access=ALL-UNNAMED",
        "-jar",
        "/absolute/path/to/lang/lsp-server/build/libs/lsp-server-0.4.4-SNAPSHOT-all.jar"
      ],
      "selector": "source.x"
    }
  }
}
```

3. Open a `.x` file. The server should start automatically.

---

## Headless Testing with Neovim

You can verify the LSP server works end-to-end without opening a GUI by running
Neovim in headless mode. These commands exercise the actual LSP protocol and
confirm the server responds correctly.

All examples use `Boolean.x` from the standard library. Adjust the path for
your own `.x` files.

### Verify the server attaches

Confirms the LSP client connects and the filetype is detected:

```bash
nvim --headless \
  -c "edit lib_ecstasy/src/main/x/ecstasy/Boolean.x" \
  -c "sleep 5" \
  -c "lua print('filetype=' .. vim.bo.filetype)" \
  -c "lua print('clients=' .. #vim.lsp.get_clients())" \
  -c "qa" 2>&1
```

Expected output:

```
filetype=xtc
clients=1
```

### Check server capabilities

Queries the capabilities the server advertised during initialization:

```bash
nvim --headless \
  -c "edit lib_ecstasy/src/main/x/ecstasy/Boolean.x" \
  -c "sleep 5" \
  -c "lua local c = vim.lsp.get_clients()[1].server_capabilities; \
       print('hover=' .. tostring(c.hoverProvider)); \
       print('completion=' .. tostring(c.completionProvider ~= nil)); \
       print('definition=' .. tostring(c.definitionProvider)); \
       print('documentSymbol=' .. tostring(c.documentSymbolProvider)); \
       print('references=' .. tostring(c.referencesProvider)); \
       print('rename=' .. tostring(c.renameProvider)); \
       print('foldingRange=' .. tostring(c.foldingRangeProvider))" \
  -c "qa" 2>&1
```

Expected output:

```
hover=true
completion=true
definition=true
documentSymbol=true
references=true
rename=true
foldingRange=true
```

### Request document symbols

Sends `textDocument/documentSymbol` and prints the symbols found:

```bash
nvim --headless \
  -c "edit lib_ecstasy/src/main/x/ecstasy/Boolean.x" \
  -c "sleep 5" \
  -c "lua local params = { textDocument = vim.lsp.util.make_text_document_params() }; \
       local results = vim.lsp.buf_request_sync(0, 'textDocument/documentSymbol', params, 5000); \
       for _, res in pairs(results or {}) do \
         if res.result then \
           print('Document symbols: ' .. #res.result); \
           for i, sym in ipairs(res.result) do \
             if i <= 10 then \
               print('  ' .. sym.name .. ' [' .. (vim.lsp.protocol.SymbolKind[sym.kind] or sym.kind) .. ']') \
             end \
           end \
         end \
       end" \
  -c "qa" 2>&1
```

Expected output (first 10 of 31 symbols):

```
Document symbols: 31
  Boolean [Enum]
  and [Method]
  or [Method]
  xor [Method]
  not [Method]
  toBit [Method]
  toByte [Method]
  and [Method]
  or [Method]
  xor [Method]
```

### Request hover information

Sends `textDocument/hover` for line 1, column 0 (the `enum Boolean` declaration):

```bash
nvim --headless \
  -c "edit lib_ecstasy/src/main/x/ecstasy/Boolean.x" \
  -c "sleep 5" \
  -c "lua vim.api.nvim_win_set_cursor(0, {1, 0}); \
       local params = vim.lsp.util.make_position_params(); \
       local results = vim.lsp.buf_request_sync(0, 'textDocument/hover', params, 5000); \
       for _, res in pairs(results or {}) do \
         if res.result and res.result.contents then \
           local c = res.result.contents; \
           print('Hover: ' .. (type(c) == 'table' and c.value or tostring(c))) \
         end \
       end" \
  -c "qa" 2>&1
```

Expected output:

```
Hover: ```xtc
enum Boolean
```​
```

### Check the log file after tests

After running any of the above, verify the server logged the requests:

```bash
tail -20 ~/.xtc/logs/lsp-server.log
```

You should see entries like:

```
XTC Language Server v0.4.4-SNAPSHOT (pid=...)
Backend: TreeSitter
XTC Language Server initialized
textDocument/didOpen: .../Boolean.x (2050 bytes)
[TreeSitter] parsed in 2.3ms (full), 0 errors, 31 symbols
```

---

## Troubleshooting

### Check the log file

The LSP server writes detailed logs to:

```
~/.xtc/logs/lsp-server.log
```

Tail the log while testing to see server activity in real time:

```bash
tail -f ~/.xtc/logs/lsp-server.log
```

### "UnsatisfiedLinkError" or tree-sitter failures

This means the tree-sitter native library could not be loaded. Either:

- Make sure you're running Java 25+ (`java -version`)
- Or rebuild with the mock adapter: `./gradlew :lang:lsp-server:fatJar -Plsp.adapter=mock`

### Server starts but editor shows no features

- Confirm the editor is sending `initialize` and receiving a response (check
  the log file).
- Make sure the file extension is `.x` and mapped to the correct filetype/mode.
- In Neovim, check `:LspLog` for errors.
- In Emacs eglot, check `*EGLOT (...) events*` buffer.

### Wrong Java version on PATH

If your default `java` is older than 25, point to the correct binary explicitly:

```bash
/path/to/java-25/bin/java --enable-native-access=ALL-UNNAMED -jar lsp-server-0.4.4-SNAPSHOT-all.jar
```

Update the editor configuration to use the full path to the Java 25+ binary
instead of just `java`.
