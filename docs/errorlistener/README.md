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
ErrorListener mine = err -> {
    seen.add(err.getCode());
    return false;                 // see section 5 before copying this
};

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

## 5. The one sharp edge: `log()`'s return value is control flow

**A listener decides whether the compiler throws.** This is a property of the existing design, it is
not new, and a host must know it.

```java
// ErrorList
public boolean log(ErrorInfo err) {
    ...
    return isAbortDesired();
}

public boolean isAbortDesired() {
    return m_severity == Severity.FATAL || f_cMaxErrors > 0 && ... m_cErrors >= f_cMaxErrors;
}

// Parser, Lexer
if (m_errs.log(severity, sCode, aoParam, ...)) {
    throw new CompilerException("error list is full: " + m_errs);
}
```

So logging a `FATAL` makes `log()` answer `true`, which makes the caller **throw**. A host listener
returning `true` unconditionally makes the compiler abort on the first diagnostic; returning `false`
unconditionally - the obvious thing to write, and what the examples above do - means a `FATAL` no
longer aborts anything.

### Why this is a bad design and what to do about it

Overloading one boolean to mean both *"I recorded it"* and *"stop compiling"* conflates observing
with participating. An observer that only wants to watch has no correct value to return: `false`
suppresses a legitimate abort, `true` invents one. And because the throw is raised by whichever
parse method happened to be running, the exception carries no information about what the host
decided - it says "error list is full" regardless.

Three options, worst to best:

1. **Leave it, document it.** What this document does. A host must delegate: wrap a real
   `ErrorList` and return *its* answer, as `Slf4jErrorListener` and `JfrErrorListener` do. Fine for
   a decorator, a trap for a lambda.
2. **Separate the two questions.** Keep `log` for recording and let `isAbortDesired()` alone decide
   control flow, with call sites asking it rather than reading `log`'s result. Mechanical, and it
   makes an observer impossible to get wrong.
3. **Make aborting explicit.** The compiler decides to stop by testing the listener's state at
   points it chooses, and a diagnostic never *causes* a throw from inside a `log` call.

**Recommendation: (2).** It removes the trap without changing when compilation gives up, and it is
the smallest change that makes "just observe" expressible. Until then, a host that is not sure
should wrap an `ErrorList` rather than write a lambda.

Note also that `ErrorList.log()` returning `isAbortDesired()` means the answer depends on
`cMaxErrors` - so an `ErrorList.unlimited()` never asks to abort on count, while
`ErrorList.firstError()` asks after one. Use the named factories rather than a number, so the intent
is visible:

```java
ErrorList.firstError()   // a speculative attempt: does this work at all?
ErrorList.unlimited()    // collect everything, never ask to abort
new ErrorList()          // DEFAULT_MAX_ERRORS
```

---

## 6. What is still open

- **53 `System.err` sites.** Only 2 have a listener in scope, and neither should be converted (one
  is a developer TODO marker, the other carries its author's note that changing it needs focused
  tests). The other 51 are in `runtime`/`javajit`/`tool`, mostly in handle constructors and
  `CallChain` that run outside any frame and so have no owner to propagate from. Giving those an
  owner is a separate, smaller problem than the listener threading was.
- **75 no-argument `ensureTypeInfo()` calls.** Section 2.3 explains why this is mostly fine: adoption
  makes the asker the owner. Converting them all would make the invariant unconditional rather than
  a consequence of adoption, but would also route corrupt-library faults into user-facing
  diagnostics with no source position. See E35 D in the enhancement list for the full argument; the
  recommendation is a targeted test of the adoption invariant rather than seventy conversions.
- **`ConstantPool.m_errs` is the one non-final listener field** (13 of 14 are final). The compiler
  swaps it for the registration-phase silence. Passing `BLACKHOLE` to the calls that should be
  silent would let it be constructor-set and final.
