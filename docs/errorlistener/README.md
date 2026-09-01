# Diagnostics in XVM: the `ErrorListener` architecture

How a diagnostic gets from the place that detects it to the host that wants to see it, why the
previous arrangement could not do that, and what a host has to know to use it.

---

## 1. What was wrong before

The listener was not a design. It was six mechanisms that each solved one call site, and the reason
they did not add up is worth stating precisely, because every one of them looked locally reasonable.

### 1.1 It was optional, so the quiet path was the default

Twenty-eight sites accepted `null` and turned it into `ErrorListener.BLACKHOLE`:

```java
if (errs == null) {
    errs = ErrorListener.BLACKHOLE;
}
```

Nothing forced a caller to supply a listener, so "I did not think about errors" and "I deliberately
discard errors" compiled to the same thing - and the first was what you got by default. Sixty-six
call sites passed a literal `null` for exactly this reason, including `ReturnStatement`, which
passed `null` on one line and its real listener on the next.

### 1.2 The listener was smuggled through a callback interface

```java
interface ResolutionCollector {
    default ErrorListener getErrorListener() { return ErrorListener.BLACKHOLE; }
}
```

Name resolution asked the *collector* for a sink rather than being handed one. Two consequences.
Any implementor that did not override got silence for free. And an implementor whose listener varies
per call had to park it on itself - which `NameResolver` did, with an honest comment:

```java
// store off the error list for use by call backs
// (note: there's no attempt to clean this up later)
m_errs = errs;
```

That field could never be `final`: `NameResolver` is cached on the AST node, so it outlives any one
listener and is reused across compilation stages with different ones. No local tidying could fix it;
the interface pulling instead of pushing is what forced it. Four more AST statements had the same
pattern for `getLabelVar`.

### 1.3 It was found in ambient state

`TypeConstant.ensureTypeInfo()` walked the structure tree to a mutable field on `FileStructure`,
which `Compiler` set and cleared a hundred and sixty lines apart - and cleared *conditionally*:

```java
if (!m_errs.hasSeriousErrors()) {
    m_structFile.setErrorListener(null);
}
```

A compilation that produced errors therefore left the structure permanently silenced. The clear only
ran on the path that had nothing to report. Worse, the setter was reachable through
`XvmStructure.setErrorListener`, which delegated the mutation *to the parent*, so any structure could
silence its whole tree.

The file's own comment records that master consulted a `ConstantPool` thread-local here and that E3
replaced it with an explicit parameter. The field is what was left behind - the same ambient channel
in a different shape.

### 1.4 A legitimate decision was expressed by destroying the parameter

Eight sites in `TypeConstant` did this, always on the line after setting a completeness flag:

```java
fIncomplete = true;
errs        = ErrorListener.BLACKHOLE;
```

The *intent* is correct and standard: once a type computation is known incomplete, the errors that
follow are consequences, and surfacing them buries the real one. **A refactor that made these always
report would regress the compiler into error cascades.** What was wrong was saying it by overwriting
the caller's listener: redundant with the flag set on the same line, invisible at the call site,
irreversible for a genuinely unrelated later error, and it destroyed the errors rather than setting
them aside.

### 1.5 The construction constants were arbitrary

`new ErrorList(341)`. Also 24, 100, 10, 5, 1 and `Integer.MAX_VALUE`, with no shared rationale and
at least two that cannot have been chosen deliberately.

### 1.6 There was nowhere for a failure to go

Fifty-three `System.err.println` / `printStackTrace` sites print a real diagnostic and continue,
because no sink was reachable. One is labelled `// soft assert`.

---

## 2. The architecture now

### 2.1 One rule: the listener is reached by **ownership**

Not by a thread-local, not by walking a structure tree, not by asking a callback. Two owners:

| owner | owns | default |
|---|---|---|
| `ConstantPool` | compile-time work - every `Constant` and `XvmStructure` can reach its pool | `ErrorListener.RUNTIME` |
| `Container` | runtime work - reached from a `Frame` via `frame.container()` | inherits from parent; root answers `RUNTIME` |

`ensureTypeInfo()` with no argument asks its own pool. One hop to a real owner.

