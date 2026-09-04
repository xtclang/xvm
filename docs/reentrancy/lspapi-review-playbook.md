# Review playbook for PR #545 (`cpurdy/LSPAPI`)

A running order for one review session. Every line reference is verified against `2568d6be4`.

Findings are `H1`-`H21` in
[lspapi-integration-analysis.md](lspapi-integration-analysis.md); this file only says **where each
one goes** and **in what order**.

**The disposition rule.** Commit directly only what is uncontroversial and self-contained. Anything
that changes a design decision goes in a sub-branch so it can be accepted or rejected as a unit.
Anything that is really a master defect does not belong in this PR at all.

---

## Before you start: the frame

Lead with this, or the whole review reads as style nagging.

> Most of what follows is small. Two things are not: a standard XDK module cannot run under the
> runner as written (H19), and every run shares container zero's op-info cache (H21). Both were
> found by wiring `XtcEngine` to this branch and running existing tests against it - not by reading.

And say this early, because it changes the tone of everything about `LspSupport`:

> The singleton is **not** a style choice - it is load-bearing. `xExternalConsole.register` is a
> static reading a mutable static `INSTANCE`, and master has **144** templates shaped that way, so
> the runtime genuinely cannot host two connectors. The singleton is a symptom of E1, not laziness.

---

## Order of business

Ordered so that the expensive conversations happen while everyone is fresh, and the mechanical
changes are left as a tidy-up.

### 1. H19 - a standard XDK module cannot run (BLOCKING)

**Comment on** `lib_runner/src/main/x/runner.x:161` - `injector = new BasicResourceProvider();`

> `BasicResourceProvider` is a minimal whitelist - `HashCollector`, `Linker`, nullable types - and
> supplies no `curDir`, `storage` or clock. Wiring this up and running `TestFiles`
> (`manualTests/src/main/x/files.x`, in `testModuleNames`, run by CI) gives
> `Invalid resource: Key: storage, FileStore`. The old `manualTests/runner.x` used
> `PassThroughResourceProvider` for exactly this reason.
>
> Switching to pass-through fixes availability and loses isolation instead - every run then resolves
> to container zero's instances. So the two stock providers are opposite extremes and neither is
> right: `runTask(template, repository, consoleId)` has no injector or `rootDir` parameter, so the
> API cannot express "this run gets its own file system rooted here". Same gap as the
> `UnsupportedOperationException` at `LspSupport.java:476`.

**Also comment on** `LspSupport.java:476` - one line, pointing at the above.

**Where the fix goes:** **sub-branch**. It is an API change to `runTask` plus a provider that
fabricates a complete set per container. Do not attempt it inline.

**Test to add:** run `TestFiles` through the runner. It is an existing module; if it passes, the gap
is closed.

---

### 2. H21 - container zero caches op-info across runs (DESIGN QUESTION)

**Comment on** `lib_runner/src/main/x/runner.x:101` - `tasks[id] = task;` (the request entry point)

> Every run is a request into the same container zero, so every run executes the same `runTask` ops
> on the same `ServiceContext` - and that context carries `f_mapOpInfo`
> (`ServiceContext.java:2186`, `WeakHashMap<Op, EnumMap>`). Entries cached while serving one run are
> still there for the next.
>
> Values are `WeakReference`, so retention is not the worry. The question is correctness: can a
> shared op cache a resolution derived from one request and serve it to another? On the compile side
> this exact shape (a long-lived structure caching something derived from one request) took a long
> soak to find, because it fails far from its cause.

**Where the fix goes:** nowhere yet - **this is a question, not a patch**. Get an answer on whether
op-info may be cached on a context that serves many requests before anyone writes code.

**Test to add (this branch has it):** `RepeatedRunSweepTest` - two runs on one engine, then sweep for
cross-container references. It currently fails, which is the point.

---

### 3. H5 - the task registry never releases anything

**Comment on** `lib_runner/src/main/x/runner.x:94` - `private Map<Int, Task> tasks = new HashMap();`

> `tasks` has four references in the module - declaration, `tasks[id] = task`, `contains`, `get` -
> and none of them removes. `Task.container` is likewise never cleared, not even by `kill()`
> (`:182`), and on normal completion `kill()` is never called at all. So the registry grows by one
> task per run for the life of container zero, each pinning a `Container` and through it that
> container's composition and template caches.
>
> Eviction has to be explicit rather than automatic on completion, because the Java side reads
> `taskResult` and `taskFailure` after `taskRunning` goes false.

