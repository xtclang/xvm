# Configuration Reference

Every setting, property, environment variable and configurable path of the language tooling under
`lang/`, grouped by who sets the value: the editor user, the language server's launcher, the debug
adapter, the build, and the tests. Each entry names the code that reads it, as `path:line`, so that
the entry can be checked against the source. A value appears here only if code reads it; values
that other documents describe but no code reads are listed under
[Stale in other docs](#stale-in-other-docs).

The feature guides keep their own, partial lists: the [language server](../lsp-server/README.md),
the [IntelliJ plugin](../intellij-plugin/README.md) and its [testing reference](../intellij-plugin/testing.md),
the [VS Code extension](../vscode-extension/README.md) and the [manual playbook](manual-test-plan.md).

## Reading the locations

Paths are relative to the repository root. These prefixes shorten the long ones:

| Prefix | Directory |
|--------|-----------|
| `lsp/` | `lang/lsp-server/src/main/kotlin/org/xvm/lsp/` |
| `lspTest/` | `lang/lsp-server/src/test/kotlin/org/xvm/lsp/` |
| `dap/` | `lang/dap-server/src/main/kotlin/org/xvm/debug/` |
| `idea/` | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/` |
| `ideaIt/` | `lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/` |
| `vsc/` | `lang/vscode-extension/src/` |

`logback.xml` alone is the server's, `lang/lsp-server/src/main/resources/logback.xml`.

Three ways of reaching the server recur in the tables:

- **JVM option**: an argument on the server's `java` command line, usually `-D<name>=<value>`; read
  once at startup, so a change needs a server restart.
- **Initialization option**: a field of `initializationOptions` in the LSP `initialize` request;
  also fixed for the life of a connection.
- **`workspace/configuration`**: a section the server asks the editor for after initialization and
  again after `workspace/didChangeConfiguration`; these apply without a restart.

## 1. Editor settings

### 1.1 VS Code

The 19 settings in `contributes.configuration` of `lang/vscode-extension/package.json`, whose
entries start at lines 292 to 556. The three machine-scoped ones, `xtc.java.vmOptions`,
`xtc.server.compileLibraries` and `xtc.server.logs`, are read from the user settings only
(`inspect(...).globalValue`); workspace values of them are ignored.

| Setting | Type, default | What it does | Reaches the server as | Read at |
|---------|---------------|--------------|-----------------------|---------|
| `xtc.compiler.sourceModules` | array or null, `null` | The compiler's source graph: `null` discovers the workspace's modules, `[]` turns discovery off, an array lists modules as `{name, uri, dependencies, resourceRoots}`. Window scope; applies without a restart. | `xtcCompiler.sourceModules` initialization option and the `xtc.compiler` section; changes are pushed with `workspace/didChangeConfiguration` | `vsc/rename-proposal.ts:53`, `vsc/lsp-client.ts:83`, `vsc/lsp-client.ts:94` |
| `xtc.sourceRoots` | string array, `[]` | Extra directories of `.x` sources outside the workspace for the server to index. Machine-overridable; a change restarts the server. | `xtcSourceRoots` initialization option | `vsc/lsp-client.ts:165`, restart key `vsc/lsp-client.ts:37` |
| `xtc.compiler.libraries` | object, `{"modulePath": null, "sourceAttachments": []}` | Binary libraries (`modulePath`; `null` inherits the Gradle model) and read-only source attachments (`[{module, roots}]`). The bundled XDK is always included. Window scope; applies without a restart. | `xtcCompiler.libraries` initialization option and the `xtc.compiler` section | `vsc/library-settings.ts:6`, `vsc/lsp-client.ts:83` |
| `xtc.languageService.adapter` | `default`, `compiler` or `treesitter`; `default` | The server's backend. `default` keeps the one built into the server jar. A change restarts the server. | `-Dxtc.lsp.adapter=<value>`, left out for `default` | `vsc/editor-settings.ts:7`, `vsc/lsp-client.ts:133` |
| `xtc.build.mode` | `auto`, `embedded` or `gradle`; `embedded` | How Build, Run and Rebuild work: the server's embedded compiler, Gradle, or Gradle when the folder has a Gradle build. Resource scope. | Not sent; a debug launch in Gradle mode carries `buildMode: "gradle"` (section 3.1) | `vsc/embedded-execution.ts:30`, `vsc/build-mode-selection.ts:40` |
| `xtc.trace.server` | `off`, `messages` or `verbose`; `off` | Protocol tracing in the output channel. Read by `vscode-languageclient` for the client ID `xtc`. | `trace` in `initialize`, then `$/setTrace` | `vsc/lsp-client.ts:300`, `vsc/lsp-client.ts:400` |
| `xtc.codeLens.references` | boolean, `true` | Reference-count lenses on declarations. Resource scope; applies immediately. | `xtc.codeLens` section, field `references` | `vsc/lsp-client.ts:228` |
| `xtc.inlayHints.enabled` | boolean, `true` | Inlay hints. The client drops the request when off; the server is not told. | Not sent | `vsc/lsp-client.ts:204` |
| `xtc.languageService.textSynchronization` | `full` or `incremental`; `full` | Whether edits are sent as whole documents or as changes. A change restarts the server. | `xtcDocumentSync.incremental` initialization option | `vsc/editor-settings.ts:8`, `vsc/service-settings.ts:32` |
| `xtc.languageService.saveFormatting` | `editor` or `server`; `editor` | Who formats on save. Native `editor.formatOnSave` wins, so two formatters never run. A change restarts the server. | `xtcDocumentSync.formatOnSave` initialization option | `vsc/editor-settings.ts:9`, `vsc/service-settings.ts:33`, per-save check `vsc/editor-settings.ts:15` |
| `xtc.formatting.indentSize` | integer 1 to 32, `4` | Spaces per indentation level. | `xtc.formatting` section | `vsc/editor-settings.ts:29` |
| `xtc.formatting.continuationIndentSize` | integer 1 to 32, `8` | Spaces for continuation lines. | `xtc.formatting` section | `vsc/editor-settings.ts:29` |
| `xtc.formatting.tabSize` | integer 1 to 32, `4` | Deprecated; nothing reads it (`deprecationMessage` in `package.json`). Use `editor.tabSize`. | Not sent | none |
| `xtc.formatting.insertSpaces` | boolean, `true` | Spaces instead of tabs. | `xtc.formatting` section | `vsc/editor-settings.ts:27` |
| `xtc.formatting.maxLineWidth` | integer 1 to 1000, `120` | Target width for the compiler adapter's wrapping. | `xtc.formatting` section | `vsc/editor-settings.ts:29` |
| `xtc.java.home` | string, `""` | The Java 25+ installation that runs the server. Empty: `jdk-utils` discovery (`JAVA_HOME`, `PATH`, SDKMAN and others), then a JRE downloaded earlier, then a download from Adoptium. A path without `bin/java` is an error. A change restarts the server. | The `java` executable of the command line | `vsc/java.ts:54`, order `vsc/java.ts:22` |
| `xtc.java.vmOptions` | string array, `[]` | JVM options, one per item: `-Xms`, `-Xmx`, `-Xss`, `-XX:MaxMetaspaceSize=`, `-XX:ReservedCodeCacheSize=` (K, M or G), `-XX:ActiveProcessorCount=`, `-XX:+UseG1GC`, `UseSerialGC`, `UseParallelGC`, `UseZGC`. Anything else is rejected. Without a collector or `-Xmx` among them, the server runs `-XX:+UseZGC -XX:SoftMaxHeapSize=1G`: ZGC keeps the heap near 1 GB and grows past it only as far as the live data needs. Machine scope. | JVM options | `vsc/runtime-settings.ts:37`, validation `vsc/runtime-settings.ts:4`, default `vsc/runtime-settings.ts:31` |
| `xtc.server.compileLibraries` | boolean, `true` | Compile an opened XDK source with its whole library, so that navigation works for every name in it. Needs a server heap of about 1 GB. Machine scope. | `-Dxtc.lsp.compileLibraries=<value>` | `vsc/runtime-settings.ts:59` |
| `xtc.server.logs` | object, `{"historyDays": 7, "maxFileMb": 10, "totalSizeMb": 50, "retainedSessions": 5}` | Server log retention (section 4). Ranges: days 1 to 90, file MB 1 to 100, archive MB from the file MB to 1000, sessions 1 to 20. Machine scope. | `-Dxtc.logs.historyDays`, `-Dxtc.logs.maxFileMb`, `-Dxtc.logs.totalSizeMb`, `-Dxtc.logs.retainedSessions` | `vsc/runtime-settings.ts:52`, `vsc/runtime-settings.ts:55` |

`package.json` also sets editor defaults for `[xtc]` files in `configurationDefaults`, such as
`editor.tabSize` 4 and `editor.formatOnType` true; those are VS Code's own settings.

### 1.2 IntelliJ

The plugin registers five settings pages (`lang/intellij-plugin/src/main/resources/META-INF/plugin.xml:84`
to `:88`) and a code style. LSP4IJ's own Language Servers page holds the rest.

**Languages & Frameworks | Ecstasy Server Runtime and Logs** (`ServerRuntimeConfigurable`). Stored per
machine in `ecstasy-server-runtime.xml`, excluded from Settings Sync
(`idea/lsp/ServerRuntimeSettings.kt:13`). Apply saves for the next start; the page's button restarts
running servers. The server runs on IntelliJ's bundled Java runtime.

| Field | Type, default | Reaches the server as | Read at |
|-------|---------------|-----------------------|---------|
| JVM options (`vmOptions`) | text, one option per line, `""`; the same options and the same default as VS Code's `xtc.java.vmOptions` | JVM options | `idea/lsp/ServerRuntimeSettings.kt:21`, validation `idea/lsp/ServerJvmOptions.kt:17`, default `idea/lsp/ServerJvmOptions.kt:14` |
| Log history (days) (`logHistoryDays`) | 1 to 90, `7` | `-Dxtc.logs.historyDays` | `idea/lsp/ServerRuntimeSettings.kt:22`, `:65` |
| Each log file (MB) (`logMaxFileMb`) | 1 to 100, `10` | `-Dxtc.logs.maxFileMb` | `idea/lsp/ServerRuntimeSettings.kt:23`, `:66` |
| Rolled archive cap per stream (MB) (`logTotalSizeMb`) | file MB to 1000, `50` | `-Dxtc.logs.totalSizeMb` | `idea/lsp/ServerRuntimeSettings.kt:24`, `:67` |
| Retired process sessions to keep (`logRetainedSessions`) | 1 to 20, `5` | `-Dxtc.logs.retainedSessions` | `idea/lsp/ServerRuntimeSettings.kt:25`, `:68` |
| Compile an XDK library as a whole when one of its sources is opened (`compileLibraries`) | boolean, `true` | `-Dxtc.lsp.compileLibraries` | `idea/lsp/ServerRuntimeSettings.kt:26`, `:58` |

The arguments are inserted before `-jar` at `idea/lsp/XtcLspServerSupportProvider.kt:160`.

**Languages & Frameworks | Ecstasy Language Service Defaults** (application) and **Ecstasy Language
Service** (project) (`LanguageServiceConfigurable`). Stored as JSON under `xtc.languageService` in
LSP4IJ's settings for the server `xtcLanguageServer`, global or per project
(`idea/lsp/LanguageServiceSettings.kt:12`); a project value overrides the application default key by key
(`idea/lsp/LanguageServiceConfiguration.kt:77`).

| Field | Type, default | Reaches the server as | Read at |
|-------|---------------|-----------------------|---------|
| Use application language-service defaults | boolean, on for a new project; project page only | Removes the project's `xtc.languageService` | `idea/lsp/LanguageServiceConfigurable.kt:34`, `:199` |
| Language adapter (`adapter`) | `default`, `compiler` or `treesitter`; `default` | `-Dxtc.lsp.adapter=<value>`, left out for `default`; a change restarts the server | `idea/lsp/LanguageAdapter.kt:13`, `idea/lsp/XtcLspServerSupportProvider.kt:161`, restart `idea/lsp/XtcLanguageClient.kt:74` |
| Text synchronization (`textSynchronization`) | `full` or `incremental`; `full` | `xtcDocumentSync.incremental` initialization option; a change restarts the server | `idea/lsp/LanguageServiceConfiguration.kt:32` |
| Save formatting owner (`saveFormatting`) | `editor` or `server`; `editor` | Disabled: LSP4IJ has no `willSaveWaitUntil`, so the plugin always sends `formatOnSave: false` | `idea/lsp/LanguageServiceConfigurable.kt:164`, `idea/lsp/XtcLspServerSupportProvider.kt:195` |
| Show Ecstasy inlay hints (`inlayHints`) | boolean, `true` | Not sent; the client skips the request | `idea/lsp/XtcClientFeatures.kt:131` |
| Show Ecstasy reference counts (`referenceCodeLens`) | boolean, `true` | `xtc.codeLens` section, field `references` | `idea/lsp/XtcLanguageClient.kt:384` |

**Languages & Frameworks | Ecstasy Compiler** (`CompilerProjectConfigurable`). Stored as JSON under
`xtc.compiler` in LSP4IJ's project settings for `xtcLanguageServer`
(`idea/lsp/CompilerProjectConfigurable.kt:503`). The plugin answers the `xtc.compiler` section from
this JSON and adds `buildModels` from the Gradle model file (section 4)
(`idea/lsp/XtcLanguageClient.kt:221`, `idea/lsp/CompilerBuildModel.kt:113`).

| Field | Type, default | Reaches the server as | Read at |
|-------|---------------|-----------------------|---------|
| Use Gradle model or automatic source discovery | boolean, on | `sourceModules: null` | `idea/lsp/CompilerProjectConfigurable.kt:52` |
| Source modules table (module, root URI or relative path, dependencies, resource roots) | rows, none | `sourceModules: [{name, uri, dependencies, resourceRoots}]` | `idea/lsp/CompilerProjectConfigurable.kt:59`, `idea/lsp/SourceGraphConfiguration.kt:57` |
| Inherit binary libraries from Gradle | boolean, on | `libraries.modulePath: null` | `idea/lsp/CompilerLibrariesPanel.kt:24`, `:94` |
| Binary libraries | ordered paths, none | `libraries.modulePath` | `idea/lsp/CompilerLibrariesPanel.kt:94` |
| Source attachments (module, roots) | rows, none | `libraries.sourceAttachments: [{module, roots}]` | `idea/lsp/CompilerLibrariesPanel.kt:95` |

**Build, Execution, Deployment | Ecstasy** (`XtcBuildConfigurable`). Stored in the project's
`ecstasy-build.xml` (`idea/run/XtcBuildSettings.kt:29`).

| Field | Type, default | Reaches the server as | Read at |
|-------|---------------|-----------------------|---------|
| Build and run using | Embedded compiler, Automatic (Gradle when the project has a Gradle build), Gradle; Embedded | Not sent; a debug launch in Gradle or command-line mode carries `buildMode` (section 3.1) | `idea/run/XtcBuildSettings.kt:39`, `:51` |

**Editor | Code Style | Ecstasy**. IntelliJ's code style, answered as the `xtc.formatting` section
(`idea/lsp/XtcLanguageClient.kt:388`); defaults from
`idea/style/XtcLanguageCodeStyleSettingsProvider.kt:38`.

| Field | Default | Reaches the server as |
|-------|---------|-----------------------|
| Indent | 4 | `indentSize` |
| Continuation indent | 8 | `continuationIndentSize` |
| Tab size | 4 | `tabSize`, which the server does not read |
| Use tab character | off | `insertSpaces` (inverted) |
| Hard wrap at (right margin) | 120 | `maxLineWidth` |

**LSP4IJ's Language Servers page** keeps the trace level and the debug port for `xtcLanguageServer`.
They belong to LSP4IJ: the plugin builds the command with LSP4IJ's `JavaProcessCommandBuilder`, which
adds the debug options (`idea/lsp/XtcLspServerSupportProvider.kt:151`).

### 1.3 Values the editors pass through from their own process

| Name | Set by | What happens | Read at |
|------|--------|--------------|---------|
| `XTC_LOG_LEVEL` | Environment of the VS Code extension host | Passed as `-Dxtc.logLevel=<value>`, `INFO` when unset | `vsc/lsp-client.ts:132`, `vsc/java.ts:292` |
| `xtc.logLevel`, else `XTC_LOG_LEVEL` | IntelliJ's JVM or environment; `runIde` sets both from `-Plog` | Passed as `-Dxtc.logLevel=<value>`, `INFO` when unset | `idea/lsp/XtcLspServerSupportProvider.kt:141` |
| `xtc.lsp.semanticTokens`, else `XTC_LSP_SEMANTIC_TOKENS` | IntelliJ's JVM or environment; `runIde` sets both from `-Pxtc.intellij.semanticTokens` | Always passed as `-Dxtc.lsp.semanticTokens=<value>`, `true` when unset, so the jar's built-in default never applies under IntelliJ | `idea/lsp/XtcLspServerSupportProvider.kt:145` |
| `xtc.trace.directory`, `xtc.trace.level`, `xtc.logs.directory`, `xtc.lsp.test.holdReferences` | IntelliJ's JVM | Each one that is set is passed on as the same `-D` option | `idea/lsp/XtcLspServerSupportProvider.kt:170` |

VS Code starts the server without its own environment map, so the server inherits the extension
host's environment, including `XTC_SOURCE_ROOTS`, `XTC_LSP_LOG_DIR` and the other variables of
section 2.3.

## 2. Language server

### 2.1 Command line

The server is `java [JVM options] -jar xtc-lsp-server.jar` and speaks LSP over stdin and stdout
only (`lsp/server/XtcLanguageServerLauncher.kt:193`). It takes no program arguments: `main` ignores
them (`lsp/server/XtcLanguageServerLauncher.kt:143`). The main class is
`org.xvm.lsp.server.XtcLanguageServerLauncherKt` (`lang/lsp-server/build.gradle.kts:458`). The
adapter is chosen before anything else (`lsp/server/XtcLanguageServerLauncher.kt:151`), and old log
sessions are pruned at startup (`:161`).

| Option | Added by | Read at |
|--------|----------|---------|
| `--enable-native-access=ALL-UNNAMED`, `-Dapple.awt.UIElement=true`, `-Djava.awt.headless=true` | VS Code | `vsc/java.ts:289` |
| `-Dapple.awt.UIElement=true`, `-Djava.awt.headless=true` | IntelliJ | `idea/lsp/XtcLspServerSupportProvider.kt:163` |
| `-agentlib:jdwp=...,address=5005` | VS Code, when the extension itself runs in debug mode | `vsc/lsp-client.ts:153` |
| Debug options | IntelliJ, from LSP4IJ's debug port setting | `idea/lsp/XtcLspServerSupportProvider.kt:151` |
| Working directory | VS Code: the extension's directory; IntelliJ: the project's base path | `vsc/lsp-client.ts:148`, `idea/lsp/XtcLspServerSupportProvider.kt:180` |

### 2.2 JVM system properties

| Property | Type, default | What it does | Read at |
|----------|---------------|--------------|---------|
| `xtc.lsp.adapter` | string, the jar's `lsp.adapter` (`compiler`) | The backend: `compiler` (also `xtc`, `full`), `treesitter` (also `tree-sitter`) or `mock`. Another value fails the start. Tree-sitter falls back to mock when its native library does not load, and the editor is told. | `lsp/server/XtcLanguageServerLauncher.kt:151`, values `:74` |
| `xtc.lsp.semanticTokens` | boolean, else `XTC_LSP_SEMANTIC_TOKENS`, else the jar's `lsp.semanticTokens` (`true`) | Whether the server offers semantic tokens. | `lsp/server/XtcLanguageServer.kt:401` |
| `xtc.lsp.compileLibraries` | boolean, `true` | Compile an opened XDK source's read-only copy with its whole library (section 4). Only the exact value `false` turns it off. It also needs a maximum heap of 1 GB less 64 MB; with less, each XDK source compiles alone and a warning is logged once. | `lsp/adapter/xdk/XdkLibrarySources.kt:73`, heap `:80`, `lsp/adapter/xdk/XdkAdapter.kt:697` |
| `xtc.sourceRoots` | path list split on the platform's path separator, none | Extra source roots, merged with `xtcSourceRoots` and `XTC_SOURCE_ROOTS`. | `lsp/server/SourceRootResolver.kt:42` |
| `xtc.logLevel` | `TRACE`, `DEBUG`, `INFO`, `WARN` or `ERROR`; else `XTC_LOG_LEVEL`, else `INFO` | Level of the `org.xvm.lsp` loggers. Other loggers stay at `WARN`, LSP4J's at `ERROR`. | `lang/lsp-server/src/main/resources/logback.xml:43` |
| `xtc.logs.directory` | path, else `XTC_LSP_LOG_DIR`, else `~/.xtc/logs/lsp` | Root of the log sessions (section 4). | `lsp/util/ServerLogs.kt:197` |
| `xtc.logs.historyDays` | integer 1 to 90, `7` | Days of rolled logs kept, and the age after which closed sessions are pruned. | `lsp/util/ServerLogs.kt:46`, `logback.xml:30` |
| `xtc.logs.maxFileMb` | integer 1 to 100, `10` | Size at which `server.log` and the trace file roll. | `lsp/util/ServerLogs.kt:46`, `logback.xml:29` |
| `xtc.logs.totalSizeMb` | integer, from `maxFileMb` to 1000, `50` | Cap on each stream's rolled archives. | `lsp/util/ServerLogs.kt:46`, `logback.xml:32` |
| `xtc.logs.retainedSessions` | integer 1 to 20, `5` | Closed sessions kept by pruning. | `lsp/util/ServerLogs.kt:46` |
| `xtc.trace.directory` | path, else `XTC_LSP_TRACE_DIR`, else the session directory | Where the execution trace (`lsp-trace-*.jsonl`) is written. | `lsp/util/ServerLogs.kt:206`, `logback.xml:48` |
| `xtc.trace.level` | logback level, else `XTC_LSP_TRACE_LEVEL`, else `INFO` | Level of the execution trace; `OFF` turns it off. | `logback.xml:62` |
| `xtc.lsp.test.holdReferences` | file name, unset | Test seam: reference searches in the file of that name wait until canceled. The IntelliJ playbook's X145 sets it. | `lsp/server/XtcTextDocumentService.kt:172`, `:1193` |

Non-integer `xtc.logs.*` values fall back to the default (`lsp/util/ServerLogs.kt:45`); values out of
range are rejected (`lsp/util/ServerLogs.kt:35`). Both editors check the same ranges before they start
the server. The JDK's `java.io.tmpdir` and `user.home` place the files of section 4.

### 2.3 Environment variables

| Variable | Default | What it does | Read at |
|----------|---------|--------------|---------|
| `XTC_LSP_SEMANTIC_TOKENS` | unset | As `xtc.lsp.semanticTokens`, which wins over it. | `lsp/server/XtcLanguageServer.kt:404` |
| `XTC_SOURCE_ROOTS` | unset | As `xtc.sourceRoots`; all three sources are merged. | `lsp/server/SourceRootResolver.kt:43` |
| `XTC_LOG_LEVEL` | unset | As `xtc.logLevel`, which wins over it. | `logback.xml:43` |
| `XTC_LSP_LOG_DIR` | unset | As `xtc.logs.directory`, which wins over it. | `lsp/util/ServerLogs.kt:199` |
| `XTC_LSP_TRACE_DIR` | unset | As `xtc.trace.directory`, which wins over it. | `lsp/util/ServerLogs.kt:206`, `logback.xml:48` |
| `XTC_LSP_TRACE_LEVEL` | unset | As `xtc.trace.level`, which wins over it. | `logback.xml:62` |

### 2.4 Defaults built into the jar

The build writes `lsp-version.properties` into the jar (`lang/lsp-server/build.gradle.kts:96`); section 5
says how to set its inputs.

| Key | Default | Used for | Read at |
|-----|---------|----------|---------|
| `lsp.adapter` | `compiler` | The backend when `-Dxtc.lsp.adapter` is absent | `lsp/server/XtcLanguageServerLauncher.kt:151` |
| `lsp.semanticTokens` | `true` | Semantic tokens when neither override is set | `lsp/server/XtcLanguageServer.kt:405` |
| `lsp.version` | the project version | The startup banner and the service status | `lsp/server/XtcLanguageServer.kt:399` |
| `lsp.build.time` | the build's time, or `sourceDateEpoch` | The startup banner | `lsp/server/XtcLanguageServer.kt:400` |

### 2.5 Initialization options

| Field | Type, default | What it does | Sent by | Read at |
|-------|---------------|--------------|---------|---------|
| `xtcDocumentSync.incremental` | boolean, `false` | Incremental rather than full document synchronization. A non-boolean fails `initialize`. | Both editors | `lsp/server/DocumentSynchronization.kt:45` |
| `xtcDocumentSync.formatOnSave` | boolean, `false` | The server formats on save. | VS Code; IntelliJ always sends `false` | `lsp/server/DocumentSynchronization.kt:46` |
| `xtcSourceRoots` | string array, `[]` | Extra source roots (section 2.2). | VS Code | `lsp/server/SourceRootResolver.kt:45` |
| `xtcCompiler` | object, absent | The first compiler configuration, the shape of the `xtc.compiler` section (2.6). Invalid content fails `initialize` with `InvalidParams`. | VS Code | `lsp/server/CompilerConfiguration.kt:17`, `lsp/server/XtcLanguageServer.kt:484` |
| `xtcCodeActions.quickFixesPerDiagnostic` | boolean, `false` | Quick fixes are asked for per diagnostic. | IntelliJ (`true`) | `lsp/server/ClientPresentation.kt:169`, sent at `idea/lsp/XtcLspServerSupportProvider.kt:117` |
| `xtcCodeActions.automaticFromCache` | boolean, `false` | Automatic code action requests are answered from what is proved already. | Neither | `lsp/server/ClientPresentation.kt:170` |
| `xtcCodeActions.automaticWaitMillis` | integer, 500 | How long an Automatic code action request waits for its full answer. | Neither | `lsp/server/ClientPresentation.kt:171`, default `:95` |
| `trace` (LSP's own) | `off`, `messages` or `verbose` | The server's protocol trace; `$/setTrace` changes it later. | Both | `lsp/server/XtcLanguageServer.kt:472`, `:168` |

### 2.6 `workspace/configuration` sections

The server asks only when the client declares `workspace.configuration`.

| Section and field | Type, default | What it does | Requested at, parsed at |
|-------------------|---------------|--------------|-------------------------|
| `xtc.compiler` `sourceModules` | null or array, `null` | `null` discovers modules (from `buildModels` when present), `[]` none; each entry is `{name, uri, dependencies, resourceRoots}`. Relative URIs need exactly one workspace folder. | `lsp/server/XtcLanguageServer.kt:752`, `lsp/server/CompilerConfiguration.kt:55` |
| `xtc.compiler` `buildModels` | array, absent | Gradle source sets from `.gradle/xtc/lsp-model.json`; used when `sourceModules` is `null`. | `lsp/server/CompilerConfiguration.kt:36` |
| `xtc.compiler` `libraries.modulePath` | null or path array, `null` | Binary libraries; `null` inherits the build model's. | `lsp/server/CompilerLibraries.kt:109` |
| `xtc.compiler` `libraries.sourceAttachments` | array, `[]` | `[{module, roots}]`: read-only sources for binary modules. | `lsp/server/CompilerLibraries.kt:111` |
| `xtc.formatting` `indentSize` | integer 1 to 32, `4` | Formatter indent. | `lsp/server/XtcLanguageServer.kt:849`, `lsp/server/EditorFormattingState.kt:72` |
| `xtc.formatting` `continuationIndentSize` | integer 1 to 32, `8` | Continuation indent. | `lsp/server/EditorFormattingState.kt:73` |
| `xtc.formatting` `insertSpaces` | boolean, `true` | Spaces or tabs. | `lsp/server/EditorFormattingState.kt:65` |
| `xtc.formatting` `maxLineWidth` | integer 1 to 1000, `120` | Wrapping width. | `lsp/server/EditorFormattingState.kt:75` |
| `xtc.codeLens` `references` | boolean, `true` | Reference-count lenses, asked per document (`scopeUri`); after 10 seconds without an answer the lenses are off. | `lsp/server/XtcLanguageServer.kt:427`, `lsp/server/CodeLensSettings.kt:11` |

On `workspace/didChangeConfiguration` the server asks for `xtc.formatting` again, refreshes inlay
hints and lenses, and takes `settings.xtc.compiler` as given; when the notification has only
`formatting`, `presentation` or `languageService` under `xtc`, the compiler configuration stays, and
otherwise the server asks for `xtc.compiler` (`lsp/server/XtcWorkspaceService.kt:51`,
`lsp/server/CompilerConfiguration.kt:22`, `lsp/server/XtcLanguageServer.kt:725`). An invalid
compiler configuration after initialization keeps the previous one and shows an error.

## 3. Debug adapter and run workers

The debug adapter (`lang/dap-server`) is a library of the language server: each debug session runs
in the server's process (`lang/dap-server/build.gradle.kts:15`). Programs run in a separate worker
JVM; tests run as an `xtc test` process.

### 3.1 Launch and attach arguments

VS Code declares these in `contributes.debuggers` of `package.json`; IntelliJ's run configurations
send the same request.

| Argument | Request | Type, default | What it does | Read at |
|----------|---------|---------------|--------------|---------|
| `module` | launch | string | The module to run; required unless `moduleUri` is given. | `dap/XtcDebugServer.kt:1516` |
| `moduleUri` | launch | URI | The module's source; wins over `module`. | `dap/XtcDebugServer.kt:1517` |
| `method` | launch | string, the module's `run` | Method to invoke. | `dap/XtcDebugServer.kt:1529` |
| `args` | launch | array, or comma-separated string; none | Program arguments. | `dap/XtcDebugServer.kt:1520` |
| `workingDirectory`, else `cwd` | launch | path; the build's workspace folder | Working directory. VS Code's default is `${workspaceFolder}`. | `dap/XtcDebugServer.kt:1531` |
| `tests` | launch | array of `{selector, kind}`; absent | Run tests instead of `run`; `kind` is `module`, `package`, `class` or `test`. | `dap/XtcDebugServer.kt:1532`, `:1544` |
| `stopOnEntry` | launch, attach | boolean, `false` | Stop at the program's first line. | `dap/XtcDebugServer.kt:630`, `:703` |
| `noDebug` | launch | boolean, `false` | Run without the debugger; not allowed with `buildMode`. | `dap/XtcDebugServer.kt:621`, `:627` |
| `evaluationRunsProgram` | launch | boolean, `false` | The rest of the program runs while console code runs. | `dap/XtcDebugServer.kt:628` |
| `buildMode` | launch | `gradle` or `commandLine`; absent | The editor runs the program (`runXtc`, or `xtc run`) with `--debug connect:`; absent, the server builds and runs it. | `dap/XtcDebugServer.kt:620`, `dap/DebugRuns.kt:61` |
| `port` | attach | 1 to 65535; VS Code default 5005 | The port of the program's `--debug` option; required. | `dap/XtcDebugServer.kt:1503` |
| `host` | attach | string, `localhost` | Where the program listens. | `dap/XtcDebugServer.kt:1509` |
| `token` | attach | string, none | The token of the program's `--debug` option. | `dap/XtcDebugServer.kt:1511` |

### 3.2 Fixed limits

These are constants in the code; no setting changes them.

| Limit | Value | Read at |
|-------|-------|---------|
| Programs running at once in the worker | 4 | `lsp/run/RunWorkerProcess.kt:72` |
| Worker startup | 60 seconds | `lsp/run/RunWorkerProcess.kt:73` |
| Worker stop | 10 seconds | `lsp/run/RunWorkerProcess.kt:74` |
| Idle worker kept | 10 minutes | `lsp/run/RunWorkerProcess.kt:75` |
| Heap in use after a run, above which the worker serves no further run | 512 MB | `lsp/run/RunWorkerProcess.kt:76` |
| Threads after a run, above which the worker serves no further run | 200 | `lsp/run/RunWorkerProcess.kt:77` |
| Lazy-value computation in a debugged program | the debugger's own `ON_DEMAND` | `lsp/run/RunWorkerProcess.kt:78` |
| Stop of a run inside the worker | 5 seconds | `lsp/run/RunWorker.kt:57` |
| Program output per event, and held while waiting | 16,384 characters, sent after 20 ms | `lsp/run/RunWorker.kt:421`, `:424` |
| Debug console code on a stopped fiber | 2 seconds | `dap/XtcDebugServer.kt:308` |
| Connecting to an attached program | 10 seconds | `lsp/run/AttachedProgram.kt:128` |

The server's own instance uses the defaults (`lsp/server/XtcLanguageServer.kt:142`).

### 3.3 Child processes

| Process | Command | Read at |
|---------|---------|---------|
| Run worker | The server's own `java` executable, `-Dfile.encoding=UTF-8 -cp <server class path> org.xvm.lsp.run.RunWorker`; no JVM options of its own | `lsp/run/RunWorkerProcess.kt:524` |
| Test run | `xtc test` through the server's class path: `-L` the bundled libraries' directory (section 4) and the build's module path, `--no-recompile`, `-x <workspace>/build/xtc/embedded/xunit`, `-I xvm.xunit.report=<json>`, then `-p`, `-c` or `-t` per selected test, and `--debug` when debugged; run in the workspace folder | `lsp/run/TestProcess.kt:57`, `lsp/server/EmbeddedRuns.kt:491`, `:998` |

## 4. Files and directories

| What | Default location | Lifetime | What moves it | Code |
|------|------------------|----------|---------------|------|
| Log root | `~/.xtc/logs/lsp` | Kept | `-Dxtc.logs.directory`, `XTC_LSP_LOG_DIR` | `lsp/util/ServerLogs.kt:195` |
| Session directory | `<log root>/server-<pid>-<start millis>/` | Pruned at a later server's start, when closed and beyond `retainedSessions` or older than `historyDays`; a directory with unknown files or symbolic links is kept | The log root | `lsp/util/ServerLogs.kt:201`, `:228`, `lsp/util/TraceProcessId.kt` |
| Server log | `<session>/server.log`, rolled to `server.<date>.<n>.log` | Rolled by `xtc.logs.*` | The log root | `lang/lsp-server/src/main/resources/logback.xml:26` |
| Execution trace | `<session>/lsp-trace-<pid>-<start>.jsonl`, rolled the same way | As the log | `xtc.trace.directory`, `XTC_LSP_TRACE_DIR` | `logback.xml:50`, `lsp/util/ServerLogs.kt:206` |
| Session lock | `<session>/server.lock` | OS lock held while the server runs; pruning keeps a locked session | The log root | `lsp/util/ServerLogs.kt:204` |
| Pruning lock | `<log root>/.retention.lock` | Kept | The log root | `lsp/util/ServerLogs.kt:235` |
| Library source copies | `<java.io.tmpdir>/xtc-library-sources-<random>/`: read-only copies of the bundled XDK sources that navigation opens | Deleted when the JVM exits; a killed server's directory is deleted by the next server | `java.io.tmpdir` | `lsp/adapter/xdk/XdkLibrarySources.kt:58`, `lsp/util/ProcessDirectories.kt:17` |
| Library root | `<copies>/<module>.x`, with every source of that library and the resources they name (`implicit.x`, empty files for others) written beside it, when `xtc.lsp.compileLibraries` is on | As the copies | As the copies | `lsp/adapter/xdk/XdkLibrarySources.kt:306` |
| Attached source snapshots | `<java.io.tmpdir>/xtc-attached-sources-<random>/` | As the copies | `java.io.tmpdir` | `lsp/adapter/xdk/XdkAttachedSources.kt:21` |
| Locks of the two above | `<directory>.lock` beside each; `.xtc-library-sources-lock` and `.xtc-attached-sources-lock` in the temporary directory | As the copies | `java.io.tmpdir` | `lsp/util/ProcessDirectories.kt:19`, `lsp/util/ServerLogs.kt:69` |
| Bundled libraries | `<java.io.tmpdir>/xtc-lsp-xdk-<16 hex digits of the bundle's fingerprint>/`, complete when `.complete` is there; the `-L` of test runs | Shared by all servers of one build and kept; a server deletes other builds' directories that no process holds | `java.io.tmpdir` | `lsp/adapter/xdk/XdkLibraries.kt:87`, `:117` |
| Its locks | `xtc-lsp-xdk-<hash>.lock` (shared lock) and `.xtc-lsp-xdk-lock` in the temporary directory | Kept | `java.io.tmpdir` | `lsp/adapter/xdk/XdkLibraries.kt:90`, `:99` |
| Tree-sitter native libraries | `<java.io.tmpdir>/libtree-sitter<random>.<ext>` and `tree-sitter-xtc<random>.<ext>` | Deleted when the JVM exits | `java.io.tmpdir` | `lsp/treesitter/TreeSitterLibraryLookup.kt:30`, `lsp/treesitter/XtcParser.kt:149` |
| Embedded build output | `<workspace folder>/build/xtc/embedded/lib/<module>.xtc`, `dependencies/` and `manifest.json` | Rewritten by each build; a module that failed has no file | Nothing; the folder that owns the module | `lsp/adapter/xdk/XdkBuild.kt:75` |
| Embedded test results | `<workspace folder>/build/xtc/embedded/xunit` | Rewritten by each test run | Nothing | `lsp/server/EmbeddedRuns.kt:998` |
| Gradle model | `<Gradle root>/.gradle/xtc/lsp-model.json` and `lsp-workspace.json`, written by the init script `lang/gradle/compiler-model.init.gradle` when an editor imports | Rewritten by each import | Nothing | `lang/gradle/compiler-model.init.gradle:68`, read at `vsc/build-model.ts:21`, `idea/lsp/CompilerBuildModel.kt:26`, `idea/lsp/CompilerWorkspaceModels.kt:15` |
| Editor control | `~/.xtc/editor-control/<id>.json`, owner-only, one per editor session, for the `xtc-lsp` restart command | Removed when the session ends | Nothing for the editors; `XTC_EDITOR_CONTROL_DIR` for `xtc-lsp` | `idea/lsp/ServerControlStartup.kt:18`, `idea/lsp/ServerControlEndpoint.kt:34`, `vsc/extension.ts:138`, `lang/scripts/xtc-lsp:87` |
| Downloaded Java runtime | `<VS Code global storage>/jre` | Kept | Nothing; `xtc.java.home` makes it unused | `vsc/java.ts:68` |
| IntelliJ settings | `ecstasy-server-runtime.xml` (application, not synced), `ecstasy-build.xml` (project), LSP4IJ's server settings (`xtc.languageService`, `xtc.compiler`), and the workspace file for the last launch's support log | Kept | IntelliJ's configuration directories | `idea/lsp/ServerRuntimeSettings.kt:13`, `idea/run/XtcBuildSettings.kt:29`, `idea/lsp/ServerSupportLogs.kt:22` |
| Log export | `ecstasy-server-<pid>-<start>.zip`, at most 512 KB per file and 4 MB in all; VS Code proposes `~/ecstasy-server-logs.zip` | Written on request | The save dialog | `lsp/util/ServerLogs.kt:210`, `:377`, `vsc/extension.ts:179` |

`lang/scripts/xtc-lsp`, shipped in both plugins' `bin` directories, restarts a server through its
editor: `xtc-lsp list`, and `xtc-lsp restart` with `--project` (default: the current directory),
`--ide intellij` or `vscode`, `--session <id>` and `--timeout <seconds>` (default 65, at most 600)
(`lang/scripts/xtc-lsp:79`).

The editors' JVM option settings accept no `-D` options (`vsc/runtime-settings.ts:6`,
`idea/lsp/ServerJvmOptions.kt:5`), so neither editor setting moves `java.io.tmpdir` or `user.home`.
`lang/dap-server/src/main/resources/logback.xml` names `~/.xtc/logs/dap-server.log`, but the server jar
keeps the first `logback.xml` it is given, the server's own (`lang/lsp-server/build.gradle.kts:428`,
`:433`): debug sessions log to the server log, and the `org.xvm.debug` loggers stay at `WARN`.

