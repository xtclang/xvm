# Plan: Make XTC Tests First-Class Gradle Test Tasks

## Goal

Make XTC/xUnit tests feel like normal Gradle tests instead of custom launcher tasks that happen to
be wired into `check`.

That means:

- users can rely on normal Gradle test lifecycle expectations
- builds show useful progress and summaries in rich/plain console output
- test results are reportable and aggregatable
- the DSL looks familiar to Java/Gradle users
- `testXtc` remains compatible with the XDK self-hosting build and external consumer builds

## Current State

Today the plugin already does one important thing correctly:

- [`testXtc`](../../../plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java) is wired
  into `check`, so `build` runs XTC tests as part of verification

But from Gradle's point of view, XTC tests are still opaque:

- [`XtcTestTask`](../../../plugin/src/main/java/org/xtclang/plugin/tasks/XtcTestTask.java) extends
  [`XtcRunTask`](../../../plugin/src/main/java/org/xtclang/plugin/tasks/XtcRunTask.java), not
  Gradle's `Test`
- the launcher strategies just run `TestRunner` and stream process output
- Gradle does not receive structured test events
- users do not get native-style per-test progress, counts, XML/HTML reports, or familiar
  `testLogging` behavior

The plugin itself already acknowledges this gap in:

- [`xdk/build.gradle.kts`](../../../xdk/build.gradle.kts)

## Constraints

Any design has to respect these realities:

1. XTC tests are not JVM/JUnit tests.
2. The current execution model uses `javatools` / `TestRunner`, often in a forked process.
3. The plugin must continue to work both:
   - inside this monorepo while self-hosting the XDK
   - as a published third-party Gradle plugin
4. Configuration cache compatibility remains non-negotiable.
5. We should not try to fake Gradle support by scraping console text only.

## Recommended Direction

Do not try to make `XtcTestTask` literally subclass Gradle's `Test`.

That would fight the model instead of aligning with it:

- `Test` assumes JVM test execution and test framework adapters
- XTC tests are a different runtime and reporting system

Instead:

1. Keep XTC execution separate from JVM test execution.
2. Introduce a first-class XTC test task model that produces structured test results.
3. Bridge those structured results into Gradle-style reporting and logging.
4. Evolve the DSL so it looks and behaves like Gradle's testing model where that makes sense.

This is the same practical shape used by many non-JVM testing integrations: native task type,
Gradle lifecycle wiring, Gradle-like reporting surface.

## What A User Would Reasonably Expect

The following are either missing today or only partially present, and a user would reasonably expect
them from a Gradle testing plugin:

1. Familiar test task names and lifecycle behavior
   - `testXtc`
   - additional suite tasks such as `integrationTestXtc`
   - wiring into `check`

2. Familiar test logging controls
   - `testLogging.events(...)`
   - concise default summary
   - optional standard stream visibility
   - optional stack traces / causes / exception formatting

3. Structured reports
   - machine-readable results
   - JUnit XML compatibility for CI tooling
   - HTML summary report

4. Suite model / source-set model
   - more than just `main` + `test`
   - user-defined test suites with separate dependencies and task names

5. Filtering and selection
   - run a subset of modules/classes/groups/tests
   - suite-specific configuration

6. Failure controls
   - `failFast`
   - fail-on-test-failure is already present, but it should fit the broader model

7. CI/tooling friendliness
   - aggregate counts
   - stable report locations
   - predictable skip behavior
   - understandable rich console output

8. IDE/task discoverability
   - obvious task names and groups
   - generated reports that CI/IDEs can consume

## Phased Plan

### Phase 1: Define The Structured Result Contract

Objective:
- stop treating XTC tests as raw stdout/stderr plus exit code

Tasks:
1. Audit what the xUnit runner already writes to the configured output directory.
2. Define the canonical internal result model for XTC tests:
   - suite/module
   - test case
   - status: passed / failed / skipped
   - duration
   - failure message
   - stack/diagnostic details
3. Decide whether to:
   - consume existing xUnit output as-is, or
   - extend `TestRunner` output to emit a cleaner structured format
4. Freeze a stable schema for plugin-side consumption.

Deliverable:
- a documented structured XTC test result format

Notes:
- this is the foundation for every later improvement
- if the result shape is weak, everything above it will stay weak

### Phase 2: Introduce A Reporting Layer In The Plugin

Objective:
- separate "run tests" from "present and report tests"

Tasks:
1. Refactor [`XtcTestTask`](../../../plugin/src/main/java/org/xtclang/plugin/tasks/XtcTestTask.java)
   so execution and result processing are distinct steps.
2. After runner execution, load structured xUnit results from the output directory.
3. Compute task-level summary counts:
   - total
   - passed
   - failed
   - skipped
4. Emit concise lifecycle logging in a Java-like style, for example:
   - `:xdk:lib-json:testXtc > 42 tests completed, 0 failed, 3 skipped`
5. Preserve detailed stdout/stderr as optional diagnostics, not default noise.

Deliverable:
- `testXtc` prints useful Gradle-style summaries without pretending to be a JVM `Test` task

### Phase 3: Add Report Generation

Objective:
- make XTC test results consumable by CI tools and humans

Tasks:
1. Generate JUnit XML from structured XTC results.
2. Generate a simple HTML summary report.
3. Standardize report locations under `build/reports/xtc-tests` and
   `build/test-results/xtc`.
4. Make report paths task inputs/outputs for cache correctness.
5. Ensure failures include report locations in task output.

