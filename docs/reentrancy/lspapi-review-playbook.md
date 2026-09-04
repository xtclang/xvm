# Review playbook for PR #545 (`cpurdy/LSPAPI`)

A running order for one review session. Every line reference is verified against `2568d6be4`.

Findings are `H1`-`H21` in
[lspapi-integration-analysis.md](lspapi-integration-analysis.md); this file only says **where each
one goes** and **in what order**.

**The disposition rule.** Anything that changes a design decision goes in a sub-branch so it can be
accepted or rejected as a unit. Anything that is really a master defect does not belong in this PR
at all.

**Revised 2026-09-04: "commit directly" is not available.** Gene has force-pushed `cpurdy/LSPAPI`
five times since 31 Aug (08-31, 09-01, 09-02, 09-03 twice), so a commit of ours onto that branch
would be silently dropped by the next one. The small, uncontroversial fixes instead accumulate on
**`lagergren/lspapi-review-fixes`** (off `2568d6be4`), **one commit per finding**, so they can be
cherry-picked individually or taken together. The design-changing units still get their own
branches. Rows below that say "commit directly" mean "a commit on that branch".

---

## Live index

**Work through in this order:**

| | topic | goes where |
| --- | --- | --- |
| [1](#1-h19---a-standard-xdk-module-cannot-run-blocking) | H19 - a CI module cannot run | sub-branch |
| [2](#2-h21---container-zero-caches-op-info-across-runs-design-question) | ~~H21 - op-info shared across runs~~ **WITHDRAWN** | do not post |
| [3](#3-h5---the-task-registry-never-releases-anything) | H5 - registry never releases | **commit directly** |
| [4](#4-h1--h3b--h13---the-configuration-state-one-sub-branch-in-this-order) | H1+H3b+H13 - configuration state | sub-branch |
| [5](#5-h17--h14--h16--h3---the-console-one-sub-branch) | H17+H14+H16+H3 - the console | sub-branch |
| [6](#6-h12---write-once-fields-small-sub-branch) | H12 - write-once fields | sub-branch |
| [7](#7-mechanical---commit-directly-one-commit-each) | H4, H6, H2-partial | **commit directly** |
| [8](#8-comment-only---not-this-prs-job) | H18, H8, H9, H20, H10, tests | comment only |
| [9](#9-separate-master-issues---open-these-regardless-of-this-pr) | rows 46, E1, E38, 43-45 | master issues |

**If they ask, jump to:**

| question | section |
| --- | --- |
| "why is the singleton a problem?" | [the frame](#before-you-start-the-frame) - it is not; it is load-bearing, and E1 is the cause |
| "do we ever need two connectors?" | [two connectors](#if-asked-is-two-connectors-in-one-jvm-actually-a-use-case-or-is-this-theoretical) - 19 sites in this branch's tests already do |
| "isn't that what the old runner did?" | [not what runner.x did](#no---this-is-not-what-the-old-runnerx-did) - no, it made Containers, never Connectors |
| "when do we get concurrent runners?" | [which day and how](#we-want-concurrent-runners-some-day---which-day-and-how) - Axis A first; H19 is worth fixing for sequential runs anyway (H21 is withdrawn) |
| "should we just take your engine?" | [no](#what-to-say-if-asked-should-we-just-take-the-lazy-instance-engine-instead) - take their runner model, lift back four narrow things |
| "didn't you already say this?" | [already open](#already-open-on-the-pr---deal-with-these-before-posting-anything-new) - yes, six threads from 2026-08-28; two are now fixed |
| "is this a regression from what we had?" | almost never - most findings are pre-existing or unimplemented upstream; H19 is the exception, and it applies to their branch too |

---

## Already open on the PR - deal with these before posting anything new

Six review threads from 2026-08-28 are still unresolved. Checked against `2568d6be4`; two of them
have since been fixed upstream, one is superseded in a way that matters, and one anchors to the
exact line H1 wants.

| # | anchored at | state at `2568d6be4` | do |
| --- | --- | --- | --- |
| 1 | `ToolConnector.java:48` - `instance` not final | **still true** (`LspSupport.java:66`), but the file was renamed at `d3a1ba480`, so GitHub shows the thread outdated and it never appears next to the code | close; H2 re-posts it live, with the rationale |
| 2 | `ToolConnector.java:51` - `LOCK` not final | **fixed** - `LspSupport.java:69` is now `private static final Object LOCK` | close as addressed |
| 3 | `LspSupport.java:83` - the field block | **still true**, and this is exactly where H1 goes | **reply in this thread; do not open a second one** |
| 4 | `LspSupport.java:86` - `connector` | its claim still holds (the field is touched only inside `ensureConnector`, which is `synchronized (LOCK)` at `:122`), but it concedes a point H13 retracts, and it misses the defect | close, superseded - see below |
| 5 | `ToolConnector.java:95` - module name mismatch | **fixed** - `InterpreterControl.java:50` now loads `runner.xtclang.org`, matching `runner.x:6` | close as addressed |
| 6 | `LspSupport.java:169` - `configured` read without `LOCK` | **still true** (the read is `:170`), but it names two unsynchronized reads where H1 names six | close, folded into H1 |

**Thread 3 is the one to be careful with.** It already proposes the `record Config` + `volatile`
shape that is step 4.1 of this playbook, and it has had no response since 2026-08-28. So that design
is not a new suggestion to them - it is an unanswered one. Posting H1 as a fresh comment on the same
line would read as repeating myself louder. Reply in the thread instead.

**Thread 4 needs an explicit retraction, not a silent close.** It says `connector` "is the one field
here with a genuine reason not to be final". H13 retracts that: a `final Lazy.Bound` field is lazy
*and* final. It also missed what the analysis later found - `ensureConnector` is public and never
calls `verifyConfigured`, so calling it before `configure` builds a connector on a null `cfgRepo`
(`LspSupport.java:125-126`). That path is **latent, not demonstrated** - `LspTest.main` configures at
`:49` before any scenario runs, and only reaches `ensureConnector` at `:184`, so nothing upstream
exercises it. Latent still beats the hypothetical the comment offers, which is what makes leaving it
standing wrong: it tells them the field is fine as it is.

Threads 3, 4 and 6 are all H1. After this, there should be **one** thread on that topic.

---

## Before you start: the frame

Lead with this, or the whole review reads as style nagging.

> Most of what follows is small. One thing is not: a standard XDK module cannot run under the
> runner as written (H19). It was found by wiring `XtcEngine` to this branch and running existing
> tests against it - not by reading.

And say this early, because it changes the tone of everything about `LspSupport`:

> The singleton is **not** a style choice - it is load-bearing. `xExternalConsole.register` is a
> static reading a mutable static `INSTANCE`, and master has **144** templates shaped that way, so
> the runtime genuinely cannot host two connectors. The singleton is a symptom of E1, not laziness.

---

## Order of business

Ordered so that the expensive conversations happen while everyone is fresh, and the mechanical
changes are left as a tidy-up.

### 1. H19 - a standard XDK module cannot run (BLOCKING)

**POSTED 2026-09-04** on `runner.x:161`:
https://github.com/xtclang/xvm/pull/545#discussion_r3933436017 - with the corrected provider
description, not the wording below, which had the clock and the whitelist wrong.

**Comment on** `lib_runner/src/main/x/runner.x:161` - `injector = new BasicResourceProvider();`

> `BasicResourceProvider`'s `getResource` handles `Console`, `Clock`, `Timer`, `Random`/`rnd`,
> `String`, `List<String>` and enum/`Destringable` string injections. It has **no case for
> `Directory` or `FileStore`**, so `curDir`, `rootDir`, `homeDir` and `storage` fall to `default:`
> and return a deferred exception. Wiring this up and running `TestFiles`
> (`manualTests/src/main/x/files.x`, in `testModuleNames`, run by CI) gives
> `Invalid resource: Key: storage, FileStore`. The old `manualTests/runner.x` used
> `PassThroughResourceProvider` for exactly this reason - `manualTests/src/main/x/runner.x:100`
> declares `RunnerResourceProvider ... extends PassThroughResourceProvider`, the same
> console-supplying-subclass shape as `TaskResourceProvider` here, differing only in the base.
>
> Switching to pass-through fixes availability and loses isolation instead - every run then resolves
> to container zero's instances. So the two stock providers are opposite extremes and neither is
> right: `runTask(template, repository, consoleId)` has no injector or `rootDir` parameter, so the
> API cannot express "this run gets its own file system rooted here". Same gap as the
> `UnsupportedOperationException` at `LspSupport.java:476`.

**Also comment on** `LspSupport.java:476` - one line, pointing at the above.

**Where the fix goes:** **sub-branch**. It is an API change to `runTask` plus a provider that
fabricates a complete set per container. Do not attempt it inline.

**If the conversation turns to concurrency here** - it will, because "a run needs its own resources"
sounds like a parallelism question - go to
[which day and how](#we-want-concurrent-runners-some-day---which-day-and-how). The short version is
that this is worth fixing for **sequential** runs on its own merits, and fixing it happens to be one
of the three things concurrency later needs.

**Test to add:** run `TestFiles` through the runner. It is an existing module; if it passes, the gap
is closed.

---

### 2. H21 - WITHDRAWN, do not post

**Checked 2026-09-04 before drafting the comment, and it does not hold.** Every consumer of
`getOpInfo` re-validates before use - `OpInvocable.getCallChain:143` guards on **object identity** of
the target's `TypeComposition`, and compositions are per-container, so run 2 can never be served run
1's answer. The `OpCallable` sites compare `IdentityConstant`s. The one site guarded only by
`function == null` caches a compile-time `MethodStructure` and a template from
`context.f_container`, which is `public final` (`ServiceContext.java:2055`) and therefore always the
context's own container. Full evidence in the analysis under H21's correction block.

**What survives is not a finding against this PR:**

- container zero's cache holds *weak* references into each run's container, so the graph stays
  reachable until GC - and H5's registry pins the same containers strongly, which is worse and is
  already covered;
- `ServiceContext.java:2181-2185` documents "only one fiber can access the service context at any
  time, a simple HashMap is used" - which is what **concurrent** runs on one container zero would
  break. That is an Axis A blocker, not a defect today, and it belongs in that conversation.

**And this branch's `RepeatedRunSweepTest` is the thing that is wrong.** It follows weak referents
and reports them as foreign references. Weak reachability from a shared cache is not ownership
leakage. Fix the test here; do not report it there.

**Consequence for the PR description:** it currently names this as one of two things worth answering
before the PR lands. That paragraph has to go - the question in it now has an answer, and it is "no".

---

### 3. H5 - POSTED 2026-09-04

https://github.com/xtclang/xvm/pull/545#discussion_r3933558506 - fix `e886707`.

#### the finding as written

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

### 4. H1 + H3b + H13 - POSTED 2026-09-04

Reply into the August thread, not a new comment:
https://github.com/xtclang/xvm/pull/545#discussion_r3933764850
Branch `lagergren/lspapi-config-lifecycle`, commit `618e1f9` (pushed, `:javatools:compileJava` clean).

**Two corrections found while implementing it:** the unsynchronized read sites are **eight**
(`:93`, `:103`, `:170`, `:177`, `:185`, `:211`, `:459`, `:474`), not the six this file listed -
`getConfiguredInjector` (`:185`) and `compile` (`:211`) were missed; and the August comment
itself said *two*, which the reply corrects first. `getConstantPool`'s `verifyConfigured()` call
became redundant once the precondition moved into `createConnector`.

#### the plan as written

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
> repository. Latent rather than reached today: `LspTest` calls `ensureConnector` directly
> (`:184`), but only after `main` has configured (`:49`).

**Comment on** `LspSupport.java:52` - the thread-safety claim

> This is not met today. It is also latent in the supported single-threaded flow, since those fields
> are only read by the calling thread - so this is about the claim, not about a bug users are hitting.

**Where the fix goes:** **sub-branch**, in order:
1. config becomes one immutable `record Config(ModuleRepository repo, String injector)` behind a
   single `volatile` - "half-configured" stops being representable;
2. `createConnector` asserts it, so the precondition is stated;
3. `connector` becomes a `final Lazy.Bound` field - which then lets the static `LOCK` be deleted
   rather than bypassed. `Lazy` is already in the tree, and `AbstractConverterMap` already uses exactly this idiom -
   three `Lazy.ofBound` fields at `javatools_utils/.../converter/AbstractConverterMap.java:30-43`.
   It is unused in `javatools` specifically, not unused in the codebase, which makes it a
   precedent rather than a novelty.

**Test to add:** two threads calling `configure` and `compile` concurrently; and `ensureConnector()`
before `configure` must fail with a clear message rather than an NPE later.

---

### 5. H17 + H14 + H16 + H3 - POSTED 2026-09-04, scope changed

https://github.com/xtclang/xvm/pull/545#discussion_r3933846869 - and the rename landed as
`045404b` on `lagergren/lspapi-review-fixes` (full `xdk:installDist` green).

**The consolidation did NOT go on a branch off their head, deliberately.** It changes
`xTerminalConsole`, which is master's code, and `CONSOLE_OUT` cannot simply be deleted:
`DebugConsole` writes to it at `:232`, `:302` and `:2133`, and a terminal debugger writing to
the terminal is correct. What changes is the console template's *dependence* on the static, not
its existence - which makes this **E38, off `origin/master`**, not a rejection of two files in
their PR.

**One claim in the wording below was wrong and was measured before posting.** `println` does not
universally depend on auto-flush: over an *unbuffered* sink it delivers with no flush. Over a
**buffered** sink it is stark - 0 bytes until a flush, versus arriving immediately with
`autoFlush=true`. `LspTest` passes a `ByteArrayOutputStream`, unbuffered, which is why nothing
has hit it. The posted comment carries the measured version.

#### the plan as written

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

### 6. H12 - POSTED 2026-09-04

https://github.com/xtclang/xvm/pull/545#discussion_r3933798724 - branch
`lagergren/lspapi-control-finality`, commit `980d1a5` (pushed, `:javatools:compileJava` clean).

**Two things the analysis's diff did not predict.** `repository` stops being a field at all -
it is only read while starting, so it becomes a parameter of a now-static `prepareModule`
rather than lifetime state; the analysis expected it to become a final field. And
`unregisterConsole` no longer needs to null `consoleId` for idempotency: with the failure path
moved into the factory, its only caller is `finish`, which is synchronized and returns early
once `running` is false. Seven final fields, not eight.

#### the plan as written

**Comment on** `InterpreterControl.java:65` - `new InterpreterControl(...).start()`

> Construct-then-start is why `started`, `taskId` and `consoleId` cannot be `final`. Moving the work
> into the constructor would be the wrong fix; moving it into the factory that already exists is the
> right one, after which eight fields are final and the class has no "constructed but not yet valid"
> state. That state is also what makes `taskId`'s publication depend on the `watch()` submission
> ordering - safe today, but by accident rather than by design.

**Where the fix goes:** **sub-branch**; there is a ready-made diff in the analysis document.

---

### 7. Mechanical - POSTED 2026-09-04, fixes on `lagergren/lspapi-review-fixes`

All three posted, each linking its commit on `lagergren/lspapi-review-fixes` (pushed, off
`2568d6be4`; all four commits verified to compile with `xdk:installDist`):
H4 https://github.com/xtclang/xvm/pull/545#discussion_r3933563530 (`9f2e7f6`),
H6 https://github.com/xtclang/xvm/pull/545#discussion_r3933579905 (`b561e3b`),
H2 https://github.com/xtclang/xvm/pull/545#discussion_r3933586804 (`6b92b36`).

| finding | file:line | change |
| --- | --- | --- |
| H4 | `NativeContainer.java:403` | `assert !containsKey` + `put` -> `putIfAbsent`; the map was made concurrent precisely because more than one thread reaches it |
| H6 | `runner.x:139` | `running`/`result`/`failure` are outputs; make them `@RO` with private setters, as `status.get()` already is |
| H2 (partial) | `LspSupport.java:60,66` | constructor `private`, `instance` `static final`. The holder idiom's safe-publication guarantee is what `static final` buys; without it a reader can in principle observe a partially constructed instance, so this is not cosmetic. **Not** the singleton itself - see the frame |

---

### 8. Comment only - not this PR's job

| finding | where | say |
| --- | --- | --- |
| **H18** POSTED (r3933621812) | `xExternalConsole.java:83` - *not* `xTerminalConsole`, which is not in the diff and so cannot take an inline comment | `ConsoleLog` is a shared static ring buffer with **zero** synchronization in the whole file, written on every print. A **master** defect (row 46), not this PR's. Rewritten after checking: their `xExternalConsole` override writes to the per-run stream and never reaches `CONSOLE_LOG`, so it *is* the mitigation row 46 proposed. The residual exposure is the no-console-id fallback, which is the path `runTask(..., Null)` takes today. The comment credits the design and names the gap. |
| **H8** POSTED (r3933644114) | `InterpreterControl.java:96` | every Java/Ecstasy result is an unchecked `ObjectHandle` downcast, because `MainContainer.invokeAsync` (`:250`) returns `CompletableFuture<ObjectHandle>`. Exactly **three** sites in the whole api package - `:96`, `:168`, `:175` - and all three are correct. Not this PR's to fix; a typed `invokeAsync` overload would stop it growing while the surface is still one method. **Count check:** `grep -rE '\(\s*[A-Za-z_][A-Za-z0-9_.]*Handle\s*\)'` over `javatools` gives **1,203**, not the 1,439 this row previously cited from E22 - different definition, unverified here, so the comment quotes ~1,200 with the pattern that produces it. |
| **H9** | any `new Object[]{...}` site | not their fault - master's `ErrorListener` has no varargs `log`, so every call site boxes its parameters by hand. **Separate master change**, described on its own merits: add a varargs `log` overload to `ErrorListener`, after which these sites collapse to ordinary calls. Related to E32/E34, which are already about this interface. Do **not** reference any private branch as the source - see the rule in the session note. Nothing to say on this PR beyond "this is master's shape, not yours". |
| ~~**H20**~~ | ~~`xExternalConsole.register`~~ | **DROPPED 2026-09-04.** The visibility widening on `addResourceSupplier`, the new `removeResourceSupplier` and both maps going concurrent are all in their own diff against master - a deliberate design, not an oversight. Describing it back to them says nothing they don't know. The invariants it named (uniqueness, lifetime, non-leakage) still belong in *this* branch's ownership tests; they are not a review comment. |
| **H10** POSTED (r3933696495) | `InterpreterControl.java:132` | the 25ms poll rediscovers something Ecstasy already has - `Task.start` holds the completion at `runner.x:170`. A `waitForTask(id)` would replace ~40 requests/sec/run plus two closing requests with one future, and would drop the ~25ms floor on reported latency. **Attempted, then withheld.** `private @Future Tuple outcome` + `Tuple waitForCompletion() = outcome` compiles (`Ref.x:70`: `@Future` refs may be unassigned; verified with a forced `:xdk:lib-runner:compileXtc`), but `Task` registers its own `whenComplete` on that same future to record `result`/`failure`, and nothing I can read guarantees the task's continuation runs before a waiter is resumed. If it does not, the Java side reads `taskResult`/`taskFailure` before they are set - worse than the poll. So this ships as a question, not a commit. |
| **Part 5b** POSTED (r3933723359) | `LspTest.java:43` | it is a `main`, referenced by no build file or workflow, asserting by throwing. The scenarios are right; nothing re-checks them. `testPoolGrows` in particular prints the pool size **13** times (`i <= 12`, not twelve as this row said) and never checks it. The comment leads with the cheap version - just add `@Test`, since the checks already throw - and points at `DirRepositoryConcurrentScanTest.java:44-50` for the XDK-path bootstrap, which is the only thing genuinely tied to `main`. |

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

## If asked: "is two connectors in one JVM actually a use case, or is this theoretical?"

### It is not theoretical - it is happening in this branch right now

`XtcEngine.builder()` appears at **19 sites** across this branch's tests, no `forkEvery` is
configured, so Gradle runs them all in **one** JVM. Multiple connectors, each with its own runtime
and native container, coexist there on every build. That works here **only** because the 144 static
`INSTANCE` templates are gone (E1); on master the second one would quietly overwrite the first's.

### The use cases, concretely

| use case | why one connector is not enough |
| --- | --- |
| **Testing** - the one in use today | Each test wants its own module path and its own clean plane. With a one-shot `configure`, the first test wins and every later one inherits its configuration. |
| **A multi-root LSP workspace** | VS Code opens several roots; two projects can sit on different XDK versions. One connector means one module path per process, so the server must fork a process per root. |
| **Two XDK versions side by side** | Anything comparing behaviour across versions - a migration tool, a bisect harness - needs both loaded at once. |
| **Isolating untrusted code** | A playground or CI service wants a run's plane thrown away entirely, not just its container. |

### What being unable to do it blocks

For an LSP server: **one module path per JVM, permanently**. Multi-root support becomes
process-per-root, with the memory and warm-up cost repeated. For tests: no independence, so the
suite cannot check configuration behaviour at all - which is why nothing upstream tests
`configure(...)` being called twice.

### No - this is NOT what the old `runner.x` did

Worth being precise, because it is easy to assume this is a regression. The old
`manualTests/runner.x` creates `new Container(template, Lightweight, repository, injector)` - it
never creates a `Connector`. The shape was **one connector, one container zero, N sibling child
containers**, all on a single runtime and a single native plane.

So two connectors has **never** existed. Nothing regressed; it is a capability that was never
available, and the LSPAPI branch does not change that either way.

### "We want concurrent runners some day" - which day, and how

Two different axes, and conflating them is what makes this look harder than it is.

**Axis A - concurrent RUNS within one connector.** This is what "concurrent runners" almost always
means, and the runner model already supports it *structurally*: each `Task` is a service, and
`TaskRegistry` serializes admission. What stands in the way is not architecture:

1. **H19** - a run cannot be given a complete resource set of its own, so concurrent runs would
   share or lack resources;
2. **the op-info cache's single-fiber assumption** - `ServiceContext.java:2181-2185` says "only
   one fiber can access the service context at any time, a simple HashMap is used", and concurrent
   runs on one container zero put two fibers on that unsynchronized `WeakHashMap`. (This is all that
   survives of the withdrawn H21, and only in the concurrent case.);
3. **native-plane warm-up** - the first run still builds shared metadata that later runs read. This
   branch hit exactly that on the compile side, where building root `Object` sweeps the whole pool
   ([T8.1](plans/parallel-compiler-plan.md)); the answer there was to do the one-time destructive
   work deliberately at startup rather than let it happen under concurrency.

**Axis B - multiple connectors, i.e. multiple planes.** Needed only for multi-workspace and
multi-version, and blocked by exactly one thing: **E1**. Nothing in the runner model prevents it.

**Order: A first, B when there is a workspace that needs it.** A is a smaller, better-defined
problem, it is what a hosted test runner or `xunit` actually wants, and its three blockers are all
already written down. B is a bigger mechanical change (144 templates) whose only consumers today are
tests - which this branch already serves, so there is no urgency.

**What to do now, given "not yet":** nothing structural. Keep `TaskRegistry` and `Task` as services,
which is already right, and close H19 - which is worth doing for *sequential* runs anyway. That
leaves concurrency a configuration change rather than a redesign.

## What to say if asked "should we just take the lazy-instance engine instead?"

No. Take the **runner model** - it is theirs and it is right: container creation belongs in Ecstasy,
and `TaskRegistry`/`Task` being services means the language provides the serialization rather than
Java locking. What this branch has that is worth lifting the other way is narrower: the
`ErrorListener` varargs overload (H9), the console sink (E38), `Lazy` actually being used (H13), and
the ownership test that found H19. (Not the sweep test - see the H21 withdrawal: it follows weak
referents and needs fixing here before it is worth anyone's attention.)
