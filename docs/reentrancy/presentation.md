# Why the Runtime Cannot Be Reused, and What We Do About It

A 10-15 minute brief. Every claim below is backed by a committed test, a
file:line citation, or a reproducer on the `lagergren/lazy-instance` branch;
nothing here is opinion about style. The numbers are all re-measurable with
the grep commands recorded in the audit docs. Every "code:" link below
points at **unmodified master**, pinned at commit `61e555a68` (2026-08-25) -
click any of them live in the meeting.

Presenter jump targets:
[World X-Ray printouts](#appendix-world-x-ray-printouts) |
[WorldSnapshotDemoTest.java](../../javatools/src/test/java/org/xvm/runtime/WorldSnapshotDemoTest.java) |
[OwnershipDiagnostics.java](../../javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java) |
[ErrorList/BLACKHOLE findings](logging-diagnostics-audit.md#direct-errorlist-and-blackhole-findings) |
[Nullness findings](nullness-annotation-audit.md#strategic-finding-null-means-too-many-things)

---

## 0. Why this became blocking (1 min)

This is not the first attempt to work around the monolith. Over the years I
have tried to make the compiler/runtime usable from the Gradle plugins, from
tests, from same-JVM stress runs, and from the LSP. I have tried shapes such as
a Gradle-plugin compiler daemon to avoid paying full JVM startup and full
recompile cost for every module. The problem is always the same: the compiler
and runtime do not come apart into owned pieces that can be reused
incrementally. They behave like one blob that mutates process-global state,
thread-local state, static caches, and owner-bearing runtime state in ways the
host cannot inspect or reset.

This has not always been the highest priority because a one-shot command-line
compiler can hide the design problem by exiting the JVM. That escape hatch is
gone for the work we need now. A Gradle daemon, a fast test suite, a useful LSP,
an integrated debugger, and same-JVM module stress all need to run more than
one compilation/container/request inside the same process. Today, if we try
that, stale state is everywhere: old pools, old native templates, old static
handles, old diagnostics, old JIT statics, and old runtime metadata can survive
into the next run.

The upside is large. If the XDK build can reuse one resident compiler/runtime
process instead of starting a new JVM and rebuilding state for every module, the
resource and latency savings are potentially massive - plausibly moving an
entire XDK compile toward tens of seconds instead of repeated cold starts. But
there is no credible path to that with hidden ownership. We need explicit
owners: compiler request, module/pool, container, frame, service, diagnostic
session. `getCurrentPool()` and similar ambient state make that impossible and
turn reuse into nondeterminism.

So the claim is not "I would prefer a cleaner architecture." The claim is "I am
not getting much further without separating ownership from process lifetime."

---

## 1. The framing: I asked for three ordinary things (1 min)

I did not set out to redesign anything. I wanted:

1. **Run the compiler/runtime more than once in the same JVM** (test suites,
   build daemons, REPL-style workflows).
2. **Run containers concurrently** (the thing the language literally
   advertises).
3. **LSP-grade performance** (incremental compilation needs a resident,
   reusable compiler - you cannot fork a JVM per keystroke).

All three failed, and they failed for the *same* root cause: **the codebase
assumes one world per process, forever.** There is no API seam anywhere that
says "this is a world, and here is its boundary." State that belongs to a
compilation or a container lives in statics, thread-locals, public fields,
and raw shared arrays. That is not a threading bug - it is the absence of a
design provision, and it bites **even single-threaded**: the second
sequential run in one JVM inherits the first run's world.

This is not optional infrastructure for the LSP or compiler:

- A real LSP is a resident server. It must keep parsed modules, type info,
  diagnostics, semantic tokens, completion state, and open-document overlays in
  memory while the editor sends more requests. Forking a JVM per keystroke is
  not an LSP architecture; it throws away the cache that makes LSP useful.
- A working integrated debugger has the same requirement. Breakpoints,
  evaluation, hover inspection, stepping, and watch expressions all need to
  inspect or run code inside the existing world, often while the compiler and
  runtime answer other requests. If `toString()` warms caches and the runtime
  has no world boundary, the debugger changes the program it is trying to
  inspect.
- The compiler needs both sequential and parallel same-JVM execution. Build
  daemons, test suites, incremental compiles, and IDE requests all reuse one
  process. Parallel validation/type-checking is the normal way to make large
  projects responsive; request-local compiler state has to be owned by a
  request, not by the process or a reused Java thread.

> The ask at the end is not "rewrite it." We have a staged, test-backed plan
> where most of the fixes are hunk-sized. But first, the evidence.

---

## 2. One world per process: the global-state census (2 min)

What we found when we tried to make run #2 work:

- **Native templates published themselves from their constructors into
  process-wide statics** (`INSTANCE = this` while the object was still under
  construction). Last writer wins; the second container silently gets the
  first container's template state.
  - code: [xObject.java:23 - `INSTANCE = this` in the constructor](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/xObject.java#L23)
- **The JIT bakes the first world into class initializers.** Generated
  `<clinit>` code reads the ambient `Ctx.get()` and writes constants,
  injected values, and singletons into **classloader-wide static fields**.
  The first active container becomes *permanent state* for every later one.
  - code: [jitbridge Array.java:160 - `static final $INSTANCE = new eMutability(Ctx.get())`](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java#L160)
- **A single static `java.util.Timer` serviced all containers**, and its
  callback registry was a plain `HashMap` mutated from both the service
  thread and the Timer thread - one race and the shared Timer thread dies,
  which kills alarms **for every container in the JVM**. (Reachable in any
  timer-using program; fixed + test: `NativeCallbackRegistrationTest`.)
  - code: [xLocalClock.java:282 - `public static Timer TIMER` (not even final)](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java#L282)
- The ambient `ConstantPool` travels in a **ThreadLocal**, compiler counters
  are global statics, and the `Xvm` bootstrap wrote six public mutable
  fields *while booting*, visible half-initialized to any observer.
  - code: [ConstantPool.java:4010 - the ThreadLocal pool](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/ConstantPool.java#L4010) ; [Xvm.java:78-105 - the public world fields](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/javajit/Xvm.java#L78)

**The pedagogical point:** none of this is exotic concurrency. It is the
absence of an owner parameter. Everything that should have been
"container.thing" or "compilation.thing" is "the process's thing."

---

## 3. No abstraction seams - even where it costs nothing (1.5 min)

The deeper problem than any single global: **there is no interface between
the runtime and its world.** Pools, containers, templates, and heaps reach
each other through statics and public fields. Consequences:

- You cannot substitute a slow-but-obviously-correct implementation to test
  against (no seam to substitute at).
- You cannot thread a logger, a clock, or a diagnostics sink through -
  there is no parameter to thread it through. The compiler's idea of a
  diagnostic today is literally:

  ```java
  System.err.println("No conversion found ...");   // inside the compiler
  ```
  code: [ConvertExpression.java:95](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java#L95)

- You cannot upgrade an implementation later, because callers depend on the
  concrete representation - which brings us to the arrays.

---

## 4. Arrays everywhere: the representation with no contract (2.5 min)

The census (all in `array-element-exposure-audit.md`):
**105 public/protected array fields, 358 array-returning methods, 21 of them
returning the raw internal array, 12 constructor families retaining the
caller's array without a copy.**

A Java array has every wrong property for a public position: always mutable,
covariant, and *incapable of stating a contract*. So every contract lives in
a comment or a convention - and we can measure what conventions are worth,
because the codebase broke its own, twice, in ways that corrupt user data:

The important representation fact is this: arrays are not just temporary Java
helpers here. They are the runtime object representation (`GenericHandle`
field arrays), the register file (`Frame.f_ahVar`), tuple storage
(`TupleHandle.m_ahValue`), native array delegate storage, method signatures,
annotations, parameter lists, op streams, and constant children. In other
words, `ObjectHandle[]` and `Constant[]` often mean "live owner-bearing state",
but the type says only "array". The missing owner/type contract is the bug.

**Exhibit A - a reflective call corrupts your immutable tuple** (master bug,
single-threaded, fixed + red-on-master test):

```java
ObjectHandle[] ahPass = hTuple.m_ahValue;  // TODO GG+CP do we need to check these?
return chain.invokeT(frame, hTarget, ahPass, iReturn);
```
code: [xRTMethod.java:206](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java#L206)

When the callee needs no extra registers, the caller's tuple storage
**becomes the callee's register file** - a parameter reassignment inside the
invoked method writes into the caller's tuple, including *const-heap-cached*
ones (container-wide constant corruption). The sibling function-call path
clones defensively; this path forgot. The `TODO` shows the author suspected
it. That is convention-based safety in one picture.

**Exhibit B - compiling your module mutates the loaded library** (master
bug, single-threaded compile, fixed + test): short-hand property overrides
aliased the *super method's* `Parameter` objects - owned by the loaded
ecstasy module - into a new method, and constant registration then rewrote
the shared library objects into the user module's pool.
code: [MethodDeclarationStatement.java:504](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java#L504)

**Exhibit C - the read-only contract that evaporates in production:**

```java
assert (list = Collections.unmodifiableList(m_listContribs)) != null;
return list;
```
code: [Component.java:472](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/Component.java#L472) (one of eight such sites)

Eight getters wrapped their internal collections in `unmodifiable*` **only
inside an `assert`** - run with `-da` (i.e., production) and callers get the
live internal list. The API's safety depended on a JVM flag.

---

## 5. The type system was never consulted (2.5 min)

This is the "runtime shapes are everything" point. Measured on master:
**zero `sealed` types** against **1,549 `instanceof`**, **141
format-enum switches** - and **39 of those switches silently produce a
value when handed a format they don't know.** Not throw. *Answer wrong.*

**Exhibit A - "let's be tolerant":**

```java
switch (constant.getFormat()) {
    case ... 16 cases ...
    default:            // let's be tolerant
        return false;   // a new format silently makes every type "not a tuple"
}
```
code: [TerminalTypeConstant.java:844 - isTuple()](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java#L844)

**Exhibit B - dispatch by string concatenation** (constant folding):

```java
switch (op.TEXT + that.getFormat().name())   // "add" + "Int64" ...
```
code: [StringConstant.java:83](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/constants/StringConstant.java#L83)

A typo'd case string is a silent non-match. This code had **zero unit tests**
until this month - the first tests constant folding ever had.

**Exhibit C - the JIT emits unverifiable bytecode behind an assert:** the
register-kind dispatch in `Builder.checkNull` ended in an `else` guarded
only by `assert` - under `-da`, an unexpected register kind takes the
reference-comparison path and the generated bytecode is simply wrong.
code: [Builder.java:1018 - checkNull()](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/javajit/Builder.java#L1018)

The codebase knows this is fragile - it says so **63 times** in "shouldn't
happen" comments and **814** hand-written `IllegalStateException`s. Those
are the invariants a `sealed` hierarchy states once and javac enforces
everywhere. We proved the cure on the branch: 48 sealed declarations later,
the worst file (23 dispatch switches, 48 blind casts) is down to *one*
legitimate data switch, and adding an enum case now produces a *named compile
error at every affected site* instead of a silent wrong answer. Across the
whole sealing sequence the main sources are +752/-611 lines because tests,
permits lists, and named runtime errors were added; the relevant line-count
win is at the dispatch sites, where casts and default arms disappear and the
compiler takes over review.

That proof is intentionally scoped. The selected closed hierarchies are sealed
and demonstrably better; the entire repository is not cast-free or raw-free.
The remaining work is tracked separately: typed service/fiber payloads,
`OpInfoKey<T>` cache keys, typed native-template reflection, `BinaryAST`
checked reads, `Utils.ANY`, JIT injection suppliers, and the low-risk generic
plumbing. That is the point of the exhibit: every time we replace a cast with a
generic or sealed boundary, a class of runtime review obligations becomes a
compile-time error.

The same pattern shows up in diagnostics and nullness. `ErrorListener` can be
created deep inside the compiler, branched into `BLACKHOLE`, or reduced to
rendered text before a host ever sees a structured event. There is no single
request-owned diagnostic session that an LSP can initialize and pass through.
The nullness picture is similarly backwards: the main Java sources have roughly
700 `return null` sites, thousands of null checks/assignments, and only a
handful of `@NotNull`/`@Nullable` annotations even though JetBrains annotations
are already on the compile classpath. That means the linter keeps telling us
"this receiver may be null" in places where the lifecycle contract is supposed
to make that impossible, and the code does not say which nulls mean "absent",
"not computed", "error", "not applicable", or "legacy sentinel".

Duplicated code and old Java control flow are part of the same story, not a
separate style complaint. Repeated decision blocks, labelled nested loops,
parameter reassignment, raw collections, and handwritten visitor/switch
scaffolding force each call site to remember the invariant again. Modern Java
features - records, sealed hierarchies, generics, lambdas, pattern variables,
`Optional` where absence is a value, and empty collections where absence is
"none" - reduce the number of mutable places where state can leak.

The clone point is not "clone is old-fashioned." It is that `Object.clone()` is
structurally incapable of expressing ownership in 2026 Java. It is shallow,
bypasses constructors, copies final helper cells by reference, copies transient
caches unless every subclass remembers to clear them, and on inner classes
copies the hidden outer pointer. Two defects the type system would have refused
outright:
`Object.clone()` on **inner classes** copies the hidden outer pointer, so a
cloned method's source still answered `getConstantPool()` **through the
original method**, and a cloned component's contributions answered
`getComponent()` with the **source** component. The copy constructor fix
(`that.new Source(...)`) expresses what `clone()` structurally cannot.
code: [MethodStructure.java:1775 - `that.m_source = this.m_source.clone()`](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/MethodStructure.java#L1775) ; [Component.java:2572 - inner class Contribution, cloned the same way](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/Component.java#L2572)

---

## 6. "But XTC is about isolation" - the security-model leaks (2 min)

The language's core promises are container isolation, `maskAs` type
boundaries, and controlled injection. The runtime internals do not enforce
them:

- **Immutability held per-view, not per-object.** Freezing an object through
  one access view left sibling views still writing into the "frozen" shared
  field storage - the *language's central guarantee*, implemented as a
  per-copy boolean. (Five desync mechanisms found, all fixed + tests.)
  - code: [ObjectHandle.java:91 - makeImmutable() flips a per-instance flag](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/ObjectHandle.java#L91)
- **`maskAs` is defeated by its own native code:** `xRTSocket.finishConnect`
  hands application code the **unmasked** native socket view.
  - code: [xRTSocket.java:237](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java#L237)
- **The shared const heap could serve a live `HandleConstant` - an actual
  runtime object - across container boundaries** (now guarded + tested).
  - code: [HandleConstant.java:34 - getHandle() with no owner check](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java#L34)
- **Injection is a single FIFO choke point with no cycle detection.** Issue
  #541: a 12-worker jsondb test deadlocks *permanently* at 0% CPU because
  every fresh `@Inject` is a cross-service round-trip, a blocked
  non-concurrent fiber freezes its whole service, and the injector chain
  calls back into its own ancestry. The runtime's own source says:

  ```java
  // TODO: check for the deadlock        (Fiber.java)
  ```

  The field "used for deadlock detection" is read only by the debugger.
  code: [Fiber.java:487](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/Fiber.java#L487) ; the FIFO choke point: [xunit.x:48 - PassThruInjector](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/lib_xunit/src/main/x/xunit.x#L48)
  A real user hit this last week and had to hand-build fiber-dump tooling
  to even see the cycle.

---

## 7. Errors are swallowed; debugging makes it worse (2 min)

**Failure handling today:** unchecked exceptions, many swallowed on the way
up. Concrete, all fixed on the branch with red-on-master tests:

- Compiler codegen wrapped in **catch-Throwable-and-continue** - an unchecked
  internal codegen defect could be downgraded to console output and the retry
  loop could continue against already-mutated module state. This is separate
  from normal compile diagnostics, which should still accumulate through the
  diagnostic path.
  - code: [Compiler.java:471](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/tool/Compiler.java#L471)
- Op-assembly failure **serialized a zero-op method** - a corrupt `.xtc`
  written without an error.
  - code: [MethodStructure.java:2037 - `System.err.println("Error in ... assemble() of ops")`, then serializes anyway](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/MethodStructure.java#L2037)
- A failed module load **discarded its cause** - "could not load" with no
  why, for any corrupt file.
  - code: [FileRepository.java:205 - `catch (Exception e) { System.out.println(...getMessage()); }`](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/FileRepository.java#L205)
- Async file writes: submit **ignored queued write failures**.
  - code: [xRawOSFileChannel.java:212 - submit()](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java#L212)
- `FullyBoundHandle.chain()`: with `-ea` it asserts; with `-da` it
  **silently drops registered finalizers**. Production behavior differs
  from tested behavior by design of the failure path.
  - code: [xRTFunction.java:918 - `assert m_next == null`](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java#L918) ; the future twin: [xFuture.java:510 - `// must not happen`](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java#L510)

**And when you attach a debugger, it lies:** `toString()` implementations
*mutate state and throw* when invoked on an already-broken object - so
inspecting the crash corrupts or crashes the crash.
code: [TerminalTypeConstant.java:2048 - getValueString() writes m_constId at display time](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java#L2048) ; [TypeInfoReal.java:2164 - toString() warms method chains for the whole type](https://github.com/xtclang/xvm/blob/61e555a68cd82a866f82aea40a3bb97a424a3809/javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java#L2164) There is no logger to
thread through (Section 3), no execution snapshot, and until this branch
there were **essentially no unit tests** on these paths - there are now 560+,
each one red on master first, which is how every claim in this document was
proven rather than argued.

---

## 8. What we should do - the staged plan (2 min)

We are not proposing a rewrite. The audits are done, everything is
classified, and the work splits cleanly:

1. **File the pure bug fixes now.** 18 master bugs, each with a
   red-on-master proof and a master-portable hunk or extraction note. Most
   do **not** need reentrancy to fail: jsondb rollback evidence loss,
   corrupt-module cause loss, zero-op serialization, compiler
   catch-and-continue, async write false success, `Future.and` wrong input,
   JIT failure misreporting, detached startup causes, hash/equality contract
   breaks, reflection tuple aliasing, library `Parameter` mutation, hidden
   outer clone owners, and dropped finalizers. The timer/callback rows break
   with ordinary Java Timer/server threads inside one container. The remaining
   owner-boundary rows become security-critical the moment two containers or
   runs share a JVM: split handle lifecycle state, live `HandleConstant`
   serving, and copied method source metadata resolving through the wrong
   owner.
2. **Land the machine-checked gates.** Fatal `this-escape` and `fallthrough`
   lints (zero suppressions left for the former), the sealing waves, and
   unconditional read-only wrappers. The sealing tally is precise:
   **0 -> 48 sealed declarations**, **30 sealed roots**, about **150
   explicitly permitted classes**, exactly one main-source `non-sealed` hatch,
   **141 format switches reduced to 77**, and **zero message-less runtime
   `UnsupportedOperationException`s**. Including tests, the sealing sequence is
   +1163/-808 lines; main source only is +752/-611. That is not a line-count
   stunt - it buys compile-time exhaustiveness where master had 39
   silent-answer discriminator switches.
3. **Adopt explicit ownership for reentrancy.** The enablement waves:
   owner-passed pools and containers, no constructor publication, per-owner
   metadata - the substance of the prepared PR series. This is what makes
   same-JVM sequential, concurrent containers, and a resident LSP compiler
   *possible*.
4. **Build the diagnostics we were missing:** the world X-ray (implemented:
   snapshot/diff every live container, `retained:` = the sequential-leak
   signal). Show this live from the clickable appendix:
   [World X-Ray printouts](#appendix-world-x-ray-printouts). It contains the
   printout for two sequential runs and for two concurrent containers in one
   JVM. Extend the same diagnostic surface with fiber wait-graph capture -
   which is simultaneously the #541 deadlock detector: when the pool goes idle
   with fibers still waiting, walk the graph, fail one victim with a `Deadlock`
   exception that *names the cycle*. Hangs become diagnosable failures.
5. **Decide the vehicle:** staged PRs into master (prepared, preferred) or a
   long-lived fork (constant rebase cost, split ecosystem). The plan,
   commit-to-PR map, and per-PR descriptions exist either way.

There are only three plausible alternatives:

- **Fork per request/run.** This preserves today's process-global assumptions,
  but it is exactly what prevents a fast LSP, resident compiler, integrated
  debugger, and same-JVM test/stress harness. It also hides bugs that appear
  the moment an embedding keeps the VM alive.
- **Serialize everything through one global lock.** This avoids some races but
  keeps wrong-owner state wrong, makes concurrent containers fictional, and
  still leaves the second sequential run inheriting the first run's statics.
- **Make owners explicit and test the boundaries.** This is the branch's plan:
  small master bug fixes first, then explicit owner APIs, owner-local caches,
  structural gates, and world diagnostics that can prove what is alive.

**Closing line:** every problem above was found by trying to do something
completely ordinary, every one is demonstrated by a test that fails on
master today, and most fixes are small. The expensive thing is not fixing
this - it is every future feature paying the tax of a runtime that cannot
be observed, cannot be tested, and cannot be trusted to fail loudly.

---

## Appendix: World X-Ray Printouts

This is the meeting click target. The identities vary from run to run; the
important shape is stable: `world:`, container identities, `ownership: valid`,
and the `retained:` section in the diff.

Provenance and rerun command:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.WorldSnapshotDemoTest \
  -Porg.xtclang.java.test.stdout=true \
  --rerun-tasks --no-build-cache
```

Sequential same-JVM runs: run 2 starts while run 1's container is still
reachable. The `retained:` line is the leak signal.

```text
========================================================================
== SEQUENTIAL RUNS: run 1 boots its world
========================================================================
world: 1 container(s)
  - Primordial container id=5d9e3d9f pool=3624da92
ownership: valid

========================================================================
== SEQUENTIAL RUNS: run 2 boots; run 1's container still reachable
========================================================================
world: 2 container(s)
  - Primordial container id=463e197 pool=2b0bebff
  - Primordial container id=5d9e3d9f pool=3624da92
ownership: valid

========================================================================
== DIFF world2 - world1: 'retained' is the sequential-run leak signal
========================================================================
world diff:
added: 1
  - Primordial container id=463e197
retained: 1
  - Primordial container id=5d9e3d9f
removed: 0

retained container identity 1570651551 is run 1's world surviving into run 2:
after a completed run this means the old world is still reachable and its
container, pools, and handles cannot be collected
```

Concurrent containers: one world can hold two containers and sweep both for
foreign-owner references.

```text
========================================================================
== MULTIPLE CONTAINERS: one world, two containers, one consistency sweep
========================================================================
world: 2 container(s)
  - Primordial container id=21b8832c pool=4fe9d963
  - Primordial container id=42b70e13 pool=7659e6f0
ownership: valid

'ownership: valid' above is the cross-container consistency check: every
handle, pool, and template reachable from either container was verified to
belong to it - a foreign-owner reference (container A state reachable from
container B) would print the owner path here and fail this test
```

This is why the diagnostic matters: it turns "the process has some state" into
"these are the worlds, these are their owners, and this exact old world is
still reachable after the next run."

---

## Appendix: Master Bug Exposure Sort

Use this when someone says "but we do not run multiple containers in one JVM".
That is not true for every path, but even granting it, most filed bugs remain
bugs:

| Bucket | Master issue rows | Meeting phrasing |
| --- | --- | --- |
| Breaks without multiple containers/runs/compilations | 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 18 | These are ordinary wrong results, lost failures, or already-present Java thread races: swallowed rollback evidence, corrupt module cause loss, zero-op serialization, compiler catch-and-continue, false-success async write, wrong future input, JIT failure misreporting, detached startup causes, timer callback map races, native keep-alive leaks, broken Java collection contracts, tuple aliasing, library metadata mutation, hidden-outer clone owners, and dropped finalizers. |
| Starts breaking immediately when one JVM hosts multiple containers/runs/compilations | 12, 13, 17 | These are owner-boundary bugs: split handle lifecycle state, live `HandleConstant` serving across containers, and copied method source metadata resolving through the wrong pool. |

Rows 9 and 10 are worth calling out verbally: they are not multi-container
bugs, but they are already thread bugs because master uses Timer/server/IO
threads inside a single container.

Detailed filing text: [plans/master-issue-submissions.md](plans/master-issue-submissions.md),
section "Reuse Exposure Categories".

---

## Appendix: Investigation Map

This is the backup index. The main talk uses the highlights; these are the
documents to open when someone asks for method, census, or proof.

| Topic | Documents | What they prove |
| --- | --- | --- |
| Board and filing plan | `must-audit-backlog.md`, `plans/master-issue-submissions.md`, `plans/global-issue-pr-backlog.md`, `plans/github-issue-breakdown.md`, `test-failure-evidence.md` | The authoritative must/should/audit board, the 18 master issue bodies, the broader issue/PR backlog, the PR split, and which tests fail on master. |
| Bad-design taxonomy | `bad-design-decisions-reference.md`, `runtime-ownership-hardening-ledger.md`, `fixed-in-this-branch.md` | The recurring failure families and the branch proof ledger: globals, ambient pools, clone/adoption, error swallowing, callback leaks, split lifecycle state, raw casts. |
| Arrays and mutable representation | `array-list-immutability-study.md`, `array-element-exposure-audit.md`, `modern-java-syntax-audit.md` | Arrays are the object/register/signature representation; counts of exposed arrays/collection getters; where arrays are appropriate and where they are owner leaks. |
| Clone and adoption | `clone-usage-audit.md`, `constant-adoption-clone-audit.md`, `plans/clone-free-adoption-plan.md` | Why `Object.clone()` is hostile to owner-bearing state, which clone families were fixed, and the copy/adoption proof standard. |
| Generics and typed APIs | `generics-api-audit.md`, `nullness-annotation-audit.md` | Raw `Object`, raw futures, untyped handles, caller casts, overloaded null returns, and missing non-null contracts hide payload/owner/lifecycle contracts that generics, typed result records, annotations, or empty immutable collections can express. |
| Sealed hierarchies and compile-time checks | `sealed-hierarchy-audit.md` | Zero sealed types on master, 48 on branch, discriminator-switch/cast counts, staged sealing, and exactly what javac now refuses. |
| Ambient context and explicit ownership | `ambient-context-audit.md`, `scoped-value.md`, `constant-pool-state-audit.md`, `constant-pool-must-audit-classification.md`, `constant-pool-hostile-state-audit.md` | ThreadLocal/ScopedValue are bridges, not ownership. Semantic pool ownership must be explicit; destructive pool mutation and runtime-published pools are tracked separately. |
| Native runtime startup and globals | `native-template-startup-safety.md`, `state-inventory.md`, `must-fix-races.md`, `manual-lazy-cache-audit.md` | The old `INSTANCE`/static metadata model, native template startup hazards, mutable global fields, and lazy cache publication risks. |
| Isolation and security | `541.md`, `master-container-isolation-bug-reports.md`, `ownership-diagnostics.md`, `plans/same-jvm-launcher-stress.md` | Deadlock analysis, container isolation bugs, world snapshots/diffs, and the same-JVM stress proof strategy. |
| Compiler and JIT | `compiler-lexer-parser-this-escape.md`, `jit-implications.md`, `jit-global-owner-classification.md`, `plans/jit-xvm-owner-refactor.md` | Compiler request-state risks, JIT classloader/static owner risks, and the parked/generated-static work. |
| Diagnostics and observability | `logging-diagnostics-audit.md`, `logging-strategy.md`, `exception-hygiene-audit.md`, `plans/unified-logging-jfr-telemetry.md`, `plans/side-effect-free-tostring.md` | Why stdout/stderr and side-effecting `toString()` are not diagnostics; why deep `ErrorList` creation/`BLACKHOLE` suppression blocks request-owned LSP diagnostics; structured logging/JFR and debugger-safe display plans. |
| Lint, duplication, and construction safety | `lint-parallelism-risk-audit.md`, `modern-java-syntax-audit.md`, `this-escape-removal-audit.md`, `this-escape-tally.md`, `compiler-lexer-parser-this-escape.md` | Constructor escape and fallthrough gates, duplicate invariant/control-flow risks, legacy Java patterns, and what remains. |

---

## Appendix: Teleprompter And IDE Cues

Use this as a read-through script. The main body above is the slide deck; this
is what to say and what to open.

1. **Open with why this became blocking.**
   Read: "This is not my first attempt to work around the monolith. Over the
   years I have tried to use the compiler/runtime from Gradle plugins, tests,
   same-JVM stress runs, and the LSP. I even tried shapes like a Gradle-plugin
   compiler daemon to avoid starting a JVM and rebuilding the world for every
   module. The same problem keeps appearing: there is no owned compiler or
   runtime world to reuse incrementally. There is one blob of process-global
   state, thread-local state, static caches, and runtime metadata. If we keep
   forking a JVM, we hide that. If we reuse the JVM, old state leaks into the
   next run."

   Then read: "This has not always been the top priority, but I am not getting
   much further without it. The gains are too large to ignore: a resident
   compiler/runtime could avoid repeated cold starts and plausibly move full
   XDK compiles toward tens of seconds with far less resource use. But that
   requires explicit ownership - request, pool, container, frame, service,
   diagnostic session - not `getCurrentPool()` and ambient state."

   Close the opener with: "The concrete asks are same-JVM compiler/runtime
   reuse, concurrent containers, and LSP-grade performance. Those are not
   future luxuries; they are table stakes for a resident compiler, build
   daemon, LSP, debugger, and test runner."

   IDE: open `docs/reentrancy/presentation.md`, section 0, then section 1.
   Keep this as the visible agenda.

2. **Show one-world-per-process state.**
   Read: "The runtime assumes there is one world in the process forever. The
   second run inherits the first run's globals; a second container is not a new
   world, it is a new caller looking at old state."

   IDE: open `javatools/src/main/java/org/xvm/runtime/template/xObject.java`,
   search `INSTANCE = this`. Then open
   `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java`,
   search `$INSTANCE = new eMutability(Ctx.get())`.

3. **Show the Timer/security point.**
   Read: "This one does not need two XTC containers. A normal Java Timer thread
   already races the service thread. If the task throws, the shared timer dies
   and alarms stop process-wide."

   IDE: open
   `javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java`,
   search `public static Timer TIMER`. Then open
   `docs/reentrancy/runtime-ownership-hardening-ledger.md`, search
   `Alarm callback registry race`.

4. **Show arrays as the runtime representation.**
   Read: "`ObjectHandle[]` is not just a convenient array. It is the register
   file, tuple storage, argument storage, native array backing, and sometimes a
   const-heap object. The type says none of that."

   IDE: open `docs/reentrancy/array-element-exposure-audit.md`, section
   `1.0 Census`. Then open
   `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java`,
   search `hTuple.m_ahValue`.

5. **Explain why clone is indefensible here.**
   Read: "`Object.clone()` bypasses constructors and copies hidden owner state.
   On inner classes it copies the hidden outer pointer. That is how a cloned
   method source still answered through the original method, and a copied
   contribution still belonged to the source component."

   IDE: open `docs/reentrancy/clone-usage-audit.md`, section
   `Why clone() Is Hostile`. Then open
   `javatools/src/main/java/org/xvm/asm/MethodStructure.java`, search
   `m_source.clone`, and `javatools/src/main/java/org/xvm/asm/Component.java`,
   search `class Contribution`.

6. **Make the generics argument concrete.**
   Read: "The argument for generics is not bytecode. It is source-level
   contracts. A raw future or raw handle lets the wrong payload or owner
   compile, then fail later in another service, frame, or cache. The same is
   true for null: if the API does not say whether null means absent, failed,
   not computed, or disabled, the linter cannot help us turn required state
   into final state."

   IDE: open `docs/reentrancy/generics-api-audit.md`, section
   `Bad Single-Threaded Design`, then section `Worked Examples`. Then open
   `docs/reentrancy/nullness-annotation-audit.md`, section
   `Strategic Finding: Null Means Too Many Things`.

7. **Make the sealed-interface argument concrete.**
   Read: "Sealed types do not add happens-before edges. They do something
   different: they move shape mistakes from runtime defaults to javac errors.
   Master had zero sealed types, 1,549 `instanceof`, and 39 discriminator
   switches that silently answered on unknown formats."

   IDE: open `docs/reentrancy/sealed-hierarchy-audit.md`, search
   `Final census`. Then open
   `javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java`,
   search `let's be tolerant`. If the discussion turns to duplication or old
   Java patterns, open `docs/reentrancy/modern-java-syntax-audit.md`, section
   `Why This Is Not Style Cleanup`.

8. **Show the security-model leaks.**
   Read: "The language promises isolation, immutability, masking, and
   injection control. The runtime sometimes stores those promises as
   per-view booleans, raw handles, or single FIFO chains."

   IDE: open `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java`,
   search `makeImmutable`. Open
   `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java`,
   search `getHandle(Frame`. Open `docs/reentrancy/541.md`, search
   `The diagnostic gap`.

9. **Show swallowed errors.**
   Read: "A compiler or runtime that prints and continues after internal
   failure produces artifacts and host statuses that are not trustworthy.
   These are not style fixes; they are false success fixes. The same diagnostic
   problem exists one layer down: if parser/type/runtime helpers create their
   own `ErrorList` or send errors to `BLACKHOLE`, the LSP cannot own one
   coherent diagnostic stream for a request."

   IDE: open `javatools/src/main/java/org/xvm/tool/Compiler.java`, search
   `Failed to generate code`. Open
   `javatools/src/main/java/org/xvm/asm/MethodStructure.java`, search
   `Error in MethodStructure.assemble() of ops`. Then open
   `docs/reentrancy/logging-diagnostics-audit.md`, section
   `Direct ErrorList And BLACKHOLE Findings`.

10. **Show the master-bug categorization.**
    Read: "Most of the 18 issues break even if we never allow multiple
    containers. Some break with ordinary Java Timer/server threads. The
    owner-boundary issues become security-critical immediately when two
    containers or runs share one JVM."

    IDE: open `docs/reentrancy/plans/master-issue-submissions.md`, section
    `Reuse Exposure Categories`.

11. **Show the world X-ray.**
    Read: "This is the diagnostic we were missing. It makes worlds visible:
    current containers, owner validation, and retained old worlds after a
    second run."

    Click first: [World X-Ray printouts](#appendix-world-x-ray-printouts).
    Show the sequential block first. Point at `world: 1 container(s)`, then
    `world: 2 container(s)`, then `retained: 1`. Say: "This is the old world
    still reachable after run 2 starts. Without a diagnostic like this, we only
    know that some static or cache somewhere is alive; we cannot name the world
    or its owner."

    Then show the concurrent-container block. Point at `ownership: valid`.
    Say: "This is the other mode the LSP and debugger need: two containers in
    one process, both swept for foreign-owner state. If container A's handle is
    reachable from container B, this check prints the owner path and fails."

    Terminal, optional:
    ```bash
    ./gradlew :javatools:test \
      --tests org.xvm.runtime.WorldSnapshotDemoTest \
      -Porg.xtclang.java.test.stdout=true \
      --rerun-tasks --no-build-cache
    ```

    IDE provenance: open
    [WorldSnapshotDemoTest.java](../../javatools/src/test/java/org/xvm/runtime/WorldSnapshotDemoTest.java).
    Search `SEQUENTIAL RUNS` and `MULTIPLE CONTAINERS`. Then open
    [OwnershipDiagnostics.java](../../javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java)
    and search `WorldDiff`.

12. **Close with the proposed vehicle.**
    Read: "The ask is not 'merge everything blind'. File the 18 master bugs
    first. Land machine-checked gates. Then move to explicit ownership with
    world diagnostics proving what is alive. The alternatives are forking a JVM
    per request, serializing through one global lock, or accepting that the
    runtime cannot be embedded safely."

    IDE: open `docs/reentrancy/plans/github-issue-breakdown.md`, search
    `Master-Bug PRs Versus Reentrancy Enablement`.

---

*Backup slides / deep links:
[World X-Ray printouts](#appendix-world-x-ray-printouts),
`must-audit-backlog.md` (the board),
`master-issue-submissions.md` (18 master bugs),
`global-issue-pr-backlog.md` (broader issue/PR queue),
`github-issue-breakdown.md` (PR plan),
`array-element-exposure-audit.md`, `sealed-hierarchy-audit.md`,
`541.md` (the deadlock analysis), `test-failure-evidence.md` (what each
test proves).*
