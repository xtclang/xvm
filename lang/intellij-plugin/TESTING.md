# IntelliJ Plugin Testing Infrastructure

Comprehensive reference for testing the Ecstasy IntelliJ plugin (`lang/intellij-plugin`),
covering headless CI testing, the IntelliJ Platform Test Framework, the Starter+Driver
E2E framework, log harvesting, and how each approach maps to the plugin's extension points.

## Table of Contents

- [Current State](#current-state)
- [Testing Approaches Overview](#testing-approaches-overview)
- [1. Unit Tests (What We Have Now)](#1-unit-tests-what-we-have-now)
- [2. IntelliJ Platform Test Framework (Headless)](#2-intellij-platform-test-framework-headless)
- [3. Starter+Driver Framework (E2E)](#3-starterdriver-framework-e2e)
- [4. Can runIde Run Headlessly?](#4-can-runide-run-headlessly)
- [5. Log Harvesting](#5-log-harvesting)
- [6. What to Test in the Ecstasy Plugin](#6-what-to-test-in-the-ecstasy-plugin)
- [7. Build Configuration Changes Required](#7-build-configuration-changes-required)
- [8. CI Pipeline Considerations](#8-ci-pipeline-considerations)
- [9. Development Run Configurations](#9-development-run-configurations)
- [10. Debugging](#10-debugging)
- [11. Plugin Verifier](#11-plugin-verifier)
- [12. Testing with Third-Party Plugin Dependencies](#12-testing-with-third-party-plugin-dependencies)
- [13. Gradle Task Reference](#13-gradle-task-reference)

---

## Current State

The `errs` branch has headless unit/manifest tests under `src/test/kotlin` and the native
Starter+Driver compiler playbook under `src/integrationTest/kotlin`. The current headless run
contains 106 tests. It covers compiler configuration/build models, root
watch ownership, source graphs, lifecycle/process cleanup, edit/move guards, startup and diagnostic
messages, capabilities, manifest wiring and bundled resources.
The eight new compiler-import tests cover accepted-report ownership, cancellation/failure,
delayed watcher completion, overlapping imports and project-owned progress disposal. The progress
owner tests use application-free indicators; shared X260 separately exercises the visible native
Cancel button. X261/X262 cover failed/invalid Gradle output and retry. See the
[shared import receipt](../../docs/errs-integration-plan.md#shared-compiler-import-acceptance-and-remaining-ui-work-2026-10-05).

The native suite uses the shared 267-scenario catalog (X1–X262, CFG1–CFG3, 7a.8/7a.9).
It launches an isolated Community-capable IntelliJ environment with Ultimate disabled. Selected
runs preserve explicit `partial` statuses where LSP4IJ cannot exercise a native feature; neither
passing protocol assertions nor an unselected scenario count as full UI acceptance. The
[manual playbook](../doc/manual-test-plan.md) and
[integration receipt](../../docs/errs-integration-plan.md) are authoritative for current results
and upstream limitations.

Post-rebase acceptance covers the complete catalog across `run-1475359363733913185` and
`run-7075359973250470210`: 265 passes, X254/X257 partial for UP25/UP26, zero IDE errors. The
continuation repairs fixture graph ordering and stale closed dirty buffers; it does not replace
native actions with direct edits. Startup, focus recovery and project lifecycle also pass.
The opt-in UP17 large-file diagnostic is excluded. Original failures and exact counts remain in
the [receipt](../../docs/errs-integration-plan.md#modern-vs-code-baseline-and-complete-rebase-acceptance-2026-10-05).

From the composite root:

```bash
./gradlew :lang:intellij-plugin:test \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache
./gradlew :lang:intellij-plugin:testCompilerPlaybook \
  --tests '*CompilerPlaybookTest.compilerPlaybook' \
  -Plsp.adapter=compiler -PincludeBuildLang=true -PincludeBuildAttachLang=true --no-build-cache
```

Use `-PintellijPlaybookCases=X145,X146,X147,X259` for exact scenario selection. Omit it for the
full implemented catalog. The status bar shows completed/remaining counts and the current case's
short description. The driver can restore focus without moving the mouse; avoid typing into the
fixture while it runs. All waits are bounded and completed edits are not replayed after focus loss.

Unit results are in `build/test-results/test/*.xml`. Native per-case results, IDE failures,
server traces and screenshots are under `build/reports/compiler-playbook/run-*`. A green Gradle
summary alone is insufficient: inspect skipped counts, per-case statuses and `ideFailures`.
`--rerun-tasks --no-build-cache` forces a complete re-execution when required.

The sections below provide testing approaches and examples; consult `build.gradle.kts` and the
version catalog for the active dependencies and task wiring, rather than copying older examples
as current build configuration.

---

## Testing Approaches Overview

| Approach | Headless? | GUI? | What It Tests | Complexity | CI-Ready? |
|---|---|---|---|---|---|
| **Unit tests** (current) | Yes | No | Pure utility logic | Lowest | Yes |
| **Platform test framework** | Yes | No | Extensions, services, PSI, actions | Low–Medium | Yes |
| **`testIde` custom tasks** | Yes | No | Same as above, multi-version | Medium | Yes |
| **Starter+Driver (`testIdeUi`)** | Xvfb on Linux | Yes | Full UI interaction, real IDE | High | Yes (with Xvfb) |
| **`runIde`** | No | Yes | Manual exploratory testing | N/A | No |

---

## 1. Unit Tests (What We Have Now)

Pure JUnit 5 tests that exercise utility/helper methods with no IntelliJ dependencies.
These run instantly (`< 1s`) and require no special infrastructure.

**Good for**: File resolution, path manipulation, provisioning logic, serialization,
configuration parsing — anything that doesn't touch IntelliJ APIs.

**Limitations**: Cannot test extension point registration, IDE services, project model
integration, or anything that requires the IntelliJ application environment.

---

## 2. IntelliJ Platform Test Framework (Headless)

The IntelliJ Platform provides a functional test framework that boots a **real IntelliJ
application environment** entirely in-process, with no GUI. Tests run against real
implementations of the plugin API — extensions are registered, services are available,
the project model works — but UI rendering is stubbed out.

### How It Works

1. The test JVM boots IntelliJ's application container (headlessly)
2. Your plugin's `plugin.xml` extensions are registered
3. A test project/fixture is created (light or heavy)
4. Your test code interacts with real IntelliJ APIs
5. Teardown cleans up the project and application state

### Base Classes

| Base Class | Use Case | Weight |
|---|---|---|
| `BasePlatformTestCase` | General plugin tests without Java PSI | Light |
| `LightPlatformCodeInsightFixtureTestCase` | Code insight (completion, highlighting) without Java | Light |
| `LightJavaCodeInsightFixtureTestCase5` | JUnit 5 + Java PSI (since 2025.1) | Light |
| `HeavyPlatformTestCase` | Multi-module projects, complex project setup | Heavy |
| `CodeInsightFixtureTestCase` | Lower-level fixture control | Light |

**Light tests** share a project instance across the test class (fast, ~100ms each).
**Heavy tests** create a new project for each test (slower, ~1–2s each, but isolated).

### Test Data

Test files (`.x` sources, project configs) go in `src/test/testData/`. The fixture
loads them into the test project:

```kotlin
class XtcCompletionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData"

    fun testCompletionInXFile() {
        myFixture.configureByFile("simple.x")
        myFixture.completeBasic()
        // assert completion items
    }
}
```

### What Can Be Tested

- **Extension registration**: Verify `plugin.xml` extensions are loaded
- **File type recognition**: `.x` files recognized, icons assigned
- **Run configurations**: `XtcRunConfigurationType` registered and creates configs
- **LSP4IJ integration**: Language server descriptor registered
- **TextMate bundle**: Grammar loaded for `.x` files
- **Project wizard**: `XtcNewProjectWizard` appears in New Project dialog
- **Settings/preferences**: Plugin settings pages render and persist

### Example Test

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class XtcPluginRegistrationTest : BasePlatformTestCase() {
    fun testRunConfigurationTypeRegistered() {
        val configType = ConfigurationTypeUtil.findConfigurationType("XtcRunConfiguration")
        assertNotNull("XTC run configuration type should be registered", configType)
        assertEquals("XTC Application", configType.displayName)
    }

    fun testTextMateBundleProviderRegistered() {
        val extensions = TextMateBundleProvider.EP_NAME.extensionList
        val xtcBundle = extensions.filterIsInstance<XtcTextMateBundleProvider>()
        assertFalse("XtcTextMateBundleProvider should be registered", xtcBundle.isEmpty())
    }
}
```

### Key Characteristics

- Runs via standard `./gradlew :lang:intellij-plugin:test`
- No display required — fully headless
- Real IntelliJ internals, not mocks
- First test in a class pays ~2–5s for application bootstrap, subsequent tests are fast
- Requires `testFramework(TestFrameworkType.Platform)` dependency (see section 7)

---

## 3. Starter+Driver Framework (E2E)

For full end-to-end testing where you interact with the actual IDE UI — opening files,
clicking menus, inspecting tool windows.

### Architecture

The Starter+Driver framework uses a **two-process model**:

```
┌───────────────┐     JMX/RMI      ┌───────────────┐
│  Test Process │ ◄──────────────► │  IDE Process  │
│  (JUnit)      │                  │  (IntelliJ)   │
│               │  - Open file     │               │
│  assertions   │  - Click menu    │  plugin.xml   │
│  + driver API │  - Type text     │  extensions   │
│               │  - Read UI tree  │  LSP server   │
└───────────────┘                  └───────────────┘
```

The test process launches a real IDE instance (as a subprocess), then sends commands
via a driver API. The IDE process runs with the plugin installed in its sandbox.

### Gradle Configuration

```kotlin
intellijPlatformTesting {
    testIdeUi {
        register("uiTest") {
            task {
                testClassesDirs = sourceSets["uiTest"].output.classesDirs
                classpath = sourceSets["uiTest"].runtimeClasspath
                useJUnitPlatform()
            }
        }
    }
}
```

### Required Dependency

```kotlin
intellijPlatform {
    testFramework(TestFrameworkType.Starter)
}
```

### Example E2E Test

```kotlin
@ExtendWith(StarterRule::class)
class XtcIdeUiTest {
    @Test
    fun lspServerStartsWhenOpeningXFile(driver: Driver) {
        driver.openProject("/path/to/test-project")
        driver.openFile("src/main.x")

        // Wait for LSP server startup notification
        driver.waitForNotification("Ecstasy Language Server Started", timeout = 30.seconds)

        // Verify hover works
        driver.moveCursorTo(line = 5, column = 10)
        driver.triggerAction("QuickJavaDoc")
        driver.assertPopupContains("module")
    }
}
```

### CI Requirements

- **macOS/Windows**: Works with native display (or headless with virtual display)
- **Linux CI**: Requires `Xvfb` (X Virtual Frame Buffer) for the IDE subprocess:
  ```bash
  apt-get install xvfb
  xvfb-run --auto-servernum ./gradlew :lang:intellij-plugin:uiTest
  ```
- **Docker**: Use a container with Xvfb preinstalled

### When to Use

- Testing real user workflows end-to-end
- Verifying that the LSP server actually starts and serves responses
- Testing the New Project wizard UI flow
- Screenshot-based visual regression testing
- Performance profiling (startup time, indexing time)

### Limitations

- Slow: each test takes 10–30s (IDE startup + shutdown)
- Flaky: UI timing, focus changes, popup positioning
- Infrastructure-heavy: needs display server on CI
- Not suitable for unit-level logic testing

---

## 4. Can `runIde` Run Headlessly?

**No, not meaningfully.** `runIde` launches a full IntelliJ IDEA instance and expects
a display. You could pass `-Djava.awt.headless=true` via JVM args, but most IDE
subsystems (editors, tool windows, dialogs) throw `HeadlessException` when they try
to render.

The one headless IDE task that already exists is `buildSearchableOptions` (line 398 of
`build.gradle.kts`), which launches a constrained headless IDE to index settings pages.
This proves that *some* IDE functionality works headlessly, but it's limited to settings
indexing. It's disabled by default (`-Plsp.buildSearchableOptions=true` to enable) because
it adds ~30–60s to the build.

**For automated testing, use the Platform Test Framework or Starter+Driver instead.**

---

## 5. Log Harvesting

### IDE Sandbox Logs

The IntelliJ sandbox persists on disk at:

```
lang/intellij-plugin/build/idea-sandbox/
├── config/           # IDE settings, disabled_plugins.txt, log-categories.xml
├── plugins/          # Built plugin + dependencies
├── system/           # IDE caches, indices
└── log/
    └── idea.log      # Main IDE log file
```

The `runIde` task logs the path at startup (line 611–613 of `build.gradle.kts`):

```
[runIde]   IDE log:   .../build/idea-sandbox/log/idea.log
[runIde]              tail -f .../build/idea-sandbox/log/idea.log
```

Logs **persist after the IDE exits** — you can always read them post-hoc.

### LSP Server Logs

The LSP server writes to a separate log file:

```
~/.xtc/logs/lsp-server.log
```

This is independent of the IDE sandbox and persists across IDE sessions.

### Test Sandbox Logs

When using the Platform Test Framework, tests get their own sandbox at:

```
lang/intellij-plugin/build/idea-sandbox-test/
└── log/
    └── idea.log      # Test harness log
```

### Harvesting for CI

After any Gradle test or IDE run, archive these paths as CI artifacts:

| Artifact | Path | When |
|---|---|---|
| IDE log | `build/idea-sandbox/log/idea.log` | After `runIde` or `testIdeUi` |
| LSP server log | `~/.xtc/logs/lsp-server.log` | After any LSP session |
| Test results (XML) | `build/test-results/test/` | After `test` |
| Test report (HTML) | `build/reports/tests/test/` | After `test` |
| Test sandbox log | `build/idea-sandbox-test/log/idea.log` | After platform tests |

### Live Monitoring During `runIde`

To watch logs while the IDE is running:

```bash
# IDE log
tail -f lang/intellij-plugin/build/idea-sandbox/log/idea.log

# LSP server log
tail -f ~/.xtc/logs/lsp-server.log

# Both, interleaved
tail -f lang/intellij-plugin/build/idea-sandbox/log/idea.log ~/.xtc/logs/lsp-server.log
```

---

## 6. What to Test in the Ecstasy Plugin

The plugin registers these extension points in `plugin.xml`:

| Extension | Class | What to Test |
|---|---|---|
| `notificationGroup` | — | Notification group "XTC Language Server" exists |
| `newProjectWizard.generator` | `XtcNewProjectWizard` | Wizard registered, generates valid project structure |
| `configurationType` | `XtcRunConfigurationType` | Type registered with ID `XtcRunConfiguration`, factory creates configs |
| `runConfigurationProducer` | `XtcRunConfigurationProducer` | Produces config from `.x` file context |
| `iconProvider` | `XtcIconProvider` | Returns XTC icon for `.x` files |
| `textmate.bundleProvider` | `XtcTextMateBundleProvider` | Bundle registered, grammar loaded for `.x` |
| `lsp4ij:server` | `XtcLanguageServerFactory` | Server descriptor registered, factory creates connection |
| `lsp4ij:fileNamePatternMapping` | — | `*.x` pattern maps to `xtcLanguageServer` |

### Suggested Test Plan

**Tier 1 — Platform Tests (headless, fast, high value)**:

1. **Plugin loads**: `PluginManagerCore.getPlugin("org.xtclang.idea")` is not null
2. **Run configuration type registered**: `ConfigurationTypeUtil.findConfigurationType("XtcRunConfiguration")` exists
3. **Icon provider returns icon for `.x` files**: Create `.x` fixture file, assert icon is non-null
4. **TextMate bundle registered**: `XtcTextMateBundleProvider` in `TextMateBundleProvider.EP_NAME.extensionList`
5. **Run configuration producer**: Given a `.x` file, `XtcRunConfigurationProducer` offers a run config
6. **New project wizard**: `XtcNewProjectWizard` appears in new project wizard generators

**Tier 2 — Platform Tests (headless, medium complexity)**:

7. **LSP4IJ server descriptor**: Verify `xtcLanguageServer` is registered for `*.x` files
8. **Run configuration serialization**: Create, serialize, deserialize a run config, verify fields
9. **TextMate highlighting**: Open `.x` file in fixture, verify syntax highlighting tokens

**Tier 3 — E2E / Starter+Driver (requires display)**:

10. **LSP server starts**: Open `.x` file in real IDE, verify server process spawns
11. **Hover works**: Position cursor on symbol, trigger hover, verify response
12. **Completion works**: Trigger completion in `.x` file, verify items appear
13. **New Project wizard flow**: Walk through wizard steps, verify project created on disk

---

## 7. Build Configuration Changes Required

### For Platform Test Framework (Tier 1 + 2)

Add the test framework dependency in `build.gradle.kts`:

```kotlin
dependencies {
    intellijPlatform {
        // ... existing dependencies ...
        testFramework(TestFrameworkType.Platform)
    }
}
```

Create test data directory:

```
lang/intellij-plugin/src/test/testData/
├── simple.x          # Minimal valid XTC file
├── module.x          # Module declaration
└── ...
```

No changes needed to the `test` task — the IntelliJ Platform Gradle Plugin already
configures it with the correct JVM args and sandbox.

### For Starter+Driver (Tier 3)

Add a separate source set and `testIdeUi` configuration:

```kotlin
sourceSets {
    create("uiTest") {
        kotlin.srcDir("src/uiTest/kotlin")
        resources.srcDir("src/uiTest/resources")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Starter)
    }
    "uiTestImplementation"(platform(libs.junit.bom))
    "uiTestImplementation"(libs.junit.jupiter)
}

intellijPlatformTesting {
    testIdeUi {
        register("uiTest") {
            task {
                testClassesDirs = sourceSets["uiTest"].output.classesDirs
                classpath = sourceSets["uiTest"].runtimeClasspath
                useJUnitPlatform()
            }
        }
    }
}
```

### For Multi-Version Testing

Test against multiple IntelliJ versions with `testIde`:

```kotlin
intellijPlatformTesting {
    testIde {
        register("testAgainst2024_3") {
            version = "2024.3"
            task {
                useJUnitPlatform()
            }
        }
        register("testAgainst2025_1") {
            version = "2025.1"
            task {
                useJUnitPlatform()
            }
        }
    }
}
```

---

## 8. CI Pipeline Considerations

### Standard Test Run (Headless)

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Unit tests + platform tests (no display required)
./gradlew :lang:intellij-plugin:test

# Archive artifacts
mkdir -p artifacts
cp -r lang/intellij-plugin/build/test-results/ artifacts/
cp -r lang/intellij-plugin/build/reports/tests/ artifacts/
cp lang/intellij-plugin/build/idea-sandbox/log/idea.log artifacts/ 2>/dev/null || true
```

### E2E Test Run (Linux CI with Xvfb)

```bash
# Install Xvfb
apt-get install -y xvfb

# Run E2E tests with virtual display
xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" \
    ./gradlew :lang:intellij-plugin:uiTest
```

### GitHub Actions Example

```yaml
test-intellij-plugin:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'corretto'
    - name: Run tests
      run: ./gradlew :lang:intellij-plugin:test
    - name: Archive test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: intellij-plugin-test-results
        path: |
          lang/intellij-plugin/build/test-results/
          lang/intellij-plugin/build/reports/tests/
          lang/intellij-plugin/build/idea-sandbox/log/idea.log
```

### Performance Characteristics

| Test Type | Bootstrap Time | Per-Test Time | Total for 20 Tests |
|---|---|---|---|
| Unit tests (current) | ~0s | ~10ms | < 1s |
| Platform tests (light) | ~3–5s | ~100ms | ~5–7s |
| Platform tests (heavy) | ~3–5s | ~1–2s | ~25–45s |
| Starter+Driver (E2E) | ~15–30s per test | ~10–30s | ~5–10min |

The Platform Test Framework (light fixtures) is the sweet spot: fast enough for CI,
powerful enough to test real plugin integration, no display infrastructure required.

---

## 9. Development Run Configurations

### Why There's No "Run Plugin in IDE" Button

In a wizard-created IntelliJ plugin project, the IDE auto-generates a "Run Plugin" run
configuration. This project doesn't get one because:

1. It's a **composite Gradle build** — the plugin module (`lang/intellij-plugin`) is
   nested inside the `xtc-lang` included build, and IntelliJ doesn't auto-generate
   run configurations for tasks in included builds
2. `.idea/` is gitignored (root `.gitignore` line 74), so any manually created run
   configurations in `.idea/runConfigurations/` aren't shared across clones

### Shared Run Configurations (`.run/` Directory)

The modern solution is the `.run/` directory at the project root. IntelliJ automatically
discovers `*.run.xml` files here and shows them in the toolbar dropdown. Unlike
`.idea/runConfigurations/`, the `.run/` directory is not gitignored and can be committed.

The following shared run configurations are provided:

| File | Name | Gradle Task |
|---|---|---|
| `.run/Run Plugin in IDE.run.xml` | Run Plugin in IDE | `lang:runIntellijPlugin` |
| `.run/Run Plugin Tests.run.xml` | Run Plugin Tests | `lang:intellij-plugin:test` |
| `.run/Build Plugin ZIP.run.xml` | Build Plugin ZIP | `lang:intellij-plugin:buildPlugin` |

After syncing the Gradle project, these appear in the run configuration dropdown in the
IntelliJ toolbar. Select "Run Plugin in IDE" and click the green play button (or
Shift+F10) to launch the development IDE with the plugin installed.

### Task Paths

The `lang:runIntellijPlugin` task is an aggregation task defined in `lang/build.gradle.kts`
(line 132) that depends on `:intellij-plugin:runIde`. This is the same task executed by
`./gradlew lang:runIntellijPlugin` on the command line. The run configuration uses this
path because IntelliJ resolves tasks relative to the root composite build.

---

## 10. Debugging

### Debugging the Plugin (In-IDE)

The "Run Plugin in IDE" run configuration has `GradleScriptDebugEnabled=true`, which
means you can **debug directly**:

1. Set breakpoints in any `lang/intellij-plugin/src/main/kotlin/` file
2. Click the debug button (or Shift+F9) instead of run
3. The development IDE launches with JDWP attached
4. Breakpoints fire when the plugin code executes in the development IDE

This works for all plugin code: extension points, services, actions, settings panels.

### Debugging the LSP Server (Separate Process)

The LSP server runs **out-of-process** as a separate Java process for classloader
isolation (avoids lsp4j version conflicts with LSP4IJ). This means plugin debugging
does NOT debug the LSP server — they're separate JVMs.

To debug the LSP server:

**Option A — LSP4IJ debug port configuration:**

LSP4IJ has built-in debug support per language server. The `JavaProcessCommandBuilder`
automatically adds JDWP flags when a debug port is configured in LSP4IJ's settings
for the `xtcLanguageServer` server ID.

Then attach a Remote JVM Debug configuration to `localhost:<port>` from the outer IDE.

**Option B — Manual JVM debug flags:**

Add JDWP args to the command list in `XtcLspConnectionProvider.init {}`:

```kotlin
commands.addAll(jarIndex, listOf(
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
    // ... other args
))
```

Then attach a Remote JVM Debug configuration to `localhost:5005` from the outer IDE.
Use `suspend=y` instead of `suspend=n` if you need to debug startup.

**Option B — System property toggle:**

A cleaner approach is to check a system property:

```kotlin
if (System.getProperty("xtc.debug.lsp") != null) {
    add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
}
```

Then launch the development IDE with `-Dxtc.debug.lsp=true` in the `runIde` JVM args.

### Debugging Platform Tests

Platform tests run in the same JVM, so standard IntelliJ test debugging works:

1. Open the test file
2. Click the gutter icon next to the test method
3. Select "Debug"

The test JVM boots IntelliJ's application container, registers extensions, and hits
breakpoints in both test code and plugin code.

---

## 11. Plugin Verifier

The IntelliJ Plugin Verifier checks binary compatibility of the plugin against a range
of IDE versions. It catches issues like:

- Using APIs removed in newer IDE versions
- Using APIs not available in the declared `sinceBuild`
- Missing transitive dependencies
- Deprecated API usage that will break in future versions

### Current State

Plugin verifier is commented out in `build.gradle.kts` (line 281):

```kotlin
// pluginVerifier() - only enable when publishing to verify compatibility
```

### Enabling It

```kotlin
intellijPlatform {
    pluginVerifier()
}
```

Then configure the IDE versions to verify against:

```kotlin
intellijPlatform {
    pluginVerification {
        ides {
            recommended()  // Automatically selects recent stable releases
            // Or specify explicitly:
            // ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            // ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
        }
    }
}
```

Run with:

```bash
./gradlew :lang:intellij-plugin:verifyPlugin
```

This is primarily useful before publishing to the JetBrains Marketplace, but can also
be run in CI to catch compatibility regressions early.

---

## 12. Testing with Third-Party Plugin Dependencies

The XTC plugin depends on three bundled/third-party plugins:

| Plugin | Dependency Type | Impact on Testing |
|---|---|---|
| `com.intellij.java` | Bundled | Available in platform test framework automatically |
| `com.intellij.gradle` | Bundled | Available in platform test framework automatically |
| `org.jetbrains.plugins.textmate` | Bundled | Available, but TextMate bundle loading requires sandbox setup |
| `com.redhat.devtools.lsp4ij` (0.19.1) | Third-party | Must be explicitly added to test dependencies |

### LSP4IJ in Tests

LSP4IJ is the most complex dependency. For platform tests, you need it on the test
classpath so that the `lsp4ij:server` and `lsp4ij:fileNamePatternMapping` extensions
in `plugin.xml` resolve correctly.

Add it to the test sandbox:

```kotlin
intellijPlatformTesting {
    testIde {
        register("test") {
            plugins {
                plugin("com.redhat.devtools.lsp4ij", libs.versions.lang.intellij.lsp4ij.get())
            }
        }
    }
}
```

Or for the standard test task, the plugin should already be available since it's declared
in the main `intellijPlatform` dependencies block.

### Testing Without a Real LSP Server

For platform tests that verify extension registration (Tier 1), you don't need a running
LSP server. The tests only check that descriptors are registered, not that the server
responds.

For tests that need LSP responses (Tier 2–3), consider:

1. **Mock server**: Start a lightweight LSP server in the test that responds to
   `initialize`, `textDocument/hover`, etc. with canned responses
2. **Test adapter**: The LSP server has an adapter layer (`lsp.adapter` property) —
   a `mock` adapter could be used in tests
3. **Starter+Driver**: For full E2E, let the real server start (it self-provisions
   its JRE and starts within seconds)

### TextMate Bundle in Tests

The `XtcTextMateBundleProvider` looks for the bundle at `<plugin-dir>/lib/textmate/`.
In tests, the `prepareTestSandbox` task must include the TextMate grammar files. The
`copyTextMateToSandbox` task (which runs for `prepareSandbox`) may need a corresponding
test variant, or the test can set up the fixture path manually.

---

## 13. Gradle Task Reference

All testing and development tasks for the IntelliJ plugin, runnable from the project root:

### Development

| Task | Description |
|---|---|
| `lang:runIntellijPlugin` | Launch development IDE with plugin (aggregation task) |
| `lang:intellij-plugin:runIde` | Same, direct task path |
| `lang:intellij-plugin:buildPlugin` | Build distributable ZIP for manual installation |
| `lang:intellij-plugin:buildSearchableOptions` | Index settings pages (headless IDE, disabled by default) |

### Testing

| Task | Description |
|---|---|
| `lang:intellij-plugin:test` | Run all tests (unit + platform if configured) |
| `lang:intellij-plugin:verifyPlugin` | Check plugin structure and `plugin.xml` validity |
| `lang:intellij-plugin:verifyPluginProjectConfiguration` | Verify Gradle plugin configuration |

### Sandbox Management

| Task | Description |
|---|---|
| `lang:intellij-plugin:prepareSandbox` | Build and install plugin into IDE sandbox |
| `lang:intellij-plugin:prepareTestSandbox` | Prepare sandbox for test execution |
| `lang:intellij-plugin:clean` | Delete sandbox (forces fresh IDE state on next run) |

### Publishing

| Task | Description |
|---|---|
| `lang:intellij-plugin:publishPlugin` | Publish to JetBrains Marketplace (requires `-PenablePublish=true`) |
| `lang:intellij-plugin:signPlugin` | Sign plugin for Marketplace distribution |
| `lang:intellij-plugin:patchPluginXml` | Patch `plugin.xml` with version and changelog |

---

## References

- [IntelliJ Platform SDK — Testing Overview](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [IntelliJ Platform SDK — Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html)
- [IntelliJ Platform SDK — Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)
- [IntelliJ Platform Gradle Plugin — Testing Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html)
- [Integration Tests for Plugin Developers (Feb 2025)](https://blog.jetbrains.com/platform/2025/02/integration-tests-for-plugin-developers-intro-dependencies-and-first-integration-test/)
- [Integration Tests: API Interaction (Mar 2025)](https://blog.jetbrains.com/platform/2025/03/integration-tests-for-plugin-developers-api-interaction/)
- [IntelliJ Platform SDK — IDE Development Instance](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html)


### L82 refresh and VFS regression selection

X146 additionally keeps 16 extra documents connected and schedules 64 lens, hint and semantic-token
refreshes each while a write action delays their read passes. The test temporarily raises the sandbox
IDE tab limit and restores it afterward. The untouched consumer must still show Int, then String,
then Int hints. X259 covers hint settings and connection retirement during restart. Hints stay enabled.

For the UP03/UP27 repair, the focused selection is X103/X118/X130/X146/X161/X162/X163/X169/
X173/X174/X175/X176/X216/X217/X218/X259. `run-11532602109763278599` passes all 16 plus START,
with zero IDE errors. The 172-case continuation `run-13878115092192383073` subsequently passes
with zero IDE errors/freeze dumps (two known partial cases). Together with the first segment it
covers all 264 scenarios, but a corrected X185 popup-harness failure means this is not one
uninterrupted clean run. Keep the full-suite IDE-error gate; do not suppress upstream alerts. `PreflightedRenamesTest` also tests the production launcher with an unset
optional class loader, preventing the startup regression seen in `run-2406379153883211212`.
See the [repair receipt and extraction map](../../docs/errs-integration-plan.md#l82-intellij-refresh-and-vfs-repair-2026-10-05).


Compiler workspace synchronization now has shared X263–X265 cases for nested composite input
aggregation, automatic Gradle refresh/export watching, and project/folder closure during import.
Both clients preserve explicit settings and the last accepted inputs after failed refresh.
X129/X260–X265 now have passing selected receipts in both editors: VS Code `run-40itia`;
IntelliJ `run-12471400462423684800` plus corrected `run-11279722985432501847` (zero IDE
errors in the correction). Headless/client checks pass: 112 IntelliJ units, 37 VS Code extension
tests, three Gradle model tests and five backend build-model tests. Late daemon publication after
cancellation is guarded by import identity; the configuration cache is reused across identities.
Automatic exports require an initial imported report; VS Code uses public Gradle task events in
trusted local workspaces. The catalog has 270 cases; this was a selected run, not a full rerun.
See the [contract, failures and commit map](../../docs/errs-integration-plan.md#compiler-workspace-synchronization-batch-ui5ui6-2026-10-05).
