# Embedding resource ownership audit

Audit of `lagergren/embedded-gradle-runtime` at `c884779d6`, based on master `6539aa6eb`.
The initial audit added tests and recorded remaining fixes. The common ownership mechanism and
keystore correction have subsequently been implemented locally; resource-specific migrations
remain outstanding.

**Result: request isolation is not yet complete for native resources.** Pending IO and runtime
workers have explicit shutdown, but idle native handles and watcher subscriptions do not all
participate. A successful `Control.close()` or session close is therefore not evidence that every
application-opened file or socket was closed. Do not use the existing performance results or green
manual tests as that guarantee.

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

This is the mechanism and keystore correction only: channel/socket creation, watcher subscriptions
and HTTP resources have **not** yet been migrated. The leaks below remain open except for the
keystore stream. The new `OwnedResourceTest` cases exercise controlled acquisition/close races,
normal-close removal, failure propagation, future cancellation and shared-service owner selection
without a built XDK, sleeps or GC-dependent assertions.

Validation of this implementation passed 12 ownership tests, 14 keystore tests, seven existing
runtime activity/shutdown/completion tests, and all 16 embedding lifecycle tests: **49 tests,
zero skips, failures or errors** in the JUnit XML. The XDK task provisioned its distribution for
the integration cases. Formatting checks also passed.

### Remaining implementation status

The mechanism is implemented; existing native resources must explicitly use it. The
[submission plan](../plugin/doc/plans/embedded-runtime-pr-plan.md#remaining-native-resource-integration-scopes)
defines separate change scopes for these remaining integrations.

| Resource | Current status | Remaining cleanup |
|---|---|---|
| File channels | Open handle surviving request and session close reproduced | Register acquisition, close abandoned/ignored/undelivered handles, remove registration on explicit close |
| Sockets | Missing owner disposal confirmed in source | Own connection acquisition and transfer, close idle or blocked sockets on request termination |
| Watch subscriptions | Registration growth across sequential requests reproduced | Cancel each owner's listeners, implement native unwatch and release the final directory key |
| HTTP clients | Missing client-pool shutdown confirmed in source | Own pooled clients and cookie state, release them on owner termination and cancel active sends |
| HTTP/HTTPS servers and exchanges | Missing owner disposal and partial-startup cleanup confirmed in source | Stop listeners, close unfinished exchanges and release the handler executor, including failed startup |
| Cancelled alarms | Callback-map retention confirmed in source | Remove callback/frame references on cancellation without racing callback execution |

These are **sequential reuse issues too**. Request A can finish and its control can close while
its idle handle or subscription remains alive; request B then runs in that same session. Running
one request at a time prevents concurrent requests but does not dispose A's resources. Session
close currently clears the watch daemon's registrations, but the channel probe remained open even
after session close. The shared mechanism does not change these results until native call sites
adopt it.

The passing sequential manual suite exercises execution and explicit application cleanup. It
does not prove abandoned native resources are released. Each integration needs repeated requests
in **one still-open session**, checking cleanup after each control closes, covering normal return,
failure and cancellation, and then running a healthy request. Use explicit handle/registration
state and completion barriers; do not infer success from GC, sleeps, thread counts or eventual
session shutdown. Network/HTTP cases need a test resource-provider fixture because the default
embedding provider does not expose them.

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

Source comparison with master establishes that the missing native disposal paths predate this
branch: `OSStorage.x` already had the final-listener cleanup TODO and unfinished native unwatch;
channel/socket allocation and close paths lacked owner registration; HTTP server code is unchanged,
and the client pool already had its disposal TODO. The branch changes their IO ownership, not
those allocation/close paths. Its new cancellation behavior still needs allocation/transfer race
regressions; this source comparison does not prove identical cancellation behavior on master.

The old runner completion callback also set `container = Null` and unregistered the task when
the entry method completed, without calling `container.kill()` on that path. Its `kill()` only
acted while `running` was true. This branch retains the child until explicit release and waits for
application activity, but native handles still need to participate in that termination.

Existing checks missed a different property: successful work or explicit application close does
not establish disposal of abandoned resources. The old `TestFiles` watcher test printed events,
waited on timers and called cancel; it asserted neither event delivery nor native registration
removal. The CLI `Launcher.main()` calls `System.exit()`, so process-level runs cannot expose
accumulation across later requests in that JVM. Persistent runner users could have encountered
these gaps before; this audit has not established their historical frequency or whether anyone
reported them. The reproduced leak evidence here comes from this branch, not a rerun on master.

## Findings

### 1. Watch cancellation leaves native registrations behind — reproduced

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

Required fix: give each subscription an owner and cancellation identity; remove it on explicit
cancel and owner termination. Release the native key and directory entry when the final listener
leaves. Closing one request must preserve other requests' subscriptions to the same path. Merely
attaching the shared directory key to the first requesting container would be incorrect.

Related source findings: `processKey()` always passes `isFile=True`, including directory events;
it ignores invalidation from `key.reset()`, and `OVERFLOW` is discarded. Directory deletion needs
enough retained type information to classify an already-removed node. These event semantics are
not covered by the new file-event test and remain follow-ups.

### 2. An abandoned file channel survives request and session close — reproduced

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

Required fix: register ownership immediately after opening, cover handle-construction failure and
ignored results, and unregister on normal close. Closing the channel must not delete a caller's
file. The new manual tests verify explicit close and `using` cleanup, not automatic disposal of
abandoned channels.

### 3. Sockets and HTTP objects have the same ownership gap — source audit

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

Those passing tests cover **session** watcher shutdown and application-visible explicit close.
They do not assert the missing request-level watch/channel disposal. The negative host probes
above exposed those gaps independently; no expected-leak assertion was added to bless the broken
behavior as a permanent contract.

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

## Implementation boundary and acceptance criteria

The reusable ownership registration and frame helper are now implemented as described above.
The next changes should apply them to native allocations and complete the remaining cleanup work:

1. Apply it to file channels and sockets, including allocation-to-handle-transfer failure and
   cancellation. Verify the same native object is closed after successful, failed and cancelled
   requests while the session stays open. Repeated explicit close must not grow registrations.
2. Apply it to individual watcher subscriptions and fix native unwatch separately from the shared
   daemon lifetime. Test two owners on the same directory, both cancellation orders, abandoned
   listeners, invalidated keys and a new subscription after the old one ends. Add directory and
   overflow event coverage with controlled native events where the OS cannot provide determinism.
3. Scope HTTP clients, listeners, executors and exchanges, including partial bind failure. Use
   loopback peers and completion barriers, not public servers, sleeps or fixed port numbers.
   Add the necessary resource-provider fixture rather than silently widening default injections.
4. Release cancelled callback entries. The certificate-manager stream fix is implemented; retain
   coverage of extraction failures as well as normal completion.
5. Repeat reuse checks with explicit counts of registrations/open handles. Follow with retained
   memory/classloader measurements. A thread-count test alone cannot detect these leaks.

These fixes do not depend on redesigning constant-pool ownership or a host that persists across
builds. They belong at the embedding/runtime ownership boundary and should precede expanding
DIRECT's supported resources or promoting it as the general plugin default.
