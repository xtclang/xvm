package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;


/**
 * A tuple packing expression. This packs the multiple values from the sub-expression into a tuple.
 */
public class PackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PackExpression(Expression expr, ErrorListener errs)
        {
        super(expr);

        ConstantPool pool = pool();
        TypeConstant type = pool.ensureTupleType(expr.getTypes());
        Constant     val  = null;
        if (expr.isConstant())
            {
            type = pool.ensureImmutableTypeConstant(type);
            val  = pool.ensureTupleConstant(type, expr.toConstants());
            }
        finishValidation(null, null, type, expr.getTypeFit().addPack(), val, errs);
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return getType();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        return this;
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        expr.generateVoid(ctx, code, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        // generate the tuple fields
        Argument[] args = expr.generateArguments(ctx, code, fLocalPropOk, fUsedOnce, errs);
        assert args != null && args.length == 1;

        // generate the tuple value
        code.add(new Var_T(getType(), args));
        return code.lastRegister();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Packed:" + getUnderlyingExpression().toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    }
