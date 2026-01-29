package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * A right-to-left type inference place-holder, either "var" or "val", for a type expression in a
 * variable declaration.
 */
public class VariableTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public VariableTypeExpression(Token token) {
        assert token.getId() == Id.VAR || token.getId() == Id.VAL;
        this.token = token;
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>No CHILD_FIELDS in this class (leaf node)</li>
     *   <li>No transient fields in this class</li>
     * </ul>
     *
     * @param original  the VariableTypeExpression to copy from
     */
    protected VariableTypeExpression(VariableTypeExpression original) {
        super(original);

        this.token = original.token;  // Token is immutable
    }

    @Override
    public VariableTypeExpression copy() {
        return new VariableTypeExpression(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Assemble the qualified name.
     *
     * @return the dot-delimited name
     */
    public Token getToken() {
        return token;
    }

    @Override
    public long getStartPosition() {
        return token.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return token.getEndPosition();
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        // this will be replaced after the actual type is known
        return pool().typeObject();
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return token.getValueText();
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected Token token;
}
