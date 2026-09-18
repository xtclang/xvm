# Threading `errs` through the compiler

Scoping document for making `ErrorListener` an always-present, non-null, immutable part of the
compiler's call stack, so that an embedding host — an LSP server above all — can rely on hearing
every diagnostic the compiler produces.

**Status.** All seven phases are implemented on `lagergren/errs`. What remains is one decision and
the work that follows from it; see *What is left*.

Measurements throughout are of `origin/master` at `794bf23e6` - the state this was written against,
kept as the record of what was wrong. Where that has since changed:

| | before | after |
|---|---|---|
| null-coalescing sites | 33 | 8, all in the deferred ownership area |
| names for the listener | 6 | 1 - `errs` |
| kinds of silence | 1 undifferentiated | 133 `PROBE`, 6 `BLACKHOLE`, 10 `suppressCascade()` |
| fields using null as a state flag | 4 | 0 |
| `new Object[]` at report sites | 31 | 0 |

## Why

An LSP server needs three things the current design cannot promise:

1. **Every diagnostic reaches the host.** Today a listener is optional at most boundaries, so
   "I deliberately discard errors" and "I did not think about errors" compile to the same call.
2. **Diagnostics are attributable to a request.** A listener that can be replaced through a setter
   on a shared structure is not owned by the request that created it.
3. **Speculative work is silent without being invisible.** The compiler probes constantly —
   "would this expression fit this type?" — and those failures are answers, not errors. They must
   not reach the host, but they must be distinguishable in the source from genuine indifference.

The prior art is the experimental branch `lagergren/lazy-instance` in a sibling checkout, which did
this work and wrote it up at `docs/errorlistener/README.md` (690 lines) and
`docs/reentrancy/plans/master-enhancement-submissions.md` (rows E32, E34, E35, E47). That branch is
a much larger "make the source beautiful" effort; the errs portion is roughly 1,500–2,500 lines of
real signal out of a ~123k-line diff, so it is separable, but it is not cherry-pickable — the errs
edits sit in files that were heavily churned for unrelated reasons. This document is a
re-derivation, not a port.

## What was true, and what the phases changed

| | |
|---|---|
| Files mentioning `ErrorListener` | 137 of 804 (17%) |
| Method/constructor declarations taking one | 586 |
| Parameters already named `errs` | 587 of 676 (87%) |
| Fields holding one | 15 — 11 final, 4 mutable/transient |
| `ErrorListener.BLACKHOLE` references | 80, in 33 files |
| `branch(` / `merge()` call sites | 44 / 56 |
| Null-check lines involving a listener | 35 |
| `@param errs … (optional)` javadoc lines | ~29 |

**The type change is nearly free.** The parameter is already non-null in practice almost
everywhere, and `errs` is already the name. Three places already reject null outright —
`Lexer.java:46`, `Parser.java:59`, `compiler/Compiler.java:36`. The invariant exists; it simply is
not propagated.

**The real work is small and concentrated**: about 13 null-substitution sites
(`errs == null ? BLACKHOLE : errs`) and 10 conditional-logging sites (`if (errs != null)`), across
roughly 16 files.

**Most `BLACKHOLE` use is legitimate and must survive.** Of the 80 references, ~52 are speculative
probes — `testFit`, `getImplicitType`, `resolveRawArgument`, `isNewable`, `selectCommonType` — where
the *return value* is the answer and the diagnostics are genuinely unwanted. Only ~7 are
null-substitution placeholders.

### Where the actual design questions live

These are not mechanical, and they are the reason this is a plan rather than a sweep.

**1. "Absent listener" currently means two opposite things.**
`XvmStructure.ensureErrorListener(errs)` and `FileStructure.getErrorListener()` fall back to
`ErrorListener.RUNTIME`, which *throws* on `Severity.ERROR`. Meanwhile `compiler/Compiler.java:125`
parks the file structure on `BLACKHOLE`, which *swallows*, and resets it to `null` at `:293`. So the
same missing listener aborts compilation or silently discards, depending only on timing.

**2. Four fields use `null` as a state flag, not as "no listener".**
`ForStatement.m_errsLabelVars`, `ForEachStatement.m_errsLabelVars`, `WhileStatement.m_errsLabelVars`
and `TryStatement.m_errsValidatingFinally` are set on entry to a validation region and cleared on
exit; `null` means "not currently inside it". Annotating these `@NotNull` would be wrong — they need
a different idiom, not a non-null type.

