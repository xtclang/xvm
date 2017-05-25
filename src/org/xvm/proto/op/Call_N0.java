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
        try
            {
            if (f_nFunctionValue == A_SUPER)
                {
                return callSuperN(frame, f_anArgValue, Frame.R_UNUSED);
                }

            if (f_nFunctionValue < 0)
                {
                FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

                ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, function.m_cVars, 0);
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                return frame.call1(function, null, ahVar, Frame.R_UNUSED);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, hFunction.getVarCount(), 0);
            if (hFunction == null || ahVar == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, ahVar, Frame.R_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }