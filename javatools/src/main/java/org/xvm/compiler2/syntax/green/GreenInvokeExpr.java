package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A method/function invocation expression.
 */
public final class GreenInvokeExpr extends GreenExpression {

    /**
     * The target expression (what's being invoked).
     */
    private final GreenExpression target;

    /**
     * The opening parenthesis.
     */
    private final GreenToken openParen;

    /**
     * The arguments (may be empty).
     */
    private final GreenList arguments;

    /**
     * The closing parenthesis.
     */
    private final GreenToken closeParen;

    /**
     * Private constructor - use factory methods.
     */
    private GreenInvokeExpr(GreenExpression target, GreenToken openParen,
                           GreenList arguments, GreenToken closeParen) {
        super(SyntaxKind.INVOKE_EXPRESSION,
                target.getFullWidth() + openParen.getFullWidth() +
                arguments.getFullWidth() + closeParen.getFullWidth());
        this.target = target;
        this.openParen = openParen;
        this.arguments = arguments;
        this.closeParen = closeParen;
    }

    /**
     * Create an invocation expression.
     *
     * @param target     the target being invoked
     * @param openParen  the '(' token
     * @param arguments  the argument list
     * @param closeParen the ')' token
     * @return the interned expression
     */
    public static GreenInvokeExpr create(GreenExpression target, GreenToken openParen,
                                         GreenList arguments, GreenToken closeParen) {
        return intern(new GreenInvokeExpr(target, openParen, arguments, closeParen));
    }

    /**
     * Create an invocation expression with default parens.
     *
     * @param target    the target being invoked
     * @param arguments the argument expressions
     * @return the interned expression
     */
    public static GreenInvokeExpr create(GreenExpression target, GreenExpression... arguments) {
        return create(
                target,
                GreenToken.create(SyntaxKind.LPAREN, "("),
                GreenList.create(SyntaxKind.ARGUMENT, arguments),
                GreenToken.create(SyntaxKind.RPAREN, ")"));
    }

    /**
     * @return the target expression
     */
    public GreenExpression getTarget() {
        return target;
    }

    /**
     * @return the opening parenthesis
     */
    public GreenToken getOpenParen() {
        return openParen;
    }

    /**
     * @return the argument list
     */
    public GreenList getArguments() {
        return arguments;
    }

    /**
     * @return the number of arguments
     */
    public int getArgumentCount() {
        return arguments.getChildCount();
    }

    /**
     * Get an argument by index.
     *
     * @param index the argument index
     * @return the argument expression
     */
    public GreenExpression getArgument(int index) {
        return (GreenExpression) arguments.getChild(index);
    }

    /**
     * @return the closing parenthesis
     */
    public GreenToken getCloseParen() {
        return closeParen;
    }

    @Override
    public int getChildCount() {
        return 4;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> target;
            case 1 -> openParen;
            case 2 -> arguments;
            case 3 -> closeParen;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == target ? this : create((GreenExpression) child, openParen, arguments, closeParen);
            case 1 -> child == openParen ? this : create(target, (GreenToken) child, arguments, closeParen);
            case 2 -> child == arguments ? this : create(target, openParen, (GreenList) child, closeParen);
            case 3 -> child == closeParen ? this : create(target, openParen, arguments, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "InvokeExpr[" + target + "(" + arguments.getChildCount() + " args)]";
    }
}
