# Compiler2 Implementation Plan

## Phase 1: Green Node Foundation

### 1.1 SyntaxKind Enum
```java
public enum SyntaxKind {
    // Tokens
    IDENTIFIER,
    INT_LITERAL,
    STRING_LITERAL,
    PLUS, MINUS, STAR, SLASH,
    // ...

    // Expressions
    BINARY_EXPRESSION,
    LITERAL_EXPRESSION,
    NAME_EXPRESSION,
    PARENTHESIZED_EXPRESSION,
    // ...

    // Statements
    BLOCK_STATEMENT,
    IF_STATEMENT,
    RETURN_STATEMENT,
    // ...
}
```

### 1.2 GreenNode Base
```java
public abstract sealed class GreenNode
    permits GreenToken, GreenExpression, GreenStatement {

    private final SyntaxKind kind;
    private final int fullWidth;  // Total text width including trivia

    protected GreenNode(SyntaxKind kind, int fullWidth) {
        this.kind = kind;
        this.fullWidth = fullWidth;
    }

    public abstract int getChildCount();
    public abstract GreenNode getChild(int index);
    public abstract GreenNode withChild(int index, GreenNode child);

    // Factory for interning
    private static final ConcurrentHashMap<GreenNode, WeakReference<GreenNode>> cache
        = new ConcurrentHashMap<>();

    protected static <T extends GreenNode> T intern(T node) {
        // Intern for structural sharing
    }
}
```

### 1.3 GreenToken
```java
public final class GreenToken extends GreenNode {
    private final String text;
    private final Object value;  // For literals

    private GreenToken(SyntaxKind kind, String text, Object value) {
        super(kind, text.length());
        this.text = text;
        this.value = value;
    }

    public static GreenToken create(SyntaxKind kind, String text) {
        return intern(new GreenToken(kind, text, null));
    }

    public static GreenToken literal(long value) {
        return intern(new GreenToken(SyntaxKind.INT_LITERAL,
            String.valueOf(value), value));
    }

    @Override public int getChildCount() { return 0; }
    @Override public GreenNode getChild(int index) { throw ...; }
    @Override public GreenNode withChild(int index, GreenNode child) { return this; }
}
```

### 1.4 GreenExpression Hierarchy
```java
public abstract sealed class GreenExpression extends GreenNode
    permits GreenBinaryExpr, GreenLiteralExpr, GreenNameExpr, ... {

    protected GreenExpression(SyntaxKind kind, int width) {
        super(kind, width);
    }
}

public final class GreenBinaryExpr extends GreenExpression {
    private final GreenExpression left;
    private final GreenToken operator;
    private final GreenExpression right;

    private GreenBinaryExpr(GreenExpression left, GreenToken op, GreenExpression right) {
        super(SyntaxKind.BINARY_EXPRESSION,
              left.getFullWidth() + op.getFullWidth() + right.getFullWidth());
        this.left = left;
        this.operator = op;
        this.right = right;
    }

    public static GreenBinaryExpr create(GreenExpression left, GreenToken op,
                                          GreenExpression right) {
        return intern(new GreenBinaryExpr(left, op, right));
    }

    @Override public int getChildCount() { return 3; }

    @Override public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> left;
            case 1 -> operator;
            case 2 -> right;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> create((GreenExpression) child, operator, right);
            case 1 -> create(left, (GreenToken) child, right);
            case 2 -> create(left, operator, (GreenExpression) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public GreenExpression getLeft() { return left; }
    public GreenToken getOperator() { return operator; }
    public GreenExpression getRight() { return right; }
}

public final class GreenLiteralExpr extends GreenExpression {
    private final GreenToken literal;

    private GreenLiteralExpr(GreenToken literal) {
        super(SyntaxKind.LITERAL_EXPRESSION, literal.getFullWidth());
        this.literal = literal;
    }

    public static GreenLiteralExpr create(GreenToken literal) {
        return intern(new GreenLiteralExpr(literal));
    }

    @Override public int getChildCount() { return 1; }
    @Override public GreenNode getChild(int index) {
        if (index == 0) return literal;
        throw new IndexOutOfBoundsException(index);
    }
    @Override public GreenNode withChild(int index, GreenNode child) {
        if (index == 0) return create((GreenToken) child);
        throw new IndexOutOfBoundsException(index);
    }

    public GreenToken getLiteral() { return literal; }
}

public final class GreenNameExpr extends GreenExpression {
    private final GreenToken identifier;

    // Similar pattern...
}
```

### 1.5 Tests
```java
class GreenNodeTest {
    @Test void binaryExpr_withChild_returnsSameIfUnchanged() {
        var left = GreenLiteralExpr.create(GreenToken.literal(1));
        var op = GreenToken.create(SyntaxKind.PLUS, "+");
        var right = GreenLiteralExpr.create(GreenToken.literal(2));
        var expr = GreenBinaryExpr.create(left, op, right);

        // Same child -> same node (structural sharing)
        assertSame(expr, expr.withChild(0, left));
    }

    @Test void binaryExpr_withChild_createsNewIfChanged() {
        var expr = createBinaryExpr();
        var newLeft = GreenLiteralExpr.create(GreenToken.literal(99));

        var modified = expr.withChild(0, newLeft);

        assertNotSame(expr, modified);
        assertSame(newLeft, ((GreenBinaryExpr) modified).getLeft());
        // Operator and right are shared
        assertSame(expr.getOperator(), ((GreenBinaryExpr) modified).getOperator());
        assertSame(expr.getRight(), ((GreenBinaryExpr) modified).getRight());
    }

    @Test void greenNodes_areInterned() {
        var a = GreenToken.literal(42);
        var b = GreenToken.literal(42);
        assertSame(a, b);  // Same value = same object
    }
}
```

