package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A name expression specifies a name. This handles both a simple name, a qualified name, and a name
 * with type parameters.
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        String   sName = names.get(0).getValue().toString();
        Argument arg   = ctx.resolveName(sName);
        if (arg == null)
            {
            log(errs, Severity.ERROR, org.xvm.compiler.Compiler.NAME_MISSING, sName);
            fValid = false;
            }
        else
            {
            // TODO resolve subsequent names
            if (names.size() > 1)
                {
                notImplemented();
                }

            m_arg = arg;
            }

        // TODO what does it mean if there are params?
        if (params != null)
            {
            for (TypeExpression type : params)
                {
                fValid &= type.validate(ctx, errs);
                }
            }

        return fValid;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        return m_arg == null
                ? pool().typeObject()
                : m_arg.getType();
        }

    @Override
    public boolean isAssignable()
        {
        // TODO it needs to be an argument >= 0 but NOT a method parameter
        return super.isAssignable();
        }

    @Override
    public boolean isConstant()
        {
        // TODO verify that this is correct; what if the arg points to a Property, for example?
        return m_arg != null && m_arg instanceof Constant;
        }

    @Override
    public Constant toConstant()
        {
        assert isConstant();
        return (Constant) m_arg;
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk, ErrorListener errs)
        {
        if (m_arg == null)
            {
            // TODO log error
            return generateBlackHole(type);
            }

        return isConstant()
                ? super.generateArgument(code, type, fTupleOk, errs)
                : validateAndConvertSingle(m_arg, code, type, errs);
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

    private Argument m_arg;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "params");
    }
