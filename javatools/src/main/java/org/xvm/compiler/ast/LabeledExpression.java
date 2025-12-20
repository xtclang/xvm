package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.compiler.Token;


/**
 * Used for named arguments.
 */
public class LabeledExpression
        extends DelegatingExpression {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledExpression(Token name, Expression expr) {
        super(expr);

        this.name = name;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the token that provides the label (the name) for the expression
     */
    public Token getNameToken() {
        return name;
    }

    /**
     * @return the label name
     */
    public String getName() {
        return name.getValueText();
    }

    @Override
    public long getStartPosition() {
        return name.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return expr.getEndPosition();
    }

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        return new LabeledExpression(name, (Expression) children.get(0));
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return name + " = " + expr;
    }


    // ----- fields --------------------------------------------------------------------------------

    private final Token name;
}
