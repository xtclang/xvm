# PR 2: Arrays.asList() → List.of()

## Summary

Replace `Arrays.asList(a, b, c)` with `List.of(a, b, c)`.

**This PR does ONE thing:** Replace `Arrays.asList()` calls with `List.of()`.

## Pattern

```diff
-Arrays.asList(item1, item2, item3)
+List.of(item1, item2, item3)
```

## Rationale

- `List.of()` is more concise and idiomatic in modern Java (9+)
- `List.of()` is explicitly immutable (throws on modification)
- `Arrays.asList()` allows `.set()` but not structural modification - confusing behavior
- Consistent with `Set.of()` and `Map.of()` patterns

## Risk Assessment

**Risk: Low**
- Must verify the list is not modified after creation
- All instances in XVM codebase are used as immutable

## Source in Branch

These changes come from:
- Commit `868a7ca15` - **PURE** (can cherry-pick)
- Commit `e0dca6acc` - **MIXED** (also contains System.arraycopy changes)

**To extract mixed commit:** Use grep to find only `Arrays.asList` → `List.of` changes.

```bash
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B5 -A5 "Arrays\.asList"
```

## Files Changed (19 occurrences, 12 files)

| File | Lines |
|------|-------|
| `asm/MethodStructure.java` | 407, 409, 1178, 1192 |
| `asm/constants/SignatureConstant.java` | 170, 193 |
| `asm/ClassStructure.java` | 1419 |
| `asm/constants/PropertyInfo.java` | 137 |
| `asm/constants/ParameterizedTypeConstant.java` | 263 |
| `asm/constants/TypeConstant.java` | 3234 |
| `compiler/Parser.java` | 2410, 2468 |
| `compiler/ast/AstNode.java` | 1010, 1023 |
| `compiler/ast/TupleExpression.java` | 189 |
| `compiler/ast/ReturnStatement.java` | 71 |
| `runtime/template/reflect/xRef.java` | 341-344 |
| `tool/LauncherOptions.java` | 52, 53 |

### Plugin Module (from commit e0dca6acc)

| File | Lines |
|------|-------|
| `plugin/.../DefaultXtcRuntimeExtension.java` | Mixed with arraycopy |
| `plugin/.../ForkedStrategy.java` | Mixed with arraycopy |

## Diff Examples

### Simple replacement

```diff
-List<String> dirs = Arrays.asList(
+List<String> dirs = List.of(
     "resources",
     "lib"
 );
```

### Inline replacement

```diff
-return Arrays.asList(typeParam1, typeParam2);
+return List.of(typeParam1, typeParam2);
```

### xRef.java - EXCLUDE FROM THIS PR

The xRef.java change is a **bug fix** (Arrays.asList + removeIf = crash) and should be a **separate PR**.

See: `BUG-02-xref-arrays-aslist.md`

## Import Changes

```diff
-import java.util.Arrays;
+import java.util.List;  // if not already present
```

Remove `Arrays` import only if no other usage remains in the file.

## Creating This PR

```bash
git checkout master
git checkout -b modernize/arrays-aslist

# Cherry-pick the pure commit
git cherry-pick 868a7ca15

# Then manually extract Arrays.asList changes from e0dca6acc
# (skip the System.arraycopy changes from that commit)

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

Replace `Arrays.asList()` with `List.of()`:

```java
// Before
return Arrays.asList(a, b, c);

// After
return List.of(a, b, c);
```

`List.of()` is explicitly immutable and more idiomatic in modern Java.

## Changes

- 19 `Arrays.asList()` → `List.of()` replacements (12 files)

## Test plan

- [x] All existing tests pass
- [x] Build succeeds
- [x] Verified lists are not modified after creation
```
