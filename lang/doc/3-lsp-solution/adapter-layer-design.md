# LSP Adapter Layer Design

## The Short Version

Rather than fixing every architectural problem in the XTC compiler, build an **adapter layer** that extracts data from the compiler into clean, immutable structures suitable for LSP. Let the compiler do what it does, then snapshot its results into a parallel data model designed for concurrent, interactive use.

## The Strategy

### Why an Adapter Layer?

The compiler works. It compiles XTC code correctly. Trying to fix all its architectural issues would:
- Take months of refactoring
- Risk introducing regression bugs
- Require deep understanding of every subsystem
- Block any LSP progress during refactoring

Instead:
1. Let the compiler compile (single-threaded, mutable, fine)
2. After compilation, extract results into immutable structures
3. LSP queries run against the clean structures
4. Compiler and LSP never share mutable state

### The Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    LSP Server                               │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  Hover  │  │Complete  │  │ GoToDef  │  │ FindRefs     │  │
│  └────┬────┘  └────┬─────┘  └────┬─────┘  └───────┬──────┘  │
│       │            │             │                │         │
│       └────────────┴─────────────┴────────────────┘         │
│                          │                                  │
│                    ┌─────▼─────┐                            │
│                    │ Snapshot  │  (Immutable, thread-safe)  │
│                    │  Cache    │                            │
│                    └─────┬─────┘                            │
│                          │                                  │
├──────────────────────────┼──────────────────────────────────┤
│                    ┌─────▼─────┐                            │
│                    │  Adapter  │  (Extraction layer)        │
│                    │   Layer   │                            │
│                    └─────┬─────┘                            │
│                          │                                  │
├──────────────────────────┼──────────────────────────────────┤
│                    ┌─────▼─────┐                            │
│                    │  XTC      │  (Mutable, single-threaded)│
│                    │ Compiler  │                            │
│                    └───────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

## The Clean Data Model

### Core Types (Records)

```java
package org.xvm.lsp.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

// Source location - all fields required
public record Location(
    @NonNull String file,
    int line,
    int column,
    int offset,
    int length
) {
    public Location {
        Objects.requireNonNull(file, "file");
    }
}

public record Range(@NonNull Location start, @NonNull Location end) {
    public Range {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }
}

// Symbols - sealed hierarchy for exhaustive matching
public sealed interface Symbol permits TypeSymbol, MethodSymbol, PropertySymbol, VariableSymbol {
    @NonNull String name();
    @NonNull Location definition();
    @Nullable String documentation();  // May not have docs
}

public record TypeSymbol(
    @NonNull String name,
    @NonNull String qualifiedName,
    @NonNull TypeKind kind,
    @NonNull Location definition,
    @Nullable String documentation,
    @NonNull List<String> typeParameters,
    @Nullable String superType,  // Object has no super
    @NonNull List<String> interfaces,
    @NonNull List<MethodSymbol> methods,
    @NonNull List<PropertySymbol> properties
) implements Symbol {
    public TypeSymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(definition, "definition");
        typeParameters = List.copyOf(typeParameters);
        interfaces = List.copyOf(interfaces);
        methods = List.copyOf(methods);
        properties = List.copyOf(properties);
    }
}

public record MethodSymbol(
    @NonNull String name,
    @NonNull Location definition,
    @Nullable String documentation,
    @NonNull List<ParameterSymbol> parameters,
    @NonNull String returnType,
    @NonNull Set<Modifier> modifiers
) implements Symbol {
    public MethodSymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(returnType, "returnType");
        parameters = List.copyOf(parameters);
        modifiers = Set.copyOf(modifiers);
    }
}

public record PropertySymbol(
    @NonNull String name,
    @NonNull Location definition,
    @Nullable String documentation,
    @NonNull String type,
    @NonNull Set<Modifier> modifiers
) implements Symbol {
    public PropertySymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(type, "type");
        modifiers = Set.copyOf(modifiers);
    }
}

public record VariableSymbol(
    @NonNull String name,
    @NonNull Location definition,
    @Nullable String documentation,
    @NonNull String type,
    @NonNull Range scope
) implements Symbol {
    public VariableSymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
    }
}

public record ParameterSymbol(@NonNull String name, @NonNull String type) {
    public ParameterSymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}

public enum TypeKind { CLASS, INTERFACE, MIXIN, ENUM, SERVICE, CONST }
public enum Modifier { PUBLIC, PRIVATE, PROTECTED, STATIC, ABSTRACT, FINAL }
```

### Diagnostic Types

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record Diagnostic(
    @NonNull Range range,
    @NonNull Severity severity,
    @NonNull String code,
    @NonNull String message,
    @NonNull String source,
    @NonNull List<DiagnosticRelatedInfo> relatedInfo
) {
    public Diagnostic {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        relatedInfo = List.copyOf(relatedInfo);
    }
}