## 5. Build properties

Most `lang/` properties go through `xdkProperties`, which takes the first of: the environment
variable of the key in upper snake case (`lsp.adapter` is `LSP_ADAPTER`), the Gradle property, the
Gradle property with dots as underscores, the JVM system property, and the composite root's
`gradle.properties`, `xdk.properties` and `version.properties`
(`build-logic/common-plugins/src/main/kotlin/ProjectXdkProperties.kt:14`). The rows below marked
`providers` read only the Gradle property (`-P`, `gradle.properties` or
`ORG_GRADLE_PROJECT_<name>`).

### 5.1 Composite and lang build

| Property | Default | What it does | Read at |
|----------|---------|--------------|---------|
| `includeBuildLang` | `true` when unset; this branch's `gradle.properties` sets `true`, master's `false` | Include `lang` as a composite build (`providers`). | `settings.gradle.kts:91`, `gradle.properties:114` |
| `includeBuildAttachLang` | `true` when unset; this branch's `gradle.properties` sets `true`, master's `false` | Attach lang's `build`, `check`, `assemble` and `clean` to the root's (`providers`). Any included build has such a flag, `includeBuildAttach<Name>`. | `build-logic/aggregator/src/main/kotlin/org.xtclang.build.aggregator.gradle.kts:28`, `gradle.properties:115` |
| `includeBuildAttachIntellijPlugin` | `false` | Attach the IntelliJ plugin to lang's lifecycle (`build` runs `buildPlugin`) (`providers`). | `lang/build.gradle.kts:134` |
| `includeBuildAttachVsCodeExtension` | `false` | Attach the VS Code extension to lang's lifecycle (`providers`). | `lang/build.gradle.kts:140` |
| `idePluginsDir` | unset | `assembleIdePlugins` also copies both plugin packages there; relative to the directory `gradlew` runs in (`providers`). | `lang/build.gradle.kts:214` |
| `CI` (environment) | unset | When set, `check` runs `spotlessCheck` instead of `spotlessApply`, and the IntelliJ build keeps its IDE downloads in the checkout. | `lang/build.gradle.kts:31`, `lang/intellij-plugin/build.gradle.kts:495` |
| `org.xtclang.java.jdk` | `25` (`version.properties`) | The JDK toolchain of every lang project. | `build-logic/common-plugins/src/main/kotlin/org.xtclang.build.xdk.properties.gradle.kts:53`, `version.properties:26` |
| `XTC_COMPILER_IMPORT_ID` (environment) | `""` | Set by the editors when they import a Gradle model; the init script records it in the model. | `lang/gradle/compiler-model.init.gradle:67`, set at `vsc/compiler-paths.ts:71`, `idea/lsp/CompilerBuildModel.kt:259` |

