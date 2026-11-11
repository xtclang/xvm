# Running the Ecstasy runtime in parallel

This document analyzes what keeps the interpreter in `javatools` from running safely in parallel
through the embedding API, `org.xvm.api.EmbeddingSupport`, and what it takes to change that. It
inventories the JVM-wide state, traces what that state does when two runtimes or many runs share a
JVM, proposes a staged refactor, and lists the tests that would show the work is done.

Paths are relative to `javatools/src/main/java/org/xvm/` unless they start with another top-level
directory (`lang/`, `lib_runner/`, `javatools_jitbridge/`, `javatools_utils/`, `javatools/src/test/`).
Line numbers are for commit `d2ecd0a254`. Nothing was run for this analysis. A statement marked
*inferred* follows from the code but was not traced end to end; everything else was traced through
the cited lines.

## Summary

- **Two runtimes in one JVM do not work.** When a second `NativeContainer` boots, 281 stores
  overwrite 281 of the 285 per-runtime static fields in 155 classes (the other four are never
  assigned). From then on the first runtime mixes its
  own handles with the second one's, and identity checks fail in ordinary code. In the first
  runtime, `False.toString()` returns `"True"` and `!False` is `False`
  (`runtime/template/xBoolean.java:64-77`), and `JumpNull` does not recognize the runtime's own
  `Null` (`asm/op/JumpNull.java:50`). These paths are traced, not run.
- **Concurrent runs in one runtime, today's model, are not fully safe either**, but static fields
  are not the reason: within one runtime they are consistent. The problems come from state that is
  per runtime by design. All runs share one debugger, a stop in one debugged run freezes every run's
  clock, the container registry is read without its lock, and output goes to the process streams.
- **The JIT path has no non-final static fields** and passes its context explicitly. What it lacks
  is lifecycle and isolation from process effects. `EmbeddingSupport` cannot reach it today.
  Appendix A adds that it writes into the module it is given (`javajit/Linker.java:331`,
  `:376-380`), that its class loaders are not parallel-capable, and that its console prints through
  the interpreter's JVM-wide streams; it lists 15 tasks, 19 to 28 engineer-days for the first 14.
- **The language server does not run programs in its own process.** It works around the limit
  with a JVM for each running program, so several runs in one runtime is not its case. Each
  program runs in an execution worker JVM, one program per worker at a time, with up to four
  workers (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:28-31`, `:72`;
  `lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:95-99`, `:126-127`). Debug
  sessions use the same workers (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DebugSessions.kt:56`).
  Its live case is one runtime serving consecutive runs in a reused worker. The price is a JVM
  start, a runtime boot, a heap and a full worker pool for each running program (1.5).

| Measure | Count | How counted |
| --- | --- | --- |
| Non-final static fields in `org.xvm.runtime` | 301 | `javap -p` over 572 class files; matches a source grep, see 2.1 |
| ... that hold per-runtime state, category (ii) | 285, in 155 classes | classification in 2.3 |
| ... of which template `INSTANCE` fields | 145; 36 never read | read-site scan, see 2.1 |
| Stores to those fields when a runtime boots | 281 of 288 assignment sites | enclosing-method check, see 2.1 |
| Direct read sites of category (ii) fields | 973 | regex scan of all `javatools` main sources |
| Static helper methods that read them | 135, with 995 call sites | body and call-site scan |
| Non-final statics in `org.xvm.javajit`, `api`, `asm`, `compiler`, `tool`, `type` | 0 | `javap -p` |
| Non-final statics in the JIT bridge (`javatools_jitbridge`) | 14, all per JIT instance | `javap -p` over 203 class files |

**Verdict.** Making the interpreter support several runtimes is a large refactor, but it is
mechanical and its concepts are not risky. Every template already holds its container
(`runtime/ClassTemplate.java:77`), and every container reaches its native container and runtime
(`runtime/Container.java:742-750`, `:764`). The work is giving about 2,000 read and call sites a way to
reach them without a static. Most sites already have a `Frame`. My estimate is 25 to 35
engineer-days for the interpreter in the stages of section 4, about half of it in stage 4. The
performance risk is concentrated in about 530 hot sites: booleans, `Null`, comparison results and
the void tuple. Making the concurrent-runs model safe is a much smaller job: a few targeted fixes
plus a decision on how far a debugger reaches. The JIT needs lifecycle work, not a state refactor.

## The short case: a second runtime breaks the first one's booleans

The static runtime state is often defended as harmless because there is only one runtime. That
holds only because `EmbeddingSupport` makes it hold: it is a singleton, and `configure` accepts one
core repository, once (`api/EmbeddingSupport.java:90-96`, `:320-325`). The restriction follows
from the static state. The interpreter needs the restriction; nothing else does.

The clearest demonstration needs no threads, no race and no timing, only a second runtime in the
same JVM. `xBoolean` keeps the runtime's two `Boolean` values in static fields
(`runtime/template/xBoolean.java:18-19`). Every runtime that boots assigns them again, from its own
enum values:

```java
// runtime/template/xBoolean.java:30-31, in initNative(), which runs as each runtime boots
FALSE = (BooleanHandle) getEnumByOrdinal(0);
TRUE  = (BooleanHandle) getEnumByOrdinal(1);
```

The template then tests a handle by identity against those fields:

```java
// runtime/template/xBoolean.java:68, in buildStringValue(): Boolean.toString()
xString.makeHandle(hTarget == FALSE ? "False" : "True")

// runtime/template/xBoolean.java:75-76: Boolean.not(), which Neg and Compl use too (:55-63)
public static BooleanHandle not(BooleanHandle hValue) {
    return hValue == FALSE ? TRUE : FALSE;
}
```

Boot runtime A, then runtime B. B's boot replaces `FALSE` and `TRUE` with B's handles. A program
in A still gets A's own `False` from A's constant heap (`runtime/template/xEnum.java:85-103`), and
that handle is no longer `==` the static `FALSE`. So in A:

| In runtime A, with `b` a `Boolean` holding `False` and `s` a `String?` holding `Null` | Should be | Is after B boots | Why |
| --- | --- | --- | --- |
| `b.toString()` | `"False"` | `"True"` | A's `False` is not B's `FALSE` (`xBoolean.java:68`) |
| `!b` | A's `True` | B's `False` | `not` returns the static `FALSE`, now B's (`xBoolean.java:76`) |
| `s == Null` | `True` | `False` | `IsNull` compares with the static `xNullable.NULL` (`asm/op/IsNull.java:49`); `JumpNull` and `JumpNotNull` branch the same way (`asm/op/JumpNull.java:50`, `asm/op/JumpNotNull.java:49`) |

The values have to reach the ops at run time, for example as a method's arguments: the compiler may
fold a literal expression such as `!False` into a constant *(inferred)*.

These outcomes are traced through the cited lines, not observed in a run. Three properties make
the case strong:

- **It is deterministic.** It happens on one thread, every time, in the order "boot A, boot B, run
  in A". No thread-safety argument is involved.
- **The newest runtime looks fine.** B's handles match the statics, so a test that checks only the
  runtime it just made passes. The runtime that booted first is the one that breaks.
- **It is one instance of a pattern, not a bug in `xBoolean`.** 281 of the 285 per-runtime static
  fields are reassigned when a runtime boots (section 3.1, and 2.3 for the list), and about 2,000
  sites read them. `False`/`True` and `Null` are only the shortest paths to a wrong answer.

What triggers it, and what does not:

- **Consecutive runs in one runtime do not.** The statics are assigned only while a runtime boots:
  the template constructors run from `NativeContainer` (`runtime/NativeContainer.java:195`), and
  the other templates made "as the instance" are made in `registerNativeTemplates` during that boot
  (`runtime/template/collections/xArray.java:75-80`,
  `runtime/template/_native/collections/arrays/xRTViewToBit.java:31-34`). Only the
  `InterpreterConnector` constructor makes a `NativeContainer` (`api/InterpreterConnector.java:36-41`),
  and every run through an `EmbeddingSupport` reuses its connector (`api/EmbeddingSupport.java:247-255`,
  `:1419`). So the second, tenth and hundredth run of one runtime see the statics of that
  runtime. This is the language server's case: a reused execution worker serves consecutive runs
  with one runtime (section 1.5).
- **Shutting a runtime down and booting the next one does not either**, for the booleans: after
  the next boot the statics are consistent again. That sequence has failures of its own, such as a
  pending wake-up that can stop the JVM-wide timer for every later runtime (section 3.2).
- **Two runtimes alive at once do.** `EmbeddingSupport`'s singleton prevents that in its own use,
  but `InterpreterConnector`'s constructor is public (`api/InterpreterConnector.java:36`), and
  `InterpreterControl` and `tool/Runner.java` each make one per call (`api/InterpreterControl.java:56`,
  `tool/Runner.java:494`). Any host or test that makes a second one, before shutting the first down,
  is in the case above.

A test makes the case concrete. Tests in `org.xvm.api` can make their own instances, since the
constructor is package-private (`javatools/src/test/java/org/xvm/api/EmbeddingApiCompatibilityTest.java:40`).
Make two `EmbeddingSupport` instances over the same core repository, boot both with
`ensureConnector()`, then run a module in the first that passes `False` and `Null` to a method
that prints `b.toString()`, `!b` and `s == Null` through a captured console. It should print
`False`, `True` and `True`. Today it should print `True`, `False` and `False` *(expected, not
run)*. The test passes once stage 4 of
section 4.4 has removed the statics it reads, and it belongs with the acceptance tests of 6.1.

## 1. Scope

| Level | What it means | Today | Blocking |
| --- | --- | --- | --- |
| a. Concurrent runs in one runtime | Several `runMethod` calls and debug sessions on one `EmbeddingSupport` | Supported by the API; no current host does it, but other embedders and tests can | Runtime-wide debugger and clock freeze, a few unsynchronized structures, process output (3.3) |
| a'. Consecutive runs in one runtime | One run after another on a runtime that is never shut down | The language server's live case (1.5) | State that one run leaves to the next (1.5) |
| b. Independent runtimes in one JVM | Different core repositories or XDK versions, separate shutdown and restart, tests that each make their own | Refused for the singleton, unguarded otherwise | 285 per-runtime statics (2.3), JVM-wide threads (2.4) |
| c. A runtime alongside concurrent compiles | Compiling while the runtime runs, in one JVM | Works with the documented serialization rule | Covered by the compiler document; notes in 1.3 |
| d. The JIT path | `org.xvm.javajit` behind `JitConnector` | Not reachable through `EmbeddingSupport` | Lifecycle and process effects (section 5) |

### 1.1 Concurrent runs in one runtime (a)

`EmbeddingSupport` has one connector, and every run shares it
(`api/EmbeddingSupport.java:247-256`). A run is a task of the `runner.xtclang.org` module in the
main container, registered and started through `invokeAsync`
(`api/InterpreterControl.java:55-61`, `:119-149`), and each task gets its own nested `Container`
(`lib_runner/src/main/x/runner.x:240-261`). Nothing in `runMethod` limits how many runs are active
(`api/EmbeddingSupport.java:1387-1432`). The method's own Javadoc says that a run with a debugger
"is meant to be its only run" (`api/EmbeddingSupport.java:1377-1378`), and nothing enforces that.
No current host runs programs this way: the language server's worker refuses a second run (1.5).
The case still matters for other embedders and for tests.

What is per run and what is shared:

| State | Scope | Evidence |
| --- | --- | --- |
| Templates, compositions, constant heap of core types | Runtime (native container), concurrent maps | `runtime/Container.java:819-824`, `runtime/ConstHeap.java:233` |
| Program templates and compositions | Run (nested container) | `runtime/Container.java:296-352`, `:394-418` |
| Console | Run: a named resource with a JVM-unique id | `runtime/template/_native/io/xExternalConsole.java:75-89`, `runtime/NativeContainer.java:402-417` |
| Console when the host passes none | Process: the terminal console, prefixed by task id | `lib_runner/src/main/x/runner.x:248-256`, `runtime/template/_native/io/xTerminalConsole.java:226-237` |
| Debugger and its "active" flag | Runtime | `runtime/Runtime.java:229-237`, `runtime/ServiceContext.java:210-228` |
| Frozen time | Container chain up to the native container | `runtime/Container.java:526-550` |
| Worker pool | Runtime, fixed size | `runtime/Runtime.java:31-46` |
| Current service, working pool | Thread, set and restored per drain | `runtime/ServiceContext.java:317-319`, `:349` |

Section 3.3 lists what concurrent runs can still corrupt. In short, consoles, templates and
compositions are safe; the debugger, time, reflection on foreign types and default output are not.

### 1.2 Independent runtimes in one JVM (b)

The class Javadoc gives the reason for the singleton (`api/EmbeddingSupport.java:90-96`), and
`configure` rejects a second configuration (`api/EmbeddingSupport.java:320-333`). That guard
covers only the singleton. The package-private constructor (`api/EmbeddingSupport.java:115`),
which the tests use (for example `javatools/src/test/java/org/xvm/api/RuntimeRetentionTest.java:86`),
and the public `InterpreterConnector` constructor (`api/InterpreterConnector.java:36-41`, also used by
`tool/Runner.java:494`) both create runtimes without a check. A static lock serializes connector
creation across `EmbeddingSupport` instances (`api/EmbeddingSupport.java:124`, `:248`). It prevents
interleaved boots but not two runtimes existing at once. Today two runtimes work only if they never
overlap, and the javatools tests depend on that: they run in one JVM without parallel execution
(no `junit-platform.properties`, no parallel settings in `javatools/build.gradle.kts`), and they
shut each interpreter down before the next starts (`javatools/src/test/java/org/xvm/api/RuntimeRetentionTest.java:52`, `:65`, `:74`).
Section 3.1 shows what happens when two runtimes do overlap.

### 1.3 A runtime alongside concurrent compiles (c)

This is the compiler document's subject. The points that involve the runtime are:

- Booting a runtime clobbers the calling thread's working pool. `loadNativeTemplates` calls
  `setCurrentPool(pool)` and later `setCurrentPool(null)` instead of opening a `withPool` scope
  (`runtime/NativeContainer.java:150`, `:221`; `asm/ConstantPool.java:3462-3484`). A host that calls
  `ensureRuntimePool()` inside a compile's pool scope then has no working pool until that scope
  closes.
- The runtime does not share the compiler's structures. Merging a module clones it
  (`asm/FileStructure.java:229`), a run copies the application module through its bytes
  (`api/InterpreterControl.java:157-175`), and `DirRepository` synchronizes its entry points
  (`asm/DirRepository.java:27`, `:84`).
- `asm` and `compiler` have no non-final statics. Their shared mutable state is the unsynchronized
  `TypeConstant.s_setRecursions` (`asm/constants/TypeConstant.java:6189`, `:8656-8660`), which
  runtime type checks also reach, and four atomic label counters used only for names
  (`compiler/ast/ElvisExpression.java:318`, `compiler/ast/ElseExpression.java:220`,
  `compiler/ast/ConditionalStatement.java:77`, `compiler/ast/MethodDeclarationStatement.java:1257`).
- The rule "hosts must serialize compilations that share this instance's configured repository"
  still applies (`api/EmbeddingSupport.java:87-88`).

### 1.4 The JIT path (d)

`useJit()` returns false (`api/EmbeddingSupport.java:240-242`), and `JitControl` throws for every
operation (`api/JitControl.java:17-60`). `tool/Runner.java:492` is the only place that creates a
`JitConnector`. Section 5 analyzes the JIT's state.

### 1.5 How the language server works around the limit

**The workers.**

- Each running program has a worker JVM of its own, with at most four running at once
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:28-31`, `:72`). A fifth
  program is refused with a message (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:183-187`).
- An idle worker is reused for the next program. One idle worker is kept for ten minutes; any
  other worker exits when its run ends
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:35-36`, `:75`, `:462-480`).

**Inside a worker,** the runtime is the singleton.

- It is configured once from the bundled XDK
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibraries.kt:60-62`,
  `lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:71`), booted by the first run and
  never shut down. The worker ends by exiting the process
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:89-91`).
- It takes one run at a time (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:95-99`,
  `:126-127`).
