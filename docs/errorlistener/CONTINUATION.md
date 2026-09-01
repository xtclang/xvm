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
   isolated and why the no-arg calls are safe. Documented at `TypeConstant.ensureTypeInfo()` and
   pinned by `ConstantAdoptionListenerTest`.
6. **`ensureTypeInfo` builds and validates in one call, and that is why callers pass `BLACKHOLE`.**
   It is not a getter: it flattens the whole type, interns constants, memoizes, handles mutual
   recursion, and logs ~90 distinct diagnostics from `buildTypeInfo`. Because it memoizes, the
   diagnostics go to whichever caller asks FIRST - so a speculative `testFit` can both produce
   user-visible errors and leave the caller that actually cared hearing nothing. `BLACKHOLE` is
   therefore a **mode flag in disguise**: "this call is a question, not an assertion". README §8 has
   the full account, including why splitting compute from validate is a real refactor and not a
   rename. Do not start it casually.
2. **`log()` no longer carries control flow — this is DONE, do not "restore" it.** `log` returns
   `void`; `isAbortDesired()` is the only question about stopping, asked by the code that detects
   the problem. `ErrorListener.RUNTIME` no longer throws from inside `log` either. The consequence
   to know: a stateless lambda host cannot abort a compilation, not even on `FATAL`, and that is
   deliberate — an observer has no basis for stopping one. A host that wants to participate wraps a
   real `ErrorList`. README §5, pinned by `AbortIsNotLoggedTest`.
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
| null-guard sites (`errs == null`) | **0** (was 28, then 12) |
| `System.err` / `printStackTrace` | **25** (was 53); a grep finds 26 - the extra is `RuntimeErrorListener`'s own output |
| sites reading `log()` as control flow | **0** (`log` is `void`) |
| no-arg `ensureTypeInfo()` | **60**, and **0** of them in a method that has a listener in scope |
| `ErrorListener` params never used | 11, all base implementations of virtual methods (7 dead ones removed) |
| `BLACKHOLE` uses | rose again, deliberately: explicit beats implicit |

Verified after every change below: `./gradlew build` green; **796 tests, 0 failures** (63 skipped are
the standing `@Disabled` set); `xdk:installDist --rerun-tasks --no-build-cache` green with 22
`compileXtc` tasks and **no** `VERIFY-9x` / `RUNTIME-nn` diagnostic emitted.

## What was just done - do not redo

**1. Twenty-eight `System.err` sites now report through a listener** (8 `asm`, 13 `runtime`, 7
`javajit`). No threading was needed anywhere: a `Constant`/`XvmStructure` asks its pool, runtime code
asks `frame.container()`/`container()`, JIT builders ask `typeSystem.pool()`. New codes
`VERIFY-94`..`VERIFY-100` and a new `RUNTIME-01`..`RUNTIME-19` family, both in `asm/Constants.java`
and `errors.properties`. Table and the full "left alone, and why" list: README §6.

**2. Severity is the load-bearing part - README §7.** All but one report at `WARNING`. Two reasons,
and only the second still applies: at the time, `ErrorListener.RUNTIME` still **threw** at `ERROR`
and above (fixed in item 8); and independently of that, these are soft asserts that continue with a
defined answer on paths (`calculateRelation`, `calculateAssignability`, `adoptParameters`, `TypeInfo`
construction) that run constantly, so `ERROR` would make any compile that hits one give up.
`MethodStructure.assemble` is the exception: it does not recover, so `FATAL` then an explicit
`throw` - and since item 8 that `IOException` actually reaches the caller.

**3. The compiler no longer swaps the pool's listener - README §9.** It used to set `BLACKHOLE` after
registration and restore `m_errs` ~170 lines later, silencing every ambient ask for the whole
compile. That silence is real (pointing the pool at the compiler's listener with nothing else fails
`lib_ecstasy` with 12 `VERIFY-67`/`VERIFY-70` errors) but it was **three call sites**, found by
tracing which callers actually reached the pool listener during an XDK build:
`TypeConstant.getConverterTo`, `PropertyConstant.getValueType`,
`PropertyClassTypeConstant.getPropertyInfo` (this one **memoizes**, so reporting would serve
whichever caller asked first). Each now passes `BLACKHOLE` explicitly.

**4. A listener is never null - README §9.1.** Zero `errs == null` paths remain.
`XvmStructure.ensureErrorListener` deleted; `AstNode.log` no longer discards on null; seven
`errs == null ? BLACKHOLE : errs` coalescings and the eight literal `null` arguments feeding them
converted. `new Parser(source)` and `TypeExpression.ensureTypeConstant()` are overloads that pass
`BLACKHOLE` - wanting no diagnostics is a choice, not an absence. Proven empirically with
`requireNonNull` + a clean `xdk:installDist`, which named every offender by stack trace.

**5. `FileStructure.getErrorListener()` gap closed.** It answered `RUNTIME` even when its own pool
had a listener, because master's `poolCurrent != m_pool` guard protected a field that no longer
exists. It now answers with the named pool, or its own.

