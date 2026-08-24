# Logging, Diagnostics, And Reproducer Audit

This document belongs to the reentrancy and memory-model hardening audit set.

Audit date: 2026-08-23

Scope: `ErrorListener`, compiler diagnostics, runtime/JIT exception reporting,
developer logging, ownership diagnostics, and current test/reproducer practice.
This is a design audit only. It does not replace `logging-strategy.md`; that
document is the concrete disabled-cost logging direction. This audit explains
why the current diagnostic style blocks reentrant runtime work, parallel
containers, incremental compilation/LSP, and reliable debugging.

## Search Commands

The audit used broad source-shape scans first, then inspected representative
sites by hand:

```bash
rg -n "ErrorListener" javatools docs manualTests lang/lsp-server \
  --glob '!**/build/**'
```

```bash
rg -n "System\.(out|err)\.print(?:ln)?|\.printStackTrace\(" \
  javatools/src/main/java javatools/src/test xdk/src/test manualTests \
  --glob '*.java' --glob '*.x' --glob '!**/build/**'
```

```bash
rg -n "catch\s*\([^)]*(?:ignore|ignored|_)\)|catch\s*\([^)]*\)\s*\{\s*\}" \
  javatools/src/main/java javatools_bridge/src/main/x \
  javatools_jitbridge/src/main/java lib_ecstasy/src/main/x \
  lib_jsondb/src/main/x lib_web/src/main/x manualTests/src/main/x \
  --glob '!**/build/**'
```

```bash
rg -n "ErrorListener\.BLACKHOLE|branch\(|merge\(|hasSeriousErrors\(|\
isAbortDesired\(|isSilent\(|hasError\(" \
  javatools/src/main/java/org/xvm/compiler \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/tool \
  --glob '!**/build/**'
```

```bash
rg -n "TestSimple|negative|should fail|compile.*fail|diagnostic|golden" \
  javatools/src/test manualTests lang/lsp-server/src/test \
  --glob '!**/build/**'
```

Focused production-scale scans on this branch found:

- 67 direct `System.out`, `System.err`, or `printStackTrace(...)` sites in
  `javatools/src/main/java`, `javatools_jitbridge/src/main/java`, and
  `plugin/src/main/java`;
- 112 broad `Throwable`, `Exception`, or `RuntimeException` catch sites in the
  same Java production/plugin areas;
- 62 ignored or empty catch sites in the inspected Java/XTC production areas
  (`javatools`, bridge code, `lib_jsondb`, `lib_web`, and `lib_ecstasy`).

The counts are not themselves the must-fix list. Parser lookahead,
authentication failure, debug rendering, and cleanup best-effort catches can be
legitimate. The counts show why the project needs a diagnostic policy and
source-shape gates: the risky patterns are widespread enough that fixing only
the latest crash site will not prevent the next hidden owner or failure-channel
bug.

## Executive Finding

The tree has several partial diagnostic systems:

- `ErrorListener` for compiler/source/verifier errors.
- `Console` and `Launcher.log(...)` for user-facing launcher output.
- `OwnershipDiagnostics` for opt-in runtime owner validation and dumps.
- Gradle logging in build logic.
- LSP diagnostic model tests.
- Ad hoc stderr/stdout/stack printing in runtime, compiler, ASM, JIT, and
  native templates.

Those systems do not compose. A runtime ownership failure can be printed to
stderr, converted to a catchable `"Run-time error"`, swallowed behind
`False`, hidden by `ErrorListener.BLACKHOLE`, or reduced to `e.getMessage()`.
The result is not just ugly output. It prevents a reviewer, test, LSP client, or
embedding host from answering the only questions that matter during reentrancy
work:

- Which container, module, service, frame, native template, constant pool, or
  cache owner was involved?
- Which phase or host boundary observed the failure?
- Did the operation actually fail, or did the launcher/runtime report success
  after printing something?
- Is this diagnostic stable enough for a test assertion?
- Can two parallel containers produce separate traces without interleaving
  unrelated state?
- Can an incremental compiler or LSP attach a diagnostic to the right document
  version and source range?

The current answer is often "read stderr and guess". That is not enough for a
reentrant runtime.

## Why This Blocks Reentrancy

Reentrancy and parallel-container correctness are owner problems. Diagnostics
that do not carry owner identity are actively harmful because they make wrong
owner use look like ordinary compiler or runtime noise.

Examples:

- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:322` prints
  `WARNING: Foreign method chain...` and disables caching. That is exactly the
  kind of owner/pool violation that should say which `ClassComposition`, which
  container, which pool, and which method cache was involved.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5971`
  prints an `isA()` recursion rejection with only left/right type strings. There
  is no pool id, relation cache owner, module, or correlation with the caller
  that requested the assignability check.