- It sends the process streams to the current run, and the runtime's own failures to the worker's
  log (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:66-70`, `:119-122`).

This avoids all three failure scenarios of section 3:

- there is never a second runtime in the JVM (3.1);
- no runtime is shut down and replaced inside a JVM (3.2);
- with one run at a time, no run shares the debugger, the frozen clock or the stray output with
  another (3.3).

**What it does not avoid** is state that one run leaves to the next in a reused worker.

- After each run, the worker reports its heap after the JVM's latest collections, its threads, its
  runtime pool, and the consoles and runs it still holds for ended programs
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:540-562`).
- The server takes no further runs on a worker that still holds a console or a run of an ended
  program, whose runtime pool grew, or that is over 512 MB of heap or 200 threads
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:39-41`, `:76-77`,
  `:440-460`).
- Commit `b55d92c1f7` added a soak test that drives one worker through rounds of runs, stops and
  debug sessions.

Some carried-over state is not in those figures:

- the native container's shared injectables, such as OS storage and the networks, which are
  created by the first run that injects them (`runtime/NativeContainer.java:883-892`);
- the terminal console's service (`runtime/template/_native/io/xTerminalConsole.java:73-81`);
- the directory watches of ended runs, which are never removed
  (`runtime/template/_native/fs/xOSStorage.java:288`); that their events still reach the shared
  storage service is *inferred*;
- service wake-ups still pending on the JVM-wide timer
  (`runtime/ServiceContext.java:2129-2154`).

**The cost of the workaround.** None of these costs were measured here.

- **Startup.** Every new worker starts a JVM and configures the XDK. Its first run then boots a
  runtime: loading and cloning the core modules and constructing every native template
  (`runtime/NativeContainer.java:110-223`). A worker may take up to 60 seconds to report ready
  before it is ended (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:73`,
  `:298-301`).
- **A cold JIT.** HotSpot compiles each process separately, so every new worker starts cold. Only
  a reused worker is warm.
- **Memory.** The worker command passes no heap option
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:524-535`), so each worker
  gets the JDK's default maximum heap. A runtime held about 115 MB in the measurement of commit
  `f5ab6a72b7`.
- **Threads.** Each worker's runtime sizes its pool to all cores (`runtime/Runtime.java:31-46`).
  Four running programs therefore mean four times as many pool threads as cores.
- **Indirection.** All output, input and debugger traffic travels as JSON lines over the worker's
  stdin and stdout (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:44-53`).

What the workers buy beyond working around the limit is isolation: "a hung or crashing program
cannot take editor features down with it", and a stuck program can be ended by ending its process
(`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:28-36`). Section 7 weighs that.

## 2. Inventory of JVM-wide state

### 2.1 How it was counted

- **Fields.** I ran `javap -p` over a copy of the 572 class files in
  `javatools/build/classes/java/main/org/xvm/runtime`, which were built from the clean tree at
  `d2ecd0a254`, and selected field lines that contain `static` and not `final`. That gives
  301. The source grep from the request (`^\s*(public |protected |private )?static (?!final\b)…`)
  gives 300. The one it misses is `Utils.CONST_HELPER`, which is declared with two spaces
  (`runtime/Utils.java:1774`). With that one added, the field names from the two methods match
  exactly. The same `javap` pass over `api` (23 classes), `asm` (500), `compiler` (255),
  `javajit` (82), `tool` (43) and `type` (5) finds no non-final statics. Over the 203 class files of
  `javatools_jitbridge/build/classes/java/main` it finds 14.
- **Static finals with mutable contents.** `org.xvm.runtime` has 357 static finals. I listed the
  ones whose type is a collection, array, atomic, `ThreadLocal`, timer or similar, then grepped for
  writes (`NAME[...] =`, `.add(`, `.put(`, `.remove(`, `.clear(`, `.computeIfAbsent(`). One array
  holds per-runtime contents: `xArray.CONSTRUCTORS`, which `initNative` fills
  (`runtime/template/collections/xArray.java:103-119`, `:1038`). No static final is initialized
  from a template, handle or composition. The grep `static final [^=]+=.*(\.INSTANCE|getCanonicalClass|makeHandle\(|xBoolean\.|xNullable\.|xString\.)`
  finds nothing.
- **Assignments.** For each field I counted lines in its own file that assign the unqualified name,
  plus qualified assignments anywhere, and attributed each site to its enclosing method; I
  spot-checked the attribution by hand. The scan missed one store qualified by a nested class name,
  `FullyBoundHandle.NO_OP = ...` (`runtime/template/_native/reflect/xRTFunction.java:66`), which I
  added by hand. That gives 288 assignment sites and 10 declaration initializers. Of the 288 sites,
  141 are `INSTANCE = this` in template constructors and 140 are in `registerNativeTemplates`,
  `initNative` or `Utils.initNative`, so 281 run when a runtime boots: exactly one store for each
  category (ii) field except the four that are never assigned. The other 7 are `DebugConsole.activate` (3), `xTerminalConsole.ensureLineReader` (2),
  `Runtime.setFailureReporter` (1) and `xOSStorage.ensureWatchDaemon` (1).
- **Reads.** For each field I counted qualified uses (`Class.FIELD`) in every main source file and
  unqualified uses in its own file, excluding comments, declarations and assignments. That gives
  1,001 read sites, 973 of them of category (ii). The static helpers are static methods in
  `org.xvm.runtime` whose bodies read a category (ii) field: 135 of them, with 995 qualified and
  same-file call sites. These are regex counts, so a few may be off.

### 2.2 Totals by category

| Category | Non-final statics | Static finals and other state |
| --- | --- | --- |
| (i) immutable after class init, identical in every runtime | 8 | the other static finals: enums, patterns, constants, empty arrays |
| (ii) per-runtime state stored statically | 285 | `xArray.CONSTRUCTORS` |
| (iii) global mutable caches and registries | 2 | counters, `ConsoleLog`, `DebugConsole.INSTANCE`, `TypeConstant.s_setRecursions` |
| (iv) process-level side effects | 6 | `System.exit`, process streams, system properties, files |
| (v) ThreadLocals and executors | 0 | 4 static final `ThreadLocal`s; executors are per `Runtime` instance |
| Total | 301 | |

### 2.3 The categories

**(i) Safe to share.** `ClassTemplate.VOID`, `THIS`, `OBJECT`, `INT`, `STRING`, `BOOLEAN` and `BYTES`
are string arrays that are never reassigned and never read (`runtime/ClassTemplate.java:2428-2434`).
`xRTClassTemplate.NO_TEMPLATES` is an empty array
(`runtime/template/_native/reflect/xRTClassTemplate.java:573`). None of the eight is `final`, which
they should be. Most of the 357 static finals also belong here: enum constants, `Pattern`s,
`LongLong` constants, empty arrays such as `Utils.OBJECTS_NONE`, `Debugger.NONE`
(`runtime/Debugger.java:12`), `ObjectHandle.DEFAULT` (`runtime/ObjectHandle.java:1139`) and the
`Op[]` continuations in `Frame` and `Utils`.

**(ii) Per-runtime state stored statically.** This is the abstraction break. By kind:

| Kind | Count | Examples |
| --- | --- | --- |
| Template references | 160 | 145 `INSTANCE`, 6 other `ClassTemplate`s (`Utils.ANNOTATION_TEMPLATE`, `xEnum.RANGE_TEMPLATE`), 5 enum templates (`xArray.MUTABILITY`), 4 maps of templates (`xArray.ARRAY_TEMPLATES`, `xAtomic.NUMBER_TEMPLATES`, `xRTDelegate.DELEGATES`, `xRTViewToBit.VIEWS`) |
| Handles | 15 | `xBoolean.TRUE`/`FALSE`, `xNullable.NULL`, `xOrdered.LESSER`/`EQUAL`/`GREATER`, `xTuple.H_VOID`, four `xString` handles, `xBit.ZERO`/`ONE`, `xRTFunction.NATIVE_NO_OP`, `FullyBoundHandle.NO_OP` |
| Compositions | 29 | `xObject.CLASS`, 21 `xException.s_clz*`, six `xArray.*_CLZ`, `xRTPropertyClassTemplate.PROPERTY_CLASS_TEMPLATE_COMP` |
| Pool-bound structures and constants | 79 | 35 `MethodStructure`s, 20 `TypeConstant`s, 7 `SignatureConstant`s, 5 `ClassConstant`s, 5 `ArrayConstant`s, 4 `PropertyConstant`s, a `MethodConstant`, a `VersionConstant`, `Utils.CONST_HELPER` |
| Values derived from the core modules | 2 | `xService.s_setAtomicProperties`, and `xRTConnector.s_sAgent`, which embeds the core module's version (`runtime/template/_native/web/xRTConnector.java:75-79`) |

By package, with every class that declares one (a count in parentheses is the number of fields,
when more than one):

| Package under `runtime` | Fields | `INSTANCE` | Never read | Reads | Classes |
| --- | --- | --- | --- | --- | --- |
| `template` | 55 | 7 | 0 | 459 | Identity (2), Proxy, xBoolean (2), xConst (14), xEnum (3), xException (22), xNullable, xObject (2), xOrdered (3), xService (5) |
| `template._native.reflect` | 41 | 14 | 0 | 155 | xRTClassTemplate (11), xRTComponentTemplate, xRTFileTemplate (3), xRTFunction (5), xRTFunction.FullyBoundHandle, xRTMethod, xRTMethodTemplate, xRTModuleTemplate (2), xRTPackageTemplate (2), xRTProperty (2), xRTPropertyClassTemplate (2), xRTPropertyTemplate, xRTSignature, xRTType (5), xRTTypeTemplate (3) |
| `template.numbers` | 32 | 30 | 4 | 110 | xBFloat16, xBit (2), xCheckedInt8, xCheckedInt16, xCheckedInt32, xCheckedInt64, xCheckedUInt8, xCheckedUInt16, xCheckedUInt32, xCheckedUInt64, xDec32, xDec64, xDec128, xFPLiteral, xFloat16, xFloat32, xFloat64, xInt8, xInt16, xInt32, xInt64, xInt128, xIntLiteral, xIntN, xNibble, xUInt8, xUInt16, xUInt32, xUInt64, xUInt128, xUIntN |
| `template.collections` | 20 | 6 | 1 | 64 | BitBasedArray, xArray (13), xBitArray, xByteArray, xNibbleArray, xTuple (3) |
| `template._native.collections.arrays` | 45 | 43 | 18 | 46 | LongBasedBitView, LongDelegate, LongLongDelegate, xRTBitDelegate, xRTBooleanDelegate, xRTCharDelegate, xRTDelegate (2), xRTFloat64Delegate, xRTInt8Delegate, xRTInt16Delegate, xRTInt32Delegate, xRTInt64Delegate, xRTInt128Delegate, xRTNibbleDelegate, xRTSlicingDelegate, xRTStringDelegate, xRTUInt8Delegate, xRTUInt16Delegate, xRTUInt32Delegate, xRTUInt64Delegate, xRTUInt128Delegate, xRTViewFromBit, xRTViewFromBitToBoolean, xRTViewFromBitToByte, xRTViewFromBitToNibble, xRTViewFromByte, xRTViewFromByteToFloat64, xRTViewFromByteToInt8, xRTViewFromByteToInt16, xRTViewFromByteToInt64, xRTViewToBit (2), xRTViewToBitFromFloat64, xRTViewToBitFromInt8, xRTViewToBitFromInt16, xRTViewToBitFromInt32, xRTViewToBitFromInt64, xRTViewToBitFromInt128, xRTViewToBitFromNibble, xRTViewToBitFromUInt8, xRTViewToBitFromUInt16, xRTViewToBitFromUInt32, xRTViewToBitFromUInt64, xRTViewToBitFromUInt128 |
| `template.reflect` | 19 | 9 | 3 | 30 | xClass (2), xClassTemplate, xEnumValue, xEnumeration, xInjector, xModule (4), xPackage (3), xRef (3), xVar (3) |
| `runtime` | 16 | 0 | 0 | 29 | Utils (16) |
| `template.text` | 8 | 3 | 2 | 19 | xChar, xRegEx, xString (6) |
| `template._native.fs` | 9 | 3 | 1 | 16 | xCPDirectory, xCPFile, xCPFileStore, xOSDirectory (2), xOSFile (2), xOSStorage (the `onEvent` method), xRawOSFileChannel |
| `template.annotations` | 7 | 4 | 2 | 10 | xAtomic (2), xAtomicIntNumber, xFuture (3), xInject |
| `template._native.io` | 4 | 3 | 1 | 5 | xExternalConsole, xRTBuffer (2), xTerminalConsole |
| `template._native.crypto` | 8 | 7 | 4 | 5 | xRTAlgorithms, xRTCertificateManager, xRTDecryptor, xRTHasher, xRTKeyGenerator, xRTKeyStore (2), xRTSigner |
| `template._native.mgmt` | 4 | 3 | 0 | 5 | xContainerControl, xContainerLinker (2), xCoreRepository |
| `template.maps` | 1 | 1 | 0 | 4 | xListMap |
| `template._native.net` | 5 | 4 | 1 | 4 | xRTNameService (2), xRTNetwork, xRTNetworkInterface, xRTSocket |
| `template._native.lang.src` | 2 | 1 | 0 | 3 | xRTCompiler (2) |
| `template._native.web` | 3 | 2 | 0 | 3 | xRTConnector (2), xRTServer |
| `template._native` | 2 | 1 | 0 | 2 | xRTServiceControl (2) |
| `template._native.temporal` | 2 | 2 | 0 | 2 | xLocalClock, xNanosTimer |
| `template._native.numbers` | 1 | 1 | 0 | 1 | xRTRandom |
| `template._native.collections` | 1 | 1 | 0 | 1 | xBasicHashCollector |
| Total | 285 | 145 | 37 | 973 | 155 classes |

The 37 never-read fields are 36 `INSTANCE`s and `xString.EMPTY_ARRAY`. Four of the `INSTANCE`s are
also never assigned, because they are declared on abstract bases: `BitBasedArray`,
`LongBasedBitView`, `LongDelegate` and `LongLongDelegate`. For example,
`xRTInt32Delegate.INSTANCE` is assigned when `xRTDelegate` registers it
(`runtime/template/_native/collections/arrays/xRTDelegate.java:64`) and never read anywhere.

Reference counts for the main statics:

| Static | Declared, assigned | Reads | Files |
| --- | --- | --- | --- |
| `xBoolean.FALSE` | `runtime/template/xBoolean.java:19`, `:30` | 137 | 56 |
| `xBoolean.TRUE` | `runtime/template/xBoolean.java:18`, `:31` | 109 | 50 |
| `xNullable.NULL` | `runtime/template/xNullable.java:14`, `:25` | 84 | 36 |
| `xRTSignature.INSTANCE` | `runtime/template/_native/reflect/xRTSignature.java:37`, `:43` | 26 | 1 |
| `xRTFunction.INSTANCE` | `runtime/template/_native/reflect/xRTFunction.java:49`, `:55` | 22 | 3 |
| `xArray.INSTANCE` | `runtime/template/collections/xArray.java:63`, `:70` | 21 | 11 |
| `xOrdered.LESSER`, `GREATER`, `EQUAL` | `runtime/template/xOrdered.java:16-18`, `:29-31` | 18, 18, 11 | 7, 7, 5 |
| `xInt64.INSTANCE` | `runtime/template/numbers/xInt64.java:16`, `:22` | 15 | 10 |
| `xTuple.H_VOID` | `runtime/template/collections/xTuple.java:46`, `:60` | 12 | 5 |
| `xObject.INSTANCE` | `runtime/template/xObject.java:14`, `:21` | 11 | 6 |
| `xObject.CLASS` | `runtime/template/xObject.java:15`, `:28` | 2 | 2 |
| All 145 `INSTANCE` fields | | 372 | |

The direct reads understate how far the statics reach, because most code reaches them through
static helpers that read them inside. The helpers with the most call sites:

| Helper | Static read | Call sites |
| --- | --- | --- |
| `xBoolean.makeHandle(boolean)` | `TRUE`, `FALSE` (`runtime/template/xBoolean.java:71-73`) | 118 |
| `xInt64.makeHandle(long)` | `INSTANCE` (`runtime/template/numbers/xInt64.java:63-64`) | 102 |
| `xString.makeHandle(...)` | `INSTANCE`, `EMPTY_STRING` (`runtime/template/text/xString.java:403-411`) | 101 |
| `xException.makeHandle(...)` | `s_clzException` (`runtime/template/xException.java:288-309`) | 68 |
| `xException.illegalArgument`, `outOfBounds`, `ioException`, `unsupported`, `makeObscure`, `illegalState` | the `s_clz*` compositions | 35, 28, 24, 17, 17, 13 |
| `xFuture.makeHandle`, `xArray.makeByteArrayHandle`, `xOrdered.makeHandle`, `xArray.makeStringArrayHandle` | `TYPE`, `BYTE_ARRAY_CLZ`, `LESSER`/`EQUAL`/`GREATER`, `STRING_ARRAY_CLZ` | 20, 20, 18, 17 |
| 123 others | | 377 |

