package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A block statement: { statements... }
 */
public final class GreenBlockStmt extends GreenStatement {

    private final GreenToken openBrace;
    private final GreenList statements;
    private final GreenToken closeBrace;

    private GreenBlockStmt(GreenToken openBrace, GreenList statements, GreenToken closeBrace) {
        super(SyntaxKind.BLOCK_STATEMENT,
                openBrace.getFullWidth() + statements.getFullWidth() + closeBrace.getFullWidth());
        this.openBrace = openBrace;
        this.statements = statements;
        this.closeBrace = closeBrace;
    }

    public static GreenBlockStmt create(GreenToken openBrace, GreenList statements,
                                        GreenToken closeBrace) {
        return intern(new GreenBlockStmt(openBrace, statements, closeBrace));
    }

    public static GreenBlockStmt create(GreenStatement... statements) {
        return create(
                GreenToken.create(SyntaxKind.LBRACE, "{"),
                GreenList.create(SyntaxKind.BLOCK_STATEMENT, statements),
                GreenToken.create(SyntaxKind.RBRACE, "}"));
    }

    public GreenToken getOpenBrace() {
        return openBrace;
    }

    public GreenList getStatements() {
        return statements;
    }

    public int getStatementCount() {
        return statements.getChildCount();
    }

    public GreenStatement getStatement(int index) {
        return (GreenStatement) statements.getChild(index);
    }

    public GreenToken getCloseBrace() {
        return closeBrace;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> openBrace;
            case 1 -> statements;
            case 2 -> closeBrace;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == openBrace ? this : create((GreenToken) child, statements, closeBrace);
            case 1 -> child == statements ? this : create(openBrace, (GreenList) child, closeBrace);
            case 2 -> child == closeBrace ? this : create(openBrace, statements, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "BlockStmt[" + statements.getChildCount() + " statements]";
    }
}
