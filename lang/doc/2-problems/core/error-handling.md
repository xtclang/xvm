# Error Handling Chaos

## The Problem

The XVM compiler has no coherent error handling strategy. Instead, it uses a chaotic mix of:
- `null` returns to indicate failure (357 occurrences)
- `ErrorListener` parameters passed in many places, but far from everywhere (687 occurrences)
- `RuntimeException` throws (1,276 occurrences)
- `ErrorListener.BLACKHOLE` to silently discard errors (25+ occurrences)
- Exceptions swallowed with empty catch blocks (15+ occurrences)

**This makes the code impossible to reason about.** For any method call, you cannot know:
- Will it return null on error?
- Will it throw an exception?
- Will it log to an ErrorListener?
- Will it silently swallow errors?

## The BLACKHOLE Anti-Pattern

The `ErrorListener.BLACKHOLE` is a special error listener that **discards all errors**:

```java
// ErrorListener.java line 433
ErrorListener BLACKHOLE = new BlackholeErrorListener();

// The BLACKHOLE implementation just ignores everything
private static class BlackholeErrorListener implements ErrorListener {
    @Override
    public boolean log(ErrorInfo err) {
        return false;  // Error? What error? Never heard of it.
    }
}
```

### Real Usage in Codebase

**TypeConstant.java** - Silently discards errors during type resolution:
```java
// TypeConstant.java line 2086
fComplete && !errs.hasSeriousErrors() ? errs : ErrorListener.BLACKHOLE);

// TypeConstant.java line 2289 - when things go wrong, just ignore errors
errs = ErrorListener.BLACKHOLE;

// TypeConstant.java line 3014 - more error hiding
errs = ErrorListener.BLACKHOLE;
```

**Parser.java** - Disables error reporting during speculative parsing:
```java
// Parser.java line 143
m_errorListener = ErrorListener.BLACKHOLE;
```

**Compiler.java** - Disables errors at some point:
```java
// Compiler.java line 132
m_structFile.setErrorListener(ErrorListener.BLACKHOLE);
```

**NameExpression.java** - Type inference errors silently ignored:
```java
// NameExpression.java line 513
: getImplicitType(ctx, null, ErrorListener.BLACKHOLE);

// NameExpression.java line 542
errs = ErrorListener.BLACKHOLE;
```

### Why BLACKHOLE Is Catastrophic

1. **Debugging nightmare**: When something goes wrong, there's no error message
2. **Silent corruption**: Operations fail but continue with invalid state
3. **User confusion**: Compilation fails with no explanation
4. **No error recovery**: Can't show user what went wrong
5. **LSP impossible**: LSP needs all errors to show diagnostics

## The ErrorListener Parameter Soup

Every method that might produce errors takes an `ErrorListener` parameter:

```java
// Different signatures from across the codebase
Expression validate(Context ctx, TypeConstant type, ErrorListener errs);
boolean validateCondition(ErrorListener errs);
void resolveNames(StageMgr mgr, ErrorListener errs);
TypeConstant ensureTypeInfo(ErrorListener errs);
TypeInfo buildTypeInfo(ErrorListener errs);
void validate(Validator validator, ErrorListener errs);
Constant resolve(ConstantPool pool, ErrorListener errs);
```

### Problems with ErrorListener Passing

**Problem 1: Easy to forget to pass it**
```java
// Oops, used BLACKHOLE because I forgot to pass the real one
TypeInfo info = type.ensureTypeInfo(ErrorListener.BLACKHOLE);
```

**Problem 2: Easy to use wrong one**
```java
void outerMethod(ErrorListener errs) {
    // Create local error list for "temporary" errors
    ErrorListener localErrs = new ErrorList(10);

    // Now which one should I use?
    inner(localErrs);  // Errors go to local list, never reported!
}
```

**Problem 3: Methods create their own**
```java
// ErrorList created locally, never merged with parent
ErrorListener errs = new ErrorList(1);
doSomething(errs);
// errs has errors, but caller doesn't know!
```

**Problem 4: Threading/lifecycle confusion**
```java
// Who owns this ErrorListener?
// When is it valid?
// Is it thread-safe?
private ErrorListener m_errorListener;
```

## The Null Return Convention

Methods return `null` to indicate "failure," but:
- No documentation of what null means
- No way to get error details when null is returned
- Null can also mean "no value" legitimately

```java
// Expression.java - null return on error
public TypeConstant getType() {
    checkValidated();

    if (m_oType instanceof TypeConstant type) {
        return type;
    }

    TypeConstant[] atype = (TypeConstant[]) m_oType;
    return atype.length == 0 ? null : atype[0];  // null = no type? or error?
}

// Caller must guess
TypeConstant type = expr.getType();
if (type == null) {
    // Is this an error? Or just a void expression?
    // Who knows!
}
```

### The 357 Null Returns

```java
// Sampling of null returns from compiler/ast package
return null;  // Error during validation
return null;  // Type not found
return null;  // Resolution failed
return null;  // No match found
return null;  // Invalid state
return null;  // Already reported error
return null;  // Speculative parse failed
```

**Every single one loses information.** The caller doesn't know WHY it's null.

## Exception Swallowing

```java
// Actual pattern found in multiple places
try {
    doSomethingRisky();
} catch (RuntimeException ignore) {
    // Swallowed! Bug? IO error? Out of memory? Nobody knows!
}

// More swallowing
try {
    parseExpression();
} catch (CompilerException e) {
    // "It's fine, probably"
}
```

