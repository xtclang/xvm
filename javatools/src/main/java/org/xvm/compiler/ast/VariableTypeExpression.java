package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * A right-to-left type inference place-holder, either "var" or "val", for a type expression in a
 * variable declaration.
 */
public final class VariableTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public VariableTypeExpression(Token token) {
        assert token.getId() == Id.VAR || token.getId() == Id.VAL;
        this.token = token;
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

    // ----- copy support --------------------------------------------------------------------------

    /**
     * Shallow copy constructor for {@link AstNode#deepCopy()}: every declared field of this
     * tier is carried over verbatim (children are re-copied and re-adopted by the walk); the
     * field parity assertion in deepCopy() fails loudly if a field is added but not copied.
     */
    protected VariableTypeExpression(VariableTypeExpression that) {
        super(that);
        this.token = that.token;
    }

    @Override
    protected AstNode shallowCopy() {
        return new VariableTypeExpression(this);
    }
}