**6. `Container.f_errs` resolves inheritance in the constructor.** The field was final but `null`
meant "inherit", decoded on every call. Since there is no setter and a parent always precedes its
child, resolving once is equivalent - and the field is now genuinely non-null, `@NotNull`,
`requireNonNull`-enforced, accessor is one `return`. Same annotation treatment on
`ConstantPool.m_errs` (which stays settable for a real reason: a library pool is re-pointed at
whichever engine uses it).

**7. Small things asked for along the way.**
- `Expression.testFitExhaustive` deleted; `testFitMultiExhaustive(ctx, errs, TypeConstant...)` takes
  trailing varargs, so the listener moved before them (a varargs parameter must be last - the same
  reason `ErrorListener.log` needed a separate varargs overload). The wrapper existed only to write
  `new TypeConstant[] {typeRequired}`.
- `ModuleInfo.Node`'s `ErrorList` is created with the node instead of lazily; four null checks and a
  mutating `errs()` gone.
- `javatools_jitbridge/.../nType.java`: the module's only two `unchecked` warnings, both raw
  `java.lang.Class` -> `Class<?>`; `:javatools_jitbridge:compileJava` no longer prints the note.
  (The `java.lang.String`/`Class`/`Exception` qualification in that file is **forced** - the package
  deliberately shadows those names with Ecstasy types - and the file is hand-written, not generated.)
- `CompilerThisEscapeConstructionTest` anchors on `"private Parser("` rather than the full signature,
  which broke when the parameters gained annotations. The test is about the constructor body.

**8. Recording and aborting are separate questions - README §5, the campaign's headline fix.**
`ErrorListener.log` is now `void`. Only **three** call sites in the codebase read its result as
control flow (`Lexer` x2, `Parser` x1) and two more read it indirectly through `fHalt |= log(...)` in
`validate()` (`Annotation`, `ImmutableTypeConstant`); all five now log and then ask
`errs.isAbortDesired()`, which is exactly what `log` used to return. `ErrorListener.RUNTIME` prints
instead of throwing `IllegalStateException` from inside `log`, so the runtime behaves like the
compiler and `MethodStructure.assemble`'s `IOException` actually reaches its caller. The
`CompilerException` message no longer claims "error list is full" when a `FATAL` was the reason.

The cost, stated because it is a real semantic change: a **stateless lambda host can no longer abort
a compilation**, not even on `FATAL`, because it cannot answer `isAbortDesired()`. That is the point
- previously it could abort *by accident*, and it had no correct value to return. A host that wants
to participate wraps an `ErrorList`; a host that wants to watch writes
`err -> seen.add(err.getCode())`.

`AbortIsNotLoggedTest` (5 tests) pins all of it against the real `Parser`, and was mutation-checked:
replacing the abort condition with `false` makes it fail.

**9. The three follow-ups are done.**

- **`ensureTypeInfo()`** - fifteen of the 75 converted, and **zero** no-arg calls now remain in a
  method that has a listener in scope. The rule for picking them is README §8.4's: *is this call
  asserting the type is valid, or asking whether it is?* Two got the caller's listener
  (`CmpExpression.checkConstType`, `ArrayAccessExpression.validate` - both run from `validate`); the
  other thirteen got an explicit `BLACKHOLE`. The remaining 60 stay: adoption makes the asker the
  owner (§2.3), and E35 D's argument is unchanged.

  Note the first scan for this was **wrong** - a line-based regex for the enclosing method missed
  multi-line signatures and reported 5 candidates when there were more. The brace-aware scan found
  the rest. If you redo this, walk braces.

- **Seven dead `ErrorListener` parameters removed** (`RelOpExpression.guessLeftType`,
  `selectRightType`, `getImplicitMethod`; `ArrayAccessExpression.determineIndexType`,
  `findArrayAccessor`; `ToIntExpression.getExtractor`, `getConvertMethod`). All searches or guesses
  that took a listener and never used it, because every call inside them already passed `BLACKHOLE`.
  §8 settles which way to resolve that: a guess that comes out "no" is an answer, so the parameter
  goes rather than the silence. Eleven such methods remain and **must** keep the parameter - they
  are base implementations of virtual methods whose overrides use it.

- **Severities re-decided, now that §5/§7 no longer force `WARNING`.** Evidence first: a full clean
  `./gradlew build` (23 `compileXtc`, 23 `testXtc`) emits **zero** `VERIFY-9x` and **zero**
  `RUNTIME-nn`, so none of these fires in normal operation and severity is honesty, not risk.
  Five raised to `ERROR` (`Frame.resolveType`, `MainContainer.invoke0`,
  `ClassTemplate.markNativeMethod`/`markNativeProperty`, `AugmentingBuilder.assembleMethod`) - the
  caller's operation did not happen. Two lowered to `INFO` (`Frame`'s `-DDEBUG=all` wrapping notice,
  `xException`'s detail dump) - developer tracing. The rest stay `WARNING`: they recover with a
  defined answer, and `ERROR` would make a compile that hits one give up. README §7 has the rule.

