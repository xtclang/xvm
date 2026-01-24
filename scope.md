# Kotlin Scope Functions: Real-World Examples from This Codebase

This document showcases effective uses of Kotlin's scope functions (`let`, `run`, `with`, `apply`, `also`) found in this repository, with Java equivalents to illustrate why the Kotlin versions are more readable and maintainable.

## Quick Reference

| Function | Object Reference | Return Value | Use Case |
|----------|-----------------|--------------|----------|
| `let`    | `it`            | Lambda result | Null-safe transformations |
| `run`    | `this`          | Lambda result | Compute result with scoping |
| `with`   | `this`          | Lambda result | Grouping calls on an object |
| `apply`  | `this`          | Context object | Object configuration |
| `also`   | `it`            | Context object | Side effects |

---

## 1. `apply` — Object Configuration

### Example: Building a Command Line
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt:47-58`

```kotlin
private fun createGradleCommandLine() = GeneralCommandLine().apply {
    exePath = when {
        "win" in System.getProperty("os.name").lowercase() -> "gradlew.bat"
        else -> "./gradlew"
    }
    addParameter("runXtc")
    moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
    methodName.takeIf { it.isNotBlank() }?.let { addParameter("--method=$it") }
    moduleArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
    workDirectory = project.basePath?.let { Path(it).toFile() }
}
```

#### Equivalent Java Code

```java
private GeneralCommandLine createGradleCommandLine() {
    GeneralCommandLine cmd = new GeneralCommandLine();

    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("win")) {
        cmd.setExePath("gradlew.bat");
    } else {
        cmd.setExePath("./gradlew");
    }

    cmd.addParameter("runXtc");

    if (moduleName != null && !moduleName.isBlank()) {
        cmd.addParameter("--module=" + moduleName);
    }
    if (methodName != null && !methodName.isBlank()) {
        cmd.addParameter("--method=" + methodName);
    }
    if (moduleArguments != null && !moduleArguments.isBlank()) {
        cmd.addParameter("--args=" + moduleArguments);
    }

    String basePath = project.getBasePath();
    if (basePath != null) {
        cmd.setWorkDirectory(Path.of(basePath).toFile());
    }

    return cmd;
}
```

#### Why Kotlin is Better

1. **No repetition of the variable name**: Java requires `cmd.` prefix on every call (7 times). Kotlin's `apply` makes the object the implicit receiver.
2. **Expression-oriented**: The Kotlin version is a single expression that returns the configured object. Java requires explicit `return`.
3. **Chained null handling**: `?.let` elegantly handles optional parameters inline. Java requires separate if-null checks for each field.
4. **Cognitive load**: Reading the Kotlin version, you immediately see "create a GeneralCommandLine configured with these properties." In Java, you must mentally track that `cmd` is being built throughout.

---

### Example: Nested Object Configuration
**File:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt:114-151`

```kotlin
val capabilities = ServerCapabilities().apply {
    textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
    hoverProvider = Either.forLeft(true)
    completionProvider = CompletionOptions().apply {
        triggerCharacters = listOf(".", ":", "<")
        resolveProvider = false
    }
    definitionProvider = Either.forLeft(true)
    referencesProvider = Either.forLeft(true)
    documentSymbolProvider = Either.forLeft(true)
}
```

#### Equivalent Java Code

```java
ServerCapabilities capabilities = new ServerCapabilities();
capabilities.setTextDocumentSync(Either.forLeft(TextDocumentSyncKind.Full));
capabilities.setHoverProvider(Either.forLeft(true));

CompletionOptions completionOptions = new CompletionOptions();
completionOptions.setTriggerCharacters(List.of(".", ":", "<"));
completionOptions.setResolveProvider(false);
capabilities.setCompletionProvider(completionOptions);

capabilities.setDefinitionProvider(Either.forLeft(true));
capabilities.setReferencesProvider(Either.forLeft(true));
capabilities.setDocumentSymbolProvider(Either.forLeft(true));
```

#### Why Kotlin is Better