---

## Phase 2: Red Node Facade

### 2.1 SyntaxNode
```java
public class SyntaxNode {
    private final GreenNode green;
    private final SyntaxNode parent;
    private final int position;

    private SyntaxNode(GreenNode green, SyntaxNode parent, int position) {
        this.green = green;
        this.parent = parent;
        this.position = position;
    }

    public static SyntaxNode createRoot(GreenNode green) {
        return new SyntaxNode(green, null, 0);
    }

    public GreenNode getGreen() { return green; }
    public SyntaxNode getParent() { return parent; }
    public int getPosition() { return position; }
    public SyntaxKind getKind() { return green.getKind(); }

    public int getChildCount() { return green.getChildCount(); }

    public SyntaxNode getChild(int index) {
        GreenNode greenChild = green.getChild(index);
        int childPos = position;
        for (int i = 0; i < index; i++) {
            childPos += green.getChild(i).getFullWidth();
        }
        return new SyntaxNode(greenChild, this, childPos);
    }

    public SyntaxNode findNode(int position) {
        // Binary search through children to find node at position
    }

    // Convenience for expressions
    public ExpressionSyntax asExpression() {
        if (green instanceof GreenExpression) {
            return new ExpressionSyntax(this);
        }
        throw new IllegalStateException("Not an expression");
    }
}
```

### 2.2 Typed Wrappers
```java
public class ExpressionSyntax {
    private final SyntaxNode node;

    public ExpressionSyntax(SyntaxNode node) {
        this.node = node;
    }

    public SyntaxNode getNode() { return node; }
    public SyntaxKind getKind() { return node.getKind(); }

    // Type-safe child access for binary expression
    public ExpressionSyntax getLeft() {
        if (getKind() == SyntaxKind.BINARY_EXPRESSION) {
            return node.getChild(0).asExpression();
        }
        throw new IllegalStateException();
    }
}
```

---

## Phase 3: Parser Bridge

### Option A: Emit Green Directly
Modify parser to build green nodes instead of AST nodes.

### Option B: Convert Post-Parse (Simpler Start)
```java
public class AstToGreenConverter {
    public GreenNode convert(AstNode ast) {
        return switch (ast) {
            case BiExpression e -> GreenBinaryExpr.create(
                (GreenExpression) convert(e.getExpr1()),
                convertToken(e.getOperator()),
                (GreenExpression) convert(e.getExpr2())
            );
            case LiteralExpression e -> GreenLiteralExpr.create(
                convertToken(e.getLiteral())
            );
            // ...
        };
    }
}
```

---

## Phase 4: Semantic Model

### 4.1 SemanticModel
```java
public class SemanticModel {
    private final GreenNode root;
    private final ConcurrentHashMap<GreenNode, Symbol> symbols = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GreenNode, TypeConstant> types = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GreenNode, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();

    public SemanticModel(GreenNode root) {
        this.root = root;
    }

    public TypeConstant getType(SyntaxNode expression) {
        return types.computeIfAbsent(expression.getGreen(),
            g -> computeType(expression));
    }

    private TypeConstant computeType(SyntaxNode expr) {
        return switch (expr.getKind()) {
            case LITERAL_EXPRESSION -> computeLiteralType(expr);
            case BINARY_EXPRESSION -> computeBinaryType(expr);
            case NAME_EXPRESSION -> computeNameType(expr);
            default -> throw new UnsupportedOperationException();
        };
    }

    public void invalidate(GreenNode node) {
        symbols.remove(node);
        types.remove(node);
        diagnostics.remove(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            invalidate(node.getChild(i));
        }
    }
}
```

---

## Milestones

### M1: Basic Expressions (Week 1-2)
- [ ] SyntaxKind enum (expressions only)
- [ ] GreenNode, GreenToken
- [ ] GreenBinaryExpr, GreenLiteralExpr, GreenNameExpr
- [ ] SyntaxNode wrapper
- [ ] Unit tests
- [ ] Can represent: `1 + 2`, `x * y + z`

### M2: Parser Integration (Week 3)
- [ ] AstToGreenConverter for expressions
- [ ] Round-trip test: parse -> convert -> toString
- [ ] Position tracking works

### M3: Simple Type Checking (Week 4)
- [ ] SemanticModel with type cache
- [ ] Type Int expressions
- [ ] Diagnostics for type errors

### M4: Statements (Week 5-6)
- [ ] GreenStatement hierarchy
- [ ] Block, Return, If, Assign
- [ ] Method bodies compile

### M5: Incremental (Week 7-8)
- [ ] Token reuse on edit
- [ ] Node reuse on edit
- [ ] Cache invalidation
- [ ] Benchmarks

### M6: Full Module (Week 9-12)
- [ ] Declarations (class, method, property)
- [ ] Full module compilation
- [ ] Output matches original compiler