### 5.2 Language server

| Property | Default | What it does | Read at |
|----------|---------|--------------|---------|
| `lsp.adapter` | `compiler` | The jar's default backend (`treesitter`, `compiler` or `mock`); with `compiler`, `xtc` or `full`, `check` also runs `compilerStdioTest`. | `lang/lsp-server/build.gradle.kts:59`, `:486` |
| `lsp.semanticTokens` | `true` | The jar's default for semantic tokens. | `lang/lsp-server/build.gradle.kts:60` |
| `log` | `XTC_LOG_LEVEL`, else `INFO` | Log level of the server tests (`xtc.logLevel`, `XTC_LOG_LEVEL`). | `lang/lsp-server/build.gradle.kts:65`, `:388` |
| `sourceDateEpoch`, else `SOURCE_DATE_EPOCH` | the build's time | The jar's `lsp.build.time`, for reproducible builds (`providers`). | `lang/lsp-server/build.gradle.kts:80` |
| `lspTests` | `default` | Which tests run: `default` leaves out the `slow` and `heavy` tags and runs only the first case of each parameterized test; `all` runs everything; any other value is a JUnit tag expression, with every case (`providers`). | `lang/lsp-server/build.gradle.kts:352` |
| `lspTestForks` | half the processors, 1 to 8 | Test JVMs in parallel (`providers`). | `lang/lsp-server/build.gradle.kts:337` |
| `lsp.retentionCycles` | `120` | Cycles of the retention test (`providers`). | `lang/lsp-server/build.gradle.kts:358` |
| `lsp.soakRounds` | `5` | Rounds of the execution soak test (`providers`). | `lang/lsp-server/build.gradle.kts:362` |

