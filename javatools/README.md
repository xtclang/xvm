# Directory: ./javatools/ #

This directory contains the "javatools" project, which is a
set of Java classes that implement the Ecstasy lexer, parser,
compiler, IR, runtime, etc.

This project uses the library produced by the "javatools_utils" project,
and the `implicit.x` resource file from the "ecstasy" project.

Long term, this project will be used to help support IDE
plug-ins for common Java-based IDEs, such as IntelliJ IDEA.

(Note: The test portion of this project may have dependencies
on test frameworks.) 

This project produces the `javatools.jar` file.

The License is the Apache License, Version 2.0. 

## Compiler

Status: Suitable for use

* Driven by `org.xvm.tool.Compiler`
* (The original command line tool for the compiler is
  `org.xvm.compiler.CommandLine`)
* Lexed by `org.xvm.compiler.Lexer` into
  `org.xvm.compiler.Token` objects
* Recursive descent parsed by `org.xvm.compiler.Parser` into
  `org.xvm.compiler.ast.AstNode` objects
* AST nodes are multi-pass compiled (with optional re-queuing)
  via `org.xvm.compiler.ast.StageManager`

## Error-listener API migration (breaking change)

### Why the compiler adapter needs this contract

An editor repeatedly compiles incomplete source. It needs the diagnostics from that particular
attempt, accurate error state before using its results, and a stop request that survives temporary
reporting wrappers. A message printed to a console, or silently discarded below the embedding
entry point, cannot be reconstructed into a reliable editor diagnostic afterward. These are
requirements on the compiler's reporting contract, not on the LSP wire protocol.

There are concrete differences from ordinary `ErrorList` use:

| Failure or requirement | What the change does | Executable evidence |
|---|---|---|
| The old default branch gave a callback-based host a one-error budget it never requested. The lexer stopped at the first bad escape and never reported the second. | A default branch buffers without a finite count budget; `ErrorList` branches keep their configured budget. | `ErrorListenerHostRegressionTest.hostBranchLetsLexerReportBothErrors` drives the real lexer. |
| A branch created before its parent requested an abort could continue using only its own state. | A branch observes both its local policy and the parent's current stop request. | `ErrorListenerHostRegressionTest.parentAbortReachesTheLexerThroughAnExistingBranch`. |
| A bare reporting callback receives messages but inherits state queries that still answer false. | `collecting` records severity and codes; `tee` combines its destinations' state as well as delivery. | `ErrorListenerBranchTest` compares a bare callback with a stateful collector; `ErrorListenerSiteTest` checks forwarding and budgets. |
| Suppressing follow-on reports must not erase the operation's stop request. | A derived `errors.silence(reason)` discards reports while delegating the abort query to `errors`. | `ErrorListenerMigrationTest.branchesAndDerivedSilenceObserveTheParentBudget`. |
| Changing reporting signatures must preserve existing stop checkpoints and diagnostic locations. | The lexer/parser report first and explicitly query the same policy; branch re-anchoring remains supported. | `ErrorListenerMigrationTest` checks parser/lexer budgets and legacy structure-report locations. |

The tests above live in [the listener test package](src/test/java/org/xvm/asm).
The two `ErrorListenerHostRegressionTest` cases deliberately use API shapes available before and
after C1, allowing the same test source to be run against both revisions. They require no installed
XDK modules and cannot silently skip for lack of those modules.

Review verification on 2026-10-09: the identical two-test source failed both cases on the
pre-C1 revision `1d9352b6f` and passed both on this implementation. The failures were the
one-error branch abort and the missing parent abort, respectively. The focused listener/boundary/launcher
run passed 60 tests with zero skips. These are actual behavioral regressions; tests of null
rejection and reason names instead enforce deliberate API choices.

The return type and helper names are design choices. The old boolean `log` API could have been
implemented consistently by returning `isAbortDesired()` everywhere. C1 chooses one query for
continuation policy and a separate reporting operation, making wrappers and independent stop
checks easier to compose. `Site` and severity helpers also reduce positional argument ambiguity;
they do not change the source language or constitute an LSP requirement by themselves.

