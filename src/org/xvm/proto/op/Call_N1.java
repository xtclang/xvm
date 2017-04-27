package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_N1 rvalue-function, #params:(rvalue) lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Call_N1 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public Call_N1(int nFunction, int[] anArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        if (f_nFunctionValue == A_SUPER)
            {
            hException = callSuperN(frame, f_anArgValue, f_nRetValue);
            }
        else if (f_nFunctionValue >= 0)
            {
            try
                {
                FunctionHandle function = (FunctionHandle) frame.getArgument(f_nFunctionValue);

                hException = function.call1(frame, f_anArgValue, f_nRetValue);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                hException = e.getExceptionHandle();
                }
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            try
                {
                ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, function.m_cVars, 0);

                hException = frame.f_context.createFrame1(frame, function, null, ahVar, f_nRetValue).execute();
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