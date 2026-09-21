# Threading `errs` through the compiler

Scoping document for making `ErrorListener` an always-present, non-null, immutable part of the
compiler's call stack, so that an embedding host — an LSP server above all — can rely on hearing
every diagnostic the compiler produces.

**Status.** All seven phases are implemented on `lagergren/errs`, plus the follow-on work that
came out of review:

- the array-shaped reporting overloads are deprecated, with no callers left in the tree;
- an in-memory document can be named, so a host compiling several of them stops losing diagnostics;
- the three silences are one concept with the reason as a value, and a derived silence keeps what
  it silenced;
- nothing rebinds an `errs` parameter any more - not `TypeConstant`, not the parser's module-name
  scan;
- the parser's speculation is a `branch`, not a second reporting mechanism of its own;
- the compile-time silence is a scope with a stated lifetime, not a setter on a shared structure;
- one `Reporting` holder owns the only mutable listener reference left, so every listener field
  in `javatools/src/main` is final;
- the ambient listener lookup is gone rather than propagated through - see *Deleting the ambient
  lookup* below;
- the language server compiles through the embedding API and publishes real diagnostics, which is
  the first thing that actually consumes any of this;
- a warning master loses entirely is reported again - see *A warning master never shows you* in
  the appendix, which is worth an issue against master on its own.

**Behaviour change worth calling out:** source that compiled silently before can now emit
`VERIFY-75`. Anyone with a duplicated property annotation will see a new warning. That is the
point - master was dropping it - but it is a visible change and not only a refactor.

What remains is one decision and the work that follows from it; see *What is left*.

If you are here to *use* the thing rather than to read how it got this way, skip to *Using it*.

Measurements throughout are of `origin/master` at `794bf23e6` - the state this was written against,
kept as the record of what was wrong. Where that has since changed:

| | before | after |
|---|---|---|
| null-coalescing sites | 33 | 1, plus one assert - the `FileStructure` pair is gone with the ambient lookup |
| names for the listener | 6 | 1 - `errs` |
| kinds of silence | 1 undifferentiated | one concept, 3 named reasons: `PROBE` (133), `DISCARD` (3), `CASCADE` (3) |
| fields using null as a state flag | 4 | 0 |
| `new Object[]` at report sites | 31 | 0 (4 left in the tree, none of them report sites) |
| callers of the array-shaped `log` overloads | all of them | 0 — the overloads are `@Deprecated` |
| `errs` parameters rebound mid-method | 8 in `TypeConstant` | 0 |
| tests covering the listener contract | 0 | 46, in 10 files, plus 8 in the language server |
| mutable listener references | 15 fields | 1, inside `Reporting` |
| unguarded reads of the ambient constant pool | 18 | 0 |
| reporting mechanisms in the parser | 2, checked in order | 1 |
| `catch (… ignore)` naming an exception only to drop it | 62 | 0 - the unnamed variable instead |

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
| `silent(DISCARD)` references | 80, in 33 files |
| `branch(` / `merge()` call sites | 44 / 56 |
| Null-check lines involving a listener | 35 |
| `@param errs … (optional)` javadoc lines | ~29 |

**The type change is nearly free.** The parameter is already non-null in practice almost
everywhere, and `errs` is already the name. Three places already reject null outright —
`Lexer.java:46`, `Parser.java:59`, `compiler/Compiler.java:36`. The invariant exists; it simply is
not propagated.

**The real work is small and concentrated**: about 13 null-substitution sites
(`errs == null ? silent(DISCARD) : errs`) and 10 conditional-logging sites (`if (errs != null)`), across
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
`m_errors != BLACKHOLE`, which becomes meaningless once `BLACKHOLE` stops being the
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

## Using it

Worked examples of the API the phases arrived at. Each is real code from the tree or the tests,
not a sketch.

### Compiling from a host

The embedding API is what a host calls, and it gained two things the language server needed:

```java
// the document carries its own name, so its diagnostics are distinguishable from another
// unsaved document's - the name belongs to the document, not to the act of compiling it
embedding.compile(new Source(text, uri), null, errs);

// and a failed compilation no longer answers with nothing but null: the structures a
// verification error was raised against still exist, and the pool they were interned in is
// the only way to reach them
Compilation result = embedding.compileModule(new Source(text, uri), null, errs);
result.succeeded();   // a module came out
result.file();        // what was built, either way
result.pool();        // what it interned into
result.parsed();      // and the AST, which is the only thing that knows where anything is
```

**Where symbols come from, which is not where you would look first.** A `ClassStructure` knows
its name, its kind, its members and their types - it is the resolved side of the compiler, and it
is the side with all the answers - and it knows nothing whatever about the text it was written in.
It carries no source position. An editor cannot use a symbol it cannot point at, so the resolved
side is the wrong source for an outline and the AST is the right one.