### 5.3 IntelliJ plugin

| Property | Default | What it does | Read at |
|----------|---------|--------------|---------|
| `lsp4ijPlugin` | unset | An absolute path to a built LSP4IJ plugin directory, used instead of the Marketplace release; excludes the next two (`providers`). | `lang/intellij-plugin/build.gradle.kts:56` |
| `lsp4ijChannel` | unset | The newest LSP4IJ of a Marketplace channel, such as `nightly` (`providers`). | `lang/intellij-plugin/build.gradle.kts:70` |
| `lsp4ijVersion` | the catalog's version | An LSP4IJ version from the Marketplace (`providers`). | `lang/intellij-plugin/build.gradle.kts:73` |
| `log` | `XTC_LOG_LEVEL`, else `INFO` | Passed to `runIde`'s IDE as `xtc.logLevel` and `XTC_LOG_LEVEL`, which the plugin forwards to the server. | `lang/intellij-plugin/build.gradle.kts:116`, `:922` |
| `xtc.intellij.semanticTokens` | `true` | Passed to `runIde`'s IDE as `xtc.lsp.semanticTokens` and `XTC_LSP_SEMANTIC_TOKENS`. | `lang/intellij-plugin/build.gradle.kts:125`, `:924` |
| `XTC_LSP_LOG_DIR` (environment) | `~/.xtc/logs/lsp` | The directory whose server log `runIde` tails into its console. | `lang/intellij-plugin/build.gradle.kts:903` |
| `lsp.buildSearchableOptions` | `false` (also in the root `gradle.properties`) | Build the settings search index. | `lang/intellij-plugin/build.gradle.kts:628` |
| `org.xtclang.idea.home` | unset; required by `installPlugin` | The installed IntelliJ that `installPlugin` and `installIntellijPlugin` install into; it must be closed (`providers`). | `lang/intellij-plugin/build.gradle.kts:689` |
| `org.xtclang.idea.pluginsDir` | the plugins directory from the IDE's own log | Its plugins directory, when the IDE's report of it fails (`providers`). | `lang/intellij-plugin/build.gradle.kts:690`, `build-logic/common-plugins/src/main/kotlin/InstallIntellijPluginTask.kt:83` |
| `enablePublish` | `false` | Allow `publishPlugin` (`providers`). | `lang/intellij-plugin/build.gradle.kts:89` |
| `xdk.intellij.release.channel` | `alpha` | The Marketplace channel to publish to. | `lang/intellij-plugin/build.gradle.kts:40` |
| `jetbrains.token` | `""` | The Marketplace token; the environment variable is `JETBRAINS_TOKEN`. | `lang/intellij-plugin/build.gradle.kts:95` |
| `jetbrains.publish.suffix` | `""`; required for a published snapshot | Suffix of a snapshot's Marketplace version. | `lang/intellij-plugin/build.gradle.kts:96` |
| `JETBRAINS_CERTIFICATE_CHAIN`, `JETBRAINS_PRIVATE_KEY`, `JETBRAINS_PRIVATE_KEY_PASSWORD` (environment) | unset | Plugin signing. | `lang/intellij-plugin/build.gradle.kts:541` |

