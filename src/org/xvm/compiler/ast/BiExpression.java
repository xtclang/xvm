package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * Generic expression for something that follows the pattern "expression operator expression".
 *
 * @author cp 2017.04.06
 */
public class BiExpression
        extends Expression
    {
    public BiExpression(Expression expr1, Token operator, Expression expr2)
        {
        this.expr1    = expr1;
        this.operator = operator;
        this.expr2    = expr2;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr1)
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(expr2);

        return sb.toString();
        }

    public final Expression expr1;
    public final Token      operator;
    public final Expression expr2;
    }
