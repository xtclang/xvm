# Compiling in parallel on a warm engine — task list

The target is the one the XDK direction already sanctions: a resident host compiles many requests
against an **already-built XDK**, concurrently, in one VM, without going through `Launcher`. That
is `LspSupport`'s stated contract — *"the methods on the LspSupport itself can be assumed to be
thread-safe and concurrent"* — and its `compile(source, input, errs)` signature, which takes the
library per call.

Building the XDK itself in parallel is a separate, harder problem (the library is being produced as
you go); it is T7 and deliberately last.

## Where this starts from — measured, not assumed

| | result |
| --- | --- |
| Sequential, 45 manualTests modules, one warm engine | 42 ok, **0 crashes**, 9.5s |
| Parallel, 42 modules, 8 threads, 3 iterations | **42/42 every iteration**; 4660 / 2578 / 2489 ms |
| Cold sequential vs warm parallel | 10.6s -> ~2.5s |
| Cloning `ecstasy` per compile | 60-72ms warm, **~18% of a warm compile** |

Two things are already true and should not be re-litigated:

- **A compile never touches a container.** `containerNative()` is reached only from `runFrom` and
  `diagnosticContainer()`. Concurrent compiles are concurrent users of a module repository, not
  concurrent containers.
- **Concurrent compiles do not mutate shared library structures today.** A read-through
  `LinkedRepository` clones each library module into that compile's own `BuildRepository` and
  returns the clone (`LinkedRepository:119`). That is why the parallel runs are correct - and it is
  also what costs the 18%.

## What this branch fixes, and what it only records

Not everything found on the way belongs here, and mixing the two is how a branch stops being
reviewable.

**In scope - these block parallel compilation:** T1-T5, T8. Concretely the TypeInfo ownership
record (shipped), the deferred-list scoping (shipped), the root-`Object` sweep (T8.1, prototyped),
the setter that demotes a complete answer (T8.2), and the lock on the wrong object (T8.3).

**In scope but only as far as a host needs:** diagnostic attribution (T5). The measurement and the
isolation test belong here; the 665-parameter repair does not.

**Recorded, NOT fixed here** - they are real and they are master enhancement rows, and none of them
blocks a parallel compiler:

| finding | where |
| --- | --- |
| The L-value bridge fails open; `isAssignable` is a boolean standing in for a type | [E37](master-enhancement-submissions.md) |
| `Assignable[]` as a raw-array API (38 usages) | E37 |
| `Launcher implements ErrorListener` and overloads `log` with incompatible semantics | E37 note / `ErrorListener.java` |
| An unknown error code throws `MissingResourceException` - a typo'd diagnostic takes the process down | (this file, below) |
| Thread one `ErrorListener` everywhere | E32, E34, E35 |

### A note on the last one

`ErrorInfo.getMessageText` does `MessageFormat.format(RESOURCES.getString(getCode()), getParams())`.
`ResourceBundle.getString` throws `MissingResourceException` on an unknown key, so a diagnostic with
a code that is not in `errors.properties` **crashes the compiler instead of reporting the
diagnostic** - the failure path fails. It is one `containsKey` check away from degrading to the raw
code, and it is not parallel-compiler work, but anything that adds diagnostic codes trips over it.

## The principle: prefer a mechanism that is reentrant by construction

Every concurrency defect found in this work has been the same mistake, and naming it is worth more
than the individual fixes. **A fact about one call stack was stored where every call stack could see
it.**

| what it was | where it lived | what it made happen |
| --- | --- | --- |
| "this TypeInfo is being built" | a place-holder in the type's shared slot | a peer's build read as recursion; deferred lists that never drained |
| "I am inside this component" | `Component.m_FVisited` | `VERIFY-11`, contribution forms a cycle, on a declaration with no cycle |
| "how deep is this rebuild" | `TypeConstant.m_cRecursiveDepth`, an `AtomicInteger` | two threads two deep summed to four, and one threw "Infinite loop" |
| "what work do I still owe" | a thread-local hung off the POOL | entries recorded on a library pool, drained against a compile's |

The third is the sharpest. It was **atomic**, so the count was always correct - and it was still
wrong, because the quantity itself was mis-scoped. **Thread-safety of a mechanism cannot repair a
fact that belongs to a narrower scope than the place it is kept.** Reaching for `Atomic*`,
`volatile` or a lock at that point makes the defect harder to see, not less real.

### The ladder, best first

1. **No shared mutable state.** The operation is a function of its inputs; results are returned
   rather than stashed. Reentrant because there is nothing to re-enter.
2. **State on the call stack - a parameter.** This is what `ErrorListener` already does, and it is
   correct under ANY execution model: threads, virtual threads, work-stealing, continuations. The
   honest end state for `building`/`deferred` is `ensureTypeInfo(work, errs)`.
