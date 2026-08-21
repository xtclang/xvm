# Must-Audit Backlog

This file tracks repository-wide state patterns that are not yet proven defects
but are too owner-sensitive or concurrency-sensitive to treat as ordinary style
cleanup.

`Must audit` sits between `must fix` and `should fix`:

- `Must fix`: a concrete race, wrong-owner cache, constructor escape, or
  broken lifecycle state is known.
- `Must audit`: the code may be safe only because of an assumption that is not
  encoded in the API, such as single-threaded compilation, per-container method
  graph ownership, no same-JVM reuse, or manual ThreadLocal cleanup.
- `Should fix`: the code is unnecessarily mutable or awkward, but current
  owner/lifecycle evidence does not make it a likely correctness bug.

The standard for closing a `must audit` item is higher than "it has not crashed
yet". Close it only by proving confinement/ownership, adding a focused test, or
moving the state behind an explicit owner/synchronization model.

## Current Wide Scans

These commands were run on branch `lagergren/lazy-instance` on 2026-08-21.

Manual lazy null caches across all Java sources:

```bash
rg -U --pcre2 -c \
  "if\s*\(\s*((?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,320}\1\s*=(?!=)" \
  javatools/src/main/java | awk -F: '{s+=$2} END {print s+0}'
```

```text
43 strong same-field lazy-init matches across javatools/src/main/java
23 of those are in runtime/asm
```

Non-final static fields across all Java sources:

```bash
rg -n --pcre2 \
  "^\s*(public|protected|private)?\s*static\s+(?!final\b)(?!class\b|interface\b|enum\b)[^;(=]+\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java | sort
```

```text
javatools/src/main/java/org/xvm/compiler/ast/ConditionalStatement.java:80
javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java:220
javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java:316
javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:1265
javatools/src/main/java/org/xvm/javajit/Ctx.java:186
javatools/src/main/java/org/xvm/javajit/builders/EnumValueBuilder.java:90
javatools/src/main/java/org/xvm/javajit/builders/EnumerationBuilder.java:77
javatools/src/main/java/org/xvm/javajit/builders/EnumerationBuilder.java:82
```

Ambient and weak/identity state scan:

```bash
rg -n "ThreadLocal|TransientThreadLocal|ScopedValue|WeakHashMap|IdentityHashMap|volatile|synchronized\s*\(" \
  javatools/src/main/java/org/xvm
```

This scan is intentionally broad. Each hit needs classification by owner and
lifetime, not blind conversion.

## Audit Categories

