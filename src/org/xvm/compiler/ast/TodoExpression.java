package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A to-do expression raises an exception indicating missing functionality,
 * with an optional message. It can be used as an expression, as a type expression, or as a
 * statement.
 *
 * @author cp 2017.03.28
 */
public class TodoExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TodoExpression(Token keyword, Expression message)
        {
        this.keyword = keyword;
        this.message = message;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean canComplete()
        {
        return false;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return message == null ? keyword.getEndPosition() : message.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("TODO(");
        if (message != null)
            {
            sb.append(message);
            }
        sb.append(')');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression message;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TodoExpression.class, "message");
    }
