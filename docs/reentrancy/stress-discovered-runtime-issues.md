# Stress-Discovered Runtime Issues

This document tracks concrete failures found while using the parallel manual-test
runner to validate the runtime state-safety work. These are adjacent to the
startup-cache work, but they are not all the same class of bug. The useful rule
is to keep each failure honest: document the observed crash, the root cause, the
fix, and the test that proves the specific replacement.

## Adopted `SingletonConstant` Runtime State Leak

Status: fixed in this branch.

Observed failure:

```text
./gradlew :manualTests:runParallelStress \
  -PstressIterations=2 \
  -PstressModules=TestProps

!&lazyInstance.assigned
```

Running a single `TestProps` copy passed. Running two copies in the parallel
manual runner failed sporadically after both child containers linked the same
module into the same parent runtime. The XTC-level assertion made it look like a
plain `@Lazy` property bug: a fresh module instance in one child container saw a
lazy reference as already assigned before that container had touched it.

Ownership validation then showed the real shape of the failure before the XTC
assertion:

```text
Invalid XVM runtime ownership
owner-mismatches: 4
  - mgmt.Container.invoke module target expected=C0 actual=external@... object=PackageHandle@...
  - [1] expected=C0 actual=external@... object=ProxyHandle@...
  - [2] expected=C0 actual=external@... object=LazyHandle@...
  - [1] expected=C0 actual=external@... object=PackageHandle@...
```

That means container C0 was about to invoke code with a module/package handle
whose owner was not C0. The leaked `LazyHandle` was only a symptom: the second
container was reusing another container's module singleton object graph.

### Root Cause

`ConstantPool.register(...)` adopts a constant from one pool into another pool
by calling `Constant.adoptedBy(...)`. The base implementation uses
`Object.clone()`. That is already a questionable legacy design, but the concrete
bug was worse for constants that carry transient runtime state.

`SingletonConstant` now correctly stores singleton initialization in one final
state cell:

```java
private final transient AtomicReference<InitState> f_state =
        new AtomicReference<>(InitState.EMPTY);
```

The final field fixed the split lifecycle race inside one constant, but
`Object.clone()` shallow-copied the `AtomicReference` itself. Two adopted
`SingletonConstant` objects in different pools therefore pointed at the same
mutable runtime state cell. Once one pool initialized its module, package, or
property singleton, another pool could observe the same completed handle even
though that handle was owned by the first container.

This is a useful Java memory-model lesson: a final reference is safely
published, but it does not make the object referenced by that field immutable or
owner-local. Cloning a final reference to mutable state intentionally shares the
state.

The same adoption hazard exists for transient runtime handles on
`FSNodeConstant` and `FileStoreConstant`, and for the derived
`FSNodeConstant.m_constPath` literal cache. Those classes did not have the
`AtomicReference` indirection, but the base clone still copied owner-local cache
state into a constant registered under a different pool.

### Replacement

`SingletonConstant.adoptedBy(...)` now constructs a new `SingletonConstant`
instead of using the base shallow clone:

```java
protected SingletonConstant adoptedBy(ConstantPool pool) {
    return new SingletonConstant(pool, f_fmt, m_constClass);
}
```

The adopted constant keeps the same logical constant value and class identity,
and normal constant registration still adopts/registers the referenced class
constant into the target pool. The runtime initialization cell is fresh and
empty for the target owner.

`FSNodeConstant.adoptedBy(...)` and `FileStoreConstant.adoptedBy(...)` still use
the legacy clone path for their serialized constant payload, but immediately
clear cloned owner-local state. `FileStoreConstant` clears its runtime handle;
`FSNodeConstant` clears its runtime handle and derived path cache:

```java
that.m_handle    = null;
that.m_constPath = null;
```

That containment is not a beautiful API. It is the smallest correct patch for
the current constant-pool design because adoption is the exact owner boundary
where runtime state must not cross. A cleaner long-term design would replace
clone-based adoption with explicit copy/adoption constructors that copy only
constant-pool value state and never copy transient runtime state.

