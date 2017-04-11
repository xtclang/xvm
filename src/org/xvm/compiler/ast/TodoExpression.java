package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * A to-do expression raises an exception indicating missing functionality,
 * with an optional message.
 *
 * @author cp 2017.03.28
 */
public class TodoExpression
        extends TypeExpression
    {
    public TodoExpression(Token keyword, Expression message)
        {
        this.keyword = keyword;
        this.message = message;
        }

    @Override
    public String toString()
        {
        String s = "TODO";
        return message == null ? s : (s + '(' + message + ')');
        }

    public final Token      keyword;
    public final Expression message;
    }