- **`@NotNull` where `requireNonNull` enforces it** - every listener field, and every constructor,
  setter and accessor that takes or returns one. That pass found three real defects, not just
  missing annotations: `Launcher.m_errors` was still `errors == null ? BLACKHOLE : errors` (the entry
  point every CLI tool goes through - nine call sites were passing null, all named by stack trace);
  `EvalCompiler.m_errs` was non-final and null until `createLambda` ran, so `getErrors()` NPE'd if
  asked first; `ModuleInfo.DirNode.errs()` could answer null while `DirNode` *is* an ErrorListener.
  New overloads supply BLACKHOLE by being a different overload: `Launcher.launch(cmd, args, console)`,
  `Launcher.launch(options, console)`, `new Compiler(options)`.
  The ~590 `ErrorListener errs` *parameters* are deliberately not annotated - they cannot be null
  (proved in item 4), but an annotation with no `requireNonNull` behind it is decoration. Enforcement
  lives at the boundaries.

**10. The three follow-ups are closed - none of them was a backlog item after all.**

- **The "an embedder cannot route runtime diagnostics" item was wrong** - I wrote it without
  checking. `NativeContainer.create(runtime, repository, errs)` has taken a listener all along, and
  every nested container inherits it at construction, so naming it once covers the whole world under
  it. `XtcEngine` already passes its `diagnosticSink` there. The only real gap was that
  `InterpreterConnector` hardcoded `ErrorListener.RUNTIME` with no way for its caller to say
  otherwise; it now has a two-argument constructor, and the one-argument one documents that it
  prints.

- **`ConstantPool`'s `implicit.x` bootstrap** genuinely has no owner - it runs in a static
  initializer before any pool instance exists. What it could stop doing is printing the evidence and
  then throwing `new IllegalStateException()` with **no message**. The throw now carries the errors.

**11. `ensureTypeInfo` is split into a question form and an assertion form - README §8.4.**
`TypeInfo` now carries the diagnostics its own construction produced. `typeInfo()` builds, caches and
reports nothing; `ensureTypeInfo(errs)` does the same build and replays what it found, including on
the memoized fast path - which is the actual bug fixed: before, the diagnostics went to whoever asked
FIRST and to nobody after. All **14** `ensureTypeInfo(ErrorListener.BLACKHOLE)` sites became
`typeInfo()`, and `TypeInfoModeIsExplicitTest` gates the idiom out.

Two deliberate departures from the plan, both toward less risk, both written up in §8.4:
- the `invalidateTypeInfo()`-on-serious-errors hatch was **kept**, not deleted. It also bumps a
  pool-wide invalidation count that forces other types to rebuild, so removing it is a scheduling
  change rather than a caching one, and the recording does not need it gone;
- step 3 became a source-shape gate rather than a rename of `ensureTypeInfo(errs)`. The rename would
  touch 142 call sites for a distinction the `typeInfo()` split already makes visible.

Both new tests are mutation-checked: delete the replay and `TypeInfoDiagnosticsReplayTest` fails;
reintroduce one `ensureTypeInfo(BLACKHOLE)` and `TypeInfoModeIsExplicitTest` fails.

## Next task

Nothing in this campaign is unfinished. One honest gap remains, recorded at the end of README §8.4:
a type built *only* through `typeInfo()` has its diagnostics recorded but unread, because no caller
asked to hear them. That is right for a fit test and would be wrong if some type were never reached
by an asserting caller. Nothing observed does that, and the record makes it recoverable where it used
to be lost - but it is why §8.4 claims a better model rather than a finished one.

## Watch out for## Watch out for

- `./gradlew build --rerun-tasks` races `:javatools:test` against `:xdk:installDist` and fails
  `TypeComparisonCorpusTest` with an `EOFException` on a half-written `.xtc`. That is the documented
  race, not a regression - run them separately to confirm. Plain `./gradlew build` is fine.

## Master-bug PR status (verified against GitHub 2026-09-01)

- **Merged:** #547 (row 27), #548 (29), #550 (30), #556 (31), #558 (33), #560 (35), #563 (36),
  #564 (37+38), plus #561 and #562.
- **Open:** **#549** (row 28, `MethodStructure` volatile publication), **#559** (row 32,
  `Format.TimeZone` end to end) and **#566** (row 19, implicit-identity cache concurrency).
  Nothing is blocked on any of them.
- **#557 (row 34) was CLOSED unmerged** - and the fix is on master anyway, because the maintainer
  applied it directly: *"I don't think there is a reason to clutter the repo with such a trivial
  typo fix. I pushed it as a minimal change."* Treat that as filing guidance: **a one-word fix on
  its own is not worth a PR here.** Bundle by theme.
- **Rows 1-26 are all still unfiled.** `plans/master-issue-submissions.md` carries the per-row
  readiness; the nine marked "Ready after manual review" (source-only clean, red-on-master test
  already written) are rows 1, 2, 3, 4, 7, 8, 14, 15 and 19.

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
