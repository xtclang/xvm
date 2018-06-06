package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.I_Get;


/**
 * A tuple un-packing expression. This unpacks the values from the sub-expression tuple.
 */
public  class UnpackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnpackExpression(Expression exprTuple, UnpackExpression[] aUnpackExprs, int iField, ErrorListener errs)
        {
        super(exprTuple);

        m_aUnpackExprs = aUnpackExprs;
        m_iField       = iField;

        TypeConstant typeTuple = exprTuple.getType();
        assert typeTuple.isTuple() && typeTuple.isParamsSpecified();
        TypeConstant typeField = typeTuple.getParamTypesArray()[iField];
        Constant     constVal  = null;
        if (exprTuple.hasConstantValue())
            {
            Constant constTuple = exprTuple.toConstant();
            assert constTuple.getFormat() == Format.Tuple;
            constVal = ((ArrayConstant) constTuple).getValue()[iField];
            }
        finishValidation(null, typeField, expr.getTypeFit().addUnpack(), constVal, errs);
        }


    // ----- accessors -----------------------------------------------------------------------------

    Argument ensureTuple(Code code, ErrorListener errs)
        {
        Argument arg = m_argTuple;
        if (arg == null)
            {
            // generate the reference to the Tuple
            arg = expr.generateArgument(code, false, false, errs);

            // stamp the tuple onto all of the UnpackExpressions
            UnpackExpression[] aUnpackExprs = m_aUnpackExprs;
            for (int i = 0, c = aUnpackExprs.length; i < c; ++i)
                {
                assert aUnpackExprs[i].m_argTuple == null;
                aUnpackExprs[i].m_argTuple = arg;
                }
            assert m_argTuple != null;
            }

        return arg;
        }

    /**
     * @return the tuple field index that this expression unpacks
     */
    public int getFieldIndex()
        {
        return m_iField;
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk,
            boolean fUsedOnce, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            return toConstant();
            }

        Argument argTuple = ensureTuple(code, errs);
        Register regField = new Register(getType());
        code.add(new I_Get(argTuple, pool().ensureIntConstant(m_iField), regField));
        return regField;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            super.generateAssignment(code, LVal, errs);
            }

        Argument argTuple = ensureTuple(code, errs);
        if (LVal.isLocalArgument())
            {
            code.add(new I_Get(argTuple, pool().ensureIntConstant(m_iField), LVal.getLocalArgument()));
            }
        else
            {
            Argument argField = generateArgument(code, LVal.supportsLocalPropMode(), true, errs);
            LVal.assign(argField, code, errs);
            }
        }

    @Override
    public void generateVoid(Code code, ErrorListener errs)
        {
        expr.generateVoid(code, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Unpacked:" + getUnderlyingExpression().toString() + "[" + m_iField + "]";
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An array of all of the UnpackExpressions for the Tuple.
     */
    private UnpackExpression[] m_aUnpackExprs;

    /**
     * The argument for the tuple, once it has been generated.
     */
    private Argument m_argTuple;

    /**
     * The 0-based tuple field index.
     */
    private int m_iField;
    }