public record DiagnosticRelatedInfo(
    @NonNull Location location,
    @NonNull String message
) {
    public DiagnosticRelatedInfo {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }
}

public enum Severity { ERROR, WARNING, INFO, HINT }
```

### The Snapshot

```java
import org.jspecify.annotations.NonNull;

public record LspSnapshot(
    long version,
    @NonNull Instant timestamp,
    @NonNull Map<String, SourceFile> files,
    @NonNull Map<String, TypeSymbol> types,
    @NonNull Map<String, List<Symbol>> symbolsByFile,
    @NonNull Map<String, List<Diagnostic>> diagnosticsByFile,
    @NonNull SymbolIndex symbolIndex
) {
    // All fields are immutable - Map.copyOf() in compact constructor
    public LspSnapshot {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(symbolIndex, "symbolIndex");
        files = Map.copyOf(files);
        types = Map.copyOf(types);
        symbolsByFile = Map.copyOf(symbolsByFile);
        diagnosticsByFile = Map.copyOf(diagnosticsByFile);
    }

    public static @NonNull LspSnapshot empty() {
        return new LspSnapshot(0, Instant.now(), Map.of(), Map.of(), Map.of(), Map.of(),
            new SymbolIndex(Map.of(), Map.of(), Map.of()));
    }
}

public record SourceFile(
    @NonNull String path,
    @NonNull String content,
    long modificationTime,
    @NonNull List<Symbol> symbols,
    @NonNull List<Diagnostic> diagnostics
) {
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        symbols = List.copyOf(symbols);
        diagnostics = List.copyOf(diagnostics);
    }
}

// Index for fast lookup
public record SymbolIndex(
    @NonNull Map<String, List<Location>> definitionsByName,
    @NonNull Map<Location, List<Location>> referencesByDefinition,
    @NonNull Map<String, List<Symbol>> symbolsByPrefix  // For completion
) {
    public SymbolIndex {
        definitionsByName = Map.copyOf(definitionsByName);
        referencesByDefinition = Map.copyOf(referencesByDefinition);
        symbolsByPrefix = Map.copyOf(symbolsByPrefix);
    }
}
```

## The Adapter Layer

### Main Adapter Class

```java
package org.xvm.lsp.adapter;

import org.jspecify.annotations.NonNull;

public final class CompilerAdapter {
    private final @NonNull XtcCompiler compiler;

    public CompilerAdapter() {
        this.compiler = new XtcCompiler();
    }

    /**
     * Compile a file and extract an LSP snapshot.
     * This is the ONLY entry point from LSP into the compiler.
     */
    public @NonNull LspSnapshot compile(
            final @NonNull String filePath,
            final @NonNull String content) {
        // Phase 1: Run compiler (single-threaded, isolated)
        final var result = compiler.compile(filePath, content);

        // Phase 2: Extract into clean model (while compiler state is stable)
        return extractSnapshot(result);
    }

    private @NonNull LspSnapshot extractSnapshot(final @NonNull CompileResult result) {
        final var files = new HashMap<String, SourceFile>();
        final var types = new HashMap<String, TypeSymbol>();
        final var symbolsByFile = new HashMap<String, List<Symbol>>();
        final var diagnosticsByFile = new HashMap<String, List<Diagnostic>>();

        // Extract types
        for (final var struct : result.getClasses()) {
            final var typeSymbol = extractType(struct);
            types.put(typeSymbol.qualifiedName(), typeSymbol);
        }

        // Extract file-level info
        for (final var entry : result.getSourceFiles().entrySet()) {
            final var path = entry.getKey();
            final var ast = entry.getValue();

            final var symbols = extractSymbols(ast);
            final var diagnostics = extractDiagnostics(path, result.getErrors());

            files.put(path, new SourceFile(path, result.getContent(path),
                System.currentTimeMillis(), symbols, diagnostics));
            symbolsByFile.put(path, symbols);
            diagnosticsByFile.put(path, diagnostics);
        }

        // Build index
        final var index = buildIndex(symbolsByFile.values());

        return new LspSnapshot(
            System.currentTimeMillis(),
            Instant.now(),
            files, types, symbolsByFile, diagnosticsByFile, index
        );
    }
}
```

### Type Extraction

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

private @NonNull TypeSymbol extractType(final @NonNull ClassStructure struct) {
    final var info = struct.getType().ensureTypeInfo();

    return new TypeSymbol(
        struct.getName(),
        struct.getType().getValueString(),
        extractTypeKind(struct),
        extractLocation(struct),
        extractDocumentation(struct),
        extractTypeParameters(struct),
        extractSuperType(info),
        extractInterfaces(info),
        extractMethods(info),
        extractProperties(info)
    );
}

private @NonNull Location extractLocation(final @NonNull Component component) {
    // Extract source location from component
    // This is where we translate compiler's position to LSP position
    return new Location(
        component.getSource().getFileName(),
        component.getLineNumber(),
        component.getColumn(),
        component.getOffset(),
        component.getLength()
    );
}

private @NonNull List<MethodSymbol> extractMethods(final @NonNull TypeInfo info) {
    return info.getMethods().values().stream()
        .filter(m -> !m.isSynthetic())
        .map(this::extractMethod)
        .toList();
}

private @NonNull MethodSymbol extractMethod(final @NonNull MethodInfo method) {
    return new MethodSymbol(
        method.getName(),
        extractLocation(method.getMethodStructure()),
        extractDocumentation(method.getMethodStructure()),
        extractParameters(method),
        method.getReturnType().getValueString(),
        extractModifiers(method)
    );
}
```

