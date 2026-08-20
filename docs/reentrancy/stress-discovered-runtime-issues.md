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