That is the whole reason `Compilation` carries the parsed source. It costs precision: an AST node
knows what was *written*, not what it *resolved to*, so names are as they appear and nothing is
qualified. That is enough for `documentSymbol` and for finding what the cursor is inside. It is
not enough for completion or go-to-definition, which need to know what a name refers to.

`ensureRuntimePool()` is not that pool. It boots an interpreter - a connector builds a
NativeContainer, which loads a native template for every core module - so it needs the whole
library, not just the part the compiler bootstraps against. It was called `getConstantPool()`,
which read like an accessor.

### Which listener do I want?

| situation | use |
|---|---|
| I am the compiler and someone passed me one | the `errs` parameter. Pass it on; never replace it |
| I want to collect diagnostics | `new ErrorList()`, or `ErrorListener.collecting(sink)` |
| I am trying something and its failure *is* my answer | `PROBE` |
| I am trying something and want the errors only if I keep the result | `errs.branch(node)`, then `merge()` on the branch you keep |
| My result is already known to be incomplete | `errs.silence(CASCADE)` |
| I genuinely want no diagnostics at all | `silent(DISCARD)` |
| I want to both act on them and watch them | `ErrorListener.tee(act, watch)` |

The three silences behave identically on purpose — nothing may branch on which one it holds — so
the choice is documentation for the next reader, and `grep PROBE` is the list of the compiler's
speculative paths.

They are one concept with the reason as a value, not three different constructs. Two entry points,
differing only by whether you have a listener to derive from:

```java
silent(why)        // you have none: a shared constant per reason
errs.silence(why)  // you have one: a wrapper that keeps it reachable via suppressed()
```

`silenceReason()` gives the reason back, so a host that republishes diagnostics can tell a probe
from a cascade — which is what lets it offer the second as related information and drop the first.
Of the 160 sites that reach for a probe, 76 have no listener in scope at all, which is why both
entry points exist rather than one.


### Report a diagnostic

The message parameters are the trailing arguments. The location is a `Site`, which is what makes
that possible: an `Object[]` in the middle of the signature is what used to force every call site
to build an array by hand.

Static-import the site factories and the silences rather than qualifying them; `ErrorListener.`
repeated on every line is noise, and these names do not collide with anything:

```java
import static org.xvm.asm.ErrorListener.at;
import static org.xvm.asm.ErrorListener.in;
import static org.xvm.asm.ErrorListener.NOWHERE;

errs.error(Compiler.NAME_UNRESOLVABLE, at(this), getValueString());
errs.warn (Compiler.SUSPICIOUS_CAST, in(source, lStart, lEnd), typeFrom, typeTo);
errs.fatal(Parser.FATAL_ERROR, NOWHERE);
```

Three ways to say where, and they are a closed set, so a host can switch over them exhaustively:

| | means |
|---|---|
| `in(source, lPosStart, lPosEnd)` | a span of source text |
| `at(xs)` | an XVM structure, which has no source location of its own |
| `NOWHERE` | genuinely nowhere: a whole-compilation failure |

The older `log(severity, sCode, Object[], source, start, end)` overloads still exist and are
deprecated. Nothing in the tree calls them; `javatools` compiles under `-Xlint:all` with no
deprecation warning, which is the check that keeps it that way.

### Ask what a listener has seen

Recording a diagnostic and deciding to abandon the work are separate questions. `log` is `void`
precisely so that they cannot be confused.

```java
if (errs.isAbortDesired()) {          // budget spent, or a FATAL arrived
    throw new CompilerException("error list is full: " + errs);
}
if (errs.hasSeriousErrors()) { ... }  // an ERROR or worse has been reported
if (errs.hasError(Compiler.NAME_MISSING)) { ... }  // this specific code
```

### Choose a budget

Three named values, defined once on `ErrorList` and nowhere else:

```java
new ErrorList()                      // no reason to choose a number: DEFAULT_MAX_ERRORS
new ErrorList(ErrorList.UNLIMITED)   // only a FATAL stops the work
new ErrorList(ErrorList.FIRST_ERROR) // stop at the first serious error
```

### Speculate

Two different things, and picking the wrong one is the classic bug in this area.

**A trial whose failure the user should hear about** if every alternative also fails — branch,
then merge only the attempt you keep:

```java
ErrorListener errsAttempt = errs.branch(node);
Expression    exprNew     = expr.validate(ctx, typeTarget, errsAttempt);
if (exprNew != null) {
    errsAttempt.merge();   // it was the right road; the diagnostics count
}                          // otherwise drop it: nothing was reported
```

**A trial whose failure *is* the answer** — "would this expression fit that type?" — probe. The
return value is what the caller acts on, so the diagnostics are noise by construction:

```java
if (!ctx.requireThis(getStartPosition(), silent(PROBE))) {
    return null;   // a question, not an assertion
}
```

### Suppress a cascade

Different again from both of the above. Once a result is known to be built from incomplete
information, everything after it describes the incompleteness rather than the user's code, and
reporting it buries the one diagnostic that matters.

```java
private static ErrorListener cascade(boolean fIncomplete, ErrorListener errs) {
    return fIncomplete ? errs.silence(CASCADE) : errs;
}
```

Choose at each use rather than rebinding the listener, so that an `errs` parameter still means
what its signature says all the way down a method:

```java
if (!collectChildInfo(constId, ..., cascade(fIncomplete, errs))) {
    fIncomplete = true;
}
```

`silence(CASCADE)` wraps the receiver instead of returning a shared constant, so the suppression
is a decision about one computation, and what was suppressed is still reachable:

```java
ErrorListener quiet = errs.silence(CASCADE);
assert quiet.isSilent();
assert quiet.silenceReason() == CASCADE;
assert ((SilentErrorListener) quiet).suppressed() == errs;
assert quiet.silence(CASCADE) == quiet;   // idempotent; safe to call per use
```

### Say you want nothing

```java
compile(source, silent(DISCARD));   // "I do not want them"
```

`DISCARD` and `PROBE` behave identically and deliberately so — nothing may branch on which one a
listener holds. They are separate reasons because they are separate intentions, and because
`grep PROBE` is the list of the compiler's speculative paths.

### Host it

A host implements the interface, or takes the one-liner. A bare lambda is a trap: it supplies
only `log`, and inherits defaults that answer as though nothing had been reported, which the
compiler asks around a hundred times to decide whether a stage may proceed.

```java
// WRONG: receives the diagnostics, then tells the compiler they did not happen
ErrorListener naive = problems::add;
assert !naive.hasSeriousErrors();   // even after an ERROR

// RIGHT: same one-liner, answers truthfully
ErrorListener host = collecting(problems::add);
```

Report to two places at once — one to act on, one to watch — with `tee`:

```java
ErrorList recorder = new ErrorList(ErrorList.UNLIMITED);
buildSomething(tee(errs, recorder));
// later, replay to a different caller; deduplication makes it idempotent
recorder.logTo(errsLater);
```

### Publish to a problem view

What an LSP adapter actually needs. `Site` being a closed set is what lets the mapping be
exhaustive instead of testing which nullable field happened to be populated:

```java
ErrorListener host = collecting(err -> publish(switch (err.site()) {
    case Site.In   site -> diagnostic(err, site.source(), site.lPosStart(), site.lPosEnd());
    case Site.At   site -> diagnostic(err, site.xs().getDescription());
    case Site.None ignore -> wholeFileDiagnostic(err);
}));
```

**Name every document.** A diagnostic's identity includes the name of the source it came from,
and an `ErrorList` drops anything whose identity it has already seen. Two unnamed in-memory
documents with a problem at the same offset therefore collide, and the second is discarded
uncounted. An editor's unsaved buffers are exactly that case — not on disk, but the host knows
the URI:

```java
new Source(text, uri.toString())   // not new Source(text)
```

### Tutorial: wiring a host from scratch

What an embedding host — an editor's language server, say — has to do, end to end. Each step is
one of the guarantees the phases exist to provide.

**1. Build a listener that answers truthfully.** Do not pass a bare lambda. `ErrorListener` is a
functional interface, so a lambda compiles, but it supplies only `log` and inherits defaults that
say nothing was reported. The compiler asks those defaults around a hundred times to decide
whether a stage may proceed.

```java
List<ErrorInfo> problems = new ArrayList<>();
ErrorListener   errs     = collecting(problems::add);   // NOT problems::add on its own
```

**2. Name the document.** A diagnostic's identity includes the name of the source it came from,
and an `ErrorList` drops anything whose identity it has already seen. Two unnamed in-memory
documents with a problem at the same offset collide, and the second is silently discarded.

```java
Source source = new Source(text, uri.toString());
```

**3. Compile, and expect failure to be ordinary.** Source that does not compile is the normal
case for an editor, not an exceptional one. Aborting because the source has errors is not an
internal error, and the diagnostics have already been reported through the listener.

```java
try {
    new Parser(source, errs).parseSource();
} catch (CompilerException ignore) {
    // an unrecoverable parse abandons its progress; the diagnostics are the point
}
```

**4. Ask the listener, not the exception, what happened.**

```java
if (errs.hasSeriousErrors()) { ... }
```

**5. Turn each diagnostic into whatever your protocol wants.** `Site` is a sealed interface, so
this switch is exhaustive and the compiler will tell you if a new shape is ever added — which is
the whole reason it is a closed set rather than three nullable fields.

