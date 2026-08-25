# Why the Runtime Cannot Be Reused, and What We Do About It

A 10-15 minute brief. Every claim below is backed by a committed test, a
file:line citation, or a reproducer on the `lagergren/lazy-instance` branch;
nothing here is opinion about style. The numbers are all re-measurable with
the grep commands recorded in the audit docs.

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

> The ask at the end is not "rewrite it." We have a staged, test-backed plan
> where most of the fixes are hunk-sized. But first, the evidence.

---

## 2. One world per process: the global-state census (2 min)

What we found when we tried to make run #2 work:

- **Native templates published themselves from their constructors into
  process-wide statics** (`INSTANCE = this` while the object was still under
  construction). Last writer wins; the second container silently gets the
  first container's template state.
- **The JIT bakes the first world into class initializers.** Generated
  `<clinit>` code reads the ambient `Ctx.get()` and writes constants,
  injected values, and singletons into **classloader-wide static fields**.
  The first active container becomes *permanent state* for every later one.
- **A single static `java.util.Timer` serviced all containers**, and its
  callback registry was a plain `HashMap` mutated from both the service
  thread and the Timer thread - one race and the shared Timer thread dies,
  which kills alarms **for every container in the JVM**. (Reachable in any
  timer-using program; fixed + test: `NativeCallbackRegistrationTest`.)
- The ambient `ConstantPool` travels in a **ThreadLocal**, compiler counters
  are global statics, and the `Xvm` bootstrap wrote six public mutable
  fields *while booting*, visible half-initialized to any observer.

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

**Exhibit A - a reflective call corrupts your immutable tuple** (master bug,
single-threaded, fixed + red-on-master test):

```java
ObjectHandle[] ahPass = hTuple.m_ahValue;  // TODO GG+CP do we need to check these?
return chain.invokeT(frame, hTarget, ahPass, iReturn);
```

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

**Exhibit C - the read-only contract that evaporates in production:**

```java
assert (list = Collections.unmodifiableList(m_listContribs)) != null;
return list;
```

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

**Exhibit B - dispatch by string concatenation** (constant folding):

```java
switch (op.TEXT + that.getFormat().name())   // "add" + "Int64" ...
```

A typo'd case string is a silent non-match. This code had **zero unit tests**
until this month - the first tests constant folding ever had.

**Exhibit C - the JIT emits unverifiable bytecode behind an assert:** the
register-kind dispatch in `Builder.checkNull` ended in an `else` guarded
only by `assert` - under `-da`, an unexpected register kind takes the
reference-comparison path and the generated bytecode is simply wrong.

The codebase knows this is fragile - it says so **63 times** in "shouldn't
happen" comments and **814** hand-written `IllegalStateException`s. Those
are the invariants a `sealed` hierarchy states once and javac enforces
everywhere. We proved the cure on the branch: 48 sealed declarations later,
the worst file (23 dispatch switches, 48 blind casts) is down to *one*
legitimate data switch, at **net negative lines**, and adding an enum case
now produces a *named compile error at every affected site* instead of a
silent wrong answer.

Two bonus defects the type system would have refused outright -
`Object.clone()` on **inner classes** copies the hidden outer pointer, so a
cloned method's source still answered `getConstantPool()` **through the
original method**, and a cloned component's contributions answered
`getComponent()` with the **source** component. The copy constructor fix
(`that.new Source(...)`) expresses what `clone()` structurally cannot.

---

## 6. "But XTC is about isolation" - the security-model leaks (2 min)

The language's core promises are container isolation, `maskAs` type
boundaries, and controlled injection. The runtime internals do not enforce
them:

- **Immutability held per-view, not per-object.** Freezing an object through
  one access view left sibling views still writing into the "frozen" shared
  field storage - the *language's central guarantee*, implemented as a
  per-copy boolean. (Five desync mechanisms found, all fixed + tests.)
- **`maskAs` is defeated by its own native code:** `xRTSocket.finishConnect`
  hands application code the **unmasked** native socket view.
- **The shared const heap could serve a live `HandleConstant` - an actual
  runtime object - across container boundaries** (now guarded + tested).