**3. A setter can silence a subtree it does not own.**
`XvmStructure.setErrorListener(errs)` delegates the mutation *to its parent*, so any structure can
redirect the diagnostics of its whole containment tree. It has exactly two call sites, both in
`compiler/Compiler.java`.

**4. `merge()`'s default implementation throws.**
`ErrorListener.merge()` is a `default` method whose body is
`throw new UnsupportedOperationException("nothing to merge")`. Any listener that is not an
`ErrorList` — including the lambda an LSP server would naturally write, and including
`tool.Launcher` and `ModuleInfo.Node` — blows up if `merge()` reaches it.

**5. `branch()`'s default budget is 1.**
`ErrorListener.branch(node)` defaults to `new ErrorList.BranchedErrorListener(this, 1, node)`,
while `ErrorList.branch()` overrides it to propagate `f_cMaxErrors`. A method body is validated
through such a branch, and a branched listener reports `isAbortDesired()` as soon as its budget is
spent — so a custom listener that inherits the default makes the compiler **abandon validation of a
method after its first error**. The prior-art branch hit exactly this and had to override it.

**6. The CLI drains errors by downcast.**
`tool/Compiler.java:328-337` flushes a stage's errors with `if (errs instanceof ErrorList list)`.
A listener that is not an `ErrorList` is silently skipped. Relatedly, `:649` identity-compares
`m_errors != ErrorListener.BLACKHOLE`, which becomes meaningless once `BLACKHOLE` stops being the
null stand-in.

## The target contract

- A listener is **never null**. Not "is asserted non-null" — cannot be null, because nothing
  produces one.
- It is **taken as a constructor parameter and stored in a final field**, or passed as a parameter
  named `errs`. It is never found ambiently and never replaced.
- **Silence is requested, not defaulted.** A caller that wants no diagnostics says so by naming a
  silent listener, and the name says which kind of silence it means.
- **Speculation branches or probes; it does not coalesce.** A trial whose failure the user should
  hear about if every alternative also fails uses `branch()`/`merge()`. A trial whose failure *is*
  the answer uses a named silent listener.
- **`@NotNull` means enforced.** An annotation with no `requireNonNull` behind it is a comment with
  syntax. Enforcement lives at the boundaries where a listener enters the system or is stored — not
  on all 586 parameters, which would be decoration.

## Plan

Seven phases, each independently reviewable and revertable, ordered so the mechanical work lands
before anything semantic. Phases 1-3 are worth doing even if the rest is deferred.

### Phase 1 — Fix the diagnostic deduplication bug *(independent, do first)*

`ErrorInfo.genUID()` builds a key that `ErrorList.log` uses to drop duplicates — and a collision
drops the diagnostic entirely, since the whole body is inside `if (f_setUID.add(uid))`. The key is
wrong twice:

```java
    .append(m_lPosStart)
    .append(':')
    .append(m_lPosStart);     // <- the second is meant to be m_lPosEnd
...
    .append(Arrays.hashCode(m_aoParam));   // 32-bit digest of the parameters
```

Two diagnostics about an expression and about the larger expression containing it start at the same
character, so this is not a contrived shape. Demonstrated against `origin/master`:

```
logged 2 diagnostics with different end positions
  errors retained : 1
     [1:1..1:11] PARSER-03: Expected token a; found b. ("")
```

The second diagnostic is not reported, not counted, and nothing indicates that anything was
suppressed. The user fixes the reported error, recompiles, and is shown the next one — so it
presents as a compiler that reports errors one at a time rather than as a bug.

Two one-line edits, no signature change, plus a test. This is the highest value-per-line change in
the document and does not depend on anything else here.

### Phase 2 — A reporting API that can be called without ceremony *(additive)*

Today every diagnostic is reported through one of two positional overloads:

```java
boolean log(Severity severity, String sCode, Object[] aoParam, Source source, long start, long end);
boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs);
```

which forces call sites to write the parameters as an explicit array:

```java
errs.log(Severity.ERROR, Compiler.NAME_UNRESOLVABLE, new Object[]{getValueString()}, this);
```

