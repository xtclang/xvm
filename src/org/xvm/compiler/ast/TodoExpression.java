package org.xvm.compiler.ast;


/**
 * A to-do expression raises an exception indicating missing functionality,
 * with an optional message.
 *
 * @author cp 2017.03.28
 */
public class TodoExpression
        extends Expression
    {
    public TodoExpression(Expression message)
        {
        this.message = message;
        }

    @Override
    public String toString()
        {
        String s = "TODO";
        return message == null ? s : (s + '(' + message + ')');
        }

    public final Expression message;
    }
