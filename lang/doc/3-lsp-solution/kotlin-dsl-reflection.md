# Kotlin DSL for XTC Reflection and Querying

## Overview

This chapter explores the potential of creating a Kotlin DSL for querying and analyzing XTC code structures. Such a DSL would provide a type-safe, expressive way to inspect XTC modules, classes, methods, and types—enabling powerful tooling, code generation, and analysis without needing deep compiler knowledge.

## Why Kotlin?

The XTC Gradle plugin is already written in Kotlin. Extending it with a DSL provides:

1. **Type safety**: Compile-time verification of queries
2. **IDE support**: Autocompletion, navigation, refactoring
3. **Composability**: Build complex queries from simple primitives
4. **Familiarity**: Kotlin DSLs feel natural to developers

## Conceptual Design

### Core Model

The DSL would expose XTC's structure through a clean, immutable API:

```kotlin
// Core types in the DSL
interface XtcModule {
    val name: String
    val version: Version?
    val packages: List<XtcPackage>
    val imports: List<XtcImport>
    val classes: List<XtcClass>
    val interfaces: List<XtcInterface>
    val services: List<XtcService>
}

interface XtcClass {
    val name: String
    val qualifiedName: String
    val visibility: Visibility
    val annotations: List<XtcAnnotation>
    val typeParameters: List<XtcTypeParameter>
    val superclass: XtcTypeReference?
    val interfaces: List<XtcTypeReference>
    val properties: List<XtcProperty>
    val methods: List<XtcMethod>
    val constructors: List<XtcConstructor>
    val innerClasses: List<XtcClass>
}

interface XtcMethod {
    val name: String
    val visibility: Visibility
    val annotations: List<XtcAnnotation>
    val parameters: List<XtcParameter>
    val returnTypes: List<XtcTypeReference>  // XTC supports multi-return
    val isConditional: Boolean
    val body: XtcStatementBlock?
}
```

### DSL for Querying

```kotlin
// Query all public methods in a module
val publicMethods = xtcModule {
    classes {
        methods {
            filter { it.visibility == Visibility.PUBLIC }
        }
    }
}

// Find all classes that extend a specific base class
val persistentClasses = xtcModule {
    classes {
        filter { it.superclass?.name == "Persistent" }
    }
}

// Find methods with specific annotations
val restEndpoints = xtcModule {
    classes {
        methods {
            filter { method ->
                method.annotations.any { it.name in listOf("Get", "Post", "Put", "Delete") }
            }
        }
    }
}

// Complex query: Find all service classes with conditional methods
val servicesWithConditionals = xtcModule {
    services {
        filter { service ->
            service.methods.any { it.isConditional }
        }
        map { service ->
            ServiceInfo(
                name = service.name,
                conditionalMethods = service.methods.filter { it.isConditional }
            )
        }
    }
}
```

### DSL for Pattern Matching

```kotlin
// Find potential bugs: getters that return void (example from earlier doc)
val suspiciousGetters = xtcModule.analyze {
    methods {
        filter {
            it.name.startsWith("get") &&
            it.returnTypes.isEmpty()
        }
        report { "Getter ${it.qualifiedName} returns void" }
    }
}

// Find unused private methods
val unusedPrivateMethods = xtcModule.analyze {
    val allCalls = methods.flatMap { it.body?.methodCalls ?: emptyList() }

    methods {
        filter {
            it.visibility == Visibility.PRIVATE &&
            it.qualifiedName !in allCalls.map { call -> call.targetMethod }
        }
        report { "Potentially unused: ${it.qualifiedName}" }
    }
}
```

## Integration with Gradle Plugin

### Current Plugin Structure

The existing XTC Gradle plugin provides:
- `XtcCompileTask` - Compiles `.x` files to `.xtc` modules
- `XtcRunTask` - Runs XTC modules
- Source set integration (`src/main/x`, `src/test/x`)

### Enhanced Plugin with DSL

```kotlin
// build.gradle.kts
plugins {
    id("org.xvm.xtc") version "1.0"
}

xtc {
    mainModule = "myapp"

    // New: DSL-based analysis
    analyze {
        // Run on every build
        onCompile {
            // Find deprecated API usage
            methods {
                filter { it.annotations.any { a -> a.name == "Deprecated" } }
                forEach { method ->
                    logger.warn("Using deprecated method: ${method.qualifiedName}")
                }
            }
        }

        // Custom checks
        check("no-void-getters") {
            methods {
                filter { it.name.startsWith("get") && it.returnTypes.isEmpty() }
                fail { "Getter should not return void: ${it.qualifiedName}" }
            }
        }

        check("service-documentation") {
            services {
                filter { it.doc == null }
                warn { "Service ${it.name} lacks documentation" }
            }
        }
    }
}
```

