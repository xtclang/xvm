# Exception Hygiene Audit

Audit date: 2026-08-22

Scope: production Java, Kotlin, and Ecstasy source in the XVM workspace. I excluded
generated build output, Gradle caches, docs examples, manual tests, TCK examples, and
`src/test` from the main counts. Test/example catches are useful language coverage,
but they are not runtime/compiler exception ownership boundaries.

## Search Commands

The audit started with broad text searches, then inspected the surrounding code for
each production site that could change exception ownership or propagation.

```bash
rg -n --glob '!**/build/**' --glob '!**/.gradle/**' \
  'catch\s*\(\s*(?:final\s+)?(?:Exception|Throwable|RuntimeException|Error)\b|catch\s*\(\s*(?:final\s+)?[^)]*\|[^)]*\)' .
```

```bash
rg -n --glob '!**/build/**' --glob '!**/.gradle/**' \
  'catch\s*\([^)]*(?:ignore|ignored|_)\)|catch\s*\([^)]*\)\s*\{\s*\}' .
```

```bash
rg -n --glob '!**/build/**' --glob '!**/.gradle/**' \
  'new\s+RuntimeException\b|throw\s+new\s+(?:RuntimeException|IllegalStateException|AssertionError)\b|throws\s+(?:Exception|Throwable|RuntimeException)\b' .
```

```bash
rg -n --glob '!**/build/**' --glob '!**/.gradle/**' \
  'catch\s*\([^)]*:\s*(?:Exception|Throwable)' build-logic plugin -g '*.kt' -g '*.kts'
```

Production broad-catch counts from the first command, excluding docs/manual tests/TCK
and Java tests:

| Area | Broad catch sites |
| --- | ---: |
| `javatools` | 118 |
| `lib_jsondb` | 41 |
| `lib_ecstasy` | 16 |
| `plugin` | 12 |
| `javatools_bridge` | 10 |
| `lib_xenia` | 10 |
| `javatools_jitbridge` | 7 |
| `lib_web` | 7 |
| `lib_xunit_engine` | 6 |
| `lib_json` | 5 |
| `lib_oodb` | 3 |
| `lib_xml` | 2 |
| `lib_xunit_db` | 2 |
| `javatools_utils`, `lib_cli`, `lib_collections`, `lib_xunit` | 1 each |

Kotlin build logic has two `catch (e: Exception)` sites:
`build-logic/aggregator/src/main/kotlin/org.xtclang.build.aggregator.gradle.kts:94`
and `build-logic/common-plugins/src/main/kotlin/DockerTasks.kt:221`.

## Categories

| Classification | Meaning | Expected handling |
| --- | --- | --- |
| Acceptable | The catch is the declared boundary: parser lookahead, constant-folding fallback, diagnostics-safe `toString`, transaction result collection, launcher final boundary, or future completion. | Keep, but prefer a comment when the catch is intentionally broad or empty. |
| Suspicious | The catch is probably intentional, but it loses cause, narrows an operational failure to `False`, logs only to stderr/console, or relies on "must not happen". | Tighten type, preserve cause, add health signal, or document why failure is non-observable. |
| Must-fix | The catch can hide startup failure, report success after failure, strand native callback or pending work counts, convert VM defects into user-catchable errors, or lose the only ownership/reentrancy clue. | Change propagation semantics, not just logging. Add tests or stress coverage when touching runtime paths. |

## Intended Propagation Model

Launcher/tool path:

`Runner.process()` should let runtime setup, module load, and invocation defects keep
their cause until they reach `Launcher.log(FATAL, cause, ...)` or the final
`Launcher.launch(LauncherOptions, ...)` catch. `LauncherException` is the existing
non-fatal control-flow exception. Unexpected Java defects should not be converted to
cause-less `RuntimeException`.

Interpreter runtime path:

Language-level exceptions should travel as `ExceptionHandle` via
`frame.raiseException(...)`, future exceptional completion, or service response
delivery. Native asynchronous Java failures should be translated at the waiting frame,
using the correct owner container. Java defects that indicate broken runtime state
should not silently become normal XTC exceptions that user code can catch and continue
from unless the runtime has a deliberate containment policy and records the failure.

Service and scheduler path:

`Container.schedule()` increments pending work and submits a runnable. If the runnable
catches a defect and only prints to stderr, `InterpreterConnector.join()` can observe
the container as idle and return the default result. Any unexpected scheduler/runtime
failure needs a container-level terminal failure channel, otherwise parallelism bugs
become "successful" exits.

Compiler path:

Expected source errors belong in `ErrorListener`. Speculative parser failures and
constant-folding misses can be swallowed. Compiler bugs should propagate with module,
source, phase, and op context. Catching `Throwable` in compiler phases must not absorb
`Error` subclasses such as `OutOfMemoryError`, linkage failures, or VM-level defects.

Database path:

Transaction callbacks and processors intentionally model failures as
`CommitResult | Exception`. That is acceptable if the failure is retained as a domain
result and recovery/panic state is visible. It becomes suspicious when rollback,
repair, or startup corruption is logged and then hidden from the caller.

Gradle plugin path:

Build failures should become `GradleException` with cause. Returning an exit code from
a direct launcher strategy is acceptable only after the underlying failure has already
been logged and the task code treats the non-zero code as failure.

## Must-Fix Sites

| Priority | Site | Classification | Risk | Recommended fix |
| --- | --- | --- | --- | --- |
| P0 | `javatools/src/main/java/org/xvm/javajit/JitConnector.java:143` | Must-fix | `InvocationTargetException` for an unhandled XTC exception is printed at `JitConnector.java:150`, but no exception is thrown and `result` remains the default zero. JIT/direct execution can report success after an unhandled language exception. | Preserve language exception semantics: set a non-zero result or throw a typed `JitUnhandledException`/`LauncherException` with the cause. Keep the printable XTC exception text, but do not let `join()` return success. |
| P0 | `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java:130`, `:187`, `:231` | Must-fix | Reflective `equals`, `compare`, and `hashCode` catch `InvocationTargetException` and convert every failure to `$unsupported`. If the invoked method threw an XTC `nException`, the language exception is lost. | Catch `InvocationTargetException`, unwrap `getCause()`, rethrow `nException`, and only translate reflection/signature failures to `$unsupported` or type mismatch. |
| P0 | `javatools/src/main/java/org/xvm/runtime/MainContainer.java:253` | Must-fix, fixed in branch | Startup/invocation setup failures are wrapped as `new RuntimeException("failed to run... Cause: " + e.getMessage())` with no cause. This cuts off module load, injection, owner, and stack information before the launcher sees it. | Fixed by throwing `new RuntimeException("failed to run: " + f_idModule, e)`. The launcher still gets module context, and diagnostics keep the original cause and stack. A typed startup exception remains a later cleanup option. |
| P0 | `javatools/src/main/java/org/xvm/runtime/Container.java:168` and `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:324` | Must-fix | Unexpected service scheduling/execution failure is printed and swallowed. Pending work is decremented, the service may be terminated, and `join()` can return normal completion. This hides parallelism, ownership, and runtime defects. | Add a container/runtime terminal failure field observed by `InterpreterConnector.join()`. Store the first unexpected `Throwable`, return non-zero or rethrow through the launcher boundary, and keep stderr as secondary diagnostics. |
| P0 | `javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xNanosTimer.java:310` | Must-fix, fixed in branch | `Alarm.start()` registers `nativeCallback` before scheduling the timer, catches `Throwable`, and only calls `cancelTrigger()`. If scheduling fails after registration, the callback count can remain positive and keep the container non-idle forever. | Fixed with an explicit rollback guard: the alarm stores the exact registered container, unregisters it on schedule failure, no longer swallows `Throwable`, and removes the alarm from the set if startup fails. |
| P0 | `javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java:121` | Must-fix, fixed in branch | `Alarm` registers keep-alive in the constructor before `scheduleTimer()`. The catch calls `alarm.cancel()`, but an unscheduled trigger may not unregister. Startup/schedule failure can leak callback ownership. | Fixed by moving registration out of construction and adding `cancelAfterScheduleFailure()`, which unregisters from the stored owner independently of `TimerTask.cancel()` result. |
| P1 | `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:280` and `:287` | Must-fix, fixed in branch | Server startup registers a native callback, then creates contexts. If a post-registration startup step throws, the catch terminates the service context but does not unregister the callback. `join()` can hang on callback count. | Fixed by tracking registration and running `rollbackBind(...)` before raising the XTC I/O exception. Rollback unregisters the callback, stops partial Java servers, shuts down the executor, clears routes, and clears the native handle. |
| P1 | `javatools/src/main/java/org/xvm/tool/Compiler.java:471` | Must-fix | Code generation catches `Throwable`, logs, and continues. This can absorb `Error` subclasses and keep compiling after corrupted compiler state. | Catch `RuntimeException` or a compiler-specific exception for recoverable compiler bugs. Rethrow `Error`. Include compiler/module/phase context in the propagated exception. |
| P1 | `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:556` | Must-fix | Each op execution catches `Throwable`, prints stack, and raises a generic XTC `"Run-time error"` exception. Java VM defects and ownership assertions can become user-catchable language exceptions. | Rethrow `Error`. For runtime bugs, use a runtime-failure channel or a non-user-catchable fatal path. If translating to XTC, include a diagnostic tag/cause and mark it as native runtime failure. |
| P1 | `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:229` | Must-fix | `scheduleIO(task); // don't wait` discards the returned `CompletableFuture`; any write failure completes an unobserved future while the method returns OK. | Return or store a future, or make the API explicitly fire-and-forget with an error sink. For channel writes, propagate completion/failure to the caller. |
| P1 | `javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:491` and `:497` | Must-fix | `CompletableFuture.allOf(...).whenComplete` says `cfThis.get()`/`cfThat.get()` "must not happen" and only asserts on `Throwable`. If interruption or a race occurs, `hAnd` may be completed with nulls or never completed correctly. | Complete `hAnd` exceptionally through `Utils.translate(...)`, and restore interrupt status for `InterruptedException`. |
| P1 | `lib_jsondb/src/main/x/jsondb/Client.x:1425` and `:1430` | Must-fix | Commit failure is logged and downgraded to `DatabaseError`; rollback failure is ignored. A failed rollback after a failed commit is exactly the state that should surface in DB health/recovery. | Log and retain rollback failure as suppressed/secondary failure. Consider returning a richer commit result that carries both commit and rollback exceptions. |

## Suspicious Sites

### Runtime Ownership And Native Translation

| Site | Classification | Why it is suspicious | Recommended fix |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:480` | Suspicious | Proxy creation catches `Throwable` and raises `"Failed to create a proxy..."` without the cause. Proxy failures are often owner/type-system failures, so the dropped Java cause is important. | Catch narrower runtime exceptions, rethrow `Error`, and include a diagnostic cause/tag in the XTC exception. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1418` | Suspicious | `adoptParameters` failure becomes `"No common TypeSystem..."` and assumes one type argument. The original cause can identify the foreign pool/owner mismatch. | Preserve cause details and handle multiple parameters. This is owner-sensitive code. |
| `javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:243` | Suspicious | `ensureClass(...)/cloneAs(...)` failure becomes `"Failed to create a class handle"` without cause. | Keep cause text or a cause handle. Rethrow non-translatable runtime defects. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:216` | Suspicious | Bind failures are converted to `frame.raiseException(e.getMessage())`. Cause, exception type, and owner context are lost. | Use a typed XTC exception and include the cause/RT diagnostic. |
| `javatools/src/main/java/org/xvm/runtime/Utils.java:829` | Suspicious | `Utils.translate()` unwraps futures, but default Java throwables become `"Unexpected native exception: <message>"` with no stack/cause. | Add a native-exception translation helper that preserves Java class name and RT diagnostic text. Keep owner container allocation. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java:155`, `:180` | Suspicious | DNS resolve/lookup failures are swallowed and reported as conditional `False`; comments already ask whether the reason should be reported. Infrastructure failures and invalid input look identical. | Distinguish no-answer from Java execution failure. Return `False` for name-not-found; raise or log for unexpected native failure. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java:175`, `:300`, `:344` | Suspicious | Async socket failures are translated, but often only via `unwrap(e).getMessage()`. | Preserve exception class and interrupted status. Return `False` only for connection refusal/IO conditions specified by the API. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTConnector.java:210`, `:217`, `:265` | Suspicious | HTTP client send/response mapping catches broad exceptions and raises I/O exceptions by message only. | Translate `InterruptedException`, `IOException`, `IllegalArgumentException`, and response-shape bugs separately. Preserve cause in obscure RT error where available. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java:151`, `:501`, `:555` | Suspicious | IO-task failures are printed temporarily or collapsed to obscure I/O. One catch has a TODO to tighten. | Tighten to documented `GeneralSecurityException`, `IOException`, and `AcmeException`; restore interrupts; remove temporary stack printing after structured propagation exists. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTKeyStore.java:188` | Suspicious | KeyStore construction catches `Exception` and returns an obscure XTC exception. | Narrow to security/I/O/argument exceptions and preserve the underlying type in RT diagnostics. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java:123` | Suspicious beyond leak risk | The catch raises only `e.getMessage()`, so timer state/setup failures lose type and stack. | Use `xException.illegalState` or a native timer exception with cause text. |

### Launchers, Compiler, And JIT

| Site | Classification | Why it is suspicious | Recommended fix |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/tool/Launcher.java:320` | Acceptable boundary, suspicious breadth | Top-level programmatic launch catches `Throwable` and returns exit code 1. This is a valid final boundary, but only if inner code preserves cause until here. | Keep as final boundary. Consider rethrowing `ThreadDeath`/fatal VM errors if this API is used inside daemons. |
| `javatools/src/main/java/org/xvm/tool/Runner.java:254` | Acceptable boundary | Runner catches `Exception`, logs FATAL, and `LauncherException` carries the cause. | Keep. Do not add inner cause-less wrappers before this point. |
| `javatools/src/main/java/org/xvm/tool/Runner.java:118` | Suspicious | Module identification catches `RuntimeException` and returns accumulated errors. This is user-facing validation, but it can hide programmer defects thrown by `ModuleInfo`. | Prefer typed `ModuleInfoException`/`InvalidModuleSpecException` for expected validation failures. |
| `javatools/src/main/java/org/xvm/compiler/EvalCompiler.java:138` | Suspicious | Eval compilation catches any `Exception`, logs fatal if no serious errors, and returns null. | Include exception text/cause in the error list; narrow if possible. |
| `javatools/src/main/java/org/xvm/javajit/BuildContext.java:561` | Acceptable with note | JIT code generation catches `Throwable` and wraps with op/source context. This is useful, but should not catch fatal `Error` unless the JIT intentionally wraps all classfile API defects. | Rethrow fatal `Error` or document that this is a compiler boundary. |
| `javatools/src/main/java/org/xvm/javajit/TypeSystem.java:347` | Acceptable | Classfile build `RuntimeException` is wrapped with the type name. | Keep; this is good compiler context preservation. |
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:152` | Suspicious | Printing the XTC exception object is in a broad ignored catch. If reflection of the exception object fails, the fallback is silent. | At least print the original `cause` or include fallback text before continuing to the non-zero propagation fix. |

### ASM, Type System, And Compiler Recovery

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1814` | Acceptable | Catch-and-rethrow cleans deferred type-info state, then rethrows. This is good hygiene. | |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6058`, `:6532`, `:6578` | Acceptable | Cache entries are rolled back on `RuntimeException` or `Error`, then rethrown. This prevents poisoned recursive caches. | |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:317` | Acceptable | Diagnostic `constant.toString()` fallback avoids masking the real late-registration failure. | |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4039` | Suspicious | `Exception` is converted to `RuntimeException`. If this initialization can fail from I/O/linker causes, callers cannot distinguish setup failure type. | Use a typed initialization exception or include phase/module context. |
| `javatools/src/main/java/org/xvm/asm/ModuleStructure.java:203` | Suspicious | Digest assembly failure becomes raw `RuntimeException`. | Use `IllegalStateException("failed to assemble module digest", e)` or a typed repository exception. |
| `javatools/src/main/java/org/xvm/asm/FileRepository.java:205` and `DirRepository.java:370` | Suspicious | Repository load errors are printed and cached as missing/invalid module. This is okay for optional repository scanning but can hide startup failure if the requested module is in that file. | Keep best-effort scanning behavior, but make requested-module load paths surface the cause. |
| `javatools/src/main/java/org/xvm/asm/DirRepository.java:235`, `:282`, `:306` | Acceptable | Persistent cache read/write/get-cache-file failures are performance-only and safely fall back. | |
| `javatools/src/main/java/org/xvm/asm/Argument.java:56` and `OpVar.java:121` | Acceptable | Debug string fallback must not throw while rendering ops. | |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:2133` | Acceptable | Runtime exception is wrapped with the type name, preserving cause. | |
| `javatools/src/main/java/org/xvm/asm/constants/AnnotatedTypeConstant.java:427`, `RangeConstant.java:197`, `TypeConstant.java:2133` | Acceptable/suspicious by context | These wrap invalid constant/type state. They are fine for internal invariants if cause is retained. | |

