# `XtcEngine` compiles differently from the CLI — open investigation

**Status: not root-caused.** This is the log of what has been tried, so nobody repeats it. Every
row under "eliminated" was tested, not reasoned about.

The engine is the target path: a warm VM doing repeated sequential and then concurrent compiles,
without going through the launcher — the LSP compiler case. The CLI is **not** an alternative
architecture here; it is the *oracle* that says what the right answer is. So this divergence is the
blocker, not something to route around. Engine compiles are gated behind
`-Dxtc.plugin.engineCompile=true` in the Gradle plugin only to keep the default build green while
it is open.

## The symptom

`lib_json` is the smallest input found where the two disagree.

| Path | Result |
| --- | --- |
| `xcc -o <dir> -L <xdk>/lib lib_json/src/main/x/json.x` | **exit 0**, 0 diagnostics, `json.xtc` produced |
| `XtcEngine.compile(json.x)` | **exit 1**, 5 diagnostics |

```
COMPILER-137 (warning): The evaluating expression "buf" has a type of "TerminalType{type=Null}";
                        it always matches type "TerminalType{type=Null}".   json/Lexer.x:457
COMPILER-56  (error):   Could not find a matching method or function "add"
                        for type "StringBuffer?".                            json/Lexer.x:482
```

The declared type of a nullable local is being lost — `StringBuffer? buf = Null` is treated as
`Null` rather than `StringBuffer?` — so the narrowing after `buf = new StringBuffer()` never
happens and the later `buf.add(ch)` has nothing to bind to.

## Reproducer

`EngineCompilesLikeTheCliTest` (`javatools/src/test/java/org/xvm/api/`), `@Disabled` because it
currently fails. Enable it the moment this is fixed; it is the regression test.

## Eliminated — each tested, with the test

| Hypothesis | How it was tested | Result |
| --- | --- | --- |
| Concurrency between compiles | One compile, one thread | **Not it** |
| Warm-engine reuse / state from an earlier compile | Fresh engine, `lib_json` the only compile through it (`Booting engine plane ... ` then exactly 1 `on the warm engine`) | **Not it** — identical 5 errors |
| The module itself is uncompilable | Same source, same module path, through `xcc` | **Not it** — CLI exits 0 |
| A phase not iterating to a fixed point | The engine's `runPhase` already loops `0x3F` times, same as the CLI's `runCompilerPhase` | **Not it** |
| A phase *exhausting* without converging | Instrumented `runPhase` to print passes and outcome | **Not it** — all three phases converged (1, 3, 3 passes) |
| One shared `ErrorListener` across compilers, where the CLI gives each a per-node `ErrorList` (`node.errs()`), letting `isAbortDesired()` cut a phase short | Gave each compiler its own `ErrorList` tee'd into the collector | **Not it** — identical 5 errors |
| `ModuleInfo` deduction — the engine hardcoded `deduce=true` where the CLI passes an option | Set `deduce=false` | **Not it** — identical 5 errors. Changed anyway: the engine should take exact known paths, never deduce |
| NakedRef/turtle injected into the wrong repository (`injectNakedRefType(repoBuild)` vs the CLI's `injectNativeTurtle(repoLib)`) | Read both: the CLI does `extractBuildRepo(repoLib)` first, so both write the same build repo | **Not it** — functionally identical |
| The engine prelinking system libraries when the CLI does not | The CLI prelinks when `cSystemModules == 0`, which holds for a non-system module like `lib_json` | **Not it** — both prelink |
| The engine setting an `ErrorListener` on the **library** module's `ConstantPool` in its prelink (`struct.getConstantPool().setErrorListener(diagnosticSink)`), which the CLI's prelink does not do at all — a listener with different abort semantics could end a type resolution early | Removed the assignment so the prelink matches the CLI's | **Not it** — identical 5 errors |
| A nested vs flat library repository. The CLI builds one flat `LinkedRepository(true, BuildRepo, DirRepo…)`; the engine nests `LinkedRepository(true, repoBuild, repoLibrary)` where `repoLibrary` is itself a `LinkedRepository` with `readThrough=false` | Read both constructors; read-through stores a copy in the FIRST repo, and both end up caching into a build repo at the front | **Not established either way** — the shapes differ but no behavioural difference was demonstrated; left as a suspect, not an elimination |
| The ambient `ConstantPool` — the CLI establishing a current pool that the engine does not, with generation depending on it | Grepped every `getCurrentPool()` reference | **Not it** — all five are *comments* documenting that this branch deleted it; there are no live calls, and the CLI does not set one either |
| A prebuilt `json.xtc` on the module path, so the engine resolves against the built module instead of the one being compiled — this would also have invalidated the CLI baseline | Ran BOTH sides with a module path stripped of `json.xtc` | **Not it** — the engine fails identically with and without, and the CLI still succeeds without, so the A/B is sound |
| The engine silently swallowing a non-convergence where the CLI calls `logRemainingDeferredAsErrors()` | Read `runPhase` in full | **Not it** — the engine already calls it. This was briefly written up as a suspect on the strength of a truncated `grep`; the line was there all along |

## The narrowing that matters: it is `generateCode`, not validation

Instrumented the engine's phase sequence with the running error count:

```
linkModules          errors = 0
resolveNames         errors = 0
validateExpressions  errors = 0
generateCode         errors = 10     <-- all of them, on ONE pass, reporting done=true
```

**Validation passes cleanly.** Every diagnostic appears during code generation, and generation
converges on its first pass while logging them. That is a much narrower target than "the compile
paths differ": the AST validated, so the types were resolvable a phase earlier, and it is code
generation's own resolution that fails. `COMPILER-56 "could not find a matching method add for
type StringBuffer?"` has no business arriving in `generateCode` at all.

### Localized further: the engine never resolves the target type at all

Instrumented both `MISSING_METHOD` log sites to print the target type, its pool, and the phase,
then ran the engine and the CLI on the same source with the same lean module path:

| | `MISSING_METHOD` lookups | what it sees for `buf.add(...)` |
| --- | --- | --- |
| CLI (succeeds) | 88 | `type=Null` **and** `type=StringBuffer? nullable=true` |
| Engine (fails) | 668 | `type=Null` **only** |

Both compilers do a speculative lookup that sees `Null` - that one is normal and discarded. The
CLI *also* resolves the target as `StringBuffer?`, which is the lookup that succeeds. **The engine
never produces that resolution at all.**

The 668-vs-88 ratio says this is not one expression going wrong. Type resolution is broadly
degraded during the engine's `generateCode`, and `lib_json` is simply the first module whose code
depends on it enough to fail.

**A methodology note that nearly cost a false conclusion.** The first run of this comparison showed
the CLI with ZERO such lookups, which would have meant it never reaches the code path. That was
wrong: `xcc` runs the *installed* javatools jar, which did not yet contain the instrumentation.
The numbers above are from a rebuilt `installDist`. Any engine-vs-CLI comparison that instruments
javatools must rebuild the distribution first, or the CLI side measures unmodified code.

Next step from here: find where `buf`'s declared type is lost. Validation had it (validation
passes); generation sees `Null`. The question is what the engine's generation reads that the CLI's
does not.