This is not a promise that every existing Java caller is compatible. The migration below lists
the deliberate API and behavior changes. Existing `ErrorList` budgets, diagnostic payloads and
ordinary parser/lexer error handling have direct regression coverage. Compiler-consumer tests and
a full XDK build check ordinary compilation beyond those focused examples. None of these checks
prove that every possible source program or third-party listener is unaffected.

This change requires recompiling Java clients and listener implementations. `ErrorListener.log`
now returns `void`, including its legacy positional overloads. The forwarding `log` helpers on
`XvmStructure`, `AstNode` and `Token` also return `void`. Java cannot retain a compatibility overload
that differs only by return type. These source and binary compatibility breaks are planned for **0.5.0**; Java clients and listener
implementations must be migrated and recompiled when adopting that release.

Report a diagnostic, then ask the listener whether the work should stop:

```java
ErrorListener errors = ErrorListener.collecting(hostDiagnostics::add);
errors.error(code, ErrorListener.in(source, start, end), arguments);
if (errors.isAbortDesired()) {
    return;
}
```

`collecting` remembers severity and reported codes and forwards every report without deduplication.
A bare `ErrorListener` lambda only implements reporting and inherits state queries that always
answer false. Use `ErrorList` when repeated diagnostics should collapse or a count budget is needed:
its default constructor uses `DEFAULT_MAX_ERRORS`; `FIRST_ERROR` and `UNLIMITED` name other policies.
A fatal diagnostic requests an abort even with an unlimited budget.

`Site.In`, `Site.At` and `Site.None` describe source spans, structures and unpositioned diagnostics.
The severity helpers accept message arguments as trailing varargs. The old positional forms are
deprecated but still work after recompilation, including source re-anchoring through a branch.
`BLACKHOLE` and `BlackholeErrorListener` are removed. Use `silent(Silence.PROBE/CASCADE/DISCARD)`
or a listener's `silence(...)` wrapper. A derived silence keeps its parent's abort request.

A custom listener's default branch buffers without an arbitrary one-error budget and observes the
parent's abort state. `ErrorList` branches also retain their configured budget. Merging an unbranched
listener returns itself. `tee` forwards reports to both sinks and honors either sink's abort state.
Callbacks must be serialized by the host; these helpers do not make compiler execution concurrent.

`RUNTIME` now prints diagnostics and records an abort request for ERROR/FATAL; reporting no longer
throws. Runtime callers that depended on that exception must explicitly check `isAbortDesired()`
and choose their own failure handling. Parser and lexer reporting sites use that sequence.

### Why explicit silence and stable listener ownership

`PROBE`, `CASCADE` and `DISCARD` are **reasons, not levels**. They all discard reports. No compiler
algorithm should change because one reason was selected instead of another:

| Reason | Intent at the call site |
|---|---|
| `PROBE` | Test an alternative, such as whether an expression fits a type. Failure is the answer to the query, not necessarily an error in the user's selected program. |
| `CASCADE` | Suppress follow-on reports caused by already incomplete information, so they do not obscure the original failure. |
| `DISCARD` | Deliberately ignore this destination, for example the CLI's external delegate when its console already displays diagnostics. |

`BLACKHOLE` provided the last behavior, but did not explain which intent applied. More importantly,
replacing an active listener with `BLACKHOLE` also lost access to its abort policy. The distinction
between the two replacement forms is behavioral, not just a rename:

```java
ErrorListener independent = ErrorListener.silent(ErrorListener.Silence.PROBE);
ErrorListener quiet = errors.silence(ErrorListener.Silence.CASCADE);
```

`independent` has no parent and never requests an abort. `quiet` discards reports but still observes
`errors.isAbortDesired()`. Neither buffers messages for recovery later; use `branch` and `merge`
when reports might need to be kept. Naming the reasons makes suppression searchable and reviewable;
it does not itself prevent a caller from selecting the wrong reason or the wrong factory.

C2 removes the old constant and class so compilation identifies remaining call sites that have
not made this decision. Keeping a deprecated alias was possible; removal is a deliberate migration
choice at the 0.5.0 breaking boundary, not a technical prerequisite of LSP. Code that truly wants the
old standalone sink can use `silent(DISCARD)` with the same discard/no-abort behavior.