### The Danger

```java
// Real scenario - the codebase catches Throwable in places
try {
    typeConstant.ensureTypeInfo(errs);
} catch (Throwable e) {
    // Maybe it's a normal "type not found"?
    // Or maybe it's:
    // - NullPointerException from a bug
    // - OutOfMemoryError (should NEVER be caught!)
    // - ConcurrentModificationException from threading bug
    // - StackOverflowError (should NEVER be caught!)
    // We'll never know!
}
```

Catching `Throwable` or `Error` is almost always wrong - it swallows JVM-level failures that the program cannot recover from.

## What LSP Needs

An LSP server needs:

1. **All diagnostics collected**: Every error, warning, and hint
2. **Source locations**: Exact positions for squiggly underlines
3. **No silent failures**: Every problem must be reported
4. **Graceful degradation**: Partial results even with errors
5. **Never abort**: Keep going to find all issues

The current system provides **none of this**.

## The Correct Approach: Result Types

### Define Result Types

```java
// Sealed interface for type-safe results
public sealed interface ValidationResult permits ValidationSuccess, ValidationFailure {
    boolean isSuccess();
    List<Diagnostic> getDiagnostics();
}

public record ValidationSuccess(
    Expression validatedExpr,
    List<Diagnostic> warnings
) implements ValidationResult {
    @Override public boolean isSuccess() { return true; }
    @Override public List<Diagnostic> getDiagnostics() { return warnings; }
}

public record ValidationFailure(
    List<Diagnostic> errors
) implements ValidationResult {
    @Override public boolean isSuccess() { return false; }
    @Override public List<Diagnostic> getDiagnostics() { return errors; }
}
```

### Use Result Types Instead of ErrorListener

```java
// Before: ErrorListener parameter, null return
public Expression validate(Context ctx, TypeConstant type, ErrorListener errs) {
    if (problem) {
        errs.log(new ErrorInfo(...));
        return null;  // What does this mean?!
    }
    return this;
}

// After: Result type, no parameter
public ValidationResult validate(Context ctx, TypeConstant type) {
    if (problem) {
        return new ValidationFailure(List.of(
            new Diagnostic(getRange(), Severity.ERROR, "Problem description")
        ));
    }
    return new ValidationSuccess(this, List.of());
}
```

### Collect All Diagnostics

```java
// DiagnosticCollector - never aborts, collects everything
public class DiagnosticCollector {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void error(Range range, String message) {
        diagnostics.add(new Diagnostic(range, Severity.ERROR, message));
    }

    public void warning(Range range, String message) {
        diagnostics.add(new Diagnostic(range, Severity.WARNING, message));
    }

    public List<Diagnostic> getAll() {
        return List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
```

### No BLACKHOLE Equivalent

```java
// Instead of ErrorListener.BLACKHOLE, use a real collector
DiagnosticCollector diagnostics = new DiagnosticCollector();

// Speculative parsing
ValidationResult result = speculativelyValidate(expr, diagnostics);
if (!result.isSuccess()) {
    // We still have the diagnostics! Can use for error recovery hints
    List<Diagnostic> errors = diagnostics.getAll();
}
```

## Migration Path

### Phase 1: Create Diagnostic Infrastructure

```java
// Add new classes
public record Diagnostic(Range range, Severity severity, String code, String message) {}
public class DiagnosticCollector { ... }
public sealed interface ValidationResult { ... }
```

### Phase 2: Add Result-Returning Methods Alongside Old Ones

```java
// Keep old method for compatibility
public Expression validate(Context ctx, TypeConstant type, ErrorListener errs) {
    ValidationResult result = validateWithResult(ctx, type);
    // Bridge to old API
}

// New method returns result
public ValidationResult validateWithResult(Context ctx, TypeConstant type) {
    // Proper implementation
}
```

### Phase 3: Migrate Callers

```java
// Before
Expression validated = expr.validate(ctx, type, errs);
if (validated == null) {
    // Error handling with no information
}

// After
ValidationResult result = expr.validateWithResult(ctx, type);
if (!result.isSuccess()) {
    // Full diagnostic information available
    result.getDiagnostics().forEach(this::reportDiagnostic);
}
```

### Phase 4: Remove BLACKHOLE Usage

For every `ErrorListener.BLACKHOLE`, determine:
1. Why are errors being discarded?
2. Should they be collected instead?
3. Is this speculative parsing (collect but don't report)?
4. Is this a bug (should report)?

### Phase 5: Remove Old ErrorListener API

Once all callers migrated, delete:
- `ErrorListener` interface
- `ErrorList` class
- `ErrorInfo` class
- All ErrorListener parameters

## Summary

| Current State | Required for LSP |
|---------------|------------------|
| `null` returns | Result types |
| `ErrorListener` parameters | `DiagnosticCollector` |
| `BLACKHOLE` discards errors | Always collect |
| Exception swallowing | Proper error handling |
| Side-effect logging | Return values |
| Abort on error | Continue and collect |

The current error handling is:
- Inconsistent
- Information-destroying (BLACKHOLE and unexplained null:s for everything)
- Side-effect based
- Impossible to reason about
- Incompatible with LSP

**There is no way to build a proper LSP server on this foundation.** The error handling must be rewritten to use result types and always-collecting diagnostics.