### In-process is NOT the problem — the fault is the engine's driver

The CLI compiler was driven **in the same JVM** as the engine, via
`Launcher.launch("build", …)`, so one instrumentation build covered both and no installed jar was
involved:

```
=== CLI in-process ===      CLI rc=0        <- succeeds
=== ENGINE in-process ===   success=false, 5 diagnostics
```

That settles the question the whole comparison existed to answer. A warm JVM compiles `lib_json`
correctly through the CLI's driver; only `XtcEngine`'s driver fails. **The divergence is in the
engine's compile driver, not in compiling in-process, not in the environment, and not in the
module.** No further CLI comparison is needed - from here the work is engine-only.

Two harness notes, both of which produced a false signal first:

- `Launcher.launch` takes the command **`build`**, not `xcc`; `xcc` is a shell alias. Passing
  `xcc` returns 1 with a usage banner, which looks exactly like a compile failure.
- The CLI needs `xdk/.../javatools` on its module path as well as `.../lib`, or it fails with
  "Unable to load module: mack.xtclang.org" - the turtle. That also returns 1 and also looks like
  a compile failure.

### What is NOT wrong

`buf`'s register is created with the correct declared type. Instrumenting `Context.createRegister`
during an engine compile of `lib_json`:

```
REG buf type=StringBuffer?  host=Lexer            <- correct
REG buf type=StringBuffer   host=DocInputStream, DocOutputStream, Printer, TupleMapping
```

So the declared type survives into the register. The `Null` appears later, between there and the
method lookup in `generateCode`.

An earlier claim that "the engine is also compiling the ecstasy library" was **wrong** and is
retracted: it came from grepping a Gradle log that interleaved several compile tasks. The
in-process probe shows the engine compiling exactly the five `lib_json` classes, as it should.

### Ruled out inside the engine itself

- **Not duplicate compilation.** The engine builds exactly **one** `Compiler`, for
  `json.xtclang.org`. The same module is not compiled twice.