- Master `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554`
  caught `Throwable`, printed two strings, and raised a generic XTC exception.
  A Java assertion proving wrong ownership could become a normal language
  exception that user code may catch. This branch fixes the op-loop policy.
- Master `javatools/src/main/java/org/xvm/javajit/JitConnector.java:142`
  handled an unhandled XTC exception by printing to stdout and leaving the
  default result path available. That could make a failed run look successful.
  This branch fixes the connector result boundary.

Incremental compilation and LSP have a different version of the same problem.
They need diagnostics that are stable values: URI, range, code, severity,
message, document version, module, phase, and related context. A printed string
from a compiler branch or a swallowed `BLACKHOLE` error cannot be turned into a
reliable editor diagnostic.

## Concrete Architecture Failures

These are not complaints about cosmetics or console style. They are API and
process failures that make the compiler/runtime impossible to embed reliably.

### `ErrorListener` Is Not A Diagnostic System

`ErrorListener` describes source and XVM-structure errors. It does not describe
compiler/runtime events.

Concrete shape:

- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:36` is one
  functional `log(ErrorInfo)` sink.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:53` and `:72`
  construct `ErrorInfo` from either source positions or one `XvmStructure`.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:85` creates a
  branch, but the branch has no phase id, owner id, speculative-attempt id, or
  correlation id.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:223` stores one
  `XvmStructure`; the TODO at `:228` admits structure diagnostics still cannot
  ask the structure for source and location.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:168` renders runtime
  errors to a string, throws `IllegalStateException` for errors, and prints
  non-errors to stdout at `:173`.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:433` exposes
  `BLACKHOLE`, which makes diagnostic suppression indistinguishable from a
  deliberate speculative probe unless the caller documents the reason.

That means the same compiler run can have structured source errors, silent
speculative errors, stderr conversion traces, stdout runtime warnings, and
thrown Java exceptions with no shared identity. A host cannot ask "what did the
compiler decide during method lookup?" or "which container owned the type-info
graph that produced this diagnostic?" because no event model exists for those
questions.

This is broken even in one thread. Compiler phases are iterative and
speculative. If branch A suppresses diagnostics with `BLACKHOLE`, branch B
prints a conversion miss, and branch C finally emits an `ErrorInfo`, the host
sees three unrelated channels for one decision. In an LSP, that becomes
unusable: diagnostics must be attached to a URI, document version, source
range, compiler phase, and request id. A mutable text stream cannot be
reconciled with the current editor document after the next incremental compile
has already started.

Proper shape:

```java
record DiagnosticEvent(
        Severity severity,
        String code,
        DiagnosticKind kind,
        DiagnosticContext context,
        List<SourceSpan> spans,
        Map<String, ?> attributes,
        Throwable cause) {
}
```

`ErrorListener` should be one bridge that emits `DiagnosticEvent` values for
source/compiler errors. Runtime ownership checks, type-relation traces,
constant-pool registration, module loading, and native callback lifecycle
should emit the same event shape through explicit sinks. Rendering to console,
SLF4J, LSP, golden-test JSON, or ownership dumps should be subscribers.

### Compiler Decisions Are Printed Instead Of Emitted

Two conversion paths currently print a compiler decision:

- `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:104`
  prints `No conversion found for ...`.
- `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:814` prints
  the same message from required-type fitting.

The current branch keeps legacy behavior and marks the print with TODOs because
changing constant-folding semantics is a separate compiler change. The design
failure is still real: a conversion decision needs at least expression source,
required type, actual type, conversion method, whether this was constant
folding or runtime fallback, and the compile phase. None of that is present in
the text. A regression test cannot assert why the conversion path was selected
without parsing stderr and guessing which expression printed the line.

This directly blocks incremental compilation. A type-system change that affects
`@Auto` conversion or constant folding should be testable as a stable decision
trace. Today, if a developer edits `TestSimple.x`, reads stderr, and then
rewrites the file for the next bug, the decision evidence disappears.

Proper shape:

```java
if (diagnostics.isTraceEnabled(DiagnosticArea.CONVERSION)) {
    diagnostics.emit(ConversionDecision.runtimeFallback(
            expression.sourceSpan(),
            typeActual,
            typeRequired,
            idConversion,
            "constant-folding conversion returned null"));
}
```

That event is disabled by default. When enabled it is structured, owner-aware,
and assertable.

### Assembly And Code Generation Can Print And Continue

Two production sites are especially dangerous:

- Master `javatools/src/main/java/org/xvm/tool/Compiler.java:471` caught
  `Throwable`, prints `"Failed to generate code..."`, prints the Java stack,
  logs a formatted message, and continues the retry loop.
- Master `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2077` caught
  `UnsupportedOperationException` from op assembly, printed a method name, and
  continued to write the method with whatever op bytes were present (none).

