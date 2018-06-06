package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.op.Invoke_01;


/**
 * An type conversion expression. This converts a value from the sub-expression into a value of a
 * different type.
 */
public  class ConvertExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ConvertExpression(Expression expr, MethodConstant idConv, ErrorListener errs)
        {
        super(expr);

        assert idConv != null;
        assert idConv.getRawParams().length == 0; // TODO add support for default parameters
        assert idConv.getRawReturns().length > 0;
        assert !idConv.getComponent().isStatic();

        TypeConstant type = idConv.getRawReturns()[0];
        Constant     val  = null;
        if (expr.isConstant())
            {
            // determine if compile-time conversion is supported
            val = convertConstant(expr.toConstant(), type);
            }

        finishValidation(null, type, expr.getTypeFit().addConversion(), val, errs);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the method id that specifies the conversion method
     */
    public MethodConstant getConversionMethod()
        {
        return m_idConv;
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public void generateVoid(Code code, ErrorListener errs)
        {
        expr.generateVoid(code, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            super.generateAssignment(code, LVal, errs);
            return;
            }

        // get the value to be converted
        Argument argIn = getUnderlyingExpression().generateArgument(code, true, true, errs);

        // determine the destination of the conversion
        if (LVal.isLocalArgument())
            {
            code.add(new Invoke_01(argIn, m_idConv, LVal.getLocalArgument()));
            }
        else
            {
            Register regResult = new Register(getType());
            code.add(new Invoke_01(argIn, m_idConv, regResult));
            LVal.assign(regResult, code, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return getUnderlyingExpression().toString()
                + '.' + m_idConv.getName()
                + '<' + getType().getValueString() + ">()";
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The conversion method.
     */
    private MethodConstant m_idConv;
    }
