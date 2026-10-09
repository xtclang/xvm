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

## Compiler diagnostics and error listeners

### Why the old reporting model had to change

The old error-listener API did not provide a reliable contract for embedding the compiler.
Accepting a listener at the entry point was not enough: deeper calls could substitute `BLACKHOLE`,
accept null as an implicit request to discard reports, or find a different listener through a
shared structure. The caller could receive only part of an attempt's diagnostics without knowing
anything had been lost. Suppression, ownership and stopping policy were spread across unrelated
call sites, so adding a callback at the outer API could not repair what happened underneath it.

This caused observable failures. The default branch for a host listener imposed a one-error
budget the host had never requested: the lexer stopped at the first recoverable error and never
reported the next one. A previously created branch could also ignore its parent's later abort
request. Two regression tests reproduce those failures on the old compiler with exactly the same
test source that passes on the repaired implementation.

Reporting also had inconsistent control-flow effects. `log` returned an abort decision, but the
fallback `RuntimeErrorListener` instead threw an exception for ERROR and FATAL. Selecting a
diagnostic destination could therefore change how the operation failed. An editor must be able to
collect an ordinary source error as data, distinguish it from an internal compiler failure, and
let the compiler stop at an appropriate checkpoint. The destination receiving a report should not
introduce a different failure path merely because that report has a particular severity.

### Changes and enhancements

* **Separate reporting from stopping.** `log(ErrorInfo)` delivers a diagnostic;
  `isAbortDesired()` answers whether work should stop. The lexer and parser retain explicit stop
  checkpoints, and forwarding listeners preserve the owner's policy.
* **Make host callbacks usable by the compiler.** `collecting` forwards structured diagnostics
  while remembering severity and error codes. A bare callback receives reports but does not
  implement those state queries automatically.
* **Make branching consistent.** A host's default branch buffers without an arbitrary one-error
  limit. `ErrorList` branches retain their configured budget, and both observe their parent's
  current abort request.
* **Make suppression explicit.** Named reasons distinguish probes, cascade suppression and
  intentional discards. A derived silence discards messages while retaining the owner's stop
  policy.
* **Require a reporting destination at active compilation boundaries.** An omitted listener is
  rejected before compilation starts; intentional silence is supplied as an explicit listener.
* **Keep ownership stable.** Constructor-owned listeners remain attached to their operation.
  Temporary suppression derives a local listener instead of overwriting the original destination.
* **Preserve diagnostic structure.** `Site` describes source spans, structures or unpositioned
  reports. Severity helpers take message arguments as trailing varargs; `tee` forwards reports to
  two destinations while combining their state.

### Removed behavior and its replacement

* **The boolean return from `log`** is replaced by a reporting call followed by an explicit policy
  query where the caller needs to stop. There is one continuation query for wrappers to preserve.
* **`BLACKHOLE` and `BlackholeErrorListener`** are replaced by named suppression factories.
  Removing the old symbols makes unmigrated call sites fail to compile, requiring each caller to
  identify its reason for silence and whether a parent's abort policy must survive.
* **Null-to-discard fallback at compilation boundaries** is replaced by a required listener.
  Passing `silent(DISCARD)` still supports a caller that deliberately wants no reports; accidentally
  omitting the destination no longer looks like a successful choice of silence.
* **The silent default in `ResolutionCollector.getErrorListener()`** is removed. Implementations
  must provide their destination, and `SimpleCollector` retains the listener it was given.
* **Reassigning a TypeInfo build's listener to `BLACKHOLE`** is replaced by local cascade
  suppression. The original listener continues to identify the operation and its stop policy.
* **Severity-triggered exceptions in the fallback runtime listener** are replaced by recorded
  abort state. The compiler or runtime caller decides how to end its operation. This does not
  remove the command-line launcher's deliberate fatal-error and exit handling.
* **The listener parameter on `Expression.testFitAsType`** is removed because that operation is
  a probe. Its staging and fit checks use explicit probe silence; selected validation can still
  retain and merge its diagnostics.

The legacy positional reporting overloads remain available after recompilation, but are
deprecated in favor of `Site` and trailing arguments. Their source re-anchoring behavior is tested.

### Why this is a prerequisite for the compiler-backed LSP

