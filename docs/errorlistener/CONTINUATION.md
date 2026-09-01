# Continuation prompt — ErrorListener work

Paste everything below the line into a fresh session.

---

Continue the **ErrorListener threading campaign** in `/Users/marcus/src/xtclang0`, branch
`lagergren/lazy-instance`. Tree is clean at `01cdacc23` (the listener work ends at `9653e8c7d`; later commits are docs and
unrelated PR maintenance). Last full run was **791 tests, 0 failures**; xdk builds; `array.x`
and `numbers.x` clean.

## Read these first

- `docs/errorlistener/README.md` — the design doc: what was wrong before, the ownership rule,
  container inheritance, the two sinks, worked API examples, and the `log()`-returns-control-flow
  hazard.
- `docs/reentrancy/plans/master-enhancement-submissions.md` — rows **E32** (thread one listener),
  **E34** (pass it to resolution, done), **E35** (finishing: parallel gap, mutable fields,
  `withListener`).
- `docs/reentrancy/plans/master-issue-submissions.md` — filed master bugs.

## What is already done — do not redo

- **Ownership, not ambient lookup.** `ConstantPool` owns the compile-side listener; `Container` owns
  the runtime-side one and **inherits from its parent** when unset (`f_errs == null` → walk
  `f_parent` → root answers `RUNTIME`). `Container`'s field is `final` and constructor-injected.
