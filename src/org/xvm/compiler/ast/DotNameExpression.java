package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * If you already have an expression "expr", this is for "expr.name".
 *
 * @author cp 2017.04.08
 */
public class DotNameExpression
        extends Expression
    {
    public DotNameExpression(Expression expr, Token name)
        {
        this.expr = expr;
        this.name = name;
        }

    @Override
    public String toString()
        {
        return expr.toString() + '.' + name.getValue();
        }

    public final Expression expr;
    public final Token      name;
    }