There are **31 `new Object[]` sites in 13 files**, and of 122 `log(Severity…)` call sites **30 pass
`null`** where the array would go. Varargs cannot simply be added: `aoParam` sits in position three,
*before* the location arguments, so `log(Severity, String, Object...)` is ambiguous with
`log(Severity, String, Object[], XvmStructure)` — and the 30 sites passing a literal `null` would
become ambiguous too.

The fix is to make the location a **type** rather than a variable-length tail, which frees the tail
for the parameters. Small, sealed, three cases:

```java
sealed interface Site {
    record At(XvmStructure xs)                          implements Site {}
    record In(Source source, long lPosStart, long lPosEnd) implements Site {}
    // plus a NONE for diagnostics with no location
}

static Site at(XvmStructure xs);
static Site in(Source source, long lPosStart, long lPosEnd);
```

which makes the parameters a genuine tail, and lets the severity read as the verb:

```java
default boolean log  (Severity severity, String sCode, Site site, Object... aoParam);
default boolean error(String sCode, Site site, Object... aoParam);
default boolean warn (String sCode, Site site, Object... aoParam);
default boolean info (String sCode, Site site, Object... aoParam);
default boolean fatal(String sCode, Site site, Object... aoParam);
```

```java
errs.error(Compiler.NAME_UNRESOLVABLE, at(this), getValueString());
```

**This phase is purely additive.** The two existing overloads stay and keep working, so nothing has
to move; only the 31 `new Object[]` sites are migrated, as a demonstration and to retire the worst
of the noise. The remaining ~90 call sites migrate opportunistically, or never.

**Why a type and not just more overloads.** `Site` is also the thing an LSP server needs. An LSP
`Diagnostic` is a range plus a severity plus a message, and today a listener receives the location
as either a `Source` and two `long`s or an `XvmStructure`, and has to work out which. A sealed
three-case type is directly switchable:

```java
ErrorListener toLsp = err -> publish(switch (err.site()) {
    case Site.In in -> range(in.source(), in.lPosStart(), in.lPosEnd());
    case Site.At at -> rangeOf(at.xs());
    case Site.None n -> WHOLE_FILE;
});
```

That is the shape the prior-art branch arrived at, and the reason it could offer decorators —
a tee, an SLF4J sink, a JFR sink — as ordinary listeners. Those are **not** in scope here; the point
is only that this phase does not paint us out of them.

**Severity is decided, not inherited.** Worth writing down as the convention while adding the verbs:
report at `WARNING` when the compiler recovers, `ERROR` when the caller's operation failed, `INFO`
when only tracing, and `FATAL` immediately before throwing. Never make control flow depend on what
the listener does with it.

### Phase 3 — Make the two traps safe for a custom listener

Both bite the moment someone writes the LSP listener, and neither is a sweep.

- Give `merge()` a default that is safe on a non-branched listener, or make branching the only way
  to obtain something mergeable.
- Make `branch()`'s default budget agree with `ErrorList`'s rather than silently being 1.

### Phase 4 — Never null *(the mechanical core)*

- Delete the ~13 `errs == null ? BLACKHOLE : errs` substitutions and the ~10 `if (errs != null)`
  guards, in `StageMgr`, `Expression`, `InvocationExpression`, `TypeExpression`, `ElvisExpression`,
  `NameExpression`, `LambdaExpression`, `NewExpression`, `Launcher`, `api/EmbeddingSupport`.
- Add `requireNonNull(errs, "errs")` at the boundaries only: the constructors that store one, and
  the public entry points (`EmbeddingSupport.compile`/`run`, the six `tool/` launcher constructors,
  `Launcher.launch`).
- Annotate those boundary parameters and the fields `@NotNull`
  (`org.jetbrains.annotations`, already wired as `compileOnly` in `javatools` and
  `javatools_utils`; the existing house pattern is `Lazy.java` — `requireNonNull(x, "x")`).
- Convert every literal `null` argument into a named silent listener, so the call site states its
  intent.
- Delete the ~29 `@param errs … (optional)` javadoc lines, which will then be false. Two are
  already false today: `ModuleInfo.getSourceTree` and `Context.markVarWrite` both document `errs`
  as optional and dereference it unconditionally — there is a test asserting the resulting NPE.

