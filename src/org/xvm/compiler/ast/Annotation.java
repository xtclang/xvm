package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;


/**
 * An annotation is a type annotation and an optional argument list.
 *
 * @author cp 2017.03.31
 */
public class Annotation
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public Annotation(NamedTypeExpression type, List<Expression> args)
        {
        this.type = type;
        this.args = args;
        }


    // ----- accessors -----------------------------------------------------------------------------

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

        sb.append('@')
          .append(type);

        if (args != null)
            {
            sb.append('(');

            boolean first = true;
            for (Expression expr : args)
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

            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected NamedTypeExpression type;
    protected List<Expression>    args;

    private static final Field[] CHILD_FIELDS = fieldsForNames(Annotation.class, "type", "args");
    }
