package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * Generic expression for something that follows the pattern "expression operator".
 *
 * @author cp 2017.04.06
 */
public class PostfixExpression
        extends Expression
    {
    public PostfixExpression(Expression expr, Token operator)
        {
        this.operator = operator;
        this.expr     = expr;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append(operator.getId().TEXT);

        return sb.toString();
        }

    public final Expression expr;
    public final Token      operator;
    }