**Prove it the way the prior-art branch did**: add the `requireNonNull` calls first, then run a
clean `./gradlew xdk:installDist`. Every remaining offender names itself in a stack trace. Do not
rely on grep — that branch found a site (`Expression.testFitAsType`) whose behaviour silently
depended on whether its caller passed null.

### Phase 5 — Single ownership, no setters

Done:

- Deleted `XvmStructure.setErrorListener`, which delegated the mutation to its *parent*, so any
  structure could redirect the diagnostics of its whole containment tree. Removing it broke exactly
  one line - the `@Override` on `FileStructure` - which is how little it was used for anything
  legitimate.
- Deleted `XvmStructure.ensureErrorListener`: once the listener was required it could only return
  its own argument.
- Fixed the restore in `compiler/Compiler`. The file is parked on a silent listener for the
  duration of a compilation, but the restore sat inside an "if no serious errors" branch, so a
  compilation that reported errors left the file permanently silenced - which matters for a
  resident compiler that reuses structures across requests. It is a `finally` now.

Deferred, because it needs the ownership decision below rather than more deletion:

- `FileStructure` still resolves its listener through a field instead of taking one at
  construction: 8 constructors, 67 call sites.
- `getErrorListener()` still consults the ambient current pool.
- The `RUNTIME`-versus-silent asymmetry therefore survives: an absent listener still throws after a
  compilation and swallows during one.

The prior art put ownership on `ConstantPool`, with a documented argument: the two callers of the
old setter were the compiler patching in a listener it already had (a constructor parameter written
as a mutation) and an engine pointing a *shared library pool* at one host's sink (a race, last
writer winning).

### Phase 6 — Name the kinds of silence

Split the single `BLACKHOLE` by intent. The prior art used `PROBE` for "speculative work whose
failure is the answer" and kept `BLACKHOLE` for "no sink attached", behaviourally identical and
deliberately so, so that nothing can branch on which it holds. The value is that
`grep PROBE` becomes the list of the compiler's speculative paths.

Also replace the ~10 `errs = ErrorListener.BLACKHOLE` reassignments in `TypeConstant` — which mean
"this result is provisional, stop reporting" — with a named method conveying that, e.g.
`errs.suppressCascade()`. **Do not make these sites report.** They exist to prevent error cascades
from incomplete `TypeInfo` builds; "fixing" them regresses the compiler into cascades.

### Phase 7 — The state-flag fields

Replace the `null`-as-state-flag idiom in the four `m_errs*LabelVars` /
`m_errsValidatingFinally` fields. Last, because it is the only phase that needs a real redesign
rather than a deletion, and nothing else depends on it.

## What not to do

- **Do not introduce a `Diagnostics` type.** The prior-art branch assessed exactly this
  (`docs/reentrancy/plans/diagnostics-vehicle-assessment.md`) and rejected it: a 639-parameter,
  1814-argument, 164-file rename that fixes none of the four things actually broken. The vehicle is
  fine.
- **Do not make it ambient.** A thread-local or context object would rebuild
  `ConstantPool.getCurrentPool()`, which is already a known smell here —
  `FileStructure.getErrorListener()` consults it and has a comment saying ownership belongs in a
  parameter.
- **Do not branch the speculative probes.** The user's instinct was "use `branch` instead of
  `BLACKHOLE` for speculative execution", and that is right for trials whose failure the user
  should eventually hear about. It is wrong for the ~52 `testFit`/`isA` probes: the return value is
  the answer, and branching would allocate a listener per probe on a hot path to collect
  diagnostics nobody reads. The prior art re-scoped its own plan for this reason. Those become a
  *named* silent listener (Phase 5), not a branch.
- **Do not annotate all 586 parameters.** Enforcement at boundaries, not decoration everywhere.

## Verification

Per phase:

1. `./gradlew :javatools:test --tests "org.xvm.asm.*" --rerun-tasks --no-build-cache`, reading the
   JUnit XML rather than the console. A new test pins the dedup fix.
2. A test that a non-`ErrorList` listener survives `branch()`/`merge()` and does not abort early.
3. Clean `./gradlew xdk:installDist` with the `requireNonNull` calls in place — the stack traces
   are the work list. Then full `./gradlew build`.
