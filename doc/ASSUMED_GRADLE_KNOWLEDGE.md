# Gradle Mental Model for New Users
*A practical, industry-aligned explanation*

---

## 1. What Gradle Is (in one paragraph)

Gradle is a **build automation tool**. It describes:
- how source code is compiled,
- how dependencies are resolved,
- how artifacts are produced,
- and how tasks are wired together.

It is **declarative by default**, **lazy by design**, and **convention‑driven**, much like Maven — but more flexible.

---

## 2. The Gradle Build Lifecycle (Core Concept)

Every Gradle build runs in **three phases**, always.

### ① Initialization phase
**Purpose:** decide *what* is being built.

What happens:
- Reads `settings.gradle(.kts)`
- Determines which projects participate
- Builds the project graph

What does *not* happen:
- No tasks
- No build scripts executed

Mental model:
> "Which projects exist?"

---

### ② Configuration phase (**critical to understand**)
**Purpose:** decide *how* the build could run.

What happens:
- All `build.gradle(.kts)` files execute
- Plugins are applied
- Tasks are **registered and configured**
- Task dependencies are wired

Important rule:
> **All build scripts run, even if you execute one task.**

This is why Gradle emphasizes:
- lazy configuration
- avoiding heavy logic at top level

Mental model:
> "What *could* run, and how is it connected?"

---

### ③ Execution phase
**Purpose:** actually do the work.

What happens:
- Only requested tasks (and dependencies) execute
- Tasks run in dependency order
- Up‑to‑date tasks are skipped

Mental model:
> "Now do it."

---

## 3. Industry Conventions (Why Gradle Feels Familiar)

Gradle intentionally follows **industry conventions**, mostly inherited from Maven.

### Standard directory layout (Java projects)

```
src/main/java
src/main/resources

src/test/java
src/test/resources
```

Why this matters:
- Tools understand your project without configuration
- IDEs auto-detect sources
- CI systems work out-of-the-box

If you follow conventions:
- Gradle does less work
- Builds are faster
- Less configuration is required

Convention over configuration is a **feature**, not a limitation.

---

## 4. The Base Plugin (Minimal Build Lifecycle)

The `base` plugin provides **intent**, not behavior.

### Lifecycle tasks it defines
- `clean`
- `assemble`
- `check`
- `build`

Default wiring:
```
build
 ├─ assemble
 └─ check
```

By default:
- `assemble` does nothing
- `check` does nothing

Expectation:
> You attach real work to these lifecycle tasks.

Mental model:
> "These tasks describe *what* should exist, not *how*."

---

## 5. The Java Plugin (Strong Conventions)

The `java` plugin builds on `base` and adds **opinionated behavior**.

It assumes:
- You are building JVM bytecode
- You want JARs
- You want tests
- You follow Maven layout

### Tasks it creates (simplified)
```
compileJava
processResources
classes

compileTestJava
processTestResources
testClasses

jar
test
check
assemble
build
```

### Core dependency graph
```
build
 ├─ assemble
 │   └─ jar
 │       └─ classes
 │           ├─ compileJava
 │           └─ processResources
 └─ check
     └─ test
         └─ testClasses
             ├─ compileTestJava
             └─ processTestResources
```

Expectation:
- `build` produces a JAR
- `check` runs tests
- `clean` deletes `build/`

If you fight these assumptions, you must rewire tasks manually.

---

## 6. Source Sets (Foundational Concept)

A **SourceSet** is a *logical build unit*:
- source files
- resources
- compile classpath
- runtime classpath
- generated tasks

It is **not just a folder**.

### Default source sets

| SourceSet | Purpose |
|---------|--------|
| `main` | Production code |
| `test` | Test code |

Each source set automatically creates tasks and outputs.

### Classpath rules
- `main` sees its own dependencies
- `test` sees `main` + test dependencies

Mental model:
> A SourceSet is a **mini build pipeline**.

---

## 7. Artifacts: group:name:version

Gradle and Maven share the **same artifact model**.

An artifact is identified by:
```
group:name:version
```

Example:
```
org.slf4j:slf4j-api:2.0.9
```

Meaning:
- **group** → organization or namespace
- **name** → library or module
- **version** → immutable release

This is *exactly how Maven works*.

---

## 8. Artifact Repositories

Artifacts live in **repositories**.

Common ones:
- Maven Central
- Google Maven
- Company internal repositories

Repositories store:
- compiled JARs
- metadata
- dependency graphs

Gradle:
- downloads artifacts once
- caches them locally
- reuses them across projects

Mental model:
> "A repository is a global package warehouse."

---

## 9. The Gradle Wrapper (Extremely Important)

The **Gradle Wrapper** (`gradlew`) is not optional.

### What it does
- Downloads the **correct Gradle version**
- Ensures **build reproducibility**
- Avoids system Gradle mismatches

Files involved:
```
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.properties
```

### Why industry requires it
- CI systems rely on it
- Developers don't install Gradle manually
- Builds are deterministic

Rule:
> **Always run `./gradlew`, never `gradle`.**

---

## 10. How This Maps to Maven

| Concept | Maven | Gradle |
|------|------|-------|
| Lifecycle | Fixed | Extensible |
| Artifacts | group:artifact:version | group:name:version |
| Repos | Maven repos | Same |
| Convention | Strong | Strong |
| Flexibility | Low | High |

Gradle is not "different" — it is **Maven's model plus programmability**.

---

## 11. Common Beginner Pitfalls

- Doing work during configuration phase
- Ignoring the wrapper
- Fighting conventions
- Replacing lifecycle tasks instead of extending them
- Treating Gradle as a script instead of a model

---

## Final Mental Model

| Layer | Responsibility |
|----|----|
| Gradle core | Lifecycle |
| Wrapper | Reproducibility |
| Base plugin | Build intent |
| Java plugin | JVM conventions |
| Source sets | Code topology |
| Artifacts | Binary identity |
| Repositories | Distribution |

---

## One sentence summary

> Gradle describes *what can happen* during configuration, then executes *only what must happen*, using industry‑standard artifact conventions and reproducible tooling.