- **Not a skipped or stalled phase.** The compiler's stage advances correctly through every phase:
  `Registered -> Loaded -> Resolved -> Validated -> Emitted`. Validation genuinely ran and
  completed.

### The shape of what is left

`COMPILER-137` is raised by `CmpExpression.checkNullComparison`, which fires when
`exprTarget.getType().isOnlyNullable()` - that is, when `buf`'s expression type is literally
`Null`. That method is **validation** logic, and the phase instrumentation shows it running during
`generateCode`.

So: the register holds `StringBuffer?`, validation completed and advanced the stage, and then
during code generation a validation check runs again on `buf == Null` and sees `Null`. Either the
expression is re-validated at generation time against a context where `buf` still holds its
initial `Null` assignment, or generation reads a narrowing that validation had already discarded.

`eatString()` is the relevant shape: `buf` is declared `StringBuffer?` and assigned `Null`, then
assigned a real buffer inside `case '\\':` of a `switch` inside a `while`, and used after. The
narrowing that has to survive is "assigned in the then-branch, therefore non-null afterwards".

### Traced to the narrowing context, and to a pass-count difference

Tracing `CmpExpression.checkNullComparison` at the moment it fires, during an engine compile:

```
NULLCMP name=buf exprType=Null ctxVar=Null assign=AssignedOnce ctx=IfContext
  path= CmpExpression.validate <- Statement.validate x5
        <- MethodDeclarationStatement.generateCode <- TypeCompositionStatement.generateCode
        <- Compiler.generateCode
```

Two things fall out.

**"Errors only in generateCode" is not the anomaly.** The call path shows method bodies are
validated *during* code generation by design - `Compiler.generateCode` ->
`MethodDeclarationStatement.generateCode` -> `Statement.validate`. Both compilers do this. That
earlier framing is withdrawn.

**The context narrows `buf` to `Null`.** `ctxVar=Null` is the Context's own type for the variable,
against a `Register` that correctly holds `StringBuffer?`. In `eatString()` the declaration is
`StringBuffer? buf = Null` inside a `while` loop, and `buf` is assigned a real buffer inside one of
the `switch` branches. Narrowing it to `Null` at the top of the loop ignores the back-edge: on any
iteration after the first, `buf` can be a `StringBuffer`. The warning "always matches Null" is
therefore wrong, and the later `buf.add(...)` failure follows from it.

**The likely mechanism is the pass count.** Measured earlier:

| | `generateCode` passes |
| --- | --- |
| Engine | **1** - reports `done=true` while logging 10 errors |
| CLI | **2-3** |

A single pass cannot converge narrowing across a loop back-edge: the assignment inside the loop is
not yet known when the loop head is analyzed. The CLI iterates and converges; the engine declares
itself done after one pass and logs the not-yet-converged state as errors. `buf` is seen four times
during that pass, twice as `AssignedOnce` and twice as `Assigned`, which is the shape of an
analysis still in motion.

**The abort path is eliminated.** `Compiler.generateCode` returns
`m_mgr.processComplete()`, and `StageMgr` short-circuits to "complete" whenever
`getErrorListener().isAbortDesired()` - so an over-eager listener would produce exactly this
symptom. It is not what happens here:

- the engine's primary is `ErrorList.unlimited()`, i.e. `new ErrorList(Integer.MAX_VALUE)`, whose
  `isAbortDesired()` needs `m_cErrors >= Integer.MAX_VALUE` (or FATAL) - effectively never;
- its secondary for `compile(Path...)` is `BLACKHOLE`, which does not override `isAbortDesired()`
  and so takes the interface default, `false`;
- `TeeErrorListener` ORs the two, so the answer is `false`.

Note the engine's list is *less* eager to abort than the CLI's, which uses
`new ErrorList(MAX_NODE_ERRORS)` per node - the opposite direction from the symptom.

**So `processComplete()` returned true honestly: the StageMgr had nothing left to revisit.** The
engine's `generateCode` finishes in one pass because no node deferred itself, and it logs the
unconverged result. The CLI takes two to three passes because nodes there *do* defer and get
revisited.

### Measured: nodes defer during name resolution, and not at all afterwards

Instrumented `StageMgr.requestRevisit()` with a cumulative counter and printed it at every
`processComplete()` during an engine compile of `lib_json` (2804 calls):

```
Resolved   complete=true  revisitLeft=0  revisitRequests=477
Validated  complete=true  revisitLeft=0  revisitRequests=477   <- no new deferrals
Emitted    complete=true  revisitLeft=0  revisitRequests=477   <- none here either
```