Identity is involved in many of these uses. There are 36 `==` or `!=` comparisons against
`xBoolean.TRUE` or `FALSE` and 25 against `xNullable.NULL`. In addition, 25 `this == INSTANCE`
guards in 19 classes make `initNative` behave differently depending on the static (for example
`runtime/template/xObject.java:27`).

**Which statics the hot interpreter paths read.** The weight of the performance check in section 4
falls on these:

| Path | Static reads | Evidence |
| --- | --- | --- |
| Null tests in ops | `xNullable.NULL` by identity | `asm/op/JumpNull.java:50`, `asm/op/JumpNotNull.java:49`, `asm/op/IsNull.java:49` |
| Comparison jumps and switches | `xOrdered.LESSER`, `GREATER` by identity | `asm/op/JumpLt.java:61`, `asm/op/OpSwitch.java:194-217`, and the other `Jump*`/`Is*` comparisons |
| Boolean results of ops | `xBoolean.makeHandle`, 16 calls in 11 op classes | for example `asm/op/OpSwitch.java:208` |
| All ops together | 48 direct reads in 25 op classes, 3 more in `asm/constants` | grep of `asm` for template statics |
| Conditional and void returns | `xBoolean.TRUE`, `xTuple.H_VOID` | `runtime/Frame.java:1156-1157`, `:1182-1183` |
| Fiber creation | `xNullable.NULL` for the timeout and async section | `runtime/Fiber.java:50`, `:103`, `:658` |
| Service state | `xNullable.NULL`, `xTuple.H_VOID` | `runtime/ServiceContext.java:139`, `:146`, `:2074`, `:2249` |
| Integer and string results of natives | `xInt64.makeHandle`, `xString.makeHandle` | see the helper table |

Some hot paths read no static at all. Integer arithmetic creates its result through the template
instance (`runtime/template/numbers/xConstrainedInteger.java:435-441`), `JumpTrue` reads the handle's
field (`asm/op/JumpTrue.java:53`), and the per-op debugger check goes through the runtime instance
(`runtime/ServiceContext.java:592`, `:210-214`).

**(iii) Global mutable caches and registries.**

| Item | What it is | Evidence |
| --- | --- | --- |
| `xLocalClock.TIMER` | One `java.util.Timer` thread for the JVM. It serves clock alarms, timer alarms and every service's wake-ups in every runtime | `runtime/template/_native/temporal/xLocalClock.java:319`, `runtime/template/_native/temporal/xNanosTimer.java:318`, `:462`, `runtime/ServiceContext.java:2154` |
| `xOSStorage.s_daemonWatch` | One file-watch thread for the JVM. It is created with the pool of the first runtime that watches a directory and keeps every registered storage handle | `runtime/template/_native/fs/xOSStorage.java:251-262`, `:272`, `:288`, `:293` |
| `DebugConsole.INSTANCE` | The console debugger, with its breakpoints, frames and step mode; it is the default debugger of every `Runtime` | `runtime/DebugConsole.java:2416`, `:2442-2580`, `runtime/Runtime.java:175-181` |
| `xTerminalConsole.CONSOLE_LOG` | An unsynchronized 1,024-line ring buffer of everything printed to the terminal console | `runtime/template/_native/io/xTerminalConsole.java:229`, `javatools_utils/src/main/java/org/xvm/util/ConsoleLog.java:160-192` |
| `TypeConstant.s_setRecursions` | An unsynchronized `HashSet` that type checks add to | `asm/constants/TypeConstant.java:6189`, `:8656-8660` |
| Id counters | `Fiber.s_counter`, `ObjectHandle.TransientId.s_hashCode`, `xExternalConsole.CONSOLE_ID`. These are atomic and monotonic, so sharing them is harmless | `runtime/Fiber.java:696`, `runtime/ObjectHandle.java:993`, `runtime/template/_native/io/xExternalConsole.java:224` |

**(iv) Process-level side effects.**

| Effect | Evidence |
| --- | --- |
| `System.exit(0)` on Ctrl-C at a JLine prompt, and `System.exit(1)` in the console debugger. These are reachable only when `System.console()` is not null | `runtime/template/_native/io/xTerminalConsole.java:152-155`, `runtime/DebugConsole.java:325-328` |
| The terminal console captures `System.in` and `System.out`, or `System.console()`, once at class initialization | `runtime/template/_native/io/xTerminalConsole.java:226-237` |
| JLine reader and terminal, static in two classes (4 of the 6 non-final statics) | `runtime/template/_native/io/xTerminalConsole.java:167-192`, `runtime/DebugConsole.java:81-86`, `:2421-2431` |
| `Runtime.s_failureReporter`, a JVM-wide setter whose default is `System.err`; the language server's worker sets it | `runtime/Runtime.java:91-95`, `:202`, `lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:70` |
| The default unhandled-exception handler prints to `System.out`, and an op's Java exception goes to `System.err` | `runtime/ServiceContext.java:1648-1655`, `runtime/Utils.java:359-377`, `runtime/ServiceContext.java:603-604` |
| Diagnostics on `System.err` from about 20 sites | for example `runtime/ClassTemplate.java:2022`, `runtime/Frame.java:1560`, `runtime/template/xException.java:82` |
| System properties read at boot (`xvm.parallelism`, `org.xvm.runtime.debugger`) and exposed to programs (`xvm.*`) | `runtime/Runtime.java:31`, `:56`, `runtime/NativeContainer.java:532-545` |
| Directories from `user.home`, `user.dir` and `java.io.tmpdir`, and a JLine history file named after the module | `runtime/template/_native/fs/xOSStorage.java:92-100`, `runtime/template/_native/io/xTerminalConsole.java:179-182` |

The language server's worker shows what a host has to do about this. It redirects `System.out` and
`System.err` and sets the failure reporter before configuring
(`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:66-71`). That redirection assumes a
single run.

**(v) ThreadLocals and executors.**

| Item | Scope | Evidence |
| --- | --- | --- |
| `ServiceContext.s_tloContext` | Per thread; set and restored around each drain | `runtime/ServiceContext.java:2323`, `:317-319`, `:349` |
| `ConstantPool.s_tloPool` | Per thread; boot does not restore it (1.3) | `asm/ConstantPool.java:3829`, `runtime/NativeContainer.java:150`, `:221` |
| `MultiMethodStructure.s_tloIgnoreNative` | Per thread | `asm/MultiMethodStructure.java:57-67` |
| `JumpNSample.f_rnd` | `ThreadLocalRandom.current()` stored in a static and used from every pool thread | `asm/op/JumpNSample.java:62`, `:65` |
| XVM worker pool | Per `Runtime`: fixed size, `xvm.parallelism` or all cores, daemon threads | `runtime/Runtime.java:31-46` |
| IO executor | Per `Runtime`: a virtual thread per task | `runtime/Runtime.java:48-51` |
| JVM-wide threads | `ecstasy:LocalClock` timer, `WatchServiceDaemon` | see (iii) |

The JDK says to use `ThreadLocalRandom` only as `ThreadLocalRandom.current().nextX(...)`. In
current JDKs the seed lives in the calling thread and `current()` initializes it. A pool thread
that never calls `current()` therefore starts from the same seed as every other such thread
(*inferred* from the JDK implementation). The effect is correlated sampling, not memory unsafety.

## 3. Failure scenarios

### 3.1 A second Connector in the same JVM

Suppose runtime A is running and a host creates runtime B, with a second `EmbeddingSupport` or with
`new InterpreterConnector(repo)`.

1. `new Runtime()` creates B's executors and reads two system properties. It touches no static
   state (`runtime/Runtime.java:30-59`).
2. `new NativeContainer(runtime, repo)` boots B (`runtime/NativeContainer.java:97-106`):
   - It loads and clones the core modules into B's own `FileStructure` and pool
     (`runtime/NativeContainer.java:111-150`).
   - It constructs `xObject`, `xEnum`, `xConst` and `xService` first, with `fInstance` set, because
     their `INSTANCE`s "are used by the ClassTemplate constructor"
     (`runtime/NativeContainer.java:168-173`).
   - It constructs every concrete `x*` class under `org/xvm/runtime/template` reflectively with
     `fInstance` set (`runtime/NativeContainer.java:175-200`). There are 141 `INSTANCE = this`
     stores across these and the templates that `registerNativeTemplates` creates
     (`runtime/NativeContainer.java:209-211`).
   - `Utils.initNative` replaces 16 statics (`runtime/Utils.java:81-102`), and `initNative` on
     every template replaces the rest of the 281 (`runtime/NativeContainer.java:215-217`): the
     booleans (`runtime/template/xBoolean.java:28-31`), `Null`, the `Ordered` values, `CLASS`, the
     exception compositions (`runtime/template/xException.java:44-64`), the array compositions and
     constructors (`runtime/template/collections/xArray.java:103-143`), and so on.
3. A keeps running, and every static read now returns B's object. What follows was traced through
   the code; the outcomes have not been seen in a run.
   - **Booleans.** A program's `False` comes from A's constant heap. Its handle is created by A's
     enum value template from A's `xBoolean` (`runtime/template/xEnum.java:85-103`, `:311-312`).
     `xBoolean.buildStringValue` compares the handle to the static `FALSE`, which is now B's, and
     returns `"True"` (`runtime/template/xBoolean.java:64-67`). `not` returns B's `FALSE` for A's
     `False` (`runtime/template/xBoolean.java:75-77`), and `Neg` and `Compl` use `not`
     (`runtime/template/xBoolean.java:55-63`).
   - **Null.** `JumpNull`, `JumpNotNull` and `IsNull` compare by identity with the static `NULL`
     (`asm/op/JumpNull.java:50`, `asm/op/JumpNotNull.java:49`, `asm/op/IsNull.java:49`), so A's own
     `Null` is not null to them. `ServiceContext.setSynchronizedSection` would take A's `Null` for a
     section and read a field from it (`runtime/ServiceContext.java:139-150`); that the read then
     fails is *inferred*.
   - **New handles.** `xInt64.makeHandle`, `xString.makeHandle` and the `xException` factories now
     create handles with B's compositions (`runtime/template/numbers/xInt64.java:63-64`,
     `runtime/template/text/xString.java:407-411`, `runtime/template/xException.java:300-309`). A's
     frames hold them. Anything that later reaches a container or pool through them reaches B's.
     That this fails once B is shut down is *inferred*.
   - **Template wiring.** A creates templates for its program's classes lazily
     (`runtime/Container.java:314-351`). A template without an `extends` takes the static
     `xObject.INSTANCE` as its super (`runtime/ClassTemplate.java:174-188`), so A's later classes
     get B's `Object`. `xArray.ARRAY_TEMPLATES` is keyed by `TypeConstant`, whose equality is
     structural across pools (`asm/Constant.java:596-605`), so A's bit and byte arrays get B's
     templates (`runtime/template/collections/xArray.java:87-92`, `:173`, `:182`). A delegate
     compares a template with `xInt32.INSTANCE` by identity
     (`runtime/template/_native/collections/arrays/LongBasedDelegate.java:574`).
   - **Consoles.** `xExternalConsole.register(A's container, ...)` binds the resource to the static
     `INSTANCE.ensureConsole` (`runtime/template/_native/io/xExternalConsole.java:75-80`). That
     method creates the console's service in the template's own container
     (`runtime/template/_native/io/xExternalConsole.java:106-112`), so the console of A's new run
     becomes a service of B, scheduled on B's pool. Once B shuts down, scheduling it fails
     (*inferred*: `runtime/Container.java:173-174`, `runtime/Runtime.java:132-135`).
   - **The embedding boundary itself.** `InterpreterControl.create` builds the runner's arguments
     on the host thread from statics: `xCoreRepository.INSTANCE`, `xNullable.NULL`,
     `xInt64.makeHandle` and `xString.makeHandle` (`api/InterpreterControl.java:106-117`). A's
     runner is therefore handed B's handles.
   - **Shared threads.** Both runtimes' alarms and service wake-ups run on the one timer thread, and
     file-watch events run under whichever pool watched first (see 2.3, (iii)).
4. If A and B boot at the same time on two threads, two more failures appear. The 25
   `this == INSTANCE` guards can see the other runtime's template and skip native setup; for
   example, `xObject.initNative` marks the native methods only when `this == INSTANCE`
   (`runtime/template/xObject.java:26-34`). And `initResources` binds A's injectable resources to
   whichever `INSTANCE` is current at that moment (`runtime/NativeContainer.java:300-394`). The
   resulting failures are *inferred*. `EmbeddingSupport`'s static lock prevents this case for its
   own instances (`api/EmbeddingSupport.java:248`). Direct connector construction is not locked.

The order also matters: the runtime that booted *first* is corrupted, so a test that checks only
the newest runtime passes.

### 3.2 Shutdown, then a new runtime

`shutdownInterpreter()` calls `InterpreterConnector.shutdown()`. That cancels the native
container's timer alarms and calls `shutdownXVM()` (`api/EmbeddingSupport.java:263-270`,
`api/InterpreterConnector.java:194-203`). `shutdownXVM()` calls `shutdown()`, not `shutdownNow()`,
so running tasks finish (`runtime/Runtime.java:153-156`). Nothing resets the statics.

- **Before the next boot,** all 285 statics still point at the dead runtime. Its templates reach
  its native container, its `Runtime` and its executors, so a host that shuts down without booting
  again keeps one dead runtime. Commit `f5ab6a72b7` measured about 115 MB for each runtime that a
  JVM kept.
- **After the next boot,** the 281 boot stores replace the statics, and the new runtime is
  consistent. Commit `f5ab6a72b7` moved 27 lazily cached values into template instance fields for
  this reason, and `RuntimeRetentionTest` checks two of them
  (`javatools/src/test/java/org/xvm/api/RuntimeRetentionTest.java:36-53`).
- **Stale after the next boot:**
  - **The file-watch daemon.** It keeps the pool of the first runtime that watched, uses that pool
    as the working pool for every later event, and keeps every storage handle registered with it.
    Nothing in the class removes a watch (`runtime/template/_native/fs/xOSStorage.java:251-262`,
    `:288`, `:293`).
  - **Pending service wake-ups.** `WakeUpScheduler` is an inner class of `ServiceContext`, and its
    `TimerTask` is an anonymous inner class of the scheduler
    (`runtime/ServiceContext.java:2129-2154`, `:2350`). A pending wake-up therefore holds the dead
    runtime's service on the JVM-wide timer until it is due, and shutdown does not cancel it
    (`api/InterpreterConnector.java:194-203`). Clock alarms hold their service only weakly
    (`runtime/WeakCallback.java`, which extends `WeakReference<ServiceContext>`).
  - **That wake-up can disable timers for every later runtime.** When it fires, it calls
    `ensureScheduled(true)`, then `execute(false)`, `Container.schedule` and
    `Runtime.submitService` (`runtime/ServiceContext.java:2143-2148`, `:364-379`;
    `runtime/Container.java:173-174`; `runtime/Runtime.java:132-135`). The submit happens outside
    `schedule`'s try block, on an executor that has been shut down and has no rejection handler
    (`runtime/Runtime.java:45-46`). The rest is JDK behavior: the executor throws
    `RejectedExecutionException`, an exception in a `TimerTask` ends a `java.util.Timer`'s thread,
    and every later `schedule` call throws `IllegalStateException`. Because the timer is static
    (`runtime/template/_native/temporal/xLocalClock.java:319`), the next runtime's alarms and
    service timeouts then fail. `xLocalClock.invokeSchedule` turns that failure into a program
    exception (`runtime/template/_native/temporal/xLocalClock.java:129-134`);
    `WakeUpScheduler.schedule` does not catch it (`runtime/ServiceContext.java:2154`).
  - **The console debugger.** `DebugConsole.INSTANCE` keeps the frames and breakpoints of its last
    session (`runtime/DebugConsole.java:2442-2580`). This matters only where the console debugger is
    the runtime's debugger; `EmbeddingSupport` sets `Debugger.NONE`
    (`api/InterpreterControl.java:57`).
  - **Work still running after shutdown.** A task that resumes after the next boot, such as an IO
    task blocked in a console read, reads the new runtime's statics (*inferred*).