- **Deleted:** `XvmStructure.setErrorListener` (it mutated the *parent's* state),
  `FileStructure.setErrorListener` and its transient field, `ResolutionCollector.getErrorListener`
  (E34), `Container.setErrorListener`, and **all five listeners parked on an object for a callback**
  (`NameResolver`, `ForStatement`, `WhileStatement`, `ForEachStatement`, `TryStatement`).
- **E34 done:** `resolveName`, `resolveContributedName`, `resolvedComponent`, `resolvedConstant` all
  take the listener.
- **E32 stage 2 done:** 66 literal `null` arguments to `testFit`/`testFitMulti`/`getImplicitType`
  became explicit `ErrorListener.BLACKHOLE`; the three silent `null → BLACKHOLE` translations are gone.
- **`suppressCascade()`** names the eight `TypeConstant` sites that used to overwrite the caller's
  listener. **This intent is correct** — cascade suppression is standard; do not "fix" it by making
  them always report, that regresses into error cascades.
- **Two sinks, deliberately separate:** `compile(listener, …)` = one compile's diagnostics;
  `builder().diagnosticSink(…)` = work no compile owns (library pools, container). They cannot be
  merged without reintroducing shared mutable state.
- **Decorators:** `Slf4jErrorListener`, `JfrErrorListener` (+ `DecoratingErrorListenerTest`).
- **`ErrorList`:** `DEFAULT_MAX_ERRORS`, `firstError()`, `unlimited()`; every bare numeric is either
  named or expressed as intent.
- Tests: `XtcEngineTest` (8, incl. parallel isolation + two-sink separation),
  `ConstantAdoptionListenerTest`, `DecoratingErrorListenerTest`.

## Facts that cost effort to establish — trust these, don't re-derive

1. **Constants are adopted into the pool that registers them** (`ConstantPool.register`:
   `constant.adoptedBy(this)`). So a compile referencing a library type owns that constant, and
   `ensureTypeInfo()` resolves to the *compile's* listener. This is why parallel compiles are
   isolated and why the 75 no-arg calls are safe. Documented at `TypeConstant.ensureTypeInfo()` and
   pinned by `ConstantAdoptionListenerTest`.
2. **`log()`'s return value is compiler control flow.** `ErrorList.log` returns `isAbortDesired()`,
   which is true on `FATAL`; `Parser`/`Lexer` do `if (errs.log(...)) throw new CompilerException`.
   A host lambda returning `false` suppresses a legitimate abort; returning `true` invents one.
   See README §5 — recommendation is to separate the two questions.
3. **`Lazy.ofBound` exists because of `-Xlint:this-escape`**, which is *fatal* on this branch.
   `Lazy.of(() -> this.compute())` in a field initializer captures `this` during construction.
   Master does **not** have that lint fatal.
4. **`Bound` is already on master** (landed via #377); branch and master `Lazy.java` were identical
   before the resettable work.
5. `@NotNull` **is** available to `javatools` (`compileOnly(libs.jetbrains.annotations)`), already
   used ~7 times. It enforces nothing — `requireNonNull` does.

## Current measurements

| | |
| --- | --- |
| `ErrorListener` fields | 14, **12 final** |
| null-guard sites | **12** (was 28) |
| `System.err` / `printStackTrace` | **53** (unchanged) |
| no-arg `ensureTypeInfo()` | **75** |
| `BLACKHOLE` uses | 151 (rose deliberately: explicit beats implicit) |

The two non-final fields are `ConstantPool.m_errs` (the compiler's registration silence) and
`XtcEngine.Builder`'s (a builder — correct).

## Next task

**Convert the `asm`/`compiler` `System.err.println` sites to report through a listener, with any
throw made explicit** — the pattern being `errs.log(Severity.FATAL, CODE, …)` followed by an
explicit `throw`, rather than relying on `log()`'s return value.

The unlock: most of those classes are `Constant`s or `XvmStructure`s, so they can reach a listener
with **no threading** via `getConstantPool().getErrorListener()`.

Candidates verified as reachable that way:

```
asm/constants/TypeConstant.java:6037      calculateRelation      "rejecting isA() due to a recursion"
asm/constants/ParameterizedTypeConstant.java:289   resolveTypedefs   "// TODO: soft assert"
asm/constants/ParameterizedTypeConstant.java:479   adoptParameters
asm/ClassStructure.java:1765              calculateAssignability "// soft assert"
asm/ConstantPool.java:3723                checkTupleCompatibility
asm/MethodStructure.java:2157             assemble
asm/constants/TypeInfoReal.java:1544/1568/1759
asm/constants/MethodInfo.java:1134        populateCache
```

**Do not convert these two** — checked, and both should stay:
- `compiler/ast/NameExpression.java:1188` — a developer TODO marker (`"TODO: AST for "`), not a
  diagnostic.
- `compiler/ast/ConvertExpression.java:105` — carries its author's note that it is *"not a compiler
  diagnostic"* and that changing it needs focused tests.

The remaining ~36 are in `runtime`/`javajit`/`tool`, in handle constructors and `CallChain` that run
outside any frame and so have **no owner to propagate from**. That is a separate, smaller problem —
giving those an owner — not part of this task.

## Also open (as of 2026-09-01)

- **PR #560** (index narrowing, master bug 35) — OPEN, MERGEABLE, APPROVED, review thread resolved.
  Was conflicting; rebased onto master keeping all three `array.x` tests
  (`testDeleteRange`, `testSliceIdentity`, `testIndexBounds`).
- **PR #562** (OSDirectory single-lookup, plus `Lazy.Bound.reset()` / `Lazy.ofResettable`
  piggybacking) — OPEN, MERGEABLE, both review threads resolved.
- **#561, #563, #564 are merged** — master bugs 36, 37 and 38 have landed.

Nothing is blocked on these; they are listed so a new session does not re-derive their state.

## Working rules for this repo

- **Never `cd` into a subdirectory in a Bash command.** Always `cd /Users/marcus/src/xtclang0` and
  use paths relative to the root. `./gradlew` from a subdir prints nothing matching `error:`, so
  `grep -c 'error:'` returns **0** and a failing build reads as clean. This has faked a clean build
  repeatedly.
- Verify from the repo root; read test results from the JUnit XML, not the console.
- `./gradlew clean` runs alone. Never combine test tasks with `xdk:installDist`.
- **Exact-string replaces with assertions**, never computed line ranges, never regex across nested
  parens — both have corrupted files in this campaign, and a global replace once rewrote a factory's
  own body into infinite recursion.
- **Never resolve a merge conflict by overwriting a file wholesale.** Doing that twice discarded
  newer master work (`Lazy.java`, then `LazyTest.java`, where master had 292 lines and the older
  base had 215). Check what each side actually contains first.
- **Verify with a check that can actually fail.** `grep -c Unhandled` said 0 on an `array.x` that
  did not parse at all, because a `PARSER-02` error is not an unhandled exception. Grep for the
  thing that should be there, not only for the thing that should not.
- Never push or open/modify PRs without explicit permission.
- No Hungarian notation in new classes; fields at the top; `@NotNull` only where `requireNonNull`
  also enforces it.
- No "Generated with Claude Code" or `Co-Authored-By` trailers.
