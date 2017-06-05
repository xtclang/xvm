package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class ImportStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImportStatement(Expression cond, Token keyword, Token alias, List<Token> qualifiedName)
        {
        this.cond          = cond;
        this.keyword       = keyword;
        this.alias         = alias;
        this.qualifiedName = qualifiedName;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
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

        sb.append("import ");

        boolean first = true;
        String last = null;
        for (Token name : qualifiedName)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            last = String.valueOf(name.getValue());
            sb.append(last);
            }

        if (alias != null && !last.equals(alias.getValue()))
            {
            sb.append(" as ")
              .append(alias.getValue());
            }

        sb.append(';');

        if (cond != null)
            {
            sb.append(" }");
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression  cond;
    protected Token       keyword;
    protected Token       alias;
    protected List<Token> qualifiedName;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ImportStatement.class, "cond");
    }
