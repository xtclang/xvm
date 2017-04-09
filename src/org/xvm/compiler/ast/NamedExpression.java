package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * Used for named arguments.
 *
 * @author cp 2017.04.08
 */
public class NamedExpression
        extends Expression
    {
    public NamedExpression(Token name, Expression expr)
        {
        this.name = name;
        this.expr = expr;
        }

    @Override
    public String toString()
        {
        return name + " = " + expr;
        }

    public final Expression expr;
    public final Token      name;
    }
