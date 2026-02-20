# XTC Gradle Plugin: Migration to Standalone Language Plugin

## Executive Summary

The current XTC Gradle plugin piggybacks on the Java plugin (`JavaPlugin`/`JavaBasePlugin`) to get source sets, lifecycle tasks, dependency configurations, and toolchain support. While this was expedient, it results in hidden/disabled Java-specific tasks (`jar`, `classes`, `compileJava`, `processResources`), confusion for users, and unnecessary coupling to Java's build model. This plan describes a phased migration to a standalone XTC language plugin that owns its entire lifecycle — mirroring what Gradle's native C++/Swift plugins do — while keeping the familiar `sourceSets`, `assemble`/`build`/`check` lifecycle, and variant-aware dependency management that Gradle users expect.

---

## Table of Contents

1. [Current Architecture Analysis](#1-current-architecture-analysis)
2. [Landscape: How Other Language Plugins Work](#2-landscape-how-other-language-plugins-work)
3. [Pros and Cons of Migration](#3-pros-and-cons-of-migration)
4. [Target Architecture](#4-target-architecture)
5. [Plugin Split Strategy](#5-plugin-split-strategy)
6. [XTC Test Suite Support (xUnit)](#6-xtc-test-suite-support-xunit)
7. [Detailed Migration Phases](#7-detailed-migration-phases)
8. [Risk Analysis and Mitigations](#8-risk-analysis-and-mitigations)
9. [Compatibility and Migration Path](#9-compatibility-and-migration-path)
10. [Task List](#10-task-list)

---

## 1. Current Architecture Analysis

### 1.1 Plugin Structure

The plugin consists of:

- **`XtcPlugin`** — Entry point. Applies `JavaBasePlugin` and inner `XtcProjectPlugin`.
- **`XtcProjectPlugin`** — Creates an `AdhocComponentWithVariants` via `SoftwareComponentFactory`, delegates to `XtcProjectDelegate`.
- **`XtcProjectDelegate`** (949 lines) — The monolithic configuration class. Applies `JavaPlugin`, creates source sets, tasks, configurations, extensions, and wires everything together.
- **Task classes**: `XtcCompileTask`, `XtcRunTask`, `XtcTestTask`, `XtcExtractXdkTask`, `XtcLoadJavaToolsTask`, `XtcVersionTask`.
- **Extension interfaces**: `XtcExtension`, `XtcCompilerExtension`, `XtcRuntimeExtension`, `XtcTestExtension`.
- **Launcher infrastructure**: `ExecutionStrategy` (Direct/Attached/Detached), `ModulePathResolver`, `LauncherOptionsBuilder`.

### 1.2 Java Plugin Dependencies — Complete Inventory

| Dependency | Severity | Current Usage |
|---|---|---|
| `JavaPlugin` | CRITICAL | Explicitly applied in `applyJavaPlugin()` |
| `JavaBasePlugin` | CRITICAL | In `REQUIRED_PLUGINS` set |
| `JavaPluginExtension` | CRITICAL | Access to `SourceSetContainer`, toolchain config |
| `SourceSet` / `SourceSetContainer` | CRITICAL | Iterates source sets, creates per-set tasks/configs |
| `SourceSetOutput` | CRITICAL | Adds output directories for modules and resources |
| `JavaToolchainService` | CRITICAL | Resolves Java executable for forked execution |
| `LifecycleBasePlugin` | CRITICAL | `CHECK_TASK_NAME`, `VERIFICATION_GROUP` constants |
| `DefaultSourceSet` (internal API) | MEDIUM | Cast to get `getDisplayName()` |
| `DefaultSourceDirectorySet` (internal API) | MEDIUM | Base class for `DefaultXtcSourceDirectorySet` |
| `ApplicationPlugin.APPLICATION_GROUP` | LOW | Task group constant for `runXtc` |
| `"classes"` / `"jar"` tasks | HIGH | Wired into lifecycle, then hidden |
| `"processResources"` task | HIGH | XTC compile depends on it |

### 1.3 What the Plugin Currently Does to Java Tasks

```
Hidden/Disabled tasks (exist but invisible to users):
  - jar
  - classes (and testClasses, etc.)
  - compileJava (and all *java tasks)
  - processResources (Java version; XTC creates its own)

Lifecycle task wiring:
  assemble → classes → compileXtc → [processXtcResources, extractXdk, loadJavaTools]
  check → testXtc → [compileXtc, compileTestXtc]
  build → assemble + check
```

### 1.4 Consumer Usage Patterns

**Bootstrap build (xvm3)** — 23 subprojects apply `alias(libs.plugins.xtc)`:
- `lib_ecstasy`, `lib_json`, `lib_crypto`, etc. use `xtcModule`/`xdkJavaTools` dependencies
- Special bootstrap patterns: `javatools_bridge` uses `outputFilename("_native.xtc" to "javatools_bridge.xtc")`, `javatools_turtle` disables `compileXtc` entirely (just provides `mack.x` source)
- `lib_ecstasy` adds custom source dirs: `sourceSets { main { xtc { srcDir(xdkTurtleConsumer) } } }`
- XDK assembly (`xdk/build.gradle.kts`) collects from `configurations.xtcModule`, distributes to `lib/` and `javatools/` dirs
- Inter-project dependencies via `xtcModule(project(":lib_json"))` etc.
- `manualTests` extensively tests run/test features with multiple execution modes, parallel execution, property-based test selection (`-PtestName=...`), and stdout/stderr redirection

**External projects (platform)** — Real-world XTC consumer (11 subprojects):
- Uses published XDK as Maven dependency: `xdkDistribution(libs.xdk)` — resolved from Maven Central or mavenLocal for SNAPSHOTs
- No included builds — pure consumer model (vs. xvm3's composite build)
- Standard `xtcModule` dependencies for inter-module deps
- Source in `src/main/x/`, resources in `src/main/resources/`
- **Minimal modules** (auth, challenge, stub): Just apply `xtc` plugin + `xdkDistribution(libs.xdk)`
- **Complex modules**: `kernel` uses property-based resource templating (`filesMatching("cfg.json")`) and exports a custom `cfgJsonElements` consumable configuration for root project consumption
- **Multi-plugin modules**: `platformUI` combines XTC plugin with gradle-node plugin for Quasar GUI builds, includes built GUI output as XTC resources via `sourceSets { main { resources { srcDir(guiDistDir) } } }`
- Root project has custom `installDist` task: copies from `configurations.xtcModule` into `build/install/platform/lib`

### 1.5 Key Observation

The XTC plugin uses Java's `SourceSet` primarily as an organizational container. It never actually compiles Java code — it hides all Java compilation tasks. The real work is done by `XtcCompileTask` which invokes the XTC compiler (`org.xvm.tool.Compiler`) via `ProcessBuilder` or reflection.

---

## 2. Landscape: How Other Language Plugins Work

### 2.1 Plugin Hierarchy in Gradle

```
LifecycleBasePlugin          → clean, assemble, check, build (task stubs)
  └─ BasePlugin              → archivesName, configurations, archive defaults
      └─ JvmEcosystemPlugin  → JVM variant attributes, SourceSetContainer (empty), resolution rules
          └─ JavaBasePlugin   → JavaPluginExtension, per-set compile/resources/classes tasks,
              │                  toolchain support, JvmToolchainsPlugin, Javadoc/Test task config
              └─ JavaPlugin   → main/test source sets, jar, test, implementation/api configs,
                                 apiElements/runtimeElements outgoing variants, "java" component
```

**Note:** `JvmEcosystemPlugin` is the minimum needed for JVM variant-aware dependency resolution. `JavaBasePlugin` adds per-source-set task creation. For XTC, we need neither — we create our own source sets and tasks, and use custom attributes for dependency resolution.

### 2.2 JVM Language Plugins (Groovy, Scala, Kotlin, Clojure)

**All JVM language plugins follow a Base/Convention split and depend on `JavaBasePlugin` or `JavaPlugin`:**

| Plugin | Base Plugin Applies | Convention Plugin Applies | Source Set Strategy |
|--------|-------------------|--------------------------|-------------------|
| **GroovyBasePlugin** | `JavaBasePlugin` | `GroovyPlugin` applies `JavaPlugin` + `GroovyBasePlugin` | Adds `groovy` SourceDirectorySet extension to each Java SourceSet |
| **ScalaBasePlugin** | `JavaBasePlugin` | `ScalaPlugin` applies `JavaPlugin` + `ScalaBasePlugin` | Adds `scala` SourceDirectorySet extension to each Java SourceSet |
| **KotlinJvmPlugin** | N/A (no separate base) | Directly applies `JavaPlugin` | Adds `kotlin` SourceDirectorySet extension to each Java SourceSet |
| **Android (AGP)** | `JavaBasePlugin` (internally) | Does NOT apply `JavaPlugin` | Own variant model, but still uses JavaBasePlugin for core infra |
| **Clojure (Clojurephant)** | `JavaBasePlugin` | `ClojurePlugin` adds conventions on top | Same Base/Convention split as Groovy/Scala |

**The Base/Convention pattern**: The *base* plugin iterates over all source sets using `sourceSets.configureEach { }` and adds a language-specific `SourceDirectorySet` as an **extension** on each source set, plus a compilation task. The *convention* plugin creates the actual source sets (main, test) by applying `JavaPlugin`. This means the base plugin alone does NOT create any source sets — it just configures whatever sets exist.

**Joint compilation support**: Groovy (GroovyCompile), Scala (via Zinc), and Kotlin all support mixed Java+language compilation. XTC has no Java compilation at all — another reason the Java plugin infrastructure is unnecessary.

**Key insight**: These plugins all produce JVM bytecode and need Java's classpath/module-path infrastructure. XTC does NOT produce JVM bytecode — it produces `.xtc` module binaries. This is a fundamental difference that justifies a standalone approach.

### 2.3 Native Language Plugins (C++, Swift) — The Model to Follow

Gradle's native plugins are the best precedent for XTC because they:
- Do NOT apply any Java plugin
- Apply only `LifecycleBasePlugin` (via `NativeBasePlugin`)
- Define their own component model (`CppLibrary`, `CppApplication`, `SwiftLibrary`, etc.)
- Manage their own source sets via `FileCollection` / `ConfigurableFileCollection`
- Create their own compilation tasks, dependency configurations, and lifecycle wiring
- Split into separate library vs. application plugins
- Use variant-aware dependency management with custom attributes

**NativeBasePlugin architecture:**
```
LifecycleBasePlugin
  └─ NativeBasePlugin     → Component registration, variant creation, assemble wiring
      ├─ CppBasePlugin    → C++ compilation, source conventions
      │   ├─ CppLibraryPlugin     → Shared/static library variants, API headers
      │   └─ CppApplicationPlugin → Executable, install task, run task
      ├─ SwiftBasePlugin  → Swift compilation
      │   ├─ SwiftLibraryPlugin
      │   └─ SwiftApplicationPlugin
      └─ XCTestPlugin     → Test execution (analogous to our xUnit)
```

**What NativeBasePlugin provides:**
- Applies `LifecycleBasePlugin` for lifecycle tasks (assemble, build, check, clean)
- Registers components in the project's component container
- Creates `assemble` tasks for each binary of the main component
- Wires the development binary to the `assemble` lifecycle task
- Creates outgoing configurations with proper attributes for variant selection
- Manages the `implementation` dependency configuration per component

### 2.4 Gradle's Test Suite Pattern

The `jvm-test-suite` plugin provides a modern DSL:
```kotlin
testing {
    suites {
        val test by getting(JvmTestSuite::class) { // default "test" suite
            useJUnitJupiter()
        }
        val integrationTest by registering(JvmTestSuite::class) {
            dependencies { implementation(project()) }
        }
    }
}
```
- Applies `TestSuiteBasePlugin` + `JavaBasePlugin`
- Each suite creates: source set, compile task, Test task, dependency configurations
- Suites auto-wire to `check` lifecycle
- Framework selection: JUnit 4, JUnit Jupiter, TestNG — analogous to XTC's xUnit

### 2.5 Gradle's Application Plugin Pattern

The `application` plugin adds:
- `JavaApplication` extension with `mainClass`, `mainModule`, `applicationDefaultJvmArgs`
- `run` task (type `JavaExec`)
- `startScripts`, `distZip`, `distTar` tasks
- Distribution configuration

For XTC, the equivalent would be `XtcApplication` with `moduleName`, `methodName`, `moduleArgs`, plus `runXtc` task and distribution support.

### 2.6 Publishing Without Java Plugin

`SoftwareComponentFactory.adhoc("xtc")` + `addVariantsFromConfiguration()` allows publishing custom artifacts without the Java plugin. The current XTC plugin already uses this pattern — it's one of the few things that doesn't need to change.

---

## 3. Pros and Cons of Migration

### 3.1 Pros

| Benefit | Description |
|---------|-------------|
| **Clean user experience** | No hidden Java tasks, no confusion about `jar`, `compileJava`, etc. |
| **Correct mental model** | Users see XTC-specific tasks only; `compileXtc` replaces `classes` |
| **No internal API dependencies** | Remove `DefaultSourceSet`, `DefaultSourceDirectorySet` casts |
| **Simpler plugin code** | No `hideAndDisableTask()` hacks, no Java task name gymnastics |
| **Better error messages** | Users won't see Java-related errors for an XTC project |
| **Future-proof** | Not coupled to Java plugin internals that may change across Gradle versions |
| **Plugin split opportunity** | Clean separation into `xtc-base`, `xtc-library`, `xtc-application`, `xtc-test-suite` |
| **Follows Gradle best practices** | Matches the pattern Gradle recommends for non-JVM languages |
| **Smaller plugin footprint** | Less Gradle infrastructure pulled in transitively |

### 3.2 Cons

| Cost | Description |
|------|-------------|
| **Significant implementation effort** | Must reimplement source set management, lifecycle wiring, resource processing |
| **Own SourceSet implementation** | Gradle's `SourceSet` is tied to `org.gradle.api.tasks` (JVM package). Need custom `XtcSourceSet` |
| **Toolchain resolution** | Must find Java executable without `JavaToolchainService` (or apply `JvmToolchainsPlugin` standalone) |
| **Testing required** | All 24+ xvm3 consumers and external projects (platform) must be validated |
| **Learning curve** | Contributors familiar with Java plugin patterns need to learn the new model |
| **Gradle version coupling** | Some APIs (e.g., `ObjectFactory.sourceDirectorySet()`) evolved over versions |
| **Bootstrap complexity** | The xvm3 bootstrap build has intricate inter-project dependencies |
| **Dual maintenance period** | During migration, must support both old and new plugin versions |

### 3.3 Assessment

**The migration is strongly recommended** because:
1. XTC does not produce JVM bytecode — it's conceptually closer to C++/Swift than to Groovy/Scala
2. The current approach of hiding Java tasks is a maintenance burden and source of user confusion
3. The internal API dependencies (`DefaultSourceSet`, `DefaultSourceDirectorySet`) are a Gradle version compatibility risk
4. The native plugin pattern is well-established and provides a clear blueprint
5. The plugin already does most of the heavy lifting independently (custom compiler, custom configs, custom tasks)

---

## 4. Target Architecture

### 4.1 Plugin Hierarchy

```
LifecycleBasePlugin (Gradle built-in: assemble, build, check, clean)
  └─ XtcBasePlugin (NEW)
      │   - Applies LifecycleBasePlugin + BasePlugin
      │   - Creates XtcExtension
      │   - Provides XtcSourceSet container
      │   - Registers per-source-set: compileXtc, processXtcResources tasks
      │   - Creates per-source-set dependency configurations (xtcModule, etc.)
      │   - Creates XDK configurations (xdk, xdkJavaTools, etc.)
      │   - Wires extractXdk, loadJavaTools tasks
      │   - Creates "xtcModules" lifecycle task (replaces "classes")
      │   - Publishes via SoftwareComponentFactory
      │   - Resolves Java executable via JvmToolchainsPlugin (standalone, no Java plugin)
      │
      ├─ XtcLibraryPlugin (NEW — default for most projects)
      │   - Applies XtcBasePlugin
      │   - Creates "main" source set
      │   - Wires assemble → compileXtc
      │   - Creates outgoing xtcModuleProvider configuration
      │
      ├─ XtcApplicationPlugin (NEW — for runnable modules)
      │   - Applies XtcLibraryPlugin
      │   - Creates XtcApplication extension (moduleName, methodName, moduleArgs)
      │   - Creates runXtc task
      │   - Creates distribution tasks (installDist, distZip)
      │
      └─ XtcTestSuitePlugin (NEW — for xUnit testing)
          - Applies XtcBasePlugin
          - Creates "test" source set
          - Creates testing { suites { } } DSL (XtcTestSuite)
          - Creates testXtc task per suite
          - Wires check → testXtc
```

### 4.2 Custom XtcSourceSet

Instead of reusing Java's `SourceSet`, create a purpose-built `XtcSourceSet`:

```java
public interface XtcSourceSet extends Named {
    // XTC source files (*.x)
    SourceDirectorySet getXtc();

    // Resource files
    SourceDirectorySet getResources();

    // Output: compiled .xtc modules
    ConfigurableFileCollection getXtcModuleOutput();

    // Output: processed resources
    ConfigurableFileCollection getResourceOutput();

    // Compile task name: "compileXtc", "compileTestXtc", etc.
    String getCompileTaskName();

    // Process resources task name
    String getProcessResourcesTaskName();

    // Lifecycle task name: "xtcModules", "testXtcModules", etc.
    String getModulesTaskName();
}
```

**Creation via ObjectFactory:**
```java
var xtcSource = objects.sourceDirectorySet("xtc", displayName + " XTC source");
xtcSource.getFilter().include("**/*." + XTC_SOURCE_FILE_EXTENSION);
xtcSource.srcDir("src/" + name + "/x");
```

This avoids all internal API dependencies (`DefaultSourceSet`, `DefaultSourceDirectorySet`, `DefaultTaskDependencyFactory`).

### 4.3 Dependency Configurations

Per source set (e.g., for "main"):

| Configuration | Role | Attributes |
|---|---|---|
| `xtcModule` | Resolvable: incoming .xtc module dependencies | CATEGORY=library, LIBRARY_ELEMENTS=xtc |
| `xtcModuleProvider` | Consumable: outgoing .xtc modules | CATEGORY=library, LIBRARY_ELEMENTS=xtc |
| `xtcCompileOnly` | Resolvable: compile-time-only deps | same |
| `xtcModulePath` | Resolvable: full module path for compilation | extends xtcModule, xtcCompileOnly |

For "test":

| Configuration | Role | Notes |
|---|---|---|
| `xtcModuleTest` | Resolvable: test module dependencies | Automatically includes main output |
| `xtcModuleTestProvider` | Consumable: test .xtc modules | LIBRARY_ELEMENTS=xtc-test |

XDK configurations (project-wide, same as today):
- `xdk` / `xdkDistribution` — Resolve XDK zip
- `xdkContents` — Extracted XDK directory
- `xdkJavaTools` / `xdkJavaToolsProvider` — javatools.jar

### 4.4 Lifecycle Task Wiring

```
assemble
  └─ xtcModules (NEW — replaces "classes")
      └─ compileXtc
          ├─ processXtcResources
          ├─ extractXdk
          └─ loadJavaTools

build
  ├─ assemble
  └─ check
      └─ testXtc
          └─ compileTestXtc
              ├─ processTestXtcResources
              └─ compileXtc (main)
```

### 4.5 Java Toolchain Resolution (Without Java Plugin)

The `JvmToolchainsPlugin` can be applied standalone without JavaPlugin:
```java
project.getPluginManager().apply(JvmToolchainsPlugin.class);
var toolchainService = project.getExtensions().getByType(JavaToolchainService.class);
var launcher = toolchainService.launcherFor(spec ->
    spec.getLanguageVersion().set(JavaLanguageVersion.of(jdkVersion)));
```

This gives us `launcher.get().getExecutablePath()` for forked XTC compiler/runner execution — without pulling in any of the Java compilation infrastructure.

---

## 5. Plugin Split Strategy

### 5.1 Recommended Split

| Plugin ID | Gradle Class | Purpose |
|---|---|---|
| `org.xtclang.xtc-base` | `XtcBasePlugin` | Core infrastructure (source sets, configs, XDK) |
| `org.xtclang.xtc-library` | `XtcLibraryPlugin` | Library module compilation and publishing |
| `org.xtclang.xtc-application` | `XtcApplicationPlugin` | Runnable module support (runXtc, distributions) |
| `org.xtclang.xtc-test-suite` | `XtcTestSuitePlugin` | xUnit test suite support |
| `org.xtclang.xtc` | `XtcPlugin` (facade) | Convenience: applies library + test-suite (backwards compatible) |

### 5.2 Backward Compatibility

The existing `org.xtclang.xtc` plugin ID becomes a convenience facade that applies `xtc-library` + `xtc-test-suite`. This means:
- All existing `build.gradle.kts` files using `alias(libs.plugins.xtc)` continue to work
- New projects can use more specific plugins
- The `platform` project would add `xtc-application` for its kernel module

### 5.3 Consumer DSL (Target State)

**Simple library (most XDK modules):**
```kotlin
plugins {
    alias(libs.plugins.xtc)  // = xtc-library + xtc-test-suite
}

dependencies {
    xtcModule(libs.xdk.ecstasy)
}
```

**Application module (platform kernel):**
```kotlin
plugins {
    alias(libs.plugins.xtc)
    alias(libs.plugins.xtc.application)
}

xtcApplication {
    moduleName = "kernel.xqiz.it"
    methodName = "run"
    moduleArgs("password")
}

dependencies {
    xtcModule(libs.xdk.ecstasy)
    xtcModule(project(":common"))
}
```

**Test suite configuration:**
```kotlin
xtcTesting {
    suites {
        val test by getting {  // default test suite
            useXUnit()  // default, but explicit for clarity
        }
        val integrationTest by registering {
            dependencies {
                xtcModule(project())
            }
        }
    }
}
```

---

## 6. XTC Test Suite Support (xUnit)

### 6.1 Current State

- `XtcTestTask` extends `XtcRunTask`, invokes `org.xvm.tool.TestRunner`
- Wired to `check` lifecycle via `tasks.named(CHECK_TASK_NAME).configure(checkTask -> checkTask.dependsOn(testTask))`
- Configuration via `xtcTest { failOnTestFailure = true; module { moduleName = "..." } }`
- Test source set: `src/test/x/`
- Auto-discovers test modules from compiled output if none configured

### 6.2 Target: XTC Test Suite DSL

Modeled on Gradle's `jvm-test-suite` plugin but adapted for XTC:

```kotlin
xtcTesting {
    suites {
        val test by getting(XtcTestSuite::class) {
            // Source: src/test/x/
            // Auto-configures xUnit framework
            failOnTestFailure = true
            verbose = false
        }
        val integrationTest by registering(XtcTestSuite::class) {
            // Source: src/integrationTest/x/
            dependencies {
                xtcModule(project())
                xtcModule(libs.xdk.xunit.engine)
            }
        }
    }
}
```

**Each suite creates:**
- XtcSourceSet with `src/{suiteName}/x/` convention
- `compile{SuiteName}Xtc` task
- `process{SuiteName}XtcResources` task
- `test{SuiteName}Xtc` task (wired to `check`)
- `xtcModule{SuiteName}` dependency configuration (extends main's output)

### 6.3 Test Reporting

Currently XTC tests produce console output only. Future enhancements:
- Generate JUnit-compatible XML reports (for CI integration)
- HTML test report generation
- Integration with Gradle's `TestReport` task type

---

## 7. Detailed Migration Phases

### Phase 0: Preparation (Non-Breaking)

**Goal:** Reduce Java plugin coupling without changing the public API.

1. **Extract source set logic from `XtcProjectDelegate`** into a new `XtcSourceSetManager` class
2. **Replace `DefaultSourceSet` cast** — use `sourceSet.getName()` + string formatting instead of `((DefaultSourceSet)sourceSet).getDisplayName()`
3. **Replace `ApplicationPlugin.APPLICATION_GROUP` constant** with hardcoded `"application"` string
4. **Create `XtcSourceSet` interface** alongside existing code (don't use it yet)
5. **Add integration tests** for all current functionality as a safety net
6. **Replace `DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet`** with composition over `ObjectFactory.sourceDirectorySet()`

### Phase 1: XtcBasePlugin (Foundation)

**Goal:** Create the standalone base plugin that provides all infrastructure without Java plugin.

1. **Create `XtcBasePlugin`** that applies:
   - `LifecycleBasePlugin` (for clean/assemble/build/check)
   - `BasePlugin` (for archive conventions)
   - `JvmToolchainsPlugin` (for Java executable resolution only)
2. **Implement `XtcSourceSetContainer`** — a `NamedDomainObjectContainer<XtcSourceSet>` created via `ObjectFactory`
3. **Implement `DefaultXtcSourceSet`** using `ObjectFactory.sourceDirectorySet()` — no internal API dependencies
4. **Move source set iteration** from `XtcProjectDelegate.getSourceSets()` (via `JavaPluginExtension`) to the new container
5. **Create per-source-set tasks:**
   - `compileXtc` / `compile{Name}Xtc`
   - `processXtcResources` / `process{Name}XtcResources`
   - `xtcModules` / `{name}XtcModules` (lifecycle task, replaces `classes`)
6. **Create per-source-set dependency configurations:** `xtcModule`, `xtcModuleProvider`, `xtcModuleTest`, etc.
7. **Create XDK configurations:** `xdk`, `xdkDistribution`, `xdkContents`, `xdkJavaTools`, etc.
8. **Register `extractXdk` and `loadJavaTools` tasks**
9. **Wire lifecycle:** `assemble` → `xtcModules` → `compileXtc`
10. **Resolve Java toolchain** via `JvmToolchainsPlugin` + `JavaToolchainService` (no `JavaPluginExtension`)
11. **Create `XtcExtension`** for project-wide configuration
12. **Publish via `SoftwareComponentFactory`** (already done, just move the code)

### Phase 2: XtcLibraryPlugin

**Goal:** Default plugin for XTC library modules.

1. **Create `XtcLibraryPlugin`** that applies `XtcBasePlugin`
2. **Create "main" source set** automatically
3. **Wire `assemble` → `xtcModules`** for the main source set
4. **Configure outgoing `xtcModuleProvider`** with compiled `.xtc` artifact
5. **Ensure inter-project dependencies work:** `xtcModule(project(":lib_json"))` resolves correctly

### Phase 3: XtcApplicationPlugin

**Goal:** Support for runnable XTC modules (replaces current `xtcRun` in the monolithic plugin).

1. **Create `XtcApplicationPlugin`** that applies `XtcLibraryPlugin`
2. **Create `XtcApplication` extension:**
   ```java
   public interface XtcApplication {
       Property<String> getModuleName();
       Property<String> getMethodName();  // default: "run"
       ListProperty<String> getModuleArgs();
       Property<ExecutionMode> getExecutionMode();
       ListProperty<String> getJvmArgs();
   }
   ```
3. **Register `runXtc` task** (type `XtcRunTask`)
4. **Create `installDist` task** that assembles `.xtc` modules and resources into a distribution directory
5. **Support CLI overrides:** `--module=Name --method=main --args=arg1,arg2`

### Phase 4: XtcTestSuitePlugin

**Goal:** Modern test suite support following Gradle's `jvm-test-suite` pattern.

1. **Create `XtcTestSuitePlugin`** that applies `XtcBasePlugin`
2. **Create `XtcTestingExtension`** with `suites` container
3. **Create `XtcTestSuite`** that encapsulates:
   - Source set (`src/test/x/` by default)
   - Compile task
   - Test execution task
   - Dependency configurations
   - `failOnTestFailure` property
4. **Default suite** named `"test"` with xUnit framework
5. **Wire all suites to `check` lifecycle**
6. **Support test filtering** (via module name patterns)

### Phase 5: Facade Plugin and Migration

**Goal:** Wire the convenience `org.xtclang.xtc` plugin and migrate all consumers.

1. **Update `XtcPlugin` (facade)** to apply `XtcLibraryPlugin` + `XtcTestSuitePlugin`
2. **Migrate xvm3 bootstrap build (23 subprojects):**
   - Standard library modules (`lib_json`, `lib_crypto`, etc.) — mostly zero-change via facade plugin
   - `lib_ecstasy` — custom source set: `xtc { srcDir(xdkTurtleConsumer) }` must work with new `XtcSourceSet`
   - `javatools_bridge` — custom `outputFilename("_native.xtc" to "javatools_bridge.xtc")`, must remain supported
   - `javatools_turtle` — disables `compileXtc` task entirely (provides mack.x source only)
   - `xdk` distribution assembly — collects from `configurations.xtcModule`, separates `lib/` vs `javatools/`
   - `manualTests` — extensive source exclusions, multiple custom run tasks, execution mode tests, property-based test selection
   - All version stamping via `tasks.withType<XtcCompileTask>().configureEach { xtcVersion = ... }` must continue working
3. **Migrate platform project (11 subprojects):**
   - Minimal modules (auth, challenge, stub, etc.) — zero-change via facade plugin
   - `kernel` — uses `processResources` task customization and exports `cfgJsonElements` configuration
   - `platformUI` — combines XTC plugin with gradle-node, injects GUI dist as resources
   - Root project `installDist` — copies from `configurations.xtcModule` into distribution dir
   - Add `xtc-application` plugin where `xtcRun` is used
4. **Remove Java plugin application** from `XtcProjectDelegate`
5. **Remove `hideAndDisableTask()` code**
6. **Remove all `JavaPlugin`/`JavaPluginExtension` references**
7. **Update documentation and README**

### Phase 6: Polish and Enhancement

1. **Test reporting** — Generate JUnit-compatible XML from xUnit results
2. **IDE integration** — Ensure IntelliJ/VS Code recognize `.x` source roots
3. **Build cache optimization** — Verify all tasks are properly cacheable
4. **Configuration cache verification** — Full test suite with `--configuration-cache`
5. **Gradle version compatibility testing** — Minimum version through latest

---

## 8. Risk Analysis and Mitigations

### 8.1 High Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking bootstrap build | Build completely fails | Phase 0 adds comprehensive integration tests first |
| SourceSet API incompatibility | Users can't configure source dirs | Custom `XtcSourceSet` has same DSL patterns, just not the Java type |
| Toolchain resolution without Java | Can't find JDK for forked execution | `JvmToolchainsPlugin` works standalone; fallback to `JAVA_HOME` |
| Inter-project dependency resolution | Variant selection breaks | Keep same attributes (`CATEGORY=library`, `LIBRARY_ELEMENTS=xtc`) |

### 8.2 Medium Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| `SourceSetContainer` DSL change | Consumer build scripts break | Facade plugin preserves DSL; `sourceSets { main { xtc { } } }` becomes `xtcSourceSets { main { ... } }` |
| Composite build interactions | Included builds may depend on Java | Test with full xvm3 composite build |
| Published plugin compatibility | External users on old plugin break | Semantic versioning; major version bump for breaking change |

### 8.3 Low Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Task name changes | CI scripts reference old names | Keep same task names: `compileXtc`, `testXtc`, `runXtc` |
| Configuration name changes | Build scripts reference old configs | Keep same config names: `xtcModule`, `xtcModuleTest`, etc. |
| Extension DSL changes | `xtcCompile { }` block changes | Preserve extension names and properties |

---

## 9. Compatibility and Migration Path

### 9.1 What Stays the Same (Zero Migration)

- Task names: `compileXtc`, `compileTestXtc`, `testXtc`, `runXtc`, `extractXdk`, `xtcVersion`
- Configuration names: `xtcModule`, `xtcModuleTest`, `xtcModuleProvider`, `xdk`, `xdkJavaTools`
- Extension names: `xtcCompile`, `xtcRun`, `xtcTest`
- Source directory conventions: `src/main/x/`, `src/test/x/`
- Output directory structure: `build/xtc/{sourceSet}/lib/`, `build/xtc/{sourceSet}/resources/`
- Plugin ID: `org.xtclang.xtc`

### 9.2 What Changes

| Before | After | Migration |
|--------|-------|-----------|
| `sourceSets { main { xtc { ... } } }` | `xtcSourceSets { main { xtc { ... } } }` or `sourceSets { main { ... } }` | Update build scripts |
| Java tasks exist but hidden | No Java tasks at all | No action needed (improvement) |
| `classes` task in lifecycle | `xtcModules` task in lifecycle | Update any explicit `classes` task references |
| Single monolithic plugin | Split into base/library/application/test | Use facade plugin for zero change, or specific plugins for clarity |

### 9.3 Migration Script

A Gradle migration task or build logic could detect old-style usage and print deprecation warnings before the breaking change.

---

## 10. Task List

### Phase 0: Preparation (estimated: foundational, do first)
- [ ] Add comprehensive integration tests for all current plugin behavior
- [ ] Extract `XtcSourceSetManager` from `XtcProjectDelegate`
- [ ] Replace `DefaultSourceSet` internal API cast with public API alternative
- [ ] Replace `DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet` with composition using `ObjectFactory.sourceDirectorySet()`
- [ ] Replace `ApplicationPlugin.APPLICATION_GROUP` constant with string literal
- [ ] Verify all tests pass with current plugin (safety net)

### Phase 1: XtcBasePlugin (estimated: core work)
- [ ] Create `XtcSourceSet` interface
- [ ] Create `DefaultXtcSourceSet` implementation using `ObjectFactory.sourceDirectorySet()`
- [ ] Create `XtcSourceSetContainer` (NamedDomainObjectContainer)
- [ ] Create `XtcBasePlugin` class
- [ ] Apply `LifecycleBasePlugin` + `BasePlugin` + `JvmToolchainsPlugin`
- [ ] Register `XtcExtension` extension
- [ ] Register `XtcSourceSetContainer` as `xtcSourceSets` extension
- [ ] Implement per-source-set configuration creation (`xtcModule`, `xtcModuleProvider`, etc.)
- [ ] Implement per-source-set task creation (`compileXtc`, `processXtcResources`, `xtcModules`)
- [ ] Implement XDK configuration creation
- [ ] Register `extractXdk`, `loadJavaTools`, `xtcVersion` tasks
- [ ] Implement Java toolchain resolution via `JvmToolchainsPlugin`
- [ ] Wire lifecycle tasks: `assemble` → `xtcModules` → `compileXtc`
- [ ] Register `AdhocComponentWithVariants` via `SoftwareComponentFactory`
- [ ] Unit tests for XtcBasePlugin
- [ ] Integration tests: verify source set creation, task wiring, config resolution

### Phase 2: XtcLibraryPlugin
- [ ] Create `XtcLibraryPlugin` that applies `XtcBasePlugin`
- [ ] Auto-create "main" source set
- [ ] Wire `assemble` → main source set's `xtcModules` task
- [ ] Configure outgoing artifact (`xtcModuleProvider`) with directory artifact
- [ ] Integration tests: compile a simple XTC module, inter-project dependency resolution

### Phase 3: XtcApplicationPlugin
- [ ] Create `XtcApplication` extension interface
- [ ] Create `DefaultXtcApplication` implementation
- [ ] Create `XtcApplicationPlugin` that applies `XtcLibraryPlugin`
- [ ] Register `runXtc` task
- [ ] Register `installDist` task (copies compiled modules + deps to install dir)
- [ ] Support CLI overrides (`--module`, `--method`, `--args`)
- [ ] Integration tests: run a simple XTC module

### Phase 4: XtcTestSuitePlugin
- [ ] Create `XtcTestSuite` interface
- [ ] Create `DefaultXtcTestSuite` implementation
- [ ] Create `XtcTestingExtension` with `suites` container
- [ ] Create `XtcTestSuitePlugin` that applies `XtcBasePlugin`
- [ ] Register default "test" suite with `src/test/x/` convention
- [ ] Wire test suites to `check` lifecycle
- [ ] Test source set auto-includes main source set output
- [ ] `failOnTestFailure` support
- [ ] `skipTests` / `skipAllTests` property support
- [ ] Integration tests: compile and run xUnit tests

### Phase 5: Facade and Migration
- [ ] Update `XtcPlugin` facade to apply `XtcLibraryPlugin` + `XtcTestSuitePlugin`
- [ ] Migrate `lib_ecstasy` build (most complex: custom source dirs, turtle consumer)
- [ ] Migrate all other `lib_*` builds
- [ ] Migrate `javatools_bridge` and `javatools_turtle` builds
- [ ] Migrate `xdk` distribution build
- [ ] Migrate `manualTests` build
- [ ] Migrate `platform` project (add `xtc-application` plugin)
- [ ] Remove all `JavaPlugin`/`JavaBasePlugin` references from plugin source
- [ ] Remove `hideAndDisableTask()` code
- [ ] Remove `applyJavaPlugin()` method
- [ ] Full integration test suite: xvm3 bootstrap build end-to-end
- [ ] Full integration test: platform project build

### Phase 6: Polish
- [ ] JUnit-compatible XML test reporting from xUnit
- [ ] IDE source root detection support
- [ ] Build cache verification for all task types
- [ ] Configuration cache full test suite
- [ ] Gradle version compatibility matrix testing (minimum through latest)
- [ ] Update plugin documentation
- [ ] Publish new major version

---

## Appendix A: File-by-File Impact Analysis

| File | Changes Required |
|------|-----------------|
| `XtcPlugin.java` | Replace `JavaBasePlugin` with `XtcBasePlugin`; becomes thin facade |
| `XtcProjectDelegate.java` | Major refactoring — split into `XtcBasePlugin`, `XtcLibraryPlugin`, etc. |
| `XtcCompileTask.java` | Minimal — replace `SourceSet` references with `XtcSourceSet` |
| `XtcRunTask.java` | Minimal — move to application plugin |
| `XtcTestTask.java` | Moderate — wire into test suite infrastructure |
| `XtcLauncherTask.java` | Replace `JavaPluginExtension` toolchain lookup with direct `JavaToolchainService` |
| `XtcSourceDirectorySet.java` | Replace with `XtcSourceSet` interface |
| `DefaultXtcSourceDirectorySet.java` | Rewrite using composition, not inheritance from internal API |
| `XtcExtractXdkTask.java` | No changes |
| `XtcLoadJavaToolsTask.java` | No changes |
| `XtcVersionTask.java` | No changes |
| All extension interfaces | No changes (API preserved) |
| All launcher classes | No changes |

## Appendix B: Gradle API Usage Summary

| API | Available Without Java Plugin? | Notes |
|-----|-------------------------------|-------|
| `LifecycleBasePlugin` | Yes | `org.gradle.language.base.plugins` |
| `BasePlugin` | Yes | `org.gradle.api.plugins` |
| `ObjectFactory.sourceDirectorySet()` | Yes | Creates `SourceDirectorySet` without Java |
| `SoftwareComponentFactory` | Yes | Core API, injectable |
| `JvmToolchainsPlugin` | Yes | Can be applied standalone |
| `JavaToolchainService` | Yes (with JvmToolchainsPlugin) | Resolves JDK launchers |
| `Configuration` / `ConfigurationContainer` | Yes | Core Gradle API |
| `Attribute` / `AttributeContainer` | Yes | Variant-aware resolution |
| `Copy` task type | Yes | Core Gradle API |
| `NamedDomainObjectContainer` | Yes | Core Gradle API |
| `Provider` / `Property` | Yes | Core Gradle API |
