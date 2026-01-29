package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


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

    /**
     * Copy constructor.
     *
     * <p><b>Master clone() semantics:</b>
     * <ul>
     *   <li>Deep copy (from CHILD_FIELDS): expr (handled by parent)</li>
     *   <li>Shallow copy (same reference): name</li>
     * </ul>
     *
     * @param original  the expression to copy
     */
    protected LabeledExpression(LabeledExpression original) {
        super(original);  // Deep copies expr

        // Shallow copy non-child fields
        this.name = original.name;
    }

    @Override
    public LabeledExpression copy() {
        return new LabeledExpression(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
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
}
