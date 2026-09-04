# The `cpurdy/LSPAPI` branch, and what `XtcEngine` has to become

Analysis of `cpurdy/LSPAPI` at `2568d6be4` ("All reproducers are working as expected"), branched
from master at `443770bcc`. Checked out locally at `../lspapi`. **No code was changed anywhere for
this analysis.**

The branch is 19 files, +1761/-90. It is small, and almost all of it exists to make one structural
change possible.

> **Status.** Nothing has been raised on PR #545, nothing has been committed to `cpurdy/LSPAPI`,
> and the `../lspapi` checkout is unmodified - verified by `git status` returning zero changed files.
>
> **The migration itself HAS been carried out in `lagergren/lazy-instance`** (see
> [Part 6](#part-6---the-migration-what-to-lift-in-what-form-in-what-order) for what landed and what
> did not). Several findings below - H5, H19, H20, H21 - were found *by* doing that rather than by
> reading, which is why they carry test evidence. The hardening items in
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

## Part 3 - What `XtcEngine` has to become (DONE - see Part 6 for the outcome)

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

### Corrections to the review comments already posted on 2026-08-28

Six review threads were posted before this analysis existed. Re-checked against `2568d6be4`; the
playbook carries the disposition, but two of them need recording here because they change what this
document should claim.

**Two were real and have since been fixed upstream**, so neither is a live finding:

- `LOCK` was not `final` when commented; `LspSupport.java:69` is now
  `private static final Object LOCK = new Object();`. Every mutual-exclusion argument in H2 and H13
  below assumes the fixed form, which is correct as of `2568d6be4`.
- `InterpreterControl` loaded `"org.ecstasy.runner"` while `runner.x:6` declares
  `module runner.xtclang.org`. `InterpreterControl.java:50` now loads the declared name. This
  document never assigned the mismatch an `H` number; it is recorded here only so it is not raised
  again.

**One was wrong and H13 retracts it.** The thread on `LspSupport.java:86` says `connector` "is the
one field here with a genuine reason not to be final", conceding lazy initialization as grounds for
mutability. H13 shows that is not so: a `final Lazy.Bound` field is lazy *and* final, which is why
the static `LOCK` can then be deleted rather than bypassed. The same comment also asserts the field
is safe because it is only read under `LOCK` - true (`ensureConnector` is `synchronized` at `:122`,
and the only other use, `:480`, goes through it) but beside the point, because it misses the defect
H1 records: `ensureConnector` is public and never calls `verifyConfigured`, so calling it before
`configure` builds a connector on a null `cfgRepo` (`:125-126`).

**Correction, 2026-09-04.** An earlier draft of this section said `LspTest` does exactly that. It
does not. `LspTest.main` calls `configure(repo(), null)` at `:49` before invoking any of the five
scenario methods, and the only `ensureConnector` call is at `:184` inside `testPoolGrows`, which runs
last. So the null-repository path is **latent** - reachable by any caller of a public method, not
reached by anything upstream today. The comment being wrong does not depend on it: conceding
mutability for lazy initialization is wrong whether or not the second defect is reachable.

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

#### If it were not a singleton and two were run, would that break? YES - and here is what breaks

This is the question that decides whether the singleton is a style choice or load-bearing. It is
load-bearing, and the reason is not in `LspSupport` at all.

`xExternalConsole.register(...)` is a **static** method that reads a **mutable static**:

```java
public static xExternalConsole INSTANCE;                       // :31
public xExternalConsole(Container container, ...) { ... INSTANCE = this; }   // :37

public static long register(NativeContainer container, PrintStream out) {
    container.addResourceSupplier(
            new InjectionKey(consoleName(id), INSTANCE.getCanonicalType()),   // :49
            (frame, opts) -> INSTANCE.ensureConsole(frame, out));             // :50
}
```

Two `LspSupport` instances mean two connectors, two `NativeContainer`s, and therefore **two
`xExternalConsole` instances - the second overwrites `INSTANCE`**. From then on
`register(containerA, out)` builds its injection key and its supplier from container **B**'s
template, whose `f_container` is B, so the console's service context comes from the wrong container.

And that is one instance of a class: **144 templates in master declare a mutable
`public static X INSTANCE`**, versus **0** in this branch. So on master the runtime cannot host two
independent connectors, and the singleton is the honest response to that - not laziness.

**Which reframes the objection.** The singleton is a *symptom*. The defect is the template design,
already filed as [E1](plans/master-enhancement-submissions.md), and this is new evidence for it: the
static `INSTANCE` fields are what make an instance-based tool API impossible upstream. That this
branch removed all 144 is precisely why `XtcEngine` *can* be instance-based - the campaign that
started this branch is what buys the choice.

#### Why do they want a singleton at all, and is there an alternative?

Worth asking rather than just objecting. The defensible reasons:

- **container zero is expensive to boot** - it starts a runtime, a native container, loads
  `runner.xtclang.org` and runs its `run()` - so it should be shared rather than repeated;
- **an LSP server is one process serving one workspace**, so one connector is all that is ever
  wanted, and a singleton makes that the path of least resistance;
- it removes lifecycle questions from the caller: nothing to construct, nothing to close.

**But none of those requires a singleton, and one of them is not even true.** Sharing an expensive
resource is a *caller's* decision, not a property the class has to enforce. And the assumption
underneath - that a JVM can only host one runtime - does not hold: `XtcEngine` in this branch boots
its own `Runtime` and `NativeContainer` per instance, and the suite runs several engines in one JVM.

**The alternative is what this branch already does:** an instance with a builder, configured
immutably at construction. It gives everything the singleton gives, because a host that wants one
shared connector simply holds one instance - and it gives back what the singleton takes away:

| | singleton + `configure` | instance + builder |
| --- | --- | --- |
| two workspaces / module paths in one JVM | impossible | natural |
| tests in one JVM | first test wins, the rest see its configuration | each test builds its own |
| configuration validity | a mutable flag plus three fields (H1) | final fields, valid on construction |
| lifecycle | implicit and unbounded | `close()` on something you own |
| sharing | forced | the caller's decision |

The one thing the singleton offers that the builder does not is a *default* - "just call
`instance()`" - and that is a static convenience method over an instance, not a reason to make the
type itself a singleton.

**Plan for this branch, as best practice:** take the connector-and-runner model, leave
`LspSupport`'s singleton behind. Concretely:

1. `XtcEngine` stays **instance-based with a builder**; configuration is final at construction, so
   there is no `configured` flag, no one-shot `configure`, and no half-configured state (H1 for
   free);
2. each engine owns **its own connector**, booted lazily so that compiling never requires a runtime;
3. `close()` releases that engine's connector and nothing else - no process-global teardown;
4. two engines with different module paths coexist, which is what the test suite already does;
5. a host that wants one shared engine holds one, which is a caller's decision rather than the
   type's.

This is only safe **because** the static-`INSTANCE` templates are gone here. Any port of this shape
to master has to take E1 first, or it will hit the `xExternalConsole` failure above. If upstream
wants the convenience, `LspSupport.instance()` can remain as a thin default over an instance-based
implementation, which is a strictly smaller commitment than the current one-shot `configure`.

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

> **Superseded by [H17](#h17---module-output-now-has-two-mechanisms-for-one-concern).** The
> observation stands as a description of their code, but its remedy is moot if the better answer is
> taken: giving `xTerminalConsole` an instance sink means `xExternalConsole` does not exist, so
> neither does its static. Fix H3 only if the second template is kept.

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

**Is this a real bug? Yes - and here is what was verified rather than assumed.**

Certain, from the code:

1. `tasks` has exactly four references in the module: the declaration (`:94`), `tasks[id] = task`
   (`:101`), `contains` (`:125`) and `get` (`:130`). **No removal anywhere.** One permanent entry per
   `runTask`.
2. `kill()` (`:182`) calls `container.kill()` and sets `running = False`; it does **not** clear
   `container`. On normal completion `kill()` is never called at all, so even that path does not run.
3. A Java `Container` holds `f_mapCompositions` (`Map<TypeConstant, ClassComposition>`,
   `Container.java:743`) and `f_mapTemplatesByType` (`:748`), both populated lazily as it runs.

Which gives the retained chain:

```
TaskRegistry (static service - lives as long as container zero, i.e. the JVM)
  -> tasks -> Task -> Container
      -> f_mapCompositions / f_mapTemplatesByType
          -> TypeConstants -> ConstantPool -> FileStructure
```

**Not verified: the magnitude.** How many megabytes a finished `Container` actually pins has not
been measured, and is not claimed. The unbounded *reference* retention is certain; the cost per run
is not. That distinction matters here because getting it wrong in the other direction is exactly the
error [T9](plans/parallel-compiler-plan.md) records - a short measurement turned into a confident
generalisation that did not survive more compiles.

**In fairness:** one of the branch's own commits is titled *"Add runner.x API (WIP)"*. This is a real
defect in work-in-progress code, not a shipped bug, and it is easy to miss precisely because nothing
fails until much later - the same reason T15 took a long soak to find on this branch's compile side.

**Fixed on the way in.** `lib_runner` as lifted here adds `forgetTask(id)` for the caller to evict
once it has collected result and failure, clears `container` when a run completes or is killed, and
adds an idempotent `release()`. Eviction has to be explicit rather than automatic on completion,
because the Java control flow polls `taskRunning` and only then reads `taskResult` and `taskFailure`
- an id removed at completion would break the collection it exists to serve.


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

**Recommendation - CORRECTED after implementing it.** The first version of this section
recommended reusing `org.xvm.tool.Console`, on the grounds that it exists, is already imported by
this file, and separates `out`/`err`. **That is wrong, and trying it is what showed why.**
`Console` is LINE-oriented: `out(Object)` always prints a line and there is no partial-line
primitive. The Ecstasy console's contract is `print(Object, Boolean suppressNewline)`, so a
suppressed newline cannot be expressed through it at all. `Console` is the right type for the
TOOL's messages and the wrong type for a program's output - which is [H17](#h17---module-output-now-has-two-mechanisms-for-one-concern)'s
distinction, arrived at from the other direction.

So, in order of preference:

- **`PrintWriter`** - what the parent class already uses, takes the `char[]` the runtime produces
  without an encoding guess, has `flush()`, and cannot silently swallow a write failure. This is
  what this branch implemented;
- `Appendable` if only text is needed and flushing is the caller's business;
- `Consumer<String>` if a functional sink is wanted, though it loses the partial-line distinction
  unless the boolean is passed too.

Not `org.xvm.tool.Console`, and not `PrintStream`.

**A related loose end.** `Control.console()` is documented as returning *"the File containing the
output that the application printed to the Console"*, and `InterpreterControl.console()` returns
`null` unconditionally. An interface method that always returns null is a promise the implementation
does not keep; either it is unimplemented and should say so, or it should be removed until there is
a file to return.

### H16 - Was a new native console needed at all? Yes - and here is the comparison

`xExternalConsole.java` and `ExtermalConsole.x` are both **new files** in the branch (9 new files in
total). Worth asking whether they had to be, because a per-run console redirect **already existed**.

**The existing option is Ecstasy-side**, in `manualTests/runner.x`:

```ecstasy
const ConsoleBuffer implements Console {
    ConsoleBack backService = new ConsoleBack();
    void print(Object object = "", Boolean suppressNewline = False) {
        backService.print(object.toString(), suppressNewline);
    }
}
service ConsoleBack { private StringBuffer buffer = new StringBuffer(); ... }
```

injected through `RunnerResourceProvider`. It needs **no Java changes whatsoever** - no native
template, no dynamic resource registration, no concurrent resource maps.

**There is no Java variant.** `xTerminalConsole` writes to a static `CONSOLE_OUT` and cannot be
redirected per instance, which is precisely why a new template was needed.

| | Ecstasy `ConsoleBuffer` (existing) | `xExternalConsole` (new) |
| --- | --- | --- |
| Java changes required | none | concurrent resource maps, public add/remove, dynamic registration |
| delivery | **batch** - a `String` read at the end via `backService.toString()` | **streaming** - written to the host sink as it happens |
| memory for a long run | unbounded Ecstasy `StringBuffer` | none retained |
| host integration | marshal a String back across the boundary | the host's own sink receives it directly |

**So the new template is justified**, and for a real reason rather than novelty: a host wants output
as it is produced, and a long-running module must not accumulate its entire output in an Ecstasy
buffer first. The cost is the `NativeContainer` work in Part 1.3, which is what pays for it.

**Two trivia worth fixing while it is new.** The Ecstasy file is named `ExtermalConsole.x` while
declaring `service ExternalConsole`; every sibling in that directory (`TerminalConsole.x`,
`RTBuffer.x`, `RTChannel.x`) matches its declaration. Nothing references the filename so it compiles,
but it breaks the convention and defeats a search for the name. And see
[H14](#h14---printstream-as-the-console-type-is-a-new-decision-and-it-disagrees-with-its-own-parent-class)
for the stream type: the parent writes through a `PrintWriter`, so the child taking a `PrintStream`
puts a byte stream under a char-oriented parent.

### H17 - Module output now has two mechanisms for one concern

Tracing what the CLI actually does separates a real wart from an apparent one.

**Not a wart.** The CLI `Runner` takes an `org.xvm.tool.Console` for the TOOL's own messages -
diagnostics, usage, compiler output. A running module's `@Inject Console` is a different thing
entirely: the program's own output. Two concerns, two abstractions, correctly.

**A real wart.** *Module* output now has **two** mechanisms:

| | CLI | LSP / engine |
| --- | --- | --- |
| sink | the **static** `xTerminalConsole.CONSOLE_OUT` | a per-run `PrintStream` on `xExternalConsole` |
| type | `PrintWriter` | `PrintStream` |
| redirectable | **no** - hard-wired at `:225-237`, written at `:240`, `:248` | yes |
| class | `xTerminalConsole` | a second template, plus a second Ecstasy declaration |

One concern, two code paths, two stream types, and a second native template - and neither can do
the other's job: the CLI's console cannot be redirected, and the new one cannot be the terminal.

**The fix is to delete the second mechanism rather than add to it.** `xTerminalConsole`'s sink is
static only because nothing ever needed it otherwise. Give the template an instance sink defaulting
to `CONSOLE_OUT` and:

- the CLI keeps its behaviour exactly - the default sink *is* the terminal;
- a host registers a console with its own sink, with no second class;
- there is one type (`PrintWriter`, which [H14](#h14---printstream-as-the-console-type-is-a-new-decision-and-it-disagrees-with-its-own-parent-class) argues for anyway), one code path, and one place where console semantics live;
- `xExternalConsole.java` and `ExtermalConsole.x` - and its filename typo - are not needed at all.

**A caveat that matters:** none of this says the CLI is the reference implementation. The launcher
side is itself incompletely wired for both consoles and error listeners - that is the subject of
[E32, E34 and E35](plans/master-enhancement-submissions.md), which count 665 listener parameters, 87
`BLACKHOLE` sites and an error sink smuggled through a resolution callback. So the argument is not
"make the LSP path match the CLI". It is that **one concern should have one mechanism**, and that
mechanism should be designed rather than inherited from either side's status quo. Unifying on a
sink-parameterised console is a step toward that; it does not fix the launcher's own wiring, and it
should not be described as if it did.

**This branch implements it that way**, so the engine's run path and the CLI share one console
implementation rather than forking it. It is a smaller change than the one it replaces: the
per-instance sink and the registration helper, against a new template, a new Ecstasy service and a
new mutable public static.

### H18 - `ConsoleLog` is a shared unsynchronized ring buffer, written on every print

Found while wiring the console. `xTerminalConsole` writes to `CONSOLE_LOG` on **every** console
print:

```java
CONSOLE_LOG.log(ach, false);      // in the PRINT continuation
CONSOLE_LOG.log(ach, true);       // in the PRINTLN continuation
```

`CONSOLE_LOG` is a `public static final ConsoleLog` - one instance for the whole JVM - and
`ConsoleLog` is a 1024-entry ring buffer:

```java
private final String[] m_asLine = new String[1024];
private int            m_cLines = 0;
private int            m_iLine  = 0;
```

with **zero** occurrences of `synchronized`, `volatile`, `Atomic` or any lock **in the entire
file** - verified by grep on both branches. Two threads printing concurrently race on `m_iLine` and
`m_cLines`: lines lost, slots overwritten, and `get(i)`/`render(...)` reading a torn state.

**This is a master defect, not an LSPAPI one** - the file and the call sites are identical on both -
but the runner model is what makes it *routine*, because concurrent runs are the point of it. Before,
two services printing at once was possible but unusual; now it is the design.

**Mitigated here.** In this branch's sink-parameterised console, only the terminal console feeds
`CONSOLE_LOG` - a redirected run writes to its own writer and does not touch the shared buffer. That
narrows the exposure to genuinely-concurrent *terminal* printing rather than fixing `ConsoleLog`,
which still needs synchronizing or replacing with a concurrent structure.

### H19 - The runner cannot give a run both a complete resource set AND per-run isolation

Found by wiring the engine to the runner and running this branch's existing ownership tests. It is
the most consequential gap found, because it is not a defect that can be patched - the API has no
way to express what is needed.

**What LSPAPI's runner does.** `Task.start` picks one of two injectors:

```ecstasy
if (Int consoleId ?= this.consoleId) {
    injector = new TaskResourceProvider(console);      // extends BasicResourceProvider
} else {
    injector = new BasicResourceProvider();
}
```

**What `BasicResourceProvider` actually supplies.** Its own javadoc calls it "a minimal
`ResourceProvider` implementation that is necessary to load an Ecstasy module dynamically into a
lightweight container". It hand-implements a small whitelist - `HashCollector`, `Container.Linker`,
nullable types - and **fabricates nothing else**. Both providers are pre-existing core Ecstasy; the
branch did not touch `lib_ecstasy` at all. Nothing was missing from the platform. The more
restrictive of two available options was chosen.

**How the bug manifests.** Wiring `XtcEngine` to `runTask` and running modules that had worked
under the previous host-container path:

```
IllegalStateException: Exception: Invalid resource: Key: curDir, Directory
    at run() (InjectProbe.x:9)

IllegalStateException: Exception: Invalid resource: Key: storage, FileStore
    at testInject() (files.x:56)
```

Any module asking for a file system, a directory, a clock or any other ordinary container resource
dies.

**And this is not an artifact of this branch's tests.** `InjectProbe` is inlined test source written
here for the issue-576 ownership work, and it is deliberately resource-heavy - so on its own it
would prove little. The second failure is not: `RepeatedRunSweepTest` runs **`TestFiles`**, which is
`manualTests/src/main/x/files.x` - a **pre-existing XDK module**, listed in `testModuleNames` and run
by CI on every commit. It asks for `storage` and dies under `BasicResourceProvider` exactly the same
way.

So the limitation is not "this branch injects unusual things". **A standard XDK test module cannot
run under the runner as written.** Any module that touches the file system is excluded, which is a
large fraction of anything real. The old `manualTests/runner.x` used `PassThroughResourceProvider` for exactly this reason -
LSPAPI's runner is a strictly smaller world than the path it replaces, and `LspSupport.run` throwing
`UnsupportedOperationException` for `rootDir` is the same gap seen from the Java side.

**What was tried, and what it revealed.** Switching the runner to `PassThroughResourceProvider`
(`@Inject Injector injector; opts -> injector.inject(type, name, opts)`) **fixes the missing
resources** - those errors disappear. But two ownership tests then fail differently:

```
expected each of the two run containers to hold its own injected resources, found 1 that do
a container holds a reference to an unrelated container's state after a second run
    [ForeignReference path=container.m_contextMain.f_mapOpInfo...]
```

Because pass-through delegates to the PARENT, every run now resolves to **container zero's**
resource instances - so runs share what they used to own separately.

**Which is the actual gap.** The two available providers sit at opposite ends and neither is right
for a host:

| | complete resource set | per-run isolation |
| --- | --- | --- |
| `BasicResourceProvider` (LSPAPI's choice) | **no** - no `curDir`, no `storage`, no clock | yes - it fabricates |
| `PassThroughResourceProvider` | yes | **no** - every run shares the parent's instances |
| the previous host-container path | yes | yes |

`runTask(template, repository, consoleId)` takes only a console id. There is no injector parameter,
no root directory, no way for a caller to say "this run gets its own file system rooted here". So
the runner API **cannot currently express** what the path it replaces already did.

**The enhancement.** `runTask` needs to accept a resource description - at minimum a root directory
and a string-injection map, ideally an injector selection - and `Task` needs to build a provider
that supplies a complete set *per container* rather than choosing between fabricating a minimal one
and forwarding to a shared one. That is a contribution to `lib_runner`, and it is the thing standing
between the runner model and feature parity.

### H20 - Dynamic injection changes what "owns" a resource, and that IS intended

Worth settling explicitly, because this branch's ownership diagnostics were written against the
older model and will otherwise report the new one as broken.

**Their model, from the code.** A per-run resource is:

- registered on the **`NativeContainer`** - the shared plane - not on the run's own container
  (`xExternalConsole.register(NativeContainer, ...)`);
- keyed by a **unique name**, `console_<id>`, from an `AtomicLong`;
- reached from Ecstasy **by name**: `@Inject(resourceName=$"console_{consoleId}") Console console`;
- **unregistered when the run ends**, on both the success path (`finish`) and the failure path in
  `start`.

That is a deliberate design, and the concurrent resource maps and the public `add`/`remove` exist to
serve it. It is also the only shape available: Java cannot register a resource on a container that
**Ecstasy** is about to create, so the resource has to be parked somewhere both sides can see, under
a name only this run knows.

**What it changes.** Ownership moves from STRUCTURAL - the resource lives on the run's own container,
which is what `NestedContainer.registerHostResource` gave - to **name-scoped with an explicit
lifetime**: the resource lives on the shared plane, under a name no other run can guess, for exactly
as long as the run.

**So the diagnostics have to change, not the design.** The invariant worth asserting is no longer
"each run container holds its own injected resources". It is:

1. **uniqueness** - no two live runs share a resource name, so no run can resolve another's;
2. **lifetime** - the name is gone from the plane once the run ends, so the plane does not
   accumulate;
3. **non-leakage** - a run cannot reach a resource it was not given a name for.

That is a stronger and more directly testable property than the structural one, and it is what
`InjectedResourceOwnershipTest` should assert against the runner model.

**But note what this does NOT cover, which is H19.** Named host resources are only the *supplied*
ones - a console. Ordinary resources like `curDir` and `storage` come from the `ResourceProvider`,
and there the intended model is per-run fabrication (`BasicResourceProvider`) rather than a shared
plane. So the complete intended picture is:

| resource kind | owner | isolation mechanism |
| --- | --- | --- |
| host-supplied (console) | native container | unique name, removed at end of run |
| fabricated (`HashCollector`, `Linker`, ...) | the run's provider | a new instance per container |
| **everything else** (`curDir`, `storage`, clock) | **nothing supplies it** | **H19 - this is the gap** |

Which means the `PassThroughResourceProvider` substitution tried in H19 is **not** their model
either: it shares the parent's instances for everything, so it fixes availability by giving up
exactly the isolation this section says the design is trying to preserve. The intended fix is to
fabricate the missing resources per run, not to forward them.

### H21 - Container zero caches op-info across runs, so one run can see another's resolutions

Found by this branch's `RepeatedRunSweepTest` after the engine was wired to the runner. Two runs on
one engine, and the sweep reports:

```
a container holds a reference to an unrelated container's state after a second run
  [ForeignReference path=container.m_contextMain.f_mapOpInfo.value[250].value[1].ref]
```

**Why the runner model creates this.** Every run is a request into the SAME container zero, so every
run executes the same `runTask` ops on the same `ServiceContext`. That context carries
`f_mapOpInfo`, a per-op cache, and entries cached while serving run 1 are still there when run 2
arrives. Under the previous model each run had its own container and its own contexts, so there was
no shared op to cache on.

**How bad.** The values are `WeakReference`, so this is not primarily a retention problem - the
sweep follows the referent, which is why it is visible at all. The correctness question is the live
case: if an op in the shared runner module caches a resolution derived from run 1's types, run 2
executing that same op can be served run 1's answer. That is the shape of
[T12](plans/parallel-compiler-plan.md) - a long-lived shared structure caching something derived
from one request - which took a long time to find on the compile side precisely because it fails far
from its cause.

**Does this break only here, or upstream too? Upstream too.** The cache is not this branch's
invention: `ServiceContext.java:2186` in `cpurdy/LSPAPI` reads

```java
private final Map<Op, EnumMap> f_mapOpInfo = new WeakHashMap<>();
```

and their design routes **every** run through the one container zero's main context via
`invokeAsync`. So the same ops cache across the same runs there. The three tests that surface this
(`RepeatedRunSweepTest`, `PerRunInjectionTest`, `InjectedResourceOwnershipTest`) are all **this
branch's** - none exists upstream, where the only test is `LspTest` - so nothing there looks for it.
**These tests are detecting their defects, not being broken by them.**

The same is true of per-run injections: `LspSupport.java:475` throws
`UnsupportedOperationException` for `rootDir`, `injections` and `customInjector`, so that capability
is absent upstream as well. `PerRunInjectionTest` failing here is not a regression this branch
introduced by adopting the runner model - it is that model's unimplemented feature, made visible by
a test that exists only here.

**CORRECTION, 2026-09-04 - the correctness claim above does not hold.** Written before the
consumers of `getOpInfo` were read. They were read before drafting the review comment, and every one
of them re-validates the cached value before using it:

| site | guard |
| --- | --- |
| `OpInvocable.getCallChain` `:143` | `if (chain != null && clazz == clazzPrev)` - **object identity** on the target's `TypeComposition` |
| `OpVar.getArrayClass` `:145` | `if (clzArray == null || !typeList.equals(typePrev))` |
| `OpCallable.getChildConstructor` `:205-209` | cached `IdentityConstant` vs the run-time parent's |
| `OpCallable.getTargetConstructor` `:259-264` | same, against the run-time target |
| `OpCallable.getConstructor` `:333` | same, against `frame.getThis()` |
| `OpCallable.getFunction` `:403-425` | only `function == null` - but see below |

A `TypeComposition` is per-container, which is exactly why the sweep reported one owned by a
`NestedContainer`. So for the composition-keyed sites, run 2's handle can never satisfy an identity
guard against run 1's cached composition: the guard fails and the op recomputes. "A resolution
derived from one request served to another" would require a guard to pass while the answer is wrong,
and identity comparison does not permit that.

The one site with only a null guard, `getFunction`'s `Module`/`Package`/`Class` branch, caches
`idFunction.getComponent()` - a compile-time `MethodStructure` - and
`context.f_container.getTemplate(typeTarget)`. `ServiceContext.java:2055` declares
`public final Container f_container`, so that template is always the *context's own* container's,
invariant across every run served by that context. Safe for a different reason: nothing per-run
enters it.

**What is actually left**, and it is much smaller than this section claimed:

1. **Retention, weakly.** Container zero's cache keeps a `WeakReference` to run 1's
   `ClassComposition`, so that run's container graph stays reachable from the shared plane until GC.
   Bounded by weakness - and H5's registry pins the same containers **strongly**, which is strictly
   worse and is already the finding.
2. **A documented assumption that concurrency would break.** `ServiceContext.java:2181-2185`:
   "Since only one fiber can access the service context at any time, a simple HashMap is used."
   Concurrent runs on one container zero put two fibers on that unsynchronized `WeakHashMap`. That
   is forward-looking, not a present defect, and belongs with the Axis A blockers rather than as a
   finding against this PR.

**And `RepeatedRunSweepTest` is over-strict, not vindicated.** It follows weak referents and reports
them as foreign references. Weak reachability from a shared cache is not ownership leakage. The test
needs to distinguish strong from weak reachability before its failure means anything; as it stands
the failure is the test's fault, not the runner model's. That is a defect in this branch's
diagnostics, not upstream's code.

**Not investigated further here**, and deliberately not papered over: `RepeatedRunSweepTest` is left
failing rather than relaxed, because it is reporting a genuine cross-run reference that the runner
model introduces and the previous model did not have. What it needs is a decision about whether
op-info may be cached on a context that serves many requests, or must be keyed by something that
distinguishes them.

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

## Part 5b - Their tests: valid, but not tests

`LspTest` (252 lines) is the only test the branch adds, and the first thing to say is that **it is
not a test**:

- **no JUnit.** No `import org.junit`, no `@Test`. Its entry point is `static void main(String[])`,
  run by hand from a command line documented in its own javadoc - `--patch-module`, explicit
  `-p` module path, two directory arguments.
- **not referenced by any build file or CI workflow.** `grep` across `javatools/build.gradle.kts`,
  `xdk/build.gradle.kts` and `.github/workflows/` finds nothing. It cannot run in CI, cannot gate a
  merge, and will rot the first time an API it touches moves.
- **it "asserts" by throwing `IllegalStateException`** - fourteen sites - which works for a
  reproducer and gives no test report, no per-case isolation, and no continuation after the first
  failure.

That is a fair thing for a spike to be. It is stated here because *"the reproducers are working"*
(the head commit message) is a weaker claim than it sounds: nothing re-checks it.

**What the five cases do cover, and well:** compiling from a String including a deliberately broken
module, running and capturing that run's own console, an application exception reaching the host,
five sequential runs of one module in a hot JVM with timings, and shared-pool growth over twelve
runs with distinct generic shapes. Those are the right scenarios; the intent is not in question.

### What is missing, and why each matters

| missing | why it matters |
| --- | --- |
| **Any automated test at all** | The single biggest gap. Everything below is secondary to `LspTest` not running. |
| **An assertion in `testPoolGrows`** | It prints `ConstantPool size = N` twelve times and **never checks it**. It is an observation, not a test - so unbounded growth in the shared plane cannot fail the build. Compare [T15](plans/parallel-compiler-plan.md), where growth was only found because something measured it *against a threshold*. |
| **Task/container accumulation** | [H5](#h5---taskregistry-needs-an-eviction-rule-and-task-should-release-its-container): `TaskRegistry.tasks` is never pruned and `Task.container` never cleared. In their headline scenario - consecutive runs in a hot VM - this grows once per run and nothing observes it. |
| **Concurrency of any kind** | No parallel compiles, no parallel runs. The class javadoc claims the API "can be assumed to be thread-safe and concurrent" (4.1) and **not one test exercises more than one thread**. |
| **`Control.kill()`** | Appears once, in `await`'s timeout path, so it only runs when a test is already failing. The normal kill path is unexercised. |
| **The failure paths** | `ERR_NO_APP_MODULE`, `ERR_NO_APP_MODULE_VER`, `ERR_MISSING_MODULE` - none reached. `grep` finds no test for a missing or misnamed module, so the TC codes are untested. |
| **Diagnostic isolation** | Two concurrent runs with distinct known errors, each `ErrorListener` receiving exactly its own. Directly analogous to this branch's T5. |
| **Repeated `configure(...)`** | The one-shot semantics, including the `IllegalStateException` on a differing second call, are unexercised - and 4.1 shows that path is more fragile than it looks. |

### What this branch should contribute

`EngineParallelCompileTest` and `SharedLibraryIsolationTest` here already have the shape the first
four of those need - a distribution over iterations rather than one bit, a thread-count and seed
knob, and an invariant asserted directly rather than inferred. Porting that shape onto the runner
API is a better contribution than porting more assertions into a `main`.

## Part 6 - The migration: what to lift, in what form, in what order

### Status ledger - what actually landed

| piece | state | adaptation forced |
| --- | --- | --- |
| `MainContainer.invokeAsync` | **done** | their body opens `ConstantPool.withPool`; the ambient pool is deleted here, so ownership comes from `f_idModule` and `frame.poolContext()` |
| `InterpreterConnector` accessors | **done** | their guard checks only `m_fStarted`; this connector also clears `m_containerMain` in `join()`, so the guard covers that |
| `NativeContainer` dynamic resources | **done** | + H4: `putIfAbsent` instead of assert-then-put |
| `xCoreRepository` per-handle repository | **done** | the owner's handle stays in its `Lazy.Bound` cell; the per-request path bypasses it |
| `lib_runner` in the build | **done** | + H5: `forgetTask`, container released on completion and kill |
| per-run console | **done, differently** | H17/E38: `xTerminalConsole` gained an instance sink instead of adding a second template |
| `XtcEngine` run path -> `runTask` | **done** | `createForHost`, `registerInjections` and `RuntimePlane` deleted |
| `Control` as the run handle | **not done** | the engine still returns `CompletableFuture<ObjectHandle>`; adopting `Control` is a follow-up |
| per-run injections | **blocked** | H19 - `runTask` has no injector or rootDir parameter |
| `waitForTask` instead of polling | **not done** | H10 - the engine polls, as theirs does |

**Test state: 672 tests, 2 failing, both deliberately left red** because they report defects that
apply upstream as well (H19, H21) rather than local breakage. Every other test passes, including the
compile path, which the migration does not touch.

### The original plan, for reference

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
| H16 | was a new native console needed? yes - existing redirect is Ecstasy-side and batch-only | streaming to a host sink justifies it; note the `ExtermalConsole.x` filename typo |
| H17 | module output has two mechanisms - static `CONSOLE_OUT` vs a per-run `PrintStream` | give `xTerminalConsole` an instance sink; the second template stops being needed |
| H18 | `ConsoleLog` - shared static ring buffer, no synchronization at all, written on every print | a master defect the runner model makes routine |
| H19 | the runner can give a run a complete resource set **or** per-run isolation, never both | `runTask` takes no injector/rootDir; the two stock providers are opposite extremes |
| H20 | dynamic injection moves ownership from structural to name-scoped-with-lifetime, deliberately | the diagnostics must assert uniqueness/lifetime/non-leakage instead |
| H21 | container zero caches op-info across runs, so a shared op can serve one run another's resolution | same shape as T12; `RepeatedRunSweepTest` left failing rather than relaxed |
| H15 | what is NOT a smell, having checked | anonymous `Console`, the two-map update, native `switch` dispatch |
