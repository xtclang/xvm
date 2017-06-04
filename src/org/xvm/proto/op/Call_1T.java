package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_1T rvalue-function, rvalue-param, lvalue-return-tuple
 *
 * @author gg 2017.03.08
 */
public class Call_1T extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Call_1T(int nFunction, int nArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (f_nFunctionValue == A_SUPER)
                {
                return callSuperN1(frame, new int[]{f_nArgValue}, f_nRetValue); // TODO is that right?
                }

            if (f_nFunctionValue < 0)
                {
                InvocationTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

                ObjectHandle hArg = frame.getArgument(f_nArgValue);
                if (hArg == null)
                    {
                    return R_REPEAT;
                    }

                ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];
                ahVar[0] = hArg;

                return frame.call1(function, null, ahVar, f_nRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            ObjectHandle[] ahVars = frame.getArguments(new int[]{f_nArgValue}, hFunction.getVarCount(), 0);

            if (hFunction == null || ahVars == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, ahVars, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