These are must-fix because they make failure non-transactional. Code generation
and method assembly are not optional trace points; they publish artifacts. A
compiler that continues after a VM `Error`, an ownership assertion, or an
unsupported op-generation path can write an invalid `.xtc` or leave the caller
with a successful build plus stderr. That is bad in one thread and catastrophic
for same-process incremental compilation, where stale compiler state and stale
artifacts can be reused for later runs.

This branch fixes both boundaries. The compiler code-generation boundary
rethrows `Error` directly and routes unchecked compiler defects through the
fatal launcher path with the cause intact. Method op assembly no longer prints
and serializes a zero-op body; it fails module assembly with the artifact
identity and the original cause:

```java
try {
    m_code.ensureAssembled(m_registry);
} catch (UnsupportedOperationException e) {
    throw new IllegalStateException("op assembly failed for method \""
            + getIdentityConstant().getPathString() + "\" in module \""
            + getFileStructure().getModuleId().getName() + '"', e);
}
```

`IllegalStateException` is deliberate: op-assembly failure is a compiler
defect, not an environmental condition, so it must not be encoded as
`IOException`. The emit path (`Compiler.emitModules`, `Compiler.addVersion`,
repository `storeModule`) treats `IOException` as an I/O problem to log or
retry; classifying a defect that way would let a corrupt-artifact bug be
swallowed as a failed version stamp. The unchecked exception propagates
through `FileStructure.writeTo` to the launcher's terminal `Throwable`
handler, so the build fails with the cause intact. A dedicated
artifact-assembly exception type remains a design option for the diagnostics
PR, but it is not needed for correctness.

The compiler boundary should decide whether this is a user diagnostic or an
internal compiler defect. The assembly method itself must not print and then
pretend the artifact is still valid.

### Repository And Module Loading Collapse Causes Into Text

Representative sites:

- `javatools/src/main/java/org/xvm/asm/FileRepository.java:206` prints
  `"Error loading module from file..."` and returns `null`.
- `javatools/src/main/java/org/xvm/asm/DirRepository.java:371` prints the same
  shape and returns `null`.
- `javatools/src/main/java/org/xvm/asm/LinkedRepository.java:109` and `:130`
  print module-load errors to stderr.
- `javatools/src/main/java/org/xvm/runtime/MainContainer.java:203` prints a
  missing method and returns from invocation setup.

Best-effort repository scans may need to skip broken candidates. Requested
module loads do not. A host launching one named module must be able to tell the
difference between "not found", "bad module bytes", "wrong repository",
"module belongs to a different constant-pool owner", and "loader bug". Text on
stdout is neither an exception contract nor a diagnostic contract.

Proper shape:

```java
sealed interface ModuleLoadResult {
    record Loaded(ModuleStructure module) implements ModuleLoadResult {}
    record NotFound(String moduleName) implements ModuleLoadResult {}
    record Failed(ModuleLoadContext context, Throwable cause) implements ModuleLoadResult {}
}
```

The launcher can render this as one line. The LSP and plugin can keep the cause,
module path, repository id, and request id.

### Runtime And JIT Boundaries Used Text As Control Flow

The branch already fixes several instances, but they are worth listing here
because they show the pattern:

- Master `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554`
  caught every `Throwable`, printed Java/XTC stacks, and converted the defect
  to a generic XTC `"Run-time error"`.
- Master `javatools/src/main/java/org/xvm/javajit/JitConnector.java:142`
  printed an unhandled generated XTC exception and could leave the connector
  result as success.
- Master `javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:497`
  treated async completion failure as impossible and relied on `assert false`.
- Master `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:229`
  discarded the `CompletableFuture` returned by `scheduleIO(task)`.

These bugs all have the same root: text output or debug assertions were used
where the owner boundary needed a machine-visible failure channel. A stress
test cannot prove reentrancy if a worker thread can fail, print something, and
let `join()` return success.

Proper shape:

```java
container.recordRuntimeFailure(
        RuntimeFailure.unexpectedWorkerFailure(container, service, operation, cause));
connector.join(); // throws if the owner recorded a terminal failure
```

Language exceptions remain language exceptions. Java VM/runtime defects remain
host failures with owner context.

### Manual Reproducers Are Not Regression Tests

The tree contains real test infrastructure, but the bug-hunting workflow still
leans on mutable scratch modules:

- `manualTests/src/main/x/TestSimple.x:1` is the current scratch module. Today
  it performs live DNS/network lookups against `welcome.xqizit.cloud` and
  `xqizit.cloud`.
- `manualTests/build.gradle.kts:123` says several files are meant to be
  compiled and run manually and should be moved elsewhere.
- `manualTests/build.gradle.kts:133` excludes `TestSimple.x` from the source
  set.
- `manualTests/src/main/x/compiler.x:16` explicitly compiles
  `src/main/x/TestSimple.x`.
- `manualTests/build.gradle.kts:592` to `:594` document that one test is
  failing, the runner swallows/prints exceptions, and negative/positive tests
  need real integration.

