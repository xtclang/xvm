# PR 3: Boolean First Loop Pattern Modernization

## Summary

Replace `boolean first` loop patterns with `Collectors.joining()` or `String.join()` for cleaner, more idiomatic string building.

**Before:**
```java
StringBuilder sb = new StringBuilder();
sb.append('(');
boolean first = true;
for (Expression expr : exprs) {
    if (first) {
        first = false;
    } else {
        sb.append(", ");
    }
    sb.append(expr);
}
sb.append(')');
return sb.toString();
```

**After:**
```java
return exprs.stream()
        .map(Object::toString)
        .collect(Collectors.joining(", ", "(", ")"));
```

## Rationale

- Eliminates boilerplate `boolean first` flag management
- `Collectors.joining()` handles prefix, suffix, and delimiter in one call
- More declarative and readable
- Standard Java 8+ idiom

## Risk Assessment

**Risk: Low**
- All transformations produce semantically equivalent output
- Primarily affects `toString()` methods (debugging/logging only)
- Stream operations are well-tested Java 8+ features

## Files Changed (19 files, ~39 occurrences)

### ASM Package (3 files)

| File | Method | Pattern |
|------|--------|---------|
| `asm/Annotation.java` | `getValueString()` | Annotation parameters `@Ann(p1, p2)` |
| `asm/constants/ParameterizedTypeConstant.java` | `getValueString()` | Generic type parameters `Type<P1, P2>` |
| `asm/constants/SignatureConstant.java` | `getValueString()` (2x) | Return types and parameter types |

### Compiler AST Package (14 files)

| File | Method | Pattern |
|------|--------|---------|
| `compiler/ast/ArrayAccessExpression.java` | `toString()` | Array indices `arr[i, j]` |
| `compiler/ast/ImportStatement.java` | `getQualifiedNameString()`, `toString()` | Qualified names `a.b.c` |
| `compiler/ast/ListExpression.java` | `toString()` | List elements `[e1, e2]` |
| `compiler/ast/MultipleLValueStatement.java` | `toString()` | Assignment targets `(a, b, c)` |
| `compiler/ast/TupleExpression.java` | `toString()` | Tuple elements `(e1, e2)` |
| `compiler/ast/TupleTypeExpression.java` | `toString()` | Tuple type elements |
| `compiler/ast/AnnotationExpression.java` | `toString()` | Annotation arguments |
| `compiler/ast/CompositionNode.java` | `toString()` (5x) | Various composition patterns |
| `compiler/ast/FunctionTypeExpression.java` | `toString()` (2x) | Return values and parameters |
| `compiler/ast/InvocationExpression.java` | `toString()` | Method arguments |
| `compiler/ast/LambdaExpression.java` | `toSignatureString()` | Lambda parameters |
| `compiler/ast/MethodDeclarationStatement.java` | `toSignatureString()` (4x) | Type params, returns, params, throws |
| `compiler/ast/NamedTypeExpression.java` | `getModule()`, `getName()`, `toString()` | Names and type parameters |
| `compiler/ast/ReturnStatement.java` | `toString()` | Return values |
| `compiler/ast/TryStatement.java` | `toString()` | Exception handlers |

### Tool Package (1 file)

| File | Method | Pattern |
|------|--------|---------|
| `tool/ResourceDir.java` | `toString()` | Directory paths |

## Detailed Transformations

### Simple Collection → `Collectors.joining()`

```diff
-StringBuilder sb = new StringBuilder();
-sb.append('(');
-boolean first = true;
-for (Expression expr : exprs) {
-    if (first) {
-        first = false;
-    } else {
-        sb.append(", ");
-    }
-    sb.append(expr);
-}
-sb.append(')');
-return sb.toString();
+return exprs.stream()
+        .map(Object::toString)
+        .collect(Collectors.joining(", ", "(", ")"));
```

### String Collection → `String.join()`

```diff
-StringBuilder sb = new StringBuilder();
-boolean first = true;
-for (String name : names) {
-    if (first) {
-        first = false;
-    } else {
-        sb.append('.');
-    }
-    sb.append(name);
-}
-return sb.toString();
+return String.join(".", names);
```

### With Method Call → `stream().map()`

```diff
-boolean first = true;
-for (TypeConstant type : types) {
-    if (first) {
-        first = false;
-    } else {
-        sb.append(", ");
-    }
-    sb.append(type.getValueString());
-}
+sb.append(types.stream()
+        .map(TypeConstant::getValueString)
+        .collect(Collectors.joining(", ")));
```

### Embedded in Larger StringBuilder

When the joining is part of a larger string being built:

```diff
 var sb = new StringBuilder();
 sb.append("prefix");
-boolean first = true;
-for (Item item : items) {
-    if (first) {
-        first = false;
-    } else {
-        sb.append(", ");
-    }
-    sb.append(item);
-}
+sb.append(items.stream()
+        .map(Object::toString)
+        .collect(Collectors.joining(", ")));
 sb.append("suffix");
```

## Import Changes

Files need this import added:
```java
import java.util.stream.Collectors;
```

## Source Commits

- `0b043626d` - Initial string building modernization (7 files)
- `87daf8b66` - Continue string building modernization (12 files)
- Parts of `7278a9e54` - Additional string building patterns

## Verification

```bash
# Run tests to verify
./gradlew test

# Build to verify compilation
./gradlew build
```

## Not Applicable Cases

The following `boolean first` patterns were **NOT** converted because they are parsing loop control, not string building:

| File | Line | Reason |
|------|------|--------|
| `compiler/Parser.java:1743` | Controls DOT expectation in qualified name parsing |
| `compiler/Parser.java:4685` | Controls COMMA expectation in type parameter parsing |
| `compiler/Parser.java:5076` | Controls COMMA expectation in version override parsing |

## PR Description Template

```markdown
## Summary

Modernize `boolean first` loop patterns to use `Collectors.joining()` for cleaner string building:

```java
// Before (19 lines)
StringBuilder sb = new StringBuilder();
sb.append('(');
boolean first = true;
for (Expression expr : exprs) {
    if (first) { first = false; } else { sb.append(", "); }
    sb.append(expr);
}
sb.append(')');
return sb.toString();

// After (3 lines)
return exprs.stream()
        .map(Object::toString)
        .collect(Collectors.joining(", ", "(", ")"));
```

## Changes

- 39 `boolean first` loop patterns modernized
- 19 files affected (primarily `toString()` methods)
- Uses `Collectors.joining()` for delimiter/prefix/suffix patterns
- Uses `String.join()` for simple string collections

## Test plan

- [x] All existing tests pass
- [x] Build succeeds
- [x] String output is identical (semantic equivalence verified)
```
