# PR 5: Lazy List Instantiation Modernization

## Summary

Replace lazy `List<T> x = null` patterns with upfront allocation `var x = new ArrayList<T>()` and change null checks to `isEmpty()` checks.

**Before:**
```java
List<Item> items = null;
for (...) {
    if (condition) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
    }
}
return items == null ? Collections.emptyList() : items;
```

**After:**
```java
var items = new ArrayList<Item>();
for (...) {
    if (condition) {
        items.add(item);
    }
}
return items.isEmpty() ? List.of() : items;
```

## Rationale

- **Modern GC is fast**: Short-lived small objects are collected efficiently. Allocation cost is negligible.
- **Cognitive overhead**: Every reader must trace through null checks
- **Bug-prone**: Easy to forget a null check
- **Cleaner code**: No conditional instantiation scattered through the method
- Empty ArrayList is tiny (~24 bytes) and often never escapes method scope

## Risk Assessment

**Risk: Low**
- All transformations maintain semantic equivalence
- `isEmpty()` check is equivalent to null check for this pattern
- Modern JVM optimizes away unused allocations in many cases

## Files Changed (21 patterns, 11 files)

| File | Patterns Changed |
|------|------------------|
| `asm/ClassStructure.java` | 3 |
| `asm/Component.java` | 1 |
| `asm/PropertyStructure.java` | 4 |
| `asm/VersionTree.java` | 1 |
| `asm/constants/DynamicFormalConstant.java` | 1 |
| `asm/constants/MethodInfo.java` | 3 |
| `asm/constants/PropertyInfo.java` | 1 |
| `asm/constants/TypeConstant.java` | 4 |
| `compiler/ast/InvocationExpression.java` | 1 |
| `compiler/ast/TypeCompositionStatement.java` | 1 |
| `runtime/template/xConst.java` | 1 |

## Detailed Transformations

### ClassStructure.java - collectAnnotations()

```diff
 public Annotation[] collectAnnotations(boolean fIntoClass) {
     Annotation[] annos = fIntoClass ? m_aAnnoClass : m_aAnnoMixin;
     if (annos == null) {
-        List<Annotation> listAnnos = null;
+        var listAnnos = new ArrayList<Annotation>();

         for (Contribution contrib : getContributionsAsList()) {
             if (contrib.getComposition() == Composition.Annotation) {
                 Annotation anno = contrib.getAnnotation();

                 if (fIntoClass == anno.getAnnotationType().getExplicitClassInto().isIntoClassType()) {
-                    if (listAnnos == null) {
-                        listAnnos = new ArrayList<>();
-                    }
                     listAnnos.add(anno);
                 }
             }
         }
-        annos = listAnnos == null
+        annos = listAnnos.isEmpty()
                 ? Annotation.NO_ANNOTATIONS
                 : listAnnos.toArray(Annotation.NO_ANNOTATIONS);
```

### Component.java - potentialVirtualChildContributors()

```diff
-List<IdentityConstant> list = null;
+var list = new ArrayList<IdentityConstant>();

 for (...) {
     if (type.isExplicitClassIdentity(true)) {
-        if (list == null) {
-            list = new ArrayList<>();
-        }
         list.add(type.getSingleUnderlyingClass(true));
     }
 }

-return list == null
+return list.isEmpty()
         ? Collections.emptyIterator()
         : list.iterator();
```

### TypeConstant.java - containsUnresolved()

```diff
-List<TypeConstant> listParams = null;
+var listParams = new ArrayList<TypeConstant>();
 for (int i = 0, c = getParamsCount(); i < c; ++i) {
     TypeConstant constParam = getParamType(i);
     if (constParam.containsUnresolved()) {
         constParam = constParam.resolveUnresolved(pool);
-        if (listParams == null) {
-            listParams = new ArrayList<>(c);
-            for (int iPrev = 0; iPrev < i; ++iPrev) {
-                listParams.add(getParamType(iPrev));
-            }
+        if (listParams.isEmpty()) {
+            // first modified param - copy all previous params
+            for (int iPrev = 0; iPrev < i; ++iPrev) { listParams.add(getParamType(iPrev)); }
         }
-    }
-    if (listParams != null) {
         listParams.add(constParam);
+    } else if (!listParams.isEmpty()) {
+        listParams.add(constParam);
     }
 }
-return listParams == null ? this : cloneSingle(pool, listParams);
+return listParams.isEmpty() ? this : cloneSingle(pool, listParams);
```

### xConst.java - makeHandle()

```diff
-List<String> listNames = null;
+var listNames = new ArrayList<String>();
 for (Map.Entry<Object, ObjectHandle> entry : mapFields.entrySet()) {
     if (entry.getValue() == null) {
-        if (listNames == null) {
-            listNames = new ArrayList<>();
-        }
         listNames.add(entry.getKey().toString());
     }
 }
-if (listNames != null) {
+if (!listNames.isEmpty()) {
     return frame.raiseException(xException.unassignedFields(frame, ...));
 }
```

## Cases NOT Converted

| File | Reason |
|------|--------|
| `compiler/Parser.java` | `null` indicates "element not present" (API semantic) |
| `asm/MethodStructure.java` | Recursive helper passes list through multiple calls |
| Various `startList()` patterns | Copy-on-write from specific index (different pattern) |
| Various `ensureList()` patterns | Shared list management across methods |

## Import Changes

Files may need this import:
```java
import java.util.ArrayList;
```

## Source Commits

- `c53d6ecde` - Main lazy list modernization (10 files)
- `1e1561967` - xConst lazy list modernization (1 file)

## Verification

```bash
# Run tests to verify
./gradlew test

# Build to verify compilation
./gradlew build
```

## PR Description Template

```markdown
## Summary

Replace lazy `List<T> x = null` patterns with upfront allocation:

```java
// Before (scattered null checks)
List<Item> items = null;
for (...) {
    if (items == null) { items = new ArrayList<>(); }
    items.add(item);
}
return items == null ? emptyList() : items;

// After (clean, no null checks)
var items = new ArrayList<Item>();
for (...) {
    items.add(item);
}
return items.isEmpty() ? List.of() : items;
```

## Rationale

- Modern GC efficiently handles small short-lived objects
- Removes cognitive overhead of tracking null state
- Eliminates potential for null check bugs
- Empty ArrayList is tiny (~24 bytes)

## Changes

- 21 lazy list patterns modernized
- 11 files affected
- Null checks → isEmpty() checks

## Test plan

- [x] All existing tests pass
- [x] Build succeeds
- [x] Same results produced (empty vs non-empty lists)
```