That is a process-level architecture bug. A scratch file is fine for discovery.
It is not acceptable as the final carrier of a compiler/runtime regression. If
the reproducer is overwritten after the fix, the project loses the proof that
the bug existed, the proof that the fix addresses it, and the guard against a
future regression.

Proper workflow:

```text
manualTests/src/main/x/TestSimple.x
    discovery scratch only, never final proof

javatools/src/test/resources/repro/<area>/<issue-name>.x
    stable source fixture

javatools/src/test/java/org/xvm/<area>/<IssueName>Test.java
    compiles/runs the fixture and asserts structured result

manualTests/build/reports/reentrancy/<run-id>.json
    optional stress evidence with ownership snapshots
```

No fix for a compiler/runtime bug should land with only "I changed
`TestSimple.x` and saw the right stdout" as evidence.

## `ErrorListener` Audit

`ErrorListener` is useful, but it is too narrow for the current runtime and
tooling needs.

The core API is a single functional method:

- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:36` accepts only
  `ErrorInfo`.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:53` and `:72` build
  `ErrorInfo` from severity, code, parameters, source range, or one
  `XvmStructure`.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:85` creates a
  branch, and `:94` merges it.

`ErrorInfo` carries user/source diagnostics, not runtime diagnostic context:

- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:203` stores severity,
  code, params, source, and source positions.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:223` stores severity,
  code, params, and one `XvmStructure`; the constructor has a TODO at `:228`
  saying the structure should be able to provide source and location.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:315` builds a string
  UID from severity, code, params hash, source file, positions, or structure
  description. It is a duplicate filter, not a diagnostic identity.
- `javatools/src/main/java/org/xvm/asm/ErrorListener.java:345` renders the
  diagnostic into display text.

Missing fields:

- correlation id for a compile/run/request/container operation,
- owner/container id,
- module id and module version,
- constant-pool id,
- service/fiber/frame identity,
- runtime phase or compiler phase,
- related source spans,
- underlying Java cause,
- machine-checkable assertion kind,
- structured key/value context,
- document URI/version for incremental/LSP,
- stable event id separate from localized text.

The built-in listeners show the mismatch:

- `ErrorListener.BLACKHOLE` at
  `javatools/src/main/java/org/xvm/asm/ErrorListener.java:433` discards
  everything and returns `false`.
- `RuntimeErrorListener.log(...)` at
  `javatools/src/main/java/org/xvm/asm/ErrorListener.java:168` converts the
  error to a string, throws `IllegalStateException` for errors, and prints
  warnings/info to stdout at `:173`. It has no owner context and no cause.
- `ErrorList.log(...)` at
  `javatools/src/main/java/org/xvm/asm/ErrorList.java:34` deduplicates by
  string UID and stores values in a mutable `ArrayList`. That is fine for a
  single compile collector; it is not a parallel diagnostic event store.
- `BranchedErrorListener.log(...)` at
  `javatools/src/main/java/org/xvm/asm/ErrorList.java:177` rewrites a structure
  diagnostic to the branch AST node source range when a node exists. That is
  useful for speculative compiler errors, but it makes branch context implicit.

Concrete compiler/type examples:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1742`
  branches an `ErrorListener` while building `TypeInfo`; `:1785` creates a
  nested branch for deferred type info. The branch carries no type-info build
  id, pool id, or dependency edge. When this fails, the reviewer has a text
  error list and a type string, not a structured graph of "type A in pool P
  needed type B in pool Q".
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:2624`
  switches to `ErrorListener.BLACKHOLE` when a contribution type info is
  incomplete. That may be correct for speculative recovery, but it means tests
  cannot assert which diagnostics were suppressed or why.
- `javatools/src/main/java/org/xvm/tool/Launcher.java:590` receives
  `ErrorInfo`, updates worst severity, renders `err.toString()` to console, and
  forwards the same `ErrorInfo`. The launcher has a process/tool context, but
  the event that leaves the launcher does not gain that context.
- `javatools/src/main/java/org/xvm/tool/Compiler.java:570` suppresses most
  `VERIFY` diagnostics when compiler errors exist. That may be user-friendly
  console behavior, but it is not a structured diagnostic policy that a test or
  LSP client can inspect.

What should have been written instead:

```java
interface DiagnosticSink {
    boolean emit(DiagnosticEvent event);
}

record DiagnosticEvent(
        Severity severity,
        String code,
        String messageKey,
        Object[] params,
        DiagnosticContext context,
        List<SourceSpan> spans,
        Throwable cause) {
}

record DiagnosticContext(
        String correlationId,
        String phase,
        String moduleName,
        String containerId,
        String serviceId,
        String frameId,
        String constantPoolId,
        String ownerPath) {
}
```

`ErrorListener` should become one adapter on top of this sink for
source/compiler diagnostics:

```java
final class ErrorListenerBridge implements ErrorListener {
    private final DiagnosticSink sink;
    private final DiagnosticContext base;

    @Override
    public boolean log(ErrorInfo err) {
        return sink.emit(DiagnosticEvents.fromErrorInfo(err, base));
    }
}
```

Tests should assert `DiagnosticEvent` fields directly. Console rendering,
localized messages, LSP publishing, and logger output should be subscribers or
bridges, not the primary representation.

## Logging Framework Audit

There is no integrated runtime/compiler logging layer. The existing
`docs/reentrancy/logging-strategy.md` already makes the right implementation
case: use the SLF4J 2.x API already present in the build, keep standalone
`javatools` on a no-op provider by default, and require explicit guards around
disabled trace/debug paths.

The code base still has direct output and one-off debug prints. Some
representative master findings are fixed in this branch; the remaining rows are
still open:

| Site | Current behavior | Why it is bad design |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/tool/Compiler.java:471` | Master caught `Throwable`, printed "Failed to generate code..." to stderr, printed the stack, logged a message with `{}` parameters, and continued; fixed in this branch. | Two reporting paths competed, one was unstructured, and the catch continued the compiler after a possible VM/compiler defect. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554` | Master caught `Throwable`, printed Java stack, printed XTC frame stack, then raised `"Run-time error: " + e`; fixed in this branch. | The owner/fiber/container evidence was lost as machine data, and Java defects could become user-catchable language exceptions. |
| `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:104` | Prints `No conversion found for ...` while preserving legacy constant-folding behavior. | A compiler decision about constant conversion is neither an `ErrorListener` diagnostic nor a trace event; tests cannot assert the conversion decision. |
| `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:814` | Same `No conversion found` stderr path during fit/validation. | This should be correlated with required type, actual type, expression, source span, and conversion method search. |
| `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:322` and `:334` | Prints "Foreign method chain" and "Foreign nested method", with TODO remove. | This is owner/pool evidence. Printing and moving on makes the important fact easy to miss. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5971` | Prints a process-wide de-duplicated recursion message. | The process-wide suppression hides repeated failures across containers and does not include pool or caller context. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:721` | `sendError` calls `t.printStackTrace()` and then may print a second stack in the `IOException` catch. | Web/native callback boundaries need server/container/route/request context and structured failure state, not default stderr. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:780`, `:787`, `:880` | TLS/route traces print timestamped strings to stderr. | Trace output is not level-controlled, not routed by host, and not correlated with server/container/route ownership. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java:151` and `:501` | Temporarily prints stack traces before raising obscure XTC exceptions. | The TODO admits this is temporary; it still loses typed cause and owner context. |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java:206` and `DirRepository.java:371` | Master printed load failure to stdout and returned null; fixed in this branch for requested loads (typed `ModuleLoadException` with retained cause; scans still skip broken candidates, printing to stderr). | Repository load failure became "module not found" unless the caller already knew this exact stdout text mattered. |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2077` | Master caught `UnsupportedOperationException` while assembling ops, printed, and continued to serialize the method with zero op bytes; fixed in this branch. | Assembly could proceed after a code-generation failure, persisting a corrupt method body while the build looked successful. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3498` | Prints "Unsupported tuple type..." as a soft assert and returns incompatible. | A type-system invariant failure is a typed diagnostic, not stderr. |

Trace mode should be able to write the book on a failing run. A proper
zero-cost-disabled logging layer would allow narrow trace categories for:

- type conversions and `@Auto` method selection,
- `TypeConstant.isA` and relation cache decisions,
- compiler phase retries and last-attempt failures,
- bytecode/AST byte counts during method assembly,
- module repository loading/linking/cache hits,
- owner assignment for containers/templates/compositions/handles,
- constant-pool registration/adoption and late-publication checks,
- native callback registration, rollback, and unregister lifecycle,
- service/fiber scheduling and async boundary failures.

Disabled trace/debug logging must not allocate, call `toString()`, compute
`getValueString()`, format source snippets, mutate MDC, or build diagnostic
dumps. The guarded pattern is:

```java
if (TYPE_LOG.isTraceEnabled()) {
    TYPE_LOG.trace("type.convert result={} from={} to={} method={} pool={} phase={}",
            result,
            diagnosticTypeName(typeFrom),
            diagnosticTypeName(typeTo),
            diagnosticMethodName(method),
            diagnosticPoolId(pool),
            phase);
}
```

That is not a replacement for `ErrorListener`. It is the developer trace plane
that makes the existing compiler/runtime decisions inspectable without changing
normal behavior.

## Runtime Exception Hygiene

The logging problem is inseparable from exception hygiene. A host boundary that
prints or wraps without cause destroys the only evidence needed to debug
ownership and reentrancy defects.

Must-fix exception/logging boundaries:

| Site | Failure mode | Why it is Must-fix |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:142` | Master printed unhandled XTC exceptions at `:150`; fixed in this branch by keeping a non-zero result. | A failed program could report success after a printed exception. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554` | Master printed every `Throwable` from op execution and translated it to a generic XTC runtime error; fixed in this branch. | Java `Error`, ownership assertion failures, and runtime defects could become normal language exceptions. |
| `javatools/src/main/java/org/xvm/tool/Compiler.java:471` | Master code generation caught `Throwable`, printed, logged, and kept looping; fixed in this branch. | A compiler defect could be downgraded to console noise and later phases could run on corrupted state. |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2077` | Master printed the op-assembly failure and still wrote the method with zero op bytes; fixed in this branch. | This could produce or persist a broken module while the build path only saw stderr. |
| `javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:497` | Master used a "must not happen" async failure path that only asserted false inside a callback; fixed in this branch. | With assertions disabled, the code could continue with null values or incomplete failure propagation. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:229` | Master discarded the `CompletableFuture` returned by `scheduleIO(task)` for queued writes; fixed in this branch. | Async IO failure could disappear after the native method returned OK. |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java:206` and `DirRepository.java:371` on requested-module paths | Master printed the load failure and converted it to null; fixed in this branch. Requested loads now throw `ModuleLoadException` with file and cause, best-effort scans still skip broken candidates, and `LinkedRepository` searches rethrow only when the whole search fails. | A concrete repository failure could become indistinguishable from a missing module. |