### 5.4 VS Code extension and Tree-sitter

| Property | Default | What it does | Read at |
|----------|---------|--------------|---------|
| `vscode.version.suffix` | `""` | A pre-release suffix for the packaged version (letters, digits, dots, hyphens) (`providers`). | `lang/vscode-extension/build.gradle.kts:170` |
| `vscode.marketplace.build` | `""`; required for a Marketplace package | The build number that replaces the patch version (`providers`). | `lang/vscode-extension/build.gradle.kts:187` |
| `testFiles` | unset | Comma-separated patterns that limit `testTreeSitterParse` (`providers`). | `lang/tree-sitter/build.gradle.kts:872` |
| `showTiming` | timing shown | `false` hides `testTreeSitterParse`'s timing (`providers`). | `lang/tree-sitter/build.gradle.kts:873` |

The Tree-sitter build caches Zig and the native libraries in Gradle's user home, under
`caches/zig` and `caches/tree-sitter-xtc` (`lang/tree-sitter/build.gradle.kts:77`).

## 6. Tests, playbooks, tours and the container

### 6.1 Language server tests

The test tasks set these JVM properties for the test code.

| Property | Set from | Read at |
|----------|----------|---------|
| `xtc.lsp.parameterizedCases` | `-PlspTests`: `first` for `default`, else `all` (`lang/lsp-server/build.gradle.kts:355`) | `lspTest/ParameterizedCases.kt:40` |
| `xtc.lsp.retentionCycles` | `-Plsp.retentionCycles` (`:356`) | `lspTest/adapter/XdkRetentionTest.kt:226` |
| `xtc.lsp.soakRounds` | `-Plsp.soakRounds` (`:360`) | `lspTest/run/EmbeddedExecutionTest.kt:2661` |
| `xtc.composite.root` | The directory with `version.properties` above lsp-server (`:397`) | `lspTest/server/LspIntegrationTest.kt:146` |
| `xtc.lsp.jar` | The fat jar, for the process tests (`:408`, `:481`) | `lspTest/server/LspProcessLifecycleTest.kt:49` |
| `xtc.logs.directory` | `lang/lsp-server/build/reports/server-logs` for tests, `build/reports/library-playbook/server-logs` for `prepareLibraryPlaybook` (`:258`, `:545`) | `lsp/util/ServerLogs.kt:197` |