| Priority | Category | Representative sites | Why it must be audited | Proper closure |
| --- | --- | --- | --- | --- |
| Fixed in this branch | Runtime-executed owner-bearing `Op` caches | `asm/op/JumpCond.m_cond`, `asm/op/JumpNCond.m_cond`, runtime write-back to `asm/OpTest.m_typeCommon` and `asm/OpCondJump.m_typeCommon` | These cached constants obtained from a `Frame`. If decoded op graphs are reused across containers or constant pools, the cached value can carry the wrong owner. | The condition fields were removed, and common-type execution resolves from the current `Frame` without writing frame constants onto the shared `Op`. `OpRuntimeCacheTest` guards against reintroducing the pattern. |
| Must audit, likely safe if eagerly linked before publication | Runtime `Op` address/link caches | `asm/OpJump.m_opDest`, `asm/OpCondJump.m_opDest`, `asm/op/LoopEnd.m_opDest`, `asm/op/OpSwitch.m_aOpCase`, `asm/op/JumpInt.m_aOpCase`, `asm/op/GuardStart.m_aOpCatch` | Mutable decoded instruction links are safe only if resolution happens during exclusive linking before runtime publication. Lazy runtime linking would be a plain-field race. | Document and test eager link ordering, or move to synchronized/atomic method-owner link state. |
| Must audit, runtime metadata owner boundary | Type and constant metadata caches | `asm/ConstantPool` volatile maps and thread-local pool, `asm/constants/TypeConstant` volatile caches and `ScopedValue`, `asm/constants/TypeInfoReal` maps, `runtime/ClassComposition.m_mapFields` | These are central owner-bearing metadata structures. Some use synchronization already, but same-JVM incremental compile/runtime reuse depends on exact ownership. Runtime boundary `ConstantPool` scopes are now asserted, but deeper ASM helpers still use ambient lookup. | For each cache, document owner, key, invalidation, and publication. Run with `-Dxvm.asm.validateConstantPoolLateRegistration=true` to find constants created after runtime publication. Convert plain lazy fields to final `Lazy` or owner-owned concurrent caches when they escape one thread. |
| Must audit, compiler reentrancy | Compiler AST and context mutation | `compiler/ast/Context.m_mapByName`, `NameResolver.m_constant`, `InvocationExpression.m_method`, `LambdaExpression.m_lambda`, statement break/continue lists | The compiler historically assumes request-local AST mutation. Incremental and parallel compilation makes that assumption dangerous unless request ownership is explicit. | Prove AST/request confinement, or move caches to a compilation context keyed by request. Separate known global counters are fixed on `lagergren/compiler-counter-atomics`. |
| Must audit, process-global compiler/JIT state | Non-final statics | The four compiler counters; `javajit/Ctx.MD_inject`; JIT builder field-name strings | Non-final statics are shared across every compile/runtime in the JVM and usually have no reset story. | Make immutable constants `static final`; use atomics or owner/request state for counters. |
| Must audit, ambient context | `ThreadLocal`, `TransientThreadLocal`, and `ScopedValue` | `ConstantPool.s_tloPool`, `MultiMethodStructure.s_tloIgnoreNative`, `ServiceContext.s_tloContext`, `TypeConstant.s_context`, `javajit/Ctx.Current` | Ambient context hides dependencies from APIs. ThreadLocal state also leaks across pooled threads if cleanup is missed. This branch removed raw `ConstantPool.setCurrentPool(...)`; the remaining current-pool bridge is lexical and asserted at runtime boundaries. | Prefer explicit owner parameters for permanent APIs. Use `ScopedValue` only as a bounded transitional bridge with lexical lifetime. |
| Must audit, weak/identity owner registries | Weak/identity maps | Fixed in this branch for `Runtime.f_containers`; remaining audit examples are `ServiceContext.m_mapTransient`, `ServiceContext.f_mapOpInfo`, `ConstantPool.f_setValidPools`, and `OwnershipDiagnostics` maps | These maps often encode identity ownership or lifecycle. They are not concurrent by default and weak keys can disappear at surprising times. `Runtime.findContainer(...)` was a real inconsistent-monitor bug and now uses the same monitor as registration/snapshotting. | Prove confinement or synchronization around every access; otherwise use owner-owned concurrent structures or immutable snapshots. |
| Should fix soon, must audit if owner-bearing | Static mutable arrays and collections | `BinaryAST.ALREADY_DISPLAYED`, `Token.KEYWORDS`, `NativeNames.reservedMethodName`, static `Op[]` wait-frame arrays, public/protected `NO_*` arrays | `final` protects only the reference. Public arrays and mutable static collections are shared mutable variables. | Replace true constants with immutable collections or private arrays plus defensive copies. Keep only stateless immutable op arrays as documented process-wide constants. |
| Should fix soon, must audit if externally shared | Public/protected mutable fields | `Frame` public execution fields, `ObjectHandle.m_clazz`, `ObjectHandle.m_fMutable`, array delegate storage fields, compiler AST protected fields | Broad mutability makes invariants unenforceable and hides synchronization requirements. Some are deliberate hot-path structs; some are accidental. | Make owner-bearing fields private and expose methods that preserve invariants. For hot-path structs, document owner-thread confinement. |

## Manual Lazy Null Caches

The focused classification of current same-field lazy-null sites is maintained
in [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md).

The rule is:

- If the receiver can be shared by runtime containers, fibers, compilation
  workers, or same-JVM executions, this is `must audit`.
- If the cached value is owner-bearing and the receiver can be shared across
  owners, it becomes `must fix`.
- If the receiver is proven request/thread confined, document that proof and
  leave it out of the first runtime-owner PR.

## Stress Verification Backlog

More same-JVM direct-mode stress is useful verification depth, not a known code
hole by itself. The first PR already adds `manualTests:runDirectSequenceStress`;
the next confidence wave is to run more modules and more iterations with
ownership validation enabled:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

The longer plan is in
[plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md): serial
same-JVM execution first, then structured failure artifacts, benchmark output,
parallel same-JVM execution, and Gradle direct-mode integration coverage.