### 2.2 Containers inherit down the declared parent chain

`Container`'s listener is a `final` constructor parameter. `null` means *inherit*:

```java
public ErrorListener getErrorListener() {
    ErrorListener errs = f_errs;
    if (errs != null) {
        return errs;
    }
    Container parent = f_parent;
    return parent == null ? ErrorListener.RUNTIME : parent.getErrorListener();
}
```

This is inheritance down a real ownership chain, not an ambient lookup - `f_parent` is the container
that created this one. It matters because **every run is a nested container**: without inheritance a
host that configured the engine would get nothing from the runs it started.

### 2.3 Constants are adopted, which is what makes per-compile isolation work

The non-obvious mechanism, worth knowing before "improving" it:

```java
// ConstantPool.register
if (constant.getContaining() != this) {
    constant = (T) constant.adoptedBy(this);
}
```

A compile that references `ecstasy.collections.Map` **registers it into its own pool**. So
`getConstantPool()` - and therefore the listener `ensureTypeInfo()` resolves - is the *compiling*
pool, not the library's. Parameterized types likewise: `ensureParameterizedTypeConstant` builds
`new ParameterizedTypeConstant(this, ...)` where `this` is the interning pool.

This is why two parallel compiles cannot report into each other's listener, and why library types a
compile touches still report to that compile.

### 2.4 Cascade suppression has a name

```java
fIncomplete = true;
errs        = errs.suppressCascade();
```

`suppressCascade()` returns a branch. A branch collects and only `merge()` promotes, so declining to
merge is already "record but do not surface" - using the mechanism the compiler already uses
everywhere else. The errors survive for inspection; dropping them is a choice rather than the only
option.

### 2.5 Decorators, not interface changes

`Slf4jErrorListener` and `JfrErrorListener` wrap a listener and pass everything on. The wrapped one
still owns `isAbortDesired`, `hasSeriousErrors` and the collecting that drives the compiler's stages.

---

## 3. The two sinks, and why there are two

This is the part most likely to be got wrong.

| | scope | receives |
|---|---|---|
| `compile(listener, ...)` | one compile | that compile's diagnostics |
| `builder().diagnosticSink(...)` | the engine's lifetime | work no compile owns: library-internal resolution, runtime metadata |

They cannot be merged. Giving a per-compile listener the second would mean writing it onto the
shared library pools, and two parallel compiles would then fight over one field - reintroducing
exactly the shared mutable state this replaced. **A host that wants everything sets both.**

---

## 4. Using it

### 4.1 A host sink for one compile

```java
var seen = new CopyOnWriteArrayList<String>();
ErrorListener mine = err -> seen.add(err.getCode());   // log() is void; see section 5

try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
    var compiled = engine.compile(mine, new XtcEngine.SourceUnit("Bad", """
            module Bad {
                void run() {
                    this is not ecstasy
                }
            }
            """));

    assertFalse(compiled.isSuccess());
    assertFalse(seen.isEmpty());          // the listener was TOLD, as errors were produced
    assertFalse(compiled.diagnostics().isEmpty());   // and the result still carries them
}
```

### 4.2 Parallel compiles stay isolated

```java
var futureA = CompletableFuture.supplyAsync(() -> engine.compile(listenerA, invalidUnit));
var futureB = CompletableFuture.supplyAsync(() -> engine.compile(listenerB, validUnit));

assertFalse(futureA.get().isSuccess());
assertTrue(futureB.get().isSuccess());    // A must not fail B
assertTrue(seenB.isEmpty());              // and B's listener must not hear A's diagnostics
```

### 4.3 Both sinks

```java
try (var engine = XtcEngine.builder()
        .modulePath(xdkModulePath())
        .diagnosticSink(engineSink)       // library + runtime work
        .build()) {
    engine.compile(compileSink, unit);    // this compile's diagnostics
}
```

### 4.4 Logging, at near-zero cost when disabled

```java
var listener = new Slf4jErrorListener(collectingList);
```

Two rules make a disabled level cost a boolean read, and both are easy to get wrong:

- guard with `isXxxEnabled()` **before** anything that exists only for the log. `ErrorInfo.getMessage()`
  formats the message from its code and parameters, so calling it unconditionally is the expensive
  mistake;
- pass arguments as slf4j parameters, never concatenation.

```java
// near-zero when TRACE is off: no formatting, no allocation
public void trace(String what, Object detail) {
    if (logger.isTraceEnabled()) {
        logger.trace("{}: {}", what, detail);
    }
}
```

### 4.5 JFR

`JfrErrorListener` emits one event per diagnostic behind `shouldCommit()`. It does **not** replace
`XtcEngine.CompileEvent`: that is a *span* over a whole operation, and a listener never learns when a
compile starts or what it produced. Different granularities; a profile wants both.

---

## 5. Recording and aborting are two questions (was: the sharp edge)

This section used to describe a trap and recommend a fix. The fix is now in, and this records what
changed and why, because the shape of the mistake is worth keeping.

### 5.1 What it was

`log` returned a boolean that meant *abort*:

```java
// ErrorList
public boolean log(ErrorInfo err) {
    ...
    return isAbortDesired();
}

// Parser, Lexer
if (m_errs.log(severity, sCode, aoParam, ...)) {
    throw new CompilerException("error list is full: " + m_errs);
}
```

One boolean meant both *"I recorded it"* and *"stop compiling"*, which conflated observing with
participating. **A host that only wanted to watch had no correct value to return**: `false`
suppressed a legitimate abort, `true` invented one. And the exception said "error list is full"
whatever the actual reason - a `FATAL` aborts too.

`ErrorListener.RUNTIME` had the same defect in a sharper form: it **threw `IllegalStateException`
from inside `log`** at ERROR and above. So the same diagnostic behaved differently depending on who
owned the pool - an `ErrorList` recorded it and carried on, `RUNTIME` blew up from wherever the code
happened to be - and the throw pre-empted whatever the detecting code intended to throw next, making
even the exception *type* depend on the listener.

### 5.2 What it is

`log` returns `void`. `isAbortDesired()` is the only question about control flow, and the code that
detects a problem is the code that decides to stop:

```java
// Parser, Lexer
m_errs.log(severity, sCode, aoParam, m_source, lPosStart, lPosEnd);

if (m_errs.isAbortDesired()) {
    m_fAvoidRecovery = true;
    throw new CompilerException("aborting the parser; " + m_errs);
}
```

`RuntimeErrorListener` prints and returns - serious diagnostics to `System.err`, the rest to
`System.out` - and never throws. The runtime now behaves like the compiler.

Only **three** call sites in the whole codebase were reading `log`'s result as control flow (two in
`Lexer`, one in `Parser`); everything else forwarded it. Two more read it indirectly, through
`fHalt |= log(...)` in `validate()` - `Annotation` and `ImmutableTypeConstant` - and those now ask
`errs.isAbortDesired()` after logging, which is exactly what `log` used to return.

### 5.3 What this costs, stated plainly

A listener that keeps no state cannot answer `isAbortDesired()`, and answers "no". So a bare lambda
host no longer aborts a compilation - not even on `FATAL`. **That is the intended behaviour**: an
observer has no basis for stopping a compilation, and the alternative was letting one stop a
compilation by accident. A host that wants to participate wraps a real `ErrorList`, as
`Slf4jErrorListener` and `JfrErrorListener` do; a host that wants to watch now simply watches:

```java
ErrorListener mine = err -> seen.add(err.getCode());   // that is the whole listener
```

Pinned by `AbortIsNotLoggedTest`: the parser still aborts for a `firstError()` list, still carries on
for an `unlimited()` one, an observer changes neither, `RUNTIME` reports at every severity without
throwing, and a branch carries the decision until it merges.

**One correction, because this advice was false through one path.** `XtcEngine.compile` tees the
caller's listener alongside the engine's own `ErrorList`, and `TeeErrorListener.isAbortDesired()`
consulted only the engine's. So a host could follow the advice above to the letter - wrap a real
`ErrorList.firstError()`, pass it to `compile` - and still watch the compiler run to completion. The
tee now consults both, so either can ask to stop. A host that only observes is unaffected, because a
stateless listener answers `false`. Two related wiring gaps were fixed with it:
`Runner.createBaseConnector` built its `InterpreterConnector` without a listener, so the CLI's own
listener never reached the container that runs the program.

## 6. What is still open

- **25 `System.err` sites, down from 53.** Twenty-eight have been converted; what is left has no
  owner, or is not a diagnostic. (A grep now finds 26: the extra one is inside
  `RuntimeErrorListener` itself, which is where a serious diagnostic *lands* rather than a place
  that has nowhere to send one.)

  Reaching an owner turned out to need no threading anywhere. A `Constant` or `XvmStructure` asks its
  pool (`getConstantPool().getErrorListener()`); runtime code asks its container
  (`frame.container()`, `container()`); the JIT builders ask their type system
  (`typeSystem.pool()`). Where a method already receives the caller's pool, that one is used instead
  - *who is asking* beats *who owns the constant*, per §2.3.

  | area | sites | codes |
  |---|---|---|
  | `asm` | 8 | `VERIFY-94` .. `VERIFY-100`, plus the existing `VE_TYPE_PARAMS_WRONG_NUMBER` |
  | `runtime` | 13 | `RUNTIME-01` .. `RUNTIME-13` |
  | `javajit` | 7 | `RUNTIME-14` .. `RUNTIME-19` |

  **All but one report at `WARNING`, and that is not timidity - see §7.** The exception,
  `MethodStructure.assemble`, is a genuine failure that already threw; it reports at `FATAL` and then
  throws explicitly, so the throw is what aborts rather than `log()`'s return value.

  The 25 that remain are not diagnostics looking for a sink:

  | site | why it stays |
  |---|---|
  | `tool/Console` | it *is* the console - the tool's own stdout/stderr |
  | `tool/Runner`, `javajit/JitConnector` | top-level crash handlers, outside any container |
  | `javajit/Ctx.log` | the debug-print hook **called from generated bytecode** (`MD_log`) |
  | `javajit/ModuleLoader` | a debug class-dump writer failing to open its file |
  | `runtime/DebugConsole` | interactive debugger output |
  | `runtime/Container.recordRuntimeFailure` | already has a channel (`f_runtimeFailure` -> `join()`), and carries a Java stack trace an `ErrorInfo` cannot |
  | `runtime/.../web/xRTServer`, `.../crypto/xRTCertificateManager` | HTTP request tracing and swallowed exceptions - operational logging and a silent-failure problem, not language diagnostics |
  | `asm/LinkedRepository`, `asm/DirRepository` | `ModuleRepository` implementations - not structures, no pool |
  | `asm/ConstantPool` static init | parses `implicit.x` before any pool instance exists; it already has a local `ErrorList` and already throws explicitly |
  | `compiler/ast` x3 | `NameExpression:1188` is a developer TODO marker; `Expression:825` and `ConvertExpression:105` carry their author's note that they are *not compiler diagnostics* |

- **60 no-argument `ensureTypeInfo()` calls, and that is the answer, not a backlog.** §2.3 explains
  why: adoption makes the asker the owner, so the pool one of these resolves to is the *compiling*
  pool. Converting the rest would make the invariant unconditional rather than a consequence of
  adoption, but would also route corrupt-library faults into user-facing diagnostics with no source
  position. E35 D has the argument; the recommendation is the adoption-invariant test that exists
  (`ConstantAdoptionListenerTest`), not seventy conversions.

  Fifteen were converted, and the rule for picking them is the one §8.4 states: **is this call
  asserting that the type is valid, or asking whether it is?** Two got the caller's listener
  (`CmpExpression.checkConstType`, `ArrayAccessExpression.validate`) - both run from `validate`,
  where the type is being asserted. The other thirteen got an explicit `BLACKHOLE`, because they sit
  inside searches and guesses.

  **Zero no-argument calls now remain in a method that has a listener in scope.** Every call that
  could have used a caller's listener either does, or says `BLACKHOLE` with a reason at the site.

