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

This change requires recompiling Java clients and listener implementations. `ErrorListener.log`
now returns `void`, including its legacy positional overloads. The forwarding `log` helpers on
`XvmStructure`, `AstNode` and `Token` also return `void`. Java cannot retain a compatibility overload
that differs only by return type. This belongs at an explicitly breaking release boundary; the
release/version must be selected before publication.

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

### Explicit listener migration

Compilation and launcher boundaries now require a listener. `EmbeddingSupport.compile`/`run`,
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

The file/pool ambient-listener getters and setters have been removed. Structure reporting requires
an explicit listener. Use `TypeConstant.ensureTypeInfo(errors)` when a selected source use must
report diagnostics; the no-argument metadata query is deliberately silent. Completed cached
TypeInfo results replay their recorded diagnostics to a later reporting caller. Use an ErrorList
to deduplicate repeated reports. These changes do not make the compiler or its pools concurrent.

### Scoped parser and validation reporting

`Parser.attempt()` replaces `Parser.SafeLookAhead`: use `Attempt.keep()` in place of
`keepResults()`. A kept attempt merges its buffered diagnostics, including warnings; a discarded
attempt restores its tokens and recovery state. Nested attempts observe their parent's abort
request. This removes a public nested parser class and requires source migration/recompilation at
the same breaking release boundary described above.

Parser and resolver destinations use `Reporting` scopes, closed in reverse order with
try-with-resources. `NameResolver.getErrorListener()` is null outside `resolve`; callbacks borrow
the caller's non-null listener only during that call. These scopes restore destinations on every
exit. They do not make a parser, resolver, AST or listener safe for concurrent use. The lexer still
reports to its original listener; module-name-only scans should construct the parser with an
explicit discard listener if lexical diagnostics are unwanted.

Loop and try/finally label variables retain their context/listener as one immutable
`ValidationScope` value. Each statement restores its previous value on normal, early and
exceptional exits, and `Statement.validate` likewise restores its common validation context.
The temporary references stay on the AST because label-variable and jump callbacks need the
currently validating statement. No shared mutable holder is added to cloned AST nodes.
`EvalCompiler` and `ModuleInfo.Node` own final diagnostic buffers from construction onward.

The unit regressions cover nested reporting, failed host/resolver callbacks, token rollback,
abort propagation and exceptional statement exits without requiring installed modules. Compile
and run the existing `manualTests/src/main/x/loop.x` and `exceptions.x` exercises with the built
XDK to check lazy label variables and try/finally behavior through code generation and execution.
The separate Gradle compiler-consumer test supplies its compiled modules through dependencies.

The [integration plan](../docs/errs-integration-plan.md#proposed-prs-and-dependencies) maps this
migration to C1/C2/C3/C4 and records the source commits and compatibility policy. The
[embedding/listener guide](../docs/errs-error-listeners.md) explains the pipeline changes and
current host contract.

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
