# Adding XTC Support to Analysis Tools

## Overview

This chapter examines how to add XTC language support to popular static analysis tools, code quality platforms, and development utilities. Each tool has different extension mechanisms, and the adapter layer provides a foundation for all of them.

## The Foundation: Adapter Layer

All tool integrations share the same foundation—the adapter layer extracts clean, immutable data structures from the XTC compiler:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Analysis Tools                               │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐  │
│  │  PMD    │ │ErrorProne│ │Checkstyle│ │ SpotBugs │ │SonarQube│ │
│  └────┬────┘ └────┬────┘ └────┬─────┘ └────┬─────┘ └────┬────┘  │
│       └──────────┬┴──────────┬┴────────────┴┬───────────┘       │
│                  │           │              │                   │
│            ┌─────▼───────────▼──────────────▼─────┐             │
│            │         XTC Adapter Layer            │             │
│            │  (Shared by all tools)               │             │
│            └─────────────────┬────────────────────┘             │
│                              │                                  │
│                        ┌─────▼─────┐                            │
│                        │    XTC    │                            │
│                        │ Compiler  │                            │
│                        └───────────┘                            │
└─────────────────────────────────────────────────────────────────┘
```

## Tool-by-Tool Integration Guide

### 1. PMD (Pretty Much Done) Analyzer

**What PMD provides**: Rule-based source code analysis for bug patterns, code style, and best practices.

**Extension mechanism**: PMD supports custom language modules through its `Language` SPI.

**Integration approach**:

```java
// pmd-xtc/src/main/java/net/sourceforge/pmd/lang/xtc/XtcLanguageModule.java
public final class XtcLanguageModule extends BaseLanguageModule {
    public static final String NAME = "Ecstasy";
    public static final String TERSE_NAME = "xtc";

    public XtcLanguageModule() {
        super(LanguageMetadata.languageMetadata(NAME)
            .extensions("x", "xtc")
            .addVersion("1.0")
            .addDefaultVersion("1.0"));
    }

    @Override
    public XtcParser createParser(@NonNull ParserTask task) {
        return new XtcParser();
    }
}

// Parser adapter
public final class XtcParser extends AbstractParser {
    @Override
    public @NonNull Node parse(@NonNull ParserTask task) {
        final XtcCompilerAdapter adapter = new XtcCompilerAdapter();
        final LspSnapshot snapshot = adapter.compile(
            task.getFileId().getAbsolutePath(),
            task.getSourceText()
        );

        // Convert XTC AST to PMD's AST format
        return XtcAstConverter.convert(snapshot.ast());
    }
}
```

**Creating PMD rules for XTC**:

```java
// pmd-xtc/src/main/java/net/sourceforge/pmd/lang/xtc/rule/design/AvoidPublicFieldsRule.java
public final class AvoidPublicFieldsRule extends AbstractXtcRule {
    @Override
    public Object visit(@NonNull final XtcPropertyNode node, @NonNull final Object data) {
        if (node.getVisibility() == Visibility.PUBLIC && node.hasBacking()) {
            addViolation(data, node, "Avoid public fields; use a property instead");
        }
        return super.visit(node, data);
    }
}
```

**Ruleset definition**:

```xml
<!-- pmd-xtc/src/main/resources/rulesets/xtc/design.xml -->
<ruleset name="XTC Design Rules">
    <rule name="AvoidPublicFields"
          class="net.sourceforge.pmd.lang.xtc.rule.design.AvoidPublicFieldsRule"
          language="xtc"
          message="Avoid public fields">
        <description>
            Public fields expose internal state. Use properties with getters/setters.
        </description>
        <priority>3</priority>
    </rule>
</ruleset>
```

**Effort estimate**: 3-4 weeks for basic integration, 2-3 additional weeks for rule library.

---

### 2. Error Prone

**What Error Prone provides**: Compile-time bug detection with automatic fixes for Java.

**Extension mechanism**: Error Prone is Java-specific and uses `@BugPattern` annotations with javac plugin infrastructure.

**Challenge**: Error Prone is deeply tied to javac's internal APIs. Direct integration is not feasible.

**Alternative approach**: Create an "Error Prone-style" checker for XTC:

```java
// xtc-errorprone/src/main/java/org/xvm/errorprone/XtcBugChecker.java
@BugPattern(
    summary = "Getter methods should not return void",
    severity = WARNING,
    linkType = NONE
)
public final class VoidGetterChecker implements XtcChecker {
    @Override
    public void analyze(@NonNull final LspSnapshot snapshot, @NonNull final Reporter reporter) {
        for (final SymbolInfo symbol : snapshot.symbols().allSymbols()) {
            if (symbol.kind() == SymbolKind.METHOD) {
                final String name = symbol.name();
                if (name.startsWith("get") && returnsVoid(symbol)) {
                    reporter.report(
                        symbol.location(),
                        "Getter method '%s' should not return void",
                        name
                    );
                }
            }
        }
    }

    private boolean returnsVoid(@NonNull final SymbolInfo method) {
        return method.returnTypes().isEmpty();
    }
}
```

**Framework for XTC-ErrorProne-style checks**:

```java
// Core infrastructure
public interface XtcChecker {
    void analyze(@NonNull LspSnapshot snapshot, @NonNull Reporter reporter);
}

public interface Reporter {
    void report(@NonNull Location loc, @NonNull String message, Object... args);
    void reportWithFix(@NonNull Location loc, @NonNull String message, @NonNull Fix fix);
}

public record Fix(@NonNull String description, @NonNull String replacement) {}

// Runner that invokes all checkers
public final class XtcBugPatternRunner {
    private final List<XtcChecker> checkers;

    public List<Diagnostic> run(@NonNull final LspSnapshot snapshot) {
        final var reporter = new DiagnosticCollector();
        for (final XtcChecker checker : checkers) {
            checker.analyze(snapshot, reporter);
        }
        return reporter.diagnostics();
    }
}
```

**Gradle integration**:

```kotlin
// build.gradle.kts
plugins {
    id("org.xvm.xtc") version "1.0"
    id("org.xvm.errorprone") version "1.0"  // New plugin
}

xtcErrorProne {
    enable("VoidGetter", "UnusedPrivateMethod", "MissingDocumentation")
    disable("NamingConvention")  // Team doesn't use this rule

    // Treat specific checks as errors
    error("NullPointerDereference", "ResourceLeak")
}
```

**Effort estimate**: 2-3 weeks for framework, ongoing effort for checker library.

---

### 3. Checkstyle

**What Checkstyle provides**: Style enforcement for code formatting and naming conventions.

**Extension mechanism**: Checkstyle uses a modular check system with TreeWalker for AST traversal.

**Integration approach**:

```java
// checkstyle-xtc/src/main/java/com/puppycrawl/tools/checkstyle/xtc/XtcFileParser.java
public final class XtcFileParser implements FileParser {
    @Override
    public @NonNull FileText parse(@NonNull final File file) {
        final XtcCompilerAdapter adapter = new XtcCompilerAdapter();
        final LspSnapshot snapshot = adapter.compile(file.toPath());
        return new XtcFileText(file, snapshot);
    }
}

// XTC-specific checks
public final class XtcNamingConventionCheck extends AbstractCheck {
    private Pattern classPattern = Pattern.compile("[A-Z][a-zA-Z0-9]*");
    private Pattern methodPattern = Pattern.compile("[a-z][a-zA-Z0-9]*");

    @Override
    public void visitToken(@NonNull final DetailAST ast) {
        if (ast instanceof XtcClassNode classNode) {
            final String name = classNode.getName();
            if (!classPattern.matcher(name).matches()) {
                log(ast, "Class name ''{0}'' must match pattern ''{1}''",
                    name, classPattern.pattern());
            }
        } else if (ast instanceof XtcMethodNode methodNode) {
            final String name = methodNode.getName();
            if (!methodPattern.matcher(name).matches()) {
                log(ast, "Method name ''{0}'' must match pattern ''{1}''",
                    name, methodPattern.pattern());
            }
        }
    }
}
```

**Checkstyle configuration**:

```xml
<!-- checkstyle-xtc.xml -->
<!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="XtcTreeWalker">
        <module name="XtcNamingConvention">
            <property name="classPattern" value="[A-Z][a-zA-Z0-9]*"/>
            <property name="methodPattern" value="[a-z][a-zA-Z0-9]*"/>
            <property name="propertyPattern" value="[a-z][a-zA-Z0-9]*"/>
        </module>
        <module name="XtcWhitespace">
            <property name="afterComma" value="true"/>
            <property name="beforeBrace" value="true"/>
        </module>
        <module name="XtcDocumentation">
            <property name="requirePublicMethodDoc" value="true"/>
            <property name="requireClassDoc" value="true"/>
        </module>
    </module>
</module>
```

**Effort estimate**: 2-3 weeks for integration, 2 weeks for common checks.

---

### 4. SpotBugs (FindBugs successor)

**What SpotBugs provides**: Bytecode-level bug detection.

**Challenge**: SpotBugs analyzes JVM bytecode, but XTC compiles to `.xtc` format, not JVM bytecode.

**Integration approach**: Analyze XTC's semantic model instead of bytecode:

```java
// spotbugs-xtc/src/main/java/edu/umd/cs/findbugs/xtc/XtcDetector.java
public abstract class XtcDetector implements Detector {
    protected LspSnapshot snapshot;
    protected BugReporter bugReporter;