```java
for (ErrorInfo err : problems) {
    publish(switch (err.site()) {
        case Site.In   site   -> range(site.source(), site.lPosStart(), site.lPosEnd());
        case Site.At   site   -> wholeSymbol(site.xs().getDescription());
        case Site.None ignore -> wholeFile();
    }, err.getSeverity(), err.getCode(), err.getMessage());
}
```

**6. Reuse the listener across documents if you want one problem list** — that works, provided
step 2 was done. `CompilerDiagnosticsTest` pins both halves of this: that named documents
accumulate, and, as the positive control, that unnamed ones do not.

### Writing compiler code that reports

Five rules, each of which is a phase:

1. **Take `errs` as a parameter and pass it on.** Never find one ambiently, never store one you
   were not given, never assign over the parameter. If you need a different listener for part of
   the work, choose it at the point of use.
2. **Never accept `null`.** There is nothing to check, because nothing produces one. A caller that
   wants silence names it.
3. **Report through the severity-named methods**, with the parameters trailing:
   `errs.error(CODE, at(this), a, b)`. The array-shaped overloads are deprecated.
4. **Recording is not deciding.** `log` is `void`. If you need to stop, ask `isAbortDesired()`.
5. **Say which silence you mean.** `PROBE`, `CASCADE` and `DISCARD` are three intentions, not
   three spellings — and the reason travels with the listener, so a host can tell them apart.
6. **Speculate with `branch`.** A trial whose diagnostics count only if you keep the result is a
   branch; the parser's `attempt()` is one. Do not build a second mechanism for it.

### Testing diagnostics

The contract is covered by six test classes; add to them rather than re-deriving. The pattern that
catches the most is a **positive control** — assert the thing still fails when the fix is removed:

```java
@Test
public void testUnnamedDocumentsAreIndistinguishable() {
    ErrorList errs = new ErrorList(UNLIMITED);
    parse(new Source(DOC), errs);
    parse(new Source(DOC), errs);
    assertEquals(1, errs.getErrors().size(),
            "identical unnamed sources produce one identity, so one diagnostic");
}
```

### Divert reporting for a stretch of work

Most code takes a listener as a parameter and passes it on. Three things cannot: the parser
(threading one through two hundred parse methods is not a refactor), a `FileStructure` (an interned
`TypeConstant` asked to build a `TypeInfo` has no caller to ask), and a `NameResolver` (the
callbacks it makes have no listener of their own). Those report through a destination, and
`Reporting` is where the destination lives.

```java
private final Reporting f_errs;              // the holder is final

f_errs.get().log(...);                       // where diagnostics go right now

try (Reporting.Scope quiet = f_errs.to(silent(DISCARD))) {
    ...                                      // and for this stretch, somewhere else
}                                            // put back on every exit, exceptions included
```

It is the only mutable `ErrorListener` reference left in the compiler. **It fixes lifetime, not
ownership**: the destination is reachable by anything holding the owner, so two threads through one
owner still interfere. Ownership is fixed by a parameter. Do not add a fourth user without first
asking whether a parameter would do.

### A note on budgets

`ErrorList` takes a number of serious errors to tolerate. Most call sites in the tree pass a
literal, and the literals are arbitrary: instrumenting every construction across the whole test
suite and a full XDK build shows that **no budget except a deliberate one in a budget test ever
binds** — the largest number of errors any list actually accumulated was 50, against budgets of
5, 10, 24, 25, 100 and 1000.

So do not agonise. `new ErrorList()` is the answer unless you have a reason; name `UNLIMITED` or
`FIRST_ERROR` when you do.

### Style

`ErrorListener.` repeated on every line is noise. Static-import what you use — none of these
names collide with anything in the tree:

```java
import static org.xvm.asm.ErrorList.FIRST_ERROR;
import static org.xvm.asm.ErrorList.UNLIMITED;
import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.at;
import static org.xvm.asm.ErrorListener.collecting;
import static org.xvm.asm.ErrorListener.in;
```


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

- Delete the ~13 `errs == null ? silent(DISCARD) : errs` substitutions and the ~10 `if (errs != null)`
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

*Since built:* the park is a scope rather than a setting. `setErrorListener` is gone;
`reportingTo()` hands out a `Reporting.Scope` that restores on close and nests, and the compiler
holds it for exactly as long as the compilation lasts. The silence is derived from the caller's
listener as a `CASCADE`, so what it suppresses stays reachable - which matters, because
instrumenting it showed the park swallows about sixty ERROR-severity diagnostics during a
*successful* XDK build:

| code | count | |
|---|---|---|
| `VERIFY-70` | 30 | `@Override` indicated, no super method found |
| `VERIFY-67` | 24 | property information contains conflicting types |
| `COMPILER-140` | 4 | annotation not applicable |
| `COMPILER-38` | 3 | name unresolvable |