Should-fix boundaries:

- `javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java:155`
  and `:180` catch `Throwable`/`Exception`, ask whether the reason should be
  reported, and return conditional `False`. Name-not-found and Java execution
  failure should not be identical.
- `javatools_bridge/src/main/x/_native/web/RequestInfoImpl.x:433` swallows
  response-send failures. A broken client connection may be benign; an owner or
  context failure is not.
- `javatools_bridge/src/main/x/_native/web/RequestInfoImpl.x:463` swallows
  observer failures. Instrumentation bugs should not necessarily fail the
  response, but they need a debug/warn path.
- `javatools/src/main/java/org/xvm/runtime/MainContainer.java:203` prints
  missing method and returns from `invoke0`. A programmatic host boundary should
  return a typed failure with module/method context.
- `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:820`
  and `:832` return empty/null for key-store failures. Some TLS manager APIs
  force this shape, but the server diagnostics should still retain route/key
  context.

What should have been written instead:

```java
final class RuntimeBoundaryException extends RuntimeException {
    private final RuntimeFailureContext context;

    RuntimeBoundaryException(String message, RuntimeFailureContext context, Throwable cause) {
        super(message, cause);
        this.context = context;
    }
}

record RuntimeFailureContext(
        String containerId,
        String moduleName,
        String serviceId,
        String frameId,
        String nativeTemplate,
        String operation) {
}
```

Host boundaries should preserve cause and owner context:

```java
try {
    return runNative(frame);
} catch (Error e) {
    throw e;
} catch (RuntimeException e) {
    throw new RuntimeBoundaryException(
            "native runtime failure",
            RuntimeFailureContext.from(frame, templateName, methodName),
            e);
}
```

If a Java exception must become an XTC exception, the translation should still
emit a `DiagnosticEvent` with the Java class, cause, container, service, frame,
and native method. User-facing text can stay small; debugging data cannot.

## Testing And Reproducer Audit

There are useful tests in the tree:

- `javatools/src/test/java/org/xvm/compiler/ParserTest.java:57` asserts a
  missing semicolon produces `Parser.MISSING_SEMICOLON`.
- `javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java:64`
  has a test listener, but it stores `err.getCode() + ": " + err` as strings.
- `lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XtcLanguageServerTest.kt:77`
  verifies that LSP publishes diagnostics.
- `javatools/src/test/java/org/xvm/api/InterpreterConnectorTest.java:71`
  asserts ownership diagnostics after parallel connector loading.
- `manualTests/build.gradle.kts:522` and `:562` define `runParallelStress` and
  `runDirectSequenceStress`, which are the right direction for race discovery.

The gap is not "no tests". The gap is that many real compiler/runtime bugs are
still found by editing a scratch module, watching stdout/stderr, and then not
turning the reproducer into a stable regression.

Concrete examples:

- `manualTests/src/main/x/TestSimple.x:1` is a mutable module named
  `TestSimple`. Its current body performs live DNS/network lookups against
  `welcome.xqizit.cloud` and `xqizit.cloud` at `:13` and `:17`. That is not a
  stable compiler/runtime regression; it depends on external network state.
- `manualTests/build.gradle.kts:123` says some files are meant to be compiled
  and run manually and should be moved elsewhere. `TestSimple.x` is excluded at
  `:133`.
