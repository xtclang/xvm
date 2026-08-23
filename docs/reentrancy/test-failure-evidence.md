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
| `org.xvm.asm.OpRuntimeCacheTest` | Runtime `Op` condition/common-type cache fields still exist, `JumpVal`/`JumpVal_N` still expose owner-bearing switch-table fields, and `JumpNFirst` still uses a plain boolean for `assert:once` state. | Shared decoded op graphs could retain frame-owned constants, handles, type constants, and maps from one execution/container; parallel `assert:once` execution had no exact one-winner guarantee. |
| `org.xvm.asm.RegisterHashCodeTest` | Equal register metadata objects have different hashes. | Hash/equality contract was broken for ordinary map/set use. |
| `org.xvm.asm.VersionTest` | Equal `VersionTree` values have different hashes. | Hash/equality contract was broken for version metadata. |
| `org.xvm.asm.constants.ConstantRangeOwnerTest` | No ambient pool throws; wrong ambient pool returns wrong owner. | Numeric range folding used hidden current-pool ownership. |
| `org.xvm.asm.constants.NestedIdentityOwnerTest` | Nested generic resolution uses the wrong ambient pool. | Resolver-backed nested identities ignored the explicit output pool. |
| `org.xvm.asm.constants.TypeConstantRecursionDiagnosticsTest` | Recursion diagnostics use `HashSet`, not a concurrent set. | Process-global diagnostic state was mutated unsafely during type checks. |
| `org.xvm.compiler.CompilerThisEscapeConstructionTest` | Lexer/parser/AST constructor shape guards fail. | Compiler constructors called overridable hooks while incompletely constructed. |
| `org.xvm.javajit.JitConstructorEscapeTest` | JIT descriptor-construction shape guard fails. | Local JIT constructor-time owner/descriptor publication remained. |
| `org.xvm.runtime.RuntimeThisEscapeConstructionTest` | Runtime constructor shape guards fail. | Container, heap, templates, method handles, and field chains used constructor escapes. |
| `org.xvm.runtime.template.reflect.RefHandleConstructionTest` | Register ref/node handle factory guards fail. | Runtime handle constructors published or mutated visible state before completion. |
| `org.xvm.runtime.template.text.RegExHandleTest` | Compiled regex cache field shape is the old mutable cache. | Lazy regex cache publication was not final/lazy-safe. |
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
| `org.xvm.asm.ConstantPoolDiagnosticsTest` | Current-pool assertion/removal APIs and late-registration diagnostics. |
| `org.xvm.asm.constants.MethodInfoTest` | `MethodInfo.create(...)` factory and owned-body model. |
| `org.xvm.asm.constants.TypeConstantOwnerApiTest` | Explicit-pool covariance/contravariance APIs. |
| `org.xvm.asm.constants.TypeInfoMemberOwnershipTest` | `PropertyInfo.create(...)` factory and owner-copy model. |
| `org.xvm.runtime.ClassCompositionLateRegistrationTest` | Late-registration diagnostic API. |
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
That is a separate compiler/constant-pool recursion finding, not proof that the
runtime late-registration fix failed. The focused
`ClassCompositionLateRegistrationTest` tests are the proof for the
class-composition access-type slice until the Gradle task can pass runtime-only
JVM properties to the direct runner.

## Review Rule

When splitting this branch into smaller PRs, each PR should cite one of:

- a direct master failure from the first table;
- a branch-only source-shape/diagnostic test from the second table, with an
  explanation of why the branch API is required to express the invariant;
- a stress failure from `stress-discovered-runtime-issues.md`;
- a source scan that proves the old pattern is gone and a targeted test that
  proves the replacement preserves behavior.
