package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_N0 rvalue-function, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Call_N0 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;

    public Call_N0(int nFunction, int[] anArg)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            hException = callSuperN(frame, f_anArgValue, -1);
            }
        else if (f_nFunctionValue >= 0)
            {
            try
                {
                FunctionHandle function = (FunctionHandle) frame.getArgument(f_nFunctionValue);

                hException = function.call1(frame, f_anArgValue, -1);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                hException = e.getExceptionHandle();
                }
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            try
                {
                ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, function.m_cVars, 0);

                hException = frame.call1(function, null, ahVar, -1);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                hException = e.getExceptionHandle();
                }
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }