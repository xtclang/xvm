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
| The engine silently swallowing a non-convergence where the CLI calls `logRemainingDeferredAsErrors()` | Read `runPhase` in full | **Not it** — the engine already calls it. This was briefly written up as a suspect on the strength of a truncated `grep`; the line was there all along |

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