### 3.3 Concurrent runs in one runtime

The statics are consistent within one runtime, so they are not the problem here. What concurrent
runs can still corrupt:

| Hazard | Evidence | Status |
| --- | --- | --- |
| **One debugger for all runs.** Starting run B with a debugger replaces run A's and detaches it (`Runtime.setDebugger` calls `previous.detach()`). When A ends, `replaceDebugger` finds B's debugger in place and does nothing, so A's session silently stops getting stops | `api/InterpreterControl.java:127-132`, `runtime/Runtime.java:186-195`, `api/InterpreterConnector.java:82-89` | traced |
| **A debugger sees every run.** Breakpoint checks run for every service of the runtime, and the "active" flag is global ("for now, we use a global flag"). Only the host's container is excluded, so a breakpoint in a module that another run also executes stops that run too | `asm/op/Nop.java:82-83`, `runtime/ServiceContext.java:210-228`, `runtime/SessionDebugger.java:2210-2213` | traced; the stop in another run is *inferred* |
| **A stop freezes everyone's clock.** A stop freezes the *native* container's time, and every container adds the frozen time of its ancestors | `runtime/SessionDebugger.java:2529-2530`, `runtime/Container.java:488-550` | traced |
| **No yielding while the debugger holds fibers.** While it does, no service in the runtime yields after `MAX_OPS_PER_RUN`, so other runs can occupy pool threads | `runtime/ServiceContext.java:592` | traced; the starvation is *inferred* |
| **The container registry is read without its lock.** `findContainer` iterates the `WeakHashMap` unsynchronized, while new runs' containers register under the lock. `WeakHashMap` also expunges entries during reads. Reflection on foreign types uses it | `runtime/Runtime.java:100-125`, `runtime/Container.java:63-66`, `runtime/template/_native/reflect/xRTType.java:544` (and six more sites) | traced; a `ConcurrentModificationException` or a wrong result is *inferred* |
| **Shared injectables are created check-then-act.** OS storage, directories, properties and networks are cached in plain fields of the native container, so two runs injecting at once can create two | `runtime/NativeContainer.java:427-460`, `:532-578`, `:640-659` | traced; the duplicate is *inferred* and mostly harmless |
| **Program failures go to the process streams.** The default unhandled-exception handler and op failures print to the process streams, which a host cannot attribute to a run; the language server's routing of stray output assumes one run | `runtime/ServiceContext.java:1648-1655`, `:603-604`, `lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:119-122` | traced |
| **One timer for all alarms and timeouts.** If one task throws, timers stop for every run (the JDK behavior in 3.2) | `runtime/template/_native/temporal/xLocalClock.java:319`, `runtime/ServiceContext.java:2154` | *inferred* for a live runtime |
| **`TypeConstant.s_setRecursions`** is mutated from any thread | `asm/constants/TypeConstant.java:6189` | traced; corruption is *inferred* from `HashSet`'s contract |
| **`JumpNSample`** uses one `ThreadLocalRandom` from many threads | `asm/op/JumpNSample.java:62-65` | see 2.3, (v) |

Not hazards: per-run consoles (ids are JVM-unique and the resource map is concurrent), template
and composition caches (concurrent maps), the constant heap, and the thread-local context, which
is restored after each drain (all cited in 1.1). The terminal console's ring buffer is written only
by the runtime's single console service, so it races only when two runtimes print at once
(`runtime/template/_native/io/xTerminalConsole.java:73-81`).

## 4. Fix design

### 4.1 The Javadoc's proposal against the code

The class Javadoc proposes holding the templates and compositions in the `NativeContainer` and
reaching them through the container each template refers to
(`api/EmbeddingSupport.java:102-107`). Taking its claims one at a time:

- **"The NativeContainer already maps types to their templates."** True:
  `f_mapTemplatesByType` (`runtime/Container.java:824`), filled at boot
  (`runtime/NativeContainer.java:293-298`, `:782-784`; `runtime/ClassTemplate.java:127-129`).
  But it is keyed by `TypeConstant`, and the name-based accessor costs two hash lookups
  (`runtime/NativeContainer.java:734-737`, `:859-866`). That cannot replace a static read on a hot
  path, so the container also needs typed accessors.
- **Compositions.** Compositions are already per container (`runtime/Container.java:394-418`,
  `:819`). The 29 composition statics only cache a few of them, and they can become instance fields
  the way the 27 caches of commit `f5ab6a72b7` did. `xString` already caches its empty string array
  in an instance field, reached through the static `INSTANCE`
  (`runtime/template/text/xString.java:430-434`). That shows the half-way state.
- **"Reached through the container that each template already refers to."** Templates hold their
  container (`runtime/ClassTemplate.java:77`, `:2439`). Most readers, however, are not template
  instance methods. They are the 135 static helpers, which have no `this`, the ops, the field
  initializers in `Frame`, `Fiber` and `ServiceContext`, and host code
  (`api/InterpreterControl.java:106-117`). Each of these needs a context passed in or found
  through a handle, and that is where most of the work is.
- **What the proposal leaves out.** It does not cover runtime services held in statics: the timer,
  the watch daemon, the failure reporter, the console debugger and the console log. These belong to
  the `Runtime`, whose lifecycle owns the executors (`runtime/Runtime.java:153-156`). It also does
  not cover the process effects in 2.3, (iv). Those do not prevent a second runtime, but they break
  isolation between runtimes.
- **"`configure` would become a constructor."** Yes. A `close()` is also needed, to replace the
  test-only `shutdownInterpreter()`.

The proposal names the right destination but underestimates the reach. The Javadoc counts about
150 assignments, while the readers number about 1,000 direct reads and 1,000 helper calls.

### 4.2 Where the per-runtime state lives

| State | Owner | Reason |
| --- | --- | --- |
| Native templates, hot handles, cached compositions, pool-bound constants | `NativeContainer` | They are bound to its pool and `FileStructure` (`runtime/NativeContainer.java:135-150`) |
| Timer, file-watch daemon, failure reporter, console log, console debugger | `Runtime` | Shut down with the executors |
| A run's debugger | The run's container subtree | The API already passes a debugger per run (`api/EmbeddingSupport.java:1387-1396`) |

`Runtime` and `NativeContainer` are created as a pair (`api/InterpreterConnector.java:39-40`,
`tool/Runner.java:494`), so neither needs a new runtime-context object.

**Reaching it:**

- **Containers** get a final `f_native` field: the container itself for the native container,
  otherwise its parent's. This replaces the parent walk in `getNativeContainer()`
  (`runtime/Container.java:742-750`).
- **`ServiceContext`** gets a final field with the hot entries, copied from its container when it
  is constructed. Ops then read `frame.f_context.<field>.TRUE`.
- **Templates** read `f_container.f_native`. Template-to-template references that are now
  `xFoo.INSTANCE` either move into an instance field resolved in `initNative`, or go through a
  class-keyed index that `storeNativeTemplate` and `registerNativeTemplate` fill.
- **Code that holds only a handle** uses `getComposition().getContainer()`
  (`runtime/TypeComposition.java:25`).
- **Static helpers** take a `Frame` or `Container` argument, or become template instance methods.
  Most callers already have a frame. A heuristic scan of enclosing signatures finds that at most 6
  of 115 `xBoolean.makeHandle` calls, 21 of 102 `xInt64.makeHandle` calls and 18 of 93
  `xString.makeHandle` calls sit in methods without a `Frame` parameter. Those take the container
  of their template or of a handle.
- **Host code** uses its connector's native container (`api/InterpreterConnector.java:151-153`).
- **Identity checks** become structural, so that they need no context: `BooleanHandle.get()`
  (`runtime/template/xBoolean.java:85`) for truth, and a `NullHandle` subclass, which `xNullable`'s
  handle factory would create, for null. This removes the 61 identity comparisons and makes null
  tests in ops an `instanceof` check.

**Performance.** A read of a non-final static is one load: C2 folds only `static final` fields, so
these reads are not folded today either. `frame.f_context.f_core.TRUE` is three dependent loads
from objects that are hot in the cache. HotSpot does not treat ordinary instance finals as
constants. The added cost is therefore two loads for each value produced on the hot paths in 2.3,
small next to an op's virtual dispatch (`runtime/ServiceContext.java:598`). I expect it to be
unmeasurable on large programs and a few percent at most on micro benchmarks of boolean-producing
ops, but that is an *inferred* estimate that stage 3 must measure. `instanceof NullHandle` costs
about what `getstatic` plus a compare costs. Replacing the parent walk in `getNativeContainer()`
makes the existing callers faster. A `ThreadLocal` or `ScopedValue` lookup of the current runtime
would cost more than a field chain and does not exist on host threads, so it should not be used on
hot paths.

### 4.3 Alternatives

- **One class loader for each runtime.** Loading `org.xvm.runtime` once per runtime in its own
  loader would give each runtime its own statics without touching 155 classes. But
  `ModuleStructure`, `ErrorListener`, `Debugger` and `Control` cross the API, so the boundary would
  have to use only JDK types, and every runtime would carry its own copy of about 1,500 classes in
  metaspace. This could serve as an interim measure for tests, but it is not a fix.
- **A thread-local current runtime.** `ServiceContext.getCurrentContext()` already exists
  (`runtime/ServiceContext.java:186-188`). It is unset on host and IO threads, and every read costs a
  `ThreadLocal` lookup. It is acceptable for cold paths during migration, not as the design.
- **Process isolation.** This is what the language server does
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:28-31`). It costs a JVM
  start, a runtime boot, a cold JIT, a heap and a full worker pool for each running program (1.5).
  In return it protects against hung programs and `System.exit`, which nothing inside a JVM can
  protect against. See section 7.

### 4.4 Stages

Each stage compiles and keeps the tests green. The order within a stage is always the same: add
the new accessor, migrate the readers while the static is still assigned, then delete the static
so that the compiler finds any reader that was missed. An architecture test from stage 0, an
allowlist of non-final statics, shrinks with each stage.

| Stage | Content | Sites | Effort | Risk | Performance check |
| --- | --- | --- | --- | --- | --- |
| 0 | Guard and baseline | 3 files + 2 tests | 1–2 d | low | record the benchmark baseline |
| 1 | Dead statics, local races | about 45 files | 2–3 d | low | none needed |
| 2 | Container and service reach their native container in O(1) | 4 files | 1 d | low | expect a small gain |
| 3 | Booleans, `Null`, `Ordered`, void tuple | about 530 sites, about 120 files | 4–6 d | medium | micro and macro benchmarks |
| 4 | `INSTANCE` reads and static helpers, by family | 372 reads + up to 854 helper calls | 8–12 d | medium | string, integer and collection benchmarks |
| 5 | Pool-bound constants and cached compositions | 133 statics, 212 reads | 3–4 d | low–medium | exception benchmark |
| 6 | Runtime services, debugger scope, process effects | about 15 files | 4–6 d | medium | none needed |
| 7 | `EmbeddingSupport` API | 2 files + tests | 1–2 d | low | none needed |

The effort figures are estimates for mechanical edits plus review. They do not include
reviewers' time or the benchmark runs.

**Stage 0: guard and baseline.**

- `NativeContainer`'s constructor refuses to boot while another native container is live, using a
  temporary static that shutdown clears. This turns the silent corruption of 3.1 into an
  `IllegalStateException`. Tests that leave a runtime running will fail, which is the point.
- Add the allowlist test of non-final statics in `org.xvm.runtime`, initially with all 301.
- Add the benchmark harness of 6.4 and record the baseline.

**Stage 1: dead statics and local races.**

- Delete the 37 never-read fields and their stores, and make the eight category (i) fields final.
- Read `findContainer`'s map under its lock (`runtime/Runtime.java:118-125`).
- Call `ThreadLocalRandom.current()` at each use (`asm/op/JumpNSample.java:62-65`).
- Make `s_setRecursions` a concurrent set (`asm/constants/TypeConstant.java:8656-8660`).
- Use `withPool` at boot (`runtime/NativeContainer.java:150`, `:221`).
- Make the lazy injectables safe to publish (`runtime/NativeContainer.java:427-659`). Their `R_CALL`
  paths assign in continuations, so this needs care.
- Until stage 6, enforce in `runMethod` the rule that a debugged run is the only run.

**Stage 2: O(1) reach.** Add `Container.f_native` and the `ServiceContext` field. Add the
class-keyed template index and typed accessors on `NativeContainer` for the hot entries, set once
at the end of boot.

**Stage 3: the hot handles.** These are `TRUE`, `FALSE`, `NULL`, `LESSER`, `EQUAL`, `GREATER` and
`H_VOID`: 389 direct reads, plus 141 calls of `xBoolean.makeHandle`, `xBoolean.not` and
`xOrdered.makeHandle`.

- Make the identity checks structural, as described in 4.2.
- Make producers take their value from the frame's service, or from `this` inside the template.
- Initialize the fields of `Fiber` and `ServiceContext` from their container
  (`runtime/Fiber.java:50`, `:103`, `:658`; `runtime/ServiceContext.java:2249`).
- The risk is in semantics. Ecstasy code can return `Lesser` from a natural `compare`, so
  comparison results must be tested by ordinal, not by identity, wherever natural code can produce
  them (`asm/op/JumpLt.java:61`).

**Stage 4: `INSTANCE` reads and helpers, one family at a time.** The order follows read volume:
`numbers` (30 fields, 105 reads), `_native.reflect` (14, 106), the core `template` classes (7,
28), the collections and array delegates (49, 72), `reflect`, `text`, then the rest.

- Turn helpers into instance methods or give them a context argument.
- Turn the 25 `this == INSTANCE` guards into an instance flag that keeps the `fInstance`
  constructor argument.
- Delete each `INSTANCE` once nothing reads it.

**Stage 5: pool-bound values.** Move the 35 `MethodStructure`s, 20 `TypeConstant`s, 29
compositions, `xArray.CONSTRUCTORS`, the `Utils` statics and the rest into instance fields of the
template that computes them. Readers reach them through the accessors from stage 4.

**Stage 6: runtime services and process effects.**

- The timer and the watch daemon become `Runtime`-owned and are cancelled in `shutdownXVM`. The
  wake-up and alarm tasks catch `RejectedExecutionException`.
- The failure reporter becomes a `Runtime` field that defaults to the static, kept for
  compatibility with `lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:70`.
- The console debugger and the console log move to the `Runtime`.
- The debugger is scoped to a run's container subtree. `ServiceContext.getDebugger()` then asks
  its container, and a stop freezes only that subtree's time
  (`runtime/SessionDebugger.java:2529-2530`).
- Replace `System.exit` in `xTerminalConsole` and `DebugConsole` with terminating the container.
- Route the default unhandled-exception output and the runtime's diagnostics to the runtime's
  reporter.

**Stage 7: the API**, described in 4.6. Remove the stage 0 guard.

### 4.5 What stays static

These are category (i) items that are identical in every runtime:

- the eight fields of 2.3, (i), made final;
- the enums, `Pattern`s, `LongLong` constants, empty arrays, `Debugger.NONE`,
  `ObjectHandle.DEFAULT` and the `Op[]` continuations;
- the monotonic id counters (`runtime/Fiber.java:696`, `runtime/ObjectHandle.java:993`,
  `runtime/template/_native/io/xExternalConsole.java:224`);
- the per-thread `s_tloContext` and `s_tloPool`.

The process terminal also stays process-wide: `System.console()`, its reader and writer, and the
JLine reader and terminal (`runtime/template/_native/io/xTerminalConsole.java:167-192`,
`:226-237`). Only one runtime can own the terminal. That should be documented as a limitation,
not hidden.

### 4.6 `EmbeddingSupport`'s API

- **Constructors.** Add `public EmbeddingSupport(ModuleRepository coreRepo)` and a variant that
  also takes the custom injector. Today's package-private constructor
  (`api/EmbeddingSupport.java:115`) and `configure` (`api/EmbeddingSupport.java:320-333`) are
  replaced. The class stays non-final, because a test subclasses it
  (`javatools/src/test/java/org/xvm/api/EmbeddingApiCompatibilityTest.java:153`).
- **`close()`.** Make the class implement `AutoCloseable`. `close()` is idempotent, closes the
  runs' `Control`s, and shuts the connector down. It replaces the test-only `shutdownInterpreter()`
  (`api/EmbeddingSupport.java:263-270`). After `close()`, runs and `ensureConnector()` throw
  `IllegalStateException`.
- **The lock** becomes per instance instead of static (`api/EmbeddingSupport.java:124`).
- **`instance()` stays** as the process default, configured lazily from `XDK_HOME` or once
  through `configure`, so the language server's worker needs no change
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:71`, `:143`). `configure` stays
  for the default instance only, with today's same-or-throw rule, and is deprecated for new hosts.
