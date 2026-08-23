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
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554` catches
  `Throwable`, prints two strings, and raises a generic XTC exception. A Java
  assertion proving wrong ownership can become a normal language exception that
  user code may catch.
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:142` handles an
  unhandled XTC exception by printing to stdout and leaving the default result
  path available. That can make a failed run look successful.

Incremental compilation and LSP have a different version of the same problem.
They need diagnostics that are stable values: URI, range, code, severity,
message, document version, module, phase, and related context. A printed string
from a compiler branch or a swallowed `BLACKHOLE` error cannot be turned into a
reliable editor diagnostic.

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

The current production source still has direct output and one-off debug prints.
Representative examples:

| Site | Current behavior | Why it is bad design |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/tool/Compiler.java:471` | Catches `Throwable`, prints "Failed to generate code..." to stderr, prints the stack, then logs a message with `{}` parameters. | Two reporting paths compete, one is unstructured, and the catch continues the compiler after a possible VM/compiler defect. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554` | Catches `Throwable`, prints Java stack, prints XTC frame stack, then raises `"Run-time error: " + e`. | The owner/fiber/container evidence is lost as machine data, and Java defects can become user-catchable language exceptions. |
| `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:104` | Prints `No conversion found for ...` while preserving legacy constant-folding behavior. | A compiler decision about constant conversion is neither an `ErrorListener` diagnostic nor a trace event; tests cannot assert the conversion decision. |
| `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:814` | Same `No conversion found` stderr path during fit/validation. | This should be correlated with required type, actual type, expression, source span, and conversion method search. |
| `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:322` and `:334` | Prints "Foreign method chain" and "Foreign nested method", with TODO remove. | This is owner/pool evidence. Printing and moving on makes the important fact easy to miss. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5971` | Prints a process-wide de-duplicated recursion message. | The process-wide suppression hides repeated failures across containers and does not include pool or caller context. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:721` | `sendError` calls `t.printStackTrace()` and then may print a second stack in the `IOException` catch. | Web/native callback boundaries need server/container/route/request context and structured failure state, not default stderr. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:780`, `:787`, `:880` | TLS/route traces print timestamped strings to stderr. | Trace output is not level-controlled, not routed by host, and not correlated with server/container/route ownership. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java:151` and `:501` | Temporarily prints stack traces before raising obscure XTC exceptions. | The TODO admits this is temporary; it still loses typed cause and owner context. |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java:206` and `DirRepository.java:371` | Prints load failure to stdout and returns null. | Repository load failure becomes "module not found" unless the caller already knows this exact stdout text matters. |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2077` | Catches `UnsupportedOperationException` while assembling ops, prints, and continues. | Assembly can proceed after a code-generation failure; this can hide a corrupt or incomplete method body. |
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
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:142` | Unhandled XTC exceptions are printed at `:150`; the catch does not throw on that path. | A failed program can report success after a printed exception. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:554` | Every `Throwable` from op execution is printed and translated to a generic XTC runtime error. | Java `Error`, ownership assertion failures, and runtime defects can become normal language exceptions. |
| `javatools/src/main/java/org/xvm/tool/Compiler.java:471` | Code generation catches `Throwable`, prints, logs, and keeps looping. | A compiler defect can be downgraded to console noise and later phases can run on corrupted state. |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2077` | Method op assembly failure is printed and the method still writes its op byte count. | This can produce or persist a broken module while the build path only saw stderr. |
| `javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:497` | A "must not happen" async failure only asserts false inside a callback. | With assertions disabled, the code can continue with null values or incomplete failure propagation. |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java:206` and `DirRepository.java:371` on requested-module paths | Load failure is printed and converted to null. | A concrete repository failure can become indistinguishable from missing module. Best-effort scanning may return null; requested loads need cause. |

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
| Runtime op boundary | `ServiceContext.java:554` | Do not catch all `Throwable` and translate runtime defects into generic catchable XTC exceptions. Preserve Java cause and owner/frame context. |
| JIT host boundary | `JitConnector.java:142` | Unhandled XTC exceptions must produce a non-zero result or typed thrown failure. Printing is secondary. |
| Compiler codegen boundary | `Compiler.java:471` | Do not continue after `Throwable`. Rethrow fatal errors, preserve cause, and attach compiler/module/phase context. |
| Method assembly | `MethodStructure.java:2077` | Do not print and assemble through unsupported op generation. Fail the module assembly with method context. |
| Async future callback | `xFuture.java:497` | Do not rely on `assert false` for impossible async failure. Complete exceptionally or route through runtime failure diagnostics. |
| Requested module load | `FileRepository.java:206`, `DirRepository.java:371` | Preserve cause when the host requested that module. Best-effort scans may continue, but requested loads need typed failure. |
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

- fix `JitConnector` unhandled exception status propagation,
- fix `ServiceContext` `Throwable` translation policy,
- fix compiler codegen `Throwable` catch,
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
