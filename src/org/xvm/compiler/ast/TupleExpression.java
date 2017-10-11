package org.xvm.compiler.ast;


import java.io.DataInput;
import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;

import org.xvm.util.Severity;


/**
 * A tuple expression is an expression containing some number (0 or more) expressions.
 *
 * @author cp 2017.04.07
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
    TypeExpression getTypeExpression()
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
                    : getConstantPool().ensureEcstasyTypeConstant("Object");
            aconst[i] = listExprs.get(i).toConstant();
            }
        return getConstantPool().ensureTupleConstant(constTType, aconst);
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
    public TypeConstant getImplicitType()
        {
        TypeConstant constType = m_constType;
        if (constType == null)
            {
            // obviously the type is "tuple", but "tuple of what?", and there are two ways that the
            // fields of the tuple can be typed:
            // 1) by specifying a type for the tuple, which defines the types of each field, or
            // 2) by not specifying a type, and getting the implicit type by asking each field for its
            //    own type
            List<Expression> listExprs = m_exprs;
            TypeExpression   exprType  = this.m_type;
            if (exprType == null)
                {
                int              cExprs      = listExprs.size();
                TypeConstant[]   aconstTypes = new TypeConstant[cExprs];
                for (int i = 0; i < cExprs; ++i)
                    {
                    aconstTypes[i] = listExprs.get(i).getImplicitType();
                    }
                ConstantPool pool = getConstantPool();
                constType = pool.ensureParameterizedTypeConstant(
                        pool.ensureEcstasyTypeConstant("collections.Tuple"), aconstTypes);
                }
            else
                {
                // let's start by evaluating the type that the tuple was told that it is
                constType = exprType.ensureTypeConstant();
                assert constType.isTuple();
                // note: the type might not match the # of expressions; that will be figured out by
                // a call to one of the generateArgument(s) methods
                }

            m_constType = constType;
            }

        return constType;
        }

    @Override
    public Op.Argument generateArgument(MethodStructure.Code code, TypeConstant constType,
            boolean fTupleOk, ErrorListener errs)
        {
        if (constType.isTuple())
            {
            // this is the expected case, i.e. that someone is asking for this expression to be
            // represented as a tuple
            }
        else
            {
            log(errs, Severity.ERROR, Compiler)
            }
        }

    @Override
    public List<Op.Argument> generateArguments(MethodStructure.Code code,
            List<TypeConstant> listTypes, boolean fTupleOk, ErrorListener errs)
        {
        // TODO
        return super.generateArguments(code, listTypes, fTupleOk, errs);
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

    private TypeExpression   m_type;
    private List<Expression> m_exprs;
    private long             m_lStartPos;
    private long             m_lEndPos;

    /**
     * Cached type of the tuple.
     */
    private transient TypeConstant m_constType;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleExpression.class, "m_type", "m_exprs");
    }
