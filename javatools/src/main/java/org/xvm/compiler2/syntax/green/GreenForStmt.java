package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A for statement: for (init; condition; update) body
 */
public final class GreenForStmt extends GreenStatement {

    private final GreenToken forKeyword;
    private final GreenToken openParen;
    private final GreenNode init; // statement or expression, may be null
    private final GreenToken semi1;
    private final GreenExpression condition; // may be null
    private final GreenToken semi2;
    private final GreenExpression update; // may be null
    private final GreenToken closeParen;
    private final GreenStatement body;

    private GreenForStmt(GreenToken forKeyword, GreenToken openParen, GreenNode init,
                        GreenToken semi1, GreenExpression condition, GreenToken semi2,
                        GreenExpression update, GreenToken closeParen, GreenStatement body) {
        super(SyntaxKind.FOR_STATEMENT, computeWidth(forKeyword, openParen, init, semi1,
                condition, semi2, update, closeParen, body));
        this.forKeyword = forKeyword;
        this.openParen = openParen;
        this.init = init;
        this.semi1 = semi1;
        this.condition = condition;
        this.semi2 = semi2;
        this.update = update;
        this.closeParen = closeParen;
        this.body = body;
    }

    private static int computeWidth(GreenToken forKw, GreenToken open, GreenNode init,
                                    GreenToken s1, GreenExpression cond, GreenToken s2,
                                    GreenExpression upd, GreenToken close, GreenStatement body) {
        int w = forKw.getFullWidth() + open.getFullWidth() + s1.getFullWidth() +
                s2.getFullWidth() + close.getFullWidth() + body.getFullWidth();
        if (init != null) {
            w += init.getFullWidth();
        }
        if (cond != null) {
            w += cond.getFullWidth();
        }
        if (upd != null) {
            w += upd.getFullWidth();
        }
        return w;
    }

    public static GreenForStmt create(GreenToken forKeyword, GreenToken openParen,
                                      GreenNode init, GreenToken semi1,
                                      GreenExpression condition, GreenToken semi2,
                                      GreenExpression update, GreenToken closeParen,
                                      GreenStatement body) {
        return intern(new GreenForStmt(forKeyword, openParen, init, semi1, condition,
                semi2, update, closeParen, body));
    }

    public GreenToken getForKeyword() {
        return forKeyword;
    }

    public GreenNode getInit() {
        return init;
    }

    public GreenExpression getCondition() {
        return condition;
    }

    public GreenExpression getUpdate() {
        return update;
    }

    public GreenStatement getBody() {
        return body;
    }

    @Override
    public int getChildCount() {
        return 9;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> forKeyword;
            case 1 -> openParen;
            case 2 -> init;
            case 3 -> semi1;
            case 4 -> condition;
            case 5 -> semi2;
            case 6 -> update;
            case 7 -> closeParen;
            case 8 -> body;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == forKeyword ? this : create((GreenToken) child, openParen, init, semi1, condition, semi2, update, closeParen, body);
            case 1 -> child == openParen ? this : create(forKeyword, (GreenToken) child, init, semi1, condition, semi2, update, closeParen, body);
            case 2 -> child == init ? this : create(forKeyword, openParen, child, semi1, condition, semi2, update, closeParen, body);
            case 3 -> child == semi1 ? this : create(forKeyword, openParen, init, (GreenToken) child, condition, semi2, update, closeParen, body);
            case 4 -> child == condition ? this : create(forKeyword, openParen, init, semi1, (GreenExpression) child, semi2, update, closeParen, body);
            case 5 -> child == semi2 ? this : create(forKeyword, openParen, init, semi1, condition, (GreenToken) child, update, closeParen, body);
            case 6 -> child == update ? this : create(forKeyword, openParen, init, semi1, condition, semi2, (GreenExpression) child, closeParen, body);
            case 7 -> child == closeParen ? this : create(forKeyword, openParen, init, semi1, condition, semi2, update, (GreenToken) child, body);
            case 8 -> child == body ? this : create(forKeyword, openParen, init, semi1, condition, semi2, update, closeParen, (GreenStatement) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ForStmt[" + body + "]";
    }
}
