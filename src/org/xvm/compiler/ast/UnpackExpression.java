package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;


/**
 * A tuple un-packing expression. This unpacks the values from the sub-expression tuple.
 */
public  class UnpackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnpackExpression(Expression expr, int iField)
        {
        super(expr);

        m_iField = iField;

        ConstantPool pool = pool();
        TypeConstant type = pool.ensureParameterizedTypeConstant(pool.typeTuple(), expr.getTypes());
        finishValidation(typeRequired, type, TypeFit.Fit, expr.isConstant()
                ? pool.ensureTupleConstant(type, expr.toConstants())
                : null, errs);
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the tuple field index that this expression unpacks
     */
    public int getFieldIndex()
        {
        return m_iField;
        }

    // ----- Expression compilation ----------------------------------------------------------------


    @Override
    protected boolean hasSingleValueImpl()
        {
        return true;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk,
            boolean fUsedOnce, ErrorListener errs)
        {
        if (fPack)
            {
            throw new IllegalStateException(this.toString());
            }

        if (isConstant())
            {
            return super.generateArgument(code, fLocalPropOk, fUsedOnce, errs);
            }

        // generate the tuple fields
        Argument[] args = expr.generateArguments(code, true, errs);
        assert args != null && args.length == 1;
        return args[0];
        }

    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        throw new IllegalStateException(this.toString());
        }

    @Override
    public Assignable[] generateAssignables(Code code, ErrorListener errs)
        {
        throw new IllegalStateException(this.toString());
        }

    @Override
    public void generateVoid(Code code, ErrorListener errs)
        {
        expr.generateVoid(code, errs);
        }

    @Override
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue,
            ErrorListener errs)
        {
        throw new IllegalStateException(this.toString());
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Packed:" + getUnderlyingExpression().toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    private int m_iField;
    }
