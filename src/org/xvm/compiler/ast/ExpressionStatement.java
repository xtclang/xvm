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
        this(expr, true);
        }

    public ExpressionStatement(Expression expr, boolean standalone)
        {
        this.expr = expr;
        this.term = standalone;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr);
        if (term)
            {
            sb.append(';');
            }
        return sb.toString();
        }

    public final Expression expr;
    public final boolean    term;
    }