**Reliable diagnostic ownership and abort propagation are required for the compiler-backed LSP.**
The adapter must associate reports with the requested compilation, reject unusable semantic
results, and stop obsolete work at compiler checkpoints. It cannot reconstruct diagnostics that
the compiler discarded or obtain errors from work that an unintended one-error budget prevented.
Leaving those failures in place blocks a reliable compiler-backed adapter, regardless of how much
code is added around the public entry point. These changes establish the reporting foundation;
the companion ownership and delivery changes described below complete the paths built on it.
The exact helper names and the decision to remove a legacy alias are API design choices. The
delivery, state and stopping guarantees they implement are the essential requirements.

### Compatibility with ordinary compilation

Normal command-line compilation continues to use the existing compiler pipeline, console and
exit policy. Users do not need new flags or changes to their Ecstasy source. The CLI supplies an
explicit discard listener only for its external delegate; its console still reports diagnostics.
`ErrorList` retains its configured serious-error budget, fatal reports still request an abort,
and the lexer/parser still stop when the applicable policy requires it. The changes repair
diagnostic delivery and listener ownership without introducing an IDE dependency into the compiler.

There is strong regression evidence for that continuity: the full Java suite for this listener
migration executed 418 tests successfully, and all 24 rebuilt XDK modules matched a freshly built
master baseline after normalizing only module creation timestamps. The focused checks below add
direct coverage of reporting, aborts, boundary validation and launcher behavior. These results
support confidence in ordinary compilation; they are evidence for the tested paths, not an
exhaustive guarantee for every source program or external integration.

**The Java embedding API deliberately changes at the planned 0.5.0 boundary.** Existing Java
callers and listener implementations must migrate and recompile. Changed return types, removed
symbols, rejected nulls and the runtime listener's new exception behavior prevent a claim of
100% source or binary compatibility, even for a non-LSP Java consumer. The migration steps below
identify those changes explicitly so ordinary consumers can preserve their intended behavior.

### Code tour and practical usage

#### 1. Give each compilation its own diagnostic owner

Start at [ModuleCompiler](src/main/java/org/xvm/api/ModuleCompiler.java) or
[EmbeddingSupport](src/main/java/org/xvm/api/EmbeddingSupport.java). Supply the attempt's listener
at the entry point and keep it available until the result and diagnostics have been consumed.
For example, with a configured core repository and source text:

```java
final var errors = new ErrorList(100);
var compiler = new ModuleCompiler(coreRepository);
var module = compiler.compile(sourceText, null, errors);
var diagnostics = List.copyOf(errors.getErrors());

if (module == null || errors.hasSeriousErrors()) {
    // Publish the diagnostics and do not treat this as a successful compilation.
    return;
}
// The caller can now use the successfully compiled module.
```

The null argument here is the optional dependency repository, not the listener. The required
listener also reaches launcher construction and dispatch, the compiler, lexer/parser,
[StageMgr](src/main/java/org/xvm/compiler/ast/StageMgr.java), anonymous-class construction and
[Component.SimpleCollector](src/main/java/org/xvm/asm/Component.java).
[ResolutionCollector](src/main/java/org/xvm/asm/ComponentResolver.java) implementations must
return their selected destination. AST reporting and validation helpers no longer silently
accept a missing listener.

Use a fresh collector for an independent attempt. An `ErrorList` is mutable; even `clear()` keeps
its previously seen diagnostic UIDs. A new operation should not inherit that deduplication history.
Hosts must serialize compiler access and listener callbacks. Final references and non-null
parameters do not make compiler objects or their accumulated state thread-safe.

#### 2. Report data, then make the control-flow decision

[ErrorListener](src/main/java/org/xvm/asm/ErrorListener.java) remains the central interface.
Its nested `ErrorInfo` carries severity, code, message arguments and location. `Site.In`, `Site.At`
and `Site.None` describe the location independently of the message:

```java
errors.error(code, ErrorListener.in(source, start, end), arguments);
if (errors.isAbortDesired()) {
    return;
}
```

Use `in(source, start, end)` for source text, `at(structure)` for an assembled structure, and
`NOWHERE` for a report without either location. Source positions are the encoded values returned
by `Source.getPosition()`, not raw byte offsets or LSP positions. The `error`, `warn`, `info` and
`fatal` helpers select severity and delegate to `log`; they do not transfer control themselves.

`isAbortDesired()` and `hasSeriousErrors()` answer different questions. An operation can have
errors while remaining within its budget, and a host may request a stop without reporting an
error. Callers use the first query at stop checkpoints and the second when deciding whether a
result is usable. `hasError(code)` checks whether a particular diagnostic was recorded.