Deliverable:
- CI-readable XML and human-readable HTML for `testXtc`

Why this matters:
- once JUnit XML exists, a lot of external tooling starts working immediately

### Phase 4: Add Gradle-Like Test Logging DSL

Objective:
- let users configure XTC test output in familiar Gradle terms

Tasks:
1. Introduce an XTC-specific `testLogging` model on the task/extension.
2. Support the subset that maps cleanly:
   - `events("failed", "skipped", "passed")`
   - `showStandardStreams`
   - `showExceptions`
   - `showCauses`
   - `showStackTraces`
   - `exceptionFormat`
3. Define explicit behavior for unsupported JVM-specific concepts.
4. Document parity and non-parity with Gradle `Test`.

Deliverable:
- `xtcTest { testLogging { ... } }` and task-level overrides with familiar semantics

Important:
- mirror Gradle where useful
- do not invent subtly-different names unless there is a strong reason

### Phase 5: Add XTC Test Suite Support

Objective:
- move beyond the single built-in `testXtc` task

Tasks:
1. Define an XTC suite model analogous to Gradle test suites.
2. Allow registering additional suites, for example:
   - `test`
   - `integrationTest`
   - `functionalTest`
3. Back each suite with:
   - an XTC source set
   - dependencies
   - compile task(s)
   - run/test task(s)
   - reports
4. Generate predictable task names:
   - `testXtc`
   - `integrationTestXtc`
   - `functionalTestXtc`
5. Wire selected suites into `check` by convention, with opt-out/opt-in controls.

Deliverable:
- a real multi-suite XTC testing model

Suggested DSL shape:

```kotlin
xtcTesting {
    suites {
        val test by getting
        register("integrationTest") {
            dependencies {
                xtcModule("org.xtclang:lib-test")
            }
        }
    }
}
```

This does not need to be the final syntax, but the intent should match Gradle expectations.

### Phase 6: Add Filtering And Selection

Objective:
- let users run only what they need

Tasks:
1. Support suite/module/class/group/method filters in the DSL.
2. Support command-line properties for ad hoc CI/local selection.
3. Decide whether an analogue to `--tests` is worthwhile and feasible.
4. Keep filtering compatible with structured reporting.

Deliverable:
- predictable partial test execution without custom task proliferation

### Phase 7: Tighten Lifecycle, Caching, And Configuration Cache Behavior

Objective:
- keep the improved test model operationally sound

Tasks:
1. Re-validate task inputs/outputs for all XTC test tasks.
2. Ensure configuration cache compatibility for new reporting/model objects.
3. Re-check interactions with JavaTools loading and launcher strategy selection.
4. Ensure suite-specific tasks remain incremental and cacheable where valid.
5. Add TestKit coverage for:
   - default `testXtc`
   - suite registration
   - XML/HTML generation
   - logging controls
   - `check` lifecycle wiring

Deliverable:
- test infrastructure that is better, not just noisier or more complicated

## DSL Evolution Proposal

### Keep

- `xtcTest { ... }`
- `testXtc`

These already exist and users may rely on them.

### Add

1. `xtcTesting { suites { ... } }`
   - higher-level test suite model
   - similar in spirit to Gradle JVM test suites

2. `reports { ... }`
   - XML / HTML controls

3. `testLogging { ... }`
   - familiar output configuration

4. `filter { ... }`
   - groups / classes / methods / modules

5. `failFast`
   - per-task and/or per-suite

### Keep Compatibility

`xtcTest { ... }` should become the compatibility/default suite view, not a dead-end legacy block.

That means:
- existing builds should keep working
- new suite support should not force an immediate DSL rewrite

## Explicit Non-Goals For The First Iteration

These should not block the first useful delivery:

1. Full binary compatibility with every Gradle `Test` API detail
2. Perfect IDE integration on day one
3. Re-implementing Gradle's internal test engine APIs
4. Parallel fork semantics identical to JVM `Test`
5. Test retries / flaky test management

Those can come later if the result model is strong.

## Recommended Delivery Order

Implement in this order:

1. Structured XTC result model
2. Task-level concise summary logging
3. JUnit XML generation
4. HTML summary generation
5. `testLogging` DSL
6. XTC suite model
7. advanced filtering and fail-fast controls

That order gives immediate value early and avoids premature DSL design on top of weak internals.

## Success Criteria

This effort is successful when:

1. `./gradlew build` shows concise, trustworthy XTC test summaries.
2. CI can consume XTC test XML results like normal test outputs.
3. Users can configure XTC test output with familiar `testLogging`-style controls.
4. Additional XTC test suites can be declared without custom ad hoc task wiring.
5. Existing `testXtc` builds continue to work.

## Open Questions

1. Does the current xUnit engine already produce enough structured output, or should `TestRunner`
   grow a cleaner machine-readable result format?
2. Should XTC suites mirror Gradle's JVM test suite naming exactly, or use a slightly more explicit
   XTC-specific container?
3. Do we want JUnit XML as the primary interchange format, or as a generated compatibility layer
   on top of richer native XTC results?
4. How much of Gradle `TestLogging` should be mirrored exactly versus adapted?

## Immediate Next Step

Before changing task types or DSLs:

1. inspect the actual contents of the xUnit output directory produced by `testXtc`
2. decide whether it is sufficient as the canonical structured result source

That decision determines whether the next work is plugin-side reporting only, or coordinated
plugin + `javatools` + xUnit engine work.
