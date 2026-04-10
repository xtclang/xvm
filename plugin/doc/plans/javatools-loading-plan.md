# Plan: Stabilize JavaTools Loading And XTC Task Classpaths

## Problem

The Gradle plugin currently models JavaTools loading as a task:

- [`loadJavaTools`](../../../plugin/src/main/java/org/xtclang/plugin/tasks/XtcLoadJavaToolsTask.java)

That task exists because some plugin code needs `javatools.jar` classes visible in the Gradle daemon
classloader for direct/in-process behavior and shared launcher plumbing.

Today it has two costly properties:

1. It is intentionally never up to date.
2. It contributes to unstable task implementation/runtime classpaths for custom XTC tasks across
   successive Gradle invocations in the same CI job.

This is visible most clearly in `manualTests`.

## Evidence

In a CI-like local reproduction:

1. `./gradlew clean`
2. `./gradlew distZip check :plugin:compileJava ... --info`
3. `./gradlew manualTests:runXtc manualTests:runOne ... manualTests:runAllTestTasksParallel --info`

the second invocation does **not** rebuild the whole XDK. The XDK `compileXtc` tasks are already
`UP-TO-DATE`.

The expensive unexpected recompilation is instead:

- `:manualTests:compileXtc`

with Gradle reporting:

```text
Task ':manualTests:compileXtc' is not up-to-date because:
  Class path of task ':manualTests:compileXtc' has changed from 4186fc3f... to 0b73d638...
```

On the immediately following invocation, the same task becomes `UP-TO-DATE` again. That means the
problem is a one-time task-classpath transition after the main build has rebuilt plugin/build-logic
artifacts, not loss of workspace outputs or the local build cache between GitHub Actions steps.

## Why This Happens

### 1. `loadJavaTools` is a side-effect task

[`XtcLoadJavaToolsTask`](../../../plugin/src/main/java/org/xtclang/plugin/tasks/XtcLoadJavaToolsTask.java)
mutates the plugin classloader so `javatools.jar` classes become available in the current Gradle
daemon.

It therefore declares:

```java
getOutputs().upToDateWhen(_ -> false);
```

which means every fresh XTC invocation reruns it.

That behavior is understandable, but it is not a good fit for Gradle's declarative task model:

- it has no durable outputs
- its important effect is process-local
- it is hard to cache or reason about incrementally

### 2. XTC tasks depend on plugin/build-tooling artifacts rebuilt earlier in the same job

All compile, run, and test tasks depend on `loadJavaTools` through
[`XtcProjectDelegate`](../../../plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java).

The custom XTC tasks themselves are implemented by the plugin and build logic, so their task
implementation/runtime classpath fingerprint can change when the earlier main build invocation
rebuilds jars such as:

- [`plugin/build/libs/xtc-plugin-0.4.4-SNAPSHOT.jar`](../../../plugin/build/libs/xtc-plugin-0.4.4-SNAPSHOT.jar)
- [`build-logic/common-plugins/build/libs/common-plugins.jar`](../../../build-logic/common-plugins/build/libs/common-plugins.jar)
- [`build-logic/aggregator/build/libs/aggregator.jar`](../../../build-logic/aggregator/build/libs/aggregator.jar)

That explains why the **first** follow-up invocation can see `manualTests:compileXtc` as stale even
though the XDK module compiles are already up to date.

## Architectural Context

There are two distinct use cases that the current plugin is trying to support with one mechanism.

### External consumer / published plugin path

Example:

- `../xtc-app-template`

In that model, the plugin resolves an XDK distribution or related incoming artifacts and needs a
usable `javatools.jar` source for compiler/runner invocations.

### XVM self-hosting path

Inside this repository, building the XDK itself also needs JavaTools on hand so XTC compile/run/test
tasks can invoke the compiler and runner.

The awkward part is that the current architecture mixes:

- locating JavaTools
- loading JavaTools into the current Gradle daemon classloader
- constructing child JVM launch classpaths for forked execution

Those are not the same concern, and they do not all belong in the task graph.

## What Should Improve

### Near-term direction

Keep forked execution as the normal path, but reduce reliance on daemon classloader mutation.

For forked tasks, `javatools.jar` should primarily be:

- an input file or file collection
- placed on the child JVM classpath

not something that always has to be injected into the plugin's own classloader first.

### Better place for daemon-local loading

If direct/in-process execution still needs daemon-local JavaTools loading, that responsibility would
fit much better in a shared Gradle `BuildService` or similar build-scoped runtime helper than in a
task with:

- no outputs
- `upToDateWhen(false)`
- side effects on the daemon classloader

That would make the behavior:

- once per build service lifecycle
- explicit about being process-local
- less disruptive to normal task up-to-date reasoning

### Separate concerns in code

The plugin should more clearly separate:

1. resolve JavaTools location
2. decide execution mode
3. construct child launcher classpaths
4. load JavaTools into the daemon only when a direct mode actually needs it

## Concrete Next Steps

1. Audit which plugin/task code truly requires JavaTools classes in the daemon classloader.
2. Remove unnecessary daemon-local loading for normal forked compile/run/test flows.
3. Replace `loadJavaTools` task-driven classloader mutation with a build-scoped service or runtime
   helper for direct mode only.
4. Re-check `manualTests:compileXtc` task-classpath stability across:
   - main build invocation
   - immediate follow-up manual-tests invocation
5. Add regression coverage for the one-time stale `manualTests:compileXtc` classpath transition if
   the behavior can be reproduced in a controlled TestKit scenario.

## Expected Outcome

If this is cleaned up properly:

- follow-up Gradle invocations after the main build should stop paying the one-time
  `manualTests:compileXtc` recompilation cost
- XTC task graphs should be easier to reason about
- the plugin should behave more like normal Gradle tasks with stable implementation inputs
- direct mode can remain supported without poisoning every XTC task path