The counter is cumulative, so it flat-lining across `Validated` and `Emitted` means **no node
deferred itself during validation or code generation**. One pass sufficed because nothing asked to
be revisited - the loop is not stopping early, it has nothing to do.

Deferral clearly works in this engine: 477 requests during name resolution, all satisfied. So the
mechanism is intact and the question is narrower again - what would have made a node defer during
validation, and why is that condition never met here?

**Next:** the `buf` narrowing is not obviously a deferral problem at all. `StringBuffer? buf = Null`
followed by a `while` loop that assigns a real buffer in one branch requires the loop's back-edge
to feed the loop head - a fixed point over the loop body, computed inside a single validation of
that method, not across StageMgr passes. Look at how `WhileStatement` merges its body's exit state
back into the loop head, and whether the engine reaches that merge at all.

**Next (superseded):** find why nodes do not defer in the engine. A node that cannot resolve yet is supposed to
put itself on the revisit list rather than log; something about the engine's setup makes them
resolve-and-log on the first attempt instead. `StageMgr.markLastAttempt()` is only called when
`fLastAttempt` is true, and on a first pass `runPhase` passes `false`, so the nodes should have
been in deferring mode.

## ROOT CAUSE LOCALIZED: narrowing state survives into the engine's second validation pass

### A 14-line reproducer

`javatools/src/test/resources/engine/NullableInLoopSwitch.x`. The CLI compiles it (exit 0); the
engine reports three diagnostics - the same COMPILER-137 / COMPILER-56 / COMPILER-117 as
`lib_json`.

```
module NullableInLoopSwitch {
    void run() {}
    String eatString() {
        StringBuffer? buf = Null;
        while (True) {
            Char ch = 'x';
            switch (ch) {
            default:
                if (buf == Null) { buf = new StringBuffer(); }
                buf.add(ch);
                break;
            }
        }
    }
}
```

Bisected from `lib_json`, checking at every step that **the CLI still succeeds** - the rule the
earlier rejected reduction violated. Both ingredients are load-bearing: removing the `switch`
(leaving loop + `if` + assign + use) compiles on the engine, and removing the `while` makes the
CLI fail too, so neither alone is the trigger.

### The divergence, instrumented on both

`Context.narrowLocalRegister` traced for `buf`, same source, same build:

| pass 2, at `if (buf == Null)` | `WhenTrue` | `WhenFalse` |
| --- | --- | --- |
| CLI | `from=StringBuffer? -> Null` | `from=StringBuffer? -> StringBuffer` |
| Engine | `from=Null -> Null` | `from=Null -> Null` |

The **first** pass is identical in both: the declaration narrows `buf` from `StringBuffer?` to
`Null`, the `if` splits it, and the body assigns `StringBuffer`.

The difference is the state at the **start of the second pass**. The CLI re-enters the loop with
`buf` at its declared `StringBuffer?`; the engine re-enters still carrying the `Null` left by pass
one. `from=` is `reg.getType()`, so the engine is being handed the *narrowed* register from the
previous pass rather than the declared one.

That is the bug: **the engine's narrowing state survives from one validation pass into the next,
where the CLI's is rebuilt.** With `buf` pinned at `Null` at the loop head, the back-edge can never
widen it, `WhenFalse` narrows `Null` to `Null` instead of to `StringBuffer`, and every diagnostic
downstream - including the COMPILER-117 about the `switch` arm - follows from that one wrong
premise. It also explains the 668-vs-88 failed-lookup ratio: a whole module validated against
types that never converged.

**Next:** find what the CLI resets between passes that the engine does not. The candidates are the
AST's own stage/validation state and the `Context` chain built for the method body - one of them is
rebuilt per pass in the CLI's driver and reused in the engine's.

## Also established

- Both paths build the module node tree through the same `ModuleInfo.getSourceTree(errs)` walk.
- The phase **order** matches: link → resolveNames → inject turtle → validateExpressions →
  generateCode.
- The engine's assemble round-trip happens *after* compilation, so it cannot produce compile
  diagnostics.

## Still open, in order of suspicion

1. **`flushAndCheckErrors` between phases.** The CLI stops between phases when errors have
   accumulated; the engine runs every phase unconditionally. This cannot manufacture the *first*
   error, but it can turn one into a cascade, and the two reported diagnostics may not both be
   primary.