All twenty distinct messages sit on anonymous inner classes and unbound generics - one names
`Future<PendingTypeParameter>`, an internal placeholder for a type parameter that has not resolved
- which is the signature of a `TypeInfo` built on half-finished structures. They are very probably
artefacts. Nobody could have known, because until now they were destroyed rather than suppressed.
**Whether any is real is a type-system question and deserves an owner.**

*And then deleted.* The park existed only to quieten one line - `TypeConstant.ensureTypeInfo()`,
which walked up to the file because an interned value has no caller to ask. Two measurements
settled what that line should do instead:

- across a full XDK build and test run, `ErrorListener.RUNTIME` - the end of the walk, which
  prints to stdout - received **nothing** from real code. The one hit was a test calling it
  deliberately. So the ~68 runtime and JIT callers never reported anything;
- during a compilation the file was parked on a silence anyway, so the ~46 compile-time callers
  were already quiet.

Both halves were therefore already silent, by arrangement rather than by design.
`ensureTypeInfo()` says its own silence now, and the arrangement is gone with it:
`FileStructure.getErrorListener` and its pool-then-RUNTIME fallback, `FileStructure.reportingTo`
and its holder, `XvmStructure.getErrorListener`, and the compiler's park. 166 lines deleted, 57
added, and the XDK compiles to byte-identical modules.

The lesson worth keeping: **ownership here was not fixed by propagating a listener to 115 call
sites, it was fixed by deleting the thing that let 115 call sites exist.** What remains is the
optional, incremental part - the ~46 compile-time sites can each be given a real listener one at a
time, turning a deliberate silence into a diagnostic, visibly and reviewably.

The prior art put ownership on `ConstantPool`, with a documented argument: the two callers of the
old setter were the compiler patching in a listener it already had (a constructor parameter written
as a mutation) and an engine pointing a *shared library pool* at one host's sink (a race, last
writer winning).

### Phase 6 — Name the kinds of silence

Split the single `BLACKHOLE` by intent. The prior art used `PROBE` for "speculative work whose
failure is the answer" and kept `BLACKHOLE` for "no sink attached", behaviourally identical and
deliberately so, so that nothing can branch on which it holds. The value is that
`grep PROBE` becomes the list of the compiler's speculative paths.

Also replace the ~10 `errs = silent(DISCARD)` reassignments in `TypeConstant` — which mean
"this result is provisional, stop reporting" — with a named method conveying that, e.g.
`errs.silence(CASCADE)`. **Do not make these sites report.** They exist to prevent error cascades
from incomplete `TypeInfo` builds; "fixing" them regresses the compiler into cascades.

*As built, this went further than planned.* Review pointed out that naming the decision was not
enough while it was still carried out by assigning over the `errs` **parameter**, which in all four
methods stopped meaning what its signature said partway down. Two changes followed:

- the choice is made at each use — `cascade(fIncomplete, errs)` — so nothing is rebound;
- `errs.silence(CASCADE)` returns a `SilentErrorListener` wrapping the receiver, instead of the
  shared `PROBE` constant. A probe and a cascade are not the same silence: a probe's failure *is*
  the answer and nobody ever wants those diagnostics, whereas a cascade's are real diagnostics
  that happen to be consequences of a known-missing piece. Keeping the receiver is what leaves
  that difference recoverable for a host that wants them as related information.

Verified byte-for-byte: the whole XDK compiles to identical modules before and after, all 22 of
them, once the wall-clock timestamp stamped into each one is masked out.

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

### The gate that actually catches things

A green build says less than it looks. Three things caught real mistakes in this work and are
worth repeating on anything that touches reporting:

**Compiled output, byte for byte.** Every change here claims not to alter what the compiler emits,
and that is checkable: build the XDK before and after and compare the modules. The only difference
between two builds of identical source is the wall-clock timestamp stamped into each module
(`FileStructure(String)` uses `Instant.now()`; the `(String, Instant)` overload exists for
reproducibility and the tests use it). Mask that and the comparison is exact - 179 differing bytes
across 22 modules, every one of them inside an ISO-8601 timestamp.

```java
Pattern TS = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z");
// blank the timestamps, then compare xdk/build/install/xdk/lib/*.xtc before and after
```

**A positive control.** A test that passes proves nothing until you have watched it fail. Every
guard added here was checked by reverting the fix and confirming the test catches it - the naming
fix, the kept-attempt warning. A guard nobody has seen fail is a guard nobody knows works.

**Measuring rather than asserting.** Several confident-sounding claims in this document started out
wrong and were corrected by instrumenting: that the park discarded nothing (it discards about
sixty diagnostics a build), that the error budgets were chosen (no budget ever binds), that a
cascade decorator would be too costly (7040 allocations over a whole XDK build). If a claim about
behaviour can be counted, count it.

## What is left

