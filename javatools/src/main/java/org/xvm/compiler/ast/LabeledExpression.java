package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Used for named arguments.
 */
public final class LabeledExpression
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
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return name + " = " + expr;
    }


    // ----- fields --------------------------------------------------------------------------------

    private final Token name;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledExpression.class, "expr");

    // ----- copy support --------------------------------------------------------------------------

    /**
     * Shallow copy constructor for {@link AstNode#deepCopy()}: every declared field of this
     * tier is carried over verbatim (children are re-copied and re-adopted by the walk); the
     * field parity assertion in deepCopy() fails loudly if a field is added but not copied.
     */
    protected LabeledExpression(LabeledExpression that) {
        super(that);
        this.name = that.name;
    }

    @Override
    protected AstNode shallowCopy() {
        return new LabeledExpression(this);
    }
}
