package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * Expression for "expression is expression" or "expression instanceof type".
 */
public class IsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IsExpression(Expression expr1, Token operator, TypeExpression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------

    // TODO


    // ----- fields --------------------------------------------------------------------------------

    }
