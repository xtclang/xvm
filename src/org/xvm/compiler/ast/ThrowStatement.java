package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * A throw statement throws an exception.
 *
 * @author cp 2017.04.09
 */
public class ThrowStatement
        extends Statement
    {
    public ThrowStatement(Token keyword, Expression expr)
        {
        this.keyword = keyword;
        this.expr    = expr;
        }

    @Override
    public String toString()
        {
        return "throw " + expr.toString() + ';';
        }

    public final Token keyword;
    public final Expression expr;
    }
