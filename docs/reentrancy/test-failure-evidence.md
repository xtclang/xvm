# Test Failure Evidence For Reentrancy Work

This file records the known test failures that are relevant to the runtime
ownership, reentrancy, and parallel-container hardening work. It exists so the
evidence is not only in terminal logs or temporary audit output.

The important distinction is whether a test fails on `master` because it
exposes the original broken behavior, or whether it cannot run on `master`
because the branch introduced a diagnostic API, source-shape guard, or helper
needed to express the invariant.

## Master Audit Snapshot

Audit run:

```text
master under test: 145f12f51074bae5e073db6181b0d015414dda65
branch under audit: cda5b8304bd152756387510fe9cde026ee7cbcf3
logs: /tmp/xtclang-master-audit-logs
report: /tmp/xtclang-master-audit-report.md
```

The audit copied committed branch tests into a clean `master` checkout one test
class or task at a time and ran the matching Gradle filter. Branch expectation
is that the same tests pass on `lagergren/lazy-instance`.

## Tests That Fail On Master For The Intended Bug

These are direct evidence for the old design being wrong. They should be cited
in review descriptions when the corresponding source slice is split into a
smaller PR.

| Test | Master failure signal | Broken design proven |
| --- | --- | --- |
| `org.xvm.asm.ConstantHashCodeCacheTest` | `Constant.m_iHash` is not `volatile`. | Cached hash publication was an unexplained plain-field race shape. |
| `org.xvm.asm.FileStructureTest` | Error listener lookup returns the wrong ambient pool listener. | Diagnostics depended on `ConstantPool.getCurrentPool()` instead of file ownership. |
| `org.xvm.asm.ModuleRepositoryLoadFailureTest` | Requested loads of a corrupt module file return null after a stdout print, in `FileRepository`, `DirRepository`, and through `LinkedRepository` searches. | A broken module file was indistinguishable from a missing module for every caller; the load failure cause existed only as console text. (Needs the additive `ModuleLoadException` type present to compile on master.) |
| `org.xvm.asm.MethodStructureAssemblyFailureTest` | `FileStructure.writeTo` completes without an exception for a method whose ops cannot be encoded; the method is serialized with zero op bytes after a one-line stderr print. | Method op-assembly failure was non-terminal: a compiler defect could persist a corrupt module that read back as a successful build. |
| `org.xvm.asm.OpRuntimeCacheTest` | Runtime `Op` condition/common-type cache fields still exist, `JumpVal`/`JumpVal_N` still expose owner-bearing switch-table fields, and `JumpNFirst` still uses a plain boolean for `assert:once` state. | Shared decoded op graphs could retain frame-owned constants, handles, type constants, and maps from one execution/container; parallel `assert:once` execution had no exact one-winner guarantee. |
| `org.xvm.asm.RegisterHashCodeTest` | Equal register metadata objects have different hashes. | Hash/equality contract was broken for ordinary map/set use. |
| `org.xvm.asm.VersionTest` | Equal `VersionTree` values have different hashes. | Hash/equality contract was broken for version metadata. |
| `org.xvm.asm.constants.ConstantRangeOwnerTest` | No ambient pool throws; wrong ambient pool returns wrong owner. | Numeric range folding used hidden current-pool ownership. |
| `org.xvm.asm.constants.NestedIdentityOwnerTest` | Nested generic resolution uses the wrong ambient pool. | Resolver-backed nested identities ignored the explicit output pool. |
| `org.xvm.asm.constants.TypeConstantRecursionDiagnosticsTest` | Recursion diagnostics use `HashSet`, not a concurrent set. | Process-global diagnostic state was mutated unsafely during type checks. |
| `org.xvm.compiler.CompilerThisEscapeConstructionTest` | Lexer/parser/AST constructor shape guards fail. | Compiler constructors called overridable hooks while incompletely constructed. |
| `org.xvm.javajit.JitConstructorEscapeTest` | JIT descriptor-construction shape guard fails. | Local JIT constructor-time owner/descriptor publication remained. |
| `org.xvm.javajit.JitFailurePropagationTest` | Generated unhandled XTC exception branch only prints diagnostics and has `catch (Throwable ignore)`; `nType` bridge dispatch catches `IllegalAccessException | InvocationTargetException` together. | JIT/direct execution could report success after an unhandled generated exception if the connector result was already zero; bridge reflection could convert a real generated `nException` into Unsupported; diagnostic rendering could hide arbitrary VM failures. |
| `org.xvm.runtime.RuntimeThisEscapeConstructionTest` | Runtime constructor shape guards fail. | Container, heap, templates, method handles, and field chains used constructor escapes. |
| `org.xvm.runtime.NativeTemplatesTest#typeHandlesAreCachedByContainerOwner` | `TypeConstant.m_handle` still exists on master. | Runtime Type handles were cached on a pool/type object instead of under the requesting container owner. |
| `org.xvm.runtime.GenericHandleCloneAsTest#sameOwnerCloneKeepsInflatedRefOuterViewLocal` | The source inflated ref's `$outer` changes to the cloned access view. | `GenericHandle.cloneAs(...)` shallow-copied the handle, shared the final field array, and then rewrote a view-specific `RefHandle.$outer` inside that shared backing. |
| `org.xvm.runtime.NativeCallbackRegistrationTest` | LocalClock constructor registration, NanoTimer `catch (Throwable)` swallowing, and xRTServer missing rollback are still present. | Native keep-alive registration was not exception-safe; schedule/bind failure could strand callback counts and keep a container non-idle forever. |
| `org.xvm.runtime.RuntimeFailurePropagationTest` | `MainContainer.invoke0()` still builds `". Cause: " + e.getMessage()` and drops the original exception; worker catch blocks still print runtime failures without publishing them to `join()`; the op loop still catches every `Throwable` and raises generic XTC `"Run-time error"`. | Startup/invocation diagnostics lost owner, module-load, stack, suppressed, and cause-chain information; worker defects could be printed on stderr while the host boundary later reported success; VM/runtime defects from op execution could become user-catchable language exceptions. |
| `org.xvm.runtime.template.reflect.RefHandleConstructionTest` | Register ref/node handle factory guards fail. | Runtime handle constructors published or mutated visible state before completion. |
| `org.xvm.runtime.template.annotations.FutureCompletionSafetyTest` | `Future.and` reads `cfThis` twice on the fast path and uses `assert false` for async get failure. | Combined futures can use the wrong input or hide async completion failure when assertions are disabled. |
| `org.xvm.runtime.template._native.fs.RawOSFileChannelSubmitTest` | `RawOSFileChannel.submit` discards the `CompletableFuture` returned by `scheduleIO(task)`. | Async write failure can disappear after the native method has already returned OK. |
| `org.xvm.runtime.template.text.RegExHandleTest` | Compiled regex cache field shape is the old mutable cache. | Lazy regex cache publication was not final/lazy-safe. |
| `org.xvm.tool.CompilerCodegenFailureTest` | `Compiler.generateCode(...)` catches `Throwable`, prints to stderr, logs, and continues the retry loop. | Code generation can continue after an unchecked compiler defect or VM error while mutating module/constant-pool state. |
| `org.xvm.tool.ModuleInfoTest` | Explicit-resource constructor calls overridable `getResourceDir()`. | Tool metadata constructor escaped through virtual dispatch. |
| `org.xvm.util.UtilityConstructorEscapeTest` | `PackedInteger`, `ListSet`, and `HasherReference` constructor guards fail. | Utility constructors called public/protected helpers while subclass construction was incomplete. |

