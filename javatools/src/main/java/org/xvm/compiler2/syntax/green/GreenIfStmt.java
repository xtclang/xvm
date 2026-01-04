package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * An if statement: if (condition) thenStmt [else elseStmt]
 */
public final class GreenIfStmt extends GreenStatement {

    private final GreenToken ifKeyword;
    private final GreenToken openParen;
    private final GreenExpression condition;
    private final GreenToken closeParen;
    private final GreenStatement thenStmt;
    private final GreenToken elseKeyword; // may be null
    private final GreenStatement elseStmt; // may be null

    private GreenIfStmt(GreenToken ifKeyword, GreenToken openParen, GreenExpression condition,
                       GreenToken closeParen, GreenStatement thenStmt,
                       GreenToken elseKeyword, GreenStatement elseStmt) {
        super(SyntaxKind.IF_STATEMENT, computeWidth(ifKeyword, openParen, condition, closeParen,
                thenStmt, elseKeyword, elseStmt));
        this.ifKeyword = ifKeyword;
        this.openParen = openParen;
        this.condition = condition;
        this.closeParen = closeParen;
        this.thenStmt = thenStmt;
        this.elseKeyword = elseKeyword;
        this.elseStmt = elseStmt;
    }

    private static int computeWidth(GreenToken ifKw, GreenToken open, GreenExpression cond,
                                    GreenToken close, GreenStatement then,
                                    GreenToken elseKw, GreenStatement elseS) {
        int w = ifKw.getFullWidth() + open.getFullWidth() + cond.getFullWidth() +
                close.getFullWidth() + then.getFullWidth();
        if (elseKw != null) {
            w += elseKw.getFullWidth();
        }
        if (elseS != null) {
            w += elseS.getFullWidth();
        }
        return w;
    }

    public static GreenIfStmt create(GreenToken ifKeyword, GreenToken openParen,
                                     GreenExpression condition, GreenToken closeParen,
                                     GreenStatement thenStmt, GreenToken elseKeyword,
                                     GreenStatement elseStmt) {
        return intern(new GreenIfStmt(ifKeyword, openParen, condition, closeParen,
                thenStmt, elseKeyword, elseStmt));
    }

    public static GreenIfStmt create(GreenExpression condition, GreenStatement thenStmt) {
        return create(
                GreenToken.create(SyntaxKind.KW_IF, "if"),
                GreenToken.create(SyntaxKind.LPAREN, "("),
                condition,
                GreenToken.create(SyntaxKind.RPAREN, ")"),
                thenStmt,
                null, null);
    }

    public static GreenIfStmt create(GreenExpression condition, GreenStatement thenStmt,
                                     GreenStatement elseStmt) {
        return create(
                GreenToken.create(SyntaxKind.KW_IF, "if"),
                GreenToken.create(SyntaxKind.LPAREN, "("),
                condition,
                GreenToken.create(SyntaxKind.RPAREN, ")"),
                thenStmt,
                GreenToken.create(SyntaxKind.KW_ELSE, "else"),
                elseStmt);
    }

    public GreenToken getIfKeyword() {
        return ifKeyword;
    }

    public GreenToken getOpenParen() {
        return openParen;
    }

    public GreenExpression getCondition() {
        return condition;
    }

    public GreenToken getCloseParen() {
        return closeParen;
    }

    public GreenStatement getThenStatement() {
        return thenStmt;
    }

    public GreenToken getElseKeyword() {
        return elseKeyword;
    }

    public GreenStatement getElseStatement() {
        return elseStmt;
    }

    public boolean hasElse() {
        return elseKeyword != null;
    }

    @Override
    public int getChildCount() {
        return hasElse() ? 7 : 5;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> ifKeyword;
            case 1 -> openParen;
            case 2 -> condition;
            case 3 -> closeParen;
            case 4 -> thenStmt;
            case 5 -> elseKeyword;
            case 6 -> elseStmt;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == ifKeyword ? this : create((GreenToken) child, openParen, condition, closeParen, thenStmt, elseKeyword, elseStmt);
            case 1 -> child == openParen ? this : create(ifKeyword, (GreenToken) child, condition, closeParen, thenStmt, elseKeyword, elseStmt);
            case 2 -> child == condition ? this : create(ifKeyword, openParen, (GreenExpression) child, closeParen, thenStmt, elseKeyword, elseStmt);
            case 3 -> child == closeParen ? this : create(ifKeyword, openParen, condition, (GreenToken) child, thenStmt, elseKeyword, elseStmt);
            case 4 -> child == thenStmt ? this : create(ifKeyword, openParen, condition, closeParen, (GreenStatement) child, elseKeyword, elseStmt);
            case 5 -> child == elseKeyword ? this : create(ifKeyword, openParen, condition, closeParen, thenStmt, (GreenToken) child, elseStmt);
            case 6 -> child == elseStmt ? this : create(ifKeyword, openParen, condition, closeParen, thenStmt, elseKeyword, (GreenStatement) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return hasElse() ? "IfStmt[" + condition + " then " + thenStmt + " else " + elseStmt + "]"
                        : "IfStmt[" + condition + " then " + thenStmt + "]";
    }
}
