package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;


/**
 * A tuple packing expression. This packs the values from the sub-expression into a tuple.
 */
public  class PackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PackExpression(Expression expr)
        {
        super(expr);

        ConstantPool pool = pool();
        TypeConstant type = pool.ensureParameterizedTypeConstant(pool.typeTuple(), expr.getTypes());
        finishValidation(typeRequired, type, TypeFit.Fit, expr.isConstant()
                ? pool.ensureTupleConstant(type, expr.toConstants())
                : null, errs);
        }

    // ----- accessors -----------------------------------------------------------------------------


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public Argument generateArgument(Code code, boolean fPack, boolean fLocalPropOk,
            boolean fUsedOnce, ErrorListener errs)
        {
        if (fPack)
            {
            throw new IllegalStateException(this.toString());
            }

        if (isConstant())
            {
            return super.generateArgument(code, fPack, fLocalPropOk, fUsedOnce, errs);
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

    }