1. **Visual hierarchy matches object hierarchy**: The nested `apply` for `CompletionOptions` is visually indented within the parent's configuration, making the relationship clear.
2. **No intermediate variables needed**: Java requires `completionOptions` as a separate variable, breaking the flow of configuration.
3. **Builder pattern without a builder**: Achieves the readability of the builder pattern without requiring special builder classes.

---

### Example: File Operations
**File:** `plugin/build.gradle.kts:34-42`

```kotlin
buildInfoFile.get().asFile.apply {
    parentFile.mkdirs()
    writeText("""
        # Auto-generated build information
        xdk.version=$xdkVersion
        jdk.version=$jdkVersion
        defaultJvmArgs=${jvmArgs.joinToString(",")}
        """.trimIndent())
}
```

#### Equivalent Java Code

```java
File file = buildInfoFile.get().getAsFile();
file.getParentFile().mkdirs();
file.writeText(
    "# Auto-generated build information\n" +
    "xdk.version=" + xdkVersion + "\n" +
    "jdk.version=" + jdkVersion + "\n" +
    "defaultJvmArgs=" + String.join(",", jvmArgs)
);
```

#### Why Kotlin is Better

1. **Scope clarity**: `apply` makes it clear all operations are on the same file object.
2. **No variable assignment needed**: The result isn't assigned because we don't need to reference the file again.
3. **Multi-line strings**: Kotlin's `"""...""".trimIndent()` is far cleaner than Java string concatenation.

---

## 2. `let` — Null-Safe Transformations

### Example: Conditional Parameter Addition
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt:54-57`

```kotlin
moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
methodName.takeIf { it.isNotBlank() }?.let { addParameter("--method=$it") }
moduleArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
workDirectory = project.basePath?.let { Path(it).toFile() }
```

#### Equivalent Java Code

```java
if (moduleName != null && !moduleName.isBlank()) {
    addParameter("--module=" + moduleName);
}
if (methodName != null && !methodName.isBlank()) {
    addParameter("--method=" + methodName);
}
if (moduleArguments != null && !moduleArguments.isBlank()) {
    addParameter("--args=" + moduleArguments);
}

String basePath = project.getBasePath();
workDirectory = basePath != null ? Path.of(basePath).toFile() : null;
```

#### Why Kotlin is Better

1. **One-liners for conditional operations**: Each Kotlin line is a complete, self-contained conditional action. Java requires 3 lines per check.
2. **Declarative intent**: `takeIf { ... }?.let { ... }` reads as "take this if condition, then do something." The Java version is imperative and verbose.
3. **Transformation chaining**: `project.basePath?.let { Path(it).toFile() }` chains null-check and transformation cleanly. Java requires a temporary variable or nested ternary.

---

### Example: Regex Match Processing
**File:** `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/MockXtcCompilerAdapter.kt` (pattern)

```kotlin
MODULE_PATTERN.find(content)?.let { match ->
    val line = countLines(content, match.range.first)
    val moduleName = match.groupValues[1]
    symbols.add(
        SymbolInfo(
            name = moduleName,
            qualifiedName = moduleName,
            kind = SymbolKind.MODULE,
            location = Location(uri, line, 0, line, match.value.length),
            documentation = "Module $moduleName"
        )
    )
}
```

#### Equivalent Java Code

```java
Matcher matcher = MODULE_PATTERN.matcher(content);
if (matcher.find()) {
    MatchResult match = matcher.toMatchResult();
    int line = countLines(content, match.start());
    String moduleName = match.group(1);
    symbols.add(new SymbolInfo(
        moduleName,
        moduleName,
        SymbolKind.MODULE,
        new Location(uri, line, 0, line, match.group().length()),
        "Module " + moduleName
    ));
}
```

#### Why Kotlin is Better

1. **Scoped binding**: The `match` variable only exists within the `let` block. In Java, `matcher` and potentially `match` pollute the outer scope.
2. **Null-safe by design**: `find()?.let` ensures the block only runs if there's a match. The Java pattern requires an explicit `if` check.
3. **Named parameter for clarity**: Using `match ->` makes it clear what we're working with inside the block.

---

## 3. `also` — Side Effects

### Example: Logging During Object Creation
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt:41-44`