## Branch Tests That Cannot Run On Master Without Backporting APIs

These still matter, but they are not direct "red on master" behavioral tests.
They protect new invariants introduced by the branch, such as diagnostics,
explicit owner APIs, and typed runtime accessors.

| Test | Missing master API or branch-only invariant |
| --- | --- |
| `org.xvm.api.InterpreterConnectorTest` | `OwnershipDiagnostics` and connector diagnostic-container access. |
| `org.xvm.asm.AsmConstructorEscapeTest` | `MultiMethodStructure.createMethodCopyingParameters(...)`. |
| `org.xvm.asm.ConstantAdoptionTest` | `ConstantAdoptionValidator` and its validation property. |
| `org.xvm.asm.ConstantPoolDiagnosticsTest` | Current-pool assertion/removal APIs, `xvm.asm.validateConstantPoolCurrentScope`, and late-registration diagnostics. |
| `org.xvm.asm.ConstantPoolRegistrationDeadlockTest` | Registration-completion guard (branch-only). Red on the branch's pre-fix guard shape, which awaited another thread's registration completion inside the pool monitor and deadlocked the pool; master has no completion guard to deadlock. |
| `org.xvm.asm.constants.MethodInfoTest` | `MethodInfo.create(...)` factory and owned-body model. `owningFreshBodyDoesNotFabricateSelfTarget()` is additionally red on the branch's own broken owned-copy shape (post-`93189541f`), where fresh bodies gained fabricated self targets that corrupted union/difference TypeInfo merges and broke the XDK build at lib_json. |
| `org.xvm.asm.constants.TypeConstantOwnerApiTest` | Explicit-pool covariance/contravariance APIs. |
| `org.xvm.asm.constants.TypeInfoMemberOwnershipTest` | `PropertyInfo.create(...)` factory and owner-copy model. |
| `org.xvm.runtime.ClassCompositionLateRegistrationTest` | Late-registration diagnostic API. |
| `org.xvm.runtime.ClassCompositionSafePublicationTest` | Native-container factory and branch field-publication shape. |
| `org.xvm.runtime.NativeTemplatesTest` | Owner-local native template table and related runtime helper APIs. |
| `org.xvm.runtime.OwnershipDiagnosticsTest` | Runtime ownership diagnostic graph walker/validator. |
| `org.xvm.runtime.RuntimeTest` | Generic `Runtime.registerContainer(...)` return type. |
| `org.xvm.runtime.SingletonConstantTest` | Branch helper construction surfaces used by singleton lifecycle tests. |
| `org.xvm.runtime.template._native.web.xRTServerTest` | Route/key-manager helper API changes. |
| `org.xvm.util.LazyTest` | `Lazy.Owner` and `Lazy.ofOwner(...)`. |
| `manualTests/build.gradle.kts` direct stress task | `XtcRunTask.validateRuntimeOwnership`. |

