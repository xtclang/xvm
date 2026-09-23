# Experimental JIT execution through embedding

`RunRequest.Backend.JIT` selects the existing Java-targeting JIT through the embedding API.
It does not make the JIT feature-complete. The interpreter remains the default backend and is
the appropriate choice for the general manual-test and xUnit suites.

## What is reused

An `EmbeddingSupport` session creates its JIT `Xvm` on the first JIT request. Subsequent requests
reuse the native type system, generated native classes and template loader. The JIT can reuse
matching application type systems while they remain reachable; it does not promise permanent
application caching. Each request gets a fresh application container, console and injection map.
The application module is copied before linking so that JIT preparation does not mutate the
caller's module. Recompiling the same module name can therefore supply a new definition.

Compilation, interpreter execution and JIT execution use the same session API. Compilation does
not start either execution runtime. The Gradle DIRECT build service owns the session for one
build and closes it at the end. The separate opt-in PERSISTENT mode keeps an interpreter host warm across builds; it currently
rejects JIT requests. Use DIRECT or ATTACHED for the supported JIT subset. See the
[embedding plan](../plugin/doc/plans/embedded-runtime-plan.md#persistent-keep-the-host-warm-across-builds).

For a JIT request, set the final `RunRequest` argument to `RunRequest.Backend.JIT`; existing
constructors retain interpreter behavior. Entry methods may accept no parameters or `String[]`,
and return `void` or `Int`. `Control.result()` preserves the full 64-bit `Int`. Uncaught Ecstasy
exceptions and Java execution failures produce diagnostics and no successful result.

An installed XDK supplies the JIT templates beside `javatools.jar`. Other hosts can use
`EmbeddingSupport.create(repository, pathToJitBridge)` to specify the template JAR or class
directory. Do not add `javatools-jitbridge.jar` to the application's Java classpath: the JIT reads
and augments these templates with generated methods before loading them.

Native resources must be initialized before entry-method discovery builds application TypeInfo.
The embedded connector follows the launcher's order. Reversing that order can cache incomplete
constructor metadata and cause a missing generated `$init` method.

## Incompleteness and failure detection

The JIT deliberately generates placeholder bodies for some classes and methods. In
[`CommonBuilder.generateCode`](../javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java),
`JIT_LIST`, `NO_JIT_LIST` and `NO_JIT_METHODS` control which bodies are generated. Skipped bodies
return default values, sometimes after a diagnostic printed only once. A zero exit code alone
does **not** prove that every intended method ran. Assertions inside a skipped method do not
provide coverage either. Use known-working suites and externally checked results or observable
effects when validating the embedding boundary.

Embedding reports failures that actually occur; it cannot infer that a placeholder body should
have done something else. This change does not alter the JIT's allowlists or repair unrelated
code-generation gaps. Its internal code-generation diagnostics still use the JIT's existing
process diagnostic streams; application console output uses the request's writer.

Other current limits:

- The JIT linker still has incomplete dependency linking. Arbitrary imported modules, xUnit's
  reflection and nested test containers are not supported by this integration. DIRECT xUnit
  execution remains available through the interpreter.
- The supplied resources are console output and request-local String/String-array injections.
  The JIT does not supply the interpreter's filesystem roots, clocks, network services or general
  resource provider. `RunRequest` directory options do not provision JIT filesystem resources.
- Embedded console input is rejected instead of reading the Gradle daemon's terminal. The host
  retains ownership of the output writer.
- The current JIT invocation runs synchronously on its request's Java thread. This is not an
  implementation of the interpreter's asynchronous services and container-idleness semantics.
  Sequential reuse is the validation target; concurrent JIT requests need separate validation.

## Shutdown

Each control owns its execution thread. `join()` waits for that thread; `close()` uses the default
shutdown budget, and `close(Duration)` accepts an override. Closing interrupts the worker and
waits for it to finish. Session shutdown closes outstanding controls before the template loader.
The low-level loader and `Xvm.close()` propagate `IOException`; the session collects shutdown
failures at its lifecycle boundary.

The interpreter's common native-resource ownership mechanism does not establish JIT resource
cleanup. The [resource audit](embedding-resource-ownership.md) records the interpreter channel, socket,
watcher, HTTP and callback integrations and their native-disposal tests.
Broader JIT resource support needs its own ownership integration and cleanup tests. Sequential
requests alone do not prevent resources left by one request from surviving into the next.

Generated loops without interruption checks and uncooperative native code cannot be forcibly
stopped safely inside the host JVM. If the budget expires, close fails, the control continues to
report that its worker is running, and the session refuses further work. The template loader is
not closed underneath a worker that failed to stop. Use a separate process when forced
termination is required; DIRECT is not a containment boundary for arbitrary code.

## Follow-up work

These are separate backend improvements, not prerequisites for the verified sequential subset:

- Route code-generation diagnostics to the current request, including warnings about skipped
  methods, without retaining that request's diagnostic destination in shared metadata.
- Add an optional strict execution mode: invoking a placeholder body must report the method and
  fail the request. Merely generating an unused placeholder should not fail a supported program.
  Test an executed placeholder, an unused placeholder and recovery with a subsequent valid request.
- Extend module dependency linking and request-owned resource providers, including an explicit
  console-input contract. Verify resource isolation and cleanup on failure and cancellation.
- Implement asynchronous completion and cancellation semantics, reflection and nested containers
  before enabling JIT xUnit. Concurrent requests require their own ownership validation; the
  Gradle DIRECT service currently serializes execution.

The [embedding follow-up plan](../plugin/doc/plans/embedded-runtime-plan.md#follow-up-work-after-this-branch)
records priorities, validation criteria, metadata ownership work and the later default-mode decision.
Keep broader JIT test coverage opt-in until the corresponding capabilities are verified.

## Focused validation

The XDK embedding lifecycle tests exercise runtime reuse, fresh static service state, entry
arguments, separate consoles and injections, full-width results, same-name recompilation,
failure recovery and mixed interpreter/JIT execution. Shutdown tests block on explicit latches:
they check cooperative interruption and a zero-budget failure while an uncooperative worker is
known to be blocked. They do not assert elapsed times or depend on sleeps.

The unchanged `TestSmallFloats` module provides existing Float16, Float8e4, Float8e5 and BFloat16
coverage. Run it explicitly through both backends:

```bash
./gradlew :manualTests:runSmallFloats :manualTests:runSmallFloatsJit \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Both tasks default to DIRECT in `manualTests`. To compare JIT execution with the existing
launcher, add `--mode=ATTACHED` immediately after `:manualTests:runSmallFloatsJit`. No JIT manual
suite has been added to the ordinary build/check dependencies. The focused Java integration
tests live in `xdk`, whose test task provisions its own distribution; they do not assume that
`javatools:test` has compiled XTC libraries available.
