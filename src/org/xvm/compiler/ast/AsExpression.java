package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * Expression for "expression as type".
 */
public class AsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AsExpression(Expression expr1, Token operator, TypeExpression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------

    // TODO


    // ----- fields --------------------------------------------------------------------------------

    }
