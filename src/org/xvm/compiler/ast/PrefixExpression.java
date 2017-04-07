package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * Generic expression for something that follows the pattern "operator expression".
 *
 * @author cp 2017.04.06
 */
public class PrefixExpression
        extends Expression
    {
    public PrefixExpression(Token operator, Expression expr)
        {
        this.operator = operator;
        this.expr     = expr;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(operator.getId().TEXT)
          .append(expr);

        return sb.toString();
        }

    public final Token      operator;
    public final Expression expr;
    }