### API Extraction and Code Generation

```kotlin
// Extract API surface for documentation
val apiDoc = xtcModule.extract {
    classes {
        filter { it.visibility == Visibility.PUBLIC }
        map { cls ->
            ClassDoc(
                name = cls.name,
                doc = cls.doc,
                methods = cls.methods
                    .filter { it.visibility == Visibility.PUBLIC }
                    .map { MethodDoc(it.name, it.doc, it.signature) }
            )
        }
    }
}

// Generate TypeScript type definitions
val typeScriptDefs = xtcModule.generate {
    interfaces {
        filter { it.visibility == Visibility.PUBLIC }
        template { iface ->
            """
            export interface ${iface.name} {
                ${iface.methods.joinToString("\n    ") { m ->
                    "${m.name}(${m.parameters.toTypeScript()}): ${m.returnTypes.toTypeScript()};"
                }}
            }
            """.trimIndent()
        }
    }
}
```

## Implementation Architecture

### Adapter Layer Integration

The DSL would sit on top of the adapter layer described in [Adapter Layer Design](./adapter-layer-design.md):

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kotlin DSL Layer                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ Query DSL    │ │ Analysis DSL │ │ Code Generation DSL      │ │
│  └──────┬───────┘ └──────┬───────┘ └────────────┬─────────────┘ │
│         └────────────────┼──────────────────────┘               │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                    ┌─────▼─────┐                                │
│                    │ Snapshot  │  (Immutable data model)        │
│                    │   API     │                                │
│                    └─────┬─────┘                                │
├──────────────────────────┼──────────────────────────────────────┤
│                    ┌─────▼─────┐                                │
│                    │  Adapter  │  (Extraction from compiler)    │
│                    │   Layer   │                                │
│                    └─────┬─────┘                                │
├──────────────────────────┼──────────────────────────────────────┤
│                    ┌─────▼─────┐                                │
│                    │    XTC    │                                │
│                    │ Compiler  │                                │
│                    └───────────┘                                │
└─────────────────────────────────────────────────────────────────┘
```

### Core Implementation

```kotlin
// XtcDsl.kt - Entry point
object XtcDsl {
    fun module(path: Path): XtcModuleScope {
        // Use adapter layer to extract module data
        val adapter = XtcCompilerAdapter()
        val snapshot = adapter.compile(path)
        return XtcModuleScope(snapshot)
    }

    fun module(compiledModule: FileStructure): XtcModuleScope {
        val snapshot = XtcSnapshotExtractor.extract(compiledModule)
        return XtcModuleScope(snapshot)
    }
}

// Scoped DSL for type-safe queries
class XtcModuleScope(private val snapshot: LspSnapshot) {
    fun classes(block: ClassQueryScope.() -> Unit): List<XtcClass> {
        return ClassQueryScope(snapshot.symbols().classes()).apply(block).results()
    }

    fun methods(block: MethodQueryScope.() -> Unit): List<XtcMethod> {
        return MethodQueryScope(snapshot.symbols().allMethods()).apply(block).results()
    }

    fun services(block: ClassQueryScope.() -> Unit): List<XtcClass> {
        return ClassQueryScope(snapshot.symbols().services()).apply(block).results()
    }
}

// Query scope with filter/map operations
class ClassQueryScope(private var classes: Sequence<XtcClass>) {
    private val operations = mutableListOf<(Sequence<XtcClass>) -> Sequence<XtcClass>>()

    fun filter(predicate: (XtcClass) -> Boolean) {
        operations.add { it.filter(predicate) }
    }

    fun <R> map(transform: (XtcClass) -> R): List<R> {
        return results().map(transform)
    }

