# LSP Server Logging

**Status**: COMPLETE (2026-01-31)

**Goal**: Add comprehensive logging to the LSP server core, common to ALL adapters (Mock, TreeSitter, future Compiler).

---

## Core Logging (XtcLanguageServer)

These logging points apply regardless of which adapter is used:

1. **Server Lifecycle**
   - Server initialization: adapter type, configuration
   - Client capabilities received
   - Workspace folder changes
   - Server shutdown

2. **Document Events**
   - File open: URI, size, detected language version
   - File change: URI, change type (full/incremental)
   - File close: URI, session duration
   - File save: URI

3. **LSP Request/Response**
   - Request received: method, params summary
   - Response sent: method, result count, timing
   - Errors: method, error code, message

---

## Adapter-Specific Logging

Additional logging in each adapter implementation:

**MockXtcCompilerAdapter**:
- Regex pattern matches
- Declaration extraction

**TreeSitterAdapter**:
- Native library loading: path, platform, load time
- Parse operations: file path, source size, parse time, error count
- Query execution: query name, execution time, match count

**Future CompilerAdapter**:
- Type resolution, semantic analysis timing

---

## Implementation

Use SLF4J (already a dependency) with appropriate log levels:

| Level | Use |
|-------|-----|
| `DEBUG` | Detailed operation info (parse times, query results) |
| `INFO` | High-level operations (server started, file opened) |
| `WARN` | Recoverable issues (parse errors, missing symbols) |
| `ERROR` | Failures (initialization failed, unhandled exceptions) |

---

## Example Output

```
INFO  [XtcLanguageServer] Started with adapter: TreeSitterAdapter
INFO  [XtcLanguageServer] Client capabilities: completion, hover, definition
DEBUG [XtcLanguageServer] textDocument/didOpen: file:///project/MyClass.x (2,450 bytes)
DEBUG [TreeSitterAdapter] Parsed in 3.2ms, 0 errors
DEBUG [XtcLanguageServer] textDocument/documentSymbol: 12 symbols in 4.1ms
```

---

## Testing

```bash
# Run with debug logging (works with any adapter)
./gradlew :lang:intellij-plugin:runIde

# In IntelliJ: Help → Diagnostic Tools → Debug Log Settings
# Add: org.xvm.lsp
```