## Tests That Pass On Master And What They Mean

| Test/task | Why passing on master is expected |
| --- | --- |
| `org.xvm.runtime.NativeTemplateOldPatternTest` | It is a self-contained demonstration of old/new model shapes, not a direct call into master production templates. |
| `manualTests:runOne -PtestName=StringBufferTest -PtestArgs=deterministic-only` | The deterministic string-buffer sequence is a regression guard for branch behavior; the specific deterministic case did not expose a master failure in the audit run. |

## Stress Failures And Guard Caveats

`manualTests:runParallelStress -PstressModules=TestProps` exposed the adopted
`SingletonConstant` owner leak. The failure looked like an XTC lazy assertion,
but ownership diagnostics showed that one container was invoking with another
container's module/package/lazy handle graph. The cause, replacement, and tests
are documented in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md).

`manualTests:runDirectSequenceStress` is the same-JVM repeated-execution guard.
It validates that repeated direct runner invocations do not reuse stale
container-owned runtime state from a prior run in the same Gradle JVM.

The late-registration diagnostic property currently has an important Gradle
caveat: passing `-Dxvm.asm.validateConstantPoolLateRegistration=true` to Gradle
also enables it during XTC compilation. In the audit run, that broad command
failed earlier in `:xdk:lib-ecstasy:compileXtc` with a stack repeating through
`MethodInfo.equals(...)`, `MethodBody.equals(...)`, and `Handy.equals(...)`.
That was a separate metadata equality recursion finding, not proof that the
runtime late-registration fix failed. This branch now fixes that equality shape:
`MethodBody.equals(...)` and `MethodBody.hashCode()` compare `FromInto`,
`Implicit`, and `Union` method targets by stable method identity shape instead
of recursively walking owner metadata graphs. The focused
`MethodInfoTest.methodInfoEqualityDoesNotRecurseThroughMethodTargets()` test
builds the cyclic target shapes directly. The focused
`ClassCompositionLateRegistrationTest` tests remain the proof for the
class-composition access-type slice until the Gradle task can pass runtime-only
JVM properties to the direct runner.

The optimized method/property chain cache tests are source-shape and
parallel-publication proofs rather than heavy end-to-end reproducers.
`MethodInfoTest.optimizedMethodChainCacheIsSafelyPublishedInParallel()` fails
the master source-shape requirement because `m_aBodyResolved` is not volatile
there, and it exercises parallel first chain construction. The corresponding
`TypeInfoMemberOwnershipTest.optimizedPropertyAccessorChainsAreSafelyPublishedInParallel()`
test does the same for `PropertyInfo.m_chainGet` and `m_chainSet`. These tests
exist because the old caches looked idempotent but had no Java memory-model
publication edge, and because `PropertyInfo` accepted nested ids while caching
only one top-level slot.

