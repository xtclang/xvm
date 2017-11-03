package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;

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
    public Constant toConstant()
        {
        if (isConstant())
            {
            List<Expression> listExprs    = m_exprs;
            int              cFields      = listExprs == null ? 0 : listExprs.size();
            Constant[]       aconstFields = new Constant[cFields];
            TypeConstant[]   atypeFields  = new TypeConstant[cFields];
            for (int i = 0; i < cFields; ++i)
                {
                Constant constVal = listExprs.get(i).toConstant();
                aconstFields[i] = constVal;
                atypeFields [i] = constVal.getType();
                }
            TypeConstant typeTuple =
                    pool().ensureParameterizedTypeConstant(pool().typeTuple(), atypeFields);
            return pool().ensureTupleConstant(typeTuple, aconstFields);
            }

        return super.toConstant();
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
    public Constant generateConstant(Code code, TypeConstant type, ErrorListener errs)
        {
        assert isConstant();

        if (!isAssignableTo(type))
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, getImplicitType(), type);
            return generateFakeConstant(type);
            }

        // we'll need to know the type to ask for for each field, even if the caller didn't specify
        // field types (in which case we'll use the implicit type)
        TypeConstant typeTuple = type.isTuple() && type.isParamsSpecified()
                ? type
                : getImplicitType();

        // for each field, generate an argument representing the value of that field
        List<Expression> listExprs = m_exprs;
        int              cExprs    = listExprs.size();
        Constant[]       aField    = new Constant[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            TypeConstant typeField = typeTuple.getTupleFieldType(i);
            aField[i] = listExprs.get(i).generateConstant(code, typeField, errs);
            }

        return pool().ensureTupleConstant(type, aField);
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk, ErrorListener errs)
        {
        if (isConstant())
            {
            return generateConstant(code, type, errs);
            }

        if (!isAssignableTo(type))
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, getImplicitType(), type);
            return generateBlackHole(type);
            }

        // we'll need to know the type to ask for for each field, even if the caller didn't specify
        // field types (in which case we'll use the implicit type)
        TypeConstant typeTuple = type.isTuple() && type.isParamsSpecified()
                ? type
                : getImplicitType();

        // for each field, generate an argument representing the value of that field
        List<Expression> listExprs = m_exprs;
        int              cExprs    = listExprs.size();
        Argument[]       aField    = new Argument[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            aField[i] = listExprs.get(i).generateArgument(code, typeTuple.getTupleFieldType(i), false, errs);
            }

        // generate the tuple itself, and return it as an argument
        code.add(new Var_T(type, aField));
        return code.lastRegister();
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