3. **State owned by the executing thread** - `TypeSystemThread`. Correct while one task equals one
   thread, which is why [the execution-model section](#the-execution-model-this-depends-on-and-why-it-is-not-a-parallel-stream)
   rules out work-stealing pools. This is where the branch is now, and it is a stepping stone to 2,
   not a destination.
4. **A marker on shared state.** What all of the above were. It asks "am I inside?" and answers
   "is anyone inside?", and the two are indistinguishable at the point of use.

### How to find the next one

The smell is greppable, because these fields name themselves - `m_FVisited`, `m_fRecurseReg`,
`m_cRecursiveDepth`, `m_tloInProgress`, anything whose javadoc says *recursion check*, *in
progress*, *being built*, or (as `m_FVisited`'s did) *Not thread-safe*. A field whose NAME is a
sentence about the current call is a call-stack fact, and it belongs to the call.

Two further rules learned the hard way:

- **Reset in a `finally`.** `m_FVisited` and `m_cRecursiveDepth` both reset with a plain statement
  after the recursive call, so a throw left the marker set or the depth raised forever. On a
  per-compile clone that poison died with the clone; shared, it does not.
- **State that is not there needs no clearing.** `ConstantAdoptionTest` used to assert that
  adoption CLEARED `m_cRecursiveDepth` correctly. Moving the field out deleted the requirement
  rather than satisfying it.

## T1 - Share the library instead of cloning it

The one change that matters. Link and inject the system libraries **once per engine**, publish
their pools, and serve them read-only to every compile.

1. Hoist `prelinkSystemLibraries` out of `compileInternal` to a `Lazy.Bound` on the engine, so
   `setErrorListener` and `linkModules(repo, false)` run once rather than per compile.
2. Hoist `injectNakedRefType` for the library modules the same way. It is idempotent - the same
   NakedRef type every time - so a library injected once satisfies every later compile.
3. Stop read-through cloning for the shared library: the per-compile `BuildRepository` holds only
   the modules being compiled; library modules are resolved from the shared, published repository.
4. Mark the shared library pools published (`markRuntimePublished`).

**Verification.** Clone time per compile goes to zero (instrument as in
[engine-compile-divergence.md](../engine-compile-divergence.md)); `EngineSuiteCompileTest` stays at
0 crashes; `EngineParallelCompileTest` stays 42/42 across iterations; measure warm compile time
before and after and state the delta.

**Risk, and why it is bounded.** A compile that registers a NEW constant into a shared library pool
- which happens when user code parameterizes a library generic - would leak it into every later
compile. That is exactly what `assertRegisterBeforeRuntimePublished` exists to catch, so the
failure mode is a loud error at the offending write rather than a silent cross-request leak. T2
makes that error usable.

### T1 progress - implemented, measured, and blocked on T4

**Done and measured.** The change is small: prepare each library once (`ensureLibraryPrepared`,
keyed by repository so a per-call library gets its own preparation), take the NakedRef type from
that preparation rather than from a clone in the build repo, and turn read-through OFF so
`repoBuild` holds only the modules being compiled.

| | before | after |
| --- | --- | --- |
| Sequential, 45 manualTests modules | 9494 ms | **5855 ms** (-38%) |
| crashes | 0 | 0 |
| ok / fail | 42 / 3 | 42 / 3 |

One bug found on the way, caught immediately by `EngineSuiteCompileTest`: `injectNakedRefType`
looked the turtle up **in `repoBuild`**, which only ever contained it because read-through had
cloned it there. With cloning off, all 43 compiles threw "Mack module (javatools_turtle) is
missing". Sourcing the type from the prepared library fixes it - and is the more honest place for
it to come from.

**Blocked on T4, exactly as this plan predicted.** With the clone gone, concurrent compiles share
the library's `TypeConstant`s, and `EngineParallelCompileTest` goes from 42/42 to **0/42**, every
one of them:

```
NullPointerException: Cannot invoke "TypeInfo.isPlaceHolder()" because "info" is null
```

That is the TypeInfo placeholder race - row 26 of the master issue board, *"TypeInfo placeholder
identity race strands types as 'being built'"*. The per-compile clone was what hid it. This is the
plan's own warning coming true: *"the clone is currently what makes concurrent compiles safe, so
removing it moves the burden to the shared read path"*.

**State.** T1 is reverted from the tree so the suite stays green, and the diff is kept at
[t1-share-the-library.patch](t1-share-the-library.patch) - 106 lines, re-appliable once T4 lands.
It is worth ~38% sequentially on its own, so if concurrency slips, T1 could ship first behind a
single-threaded engine contract; that is a decision, not a default.

**T4 is therefore no longer optional or later - it is T1's blocker**, and it has a precise target
rather than a general audit: make `TypeInfo` construction safe for concurrent readers of a shared
library, starting from master row 26.

## T2 - Make a publication-guard trip actionable

Today the guard throws. For a host serving requests it has to say which module, which constant, and
which request, and it has to arrive as a diagnostic the caller can attribute rather than an
exception escaping `compile()`.

**Verification.** A test that deliberately registers into a published library pool gets a
diagnostic naming the constant and the module, and the engine remains usable for the next compile.

## T3 - Shared `TypeInfo`

Falls out of T1: once library modules are shared, the `TypeInfo` built on their `TypeConstant`s is
built once instead of per compile. `ensureTypeInfo` was 12% of runtime startup in
[the performance analysis](../../perf/runtime-performance-analysis.md), and compiles pay a similar
cost today.

**Verification.** Measure it separately from T1 rather than claiming it as part of the same number:
count `ensureTypeInfo` calls per compile before and after.

## T4 - Audit the shared read path for thread safety

T1 turns "each compile has its own copy" into "every compile reads one structure". That is the
point at which the read path has to actually be safe:

- `DirRepository`'s scan cache - already the subject of a fixed data race (row 27, merged as #547);
  re-check it under concurrent load.
- `ConstantPool`'s lookup maps under concurrent read, including the lazily-created per-format
  locator tables.
- The `TypeInfo` cache from T3, which becomes a concurrently-populated cache rather than a
  per-compile one.

**Verification.** A stress test at higher thread counts than 8, run repeatedly, asserting every
module matches its sequential result - the shape `EngineParallelCompileTest` already has. A single
green run is not evidence; iterate.

### T4 progress - two approaches tried, neither shipped

**The failure, precisely.** With T1 applied, the FIRST exception under concurrency is not the
NullPointerException that dominates the output - that is downstream. It is:

```
IllegalStateException: Failure to make progress on a TypeInfo for private ConstOrdinalListTest;
deferred types=[private Module, TerminalType{type=Module}, private Native(Module), ...]
    at TypeConstant.ensureTypeInfoInWindow
```

Two threads building the TypeInfo of the SAME shared library `TypeConstant` interfere. The
deferred-TypeInfo machinery is built for one thread recursing into a type it is already building -
that is what the placeholder is for - and has no answer for two threads doing it at once.

**Approach A - lock per TypeConstant during the build. Not attempted, and deliberately.** The
recursion is a graph: two threads entering it at different points can each hold what the other
needs. A deadlock here would be worse than the current failure, which at least reports itself.

**Approach B - warm the library's TypeInfo once, single-threaded, during preparation. Tried,
reverted.** The idea is sound: library types are the only shared ones, so if they are complete
before any compile starts, concurrent compiles only read them. Measured:

| | sequential | parallel |
| --- | --- | --- |
| T1 alone | 42 ok, 0 threw | **0/42** |
| T1 + warming | **33 ok, 9 threw** | 30/42 |

Parallel improved from 0 to 30 of 42, which says the direction is right. Sequential got *worse*,
and the cause was in the warm-up itself: it wrapped each type in `catch (RuntimeException ignore)`
so that a library type which cannot build a TypeInfo standalone would not fail preparation. That
left a **half-built TypeInfo cached on a shared TypeConstant**, which then poisoned every later
compile. The swallow did not paper over a failure; it manufactured one.

**What that told the next attempt.** Warming treats the symptom: it tries to finish all the
shared work before anyone can race on it. The next attempt removed the REASON the shared path
fails instead, and that turned out to be much smaller.

### T4 approach C - give the in-progress build a declared owner. SHIPPED

Two defects, both a thread being unable to tell its own work from a peer's. Committed as
`1026e5e3e` here and as `ca2aa7434` on the local branch `lagergren/master-typeinfo-build-owner`;
written up as rows 41 and 42 of [master-issue-submissions.md](master-issue-submissions.md).

**C1 - the place-holder cannot say WHO is building.** `ensureTypeInfoInternal` treated any
place-holder as the catch-22 it was invented for and deferred. Right when the place-holder is
ours; wrong when it is a peer's, because then it is not recursion at all, and deferring bets on
the other thread publishing before we run out of retries - which nothing makes true, since the
deferred list is thread-local and their progress can never drain ours. The place-holder cannot
answer "is it mine" even in principle: it is a per-pool singleton in one shared slot that must
stay identity-stable for `clearTypeInfoPlaceholder`'s CAS. So the owner is now recorded
separately and per thread, exactly as the deferred list already is. Ours defers; theirs builds
its own copy - which is what the comment on that branch always said it wanted to do.

Duplicating is safe: building is deterministic, and `setTypeInfo` is a rank-ordered CAS that
keeps the better answer, so the loser costs CPU and never correctness. And no thread ever waits,
so this adds no deadlock - which is what ruled out approach A.

**C2 - the deferred list was per-pool but is drained per-window.** Found only after C1 stopped
masking it. An entry is recorded against the DEFERRED TYPE's pool; the drain runs against the
WINDOW TYPE's pool. Different pools as soon as a compile's types depend on a shared library's
types, so an entry recorded on the library pool survives a window that drained only the compile
pool, and the next window on the library pool throws "Infinite loop while producing a TypeInfo".
The drain loop already re-registers entries from another pool - it could only ever see one if the
list spanned pools, so making it `static` restores the original intent.

| | sequential | parallel (42 modules, 8 threads) |
| --- | --- | --- |
| T1 alone | 42 ok, 0 threw | 0/42 |
| T1 + warming (approach B) | 33 ok, 9 threw | 30/42 |
| T1 + C1 | 42 ok, 0 threw | 37/42 |
| **T1 + C1 + C2** | **42 ok, 0 threw** | **41/42**, flaky; a full iteration is sometimes green |

Full `javatools` + `javatools_utils` suites: **667 tests, 0 failures** - measured without T1, since
that is what is committed.

**Two residuals remain, and neither is a TypeInfo race:**

1. `THREW IllegalStateException: Mack module (javatools_turtle) is missing` - **XtcEngine's own
   bug, not the compiler's.** `ensureLibraryPrepared` is check-then-act rather than atomic, so a
   second thread can observe a half-prepared library. Fix belongs in T1's patch.
2. `reflect.x -> FAIL(1) COMPILER-56: Could not find a matching method or function "toString" for
   type "Directory"` - a genuine incomplete-`TypeInfo` read, surfacing as a miscompile rather than
   a crash. Note `setTypeInfo`'s `|| info.isPlaceHolder()` clause lets a place-holder overwrite a
   COMPLETE TypeInfo, which is the most likely mechanism and the place to look next.

**T1 status.** Still not landed, and still kept as
[t1-share-the-library.patch](t1-share-the-library.patch) (applies cleanly; it is `XtcEngine`-only).
It is worth ~38% sequentially on its own and now reaches 41/42 in parallel rather than 0/42. It
lands when the two residuals above are closed.

## T8 - The three TypeInfo-cache defects that T4 uncovered

T4 removed the reason concurrent builds *interfered*. These are the reasons the cache is still
not safe to share, all found by tracing rather than reasoning, and all of them are what now
stands between 41/42 and 42/42.

### T8.1 - Building root `Object` wipes the whole pool

The one that is genuinely surprising. On the FIRST successful build of root `Object`,
`ensureObjectTypeInfo` discards **every other TypeInfo in the pool**:

```java
// discard any partial TypeInfos created as part of creating the Object TypeInfo
for (int i = 0, c = pool.size(); i < c; ++i) {
    if (pool.getConstant(i) instanceof TypeConstant type
            && type.getTypeInfo() != null && !type.isRootObject()) {
        type.clearTypeInfo();
    }
}
```

The reasoning is sound single-threaded: anything built while `Object` was itself incomplete may
have been flattened against a partial `Object`, so it is thrown away and rebuilt. It is a lazy
getter with a **global destructive side effect** - a cache warm-up that wipes the cache - and on a
shared pool it clears types other threads are in the middle of using. A type flattened across that
moment can be marked `Complete` while missing members it should have inherited, which surfaces far
away and much later as `COMPILER-56: Could not find a matching method or function "toString" for
type "Directory"` - a miscompile, not a crash.

**Fix, and it is already prototyped:** build root `Object` once during library preparation, while
that is still single-threaded, so the sweep happens with nobody else in the pool. Measured: it took
one full parallel iteration to 42/42. This is NOT the blanket warm-up rejected in T4 approach B -
that swallowed failures and cached half-built TypeInfos. This warms ONE type, the one whose
construction is documented as special, and lets failures propagate.

**Verification.** The sweep must not run during any compile: assert the root `Object` TypeInfo is
already present when a compile starts, so a regression is loud rather than a rare miscompile.

### T8.2 - `setTypeInfo` is a one-way setter with a clause that goes backwards

It keeps whatever is "better than" what is there - except:

```java
while (rankTypeInfo(info) > rankTypeInfo(infoOld = s_typeinfo.get(this))
        || info.isPlaceHolder()) {
```

`|| info.isPlaceHolder()` lets a place-holder **overwrite a COMPLETE TypeInfo**. Intentional
single-threaded, where installing a place-holder means "I have decided this needs rebuilding". On a
shared pool it means one thread can demote another thread's finished answer to "being built", and
`clearTypeInfoPlaceholder` can then CAS it to null - so a completed type becomes absent. There is
also no way to say "this attempt failed, remove what I put there", which is the gap T4 approach B
fell into.

**Fix direction:** separate "invalidate, I am rebuilding" from "publish a place-holder" so the
demotion is an explicit operation rather than a side effect of the setter, and give the cache a way
to retract a failed attempt.

### T8.3 - The lock is on the wrong object

`ensureTypeInfo(TypeInfo, ErrorListener)` is `private synchronized` (`TypeConstant:1849`), so it
locks the type being ASKED for. Two threads asking for the same type serialize; two threads asking
for different types that share a dependency do not, and the dependency is reached through the
unsynchronized internal path. It therefore serializes the one case that was never the problem.

A half-lock is worse than none, because it reads as handled. Either drop it - T4's ownership record
makes the recursion safe without it - or state in the javadoc exactly which race it does and does
not cover. Removing it should be measured, not assumed: it may also be load-bearing for something
else.

## T9 - It was never a leak: a 512m ceiling, and the residuals it was hiding

Recorded because the wrong conclusion cost hours, and because the shape of the mistake is worth
keeping.

**What it looked like.** Running the parallel suite for more iterations degraded monotonically -
42/42, then 36, then 29, eventually 15 - while wall time went from 1s to 172s and ended in
`OutOfMemoryError`. Monotonic degradation plus OOM reads as a leak, and sharing the library instead
of cloning it is exactly the kind of change that would cause one.

**Four hypotheses, all measured, all wrong.** Constants accumulating in the shared pool (flat at
76641); TypeInfo invalidation churn (invalidation count 0 throughout); repeated root-Object sweeps
(exactly 1, thanks to T8.1); relation caches growing on library types (flat at 12828). Each was a
plausible mechanism, each was measured, each was dead - and measuring one suspect at a time was the
wrong method.

**What a heap histogram said in one shot.** `jcmd GC.class_histogram` on the live worker, sampled
six times:

| class | first | last |
| --- | --- | --- |
| `ConstantPool` | 102 | **103** |
| `FileStructure` | 102 | **103** |
| `TypeInfoReal` | 18,899 | 19,181 |
| `MethodBody` | 1,304,112 | 1,329,073 |
| `TypeSystemThread` | 8 | 8 |

Nothing was leaking. `poolsCreated` had passed 400 while only **103 pools were live** - they were
being collected exactly as they should. The working set is simply large: ~19,000 live TypeInfos and
1.3M `MethodBody`, of which the shared library accounts for 952 TypeInfos and the other ~18,000 are
per-compile re-flattenings that die with their pools.

**The actual ceiling was 512MB, and the JVM did not set it.** The JVM's own default is
`MaxRAMPercentage=25%`, which on the test machine is 16GB. Gradle's `Test` task overrides that with
its own **512m** default for forked workers. The heap topping out at 505-508MB was hitting 512m
exactly. `cacheReport` now prints usage AGAINST the ceiling for this reason: "heap=505MB" is
healthy against 8GB and terminal against 512m, and that difference was the whole confusion.
`-PtestMaxHeap=<size>` sets it explicitly.

**With headroom the degradation vanishes** - 14 iterations, wall time flat at 1.2-1.5s, no OOM -
and the real residuals become a clean distribution instead of noise:

| count / 14 | failure |
| --- | --- |
| **13** | `xunit-demo.x` - `IllegalStateException: Mack module (javatools_turtle) is missing` |
| 4 | `VERIFY-11` "contribution forms a cycle" (Dec28, reflect, timeouts) |
| 3 | `ConcurrentModificationException` (NumericConversions, array, services) |
| 2 | NPE - `UnresolvedNameConstant.compareDetails`, and `m_FVisited` null |

That turtle failure is near-deterministic and is therefore the next thing to fix, not the rarest
one. **Lesson for the method:** get a histogram before proposing a mechanism. A leak hypothesis is
cheap to state and expensive to disprove one cache at a time.

### Footprint, if it is ever worth attacking

Not yet - correctness first - but the numbers are recorded so the ranking is not guesswork:

1. **T3 is the real win.** ~18,000 of 19,000 TypeInfos are per-compile re-flattenings of LIBRARY
   types; that is where the 1.3M `MethodBody` plus 911k `MethodInfo` (roughly 80MB) sits. Sharing
   the library's TypeInfo removes it structurally rather than shaving it.
2. **Stripe the per-constant locks.** `ParameterizedTypeConstant` (74,708 instances) and
   `SignatureConstant` (105,544) each hold a `final StampedLock`, which is exactly the 180,252
   `StampedLock` objects in the histogram - about 8.6MB guarding memo fields that are almost never
   contended. A small static striped array indexed on identity takes that to ~64 objects.
3. **Do not micro-optimize `MethodBody`.** It is 68 objects per TypeInfo because a TypeInfo is a
   flattened view; the fix is to build fewer of them, which is item 1.

## T10 - Preparation was being silently undone: hold the library, do not re-fetch it

The dominant residual after T9 - 13 of 14 iterations - was
`IllegalStateException: Mack module (javatools_turtle) is missing`, always on `xunit-demo.x`.

**The wrong answers first**, because both were plausible and both cost time. I first blamed
`ensureLibraryPrepared` being check-then-act; it is a `ConcurrentHashMap.computeIfAbsent`, which is
atomic, so that was wrong. I then blamed `DirRepository`'s one-second scan window handing out fresh
instances - and wrote `LibraryInstanceStabilityTest` to check, which PASSED: `rebuildCache` reuses
its `ModuleInfo` when timestamp and size match, so an unchanged directory keeps the same instance.
Wrong again, and the test is kept as a guard.

**What actually found it** was making the error name the pool that lacked the type rather than only
the module that would have supplied it. One run then said:

```
no NakedRef type has been injected into the pool of xunit.xtclang.org@4541a1e6
```

while preparation had injected `xunit.xtclang.org@384dfef5`. Two instances of the same module - so
the question stopped being "why is the type missing" and became "who replaced the module".

**The answer is in `DirRepository.ensureModule`:**

```java
if (module == null || module.isModified()) {
    module = tryLoad();          // discard and re-read from disk
}
```

Preparation MODIFIES the library - it links modules and sets the NakedRef type on every pool - so
the repository treats the prepared structure as stale and re-reads it. The replacement has a fresh
`ConstantPool` with no NakedRef type, and the compile that receives it dies much later, in an
unrelated phase. **Preparation was silently undone by the act of using it.**

This is the same fact the clone was hiding, stated from the other side. `LinkedRepository`'s comment
is "create a copy, allowing the compiler to mutate the repos[0] contents": the compiler mutates what
it compiles against. Serving ONE library to many compiles therefore requires OWNING the instances,
not asking a repository for them again.

**The fix** is that `ensureLibraryPrepared` materializes the prepared modules into a
`BuildRepository` and holds it for the engine's lifetime; compiles resolve library modules from that
held repository. `injectNakedRefIntoLibrary` also stops returning null when the turtle is absent -
that silent null was what turned a preparation failure into a "Mack module is missing" in a
validation phase - and `cacheReport` now reports the held instances, because asking the repository
again described structures nothing was using. That is why the report once showed a library module
with zero cached TypeInfos while a compile was actively building against it.

| | before T10 | after T10 |
| --- | --- | --- |
| turtle failure | 13 of 14 iterations | **0 of 15** |
| fully clean iterations | 0 | **11 of 15** |
| wall per iteration | 1.2-1.5 s | **0.42-0.55 s** |

The speedup is a side effect worth naming: compiles had been re-reading library modules from disk
on every request.

### What is left, measured over 15 iterations

| count | failure |
| --- | --- |
| 6 | `VERIFY-11` "contribution forms a cycle" - always on a LIBRARY type (IntNumber, Sequential, Array, Const, String, Int64) |
| 2 | `ConcurrentModificationException` |

`VERIFY-11` is now the dominant class and the next target. That it always names a library type, and
that a cycle is reported where none exists, points at contribution resolution reading a partially
built state on a shared type rather than at anything in the compiled module.

For the `ConcurrentModificationException`: `ConstantPool.preRegisterAll` now reports which POOL is
being re-registered twice at once, and detects the pool growing during its own pass, rather than
iterating live and surfacing a bare CME. The two remaining occurrences did NOT come from there, so
there is a second site still to find.

## T11 - The contribution-visit marker, and registration that was not a fixed point

Two more, and the first is the same defect as T4 on a different mechanism.

### T11.1 - `m_FVisited`: a recursion marker on shared state

`Component.resolveContributedName` noticed it had come back round to a component it was already
inside by way of a field on the component:

```java
if (m_FVisited != null && m_FVisited.booleanValue() == fAllowInto) {
    errs.log(Severity.FATAL, VE_CYCLICAL_CONTRIBUTION, ...);   // "forms a cycle"
}
m_FVisited = fAllowInto;
... clzContrib.resolveContributedName(...) ...
m_FVisited = null;
```

Its own javadoc said **"Not thread-safe."** Once the library is shared, one thread descending
through `Array` is visible to every other, so a peer reports `VERIFY-11`, *"contribution forms a
cycle"*, against a declaration with no cycle in it. The same field produced the other residual
too - `Cannot invoke Boolean.booleanValue() because m_FVisited is null` is one thread clearing it
between another's null-check and read. **One defect, both failure classes.**

Recursion is a property of the descent, so the marker moved to `TypeSystemThread` alongside the
TypeInfo ownership record. The reset also moved into a `finally`: it was a bare assignment after
the recursive call, so a throw left the marker set permanently, poisoning every later resolution of
that component. On a per-compile clone that died with the clone; shared, it would not have.

Measured over 15 iterations: `VERIFY-11` **6 -> 0**, and the `m_FVisited` NPE with it.

### T11.2 - `writeTo` assumed one registration pass was a fixed point

The remaining failures were `ConcurrentModificationException` inside `ConstantPool.assemble`, which
says nothing useful. Replacing the for-each with an indexed loop that reports growth turned it into
a fact:

```
the pool of TestMisc@3d24b03b grew from 1830 to 1837 while being written;
the constant being assembled was MethodConstant Method{host=OrderLine, name=toString, ...}
```

Not a race at all, and not a library pool - the module's OWN pool, growing during its own write.
`assemble` writes the constant count first and then writes each constant by the POSITION of
everything it references, so a reference registration did not reach gets interned mid-write, the
pool passes the count already written, and the file is malformed. Registration can itself intern,
because resolving a reference is lazy; whether it needs to depends on shared library cache state,
which is why this shows up under concurrency and not sequentially, where the same types are warm by
the time anything is written.

`writeTo` now registers to a fixed point before assembling, bounded at eight passes with a
diagnostic rather than an unbounded loop.

| | before T11 | after T11 |
| --- | --- | --- |
| fully clean iterations | 8 of 12 | **13 of 15** |
| `VERIFY-11` | 6 | **0** |
| pool growth during write | 5 | 1 |

### What is left

| count / 15 | failure |
| --- | --- |
| 1 | `NullPointerException: UnresolvedNameConstant.compareDetails ... m_constId is null` |
| 1 | `TestGenerics` pool still grew during write, assembling a `ThisClassConstant` |

The second says the fixed point is not always reached in eight passes for one shape, which is worth
understanding rather than raising the bound. Sequential compilation is unchanged throughout at 42
ok, 0 crashes; full suite 668 tests, 0 failures.

## T12 - CORRECTED: the shared library does NOT capture per-compile types

T12 originally claimed that the shared library's `TypeInfo` and relation caches capture per-compile
types, and that this was T1's real limit. **That claim is wrong, and it was disproved by measuring
it rather than by reasoning about it.** The section is kept, corrected, because the wrong version
was acted on and the correction is the useful part.

**The invariant, stated properly.** A shared library is sound only if **nothing in it refers to a
per-request constant**. Cross-MODULE references inside the library are not violations - a flattened
`TypeInfo` contains everything the type inherits, most of it declared in `ecstasy`.

**Getting that distinction wrong is what made the first measurement useless.** The first detector
counted any member whose pool differed from the type's own, and reported hundreds: `xunit` 456,
`net` 166, `crypto` 80. They looked damning. They were **stable across iterations** - and real
pollution would GROW with the number of requests served. That stability is what exposed the
detector, not the code.

**Measured with the right definition**, over repeated concurrent runs of the suite:

| library cache | entries from outside the library |
| --- | --- |
| `TypeInfo` members (methods + properties) | **0** |
| relation-map keys | **0** |
| `f_mapRefTypes` (NakedRef, keyed by caller-supplied referent) | **0** |

The library holds nothing from any request. `SharedLibraryIsolationTest` now asserts this directly
instead of inferring it from whether compiles pass, because the failure it guards against is silent
for a long time and then surfaces as a malformed module far from its cause.

### What was real, and got fixed

`checkReservedCompatibility` took its pool from an ARGUMENT:

```java
ConstantPool pool = typeLeft.getConstantPool();   // whoever typeLeft belongs to
...
idLeft.equals(pool.clzFunction())                 // interns into THAT pool
```

so it interned into whichever pool the caller's left type happened to belong to. Its siblings
`isCovariantReturn` and `isContravariantParameter` already take the owner explicitly, with the note
that *"the owner pool is part of the API contract"* - the same rule, not applied here. The owner is
now a parameter, and the caller passes the pool whose relation map is being populated, so the
structure doing the caching and the pool being interned into are the same one.

This is the reentrancy principle again in its ownership form: **scavenging an owner out of an
argument is ambient state with extra steps.**

### What is actually left

One residual in roughly 60 iterations, and it is now legible rather than mysterious:

```
IndexOutOfBoundsException: Index 5503 out of bounds for length 396
  at ConstantPool.readConstant / getConstant
  at Component.disassembleChildren / FileStructure.disassemble
  at FileStructure.<init>            <- reading back what was just written
```

The module's CHILDREN were written with a position from a larger, foreign pool: a constant that
registration never re-interned locally kept its original owner, so `getPosition()` returned an index
valid somewhere else. That is [master row 45](master-issue-submissions.md) - registration and
assembly disagreeing about the reachable set - in its sharper form: not merely a pool that grows
during its own write, but a foreign index written into the file.

**The design answer is an assertion, not a lock:** assembly must never write a position belonging to
another pool, and that is checkable at the moment it happens. Locks made this class rare; only
enforcing ownership at the write will make it impossible.

## T15 - A soak found a retention leak proportional to compiles. T1 is not shippable yet.

126 iterations, 8 threads, 8 GB heap, roughly 5,300 compiles. It ended in `OutOfMemoryError`, and
the per-iteration cache report says exactly what grew:

| iteration | library typeInfos | library relations | poolsCreated | heap after GC |
| --- | --- | --- | --- | --- |
| 1 | 1030 | 13095 | 109 | 188 MB |
| 32 | 1030 | 13095 | 2713 | 2249 MB |
| 64 | 1030 | 13095 | 5401 | 4232 MB |
| 95 | 1030 | 13095 | 8005 | 6172 MB |
| 126 | 1030 | 13095 | 10597 | **8177 MB (99%)** |

**The library caches are perfectly flat and the heap is perfectly linear in pools created.** About
770 KB retained per compile. So the leak is not the shared library - the isolation test already
proves the library holds nothing from any request - it is that **per-compile `ConstantPool`s are
retained**, one per compile, forever.

This also corrects [T9](#t9---it-was-never-a-leak-a-512m-ceiling-and-the-residuals-it-hid), which
concluded "no leak" from a five-iteration histogram showing pools being collected (102 live against
400 created). That measurement was right and the generalisation was wrong: at five iterations
nothing had accumulated, and the conclusion did not survive two orders of magnitude more compiles.
**A leak that only shows at scale is invisible to a short run, and short runs were all T9 had.**

### What this means for T1

A resident host built on this engine would die after roughly ten thousand compiles. That is worse
than the clone it replaces, whose cost - about 18% of a warm compile - is known, bounded and
survivable. **T1 should not be the default until the retainer is found.**

The correctness work is separable and stands on its own: the ownership fixes (T4, T11, T13, T14),
the null guard, the locking and the diagnostics are all improvements with or without sharing, and
rows 41-45 carry the master-applicable ones.

### The next step, precisely

Take a class histogram at high iteration count - `jcmd <pid> GC.class_histogram` against a run of 60
or more iterations - and compare live `ConstantPool` instances against `poolsCreated`. That is the
same method that settled T9 in one shot, and the same method that should have been applied at scale
before T9's conclusion was written. If pools are live, the histogram's dominators name the retainer.

**Thread-local state is already RULED OUT**, and by accident. Fixing the harness to await executor
termination (below) means every worker thread now dies at the end of its iteration, taking its
`ThreadLocal`s with it - and the heap still climbs about 68 MB per iteration afterwards:

| iteration | poolsCreated | heap after GC |
| --- | --- | --- |
| 1 | 109 | 187 MB |
| 16 | 1369 | 1152 MB |
| 30 | 2545 | 2035 MB |

So the retainer is neither `TypeSystemThread` nor `ConstantPool.ASSEMBLING`, which were the two
obvious candidates and are both thread-local. Roughly 800 KB per compile is held by something that
outlives the threads.

### SUSPECT 1 CONFIRMED, 2026-09-07: `f_mapPreparedLibraries` retains one prepared library per compile

Found with the [XDK build harness](xdk-in-process-build-assessment.md), which is a far cheaper
reproducer than this soak: **44 compiles, not 5,300.**

```
prepared libraries retained: 44  (compiles performed: 44)
```

Exactly one per compile. `f_mapPreparedLibraries` is a `ConcurrentHashMap` **keyed by the input
repository INSTANCE** (`XtcEngine.java:191`, `ensureLibraryPrepared` at `:223`) and never evicted.
Each value is a linked, NakedRef-injected, root-warmed `BuildRepository` - about 16 MB.

**Why it only bites some callers.** `compile(Path...)` passes the engine's own `repoLibrary` every
time, so there is one entry and T1 works exactly as intended. But a caller that feeds outputs
forward - which is what compiling a dependency graph requires - must pass a *composite* repository
containing the library plus what it has built, and the natural way to write that is a fresh
repository per compile. Every one of those gets its own prepared library, forever.

So the mechanism is not broken; the **cache key is**. The contract is effectively "reuse your input
repository object or leak", and nothing says so.

**Confirmed by fixing the caller**: reusing one `LinkedRepository` over a mutable `BuildRepository`
for the whole build took retained libraries from **44 to 2** (one per pass) and pass-1 heap from
745 MB to 558 MB.

**CORRECTION: it IS the whole leak in this workload, and the "second retainer" was the same one.**
That 515 MB was measured with the map still STRONG, and a strong map pins its KEY as well as its
value. The key is the composite input repository, which wraps the `BuildRepository` holding
**everything the build has produced** - so pass 1's entire set of 22 compiled XDK modules stayed
reachable through the map. There was no second retainer; there was one retainer with two effects.

### FIXED: weak keys

`f_mapPreparedLibraries` is now `Collections.synchronizedMap(new WeakHashMap<>())`. The key decides
the lifetime, which is what it should always have done:

- a caller compiling against the engine's own library passes a repository that is a **field of the
  engine**, so its entry stays strongly reachable and the library is prepared exactly once - T1
  intact;
- a caller feeding outputs forward drops its composite repository after each compile, and the entry
  goes with it.

Safe to key weakly because the value cannot reach the key: a `PreparedLibrary` holds a
`BuildRepository` of `ModuleStructure`s, and a module has no back-reference to the repository it came
from (`ModuleRepository` appears in `FileStructure` only as a method parameter).

Preparation also moved out of `computeIfAbsent`, which would otherwise hold the map's lock across
linking and warming an entire library and serialize every compile behind the first.

**Measured, 44 compiles building the XDK twice on one engine:**

| pattern | before | after |
| --- | --- | --- |
| fresh repository per compile | 44 retained, heap 745 -> 1446 MB | **0 retained, heap 596 -> 591 MB** |
| one repository reused | 2 retained, heap 558 -> 1073 MB | **1 retained, heap 558 -> 558 MB** |

Flat in both. Suspects 2 and 3 are not disproved - this workload simply no longer grows - so if the
5,300-compile soak still climbs, the class histogram remains the method.

Remaining suspects, in order:

1. ~~**Something reachable from the engine itself.**~~ **CONFIRMED above for
   `f_mapPreparedLibraries`, and it accounts for only part of the growth.** The runtime plane and
   the diagnostic sink are still unexamined.
2. **A static.** `POOLS_CREATED` is an int, but the locator tables and interning maps are worth
   confirming rather than assuming.
3. **The JFR `CompileEvent`** committed per compile, if a recording is active.

The histogram will say which without further guessing, and guessing is what T9 got wrong.

### The harness lied about this, and that is fixed

The soak reported `skipped="1" failures="0"` while its output contained twelve
`OutOfMemoryError`s. The per-task `catch (Throwable)` recorded the OOM as a compile *outcome* - the
"never swallow" rule broken by the code meant to enforce it. An `OutOfMemoryError` is not a compile
result: the compile did not fail, the harness ran out of memory, and every result after it is
meaningless. It now rethrows, so a soak that exhausts the heap fails loudly.

The same run then sat for **twelve hours** after finishing its work in 22 minutes, parked in
Gradle's `MessageHub.stop`. It would not have exited on its own. The test now carries a 30-minute
`@Timeout`, so a wedge becomes a failed build in a knowable time rather than a machine quietly
holding a dead JVM.

## T5 - Prove diagnostics stay per request

Each compile already collects into its own `ErrorList`, but that has not been tested under
concurrency, and a host attributing another request's errors to the wrong file is worse than a
slow compile.

**Verification.** N concurrent compiles of modules with *distinct, known* errors; each result must
contain exactly its own and none of its neighbours'.

### T5 progress - the listener is NOT threaded through, and now there is a number

`TypeInfoTrace` reports through the `ErrorListener` precisely so that *where an event lands*
measures the plumbing. First run, one concurrent compile of the suite traced for `Directory`:

| | |
| --- | --- |
| events emitted | 57 |
| events that arrived in the caller's diagnostics | **16** |

Two thirds are dropped, and every event for that type was reported through a blackhole-backed
listener. That is not a bug in the trace - it is the documented state of `ensureTypeInfo`, whose
own javadoc says **"the listener parameter is a mode flag in disguise"**: the method fuses COMPUTE
(idempotent, cacheable, must never report) with VALIDATE (diagnostics owned by a source position),
so every caller has to pick a mode, and speculative probes - `testFit`, `getImplicitType`,
`getConverterTo` - pass `BLACKHOLE`. Whichever caller asks FIRST owns the diagnostics; the one that
actually cared gets the memoized answer and hears nothing.

**Why this matters more for a host than for the CLI.** The CLI compiles one module and exits, so
"the diagnostics went to the wrong listener" is invisible. A resident host serving concurrent
requests has to attribute every diagnostic to the request that caused it, and today a library
type's build reports to whatever listener happened to be threaded down that call chain - which per
the measurement is usually nobody.

**Unresolved, and recorded rather than guessed:** those 16 arrivals were all marked `SILENT` by
`ErrorListener.isSilent()`. Either the flag is wrong for a Tee-rooted chain, or silence does not
imply dropping. The contradiction is real and is not yet explained.

**Scope.** The full repair is [E32](master-enhancement-submissions.md) (thread one listener; 665
parameters, 87 `BLACKHOLE` sites) plus E34 and E35, and it is far larger than this branch. What
belongs HERE is the part a parallel host cannot ship without: the isolation test above, and a
decision on whether `ensureTypeInfo` gets split into `typeInfo()` (never reports, always caches)
and `validate(errs)` (reports once, called by the stage that owns the source position). That split
is described in `TypeConstant.ensureTypeInfo`'s own javadoc as "a project rather than a rename",
with the staged migration in `docs/errorlistener/README.md` section 8.4.

## T6 - Finish aligning the API with `LspSupport`

- Output repository per call (the input repository landed in `0bf7c88bd`).
- Adopt the `TC-xx` diagnostic code vocabulary rather than inventing a second one.
- Console capture for runs - they already built `xExternalConsole` for this; take theirs rather
  than writing a second.

## T7 - Building the XDK itself in parallel

Deliberately last. Every earlier task assumes a stable prebuilt library; here the library is the
output. It needs dependency ordering between module compiles and a story for what "the library" is
while it is still being produced. Worth doing, but not before T1-T5 make the simple case solid.

## The execution model this depends on, and why it is not a parallel stream

The obvious wish is to make a compile an element and write `sources.parallelStream()`. That is not
available here, and the reason is worth stating because it also explains what the per-thread state
in `TypeSystemThread` really is.

**The state is CALL-STACK state, not thread state.** `building` is which types this recursive
descent is currently inside; `deferred` is what that descent still owes. Both belong to the call.
The thread-local is a shortcut for not threading a context parameter through every method on the
TypeInfo build path - exactly the way `ErrorListener` IS threaded. So the correct end state is a
parameter, `ensureTypeInfo(work, errs)`, after which the execution model stops mattering at all.
That is not done because it is wide, the same shape as [E32](master-enhancement-submissions.md).

**Why `parallelStream` specifically is wrong, not merely unnecessary:**

1. It runs on the **common ForkJoinPool**, which steals work and also executes tasks on the
   CALLING thread. One carrier thread therefore runs many tasks in sequence, so anything left in
   per-thread state bleeds from one task into the next - precisely the leak
   `TypeSystemThread.endBuilding` warns about, and precisely the bug class T4 removed.
2. The common pool is process-wide and shared with every other parallel stream in the JVM, so a
   request can be neither isolated nor bounded.
3. Streams assume independent elements. Here the entire difficulty IS the shared library state;
   that is what is contended, not the elements.

**What the current design actually requires is `1 task = 1 thread`,** which a fixed pool satisfies
and work-stealing does not. The cheap way to keep that guarantee while scaling is a **virtual
thread per compile** - one task, one thread, per-task state per-thread by construction - and the
machinery is already in the tree: `Runtime.java:44` builds its IO executor with
`Thread.ofVirtual()`.

**Order:** thread-locals (today; correct only while 1 task = 1 thread) -> virtual thread per
compile (cheap, safe, available now) -> explicit context parameter (correct under any model,
wide). Do NOT skip to a work-stealing pool before the last step.

## What not to do

- **Do not route through `Launcher`.** It is a CLI entry point that does whole-process setup per
  invocation. It was used once as a diagnostic - to establish that warm-JVM compilation works at
  all, which it does - and has no place in the engine.
- **Do not make the engine a singleton.** `LspSupport.instance()` plus a one-shot `configure` is
  process-global first-configuration-wins state, and it forecloses the isolated parallel compiles
  its own proposal asks for.
- **Do not treat a passing parallel run as proof of thread safety.** The current one passes because
  of the clone, not because the shared path is safe; T1 removes that protection and T4 is what
  replaces it.
