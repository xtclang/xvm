package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A tuple expression is an expression containing some number (0 or more) expressions.
 */
public class TupleExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TupleExpression(TypeExpression type, List<Expression> exprs, long lStartPos, long lEndPos)
        {
        this.m_type      = type;
        this.m_exprs     = exprs;
        this.m_lStartPos = lStartPos;
        this.m_lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return get the TypeExpression for the tuple, if any; otherwise return null
     */
    public TypeExpression getTypeExpression()
        {
        return m_type;
        }

    /**
     * @return the expressions making up the tuple value
     */
    public List<Expression> getExpressions()
        {
        return m_exprs;
        }

    @Override
    public long getStartPosition()
        {
        return m_lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return m_lEndPos;
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

        TypeConstant[] atypeField = null;
        if (m_type != null)
            {
            fValid &= m_type.validate(ctx, errs);

            // validate that the type is a tuple, and if it specifies any field types, then grab
            // those so that we can subsequently validate the values of those fields
            TypeConstant type = m_type.ensureTypeConstant();
            if (type.isTuple())
                {
                if (type.isParamsSpecified())
                    {
                    atypeField  = type.getParamTypesArray();
                    m_constType = type;
                    }
                }
            else
                {
                // log an error
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, pool().typeTuple(), type);
                fValid = false;
                }
            }

        List<Expression> listExprs = m_exprs;
        int cExprs = listExprs == null ? 0 : listExprs.size();
        int cTypes = atypeField == null ? 0 : atypeField.length;

        boolean        fMismatch   = atypeField != null && cExprs != cTypes;
        boolean        fBuildType  = atypeField == null || fMismatch;
        TypeConstant[] atypeActual = fBuildType ? new TypeConstant[cExprs] : null;

        for (int i = 0; i < cExprs; ++i)
            {
            // validate the field expression
            Expression expr = listExprs.get(i);
            fValid &= expr.validate(ctx, errs);

            // validate the type of the field expression (if field types were specified)
            if (i < cTypes)
                {
                TypeConstant typeField = atypeField[i];
                if (!expr.isAssignableTo(typeField))
                    {
                    expr.log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeField, expr.getImplicitType());
                    fValid = false;
                    }
                }

            if (fBuildType)
                {
                atypeActual[i] = expr.getImplicitType();
                }
            }

        TypeConstant typeActual = null;
        if (fBuildType)
            {
            ConstantPool pool = pool();
            typeActual = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeActual);
            if (m_constType == null)
                {
                m_constType = typeActual;
                }
            }

        if (fMismatch)
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, m_type.ensureTypeConstant(), typeActual);
            fValid = false;
            }

        return fValid;
        }

    @Override
    public boolean isConstant()
        {
        List<Expression> exprs = getExpressions();
        if (exprs != null)
            {
            // the tuple is constant if its members are constants
            for (Expression expr : getExpressions())
                {
                if (!expr.isConstant())
                    {
                    return false;
                    }
                }
            }
        return true;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        TypeConstant constType = m_constType;
        if (constType == null)
            {
            throw new IllegalStateException("implicit type not available before validate()");
            }

        return constType;
        }

    @Override
    public Constant generateConstant(MethodStructure.Code code, TypeConstant type,
            ErrorListener errs)
        {
        // TODO current design does not allow conformance to the tuple type that was specified
        TypeConstant     constTType = getImplicitType();
        assert constTType.isTuple();
        int              cFields    = constTType.getTupleFieldCount();
        List<Expression> listExprs  = m_exprs;
        int              cExprs     = listExprs.size();
        Constant[]       aconst     = new Constant[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            TypeConstant constFType = i < cFields
                    ? constTType.getTupleFieldType(i)
                    : pool().typeObject();
            aconst[i] = listExprs.get(i).toConstant();
            }
        return pool().ensureTupleConstant(constTType, aconst);
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk,
            ErrorListener errs)
        {
        if (type.isTuple())
            {
            // this is the expected case, i.e. that someone is asking for this expression to be
            // represented as a tuple
            }
        else
            {
            // TODO log(errs, Severity.ERROR, Compiler)
            }
        return super.generateArgument(code, type, fTupleOk, errs);
        }

    @Override
    public Argument[] generateArguments(Code code, TypeConstant[] atype, boolean fTupleOk,
            ErrorListener errs)
        {
        return super.generateArguments(code, atype, fTupleOk, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        if (m_exprs != null)
            {
            boolean first = true;
            for (Expression expr : m_exprs)
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

    protected TypeExpression   m_type;
    protected List<Expression> m_exprs;
    protected long             m_lStartPos;
    protected long             m_lEndPos;

    /**
     * Cached type of the tuple.
     */
    private transient TypeConstant m_constType;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleExpression.class, "m_type", "m_exprs");
    }