### Ramifications

This fix preserves XTC semantics:

- The logical constants compare/register the same way after adoption.
- Each pool/container still gets the same runtime singleton cache behavior it
  had after first initialization: compute once per owner, then reuse.
- No runtime handle from pool A is carried into pool B.
- The normal single-container steady state does not allocate extra handles or
  recompute after initialization.
- The parallel runner no longer relies on timing to avoid cross-container
  module/package/property leaks.

It also avoids the larger, rejected workaround that temporarily cloned linked
`FileStructure` objects in `xContainerLinker`. That workaround added footprint,
did not address the shared `AtomicReference`, and would still have left any
other adopted constant with copied transient runtime state. The correct fix is
at the adoption boundary.

### Regression Proof

The focused Java tests are:

```text
SingletonConstantTest.adoptedSingletonHasOwnerLocalRuntimeState()
SingletonConstantTest.adoptedFsNodeClearsOwnerLocalHandle()
SingletonConstantTest.adoptedFileStoreClearsOwnerLocalHandles()
OwnershipDiagnosticsTest.validatorRejectsForeignRootHandle()
OwnershipDiagnosticsTest.validatorWalksHandleFieldGraph()
```

On the old shallow-clone behavior, the adopted singleton test observes the
source handle through the adopted constant, and the file-system constant tests
observe the source runtime handle after adoption. On this branch the adopted
objects start empty while the source object retains its cache.

The stress command that exposed the bug also passes with ownership validation
enabled by `manualTests:runParallelStress`:

```bash
./gradlew :manualTests:runParallelStress \
  -PincludeBuildLang=false \
  -PincludeBuildAttachLang=false \
  -PstressIterations=2 \
  -PstressModules=TestProps \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```

## Late Constant Registration During User Execution

Status: diagnostic added; runtime warmup/design fix still open.

Observed failure with the new late-registration guard:

```text
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestProps \
  -Dxvm.asm.validateConstantAdoption=true \
  -Dxvm.asm.validateConstantPoolLateRegistration=true

java.lang.IllegalStateException:
ConstantPool registered private TestProps:Standard after runtime publication
by MainContainer.invoke0(run)
```

The marker is installed at the `MainContainer.invoke0(...)` call-chain boundary:
after method lookup, entry setup, and module singleton resolution, but before
the user method body is invoked. That means this failure is not the module
singleton startup work. It is a runtime class-composition path creating new
constant-pool entries while user code is already executing.

The concrete stack was:

```text
ConstantPool.register(...)
ConstantPool.ensureAccessTypeConstant(...)
ClassComposition.<init>(...)
Container.ensureClassComposition(...)
ClassTemplate.getCanonicalClass(...)
New_1.process(...)
testStandardProperty() (prop.x:20)
```

The second reported `StringBuffer` registration in the same run was exception
formatting fallout after the diagnostic exception, not the root trigger.

### Root Cause

`ClassComposition` constructs the private and struct access views for the
inception type in its constructor:

```java
f_typeInception = pool.ensureAccessTypeConstant(typeInception, Access.PRIVATE);
f_typeStructure = pool.ensureAccessTypeConstant(typeInception, Access.STRUCT);
```

If a class is first instantiated during user execution, those access-type
constants are registered during execution as well. The constants are logical
pool-owned values, so this is not the same kind of wrong-owner leak as the old
global `INSTANCE` fields or adopted singleton state. It is still a runtime
publication problem: a supposedly running/published pool can keep mutating its
constant list and lookup maps on demand.

### Proper Fix Direction

Do not weaken the guard by ignoring access-type constants indefinitely. Choose
one of these explicit designs:

- pre-warm class compositions and their private/struct access-type constants
  before the pool is marked runtime-published;
- move class-composition helper state that is not serialized constant identity
  out of `ConstantPool` registration and into an owner-local composition table;