**Where the fix goes:** **commit directly to the PR branch**. It is additive and uncontroversial:
`forgetTask(id)`, clear the container on completion and kill, and an idempotent `release()`. Already
written in `lagergren/lazy-instance` (`lib_runner/src/main/x/runner.x`) - lift it.

**Test to add:** N runs, then assert the registry is empty. There is no test for this at all today.

---

### 4. H1 + H3b + H13 - the configuration state (ONE sub-branch, in this order)

These three only work together; taking H13 alone trades a real if incidental serialization for none.

**Comment on** `LspSupport.java:83` - the field block

> These four are two different lifecycles in one block. `cfgRepo`/`cfgInjector`/`configured` are
> written together at `:158-160` with the flag deliberately last - the publish-when-ready ordering -
> but without `volatile` that ordering buys nothing, because a reader that does not take `LOCK` has
> no happens-before. Only `configure` (`:152`) and `ensureConnector` (`:121`) take it; six other
> reads do not, at `:93`, `:103`, `:170`, `:177`, `:459`, `:474`.
>
> `connector` is not part of that group - it is a lazily derived resource, and `ensureConnector` is
> public and never calls `verifyConfigured`, so calling it first builds a connector on a null
> repository. `LspTest` calls it directly.

**Comment on** `LspSupport.java:52` - the thread-safety claim

> This is not met today. It is also latent in the supported single-threaded flow, since those fields
> are only read by the calling thread - so this is about the claim, not about a bug users are hitting.

**Where the fix goes:** **sub-branch**, in order:
1. config becomes one immutable `record Config(ModuleRepository repo, String injector)` behind a
   single `volatile` - "half-configured" stops being representable;
2. `createConnector` asserts it, so the precondition is stated;
3. `connector` becomes a `final Lazy.Bound` field - which then lets the static `LOCK` be deleted
   rather than bypassed. `Lazy` is already in the tree and **entirely unused**.

**Test to add:** two threads calling `configure` and `compile` concurrently; and `ensureConnector()`
before `configure` must fail with a clear message rather than an NPE later.

---

### 5. H17 + H14 + H16 + H3 - the console (ONE sub-branch)

**Comment on** `javatools/src/main/java/org/xvm/runtime/template/_native/io/xExternalConsole.java:31`

> Module output now has two mechanisms for one concern: the CLI writes to the static
> `xTerminalConsole.CONSOLE_OUT`, which cannot be redirected, and a hosted run writes to a per-run
> `PrintStream` in a second template. Neither can do the other's job.
>
> `xTerminalConsole`'s sink is static only because nothing had ever needed otherwise. Giving that
> template an instance sink defaulting to `CONSOLE_OUT` leaves the CLI byte-for-byte unchanged and
> removes the need for this class, its Ecstasy declaration, and this mutable public static.
>
> On the type: `PrintStream` cannot report a write failure (it sets a flag nobody polls), its charset
> is whatever the caller happened to construct it with, and auto-flush is a requirement the API
> cannot state - `println` paths here rely on it while `print` paths flush explicitly. The parent
> writes through a `PrintWriter`, which takes the `char[]` directly. Not `org.xvm.tool.Console`
> either: that is line-oriented and cannot express `suppressNewline`.

**Where the fix goes:** **sub-branch** - it deletes files, so it wants to be reviewable as a unit.
Prototyped in `lagergren/lazy-instance` (`xTerminalConsole.ensureConsole(Frame, PrintWriter)` plus
routing by handle).

**Trivial, commit directly:** rename `javatools_bridge/src/main/x/_native/io/ExtermalConsole.x` to
`ExternalConsole.x` - it declares `service ExternalConsole` and every sibling matches its
declaration. (Skip if the sub-branch above lands, which deletes the file.)

---

### 6. H12 - write-once fields (small sub-branch)

**Comment on** `InterpreterControl.java:65` - `new InterpreterControl(...).start()`