    @Override
    public void visitSnapshot(@NonNull final LspSnapshot snapshot) {
        this.snapshot = snapshot;
        analyzeSnapshot();
    }

    protected abstract void analyzeSnapshot();

    protected void reportBug(
            @NonNull final String type,
            @NonNull final Location location,
            @NonNull final String message) {
        bugReporter.reportBug(new XtcBugInstance(type, location, message));
    }
}

// Example detector: Null dereference detection
public final class NullDereferenceDetector extends XtcDetector {
    @Override
    protected void analyzeSnapshot() {
        for (final MethodInfo method : snapshot.symbols().allMethods()) {
            analyzeMethod(method);
        }
    }

    private void analyzeMethod(@NonNull final MethodInfo method) {
        // Simplified: check for Nullable parameter used without null check
        for (final ParameterInfo param : method.parameters()) {
            if (param.isNullable()) {
                checkNullableUsage(method, param);
            }
        }
    }

    private void checkNullableUsage(
            @NonNull final MethodInfo method,
            @NonNull final ParameterInfo param) {
        // Walk the method body looking for unguarded access
        final List<Location> unguardedAccesses = findUnguardedAccesses(method, param);
        for (final Location loc : unguardedAccesses) {
            reportBug("XTC_NULL_DEREF", loc,
                "Nullable parameter '" + param.name() + "' accessed without null check");
        }
    }
}
```

**Effort estimate**: 4-5 weeks due to custom bytecode-equivalent analysis.

---

### 5. SonarQube

**What SonarQube provides**: Comprehensive code quality platform with dashboards, history, and CI integration.

**Extension mechanism**: SonarQube supports language plugins through its `sonar-plugin-api`.

**Integration approach**:

```java
// sonar-xtc-plugin/src/main/java/org/sonar/plugins/xtc/XtcPlugin.java
public final class XtcPlugin implements Plugin {
    @Override
    public void define(@NonNull final Context context) {
        context.addExtensions(
            XtcLanguage.class,
            XtcQualityProfile.class,
            XtcRulesDefinition.class,
            XtcSensor.class
        );
    }
}

// Language definition
public final class XtcLanguage extends AbstractLanguage {
    public XtcLanguage() {
        super("xtc", "Ecstasy");
    }

    @Override
    public String @NonNull [] getFileSuffixes() {
        return new String[] { ".x", ".xtc" };
    }
}

// Sensor that analyzes files
public final class XtcSensor implements Sensor {
    @Override
    public void execute(@NonNull final SensorContext context) {
        final FileSystem fs = context.fileSystem();
        final Iterable<InputFile> files = fs.inputFiles(
            fs.predicates().hasLanguage("xtc")
        );

        for (final InputFile file : files) {
            analyzeFile(context, file);
        }
    }

    private void analyzeFile(
            @NonNull final SensorContext context,
            @NonNull final InputFile file) {
        final XtcCompilerAdapter adapter = new XtcCompilerAdapter();
        final LspSnapshot snapshot = adapter.compile(file.path());

        // Report issues
        for (final DiagnosticInfo diag : snapshot.diagnostics()) {
            saveIssue(context, file, diag);
        }

        // Report metrics
        saveMeasures(context, file, computeMetrics(snapshot));
    }

    private @NonNull Metrics computeMetrics(@NonNull final LspSnapshot snapshot) {
        return new Metrics(
            linesOfCode: snapshot.metrics().linesOfCode(),
            complexity: snapshot.metrics().cyclomaticComplexity(),
            classes: snapshot.symbols().classes().size(),
            methods: snapshot.symbols().allMethods().size()
        );
    }
}
```

**SonarQube rules**:

```java
// Define XTC-specific rules
public final class XtcRulesDefinition implements RulesDefinition {
    @Override
    public void define(@NonNull final Context context) {
        final NewRepository repository = context.createRepository("xtc", "xtc")
            .setName("XTC Analyzer");

        // Built-in rules
        addRule(repository, "VoidGetter",
            "Getter methods should return a value",
            Severity.MAJOR, "5min");
        addRule(repository, "MissingDoc",
            "Public APIs should be documented",
            Severity.MINOR, "10min");
        addRule(repository, "ServiceNaming",
            "Service classes should end with 'Service'",
            Severity.INFO, "2min");

        repository.done();
    }
}
```

**Effort estimate**: 3-4 weeks for basic integration, 4-6 weeks for comprehensive rule set.

---

### 6. IntelliJ IDEA Inspections

**What IntelliJ provides**: IDE-integrated code inspections with quick fixes.

**Extension mechanism**: IntelliJ plugin API with `LocalInspectionTool`.

**Integration approach**:

```kotlin
// intellij-xtc/src/main/kotlin/org/xvm/intellij/inspection/XtcInspectionBase.kt
abstract class XtcInspectionBase : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return object : XtcVisitor() {
            override fun visitMethod(method: XtcMethod) {
                checkMethod(method, holder)
            }