- if late registration is intentionally allowed for these logical constants,
  add a narrow allowlist with a comment that states why registration is
  deterministic, owner-local, and safe under same-pool concurrency.

The first option is the preferred runtime direction because it moves mutable
pool extension back into startup/linking, where publication ordering is much
easier to reason about.

## Direct Sequence Validator Native-Parent False Positive

Status: fixed in the diagnostic validator.

Observed failure while checking repeated same-JVM direct execution:

```text
./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestProps

Runtime ownership validation failed after module TestProps
Char{char='0', index=48} expected=C0 actual=external@...
EnumValueConst{singleton-service=False} expected=C0 actual=external@...
String{char-string=""} expected=C0 actual=external@...
```

This did not reproduce the adopted `SingletonConstant` leak. It exposed an
over-strict diagnostic rule. The constant heap for a main container can legally
memoize canonical values whose handle composition belongs to that container's
own `NativeContainer` parent: small chars, ints, booleans, strings, and native
enum values are examples. The validator already allowed the same
native-parent-sharing rule for template lookup, because
`Container.getTemplate(...)` delegates shared core templates to the native
parent. It had not applied the rule to constant-heap handle values.

### Root Cause

`OwnershipDiagnostics.dumpConstHeap(...)` traversed `Container.f_heap` with a
strict "everything under C0 must be owned by C0" expectation. That is too
strong for canonical native-parent runtime values. It correctly catches handles
from another main container, but it also flagged legal handles from C0's own
native parent as `external@...` because the native parent is not one of the main
containers retained by the direct-run stress window.

### Replacement

The const-heap traversal now uses the same narrow native-parent allowance as
native-template traversal:

```java
dumpMap("constHeap.entries", readField(heap, "f_mapConstants"),
        container, 2, true);
```

That changes only diagnostics. It does not change runtime ownership,
construction, or caching. The validator still rejects a main container using a
handle owned by another main container or by another runtime's native parent.

### Ramifications

- Direct same-JVM validation can inspect realistic warmed constant heaps without
  failing on expected core/native values.
- The diagnostic still catches the class of bug this branch is fixing: stale
  owner-scoped values from a previous run or a parallel sibling container.
- This explains why direct-sequence validation can show many native-parent
  values even when parallel `TestProps` is fixed. The former is legal canonical
  sharing; the latter was wrong-owner module singleton state.

## Native `OSFileNode.created` Lazy Owner Leak

Status: fixed in this branch.

Observed failure while adding `TestFiles` to the same-JVM direct sequence
stress:

```text
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=1 \
  -PsameJvmModules=TestFiles

Runtime ownership validation failed after module TestFiles
GenericHandle type=Time expected=external actual=C0
```

The typed ownership dump showed the leaked handle below the native file-store
root:

```text
SingletonConst{singleton-const=TestFiles} owner=C0 type=TestFiles
  [2] immutable FileStore owner=external/native
    [1] LazyHandle type=immutable _native:fs.OSFileStore:private.root owner=external
      [0] NodeHandle type=immutable Directory owner=external
        [3] LazyHandle type=immutable _native:fs.OSDirectory:private.created owner=external
          [0] GenericHandle owner=C0 type=Time OWNER-MISMATCH expected=external
```

This is exactly the kind of failure the ownership diagnostics are meant to make
obvious: a native-owned object graph retained a handle created by the application
container that happened to call a property getter.

### Root Cause

`OSStorage` is a native service. Its `OSFileStore` and root `OSDirectory` are
owned by the native runtime container, not by each application container using
file APIs. `OSFileNode.created` was declared as an XTC `@Lazy` property:

```xtc
@Lazy Time created.calc() = new Time(createdMillis*TimeOfDay.PicosPerMilli);
```

That is wrong for a native-owned node whose getter can execute in a caller
frame. The getter allocated a `Time` value in the caller's owner context and the
lazy property cached that caller-owned handle inside the native-owned file node.
The next ownership walk then found a native graph containing a child-container
`Time`.

