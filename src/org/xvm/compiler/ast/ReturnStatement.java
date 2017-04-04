package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.Collections;
import java.util.List;


/**
 * A return statement specifies a return with optional values.
 *
 * @author cp 2017.04.03
 */
public class ReturnStatement
        extends Statement
    {
    public ReturnStatement(Token keyword)
        {
        this(keyword, (List) null);
        }

    public ReturnStatement(Token keyword, Expression expr)
        {
        this(keyword, Collections.singletonList(expr));
        }

    public ReturnStatement(Token keyword, List<Expression> exprs)
        {
        this.keyword = keyword;
        this.exprs   = exprs;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("return");
        if(exprs != null)
            {
            switch (exprs.size())
                {
                case 0:
                    break;

                case 1:
                    sb.append(' ')
                      .append(exprs.get(0));
                    break;

                default:
                    boolean first = true;
                    for (Expression expr : exprs)
                        {
                        if (first)
                            {
                            first = false;
                            }
                        else
                            {
                            sb.append(", ");
                            }
                        sb.append(expr);
                        }
                    break;
                }
            }
        sb.append(';');
        return sb.toString();
        }

    public final Token keyword;
    public final List<Expression> exprs;
    }