    fun results(): List<XtcClass> {
        return operations.fold(classes) { acc, op -> op(acc) }.toList()
    }
}
```

## Use Cases

### 1. API Compatibility Checking

```kotlin
// Compare two module versions for breaking changes
fun checkCompatibility(oldModule: XtcModule, newModule: XtcModule): List<BreakingChange> {
    val oldPublicApi = oldModule.publicApi()
    val newPublicApi = newModule.publicApi()

    return buildList {
        // Check for removed public classes
        oldPublicApi.classes.forEach { oldClass ->
            if (newPublicApi.classes.none { it.qualifiedName == oldClass.qualifiedName }) {
                add(BreakingChange.RemovedClass(oldClass.qualifiedName))
            }
        }

        // Check for removed public methods
        oldPublicApi.methods.forEach { oldMethod ->
            val newClass = newPublicApi.classes
                .find { it.qualifiedName == oldMethod.declaringClass }
            if (newClass?.methods?.none { it.signature == oldMethod.signature } == true) {
                add(BreakingChange.RemovedMethod(oldMethod.signature))
            }
        }

        // Check for changed method signatures
        // ...
    }
}
```

### 2. Documentation Generation

```kotlin
// Generate markdown documentation
fun generateDocs(module: XtcModule): String = buildString {
    appendLine("# ${module.name}")
    appendLine()
    appendLine(module.doc ?: "_No module documentation_")
    appendLine()

    module.classes.filter { it.visibility == Visibility.PUBLIC }.forEach { cls ->
        appendLine("## ${cls.name}")
        appendLine()
        appendLine(cls.doc ?: "_No class documentation_")
        appendLine()

        appendLine("### Methods")
        appendLine()
        cls.methods.filter { it.visibility == Visibility.PUBLIC }.forEach { method ->
            appendLine("#### `${method.signature}`")
            appendLine()
            appendLine(method.doc ?: "_No documentation_")
            appendLine()
        }
    }
}
```

### 3. Custom Lint Rules

```kotlin
// Define custom lint rules for a project
class ProjectLintRules : XtcLintRules() {
    override fun rules() = listOf(
        rule("naming/service-suffix") {
            services {
                filter { !it.name.endsWith("Service") }
                error { "Service classes must end with 'Service': ${it.name}" }
            }
        },

        rule("design/no-public-fields") {
            classes {
                flatMap { it.properties }
                filter { it.visibility == Visibility.PUBLIC && it.hasBacking }
                error { "Avoid public fields, use properties: ${it.name}" }
            }
        },

        rule("performance/avoid-string-concat-in-loop") {
            methods {
                filter { method ->
                    method.body?.containsPattern(
                        StringConcatInLoop::class
                    ) == true
                }
                warn { "String concatenation in loop: ${it.name}" }
            }
        }
    )
}
```

### 4. IDE Integration Features

```kotlin
// Find all implementations of an interface
fun findImplementations(iface: XtcInterface, module: XtcModule): List<XtcClass> {
    return module.classes {
        filter { cls ->
            cls.interfaces.any { it.resolves(iface) }
        }
    }
}

// Find all call sites of a method
fun findUsages(method: XtcMethod, module: XtcModule): List<Location> {
    return module.methods {
        flatMap { m -> m.body?.methodCalls ?: emptyList() }
        filter { call -> call.resolves(method) }
        map { call -> call.location }
    }
}

// Suggest refactoring: Extract interface from class
fun suggestInterface(cls: XtcClass): InterfaceProposal {
    val publicMethods = cls.methods.filter { it.visibility == Visibility.PUBLIC }
    return InterfaceProposal(
        name = "I${cls.name}",
        methods = publicMethods.map { it.toAbstract() }
    )
}
```

## Comparison with Alternatives

### vs Direct Compiler API

| Aspect | Kotlin DSL | Direct Compiler API |
|--------|------------|---------------------|
| Type safety | ✅ Compile-time | ❌ Runtime casts |
| Readability | ✅ Declarative | ❌ Imperative |
| Learning curve | ✅ Gradual | ❌ Steep |
| IDE support | ✅ Excellent | ⚠️ Limited |
| Flexibility | ⚠️ API-constrained | ✅ Full access |

### vs Reflection at Runtime

| Aspect | Kotlin DSL | Runtime Reflection |
|--------|------------|-------------------|
| Performance | ✅ Compile-time analysis | ❌ Runtime overhead |
| Static analysis | ✅ Pre-execution | ❌ Requires running code |
| Tooling | ✅ Build integration | ⚠️ Separate tool |

## Implementation Roadmap

### Phase 1: Core Model (2 weeks)
- Define immutable data model types
- Implement adapter layer extraction
- Basic DSL structure

### Phase 2: Query DSL (2 weeks)
- Filter/map/flatMap operations
- Sequence-based lazy evaluation
- Type-safe builders

### Phase 3: Gradle Integration (1 week)
- Extension functions for XtcCompileTask
- Analysis hooks in build lifecycle
- Report generation

### Phase 4: Analysis DSL (2 weeks)
- Pattern matching helpers
- Lint rule framework
- Error/warning reporting

### Phase 5: Generation DSL (2 weeks)
- Template-based code generation
- TypeScript/JSON/Markdown outputs
- Custom generator plugins

## Conclusion

A Kotlin DSL for XTC reflection would provide:

1. **Powerful querying** of XTC code structures
2. **Type-safe analysis** without deep compiler knowledge
3. **Gradle integration** for build-time checks
4. **Extensibility** for custom tools and generators

The DSL builds on the adapter layer approach, providing a developer-friendly facade over the extracted data model. This enables sophisticated tooling without coupling to compiler internals.
