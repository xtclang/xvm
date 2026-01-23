# BUG FIX: xRef.java Arrays.asList() doesn't support removeIf()

## Bug Location

**File:** `javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:223`

## Bug Description

```java
List<Annotation> listAnno = Arrays.asList(typeOrig.getAnnotations());
listAnno.removeIf(anno -> !anno.getAnnotationType().isShared(pool));
```

`Arrays.asList()` returns a **fixed-size list** that does not support structural modification. Calling `removeIf()` will throw `UnsupportedOperationException` at runtime.

## Impact

- **Severity:** High (runtime crash when triggered)
- **Trigger condition:** A type has annotations where at least one is NOT shared
- **Current exposure:** This code path is in production runtime code

## Fix

```diff
-List<Annotation> listAnno = Arrays.asList(typeOrig.getAnnotations());
+List<Annotation> listAnno = new ArrayList<>(List.of(typeOrig.getAnnotations()));
 listAnno.removeIf(anno -> !anno.getAnnotationType().isShared(pool));
```

## Why This Works

- `List.of()` creates an immutable list (same as `Arrays.asList()` for reads)
- `new ArrayList<>(...)` creates a mutable copy that supports `removeIf()`

## PR Content

This PR should contain ONLY:
1. The one-line fix in `xRef.java`
2. Import change: remove `java.util.Arrays`, add `java.util.ArrayList`

## Commit Message

```
Fix Arrays.asList() bug in xRef.java

Arrays.asList() returns a fixed-size list that throws
UnsupportedOperationException when removeIf() is called.
Changed to new ArrayList<>(List.of(...)) to create a mutable copy.

This would crash at runtime when a type has annotations where
at least one is not shared.
```

## Note

This bug was discovered during the Arrays.asList() → List.of() modernization work. It was **already fixed** on branch `lagergren/sb-simplify` in commit `868a7ca15`.

If you want a separate bug-fix PR, cherry-pick only the xRef.java change from that commit, or apply manually.