> Construct-then-start is why `started`, `taskId` and `consoleId` cannot be `final`. Moving the work
> into the constructor would be the wrong fix; moving it into the factory that already exists is the
> right one, after which eight fields are final and the class has no "constructed but not yet valid"
> state. That state is also what makes `taskId`'s publication depend on the `watch()` submission
> ordering - safe today, but by accident rather than by design.

**Where the fix goes:** **sub-branch**; there is a ready-made diff in the analysis document.

---

### 7. Mechanical - commit directly, one commit each

| finding | file:line | change |
| --- | --- | --- |
| H4 | `NativeContainer.java:403` | `assert !containsKey` + `put` -> `putIfAbsent`; the map was made concurrent precisely because more than one thread reaches it |
| H6 | `runner.x:139` | `running`/`result`/`failure` are outputs; make them `@RO` with private setters, as `status.get()` already is |
| H2 (partial) | `LspSupport.java:59,66` | constructor `private`, `instance` `static final`. **Not** the singleton itself - see the frame |

---

### 8. Comment only - not this PR's job

| finding | where | say |
| --- | --- | --- |
| **H18** | `xTerminalConsole` `CONSOLE_LOG` use | `ConsoleLog` is a shared static ring buffer with **zero** synchronization in the whole file, written on every print. A **master** defect (filed as row 46), not this PR's - but this PR makes concurrent printing routine, so it is worth fixing before this lands. |
| **H8** | `InterpreterControl.java:96` | every Java/Ecstasy result is an unchecked `ObjectHandle` downcast. That is E22 (1,439 casts tree-wide), not this PR's to fix; a typed `invokeAsync` wrapper would stop it growing. |
| **H9** | any `new Object[]{...}` site | not their fault - master's `ErrorListener` has no varargs `log`. Offer the overload from `lagergren/lazy-instance` as a separate small PR, then these collapse. |
| **H20** | `xExternalConsole.register` | confirm the intent: per-run resources live on the native container under a unique name with an explicit lifetime, not on the run's container. Worth stating in the class doc, because it changes what "ownership" means for anything auditing it. |
| **H10** | `InterpreterControl.java:132` | the 25ms poll rediscovers something Ecstasy already has - `Task.start` holds the completion at `runner.x:170`. A `waitForTask(id)` would replace 40 requests/sec/run with one future. Worth doing if `runner.x` is being touched anyway. |
| **Part 5b** | `LspTest.java` | it is a `main`, referenced by no build file or workflow, asserting by throwing. The scenarios are right; nothing re-checks them. `testPoolGrows` in particular prints the pool size twelve times and never checks it. |

---

### 9. Separate master issues - open these regardless of this PR

| row | subject |
| --- | --- |
| **46** | `ConsoleLog` unsynchronized shared ring buffer |
| **E1** | the 144 mutable static `INSTANCE` templates - the reason the singleton is load-bearing |
| **E38** | give the console its sink instead of hard-wiring it to the terminal |
| 43, 44, 45 | already written up: `m_FVisited`, `UnresolvedTypeConstant.compareDetails`, `writeTo`'s registration fixed point |

---

## Tests to ask for, in priority order

1. **Anything automated at all.** `LspTest` cannot run in CI. Until one test runs on every commit,
   the rest of this list is theoretical.
2. **`TestFiles` through the runner** - closes H19 when it passes.
3. **Registry empty after N runs** - closes H5.
4. **Two concurrent runs** - the class doc claims thread safety and not one test uses two threads.
5. **`testPoolGrows` with an assertion.** For reference, this branch's version over 12 runs gives
   `[84983, 85015, 85046, 85075, 85103, 85134, 85134, 85134, 85134, 85134, 85134, 85134]` - novel
   shapes cost ~30 constants each, repeated shapes cost zero. That is a *positive* result, and a
   printed number could never have shown it.
6. **`Control.kill()` on the normal path** - today it appears only in a timeout branch.
7. **The TC error codes** - `ERR_NO_APP_MODULE` and friends are entirely unexercised.

---

## What to say if asked "should we just take the lazy-instance engine instead?"

No. Take the **runner model** - it is theirs and it is right: container creation belongs in Ecstasy,
and `TaskRegistry`/`Task` being services means the language provides the serialization rather than
Java locking. What this branch has that is worth lifting the other way is narrower: the
`ErrorListener` varargs overload (H9), the console sink (E38), `Lazy` actually being used (H13), and
the ownership tests that found H19 and H21.