- `manualTests/src/main/x/compiler.x:16` explicitly compiles
  `src/main/x/TestSimple.x`. That makes the mutable scratch module part of a
  manual compiler exercise.
- `manualTests/build.gradle.kts:111` says negative "should fail" sources are
  filtered out through source sets, but there is no stable negative/golden
  compiler diagnostic suite attached to those sources.
- `manualTests/build.gradle.kts:592` excludes a failing test and `:594` says
  the project should integrate with xUnit or implement negative and positive
  tests. The comment is directionally correct: the harness should not rely on
  runner output as the pass/fail authority.
- `manualTests/build.gradle.kts:512` documents `runParallelStress` as local and
  intentionally not wired into CI aggregate tasks. That is appropriate for a
  race finder, but any discovered failure needs a stable focused regression.
- `manualTests/build.gradle.kts:547` documents `runDirectSequenceStress` for
  same-JVM direct execution. This is a strong reentrancy tool, but the output is
  still task/process oriented, not a structured sequence report that captures
  per-run diagnostics.

Correct workflow:

1. Use `TestSimple.x` or `runOne` only as a temporary scratchpad.
2. When the bug is understood, move the smallest source into a named stable
   module or Java test resource, for example
   `javatools/src/test/resources/repro/type-conversion/<case>.x`.
3. Add a Java, xUnit, LSP, or Gradle-plugin test that compiles/runs that source
   and asserts structured diagnostics, exit status, and owner snapshots.
4. If the failure requires parallelism, keep a stress command, but also add the
   smallest deterministic same-JVM sequence test that fails without timing luck.
5. If the bug is only observable with trace data, add a diagnostic snapshot
   assertion instead of expecting a human to read stderr.

What should have been written instead:

```java
@Test
void missingConversionHasGoldenDiagnostic() {
    var result = compileFixture("repro/conversions/missing-auto.x",
            DiagnosticOptions.captureStructuredEvents());

    assertEquals(1, result.errors().size());
    assertEquals("COMPILER-MISSING-CONVERSION", result.errors().getFirst().code());
    assertEquals("TestMissingAuto", result.errors().getFirst().context().moduleName());
    assertEquals("Validated", result.errors().getFirst().context().phase());
    assertThat(result.trace("org.xvm.compiler.convert"))
            .anyMatch(event -> event.has("fromType", "IntLiteral")
                    && event.has("toType", "UInt8"));
}
```

For ownership:

```java
@Test
void repeatedDirectRunsDoNotReuseForeignOwners() {
    var report = runSameJvmSequence("TestProps", "TestReflection");

    assertTrue(report.success());
    assertThat(report.ownershipSnapshots())
            .allMatch(snapshot -> snapshot.foreignOwners().isEmpty());
}
```

The important point is that the assertion is against structured state. The
human-readable dump is secondary evidence.

## Classification

### Must-fix

Must-fix items are limited to defects that can hide failure, report success
after failure, or make ownership/reentrancy bugs unobservable.

| Area | Sites | Required outcome |
| --- | --- | --- |
| Runtime op boundary | `ServiceContext.java:554` | Done in this branch: do not catch all `Throwable` and translate runtime defects into generic catchable XTC exceptions. Preserve Java cause and owner/frame context. |
| JIT host boundary | `JitConnector.java:142` | Done in this branch: unhandled XTC exceptions produce a non-zero result. Printing is secondary. |
| Compiler codegen boundary | `Compiler.java:471` | Done in this branch: do not continue after `Throwable`. Rethrow fatal errors, preserve cause, and attach compiler/module/phase context through the launcher failure path. |
| Method assembly | `MethodStructure.java:2077` | Done in this branch: op-assembly failure throws `IllegalStateException` with method and module identity and the `UnsupportedOperationException` cause, instead of printing and serializing a zero-op method body. |
| Async future callback | `xFuture.java:497` | Done in this branch for `Future.and`: do not rely on `assert false` for impossible async failure. Complete exceptionally or route through runtime failure diagnostics. |
| Discarded async IO future | `xRawOSFileChannel.java:229` | Done in this branch for `submit`: retain the scheduled write future and route late failure through the container failure channel. |
| Requested module load | `FileRepository.java:206`, `DirRepository.java:371` | Done in this branch: requested loads throw typed `ModuleLoadException` with file and retained cause, scans stay best-effort, and `LinkedRepository` searches rethrow only when the whole search fails (`ModuleRepositoryLoadFailureTest`). |
| Test pass/fail authority | `manualTests/build.gradle.kts:592` to `:594` | Runner paths must not swallow/print exceptions as success. Stress/manual runners need a machine pass/fail channel. |

### Should-fix

Should-fix items are not always immediate correctness bugs, but they are
blocking diagnostic quality:

- Introduce a guarded SLF4J facade for runtime/compiler developer trace logs.
- Add `DiagnosticSink` or equivalent structured event model behind
  `ErrorListener`.