### Diagnostic Extraction

```java
import org.jspecify.annotations.NonNull;

private @NonNull List<Diagnostic> extractDiagnostics(
        final @NonNull String file,
        final @NonNull List<ErrorInfo> errors) {
    return errors.stream()
        .filter(e -> file.equals(e.getSource()))
        .map(this::extractDiagnostic)
        .toList();
}

private @NonNull Diagnostic extractDiagnostic(final @NonNull ErrorInfo error) {
    return new Diagnostic(
        new Range(
            new Location(error.getSource(), error.getLine(), error.getColumn(),
                error.getOffset(), 0),
            new Location(error.getSource(), error.getEndLine(), error.getEndColumn(),
                error.getEndOffset(), 0)
        ),
        convertSeverity(error.getSeverity()),
        error.getCode(),
        error.getMessage(),
        "xtc",
        extractRelatedInfo(error)
    );
}
```

## The LSP Service

### Service Layer

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class XtcLanguageService {
    private final @NonNull CompilerAdapter adapter = new CompilerAdapter();
    private volatile @NonNull LspSnapshot currentSnapshot = LspSnapshot.empty();
    private final @NonNull ExecutorService compileExecutor = Executors.newSingleThreadExecutor();
    private static final @NonNull Logger log = LoggerFactory.getLogger(XtcLanguageService.class);

    // Called when file changes
    public void onDidChangeTextDocument(
            final @NonNull String uri,
            final @NonNull String content) {
        // Compile in background, update snapshot atomically
        compileExecutor.submit(() -> {
            try {
                final var snapshot = adapter.compile(uri, content);
                currentSnapshot = snapshot;  // Atomic volatile write
            } catch (final Exception e) {
                log.error("Compilation failed", e);
            }
        });
    }

    // All queries use current snapshot - never touch compiler
    public @NonNull List<CompletionItem> completion(
            final @NonNull String uri,
            final @NonNull Position position) {
        final var snapshot = currentSnapshot;  // Single volatile read
        return snapshot.symbolIndex()
            .symbolsByPrefix()
            .getOrDefault(getPrefix(uri, position), List.of())
            .stream()
            .map(this::toCompletionItem)
            .toList();
    }

    public @NonNull Optional<Location> definition(
            final @NonNull String uri,
            final @NonNull Position position) {
        final var snapshot = currentSnapshot;
        return findSymbolAt(snapshot, uri, position)
            .map(Symbol::definition);
    }

    public @NonNull List<Location> references(
            final @NonNull String uri,
            final @NonNull Position position) {
        final var snapshot = currentSnapshot;
        return findSymbolAt(snapshot, uri, position)
            .map(Symbol::definition)
            .map(def -> snapshot.symbolIndex()
                .referencesByDefinition()
                .getOrDefault(def, List.of()))
            .orElse(List.of());
    }

    public @Nullable Hover hover(
            final @NonNull String uri,
            final @NonNull Position position) {
        final var snapshot = currentSnapshot;
        return findSymbolAt(snapshot, uri, position)
            .map(symbol -> new Hover(formatHover(symbol)))
            .orElse(null);
    }
}
```

### Request Handlers

```java
import org.jspecify.annotations.NonNull;

public final class XtcTextDocumentService implements TextDocumentService {
    private final @NonNull XtcLanguageService service;

