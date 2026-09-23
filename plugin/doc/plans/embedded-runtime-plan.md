# Reusable compiler and runtime for Gradle

Investigation baseline: `master` at `c3e9d641808091910abb018cb40885300ee08001`.
Working branch: `lagergren/embedded-gradle-runtime`.

The [PR submission plan](embedded-runtime-pr-plan.md) defines exact change scopes, dependencies,
extraction steps and validation for eleven proposed PRs. The native-resource integrations are a
separate commit and review scope from the common mechanism, embedding API and Gradle adapter.

The within-build implementation is on this branch and `DIRECT` execution has passed all
21 existing sequential manual-test modules. Five measured runs per mode reduced median elapsed
time from 67.08 seconds ATTACHED to 43.47 seconds DIRECT. A later milestone extends the same
ownership design to keep the host warm across builds.

## Recommendation

Extend the existing `DirectRuntimeBuildService` to own an explicitly closeable embedding session.
Create it lazily for the selected XDK, reuse it for sequential compile/run requests, and close it
when Gradle releases the service. Keep the Java classes and runtime infrastructure warm; create
fresh compilation state and a fresh application container for every request.

This follows `lib_runner`'s existing Container 0 design: reuse the host, register a new task and
child container for each application, and release that child through its control. "Session reuse"
does not mean reusing a completed application's container or keeping its native resources alive.
The [ownership audit](../../../doc/embedding-resource-ownership.md#runner-reuse-and-why-earlier-tests-did-not-establish-cleanup)
distinguishes pre-existing disposal gaps from the branch's lifecycle changes and test coverage.

The embedding API is the required boundary for repeated in-process compilation and execution.
The plugin and any reusable worker must submit requests through that API; they must not repeatedly
call `Launcher.launch(...)`, construct `Runner`/`TestRunner`, or manage connectors themselves.
Missing capabilities must be implemented in embedding, with no fallback to repeated launcher calls.
Embedding owns runtime reuse, request isolation, completion, cancellation, and cleanup. The plugin
owns the session's build lifetime and maps Gradle inputs and results to that API. Keep `ATTACHED`
as the plugin default while proving the new `DIRECT` implementation. The `manualTests` build now
defaults compilation, interpreter execution, and xUnit to `DIRECT`. Its explicit mode smoke tests
retain their selected modes. `runSmallFloatsJit` now also uses DIRECT through the experimental JIT
embedding backend. Use `-PxtcDefaultExecutionMode=ATTACHED` to override the manual-test default, or
`--mode=ATTACHED` on an individual task (including a run task given `--jit`). The root Gradle daemon
enables preview at startup to satisfy the manual tests' existing JVM option.

Validation of this default ran all 21 sequential modules, all 22 parallel-runner modules, the
19 xUnit demo tests, the shared small-float tests on both backends, and the explicit execution-mode
smoke tasks. All 11 embedding lifecycle tests passed without skips. The combined Gradle invocation
also passed on a second run using the stored configuration cache; run tasks executed again while
unchanged compilation and test outputs remained up to date. Formatting checks passed.

Compilation must work without starting an XVM. In particular, compiling the XDK's bootstrap
libraries must not require an already compiled `runner.xtc` or an installed XDK. Lazily starting
the execution runtime only on the first run avoids that dependency cycle.

## Implementation on this branch

- `EmbeddingSupport.create(repository)` creates an owned session and starts its runtime lazily.
  Compilation alone does not start the interpreter. Host-side compilation and run preparation
  are serialized; one embedding session may own a native runtime per implementation classloader.
- Headless startup initializes the runner registry without invoking its HTTP server entry point.
  Each request uses a fresh child container, copied module state, its own console registration,
  and a unique temporary directory unless the caller supplies a root. Versioned repository
  read-through now copies modules before exposing them to mutable request state.
- Sessions and controls keep `close()` for `AutoCloseable` and try-with-resources, plus
  `close(Duration)` for an explicit shutdown budget. The named default is 30 seconds. A monotonic
  deadline shares that budget across cleanup steps instead of restarting it for each wait.
  Runtime teardown cancels owned timers and stops both executors. Closing a request retains
  caller-owned roots and removes its owned temporary root.
- Lifecycle tests exposed a lost wake-up in `Frame.waitForExternalCompletion`: scheduling and
  marking the fiber ready happened in separate completion callbacks. The wake-up callback now
  marks readiness first, following the existing IO-wait pattern. A unit test forces the previously
  failing notification order without threads, sleeps, or installed XDK artifacts.
- Unit tests cover executor shutdown, polymorphic no-argument close, deadline accounting with a
  controlled clock, and versioned repository isolation. Integration tests live in `xdk`, whose
  test task already provisions `installDist`; its installed distribution is now also a declared
  test input. They cover headless startup, reuse, same-name replacement, failures, roots, active
  cancellation, and shutdown followed by a new session.

- Completion now waits for the application's container and descendants to become idle, including
  scheduled work, service fibers, native IO and keep-alive callbacks. Parent and sibling activity
  does not block it. Unhandled background failures invalidate an otherwise successful entry result.
  Container termination cancels owned IO and waits for the worker to finish, not merely for its
  future to become cancelled. Native IO invoked through shared services is assigned to the
  requesting application's container using the logical call chain.
- File compilation reuses the standard compiler's source-tree, linking, resource and emission
  pipeline through a fresh embedding request. `RunRequest` supplies method, string arguments,
  console and directory context. The plugin uses host filesystem semantics with an explicit
  project working directory; it never changes process-wide streams or `user.dir`.
- DIRECT xUnit execution runs `xunit_engine` through the same session, with the test module name,
  version and output directory supplied as request-local injections, matching `TestRunner`.
  The runner's resource provider supplies the request repository, compiler, linker and a masked
  injector for nested test containers. A regression runs a failing test followed by two passing
  executions in one session, checking exit codes, discovery output and separate output directories.
  In-memory compilation now assembles its result before publishing it: compiler-owned ops cannot
  safely be copied into another pool while still carrying compilation-state references.
- Bootstrap execution obtains its runtime modules from declared module dependencies when no
  installed XDK is present. The JSON and metrics tests declare the runner module needed by the
  embedding host. Compilation itself still starts no runtime and needs no runner. Bootstrap
  dependency sets have separate runtime fingerprints; installed-XDK consumers share the same
core-module fingerprint across their requests.
- The build service now owns an executor instance with an embedding session. DIRECT calls are
  serialized inside the service, without imposing that limit on ATTACHED tasks. Runtime identity
  includes jar, core-module and plugin contents; application repositories are fresh per request.
  Implementation classes load from the selected XDK, and the calling thread's context loader is
  restored. Service shutdown closes sessions before their classloaders.
- Interruption stops the host's wait; request cleanup still uses its bounded shutdown budget and
  preserves the interrupt flag. A failed request release prevents further session reuse. Unit
  tests use controlled clocks, latches and explicit completion signals, not timing assertions.
- Running the parallel manual tests through embedding exposed a native file-resolution shortcut
  that ignored the supplied repository and linked against the host's repository. It now uses the
  repository in the handle. A regression loads a dependent module in a nested container, then
  recompiles its dependency and checks the new value through the same session.
- Failure while remote service calls were pending exposed an assertion during shutdown. Fibers
  that had already returned remained registered without a frame to drain. Shutdown now removes
  those terminated fibers; a regression fails a request with a waiting service call, closes it,
  and successfully runs another request through the same session.
- Repeated-build testing exposed heap retention that a single successful run did not catch.
  Empty typed thread-local holders and null entries in `TransientThreadLocal` can retain an
  implementation loader on a Gradle thread. Scope exit now removes those empty bindings while
  preserving nested restoration. The native filesystem watcher also outlived the runtime; it
  now registers asynchronous container cleanup, closes its watch service, and participates in
  bounded termination. Tests inspect thread-local ownership directly and exercise watcher shutdown.

DIRECT also accepts JIT run requests through the same owned session, with a shared JIT Xvm and
a fresh container per invocation. The JIT is incomplete and can generate placeholder method
bodies; this is not general runner parity. See [JIT embedding](../../../doc/jit-embedding.md) for
its supported boundary, failure reporting, shutdown limitations and focused validation.
Custom injector implementations remain unsupported. JVM startup options must already be present on
the Gradle JVM; assertions are enabled on the implementation loader. The manual tests request
`--enable-preview`, so their DIRECT host needs that option too. Cancellation cannot forcibly stop
uncooperative native code: failure to terminate is reported and the session is not reused. General
native-resource ownership now includes the six integrations described in the
[resource audit](../../../doc/embedding-resource-ownership.md), with explicit native-handle cleanup
assertions between requests. Retained-memory behavior under long workloads and fatal-runtime
recovery need further validation. No automatic JIT test dependencies have been added.

The JIT integration passed all 16 embedding lifecycle tests (including five JIT tests) and all
20 plugin tests, with no skips. The unchanged small-float suite passed through DIRECT on both
backends and through ATTACHED with the JIT. All 21 sequential interpreter modules and the 19
xUnit demo tests also passed. Formatting and whitespace checks passed.

### Follow-up work after this branch

The reusable Gradle build service within one build is implemented. The work below is separate
from [keeping the host warm across builds](#later-keep-the-host-warm-across-builds). These are
follow-ups to the validated sequential execution model, not claims that the capabilities already
exist. Prioritize the remaining resource ownership fixes and their validation, then the constant-pool
and metadata project.

1. **Native resource ownership — migrations implemented; shutdown gaps remain.** The
   [resource audit](../../../doc/embedding-resource-ownership.md) originally reproduced retained
   watches and an open file channel after control/session close. The common mechanism, keystore
   fix and six native migrations now have separate commit boundaries. Channels, sockets, individual
   watch subscriptions, HTTP clients, server listeners/exchanges/executors and cancelled callback
   entries participate in owner cleanup. Paused alarms are disposed too.

   HTTP pools are created for the requesting application at first use, rather than attaching to
   the shared runner during injection. Watch directory keys remain until their last owner leaves.
   Native-handle tests retain references and assert cleanup after each control closes while the
   host remains running, then execute a healthy request. Network tests use a test-only provider;
   production network injection policy and incomplete JIT resource support are unchanged.
   The [six integration scopes](embedded-runtime-pr-plan.md#native-resource-integration-scopes)
   identify the exact PR 4b boundary. A
   [second audit](../../../doc/embedding-resource-ownership.md#follow-up-findings-after-the-native-migrations)
   reproduced premature runtime termination status and cancelled tasks retained in the Java timer
   queue. Four subsequent corrections cover those failures, deferred host cleanup and failed/ignored
   native socket handoff, with regressions mapped to the existing PR scopes. Nested-owner retention
   remains open. Longer retained-handle, heap and classloader measurements, watcher
   directory/overflow semantics and broader platform coverage remain follow-ups too.

2. **Explicit constant-pool ownership and metadata reuse — next performance project.** Coordinate
   with the [errs work](#relationship-to-the-errs-branch) before sharing linked definitions or
   TypeInfo. Establish stable definition generations and keep execution state and diagnostics
   request-owned. Then measure reuse of compiler dependencies and, subsequently, prepared
   applications. Completion requires correct same-name/dependency replacement even with unchanged
   timestamps, fresh singleton state, diagnostics delivered to the current request, bounded cache
   retention, and a measured reduction in preparation work. The detailed boundaries are in
   [avoiding repeated metadata work](#avoiding-repeated-metadata-work); the system-definition
   template experiment that showed no benefit remains excluded.

3. **Custom injectors — embedding API capability.** Implement the currently rejected custom
   injector path for hosts that need richer resources than the provided string/list injections.
   Define resource ownership, disposal and supported backend behavior before exposing the API.
   Verify two requests can supply different resources under the same name without sharing state,
   and that startup failure and cancellation release owned resources. Unsupported backend/resource
   combinations must remain explicit.

4. **Parallel DIRECT requests — optional throughput work.** The build service currently serializes
   requests. Audit compiler, constant-pool, native-template and JIT state before relaxing that
   serialization; removing the synchronization alone is insufficient. Validate simultaneous
   projects with identical module names, separate consoles and repositories, dependency replacement,
   and cancellation of one request while another continues. Keep configuration-cache compatibility
   and demonstrate a throughput benefit on independent Gradle tasks before changing the policy.

5. **JIT diagnostics and capability growth — separate experimental backend work.** Route
   code-generation diagnostics to the current request and add an optional mode that fails when
   execution reaches a placeholder method, instead of silently returning its default value.
   Extend dependency linking, resource providers and asynchronous completion semantics before
   enabling JIT xUnit execution. Verify each extension against a known-working subset with
   externally checked results; do not automatically expand the manual JIT suite in CI. Interpreter
   xUnit already works. See the [JIT follow-ups](../../../doc/jit-embedding.md#follow-up-work).

6. **Plugin-wide DIRECT default — later rollout decision.** Keep ATTACHED as the general default
   while manualTests exercises DIRECT. Before changing it, land the native cleanup scope and
   validate additional consumer projects, multiple runtime identities, cancellation, output
   redirection and incompatible JVM-option handling. Repeat real work with configuration-cache
   reuse and preserve the explicit ATTACHED override. Decide from correctness and representative
   workload measurements, including builds where Gradle restores or skips the work.

### Relationship to the `errs` branch

Reviewed `docs/errs.md`, `docs/errs-audit.md` and `docs/errs-integration-plan.md` at
[`lagergren/errs`, `f98b0fe87`](https://github.com/xtclang/xvm/tree/f98b0fe87/docs).
Its constant-pool guards preserve an explicitly bound pool ahead of a constant's own fallback;
they remove null-scope crashes without making ownership explicit. Its diagnostic work makes the
listener request-owned and replays cached TypeInfo diagnostics instead of attaching a host sink to
a shared pool. This branch retains one `ErrorList` per request and does not import that wider API
or ownership redesign. The thread-local changes here concern release and restoration only.

### Performance investigation

The initial warmup measured 67.04 seconds for ATTACHED and 42.00 seconds for DIRECT in
`runSequential`. These are preliminary, not the final benchmark: a later DIRECT build exhausted
the unchanged 2 GB Gradle heap, exposing the lifecycle defects above. Do not present that run as a
stable speedup or compensate merely by increasing the heap.

After fixing retention, the completed comparison on Java 25 used one warmup per mode followed
by five measured runs per mode, alternating their order. All samples used the same Gradle daemon
and reused its configuration cache. Each DIRECT build created one embedding runtime, reused it
for all 21 modules, and closed it at build completion. All 210 measured module executions passed.

| Measurement | ATTACHED | DIRECT |
| --- | --- | --- |
| Median command elapsed time | 67.08 s | 43.47 s |
| Command elapsed range | 65.48–86.62 s | 41.45–47.31 s |
| Median `runSequential` task time | 66.10 s | 42.39 s |
| Task time range | 64.79–85.98 s | 40.66–46.67 s |

This is a 35.2% reduction in median elapsed time (1.54 times the throughput for this fixed
workload). The slow ATTACHED sample is retained in the range, not discarded; this is a local
benchmark with visible variation, not a guarantee for other workloads. Post-build probes after
the DIRECT warmup and final sample found no owned runtime/watcher threads and no retained
`NativeContainer` after a full GC. Those probes ran outside measured time; no forced GC was
inserted between measured runs. They establish bounded-run cleanup, not a general memory proof.

Validation also passed 44 focused/runtime/plugin tests with zero failures, errors or skips,
plus `spotlessCheck`. A separate forced DIRECT source compilation followed by all 21 sequential
runs passed in one build. The execution benchmark excludes compilation; compiler speedup has
not yet been measured.

A separate Java Flight Recorder run showed constant registration/comparison, map growth,
structure copying and TypeInfo construction among the dominant Java CPU/allocation sites.
`ConstantPool.register` appeared in 21.1% of sampled stacks (inclusive CPU samples, not a wall-time
percentage). Small modules still took roughly 1.2–1.6 seconds each in that instrumented run.
With full stack traces, `InterpreterControl.prepareModule` accounted for 40.9% of samples;
type-metadata construction outside that preparation path accounted for another 26.6%.
Runtime fingerprinting accounted for 0.9%. These are sampled Java CPU stacks, not exclusive
wall-clock durations; they exclude waiting and JVM-internal work.
The runtime infrastructure is shared, but request preparation still copies and links modules and
rebuilds metadata. That is the main optimization candidate to measure next; it is not evidence
that mutable application pools can safely be shared.

The workload also contains deliberate delays: `TestFiles` waits twice for one second, and
`TestServices` exercises a half-second timeout followed by asynchronous waits through four seconds.
Those costs remain with a warm runtime. Compilation is not included in the execution timing.

### Avoiding repeated metadata work

The current implementation reuses execution infrastructure, but not a prepared application.
`InterpreterControl.prepareModule` serializes/deserializes the application, asks
`NativeContainer.createFileStructure` to merge the system modules and application into a new
pool, and links dependencies. A read-through `LinkedRepository` also copies loaded modules.
Adopting a `TypeConstant` into another pool clears its TypeInfo, type relations, normalized type
and runtime type handle. A cache attached to the old constants therefore cannot eliminate all
of the next request's work.

There is already useful sharing to build on: `Container.getTemplate` delegates shared types to
the parent, and containers cache class compositions. However, module sharing is not merely
metadata sharing: `getOriginContainer` also delegates singleton ownership. `SingletonConstant`
holds a live object handle, its initializing fiber and a completion future. Marking every
dependency shared or caching an executed application pool would change request isolation.

Proceed in separately measured steps:

1. **Reduce preparation without changing ownership.** Count module copies, links and TypeInfo
   builds by module/pool to distinguish reusable core work from application-specific work. Audit
   the serialization, repository-copy and merge stages for redundant copies. Keep one explicit
   request owner and remove a copy only after verifying source structures and runtime state are
   not shared accidentally. Also investigate local hot paths such as `isSiblingAllowed` calling
   `getFileKind`, which scans library contents even though both Single and Library permit siblings.
   These changes can reduce cost without introducing a new shared type system.
2. **Reuse stable definitions with explicit ownership.** Design a session-owned, read-only linked
   definition layer for an exact dependency generation, using the existing parent delegation
   where its semantics fit. Keep application singletons, runtime handles, injections, diagnostics,
   services and resources request-owned. Metadata involving application types, such as
   `List<MyApplicationType>`, must not be retained by the longer-lived core layer. Simply keeping
   a TypeInfo across a pool change is unsafe: its referenced constants/structures need a stable
   owner too. Reuse compiler dependency definitions through the same mechanism only when the
   compiler's mutation boundary is explicit; ASTs and compilation state remain fresh.
3. **Cache prepared applications only after that boundary is sound.** An embedding session could
   prepare a definition once and instantiate fresh executions from it. Key preparation by module
   and resolved dependency contents, runtime identity, resolution order and relevant options,
   rather than module name or timestamps. Bound retention and discard it on session close.
   This helps repeated runs of one application; the 21 distinct manual modules primarily benefit
   from reusing their common dependency metadata.

Tests should prove fresh singleton state, distinct projects with the same module name, dependency
replacement with unchanged size/timestamp, failed request recovery and diagnostics per request.
Use deterministic preparation/build counts to establish reuse; measure speed and allocation in
benchmarks rather than making timing assertions. This is follow-up design, not an implemented
shared-metadata cache in this branch. It must preserve the explicit-pool and diagnostic ownership
constraints documented on `lagergren/errs`.

### Performance POC and review boundaries

Keep the embedding lifecycle change and preparation optimizations in separate commits within
the same PR. The preparation experiments do not change the compiler's ambient-pool selection rules, share live
application pools, or introduce a global TypeInfo cache.

The first candidate removes work before adding a cache:

- Runtime `FileStructure.linkModules` now skips definitions already present in the target before
  asking the repository to load them. Previously it loaded/copied them, then discarded that work.
  This matters when Gradle's fresh request repository includes the XDK directory: unchanged core
  libraries were loaded again even though the native container had already supplied their definitions.
  Compile-time linking keeps its existing path. A regression test links an embedded definition
  with an empty repository, establishing that no external copy is required.
- `isSiblingAllowed` reads the stored Linked/non-Linked distinction without scanning module/version
  contents to distinguish Single from Library. The old scan could cache an incomplete version list
  during cloning. The existing multi-version round-trip test now checks that the copy is a Library,
  and equality compares effective kinds so inferred and deserialized kinds agree.

The second candidate is a private system-definition template in `NativeContainer`, initialized
once and copied into a fresh pool for each application. It reuses preparation, not live TypeInfo
or application state. Serializing that template is unsuitable: interpreter native-method markings
are transient. Making its in-memory structures read-only exposed a separate fingerprint bug:
`ModuleStructure.markReadOnly` asked fingerprints for concrete versions rather than freezing their
version constraints. The prototype fixes that guard and tests mutation isolation of a mutable copy.
The template still copies/re-registers constants, clears pool-owned TypeInfo, and incurs an initial
template-construction/copy cost. Its value must be established against the first candidate alone.

A temporary JFR event around the base `TypeConstant.buildTypeInfo` implementation counted 36,334
calls for the 21 modules, representing 17,970 distinct type spellings. Of those spellings, 3,481
occurred in more than one pool; several core reflection/function types occurred in 23 pools.
This diagnostic run used an application-directory repository rather than the full Gradle module
path. It establishes repeated work, not a cache-hit estimate: equal printed names do not prove
equal definitions, and the counter includes recursive/partial builds. The instrumentation lives
outside the repository and is excluded from timed candidate binaries.

For the timing comparison, an embedding harness recreates Gradle's exact ordered module path and
fresh request repositories, runs the same 21 modules in one session, and checks every result. Each
batch gets a fresh JVM with the same flags. Rotate candidate order, use warmup batches, and report
all measured ranges. This isolates preparation changes from Gradle configuration and compilation;
confirm the chosen candidate through the real Gradle task afterwards.

The completed POC used one warmup per candidate, then three measured batches per candidate with
rotating order. All 189 measured module executions passed (252 including warmups). The binaries
contained no diagnostic instrumentation. The preparation-only binary uses the same build's
FileStructure with the baseline NativeContainer/ModuleStructure classes, isolating the additional
template change; the combined candidate was also built and tested through Gradle.

| Embedding harness candidate | Median elapsed | Range |
| --- | --- | --- |
| Lifecycle implementation before preparation changes | 39.21 s | 38.88–39.55 s |
| Preparation fixes, fresh request pools | 23.04 s | 22.78–23.31 s |
| Preparation fixes plus system-definition template | 23.17 s | 23.17–23.46 s |

The preparation fixes reduced median elapsed time by 41.2% in this harness. The template showed
no benefit over those fixes; the small median difference is within the observed spread. The
working branch therefore retains only the FileStructure changes and their regression tests.
The NativeContainer template, fingerprint-freeze fix and associated test were removed from the
working code and saved as an experimental patch. They passed the eight embedding lifecycle tests
and their structure tests, but do not earn additional cache state or first-use copying cost here.

**Decision:** retain preparation optimization in the current PR as the separate local commit
`75031a2a4` (`Avoid redundant module preparation during runtime linking`). It changes only
FileStructure and its tests: 9 production lines added, 8 removed, and 32 test lines added.
Do not proceed to a live TypeInfo or prepared-application cache in this POC. Coordinate that work with explicit pool
ownership and the `errs` diagnostic model as a separate project. The duplicated TypeInfo work
remains a valid target, but the inexpensive preparation fixes already deliver a substantial gain
without changing execution-state ownership. Compiler throughput is not measured by this result.

The retained version also passed the real Gradle `runSequential` twice with normal build caching
enabled. The second invocation reused the configuration cache and took 23.809 seconds in the task;
all 21 modules passed, with one runtime creation, 20 reuses and one close. Fifteen active
FileStructure tests and all eight embedding lifecycle tests passed; the six older FileStructure
tests remain explicitly disabled. `spotlessCheck` passed. After the second build, thread and
post-GC heap probes found no owned runtime/watcher threads or retained NativeContainer.

The intended split is:

| Work | Review scope | Ownership dependency |
| --- | --- | --- |
| Embedding POC | Session API, compiler/runtime requests, lifecycle, Gradle service, integration tests | Fresh request pools; no shared metadata cache |
| Preparation commit in the same PR | Retained `FileStructure` optimizations and their regression tests; template excluded after measurement | Independent of embedding lifecycle and pool ownership changes |
| Shared metadata project | Stable definition generations, explicit pool ownership, request-local runtime state, invalidation and diagnostic replay | Coordinate with `lagergren/errs` |
| Prepared application cache | Reuse a resolved application definition while creating fresh execution state | Build on the shared metadata boundary |

The metadata project must distinguish module identity from definition identity: `isShared` currently
accepts a module found in another pool, which does not establish equal dependency contents. TypeInfo
also refers to pool-owned constants/structures and invalidation state; SingletonConstant and
TypeConstant carry runtime handles. Read-only structure flags alone do not separate those lifetimes.
Application-specific generic types must remain scoped to their definition generation, and cached
diagnostics must be replayed to the current request rather than retaining its listener. The compiler
may reuse dependency definitions through that boundary, but its ASTs and mutation/output state must
remain request-owned. Do not make the current PR wait for this wider project.

### Full XDK build comparison

After implementing DIRECT xUnit execution, the full `:xdk:build` was compared in both modes,
including Java tools, XTC libraries, packaging, Java integration tests and the JSON/metrics xUnit
suites. Each mode has three samples per cache condition. Cold-build order alternated between
modes; cache-restoration samples followed a separate cache-populating build per mode.

| Cache condition | ATTACHED median (range) | DIRECT median (range) |
| --- | --- | --- |
| Clean project outputs, build-cache reuse disabled | 88.41 s (76.71–88.75) | 68.88 s (66.55–69.42) |
| Clean project outputs, populated build cache | 4.38 s (4.32–4.71) | 5.84 s (5.54–7.00) |
| Unchanged outputs, up-to-date build | 1.03 s (1.02–1.16) | 1.31 s (1.30–1.51) |

DIRECT reduced median cold-build elapsed time by 22.1%, or 19.53 seconds. It showed no benefit
when Gradle restored or skipped the work. The cache-restoration and up-to-date samples invoked
neither compiler nor embedding runtime; their elapsed differences measure Gradle/build overhead.
These measurements do not demonstrate reuse of a running XVM across separate builds.

All six cold builds executed the same 24 XTC compile tasks, passed the 175 JSON and four metrics
xUnit tests, and passed the XDK Java test task. Its nine embedding tests ran without skips; eight
pre-existing XdkIntegrationTest cases remain disabled. Each cold DIRECT build recorded 26 requests:
24 compilations sharing one compiler session, plus two xUnit executions with distinct bootstrap
dependency fingerprints. All three session entries closed at build completion. The cache-restored
builds restored all 24 compile tasks and the Java test results; up-to-date builds skipped compilation.

Cold means project outputs removed with standalone `clean`, followed by `:xdk:build` with
`--no-build-cache`. It does not mean an empty dependency-download cache or a newly started machine.
All samples used the same Java 25 Gradle daemon, with existing dependency and OS caches, the same
parallelism and JVM settings, and `CI=true` to keep checks read-only. Timings include the build
command's elapsed time and exclude the preceding clean. The configuration cache was enabled
throughout and reused by the unchanged builds; removing generated plugin outputs invalidated it
for the clean builds. Test-worker memory settings were identical in both modes.

Reproduce each mode with `-PxtcDefaultExecutionMode=ATTACHED` or `DIRECT`. Run `clean` separately,
then `:xdk:build --no-build-cache --configuration-cache` for a real rebuild. For cache restoration,
first populate the cache with `:xdk:build --build-cache`, then run standalone `clean` and repeat that
build. A second unchanged invocation measures the up-to-date case. Benchmark logs, task profiles,
commands and numeric results were retained outside the checkout in `embedding-xdk-full-benchmark`.

After this change, all 21 manual modules also passed again through DIRECT: one runtime creation,
20 reuses and one close. Plugin tests and `spotlessCheck` passed. Final thread and post-GC heap
probes of the shared Gradle daemon found no owned XVM/watcher threads, NativeContainer/Runtime
instances or PluginRuntimeClassLoader instances remaining from these builds.

### JVM limits used for measurement

`jcmd VM.flags` confirmed G1 in the measured Gradle daemon, not ZGC. The explicit 2 GiB heap and
512 MiB metaspace caps were added by `afd0464c14` (JIT milestone #515); that diff supplies no measured
reason for those particular values. They are unchanged in this branch.

An explicit `-Xmx` is optional: Java 25 selects a maximum ergonomically when it is absent. ZGC can
resize the committed heap within its limits; its documentation still describes maximum heap size
and allocation headroom as relevant tuning choices. Metaspace has no explicit maximum by default.
Ecstasy TypeInfo objects are ordinary Java heap data, not JVM metaspace, so raising a metaspace cap
does not address the repeated work measured here. See the
[Java 25 ZGC guide](https://docs.oracle.com/en/java/javase/25/gctuning/z-garbage-collector1.html) and
[Java 25 VM options](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).

Changing collectors or memory limits is a separate experiment. Keep them constant while comparing
code changes; neither the API nor the plugin requires these exact values. Normal Gradle builds keep
their configured caches. The preparation harness does not use Gradle task caches at all.

The new nested xUnit integration test exceeded Gradle's default 512 MiB test-worker heap. It passed
all three requests in a diagnostic worker with a 2 GiB limit. Setting `maxHeapSize = null` still
produced a worker command with `-Xmx512m`; it does not opt out of Gradle's default. The XDK test task
therefore uses the configurable `xdkTestMaxHeapSize` property, defaulting to the existing daemon's
2 GiB budget. This changes the integration-test worker only; the Gradle daemon settings above
remain unchanged. Full-build comparisons use the same worker limit in both execution modes.

A standalone six-request probe measured approximately 93 MiB after starting the embedding host,
387–388 MiB after each xUnit request was released, and 464 MiB at completion of the later requests,
using explicit GC only for this diagnostic. The retained baseline stayed level over six requests.
Repeating with a 512 MiB maximum failed during the second execution: repeated full collections
left about 510 MiB occupied. This establishes a live-memory peak, not merely delayed collection.
It does not establish that every retained object is necessary; reducing the warmed metadata
working set remains part of the ownership/metadata investigation. No explicit GC was added to
production code or tests.

## Baseline before this branch

| Area | Current behavior | Consequence |
| --- | --- | --- |
| [Direct runtime service](../../src/main/java/org/xtclang/plugin/runtime/DirectRuntimeBuildService.java) | Caches an isolated classloader and three reflected static methods per runtime fingerprint. | The ownership mechanism exists, but it does not own a running XVM. |
| [Direct executor](../../src/main/java/org/xtclang/plugin/runtime/impl/IsolatedDirectExecutor.java) | Calls `Launcher.launch(...)` for compilation and constructs a new `Runner` or `TestRunner` for execution. | Java classes can stay warm; runtime initialization is repeated. |
| [Interpreter connector](../../../javatools/src/main/java/org/xvm/api/InterpreterConnector.java) | Constructs a new `Runtime` and `NativeContainer`. `join()` polls with an unconditional initial 500 ms sleep. | Each ordinary runner invocation pays initialization and a polling delay. |
| [Embedding support](../../../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | Configured singleton, lazy shared connector, compile APIs, and per-run `Control`. | It has the reuse model we need, but lacks an owned session lifecycle. |
| [Interpreter control](../../../javatools/src/main/java/org/xvm/api/InterpreterControl.java) | Copies/prepares a module, registers a task, and uses futures for completion. | Useful existing isolation and completion machinery to preserve. |
| [Runner module](../../../lib_runner/src/main/x/runner.x) | Creates a lightweight child container per task, supplies console/filesystem resources, and tracks tasks. | A suitable starting point, subject to the compatibility and lifecycle work below. |
| [Sequential tests](../../../manualTests/build.gradle.kts) | `runSequential` selects 21 of the 22 listed modules; `TestAnnotations` is already excluded. | An existing concrete workload; preserve its test set and actual execution order. |

The current direct path avoids CLI `main()` and its `System.exit()`, but that does not provide
the lifecycle and isolation required for repeated calls. It must be replaced by embedding.
The concrete gaps are runtime ownership, global native state, request isolation, and completion
and cleanup guarantees. The existing embedding implementation also needs the fixes below before
it can provide that supported contract.

The older [JavaTools loading plan](javatools-loading-plan.md) predates the current build service.
Its proposed replacement of `loadJavaTools` is already represented in this code; this work should
extend that mechanism, not create another classloader cache alongside it.

## Baseline findings that drove the changes

The findings below describe the original code and design requirements. The implementation status
above distinguishes what is now implemented from the remaining parity and hardening work.

1. **Runtime termination.** The ordinary runner creates interpreter executors for each module.
   `InterpreterConnector.join()` does not shut them down, and the current service only closes
   its URL classloaders. [Runtime.shutdownXVM()](../../../javatools/src/main/java/org/xvm/runtime/Runtime.java)
   exists but has no callers in the inspected main Java sources; it also does not await termination.
   Closing a classloader does not terminate its threads. Add owned shutdown, bounded termination,
   and cleanup on construction failure, cancellation, and ordinary completion.

2. **Headless startup.** `InterpreterControl.createConnector()` currently invokes `runner.run()`.
   That method starts the HTTP/HTTPS server with default ports 8080/8090. An embedded Gradle run
   must initialize the task registry without opening listeners. Preserve the explicit standalone
   web-runner entry point. Starting the interpreter container and calling the registry methods
   directly is the first implementation to verify.

3. **One native runtime per implementation classloader.** Native templates such as
   [xArray](../../../javatools/src/main/java/org/xvm/runtime/template/collections/xArray.java)
   assign mutable static `INSTANCE` fields when a native container is constructed.
   Creating overlapping runtimes in the same loader can overwrite those references. Initially
   serialize session requests and allow only one live native runtime in each isolated loader.
   Distinct XDK/core configurations need distinct loaders. Restore host thread-local constant-pool
   and context-classloader state even when a request fails.

4. **Completion is more than a returned method.** The runner registry currently marks a task
   complete and drops its container when `Container.invoke("run", ())` returns. Prove that
   asynchronous services, timers, deferred console writes, and unhandled background failures are
   accounted for before releasing the request. Do not substitute "main method returned" for
   "application finished" to make the benchmark faster. Define completion and cancellation in
   the embedding API; use container-local completion, not whole-runtime idleness.

5. **Runner capabilities.** Embedding currently invokes only `run()` with no arguments; nonempty
   injections/custom injectors are rejected, and `JitControl` is a stub. Add method/argument
   support with the existing runner's validation and explicit failure/result reporting. For the
   first milestone, reject `DIRECT` with JIT and point callers to the existing `ATTACHED` backend;
   never silently run a JIT request in the interpreter or change `DIRECT` to fork a process.
   `XtcTestTask` also needs its xUnit behavior preserved, not redirected to a plain `run()` call.

6. **Project context and streams.** The current direct DTO carries `projectDir`, but the options
   adapter does not use it. It also carries no stream-redirection contract. Forked execution
   sets the process directory and handles the task's streams. Supply context and output explicitly
   to embedding; do not change JVM-wide `user.dir`, `System.out`, or `System.err`. The existing
   runner provider maps root/home/current directories to one task root, which differs from the
   ordinary OS-backed runner. Preserve the intended Gradle runner semantics and test them;
   avoid choosing a different filesystem solely to make the embedded tests pass.

7. **Compiler feature parity.** `EmbeddingSupport.compile(File, ...)` reads that file into a
   string and delegates to the in-memory compiler. It does not implement the documented directory
   form or the full multi-file/module/resource workflow. The plugin uses multiple source files,
   resources, output naming, version stamping, warnings/strictness, and rebuild options. Expose
   the existing full compiler pipeline through embedding; do not compile every file as an
   independent string or duplicate parsing/linking/emission in the plugin.

8. **Fresh code after compilation.** [DirRepository](../../../javatools/src/main/java/org/xvm/asm/DirRepository.java)
   caches loaded modules and throttles validation for one second. Its change checks use size and
   timestamp. A warm session must immediately observe a successful recompile, including a
   same-name/same-size replacement. Use fresh request repositories or an explicit generation
   change after compilation; do not rely on a sleep or timestamps alone. Keep mutable compiler
   overlays request-local. [LinkedRepository](../../../javatools/src/main/java/org/xvm/asm/LinkedRepository.java)
   already has a read-through copy mechanism worth reusing after checking its versioned path.

9. **Complete runtime identity.** The current key hashes runtime jars but uses only the plugin
   code-source URL and does not include core `.xtc` contents. A live runtime also depends on its
   bootstrap/core modules and backend configuration. Separate immutable runtime identity from
   mutable application/dependency generations. Changing application code must refresh its
   containers/repositories; changing the XDK must retire the relevant runtime safely.

10. **Gradle service ownership and isolation.** The current task stores the service provider as
    `@Internal` but does not call `usesService`; registration has no concurrency limit. Declare
    service usage and serialize direct tasks initially, without unnecessarily serializing forked
    tasks. [PluginRuntimeClassLoader](../../src/main/java/org/xtclang/plugin/runtime/PluginRuntimeClassLoader.java)
    is child-first only for the bridge implementation package. Ensure XVM implementation classes
    resolve from the selected runtime, not an incidental parent copy. Retain shared Gradle/DTO
    classes and the existing exclusion of unaugmented `javatools-jitbridge.jar` templates.

Gradle documents lazy build-service creation, configuration-cache support, `usesService`,
concurrency limits, and closing between the last consumer and build completion. These fit the
first milestone; they do not promise a live service spanning builds.
[Gradle build services](https://docs.gradle.org/current/userguide/build_services.html).

## Proposed ownership and API shape

```text
Gradle build
  DirectRuntimeBuildService
    runtime identity -> isolated implementation loader
      owned embedding session
        compiler: fresh request state + reusable library inputs
        interpreter: one lazy Runtime + NativeContainer + headless coordinator
          request 1 -> fresh application container -> await/close
          request 2 -> fresh application container -> await/close
    close sessions, await termination, then close loaders
```

Add an owned session factory alongside the existing singleton API, preserving existing callers.
The session exposes full compilation and controlled execution, and implements `AutoCloseable`.
The exact names can be settled in the first API change; the required contracts are:

- Compilation accepts the existing compiler option semantics, explicit repositories, console,
  and diagnostics. It returns an unambiguous result and emitted-module information. Reuse the
  compiler pipeline; allocate new ASTs, compiler instances, error lists, and writable build
  repositories for each request. Sharing a session does not mean reusing a completed compiler.
- Execution accepts module identity/repository, entry method, arguments, project/resource context,
  and diagnostics/output. Return an owned `Control` with result, failure, completion, and
  cancellation. A missing result must not turn an exception into success.
- Session close prevents new work, completes or cancels active controls, releases request resources,
  stops runtime infrastructure, and waits for termination. Cleanup must be idempotent, including
  an API call failing halfway through startup. A close failure must not skip the remaining owners.
- The service owns an instance of the isolated executor, rather than calling three static methods.
  Keep XVM-specific objects inside the isolated loader and use the plugin's existing request
  records at the boundary. Reuse the option mapping where appropriate.
- Request failures remain local when recovery is proven. A runtime invariant failure poisons the
  session: fail the operation, retire the owner, and never silently retry a side-effecting run.

The session must also be usable by a plain Java host. That keeps the later persistent worker
from requiring another implementation of compiler or runner semantics.

## Alternatives and why they matter

| Approach | Benefit | Limitation / decision |
| --- | --- | --- |
| Existing build service owning an embedding session | Smallest change to plugin ownership; shares the Gradle JVM and runtime within one build. | Recommended first, after the lifecycle gaps above are fixed. |
| Embedding backend using successive main containers | `InterpreterConnector.join()` clears the main-container field, suggesting a smaller sequential prototype; avoids the runner web module. | An internal embedding implementation option if child-container semantics cause incompatibilities. It still needs shutdown, output/context isolation, fresh repositories, and reliable completion. The plugin must use the same embedding API in either case. |
| One isolated Gradle worker action for the whole sequential batch | Shares a JVM/runtime across the batch while keeping failures outside the Gradle daemon. | Useful fallback/comparison. A single action can own and close one session; separate work items must not assume they share a live Java object. |
| Classloader-isolated action per module | Keeps implementation dependencies apart. | Classloader isolation alone does not supply runtime reuse or resource shutdown. |
| Persistent XVM worker shared across builds | Amortizes initialization across separate invocations and allows a separate JDK/heap. | Recommended direction for the later milestone; needs a host protocol and lifecycle. |
| Static runtime map in the plugin | Appears simple. | Ties live runtime state to Gradle/plugin classloader accidents and bypasses build ownership. Do not use as the cross-build solution. |

Gradle's Worker API can reuse compatible worker JVMs and select separate JVM settings. It is an
alternative execution boundary, not a public guarantee that an embedding session survives every
work item or build. Isolated workers also cannot consume a build-service instance directly;
a batch must own its session inside the worker.
[Worker API](https://docs.gradle.org/current/userguide/worker_api.html),
[service/worker restrictions](https://docs.gradle.org/current/userguide/build_services.html).

## Implementation sequence

1. **Establish the baseline and lifecycle tests.** Capture `ATTACHED` behavior with the current
   sequential workload. Record per-module results, timings, threads, and effective JVM flags.
   The legacy repeated-launcher `DIRECT` path is not a supported reuse baseline.
   Add an explicitly provisioned embedding integration
   fixture. The existing [LspTest](../../../javatools/src/test/java/org/xvm/runtime/LspTest.java)
   is a `main` program, not a JUnit test; adapt its useful cases rather than assuming they already
   run in CI. Its latency loop times only waiting after `run()`, not complete request latency,
   and its pool-growth case prints counts rather than asserting a bound.

2. **Provide owned, headless embedding.** Add the session lifecycle, full file-based compiler
   entry point, and headless coordinator startup. Prove compile/run/failure/cancel/close/reopen
   and same-module replacement. Resolve the asynchronous-completion contract before using
   elapsed time as performance evidence.

3. **Wire compilation and sequential execution through the service.** Convert the isolated
   executor to an owned embedding session, declare service consumption, implement serialized
   requests and explicit context/output, and remove its launcher calls. Keep the existing module
   selection and fail-fast behavior. Run the same `runSequential` modules under opt-in `DIRECT`;
   do not copy or weaken those tests and do not add JIT runs or new automatic CI dependencies.
   Unsupported direct operations must fail explicitly until embedding supports them.

4. **Verify compiler and runner parity.** Reuse the standard compiler pipeline and options
   behind the embedding boundary. Verify nested sources, multi-module linkage, resources,
   warning/error policies, versioned/qualified outputs, and XDK bootstrap compilation. Demonstrate
   compile A -> run A -> edit A -> compile A -> run new A in the same host. The runtime can stay
   warm, while every compilation and application instance starts with fresh mutable state.

5. **Measure and decide.** Benchmark identical workloads and output handling. Only call the
   replacement faster after recording repeatable results, and only call it sound after the
   lifecycle, isolation, and configuration-cache gates pass. Keep unsupported JIT/stream
   combinations explicit until parity is implemented. Runtime lifecycle fixes belong in embedding
   so every Java host receives the same guarantees.

## Acceptance and benchmark plan

Use artifact-producing Gradle dependencies for integration fixtures. Do not add javatools unit
tests that assume an already installed XDK, or hide missing artifacts behind `assumeTrue`.
Keep ordinary unit tests independent of a built XDK; invoke the integration suite explicitly
while developing this feature.

Required checks:

- Plugin direct compile/run/test requests use the embedding API exclusively, including failure
  paths. No launcher, runner, or connector fallback remains in the plugin bridge. Embedding may
  reuse compiler internals; the plugin must not bypass its ownership and request contracts.
- All 21 currently enabled sequential modules complete with the same pass/fail outcomes and order.
  Prove one host JVM, one native runtime initialization, and fresh application containers.
- Valid -> invalid -> valid compilation and success -> exception -> success execution in one session.
  Exercise nonzero returns, assertion failures, wrong/missing entry methods, and arguments.
- Recompile the same module and a dependency immediately; verify changed behavior even with the same
  file size/timestamp. Use different projects with the same module name in the same build.
- Repeat application runs to detect singleton/global leakage. Observe heap, constant-pool size,
  live containers, thread counts, console registrations, and open files after warmup. Repeated
  identical work should stabilize; distinct generic shapes may legitimately grow shared metadata.
- Exercise outstanding services/timers after `run()` returns, asynchronous failures, cancellation,
  and Gradle interruption. No output or background work may leak into the following request.
- Verify project-relative files, caller-owned roots, temporary cleanup, console output, and redirect
  failures. TestFiles and TestReflection are particularly relevant to resource-provider semantics.
- Test service shutdown and a second build in the same Gradle daemon. Verify no remaining owned
  workers/listeners/resources and that runtime classes can become unreachable.
- Run with configuration cache twice: configuration is reused, execution still happens, and the
  second build gets a new session in milestone one. Test two runtime identities and a rebuilt XDK.
- Verify incompatible toolchains/JVM arguments are rejected or deliberately sent to `ATTACHED`.
  A direct invocation cannot change the Gradle JVM's startup configuration.

First prepare the existing consumer build through its normal dependency graph:

```bash
./gradlew :manualTests:compileXtc \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Then compare the same task and compiled outputs:

```bash
./gradlew :manualTests:runSequential --mode=ATTACHED \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 --enable-preview' \
  --configuration-cache --no-build-cache --console=plain --info --profile

./gradlew :manualTests:runSequential --mode=DIRECT \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 --enable-preview' \
  --configuration-cache --no-build-cache --console=plain --info --profile
```

The memory options above reproduce the existing `gradle.properties` settings; this branch does
not introduce heap/metaspace caps. They are repeated because supplying `org.gradle.jvmargs` to add
`--enable-preview` replaces the whole property. They are benchmark conditions, not requirements
of the embedding API. Do not prescribe them as universal tuning values.

`--no-build-cache` is only a validation/benchmark override. It does not disable configuration
caching or force up-to-date tasks to run. Normal builds retain their configured build cache, and
`runSequential` executes without that override. The measured samples must reuse the configuration
cache and verify actual module execution independently of Gradle's task-cache status.

Verify all 21 executions from outcomes/logs on every sample. Keep compilation outside the
execution-only timing and do not globally apply `--rerun-tasks` to that benchmark: it also reruns
upstream builds. For compilation measurements, use a dedicated fixture that forces the compiler
request itself. `manualTests` explicitly sets `rebuild = false`; a Gradle task executing does not
prove source compilation actually happened.

Measure existing attached and the new embedded direct mode. Use at least five measured runs per
variant in alternating order after preparation. Within the embedded host also
repeat a trivial module and repeated compile/run requests to distinguish initial startup from
steady-state requests. Report median and spread, total task time including teardown, first-run
and later-run times, compile time separately, and peak/retained memory and thread counts. Include
an execution-output check so truncated output cannot masquerade as an improvement.

There is a strong reason to expect a speedup, especially for short modules: 21 initial 500 ms
polls represent at least 10.5 seconds spent inside the current joins, as well as 21 runtime
initializations and, in attached mode, 21 JVM starts. Some useful execution overlaps those waits,
so 10.5 seconds is not a promised saving. Embedding also pays module copying/linking and container
creation. Separate the gain from eliminating coarse polling from the gain due to runtime reuse;
if needed, include a fresh-runtime baseline with corrected completion/shutdown. Record an
unsuccessful or noisy speed comparison honestly rather than adding a timing assertion to CI.

## Later: keep the host warm across builds

The first milestone's embedding session API remains the only in-process entry point. The next stage moves its owner into
a small, headless Java worker process and makes the per-build Gradle service a client/lease owner.
The same worker JVM can then handle requests from successive builds; it need not be the Gradle
daemon JVM. Introduce an explicitly named persistent-worker mode rather than making `DIRECT`
secretly fork a process.

```text
Build A service ---- lease A ----\
                                persistent Java worker
Build B service ---- lease B ----/  implementation loader + warm XVM per runtime identity
                                    fresh request/compiler/container state each time
```

1. **Host and discovery.** Start or connect to a compatible local worker on demand. Key discovery
   by host protocol/API version, Java executable and required flags, and XDK/plugin runtime
   contents. Use an atomic startup/discovery mechanism and handshake so simultaneous builds do
   not accidentally attach to an incompatible or half-started process. Use local IPC with explicit
   ownership; the current runner's web server is not the Gradle worker protocol.

2. **Build leases and request isolation.** Each build obtains a lease; every request identifies
   its build/project, module-path generation, working directory, output targets, and resource
   context. Initially queue requests serially per runtime. Build completion releases the lease
   and all build-owned controls, streams, and temporary state while retaining the healthy host
   and reusable core runtime. A disconnected/cancelled client must cancel its work and release
   its lease without stopping another build's work.

3. **Versioning and invalidation.** Never key reuse only by a version string or path. Changed
   runtime jars, bootstrap modules, JVM requirements, or protocol require a new compatible owner;
   retire old owners after active leases finish. Project outputs and dependency changes must
   refresh request repositories immediately. Keep compiler ASTs, diagnostics, application
   singletons, and failed results out of cross-build caches. Gradle remains authoritative for
   task inputs, outputs, up-to-date checks, and build-cache correctness.

4. **Bounded lifetime and recovery.** Define idle timeout, memory/runtime-count limits, an explicit
   stop command, and cleanup of stale discovery records. Rotate idle runtimes when retained
   metadata exceeds a measured budget. Detect a dead/unhealthy worker and start a replacement
   for subsequent requests. Do not automatically replay a run whose side effects may already
   have happened. Shutdown must close all sessions and await threads before process exit.

5. **Extend the evidence.** Benchmark first build, immediate second build, changed-source build,
   and changed-XDK build; show the same worker/runtime IDs for compatible requests and new IDs
   after invalidation. Test different projects, two simultaneous Gradle invocations, interrupted
   clients, worker crashes, and stale module replacements. Measure IPC and retained-memory costs
   as well as time saved. This stage succeeds only if later builds stay correct and measurably
   benefit, not merely because a process remains alive.

A Gradle process-isolated worker is worth comparing for within-build isolation, but do not base
cross-build ownership on undocumented worker/classloader retention. A dedicated host makes the
cross-build lifecycle explicit and also supports reuse by other Java/tooling clients later.