- **Seven dead `ErrorListener` parameters removed.** Finding the above turned up a pattern worth
  naming: a helper that *takes* a listener and never uses it, because every call inside it passes
  `BLACKHOLE`. It reads as though the method reports, when it deliberately does not.

  | method | why the listener had to go, not the silence |
  |---|---|
  | `RelOpExpression.guessLeftType` | a guess; "no" is the answer |
  | `RelOpExpression.selectRightType` | the same, for the right-hand side |
  | `RelOpExpression.getImplicitMethod` | its own javadoc says "for inference purposes"; both callers already passed BLACKHOLE |
  | `ArrayAccessExpression.determineIndexType` | searches for an index type that fits |
  | `ArrayAccessExpression.findArrayAccessor` | searches for an accessor; the **caller** reports `MISSING_OPERATOR_SIGNATURE` against a source position this method does not have |
  | `ToIntExpression.getExtractor` | a lookup by class name; "nothing to extract" is the return value |
  | `ToIntExpression.getConvertMethod` | the same |

  What is left is eleven methods that take an unused listener and **must** keep it: they are base
  implementations of virtual methods (`AstNode.resolveNames`, `Statement.emit`,
  `TypeExpression.instantiateTypeConstant`, ...) whose overrides do use it. The contract owns the
  parameter, not the body.

- **`ConstantPool.m_errs` stays settable, and now for a real reason.** The compiler no longer swaps
  it (§9), so the remaining setter has one job: a library pool is pointed at whichever engine is
  using it (`XtcEngine.prelinkSystemLibraries`). That is a genuine change of owner over the pool's
  life, not a phase flag, and two engines may legitimately target the same repository - so
  set-once would be wrong. The field is `volatile` and `@NotNull`, and the setter `requireNonNull`s.

---

## 7. Severity, decided rather than inherited

The §6 conversions were made while `ErrorListener.RUNTIME` still threw from inside `log()` at ERROR
and above (§5.1), which forced almost all of them to `WARNING` regardless of how bad the condition
was. That constraint is gone, so the severities were re-decided rather than left as they landed.

The evidence that made it safe to decide freely: a full clean `./gradlew build` - 23 `compileXtc`
and 23 `testXtc` tasks - emits **zero** `VERIFY-9x` and **zero** `RUNTIME-nn` diagnostics. None of
these sites fires in normal operation, so severity is a question of honesty, not of risk.

**`WARNING` - the site recovers with a defined answer.** All eight `asm` conversions, and most of
the runtime and JIT ones. `Relation.INCOMPATIBLE`, the existing type parameters, `methodBest`,
`null`, a fallback builder: the computation continues and the caller gets a usable answer.
`WARNING` is also exact under every listener - `ErrorList` records it without touching
`hasSeriousErrors()`, `m_cErrors` or `isAbortDesired()` - so nothing downstream changes its mind.
Raising these would make a compile that hits one give up.

**`ERROR` - the operation the caller asked for did not happen.**

| site | what actually happened |
|---|---|
| `Frame.resolveType` | the type is still formal; it did not resolve |
| `MainContainer.invoke0` | the entry point does not exist, so nothing ran |
| `ClassTemplate.markNativeMethod` / `markNativeProperty` | a native template names a member the Ecstasy source does not declare - a defect in the runtime's own wiring |
| `AugmentingBuilder.assembleMethod` | the method is left with no implementation; the line above it used to be a commented-out `throw new IllegalStateException` |

Nothing at run time gates on `hasSeriousErrors()`, so these inform a host without changing what the
runtime does - which is exactly what §5.2 bought.

**`INFO` - a developer asked to watch.** `Frame`'s wrapping notice is emitted only under
`-DDEBUG=all`; `xException`'s detail dump is internal information about an exception the program is
already handling. Neither is a statement that something is wrong.

**`FATAL` then an explicit `throw`** - `MethodStructure.assemble`, the one site that does not
recover. Since §5.2 its `IOException` reaches the caller whoever owns the pool.

The rule for `asm` and `runtime` code:

> Report at `WARNING` when you recover, `ERROR` when the caller's operation failed, `INFO` when you
> are only tracing. Report at `FATAL` and `throw` on the next line when you cannot continue. Never
> make control flow depend on the listener.

