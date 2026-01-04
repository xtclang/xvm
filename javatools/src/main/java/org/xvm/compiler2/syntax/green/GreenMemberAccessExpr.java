package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A member access expression: target.member
 */
public final class GreenMemberAccessExpr extends GreenExpression {

    private final GreenExpression target;
    private final GreenToken dot;
    private final GreenToken member;

    private GreenMemberAccessExpr(GreenExpression target, GreenToken dot, GreenToken member) {
        super(SyntaxKind.MEMBER_ACCESS_EXPRESSION,
                target.getFullWidth() + dot.getFullWidth() + member.getFullWidth());
        this.target = target;
        this.dot = dot;
        this.member = member;
    }

    /**
     * Create a member access expression.
     *
     * @param target the target expression
     * @param dot    the '.' token
     * @param member the member name token
     * @return the interned expression
     */
    public static GreenMemberAccessExpr create(GreenExpression target, GreenToken dot,
                                               GreenToken member) {
        return intern(new GreenMemberAccessExpr(target, dot, member));
    }

    /**
     * Create a member access expression with default dot.
     *
     * @param target the target expression
     * @param member the member name token
     * @return the interned expression
     */
    public static GreenMemberAccessExpr create(GreenExpression target, GreenToken member) {
        return create(target, GreenToken.create(SyntaxKind.DOT, "."), member);
    }

    /**
     * @return the target expression
     */
    public GreenExpression getTarget() {
        return target;
    }

    /**
     * @return the dot token
     */
    public GreenToken getDot() {
        return dot;
    }

    /**
     * @return the member name token
     */
    public GreenToken getMember() {
        return member;
    }

    /**
     * @return the member name as a string
     */
    public String getMemberName() {
        return member.getText();
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> target;
            case 1 -> dot;
            case 2 -> member;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == target ? this : create((GreenExpression) child, dot, member);
            case 1 -> child == dot ? this : create(target, (GreenToken) child, member);
            case 2 -> child == member ? this : create(target, dot, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "MemberAccessExpr[" + target + "." + member.getText() + "]";
    }
}
