# XTC Language Tooling - Reflective Kotlin DSL for IDE Support

## The Core Concept

**Define your language model ONCE → Generate support for ALL editors automatically**

This demonstrates a reflective approach to language tooling: instead of manually creating syntax highlighting,
code completion, and navigation for each IDE, we define the language structure once in `XtcLanguage.kt`
and generate IDE-specific files programmatically.

---

## Ground Truth: XtcLanguage.kt

**`XtcLanguage.kt` is THE single source of truth for the entire XTC/Ecstasy language.**

Everything about the language is defined here:
- **All keywords** (reserved and context-sensitive) with semantic categories
- **All operators** with precedence and associativity
- **All built-in types** (Int, String, Boolean, Array, Map, etc.)
- **All punctuation** and delimiters
- **All AST concepts** and their structure (109 node types)
- **Token patterns** for lexical analysis

All generators read from this model. To modify the language:
1. Edit `XtcLanguage.kt`
2. Run `./gradlew generateAllEditorSupport`
3. All editor files update automatically

---

## The Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        XtcLanguage.kt                           │
│  "I AM the XTC language definition"                             │
│                                                                 │
│  val xtcLanguage = language("Ecstasy", ...) {                   │
│      keywords(KeywordCategory.CONTROL, "if", "else", ...)       │
│      operator("&&", precedence=4, LEFT, LOGICAL)                │
│      builtinTypes("Int", "String", ...)                         │
│      concept("IfStatement") { child("condition", "Expression") }│
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ builds
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      LanguageModel.kt                           │
│  "I AM the data model and DSL builders"                         │
│                                                                 │
│  - data class LanguageModel(keywords, operators, concepts, ...) │
│  - data class OperatorDefinition(symbol, precedence, assoc)     │
│  - class LanguageModelBuilder { fun keywords(), operator() }    │
│  - fun language(): LanguageModel                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ consumed by
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Generators                              │
│  "We READ the model and GENERATE editor files"                  │
│                                                                 │
│  VimGenerator(model).generate()         → xtc.vim               │
│  EmacsGenerator(model).generate()       → xtc-mode.el           │
│  TextMateGenerator(model).generate()    → xtc.tmLanguage.json   │
│  TreeSitterGenerator(model).generate()  → grammar.js            │
└─────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer             | File               | Role                                                             |
|-------------------|--------------------|------------------------------------------------------------------|
| **Language Spec** | `XtcLanguage.kt`   | Defines ALL keywords, operators, types, precedence using the DSL |
| **Data Model**    | `LanguageModel.kt` | Data classes + DSL builders that XtcLanguage.kt uses             |
| **Generators**    | `*Generator.kt`    | Transform LanguageModel → editor-specific files                  |

### Dependency Direction

```
LanguageModel.kt  ←─uses─  XtcLanguage.kt  ←─uses─  Generators
   (schema)                (ground truth)           (consumers)
```

- **LanguageModel.kt** is language-agnostic - it defines `data class OperatorDefinition`, `fun language()`, etc.
- **XtcLanguage.kt** imports LanguageModel.kt and calls its DSL to create `val xtcLanguage: LanguageModel`
- **Generators** import XtcLanguage.kt and read from `xtcLanguage` to produce output

LanguageModel.kt knows nothing about XTC - it could be used to define any language. XtcLanguage.kt is where the actual
language knowledge lives.

### How Data Flows

```kotlin
// 1. XtcLanguage.kt uses the DSL to define the language
val xtcLanguage = language("Ecstasy", listOf("x", "xtc"), "source.xtc") {
    keywords(KeywordCategory.CONTROL, "if", "else", "for", "while")
    operator("+", precedence = 12, LEFT, ARITHMETIC)
    builtinTypes("Int", "String", "Boolean")
}

// 2. Generators query the model
val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
val operators = model.operators.filter { it.precedence == 12 }

// 3. Generators produce output
VimGenerator(xtcLanguage).generate()  // → "syn keyword xtcControl if else for while"
```

---

## Architecture Overview (Detailed)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      XtcLanguage.kt (DSL Model)                     │
│  - Keywords, Operators, Token Rules, AST Concepts, Scopes           │
└─────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│  VSCodeGenerator  │   │   EclipseGenerator  │   │  IntelliJGenerator  │
│                   │   │                     │   │                     │
│ • package.json    │   │ • plugin.xml        │   │ • plugin.xml        │
│ • extension.ts    │   │ • CodeScanner.java  │   │ • JFlex lexer       │
│ • snippets.json   │   │ • templates.xml     │   │ • TokenTypes.java   │
│ • tmLanguage.json │   │ • MANIFEST.MF       │   │ • SyntaxHighlighter │
└───────────────────┘   └─────────────────────┘   └─────────────────────┘
        │                           │                           │
        ▼                           ▼                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Shared LSP Server (lsp4j)                        │
│  • Completion • Hover • Go-to-definition • Diagnostics • Rename     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Files in This Directory

| File                          | Purpose                                                      |
|-------------------------------|--------------------------------------------------------------|
| `XtcLanguage.kt`              | **The source of truth** - Complete language model definition |
| `LspServerGenerator.kt`       | Generates LSP server implementation (Java)                   |
| `VSCodeExtensionGenerator.kt` | Generates VS Code extension files                            |
| `EclipseGenerator.kt`         | Generates Eclipse plugin components                          |
| `IntellijGenerator.kt`        | Generates IntelliJ IDEA plugin components                    |
| `TextMateGenerator.kt`        | Generates TextMate grammar (used by VS Code, Sublime, etc.)  |
| `TreeSitterGenerator.kt`      | Generates Tree-sitter grammar (used by Zed, Neovim, GitHub)  |
| `AdditionalGenerators.kt`     | Vim, Emacs, Monaco editor support                            |

### Pre-Generated Files

| File                          | Format              | Used By                       |
|-------------------------------|---------------------|-------------------------------|
| `xtc.tmLanguage.json`         | TextMate Grammar    | VS Code, Cursor, Sublime Text |
| `highlights.scm`              | Tree-sitter Queries | Zed, Neovim, Helix, GitHub    |
| `xtc.vim`                     | Vim Syntax          | Vim, Neovim                   |
| `xtc-mode.el`                 | Emacs Lisp          | Emacs                         |
| `xtc.ts`                      | TypeScript/Monarch  | Monaco Editor, VS Code Web    |
| `language-configuration.json` | VS Code Config      | VS Code, Cursor               |

---

## The Language Model (XtcLanguage.kt)

The language model captures everything about Ecstasy's syntax:

```code
val XtcLanguage = language(
    name = "Ecstasy",
    fileExtensions = listOf("x", "xtc"),
    scopeName = "source.xtc"
) {
    // Scopes - Map language elements to editor-specific styling
    scope("keyword") {
        textMate = "keyword.control.xtc"
        intellij = "KEYWORD"
        eclipse = "keyword"
        semanticToken = "keyword"
    }

    // Keywords
    keywords(
        "module", "package", "class", "interface", "mixin", "service",
        "const", "enum", "typedef", "if", "else", "for", "while", ...
    )

    // Operators with precedence
    operator("==", 8, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator("+", 12, Associativity.LEFT, OperatorCategory.ARITHMETIC)

    // Token rules (regex patterns)
    token("IDENTIFIER", "[a-zA-Z_][a-zA-Z0-9_]*", "variable.other.xtc")
    token("STRING", "\"(?:[^\"\\\\]|\\\\.)*\"", "string.quoted.double.xtc")

    // AST Concepts for semantic analysis
    concept("ClassDeclaration") {
        extends("TypeDeclaration")
        property("isAbstract", "Boolean", default = "false")
        children("members", "ClassMember")
    }
}
```

---

## Concrete Implementation: VS Code Extension

The `VSCodeExtensionGenerator` produces a complete extension structure.

### Generated Project Structure

```
vscode-xtc/
├── package.json                 # Extension manifest
├── language-configuration.json  # Brackets, comments, folding
├── src/
│   └── extension.ts            # LSP client entry point
├── syntaxes/
│   └── xtc.tmLanguage.json     # TextMate grammar
├── snippets/
│   └── ecstasy.json            # Code snippets
└── server/
    └── ecstasy-lsp.jar         # Bundled LSP server
```

### Extension Entry Point (extension.ts)

The generated TypeScript connects VS Code to the LSP server:

```code
import * as path from 'path';
import { workspace, ExtensionContext } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    // Path to the LSP server JAR (built from lang project)
    const serverJar = context.asAbsolutePath(
        path.join('server', 'ecstasy-lsp.jar')
    );

    const javaExecutable = process.env.JAVA_HOME
        ? path.join(process.env.JAVA_HOME, 'bin', 'java')
        : 'java';

    const serverOptions: ServerOptions = {
        run: {
            command: javaExecutable,
            args: ['-jar', serverJar],
            transport: TransportKind.stdio
        },
        debug: {
            command: javaExecutable,
            args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005',
                   '-jar', serverJar],
            transport: TransportKind.stdio
        }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'ecstasy' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/*.{x,xtc}')
        }
    };

    client = new LanguageClient(
        'ecstasyLanguageServer',
        'Ecstasy Language Server',
        serverOptions,
        clientOptions
    );

    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
```

### Usage

```bash
# Generate the extension
./gradlew generateVSCodeExtension

# Install in VS Code
cd build/vscode-xtc
npm install
npm run compile
code --install-extension .
```

---

## Concrete Implementation: Eclipse Plugin

The `EclipseGenerator` creates a traditional Eclipse plugin with LSP4E integration.

### Generated Project Structure

```
org.xtclang.eclipse/
├── META-INF/
│   └── MANIFEST.MF
├── plugin.xml
├── templates/
│   └── templates.xml
└── src/org/xtclang/eclipse/
    ├── Activator.java
    ├── EcstasyNature.java
    ├── editor/
    │   ├── EcstasyEditor.java
    │   ├── EcstasyCodeScanner.java      # Rule-based syntax highlighting
    │   ├── EcstasyColorManager.java
    │   └── EcstasyContentAssistProcessor.java
    ├── lsp/
    │   └── XtcLanguageServerConnectionProvider.java  # LSP4E integration
    └── preferences/
        └── EcstasyPreferencePage.java
```

### LSP4E Integration

Eclipse supports LSP via LSP4E. The generator creates a connection provider:

```code
package org.xtclang.eclipse.lsp;

import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;
import java.util.Arrays;
import java.util.List;

/**
 * Connects Eclipse to the XTC Language Server.
 *
 * Register in plugin.xml:
 * <extension point="org.eclipse.lsp4e.languageServer">
 *   <server id="org.xtclang.lsp"
 *           class="org.xtclang.eclipse.lsp.XtcLanguageServerConnectionProvider"
 *           label="Ecstasy Language Server"/>
 *   <contentTypeMapping
 *       id="org.xtclang.eclipse.contenttype"
 *       languageId="ecstasy"/>
 * </extension>
 */
public class XtcLanguageServerConnectionProvider extends ProcessStreamConnectionProvider {

    public XtcLanguageServerConnectionProvider() {
        String serverPath = getServerPath();
        String javaPath = getJavaPath();

        List<String> commands = Arrays.asList(
            javaPath,
            "-jar",
            serverPath
        );

        setCommands(commands);
        setWorkingDirectory(System.getProperty("user.home"));
    }

    private String getServerPath() {
        // Look in plugin bundle first, then external locations
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        URL url = bundle.getEntry("server/ecstasy-lsp.jar");
        if (url != null) {
            try {
                return FileLocator.toFileURL(url).getPath();
            } catch (IOException e) {
                // Fall back to system path
            }
        }

        // Check XTC_HOME environment variable
        String xtcHome = System.getenv("XTC_HOME");
        if (xtcHome != null) {
            return xtcHome + "/lib/ecstasy-lsp.jar";
        }

        throw new IllegalStateException("Cannot find Ecstasy LSP server");
    }

    private String getJavaPath() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            return javaHome + "/bin/java";
        }
        return "java";
    }
}
```

### Native Syntax Highlighting (EcstasyCodeScanner)

While LSP provides semantic tokens, the generator also creates a fast rule-based scanner for immediate feedback:

```code
// Generated from XtcLanguage model - see EclipseGenerator.kt
public class EcstasyCodeScanner extends RuleBasedScanner {

    public EcstasyCodeScanner(EcstasyColorManager colorManager) {
        IToken keywordToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_KEYWORD), null, SWT.BOLD));
        IToken stringToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_STRING)));
        // ... (other tokens from model)

        List<IRule> rules = new ArrayList<>();

        // Whitespace
        rules.add(new WhitespaceRule(new EcstasyWhitespaceDetector()));

        // Comments - derived from model.comments
        rules.add(new EndOfLineRule("//", commentToken));
        rules.add(new MultiLineRule("/**", "*/", docCommentToken, (char) 0, true));
        rules.add(new MultiLineRule("/*", "*/", commentToken, (char) 0, true));

        // Strings
        rules.add(new SingleLineRule("\"", "\"", stringToken, '\\'));
        rules.add(new SingleLineRule("$\"", "\"", stringToken, '\\'));

        // Keywords - directly from model.keywords
        WordRule wordRule = new WordRule(new EcstasyWordDetector(), defaultToken);
        wordRule.addWord("module", keywordToken);
        wordRule.addWord("class", keywordToken);
        wordRule.addWord("interface", keywordToken);
        // ... all 50+ keywords from XtcLanguage.keywords

        setRules(rules.toArray(new IRule[0]));
    }
}
```

---

## Concrete Implementation: IntelliJ IDEA Plugin

The `IntellijGenerator` creates a Grammar-Kit compatible plugin structure.

### Generated Project Structure

```
intellij-xtc/
├── build.gradle.kts
├── src/main/
│   ├── java/org/xtclang/intellij/
│   │   ├── EcstasyLanguage.java
│   │   ├── EcstasyFileType.java
│   │   ├── EcstasyIcons.java
│   │   ├── lexer/
│   │   │   ├── Ecstasy.flex          # JFlex specification
│   │   │   └── EcstasyLexerAdapter.java
│   │   ├── parser/
│   │   │   └── EcstasyParserDefinition.java
│   │   ├── psi/
│   │   │   ├── EcstasyTokenType.java
│   │   │   └── EcstasyTypes.java
│   │   ├── highlighting/
│   │   │   ├── EcstasySyntaxHighlighter.java
│   │   │   └── EcstasyColorSettingsPage.java
│   │   └── lsp/
│   │       ├── EcstasyLanguageServerFactory.java
│   │       └── EcstasyServerConnectionProvider.java
│   └── resources/
│       └── META-INF/
│           └── plugin.xml
```

### JFlex Lexer (generated from model)

The `IntellijGenerator.generateLexer()` produces a complete JFlex specification:

```jflex
package org.xtclang.intellij.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static org.xtclang.intellij.psi.EcstasyTypes.*;

%%

%class EcstasyLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%state STRING TEMPLATE_STRING BLOCK_COMMENT DOC_COMMENT

DIGIT = [0-9]
HEX_DIGIT = [0-9a-fA-F]
LETTER = [a-zA-Z_]
IDENTIFIER = {LETTER}({LETTER}|{DIGIT})*
WHITESPACE = [ \t\r\n]+

%%

<YYINITIAL> {
  {WHITESPACE}           { return com.intellij.psi.TokenType.WHITE_SPACE; }

  // Comments
  "/**"                  { yybegin(DOC_COMMENT); return DOC_COMMENT_START; }
  "/*"                   { yybegin(BLOCK_COMMENT); return BLOCK_COMMENT_START; }
  "//" [^\r\n]*          { return LINE_COMMENT; }

  // Keywords (from XtcLanguage.keywords)
  "module"               { return MODULE; }
  "package"              { return PACKAGE; }
  "class"                { return CLASS; }
  "interface"            { return INTERFACE; }
  "mixin"                { return MIXIN; }
  "service"              { return SERVICE; }
  "const"                { return CONST; }
  "enum"                 { return ENUM; }
  // ... all 50+ keywords

  // Operators (from XtcLanguage.operators, sorted by length desc)
  ">>>="                 { return USHR_ASSIGN; }
  "<=>"                  { return SPACESHIP; }
  ">>>"                  { return USHR; }
  // ... all operators

  {IDENTIFIER}           { return IDENTIFIER; }
}
```

### LSP Integration for IntelliJ

IntelliJ supports LSP via the LSP4IJ plugin (since 2023.2+):

```code
// LSP Server Descriptor
package org.xtclang.intellij.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl

class EcstasyLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return EcstasyServerConnectionProvider()
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl {
        return EcstasyLanguageClient(project)
    }
}

class EcstasyServerConnectionProvider : ProcessStreamConnectionProvider() {

    init {
        val serverPath = findServerJar()
        val javaPath = findJava()

        commands = listOf(javaPath, "-jar", serverPath)
    }

    private fun findServerJar(): String {
        // Check XTC_HOME
        System.getenv("XTC_HOME")?.let { xtcHome ->
            val serverJar = File(xtcHome, "lib/ecstasy-lsp.jar")
            if (serverJar.exists()) return serverJar.absolutePath
        }

        // Check bundled
        val bundled = PathManager.getPluginsPath() + "/intellij-xtc/server/ecstasy-lsp.jar"
        if (File(bundled).exists()) return bundled

        throw IllegalStateException("Cannot find Ecstasy LSP server")
    }

    private fun findJava(): String {
        System.getenv("JAVA_HOME")?.let { javaHome ->
            return "$javaHome/bin/java"
        }
        return "java"
    }
}
```

### plugin.xml Registration

```code
<idea-plugin>
    <id>org.xtclang.intellij</id>
    <name>Ecstasy Language Support</name>
    <vendor>xtclang.org</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.redhat.devtools.lsp4ij</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- File Type -->
        <fileType name="Ecstasy File"
                  implementationClass="org.xtclang.intellij.EcstasyFileType"
                  fieldName="INSTANCE"
                  language="ecstasy"
                  extensions="x;xtc"/>

        <!-- Syntax Highlighter (fast, lexer-based) -->
        <lang.syntaxHighlighterFactory
            language="ecstasy"
            implementationClass="org.xtclang.intellij.highlighting.EcstasySyntaxHighlighterFactory"/>

        <!-- Color Settings -->
        <colorSettingsPage
            implementationClass="org.xtclang.intellij.highlighting.EcstasyColorSettingsPage"/>

        <!-- Bracket Matching -->
        <lang.braceMatcher
            language="ecstasy"
            implementationClass="org.xtclang.intellij.EcstasyBraceMatcher"/>

        <!-- Commenter -->
        <lang.commenter
            language="ecstasy"
            implementationClass="org.xtclang.intellij.EcstasyCommenter"/>
    </extensions>

    <!-- LSP4IJ Integration -->
    <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
        <server id="ecstasyLanguageServer"
                name="Ecstasy Language Server"
                factoryClass="org.xtclang.intellij.lsp.EcstasyLanguageServerFactory">
            <description><![CDATA[
                Language server for the Ecstasy programming language.
            ]]></description>
        </server>
        <languageMapping language="ecstasy"
                         serverId="ecstasyLanguageServer"/>
    </extensions>
</idea-plugin>
```

---

## The Shared LSP Server

All three IDEs connect to the same LSP server, which lives in `lang/src/main/java/org/xvm/lsp/`.

### Key Components

| File                          | Purpose                                          |
|-------------------------------|--------------------------------------------------|
| `XtcLanguageServer.java`      | Main server implementing LSP protocol            |
| `XtcTextDocumentService.java` | Handles document operations (open, change, save) |
| `XtcWorkspaceService.java`    | Handles workspace operations                     |
| `XtcCompilerAdapter.java`     | Interface to actual XTC compiler                 |
| `XtcDiagnosticProvider.java`  | Produces error/warning diagnostics               |
| `XtcCompletionProvider.java`  | Provides code completion suggestions             |

### LSP Capabilities Provided

- **Text Document Sync**: Full and incremental sync
- **Completion**: Keywords, types, snippets, context-aware suggestions
- **Hover**: Documentation for symbols
- **Go to Definition**: Navigate to symbol declarations
- **Find References**: Find all usages of a symbol
- **Document Symbols**: Outline view
- **Workspace Symbols**: Search symbols across project
- **Code Actions**: Quick fixes and refactorings
- **Rename**: Safe symbol renaming
- **Formatting**: Document and range formatting
- **Folding**: Code folding ranges
- **Semantic Tokens**: Enhanced syntax highlighting

---

## Integration into the Lang Project

### 1. Build Configuration

```code
// lang/build.gradle.kts
plugins {
    java
    application
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
}

dependencies {
    // LSP4J
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")

    // For DSL/generators
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("dsl")
        }
    }
}
```

### 2. Generation Tasks

```code
// lang/build.gradle.kts

val generateVSCodeExtension by tasks.registering {
    group = "generation"
    description = "Generate VS Code extension from language model"

    doLast {
        javaexec {
            mainClass.set("org.xtclang.tooling.GenerateVSCodeKt")
            classpath = sourceSets.main.get().runtimeClasspath
            args = listOf("${layout.buildDirectory.get()}/vscode-xtc")
        }
    }
}

val generateEclipsePlugin by tasks.registering {
    group = "generation"
    description = "Generate Eclipse plugin from language model"

    doLast {
        javaexec {
            mainClass.set("org.xtclang.tooling.GenerateEclipseKt")
            classpath = sourceSets.main.get().runtimeClasspath
            args = listOf("${layout.buildDirectory.get()}/eclipse-xtc")
        }
    }
}

val generateIntelliJPlugin by tasks.registering {
    group = "generation"
    description = "Generate IntelliJ plugin from language model"

    doLast {
        javaexec {
            mainClass.set("org.xtclang.tooling.GenerateIntelliJKt")
            classpath = sourceSets.main.get().runtimeClasspath
            args = listOf("${layout.buildDirectory.get()}/intellij-xtc")
        }
    }
}

val generateAll by tasks.registering {
    group = "generation"
    description = "Generate all IDE plugins from language model"
    dependsOn(generateVSCodeExtension, generateEclipsePlugin, generateIntelliJPlugin)
}
```

---

## The Power of This Approach

| Traditional Approach                   | Model-Driven Approach              |
|----------------------------------------|------------------------------------|
| Write TextMate grammar manually        | Define model once                  |
| Write Eclipse scanner manually         | Generate TextMate from model       |
| Write IntelliJ lexer manually          | Generate Eclipse from model        |
| Write Vim syntax manually              | Generate IntelliJ from model       |
| Update each file when language changes | Generate Vim from model            |
| 5x maintenance burden                  | Re-generate all from updated model |

### Adding a New Keyword

```code
// 1. Add to XtcLanguage.kt
keywords(
    "module", "class", "interface", ...,
    "await"  // New keyword!
)

// 2. Run generators
./gradlew generateAll

// 3. All IDEs automatically updated
```

### Adding a New Editor

```kotlin
// 1. Create new generator
class SublimeGenerator(private val model: LanguageModel) {
    fun generateSyntax(): String = buildString {
        // Map model to Sublime Text syntax format
    }
}

// 2. The new editor gets full support from the existing model
```

---

## What Else Can This Model Generate?

Beyond syntax highlighting, the same model can drive:

### 1. Parser Generation
- ANTLR grammar skeleton
- PEG parser rules
- Hand-written recursive descent parser structure

### 2. Code Formatting
- Indentation rules
- Bracket/brace formatting
- Statement terminator handling

### 3. Documentation
- Keyword reference documentation
- Operator precedence tables
- Built-in type documentation

### 4. Testing Tools
- Syntax test cases
- Highlighting regression tests
- Parser test fixtures

### 5. Migration Tools
- Code modernization (when keywords change)
- Syntax upgrade assistants

---

## Usage

### Building the LSP Server

```bash
cd lang
./gradlew fatJar
# Output: build/libs/lang-0.1.0-SNAPSHOT-all.jar
```

### Installing in VS Code

```bash
./gradlew generateVSCodeExtension
cd build/vscode-xtc
npm install
npm run compile
code --install-extension .
```

### Installing in IntelliJ

```bash
./gradlew generateIntelliJPlugin
cd build/intellij-xtc
./gradlew buildPlugin
# Install from build/distributions/intellij-xtc-*.zip
```

### Installing in Eclipse

```bash
./gradlew generateEclipsePlugin
# Import build/eclipse-xtc as Eclipse plugin project
# Export and install
```

---

## When to Use Lexer vs Parser vs Both

Language tooling has different tiers of sophistication. Understanding when to use each component helps optimize
performance and complexity.

### Lexer-Only Scenarios (Token-Level)

A lexer (tokenizer) breaks source code into tokens but doesn't understand structure. Use **lexer-only** when you need:

| Scenario                      | Use Case                           | Why Lexer is Sufficient                               |
|-------------------------------|------------------------------------|-------------------------------------------------------|
| **Basic Syntax Highlighting** | TextMate, JFlex, Sublime           | Coloring keywords, strings, comments doesn't need AST |
| **Bracket Matching**          | All editors                        | `{` matches `}` - just token pairs, no tree needed    |
| **Comment Extraction**        | Documentation generators           | Find all `//` and `/* */` tokens                      |
| **String Extraction (i18n)**  | Internationalization tools         | Find all `"..."` tokens for translation               |
| **Code Formatting (Simple)**  | Indentation based on braces        | Count `{` and `}` tokens to determine indent level    |
| **Error Recovery Display**    | Show where parsing failed          | Lexer continues tokenizing even with syntax errors    |
| **Token Counting**            | Code metrics (LOC, comments ratio) | Count token types without structure                   |
| **Fast Navigation**           | Jump to next/prev keyword          | Token stream is enough                                |

**Example: IntelliJ Syntax Highlighter**
```java
// Uses only lexer - no parsing needed
public class EcstasySyntaxHighlighter extends SyntaxHighlighterBase {
    private final EcstasyLexerAdapter lexer = new EcstasyLexerAdapter();

    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType == EcstasyTypes.CLASS) return KEYWORD_KEYS;
        if (tokenType == EcstasyTypes.STRING) return STRING_KEYS;
        // Pure token-type mapping - no AST involved
    }
}
```

**Generated from XtcLanguage.kt:**
- `xtc.tmLanguage.json` - TextMate grammar (regex-based, lexer-level)
- `Ecstasy.flex` - JFlex lexer specification
- `xtc.vim` - Vim syntax (regex-based)

### Parser-Only Scenarios (Structure-Level)

A parser consumes tokens to build an AST (Abstract Syntax Tree). Use **parser with AST** when you need to understand
code structure:

| Scenario                  | Use Case                         | Why Parser is Required                                     |
|---------------------------|----------------------------------|------------------------------------------------------------|
| **Go-to-Definition**      | Navigate to declarations         | Must resolve names through scope hierarchy                 |
| **Find References**       | Find all usages                  | Must match symbol definitions to usages                    |
| **Code Completion**       | Context-aware suggestions        | Need to know: Am I in a class? Method? What's in scope?    |
| **Refactoring**           | Rename, Extract Method           | Must understand what is being renamed and all its usages   |
| **Type Checking**         | Error diagnostics                | Must resolve types through the AST                         |
| **Semantic Highlighting** | Color by meaning, not syntax     | `foo` could be variable, parameter, or type - need context |
| **Code Folding**          | Collapse regions                 | Need to identify class/method/block boundaries             |
| **Outline View**          | Symbol tree in sidebar           | Requires AST to show class→method→field hierarchy          |
| **Quick Fixes**           | "Add import", "Implement method" | Must understand what's missing from AST                    |

**Example: LSP Document Symbols**
```java
// Requires full AST to produce outline
public CompletableFuture<List<DocumentSymbol>> documentSymbol(DocumentSymbolParams params) {
    // 1. Parse document to AST
    AstNode ast = compiler.parse(getDocument(params));

    // 2. Walk AST to collect symbols
    List<DocumentSymbol> symbols = new ArrayList<>();
    ast.visit(new AstVisitor() {
        @Override
        public void visitClass(ClassDeclaration cls) {
            symbols.add(new DocumentSymbol(
                cls.getName(),
                SymbolKind.Class,
                cls.getRange(),
                cls.getSelectionRange()
            ));
            super.visitClass(cls);  // Visit children for nested symbols
        }
    });
    return CompletableFuture.completedFuture(symbols);
}
```

**Uses AST concepts from XtcLanguage.kt:**
- `ClassDeclaration`, `MethodDeclaration`, `PropertyDeclaration`
- Symbol hierarchy and scope resolution

### Layered Architecture: Combining Both

Modern IDE support typically uses a layered approach:

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER EXPERIENCE                          │
├─────────────────────────────────────────────────────────────────┤
│  Semantic Highlighting    Code Completion    Refactoring        │
│  (LSP semantic tokens)    (LSP completion)   (LSP code actions) │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ Requires AST (Parser + Semantics)
┌─────────────────────────────────▼───────────────────────────────┐
│                         LSP SERVER                              │
│  • Parses documents to AST                                      │
│  • Resolves symbols and types                                   │
│  • Provides rich language features                              │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ Falls back when unavailable
┌─────────────────────────────────▼───────────────────────────────┐
│                     LEXER-BASED FALLBACK                        │
│  • TextMate grammar (instant, regex-based)                      │
│  • Bracket matching                                             │
│  • Basic keyword highlighting                                   │
│  • Works even when LSP server is starting/crashed               │
└─────────────────────────────────────────────────────────────────┘
```

**Why This Matters:**

1. **Immediate Feedback**: TextMate highlighting appears instantly when you open a file, even before the LSP server
connects.

2. **Graceful Degradation**: If the LSP server crashes or hangs, basic syntax highlighting continues working.

3. **Performance**: Lexer-based highlighting handles rapid keystrokes smoothly. Semantic tokens are updated less
frequently.

4. **Incremental Parsing**: Some systems use incremental parsing - re-lex and reparse only changed regions.

### Performance Trade-offs

| Approach                         | Speed     | Accuracy           | Use When                              |
|----------------------------------|-----------|--------------------|---------------------------------------|
| **Lexer Only**                   | ⚡ Instant | 🔶 Token-level     | Syntax highlighting, bracket matching |
| **Lexer + Shallow Parse**        | ⚡ Fast    | 🔷 Structure-level | Outline, folding, basic navigation    |
| **Full Parse + Type Resolution** | 🐌 Slower | ✅ Semantic         | Go-to-def, completion, refactoring    |

### Practical Example: Highlighting `class`

```ecstasy
class Foo {           // `class` = keyword (lexer knows)
    Class type;       // `Class` = type reference (parser knows)
    void bar() {
        val c = Foo;  // `Foo` = type, not variable (semantics know)
    }
}
```

| Layer        | What it sees                        | Result                                |
|--------------|-------------------------------------|---------------------------------------|
| **Lexer**    | Token `class` at position 0         | Color: keyword blue                   |
| **Lexer**    | Token `Class` (identifier)          | Color: default                        |
| **Parser**   | `Class` is a type reference         | Color: type teal                      |
| **Semantic** | `Foo` resolves to class declaration | Color: class teal, link to definition |

---

## Summary

| Component                    | Source                    | Outputs                              |
|------------------------------|---------------------------|--------------------------------------|
| **XtcLanguage.kt**           | Language model definition | -                                    |
| **LspServerGenerator**       | Model                     | LSP server (Java)                    |
| **VSCodeExtensionGenerator** | Model                     | package.json, extension.ts, snippets |
| **TextMateGenerator**        | Model                     | xtc.tmLanguage.json                  |
| **EclipseGenerator**         | Model                     | plugin.xml, CodeScanner.java         |
| **IntellijGenerator**        | Model                     | plugin.xml, JFlex lexer, TokenTypes  |
| **TreeSitterGenerator**      | Model                     | grammar.js, highlights.scm           |
| **AdditionalGenerators**     | Model                     | Vim, Emacs, Monaco                   |

**Result**: One source of truth, consistent support across all editors, minimal maintenance.
