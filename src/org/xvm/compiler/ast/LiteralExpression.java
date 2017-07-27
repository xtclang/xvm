package org.xvm.compiler.ast;


import org.xvm.asm.Constant;

import org.xvm.compiler.Token;

import org.xvm.util.Handy;
import org.xvm.util.PackedInteger;


/**
 * A literal expression specifies a literal value.
 *
 * @author cp 2017.03.28
 */
public class LiteralExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public LiteralExpression(Token literal)
        {
        this.literal = literal;
        }


    // ----- accessors -----------------------------------------------------------------------------


    @Override
    public boolean isConstant()
        {
        return true;
        }

    @Override
    public Constant toConstant()
        {
        switch (literal.getId())
            {
            case LIT_INT:
                return getConstantPool().ensureIntConstant((PackedInteger) literal.getValue());

            case LIT_STRING:
                return getConstantPool().ensureCharStringConstant((String) literal.getValue());

            default:
                // TODO
                throw new UnsupportedOperationException(
                        "LiteralExpression.toConstant() not implemented for " + literal.getId());
            }
        }

    @Override
    public long getStartPosition()
        {
        return literal.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return literal.getEndPosition();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        switch (literal.getId())
            {
            case LIT_INT:
                return String.valueOf(literal.getValue());

            case LIT_STRING:
                 return Handy.quotedString(String.valueOf(literal.getValue()));

            default:
                // TODO
                return "LiteralExpression.toString() not implemented for " + literal.getId();
            }
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token literal;
    }
