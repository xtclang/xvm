package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;


/**
 * A name expression specifies a name. This handles both a simple name, a qualified name, and a name
 * with type parameters.
 *
 * @author cp 2017.03.28
 */
public class NameExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NameExpression(Token name)
        {
        this(Collections.singletonList(name), null, name.getEndPosition());
        }

    public NameExpression(List<Token> names, List<TypeExpression> params, long lEndPos)
        {
        this.names   = names;
        this.params  = params;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return isDotNameWithNoParams("present") || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            ConstantPool pool = pool();
            return pool.ensurePresentCondition(new UnresolvedNameConstant(pool, getUpToDotName()));
            }

        return super.toConditionalConstant();
        }

    /**
     * Determine if the expression is a multi-part, dot-delimited name that has no type params.
     *
     * @param sName  the last name of the expression must match this name
     *
     * @return true iff the expression is a multi-part, dot-delimited name that has no type params,
     *         with the last part of the name matching the specified name
     */
    protected boolean isDotNameWithNoParams(String sName)
        {
        List<Token> names  = this.names;
        int         cNames = names.size();
        return cNames > 1 && names.get(cNames-1).getValue().equals(sName) && (params == null || params.isEmpty());
        }

    /**
     * Get all of the names in the expression except the last one.
     *
     * @return an array of names
     */
    protected String[] getUpToDotName()
        {
        List<Token> listNames = this.names;
        int         cNames    = listNames.size() - 1;
        String[]    aNames    = new String[cNames];
        for (int i = 0; i < cNames; ++i)
            {
            aNames[i] = (String) listNames.get(i).getValue();
            }
        return aNames;
        }

    /**
     * @return the number of dot-delimited names in the expression
     */
    public int getNameCount()
        {
        return names.size();
        }

    /**
     * @param i  the index of the name to obtain from the dot-delimited names in the expression
     *
     * @return the i-th name in the expression
     */
    String getName(int i)
        {
        return (String) names.get(i).getValue();
        }

    @Override
    public long getStartPosition()
        {
        return names.get(0).getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    public boolean isSpecial()
        {
        for (Token name : names)
            {
            if (name.isSpecial())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return new NamedTypeExpression(null, names, null, null, params, lEndPos);
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

        boolean first = true;
        for (Token token : names)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(token.getValue());
            }

        if (params != null)
            {
            sb.append('<');
            first = true;
            for (Expression param : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Token>          names;
    protected List<TypeExpression> params;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "params");
    }
