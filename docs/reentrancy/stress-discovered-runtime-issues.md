# Stress-Discovered Runtime Issues

This document tracks concrete failures found while using the parallel manual-test
runner to validate the runtime state-safety work. These are adjacent to the
startup-cache work, but they are not all the same class of bug. The useful rule
is to keep each failure honest: document the observed crash, the root cause, the
fix, and the test that proves the specific replacement.

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

and:

```text
Failed to read magic number from .../lib_xunit/build/xtc/main/lib/xunit.xtc
Failed to store cache entry ... Entry 'tree-outputXtcModules/javatools.jar' closed
```

In the `NoClassDefFoundError` case, inspecting the produced
`manualTests/build/xtc/xdk/lib/javatools.jar` showed that `NewV_1.class` existed
in the jar after the build completed. That points away from an XVM runtime owner
leak and toward a workspace-output race during concurrent build/test execution.

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
build outputs when the goal is to test runtime state sharing.

### Replacement

No runtime replacement belongs in this branch for this issue. The correct
verification rule is:

- Do not run multiple heavy Gradle/manual-test invocations concurrently in the
  same checkout when interpreting runtime ownership failures.
- Use a single `manualTests:runParallelStress` invocation to create concurrent
  XVM containers inside one controlled runtime execution.
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