Nothing in the seven phases. This section is the gap between what those phases did and the full
shape the prior-art branch reached, measured against this tree.

### Gap to full parity

| | state |
|---|---|
| Dedup key, the named silences, `Site` + varargs, never-null, no parent-mutating setter, no null-as-state | done, phases 1-7 |
| `log()` returns `void`, abort asked separately | done |
| `ErrorListener.RUNTIME` stops throwing from inside `log()` | done |
| `ResolutionCollector.getErrorListener()` - the listener smuggled through a callback interface | done |
| `TypeInfo` carries and replays its own diagnostics | mechanism POC done; the 126-call-site migration is not |
| `EvalCompiler` / `ModuleInfo.Node` listeners final and created with their owner | done |
| every listener field final; one mutable reference, inside `Reporting` | done |
| the parser's speculation expressed as `branch`/`merge` rather than its own mechanism | done |
| the compile-time park expressed as a scope with a stated lifetime | done |
| an end-to-end test of what an editor is told | done - `LspRoundTripTest` |
| the LSP server's `XdkAdapter` wired to the compiler | done - it compiles through the embedding API and publishes diagnostics |
| an outline from the compiler: `documentSymbol`, and the symbol under the cursor | done - from the AST, since the structures carry no positions |
| completion, go-to-definition, find-references | not started; they need resolution, not syntax |
| the ambient listener lookup | deleted rather than propagated through |
| cancellation of a stale compilation | not started; `isAbortDesired()` is the hook and nothing drives it |
| Runtime-side listener: `Container`, the connector, `recordRuntimeFailure` | not started |
| Failures with nowhere to go: 56 `System.err`, 35 empty catches | audited; see `errs-audit.md` |
| `Origin` (thread, fiber) stamped on each diagnostic | POC done, unconsumed |
| Decorators - tee, SLF4J, JFR sinks | `tee` done, the rest not needed until a host asks |
| `ErrorList` thread-safety | **answered differently**; see below |

None of it blocks an LSP that compiles a document with its own listener and reads what it
collected. Why you would do each, and why you might not:

**`ResolutionCollector.getErrorListener()` - done, and smaller than expected.** It defaulted to a
silent listener, so name resolution reported into a sink nobody chose. Both collectors in the tree
already overrode it, so the default was never reached and existed only as a trap for the next one:
deleting it compiles untouched and is enforced from here on. The prior art threaded `errs` through
the three resolution methods instead, which would have meant 8 implementors and 18 call sites of
`resolveName` to enforce an invariant two implementations already kept.

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

### Why not ScopedValue

A dynamically-scoped ambient value is the obvious modern answer to "who hears a diagnostic nobody
was given a listener for", and Java 25 has `ScopedValue`. It was considered and rejected.

It would fix the lifetime and the thread-confinement of the ambient lookup while preserving the
actual defect: a signature still would not say where diagnostics go. A missed binding would not
fail - it would fall back silently, here to `RUNTIME`, which prints. That is the same failure class
this work exists to remove, and the invisibility to the type system is exactly how 110 of the 124
`ensureTypeInfo()` call sites came to have no listener in scope in the first place.

Explicit propagation is the correct fix. It is not affordable yet, and the measurement says why:
those 110 sites are not a mechanical rename but 110 decisions about where a listener comes from,
each cascading into its callers. Approach it from the leaves, if and when someone is working on the
type system anyway.

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

- ~~Should `log()` keep returning `boolean`?~~ **Answered: no, and it should not return anything
  else either.** It is `void`; `isAbortDesired()` is asked separately. The old `boolean` meant
  *abort*, which conflated recording with control flow and left a listener that only wants to
  watch with no correct value to return. Exactly three call sites on master read it —
  `Lexer.java:2507`, `Parser.java:5580`, `Token.java:328` — all of them asking "is the error list
  full?", which is now its own question.

  Returning `this` for fluent chaining was considered and rejected: nowhere in
  `javatools/src/main/java` are two diagnostics reported in a row to the same listener, so
  chaining has no customer, and re-introducing a value on `log` re-opens the ambiguity that
  removing one closed.
Thread safety is answered above: per-request listeners, immutable value types, and no explicit
synchronization.

## Appendix: what this work found and did not fix

Things turned up along the way that are real, are not this branch's to fix, and would otherwise be
forgotten. Each is written with what was measured, so the next person does not have to re-derive
it.

### The ambient constant pool *(made safe; ownership still ambient)*

`ConstantPool.getCurrentPool()` is a thread-local, bound by `withPool` around stretches of
compilation and by the runtime container. Outside those it is null, which is the ordinary state of
every thread driving the compiler from Java - a build tool, an embedding host, a test, a debugger
evaluating a watch - and eighteen places dereferenced it without asking. Two of them threw during
this branch's own work, in unrelated code, **both found by accident**: `FileStructure.getErrorListener`,
and the code that describes a `MethodBody` for a log line, where the NullPointerException hid the
assertion failure it was formatting.