    public XtcTextDocumentService(final @NonNull XtcLanguageService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public @NonNull CompletableFuture<List<CompletionItem>> completion(
            final @NonNull CompletionParams params) {
        return CompletableFuture.supplyAsync(() ->
            service.completion(
                params.getTextDocument().getUri(),
                params.getPosition()
            )
        );
    }

    @Override
    public @NonNull CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
            definition(final @NonNull DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            final var def = service.definition(
                params.getTextDocument().getUri(),
                params.getPosition()
            );
            return Either.forLeft(def.map(List::of).orElse(List.of()));
        });
    }

    @Override
    public @NonNull CompletableFuture<Hover> hover(final @NonNull HoverParams params) {
        return CompletableFuture.supplyAsync(() ->
            service.hover(
                params.getTextDocument().getUri(),
                params.getPosition()
            )
        );
    }
}
```

## Key Design Principles

### 1. Compiler Isolation

The compiler runs in its own thread. LSP requests never directly call compiler methods. All data flows through the adapter.

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

// WRONG - direct compiler access from LSP
public Hover hover(Position pos) {
    TypeConstant type = compiler.findTypeAt(pos);  // Race condition!
    return formatHover(type.ensureTypeInfo());     // Blocks!
}

// RIGHT - query snapshot
public @Nullable Hover hover(final @NonNull Position pos) {
    final var snapshot = currentSnapshot;  // Single volatile read
    return snapshot.findSymbolAt(pos)       // Immutable, fast
        .map(this::formatHover)
        .orElse(null);
}
```

### 2. Atomic Snapshot Updates

Snapshots are replaced atomically. Queries always see consistent state.

```java
import org.jspecify.annotations.NonNull;

// Atomic update
private volatile @NonNull LspSnapshot currentSnapshot;

public void updateSnapshot(final @NonNull LspSnapshot newSnapshot) {
    this.currentSnapshot = Objects.requireNonNull(newSnapshot, "newSnapshot");
}

public @NonNull LspSnapshot getSnapshot() {
    return currentSnapshot;  // Single volatile read - always consistent
}
```

### 3. Immutable Everything

Every type in the model is immutable. Records with `List.copyOf()` and `Map.copyOf()`.

```java
import org.jspecify.annotations.NonNull;

// All immutable - collections defensively copied in compact constructor
public record TypeSymbol(
    @NonNull String name,
    @NonNull List<MethodSymbol> methods,
    @NonNull Map<String, PropertySymbol> props
) {
    public TypeSymbol {
        Objects.requireNonNull(name, "name");
        methods = List.copyOf(methods);      // Defensive copy
        props = Map.copyOf(props);           // Defensive copy
    }
}
```

### 4. Fast Queries, Slow Updates

Queries are O(1) lookups. Updates recompute indexes. This is the right trade-off for an IDE.

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

// Fast - direct lookup (O(1))
final @Nullable Symbol symbol = snapshot.symbolIndex()
    .definitionsByName()
    .get(name);

// Slow (but infrequent) - rebuild index on compile (O(n))
final @NonNull SymbolIndex newIndex = buildIndex(symbols);
```

## Handling Incomplete Code

LSP must handle code that doesn't compile. The adapter provides best-effort results:

```java
import org.jspecify.annotations.NonNull;

public @NonNull LspSnapshot extractSnapshot(final @NonNull CompileResult result) {
    final var symbols = new ArrayList<Symbol>();
    final var errors = new ArrayList<Diagnostic>();

    // Extract whatever we can from partial AST
    if (result.hasPartialAst()) {
        try {
            symbols.addAll(extractSymbols(result.getPartialAst()));
        } catch (final Exception e) {
            // Log but don't fail - partial is better than nothing
            log.warn("Partial symbol extraction failed", e);
        }
    }

    // Always extract diagnostics
    errors.addAll(extractDiagnostics(result.getErrors()));

    return new LspSnapshot(
        System.currentTimeMillis(),
        Instant.now(),
        Map.of(),
        Map.of(),
        Map.of(result.getPath(), List.copyOf(symbols)),
        Map.of(result.getPath(), List.copyOf(errors)),
        SymbolIndex.empty()
    );
}
```

## Summary

The adapter layer provides:

| Aspect | Compiler | Adapter | LSP Model |
|--------|----------|---------|-----------|
| Mutability | Mutable | Transforms | Immutable |
| Threading | Single-threaded | Serialized | Concurrent |
| State | Global | Extraction | Snapshot |
| Updates | In-place | Full rebuild | Atomic swap |
| Errors | Throws/logs | Collects | Returns |

**Benefits:**
- LSP ships faster (don't wait for compiler fixes)
- Clean separation of concerns
- Testable independently
- Future compiler fixes can improve extraction, not require model changes

**Costs:**
- Memory for snapshot copies
- Recomputation on every edit
- Some loss of information in extraction

The benefits vastly outweigh the costs for an LSP use case.