4. Full `./gradlew build`, plus the JIT test module run interpreted and under `--jit`.
5. `grep PROBE` should enumerate the speculative paths and nothing else; the XDK must still build
   with identical output.
6. Full `./gradlew build`; the loop-label tests are the coverage that matters.

Throughout: `./gradlew spotlessCheck` alone before every commit, since locally `check` runs
`spotlessApply` and silently repairs the tree.

## What is left

Nothing in the seven phases. This section is the gap between what those phases did and the full
shape the prior-art branch reached, measured against this tree.

### Gap to full parity

| | state |
|---|---|
| Dedup key, `PROBE`/`BLACKHOLE`, `suppressCascade`, `Site` + varargs, never-null, no parent-mutating setter, no null-as-state | done, phases 1-7 |
| `log()` returns `void`, abort asked separately | **postponed** by decision, revisit after the PR |
| `ErrorListener.RUNTIME` stops throwing from inside `log()` | not started |
| `ResolutionCollector.getErrorListener()` - the listener smuggled through a callback interface | not started |
| `TypeInfo` carries and replays its own diagnostics | not started; the real shape of the last 8 sites |
| `EvalCompiler.m_errs` / `ModuleInfo.Node.m_errs` final and created with their owner | not started |
| Runtime-side listener: `Container`, the connector, `recordRuntimeFailure` | not started |
| Failures with nowhere to go: 56 `System.err`/`printStackTrace`, 34 empty catches | not started |
| `Origin` (thread, fiber) stamped on each diagnostic | not started |
| Decorators - tee, SLF4J, JFR sinks | not started, and not needed until a host asks |
| `ErrorList` thread-safety | **answered differently**; see below |

None of it blocks an LSP that compiles a document with its own listener and reads what it
collected. Why you would do each, and why you might not:

**`ResolutionCollector.getErrorListener()` - do it next.** It defaults to a silent listener, so
name resolution reports into a sink nobody chose: the "quiet by default" hole phase 4 removed from
parameters, surviving in a callback interface. Seven call sites plus the default; the fix is to
pass `errs` to the three resolution methods. Compiler-enforced, low risk. Against: it widens three
signatures that implementors must follow.

**`RUNTIME` throwing from `log()` - a correctness hazard, not a style one.** It throws
`IllegalStateException` from inside the reporting call at `ERROR` and above, so the throw pre-empts
whatever the detecting code was about to throw, and the exception *type* ends up depending on who
is listening. Against: something must still fail loudly when nobody is listening, and the
no-listener case is exactly where nobody is watching, so the replacement behaviour has to be
decided. Entangled with the next item.

**`log()` returning `void` - the one thing between us and an honest host listener.** The boolean
means *abort*, conflating recording with control flow: a host that only wants to watch has no
correct value to return, since `false` suppresses a legitimate abort and `true` invents one.
Mechanically tiny - six readers, of which only `Lexer` and `Parser` branch on it. Against: it is
public API, so any external implementor breaks. Postponed for that reason alone.

**`TypeInfo` diagnostics - the highest-value correctness item, and its own project.** `TypeInfo` is
memoized, so whichever caller builds it first owns its diagnostics; if that is a speculative probe,
the probe produces the user-visible errors and the caller that cared gets the cache and hears
nothing. Ownership decided by call ordering is not something anyone can reason about. It also
finishes the never-null story by deletion rather than relocation. Against: 126 `ensureTypeInfo()`
call sites if the no-arg overload goes, and replay must be idempotent per listener or it
reintroduces the double-reporting that phase 1 fixed.

**`EvalCompiler` / `ModuleInfo.Node` listeners final - cheap, low value.** `EvalCompiler.m_errs` is
null until `createLambda` runs, so `getErrors()` throws if asked first, and it is one-shot so it
should be final. Local and near-zero risk, but it unblocks nothing.

**The runtime side - not yet.** A host cannot observe VM-level failures today:
`recordRuntimeFailure` captures a defect for `join()` to rethrow and prints it. That matters for a
debugger or a test runner that *runs* code, and not at all for an LSP that compiles. Do it when
there is a host that runs code.

**`Origin` (thread, fiber) - not yet, and carefully when it happens.** It earns its keep only with
parallel compilation or a server multiplexing requests. It is also a trap: it must never enter the
deduplication key, which is the mechanism phase 1 repaired, so adding it early risks re-breaking
that for no current benefit.

