# BUG FIX: MarkAndSweepGcSpace arraycopy copies to itself

## Status

**FIXED** on branch `lagergren/sb-simplify` - included in the modernization work.

Both bugs fixed:
1. `System.arraycopy` copying to itself → `Arrays.copyOf()`
2. Off-by-one condition `>` → `>=`

## Bug Location

**File:** `javatools/src/main/java/org/xvm/runtime/gc/MarkAndSweepGcSpace.java:291`

## Bug Description

```java
int[] anWeaksNew = new int[anNotify.length * 2];
System.arraycopy(anWeaksNew, 0, anWeaksNew, 0, anNotify.length);  // BUG!
anNotify = anWeaksNew;
```

The code creates a new array `anWeaksNew` and then copies **from itself to itself**, which is a no-op. The source should be `anNotify`.

## Impact

- **Severity:** Medium (data loss when triggered)
- **Trigger condition:** More than 8 weak references with notifiers are cleared in a single GC cycle
- **Current exposure:** LOW - The GC code is **not currently used** in the XVM runtime

### Is This Code Used?

**NO** - The `org.xvm.runtime.gc` package is experimental/future work:
- No imports of the gc package exist outside the gc package itself
- No production code instantiates `MarkAndSweepGcSpace`
- Only unit tests exercise this code

### Why Fix It?

Even though unused now, the bug should be fixed because:
1. It's clearly incorrect
2. Tests don't cover this path (only 1 weak ref in tests)
3. When/if the GC is integrated, this would cause silent data corruption

## Fix

```diff
 int[] anWeaksNew = new int[anNotify.length * 2];
-System.arraycopy(anWeaksNew, 0, anWeaksNew, 0, anNotify.length);
+System.arraycopy(anNotify, 0, anWeaksNew, 0, anNotify.length);
 anNotify = anWeaksNew;
```

Or modernized:
```diff
-int[] anWeaksNew = new int[anNotify.length * 2];
-System.arraycopy(anWeaksNew, 0, anWeaksNew, 0, anNotify.length);
-anNotify = anWeaksNew;
+anNotify = Arrays.copyOf(anNotify, anNotify.length * 2);
```

## Additional Recommended Fix

There's also an off-by-one error in the condition:

```java
} else if (nNotifyTop > anNotify.length) {  // Should be >=
```

When `nNotifyTop == anNotify.length`, the array is full and the next write at `anNotify[nNotifyTop]` would be out of bounds.

## Bug Origin

Introduced in commit `9d4eba8c4` ("Weak-ref handling cleanup.")

## Test Gap

The existing test `shouldClearWeakRefsToUnreachables()` only creates 1 weak reference. A test with >8 weak refs needing notification would catch this.

## PR Content

This PR should contain ONLY:
1. The one-line fix to `System.arraycopy` (or `Arrays.copyOf`)
2. Optionally: the off-by-one condition fix
3. Optionally: a test case that exercises the resize path

## Commit Message

```
Fix System.arraycopy bug in MarkAndSweepGcSpace

The weak-ref notification array resize was copying the newly allocated
array to itself (a no-op) instead of copying from the old array.
This would cause data loss when more than 8 weak refs are cleared
in a single GC cycle.

Note: The GC code is not currently used in the runtime, but this
fixes the bug for when it is integrated.
```
