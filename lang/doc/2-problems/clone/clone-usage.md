# How Clone Is Actually Used in XVM

## Overview

Understanding **why** clone exists in the codebase helps explain why it's problematic and what should replace it. There are three main use cases, and each one has a better solution.

## Use Case 1: Parser Backtracking

### What the Code Does

The parser clones tokens to save and restore its position during speculative parsing:

```java
// Parser.java line 5357-5359
mark.token     = m_token        == null ? null : m_token.clone();
mark.putBack   = m_tokenPutBack == null ? null : m_tokenPutBack.clone();
mark.lastMatch = m_tokenPrev    == null ? null : m_tokenPrev.clone();
```

### Why This Exists

The parser needs to try multiple parsing strategies. For example, when seeing an identifier, it might be:
- A variable reference
- A type name
- A method call
- A constructor call

The parser tries one interpretation, and if it fails, restores to the saved position and tries another.

### Why This Design is Bad

Tokens should be immutable value objects. You shouldn't need to clone them because they should never change. The fact that tokens are cloned suggests they're mutable, which creates several problems:

1. **Memory overhead**: Creating new Token objects for every save point
2. **Hidden mutation**: If tokens were immutable, there'd be no need to copy them
3. **Error-prone**: Easy to forget to clone, leading to shared state bugs

### What Should Be Done Instead

Tokens should be immutable records:

```java
// Tokens are immutable - no cloning needed
public record Token(Id id, long startPos, long endPos, Object value) {}

// Parser state is an index into a token list
public record ParserState(int tokenIndex, List<Token> tokens) {
    public Token current() { return tokens.get(tokenIndex); }
    public ParserState advance() { return new ParserState(tokenIndex + 1, tokens); }
    public ParserState restoreTo(ParserState saved) { return saved; }
}
```

With immutable tokens, "restoring" state is just using the old `ParserState` object - no cloning required.

## Use Case 2: AST Cloning for Multiple Validation Passes

### What the Code Does

Loop constructs clone their body blocks before validation:

```java
// ForStatement.java line 327-333
for (AstNode cond : condOrig) {
    conds.add(cond.clone());
}
for (Statement stmt : updateOrig) {
    update.add((Statement) stmt.clone());
}
block = (StatementBlock) blockOrig.clone();

// LambdaExpression.java line 708
StatementBlock blockTemp = (StatementBlock) body.clone();
// ... validate blockTemp ...

// WhileStatement.java line 235-237
for (AstNode cond : condsOrig) {
    conds.add(cond.clone());
}
block = (StatementBlock) blockOrig.clone();
```

### Why This Exists

The validator **mutates** AST nodes during validation:
- Adds resolved type information
- Records symbol references
- Marks nodes as validated or errored

If validation fails and needs to retry with different assumptions (e.g., different type inference), a fresh copy is needed because the original was corrupted by the failed validation.

### Why This Design is Bad

This is a fundamental architectural mistake. AST nodes should represent the **syntax** of the code - what the user wrote. Type information and validation state belong in a separate **semantic model**.

The current design:
1. **Destroys reusability**: Can't validate the same AST with different contexts
2. **Requires expensive cloning**: Deep-copying entire subtrees
3. **Creates race conditions**: Can't have concurrent validations
4. **Violates separation of concerns**: Syntax and semantics are mixed

### What Should Be Done Instead

Separate syntax from semantics:

```java
// Immutable AST - represents what user wrote
public record ForStatement(
    List<Expression> initializers,
    Expression condition,
    List<Statement> updates,
    StatementBlock body,
    SourceRange range
) implements Statement {}

// Separate semantic model - stores validation results
public class SemanticModel {
    private final Map<AstNode, TypeConstant> resolvedTypes;
    private final Map<AstNode, SymbolRef> symbolRefs;
    private final List<Diagnostic> diagnostics;

    public Optional<TypeConstant> typeOf(Expression expr) {
        return Optional.ofNullable(resolvedTypes.get(expr));
    }
}

// Validator produces semantic model, doesn't mutate AST
public SemanticModel validate(Statement stmt, TypeContext context) {
    return new Validator(context).visit(stmt);
}
```

With this design:
- The same AST can be validated multiple times with different contexts
- No cloning needed
- Multiple validations can run concurrently
- Clear separation between syntax and semantics

## Use Case 3: Array Defensive Copying

### What the Code Does

Arrays are cloned before modification to avoid corrupting shared state:

```java
// InvocationExpression.java line 785
atypeParams = atypeParams.clone();  // Don't mess up the actual types

// ConvertExpression.java line 78
TypeConstant[] aType = expr.getTypes().clone();
aType[0] = type;

// ReturnStatement.java line 136
aRetTypes = aRetTypes.clone();
```

### Why This Exists

Arrays in Java are mutable. When a method returns an array, it might return a reference to its internal state. If the caller modifies that array, it corrupts the original object's state.

### Why This Design is Bad

This is defensive programming against the codebase's own design flaws:

1. **Error-prone**: Easy to forget to clone, leading to corruption
2. **Performance overhead**: Unnecessary copies when not modified
3. **Unclear ownership**: Who owns the array? Can I modify it?
4. **Hidden bugs**: Some code paths clone, some don't - inconsistent

### What Should Be Done Instead

Use immutable collections:

```java
// Return immutable list - caller cannot modify
public List<TypeConstant> getTypes() {
    return List.copyOf(types);  // Returns unmodifiable view
}

// Or store as immutable internally
private final List<TypeConstant> types;

public MethodDeclaration(List<TypeConstant> types) {
    this.types = List.copyOf(types);  // Defensive copy at construction
}

public List<TypeConstant> getTypes() {
    return types;  // Safe to return - it's immutable
}
```

With immutable collections:
- No defensive copying needed
- Clear ownership semantics
- Thread-safe by design
- Compiler helps catch modification attempts

## Summary of Clone Usage

| Use Case | Why It Exists | Why It's Bad | Better Solution |
|----------|--------------|--------------|-----------------|
| Parser backtracking | Save/restore parser state | Tokens should be immutable | Immutable tokens + index-based state |
| AST validation retry | Validator mutates AST | Syntax/semantics mixed | Separate SemanticModel |
| Array defensive copy | Prevent shared mutation | Error-prone, unclear ownership | Immutable collections |

## The Common Thread

All three use cases exist because of **unnecessary mutability**:

1. Tokens are mutable when they should be immutable
2. AST nodes are mutated during validation when results should be separate
3. Arrays are used where immutable collections would be safer

The fix is not "better cloning" - it's **eliminating the need for cloning** by using immutable data structures and separating mutable state from immutable syntax.

## Impact on LSP

For an LSP implementation, this matters because:

1. **Parser backtracking** - Not a direct LSP concern, but immutable tokens enable incremental lexing
2. **AST cloning for validation** - Major blocker. LSP needs to query partially-validated ASTs while validation continues in the background
3. **Array defensive copying** - Creates unnecessary allocations during the hot path of completion/hover

The [adapter layer approach](../../3-lsp-solution/adapter-layer-design.md) works around these issues by extracting data into clean structures after the compiler does its work.
But it is not a most ideal solution. Research will continue to find parallel paths that will get us towards the goal, and that have 
mutually useful benefits to each other. It is not lost on the author that the irony here is that the best possible end goal would be
a parallel XTC compiler implementation, that supports full API granularity and partial, stateless execution.