`RuntimeErrorListener` now prints diagnostics and records an abort request for ERROR/FATAL
instead of deliberately throwing from `log`. Its abort flag remains set for that instance's
lifetime; the shared `RUNTIME` sink is not a fresh per-attempt collector. A reporting callback
can still throw its own exception, which forwarding helpers propagate. The redesign removes the
fallback sink's severity-driven exception policy, not Java exception handling in general.

#### 3. Choose retention, callback delivery or both

[ErrorList](src/main/java/org/xvm/asm/ErrorList.java) retains the first report for each diagnostic
UID in insertion order. ERROR/FATAL reports spend its count budget; warnings do not. Its
no-argument constructor uses `ErrorList.DEFAULT_MAX_ERRORS` (100), `FIRST_ERROR` selects one,
and `UNLIMITED` disables the count limit. A retained FATAL requests an abort with any budget.
The older `ErrorListener.DEFAULT_MAX_ERRORS` constant is a separate compiler/tool policy.

When a host wants every report delivered to a callback, use:

```java
ErrorListener errors = ErrorListener.collecting(hostDiagnostics::add);
```

`collecting` forwards every report, including duplicates, and tracks severity and codes before
calling the consumer. It has no count budget and requests an abort on FATAL. A bare lambda
implements only `log` and inherits state queries that return false, so it is not a substitute
when the compiler relies on those queries.

`tee(first, second)` delivers to both destinations in order and observes either destination's
abort, serious-error and code state. This supports reporting to a host while recording diagnostics
for later replay. `ErrorList.logTo(destination)` replays retained reports without clearing them;
deduplication belongs to the destination. If the first destination throws, the second is not called.

#### 4. Buffer speculative diagnostics until a result is accepted

`branch(node)` creates an `ErrorList.BranchedErrorListener`. Pass it to speculative work and merge
it once if that work's diagnostics should be retained:

```java
var trial = errors.branch(node);
// Run the candidate validation with trial as its listener.
if (keepDiagnostics) {
    trial.merge();
}
```

Unmerged diagnostics remain local to the branch. A default host branch has an unlimited count
budget, while an `ErrorList` branch copies its parent's configured limit. Both observe the
parent's current abort request, and a local FATAL still stops the branch. A node-associated
branch re-anchors structure reports to that node's source span; explicit source spans remain intact.

Merging an unbranched listener returns that listener. Merging a branch forwards its reports and
returns the parent without clearing the buffer. Do not repeatedly merge an accepted branch:
an `ErrorList` parent can deduplicate replay, but an arbitrary callback need not.

#### 5. Suppress reports without losing the operation's owner

`Silence` and `SilentErrorListener`, nested in `ErrorListener`, make suppression explicit.
`PROBE`, `CASCADE` and `DISCARD` are **reasons, not severity levels**. They have identical discard
behavior; compiler algorithms must not depend on which name is selected.

| Reason | Use it when |
|---|---|
| `PROBE` | Testing an alternative, such as whether an expression fits a candidate type. Failure is the answer to that query, not necessarily a problem in the selected program. |
| `CASCADE` | Examining already incomplete information whose follow-on errors would obscure the original failure. |
| `DISCARD` | Deliberately ignoring this destination, such as the CLI's external delegate when its console already reports diagnostics. |

The important behavioral distinction is between a standalone sink and a derived silence:

```java
// No parent listener or stop policy: an independent, deliberately silent probe.
ErrorListener independent = ErrorListener.silent(ErrorListener.Silence.PROBE);

// Suppress follow-on reports but continue to honor this operation's abort request.
ErrorListener quiet = errors.silence(ErrorListener.Silence.CASCADE);
```

`independent` never requests an abort. `quiet.isAbortDesired()` observes `errors`, even when
no diagnostic caused that stop request. Neither retains discarded messages; use a branch when
diagnostics might later be needed. `suppressed()` exposes a derived silence's parent, not a buffer
of hidden diagnostics. Re-silencing an existing silence returns the same instance and reason.

For a separately constructed standalone sink, `new SilentErrorListener(reason)` needs no
explicit null parent. The two-argument constructor retains an optional parent for callers that
need its stop policy. Required factory arguments and results use `@NotNull`; intentional absence
uses `@Nullable`, including `suppressed()` on a standalone sink. Public construction boundaries
still reject invalid null arguments immediately; annotations also expose the contract to IDEs
and Kotlin callers.

