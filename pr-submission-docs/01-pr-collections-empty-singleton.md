# PR 1: Collections.emptyList/singletonList → List.of()

## Summary

Replace `Collections.emptyList()` and `Collections.singletonList(x)` with `List.of()` equivalents.

**This PR does ONE thing:** Replace these two specific Collections factory methods.

## Pattern

```diff
-Collections.emptyList()
+List.of()

-Collections.singletonList(item)
+List.of(item)
```

## Rationale

- `List.of()` is more concise and idiomatic in modern Java (9+)
- Both return immutable lists with identical semantics
- Clearer intent (no "Collections" prefix needed)

## Risk Assessment

**Risk: Very Low** - Semantic-equivalent transformation

## Source in Branch

These changes come from commit `7278a9e54` which is **MIXED** (also contains Boolean First changes).

**To extract:** Use grep to find only `Collections.emptyList` and `Collections.singletonList` lines.

```bash
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B3 -A3 "Collections\.emptyList\|Collections\.singletonList"
```

## Files Changed

### `Collections.emptyList()` → `List.of()` (49 occurrences, 22 files)

| File | Lines |
|------|-------|
| `asm/ClassStructure.java` | 561, 916, 2616, 2639, 2651, 2782, 2791 |
| `asm/Component.java` | 466, 511 |
| `asm/ConstantPool.java` | 2299, 2318, 2380 |
| `asm/constants/TypeConstant.java` | 814, 1004, 1035, 1079, 1203, 2133, 2185, 6066, 6072 |
| `asm/constants/ParameterizedTypeConstant.java` | 517 |
| `asm/constants/AbstractDependantChildTypeConstant.java` | 136 |
| `asm/constants/TypeCollector.java` | 68, 82 |
| `asm/constants/PropertyClassTypeConstant.java` | 140, 228 |
| `asm/constants/RelationalTypeConstant.java` | 212 |
| `compiler/Parser.java` | 1584, 1663, 2063, 2130, 2262, 3623 |
| `compiler/ast/ListExpression.java` | 120, 390 |
| `compiler/ast/AnonInnerClass.java` | 122, 195 |
| `compiler/ast/ForStatement.java` | 109, 166 |
| `compiler/ast/CompositionNode.java` | 413 |
| `compiler/ast/AnnotationExpression.java` | 93 |
| `compiler/ast/ConditionalStatement.java` | 32 |
| `compiler/ast/AssertStatement.java` | 58 |
| `compiler/ast/ImportStatement.java` | 168 |
| `compiler/ast/NameExpression.java` | 1476 |
| `runtime/DebugConsole.java` | 908 |
| `runtime/template/_native/mgmt/xContainerLinker.java` | 182 |

### `Collections.singletonList(x)` → `List.of(x)` (22 occurrences, 10 files)

| File | Lines |
|------|-------|
| `compiler/Parser.java` | 1523, 1554, 1664, 2064, 2135, 2198, 2263, 2307, 2356, 2374, 3624 |
| `compiler/ast/ForEachStatement.java` | 239, 407 |
| `compiler/ast/AstNode.java` | 1029 |
| `compiler/ast/AnnotationExpression.java` | 85 |
| `compiler/ast/NamedTypeExpression.java` | 145 |
| `compiler/ast/MethodDeclarationStatement.java` | 553 |
| `compiler/ast/TypeCompositionStatement.java` | 1159, 1318 |
| `compiler/ast/StageMgr.java` | 41 |
| `asm/ConstantPool.java` | 2411 |
| `runtime/ObjectHandle.java` | 148 |

## Diff Examples

### emptyList

```diff
 public List<Map.Entry<StringConstant, TypeConstant>> getTypeParamsAsList() {
     ListMap<StringConstant, TypeConstant> mapThis = m_mapParams;
     return mapThis == null
-            ? Collections.emptyList()
+            ? List.of()
             : mapThis.asList();
 }
```

### singletonList

```diff
-return Collections.singletonList(exprTarget);
+return List.of(exprTarget);
```

## Import Changes

```diff
-import java.util.Collections;
+import java.util.List;  // if not already present
```

Remove `Collections` import only if no other usage remains in the file.

## Creating This PR

```bash
git checkout master
git checkout -b modernize/collections-empty-singleton

# For each file listed above:
# 1. Find Collections.emptyList() → replace with List.of()
# 2. Find Collections.singletonList(X) → replace with List.of(X)
# 3. Update imports

./gradlew build test
```

## Verification

```bash
./gradlew clean
./gradlew build
./gradlew test
```

## PR Description Template

```markdown
## Summary

Replace `Collections.emptyList()` and `Collections.singletonList(x)` with `List.of()`:

```java
// Before
return Collections.emptyList();
return Collections.singletonList(item);

// After
return List.of();
return List.of(item);
```

Both return immutable lists - this is a semantic-equivalent transformation.

## Changes

- 49 `Collections.emptyList()` → `List.of()` (22 files)
- 22 `Collections.singletonList()` → `List.of()` (10 files)

## Test plan

- [x] All existing tests pass
- [x] Build succeeds
```
