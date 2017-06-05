package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A typedef statement specifies a type to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class TypedefStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypedefStatement(Expression cond, Token keyword, TypeExpression type, Token alias)
        {
        this.cond      = cond;
        this.lStartPos = cond == null ? keyword.getStartPosition() : cond.getStartPosition();
        this.type      = type;
        this.alias     = alias;
        }


    // ----- accessors -----------------------------------------------------------------------------


    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return alias.getEndPosition();
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

        if (cond != null)
            {
            sb.append("if (")
              .append(cond)
              .append(") { ");
            }

        sb.append("typedef ")
          .append(type)
          .append(' ')
          .append(alias.getValue())
          .append(';');

        if (cond != null)
            {
            sb.append(" }");
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     cond;
    protected Token          alias;
    protected TypeExpression type;
    protected long           lStartPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypedefStatement.class, "cond", "type");
    }