The names make intent searchable and reviewable. `BLACKHOLE` did not distinguish those purposes,
and substituting it for an active listener also removed access to that listener's stop policy.
Removing the old constant and class makes missed migrations visible to the Java compiler. A
deprecated alias was possible, but would allow unexplained suppression to survive the migration.
Callers that want exactly the old standalone discard behavior can use `silent(DISCARD)`.

Stable ownership is equally important. In
[TypeConstant](src/main/java/org/xvm/asm/constants/TypeConstant.java), `cascade(incomplete, errors)`
derives suppression at affected calls instead of assigning a sink to the `errors` parameter.
The original destination remains available throughout the build. Constructor-owned references,
such as those in `StageMgr`, `SimpleCollector` and `Launcher`, are final so they cannot drift to
another destination during the operation. Their listeners' counters can still change.

Non-null means that an active reporting boundary has an explicit owner or an explicit discard
policy. It does not mean that every listener field everywhere must be permanently non-null or
final: nested parser/resolver work needs temporary destinations and scoped restoration. That
ownership work is described separately below.

#### 6. Migrate Java callers and implementations

The planned release boundary is **0.5.0**. Recompile Java clients and listener implementations
after applying these changes; Java cannot retain compatibility overloads that differ only by
the return type of `log`.

| Previous use | Replacement and consequence |
|---|---|
| `if (errors.log(...))` | Report first, then query `errors.isAbortDesired()` and perform the same return/throw at that checkpoint. Update custom implementations to `void log(ErrorInfo)`. |
| `BLACKHOLE` or `BlackholeErrorListener` | Choose a named standalone silence or derive one from the current owner when its abort policy must survive. |
| Null at a compilation/launcher boundary | Pass the operation's listener or explicit `silent(DISCARD)`. Missing listeners now fail at the boundary. |
| A `ResolutionCollector` relying on the default sink | Implement `getErrorListener()` and retain the intended destination. |
| `testFitAsType(..., errors)` | Remove the listener argument; the operation owns its probe policy. |
| Depending on `RUNTIME.log` to throw on severity | Check `isAbortDesired()` and explicitly choose the operation's failure handling. |
| Legacy positional reporting overloads | They remain callable after recompilation but are deprecated; migrate to `Site` and trailing message arguments. |

### Regression evidence and how to reproduce it

The strongest evidence is the pair of
[host regression tests](src/test/java/org/xvm/asm/ErrorListenerHostRegressionTest.java).
Their source uses API shapes available on both sides of the reporting change. On the previous
implementation at `1d9352b6f`, both compile and fail for the expected behavioral reason. On the
repaired implementation, the same source passes both cases:

* `hostBranchLetsLexerReportBothErrors` sends two bad string escapes through the real lexer.
  Previously it threw after the first error because the default branch had a one-error budget.
  It now completes the scan and publishes both reports when the branch is merged.
* `parentAbortReachesTheLexerThroughAnExistingBranch` aborts the parent after creating its branch.
  Previously the lexer continued because the branch considered only its local state. It now
  honors the parent at its reporting checkpoint.

[ErrorListenerMigrationTest](src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java) also proves
that derived silence preserves a host stop request without publishing an error. Its parser/lexer
budget tests and source-reanchoring test guard existing behavior. The branch, site, silence and
abort suites cover collection, forwarding, replay and suppression.
[ErrorListenerBoundaryTest](src/test/java/org/xvm/api/ErrorListenerBoundaryTest.java) checks required
listeners and destination retention; these assertions enforce the chosen API contract rather than
claiming that every API change reproduces an old bug.

On 2026-10-09, the focused run on the integrated implementation passed **69 tests with zero
failures, errors or skips**. The reporting-contract review version passed 28 focused tests and
the explicit-listener migration passed 60, also without skips. Earlier full-suite verification
of that migration passed 418 executed Java tests, with 42 skips recorded separately, and its
24 rebuilt XDK modules matched the master baseline after normalizing creation timestamps.
The separate integration checkout combining the reporting changes with the embedding foundations
also passed the real compiler-consumer test with no skips. These results identify the implementation
and scope tested; they do not conflate focused listener tests with full compiler validation.

Run the focused tests from the repository root:

```bash
./gradlew :javatools:test \
  --tests 'org.xvm.asm.ErrorListener*Test' \
  --tests org.xvm.api.ErrorListenerBoundaryTest \
  --tests org.xvm.tool.LauncherErrorHandlingTest \
  --rerun-tasks --no-build-cache
```

These focused cases do not require installed XDK modules. For broader compiler verification,
build the XDK first, then force the Java test run:

```bash
./gradlew :xdk:installDist
./gradlew :javatools:test --rerun-tasks --no-build-cache
```

Check failures and skips in `javatools/build/test-results/test/TEST-*.xml`; a green task alone does
not establish that optional tests executed. The real compiler-consumer integration test additionally
exercises the standard build path with compiled modules supplied by its Gradle dependencies.

### Review boundaries and additional ownership changes

[PR #680: separating diagnostic reporting from abort policy](https://github.com/xtclang/xvm/pull/680)
introduces the listener contract, factories and branch fixes. The **explicit listener ownership
and named suppression** review branch migrates callers and active compilation boundaries.
The separate **scoped parser and validation reporting** branch handles temporary destinations
and restoration. This integrated implementation also contains the later structure-reporting,
TypeInfo replay and host integration described below.

#### Explicit structure reporting and cached TypeInfo diagnostics

The ambient file/pool listener getters and setters have been removed here. Structure reporting
requires an explicit listener. Use `TypeConstant.ensureTypeInfo(errors)` when a selected source
use must report diagnostics; the no-argument metadata query is deliberately silent. Completed
cached TypeInfo results replay their recorded diagnostics to a later reporting caller. Use an
`ErrorList` to deduplicate repeated reports. These changes do not make the compiler or pools
safe for concurrent access.

#### Parser attempts and scoped destinations

[Reporting](src/main/java/org/xvm/asm/Reporting.java) provides scoped destinations for parser and
resolver work. Close scopes in reverse order with try-with-resources so the previous destination
is restored on every exit. `NameResolver.getErrorListener()` is null outside `resolve`; callbacks
borrow the caller's non-null listener during that call. This inactive state is distinct from
accepting a missing listener at an active compilation boundary.

`Parser.attempt()` replaces `Parser.SafeLookAhead`; use `Attempt.keep()` instead of `keepResults()`.
A kept attempt merges buffered diagnostics, including warnings. A discarded attempt restores
tokens and recovery state. Nested attempts observe their parent's abort request. Removing the
old public nested parser class requires source migration and recompilation at the same release
boundary. The lexer still reports to its original listener; module-name-only scans should
construct the parser with an explicit discard listener when lexical diagnostics are unwanted.

Loop and try/finally label variables retain their context/listener as one immutable
`ValidationScope` value. Statements restore their previous value on normal, early and exceptional
exits, and `Statement.validate` restores its common validation context. These temporary references
remain on the AST because label-variable and jump callbacks need the currently validating
statement. No shared mutable holder is added to cloned AST nodes. `EvalCompiler` and
`ModuleInfo.Node` own final diagnostic buffers from construction onward.

The scope tests cover nested reporting, failed host/resolver callbacks, token rollback, abort
propagation and exceptional statement exits without installed modules. The existing
`manualTests/src/main/x/loop.x` and `exceptions.x` exercises verify lazy label variables and
try/finally behavior through code generation and execution with the built XDK.

#### Related embedding contracts

The [integration plan](../docs/errs-integration-plan.md#proposed-prs-and-dependencies) records the
review boundaries, source commits and compatibility policy. The
[embedding/listener guide](../docs/errs-error-listeners.md) describes the complete pipeline and
its host consumers, including request cancellation and diagnostic publication.

The [embedding compatibility table](../docs/errs-integration-plan.md#compatibility-and-migration-contract)
also covers result records and retained constructors. `CursorBinding` currently has fourteen
components: adding constructors preserves their descriptors, not old record-pattern arity.
Prefer accessors for consumers that do not need deconstruction. Lists/maps are immutable
snapshots, but their compiler/AST values still belong to the compilation attempt; copy them into
detached host values while owning the compiler worker. Immutability of the outer collection does
not make ASTs, constant pools or TypeInfo safe for concurrent queries.

`PartialAnalysis` accepts and returns a nullable `ConstantPool`, matching `Compilation`: null
means semantic analysis did not start. Its three- through six-argument constructors take the
pool directly, without an `Optional` wrapper. Clients of the earlier branch API must recompile,
pass the pool or null, and replace `pool().orElse(...)` with a null check.

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