The test JVMs get a 1 GB heap, so that the library compile of `xtc.lsp.compileLibraries` runs
(`lang/lsp-server/build.gradle.kts:343`).

### 6.2 IntelliJ playbook and tour

`testCompilerPlaybook` (`lang/intellij-plugin/build.gradle.kts:1046`) turns Gradle properties into JVM
properties of the Starter test JVM. All are `providers` properties.

| Gradle property | System property | Default | What it does | Read at |
|-----------------|-----------------|---------|--------------|---------|
| `intellijPlaybookCases` | `xtc.playbook.cases` | all cases | Comma-separated case IDs. | `ideaIt/CompilerPlaybookTest.kt:210` |
| `intellijPlaybookIde` | `xtc.playbook.ideVersion` | `2026.2.3`, the IDE the plugin builds against | The IntelliJ the playbook drives: the default, or `2026.3`, with IDE Starter and its driver for that IDE (the `lang-intellij-next-*` versions in `gradle/libs.versions.toml`). The plugin still builds against the default; only the playbook's IDE changes. | `lang/intellij-plugin/build.gradle.kts:259`, `ideaIt/PlaybookIde.kt:37` |
| `intellijPlaybookReplacementGates` | `xtc.playbook.replacementGates` | none | Upstream replacement gates to run; needs `-Plsp4ijPlugin` or `-Plsp4ijChannel`. | `ideaIt/CompilerPlaybookTest.kt:201` |
| `intellijLargeFileProbe` | `xtc.playbook.largeFileProbe` | `false` | Run the large-file editing probe. | `ideaIt/CompilerPlaybookTest.kt:25` |
| `intellijTour` | `xtc.tour` | `false` | Run the tour, which records and checks the plugin's features. | `ideaIt/TourTest.kt:20` |
| `intellijTourSections` | `xtc.tour.sections` | all sections | Sections of the tour to run. | `ideaIt/TourTest.kt:27` |
| `intellijTourVisible` | `xtc.tour.visible` | `false` | Show the pointer, keys and typing without a recording. | `ideaIt/TourTest.kt:43` |
| `intellijTourHang` | `xtc.tour.hang` | an hour | Seconds before a tour step counts as hung. | `ideaIt/Tour.kt:439` |
| `lsp.adapter` | `xtc.playbook.adapter` | `compiler` | The adapter the playbook expects; `treesitter` only for the settings-persistence session. | `ideaIt/CompilerPlaybookTest.kt:29` |
| `lsp4ijPlugin`, `lsp4ijVersion`, `lsp4ijChannel` | `xtc.playbook.lsp4ijPlugin`, `xtc.playbook.lsp4ijVersion`, `xtc.playbook.lsp4ijChannel` | the release | The LSP4IJ the test IDE installs. | `ideaIt/PlaybookIde.kt:38`, `:41`, `:42` |
| none | `xtc.playbook.scenarios`, `xtc.playbook.manual`, `xtc.playbook.reports`, `allure.results.directory` | from the build | `lang/test-fixtures/compiler-playbook/scenarios.json`, `lang/doc/manual-test-plan.md`, and `build/reports/compiler-playbook` | `ideaIt/CompilerPlaybookTest.kt:33`, `:36`, `:39` |
| `playbookRetainedRuns` | none | `5` | Completed run directories kept under `build/reports/compiler-playbook`; also for VS Code's reports. | `lang/intellij-plugin/build.gradle.kts:1041`, `lang/vscode-extension/build.gradle.kts:338` |

