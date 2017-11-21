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
    protected boolean validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;

        TypeConstant[] atypeRequired  = null;   // the optional types passed in as a requirement
        TypeConstant[] atypeSpecified = null;   // tuple field types specified in the source code

        if (typeRequired != null && typeRequired.isParamsSpecified())
            {
            atypeRequired = typeRequired.getParamTypesArray();
            }

        TypeConstant typeSpecified = null;
        if (m_type != null)
            {
            fValid &= m_type.validate(ctx, null, errs);

            // validate that the type is a tuple, and if it specifies any field types, then grab
            // those so that we can subsequently validate the values of those fields
            typeSpecified = m_type.ensureTypeConstant();
            if (typeSpecified.isTuple())
                {
                if (typeSpecified.isParamsSpecified())
                    {
                    atypeSpecified = typeSpecified.getParamTypesArray();
                    }
                }
            else
                {
                // log an error
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, pool().typeTuple(), typeSpecified);
                fValid = false;
                }
            }

        List<Expression> listExprs    = m_exprs;
        int              cExprs       = listExprs == null ? 0 : listExprs.size();
        int              cTypeReqs    = atypeRequired  == null ? 0 : atypeRequired.length;
        int              cTypeSpecs   = atypeSpecified == null ? 0 : atypeSpecified.length;
        boolean          fMismatch    =  (atypeRequired  != null && cExprs != cTypeReqs )
                                      || (atypeSpecified != null && cExprs != cTypeSpecs);
        boolean          fBuildType   = (atypeRequired == null && atypeSpecified == null) || fMismatch;
        TypeConstant[]   atypeImplied = fBuildType ? new TypeConstant[cExprs] : null;

        for (int i = 0; i < cExprs; ++i)
            {
            // validate the field expression; use the specified type, if it is provided, otherwise
            // use he required type. the reason for going in two steps (implicit -> specified ->
            // required) instead of testing directly for required is that the flow has to be
            // verified to be legal, even if we skip the middle step in the compiled result
            Expression   expr = listExprs.get(i);
            TypeConstant type = i < cTypeSpecs
                    ? atypeSpecified[i]
                    : i < cTypeReqs
                            ? atypeRequired[i]
                            : null;
            fValid &= expr.validate(ctx, type, errs);

            // validate the type of the field expression (if field types were specified)
            if (i < cTypeReqs && i < cTypeSpecs)
                {
                TypeConstant typeReq = atypeRequired[i];
                if (!type.isA(typeReq))                     // TODO isConvertibleTo, not isA
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeReq, type);
                    fValid = false;
                    }

                // the required type overrides the specified type
                type = typeReq;
                }

            if (fBuildType)
                {
                atypeImplied[i] = expr.getImplicitType();
                }
            }

        TypeConstant typeImplied = null;
        if (fBuildType)
            {
            ConstantPool pool = pool();
            typeImplied = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeImplied);
            m_constType = typeImplied;
            }
        else
            {
            m_constType = atypeRequired == null
                    ? typeSpecified
                    : typeRequired;
            assert m_constType != null && m_constType.isTuple() && m_constType.isParamsSpecified();
            }

        if (fMismatch)
            {
            if (atypeSpecified != null)
                {
                assert cExprs != cTypeSpecs || (atypeRequired != null && cTypeSpecs != cTypeReqs);

                if (cExprs != cTypeSpecs)
                    {
                    // possible issue converting from the actual tuple to the specified types ...
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeSpecified, typeImplied);
                    }

                // ... and from the specified types to the required types
                if (atypeRequired != null && cTypeSpecs != cTypeReqs)
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeRequired, typeSpecified);
                    }
                }
            else
                {
                assert atypeRequired != null && cExprs != cTypeReqs;

                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeRequired, typeImplied);
                }

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