### Parser And Constant Folding

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/compiler/Parser.java:169`, `:1705`, `:3077`, `:3426` | Acceptable | Speculative parse/lookahead failure is intentionally discarded and parser state is restored. | |
| `javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java:289`, `:299` | Acceptable | Runtime type/class parsing probes multiple syntactic interpretations. Null result represents "not parseable". | |
| `javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java:578`, `CmpExpression.java:280`, `UnaryMinusExpression.java:73`, `UnaryComplementExpression.java:71` | Acceptable | Constant folding failure falls back to runtime evaluation; arithmetic out-of-range is separately reported where needed. | |
| `javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java:927`, `:945`, `:1011`, `:1122` | Acceptable | Tuple/index inference treats invalid constant conversion as "cannot infer". | |
| `javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:1212`, `:1227` | Acceptable | `toString()` rendering fallback avoids debug/display failures. | |
| `javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:975` | Suspicious | Broad catch in compiler case logic needs source context if it represents compiler recovery rather than display. | Audit separately if this area changes; use typed compiler diagnostics. |

### Database And Transactional Code

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `lib_oodb/src/main/x/oodb/Transaction.x:202`, `:210` | Acceptable | Commit and rollback exceptions are retained and rethrown from `close()`. This is the right ownership boundary. | |
| `lib_jsondb/src/main/x/jsondb/TxManager.x:996`, `:1004`, `:1053`, `:1082` | Acceptable with monitoring | Failures are logged and converted to rollback/panic paths. | Ensure panic is observable by callers and service health. |
| `lib_jsondb/src/main/x/jsondb/TxManager.x:2204` | Acceptable | Rollback store failures are logged and return `False`. | |
| `lib_jsondb/src/main/x/jsondb/TxManager.x:2224`, `:2230` | Acceptable | Termination callbacks should not block transaction completion; logging is enough. | |
| `lib_jsondb/src/main/x/jsondb/TxManager.x:2425`, `:2554`, `:2786`, `:2804`, `:2828` | Acceptable/suspicious | Recovery code logs and returns failure. | This is acceptable if the database startup path refuses to continue when recovery returns `False`. |
| `lib_jsondb/src/main/x/jsondb/TxManager.x:3084` | Suspicious | Background maintenance logs and reschedules. Repeated failure can be invisible except logs. | Add backoff/error counter or health signal. |
| `lib_jsondb/src/main/x/jsondb/Client.x:454`, `:471`, `:1467` | Acceptable/suspicious | Message retry/abandon notifications should not crash processing, but `DBClosed` empty catches are too quiet for shutdown races. | Leave `DBClosed` as benign only with a comment; log unexpected rollback/abandon failures. |
| `lib_jsondb/src/main/x/jsondb/Client.x:218` | Acceptable | `safeToString()` must not throw while logging. | |
| `lib_jsondb/src/main/x/jsondb/Scheduler.x:770`, `:806`, `:871` | Acceptable | Processor exceptions are returned as domain results for retry/abandon. | |
| `lib_jsondb/src/main/x/jsondb/storage/JsonValueStore.x:559`, `:586`; `JsonNtxCounterStore.x:79`; `KeyBasedStore.x:119` | Acceptable/suspicious | Deep scan/recovery catches are acceptable when they log and return failure. | Do not continue startup after unrepaired corruption. |
| `lib_jsondb/src/main/x/jsondb/storage/LogStorageSupport.x:49` | Acceptable | Invalid rotated-log timestamp means "not a log file". | |
| `lib_jsondb/src/main/x/jsondb.x:274` | Acceptable | Backup rename/copy retry retains the last exception and throws it after retries. | |

### Web, Xenia, And Request Handling

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `lib_web/src/main/x/web/WebService.x:114`, `:116` | Acceptable | Request abort is converted to response; other exceptions go to optional error handler or rethrow. | |
| `lib_web/src/main/x/web/HttpClient.x:92` | Acceptable | Async response processing completes the future exceptionally. | |
| `lib_web/src/main/x/web/HttpClient.x:341` | Acceptable | `ResponseIn.to(Type)` returns conditional `False` when codec decode fails. | |
| `lib_web/src/main/x/web/security/BasicAuthenticator.x:91`, `TokenAuthenticator.x:93`, `DigestAuthenticator.x:521` | Acceptable | Bad credentials/encoding are authentication failures, not service failures. | |
| `lib_web/src/main/x/web/AcceptList.x:320` | Acceptable | Invalid `q` value makes the header unparseable. | |
| `javatools_bridge/src/main/x/_native/web/RequestInfoImpl.x:433` | Suspicious | `respond()` swallows all response send failures. That can hide broken response ownership or closed-context errors. | Treat broken client connection as benign only for known I/O/closed cases; log unexpected failures. |
| `javatools_bridge/src/main/x/_native/web/RequestInfoImpl.x:463` | Suspicious | Observer callbacks are ignored. This prevents observer bugs from affecting response, but makes instrumentation failures invisible. | Log observer failures at debug/warn or expose an observer error hook. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:600`, `:665` | Acceptable/suspicious | Request handling sends HTTP 500 on handler failures. This is correct boundary behavior, but stack traces go to default stderr. | Route to the natural unhandled-exception handler noted in the TODO. |
| `lib_xenia/src/main/x/xenia/Dispatcher.x:62` | Acceptable | Bad URI becomes HTTP 400. | |
| `lib_xenia/src/main/x/xenia/Dispatcher.x:231` | Acceptable | Client disconnect while sending response is logged and cannot be repaired. | |
| `lib_xenia/src/main/x/xenia.x:130` | Suspicious | Existing generated keystore load failure is ignored and a new keystore is created. This may hide corrupt keystore/startup ownership issues. | Log at least once with path and reason before regenerating. |
| `lib_xenia/src/main/x/xenia.x:156` | Acceptable | Startup failure closes the server and rethrows. | |
| `lib_xenia/src/main/x/xenia/SessionManager.x:238`, `:310`; `SessionCookie.x:433`; `SessionImpl.x:572` | Acceptable | Bad/corrupt cookies are expected user input and map to no session/corrupt match. | |
| `lib_xenia/src/main/x/xenia/SessionImpl.x:881` | Suspicious | Session event handler exceptions are logged and swallowed. This may be intentional event isolation, but application owners may miss broken lifecycle handlers. | Surface through an application error hook or session health counter. |