| Name | Kind | What it does | Read at |
|------|------|--------------|---------|
| `XTC_RECORD_VIDEO` | environment | `1`: the tour shows its actions for the screen recording. | `ideaIt/TourTest.kt:43` |
| `XTC_RECORDING_START_FILE` | environment, default `/out/screen.start` | The recording's start time, for the tour's clips. | `ideaIt/TourTest.kt:60` |
| `xtc.test.textmate` | system property, set by `test` | The TextMate bundle for the light platform tests. | `lang/intellij-plugin/build.gradle.kts:1019`, `lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/LightEditor.kt:46` |

### 6.3 VS Code tests

`testCompilerPlaybook` passes its Gradle properties to `npm run test:playbook` or `test:tour`
(`lang/vscode-extension/build.gradle.kts:369`); all are `providers` properties.

| Gradle property | Runner argument | Default | What it does |
|-----------------|-----------------|---------|--------------|
| `compilerPlaybookCases` | `--cases=<ids>` | all cases; without it, the task also runs the server's tests | Cases to run |
| `compilerPlaybookMultiRoot` | `--multi-root` | `false` | A two-folder workspace |
| `compilerPlaybookCancelUi` | `--cancel-ui` | `false` | Cancel through the UI; needs X145 |
| `vscodeTour` | `--tour` | `false` | Run the tour instead of the playbook |
| `vscodeTourSections` | `--tour-sections=<ids>` | all sections | Sections of the tour |
| `vscodeTourHang` | `--tour-hang=<seconds>` | an hour | Seconds before a tour step counts as hung |