All eighteen are guarded now, in a separate commit so it can be reviewed and moved on its own:

```java
ConstantPool.currentOr(fallback)   // the primitive
Constant.poolInUse()               // for a constant, its own pool as the fallback
```

The ambient pool is **preferred, not replaced**. `withPool` exists precisely because the compiler
works across pools, so a constant's own pool is not always the one the caller meant, and answering
from the wrong pool is worse than answering from none. What says the preference order is right:
the XDK compiles to byte-identical modules, so every one of these had a pool bound during a build
and none of them changed answer. The fix reaches only the null case.

**What is not fixed is the ownership.** "Which pool am I working in" is still a property of the
thread rather than of the work, and that is still an ownership parameter in disguise - the same
mistake as the ambient listener, which this branch deleted rather than guarded. Guarding it
removes the crashes, not the design. Doing it properly is the same shape of job as the listener
propagation below, and wants the same owner.

### Propagating a listener to the TypeInfo builders

`ensureTypeInfo()` - the form that takes no listener - is called at **124 sites**, and since the
ambient lookup was deleted each one visibly says `silent(CASCADE)`: "I am deliberately not
reporting". They divide:

| | sites | what a listener would mean |
|---|---|---|
| runtime and JIT | 69 | nothing - there is no compilation |
| compile-time | 55 | a real diagnostic |
| …whose own method already takes a listener | 2 | done - see below |
| …whose method does not | 53 | a decision per site, and its callers too |

The rest cluster - `RelOpExpression`, `TypeConstant`, `ArrayAccessExpression` - so a single file is
a contained change. This is no longer a transitive closure; it is a backlog.

**The two that were one hop away are done, and they are the warning for the rest.** They wanted
*opposite* treatment:

- `StatementBlock.resolveReservedName` is resolving `super`. If the TypeInfo for the context type
  cannot be built, that is the caller's business, so it reports to the `errs` it was given.
- `RelOpExpression`'s site is inside `testFit`, which is the speculative question - the return
  value is the answer and a diagnostic would be noise. It now says `silent(PROBE)` rather than
  using the no-arg form, whose javadoc says it is for the runtime and whose silence is a cascade.

A sweep would have made both report and one would have been wrong. Whoever takes the remaining 53
should expect to read each one.

### A TypeInfo that could not describe itself *(fixed)*

Recorded here first as "`ensureTypeInfo` throws on a later call", which was wrong - a misreading
of a stack trace. `ensureTypeInfo` is fine; what threw was `TypeInfo.toString()`, called by AssertJ
while formatting an assertion failure. The failure it was formatting got lost behind the
NullPointerException it raised.

The cause was `MethodBody.pool()`, which was
`return ConstantPool.getCurrentPool()` - the ambient thread-local again, null on any thread that
has never had a pool pushed. It is reached from `isOp()` and from `toString()`, so printing a
MethodBody threw out of the code meant to describe it, and an assertion failure mentioning one
could not report itself. Exactly the fault `FileStructure.getErrorListener()` had, from the same
thread-local. Guarded now, falling back to the pool the body's own identity belongs to, with
`MethodBodyAmbientPoolTest` pinning it on a thread that has no ambient pool.

**Two other things that probe turned up, neither fixed:**

- `ensureTypeInfo` on such a type *rebuilds* rather than replaying, and deliberately: a build that
  reports a serious error does not cache its TypeInfo, which `TypeConstant` says in as many words -
  `if (errs.hasSeriousErrors()) { // we need to return what we've got, but don't cache it`. So for
  an error the recording and the cached path are mutually exclusive by construction, and the later
  caller hears the diagnostics from a fresh build instead. That rule is on master too, unchanged.

  A **warning** is the case where they do meet, because only a *serious* error prevents caching. A
  warning-only build caches its TypeInfo and keeps its recording, so the next caller takes the
  cached path and is told what the first caller was told. That is the whole reason the memoized
  diagnostics exist, it is the only condition under which they can fire, and it is now covered -
  measured firing as `REPLAYED VERIFY-75 ... (cached path, not rebuilt)`. I had concluded the memo
  was dead code on the strength of error cases alone and was about to delete it.
- That is two ambient-pool NPEs found in one branch, in unrelated code, by accident. There are
  **19** call sites left. It is worth assuming more of them are wrong.

### A warning master never shows you *(fix here; file against master)*

**This one should be an issue.** On `origin/master`, this compiles and reports *nothing at all*:

```
module M {
    class Base    { @Atomic Int x = 1; }
    class Derived extends Base { @Atomic @Override Int x = 2; }
}
```