### XUnit And CLI

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `lib_xunit/src/main/x/xunit/assertions.x:64` | Acceptable | `assertThrows` intentionally captures expected exceptions. | |
| `lib_xunit_engine/src/main/x/xunit_engine/executor/ExceptionCollector.x:85`, `:103` | Acceptable | Test execution collects thrown exceptions as test result state. | |
| `lib_xunit_engine/src/main/x/xunit_engine/executor/TestExecutor.x:166`, `:179` | Suspicious | Lifecycle hook exceptions are printed, but do not fail the test run. Extensions can break silently. | Add listener/error result integration or mark the test/container failed when lifecycle callbacks fail. |
| `lib_xunit_engine/src/main/x/xunit_engine/discovery/resolvers/ClassResolver.x:38` | Suspicious | Discovery child-selector failure warns and returns no children. This can under-report tests. | Consider failed discovery result instead of empty children for non-enum failures. |
| `lib_cli/src/main/x/cli/Runner.x:335` | Acceptable | Interactive command runner catches command exceptions and prints them. | |

### Gradle Plugin And Build Logic

| Site | Classification | Rationale | Recommended fix |
| --- | --- | --- | --- |
| `plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeBuildService.java:66`, `:69`, `:110` | Acceptable | Reflection and classloader failures become `GradleException` with cause. | |
| `plugin/src/main/java/org/xtclang/plugin/launchers/DirectStrategy.java:45`, `:56`, `:67` | Acceptable/suspicious | Direct execution logs and returns an exit code. This is acceptable if task callers always fail on non-zero. | Keep cause logging; ensure all callers convert non-zero to task failure. |
| `plugin/src/main/java/org/xtclang/plugin/tasks/XtcTestTask.java:105` | Acceptable | Test task optionally suppresses failure when `failOnTestFailure=false`, with warning. | |
| `plugin/src/main/java/org/xtclang/plugin/internal/DefaultXtcLauncherTaskExtension.java:144` | Suspicious | Missing/corrupt plugin build-info resource silently falls back to default JVM args. This can hide broken plugin packaging. | Log a warning with cause or fail when the resource is missing in a released plugin. |
| `plugin/src/main/java/org/xtclang/plugin/XtcPluginUtils.java:282` | Suspicious/low | Jar metadata hash returns literal `"ERROR"` on failure. It is logging-only today, but indistinguishable from a real formatted value. | Return `sha256=<unavailable: reason>` or throw when used for identity. |
| `plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeFingerprint.java:62` | Acceptable | Runtime fingerprinting fails fast with cause. This is the right behavior for direct-runtime cache keys. | |
| `build-logic/aggregator/src/main/kotlin/org.xtclang.build.aggregator.gradle.kts:94` | Acceptable | Missing lifecycle task in included build is intentionally skipped. | |
| `build-logic/common-plugins/src/main/kotlin/DockerTasks.kt:221` | Acceptable | Forced cleanup downgrades GitHub API failure to warning; non-forced mode throws `GradleException`. | |

