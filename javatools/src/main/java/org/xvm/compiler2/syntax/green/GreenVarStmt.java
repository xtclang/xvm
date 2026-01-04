package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A variable declaration statement: Type name [= value];
 */
public final class GreenVarStmt extends GreenStatement {

    private final GreenType type;
    private final GreenToken name;
    private final GreenToken assign; // may be null
    private final GreenExpression initializer; // may be null
    private final GreenToken semicolon;

    private GreenVarStmt(GreenType type, GreenToken name, GreenToken assign,
                        GreenExpression initializer, GreenToken semicolon) {
        super(SyntaxKind.VARIABLE_STATEMENT, computeWidth(type, name, assign, initializer, semicolon));
        this.type = type;
        this.name = name;
        this.assign = assign;
        this.initializer = initializer;
        this.semicolon = semicolon;
    }

    private static int computeWidth(GreenType type, GreenToken name, GreenToken assign,
                                    GreenExpression init, GreenToken semi) {
        int w = type.getFullWidth() + name.getFullWidth() + semi.getFullWidth();
        if (assign != null) {
            w += assign.getFullWidth();
        }
        if (init != null) {
            w += init.getFullWidth();
        }
        return w;
    }

    public static GreenVarStmt create(GreenType type, GreenToken name, GreenToken assign,
                                      GreenExpression initializer, GreenToken semicolon) {
        return intern(new GreenVarStmt(type, name, assign, initializer, semicolon));
    }

    public static GreenVarStmt create(GreenType type, String name, GreenExpression initializer) {
        return create(type,
                GreenToken.identifier(name),
                GreenToken.create(SyntaxKind.ASSIGN, "="),
                initializer,
                GreenToken.create(SyntaxKind.SEMICOLON, ";"));
    }

    public static GreenVarStmt create(GreenType type, String name) {
        return create(type,
                GreenToken.identifier(name),
                null, null,
                GreenToken.create(SyntaxKind.SEMICOLON, ";"));
    }

    public GreenType getType() {
        return type;
    }

    public GreenToken getName() {
        return name;
    }

    public String getNameText() {
        return name.getText();
    }

    public boolean hasInitializer() {
        return initializer != null;
    }

    public GreenExpression getInitializer() {
        return initializer;
    }

    @Override
    public int getChildCount() {
        return hasInitializer() ? 5 : 3;
    }

    @Override
    public GreenNode getChild(int index) {
        if (hasInitializer()) {
            return switch (index) {
                case 0 -> type;
                case 1 -> name;
                case 2 -> assign;
                case 3 -> initializer;
                case 4 -> semicolon;
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> type;
                case 1 -> name;
                case 2 -> semicolon;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (hasInitializer()) {
            return switch (index) {
                case 0 -> child == type ? this : create((GreenType) child, name, assign, initializer, semicolon);
                case 1 -> child == name ? this : create(type, (GreenToken) child, assign, initializer, semicolon);
                case 2 -> child == assign ? this : create(type, name, (GreenToken) child, initializer, semicolon);
                case 3 -> child == initializer ? this : create(type, name, assign, (GreenExpression) child, semicolon);
                case 4 -> child == semicolon ? this : create(type, name, assign, initializer, (GreenToken) child);
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> child == type ? this : create((GreenType) child, name, null, null, semicolon);
                case 1 -> child == name ? this : create(type, (GreenToken) child, null, null, semicolon);
                case 2 -> child == semicolon ? this : create(type, name, null, null, (GreenToken) child);
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public String toString() {
        return hasInitializer() ? "VarStmt[" + type + " " + name.getText() + " = " + initializer + "]"
                               : "VarStmt[" + type + " " + name.getText() + "]";
    }
}
