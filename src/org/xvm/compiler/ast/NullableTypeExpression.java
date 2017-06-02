package org.xvm.compiler.ast;


import java.lang.reflect.Field;

/**
 * A nullable type expression is a type expression followed by a question mark.
 *
 * @author cp 2017.03.31
 */
public class NullableTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NullableTypeExpression(TypeExpression type, long lEndPos)
        {
        this.type    = type;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------


    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
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

        sb.append(type)
          .append("?");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected long           lEndPos;


    private static final Field[] CHILD_FIELDS = fieldsForNames(NullableTypeExpression.class, "type");
    }