- Stop direct stderr/stdout in compiler/type-system decisions.
- Replace message-only native exception translations with cause-preserving
  translations and diagnostic events.
- Move mutable `TestSimple.x` reproducers into stable named regression tests.
- Add golden compiler diagnostics for type conversion, method lookup, module
  loading, and source/span stability.
- Add ownership diagnostic snapshots to same-JVM and parallel-container tests.
- Distinguish expected conditional failures from native execution failures in
  DNS, TLS, HTTP, crypto, and file-system templates.

### Design Backlog

These need design slices, not drive-by edits:

- Define `DiagnosticEvent`, `DiagnosticContext`, `SourceSpan`, and the
  `ErrorListener` bridge.
- Define owner-scoped trace context that is explicit, not ambient thread state.
- Decide how LSP document version/URI maps onto compiler source diagnostics.
- Decide which runtime failures are user-catchable XTC exceptions and which are
  host/runtime terminal failures.
- Define stable ids for containers, pools, modules, services, frames, native
  callbacks, and lazy owner cells.
- Build a same-JVM sequence report format with per-run owner snapshots and
  structured failures.
- Decide where golden diagnostics live and how they are updated.

## PR Slices

These slices are intentionally small enough for upstream review.

### PR 1: Logging Facade Introduction

Goal: add the logging API dependency shape and a tiny internal helper package,
without changing behavior.

Scope:

- add explicit `slf4j-api` dependency if the build currently relies on
  transitive API,
- keep no-op provider only on standalone `javatools` launch/test classpaths,
- add categories documented in `logging-strategy.md`,
- migrate one low-risk stderr trace site, such as the TLS route trace, behind a
  guarded logger,
- add a source-shape test preventing new `System.err.println` in production
  runtime/compiler code outside approved boundaries.

### PR 2: ErrorListener Structured Context

Goal: introduce structured diagnostics without rewriting the compiler.

Scope:

- add `DiagnosticSink` and `DiagnosticContext`,
- add `ErrorListenerBridge`,
- make `ErrorList` optionally expose diagnostic events while preserving current
  API,
- add tests that assert source code, severity, source span, module, and phase
  without parsing display strings.

### PR 3: Exception Cause Propagation

Goal: fix boundaries that can hide failure.

Scope:

- keep branch fixes for `JitConnector` unhandled exception status propagation,
  `nType` reflective exception unwrapping, and `ServiceContext` op-loop
  runtime-defect propagation,
- keep the branch fix for compiler codegen `Throwable` catch,
- fix `MethodStructure` assembly print-and-continue,
- preserve Java cause plus owner/module/frame context,
- add tests proving failures do not report success.

### PR 4: TestSimple Migration Policy

Goal: stop losing reproducers.

Scope:

- document `manualTests/src/main/x/TestSimple.x` as scratch-only in code
  comments or build docs if docs are being updated in that PR,
- add a `repro` fixture directory for stable compiler/runtime cases,
- migrate the current network/DNS `TestSimple` scenario to a named manual
  module if it is still needed,
- require every bug-hunting `TestSimple` reproducer to become a stable test
  before the fix PR lands.

### PR 5: Golden Diagnostic Tests

Goal: make compiler/type-system diagnostics reviewable.

Scope:

- add golden tests for parser, type conversion, method lookup, module loading,
  and verification suppression behavior,
- assert structured diagnostic fields first and rendered text second,
- include LSP range/document-version assertions where applicable.

### PR 6: Stress And Ownership Snapshots

Goal: make parallel and same-JVM stress actionable.

Scope:

- keep `runParallelStress` as the aggressive race finder,
- keep `runDirectSequenceStress` as the same-JVM reuse finder,
- emit per-run structured reports under `manualTests/build/reports`,
- include ownership diagnostics snapshots for each run,
- fail the task on hidden runner exceptions instead of relying on stderr,
- add a focused Java regression for any stress-discovered issue.

## Review Standard

A future diagnostic or logging change should be rejected if it does any of the
following:

- prints a runtime/compiler failure directly to stdout or stderr from production
  code outside a console boundary,
- catches `Throwable` without rethrowing `Error` or recording a terminal
  runtime failure,
- wraps an exception using only `getMessage()`,
- returns success after printing a failure,
- converts an owner/runtime defect into an ordinary user-catchable exception
  without structured diagnostics,
- uses `ErrorListener.BLACKHOLE` without a clear speculative/recovery reason,
- makes a compiler/runtime decision observable only by reading a free-form log
  string,
- leaves a bug reproducer only in `TestSimple.x`,
- adds a stress-only test without a focused deterministic regression when one is
  possible.

The right bar is not "more logging". The right bar is that a failed compile,
run, owner check, native callback, or service operation leaves structured
evidence that a test can assert and a reviewer can trust.