The compiler does detect it - `VERIFY-75: The annotation "Atomic" on property "x" on "M:Derived"
duplicates an annotation that is already present from the base property; the annotation on the
derived property is ignored` - and then loses it. The author is never told, and their annotation
is silently dropped.

Reproduced, attributed and fixed:

| | master | this branch |
|---|---|---|
| what the compilation reports | **0 diagnostics** | 1 - `WARNING VERIFY-75` |

Instrumenting master's park prints `### PARK SWALLOWED WARNING VERIFY-75` at the moment it goes.
The cause is `Compiler.generateInitialFileStructure`, which set the file structure's listener to
`BLACKHOLE` for the duration of a compilation; a diagnostic raised while a TypeInfo was assembled
reached that listener rather than the caller's. Deleting the ambient lookup removed the park and
with it the loss, so the fix is already here - but the **bug is master's** and wants an issue of
its own, because it is user-visible, silent, and has nothing to do with error listeners as far as
anyone reading the symptom would be able to tell.

It is a warning, not an error: the duplicate is redundant, the base already carries the
annotation, and the code compiles and runs correctly. Nothing crashes. What is wrong is only that
you are not told.

`TypeInfoDiagnosticsTest` covers it, and covers the replay below.

### The parked diagnostics nobody has read

The same park swallowed about sixty ERROR-severity diagnostics during a *successful* XDK build -
`VERIFY-70` x30, `VERIFY-67` x24, `COMPILER-140` x4, `COMPILER-38` x3. All twenty distinct
messages sit on anonymous inner classes and unbound generics, one naming
`Future<PendingTypeParameter>`, which is the signature of a TypeInfo built on half-finished
structures.

I assumed these were all artefacts. **`VERIFY-75` above is the reason not to.** It was lost by the
same mechanism, it looked like more of the same, and it turned out to be signal - it only surfaced
because a specific source shape was constructed to provoke it. "Probably noise" was an assumption,
not a finding.

These differ from `VERIFY-75` in a way that matters: they are ERRORs. If any is genuine then the
XDK built successfully while the compiler was suppressing errors that should have failed it. That
is a different order of problem from a missing warning, and **it is a type-system question that
deserves an owner.** They are recoverable now rather than destroyed, so the triage is possible
where before it was not.

#### A first pass at the triage

With the park gone, these now reach the explicit `silent(CASCADE)` in `ensureTypeInfo()` instead,
so they can be captured there. Over an XDK build that is **70 distinct** ERROR-severity messages,
dominated by `VERIFY-70`. Three were followed to their source, chosen to be different shapes:

| | what it is | why the `@Override` has no super |
|---|---|---|
| `maps.ListMapIndex.clear()` | `mixin ListMapIndex into ListMap` | `ListMap` declares `clear()`; the super exists only once the mixin is layered onto its `into` target |
| `Interval.adjoins(…)` | `mixin Interval into Range` | the same |
| `json:ObjectInputStream.PeekAhead.openObject(…)` | `annotation PeekAhead` | the same |

So the dominant shape is a TypeInfo built for a mixin or annotation **in isolation**, where by
construction the members it overrides are not yet present. The `VERIFY-67`s are on
`…Object:1.element…`, anonymous inner classes with unbound type parameters, and the `COMPILER-38`
names `Future<PendingTypeParameter>`, an internal placeholder. All consistent with building a type
that is not yet in its final composition - which is what a cascade suppression is *for*.

On this evidence the cascade silence is doing its job and the park was the over-broad one: it
silenced everything for the whole compilation, which is how it caught a real warning as well.

**Three of seventy is a sample, not a survey.** It is recorded so the next person starts from a
hypothesis rather than from nothing, not so they can skip the other sixty-seven.

### Printed failures and empty catches

`docs/errs-audit.md` has the full count: 56 places that print a failure instead of reporting it,
and 35 `catch` blocks with empty bodies. Deliberately not swept - each needs a judgement about
whether the failure is real and where it should go, and those judgements belong to whoever owns
the code. The prior-art branch found real bugs in this category, not just untidiness.

### Smaller things

- `EmbeddingSupport` is a singleton that takes its configuration once, so two tests cannot
  configure it differently in one JVM. Awkward for testing; not wrong for its purpose.
- The language server has no cancellation. `isAbortDesired()` is the hook the compiler already
  has, and nothing drives it. Until something does, a stale keystroke's compilation runs to
  completion - which is why the adapter queues rather than parallelises.
- Completion, go-to-definition and find-references in the XDK adapter are unimplemented. The
  outline is done - symbols with real positions, walked out of the AST, and the innermost
  declaration containing a position - but these three need *resolution* rather than syntax: what
  a name refers to, not what was typed. The AST cannot answer that, and the resolved structures
  that can have no positions to answer it with. Something has to bridge the two, and that is a
  larger piece of work than the outline was.