## 8. Why `ensureTypeInfo` takes a listener, and why callers pass `BLACKHOLE`

This is the part of the design that reads wrong, and it reads wrong because it *is* wrong. Worth
stating precisely, because the fix is bigger than anything else in this document and nobody should
start it by accident.

### 8.1 `ensureTypeInfo` is not a getter

It builds the flattened `TypeInfo` for a type: every method, property, child and contribution the
type has after walking `extends` / `implements` / `incorporates` / `into` / annotations, resolving
generics, normalizing type parameters and resolving auto-narrowing. Along the way it:

- **interns constants into the pool** - that is why it opens `openRuntimeSynthesisWindow`;
- **memoizes** the answer (`setTypeInfo` / `getTypeInfo`), under `synchronized`;
- **handles mutual recursion** - to build X you may need Y, which needs X - through a deferred
  queue, a placeholder `TypeInfo`, and a notion of an *incomplete* answer that gets completed later;
- **validates**, and that is the point here. `buildTypeInfo` logs about ninety distinct diagnostics.
  `VERIFY-67` ("property information contains conflicting types"), `VERIFY-70` ("an `@Override`
  names a super method that cannot be found"), `NAME_UNRESOLVABLE`. These are real diagnostics about
  somebody's source - they are exactly what appears if you point the pool at a compiler's listener
  without doing anything else (§9).

So the listener is not decoration. Building a `TypeInfo` is where a large class of type errors is
*discovered*.

### 8.2 So why would a caller ask for silence?

Two reasons, and they are different.

**Speculation.** `testFit`, `getImplicitType`, `getConverterTo`, `guessLeftType` all ask *would this
work?* The compiler tries candidate types and keeps one that fits. Diagnostics from a candidate that
did not fit are not diagnostics - they *are* the answer, "no". Surfacing them buries the real error
under a pile of failed guesses. This is ordinary cascade suppression (§2.4) and it is correct.

**Memoization, which is the sharper reason.** The answer is cached, so the diagnostics are produced
by *whichever caller asks first*. If that is a speculative probe, then a probe produces user-visible
errors, and the later caller that actually cared gets the cached result and hears nothing. Which
caller "owns" the errors is decided by call ordering - not a property anyone can reason about.

The code half-knows this. At the end of `ensureTypeInfoInWindow`:

```java
if (errs.hasSeriousErrors()) {
    // we need to return what we've got, but don't cache it
    invalidateTypeInfo();
    ...
}
```

It refuses to cache a `TypeInfo` whose build produced serious errors, precisely so the next caller
rebuilds and can hear them again. That is a real mitigation - and it is also an admission. It only
covers `ERROR` and above, and it means a type that fails is rebuilt on **every** subsequent ask,
forever. `PropertyClassTypeConstant.getPropertyInfo` has no such escape hatch: it memoizes into
`m_info` unconditionally, which is why §9 lists it as the case where silence is not merely right but
necessary.

### 8.3 What the parameter actually is

`ensureTypeInfo(errs)` fuses two operations with different natures:

| | nature |
|---|---|
| compute the metadata | idempotent, cacheable, should never report |
| validate the type | produces diagnostics owned by whoever asked, at a source position they own |

Because they are fused, every caller has to pick a mode, and **the mode is expressed by which
listener it hands in**. `BLACKHOLE` means "I am in the compute half."

That is the answer to "why do we pass BLACKHOLE to a type constant": *the listener parameter is a
mode flag in disguise.* It is not describing where errors should go; it is describing whether this
call is a question or an assertion.

### 8.4 The fix, as actually applied

The model is now: **the `TypeInfo` carries the diagnostics its own construction produced.** Building
records them; a caller that is *asserting* the type's validity replays them; a caller that is only
*asking* does not.

```java
TypeInfo TypeConstant.typeInfo();              // builds, caches, reports NOTHING - the question form
TypeInfo TypeConstant.ensureTypeInfo(errs);    // same build, and replays what it found - the assertion form
List<ErrorInfo> TypeInfo.diagnostics();        // what building it found
void TypeInfo.replayDiagnostics(errs);
```

Three properties already in the code are what made this safe:

1. **Replay is idempotent.** `ErrorList.log` deduplicates by `genUID`, so telling one listener twice
   records once. That is what lets *every* caller be told without producing duplicates - and it is
   precisely what the old design could not use, because the diagnostics were gone after the first
   build.
2. **"Partial" versus "final" was already distinguished.** `isComplete(info)` exists, and the build
   already collected into a branch and merged at the end. Only a *complete* TypeInfo keeps its
   record; a discarded partial build discards its branch, exactly as before.
3. **`TypeInfo` is `sealed permits TypeInfoReal`**, so there was one implementation to change.

**What was applied, in the order it had to go:**

| step | what | proof |
|---|---|---|
| 1 | Build records into the complete `TypeInfo`; the memoized fast path in `ensureTypeInfo(errs)` replays. | `TypeInfoDiagnosticsReplayTest` - three cases: every later caller is told, being told twice records once, a clean build records nothing. Mutation-checked: deleting the replay fails it. |
| 2 | `typeInfo()` added; all **14** `ensureTypeInfo(ErrorListener.BLACKHOLE)` call sites migrated. | Semantically a no-op per site; XDK builds and the suite are unchanged. |
| 3 | `TypeInfoModeIsExplicitTest` gates the idiom out. | Mutation-checked: reintroducing one `ensureTypeInfo(BLACKHOLE)` call fails it. |

**Two deliberate departures from the plan as written, both toward less risk:**

- **The `invalidateTypeInfo()`-on-serious-errors hatch was KEPT.** The plan said step 1 would delete
  it. On reading it properly, it does more than "do not cache me": it also calls
  `pool.invalidateTypeInfos(this)`, which bumps a pool-wide invalidation count and forces *other*
  types to rebuild. Removing that is a scheduling change, not a caching change, and it is not needed
  for the recording to work. Serious errors therefore still invalidate and rebuild (and re-report, as
  they always did); the recording is what fixes the previously-silent case - a build whose
  diagnostics were below `ERROR`, which was cached and never spoken of again.
- **Step 3 became a gate rather than a rename.** Renaming `ensureTypeInfo(errs)` to say "assertion"
  would touch **142** call sites: pure churn, and a large future conflict surface against master, for
  a distinction the javadoc and the `typeInfo()` / `ensureTypeInfo(errs)` split already make visible
  at every call site. A source-shape gate keeps the property instead of restating it.

**What is still not done**, and is the remaining honest gap: a caller of `typeInfo()` gets no
diagnostics *and* leaves none for anyone else if it is the one that triggers the build - the record is
attached, but nobody has asked to hear it. That is correct for a fit test. It would be wrong if a type
were only ever built through `typeInfo()`, in which case its diagnostics sit on the TypeInfo unread.
Nothing observed does that, and the recording makes it *recoverable* where before it was lost, but it
is the reason this is called a better model rather than a finished one.

## 9. Silence is now something a call site asks for

The compiler used to blank the pool's listener for the duration of a compile:

```java
// generateInitialFileStructure, after registration
m_structFile.getConstantPool().setErrorListener(ErrorListener.BLACKHOLE);
...  // ~170 lines and four compiler stages later
// end of generateCode
m_structFile.getConstantPool().setErrorListener(m_errs);
```

Everything that reached the pool for a listener - every no-argument `ensureTypeInfo()`, everything in
§6 - was therefore silent for the whole compilation, and the field could never be final. The silence
was real and load-bearing: pointing the pool at the compiler's own listener without doing anything
else fails `lib_ecstasy` with twelve `VERIFY-67`/`VERIFY-70` errors out of half-built `TypeInfo`s.

But it was **three call sites**, not a phase. Tracing which callers actually reached the pool's
listener during an XDK build named them exactly:

| site | why it must be silent |
|---|---|
| `TypeConstant.getConverterTo` | a speculative query - every caller uses it as a predicate ("is there a conversion?") |
| `PropertyConstant.getValueType` | a metadata lookup; whoever asked for the type to be *validated* owns those errors |
| `PropertyClassTypeConstant.getPropertyInfo` | the same, and it **memoizes** - so reporting would hand the errors to whichever caller asked first, routinely a speculative `testFit`, and say nothing to any caller after it |

Each now passes `ErrorListener.BLACKHOLE` explicitly, with the reason at the site. The compiler sets
the pool's listener once, to its own, and never swaps it. Three named requests for silence replaced a
field blanked a hundred and seventy lines away from where it was restored.

### 9.1 A listener is never null

`null` no longer means anything. There are **zero** `errs == null` paths left in `javatools`:

- `XvmStructure.ensureErrorListener` is **deleted**. It turned `null` into the ambient listener, and
  nothing called it that way - `log(null, ...)` appears zero times in main or test source.
- `AstNode.log(errs, ...)` used to answer `severity >= ERROR` and **discard the diagnostic** when
  `errs` was null. It now requires one.
- Seven `errs == null ? BLACKHOLE : errs` coalescings are gone, and the eight literal `null`
  arguments that reached them (`TypeExpression.ensureTypeConstant()`, `ListExpression`,
  `MapExpression`, `TupleExpression`, `NonBindingExpression`, `AsExpression`, `RelOpExpression`) now
  pass `BLACKHOLE`. This was E32 stage 2's remainder: those sites are all in the
  `testFit`/`getImplicitType` family that stage 2 converted, and the coalescing was what let the
  stragglers survive.
- A convenience overload that wants no diagnostics says so by **being a different overload** -
  `TypeExpression.ensureTypeConstant()` passes `BLACKHOLE`, `new Parser(source)` passes `BLACKHOLE`.
  "I do not want the diagnostics" and "I did not think about them" no longer compile to the same
  call.

The proof is empirical, not a grep: `requireNonNull(errs)` on the paths that used to coalesce, then a
full `xdk:installDist` from clean. Every offender showed up as a stack trace naming its call site.

`FileStructure.getErrorListener()` also disagreed with `getConstantPool().getErrorListener()` for the
whole of a compilation. Master guarded its pool lookup with `poolCurrent != m_pool`, because it had a
listener *field* of its own that was meant to answer for its own pool. The field is gone; keeping the
guard left "my own pool is driving" falling through to `RUNTIME`. Now whoever is named answers, and
when nobody is named, the file's own pool does.

`Container.f_errs` was final already, but `null` meant "inherit", decoded on every call. Since the
field is final, there is no setter, and a parent always exists before the child that names it,
resolving the inheritance **in the constructor** gives the same answer - and leaves the field
genuinely non-null, `@NotNull`, `requireNonNull`-enforced, with an accessor that is one `return`.


### 9.2 Non-null is enforced, not just asserted

`@NotNull` on this branch means `requireNonNull` also enforces it - an annotation nothing checks is
a comment with syntax. Every field that holds a listener, and every constructor, setter and accessor
that takes or returns one, now carries both.

Making that true found three things a grep would not have:

- **`Launcher.m_errors` was `errors == null ? BLACKHOLE : errors`** - the same coalescing §9.1
  removed everywhere else, on the entry point every command-line tool goes through. It now requires
  a listener, and `launch(cmd, args, console)` / `launch(options, console)` / `new Compiler(options)`
  are the overloads that supply `BLACKHOLE`. Nine call sites were passing `null`; `requireNonNull`
  named every one of them by stack trace.
- **`EvalCompiler.m_errs` was non-final and null until `createLambda` ran**, so `getErrors()` threw a
  `NullPointerException` if anyone asked first. An EvalCompiler is one-shot - the debugger builds one
  per evaluation - so the list is now final and created with it.
- **`ModuleInfo.DirNode.errs()` could answer `null`**, while `DirNode` *is* an `ErrorListener`. It now
  falls back to the directory's own list when it has no source node yet, and `Node.log` goes through
  the virtual `errs()` so a DirNode's diagnostics still reach the source node for the declaration it
  stands for.

What is deliberately **not** annotated: the ~590 `ErrorListener errs` parameters threaded through
the compiler. They cannot be null - §9.1 proved that empirically - but annotating each one without a
`requireNonNull` behind it would be decoration. The enforcement lives at the boundaries where a
listener enters the system or is stored, and those are covered.
