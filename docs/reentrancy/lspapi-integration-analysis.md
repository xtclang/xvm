# The `cpurdy/LSPAPI` branch, and what `XtcEngine` has to become

Analysis of `cpurdy/LSPAPI` at `2568d6be4` ("All reproducers are working as expected"), branched
from master at `443770bcc`. Checked out locally at `../lspapi`. **No code was changed anywhere for
this analysis.**

The branch is 19 files, +1761/-90. It is small, and almost all of it exists to make one structural
change possible.

> **Status: analysis only.** Nothing here has been raised on PR #545, nothing has been committed to
> `cpurdy/LSPAPI`, and the `../lspapi` checkout is unmodified. The hardening items in
> [Part 4b](#part-4b---hardening-the-lspapi-branch-ownership-finality-isolation) are written to be
> taken up as a **structured review or a sub-branch**, not as drive-by comments. Every claim below
> cites a file and line so a reviewer can check it rather than take it on trust - and two of my own
> first readings were wrong and are marked as such where they were corrected.

---

## Part 1 - What the LSPAPI branch actually is

### 1.1 The one structural idea

**Container creation moves out of Java and into Ecstasy.**

Today - in master, and in this branch's `XtcEngine` - a host that wants to run a module reaches into
the runtime from Java, builds a container, and invokes it. LSPAPI instead boots **one long-lived
Ecstasy application in container zero** whose whole job is to spawn child containers on request:

```java
// InterpreterControl.createConnector
InterpreterConnector connector = new InterpreterConnector(repository);
connector.loadModule("runner.xtclang.org");
connector.start(null);
connector.getMainContainer().invokeAsync("run").join();   // starts, and STAYS running
```

Every subsequent run is a **request posted into that already-running application**:

```java
// InterpreterControl.start
ObjectHandle hTaskId = postRequest("runTask", hModule, hRepository, hConsoleId).join();
```

and the container itself is created by Ecstasy code, using Ecstasy's own container API:

```ecstasy
// lib_runner/src/main/x/runner.x
service Task(Int id, ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
    void start() {
        Container container = new Container(
                template, Container.Model.Lightweight, repository, injector);
        ...
        @Future Tuple outcome = container.invoke("run", ());
    }
}
```

That is the whole proposal. Everything else in the branch is the plumbing that makes it expressible.

### 1.2 The new `lib_runner` module

A new Ecstasy library, `runner.xtclang.org`, containing:

| element | kind | role |
| --- | --- | --- |
| `runTask` / `taskRunning` / `taskResult` / `taskFailure` / `killTask` / `taskStatus` | module methods | the surface the Java side calls by name |
| `TaskRegistry` | **`static service`** | owns the task map and the id counter |
| `Task` | **`service`** per run | owns one container and its lifecycle |
| `Api` | `@WebService` | an HTTP surface over the same registry |
| `TaskResourceProvider` | `service` | supplies the per-run console, delegating the rest to `BasicResourceProvider` |

`TaskRegistry` being a **service** is the load-bearing detail. An Ecstasy service processes one
fiber at a time, so the registry's mutable state - `tasks`, `nextTaskId` - is serialized *by the
language*, not by Java locking. The same is true of each `Task`. This is the reentrancy principle
this branch has been converging on, applied on the Ecstasy side: **state is owned by something that
is single-threaded by construction.**

Note that the module is `@web.WebApp` and its `run()` starts a Xenia HTTP server. The javadoc says
"Native callers should invoke `runTask` directly", so the HTTP surface is an alternative front end,
not part of the Java path.

### 1.3 The Java changes, and why each is needed

| change | why it is required |
| --- | --- |
| `MainContainer.invokeAsync(String, ObjectHandle...)` returning `CompletableFuture<ObjectHandle>` | **The enabling change.** Master had only `invoke0`, which is fire-and-forget with no result. There was no way to call *into* a running container-zero and get an answer back. `invokeAsync` posts through `m_contextMain.postRequest(...)`, so the call is a normal service request on the runner's own fiber. Without this, the runner-app model is not expressible at all. |
| `InterpreterConnector.getNativeContainer()` / `getMainContainer()` | The control layer needs the native container (to register consoles) and the main container (to post requests). Previously neither was reachable. |
| `NativeContainer` resource maps become `ConcurrentHashMap`; `addResourceSupplier`/`removeResourceSupplier` made public | Injectable resources are now **registered and unregistered dynamically, while the runtime is live** - one console per run, removed when that run ends. Master's `HashMap` and private registration assumed the resource set was fixed at boot. |
| `NativeContainer.getInjectable` re-reads the supplier and null-checks it | Direct consequence of the above: a resource can now disappear between the name lookup and the supplier lookup, because another run just finished. |
| `xExternalConsole` (new, 153 lines) | Per-run console. Registered as a **named** native resource `console_<id>`, so the Ecstasy side asks for it by name: `@Inject(resourceName=$"console_{consoleId}") Console console`. |
| `xCoreRepository` handle carries its own repository | Master took the repository from `f_container.getModuleRepository()` - one per container. Now `CoreRepoHandle` holds the repository it was made with, so **each run can be handed a different repository** as an argument. |
| `compiler.Compiler` takes `ErrorListener` instead of `ErrorList` | A tool caller wants to supply its own sink. Independently the same generalisation this branch made for [E32](plans/master-enhancement-submissions.md). |
| `tool.Compiler` gains `protected compile(List<Compiler>, ModuleRepository)` and `protected flushAndCheckErrors(...)` | So `LspSupport.LspCompiler` can **subclass the real CLI compiler** and reuse its pipeline rather than reimplementing it. |
| `TC-01`..`TC-99` in `errors.properties` | A diagnostic vocabulary for tool-facing failures, distinct from `COMPILER-`/`VERIFY-`. |

### 1.4 The compile path

`LspSupport.compile(String source, ModuleRepository input, ErrorListener errs)` builds an
`LspCompiler extends org.xvm.tool.Compiler` and calls `process()`. It:

1. builds its library repo by overriding `configureLibraryRepo`:
   `new LinkedRepository(true, build, inRepo, coreRepo)` - **read-through is `true`**;
2. parses the source from a `String`;
3. requires the last statement to be a module declaration;
4. `generateInitialFileStructure()`, stores the module into the library repo;
5. delegates to the inherited `super.compile(List.of(compiler), repoLib)`;
6. forbids `emitModules` and the inherited `compile(...)` overload with
   `throw new IllegalStateException("This method must not be called")`.

Two things follow. First, **this is the CLI compiler**, not a reimplementation - which is a
deliberate and good decision, because it means the tool path and the CLI path cannot drift.
Second, **read-through cloning is retained**, so every compile gets private copies of the library
modules. That is the isolation mechanism [T1](plans/parallel-compiler-plan.md) tried to remove, and
LSPAPI keeps it.

### 1.5 The run path, end to end

```
LspSupport.run(module, console, rootDir, injections, errs)
  -> InterpreterControl.create(connector, module, repository, console, errs).start()
       prepareModule():
         module.getFileStructure().writeTo(bytes)                 // SERIALIZE
         new FileStructure(new ByteArrayInputStream(bytes), ...)  // DESERIALIZE -> fresh pool
         connector.getNativeContainer().createFileStructure(copy)
         file.linkModules(repository, true)
       xExternalConsole.register(nativeContainer, console)  -> console_<id>
       postRequest("runTask", hModule, hRepository, hConsoleId)
         -> MainContainer.invokeAsync -> m_contextMain.postRequest
            -> runner.runTask -> TaskRegistry (service) -> new Task (service)
               -> new Container(template, Lightweight, repository, injector)
               -> container.invoke("run", ())
       watch(): poll taskRunning() every 25ms; then taskResult()/taskFailure()
       finish(): report, unregister console
```

The `prepareModule` round-trip deserves emphasis. **The module is serialized and read back before
it is run.** That gives the run a `FileStructure` with a brand-new `ConstantPool`, structurally
disconnected from whatever the compiler produced. It is the same isolation the CLI gets for free by
writing a `.xtc` to disk and loading it again - and it is exactly what `XtcEngine.assemble` already
does in this branch, for the same reason.

### 1.6 What it is intended to solve

Reading the reproducers in `LspTest`, the intent is explicit:

- `testCompile` - compile from a String, in process, and get either a module or errors.
- `testRun` - run a compiled module and capture **its own** console output.
- `testRunException` - an application exception must reach the host, on that run's console and
  through that run's `ErrorListener`, rather than being printed to the JVM's stderr.
- `testRunLatency` - **five sequential runs of the same module in one hot JVM**, timed. This is the
  headline case: repeated runs must be fast and must not interfere.
- `testPoolGrows` - twelve runs, each using a *distinct* parameterized type, printing the native
  `ConstantPool` size after each. The comment is candid: *"a main container's pool dies with its
  run, but interning routinely reaches this shared pool"*. They are watching the shared plane grow.

---

## Part 2 - Why the old model was racy, and what replaced it

### 2.1 `manualTests/runner.x` was never designed for this

The existing `Runner` module used by `:manualTests:runParallel`:

```ecstasy
void run(String[] modules=[]) {
    Tuple<String, Future, ConsoleBuffer>[] results =
        new Array(modules.size, i -> loadAndRun(modules[i]));
    ...
}
```

`loadAndRun` creates a container and starts it. The array initializer therefore **creates and starts
every container in one burst, from a single fiber**, and only afterwards walks the results in order.

That shape has no lifecycle at all: no registry, no ids, no status, no kill, no per-task result. It
is a fixture for running a fixed list of test modules once, and it was written for that. Using it as
the model for "how a host runs modules" reads intent into it that was never there.

The concurrency consequence is specific: N containers perform their **first-time** initialization
simultaneously. Everything lazily built on the shared native plane - template `INSTANCE` fields,
the template cache, `TypeInfo` on shared pools, implicitly-imported identities - is raced by N
threads at once. This branch has documented that class of defect exhaustively; the mutable-static
`INSTANCE` cache is one member of it, and rows 26, 27, 41-45 of
[master-issue-submissions.md](plans/master-issue-submissions.md) are others.

### 2.2 What LSPAPI does instead

| | old `Runner` | LSPAPI `runner.xtclang.org` |
| --- | --- | --- |
| container zero | started per invocation, exits when the list is done | started **once**, stays alive |
| how a run is requested | an argument in the initial `run(String[])` | a **request** posted into the live app |
| when containers are created | all of them, at once, in an array initializer | one at a time, on demand |
| what serializes the state | nothing | `TaskRegistry` and `Task` are **services** |
| lifecycle | none | `running` / `result` / `failure` / `kill` / `status`, per task |
| console | `ConsoleBuffer` per container | native `console_<id>`, registered and unregistered per run |
| repository | the container's | passed per task as a handle |

The startup burst disappears not because the races were fixed, but because **the shape stopped
producing them**: the first task warms the shared plane while later tasks queue behind the
registry's fiber.

### 2.3 Why this was not possible before

Three hard blockers in master, each removed by exactly one change in this branch:

1. **No way to call into a running container.** `MainContainer.invoke0` is fire-and-forget with no
   return. A long-lived container-zero you cannot ask for anything is useless as a service.
   `invokeAsync` fixes this.
2. **No way to give a run its own console.** Resources were registered at boot into a `HashMap`;
   there was no dynamic registration, and no named-resource convention. `xExternalConsole` plus the
   concurrent resource maps fix this.
3. **No way to give a run its own repository.** `xCoreRepository` read the repository from its
   container. Carrying it on the handle fixes this.

Absent all three, a Java host had no choice but to build containers itself - which is precisely what
`XtcEngine.runFrom` does today.

---

## Part 3 - What `XtcEngine` has to become

### 3.1 What `XtcEngine` does now

```java
// XtcEngine.runFrom
NativeContainer containerNative = containerNative();
FileStructure   struct          = containerNative.createFileStructure(moduleApp);
struct.linkModules(repoRun, true);
NestedContainer containerRun =
        NestedContainer.createForHost(containerNative, struct.getModuleId(), List.of());
registerInjections(containerRun, mapInjections);
return containerRun.runModule(sMethodName);
```

**`NestedContainer.createForHost` does not exist in master or in LSPAPI.** It is this branch's own
invention. So the engine's run path is not merely different from what Cam and Gene propose - it is
built on an API they have not got and have not asked for.

### 3.2 The changes required

| # | change | replaces |
| --- | --- | --- |
| 1 | Boot container zero on `runner.xtclang.org` once per engine, and keep it | `NestedContainer.createForHost` per run |
| 2 | Run by `invokeAsync("runTask", hModule, hRepository, hConsoleId)` | `containerRun.runModule(sMethodName)` |
| 3 | Return a `Control`-shaped handle (`running`/`whenStarted`/`whenStopped`/`kill`/`result`) | returning a bare `CompletableFuture<ObjectHandle>` |
| 4 | Per-run console through `xExternalConsole.register/unregister` | nothing - the engine has no per-run console today |
| 5 | Per-run repository as an `xCoreRepository` handle | `LinkedRepository` assembled Java-side and given to the container |
| 6 | `prepareModule`'s serialize/deserialize round-trip before running | already equivalent - `XtcEngine.assemble` does this |
| 7 | Adopt `TC-xx` codes for tool-facing diagnostics | the engine's own ad-hoc messages |

Item 2 has a consequence worth stating plainly: **`runTask` runs `run()`**. The engine's ability to
invoke an arbitrary `sMethodName` has no equivalent in the LSPAPI surface. Either the runner module
grows a method-name parameter, or that capability is given up.

### 3.3 Where the engine should NOT follow

**`LspSupport` is a singleton with one-shot configuration.** `instance()` plus a `configure` that
throws if called twice with different arguments is process-global, first-caller-wins state. That
forecloses two engines with different module paths in one JVM, which is exactly what a test suite
and a multi-root LSP server both need. `XtcEngine` is instance-based with a builder, and that is the
better shape. The Ecstasy-side runner-app model and the Java-side singleton are **separable
decisions**, and only the first is load-bearing.

### 3.4 The compiler question, answered

The user asked whether they intend the compiler to stay a containerless Java API. **Yes - and the
code is unambiguous.** `LspSupport.compile` never touches the connector, never creates a container,
and calls `verifyConfigured()` only to obtain the core repository. Container zero is *only* for
runs. `LspCompiler` is a subclass of the CLI compiler operating on ordinary Java structures.

So the split they intend is:

- **compiling** - a plain Java API over the existing compiler, isolated by per-compile
  read-through cloning;
- **running** - a request into a long-lived Ecstasy application that owns container creation.

This branch's `XtcEngine` already matches on the compile side and diverges entirely on the run side.

---

## Part 4 - Concurrency

### 4.1 They have not addressed concurrent use, and the class doc overstates it

`LspSupport`'s javadoc says *"The methods on the LspSupport itself can be assumed to be thread-safe
and concurrent."* The implementation does not yet support that claim:

- **`configured`, `cfgRepo`, `cfgInjector`, `connector` are plain fields - no `volatile`, no
  `final`** (`LspSupport.java:83-86`) - and only TWO methods in the class take the lock:
  `configure` (`:152`) and `ensureConnector` (`:122`). Every other access reads them with no
  synchronization at all:

  | line | method | reads |
  | --- | --- | --- |
  | 93, 103 | `verifyConfigured()` | `configured` |
  | 170 | `isConfigured()` | `configured` |
  | 177 | `getConfiguredRepository()` | `cfgRepo` |
  | 185 | `getConfiguredInjector()` | `cfgInjector` |
  | 459 | `run(...)` | `cfgRepo` |
  | 474 | `run(...)` | `cfgInjector` |

  This is textbook unsafe publication. `configure` writes the fields while holding `LOCK`, but a
  reader that never acquires `LOCK` has no happens-before with those writes, so it may see any,
  all, or none of them. Two concrete failures follow, and both are silent:

  1. **A configured host reports itself unconfigured.** Thread B's `verifyConfigured()` may not
     observe `configured == true` at all, falls into the `XDK_HOME` path, and - if that variable is
     unset - throws *"ToolConnect has not been configured"* about an instance that was configured
     before the call was made.
  2. **A half-configured host is worse.** The boolean and the reference are independent
     non-volatile writes with no ordering between them, so B can observe `configured == true` while
     `cfgRepo` is still `null`. `verifyConfigured()` then returns happily and `compile` hands that
     null to `LspCompiler` as `coreRepo`, where `configureLibraryRepo` passes it into
     `new LinkedRepository(true, build, coreRepo)` - which asserts its repositories are non-null.
     The failure surfaces inside the compiler, attributed to the compile, with nothing pointing back
     at configuration.
- **The auto-configure path is safe, but only by luck of one class having value equality.**
  `verifyConfigured()` builds a `new DirRepository(dir, true)` and calls `configure(...)`, so two
  threads arriving together produce two distinct instances and the second is compared against the
  first with `Objects.equals`. That is benign *because* `DirRepository.equals` compares directory
  and read-only flag (`DirRepository.java:129`), so the two are equal and the loser simply discards
  its copy. The guard is not robust in general: the same double-configure with a repository type
  that does not define value equality - `LinkedRepository`, for instance - would fail with
  `IllegalStateException: configuration has been performed, and cannot be modified`, for doing
  nothing worse than calling a method concurrently. **Verified rather than assumed; my first reading
  of this was wrong.**
- **`InterpreterControl`'s `taskId` and `consoleId` are non-volatile - and this one is NOT a
  defect.** Both are written on the calling thread in `start()` *before* `watch()` submits the first
  polling task, and handing a task to an `Executor` establishes a happens-before with its execution,
  so the watcher sees both; each recursive `watch()` re-submission chains that edge onward. It is a
  fragility rather than a bug: the safety comes from a submission ordering nothing states, and
  moving the `watch()` call or adding a second reader breaks it silently. **I flagged this as an
  omission on first reading and that was wrong.**
- **`watch()` polls every 25ms on the common ForkJoinPool** via
  `CompletableFuture.delayedExecutor`. Each poll is a request into container zero. With many
  concurrent runs this is both traffic on the runner's single fiber and load on a JVM-wide pool the
  host does not control.

None of these are hard to fix, and none invalidate the design. They mean the claim is aspirational
at `2568d6be4`.

### 4.1b Does any of that bite in the SUPPORTED scenario? No - but something else does

The unsafe publication in 4.1 is **latent, not active**, for consecutive compiles and runs driven
from one thread. Those fields are read only by the calling thread, and a thread reading its own
prior writes needs no synchronization. Other threads do exist in that scenario - the polling watcher
on the common ForkJoinPool, the runtime's service threads - but none of them touch
`configured`/`cfgRepo`/`cfgInjector`; `InterpreterControl` reaches the connector through its own
`final` field. So the sequential story is thread-safe, and the javadoc's claim only becomes wrong
when a host takes it at its word and calls from several threads.

**The sequential scenario has a different problem, and this one is active: nothing is ever
released.**

```ecstasy
Int runTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId) {
    Int  id   = allocateTaskId();
    Task task = new Task(id, template, repository, consoleId);
    tasks[id] = task;          // added...
    task.start();
    return id;
}
```

There is **no removal from `tasks` anywhere in `runner.x`** - the only other references to the map
are the `get` in `taskFor` and the `contains` in `allocateTaskId`. And `Task.container` is assigned
once in `start()` (`:166`) and never cleared, not even by `kill()` (`:182-186`), which calls
`container.kill()` and sets `running = False`.

So every run permanently adds a `Task` service to the registry, and each `Task` holds its
`Container`, its `ModuleTemplate` and its `ModuleRepository`. On normal completion `kill()` is not
called at all - the container simply finishes `run()` - so even the explicit teardown path does not
run. The reference chain is retained for the life of container zero, which in this design is the
life of the JVM.

How much memory that holds depends on what a completed `Container` still owns, which is not measured
here. What is certain is that the retention is unbounded and grows once per run, in precisely the
scenario the branch exists to support - a long-lived VM doing many consecutive runs.

This is the same shape as [T15](plans/parallel-compiler-plan.md)'s finding on the compile side of
this branch, arrived at independently on the Ecstasy side: **the thing that makes reuse fast is a
long-lived owner, and a long-lived owner turns every missing release into a leak.** A registry needs
an eviction rule, and `Task` needs to drop its container when it stops.

They are plainly aware of the adjacent version of this: `testPoolGrows` prints the shared native
`ConstantPool` size after each of thirteen runs and its comment notes that "interning routinely
reaches this shared pool". That is measurement of a known concern, not a solution to it.

### 4.2 Sequential runs - what the design gives, honestly

The runner-app model makes sequential runs **structurally sound**: tasks queue on the registry's
fiber, containers are created one at a time, each gets its own console, and each is torn down before
the next result is collected.

It does not make the *shared native plane* safe. `testPoolGrows` says as much in its own comment.
The first run still warms every lazily-built structure on that plane; later runs read what it built.
Sequentially that is fine, and it is exactly why this model works where the old burst did not.

### 4.3 Parallel runs - not addressed

Nothing prevents a host calling `run(...)` from several threads. What then happens:

- `TaskRegistry` serializes registry mutation - **safe**;
- each `Task` is its own service - **safe**;
- but `new Container(...)` inside each task still initializes shared native state, and two tasks
  admitted in quick succession can be inside that concurrently, since the registry's fiber returns
  as soon as `task.start()` posts;
- `xExternalConsole.register/unregister` mutate the native container's resource maps concurrently -
  which is why they became `ConcurrentHashMap`, and why `getInjectable` had to tolerate a
  disappearing supplier. **That specific hazard they did address.**

So parallel runs are *better founded* than before but not established. The honest reading is that
they built for sequential and left parallel to be proven.

### 4.4 Parallel compiles - safe, and for the reason we already know

`LspCompiler` uses `LinkedRepository(true, ...)`, so each compile clones the library modules it
touches, each clone getting a fresh `ConstantPool`. Concurrent compiles therefore do not share
mutable type state. This is the same isolation the CLI has, and it is the reason master's compiler
survives concurrency at all - documented in [T12](plans/parallel-compiler-plan.md) and measured at
about **18% of a warm compile**.

**This is where this branch and LSPAPI actively disagree.** T1 removes the clone to reclaim that
18%; LSPAPI keeps it. Everything T4/T8/T10-T15 found - the place-holder without an owner,
`m_FVisited`, `m_cRecursiveDepth`, the unsynchronized `f_listConst` reads, the retention leak - are
defects the clone hides and T1 exposes. If the engine adopts LSPAPI's compile path as-is, it gets
their safety and loses T1's win; if it keeps T1, it must also keep the fixes this branch made, and
close the retention leak in [T15](plans/parallel-compiler-plan.md).

### 4.5 What a combined design would look like

| | compiles | runs |
| --- | --- | --- |
| **sequential** | LSPAPI's `LspCompiler` as-is | runner-app; sound today |
| **parallel** | needs T1 + T4/T8/T11/T13/T14 fixes + T15's leak closed; otherwise keep cloning | needs container-creation admission control, or an explicit warm-up before concurrency |

The cheapest correct parallel-run story is to **warm the native plane deliberately before admitting
concurrency** - run one trivial module to completion at boot - which is the same shape as
[T8.1](plans/parallel-compiler-plan.md)'s fix for the root-`Object` sweep.

---

## Part 4b - Hardening the LSPAPI branch: ownership, finality, isolation

Concrete, small, and mostly deletions of mutability. Ordered by value.

### H1 - Collapse the four configuration fields into one immutable record

```java
private boolean          configured;      // :83
private ModuleRepository cfgRepo;         // :84
private String           cfgInjector;     // :85
private Connector        connector;       // :86
```

**They are not four fields describing one thing - they are two different lifecycles in one
block, and the code shows an intent the language does not honour.**

The three configuration fields are written together, once, in `configure` (`:158-160`):

```java
cfgRepo     = coreRepo;
cfgInjector = customInjector;
configured  = true;        // deliberately LAST
```

Writing the flag last is the classic "publish only when the data is ready" ordering, so the intent
is unmistakable. It buys nothing here: without `volatile` there is no happens-before for a reader
that does not take `LOCK`, and the JMM permits a reader to see `configured == true` before either
reference. **The design is visible in the code and unenforced by it, and it is written down
nowhere** - no comment states that the flag guards the other two, which is why the six unsynchronized
reads look reasonable at each individual site.

`connector` is a different lifecycle entirely: written once, lazily, in `ensureConnector` (`:127`),
long after configuration. Grouping it with the configuration fields hides that it is a derived
resource rather than a setting - and that it *depends* on configuration being complete, since
`ensureConnector` reads `cfgRepo` to build it.

That dependency is unchecked. **`ensureConnector()` is `public` and does not call
`verifyConfigured()`**, so calling it before `configure(...)` builds a connector on a null
repository. `LspTest` calls it directly (`support.ensureConnector()`), so it is a real entry point,
not a theoretical one.

Replace with a single reference:

```java
private record Config(ModuleRepository repo, String injector) {}
private volatile Config config;           // null == not configured
```

This is worth more than adding `volatile` to each field, because it changes what is expressible:

- **"half-configured" stops being a state.** The record's fields are `final`, so publishing it
  through one volatile write publishes them safely as a unit. The
  `configured == true` / `cfgRepo == null` window in 4.1 cannot occur.
- **`configured` stops being separate data**; it becomes `config != null`, so the two cannot
  disagree.
- **The reads need no lock at all**, which removes the disagreement between the two methods that
  take `LOCK` and the six places that do not.

### H2 - The singleton is not actually a singleton, and the lock assumes it is

```java
LspSupport() {}                                        // package-private, not private
private static class Singleton {
    static LspSupport instance = new LspSupport();     // not final
}
private static final Object LOCK = new Object();       // STATIC lock, INSTANCE state
```

Three defects that only combine into correctness by convention:

1. the constructor is **package-private**, so any class in `org.xvm.api` can make a second instance;
2. `Singleton.instance` is **not `final`**, so it is reassignable;
3. `LOCK` is **static** but guards **instance** fields - correct only while exactly one instance
   exists, which (1) and (2) do not guarantee.

Make the constructor `private`, make `instance` `static final`, and either make the lock an instance
field or drop it entirely once H1 removes the need. If a second instance is ever wanted - and this
branch argues it should be, see 3.3 - then the static lock is actively wrong and must go first.

### H3 - `xExternalConsole.INSTANCE` is a mutable public static, newly used across threads

```java
public static xExternalConsole INSTANCE;               // :31

public xExternalConsole(Container container, ClassStructure structure, boolean fInstance) {
    super(container, structure, false);
    if (fInstance) {
        INSTANCE = this;                               // this-escape from a constructor
    }
}

public static long register(NativeContainer container, PrintStream out) {
    ... INSTANCE.getCanonicalType() ...                // read from a HOST thread
}
```

**In fairness this is the house pattern** - 144 native templates declare
`public static X INSTANCE`, including `xTerminalConsole` which this class extends. It is not a new
sin. What *is* new is the exposure: `register`/`unregister` are static entry points called by the
LSP control layer on a host thread, while `INSTANCE` was written by a constructor on the boot
thread, into a field that is neither `final` nor `volatile`. The old pattern was only ever read from
runtime threads that had already synchronized with boot.

Minimum: make it `volatile`, or set it outside the constructor so `this` does not escape before
construction finishes. Better: have `register` take the template explicitly - the
`NativeContainer` can resolve it - so the static is not on the path at all. This is the same
mutable-static-`INSTANCE` class this branch has spent months removing, and the fix here is one field.

### H3b - `ensureConnector()` is public and skips the precondition it depends on

Covered under H1: it reads `cfgRepo` and is callable before anything sets it. Either call
`verifyConfigured()` at the top, or make it non-public and let `getConstantPool()`/`run(...)` - which
do check - be the entry points.

### H4 - `addResourceSupplier` is check-then-act on a concurrent map

```java
public void addResourceSupplier(InjectionKey key, InjectionSupplier supplier) {
    assert !f_mapResources.containsKey(key);           // check ...
    f_mapResources.put(key, supplier);                 // ... then act
    f_mapResourceNames.put(key.f_sName, key);
}
```

The map became `ConcurrentHashMap` precisely because registration is now concurrent, but the
assertion that a key is unregistered is not atomic with registering it. Today the keys carry a
unique `console_<id>` so a collision is unlikely; the guard still asserts something it cannot see.
`putIfAbsent` states and enforces the same invariant in one operation.

**Credit where due:** the matching hazard on the read side *was* handled -
`removeResourceSupplier` updates two maps non-atomically, and `getInjectable` was changed to look
the supplier up and null-check it rather than assume the name mapping implies one. That is exactly
the right treatment.

### H5 - `TaskRegistry` needs an eviction rule, and `Task` should release its container

From 4.1b: `tasks` is never pruned and `Task.container` is never cleared. Whatever policy is chosen -
evict on completion, bounded LRU, explicit `forgetTask(id)` - the state to assert is that **a
stopped task holds no container**. That invariant is checkable in one line and would have made the
retention visible immediately rather than as heap growth much later.

### H6 - `Task`'s observable state is publicly writable

```ecstasy
service Task(...) {
    Boolean running;      // :139
    Int?    result;       // :141
    String? failure;      // :143
    private Container? container;   // :151  - correctly private
}
```

`container` is properly private; the other three are public and mutable, so anything holding a
`Task` can assign them. They are outputs, not inputs. Making them `@RO` with private setters - the
same shape `status.get()` already uses - states that they are computed by the task and observed by
everyone else.

### H7 - State that could be described but is not

Two assertions worth adding because they are cheap and encode assumptions currently living in
comments:

- **`prepareModule` must yield a pool distinct from the compiler's.** The serialize/deserialize
  round-trip exists to guarantee that; asserting `file.getConstantPool() != module.getConstantPool()`
  documents the intent and catches a future "optimization" that skips the round-trip.
- **A run's container must be a child of the native container it registered its console with.**
  This branch's `OwnershipDiagnostics` already expresses this kind of check; the LSP path creates
  containers in Ecstasy but registers resources in Java, which is exactly where the two can drift.

### H8 - Every result crossing the Java/Ecstasy boundary is an unchecked downcast

```java
taskId = ((JavaLong) hTaskId).getValue();                          // InterpreterControl:96
        : ((JavaLong) result).getValue());                         // :168
        : ((StringHandle) result).getStringValue());               // :175
```

`MainContainer.invokeAsync` returns `CompletableFuture<ObjectHandle>`, so every caller casts to the
shape it expects. If a runner method's signature ever changes, the failure is a
`ClassCastException` inside the LSP control layer, blaming the caller rather than the Ecstasy method
that returned the wrong thing.

This is the `ObjectHandle`-as-calling-convention problem - **[E22](plans/master-enhancement-submissions.md)
counts 1,439 such casts, about 50% of all casts in the tree** - and the new API adds to it rather
than containing it. Contained cheaply: typed wrappers over `invokeAsync` (`invokeLong`,
`invokeString`, `invokeBoolean`, or one `invokeAsync(String, Class<T>, ObjectHandle...)`), so the
cast happens once, in one place, with a message naming the method and the type it actually returned.

### H9 - `new Object[] {...}` at every diagnostic site - and this branch already fixed it

Six sites in `LspSupport` build an array by hand to log a diagnostic:

```java
errs.log(ERROR, ERR_INTERNAL, new Object[] {e, "Compilation failed"}, null);
```

**This is not their fault: master's `ErrorListener` has no varargs `log` overload**, so the array is
the only option. This branch added `log(Severity, String, XvmStructure, Object...)` and the
`info`/`warn`/`error`/`fatal` aliases, which turn every one of those into
`errs.error(ERR_INTERNAL, e, "Compilation failed")`. It is a small, self-contained contribution that
would delete boilerplate from their branch rather than add to it.

### H10 - Java polls every 25ms for something Ecstasy already knows

```java
private void watch() {
    CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS).execute(() ->
        taskRunning().whenComplete(...));      // re-arms itself until the task stops
}
```

Each tick is a full service request into container zero, per running task, forever, on the common
ForkJoinPool. But the Ecstasy side **already has the completion event** - `Task.start` does
`&outcome.whenComplete((tuple, exception) -> ...)` (`runner.x:170`) and sets `running = False` right
there.

So the information exists and is being rediscovered by polling. A `waitForTask(id)` on the runner
that returns only when the task finishes would let Java hold **one** future per run instead of
40 requests per second per run, and would remove the JVM-wide pool from the design. The polling
looks like a placeholder for a completion channel that was not built yet.

### H12 - Write-once state that is not final, and the refactor that fixes it without work in a constructor

```java
private volatile boolean running;     // genuinely mutable - lifecycle
private volatile Instant started;     // WRITE-ONCE
private volatile Instant stopped;     // genuinely mutable
private volatile Long    result;      // genuinely mutable
private          long    taskId;      // WRITE-ONCE
private          Long    consoleId;   // write-once, then nulled on unregister
```

Three of the six never change after startup, but none can be `final`, because the object is
constructed and *then* started:

```java
return new InterpreterControl(interpreter, module, repository, console, errs).start();   // :65
```

The obvious repair - move `start()`'s work into the constructor - is the wrong one, and this branch
has a rule against it: a constructor that boots things fails at construction time for reasons
unrelated to what the caller asked for. The right shape puts the work in the **factory**, which is
already there, and hands the constructor finished values:

```java
static Control create(...) {
    Long consoleId = console == null ? null : xExternalConsole.register(native, console);
    long taskId    = postRunTask(...);                       // work happens HERE
    return new InterpreterControl(connector, module, consoleId, taskId, Instant.now());
}
```

Then `connector`, `module`, `repository`, `console`, `errs`, `started`, `taskId` and `consoleId` are
all `final`, and only `running`/`stopped`/`result` remain mutable - which they honestly are, since
they are the outcome. **The class stops having a "constructed but not yet valid" state at all**,
which is the state that made `taskId`'s safe publication depend on an unstated ordering (see 4.1).

`LspCompiler.module` (`:276`) is the same shape - assigned in `process()`, read by `getModule()` -
but there it is forced by the inherited `int process()` protocol, which has nowhere to return a
module. Worth a comment rather than a refactor.

#### The diff

```diff
--- a/javatools/src/main/java/org/xvm/api/InterpreterControl.java
+++ b/javatools/src/main/java/org/xvm/api/InterpreterControl.java
@@ -55,17 +55,29 @@
     /**
      * Create and start a control for the specified module.
      */
     static LspSupport.Control create(Connector connector, ModuleStructure module,
                                      ModuleRepository repository, PrintStream console,
                                      ErrorListener errs) {
         if (!(connector instanceof InterpreterConnector interpreter)) {
             throw new IllegalArgumentException("An InterpreterConnector is required");
         }
-        return new InterpreterControl(interpreter, module, repository, console, errs).start();
+
+        // Do the starting work HERE, then hand the constructor finished values, so the object has
+        // no "constructed but not yet valid" state and its identity fields can all be final.
+        Long consoleId = console == null
+                ? null
+                : xExternalConsole.register(interpreter.getNativeContainer(), console);
+        long taskId;
+        try {
+            taskId = postRunTask(interpreter, module, repository, consoleId);
+        } catch (RuntimeException e) {
+            unregisterConsole(interpreter, consoleId);
+            throw e;
+        }
+
+        var control = new InterpreterControl(
+                interpreter, module, console, errs, Instant.now(), taskId, consoleId);
+        control.watch();
+        return control;
     }
 
-    private InterpreterControl(InterpreterConnector connector, ModuleStructure module,
-                               ModuleRepository repository, PrintStream console,
-                               ErrorListener errs) {
-        this.connector  = connector;
-        this.module     = module;
-        this.repository = repository;
-        this.console    = console;
-        this.errs       = errs;
-    }
-
-    private InterpreterControl start() {
-        started = Instant.now();
-        running = true;
-
-        try {
-            FileStructure file = prepareModule();
-            MainContainer main = connector.getMainContainer();
-
-            if (console != null) {
-                consoleId = xExternalConsole.register(connector.getNativeContainer(), console);
-            }
-
-            ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, file.getModule());
-            ObjectHandle hRepository = xCoreRepository.INSTANCE.makeHandle(repository);
-            ObjectHandle hConsoleId  = consoleId == null
-                    ? xNullable.NULL
-                    : xInt64.makeHandle(consoleId);
-            ObjectHandle hTaskId = postRequest("runTask", hModule, hRepository, hConsoleId).join();
-            taskId = ((JavaLong) hTaskId).getValue();
-        } catch (RuntimeException e) {
-            stopped = Instant.now();
-            running = false;
-            unregisterConsole();
-            throw e;
-        }
-
-        watch();
-        return this;
-    }
+    private InterpreterControl(InterpreterConnector connector, ModuleStructure module,
+                               PrintStream console, ErrorListener errs,
+                               Instant started, long taskId, Long consoleId) {
+        this.connector = connector;
+        this.module    = module;
+        this.console   = console;
+        this.errs      = errs;
+        this.started   = started;
+        this.taskId    = taskId;
+        this.consoleId = consoleId;
+    }
+
+    /**
+     * Prepare the module and post the run request. Static: it runs before any control exists.
+     */
+    private static long postRunTask(InterpreterConnector connector, ModuleStructure module,
+                                    ModuleRepository repository, Long consoleId) {
+        FileStructure file = prepareModule(connector, module, repository);
+        MainContainer main = connector.getMainContainer();
+
+        ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, file.getModule());
+        ObjectHandle hRepository = xCoreRepository.INSTANCE.makeHandle(repository);
+        ObjectHandle hConsoleId  = consoleId == null
+                ? xNullable.NULL
+                : xInt64.makeHandle(consoleId);
+        ObjectHandle hTaskId = main.invokeAsync("runTask", hModule, hRepository, hConsoleId).join();
+        return ((JavaLong) hTaskId).getValue();
+    }
@@ -240,11 +252,14 @@
     private final InterpreterConnector connector;
     private final ModuleStructure      module;
-    private final ModuleRepository     repository;
     private final PrintStream          console;
     private final ErrorListener        errs;
+    private final Instant              started;
+    private final long                 taskId;
+    private final Long                 consoleId;
 
-    private volatile boolean running;
-    private volatile Instant started;
+    private volatile boolean running = true;
     private volatile Instant stopped;
     private volatile Long    result;
-    private          long    taskId;
-    private          Long    consoleId;
```

`prepareModule` and `unregisterConsole` become static helpers taking what they need; `repository`
stops being a field because only `prepareModule` used it. Three consequences beyond finality:

- **no half-built object can escape.** The old failure path set `stopped`/`running` on a
  partially-initialized `this` and rethrew, so a `Control` could in principle be observed in a state
  that never ran. Now the failure happens before the object exists.
- **`consoleId` stops being nulled.** `unregisterConsole()` currently writes `this.consoleId = null`
  as its idempotence guard; with a final field, idempotence moves to a `volatile boolean
  consoleReleased`, which says what it means.
- **`taskId`'s safe publication stops depending on the `watch()` submission ordering** (see 4.1),
  because a final field written in the constructor is safely published by the JMM.

### H13 - `Lazy` is already in the tree, unused, and this is exactly what it is for

`javatools_utils/.../util/Lazy.java` is present and tracked on their branch - it came in with the
owner-lazy work - and **`grep` finds no use of it anywhere in `javatools`**. The tool landed and
nobody applied it. `LspSupport.connector` is the textbook case:

```java
private Connector connector;                                    // non-final

public Connector ensureConnector() {
    synchronized (LOCK) {
        if (connector == null) {
            Connector connector = useJit() ? JitControl.createConnector(cfgRepo)
                                           : InterpreterControl.createConnector(cfgRepo);
            this.connector = connector;
        }
        return connector;
    }
}
```

becomes

```java
private final Lazy.Bound<LspSupport, Connector> f_connector =
        Lazy.ofBound(LspSupport::createConnector);
```

and that single change:

- makes the field **`final`**, so it cannot be reassigned or observed half-published;
- **deletes the static `LOCK`**, which H2 shows is guarding instance state and is only safe while
  the un-enforced singleton holds;
- **deletes the null check**, and with it the "is it built yet" state a reader could see;
- gives **exactly-once** creation without the double-checked idiom;
- gives `isComputed()`, so a shutdown path can skip a connector that was never booted rather than
  booting one in order to close it.

`ofBound` rather than `of` is deliberate: `Lazy.of(this::createConnector)` in a field initializer
captures `this` before construction finishes, which is a constructor this-escape. `Lazy.Bound` takes
the owner as a parameter at `get(this)` time and avoids it.

#### It is NOT a drop-in, and the differences matter

Checked against `Lazy`'s implementation rather than assumed:

| behaviour | their `ensureConnector` | `Lazy` | same? |
| --- | --- | --- | --- |
| supplier runs at most once | yes | yes - `ThreadSafeLazy.get` double-checks under `synchronized (this)` | **yes** |
| supplier throws | `connector` stays null, next call retries | `valueRef` stays `UNSET` and the supplier is not cleared, next call retries | **yes** |
| value publication | non-final field written under a lock, read under the same lock | `AtomicReference` / `VarHandle.getVolatile` | **yes, and stronger** |
| mutual exclusion scope | a **static** `LOCK`, shared by every instance | `synchronized (this)` on the holder, i.e. **per instance** | **NO** |
| serialized against `configure(...)` | yes - `configure` takes the same static `LOCK` | no - different monitors | **NO** |

The last two are why this cannot be applied on its own. Today `configure(...)` and
`ensureConnector()` are mutually exclusive because they share `LOCK`, so a thread cannot be reading
`cfgRepo` to build a connector while another thread is writing it. `Lazy` would remove that.

It is worth being precise about how much that protection is actually worth: it is **incidental, not
designed**. The lock narrows the window but does not establish the precondition - a thread that
takes the lock first still builds a connector on a null `cfgRepo`, which is H3b. So the current code
is not correct either; it is merely less likely to be observed failing.

**The three changes only work together:**

1. **H1** makes configuration one immutable `Config` behind a volatile, so reading it is atomic and
   needs no lock;
2. **H3b** makes the connector's supplier *assert* it, so the precondition is stated instead of
   assumed:
   ```java
   private Connector createConnector() {
       Config cfg = config;      // one volatile read
       if (cfg == null) {
           throw new IllegalStateException("configure(...) must be called before ensureConnector()");
       }
       return useJit() ? JitControl.createConnector(cfg.repo())
                       : InterpreterControl.createConnector(cfg.repo());
   }
   ```
3. **H13** then holds it in a final `Lazy` field, and the static `LOCK` can be deleted rather than
   merely bypassed.

Applied in that order the result is strictly stronger than today. Applied alone, H13 trades a real
(if incidental) serialization for none.

#### Other `Lazy` candidates in the new code: none

Scanned, so the answer is not a shrug. Within what LSPAPI adds, `connector` is the only clean
lazy-initialization: `xExternalConsole.INSTANCE` is constructor-assigned rather than lazy (H3),
`InterpreterControl` holds no deferred state once H12 lands, and `LspCompiler` is short-lived.
`verifyConfigured`'s `XDK_HOME` fallback is *shaped* like a lazy default, but it belongs in H1's
explicit configuration rather than behind another holder.

Two adjacent populations, for scope rather than for this review: `NativeContainer` - a file LSPAPI
already touches - has cache-if-null getters at `:421`, `:456`, `:470`, `:484` and more, and
[E31](plans/master-enhancement-submissions.md) counts **42 such getters** across the tree that could
be final `Lazy` fields, of which 7 need a resettable variant. None of that is this branch's to fix;
it is where the same change pays next.

### H14 - `PrintStream` as the console type is a new decision, and it disagrees with its own parent class

**This is new in LSPAPI, and the codebase already had two better answers - one of which `LspSupport`
itself is already using.**

| where | abstraction |
| --- | --- |
| `org.xvm.tool.Console` (existing interface) | `out()` / `err()`, **separate streams** - and `LspSupport` uses it for `SILENT_CONSOLE` (`:71`) |
| `xTerminalConsole` (existing, the class `xExternalConsole` **extends**) | `PrintWriter CONSOLE_OUT`, built from `System.console().writer()` when available (`:226-237`) |
| `xExternalConsole` / `LspSupport.run` (**new**) | `PrintStream` |

So a single class, `LspSupport`, now uses `Console` for the compiler's output and `PrintStream` for a
run's output, and the new native template takes a byte stream while the template it inherits from
writes through a character writer. That is three abstractions for one concept.

**Why `PrintStream` is the wrong one of the three, concretely:**

1. **It cannot report a write failure.** `PrintStream` never throws `IOException`; it sets an
   internal flag that the caller is expected to poll with `checkError()`, and nothing here does. If
   the host's stream is a closed socket or a full disk, **the application's output is silently
   discarded** and the run reports success. This is the never-swallow rule violated by the *choice of
   type* rather than by a `catch` block - the swallowing is inside the JDK class.
2. **The character encoding becomes the caller's accident.** Ecstasy strings arrive as `char[]` and
   are written with `out.print(ach)` (`:100-118`). `PrintStream` encodes them with whatever charset
   it was constructed with, and `LspTest` uses `new PrintStream(bytes, true)` - the platform default.
   The parent class deliberately goes through `System.console().writer()` precisely to get the
   console's real encoding. The new path throws that away, and the first non-ASCII test will find it.
3. **Auto-flush is a requirement the API cannot state.** `xExternalConsole` flushes explicitly after
   `print` (`:103`, `:118`) but not after `println` (`:100`, `:112`), so line output depends on the
   stream having been constructed with `autoFlush = true`. `LspTest` passes `true`; a host that
   forgets gets a console that appears to produce nothing. A type that carried the guarantee - or an
   interface with a `flush()` contract - would remove the trap.
4. **It is a concrete class, so the host cannot adapt.** Routing a run's output to a logger, an LSP
   `window/logMessage` notification, or a structured capture all require first turning it back into
   bytes. An interface - even `Consumer<String>` - would let the host receive text.
5. **`out` and `err` are conflated.** `InterpreterControl.finish` writes
   `console.println("Unhandled exception: " + failure)` into the same stream as ordinary output, so a
   host cannot distinguish a crash report from what the program printed. The existing
   `org.xvm.tool.Console` already separates them.

**Recommendation, in order of preference:**

- reuse `org.xvm.tool.Console` - it exists, it is already imported by this file, it separates
  `out`/`err`, and it makes the compiler and run paths speak the same type;
- failing that, `PrintWriter` or `Appendable`, which at least matches the `char[]` data and the
  parent class;
- if a functional sink is wanted, `Consumer<String>` composes with everything and costs nothing.

**A related loose end.** `Control.console()` is documented as returning *"the File containing the
output that the application printed to the Console"*, and `InterpreterControl.console()` returns
`null` unconditionally. An interface method that always returns null is a promise the implementation
does not keep; either it is unimplemented and should say so, or it should be removed until there is
a file to return.

### H15 - What is NOT a smell here, having checked

Worth recording so a reviewer does not re-raise them:

- **`SILENT_CONSOLE` as an anonymous class is correct.** `Console` declares four methods, so it is
  not a functional interface and cannot be a lambda.
- **`removeResourceSupplier`'s two-map update is deliberately tolerated**, and correctly - see H4.
- **`xExternalConsole`'s `switch` on method name** is the established native-template dispatch
  convention, not new code. ([E23](plans/master-enhancement-submissions.md) proposes replacing that
  convention wholesale across 744 labels; it is not this branch's to fix.)

## Part 5 - Porting cost, here and on master

### 5.1 Into this branch

Three real obstacles, all from this branch being *ahead* of master rather than behind:

1. **This branch deleted the ambient pool.** `ConstantPool.withPool` / `getCurrentPool` were removed
   here (E3); `XvmStructure.java:416` records that "ownership is always a parameter". LSPAPI's
   `MainContainer.invokeAsync` and the `compiler.Compiler` phases all use
   `try (var _ = ConstantPool.withPool(...))`. Porting means rewriting those to pass ownership
   explicitly - mechanical, and in the direction this branch already argues for.
2. **`NestedContainer.createForHost` has to go**, along with `registerInjections`, replaced by the
   runner-app path. That is a deletion, which is the good kind of change.
3. **The `ErrorListener` work overlaps.** LSPAPI's `ErrorList` -> `ErrorListener` change in
   `compiler.Compiler` is a subset of what this branch did; they converge, and this branch's version
   is the more complete one.

Nothing here is architecturally hard. The work is largely deleting this branch's run path.

### 5.2 On a merged-LSPAPI master

If LSPAPI lands and `XtcEngine` is rebuilt fresh on top of it, the shape is much simpler, because
most of what `XtcEngine` currently does would be provided:

- **compile** - wrap `LspSupport.compile`, or subclass `tool.Compiler` the same way `LspCompiler`
  does. Keep this branch's per-call input repository and the `TeeErrorListener` fix (the missing
  `branch()` override that caused the `lib_json` miscompile), neither of which LSPAPI has.
- **run** - boot the runner app, post `runTask`, wrap `Control`.
- **do not** reproduce `LspSupport`'s singleton. Take the connector-and-runner model, keep the
  builder.
- **contribute back** what LSPAPI leaves unimplemented: `run(...)` currently throws
  `UnsupportedOperationException` for `rootDir`, `injections` and `customInjector`. This branch has
  working per-run `String`/`String[]` injections and the ownership analysis behind them; on the new
  model they belong in `TaskResourceProvider` on the Ecstasy side rather than in Java.

The engine then becomes a thin, instance-based, builder-configured facade over their two APIs -
which is what it should have been, and roughly a third of its current size.

---

## Part 6 - The migration: what to lift, in what form, in what order

Measured gap, not estimated. Every piece of the runner machinery is **absent** from this branch:

| piece | here |
| --- | --- |
| `lib_runner` (`runner.xtclang.org`) | absent |
| `MainContainer.invokeAsync` | absent |
| `InterpreterConnector.getMainContainer` / `getNativeContainer` | absent |
| `xExternalConsole` | absent |
| `xCoreRepository` per-handle repository | absent - still reads `f_container.getModuleRepository()` |
| `NativeContainer` concurrent resource maps + add/remove | absent - still `HashMap`, registration private |

That is good news: the set is well defined and nothing has to be reconciled with a local variant.
`Connector` and `InterpreterConnector` already exist here unchanged; `XtcEngine` simply does not use
them, booting `Runtime` + `NativeContainer` itself into a `RuntimePlane`.

### Tier 1 - lift essentially verbatim

No conflict with this branch's divergence; take them as they are.

1. **`lib_runner`** - `runner.x`, `runnerClient.x`, its `build.gradle.kts`, and the `xdk`
   settings/build wiring. Pure Ecstasy; nothing in this branch touches it.
   **Apply H5 while lifting** - give `TaskRegistry` an eviction rule and have `Task` drop its
   container when it stops - because this branch already knows (T15) what a long-lived owner without
   a release rule costs, and lifting the leak knowingly would be silly.
2. **`NativeContainer`**: resource maps to `ConcurrentHashMap`, `addResourceSupplier` /
   `removeResourceSupplier` public, `getInjectable` re-reading the supplier with a null check.
   **Apply H4** - `putIfAbsent` instead of `assert !containsKey` then `put`.
3. **`xCoreRepository`** - carry the repository on `CoreRepoHandle` and add `makeHandle(repository)`.
   Mechanical, and it is what lets a run be given its own repository.
4. **`InterpreterConnector`** - the two accessors, and `invoke0` -> `invoke`.

### Tier 2 - lift with one real adaptation

5. **`MainContainer.invokeAsync`.** The only piece that genuinely conflicts. Their implementation
   opens `try (var _ = ConstantPool.withPool(f_idModule.getConstantPool()))`, and **this branch
   deleted the ambient pool** (E3; see `XvmStructure.java:416`, "ownership is always a parameter").
   Lift the body - `findModuleMethod`, the `NativeFunctionHandle`, `postRequest` - and pass the pool
   explicitly instead of installing it ambiently. This is a small rewrite in the direction this
   branch already argues for, and it is the one place a naive copy would not compile.

### Tier 3 - take the shape, write the code here

6. **`xExternalConsole`** - lift the mechanism (named `console_<id>` resource, register/unregister),
   but **apply H14**: take `org.xvm.tool.Console` or a `PrintWriter`, not a `PrintStream`. The class
   it extends already uses `PrintWriter`, and `XtcEngine` has no `PrintStream` in its API to
   preserve compatibility with. **Apply H3** too - do not add another mutable public static
   `INSTANCE` to a branch that has spent months removing them.
7. **`InterpreterControl` -> the engine's run handle.** Lift the flow exactly - `prepareModule`'s
   serialize/deserialize round-trip, `runTask`, result collection - but build it as
   [H12](#h12---write-once-state-that-is-not-final-and-the-refactor-that-fixes-it-without-work-in-a-constructor)
   describes: the factory does the work, the constructor takes finished values, the fields are final.
   **Prefer H10's `waitForTask` over the 25ms poll** if `lib_runner` is being modified anyway for H5 -
   the completion is already in hand at `runner.x:170`, and adding one method there is cheaper than
   inheriting a polling loop.
8. **`Control`** - adopt the interface as the return type of `run(...)`, replacing
   `CompletableFuture<ObjectHandle>`. `running` / `whenStarted` / `whenStopped` / `kill` / `result`
   is strictly more than a bare future gives, and it is their vocabulary.
   Drop `console()` until it returns something (H14's loose end).

### Tier 4 - do NOT lift

9. **`LspSupport` itself.** Take `compile`/`run`/`Control` as *shapes*; leave the singleton,
   `configure`, the static `LOCK` and `verifyConfigured` behind. `XtcEngine`'s builder already
   expresses configuration immutably at construction, which is what H1 and H2 are asking `LspSupport`
   to become. Lifting it would import the exact defects this review lists.
10. **Their `LspCompiler`.** This branch's compile path is ahead: the `TeeErrorListener.branch()`
    fix (the missing budget propagation that caused the `lib_json` miscompile), the per-call input
    repository, the inter-phase error checks. Keep them. What is worth taking is the *idea* of
    subclassing `tool.Compiler` rather than reimplementing the pipeline - and the protected hooks
    (`compile(List, ModuleRepository)`, `flushAndCheckErrors`) that make it possible.

### What gets deleted here

11. `NestedContainer.createForHost` - the API that exists in neither master nor LSPAPI.
12. `XtcEngine.runFrom`'s container creation, and `registerInjections`.
13. `RuntimePlane` - replaced by the connector, which owns both the runtime and the native container.

### Where this branch's injections work goes

`LspSupport.run` currently throws `UnsupportedOperationException` for `rootDir`, `injections` and
`customInjector`. This branch has working per-run `String`/`String[]` injections. They cannot be
lifted as-is, because on the new model the injector is chosen **in Ecstasy** -
`TaskResourceProvider` already does exactly this for the console. The port is to extend
`TaskResourceProvider` and pass the injection map through `runTask`, which is a contribution to
`lib_runner` rather than to Java, and fills a hole they have explicitly left open.

### Ordering, and the one judgement call

Tier 1 first (mechanical, independently testable), then 5, then 6-8. `EngineSuiteCompileTest`
guards the compile path throughout, since none of this touches it.

**The judgement call is T1.** Their compile path clones per compile; this branch's T1 shares one
prepared library and is worth ~38% sequentially, but [T15](plans/parallel-compiler-plan.md) has an
unclosed retention leak. **Recommendation: default to their semantics** - cloning - and keep T1
behind the existing opt-in until T15 is closed. A resident host that is 38% slower is strictly better
than one that dies after ten thousand compiles, and this ordering also means the runner migration
can be evaluated without T1's variables in the same measurement.

## Summary

**What it is**

1. LSPAPI's single idea is to **move container creation from Java into a long-lived Ecstasy runner
   app**, with `TaskRegistry`/`Task` as services so the language provides the serialization.
2. It was impossible before because `MainContainer` had no way to be *called into* with a result, a
   run could not be given its own console, and a run could not be given its own repository. One
   change each fixes those.
3. The old `manualTests/runner.x` created every container at once from one fiber with no lifecycle.
   It was a fixture, not a design, and the startup races follow directly from that shape.
4. **The compiler stays a containerless Java API** - deliberately, and the code is unambiguous.
   Container zero is only for runs.

**What it means for `XtcEngine`**

5. It must drop `NestedContainer.createForHost` - which exists in neither master nor LSPAPI and is
   this branch's own invention - boot the runner app, post `runTask`, and return a `Control`. It
   should **not** adopt the singleton.
6. Parallel *compiles* are safe in LSPAPI only because the per-compile clone is retained: the same
   18% [T1](plans/parallel-compiler-plan.md) reclaims, and the reason every defect T4-T15 found was
   invisible before.

**What is not yet true**

7. The class javadoc's thread-safety claim is **not met** (`LspSupport.java:52` against `:83-86`),
   though it is **latent in the supported single-threaded scenario** - the unsynchronized fields are
   read only by the calling thread.
8. **The supported scenario has its own active defect:** `TaskRegistry.tasks` is never pruned and
   `Task.container` is never cleared, so consecutive runs in a hot VM accumulate a task and a
   container each, forever. Independently the same shape as
   [T15](plans/parallel-compiler-plan.md) on this branch's compile side.
9. Parallel *runs* are better founded than the old model but unproven; container creation still
   touches the shared native plane, which `testPoolGrows` measures without solving.

**Hardening, for a structured review.** These are recorded here and **nowhere else** - none has
been raised on PR #545, and nothing has been committed to `cpurdy/LSPAPI`. Each links to a section
above with file and line references so it can be checked rather than believed.

| | item | shape |
| --- | --- | --- |
| H1 | four config fields -> one immutable `Config` record behind a volatile | makes "half-configured" unrepresentable |
| H2 | package-private constructor, non-final `instance`, static lock over instance state | the singleton is not enforced and the lock assumes it is |
| H3 | `xExternalConsole.INSTANCE` mutable public static, written by a constructor, read cross-thread | the house pattern, newly load-bearing |
| H3b | `ensureConnector()` is public and never checks the precondition it depends on | builds a connector on a null repository; `LspTest` calls it directly |
| H4 | `addResourceSupplier` check-then-act on a concurrent map | `putIfAbsent` |
| H5 | task/container eviction | assert a stopped task holds no container |
| H6 | `Task.running`/`result`/`failure` publicly writable | `@RO` |
| H7 | two cheap assertions encoding assumptions now living in comments | pool distinctness; container parentage |
| H8 | every Java/Ecstasy result is an unchecked `ObjectHandle` downcast | typed `invokeAsync` wrappers; E22's 1,439-cast problem, extended |
| H9 | `new Object[]{}` at six diagnostic sites | master lacks a varargs `log`; **this branch already added one** |
| H10 | Java polls every 25ms for a completion Ecstasy already has | a `waitForTask(id)` future instead of 40 requests/sec/run |
| H12 | write-once fields that cannot be `final` because the object is constructed then started | do the work in the factory, hand the constructor finished values |
| H13 | `Lazy` is already upstream and **entirely unused**; `connector` is the textbook case | one `Lazy.ofBound` field deletes the lock, the null check and the mutability |
| H14 | `PrintStream` as the console type - new, and disagrees with both existing abstractions | swallows write failures, charset is the caller's accident, conflates out/err |
| H15 | what is NOT a smell, having checked | anonymous `Console`, the two-map update, native `switch` dispatch |
