package org.xvm.compiler.ast;


/**
 * An expression statement is just an expression that someone stuck a semicolon on the end of.
 *
 * @author cp 2017.04.03
 */
public class ExpressionStatement
        extends Statement
    {
    public ExpressionStatement(Expression expr)
        {
        this.expr = expr;
        }

    @Override
    public String toString()
        {
        return expr.toString() + ';';
        }

    public final Expression expr;
    }
