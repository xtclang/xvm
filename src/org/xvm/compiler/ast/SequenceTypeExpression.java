package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import org.xvm.compiler.Token;


/**
 * An sequence type expression is a type expression followed by an ellipsis.
 *
 * @author cp 2017.03.31
 */
public class SequenceTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SequenceTypeExpression(TypeExpression type, Token tokDots)
        {
        this.type    = type;
        this.lEndPos = tokDots.getEndPosition();
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
          .append("...");

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

    private static final Field[] CHILD_FIELDS = fieldsForNames(SequenceTypeExpression.class, "type");
    }