## Inappropriate `RuntimeException` Use

The codebase uses many `IllegalStateException` sites for internal invariants. Most are
reasonable compiler/runtime assertions. The problematic cases are generic
`RuntimeException` wrappers at ownership boundaries:

| Site | Classification | Problem | Recommended fix |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/runtime/MainContainer.java:254` | Must-fix, fixed in branch | Generic runtime wrapper without cause. | Cause is now preserved; a typed startup exception is optional later cleanup. |
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:158` | Suspicious | `new RuntimeException(cause)` loses command/module context. | Wrap with `"Failed to invoke JIT main"` and preserve cause, except language exceptions should become non-zero launcher failure. |
| `javatools/src/main/java/org/xvm/compiler/Source.java:92` | Suspicious | `Source(InputStream)` converts `IOException` to `RuntimeException`. | Prefer `throws IOException`, or a typed unchecked `SourceReadException` if API compatibility prevents checked throws. |
| `javatools/src/main/java/org/xvm/asm/ModuleStructure.java:204` | Suspicious | Digest assembly failure is raw `RuntimeException`. | Use `IllegalStateException` with context and cause. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4040` | Suspicious | Initialization wraps checked failures as generic runtime. | Use typed `ConstantPoolInitializationException` or include phase context. |
| `javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java:41` | Suspicious | Unknown resource becomes generic `RuntimeException`. | Use the bridge's language exception type or typed injection failure so launchers can report missing resource cleanly. |

## Typed Exceptions Worth Adding

These are not required everywhere, but they would make intended propagation clearer:

| Proposed type | Where it helps | Why |
| --- | --- | --- |
| `XvmStartupException` or use existing `LauncherException` | `MainContainer`, `InterpreterConnector`, native template startup | Startup/load/injection failures should percolate to launchers with cause and module/resource context. |
| `UnexpectedServiceFailure` stored on `Container`/`Runtime` | `Container.schedule`, `ServiceContext.drainWork`, op loop defects | `join()` needs to see scheduler/runtime failure rather than infer success from idle queues. |
| `NativeCallbackRegistration` guard | `xLocalClock`, `xNanosTimer`, `xRTServer` | Register/unregister symmetry is currently hand-coded and easy to leak under exceptions. |
| `NativeIoFailure` translation helper | `xOSFile`, `xRTSocket`, `xRTConnector`, `xRTNameService`, crypto templates | Preserve Java exception class, message, interrupt status, and owner container while producing XTC exceptions. |
| `CompilerPhaseException` | `Compiler.generateCode`, JIT `BuildContext`, `TypeSystem` | Compiler bugs need module/phase/source/op context and should not be mixed with ordinary source diagnostics. |
| `RepositoryLoadException` | `FileRepository`, `DirRepository`, `ModuleInfo` | Distinguish optional repository scan failure from failure to load the requested module. |

## Priority Backlog

P0:

1. Fix JIT unhandled XTC exception propagation in `JitConnector.invoke0Impl()`.
2. Unwrap and rethrow `nException` in JIT bridge reflective dispatch (`nType`).
3. DONE in this branch: preserve cause in `MainContainer.invoke0()`
   startup/invocation failure.
4. Add a runtime/container terminal failure channel for unexpected service scheduler
   and drain failures.
5. DONE in this branch: make native callback registration exception-safe for
   timers and server startup.

P1:

1. Stop catching `Throwable` in compiler code generation and op execution unless
   fatal `Error` is rethrown.
2. Fix unobserved async write failure in `xRawOSFileChannel.invokeSubmit()`.
3. Complete futures exceptionally instead of asserting in `xFuture.and` "must not
   happen" paths.
4. Preserve native Java cause/type when translating proxy, reflection, socket, HTTP,
   file, and crypto failures into XTC exceptions.
5. Log/retain rollback failure after commit failure in `lib_jsondb` client paths.
6. Convert XUnit discovery/lifecycle hook failures into visible test/discovery
   failures or explicit extension warnings.

P2:

1. Add comments to intentionally empty parser, constant-folding, and metadata fallback
   catches.
2. Tighten plugin fallback catches around build-info loading and logging-only hashing.
3. Add health counters for repeated background DB maintenance/session event failures.
4. Audit generic `RuntimeException` wrappers as APIs evolve toward typed startup,
   repository, and compiler phase exceptions.

## Changed Files

Created:

```text
docs/reentrancy/exception-hygiene-audit.md
```

## Summary

The broad catches are not uniformly bad. Parser speculation, constant-folding fallback,
diagnostic rendering, future completion, and transaction result collection are mostly
valid. The highest-risk issues are where a catch changes the owner-visible result:
JIT unhandled exceptions can report success, service scheduler failures can be printed
and lost, native callback registration can leak keep-alive counts, startup failures can
lose their cause, and unobserved async I/O can fail after returning OK. Those should be
fixed before broad stylistic tightening.

The original audit did not modify source code. The native callback rollback
rows above are now fixed in this branch and documented below because they were
real keep-alive leaks.

## Fixed In This Branch: Native Callback Rollback

`Container.registerNativeCallback()` is a keep-alive counter. It is intentionally
owner-visible: while the count is non-zero, the container is not idle and
`join()` should not report natural completion. That makes registration a
transactional operation. Any code path that registers must either publish a
live native callback that will later unregister, or roll back the count before
returning failure.

The old timer/server implementations broke that rule:

- `xLocalClock.Alarm` registered from the constructor. If `Timer.schedule(...)`
  then failed, the catch called `alarm.cancel()`, but an unscheduled
  `TimerTask.cancel()` can report false and skip unregistering.
- `xNanosTimer.Alarm.start()` registered, caught `Throwable`, canceled the
  trigger, and returned as if scheduling had succeeded. If the weak callback was
  gone or the cancel path did not report success, the callback count stayed
  registered forever.
- `xRTServer.invokeBind(...)` registered the callback after starting the Java
  servers but before creating contexts. A later failure terminated the service
  context without unregistering the callback or closing partial Java server
  resources.

This is not primarily a parallel-only bug. A single-threaded failed schedule or
bind can leave the container permanently non-idle. Parallel and same-JVM stress
make it easier to notice because a stranded callback count looks like a hang or
stale runtime state from an unrelated container.

The branch fix keeps successful-path behavior:

- LocalClock and NanoTimer still use the same process-wide daemon `Timer`.
- Keep-alive callbacks still increment the owning container's callback count
  while the native alarm is pending.
- xRTServer still uses the same Java `HttpServer`/`HttpsServer` and executor
  startup order on success.

The difference is failure handling. Timer alarms now store the exact
`Container` that was registered, so cleanup does not depend on rediscovering
the owner through a weak callback. Server bind tracks whether callback
registration happened and rolls back all native resources before raising the
same natural I/O failure shape. `NativeCallbackRegistrationTest` source-shape
guards fail on the master code because the old constructor registration,
`catch (Throwable)` swallow, and missing server rollback are still present
there.

## Fixed In This Branch: MainContainer Failure Cause Preservation

`MainContainer.invoke0(...)` is the inner runtime entry point that finds the
module method, resolves the module singleton, installs runtime publication
diagnostics, and posts the entry invocation. If any of that setup fails, the
exception is exactly where module identity, owner/pool state, and stack context
matter most.

Master replaced that exception with a new message-only wrapper:

```java
throw new RuntimeException("failed to run: " + f_idModule
        + ". Cause: " + e.getMessage());
```

That was bad even without parallel execution. It kept a fragment of text and
discarded the Java exception type, stack trace, suppressed exceptions, and cause
chain. Under same-JVM and parallel-container testing it is worse because the
lost cause is often the only clue that a stale owner, late pool registration, or
runtime publication assertion fired before the launcher saw the failure.

The branch keeps the same runtime failure shape but preserves the cause:

```java
throw new RuntimeException("failed to run: " + f_idModule, e);
```

Successful execution is unchanged. Failed execution still reports the module
entry context, but now the top-level launcher and diagnostics can see the
original exception. `RuntimeFailurePropagationTest` records the old source shape
as a red-on-master guard.