**Decorators - when a host asks.** Small and additive, and trivial to add later precisely because
the interface is now clean. That is the payoff of the phases, not more work to do up front.

**The 56 `System.err` sites and 34 empty catches - an audit, not a refactor.** These are places
where a failure has nowhere to go, and the prior art found real bugs in this category: a DNS
continuation that turned an `Error` into "host not found", a swallowed keystore delete so a
certificate revocation reported success. Each of the ninety needs a judgement call, and the output
is bug reports rather than a diff. Worth doing as its own investigation.

### The decision, and what follows from it

#### Ownership: the request owns it, and nothing else should

Decided by looking at the two consumers rather than in the abstract.

**The Gradle plugin already does the right thing.** `IsolatedDirectExecutor` builds a fresh
`ErrorList(100)` per compile, per run and per test, and passes it in:

```java
final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
return Launcher.launch(options, console, err);
```

One listener per request, created by the caller, discarded with the result. Gradle runs tasks in
parallel, so several of these can be live at once in one JVM.

**The language server needs exactly the same shape.** `Adapter.compile(uri, content)` returns a
`CompilationResult` carrying `List<Diagnostic>`, where `Diagnostic` is already an immutable record
of location, severity, message and code - which is `ErrorInfo` plus `Site` under a different name.
The existing adapters note that `compile()` runs on the LSP message thread while reads happen on
the ForkJoin pool, so per-document results are already held in a `ConcurrentHashMap`. A compiler
adapter would compile a document with its own listener and convert what it collected.

**So the listener must not be owned by a long-lived structure.** Putting it on `ConstantPool` -
which is what the prior-art branch did - is wrong for these two consumers, and for the reason that
branch itself gives when arguing against a shared sink: a pool outlives the request, and library
pools are shared across requests, so two concurrent compiles would race, last writer winning, each
reporting the other's diagnostics. A `FileStructure` outlives the request too; that is exactly why
leaving one silenced after a failed compile was a bug worth fixing.

The listener is therefore a **parameter**, which it already is at 586 sites and is now non-null.
Nothing needs re-homing.

**Which reframes the remaining 8 sites.** They are not an ownership question. They exist to serve
`ensureTypeInfo()` with no argument, and `XvmStructure.log` where no listener is in hand - and the
reason those have no listener is that `TypeInfo` is *memoized*. Whichever caller builds it first
produces its diagnostics; a later caller that actually cared gets the cached result and hears
nothing. Which caller owns them is decided by call ordering.

The fix is to make `TypeInfo` carry its own diagnostics and replay them to whoever asks, rather
than to find an ambient listener to report them to once. Then:

- `ensureTypeInfo()` with no argument either goes away or means "the cached info, reporting
  nothing";
- `FileStructure.getErrorListener()`, its field and the ambient pool lookup are deleted rather than
  re-homed;
- the `RUNTIME`-throws-versus-silent asymmetry goes with them, because nothing looks for an absent
  listener any more.

That is a bigger piece of work than the seven phases and should be its own plan. It is a
memoization problem wearing an ownership problem's clothes.

#### Thread safety without synchronization

`ErrorList` is not thread-safe. The way to keep it that way is for a listener never to be shared:
one per request, as both consumers already do, so there is nothing to synchronize. A branch is
per-subtree and merges back into its parent on one thread, which does not change that.

Where something genuinely does cross threads, make it immutable rather than locked. `ErrorInfo`
and `Site` are already values and should become records; a host that fans diagnostics out to
another thread is then safe by construction. If a concurrent sink is ever unavoidable - parallel
compilation reporting into one list - it should accumulate lock-free rather than with a monitor,
and be a separate implementation of the interface rather than a change to `ErrorList`, so the
single-threaded path pays nothing.

## Open questions
- **Postponed until after the PR.** Should `log()` keep returning `boolean`? It currently means *abort*, which conflates recording
  with control flow and leaves a host that only wants to watch with no correct value to return.
  The prior art made it `void` and asked `isAbortDesired()` separately; only 3–5 call sites read
  the result. Cheap, but it is an API break.
Thread safety is answered above: per-request listeners, immutable value types, and no explicit
synchronization.