            override fun visitClass(cls: XtcClass) {
                checkClass(cls, holder)
            }
        }
    }

    abstract fun checkMethod(method: XtcMethod, holder: ProblemsHolder)
    abstract fun checkClass(cls: XtcClass, holder: ProblemsHolder)
}

// Example inspection
class VoidGetterInspection : XtcInspectionBase() {
    override fun checkMethod(method: XtcMethod, holder: ProblemsHolder) {
        if (method.name.startsWith("get") && method.returnType == XtcType.VOID) {
            holder.registerProblem(
                method.nameIdentifier,
                "Getter method should not return void",
                QuickFixFactory.getInstance().createRenameElementFix(method)
            )
        }
    }

    override fun checkClass(cls: XtcClass, holder: ProblemsHolder) {
        // No class-level checks for this inspection
    }
}
```

**Effort estimate**: Already part of IntelliJ plugin; 1-2 weeks additional for rich inspection set.

---

### 7. GitHub Code Scanning / SARIF

**What it provides**: Security-focused scanning with GitHub integration.

**Extension mechanism**: SARIF (Static Analysis Results Interchange Format) output.

**Integration approach**:

```java
// xtc-sarif/src/main/java/org/xvm/sarif/XtcSarifExporter.java
public final class XtcSarifExporter {
    public @NonNull SarifLog export(@NonNull final List<DiagnosticInfo> diagnostics) {
        final var runs = new ArrayList<Run>();
        final var tool = Tool.builder()
            .driver(ToolComponent.builder()
                .name("XTC Analyzer")
                .version("1.0.0")
                .informationUri(URI.create("https://xtclang.org"))
                .rules(defineRules())
                .build())
            .build();

        final var results = diagnostics.stream()
            .map(this::toResult)
            .toList();

        runs.add(Run.builder()
            .tool(tool)
            .results(results)
            .build());

        return SarifLog.builder()
            .version("2.1.0")
            .runs(runs)
            .build();
    }

    private @NonNull Result toResult(@NonNull final DiagnosticInfo diag) {
        return Result.builder()
            .ruleId(diag.code())
            .level(toLevel(diag.severity()))
            .message(Message.builder().text(diag.message()).build())
            .locations(List.of(toLocation(diag.location())))
            .build();
    }
}
```

**GitHub Actions integration**:

```yaml
# .github/workflows/xtc-analysis.yml
name: XTC Code Analysis
on: [push, pull_request]

jobs:
  analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup XTC
        uses: xtclang/setup-xtc@v1

      - name: Run XTC Analysis
        run: |
          xtc analyze --sarif-output=results.sarif

      - name: Upload SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: results.sarif
```

**Effort estimate**: 1 week for SARIF export, minimal for GitHub integration.

---

## Summary: Effort Estimates

| Tool | Integration Effort | Rule Library | Total |
|------|-------------------|--------------|-------|
| PMD | 3-4 weeks | 2-3 weeks | 5-7 weeks |
| Error Prone-style | 2-3 weeks | Ongoing | 2-3 weeks + ongoing |
| Checkstyle | 2-3 weeks | 2 weeks | 4-5 weeks |
| SpotBugs | 4-5 weeks | 3-4 weeks | 7-9 weeks |
| SonarQube | 3-4 weeks | 4-6 weeks | 7-10 weeks |
| IntelliJ | (Part of plugin) | 1-2 weeks | 1-2 weeks |
| GitHub/SARIF | 1 week | — | 1 week |

## Recommended Priority

1. **GitHub/SARIF** — Low effort, immediate CI/CD value
2. **IntelliJ Inspections** — Already building plugin anyway
3. **PMD** — Well-established, extensible framework
4. **SonarQube** — Enterprise value, comprehensive metrics
5. **Checkstyle** — Style enforcement, lower priority
6. **SpotBugs** — High effort, semantic analysis
7. **Error Prone-style** — Build custom framework if needed

## The Key Insight

All these integrations share the **adapter layer**. Build it once, and every tool can use it:

```java
// All tools use this same interface
public interface XtcCompilerAdapter {
    @NonNull LspSnapshot compile(@NonNull Path source);
    @NonNull LspSnapshot compile(@NonNull String path, @NonNull String content);
}
```

The adapter extracts:
- AST nodes with positions
- Symbol table with types
- Diagnostics with locations
- Metrics (lines of code, complexity, etc.)

Each tool just needs a thin translation layer from `LspSnapshot` to its own data model.