Parallel execution was not required for this bug. It was exposed by the
same-JVM direct stress because that harness keeps enough live ownership roots to
notice that the cached value belongs to the wrong container. In normal runs this
kind of leak can retain an application container through the native file-system
graph or let a later container observe a value created under an earlier caller.

### Replacement

`OSFileNode.created` is now a plain computed getter:

```xtc
Time created.get() = new Time(createdMillis*TimeOfDay.PicosPerMilli);
```

This matches the existing `modified` and `accessed` properties, which were
already computed getters rather than `@Lazy` caches. It preserves the visible
value calculation and removes only the invalid cache location. The native node
no longer stores a caller-owned `Time` handle.

### Ramifications

- No normal XTC semantics change: every read still returns the same timestamp
  value derived from the node metadata.
- No steady-state owner cache is lost for `modified` or `accessed`; `created`
  now follows their existing behavior.
- The footprint decreases for native file nodes because they no longer retain a
  lazy storage slot for `created`.
- The only performance cost is constructing a small immutable `Time` value on
  each `created` read. That is the safer representation until the runtime has an
  owner-aware native file metadata cache. Caching this value in the native node
  is unsound because the value is a handle allocated under the caller's owner.

### Regression Proof

The same focused stress run passed after the change and did not report ownership
mismatches:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=1 \
  -PsameJvmModules=TestFiles \
  --rerun-tasks --no-build-cache --console=plain
```

The broader wave-5 stress run also completed with a successful Gradle result:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestRegularExpressions,TestFiles,TestProps \
  --rerun-tasks --no-build-cache --console=plain
```

That broader run still logged asynchronous `OSStorage` class-loading errors
documented below. Those logs are a separate direct-runner/classloader issue:
they did not produce ownership mismatches, and the reported nested class was
present in the built `javatools` jar.

## `StringBuffer` Chunk Mutability Invariant

Status: fixed in this branch.

Observed failure:

```text
./gradlew :manualTests:runParallelStress \
  -PstressIterations=2 \
  -PstressModules=TestServices

ecstasy:TypeMismatch: Expected "immutable Array<Char>", actual "Array<Char>"
    at collections.Array.add(Array.Element) (Array.x:418)
    at text.StringBuffer.commitBuf() (StringBuffer.x:630)
    at ConsoleBack.print(Object, Boolean) (runner.x:90)
```

This is separate from the Java static-template/metadata race. The failing stack
is inside the manual runner's buffered console service. Parallel execution made
the output shape more likely, but the root cause is a deterministic
`StringBuffer` representation bug.

### Root Cause

`StringBuffer` stores already committed chunks in `bufs` and the current append
buffer in `buf`. Large stable string input can be added directly as an immutable
`Char[]` chunk. That can narrow the runtime element type of `bufs` to immutable
`Char[]` chunks. Later, `commitBuf()` tried to append the current mutable `buf`
to the same chunk list:

```text
large immutable string chunk -> bufs element type is immutable Array<Char>
fill current mutable buffer  -> buf is mutable Array<Char>
append one more character    -> commitBuf() appends mutable buf to bufs
```

The resulting `TypeMismatch` is correct from the array type system's point of
view: a list that has become a list of immutable character arrays must not accept
a mutable character array.

### Replacement

`commitBuf()` now normalizes committed append buffers to immutable chunks before
adding them to `bufs`. That matches the existing representation used for large
stable string chunks and is semantically correct because committed buffers are no
longer appended to. If a later operation needs to mutate an earlier committed
chunk, existing code already reifies that chunk before replacing elements.

This preserves behavior and performance:

- No extra copy is required for the normal full-buffer path; `freeze(inPlace=True)`
  can publish the existing buffer as the committed chunk.
- The old copy-to-trim-waste path is unchanged for oversized buffers.
- `bufs` keeps the same chunked representation and still avoids flattening on
  every append.