A missing listener and a deliberately silent listener must also be distinguishable. At an active
compilation boundary, accepting null and substituting a sink hides wiring mistakes; later code
cannot know whether the caller intended silence. C2 requires the destination before work begins,
while allowing an explicit silent listener. `ErrorListenerBoundaryTest` checks rejection and
retention of supplied destinations. These are tests of the chosen API contract, separate from the
old-behavior regressions above; a null rejection is an intentional compatibility change.

Stable ownership does not mean that every listener variable in the compiler must be `final` and
non-null at all times. An operation should retain its original destination: constructor-owned
references can be final, and temporary suppression should use a derived local value instead of
overwriting that destination. C2's TypeInfo cascade calls follow this rule, so suppressing one
incomplete contribution does not replace the operation's listener. A final reference does not
freeze the listener's counters, and neither final nor non-null makes compiler objects thread-safe.
Parser/resolver destinations that genuinely change during nested work need scoped restoration;
that is later ownership work, not a justification for banning all mutable or inactive fields.

The broad file count reflects migration of existing reporting calls and explicit decisions at
their callers. The substantive review points are null boundaries, retained listener ownership,
probe/cascade choices and removal of the collector's silent default. The source-span/varargs
rewrites should preserve the same severity, code, arguments and location. CLI entry points select
an explicit discard delegate; their console reporting and exit policy remain active.

### Explicit listener migration

Compilation and launcher boundaries now require a listener. `ModuleCompiler.compile`, `EmbeddingSupport.compile`/`run`,
launcher constructors and dispatch, the compiler, lexer/parser, `StageMgr`, anonymous-class
construction and `Component.SimpleCollector` reject null listeners. AST reporting and validation
helpers no longer silently ignore null or replace it with a discard listener. Pass the operation's
listener for work whose diagnostics belong to the caller. For an intentional discard, pass
`silent(Silence.DISCARD)` explicitly; command-line entry points do this for their external delegate
because their Console already reports diagnostics.

Fit tests and other speculative paths use `silent(Silence.PROBE)` when failure is only a return
value. `Expression.testFitAsType` no longer takes a listener; its staging and fit checks both use a
probe. Kept validation branches still merge into the supplied listener. An incomplete TypeInfo
build derives `errs.silence(Silence.CASCADE)` at affected calls, preserving the original parameter
and parent abort state throughout the operation.

`ResolutionCollector.getErrorListener()` is now abstract. Every implementation must provide a
destination; `SimpleCollector` retains the one supplied to its constructor. This default removal,
the removed blackhole names and `testFitAsType` parameter, and rejecting previously accepted nulls
are additional compatibility changes at the same breaking release boundary as the return-type
change. Recompile implementations and migrate their callers before publication.

This slice retains the existing file/pool ambient-listener API and parser/resolver ownership.
Their removal and scoped replacements belong to later slices. In particular, `XvmStructure.log`
still uses its existing fallback when passed null; the explicit compilation boundaries above do
not. The reporting-call migration alone does not establish complete diagnostic delivery on all
compiler failures or TypeInfo cache hits.

## Assembler

Status: Suitable for use

* Structures are all based on `org.xvm.asm.XvmStructure`
* Constant values and persistent identity references encoded
  as `org.xvm.asm.Constant` objects
* Inheritance tree of component types starting with
  `org.xvm.asm.Component`
* Virtual machine instructions encoded as `org.xvm.asm.Op`
  objects (see the `ops.txt` file in the documentation)

## Runtime

Status: Working proof-of-concept (will be replaced by an
LLVM-based adaptive compiler).

* Command line is `org.xvm.tool.Runner`
* Implementation in `org.xvm.runtime` package

**Warning:** This runtime is not speedy, by any stretch; this
is expected, because it is only intended as a proof-of-concept.
The runtime is currently implemented as an interpreter, and
the interpreter (which would be naturally slow to begin with)
has not been optimized. Its purpose is to be malleable and easy
to test, so that we could prove out the design of the compiler
and the Ecstasy IR.