```kotlin
override fun createConnectionProvider(project: Project) =
    XtcLspConnectionProvider().also {
        logger.info("Creating XTC LSP connection provider - $buildInfo")
    }
```

#### Equivalent Java Code

```java
@Override
public StreamConnectionProvider createConnectionProvider(Project project) {
    XtcLspConnectionProvider provider = new XtcLspConnectionProvider();
    logger.info("Creating XTC LSP connection provider - " + buildInfo);
    return provider;
}
```

#### Why Kotlin is Better

1. **Expression-oriented**: Kotlin returns the created object directly. Java needs to store, log, then return.
2. **Intent clarity**: `also` signals "do this side effect but keep the original value." In Java, you must trace the code to see what's returned.
3. **No temporary variable**: The Kotlin version doesn't need to name the provider.

---

### Example: Nested Setup with Side Effects
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt:80-85`

```kotlin
server = XtcLanguageServer(MockXtcCompilerAdapter()).also { srv ->
    LSPLauncher.createServerLauncher(srv, serverInput, serverOutput).also { launcher ->
        srv.connect(launcher.remoteProxy)
        serverFuture = launcher.startListening()
    }
}
```

#### Equivalent Java Code

```java
XtcLanguageServer srv = new XtcLanguageServer(new MockXtcCompilerAdapter());
Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(srv, serverInput, serverOutput);
srv.connect(launcher.getRemoteProxy());
serverFuture = launcher.startListening();
server = srv;
```

#### Why Kotlin is Better

1. **Clear ownership**: The `also` block shows that we're performing setup on `srv` before assigning to `server`. In Java, the assignment to `server` comes at the end, after several other operations.
2. **Nested scope**: The inner `also` for `launcher` makes it clear that launcher setup is part of server initialization.
3. **Atomic assignment intent**: The structure makes it clear: "create server, set it up completely, then assign to field."

---

## 4. `with` — Grouping Calls on an Object

### Example: UI Builder DSL
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizardStep.kt:36-43`

```kotlin
override fun setupUI(builder: Panel) {
    with(builder) {
        row("Project type:") {
            comboBox(XtcProjectCreator.ProjectType.entries.toList()).bindItem(projectTypeProperty)
        }
        row { checkBox("Multi-module project").bindSelected(multiModuleProperty) }
    }
}
```

#### Equivalent Java Code

```java
@Override
public void setupUI(Panel builder) {
    builder.row("Project type:", row -> {
        row.comboBox(List.of(XtcProjectCreator.ProjectType.values()))
           .bindItem(projectTypeProperty);
        return Unit.INSTANCE;
    });
    builder.row(row -> {
        row.checkBox("Multi-module project").bindSelected(multiModuleProperty);
        return Unit.INSTANCE;
    });
}
```

#### Why Kotlin is Better

1. **No receiver prefix**: Inside `with(builder)`, all calls like `row(...)` implicitly operate on `builder`. Java would require `builder.row(...)` for each call.
2. **DSL-friendly**: Kotlin's `with` enables clean DSL syntax. The UI structure is visually apparent from indentation.
3. **Cleaner lambdas**: Kotlin lambdas don't need explicit return statements for Unit-returning functions.

---

## 5. `run` — Scoped Computation with Early Return

### Example: Default Value with Logging
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizardStep.kt:46-49`

```kotlin
val base = baseData ?: run {
    logger.error("No base data available")
    return
}
```

#### Equivalent Java Code

```java
var base = baseData;
if (base == null) {
    logger.error("No base data available");
    return;
}
```

#### Why Kotlin is Better

1. **Smart cast after null check**: After the `?: run { return }`, Kotlin knows `base` is non-null. No explicit cast needed.
2. **Single expression**: The Kotlin version is a single val declaration. Java requires a mutable var plus a conditional block.
3. **Guard clause pattern**: The `?: run { ... return }` is idiomatic for guard clauses. It reads as "use baseData, or if null, log and return."

---

## 6. Combined Patterns — Real-World Complexity

### Example: Safe Resource Loading with Fallback
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt:29-39`

