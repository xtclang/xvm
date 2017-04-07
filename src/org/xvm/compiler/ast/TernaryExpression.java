package org.xvm.compiler.ast;


/**
 * A ternary expression is the "a ? b : c" expression.
 *
 * @author cp 2017.04.06
 */
public class TernaryExpression
        extends Expression
    {
    public TernaryExpression(Expression expr, Expression exprThen, Expression exprElse)
        {
        this.expr     = expr;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }

    public final Expression expr;
    public final Expression exprThen;
    public final Expression exprElse;
    }
