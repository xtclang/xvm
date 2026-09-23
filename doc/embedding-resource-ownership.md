# Embedding resource ownership audit

Audit of `lagergren/embedded-gradle-runtime` at `c884779d6`, based on master `6539aa6eb`.
The initial audit added tests and recorded missing cleanup. The keystore fix is committed as
`ca52e2aae`; the common mechanism and initial audit are committed as `9b51e0f23`. Native resource
integrations now form a separate change, **Release application-owned native resources between
embedded runs**, so their submission boundary remains independent of the Gradle adapter.

The six requested integrations are implemented: file channels, sockets, individual watcher
subscriptions, HTTP clients, HTTP servers/exchanges, and cancelled callbacks. The release boundary
is each application's `Control.close()` while its runner host remains alive. Validation below
identifies what is exercised; this is not a claim of JIT resource parity or arbitrary native-code
termination.

The follow-up audit of `6fea82130` found additional gaps. The four requested corrections now cover
runtime termination status, cancelled timer queue entries, deferred host cleanup and failed/ignored
native socket handoff. Nested-container reachability remains an open source-level concern. See
[Follow-up findings after the native migrations](#follow-up-findings-after-the-native-migrations)
for the historical evidence, corrections and remaining boundaries.

## Follow-up implementation

[`OwnedResource`](../javatools/src/main/java/org/xvm/runtime/OwnedResource.java) and
`Container.acquireResource()` now reserve ownership **before** calling an acquisition factory.
If shutdown races with acquisition, the owner waits for the factory and cleanup, and the resource
is not delivered to the application. Factories must clean up partial allocations when they throw.
Acquisition is rejected without invoking the factory once termination has started.

Explicit `closeAsync()` and owner termination initiate the same cleanup once. In-progress cleanup
remains registered; completion removes the registration and its resource/owner/cleanup references.
Cleanup failures are reported both to the explicit closer and to owner shutdown. Completing or
cancelling a returned future cannot falsify the internal cleanup completion. No new shutdown
timeout is introduced; the enclosing control/runtime still applies its existing deadline.

`Frame.acquireResource()` selects the initiating application across shared-service calls. There
is an overload for nonblocking `AutoCloseable.close()` and one for asynchronous cleanup. Cleanup
starts outside resource/container monitors, has no guaranteed ordering, and must not require
application services to keep running. Existing `onTermination` actions use the same registry.

The keystore path now delegates to `KeyStoreOperations.extractKey()`, which already scopes its
input stream with try-with-resources. This preserves missing-file, invalid-password and missing-key
behavior without duplicating the extraction implementation.

The native integrations reserve channel/socket ownership before opening or connecting and keep
it through handle delivery. Explicit channel/socket close uses that same registration. HTTP client
pools are created **on first use by the requesting application**, not while injecting a shared
connector into the runner. Each owner gets independent cookies and connections. Owner cleanup
stops and awaits all its clients, then removes its entry from the shared connector.

HTTP servers register before binding. Partially constructed HTTP and HTTPS listeners are retained
for cleanup even when later setup fails. Closing releases listeners, unfinished exchanges, the
handler executor, route references and the matching owner's keep-alive registration. Potentially
blocking native shutdown runs on a dedicated virtual thread; completion is included in the
existing owner deadline. Request-body reads use owned IO rather than blocking a service worker.

Watch subscriptions are individually owned native registrations. A shared directory key remains
until its final subscription leaves. Cancellation releases listener references directly, without
requiring a terminating application's service to run. Queued event notifications carry IDs and
resolve the listener at dispatch, so a cancelled subscription is not retained by a queued event.
Invalidated keys cancel their subscriptions. Directory/overflow event semantics remain separate.

Callback registrations use a concurrent map and release their frame/function on extraction,
explicit cancellation and owner termination. Alarm disposal also removes paused alarms from the
timer. Timer cancellation runs outside the container monitor to avoid lock inversion with alarm
cleanup. No GC or elapsed-time assertion determines whether a cleanup test passes.

### Native integration status

| Resource | Implemented release | Verification |
|---|---|---|
| File channels | Explicit close, ignored results, failed immediate handle delivery and owner termination | Retained native handle checked after request success/failure/cancellation; caller file survives |
| Sockets | Ownership before connect, connect failure/cancellation, explicit close and owner termination | Loopback success/failure/cancellation/read scenarios; actual socket closed and peer sees EOF |
| Watch subscriptions | Individual cancel, owner termination, last-listener native unwatch and invalidated keys | Repeated requests leave no registrations; two owners share one key and can close in either order |
| HTTP clients | Per-application client pool, active-send cancellation and awaited client shutdown | Native clients terminate after control close; unit test cancels a send after loopback peer accepts it |
| HTTP/HTTPS servers and exchanges | Partial startup, explicit close and owner termination | Ports can be rebound after request close; unit tests cover unfinished exchanges, executor termination and partial initialization |
| Cancelled alarms | Callback/frame removal and alarm disposal, including paused timers | Explicit repeated cancellation, extraction/cancel race, owner close and late disposal-hook attachment |

These are **sequential reuse issues too**. Serialization does not dispose resources left by an
application. Tests keep one session open, retain actual native references, close each control,
check cleanup, and then run a healthy application through the same connector. Separate overlapping
watch controls verify shared-key ownership. Network fixtures use loopback and dynamic ports.

The default embedding provider still does not expose networking/web resources. The XDK tests
compile the real runner source with additional **test-only** resource-provider cases, leaving its
registration/start/release protocol intact. The production runner source is a declared test
resource input. This does not implement custom injectors or widen default injection policy.

## Intended ownership

| Owner | Resources | Required release boundary |
|---|---|---|
| Host/caller | Supplied repository, console writer, filesystem root and its existing files | Remain usable after request and session close |
| Embedding session | Interpreter executors, timer thread, shared native services, watcher dispatcher; JIT runtime and template loader | Session close, after owned requests have stopped |
| Application request | Open channels/sockets, watcher subscriptions and their callbacks, alarms, pending IO, application HTTP clients/servers, generated temporary root | Explicit close/cancel or request close, including failure and cancellation |
| Individual operation | Short-lived streams, DNS contexts/enumerations, HTTP exchanges | Completion, failure or cancellation of that operation |

Ownership follows the initiating application, including calls through services in a parent
container. `Frame.scheduleIO()` already uses `Fiber.getResourceContainer()` for this reason.
The container that hosts a shared native service is not necessarily the owner of the resource
created by that service. Intentionally shared resources would need an explicit session owner or
lease; they should not acquire that lifetime accidentally.

### Runner reuse and why earlier tests did not establish cleanup

The long-lived host is intentional. On the audited master baseline (`6539aa6eb`), both
`EmbeddingSupport` and [`runner.x`](../lib_runner/src/main/x/runner.x) already describe a persistent
"Container 0" that creates child application containers. This branch retains that design:
one owned embedding session keeps the runner/runtime alive, while each run gets a new `Task`,
child container, resource context and `Control`. It does not run successive applications in the
same child container. A runner-owned HTTP endpoint, when using the standalone web runner, should
survive child completion; an application-owned listener should not. Headless embedding does not
start that runner endpoint.

There are two release boundaries. `Control.close()` calls `releaseTask()`, which kills the child
and removes its registry entry. Closing the embedding session stops the host after releasing its
controls. Starting a new host per test would discard the intended runner reuse and would still
not substitute for native disposal: the channel probe remained open after session close.

The initial source comparison with master established that the missing disposal paths predated
the native migrations: `OSStorage.x` already had the final-listener cleanup TODO and unfinished native unwatch;
channel/socket allocation and close paths lacked owner registration; HTTP server code had not
changed, and the client pool already had its disposal TODO. Those native paths are now changed
by the scoped cleanup implementation. This comparison does not prove identical cancellation
behavior on master.

The old runner completion callback also set `container = Null` and unregistered the task when
the entry method completed, without calling `container.kill()` on that path. Its `kill()` only
acted while `running` was true. This branch retains the child until explicit release and waits for
application activity, and the new native registrations now participate in that termination.

Existing checks missed a different property: successful work or explicit application close does
not establish disposal of abandoned resources. The old `TestFiles` watcher test printed events,
waited on timers and called cancel; it asserted neither event delivery nor native registration
removal. The CLI `Launcher.main()` calls `System.exit()`, so process-level runs cannot expose
accumulation across later requests in that JVM. Persistent runner users could have encountered
these gaps before; this audit has not established their historical frequency or whether anyone
reported them. The reproduced leak evidence here comes from this branch, not a rerun on master.

## Original findings and their corrections

The descriptions and probe counts in this section describe the pre-migration snapshot. The
implementation/status section above records their corrections; linked source files now contain
those fixes.

### 1. Watch cancellation retained native registrations — reproduced before correction

[`OSStorage.removeWatch()`](../javatools_bridge/src/main/x/_native/fs/OSStorage.x) nulls the
listener slot but leaves both the directory entry and its native watch registered. The native
`unwatch` declaration is marked native by
[`xOSStorage`](../javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSStorage.java),
but there is no implementation in its dispatch switch. The existing TODO describes this missing
cleanup; it predates this branch.

A host probe ran three requests using distinct directories, explicitly called each watch's
cancellation function, joined and closed each control, and inspected the daemon's registration
map. Its size was **1, 2, 3** after successive requests and **0** only after session close.
The probe used explicit request completion, not sleeps, event-delivery timing or GC.

Abandoning the cancellation function also retains the listener itself. `NativeContainer` caches
one `OSStorage`; its `allWatchers` map is consequently shared across requests. The session-owned
daemon retains that service. Request shutdown does not remove the request's listener or prevent
that registration from outliving its application.

Correction now implemented: give each subscription an owner and cancellation identity; remove it on explicit
cancel and owner termination. Release the native key and directory entry when the final listener
leaves. Closing one request must preserve other requests' subscriptions to the same path. Merely
attaching the shared directory key to the first requesting container would be incorrect.

Related source findings: `processKey()` always passes `isFile=True`, including directory events;
it ignores invalidation from `key.reset()`, and `OVERFLOW` is discarded. Directory deletion needs
enough retained type information to classify an already-removed node. These event semantics are
not covered by the new file-event test and remain follow-ups.

### 2. An abandoned channel survived request and session close — reproduced before correction

[`xOSFile.invokeOpen()`](../javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFile.java)
opens a Java channel and hands it to
[`xRawOSFileChannel`](../javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java).
Only the native `close` method closes it; neither the request nor session registers its disposal.
The `createHandle()` path with `Op.A_IGNORE` also returns without closing the supplied channel.

A probe retained the native channel reference from an application that left `File.open()` open.
`FileChannel.isOpen()` was **true before control close, after control close, and after session
close**. The probe retained the reference to avoid GC/finalization affecting the observation and
closed it itself afterwards. The creation/close paths are unchanged from master; this branch
changed which container owns the channel's IO tasks, not the channel itself.

Correction now implemented: register ownership immediately after opening, cover handle-construction failure and
ignored results, and unregister on normal close. Closing the channel must not delete a caller's
file. The new manual tests verify explicit close and `using` cleanup, not automatic disposal of
abandoned channels.

### 3. Sockets and HTTP objects lacked owner disposal — original source audit

- [`xRTSocket`](../javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java)
  closes failed connects and explicit closes, but does not register a connected socket for owner
  termination. IO tracking ends when an operation ends. There is also a transfer race: cancellation
  can discard an IO result after a socket has opened but before its handle is delivered. Account
  for connect-in-progress, failed asynchronous handle construction, idle sockets and blocked reads.
- [`xRTConnector.ConnectorHandle`](../javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTConnector.java)
  keeps a pool of `HttpClient` instances without a shutdown hook. Cancelling an individual send
  does not establish ownership or release of the pooled client. It also retains cookies, so the
  owner must be explicit before any sharing across requests.
- [`xRTServer`](../javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java)
  binds HTTP/HTTPS listeners and creates a handler executor. Explicit `closeImpl()` stops them,
  but container shutdown does not call it. The bind-failure handler terminates the service without
  disposing partially created servers. HTTPS initialization can fail before its server is stored
  in the handle. An unfinished `HttpExchange` also needs cancellation cleanup; body reads currently
  run directly on the service worker.

HTTP client and server migrations are separate scopes. A client pool needs an explicit owner
before sharing its cookies or connections; cancelling one send is not client disposal. Server
cleanup must handle every partially initialized state, not assume that both HTTP and HTTPS
listeners, contexts and their executor exist. Stop accepting requests, close active exchanges,
release the executor and balance keep-alive registration without relying on application handlers
continuing to run. Integrate completion with the existing shutdown deadline rather than adding
a fresh timeout or blocking the owner monitor. Add loopback-only success/failure/cancellation tests
with dynamically allocated ports and explicit handshakes before claiming HTTP cleanup works.

These are source-confirmed missing disposal paths, not successful network integration tests.
The default embedding `TaskResourceProvider` delegates to `BasicResourceProvider`, which does not
supply networking/web resources. A future custom-injector/network capability must not be claimed
safe based on the current embedding suite. The underlying native paths are also used outside that
restricted provider. Their missing disposal paths predate this branch.

### 4. Other resources and retention

| Area | Finding and evidence |
|---|---|
| Runtime workers and active IO | `Container.terminateServices()` cancels tracked IO and waits for worker completion; `Runtime.close(Duration)` stops both executors within its deadline. Existing deterministic lifecycle/unit tests cover cancellation and failure reporting. Interrupting work is not a substitute for closing an idle handle. |
| Timers/clock | Scheduled tasks are container-tracked and cancelled on termination; the runtime timer is session-owned. However, `WeakCallback` stores the callback/frame strongly in `ServiceContext.m_mapCallbacks`. Explicit alarm cancellation does not remove that entry; only callback extraction does. Long-lived services can therefore retain cancelled callbacks. This is a source finding, not a retained-heap measurement. |
| Short-lived file IO | The range-read and truncate helpers use try-with-resources; byte reads/writes and copy/move helpers use scoped file utilities. No persistent handle is intentionally transferred from these operations. |
| DNS | `xRTNameService` closes its `DirContext` and each `NamingEnumeration` in `finally`. Work uses request-owned IO. No external DNS/network tests were added; prompt interruption of the provider is not established. |
| Keystores | The audit found that `xRTCertificateManager.loadKey()` passed a new `FileInputStream` to `KeyStore.load()` without closing it. Fixed locally by delegating to the already-scoped `KeyStoreOperations.extractKey()`. |
| Console | The supplied `PrintWriter` remains caller-owned. Expanded integration tests verify that request/session shutdown do not close it. External/buffered embedding consoles reject input. Legacy terminal input is synchronous, uses process-static terminal state, and has a `System.exit` path; it must not become an embedded-input fallback. |
| Filesystem roots | Existing integration tests prove generated request roots are deleted and caller roots survive. Deleting a path is not proof that its native channel was closed, especially on Unix. |
| JIT | The owned template loader closes after the execution worker stops. This does not provide interpreter-native resource parity. Filesystem/network resource scenarios were not run through the incomplete JIT. See [JIT embedding](jit-embedding.md). |

## Tests added and their limits

[`manualTests/src/main/x/files.x`](../manualTests/src/main/x/files.x) now waits for actual file
create/delete events, exercises two listeners for one directory, cancels one while the other
continues, repeats cancellation and re-registers the same path. It uses futures and assertions.
Its one-minute timeout is a deadlock guard, not a timing assertion; no sleep decides success.
Fresh directories are claimed without deleting another test's files. File-channel assertions
cover explicit/idempotent close, positioning, `using` cleanup on an exception and file preservation.

This replaces a timer-and-console demonstration that could pass without receiving any watcher
event. It remains the same `TestFiles` module in the existing sequential/parallel module lists;
there are no new automatic JIT runs or Gradle dependencies. Event delivery can take longer than
the old unconditional two-second wait on platforms that poll their filesystems.

[`ownership/Watching.x`](../xdk/src/test/resources/ownership/Watching.x) deliberately abandons a
watch on success, failure and cancellation. The expanded `EmbeddingLifecycleTest` closes each
control, runs a healthy request afterwards, closes/reopens the session and checks that its watcher
threads stop. It also checks caller-owned consoles/directories. The XDK test task provisions the
distribution; there are no assumptions that silently skip these tests.

Those initial passing tests covered **session** watcher shutdown and application-visible explicit
close. The new `EmbeddingResourceOwnershipTest` adds the missing request-level native-handle
assertions. The negative host probes above remain historical evidence; no expected-leak assertion
blesses the broken behavior as a permanent contract.

Validation on the audited branch:

- All 16 `EmbeddingLifecycleTest` tests passed; JUnit XML reported zero skips, failures or errors.
- All 21 modules in `runSequential` passed in DIRECT mode, including the changed `TestFiles`.
- The same `TestFiles` passed through ATTACHED with the interpreter.
- `spotlessCheck` and `git diff --check` passed. No Gradle configuration or dependency changes were
  needed; the repeated standalone DIRECT file-test run reused the configuration cache.

Re-run the committed coverage with:

```bash
./gradlew :xdk:test --tests org.xvm.xdk.EmbeddingLifecycleTest \
  :manualTests:runSequential spotlessCheck \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true

./gradlew :manualTests:runOne --mode=ATTACHED -PtestName=TestFiles \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

The first command's Java test task can be up-to-date on a repeated invocation. To deliberately
repeat the Java lifecycle tests, add `--rerun-tasks --no-build-cache`; their installed XDK is still
provided by the task dependencies. The two negative native-resource probes are audit evidence,
not part of this green coverage.

## Current regression coverage and submission boundary

`OwnedResourceTest` covers acquisition/termination races, explicit-close removal, cleanup failures,
cancellation-proof completion, shared-service owner selection, callback extraction/cancellation
and late alarm-hook attachment. `HttpResourceOwnershipTest` exercises real loopback clients,
active-send cancellation, partial HTTP/HTTPS binding and unfinished exchanges with executor close.
These Java tests require no installed XDK.

`EmbeddingResourceOwnershipTest` uses `.x` fixtures in `xdk/src/test/resources/ownership/` and the
installed distribution provisioned by the XDK test task. It retains channels, sockets and HTTP
clients, inspects subscription/callback/alarm registrations, and checks server port release after
control close. It covers sequential success/failure/cancellation, shared watcher keys in both
close orders, paused alarms, and a healthy request after cleanup. It does not wait for session
shutdown or GC to make those assertions pass.

Run the focused matrix and the existing manual suite with:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.OwnedResourceTest \
  --tests org.xvm.runtime.ContainerActivityTest \
  --tests org.xvm.runtime.RuntimeShutdownTest \
  --tests org.xvm.runtime.ExternalCompletionTest \
  --tests org.xvm.runtime.template._native.web.HttpResourceOwnershipTest \
  :xdk:test --tests org.xvm.xdk.EmbeddingResourceOwnershipTest \
  --tests org.xvm.xdk.EmbeddingLifecycleTest \
  :manualTests:runSequential spotlessCheck \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Validation on 2026-09-23:

- **48 Java tests passed with zero skips, failures or errors** in JUnit XML: 16 ownership/callback
  tests, five native HTTP tests, seven existing runtime tests and 20 embedding integration tests.
- All **21 sequential manual modules passed in DIRECT**, including file events, explicit channel
  cleanup and the existing timer/service tests.
- The final embedding run also exercised repeated explicit HTTP-server close. It passed all
  20 integration tests; production networking injections remain unchanged.
- `spotlessCheck` and `git diff --check` passed. Repeating the final XDK test/formatting command
  reused the configuration cache; its Java test outputs were up to date on that cache check.

Test-only timeouts are deadlock guards; no timing performance thresholds, public servers or
fixed ports are involved. The result establishes disposal for these interpreter resource paths,
not an exhaustive proof for every native operation, platform or failure mode.

The native migration commit includes only the six native integrations, supporting owner/frame/
callback changes, focused tests, the runner-source test input and matching documentation. It adds
no automatic JIT tasks and does not change the plugin default. The
[submission plan](../plugin/doc/plans/embedded-runtime-pr-plan.md#native-resource-integration-scopes)
places it after the common mechanism and interpreter request API; its six resource scopes remain
explicit if reviewers prefer further extraction.

Remaining implementation work includes nested-owner retention, described below. Broader validation
and capability growth also remain: retained heap/classloader measurements under long workloads, directory/overflow
watcher event semantics, general custom injectors and JIT resource support. TCP listen/accept is
still a pre-existing unimplemented native capability; this change owns existing connected TCP
sockets and HTTP/HTTPS listeners, not a new TCP server API. The bounded-shutdown failure contract
now includes native cleanup completion and retryable closing; cleanup failures remain visible.
These targeted disposal assertions do not prove every resource or platform failure safe.

## Follow-up findings after the native migrations

The second audit inspected allocation, handoff and shutdown paths at `6fea82130`. Its two Java
host probes used controlled state without sleeps, GC assertions or timing thresholds. The
reproductions below describe that snapshot. The subsequent corrections add committed regressions;
the earlier 48-test result alone did not cover these properties.

### 1. Executor termination can precede native cleanup — corrected

At the audited snapshot, [`Runtime.close()` and `isTerminated()`](../javatools/src/main/java/org/xvm/runtime/Runtime.java)
only used the two executors to determine whether shutdown had finished. Owned asynchronous
cleanup could still be pending after both executors stopped; HTTP cleanup workers run separately.

The probe registered a resource whose cleanup returns an explicitly incomplete future, then
closed the runtime with a zero budget. The first close correctly failed. While that same future
and container termination remained incomplete, `isTerminated()` returned true and a second
`close(Duration.ZERO)` returned successfully. Completing the future afterwards disposed of the
probe cleanly. No worker scheduling or elapsed-time comparison determines this result.

[`InterpreterConnector.isClosed()`](../javatools/src/main/java/org/xvm/api/InterpreterConnector.java)
delegates to this status. [`EmbeddingSupport.close()`](../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java)
uses it to release the implementation-classloader's runtime ownership slot, so the slot could be
released while native cleanup remained pending. The original session still reported its close
failure; that did not make releasing the slot safe.

Correction: runtime termination now requires successful container/native cleanup as well as both
executors stopping. The first cleanup future is retained across close attempts, preserving failures.
A caller timeout does not discard queued service shutdown work: active service work is interrupted,
and the service executor closes when container cleanup finishes. Dropping queued work had left
termination futures permanently incomplete in the zero-budget integration regression.

`RuntimeShutdownTest` uses controlled futures to verify pending cleanup, repeated close and lasting
cleanup failure. The embedding regression verifies that a failed close blocks another session,
then allows replacement after cleanup and a successful retry. Submission scope: PR 3/4 and PR 5a.

### 2. Cancelled timer tasks remain queued across request close — corrected

The container's alarm index is a **weak** set; it is not the retaining root. The runtime's
`java.util.Timer` queue strongly retains cancelled tasks until they are removed from that queue.
The audited cancellation paths called `TimerTask.cancel()` without explicitly removing queued tasks.

The probe scheduled one future live alarm for a surviving owner and 100 later alarms for another
owner, cancelled those 100 and terminated their owner. Inspection under the queue monitor showed:

```text
Weak container index entries after request termination: 0
Strong Java timer queue entries after request termination (one live, 100 cancelled): 101
Cancelled queue entries removed by explicit probe purge: 100
```

The deadlines only keep all tasks pending during the probe; it never waits for them. Queue
inspection used a test-only `--add-opens java.base/java.util=ALL-UNNAMED`. Clearing callback maps
and paused-alarm sets, as existing regressions verify, does not establish queue removal. Retained
triggers can retain alarm/timer objects; retained-heap size has not been measured.

Correction: container termination purges cancelled entries after all of its resource cancellation
hooks finish, including hooks completed asynchronously. This scans the shared queue once per owner
termination rather than once per alarm. Tests cover 100 explicitly cancelled alarms, cancellation
by termination itself and asynchronous cancellation of a task scheduled in another container;
another owner's live alarm stays scheduled. The committed tests use the purge count and task state,
without JDK reflection or `--add-opens`.

Cancellation during a still-running request can retain queue entries until the next purge or normal
queue removal. This correction establishes the requested owner-shutdown boundary. Submission scope:
PR 3 timer lifecycle and PR 4b/R6 alarm integration.

### 3. Failed control release strands host-side cleanup — corrected

In the audited [`InterpreterControl.close()`](../javatools/src/main/java/org/xvm/api/InterpreterControl.java),
console unregister and temporary-root deletion were inside the block reached **after** awaiting
`releaseTask`. If that await failed or timed out, neither action ran. The control was marked closed
and its release future permanently recorded failure; later close calls did not attempt these
actions again, even if the application eventually stopped. Session close cleared its control set
after runtime shutdown without completing this deferred host cleanup.

Correction: the release operation outlives a caller's deadline. Once the runner acknowledges
termination, host cleanup runs once on a tracked cleanup worker even if the caller has already
returned. A failed protocol attempt can be retried. If the runner cannot acknowledge release,
successful runtime termination provides the fallback boundary for host cleanup. Session close
retains unfinished controls/runtimes and supports another bounded close attempt; it keeps the
implementation-classloader ownership slot until native and host cleanup have completed.

The integration tests hold native cleanup behind a future, verify that the temporary directory and
console registration survive a zero-budget close, and then check their removal without calling
close again to trigger it. Separate cases cover a stopped runner and session-close retry. Existing
caller-owned directory/writer assertions remain in the lifecycle suite. Submission scope: PR 5a,
with PR 5b resource-context coverage.

### 4. Weak container discovery is not a lifetime owner — source risk requiring a regression

`Runtime.f_containers` is a `WeakHashMap`. A parent does not hold a strong child collection;
termination discovers children through that weak registry. `OwnedResource` is strongly held by
its owning container, but this does not itself keep an otherwise unreachable container rooted.
The runner retains its direct application's container until release, so the sequential request
tests do not exercise an abandoned nested child with idle native resources.

Define who retains such a child while it owns resources, acquisitions or pending cleanup, and
release that retention when ownership ends. This is a source-level lifetime concern, not a
reproduced GC-dependent leak. A regression should inspect explicit ownership registration and
release deterministically, rather than wait for GC. Submission scope: PR 3/4.

### 5. Failed socket handoff can postpone disposal until owner close — corrected

The audited [`xRTSocket.constructSocket()`](../javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java)
closed on immediate construction failure. Its `R_CALL` continuation only finished a successful
construction; asynchronous failure had no corresponding disposal continuation. `finishConnect()`
also did not close when its result was ignored or assignment failed. Ownership still guaranteed
release at successful owner termination, but a long-running application could retain these
undelivered sockets until then.

Correction: the native connect continuation carries an exception cleanup through asynchronous
construction and result assignment. Interpreter exception unwinding invokes it; successful handoff
disarms it. Ignored native results close the connected socket immediately. The loopback regression
uses the real interpreter with injected construction/assignment failures and checks peer EOF and
an empty socket ownership set while the application remains running. It covers ignored results,
asynchronous construction failure and both synchronous and asynchronous assignment failure.

The existing integration fixture's read scenario still signals readiness before entering the read,
so it does not deterministically establish an already-blocked native read; a native-entry barrier
remains a coverage follow-up. Submission scope: PR 4b/R2, including the frame/continuation support.

Nested-owner retention and the stated coverage boundaries remain open. These four corrections do
not establish exhaustive shutdown ownership. DNS interruption, custom injectors, JIT native
resources, watcher event semantics and broader platform/retention measurements remain the
previously documented boundaries.

### Validation of the four corrections

The combined run on 2026-09-23 passed **76 Java tests with zero failures, errors or skips** in
JUnit XML: 32 focused runtime/native tests, 24 embedding integration tests and 20 plugin tests.
All **21 sequential manual modules passed in DIRECT mode**. `spotlessCheck`, `git diff --check`
and relative documentation-link/anchor checks passed. The focused socket regression also reused
the Gradle configuration cache while actually rerunning its changed Java test.

The four new runtime tests cover pending native cleanup, persistent cleanup failure, cancellation
by explicit close/owner termination and asynchronous timer cancellation. The four new embedding
tests cover late request release, fallback after the runner stops, bounded session-close retry
and the four socket-handoff outcomes. They reuse the existing `.x` application fixture; the
installed XDK remains a declared dependency of the XDK integration test task. Runtime unit tests
need no compiled XDK and no test silently assumes one exists.

The socket failure fixture uses native operations with descriptive stack-trace names. It replaces
the socket template only within that test and restores it in `finally`; no failure switch is added
to production constructors. It asserts the expected exception as well as peer EOF while the owner
remains alive. Existing successful socket scenarios cover normal handoff and owner close.

Validation command:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.OwnedResourceTest \
  --tests org.xvm.runtime.ContainerActivityTest \
  --tests org.xvm.runtime.RuntimeShutdownTest \
  --tests org.xvm.runtime.ExternalCompletionTest \
  --tests org.xvm.runtime.template._native.web.HttpResourceOwnershipTest \
  :xdk:test --tests org.xvm.xdk.EmbeddingResourceOwnershipTest \
  --tests org.xvm.xdk.EmbeddingLifecycleTest \
  :plugin:test :manualTests:runSequential spotlessCheck \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Local correction commits are `0814693ae`, `c3bfc9582`, `0406f3062` and `e1eb9fb1a`.
Their mapping to PR 3/4, PR 5a/5b and PR 4b/R2/R6 is recorded in the submission plan.