- **The class Javadoc** describes instances, the per-run debugger, and the process-wide terminal.

## 5. The JIT path

[Appendix A](#appendix-a-the-jit-what-must-be-done) checks this section against the code, corrects
it (A.0), and turns it into numbered tasks (A.5).

| Aspect | Finding | Evidence |
| --- | --- | --- |
| Reachability | `EmbeddingSupport` never uses the JIT, and `JitControl` is a stub | `api/EmbeddingSupport.java:240-242`, `api/JitControl.java:17-60` |
| Static state | No non-final statics in `org.xvm.javajit` (82 classes). Its static finals are immutable: comparators and `Map.ofEntries` | `javap -p`; `javajit/Xvm.java:172-179`, `javajit/builders/CommonBuilder.java:4577` |
| Registries and counters | Instance fields of `Xvm`: containers, type systems, module loaders, name counters, locks | `javajit/Xvm.java:94-190`, `javajit/NativeTypeSystem.java:139-170` |
| Context | Every generated call takes a `Ctx` holding the `Xvm` and the container. The `ScopedValue` for the current context is an `Xvm` instance field | `javajit/Ctx.java`, `javajit/Xvm.java:190`, `javajit/Container.java:163` |
| Class loaders | Each type system has a `TypeSystemLoader` whose parent is the application loader, and `ModuleLoader`s that define generated classes | `javajit/TypeSystemLoader.java:30`, `javajit/ModuleLoader.java:86-108` |
| Bridge classes | Read as bytes through a per-`Xvm` `URLClassLoader` and defined in the native type system's `ModuleLoader`, so their statics are per `Xvm`. The bridge jar is deliberately kept off the classpath | `javajit/NativeTypeSystem.java:76`, `:134-137`, `:171-220`; `javatools_jitbridge/build.gradle.kts:19`; `xdk/build.gradle.kts:51` |
| Bridge statics | 14 non-final statics in 203 classes. `NanosTimer.TIMER` is one `java.util.Timer` thread per `Xvm` and is never cancelled. `RTRandom.THREAD_LOCAL` is one random number generator per `Xvm`, despite its name. The other 12 are `$INSTANCE` singletons of enum values | `javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java:63`, `javatools_jitbridge/src/main/java/org/xtclang/_native/numbers/RTRandom.java:224-233` |
| Process effects | After every run, `invoke0Impl` deletes and recreates `./jasm/` in the working directory, and it prints unhandled exceptions to `System.out` | `javajit/JitConnector.java:164-183`, `:152` |
| Lifecycle | No shutdown. Each `Xvm`'s timer thread and its bridge `URLClassLoader` stay open | `javajit/NativeTypeSystem.java:76`; there is no `close` or `shutdown` in `javajit/Xvm.java` |
| Debugger | Not supported | `api/EmbeddingSupport.java:1397-1399` |

The JIT was designed for several instances: it passes context explicitly, as stage 4 does for the
interpreter. To run it in parallel through `EmbeddingSupport`, it needs:

- a real `JitControl` and `JitConnector.shutdown()` that cancel `NanosTimer.TIMER` and close the
  bridge loader;
- the class dump moved behind a flag and into a directory for each `Xvm`, because two concurrent
  JIT runs delete each other's `./jasm/`;
- output routed to the run's console.

One risk follows from `ModuleLoader` overriding only `findClass`: class loading is then
parent-first. If the bridge jar ever ended up on the application class path, the application loader
would resolve the bridge classes, and their statics would become JVM-wide (*inferred*).

## 6. Acceptance tests

None of these tests may depend on the collector or on machine speed: no `System.gc()`, no
`WeakReference` polling, and no assertion on elapsed time. Waits are bounded only to stop hangs.

### 6.1 Two runtimes with different core repositories, running at once

- **Setup.** Repository A is the test resource `/xdk-modules`, which `RuntimeRetentionTest`
  already uses (`javatools/src/test/java/org/xvm/api/RuntimeRetentionTest.java:59-61`). Repository B
  is a copy in a temporary directory with `ecstasy.xtclang.org` stamped with a different version.
  How to stamp it without an XDK build is open (section 7).
- **Program.** One module, compiled against each core. It prints `False.toString()`, `!False`, a
  `x == Null` branch on a nullable local, the result of a natural `compare` that returns `Lesser`,
  a string built from integers, a caught `OutOfBounds` raised by a native, a `Byte[]` and a
  `Bit[]` operation, and a console line. It loops K times.
- **Run.** Create two `EmbeddingSupport`s, release their first runs together from a
  `CyclicBarrier` so that the boots overlap, then run both programs concurrently.
- **Assert:**
  - each run's output is exact;
  - every template in each native container's template map has a `f_container` whose native
    container is its own, and so does the super of each template the program created;
  - the hot handles' compositions belong to their own runtime;
  - each runtime's HTTP connector user agent contains its own core version, which today comes from
    a shared static (`runtime/template/_native/web/xRTConnector.java:75-79`).
- **Today.** This test fails on the runtime that booted first, on its `False.toString()` line
  (3.1).

### 6.2 Shutdown and restart without stale references

- **First runtime.** Run, in runtime A, a program that leaves a pending alarm, a service wake-up
  with a timeout, an open console read, and a file watch. Close A.
- **Assert on A:**
  - A's executors are terminated, checked with `awaitTermination` and an hour bound;
  - its timer and watch threads have ended, checked with `join` and the same bound;
  - its runs' consoles are unregistered;
  - using A's `EmbeddingSupport` or a `Control` of A throws `IllegalStateException` instead of
    reaching another runtime.
- **Second runtime.** Boot runtime B and run a program that schedules an alarm and a service
  timeout, and assert that both fire.
- **Today.** That last assertion is the one that catches the timer failure of 3.2.
- **Static scan.** Enumerate the classes of `org.xvm.runtime` (from the class directory or jar, as
  `runtime/NativeContainer.java:157-166` does) and read every static field. Look one level into
  arrays, collections and maps. Map each value to its native container: through `f_container` for
  a template, `getContainer()` for a composition, `getComposition()` for a handle, the pool for a
  constant, and the thread group for a thread. Assert that nothing maps to A. This is reachability
  from statics, checked directly, with no collector involved.

### 6.3 Many concurrent runs in one runtime

- **Plain runs.** Start 32 runs from 8 host threads, for several rounds, in one `EmbeddingSupport`.
  The programs:
  - reflect on foreign types while creating nested containers, which exercises `findContainer`;
  - schedule short alarms and calls with timeouts;
  - read and write their own consoles;
  - inject storage and directories at the same moment, which exercises the lazy injectables;
  - throw and catch exceptions.
- **Debug run.** One more run of a module M is debugged with a breakpoint, while four plain runs of
  M run at the same time. Assert:
  - the plain runs complete with the expected output while the debugged run is held at its
    breakpoint; the test joins them before resuming the debugged run, so it checks completion, not
    timing;
  - their 1 ms alarms fire, so their clocks are not frozen;
  - every stop the debugger reports belongs to the debugged run's container.
- **After all runs close.** Assert `registeredConsoles() == 0` (`api/EmbeddingSupport.java:279-283`),
  an unchanged `poolThreads()` (`api/EmbeddingSupport.java:293-297`), and an empty registry of run
  containers. The registry is weak today (`runtime/Runtime.java:217`), so stage 6 must make it
  explicit for this check to be possible without the collector.
- **Consecutive runs.** Run the same programs one after another, 200 times, on the one runtime,
  which is the language server's case (1.5). After each run, assert that the runtime holds no
  console, run, container, directory watch or pending timer task of an ended program, and that its
  pool has not grown. These are the worker's retirement criteria, checked here as exact counts
  instead of heap and thread totals.
- **Today.** The debug part fails (3.3). A race test cannot prove the absence of a race, so the
  `findContainer` fix also needs review.

### 6.4 Interpreter performance before and after

This is a benchmark, run on demand on one quiet machine with fixed JVM flags. It is not a
pass/fail test in `check`.

- **Harness.** Run each program through `EmbeddingSupport` in one JVM: 5 warm-up runs, then 20
  measured runs, reporting the median and the 90th percentile.
- **Micro programs.** Comparisons that produce booleans, and `!`; null tests in a loop over a
  nullable list; comparison-heavy sorting; building strings from integers; throwing and catching;
  calls across services, which create fibers and return void.
- **Macro programs.** The `manualTests` modules that run without input, such as
  `manualTests/src/main/x/collections.x`, `numbers.x` and `loop.x`, and one `xunit` suite.
- **Thresholds.** Run the benchmark after stages 3, 4 and 5. A macro median regression over 2%
  blocks the stage. A micro regression over 5% is investigated with `-XX:+PrintInlining` and JFR
  allocation profiles before the stage lands.
- **Records.** The figures go in the stage's commit message.

## 7. Open questions and risks

- **Who needs two runtimes in one JVM?** Today the language server pays a JVM, a runtime boot, a
  cold JIT, a heap and a full worker pool for each running program (1.5). Part of that is a
  workaround for the limit, and part of it buys isolation: a hung or crashing program must not
  reach the editor (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:28-31`).
  The isolation part remains after this work, because nothing inside a JVM can stop a runaway
  program or contain `System.exit`. The fix would let a worker run several trusted programs, such
  as test runs, in one warm runtime. The other beneficiaries are the javatools tests and embedders
  that trust their programs. Whether stages 3 to 5, about three to four weeks, are worth it should
  be decided before stage 3.
- **What pays off either way.** Stages 1 and 6 fix concurrent runs in one runtime and the state
  that one run leaves to the next. That second part is the language server's live case.
- **Debugger semantics for each run.** Which container subtree does a debugger own? Should a stop
  freeze only that subtree's time? "Solo" stepping recognizes the fibers it spawns by id, from a
  JVM-wide counter (`runtime/Fiber.java:164-166`, `runtime/SessionDebugger.java:1896`, `:3456`), so
  another run's new fibers would count as the stepped run's (*inferred*).
- **Hidden identity.** The grep found 61 identity comparisons, but handles may also be compared in
  other ways, such as map keys or `equals` overrides, that a grep cannot see. Every producer of
  `Null` must create the new handle class, including constants, defaults and natives.
- **Sharing a constant pool.** Enum handles are cached on their `SingletonConstant`
  (`runtime/template/xEnum.java:87-98`). Runtimes are isolated only because a run copies its module
  (`api/InterpreterControl.java:157-175`) and the native container clones the core modules
  (`asm/FileStructure.java:229`). Any new path that shares a pool between runtimes would leak
  handles from one to another. The invariant should be asserted.
- **Threads and memory.** Each runtime sizes its pool to every core (`runtime/Runtime.java:31-46`)
  and costs about 115 MB (commit `f5ab6a72b7`). N runtimes multiply both. A shared XVM pool with a
  queue per runtime may be needed.
- **Performance unknowns.** It is not known how an extra parameter on hot helpers affects
  inlining, or what an extra field in `ServiceContext` costs. Only measurement (6.4) settles this.
- **The test fixture.** It is unverified whether a core repository with a different version can be
  produced in a unit test without building a second XDK.
- **The command-line runner.** `xec` creates its own connector (`tool/Runner.java:492-494`) and
  uses the console debugger by default. Should `DebugConsole` stay process-level for it?
- **The JIT bridge on the class path** (section 5).

## Appendix A. The JIT: what must be done

This appendix turns section 5 into work. It checks section 5 against the code, says what "parallel"
means for the JIT path, compares what `EmbeddingSupport` does with the interpreter with what a JIT
backend must also do, lists every resource an `Xvm` holds and every effect it has on the process,
and ends with tasks and open questions.

Paths follow the conventions at the top of this document; `plugin/`, `xdk/` and `build-logic/` are
also top-level directories, and `doc/parallel-compiler.md` is the compiler document. A JDK citation
names the class; where it gives a line, the line is from the OpenJDK 27.0.1 sources. Line numbers
are for commit `d2ecd0a254`. Nothing was run. *Inferred* marks what follows from the code but was not
traced end to end.

An `Xvm` is the JIT's runtime. Today every `JitConnector` makes one (`javajit/JitConnector.java:35-39`).
A *run* below is one entry-method call in its own main container.

### A.0 Section 5, checked

| Section 5 says | Finding |
| --- | --- |
| `JitControl` throws, `useJit()` returns false | Confirmed (`api/JitControl.java:17-60`, `api/EmbeddingSupport.java:240-242`). In addition, `JitControl.create` takes no entry point, console input or debugger (`api/JitControl.java:21-23`, called at `api/EmbeddingSupport.java:1420-1421`), so a real one needs a new signature. |
| No non-final statics in `org.xvm.javajit`; its static finals are immutable | Confirmed by `javap -p` (2.1) and a source grep. One static final is a `HashMap` that a static initializer fills and nothing writes afterward (`javajit/NativeNames.java:21-23`). |
| Context passes explicitly through `Ctx`, held per `Xvm` | Confirmed for calls (`javajit/Ctx.java:31-39`, `javajit/Xvm.java:190`). Two exceptions: a generated `<clinit>` takes the `Ctx` of whichever run first initializes the class (`javajit/builders/CommonBuilder.java:959-967`, `javajit/ModuleLoader.java:135-137`), and JIT names are cached on the asm constants, not in the `Xvm` (A.1.3). |
| The bridge has 14 non-final statics, per `Xvm` | Confirmed: 12 `$INSTANCE` fields (`javatools_jitbridge/src/main/java/org/xtclang/ecstasy/Ordered.java:49-63`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/numbers/FPNumber.java:483-511`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java:113-134`), `NanosTimer.TIMER` and `RTRandom.THREAD_LOCAL`. They are per `Xvm` only while the bridge stays off javatools' class path (A.1.2). Section 5 misses that the bridge's console reaches JVM-wide state: `TerminalConsole` prints and reads through the interpreter's static `xTerminalConsole.CONSOLE_OUT`, `CONSOLE_IN` and `CONSOLE` (`javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:15`, `:44-48`, `:62-70`; `runtime/template/_native/io/xTerminalConsole.java:224-237`). Every `Xvm` and the interpreter share the streams that class captured when it was initialized. |
| `RTRandom.THREAD_LOCAL` is one random number generator per `Xvm` | One object per `Xvm`, but without a seed it draws from `ThreadLocalRandom.current()` on each call (`javatools_jitbridge/src/main/java/org/xtclang/_native/numbers/RTRandom.java:25-43`), so the values are per thread. Its lazy initialization is double-checked on a non-volatile field (`:219-233`); the object's only field is final, so the publication is safe (*inferred* from final-field semantics). |
| `NanosTimer.TIMER` is never cancelled | Confirmed: nothing calls `cancel()` on it. Also: the thread starts when `NanosTimer` is initialized, so only an `Xvm` whose program injects a timer has one (*inferred*); every `Xvm`'s thread has the same name, `ecstasy:NanosTimer` (`javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java:63`); each pending task holds the scheduling run's `Ctx` (`:383-388`); and the JDK ends a `Timer`'s thread on its own only through a `Cleaner` once the `Timer` is unreachable (JDK `java.util.Timer`, `Timer.java:183-184`), which depends on the collector. No alarm runs its function: it prints a TODO to `System.err` (`:244-248`). |
| Each `TypeSystemLoader`'s parent is the application loader | Its parent is javatools' own loader, `TypeSystemLoader.class.getClassLoader()` (`javajit/TypeSystemLoader.java:30`). That is the application loader only when javatools is on the application class path, which is not the case inside an IDE plugin, for example. |
| `invoke0Impl` deletes and recreates `./jasm/` after every run, and prints unhandled exceptions to `System.out` | Confirmed (`javajit/JitConnector.java:162-184`, `:150-154`). It does this in a `finally`, whether or not the run succeeded, and dumps only classes whose names contain `¤module`, plus a class a `VerifyError` names (`:91`, `:156-158`, `:236-238`). Stack traces and I/O errors go to `System.err` (`:137`, `:159`, `:177`). |
| No shutdown | Confirmed: `JitConnector` inherits the no-op `Connector.shutdown()` (`api/Connector.java:105-111`), and `Xvm` has no `close`. |
| No debugger | Confirmed (`api/EmbeddingSupport.java:1397-1399`). `xec` also refuses `--debug` with `--jit` (`tool/Runner.java:314-317`). |
| Parent-first loading would make the bridge statics JVM-wide (*inferred*) | Plausible, but not the first consequence. The plugin keeps the bridge off its launcher class path because loading it through the application loader "bypasses augmentation and omits generated methods" (`plugin/src/main/java/org/xtclang/plugin/XtcJavaToolsRuntime.java:124-127`, tested at `plugin/src/test/java/org/xtclang/plugin/XtcLauncherRuntimeTest.java:90-101`). |
| Not in section 5 | (1) The JIT writes into the module it is given (A.1.3). (2) Its class loaders are not parallel-capable and call each other's `findClass` directly, so concurrent runs in one `Xvm` can race to define one class (A.1.1). (3) A JIT run executes on the calling thread, and `join()` does not wait (`javajit/JitConnector.java:82-84`, `:205-208`; `javajit/Container.java:162-164`). (4) The entry method's name is ignored: the connector always calls `run` or `run$p` (`javajit/JitConnector.java:96-97`). |

### A.1 What "parallel" means for the JIT

#### A.1.1 Concurrent runs in one `Xvm`

No host can do this today. Every `JitConnector` makes its own `Xvm` and keeps one module, type
system, container and result (`javajit/JitConnector.java:35-39`, `:213-233`). The question is what
happens once a `JitControl` puts several runs on one `Xvm`, the way the interpreter puts several runs
on one runtime (1.1).

What a run would own, and what it would share:

| State | Scope | Evidence |
| --- | --- | --- |
| Core modules, their type system and generated classes | The `Xvm`: copied and linked when it is built | `javajit/NativeTypeSystem.java:96-131`, `asm/FileStructure.java:905-928` |
| A module's type system and generated classes | Every run of an identical module: refined, same name, same digest | `javajit/Xvm.java:371-395`, `:498-502` |
| Static property values and container-scoped singletons | The run's container | `javajit/Container.java:88`, `:175-209`; `javajit/builders/CommonBuilder.java:826-831`, `:877-880` |
| Module and package singletons, and consts that hold no service | The type system, so every run of one module | `javajit/Builder.java:899-918` |
| `Ctx` and the `ScopedValue` binding | The run's thread | `javajit/Container.java:162-164`, `javajit/Xvm.java:190` |
| Service calls | No scheduling: `callLater` runs its function at once, on the caller's thread, and other service calls are plain Java calls (*inferred*) | `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nService.java:28-39` |

The registries, counters and locks:

| Structure | Guard | Under concurrent runs |
| --- | --- | --- |
| Containers by id | `ConcurrentHashMap`, `AtomicLong` ids (`javajit/Xvm.java:110`, `:115`, `:321-339`) | Safe. Entries are weak and swept every 1,024 creations (`:332-336`), so nothing says when a run's container is gone. |
| Type systems by name | Lookup and creation under a lock striped by module names (`:371`, `:713-715`). Name generation is check-then-act (`:428-456`), and `register` throws on a duplicate (`:403-408`) | Two type systems with the same first owned module but different module sets, created at once, can get the same name; the second throws `IllegalStateException` (*inferred*). Not reachable while each run's type system owns one module (`javajit/JitConnector.java:48`). |
| Module loaders and the package index | Writes under `synchronized (moduleLoaders)` (`:535`, `:576`). `ensureTypeSystem` reads without that lock (`:373-389`), and the index arrays change in place (`:562`, `:603`) | A stale read finds no loader or an empty slot. Readers re-check for null and for the module name, so they miss rather than find a wrong loader (*inferred*). |
| Sweep counters | Plain `int`s, documented as racy (`:124-128`, `:136-141`) | Harmless. |
| Name counters | Atomic `compute` (`:293-296`) | Safe. Each name, however, is cached on the constant it names (A.1.3), and its suffix depends on the order in which runs generate classes, so JIT names differ from one run to the next (*inferred*). |
| `TypeConstant.s_setRecursions` | None: a static `HashSet` (`asm/constants/TypeConstant.java:6189`, `:8656-8660`) | Unsafe and JVM-wide, shared with compiles and the interpreter (1.3). Stage 1 of section 4 fixes it. |
| The working pool | Thread-local. Class generation opens no pool scope; only `javajit/JitConnector.java:65` and `javajit/NativeTypeSystem.java:117` do, and `xec` opens the module's pool around the run (`tool/Runner.java:268-269`) | A run on a thread of its own must open it too (*inferred* that generation needs a current pool). |

**Two runs loading or generating the same class at once.** This is the defect that concurrent runs
would hit first.

- Neither loader calls `registerAsParallelCapable()`, so `ClassLoader.loadClass` locks the whole
  loader (JDK `ClassLoader`, "parallel capable class loaders"). Neither overrides `loadClass`, so
  delegation is parent-first: a `ModuleLoader` asks its `TypeSystemLoader`, which asks javatools'
  loader, and only then calls `findClass` (`javajit/ModuleLoader.java:46-47`,
  `javajit/TypeSystemLoader.java:29-30`).
- The loaders call each other's `findClass` directly, without the other loader's lock.
  `TypeSystemLoader.findClass` calls the owning `ModuleLoader.findClass` for owned and shared modules
  alike (`javajit/TypeSystemLoader.java:65-81`), and `ModuleLoader.findClass` calls
  `TypeSystemLoader.findClass` for names outside its package (`javajit/ModuleLoader.java:104-105`).
- The core module's loader is shared into every type system (`javajit/Xvm.java:199-201`). Two runs of
  different modules that first need the same core class therefore enter `findClass` of one
  `ModuleLoader` under two different `TypeSystemLoader` locks. Both find no loaded class
  (`javajit/ModuleLoader.java:90`), both generate it (`:97`), both call `defineClass` (`:101`), and
  the second gets a `LinkageError` for a duplicate definition (JVMS 5.3.5). Both also write the
  unsynchronized `loadedClasses` map (`:102`, `:221`). All of this is *inferred*.
- Inside one type system, resolution from a generated class takes the `ModuleLoader`'s lock and then
  the `TypeSystemLoader`'s. A host call of `TypeSystemLoader.loadClass` takes the
  `TypeSystemLoader`'s lock and then defines a class in the `ModuleLoader`, which can make the JVM
  load its superclass through that `ModuleLoader`. The two orders can deadlock. The JDK's
  `ClassLoader` documentation warns of this for loaders whose delegation is not strictly
  hierarchical (*inferred*).

Two runs of the same module share one type system and its generated classes, which is the design
(`javajit/TypeSystem.java:100-104`). The races above apply to those classes as well.

**Verdict.** An `Xvm` is safe for one run at a time. Concurrent runs need parallel-capable loaders
that define each class under a per-name lock (task 3), atomic type-system naming (task 4), a private
copy of each run's module (task 2), and explicit removal of a run's container (task 6).

#### A.1.2 Several `Xvm`s in one JVM

Each `Xvm` has its own:

- JIT state: `org.xvm.javajit` has no statics to share, and the registries are fields (A.1.1);
- core modules: loaded from the repository, copied into a new `FileStructure` and linked, which also
  copies their dependencies (`javajit/NativeTypeSystem.java:96-111`; `asm/FileStructure.java:171-181`,
  `:905-928`);
- bridge classes: read as bytes through its own `URLClassLoader` and defined by its own
  `ModuleLoader` (`javajit/NativeTypeSystem.java:76`, `:171-221`), so their statics are its own.

Shared between `Xvm`s:

| Shared | Evidence | Effect |
| --- | --- | --- |
| The process console | `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:44-48`, `:62-70` | Output from all runs interleaves on one stream, and a line of input goes to whichever run reads first |
| `./jasm/` in the working directory | `javajit/JitConnector.java:162-184` | One `Xvm`'s run deletes another's dump, possibly while it is written. The walk can then throw an `UncheckedIOException`, which `catch (IOException)` does not catch, and which replaces the run's own outcome (*inferred*) |
| `System.err` | A.4 | Diagnostics interleave |
| The module given to a run, when hosts share a repository instance | A.1.3 | Both `Xvm`s write into it |
| `TypeConstant.s_setRecursions` | `asm/constants/TypeConstant.java:8656-8660` | Unsynchronized writes |

**Class-loader isolation, and the parent-first risk.** Generated classes cannot leak: no loader
outside an `Xvm` can produce them. Bridge classes can, because they exist as class files. If
`org/xtclang/...` classes are visible to javatools' loader, the parent-first delegation of A.1.1
lets that loader define them:

- an augmented class without the Ecstasy methods the JIT adds to it
  (`javajit/NativeTypeSystem.java:179-215`), which is the failure the plugin's comment describes;
- a "no-modification" class as it is (`javajit/NativeTypeSystem.java:179-183`), with statics that
  every `Xvm` then shares (*inferred*);
- and either kind then fails to resolve any generated class it refers to, since javatools' loader
  cannot see generated classes (*inferred*).

The bridge's `URLClassLoader` delegates to the system class loader (JDK `URLClassLoader(URL[])`), so
even its resource lookups prefer a copy on the class path (`javajit/NativeTypeSystem.java:76`, `:174`).

Where the bridge stands today:

- **XDK.** It ships in `javatools/` from a configuration of its own, while the launch scripts' class
  path comes from `xdkJavaTools` (`xdk/build.gradle.kts:50-58`, `:151`, `:377-382`).
- **Gradle plugin.** Filtered out of the launcher class path, with a test (above).
- **Development tree.** Found by replacing `javatools` with `javatools_jitbridge` in javatools' class
  directory (`javajit/NativeTypeSystem.java:72-74`). `String.replace` replaces every occurrence, so a
  checkout whose path contains `javatools` elsewhere gets a wrong location.
- **Other embedders** (the language server, an IDE plugin, a test JVM): nothing checks.

**Verdict.** Two `Xvm`s are isolated in their Java state. They are not isolated in output, in the
working directory, in a module both are given, or in one asm static.

#### A.1.3 A JIT run alongside concurrent compiles

The interpreter does not share the compiler's structures (1.3). The JIT does: it writes into the
module it is given.

| Write | Where | What it changes |
| --- | --- | --- |
| Synthetic children | `javajit/Linker.java:331` | Creates the method structures of the `Const` interface, with their code, in every `const` and `enum` class of the module (`asm/ClassStructure.java:3123-3139`, `:3320-3326`) |
| The core module swap | `javajit/Linker.java:376-380` | Removes the module's own `ecstasy.xtclang.org` from its `FileStructure`, adds the `Xvm`'s, and sets the pool's naked-ref type. `addChild` and `removeChild` check mutability and mark the file modified (`asm/FileStructure.java:1159`, `:1175`, `:1183`) |
| JIT names | `asm/constants/SignatureConstant.java:723-738`, `asm/constants/MethodConstant.java:294-333`, `asm/constants/PropertyConstant.java:315-336`, `asm/constants/TypeConstant.java:7399-7411` | Caches a name on the constant, with a suffix from one `Xvm`'s counter. Nothing clears it (`asm/Constant.java:425-427` resets only the reference count), and `adoptedBy` copies it into every clone (`asm/Constant.java:310-320`) |
| JIT method descriptors | `asm/constants/PropertyInfo.java:1454-1457` | Caches a descriptor built for one type system on a `TypeInfo` |
| New constants | `javajit/JitConnector.java:105`, and the builders | Registers constants in the module's pool. Registration is synchronized; its readers are not (`doc/parallel-compiler.md`, 3.2) |

What the JIT is given:

- `xec` gives it a copy, because the runner's repository is a read-through `LinkedRepository` over a
  `BuildRepository` (`tool/Launcher.java:695-707`, `asm/LinkedRepository.java:97-119`).
- `EmbeddingSupport` would give it the host's own instance. `runMethod` builds a `LinkedRepository`
  without read-through (`api/EmbeddingSupport.java:1405-1408`, `asm/LinkedRepository.java:25-27`),
  `run(ModuleStructure, ...)` wraps the host's module (`api/EmbeddingSupport.java:1282-1290`), and a
  `DirRepository` hands out one cached instance per file (`asm/DirRepository.java:84-88`).

The consequences, all *inferred*:

- a compile or another run that reads the module sees structural changes while it reads;
- the module's file now holds one `Xvm`'s core module, so a second link of the same instance, in the
  same or another `Xvm`, trips `assert child.getParent() == this` (`asm/FileStructure.java:1182`).
  The launch scripts and the tests run with `-ea` (`xdk/build.gradle.kts:154`;
  `build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts:111-118`,
  `:190-192`), and `aStoredModuleRunsByNameMoreThanOnce` is that case for the interpreter
  (`javatools/src/test/java/org/xvm/api/EmbeddingSupportTest.java:161`);
- a second `Xvm` reuses the names the first cached, while its own counter can give the same suffix to
  another signature;
- once the compiler document's stage 2 freezes the shared sources, the swap throws
  `IllegalStateException` (`asm/XvmStructure.java:177-181`).

**The interpreter's copy is not read-only either.** `InterpreterControl.prepareModule` serializes the
host's file (`api/InterpreterControl.java:162`). `writeTo` re-registers every constant of the host's
pool, optimizes it, and resets the file's modified flag (`asm/FileStructure.java:348-353`,
`:1345-1352`). Optimizing sorts and refills the constant list, renumbers the positions, and clears
the lookup maps and the implicit-import cache (`asm/ConstantPool.java:2786-2806`, `:3489-3695`). The
module means the same afterward, but a compile reading that pool at the same moment can see it half
rebuilt (*inferred*). The copy constructor, `new FileStructure(module, fSynthesize)`, only reads its
source (`asm/FileStructure.java:171-181`; `doc/parallel-compiler.md`, 2.3). The JIT should copy
that way (task 2), and section 4 may want the same for the interpreter.

**What is already safe.** Building an `Xvm` reads the core modules through the repository's
thread-safe lazy loading (`doc/parallel-compiler.md`, 3.2), copies them, and sets its working pool
inside a `withPool` scope (`javajit/NativeTypeSystem.java:116-123`). Unlike an interpreter boot, it
leaves the caller's working pool alone (1.3).

### A.2 What `EmbeddingSupport` does with the interpreter, and what the JIT must also do

**How `xec --jit` drives the JIT today.** `Runner.createBaseConnector` makes a `JitConnector`, which
builds an `Xvm` at once (`tool/Runner.java:490-493`, `javajit/JitConnector.java:35-39`). `loadModule`
loads the module from the connector's repository and links a type system around it
(`javajit/JitConnector.java:42-50`). `start` loads `nMainInjector` from the bridge and creates the
main container (`:58-74`). `execute` opens the module's pool, finds the method, checks the arguments
and calls `invoke0`, which runs the program to its end on the calling thread; `join()` then returns
the stored result (`tool/Runner.java:264-297`; `javajit/JitConnector.java:82-84`, `:205-208`).
Nothing shuts down: the process exits (`tool/Launcher.java:173-175`). The test runner uses the same
connector for the xunit module (`tool/TestRunner.java:89-111`).

**Runs, entry points and arguments.**

- *Interpreter.* `runMethod` loads the module (`api/EmbeddingSupport.java:1405-1408`).
  `InterpreterControl.create` copies and links it (`api/InterpreterControl.java:157-175`), selects
  the entry method as the command line does (`:89`; `api/EmbeddingSupport.java:1450-1488`), warns of
  dropped arguments (`api/InterpreterControl.java:96-99`), passes them as `passesArguments` decides
  (`api/EmbeddingSupport.java:1497-1500`), starts the task without waiting, and returns a `Control`
  that the task's completion updates (`api/InterpreterControl.java:134-150`, `:207-221`).
- *The JIT has* type-system linking, a main container, a fiber binding, a reflective call of the
  module's `run`, construction of the `String[]` argument (`javajit/JitConnector.java:110-126`), and an
  exit code from an `Int` return (`:96-97`, `:132-135`).
- *The JIT lacks* a copy of the module (A.1.3); the entry method's name, since it calls `run` or
  `run$p` whatever was asked (`:96-97`); `passesArguments`, since it passes an array whenever the method
  has a parameter (`:101`); any parameter type but exactly `String[]`, which an assertion demands
  (`:107-108`); an asynchronous start; the `Control` methods; and per-run state, since the connector
  holds one run's fields (`:213-233`). `EmbeddingSupport.entryMethod` also refuses a module whose file is
  not linked (`api/EmbeddingSupport.java:1451-1453`), and the JIT's link never marks it so
  (`javajit/Linker.java:363-387`).
- *To build:* task 6.

**Consoles and injection.**

- *Interpreter.* The host's `PrintWriter` and `ConsoleInput` are registered as an external console with
  a JVM-unique id (`api/InterpreterControl.java:101-104`) and unregistered when the run finishes
  (`:218`); `registeredConsoles()` counts them (`api/EmbeddingSupport.java:279-283`). Both backends
  refuse custom injectors and string injections (`api/EmbeddingSupport.java:1328-1330`, `:1414-1416`).
- *The JIT has* an injector API of suppliers (`javajit/Injector.java:23-95`); one injector per
  container (`javajit/JitConnector.java:60-68`); `nMainInjector`, which supplies the console, random
  numbers, clocks and a timer (`javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java:80-98`);
  and command-line string injections through `nCommandLineInjector` (`:35-38`, `:54-66`).
- *The JIT lacks* a console of the run's own: its console is the process terminal (A.0). It has no file
  system, so `rootDir` means nothing; a program that injects storage fails with "Unknown resource"
  (`javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java:69-74`). It has no
  child containers, which a custom injector needs (`javajit/Container.java:150`).
- *To build:* task 8. Because the run's injector holds its console, the JIT needs no console registry,
  and `registeredConsoles()` stays 0 by construction. Custom injectors stay refused until child
  containers exist.

**`footprint()`, `poolThreads()` and shutdown.**

- `footprint()` reads the configured repository, a compilation's pool and the heap, and touches no
  runtime (`api/EmbeddingSupport.java:190-206`). It needs no change. It says nothing about the JIT,
  whose memory is mostly generated classes in metaspace, which `totalMemory() - freeMemory()` does not
  count (`:203-205`). *To build:* a separate JIT diagnostic, task 10.
- `poolThreads()` reports the interpreter's fixed pool and 0 otherwise (`:293-297`). The JIT has no
  pool, and with task 6 it has one thread per active run, not a pool. It stays 0; the diagnostic
  reports live runs.
- *Shutdown, interpreter.* `shutdownInterpreter()` calls `connector.shutdown()` under the static lock
  and drops the connector (`:263-270`). `InterpreterConnector.shutdown()` cancels the native
  container's timer alarms and shuts the runtime's executors down
  (`api/InterpreterConnector.java:184-194`). *The JIT has* the inherited no-op
  (`api/Connector.java:105-111`). *To build:* tasks 9 and 10.

**The debugger.**

- *Interpreter.* A run's debugger is set on the runtime before the task starts and replaced when it
  ends or is closed (`api/InterpreterControl.java:127-132`, `:207-221`, `:264-268`).
- *The JIT has* nothing. `EmbeddingSupport` and `xec` refuse (`api/EmbeddingSupport.java:1397-1399`,
  `tool/Runner.java:314-317`), and the frozen-clock hooks a debugger would need are stubs
  (`javajit/Container.java:137-148`).
- *To build:* nothing in this plan. The refusal stays (A.6).

### A.3 Lifecycle

Every resource an `Xvm` creates:

| Resource | Created | Released today | What `close()` does |
| --- | --- | --- | --- |
| Core type system: copied core modules, `TypeSystemLoader`, `ModuleLoader`s for `ecstasy`, `mack`, `_native` and the libraries `_native` imports (`javatools_bridge/src/main/x/_native.x:9-12`), their generated classes | `javajit/Xvm.java:47`; `javajit/NativeTypeSystem.java:92-131` | By the collector, once unreachable | Clears the registries (step 5). The loaders hold no operating-system resource |
| Bridge `URLClassLoader` | `javajit/NativeTypeSystem.java:76` | Never closed. With a jar, it holds the jar open (*inferred* from `URLClassLoader`) | Closes it last (step 6): class generation reads from it (`:174`) |
| Class-hierarchy resolvers and their caches | `javajit/NativeTypeSystem.java:80`, `javajit/TypeSystem.java:361-366` | Collector | Nothing |
| Native container | `javajit/Xvm.java:50` | Collector | Nothing |
| Registries: containers, type systems, module loaders, package index, name counters | `javajit/Xvm.java:110-167` | Weak entries, swept every 1,024 or 256 creations (`:332-336`, `:433-435`, `:531-533`) | Clears them (step 5) |
| A run's type system, loaders and generated classes | `javajit/JitConnector.java:48`; `javajit/Xvm.java:392-393`; `javajit/TypeSystemLoader.java:45-47` | Collector; the registry keeps them for reuse | Step 5 |
| A run's container and its static values | `javajit/JitConnector.java:68`; `javajit/Container.java:88` | A weak registry entry | Removed when the run closes (task 6), so none is left at step 5 |
| A run's injector | `javajit/JitConnector.java:60-64` | With the container | Nothing |
| `Ctx` and the `ScopedValue` binding | `javajit/Container.java:162-164` | When the run returns | Nothing |
| A run's thread | None: a run uses the caller's thread (`javajit/JitConnector.java:82-84`) | Not applicable | After task 6, step 2 ends them and step 4 shuts the executor down |
| Timer thread `ecstasy:NanosTimer` | `NanosTimer`'s initialization (`javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java:63`) | Never; the JDK's `Cleaner` after collection (A.0) | After task 5, step 3 shuts the `Xvm`'s scheduler down |
| Pending alarms, each holding a `Ctx` | `javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java:255-264`, `:383-388` | When due, or cancelled by the program | Discarded at step 3 |
| Bridge singletons (`$INSTANCE`, `RTRandom`) | Class initialization in the `Xvm`'s loaders | With the loaders | Nothing: only the `Xvm`'s loaders reach them |
| `./jasm/` | Every run (`javajit/JitConnector.java:162-184`) | Never; the next run deletes it | Not created (task 7) |
| Writes into the module a run was given | `javajit/Linker.java:331`, `:376-380`; the name caches (A.1.3) | Never | Not made (task 2) |
| `xTerminalConsole`'s captured streams | First console use (`javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:44`) | Never: JVM-wide | Not the `Xvm`'s; runs given a console do not touch them (task 8) |

**The order of `close()`.** It is idempotent, and a second caller waits for the first, as
`InterpreterControl.close()` does (`api/InterpreterControl.java:255-261`).

1. Mark the `Xvm` closing. Creating a type system, a container or a run then throws
   `IllegalStateException`.
2. Close every live run (task 6) and wait for its thread, with a bound only against hangs.
3. Shut the scheduler down (`shutdownNow`) and await its termination, so that no alarm is left to
   call code whose loader is about to be closed.
4. Shut the run executor down and await its termination.
5. Clear the registries.
6. Close the bridge `URLClassLoader`. No run is left that could generate a class.
7. Mark the `Xvm` closed.

**Proving that a closed `Xvm` holds nothing, without the collector.** No `System.gc()`, no
`WeakReference` waits, no figure taken after a collection. Each check is direct:

- **Threads.** The scheduler's thread is a platform thread named `xvm-<id>-timer` (task 5). After
  `close()`, no thread in `Thread.getAllStackTraces()` has the prefix `xvm-<id>-`, and the scheduler
  reports `isTerminated()`. That map lists platform threads only (JDK `Thread.getAllStackTraces`), so
  for run threads, which are virtual, the test keeps each run's `Thread` and joins it, and asserts that
  the run executor `isTerminated()`.
- **Loaders.** `Xvm.isClosed()` is true, and a lookup of a known bridge resource through the bridge
  loader returns null, as the JDK documents for a closed `URLClassLoader`.
- **File handles.** On Linux, no entry of `/proc/self/fd` resolves to the bridge jar the test gave
  the `Xvm`. Elsewhere this check reports as skipped, so it must run on a Linux CI host.
- **Registries.** Every registry of the `Xvm` reports zero entries.
- **Static reachability.** Read every static field of javatools' classes, as 6.2 does for
  `org.xvm.runtime`, and look one level into arrays, collections and maps. No value is an `Xvm`,
  `Container`, `TypeSystem`, `Ctx`, `ModuleLoader` or `TypeSystemLoader` of the closed `Xvm`, or an
  object whose class one of its loaders defined.
- **Context class loaders.** No live platform thread has one of the closed `Xvm`'s loaders as its
  context class loader.
- **Use after close.** A run, `createContainer` or `createLinker` on the closed `Xvm` throws
  `IllegalStateException` instead of working on half-released state.
- **The working directory.** It has no `jasm` entry.

### A.4 Process effects

| Effect | Where | Instead |
| --- | --- | --- |
| Deletes and recreates `./jasm/` in the working directory after every run | `javajit/JitConnector.java:162-184`; `javajit/ModuleLoader.java:141-194` | Off by default. An `Xvm` option names a directory; dumps go to `<dir>/xvm-<id>/run-<n>/`; nothing outside it is deleted; I/O errors go to the run's `ErrorListener` (task 7) |
| Unhandled Ecstasy exceptions printed to `System.out` | `javajit/JitConnector.java:150-154` | `ERR_UNHANDLED_EXCEPTION` to the run's `ErrorListener`, as `InterpreterControl.finish` does (`api/InterpreterControl.java:207-214`); `xec` prints what it receives (task 6) |
| Stack traces and messages on `System.err` | `javajit/JitConnector.java:137`, `:159`, `:177`; `javajit/ModuleLoader.java:190`; `javajit/TypeSystem.java:473`; `javajit/Ctx.java:162-164`; `javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java:247`; `asm/constants/TypeConstant.java:6189-6191` | An `Xvm` reporter that defaults to `System.err` for `xec` (task 8); the asm message stays process-wide |
| Console output and input on the process streams | `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:44-48`, `:62-70`; `runtime/template/_native/io/xTerminalConsole.java:224-237` | A console of the run's own, bound to the host's `PrintWriter` and `ConsoleInput`; `TerminalConsole` only when no console is given (task 8) |
| `readLine` echoes when asked not to, and the reverse; it throws `NullPointerException` without a system console | `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:67-71` | Swap the branches, and fall back to `CONSOLE_IN` when `CONSOLE` is null (task 8) |
| The working directory | `javajit/JitConnector.java:167`, for `./jasm/` only | As for `./jasm/` |
| The bridge's location, derived from javatools' code source | `javajit/NativeTypeSystem.java:65-74` | An explicit `Xvm` input, with today's probe as the default (task 1) |
| System properties | None read in `org.xvm.javajit` or the bridge | Keep it so: the dump option goes through the `Xvm`'s constructor, not a property |
| The JVM's default time zone | Read by `javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/LocalClock.java:27` | Nothing: a local clock is process-wide by nature |
| Shutdown hooks | None in `org.xvm.javajit` or the bridge | None needed: `close()` is explicit |
| `System.exit` | None in `org.xvm.javajit` or the bridge; `xec` exits in `tool/Launcher.java:173-175` | Keep it out of the JIT |

### A.5 Tasks

Sizes: S is up to a day, M two to three days, L four days or more. Effort is engineer-days for
the edits and their tests, without review time.

| No. | Task | Size | Effort | Risk | Depends on |
| --- | --- | --- | --- | --- | --- |
| 1 | Keep the bridge off the class path; make its location explicit | S | 1–1.5 d | low | none |
| 2 | Run a private copy of the module | S–M | 1.5–2 d | low–medium | none |
| 3 | Parallel-capable, child-first class loaders | M | 2–3 d | medium | none |
| 4 | Atomic type-system names | S | 0.5 d | low | none |
| 5 | An `Xvm`-owned scheduler instead of `NanosTimer.TIMER` | S–M | 1–1.5 d | low | 1 |
| 6 | One `Xvm` per connector, one `JitControl` per run | L | 4–6 d | medium | 2, 3, 4 |
| 7 | The class dump on request only | S | 0.5–1 d | low | 6 |
| 8 | A console of the run's own, and a reporter | M | 2–3 d | low–medium | 6 |
| 9 | `Xvm.close()` and `JitConnector.shutdown()` | M | 1.5–2 d | low | 5, 6 |
| 10 | Wire `EmbeddingSupport` to the JIT | S–M | 1–2 d | low | 6, 8, 9 |
| 11 | Test: two `Xvm`s running different programs at once | S–M | 1–1.5 d | low | 1, 2, 6, 8, 10 |
| 12 | Test: concurrent runs in one `Xvm` | M | 1.5–2 d | medium | 3, 4, 6, 8, 10 |
| 13 | Test: shutdown, then a new `Xvm` | S–M | 1–1.5 d | low | 1, 5, 9 |
| 14 | Test: the bridge jar stays off the application class path | S | 0.5–1 d | low | 1 |
| 15 | Stopping a JIT run (only if A.6 decides for it) | L | 5–8 d | high | 6 |

**Total: 19 to 28 engineer-days for tasks 1 to 14**, about a fifth of it the four tests. Tasks 6,
8 and 10 are needed to reach the JIT through `EmbeddingSupport` at all, parallel or not. Task 15 adds
5 to 8 days. The longest chain is 3, 6, 9 and 10, about 9 to 13 days; tasks 2 and 4 feed task 6.

The table gives each task's size, effort, risk and dependencies. The entries below give its files,
the change, what makes the risk, and the test that accepts it.

**1. Keep the bridge off the class path, and make its location explicit.**

- *Files.* `javajit/NativeTypeSystem.java` (constructor, `:62-83`), `javajit/Xvm.java` (constructor),
  `javatools/build.gradle.kts` (the test task).
- *Change.* `Xvm` takes the bridge's location as an argument; today's probe becomes the default, and
  it replaces only the build-directory segment, not every `javatools` in the path
  (`javajit/NativeTypeSystem.java:72-74`). The constructor refuses, with an `IllegalStateException`
  that says where it found it, when javatools' loader can see a bridge class, for example
  `org/xtclang/_native/temporal/NanosTimer.class`. The javatools test task receives the bridge jar
  through a resolvable configuration of the `jit-bridge-binary` variant
  (`javatools_jitbridge/build.gradle.kts:18-28`, consumed as `xdk/build.gradle.kts:50-58` does), passed
  as a system property by an argument provider with declared input files, so that it works with the
  configuration cache and never enters the test's runtime class path. An allowlist test pins the
  non-final statics: none in `org.xvm.javajit`, the 14 of A.0 in the bridge, shrinking as tasks
  remove them.
- *Risk.* Low. The uncertain part is the build wiring: the bridge depends on javatools
  (`javatools_jitbridge/build.gradle.kts:32-35`), and the composite build must not get a task cycle
  (*inferred* that it does not).
- *Accepted by* task 14 and the allowlist test.

**2. Run a private copy of the module.**

- *Files.* `javajit/JitConnector.java` (`loadModule`, `:42-50`), `javajit/Linker.java` (`:327-335`,
  `:363-387`), the adoption paths of `asm/constants/SignatureConstant.java`, `MethodConstant.java`,
  `PropertyConstant.java` and `TypeConstant.java`.
- *Change.* Copy the module with the copy constructor (`asm/FileStructure.java:171-181`), which only
  reads the source, and link the copy for the runtime (`asm/FileStructure.java:721-738`). Do not
  serialize the host's file, which rewrites its pool (A.1.3). Clear the four `m_sJitName` caches when a
  constant is adopted, so that a copy never carries another `Xvm`'s names (`asm/Constant.java:310-320`).
  Mark the copy linked once `Linker.link` has swapped in the core module, so that
  `EmbeddingSupport.entryMethod` accepts it (`api/EmbeddingSupport.java:1451-1453`).
- *Risk.* Low to medium: a runtime-linked copy followed by `Linker`'s core-module swap is a
  combination nothing runs today.
- *Accepted by* a new `javatools/src/test/java/org/xvm/api/JitModuleIsolationTest.java`. Run one
  module instance twice in one `Xvm`, then once in a second `Xvm`. Assert that the instance's file is
  not modified (`asm/XvmStructure.java:259`), that its child modules are the same objects as before
  the runs, that no constant of its pool carries a JIT name, and that the second link succeeds under
  `-ea`. Today that link is expected to trip the assertion at `asm/FileStructure.java:1182`
  (*inferred*).

**3. Parallel-capable, child-first class loaders.**

- *Files.* `javajit/ModuleLoader.java`, `javajit/TypeSystemLoader.java`.
- *Change.* Both call `registerAsParallelCapable()` in a static initializer. `ModuleLoader` overrides
  `loadClass(String, boolean)`. For a name under its own package it holds
  `getClassLoadingLock(name)`, checks `findLoadedClass`, and otherwise generates and defines the class,
  without asking a parent first. Any other name goes to its `TypeSystemLoader`.
  `TypeSystemLoader.findClass` calls the owning loader's `loadClass`, not its `findClass`
  (`javajit/TypeSystemLoader.java:65-81`), and `ModuleLoader` no longer calls
  `TypeSystemLoader.findClass` directly (`javajit/ModuleLoader.java:104-105`). `loadedClasses` becomes
  a concurrent map, or goes away with the dump (task 7). Child-first loading for owned packages also
  removes the parent-first risk of A.1.2 for every class the JIT generates or augments.
- *Risk.* Medium. Loader changes show up as `LinkageError` or `NoClassDefFoundError` deep in generated
  code, and the hierarchy resolver loads classes re-entrantly during generation
  (`javajit/TypeSystem.java:361-366`).
- *Accepted by* a new `javatools/src/test/java/org/xvm/javajit/JitClassLoadingTest.java`. In one `Xvm`,
  K type systems over K modules; K threads, released together by a `CyclicBarrier`, each load the
  same list of core class names, some through their `TypeSystemLoader` and some through the loader of
  a generated class. Assert no `LinkageError`, one `Class` object per name across all type systems,
  and no deadlock: while waiting, the test polls `ThreadMXBean.findDeadlockedThreads()`, and its joins
  are bounded only to stop a hang. Repeat for several rounds with fresh `Xvm`s, and also as task 12.

**4. Atomic type-system names.**

- *Files.* `javajit/Xvm.java` (`:403-408`, `:428-456`).
- *Change.* Reserve each candidate name with `putIfAbsent` while generating it, instead of checking
  and registering separately. Keep the weak sweep.
- *Risk.* Low.
- *Accepted by* a unit test that creates, at once, type systems whose sorted first owned module is the
  same and whose module sets differ, and asserts distinct names and no `IllegalStateException`.

**5. An `Xvm`-owned scheduler instead of `NanosTimer.TIMER`.**

- *Files.* `javajit/Xvm.java`; `javatools_jitbridge/src/main/java/org/xtclang/_native/temporal/NanosTimer.java`
  (`:63`, `:255-264`, `:380-415`).
- *Change.* `Xvm` gets a JVM-unique id from a static final counter, which is harmless for the reasons of
  4.5, and a lazily created `ScheduledThreadPoolExecutor` with one daemon platform thread named
  `xvm-<id>-timer`. `NanosTimer` schedules through `ctx.xvm`; both scheduling sites already have a
  `Ctx` (`:263`, `:402`). The static field goes, which brings the bridge to 13 non-final statics. A
  task that arrives after shutdown catches `RejectedExecutionException`. Code that ever runs alarm
  functions on that thread must bind the `Xvm`'s `ScopedValue` first, since `getCtx()` documents null
  but `ScopedValue.get()` throws when nothing is bound (`javajit/Xvm.java:298-305`).
- *Risk.* Low. Alarm functions still do not run (`:244-248`); that is separate work.
- *Accepted by* task 13, and the allowlist of task 1.

**6. One `Xvm` per connector, one `JitControl` per run.**

- *Files.* `javajit/JitConnector.java`, `api/JitControl.java`, `api/EmbeddingSupport.java:1420-1423`,
  `tool/Runner.java:264-297`, `:490-493`, `tool/TestRunner.java:89-111`.
- *Change.* The connector keeps only the `Xvm`; the per-run fields (`javajit/JitConnector.java:213-233`)
  move into `JitControl`, whose `create` takes the same inputs as `InterpreterControl.create`
  (`api/InterpreterControl.java:72-76`) except the debugger. A run:
  - copies its module (task 2) and links a type system;
  - selects the entry method with `EmbeddingSupport.entryMethod` and `passesArguments`
    (`api/EmbeddingSupport.java:1450-1500`), and warns of dropped arguments as
    `api/InterpreterControl.java:96-99` does;
  - finds the Java method from the entry method's JIT name and descriptor instead of `run` or `run$p`
    (`javajit/JitConnector.java:96-97`), passes the array only when `passesArguments` says so, and
    accepts any parameter type that `String[]` is assignable to (today `:101-108`);
  - runs on a virtual thread named `xvm-<id>-run-<n>` from the connector's executor, inside
    `withPool(ts.pool())` and `container.newFiber`;
  - reports its result as a `Long`, or as null with `ERR_UNHANDLED_EXCEPTION`
    (`api/InterpreterControl.java:207-214`);
  - implements `running`, `join`, `whenStarted`, `whenStopped`, `result` and `close`, and removes its
    container from the `Xvm`'s registry when it ends.

  `Runner` and `TestRunner` use the same code, so `xec --jit` and `EmbeddingSupport` share one
  implementation.
- *Risk.* Medium. Entry methods other than `run`, default parameters in JIT signatures (*inferred*
  that they add parameters, as `print$p(Ctx, Object, boolean, boolean)` shows in
  `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:36`), and what
  `close()` can do with a run that does not end (A.6).
- *Accepted by* `EmbeddingSupportTest.entryMethodSelectionFollowsTheCommandLineRunner`
  (`javatools/src/test/java/org/xvm/api/EmbeddingSupportTest.java:443`) and the other run tests of
  that class, run against both backends, and by task 12.

**7. The class dump on request only.**

- *Files.* `javajit/JitConnector.java:162-184`, `:236-238`; `javajit/ModuleLoader.java:141-194`;
  `javajit/TypeSystemLoader.java:85-89`; an `xec` option in `tool/Runner.java`.
- *Change.* Dump only when an `Xvm` option names a directory, into `<dir>/xvm-<id>/run-<n>/`, after
  the run has finished and reported, never deleting anything outside that directory, catching
  `UncheckedIOException`, and reporting failures to the run's `ErrorListener`. Dumping loads further
  classes (`javajit/ModuleLoader.java:144-146`), so it must stay out of the default path.
- *Risk.* Low. JIT developers lose an always-on dump; the option's name should be agreed with them.
- *Accepted by* tasks 11 and 12, which assert that the working directory has no `jasm` entry, and by a
  run of two concurrent dumps that leaves two complete trees.

**8. A console of the run's own, and a reporter.**

- *Files.* A new bridge class in `javatools_jitbridge/src/main/java/org/xtclang/_native/io/` for the
  run's console; `javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java:80-98`
  or a per-run injector; `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:54-76`;
  `javajit/Xvm.java`; the `System.err` sites of A.4.
- *Change.* The run's injector supplies a console bound to the host's `PrintWriter` and
  `ConsoleInput`. `TerminalConsole` stays for `xec` and for runs given no console, with its `readLine`
  fixed (A.4). The diagnostics go to an `Xvm` reporter that defaults to `System.err`.
- *Risk.* Low to medium: the new class must follow the bridge's conventions (`$xvmType`, the `$p`
  signatures).
- *Accepted by* tasks 11 and 12. They assert that each run's `PrintWriter` receives exactly its own
  lines, and that the `Xvm`'s bridge loader never loaded `TerminalConsole` in a run that was given a
  console.

**9. `Xvm.close()` and `JitConnector.shutdown()`.**

- *Files.* `javajit/Xvm.java`; `javajit/NativeTypeSystem.java` (the bridge loader, `:76`, `:137`, kept
  as a `URLClassLoader` so it can be closed); `javajit/JitConnector.java`.
- *Change.* `close()` in the order of A.3, idempotent, with `IllegalStateException` afterward.
  `JitConnector.shutdown()` calls it, which is what `Connector.shutdown()` promises
  (`api/Connector.java:105-111`).
- *Risk.* Low.
- *Accepted by* task 13.

**10. Wire `EmbeddingSupport` to the JIT.**

- *Files.* `api/EmbeddingSupport.java:240-242`, `:247-256`, `:263-297`, `:1397-1399`, `:1420-1423`;
  `api/JitControl.java`.
- *Change.* The backend comes from configuration (a constructor argument or a `configure` option), not
  from a constant. `runMethod` passes the entry point and console input to `JitControl`. The debugger
  refusal stays. A non-null `rootDir` is reported as unsupported, since the JIT injects no file
  system (A.6). `shutdownInterpreter()` becomes the `close()` of 4.6 and calls `connector.shutdown()`
  for either backend. `registeredConsoles()` and `poolThreads()` stay 0 for the JIT, as A.2 explains. A
  new `jitDiagnostics()` returns live runs, containers, type systems and module loaders, read from
  the `Xvm`'s registries. `footprint()` does not change.
- *Risk.* Low. Several instances at once need 4.6's per-instance lock (`api/EmbeddingSupport.java:124`);
  until then the tests use the package-private constructor (`api/EmbeddingSupport.java:115`).
- *Accepted by* the run tests of `EmbeddingSupportTest` against both backends, and by task 13
  through `EmbeddingSupport`.

**11. Test: two `Xvm`s running different programs at once.**

- *File.* A new `javatools/src/test/java/org/xvm/api/JitParallelTest.java`.
- *Setup.* Two JIT `EmbeddingSupport` instances over the same core repository. Module A and module B
  are compiled in the test: each prints a line with its name, builds strings from integers, throws
  and catches an exception, uses an array and a map, and returns a distinct exit code, K times. Both
  runs start from a `CyclicBarrier`, so that the two `Xvm`s also boot at the same time.
- *Assert.* Each run's console output is exact, and so is its result. Each core class a run used was
  defined by a `ModuleLoader` whose `getXvm()` is that run's `Xvm`. The bridge singletons differ: the
  `Ordered.Lesser.$INSTANCE` read through each `Xvm`'s loader is a different object. The host's
  modules are untouched, as task 2 checks. The working directory has no `jasm` entry.
- *Today.* The test cannot be written against `EmbeddingSupport`, whose JIT path throws. Written
  against `JitConnector`, the output assertions fail, because both programs print to the process
  console.

**12. Test: concurrent runs in one `Xvm`.**

- *File.* `javatools/src/test/java/org/xvm/api/JitParallelTest.java`.
- *Setup.* One JIT `EmbeddingSupport`, so one `Xvm`, and 16 runs from 8 host threads, for several
  rounds: 8 runs of module M, which share one type system, and 8 runs of distinct modules, which
  share only the core loaders. All start from a barrier, so that the first generation of the same
  core classes overlaps. The programs print, inject random numbers, a clock and a timer, throw and
  catch, and build strings, and M also prints from module-level code through an injected console
  (A.6).
- *Assert.* Each run's output and result are exact. No run reports a `LinkageError` or
  `NoClassDefFoundError`. No thread deadlocks (task 3's check). There is one `Class` object per core
  class name. M has one type system. After every run is closed, the `Xvm` has no live container and
  its run executor has no active thread.
- *Today.* Not possible through any API, since each connector makes its own `Xvm`. Driven through `Xvm`
  directly, it is expected to fail with a duplicate class definition (A.1.1, *inferred*).

**13. Test: shutdown, then a new `Xvm`.**

- *File.* A new `javatools/src/test/java/org/xvm/api/JitShutdownTest.java`, following
  `javatools/src/test/java/org/xvm/api/RuntimeRetentionTest.java:45-56`.
- *Setup.* `Xvm` A runs a program that schedules a day-long alarm and then ends; the test closes A,
  creates `Xvm` B and runs a program that schedules an alarm.
- *Assert on A.* Everything in A.3's proof list: no `xvm-<A>-` thread, the scheduler and the run
  executor terminated, the bridge loader closed, no open handle on the bridge jar, empty registries,
  nothing of A reachable from a javatools static or a thread's context class loader, and
  `IllegalStateException` on use. *Assert on B.* Its run completes, and its alarm is pending in B's
  scheduler, not A's.
- *Today.* Fails: the timer thread of A lives on, and `JitConnector.shutdown()` does nothing.

**14. Test: the bridge jar stays off the application class path.**

- *Files.* A new `javatools/src/test/java/org/xvm/javajit/JitClassPathTest.java`;
  `build-logic/common-plugins/src/test/kotlin/XdkLauncherScriptsTest.kt`.
- *Assert.* Neither javatools' loader nor the system class loader finds a resource under
  `org/xtclang/_native/` or `org/xtclang/ecstasy/`, and no `java.class.path` entry names the bridge.
  Every bridge class an `Xvm` uses is defined by one of that `Xvm`'s `ModuleLoader`s. The guard of
  task 1 refuses a loader that can see the bridge. The XDK's launch scripts put no bridge jar on
  their class path, which complements the plugin's test
  (`plugin/src/test/java/org/xtclang/plugin/XtcLauncherRuntimeTest.java:90-101`).
- *Today.* The class-path assertions pass, since nothing puts the bridge there; the guard assertion
  fails, since there is no guard.

**15. Stopping a JIT run** (only if A.6 decides for it).

- *Files.* The code generators (`javajit/builders/CommonBuilder.java`, `javajit/BuildContext.java`),
  `javajit/Container.java`, `javajit/Ctx.java`.
- *Change.* Generated code checks the container's termination flag at loop back-edges and method
  entries, and unwinds with a JIT-internal exception. `JitControl.close()` sets the flag and joins the
  run. Today generated code has no such check: there is no use of interruption anywhere in
  `org.xvm.javajit` or the bridge.
- *Size and risk.* L, 5 to 8 days (*estimate*), high: it adds work to hot paths and needs the
  benchmark discipline of 6.4.
- *Accepted by* a run in an endless loop that ends after `close()`, and a benchmark within 6.4's
  thresholds.

### A.6 Open questions

- **One `Xvm` for all runs, or one per run?** Sharing saves the core copy and the generated core
  classes for every run after the first. An `Xvm` per run releases everything at the run's end, at the
  cost of a boot per run. Nothing was measured.
- **How long a type system lives.** Runs of identical modules share a type system
  (`javajit/Xvm.java:371-395`), and classes go only when their loader is collected. A long-lived `Xvm`
  that runs many distinct modules, the language server's consecutive-runs case (1.5), therefore keeps
  growing in metaspace (*inferred*). It needs either a bound on cached type systems or a rule to
  replace the `Xvm`, like the worker's retirement criteria
  (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:39-41`, `:440-460`).
- **Stopping a run.** Without task 15, `close()` cannot stop a JIT run that computes without
  blocking; interrupting its thread helps only where it blocks, for example on console input. Is
  that acceptable for the hosts that would use the JIT, or does the JIT need task 15 first?
- **Shared singletons.** Module and package singletons are not container-scoped
  (`javajit/Builder.java:899-918`), and a `<clinit>` runs with the first run's `Ctx`
  (`javajit/builders/CommonBuilder.java:959-967`). Whether per-run state, such as an injected console,
  can end up in a singleton that the next run of the same module sees was not traced. The connector
  constructs its own module instance for the call (`javajit/JitConnector.java:95`), which does not
  settle it for code that reaches the module's `$INSTANCE`. Task 12 covers the case; it should be
  traced before task 6 lands.
- **The debugger.** None is planned. A JIT debugger needs either hooks in generated code, the
  interpreter's approach of a check per op (`runtime/ServiceContext.java:592`), or the JVM's own
  debugging interface. The frozen clock (`javajit/Container.java:137-148`) matters only then.
- **`rootDir` and storage.** The JIT injects no file system
  (`javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java:80-98`). Should
  `EmbeddingSupport` refuse a `rootDir` for the JIT, or ignore it with a warning?
- **Dependencies other than the core module.** The linker shares in only the core module's loader and
  links nothing else (`javajit/Xvm.java:199-201`, `javajit/Linker.java:371-373`). Whether runs of
  modules with other dependencies work is outside this appendix (*inferred* that they do not).
- **Virtual or platform threads for runs.** Virtual threads share the JDK's carrier threads across the
  JVM and are missing from `Thread.getAllStackTraces()`. Platform threads isolate runs better and are
  easier to check, at a higher cost per run.
- **Who uses the always-on dump?** Task 7 turns it off by default; its users should name the option.
- **The interpreter's copy.** `prepareModule`'s round trip rewrites the host's pool (A.1.3). Should
  section 4 switch it to the copy constructor as well?