- Public `StringBuffer` behavior is unchanged; only the internal chunk mutability
  invariant is made stable.

### Regression Tests

The focused deterministic test is:

```text
StringBufferTest.committedChunksStayAppendable()
```

It constructs the old failing sequence directly:

```text
add 65-character string chunk
add 64 characters to fill the mutable append buffer
add one more character to force commitBuf()
verify iteration, indexing, and toString()
```

Run it without the long random loop:

```bash
./gradlew :manualTests:runOne \
  -PtestName=StringBufferTest \
  -PtestArgs=deterministic-only \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```
The stress command that originally exposed the issue also passes after the fix:

```bash
./gradlew :manualTests:runParallelStress \
  -PstressIterations=2 \
  -PstressModules=TestServices \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```

## `xRTCompiler` Exception Diagnostics List

Status: fixed in this branch.

Observed failure:

```text
./gradlew :manualTests:runParallelStress \
  -PstressIterations=... \
  -PstressModules=TestCompiler

java.lang.UnsupportedOperationException
    at java.base/java.util.ImmutableCollections.uoe(...)
    at java.base/java.util.ImmutableCollections$AbstractImmutableCollection.add(...)
    at org.xvm.runtime.template._native.lang.src.xRTCompiler.addError(...)
```

This was not an ownership leak, but the stress run found it while validating the
same startup-state work. The native compiler service had an exception-reporting
path that assumed the compiler diagnostic list was mutable.

### Root Cause

`CompilerAdapter.getErrors()` returns a list produced by `stream().toList()`.
On current Java that result is unmodifiable. The normal compilation-completion
path only reads the list, but the exception path tried to append the caught Java
exception to it before returning the diagnostic array to XTC code.

That is bad runtime design for two reasons:

- Error reporting became another crash source exactly when the runtime was
  already trying to report a failure.
- The mutability contract was implicit. A native service helper accepted a
  `List<String>` and modified it even though the producer did not promise a
  mutable list.

### Replacement

`xRTCompiler.addError(...)` now creates a mutable copy before adding the caught
exception text:

```java
listErrors = new ArrayList<>(listErrors);
listErrors.add(exception.toString());
```

This preserves all existing compiler diagnostics and appends the exception in
the same externally visible position as before. It does not change owner
semantics or caching: the resulting string array is still built in the active
compiler service's container, and the file-template array type is still resolved
from that native compiler's owner.

### Regression Proof

The `TestCompiler` parallel stress shape that exposed this now completes without
the unmodifiable-list crash. The fix is also covered structurally by the code
comment in `xRTCompiler.addError(...)`, because the important invariant is that
compiler-owned diagnostics may be immutable snapshots and native exception
reporting must not mutate them in place.

## `xException` Subclass Formatter Metadata

Status: fixed in this branch.

Observed failure:

```text
./gradlew :manualTests:runParallelStress \
  -PstressIterations=5 \
  -PstressModules=TestReflection,TestArray,TestServices,TestTuples

java.lang.NullPointerException:
Cannot invoke "org.xvm.asm.MethodStructure.getMaxVars()" because
"methodFormat" is null
    at org.xvm.runtime.template.xException.buildStringValue(xException.java:81)
    at org.xvm.runtime.Utils.callToString(Utils.java:250)
```

The runner printed the exception while still returning a successful Gradle exit,
so stress output must be reviewed for Java exceptions, not only task status.

### Root Cause

The `xException` owner-scope conversion grouped the old static exception class
and formatter metadata into `ExceptionInfo`. That was the right owner model, but
`buildStringValue()` read `info()` from the concrete exception template. Stock
exception handles can be instances of subclasses such as `IllegalState` or
`TypeMismatch`, and those subclass structures do not declare
`formatExceptionString`. Under stress, formatting such an exception could cause
the subclass template to compute an `ExceptionInfo` with a null formatter
method.

