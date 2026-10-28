# Directory: ./javatools/ #

This directory contains the "javatools" project, which is a
set of Java classes that implement the Ecstasy lexer, parser,
compiler, IR, runtime, etc.

This project uses the library produced by the "javatools_utils" project,
and the `implicit.x` resource file from the "ecstasy" project.

The compiler also supports the compiler-backed language server. Start with
[Compiler architecture for language tooling](#compiler-architecture-for-language-tooling) for the
design rationale, API boundaries and compiler changes required by editor consumers.

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

## Compiler architecture for language tooling

The language server needs the compiler's answers while a user is editing: which declaration a
name binds to, which overload an argument fits, what type an expression has, and which diagnostics
belong to the current document revision. Running the command-line compiler and parsing its output
cannot supply those answers. Successful compilation also discards or rewrites some of the source
information that navigation needs; an unfinished buffer may never reach emission at all.

This section and the detailed diagnostic guide below explain the design rationale and changes to
`javatools` that make this possible. The compiler remains independent of LSP, IntelliJ and VS Code.
It exposes compilation results and source facts; the adapter in `lang/lsp-server` converts those
facts into immutable editor models, applies document-version checks and implements the protocol.

| Compiler mechanism | Why an editor needs it | Code and contract |
| --- | --- | --- |
| Owned diagnostics and explicit stopping | Associate every report with an attempt, retain speculative failures appropriately, and cancel obsolete work without manufacturing source errors. | `ErrorListener`, `ErrorList`, `Reporting`; [diagnostic design](#compiler-diagnostics-and-error-listeners). |
| Snapshot inputs and compilation outcomes | Compile unsaved module/member files together and distinguish successful output from ordinary partial progress after a failure. | `EmbeddingSupport` and the `ModuleInfo` source hooks; [host snapshots and outcomes](#compile-a-host-snapshot-and-inspect-its-outcome). |
| Selected semantic facts | Navigate exact overloads and argument slots without reproducing compiler resolution in the editor. Preserve references lost to constant folding. | `InvocationBinding`, `InitializerBinding` and `EmbeddingSupport.Compilation`. |
| Declaration and incomplete-source analysis | Answer bounded scope, type and argument queries while the buffer is unfinished, without presenting invalid source as executable code. | `EmbeddingSupport.analyzeDeclarations`, `analyzeIncomplete`, `CursorBinding` and `compiler.ast.partial`. |
| Source-tree copying | Probe proposed syntax on an independently parented tree without changing the source tree being inspected. | `AstNode.copyTree` and its covariant specializations. |
| Repository failure observations | Explain a required dependency that cannot be read while preserving repositories' existing best-effort search behavior. | `ModuleRepository`, `DirRepository`, `FileRepository` and the embedding dependency search. |
| Scoped compiler state and cache reporting | Release operation-owned listeners and temporary state, and replay cached diagnostics to the current attempt. | `Reporting`, `TransientThreadLocal` and `TypeInfo` diagnostic replay. |

### Preserve selected semantic facts

`InvocationBinding` records selected methods, function signatures, constructors and argument-to-
parameter mappings at the point where the compiler establishes them. The maps use AST identity;
they do not guess a target by spelling or make a dynamic function call look like a statically
selected method. `InitializerBinding` preserves validated source facts before a constant
initializer's temporary method is discarded. Without that step, a name can compile correctly but
disappear from references, rename and highlighting after constant folding.

These facts belong to a compilation attempt. An immutable map does not make the contained AST,
type constants or constant pool safe to share across workers. The language adapter copies the
facts it needs into detached models before releasing the attempt. Keeping this boundary explicit
is essential for a long-running editor that repeatedly replaces analyses of unsaved text.

### Analyze declarations and unfinished source explicitly

`analyzeDeclarations` resolves declaration structure without promising a fully validated or
emitted module. `analyzeIncomplete` retains supported incomplete syntax and reports cursor facts
from its real lexical context. Its cursor overloads support a broader set of editing positions
than the deliberately narrow trailing-expression overload; their Javadoc defines each boundary.
Unsupported damage remains a diagnostic rather than a guessed semantic result.

Incomplete nodes and their analysis helpers live in `org.xvm.compiler.ast.partial`:
`PartialQueries` coordinates inspection, `PartialCallResolver` and `PartialConstructionResolver`
fit candidates, `PartialArgument` builds disposable argument proposals, and `CursorScope` captures
visible names and resolves unfinished type headers. These helpers are package private.

Java subpackages do not share package access. One internal bridge, `ast.PartialAst`, lets these
helpers reuse ordinary parenting, validation, argument fitting, construction preparation and
scope lookup. Existing AST members retain their protected or package-private visibility. The
bridge delegates compiler operations; the partial package decides which incomplete syntax and
candidate facts to retain. It neither introduces another type checker nor exposes compiler
contexts as a host API. Embedding hosts continue to consume `EmbeddingSupport` results.

Candidate fitting uses ordinary compiler rules on trial syntax. A possible completion is not a
selected invocation, and partial analysis never supplies an executable module. This distinction
lets signature help and completion use authentic compiler information without weakening the
normal validation or emission contract.

### Copy source trees and preserve ordinary compiler behavior

The old pervasive `clone()` calls obscure an important distinction: `Object.clone()` starts with
a shallow field copy, while AST overrides selectively copy children and repair ownership. The
name alone does not tell a caller which bindings, transient state or parents remain shared, and
adding a field can silently change what a clone retains. A clone of a validated node is therefore
not equivalent to freshly parsed syntax. This is especially confusing for speculative editor
queries, which need to know exactly what a trial may mutate without affecting the original.

`copyTree()` names the source-tree copy contract and gives expression/statement callers covariant
return types. Its contract covers child ownership, source metadata and the existing stage-aware
copy behavior; it is not a general deep copy of an entire compiler or constant pool. Specialized
incomplete nodes copy and adopt their children explicitly. The existing `clone()` entry point
continues to use the same copying machinery so the two APIs cannot drift independently.

The compiler's callable typing and fitting corrections serve both ordinary compilation and
cursor probes. Boolean operand corrections preserve evaluation and side effects even when the
result is constant. These are compiler correctness rules, not alternate semantics enabled by an
LSP flag. The separate removal of the abandoned binary AST does not remove the source AST used
by tooling; legacy `.xtc` slots are still read or skipped as required for format compatibility.

Repository observation is similarly additive to search behavior: an unreadable candidate can
still be skipped while another repository supplies the module. A host receives a useful cause
when a required dependency ultimately cannot be found. The diagnostic guide below explains the
intentional Java API migrations, the behavior retained for ordinary CLI use and the regression
evidence; it does not claim universal source or binary compatibility for old Java embedding code.

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
the result, source-snapshot and cached-diagnostic APIs below complete the host-facing path.
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
no-argument constructor uses the shared `ErrorListener.DEFAULT_MAX_ERRORS` (512), `FIRST_ERROR`
selects one, and `UNLIMITED` disables the count limit. A retained FATAL requests an abort with
any budget. Pass an explicit limit to `ErrorList(int)` when an operation needs a different policy.

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

The 2026-10-09 focused run on this implementation passed **60 tests with zero failures, errors
or skips**. Earlier full-suite verification of the same production listener migration passed
418 executed Java tests, with 42 skips recorded separately. A forced XDK rebuild produced
24 modules matching the master baseline after normalizing creation timestamps. The separate
integration checkout combining the reporting changes with the embedding foundations also passed
the real compiler-consumer test with no skips. These checks cover different scopes; the 60 focused
tests are not presented as a substitute for compilation and integration validation.

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

### Embedding API boundaries

The embedding API combines the listener contract, scoped parser/validation reporting, compilation
outcomes, cached TypeInfo diagnostic replay, cancellation and host-supplied source snapshots.
Consumer tests exercise these mechanisms together through the public entry points.

The independent [repository read-failure fix](https://github.com/xtclang/xvm/pull/683) supplies
retained I/O causes; this API forwards them only after a required dependency search fails.
[The parser/branch correction](https://github.com/xtclang/xvm/pull/684) supplies the parent-abort,
default-budget and lexical-rollback behavior preserved here. Empty transient-local retention is
an independent utility fix. None of these APIs depends on an LSP implementation or editor.

The ordinary compilation result exposes partial progress without treating invalid code as
executable output. Selected-call facts and the separate declaration/incomplete-analysis APIs
extend that model as described in the [architecture overview](#compiler-architecture-for-language-tooling).
The language server remains a consumer outside `javatools`.

### Scoped parser and validation reporting

`Parser.attempt()` replaces `Parser.SafeLookAhead`: use `Attempt.keep()` in place of
`keepResults()`. A kept attempt merges its buffered diagnostics, including warnings; a discarded
attempt restores its tokens and recovery state. Nested attempts observe their parent's abort
request. This removes a public nested parser class and requires source migration/recompilation at
the same breaking release boundary described above.

Parser and resolver destinations use `Reporting` scopes, closed in reverse order with
try-with-resources. `NameResolver.getErrorListener()` is null outside `resolve`; callbacks borrow
the caller's non-null listener only during that call. These scopes restore destinations on every
exit. They do not make a parser, resolver, AST or listener safe for concurrent use. Lexical
reports also follow the active attempt, preserving lexical rollback from the parser correction.
Module-name-only scans can supply an explicit discard listener when reports are unwanted.

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

### Compile a host snapshot and inspect its outcome

```java
ErrorList reports = new ErrorList();
ErrorListener operation = ErrorListener.cancellable(reports, cancelled::get);
EmbeddingSupport.Compilation result = embedding.compileModule(
        new Source(text, documentName), dependencies, operation);

if (result.succeeded()) {
    ModuleStructure executable = result.module();
}
if (result.parsed() != null) {
    // Inspect ordinary parsed syntax, even if semantic validation subsequently failed.
}
if (result.pool() != null) {
    // Inspect structures built before a semantic failure, on the compiling worker.
}
```

`Compilation` separates a successful executable module from the file structure and assembled AST
that an attempt reached. Loading/parsing failures can leave no AST; semantic failures can leave a
file and pool without an executable module. Returning these objects does not make them immutable
or safe to share between workers. The host owns their lifetime and must detach any facts it shares.
The existing String/File convenience methods delegate to this path and retain their module/null
or boolean result shapes. A compilation creates a fresh compiler and build repository; hosts must
serialize attempts sharing mutable repositories.

`compileModule(ModuleInfo, ...)` runs the same source discovery, package assembly and resource
association used by the CLI. A host can override `readSource` for unsaved text, `sourceEntries`
for added/deleted members, and the protected source-identity constructor for a wholly virtual
module. Membership, text and resource identity must describe one consistent snapshot. Construct a
fresh `ModuleInfo` per attempt: it caches its assembled tree. Ordinary file constructors retain
disk discovery and reading.

Errors stay structured. A non-module/empty source gets a source diagnostic. Failed dependency
resolution can include the original repository path and I/O cause. Successful fallback past an
unreadable file does not fail a valid compilation. Unexpected exceptions and assertions are reported
as internal failures even when an earlier source error was already collected. FATAL reports reach
the host before the CLI console's deliberate exception can stop execution.

`footprint(result)` reads the supplied compilation pool and repository counts without starting an
interpreter. `ensureRuntimePool()` explicitly initializes the runtime if needed; the deprecated
`getConstantPool()` retains that behavior. A runtime pool is not the pool of a failed compilation.

### Report diagnostics from cached type information

Structures no longer store, inherit or discover a listener through an ambient pool. Pass the
operation's listener to `XvmStructure.log` and `TypeConstant.ensureTypeInfo(errors)`. The no-argument
TypeInfo lookup is an explicit silent metadata query. A successful TypeInfo build retains an
immutable diagnostic list; a later explicit lookup replays it to the new listener, including when
it returns the same cached TypeInfo instance. Receiving `ErrorList` instances deduplicate repeated
reports; a raw callback deliberately receives each replay. Cache invalidation causes a fresh build
and replaces the retained reports.

This matters when speculative work builds a generic type before the selected source use: the
later caller must still hear an ignored-annotation warning. Tests cover source and serialized
library types, silent-first/cache-hit ordering, distinct reporting owners, final selected uses
and partial structures after semantic failure.

### Cancel obsolete work without manufacturing errors

`ErrorListener.cancellable` decorates the operation listener, preserving its reporting, state,
branching and merging while polling the host predicate at abort checkpoints. A valid token stream
also polls; cancellation cannot depend on finding a source error. Module-member loading and phase
boundaries retain the same policy. This is cooperative cancellation, not interruption: a host must
keep its predicate true once an attempt becomes obsolete. Other attempts have separate policies.

The direct consumer suites `EmbeddingDiagnosticsTest`, `EmbeddingSourceSnapshotTest` and
`TypeInfoDiagnosticsTest` use compiled modules supplied by Gradle, with no production adapter.
Run them from the repository root:

```bash
./gradlew :lang:lsp-server:test \
  -PincludeBuildLang=true -PincludeBuildAttachLang=true \
  --tests 'org.xvm.lsp.adapter.Embedding*Test' \
  --tests org.xvm.lsp.adapter.TypeInfoDiagnosticsTest \
  --rerun-tasks --no-build-cache
```

The earlier numerical receipts above describe the original listener migration. Validation of the
combined branch must additionally include these consumer suites and a fresh XDK build; those
historical receipts alone do not validate the enlarged scope.

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