2. **What the compiler is handed for the library modules.** The engine compiles against
   `LinkedRepository(true, repoBuild, repoLibrary)` where `repoLibrary` is the same repo its
   native container boots from; the CLI uses `ensureLibraryRepo()` plus a `ModuleInfoRepository`
   for output. If `ecstasy` arrives with different `TypeInfo` state in one path, nullable/`Ref`
   resolution is exactly the sort of thing that would differ — and `StringBuffer?` is a nullable
   over an `ecstasy` type.

## Design note: the module path is bound to the ENGINE, not to the compile

Asked while investigating, and worth writing down because it shapes everything above.

`repoLibrary` is a `final` field set in the constructor, so **one engine serves exactly one module
path**. Every `compile()` reuses it. That is deliberate and it is the point of a warm engine: the
repository, and the module structures it has loaded and linked, are the expensive state, and
rebuilding them per compile is what the CLI does and what warmth is supposed to avoid.

Two consequences follow, neither of which is the bug above but both of which matter:

- **The plugin needs one engine per distinct module path** — hence
  `Map<List<File>, XtcEngine> engines` in `IsolatedDirectExecutor`. In a build where many modules
  have different paths, that is many engines and correspondingly little reuse. The measured hit
  rate for the *classloader* cache was 20/21; the engine cache is keyed separately and was not
  measured.
- **State accumulates in that repository across compiles**: read-through caches a copy of every
  library module into the build repo, and `prelinkSystemLibraries` links those structures in
  place. This is NOT the cause of the `lib_json` failure - a fresh engine doing exactly one
  compile fails identically - but it is the obvious hazard once concurrent compiles share an
  engine, which is the next thing this work wants.

If a compile should be self-contained, the shape would be `compile(modulePath, sources…)` with the
engine caching repositories internally by path: same warmth, but the caller states what it means
each time instead of inheriting whatever the engine was constructed with.

## A rejected reduction — do not repeat it

A seven-line module was written to reduce this:

```
module NullableDeclProbe {
    void run() {
        StringBuffer? buf = Null;
        if (buf == Null) { buf = new StringBuffer(); }
        buf.add('x');
    }
}
```

It reproduces both diagnostics **on the engine and on the CLI**. Both compilers agree, so it
reduces a different question and is not a reduction of this one. **Any further reduction must be
checked against the CLI: if the CLI also fails, it is pinning the wrong thing.**


## The library is cloned on every compile, and need not be

Measured by timing `new FileStructure(module, false)` inside the read-through clone in
`LinkedRepository`, compiling `misc.x` three times on one warm engine:

| compile | clone of `ecstasy` | total | share |
| --- | --- | --- | --- |
| 1 (cold) | 257 ms | 1593 ms | 16% |
| 2 | 72 ms | 441 ms | 16% |
| 3 | 60 ms | 328 ms | 18% |

**Roughly a fifth of every warm compile is spent copying the ecstasy module**, and it recurs for
every compile forever. For an LSP recompiling on each edit that is the single largest avoidable
cost in the request.

### Why the clone exists

`LinkedRepository:119` says it plainly - *"create a copy, allowing the compiler to mutate the
repos[0] contents"*. Three things mutate a library module during a compile:

- `prelinkSystemLibraries` - `struct.getConstantPool().setErrorListener(...)` and
  `struct.linkModules(repo, false)`;
- `injectNakedRefType` - sets the NakedRef type on the pool of **every** module in the build repo,
  which includes the cloned library modules;
- `TypeInfo` population - validation builds and caches `TypeInfo` on library `TypeConstant`s.

So the library is not read-only because the compiler writes to it, and the clone is what keeps one
compile's writes out of another's.

### Why it could be

The first two are idempotent: the NakedRef type and the link result are the same every time, so a
library that was linked and injected **once per engine** would satisfy every later compile without
being touched again. The third is not merely harmless but valuable - a shared `TypeInfo` cache on
library types is work every compile currently redoes, and `ensureTypeInfo` was 12% of runtime
startup in the performance analysis.

The hazard is the one this branch already has machinery for: a compile that registers a NEW
constant into a shared library pool - which happens when user code parameterizes a library generic
- would leak that constant into every later compile. `ConstantPool`'s publication marker
(`markRuntimePublished` / `assertRegisterBeforeRuntimePublished`) exists to catch exactly that, and
would turn a silent leak into a loud failure at the point of the write.

So the shape is: link and inject the library once per engine, publish its pools, share them
read-only across compiles, and let the guard reject any compile that tries to write. That removes
the clone AND the repeated TypeInfo work. It is the same disease the performance analysis found on
the runtime side, where `NativeContainer.createFileStructure` re-merges the whole XDK per run -
one shared immutable library, many cheap per-request scratch areas.