```kotlin
private val buildInfo: String by lazy {
    runCatching {
        Properties().apply {
            XtcLanguageServerFactory::class.java
                .getResourceAsStream("/lsp-version.properties")
                ?.use { load(it) }
        }.let { props ->
            "v${props.getProperty("lsp.version", "?")} built ${props.getProperty("lsp.build.time", "?")}"
        }
    }.getOrDefault("unknown")
}
```

#### Equivalent Java Code

```java
private final Supplier<String> buildInfo = Suppliers.memoize(() -> {
    try {
        Properties props = new Properties();
        InputStream stream = XtcLanguageServerFactory.class
            .getResourceAsStream("/lsp-version.properties");
        if (stream != null) {
            try (stream) {
                props.load(stream);
            }
        }
        String version = props.getProperty("lsp.version", "?");
        String buildTime = props.getProperty("lsp.build.time", "?");
        return "v" + version + " built " + buildTime;
    } catch (Exception e) {
        return "unknown";
    }
});
```

#### Why Kotlin is Better

1. **Composable operations**: `runCatching { ... }.getOrDefault(...)` cleanly handles exceptions with a fallback. Java requires try-catch blocks.
2. **Resource management**: `?.use { load(it) }` combines null-safety and auto-close. Java needs nested try-with-resources.
3. **Transformation pipeline**: `apply { ... }.let { ... }` creates a clear pipeline: create Properties → load → transform to String.
4. **Lazy delegation**: `by lazy` is built into the language. Java requires Guava's `Suppliers.memoize` or manual synchronization.

---

## 7. Server Shutdown — Safe Null Handling with Error Recovery
**File:** `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt:102-106`

```kotlin
server?.let { srv ->
    runCatching { srv.shutdown()?.get(2, TimeUnit.SECONDS) }
        .onFailure { logger.debug("Server shutdown: ${it.message}") }
    runCatching { srv.exit() }
}
```

#### Equivalent Java Code

```java
if (server != null) {
    try {
        CompletableFuture<?> shutdownFuture = server.shutdown();
        if (shutdownFuture != null) {
            shutdownFuture.get(2, TimeUnit.SECONDS);
        }
    } catch (Exception e) {
        logger.debug("Server shutdown: " + e.getMessage());
    }
    try {
        server.exit();
    } catch (Exception ignored) {
        // Ignore exit exceptions
    }
}
```

#### Why Kotlin is Better

1. **Null-safe scoping**: `server?.let { srv ->` ensures the entire block only runs if server is non-null, and provides a non-null reference inside.
2. **Exception handling as expressions**: `runCatching { ... }.onFailure { ... }` is more concise than try-catch and chains naturally.
3. **Chained null checks**: `srv.shutdown()?.get(...)` handles null CompletableFuture gracefully without nested ifs.
4. **Intent clarity**: The Kotlin code clearly shows two operations, each with its own error handling. The Java version's nested try-catch-if structure obscures this.

---

## Summary

Kotlin scope functions provide:

| Benefit | Explanation |
|---------|-------------|
| **Reduced ceremony** | No repeated variable references, no explicit returns for expressions |
| **Null safety** | `?.let`, `?.apply` handle optionals elegantly without if-null boilerplate |
| **Clear intent** | Each scope function signals what you're doing: configuring (`apply`), transforming (`let`), side-effecting (`also`) |
| **Scoped variables** | Variables declared in scope functions don't leak to outer scope |
| **Expression-oriented** | Everything returns a value, enabling functional composition |
| **DSL enablement** | `with` and `apply` enable clean domain-specific language syntax |

Java can achieve similar functionality, but requires more code, more variables, and more cognitive overhead to understand the same logic.