The old static cache had one formatter method for the canonical `Exception`
template. The owner-scoped replacement must preserve that semantic shape: one
formatter per owner, not one formatter per concrete exception subclass.

### Replacement

`xException.buildStringValue()` now resolves `ExceptionInfo` through the
exception handle's owning container and its canonical `Exception` template:

```java
NativeTemplates.get(hException.getComposition().getContainer())
        .exception()
        .info()
```

This keeps the cache owner-local and preserves the old canonical formatter
behavior. It does not add per-format lookup allocation; the canonical
`ExceptionInfo` remains a final `Lazy` on the owning template.

### Regression Proof

The mixed parallel stress command above passes after the fix:

```bash
./gradlew :manualTests:runParallelStress \
  -PstressIterations=5 \
  -PstressModules=TestReflection,TestArray,TestServices,TestTuples \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```

## Concurrent Gradle/XTC Output Race During Verification

Status: documented verification caveat and same-JVM stress backlog item.

Observed failures while multiple heavy Gradle/manual-test commands were running
against the same checkout at the same time:

```text
java.lang.NoClassDefFoundError: org/xvm/asm/op/NewV_1
```

or, from asynchronous native `OSStorage` watcher fibers during same-JVM direct
stress:

```text
java.lang.NoClassDefFoundError: org/xvm/runtime/template/reflect/xClass$1
```

and:

```text
Failed to read magic number from .../lib_xunit/build/xtc/main/lib/xunit.xtc
Failed to store cache entry ... Entry 'tree-outputXtcModules/javatools.jar' closed
```

In the `NoClassDefFoundError` case, inspecting the produced
`manualTests/build/xtc/xdk/lib/javatools.jar` showed that `NewV_1.class` existed
in the jar after the build completed. The same check for the same-JVM direct
`xClass$1` symptom showed `xClass$1.class` present in
`javatools/build/libs/javatools-0.4.4-SNAPSHOT.jar`. That points away from a
missing compile artifact and toward output/classloader context problems around
the stress harness and asynchronous service callbacks.

### Root Cause

Several Gradle tasks in this repository share generated outputs under the same
checkout, including XTC module files, jars, and Gradle build-cache entries. If
two independent Gradle/manual-test invocations run concurrently, one process can
observe another process's partially written `.xtc` file, jar, or cache entry.

This is still relevant to the reentrancy work because it can look like stale
runtime state:

- A truncated `.xtc` file can fail with an invalid magic number or EOF.
- A jar observed during update can look like a missing runtime class.
- A failed concurrent build-cache pack can leave confusing secondary errors.

Those symptoms do not prove that two XVM containers shared a bad template,
handle, constant pool, or composition. They prove that verification must isolate
build outputs and async classloader context when the goal is to test runtime
state sharing.

### Replacement

No runtime replacement belongs in this branch for this issue. The correct
verification rule is:

- Do not run multiple heavy Gradle/manual-test invocations concurrently in the
  same checkout when interpreting runtime ownership failures.
- Use a single `manualTests:runParallelStress` invocation to create concurrent
  XVM containers inside one controlled runtime execution.
- Treat asynchronous service-thread `NoClassDefFoundError` output as a separate
  classloader-context backlog item unless it is accompanied by an ownership
  validation failure or a task failure.
- For future same-JVM launcher and Gradle direct-mode stress, isolate build
  outputs, XTC outputs, and Gradle cache locations per outer process, or run the
  entire stress loop inside one Gradle invocation.

### Regression Proof

The mixed runtime stress command that found the `xException` metadata bug passed
when run as a single Gradle invocation after the branch fix. Separate attempts
that overlapped unrelated Gradle/manual-test builds produced file/classloading
symptoms instead of stable runtime failures. The backlog plan in
[plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md) therefore
requires one controlled same-JVM harness with ownership diagnostics rather than
parallel ad hoc Gradle processes in the same checkout.