- **Injection is a single FIFO choke point with no cycle detection.** Issue
  #541: a 12-worker jsondb test deadlocks *permanently* at 0% CPU because
  every fresh `@Inject` is a cross-service round-trip, a blocked
  non-concurrent fiber freezes its whole service, and the injector chain
  calls back into its own ancestry. The runtime's own source says:

  ```java
  // TODO: check for the deadlock        (Fiber.java)
  ```

  The field "used for deadlock detection" is read only by the debugger.
  A real user hit this last week and had to hand-build fiber-dump tooling
  to even see the cycle.

---

## 7. Errors are swallowed; debugging makes it worse (2 min)

**Failure handling today:** unchecked exceptions, many swallowed on the way
up. Concrete, all fixed on the branch with red-on-master tests:

- Compiler codegen wrapped in **catch-Throwable-and-continue** - a compiler
  defect produced a *silently wrong* build instead of a failed one.
- Op-assembly failure **serialized a zero-op method** - a corrupt `.xtc`
  written without an error.
- A failed module load **discarded its cause** - "could not load" with no
  why, for any corrupt file.
- Async file writes: submit **ignored queued write failures**.
- `FullyBoundHandle.chain()`: with `-ea` it asserts; with `-da` it
  **silently drops registered finalizers**. Production behavior differs
  from tested behavior by design of the failure path.

**And when you attach a debugger, it lies:** `toString()` implementations
*mutate state and throw* when invoked on an already-broken object - so
inspecting the crash corrupts or crashes the crash. There is no logger to
thread through (Section 3), no execution snapshot, and until this branch
there were **essentially no unit tests** on these paths - there are now 561,
each one red on master first, which is how every claim in this document was
proven rather than argued.

---

## 8. What we should do - the staged plan (2 min)

We are not proposing a rewrite. The audits are done, everything is
classified, and the work splits cleanly:

1. **File the pure bug fixes now.** ~18 master bugs, each hunk-sized with a
   red-on-master test (the tuple corruption, the library-pool rewrite, the
   Timer-killer, the swallowed-error family, the clone outer-pointer pair,
   ...). Zero design controversy: each PR is "this test fails before, passes
   after." Openers are already drafted.
2. **Land the machine-checked gates.** Fatal `this-escape` and `fallthrough`
   lints (zero suppressions left for the former), the sealing waves (net
   negative lines, behavior preserved arm-for-arm), unconditional read-only
   wrappers. These turn every convention this document complained about into
   a compile error, so the bug classes cannot come back.
3. **Adopt explicit ownership for reentrancy.** The enablement waves:
   owner-passed pools and containers, no constructor publication, per-owner
   metadata - the substance of the prepared PR series. This is what makes
   same-JVM sequential, concurrent containers, and a resident LSP compiler
   *possible*.
4. **Build the diagnostics we were missing:** the world X-ray (implemented:
   snapshot/diff every live container, `retained:` = the sequential-leak
   signal) extended with fiber wait-graph capture - which is simultaneously
   the #541 deadlock detector: when the pool goes idle with fibers still
   waiting, walk the graph, fail one victim with a `Deadlock` exception that
   *names the cycle*. Hangs become diagnosable failures.
5. **Decide the vehicle:** staged PRs into master (prepared, preferred) or a
   long-lived fork (constant rebase cost, split ecosystem). The plan,
   commit-to-PR map, and per-PR descriptions exist either way.

**Closing line:** every problem above was found by trying to do something
completely ordinary, every one is demonstrated by a test that fails on
master today, and most fixes are small. The expensive thing is not fixing
this - it is every future feature paying the tax of a runtime that cannot
be observed, cannot be tested, and cannot be trusted to fail loudly.

---

*Backup slides / deep links: `must-audit-backlog.md` (the board),
`github-issue-breakdown.md` (Category A bug list + PR plan),
`array-element-exposure-audit.md`, `sealed-hierarchy-audit.md`,
`541.md` (the deadlock analysis), `test-failure-evidence.md` (what each
test proves).*