The runner (`vsc/test/runTest.ts:20`) also takes `--theme=<name>` (default `Default Dark Modern`,
`:57`), `--lexical-baseline` (X277 without semantic highlighting), `--project-lifecycle` with
`--shared-process`, `--explorer-move-probe` with `--refresh-during-move`, and
`--settings-persistence`. On Linux without `DISPLAY` it runs under `xvfb-run`
(`lang/vscode-extension/scripts/run-vscode-tests.cjs:35`).

Each run gets a new profile and workspace. The profile's user settings set `workbench.colorTheme`
and `chat.disableAIFeatures: true`; the workspace's `.vscode/settings.json` sets `files.autoSave: off`,
`editor.semanticHighlighting.enabled: true`, `editor.inlayHints.enabled: on`, and for `[xtc]`
semantic highlighting except under `--lexical-baseline` (`vsc/test/runTest.ts:84`, `:93`).

The runners hand these environment variables to the test code in VS Code:

| Variable | Set at | Read at |
|----------|--------|---------|
| `XTC_PLAYBOOK_REPORT_DIR`, `XTC_PLAYBOOK_CASES`, `XTC_PLAYBOOK_CANCEL_UI`, `XTC_PLAYBOOK_UI_PORT` | `vsc/test/runTest.ts:125` | `vsc/test/playbook/index.ts:32`, `:34`, `:62`, `vsc/test/workbenchUi.ts:11` |
| `XTC_COMMIT`, `XTC_PLAYBOOK_DIRTY` | `vsc/test/runTest.ts:131`, from the environment when set (the container), else from git | `vsc/test/playbook/index.ts:60` |
| `XTC_LSP_LOG_DIR` | `vsc/test/runTest.ts:126`, `vsc/test/runTour.ts:71`: the run's `server-logs` | The server, `lsp/util/ServerLogs.kt:199`; `vsc/test/tour/tour.ts:205` |
| `XTC_TOUR_REPORT_DIR`, `XTC_TOUR_SECTIONS`, `XTC_TOUR_HANG`, `XTC_TOUR_FIXTURE` | `vsc/test/runTour.ts:61` | `vsc/test/tour/index.ts:15`, `:19`, `vsc/test/tour/tour.ts:146`, `vsc/test/tour/index.ts:16` |
| `XTC_RECORD_VIDEO`, `XTC_RECORDING_START_FILE` | `vsc/test/runTour.ts:69`, from the environment | `vsc/test/tour/ui.ts:28`, `vsc/test/tour/index.ts:31` |
| `XTC_LIFECYCLE_ROLE`, `XTC_LIFECYCLE_REPORT`, `XTC_LIFECYCLE_SHARED_PROCESS` | `vsc/test/runLifecycle.ts:68` | `vsc/test/lifecycle/index.ts:21`, `:11`, `:12` |
| `XTC_EXPLORER_PROBE_REPORT`, `XTC_EXPLORER_PROBE_REFRESH` | `vsc/test/runTest.ts:133` | `vsc/test/explorer-probe/index.ts:14`, `:16` |
| `XTC_SETTINGS_PHASE` | `vsc/test/runTest.ts:134` | `vsc/test/settings-persistence/index.ts:11` |
| `XTC_VSCODE_TEST_CACHE` | The user, or the container (`lang/intellij-plugin/container/in-container.sh:28`) | `vsc/test/vscodeCache.ts:42`; without it, `$XDG_CACHE_HOME`, `~/Library/Caches` or `%LOCALAPPDATA%`, then `xtclang/vscode-test` |

### 6.4 The playbook container

The root build's `containerPlaybook`, `containerPlaybookImage`, `containerPlaybookBuild`,
`containerPlaybookIntellij` and `containerPlaybookVsCode` run
`lang/intellij-plugin/container/playbook.sh` (`build.gradle.kts:235`).

| Name | Kind, default | What it does | Read at |
|------|---------------|--------------|---------|
| `playbookCases` | Gradle property (`providers`), `all` | Cases for the IntelliJ and VS Code container tasks. | `build.gradle.kts:233` |
| `org.xtclang.docker.command` | `xdkProperties`, `docker` (`xdk.properties:17`) | The container engine, such as `podman`; passed as `XTC_CONTAINER_COMMAND`. | `build.gradle.kts:232`, `:240` |
| `XTC_CONTAINER_COMMAND` | environment; else `docker`, or `podman` when `docker` is not installed | The engine `playbook.sh` runs. | `lang/intellij-plugin/container/playbook.sh:37` |
| `XTC_CONTAINER_MEMORY` | environment, `7g` | The container's memory limit; the IntelliJ tour needs more, such as `14g`. | `lang/intellij-plugin/container/playbook.sh:140` |
| `XTC_RECORD_VIDEO` | environment, empty | `1` records the virtual display to `/out/screen.mp4`; passed into the container. | `lang/intellij-plugin/container/playbook.sh:142`, `lang/intellij-plugin/container/in-container.sh:61` |
| `XTC_COMMIT`, `XTC_BRANCH`, `XTC_PLAYBOOK_DIRTY` | set by `playbook.sh` from git, since the export has no `.git` | The commit, branch and uncommitted paths, for javatools' build information and the VS Code playbook. | `lang/intellij-plugin/container/playbook.sh:141`, `javatools/build.gradle.kts:172`, `:174` |
| `XTC_VSCODE_TEST_CACHE`, `XTC_RECORDING_START_FILE` | set inside the container | The VS Code download cache on the Gradle volume; the recording's start file. | `lang/intellij-plugin/container/in-container.sh:28`, `:66` |
| `PLAYBOOK_WM` | environment inside the container, `openbox` | Window manager on Xvfb; `playbook.sh` does not pass it in, so it stays `openbox`. | `lang/intellij-plugin/container/in-container.sh:47` |

Inside the container, Gradle runs with `/gradle/gradle.properties`: `org.gradle.jvmargs=-Xmx2g
-XX:MaxMetaspaceSize=768m`, `kotlin.daemon.jvmargs=-Xmx1g`, `org.gradle.workers.max=4`, no file
watching (`lang/intellij-plugin/container/in-container.sh:31`). The volumes `xvm-playbook-gradle`,
`xvm-playbook-work` and `xvm-playbook-m2` keep caches between runs; one run at a time holds
`xvm-playbook-container.lock` in the repository's common git directory
(`lang/intellij-plugin/container/playbook.sh:97`). `playbook.sh playbook` and `playbook.sh vscode`
take further Gradle arguments, such as `-Plsp4ijChannel`; the root tasks pass none.

## Stale in other docs

None known. The survey behind this page found six statements in other documents that the code no longer bore
out, and they were corrected on 2026-11-11: `XTC_JAVA_HOME` in the manual test plan, which nothing reads; the
`-Dxtc.debug.lsp` toggle in the IntelliJ testing guide, which is code to add, not a property the plugin reads;
`xtc.formatting.tabSize` in the VS Code README, deprecated and unread; the lang build flags' defaults in the
language server's README and `lang/README.md`, which `gradle.properties` turns on; the launcher's `--socket`
option, which it never had; and the DAP server's `logback.xml`, which applies only to that server's own tests.