`TypeInfoMemberOwnershipTest.derivedTypeInfoCachesAreSafelyPublishedInParallel()`
covers the related `TypeInfoReal` cache wave. It fails the master source-shape
requirements because `m_mapPropertiesByName`, `m_mapMethodsBySignature`,
`m_delegates`, `m_fCacheReady`, and `m_fChildrenChecked` are not volatile on
master. It also proves parallel first access observes one immutable name map,
one synchronized expanding signature cache, one delegate view, and one safely
published abstractness cache. The signature cache distinction is important:
`getMethodBySignature(...)` caches substitutable/runtime lookup hits with
`putIfAbsent(...)`, so replacing the old `HashMap` with an immutable snapshot
was not semantically equivalent. A short `manualTests:runDirectSequenceStress`
attempt caught that draft bug during `lib-ecstasy` compilation as
`UnsupportedOperationException` from `TypeInfoReal.getMethodBySignature(...)`.
The unit deliberately does not drive the full virtual-child `isNewable()` pool
registration path, because a hand-built test `TypeInfoReal` is not a registered
pool owner. That path remains covered by runtime stress and late-registration
diagnostics.

`TypeInfoMemberOwnershipTest.propertyHelperCachesAreSafelyPublishedInParallel()`
closes the remaining `PropertyInfo` helper-cache source shape. It fails the
master source-shape requirement because `m_annotations`, `m_FInjected`,
`m_FImplicitlyAssigned`, `m_typeBaseRef`, `m_idGetter`, and `m_idSetter` are
plain fields there. The branch test then races first access and proves the
replacement still returns one cached helper identity per owned property: the
same cached annotation array, base Ref/Var type, getter id, setter id, and
boolean helper values are observed by every thread. This is not a heavy
end-to-end crash reproducer, but it is the right proof for the defect: the old
code lacked a Java memory-model publication edge around owner-pool helper
values.

`ClassCompositionSafePublicationTest.accessViewsShareSafelyPublishedInceptionRuntimeCaches()`
covers the related runtime composition field-layout and helper caches. On
master, field layout is a loose group of `m_mapFields`, `m_cRegularFields`,
`m_fHasOuter`, and `m_fHasSpecial`; access-view constructors copy those values
at construction time. A view created before field layout exists can therefore
keep stale null/default layout while the inception composition later builds the
real layout. On master, the field-name array and auto-initializer cells are also
plain fields. Access-view field-name arrays are clone-local duplicate lazy
cells, and access-view initializer cells copied whatever inception value existed
at clone construction time. That means views can allocate unnecessary duplicate
owner-bearing `StringHandle[]` arrays or duplicate synthetic initializers even
though the owner, field layout, and struct type are inception-owned. The branch
test creates the protected view before installing a synthetic layout, uses a
real native owner, races canonical and protected views, and verifies one safely
published inception-owned field-layout map, one field-name array, and a final
owner-lazy initializer cell. The test is not a direct master runner because it
uses branch lifecycle helpers, but the source-shape defect is explicit in the
old field-layout group plus the old `m_ashFieldNames` and `m_methodInit`
declarations.

`ClassCompositionSafePublicationTest.propertyCompositionStructViewIsOwnerLazyAndShared()`
covers the custom-property struct-view cache. On master,
`PropertyComposition.m_clzStruct` is a plain mutable lazy field that publishes a
runtime `PropertyComposition` identity without a memory edge. The branch test
uses a real native `String.size` property composition, races first `STRUCT`
access, and proves all callers observe the same struct-view identity. It also
checks the replacement source shape: final `Lazy.Owner` for the struct view and
final inception/struct role fields. The test is branch-only because master does
not have this final lazy field shape, but the broken old source pattern is the
single `if (m_clzStruct == null) m_clzStruct = ...` cache.

## Review Rule

When splitting this branch into smaller PRs, each PR should cite one of:

- a direct master failure from the first table;
- a branch-only source-shape/diagnostic test from the second table, with an
  explanation of why the branch API is required to express the invariant;
- a stress failure from `stress-discovered-runtime-issues.md`;
- a source scan that proves the old pattern is gone and a targeted test that
  proves the replacement preserves behavior.
